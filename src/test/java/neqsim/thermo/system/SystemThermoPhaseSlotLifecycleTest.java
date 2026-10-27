package neqsim.thermo.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import neqsim.thermo.phase.PhaseType;
import org.junit.jupiter.api.Test;

/**
 * Verifies that logical phase removal preserves a permutation of distinct physical phase slots.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class SystemThermoPhaseSlotLifecycleTest {
  private static final double TOLERANCE = 1.0e-12;

  /** Verifies slot, metadata and inventory ownership for both removal APIs and phase orders. */
  @Test
  void removeAndAddRecyclesPhysicalSlotForBothRemovalApis() {
    for (boolean reordered : new boolean[] {false, true}) {
      for (boolean keepTotalComposition : new boolean[] {false, true}) {
        for (int removedPosition = 0; removedPosition < 3; removedPosition++) {
          SystemThermo fluid = createThreePhaseFluid(reordered);
          Object[] originalPhases = activePhases(fluid);
          PhaseType[] originalTypes = configuredPhaseTypes(fluid);
          double[] originalBetas = activeBetas(fluid);
          double[] originalInventory = overallComponentMoles(fluid);
          double[] removedInventory = phaseComponentMoles(fluid, removedPosition);
          double originalTotal = fluid.getTotalNumberOfMoles();
          double removedTotal = fluid.getPhase(removedPosition).getNumberOfMolesInPhase();
          int removedPhysicalIndex = fluid.getPhaseIndex(removedPosition);

          if (keepTotalComposition) {
            fluid.removePhaseKeepTotalComposition(removedPosition);
          } else {
            fluid.removePhase(removedPosition);
          }

          assertEquals(2, fluid.getNumberOfPhases());
          assertEquals(removedPhysicalIndex, fluid.getPhaseIndex(2),
              "The released physical slot must be parked at the inactive tail");
          assertSurvivingOrder(fluid, originalPhases, removedPosition);
          assertDistinctActiveSlots(fluid);
          assertSurvivingMetadata(fluid, originalTypes, originalBetas, removedPosition);

          if (keepTotalComposition) {
            assertEquals(originalTotal, fluid.getTotalNumberOfMoles(), TOLERANCE);
            assertComponentInventory(fluid, originalInventory);
          } else {
            assertEquals(Math.max(0.0, originalTotal - removedTotal), fluid.getTotalNumberOfMoles(), TOLERANCE);
            for (int component = 0; component < originalInventory.length; component++) {
              assertEquals(originalInventory[component] - removedInventory[component],
                  fluid.getPhase(0).getComponent(component).getNumberOfmoles(), TOLERANCE);
            }
          }

          fluid.addPhase();

          assertEquals(3, fluid.getNumberOfPhases());
          assertSame(originalPhases[removedPosition], fluid.getPhase(2),
              "addPhase must reactivate the released physical slot");
          assertEquals(originalBetas[removedPosition], fluid.getBeta(2), TOLERANCE);
          assertEquals(originalTypes[removedPosition], fluid.phaseType[fluid.getPhaseIndex(2)]);
          assertDistinctActiveSlots(fluid);

          // init(1) consumes configured root metadata without resetting the phase mapping.
          fluid.init(1);
          assertEquals(3, fluid.getNumberOfPhases());
          assertSurvivingMetadata(fluid, originalTypes, originalBetas, removedPosition);
          assertEquals(originalTypes[removedPosition], fluid.phaseType[fluid.getPhaseIndex(2)]);
          assertDistinctActiveSlots(fluid);
        }
      }
    }
  }

  /** Verifies that successive removals recycle each released physical slot exactly once. */
  @Test
  void repeatedRemovalAndAdditionMaintainsSlotPermutation() {
    SystemThermo fluid = createThreePhaseFluid(false);
    Object[] originalPhases = activePhases(fluid);

    fluid.removePhaseKeepTotalComposition(1);
    fluid.removePhaseKeepTotalComposition(0);
    fluid.addPhase();
    fluid.addPhase();

    assertSame(originalPhases[2], fluid.getPhase(0));
    assertSame(originalPhases[0], fluid.getPhase(1));
    assertSame(originalPhases[1], fluid.getPhase(2));
    assertDistinctActiveSlots(fluid);
  }

  /** Verifies deletion and reuse at every logical position, including reordered mappings. */
  @Test
  void deleteFluidPhaseAlsoRecyclesItsPhysicalSlot() {
    for (boolean reordered : new boolean[] {false, true}) {
      for (int removedPosition = 0; removedPosition < 3; removedPosition++) {
        SystemThermo fluid = createThreePhaseFluid(reordered);
        Object[] originalPhases = activePhases(fluid);
        PhaseType[] originalTypes = configuredPhaseTypes(fluid);
        double[] originalBetas = activeBetas(fluid);
        double[] originalInventory = overallComponentMoles(fluid);
        double originalTotal = fluid.getTotalNumberOfMoles();
        int removedPhysicalIndex = fluid.getPhaseIndex(removedPosition);

        fluid.deleteFluidPhase(removedPosition);

        assertEquals(2, fluid.getNumberOfPhases());
        assertEquals(removedPhysicalIndex, fluid.getPhaseIndex(2));
        assertSurvivingOrder(fluid, originalPhases, removedPosition);
        assertSurvivingMetadata(fluid, originalTypes, originalBetas, removedPosition);
        assertEquals(originalTotal, fluid.getTotalNumberOfMoles(), TOLERANCE);
        assertComponentInventory(fluid, originalInventory);
        assertDistinctActiveSlots(fluid);

        fluid.addPhase();

        assertSame(originalPhases[removedPosition], fluid.getPhase(2));
        assertEquals(originalTypes[removedPosition], fluid.phaseType[fluid.getPhaseIndex(2)]);
        assertEquals(originalBetas[removedPosition], fluid.getBeta(2), TOLERANCE);
        assertDistinctActiveSlots(fluid);
      }
    }
  }

  /** Verifies normalization visits each reactivated physical beta only once. */
  @Test
  void betaNormalizationAfterReusePreservesDistinctSlotFractions() {
    SystemThermo fluid = createThreePhaseFluid(true);
    fluid.removePhaseKeepTotalComposition(0);
    fluid.addPhase();
    fluid.setBeta(0, 0.4);
    fluid.setBeta(1, 0.6);
    fluid.setBeta(2, 1.0);

    fluid.normalizeBeta();

    assertEquals(0.2, fluid.getBeta(0), TOLERANCE);
    assertEquals(0.3, fluid.getBeta(1), TOLERANCE);
    assertEquals(0.5, fluid.getBeta(2), TOLERANCE);
    assertDistinctActiveSlots(fluid);
  }

  /**
   * Builds a synthetic three-slot state for lifecycle checks, without asserting equilibrium.
   *
   * @param reordered whether to rotate the logical-to-physical mapping before removal
   * @return initialized fluid with distinct configured roots and phase fractions
   */
  private static SystemThermo createThreePhaseFluid(boolean reordered) {
    SystemThermo fluid = new SystemSrkEos(293.15, 60.0);
    fluid.addComponent("methane", 0.7);
    fluid.addComponent("n-heptane", 0.3);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);
    fluid.init(0);
    fluid.setNumberOfPhases(3);
    if (reordered) {
      fluid.setPhaseIndex(0, 2);
      fluid.setPhaseIndex(1, 0);
      fluid.setPhaseIndex(2, 1);
    }
    fluid.setBeta(0, 0.2);
    fluid.setBeta(1, 0.3);
    fluid.setBeta(2, 0.5);
    fluid.setPhaseType(0, PhaseType.GAS);
    fluid.setPhaseType(1, PhaseType.OIL);
    fluid.setPhaseType(2, PhaseType.AQUEOUS);
    fluid.init(1);
    assertEquals(3, fluid.getNumberOfPhases());
    return fluid;
  }

  /**
   * Captures active phase identities.
   *
   * @param fluid three-phase fluid
   * @return physical objects in logical order
   */
  private static Object[] activePhases(SystemInterface fluid) {
    return new Object[] {fluid.getPhase(0), fluid.getPhase(1), fluid.getPhase(2)};
  }

  /**
   * Reads the configured root types; EOS classification may differ for a single volume root.
   *
   * @param fluid fluid to inspect
   * @return configured types in logical order
   */
  private static PhaseType[] configuredPhaseTypes(SystemThermo fluid) {
    return new PhaseType[] {fluid.phaseType[fluid.getPhaseIndex(0)], fluid.phaseType[fluid.getPhaseIndex(1)],
        fluid.phaseType[fluid.getPhaseIndex(2)]};
  }

  /**
   * Checks that surviving metadata stays attached to its physical slot.
   *
   * @param fluid fluid after removal
   * @param originalTypes configured types before removal
   * @param originalBetas fractions before removal
   * @param removedPosition removed logical position
   */
  private static void assertSurvivingMetadata(SystemThermo fluid, PhaseType[] originalTypes, double[] originalBetas,
      int removedPosition) {
    for (int phase = 0; phase < 2; phase++) {
      int originalPosition = survivorOriginalPosition(phase, removedPosition);
      assertEquals(originalTypes[originalPosition], fluid.phaseType[fluid.getPhaseIndex(phase)]);
      assertEquals(originalBetas[originalPosition], fluid.getBeta(phase), TOLERANCE);
    }
  }

  /**
   * Captures active fractions.
   *
   * @param fluid three-phase fluid
   * @return fractions in logical order
   */
  private static double[] activeBetas(SystemInterface fluid) {
    return new double[] {fluid.getBeta(0), fluid.getBeta(1), fluid.getBeta(2)};
  }

  /**
   * Captures overall component amounts.
   *
   * @param fluid fluid to inspect
   * @return component amounts in mol
   */
  private static double[] overallComponentMoles(SystemInterface fluid) {
    double[] inventory = new double[fluid.getNumberOfComponents()];
    for (int component = 0; component < inventory.length; component++) {
      inventory[component] = fluid.getPhase(0).getComponent(component).getNumberOfmoles();
    }
    return inventory;
  }

  /**
   * Captures component amounts in one phase.
   *
   * @param fluid fluid to inspect
   * @param phase logical phase index
   * @return phase component amounts in mol
   */
  private static double[] phaseComponentMoles(SystemInterface fluid, int phase) {
    double[] inventory = new double[fluid.getNumberOfComponents()];
    for (int component = 0; component < inventory.length; component++) {
      inventory[component] = fluid.getPhase(phase).getComponent(component).getNumberOfMolesInPhase();
    }
    return inventory;
  }

  /**
   * Checks retained overall component amounts.
   *
   * @param fluid fluid to inspect
   * @param expected expected amounts in mol
   */
  private static void assertComponentInventory(SystemInterface fluid, double[] expected) {
    for (int component = 0; component < expected.length; component++) {
      assertEquals(expected[component], fluid.getPhase(0).getComponent(component).getNumberOfmoles(), TOLERANCE);
    }
  }

  /**
   * Checks survivor identity and logical ordering.
   *
   * @param fluid fluid after removal
   * @param originalPhases identities before removal
   * @param removedPosition removed logical position
   */
  private static void assertSurvivingOrder(SystemInterface fluid, Object[] originalPhases, int removedPosition) {
    for (int phase = 0; phase < 2; phase++) {
      assertSame(originalPhases[survivorOriginalPosition(phase, removedPosition)], fluid.getPhase(phase));
    }
  }

  /**
   * Maps a survivor back to its original logical position.
   *
   * @param survivorPosition logical position after removal
   * @param removedPosition removed logical position
   * @return original logical position
   */
  private static int survivorOriginalPosition(int survivorPosition, int removedPosition) {
    return survivorPosition < removedPosition ? survivorPosition : survivorPosition + 1;
  }

  /**
   * Checks that no active logical phases alias a physical index or object.
   *
   * @param fluid fluid to inspect
   */
  private static void assertDistinctActiveSlots(SystemInterface fluid) {
    for (int first = 0; first < fluid.getNumberOfPhases(); first++) {
      for (int second = first + 1; second < fluid.getNumberOfPhases(); second++) {
        assertNotEquals(fluid.getPhaseIndex(first), fluid.getPhaseIndex(second));
        assertNotSame(fluid.getPhase(first), fluid.getPhase(second));
      }
    }
  }
}
