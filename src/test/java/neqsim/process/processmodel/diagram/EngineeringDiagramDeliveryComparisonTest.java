package neqsim.process.processmodel.diagram;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import neqsim.process.engineering.model.EngineeringDiagramDocumentSet.ContentProfile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EngineeringDiagramDeliveryComparisonTest {
  @TempDir
  Path temporaryDirectory;

  @Test
  void identicalTransferredCopiesProduceStableRestartableEvidence() throws IOException, ClassNotFoundException {
    Path firstDirectory = deliver("copy-a", "PLANT-CMP", "A", "Comparison case");
    Path secondDirectory = deliver("copy-b", "PLANT-CMP", "A", "Comparison case");

    EngineeringDiagramDeliveryComparison.Report first = EngineeringDiagramDeliveryComparison.compare(firstDirectory,
        secondDirectory);
    EngineeringDiagramDeliveryComparison.Report second = EngineeringDiagramDeliveryComparison.compare(secondDirectory,
        firstDirectory);

    assertTrue(first.isComplete(), first.toJson());
    assertEquals(EngineeringDiagramDeliveryComparison.Status.IDENTICAL, first.getStatus());
    assertTrue(first.getChangedArtifactPaths().isEmpty());
    assertTrue(first.getReviewScopes().isEmpty());
    assertEquals(first.getFingerprint(), second.getFingerprint());
    assertTrue(first.toJson().contains("DELIVERY_COMPARISON_IDENTICAL"));
    assertTrue(first.toJson().contains("\"engineeringReviewRequired\": false"));

    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    ObjectOutputStream output = new ObjectOutputStream(bytes);
    try {
      output.writeObject(first);
    } finally {
      output.close();
    }
    ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()));
    EngineeringDiagramDeliveryComparison.Report restored;
    try {
      restored = (EngineeringDiagramDeliveryComparison.Report) input.readObject();
    } finally {
      input.close();
    }
    assertEquals(first.toJson(), restored.toJson());
    assertThrows(UnsupportedOperationException.class, () -> restored.getArtifactChanges().clear());
    assertThrows(UnsupportedOperationException.class, () -> restored.getChangedArtifactPaths().clear());
    assertThrows(UnsupportedOperationException.class, () -> restored.getReviewScopes().clear());
    assertThrows(UnsupportedOperationException.class, () -> restored.getDiagnostics().clear());
  }

  @Test
  void changedRevisionClassifiesArtifactsAndReviewScopes() throws IOException {
    Path baseline = deliver("revision-a", "PLANT-CMP", "A", "Comparison case");
    Path revised = deliver("revision-b", "PLANT-CMP", "B", "Comparison case");

    EngineeringDiagramDeliveryComparison.Report report = EngineeringDiagramDeliveryComparison.compare(baseline,
        revised);

    assertTrue(report.isComplete(), report.toJson());
    assertEquals(EngineeringDiagramDeliveryComparison.Status.CHANGED, report.getStatus());
    assertFalse(report.getChangedArtifactPaths().isEmpty());
    assertTrue(report.getChangedArtifactPaths().contains("document-set.json"));
    assertTrue(report.getChangedArtifactPaths().contains("drawing-set.pdf"));
    assertFalse(report.getChangedArtifactPaths().contains("dexpi-process.xml"));
    assertTrue(
        report.getReviewScopes().contains(EngineeringDiagramDeliveryComparison.ReviewScope.CONTROLLED_DOCUMENT_SET));
    assertTrue(report.getReviewScopes().contains(EngineeringDiagramDeliveryComparison.ReviewScope.DELIVERY_MANIFEST));
    assertFalse(
        report.getReviewScopes().contains(EngineeringDiagramDeliveryComparison.ReviewScope.DEXPI_PROCESS_EXCHANGE));
    assertTrue(report.getReviewScopes().contains(EngineeringDiagramDeliveryComparison.ReviewScope.NATIVE_PDF));
    assertTrue(report.getReviewScopes().contains(EngineeringDiagramDeliveryComparison.ReviewScope.NATIVE_SVG));
    assertTrue(report.toJson().contains("DELIVERY_COMPARISON_REVIEW_REQUIRED"));
    assertTrue(report.toJson().contains("\"fitnessForConstruction\": false"));
    assertTrue(report.toJson().contains("\"iso10628ConformanceClaimed\": false"));
  }

  @Test
  void changedContentCannotReuseControlledRevision() throws IOException {
    Path baseline = deliver("title-a", "PLANT-CMP", "A", "First title");
    Path revised = deliver("title-b", "PLANT-CMP", "A", "Changed title");

    EngineeringDiagramDeliveryComparison.Report report = EngineeringDiagramDeliveryComparison.compare(baseline,
        revised);

    assertFalse(report.isComplete());
    assertEquals(EngineeringDiagramDeliveryComparison.Status.REVISION_REUSE, report.getStatus());
    assertTrue(report.toJson().contains("DELIVERY_REVISION_REUSED_WITH_CHANGED_CONTENT"));
    assertTrue(report.getReviewScopes().contains(EngineeringDiagramDeliveryComparison.ReviewScope.ENGINEERING_REVIEW));
  }

  @Test
  void multiAreaRevisionRetainsPackageScopeAndQualificationBoundary() throws IOException {
    Path baseline = deliverModel("model-a", "A");
    Path revised = deliverModel("model-b", "B");

    EngineeringDiagramDeliveryComparison.Report report = EngineeringDiagramDeliveryComparison.compare(baseline,
        revised);

    assertTrue(report.isComplete(), report.toJson());
    assertEquals("PROCESS_MODEL", report.getSourceScope());
    assertEquals(EngineeringDiagramDeliveryComparison.Status.CHANGED, report.getStatus());
    assertTrue(report.getChangedArtifactPaths().contains("dexpi-process-model.zip"));
    assertTrue(report.getReviewScopes()
        .contains(EngineeringDiagramDeliveryComparison.ReviewScope.DEXPI_PROCESS_MODEL_PACKAGE));
    assertTrue(report.isEngineeringReviewRequired());
    assertTrue(report.toJson().contains("Does not repeat DEXPI semantic or external-validator assessment"));
  }

  @Test
  void rejectsDifferentPlantAndIncompleteAssessment() throws IOException {
    Path first = deliver("plant-a", "PLANT-A", "A", "Plant A");
    Path second = deliver("plant-b", "PLANT-B", "B", "Plant B");
    EngineeringDiagramDeliveryAssessment.Report firstAssessment = EngineeringDiagramDeliveryAssessment.assess(first);
    EngineeringDiagramDeliveryAssessment.Report secondAssessment = EngineeringDiagramDeliveryAssessment.assess(second);

    assertThrows(IllegalArgumentException.class,
        () -> EngineeringDiagramDeliveryComparison.compare(firstAssessment, secondAssessment));
    assertThrows(IllegalArgumentException.class,
        () -> EngineeringDiagramDeliveryComparison.compare(null, secondAssessment));

    Path damaged = deliver("damaged", "PLANT-A", "B", "Plant A");
    Files.write(damaged.resolve("drawing-set.pdf"), new byte[] { 1 }, StandardOpenOption.APPEND);
    EngineeringDiagramDeliveryAssessment.Report incomplete = EngineeringDiagramDeliveryAssessment.assess(damaged);
    assertFalse(incomplete.isComplete());
    assertThrows(IllegalArgumentException.class,
        () -> EngineeringDiagramDeliveryComparison.compare(firstAssessment, incomplete));
  }

  private Path deliver(String name, String plantId, String revision, String title) throws IOException {
    Path target = temporaryDirectory.resolve(name);
    EngineeringDiagramDelivery.Request request = EngineeringDiagramDelivery.Request
        .builder(plantId, revision, "PFD-CMP-001", title, ContentProfile.PFD).build();
    EngineeringDiagramDelivery.deliver(EngineeringDiagramReferenceFixtures.simpleTrain().getProcessSystem(), target,
        request);
    return target;
  }

  private Path deliverModel(String name, String revision) throws IOException {
    Path target = temporaryDirectory.resolve(name);
    EngineeringDiagramDelivery.Request request = EngineeringDiagramDelivery.Request
        .builder("PLANT-MODEL", revision, "PFD-MODEL-001", "Multi-area comparison", ContentProfile.PFD).build();
    EngineeringDiagramDelivery.deliver(EngineeringDiagramReferenceFixtures.multiAreaFacility().getProcessModel(),
        target, request);
    return target;
  }
}
