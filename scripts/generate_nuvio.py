#!/usr/bin/env python3
"""
Generates `nuvio/manifest.json` and standalone Nuvio JS scrapers under
`nuvio/providers/` for all 13 Turkish sites configured in `domains.json`.

Each generated JS scraper is self-contained (as required by Nuvio) and
dynamically fetches the live domain from `domains.json` at runtime, with
request-failure failover to `nextUrl`, `mirrors`, and sequential numbers.
Site-specific players still require individual extraction support.
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

PROVIDER_TEMPLATE = (ROOT / "scripts" / "vendor" / "crypto-js.min.js").read_text(encoding="utf-8") + "\n" + (ROOT / "scripts" / "nuvio_provider.js").read_text(encoding="utf-8")


def main() -> None:
    domains_data = json.loads((ROOT / "domains.json").read_text(encoding="utf-8"))
    sites_cfg = domains_data["sites"]

    PROVIDERS_DIR.mkdir(parents=True, exist_ok=True)
    scrapers = []

    for meta in SITES_META:
        site_cfg = sites_cfg[meta["siteKey"]]
        filename = f"providers/{meta['id']}.js"
        js_code = PROVIDER_TEMPLATE
        replacements = {
            "__SITE_KEY__": meta["siteKey"],
            "__PROVIDER_NAME__": meta["name"],
            "__FALLBACK_CONFIG__": site_cfg,
            "__SEARCH_MODE__": meta["searchMode"],
            "__DOMAINS_JSON_URL__": DOMAINS_RAW_URL,
            "__SUPPORTED_TYPES__": meta["supportedTypes"],
        }
        for token, value in replacements.items():
            js_code = js_code.replace(token, json.dumps(value, ensure_ascii=False))
        (NUVIO_DIR / filename).write_text(js_code, encoding="utf-8")
        domain_host = site_cfg["currentUrl"].replace("https://", "").replace("http://", "").strip("/")
        scrapers.append(
            {
                "id": meta["id"],
                "name": meta["name"],
                "description": meta["description"],
                "version": "1.3.0",
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
        "name": "re-cox TR (Nuvio JS)",
        "description": "Otomatik domain takipli Türkçe dizi ve film sağlayıcıları",
        "version": "1.1.0",
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
