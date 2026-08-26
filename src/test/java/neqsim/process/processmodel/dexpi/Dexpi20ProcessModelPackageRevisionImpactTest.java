package neqsim.process.processmodel.dexpi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Path;
import neqsim.process.processmodel.ProcessConnection;
import neqsim.process.processmodel.diagram.EngineeringDiagramReferenceFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class Dexpi20ProcessModelPackageRevisionImpactTest {
  @TempDir
  Path temporaryDirectory;

  @Test
  void reportsIdenticalSnapshotAsUnchangedAndDeterministic() throws IOException {
    EngineeringDiagramReferenceFixtures.ModelCase fixture = referenceFixture();
    Dexpi20ProcessModelPackageReader.Snapshot snapshot = writeAndRead(fixture, "same.zip", "PLANT-30", "REV-A");

    Dexpi20ProcessModelPackageRevisionImpact first = Dexpi20ProcessModelPackageRevisionImpact.compare(snapshot,
        snapshot);
    Dexpi20ProcessModelPackageRevisionImpact second = Dexpi20ProcessModelPackageRevisionImpact.compare(snapshot,
        snapshot);

    assertEquals(Dexpi20ProcessModelPackageRevisionImpact.Status.UNCHANGED, first.getStatus());
    assertTrue(first.isExactPackageMatch());
    assertTrue(first.isAreaIdentitySetEquivalent());
    assertTrue(first.isConnectionTopologyEquivalent());
    assertEquals(0, first.getChangedAreaCount());
    assertEquals(0, first.getChangedConnectionCount());
    assertEquals(first.toJson(), second.toJson());
    assertEquals(64, String.valueOf(first.toMap().get("fingerprint")).length());
    assertEquals("REVIEW_REQUIRED", first.getApprovalStatus());
    assertTrue(first.isEngineeringReviewRequired());
    assertFalse(first.isFitnessForConstruction());
    assertFalse(first.isNativeWholePlantDexpiExchange());
  }

  @Test
  void separatesDocumentRevisionFromConnectionTopology() throws IOException {
    EngineeringDiagramReferenceFixtures.ModelCase fixture = referenceFixture();
    Dexpi20ProcessModelPackageReader.Snapshot baseline = writeAndRead(fixture, "revision-a.zip", "PLANT-30", "REV-A");
    Dexpi20ProcessModelPackageReader.Snapshot revised = writeAndRead(fixture, "revision-b.zip", "PLANT-30", "REV-B");

    Dexpi20ProcessModelPackageRevisionImpact impact = Dexpi20ProcessModelPackageRevisionImpact.compare(baseline,
        revised);

    assertEquals(Dexpi20ProcessModelPackageRevisionImpact.Status.CHANGED, impact.getStatus());
    assertEquals("REV-A", impact.getBaselineRevision());
    assertEquals("REV-B", impact.getRevisedRevision());
    assertFalse(impact.isExactPackageMatch());
    assertTrue(impact.isAreaIdentitySetEquivalent());
    assertTrue(impact.isConnectionTopologyEquivalent());
    assertEquals(4, impact.getChangedAreaCount());
    assertEquals(0, impact.getChangedConnectionCount());
    for (Dexpi20ProcessModelPackageRevisionImpact.AreaChange change : impact.getAreaChanges()) {
      assertEquals(Dexpi20ProcessModelPackageRevisionImpact.ChangeType.MODIFIED, change.getChangeType());
      assertEquals(change.getBaseline().getAreaId(), change.getRevised().getAreaId());
      assertNotEquals(change.getBaseline().getFileSha256(), change.getRevised().getFileSha256());
    }
  }

  @Test
  void detectsAddedSignalConnectionWithStableEvidence() throws IOException {
    EngineeringDiagramReferenceFixtures.ModelCase fixture = referenceFixture();
    Dexpi20ProcessModelPackageReader.Snapshot baseline = writeAndRead(fixture, "connection-a.zip", "PLANT-30", "REV-A");

    fixture.getProcessModel().get("Inlet").connect("30-XV-001", "signalOut2", "30-SP-001", "signalIn2",
        ProcessConnection.ConnectionType.SIGNAL);
    Dexpi20ProcessModelPackageReader.Snapshot revised = writeAndRead(fixture, "connection-b.zip", "PLANT-30", "REV-B");

    Dexpi20ProcessModelPackageRevisionImpact impact = Dexpi20ProcessModelPackageRevisionImpact.compare(baseline,
        revised);

    assertFalse(impact.isConnectionTopologyEquivalent());
    assertEquals(1, impact.getChangedConnectionCount());
    int addedSignalConnections = 0;
    for (Dexpi20ProcessModelPackageRevisionImpact.ConnectionChange change : impact.getConnectionChanges()) {
      if (change.getChangeType() == Dexpi20ProcessModelPackageRevisionImpact.ChangeType.ADDED) {
        addedSignalConnections++;
        assertEquals("SIGNAL", change.getRevised().getConnectionType());
        assertEquals("signalOut2", change.getRevised().getSourcePort());
        assertEquals("signalIn2", change.getRevised().getTargetPort());
        assertEquals("MANIFEST_ONLY_NOT_MAPPED_TO_NATIVE_PROCESS", change.getRevised().getExchangeStatus());
      }
    }
    assertEquals(1, addedSignalConnections);
  }

  @Test
  void remainsSerializableDefensiveAndPlantScoped() throws IOException, ClassNotFoundException {
    EngineeringDiagramReferenceFixtures.ModelCase fixture = referenceFixture();
    Dexpi20ProcessModelPackageReader.Snapshot baseline = writeAndRead(fixture, "restart-a.zip", "PLANT-30", "REV-A");
    Dexpi20ProcessModelPackageReader.Snapshot revised = writeAndRead(fixture, "restart-b.zip", "PLANT-30", "REV-B");
    Dexpi20ProcessModelPackageRevisionImpact impact = Dexpi20ProcessModelPackageRevisionImpact.compare(baseline,
        revised);

    assertThrows(UnsupportedOperationException.class, () -> impact.getAreaChanges().clear());
    assertThrows(UnsupportedOperationException.class, () -> impact.getConnectionChanges().clear());

    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    ObjectOutputStream output = new ObjectOutputStream(bytes);
    try {
      output.writeObject(impact);
    } finally {
      output.close();
    }
    ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()));
    Dexpi20ProcessModelPackageRevisionImpact restored;
    try {
      restored = (Dexpi20ProcessModelPackageRevisionImpact) input.readObject();
    } finally {
      input.close();
    }
    assertEquals(impact.toJson(), restored.toJson());

    EngineeringDiagramReferenceFixtures.ModelCase otherFixture = referenceFixture();
    Dexpi20ProcessModelPackageReader.Snapshot otherPlant = writeAndRead(otherFixture, "other-plant.zip", "PLANT-31",
        "REV-B");
    assertThrows(IllegalArgumentException.class,
        () -> Dexpi20ProcessModelPackageRevisionImpact.compare(baseline, otherPlant));
    assertThrows(IllegalArgumentException.class, () -> Dexpi20ProcessModelPackageRevisionImpact.compare(null, revised));
  }

  private EngineeringDiagramReferenceFixtures.ModelCase referenceFixture() {
    EngineeringDiagramReferenceFixtures.ModelCase fixture = EngineeringDiagramReferenceFixtures.multiAreaFacility();
    fixture.getProcessModel().get("Inlet").connect("30-XV-001", "energyOut", "30-VA-001", "energyIn",
        ProcessConnection.ConnectionType.ENERGY);
    fixture.getProcessModel().get("Inlet").connect("30-VA-001", "signalOut", "30-SP-001", "signalIn",
        ProcessConnection.ConnectionType.SIGNAL);
    return fixture;
  }

  private Dexpi20ProcessModelPackageReader.Snapshot writeAndRead(EngineeringDiagramReferenceFixtures.ModelCase fixture,
      String fileName, String plantId, String revision) throws IOException {
    File packageFile = temporaryDirectory.resolve(fileName).toFile();
    Dexpi20ProcessModelPackageWriter.writeAndAssess(fixture.getProcessModel(), packageFile, plantId, revision);
    return Dexpi20ProcessModelPackageReader.read(packageFile);
  }
}
