package neqsim.process.equipment.heatexchanger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import neqsim.physicalproperties.system.PhysicalPropertyModel;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermo.util.steam.Iapws_if97;

public class SteamHeaterTest {
  @Test
  void testSteamHeater() {
    SystemInterface water = new SystemSrkEos(298.15, 1.0);
    water.addComponent("water", 1.0);
    water.setMixingRule(2);
    water.setPhysicalPropertyModel(PhysicalPropertyModel.WATER);

    Stream feed = new Stream("water feed", water);
    feed.setTemperature(25.0, "C");
    feed.setPressure(1.0, "bara");
    feed.setFlowRate(10.0, "kg/hr");

    SteamHeater heater = new SteamHeater("heater", feed);
    heater.setOutTemperature(80.0, "C");
    heater.setSteamInletTemperature(180.0, "C");
    heater.setSteamOutletTemperature(100.0, "C");
    heater.setSteamPressure(2.0, "bara");

    ProcessSystem ps = new ProcessSystem();
    ps.add(feed);
    ps.add(heater);
    ps.run();

    assertEquals(80.0, heater.getOutletStream().getTemperature("C"), 1e-3);

    assertEquals(
        PhysicalPropertyModel.WATER,
        heater.getOutletStream().getThermoSystem().getPhase(0).getPhysicalPropertyModel());

    double hin = Iapws_if97.h_pt(0.2, 453.15); // 2 bara, 180C
    double hout = Iapws_if97.h_pt(0.2, 373.15); // 2 bara, 100C
    double expectedFlow = heater.getDuty() / ((hin - hout) * 1000.0);
    assertEquals(expectedFlow, heater.getSteamFlowRate("kg/sec"), 1e-6);

  }
}
