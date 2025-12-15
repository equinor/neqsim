package neqsim.process.equipment.pipeline.twophasepipe;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import neqsim.process.equipment.pipeline.TwoFluidPipe;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;

/**
 * Test for three-phase transient flow in a pipeline. Based on the
 * TransientPipelineLiquidAccumulationExample.
 */
class TransientThreePhaseFlowTest {

  @Test
  void testThreePhaseTransientStability() {
    // Create a rich gas condensate fluid with water (CPA for accurate water modeling)
    SystemInterface fluid = new SystemSrkCPAstatoil(333.15, 120.0); // 60°C, 120 bara

    // Gas components
    fluid.addComponent("nitrogen", 1.0);
    fluid.addComponent("CO2", 2.5);
    fluid.addComponent("methane", 65.0);
    fluid.addComponent("ethane", 8.0);
    fluid.addComponent("propane", 6.0);
    fluid.addComponent("i-butane", 2.0);
    fluid.addComponent("n-butane", 3.0);

    // Heavier components for more liquid
    fluid.addComponent("i-pentane", 2.5);
    fluid.addComponent("n-pentane", 3.0);
    fluid.addComponent("n-hexane", 2.5);
    fluid.addComponent("n-heptane", 2.0);
    fluid.addComponent("n-octane", 1.0);

    // Water
    fluid.addComponent("water", 1.5);

    // CPA mixing rule for water
    fluid.setMixingRule(10);
    fluid.setMultiPhaseCheck(true);

    // Create inlet stream
    Stream inlet = new Stream("GasCondensateFeed", fluid);
    inlet.setFlowRate(50.0, "kg/sec");
    inlet.setTemperature(60.0, "C");
    inlet.setPressure(120.0, "bara");
    inlet.run();

    // Create a shorter pipe for faster testing (10 km instead of 80 km)
    double pipeLength = 10000.0; // 10 km
    double pipeDiameter = 0.5; // 500 mm
    int numberOfSections = 20;

    TwoFluidPipe pipe = new TwoFluidPipe("TestPipeline", inlet);
    pipe.setLength(pipeLength);
    pipe.setDiameter(pipeDiameter);
    pipe.setNumberOfSections(numberOfSections);
    pipe.setRoughness(4.5e-5);
    pipe.setOutletPressure(40.0, "bara");
    pipe.setThermodynamicUpdateInterval(10);

    // Run steady state
    pipe.run();

    double initialInventory = pipe.getLiquidInventory("m3");
    System.out.println("Three-phase test - Initial liquid inventory: " + initialInventory + " m3");

    // Should have some liquid in the pipe
    assertTrue(initialInventory > 0, "Should have liquid in pipe");

    // Run transient for 60 seconds
    double dt = 2.0; // 2 second steps
    int numSteps = 30;

    for (int i = 0; i < numSteps; i++) {
      pipe.runTransient(dt);
    }

    double finalInventory = pipe.getLiquidInventory("m3");
    System.out.println("Three-phase test - Final liquid inventory: " + finalInventory + " m3");

    // Should stabilize (not blow up or go to zero)
    assertTrue(finalInventory > 0, "Liquid inventory should remain positive");
    assertTrue(finalInventory < 1000, "Liquid inventory should not blow up");

    // Change ratio should be within reasonable bounds (factor of 5)
    double ratio = finalInventory / initialInventory;
    assertTrue(ratio > 0.2 && ratio < 5.0, "Inventory ratio should be reasonable. Initial: "
        + initialInventory + ", Final: " + finalInventory + ", Ratio: " + ratio);
  }
}
