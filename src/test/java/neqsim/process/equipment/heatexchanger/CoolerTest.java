package neqsim.process.equipment.heatexchanger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

class CoolerTest {
  neqsim.thermo.system.SystemInterface testSystem;
  Stream inletStream;
  ProcessSystem process;

  @BeforeEach
  void setUp() {
    testSystem = new SystemSrkEos(298.0, 50.0);
    testSystem.addComponent("methane", 90.0);
    testSystem.addComponent("ethane", 10.0);
    testSystem.setMixingRule("classic");

    inletStream = new Stream("inlet", testSystem);
    inletStream.setPressure(50.0, "bara");
    inletStream.setTemperature(80.0, "C");
    inletStream.setFlowRate(5.0, "MSm3/day");

    process = new ProcessSystem();
    process.add(inletStream);
  }

  @Test
  void testCoolerReducesTemperature() {
    Cooler cooler = new Cooler("cooler", inletStream);
    cooler.setOutTemperature(273.15 + 30.0);
    process.add(cooler);
    process.run();

    double outletTempC = cooler.getOutletStream().getTemperature("C");
    assertEquals(30.0, outletTempC, 0.5);
  }

  @Test
  void testCoolerDutyIsNegative() {
    // Cooling should remove heat, resulting in a negative duty for the cooler
    Cooler cooler = new Cooler("cooler", inletStream);
    cooler.setOutTemperature(273.15 + 30.0);
    process.add(cooler);
    process.run();

    // getDuty() returns heat added. For a cooler, this should be negative
    // (heat is removed from the stream)
    double duty = cooler.getDuty();
    assertTrue(duty < 0, "Cooler duty should be negative (heat removed), got " + duty);
  }

  @Test
  void testCoolerPreservesPressure() {
    Cooler cooler = new Cooler("cooler", inletStream);
    cooler.setOutTemperature(273.15 + 30.0);
    process.add(cooler);
    process.run();

    double inletP = inletStream.getPressure("bara");
    double outletP = cooler.getOutletStream().getPressure("bara");
    assertEquals(inletP, outletP, 0.01);
  }

  @Test
  void testCoolerWithOutletPressure() {
    Cooler cooler = new Cooler("cooler", inletStream);
    cooler.setOutTemperature(273.15 + 30.0);
    cooler.setOutPressure(45.0, "bara");
    process.add(cooler);
    process.run();

    double outletP = cooler.getOutletStream().getPressure("bara");
    assertEquals(45.0, outletP, 0.1);

    double outletTempC = cooler.getOutletStream().getTemperature("C");
    assertEquals(30.0, outletTempC, 0.5);
  }

  @Test
  void testCoolerMassBalance() {
    Cooler cooler = new Cooler("cooler", inletStream);
    cooler.setOutTemperature(273.15 + 30.0);
    process.add(cooler);
    process.run();

    double inletFlow = inletStream.getThermoSystem().getFlowRate("kg/hr");
    double outletFlow = cooler.getOutletStream().getThermoSystem().getFlowRate("kg/hr");
    assertEquals(inletFlow, outletFlow, inletFlow * 1e-6);
  }

  @Test
  void testCoolerToJson() {
    Cooler cooler = new Cooler("cooler", inletStream);
    cooler.setOutTemperature(273.15 + 30.0);
    process.add(cooler);
    process.run();

    String json = cooler.toJson();
    assertNotNull(json);
    assertFalse(json.isEmpty());
  }

  @Test
  void testCoolerNeedRecalculation() {
    Cooler cooler = new Cooler("cooler", inletStream);
    cooler.setOutTemperature(273.15 + 30.0);
    process.add(cooler);
    process.run();

    assertFalse(cooler.needRecalculation());

    cooler.setOutTemperature(273.15 + 20.0);
    assertTrue(cooler.needRecalculation());
  }
}
