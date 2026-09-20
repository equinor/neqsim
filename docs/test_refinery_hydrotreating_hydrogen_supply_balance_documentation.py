from pathlib import Path
import re
import unittest
from urllib.parse import urlsplit


DOC = Path(__file__).parent / "thermo" / "characterization" / (
    "refinery_hydrotreating_hydrogen_supply_balance.md"
)


class HydrotreatingHydrogenSupplyBalanceDocumentationTest(unittest.TestCase):
    """Verify the gas-supply evidence, provenance, and engineering boundary."""

    def test_screen_documents_receipts_references_and_boundary(self):
        source = DOC.read_text(encoding="utf-8")
        text = " ".join(source.split())

        for phrase in (
            "RefineryHydrotreatingHydrogenSupplyBalance",
            "RefineryHydrotreatingSulfurBalance",
            "90 mol% H2",
            "1.5 supply factor",
            "1.953729586 kg makeup gas",
            "5.769550859 kg outlet gas",
            "Overall mass closure",
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
            ("https", "webbook.nist.gov", "/cgi/inchi/InChI%3D1S/N2/c1-2"),
        ):
            with self.subTest(reference=reference):
                self.assertIn(reference, references)
