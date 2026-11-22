"""Documentation contract for S8 reconciliation checkpoint manifest transitions."""

from pathlib import Path
import unittest


DOC = Path(__file__).parent / "chemicalreactions" / (
    "h2s_oxygen_s8_stream_application_batch_reconciliation_checkpoint_manifest_transition.md"
)


class ManifestTransitionDocumentationTest(unittest.TestCase):
    """Keep the transition evidence and ownership boundary explicit."""

    @classmethod
    def setUpClass(cls):
        cls.text = DOC.read_text(encoding="utf-8")
        cls.prose = " ".join(cls.text.split())

    def test_front_matter_and_api_are_present(self):
        self.assertTrue(self.text.startswith("---\n"))
        self.assertIn("ManifestTransition`", self.text)
        self.assertIn("create(prior, candidate)", self.text)
        self.assertIn("`verify`", self.text)

    def test_exact_prefix_failures_are_named(self):
        for token in ("truncation", "reordering", "replacement", "manifest-identity drift"):
            self.assertIn(token, self.text)
        self.assertIn("same manifest identifier", self.text)
        self.assertIn("same order", self.text)

    def test_digest_and_count_closure_are_explicit(self):
        self.assertIn("SHA-256", self.text)
        self.assertIn("MessageDigest.isEqual", self.text)
        self.assertIn("defensive copy", self.text)
        self.assertIn("close exactly", self.text)

    def test_non_claims_and_non_mutation_are_explicit(self):
        for token in (
            "not a durable store",
            "exactly-once",
            "no flash calculation",
            "no stream, fluid, process, or pipeline mutation",
            "does not infer sulfur yield",
        ):
            self.assertIn(token, self.prose)


if __name__ == "__main__":
    unittest.main()
