package neqsim.process.processmodel.diagram;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import neqsim.process.processmodel.ProcessSystem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EngineeringDiagramDualProfileDeliveryTest {
  @TempDir
  Path temporaryDirectory;

  @Test
  void publishesPfdAndReviewRequiredPidFromOneCanonicalPlant() throws IOException {
    ProcessSystem process = EngineeringDiagramReferenceFixtures.simpleTrain().getProcessSystem();
    String classicDot = process.toDOT();
    EngineeringDiagramDualProfileDelivery.Request request = EngineeringDiagramDualProfileDelivery.Request
        .builder("PLANT-10", "A", "PFD-10-001", "PID-10-001", "Separation and compression").build();

    EngineeringDiagramDualProfileDelivery.Report report = EngineeringDiagramDualProfileDelivery.deliver(process,
        temporaryDirectory.resolve("dual-profile"), request);

    assertTrue(report.isComplete(), report.toJson());
    assertEquals(report.getPfd().getDocumentSet().getSourceGraphFingerprint(),
        report.getPid().getDocumentSet().getSourceGraphFingerprint());
    assertEquals(classicDot, process.toDOT());
    assertTrue(Files.isRegularFile(report.getDirectory().resolve("pfd/drawing-set.pdf")));
    assertTrue(Files.isRegularFile(report.getDirectory().resolve("pid/drawing-set.pdf")));
    assertTrue(Files.isRegularFile(report.getDirectory().resolve("dual-profile-manifest.json")));
    assertTrue(report.toJson().contains("\"pfdContentProfile\": \"PFD\""));
    assertTrue(report.toJson().contains("\"pidContentProfile\": \"PID\""));
    assertTrue(report.toJson().contains("\"pidApprovalStatus\": \"REVIEW_REQUIRED\""));
    assertTrue(report.toJson().contains("\"pidDexpiInformationModel\": \"PROCESS_PFD_BFD_COMPANION_ONLY\""));
    assertFalse(report.toJson().contains("\"fitnessForConstruction\": true"));
    assertNull(report.getPidEngineeringRegisters());
    assertFalse(Files.exists(report.getDirectory().resolve("pid/pid-engineering-registers.json")));
  }

  @Test
  void publishesOptInProposalSidecarsAndPidOverlayWithoutChangingTheSourceOrPfd() throws IOException {
    ProcessSystem process = EngineeringDiagramReferenceFixtures.simpleTrain().getProcessSystem();
    String sourceDot = process.toDOT();
    int measurementCount = process.getMeasurementDevices().size();
    EngineeringDiagramDualProfileDelivery.Request.Builder builder = EngineeringDiagramDualProfileDelivery.Request
        .builder("PLANT-10", "A", "PFD-10-001", "PID-10-001", "Separation and compression");
    EngineeringDiagramDualProfileDelivery.Report baseline = EngineeringDiagramDualProfileDelivery.deliver(process,
        temporaryDirectory.resolve("baseline"), builder.build());
    EngineeringDiagramDualProfileDelivery.Report report = EngineeringDiagramDualProfileDelivery.deliver(process,
        temporaryDirectory.resolve("registers"), builder.includePidEngineeringRegisters(true).build());

    assertTrue(report.isComplete(), report.toJson());
    assertEquals(sourceDot, process.toDOT());
    assertEquals(measurementCount, process.getMeasurementDevices().size());
    assertEquals(baseline.getPfd().getFingerprint(), report.getPfd().getFingerprint());
    assertNotEquals(baseline.getPid().getFingerprint(), report.getPid().getFingerprint());
    assertEquals(baseline.getPfd().getRendering().getSvgBySheetId(), report.getPfd().getRendering().getSvgBySheetId());
    assertNotEquals(baseline.getPid().getRendering().getSvgBySheetId(),
        report.getPid().getRendering().getSvgBySheetId());
    String pidSvg = String.join("\n", report.getPid().getRendering().getSvgBySheetId().values());
    assertTrue(pidSvg.contains("P&amp;ID PROPOSAL OVERLAY"));
    assertFalse(report.getPfd().getRendering().getSvgBySheetId().toString().contains("P&amp;ID PROPOSAL OVERLAY"));
    EngineeringDiagramPidRegisters registers = report.getPidEngineeringRegisters();
    assertEquals(report.getPid().getDocumentSet().getSourceGraphFingerprint(), registers.getSourceGraphFingerprint());
    assertTrue(registers.getLineCount() > 0);
    assertTrue(registers.getNozzleCount() > 0);
    assertTrue(registers.getValveCount() > 0);
    assertTrue(registers.getInstrumentCount() > 0);
    assertTrue(registers.getControlSignalCount() > 0);
    assertTrue(registers.getInterfaceCount() > 0);
    assertTrue(registers.getGapCount() > 0);
    int proposalCount = 0;
    Map<String, List<String>> proposalIdsByOwnerAndRegister = new java.util.TreeMap<String, List<String>>();
    Map<String, String> proposalOwnerById = new java.util.TreeMap<String, String>();
    for (String register : new String[] { "nozzles", "valves", "instruments", "interfaces" }) {
      for (Map<String, Object> row : rows(registers, register)) {
        String id = String.valueOf(row.get("id"));
        String tag = String.valueOf(row.get("tag"));
        String group = row.get("semanticEquipmentId") + "|" + register;
        List<String> proposalIds = proposalIdsByOwnerAndRegister.get(group);
        if (proposalIds == null) {
          proposalIds = new java.util.ArrayList<String>();
          proposalIdsByOwnerAndRegister.put(group, proposalIds);
        }
        proposalIds.add(id);
        proposalOwnerById.put(id, String.valueOf(row.get("semanticEquipmentId")));
        assertTrue(pidSvg.contains("data-semantic-id=\"pid-proposal:" + id + "\""), id);
        assertTrue(pidSvg.contains(">" + tag + "</text>"), tag);
        proposalCount++;
      }
    }
    assertTrue(proposalCount > 4);
    assertFalse(pidSvg.contains(" +1</text>"));
    assertTrue(pidSvg.contains("font-size=\"2.5\""));
    for (Map.Entry<String, List<String>> entry : proposalIdsByOwnerAndRegister.entrySet()) {
      if (entry.getValue().size() < 2) {
        continue;
      }
      Set<String> ownerTerminals = new HashSet<String>();
      for (String id : entry.getValue()) {
        ownerTerminals.add(proposalConnectionTerminal(pidSvg, id));
      }
      assertEquals(entry.getValue().size(), ownerTerminals.size(), entry.getKey());
      if ((entry.getKey().endsWith("|instruments") || entry.getKey().endsWith("|valves"))
          && entry.getValue().size() > 4) {
        Set<String> markerRows = new HashSet<String>();
        double minimumX = Double.POSITIVE_INFINITY;
        double maximumX = Double.NEGATIVE_INFINITY;
        for (String id : entry.getValue()) {
          double[] marker = proposalMarkerPoint(pidSvg, id);
          minimumX = Math.min(minimumX, marker[0]);
          maximumX = Math.max(maximumX, marker[0]);
          markerRows.add(String.valueOf(marker[1]));
        }
        assertTrue(maximumX - minimumX <= 84.0, entry.getKey());
        assertTrue(markerRows.size() > 1, entry.getKey());
      }
    }
    List<Map<String, Object>> signals = rows(registers, "controlSignals");
    assertTrue(signals.size() > 1);
    for (Map<String, Object> signal : signals) {
      String signalId = "pid-signal:" + signal.get("sourcePidElementId") + ":" + signal.get("targetPidElementId");
      assertTrue(pidSvg.contains("data-semantic-id=\"" + signalId + "\""), signalId);
      String sourceId = String.valueOf(signal.get("sourcePidElementId"));
      String targetId = String.valueOf(signal.get("targetPidElementId"));
      if (proposalOwnerById.get(sourceId).equals(proposalOwnerById.get(targetId))) {
        String[] points = pointsForSemanticId(pidSvg, signalId).split(" ");
        double sourceX = Double.parseDouble(points[0].split(",", 2)[0]);
        double targetX = Double.parseDouble(points[points.length - 1].split(",", 2)[0]);
        double trackX = Double.parseDouble(points[1].split(",", 2)[0]);
        assertTrue(trackX < Math.min(sourceX, targetX) || trackX > Math.max(sourceX, targetX), signalId);
      }
    }
    assertEquals(signals.size(), signalPaths(pidSvg).size());
    assertTrue(((java.util.List<?>) registers.toMap().get("nozzles")).stream().anyMatch(
        row -> !((java.util.List<?>) ((java.util.Map<?, ?>) row).get("candidateSemanticConnectionIds")).isEmpty()));
    for (String file : new String[] { "pid-design-model.json", "pid-completeness-report.json",
        "pid-engineering-registers.json" }) {
      JsonObject sidecar = new Gson().fromJson(
          new String(Files.readAllBytes(report.getDirectory().resolve("pid").resolve(file)), StandardCharsets.UTF_8),
          JsonObject.class);
      assertFalse(sidecar.get("fitnessForConstruction").getAsBoolean());
    }
    assertEquals(registers.toJson(),
        new String(Files.readAllBytes(report.getDirectory().resolve("pid/pid-engineering-registers.json")),
            StandardCharsets.UTF_8));
    assertTrue(
        report.toJson().contains("REVIEW_REQUIRED_SOURCE_LINKED_OVERLAY_IN_SVG_PDF_WITH_SIDECARS;EXCHANGES_UNCHANGED"));
  }

  @Test
  void isDeterministicAcrossFreshDestinations() throws IOException {
    EngineeringDiagramDualProfileDelivery.Request request = EngineeringDiagramDualProfileDelivery.Request
        .builder("PLANT-20", "B", "PFD-20-001", "PID-20-001", "Branched separation and compression").build();

    EngineeringDiagramDualProfileDelivery.Report first = EngineeringDiagramDualProfileDelivery.deliver(
        EngineeringDiagramReferenceFixtures.branchedSeparatorCompressionTrain().getProcessSystem(),
        temporaryDirectory.resolve("first"), request);
    EngineeringDiagramDualProfileDelivery.Report second = EngineeringDiagramDualProfileDelivery.deliver(
        EngineeringDiagramReferenceFixtures.branchedSeparatorCompressionTrain().getProcessSystem(),
        temporaryDirectory.resolve("second"), request);

    assertEquals(first.toJson(), second.toJson());
    assertEquals(first.getFingerprint(), second.getFingerprint());
    assertEquals(first.getPfd().getFingerprint(), second.getPfd().getFingerprint());
    assertEquals(first.getPid().getFingerprint(), second.getPid().getFingerprint());
  }

  @Test
  void refusesExistingDestinationAndMismatchedPlantIdentity() throws IOException {
    Path existing = Files.createDirectory(temporaryDirectory.resolve("existing"));
    EngineeringDiagramDualProfileDelivery.Request request = EngineeringDiagramDualProfileDelivery.Request
        .builder("PLANT-10", "A", "PFD-10-001", "PID-10-001", "Separation and compression").build();

    assertThrows(IllegalArgumentException.class, () -> EngineeringDiagramDualProfileDelivery
        .deliver(EngineeringDiagramReferenceFixtures.simpleTrain().getProcessSystem(), existing, request));
    assertThrows(IllegalArgumentException.class, () -> EngineeringDiagramDualProfileDelivery.Request
        .builder(" ", "A", "PFD-10-001", "PID-10-001", "Separation and compression").build());
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> rows(EngineeringDiagramPidRegisters registers, String name) {
    return (List<Map<String, Object>>) (List<?>) registers.toMap().get(name);
  }

  private static Set<String> signalPaths(String svg) {
    Pattern pattern = Pattern.compile("<polyline points=\"([^\"]+)\"[^>]+data-semantic-id=\"pid-signal:[^\"]+\"");
    Matcher matcher = pattern.matcher(svg);
    Set<String> result = new HashSet<String>();
    while (matcher.find()) {
      result.add(matcher.group(1));
    }
    return result;
  }

  private static String proposalConnectionTerminal(String svg, String proposalId) {
    Pattern pattern = Pattern.compile("<polyline points=\"([^\"]+)\"[^>]+data-semantic-id=\""
        + Pattern.quote("pid-proposal:" + proposalId + ":connection") + "\"");
    Matcher matcher = pattern.matcher(svg);
    assertTrue(matcher.find(), proposalId);
    String[] points = matcher.group(1).split(" ");
    return points[points.length - 1];
  }

  private static double[] proposalMarkerPoint(String svg, String proposalId) {
    String[] points = pointsForSemanticId(svg, "pid-proposal:" + proposalId + ":connection").split(" ");
    String[] coordinates = points[0].split(",", 2);
    return new double[] { Double.parseDouble(coordinates[0]), Double.parseDouble(coordinates[1]) };
  }

  private static String pointsForSemanticId(String svg, String semanticId) {
    Pattern pattern = Pattern
        .compile("<polyline points=\"([^\"]+)\"[^>]+data-semantic-id=\"" + Pattern.quote(semanticId) + "\"");
    Matcher matcher = pattern.matcher(svg);
    assertTrue(matcher.find(), semanticId);
    return matcher.group(1);
  }
}
