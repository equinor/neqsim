package neqsim.thermo.characterization;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Test class for LumpingConfigBuilder fluent API.
 *
 * @author ESOL
 */
public class LumpingConfigBuilderTest {
  private SystemInterface fluid;

  @BeforeEach
  void setUp() {
    fluid = new SystemSrkEos(298.0, 10.0);
    fluid.addComponent("methane", 0.7);
    fluid.addComponent("ethane", 0.1);
    fluid.addComponent("propane", 0.05);
    fluid.addTBPfraction("C7", 0.05, 95.0 / 1000.0, 0.72);
    fluid.addTBPfraction("C8", 0.04, 106.0 / 1000.0, 0.76);
    fluid.addTBPfraction("C9", 0.03, 121.0 / 1000.0, 0.79);
    fluid.addPlusFraction("C10+", 0.03, 250.0 / 1000.0, 0.85);
    fluid.setMixingRule("classic");
  }

  @Test
  void testConfigureLumpingReturnBuilder() {
    LumpingConfigBuilder builder = fluid.getCharacterization().configureLumping();
    assertNotNull(builder, "configureLumping() should return a builder");
  }

  @Test
  void testPVTLumpingModelWithPlusFractionGroups() {
    fluid.getCharacterization().configureLumping().model("PVTlumpingModel").plusFractionGroups(5)
        .build();

    assertEquals(5, fluid.getCharacterization().getLumpingModel().getNumberOfLumpedComponents(),
        "PVTlumpingModel should have 5 lumped components for plus fraction");
  }

  @Test
  void testStandardModelWithTotalPseudoComponents() {
    fluid.getCharacterization().configureLumping().model("standard").totalPseudoComponents(6)
        .build();

    assertEquals(6, fluid.getCharacterization().getLumpingModel().getNumberOfPseudoComponents(),
        "Standard model should have 6 total pseudo-components");
  }

  @Test
  void testNoLumpingShorthand() {
    fluid.getCharacterization().configureLumping().noLumping().build();

    assertEquals("no lumping", fluid.getCharacterization().getLumpingModel().getName(),
        "noLumping() should set 'no lumping' model");
  }

  @Test
  void testBuilderReturnsCharacterise() {
    Characterise result = fluid.getCharacterization().configureLumping().model("PVTlumpingModel")
        .plusFractionGroups(3).build();

    assertNotNull(result, "build() should return the Characterise instance");
    assertEquals(fluid.getCharacterization(), result,
        "build() should return the same Characterise instance");
  }

  @Test
  void testMethodChaining() {
    // Verify fluent API allows chaining
    fluid.getCharacterization().configureLumping().model("PVTlumpingModel").plusFractionGroups(4)
        .build();

    assertEquals(4, fluid.getCharacterization().getLumpingModel().getNumberOfLumpedComponents());
  }

  @Test
  void testCustomBoundaries() {
    // Test custom carbon number boundaries (feature 3)
    int[] boundaries = {6, 10, 15, 20};
    fluid.getCharacterization().configureLumping().model("PVTlumpingModel")
        .customBoundaries(boundaries).build();

    assertTrue(fluid.getCharacterization().getLumpingModel().hasCustomBoundaries(),
        "Custom boundaries should be set");
    assertArrayEquals(boundaries,
        fluid.getCharacterization().getLumpingModel().getCustomBoundaries(),
        "Custom boundaries should match input");
  }

  @Test
  void testCustomBoundariesSetsNumberOfGroups() {
    // Custom boundaries should set the number of lumped components
    int[] boundaries = {6, 7, 10, 15, 20};
    fluid.getCharacterization().configureLumping().model("standard").customBoundaries(boundaries)
        .build();

    assertEquals(5, fluid.getCharacterization().getLumpingModel().getNumberOfLumpedComponents(),
        "Number of lumped components should equal number of boundaries");
  }

  @Test
  void testLumpingResultCreation() {
    // Test LumpingResult builder (feature 5)
    LumpingResult result = new LumpingResult.Builder().modelName("PVTlumpingModel")
        .originalComponentCount(75).lumpedComponentCount(5).originalAverageMW(0.150)
        .lumpedAverageMW(0.151).originalAverageDensity(750.0).lumpedAverageDensity(752.0)
        .addWarning("Test warning").build();

    assertEquals("PVTlumpingModel", result.getModelName());
    assertEquals(75, result.getOriginalComponentCount());
    assertEquals(5, result.getLumpedComponentCount());
    assertTrue(result.getMWError() < 0.01, "MW error should be less than 1%");
    assertTrue(result.getDensityError() < 0.01, "Density error should be less than 1%");
    assertTrue(result.hasWarnings());
    assertEquals(1, result.getWarnings().size());
  }

  @Test
  void testLumpingResultToMap() {
    LumpingResult result = new LumpingResult.Builder().modelName("standard")
        .originalComponentCount(50).lumpedComponentCount(6).build();

    java.util.Map<String, Object> map = result.toMap();
    assertNotNull(map);
    assertEquals("standard", map.get("modelName"));
    assertEquals(50, map.get("originalComponentCount"));
    assertEquals(6, map.get("lumpedComponentCount"));
  }
}
