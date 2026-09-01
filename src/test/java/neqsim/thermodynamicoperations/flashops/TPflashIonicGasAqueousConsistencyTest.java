package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemElectrolyteCPAstatoil;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/** Regression coverage for constrained ionic GAS+AQUEOUS TP-flash finalization. */
class TPflashIonicGasAqueousConsistencyTest {
  private static final String[] COMPONENTS = { "methane", "water", "Na+", "Cl-" };
  private static final double[] AMOUNTS = { 0.1, 1.0, 0.001, 0.001 };

  @Test
  void ordinaryAndMultiphaseEndpointsAreBalancedAndEquivalent() {
    SystemInterface ordinary = createAndFlash(298.15, 10.01325, false, false);
    SystemInterface multiphase = createAndFlash(298.15, 10.01325, true, false);
    SystemInterface poorBeta = createAndFlash(298.15, 10.01325, false, true);

    assertValidIonicGasAqueousEndpoint(ordinary);
    assertValidIonicGasAqueousEndpoint(multiphase);
    assertEquivalentEndpoint(ordinary, multiphase);
    assertEquivalentEndpoint(ordinary, poorBeta);

    SystemInterface ordinaryReference = ordinary.clone();
    SystemInterface multiphaseReference = multiphase.clone();
    new ThermodynamicOperations(ordinary).TPflash();
    new ThermodynamicOperations(multiphase).TPflash();
    ordinary.init(3);
    multiphase.init(3);
    assertEquivalentEndpoint(ordinaryReference, ordinary);
    assertEquivalentEndpoint(multiphaseReference, multiphase);
  }

  @Test
  void changedPressureMatchesFreshNearbyReferences() {
    SystemInterface ordinary = createAndFlash(298.15, 10.01325, false, false);
    SystemInterface multiphase = createAndFlash(298.15, 10.01325, true, false);

    ordinary.setPressure(20.0, "bara");
    multiphase.setPressure(20.0, "bara");
    new ThermodynamicOperations(ordinary).TPflash();
    new ThermodynamicOperations(multiphase).TPflash();
    ordinary.init(3);
    multiphase.init(3);

    SystemInterface freshOrdinary = createAndFlash(298.15, 20.0, false, false);
    SystemInterface freshMultiphase = createAndFlash(298.15, 20.0, true, false);
    assertValidIonicGasAqueousEndpoint(ordinary);
    assertValidIonicGasAqueousEndpoint(multiphase);
    assertEquivalentEndpoint(freshOrdinary, ordinary);
    assertEquivalentEndpoint(freshMultiphase, multiphase);
    assertEquivalentEndpoint(ordinary, multiphase);
  }

  private SystemInterface createAndFlash(double temperature, double pressure, boolean multiphaseCheck,
      boolean poorBeta) {
    SystemInterface system = new SystemElectrolyteCPAstatoil(temperature, pressure);
    for (int component = 0; component < COMPONENTS.length; component++) {
      system.addComponent(COMPONENTS[component], AMOUNTS[component]);
    }
    system.setMixingRule(10);
    system.setMultiPhaseCheck(multiphaseCheck);
    if (poorBeta) {
      system.setBeta(0, 1.0e-10);
      system.setBeta(1, 1.0 - 1.0e-10);
    }
    new ThermodynamicOperations(system).TPflash();
    system.init(3);
    return system;
  }

  private void assertValidIonicGasAqueousEndpoint(SystemInterface system) {
    assertEquals(2, system.getNumberOfPhases());
    assertTrue(system.hasPhaseType(PhaseType.GAS));
    assertTrue(system.hasPhaseType(PhaseType.AQUEOUS));

    double betaSum = 0.0;
    for (int phase = 0; phase < system.getNumberOfPhases(); phase++) {
      double beta = system.getBeta(phase);
      assertTrue(Double.isFinite(beta) && beta >= 0.0 && beta <= 1.0);
      betaSum += beta;
      double compositionSum = 0.0;
      for (int component = 0; component < system.getPhase(phase).getNumberOfComponents(); component++) {
        double composition = system.getPhase(phase).getComponent(component).getx();
        assertTrue(Double.isFinite(composition) && composition >= 0.0 && composition <= 1.0);
        compositionSum += composition;
      }
      assertEquals(1.0, compositionSum, 1.0e-12);
      assertTrue(Double.isFinite(system.getPhase(phase).getZ()) && system.getPhase(phase).getZ() > 0.0);
    }
    assertEquals(1.0, betaSum, 1.0e-12);

    for (int component = 0; component < system.getPhase(0).getNumberOfComponents(); component++) {
      double reconstructed = 0.0;
      for (int phase = 0; phase < system.getNumberOfPhases(); phase++) {
        reconstructed += system.getBeta(phase) * system.getPhase(phase).getComponent(component).getx();
      }
      assertEquals(system.getPhase(0).getComponent(component).getz(), reconstructed, 1.0e-10);
    }

    int gasPhase = system.getPhaseNumberOfPhase("gas");
    int aqueousPhase = system.getPhaseNumberOfPhase("aqueous");
    for (int component = 0; component < system.getPhase(0).getNumberOfComponents(); component++) {
      boolean ion = system.getPhase(0).getComponent(component).getIonicCharge() != 0
          || system.getPhase(0).getComponent(component).isIsIon();
      if (ion) {
        assertTrue(system.getPhase(gasPhase).getComponent(component).getx() < 1.0e-40);
        continue;
      }
      double gasFugacity = system.getPhase(gasPhase).getComponent(component).getx()
          * system.getPhase(gasPhase).getComponent(component).getFugacityCoefficient();
      double aqueousFugacity = system.getPhase(aqueousPhase).getComponent(component).getx()
          * system.getPhase(aqueousPhase).getComponent(component).getFugacityCoefficient();
      assertTrue(gasFugacity > 0.0 && aqueousFugacity > 0.0);
      assertTrue(Math.abs(Math.log(gasFugacity / aqueousFugacity)) < 1.0e-8);
    }
  }

  private void assertEquivalentEndpoint(SystemInterface expected, SystemInterface actual) {
    assertEquals(expected.getNumberOfPhases(), actual.getNumberOfPhases());
    for (String phaseType : new String[] { "gas", "aqueous" }) {
      int expectedPhase = expected.getPhaseNumberOfPhase(phaseType);
      int actualPhase = actual.getPhaseNumberOfPhase(phaseType);
      assertEquals(expected.getBeta(expectedPhase), actual.getBeta(actualPhase), 1.0e-10);
      assertEquals(expected.getPhase(expectedPhase).getZ(), actual.getPhase(actualPhase).getZ(), 1.0e-10);
      for (int component = 0; component < expected.getPhase(expectedPhase).getNumberOfComponents(); component++) {
        assertEquals(expected.getPhase(expectedPhase).getComponent(component).getx(),
            actual.getPhase(actualPhase).getComponent(component).getx(), 1.0e-10);
      }
    }
    assertEquals(expected.getGibbsEnergy(), actual.getGibbsEnergy(), 1.0e-6);
  }
}
