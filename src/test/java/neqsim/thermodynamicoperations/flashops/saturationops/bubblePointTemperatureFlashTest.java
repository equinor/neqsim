package neqsim.thermodynamicoperations.flashops.saturationops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

public class bubblePointTemperatureFlashTest {
  private static final Logger logger = LogManager.getLogger(bubblePointTemperatureFlashTest.class);

  /** Logger object for class. */

  @Test
  void testRun() {
    SystemSrkEos fluid0_HC = new SystemSrkEos();
    fluid0_HC.addComponent("methane", 0.7);
    fluid0_HC.addComponent("ethane", 0.1);
    fluid0_HC.addComponent("propane", 0.1);
    fluid0_HC.addComponent("n-butane", 0.1);
    fluid0_HC.setMixingRule("classic");

    fluid0_HC.setPressure(10.0, "bara");
    fluid0_HC.setTemperature(-150.0, "C");

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid0_HC);
    try {
      ops.bubblePointTemperatureFlash();
    } catch (Exception e) {
      logger.error(e.getMessage());
    }
    assertEquals(-117.7205968083, fluid0_HC.getTemperature("C"), 1e-2);
  }

  @Test
  void testPureHydrogenBubblePoint() throws Exception {
    SystemSrkEos hydrogen = new SystemSrkEos(20.0, 1.01325);
    hydrogen.addComponent("hydrogen", 1.0);
    hydrogen.setMixingRule("classic");

    ThermodynamicOperations operations = new ThermodynamicOperations(hydrogen);
    operations.bubblePointTemperatureFlash();

    assertEquals(20.5414083, hydrogen.getTemperature(), 1e-6);
    assertEquals(20.369, hydrogen.getTemperature(), 0.5);
    assertEquals(13.8033, hydrogen.getPhase(0).getComponent("hydrogen").getTriplePointTemperature(), 1e-4);
    assertEquals(0.07041, hydrogen.getPhase(0).getComponent("hydrogen").getTriplePointPressure(), 1e-5);
  }

  @Test
  void testPureComponentBubblePointRegressions() throws Exception {
    assertPureComponentBubblePoint("methane", 110.0, 112.0058529);
    assertPureComponentBubblePoint("nitrogen", 77.0, 77.4963520);
    assertPureComponentBubblePoint("propane", 230.0, 231.2682138);
  }

  private void assertPureComponentBubblePoint(String componentName, double initialTemperature,
      double expectedTemperature) throws Exception {
    SystemSrkEos fluid = new SystemSrkEos(initialTemperature, 1.01325);
    fluid.addComponent(componentName, 1.0);
    fluid.setMixingRule("classic");

    ThermodynamicOperations operations = new ThermodynamicOperations(fluid);
    operations.bubblePointTemperatureFlash();

    assertEquals(expectedTemperature, fluid.getTemperature(), 1e-6);
  }

}
