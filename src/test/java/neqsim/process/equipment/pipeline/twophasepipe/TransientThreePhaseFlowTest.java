package neqsim.process.equipment.pipeline.twophasepipe;

import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import neqsim.process.equipment.pipeline.TwoFluidPipe;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;

/**
 * Test for three-phase transient flow in a pipeline. Based on the TransientPipelineLiquidAccumulationExample.
 */
@Tag("slow")
class TransientThreePhaseFlowTest {
  private static final Logger logger = LogManager.getLogger(TransientThreePhaseFlowTest.class);

  @Test
  @Timeout(value = 5, unit = TimeUnit.MINUTES)
  void testThreePhaseTransientStability() {
    // Create a rich gas condensate fluid with water (CPA for accurate water
    // modeling)
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

    // Keep the physical three-phase scenario, but use a compact numerical grid.
    // The previous 10 km / 20-section / 30-step setup took more than one hour in CI.
    double pipeLength = 1000.0;
    double pipeDiameter = 0.5;
    int numberOfSections = 5;

    TwoFluidPipe pipe = new TwoFluidPipe("TestPipeline", inlet);
    pipe.setLength(pipeLength);
    pipe.setDiameter(pipeDiameter);
    pipe.setNumberOfSections(numberOfSections);
    pipe.setRoughness(4.5e-5);
    pipe.setOutletPressure(100.0, "bara");
    pipe.setThermodynamicUpdateInterval(1);

    // Run steady state
    pipe.run();

    double initialInventory = pipe.getLiquidInventory("m3");
    logger.info("Three-phase test - Initial liquid inventory: {} m3", initialInventory);

    assertTrue(initialInventory > 0, "Should have liquid in pipe");

    // A few transient steps are sufficient to exercise accumulation and phase updates.
    double dt = 1.0;
    int numSteps = 3;

    for (int i = 0; i < numSteps; i++) {
      pipe.runTransient(dt);
    }

    double finalInventory = pipe.getLiquidInventory("m3");
    logger.info("Three-phase test - Final liquid inventory: {} m3", finalInventory);

    assertTrue(finalInventory > 0, "Liquid inventory should remain positive");
    assertTrue(finalInventory < 1000, "Liquid inventory should not blow up");

    double ratio = finalInventory / initialInventory;
    assertTrue(ratio > 0.2 && ratio < 5.0, "Inventory ratio should be reasonable. Initial: " + initialInventory
        + ", Final: " + finalInventory + ", Ratio: " + ratio);
  }
}
