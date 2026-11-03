#!/usr/bin/env python3
"""Host-only guardrails for the v1.151 signed release chain."""
import hashlib
import importlib.util
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

def load(filename, module_name):
    spec = importlib.util.spec_from_file_location(module_name, ROOT / filename)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module

class ReleaseIntegrityTests(unittest.TestCase):
    def test_release_uses_exact_audited_baseline(self):
        release = load("release-local.py", "markertap_release_local")
        self.assertEqual(release.BASE_SHA, "58e6cd830350473dad218352b3e23f4efcb214b2624e1122823ceb1093345067")
        self.assertEqual(release.BUILD_SCRIPT, "build-v151-from-v150.py")
        self.assertEqual(release.VERSION, "1.151")

    def test_signer_pins_ci_verified_unsigned_bytes(self):
        signer = load("sign-release.py", "markertap_sign_release")
        self.assertEqual(signer.EXPECTED_UNSIGNED_SHA256, "54cfccffa8bd6b5149fb2da563088b1cd3dde85037f567a33182308619d8befe")
        self.assertEqual(signer.CODE, "154")
        self.assertEqual(signer.VERSION, "1.151")
        self.assertEqual(signer.CERT_SHA256, "7f42d636e4d3c251f475eb998fbe8e92b515ded8bce3ff4d30aa90b9a738cfb1")
        with tempfile.TemporaryDirectory() as tmp:
            fake = Path(tmp) / "altered-unsigned.apk"
            fake.write_bytes(b"unreviewed artifact")
            self.assertNotEqual(hashlib.sha256(fake.read_bytes()).hexdigest(), signer.EXPECTED_UNSIGNED_SHA256)
            with self.assertRaisesRegex(ValueError, "unsigned APK differs"):
                signer.verify_unsigned_digest(fake)

if __name__ == "__main__":
    unittest.main()
