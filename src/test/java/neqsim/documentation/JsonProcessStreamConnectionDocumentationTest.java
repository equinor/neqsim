package neqsim.documentation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.processmodel.JsonProcessBuilder;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.processmodel.SimulationResult;

/** Executes the connected-Stream JSON example published in the process JSON guide. */
public class JsonProcessStreamConnectionDocumentationTest extends neqsim.NeqSimTest {
  private static final String EXAMPLE_START = "<!-- connected-json-stream-example:start -->\n```json\n";
  private static final String EXAMPLE_END = "\n```\n<!-- connected-json-stream-example:end -->";

  @Test
  void connectedStreamExampleBuildsRunsAndRetainsUpstreamIdentity() throws Exception {
    Path repositoryRoot = Paths.get(System.getProperty("basedir", ".")).toAbsolutePath();
    String guide = read(repositoryRoot.resolve("docs/process/json_process_models_and_systems.md"));
    String json = extractExample(guide);

    SimulationResult result = new JsonProcessBuilder().build(json);

    assertTrue(result.isSuccess(), "Documented JSON must build and run: " + result.toJson());
    ProcessSystem process = result.getProcessSystem();
    StreamInterface feed = (StreamInterface) process.getUnit("Feed");
    Cooler cooler = (Cooler) process.getUnit("Cooler");
    StreamInterface product = (StreamInterface) process.getUnit("Product");
    assertNotNull(feed);
    assertNotNull(cooler);
    assertNotNull(product);
    assertNotNull(cooler.getOutletStream());

    assertNotSame(feed.getFluid(), product.getFluid(),
        "A standalone source Stream must retain its independent cloned fluid");
    assertSame(cooler.getOutletStream().getFluid(), product.getFluid(),
        "A connected product Stream must retain the upstream outlet fluid identity");
    assertEquals(20.0, product.getTemperature("C"), 1.0e-6);
    assertTrue(product.getFlowRate("kg/hr") > 0.0);
  }

  @Test
  void guideStatesTheBuilderAndMetadataBoundaries() throws Exception {
    Path repositoryRoot = Paths.get(System.getProperty("basedir", ".")).toAbsolutePath();
    String guide = read(repositoryRoot.resolve("docs/process/json_process_models_and_systems.md"));
    String builder = read(repositoryRoot.resolve("src/main/java/neqsim/process/processmodel/JsonProcessBuilder.java"));

    assertTrue(guide.contains("A `Stream` without `inlet` or `inlets` is a standalone source."));
    assertTrue(guide.contains("It keeps that outlet's live fluid identity"));
    assertTrue(guide.contains("does not replace those wiring fields"));
    assertTrue(builder.contains("return unitDef.has(\"inlet\") || unitDef.has(\"inlets\");"));
    assertTrue(builder.contains("wireInletStream(equipment, stream);"));
  }

  private String extractExample(String guide) {
    int start = guide.indexOf(EXAMPLE_START);
    assertTrue(start >= 0, "Connected-Stream example start marker is missing");
    assertEquals(-1, guide.indexOf(EXAMPLE_START, start + 1), "Connected-Stream example must appear exactly once");
    int contentStart = start + EXAMPLE_START.length();
    int end = guide.indexOf(EXAMPLE_END, contentStart);
    assertTrue(end > contentStart, "Connected-Stream example end marker is missing");
    assertFalse(guide.substring(contentStart, end).trim().isEmpty());
    return guide.substring(contentStart, end);
  }

  private String read(Path path) throws Exception {
    return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
  }
}
