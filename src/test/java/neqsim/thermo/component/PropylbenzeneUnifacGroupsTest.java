package neqsim.thermo.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemUMRPRUMCEosNew;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Regression coverage for propylbenzene, which was present in COMP.csv but absent from both UNIFAC group-assignment
 * tables.
 *
 * <p>
 * A component with no group assignment ends up with R and Q of zero, which makes the combinatorial term of the activity
 * coefficient evaluate log(0) and poisons every downstream fugacity with NaN. The symptom was a UMR-PRU phase envelope
 * that returned a single point. These tests assert the group assignment is present and that the model produces finite
 * numbers with it.
 * </p>
 *
 * @author ESOL
 * @version $Id: $Id
 */
public class PropylbenzeneUnifacGroupsTest extends neqsim.NeqSimTest {
  private SystemInterface createFluid(String heavyComponent) {
    SystemInterface fluid = new SystemUMRPRUMCEosNew(273.15 + 25.0, 10.0);
    fluid.addComponent("methane", 0.90);
    fluid.addComponent(heavyComponent, 0.10);
    fluid.setMixingRule("HV", "UNIFAC_UMRPRU");
    return fluid;
  }

  private SystemInterface createFluid() {
    return createFluid("propylbenzene");
  }

  /**
   * The UNIFAC components live in the excess Gibbs energy phase held by the mixing rule, not on the equation-of-state
   * phase itself, which carries ComponentPRvolcor instead.
   *
   * @param fluid a flashed fluid using the UNIFAC_UMRPRU mixing rule
   * @param componentName component to fetch
   * @return the UNIFAC component carrying the group assignment
   */
  private ComponentGEUnifac unifacComponentOf(SystemInterface fluid, String componentName) {
    neqsim.thermo.phase.PhaseEos phase = (neqsim.thermo.phase.PhaseEos) fluid.getPhase(0);
    neqsim.thermo.phase.PhaseInterface gePhase = phase.getMixingRule().getGEPhase();
    assertTrue(gePhase != null, "the UNIFAC_UMRPRU mixing rule must expose a GE phase");
    return (ComponentGEUnifac) gePhase.getComponent(componentName);
  }

  @Test
  void testPropylbenzeneHasAGroupAssignment() {
    SystemInterface fluid = createFluid();
    new ThermodynamicOperations(fluid).TPflash();

    ComponentGEUnifac component = unifacComponentOf(fluid, "propylbenzene");
    assertTrue(component.getUnifacGroups().length > 0,
        "propylbenzene must carry UNIFAC groups; without them R and Q are zero and the "
            + "activity coefficient becomes NaN");
  }

  @Test
  void testGroupAssignmentMatchesTheDdbstDecomposition() {
    SystemInterface fluid = createFluid();
    new ThermodynamicOperations(fluid).TPflash();
    ComponentGEUnifac component = unifacComponentOf(fluid, "propylbenzene");

    int ch3 = 0;
    int ch2 = 0;
    int ach = 0;
    int acch2 = 0;
    for (neqsim.thermo.atomelement.UNIFACgroup group : component.getUnifacGroups()) {
      if ("CH3".equals(group.getGroupName())) {
        ch3 += group.getN();
      } else if ("CH2".equals(group.getGroupName())) {
        ch2 += group.getN();
      } else if ("ACH".equals(group.getGroupName())) {
        ach += group.getN();
      } else if ("ACCH2".equals(group.getGroupName())) {
        acch2 += group.getN();
      }
    }

    // C6H5-CH2CH2CH3: the ring carbon carrying the chain is ACCH2, the middle carbon is a plain
    // CH2, and the chain terminates in a CH3. Matches DDBST {1:1, 2:1, 9:5, 12:1}.
    assertEquals(1, ch3, "CH3 count");
    assertEquals(1, ch2, "CH2 count");
    assertEquals(5, ach, "ACH count");
    assertEquals(1, acch2, "ACCH2 count");
  }

  @Test
  void testFlashProducesFiniteFugacitiesRatherThanNaN() {
    SystemInterface fluid = createFluid();
    ThermodynamicOperations operations = new ThermodynamicOperations(fluid);
    operations.TPflash();

    for (int phaseIndex = 0; phaseIndex < fluid.getNumberOfPhases(); phaseIndex++) {
      for (int i = 0; i < fluid.getPhase(phaseIndex).getNumberOfComponents(); i++) {
        double fugacityCoefficient = fluid.getPhase(phaseIndex).getComponent(i).getFugacityCoefficient();
        assertTrue(Double.isFinite(fugacityCoefficient),
            "fugacity coefficient of " + fluid.getPhase(phaseIndex).getComponent(i).getComponentName() + " in phase "
                + phaseIndex + " was " + fugacityCoefficient);
      }
    }

    double totalFraction = 0.0;
    for (int phaseIndex = 0; phaseIndex < fluid.getNumberOfPhases(); phaseIndex++) {
      double fraction = fluid.getPhase(phaseIndex).getPhaseFraction();
      assertTrue(Double.isFinite(fraction), "phase fraction was " + fraction);
      totalFraction += fraction;
    }
    assertEquals(1.0, totalFraction, 1.0e-6, "phase fractions must sum to one");
  }

  @Test
  void testDewTemperatureIsConsistentWithTheHomologousSeries() throws Exception {
    // Compare saturation at the same pressure. A full heavy-mixture envelope can reach its
    // pressure limit and is not needed to validate the UNIFAC group assignments.
    double toluene = dewTemperatureAtOneBar("toluene");
    double ethylbenzene = dewTemperatureAtOneBar("ethylbenzene");
    double propylbenzene = dewTemperatureAtOneBar("propylbenzene");

    assertTrue(Double.isFinite(propylbenzene),
        "propylbenzene dew temperature was " + propylbenzene + "; a missing group assignment makes this NaN");
    assertTrue(toluene < ethylbenzene && ethylbenzene < propylbenzene,
        "dew temperature must increase with molar mass along toluene, ethylbenzene, propylbenzene, got " + toluene
            + ", " + ethylbenzene + ", " + propylbenzene);
    // Propylbenzene is one methylene heavier than ethylbenzene, so the step should be modest.
    assertEquals(20.0, propylbenzene - ethylbenzene, 15.0,
        "propylbenzene should sit roughly one methylene above ethylbenzene");
  }

  /**
   * Dew temperature at 1 bara, independently of whether the full envelope closes.
   *
   * @param heavyComponent the heavy component paired with methane
   * @return dew temperature in K
   * @throws Exception if the saturation flash fails
   */
  private double dewTemperatureAtOneBar(String heavyComponent) throws Exception {
    SystemInterface fluid = createFluid(heavyComponent);
    fluid.setPressure(1.0);
    ThermodynamicOperations operations = new ThermodynamicOperations(fluid);
    operations.dewPointTemperatureFlash();
    return fluid.getTemperature();
  }
}
