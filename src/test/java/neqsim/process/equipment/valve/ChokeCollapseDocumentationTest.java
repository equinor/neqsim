package neqsim.process.equipment.valve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Executable coverage for the Python example in the choke-collapse guide.
 *
 * @author ESOL
 * @version 1.0
 */
class ChokeCollapseDocumentationTest {
  private static final Path GUIDE = Paths.get("docs", "process", "choke-collapse.md");
  private static final String MARKER = "<!-- choke-collapse-python: standalone-example -->";
  private static final Pattern PYTHON_FENCE =
      Pattern.compile("```python\\r?\\n(.*?)\\r?\\n```", Pattern.DOTALL);

  /**
   * Freezes the supported gateway and API sequence in the published Python source.
   *
   * @throws Exception if the guide cannot be read
   */
  @Test
  void pythonExampleUsesSupportedGatewayAndCurrentApis() throws Exception {
    String source = pythonExample();

    assertTrue(source.contains("from neqsim import jneqsim"));
    assertFalse(source.contains("JClass"));
    assertFalse(source.contains("jpype"));
    assertFalse(source.contains("ns."));
    assertFalse(source.contains("print("));
    assertTrue(source.contains("SystemSrkEos(298.15, 100.0)"));
    assertTrue(source.contains("Stream(\"feed\", fluid)"));
    assertTrue(source.contains("ThrottlingValve(\"CV-100\", feed)"));
    assertTrue(source.indexOf("feed.run()") < source.indexOf("ThrottlingValve("));
    assertTrue(source.indexOf("valve.run()") < source.indexOf("valve.analyseChokeCollapse()"));
    assertTrue(source.contains("result.getPressureRatio() < result.getCriticalPressureRatio()"));
  }

  /**
   * Executes the APIs and engineering assertions represented by the Python example.
   */
  @Test
  void pythonExampleApisClassifyCriticalGasFlow() {
    SystemInterface fluid = new SystemSrkEos(298.15, 100.0);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(1000.0, "kg/hr");
    feed.setPressure(100.0, "bara");
    feed.setTemperature(25.0, "C");
    feed.run();

    ThrottlingValve valve = new ThrottlingValve("CV-100", feed);
    valve.setOutletPressure(10.0);
    valve.run();

    ChokeCollapseResult result = valve.analyseChokeCollapse();
    assertNotNull(result);
    assertEquals(ChokeCollapseResult.FlowRegime.CRITICAL, result.getFlowRegime());
    assertTrue(result.getPressureRatio() < result.getCriticalPressureRatio());
    assertEquals(1.0, result.getMachNumber(), 0.0);
    assertTrue(Double.isFinite(result.getCriticalPressureRatio()));
    assertTrue(result.toJson().contains("\"flowRegime\""));
  }

  /**
   * Extracts the uniquely marked Python fence from the choke-collapse guide.
   *
   * @return exact published Python source
   * @throws Exception if the guide cannot be read
   */
  private static String pythonExample() throws Exception {
    String markdown = new String(Files.readAllBytes(GUIDE), StandardCharsets.UTF_8);
    int markerPosition = markdown.indexOf(MARKER);
    assertTrue(markerPosition >= 0, "Missing marker: " + MARKER);
    assertEquals(markerPosition, markdown.lastIndexOf(MARKER), "Marker must be unique: " + MARKER);

    Matcher fence = PYTHON_FENCE.matcher(markdown.substring(markerPosition));
    assertTrue(fence.find(), "Missing Python fence after marker: " + MARKER);
    return fence.group(1);
  }
}
