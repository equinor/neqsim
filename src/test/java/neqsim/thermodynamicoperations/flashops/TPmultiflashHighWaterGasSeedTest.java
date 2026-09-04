package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.separator.ThreePhaseSeparator;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/** Qualification coverage for gas-phase persistence in water-dominated CPA flashes. */
class TPmultiflashHighWaterGasSeedTest {
  private static final double REFERENCE_TEMPERATURE_K = 313.15;
  private static final double REFERENCE_PRESSURE_BARA = 20.0;
  private static final double NORMALIZATION_TOLERANCE = 1.0e-12;
  private static final double MATERIAL_BALANCE_TOLERANCE = 1.0e-10;
  private static final double FUGACITY_TOLERANCE = 1.0e-8;
  private static final double STATE_TOLERANCE = 1.0e-8;
  private static final double[] WATER_MASS_FRACTIONS = { 0.40, 0.45, 0.50, 0.55, 0.60, 0.70, 0.80 };
  private static final double[] EXPECTED_GAS_BETAS = { 0.0441902453511, 0.0372773421590, 0.0313666680101,
      0.0262550011428, 0.0217905845743, 0.0143670367332, 0.00844359351489 };

  @Test
  void gasPhasePersistsAcrossHighWaterFractionSweep() {
    double previousGasMoles = Double.POSITIVE_INFINITY;
    for (int point = 0; point < WATER_MASS_FRACTIONS.length; point++) {
      double waterMassFraction = WATER_MASS_FRACTIONS[point];
      SystemInterface system = createFluid(waterMassFraction);
      flash(system);

      assertEquals(3, system.getNumberOfPhases(), "Expected gas-oil-aqueous equilibrium at " + waterMassFraction);
      assertTrue(system.hasPhaseType(PhaseType.GAS),
          "Expected gas phase at water mass fraction " + waterMassFraction + ", phases=" + system.getNumberOfPhases());
      assertTrue(system.hasPhaseType(PhaseType.OIL), "Expected oil phase at water mass fraction " + waterMassFraction);
      assertTrue(system.hasPhaseType(PhaseType.AQUEOUS),
          "Expected aqueous phase at water mass fraction " + waterMassFraction);

      int gasPhase = system.getPhaseNumberOfPhase("gas");
      assertEquals(EXPECTED_GAS_BETAS[point], system.getBeta(gasPhase), 1.0e-8);
      double gasMoles = system.getPhase(gasPhase).getNumberOfMolesInPhase();
      assertTrue(gasMoles > 20.0 && gasMoles < 25.0, "Gas amount must remain physical at " + waterMassFraction);
      assertTrue(gasMoles <= previousGasMoles + 1.0e-8,
          "Gas amount must vary continuously as immiscible water is added at " + waterMassFraction);
      previousGasMoles = gasMoles;
      assertFlashClosure(system);

      flash(system);
      assertEquals(3, system.getNumberOfPhases(), "Repeated flash phase count at " + waterMassFraction);
      assertEquals(EXPECTED_GAS_BETAS[point], system.getBeta(system.getPhaseNumberOfPhase("gas")), 1.0e-8,
          "Repeated flash gas fraction at " + waterMassFraction);
      assertFlashClosure(system);
    }
  }

  @Test
  void vaporLikeSeedRecoversFromPoorBetaGuessesAtNearbyConditions() {
    double[][] conditions = { { 312.65, 19.5 }, { 313.15, 20.0 }, { 313.65, 20.5 } };
    for (double[] condition : conditions) {
      SystemInterface system = createFluid(0.55);
      system.setTemperature(condition[0]);
      system.setPressure(condition[1]);
      system.init(0);
      system.setBeta(0, 1.0e-10);
      system.setBeta(1, 1.0 - 1.0e-10);

      flash(system);

      assertEquals(3, system.getNumberOfPhases(),
          "Expected gas-oil-aqueous equilibrium at T=" + condition[0] + " K, P=" + condition[1] + " bara");
      assertFlashClosure(system);
    }
  }

  @Test
  void changedReturnedAndRepeatedPressureStatesMatchFreshEquilibrium() {
    SystemInterface reference = createFluid(0.55);
    flash(reference);
    SystemInterface reused = reference.clone();

    reused.setPressure(20.5, "bara");
    flash(reused);
    SystemInterface freshChanged = createFluid(0.55);
    freshChanged.setPressure(20.5, "bara");
    flash(freshChanged);
    assertEquivalentEquilibrium(freshChanged, reused, STATE_TOLERANCE, "changed pressure");

    reused.setPressure(REFERENCE_PRESSURE_BARA, "bara");
    flash(reused);
    assertEquivalentEquilibrium(reference, reused, STATE_TOLERANCE, "returned pressure");

    SystemInterface repeatedReference = reused.clone();
    flash(reused);
    assertEquivalentEquilibrium(repeatedReference, reused, 1.0e-10, "deterministic repeat");
  }

  @Test
  void threePhaseSeparatorKeepsContinuousGasProductAcrossFormerPhaseIsland() {
    double previousGasMoles = Double.POSITIVE_INFINITY;
    for (double waterMassFraction : new double[] { 0.50, 0.55, 0.60, 0.70 }) {
      Stream feed = new Stream("high-water feed", createFluid(waterMassFraction));
      feed.run();
      ThreePhaseSeparator separator = new ThreePhaseSeparator("three-phase separator", feed);

      separator.run();

      double gasMoles = separator.getGasOutStream().getThermoSystem().getTotalNumberOfMoles();
      assertTrue(gasMoles > 20.0 && gasMoles < 25.0,
          "Separator gas product must remain physical at " + waterMassFraction);
      assertTrue(gasMoles <= previousGasMoles + 1.0e-8,
          "Separator gas product must vary continuously as immiscible water is added at " + waterMassFraction);
      previousGasMoles = gasMoles;
    }
  }

  private void assertFlashClosure(SystemInterface system) {
    double betaSum = 0.0;
    for (int phase = 0; phase < system.getNumberOfPhases(); phase++) {
      double beta = system.getBeta(phase);
      assertTrue(Double.isFinite(beta) && beta > 0.0 && beta <= 1.0, "Phase fraction must be finite and bounded");
      betaSum += beta;

      double compositionSum = 0.0;
      for (int component = 0; component < system.getPhase(phase).getNumberOfComponents(); component++) {
        double composition = system.getPhase(phase).getComponent(component).getx();
        assertTrue(Double.isFinite(composition) && composition >= 0.0 && composition <= 1.0,
            "Phase composition must be finite and bounded");
        compositionSum += composition;
      }
      assertEquals(1.0, compositionSum, NORMALIZATION_TOLERANCE, "Phase composition must be normalized");
      assertTrue(Double.isFinite(system.getPhase(phase).getZ()) && system.getPhase(phase).getZ() > 0.0,
          "Compressibility factor must be finite and positive");
      assertTrue(Double.isFinite(system.getPhase(phase).getDensity()) && system.getPhase(phase).getDensity() > 0.0,
          "Density must be finite and positive");
    }
    assertEquals(1.0, betaSum, NORMALIZATION_TOLERANCE, "Phase fractions must be normalized");

    double maximumMaterialResidual = 0.0;
    double maximumFugacityResidual = 0.0;
    int fugacityComparisons = 0;
    for (int component = 0; component < system.getPhase(0).getNumberOfComponents(); component++) {
      double recoveredComposition = 0.0;
      for (int phase = 0; phase < system.getNumberOfPhases(); phase++) {
        double composition = system.getPhase(phase).getComponent(component).getx();
        recoveredComposition += system.getBeta(phase) * composition;
      }
      maximumMaterialResidual = Math.max(maximumMaterialResidual,
          Math.abs(system.getPhase(0).getComponent(component).getz() - recoveredComposition));

      for (int firstPhase = 0; firstPhase < system.getNumberOfPhases(); firstPhase++) {
        for (int secondPhase = firstPhase + 1; secondPhase < system.getNumberOfPhases(); secondPhase++) {
          double firstComposition = system.getPhase(firstPhase).getComponent(component).getx();
          double secondComposition = system.getPhase(secondPhase).getComponent(component).getx();
          double firstCoefficient = system.getPhase(firstPhase).getComponent(component).getFugacityCoefficient();
          double secondCoefficient = system.getPhase(secondPhase).getComponent(component).getFugacityCoefficient();
          if (firstComposition > 1.0e-20 && secondComposition > 1.0e-20 && Double.isFinite(firstCoefficient)
              && firstCoefficient > 0.0 && Double.isFinite(secondCoefficient) && secondCoefficient > 0.0) {
            double firstLogFugacity = Math.log(firstComposition * firstCoefficient);
            double secondLogFugacity = Math.log(secondComposition * secondCoefficient);
            maximumFugacityResidual = Math.max(maximumFugacityResidual, Math.abs(firstLogFugacity - secondLogFugacity));
            fugacityComparisons++;
          }
        }
      }
    }
    assertTrue(maximumMaterialResidual < MATERIAL_BALANCE_TOLERANCE,
        "Component material balance must close, residual=" + maximumMaterialResidual);
    assertTrue(fugacityComparisons > 0, "Expected comparable cross-phase fugacities");
    assertTrue(maximumFugacityResidual < FUGACITY_TOLERANCE,
        "Component fugacity must be equal across phases, residual=" + maximumFugacityResidual);
    assertTrue(Double.isFinite(system.getGibbsEnergy()), "Gibbs energy must be finite");
    assertTrue(Double.isFinite(system.getEnthalpy()), "Enthalpy must be finite");
  }

  private void assertEquivalentEquilibrium(SystemInterface expected, SystemInterface actual, double tolerance,
      String label) {
    assertEquals(expected.getNumberOfPhases(), actual.getNumberOfPhases(), label + " phase count");
    assertFlashClosure(expected);
    assertFlashClosure(actual);
    for (int expectedPhase = 0; expectedPhase < expected.getNumberOfPhases(); expectedPhase++) {
      PhaseType phaseType = expected.getPhase(expectedPhase).getType();
      int actualPhase = findPhase(actual, phaseType);
      assertEquals(expected.getBeta(expectedPhase), actual.getBeta(actualPhase), tolerance,
          label + " beta " + phaseType);
      assertEquals(expected.getPhase(expectedPhase).getZ(), actual.getPhase(actualPhase).getZ(), tolerance,
          label + " compressibility " + phaseType);
      assertEquals(expected.getPhase(expectedPhase).getDensity(), actual.getPhase(actualPhase).getDensity(),
          Math.max(1.0e-8, tolerance * Math.abs(expected.getPhase(expectedPhase).getDensity())),
          label + " density " + phaseType);
      for (int component = 0; component < expected.getPhase(expectedPhase).getNumberOfComponents(); component++) {
        assertEquals(expected.getPhase(expectedPhase).getComponent(component).getx(),
            actual.getPhase(actualPhase).getComponent(component).getx(), tolerance,
            label + " composition " + phaseType + "/" + component);
      }
    }
    assertExtensiveEquals(expected.getGibbsEnergy(), actual.getGibbsEnergy(), tolerance, label + " Gibbs energy");
    assertExtensiveEquals(expected.getEnthalpy(), actual.getEnthalpy(), tolerance, label + " enthalpy");
  }

  private void assertExtensiveEquals(double expected, double actual, double relativeTolerance, String label) {
    assertEquals(expected, actual, Math.max(1.0e-8, relativeTolerance * Math.abs(expected)), label);
  }

  private int findPhase(SystemInterface system, PhaseType phaseType) {
    for (int phase = 0; phase < system.getNumberOfPhases(); phase++) {
      if (system.getPhase(phase).getType() == phaseType) {
        return phase;
      }
    }
    throw new AssertionError("Missing phase " + phaseType);
  }

  private void flash(SystemInterface system) {
    new ThermodynamicOperations(system).TPflash();
    system.initProperties();
  }

  private SystemInterface createFluid(double waterMassFraction) {
    SystemInterface system = new SystemSrkCPAstatoil(REFERENCE_TEMPERATURE_K, REFERENCE_PRESSURE_BARA);
    system.addComponent("methane", 25.0);
    system.addComponent("ethane", 5.0);
    system.addComponent("propane", 5.0);
    system.addComponent("n-butane", 4.0);
    system.addComponent("n-pentane", 4.0);
    system.addComponent("n-hexane", 6.0);
    system.addComponent("n-heptane", 8.0);
    system.addComponent("n-octane", 8.0);
    system.addTBPfraction("C10", 15.0, 0.142, 0.78);
    system.addTBPfraction("C20", 20.0, 0.282, 0.88);
    double waterMoles = 11.29556 / 0.01801528 * waterMassFraction / (1.0 - waterMassFraction);
    system.addComponent("water", waterMoles);
    system.setMixingRule(10);
    system.setMultiPhaseCheck(true);
    return system;
  }
}
