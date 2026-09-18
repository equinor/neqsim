from pathlib import Path


DOC = Path(__file__).parent / "thermo" / "characterization" / (
    "refinery_hydrotreating_sulfur_balance.md"
)


def test_hydrotreating_screen_documents_provenance_receipts_and_boundary():
    text = " ".join(DOC.read_text(encoding="utf-8").split())

    assert "RefineryHydrotreatingSulfurBalance" in text
    assert "0.0040867518" in text
    assert "15 ppm" in text
    assert "mol H2 per mol sulfur removed" in text
    assert "hydrogen sulfide produced" in text
    assert "total-mass residual" in text
    assert "sulfur residual" in text
    assert "does not predict kinetics" in text
    assert "eia.gov/tools/glossary" in text
    assert "aiche.org/sites/default/files/cep/20211029.pdf" in text
    assert "webbook.nist.gov" in text
