package neqsim.process.equipment.pipeline;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/** Unit tests for the public component-conservation report contract. */
class TwoFluidComponentConservationReportTest {
  private static final String[] COMPONENT_NAMES = { "methane", "nitrogen" };

  @Test
  void constructorRejectsInvalidArrayShapes() {
    IllegalArgumentException phaseException = assertThrows(IllegalArgumentException.class,
        () -> createReport(COMPONENT_NAMES, new double[COMPONENT_NAMES.length], new double[2][COMPONENT_NAMES.length],
            new double[3][COMPONENT_NAMES.length][1]));
    assertTrue(phaseException.getMessage().contains("finalPhaseInventoryKg"));

    IllegalArgumentException componentException = assertThrows(IllegalArgumentException.class,
        () -> createReport(COMPONENT_NAMES, new double[1], new double[3][COMPONENT_NAMES.length],
            new double[3][COMPONENT_NAMES.length][1]));
    assertTrue(componentException.getMessage().contains("initialInventoryKg"));

    IllegalArgumentException profileException = assertThrows(IllegalArgumentException.class,
        () -> createReport(COMPONENT_NAMES, new double[COMPONENT_NAMES.length], new double[3][COMPONENT_NAMES.length],
            new double[3][1][1]));
    assertTrue(profileException.getMessage().contains("phaseMassFractionProfile"));
  }

  @Test
  void constructorRejectsInvalidNamesAndNumbers() {
    String[] duplicateNames = { "methane", "methane" };
    IllegalArgumentException nameException = assertThrows(IllegalArgumentException.class,
        () -> createReport(duplicateNames, new double[duplicateNames.length], new double[3][duplicateNames.length],
            new double[3][duplicateNames.length][1]));
    assertTrue(nameException.getMessage().contains("duplicate"));

    double[] nonFiniteInventory = { 1.0, Double.NaN };
    IllegalArgumentException numberException = assertThrows(IllegalArgumentException.class,
        () -> createReport(COMPONENT_NAMES, nonFiniteInventory, new double[3][COMPONENT_NAMES.length],
            new double[3][COMPONENT_NAMES.length][1]));
    assertTrue(numberException.getMessage().contains("finite"));
  }

  @Test
  void constructorAcceptsAbsentPhaseBoundsAndCopiesInputs() {
    double[] inventory = { 1.0, 0.0 };
    TwoFluidComponentConservationReport report = createReport(COMPONENT_NAMES, inventory,
        new double[3][COMPONENT_NAMES.length], new double[3][COMPONENT_NAMES.length][1]);

    inventory[0] = 2.0;
    assertArrayEquals(new double[] { 1.0, 0.0 }, report.getInitialInventoryKg(), 0.0);
    assertTrue(Double.isNaN(report.getMinimumMassFraction()));
    assertTrue(Double.isNaN(report.getMaximumMassFraction()));
  }

  private static TwoFluidComponentConservationReport createReport(String[] componentNames, double[] initialInventory,
      double[][] finalPhaseInventory, double[][][] profiles) {
    int componentCount = componentNames.length;
    double[] zeros = new double[componentCount];
    double[][] phaseZeros = new double[3][componentCount];
    return new TwoFluidComponentConservationReport(1.0, 1, componentNames, initialInventory, zeros, zeros, zeros, zeros,
        zeros, 0.0, finalPhaseInventory, phaseZeros, 0.0, profiles, Double.NaN, Double.NaN, 0.0, 0.0, 0.0, true,
        "valid");
  }
}
