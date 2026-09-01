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

/** Regression coverage for gas-phase persistence in water-dominated CPA flashes. */
class TPmultiflashHighWaterGasSeedTest {
  private static final double[] WATER_MASS_FRACTIONS = { 0.40, 0.45, 0.50, 0.55, 0.60, 0.70, 0.80 };
  private static final double[] EXPECTED_GAS_BETAS = { 0.0441902453511, 0.0372773421590, 0.0313666680101,
      0.0262550011428, 0.0217905845743, 0.0143670367332, 0.00844359351489 };

  @Test
  void gasPhasePersistsAcrossHighWaterFractionSweep() {
    double previousGasMoles = Double.POSITIVE_INFINITY;
    for (int point = 0; point < WATER_MASS_FRACTIONS.length; point++) {
      double waterMassFraction = WATER_MASS_FRACTIONS[point];
      SystemInterface system = createFluid(waterMassFraction);
      new ThermodynamicOperations(system).TPflash();

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

      new ThermodynamicOperations(system).TPflash();
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

      new ThermodynamicOperations(system).TPflash();

      assertEquals(3, system.getNumberOfPhases(),
          "Expected gas-oil-aqueous equilibrium at T=" + condition[0] + " K, P=" + condition[1] + " bara");
      assertFlashClosure(system);
    }
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
      assertEquals(1.0, compositionSum, 1.0e-8, "Phase composition must be normalized");
    }
    assertEquals(1.0, betaSum, 1.0e-8, "Phase fractions must be normalized");

    for (int component = 0; component < system.getPhase(0).getNumberOfComponents(); component++) {
      double recoveredComposition = 0.0;
      double referenceLogFugacity = Double.NaN;
      for (int phase = 0; phase < system.getNumberOfPhases(); phase++) {
        double composition = system.getPhase(phase).getComponent(component).getx();
        recoveredComposition += system.getBeta(phase) * composition;
        double logFugacity = Math.log(Math.max(composition, Double.MIN_NORMAL))
            + Math.log(system.getPhase(phase).getComponent(component).getFugacityCoefficient());
        assertTrue(Double.isFinite(logFugacity), "Component log fugacity must be finite");
        if (phase == 0) {
          referenceLogFugacity = logFugacity;
        } else {
          assertEquals(referenceLogFugacity, logFugacity, 2.0e-8, "Component fugacity must be equal across phases");
        }
      }
      assertEquals(system.getPhase(0).getComponent(component).getz(), recoveredComposition, 2.0e-8,
          "Component material balance must close");
    }
  }

  private SystemInterface createFluid(double waterMassFraction) {
    SystemInterface system = new SystemSrkCPAstatoil(313.15, 20.0);
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
