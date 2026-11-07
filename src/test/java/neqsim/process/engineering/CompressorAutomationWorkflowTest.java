package neqsim.process.engineering;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import neqsim.process.controllerdevice.AntiSurgeController;
import neqsim.process.engineering.pid.PidDesignModel;
import neqsim.process.engineering.pid.PidElement;
import neqsim.process.util.scenario.CompressorAutomationStudy.CaseDefinition;
import neqsim.process.util.scenario.CompressorAutomationStudy.Design;
import neqsim.process.util.scenario.CompressorAutomationStudy.Profile;

/**
 * Verifies the public agent input, native execution, canonical rerun and artifact handoff contract.
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class CompressorAutomationWorkflowTest {
  @TempDir
  Path temporary;

  /** Confirms omitted scalar fields retain native defaults while explicit edits stay typed. */
  @Test
  void defaultsAndFiniteEditsStayTyped() {
    Design defaults = CompressorAutomationWorkflow.designFromJson("{}");
    Design edited = CompressorAutomationWorkflow.designFromJson("{\"feedFlowKgPerSecond\":6.0}");
    assertEquals(new Design().getFeedPressureBara(), defaults.getFeedPressureBara(), 0.0);
    assertEquals(6.0, edited.getFeedFlowKgPerSecond(), 0.0);
    Design methane = CompressorAutomationWorkflow.designFromJson("{\"composition\":{\"methane\":1.0}}");
    assertEquals(1, methane.getComposition().size());
    assertEquals(1.0, methane.getComposition().get("methane"), 0.0);
    assertEquals(defaults.getAntiSurgeOpeningSeconds(), edited.getAntiSurgeOpeningSeconds(), 0.0);
  }

  /** Rejects misspellings, duplicate policy fields, nulls, strings and non-finite numeric inputs. */
  @Test
  void unknownOrAmbiguousScalarInputIsRejected() {
    for (String invalid : Arrays.asList("{\"antiSurgeOpeningSecond\":0.3}",
        "{\"antiSurgeOpeningSeconds\":0.3,\"antiSurgeOpeningSeconds\":5}", "{\"feedFlowKgPerSecond\":null}",
        "{\"feedFlowKgPerSecond\":\"6\"}", "{\"feedFlowKgPerSecond\":1e309}", "{\"feedFlowKgPerSecond\":NaN}", "[]",
        "{} {}", "{\"composition\":{\"methane\":1,\"methane\":2}}", "{\"composition\":{\"methane\":\"1\"}}")) {
      assertThrows(IllegalArgumentException.class, () -> CompressorAutomationWorkflow.designFromJson(invalid));
    }
  }

  /** Native physical validation must reject invalid finite inputs before publishing results. */
  @Test
  void physicalInputValidationPrecedesNativeSimulation() {
    Design invalid = CompressorAutomationWorkflow.designFromJson("{\"feedFlowKgPerSecond\":-1.0}");
    Path output = temporary.resolve("invalid");
    assertThrows(IllegalArgumentException.class, () -> CompressorAutomationWorkflow.run(invalid, output));
    assertFalse(Files.exists(output));
  }

  /**
   * Existing user artifacts must never be replaced by an agent retry.
   *
   * @throws IOException if test fixture files cannot be written
   */
  @Test
  void existingOutputIsNeverOverwritten() throws IOException {
    Path output = temporary.resolve("existing");
    Files.createDirectory(output);
    Path marker = output.resolve("retain.txt");
    Files.write(marker, "retain".getBytes(StandardCharsets.UTF_8));
    assertThrows(IllegalArgumentException.class, () -> CompressorAutomationWorkflow.run(new Design(), output));
    assertEquals("retain", new String(Files.readAllBytes(marker), StandardCharsets.UTF_8));
  }

  /** Complete profile selection must remain explicit and include future public native profiles. */
  @Test
  void nativeCaseSelectionIsExplicit() {
    assertEquals(Profile.values().length, CompressorAutomationWorkflow.defaultCases().size());
    assertThrows(UnsupportedOperationException.class, () -> CompressorAutomationWorkflow.defaultCases().clear());
    assertThrows(IllegalArgumentException.class, () -> CompressorAutomationWorkflow.main(new String[0]));
  }

  /**
   * Executes actual native cases, then proves an edited proposal reaches fresh runtime readback and artifacts.
   *
   * @throws IOException if actual artifacts cannot be written or read
   */
  @Test
  void workflowConsumesCanonicalRevisionAndWritesStrictNativeEvidence() throws IOException {
    Design design = CompressorAutomationWorkflow.designFromJson("{\"feedFlowKgPerSecond\":6.0}");
    List<CaseDefinition> cases = Arrays.asList(new CaseDefinition(Profile.NORMAL, 0.2, 8.0, 1.0),
        new CaseDefinition(Profile.MANUAL_ESD, 0.2, 8.0, 1.0));
    Path first = temporary.resolve("first");
    Map<String, Object> initial = CompressorAutomationWorkflow.run(design, null, cases, first);
    assertEquals(0, ((Number) initial.get("nativeExecutionErrorCount")).intValue());
    assertEquals(2, ((Number) initial.get("nativeProfileCount")).intValue());
    assertEquals(Boolean.FALSE, initial.get("allPublicProfilesSelected"));
    JsonObject steady = read(first.resolve("steady-state.json"));
    assertEquals(6.0,
        steady.getAsJsonObject("streams").getAsJsonObject("30-FEED-001").get("massFlowKgPerSecond").getAsDouble(),
        1.0e-10);
    PidDesignModel canonical = PidDesignModel.fromJson(text(first.resolve("canonical-pid.json")));
    boolean found = false;
    for (PidElement element : canonical.getElements()) {
      Object raw = element.getAttributes().get("nativeControlConfiguration");
      if (raw instanceof Map && AntiSurgeController.class.getName().equals(((Map<?, ?>) raw).get("nativeClass"))) {
        Map<String, Object> revised = new LinkedHashMap<String, Object>();
        for (Map.Entry<?, ?> entry : ((Map<?, ?>) raw).entrySet()) {
          revised.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        revised.put("surgeMarginSetPoint", Double.valueOf(0.30));
        element.attribute("nativeControlConfiguration", revised);
        found = true;
      }
    }
    assertTrue(found);
    Path second = temporary.resolve("revised");
    Map<String, Object> rerun = CompressorAutomationWorkflow.run(design, canonical, cases, second);
    assertEquals(0, ((Number) rerun.get("nativeExecutionErrorCount")).intValue());
    assertEquals(Boolean.TRUE, rerun.get("canonicalControlEditsApplied"));
    JsonObject configuration = read(second.resolve("case-NORMAL-dt-0.2/configuration.json"));
    boolean consumed = false;
    for (JsonElement element : configuration.getAsJsonObject("controlSystem").getAsJsonArray("nativeControls")) {
      JsonObject nativeConfiguration = element.getAsJsonObject().getAsJsonObject("nativeConfiguration");
      if (nativeConfiguration.has("surgeMarginSetPoint")) {
        assertEquals(0.30, nativeConfiguration.get("surgeMarginSetPoint").getAsDouble(), 0.0);
        consumed = true;
      }
    }
    assertTrue(consumed);
    assertTrue(Files.isRegularFile(second.resolve("native-pid-package/plant.dexpi.xml")));
    JsonObject crosswalk = read(second.resolve("native-evidence-crosswalk.json"));
    for (JsonElement element : crosswalk.getAsJsonArray("cases")) {
      JsonObject row = element.getAsJsonObject();
      Path directory = second.resolve(row.get("caseDirectory").getAsString());
      assertEquals(row.get("nativeResultFingerprint").getAsString(),
          sha256(Files.readAllBytes(directory.resolve("native-result.raw.txt"))));
      assertEquals(row.get("strictNativeResultFileSha256").getAsString(),
          sha256(Files.readAllBytes(directory.resolve("native-result.json"))));
    }
    try (Stream<Path> files = Files.walk(second)) {
      for (Path file : files.filter(path -> path.toString().endsWith(".json")).collect(Collectors.toList())) {
        assertStrictJson(text(file));
      }
    }
    assertNotNull(read(second.resolve("dynamic-pid-feedback.json")).getAsJsonArray("safetyFunctionResults"));
  }

  /**
   * Reads a UTF-8 artifact without modifying it.
   *
   * @param file actual output file
   * @return original text
   * @throws IOException if reading fails
   */
  private static String text(Path file) throws IOException {
    return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
  }

  /**
   * Reads an expected strict JSON object.
   *
   * @param file artifact
   * @return parsed original output
   * @throws IOException if reading fails
   */
  private static JsonObject read(Path file) throws IOException {
    return new Gson().fromJson(text(file), JsonObject.class);
  }

  /**
   * Requires one complete strict JSON document with finite number tokens.
   *
   * @param source original artifact text
   * @throws IOException if strict JSON cannot be read
   */
  private static void assertStrictJson(String source) throws IOException {
    try (JsonReader reader = new JsonReader(new StringReader(source))) {
      reader.setLenient(false);
      consume(reader);
      assertEquals(JsonToken.END_DOCUMENT, reader.peek());
    }
  }

  /**
   * Consumes one value while rejecting unsupported numeric encodings.
   *
   * @param reader strict native artifact reader
   * @throws IOException if reading fails
   */
  private static void consume(JsonReader reader) throws IOException {
    switch (reader.peek()) {
    case BEGIN_OBJECT:
      reader.beginObject();
      while (reader.hasNext()) {
        reader.nextName();
        consume(reader);
      }
      reader.endObject();
      break;
    case BEGIN_ARRAY:
      reader.beginArray();
      while (reader.hasNext()) {
        consume(reader);
      }
      reader.endArray();
      break;
    case NUMBER:
      assertTrue(Double.isFinite(reader.nextDouble()));
      break;
    case STRING:
      reader.nextString();
      break;
    case BOOLEAN:
      reader.nextBoolean();
      break;
    case NULL:
      reader.nextNull();
      break;
    default:
      throw new IllegalArgumentException("Unexpected strict JSON token: " + reader.peek());
    }
  }

  /**
   * Checks the exact output-file representation, separately from native-result fingerprints.
   *
   * @param bytes original artifact bytes
   * @return lowercase SHA-256
   */
  private static String sha256(byte[] bytes) {
    try {
      StringBuilder result = new StringBuilder();
      for (byte value : MessageDigest.getInstance("SHA-256").digest(bytes)) {
        result.append(String.format("%02x", Integer.valueOf(value & 0xff)));
      }
      return result.toString();
    } catch (NoSuchAlgorithmException failure) {
      throw new IllegalStateException(failure);
    }
  }
}
