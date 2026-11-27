// Read-only smoke test: title -> requested episode -> stream -> first manifest bytes.
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const root = path.resolve(__dirname, '..');
const manifest = JSON.parse(fs.readFileSync(path.join(root, 'nuvio/manifest.json'), 'utf8'));
const args = process.argv.slice(2);
const selected = args.includes('--id') ? args[args.indexOf('--id') + 1] : null;
const output = args.includes('--output') ? args[args.indexOf('--output') + 1] : null;

async function readSample(response) {
  const playlist = /mpegurl/i.test(response.headers.get('content-type') || '') || /\.m3u8(?:[?#]|$)/i.test(response.url);
  const reader=response.body?.getReader();
  if (!reader) return new Uint8Array();
  const chunks=[]; let total=0;
  try {
    do {
      const {value,done}=await reader.read();
      if (done) break;
      chunks.push(value); total+=value.length;
    } while (playlist && total<512000);
  } finally { await reader.cancel(); }
  return Buffer.concat(chunks).subarray(0,512000);
}

async function check(entry) {
  const started = Date.now(), requests = [], controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), 45000);
  const mediaType = args.includes('--type') ? args[args.indexOf('--type')+1] : entry.supportedTypes.includes('movie') ? 'movie' : 'tv';
  const tmdbId = args.includes('--tmdb') ? args[args.indexOf('--tmdb')+1] : mediaType === 'movie' ? '603' : '1396';
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
    if (args.includes('--ad-audit')) {
      const seenAds = new Set();
      result.adFilter = {rejectedUniqueUrls:0,pagesWithAdMarkup:0};
      const classify = context.advertisingUrl, scrub = context.withoutAdvertising;
      context.advertisingUrl = url => {const ad=classify(url);if(ad){seenAds.add(String(url));result.adFilter.rejectedUniqueUrls=seenAds.size;}return ad;};
      context.withoutAdvertising = html => {const clean=scrub(html);if(clean!==html)result.adFilter.pagesWithAdMarkup++;return clean;};
    }
    const streams = await context.module.exports.getStreams(tmdbId, mediaType, 1, 1);
    result.streamCount = streams.length;
    result.manifests = [];
    for (const stream of streams.slice(0, 3)) {
      try {
        const response = await boundedFetch(stream.url, {headers: stream.headers});
        const text = new TextDecoder().decode(await readSample(response));
        const isHls = text.trimStart().startsWith('#EXTM3U');
        const isVideo = /video\/mp4/i.test(response.headers.get('content-type') || '');
        const evidence = {status: response.status, isHls, isVideo, title: stream.title};
        if (response.ok && isHls) {
          try {
            let playlist = text, playlistUrl = response.url || stream.url;
            for (let level=0; level<3; level++) {
              const relative = playlist.split(/\r?\n/).map(l=>l.trim()).find(l=>l && !l.startsWith('#'));
              if (!relative) break;
              const sample = await boundedFetch(new URL(relative, playlistUrl), {headers:stream.headers});
              const chunk = {value: await readSample(sample)};
              const sampleText = new TextDecoder().decode(chunk.value || new Uint8Array());
              if (sampleText.trimStart().startsWith('#EXTM3U')) {
                playlist = sampleText; playlistUrl = sample.url; continue;
              }
              const bytes=chunk.value || new Uint8Array();
              const mediaSignature=bytes[0]===0x47 || /^(?:ftyp|styp|moof)$/.test(Buffer.from(bytes).subarray(4,8).toString()) || Buffer.from(bytes).subarray(0,3).toString()==='ID3';
              evidence.segment = {status:sample.status, bytes:bytes.length, mediaSignature,
                contentType:sample.headers.get('content-type'), signature:Buffer.from(chunk.value || []).subarray(0,16).toString('hex'),
                payloadReceived:sample.ok && bytes.length>0 && (mediaSignature || /^(?:video|audio)\//i.test(sample.headers.get('content-type') || '')) && !/^\s*<(?:!doctype|html|script|head|body)/i.test(sampleText)};
              break;
            }
          } catch (error) { evidence.segment = {error:error.message}; }
        }
        result.manifests.push(evidence);
      } catch (error) { result.manifests.push({error: error.message}); }
    }
    if (result.manifests.some(item => item.segment?.payloadReceived || item.isVideo)) result.status = 'media_sample_verified';
    else if (result.manifests.some(item => item.segment && !item.segment.payloadReceived)) result.status = 'segment_failed';
    else if (result.manifests.some(item => item.status >= 200 && item.status < 300 && item.isHls)) result.status = 'manifest_verified';
    else if (streams.length) result.status = 'unverified_links';
    else if (requests.some(r=>r.status===403)) result.status='challenge_or_denied';
    else if (requests.some(r=>r.status===429)) result.status='rate_limited';
    else if (requests.some(r=>r.error)) result.status='network_error';
  } catch (error) { result.status = 'error'; result.error = error.message; }
  finally { clearTimeout(timer); controller.abort(); }
  result.durationMs = Date.now() - started;
  result.requests = requests;
  console.log(`${result.id}: ${result.status} (${result.streamCount || 0} streams, ${result.durationMs} ms)`);
  return result;
}

if (require.main === module) (async () => {
  const queue = manifest.scrapers.filter(entry => !selected || entry.id === selected), results = [];
  await Promise.all(Array.from({length: Math.min(3, queue.length)}, async () => {
    while (queue.length) results.push(await check(queue.shift()));
  }));
  const report = {checkedAt: new Date().toISOString(), scope: 'Live metadata/search/extraction, HLS and first segment response bytes; not device playback', results};
  if (output) fs.writeFileSync(output, JSON.stringify(report, null, 2) + '\n');
  process.exitCode = results.every(result => ['manifest_verified','media_sample_verified'].includes(result.status)) ? 0 : 1;
})();

module.exports = {readSample};
