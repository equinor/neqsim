"""Protect the bounded TBP mass-basis recovery documentation contract."""

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


def test_tbp_mass_basis_recovery_is_documented() -> None:
    """Keep the guide and index aligned with the bounded mass-basis API."""
    guide = GUIDE.read_text(encoding="utf-8")
    index = INDEX.read_text(encoding="utf-8")
    source = SOURCE.read_text(encoding="utf-8")

    assert "getCumulativeMassPercentAtBoilingPointKelvin" in source
    assert "getBoilingPointKelvinAtCumulativeMassPercent" in source
    assert "getMassPercentBetweenBoilingPointsKelvin" in source
    assert "getMassPercent()" in source
    assert "Ideal-additive-volume mass-basis recovery" in guide
    assert "Partitioned bounded mass yields sum to 100%" in guide
    assert "not a measured\nweight-percent distillation curve" in guide
    assert "ideal-additive-volume mass recovery" in index
