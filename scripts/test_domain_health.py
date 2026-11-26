import json
import socket
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch
from domain_health import valid_origin, identity_error, error_status, POLICY
from update_domains import probe_url, run_update


class HealthContractTests(unittest.TestCase):
    def test_rejects_unrelated_redirect_even_with_brand_in_body(self):
        observations = []
        result = probe_url('https://dizilla.test', 'dizilla',
                           fetcher=lambda *_: (200, 'https://advert.test/', 'dizilla ' + 'x' * 2000),
                           observations=observations)
        self.assertIsNone(result)
        self.assertEqual(observations[0]['status'], 'unapproved_redirect')

    def test_known_site_with_wrong_application_template_is_rejected(self):
        result = probe_url('https://dizilla.test', 'dizilla',
                           fetcher=lambda *_: (200, 'https://dizilla.test/', 'dizilla ' + 'x' * 2000),
                           config={'requiredMarkers': ['__next_f']})
        self.assertIsNone(result)

    def test_registry_urls_cannot_supply_credentials_ports_or_ip_destinations(self):
        for value in ['https://user:pass@site.test', 'https://127.0.0.1', 'https://site.test:8080',
                      'file:///etc/passwd', 'https://service.internal', 'https://localhost']:
            self.assertFalse(valid_origin(value), value)

    def test_redirect_cannot_silently_drop_path_or_query(self):
        self.assertEqual(identity_error('https://site.test', 'https://site.test/ads?x=1', '', {}), 'redirect_path_changed')

    def test_dns_and_timeout_are_distinct(self):
        self.assertEqual(error_status(socket.gaierror()), 'dns_error')
        self.assertEqual(error_status(TimeoutError()), 'timeout')

    def test_zero_healthy_sites_preserve_registry_and_write_failure_evidence(self):
        with tempfile.TemporaryDirectory() as directory:
            p = Path(directory) / 'domains.json'
            report = Path(directory) / 'report.json'
            original = json.dumps({'sites': {'Sample': {'currentUrl': 'https://site.test'}}})
            p.write_text(original, encoding='utf-8')
            with patch('update_domains.update_site_entry', return_value=({'currentUrl': 'https://site.test'}, False, 'unreachable')):
                self.assertFalse(run_update(p, report_path=report))
            self.assertEqual(run_update.last_healthy_count, 0)
            self.assertEqual(p.read_text(encoding='utf-8'), original)
            self.assertEqual(json.loads(report.read_text(encoding='utf-8'))['results'][0]['status'], 'unverified')

    def test_shared_minimum_is_used(self):
        self.assertEqual(POLICY['minBodyLength'], 1500)
