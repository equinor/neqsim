package neqsim.process.equipment.capacity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for {@link EquipmentLimitRegister}: loading an illustrative 3-machine compressor-limits sample, year-dependent
 * lookup, JSON round trip, merge, and merging the limits into capacity constraints so that the utilisation snapshot
 * carries their status and source.
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class EquipmentLimitRegisterTest {

  /**
   * Read the sample database from the test resources.
   *
   * @return text of the sample file
   * @throws IOException if the resource cannot be read
   */
  private static String sample() throws IOException {
    try (InputStream in = EquipmentLimitRegisterTest.class
        .getResourceAsStream("/neqsim/process/equipment/capacity/compressor_limits_sample.json")) {
      assertNotNull(in, "sample resource missing");
      java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
      byte[] buf = new byte[4096];
      int n;
      while ((n = in.read(buf)) > 0) {
        out.write(buf, 0, n);
      }
      return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }
  }

  /**
   * The nested database layout loads into entries with status, source, confidence and era validity.
   *
   * @throws IOException if the sample cannot be read
   */
  @Test
  void loadsThreeMachineSampleWithStatusAndEras() throws IOException {
    EquipmentLimitRegister reg = EquipmentLimitRegister.fromCompressorLimitsDatabase(sample());
    assertEquals(2, reg.getTags().size());
    assertTrue(reg.getTags().contains("K-101") && reg.getTags().contains("K-201"));
    EquipmentLimitRegister.Entry motor = reg.lookup("k-101", "power_limit", null);
    assertNotNull(motor);
    assertEquals(765.0, motor.getValue(), 1e-9);
    assertEquals("kW", motor.getUnit());
    assertEquals(EquipmentLimitRegister.Status.ASSUMPTION, motor.getStatus());
    assertEquals(0.3, motor.getConfidence(), 1e-12);
    EquipmentLimitRegister.Entry old = reg.lookup("K-201", "n_max", Integer.valueOf(2027));
    assertEquals(10278.0, old.getValue(), 1e-9);
    assertEquals(EquipmentLimitRegister.Status.VERIFIED, old.getStatus());
    assertEquals(0.6, old.getConfidence(), 1e-12);
    assertTrue(old.getSource().length() > 5);
    EquipmentLimitRegister.Entry fresh = reg.lookup("K-201", "n_max", Integer.valueOf(2031));
    assertEquals(11571.0, fresh.getValue(), 1e-9);
    assertNull(reg.lookup("K-201", "no_such_quantity", null));
    assertNull(reg.lookup("UNKNOWN", "n_max", null));
    assertTrue(old.toSourceReference().startsWith("[VERIFIED]"));
  }

  /**
   * The Java register format survives a save and load round trip including null years.
   *
   * @param dir temporary directory
   * @throws IOException if the files cannot be handled
   */
  @Test
  void jsonRoundTrip(@TempDir Path dir) throws IOException {
    EquipmentLimitRegister reg = EquipmentLimitRegister.fromCompressorLimitsDatabase(sample());
    Path file = dir.resolve("limits.json");
    reg.save(file);
    String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
    assertTrue(text.contains("\"limits\"") && text.contains("\"equipmentTag\""));
    EquipmentLimitRegister back = EquipmentLimitRegister.load(file);
    assertEquals(reg.size(), back.size());
    EquipmentLimitRegister.Entry a = reg.getEntries().get(5);
    EquipmentLimitRegister.Entry b = back.getEntries().get(5);
    assertEquals(a.getEquipmentTag(), b.getEquipmentTag());
    assertEquals(a.getQuantity(), b.getQuantity());
    assertEquals(a.getValue(), b.getValue(), 0.0);
    assertEquals(a.getStatus(), b.getStatus());
    assertEquals(a.getValidToYear(), b.getValidToYear());
    assertEquals(a.getConfidence(), b.getConfidence(), 1e-12);
    assertEquals(2028, back.lookup("K-201", "n_max", Integer.valueOf(2020)).getValidToYear().intValue());
  }

  /**
   * Malformed input and invalid entries are rejected with a clear exception.
   */
  @Test
  void rejectsInvalidInput() {
    assertThrows(IllegalArgumentException.class, () -> EquipmentLimitRegister.fromJson("{}"));
    assertThrows(IllegalArgumentException.class, () -> EquipmentLimitRegister.fromJson("not json"));
    assertThrows(IllegalArgumentException.class,
        () -> new EquipmentLimitRegister.Entry("", "n_max", 1.0, "rpm", null, null, "", null, Double.NaN, null, ""));
    assertThrows(IllegalArgumentException.class, () -> new EquipmentLimitRegister.Entry("K", "n_max", Double.NaN, "rpm",
        null, null, "", null, Double.NaN, null, ""));
    assertThrows(IllegalArgumentException.class,
        () -> new EquipmentLimitRegister.Entry("K", "n_max", 1.0, "rpm", null, null, "", null, 1.5, null, ""));
    EquipmentLimitRegister min = EquipmentLimitRegister
        .fromJson("{\"limits\":[{\"equipmentTag\":\"X-1\",\"quantity\":\"n_max\",\"value\":5000}]}");
    assertEquals(EquipmentLimitRegister.Status.ASSUMPTION,
        min.lookup("X1", "n_max", Integer.valueOf(2030)).getStatus());
  }

  /**
   * Merge keeps existing entries unless overwrite is requested.
   *
   * @throws IOException if the sample cannot be read
   */
  @Test
  void mergeKeepsOrOverwrites() throws IOException {
    EquipmentLimitRegister reg = EquipmentLimitRegister.fromCompressorLimitsDatabase(sample());
    int before = reg.size();
    EquipmentLimitRegister other = new EquipmentLimitRegister();
    other.add(new EquipmentLimitRegister.Entry("K-101", "n_max", 9000.0, "rpm", null, null, "operator letter",
        EquipmentLimitRegister.Status.VERIFIED, 0.9, "USER_RULE", ""));
    other.add(new EquipmentLimitRegister.Entry("K-999", "n_max", 1000.0, "rpm", null, null, "new", null, Double.NaN,
        null, ""));
    assertEquals(1, reg.merge(other, false));
    assertEquals(before + 1, reg.size());
    assertEquals(10250.0, reg.lookup("K-101", "n_max", null).getValue(), 1e-9);
    assertEquals(2, reg.merge(other, true));
    assertEquals(9000.0, reg.lookup("K-101", "n_max", null).getValue(), 1e-9);
    assertEquals(before + 1, reg.size());
  }

  /**
   * The limits land in the capacity constraints as operating limits with status and source, and show up in the
   * utilisation snapshot; quantities without a matching constraint are reported, not dropped.
   *
   * @throws IOException if the sample cannot be read
   */
  @Test
  void appliesToCapacityConstraintsAndSnapshot() throws IOException {
    SystemSrkEos g = new SystemSrkEos(273.15 + 30.0, 10.0);
    g.addComponent("methane", 0.90);
    g.addComponent("ethane", 0.10);
    g.setMixingRule("classic");
    Stream feed = new Stream("feed", g);
    feed.setFlowRate(20000.0, "kg/hr");
    Compressor comp = new Compressor("K-201", feed);
    comp.setOutletPressure(20.0);
    comp.setUsePolytropicCalc(true);
    comp.setPolytropicEfficiency(0.78);
    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(comp);
    process.run();
    comp.generateCompressorChart("normal curves", 5);
    comp.getCompressorChart().setUseCompressorChart(true);
    comp.reinitializeCapacityConstraints();
    process.run();

    EquipmentLimitRegister reg = EquipmentLimitRegister.fromCompressorLimitsDatabase(sample());
    EquipmentLimitRegister.ApplyReport report = reg.applyTo(process, Integer.valueOf(2027));
    CapacityConstraint speed = comp.getCapacityConstraints().get("speed");
    assertNotNull(speed);
    assertTrue(speed.hasOperatingLimit());
    assertEquals(10278.0, speed.getOperatingLimit(), 1e-9);
    assertTrue(speed.getApplicableLimitSourceReference().startsWith("[VERIFIED]"));
    assertEquals(0.6, speed.getOperatingLimitConfidence(), 1e-12);
    List<String> applied = report.getApplied();
    assertEquals(1, applied.size(), report.toJson());
    assertTrue(applied.get(0).contains("[VERIFIED]"));
    assertFalse(report.getSkipped().isEmpty());
    String snapshot = process.getUtilizationSnapshotJson();
    assertTrue(snapshot.contains("[VERIFIED]"), snapshot);
    reg.applyTo(process, Integer.valueOf(2031));
    assertEquals(11571.0, comp.getCapacityConstraints().get("speed").getOperatingLimit(), 1e-9);
  }

  /**
   * Unit conversion for the supported limit units.
   */
  @Test
  void convertsLimitUnits() {
    assertEquals(765.0, EquipmentLimitRegister.convert(0.765, "MW", "kW"), 1e-9);
    assertEquals(150.0, EquipmentLimitRegister.convert(423.15, "K", "C"), 1e-9);
    assertEquals(5.5, EquipmentLimitRegister.convert(5.5, "bara", "bar"), 0.0);
    assertEquals(150.0, EquipmentLimitRegister.convert(150.0, "degC", "\u00b0C"), 0.0);
    assertTrue(Double.isNaN(EquipmentLimitRegister.convert(1.0, "rpm", "kW")));
  }
}
