// Read-only smoke test: title -> requested episode -> stream -> first manifest bytes.
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const root = path.resolve(__dirname, '..');
const manifest = JSON.parse(fs.readFileSync(path.join(root, 'nuvio/manifest.json'), 'utf8'));
const args = process.argv.slice(2);
const selected = args.includes('--id') ? args[args.indexOf('--id') + 1] : null;
const output = args.includes('--output') ? args[args.indexOf('--output') + 1] : null;

async function check(entry) {
  const started = Date.now(), requests = [], controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), 45000);
  const mediaType = entry.supportedTypes.includes('movie') ? 'movie' : 'tv';
  const tmdbId = mediaType === 'movie' ? '603' : '1396';
  const result = {id: entry.id, mediaType, tmdbId, season: mediaType === 'tv' ? 1 : null,
    episode: mediaType === 'tv' ? 1 : null, status: 'no_streams', playbackVerified: false};
  const boundedFetch = async (url, options = {}) => {
    const request = {url: String(url).split('?')[0], method: options.method || 'GET'};
    requests.push(request);
    try {
      const response = await fetch(url, {...options,
        signal: AbortSignal.any([controller.signal, AbortSignal.timeout(8000)])});
      request.status = response.status;
      return response;
    } catch (error) { request.error = error.message; throw error; }
  };
  try {
    const context = vm.createContext({module: {exports: {}}, console, fetch: boundedFetch});
    vm.runInContext(fs.readFileSync(path.join(root, 'nuvio', entry.filename), 'utf8'), context);
    const streams = await context.module.exports.getStreams(tmdbId, mediaType, 1, 1);
    result.streamCount = streams.length;
    result.manifests = [];
    for (const stream of streams.slice(0, 3)) {
      try {
        const response = await boundedFetch(stream.url, {headers: stream.headers});
        const reader = response.body?.getReader();
        const first = reader ? await reader.read() : {value: new Uint8Array()};
        if (reader) await reader.cancel(); // Never download the full video.
        const text = new TextDecoder().decode(first.value || new Uint8Array());
        const isHls = text.trimStart().startsWith('#EXTM3U');
        const isVideo = /video\/mp4/i.test(response.headers.get('content-type') || '');
        result.manifests.push({status: response.status, isHls, isVideo, title: stream.title});
      } catch (error) { result.manifests.push({error: error.message}); }
    }
    if (result.manifests.some(item => item.status >= 200 && item.status < 300 && (item.isHls || item.isVideo))) result.status = 'manifest_verified';
    else if (streams.length) result.status = 'unverified_links';
  } catch (error) { result.status = 'error'; result.error = error.message; }
  finally { clearTimeout(timer); controller.abort(); }
  result.durationMs = Date.now() - started;
  result.requests = requests;
  console.log(`${result.id}: ${result.status} (${result.streamCount || 0} streams, ${result.durationMs} ms)`);
  return result;
}

(async () => {
  const queue = manifest.scrapers.filter(entry => !selected || entry.id === selected), results = [];
  await Promise.all(Array.from({length: Math.min(3, queue.length)}, async () => {
    while (queue.length) results.push(await check(queue.shift()));
  }));
  const report = {checkedAt: new Date().toISOString(), scope: 'Live metadata/search/extraction and first response bytes; not device playback', results};
  if (output) fs.writeFileSync(output, JSON.stringify(report, null, 2) + '\n');
  process.exitCode = results.every(result => result.status === 'manifest_verified') ? 0 : 1;
})();
