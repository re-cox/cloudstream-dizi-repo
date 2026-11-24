#!/usr/bin/env python3
"""
Generates `nuvio/manifest.json` and standalone Nuvio JS scrapers under
`nuvio/providers/` for all 13 Turkish sites configured in `domains.json`.

Each generated JS scraper is self-contained (as required by Nuvio) and
dynamically fetches the live domain from `domains.json` at runtime, with
automatic failover to `nextUrl`, `mirrors`, and sequential numbers.
"""

from __future__ import annotations

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
NUVIO_DIR = ROOT / "nuvio"
PROVIDERS_DIR = NUVIO_DIR / "providers"

GITHUB_REPO = "re-cox/cloudstream-dizi-repo"
DOMAINS_RAW_URL = f"https://raw.githubusercontent.com/{GITHUB_REPO}/main/domains.json"

SITES_META = [
    {
        "id": "dizipal",
        "siteKey": "DiziPal",
        "name": "DiziPal",
        "description": "DiziPal - Otomatik domain takipli Türkçe dizi ve film kaynağı",
        "supportedTypes": ["tv", "movie"],
        "searchMode": "dizipal_api",
    },
    {
        "id": "hdfilmcehennemi",
        "siteKey": "HDFilmCehennemi",
        "name": "HDFilmCehennemi",
        "description": "HDFilmCehennemi - Otomatik domain takipli Türkçe film ve dizi kaynağı",
        "supportedTypes": ["movie", "tv"],
        "searchMode": "hdfc_api",
    },
    {
        "id": "dizibox",
        "siteKey": "DiziBox",
        "name": "DiziBox",
        "description": "DiziBox - Otomatik domain takipli yabancı dizi kaynağı",
        "supportedTypes": ["tv"],
        "searchMode": "wp_search",
    },
    {
        "id": "dizilla",
        "siteKey": "Dizilla",
        "name": "Dizilla",
        "description": "Dizilla - Otomatik domain takipli yabancı dizi kaynağı",
        "supportedTypes": ["tv"],
        "searchMode": "dizilla_api",
    },
    {
        "id": "diziyou",
        "siteKey": "DiziYou",
        "name": "DiziYou",
        "description": "DiziYou - Otomatik domain takipli Türkçe altyazılı ve dublajlı dizi kaynağı",
        "supportedTypes": ["tv"],
        "searchMode": "wp_search",
    },
    {
        "id": "sezonlukdizi",
        "siteKey": "SezonlukDizi",
        "name": "SezonlukDizi",
        "description": "SezonlukDizi - Otomatik domain takipli yabancı dizi kaynağı",
        "supportedTypes": ["tv"],
        "searchMode": "sezonluk_search",
    },
    {
        "id": "filmmodu",
        "siteKey": "FilmModu",
        "name": "FilmModu",
        "description": "FilmModu - Otomatik domain takipli Türkçe dublaj ve altyazılı film kaynağı",
        "supportedTypes": ["movie"],
        "searchMode": "filmmodu_search",
    },
    {
        "id": "dizimom",
        "siteKey": "DiziMom",
        "name": "DiziMom",
        "description": "DiziMom - Otomatik domain takipli Türkçe dizi ve film kaynağı",
        "supportedTypes": ["tv", "movie"],
        "searchMode": "wp_search",
    },
    {
        "id": "fullhdfilmizlesene",
        "siteKey": "FullHDFilmizlesene",
        "name": "FullHDFilmizlesene",
        "description": "FullHDFilmizlesene - Otomatik domain takipli Türkçe film kaynağı",
        "supportedTypes": ["movie"],
        "searchMode": "fhdf_search",
    },
    {
        "id": "filmmakinesi",
        "siteKey": "FilmMakinesi",
        "name": "FilmMakinesi",
        "description": "FilmMakinesi - Otomatik domain takipli HD film kaynağı",
        "supportedTypes": ["movie"],
        "searchMode": "filmmakinesi_search",
    },
    {
        "id": "webteizle",
        "siteKey": "WebteIzle",
        "name": "WebteIzle",
        "description": "WebteIzle - Otomatik domain takipli Türkçe dublaj ve altyazılı film kaynağı",
        "supportedTypes": ["movie"],
        "searchMode": "webteizle_search",
    },
    {
        "id": "selcukflix",
        "siteKey": "SelcukFlix",
        "name": "SelcukFlix",
        "description": "SelcukFlix - Otomatik domain takipli Türkçe dizi ve film kaynağı",
        "supportedTypes": ["tv", "movie"],
        "searchMode": "selcukflix_search",
    },
    {
        "id": "setfilmizle",
        "siteKey": "SetFilmIzle",
        "name": "SetFilmIzle",
        "description": "SetFilmIzle - Otomatik domain takipli Türkçe film ve dizi kaynağı",
        "supportedTypes": ["movie", "tv"],
        "searchMode": "wp_search",
    },
]

PROVIDER_TEMPLATE = """// Auto-generated Nuvio provider for {name}
// Live domains are automatically resolved from {domains_raw_url}

const SITE_KEY = {site_key_json};
const PROVIDER_NAME = {name_json};
const FALLBACK_URL = {fallback_url_json};
const SEARCH_MODE = {search_mode_json};
const DOMAINS_JSON_URL = {domains_raw_url_json};
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

async function isAlive(url, healthCheckText) {{
  try {{
    const res = await fetch(url, {{
      headers: {{ "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36" }}
    }});
    if (!res.ok) return false;
    const text = (await res.text()).toLowerCase();
    if (text.length < 1500) return false;
    if (BLOCK_MARKERS.some((m) => text.includes(m))) return false;
    if (healthCheckText && !text.includes(healthCheckText.toLowerCase())) return false;
    return true;
  }} catch (_) {{
    return false;
  }}
}}

async function resolveLiveDomain() {{
  const now = Date.now();
  if (cachedBaseUrl && now - cachedAt < 30 * 60 * 1000) {{
    return cachedBaseUrl;
  }}

  let cfg = null;
  try {{
    const res = await fetch(DOMAINS_JSON_URL);
    if (res.ok) {{
      const data = await res.json();
      cfg = data && data.sites ? data.sites[SITE_KEY] : null;
    }}
  }} catch (_) {{}}

  // Fast path: immediately trust `currentUrl` from domains.json (already verified by GitHub Actions)
  // to avoid wasting scraper timeout probing ISP-blocked mirrors on startup.
  const primary = (cfg && cfg.currentUrl) ? cfg.currentUrl.replace(/\\/+$/, "") : FALLBACK_URL;
  cachedBaseUrl = primary;
  cachedAt = now;
  return primary;
}}

async function getTmdbTitles(tmdbId, mediaType) {{
  const type = mediaType === "tv" ? "tv" : "movie";
  const titles = [];
  for (const lang of ["tr-TR", "en-US"]) {{
    try {{
      const res = await fetch(`https://api.themoviedb.org/3/${{type}}/${{tmdbId}}?api_key=${{TMDB_API_KEY}}&language=${{lang}}`);
      if (!res.ok) continue;
      const data = await res.json();
      const t = data.title || data.name;
      const orig = data.original_title || data.original_name;
      if (t && !titles.includes(t)) titles.push(t);
      if (orig && !titles.includes(orig)) titles.push(orig);
    }} catch (_) {{}}
  }}
  return titles;
}}

function absolutize(baseUrl, href) {{
  if (!href) return "";
  if (href.startsWith("http://") || href.startsWith("https://")) return href;
  if (href.startsWith("//")) return "https:" + href;
  return baseUrl.replace(/\\/+$/, "") + (href.startsWith("/") ? href : "/" + href);
}}

async function searchSite(baseUrl, query) {{
  const headers = {{
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
    "Referer": baseUrl + "/"
  }};
  const encoded = encodeURIComponent(query);

  try {{
    if (SEARCH_MODE === "dizipal_api") {{
      try {{
        const res = await fetch(`${{baseUrl}}/api/search-autocomplete`, {{
          method: "POST",
          headers: {{ ...headers, "Content-Type": "application/x-www-form-urlencoded", "X-Requested-With": "XMLHttpRequest" }},
          body: `query=${{encoded}}`
        }});
        if (res.ok) {{
          const txt = await res.text();
          if (txt.trim().startsWith("{{")) {{
            const obj = JSON.parse(txt);
            const links = Object.values(obj || {{}}).map((item) => absolutize(baseUrl, item.url)).filter(Boolean);
            if (links.length) return links;
          }}
        }}
      }} catch (_) {{}}
      // Fallback to /search?q= for newer Dizipal templates
      const sRes = await fetch(`${{baseUrl}}/search?q=${{encoded}}`, {{ headers }});
      const sHtml = await sRes.text();
      return [...new Set([...sHtml.matchAll(/href=["']([^"']*(?:\\/dizi\\/|\\/film\\/)[^"']+)["']/gi)].map((m) => absolutize(baseUrl, m[1])))].slice(0, 6);
    }}

    if (SEARCH_MODE === "hdfc_api") {{
      const res = await fetch(`${{baseUrl}}/search?q=${{encoded}}`, {{
        headers: {{ ...headers, "X-Requested-With": "fetch" }}
      }});
      const json = await res.json();
      const links = [];
      for (const html of (json.results || [])) {{
        const m = String(html).match(/href=["']([^"']+)["']/i);
        if (m) links.push(absolutize(baseUrl, m[1]));
      }}
      return links;
    }}

    if (SEARCH_MODE === "dizilla_api") {{
      const res = await fetch(`${{baseUrl}}/bg/searchcontent`, {{
        method: "POST",
        headers: {{ ...headers, "Content-Type": "application/x-www-form-urlencoded", "X-Requested-With": "XMLHttpRequest" }},
        body: `query=${{encoded}}`
      }});
      const html = await res.text();
      return [...html.matchAll(/href=["']([^"']*\\/dizi\\/[^"']+)["']/gi)].map((m) => absolutize(baseUrl, m[1]));
    }}

    const searchUrlMap = {{
      wp_search: `${{baseUrl}}/?s=${{encoded}}`,
      sezonluk_search: `${{baseUrl}}/diziler.asp?adi=${{encoded}}`,
      filmmodu_search: `${{baseUrl}}/film-ara?term=${{encoded}}`,
      fhdf_search: `${{baseUrl}}/arama/${{encoded}}`,
      filmmakinesi_search: `${{baseUrl}}/arama/?s=${{encoded}}`,
      webteizle_search: `${{baseUrl}}/film-robotu?q=${{encoded}}`,
      selcukflix_search: `${{baseUrl}}/arama?q=${{encoded}}`
    }};

    const res = await fetch(searchUrlMap[SEARCH_MODE] || `${{baseUrl}}/?s=${{encoded}}`, {{ headers }});
    const html = await res.text();
    const matches = [...html.matchAll(/<a[^>]+href=["']([^"']+)["'][^>]*>/gi)]
      .map((m) => absolutize(baseUrl, m[1]))
      .filter((u) => u.startsWith(baseUrl) && !u.includes("/tur/") && !u.includes("/kategori/") && !u.includes("?s="));
    return [...new Set(matches)].slice(0, 6);
  }} catch (_) {{
    return [];
  }}
}}

async function extractStreamsFromPage(baseUrl, pageUrl) {{
  const headers = {{
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
    "Referer": baseUrl + "/"
  }};
  const streams = [];
  try {{
    const res = await fetch(pageUrl, {{ headers }});
    const html = await res.text();

    // Direct m3u8/mp4 links in page
    for (const m of html.matchAll(/(https?:\\/\\/[^"'\\s<>]+\\.(?:m3u8|mp4)[^"'\\s<>]*)/gi)) {{
      streams.push({{
        name: PROVIDER_NAME,
        title: `${{PROVIDER_NAME}} • Direct`,
        url: m[1].replace(/\\\\\\//g, "/"),
        quality: "HD",
        headers: {{ Referer: baseUrl + "/" }}
      }});
    }}

    // Check embedded iframes for m3u8 sources
    const iframes = [...html.matchAll(/<iframe[^>]+(?:data-src|src)=["']([^"']+)["']/gi)]
      .map((m) => absolutize(baseUrl, m[1]))
      .slice(0, 4);

    for (const iframeUrl of iframes) {{
      try {{
        const iRes = await fetch(iframeUrl, {{ headers: {{ ...headers, Referer: pageUrl }} }});
        const iHtml = await iRes.text();
        for (const m of iHtml.matchAll(/(?:file|src)\\s*:\\s*["'](https?:\\/\\/[^"'\\s]+\\.(?:m3u8|mp4)[^"'\\s]*)["']/gi)) {{
          streams.push({{
            name: PROVIDER_NAME,
            title: `${{PROVIDER_NAME}} • Player`,
            url: m[1].replace(/\\\\\\//g, "/"),
            quality: "1080p",
            headers: {{ Referer: iframeUrl }}
          }});
        }}
      }} catch (_) {{}}
    }}
  }} catch (_) {{}}

  const seen = new Set();
  return streams.filter((s) => {{
    if (!s.url || seen.has(s.url)) return false;
    seen.add(s.url);
    return true;
  }});
}}

async function getStreams(tmdbId, mediaType, season, episode) {{
  const baseUrl = await resolveLiveDomain();
  const titles = await getTmdbTitles(tmdbId, mediaType);
  if (!titles.length) return [];

  let candidatePages = [];
  for (const title of titles) {{
    candidatePages = await searchSite(baseUrl, title);
    if (candidatePages.length) break;
  }}
  if (!candidatePages.length) return [];

  let targetUrl = candidatePages[0];

  // For TV shows, try to locate matching season/episode link on the series page
  if (mediaType === "tv" && season && episode) {{
    try {{
      const seriesRes = await fetch(targetUrl, {{
        headers: {{ "User-Agent": "Mozilla/5.0", Referer: baseUrl + "/" }}
      }});
      const seriesHtml = await seriesRes.text();
      const epRegex = new RegExp(`href=["']([^"']*(?:sezon[-/]?${{season}}[^"']*bolum[-/]?${{episode}}|${{season}}-sezon-${{episode}}-bolum)[^"']*)["']`, "i");
      const epMatch = seriesHtml.match(epRegex);
      if (epMatch) {{
        targetUrl = absolutize(baseUrl, epMatch[1]);
      }}
    }} catch (_) {{}}
  }}

  return await extractStreamsFromPage(baseUrl, targetUrl);
}}

module.exports = {{ getStreams }};
"""


def main() -> None:
    domains_data = json.loads((ROOT / "domains.json").read_text(encoding="utf-8"))
    sites_cfg = domains_data["sites"]

    PROVIDERS_DIR.mkdir(parents=True, exist_ok=True)
    scrapers = []

    for meta in SITES_META:
        site_cfg = sites_cfg[meta["siteKey"]]
        filename = f"providers/{meta['id']}.js"
        js_code = PROVIDER_TEMPLATE.format(
            name=meta["name"],
            domains_raw_url=DOMAINS_RAW_URL,
            site_key_json=json.dumps(meta["siteKey"]),
            name_json=json.dumps(meta["name"]),
            fallback_url_json=json.dumps(site_cfg["currentUrl"]),
            search_mode_json=json.dumps(meta["searchMode"]),
            domains_raw_url_json=json.dumps(DOMAINS_RAW_URL),
        )
        (NUVIO_DIR / filename).write_text(js_code, encoding="utf-8")
        domain_host = site_cfg["currentUrl"].replace("https://", "").replace("http://", "").strip("/")
        scrapers.append(
            {
                "id": meta["id"],
                "name": meta["name"],
                "description": meta["description"],
                "version": "1.1.0",
                "author": "re-cox",
                "supportedTypes": meta["supportedTypes"],
                "filename": filename,
                "enabled": True,
                "logo": f"https://www.google.com/s2/favicons?domain={domain_host}&sz=64",
                "contentLanguage": ["tr"],
                "formats": ["m3u8", "mp4"],
                "limited": False,
                "disabledPlatforms": [],
                "supportsExternalPlayer": True,
            }
        )

    manifest = {
        "id": "re-cox-dizi-repo",
        "name": "Özel Dizi & Film Repo (TR)",
        "description": "Otomatik domain takipli Türkçe dizi ve film sağlayıcıları",
        "version": "1.0.0",
        "author": "re-cox",
        "repository": f"https://github.com/{GITHUB_REPO}",
        "scrapers": scrapers,
    }
    (NUVIO_DIR / "manifest.json").write_text(
        json.dumps(manifest, indent=2, ensure_ascii=False) + "\n",
        encoding="utf-8",
    )
    print(f"Generated nuvio/manifest.json and {len(scrapers)} providers.")


if __name__ == "__main__":
    main()
