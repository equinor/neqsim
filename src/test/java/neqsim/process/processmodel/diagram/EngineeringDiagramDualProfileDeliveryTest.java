package neqsim.process.processmodel.diagram;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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

    assertThrows(IllegalArgumentException.class,
        () -> EngineeringDiagramDualProfileDelivery.deliver(
            EngineeringDiagramReferenceFixtures.simpleTrain().getProcessSystem(), existing, request));
    assertThrows(IllegalArgumentException.class,
        () -> EngineeringDiagramDualProfileDelivery.Request
            .builder(" ", "A", "PFD-10-001", "PID-10-001", "Separation and compression").build());
  }
}
