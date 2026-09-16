"""Documentation contract for the Sarir atmospheric product-quality screen."""

from pathlib import Path


DOC = (
    Path(__file__).parent
    / "thermo"
    / "characterization"
    / "refinery_sarir_atmospheric_product_quality.md"
)


def test_product_quality_documentation_keeps_source_and_scope_boundaries():
    text = DOC.read_text(encoding="utf-8")

    assert "10.66411/jer.v33i.46" in text
    assert "CC BY 4.0" in text
    assert "Kerosene | 221 | 214 | 221" in text
    assert "Diesel | 346 | 339 | 327" in text
    assert "qualified Sarir atmospheric fractionation result" in text
    assert "not an ASTM laboratory procedure or compliance determination" in text
    assert "does not resolve the source's light/heavy naphtha split" in text
    assert "published `550+` specification is nonnumeric" in text
    assert "does not reproduce or calibrate the Sarir plant" in text
