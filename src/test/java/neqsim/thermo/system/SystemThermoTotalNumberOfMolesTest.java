package neqsim.thermo.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Regression tests for {@link SystemThermo#setTotalNumberOfMoles(double)}.
 *
 * <p>
 * The setter used to assign the scalar total only, leaving the per-component mole numbers untouched. Since
 * {@code init(0)} derives the overall mole fractions as {@code z = n_i / totalNumberOfMoles}, that left z summing to
 * something other than one and the following flash converged on a corrupted feed (typically the trivial single-phase
 * solution, e.g. Rs = 0 for a live oil in a black-oil conversion).
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public class SystemThermoTotalNumberOfMolesTest {

  /** Composition deliberately entered as mol% so the total moles is 100, not 1. */
  private static SystemInterface buildRichGasInMolePercent() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 20.0, 60.0);
    fluid.addComponent("methane", 70.0);
    fluid.addComponent("ethane", 10.0);
    fluid.addComponent("propane", 8.0);
    fluid.addComponent("n-butane", 5.0);
    fluid.addComponent("n-heptane", 7.0);
    fluid.setMixingRule("classic");
    return fluid;
  }

  private static double sumComponentMoles(SystemInterface fluid) {
    double sum = 0.0;
    for (int i = 0; i < fluid.getPhase(0).getNumberOfComponents(); i++) {
      sum += fluid.getPhase(0).getComponent(i).getNumberOfmoles();
    }
    return sum;
  }

  private static double sumOverallMoleFractions(SystemInterface fluid) {
    double sum = 0.0;
    for (int i = 0; i < fluid.getPhase(0).getNumberOfComponents(); i++) {
      sum += fluid.getPhase(0).getComponent(i).getz();
    }
    return sum;
  }

  /**
   * Component moles must be rescaled with the total so the two stay consistent.
   */
  @Test
  public void testComponentMolesFollowTotal() {
    SystemInterface fluid = buildRichGasInMolePercent();
    fluid.init(0);
    double[] zBefore = fluid.getMolarComposition();

    fluid.setTotalNumberOfMoles(1.0);
    fluid.init(0);

    assertEquals(1.0, fluid.getTotalNumberOfMoles(), 1e-12);
    assertEquals(1.0, sumComponentMoles(fluid), 1e-10,
        "sum of component moles must equal the total after setTotalNumberOfMoles");
    double[] zAfter = fluid.getMolarComposition();
    for (int i = 0; i < zBefore.length; i++) {
      assertEquals(zBefore[i], zAfter[i], 1e-10, "composition must be preserved for component " + i);
    }
  }

  /**
   * After {@code init(0)} the overall mole fractions must still sum to one — this is what the old implementation broke.
   */
  @Test
  public void testOverallMoleFractionsStayNormalisedAfterInit() {
    SystemInterface fluid = buildRichGasInMolePercent();
    fluid.setTotalNumberOfMoles(1.0);
    fluid.init(0);

    assertEquals(1.0, sumOverallMoleFractions(fluid), 1e-10,
        "sum of z must be 1 after setTotalNumberOfMoles + init(0)");
  }

  /**
   * The idiom {@code setMolarComposition(z)} followed by {@code setTotalNumberOfMoles(1.0)} must still give a physical
   * two-phase flash. With the old setter the feed was denormalised by the factor 100 and the flash degenerated.
   */
  @Test
  public void testSetMolarCompositionThenNormaliseGivesSameFlashAsReference() {
    SystemInterface reference = buildRichGasInMolePercent();
    new ThermodynamicOperations(reference).TPflash();

    SystemInterface fluid = buildRichGasInMolePercent();
    fluid.setMolarComposition(reference.getMolarComposition());
    fluid.setTotalNumberOfMoles(1.0);
    new ThermodynamicOperations(fluid).TPflash();

    assertEquals(reference.getNumberOfPhases(), fluid.getNumberOfPhases(),
        "normalising the total moles must not change the number of phases");
    assertTrue(fluid.getNumberOfPhases() > 1, "the rich gas is two-phase at 20 C and 60 bara");
    assertEquals(reference.getBeta(0), fluid.getBeta(0), 1e-6, "gas phase fraction must be unchanged");
    assertEquals(1.0, fluid.getTotalNumberOfMoles(), 1e-9);
  }

  /**
   * Rescaling an empty fluid falls back to the stored overall mole fractions, matching {@code init(initType > 0)}.
   */
  @Test
  public void testRescalingAnEmptyFluidRestoresComposition() {
    SystemInterface fluid = buildRichGasInMolePercent();
    fluid.init(0);
    double[] zBefore = fluid.getMolarComposition();

    fluid.setEmptyFluid();
    fluid.setTotalNumberOfMoles(1.0);
    fluid.init(0);

    assertEquals(1.0, sumComponentMoles(fluid), 1e-10);
    double[] zAfter = fluid.getMolarComposition();
    for (int i = 0; i < zBefore.length; i++) {
      assertEquals(zBefore[i], zAfter[i], 1e-10, "composition must be restored for component " + i);
    }
  }

  /** Setting the total to zero must empty the fluid rather than leave stale component moles. */
  @Test
  public void testZeroTotalEmptiesComponentMoles() {
    SystemInterface fluid = buildRichGasInMolePercent();
    fluid.setTotalNumberOfMoles(0.0);

    assertEquals(0.0, fluid.getTotalNumberOfMoles(), 1e-30);
    assertEquals(0.0, sumComponentMoles(fluid), 1e-30);
  }

  /** Adding components must not be disturbed by the rescaling setter. */
  @Test
  public void testAddComponentStillAccumulatesTotal() {
    SystemInterface fluid = new SystemSrkEos(298.15, 10.0);
    fluid.addComponent("methane", 1.0);
    fluid.addComponent("ethane", 3.0);
    fluid.setMixingRule("classic");

    assertEquals(4.0, fluid.getTotalNumberOfMoles(), 1e-12);
    assertEquals(4.0, sumComponentMoles(fluid), 1e-12);
  }
}
