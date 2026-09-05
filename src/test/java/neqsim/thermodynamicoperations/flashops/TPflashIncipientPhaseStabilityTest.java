package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermo.system.SystemUMRPRUMCEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Qualification tests for incipient phase appearance and disappearance.
 *
 * <p>
 * The synthetic SRK case requires the supplementary stability trial to retain a tiny vapour phase.
 * The synthetic UMR-PRU case requires a sub-residual TPD trial not to replace a stable single-phase
 * solution. These are deterministic numerical regressions, not experimental PVT validation.
 * </p>
 */
class TPflashIncipientPhaseStabilityTest {
  private static final String[] SRK_COMPONENTS = {
    "methane", "ethane", "propane", "n-butane"
  };
  private static final double[] SRK_FEED = {
    0.5833884211682981, 0.16475359157041228, 0.19866217294783825,
    0.053195814313451245
  };
  private static final double SRK_REFERENCE_TEMPERATURE_K = 253.46685189059752;
  private static final double SRK_REFERENCE_PRESSURE_BARA = 77.53775411226596;

  private static final String[] UMR_PRU_COMPONENTS = {
    "methane", "ethane", "n-pentane", "nC16"
  };
  private static final double[] UMR_PRU_FEED = {
    0.416683, 0.17522, 0.358009, 0.0500888
  };
  private static final double UMR_PRU_REFERENCE_TEMPERATURE_K = 293.15;
  private static final double UMR_PRU_REFERENCE_PRESSURE_BARA = 90.03461693;

  private static final double NORMALIZATION_TOLERANCE = 2.0e-12;
  private static final double MATERIAL_BALANCE_TOLERANCE = 1.0e-10;
  private static final double FUGACITY_TOLERANCE = 1.0e-8;

  /**
   * The supplementary stability diagnostic must retain the closed incipient-vapour solution.
   */
  @Test
  void supplementaryStabilityTrialRetainsIncipientVapor() {
    SystemInterface ordinary =
        createSrk(SRK_REFERENCE_TEMPERATURE_K, SRK_REFERENCE_PRESSURE_BARA, false);
    TPflash ordinaryFlash = new TPflash(ordinary);
    ordinaryFlash.run();
    ordinary.init(3);

    SystemInterface multiphase =
        flash(createSrk(SRK_REFERENCE_TEMPERATURE_K, SRK_REFERENCE_PRESSURE_BARA, true));

    assertEquals("unstable - supplementary stability trial",
        ordinaryFlash.getLastStabilityOutcome());
    assertEquals(2, ordinary.getNumberOfPhases());
    assertTrue(ordinary.hasPhaseType(PhaseType.GAS));
    assertTrue(ordinary.hasPhaseType(PhaseType.OIL));
    assertEquals(3.50882832337307e-5,
        ordinary.getBeta(findPhase(ordinary, PhaseType.GAS)), 1.0e-10);
    assertClosedState(ordinary, "ordinary incipient vapour");
    assertClosedState(multiphase, "multiphase incipient vapour");
    assertEquivalentState(ordinary, multiphase, 1.0e-10,
        "incipient-vapour algorithm agreement");

    SystemInterface previous = ordinary.clone();
    ordinaryFlash.run();
    ordinary.init(3);
    assertEquivalentState(previous, ordinary, 1.0e-10,
        "incipient-vapour deterministic repeat");
  }

  /**
   * The UMR-PRU sub-residual TPD guard must retain the stable single phase at nearby pressures.
   */
  @Test
  void subResidualTpdDoesNotOverrideExistingUmrPruSolution() {
    for (double pressureBara : new double[] {
        89.5, UMR_PRU_REFERENCE_PRESSURE_BARA, 90.5
    }) {
      SystemInterface ordinary =
          flash(createUmrPru(UMR_PRU_REFERENCE_TEMPERATURE_K, pressureBara, false));
      SystemInterface multiphase =
          flash(createUmrPru(UMR_PRU_REFERENCE_TEMPERATURE_K, pressureBara, true));

      assertEquals(1, ordinary.getNumberOfPhases(),
          "ordinary topology at " + pressureBara + " bara");
      assertEquals(1, multiphase.getNumberOfPhases(),
          "multiphase topology at " + pressureBara + " bara");
      assertClosedState(ordinary, "ordinary UMR-PRU at " + pressureBara + " bara");
      assertClosedState(multiphase, "multiphase UMR-PRU at " + pressureBara + " bara");
      assertEquivalentState(ordinary, multiphase, 1.0e-11,
          "UMR-PRU algorithm agreement at " + pressureBara + " bara");
    }
  }

  /**
   * Poor beta estimates must recover the same incipient and single-phase endpoints.
   */
  @Test
  void poorInitializationRecoversReferenceEndpoints() {
    SystemInterface srkReference =
        flash(createSrk(SRK_REFERENCE_TEMPERATURE_K, SRK_REFERENCE_PRESSURE_BARA, true));
    SystemInterface srkPoor =
        createSrk(SRK_REFERENCE_TEMPERATURE_K, SRK_REFERENCE_PRESSURE_BARA, true);
    setPoorBetaEstimate(srkPoor);
    flash(srkPoor);
    assertEquivalentState(srkReference, srkPoor, 1.0e-8,
        "SRK poor beta initialization");

    SystemInterface umrPruReference =
        flash(createUmrPru(UMR_PRU_REFERENCE_TEMPERATURE_K,
            UMR_PRU_REFERENCE_PRESSURE_BARA, true));
    SystemInterface umrPruPoor =
        createUmrPru(UMR_PRU_REFERENCE_TEMPERATURE_K,
            UMR_PRU_REFERENCE_PRESSURE_BARA, true);
    setPoorBetaEstimate(umrPruPoor);
    flash(umrPruPoor);
    assertEquivalentState(umrPruReference, umrPruPoor, 1.0e-8,
        "UMR-PRU poor beta initialization");
  }

  /**
   * Reused changed and returned states must match fresh calculations for both boundary roles.
   */
  @Test
  void changedReturnedAndRepeatedStatesRemainContinuous() {
    SystemInterface srkReference =
        flash(createSrk(SRK_REFERENCE_TEMPERATURE_K, SRK_REFERENCE_PRESSURE_BARA, true));
    SystemInterface reusedSrk = srkReference.clone();

    reusedSrk.setPressure(77.7, "bara");
    flash(reusedSrk);
    SystemInterface freshSrk =
        flash(createSrk(SRK_REFERENCE_TEMPERATURE_K, 77.7, true));
    assertEquivalentState(freshSrk, reusedSrk, 1.0e-8, "changed SRK state");

    reusedSrk.setPressure(SRK_REFERENCE_PRESSURE_BARA, "bara");
    flash(reusedSrk);
    assertEquivalentState(srkReference, reusedSrk, 1.0e-8, "returned SRK state");

    SystemInterface previousSrk = reusedSrk.clone();
    flash(reusedSrk);
    assertEquivalentState(previousSrk, reusedSrk, 1.0e-10,
        "repeated SRK state");

    SystemInterface umrPruReference =
        flash(createUmrPru(UMR_PRU_REFERENCE_TEMPERATURE_K,
            UMR_PRU_REFERENCE_PRESSURE_BARA, true));
    SystemInterface reusedUmrPru = umrPruReference.clone();

    reusedUmrPru.setPressure(90.5, "bara");
    flash(reusedUmrPru);
    SystemInterface freshUmrPru =
        flash(createUmrPru(UMR_PRU_REFERENCE_TEMPERATURE_K, 90.5, true));
    assertEquivalentState(freshUmrPru, reusedUmrPru, 1.0e-8,
        "changed UMR-PRU state");

    reusedUmrPru.setPressure(UMR_PRU_REFERENCE_PRESSURE_BARA, "bara");
    flash(reusedUmrPru);
    assertEquivalentState(umrPruReference, reusedUmrPru, 1.0e-8,
        "returned UMR-PRU state");

    SystemInterface previousUmrPru = reusedUmrPru.clone();
    flash(reusedUmrPru);
    assertEquivalentState(previousUmrPru, reusedUmrPru, 1.0e-10,
        "repeated UMR-PRU state");
  }

  private SystemInterface createSrk(double temperatureK, double pressureBara,
      boolean multiphaseCheck) {
    SystemInterface system = new SystemSrkEos(temperatureK, pressureBara);
    for (int component = 0; component < SRK_COMPONENTS.length; component++) {
      system.addComponent(SRK_COMPONENTS[component], SRK_FEED[component]);
    }
    system.setMixingRule("classic");
    system.setMultiPhaseCheck(multiphaseCheck);
    return system;
  }

  private SystemInterface createUmrPru(double temperatureK, double pressureBara,
      boolean multiphaseCheck) {
    SystemInterface system = new SystemUMRPRUMCEos(temperatureK, pressureBara);
    for (int component = 0; component < UMR_PRU_COMPONENTS.length; component++) {
      system.addComponent(UMR_PRU_COMPONENTS[component], UMR_PRU_FEED[component]);
    }
    system.setMixingRule("classic");
    system.setTotalFlowRate(4.925e-7, "kg/sec");
    system.setMultiPhaseCheck(multiphaseCheck);
    return system;
  }

  private SystemInterface flash(SystemInterface system) {
    new ThermodynamicOperations(system).TPflash();
    system.init(3);
    return system;
  }

  private void setPoorBetaEstimate(SystemInterface system) {
    system.init(0);
    assertTrue(system.getNumberOfPhases() >= 2, "poor initialization requires two phase slots");
    system.setBeta(0, 1.0e-12);
    system.setBeta(1, 1.0 - 1.0e-12);
  }

  private void assertClosedState(SystemInterface system, String label) {
    int componentCount = system.getPhase(0).getNumberOfComponents();
    double betaTotal = 0.0;
    for (int phase = 0; phase < system.getNumberOfPhases(); phase++) {
      double beta = system.getBeta(phase);
      assertTrue(Double.isFinite(beta) && beta > 0.0 && beta <= 1.0,
          label + " beta " + phase);
      betaTotal += beta;

      double compositionTotal = 0.0;
      for (int component = 0; component < componentCount; component++) {
        double composition = system.getPhase(phase).getComponent(component).getx();
        assertTrue(Double.isFinite(composition) && composition >= 0.0
            && composition <= 1.0, label + " composition " + phase + "/" + component);
        compositionTotal += composition;
      }
      assertEquals(1.0, compositionTotal, NORMALIZATION_TOLERANCE,
          label + " composition normalization " + phase);
      assertTrue(Double.isFinite(system.getPhase(phase).getZ())
          && system.getPhase(phase).getZ() > 0.0,
          label + " compressibility " + phase);
    }
    assertEquals(1.0, betaTotal, NORMALIZATION_TOLERANCE,
        label + " beta normalization");

    double materialResidual = maximumComponentMaterialBalanceResidual(system);
    assertTrue(materialResidual < MATERIAL_BALANCE_TOLERANCE,
        label + " material-balance residual " + materialResidual);

    if (system.getNumberOfPhases() == 1) {
      assertEquals(1.0, system.getBeta(0), NORMALIZATION_TOLERANCE,
          label + " single-phase beta");
      for (int component = 0; component < componentCount; component++) {
        assertEquals(system.getPhase(0).getComponent(component).getz(),
            system.getPhase(0).getComponent(component).getx(),
            MATERIAL_BALANCE_TOLERANCE, label + " single-phase x=z " + component);
      }
    } else {
      double fugacityResidual = maximumComparableLogFugacityResidual(system);
      assertTrue(fugacityResidual < FUGACITY_TOLERANCE,
          label + " fugacity residual " + fugacityResidual);
    }

    assertTrue(Double.isFinite(system.getGibbsEnergy()), label + " Gibbs energy");
    assertTrue(Double.isFinite(system.getEnthalpy()), label + " enthalpy");
  }

  private void assertEquivalentState(SystemInterface expected, SystemInterface actual,
      double tolerance, String label) {
    assertEquals(expected.getNumberOfPhases(), actual.getNumberOfPhases(),
        label + " phase count");
    assertClosedState(expected, label + " expected");
    assertClosedState(actual, label + " actual");

    for (int expectedPhase = 0;
        expectedPhase < expected.getNumberOfPhases(); expectedPhase++) {
      PhaseType type = expected.getPhase(expectedPhase).getType();
      int actualPhase = findPhase(actual, type);
      assertEquals(expected.getBeta(expectedPhase), actual.getBeta(actualPhase),
          tolerance, label + " beta " + type);
      assertEquals(expected.getPhase(expectedPhase).getZ(),
          actual.getPhase(actualPhase).getZ(), tolerance,
          label + " compressibility " + type);
      for (int component = 0;
          component < expected.getPhase(expectedPhase).getNumberOfComponents(); component++) {
        assertEquals(expected.getPhase(expectedPhase).getComponent(component).getx(),
            actual.getPhase(actualPhase).getComponent(component).getx(), tolerance,
            label + " composition " + type + "/" + component);
      }
    }
    assertExtensiveEquals(expected.getGibbsEnergy(), actual.getGibbsEnergy(), tolerance,
        label + " Gibbs energy");
    assertExtensiveEquals(expected.getEnthalpy(), actual.getEnthalpy(), tolerance,
        label + " enthalpy");
  }

  private void assertExtensiveEquals(double expected, double actual,
      double relativeTolerance, String label) {
    assertEquals(expected, actual,
        Math.max(1.0e-8, relativeTolerance * Math.abs(expected)), label);
  }

  private int findPhase(SystemInterface system, PhaseType type) {
    for (int phase = 0; phase < system.getNumberOfPhases(); phase++) {
      if (system.getPhase(phase).getType() == type) {
        return phase;
      }
    }
    throw new AssertionError("missing phase " + type);
  }

  private double maximumComponentMaterialBalanceResidual(SystemInterface system) {
    double maximumResidual = 0.0;
    for (int component = 0;
        component < system.getPhase(0).getNumberOfComponents(); component++) {
      double recovered = 0.0;
      for (int phase = 0; phase < system.getNumberOfPhases(); phase++) {
        recovered +=
            system.getBeta(phase) * system.getPhase(phase).getComponent(component).getx();
      }
      maximumResidual = Math.max(maximumResidual,
          Math.abs(system.getPhase(0).getComponent(component).getz() - recovered));
    }
    return maximumResidual;
  }

  private double maximumComparableLogFugacityResidual(SystemInterface system) {
    double maximumResidual = 0.0;
    int comparisons = 0;
    for (int component = 0;
        component < system.getPhase(0).getNumberOfComponents(); component++) {
      for (int firstPhase = 0;
          firstPhase < system.getNumberOfPhases(); firstPhase++) {
        for (int secondPhase = firstPhase + 1;
            secondPhase < system.getNumberOfPhases(); secondPhase++) {
          double firstComposition =
              system.getPhase(firstPhase).getComponent(component).getx();
          double secondComposition =
              system.getPhase(secondPhase).getComponent(component).getx();
          double firstCoefficient =
              system.getPhase(firstPhase).getComponent(component).getFugacityCoefficient();
          double secondCoefficient =
              system.getPhase(secondPhase).getComponent(component).getFugacityCoefficient();
          if (firstComposition > 1.0e-20 && secondComposition > 1.0e-20
              && Double.isFinite(firstCoefficient) && firstCoefficient > 0.0
              && Double.isFinite(secondCoefficient) && secondCoefficient > 0.0) {
            maximumResidual = Math.max(maximumResidual,
                Math.abs(Math.log(firstComposition * firstCoefficient)
                    - Math.log(secondComposition * secondCoefficient)));
            comparisons++;
          }
        }
      }
    }
    assertTrue(comparisons > 0,
        "expected at least one comparable cross-phase fugacity");
    return maximumResidual;
  }
}
