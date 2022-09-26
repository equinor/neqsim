package neqsim.thermodynamicOperations.phaseEnvelopeOps.multicomponentEnvelopeOps;

import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

public class pTphaseEnvelopeHCwaterTest {
  static SystemInterface thermoSystem = null;

  @Test
  void testRun() {
    thermoSystem = new SystemSrkEos(298.0, 10.0);
    thermoSystem.addComponent("methane", 51.0);
    thermoSystem.addComponent("ethane", 1.0);
    thermoSystem.addComponent("propane", 1.0);
    thermoSystem.addTBPfraction("C6", 1.0, 90.0 / 1000.0, 0.7);
    thermoSystem.addTBPfraction("C7", 1.0, 110.0 / 1000.0, 0.73);
    thermoSystem.addTBPfraction("C8", 1.0, 120.0 / 1000.0, 0.76);
    thermoSystem.addTBPfraction("C9", 1.0, 140.0 / 1000.0, 0.79);
    thermoSystem.addTBPfraction("C10", 11.0, 290.0 / 1000.0, 0.82);

    // at the moment water is not handeled in the routine......
    // thermoSystem.addComponent("water", 10.0);

    ThermodynamicOperations testOps = new ThermodynamicOperations(thermoSystem);
    testOps.calcPTphaseEnvelopeHCwater();


  }
}
