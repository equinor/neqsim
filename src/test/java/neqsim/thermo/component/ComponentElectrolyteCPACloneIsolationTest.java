package neqsim.thermo.component;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemElectrolyteCPAstatoil;
import neqsim.thermo.system.SystemInterface;

/**
 * Checks ownership of electrolyte CPA association composition derivatives across components and phases.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
class ComponentElectrolyteCPACloneIsolationTest {
  /** Preserves initialized site values while preventing mutations through shared derivative rows. */
  @Test
  void componentClonePreservesCachedValuesAndOwnsDerivativeRows() {
    SystemInterface fluid = threePhaseFluid(false);
    fluid.init(3, 1);
    ComponentElectrolyteCPA source = (ComponentElectrolyteCPA) fluid.getPhase(1).getComponent("water");
    ComponentElectrolyteCPA copy = source.clone();
    assertTrue(source.getXsitedni().length > 0);
    assertArrayEquals(source.getXsite(), copy.getXsite(), 0.0);
    assertArrayEquals(source.getXsitedV(), copy.getXsitedV(), 0.0);
    for (int site = 0; site < source.getXsitedni().length; site++) {
      assertNotSame(source.getXsitedni()[site], copy.getXsitedni()[site]);
      assertArrayEquals(source.getXsitedni()[site], copy.getXsitedni()[site], 0.0);
    }
    double original = source.getXsitedni(0, 0);
    copy.setXsitedni(0, 0, original + 1.0);
    assertEquals(original, source.getXsitedni(0, 0), 0.0);
  }

  /**
   * Whole-system initialization must retain the fugacity derivatives obtained by initializing each phase independently.
   *
   * @param reverseLiquids whether the oil phase precedes the aqueous phase
   */
  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void wholeSystemDerivativesMatchIndependentPhasesInEitherOrder(boolean reverseLiquids) {
    SystemInterface fluid = threePhaseFluid(reverseLiquids);
    int count = fluid.getNumberOfComponents();
    double[][][] independent = new double[3][count][count];
    for (int phaseIndex = 0; phaseIndex < 3; phaseIndex++) {
      fluid.init(3, phaseIndex);
      for (int row = 0; row < count; row++) {
        for (int column = 0; column < count; column++) {
          independent[phaseIndex][row][column] = fluid.getPhase(phaseIndex).getComponent(row).getdfugdn(column);
        }
      }
    }
    fluid.init(3);
    for (int phaseIndex = 0; phaseIndex < 3; phaseIndex++) {
      PhaseInterface phase = fluid.getPhase(phaseIndex);
      double largestDerivative = 0.0;
      for (int row = 0; row < count; row++) {
        for (int column = 0; column < count; column++) {
          double actual = phase.getComponent(row).getdfugdn(column);
          double expected = independent[phaseIndex][row][column];
          double tolerance = 1e-10 * Math.max(1.0, Math.abs(expected));
          assertTrue(Double.isFinite(actual));
          assertEquals(expected, actual, tolerance,
              "Initialization order changed phase " + phaseIndex + " derivative [" + row + "][" + column + "]");
          assertEquals(phase.getComponent(column).getdfugdn(row), actual, tolerance,
              "Fugacity composition derivatives must be symmetric in phase " + phaseIndex);
          largestDerivative = Math.max(largestDerivative, Math.abs(actual));
        }
      }
      assertTrue(largestDerivative > 1e-6, "Fugacity derivatives must be initialized");
    }
  }

  /**
   * Builds a fixed gas/aqueous/oil state with distinct water association environments and conserved inventories.
   *
   * @param reverseLiquids whether to swap the two liquid phase slots
   * @return initialized three-phase fluid
   */
  private static SystemInterface threePhaseFluid(boolean reverseLiquids) {
    double[][] phaseMoles = {{0.08, 0.04, 0.00008, 0.0, 0.0}, {0.0006, 0.00004, 0.84, 0.015, 0.015},
        {0.002, 0.01, 0.000003, 0.0, 0.0}};
    String[] names = {"methane", "propane", "water", "Na+", "Cl-"};
    SystemInterface fluid = new SystemElectrolyteCPAstatoil(298.15, 50.0);
    double total = 0.0;
    for (int component = 0; component < names.length; component++) {
      double amount = 0.0;
      for (double[] phase : phaseMoles) {
        amount += phase[component];
      }
      fluid.addComponent(names[component], amount);
      total += amount;
    }
    fluid.setMixingRule(10);
    fluid.setMultiPhaseCheck(true);
    fluid.init(0);
    fluid.setNumberOfPhases(3);
    for (int phaseIndex = 0; phaseIndex < 3; phaseIndex++) {
      int source = reverseLiquids && phaseIndex > 0 ? 3 - phaseIndex : phaseIndex;
      fluid.setPhaseIndex(phaseIndex, phaseIndex);
      fluid.setPhaseType(phaseIndex, source == 0 ? PhaseType.GAS : source == 1 ? PhaseType.AQUEOUS : PhaseType.OIL);
      double amount = 0.0;
      for (double value : phaseMoles[source]) {
        amount += value;
      }
      fluid.setBeta(phaseIndex, amount / total);
      for (int component = 0; component < names.length; component++) {
        fluid.getPhase(phaseIndex).getComponent(component).setx(phaseMoles[source][component] / amount);
      }
    }
    fluid.init(3);
    return fluid;
  }
}
