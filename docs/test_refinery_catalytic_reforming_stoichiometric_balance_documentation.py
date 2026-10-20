from pathlib import Path
import re
import unittest
from urllib.parse import urlsplit


DOC = Path(__file__).parent / "thermo" / "characterization" / (
    "refinery_catalytic_reforming_stoichiometric_balance.md"
)


class CatalyticReformingStoichiometricBalanceDocumentationTest(unittest.TestCase):
    """Verify the reforming reactions, public provenance, and screening boundary."""

    def test_screen_documents_reactions_closure_and_boundary(self):
        source = DOC.read_text(encoding="utf-8")
        text = " ".join(source.split())

        for phrase in (
            "RefineryCatalyticReformingStoichiometricBalance",
            "RefineryCatalyticReformingFeedBalance",
            "naphthene dehydrogenation",
            "paraffin dehydrocyclization",
            "3.5 kmol/h hydrogen",
            "27.505461656 kg/t feed",
            "780.29702 kg/h idealized reformate",
            "52.621900454 kg/t feed",
            "not a calibrated naphtha-reformer yield slate",
            "carbon-atom",
            "hydrogen-atom",
            "does not predict kinetics",
            "not literature or plant yield claims",
        ):
            with self.subTest(phrase=phrase):
                self.assertIn(phrase, text)

        references = {
            (parsed.scheme, parsed.hostname, parsed.path)
            for target in re.findall(r"\]\((https://[^\s)]+)\)", source)
            for parsed in (urlsplit(target),)
        }
        for reference in (
            ("https", "science.osti.gov", "/-/media/bes/pdf/reports/files/cat_rpt.pdf"),
            ("https", "archive.epa.gov", "/sectors/web/pdf/petrefsn.pdf"),
        ):
            with self.subTest(reference=reference):
                self.assertIn(reference, references)
