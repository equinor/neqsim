package neqsim.process.safety.release;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/** Conservative gas blowdown regressions for issue 3905, independent of the reporting interval. */
class LeakModelBlowdownTest extends neqsim.NeqSimTest {
  private static SystemInterface gas(double pressure) {
    SystemInterface gas = new SystemSrkEos(300.0, pressure);
    gas.addComponent("nitrogen", 1.0);
    gas.setMixingRule("classic");
    return gas;
  }

  private static LeakModel leak(SystemInterface gas, double backPressure, double diameter) {
    return LeakModel.builder().fluid(gas).holeDiameter(diameter).vesselVolume(1.0).backPressure(backPressure).build();
  }

  private static void balanced(SourceTermResult result) {
    SystemInterface initial = result.getInitialInventory();
    SystemInterface remaining = result.getFinalInventory();
    assertEquals(1.0, initial.getVolume("m3"), 1e-10);
    assertEquals(1.0, remaining.getVolume("m3"), 1e-10);
    assertEquals(initial.getMass("kg"), remaining.getMass("kg") + result.getTotalMassReleased(), 1e-10);
    assertEquals(initial.getInternalEnergy("J"), remaining.getInternalEnergy("J") + result.getReleasedEnergyJ(),
        Math.max(1.0, Math.abs(initial.getInternalEnergy("J"))) * 1e-7);
    for (int c = 0; c < initial.getNumberOfComponents(); c++) {
      double mass0 = initial.getComponent(c).getNumberOfmoles() * initial.getComponent(c).getMolarMass();
      double mass1 = remaining.getComponent(c).getNumberOfmoles() * remaining.getComponent(c).getMolarMass();
      assertEquals(mass0, mass1 + result.getTotalMassReleased() * mass0 / initial.getMass("kg"), 1e-10);
    }
    int last = result.getNumberOfPoints() - 1;
    assertEquals(remaining.getTemperature(), result.getTemperature()[last], 0.0);
    assertEquals(remaining.getPressure() * 1e5, result.getPressure()[last], 0.0);
  }

  @Test
  void independentAdiabaticNitrogenBenchmarkConvergesUnderRefinement() {
    double[] pressures = new double[3];
    double[] steps = {0.5, 0.25, 0.125};
    for (int i = 0; i < steps.length; i++) {
      SourceTermResult result = leak(gas(2.0), 10000.0, 0.01).calculateSourceTerm(10.0, steps[i]);
      // Constant-cp ideal nitrogen, R=8314 J/(kmol K), MW=28.0134 kg/kmol, gamma=1.4.
      // dm/dt=-C*m^((gamma+1)/2) gives x=[1+(gamma-1)*mdot0*t/(2*m0)]^(-2/(gamma-1)).
      double gamma = 1.4;
      double molarMass = 0.0280134;
      double rSpecific = 8.314 / molarMass;
      double mass0 = 2e5 / (rSpecific * 300.0);
      double rate0 = 0.62 * Math.PI * 0.01 * 0.01 / 4.0 * 2e5 * Math.sqrt(gamma / (rSpecific * 300.0))
          * Math.pow(2.0 / (gamma + 1.0), (gamma + 1.0) / (2.0 * (gamma - 1.0)));
      double fraction = Math.pow(1.0 + (gamma - 1.0) * rate0 * 10.0 / (2.0 * mass0), -2.0 / (gamma - 1.0));
      SystemInterface remaining = result.getFinalInventory();
      pressures[i] = remaining.getPressure() * 1e5;
      // Dilute SRK nitrogen has small real-gas and temperature-dependent heat-capacity corrections.
      assertEquals(mass0 * fraction, remaining.getMass("kg"), mass0 * 0.005);
      assertEquals(2e5 * Math.pow(fraction, gamma), pressures[i], 2e5 * 0.005);
      assertEquals(300.0 * Math.pow(fraction, gamma - 1.0), remaining.getTemperature(), 300.0 * 0.005);
      balanced(result);
    }
    assertTrue(Math.abs(pressures[1] - pressures[2]) < 0.6 * Math.abs(pressures[0] - pressures[1]));
  }

  @Test
  void reportedReproducerRetainsPhysicalInventoryAtTwentySeconds() {
    for (double step : new double[] {2.0, 1.0, 0.5, 0.25}) {
      SourceTermResult result = leak(gas(10.0), 101325.0, 0.01).calculateSourceTerm(20.0, step);
      assertTrue(result.getFinalInventory().getPressure() > 7.0);
      assertTrue(result.getTotalMassReleased() > 1.8);
      assertTrue(result.getTotalMassReleased() < 2.5);
      balanced(result);
    }
  }

  @Test
  void zeroAndShortenedFinalIntervalsDoNotRemoveAnExtraSampleWorthOfMass() {
    SystemInterface gas = gas(10.0);
    gas.addComponent("methane", 0.2);
    gas.setMixingRule("classic");
    LeakModel model = leak(gas, 101325.0, 0.01);
    SourceTermResult zero = model.calculateSourceTerm(0.0, 1.0);
    assertArrayEquals(new double[] {0.0}, zero.getTime(), 0.0);
    assertEquals(0.0, zero.getTotalMassReleased(), 0.0);
    assertEquals(0.0, zero.getReleasedEnergyJ(), 0.0);
    SourceTermResult shortStep = model.calculateSourceTerm(0.1, 1.0);
    double initialRate = zero.getMassFlowRate()[0];
    assertEquals(model.calculateMassFlowRate(zero.getInitialInventory()), initialRate, 1e-12);
    assertArrayEquals(new double[] {0.0, 0.1}, shortStep.getTime(), 0.0);
    assertEquals(initialRate * 0.1, shortStep.getTotalMassReleased(), 1e-12);
    assertEquals(initialRate * 0.1 * zero.getInitialInventory().getEnthalpy("J/kg"), shortStep.getReleasedEnergyJ(),
        1e-9);
    assertNotEquals(initialRate * 0.1 * zero.getInitialInventory().getInternalEnergy("J/kg"),
        shortStep.getReleasedEnergyJ(), 1.0);
    assertArrayEquals(new double[] {0.0, 1.0, 2.0, 2.5}, model.calculateSourceTerm(2.5, 1.0).getTime(), 0.0);
    balanced(shortStep);
    assertEquals(1.2, gas.getTotalNumberOfMoles(), 1e-12);
    assertEquals(300.0, gas.getTemperature(), 0.0);
    SystemInterface copy = shortStep.getFinalInventory();
    copy.setTemperature(1000.0);
    assertTrue(shortStep.getFinalInventory().getTemperature() < 300.0);
  }

  @Test
  void receivingPressureEventClosesEnergyAndFillsAllRemainingSamples() {
    SourceTermResult result = leak(gas(1.1), 101325.0, 0.05).calculateSourceTerm(5.5, 2.0);
    assertArrayEquals(new double[] {0.0, 2.0, 4.0, 5.5}, result.getTime(), 0.0);
    for (double pressure : result.getPressure()) {
      assertTrue(pressure >= 101325.0);
    }
    assertEquals(101325.0, result.getPressure()[3], 0.002);
    assertEquals(0.0, result.getMassFlowRate()[3], 0.0);
    assertEquals(0.0, result.getJetMomentum()[3], 0.0);
    assertTrue(result.getFinalInventory().getMass("kg") > 0.0);
    balanced(result);
    SourceTermResult noFlow = leak(gas(1.0), 101325.0, 0.05).calculateSourceTerm(2.5, 1.0);
    assertEquals(0.0, noFlow.getTotalMassReleased(), 0.0);
    assertEquals(1e5, noFlow.getPressure()[3], 0.0);
    balanced(noFlow);
  }

  @Test
  void invalidInputsAndCondensingTrajectoryFailExplicitlyWithoutMutatingFluid() {
    LeakModel model = leak(gas(10.0), 101325.0, 0.01);
    assertThrows(IllegalArgumentException.class, () -> model.calculateSourceTerm(-1.0, 1.0));
    assertThrows(IllegalArgumentException.class, () -> model.calculateSourceTerm(1.0, 0.0));
    assertThrows(IllegalArgumentException.class, () -> model.calculateSourceTerm(Double.NaN, 1.0));
    assertThrows(IllegalArgumentException.class, () -> model.calculateSourceTerm(1.0, Double.POSITIVE_INFINITY));
    SystemInterface methane = new SystemSrkEos(300.0, 50.0);
    methane.addComponent("methane", 1.0);
    methane.addComponent("ethane", 0.1);
    methane.setMixingRule("classic");
    IllegalStateException failure = assertThrows(IllegalStateException.class,
        () -> leak(methane, 101325.0, 0.15).calculateSourceTerm(60.0, 5.0));
    assertTrue(failure.getMessage().contains("BLOWDOWN_PHASE_BOUNDARY"), failure.getMessage());
    assertEquals(300.0, methane.getTemperature(), 0.0);
    assertEquals(50.0, methane.getPressure(), 0.0);
    SystemInterface liquid = new SystemSrkEos(300.0, 20.0);
    liquid.addComponent("propane", 1.0);
    liquid.setMixingRule("classic");
    assertThrows(IllegalStateException.class, () -> leak(liquid, 101325.0, 0.01).calculateSourceTerm(0.1, 0.1));
    SystemInterface invalid = new InvalidGammaFluid();
    invalid.addComponent("nitrogen", 1.0);
    invalid.setMixingRule("classic");
    IllegalStateException invalidProperty = assertThrows(IllegalStateException.class,
        () -> leak(invalid, 101325.0, 0.01).calculateSourceTerm(1.0, 1.0));
    assertTrue(invalidProperty.getMessage().contains("BLOWDOWN_PROPERTIES_INVALID"));
  }

  private static final class InvalidGammaFluid extends SystemSrkEos {
    private static final long serialVersionUID = 1L;

    private InvalidGammaFluid() {
      super(300.0, 10.0);
    }

    @Override
    public double getGamma() {
      return Double.NaN;
    }
  }
}
