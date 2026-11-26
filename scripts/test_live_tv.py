import json
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1] / 'live'


class LiveCatalogTests(unittest.TestCase):
    def test_live_catalog_uses_channel_type_and_each_item_has_meta_and_stream(self):
        manifest = json.loads((ROOT / 'manifest.json').read_text(encoding='utf-8'))
        self.assertEqual(manifest['types'], ['channel'])
        items = json.loads((ROOT / 'catalog/channel/turkiye.json').read_text(encoding='utf-8'))['metas']
        self.assertEqual(len({x['id'] for x in items}), len(items))
        for item in items:
            self.assertEqual(item['type'], 'channel')
            meta = json.loads((ROOT / ('meta/channel/' + item['id'] + '.json')).read_text(encoding='utf-8'))
            self.assertEqual(meta['meta']['id'], item['id'])
            stream = json.loads((ROOT / ('stream/channel/' + item['id'] + '.json')).read_text(encoding='utf-8'))['streams'][0]
            self.assertTrue(stream['url'].startswith('https://'))
            self.assertEqual(stream['headers'], stream['behaviorHints']['proxyHeaders']['request'])
            self.assertTrue(stream['behaviorHints']['isLive'])
