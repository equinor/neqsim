package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/** Regression coverage for issue #3890: an inactive solid must preserve fluid equilibrium. */
class SolidCheckFluidEquilibriumTest extends neqsim.NeqSimTest {
  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void methaneSolidCheckPreservesTwoPhaseFluid(boolean multiphase) {
    SystemInterface expected = fluid();
    new ThermodynamicOperations(expected).TPflash();
    assertEquals(0.092231647152, expected.getPhase(PhaseType.GAS).getBeta(), 1e-8);
    SystemInterface actual = fluid();
    actual.setSolidPhaseCheck("methane");
    actual.setMultiPhaseCheck(multiphase);
    actual.init(0);
    actual.init(1);
    for (int run = 0; run < 3; run++) {
      new ThermodynamicOperations(actual).TPflash();
      assertFluidEquilibrium(expected, actual);
      assertEquals(multiphase, actual.doMultiPhaseCheck());
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void selectedSolidPreservesMultiphaseChoice(boolean multiphase) {
    SystemInterface fluid = fluid();
    fluid.setMultiPhaseCheck(multiphase);
    fluid.setSolidPhaseCheck("methane");
    assertEquals(multiphase, fluid.doMultiPhaseCheck());
    SystemInterface expected = fluid();
    new ThermodynamicOperations(expected).TPflash();
    new ThermodynamicOperations(fluid).TPflash();
    assertFluidEquilibrium(expected, fluid);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void allSolidCheckPreservesMultiphaseChoice(boolean multiphase) {
    SystemInterface fluid = fluid();
    fluid.setMultiPhaseCheck(multiphase);
    fluid.setSolidPhaseCheck(true);
    assertEquals(multiphase, fluid.doMultiPhaseCheck());
    SystemInterface expected = fluid();
    new ThermodynamicOperations(expected).TPflash();
    new ThermodynamicOperations(fluid).TPflash();
    assertFluidEquilibrium(expected, fluid);
  }

  @Test
  void methaneSolidFugacityPublishesReturnedCoefficient() {
    SystemInterface fluid = fluid();
    fluid.setSolidPhaseCheck("methane");
    PhaseInterface solid = fluid.getPhases()[3];
    ComponentInterface methane = solid.getComponent("methane");
    double result = methane.fugcoef(solid);
    assertTrue(Double.isFinite(result) && result > 0.0);
    assertEquals(result, methane.getFugacityCoefficient(), 0.0);
    assertEquals(Math.log(result), methane.getLogFugacityCoefficient(), 0.0);
    // A previously evaluated empirical coefficient must not leak into a later exclusion check.
    methane.setFugacityCoefficient(0.01);
    assertEquals(result, methane.fugcoef(solid), 0.0);
    assertEquals(result, methane.getFugacityCoefficient(), 0.0);
  }

  @ParameterizedTest
  @ValueSource(doubles = {323.15, 333.15, 343.15})
  void inactiveSolidPreservesEquilibriumAcrossTemperatureScan(double temperature) {
    for (boolean multiphase : new boolean[] {false, true}) {
      SystemInterface expected = fluid();
      expected.setTemperature(temperature);
      expected.setMultiPhaseCheck(multiphase);
      new ThermodynamicOperations(expected).TPflash();
      SystemInterface actual = fluid();
      actual.setTemperature(temperature);
      actual.setMultiPhaseCheck(multiphase);
      actual.setSolidPhaseCheck("methane");
      new ThermodynamicOperations(actual).TPflash();
      assertFluidEquilibrium(expected, actual);
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void explicitSolidFlashPreservesInactiveMethaneFluid(boolean multiphase) {
    SystemInterface expected = fluid();
    new ThermodynamicOperations(expected).TPflash();
    SystemInterface actual = fluid();
    actual.setMultiPhaseCheck(multiphase);
    actual.setSolidPhaseCheck("methane");
    new ThermodynamicOperations(actual).TPSolidflash();
    assertFluidEquilibrium(expected, actual);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void fluidMultiphaseCheckCanBeEnabledBeforeOrAfterSolidAllocation(boolean solidFirst) {
    SystemInterface expected = threePhaseFluid();
    expected.setMultiPhaseCheck(true);
    new ThermodynamicOperations(expected).TPflash();
    assertEquals(3, expected.getNumberOfPhases());
    SystemInterface actual = threePhaseFluid();
    if (solidFirst) {
      actual.setSolidPhaseCheck("methane");
      actual.setMultiPhaseCheck(true);
    } else {
      actual.setMultiPhaseCheck(true);
      actual.setSolidPhaseCheck("methane");
    }
    new ThermodynamicOperations(actual).TPflash();
    assertTrue(actual.doMultiPhaseCheck());
    assertFluidEquilibrium(expected, actual);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void supportedCarbonDioxideStillFreezes(boolean multiphase) {
    SystemInterface fluid = new SystemSrkEos(190.0, 1.0);
    fluid.addComponent("CO2", 1.0);
    fluid.setMixingRule(2);
    fluid.setMultiPhaseCheck(multiphase);
    fluid.setSolidPhaseCheck("CO2");
    new ThermodynamicOperations(fluid).TPflash();
    assertTrue(fluid.hasSolidPhase());
    assertConserved(fluid);
  }

  private void assertFluidEquilibrium(SystemInterface expected, SystemInterface actual) {
    assertFalse(actual.hasSolidPhase());
    assertEquals(expected.getNumberOfPhases(), actual.getNumberOfPhases());
    assertTrue(actual.getNumberOfPhases() >= 2);
    for (int p = 0; p < expected.getNumberOfPhases(); p++) {
      PhaseInterface reference = expected.getPhase(p);
      PhaseInterface phase = actual.getPhase(reference.getType());
      assertEquals(reference.getBeta(), phase.getBeta(), 1e-7);
      for (int i = 0; i < actual.getNumberOfComponents(); i++) {
        assertEquals(reference.getComponent(i).getx(), phase.getComponent(i).getx(), 1e-7);
        double referenceFugacity = actual.getPhase(0).getComponent(i).getx()
            * actual.getPhase(0).getComponent(i).getFugacityCoefficient();
        double fugacity = phase.getComponent(i).getx() * phase.getComponent(i).getFugacityCoefficient();
        assertEquals(0.0, Math.log(fugacity / referenceFugacity), 1e-6, "fluid fugacity equality");
      }
    }
    assertConserved(actual);
  }

  private void assertConserved(SystemInterface fluid) {
    double betaSum = 0.0;
    for (int p = 0; p < fluid.getNumberOfPhases(); p++) {
      double beta = fluid.getBeta(p);
      assertTrue(Double.isFinite(beta) && beta >= 0.0 && beta <= 1.0);
      betaSum += beta;
      double xSum = 0.0;
      for (int i = 0; i < fluid.getNumberOfComponents(); i++) {
        double x = fluid.getPhase(p).getComponent(i).getx();
        assertTrue(Double.isFinite(x) && x >= 0.0 && x <= 1.0);
        xSum += x;
      }
      assertEquals(1.0, xSum, 1e-8, "normalized phase composition");
    }
    assertEquals(1.0, betaSum, 1e-8);
    for (int i = 0; i < fluid.getNumberOfComponents(); i++) {
      double moles = 0.0;
      double reconstructedZ = 0.0;
      for (int p = 0; p < fluid.getNumberOfPhases(); p++) {
        moles += fluid.getPhase(p).getComponent(i).getNumberOfMolesInPhase();
        reconstructedZ += fluid.getBeta(p) * fluid.getPhase(p).getComponent(i).getx();
      }
      assertEquals(fluid.getComponent(i).getNumberOfmoles(), moles, 1e-8, "component inventory");
      assertEquals(fluid.getComponent(i).getz(), reconstructedZ, 1e-8, "component fraction balance");
    }
  }

  private SystemInterface threePhaseFluid() {
    SystemInterface fluid = new SystemPrEos(308.15, 40.0);
    fluid.addComponent("methane", 0.8);
    fluid.addComponent("n-decane", 0.1);
    fluid.addComponent("water", 0.1);
    fluid.setMixingRule(2);
    return fluid;
  }

  private SystemInterface fluid() {
    SystemInterface fluid = new SystemSrkEos(333.15, 60.0);
    fluid.addComponent("methane", 0.30);
    fluid.addComponent("n-heptane", 0.70);
    fluid.setMixingRule(2);
    return fluid;
  }
}
