package neqsim.process.mechanicaldesign.heatexchanger;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.GsonBuilder;
import neqsim.process.costestimation.heatexchanger.BAHXCostEstimator;
import neqsim.process.costestimation.heatexchanger.HeatExchangerCostEstimate;
import neqsim.process.equipment.heatexchanger.HeatExchanger;
import neqsim.process.equipment.heatexchanger.LNGHeatExchanger;
import neqsim.process.mechanicaldesign.MechanicalDesign;

/**
 * Design feasibility report for heat exchangers.
 *
 * <p>
 * Generates a comprehensive feasibility assessment including:
 * </p>
 * <ul>
 * <li>Mechanical design calculations (ASME, TEMA, ALPEMA)</li>
 * <li>Cost estimation (CAPEX, OPEX, lifecycle)</li>
 * <li>Supplier matching from the HeatExchangerSuppliers database</li>
 * <li>Feasibility checks with BLOCKER/WARNING/INFO severity</li>
 * <li>Overall verdict: FEASIBLE / FEASIBLE_WITH_WARNINGS / NOT_FEASIBLE</li>
 * </ul>
 *
 * <p>
 * Supports both standard heat exchangers (shell-and-tube, plate, air-cooler, double-pipe) and LNG
 * cryogenic BAHX (plate-fin). When used with {@link LNGHeatExchanger}, additional BAHX-specific
 * checks are performed: mercury risk, freeze-out risk, thermal stress, flow maldistribution, and
 * MITA margin.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public class HeatExchangerDesignFeasibilityReport {

  // ============================================================================
  // Fields
  // ============================================================================

  /** The heat exchanger equipment. */
  private final Object heatExchanger;

  /** Whether the equipment is an LNGHeatExchanger. */
  private final boolean isBAHX;

  /** Exchanger type for supplier matching and cost. */
  private String exchangerType = "shell-tube";

  /** Design standard code. */
  private String designStandard = "TEMA-R";

  /** Annual operating hours for OPEX. */
  private int annualOperatingHours = 8000;

  // ── Mechanical design ──
  private MechanicalDesign mechanicalDesign;

  // ── Cost estimation ──
  private double purchasedEquipmentCostUSD = 0.0;
  private double installedCostUSD = 0.0;
  private double annualMaintenanceCostUSD = 0.0;
  private Map<String, Object> costBreakdown;

  // ── Feasibility ──
  private boolean feasible = true;
  private boolean reportGenerated = false;
  private List<FeasibilityIssue> issues = new ArrayList<FeasibilityIssue>();
  private List<SupplierMatch> matchingSuppliers = new ArrayList<SupplierMatch>();

  // ── Operating point ──
  private double dutyKW = 0.0;
  private double hotInletTempC = 0.0;
  private double hotOutletTempC = 0.0;
  private double coldInletTempC = 0.0;
  private double coldOutletTempC = 0.0;
  private double maxPressureBara = 0.0;
  private double minTempC = 0.0;
  private double heatTransferAreaM2 = 0.0;

  // ============================================================================
  // Constructors
  // ============================================================================

  /**
   * Construct a feasibility report for a standard HeatExchanger.
   *
   * @param heatExchanger the heat exchanger to assess
   */
  public HeatExchangerDesignFeasibilityReport(HeatExchanger heatExchanger) {
    this.heatExchanger = heatExchanger;
    this.isBAHX = false;
  }

  /**
   * Construct a feasibility report for an LNGHeatExchanger (BAHX).
   *
   * @param lngHeatExchanger the LNG BAHX to assess
   */
  public HeatExchangerDesignFeasibilityReport(LNGHeatExchanger lngHeatExchanger) {
    this.heatExchanger = lngHeatExchanger;
    this.isBAHX = true;
    this.exchangerType = "plate-fin";
    this.designStandard = "ASME-VIII-Div1";
  }

  // ============================================================================
  // Configuration setters (method chaining)
  // ============================================================================

  /**
   * Set the exchanger type for supplier matching.
   *
   * @param type exchanger type ("shell-tube", "plate", "plate-fin", "air-cooler", "double-pipe")
   * @return this report for method chaining
   */
  public HeatExchangerDesignFeasibilityReport setExchangerType(String type) {
    this.exchangerType = type;
    return this;
  }

  /**
   * Set the design standard code.
   *
   * @param standard design standard (e.g. "TEMA-R", "ASME-VIII-Div1", "ALPEMA")
   * @return this report for method chaining
   */
  public HeatExchangerDesignFeasibilityReport setDesignStandard(String standard) {
    this.designStandard = standard;
    return this;
  }

  /**
   * Set annual operating hours for OPEX estimation.
   *
   * @param hours annual operating hours
   * @return this report for method chaining
   */
  public HeatExchangerDesignFeasibilityReport setAnnualOperatingHours(int hours) {
    this.annualOperatingHours = hours;
    return this;
  }

  // ============================================================================
  // Report generation
  // ============================================================================

  /**
   * Generate the complete feasibility report.
   *
   * <p>
   * Runs all assessment steps in sequence: capture operating point, run mechanical design, run cost
   * estimation, match suppliers, check feasibility, determine verdict.
   * </p>
   */
  public void generateReport() {
    issues.clear();
    matchingSuppliers.clear();
    feasible = true;

    captureOperatingPoint();
    runMechanicalDesign();
    runCostEstimation();
    matchSuppliers();
    runFeasibilityChecks();

    // Determine verdict
    for (FeasibilityIssue issue : issues) {
      if (issue.getSeverity() == IssueSeverity.BLOCKER) {
        feasible = false;
        break;
      }
    }

    reportGenerated = true;
  }

  /**
   * Capture the current operating point from the heat exchanger.
   */
  private void captureOperatingPoint() {
    if (isBAHX) {
      LNGHeatExchanger lngHX = (LNGHeatExchanger) heatExchanger;
      dutyKW = Math.abs(lngHX.getDuty());

      // Get composite curves for temperature data
      double[][] hotCurve = lngHX.getHotCompositeCurve();
      double[][] coldCurve = lngHX.getColdCompositeCurve();
      if (hotCurve != null && hotCurve.length > 1) {
        hotInletTempC = hotCurve[hotCurve.length - 1][1];
        hotOutletTempC = hotCurve[0][1];
      }
      if (coldCurve != null && coldCurve.length > 1) {
        coldInletTempC = coldCurve[0][1];
        coldOutletTempC = coldCurve[coldCurve.length - 1][1];
      }

      // Pressure from streams
      List<neqsim.process.equipment.stream.StreamInterface> inStreams = lngHX.getInletStreams();
      for (neqsim.process.equipment.stream.StreamInterface s : inStreams) {
        double p = s.getPressure("bara");
        if (p > maxPressureBara) {
          maxPressureBara = p;
        }
        double t = s.getTemperature("C");
        if (t < minTempC || minTempC == 0.0) {
          minTempC = t;
        }
      }

      // Area from core geometry or UA
      LNGHeatExchanger.CoreGeometry core = lngHX.getCoreGeometry();
      if (core != null && core.getWeight() > 0) {
        // Estimate area from core volume and surface area density
        double coreVolume = core.getLength() * core.getWidth() * core.getHeight();
        heatTransferAreaM2 = coreVolume * 1000.0; // beta ~ 1000 m2/m3
      }
      double[] uaPerZone = lngHX.getUAPerZone();
      double totalUA = 0;
      for (double ua : uaPerZone) {
        totalUA += ua;
      }
      if (totalUA > 0) {
        heatTransferAreaM2 = totalUA / 400.0; // h ~ 400 W/(m2 K)
      }
    } else {
      HeatExchanger hx = (HeatExchanger) heatExchanger;
      dutyKW = Math.abs(hx.getDuty());
      hotInletTempC = hx.getInStream(0).getTemperature("C");
      hotOutletTempC = hx.getOutStream(0).getTemperature("C");
      if (hx.getInStream(1) != null) {
        coldInletTempC = hx.getInStream(1).getTemperature("C");
        coldOutletTempC = hx.getOutStream(1).getTemperature("C");
      }
      maxPressureBara = Math.max(hx.getInStream(0).getPressure("bara"),
          hx.getInStream(1) != null ? hx.getInStream(1).getPressure("bara") : 0);
      minTempC = Math.min(hotOutletTempC, coldInletTempC);
    }
  }

  /**
   * Run mechanical design calculations.
   */
  private void runMechanicalDesign() {
    if (isBAHX) {
      LNGHeatExchanger lngHX = (LNGHeatExchanger) heatExchanger;
      BAHXMechanicalDesign bahxDesign = new BAHXMechanicalDesign(lngHX);
      bahxDesign.setMaxOperationPressure(maxPressureBara);
      bahxDesign.setMaxOperationTemperature(Math.max(hotInletTempC, coldOutletTempC) + 273.15);
      bahxDesign.calcDesign();
      this.mechanicalDesign = bahxDesign;
    } else {
      HeatExchanger hx = (HeatExchanger) heatExchanger;
      HeatExchangerMechanicalDesign hxDesign = new HeatExchangerMechanicalDesign(hx);
      hxDesign.setMaxOperationPressure(maxPressureBara);
      hxDesign.setMaxOperationTemperature(Math.max(hotInletTempC, coldOutletTempC) + 273.15);
      hxDesign.calcDesign();
      this.mechanicalDesign = hxDesign;
    }
  }

  /**
   * Run cost estimation.
   */
  private void runCostEstimation() {
    if (isBAHX && mechanicalDesign instanceof BAHXMechanicalDesign) {
      BAHXMechanicalDesign bahxDesign = (BAHXMechanicalDesign) mechanicalDesign;
      BAHXCostEstimator costEst = new BAHXCostEstimator(bahxDesign);
      purchasedEquipmentCostUSD = costEst.getEquipmentCostUSD();
      installedCostUSD = costEst.getInstalledCostUSD();
      annualMaintenanceCostUSD = costEst.getAnnualMaintenanceCostUSD();
      costBreakdown = costEst.getCostBreakdown();
    } else {
      HeatExchangerCostEstimate costEst =
          new HeatExchangerCostEstimate((HeatExchangerMechanicalDesign) mechanicalDesign);
      costEst.setExchangerType(exchangerType);
      purchasedEquipmentCostUSD = costEst.getPurchasedEquipmentCost();
      installedCostUSD = purchasedEquipmentCostUSD * 3.5;
      annualMaintenanceCostUSD = purchasedEquipmentCostUSD * 0.03;
      costBreakdown = new LinkedHashMap<String, Object>();
      costBreakdown.put("purchasedEquipmentCost_USD", round(purchasedEquipmentCostUSD, 0));
      costBreakdown.put("installedCost_USD", round(installedCostUSD, 0));
    }
  }

  /**
   * Match suppliers from the HeatExchangerSuppliers.csv database.
   */
  private void matchSuppliers() {
    List<SupplierMatch> allSuppliers = loadSupplierDatabase();
    for (SupplierMatch supplier : allSuppliers) {
      if (isSupplierMatch(supplier)) {
        matchingSuppliers.add(supplier);
      }
    }
  }

  /**
   * Check if a supplier matches the current operating requirements.
   *
   * @param supplier the supplier data
   * @return true if the supplier can potentially provide this exchanger
   */
  private boolean isSupplierMatch(SupplierMatch supplier) {
    // Check exchanger type
    if (!typeMatches(supplier.getExchangerType(), exchangerType)) {
      return false;
    }

    // Check duty range
    if (dutyKW > 0) {
      if (dutyKW < supplier.getMinDutyKW() || dutyKW > supplier.getMaxDutyKW()) {
        return false;
      }
    }

    // Check area range
    if (heatTransferAreaM2 > 0) {
      if (heatTransferAreaM2 < supplier.getMinAreaM2()
          || heatTransferAreaM2 > supplier.getMaxAreaM2()) {
        return false;
      }
    }

    // Check pressure
    if (maxPressureBara > supplier.getMaxPressureBara()) {
      return false;
    }

    // Check temperature
    if (minTempC < supplier.getMinTemperatureC()) {
      return false;
    }

    return true;
  }

  /**
   * Check if the supplier type matches the requested type.
   *
   * @param supplierType supplier's exchanger type
   * @param requestedType requested exchanger type
   * @return true if types are compatible
   */
  private boolean typeMatches(String supplierType, String requestedType) {
    if (supplierType == null || requestedType == null) {
      return false;
    }
    String sLower = supplierType.toLowerCase();
    String rLower = requestedType.toLowerCase();

    if (sLower.equals(rLower)) {
      return true;
    }

    // BAHX / plate-fin matches "plate" suppliers that handle cryogenic
    if (rLower.contains("plate-fin") || rLower.contains("bahx")) {
      return sLower.contains("plate");
    }

    // shell-tube matches shell-tube
    if (rLower.contains("shell") && sLower.contains("shell")) {
      return true;
    }

    return false;
  }

  // ============================================================================
  // Feasibility Checks
  // ============================================================================

  /**
   * Run all feasibility checks.
   */
  private void runFeasibilityChecks() {
    checkTemperatureRange();
    checkPressureRange();
    checkAreaRange();
    checkCostReasonableness();
    checkSupplierAvailability();

    if (isBAHX) {
      checkBAHXSpecific();
    }
  }

  /**
   * Check temperature range feasibility.
   */
  private void checkTemperatureRange() {
    if (minTempC < -269.0) {
      issues.add(new FeasibilityIssue(IssueSeverity.BLOCKER, "TEMPERATURE", "Minimum temperature "
          + String.format("%.1f", minTempC) + " C is below absolute zero limit for BAHX."));
    } else if (minTempC < -196.0 && !isBAHX) {
      issues.add(new FeasibilityIssue(IssueSeverity.WARNING, "TEMPERATURE",
          "Temperature " + String.format("%.1f", minTempC)
              + " C requires cryogenic materials. Consider BAHX or stainless steel."));
    }

    double maxTempC = Math.max(hotInletTempC, coldOutletTempC);
    if (isBAHX && maxTempC > 65.0) {
      issues.add(new FeasibilityIssue(IssueSeverity.WARNING, "TEMPERATURE",
          "Hot inlet temperature " + String.format("%.1f", maxTempC)
              + " C exceeds typical BAHX limit of 65 C for aluminium brazing."));
    }
  }

  /**
   * Check pressure range feasibility.
   */
  private void checkPressureRange() {
    if (isBAHX && maxPressureBara > 120.0) {
      issues.add(new FeasibilityIssue(IssueSeverity.BLOCKER, "PRESSURE",
          "Pressure " + String.format("%.1f", maxPressureBara)
              + " bara exceeds typical BAHX limit of 120 bara."));
    } else if (isBAHX && maxPressureBara > 80.0) {
      issues.add(new FeasibilityIssue(IssueSeverity.WARNING, "PRESSURE",
          "Pressure " + String.format("%.1f", maxPressureBara)
              + " bara is high for BAHX. Verify with vendor."));
    }
  }

  /**
   * Check heat transfer area range.
   */
  private void checkAreaRange() {
    if (isBAHX && heatTransferAreaM2 > 30000.0) {
      issues.add(new FeasibilityIssue(IssueSeverity.WARNING, "AREA",
          "Heat transfer area " + String.format("%.0f", heatTransferAreaM2)
              + " m2 exceeds typical single BAHX core limit. "
              + "Consider multiple cores in parallel."));
    }
    if (heatTransferAreaM2 <= 0) {
      issues.add(new FeasibilityIssue(IssueSeverity.INFO, "AREA",
          "Heat transfer area not calculated. Run the exchanger first."));
    }
  }

  /**
   * Check cost reasonableness.
   */
  private void checkCostReasonableness() {
    if (purchasedEquipmentCostUSD > 0 && heatTransferAreaM2 > 0) {
      double costPerM2 = purchasedEquipmentCostUSD / heatTransferAreaM2;
      if (isBAHX) {
        if (costPerM2 > 3000.0) {
          issues.add(new FeasibilityIssue(IssueSeverity.INFO, "COST",
              "Specific cost " + String.format("%.0f", costPerM2)
                  + " USD/m2 is high for BAHX. Typical range is 800-2000 USD/m2."));
        } else if (costPerM2 < 500.0) {
          issues.add(new FeasibilityIssue(IssueSeverity.INFO, "COST", "Specific cost "
              + String.format("%.0f", costPerM2) + " USD/m2 is low. Estimate may be optimistic."));
        }
      }
    }
  }

  /**
   * Check if any suppliers were matched.
   */
  private void checkSupplierAvailability() {
    if (matchingSuppliers.isEmpty()) {
      issues.add(new FeasibilityIssue(IssueSeverity.WARNING, "SUPPLIER",
          "No matching suppliers found in database for specified requirements. "
              + "Custom inquiry required."));
    }
  }

  /**
   * Run BAHX-specific feasibility checks.
   */
  private void checkBAHXSpecific() {
    LNGHeatExchanger lngHX = (LNGHeatExchanger) heatExchanger;

    // Mercury risk
    if (lngHX.isMercuryRiskPresent()) {
      issues.add(new FeasibilityIssue(IssueSeverity.BLOCKER, "MERCURY",
          "Mercury risk detected. Aluminium BAHX is at risk of liquid metal "
              + "embrittlement. Install mercury removal upstream."));
    }

    // Freeze-out risk
    if (lngHX.hasFreezeOutRisk()) {
      issues.add(new FeasibilityIssue(IssueSeverity.WARNING, "FREEZE_OUT",
          "CO2 or heavy hydrocarbon freeze-out risk detected in one or more zones. "
              + "Review feed composition and consider upstream removal."));
    }

    // Thermal stress
    if (lngHX.hasThermalStressWarning()) {
      issues.add(new FeasibilityIssue(IssueSeverity.WARNING, "THERMAL_STRESS",
          "Thermal gradient exceeds recommended limit in one or more zones. "
              + "Risk of fatigue cracking at brazed joints. "
              + "Consider lower cool-down rate or additional zones."));
    }

    // MITA check
    double mita = lngHX.getMITA();
    if (mita < 1.0 && mita > 0) {
      issues.add(new FeasibilityIssue(IssueSeverity.WARNING, "MITA",
          "MITA of " + String.format("%.2f", mita) + " C is very small. Risk of temperature cross. "
              + "Consider adding heat transfer area."));
    } else if (mita > 5.0) {
      issues.add(new FeasibilityIssue(IssueSeverity.INFO, "MITA", "MITA of "
          + String.format("%.1f", mita) + " C is large. The exchanger may be oversized."));
    }

    // Thermal fatigue
    if (mechanicalDesign instanceof BAHXMechanicalDesign) {
      BAHXMechanicalDesign bahxDesign = (BAHXMechanicalDesign) mechanicalDesign;
      if (!bahxDesign.isFatiguePassed()) {
        issues.add(new FeasibilityIssue(IssueSeverity.BLOCKER, "FATIGUE",
            "Thermal fatigue utilisation "
                + String.format("%.2f", bahxDesign.getFatigueUtilisation())
                + " exceeds 1.0. Reduce thermal gradients or increase core length."));
      } else if (bahxDesign.getFatigueUtilisation() > 0.5) {
        issues.add(new FeasibilityIssue(IssueSeverity.WARNING, "FATIGUE",
            "Thermal fatigue utilisation "
                + String.format("%.2f", bahxDesign.getFatigueUtilisation())
                + " is above 50%. Monitor thermal cycles closely."));
      }
    }

    // Exergy efficiency
    double etaII = lngHX.getSecondLawEfficiency();
    if (etaII > 0 && etaII < 0.80) {
      issues.add(new FeasibilityIssue(IssueSeverity.WARNING, "EXERGY",
          "Second-law efficiency " + String.format("%.1f", etaII * 100)
              + "% is below typical MCHE target of 85%. "
              + "Consider adding zones or revising temperature approaches."));
    }
  }

  // ============================================================================
  // Supplier Database Loading
  // ============================================================================

  /**
   * Load supplier data from HeatExchangerSuppliers.csv.
   *
   * @return list of all suppliers
   */
  private List<SupplierMatch> loadSupplierDatabase() {
    List<SupplierMatch> suppliers = new ArrayList<SupplierMatch>();
    try {
      InputStream is = getClass().getResourceAsStream("/designdata/HeatExchangerSuppliers.csv");
      if (is == null) {
        return suppliers;
      }
      BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
      String header = reader.readLine(); // skip header
      String line;
      while ((line = reader.readLine()) != null) {
        if (line.trim().isEmpty()) {
          continue;
        }
        SupplierMatch supplier = parseSupplierLine(line);
        if (supplier != null) {
          suppliers.add(supplier);
        }
      }
      reader.close();
    } catch (Exception ex) {
      // Return empty list if file not found
    }
    return suppliers;
  }

  /**
   * Parse a CSV line into a SupplierMatch.
   *
   * @param line CSV line
   * @return supplier data or null
   */
  private SupplierMatch parseSupplierLine(String line) {
    try {
      List<String> fields = parseCsvLine(line);
      if (fields.size() < 15) {
        return null;
      }
      SupplierMatch supplier = new SupplierMatch();
      supplier.setManufacturer(fields.get(1));
      supplier.setExchangerType(fields.get(2));
      supplier.setMinDutyKW(parseDouble(fields.get(3), 0));
      supplier.setMaxDutyKW(parseDouble(fields.get(4), Double.MAX_VALUE));
      supplier.setMinAreaM2(parseDouble(fields.get(5), 0));
      supplier.setMaxAreaM2(parseDouble(fields.get(6), Double.MAX_VALUE));
      supplier.setMinPressureBara(parseDouble(fields.get(7), 0));
      supplier.setMaxPressureBara(parseDouble(fields.get(8), Double.MAX_VALUE));
      supplier.setMinTemperatureC(parseDouble(fields.get(9), -273));
      supplier.setMaxTemperatureC(parseDouble(fields.get(10), 1000));
      supplier.setTemaTypes(fields.get(11));
      supplier.setMaterials(fields.get(12));
      supplier.setApplications(fields.get(13));
      supplier.setWebsite(fields.get(14));
      if (fields.size() > 15) {
        supplier.setNotes(fields.get(15));
      }
      return supplier;
    } catch (Exception ex) {
      return null;
    }
  }

  /**
   * Parse a quoted CSV line.
   *
   * @param line the CSV line
   * @return list of field values
   */
  private List<String> parseCsvLine(String line) {
    List<String> fields = new ArrayList<String>();
    boolean inQuotes = false;
    StringBuilder current = new StringBuilder();
    for (int i = 0; i < line.length(); i++) {
      char c = line.charAt(i);
      if (c == '"') {
        inQuotes = !inQuotes;
      } else if (c == ',' && !inQuotes) {
        fields.add(current.toString().trim());
        current = new StringBuilder();
      } else {
        current.append(c);
      }
    }
    fields.add(current.toString().trim());
    return fields;
  }

  /**
   * Safely parse a double value.
   *
   * @param value string value
   * @param defaultValue default if parsing fails
   * @return parsed value
   */
  private double parseDouble(String value, double defaultValue) {
    try {
      return Double.parseDouble(value.trim());
    } catch (NumberFormatException e) {
      return defaultValue;
    }
  }

  // ============================================================================
  // Results Access
  // ============================================================================

  /**
   * Get the overall feasibility verdict.
   *
   * @return "FEASIBLE", "FEASIBLE_WITH_WARNINGS", or "NOT_FEASIBLE"
   */
  public String getVerdict() {
    if (!reportGenerated) {
      generateReport();
    }
    if (!feasible) {
      return "NOT_FEASIBLE";
    }
    for (FeasibilityIssue issue : issues) {
      if (issue.getSeverity() == IssueSeverity.WARNING) {
        return "FEASIBLE_WITH_WARNINGS";
      }
    }
    return "FEASIBLE";
  }

  /**
   * Check if the design is feasible (no blockers).
   *
   * @return true if feasible
   */
  public boolean isFeasible() {
    if (!reportGenerated) {
      generateReport();
    }
    return feasible;
  }

  /**
   * Get the list of feasibility issues.
   *
   * @return list of issues
   */
  public List<FeasibilityIssue> getIssues() {
    return issues;
  }

  /**
   * Get the list of matching suppliers.
   *
   * @return list of supplier matches
   */
  public List<SupplierMatch> getMatchingSuppliers() {
    return matchingSuppliers;
  }

  /**
   * Get the number of matching suppliers.
   *
   * @return number of matching suppliers
   */
  public int getNumberOfMatchingSuppliers() {
    return matchingSuppliers.size();
  }

  /**
   * Get the mechanical design.
   *
   * @return mechanical design object
   */
  public MechanicalDesign getMechanicalDesign() {
    return mechanicalDesign;
  }

  /**
   * Get the purchased equipment cost (ex-works) in USD.
   *
   * @return equipment cost
   */
  public double getPurchasedEquipmentCostUSD() {
    return purchasedEquipmentCostUSD;
  }

  /**
   * Get the total installed cost in USD.
   *
   * @return installed cost
   */
  public double getInstalledCostUSD() {
    return installedCostUSD;
  }

  /**
   * Get the complete feasibility report as JSON.
   *
   * @return JSON string with all report data
   */
  public String toJson() {
    if (!reportGenerated) {
      generateReport();
    }

    Map<String, Object> report = new LinkedHashMap<String, Object>();

    // Verdict
    report.put("verdict", getVerdict());
    report.put("feasible", feasible);
    report.put("exchangerType", exchangerType);
    report.put("designStandard", designStandard);

    // Operating point
    Map<String, Object> opPoint = new LinkedHashMap<String, Object>();
    opPoint.put("duty_kW", round(dutyKW, 1));
    opPoint.put("hotInletTemperature_C", round(hotInletTempC, 1));
    opPoint.put("hotOutletTemperature_C", round(hotOutletTempC, 1));
    opPoint.put("coldInletTemperature_C", round(coldInletTempC, 1));
    opPoint.put("coldOutletTemperature_C", round(coldOutletTempC, 1));
    opPoint.put("maxPressure_bara", round(maxPressureBara, 1));
    opPoint.put("minTemperature_C", round(minTempC, 1));
    opPoint.put("heatTransferArea_m2", round(heatTransferAreaM2, 0));
    report.put("operatingPoint", opPoint);

    // Mechanical design
    if (mechanicalDesign != null) {
      Map<String, Object> mechDesign = new LinkedHashMap<String, Object>();
      mechDesign.put("designStandard", designStandard);
      mechDesign.put("totalWeight_kg", round(mechanicalDesign.getWeightTotal(), 0));
      mechDesign.put("wallThickness_mm", round(mechanicalDesign.getWallThickness(), 2));

      if (mechanicalDesign instanceof BAHXMechanicalDesign) {
        BAHXMechanicalDesign bahx = (BAHXMechanicalDesign) mechanicalDesign;
        mechDesign.put("coreMaterial", bahx.getCoreMaterialGrade());
        mechDesign.put("headerMaterial", bahx.getHeaderMaterialGrade());
        mechDesign.put("coreLength_m", round(bahx.getCoreLengthM(), 2));
        mechDesign.put("coreWidth_m", round(bahx.getCoreWidthM(), 2));
        mechDesign.put("coreHeight_m", round(bahx.getCoreHeightM(), 2));
        mechDesign.put("requiredPartingSheetThickness_mm",
            round(bahx.getRequiredPartingSheetThicknessMm(), 2));
        mechDesign.put("requiredHeaderThickness_mm", round(bahx.getRequiredHeaderThicknessMm(), 1));
        mechDesign.put("maxThermalGradient_CperM", round(bahx.getMaxThermalGradient(), 2));
        mechDesign.put("fatigueUtilisation", round(bahx.getFatigueUtilisation(), 4));
        mechDesign.put("fatiguePassed", bahx.isFatiguePassed());
      }
      report.put("mechanicalDesign", mechDesign);
    }

    // Cost estimation
    if (costBreakdown != null) {
      report.put("costEstimation", costBreakdown);
    } else {
      Map<String, Object> cost = new LinkedHashMap<String, Object>();
      cost.put("purchasedEquipmentCost_USD", round(purchasedEquipmentCostUSD, 0));
      cost.put("totalInstalledCost_USD", round(installedCostUSD, 0));
      cost.put("annualMaintenanceCost_USD", round(annualMaintenanceCostUSD, 0));
      if (heatTransferAreaM2 > 0 && purchasedEquipmentCostUSD > 0) {
        cost.put("specificCost_USDperM2", round(purchasedEquipmentCostUSD / heatTransferAreaM2, 0));
      }
      report.put("costEstimation", cost);
    }

    // Matching suppliers
    List<Map<String, Object>> supplierList = new ArrayList<Map<String, Object>>();
    for (SupplierMatch supplier : matchingSuppliers) {
      Map<String, Object> sup = new LinkedHashMap<String, Object>();
      sup.put("manufacturer", supplier.getManufacturer());
      sup.put("exchangerType", supplier.getExchangerType());
      sup.put("dutyRange_kW", supplier.getMinDutyKW() + " - " + supplier.getMaxDutyKW());
      sup.put("areaRange_m2", supplier.getMinAreaM2() + " - " + supplier.getMaxAreaM2());
      sup.put("maxPressure_bara", supplier.getMaxPressureBara());
      sup.put("temperatureRange_C",
          supplier.getMinTemperatureC() + " to " + supplier.getMaxTemperatureC());
      sup.put("materials", supplier.getMaterials());
      sup.put("applications", supplier.getApplications());
      sup.put("website", supplier.getWebsite());
      supplierList.add(sup);
    }
    report.put("matchingSuppliers", supplierList);
    report.put("numberOfMatchingSuppliers", matchingSuppliers.size());

    // BAHX-specific diagnostics
    if (isBAHX) {
      LNGHeatExchanger lngHX = (LNGHeatExchanger) heatExchanger;
      Map<String, Object> bahxDiag = new LinkedHashMap<String, Object>();
      bahxDiag.put("MITA_C", round(lngHX.getMITA(), 2));
      bahxDiag.put("secondLawEfficiency", round(lngHX.getSecondLawEfficiency(), 4));
      bahxDiag.put("totalExergyDestruction_kW", round(lngHX.getTotalExergyDestruction(), 1));
      bahxDiag.put("hasFreezeOutRisk", lngHX.hasFreezeOutRisk());
      bahxDiag.put("hasThermalStressWarning", lngHX.hasThermalStressWarning());
      bahxDiag.put("hasMercuryRisk", lngHX.isMercuryRiskPresent());
      report.put("bahxDiagnostics", bahxDiag);
    }

    // Issues
    List<Map<String, Object>> issueList = new ArrayList<Map<String, Object>>();
    for (FeasibilityIssue issue : issues) {
      Map<String, Object> iss = new LinkedHashMap<String, Object>();
      iss.put("severity", issue.getSeverity().name());
      iss.put("category", issue.getCategory());
      iss.put("message", issue.getMessage());
      issueList.add(iss);
    }
    report.put("issues", issueList);

    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create()
        .toJson(report);
  }

  /**
   * Round a double value to the specified number of decimal places.
   *
   * @param value the value to round
   * @param decimals number of decimal places
   * @return rounded value
   */
  private double round(double value, int decimals) {
    if (Double.isNaN(value) || Double.isInfinite(value)) {
      return value;
    }
    double factor = Math.pow(10, decimals);
    return Math.round(value * factor) / factor;
  }

  // ============================================================================
  // Inner Classes
  // ============================================================================

  /**
   * Severity levels for feasibility issues.
   */
  public enum IssueSeverity {
    /** Informational note - no impact on feasibility. */
    INFO,
    /** Warning - feasible but should be reviewed. */
    WARNING,
    /** Blocker - design is not feasible as specified. */
    BLOCKER
  }

  /**
   * A single feasibility issue or observation.
   */
  public static class FeasibilityIssue {
    private final IssueSeverity severity;
    private final String category;
    private final String message;

    /**
     * Constructor for FeasibilityIssue.
     *
     * @param severity issue severity
     * @param category category code
     * @param message descriptive message
     */
    public FeasibilityIssue(IssueSeverity severity, String category, String message) {
      this.severity = severity;
      this.category = category;
      this.message = message;
    }

    /**
     * Get severity.
     *
     * @return severity level
     */
    public IssueSeverity getSeverity() {
      return severity;
    }

    /**
     * Get category.
     *
     * @return category code
     */
    public String getCategory() {
      return category;
    }

    /**
     * Get message.
     *
     * @return descriptive message
     */
    public String getMessage() {
      return message;
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
      return "[" + severity + "] " + category + ": " + message;
    }
  }

  /**
   * Data class for a heat exchanger supplier match.
   */
  public static class SupplierMatch {
    private String manufacturer = "";
    private String exchangerType = "";
    private double minDutyKW = 0;
    private double maxDutyKW = Double.MAX_VALUE;
    private double minAreaM2 = 0;
    private double maxAreaM2 = Double.MAX_VALUE;
    private double minPressureBara = 0;
    private double maxPressureBara = Double.MAX_VALUE;
    private double minTemperatureC = -273;
    private double maxTemperatureC = 1000;
    private String temaTypes = "";
    private String materials = "";
    private String applications = "";
    private String website = "";
    private String notes = "";

    /**
     * Get manufacturer.
     *
     * @return manufacturer name
     */
    public String getManufacturer() {
      return manufacturer;
    }

    /**
     * Set manufacturer.
     *
     * @param manufacturer manufacturer name
     */
    public void setManufacturer(String manufacturer) {
      this.manufacturer = manufacturer;
    }

    /**
     * Get exchanger type.
     *
     * @return exchanger type
     */
    public String getExchangerType() {
      return exchangerType;
    }

    /**
     * Set exchanger type.
     *
     * @param type exchanger type
     */
    public void setExchangerType(String type) {
      this.exchangerType = type;
    }

    /**
     * Get min duty.
     *
     * @return min duty in kW
     */
    public double getMinDutyKW() {
      return minDutyKW;
    }

    /**
     * Set min duty.
     *
     * @param kw min duty in kW
     */
    public void setMinDutyKW(double kw) {
      this.minDutyKW = kw;
    }

    /**
     * Get max duty.
     *
     * @return max duty in kW
     */
    public double getMaxDutyKW() {
      return maxDutyKW;
    }

    /**
     * Set max duty.
     *
     * @param kw max duty in kW
     */
    public void setMaxDutyKW(double kw) {
      this.maxDutyKW = kw;
    }

    /**
     * Get min area.
     *
     * @return min area in m2
     */
    public double getMinAreaM2() {
      return minAreaM2;
    }

    /**
     * Set min area.
     *
     * @param m2 min area
     */
    public void setMinAreaM2(double m2) {
      this.minAreaM2 = m2;
    }

    /**
     * Get max area.
     *
     * @return max area in m2
     */
    public double getMaxAreaM2() {
      return maxAreaM2;
    }

    /**
     * Set max area.
     *
     * @param m2 max area
     */
    public void setMaxAreaM2(double m2) {
      this.maxAreaM2 = m2;
    }

    /**
     * Get min pressure.
     *
     * @return min pressure in bara
     */
    public double getMinPressureBara() {
      return minPressureBara;
    }

    /**
     * Set min pressure.
     *
     * @param bara min pressure
     */
    public void setMinPressureBara(double bara) {
      this.minPressureBara = bara;
    }

    /**
     * Get max pressure.
     *
     * @return max pressure in bara
     */
    public double getMaxPressureBara() {
      return maxPressureBara;
    }

    /**
     * Set max pressure.
     *
     * @param bara max pressure
     */
    public void setMaxPressureBara(double bara) {
      this.maxPressureBara = bara;
    }

    /**
     * Get min temperature.
     *
     * @return min temperature in C
     */
    public double getMinTemperatureC() {
      return minTemperatureC;
    }

    /**
     * Set min temperature.
     *
     * @param c min temperature
     */
    public void setMinTemperatureC(double c) {
      this.minTemperatureC = c;
    }

    /**
     * Get max temperature.
     *
     * @return max temperature in C
     */
    public double getMaxTemperatureC() {
      return maxTemperatureC;
    }

    /**
     * Set max temperature.
     *
     * @param c max temperature
     */
    public void setMaxTemperatureC(double c) {
      this.maxTemperatureC = c;
    }

    /**
     * Get TEMA types.
     *
     * @return TEMA types string
     */
    public String getTemaTypes() {
      return temaTypes;
    }

    /**
     * Set TEMA types.
     *
     * @param types TEMA types
     */
    public void setTemaTypes(String types) {
      this.temaTypes = types;
    }

    /**
     * Get materials.
     *
     * @return materials string
     */
    public String getMaterials() {
      return materials;
    }

    /**
     * Set materials.
     *
     * @param materials materials string
     */
    public void setMaterials(String materials) {
      this.materials = materials;
    }

    /**
     * Get applications.
     *
     * @return applications string
     */
    public String getApplications() {
      return applications;
    }

    /**
     * Set applications.
     *
     * @param applications applications string
     */
    public void setApplications(String applications) {
      this.applications = applications;
    }

    /**
     * Get website.
     *
     * @return website URL
     */
    public String getWebsite() {
      return website;
    }

    /**
     * Set website.
     *
     * @param website website URL
     */
    public void setWebsite(String website) {
      this.website = website;
    }

    /**
     * Get notes.
     *
     * @return notes string
     */
    public String getNotes() {
      return notes;
    }

    /**
     * Set notes.
     *
     * @param notes notes string
     */
    public void setNotes(String notes) {
      this.notes = notes;
    }
  }
}
