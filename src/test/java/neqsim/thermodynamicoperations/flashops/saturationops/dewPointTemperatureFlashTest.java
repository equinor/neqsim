package neqsim.thermodynamicoperations.flashops.saturationops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

public class dewPointTemperatureFlashTest {
  private static final Logger logger = LogManager.getLogger(dewPointTemperatureFlashTest.class);

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
    fluid0_HC.setTemperature(0.0, "C");

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid0_HC);
    try {
      ops.dewPointTemperatureFlash();
    } catch (Exception e) {
      logger.error(e.getMessage());
    }
    assertEquals(1.7007677589821242, fluid0_HC.getTemperature("C"), 1e-2);
  }

  /**
   * A component present with a zero mole fraction must not change the dew point. Water used to be special-cased on
   * presence alone, which seeded the incipient liquid as pure water and stalled the flash on the initial temperature
   * guess even when the fluid carried no water at all.
   */
  @Test
  void testZeroWaterGivesSameDewPointAsNoWater() {
    SystemSrkEos withoutWater = new SystemSrkEos();
    withoutWater.addComponent("methane", 0.7);
    withoutWater.addComponent("ethane", 0.1);
    withoutWater.addComponent("propane", 0.1);
    withoutWater.addComponent("n-butane", 0.1);
    withoutWater.setMixingRule("classic");
    withoutWater.setPressure(10.0, "bara");
    withoutWater.setTemperature(0.0, "C");

    SystemSrkEos withZeroWater = new SystemSrkEos();
    withZeroWater.addComponent("methane", 0.7);
    withZeroWater.addComponent("ethane", 0.1);
    withZeroWater.addComponent("propane", 0.1);
    withZeroWater.addComponent("n-butane", 0.1);
    withZeroWater.addComponent("water", 0.1);
    withZeroWater.setMixingRule("classic");
    withZeroWater.setMolarComposition(new double[] { 0.7, 0.1, 0.1, 0.1, 0.0 });
    withZeroWater.setPressure(10.0, "bara");
    withZeroWater.setTemperature(0.0, "C");

    ThermodynamicOperations opsWithout = new ThermodynamicOperations(withoutWater);
    ThermodynamicOperations opsZeroWater = new ThermodynamicOperations(withZeroWater);
    try {
      opsWithout.dewPointTemperatureFlash();
      opsZeroWater.dewPointTemperatureFlash();
    } catch (Exception e) {
      logger.error(e.getMessage());
    }

    assertEquals(withoutWater.getTemperature("C"), withZeroWater.getTemperature("C"), 1e-4);
    // Guard against both flashes silently returning the 0 C starting guess.
    assertEquals(1.7007677589821242, withZeroWater.getTemperature("C"), 1e-2);
  }
}
