"""Protect the qualified D86-to-assay-boundary documentation contract."""

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
GUIDE = ROOT / "docs" / "thermo" / "characterization" / "refinery_assay.md"
INDEX = ROOT / "docs" / "thermo" / "characterization" / "README.md"
SOURCE = (
    ROOT
    / "src"
    / "main"
    / "java"
    / "neqsim"
    / "thermo"
    / "characterization"
    / "OilAssayCharacterisation.java"
)


def test_d86_assay_boundary_contract_is_documented() -> None:
    """Keep the public guide aligned with the fail-closed Java integration."""
    guide = GUIDE.read_text(encoding="utf-8")
    index = INDEX.read_text(encoding="utf-8")
    source = SOURCE.read_text(encoding="utf-8")

    method_name = "addD86ReferencePointCutBoundariesCelsius"
    assert method_name in guide
    assert method_name in source
    assert "0, 10, 30, 50, 70, 90, and 95" in guide
    assert "caller must therefore supply the 100 vol% terminal TBP boundary explicitly" in guide
    assert "does not support ASTM D1160" in guide
    assert "https://www.osti.gov/biblio/5212509" in guide
    assert "RiaziDaubertDistillationConversion.convertD86ToTbpC" in source
    assert "qualified D86-reference-point data" in index
