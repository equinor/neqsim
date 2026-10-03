"""Protect the direct TBP cut-table recharacterization documentation contract."""

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


def test_tbp_cut_table_recharacterization_is_documented() -> None:
    """Keep the guide aligned with the direct Java recharacterization contract."""
    guide = GUIDE.read_text(encoding="utf-8")
    index = INDEX.read_text(encoding="utf-8")
    source = SOURCE.read_text(encoding="utf-8")

    assert "addTBPCutTable" in source
    assert "addTBPCutTable" in guide
    assert "deterministic recharacterization API" in guide
    assert "not copied or averaged from source subcuts" in guide
    assert "re-ingest and recharacterize" in index
