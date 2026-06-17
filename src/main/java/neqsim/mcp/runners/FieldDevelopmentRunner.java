package neqsim.mcp.runners;

import java.util.HashMap;
import java.util.Map;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.mcp.model.ResultProvenance;
import neqsim.process.fielddevelopment.economics.CashFlowEngine;
import neqsim.process.fielddevelopment.economics.CashFlowEngine.CashFlowResult;
import neqsim.process.fielddevelopment.economics.CashFlowEngine.AnnualCashFlow;
import neqsim.process.fielddevelopment.economics.ProductionProfileGenerator;
import neqsim.process.fielddevelopment.economics.ProductionProfileGenerator.DeclineType;

/**
 * Stateless field development economics runner for MCP integration.
 *
 * <p>
 * Supports cash flow analysis with multiple fiscal regimes (Norwegian NCS, UK, Brazil, US-GOM),
 * production profile generation with exponential/hyperbolic/harmonic decline, and breakeven
 * analysis.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class FieldDevelopmentRunner {

  private static final Gson GSON =
      new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create();

  /**
   * Private constructor — all methods are static.
   */
  private FieldDevelopmentRunner() {}

  /**
   * Runs a field development economics calculation from a JSON input.
   *
   * <p>
   * Supports two modes:
   * </p>
   * <ul>
   * <li>"cashflow" — full NPV/IRR/payback analysis with annual cash flow breakdown</li>
   * <li>"productionProfile" — generate decline curve production profiles</li>
   * </ul>
   *
   * @param json the JSON field development specification
   * @return a JSON string with the economics results
   */
  public static String run(String json) {
    if (json == null || json.trim().isEmpty()) {
      return errorJson("INPUT_ERROR", "JSON input is null or empty",
          "Provide a valid JSON field development specification");
    }

    JsonObject input;
    try {
      input = JsonParser.parseString(json).getAsJsonObject();
    } catch (Exception e) {
      return errorJson("JSON_PARSE_ERROR", "Failed to parse JSON: " + e.getMessage(),
          "Ensure the JSON is well-formed");
    }

    String mode = input.has("mode") ? input.get("mode").getAsString().toLowerCase() : "cashflow";

    switch (mode) {
      case "productionprofile":
        return runProductionProfile(input);
      case "cashflow":
      default:
        return runCashFlow(input);
    }
  }

  /**
   * Runs a full cash flow analysis.
   *
   * @param input the JSON input object
   * @return the cash flow results as JSON
   */
  private static String runCashFlow(JsonObject input) {
    long startTime = System.currentTimeMillis();
    try {
      // --- Create cash flow engine ---
      String country = input.has("country") ? input.get("country").getAsString() : "NO";
      CashFlowEngine engine = new CashFlowEngine(country);

      // --- CAPEX ---
      if (input.has("capex")) {
        JsonObject capex = input.getAsJsonObject("capex");
        if (capex.has("schedule")) {
          Map<Integer, Double> schedule = new HashMap<Integer, Double>();
          JsonObject sched = capex.getAsJsonObject("schedule");
          for (Map.Entry<String, JsonElement> entry : sched.entrySet()) {
            schedule.put(Integer.parseInt(entry.getKey()), entry.getValue().getAsDouble());
          }
          engine.setCapexSchedule(schedule);
        } else if (capex.has("totalMusd") && capex.has("year")) {
          engine.setCapex(capex.get("totalMusd").getAsDouble(), capex.get("year").getAsInt());
        }
      }

      // --- OPEX ---
      if (input.has("opex")) {
        JsonObject opex = input.getAsJsonObject("opex");
        if (opex.has("percentOfCapex")) {
          engine.setOpexPercentOfCapex(opex.get("percentOfCapex").getAsDouble());
        }
        if (opex.has("fixedPerYearMusd")) {
          engine.setFixedOpexPerYear(opex.get("fixedPerYearMusd").getAsDouble());
        }
        if (opex.has("variablePerBoe")) {
          engine.setVariableOpexPerBoe(opex.get("variablePerBoe").getAsDouble());
        }
      }

      // --- Prices ---
      if (input.has("oilPrice_usdPerBbl")) {
        engine.setOilPrice(input.get("oilPrice_usdPerBbl").getAsDouble());
      }
      if (input.has("gasPrice_usdPerSm3")) {
        engine.setGasPrice(input.get("gasPrice_usdPerSm3").getAsDouble());
      }
      if (input.has("nglPrice_usdPerBbl")) {
        engine.setNglPrice(input.get("nglPrice_usdPerBbl").getAsDouble());
      }
      if (input.has("gasTariff_usdPerSm3")) {
        engine.setGasTariff(input.get("gasTariff_usdPerSm3").getAsDouble());
      }
      if (input.has("oilTariff_usdPerBbl")) {
        engine.setOilTariff(input.get("oilTariff_usdPerBbl").getAsDouble());
      }

      // --- Production ---
      if (input.has("production")) {
        JsonObject prod = input.getAsJsonObject("production");
        if (prod.has("oil") || prod.has("gas")) {
          Map<Integer, Double> oilProfile =
              prod.has("oil") ? parseYearProfile(prod.getAsJsonObject("oil")) : null;
          Map<Integer, Double> gasProfile =
              prod.has("gas") ? parseYearProfile(prod.getAsJsonObject("gas")) : null;
          Map<Integer, Double> nglProfile =
              prod.has("ngl") ? parseYearProfile(prod.getAsJsonObject("ngl")) : null;
          engine.setProductionProfile(oilProfile, gasProfile, nglProfile);
        }
      }

      // --- Discount rate ---
      double discountRate =
          input.has("discountRate") ? input.get("discountRate").getAsDouble() : 0.08;

      // --- Calculate ---
      CashFlowResult result = engine.calculate(discountRate);

      // --- Build response ---
      JsonObject data = new JsonObject();
      data.addProperty("npv_Musd", result.getNpv());
      data.addProperty("irr", result.getIrr());
      data.addProperty("paybackYears", result.getPaybackYears());
      data.addProperty("totalRevenue_Musd", result.getTotalRevenue());
      data.addProperty("totalCapex_Musd", result.getTotalCapex());
      data.addProperty("totalTax_Musd", result.getTotalTax());
      data.addProperty("projectDuration_years", result.getProjectDuration());
      data.addProperty("country", country);
      data.addProperty("discountRate", discountRate);

      // Annual cash flows
      JsonArray cashflows = new JsonArray();
      for (AnnualCashFlow acf : result.getAnnualCashFlows()) {
        JsonObject row = new JsonObject();
        row.addProperty("year", acf.getYear());
        row.addProperty("grossRevenue_Musd", acf.getGrossRevenue());
        row.addProperty("capex_Musd", acf.getCapex());
        row.addProperty("opex_Musd", acf.getOpex());
        row.addProperty("depreciation_Musd", acf.getDepreciation());
        row.addProperty("corporateTax_Musd", acf.getCorporateTax());
        row.addProperty("petroleumTax_Musd", acf.getPetroleumTax());
        row.addProperty("totalTax_Musd", acf.getTotalTax());
        row.addProperty("afterTaxCashFlow_Musd", acf.getAfterTaxCashFlow());
        row.addProperty("cumulativeCashFlow_Musd", acf.getCumulativeCashFlow());
        row.addProperty("discountedCashFlow_Musd", acf.getDiscountedCashFlow());
        cashflows.add(row);
      }
      data.add("annualCashFlows", cashflows);

      // --- Breakeven prices ---
      if (input.has("calculateBreakeven") && input.get("calculateBreakeven").getAsBoolean()) {
        try {
          data.addProperty("breakevenOilPrice_usdPerBbl",
              engine.calculateBreakevenOilPrice(discountRate));
        } catch (Exception ignored) {
          // breakeven may not converge in all cases
        }
        try {
          data.addProperty("breakevenGasPrice_usdPerSm3",
              engine.calculateBreakevenGasPrice(discountRate));
        } catch (Exception ignored) {
          // not always applicable
        }
      }

      JsonObject response = new JsonObject();
      response.addProperty("status", "success");
      response.add("data", data);

      ResultProvenance provenance = new ResultProvenance();
      provenance.setCalculationType("field development economics (NPV/IRR)");
      provenance.setConverged(true);
      provenance.setComputationTimeMs(System.currentTimeMillis() - startTime);
      response.add("provenance", GSON.toJsonTree(provenance));

      return GSON.toJson(response);
    } catch (Exception e) {
      return errorJson("ECONOMICS_ERROR", "Cash flow calculation failed: " + e.getMessage(),
          "Check CAPEX, production, and price inputs");
    }
  }

  /**
   * Generates a production profile using decline curve analysis.
   *
   * @param input the JSON input object
   * @return the production profile as JSON
   */
  private static String runProductionProfile(JsonObject input) {
    long startTime = System.currentTimeMillis();
    try {
      ProductionProfileGenerator gen = new ProductionProfileGenerator();
      String declineTypeStr =
          input.has("declineType") ? input.get("declineType").getAsString().toUpperCase()
              : "EXPONENTIAL";
      double initialRate =
          input.has("initialRate_bblPerDay") ? input.get("initialRate_bblPerDay").getAsDouble()
              : 10000.0;
      double declineRate =
          input.has("annualDeclineRate") ? input.get("annualDeclineRate").getAsDouble() : 0.10;
      int startYear = input.has("startYear") ? input.get("startYear").getAsInt() : 2025;
      int totalYears = input.has("totalYears") ? input.get("totalYears").getAsInt() : 20;
      double economicLimit =
          input.has("economicLimit_bblPerDay") ? input.get("economicLimit_bblPerDay").getAsDouble()
              : 100.0;

      // Plateau
      boolean hasPlateau = input.has("plateauYears") && input.get("plateauYears").getAsInt() > 0;

      DeclineType declineType;
      switch (declineTypeStr) {
        case "HYPERBOLIC":
          declineType = DeclineType.HYPERBOLIC;
          break;
        case "HARMONIC":
          declineType = DeclineType.HARMONIC;
          break;
        default:
          declineType = DeclineType.EXPONENTIAL;
          break;
      }

      Map<Integer, Double> profile;
      if (hasPlateau) {
        int plateauYears = input.get("plateauYears").getAsInt();
        double bFactor = input.has("bFactor") ? input.get("bFactor").getAsDouble() : 0.5;
        profile = gen.generateWithPlateau(initialRate, plateauYears, declineRate, bFactor,
            declineType, startYear, totalYears, economicLimit);
      } else {
        switch (declineTypeStr) {
          case "HYPERBOLIC": {
            double bFactor = input.has("bFactor") ? input.get("bFactor").getAsDouble() : 0.5;
            profile = gen.generateHyperbolicDecline(initialRate, declineRate, bFactor, startYear,
                totalYears, economicLimit);
            break;
          }
          case "HARMONIC":
            profile = gen.generateHarmonicDecline(initialRate, declineRate, startYear, totalYears,
                economicLimit);
            break;
          default:
            profile = gen.generateExponentialDecline(initialRate, declineRate, startYear,
                totalYears, economicLimit);
            break;
        }
      }

      // Build result
      JsonObject data = new JsonObject();
      data.addProperty("declineType", declineTypeStr);
      data.addProperty("initialRate_bblPerDay", initialRate);
      data.addProperty("annualDeclineRate", declineRate);

      JsonArray years = new JsonArray();
      JsonArray volumes = new JsonArray();
      double totalVolume = 0;
      for (Map.Entry<Integer, Double> entry : profile.entrySet()) {
        years.add(entry.getKey());
        volumes.add(entry.getValue());
        totalVolume += entry.getValue();
      }
      data.add("years", years);
      data.add("annualVolumes_bbl", volumes);
      data.addProperty("totalVolume_bbl", totalVolume);
      data.addProperty("totalVolume_MMbbl", totalVolume / 1e6);

      JsonObject response = new JsonObject();
      response.addProperty("status", "success");
      response.add("data", data);

      ResultProvenance provenance = new ResultProvenance();
      provenance.setCalculationType("production profile (decline curve analysis)");
      provenance.setConverged(true);
      provenance.setComputationTimeMs(System.currentTimeMillis() - startTime);
      response.add("provenance", GSON.toJsonTree(provenance));

      return GSON.toJson(response);
    } catch (Exception e) {
      return errorJson("PROFILE_ERROR", "Production profile generation failed: " + e.getMessage(),
          "Check decline parameters");
    }
  }

  /**
   * Parses a JSON object of year:value pairs into a Map.
   *
   * @param obj the JSON object
   * @return the year-to-value map
   */
  private static Map<Integer, Double> parseYearProfile(JsonObject obj) {
    Map<Integer, Double> map = new HashMap<Integer, Double>();
    for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
      map.put(Integer.parseInt(entry.getKey()), entry.getValue().getAsDouble());
    }
    return map;
  }

  /**
   * Creates a standard error JSON string.
   *
   * @param code the error code
   * @param message the error message
   * @param remediation the fix suggestion
   * @return the error JSON string
   */
  private static String errorJson(String code, String message, String remediation) {
    JsonObject error = new JsonObject();
    error.addProperty("status", "error");
    JsonArray errors = new JsonArray();
    JsonObject err = new JsonObject();
    err.addProperty("code", code);
    err.addProperty("message", message);
    err.addProperty("remediation", remediation);
    errors.add(err);
    error.add("errors", errors);
    return GSON.toJson(error);
  }
}
