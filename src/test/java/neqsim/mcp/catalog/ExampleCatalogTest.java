package neqsim.mcp.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import neqsim.mcp.runners.FlashRunner;
import neqsim.mcp.runners.ProcessRunner;
import neqsim.mcp.runners.Validator;

/**
 * Tests for {@link ExampleCatalog}.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class ExampleCatalogTest {

  @Test
  void testFlashTPSimpleGas_parsesAsJson() {
    String json = ExampleCatalog.flashTPSimpleGas();
    JsonObject root = JsonParser.parseString(json).getAsJsonObject();

    assertEquals("SRK", root.get("model").getAsString());
    assertEquals("TP", root.get("flashType").getAsString());
    assertTrue(root.has("components"));
    assertEquals(3, root.getAsJsonObject("components").entrySet().size());
  }

  @Test
  void testFlashTPSimpleGas_runsSuccessfully() {
    String json = ExampleCatalog.flashTPSimpleGas();
    String result = FlashRunner.run(json);
    JsonObject root = JsonParser.parseString(result).getAsJsonObject();

    assertEquals("success", root.get("status").getAsString());
  }

  @Test
  void testFlashTPTwoPhase_runsSuccessfully() {
    String result = FlashRunner.run(ExampleCatalog.flashTPTwoPhase());
    JsonObject root = JsonParser.parseString(result).getAsJsonObject();

    assertEquals("success", root.get("status").getAsString());
    assertTrue(root.getAsJsonObject("flash").get("numberOfPhases").getAsInt() >= 2);
  }

  @Test
  void testFlashDewPointT_runsSuccessfully() {
    String result = FlashRunner.run(ExampleCatalog.flashDewPointT());
    JsonObject root = JsonParser.parseString(result).getAsJsonObject();

    assertEquals("success", root.get("status").getAsString());
  }

  @Test
  void testFlashBubblePointP_runsSuccessfully() {
    String result = FlashRunner.run(ExampleCatalog.flashBubblePointP());
    JsonObject root = JsonParser.parseString(result).getAsJsonObject();

    assertEquals("success", root.get("status").getAsString());
  }

  @Test
  void testProcessSimpleSeparation_runsSuccessfully() {
    String result = ProcessRunner.run(ExampleCatalog.processSimpleSeparation());
    JsonObject root = JsonParser.parseString(result).getAsJsonObject();

    assertEquals("success", root.get("status").getAsString());
  }

  @Test
  void testProcessCompressionWithCooling_runsSuccessfully() {
    String result = ProcessRunner.run(ExampleCatalog.processCompressionWithCooling());
    JsonObject root = JsonParser.parseString(result).getAsJsonObject();

    assertEquals("success", root.get("status").getAsString());
  }

  @Test
  void testValidationErrorFlash_hasErrors() {
    String result = Validator.validate(ExampleCatalog.validationErrorFlash());
    JsonObject root = JsonParser.parseString(result).getAsJsonObject();

    assertFalse(root.get("valid").getAsBoolean());
    assertTrue(root.getAsJsonArray("issues").size() > 0);
  }

  @Test
  void testGetCategories() {
    List<String> categories = ExampleCatalog.getCategories();

    assertEquals(6, categories.size());
    assertTrue(categories.contains("flash"));
    assertTrue(categories.contains("process"));
    assertTrue(categories.contains("validation"));
    assertTrue(categories.contains("batch"));
    assertTrue(categories.contains("property-table"));
    assertTrue(categories.contains("phase-envelope"));
  }

  @Test
  void testGetExampleNames() {
    List<String> flashExamples = ExampleCatalog.getExampleNames("flash");
    assertEquals(5, flashExamples.size());
    assertTrue(flashExamples.contains("tp-simple-gas"));

    List<String> processExamples = ExampleCatalog.getExampleNames("process");
    assertEquals(2, processExamples.size());

    List<String> unknown = ExampleCatalog.getExampleNames("unknown");
    assertTrue(unknown.isEmpty());
  }

  @Test
  void testGetExample_byName() {
    String example = ExampleCatalog.getExample("flash", "tp-simple-gas");
    assertNotNull(example);
    assertEquals(ExampleCatalog.flashTPSimpleGas(), example);

    String process = ExampleCatalog.getExample("process", "simple-separation");
    assertNotNull(process);

    String notFound = ExampleCatalog.getExample("flash", "nonexistent");
    assertNull(notFound);
  }

  @Test
  void testGetCatalogJson() {
    String json = ExampleCatalog.getCatalogJson();
    JsonObject root = JsonParser.parseString(json).getAsJsonObject();

    assertTrue(root.has("flash"));
    assertTrue(root.has("process"));
    assertTrue(root.has("validation"));
    assertTrue(root.has("batch"));
    assertTrue(root.has("property-table"));
    assertTrue(root.has("phase-envelope"));
  }

  @Test
  void testBatchExamples() {
    List<String> names = ExampleCatalog.getExampleNames("batch");
    assertEquals(2, names.size());
    assertTrue(names.contains("temperature-sweep"));
    assertTrue(names.contains("pressure-sweep"));

    String example = ExampleCatalog.getExample("batch", "temperature-sweep");
    assertNotNull(example);
    JsonObject root = JsonParser.parseString(example).getAsJsonObject();
    assertTrue(root.has("cases"));
    assertTrue(root.has("components"));
  }

  @Test
  void testPropertyTableExamples() {
    List<String> names = ExampleCatalog.getExampleNames("property-table");
    assertEquals(2, names.size());

    String example = ExampleCatalog.getExample("property-table", "temperature-sweep");
    assertNotNull(example);
    JsonObject root = JsonParser.parseString(example).getAsJsonObject();
    assertTrue(root.has("sweep"));
    assertEquals("temperature", root.get("sweep").getAsString());
  }

  @Test
  void testPhaseEnvelopeExamples() {
    List<String> names = ExampleCatalog.getExampleNames("phase-envelope");
    assertEquals(1, names.size());
    assertTrue(names.contains("natural-gas"));

    String example = ExampleCatalog.getExample("phase-envelope", "natural-gas");
    assertNotNull(example);
    JsonObject root = JsonParser.parseString(example).getAsJsonObject();
    assertTrue(root.has("components"));
  }
}
