package neqsim.util.serialization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.separator.ThreePhaseSeparator;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Regression tests for the custom XStream layout used before archive metadata was introduced.
 *
 * @author esol
 * @version 1.0
 */
class NeqSimXtreamCustomCompatibilityTest {
  /** Directory for generated archive variants. */
  @TempDir
  File tempDir;

  /**
   * Builds an operated separator whose gas outlet can be modified independently of its feed.
   *
   * @param threePhase whether to use a three-phase separator
   * @return the operated separator
   */
  private static Separator operatedSeparator(boolean threePhase) {
    SystemInterface fluid = new SystemSrkEos(298.15, 50.0);
    fluid.addComponent("methane", 0.9);
    fluid.addComponent("n-hexane", 0.1);
    fluid.setMixingRule("classic");
    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(1000.0, "kg/hr");
    feed.run();
    Separator separator = threePhase ? new ThreePhaseSeparator("separator", feed) : new Separator("separator", feed);
    separator.run();
    return separator;
  }

  /**
   * Reads a ZIP entry without closing the surrounding archive.
   *
   * @param zip the archive
   * @param entry the entry to read
   * @return the entry bytes
   * @throws IOException if the entry cannot be read
   */
  private static byte[] readEntry(ZipFile zip, ZipEntry entry) throws IOException {
    try (InputStream input = zip.getInputStream(entry); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
      byte[] buffer = new byte[8192];
      int count;
      while ((count = input.read(buffer)) != -1) {
        output.write(buffer, 0, count);
      }
      return output.toByteArray();
    }
  }

  /**
   * Saves an object with the current writer.
   *
   * @param object the object to save
   * @return its archive
   * @throws IOException if a temporary file cannot be created
   */
  private File save(Object object) throws IOException {
    File archive = File.createTempFile("current-", ".neqsim", tempDir);
    assertTrue(NeqSimXtream.saveNeqsim(object, archive.getAbsolutePath()));
    return archive;
  }

  /**
   * Reads the serialized XML of a generated archive.
   *
   * @param archive the archive
   * @return the XML
   * @throws IOException if the archive cannot be read
   */
  private static String readXml(File archive) throws IOException {
    try (ZipFile zip = new ZipFile(archive)) {
      return new String(readEntry(zip, zip.getEntry(NeqSimXtream.PROCESS_ENTRY)), StandardCharsets.UTF_8);
    }
  }

  /**
   * Writes a controlled historical-layout variant, optionally retaining the writer metadata.
   *
   * @param source the current archive, or null for a plain legacy fixture
   * @param xml the replacement XML
   * @param stamped whether to retain metadata
   * @return the modified archive
   * @throws IOException if the archive cannot be written
   */
  private File archiveVariant(File source, String xml, boolean stamped) throws IOException {
    File variant = File.createTempFile("compatibility-", ".neqsim", tempDir);
    try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(variant.toPath()))) {
      if (stamped) {
        try (ZipFile zip = new ZipFile(source)) {
          output.putNextEntry(new ZipEntry(NeqSimXtream.META_ENTRY));
          output.write(readEntry(zip, zip.getEntry(NeqSimXtream.META_ENTRY)));
          output.closeEntry();
        }
      }
      output.putNextEntry(new ZipEntry(NeqSimXtream.PROCESS_ENTRY));
      output.write(xml.getBytes(StandardCharsets.UTF_8));
      output.closeEntry();
    }
    return variant;
  }

  /**
   * Adds the cache fields that older writers persisted in a class's custom default block.
   *
   * @param xml current serialized XML
   * @param separator the operated separator
   * @param owner the class declaring the cache
   * @return XML including the old cached values
   * @throws ReflectiveOperationException if a cache cannot be inspected
   */
  private static String addOldCaches(String xml, Separator separator, Class<?> owner)
      throws ReflectiveOperationException {
    StringBuilder caches = new StringBuilder();
    for (String name : new String[] {"lastEnthalpy", "lastFlowRate", "lastPressure", "lastPressureDrop"}) {
      Field field = owner.getDeclaredField(name);
      field.setAccessible(true);
      caches.append('<').append(name).append('>').append(field.getDouble(separator)).append("</").append(name)
          .append('>');
    }
    String pattern = "<" + owner.getName().replace(".", "\\.") + ">\\s*<default>";
    String modified = xml.replaceFirst(pattern, "$0" + caches);
    assertFalse(xml.equals(modified), "custom class default block must exist");
    return modified;
  }

  /**
   * Both separator classes must discard stored caches regardless of metadata presence.
   *
   * @param threePhase whether to exercise the three-phase run cache
   * @param stamped whether to retain archive metadata
   * @throws Exception if the archive cannot be generated or read
   */
  @ParameterizedTest
  @CsvSource({"false,false", "false,true", "true,false", "true,true"})
  void customLayoutRecalculatesStoredOutlet(boolean threePhase, boolean stamped) throws Exception {
    Separator separator = operatedSeparator(threePhase);
    separator.getGasOutStream().setTemperature(99.0, "C");
    File current = save(separator);
    String xml = addOldCaches(readXml(current), separator, Separator.class);
    if (threePhase) {
      xml = addOldCaches(xml, separator, ThreePhaseSeparator.class);
    }
    Separator loaded = (Separator) NeqSimXtream.openNeqsim(archiveVariant(current, xml, stamped).getAbsolutePath());
    loaded.run();
    assertEquals(25.0, loaded.getGasOutStream().getTemperature("C"), 1e-6);
    assertEquals(separator.getLiquidOutStream().getFlowRate("kg/hr"), loaded.getLiquidOutStream().getFlowRate("kg/hr"),
        1e-6);
  }

  /**
   * Missing owner-bound containers are restored without replacing explicitly saved settings.
   *
   * @throws Exception if the archive cannot be generated or read
   */
  @Test
  void customLegacyLayoutRestoresMissingEnergyPortsWithoutResettingSettings() throws Exception {
    Separator separator = operatedSeparator(false);
    separator.isActive(false);
    File current = save(separator);
    String xml = readXml(current);
    String modified = xml.replaceAll("(?s)<energyPorts\\b[^>]*(?:/>|>.*?</energyPorts>)", "");
    assertFalse(xml.equals(modified));
    Separator loaded = (Separator) NeqSimXtream.openNeqsim(archiveVariant(current, modified, false).getAbsolutePath());
    assertNotNull(loaded.getEnergyPorts());
    assertTrue(loaded.getEnergyPorts().isEmpty());
    assertFalse(loaded.isActive(), "an explicitly persisted setting must survive default restoration");
    assertTrue(NeqSimXtream.getLastLoadReport().getRestoredFields()
        .containsKey("neqsim.process.equipment.ProcessEquipmentBaseClass.energyPorts"));
  }

  /**
   * A custom readObject sees restored defaults and its calculations are retained without a second invocation.
   *
   * @throws IOException if the archive cannot be generated or read
   */
  @Test
  void customHookUsesRestoredDefaultExactlyOnce() throws IOException {
    File current = save(new HookDefaults());
    String xml = readXml(current).replace("<introduced>7</introduced>", "");
    assertFalse(xml.equals(readXml(current)));
    HookDefaults loaded = (HookDefaults) NeqSimXtream.openNeqsim(archiveVariant(current, xml, false).getAbsolutePath());
    assertEquals(14, loaded.introduced, "the hook must see the new field's declared default");
    assertEquals(1, loaded.hookRuns, "custom hooks must execute exactly once");
  }

  /**
   * Removed fields in a custom default block follow the same compatibility policy as plain-layout fields.
   *
   * @param stamped whether to retain archive metadata
   * @throws Exception if the archive cannot be generated or read
   */
  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void customLayoutIgnoresRemovedFields(boolean stamped) throws Exception {
    File current = save(new Separator("separator"));
    String xml = readXml(current).replace("<autoSized>", "<removedLegacySetting>42</removedLegacySetting><autoSized>");
    Separator loaded = (Separator) NeqSimXtream.openNeqsim(archiveVariant(current, xml, stamped).getAbsolutePath());
    assertEquals("separator", loaded.getName());
    assertTrue(NeqSimXtream.getLastLoadReport().getIgnoredElements().containsKey("removedLegacySetting"));
  }

  /**
   * Missing zone definitions are invalid engineering input in both historical XML layouts.
   *
   * @param custom whether to use a custom serialization block
   * @throws IOException if the fixture cannot be written
   */
  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void layeredWellMissingZonesFailsLoading(boolean custom) throws IOException {
    String type = "neqsim.process.equipment.reservoir.WellFlow";
    String fields = "<isMultiLayer>true</isMultiLayer>";
    String xml = custom
        ? "<" + type + " serialization=\"custom\"><" + type + "><default>" + fields + "</default></" + type + "></"
            + type + ">"
        : "<" + type + ">" + fields + "</" + type + ">";
    File legacy = archiveVariant(null, xml, false);
    RuntimeException failure = assertThrows(RuntimeException.class,
        () -> NeqSimXtream.openNeqsim(legacy.getAbsolutePath()));
    Throwable cause = failure;
    while (cause.getCause() != null && !(cause instanceof InvalidObjectException)) {
      cause = cause.getCause();
    }
    assertTrue(cause instanceof InvalidObjectException, failure.toString());
    assertTrue(cause.getMessage().contains("Layered WellFlow has no persisted zones"));
  }

  /**
   * Serializable model whose post-load calculation depends on a field added after a legacy archive was written.
   *
   * @author esol
   * @version 1.0
   */
  private static class HookDefaults implements Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1L;

    /** Newly introduced default used by the post-load calculation. */
    private int introduced = 7;

    /** Number of hook invocations. */
    private int hookRuns;

    /**
     * Performs the post-load calculation after restoring the fields.
     *
     * @param input serialized input
     * @throws IOException if the fields cannot be read
     * @throws ClassNotFoundException if a serialized class is unavailable
     */
    private void readObject(ObjectInputStream input) throws IOException, ClassNotFoundException {
      input.defaultReadObject();
      hookRuns++;
      introduced *= 2;
    }
  }
}
