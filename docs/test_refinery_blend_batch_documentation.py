"""Documentation contract for auditable refinery blend-batch receipts."""

from pathlib import Path


DOC = Path(__file__).parent / "thermo" / "characterization" / "refinery_assay.md"


def test_blend_batch_documentation_keeps_source_and_scope_boundaries():
    text = " ".join(DOC.read_text(encoding="utf-8").split())

    assert "RefineryBlendBatch" in text
    assert "samples 50146 and 56337" in text
    assert "https://data.openei.org/submissions/23" in text
    assert "999.016 kg/m3" in text
    assert "not a measured multi-crude blend" in text
    assert "does not model blend contraction" in text
    assert "does not create or mix thermodynamic streams" in text
