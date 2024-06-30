package neqsim.processSimulation.processSystem;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeEach;
import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.security.AnyTypePermission;

/**
 * Test class for GlycolRig.
 */
public class OilProcessTest extends neqsim.NeqSimTest {
  static Logger logger = LogManager.getLogger(OilProcessTest.class);

  ProcessSystem p;
  String _name = "TestProcess";

  @BeforeEach
  public void setUp() {
    p = new ProcessSystem();
    p.setName(_name);
  }

  //@Test
  public void runTest() {
    XStream xstream = new XStream();
    xstream.addPermission(AnyTypePermission.ANY);
    // Specify the file path to read
    Path filePath = Paths.get(
        "/workspaces/neqsim/src/test/java/neqsim/processSimulation/processSystem/failing_oil_process.xml");
    String xmlContents = "";
    try {
      xmlContents = Files.readString(filePath);
    } catch (IOException e) {
      e.printStackTrace();
    }

    // Deserialize from xml
    neqsim.processSimulation.processSystem.ProcessSystem operationsCopy =
        (neqsim.processSimulation.processSystem.ProcessSystem) xstream.fromXML(xmlContents);
    operationsCopy.run();


        
    neqsim.processSimulation.processEquipment.separator.Separator secondSeparator =
        (neqsim.processSimulation.processEquipment.separator.Separator) operationsCopy
            .getUnit("2nd stage separator");

    neqsim.processSimulation.processEquipment.separator.Separator firstScrubber =
    (neqsim.processSimulation.processEquipment.separator.Separator) operationsCopy
        .getUnit("1st stage scrubber");

    neqsim.processSimulation.processEquipment.separator.Separator fourthScrubber =
    (neqsim.processSimulation.processEquipment.separator.Separator) operationsCopy
        .getUnit("4th stage scrubber");

    neqsim.processSimulation.processEquipment.separator.Separator firstSeparator =
        (neqsim.processSimulation.processEquipment.separator.Separator) operationsCopy
            .getUnit("1st stage separator");

    double volFlow = secondSeparator.getLiquidOutStream().getFluid().getFlowRate("m3/hr");
    System.out.println(
        "secondSeparator The flow rate liquid should be 293.9903880784443 m3/hr, the actual value is "
            + volFlow);
    secondSeparator.getLiquidOutStream().getFluid().initPhysicalProperties();
    double dens = secondSeparator.getLiquidOutStream().getFluid().getDensity("kg/m3");
    System.out.println(
        "secondSeparator Density liquid should be 762.5376229680833 kg/m3, the actual value is "
            + dens);

    double dens1 = (firstScrubber.getThermoSystem().phaseToSystem("oil")).getDensity("kg/m3");
    System.out.println(
        "1thScrubber Density liquid should be 762.5376229680833 kg/m3, the actual value is "
            + dens1);

    double dens4 = (fourthScrubber.getThermoSystem().phaseToSystem("oil")).getDensity("kg/m3");
    System.out.println(
        "4thScrubber Density liquid should be 762.5376229680833 kg/m3, the actual value is "
            + dens4);

  }

}
