package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import neqsim.thermo.system.SystemElectrolyteCPAstatoil;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.thermodynamicoperations.flashops.reactiveflash.FormulaMatrix;

/**
 * Coupled reaction and phase equilibrium under a gas-buffered carbonate load.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
class AqueousReactionExtentTest extends neqsim.NeqSimTest {
  /**
   * Gas-phase carbon must participate through phase equilibrium while the full reaction inventory stays conservative.
   *
   * @param pressure pressure in bar absolute
   * @param scale multiplier for every feed amount
   */
  @ParameterizedTest
  @CsvSource({"1.01325,0.01", "1.01325,1.0", "1.01325,100.0", "50.0,0.01", "50.0,1.0", "50.0,100.0"})
  void gasBufferedCarbonateConvergesWithinOriginalCouplingBound(double pressure, double scale) {
    SystemInterface fluid = carbonateFeed(pressure);
    double[] scaled = FluidEquilibriumChecks.inventory(fluid);
    for (int component = 0; component < scaled.length; component++) {
      scaled[component] *= scale;
    }
    fluid.setMolarFlowRates(scaled);
    new ThermodynamicOperations(fluid).TPflash();
    fluid.addComponent("Ca++", 0.008192 * scale);
    fluid.addComponent("CO3--", 0.008192 * scale);
    FormulaMatrix formula = new FormulaMatrix(fluid);
    double[] inventory = FluidEquilibriumChecks.inventory(fluid);
    double[] originalElements = formula.computeElementVector(inventory);
    double methane = fluid.getComponent("methane").getNumberOfmoles();
    double calcium = fluid.getComponent("Ca++").getNumberOfmoles();
    double chloride = fluid.getComponent("Cl-").getNumberOfmoles();
    double sodium = fluid.getComponent("Na+").getNumberOfmoles();

    AqueousReactionEquilibrium flash = new AqueousReactionEquilibrium(fluid);
    flash.run();
    if (pressure == 1.01325 && scale == 1.0) {
      assertTrue(flash.getReactionNewtonStepCount() > 0, "the slow gas-buffered case must use coupled correction");
    }
    assertTrue(flash.getIterations() < 30, "the original bounded iteration count must suffice");
    assertTrue(fluid.hasPhaseType("gas"));
    assertTrue(fluid.hasPhaseType("aqueous"));
    assertTrue(fluid.getChemicalReactionOperations().getMaximumAbsoluteReactionLogResidual() < 2.0e-6);
    assertEquals(0.0, fluid.getChemicalReactionOperations().getReactivePhaseChargeMoles() / scale, 1.0e-10);
    FluidEquilibriumChecks.validate(fluid, FluidEquilibriumChecks.inventory(fluid));
    double[] finalElements = formula.computeElementVector(FluidEquilibriumChecks.inventory(fluid));
    for (int element = 0; element < originalElements.length; element++) {
      assertEquals(originalElements[element], finalElements[element], 1.0e-9 * scale,
          "original amount of " + formula.getElementNames()[element]);
    }
    assertEquals(methane, fluid.getComponent("methane").getNumberOfmoles(), 1.0e-11 * scale);
    assertEquals(calcium, fluid.getComponent("Ca++").getNumberOfmoles(), 1.0e-11 * scale);
    assertEquals(chloride, fluid.getComponent("Cl-").getNumberOfmoles(), 1.0e-11 * scale);
    assertEquals(sodium, fluid.getComponent("Na+").getNumberOfmoles(), 1.0e-11 * scale);

    double[] equilibrated = FluidEquilibriumChecks.inventory(fluid);
    flash.run();
    assertEquals(1, flash.getIterations(), "the accepted coupled equilibrium must be a fixed point");
    assertEquals(0, flash.getReactionNewtonStepCount());
    double[] repeated = FluidEquilibriumChecks.inventory(fluid);
    for (int component = 0; component < equilibrated.length; component++) {
      assertEquals(equilibrated[component], repeated[component], 1.0e-10 * scale);
    }
  }

  /**
   * Builds the carbonate feed used by the public salt-saturation calculation.
   *
   * @param pressure pressure in bar absolute
   * @return reactive gas and aqueous feed
   */
  private static SystemInterface carbonateFeed(double pressure) {
    SystemInterface fluid = new SystemElectrolyteCPAstatoil(298.15, pressure);
    fluid.addComponent("methane", 5.0);
    fluid.addComponent("CO2", 0.01);
    fluid.addComponent("water", 55.5);
    fluid.addComponent("Ca++", 0.0001);
    fluid.addComponent("Cl-", 0.0002);
    fluid.addComponent("Na+", 0.001);
    fluid.addComponent("OH-", 0.001);
    fluid.chemicalReactionInit();
    fluid.createDatabase(true);
    fluid.setMixingRule(10);
    fluid.setMultiPhaseCheck(true);
    return fluid;
  }
}
