from pathlib import Path
import re
import unittest
from urllib.parse import urlsplit


DOC = Path(__file__).parent / "thermo" / "characterization" / (
    "refinery_hydrotreating_sulfur_nitrogen_hydrogen_recycle_balance.md"
)


class CoupledHydrotreatingHydrogenRecycleDocumentationTest(unittest.TestCase):
    """Verify recycle evidence, provenance, closure, and engineering boundary."""

    def test_coupled_recycle_receipt_is_auditable_and_bounded(self):
        source = DOC.read_text(encoding="utf-8")
        text = " ".join(source.split())

        for phrase in (
            "RefineryHydrotreatingSulfurNitrogenHydrogenRecycleBalance",
            "RefineryHydrotreatingSulfurNitrogenHydrogenSupplyBalance",
            "H2, H2S, NH3",
            "effective H2, H2S, NH3, and non-H2 recycle fractions",
            "3.101471912 kg fresh makeup gas",
            "7.612023933 kg gas",
            "28.5% of the once-through hydrogen supply",
            "overall mass residuals are numerical zero",
            "illustrative caller assumptions",
            "not a separator",
            "fails closed",
            "process-design claim",
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
            ("https", "webbook.nist.gov", "/cgi/inchi/InChI%3D1S/H3N/h1H3"),
            ("https", "webbook.nist.gov", "/cgi/inchi/InChI%3D1S/N2/c1-2"),
        ):
            with self.subTest(reference=reference):
                self.assertIn(reference, references)


if __name__ == "__main__":
    unittest.main()
