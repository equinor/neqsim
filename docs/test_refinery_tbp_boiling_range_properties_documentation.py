"""Protect the bounded TBP boiling-range property-receipt documentation contract."""

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


def test_tbp_boiling_range_property_receipts_are_documented() -> None:
    """Keep the guide aligned with the bounded property-receipt contract."""
    guide = GUIDE.read_text(encoding="utf-8")
    index = INDEX.read_text(encoding="utf-8")
    source = SOURCE.read_text(encoding="utf-8")

    assert "class TbpBoilingRangeProperties" in source
    assert "getBoilingRangePropertiesKelvin" in source
    assert "getSpecificGravityWeightedLiquidVolumePercent" in source
    assert "ideal-additive-volume bookkeeping term" in guide
    assert "does not temperature-correct density" in guide
    assert "TBP boiling-range SG60/60" in index
