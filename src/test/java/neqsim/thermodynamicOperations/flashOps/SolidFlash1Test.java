package neqsim.thermodynamicOperations.flashOps;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

public class SolidFlash1Test {
  @Test
  void testPhaseCheck() {
    SystemInterface fluid1 = new SystemSrkEos();
    fluid1.addComponent("CO2", 1.0);
    fluid1.setPressure(15.448979591836736);
    fluid1.setTemperature(273.15 + 46.734693877551024);
    fluid1.setSolidPhaseCheck("CO2");
    fluid1.setMultiPhaseCheck(true);
    ThermodynamicOperations flashOps = new ThermodynamicOperations(fluid1);
    flashOps.TPSolidflash();
    assertEquals("gas",fluid1.getPhase(0).getType().getDesc());
  }
}
