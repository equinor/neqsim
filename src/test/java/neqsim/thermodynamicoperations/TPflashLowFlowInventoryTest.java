package neqsim.thermodynamicoperations;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.concurrent.CancellationException;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemElectrolyteCPAstatoil;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.flashops.reactiveflash.FormulaMatrix;

/**
 * Conservation regressions for the public TP-flash normalization of small feeds.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
class TPflashLowFlowInventoryTest {
  /** A reactive flash must restore the original basis without undoing its stoichiometric total-mole change. */
  @Test
  void reactiveFlashConservesElementsAcrossNormalization() {
    SystemInterface fluid = reactiveFluid();
    FormulaMatrix formula = new FormulaMatrix(fluid);
    double[] originalElements = formula.computeElementVector(inventory(fluid));
    double originalTotal = fluid.getTotalNumberOfMoles();
    new ThermodynamicOperations(fluid).TPflash();
    assertElements(formula, originalElements, fluid);
    assertTrue(fluid.getTotalNumberOfMoles() < originalTotal - 5.0e-12,
        "CO2 hydration changes species moles, so restoring the old total would create elements");
    assertTrue(fluid.getChemicalReactionOperations().getMaximumAbsoluteReactionLogResidual() < 2.0e-6);
  }

  /**
   * The asynchronous option must restore the basis after the worker completes, rather than during its flash.
   *
   * @throws InterruptedException if the test thread is interrupted while awaiting its flash worker
   */
  @Test
  void threadedReactiveFlashConservesElementsAfterCompletion() throws InterruptedException {
    SystemInterface fluid = reactiveFluid();
    FormulaMatrix formula = new FormulaMatrix(fluid);
    double[] originalElements = formula.computeElementVector(inventory(fluid));
    ThermodynamicOperations operations = new ThermodynamicOperations(fluid);
    operations.setRunAsThread(true);
    operations.TPflash();
    Thread worker = operations.getThermoOperationThread();
    worker.join(15000);
    assertFalse(worker.isAlive(), "The bounded reactive flash must finish before its basis is inspected");
    assertElements(formula, originalElements, fluid);
    assertTrue(fluid.getChemicalReactionOperations().getMaximumAbsoluteReactionLogResidual() < 2.0e-6);
  }

  /** Cancellation after normalization must restore input species amounts and permit a later calculation. */
  @Test
  void interruptedReactiveFlashRestoresOriginalInventory() {
    SystemInterface fluid = reactiveFluid();
    double[] originalMoles = inventory(fluid);
    double originalTotal = fluid.getTotalNumberOfMoles();
    ThermodynamicOperations operations = new ThermodynamicOperations(fluid);
    Thread.currentThread().interrupt();
    try {
      assertThrows(CancellationException.class, operations::TPflash);
    } finally {
      Thread.interrupted();
    }
    assertArrayEquals(originalMoles, inventory(fluid), 1.0e-22);
    assertEquals(originalTotal, fluid.getTotalNumberOfMoles(), 1.0e-22);
    FormulaMatrix formula = new FormulaMatrix(fluid);
    double[] elements = formula.computeElementVector(originalMoles);
    operations.TPflash();
    assertElements(formula, elements, fluid);
  }

  /** Fixed-species fluids retain their original amounts under the same inverse scaling. */
  @Test
  void nonReactiveFlashPreservesSpeciesAmounts() {
    SystemInterface fluid = new SystemSrkEos(280.0, 30.0);
    fluid.addComponent("methane", 0.8);
    fluid.addComponent("ethane", 0.2);
    fluid.setMixingRule(2);
    fluid.setTotalNumberOfMoles(1.0e-7);
    double[] originalMoles = inventory(fluid);
    new ThermodynamicOperations(fluid).TPflash();
    assertArrayEquals(originalMoles, inventory(fluid), 2.0e-15);
    assertEquals(1.0e-7, fluid.getTotalNumberOfMoles(), 2.0e-15);
  }

  /**
   * Constructs a supported reaction system whose equilibrium changes total species moles.
   *
   * @return initialized reactive feed below the public TP-flash normalization threshold
   */
  private static SystemInterface reactiveFluid() {
    SystemInterface fluid = new SystemElectrolyteCPAstatoil(283.15, 50.0);
    fluid.addComponent("CO2", 10.0);
    fluid.addComponent("water", 1.0 / 0.01801528);
    fluid.addComponent("Na+", 0.5);
    fluid.addComponent("Cl-", 0.5);
    fluid.chemicalReactionInit();
    fluid.setMixingRule(10);
    fluid.setMultiPhaseCheck(true);
    fluid.setTotalNumberOfMoles(1.0e-7);
    return fluid;
  }

  /**
   * Reads the original species inventory independently of phase partitioning.
   *
   * @param fluid fluid to inspect
   * @return overall component amounts in moles
   */
  private static double[] inventory(SystemInterface fluid) {
    double[] moles = new double[fluid.getNumberOfComponents()];
    for (int component = 0; component < moles.length; component++) {
      moles[component] = fluid.getComponent(component).getNumberOfmoles();
    }
    return moles;
  }

  /**
   * Compares conserved elements on the original small-feed basis.
   *
   * @param formula original component formula matrix
   * @param expected input element amounts
   * @param fluid completed reactive fluid
   */
  private static void assertElements(FormulaMatrix formula, double[] expected, SystemInterface fluid) {
    double[] actual = formula.computeElementVector(inventory(fluid));
    for (int element = 0; element < expected.length; element++) {
      // Direct mole rescaling in the system retains round-off from subtracting its normalized inventory.
      double tolerance = Math.max(1.0e-18, Math.abs(expected[element]) * 2.0e-7);
      assertEquals(expected[element], actual[element], tolerance, formula.getElementNames()[element]);
    }
  }
}
