"""Regression tests for source-locked mechanism acquisition."""

import hashlib
import io
from pathlib import Path
import tempfile
import unittest

from acquire_qualification_mechanism import acquire_mechanism
from benchmark_qualification import QualificationInputError


def _mechanism(payload, source_url="https://example.invalid/mechanism.yaml"):
    return {
        "id": "licensed-test-mechanism",
        "role": "qualification-candidate",
        "sourceUrl": source_url,
        "expectedSha256": hashlib.sha256(payload).hexdigest(),
        "reportedSourceMd5": hashlib.md5(payload, usedforsecurity=False).hexdigest(),
        "expectedSizeBytes": len(payload),
        "license": {"redistributionVerified": True, "spdxId": "CC-BY-4.0"},
    }


class MechanismAcquisitionTest(unittest.TestCase):
    def test_exact_download_is_published_atomically(self):
        payload = b"exact licensed mechanism bytes\n"
        calls = []

        def opener(request, timeout):
            calls.append((request.full_url, timeout))
            return io.BytesIO(payload)

        with tempfile.TemporaryDirectory() as temporary_directory:
            destination = Path(temporary_directory) / "mechanism.yaml"
            fingerprint = acquire_mechanism(_mechanism(payload), destination, opener)
            self.assertEqual(fingerprint, hashlib.sha256(payload).hexdigest())
            self.assertEqual(destination.read_bytes(), payload)
            self.assertEqual(calls, [("https://example.invalid/mechanism.yaml", 60)])
            self.assertEqual(list(destination.parent.glob("*.part")), [])

    def test_bad_download_is_removed_without_publishing(self):
        payload = b"expected bytes\n"

        def opener(_request, timeout):
            self.assertEqual(timeout, 60)
            return io.BytesIO(b"different bytes\n")

        with tempfile.TemporaryDirectory() as temporary_directory:
            destination = Path(temporary_directory) / "mechanism.yaml"
            with self.assertRaisesRegex(QualificationInputError, "byte count mismatch"):
                acquire_mechanism(_mechanism(payload), destination, opener)
            self.assertFalse(destination.exists())
            self.assertEqual(list(destination.parent.glob("*.part")), [])

    def test_existing_mismatch_is_not_overwritten_or_downloaded(self):
        payload = b"expected bytes\n"

        def opener(_request, timeout):
            self.assertEqual(timeout, 60)
            self.fail("existing destinations must be verified before network access")

        with tempfile.TemporaryDirectory() as temporary_directory:
            destination = Path(temporary_directory) / "mechanism.yaml"
            destination.write_bytes(b"user-owned different bytes\n")
            with self.assertRaisesRegex(QualificationInputError, "byte count mismatch"):
                acquire_mechanism(_mechanism(payload), destination, opener)
            self.assertEqual(destination.read_bytes(), b"user-owned different bytes\n")

    def test_non_https_source_is_rejected_before_network_access(self):
        payload = b"expected bytes\n"

        def opener(_request, timeout):
            self.assertEqual(timeout, 60)
            self.fail("non-HTTPS source must not be opened")

        with tempfile.TemporaryDirectory() as temporary_directory:
            destination = Path(temporary_directory) / "mechanism.yaml"
            with self.assertRaisesRegex(QualificationInputError, "must use HTTPS"):
                acquire_mechanism(
                    _mechanism(payload, "http://example.invalid/mechanism.yaml"),
                    destination,
                    opener,
                )


if __name__ == "__main__":
    unittest.main()
