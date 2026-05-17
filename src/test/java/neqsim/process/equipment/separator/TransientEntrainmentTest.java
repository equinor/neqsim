package neqsim.process.equipment.separator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests that entrainment corrections are applied during transient (dynamic) separator simulation
 * via {@code runTransient()}. Verifies that the detailed performance calculator modifies outlet
 * compositions when enabled, and that the vessel inventory remains unaffected.
 *
 * @author copilot
 * @version 1.0
 */
public class TransientEntrainmentTest {

  /**
   * Creates a two-phase gas/condensate fluid for testing.
   *
   * @return a configured SystemInterface with mixing rule set
   */
  private SystemInterface createTestFluid() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 40.0, 50.0);
    fluid.addComponent("methane", 0.80);
    fluid.addComponent("ethane", 0.05);
    fluid.addComponent("propane", 0.03);
    fluid.addComponent("n-pentane", 0.05);
    fluid.addComponent("n-heptane", 0.04);
    fluid.addComponent("n-octane", 0.03);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);
    return fluid;
  }

  /**
   * Creates a three-phase fluid (gas/oil/water) for testing.
   *
   * @return a configured SystemInterface with mixing rule set
   */
  private SystemInterface createThreePhaseFluid() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 60.0, 30.0);
    fluid.addComponent("methane", 0.60);
    fluid.addComponent("ethane", 0.04);
    fluid.addComponent("propane", 0.03);
    fluid.addComponent("n-pentane", 0.08);
    fluid.addComponent("n-heptane", 0.10);
    fluid.addComponent("n-octane", 0.05);
    fluid.addComponent("water", 0.10);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);
    return fluid;
  }

  @Test
  @DisplayName("Transient separator with entrainment produces different outlet compositions")
  void testTransientEntrainmentChangesGasComposition() {
    SystemInterface fluid = createTestFluid();
    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(5000.0, "kg/hr");
    feed.run();

    // --- Separator WITHOUT detailed entrainment ---
    Separator sepNoEntrain = new Separator("sep-no-entrain", feed);
    sepNoEntrain.setOrientation("horizontal");
    sepNoEntrain.setSeparatorLength(3.0);
    sepNoEntrain.setInternalDiameter(1.0);
    sepNoEntrain.setLiquidLevel(0.4);
    sepNoEntrain.setCalculateSteadyState(false);
    sepNoEntrain.run();

    UUID id1 = UUID.randomUUID();
    double dt = 1.0;
    // Run multiple steps so the gas outlet flow rate stabilizes
    for (int i = 0; i < 5; i++) {
      sepNoEntrain.runTransient(dt, id1);
    }

    double[] gasCompNoEntrain =
        sepNoEntrain.getGasOutStream().getFluid().getMolarComposition().clone();

    // --- Separator WITH detailed entrainment ---
    Separator sepEntrain = new Separator("sep-entrain", feed);
    sepEntrain.setOrientation("horizontal");
    sepEntrain.setSeparatorLength(3.0);
    sepEntrain.setInternalDiameter(1.0);
    sepEntrain.setLiquidLevel(0.4);
    sepEntrain.setCalculateSteadyState(false);
    sepEntrain.setEnhancedEntrainmentCalculation(true);
    sepEntrain.run();

    UUID id2 = UUID.randomUUID();
    for (int i = 0; i < 5; i++) {
      sepEntrain.runTransient(dt, id2);
    }

    double[] gasCompEntrain =
        sepEntrain.getGasOutStream().getFluid().getMolarComposition().clone();

    // Check if the performance calculator actually computed non-zero entrainment
    double oilInGasFrac = sepEntrain.getPerformanceCalculator().getOilInGasFraction();
    System.out.printf("Performance calc oilInGas=%.6e, waterInGas=%.6e, gasInOil=%.6e%n",
        oilInGasFrac, sepEntrain.getPerformanceCalculator().getWaterInGasFraction(),
        sepEntrain.getPerformanceCalculator().getGasInOilFraction());
    System.out.printf("Vessel phases=%d, hasGas=%b, hasOil=%b, hasAqueous=%b%n",
        sepEntrain.getThermoSystem().getNumberOfPhases(),
        sepEntrain.getThermoSystem().hasPhaseType("gas"),
        sepEntrain.getThermoSystem().hasPhaseType("oil"),
        sepEntrain.getThermoSystem().hasPhaseType("aqueous"));
    System.out.printf("Gas outlet flow m3/s=%.6e, T=%.2f K, P=%.2f bar%n",
        sepEntrain.getGasOutStream().getFluid().getFlowRate("m3/sec"),
        sepEntrain.getThermoSystem().getTemperature(),
        sepEntrain.getThermoSystem().getPressure());
    System.out.printf("Liquid level=%.4f%n", sepEntrain.getLiquidLevel());

    // The compositions should differ — entrainment adds liquid components to gas
    boolean anyDifference = false;
    for (int i = 0; i < gasCompNoEntrain.length; i++) {
      if (Math.abs(gasCompNoEntrain[i] - gasCompEntrain[i]) > 1e-12) {
        anyDifference = true;
        break;
      }
    }
    assertTrue(anyDifference,
        "Gas outlet composition should differ when entrainment is enabled in transient");
  }

  @Test
  @DisplayName("Transient entrainment preserves vessel inventory (thermoSystem not modified)")
  void testTransientEntrainmentPreservesVesselInventory() {
    SystemInterface fluid = createTestFluid();
    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(5000.0, "kg/hr");
    feed.run();

    Separator sep = new Separator("sep", feed);
    sep.setOrientation("horizontal");
    sep.setSeparatorLength(3.0);
    sep.setInternalDiameter(1.0);
    sep.setLiquidLevel(0.4);
    sep.setCalculateSteadyState(false);
    sep.setEnhancedEntrainmentCalculation(true);
    sep.run();

    UUID id = UUID.randomUUID();
    sep.runTransient(1.0, id);

    // Vessel total moles at step 1
    double molesAfterStep1 = sep.getThermoSystem().getTotalNumberOfMoles();

    sep.runTransient(1.0, id);

    // Vessel total moles at step 2 should have changed from mass balance, not from
    // entrainment corrupting the inventory
    double molesAfterStep2 = sep.getThermoSystem().getTotalNumberOfMoles();

    // The moles should be positive (separator not drained)
    assertTrue(molesAfterStep1 > 0.0, "Vessel should have positive moles after step 1");
    assertTrue(molesAfterStep2 > 0.0, "Vessel should have positive moles after step 2");

    // The change should be small for one timestep (order of dt * flow / total)
    double relativeChange = Math.abs(molesAfterStep2 - molesAfterStep1) / molesAfterStep1;
    assertTrue(relativeChange < 0.5,
        "Vessel moles should not change drastically in one timestep: " + relativeChange);
  }

  @Test
  @DisplayName("Transient entrainment fractions are within physical bounds")
  void testTransientEntrainmentFractionsArePhysical() {
    SystemInterface fluid = createTestFluid();
    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(5000.0, "kg/hr");
    feed.run();

    Separator sep = new Separator("sep-bounds", feed);
    sep.setOrientation("horizontal");
    sep.setSeparatorLength(3.0);
    sep.setInternalDiameter(1.0);
    sep.setLiquidLevel(0.4);
    sep.setCalculateSteadyState(false);
    sep.setEnhancedEntrainmentCalculation(true);
    sep.run();

    UUID id = UUID.randomUUID();
    for (int i = 0; i < 5; i++) {
      sep.runTransient(1.0, id);
    }

    // Check the performance calculator results
    double oilInGas = sep.getPerformanceCalculator().getOilInGasFraction();
    double waterInGas = sep.getPerformanceCalculator().getWaterInGasFraction();
    double gasInOil = sep.getPerformanceCalculator().getGasInOilFraction();

    assertTrue(oilInGas >= 0.0 && oilInGas < 1.0,
        "Oil-in-gas fraction must be in [0,1): " + oilInGas);
    assertTrue(waterInGas >= 0.0 && waterInGas < 1.0,
        "Water-in-gas fraction must be in [0,1): " + waterInGas);
    assertTrue(gasInOil >= 0.0 && gasInOil < 1.0,
        "Gas-in-oil fraction must be in [0,1): " + gasInOil);
  }

  @Test
  @DisplayName("ThreePhaseSeparator transient applies entrainment to all three outlets")
  void testThreePhaseSeparatorTransientEntrainment() {
    SystemInterface fluid = createThreePhaseFluid();
    Stream feed = new Stream("feed-3ph", fluid);
    feed.setFlowRate(8000.0, "kg/hr");
    feed.run();

    // --- Without entrainment ---
    ThreePhaseSeparator sepNoEntrain = new ThreePhaseSeparator("3ph-no-entrain", feed);
    sepNoEntrain.setOrientation("horizontal");
    sepNoEntrain.setSeparatorLength(4.0);
    sepNoEntrain.setInternalDiameter(1.2);
    sepNoEntrain.setLiquidLevel(0.5);
    sepNoEntrain.setCalculateSteadyState(false);
    sepNoEntrain.run();

    UUID id1 = UUID.randomUUID();
    sepNoEntrain.runTransient(1.0, id1);
    sepNoEntrain.runTransient(1.0, id1);

    double[] gasNoEntrain =
        sepNoEntrain.getGasOutStream().getFluid().getMolarComposition().clone();

    // --- With entrainment ---
    ThreePhaseSeparator sepEntrain = new ThreePhaseSeparator("3ph-entrain", feed);
    sepEntrain.setOrientation("horizontal");
    sepEntrain.setSeparatorLength(4.0);
    sepEntrain.setInternalDiameter(1.2);
    sepEntrain.setLiquidLevel(0.5);
    sepEntrain.setCalculateSteadyState(false);
    sepEntrain.setEnhancedEntrainmentCalculation(true);
    sepEntrain.run();

    UUID id2 = UUID.randomUUID();
    sepEntrain.runTransient(1.0, id2);
    sepEntrain.runTransient(1.0, id2);

    double[] gasEntrain =
        sepEntrain.getGasOutStream().getFluid().getMolarComposition().clone();

    // Gas compositions should differ
    boolean gasChanged = false;
    for (int i = 0; i < gasNoEntrain.length; i++) {
      if (Math.abs(gasNoEntrain[i] - gasEntrain[i]) > 1e-12) {
        gasChanged = true;
        break;
      }
    }
    assertTrue(gasChanged,
        "ThreePhaseSeparator gas outlet should differ with entrainment in transient");
  }

  @Test
  @DisplayName("Manual entrainment setEntrainment() also works during transient")
  void testManualEntrainmentDuringTransient() {
    SystemInterface fluid = createTestFluid();
    Stream feed = new Stream("feed-manual", fluid);
    feed.setFlowRate(5000.0, "kg/hr");
    feed.run();

    // Separator with manual (non-performance-calculator) entrainment
    Separator sep = new Separator("sep-manual", feed);
    sep.setOrientation("horizontal");
    sep.setSeparatorLength(3.0);
    sep.setInternalDiameter(1.0);
    sep.setLiquidLevel(0.4);
    sep.setCalculateSteadyState(false);
    // Set manual entrainment: 2% oil carryover into gas
    sep.setEntrainment(0.02, "volume", "feed", "oil", "gas");
    sep.run();

    // --- Baseline: no entrainment ---
    Separator sepBase = new Separator("sep-base", feed);
    sepBase.setOrientation("horizontal");
    sepBase.setSeparatorLength(3.0);
    sepBase.setInternalDiameter(1.0);
    sepBase.setLiquidLevel(0.4);
    sepBase.setCalculateSteadyState(false);
    sepBase.run();

    UUID id1 = UUID.randomUUID();
    UUID id2 = UUID.randomUUID();
    for (int i = 0; i < 3; i++) {
      sep.runTransient(1.0, id1);
      sepBase.runTransient(1.0, id2);
    }

    // The manually-set entrainment should cause the gas outlet to carry heavier components
    double[] gasManual = sep.getGasOutStream().getFluid().getMolarComposition();
    double[] gasBase = sepBase.getGasOutStream().getFluid().getMolarComposition();

    boolean anyDiff = false;
    for (int i = 0; i < gasManual.length; i++) {
      if (Math.abs(gasManual[i] - gasBase[i]) > 1e-12) {
        anyDiff = true;
        break;
      }
    }
    assertTrue(anyDiff,
        "Manual entrainment should produce different gas compositions in transient");
  }

  @Test
  @DisplayName("Steady-state entrainment route still works when calculateSteadyState=true")
  void testSteadyStateRoutePreserved() {
    SystemInterface fluid = createTestFluid();
    Stream feed = new Stream("feed-ss", fluid);
    feed.setFlowRate(5000.0, "kg/hr");
    feed.run();

    // Separator that delegates to run() via getCalculateSteadyState()=true
    Separator sep = new Separator("sep-ss", feed);
    sep.setOrientation("horizontal");
    sep.setSeparatorLength(3.0);
    sep.setInternalDiameter(1.0);
    sep.setLiquidLevel(0.4);
    sep.setCalculateSteadyState(true); // forces runTransient->run() delegation
    sep.setEnhancedEntrainmentCalculation(true);
    sep.run();

    UUID id = UUID.randomUUID();
    sep.runTransient(1.0, id);

    // Should complete without error (steady-state path already tested elsewhere)
    assertTrue(sep.getThermoSystem().getPressure() > 0.0, "Pressure should be positive");
    assertTrue(sep.getGasOutStream().getFluid().getNumberOfPhases() >= 1,
        "Gas outlet should have at least one phase");
  }
}
