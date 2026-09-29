"""Documentation contract for S8 reconciliation checkpoint manifest transition chains."""

from pathlib import Path
import unittest


DOC = Path(__file__).parent / "chemicalreactions" / (
    "h2s_oxygen_s8_stream_application_batch_reconciliation_checkpoint_manifest_transition_chain.md"
)


class ManifestTransitionChainDocumentationTest(unittest.TestCase):
    """Keep chain continuity, closure, and ownership boundaries explicit."""

    @classmethod
    def setUpClass(cls):
        cls.text = DOC.read_text(encoding="utf-8")
        cls.prose = " ".join(cls.text.split())

    def test_front_matter_and_api_are_present(self):
        self.assertTrue(self.text.startswith("---\n"))
        self.assertIn("ManifestTransitionChain`", self.text)
        self.assertIn("create(chainIdentifier, transitions)", self.text)
        self.assertIn("`verify`", self.text)

    def test_adjacency_failures_are_named(self):
        for token in ("gap", "fork", "reordering", "identity drift", "duplicate transition digest"):
            self.assertIn(token, self.text)
        self.assertIn("candidate manifest digest", self.prose)
        self.assertIn("next transition's prior manifest digest", self.prose)

    def test_digest_and_count_closure_are_explicit(self):
        self.assertIn("SHA-256", self.text)
        self.assertIn("MessageDigest.isEqual", self.text)
        self.assertIn("fresh unmodifiable list", self.text)
        self.assertIn("defensive copy", self.text)
        self.assertIn("close exactly", self.text)

    def test_non_claims_and_non_mutation_are_explicit(self):
        for token in (
            "does not revalidate the underlying manifests",
            "not a durable store",
            "exactly-once",
            "no flash calculation",
            "no stream, fluid, process, or pipeline mutation",
            "does not infer sulfur yield",
        ):
            self.assertIn(token, self.prose)


if __name__ == "__main__":
    unittest.main()
