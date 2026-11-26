#!/usr/bin/env python3
"""
Automatic domain tracker for CloudStream Turkish dizi/film providers.

Supports four tracking strategies per site in `domains.json`:
1. `sequential_and_announcement`:
   - Checks `currentUrl`. If alive, scans HTML with `announcementRegex`
     (e.g. "Sonraki adresimiz Dizipal2151.com olacaktır") and saves `nextUrl`.
   - If `currentUrl` is down/blocked, checks `nextUrl`, then `mirrors`, then
     sequential numbers (`currentNumber + 1` .. `currentNumber + maxProbe`).
2. `mirror_and_redirect`:
   - Checks `currentUrl` (follows valid HTTP redirects and rejects BTK/parking pages).
   - If down/blocked, tests `nextUrl` and each URL in `mirrors` (known TLD rotations).
   - Automatically prepends newly discovered live domains to `mirrors`.
3. `redirect`:
   - Follows HTTP redirects from `redirectSourceUrl` to find the active domain.
4. `telegram`:
   - Fetches public Telegram web preview (`https://t.me/s/<telegramChannel>`)
     and extracts the latest domain matching `telegramRegex`.
"""

from __future__ import annotations

import argparse
import datetime
import json
import os
import re
import ssl
import sys
import time
from concurrent.futures import ThreadPoolExecutor
from domain_health import POLICY, valid_origin, identity_error, error_status, body_status
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path
from typing import Callable, Optional, Tuple

USER_AGENT = (
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
    "AppleWebKit/537.36 (KHTML, like Gecko) "
    "Chrome/124.0.0.0 Safari/537.36"
)

BLOCK_AND_PARKING_MARKERS = tuple(POLICY['blockedMarkers'])
MIN_VALID_BODY_LENGTH = POLICY['minBodyLength']


def normalize_origin(url: str) -> str:
    """Normalize a domain or URL to `https://host` without trailing slash."""
    raw = url.strip()
    if not raw:
        return ""
    if not re.match(r"^https?://", raw, flags=re.IGNORECASE):
        raw = f"https://{raw}"
    return valid_origin(raw)


def extract_domain_number(url: str) -> Optional[int]:
    """Extract trailing digits before the TLD (e.g. dizipal2150.com -> 2150)."""
    parsed = urllib.parse.urlparse(normalize_origin(url))
    match = re.search(r"(\d+)\.[a-zA-Z]{2,}$", parsed.netloc)
    return int(match.group(1)) if match else None


def is_blocked_or_parked(html: str) -> bool:
    lower = html.lower()
    return any(marker in lower for marker in BLOCK_AND_PARKING_MARKERS)


def default_fetcher(url: str, timeout: int = 12) -> Tuple[int, str, str]:
    """
    Fetch URL and return (status_code, final_url, body_text).
    Raises urllib.error.URLError / OSError on network failure.
    """
    req = urllib.request.Request(
        url,
        headers={
            "User-Agent": USER_AGENT,
            "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
            "Accept-Language": "tr-TR,tr;q=0.9,en;q=0.8",
        },
    )
    ctx = ssl.create_default_context()
    with urllib.request.urlopen(req, timeout=timeout, context=ctx) as resp:
        status = getattr(resp, "status", 200)
        final_url = resp.geturl()
        raw_bytes = resp.read(512_000)
        encoding = resp.headers.get_content_charset() or "utf-8"
        text = raw_bytes.decode(encoding, errors="replace")
        return status, final_url, text


def probe_url(
    url: str,
    health_check_text: Optional[str],
    fetcher: Callable[[str, int], Tuple[int, str, str]] = default_fetcher,
    timeout: int = 12,
    min_length: int = MIN_VALID_BODY_LENGTH,
    config: Optional[dict] = None,
    observations: Optional[list] = None,
) -> Optional[Tuple[str, str]]:
    """
    Check if `url` is alive and valid (not BTK-blocked or domain-parked).
    Returns (normalized_final_origin, body_text) if healthy, otherwise None.
    """
    observation = {"url": url}
    try:
        status, final_url, body = fetcher(url, timeout)
        observation.update(httpStatus=status, finalUrl=final_url)
        state = body_status(status, body, health_check_text, min_length)
        if state == 'healthy':
            state = identity_error(url, final_url, body, config or {}) or 'healthy'
    except Exception as error:
        state = error_status(error)
        body, final_url = '', url
    observation['status'] = state
    if observations is not None:
        observations.append(observation)
    return (normalize_origin(final_url), body) if state == 'healthy' else None


def extract_announcement_next_url(html: str, regex_pattern: Optional[str]) -> Optional[str]:
    if not regex_pattern:
        return None
    match = re.search(regex_pattern, html, flags=re.IGNORECASE)
    if not match:
        return None
    captured = match.group(1) if match.lastindex else match.group(0)
    normalized = normalize_origin(captured)
    return normalized or None


def update_site_entry(
    site_name: str,
    cfg: dict,
    fetcher: Callable[[str, int], Tuple[int, str, str]] = default_fetcher,
    min_length: int = MIN_VALID_BODY_LENGTH,
    observations: Optional[list] = None,
) -> Tuple[dict, bool, str]:
    """
    Check and update a single site config.
    Returns (updated_cfg, changed, status_message).
    """
    updated = dict(cfg)
    if "mirrors" in cfg and isinstance(cfg["mirrors"], list):
        updated["mirrors"] = list(cfg["mirrors"])

    deadline = time.monotonic() + 45
    def checked_probe(url, health_text, **kwargs):
        remaining = deadline - time.monotonic()
        if remaining <= 0:
            if observations is not None and not any(o['status'] == 'budget_exhausted' for o in observations):
                observations.append({'url': url, 'status': 'budget_exhausted'})
            return None
        return probe_url(url, health_text, timeout=max(1, min(12, int(remaining))), **kwargs)

    strategy = cfg.get("strategy", "sequential_and_announcement")
    current_url = normalize_origin(cfg.get("currentUrl", ""))
    next_url = normalize_origin(cfg.get("nextUrl", "")) if cfg.get("nextUrl") else None
    health_text = cfg.get("healthCheckText")
    announcement_regex = cfg.get("announcementRegex")

    def apply_live(live_origin: str, body: str, note: str) -> Tuple[dict, bool, str]:
        updated["currentUrl"] = live_origin
        if live_origin != current_url:
            updated['previousUrl'] = current_url
            updated['verifiedAt'] = datetime.datetime.now(datetime.timezone.utc).strftime('%Y-%m-%dT%H:%M:%SZ')
            updated['sourceType'] = 'verified_probe'
            updated['sourceUrl'] = live_origin
        # A redirect to another TLD (e.g. dizipal1.rest) must not reset the
        # configured .com sequence from 2150 to 1.
        if cfg.get("prefix") and cfg.get("suffix"):
            sequence = re.fullmatch(re.escape(cfg["prefix"]) + r"(\d+)" + re.escape(cfg["suffix"]), live_origin)
            if sequence:
                updated["currentNumber"] = int(sequence.group(1))

        # Keep newly discovered live domain at the front of `mirrors` when present
        if isinstance(updated.get("mirrors"), list):
            normalized_mirrors = [normalize_origin(m) for m in updated["mirrors"] if normalize_origin(m)]
            if live_origin not in normalized_mirrors:
                updated["mirrors"] = [live_origin] + normalized_mirrors

        announced = extract_announcement_next_url(body, announcement_regex)
        if announced and announced != live_origin and (not health_text or health_text.lower() in urllib.parse.urlsplit(announced).hostname):
            updated["nextUrl"] = announced
        elif updated.get("nextUrl") == live_origin:
            updated["nextUrl"] = None

        changed = updated != cfg
        return updated, changed, note

    # Strategy: redirect
    if strategy == "redirect" and cfg.get("redirectSourceUrl"):
        result = checked_probe(cfg["redirectSourceUrl"], health_text, fetcher=fetcher, min_length=min_length, config=cfg, observations=observations)
        if result:
            live_origin, body = result
            return apply_live(live_origin, body, f"{site_name}: resolved via redirect -> {live_origin}")

    # Strategy: telegram
    if strategy == "telegram" and cfg.get("telegramChannel") and cfg.get("telegramRegex"):
        tg_url = f"https://t.me/s/{cfg['telegramChannel']}"
        try:
            _, _, tg_html = fetcher(tg_url, 12)
            matches = re.findall(cfg["telegramRegex"], tg_html, flags=re.IGNORECASE)
            for candidate in reversed(matches):
                candidate_origin = normalize_origin(candidate)
                result = checked_probe(candidate_origin, health_text, fetcher=fetcher, min_length=min_length, config=cfg, observations=observations)
                if result:
                    live_origin, body = result
                    return apply_live(live_origin, body, f"{site_name}: resolved via telegram -> {live_origin}")
        except Exception:
            pass

    tried = set()

    # 1. Check currentUrl first (follows HTTP redirects)
    if current_url:
        tried.add(current_url)
        result = checked_probe(current_url, health_text, fetcher=fetcher, min_length=min_length, config=cfg, observations=observations)
        if result:
            live_origin, body = result
            note = (
                f"{site_name}: redirected {current_url} -> {live_origin}"
                if live_origin != current_url
                else f"{site_name}: active at {live_origin}"
            )
            return apply_live(live_origin, body, note)

    # 2. Current URL is down -> try announced nextUrl
    if next_url and next_url not in tried:
        tried.add(next_url)
        result = checked_probe(next_url, health_text, fetcher=fetcher, min_length=min_length, config=cfg, observations=observations)
        if result:
            live_origin, body = result
            return apply_live(live_origin, body, f"{site_name}: moved to announced nextUrl -> {live_origin}")

    # 3. Try configured mirrors / TLD rotations
    for raw_mirror in cfg.get("mirrors", []):
        mirror = normalize_origin(raw_mirror)
        if not mirror or mirror in tried:
            continue
        tried.add(mirror)
        result = checked_probe(mirror, health_text, fetcher=fetcher, min_length=min_length, config=cfg, observations=observations)
        if result:
            live_origin, body = result
            return apply_live(live_origin, body, f"{site_name}: moved to mirror -> {live_origin}")

    # 4. Sequential probing (N+1 .. N+maxProbe)
    prefix = cfg.get("prefix")
    suffix = cfg.get("suffix")
    current_num = cfg.get("currentNumber") or extract_domain_number(current_url)
    max_probe = int(cfg.get("maxProbe", 15))

    if prefix and suffix and current_num is not None:
        for offset in range(1, max_probe + 1):
            candidate = normalize_origin(f"{prefix}{current_num + offset}{suffix}")
            if candidate in tried:
                continue
            tried.add(candidate)
            result = checked_probe(candidate, health_text, fetcher=fetcher, min_length=min_length, config=cfg, observations=observations)
            if result:
                live_origin, body = result
                return apply_live(
                    live_origin,
                    body,
                    f"{site_name}: discovered via sequential probe (+{offset}) -> {live_origin}",
                )

    return cfg, False, f"{site_name}: unreachable (kept {current_url})"


def run_update(domains_path: Path, dry_run: bool = False, report_path: Optional[Path] = None) -> bool:
    data = json.loads(domains_path.read_text(encoding="utf-8"))
    sites = data.get("sites", {})
    any_changed = False

    results = []
    def check(item):
        site_name, cfg = item
        observations = []
        new_cfg, changed, msg = update_site_entry(site_name, cfg, observations=observations)
        return site_name, new_cfg, changed, msg, observations
    with ThreadPoolExecutor(max_workers=4) as executor:
        for site_name, new_cfg, changed, msg, observations in executor.map(check, list(sites.items())):
            print(msg)
            healthy = any(x['status'] == 'healthy' for x in observations)
            results.append({'site': site_name, 'status': 'healthy' if healthy else 'unverified',
                            'playbackVerified': False, 'probes': observations})
            if changed:
                sites[site_name] = new_cfg
                any_changed = True
    report = {'checkedAt': datetime.datetime.now(datetime.timezone.utc).isoformat(),
              'scope': 'Homepage identity checks, not playback', 'results': results}
    if report_path:
        report_path.parent.mkdir(parents=True, exist_ok=True)
        report_path.write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    run_update.last_healthy_count = sum(r['status'] == 'healthy' for r in results)
    if os.environ.get('GITHUB_STEP_SUMMARY'):
        with open(os.environ['GITHUB_STEP_SUMMARY'], 'a', encoding='utf-8') as summary:
            summary.write('## Domain identity checks (not playback)\n')
            for result in results:
                states = ', '.join(sorted({p['status'] for p in result['probes']}))
                summary.write('- ' + result['site'] + ': ' + result['status'] + ' (' + states + ')\n')

    if any_changed:
        data["updatedAt"] = datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
        if dry_run:
            print("[dry-run] domains.json would be updated:")
            print(json.dumps(data, indent=2, ensure_ascii=False))
        else:
            domains_path.write_text(
                json.dumps(data, indent=2, ensure_ascii=False) + "\n",
                encoding="utf-8",
            )
            print("Updated domains.json")
    else:
        print("No domain changes detected.")

    return any_changed


def main() -> int:
    parser = argparse.ArgumentParser(description="Update active domains in domains.json")
    parser.add_argument(
        "--file",
        type=Path,
        default=Path(__file__).resolve().parent.parent / "domains.json",
        help="Path to domains.json",
    )
    parser.add_argument("--dry-run", action="store_true", help="Do not write changes to disk")
    parser.add_argument('--report', type=Path, default=Path('health-report.json'))
    parser.add_argument('--require-healthy', action='store_true', help='Fail if no site identity was verified')
    args = parser.parse_args()

    run_update(args.file, dry_run=args.dry_run, report_path=args.report)
    return 1 if args.require_healthy and not run_update.last_healthy_count else 0


if __name__ == "__main__":
    sys.exit(main())
