package neqsim.thermodynamicoperations.flashops.saturationops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.system.SystemElectrolyteCPAstatoil;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Regression tests for the n-butane electrolyte phase-behavior and hydrate cases.
 *
 * @author ESOL
 * @version 1.0
 */
@Tag("slow")
public class NButaneIssueTest {
  /**
   * Checks each original TP-flash state once, including the no-n-butane comparison.
   *
   * @param temperature temperature in kelvin
   * @param includeNButane whether the feed contains n-butane
   */
  @ParameterizedTest(name = "TP flash at {0} K, n-butane={1}")
  @CsvSource({"283.15, true", "268.15, true", "263.15, true", "258.15, true", "263.15, false"})
  void testTPflashPhaseStates(double temperature, boolean includeNButane) {
    SystemInterface fluid = createFluid(temperature, includeNButane);
    new ThermodynamicOperations(fluid).TPflash();

    assertEquals(temperature, fluid.getTemperature(), 1.0e-10, "TP flash must preserve temperature");
    assertEquals(includeNButane, fluid.getPhase(0).hasComponent("n-butane"));
    assertValidPhaseState(fluid);
  }

  /**
   * Checks hydrate formation for both original feeds at 50 bara.
   *
   * @param includeNButane whether the feed contains n-butane
   * @throws Exception if the hydrate calculation fails
   */
  @ParameterizedTest(name = "Hydrate formation with n-butane={0}")
  @ValueSource(booleans = {true, false})
  void testHydrateFormationTemperature(boolean includeNButane) throws Exception {
    SystemInterface fluid = createFluid(283.15, includeNButane);
    fluid.setHydrateCheck(true);
    ThermodynamicOperations operations = new ThermodynamicOperations(fluid);
    operations.hydrateFormationTemperature();

    HydrateFormationTemperatureFlash flash = (HydrateFormationTemperatureFlash) operations.getOperation();
    assertTrue(flash.isConverged(), "Hydrate flash must converge; water fugacity residual=" + flash.getLastResidual());
    double hydrateTemperatureC = fluid.getTemperature() - 273.15;
    assertTrue(Double.isFinite(hydrateTemperatureC), "Hydrate temperature must be finite");
    assertTrue(hydrateTemperatureC > -30.0 && hydrateTemperatureC < 15.0,
        "Hydrate temperature with MEG and brine at 50 bara must be between -30 and 15 C, got " + hydrateTemperatureC);
    assertEquals(50.0, fluid.getPressure(), 1.0e-10, "Hydrate calculation must preserve pressure");
  }

  /** Verifies finite physical properties and neutral charge for the original n-butane component check. */
  @Test
  void testNButaneProperties() {
    SystemInterface fluid = new SystemElectrolyteCPAstatoil(273.15, 50.0);
    fluid.addComponent("n-butane", 1.0);
    fluid.setMixingRule(10);
    fluid.init(0);

    ComponentInterface component = fluid.getPhase(0).getComponent("n-butane");
    assertTrue(Double.isFinite(component.getTC()) && component.getTC() > 0.0);
    assertTrue(Double.isFinite(component.getPC()) && component.getPC() > 0.0);
    assertTrue(Double.isFinite(component.getAcentricFactor()));
    assertTrue(Double.isFinite(component.getMolarMass()) && component.getMolarMass() > 0.0);
    assertEquals(0.0, component.getIonicCharge(), 0.0);
  }

  /**
   * Creates the existing MEG/brine feed with or without its original n-butane amount.
   *
   * @param temperature temperature in kelvin
   * @param includeNButane whether to add n-butane
   * @return configured electrolyte CPA fluid
   */
  private SystemInterface createFluid(double temperature, boolean includeNButane) {
    SystemInterface fluid = new SystemElectrolyteCPAstatoil(temperature, 50.0);
    fluid.addComponent("water", 0.494505);
    fluid.addComponent("MEG", 0.164835);
    fluid.addComponent("methane", 0.247253);
    fluid.addComponent("ethane", 0.0164835);
    fluid.addComponent("propane", 0.010989);
    fluid.addComponent("i-butane", 0.00549451);
    if (includeNButane) {
      fluid.addComponent("n-butane", 0.00549451);
    }
    fluid.addComponent("Na+", 0.0274725);
    fluid.addComponent("Cl-", 0.0274725);
    fluid.setMixingRule(10);
    fluid.setMultiPhaseCheck(true);
    return fluid;
  }

  /**
   * Checks finite, nonnegative and normalized phase fractions and compositions after a TP flash.
   *
   * @param fluid flashed fluid
   */
  private void assertValidPhaseState(SystemInterface fluid) {
    assertEquals(50.0, fluid.getPressure(), 1.0e-10, "TP flash must preserve pressure");
    assertTrue(fluid.getNumberOfPhases() > 0, "A flashed fluid must contain a phase");
    double phaseFractionSum = 0.0;
    for (int phaseIndex = 0; phaseIndex < fluid.getNumberOfPhases(); phaseIndex++) {
      double phaseFraction = fluid.getBeta(phaseIndex);
      assertTrue(Double.isFinite(phaseFraction) && phaseFraction >= 0.0 && phaseFraction <= 1.0,
          "Invalid fraction for phase " + phaseIndex);
      phaseFractionSum += phaseFraction;
      PhaseInterface phase = fluid.getPhase(phaseIndex);
      double compositionSum = 0.0;
      for (int componentIndex = 0; componentIndex < phase.getNumberOfComponents(); componentIndex++) {
        double moleFraction = phase.getComponent(componentIndex).getx();
        assertTrue(Double.isFinite(moleFraction) && moleFraction >= 0.0 && moleFraction <= 1.0,
            "Invalid mole fraction in phase " + phaseIndex + ", component " + componentIndex);
        compositionSum += moleFraction;
      }
      assertEquals(1.0, compositionSum, 1.0e-8, "Phase composition must be normalized");
    }
    assertEquals(1.0, phaseFractionSum, 1.0e-8, "Phase fractions must be normalized");
  }
}
