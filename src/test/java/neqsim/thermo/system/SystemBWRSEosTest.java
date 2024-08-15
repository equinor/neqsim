package neqsim.thermo.system;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class SystemBWRSEosTest {

  @Test
  void testDensityBWRS() {
    SystemInterface fluid1 = new neqsim.thermo.system.SystemBWRSEos(288.15, 1.01325);
    fluid1.addComponent("methane", 1.0);

    fluid1.init(0);
    fluid1.init(3);

    Assertions.assertEquals(0.6798723916732, fluid1.getDensity("kg/m3"), 1e-3);

    fluid1.setPressure(10.0, "bara");
    fluid1.init(3);

    Assertions.assertEquals(6.8301289199844, fluid1.getPhase(0).getDensity(), 1e-3);

    fluid1.setTemperature(50.0, "C");
    fluid1.init(3);
    Assertions.assertEquals(6.047657075619, fluid1.getPhase(0).getDensity(), 1e-3);

    Assertions.assertEquals(0.6798723916732, fluid1.getDensity("kg/m3"), 1e-3);

    fluid1.setPressure(100.0, "bara");
    fluid1.init(3);

    Assertions.assertEquals(66.598898134, fluid1.getPhase(0).getDensity(), 1e-3);
  }
}


