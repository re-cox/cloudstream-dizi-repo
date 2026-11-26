const {test}=require('node:test');
const assert=require('node:assert/strict');
const {readSample}=require('./check_nuvio_live.cjs');

test('HLS signed URLs split between network chunks are read intact',async()=>{
  const playlist='#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=4000000\nhttps://cdn.test/'+'a'.repeat(6000)+'\n';
  const bytes=Buffer.from(playlist);
  const body=new ReadableStream({start(controller){
    for(let i=0;i<bytes.length;i+=900) controller.enqueue(bytes.subarray(i,i+900));
    controller.close();
  }});
  const response=new Response(body,{headers:{'content-type':'application/vnd.apple.mpegurl'}});
  assert.equal(Buffer.from(await readSample(response)).toString(),playlist);
});

test('media sample reader cancels instead of downloading an entire video',async()=>{
  let cancelled=false;
  const body=new ReadableStream({pull(controller){controller.enqueue(new Uint8Array(188));},cancel(){cancelled=true;}});
  const bytes=await readSample(new Response(body,{headers:{'content-type':'video/mp2t'}}));
  assert.equal(bytes.length,188);
  assert.ok(cancelled);
});
