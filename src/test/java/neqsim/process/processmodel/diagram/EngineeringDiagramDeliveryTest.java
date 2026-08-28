package neqsim.process.processmodel.diagram;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import neqsim.process.engineering.model.EngineeringDiagramDocumentSet.ContentProfile;
import neqsim.process.processmodel.ProcessConnection;
import neqsim.process.processmodel.ProcessModel;
import neqsim.process.processmodel.ProcessSystem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EngineeringDiagramDeliveryTest {
  @TempDir
  Path temporaryDirectory;

  @Test
  void publishesDeterministicSingleAreaDeliveryWithoutChangingClassicOutput() throws IOException {
    EngineeringDiagramReferenceFixtures.SystemCase fixture = EngineeringDiagramReferenceFixtures.simpleTrain();
    ProcessSystem processSystem = fixture.getProcessSystem();
    String classicDot = processSystem.toDOT();
    EngineeringDiagramDelivery.Request request = EngineeringDiagramDelivery.Request
        .builder("PLANT-10", "A", "PFD-10-001", "Single-area delivery", ContentProfile.PFD).build();

    EngineeringDiagramDelivery.Report first = EngineeringDiagramDelivery.deliver(processSystem,
        temporaryDirectory.resolve("single-first"), request);
    EngineeringDiagramDelivery.Report second = EngineeringDiagramDelivery.deliver(processSystem,
        temporaryDirectory.resolve("single-second"), request);

    assertTrue(first.isComplete(), first.toJson());
    assertEquals(first.toJson(), second.toJson());
    assertEquals(first.getFingerprint(), second.getFingerprint());
    assertEquals(first.getDocumentSet().getSourceGraphFingerprint(), first.toMap().get("sourceGraphFingerprint"));
    assertEquals("REVIEW_REQUIRED", first.toMap().get("approvalStatus"));
    assertEquals(Boolean.FALSE, first.toMap().get("fitnessForConstruction"));
    assertEquals(Boolean.FALSE, first.toMap().get("iso10628ConformanceClaimed"));
    assertTrue(Files.isRegularFile(first.getDirectory().resolve("document-set.json")));
    assertTrue(Files.isRegularFile(first.getDirectory().resolve("drawing-set.pdf")));
    assertTrue(Files.isRegularFile(first.getDirectory().resolve("dexpi-process.xml")));
    assertTrue(Files.isRegularFile(first.getDirectory().resolve("delivery-manifest.json")));
    assertTrue(first.getArtifacts().containsKey("dexpi-process.xml"));
    assertEquals(classicDot, processSystem.toDOT());

    for (Map.Entry<String, EngineeringDiagramDelivery.Artifact> artifact : first.getArtifacts().entrySet()) {
      assertArrayEquals(Files.readAllBytes(first.getDirectory().resolve(artifact.getKey())),
          Files.readAllBytes(second.getDirectory().resolve(artifact.getKey())));
      assertEquals(64, artifact.getValue().getSha256().length());
      assertTrue(artifact.getValue().getSizeBytes() > 0L);
    }
  }

  @Test
  void publishesMultiAreaDeliveryWithOffPageAndExplicitLossEvidence() throws IOException {
    EngineeringDiagramReferenceFixtures.ModelCase fixture = EngineeringDiagramReferenceFixtures.multiAreaFacility();
    ProcessModel processModel = fixture.getProcessModel();
    processModel.get("Inlet").connect("30-XV-001", "energyOut", "30-VA-001", "energyIn",
        ProcessConnection.ConnectionType.ENERGY);
    processModel.get("Inlet").connect("30-VA-001", "signalOut", "30-SP-001", "signalIn",
        ProcessConnection.ConnectionType.SIGNAL);
    EngineeringDiagramDelivery.Request request = EngineeringDiagramDelivery.Request
        .builder("PLANT-30", "B", "PFD-30-001", "Multi-area delivery", ContentProfile.PFD)
        .sheetFormat(NativeEngineeringDiagramRenderer.SheetFormat.A1_LANDSCAPE).build();

    EngineeringDiagramDelivery.Report report = EngineeringDiagramDelivery.deliver(processModel,
        temporaryDirectory.resolve("multi-area"), request);
    String manifest = new String(Files.readAllBytes(report.getDirectory().resolve("delivery-manifest.json")),
        StandardCharsets.UTF_8);

    assertTrue(report.isComplete(), report.toJson());
    assertEquals(4, report.getRendering().getSvgBySheetId().size());
    assertEquals(4, report.getRendering().getVisualFingerprintsBySheetId().size());
    assertTrue(report.getArtifacts().containsKey("dexpi-process-model.zip"));
    assertEquals("application/zip", report.getArtifacts().get("dexpi-process-model.zip").getMediaType());
    assertTrue(manifest.contains("\"sourceScope\": \"PROCESS_MODEL\""));
    assertTrue(manifest.contains("DEXPI_PROCESS_PACKAGE_CROSS_AREA_CONNECTION_MANIFEST_ONLY"));
    assertTrue(manifest.contains("DEXPI_PROCESS_PACKAGE_ENERGY_CONNECTION_MANIFEST_ONLY"));
    assertTrue(manifest.contains("DEXPI_PROCESS_PACKAGE_SIGNAL_CONNECTION_MANIFEST_ONLY"));
    assertTrue(manifest.contains("\"nativeWholePlantDexpiExchange\": false"));
    assertTrue(manifest.contains("\"approvalStatus\": \"REVIEW_REQUIRED\""));
    assertTrue(manifest.contains("\"fitnessForConstruction\": false"));
    assertEquals(report.toJson(), manifest);
    assertFalse(report.getDocumentSet().getDrawings().get(0).getSheets().get(0).getId().isEmpty());
    assertTrue(report.getDocumentSet().getDrawings().get(0).getSheets().stream()
        .anyMatch(sheet -> !sheet.getOffPageConnectors().isEmpty()));
    assertTrue(report.getArtifacts().keySet().toString().contains("svg/"));
  }

  @Test
  void refusesToOverwriteAnExistingDeliveryDirectory() throws IOException {
    Path existing = Files.createDirectory(temporaryDirectory.resolve("existing"));
    EngineeringDiagramDelivery.Request request = EngineeringDiagramDelivery.Request
        .builder("PLANT-10", "A", "PFD-10-001", "Single-area delivery", ContentProfile.PFD).build();

    assertThrows(IllegalArgumentException.class, () -> EngineeringDiagramDelivery
        .deliver(EngineeringDiagramReferenceFixtures.simpleTrain().getProcessSystem(), existing, request));
  }

  @Test
  void keepsPidViewSeparateFromDexpiProcessExchange() throws IOException {
    EngineeringDiagramDelivery.Request request = EngineeringDiagramDelivery.Request
        .builder("PLANT-10", "A", "PID-10-001", "P&ID proposal delivery", ContentProfile.PID).build();

    EngineeringDiagramDelivery.Report report = EngineeringDiagramDelivery.deliver(
        EngineeringDiagramReferenceFixtures.simpleTrain().getProcessSystem(), temporaryDirectory.resolve("pid"),
        request);

    assertTrue(report.isComplete(), report.toJson());
    assertTrue(report.toJson().contains("\"contentProfiles\": [\n    \"PID\""));
    assertTrue(report.toJson().contains("\"dexpiInformationModel\": \"PROCESS_PFD_BFD\""));
    assertTrue(report.toJson().contains("DELIVERY_PID_VIEW_NOT_DEXPI_PLANT_EXCHANGE"));
    assertTrue(report.toJson().contains("does not replace the existing Plant/Proteus"));
  }
}
