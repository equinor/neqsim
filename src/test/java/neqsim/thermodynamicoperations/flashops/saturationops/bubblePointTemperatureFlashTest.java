package neqsim.thermodynamicoperations.flashops.saturationops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

public class bubblePointTemperatureFlashTest {
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
      e.printStackTrace();
    }
    assertEquals(-117.7205968083, fluid0_HC.getTemperature("C"), 1e-2);
  }
}
