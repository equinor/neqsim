"""Protect the bounded TBP boiling-range spread documentation contract."""

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


def test_tbp_boiling_range_spread_is_documented() -> None:
    """Keep the guide aligned with the bounded second-moment contract."""
    guide = GUIDE.read_text(encoding="utf-8")
    index = INDEX.read_text(encoding="utf-8")
    source = SOURCE.read_text(encoding="utf-8")

    assert "getLiquidVolumeWeightedSquaredBoilingPointKelvinSquaredPercent" in source
    assert "getBoilingPointVarianceKelvinSquared" in source
    assert "getBoilingPointStandardDeviationKelvin" in source
    assert "Raw second moments close additively" in guide
    assert "not ASTM distillation reproducibility or experimental uncertainty" in guide
    assert "distribution-spread" in index
