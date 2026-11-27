const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const crypto = require('node:crypto');

function provider(id, routes = {}, config = {}) {
  const requests = [];
  const context = vm.createContext({ module: { exports: {} }, console, fetch: async (url, options) => {
    requests.push({ url, options });
    let body;
    if (url.includes('domains.json')) body = { sites: config };
    else if (url.includes('api.themoviedb.org')) body = url.includes('/tv/') ? { name: 'Breaking Bad' } : { title: 'The Matrix' };
    else body = typeof routes === 'function' ? routes(url, options) : routes[url];
    if (body instanceof Error) throw body;
    const status = body === undefined ? 404 : 200;
    return { ok: status === 200, status, url,
      text: async () => typeof body === 'object' ? JSON.stringify(body) : String(body || ''),
      json: async () => typeof body === 'object' ? body : JSON.parse(body) };
  }});
  vm.runInContext(fs.readFileSync(path.join(__dirname, '../nuvio/providers', id + '.js'), 'utf8'), context);
  return { api: context.module.exports, context, requests };
}

test('all generated providers parse and export the Nuvio entry point', () => {
  const manifest = JSON.parse(fs.readFileSync(path.join(__dirname, '../nuvio/manifest.json')));
  assert.equal(manifest.scrapers.length, 16);
  for (const entry of manifest.scrapers) assert.equal(typeof provider(entry.id).api.getStreams, 'function');
});

test('many dead mirrors cannot starve a sequential domain candidate', async () => {
  const old = 'https://dizipal2150.com', next = 'https://dizipal2151.com';
  const config = {DiziPal: {currentUrl: old, prefix:'https://dizipal', suffix:'.com', currentNumber:2150,
    healthCheckText:'dizipal', mirrors:Array.from({length:7}, (_,i)=>`https://dead${i}.test`)}};
  const {context:c, requests} = provider('dizipal', {
    [old + '/test']:new Error('offline'), [next]:'dizipal ' + 'x'.repeat(2000), [next+'/test']:'recovered'
  }, config);
  assert.equal(await c.siteText(old+'/test'), 'recovered');
  assert.ok(requests.some(r=>r.url===next));
});

test('a dual-language exact title matches without accepting its sequel', () => {
  const {context:c}=provider('kultfilmler');
  const items=c.matchingPages([{title:'The Matrix – Matrix',url:'/matrix'},
    {title:'The Matrix Reloaded – Matrix: Yeniden Yüklendi',url:'/sequel'}],['The Matrix']);
  assert.equal(items.length,1);
  assert.equal(items[0].url,'/matrix');
});

test('same-name remakes and wrong media types are rejected using structured metadata', () => {
  const {context:c}=provider('kultfilmler');
  const page = year=>`<script type="application/ld+json">{"@type":"Movie","datePublished":"${year}-01-01"}</script>`;
  assert.equal(c.matchesContentMetadata(page(1999),'movie',1999),true);
  assert.equal(c.matchesContentMetadata(page(2026),'movie',1999),false);
  assert.equal(c.matchesContentMetadata(page(1999),'tv',1999),false);
  assert.equal(c.matchesContentMetadata('<html>No metadata</html>','movie',1999),true);
});

test('redirect and parser identity are validated independently of page branding', () => {
  const {context:c}=provider('dizipal');
  const cfg={currentUrl:'https://dizipal.test',requiredMarkers:['__next_f']};
  assert.equal(c.validDomainIdentity(cfg.currentUrl,'https://ads.test','dizipal __next_f',cfg),false);
  assert.equal(c.validDomainIdentity(cfg.currentUrl,cfg.currentUrl,'dizipal',cfg),false);
  assert.equal(c.validDomainIdentity(cfg.currentUrl,cfg.currentUrl,'dizipal __next_f',cfg),true);
});

test('FirePlayer uses the public video API and emits its returned source', async () => {
  const base='https://kultfilmler.net', frame='https://player.test/video/'+'a'.repeat(32);
  const {context:c,requests}=provider('kultfilmler', {
    [frame]:'<title>FirePlayer</title>',
    [frame+'?do=getVideo']:{videoSources:[{file:'https://cdn.test/movie.m3u8',label:'HD'}]}
  });
  const streams=await c.extractStreamsFromPage(base,frame,0,new Set(),base+'/film/');
  assert.ok(streams.some(s=>s.url==='https://cdn.test/movie.m3u8'));
  const request=requests.find(r=>r.url.endsWith('?do=getVideo'));
  assert.equal(request.options.method,'POST');
  assert.equal(request.options.headers.Referer,frame);
});

test('URL resolution handles page-relative, root-relative and protocol-relative links', () => {
  const { context: c } = provider('diziyou');
  assert.equal(c.absolutize('https://site.test/show/season/', '../episode/'), 'https://site.test/show/episode/');
  assert.equal(c.absolutize('https://site.test', 'film/'), 'https://site.test/film/');
  assert.equal(c.absolutize('https://site.test/show/', '//player.test/e/1'), 'https://player.test/e/1');
  assert.equal(c.absolutize('https://site.test/show/', '/ep/?a=1&amp;b=2'), 'https://site.test/ep/?a=1&b=2');
  assert.equal(c.absolutize('https://site.test/', 'javascript:alert(1)'), '');
});

test('movie search ignores navigation and unrelated first results', async () => {
  const base = 'https://www.setfilmizle.ltd';
  const { api, requests } = provider('setfilmizle', {
    [base + '/?s=The%20Matrix']: '<a href="/">Anasayfa</a><a href="/other/">Other Film</a><a href="/matrix/">The Matrix</a>',
    [base + '/matrix/']: '<script>{"file":"https:\\/\\/cdn.test\\/video.m3u8?token=abc\\u0026exp=123"}</script>'
  });
  const streams = await api.getStreams('603', 'movie');
  assert.equal(streams.length, 1);
  assert.equal(streams[0].url, 'https://cdn.test/video.m3u8?token=abc&exp=123');
  assert.equal(streams[0].headers.Referer, base + '/matrix/');
  assert.equal(requests.some(r => r.url === base + '/other/'), false);
});

test('no matching title returns empty instead of playing another film', async () => {
  const { api } = provider('setfilmizle', () => '<a href="/wrong/">Other Film</a>');
  assert.equal((await api.getStreams('603', 'movie')).length, 0);
});

test('TV request without matching episode never extracts the series trailer', async () => {
  const base = 'https://www.diziyou.love';
  const { api } = provider('diziyou', {
    [base + '/?s=Breaking%20Bad']: '<a href="/breaking-bad/">Breaking Bad</a>',
    [base + '/breaking-bad/']: '<a href="/breaking-bad-1-sezon-10-bolum/">Episode 10</a><video src="https://cdn.test/trailer.mp4"></video>'
  });
  assert.equal((await api.getStreams('1396', 'tv', 1, 1)).length, 0);
});

test('DiziYou series alias finds exact episode and current storage domain with both audio options', async () => {
  const base = 'https://www.diziyou.love';
  const episode = base + '/breaking-bad-1-sezon-1-bolum/';
  const { api } = provider('diziyou', {
    [base + '/?s=Breaking%20Bad']: '<a href="/breaking-bad/">Breaking Bad</a>',
    [base + '/breaking-bad/']: '<a href="/breaking-bad-1-sezon-10-bolum/">10</a><a href="/breaking-bad-1-sezon-1-bolum/">1</a>',
    [episode]: '<iframe id="diziyouPlayer" src="/player/10551.html?next=foo"></iframe><span id="turkceAltyazili"></span><span id="turkceDublaj"></span>'
  });
  const streams = await api.getStreams('1396', 'series', 1, 1);
  assert.equal(streams.length, 2);
  assert.equal(streams[0].url, 'https://storage.diziyou.love/episodes/10551/play.m3u8');
  assert.equal(streams[1].url, 'https://storage.diziyou.love/episodes/10551_tr/play.m3u8');
});

test('invalid episode numbers and unsupported types return without network access', async () => {
  const { api, requests } = provider('diziyou');
  for (const args of [['1', 'movie'], ['1', 'tv', null, 1], ['1', 'tv', 1, 'x']]) {
    assert.equal((await api.getStreams(...args)).length, 0);
  }
  assert.equal(requests.length, 0);
});

test('embedded relative players resolve against their page and carry its Referer', async () => {
  const base = 'https://www.setfilmizle.ltd';
  const { api, requests } = provider('setfilmizle', {
    [base + '/?s=The%20Matrix']: '<a href="/film/matrix/">The Matrix</a>',
    [base + '/film/matrix/']: '<iframe src="../player/"></iframe>',
    [base + '/film/player/']: '<script>file: "https://cdn.test/main.m3u8?x=1&amp;y=2"</script>'
  });
  const streams = await api.getStreams('603', 'movie');
  assert.equal(streams[0].url, 'https://cdn.test/main.m3u8?x=1&y=2');
  assert.equal(streams[0].headers.Referer, base + '/film/player/');
  assert.equal(requests.find(r => r.url.endsWith('/film/player/')).options.headers.Referer, base + '/film/matrix/');
});

test('failed site request tries announced domain and caches successful replacement', async () => {
  const old = 'https://www.setfilmizle.ltd', live = 'https://new.test';
  const { api, requests } = provider('setfilmizle', {
    [old + '/?s=The%20Matrix']: new Error('network unavailable'),
    [live]: 'setfilmizle ' + 'x'.repeat(1600),
    [live + '/?s=The%20Matrix']: '<a href="/matrix/">The Matrix</a>',
    [live + '/matrix/']: '<video src="https://cdn.test/ok.mp4"></video>'
  }, { SetFilmIzle: { currentUrl: old, nextUrl: live, healthCheckText: 'setfilmizle' } });
  assert.equal((await api.getStreams('603', 'movie')).length, 1);
  const oldRequests = requests.filter(r => r.url.startsWith(old)).length;
  assert.equal((await api.getStreams('603', 'movie')).length, 1);
  assert.equal(requests.filter(r => r.url.startsWith(old)).length, oldRequests);
  assert.equal(requests.find(r => r.url === live + '/?s=The%20Matrix').options.headers.Referer, live + '/');
});

test('missing search endpoint does not trigger a domain rotation', async () => {
  const { api, requests } = provider('setfilmizle', {}, {
    SetFilmIzle: { currentUrl: 'https://www.setfilmizle.ltd', nextUrl: 'https://next.test' }
  });
  assert.equal((await api.getStreams('603', 'movie')).length, 0);
  assert.equal(requests.some(r => r.url.startsWith('https://next.test')), false);
});

test('Cloudflare challenge cannot become the cached fallback domain', async () => {
  const old = 'https://www.setfilmizle.ltd', blocked = 'https://blocked.test';
  const { api, requests } = provider('setfilmizle', {
    [old + '/?s=The%20Matrix']: new Error('network unavailable'),
    [blocked]: '<title>Just a moment...</title>setfilmizle ' + 'x'.repeat(1600)
  }, { SetFilmIzle: { currentUrl: old, nextUrl: blocked, healthCheckText: 'setfilmizle' } });
  assert.equal((await api.getStreams('603', 'movie')).length, 0);
  assert.equal(requests.some(r => r.url.startsWith(blocked + '/?s=')), false);
});

test('current Dizilla API uses getSearchSuggest and reads result.items', async () => {
  let action;
  const { context } = provider('dizilla', (url, options) => {
    if (url.endsWith('/api/mj')) {
      action = JSON.parse(options.body).action;
      return {ok:true,result:{items:[{title:'Breaking Bad',url:'/dizi/breaking-bad-2'}]}};
    }
  });
  await context.resolveLiveDomain();
  const items = await context.searchSite('https://dizilla.la','Breaking Bad');
  assert.equal(action, 'getSearchSuggest');
  assert.equal(items[0].url, 'https://dizilla.la/dizi/breaking-bad-2');
});

test('RSC episode lists handle nested arrays, escaped quotes and braces in descriptions', () => {
  const {context} = provider('dizilla');
  const data = JSON.stringify({episodes:[{s:1,n:1,title:'A "quoted" {title}',url:'/show-1-sezon-1-bolum-c02',cast:[]}]});
  const html = '<script>self.__next_f.push([1,' + JSON.stringify(data) + '])</script>';
  const pages = context.episodePages(html,'https://dizilla.la/dizi/show');
  assert.equal(pages[0].url, 'https://dizilla.la/show-1-sezon-1-bolum-c02');
});

test('public SetPlay bridge URL decodes without evaluating site scripts', () => {
  const {context} = provider('setfilmizle');
  const target = 'https://fastplay.test/stfplay.php?v=test', key = Buffer.from('fixture');
  const encoded = Buffer.from([...Buffer.from(target)].map((b,i)=>b^key[i%key.length])).toString('base64');
  const html = 'SPG.cerceve("b2",' + JSON.stringify(encoded) + ',' + JSON.stringify(key.toString('base64')) + ');';
  assert.equal(context.xorFrame(html), target);
});

test('SelcukFlix PBKDF2/AES decoding agrees with independent Node crypto', async () => {
  const {context} = provider('selcukflix');
  const password = 'fixture-password', salt = Buffer.from('0011223344556677','hex'), iv=Buffer.alloc(16,7);
  const key = crypto.pbkdf2Sync(password,salt,999,32,'sha512');
  const cipher = crypto.createCipheriv('aes-256-cbc',key,iv);
  const target = 'https://player.test/iframe.php?v=test';
  const ciphertext=Buffer.concat([cipher.update(target,'utf8'),cipher.final()]).toString('base64');
  const payload=JSON.stringify({player:{k:password,enc:{ciphertext,salt:salt.toString('hex'),iv:iv.toString('hex')}}});
  const html='<script>self.__next_f.push([1,'+JSON.stringify(payload)+'])</script>';
  const frames=await context.extraFrames(html,'https://selcukflix.app/film/test/izle');
  assert.equal(frames[0],target);
});

test('NP_CONFIG nonce hashes satisfy the public SHA-256 condition', () => {
  const {context} = provider('dizilla');
  const proof=context.playerProof('',{n:'fixture',b:8});
  assert.notEqual(proof,null);
  assert.equal(crypto.createHash('sha256').update('fixture:'+proof).digest()[0],0);
});


test('DiziPal current HTML search and hyphen episode URLs are supported', async () => {
  const base='https://dizipal2161.com';
  let requested=false;
  const {context}=provider('dizipal',(url)=>{
    if(url.startsWith(base+'/arama?')){requested=true;return '<a class="card-link" href="'+base+'/dizi/breaking-bad"><img alt="Breaking Bad"></a>';}
  }, {DiziPal:{currentUrl:base}});
  await context.resolveLiveDomain();
  const matches=await context.searchSite(base,'Breaking Bad');
  assert.equal(requested,true);
  assert.equal(matches[0].title,'Breaking Bad');
  assert.equal(context.episodeNumbers(base+'/bolum/breaking-bad-1-sezon-1-bolum').join(','),'1,1');
});


test('explicit ad URLs are filtered without confusing signed tokens or adaptive content', () => {
  const {context:c}=provider('kultfilmler');
  for (const url of ['https://ads.test/opaque.mp4','https://cdn.test/%61ds/opaque.mp4','https://cdn.test/pre-roll.mp4','https://cdn.test/v.mp4?is_ad=true']) assert.equal(c.advertisingUrl(url),true,url);
  for (const url of ['https://cdn.test/adventure/shadow.m3u8?token=ads&signature=a%2Fads%2Fb','https://cdn.test/adaptive/movie.mp4?is_ad=false','https://doubleclick.net.example.com/movie.m3u8']) assert.equal(c.advertisingUrl(url),false,url);
});
test('opaque advertising config and nested ad containers never become movie streams', async () => {
  const base='https://content.test', page=base+'/film';
  const html=`<div class="ad-container"><div><video src="https://cdn.test/opaque-a.mp4"></video></div><iframe src="https://player.test/opaque-ad"></iframe></div>
  <script>setup({file:"https://cdn.test/movie.m3u8?token=ads&exp=123",advertising:{client:'vast',schedule:[{tag:'https://cdn.test/opaque-b.mp4'}]}});</script>`;
  const {context:c,requests}=provider('kultfilmler',{[page]:html});
  const streams=await c.extractStreamsFromPage(base,page);
  assert.deepEqual(Array.from(streams,s=>s.url),['https://cdn.test/movie.m3u8?token=ads&exp=123']);
  assert.equal(requests.some(r=>r.url.includes('opaque-ad')),false);
});
test('mixed player source arrays exclude marked advertisements with opaque filenames', async () => {
  const base='https://content.test', page=base+'/film';
  const {context:c}=provider('kultfilmler',{[page]:'<script>var __PLAYER__={"sources":[{"master":"https://cdn.test/opaque.mp4","isAd":true},{"master":"https://cdn.test/movie.m3u8"}]};</script>'});
  const streams=await c.extractStreamsFromPage(base,page);
  assert.equal(streams.some(s=>s.url.includes('opaque.mp4')),false);
  assert.ok(streams.some(s=>s.url.endsWith('movie.m3u8')));
});
test('VAST media files and an advertising redirect are never returned as content', async () => {
  const base='https://content.test';
  const {context:c}=provider('kultfilmler',{[base+'/vast']:'<?xml version="1.0"?><VAST><Ad><MediaFile>https://cdn.test/opaque.mp4</MediaFile></Ad></VAST>'});
  assert.equal((await c.extractStreamsFromPage(base,base+'/vast')).length,0);
  const redirect=new Error('Direct media');redirect.directUrl='https://ads.test/opaque.mp4';redirect.mediaType='mp4';
  const {context:d}=provider('kultfilmler',{[base+'/redirect']:redirect});
  await assert.rejects(d.extractStreamsFromPage(base,base+'/redirect'));
});
