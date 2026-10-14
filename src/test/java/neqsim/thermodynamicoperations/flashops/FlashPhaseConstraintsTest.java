package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemElectrolyteCPAstatoil;
import neqsim.thermo.system.SystemInterface;

/**
 * Participation and phase-fraction equations for full ionic inventories.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class FlashPhaseConstraintsTest {
  /** A captured aqueous role survives density-label changes and leaves every molecular species free to partition. */
  @Test
  void capturedRoleIsIndependentOfTrialClassification() {
    SystemInterface fluid = brine();
    FlashPhaseConstraints constraints = FlashPhaseConstraints.capture(fluid);
    assertTrue(constraints.supportsTwoPhaseSplit());
    fluid.getPhase(1).setType(PhaseType.OIL);
    for (int component = 0; component < fluid.getNumberOfComponents(); component++) {
      boolean ion = FlashPhaseConstraints.isIon(fluid.getPhase(0).getComponent(component));
      assertEquals(!ion, constraints.isAllowed(0, component));
      assertTrue(constraints.isAllowed(1, component));
    }
    assertEquals(1.0e-40, constraints.constrainedK(2), 0.0);
    assertTrue(Double.isNaN(constraints.constrainedK(0)));
  }

  /** A dense requested gas root must not override the unique liquid role of the homogeneous aqueous feed. */
  @Test
  void homogeneousFeedUsesItsConfiguredLiquidRole() {
    SystemInterface fluid = brine();
    fluid.setPhaseType(1, PhaseType.LIQUID);
    fluid.getPhase(0).setType(PhaseType.AQUEOUS);
    fluid.getPhase(1).setType(PhaseType.AQUEOUS);
    FlashPhaseConstraints constraints = FlashPhaseConstraints.capture(fluid);
    assertTrue(constraints.supportsTwoPhaseSplit());
    assertEquals(1, constraints.getAqueousPhase());
  }

  /** A gas-rich homogeneous feed establishes its ionic host from its unique liquid root before partitioning. */
  @Test
  void gasRichFeedUsesUniqueLiquidRoleBeforeAqueousClassification() {
    SystemInterface fluid = brine();
    fluid.setPhaseType(1, PhaseType.LIQUID);
    fluid.getPhase(0).setType(PhaseType.GAS);
    fluid.getPhase(1).setType(PhaseType.OIL);
    FlashPhaseConstraints constraints = FlashPhaseConstraints.capture(fluid);
    assertTrue(constraints.supportsTwoPhaseSplit());
    assertEquals(1, constraints.getAqueousPhase());
    fluid.setPhaseType(0, PhaseType.LIQUID);
    assertFalse(FlashPhaseConstraints.capture(fluid).supportsTwoPhaseSplit());
  }

  /** Ambiguous aqueous topologies cannot silently impose a physically arbitrary ion host. */
  @Test
  void ambiguousAqueousRoleIsNotSupported() {
    SystemInterface fluid = brine();
    fluid.setPhaseType(0, PhaseType.AQUEOUS);
    assertFalse(FlashPhaseConstraints.capture(fluid).supportsTwoPhaseSplit());
  }

  /**
   * Masked beta equations retain ions exactly without dividing by vanishing ionic fugacity coefficients.
   */
  @Test
  void fullInventoryBetaEquationsRemainFiniteAndConservative() {
    SystemInterface fluid = brine();
    double[] amounts = {0.099 / 1.136, 0.0001 / 1.136, 0.0, 0.0};
    double gasBeta = amounts[0] + amounts[1];
    fluid.setBeta(0, gasBeta);
    fluid.setBeta(1, 1.0 - gasBeta);
    fluid.init(1);
    double[][] original = new double[2][fluid.getNumberOfComponents()];
    for (int component = 0; component < fluid.getNumberOfComponents(); component++) {
      double feed = fluid.getPhase(0).getComponent(component).getz();
      original[0][component] = amounts[component] / gasBeta;
      original[1][component] = (feed - amounts[component]) / (1.0 - gasBeta);
      for (int phase = 0; phase < 2; phase++) {
        fluid.getPhase(phase).getComponent(component).setx(original[phase][component]);
        fluid.getPhase(phase).getComponent(component)
            .setFugacityCoefficient(component < 2 ? 1.0 / original[phase][component] : 0.0);
      }
    }
    TPmultiflash operation = new TPmultiflash(fluid, false);
    operation.setDoubleArrays();
    operation.calcQ();
    for (int phase = 0; phase < 2; phase++) {
      assertEquals(0.0, operation.dQdbeta[phase][0], 1.0e-13);
      for (int other = 0; other < 2; other++) {
        assertTrue(Double.isFinite(operation.Qmatrix[phase][other]));
      }
    }
    operation.calcE();
    assertEquals(1.0 - gasBeta, operation.Erow[2], 0.0);
    assertEquals(1.0 - gasBeta, operation.Erow[3], 0.0);
    operation.setXY();
    for (int component = 0; component < fluid.getNumberOfComponents(); component++) {
      double recovered = 0.0;
      for (int phase = 0; phase < 2; phase++) {
        assertEquals(original[phase][component], fluid.getPhase(phase).getComponent(component).getx(), 1.0e-13);
        recovered += fluid.getBeta(phase) * fluid.getPhase(phase).getComponent(component).getx();
      }
      assertEquals(fluid.getPhase(0).getComponent(component).getz(), recovered, 1.0e-13);
    }
  }

  /** Raw molecular weights are normalized before composition setters can truncate an oversized trial weight. */
  @Test
  void molecularTrialWeightsAreNormalizedBeforeStorage() {
    SystemInterface fluid = brine();
    fluid.setBeta(0, 0.01);
    fluid.setBeta(1, 0.99);
    fluid.init(1);
    for (int component = 0; component < fluid.getNumberOfComponents(); component++) {
      fluid.getPhase(0).getComponent(component).setFugacityCoefficient(component == 0 ? 1.0e-6 : 1.0);
      fluid.getPhase(1).getComponent(component).setFugacityCoefficient(1.0);
    }
    TPmultiflash operation = new TPmultiflash(fluid, false);
    operation.setDoubleArrays();
    operation.calcE();
    double rawNitrogen = fluid.getPhase(0).getComponent(0).getz() / operation.Erow[0] / 1.0e-6;
    assertTrue(rawNitrogen > 5.0);
    operation.setXY();
    for (int phase = 0; phase < 2; phase++) {
      double sum = 0.0;
      for (int component = 0; component < fluid.getNumberOfComponents(); component++) {
        sum += fluid.getPhase(phase).getComponent(component).getx();
      }
      assertEquals(1.0, sum, 1.0e-14);
      for (int component = 2; component < fluid.getNumberOfComponents(); component++) {
        assertEquals(phase == 0 ? 0.0 : fluid.getPhase(phase).getComponent(component).getz() / fluid.getBeta(phase),
            fluid.getPhase(phase).getComponent(component).getx(), 1.0e-14);
      }
    }
  }

  /** A representable extreme molecular fugacity cannot create an overflow-times-underflow beta Hessian. */
  @Test
  void extremeNeutralFugacityKeepsBetaHessianFinite() {
    SystemInterface fluid = brine();
    fluid.setBeta(0, 0.1);
    fluid.setBeta(1, 0.9);
    fluid.init(1);
    for (int component = 0; component < fluid.getNumberOfComponents(); component++) {
      fluid.getPhase(0).getComponent(component).setFugacityCoefficient(1.0);
      fluid.getPhase(1).getComponent(component).setFugacityCoefficient(component == 0 ? 1.0e-200 : 1.0);
    }
    TPmultiflash operation = new TPmultiflash(fluid, false);
    operation.setDoubleArrays();
    operation.calcQ();
    for (int phase = 0; phase < 2; phase++) {
      assertTrue(Double.isFinite(operation.dQdbeta[phase][0]));
      for (int other = 0; other < 2; other++) {
        assertTrue(Double.isFinite(operation.Qmatrix[phase][other]));
      }
    }
    double expected = fluid.getPhase(0).getComponent(1).getz();
    for (int component = 0; component < fluid.getNumberOfComponents(); component++) {
      if (component != 1) {
        expected += fluid.getPhase(0).getComponent(component).getz() / (0.9 * 0.9);
      }
    }
    assertEquals(expected + 1.0e-3, operation.Qmatrix[1][1], 1.0e-13);
  }

  /**
   * Creates the model and declares the fluid topology before any constrained iteration.
   *
   * @return initialized two-phase nitrogen/brine system
   */
  private SystemInterface brine() {
    SystemInterface fluid = new SystemElectrolyteCPAstatoil(298.15, 50.0);
    fluid.addComponent("nitrogen", 0.1);
    fluid.addComponent("water", 1.0);
    fluid.addComponent("Na+", 0.018);
    fluid.addComponent("Cl-", 0.018);
    fluid.setMixingRule(10);
    fluid.init(0);
    fluid.setNumberOfPhases(2);
    fluid.setPhaseType(0, PhaseType.GAS);
    fluid.setPhaseType(1, PhaseType.AQUEOUS);
    fluid.init(1);
    return fluid;
  }
}
