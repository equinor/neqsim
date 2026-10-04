"""Protect the bounded TBP boiling-moment documentation contract."""

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


def test_tbp_boiling_range_moments_are_documented() -> None:
    """Keep the guide aligned with the bounded temperature-moment contract."""
    guide = GUIDE.read_text(encoding="utf-8")
    index = INDEX.read_text(encoding="utf-8")
    source = SOURCE.read_text(encoding="utf-8")

    assert "getLiquidVolumeWeightedBoilingPointKelvinPercent" in source
    assert "getAverageBoilingPointKelvin" in source
    assert "getWatsonCharacterizationFactor" in source
    assert "First moments, unlike means, close additively" in guide
    assert "not an ASTM mean-average or volumetric-average boiling point" in guide
    assert "TBP boiling-range temperature-moment" in index
