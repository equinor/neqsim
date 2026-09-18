from pathlib import Path
import re
import unittest
from urllib.parse import urlsplit


DOC = Path(__file__).parent / "thermo" / "characterization" / (
    "refinery_hydrotreating_sulfur_balance.md"
)


class HydrotreatingSulfurBalanceDocumentationTest(unittest.TestCase):
    """Verify the screening boundary and the identities of cited references."""

    def test_screen_documents_provenance_receipts_and_boundary(self):
        source = DOC.read_text(encoding="utf-8")
        text = " ".join(source.split())

        for phrase in (
            "RefineryHydrotreatingSulfurBalance",
            "0.0040867518",
            "15 ppm",
            "mol H2 per mol sulfur removed",
            "hydrogen sulfide produced",
            "total-mass residual",
            "sulfur residual",
            "does not predict kinetics",
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
        ):
            with self.subTest(reference=reference):
                self.assertIn(reference, references)
