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
    assertTrue(report.getPid().getRendering().getSvgBySheetId().toString().contains("P&amp;ID PROPOSAL OVERLAY"));
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
}
