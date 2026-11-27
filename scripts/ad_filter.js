// Generated into every standalone provider. Only explicit advertising markers count.
const AD_POLICY = __AD_POLICY__;
function advertisingUrl(value) {
  let raw = decodeHtml(value).replace(/\\\//g, '/');
  const match = raw.match(/^https?:\/\/([^/?#]+)([^?#]*)(?:\?([^#]*))?/i);
  if (!match) return false;
  const host = match[1].toLowerCase().split(':')[0];
  if (AD_POLICY.hostSuffixes.some(h => host === h || host.endsWith('.' + h)) ||
      host.split('.').some(label => AD_POLICY.hostLabels.includes(label))) return true;
  let path = match[2];
  try { path = decodeURIComponent(path); } catch (_) {}
  const tokens = path.toLowerCase().split(/[/_.-]+/);
  if (AD_POLICY.pathTokens.some(t => tokens.includes(t) ||
      (t.includes('-') && path.toLowerCase().split('/').some(p => p === t || p.startsWith(t + '.'))))) return true;
  for (const pair of (match[3] || '').split('&')) {
    let [key, val = ''] = pair.split('=');
    try { key = decodeURIComponent(key); val = decodeURIComponent(val); } catch (_) {}
    key = key.toLowerCase(); val = val.toLowerCase();
    if (AD_POLICY.queryFlags.includes(key) && ['1', 'true', 'yes'].includes(val)) return true;
    if (['type', 'media_type', 'content_type'].includes(key) && AD_POLICY.queryTypes.includes(val)) return true;
  }
  return false;
}
function advertisingSource(source) {
  return source && (source.isAd === true || source.is_ad === true ||
    ['ad', 'advertisement', 'preroll', 'midroll', 'postroll', 'vast', 'vpaid'].includes(String(source.type || source.kind || '').toLowerCase()));
}
// Find the end of a JS string/object/array without evaluating downloaded scripts.
function configValueEnd(text, start) {
  let quote = null, escaped = false, depth = 0;
  const scalar = !['{', '['].includes(text[start]);
  if (scalar && !['"', "'", '`'].includes(text[start])) return start;
  for (let i = start; i < text.length; i++) {
    const ch = text[i];
    if (quote) {
      if (escaped) escaped = false;
      else if (ch === '\\') escaped = true;
      else if (ch === quote) { quote = null; if (scalar) return i + 1; }
    } else if (['"', "'", '`'].includes(ch)) quote = ch;
    else if (ch === '{' || ch === '[') depth++;
    else if ((ch === '}' || ch === ']') && --depth === 0) return i + 1;
  }
  return start;
}
function withoutAdvertising(html) {
  if (/^\s*(?:<\?xml[\s\S]*?\?>\s*|<!--[\s\S]*?-->\s*)*<(?:VAST|(?:\w+:)?VMAP)\b/i.test(html)) return '';
  // Dedicated ad containers can hold opaque CDN media URLs with no ad-like filename.
  const tags = /<(div|section|aside|iframe|video|source|script)\b[^>]*>/gi;
  const cuts = [];
  for (const match of html.matchAll(tags)) {
    const marker = (attr(match[0], 'class') + ' ' + attr(match[0], 'id')).toLowerCase();
    if (!/(?:^|\s)(?:ads?|advertisement|advertising|reklam)(?:$|\s|[-_](?:container|banner|slot|player|video|wrapper)(?:$|\s))/.test(marker) &&
        !/\bdata-ad(?:vertisement)?\s*=\s*["'](?:true|1)["']/i.test(match[0])) continue;
    let end = match.index + match[0].length;
    if (!['source'].includes(match[1].toLowerCase()) && !/\/\s*>$/.test(match[0])) {
      const nested = new RegExp('<(/?)' + match[1] + '\\b[^>]*>', 'gi');
      nested.lastIndex = end;
      let level = 1, next;
      while ((next = nested.exec(html))) {
        level += next[1] ? -1 : 1;
        if (!level) { end = nested.lastIndex; break; }
      }
    }
    cuts.push([match.index, end]);
  }
  for (const [start, end] of cuts.sort((a,b) => b[0]-a[0])) html = html.slice(0,start) + ' '.repeat(end-start) + html.slice(end);
  const config = /(?:[,{;]|\bvar\s+|\blet\s+|\bconst\s+)\s*["']?(?:advertising|adschedule|adconfig|ads|preroll|midroll|postroll|adTagUrl|ad_url|vast_url|preroll_url|vastxml|vastTag|vpaid)["']?\s*[:=]\s*/gi;
  const ranges = [];
  for (const match of html.matchAll(config)) {
    const start = match.index + match[0].length, end = configValueEnd(html, start);
    if (end > start) ranges.push([start, end]);
  }
  for (const [start,end] of ranges.sort((a,b)=>b[0]-a[0])) html = html.slice(0,start) + '0' + ' '.repeat(end-start-1) + html.slice(end);
  // A mixed source array may put an opaque ad file beside the actual movie.
  const marked = /["']?(?:isAd|is_ad)["']?\s*:\s*true\b|["']?(?:type|kind)["']?\s*:\s*["'](?:ad|advertisement|preroll|midroll|postroll|vast|vpaid)["']/gi;
  const objects = [];
  for (const match of html.matchAll(marked)) {
    let start = html.lastIndexOf('{', match.index);
    for (let attempts = 0; start >= 0 && attempts < 32; attempts++, start = html.lastIndexOf('{', start - 1)) {
      const end = configValueEnd(html, start);
      if (end > match.index + match[0].length) { objects.push([start,end]); break; }
    }
  }
  for (const [start,end] of objects.sort((a,b)=>b[0]-a[0])) html = html.slice(0,start) + '{}' + ' '.repeat(end-start-2) + html.slice(end);
  return html;
}
