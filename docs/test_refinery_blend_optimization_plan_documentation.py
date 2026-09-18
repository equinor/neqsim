"""Documentation contract for complete optimized refinery blend-plan receipts."""

from pathlib import Path


DOC = Path(__file__).parent / "thermo" / "characterization" / "refinery_assay.md"


def test_optimized_blend_plan_documents_receipts_and_scope_boundaries():
    text = " ".join(DOC.read_text(encoding="utf-8").split())

    assert "RefineryBlendOptimizationPlan" in text
    assert "DOE/OEDI sample 50146" in text
    assert "DOE/OEDI sample 56337" in text
    assert "source-level cost closes to the optimizer unit cost and batch total cost" in text
    assert "does not re-solve the optimization" in text
    assert "does not add blend contraction, scheduling, or compliance logic" in text
