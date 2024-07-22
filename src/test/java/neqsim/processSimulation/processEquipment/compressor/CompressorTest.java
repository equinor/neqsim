package neqsim.processSimulation.processEquipment.compressor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.security.AnyTypePermission;

class CompressorTest2 extends neqsim.NeqSimTest {
  static Logger logger = LogManager.getLogger(CompressorTest.class);

  static neqsim.thermo.system.SystemInterface testSystem = null;

  /**
   * <p>
   * testIsentropicCalcMethod.
   * </p>
   */
  @Test
  public void testCompressor() {
    XStream xstream = new XStream();
    xstream.addPermission(AnyTypePermission.ANY);
    // Specify the file path to read
    Path filePath = Paths.get(
        "/workspaces/neqsim/src/test/java/neqsim/processSimulation/processEquipment/compressor/first_stage_recompressor.xml");
    String xmlContents = "";
    try {
      xmlContents = Files.readString(filePath);
    } catch (IOException e) {
      e.printStackTrace();
    }

    // Deserialize from xml
    neqsim.processSimulation.processEquipment.compressor.Compressor compressorCopy =
        (neqsim.processSimulation.processEquipment.compressor.Compressor) xstream
            .fromXML(xmlContents);
    compressorCopy.run();


  }

}
