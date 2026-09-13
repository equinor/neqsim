package neqsim.mcp.runners;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import neqsim.process.safety.risk.sis.SILVerificationResult;
import neqsim.process.safety.risk.sis.SafetyInstrumentedFunction;

/**
 * MCP runner for bounded Safety Instrumented Function (SIF) PFD screening.
 *
 * <p>
 * Computes average probability of failure on demand from caller-supplied component data and delegates SIL-band
 * classification to NeqSim's canonical {@link SafetyInstrumentedFunction} and {@link SILVerificationResult}. The result
 * is screening evidence only: it does not select or approve a SIL, verify lifecycle assumptions, demonstrate standards
 * conformance, or replace independent functional-safety assessment.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.1
 */
public final class SILRunner {

  /** Maximum accepted serialized request size. */
  private static final int MAX_REQUEST_BYTES = 16384;
  /** Maximum number of component contributions in one request. */
  private static final int MAX_COMPONENTS = 100;
  /** Maximum trimmed name, description, or component-type length. */
  private static final int MAX_TEXT_LENGTH = 256;
  /** Computational admission bound; not an engineering recommendation. */
  private static final double MAX_PROOF_TEST_INTERVAL_HOURS = 87600.0;
  /** Computational admission bound for a caller-supplied hourly failure rate. */
  private static final double MAX_FAILURE_RATE_PER_HOUR = 1.0;
  /** Architectures implemented by the bounded runner. */
  private static final Set<String> SUPPORTED_ARCHITECTURES = new HashSet<String>(Arrays.asList("1oo1", "1oo2", "2oo3"));
  /** Component type labels admitted by the public tool contract. */
  private static final Set<String> SUPPORTED_COMPONENT_TYPES = new HashSet<String>(
      Arrays.asList("sensor", "logic", "finalelement"));
  private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

  private SILRunner() {
  }

  /**
   * Runs a bounded SIF PFD screening calculation from a JSON definition.
   *
   * @param json JSON with SIF metadata and either direct PFD or component contributions
   * @return JSON string with canonical calculation evidence and explicit advisory boundaries
   */
  public static String run(String json) {
    if (json == null || json.trim().isEmpty()) {
      return errorJson("INVALID_INPUT", "JSON input is null or empty");
    }
    if (json.getBytes(StandardCharsets.UTF_8).length > MAX_REQUEST_BYTES) {
      return errorJson("REQUEST_TOO_LARGE", "SIL input exceeds 16384 UTF-8 bytes");
    }
    try {
      JsonElement parsed = JsonParser.parseString(json);
      if (!parsed.isJsonObject()) {
        return errorJson("INVALID_INPUT", "SIL input must be a JSON object");
      }
      JsonObject input = parsed.getAsJsonObject();
      String name = readBoundedString(input, "name", "SIF-001");
      String description = readBoundedString(input, "description", "Safety function");
      if (name == null) {
        return errorJson("INVALID_INPUT", "name must be a non-blank string of at most 256 characters");
      }
      if (description == null) {
        return errorJson("INVALID_INPUT", "description must be a non-blank string of at most 256 characters");
      }
      int claimedSil = readClaimedSil(input);
      String architecture = readArchitecture(input);
      double testIntervalHours = readOptionalFiniteNumber(input, "proofTestInterval_hours", 8760.0);
      if (testIntervalHours <= 0.0 || testIntervalHours > MAX_PROOF_TEST_INTERVAL_HOURS) {
        return errorJson("INVALID_INPUT", "proofTestInterval_hours must be greater than 0 and at most 87600");
      }

      boolean hasComponents = input.has("components");
      boolean hasDirectPfd = input.has("pfdAvg");
      if (hasComponents == hasDirectPfd) {
        return errorJson("INVALID_INPUT", "Provide exactly one of 'components' or top-level 'pfdAvg'");
      }

      double totalPfd;
      JsonArray componentsOut = new JsonArray();
      if (hasComponents) {
        JsonElement componentElement = input.get("components");
        if (!componentElement.isJsonArray()) {
          return errorJson("INVALID_INPUT", "components must be a JSON array");
        }
        JsonArray components = componentElement.getAsJsonArray();
        if (components.size() == 0) {
          return errorJson("INVALID_INPUT", "At least one component is required");
        }
        if (components.size() > MAX_COMPONENTS) {
          return errorJson("TOO_MANY_COMPONENTS", "At most 100 components are allowed");
        }
        totalPfd = 0.0;
        for (int i = 0; i < components.size(); i++) {
          JsonElement element = components.get(i);
          if (!element.isJsonObject()) {
            return errorJson("INVALID_COMPONENT", "components[" + i + "] must be a JSON object");
          }
          JsonObject component = element.getAsJsonObject();
          String componentName = readBoundedString(component, "name", null);
          String componentType = readBoundedString(component, "type", null);
          if (componentName == null) {
            return errorJson("INVALID_COMPONENT",
                "components[" + i + "].name must be a non-blank string of at most 256 characters");
          }
          if (componentType == null || !SUPPORTED_COMPONENT_TYPES.contains(componentType.toLowerCase(Locale.ROOT))) {
            return errorJson("INVALID_COMPONENT",
                "components[" + i + "].type must be one of sensor, logic, or finalElement");
          }
          boolean hasPfd = component.has("pfd");
          boolean hasFailureRate = component.has("lambdaDU_per_hr");
          if (hasPfd == hasFailureRate) {
            return errorJson("INVALID_COMPONENT",
                "components[" + i + "] must provide exactly one of pfd or lambdaDU_per_hr");
          }

          double failureRate = 0.0;
          double componentPfd;
          try {
            if (hasPfd) {
              componentPfd = readFiniteNumber(component, "pfd", "components[" + i + "].pfd");
            } else {
              failureRate = readFiniteNumber(component, "lambdaDU_per_hr", "components[" + i + "].lambdaDU_per_hr");
              if (failureRate <= 0.0 || failureRate > MAX_FAILURE_RATE_PER_HOUR) {
                return errorJson("INVALID_COMPONENT",
                    "components[" + i + "].lambdaDU_per_hr must be greater than 0 and at most 1");
              }
              String componentArchitecture = component.has("architecture") ? readArchitecture(component) : architecture;
              componentPfd = computePfdForArchitecture(componentArchitecture, failureRate, testIntervalHours);
            }
          } catch (IllegalArgumentException invalidComponent) {
            return errorJson("INVALID_COMPONENT", invalidComponent.getMessage());
          }
          if (!Double.isFinite(componentPfd) || componentPfd <= 0.0 || componentPfd > 1.0) {
            return errorJson("INVALID_COMPONENT",
                "components[" + i + "] PFD contribution must be greater than 0 and at most 1");
          }
          totalPfd += componentPfd;
          if (!Double.isFinite(totalPfd) || totalPfd > 1.0) {
            return errorJson("CALCULATION_OUT_OF_RANGE", "Aggregate PFD must be finite and at most 1");
          }

          JsonObject componentOut = new JsonObject();
          componentOut.addProperty("name", componentName);
          componentOut.addProperty("type", componentType);
          componentOut.addProperty("failureRate_per_hr", failureRate);
          componentOut.addProperty("pfdContribution", round(componentPfd, 8));
          componentsOut.add(componentOut);
        }
        for (JsonElement element : componentsOut) {
          JsonObject componentOut = element.getAsJsonObject();
          double contribution = componentOut.get("pfdContribution").getAsDouble();
          componentOut.addProperty("percentOfTotal", round(100.0 * contribution / totalPfd, 2));
        }
      } else {
        totalPfd = readFiniteNumber(input, "pfdAvg", "pfdAvg");
        if (totalPfd <= 0.0 || totalPfd > 1.0) {
          return errorJson("INVALID_INPUT", "pfdAvg must be greater than 0 and at most 1");
        }
      }

      SafetyInstrumentedFunction sif = SafetyInstrumentedFunction.builder().name(name).description(description)
          .sil(claimedSil).pfd(totalPfd).testIntervalHours(testIntervalHours).architecture(architecture).build();
      SILVerificationResult verification = new SILVerificationResult(sif);

      JsonObject out = new JsonObject();
      out.addProperty("status", "success");
      out.addProperty("screeningOnly", true);
      out.addProperty("standardConformanceClaimed", false);
      out.addProperty("standard",
          "Caller-supplied SIF PFD screening; independent functional-safety verification required");
      out.addProperty("standardContext",
          "IEC 61508 and IEC 61511 are context only; this result does not demonstrate conformance");
      out.addProperty("inputBasis",
          hasComponents ? "CALLER_SUPPLIED_COMPONENT_PFD_OR_FAILURE_RATE" : "CALLER_SUPPLIED_DIRECT_PFD_AVG");
      out.addProperty("advisoryBoundary",
          "The caller supplies reliability data and lifecycle assumptions; the result does not select or approve SIL, "
              + "validate SRS completeness, independence, common cause, architecture suitability, diagnostic "
              + "coverage, proof-test effectiveness or systematic capability, certify standards conformance, "
              + "authorize plant action, or replace independent functional-safety assessment and accountable approval");
      JsonArray assumptions = new JsonArray();
      assumptions
          .add("Failure rates, PFD values, proof-test interval, architecture, and claimed SIL are caller supplied "
              + "and unverified");
      assumptions.add(
          "Independence, common cause, diagnostic coverage, proof-test coverage, repair, and systematic capability "
              + "are not modelled by this bounded screening");
      assumptions
          .add("Project SRS, lifecycle evidence, device qualification, operating context, and applicable criteria "
              + "require independent qualified review");
      out.add("assumptions", assumptions);

      JsonObject summary = new JsonObject();
      summary.addProperty("name", sif.getName());
      summary.addProperty("architecture", architecture);
      summary.addProperty("claimedSIL", verification.getClaimedSIL());
      summary.addProperty("achievedSILBand", verification.getAchievedSIL());
      summary.addProperty("claimedBandMetByPfd", verification.isSilAchieved());
      summary.addProperty("pfdAvg", round(verification.getPfdAverage(), 8));
      summary.addProperty("riskReductionFactor", round(sif.getRiskReductionFactor(), 1));
      summary.addProperty("proofTestInterval_hours", testIntervalHours);
      summary.addProperty("proofTestInterval_years", round(sif.getProofTestIntervalYears(), 2));
      summary.addProperty("hardwareFaultTolerance", verification.getHardwareFaultTolerance());
      summary.addProperty("silBandIsIndicative", true);
      summary.addProperty("architectureSuitabilityVerified", false);
      summary.addProperty("diagnosticCoverageVerified", false);
      summary.addProperty("systematicCapabilityVerified", false);
      out.add("screening", summary);
      out.add("components", componentsOut);
      out.add("canonicalCalculation", JsonParser.parseString(verification.toJson()));
      return GSON.toJson(out);
    } catch (IllegalArgumentException e) {
      return errorJson("INVALID_INPUT", e.getMessage());
    } catch (Exception e) {
      return errorJson("INVALID_INPUT", "SIL input could not be processed");
    }
  }

  /** Reads a bounded string. */
  private static String readBoundedString(JsonObject object, String field, String defaultValue) {
    if (!object.has(field)) {
      return defaultValue;
    }
    JsonElement value = object.get(field);
    if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
      return null;
    }
    String text = value.getAsString().trim();
    return text.isEmpty() || text.length() > MAX_TEXT_LENGTH ? null : text;
  }

  /** Reads and validates claimed SIL. */
  private static int readClaimedSil(JsonObject input) {
    if (!input.has("claimedSIL")) {
      return 2;
    }
    double value = readFiniteNumber(input, "claimedSIL", "claimedSIL");
    if (value != Math.rint(value) || value < 1.0 || value > 4.0) {
      throw new IllegalArgumentException("claimedSIL must be an integer from 1 to 4");
    }
    return (int) value;
  }

  /** Reads and normalizes an admitted architecture. */
  private static String readArchitecture(JsonObject input) {
    String architecture = readBoundedString(input, "architecture", "1oo1");
    if (architecture == null) {
      throw new IllegalArgumentException("architecture must be one of 1oo1, 1oo2, or 2oo3");
    }
    String normalized = architecture.toLowerCase(Locale.ROOT);
    if (!SUPPORTED_ARCHITECTURES.contains(normalized)) {
      throw new IllegalArgumentException("architecture must be one of 1oo1, 1oo2, or 2oo3");
    }
    return normalized;
  }

  /** Reads an optional finite number. */
  private static double readOptionalFiniteNumber(JsonObject input, String field, double defaultValue) {
    return input.has(field) ? readFiniteNumber(input, field, field) : defaultValue;
  }

  /** Reads a required finite number with a stable field label. */
  private static double readFiniteNumber(JsonObject input, String field, String displayName) {
    if (!input.has(field)) {
      throw new IllegalArgumentException("Missing required field: " + displayName);
    }
    JsonElement element = input.get(field);
    if (!element.isJsonPrimitive()) {
      throw new IllegalArgumentException(displayName + " must be a finite number");
    }
    JsonPrimitive primitive = element.getAsJsonPrimitive();
    if (!primitive.isNumber()) {
      throw new IllegalArgumentException(displayName + " must be a finite number");
    }
    double value = primitive.getAsDouble();
    if (!Double.isFinite(value)) {
      throw new IllegalArgumentException(displayName + " must be a finite number");
    }
    return value;
  }

  /** Computes component PFD with the canonical NeqSim formulas. */
  private static double computePfdForArchitecture(String architecture, double lambdaDu, double testInterval) {
    if ("1oo2".equals(architecture)) {
      return SafetyInstrumentedFunction.calculatePfd1oo2(lambdaDu, testInterval);
    }
    if ("2oo3".equals(architecture)) {
      return SafetyInstrumentedFunction.calculatePfd2oo3(lambdaDu, testInterval);
    }
    return SafetyInstrumentedFunction.calculatePfd1oo1(lambdaDu, testInterval);
  }

  /** Rounds a value for deterministic presentation. */
  private static double round(double value, int decimals) {
    double factor = Math.pow(10, decimals);
    return Math.round(value * factor) / factor;
  }

  /** Returns a stable fail-closed error response. */
  private static String errorJson(String code, String message) {
    JsonObject error = new JsonObject();
    error.addProperty("status", "error");
    error.addProperty("code", code);
    error.addProperty("message", message);
    error.addProperty("screeningOnly", true);
    error.addProperty("standardConformanceClaimed", false);
    return error.toString();
  }
}
