package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    if (temperature == 313.15) {
      assertTrue(flash.getRejectedAcceleratedTrialCount() > 0,
          "this case must exercise rollback of a worsening extrapolation before accepting equilibrium");
    }
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
}
