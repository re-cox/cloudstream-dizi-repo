import json
import unittest
from pathlib import Path
from update_domains import update_site_entry


DIZIPAL_CFG = {
    "currentUrl": "https://dizipal2150.com",
    "nextUrl": None,
    "strategy": "sequential_and_announcement",
    "prefix": "https://dizipal",
    "suffix": ".com",
    "currentNumber": 2150,
    "maxProbe": 5,
    "announcementRegex": r"Sonraki\s+adresimiz\s+(?:https?://)?([a-zA-Z0-9.-]+\.[a-zA-Z]{2,})",
    "healthCheckText": "dizipal",
}

DIZILLA_CFG = {
    "currentUrl": "https://dizilla.now",
    "nextUrl": None,
    "strategy": "mirror_and_redirect",
    "mirrors": [
        "https://dizilla.now",
        "https://dizilla.la",
        "https://dizilla.club",
    ],
    "healthCheckText": "dizilla",
}


class TestDomainTracker(unittest.TestCase):
    def test_extracts_announced_next_domain_while_current_is_alive(self):
        def fake_fetcher(url, timeout=12):
            if url == "https://dizipal2150.com":
                return (
                    200,
                    "https://dizipal2150.com/",
                    "<html><body>DiziPal - Sonraki adresimiz Dizipal2151.com olacaktır</body></html>",
                )
            raise ConnectionError("unreachable")

        updated, changed, _ = update_site_entry("DiziPal", DIZIPAL_CFG, fetcher=fake_fetcher, min_length=20)
        self.assertTrue(changed)
        self.assertEqual(updated["currentUrl"], "https://dizipal2150.com")
        self.assertEqual(updated["nextUrl"], "https://dizipal2151.com")
        self.assertEqual(updated["currentNumber"], 2150)

    def test_failover_to_announced_next_url_on_btk_block(self):
        cfg = dict(DIZIPAL_CFG, nextUrl="https://dizipal2151.com")

        def fake_fetcher(url, timeout=12):
            if url == "https://dizipal2150.com":
                return (
                    200,
                    "https://dizipal2150.com/",
                    "<html><body>5651 sayılı kanun gereği erişime engellenmiştir.</body></html>",
                )
            if url == "https://dizipal2151.com":
                return (
                    200,
                    "https://dizipal2151.com/",
                    "<html><body>DiziPal Yeni Adres - Sonraki adresimiz Dizipal2152.com olacaktır</body></html>",
                )
            raise ConnectionError("unreachable")

        updated, changed, _ = update_site_entry("DiziPal", cfg, fetcher=fake_fetcher, min_length=20)
        self.assertTrue(changed)
        self.assertEqual(updated["currentUrl"], "https://dizipal2151.com")
        self.assertEqual(updated["nextUrl"], "https://dizipal2152.com")
        self.assertEqual(updated["currentNumber"], 2151)

    def test_failover_to_sequential_probe_when_next_skipped(self):
        cfg = dict(DIZIPAL_CFG, nextUrl="https://dizipal2151.com")

        def fake_fetcher(url, timeout=12):
            if url == "https://dizipal2153.com":
                return (
                    200,
                    "https://dizipal2153.com/",
                    "<html><body>DiziPal 2153 - Sonraki adresimiz Dizipal2154.com olacaktır</body></html>",
                )
            raise ConnectionError("unreachable")

        updated, changed, _ = update_site_entry("DiziPal", cfg, fetcher=fake_fetcher, min_length=20)
        self.assertTrue(changed)
        self.assertEqual(updated["currentUrl"], "https://dizipal2153.com")
        self.assertEqual(updated["nextUrl"], "https://dizipal2154.com")
        self.assertEqual(updated["currentNumber"], 2153)

    def test_mirror_failover_rejects_parked_domain(self):
        def fake_fetcher(url, timeout=12):
            if url == "https://dizilla.now":
                return (
                    200,
                    "https://dizilla.now/",
                    "<html><body>dizilla.now - Buy this domain on sedoparking</body></html>",
                )
            if url == "https://dizilla.la":
                return (
                    200,
                    "https://dizilla.la/",
                    "<html><body>Dizilla Yabancı Dizi İzle - Yeni Bölümler</body></html>",
                )
            raise ConnectionError("unreachable")

        updated, changed, _ = update_site_entry("Dizilla", DIZILLA_CFG, fetcher=fake_fetcher, min_length=20)
        self.assertTrue(changed)
        self.assertEqual(updated["currentUrl"], "https://dizilla.la")

    def test_redirect_updates_current_url_and_prepends_mirror(self):
        def fake_fetcher(url, timeout=12):
            if url == "https://dizilla.now":
                return (
                    200,
                    "https://dizilla.vip/",
                    "<html><body>Dizilla VIP - Tüm Yabancı Diziler</body></html>",
                )
            raise ConnectionError("unreachable")

        updated, changed, _ = update_site_entry("Dizilla", DIZILLA_CFG, fetcher=fake_fetcher, min_length=20)
        self.assertTrue(changed)
        self.assertEqual(updated["currentUrl"], "https://dizilla.vip")
        self.assertEqual(updated["mirrors"][0], "https://dizilla.vip")

    def test_domains_json_is_valid_and_has_all_13_sites(self):
        domains_file = Path(__file__).resolve().parent.parent / "domains.json"
        data = json.loads(domains_file.read_text(encoding="utf-8"))
        expected = {
            "DiziPal",
            "HDFilmCehennemi",
            "DiziBox",
            "Dizilla",
            "DiziYou",
            "SezonlukDizi",
            "FilmModu",
            "DiziMom",
            "FullHDFilmizlesene",
            "FilmMakinesi",
            "WebteIzle",
            "SelcukFlix",
            "SetFilmIzle",
        }
        self.assertEqual(set(data["sites"].keys()), expected)


if __name__ == "__main__":
    unittest.main()
