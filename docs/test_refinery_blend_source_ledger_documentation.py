"""Documentation contract for label-addressable refinery blend source receipts."""

from pathlib import Path


DOC = Path(__file__).parent / "thermo" / "characterization" / "refinery_assay.md"


def test_blend_source_ledger_documents_identity_and_scope_boundaries():
    text = " ".join(DOC.read_text(encoding="utf-8").split())

    assert "RefineryBlendSourceLedger" in text
    assert "DOE/OEDI sample 50146" in text
    assert "DOE/OEDI sample 56337" in text
    assert "caller metadata in exact batch-array order" in text
    assert "does not infer assay identity" in text
    assert "does not attest provenance" in text
    assert "does not create tanks or thermodynamic streams" in text
