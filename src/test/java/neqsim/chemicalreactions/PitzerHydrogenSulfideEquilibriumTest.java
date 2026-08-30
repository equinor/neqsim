package neqsim.chemicalreactions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.system.SystemPitzer;

/** Scientific regression gates for aqueous H2S dissociation with the Pitzer model. */
class PitzerHydrogenSulfideEquilibriumTest extends neqsim.NeqSimTest {
  private static final double WATER_MOLES = 55.508;
  private static final double H2S_MOLES = 0.01;

  /**
   * Validate reaction closure, material balance, electroneutrality, phase state, determinism, and the
   * nearby-temperature ionization trend for a dilute aqueous H2S state.
   */
  @Test
  void aqueousFirstDissociationClosesWithPhysicalBalancesAndTrend() {
    SystemPitzer system = createH2sSystem(298.15);
    assertTrue(system.getChemicalReactionOperations().solveChemEq(1, 0));
    assertTrue(system.getChemicalReactionOperations().solveChemEq(1, 1));
    system.init(1);

    assertPhysicalStateAndDiagnostics(system, 2.0e-6);
    double sulfideAt298 = system.getPhase(1).getComponent("HS-").getMolality(system.getPhase(1));
    double repeatedResidual = system.getChemicalReactionOperations().getMaximumAbsoluteReactionLogResidual();

    assertTrue(system.getChemicalReactionOperations().solveChemEq(1, 1));
    system.init(1);
    assertEquals(sulfideAt298, system.getPhase(1).getComponent("HS-").getMolality(system.getPhase(1)), 1.0e-12,
        "Repeated solves must be deterministic");
    assertEquals(repeatedResidual, system.getChemicalReactionOperations().getMaximumAbsoluteReactionLogResidual(),
        1.0e-10, "Repeated diagnostics must not retain stale state");

    system.setTemperature(318.15);
    system.init(0);
    system.init(1);
    assertTrue(system.getChemicalReactionOperations().solveChemEq(1, 1));
    system.init(1);
    assertPhysicalStateAndDiagnostics(system, 2.0e-6);
    assertTrue(system.getPhase(1).getComponent("HS-").getMolality(system.getPhase(1)) > sulfideAt298,
        "Lower pK1 at 45 degC must increase first dissociation at fixed composition");
  }

  /** Existing carbonate reactions use the same solver-selected solute-molality convention. */
  @Test
  void carbonateReactionSetClosesOnMolalityBasis() {
    SystemPitzer system = createReactiveSystem(298.15, "CO2", 0.01);
    assertTrue(system.getChemicalReactionOperations().solveChemEq(1, 0));
    assertTrue(system.getChemicalReactionOperations().solveChemEq(1, 1));
    system.init(1);

    assertPhysicalStateAndDiagnostics(system, 5.0e-6);
    Map<String, Double> residuals = system.getChemicalReactionOperations().getReactionLogResiduals();
    assertTrue(residuals.containsKey("CO2water"));
    assertTrue(residuals.containsKey("carbonate"));
    assertTrue(residuals.containsKey("waterreac"));
  }

  /** Independent systems retain deterministic Pitzer reaction state under concurrent execution. */
  @Test
  void independentH2sSystemsAreThreadSafe() throws Exception {
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      List<Callable<Double>> tasks = new ArrayList<>();
      for (int taskIndex = 0; taskIndex < 4; taskIndex++) {
        tasks.add(PitzerHydrogenSulfideEquilibriumTest::solveH2sMolality);
      }
      List<Future<Double>> results = executor.invokeAll(tasks);
      double expected = results.get(0).get();
      for (Future<Double> result : results) {
        assertEquals(expected, result.get(), 1.0e-12);
      }
    } finally {
      executor.shutdownNow();
    }
  }

  private static double solveH2sMolality() {
    SystemPitzer system = createH2sSystem(298.15);
    assertTrue(system.getChemicalReactionOperations().solveChemEq(1, 0));
    assertTrue(system.getChemicalReactionOperations().solveChemEq(1, 1));
    system.init(1);
    assertPhysicalStateAndDiagnostics(system, 2.0e-6);
    return system.getPhase(1).getComponent("HS-").getMolality(system.getPhase(1));
  }

  private static SystemPitzer createH2sSystem(double temperature) {
    return createReactiveSystem(temperature, "H2S", H2S_MOLES);
  }

  private static SystemPitzer createReactiveSystem(double temperature, String solute, double soluteMoles) {
    SystemPitzer system = new SystemPitzer(temperature, 1.01325);
    system.addComponent("water", WATER_MOLES);
    system.addComponent(solute, soluteMoles);
    system.setMultiPhaseCheck(false);
    system.chemicalReactionInit();
    system.createDatabase(true);
    system.setMixingRule("classic");
    system.init(0);
    system.init(1);
    return system;
  }

  private static void assertPhysicalStateAndDiagnostics(SystemPitzer system, double normalizedChargeTolerance) {
    PhaseInterface phase = system.getPhase(1);
    double moleFractionSum = 0.0;
    for (int componentIndex = 0; componentIndex < phase.getNumberOfComponents(); componentIndex++) {
      double moleFraction = phase.getComponent(componentIndex).getx();
      assertTrue(Double.isFinite(moleFraction));
      assertTrue(moleFraction >= 0.0);
      moleFractionSum += moleFraction;
    }
    assertEquals(1.0, moleFractionSum, 1.0e-12);

    Map<String, Double> reactionResiduals = system.getChemicalReactionOperations().getReactionLogResiduals();
    assertFalse(reactionResiduals.isEmpty());
    for (Map.Entry<String, Double> entry : reactionResiduals.entrySet()) {
      assertTrue(Double.isFinite(entry.getValue()), entry.getKey() + " residual was " + entry.getValue());
    }
    double maximumReactionResidual = system.getChemicalReactionOperations().getMaximumAbsoluteReactionLogResidual();
    assertTrue(maximumReactionResidual <= 2.0e-6,
        "Maximum absolute ln(Q/K) was " + maximumReactionResidual + ": " + reactionResiduals);

    Map<String, Double> elementResiduals = system.getChemicalReactionOperations().getElementBalanceResiduals();
    assertFalse(elementResiduals.isEmpty());
    for (double residual : elementResiduals.values()) {
      assertTrue(Double.isFinite(residual));
    }
    double maximumElementResidual = system.getChemicalReactionOperations().getMaximumAbsoluteElementBalanceResidual();
    assertTrue(maximumElementResidual <= 1.0e-8, "Maximum element residual was " + maximumElementResidual);
    double chargeMoles = system.getChemicalReactionOperations().getReactivePhaseChargeMoles();
    assertTrue(Math.abs(chargeMoles) <= 1.0e-8, "Reactive-phase charge was " + chargeMoles + " mol");
    double normalizedCharge = system.getChemicalReactionOperations().getNormalizedReactivePhaseChargeResidual();
    assertTrue(normalizedCharge <= normalizedChargeTolerance, "Normalized charge residual was " + normalizedCharge);
  }
}
