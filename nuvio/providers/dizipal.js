// Auto-generated Nuvio provider for DiziPal
// Live domains are automatically resolved from https://raw.githubusercontent.com/re-cox/cloudstream-dizi-repo/main/domains.json

const SITE_KEY = "DiziPal";
const PROVIDER_NAME = "DiziPal";
const FALLBACK_URL = "https://dizipal2150.com";
const SEARCH_MODE = "dizipal_api";
const DOMAINS_JSON_URL = "https://raw.githubusercontent.com/re-cox/cloudstream-dizi-repo/main/domains.json";
const TMDB_API_KEY = "439c478a771f35c05022f9feabcca01c";

const BLOCK_MARKERS = [
  "bilgi teknolojileri ve iletişim kurumu",
  "erişime engellenmiştir",
  "5651 sayılı kanun",
  "buy this domain",
  "sedoparking",
  "domain is for sale"
];

let cachedBaseUrl = null;
let cachedAt = 0;

async function isAlive(url, healthCheckText) {
  try {
    const res = await fetch(url, {
      headers: { "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36" }
    });
    if (!res.ok) return false;
    const text = (await res.text()).toLowerCase();
    if (text.length < 1500) return false;
    if (BLOCK_MARKERS.some((m) => text.includes(m))) return false;
    if (healthCheckText && !text.includes(healthCheckText.toLowerCase())) return false;
    return true;
  } catch (_) {
    return false;
  }
}

async function resolveLiveDomain() {
  const now = Date.now();
  if (cachedBaseUrl && now - cachedAt < 30 * 60 * 1000) {
    return cachedBaseUrl;
  }

  let cfg = null;
  try {
    const res = await fetch(DOMAINS_JSON_URL);
    if (res.ok) {
      const data = await res.json();
      cfg = data && data.sites ? data.sites[SITE_KEY] : null;
    }
  } catch (_) {}

  const primary = (cfg && cfg.currentUrl) ? cfg.currentUrl.replace(/\/+$/, "") : FALLBACK_URL;
  const health = cfg ? cfg.healthCheckText : null;

  if (await isAlive(primary, health)) {
    cachedBaseUrl = primary;
    cachedAt = now;
    return primary;
  }

  if (cfg && cfg.nextUrl) {
    const nextUrl = cfg.nextUrl.replace(/\/+$/, "");
    if (await isAlive(nextUrl, health)) {
      cachedBaseUrl = nextUrl;
      cachedAt = now;
      return nextUrl;
    }
  }

  if (cfg && Array.isArray(cfg.mirrors)) {
    for (const m of cfg.mirrors) {
      const candidate = String(m || "").replace(/\/+$/, "");
      if (!candidate || candidate === primary) continue;
      if (await isAlive(candidate, health)) {
        cachedBaseUrl = candidate;
        cachedAt = now;
        return candidate;
      }
    }
  }

  if (cfg && cfg.prefix && cfg.suffix && cfg.currentNumber) {
    for (let i = 1; i <= 5; i++) {
      const candidate = `${cfg.prefix}${cfg.currentNumber + i}${cfg.suffix}`.replace(/\/+$/, "");
      if (await isAlive(candidate, health)) {
        cachedBaseUrl = candidate;
        cachedAt = now;
        return candidate;
      }
    }
  }

  cachedBaseUrl = primary;
  cachedAt = now;
  return primary;
}

async function getTmdbTitles(tmdbId, mediaType) {
  const type = mediaType === "tv" ? "tv" : "movie";
  const titles = [];
  for (const lang of ["tr-TR", "en-US"]) {
    try {
      const res = await fetch(`https://api.themoviedb.org/3/${type}/${tmdbId}?api_key=${TMDB_API_KEY}&language=${lang}`);
      if (!res.ok) continue;
      const data = await res.json();
      const t = data.title || data.name;
      const orig = data.original_title || data.original_name;
      if (t && !titles.includes(t)) titles.push(t);
      if (orig && !titles.includes(orig)) titles.push(orig);
    } catch (_) {}
  }
  return titles;
}

function absolutize(baseUrl, href) {
  if (!href) return "";
  if (href.startsWith("http://") || href.startsWith("https://")) return href;
  if (href.startsWith("//")) return "https:" + href;
  return baseUrl.replace(/\/+$/, "") + (href.startsWith("/") ? href : "/" + href);
}

async function searchSite(baseUrl, query) {
  const headers = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
    "Referer": baseUrl + "/"
  };
  const encoded = encodeURIComponent(query);

  try {
    if (SEARCH_MODE === "dizipal_api") {
      const res = await fetch(`${baseUrl}/api/search-autocomplete`, {
        method: "POST",
        headers: { ...headers, "Content-Type": "application/x-www-form-urlencoded", "X-Requested-With": "XMLHttpRequest" },
        body: `query=${encoded}`
      });
      const obj = await res.json();
      return Object.values(obj || {}).map((item) => absolutize(baseUrl, item.url)).filter(Boolean);
    }

    if (SEARCH_MODE === "hdfc_api") {
      const res = await fetch(`${baseUrl}/search?q=${encoded}`, {
        headers: { ...headers, "X-Requested-With": "fetch" }
      });
      const json = await res.json();
      const links = [];
      for (const html of (json.results || [])) {
        const m = String(html).match(/href=["']([^"']+)["']/i);
        if (m) links.push(absolutize(baseUrl, m[1]));
      }
      return links;
    }

    if (SEARCH_MODE === "dizilla_api") {
      const res = await fetch(`${baseUrl}/bg/searchcontent`, {
        method: "POST",
        headers: { ...headers, "Content-Type": "application/x-www-form-urlencoded", "X-Requested-With": "XMLHttpRequest" },
        body: `query=${encoded}`
      });
      const html = await res.text();
      return [...html.matchAll(/href=["']([^"']*\/dizi\/[^"']+)["']/gi)].map((m) => absolutize(baseUrl, m[1]));
    }

    const searchUrlMap = {
      wp_search: `${baseUrl}/?s=${encoded}`,
      sezonluk_search: `${baseUrl}/diziler.asp?adi=${encoded}`,
      filmmodu_search: `${baseUrl}/film-ara?term=${encoded}`,
      fhdf_search: `${baseUrl}/arama/${encoded}`,
      filmmakinesi_search: `${baseUrl}/arama/?s=${encoded}`,
      webteizle_search: `${baseUrl}/film-robotu?q=${encoded}`,
      selcukflix_search: `${baseUrl}/arama?q=${encoded}`
    };

    const res = await fetch(searchUrlMap[SEARCH_MODE] || `${baseUrl}/?s=${encoded}`, { headers });
    const html = await res.text();
    const matches = [...html.matchAll(/<a[^>]+href=["']([^"']+)["'][^>]*>/gi)]
      .map((m) => absolutize(baseUrl, m[1]))
      .filter((u) => u.startsWith(baseUrl) && !u.includes("/tur/") && !u.includes("/kategori/") && !u.includes("?s="));
    return [...new Set(matches)].slice(0, 6);
  } catch (_) {
    return [];
  }
}

async function extractStreamsFromPage(baseUrl, pageUrl) {
  const headers = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
    "Referer": baseUrl + "/"
  };
  const streams = [];
  try {
    const res = await fetch(pageUrl, { headers });
    const html = await res.text();

    // Direct m3u8/mp4 links in page
    for (const m of html.matchAll(/(https?:\/\/[^"'\s<>]+\.(?:m3u8|mp4)[^"'\s<>]*)/gi)) {
      streams.push({
        name: PROVIDER_NAME,
        title: `${PROVIDER_NAME} • Direct`,
        url: m[1].replace(/\\\//g, "/"),
        quality: "HD",
        headers: { Referer: baseUrl + "/" }
      });
    }

    // Check embedded iframes for m3u8 sources
    const iframes = [...html.matchAll(/<iframe[^>]+(?:data-src|src)=["']([^"']+)["']/gi)]
      .map((m) => absolutize(baseUrl, m[1]))
      .slice(0, 4);

    for (const iframeUrl of iframes) {
      try {
        const iRes = await fetch(iframeUrl, { headers: { ...headers, Referer: pageUrl } });
        const iHtml = await iRes.text();
        for (const m of iHtml.matchAll(/(?:file|src)\s*:\s*["'](https?:\/\/[^"'\s]+\.(?:m3u8|mp4)[^"'\s]*)["']/gi)) {
          streams.push({
            name: PROVIDER_NAME,
            title: `${PROVIDER_NAME} • Player`,
            url: m[1].replace(/\\\//g, "/"),
            quality: "1080p",
            headers: { Referer: iframeUrl }
          });
        }
      } catch (_) {}
    }
  } catch (_) {}

  const seen = new Set();
  return streams.filter((s) => {
    if (!s.url || seen.has(s.url)) return false;
    seen.add(s.url);
    return true;
  });
}

async function getStreams(tmdbId, mediaType, season, episode) {
  const baseUrl = await resolveLiveDomain();
  const titles = await getTmdbTitles(tmdbId, mediaType);
  if (!titles.length) return [];

  let candidatePages = [];
  for (const title of titles) {
    candidatePages = await searchSite(baseUrl, title);
    if (candidatePages.length) break;
  }
  if (!candidatePages.length) return [];

  let targetUrl = candidatePages[0];

  // For TV shows, try to locate matching season/episode link on the series page
  if (mediaType === "tv" && season && episode) {
    try {
      const seriesRes = await fetch(targetUrl, {
        headers: { "User-Agent": "Mozilla/5.0", Referer: baseUrl + "/" }
      });
      const seriesHtml = await seriesRes.text();
      const epRegex = new RegExp(`href=["']([^"']*(?:sezon[-/]?${season}[^"']*bolum[-/]?${episode}|${season}-sezon-${episode}-bolum)[^"']*)["']`, "i");
      const epMatch = seriesHtml.match(epRegex);
      if (epMatch) {
        targetUrl = absolutize(baseUrl, epMatch[1]);
      }
    } catch (_) {}
  }

  return await extractStreamsFromPage(baseUrl, targetUrl);
}

module.exports = { getStreams };
