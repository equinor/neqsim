package neqsim.thermodynamicoperations;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import org.apache.commons.lang3.SerializationUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.WaterPhaseBoundaryResult.BoundaryResult;
import neqsim.thermodynamicoperations.WaterPhaseBoundaryResult.Status;

/**
 * Tests the one-call CPA boundary comparison and its caller-state and applicability contracts.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
class CpaWaterPhaseBoundaryCalculatorTest {
  /**
   * Checks two normalized natural gases at several absolute pressures without changing the supplied fluid.
   *
   * @param lean whether to use the lean CO2/nitrogen gas rather than the propane-bearing gas
   * @param pressure absolute pressure in bara
   */
  @ParameterizedTest
  @CsvSource({"false,20", "false,50", "false,80", "true,20", "true,50", "true,80"})
  void comparesThreeBoundariesAndPreservesGas(boolean lean, double pressure) {
    SystemInterface gas = gas(lean, pressure);
    byte[] original = SerializationUtils.serialize(gas);
    WaterPhaseBoundaryResult result = new ThermodynamicOperations(gas).cpaWaterPhaseBoundaryTemperatures();
    assertTrue(result.isComplete(), result.getLiquidWater().getDiagnostic() + "; " + result.getIce().getDiagnostic()
        + "; " + result.getHydrate().getDiagnostic());
    assertArrayEquals(original, SerializationUtils.serialize(gas),
        "The caller state and fitted parameters are retained");
    assertEquals(pressure, result.getPressureBara(), 1.0e-12);
    assertEquals("CPAs-SRK-EOS-statoil", result.getModel());
    assertEquals(10, result.getMixingRule());
    assertEquals(100.0e-6, result.getWaterMoleFraction(), 1.0e-14);
    assertThrows(UnsupportedOperationException.class, () -> result.getMoleFractions().put("water", 1.0));
    for (BoundaryResult boundary : new BoundaryResult[] {result.getLiquidWater(), result.getIce(),
        result.getHydrate()}) {
      assertTrue(Double.isFinite(boundary.getTemperatureC()));
      assertTrue(Double.isFinite(boundary.getResidual()));
      assertTrue(Double.isFinite(boundary.getWaterActivity()) && boundary.getWaterActivity() > 0.0);
      assertTrue(boundary.getPhaseReference().contains("x_water="));
    }
    assertTrue(result.getHydrate().getTemperatureC() > result.getIce().getTemperatureC());
    assertTrue(result.getIce().getTemperatureC() > result.getLiquidWater().getTemperatureC());
    assertTrue(result.getExplanation().contains("not a sequential cooling path"));
  }

  /** Executes the Java setup published in the CPA boundary guide and checks its numerical controls. */
  @Test
  void documentedGasExampleIsExecutable() {
    SystemInterface gas = new SystemSrkCPAstatoil(293.15, 50.0);
    double waterMoleFraction = 100.0e-6;
    double dryMoleFraction = 1.0 - waterMoleFraction;
    gas.addComponent("methane", 0.90 * dryMoleFraction);
    gas.addComponent("ethane", 0.08 * dryMoleFraction);
    gas.addComponent("propane", 0.02 * dryMoleFraction);
    gas.addComponent("water", waterMoleFraction);
    gas.setMixingRule(10);

    WaterPhaseBoundaryResult boundaries = new ThermodynamicOperations(gas).cpaWaterPhaseBoundaryTemperatures();
    double liquidWaterC = boundaries.getLiquidWater().getTemperatureC();
    double iceC = boundaries.getIce().getTemperatureC();
    double hydrateC = boundaries.getHydrate().getTemperatureC();

    assertTrue(boundaries.isComplete());
    assertEquals(-6.73048, liquidWaterC, 0.001);
    assertEquals(-5.99035, iceC, 0.001);
    assertEquals(-2.79076, hydrateC, 0.001);
  }

  /**
   * Oil and aqueous inhibitors must remain in both phase-equilibrium calculations instead of becoming a gas feed.
   *
   * @param inhibitor supplied aqueous inhibitor
   * @throws Exception if the independent hydrate comparison fails
   */
  @ParameterizedTest
  @ValueSource(strings = {"MEG", "methanol"})
  void retainsOilAndAqueousInhibitorInventory(String inhibitor) throws Exception {
    SystemInterface fluid = new SystemSrkCPAstatoil(293.15, 50.0);
    fluid.addComponent("methane", 0.70);
    fluid.addComponent("ethane", 0.05);
    fluid.addComponent("n-heptane", 0.10);
    fluid.addComponent("water", 0.10);
    fluid.addComponent(inhibitor, 0.05);
    fluid.setMixingRule(10);
    assertFalse(fluid.doMultiPhaseCheck(), "Exercise automatic multiphase setup without changing the caller flag");
    fluid.setBinaryInteractionParameter("methane", "n-heptane", 0.02);
    byte[] original = SerializationUtils.serialize(fluid);
    WaterPhaseBoundaryResult result = new ThermodynamicOperations(fluid).cpaWaterPhaseBoundaryTemperatures();
    assertArrayEquals(original, SerializationUtils.serialize(fluid));
    assertEquals(Status.NOT_APPLICABLE, result.getLiquidWater().getStatus());
    assertTrue(Double.isNaN(result.getLiquidWater().getTemperatureC()));
    assertFalse(result.isComplete());
    assertEquals(0.05, result.getMoleFractions().get(inhibitor), 1.0e-14);
    assertTrue(result.getHydrate().isConverged(), result.getHydrate().getDiagnostic());
    assertTrue(result.getHydrate().getPhaseReference().contains("x_" + inhibitor + "="));
    assertTrue(Double.isFinite(result.getHydrate().getWaterActivity()));
    SystemInterface independent = fluid.clone();
    independent.setSolidPhaseCheck(false);
    independent.setHydrateCheck(true);
    new ThermodynamicOperations(independent).hydrateFormationTemperature();
    assertEquals(independent.getTemperature("C"), result.getHydrate().getTemperatureC(), 1.0e-6);
    assertTrue(result.getIce().isConverged(), result.getIce().getDiagnostic());
    assertTrue(result.getIce().getPhaseReference().contains("x_" + inhibitor + "="));
  }

  /** Characterized pseudo-components are retained by cloning rather than reconstructed from component names. */
  @Test
  void retainsCharacterizedOil() {
    SystemInterface fluid = new SystemSrkCPAstatoil(293.15, 50.0);
    fluid.addComponent("methane", 0.75);
    fluid.addComponent("water", 0.10);
    fluid.addComponent("MEG", 0.05);
    fluid.addTBPfraction("oil-cut", 0.10, 0.20, 0.85);
    fluid.setMixingRule(10);
    fluid.setMultiPhaseCheck(true);
    byte[] original = SerializationUtils.serialize(fluid);
    WaterPhaseBoundaryResult result = new ThermodynamicOperations(fluid).cpaWaterPhaseBoundaryTemperatures();
    assertArrayEquals(original, SerializationUtils.serialize(fluid));
    assertTrue(result.getMoleFractions().keySet().toString().contains("oil-cut"));
    assertEquals(Status.NOT_APPLICABLE, result.getLiquidWater().getStatus());
    assertTrue(result.getHydrate().isConverged(), result.getHydrate().getDiagnostic());
  }

  /** Invalid input must fail before any state or composition mutation. */
  @Test
  void rejectsInvalidInputsBeforeMutation() {
    for (double pressure : new double[] {0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY}) {
      SystemInterface invalid = gas(false, 50.0);
      invalid.setPressure(pressure);
      byte[] original = SerializationUtils.serialize(invalid);
      assertThrows(IllegalArgumentException.class, () -> new CpaWaterPhaseBoundaryCalculator(invalid));
      assertArrayEquals(original, SerializationUtils.serialize(invalid));
    }
    SystemInterface dry = new SystemSrkCPAstatoil(293.15, 50.0);
    dry.addComponent("methane", 1.0);
    dry.setMixingRule(10);
    assertThrows(IllegalArgumentException.class, () -> new CpaWaterPhaseBoundaryCalculator(dry));
    SystemInterface wrongRule = gas(false, 50.0);
    wrongRule.setMixingRule("classic");
    assertThrows(IllegalArgumentException.class, () -> new CpaWaterPhaseBoundaryCalculator(wrongRule));
    SystemInterface otherModel = new SystemSrkEos(293.15, 50.0);
    otherModel.addComponent("methane", 0.9999);
    otherModel.addComponent("water", 0.0001);
    otherModel.setMixingRule("classic");
    assertThrows(IllegalArgumentException.class, () -> new CpaWaterPhaseBoundaryCalculator(otherModel));
  }

  /** Unavailable outcomes cannot expose a last trial temperature or remove a different valid boundary. */
  @Test
  void unavailableBoundaryKeepsOtherResultsAndClearsTemperature() {
    BoundaryResult valid = new BoundaryResult(Status.CONVERGED, -5.0, 0.0, 0.8, "checked", "aqueous");
    BoundaryResult failed = new BoundaryResult(Status.FAILED, 999.0, Double.NaN, Double.NaN, "no bracket", "ice");
    WaterPhaseBoundaryResult result = new WaterPhaseBoundaryResult(50.0, "test",
        Collections.singletonMap("water", 0.0001), valid, failed, valid);
    assertTrue(result.getLiquidWater().isConverged());
    assertTrue(result.getHydrate().isConverged());
    assertFalse(result.getIce().isConverged());
    assertTrue(Double.isNaN(result.getIce().getTemperatureC()));
    assertFalse(result.isComplete());
  }

  /** Pure water has an ice boundary but cannot acquire a fabricated guest-free gas-hydrate onset. */
  @Test
  void pureWaterHasNoHydrateGuestInventory() {
    SystemInterface water = new SystemSrkCPAstatoil(293.15, 50.0);
    water.addComponent("water", 1.0);
    water.setMixingRule(10);
    WaterPhaseBoundaryResult result = new ThermodynamicOperations(water).cpaWaterPhaseBoundaryTemperatures();
    assertTrue(result.getIce().isConverged(), result.getIce().getDiagnostic());
    assertEquals(Status.NOT_APPLICABLE, result.getHydrate().getStatus());
    assertTrue(Double.isNaN(result.getHydrate().getTemperatureC()));
    assertTrue(result.getHydrate().getDiagnostic().contains("hydrate-former inventory"));
  }

  /**
   * Creates a normalized dry-composition natural gas with 100 ppmv water on a wet basis.
   *
   * @param lean whether to use the lean gas
   * @param pressure absolute pressure in bara
   * @return configured CPA/rule 10 fluid
   */
  private SystemInterface gas(boolean lean, double pressure) {
    SystemInterface fluid = new SystemSrkCPAstatoil(293.15, pressure);
    double dry = 0.9999;
    fluid.addComponent("methane", (lean ? 0.95 : 0.90) * dry);
    fluid.addComponent("ethane", (lean ? 0.03 : 0.08) * dry);
    if (lean) {
      fluid.addComponent("nitrogen", 0.01 * dry);
      fluid.addComponent("CO2", 0.01 * dry);
    } else {
      fluid.addComponent("propane", 0.02 * dry);
    }
    fluid.addComponent("water", 0.0001);
    fluid.setMixingRule(10);
    return fluid;
  }
}
