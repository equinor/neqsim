package neqsim.mcp.runners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Tests the existing bounded static-JSON route for the new transport correlation APIs.
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class TransportPropertyCapabilityTest {
  /**
   * Checks runtime evidence against actual test sources before a packaged protocol run is needed.
   *
   * @throws IOException if the test source tree cannot be inspected
   */
  @Test
  void evidenceInventoryMatchesCurrentTestSources() throws IOException {
    try (Stream<Path> sources = Files.walk(Paths.get("src/test/java/neqsim/mcp"))) {
      long count = sources.filter(path -> path.getFileName().toString().endsWith("Test.java")).count();
      JsonObject tests = McpEvidenceInventory.build().getAsJsonObject("tests");
      assertEquals(count, tests.get("javaTestClassCount").getAsLong());
    }
  }

  /**
   * Invokes a registered double-argument correlation through the same JSON boundary used by MCP.
   *
   * @param className fully qualified correlation class
   * @param method method name
   * @param arguments JSON array of numeric arguments
   * @return parsed runner response
   */
  private JsonObject invoke(String className, String method, String arguments) {
    return JsonParser.parseString(GeneralCapabilityRunner.run("{\"action\":\"invoke\",\"className\":\"" + className
        + "\",\"methodName\":\"" + method + "\",\"arguments\":" + arguments + "}")).getAsJsonObject();
  }

  /** Verifies discoverability, SI value and explicit range refusal through the public runner. */
  @Test
  void letsouStielStaticRoute() {
    String className = "neqsim.physicalproperties.methods.liquidphysicalproperties.viscosity.LetsouStielViscosity";
    JsonObject search = JsonParser.parseString(GeneralCapabilityRunner.search("Letsou Stiel viscosity", 10))
        .getAsJsonObject();
    assertTrue(search.toString().contains("LetsouStielViscosity"));
    JsonObject value = invoke(className, "calculateViscosity", "[400,0.04607,516.25,63.83,0.6371]");
    assertEquals("success", value.get("status").getAsString(), value.toString());
    assertEquals(0.0002036150875308, value.get("result").getAsDouble(), 1e-16);
    JsonObject invalid = invoke(className, "calculateViscosity", "[300,0.04607,516.25,63.83,0.6371]");
    assertEquals("error", invalid.get("status").getAsString());
  }

  /** Verifies dense conductivity units and the recorded equation reference through the public runner. */
  @Test
  void chungDenseStaticRoute() {
    String className = "neqsim.physicalproperties.methods.commonphasephysicalproperties.conductivity."
        + "ChungDenseConductivityMethod";
    JsonObject value = invoke(className, "calculateConductivity", "[300,4000,0.0280134,126.2,89.4,0.0372,20.8,0,0]");
    assertEquals("success", value.get("status").getAsString(), value.toString());
    assertEquals(0.031034910394252442, value.get("result").getAsDouble(), 1e-14);
  }

  /** Verifies both CO2 correlation routes against published Table 6 values and preserves the extrapolation boundary. */
  @Test
  void carbonDioxideStaticRoutes() {
    String className = "neqsim.physicalproperties.methods.commonphasephysicalproperties.viscosity.CO2ViscosityMethod";
    JsonObject background = invoke(className, "calculateViscosity", "[300,65]");
    assertEquals("success", background.get("status").getAsString(), background.toString());
    assertEquals(0.000015563, background.get("result").getAsDouble(), 5e-10);
    JsonObject dilute = invoke(className, "calculateDiluteGasViscosity", "[100]");
    assertEquals("success", dilute.get("status").getAsString(), dilute.toString());
    assertEquals(0.0000053757, dilute.get("result").getAsDouble(), 5e-11);
    JsonObject finiteDensityExtrapolation = invoke(className, "calculateViscosity", "[10000,0]");
    assertEquals("error", finiteDensityExtrapolation.get("status").getAsString());
  }
}
