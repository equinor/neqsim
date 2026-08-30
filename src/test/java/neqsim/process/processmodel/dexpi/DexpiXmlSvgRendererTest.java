package neqsim.process.processmodel.dexpi;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import neqsim.NeqSimTest;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.measurementdevice.PressureTransmitter;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

/** Regression tests for the NeqSim-native DEXPI Plant/P&amp;ID SVG renderer. */
class DexpiXmlSvgRendererTest extends NeqSimTest {
  @TempDir
  Path temporaryDirectory;

  @Test
  void rendersWriterGeometryShapeCatalogueAndInstrumentationWithEmptyStream() throws Exception {
    SystemSrkEos fluid = new SystemSrkEos(298.15, 50.0);
    fluid.addComponent("methane", 0.95);
    fluid.addComponent("water", 0.05);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);

    Stream feed = new Stream("10-FEED-001", fluid);
    feed.setFlowRate(10_000.0, "kg/hr");
    Separator separator = new Separator("20-V-101", feed);
    PressureTransmitter transmitter = new PressureTransmitter("PT-101", separator.getGasOutStream());

    ProcessSystem process = new ProcessSystem("DEXPI SVG renderer regression");
    process.add(feed);
    process.add(separator);
    process.add(transmitter);
    process.run();
    process.add(new Stream("90-EMPTY-SPARE"));

    Path dexpi = temporaryDirectory.resolve("plant.xml");
    Path svg = temporaryDirectory.resolve("plant.svg");
    DexpiXmlWriter.writeForPyDexpi(process, dexpi.toFile());
    DexpiXmlSvgRenderer.render(dexpi.toFile(), svg.toFile());

    String content = new String(java.nio.file.Files.readAllBytes(svg), java.nio.charset.StandardCharsets.UTF_8);
    assertTrue(content.contains("<svg"));
    assertTrue(content.contains("data-dexpi-id=\"ID-20-V-101\""));
    assertTrue(content.contains("data-dexpi-id=\"PT-101\""));
    assertTrue(content.contains("<polyline"));
    assertTrue(content.contains("DEXPI SVG renderer regression"));
  }

  @Test
  void rendersSolidPolylineAsFilledFlowArrow() throws Exception {
    String dexpiContent = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
        + "<PlantModel><Drawing Name=\"Filled flow arrow\"><Extent><Min X=\"0\" Y=\"0\"/>"
        + "<Max X=\"100\" Y=\"70\"/></Extent><PolyLine Filled=\"Solid\">"
        + "<Presentation LineType=\"0\" LineWeight=\"0.3\" R=\"0\" G=\"0\" B=\"0\"/>"
        + "<Coordinate X=\"10\" Y=\"10\"/><Coordinate X=\"20\" Y=\"15\"/>"
        + "<Coordinate X=\"10\" Y=\"20\"/></PolyLine></Drawing></PlantModel>";
    Path dexpi = temporaryDirectory.resolve("filled-flow-arrow.xml");
    java.nio.file.Files.write(dexpi, dexpiContent.getBytes(StandardCharsets.UTF_8));

    String content = DexpiXmlSvgRenderer.render(dexpi.toFile());

    assertTrue(content.contains("fill=\"#000000\""));
    assertTrue(content.contains("data-dexpi-filled=\"solid\""));
  }

  @Test
  void rendersGlobalTrimmedCurveWithCurvePresentation() throws Exception {
    String dexpiContent = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
        + "<PlantModel><Drawing Name=\"Crossing hop\"><Extent><Min X=\"0\" Y=\"0\"/>"
        + "<Max X=\"297\" Y=\"210\"/></Extent><TrimmedCurve StartAngle=\"0\" EndAngle=\"180\">"
        + "<Presentation LineType=\"0\" LineWeight=\"0.5\" R=\"0\" G=\"0\" B=\"0\"/>"
        + "<Circle Radius=\"1.6\"><Position><Location X=\"50\" Y=\"100\"/>"
        + "</Position></Circle></TrimmedCurve></Drawing></PlantModel>";
    Path dexpi = temporaryDirectory.resolve("crossing-hop.xml");
    java.nio.file.Files.write(dexpi, dexpiContent.getBytes(StandardCharsets.UTF_8));

    String content = DexpiXmlSvgRenderer.render(dexpi.toFile());

    assertTrue(content.contains("<path d=\"M 51.6 110 A 1.6 1.6 0 0 0 48.4 110\""));
    assertTrue(content.contains("stroke-width=\"0.5\""));
  }
}
