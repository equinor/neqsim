"""Protect the conservative TBP cut-table splitting documentation contract."""

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


def test_tbp_cut_table_splitting_is_documented() -> None:
    """Keep the guide aligned with the piecewise-linear split contract."""
    guide = GUIDE.read_text(encoding="utf-8")
    index = INDEX.read_text(encoding="utf-8")
    source = SOURCE.read_text(encoding="utf-8")

    assert "splitAtBoilingPointsKelvin" in source
    assert "splitAtBoilingPointsCelsius" in source
    assert "piecewise-linear cumulative liquid-volume yield" in guide
    assert "explicit discretization assumption" in guide
    assert "export, split, re-lump, re-ingest and recharacterize" in index
