package neqsim.mcp.runners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

/**
 * Tests the existing bounded static-JSON route for the new transport correlation APIs.
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class TransportPropertyCapabilityTest {
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
}
