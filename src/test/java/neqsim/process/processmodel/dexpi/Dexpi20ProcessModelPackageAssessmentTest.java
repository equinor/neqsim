package neqsim.process.processmodel.dexpi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import neqsim.process.processmodel.ProcessConnection;
import neqsim.process.processmodel.diagram.EngineeringDiagramReferenceFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class Dexpi20ProcessModelPackageAssessmentTest {
  @TempDir
  Path temporaryDirectory;

  @Test
  void independentlyAssessesWriterPackageAndAreaConformance() throws IOException {
    File packageFile = writeReferencePackage("valid.zip");

    Dexpi20ProcessModelPackageAssessment.Report first =
        Dexpi20ProcessModelPackageAssessment.assess(packageFile);
    Dexpi20ProcessModelPackageAssessment.Report second =
        Dexpi20ProcessModelPackageAssessment.assess(packageFile);

    assertTrue(first.isValid(), first.toJson());
    assertFalse(first.isNativeWholePlantDexpiExchange());
    assertEquals("PLANT-30", first.getPlantId());
    assertEquals("REV-A", first.getRevision());
    assertEquals(64, first.getPackageFileSha256().length());
    assertEquals(64, first.getManifestSha256().length());
    assertEquals(64, first.getCanonicalFingerprint().length());
    assertEquals(4, first.getAreaEvidence().size());
    assertEquals(first.toJson(), second.toJson());
    for (Dexpi20ProcessModelPackageAssessment.AreaEvidence area : first.getAreaEvidence()) {
      assertTrue(area.isFileHashValid());
      assertTrue(area.isSchemaAndProfileConformant());
    }
  }

  @Test
  void detectsChangedAreaBytesAgainstBothDeclaredHashes() throws IOException {
    File original = writeReferencePackage("original.zip");
    Map<String, byte[]> entries = readEntries(original.toPath());
    String areaEntry = firstAreaEntry(entries);
    byte[] originalXml = entries.get(areaEntry);
    byte[] changedXml = new byte[originalXml.length + 1];
    System.arraycopy(originalXml, 0, changedXml, 0, originalXml.length);
    changedXml[changedXml.length - 1] = '\n';
    entries.put(areaEntry, changedXml);
    Path changed = temporaryDirectory.resolve("changed-area.zip");
    writeEntries(changed, entries);

    Dexpi20ProcessModelPackageAssessment.Report report =
        Dexpi20ProcessModelPackageAssessment.assess(changed.toFile());

    assertFalse(report.isValid());
    assertTrue(hasDiagnostic(report, "DEXPI_PROCESS_PACKAGE_AREA_HASH_MISMATCH"),
        report.toJson());
  }

  @Test
  void restartPackageCannotPromoteApprovalOrConstructionFitness() throws IOException {
    File original = writeReferencePackage("review-required.zip");
    Map<String, byte[]> entries = readEntries(original.toPath());
    JsonObject manifest = new Gson().fromJson(
        new String(entries.get("manifest.json"), StandardCharsets.UTF_8), JsonObject.class);
    manifest.addProperty("approvalStatus", "APPROVED");
    manifest.addProperty("fitnessForConstruction", true);
    manifest.addProperty("nativeWholePlantDexpiExchange", true);
    entries.put("manifest.json", new Gson().toJson(manifest).getBytes(StandardCharsets.UTF_8));
    Path changed = temporaryDirectory.resolve("weakened-boundary.zip");
    writeEntries(changed, entries);

    Dexpi20ProcessModelPackageAssessment.Report report =
        Dexpi20ProcessModelPackageAssessment.assess(changed.toFile());

    assertFalse(report.isValid());
    assertTrue(diagnosticCount(report, "DEXPI_PROCESS_PACKAGE_ENGINEERING_BOUNDARY_INVALID") >= 2,
        report.toJson());
    assertTrue(hasDiagnostic(report, "DEXPI_PROCESS_PACKAGE_MANIFEST_CONTRACT_MISMATCH"),
        report.toJson());
  }

  @Test
  void rejectsUndeclaredAndUnsafeArchiveEntries() throws IOException {
    File original = writeReferencePackage("safe.zip");
    Map<String, byte[]> entries = readEntries(original.toPath());
    entries.put("../unexpected.xml", "not dexpi".getBytes(StandardCharsets.UTF_8));
    Path changed = temporaryDirectory.resolve("unsafe.zip");
    writeEntries(changed, entries);

    Dexpi20ProcessModelPackageAssessment.Report report =
        Dexpi20ProcessModelPackageAssessment.assess(changed.toFile());

    assertFalse(report.isValid());
    assertTrue(hasDiagnostic(report, "DEXPI_PROCESS_PACKAGE_UNSAFE_ENTRY_NAME"), report.toJson());
    assertTrue(hasDiagnostic(report, "DEXPI_PROCESS_PACKAGE_UNDECLARED_ENTRY"), report.toJson());
  }

  @Test
  void missingManifestFailsClosedAndInvalidInputIsRejected() throws IOException {
    Map<String, byte[]> entries = new LinkedHashMap<String, byte[]>();
    entries.put("areas/uncontrolled.xml", "<Model/>".getBytes(StandardCharsets.UTF_8));
    Path missingManifest = temporaryDirectory.resolve("missing-manifest.zip");
    writeEntries(missingManifest, entries);

    Dexpi20ProcessModelPackageAssessment.Report report =
        Dexpi20ProcessModelPackageAssessment.assess(missingManifest.toFile());

    assertFalse(report.isValid());
    assertTrue(hasDiagnostic(report, "DEXPI_PROCESS_PACKAGE_MANIFEST_MISSING"), report.toJson());
    assertThrows(IllegalArgumentException.class,
        () -> Dexpi20ProcessModelPackageAssessment.assess(null));
    assertThrows(IOException.class, () -> Dexpi20ProcessModelPackageAssessment
        .assess(temporaryDirectory.resolve("absent.zip").toFile()));
  }

  private File writeReferencePackage(String name) throws IOException {
    EngineeringDiagramReferenceFixtures.ModelCase fixture =
        EngineeringDiagramReferenceFixtures.multiAreaFacility();
    fixture.getProcessModel().get("Inlet").connect("30-XV-001", "energyOut", "30-VA-001",
        "energyIn", ProcessConnection.ConnectionType.ENERGY);
    fixture.getProcessModel().get("Inlet").connect("30-VA-001", "signalOut", "30-SP-001",
        "signalIn", ProcessConnection.ConnectionType.SIGNAL);
    File packageFile = temporaryDirectory.resolve(name).toFile();
    Dexpi20ProcessModelPackageWriter.writeAndAssess(fixture.getProcessModel(), packageFile,
        "PLANT-30", "REV-A");
    return packageFile;
  }

  private static boolean hasDiagnostic(Dexpi20ProcessModelPackageAssessment.Report report,
      String code) {
    return diagnosticCount(report, code) > 0;
  }

  private static long diagnosticCount(Dexpi20ProcessModelPackageAssessment.Report report,
      String code) {
    long count = 0L;
    for (Dexpi20ProcessModelPackageAssessment.Diagnostic diagnostic : report.getDiagnostics()) {
      if (code.equals(diagnostic.getCode())) {
        count++;
      }
    }
    return count;
  }

  private static String firstAreaEntry(Map<String, byte[]> entries) {
    for (String entry : entries.keySet()) {
      if (entry.startsWith("areas/") && entry.endsWith(".xml")) {
        return entry;
      }
    }
    throw new AssertionError("No area XML entry found");
  }

  private static Map<String, byte[]> readEntries(Path file) throws IOException {
    Map<String, byte[]> result = new LinkedHashMap<String, byte[]>();
    ZipInputStream input = new ZipInputStream(Files.newInputStream(file), StandardCharsets.UTF_8);
    try {
      ZipEntry entry;
      while ((entry = input.getNextEntry()) != null) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) {
          output.write(buffer, 0, count);
        }
        result.put(entry.getName(), output.toByteArray());
      }
    } finally {
      input.close();
    }
    return result;
  }

  private static void writeEntries(Path file, Map<String, byte[]> entries) throws IOException {
    ZipOutputStream output =
        new ZipOutputStream(Files.newOutputStream(file), StandardCharsets.UTF_8);
    try {
      for (Map.Entry<String, byte[]> item : entries.entrySet()) {
        byte[] bytes = item.getValue();
        CRC32 crc = new CRC32();
        crc.update(bytes);
        ZipEntry entry = new ZipEntry(item.getKey());
        entry.setMethod(ZipEntry.STORED);
        entry.setSize(bytes.length);
        entry.setCompressedSize(bytes.length);
        entry.setCrc(crc.getValue());
        entry.setTime(0L);
        output.putNextEntry(entry);
        output.write(bytes);
        output.closeEntry();
      }
    } finally {
      output.close();
    }
  }
}
