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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import neqsim.process.processmodel.ProcessConnection;
import neqsim.process.processmodel.diagram.EngineeringDiagramReferenceFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class Dexpi20ProcessModelPackageReaderTest {
  @TempDir
  Path temporaryDirectory;

  @Test
  void readsExactAssessedAreaDocumentsAndConnectionEvidence() throws IOException {
    File packageFile = writeReferencePackage("facility.zip");

    Dexpi20ProcessModelPackageReader.Snapshot first = Dexpi20ProcessModelPackageReader.read(packageFile);
    Dexpi20ProcessModelPackageReader.Snapshot second = Dexpi20ProcessModelPackageReader.read(packageFile);

    assertEquals("PLANT-30", first.getPlantId());
    assertEquals("REV-A", first.getRevision());
    assertEquals(64, first.getPackageFileSha256().length());
    assertEquals(64, first.getManifestSha256().length());
    assertEquals(64, first.getCanonicalFingerprint().length());
    assertEquals("REVIEW_REQUIRED", first.getApprovalStatus());
    assertFalse(first.isFitnessForConstruction());
    assertFalse(first.isNativeWholePlantDexpiExchange());
    assertEquals(4, first.getAreaDocuments().size());
    assertEquals(first.toJson(), second.toJson());

    for (Dexpi20ProcessModelPackageReader.AreaDocument area : first.getAreaDocuments()) {
      assertEquals(64, area.getFileSha256().length());
      assertTrue(area.getByteLength() > 0);
      assertTrue(area.getXmlUtf8().contains("<Model"));
    }

    Set<String> connectionTypes = new LinkedHashSet<String>();
    for (Dexpi20ProcessModelPackageAssessment.ConnectionEvidence connection : first.getConnectionEvidence()) {
      connectionTypes.add(connection.getConnectionType());
      assertFalse(connection.getConnectionId().isEmpty());
      assertFalse(connection.getSourcePort().isEmpty());
      assertFalse(connection.getTargetPort().isEmpty());
    }
    assertTrue(connectionTypes.contains("MATERIAL"));
    assertTrue(connectionTypes.contains("ENERGY"));
    assertTrue(connectionTypes.contains("SIGNAL"));
  }

  @Test
  void returnsDefensiveSerializableRestartSnapshot() throws IOException, ClassNotFoundException {
    Dexpi20ProcessModelPackageReader.Snapshot snapshot = Dexpi20ProcessModelPackageReader
        .read(writeReferencePackage("restart.zip"));
    Dexpi20ProcessModelPackageReader.AreaDocument area = snapshot.getAreaDocuments().get(0);
    byte[] changed = area.getXmlBytes();
    byte originalFirstByte = changed[0];
    changed[0] = (byte) (changed[0] + 1);

    assertEquals(originalFirstByte, area.getXmlBytes()[0]);
    assertNotEquals(changed[0], area.getXmlBytes()[0]);
    assertThrows(UnsupportedOperationException.class,
        () -> snapshot.getAreaDocuments().add(snapshot.getAreaDocuments().get(0)));
    assertThrows(UnsupportedOperationException.class, () -> snapshot.getConnectionEvidence().clear());
    assertThrows(UnsupportedOperationException.class, () -> snapshot.getAssessmentReport().getAreaEvidence().clear());
    assertThrows(UnsupportedOperationException.class, () -> snapshot.getAssessmentReport().getDiagnostics().clear());

    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    ObjectOutputStream output = new ObjectOutputStream(bytes);
    try {
      output.writeObject(snapshot);
    } finally {
      output.close();
    }
    ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()));
    Dexpi20ProcessModelPackageReader.Snapshot restored;
    try {
      restored = (Dexpi20ProcessModelPackageReader.Snapshot) input.readObject();
    } finally {
      input.close();
    }
    assertEquals(snapshot.toJson(), restored.toJson());
    assertEquals(area.getXmlUtf8(), restored.getAreaDocuments().get(0).getXmlUtf8());
  }

  @Test
  void failsClosedBeforeExposingInvalidOrUnsafeArchiveContent() throws IOException {
    Path invalid = temporaryDirectory.resolve("invalid.zip");
    ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(invalid), StandardCharsets.UTF_8);
    try {
      byte[] bytes = "not DEXPI".getBytes(StandardCharsets.UTF_8);
      CRC32 crc = new CRC32();
      crc.update(bytes);
      ZipEntry entry = new ZipEntry("../escaped.xml");
      entry.setMethod(ZipEntry.STORED);
      entry.setSize(bytes.length);
      entry.setCompressedSize(bytes.length);
      entry.setCrc(crc.getValue());
      entry.setTime(0L);
      output.putNextEntry(entry);
      output.write(bytes);
      output.closeEntry();
    } finally {
      output.close();
    }

    Dexpi20ProcessModelPackageReader.InvalidPackageException exception = assertThrows(
        Dexpi20ProcessModelPackageReader.InvalidPackageException.class,
        () -> Dexpi20ProcessModelPackageReader.read(invalid.toFile()));

    assertFalse(exception.getAssessmentReport().isValid());
    assertTrue(exception.getAssessmentReport().toJson().contains("DEXPI_PROCESS_PACKAGE_UNSAFE_ENTRY_NAME"));
    assertFalse(Files.exists(temporaryDirectory.getParent().resolve("escaped.xml")));
    assertThrows(IllegalArgumentException.class, () -> Dexpi20ProcessModelPackageReader.read(null));
  }

  private File writeReferencePackage(String name) throws IOException {
    EngineeringDiagramReferenceFixtures.ModelCase fixture = EngineeringDiagramReferenceFixtures.multiAreaFacility();
    fixture.getProcessModel().get("Inlet").connect("30-XV-001", "energyOut", "30-VA-001", "energyIn",
        ProcessConnection.ConnectionType.ENERGY);
    fixture.getProcessModel().get("Inlet").connect("30-VA-001", "signalOut", "30-SP-001", "signalIn",
        ProcessConnection.ConnectionType.SIGNAL);
    File packageFile = temporaryDirectory.resolve(name).toFile();
    Dexpi20ProcessModelPackageWriter.writeAndAssess(fixture.getProcessModel(), packageFile, "PLANT-30", "REV-A");
    return packageFile;
  }
}
