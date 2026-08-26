package neqsim.process.processmodel.dexpi;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import neqsim.process.processmodel.ProcessConnection;
import neqsim.process.processmodel.ProcessModel;
import neqsim.process.processmodel.diagram.EngineeringDiagramReferenceFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class Dexpi20ProcessModelPackageWriterTest {
  @TempDir Path temporaryDirectory;

  @Test
  void writesDeterministicAssessedAreaPackageWithPlantWideConnectionEvidence() throws IOException {
    EngineeringDiagramReferenceFixtures.ModelCase fixture =
        EngineeringDiagramReferenceFixtures.multiAreaFacility();
    fixture.getProcessModel().get("Inlet").connect("30-XV-001", "energyOut", "30-VA-001",
        "energyIn", ProcessConnection.ConnectionType.ENERGY);
    fixture.getProcessModel().get("Inlet").connect("30-VA-001", "signalOut", "30-SP-001",
        "signalIn", ProcessConnection.ConnectionType.SIGNAL);

    File first = temporaryDirectory.resolve("first.zip").toFile();
    File second = temporaryDirectory.resolve("second.zip").toFile();
    Dexpi20ProcessModelPackageWriter.Report firstReport =
        Dexpi20ProcessModelPackageWriter.writeAndAssess(fixture.getProcessModel(), first,
            "PLANT-30", "REV-A");
    Dexpi20ProcessModelPackageWriter.Report secondReport =
        Dexpi20ProcessModelPackageWriter.writeAndAssess(fixture.getProcessModel(), second,
            "PLANT-30", "REV-A");

    assertArrayEquals(Files.readAllBytes(first.toPath()), Files.readAllBytes(second.toPath()));
    assertEquals(firstReport.toJson(), secondReport.toJson());
    assertTrue(firstReport.isComplete(), firstReport.toJson());
    assertFalse(firstReport.isNativeWholePlantDexpiExchange());
    assertEquals("PLANT-30", firstReport.getPlantId());
    assertEquals("REV-A", firstReport.getRevision());
    assertNull(firstReport.getOperatingCaseId());
    assertEquals(4, firstReport.getAreaExchanges().size());
    assertEquals(9, firstReport.getConnections().size());
    assertEquals(64, firstReport.getCanonicalFingerprint().length());
    assertEquals(64, firstReport.getManifestSha256().length());
    assertEquals(64, firstReport.getPackageFileSha256().length());

    for (Dexpi20ProcessModelPackageWriter.AreaExchange area : firstReport.getAreaExchanges()) {
      assertFalse(area.getAreaId().isEmpty());
      assertEquals(64, area.getFileSha256().length());
      assertTrue(area.getAssessment().isSchemaProfileAndSupportedTopologyValid(),
          area.getAssessment().toJson());
    }
    assertEquals(7, connectionCount(firstReport, "MATERIAL"));
    assertEquals(1, connectionCount(firstReport, "ENERGY"));
    assertEquals(1, connectionCount(firstReport, "SIGNAL"));
    assertEquals(3, connectionStatusCount(firstReport, "MANIFEST_ONLY_CROSS_AREA"));
    assertEquals(2,
        connectionStatusCount(firstReport, "MANIFEST_ONLY_NOT_MAPPED_TO_NATIVE_PROCESS"));
    assertTrue(hasDiagnostic(firstReport,
        "DEXPI_PROCESS_PACKAGE_CROSS_AREA_CONNECTION_MANIFEST_ONLY"));
    assertTrue(hasDiagnostic(firstReport, "DEXPI_PROCESS_PACKAGE_ENERGY_CONNECTION_MANIFEST_ONLY"));
    assertTrue(hasDiagnostic(firstReport, "DEXPI_PROCESS_PACKAGE_SIGNAL_CONNECTION_MANIFEST_ONLY"));
    assertTrue(hasDiagnostic(firstReport, "DEXPI_PROCESS_PACKAGE_NOT_NATIVE_WHOLE_PLANT_PROFILE"));

    List<String> entries = zipEntries(first.toPath());
    assertEquals(5, entries.size());
    assertTrue(entries.get(0).startsWith("areas/compression-"));
    assertTrue(entries.get(3).startsWith("areas/inlet-"));
    assertEquals("manifest.json", entries.get(4));
    String manifest = zipEntry(first.toPath(), "manifest.json");
    assertTrue(manifest.contains("\"nativeWholePlantDexpiExchange\": false"));
    assertTrue(manifest.contains("\"dexpiVersion\": \"2.0.0\""));
    assertTrue(manifest.contains("\"dexpiModel\": \"Process\""));
    assertTrue(manifest.contains("\"approvalStatus\": \"REVIEW_REQUIRED\""));
    assertTrue(manifest.contains("\"fitnessForConstruction\": false"));
    assertTrue(manifest.contains("\"canonicalFingerprint\": \""
        + firstReport.getCanonicalFingerprint() + "\""));
  }

  @Test
  void controlledInputsAndAtLeastOneAreaAreRequired() {
    Path output = temporaryDirectory.resolve("invalid.zip");
    assertThrows(IllegalArgumentException.class,
        () -> Dexpi20ProcessModelPackageWriter.writeAndAssess(null, output.toFile(), "P", "R"));
    assertThrows(IllegalArgumentException.class,
        () -> Dexpi20ProcessModelPackageWriter.writeAndAssess(new ProcessModel(), output.toFile(),
            "P", "R"));
    assertThrows(IllegalArgumentException.class,
        () -> Dexpi20ProcessModelPackageWriter.writeAndAssess(new ProcessModel(), output.toFile(),
            " ", "R"));
  }

  @Test
  void differentControlledRevisionChangesPackageEvidence() throws IOException {
    ProcessModel model = EngineeringDiagramReferenceFixtures.multiAreaFacility().getProcessModel();
    File first = temporaryDirectory.resolve("revision-a.zip").toFile();
    File second = temporaryDirectory.resolve("revision-b.zip").toFile();
    Dexpi20ProcessModelPackageWriter.Report firstReport =
        Dexpi20ProcessModelPackageWriter.writeAndAssess(model, first, "PLANT-30", "REV-A");
    Dexpi20ProcessModelPackageWriter.Report secondReport =
        Dexpi20ProcessModelPackageWriter.writeAndAssess(model, second, "PLANT-30", "REV-B");

    assertNotEquals(firstReport.getCanonicalFingerprint(), secondReport.getCanonicalFingerprint());
    assertNotEquals(firstReport.getPackageFileSha256(), secondReport.getPackageFileSha256());
  }

  private static long connectionCount(Dexpi20ProcessModelPackageWriter.Report report, String type) {
    long count = 0L;
    for (Dexpi20ProcessModelPackageWriter.ConnectionRecord connection : report.getConnections()) {
      if (type.equals(connection.getConnectionType())) {
        count++;
      }
    }
    return count;
  }

  private static long connectionStatusCount(Dexpi20ProcessModelPackageWriter.Report report,
      String status) {
    long count = 0L;
    for (Dexpi20ProcessModelPackageWriter.ConnectionRecord connection : report.getConnections()) {
      if (status.equals(connection.getExchangeStatus())) {
        count++;
      }
    }
    return count;
  }

  private static boolean hasDiagnostic(Dexpi20ProcessModelPackageWriter.Report report, String code) {
    for (Dexpi20ProcessModelPackageWriter.Diagnostic diagnostic : report.getDiagnostics()) {
      if (code.equals(diagnostic.getCode())) {
        return true;
      }
    }
    return false;
  }

  private static List<String> zipEntries(Path file) throws IOException {
    List<String> entries = new ArrayList<String>();
    ZipInputStream input = new ZipInputStream(Files.newInputStream(file));
    try {
      ZipEntry entry;
      while ((entry = input.getNextEntry()) != null) {
        entries.add(entry.getName());
      }
    } finally {
      input.close();
    }
    Collections.sort(entries);
    return entries;
  }

  private static String zipEntry(Path file, String requestedName) throws IOException {
    ZipInputStream input = new ZipInputStream(Files.newInputStream(file));
    try {
      ZipEntry entry;
      while ((entry = input.getNextEntry()) != null) {
        if (!requestedName.equals(entry.getName())) {
          continue;
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int count;
        while ((count = input.read(buffer)) != -1) {
          output.write(buffer, 0, count);
        }
        return new String(output.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
      }
    } finally {
      input.close();
    }
    throw new AssertionError("ZIP entry not found: " + requestedName);
  }
}
