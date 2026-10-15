package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import neqsim.thermo.system.SystemElectrolyteCPAstatoil;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.util.amines.AmineSystem;
import neqsim.thermodynamicoperations.flashops.reactiveflash.FormulaMatrix;

/**
 * Physical acceptance coverage for accelerated aqueous reaction and phase coupling.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
class AqueousReactionAccelerationTest extends neqsim.NeqSimTest {
  /**
   * A slowly converging reactive absorption split must meet the original physical tolerances within the coupling bound.
   *
   * @param temperature temperature in kelvin
   */
  @ParameterizedTest
  @ValueSource(doubles = {303.15, 313.15, 323.15})
  void amineAbsorptionConvergesConservatively(double temperature) {
    AmineSystem amine = new AmineSystem(AmineSystem.AmineType.MDEA, temperature, 2.0);
    amine.setAmineConcentration(0.50);
    amine.setCO2Loading(0.1);
    SystemInterface fluid = amine.createSystem();
    FormulaMatrix formula = new FormulaMatrix(fluid);
    double[] originalElements = formula.computeElementVector(FluidEquilibriumChecks.inventory(fluid));

    AqueousReactionEquilibrium flash = new AqueousReactionEquilibrium(fluid);
    flash.run();
    assertTrue(flash.getAcceleratedTrialCount() > 0, "the slow coupling must use acceleration");
    assertTrue(flash.getIterations() < 30, "acceleration must converge within the unchanged coupling bound");
    assertTrue(fluid.getChemicalReactionOperations().getMaximumAbsoluteReactionLogResidual() < 2.0e-6);
    FluidEquilibriumChecks.validate(fluid, FluidEquilibriumChecks.inventory(fluid));
    double[] finalElements = formula.computeElementVector(FluidEquilibriumChecks.inventory(fluid));
    for (int element = 0; element < originalElements.length; element++) {
      assertEquals(originalElements[element], finalElements[element], 1.0e-10,
          "original inventory of " + formula.getElementNames()[element]);
    }

    flash.run();
    assertEquals(1, flash.getIterations(), "an equilibrated fluid must not require new accelerated iterations");
    FluidEquilibriumChecks.validate(fluid, FluidEquilibriumChecks.inventory(fluid));
    assertTrue(fluid.getChemicalReactionOperations().getMaximumAbsoluteReactionLogResidual() < 2.0e-6);
  }

  /**
   * Rejection restores the conservative update exactly, independently of a particular converged trajectory.
   *
   * @param rejectedResidual deliberately worsened or nonfinite reaction residual
   * @throws ReflectiveOperationException if the internal safeguard cannot be exercised
   */
  @ParameterizedTest
  @ValueSource(doubles = {0.4, Double.NaN})
  void rejectedExtrapolationRestoresConservativeInventory(double rejectedResidual) throws ReflectiveOperationException {
    SystemInterface original = new SystemElectrolyteCPAstatoil(298.15, 1.0);
    original.addComponent("water", 10.0);
    original.addComponent("OH-", 1.0);
    original.addComponent("H3O+", 1.0);
    original.setMixingRule(10);
    SystemInterface work = original.clone();
    Class<?> type = Class.forName(AqueousReactionEquilibrium.class.getName() + "$InventoryAcceleration");
    Constructor<?> constructor = type.getDeclaredConstructor();
    constructor.setAccessible(true);
    Object acceleration = constructor.newInstance();
    Method next = type.getDeclaredMethod("nextInventory", SystemInterface.class, double[].class, double[].class,
        double.class);
    Method worsened = type.getDeclaredMethod("residualWorsened", double.class);
    Method restore = type.getDeclaredMethod("restoreUnaccelerated", SystemInterface.class);
    next.setAccessible(true);
    worsened.setAccessible(true);
    restore.setAccessible(true);
    double[] initial = {10.0, 1.0, 1.0};
    double[] first = {8.0, 2.0, 2.0};
    double[] conservative = {7.0, 2.5, 2.5};
    assertArrayEquals(first, (double[]) next.invoke(acceleration, work, initial, first, 0.6), 0.0);
    double[] extrapolated = (double[]) next.invoke(acceleration, work, first, conservative, 0.3);
    assertArrayEquals(new double[] {6.0, 3.0, 3.0}, extrapolated, 0.0,
        "the controlled contraction must create a genuine extrapolation");
    work.setMolarFlowRates(extrapolated);
    assertTrue((Boolean) worsened.invoke(acceleration, rejectedResidual));
    assertTrue((Boolean) restore.invoke(acceleration, work));
    assertArrayEquals(conservative, FluidEquilibriumChecks.inventory(work), 0.0);
    assertArrayEquals(initial, FluidEquilibriumChecks.inventory(original), 0.0,
        "an unaccepted work trial must not alter its caller");
    assertFalse((Boolean) restore.invoke(acceleration, work), "a rejected trial can only be restored once");
    assertFalse((Boolean) worsened.invoke(acceleration, rejectedResidual), "no rejected trial remains pending");
    double[] nextConservative = {6.5, 2.75, 2.75};
    assertArrayEquals(nextConservative, (double[]) next.invoke(acceleration, work, conservative, nextConservative, 0.2),
        0.0, "rollback must discard the rejected secant history");
    FormulaMatrix formula = new FormulaMatrix(original);
    assertArrayEquals(formula.computeElementVector(initial), formula.computeElementVector(conservative), 0.0,
        "the restored update retains every elemental and charge invariant");
  }

}
