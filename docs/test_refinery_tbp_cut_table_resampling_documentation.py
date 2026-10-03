"""Protect the conservative TBP target-grid resampling documentation contract."""

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


def test_tbp_target_grid_resampling_is_documented() -> None:
    """Keep the guide aligned with the conservative resampling contract."""
    guide = GUIDE.read_text(encoding="utf-8")
    index = INDEX.read_text(encoding="utf-8")
    source = SOURCE.read_text(encoding="utf-8")

    assert "resampleAtBoilingPointsKelvin" in source
    assert "resampleAtBoilingPointsCelsius" in source
    assert "ideal-additive implied mass" in guide
    assert "piecewise-linear recovery" in guide
    assert "target-grid resampling" in index
