package neqsim.process.equipment.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Regression tests for physically bounded Beggs-Brill liquid holdup.
 */
class PipeBeggsAndBrillsHoldupTest {
  @Test
  void liquidRichLowVelocityHoldupIsBounded() {
    SystemInterface fluid = new SystemPrEos(308.15, 80.0);
    fluid.addComponent("nitrogen", 0.5);
    fluid.addComponent("CO2", 1.0);
    fluid.addComponent("methane", 45.0);
    fluid.addComponent("ethane", 5.0);
    fluid.addComponent("propane", 5.0);
    fluid.addComponent("i-butane", 2.0);
    fluid.addComponent("n-butane", 3.0);
    fluid.addComponent("i-pentane", 2.0);
    fluid.addComponent("n-pentane", 2.0);
    fluid.addComponent("n-hexane", 5.0);
    fluid.addComponent("n-heptane", 8.0);
    fluid.addComponent("n-octane", 10.0);
    fluid.addComponent("n-nonane", 6.0);
    fluid.addComponent("nC10", 5.5);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);
    fluid.setTotalFlowRate(10000.0, "kg/hr");
    new ThermodynamicOperations(fluid).TPflash();
    fluid.initProperties();

    assertTrue(fluid.getNumberOfPhases() > 1, "Regression fluid must be multiphase");

    Stream inlet = new Stream("liquid-rich inlet", fluid);
    inlet.run();
    PipeBeggsAndBrills pipe =
        new PipeBeggsAndBrills("holdup regression pipe", inlet);
    pipe.setLength(10000.0);
    pipe.setDiameter(0.25);
    pipe.setElevation(0.0);
    pipe.setPipeWallRoughness(5.0e-5);
    pipe.setNumberOfIncrements(5);
    pipe.setRunIsothermal(true);
    pipe.run();

    double maximumHoldup = 0.0;
    for (double holdup : pipe.getLiquidHoldupProfile()) {
      assertTrue(holdup >= 0.0, "Liquid holdup must not be negative");
      assertTrue(holdup <= 1.0, "Liquid holdup must not exceed unity");
      maximumHoldup = Math.max(maximumHoldup, holdup);
    }

    assertEquals(
        1.0,
        maximumHoldup,
        1.0e-12,
        "This liquid-rich regression case should exercise the upper bound");
  }
}
