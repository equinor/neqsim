from pathlib import Path


DOC = Path(__file__).with_name("thermo") / "characterization" / "refinery_assay.md"
SOURCE = (
    Path(__file__).parents[1]
    / "src"
    / "main"
    / "java"
    / "neqsim"
    / "thermo"
    / "characterization"
    / "OilAssayCharacterisation.java"
)


def test_tbp_table_blending_contract_is_documented_and_linked_to_api():
    documentation = " ".join(DOC.read_text(encoding="utf-8").split())
    source = SOURCE.read_text(encoding="utf-8")

    assert "Complete TBP table blending" in documentation
    assert "TbpCutTable.blendByMass" in documentation
    assert "ordered union of all source boiling boundaries" in documentation
    assert "cumulative ideal-mass recovery at 450 K is 62.5%" in documentation
    assert "measured multi-crude distillation behavior" in documentation
    assert "public static TbpCutTable blendByMass" in source
