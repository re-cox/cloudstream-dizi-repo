"""Public Turkish landing-page shelves -> Stremio/Nuvio discovery addon.

No account/session cookies or playback endpoints. Failed refreshes retain that shelf.
"""
import ast
import http.client
import datetime as dt
import json
import os
import time
import re
import unicodedata
import urllib.parse
import urllib.request
from pathlib import Path

from bs4 import BeautifulSoup

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'platforms'
TMDB_KEY = os.environ.get('TMDB_API_KEY', '439c478a771f35c05022f9feabcca01c')
SHELVES = [
    ('netflix', 'Netflix · Gündemdekiler', 'https://www.netflix.com/tr/'),
    ('exxen', 'Exxen · Öne Çıkanlar', 'https://www.exxen.com/tr/'),
    ('disney', 'Disney+ · Türkiye’de Bugünün Top 10 Dizileri', 'https://www.disneyplus.com/tr-tr'),
]
# Public website application key, sent by Exxen's unauthenticated landing page.
EXXEN_HEADERS = {'ApiKey': 'b45c9feb837f452294f5ce6abdfd240c', 'LangCode': 'tr',
                 'Referer': 'https://www.exxen.com/'}
EXXEN_API = 'https://hnp6jz3qwxfr.merlincdn.net/v1/'
ASIAN_TV = {'KR', 'JP', 'CN', 'TW', 'TH', 'HK', 'PH', 'VN', 'ID', 'MY', 'IN'}


def fetch(url, headers=None):
    req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0', **(headers or {})})
    for attempt in range(3):
        try:
            with urllib.request.urlopen(req, timeout=25) as r:
                try:
                    data = r.read()
                except http.client.IncompleteRead as exc:
                    if not url.startswith('https://www.netflix.com/'):
                        raise
                    data = exc.partial
                text = data.decode('utf-8')
                if url.startswith('https://www.netflix.com/'):
                    parse_netflix(text)  # Retry truncated GraphQL hydration, never publish partial identities.
                return text
        except Exception:
            if attempt == 2:
                raise
            time.sleep(1)


def get_json(url, headers=None):
    return json.loads(fetch(url, headers))


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp = path.with_suffix('.tmp')
    tmp.write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    tmp.replace(path)


def parse_netflix(text):
    country = re.search(r'"requestCountry"\s*:\s*\{[^{}]*"id"\s*:\s*"([A-Z]{2})"', text)
    if country and country[1] != 'TR':
        raise ValueError('Netflix returned a non-Turkey region; retained Turkish shelf')
    soup = BeautifulSoup(text, 'html.parser')
    section = soup.select_one('[data-uia="nmhp-top-10"]')
    if not section:
        raise ValueError('Netflix Top 10 section missing')
    records = {}
    for script in soup.find_all('script'):
        if script.text.startswith('netflix.reactContext.models.graphql = JSON.parse('):
            graph = json.loads(ast.literal_eval(script.text.split('JSON.parse(', 1)[1].rsplit(')', 1)[0]))['data']
            records = {v['title']: v for v in graph.values() if isinstance(v, dict) and v.get('title')}
    result = []
    for button in section.select('button[data-uia^="top-10-element-"]'):
        card = button.find('div', recursive=False)
        title = card.get_text(' ', strip=True)
        record = records.get(title, {})
        result.append({'title': title, 'sourceId': str(record.get('videoId', title)), 'type': {'Show': 'tv', 'Movie': 'movie'}.get(record.get('__typename'), 'multi'), 'year': record.get('latestYear') if record.get('__typename') == 'Movie' else None})
    return result


def parse_disney(text):
    soup = BeautifulSoup(text, 'html.parser')
    data = json.loads(soup.select_one('#__NEXT_DATA__').string)

    def walk(node):
        if isinstance(node, dict):
            yield node
            for value in node.values():
                yield from walk(value)
        elif isinstance(node, list):
            for value in node:
                yield from walk(value)

    sections = data['props']['pageProps']['stitchDocument']['mainContent']
    for section in sections:
        nodes = list(walk(section))
        if any('Bugünün Top 10 Dizileri' in str(n.get('children', '')) for n in nodes if n.get('_type') == 'Text'):
            return [{'title': n['title'], 'type': 'tv', 'year': n.get('metadata', {}).get('year'),
                     'sourceId': n['_id']} for n in nodes if n.get('_type') == 'TopRankedCard']
    raise ValueError('Disney Turkish series Top 10 missing')


def exxen_items():
    # Resolve the named shelf from the site's current home list rather than its carousel.
    pages = get_json(EXXEN_API + 'page?contentTypeName=Page,PageText,PageLive,PageList,Search,Profile&profile=adult&mcdn-langauge-code=tr', EXXEN_HEADERS)

    def walk(pages):
        for page in pages:
            yield page['item']
            yield from walk(page.get('innerPages', []))

    home = next(p for p in walk(pages) if p.get('encodedName') == 'anasayfa')
    ids = [i['id'] for group in home.get('itemList', []) for i in group.get('items', [])]
    for id_ in ids:
        data = get_json(EXXEN_API + f'list?id={id_}&mcdn-langauge-code=tr', EXXEN_HEADERS)
        for shelf in data:
            if normalized(shelf['item'].get('displayTitle', '')) != normalized('Öne Çıkanlar'):
                continue
            return [{'title': i['displayTitle'], 'type': 'movie' if i['contentType']['name'] == 'Movie' else 'tv',
                     'year': next((f['value'] for f in i.get('customFields', []) if f['field'] == 'MadeYear'), None),
                     'sourceId': str(i['id']), 'country': 'TR'} for group in shelf.get('listGroups', []) for i in group.get('listItems', [])]
    raise ValueError('Exxen featured shelf missing')


def normalized(value):
    value = str(value).lower().replace('ı', 'i')
    return re.sub('[^a-z0-9]', '', unicodedata.normalize('NFKD', value).encode('ascii', 'ignore').decode())


def tmdb(path, **params):
    return get_json('https://api.themoviedb.org/3/' + path + '?' + urllib.parse.urlencode({'api_key': TMDB_KEY, 'language': 'tr-TR', **params}))


def resolve(item):
    key = f"{item.get('type', 'multi')}:{item['sourceId']}:{item['title']}"
    kind = item.get('type', 'multi')
    matches = tmdb('search/' + kind, query=item['title'], include_adult='false')['results']
    exact = []
    if item.get('platform') == 'disney' and item['title'] == 'Konuşanlar':
        matches = [tmdb('tv/301090')]
        matches[0].update(media_type='tv', name='Konuşanlar')
    if kind == 'tv' and len(matches) == 1 and ASIAN_TV.intersection(matches[0].get('origin_country', [])):
        return key, {'excluded': 'Asian series preference', 'title': item['title']}
    for match in matches:
        media = match.get('media_type', kind)
        if media not in ('tv', 'movie'):
            continue
        names = [match.get(k, '') for k in ('title', 'name', 'original_title', 'original_name')]
        if not any(normalized(n) == normalized(item['title']) for n in names):
            continue
        if media == 'tv' and ASIAN_TV.intersection(match.get('origin_country', [])):
            continue
        if item.get('country') and media == 'tv' and match.get('origin_country') and item['country'] not in match['origin_country']:
            continue
        year = (match.get('first_air_date') or match.get('release_date') or '')[:4]
        if item.get('platform') != 'exxen' and item.get('year') and year and str(item['year']) != year:
            continue
        exact.append((match, media))
    if len(exact) > 1:
        dated = [(m, t) for m, t in exact if m.get('first_air_date') or m.get('release_date')]
        if len(dated) == 1:
            exact = dated
    if len(exact) != 1:
        raise ValueError('ambiguous or unmatched title: ' + item['title'])
    match, media = exact[0]
    detail = tmdb(f"{media}/{match['id']}", append_to_response='external_ids')
    if media == 'tv' and ASIAN_TV.intersection(detail.get('origin_country', [])):
        return key, {'excluded': 'Asian series preference', 'title': item['title']}
    imdb = detail.get('imdb_id') or detail.get('external_ids', {}).get('imdb_id')
    if imdb and not re.fullmatch(r'tt\d+', imdb):
        raise ValueError('invalid IMDb identity')
    content_id = imdb or f"tmdb:{match['id']}"
    api_type = 'series' if media == 'tv' else 'movie'
    # Cinemeta supplies episodes and rich details for the normal detail/+ workflow.
    try:
        if not imdb:
            raise ValueError('TMDB metadata required')
        meta = get_json(f'https://v3-cinemeta.strem.io/meta/{api_type}/{imdb}.json')['meta']
    except Exception:
        meta = {'id': content_id, 'type': api_type}
        if media == 'tv':
            meta['videos'] = []
            for season in detail.get('seasons', []):
                if season['season_number'] < 1:
                    continue
                episodes = tmdb(f"tv/{match['id']}/season/{season['season_number']}")['episodes']
                for episode in episodes:
                    video = {'id': f"{content_id}:{episode['season_number']}:{episode['episode_number']}", 'title': episode['name'], 'season': episode['season_number'], 'episode': episode['episode_number']}
                    if episode.get('air_date'):
                        video['released'] = episode['air_date'] + 'T00:00:00.000Z'
                    meta['videos'].append(video)
    meta.update(id=content_id, type=api_type, name=item['title'],
                description=detail.get('overview') or meta.get('description', ''),
                releaseInfo=(detail.get('first_air_date') or detail.get('release_date') or '')[:4])
    for key_, field in [('poster', 'poster_path'), ('background', 'backdrop_path')]:
        if detail.get(field):
            meta[key_] = 'https://image.tmdb.org/t/p/' + ('w500' if key_ == 'poster' else 'w1280') + detail[field]
    meta['posterShape'] = 'poster'
    meta['genres'] = [g['name'] for g in detail.get('genres', [])]
    meta['links'] = [{'name': 'Resmî platform', 'category': 'Platform', 'url': item['sourceUrl']}]
    return key, {'meta': meta, 'tmdbId': match['id']}


def refresh():
    OUT.mkdir(exist_ok=True)
    report = {'updatedAt': dt.datetime.now(dt.timezone.utc).isoformat(), 'shelves': {}}
    for slug, title, url in SHELVES:
        try:
            raw = exxen_items() if slug == 'exxen' else (parse_netflix(fetch(url)) if slug == 'netflix' else parse_disney(fetch(url)))
            if len(raw) < 3:
                raise ValueError('suspiciously empty shelf')
            metas, exclusions, unresolved = [], [], []
            for rank, item in enumerate(raw, 1):
                item['sourceUrl'] = url
                item['platform'] = slug
                try:
                    key, record = resolve(item)
                    if 'excluded' in record:
                        exclusions.append(item['title'])
                        continue
                    meta = dict(record['meta'])
                    if meta['id'] not in {m['id'] for m in metas}:
                        metas.append(meta)
                except Exception as exc:
                    unresolved.append({'title': item['title'], 'reason': str(exc)})
            if len(metas) < 3:
                raise ValueError('fewer than three verified identities; retained previous shelf')
            for meta in metas:
                if meta['id'].startswith('tmdb:'):
                    continue  # Native Nuvio resolves canonical TMDB IDs through its metadata service.
                write_json(OUT / 'meta' / meta['type'] / (meta['id'] + '.json'), {'meta': meta})
            preview_fields = {'id', 'type', 'name', 'poster', 'posterShape', 'background', 'description', 'genres', 'releaseInfo', 'imdbRating'}
            write_json(OUT / 'catalog' / 'series' / (slug + '.json'), {'metas': [{k: v for k, v in m.items() if k in preview_fields} for m in metas]})
            report['shelves'][slug] = {'source': url, 'sourceCount': len(raw), 'published': len(metas),
                                      'excluded': exclusions, 'unresolved': unresolved, 'status': 'updated'}
        except Exception as exc:
            report['shelves'][slug] = {'status': 'retained', 'reason': str(exc)}
    write_json(OUT / 'refresh-report.json', report)
    write_json(OUT / 'manifest.json', {'id': 'com.recox.platforms.tr', 'version': '1.0.0',
        'name': 'ALEN · Platform Gündemi', 'description': 'Resmî Türkiye sayfalarından Netflix, Exxen ve Disney+ keşif bantları',
        'resources': ['catalog', {'name': 'meta', 'types': ['movie', 'series'], 'idPrefixes': ['tt']}], 'types': ['movie', 'series'],
        'catalogs': [{'id': slug, 'type': 'series', 'name': title} for slug, title, _ in SHELVES],
        'behaviorHints': {'configurable': False, 'configurationRequired': False}})
    print(json.dumps(report, ensure_ascii=False, indent=2))
    return report


if __name__ == '__main__':
    refresh()
