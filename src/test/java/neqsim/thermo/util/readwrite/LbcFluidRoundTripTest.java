package neqsim.thermo.util.readwrite;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import neqsim.physicalproperties.methods.commonphasephysicalproperties.viscosity.LBCViscosityMethod;
import neqsim.pvtsimulation.util.PVTFluidStudy;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import org.apache.commons.lang3.SerializationUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Preserve the actual LBC critical-volume contract across native, JSON and METRIC E300 files.
 *
 * @author ESOL
 * @version 1.0
 */
class LbcFluidRoundTripTest extends neqsim.NeqSimTest {
  @TempDir
  Path directory;

  /**
   * Construct petroleum fluid whose TBP density differs from the importer's MW-only estimate.
   *
   * @return initialized synthetic petroleum fluid with explicit LBC transport
   */
  private SystemInterface fluid() {
    SystemInterface fluid = new SystemSrkEos(353.15, 100.0);
    fluid.addComponent("methane", 0.40);
    fluid.addComponent("n-heptane", 0.10);
    fluid.addTBPfraction("C20", 0.50, 0.250, 0.880);
    fluid.setMixingRule("classic");
    fluid.useVolumeCorrection(true);
    fluid.init(0);
    PVTFluidStudy.configureViscosity(fluid, "LBC");
    return fluid;
  }

  /**
   * Access the active LBC correlation without altering its parameterization.
   *
   * @param fluid source fluid
   * @param phase phase index
   * @return selected phase's LBC model
   */
  private LBCViscosityMethod lbc(SystemInterface fluid, int phase) {
    return (LBCViscosityMethod) fluid.getPhase(phase).getPhysicalProperties().getViscosityModel();
  }

  /**
   * Evaluate oil viscosity at a controlled equilibrium state.
   *
   * @param fluid independent source fluid
   * @param pressureBara equilibrium pressure
   * @return oil viscosity in Pa s
   */
  private double oilViscosity(SystemInterface fluid, double pressureBara) {
    fluid.setTemperature(353.15);
    fluid.setPressure(pressureBara);
    new ThermodynamicOperations(fluid).TPflash();
    fluid.initPhysicalProperties();
    assertTrue(fluid.hasPhaseType("oil"));
    return fluid.getPhase("oil").getPhysicalProperties().getViscosity();
  }

  /**
   * Preserve Whitson-based LBC volumes and petroleum oil viscosity without changing thermodynamic VCRIT.
   *
   * @throws IOException if a temporary E300 file cannot be written
   */
  @Test
  void metricE300PreservesEffectiveVolumesAndOilViscosity() throws IOException {
    SystemInterface original = fluid();
    double[] volumes = lbc(original, 0).getCriticalVolumes();
    double genericVolume = original.getComponent(2).getCriticalVolume();
    assertNotEquals(genericVolume, volumes[2], 1.0e-3);
    double expectedWhitson = (21.573 + 0.015122 * 250.0 - 27.656 * 0.880 + 0.070615 * 250.0 * 0.880) * 62.42796;
    assertEquals(Math.max(expectedWhitson, 250.0 / 0.880 * 1.1), volumes[2], 1.0e-9);
    Path file = directory.resolve("oil.e300");
    EclipseFluidReadWrite.write(original, file.toString(), 80.0);
    String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
    assertTrue(text.contains("VCRITVIS\n   " + Double.toString(volumes[0] / 1000.0)));
    SystemInterface imported = EclipseFluidReadWrite.read(file.toString());
    for (int p = 0; p < imported.getMaxNumberOfPhases(); p++) {
      assertArrayEquals(volumes, lbc(imported, p).getCriticalVolumes(), 1.0e-9);
      assertEquals(genericVolume, imported.getPhase(p).getComponent(2).getCriticalVolume(), 5.1e-4);
    }
    for (double pressure : new double[] {100.0, 50.0}) {
      double expected = oilViscosity(original.clone(), pressure);
      assertEquals(expected, oilViscosity(imported.clone(), pressure), expected * 5.0e-4);
    }
  }

  /**
   * Preserve component order, METRIC unit conversion and overrides in replicated fluid reads.
   *
   * @throws IOException if a temporary E300 file cannot be written
   */
  @Test
  void replicatedReaderPreservesDistinctExplicitVolumesInEveryPhase() throws IOException {
    SystemInterface original = fluid();
    double[] volumes = {97.25, 445.5, 901.75};
    for (int p = 0; p < original.getMaxNumberOfPhases(); p++) {
      lbc(original, p).setCriticalVolumes(volumes);
    }
    Path file = directory.resolve("replicated.e300");
    EclipseFluidReadWrite.write(original, file.toString(), 80.0);
    SystemInterface imported = EclipseFluidReadWrite.read(file.toString(), new String[] {"A", "B"});
    double[] expected = {97.25, 445.5, 901.75, 97.25, 445.5, 901.75};
    for (int p = 0; p < imported.getMaxNumberOfPhases(); p++) {
      assertArrayEquals(expected, lbc(imported, p).getCriticalVolumes(), 1.0e-10);
    }
  }

  /** Verify JSON reload uses exported effective volumes despite reconstruction of TBP metadata. */
  @Test
  void jsonPreservesLbcVolumesAndPredictions() {
    SystemInterface original = fluid();
    String json = JsonFluidReadWrite.toJsonString(original, 80.0);
    JsonObject object = JsonParser.parseString(json).getAsJsonObject();
    assertEquals(3, object.getAsJsonObject("viscosityModel").getAsJsonArray("criticalVolumesCm3PerMol").size());
    for (String pseudoName : new String[] {"C20", "C20_PC"}) {
      object.getAsJsonArray("components").get(2).getAsJsonObject().addProperty("name", pseudoName);
      SystemInterface imported = JsonFluidReadWrite.readString(object.toString());
      assertEquals(3, imported.getNumberOfComponents());
      assertEquals("methane", imported.getComponent(0).getComponentName());
      assertEquals("n-heptane", imported.getComponent(1).getComponentName());
      assertEquals(pseudoName, imported.getComponent(2).getComponentName());
      assertEquals(0.50, imported.getComponent(2).getz(), 1.0e-12);
      assertArrayEquals(lbc(original, 0).getCriticalVolumes(), lbc(imported, 0).getCriticalVolumes(), 1.0e-9);
      assertEquals(oilViscosity(original, 50.0), oilViscosity(imported, 50.0), 1.0e-10);
    }
  }

  /**
   * Preserve explicit transport when an allocated phase changes between gas and oil identities.
   *
   * @throws IOException if a temporary E300 file cannot be written
   */
  @Test
  void explicitTransportSurvivesGasOilTypeSwitches() throws IOException {
    SystemInterface original = fluid();
    PhaseType[] initialTypes = new PhaseType[original.getMaxNumberOfPhases()];
    for (int p = 0; p < initialTypes.length; p++) {
      initialTypes[p] = original.getPhase(p).getType();
    }
    PVTFluidStudy.configureViscosity(original, "LBC");
    for (int p = 0; p < initialTypes.length; p++) {
      assertEquals(initialTypes[p], original.getPhase(p).getType());
    }
    double[] volumes = lbc(original, 0).getCriticalVolumes();
    Path file = directory.resolve("phase-types.e300");
    EclipseFluidReadWrite.write(original, file.toString(), 80.0);
    SystemInterface[] models = {original, EclipseFluidReadWrite.read(file.toString()),
        JsonFluidReadWrite.readString(JsonFluidReadWrite.toJsonString(original, 80.0))};
    for (SystemInterface model : models) {
      for (int p = 0; p < model.getMaxNumberOfPhases(); p++) {
        PhaseInterface phase = model.getPhase(p);
        PhaseType initialType = phase.getType();
        try {
          for (PhaseType type : new PhaseType[] {PhaseType.GAS, PhaseType.OIL}) {
            phase.setType(type);
            assertTrue(phase.getPhysicalProperties().isLBCViscosityModel());
            assertArrayEquals(volumes, lbc(model, p).getCriticalVolumes(), 1.0e-9);
          }
        } finally {
          phase.setType(initialType);
        }
      }
    }
  }

  /** Verify override array ownership, native serialization, clear semantics and legacy defaults. */
  @Test
  void volumeOverridesAreDefensiveAndIndependentAfterCloneAndSerialization() {
    SystemInterface original = fluid();
    double[] legacy = lbc(original, 0).getCriticalVolumes();
    double[] custom = {97.25, 445.5, 901.75};
    lbc(original, 0).setCriticalVolumes(custom);
    custom[0] = 1.0;
    double[] returned = lbc(original, 0).getCriticalVolumes();
    returned[1] = 1.0;
    assertEquals(97.25, lbc(original, 0).getCriticalVolume(0), 0.0);
    assertEquals(445.5, lbc(original, 0).getCriticalVolume(1), 0.0);
    SystemInterface cloned = original.clone();
    lbc(cloned, 0).setCriticalVolumes(new double[] {100.0, 500.0, 1000.0});
    assertEquals(97.25, lbc(original, 0).getCriticalVolume(0), 0.0);
    SystemInterface nativeCopy = SerializationUtils.clone(original);
    assertArrayEquals(lbc(original, 0).getCriticalVolumes(), lbc(nativeCopy, 0).getCriticalVolumes(), 0.0);
    lbc(original, 0).setCriticalVolumes(null);
    assertArrayEquals(legacy, lbc(original, 0).getCriticalVolumes(), 0.0);
    assertThrows(IllegalArgumentException.class, () -> lbc(original, 0).setCriticalVolumes(new double[] {1.0}));
    assertThrows(IllegalArgumentException.class,
        () -> lbc(original, 0).setCriticalVolumes(new double[] {1.0, Double.NaN, 3.0}));
    assertThrows(IllegalArgumentException.class,
        () -> lbc(original, 0).setCriticalVolumes(new double[] {1.0, 2.0, -3.0}));
  }

  /**
   * Reject malformed explicit volume arrays rather than silently falling back to estimates.
   *
   * @throws IOException if a temporary E300 file cannot be written
   */
  @Test
  void rejectsTruncatedOrNonFiniteTransportParameters() throws IOException {
    SystemInterface original = fluid();
    JsonObject json = JsonParser.parseString(JsonFluidReadWrite.toJsonString(original)).getAsJsonObject();
    JsonArray shortArray = new JsonArray();
    shortArray.add(100.0);
    json.getAsJsonObject("viscosityModel").add("criticalVolumesCm3PerMol", shortArray);
    assertThrows(IllegalArgumentException.class, () -> JsonFluidReadWrite.readString(json.toString()));
    Path file = directory.resolve("invalid.e300");
    String text = EclipseFluidReadWrite.toE300String(original);
    text = text.replaceFirst("(?s)VCRITVIS\\n.*?/", "VCRITVIS\n 0.1 NaN 0.9\n/");
    Files.write(file, text.getBytes(StandardCharsets.UTF_8));
    assertThrows(IllegalArgumentException.class, () -> EclipseFluidReadWrite.read(file.toString()));
    Path truncated = directory.resolve("truncated.e300");
    String truncatedText = EclipseFluidReadWrite.toE300String(original).replaceFirst("(?s)VCRITVIS\\n.*?/",
        "VCRITVIS\n 0.1 0.9 / -- two values for three components\n");
    Files.write(truncated, truncatedText.getBytes(StandardCharsets.UTF_8));
    assertThrows(IllegalArgumentException.class, () -> EclipseFluidReadWrite.read(truncated.toString()));
    Path empty = directory.resolve("empty.e300");
    String emptyText = EclipseFluidReadWrite.toE300String(original).replaceFirst("(?s)VCRITVIS\\n.*?/",
        "VCRITVIS\n / -- explicit empty arrays are invalid\n");
    Files.write(empty, emptyText.getBytes(StandardCharsets.UTF_8));
    assertThrows(IllegalArgumentException.class, () -> EclipseFluidReadWrite.read(empty.toString()));
    Path field = directory.resolve("field.e300");
    Files.write(field,
        EclipseFluidReadWrite.toE300String(original).replace("METRIC", "FIELD").getBytes(StandardCharsets.UTF_8));
    assertThrows(IllegalArgumentException.class, () -> EclipseFluidReadWrite.read(field.toString()));
  }
}
