package neqsim.process.equipment.heatexchanger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import neqsim.physicalproperties.system.PhysicalPropertyModel;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermo.util.steam.Iapws_if97;

public class WaterCoolerTest {
  @Test
  void testWaterCooler() {
    SystemInterface water = new SystemSrkEos(298.15, 1.0);
    water.addComponent("water", 1.0);
    water.setMixingRule(2);
    water.setPhysicalPropertyModel(PhysicalPropertyModel.WATER);

    Stream feed = new Stream("water feed", water);
    feed.setTemperature(40.0, "C");
    feed.setPressure(1.0, "bara");
    feed.setFlowRate(10.0, "kg/hr");

    WaterCooler cooler = new WaterCooler("cooler", feed);
    cooler.setOutTemperature(20.0, "C");
    cooler.setWaterInletTemperature(25.0, "C");
    cooler.setWaterOutletTemperature(35.0, "C");
    cooler.setWaterPressure(1.0, "bara");

    ProcessSystem ps = new ProcessSystem();
    ps.add(feed);
    ps.add(cooler);
    ps.run();

    assertEquals(20.0, cooler.getOutletStream().getTemperature("C"), 1e-3);
    assertEquals(PhysicalPropertyModel.WATER,
        cooler.getOutletStream().getThermoSystem().getPhase(0).getPhysicalPropertyModel());

    double hin = Iapws_if97.h_pt(0.1, 298.15); // 25C
    double hout = Iapws_if97.h_pt(0.1, 308.15); // 35C
    double expectedFlow = -cooler.getDuty() / ((hout - hin) * 1000.0);
    assertEquals(expectedFlow, cooler.getCoolingWaterFlowRate("kg/sec"), 1e-2);

  }
}

