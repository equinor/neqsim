package neqsim.process.examples;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import neqsim.process.engineering.model.EngineeringNode;
import neqsim.process.processmodel.diagram.NativeEngineeringDiagramRenderer;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.engineering.model.EngineeringDiagramDocumentSet.Sheet;
import neqsim.process.processmodel.diagram.EngineeringDiagramDualProfileDelivery;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Tag("slow")
class Comparesimulations2EngineeringDiagramReferenceTest {
  @TempDir
  Path temporaryDirectory;

  @Test
  void rendersDeterministicFullModelPfdAndPidWithoutInventingIllustratedEquipment() throws IOException {
    ProcessSystem firstProcess = Comparesimulations2EngineeringDiagramReference.createExecutedProcess();

    assertTrue(firstProcess.getUnitOperations().size() >= 39);
    assertFalse(firstProcess.getUnitOperations().stream().anyMatch(unit -> "24-VB-01".equals(unit.getName())));

    EngineeringDiagramDualProfileDelivery.Report first = Comparesimulations2EngineeringDiagramReference
        .deliver(firstProcess, temporaryDirectory.resolve("first"));

    assertTrue(first.isComplete(), first.toJson());
    assertEquals(7, first.getBlockFlowProjection().getBlockLabels().size());
    assertTrue(Files.isRegularFile(first.getDirectory().resolve("bfd/overview.svg")));
    assertTrue(Files.isRegularFile(first.getDirectory().resolve("bfd/overview.pdf")));
    assertTrue(Files.isRegularFile(first.getDirectory().resolve("bfd/material-block-graph.json")));
    assertTrue(first.getBlockFlowRendering().getDiagnostics().stream()
        .noneMatch(d -> d.getSeverity() != NativeEngineeringDiagramRenderer.Severity.INFO));
    Map<String, Integer> connectionCoverage = new TreeMap<String, Integer>();
    Set<String> blockFlows = new TreeSet<String>();
    for (EngineeringNode node : first.getBlockFlowProjection().toGraph().getNodes().values()) {
      Object ids = node.getProperties()
          .get(node.getKind() == EngineeringNode.Kind.EQUIPMENT ? "internalConnectionIds" : "sourceConnectionIds");
      if (ids instanceof List<?>) {
        for (Object id : (List<?>) ids) {
          String key = id.toString();
          connectionCoverage.put(key, connectionCoverage.containsKey(key) ? connectionCoverage.get(key) + 1 : 1);
        }
      }
      if (node.getKind() == EngineeringNode.Kind.PIPE_SEGMENT) {
        blockFlows
            .add(node.getProperties().get("sourceEquipment") + " -> " + node.getProperties().get("targetEquipment"));
      }
    }
    Set<String> canonicalConnections = new TreeSet<String>();
    first.getPfd().getDocumentSet().getSemanticObjects().stream()
        .filter(n -> n.getKind() == EngineeringNode.Kind.PIPE_SEGMENT)
        .forEach(n -> canonicalConnections.add(n.getId()));
    assertEquals(canonicalConnections, connectionCoverage.keySet());
    assertTrue(connectionCoverage.values().stream().allMatch(n -> n == 1));
    assertTrue(
        blockFlows.containsAll(Arrays.asList("Separation -> Oil export", "Cold process -> Fuel gas",
            "Cold process -> Gas export", "Recompression -> Separation", "Cold process -> Separation")),
        blockFlows.toString());
    assertFalse(blockFlows.contains("Gas export -> Oil export"));
    assertTrue(first.getPfd().getRendering().getDiagnostics().stream()
        .noneMatch(d -> d.getCode().equals("DIAGRAM_RENDER_ROUTE_ENDPOINT_INTERSECTION")
            || d.getCode().equals("DIAGRAM_RENDER_ROUTE_OUTSIDE_SHEET")
            || d.getCode().equals("DIAGRAM_RENDER_DUPLICATE_ROUTE")));
    assertEquals(first.getPfd().getDocumentSet().getSourceGraphFingerprint(),
        first.getPid().getDocumentSet().getSourceGraphFingerprint());
    assertTrue(first.getPfd().getRendering().getSvgBySheetId().size() >= 3);
    assertTrue(first.getPid().getRendering().getSvgBySheetId().size() >= 3);
    Sheet pfdOverview = first.getPfd().getDocumentSet().getDrawings().get(0).getSheets().get(0);
    Sheet pidOverview = first.getPid().getDocumentSet().getDrawings().get(0).getSheets().get(0);
    assertEquals(3, pfdOverview.getOverviewRegions().size());
    assertEquals(3, pidOverview.getOverviewRegions().size());
    String pfdSvg = first.getPfd().getRendering().getSvgBySheetId().toString();
    String pidSvg = first.getPid().getRendering().getSvgBySheetId().toString();
    String pfdOverviewSvg = first.getPfd().getRendering().getSvgBySheetId().get(pfdOverview.getId());
    String pidOverviewSvg = first.getPid().getRendering().getSvgBySheetId().get(pidOverview.getId());
    assertTrue(pfdOverviewSvg.contains("CONTROLLED SHEET INDEX - NOT PROCESS CONNECTIVITY"));
    assertTrue(pidOverviewSvg.contains("CONTROLLED SHEET INDEX - NOT PROCESS CONNECTIVITY"));
    assertTrue(pfdOverviewSvg.contains("SHEET 2 - Three-stage separation and oil export"));
    assertTrue(pfdOverviewSvg.contains("SHEET 3 - Flash-gas recompression and dew point"));
    assertTrue(pfdOverviewSvg.contains("SHEET 4 - Fuel split and gas export compression"));
    assertTrue(pfdOverviewSvg.contains("12 CANONICAL EQUIPMENT OBJECTS - SEE REFERENCED SHEET"));
    assertTrue(pfdOverviewSvg.contains("13 CANONICAL EQUIPMENT OBJECTS - SEE REFERENCED SHEET"));
    assertTrue(pfdOverviewSvg.contains("10 CANONICAL EQUIPMENT OBJECTS - SEE REFERENCED SHEET"));
    assertFalse(pfdSvg.contains("P&amp;ID PROPOSAL OVERLAY"));
    assertTrue(pidSvg.contains("P&amp;ID PROPOSAL OVERLAY"));
    assertTrue(pidSvg.contains("data-semantic-id=\"pid-proposal:"));
    assertTrue(pidSvg.contains("data-semantic-id=\"pid-signal:"));
    assertNotEquals(pfdSvg, pidSvg);
    assertTrue(Files.isRegularFile(first.getDirectory().resolve("pfd/drawing-set.pdf")));
    assertTrue(Files.isRegularFile(first.getDirectory().resolve("pid/drawing-set.pdf")));
    assertTrue(Files.isRegularFile(first.getDirectory().resolve("stream-table.json")));
    assertTrue(Files.isRegularFile(first.getDirectory().resolve("balance-table.json")));
    assertTrue(Files.isRegularFile(first.getDirectory().resolve("pid/dexpi-plant-2.0.xml")));
    assertTrue(Files.isRegularFile(first.getDirectory().resolve("pid/proteus-4.1.xml")));
    assertTrue(Files.isRegularFile(first.getDirectory().resolve("pid/pid-design-model.json")));
    assertTrue(Files.isRegularFile(first.getDirectory().resolve("pid/pid-completeness-report.json")));
    assertTrue(Files.isRegularFile(first.getDirectory().resolve("pid/pid-engineering-registers.json")));
    assertTrue(first.getPidEngineeringRegisters().getLineCount() > 0);
    assertTrue(first.getPidEngineeringRegisters().getNozzleCount() > 0);
    assertTrue(first.getPidEngineeringRegisters().getValveCount() > 0);
    assertTrue(first.getPidEngineeringRegisters().getInstrumentCount() > 0);
    assertTrue(first.getPidEngineeringRegisters().getControlSignalCount() > 0);
    assertTrue(first.getPidEngineeringRegisters().getInterfaceCount() > 0);
    assertTrue(first.getPidEngineeringRegisters().getGapCount() > 0);
    String registerJson = new String(
        Files.readAllBytes(first.getDirectory().resolve("pid/pid-engineering-registers.json")), "UTF-8");
    assertTrue(registerJson.contains("\"schemaVersion\": \"neqsim_engineering_diagram_pid_registers.v1\""));
    assertTrue(registerJson.contains("\"nominalPipeSize\": \"PROJECT_INPUT_REQUIRED\""));
    assertTrue(registerJson.contains("\"reducerDisposition\": \"NO_GOVERNED_REDUCER_DECLARATION\""));
    assertTrue(registerJson.contains("\"qualificationStatus\": \"REVIEW_REQUIRED\""));
    assertFalse(registerJson.contains("24-VB-01"));
    assertTrue(first.toJson().contains("\"pidApprovalStatus\": \"REVIEW_REQUIRED\""));
    assertTrue(first.toJson().contains("\"fitnessForConstruction\": false"));

    ProcessSystem secondProcess = Comparesimulations2EngineeringDiagramReference.createExecutedProcess();
    EngineeringDiagramDualProfileDelivery.Report second = Comparesimulations2EngineeringDiagramReference
        .deliver(secondProcess, temporaryDirectory.resolve("second"));

    assertEquals(first.getFingerprint(), second.getFingerprint());
    assertEquals(first.getBlockFlowProjection().toJson(), second.getBlockFlowProjection().toJson());
    assertEquals(first.getBlockFlowRendering().getSvgBySheetId(), second.getBlockFlowRendering().getSvgBySheetId());
    assertArrayEquals(first.getBlockFlowRendering().getPdf(), second.getBlockFlowRendering().getPdf());
    assertEquals(first.getPfd().getRendering().getSvgBySheetId(), second.getPfd().getRendering().getSvgBySheetId());
    assertEquals(first.getPid().getRendering().getSvgBySheetId(), second.getPid().getRendering().getSvgBySheetId());
    assertArrayEquals(Files.readAllBytes(first.getDirectory().resolve("pfd/drawing-set.pdf")),
        Files.readAllBytes(second.getDirectory().resolve("pfd/drawing-set.pdf")));
    assertArrayEquals(Files.readAllBytes(first.getDirectory().resolve("pid/drawing-set.pdf")),
        Files.readAllBytes(second.getDirectory().resolve("pid/drawing-set.pdf")));
    assertArrayEquals(Files.readAllBytes(first.getDirectory().resolve("pid/pid-design-model.json")),
        Files.readAllBytes(second.getDirectory().resolve("pid/pid-design-model.json")));
    assertArrayEquals(Files.readAllBytes(first.getDirectory().resolve("pid/pid-completeness-report.json")),
        Files.readAllBytes(second.getDirectory().resolve("pid/pid-completeness-report.json")));
    assertArrayEquals(Files.readAllBytes(first.getDirectory().resolve("pid/pid-engineering-registers.json")),
        Files.readAllBytes(second.getDirectory().resolve("pid/pid-engineering-registers.json")));
  }
}
