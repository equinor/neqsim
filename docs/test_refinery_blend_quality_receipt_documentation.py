"""Documentation contract for refinery blend-quality constraint receipts."""

from pathlib import Path


DOC = Path(__file__).parent / "thermo" / "characterization" / "refinery_assay.md"


def test_blend_quality_receipt_documents_evidence_and_limits():
    text = " ".join(DOC.read_text(encoding="utf-8").split())

    assert "QualityConstraintReceipt" in text
    assert "getQualityConstraintReceipt" in text
    assert "sulfurMargin" in text
    assert "documented numerical tolerance" in text
    assert "not sensitivity or uncertainty analysis" in text
    assert "does not expose dual prices" in text
    assert "certify a product specification" in text
