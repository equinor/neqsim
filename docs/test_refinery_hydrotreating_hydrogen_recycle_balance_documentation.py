from pathlib import Path
import re
import unittest
from urllib.parse import urlsplit


DOC = Path(__file__).parent / "thermo" / "characterization" / (
    "refinery_hydrotreating_hydrogen_recycle_balance.md"
)


class HydrotreatingHydrogenRecycleBalanceDocumentationTest(unittest.TestCase):
    """Verify recycle evidence, provenance, and the engineering stop boundary."""

    def test_screen_documents_receipts_references_and_boundary(self):
        source = DOC.read_text(encoding="utf-8")
        text = " ".join(source.split())

        for phrase in (
            "RefineryHydrotreatingHydrogenRecycleBalance",
            "RefineryHydrotreatingHydrogenSupplyBalance",
            "effective H2 recycle fraction is 0.855",
            "1.396916654 kg fresh makeup gas",
            "5.212737927 kg gas",
            "28.5% of the once-through hydrogen supply",
            "overall mass residuals are numerical zero",
            "not a separator",
            "fails closed",
        ):
            with self.subTest(phrase=phrase):
                self.assertIn(phrase, text)

        references = {
            (parsed.scheme, parsed.hostname, parsed.path)
            for target in re.findall(r"\]\((https://[^\s)]+)\)", source)
            for parsed in (urlsplit(target),)
        }
        for reference in (
            ("https", "www.eia.gov", "/tools/glossary/index.php"),
            ("https", "www.aiche.org", "/sites/default/files/cep/20211029.pdf"),
            ("https", "webbook.nist.gov", "/cgi/inchi/InChI%3D1S/H2S/h1H2"),
            ("https", "webbook.nist.gov", "/cgi/inchi/InChI%3D1S/N2/c1-2"),
        ):
            with self.subTest(reference=reference):
                self.assertIn(reference, references)


if __name__ == "__main__":
    unittest.main()
