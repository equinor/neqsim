package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import neqsim.thermo.system.SystemElectrolyteCPAstatoil;
import neqsim.thermo.system.SystemInterface;

/** Regression coverage for opt-in homogeneous aqueous-reference evidence used by hydrate calculations. */
class TPflashHydrateReferenceStabilityTest extends neqsim.NeqSimTest {
  /**
   * Hydrate diagnostics must distinguish a stable aqueous feed from an unstable homogeneous reference without changing
   * the accepted fluid equilibrium.
   *
   * @param co2Moles feed carbon dioxide amount in moles
   * @param expectedPhases expected number of material fluid phases
   */
  @ParameterizedTest
  @CsvSource({"0.5,1", "10.0,2"})
  void hydrateReferenceEvidencePreservesTheFluidEndpoint(double co2Moles, int expectedPhases) {
    SystemInterface ordinary = new SystemElectrolyteCPAstatoil(278.15, 100.0);
    ordinary.addComponent("CO2", co2Moles);
    ordinary.addComponent("water", 1.0 / 0.01801528);
    double saltMoles = 5.0 / 95.0 / 0.05844277;
    ordinary.addComponent("Na+", saltMoles);
    ordinary.addComponent("Cl-", saltMoles);
    ordinary.setMixingRule(10);
    ordinary.setMultiPhaseCheck(true);
    SystemInterface hydrate = ordinary.clone();
    hydrate.setHydrateCheck(true);

    TPflash ordinaryFlash = new TPflash(ordinary);
    ordinaryFlash.run();
    TPflash hydrateFlash = new TPflash(hydrate);
    hydrateFlash.run();

    assertEquals(expectedPhases, ordinary.getNumberOfPhases());
    assertEquals(expectedPhases, hydrate.getNumberOfPhases());
    double minimumDistance = Double.POSITIVE_INFINITY;
    for (double distance : hydrateFlash.getLastTangentPlaneDistances()) {
      assertTrue(Double.isFinite(distance));
      minimumDistance = Math.min(minimumDistance, distance);
    }
    assertTrue(Double.isFinite(minimumDistance), "hydrate consumers need homogeneous-reference evidence");
    assertEquals(expectedPhases == 2, minimumDistance < -1.0e-8);
    if (expectedPhases == 2) {
      assertEquals(0, ordinaryFlash.getLastTangentPlaneDistances().length,
          "ordinary multiphase flashes must not run unused homogeneous-reference diagnostics");
    }
    for (int phase = 0; phase < expectedPhases; phase++) {
      assertEquals(ordinary.getPhase(phase).getType(), hydrate.getPhase(phase).getType());
      assertEquals(ordinary.getBeta(phase), hydrate.getBeta(phase), 1.0e-12);
      for (int component = 0; component < ordinary.getNumberOfComponents(); component++) {
        assertEquals(ordinary.getPhase(phase).getComponent(component).getx(),
            hydrate.getPhase(phase).getComponent(component).getx(), 1.0e-12);
        assertEquals(ordinary.getPhase(phase).getComponent(component).getLogFugacityCoefficient(),
            hydrate.getPhase(phase).getComponent(component).getLogFugacityCoefficient(), 1.0e-9);
      }
    }
  }
}
