"""Protect the conservative adjacent-cut re-lumping documentation contract."""

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


def test_adjacent_cut_relumping_is_documented() -> None:
    """Keep the guide aligned with the conservative Java re-lumping contract."""
    guide = GUIDE.read_text(encoding="utf-8")
    index = INDEX.read_text(encoding="utf-8")
    source = SOURCE.read_text(encoding="utf-8")

    assert "relumpAdjacentCuts" in source
    assert "relumpAdjacentCuts" in guide
    assert "ideal-volume-weighted value" in guide
    assert "preserves cumulative liquid-volume yield exactly" in guide
    assert "does not split a cut" in guide
    assert "adjacent whole-cut re-lumping" in index
