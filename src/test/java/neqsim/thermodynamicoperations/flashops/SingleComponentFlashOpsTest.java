package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

class SingleComponentFlashOpsTest {
  @Test
  void testPHflashSingleComponent() {
    SystemPrEos system = new SystemPrEos(273.15, 20.0);
    system.addComponent("methane", 1.0);
    system.init(0);
    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    ops.TPflash();
    system.initProperties();
    double gasH = system.getPhase(0).getEnthalpy()
        / system.getPhase(0).getNumberOfMolesInPhase() * system.getTotalNumberOfMoles();
    double liqH = system.getPhase(1).getEnthalpy()
        / system.getPhase(1).getNumberOfMolesInPhase() * system.getTotalNumberOfMoles();
    double hSpec = (gasH + liqH) / 2.0;
    ops.PHflash(hSpec);
    assertEquals(hSpec, system.getEnthalpy(), 1e-4);
  }

  @Test
  void testVUflashSingleComponent() {
    SystemPrEos system = new SystemPrEos(273.15, 20.0);
    system.addComponent("methane", 1.0);
    system.init(0);
    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    ops.TPflash();
    try {
      ops.bubblePointTemperatureFlash();
    } catch (Exception e) {
      try {
        ops.dewPointTemperatureFlash();
      } catch (Exception ex) {
        // ignore
      }
    }
    system.init(3);
    double gasU = system.getPhase(0).getInternalEnergy()
        / system.getPhase(0).getNumberOfMolesInPhase() * system.getTotalNumberOfMoles();
    double liqU = system.getPhase(1).getInternalEnergy()
        / system.getPhase(1).getNumberOfMolesInPhase() * system.getTotalNumberOfMoles();
    double gasV = system.getPhase(0).getVolume("m3")
        / system.getPhase(0).getNumberOfMolesInPhase() * system.getTotalNumberOfMoles();
    double liqV = system.getPhase(1).getVolume("m3")
        / system.getPhase(1).getNumberOfMolesInPhase() * system.getTotalNumberOfMoles();
    double beta = 0.5;
    double Uspec = liqU + beta * (gasU - liqU);
    double Vspec = liqV + beta * (gasV - liqV);
    ops.VUflash(Vspec, Uspec, "m3", "J");
    assertEquals(beta, system.getBeta(), 1e-6);
    assertEquals(Vspec, system.getVolume("m3"), 1e-4);
  }
}
