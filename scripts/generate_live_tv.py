"""Generate a small, independently installable live catalog from verified public feeds."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def generate():
    output = ROOT / 'live'
    channels = json.loads((output / 'channels.json').read_text(encoding='utf-8'))['channels']

    def write(relative, value):
        path = output / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')

    write('manifest.json', {
        'id': 'com.recox.live', 'version': '1.1.0', 'name': 're-cox Canlı TV',
        'description': 'Ulusal canlı TV kanalları. NOW, eski FOX adıyla aynı kanaldır.',
        'resources': ['catalog', {'name': 'meta', 'types': ['channel'], 'idPrefixes': ['recox-live-']},
                      {'name': 'stream', 'types': ['channel'], 'idPrefixes': ['recox-live-']}],
        'types': ['channel'], 'idPrefixes': ['recox-live-'],
        'catalogs': [{'type': 'channel', 'id': 'turkiye', 'name': 'Canlı TV · Türkiye'}]
    })
    metas = []
    for channel in channels:
        meta = {'id': channel['id'], 'type': 'channel', 'name': channel['name'],
                'poster': channel['poster'], 'background': channel['poster'], 'posterShape': 'landscape',
                'description': channel['name'] + ' · İnternet canlı yayını', 'genres': [channel['group']]}
        metas.append(meta)
        write('meta/channel/' + channel['id'] + '.json', {'meta': meta})
        # NuvioTV's StreamMapper uses behaviorHints.proxyHeaders.request, not top-level headers.
        write('stream/channel/' + channel['id'] + '.json', {'streams': [{
            'name': 're-cox Canlı TV', 'title': channel['name'] + ' · Canlı', 'url': channel['url'],
            'headers': channel['headers'],
            'behaviorHints': {'isLive': True, 'proxyHeaders': {'request': channel['headers']}}
        }]})
    write('catalog/channel/turkiye.json', {'metas': metas})
    print(f'Generated {len(metas)} live channels.')


if __name__ == '__main__':
    generate()
