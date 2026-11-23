package neqsim.process.processmodel.dexpi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.separator.ThreePhaseSeparator;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for {@link PidDrawingRecordsDexpiWriter}: drawing records to DEXPI, simulation and rendering.
 *
 * @author NeqSim
 * @version 1.0
 */
public class PidDrawingRecordsDexpiWriterTest {
  @TempDir
  File tempDir;

  /**
   * Records for a three-phase separator: inlet from an off-page connector through a tee, gas to a compressor through an
   * actuated valve, oil to an off-page connector and water to a drain marked without normal flow.
   *
   * @return records JSON
   */
  private static String records() {
    return "{\"schemaVersion\":\"neqsim_pid_drawing_records.v1\","
        + "\"source\":{\"file\":\"demo.pdf\",\"page\":0,\"pageWidth\":1000,\"pageHeight\":700,"
        + "\"drawingNumber\":\"DEMO-P-XB-001\"}," + "\"nodes\":["
        + "{\"node_id\":\"EQ-1\",\"tag\":\"20VA001\",\"kind\":\"separator\",\"component_class\":\"ThreePhaseSeparator\","
        + "\"bbox\":[300,300,600,400],\"attributes\":{\"operatingPressure\":{\"value\":16,\"unit\":\"barg\"}},"
        + "\"ports\":[{\"port_id\":\"EQ-1-P1\",\"x\":320,\"y\":300,\"role\":\"Inlet\",\"label\":\"N01\"},"
        + "{\"port_id\":\"EQ-1-P2\",\"x\":580,\"y\":300,\"role\":\"GasOutlet\",\"label\":\"N02\"},"
        + "{\"port_id\":\"EQ-1-P3\",\"x\":400,\"y\":400,\"role\":\"OilOutlet\",\"label\":\"N03\"},"
        + "{\"port_id\":\"EQ-1-P4\",\"x\":500,\"y\":400,\"role\":\"WaterOutlet\",\"label\":\"N04\"}]},"
        + "{\"node_id\":\"EQ-2\",\"tag\":\"23KA001\",\"kind\":\"compressor\","
        + "\"component_class\":\"CentrifugalCompressor\",\"bbox\":[800,150,860,210],"
        + "\"attributes\":{\"operatingPressure\":{\"value\":40,\"unit\":\"bara\"}},"
        + "\"ports\":[{\"port_id\":\"EQ-2-P1\",\"x\":800,\"y\":180,\"role\":\"Inlet\"}]},"
        + "{\"node_id\":\"OPC-1\",\"tag\":\"DEMO-P-XB-000\",\"kind\":\"off_page_connector\",\"direction\":\"in\","
        + "\"service\":\"process\",\"label\":\"WELLSTREAM FROM HEATER\",\"bbox\":[20,190,120,210]},"
        + "{\"node_id\":\"OPC-2\",\"tag\":\"DEMO-P-XB-002\",\"kind\":\"off_page_connector\",\"direction\":\"out\","
        + "\"service\":\"process\",\"label\":\"OIL TO STABILISATION\",\"bbox\":[880,490,980,510]},"
        + "{\"node_id\":\"OPC-3\",\"tag\":\"DEMO-P-XB-057\",\"kind\":\"off_page_connector\",\"direction\":\"out\","
        + "\"service\":\"utility\",\"label\":\"TO CLOSED DRAIN\",\"bbox\":[880,590,980,610]},"
        + "{\"node_id\":\"TEE-1\",\"kind\":\"tee\",\"component_class\":\"PipeTee\",\"bbox\":[317,197,323,203]}],"
        + "\"edges\":[" + "{\"edge_id\":\"E-1\",\"from_node\":\"OPC-1\",\"to_node\":\"TEE-1\",\"normal_flow\":true,"
        + "\"line_number\":\"20L001-3000PR-BD100\",\"path\":[[120,200],[320,200]]},"
        + "{\"edge_id\":\"E-2\",\"from_node\":\"TEE-1\",\"to_node\":\"EQ-1\",\"to_port\":\"EQ-1-P1\","
        + "\"normal_flow\":true,\"path\":[[320,200],[320,300]]},"
        + "{\"edge_id\":\"E-3\",\"from_node\":\"EQ-1\",\"from_port\":\"EQ-1-P2\",\"to_node\":\"EQ-2\","
        + "\"to_port\":\"EQ-2-P1\",\"normal_flow\":true,\"path\":[[580,300],[580,180],[800,180]],"
        + "\"inline_items\":[{\"kind\":\"valve\",\"component_class\":\"BallValve\",\"tag\":\"XV-0001\","
        + "\"x\":700,\"y\":180,\"orientation\":\"horizontal\",\"gap\":12}]},"
        + "{\"edge_id\":\"E-4\",\"from_node\":\"EQ-1\",\"from_port\":\"EQ-1-P3\",\"to_node\":\"OPC-2\","
        + "\"normal_flow\":true,\"path\":[[400,400],[400,500],[880,500]]},"
        + "{\"edge_id\":\"E-5\",\"from_node\":\"EQ-1\",\"from_port\":\"EQ-1-P4\",\"to_node\":\"OPC-3\","
        + "\"normal_flow\":false,\"path\":[[500,400],[500,600],[880,600]]}],"
        + "\"instruments\":[{\"node_id\":\"INS-1\",\"tag\":\"PT-0101\",\"function\":\"PT\",\"number\":\"0101\","
        + "\"x\":650,\"y\":250}],\"warnings\":[]}";
  }

  /**
   * Records become a DEXPI file that builds and runs: the gas outlet is wired to the compressor through the inline
   * valve, the drain run is excluded, and the renderer draws the drawing geometry.
   *
   * @throws Exception on failure
   */
  @Test
  public void recordsBuildRunnableAndRenderableDexpi() throws Exception {
    File dexpi = new File(tempDir, "demo.dexpi.xml");
    PidDrawingRecordsDexpiWriter.Result result = PidDrawingRecordsDexpiWriter.write(records(), dexpi);
    assertEquals(2, (int) result.getCounts().get("equipment"));
    assertEquals(1, (int) result.getCounts().get("valves"));
    assertEquals(1, (int) result.getCounts().get("noFlowPipeRuns"));
    assertEquals("DEMO-P-XB-001", result.getDrawingNumber());

    SystemSrkEos fluid = new SystemSrkEos(273.15 + 55.0, 17.0);
    fluid.addComponent("methane", 50.0);
    fluid.addComponent("n-heptane", 30.0);
    fluid.addComponent("water", 20.0);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);
    DexpiSimulationBuilder builder = new DexpiSimulationBuilder(dexpi).setFluidTemplate(fluid)
        .setFeedPressure(16.0, "barg").setFeedTemperature(55.0, "C").setFeedFlowRate(50000.0, "kg/hr");
    ProcessSystem process = builder.build();
    process.run();

    ThreePhaseSeparator separator = (ThreePhaseSeparator) process.getUnit("20VA001");
    Compressor compressor = (Compressor) process.getUnit("23KA001");
    assertEquals(separator.getGasOutStream().getFlowRate("kg/hr"), compressor.getInletStream().getFlowRate("kg/hr"),
        1.0e-6);
    assertEquals(40.0, compressor.getOutletStream().getPressure("bara"), 1.0e-6);
    List<String> excluded = new ArrayList<String>();
    for (DexpiSimulationBuilder.BuildDiagnostic diagnostic : builder.getDiagnostics()) {
      if ("DEXPI_BUILD_NO_FLOW_BRANCH_EXCLUDED".equals(diagnostic.getCode())) {
        excluded.add(diagnostic.getElementId());
      }
    }
    assertTrue(excluded.contains("Segment-E-5"), excluded.toString());

    String svg = DexpiXmlSvgRenderer.render(dexpi);
    assertTrue(svg.contains("20VA001"));
    assertTrue(svg.contains("WELLSTREAM FROM HEATER"));
    assertEquals(1, DexpiXmlReader.readInstruments(dexpi).size());
  }

  /**
   * Unsupported records are rejected instead of producing an empty P&amp;ID.
   *
   * @throws Exception on failure
   */
  @Test
  public void rejectsUnsupportedSchema() throws Exception {
    File records = new File(tempDir, "bad.json");
    Files.write(records.toPath(), "{\"schemaVersion\":\"other\"}".getBytes(StandardCharsets.UTF_8));
    assertThrows(IOException.class,
        () -> PidDrawingRecordsDexpiWriter.write(records, new File(tempDir, "bad.dexpi.xml")));
  }
}
