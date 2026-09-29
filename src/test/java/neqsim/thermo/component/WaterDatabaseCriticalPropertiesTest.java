package neqsim.thermo.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemSrkEos;

/** Regression tests for source-backed pure-water constants in the component database. */
class WaterDatabaseCriticalPropertiesTest {
  @Test
  void waterCriticalPointMatchesIapwsReference() {
    SystemSrkEos system = new SystemSrkEos(300.0, 1.0);
    system.addComponent("water", 1.0);

    ComponentInterface water = system.getPhase(0).getComponent("water");
    assertEquals(647.096, water.getTC(), 1.0e-9);
    assertEquals(220.64, water.getPC(), 1.0e-9);
  }
}
