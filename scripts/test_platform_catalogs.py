import json
import unittest
from pathlib import Path
from unittest.mock import patch

from update_platform_catalogs import parse_netflix, parse_disney, resolve, normalized, SHELVES


class PlatformCatalogTests(unittest.TestCase):
    def test_netflix_preserves_editorial_order_and_ignores_other_sections(self):
        text = '''<div data-uia="nmhp-top-10"><button data-uia="top-10-element-1"><div>A</div><span>1</span></button>
        <button data-uia="top-10-element-2"><div>B</div><span>2</span></button></div><button>Unrelated</button>'''
        self.assertEqual([i['title'] for i in parse_netflix(text)], ['A', 'B'])

    def test_disney_only_reads_series_top10_not_movie_top10(self):
        def section(label, title):
            return {'children': [{'_type': 'Text', 'children': label}, {'_type': 'TopRankedCard', '_id': title,
                    'title': title, 'metadata': {'year': '2026'}}]}
        data = {'props': {'pageProps': {'stitchDocument': {'mainContent': [
            section('Türkiye’de Bugünün Top 10 Dizileri', 'Dizi'), section('Top 10 Filmleri', 'Film')]}}}}
        items = parse_disney('<script id="__NEXT_DATA__">' + json.dumps(data) + '</script>')
        self.assertEqual([i['title'] for i in items], ['Dizi'])

    def test_ambiguous_names_do_not_open_a_wrong_show(self):
        candidates = [{'id': n, 'name': 'Same', 'first_air_date': '2020-01-01', 'origin_country': ['US']}
                      for n in (1, 2)]
        with patch('update_platform_catalogs.tmdb', return_value={'results': candidates}):
            with self.assertRaisesRegex(ValueError, 'ambiguous'):
                resolve({'title': 'Same', 'sourceId': 's', 'type': 'tv'})

    def test_asian_series_preference(self):
        with patch('update_platform_catalogs.tmdb', return_value={'results': [
                {'id': 1, 'name': 'Korean title', 'origin_country': ['KR']}]}):
            _, record = resolve({'title': 'English title', 'sourceId': 's', 'type': 'tv'})
            self.assertIn('excluded', record)

    def test_catalogs_have_unique_canonical_ids_posters_and_correct_order(self):
        root = Path(__file__).resolve().parents[1] / 'platforms'
        manifest = json.loads((root / 'manifest.json').read_text(encoding='utf-8'))
        self.assertEqual([c['id'] for c in manifest['catalogs']], [s[0] for s in SHELVES])
        for catalog in manifest['catalogs']:
            metas = json.loads((root / 'catalog/series' / (catalog['id'] + '.json')).read_text(encoding='utf-8'))['metas']
            self.assertGreaterEqual(len(metas), 3)
            self.assertEqual(len(metas), len({m['id'] for m in metas}))
            for meta in metas:
                self.assertRegex(meta['id'], r'^(tt\d+|tmdb:\d+)$')
                self.assertIn(meta['type'], ('movie', 'series'))
                self.assertTrue(meta['poster'].startswith('https://'))
                if meta['id'].startswith('tt'):
                    detail = json.loads((root / 'meta' / meta['type'] / (meta['id'] + '.json')).read_text(encoding='utf-8'))['meta']
                    self.assertEqual(detail['id'], meta['id'])


if __name__ == '__main__':
    unittest.main()
