package neqsim.thermo.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import neqsim.NeqSimTest;
import neqsim.thermo.component.ComponentEosInterface;
import neqsim.thermo.component.attractiveeosterm.AttractiveTermInterface;
import neqsim.thermo.component.attractiveeosterm.AttractiveTermPr1978;
import neqsim.thermo.mixingrule.SoreideWhitsonParameterization;
import neqsim.thermo.phase.PhaseSoreideWhitson;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/** Regression coverage for the salinity state used by the Soreide-Whitson water alpha. */
class SoreideWhitsonSalinityPropagationTest extends NeqSimTest {
  @ParameterizedTest
  @EnumSource(SoreideWhitsonParameterization.class)
  void flashPropagatesSalinityToWaterAlphaAndDerivatives(SoreideWhitsonParameterization parameterization) {
    double previousAlpha = 0.0;
    for (double salt : new double[] {0.0, 0.05, 0.10}) {
      SystemSoreideWhitson system = fluid(salt, parameterization);
      new ThermodynamicOperations(system).TPflash();
      double concentration = ((PhaseSoreideWhitson) system.getPhase("aqueous")).getSalinityConcentration();
      assertEquals(salt == 0.0, concentration == 0.0);
      for (int phase = 0; phase < system.getNumberOfPhases(); phase++) {
        ComponentEosInterface water = (ComponentEosInterface) system.getPhase(phase).getComponent("water");
        AttractiveTermInterface term = water.getAttractiveTerm();
        double temperature = system.getTemperature();
        double expected = waterAlpha(temperature, water.getTC(), concentration);
        assertEquals(expected, term.alpha(temperature), 1e-12, "water alpha must use aqueous salinity in every phase");
        double step = 0.01;
        double plus = waterAlpha(temperature + step, water.getTC(), concentration);
        double minus = waterAlpha(temperature - step, water.getTC(), concentration);
        assertEquals((plus - minus) / (2.0 * step), term.diffalphaT(temperature), 1e-10);
        assertEquals((plus - 2.0 * expected + minus) / (step * step), term.diffdiffalphaT(temperature), 1e-10);
        ComponentEosInterface methane = (ComponentEosInterface) system.getPhase(phase).getComponent("methane");
        assertEquals(new AttractiveTermPr1978(methane).alpha(temperature),
            methane.getAttractiveTerm().alpha(temperature), 1e-12, "non-water alpha must retain PR78 behavior");
      }
      double alpha = system.getPhase("aqueous").getComponent("water").getAttractiveTerm()
          .alpha(system.getTemperature());
      assertTrue(alpha > previousAlpha, "water alpha must rise with salinity");
      previousAlpha = alpha;
    }
  }

  @Test
  void resettingClonedBrineToZeroRestoresFreshwaterWithoutChangingOriginal() {
    SystemSoreideWhitson brine = fluid(0.10, SoreideWhitsonParameterization.LEGACY);
    new ThermodynamicOperations(brine).TPflash();
    double originalAlpha = brine.getPhase("aqueous").getComponent("water").getAttractiveTerm()
        .alpha(brine.getTemperature());
    SystemSoreideWhitson reset = brine.clone();
    reset.setSalinity(0.0, "mole/sec");
    new ThermodynamicOperations(reset).TPflash();
    SystemSoreideWhitson fresh = fluid(0.0, SoreideWhitsonParameterization.LEGACY);
    new ThermodynamicOperations(fresh).TPflash();
    assertEquals(0.0, ((PhaseSoreideWhitson) reset.getPhase("aqueous")).getSalinityConcentration(), 0.0);
    assertEquals(originalAlpha,
        brine.getPhase("aqueous").getComponent("water").getAttractiveTerm().alpha(brine.getTemperature()), 0.0);
    for (String phase : new String[] {"gas", "aqueous"}) {
      assertEquals(fresh.getPhase(phase).getComponent("water").getAttractiveTerm().alpha(fresh.getTemperature()),
          reset.getPhase(phase).getComponent("water").getAttractiveTerm().alpha(reset.getTemperature()), 1e-12);
      for (int component = 0; component < fresh.getNumberOfComponents(); component++) {
        assertEquals(fresh.getPhase(phase).getComponent(component).getx(),
            reset.getPhase(phase).getComponent(component).getx(), 1e-8,
            "a reused fluid must reproduce a fresh freshwater flash");
      }
    }
  }

  @Test
  void lossAndReturnOfAqueousPhaseRefreshesAttractiveTermState() {
    SystemSoreideWhitson system = fluid(0.10, SoreideWhitsonParameterization.LEGACY);
    ThermodynamicOperations operations = new ThermodynamicOperations(system);
    operations.TPflash();
    double brineAlpha = system.getPhase("aqueous").getComponent("water").getAttractiveTerm().alpha(318.15);
    system.setTemperature(700.0);
    system.setPressure(1.0);
    operations.TPflash();
    assertFalse(system.hasPhaseType("aqueous"));
    ComponentEosInterface water = (ComponentEosInterface) system.getPhase(0).getComponent("water");
    assertEquals(waterAlpha(700.0, water.getTC(), 0.0), water.getAttractiveTerm().alpha(700.0), 1e-12);
    system.setTemperature(318.15);
    system.setPressure(40.0);
    operations.TPflash();
    assertTrue(system.hasPhaseType("aqueous"));
    assertEquals(brineAlpha, system.getPhase("aqueous").getComponent("water").getAttractiveTerm().alpha(318.15), 1e-9);
  }

  private SystemSoreideWhitson fluid(double salt, SoreideWhitsonParameterization parameterization) {
    SystemSoreideWhitson system = new SystemSoreideWhitson(318.15, 40.0);
    system.addComponent("nitrogen", 0.1, "mole/sec");
    system.addComponent("CO2", 0.2, "mole/sec");
    system.addComponent("methane", 0.3, "mole/sec");
    system.addComponent("ethane", 0.3, "mole/sec");
    system.addComponent("water", 0.1, "mole/sec");
    system.addSalinity(salt, "mole/sec");
    system.setTotalFlowRate(15.0, "mole/sec");
    system.setSoreideWhitsonParameterization(parameterization);
    system.setMixingRule(11);
    return system;
  }

  /** Evaluate the documented water-alpha equation independently of the attractive-term implementation. */
  private double waterAlpha(double temperature, double criticalTemperature, double molality) {
    double reducedTemperature = temperature / criticalTemperature;
    double value = 1.0 + 0.453 * (1.0 - reducedTemperature * (1.0 - 0.0103 * Math.pow(molality, 1.1)))
        + 0.0034 * (Math.pow(reducedTemperature, -3.0) - 1.0);
    return value * value;
  }
}
