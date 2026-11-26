// Read-only HLS/first-media verification. No full programme downloads.
const fs = require('node:fs');
const path = require('node:path');
const {readSample} = require('./check_nuvio_live.cjs');
const root = path.resolve(__dirname, '..');
const args = process.argv.slice(2);
const output = args.includes('--output') ? args[args.indexOf('--output') + 1] : null;
const channels = JSON.parse(fs.readFileSync(path.join(root, 'live/channels.json'), 'utf8')).channels;
async function check(channel) {
  const result = {id:channel.id, name:channel.name, mediaVerified:false, devicePlaybackVerified:false};
  const control = new AbortController();
  const timer = setTimeout(() => control.abort(), 45000);
  try {
    let url=channel.url, encrypted=false;
    for(let depth=0;depth<5;depth++) {
      const response=await fetch(url,{headers:channel.headers,signal:AbortSignal.any([control.signal,AbortSignal.timeout(15000)])});
      const data=await readSample(response);result.status=response.status;result.host=new URL(url).host;
      if(!response.ok) break;
      const text=new TextDecoder().decode(data);
      if(text.trimStart().startsWith('#EXTM3U')) {
        encrypted ||= /#EXT-X-KEY:.*METHOD=(?!NONE)[A-Z0-9-]+/.test(text);
        const child=text.split(/\r?\n/).map(x=>x.trim()).find(x=>x&&!x.startsWith('#'));
        if(!child) throw new Error('Empty HLS playlist');
        result.hlsVerified=true;url=new URL(child,response.url||url).href;
      } else {
        const box=Buffer.from(data).subarray(4,8).toString('ascii');
        result.container=data[0]===0x47&&data[188]===0x47?'MPEG-TS':['ftyp','styp','moof','sidx'].includes(box)?'fMP4':encrypted&&data.length>1024?'encrypted HLS payload':'unrecognized';
        result.sampleBytes=data.length;result.mediaVerified=result.hlsVerified&&result.container!=='unrecognized';break;
      }
    }
  } catch(error) {result.error=error.message;} finally {clearTimeout(timer);control.abort();}
  console.log(`${result.name}: ${result.mediaVerified?'media_verified':'not_verified'} (${result.status||'network'}, ${result.container||result.error||'no media'})`);
  return result;
}
(async()=>{
  const queue=[...channels],results=[];
  await Promise.all(Array.from({length:4},async()=>{while(queue.length)results.push(await check(queue.shift()));}));
  const report={checkedAt:new Date().toISOString(),scope:'HLS and first media response; not TV picture/audio or long-run playback',results};
  if(output) fs.writeFileSync(output,JSON.stringify(report,null,2)+'\n');
  process.exitCode=results.every(x=>x.mediaVerified)?0:1;
})().catch(error=>{console.error(error.message);process.exitCode=1;});
