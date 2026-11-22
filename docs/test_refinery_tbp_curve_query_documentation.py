"""Protect the bounded TBP curve-query documentation contract."""

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


def test_tbp_curve_queries_are_documented() -> None:
    """Keep the guide aligned with the bounded invertible query contract."""
    guide = GUIDE.read_text(encoding="utf-8")
    index = INDEX.read_text(encoding="utf-8")
    source = SOURCE.read_text(encoding="utf-8")

    assert "getCumulativeVolumePercentAtBoilingPointKelvin" in source
    assert "getBoilingPointKelvinAtCumulativeVolumePercent" in source
    assert "getLiquidVolumePercentBetweenBoilingPointsKelvin" in source
    assert "piecewise-linear cumulative-recovery" in guide
    assert "do not extrapolate" in guide
    assert "invertible TBP recovery/cut-point queries" in index
