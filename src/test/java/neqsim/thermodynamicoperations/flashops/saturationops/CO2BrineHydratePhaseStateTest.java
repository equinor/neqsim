package neqsim.thermodynamicoperations.flashops.saturationops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemElectrolyteCPAstatoil;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.thermodynamicoperations.flashops.CO2BrinePhaseEquilibrium;

/** Conservation and fluid-equilibrium regressions for issue 3584. */
class CO2BrineHydratePhaseStateTest {
  static SystemInterface brine(double pressure, double saltPercent, double co2Moles) {
    SystemInterface fluid = new SystemElectrolyteCPAstatoil(283.15, pressure);
    fluid.addComponent("CO2", co2Moles);
    fluid.addComponent("water", 1.0 / 0.01801528);
    if (saltPercent > 0.0) {
      double saltMoles = saltPercent / (100.0 - saltPercent) / 0.05844277;
      fluid.addComponent("Na+", saltMoles);
      fluid.addComponent("Cl-", saltMoles);
    }
    fluid.setMixingRule(10);
    fluid.setHydrateCheck(true);
    fluid.setMultiPhaseCheck(true);
    return fluid;
  }

  @Test
  void concentratedBrineConvergesFromTheDefaultGuess() throws Exception {
    SystemInterface fluid = brine(40.0, 10.0, 10.0);
    new ThermodynamicOperations(fluid).hydrateFormationTemperature();
    assertEndpoint(fluid, true);
    assertEquals(277.2851, fluid.getTemperature(), 0.01);
  }

  @Test
  void denseCo2EndpointAlsoRequiresFluidFugacityEquality() throws Exception {
    SystemInterface fluid = brine(100.0, 10.0, 10.0);
    new ThermodynamicOperations(fluid).hydrateFormationTemperature(278.15);
    assertEndpoint(fluid, true);
  }

  @ParameterizedTest
  @CsvSource({ "40,10,10,true", "200,10,10,true", "100,5,2,true", "100,5,0.5,false", "100,5,1,false" })
  void independentInitializationsSelectTheSameState(double pressure, double salt, double co2, boolean saturated)
      throws Exception {
    double reference = Double.NaN;
    for (double guess : new double[] { 273.15, 283.15 }) {
      SystemInterface fluid = brine(pressure, salt, co2);
      ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
      ops.hydrateFormationTemperature(guess);
      assertEndpoint(fluid, saturated);
      HydrateEquilibriumDiagnostics result = ((HydrateFormationTemperatureFlash) ops.getOperation()).getDiagnostics();
      assertTrue(result.isConverged());
      assertTrue(result.hasAqueousPhase());
      assertEquals(saturated, result.hasCO2RichPhase());
      assertEquals(saturated, result.isSaturatedCO2Boundary());
      assertEquals(saturated, result.getMinimumCo2TrialDistance() < 0.0);
      assertTrue(result.getComponentBalanceResidual() < 1.0e-10);
      assertTrue(result.getChargeResidual() < 1.0e-10);
      if (Double.isFinite(reference)) {
        assertEquals(reference, fluid.getTemperature(), 2.0e-4);
      }
      reference = fluid.getTemperature();
      fluid.init(1);
      assertEndpoint(fluid, saturated);
      SystemInterface copy = fluid.clone();
      new ThermodynamicOperations(copy).hydrateFormationTemperature(guess);
      assertEquals(reference, copy.getTemperature(), 2.0e-4);
      assertEndpoint(copy, saturated);
    }
  }

  @ParameterizedTest
  @CsvSource({ "20,5", "39,10", "41,10", "60,5", "100,9.5", "100,10.5", "190,10", "210,10" })
  void adjacentPressureAndSalinityCasesRemainConservative(double pressure, double salt) throws Exception {
    SystemInterface fluid = brine(pressure, salt, 10.0);
    new ThermodynamicOperations(fluid).hydrateFormationTemperature();
    assertEndpoint(fluid, true);
  }

  @Test
  void highPressureSalinityTrendUsesSaturatedStates() throws Exception {
    double previous = Double.POSITIVE_INFINITY;
    for (double salt : new double[] { 0.0, 5.0, 10.0 }) {
      SystemInterface fluid = brine(200.0, salt, 10.0);
      new ThermodynamicOperations(fluid).hydrateFormationTemperature();
      assertEndpoint(fluid, true);
      assertTrue(fluid.getTemperature() < previous);
      previous = fluid.getTemperature();
    }
  }

  @Test
  void diagnosticsAreSerializableSnapshotsAndFailedRunsAreExplicit() throws Exception {
    SystemInterface fluid = brine(40.0, 10.0, 10.0);
    fluid.setMultiPhaseCheck(false);
    HydrateFormationTemperatureFlash flash = new HydrateFormationTemperatureFlash(fluid);
    flash.run();
    assertFalse(fluid.doMultiPhaseCheck());
    HydrateEquilibriumDiagnostics snapshot = flash.getDiagnostics();
    assertTrue(snapshot.isSaturatedCO2Boundary());
    assertThrows(UnsupportedOperationException.class, () -> snapshot.getPhaseTypes().add("gas"));
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
      output.writeObject(snapshot);
    }
    try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      HydrateEquilibriumDiagnostics restored = (HydrateEquilibriumDiagnostics) input.readObject();
      assertEquals(snapshot.getPhaseTypes(), restored.getPhaseTypes());
      assertEquals(snapshot.getTemperature(), restored.getTemperature());
    }
    fluid.addComponent("Na+", 0.1);
    assertThrows(IllegalStateException.class, flash::run);
    assertFalse(flash.getDiagnostics().isConverged());
    assertFalse(fluid.doMultiPhaseCheck());
    assertTrue(snapshot.isConverged(), "Subsequent mutations must not change the earlier snapshot");
  }

  @Test
  void mixedInhibitorsAndReactiveFluidsRetainTheirOwnSolver() {
    SystemInterface mixed = brine(50.0, 5.0, 10.0);
    mixed.addComponent("MEG", 1.0);
    assertFalse(CO2BrinePhaseEquilibrium.isApplicable(mixed));
    SystemInterface reactive = brine(50.0, 5.0, 10.0);
    reactive.chemicalReactionInit();
    assertFalse(CO2BrinePhaseEquilibrium.isApplicable(reactive));
  }

  static void assertEndpoint(SystemInterface fluid, boolean saturated) {
    assertTrue(fluid.hasPhaseType(PhaseType.AQUEOUS));
    assertEquals(saturated ? 2 : 1, fluid.getNumberOfPhases());
    double betaSum = 0.0;
    for (int phase = 0; phase < fluid.getNumberOfPhases(); phase++) {
      betaSum += fluid.getBeta(phase);
      double sum = 0.0;
      double charge = 0.0;
      for (int component = 0; component < fluid.getNumberOfComponents(); component++) {
        double x = fluid.getPhase(phase).getComponent(component).getx();
        assertTrue(Double.isFinite(x) && x >= 0.0 && x <= 1.0);
        sum += x;
        double ionCharge = fluid.getPhase(phase).getComponent(component).getIonicCharge();
        charge += x * ionCharge;
        if (ionCharge != 0.0 && fluid.getPhase(phase).getType() != PhaseType.AQUEOUS) {
          assertTrue(x <= 1.0e-40);
        }
      }
      assertEquals(1.0, sum, 1.0e-9);
      assertEquals(0.0, charge, 1.0e-10);
    }
    assertEquals(1.0, betaSum, 1.0e-12);
    for (int component = 0; component < fluid.getNumberOfComponents(); component++) {
      double recovered = 0.0;
      for (int phase = 0; phase < fluid.getNumberOfPhases(); phase++) {
        recovered += fluid.getBeta(phase) * fluid.getPhase(phase).getComponent(component).getx();
      }
      assertEquals(fluid.getPhase(0).getComponent(component).getz(), recovered, 1.0e-10);
    }
    if (saturated) {
      for (String component : new String[] { "CO2", "water" }) {
        double ratio = fluid.getPhase(0).getFugacity(component) / fluid.getPhase(1).getFugacity(component);
        assertEquals(0.0, Math.log(ratio), 1.0e-8, component + " fluid fugacity residual");
      }
    }
    double hydrateRatio = fluid.getPhase(4).getFugacity("water") / fluid.getPhase("aqueous").getFugacity("water");
    assertEquals(0.0, 1.0 - hydrateRatio, 1.0e-6);
  }
}
