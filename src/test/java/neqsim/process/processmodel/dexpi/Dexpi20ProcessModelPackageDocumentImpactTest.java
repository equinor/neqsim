package neqsim.process.processmodel.dexpi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Path;
import neqsim.process.engineering.model.EngineeringDiagramDesignationRegister;
import neqsim.process.engineering.model.EngineeringDiagramDesignationRegister.Designation;
import neqsim.process.engineering.model.EngineeringDiagramDesignationRegister.Kind;
import neqsim.process.engineering.model.EngineeringDiagramDesignationRegister.ReviewState;
import neqsim.process.engineering.model.EngineeringDiagramDocumentSet;
import neqsim.process.engineering.model.EngineeringDiagramDocumentSet.ContentProfile;
import neqsim.process.engineering.model.EngineeringDiagramDocumentSet.SemanticObject;
import neqsim.process.engineering.model.EngineeringDiagramLayoutRegister;
import neqsim.process.engineering.model.EngineeringDiagramLayoutRegister.CoordinateUnit;
import neqsim.process.engineering.model.EngineeringDiagramLayoutRegister.EvidenceState;
import neqsim.process.engineering.model.EngineeringDiagramLayoutRegister.PinnedPosition;
import neqsim.process.engineering.model.EngineeringNode;
import neqsim.process.processmodel.ProcessConnection;
import neqsim.process.processmodel.diagram.EngineeringDiagramReferenceFixtures;
import neqsim.process.processmodel.diagram.ProcessDiagramDocumentSetAdapter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class Dexpi20ProcessModelPackageDocumentImpactTest {
  @TempDir
  Path temporaryDirectory;

  @Test
  void unchangedArtifactsProduceDeterministicEmptyProjection() throws IOException {
    EngineeringDiagramReferenceFixtures.ModelCase fixture = referenceFixture();
    Dexpi20ProcessModelPackageReader.Snapshot snapshot = writeAndRead(fixture, "same.zip", "PLANT-40", "A");
    EngineeringDiagramDocumentSet document = document(fixture, "A");
    Dexpi20ProcessModelPackageRevisionImpact packageImpact = Dexpi20ProcessModelPackageRevisionImpact.compare(snapshot,
        snapshot);

    Dexpi20ProcessModelPackageDocumentImpact first = Dexpi20ProcessModelPackageDocumentImpact.project(packageImpact,
        document, document);
    Dexpi20ProcessModelPackageDocumentImpact second = Dexpi20ProcessModelPackageDocumentImpact.project(packageImpact,
        document, document);

    assertEquals(Dexpi20ProcessModelPackageDocumentImpact.Status.UNCHANGED, first.getStatus());
    assertTrue(first.isProjectionComplete());
    assertTrue(first.getReviewScopes().isEmpty());
    assertTrue(first.getAffectedSheetIds().isEmpty());
    assertEquals(first.toJson(), second.toJson());
    assertEquals(64, String.valueOf(first.toMap().get("fingerprint")).length());
    assertFalse(first.isFitnessForConstruction());
    assertFalse(first.isNativeWholePlantDexpiExchange());
  }

  @Test
  void revisionOnlyPackageChangeDoesNotInventSheetImpact() throws IOException {
    EngineeringDiagramReferenceFixtures.ModelCase fixture = referenceFixture();
    Dexpi20ProcessModelPackageReader.Snapshot baseline = writeAndRead(fixture, "revision-a.zip", "PLANT-40", "A");
    Dexpi20ProcessModelPackageReader.Snapshot revised = writeAndRead(fixture, "revision-b.zip", "PLANT-40", "B");

    Dexpi20ProcessModelPackageDocumentImpact impact = Dexpi20ProcessModelPackageDocumentImpact.project(
        Dexpi20ProcessModelPackageRevisionImpact.compare(baseline, revised), document(fixture, "A"),
        document(fixture, "B"));

    assertEquals(Dexpi20ProcessModelPackageDocumentImpact.Status.REVIEW_REQUIRED, impact.getStatus(), impact.toJson());
    assertTrue(impact.isProjectionComplete());
    assertTrue(impact.getChangedAreaIds().isEmpty());
    assertTrue(impact.getChangedConnectionIds().isEmpty());
    assertTrue(impact.getAffectedSheetIds().isEmpty());
    assertTrue(impact.getReviewScopes().contains(Dexpi20ProcessModelPackageDocumentImpact.ReviewScope.DEXPI_PACKAGE));
    assertTrue(impact.getReviewScopes()
        .contains(Dexpi20ProcessModelPackageDocumentImpact.ReviewScope.CONTROLLED_DOCUMENT_SET));
  }

  @Test
  void addedSignalProjectsToControlledViewsAndInformationRegister() throws IOException {
    EngineeringDiagramReferenceFixtures.ModelCase fixture = referenceFixture();
    Dexpi20ProcessModelPackageReader.Snapshot baseline = writeAndRead(fixture, "signal-a.zip", "PLANT-40", "A");
    EngineeringDiagramDocumentSet baselineDocument = document(fixture, "A");

    fixture.getProcessModel().get("Inlet").connect("30-XV-001", "signalOut2", "30-SP-001", "signalIn2",
        ProcessConnection.ConnectionType.SIGNAL);
    Dexpi20ProcessModelPackageReader.Snapshot revised = writeAndRead(fixture, "signal-b.zip", "PLANT-40", "B");
    EngineeringDiagramDocumentSet revisedDocument = document(fixture, "B");

    Dexpi20ProcessModelPackageDocumentImpact impact = Dexpi20ProcessModelPackageDocumentImpact.project(
        Dexpi20ProcessModelPackageRevisionImpact.compare(baseline, revised), baselineDocument, revisedDocument);

    assertEquals(Dexpi20ProcessModelPackageDocumentImpact.Status.REVIEW_REQUIRED, impact.getStatus(), impact.toJson());
    assertEquals(1, impact.getChangedConnectionIds().size());
    assertTrue(impact.getUnmatchedConnectionIds().isEmpty());
    assertFalse(impact.getAffectedSheetIds().isEmpty());
    assertFalse(impact.getAffectedDrawingIds().isEmpty());
    assertTrue(
        impact.getReviewScopes().contains(Dexpi20ProcessModelPackageDocumentImpact.ReviewScope.INFORMATION_REGISTER));
    assertTrue(
        impact.getReviewScopes().contains(Dexpi20ProcessModelPackageDocumentImpact.ReviewScope.CONTROLLED_SHEET));
  }

  @Test
  void unmatchedPackageIdentityFailsClosed() throws IOException {
    EngineeringDiagramReferenceFixtures.ModelCase fixture = referenceFixture();
    Dexpi20ProcessModelPackageReader.Snapshot baseline = writeAndRead(fixture, "missing-a.zip", "PLANT-40", "A");
    EngineeringDiagramDocumentSet unchangedDocument = document(fixture, "A");
    fixture.getProcessModel().get("Inlet").connect("30-XV-001", "signalOut2", "30-SP-001", "signalIn2",
        ProcessConnection.ConnectionType.SIGNAL);
    Dexpi20ProcessModelPackageReader.Snapshot revised = writeAndRead(fixture, "missing-b.zip", "PLANT-40", "B");

    Dexpi20ProcessModelPackageDocumentImpact impact = Dexpi20ProcessModelPackageDocumentImpact.project(
        Dexpi20ProcessModelPackageRevisionImpact.compare(baseline, revised), unchangedDocument, unchangedDocument);

    assertEquals(Dexpi20ProcessModelPackageDocumentImpact.Status.INCOMPLETE, impact.getStatus());
    assertFalse(impact.isProjectionComplete());
    assertEquals(1, impact.getUnmatchedConnectionIds().size());
    assertTrue(impact.getAffectedSheetIds().isEmpty());
  }

  @Test
  void projectsDesignationAndLayoutChangesAndSurvivesRestart() throws IOException, ClassNotFoundException {
    EngineeringDiagramReferenceFixtures.ModelCase fixture = referenceFixture();
    Dexpi20ProcessModelPackageReader.Snapshot snapshot = writeAndRead(fixture, "register.zip", "PLANT-40", "A");
    Dexpi20ProcessModelPackageRevisionImpact packageImpact = Dexpi20ProcessModelPackageRevisionImpact.compare(snapshot,
        snapshot);
    EngineeringDiagramDocumentSet baseline = document(fixture, "A");
    SemanticObject equipment = firstEquipment(baseline);
    String sheetKey = baseline.getDrawings().get(0).getSheets().get(0).getKey();
    EngineeringDiagramDesignationRegister designations = new EngineeringDiagramDesignationRegister().withDesignation(
        new Designation(equipment.getId(), Kind.EQUIPMENT_TAG, "40-V-001", "project-register:diagram-designations",
            ReviewState.REVIEWED, "Process discipline", "review:40", "2026-08-27T08:00:00Z", "B"));
    EngineeringDiagramLayoutRegister layout = new EngineeringDiagramLayoutRegister()
        .withPinnedPosition(new PinnedPosition(equipment.getId(), sheetKey, 75.0, 45.0, CoordinateUnit.MILLIMETRE,
            "project-layout:PFD-40-001", EvidenceState.REVIEWED, "Process discipline", "2026-08-27T08:00:00Z", "B"));
    EngineeringDiagramDocumentSet revised = ProcessDiagramDocumentSetAdapter.fromProcessModel(fixture.getProcessModel(),
        "PLANT-40", "A", "PFD-40-001", "Package document impact", ContentProfile.PID, designations, layout);

    Dexpi20ProcessModelPackageDocumentImpact impact = Dexpi20ProcessModelPackageDocumentImpact.project(packageImpact,
        baseline, revised);

    assertEquals(Dexpi20ProcessModelPackageDocumentImpact.Status.REVIEW_REQUIRED, impact.getStatus());
    assertTrue(impact.getAffectedDesignationObjectIds().contains(equipment.getId()));
    assertFalse(impact.getAffectedLayoutSheetIds().isEmpty());
    assertTrue(
        impact.getReviewScopes().contains(Dexpi20ProcessModelPackageDocumentImpact.ReviewScope.DESIGNATION_REGISTER));
    assertTrue(impact.getReviewScopes().contains(Dexpi20ProcessModelPackageDocumentImpact.ReviewScope.LAYOUT_REGISTER));
    assertThrows(UnsupportedOperationException.class, () -> impact.getReviewScopes().clear());

    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    ObjectOutputStream output = new ObjectOutputStream(bytes);
    try {
      output.writeObject(impact);
    } finally {
      output.close();
    }
    ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()));
    try {
      assertEquals(impact.toJson(), ((Dexpi20ProcessModelPackageDocumentImpact) input.readObject()).toJson());
    } finally {
      input.close();
    }
  }

  @Test
  void rejectsMismatchedPlantOrMissingInput() throws IOException {
    EngineeringDiagramReferenceFixtures.ModelCase fixture = referenceFixture();
    Dexpi20ProcessModelPackageReader.Snapshot snapshot = writeAndRead(fixture, "plant.zip", "PLANT-40", "A");
    Dexpi20ProcessModelPackageRevisionImpact packageImpact = Dexpi20ProcessModelPackageRevisionImpact.compare(snapshot,
        snapshot);
    EngineeringDiagramDocumentSet matching = document(fixture, "A");
    EngineeringDiagramDocumentSet other = ProcessDiagramDocumentSetAdapter.fromProcessModel(fixture.getProcessModel(),
        "PLANT-41", "A", "PFD-40-001", "Package document impact", ContentProfile.PID);

    assertThrows(IllegalArgumentException.class,
        () -> Dexpi20ProcessModelPackageDocumentImpact.project(packageImpact, matching, other));
    assertThrows(IllegalArgumentException.class,
        () -> Dexpi20ProcessModelPackageDocumentImpact.project(null, matching, matching));
  }

  private EngineeringDiagramReferenceFixtures.ModelCase referenceFixture() {
    EngineeringDiagramReferenceFixtures.ModelCase fixture = EngineeringDiagramReferenceFixtures.multiAreaFacility();
    fixture.getProcessModel().get("Inlet").connect("30-XV-001", "energyOut", "30-VA-001", "energyIn",
        ProcessConnection.ConnectionType.ENERGY);
    fixture.getProcessModel().get("Inlet").connect("30-VA-001", "signalOut", "30-SP-001", "signalIn",
        ProcessConnection.ConnectionType.SIGNAL);
    return fixture;
  }

  private EngineeringDiagramDocumentSet document(EngineeringDiagramReferenceFixtures.ModelCase fixture,
      String revision) {
    return ProcessDiagramDocumentSetAdapter.fromProcessModel(fixture.getProcessModel(), "PLANT-40", revision,
        "PFD-40-001", "Package document impact", ContentProfile.PID);
  }

  private Dexpi20ProcessModelPackageReader.Snapshot writeAndRead(EngineeringDiagramReferenceFixtures.ModelCase fixture,
      String fileName, String plantId, String revision) throws IOException {
    File packageFile = temporaryDirectory.resolve(fileName).toFile();
    Dexpi20ProcessModelPackageWriter.writeAndAssess(fixture.getProcessModel(), packageFile, plantId, revision);
    return Dexpi20ProcessModelPackageReader.read(packageFile);
  }

  private static SemanticObject firstEquipment(EngineeringDiagramDocumentSet document) {
    for (SemanticObject object : document.getSemanticObjects()) {
      if (object.getKind() == EngineeringNode.Kind.EQUIPMENT) {
        return object;
      }
    }
    throw new AssertionError("Missing equipment semantic object");
  }
}
