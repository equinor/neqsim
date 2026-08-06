package neqsim.thermo.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.junit.jupiter.api.Test;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.phase.PhaseEos;
import neqsim.thermo.phase.PhaseGEInterface;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhasePitzer;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/** Regression tests for fixed-role EOS-gas / EOS-oil / GE-aqueous flashes. */
class SystemHybridEosGeFlashTest extends neqsim.NeqSimTest {
  /** Material-balance tolerance used by the public hybrid acceptance contract. */
  private static final double MATERIAL_BALANCE_TOLERANCE = 1.0e-7;

  /** Cross-phase logarithmic fugacity tolerance used by the public hybrid acceptance contract. */
  private static final double LOG_FUGACITY_TOLERANCE = 1.0e-5;

  /** Phase fraction below which a role is considered absent. */
  private static final double MATERIAL_PHASE_FRACTION = 1.0e-10;

  /**
   * Build the synthetic hydrocarbon/brine system from issue 2862.
   *
   * @return configured unflashed system
   */
  private SystemPitzer createGasOilAqueousSystem() {
    SystemPitzer system = new SystemPitzer(313.15, 50.0);
    system.addComponent("methane", 5.0);
    system.addComponent("n-heptane", 2.0);
    system.addComponent("water", 55.5);
    system.addComponent("Na+", 1.0);
    system.addComponent("Cl-", 1.0);
    system.setMixingRule("classic");
    system.setMultiPhaseCheck(true);
    return system;
  }

  /** Gas, oil and electrolyte-GE aqueous phases satisfy topology, balance and fugacity acceptance. */
  @Test
  void gasOilAqueousFlashMeetsAcceptanceContract() {
    SystemPitzer system = createGasOilAqueousSystem();
    new ThermodynamicOperations(system).TPflash();

    assertEquals(3, system.getNumberOfPhases());
    assertTrue(system.getEquationOfStatePhase() instanceof PhaseEos);
    assertTrue(system.getEosOilPhase() instanceof PhaseEos);
    assertTrue(system.getGeLiquidPhase() instanceof PhasePitzer);
    assertTrue(system.getGeLiquidPhase() instanceof PhaseGEInterface);
    assertEquals(0, system.getEosGasPhaseSlot());
    assertEquals(2, system.getEosOilPhaseSlot());
    assertEquals(1, system.getGeLiquidPhaseSlot());
    assertTrue(hasPhaseType(system, PhaseType.GAS));
    assertTrue(hasPhaseType(system, PhaseType.OIL));
    assertTrue(hasPhaseType(system, PhaseType.AQUEOUS));

    assertBalancedAndAtEquilibrium(system);
    PhaseInterface aqueous = findPhase(system, PhaseType.AQUEOUS);
    assertTrue(aqueous.getComponent("n-heptane").getFugacityCoefficient() > 1.0e6,
        "A non-water database solvent must use an aqueous Henry reference in Pitzer");
    for (int componentIndex = 0; componentIndex < aqueous.getNumberOfComponents(); componentIndex++) {
      ComponentInterface component = aqueous.getComponent(componentIndex);
      if (component.getIonicCharge() != 0 || component.isIsIon()) {
        assertTrue(component.getx() > 0.0);
        for (int phaseIndex = 0; phaseIndex < system.getNumberOfPhases(); phaseIndex++) {
          if (system.getPhase(phaseIndex) != aqueous) {
            assertTrue(system.getPhase(phaseIndex).getComponent(componentIndex).getx() <= 1.0e-40);
          }
        }
      }
    }
  }

  /** A trace oil role disappears from the active mapping and the same object is restored on a later flash. */
  @Test
  void disappearingOilRoleIsRestoredWithoutReplacement() {
    SystemPitzer system = new SystemPitzer(313.15, 50.0);
    system.addComponent("methane", 5.0);
    system.addComponent("n-heptane", 0.0);
    system.addComponent("water", 55.5);
    system.addComponent("Na+", 1.0);
    system.addComponent("Cl-", 1.0);
    system.setMixingRule("classic");
    system.setMultiPhaseCheck(true);
    PhaseInterface oilRole = system.getEosOilPhase();

    ThermodynamicOperations operations = new ThermodynamicOperations(system);
    operations.TPflash();
    assertEquals(2, system.getNumberOfPhases(), phaseDiagnostics(system));
    assertFalse(hasPhaseType(system, PhaseType.OIL));
    assertSame(oilRole, system.getEosOilPhase());
    assertBalancedAndAtEquilibrium(system);

    system.addComponent("n-heptane", 2.0);
    operations.TPflash();
    assertEquals(3, system.getNumberOfPhases());
    assertTrue(hasPhaseType(system, PhaseType.OIL));
    assertSame(oilRole, system.getEosOilPhase());
    assertBalancedAndAtEquilibrium(system);
  }

  /** Gas-oil and oil-aqueous limits retain only their material creation-order roles. */
  @Test
  void twoPhaseRoleCombinationsConverge() {
    SystemPitzer gasOil = new SystemPitzer(313.15, 50.0);
    gasOil.addComponent("methane", 5.0);
    gasOil.addComponent("n-heptane", 2.0);
    gasOil.addComponent("water", 0.0);
    gasOil.setMixingRule("classic");
    gasOil.setMultiPhaseCheck(true);
    new ThermodynamicOperations(gasOil).TPflash();
    assertEquals(2, gasOil.getNumberOfPhases(), phaseDiagnostics(gasOil));
    assertTrue(hasPhaseType(gasOil, PhaseType.GAS));
    assertTrue(hasPhaseType(gasOil, PhaseType.OIL));
    assertFalse(hasPhaseType(gasOil, PhaseType.AQUEOUS));
    assertBalancedAndAtEquilibrium(gasOil);

    SystemPitzer oilAqueous = new SystemPitzer(298.15, 100.0);
    oilAqueous.addComponent("methane", 0.0);
    oilAqueous.addComponent("n-heptane", 2.0);
    oilAqueous.addComponent("water", 55.5);
    oilAqueous.addComponent("Na+", 1.0);
    oilAqueous.addComponent("Cl-", 1.0);
    oilAqueous.setMixingRule("classic");
    oilAqueous.setMultiPhaseCheck(true);
    new ThermodynamicOperations(oilAqueous).TPflash();
    assertEquals(2, oilAqueous.getNumberOfPhases(), phaseDiagnostics(oilAqueous));
    assertFalse(hasPhaseType(oilAqueous, PhaseType.GAS));
    assertTrue(hasPhaseType(oilAqueous, PhaseType.OIL));
    assertTrue(hasPhaseType(oilAqueous, PhaseType.AQUEOUS));
    assertBalancedAndAtEquilibrium(oilAqueous);
  }

  /** Nearby water-rich gas-condensate states retain finite balanced hybrid solutions. */
  @Test
  void waterRichGasCondensateNearbyStatesConverge() {
    double[][] conditions = new double[][] { { 303.15, 40.0 }, { 323.15, 60.0 } };
    for (double[] condition : conditions) {
      SystemPitzer system = createGasOilAqueousSystem();
      system.setTemperature(condition[0]);
      system.setPressure(condition[1]);
      new ThermodynamicOperations(system).TPflash();
      assertTrue(system.getNumberOfPhases() >= 2, phaseDiagnostics(system));
      assertTrue(hasPhaseType(system, PhaseType.AQUEOUS));
      assertBalancedAndAtEquilibrium(system);
    }
  }

  /** Density reordering, cloning and Java serialization preserve creation-order role ownership. */
  @Test
  void rolesSurviveReorderingCloneSerializationAndRepeatedFlash() throws Exception {
    SystemPitzer system = createGasOilAqueousSystem();
    PhaseInterface gasRole = system.getEquationOfStatePhase();
    PhaseInterface oilRole = system.getEosOilPhase();
    PhaseInterface aqueousRole = system.getGeLiquidPhase();
    ThermodynamicOperations operations = new ThermodynamicOperations(system);

    operations.TPflash();
    system.orderByDensity();
    operations.TPflash();
    assertSame(gasRole, system.getEquationOfStatePhase());
    assertSame(oilRole, system.getEosOilPhase());
    assertSame(aqueousRole, system.getGeLiquidPhase());
    assertBalancedAndAtEquilibrium(system);

    SystemPitzer clone = system.clone();
    assertNotSame(gasRole, clone.getEquationOfStatePhase());
    assertNotSame(oilRole, clone.getEosOilPhase());
    assertNotSame(aqueousRole, clone.getGeLiquidPhase());
    new ThermodynamicOperations(clone).TPflash();
    assertBalancedAndAtEquilibrium(clone);

    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
      output.writeObject(system);
    }
    SystemPitzer restored;
    try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      restored = (SystemPitzer) input.readObject();
    }
    assertEquals(0, restored.getEosGasPhaseSlot());
    assertEquals(2, restored.getEosOilPhaseSlot());
    assertEquals(1, restored.getGeLiquidPhaseSlot());
    new ThermodynamicOperations(restored).TPflash();
    assertBalancedAndAtEquilibrium(restored);
  }

  /** PH, PS and TV flashes recover the extensive state of a hybrid electrolyte-GE reference. */
  @Test
  void phPsAndTvFlashesRecoverReferenceState() {
    SystemPitzer reference = createGasOilAqueousSystem();
    new ThermodynamicOperations(reference).TPflash();
    reference.initProperties();
    double targetTemperature = reference.getTemperature();
    double targetPressure = reference.getPressure();
    double targetEnthalpy = reference.getEnthalpy();
    double targetEntropy = reference.getEntropy();
    double targetVolume = reference.getVolume();

    assertTrue(Double.isFinite(targetEnthalpy));
    assertTrue(Double.isFinite(targetEntropy));
    assertTrue(Double.isFinite(targetVolume) && targetVolume > 0.0);

    SystemPitzer phSystem = reference.clone();
    phSystem.setTemperature(targetTemperature + 2.0);
    new ThermodynamicOperations(phSystem).PHflash(targetEnthalpy);
    phSystem.initProperties();
    assertEquals(targetEnthalpy, phSystem.getEnthalpy(), Math.max(1.0e-6, Math.abs(targetEnthalpy) * 1.0e-5));
    assertEquals(targetTemperature, phSystem.getTemperature(), 0.1);

    SystemPitzer psSystem = reference.clone();
    psSystem.setTemperature(targetTemperature + 2.0);
    new ThermodynamicOperations(psSystem).PSflash(targetEntropy);
    psSystem.initProperties();
    assertEquals(targetEntropy, psSystem.getEntropy(), Math.max(1.0e-6, Math.abs(targetEntropy) * 1.0e-5));
    assertEquals(targetTemperature, psSystem.getTemperature(), 0.1);

    SystemPitzer tvSystem = reference.clone();
    tvSystem.setPressure(targetPressure * 1.05);
    new ThermodynamicOperations(tvSystem).TVflash(targetVolume);
    tvSystem.initProperties();
    assertEquals(targetVolume, tvSystem.getVolume(), Math.abs(targetVolume) * 1.0e-5);
    assertEquals(targetPressure, tvSystem.getPressure(), 1.0e-3);
  }

  /**
   * Assert normalization, component material balance and neutral-component fugacity equality.
   *
   * @param system flashed system
   */
  private void assertBalancedAndAtEquilibrium(SystemInterface system) {
    double betaSum = 0.0;
    for (int phaseIndex = 0; phaseIndex < system.getNumberOfPhases(); phaseIndex++) {
      betaSum += system.getBeta(phaseIndex);
      double compositionSum = 0.0;
      for (int componentIndex = 0; componentIndex < system.getPhase(phaseIndex)
          .getNumberOfComponents(); componentIndex++) {
        ComponentInterface component = system.getPhase(phaseIndex).getComponent(componentIndex);
        assertTrue(Double.isFinite(component.getx()) && component.getx() > 0.0);
        compositionSum += component.getx();
      }
      assertEquals(1.0, compositionSum, 1.0e-8);
    }
    assertEquals(1.0, betaSum, 1.0e-8);

    for (int componentIndex = 0; componentIndex < system.getPhase(0).getNumberOfComponents(); componentIndex++) {
      ComponentInterface referenceComponent = system.getPhase(0).getComponent(componentIndex);
      double calculatedFeedFraction = 0.0;
      for (int phaseIndex = 0; phaseIndex < system.getNumberOfPhases(); phaseIndex++) {
        calculatedFeedFraction += system.getBeta(phaseIndex)
            * system.getPhase(phaseIndex).getComponent(componentIndex).getx();
      }
      assertEquals(referenceComponent.getz(), calculatedFeedFraction, MATERIAL_BALANCE_TOLERANCE,
          referenceComponent.getComponentName());

      if (referenceComponent.getz() <= 1.0e-30 || referenceComponent.getIonicCharge() != 0
          || referenceComponent.isIsIon()) {
        continue;
      }
      double referenceLogFugacity = Double.NaN;
      for (int phaseIndex = 0; phaseIndex < system.getNumberOfPhases(); phaseIndex++) {
        if (system.getBeta(phaseIndex) <= MATERIAL_PHASE_FRACTION) {
          continue;
        }
        ComponentInterface phaseComponent = system.getPhase(phaseIndex).getComponent(componentIndex);
        double logFugacity = Math.log(phaseComponent.getx() * phaseComponent.getFugacityCoefficient()
            * system.getPhase(phaseIndex).getPressure());
        assertTrue(Double.isFinite(logFugacity), referenceComponent.getComponentName());
        if (Double.isNaN(referenceLogFugacity)) {
          referenceLogFugacity = logFugacity;
        } else {
          assertEquals(referenceLogFugacity, logFugacity, LOG_FUGACITY_TOLERANCE,
              referenceComponent.getComponentName());
        }
      }
    }
  }

  /**
   * Check whether an active phase type is present.
   *
   * @param system system to inspect
   * @param phaseType phase type
   * @return true when present
   */
  private boolean hasPhaseType(SystemInterface system, PhaseType phaseType) {
    for (int phaseIndex = 0; phaseIndex < system.getNumberOfPhases(); phaseIndex++) {
      if (system.getPhase(phaseIndex).getType() == phaseType) {
        return true;
      }
    }
    return false;
  }

  /**
   * Find an active phase by type.
   *
   * @param system system to inspect
   * @param phaseType phase type
   * @return matching phase
   */
  private PhaseInterface findPhase(SystemInterface system, PhaseType phaseType) {
    for (int phaseIndex = 0; phaseIndex < system.getNumberOfPhases(); phaseIndex++) {
      if (system.getPhase(phaseIndex).getType() == phaseType) {
        return system.getPhase(phaseIndex);
      }
    }
    throw new AssertionError("Missing phase type " + phaseType);
  }

  /**
   * Format active phase fractions for assertion diagnostics.
   *
   * @param system system to inspect
   * @return phase diagnostics
   */
  private String phaseDiagnostics(SystemInterface system) {
    StringBuilder diagnostics = new StringBuilder();
    for (int phaseIndex = 0; phaseIndex < system.getNumberOfPhases(); phaseIndex++) {
      if (phaseIndex > 0) {
        diagnostics.append(", ");
      }
      diagnostics.append(system.getPhase(phaseIndex).getType()).append('=').append(system.getBeta(phaseIndex));
    }
    return diagnostics.toString();
  }
}
