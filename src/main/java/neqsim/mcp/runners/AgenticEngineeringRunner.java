package neqsim.mcp.runners;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.mcp.model.ApiEnvelope;
import neqsim.mcp.model.ResultProvenance;
import neqsim.util.agentic.AgenticEngineeringKernel;

/**
 * MCP runner for the agentic engineering kernel.
 *
 * <p>
 * Exposes the Engineering Intent Graph and Workflow Compiler, Evidence Graph and Trust Engine, and
 * Autonomous Study Engine through one JSON action contract. The runner standardizes the kernel
 * output with MCP envelope fields so clients can consume provenance, validation, and quality-gate
 * metadata consistently.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class AgenticEngineeringRunner {
  private static final Gson GSON =
      new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create();

  /**
   * Private constructor for utility class.
   */
  private AgenticEngineeringRunner() {}

  /**
   * Runs the requested agentic engineering action.
   *
   * @param json input JSON with action {@code plan}, {@code trust}, or {@code study}
   * @return standardized MCP JSON response
   */
  public static String run(String json) {
    String raw = AgenticEngineeringKernel.run(json);
    JsonObject response = JsonParser.parseString(raw).getAsJsonObject();
    boolean success =
        response.has("status") && "success".equals(response.get("status").getAsString());
    if (!response.has("data")) {
      response.add("data", response.deepCopy());
    }
    ResultProvenance provenance = new ResultProvenance();
    provenance.setCalculationType("agentic engineering orchestration");
    provenance.setBenchmarkTrustLevel(BenchmarkTrust.getMaturityLevel("runAgenticEngineering"));
    provenance.setConverged(success);
    provenance.addAssumption("Kernel is deterministic and side-effect free");
    provenance.addValidationPassed("Agentic engineering response contract applied");
    ApiEnvelope.applyStandardFields(response, "runAgenticEngineering", provenance,
        ApiEnvelope.validationStatus(success, "agentic-kernel",
            success ? "Agentic engineering action completed" : "Agentic engineering action failed"),
        ApiEnvelope.qualityGate(success ? "passed" : "failed",
            success ? "Agentic engineering kernel completed" : "Agentic engineering kernel failed",
            true));
    return GSON.toJson(response);
  }
}
