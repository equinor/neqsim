package neqsim.chemicalreactions;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import neqsim.chemicalreactions.chemicalequilibrium.LinearProgrammingChemicalEquilibrium;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemElectrolyteCPAstatoil;
import neqsim.thermo.system.SystemInterface;

/**
 * A failed or absent LP initializer must not reuse an earlier phase's mole estimate.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class ChemicalInitialEstimateLifecycleTest {
  /** A missing initializer must use Newton from the current inventory, without applying a stale LP vector. */
  @Test
  void missingInitializerDoesNotApplyAnEarlierEstimate() {
    ChemicalReactionOperations operations = createOperations();
    operations.initCalc = null;
    operations.newMoles = new double[operations.components.length];
    Arrays.fill(operations.newMoles, 0.25);

    operations.solveChemEq(operations.phase, 0);

    double[] currentInventory = phaseMoles(operations);
    operations.updateMoles(operations.phase);
    assertArrayEquals(currentInventory, phaseMoles(operations), 0.0,
        "An absent LP estimate must not overwrite the Newton fallback state");
    assertNull(operations.newMoles, "An earlier estimate must not survive a missing initializer");
    assertNotNull(operations.solver, "The Newton fallback must still be attempted");
  }

  /** An exception before the LP produces a vector must invalidate the earlier estimate as well. */
  @Test
  void throwingInitializerInvalidatesAnEarlierEstimate() {
    ChemicalReactionOperations operations = createOperations();
    operations.newMoles = new double[operations.components.length];
    Arrays.fill(operations.newMoles, 0.25);
    operations.initCalc = new LinearProgrammingChemicalEquilibrium(operations.chemRefPot, operations.components,
        operations.elements, operations, operations.phase) {
      /** {@inheritDoc} */
      @Override
      public double[] generateInitialEstimates(SystemInterface system, double[] bVector, double inertMoles,
          int phaseNum) {
        throw new IllegalStateException("Injected LP initialization failure");
      }
    };

    operations.solveChemEq(operations.phase, 0);

    double[] currentInventory = phaseMoles(operations);
    operations.updateMoles(operations.phase);
    assertArrayEquals(currentInventory, phaseMoles(operations), 0.0,
        "A failed LP attempt must not leave an applicable stale vector");
    assertNull(operations.newMoles, "A failed initializer must not leave an earlier estimate available");
    assertNotNull(operations.solver, "The existing Newton fallback must remain reachable");
  }

  /**
   * Build a small non-equilibrium aqueous CO2 system with the production reaction inventory.
   *
   * @return the system-owned production reaction operations
   */
  private ChemicalReactionOperations createOperations() {
    SystemInterface fluid = new SystemElectrolyteCPAstatoil(298.15, 1.01325);
    fluid.addComponent("CO2", 0.001);
    fluid.addComponent("water", 1.0);
    fluid.chemicalReactionInit();
    fluid.createDatabase(true);
    fluid.setMixingRule(10);
    fluid.init(0);
    ChemicalReactionOperations operations = fluid.getChemicalReactionOperations();
    fluid.getPhase(operations.phase).setType(PhaseType.AQUEOUS);
    fluid.init(1);
    operations.setReactiveComponents();
    operations.Amatrix = operations.calcAmatrix();
    operations.nVector = operations.calcNVector();
    operations.bVector = operations.calcBVector();
    assertTrue(operations.getMaximumAbsoluteReactionLogResidual() > 1.0e-5,
        "The fixture must enter chemical-equilibrium initialization");
    operations.firsttime = true;
    return operations;
  }

  /**
   * Read the current phase inventory without reinitializing the EOS.
   *
   * @param operations the system-owned reaction operations
   * @return component mole amounts in the selected aqueous phase
   */
  private double[] phaseMoles(ChemicalReactionOperations operations) {
    int count = operations.system.getPhase(operations.phase).getNumberOfComponents();
    double[] moles = new double[count];
    for (int i = 0; i < count; i++) {
      moles[i] = operations.system.getPhase(operations.phase).getComponent(i).getNumberOfMolesInPhase();
    }
    return moles;
  }
}
