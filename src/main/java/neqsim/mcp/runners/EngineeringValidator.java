package neqsim.mcp.runners;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Engineering validation layer that checks simulation results against industry design rules,
 * physical constraints, and best-practice ranges.
 *
 * <p>
 * Validation is applied post-simulation to flag issues like compressor efficiency out of range,
 * separator residence time too low, approach temperatures below minimum, hydrate risk, etc. Each
 * check has a severity (ERROR, WARNING, INFO) and a remediation hint.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class EngineeringValidator {

  private static final Gson GSON =
      new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create();

  /**
   * Private constructor — all methods are static.
   */
  private EngineeringValidator() {}

  /**
   * Validates simulation results JSON against engineering rules.
   *
   * @param resultsJson JSON from any runner (process, pipeline, flash, etc.)
   * @param context optional context string ("process", "flash", "pipeline", "compressor", etc.)
   * @return JSON with validation findings
   */
  public static String validate(String resultsJson, String context) {
    List<ValidationFinding> findings = new ArrayList<ValidationFinding>();

    try {
      JsonObject results = JsonParser.parseString(resultsJson).getAsJsonObject();

      // Apply all applicable rule sets
      checkTemperatureRanges(results, findings);
      checkPressureRanges(results, findings);
      checkCompressorRules(results, findings);
      checkSeparatorRules(results, findings);
      checkHeatExchangerRules(results, findings);
      checkPipelineRules(results, findings);
      checkPhaseRules(results, findings);
      checkMassBalance(results, findings);
      checkEnergyBalance(results, findings);
      checkConvergence(results, findings);

    } catch (Exception e) {
      findings.add(new ValidationFinding("PARSE_ERROR", Severity.WARNING,
          "Could not parse results for validation: " + e.getMessage(),
          "Ensure results JSON is well-formed"));
    }

    return buildReport(findings, context);
  }

  /**
   * Validates a specific equipment type with targeted rules.
   *
   * @param equipmentJson JSON describing the equipment
   * @param equipmentType the equipment type (compressor, separator, heatExchanger, etc.)
   * @return JSON with validation findings
   */
  public static String validateEquipment(String equipmentJson, String equipmentType) {
    List<ValidationFinding> findings = new ArrayList<ValidationFinding>();

    try {
      JsonObject eq = JsonParser.parseString(equipmentJson).getAsJsonObject();

      if ("compressor".equalsIgnoreCase(equipmentType)) {
        checkCompressorDesign(eq, findings);
      } else if ("separator".equalsIgnoreCase(equipmentType)) {
        checkSeparatorDesign(eq, findings);
      } else if ("heatExchanger".equalsIgnoreCase(equipmentType)
          || "cooler".equalsIgnoreCase(equipmentType) || "heater".equalsIgnoreCase(equipmentType)) {
        checkHeatExchangerDesign(eq, findings);
      } else if ("pipeline".equalsIgnoreCase(equipmentType)
          || "pipe".equalsIgnoreCase(equipmentType)) {
        checkPipelineDesign(eq, findings);
      } else if ("valve".equalsIgnoreCase(equipmentType)) {
        checkValveDesign(eq, findings);
      }
    } catch (Exception e) {
      findings.add(new ValidationFinding("VALIDATION_ERROR", Severity.WARNING,
          "Validation failed: " + e.getMessage(), "Check equipment JSON format"));
    }

    return buildReport(findings, equipmentType);
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // Temperature checks
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * Checks temperatures against physical and practical limits.
   *
   * @param results the results JSON
   * @param findings the findings list to append to
   */
  private static void checkTemperatureRanges(JsonObject results, List<ValidationFinding> findings) {
    checkNumericField(results, "temperature", -273.15, 1500.0, "C", findings,
        "Temperature out of physical range",
        "Verify temperature input is in correct units (Kelvin vs Celsius)");

    checkNumericField(results, "outletTemperature", -273.15, 1500.0, "C", findings,
        "Outlet temperature out of physical range", "Check heat exchange duty and flow rates");

    // Check for very low temperatures that might indicate hydrate risk
    Double temp = findNumericValue(results, "temperature");
    if (temp != null && temp < -40.0) {
      findings.add(new ValidationFinding("LOW_TEMPERATURE", Severity.WARNING,
          "Temperature below -40C may require special metallurgy (low-temp carbon steel or SS)",
          "Check material selection per NORSOK M-001/ASTM A333"));
    }

    // Check for very high discharge temperature (compressor)
    Double dischargeTemp = findNumericValue(results, "outletTemperature");
    if (dischargeTemp != null && dischargeTemp > 200.0) {
      findings.add(new ValidationFinding("HIGH_DISCHARGE_TEMP", Severity.WARNING,
          "Discharge temperature " + String.format("%.1f", dischargeTemp)
              + "C exceeds typical limit of 200C",
          "Consider adding intercooling or reducing compression ratio"));
    }
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // Pressure checks
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * Checks pressures against physical and practical limits.
   *
   * @param results the results JSON
   * @param findings the findings list to append to
   */
  private static void checkPressureRanges(JsonObject results, List<ValidationFinding> findings) {
    Double pressure = findNumericValue(results, "pressure");
    if (pressure != null) {
      if (pressure < 0.0) {
        findings.add(new ValidationFinding("NEGATIVE_PRESSURE", Severity.ERROR,
            "Pressure is negative: " + pressure + " bara",
            "Pressure must be positive. Check units and input values."));
      }
      if (pressure < 0.01 && pressure >= 0.0) {
        findings.add(new ValidationFinding("NEAR_VACUUM", Severity.INFO,
            "Pressure is near-vacuum: " + pressure + " bara",
            "Verify this is intentional. Near-vacuum conditions may cause convergence issues."));
      }
      if (pressure > 1000.0) {
        findings.add(new ValidationFinding("VERY_HIGH_PRESSURE", Severity.INFO,
            "Pressure " + pressure + " bara exceeds 1000 bar",
            "Ensure EOS is validated for ultra-high pressure conditions"));
      }
    }
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // Compressor checks
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * Checks compressor-specific parameters.
   *
   * @param results the results JSON
   * @param findings the findings list
   */
  private static void checkCompressorRules(JsonObject results, List<ValidationFinding> findings) {
    Double efficiency = findNumericValue(results, "polytropicEfficiency");
    if (efficiency == null) {
      efficiency = findNumericValue(results, "isentropicEfficiency");
    }
    if (efficiency != null) {
      if (efficiency < 0.5) {
        findings.add(new ValidationFinding("LOW_EFFICIENCY", Severity.WARNING,
            "Compressor efficiency " + String.format("%.1f%%", efficiency * 100)
                + " is unusually low",
            "Typical polytropic efficiency: 75-88%. Check efficiency input."));
      }
      if (efficiency > 0.95) {
        findings.add(new ValidationFinding("HIGH_EFFICIENCY", Severity.WARNING,
            "Compressor efficiency " + String.format("%.1f%%", efficiency * 100)
                + " exceeds practical limits",
            "Maximum practical efficiency is ~92% for centrifugal, ~88% for reciprocating"));
      }
    }

    Double compressionRatio = findNumericValue(results, "compressionRatio");
    if (compressionRatio != null && compressionRatio > 4.5) {
      findings.add(new ValidationFinding("HIGH_COMPRESSION_RATIO", Severity.WARNING,
          "Compression ratio " + String.format("%.2f", compressionRatio)
              + " exceeds typical single-stage limit (3.5-4.5)",
          "Consider multi-stage compression with intercooling per API 617"));
    }

    Double power = findNumericValue(results, "power");
    if (power != null && power > 50000.0) {
      findings.add(new ValidationFinding("HIGH_POWER", Severity.INFO,
          "Compressor power " + String.format("%.0f", power) + " kW — verify driver selection",
          "Consider gas turbine or electric motor driver per available power"));
    }
  }

  /**
   * Detailed compressor design validation.
   *
   * @param eq equipment JSON
   * @param findings findings list
   */
  private static void checkCompressorDesign(JsonObject eq, List<ValidationFinding> findings) {
    checkCompressorRules(eq, findings);

    Double suctionPressure = findNumericValue(eq, "inletPressure");
    Double dischargePressure = findNumericValue(eq, "outletPressure");
    if (suctionPressure != null && dischargePressure != null) {
      if (dischargePressure <= suctionPressure) {
        findings.add(new ValidationFinding("PRESSURE_DECREASE", Severity.ERROR,
            "Compressor outlet pressure <= inlet pressure",
            "Compressor must increase pressure. Check connections or use expander instead."));
      }
      double ratio = dischargePressure / suctionPressure;
      if (ratio > 10.0) {
        findings.add(new ValidationFinding("EXTREME_RATIO", Severity.ERROR,
            "Compression ratio " + String.format("%.1f", ratio)
                + " requires multi-stage compression",
            "Split into 2-4 stages with intercooling. Max single-stage ratio ~4.5"));
      }
    }
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // Separator checks
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * Checks separator-specific parameters.
   *
   * @param results the results JSON
   * @param findings the findings list
   */
  private static void checkSeparatorRules(JsonObject results, List<ValidationFinding> findings) {
    Double residenceTime = findNumericValue(results, "liquidResidenceTime");
    if (residenceTime != null) {
      if (residenceTime < 60.0) {
        findings.add(new ValidationFinding("LOW_RESIDENCE_TIME", Severity.WARNING,
            "Liquid residence time " + String.format("%.0f", residenceTime)
                + " seconds is below minimum (60s for 2-phase, 120-180s for 3-phase)",
            "Increase separator volume or reduce liquid flow rate per NORSOK P-001"));
      }
    }

    Double gasVelocity = findNumericValue(results, "gasVelocity");
    if (gasVelocity != null && gasVelocity > 5.0) {
      findings.add(new ValidationFinding("HIGH_GAS_VELOCITY", Severity.WARNING,
          "Gas velocity " + String.format("%.2f", gasVelocity)
              + " m/s exceeds typical separator limit",
          "Maximum gas velocity ~3-5 m/s depending on demister type. Consider larger vessel."));
    }
  }

  /**
   * Detailed separator design validation.
   *
   * @param eq equipment JSON
   * @param findings findings list
   */
  private static void checkSeparatorDesign(JsonObject eq, List<ValidationFinding> findings) {
    checkSeparatorRules(eq, findings);
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // Heat exchanger checks
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * Checks heat exchanger rules.
   *
   * @param results the results JSON
   * @param findings the findings list
   */
  private static void checkHeatExchangerRules(JsonObject results,
      List<ValidationFinding> findings) {
    Double approachTemp = findNumericValue(results, "approachTemperature");
    if (approachTemp != null) {
      if (approachTemp < 3.0) {
        findings.add(new ValidationFinding("LOW_APPROACH_TEMP", Severity.WARNING,
            "Approach temperature " + String.format("%.1f", approachTemp)
                + "C is below practical minimum (3-5C)",
            "Minimum approach: ~3C for gas/gas, ~5C for gas/liquid per TEMA"));
      }
      if (approachTemp < 1.0) {
        findings.add(new ValidationFinding("INFEASIBLE_APPROACH", Severity.ERROR,
            "Approach temperature " + String.format("%.1f", approachTemp)
                + "C is thermodynamically infeasible",
            "Increase duty split or add heat exchange area. Check temperature cross."));
      }
    }

    Double duty = findNumericValue(results, "duty");
    if (duty != null && duty < 0.0) {
      findings.add(new ValidationFinding("NEGATIVE_DUTY", Severity.INFO,
          "Heat duty is negative — heat is being removed (cooling)",
          "Verify that a cooler (not heater) is intended"));
    }
  }

  /**
   * Detailed HX design validation.
   *
   * @param eq equipment JSON
   * @param findings findings list
   */
  private static void checkHeatExchangerDesign(JsonObject eq, List<ValidationFinding> findings) {
    checkHeatExchangerRules(eq, findings);
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // Pipeline checks
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * Checks pipeline-specific parameters.
   *
   * @param results the results JSON
   * @param findings the findings list
   */
  private static void checkPipelineRules(JsonObject results, List<ValidationFinding> findings) {
    Double velocity = findNumericValue(results, "fluidVelocity");
    if (velocity == null) {
      velocity = findNumericValue(results, "velocity");
    }
    if (velocity != null) {
      if (velocity > 25.0) {
        findings.add(new ValidationFinding("HIGH_VELOCITY", Severity.WARNING,
            "Pipeline velocity " + String.format("%.1f", velocity)
                + " m/s exceeds erosional velocity limit (~25 m/s for gas)",
            "Increase pipe diameter or reduce flow rate per API RP 14E"));
      }
      if (velocity < 1.0 && velocity > 0.0) {
        findings.add(new ValidationFinding("LOW_VELOCITY", Severity.INFO,
            "Pipeline velocity " + String.format("%.2f", velocity)
                + " m/s is low — risk of solids/liquid accumulation",
            "Ensure velocity is above minimum for liquid sweeping (~1-3 m/s)"));
      }
    }

    Double pressureDrop = findNumericValue(results, "pressureDrop");
    if (pressureDrop != null) {
      Double inletPressure = findNumericValue(results, "inletPressure");
      if (inletPressure != null && inletPressure > 0) {
        double dpRatio = pressureDrop / inletPressure;
        if (dpRatio > 0.2) {
          findings.add(new ValidationFinding("HIGH_DP_RATIO", Severity.WARNING,
              "Pressure drop is " + String.format("%.0f%%", dpRatio * 100) + " of inlet pressure",
              "Consider larger pipe diameter or shorter pipeline length"));
        }
      }
    }
  }

  /**
   * Detailed pipeline design validation.
   *
   * @param eq equipment JSON
   * @param findings findings list
   */
  private static void checkPipelineDesign(JsonObject eq, List<ValidationFinding> findings) {
    checkPipelineRules(eq, findings);
  }

  /**
   * Valve design validation.
   *
   * @param eq equipment JSON
   * @param findings findings list
   */
  private static void checkValveDesign(JsonObject eq, List<ValidationFinding> findings) {
    Double inletP = findNumericValue(eq, "inletPressure");
    Double outletP = findNumericValue(eq, "outletPressure");
    if (inletP != null && outletP != null) {
      if (outletP >= inletP) {
        findings.add(new ValidationFinding("VALVE_NO_DP", Severity.ERROR,
            "Valve outlet pressure >= inlet pressure",
            "Valve must have a pressure drop. Check connections."));
      }
      double dpRatio = (inletP - outletP) / inletP;
      if (dpRatio > 0.5) {
        findings.add(new ValidationFinding("HIGH_VALVE_DP", Severity.WARNING,
            "Valve pressure drop ratio " + String.format("%.0f%%", dpRatio * 100) + " is very high",
            "Check for choked flow conditions. Consider staged pressure reduction."));
      }
    }
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // Phase, mass balance, energy balance, convergence
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * Checks phase-related issues.
   *
   * @param results the results JSON
   * @param findings the findings list
   */
  private static void checkPhaseRules(JsonObject results, List<ValidationFinding> findings) {
    Double waterDewPoint = findNumericValue(results, "waterDewPoint");
    Double operatingTemp = findNumericValue(results, "temperature");
    if (waterDewPoint != null && operatingTemp != null) {
      if (operatingTemp < waterDewPoint) {
        findings.add(new ValidationFinding("WATER_CONDENSATION", Severity.WARNING,
            "Operating temperature is below water dew point — free water will form",
            "Free water increases corrosion and hydrate risk. Consider dehydration."));
      }
    }
  }

  /**
   * Checks mass balance closure.
   *
   * @param results the results JSON
   * @param findings the findings list
   */
  private static void checkMassBalance(JsonObject results, List<ValidationFinding> findings) {
    Double massBalanceError = findNumericValue(results, "massBalanceError");
    if (massBalanceError != null && Math.abs(massBalanceError) > 0.01) {
      findings.add(new ValidationFinding("MASS_BALANCE", Severity.ERROR,
          "Mass balance error: " + String.format("%.4f%%", massBalanceError * 100),
          "Mass balance should close within 0.01%. Check for leaks in the process."));
    }
  }

  /**
   * Checks energy balance closure.
   *
   * @param results the results JSON
   * @param findings the findings list
   */
  private static void checkEnergyBalance(JsonObject results, List<ValidationFinding> findings) {
    Double energyBalanceError = findNumericValue(results, "energyBalanceError");
    if (energyBalanceError != null && Math.abs(energyBalanceError) > 0.05) {
      findings.add(new ValidationFinding("ENERGY_BALANCE", Severity.WARNING,
          "Energy balance error: " + String.format("%.4f%%", energyBalanceError * 100),
          "Energy balance should close within 5%. Check heat duties and stream connections."));
    }
  }

  /**
   * Checks convergence status.
   *
   * @param results the results JSON
   * @param findings the findings list
   */
  private static void checkConvergence(JsonObject results, List<ValidationFinding> findings) {
    if (results.has("converged")) {
      boolean converged = results.get("converged").getAsBoolean();
      if (!converged) {
        findings.add(
            new ValidationFinding("NOT_CONVERGED", Severity.ERROR, "Simulation did not converge",
                "Try different initial conditions, relaxation, or a simpler thermodynamic model"));
      }
    }
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // Utility methods
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * Recursively searches a JSON object for a numeric field by key name.
   *
   * @param obj the JSON object
   * @param fieldName the field name to search for
   * @return the numeric value, or null if not found
   */
  private static Double findNumericValue(JsonObject obj, String fieldName) {
    if (obj.has(fieldName) && obj.get(fieldName).isJsonPrimitive()
        && obj.getAsJsonPrimitive(fieldName).isNumber()) {
      return obj.get(fieldName).getAsDouble();
    }

    // Search nested objects
    for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
      if (entry.getValue().isJsonObject()) {
        Double found = findNumericValue(entry.getValue().getAsJsonObject(), fieldName);
        if (found != null) {
          return found;
        }
      }
    }
    return null;
  }

  /**
   * Checks a numeric field against min/max bounds.
   *
   * @param results the results JSON
   * @param fieldName the field name
   * @param min the minimum value
   * @param max the maximum value
   * @param unit the unit string
   * @param findings the findings list
   * @param errorMessage the error message
   * @param remediation the remediation hint
   */
  private static void checkNumericField(JsonObject results, String fieldName, double min,
      double max, String unit, List<ValidationFinding> findings, String errorMessage,
      String remediation) {
    Double value = findNumericValue(results, fieldName);
    if (value != null && (value < min || value > max)) {
      findings
          .add(new ValidationFinding("OUT_OF_RANGE", Severity.ERROR, errorMessage + ": " + fieldName
              + " = " + value + " " + unit + " (valid: " + min + " to " + max + ")", remediation));
    }
  }

  /**
   * Builds the validation report JSON.
   *
   * @param findings the list of findings
   * @param context the validation context
   * @return JSON report string
   */
  private static String buildReport(List<ValidationFinding> findings, String context) {
    JsonObject report = new JsonObject();
    report.addProperty("validationContext", context != null ? context : "general");

    int errors = 0;
    int warnings = 0;
    int infos = 0;
    for (ValidationFinding f : findings) {
      if (f.severity == Severity.ERROR) {
        errors++;
      } else if (f.severity == Severity.WARNING) {
        warnings++;
      } else {
        infos++;
      }
    }

    report.addProperty("totalFindings", findings.size());
    report.addProperty("errors", errors);
    report.addProperty("warnings", warnings);
    report.addProperty("infos", infos);
    report.addProperty("passed", errors == 0);

    String verdict;
    if (errors > 0) {
      verdict = "FAIL — " + errors + " error(s) found. Fix before proceeding.";
    } else if (warnings > 0) {
      verdict =
          "PASS_WITH_WARNINGS — " + warnings + " warning(s). Review before finalizing design.";
    } else {
      verdict = "PASS — No issues found.";
    }
    report.addProperty("verdict", verdict);

    JsonArray findingsArray = new JsonArray();
    for (ValidationFinding f : findings) {
      JsonObject fObj = new JsonObject();
      fObj.addProperty("code", f.code);
      fObj.addProperty("severity", f.severity.name());
      fObj.addProperty("message", f.message);
      fObj.addProperty("remediation", f.remediation);
      findingsArray.add(fObj);
    }
    report.add("findings", findingsArray);

    return GSON.toJson(report);
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // Inner types
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * Severity levels for validation findings.
   */
  enum Severity {
    /** Blocking issue — design is incorrect or physically impossible. */
    ERROR,
    /** Non-blocking but important — outside recommended ranges. */
    WARNING,
    /** Informational — worth noting for the engineer. */
    INFO
  }

  /**
   * A single validation finding.
   */
  static class ValidationFinding {
    /** Finding code (machine-readable). */
    final String code;
    /** Severity level. */
    final Severity severity;
    /** Human-readable message. */
    final String message;
    /** Actionable fix suggestion. */
    final String remediation;

    /**
     * Creates a validation finding.
     *
     * @param code the finding code
     * @param severity the severity
     * @param message the message
     * @param remediation the remediation hint
     */
    ValidationFinding(String code, Severity severity, String message, String remediation) {
      this.code = code;
      this.severity = severity;
      this.message = message;
      this.remediation = remediation;
    }
  }
}
