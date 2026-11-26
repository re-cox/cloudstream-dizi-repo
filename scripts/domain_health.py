"""Shared domain contract: identity validation is distinct from video playback."""
import ipaddress
import json
import re
import socket
import ssl
import urllib.error
from pathlib import Path
from urllib.parse import urlsplit

POLICY = json.loads((Path(__file__).resolve().parents[1] / 'domain-policy.json').read_text(encoding='utf-8'))


def valid_origin(url):
    try:
        p = urlsplit(url)
        if p.scheme not in ('http', 'https') or not p.hostname or p.username or p.password or p.port:
            return ''
        host = p.hostname.lower().rstrip('.')
        if '.' not in host or not re.fullmatch(r'[a-z0-9.-]+', host):
            return ''
        try:
            ipaddress.ip_address(host)
            return ''  # Registries contain named public sites, never IP destinations.
        except ValueError:
            pass
        if host.endswith(('.local', '.localhost', '.internal')):
            return ''
        return p.scheme + '://' + host
    except ValueError:
        return ''


def identity_error(requested, final, body, config):
    """Unknown cross-domain redirects require review rather than brand guessing."""
    origin = valid_origin(final)
    if not origin:
        return 'invalid_url'
    p = urlsplit(final)
    if p.query or p.fragment or p.path.rstrip('/') != urlsplit(requested).path.rstrip('/'):
        return 'redirect_path_changed'
    allowed = {valid_origin(x) for x in [requested, config.get('currentUrl', ''), config.get('nextUrl') or '', *config.get('mirrors', [])]}
    allowed_hosts = config.get('allowedRedirectHosts', [])
    if origin not in allowed and p.hostname not in allowed_hosts:
        return 'unapproved_redirect'
    if any(marker.lower() not in body.lower() for marker in config.get('requiredMarkers', [])):
        return 'parser_mismatch'
    return None


def error_status(error):
    reason = getattr(error, 'reason', error)
    if isinstance(error, urllib.error.HTTPError):
        return 'challenge' if error.code == 403 else 'rate_limited' if error.code == 429 else 'http_error'
    if isinstance(reason, (TimeoutError, socket.timeout)):
        return 'timeout'
    if isinstance(reason, socket.gaierror):
        return 'dns_error'
    if isinstance(reason, ssl.SSLError):
        return 'tls_error'
    return 'network_error'


def body_status(status, body, health_text, min_length):
    lower = body.lower()
    if 'cf-chl-' in lower or '<title>just a moment' in lower or status == 403:
        return 'challenge'
    if any(m in lower for m in POLICY['blockedMarkers']):
        return 'blocked_or_parked'
    if status == 429:
        return 'rate_limited'
    if status < 200 or status >= 300:
        return 'http_error'
    if len(body) < min_length:
        return 'short_body'
    if health_text and health_text.lower() not in lower:
        return 'identity_mismatch'
    return 'healthy'
