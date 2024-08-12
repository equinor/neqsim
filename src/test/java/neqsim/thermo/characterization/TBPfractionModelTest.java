package neqsim.thermo.characterization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermo.system.SystemSrkEos;

public class TBPfractionModelTest {
  @Test
  void testTwuModel() {
    SystemInterface thermoSystem = new SystemSrkEos(298.0, 10.0);
    thermoSystem.getCharacterization().setTBPModel("Twu");
    thermoSystem.addTBPfraction("C7", 1.0, 110.0 / 1000.0, 0.73);
    assertEquals(536.173400, thermoSystem.getComponent(0).getTC(), 1e-3);
    assertEquals(26.52357312690, thermoSystem.getComponent(0).getPC(), 1e-3);
    assertEquals(0.56001213933, thermoSystem.getComponent(0).getAcentricFactor(), 1e-3);
    assertEquals(437.335493, thermoSystem.getComponent(0).getCriticalVolume(), 1e-3);
  }

  @Test
  void testLeeKeslerModel() {
    SystemInterface thermoSystem = new SystemSrkEos(298.0, 10.0);
    thermoSystem.getCharacterization().setTBPModel("Lee-Kesler");
    thermoSystem.addTBPfraction("C7", 1.0, 110.0 / 1000.0, 0.73);
    assertEquals(562.4229803010662, thermoSystem.getComponent(0).getTC(), 1e-3);
    assertEquals(28.322987349048354, thermoSystem.getComponent(0).getPC(), 1e-3);
    assertEquals(0.3509842412742902, thermoSystem.getComponent(0).getAcentricFactor(), 1e-3);
    assertEquals(427.99744457199, thermoSystem.getComponent(0).getCriticalVolume(), 1e-3);
  }

  @Test
  void testPedersenSRKModel() {
    SystemInterface thermoSystem = new SystemSrkEos(298.0, 10.0);
    thermoSystem.getCharacterization().setTBPModel("PedersenSRK");
    thermoSystem.addTBPfraction("C7", 1.0, 110.0 / 1000.0, 0.73);
    assertEquals(554.3185637098962, thermoSystem.getComponent(0).getTC(), 1e-3);
    assertEquals(26.007549082822628, thermoSystem.getComponent(0).getPC(), 1e-3);
    assertEquals(0.508241, thermoSystem.getComponent(0).getAcentricFactor(), 1e-3);
    assertEquals(384.6714299777243, thermoSystem.getComponent(0).getCriticalVolume(), 1e-3);
  }

  @Test
  void testPedersenPRModel() {
    SystemInterface thermoSystem = new SystemPrEos(298.0, 10.0);
    thermoSystem.getCharacterization().setTBPModel("PedersenPR");
    thermoSystem.addTBPfraction("C7", 1.0, 110.0 / 1000.0, 0.73);
    assertEquals(560.546, thermoSystem.getComponent(0).getTC(), 1e-3);
    assertEquals(25.838137535018557, thermoSystem.getComponent(0).getPC(), 1e-3);
    assertEquals(0.3838836222383, thermoSystem.getComponent(0).getAcentricFactor(), 1e-3);
    assertEquals(405.0890245138075, thermoSystem.getComponent(0).getCriticalVolume(), 1e-3);
  }


}
