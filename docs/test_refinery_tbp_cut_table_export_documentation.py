"""Protect the auditable TBP cut-table export documentation contract."""

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


def test_tbp_cut_table_export_is_documented() -> None:
    """Keep the guide aligned with the fail-closed Java export contract."""
    guide = GUIDE.read_text(encoding="utf-8")
    index = INDEX.read_text(encoding="utf-8")
    source = SOURCE.read_text(encoding="utf-8")

    assert "exportTbpCutTable" in guide
    assert "exportTbpCutTable" in source
    assert "getResolvedVolumeFractions" in guide
    assert "cumulative liquid-volume yield" in guide
    assert "ideal-additive-volume identity" in guide
    assert "Gaps and overlaps are not interpolated or repaired" in guide
    assert "not a re-lumping or resampling algorithm" in guide
    assert "auditable TBP cut-table export" in index
    assert "class TbpCutTable" in source
