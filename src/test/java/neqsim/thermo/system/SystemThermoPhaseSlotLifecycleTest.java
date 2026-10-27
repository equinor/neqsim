package neqsim.thermo.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import org.junit.jupiter.api.Test;

/**
 * Verifies that logical phase removal preserves a permutation of distinct physical phase slots.
 */
class SystemThermoPhaseSlotLifecycleTest {
  private static final double TOLERANCE = 1.0e-12;

  @Test
  void removeAndAddRecyclesPhysicalSlotForBothRemovalApis() {
    for (boolean keepTotalComposition : new boolean[] {false, true}) {
      for (int removedPosition = 0; removedPosition < 3; removedPosition++) {
        SystemInterface fluid = createThreePhaseFluid();
        Object[] originalPhases = activePhases(fluid);
        String[] originalTypes = activePhaseTypes(fluid);
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

        if (keepTotalComposition) {
          assertEquals(originalTotal, fluid.getTotalNumberOfMoles(), TOLERANCE);
          assertComponentInventory(fluid, originalInventory);
        } else {
          assertEquals(Math.max(0.0, originalTotal - removedTotal),
              fluid.getTotalNumberOfMoles(), TOLERANCE);
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
        assertDistinctActiveSlots(fluid);

        if (keepTotalComposition) {
          fluid.init(0);
          for (int phase = 0; phase < 3; phase++) {
            int originalPosition = phase == 2 ? removedPosition
                : survivorOriginalPosition(phase, removedPosition);
            assertEquals(originalTypes[originalPosition], fluid.getPhase(phase).getPhaseTypeName());
          }
        }
      }
    }
  }

  @Test
  void repeatedRemovalAndAdditionMaintainsSlotPermutation() {
    SystemInterface fluid = createThreePhaseFluid();
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

  @Test
  void deleteFluidPhaseAlsoRecyclesItsPhysicalSlot() {
    SystemInterface fluid = createThreePhaseFluid();
    Object removedPhase = fluid.getPhase(1);
    int removedPhysicalIndex = fluid.getPhaseIndex(1);

    fluid.deleteFluidPhase(1);

    assertEquals(2, fluid.getNumberOfPhases());
    assertEquals(removedPhysicalIndex, fluid.getPhaseIndex(2));
    assertDistinctActiveSlots(fluid);

    fluid.addPhase();

    assertSame(removedPhase, fluid.getPhase(2));
    assertDistinctActiveSlots(fluid);
  }

  private static SystemInterface createThreePhaseFluid() {
    SystemInterface fluid = new SystemSrkEos(293.15, 60.0);
    fluid.addComponent("methane", 0.7);
    fluid.addComponent("n-heptane", 0.3);
    fluid.setMixingRule("classic");
    fluid.setNumberOfPhases(3);
    fluid.setBeta(0, 0.2);
    fluid.setBeta(1, 0.3);
    fluid.setBeta(2, 0.5);
    fluid.setPhaseType(0, "gas");
    fluid.setPhaseType(1, "oil");
    fluid.setPhaseType(2, "aqueous");
    fluid.init(0);
    return fluid;
  }

  private static Object[] activePhases(SystemInterface fluid) {
    return new Object[] {fluid.getPhase(0), fluid.getPhase(1), fluid.getPhase(2)};
  }

  private static String[] activePhaseTypes(SystemInterface fluid) {
    return new String[] {fluid.getPhase(0).getPhaseTypeName(),
        fluid.getPhase(1).getPhaseTypeName(), fluid.getPhase(2).getPhaseTypeName()};
  }

  private static double[] activeBetas(SystemInterface fluid) {
    return new double[] {fluid.getBeta(0), fluid.getBeta(1), fluid.getBeta(2)};
  }

  private static double[] overallComponentMoles(SystemInterface fluid) {
    double[] inventory = new double[fluid.getNumberOfComponents()];
    for (int component = 0; component < inventory.length; component++) {
      inventory[component] = fluid.getPhase(0).getComponent(component).getNumberOfmoles();
    }
    return inventory;
  }

  private static double[] phaseComponentMoles(SystemInterface fluid, int phase) {
    double[] inventory = new double[fluid.getNumberOfComponents()];
    for (int component = 0; component < inventory.length; component++) {
      inventory[component] =
          fluid.getPhase(phase).getComponent(component).getNumberOfMolesInPhase();
    }
    return inventory;
  }

  private static void assertComponentInventory(SystemInterface fluid, double[] expected) {
    for (int component = 0; component < expected.length; component++) {
      assertEquals(expected[component],
          fluid.getPhase(0).getComponent(component).getNumberOfmoles(), TOLERANCE);
    }
  }

  private static void assertSurvivingOrder(SystemInterface fluid, Object[] originalPhases,
      int removedPosition) {
    for (int phase = 0; phase < 2; phase++) {
      assertSame(originalPhases[survivorOriginalPosition(phase, removedPosition)],
          fluid.getPhase(phase));
    }
  }

  private static int survivorOriginalPosition(int survivorPosition, int removedPosition) {
    return survivorPosition < removedPosition ? survivorPosition : survivorPosition + 1;
  }

  private static void assertDistinctActiveSlots(SystemInterface fluid) {
    for (int first = 0; first < fluid.getNumberOfPhases(); first++) {
      for (int second = first + 1; second < fluid.getNumberOfPhases(); second++) {
        assertNotEquals(fluid.getPhaseIndex(first), fluid.getPhaseIndex(second));
        assertNotSame(fluid.getPhase(first), fluid.getPhase(second));
      }
    }
  }
}
