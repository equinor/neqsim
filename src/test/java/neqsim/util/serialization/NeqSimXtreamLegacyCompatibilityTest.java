package neqsim.util.serialization;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.File;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.separator.ThreePhaseSeparator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.component.ComponentEos;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Cross-version tests for {@link NeqSimXtream}: a .neqsim file written by NeqSim 3.2.1 must load, run and reproduce the
 * results of the version that wrote it.
 *
 * <p>
 * The fixture holds an SRK gas condensate (100 t/h, 25 C, 50 bara) through a separator, a cooler to 5 C and 70 bara and
 * a three-phase scrubber with 5 vol% oil carry-over. After the run, the writer flashed the scrubber gas outlet to its
 * dew point before saving, so the stored outlet state is not the run result. Results recorded by NeqSim 3.2.1:
 * separator liquid 13228.09 kg/h, scrubber liquid 1522.14 kg/h, scrubber gas 85249.77 kg/h.
 * </p>
 *
 * @author esol
 * @version 1.0
 */
public class NeqSimXtreamLegacyCompatibilityTest {
  /** Separator liquid flow recorded by NeqSim 3.2.1, kg/hr. */
  private static final double SEPARATOR_LIQUID_3_2_1 = 13228.08750644292;
  /** Scrubber liquid flow recorded by NeqSim 3.2.1, kg/hr. */
  private static final double SCRUBBER_LIQUID_3_2_1 = 1522.1429154790133;
  /** Scrubber gas flow recorded by NeqSim 3.2.1, kg/hr. */
  private static final double SCRUBBER_GAS_3_2_1 = 85249.76957800794;

  @TempDir
  File tempDir;

  /**
   * Returns the path of the legacy fixture.
   *
   * @return absolute path of the fixture file
   * @throws URISyntaxException if the resource URL is malformed
   */
  private static String legacyFixture() throws URISyntaxException {
    return new File(NeqSimXtreamLegacyCompatibilityTest.class
        .getResource("/neqsim/util/serialization/legacy_neqsim_3_2_1_process.neqsim").toURI()).getAbsolutePath();
  }

  /**
   * Builds the fixture flowsheet with the current API.
   *
   * @return the process, not yet run
   */
  private static ProcessSystem buildFreshProcess() {
    SystemInterface fluid = new SystemSrkEos(298.15, 50.0);
    fluid.addComponent("methane", 0.86);
    fluid.addComponent("ethane", 0.06);
    fluid.addComponent("propane", 0.035);
    fluid.addComponent("n-butane", 0.02);
    fluid.addComponent("n-hexane", 0.015);
    fluid.addComponent("n-octane", 0.01);
    fluid.setMixingRule("classic");
    ProcessSystem process = new ProcessSystem();
    Stream feed = new Stream("feed gas", fluid);
    feed.setFlowRate(100000.0, "kg/hr");
    feed.setTemperature(25.0, "C");
    feed.setPressure(50.0, "bara");
    Separator separator = new Separator("separator 1", feed);
    Heater cooler = new Heater("cooler", separator.getGasOutStream());
    cooler.setOutTemperature(5.0, "C");
    cooler.setOutPressure(70.0, "bara");
    ThreePhaseSeparator scrubber = new ThreePhaseSeparator("dew point scrubber", cooler.getOutletStream());
    scrubber.setEntrainment(0.05, "volume", "feed", "oil", "gas");
    process.add(feed);
    process.add(separator);
    process.add(cooler);
    process.add(scrubber);
    return process;
  }

  /**
   * Counts EOS components with an active OmegaA override across all unit outlet fluids.
   *
   * @param process the process to inspect
   * @return number of components with an active override
   */
  private static int countOmegaAOverrides(ProcessSystem process) {
    int count = 0;
    for (ProcessEquipmentInterface unit : process.getUnitOperations()) {
      List<StreamInterface> streams = new ArrayList<StreamInterface>(unit.getOutletStreams());
      if (unit instanceof StreamInterface) {
        streams.add((StreamInterface) unit);
      }
      for (StreamInterface stream : streams) {
        SystemInterface fluid = stream.getFluid();
        for (int phase = 0; phase < fluid.getNumberOfPhases(); phase++) {
          for (int i = 0; i < fluid.getPhase(phase).getNumberOfComponents(); i++) {
            Object component = fluid.getPhase(phase).getComponent(i);
            if (component instanceof ComponentEos && ((ComponentEos) component).hasOmegaAOverride()) {
              count++;
            }
          }
        }
      }
    }
    return count;
  }

  @Test
  void legacyFileLoadsRunsAndReproducesWriterResults() throws Exception {
    Object loaded = NeqSimXtream.openNeqsim(legacyFixture());
    assertTrue(loaded instanceof ProcessSystem);
    NeqSimXtream.LoadReport report = NeqSimXtream.getLastLoadReport();
    assertNotNull(report);
    assertTrue(report.isLegacyFormat());
    assertFalse(report.isClean());
    assertNull(report.getWriterVersion());
    assertTrue(report.getIgnoredElements().containsKey("separatorLength"), report.getIgnoredElements().toString());
    assertTrue(report.getRenamedFields().keySet().toString().contains("tagName -> tagNumber"),
        report.getRenamedFields().toString());
    assertTrue(report.getRestoredFields().containsKey("neqsim.thermo.component.ComponentEos.omegaAOverride"),
        report.getRestoredFields().keySet().toString());
    assertTrue(
        report.getRestoredFields().containsKey("neqsim.process.equipment.ProcessEquipmentBaseClass.energyPorts"));

    ProcessSystem process = (ProcessSystem) loaded;
    assertEquals(0, countOmegaAOverrides(process), "a legacy component must not get OmegaA = 0");
    process.run();

    Separator separator = (Separator) process.getUnit("separator 1");
    ThreePhaseSeparator scrubber = (ThreePhaseSeparator) process.getUnit("dew point scrubber");
    assertEquals(SEPARATOR_LIQUID_3_2_1, separator.getLiquidOutStream().getFlowRate("kg/hr"),
        SEPARATOR_LIQUID_3_2_1 * 1e-3);
    assertEquals(SCRUBBER_LIQUID_3_2_1, scrubber.getLiquidOutStream().getFlowRate("kg/hr"),
        SCRUBBER_LIQUID_3_2_1 * 1e-3);
    assertEquals(SCRUBBER_GAS_3_2_1, scrubber.getGasOutStream().getFlowRate("kg/hr"), SCRUBBER_GAS_3_2_1 * 1e-4);
    // The stored gas outlet had been flashed to its dew point after the run; a real run must replace it.
    assertEquals(5.0, scrubber.getGasOutStream().getTemperature("C"), 1e-6);
    assertEquals(70.0, scrubber.getGasOutStream().getPressure("bara"), 1e-6);
  }

  @Test
  void legacyFileAgreesWithFreshlyBuiltModel() throws Exception {
    ProcessSystem legacy = (ProcessSystem) NeqSimXtream.openNeqsim(legacyFixture());
    legacy.run();
    ProcessSystem fresh = buildFreshProcess();
    fresh.run();
    for (String unit : new String[] {"separator 1", "dew point scrubber"}) {
      double legacyLiquid = ((Separator) legacy.getUnit(unit)).getLiquidOutStream().getFlowRate("kg/hr");
      double freshLiquid = ((Separator) fresh.getUnit(unit)).getLiquidOutStream().getFlowRate("kg/hr");
      assertEquals(freshLiquid, legacyLiquid, freshLiquid * 0.02, unit + " liquid flow");
    }
  }

  @Test
  void resavedLegacyFileHasMetadataAndLoadsCleanly() throws Exception {
    ProcessSystem legacy = (ProcessSystem) NeqSimXtream.openNeqsim(legacyFixture());
    legacy.run();
    double scrubberLiquid = ((Separator) legacy.getUnit("dew point scrubber")).getLiquidOutStream()
        .getFlowRate("kg/hr");

    String resaved = new File(tempDir, "resaved.neqsim").getAbsolutePath();
    assertTrue(NeqSimXtream.saveNeqsim(legacy, resaved));
    try (ZipFile zip = new ZipFile(resaved)) {
      List<String> names = new ArrayList<String>();
      Enumeration<? extends ZipEntry> entries = zip.entries();
      while (entries.hasMoreElements()) {
        names.add(entries.nextElement().getName());
      }
      assertArrayEquals(new String[] {NeqSimXtream.META_ENTRY, NeqSimXtream.PROCESS_ENTRY},
          names.toArray(new String[0]));
    }

    ProcessSystem reloaded = (ProcessSystem) NeqSimXtream.openNeqsim(resaved);
    NeqSimXtream.LoadReport report = NeqSimXtream.getLastLoadReport();
    assertFalse(report.isLegacyFormat());
    assertNotNull(report.getWriterVersion());
    assertTrue(report.isClean(), report.toString());
    reloaded.run();
    assertEquals(scrubberLiquid,
        ((Separator) reloaded.getUnit("dew point scrubber")).getLiquidOutStream().getFlowRate("kg/hr"),
        scrubberLiquid * 1e-6);
  }

  @Test
  void zeroOmegaAIsNotTreatedAsOverride() throws Exception {
    SystemInterface fluid = new SystemSrkEos(298.15, 50.0);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule("classic");
    ComponentEos methane = (ComponentEos) fluid.getPhase(0).getComponent("methane");
    double defaultA = methane.calca();

    Field override = ComponentEos.class.getDeclaredField("omegaAOverride");
    override.setAccessible(true);
    override.setDouble(methane, 0.0);
    assertFalse(methane.hasOmegaAOverride());
    assertTrue(Double.isNaN(methane.getOmegaAOverride()));
    assertEquals(defaultA, methane.calca(), 1e-12 * defaultA);

    methane.setOmegaA(0.0);
    assertFalse(methane.hasOmegaAOverride());
    methane.setOmegaA(-0.4);
    assertFalse(methane.hasOmegaAOverride());
    methane.setOmegaA(0.5);
    assertTrue(methane.hasOmegaAOverride());
    assertEquals(0.5, methane.getOmegaAOverride(), 0.0);
    methane.setOmegaA(Double.NaN);
    assertFalse(methane.hasOmegaAOverride());
    assertEquals(defaultA, methane.calca(), 1e-12 * defaultA);
  }

  @Test
  void failedSaveKeepsExistingFileAndLeavesNoTemporaryFile() throws Exception {
    File target = new File(tempDir, "existing.neqsim");
    Files.write(target.toPath(), "previous content".getBytes(StandardCharsets.UTF_8));

    assertFalse(NeqSimXtream.saveNeqsim(new FailingSerializable(), target.getAbsolutePath()));
    assertEquals("previous content", new String(Files.readAllBytes(target.toPath()), StandardCharsets.UTF_8));
    String[] leftovers = tempDir.list();
    assertNotNull(leftovers);
    assertEquals(1, leftovers.length, "only the original file may remain");
  }

  /** Object whose serialization always fails, used to exercise the failed-save path. */
  static class FailingSerializable implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * Always fails.
     *
     * @param out the stream
     * @throws IOException always
     */
    private void writeObject(ObjectOutputStream out) throws IOException {
      throw new IOException("intentional failure");
    }
  }
}
