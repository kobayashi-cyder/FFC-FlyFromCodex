#!/usr/bin/env python3
"""Host-only negative tests; never claim these are device tests."""
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

SCRIPT = Path(__file__).resolve().parents[1] / 'verify-release-download.py'
spec = importlib.util.spec_from_file_location('verify_release_download', SCRIPT)
mod = importlib.util.module_from_spec(spec)
spec.loader.exec_module(mod)

class ReleaseAuditTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.apk = self.root / 'signed.apk'
        self.apk.write_bytes(b'fixture: not an apk')
        self.digest = mod.sha256(self.apk)
        self.receipt = self.root / 'receipt.json'
        self.r = {
            'status': 'signed_host_verified_not_device_tested',
            'package': mod.PACKAGE, 'version_name': mod.VERSION,
            'version_code': mod.CODE, 'certificate_sha256': mod.CERT,
            'unsigned_apk_sha256': mod.UNSIGNED,
            'signed_apk_sha256': self.digest, 'device_tested': False,
            'certificate_matches_existing_install': True,
        }
        self.evidence = self.root / 'device.json'
        log = self.root / 'device.log'
        log.write_text('fixture; not real Android evidence\n', encoding='utf-8')
        self.d = {
            'signed_apk_sha256': self.digest, 'package': mod.PACKAGE,
            'version_code': mod.CODE, 'android_api': 37,
            'device_model': 'Pixel 10', 'device_fingerprint': 'google/fake/fixture:17/NOT_REAL',
            'cases': [{'name': n, 'status': 'pass', 'evidence_file': 'device.log',
                       'evidence_sha256': mod.sha256(log)} for n in sorted(mod.REQUIRED_CASES)],
        }
        self.save()

    def save(self):
        self.receipt.write_text(json.dumps(self.r), encoding='utf-8')
        self.evidence.write_text(json.dumps(self.d), encoding='utf-8')

    def test_valid_structure_is_not_device_attestation(self):
        self.assertEqual(mod.validate_receipt(self.apk, self.receipt), self.digest)
        self.assertEqual(mod.validate_device_evidence(self.evidence, self.digest), 11)

    def test_signed_hash_mismatch_rejected(self):
        self.r['signed_apk_sha256'] = '0' * 64
        self.save()
        with self.assertRaisesRegex(ValueError, 'receipt mismatch'):
            mod.validate_receipt(self.apk, self.receipt)

    def test_wrong_original_certificate_rejected(self):
        self.r['certificate_sha256'] = '0' * 64
        self.save()
        with self.assertRaisesRegex(ValueError, 'certificate_sha256'):
            mod.validate_receipt(self.apk, self.receipt)

    def test_unsigned_hash_mismatch_rejected(self):
        self.r['unsigned_apk_sha256'] = '0' * 64
        self.save()
        with self.assertRaisesRegex(ValueError, 'unsigned_apk_sha256'):
            mod.validate_receipt(self.apk, self.receipt)

    def test_missing_device_case_rejected(self):
        self.d['cases'].pop()
        self.save()
        with self.assertRaisesRegex(ValueError, 'missing device cases'):
            mod.validate_device_evidence(self.evidence, self.digest)

    def test_failed_device_case_rejected(self):
        self.d['cases'][0]['status'] = 'fail'
        self.save()
        with self.assertRaisesRegex(ValueError, 'not passed'):
            mod.validate_device_evidence(self.evidence, self.digest)

    def test_changed_log_rejected(self):
        self.d['cases'][0]['evidence_sha256'] = '0' * 64
        self.save()
        with self.assertRaisesRegex(ValueError, 'evidence hash mismatch'):
            mod.validate_device_evidence(self.evidence, self.digest)

    def test_outside_log_rejected(self):
        self.d['cases'][0]['evidence_file'] = '../untrusted.log'
        self.save()
        with self.assertRaisesRegex(ValueError, 'outside directory'):
            mod.validate_device_evidence(self.evidence, self.digest)

    def test_wrong_android_api_rejected(self):
        self.d['android_api'] = 36
        self.save()
        with self.assertRaisesRegex(ValueError, 'API37'):
            mod.validate_device_evidence(self.evidence, self.digest)

    def test_other_device_needs_equivalence_reason(self):
        self.d['device_model'] = 'Other'
        self.save()
        with self.assertRaisesRegex(ValueError, 'equivalence rationale'):
            mod.validate_device_evidence(self.evidence, self.digest)

    def test_duplicate_case_rejected(self):
        self.d['cases'].append(self.d['cases'][0])
        self.save()
        with self.assertRaisesRegex(ValueError, 'duplicate device test case'):
            mod.validate_device_evidence(self.evidence, self.digest)

if __name__ == '__main__':
    unittest.main()
