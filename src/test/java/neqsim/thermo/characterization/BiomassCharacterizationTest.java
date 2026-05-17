package neqsim.thermo.characterization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link BiomassCharacterization}.
 */
class BiomassCharacterizationTest {

  @Test
  void testCustomBiomassCalculation() {
    BiomassCharacterization bc = new BiomassCharacterization("TestWood");
    bc.setProximateAnalysis(25.0, 82.0, 17.0, 1.0);
    bc.setUltimateAnalysis(51.0, 6.1, 42.0, 0.3, 0.05, 0.01);

    assertFalse(bc.isCalculated());
    bc.calculate();
    assertTrue(bc.isCalculated());

    // HHV should be in a reasonable range for woody biomass (18-21 MJ/kg daf)
    assertTrue(bc.getHHV() > 15.0, "HHV too low: " + bc.getHHV());
    assertTrue(bc.getHHV() < 25.0, "HHV too high: " + bc.getHHV());

    // LHV should be less than HHV
    assertTrue(bc.getLHV() < bc.getHHV(), "LHV should be less than HHV");
    assertTrue(bc.getLHV() > 10.0, "LHV too low: " + bc.getLHV());

    // Stoichiometric air should be in a reasonable range (4-8 kg/kg for biomass)
    assertTrue(bc.getStoichiometricAir() > 0.0, "Air requirement should be positive");

    // Chemical formula should be defined
    assertNotNull(bc.getChemicalFormula());
    assertTrue(bc.getChemicalFormula().startsWith("CH"), "Formula should start with CH");

    // H/C and O/C ratios should be reasonable for biomass
    assertTrue(bc.getHCRatio() > 0.5, "H/C too low");
    assertTrue(bc.getHCRatio() < 3.0, "H/C too high");
    assertTrue(bc.getOCRatio() > 0.1, "O/C too low");
    assertTrue(bc.getOCRatio() < 1.5, "O/C too high");
  }

  @Test
  void testAutoCalculateOnGet() {
    BiomassCharacterization bc = new BiomassCharacterization("Auto");
    bc.setProximateAnalysis(10.0, 78.0, 16.0, 6.0);
    bc.setUltimateAnalysis(47.0, 5.8, 41.0, 0.8, 0.15, 0.3);

    // Should auto-calculate when get is called
    assertFalse(bc.isCalculated());
    double hhv = bc.getHHV();
    assertTrue(bc.isCalculated());
    assertTrue(hhv > 0);
  }

  @Test
  void testLibraryWoodChips() {
    BiomassCharacterization wood = BiomassCharacterization.library("wood_chips");
    assertTrue(wood.isCalculated());
    assertEquals("wood_chips", wood.getName());
    assertEquals(25.0, wood.getMoisture(), 0.01);
    assertEquals(51.0, wood.getCarbonWt(), 0.01);
    assertTrue(wood.getHHV() > 15.0);
    assertTrue(wood.getHHV() < 25.0);
  }

  @Test
  void testLibraryCaseInsensitive() {
    BiomassCharacterization bc1 = BiomassCharacterization.library("WOOD_CHIPS");
    BiomassCharacterization bc2 = BiomassCharacterization.library("Wood-Chips");
    BiomassCharacterization bc3 = BiomassCharacterization.library("wood chips");

    assertEquals(bc1.getHHV(), bc2.getHHV(), 0.001);
    assertEquals(bc2.getHHV(), bc3.getHHV(), 0.001);
  }

  @Test
  void testAllLibraryFeedstocks() {
    List<String> feedstocks = BiomassCharacterization.getLibraryFeedstocks();
    assertTrue(feedstocks.size() >= 8, "Should have at least 8 library feedstocks");

    for (String name : feedstocks) {
      BiomassCharacterization bc = BiomassCharacterization.library(name);
      assertTrue(bc.isCalculated(), name + " should be calculated");
      assertTrue(bc.getHHV() > 0, name + " HHV should be positive: " + bc.getHHV());
      assertTrue(bc.getLHV() > 0, name + " LHV should be positive: " + bc.getLHV());
      assertTrue(bc.getStoichiometricAir() >= 0.0, name + " air should be non-negative");
    }
  }

  @Test
  void testUnknownFeedstockThrows() {
    assertThrows(IllegalArgumentException.class,
        () -> BiomassCharacterization.library("unicorn_dust"));
  }

  @Test
  void testToMapReturnsAllKeys() {
    BiomassCharacterization bc = BiomassCharacterization.library("straw");
    Map<String, Double> map = bc.toMap();

    assertTrue(map.containsKey("HHV_MJ/kg_daf"));
    assertTrue(map.containsKey("LHV_MJ/kg_daf"));
    assertTrue(map.containsKey("stoichiometricAir_kg/kg"));
    assertTrue(map.containsKey("C_wt%_daf"));
    assertTrue(map.containsKey("H/C_molar"));
    assertTrue(map.containsKey("O/C_molar"));
  }

  @Test
  void testSetHHVOverride() {
    BiomassCharacterization bc = new BiomassCharacterization("Override");
    bc.setProximateAnalysis(10.0, 80.0, 15.0, 5.0);
    bc.setUltimateAnalysis(48.0, 6.0, 40.0, 0.5, 0.1, 0.1);
    bc.setHHV(20.5);
    bc.calculate();

    assertEquals(20.5, bc.getHHV(), 0.01, "HHV should use the overridden value");
  }

  @Test
  void testToString() {
    BiomassCharacterization bc = BiomassCharacterization.library("bagasse");
    String str = bc.toString();
    assertTrue(str.contains("BiomassCharacterization"));
    assertTrue(str.contains("HHV"));
    assertTrue(str.contains("LHV"));
  }
}
