package neqsim.thermo.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import org.junit.jupiter.api.Test;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Verifies independent derivative storage for cloned components and liquid phases.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
class ComponentCloneDerivativeIsolationTest {
  /** Checks all mutable EOS composition-derivative work arrays. */
  @Test
  void cloneOwnsItsDerivativeWorkArrays() {
    ComponentEos source = (ComponentEos) sourFluid().getComponent(0);
    double[][] original = {source.dfugdn, source.dfugdx, source.Aij, source.Bij, source.dAdndn, source.dBdndn};
    for (double[] values : original) {
      values[0] = 7.0;
    }
    ComponentEos clone = source.clone();
    double[][] copied = {clone.dfugdn, clone.dfugdx, clone.Aij, clone.Bij, clone.dAdndn, clone.dBdndn};
    for (int array = 0; array < original.length; array++) {
      assertNotSame(original[array], copied[array]);
      assertEquals(7.0, copied[array][0], 0.0);
      copied[array][0] = 9.0;
      assertEquals(7.0, original[array][0], 0.0);
    }
  }

  /** Checks the analytical two-liquid Hessian against central differences at fixed configured roots. */
  @Test
  void clonedLiquidPhaseHessianMatchesIndependentCompositionDifferences() {
    SystemInterface fluid = sourFluid();
    new ThermodynamicOperations(fluid).TPflash();
    assertEquals(2, fluid.getNumberOfPhases());
    fluid.init(3);
    assertNotSame(((Component) fluid.getPhase(0).getComponent(0)).dfugdx,
        ((Component) fluid.getPhase(1).getComponent(0)).dfugdx);
    PhaseType[] roots = {fluid.getPhaseType(0), fluid.getPhaseType(1)};
    double beta = fluid.getBeta(0);
    double[] amounts = new double[3];
    double[][] jacobian = new double[3][3];
    for (int row = 0; row < 3; row++) {
      ComponentInterface first = fluid.getPhase(0).getComponent(row);
      ComponentInterface second = fluid.getPhase(1).getComponent(row);
      amounts[row] = beta * first.getx();
      for (int column = 0; column < 3; column++) {
        double diagonal = row == column ? 1.0 : 0.0;
        jacobian[row][column] = (diagonal / first.getx() - 1.0 + first.getdfugdx(column)) / beta
            + (diagonal / second.getx() - 1.0 + second.getdfugdx(column)) / (1.0 - beta);
      }
    }
    double step = 1.0e-6;
    for (int column = 0; column < 3; column++) {
      double original = amounts[column];
      amounts[column] = original + step;
      double[] plus = residual(fluid, amounts);
      amounts[column] = original - step;
      double[] minus = residual(fluid, amounts);
      amounts[column] = original;
      for (int row = 0; row < 3; row++) {
        assertEquals((plus[row] - minus[row]) / (2.0 * step), jacobian[row][column], 2.0e-7,
            "Hessian entry " + row + "," + column);
      }
      assertEquals(roots[0], fluid.getPhaseType(0));
      assertEquals(roots[1], fluid.getPhaseType(1));
    }
  }

  /**
   * Evaluates equilibrium residuals directly from a balanced phase-mole split.
   *
   * @param fluid fluid with fixed physical slots and configured roots
   * @param amounts first-phase component amounts divided by total feed moles
   * @return component log-fugacity differences
   */
  private double[] residual(SystemInterface fluid, double[] amounts) {
    double beta = 0.0;
    for (double amount : amounts) {
      beta += amount;
    }
    fluid.setBeta(0, beta);
    fluid.setBeta(1, 1.0 - beta);
    for (int component = 0; component < amounts.length; component++) {
      fluid.getPhase(0).getComponent(component).setx(amounts[component] / beta);
      fluid.getPhase(1).getComponent(component)
          .setx((fluid.getComponent(component).getz() - amounts[component]) / (1.0 - beta));
    }
    fluid.init(1);
    double[] result = new double[amounts.length];
    for (int component = 0; component < amounts.length; component++) {
      result[component] = Math.log(fluid.getPhase(0).getComponent(component).getx())
          + fluid.getPhase(0).getComponent(component).getLogFugacityCoefficient()
          - Math.log(fluid.getPhase(1).getComponent(component).getx())
          - fluid.getPhase(1).getComponent(component).getLogFugacityCoefficient();
    }
    return result;
  }

  /**
   * Creates the public PR/classic liquid-liquid boundary case.
   *
   * @return unflashed methane/carbon-dioxide/hydrogen-sulfide fluid
   */
  private SystemInterface sourFluid() {
    SystemInterface fluid = new SystemPrEos(225.0, 95.81);
    fluid.addComponent("methane", 49.88 / 99.97);
    fluid.addComponent("CO2", 9.87 / 99.97);
    fluid.addComponent("H2S", 40.22 / 99.97);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);
    return fluid;
  }
}
