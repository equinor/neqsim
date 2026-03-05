package neqsim.thermo.util.readwrite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import neqsim.thermo.phase.PhaseEos;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Tests for {@link JsonFluidReadWrite}.
 *
 * @author copilot
 * @version 1.0
 */
class JsonFluidReadWriteTest extends neqsim.NeqSimTest {

  @TempDir
  Path tempDir;

  /**
   * Minimal JSON fluid with two database components: methane and ethane.
   */
  private static final String MINIMAL_JSON = "{\n" + "  \"format\": \"neqsim-fluid\",\n"
      + "  \"version\": \"1.0\",\n" + "  \"eos\": \"SRK\",\n" + "  \"components\": [\n"
      + "    { \"name\": \"methane\", \"moleFraction\": 0.9,\n"
      + "      \"criticalTemperature\": 190.564, \"criticalPressure\": 45.99,\n"
      + "      \"acentricFactor\": 0.0115, \"molarMass\": 16.043,\n"
      + "      \"normalBoilingPoint\": 111.632, \"criticalVolume\": 0.0986,\n"
      + "      \"volumeShift\": -0.1542, \"parachor\": 77.3 },\n"
      + "    { \"name\": \"ethane\", \"moleFraction\": 0.1,\n"
      + "      \"criticalTemperature\": 305.32, \"criticalPressure\": 48.72,\n"
      + "      \"acentricFactor\": 0.0995, \"molarMass\": 30.07,\n"
      + "      \"normalBoilingPoint\": 184.55, \"criticalVolume\": 0.1455,\n"
      + "      \"volumeShift\": -0.1002, \"parachor\": 112.91 }\n" + "  ]\n" + "}";

  /**
   * JSON fluid with pseudo-components and BICs.
   */
  private static final String PSEUDO_JSON = "{\n" + "  \"format\": \"neqsim-fluid\",\n"
      + "  \"version\": \"1.0\",\n" + "  \"eos\": \"SRK\",\n" + "  \"components\": [\n"
      + "    { \"name\": \"nitrogen\", \"moleFraction\": 0.01,\n"
      + "      \"criticalTemperature\": 126.2, \"criticalPressure\": 33.9,\n"
      + "      \"acentricFactor\": 0.04, \"molarMass\": 28.014,\n"
      + "      \"normalBoilingPoint\": 77.4, \"criticalVolume\": 0.0895,\n"
      + "      \"volumeShift\": 0.0, \"parachor\": 41.0 },\n"
      + "    { \"name\": \"methane\", \"moleFraction\": 0.80,\n"
      + "      \"criticalTemperature\": 190.564, \"criticalPressure\": 45.99,\n"
      + "      \"acentricFactor\": 0.0115, \"molarMass\": 16.043,\n"
      + "      \"normalBoilingPoint\": 111.632, \"criticalVolume\": 0.0986,\n"
      + "      \"volumeShift\": -0.1542, \"parachor\": 77.3 },\n"
      + "    { \"name\": \"C7\", \"moleFraction\": 0.19, \"isPseudo\": true,\n"
      + "      \"criticalTemperature\": 585.0, \"criticalPressure\": 27.0,\n"
      + "      \"acentricFactor\": 0.35, \"molarMass\": 150.0,\n"
      + "      \"normalBoilingPoint\": 400.0, \"criticalVolume\": 0.45,\n"
      + "      \"volumeShift\": 0.0, \"parachor\": 300.0,\n" + "      \"density\": 780.0 }\n"
      + "  ],\n" + "  \"binaryInteractionCoefficients\": [\n"
      + "    { \"i\": \"nitrogen\", \"j\": \"methane\", \"kij\": 0.02 },\n"
      + "    { \"i\": \"nitrogen\", \"j\": \"C7\", \"kij\": 0.08 },\n"
      + "    { \"i\": \"methane\", \"j\": \"C7\", \"kij\": 0.04 }\n" + "  ]\n" + "}";

  /**
   * JSON fluid with LBC viscosity model.
   */
  private static final String LBC_JSON = "{\n" + "  \"format\": \"neqsim-fluid\",\n"
      + "  \"version\": \"1.0\",\n" + "  \"eos\": \"SRK\",\n" + "  \"components\": [\n"
      + "    { \"name\": \"methane\", \"moleFraction\": 0.9,\n"
      + "      \"criticalTemperature\": 190.564, \"criticalPressure\": 45.99,\n"
      + "      \"acentricFactor\": 0.0115, \"molarMass\": 16.043,\n"
      + "      \"normalBoilingPoint\": 111.632, \"criticalVolume\": 0.0986,\n"
      + "      \"volumeShift\": -0.1542, \"parachor\": 77.3 },\n"
      + "    { \"name\": \"ethane\", \"moleFraction\": 0.1,\n"
      + "      \"criticalTemperature\": 305.32, \"criticalPressure\": 48.72,\n"
      + "      \"acentricFactor\": 0.0995, \"molarMass\": 30.07,\n"
      + "      \"normalBoilingPoint\": 184.55, \"criticalVolume\": 0.1455,\n"
      + "      \"volumeShift\": -0.1002, \"parachor\": 112.91 }\n" + "  ],\n"
      + "  \"viscosityModel\": {\n" + "    \"type\": \"LBC\",\n"
      + "    \"coefficients\": [0.1023, 0.023364, 0.058533, -0.040758, 0.0093324]\n" + "  }\n"
      + "}";

  @Test
  void testReadStringMinimal() {
    SystemInterface fluid = JsonFluidReadWrite.readString(MINIMAL_JSON);
    assertNotNull(fluid);
    assertEquals(2, fluid.getNumberOfComponents());
    assertEquals("methane", fluid.getComponent(0).getComponentName());
    assertEquals("ethane", fluid.getComponent(1).getComponentName());
    assertEquals(0.9, fluid.getComponent(0).getz(), 1e-10);
    assertEquals(0.1, fluid.getComponent(1).getz(), 1e-10);
  }

  @Test
  void testReadStringWithPseudoComponents() {
    SystemInterface fluid = JsonFluidReadWrite.readString(PSEUDO_JSON);
    assertNotNull(fluid);
    assertEquals(3, fluid.getNumberOfComponents());
    assertEquals("nitrogen", fluid.getComponent(0).getComponentName());
    assertEquals("methane", fluid.getComponent(1).getComponentName());
    assertEquals("C7", fluid.getComponent(2).getComponentName());
    assertTrue(fluid.getComponent(2).isIsTBPfraction());
  }

  @Test
  void testBinaryInteractionCoefficients() {
    SystemInterface fluid = JsonFluidReadWrite.readString(PSEUDO_JSON);

    double[][] kij =
        ((PhaseEos) fluid.getPhase(0)).getMixingRule().getBinaryInteractionParameters();

    // nitrogen-methane kij = 0.02
    assertEquals(0.02, kij[0][1], 1e-10);
    assertEquals(0.02, kij[1][0], 1e-10);
    // nitrogen-C7 kij = 0.08
    assertEquals(0.08, kij[0][2], 1e-10);
    // methane-C7 kij = 0.04
    assertEquals(0.04, kij[1][2], 1e-10);
  }

  @Test
  void testCriticalProperties() {
    SystemInterface fluid = JsonFluidReadWrite.readString(MINIMAL_JSON);
    // Methane critical temperature
    assertEquals(190.564, fluid.getComponent(0).getTC(), 1e-3);
    // Methane critical pressure
    assertEquals(45.99, fluid.getComponent(0).getPC(), 1e-3);
    // Methane acentric factor
    assertEquals(0.0115, fluid.getComponent(0).getAcentricFactor(), 1e-4);
    // Methane molar mass (stored as kg/mol internally)
    assertEquals(16.043 / 1000.0, fluid.getComponent(0).getMolarMass(), 1e-6);
  }

  @Test
  void testTPflash() {
    SystemInterface fluid = JsonFluidReadWrite.readString(MINIMAL_JSON);
    fluid.setPressure(50.0, "bara");
    fluid.setTemperature(25.0, "C");

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.TPflash();

    // Methane-ethane mix at 50 bara, 25C should be single phase gas
    assertEquals(1, fluid.getNumberOfPhases());
  }

  @Test
  void testReadFromFile() throws IOException {
    // Write the minimal JSON to a temp file
    Path jsonFile = tempDir.resolve("test_fluid.json");
    Files.write(jsonFile, MINIMAL_JSON.getBytes(StandardCharsets.UTF_8));

    SystemInterface fluid = JsonFluidReadWrite.read(jsonFile.toString());
    assertNotNull(fluid);
    assertEquals(2, fluid.getNumberOfComponents());
    assertEquals("methane", fluid.getComponent(0).getComponentName());
  }

  @Test
  void testReadWithWater() throws IOException {
    Path jsonFile = tempDir.resolve("test_fluid_water.json");
    Files.write(jsonFile, MINIMAL_JSON.getBytes(StandardCharsets.UTF_8));

    SystemInterface fluid = JsonFluidReadWrite.read(jsonFile.toString(), true);
    // Should have 3 components now: methane, ethane, water
    assertEquals(3, fluid.getNumberOfComponents());
    assertEquals("water", fluid.getComponent(2).getComponentName());
  }

  @Test
  void testReadStringWithWater() {
    SystemInterface fluid = JsonFluidReadWrite.readString(MINIMAL_JSON, true);
    assertEquals(3, fluid.getNumberOfComponents());
    assertEquals("water", fluid.getComponent(2).getComponentName());
  }

  @Test
  void testWriteAndReadRoundTrip() throws IOException {
    // Read from JSON string
    SystemInterface original = JsonFluidReadWrite.readString(PSEUDO_JSON);

    // Write to file
    Path outputFile = tempDir.resolve("round_trip.json");
    JsonFluidReadWrite.write(original, outputFile.toString());

    // Read back
    SystemInterface loaded = JsonFluidReadWrite.read(outputFile.toString());

    // Verify same number of components
    assertEquals(original.getNumberOfComponents(), loaded.getNumberOfComponents());

    // Verify component names and mole fractions match
    for (int i = 0; i < original.getNumberOfComponents(); i++) {
      assertEquals(original.getComponent(i).getComponentName(),
          loaded.getComponent(i).getComponentName());
      assertEquals(original.getComponent(i).getz(), loaded.getComponent(i).getz(), 1e-10);
      assertEquals(original.getComponent(i).getTC(), loaded.getComponent(i).getTC(), 1e-3);
      assertEquals(original.getComponent(i).getPC(), loaded.getComponent(i).getPC(), 1e-3);
      assertEquals(original.getComponent(i).getAcentricFactor(),
          loaded.getComponent(i).getAcentricFactor(), 1e-6);
    }
  }

  @Test
  void testToJsonString() {
    SystemInterface fluid = JsonFluidReadWrite.readString(MINIMAL_JSON);
    String json = JsonFluidReadWrite.toJsonString(fluid);
    assertNotNull(json);
    assertTrue(json.contains("\"format\": \"neqsim-fluid\""));
    assertTrue(json.contains("\"eos\": \"SRK\""));
    assertTrue(json.contains("\"methane\""));
    assertTrue(json.contains("\"ethane\""));
  }

  @Test
  void testPReos() {
    String prJson = MINIMAL_JSON.replace("\"SRK\"", "\"PR\"");
    SystemInterface fluid = JsonFluidReadWrite.readString(prJson);
    assertNotNull(fluid);
    assertTrue(fluid.getClass().getSimpleName().toLowerCase().contains("pr"));
  }

  @Test
  void testE300ShortNames() {
    // Use E300 short names (C1, C2, N2) and verify they map correctly
    String json = "{\n" + "  \"eos\": \"SRK\",\n" + "  \"components\": [\n"
        + "    { \"name\": \"N2\", \"moleFraction\": 0.02,\n"
        + "      \"criticalTemperature\": 126.2, \"criticalPressure\": 33.94,\n"
        + "      \"acentricFactor\": 0.04, \"molarMass\": 28.014,\n"
        + "      \"normalBoilingPoint\": 77.4, \"criticalVolume\": 0.0895,\n"
        + "      \"volumeShift\": 0.0, \"parachor\": 41.0 },\n"
        + "    { \"name\": \"C1\", \"moleFraction\": 0.90,\n"
        + "      \"criticalTemperature\": 190.6, \"criticalPressure\": 46.0,\n"
        + "      \"acentricFactor\": 0.008, \"molarMass\": 16.043,\n"
        + "      \"normalBoilingPoint\": 111.6, \"criticalVolume\": 0.0986,\n"
        + "      \"volumeShift\": 0.0, \"parachor\": 77.3 },\n"
        + "    { \"name\": \"C2\", \"moleFraction\": 0.08,\n"
        + "      \"criticalTemperature\": 305.4, \"criticalPressure\": 48.8,\n"
        + "      \"acentricFactor\": 0.098, \"molarMass\": 30.07,\n"
        + "      \"normalBoilingPoint\": 184.6, \"criticalVolume\": 0.148,\n"
        + "      \"volumeShift\": 0.0, \"parachor\": 112.9 }\n" + "  ]\n" + "}";

    SystemInterface fluid = JsonFluidReadWrite.readString(json);
    assertEquals(3, fluid.getNumberOfComponents());
    // E300 short names should be mapped to NeqSim database names
    assertEquals("nitrogen", fluid.getComponent(0).getComponentName());
    assertEquals("methane", fluid.getComponent(1).getComponentName());
    assertEquals("ethane", fluid.getComponent(2).getComponentName());
  }

  @Test
  void testInvalidJsonThrows() {
    assertThrows(IllegalArgumentException.class, () -> JsonFluidReadWrite.readString("not json"));
  }

  @Test
  void testEmptyComponentsThrows() {
    String json = "{ \"eos\": \"SRK\", \"components\": [] }";
    assertThrows(IllegalArgumentException.class, () -> JsonFluidReadWrite.readString(json));
  }

  @Test
  void testMissingComponentsThrows() {
    String json = "{ \"eos\": \"SRK\" }";
    assertThrows(IllegalArgumentException.class, () -> JsonFluidReadWrite.readString(json));
  }

  @Test
  void testNullInputThrows() {
    assertThrows(IllegalArgumentException.class, () -> JsonFluidReadWrite.readString(null));
  }

  @Test
  void testFileNotFoundThrows() {
    assertThrows(IllegalArgumentException.class,
        () -> JsonFluidReadWrite.read("/nonexistent/path/fluid.json"));
  }

  @Test
  void testE300ToJsonConversion() throws IOException {
    // Read an E300 file and convert to JSON
    File dir = new File("src/test/java/neqsim/thermo/util/readwrite");
    String e300File = dir.getAbsolutePath() + "/fluid1.e300";

    SystemInterface e300Fluid = EclipseFluidReadWrite.read(e300File);
    assertNotNull(e300Fluid);

    // Write to JSON
    Path jsonFile = tempDir.resolve("from_e300.json");
    JsonFluidReadWrite.write(e300Fluid, jsonFile.toString());

    // Read back from JSON
    SystemInterface jsonFluid = JsonFluidReadWrite.read(jsonFile.toString());
    assertNotNull(jsonFluid);

    // Same number of components
    assertEquals(e300Fluid.getNumberOfComponents(), jsonFluid.getNumberOfComponents());

    // All component names match
    for (int i = 0; i < e300Fluid.getNumberOfComponents(); i++) {
      assertEquals(e300Fluid.getComponent(i).getComponentName(),
          jsonFluid.getComponent(i).getComponentName());
    }
  }

  @Test
  void testConvertE300ToJsonFile() throws IOException {
    File dir = new File("src/test/java/neqsim/thermo/util/readwrite");
    String e300File = dir.getAbsolutePath() + "/fluid1.e300";
    Path jsonOutput = tempDir.resolve("converted.json");

    JsonFluidReadWrite.convertE300ToJson(e300File, jsonOutput.toString());

    // Verify the output file exists and is valid JSON
    assertTrue(Files.exists(jsonOutput));
    String content = new String(Files.readAllBytes(jsonOutput), StandardCharsets.UTF_8);
    assertTrue(content.contains("\"format\": \"neqsim-fluid\""));
    assertTrue(content.contains("\"components\""));
  }

  @Test
  void testLBCViscosityModel() {
    SystemInterface fluid = JsonFluidReadWrite.readString(LBC_JSON);
    assertNotNull(fluid);
    assertEquals(2, fluid.getNumberOfComponents());
  }

  @Test
  void testWriteReadPreservesVolumeShift() throws IOException {
    SystemInterface original = JsonFluidReadWrite.readString(MINIMAL_JSON);

    String json = JsonFluidReadWrite.toJsonString(original);
    SystemInterface loaded = JsonFluidReadWrite.readString(json);

    for (int i = 0; i < original.getNumberOfComponents(); i++) {
      assertEquals(original.getComponent(i).getVolumeCorrectionConst(),
          loaded.getComponent(i).getVolumeCorrectionConst(), 1e-6,
          "Volume shift mismatch for " + original.getComponent(i).getComponentName());
    }
  }

  @Test
  void testWriteReadPreservesParachors() throws IOException {
    SystemInterface original = JsonFluidReadWrite.readString(MINIMAL_JSON);

    String json = JsonFluidReadWrite.toJsonString(original);
    SystemInterface loaded = JsonFluidReadWrite.readString(json);

    for (int i = 0; i < original.getNumberOfComponents(); i++) {
      assertEquals(original.getComponent(i).getParachorParameter(),
          loaded.getComponent(i).getParachorParameter(), 1e-6,
          "Parachor mismatch for " + original.getComponent(i).getComponentName());
    }
  }
}
