package neqsim.process.safety.release;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/** Contract examples, deterministic serialization and failure-payload invariants. */
class SourceTermFrameTest extends neqsim.NeqSimTest {
  private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final Instant TIME = Instant.parse("2026-09-20T00:00:00Z");

  private SourceTermFrame frame(Map<String, String> provenance) {
    SystemInterface fluid = new SystemSrkEos(300.0, 5.0);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule("classic");
    ReleaseFlowRequest request = new ReleaseFlowRequest(fluid, 0.01, 0.62, 101325.0);
    ReleaseFlowResult result = new HomogeneousEquilibriumReleaseModel().calculate(request);
    assertTrue(result.isUsable());
    return SourceTermFrame.calculated("leak-1", "area/feed", ID, 0, 0.0, TIME, request, result, provenance);
  }

  @Test
  void documentationExampleAndOrderIndependentFingerprint() {
    Map<String, String> metadata = new LinkedHashMap<String, String>();
    metadata.put("processType", "ProcessSystem");
    metadata.put("basis", "hypothetical opening");
    SourceTermFrame frame = frame(metadata);
    String json = frame.toJson();
    SourceTermFrame.verifyEnvelope(json);
    assertEquals(json + "\n", frame.toNdjson());
    String csv = SourceTermFrame.csvHeader() + frame.toCsvRow();
    assertEquals(2, csv.split("\n").length);
    assertTrue(csv.contains("massFlowRate_kg_s"));
    Map<String, String> reversed = new LinkedHashMap<String, String>();
    reversed.put("basis", "hypothetical opening");
    reversed.put("processType", "ProcessSystem");
    assertEquals(json, frame(reversed).toJson());
    metadata.clear();
    assertEquals(json, frame.toJson());
    JsonObject root = JsonParser.parseString(json).getAsJsonObject();
    assertEquals("kg/s", root.getAsJsonObject("source").getAsJsonObject("massFlowRate").get("unit").getAsString());
    assertTrue(root.getAsJsonObject("source").getAsJsonObject("massFlowRate").get("value").getAsDouble() > 0.0);
  }

  @Test
  void alteredPayloadAndUnknownMajorVersionAreRejected() {
    JsonObject root = JsonParser.parseString(frame(Collections.emptyMap()).toJson()).getAsJsonObject();
    root.addProperty("sequence", 99);
    assertThrows(IllegalArgumentException.class, () -> SourceTermFrame.verifyEnvelope(root.toString()));
    root.addProperty("schemaVersion", "neqsim_safety_source_term.v2");
    assertThrows(IllegalArgumentException.class, () -> SourceTermFrame.verifyEnvelope(root.toString()));
    assertThrows(IllegalArgumentException.class, () -> SourceTermFrame.verifyEnvelope("{}"));
  }

  @Test
  void failureAndDisabledFramesHaveNoSourceOrFakeZero() {
    for (SourceTermFrame.Status status : new SourceTermFrame.Status[] {SourceTermFrame.Status.INVALID,
        SourceTermFrame.Status.STALE, SourceTermFrame.Status.UNSUPPORTED, SourceTermFrame.Status.DISABLED}) {
      SourceTermFrame frame = SourceTermFrame.unavailable("leak-1", "area/feed", ID, 2, 1.0, TIME, status,
          "PROCESS_UNAVAILABLE", "Current process state unavailable", Collections.emptyMap());
      SourceTermFrame.verifyEnvelope(frame.toJson());
      assertFalse(JsonParser.parseString(frame.toJson()).getAsJsonObject().has("source"));
      assertTrue(frame.toCsvRow().endsWith(",\n"));
      assertEquals(status, frame.getStatus());
    }
  }

  @Test
  void locationIsExplicitAndImmutable() {
    SourceTermFrame original = frame(Collections.emptyMap());
    double[] coordinates = {1.0, 2.0, 3.0};
    SourceTermFrame located = original.withLocation("local-east-north-up", coordinates, new double[] {1, 0, 0});
    coordinates[0] = 99;
    SourceTermFrame.verifyEnvelope(located.toJson());
    assertNotEquals(original.toJson(), located.toJson());
    assertFalse(original.toJson().contains("referenceFrame"));
    assertThrows(IllegalArgumentException.class,
        () -> original.withLocation("local", coordinates, new double[] {2, 0, 0}));
  }

  @Test
  void writeActualFramesForDraft202012Validation() throws Exception {
    Path directory = Paths.get("target", "source-term-contract-fixtures");
    Files.createDirectories(directory);
    SourceTermFrame valid = frame(Collections.singletonMap("basis", "synthetic regression"));
    Files.write(directory.resolve("valid.json"), valid.toJson().getBytes(StandardCharsets.UTF_8));
    SourceTermFrame located = valid.withLocation("local-east-north-up", new double[] {0, 0, 1}, new double[] {1, 0, 0});
    Files.write(directory.resolve("located.json"), located.toJson().getBytes(StandardCharsets.UTF_8));
    for (SourceTermFrame.Status status : new SourceTermFrame.Status[] {SourceTermFrame.Status.INVALID,
        SourceTermFrame.Status.STALE, SourceTermFrame.Status.UNSUPPORTED, SourceTermFrame.Status.DISABLED}) {
      SourceTermFrame failed = SourceTermFrame.unavailable("leak-1", "area/feed", ID, 1, 1.0, TIME, status,
          "TEST_STATUS", "Synthetic lifecycle check", Collections.emptyMap());
      Files.write(directory.resolve(status.name() + ".json"), failed.toJson().getBytes(StandardCharsets.UTF_8));
    }
  }
}
