"""Read-only HTTP diagnostics. Reachable HTML is NOT a playback success.

Run: python scripts/check_sites.py --output site-health.json
Only configured currentUrl addresses are requested; no domain registry is changed.
"""
from __future__ import annotations

import argparse
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timezone
import html
import json
from pathlib import Path
import re
import socket
import ssl
import urllib.error

from update_domains import default_fetcher, is_blocked_or_parked

ROOT = Path(__file__).resolve().parent.parent


def check_site(item: tuple[str, dict], timeout: int = 12) -> dict:
    name, cfg = item
    result = {"site": name, "requestedUrl": cfg["currentUrl"], "playbackVerified": False}
    try:
        code, final_url, body = default_fetcher(cfg["currentUrl"], timeout)
        title = re.search(r"<title[^>]*>(.*?)</title>", body, re.I | re.S)
        expected = cfg.get("healthCheckText", "").lower()
        status = "reachable_html"
        if is_blocked_or_parked(body):
            status = "blocked_or_parked"
        elif len(body) < 500 or (expected and expected not in body.lower()):
            status = "unexpected_content"
        result.update(status=status, httpStatus=code, finalUrl=final_url, bodyLength=len(body),
                      title=html.unescape(title.group(1).strip()) if title else None)
    except urllib.error.HTTPError as error:
        result.update(status="http_error", httpStatus=error.code, error=str(error))
    except (ssl.SSLError, socket.timeout, TimeoutError, urllib.error.URLError, OSError) as error:
        reason = getattr(error, "reason", error)
        status = "network_error"
        if isinstance(reason, ssl.SSLError):
            status = "tls_error"
        elif isinstance(reason, (socket.timeout, TimeoutError)):
            status = "timeout"
        result.update(status=status, error=str(error))
    return result


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    sites = json.loads((ROOT / "domains.json").read_text(encoding="utf-8"))["sites"]
    with ThreadPoolExecutor(max_workers=13) as pool:
        results = list(pool.map(check_site, sites.items()))
    report = {"checkedAt": datetime.now(timezone.utc).isoformat(),
              "scope": "HTTP home pages only; not search, extraction or playback", "results": results}
    text = json.dumps(report, ensure_ascii=False, indent=2) + "\n"
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(text, encoding="utf-8")
    # ASCII output also works in Windows terminals configured for legacy encodings.
    for result in results:
        print(f"{result['site']}: {result['status']} ({result.get('httpStatus', '-')})")


if __name__ == "__main__":
    main()
