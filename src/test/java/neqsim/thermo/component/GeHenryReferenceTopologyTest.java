package neqsim.thermo.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import neqsim.thermo.phase.PhaseGENRTL;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemNRTL;

/**
 * Regression tests for phase-local Henry reference normalization.
 *
 * @author NeqSim
 * @version 1.0
 */
class GeHenryReferenceTopologyTest extends neqsim.NeqSimTest {
  /** Checks that user-supplied interactions also define the infinite-dilution reference. */
  @Test
  void waterReferenceRetainsTernaryInteractionParametersAndComponentOrder() {
    PhaseGENRTL phase = aqueousPhase();
    double expected = Math.exp(200.0 / phase.getTemperature());
    assertEquals(expected, phase.getActivityCoefficientInfDilWater(1, 2), 1.0e-9);
    assertEquals(0.2, phase.getComponent(0).getx(), 1.0e-12);
    assertEquals(0.1, phase.getComponent(1).getx(), 1.0e-12);
    assertEquals(0.7, phase.getComponent(2).getx(), 1.0e-12);
  }

  /** Checks the derivative of the complete normalized Henry fugacity. */
  @Test
  void normalizedHenryTemperatureDerivativeMatchesFiniteDifference() {
    PhaseGENRTL phase = aqueousPhase();
    phase.setAlpha(new double[][] {{0.0, 0.2, 0.3}, {0.2, 0.0, 0.3}, {0.3, 0.3, 0.0}});
    phase.setDij(new double[][] {{0.0, 100.0, -50.0}, {-80.0, 0.0, 200.0}, {120.0, -70.0, 0.0}});
    evaluate(phase);
    ComponentGE solute = (ComponentGE) phase.getComponent(1);
    double temperature = phase.getTemperature();
    double step = 1.0e-3;
    phase.setTemperature(temperature + step);
    evaluate(phase);
    double plus = Math.log(solute.fugcoef(phase));
    phase.setTemperature(temperature - step);
    evaluate(phase);
    double minus = Math.log(solute.fugcoef(phase));
    phase.setTemperature(temperature);
    evaluate(phase);
    assertEquals((plus - minus) / (2.0 * step), solute.fugcoefDiffTemp(phase), 1.0e-9);
  }

  /** A water-free pure liquid uses its pure-liquid vapor pressure, regardless of database solute tags. */
  @Test
  void pureButaneDoesNotUseAWaterHenryReference() {
    SystemNRTL system = new SystemNRTL(298.15, 10.0);
    system.addComponent("n-butane", 1.0);
    system.setMixingRule("classic");
    system.init(0);
    system.init(1);
    PhaseGENRTL phase = (PhaseGENRTL) system.getPhase(1);
    ComponentGE component = (ComponentGE) phase.getComponent(0);
    assertEquals(component.getAntoineVaporPressure(298.15) / 10.0, component.fugcoef(phase), 1.0e-12);
  }

  /** Dissolved hydrocarbons use Henry data even when their pure-fluid database tag is solvent. */
  @Test
  void aqueousHeptaneDoesNotUseItsPureLiquidVaporPressure() {
    SystemNRTL system = new SystemNRTL(298.15, 10.0);
    system.addComponent("n-heptane", 0.01);
    system.addComponent("water", 0.99);
    system.setMixingRule("classic");
    system.init(0);
    PhaseGENRTL phase = (PhaseGENRTL) system.getPhase(1);
    phase.setAlpha(new double[2][2]);
    phase.setDij(new double[2][2]);
    evaluate(phase);
    ComponentGE component = (ComponentGE) phase.getComponent(0);
    assertEquals(component.getEffectiveHenryCoefficient(phase) / phase.getPressure(), component.fugcoef(phase), 1.0e-8);
  }

  /**
   * Creates a ternary phase with a non-leading solute and water, and a closed-form dilute limit.
   *
   * @return initialized phase
   */
  private static PhaseGENRTL aqueousPhase() {
    SystemNRTL system = new SystemNRTL(298.15, 10.0);
    system.addComponent("methanol", 0.2);
    system.addComponent("propane", 0.1);
    system.addComponent("water", 0.7);
    system.setMixingRule("classic");
    system.init(0);
    PhaseGENRTL phase = (PhaseGENRTL) system.getPhase(1);
    phase.setAlpha(new double[3][3]);
    double[][] interactions = new double[3][3];
    interactions[1][2] = 200.0;
    phase.setDij(interactions);
    evaluate(phase);
    return phase;
  }

  /**
   * Refreshes activity coefficients at the current temperature and fixed composition.
   *
   * @param phase phase to evaluate
   */
  private static void evaluate(PhaseGENRTL phase) {
    phase.getExcessGibbsEnergy(phase, phase.getNumberOfComponents(), phase.getTemperature(), phase.getPressure(),
        PhaseType.AQUEOUS);
  }
}
