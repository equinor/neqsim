package neqsim.process.processmodel.diagram;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import neqsim.process.engineering.model.EngineeringDiagramDocumentSet.ContentProfile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EngineeringDiagramDeliveryAssessmentTest {
  @TempDir Path temporaryDirectory;

  @Test
  void independentlyVerifiesSingleAreaDeliveryAndRestartEvidence()
      throws IOException, ClassNotFoundException {
    Path delivery = deliverSystem("single");

    EngineeringDiagramDeliveryAssessment.Report first =
        EngineeringDiagramDeliveryAssessment.assess(delivery);
    EngineeringDiagramDeliveryAssessment.Report second =
        EngineeringDiagramDeliveryAssessment.assess(delivery);

    assertTrue(first.isComplete(), first.toJson());
    assertEquals(first.toJson(), second.toJson());
    assertEquals("PROCESS_SYSTEM", first.getSourceScope());
    assertEquals("PLANT-10", first.getPlantId());
    assertEquals("REV-A", first.getRevision());
    assertEquals(64, first.getManifestSha256().length());
    assertEquals(64, first.getManifestFingerprint().length());
    assertEquals(64, first.getFingerprint().length());
    assertTrue(first.getArtifacts().containsKey("document-set.json"));
    assertTrue(first.getArtifacts().containsKey("drawing-set.pdf"));
    assertTrue(first.getArtifacts().containsKey("dexpi-process.xml"));
    assertTrue(first.getArtifacts().keySet().toString().contains("svg/"));
    assertTrue(first.toJson().contains("DELIVERY_INTEGRITY_VERIFIED"));
    assertTrue(first.toJson().contains("\"approvalStatus\": \"REVIEW_REQUIRED\""));
    assertTrue(first.toJson().contains("\"fitnessForConstruction\": false"));

    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    ObjectOutputStream output = new ObjectOutputStream(bytes);
    try {
      output.writeObject(first);
    } finally {
      output.close();
    }
    ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()));
    EngineeringDiagramDeliveryAssessment.Report restored;
    try {
      restored = (EngineeringDiagramDeliveryAssessment.Report) input.readObject();
    } finally {
      input.close();
    }
    assertEquals(first.toJson(), restored.toJson());
    assertThrows(UnsupportedOperationException.class, () -> restored.getArtifacts().clear());
    assertThrows(UnsupportedOperationException.class, () -> restored.getDiagnostics().clear());
  }

  @Test
  void verifiesMultiAreaDeliveryWithoutPromotingWholePlantDexpi() throws IOException {
    Path delivery = deliverModel("multi");

    EngineeringDiagramDeliveryAssessment.Report report =
        EngineeringDiagramDeliveryAssessment.assess(delivery);

    assertTrue(report.isComplete(), report.toJson());
    assertEquals("PROCESS_MODEL", report.getSourceScope());
    assertTrue(report.getArtifacts().containsKey("dexpi-process-model.zip"));
    assertFalse(report.getArtifacts().containsKey("dexpi-process.xml"));
    assertEquals(
        "application/zip",
        report.getArtifacts().get("dexpi-process-model.zip").getMediaType());
  }

  @Test
  void detectsArtifactTamperingWithoutChangingManifestEvidence() throws IOException {
    Path delivery = deliverSystem("tampered");
    Path pdf = delivery.resolve("drawing-set.pdf");
    Files.write(pdf, new byte[] {1}, StandardOpenOption.APPEND);

    EngineeringDiagramDeliveryAssessment.Report report =
        EngineeringDiagramDeliveryAssessment.assess(delivery);

    assertFalse(report.isComplete());
    assertTrue(report.toJson().contains("DELIVERY_ARTIFACT_SIZE_MISMATCH"));
    assertTrue(report.toJson().contains("DELIVERY_ARTIFACT_SHA256_MISMATCH"));
    assertNotEquals(
        report.getArtifacts().get("drawing-set.pdf").getSha256(),
        manifestArtifactSha256(delivery, "drawing-set.pdf"));
  }

  @Test
  void rejectsUnlistedFilesAndUnsafeManifestPaths() throws IOException {
    Path extraDelivery = deliverSystem("extra");
    Files.write(extraDelivery.resolve("unreviewed.txt"),
        "not declared".getBytes(StandardCharsets.UTF_8));

    EngineeringDiagramDeliveryAssessment.Report extra =
        EngineeringDiagramDeliveryAssessment.assess(extraDelivery);
    assertFalse(extra.isComplete());
    assertTrue(extra.toJson().contains("DELIVERY_UNLISTED_FILE"));

    Path unsafeDelivery = deliverSystem("unsafe");
    Path manifestPath = unsafeDelivery.resolve("delivery-manifest.json");
    JsonObject manifest = parseManifest(unsafeDelivery);
    manifest.getAsJsonArray("artifacts")
        .get(0)
        .getAsJsonObject()
        .addProperty("relativePath", "../escaped.json");
    Files.write(
        manifestPath,
        new GsonBuilder().setPrettyPrinting().create().toJson(manifest)
            .getBytes(StandardCharsets.UTF_8));

    EngineeringDiagramDeliveryAssessment.Report unsafe =
        EngineeringDiagramDeliveryAssessment.assess(unsafeDelivery);
    assertFalse(unsafe.isComplete());
    assertTrue(unsafe.toJson().contains("DELIVERY_ARTIFACT_PATH_UNSAFE"));
    assertTrue(unsafe.toJson().contains("DELIVERY_MANIFEST_FINGERPRINT_MISMATCH"));
  }

  @Test
  void rejectsPromotedQualificationBoundaryAndMissingManifest() throws IOException {
    Path promotedDelivery = deliverSystem("promoted");
    Path manifestPath = promotedDelivery.resolve("delivery-manifest.json");
    JsonObject manifest = parseManifest(promotedDelivery);
    manifest.addProperty("fitnessForConstruction", true);
    Files.write(
        manifestPath,
        new GsonBuilder().setPrettyPrinting().create().toJson(manifest)
            .getBytes(StandardCharsets.UTF_8));

    EngineeringDiagramDeliveryAssessment.Report promoted =
        EngineeringDiagramDeliveryAssessment.assess(promotedDelivery);
    assertFalse(promoted.isComplete());
    assertTrue(promoted.toJson().contains("DELIVERY_QUALIFICATION_BOUNDARY_INVALID"));

    Path missingDelivery = deliverSystem("missing");
    Files.delete(missingDelivery.resolve("delivery-manifest.json"));
    EngineeringDiagramDeliveryAssessment.Report missing =
        EngineeringDiagramDeliveryAssessment.assess(missingDelivery);
    assertFalse(missing.isComplete());
    assertTrue(missing.toJson().contains("DELIVERY_MANIFEST_MISSING"));
    assertThrows(IllegalArgumentException.class,
        () -> EngineeringDiagramDeliveryAssessment.assess(null));
  }

  private Path deliverSystem(String name) throws IOException {
    Path target = temporaryDirectory.resolve(name);
    EngineeringDiagramDelivery.Request request =
        EngineeringDiagramDelivery.Request.builder(
                "PLANT-10", "REV-A", "PFD-10-001", "Assessed delivery", ContentProfile.PFD)
            .build();
    EngineeringDiagramDelivery.deliver(
        EngineeringDiagramReferenceFixtures.simpleTrain().getProcessSystem(), target, request);
    return target;
  }

  private Path deliverModel(String name) throws IOException {
    Path target = temporaryDirectory.resolve(name);
    EngineeringDiagramDelivery.Request request =
        EngineeringDiagramDelivery.Request.builder(
                "PLANT-30", "REV-B", "PFD-30-001", "Assessed facility", ContentProfile.PFD)
            .build();
    EngineeringDiagramDelivery.deliver(
        EngineeringDiagramReferenceFixtures.multiAreaFacility().getProcessModel(), target, request);
    return target;
  }

  private JsonObject parseManifest(Path delivery) throws IOException {
    String json =
        new String(
            Files.readAllBytes(delivery.resolve("delivery-manifest.json")),
            StandardCharsets.UTF_8);
    return new JsonParser().parse(json).getAsJsonObject();
  }

  private String manifestArtifactSha256(Path delivery, String relativePath) throws IOException {
    com.google.gson.JsonArray elements = parseManifest(delivery).getAsJsonArray("artifacts");
    for (int index = 0; index < elements.size(); index++) {
      com.google.gson.JsonElement element = elements.get(index);
      JsonObject artifact = element.getAsJsonObject();
      if (relativePath.equals(artifact.get("relativePath").getAsString())) {
        return artifact.get("sha256").getAsString();
      }
    }
    throw new IllegalArgumentException("Artifact not found: " + relativePath);
  }
}
