package neqsim.thermo.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Tests for ProducedWaterFluidBuilder.
 *
 * @author Copilot
 */
class ProducedWaterFluidBuilderTest extends neqsim.NeqSimTest {

  /**
   * Test creating a system from TDS.
   */
  @Test
  void testCreateFromTDS() {
    SystemInterface system =
        ProducedWaterFluidBuilder.createFromTDS(273.15 + 25.0, 1.0, 35000.0, 1.0);
    assertNotNull(system);
    assertTrue(system.getPhase(0).hasComponent("Na+"), "Should have Na+ component");
    assertTrue(system.getPhase(0).hasComponent("Cl-"), "Should have Cl- component");
    assertTrue(system.getPhase(0).hasComponent("water"), "Should have water component");
    // chemicalReactionInit adds H3O+, OH- etc., so component count > 3
    assertTrue(system.getPhase(0).getNumberOfComponents() >= 3,
        "Should have at least 3 components but had " + system.getPhase(0).getNumberOfComponents());
  }

  /**
   * Test creating seawater system.
   */
  @Test
  void testCreateFromTypeSeawater() {
    SystemInterface system =
        ProducedWaterFluidBuilder.createFromType(273.15 + 25.0, 1.0, "seawater");
    assertNotNull(system);
    assertTrue(system.getPhase(0).hasComponent("Na+"), "Should have Na+");
    assertTrue(system.getPhase(0).hasComponent("Cl-"), "Should have Cl-");
    assertTrue(system.getPhase(0).hasComponent("Mg++"), "Should have Mg++");
    assertTrue(system.getPhase(0).hasComponent("Ca++"), "Should have Ca++");
  }

  /**
   * Test creating condensed water.
   */
  @Test
  void testCreateFromTypeCondensedWater() {
    SystemInterface system =
        ProducedWaterFluidBuilder.createFromType(273.15 + 25.0, 1.0, "condensed_water");
    assertNotNull(system);
    assertTrue(system.getPhase(0).hasComponent("water"), "Should have water");
    // Even pure water gets H3O+ and OH- from chemicalReactionInit
    assertTrue(system.getPhase(0).getNumberOfComponents() >= 1,
        "Condensed water should have at least 1 component");
  }

  /**
   * Test creating formation water.
   */
  @Test
  void testCreateFromTypeFormationHigh() {
    SystemInterface system =
        ProducedWaterFluidBuilder.createFromType(273.15 + 80.0, 150.0, "formation_high");
    assertNotNull(system);
    assertTrue(system.getPhase(0).hasComponent("Na+"));
    assertTrue(system.getPhase(0).hasComponent("Cl-"));
  }

  /**
   * Test that invalid water type throws exception.
   */
  @Test
  void testCreateFromTypeInvalid() {
    assertThrows(IllegalArgumentException.class, () -> {
      ProducedWaterFluidBuilder.createFromType(273.15, 1.0, "invalid_type");
    });
  }

  /**
   * Test creating from explicit ion concentrations.
   */
  @Test
  void testCreateFromIons() {
    Map<String, Double> ions = new LinkedHashMap<String, Double>();
    ions.put("Na+", 10770.0);
    ions.put("Cl-", 19350.0);
    ions.put("Mg++", 1290.0);

    SystemInterface system =
        ProducedWaterFluidBuilder.createFromIons(273.15 + 25.0, 1.0, ions);
    assertNotNull(system);
    assertTrue(system.getPhase(0).hasComponent("Na+"));
    assertTrue(system.getPhase(0).hasComponent("Cl-"));
    assertTrue(system.getPhase(0).hasComponent("Mg++"));
    assertTrue(system.getPhase(0).hasComponent("water"));
  }

  /**
   * Test adding gas components to a water system.
   */
  @Test
  void testAddGasToWater() {
    SystemInterface system =
        ProducedWaterFluidBuilder.createFromTDS(273.15 + 25.0, 50.0, 35000.0, 0.9);

    Map<String, Double> gas = new LinkedHashMap<String, Double>();
    gas.put("methane", 0.85);
    gas.put("CO2", 0.10);
    gas.put("H2S", 0.05);

    ProducedWaterFluidBuilder.addGasToWater(system, gas, 0.1);
    assertTrue(system.getPhase(0).hasComponent("methane"), "Should have methane");
    assertTrue(system.getPhase(0).hasComponent("CO2"), "Should have CO2");
    assertTrue(system.getPhase(0).hasComponent("H2S"), "Should have H2S");
  }

  /**
   * Test that the created system can be flashed successfully.
   */
  @Test
  void testFlashCreatedSystem() {
    SystemInterface system =
        ProducedWaterFluidBuilder.createFromTDS(273.15 + 25.0, 1.0, 35000.0, 1.0);
    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    ops.TPflash();
    system.initProperties();

    double density = system.getDensity("kg/m3");
    // Seawater density should be close to 1020-1030 kg/m3 at 25C, 1 bar
    assertTrue(density > 900.0, "Density should be > 900 kg/m3 but was " + density);
    assertTrue(density < 1200.0, "Density should be < 1200 kg/m3 but was " + density);
  }
}
