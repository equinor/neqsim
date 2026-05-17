package neqsim.mcp.runners;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.process.safety.risk.sis.SafetyInstrumentedFunction;
import neqsim.process.safety.risk.sis.SILVerificationResult;

/**
 * MCP runner for SIL (Safety Integrity Level) verification per IEC 61508 / IEC 61511.
 *
 * <p>
 * Computes the average Probability of Failure on Demand (PFD<sub>avg</sub>) for a Safety
 * Instrumented Function (SIF) given a list of components (sensors / logic-solver / final-elements)
 * with either an explicit PFD or a dangerous-undetected failure rate plus proof-test interval.
 * Returns the achieved SIL, claimed SIL, RRF, and verification issues.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class SILRunner {

  private static final Gson GSON =
      new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create();

  private SILRunner() {}

  /**
   * Runs a SIL verification calculation from a JSON definition.
   *
   * @param json JSON with sif metadata and components array
   * @return JSON string with SIL verification result
   */
  public static String run(String json) {
    if (json == null || json.trim().isEmpty()) {
      return errorJson("JSON input is null or empty");
    }
    try {
      JsonObject input = JsonParser.parseString(json).getAsJsonObject();
      String name = input.has("name") ? input.get("name").getAsString() : "SIF-001";
      String description =
          input.has("description") ? input.get("description").getAsString() : "Safety function";
      int claimedSil = input.has("claimedSIL") ? input.get("claimedSIL").getAsInt() : 2;
      String architecture =
          input.has("architecture") ? input.get("architecture").getAsString() : "1oo1";
      double testIntervalHours =
          input.has("proofTestInterval_hours") ? input.get("proofTestInterval_hours").getAsDouble()
              : 8760.0;

      // Aggregate PFD from components.
      double totalPfd = 0.0;
      JsonArray componentsOut = new JsonArray();
      if (input.has("components")) {
        JsonArray comps = input.getAsJsonArray("components");
        for (JsonElement el : comps) {
          JsonObject c = el.getAsJsonObject();
          String cName = c.has("name") ? c.get("name").getAsString() : "comp";
          String cType = c.has("type") ? c.get("type").getAsString() : "sensor";
          double cPfd;
          double failureRate = 0.0;
          if (c.has("pfd")) {
            cPfd = c.get("pfd").getAsDouble();
          } else if (c.has("lambdaDU_per_hr")) {
            failureRate = c.get("lambdaDU_per_hr").getAsDouble();
            String arch =
                c.has("architecture") ? c.get("architecture").getAsString() : architecture;
            cPfd = computePfdForArchitecture(arch, failureRate, testIntervalHours);
          } else {
            throw new IllegalArgumentException(
                "Component '" + cName + "' must specify 'pfd' or 'lambdaDU_per_hr'");
          }
          totalPfd += cPfd;
          JsonObject co = new JsonObject();
          co.addProperty("name", cName);
          co.addProperty("type", cType);
          co.addProperty("failureRate_per_hr", failureRate);
          co.addProperty("pfdContribution", round(cPfd, 8));
          componentsOut.add(co);
        }
        // Compute percentages
        for (JsonElement el : componentsOut) {
          JsonObject co = el.getAsJsonObject();
          double pfd = co.get("pfdContribution").getAsDouble();
          co.addProperty("percentOfTotal", round(totalPfd > 0 ? 100.0 * pfd / totalPfd : 0.0, 2));
        }
      } else if (input.has("pfdAvg")) {
        totalPfd = input.get("pfdAvg").getAsDouble();
      } else {
        throw new IllegalArgumentException(
            "Provide either 'components' array or top-level 'pfdAvg'");
      }

      SafetyInstrumentedFunction sif = SafetyInstrumentedFunction.builder().name(name)
          .description(description).sil(claimedSil).pfd(totalPfd)
          .testIntervalHours(testIntervalHours).architecture(architecture).build();

      SILVerificationResult ver = new SILVerificationResult(sif);

      JsonObject out = new JsonObject();
      out.addProperty("status", "success");
      out.addProperty("standard", "IEC 61508 / IEC 61511");
      JsonObject sum = new JsonObject();
      sum.addProperty("name", sif.getName());
      sum.addProperty("architecture", architecture);
      sum.addProperty("claimedSIL", ver.getClaimedSIL());
      sum.addProperty("achievedSIL", ver.getAchievedSIL());
      sum.addProperty("silAchieved", ver.isSilAchieved());
      sum.addProperty("pfdAvg", round(ver.getPfdAverage(), 8));
      sum.addProperty("riskReductionFactor", round(sif.getRiskReductionFactor(), 1));
      sum.addProperty("proofTestInterval_hours", testIntervalHours);
      sum.addProperty("proofTestInterval_years", round(sif.getProofTestIntervalYears(), 2));
      sum.addProperty("hardwareFaultTolerance", ver.getHardwareFaultTolerance());
      out.add("verification", sum);
      out.add("components", componentsOut);
      out.add("verificationDetails", JsonParser.parseString(ver.toJson()));
      return GSON.toJson(out);
    } catch (Exception e) {
      return errorJson("SIL verification failed: " + e.getMessage());
    }
  }

  /**
   * Computes PFD for a given architecture using the simplified IEC 61508 formulae.
   *
   * @param arch architecture string (1oo1, 1oo2, 2oo3)
   * @param lambdaDU dangerous undetected failure rate per channel [/hr]
   * @param testInterval proof-test interval [hr]
   * @return PFD<sub>avg</sub>
   */
  private static double computePfdForArchitecture(String arch, double lambdaDU,
      double testInterval) {
    if ("1oo2".equalsIgnoreCase(arch)) {
      return SafetyInstrumentedFunction.calculatePfd1oo2(lambdaDU, testInterval);
    }
    if ("2oo3".equalsIgnoreCase(arch)) {
      return SafetyInstrumentedFunction.calculatePfd2oo3(lambdaDU, testInterval);
    }
    return SafetyInstrumentedFunction.calculatePfd1oo1(lambdaDU, testInterval);
  }

  /**
   * Rounds a value.
   *
   * @param value value
   * @param decimals decimals
   * @return rounded value
   */
  private static double round(double value, int decimals) {
    double factor = Math.pow(10, decimals);
    return Math.round(value * factor) / factor;
  }

  /**
   * Error JSON.
   *
   * @param message message
   * @return JSON string
   */
  private static String errorJson(String message) {
    JsonObject err = new JsonObject();
    err.addProperty("status", "error");
    err.addProperty("message", message);
    return err.toString();
  }
}
