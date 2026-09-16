"""Documentation contract for the Sarir side-stripper equilibrium-contact screen."""

from pathlib import Path


DOC = (Path(__file__).parent / "thermo" / "characterization"
       / "refinery_sarir_atmospheric_side_stripper_contact.md")


def test_side_stripper_contact_documentation_keeps_source_and_scope_boundaries():
    text = " ".join(DOC.read_text(encoding="utf-8").split())

    assert "10.66411/jer.v33i.46" in text
    assert "CC BY 4.0" in text
    assert "Kerosene side stripper | 68.04 | 150 | 476" in text
    assert "Diesel side stripper | 226.8 | 150 | 476" in text
    assert "single equilibrium contact" in text
    assert "does not publish the pressure basis" in text
    assert "reproduction" in text
    assert "does not claim or infer a tray mapping" in text
