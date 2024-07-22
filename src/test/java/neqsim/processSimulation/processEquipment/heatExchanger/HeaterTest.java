package neqsim.processSimulation.processEquipment.heatExchanger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.security.AnyTypePermission;
import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.processSimulation.processSystem.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

public class HeaterTest {
  static neqsim.thermo.system.SystemInterface testSystem = null;
  double pressure_inlet = 85.0;
  double temperature_inlet = 35.0;
  double gasFlowRate = 5.0;
  ProcessSystem processOps = null;

  /**
   * @throws java.lang.Exception
   */
  @BeforeEach
  public void setUpBeforeClass() throws Exception {
    testSystem = new SystemSrkEos(298.0, 10.0);
    testSystem.addComponent("methane", 100.0);
    processOps = new ProcessSystem();
    Stream inletStream = new Stream("inletStream", testSystem);
    inletStream.setName("inlet stream");
    inletStream.setPressure(pressure_inlet, "bara");
    inletStream.setTemperature(temperature_inlet, "C");
    inletStream.setFlowRate(gasFlowRate, "MSm3/day");

    Heater heater1 = new Heater("heater 1", inletStream);
    heater1.setOutTemperature(310.0);
    processOps.add(inletStream);
    processOps.add(heater1);
    processOps.run();
  }

  @Test
  void testSavedHEX() {
    XStream xstream = new XStream();
    xstream.addPermission(AnyTypePermission.ANY);
    // Specify the file path to read
    Path filePath = Paths.get(
        "/workspaces/neqsim/src/test/java/neqsim/processSimulation/processEquipment/heatExchanger/HEX.xml");
    String xmlContents = "";
    try {
      xmlContents = Files.readString(filePath);
    } catch (IOException e) {
      e.printStackTrace();
    }

    // Deserialize from xml
    neqsim.processSimulation.processEquipment.heatExchanger.HeatExchanger hexCopy =
        (neqsim.processSimulation.processEquipment.heatExchanger.HeatExchanger) xstream
            .fromXML(xmlContents);
    hexCopy.setUAvalue(0.0);
    // hexCopy.setFirstTime(true);
    hexCopy.run();

    System.out.println("Inlet Temperature " + hexCopy.getInStream(0).getTemperature("C"));
    System.out.println("Outlet Temperature " + hexCopy.getOutStream(0).getTemperature("C"));
    System.out.println("UA Value " + hexCopy.getUAvalue());
    System.out.println("Duty " + hexCopy.getDuty());
  }

  @Test
  void testNeedRecalculation() {
    ((Heater) processOps.getUnit("heater 1")).setOutTemperature(348.1, "K");
    assertTrue(((Heater) processOps.getUnit("heater 1")).needRecalculation());
    processOps.run();
    assertFalse(((Heater) processOps.getUnit("heater 1")).needRecalculation());

    ((Heater) processOps.getUnit("heater 1")).setOutPressure(10.0, "bara");
    assertTrue(((Heater) processOps.getUnit("heater 1")).needRecalculation());
    processOps.run();
    assertFalse(((Heater) processOps.getUnit("heater 1")).needRecalculation());

  }
}
