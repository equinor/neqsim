package neqsim.process.mechanicaldesign.compressor;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.GsonBuilder;
import neqsim.process.costestimation.compressor.CompressorCostEstimate;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.compressor.CompressorChartGenerator;
import neqsim.process.equipment.compressor.CompressorChartInterface;

/**
 * Comprehensive feasibility report for centrifugal gas compressor design.
 *
 * <p>
 * This class combines mechanical design (API 617), cost estimation, supplier matching, and
 * operating envelope validation into a single report that answers the question: "Is this compressor
 * realistic to build and operate?"
 * </p>
 *
 * <p>
 * The report covers:
 * </p>
 * <ul>
 * <li>Mechanical feasibility: staging, impeller sizing, tip speed, shaft dynamics</li>
 * <li>Thermodynamic feasibility: discharge temperature, pressure ratio per stage</li>
 * <li>Cost estimation: purchased equipment, driver, installation, annual OPEX</li>
 * <li>Supplier matching: which OEMs can build this machine</li>
 * <li>Compressor curve generation: realistic performance maps</li>
 * <li>Overall feasibility verdict with warnings and recommendations</li>
 * </ul>
 *
 * <p>
 * Usage:
 * </p>
 *
 * <pre>
 * Compressor comp = new Compressor("Export Compressor", feed);
 * comp.setOutletPressure(120.0);
 * comp.setPolytropicEfficiency(0.80);
 * comp.setSpeed(9000);
 * comp.run();
 *
 * CompressorDesignFeasibilityReport report = new CompressorDesignFeasibilityReport(comp);
 * report.generateReport();
 *
 * boolean feasible = report.isFeasible();
 * String json = report.toJson();
 * List&lt;SupplierMatch&gt; suppliers = report.getMatchingSuppliers();
 * </pre>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class CompressorDesignFeasibilityReport {

  /** The compressor being assessed. */
  private final Compressor compressor;

  /** Mechanical design calculations. */
  private CompressorMechanicalDesign mechanicalDesign;

  /** Cost estimation. */
  private CompressorCostEstimate costEstimate;

  /** Overall feasibility verdict. */
  private boolean feasible = false;

  /** Whether the report has been generated. */
  private boolean reportGenerated = false;

  /** List of feasibility issues (warnings and blockers). */
  private final List<FeasibilityIssue> issues = new ArrayList<FeasibilityIssue>();

  /** List of matching suppliers. */
  private final List<SupplierMatch> matchingSuppliers = new ArrayList<SupplierMatch>();

  /** Generated compressor chart (if requested). */
  private CompressorChartInterface generatedChart = null;

  /** Driver type for cost estimation. */
  private String driverType = "electric-motor";

  /** Compressor type. */
  private String compressorType = "centrifugal";

  /** Annual operating hours for OPEX calculation. */
  private double annualOperatingHours = 8000.0;

  /** Electricity rate in USD/kWh. */
  private double electricityRate = 0.10;

  /** Fuel rate in USD/GJ (for gas turbine/engine drivers). */
  private double fuelRate = 5.0;

  /** Whether to generate compressor curves as part of the report. */
  private boolean generateCurves = true;

  /** Curve template name. */
  private String curveTemplate = "CENTRIFUGAL_STANDARD";

  /** Number of speed curves to generate. */
  private int numberOfSpeedCurves = 5;

  // ============================================================================
  // Operating point data (captured during report generation)
  // ============================================================================

  private double inletPressureBara = 0.0;
  private double outletPressureBara = 0.0;
  private double inletTemperatureC = 0.0;
  private double outletTemperatureC = 0.0;
  private double massFlowKghr = 0.0;
  private double volumeFlowM3hr = 0.0;
  private double shaftPowerKW = 0.0;
  private double polytropicHead = 0.0;
  private double polytropicEfficiency = 0.0;
  private double pressureRatio = 0.0;
  private double gasMolecularWeight = 0.0;

  /**
   * Constructor for CompressorDesignFeasibilityReport.
   *
   * @param compressor the compressor to assess (must have been run)
   */
  public CompressorDesignFeasibilityReport(Compressor compressor) {
    if (compressor == null) {
      throw new IllegalArgumentException("Compressor cannot be null");
    }
    this.compressor = compressor;
  }

  /**
   * Set the driver type for cost estimation.
   *
   * @param driverType driver type ("electric-motor", "gas-turbine", "steam-turbine", "gas-engine")
   * @return this report for method chaining
   */
  public CompressorDesignFeasibilityReport setDriverType(String driverType) {
    this.driverType = driverType;
    return this;
  }

  /**
   * Set the compressor type.
   *
   * @param compressorType type ("centrifugal", "reciprocating", "screw", "axial")
   * @return this report for method chaining
   */
  public CompressorDesignFeasibilityReport setCompressorType(String compressorType) {
    this.compressorType = compressorType;
    return this;
  }

  /**
   * Set annual operating hours for OPEX calculation.
   *
   * @param hours operating hours per year (typically 8000-8760)
   * @return this report for method chaining
   */
  public CompressorDesignFeasibilityReport setAnnualOperatingHours(double hours) {
    this.annualOperatingHours = hours;
    return this;
  }

  /**
   * Set electricity rate for operating cost.
   *
   * @param rate electricity rate in USD/kWh
   * @return this report for method chaining
   */
  public CompressorDesignFeasibilityReport setElectricityRate(double rate) {
    this.electricityRate = rate;
    return this;
  }

  /**
   * Set fuel rate for gas turbine/engine drivers.
   *
   * @param rate fuel rate in USD/GJ
   * @return this report for method chaining
   */
  public CompressorDesignFeasibilityReport setFuelRate(double rate) {
    this.fuelRate = rate;
    return this;
  }

  /**
   * Set whether to generate compressor performance curves.
   *
   * @param generate true to include curve generation
   * @return this report for method chaining
   */
  public CompressorDesignFeasibilityReport setGenerateCurves(boolean generate) {
    this.generateCurves = generate;
    return this;
  }

  /**
   * Set curve template for chart generation.
   *
   * @param template template name (e.g. "CENTRIFUGAL_STANDARD", "PIPELINE", "EXPORT")
   * @return this report for method chaining
   */
  public CompressorDesignFeasibilityReport setCurveTemplate(String template) {
    this.curveTemplate = template;
    return this;
  }

  /**
   * Set number of speed curves to generate.
   *
   * @param count number of speed curves (typically 5-9)
   * @return this report for method chaining
   */
  public CompressorDesignFeasibilityReport setNumberOfSpeedCurves(int count) {
    this.numberOfSpeedCurves = count;
    return this;
  }

  /**
   * Generate the complete feasibility report.
   *
   * <p>
   * This method runs mechanical design, cost estimation, supplier matching, and feasibility
   * validation. The compressor must have been run (i.e. outlet conditions calculated) before
   * calling this method.
   * </p>
   */
  public void generateReport() {
    issues.clear();
    matchingSuppliers.clear();
    feasible = true;

    // Capture operating point
    captureOperatingPoint();

    // Run mechanical design
    runMechanicalDesign();

    // Run cost estimation
    runCostEstimation();

    // Match suppliers
    matchSuppliers();

    // Generate compressor curves if requested
    if (generateCurves) {
      generateCompressorCurves();
    }

    // Run feasibility checks
    runFeasibilityChecks();

    // Determine overall verdict
    for (FeasibilityIssue issue : issues) {
      if (issue.getSeverity() == IssueSeverity.BLOCKER) {
        feasible = false;
        break;
      }
    }

    reportGenerated = true;
  }

  /**
   * Capture the current operating point from the compressor.
   */
  private void captureOperatingPoint() {
    if (compressor.getThermoSystem() == null) {
      issues.add(new FeasibilityIssue(IssueSeverity.BLOCKER, "INIT",
          "Compressor has not been run - call run() before generating report"));
      feasible = false;
      return;
    }

    inletPressureBara = compressor.getInletStream().getPressure("bara");
    outletPressureBara = compressor.getOutletStream().getPressure("bara");
    inletTemperatureC = compressor.getInletStream().getTemperature("C");
    outletTemperatureC = compressor.getOutletStream().getTemperature("C");
    massFlowKghr = compressor.getInletStream().getFlowRate("kg/hr");
    volumeFlowM3hr = compressor.getInletStream().getFlowRate("m3/hr");
    shaftPowerKW = compressor.getPower("kW");
    polytropicHead = compressor.getPolytropicFluidHead();
    polytropicEfficiency = compressor.getPolytropicEfficiency();
    pressureRatio = outletPressureBara / inletPressureBara;
    gasMolecularWeight = compressor.getInletStream().getFluid().getMolarMass("kg/mol") * 1000.0;
  }

  /**
   * Run mechanical design calculations.
   */
  private void runMechanicalDesign() {
    compressor.initMechanicalDesign();
    mechanicalDesign = compressor.getMechanicalDesign();
    mechanicalDesign.calcDesign();
  }

  /**
   * Run cost estimation.
   */
  private void runCostEstimation() {
    costEstimate = (CompressorCostEstimate) mechanicalDesign.getCostEstimate();
    costEstimate.setCompressorType(compressorType);
    costEstimate.setDriverType(driverType);
    costEstimate.setNumberOfStages(mechanicalDesign.getNumberOfStages());
    costEstimate.setIncludeIntercoolers(mechanicalDesign.getNumberOfStages() > 1);
    costEstimate.calculateCostEstimate();
  }

  /**
   * Match compressor suppliers from the database.
   */
  private void matchSuppliers() {
    List<SupplierMatch> allSuppliers = loadSupplierDatabase();
    for (SupplierMatch supplier : allSuppliers) {
      if (isSupplierMatch(supplier)) {
        matchingSuppliers.add(supplier);
      }
    }

    if (matchingSuppliers.isEmpty()) {
      issues.add(new FeasibilityIssue(IssueSeverity.WARNING, "SUPPLIER",
          "No standard supplier found for this compressor specification. "
              + "Custom or engineered-to-order solution may be required."));
    }
  }

  /**
   * Check whether a supplier can build a compressor for the current requirements.
   *
   * @param supplier the supplier data
   * @return true if supplier can match the requirements
   */
  private boolean isSupplierMatch(SupplierMatch supplier) {
    // Must match compressor type
    if (!compressorType.equalsIgnoreCase(supplier.getCompressorType())) {
      return false;
    }

    // Power range
    if (shaftPowerKW < supplier.getMinPowerKW() || shaftPowerKW > supplier.getMaxPowerKW()) {
      return false;
    }

    // Volume flow range
    if (volumeFlowM3hr < supplier.getMinFlowM3hr() || volumeFlowM3hr > supplier.getMaxFlowM3hr()) {
      return false;
    }

    // Discharge pressure capability
    if (outletPressureBara > supplier.getMaxDischargePressureBara()) {
      return false;
    }

    // Number of stages
    if (mechanicalDesign != null
        && mechanicalDesign.getNumberOfStages() > supplier.getMaxStages()) {
      return false;
    }

    // Pressure ratio per stage
    double prPerStage = Math.pow(pressureRatio, 1.0 / mechanicalDesign.getNumberOfStages());
    if (prPerStage < supplier.getMinPressureRatioPerStage()
        || prPerStage > supplier.getMaxPressureRatioPerStage()) {
      return false;
    }

    return true;
  }

  /**
   * Generate realistic compressor performance curves.
   */
  private void generateCompressorCurves() {
    try {
      CompressorChartGenerator chartGen = new CompressorChartGenerator(compressor);
      chartGen.setChartType("interpolate and extrapolate");
      generatedChart = chartGen.generateFromTemplate(curveTemplate, numberOfSpeedCurves);
    } catch (Exception ex) {
      issues.add(new FeasibilityIssue(IssueSeverity.WARNING, "CURVES",
          "Could not generate compressor curves: " + ex.getMessage()));
    }
  }

  /**
   * Run all feasibility checks.
   */
  private void runFeasibilityChecks() {
    checkDischargeTemperature();
    checkPressureRatioPerStage();
    checkTipSpeed();
    checkPowerRange();
    checkFlowRange();
    checkRotorDynamics();
    checkEfficiency();
    checkCostReasonableness();
  }

  /**
   * Check discharge temperature limit.
   */
  private void checkDischargeTemperature() {
    double maxTempC = mechanicalDesign.getMaxDischargeTemperatureC();
    if (outletTemperatureC > maxTempC) {
      issues.add(new FeasibilityIssue(IssueSeverity.BLOCKER, "TEMPERATURE",
          "Discharge temperature " + String.format("%.1f", outletTemperatureC)
              + " C exceeds maximum " + String.format("%.1f", maxTempC)
              + " C. Consider intercooling or more stages."));
    } else if (outletTemperatureC > maxTempC * 0.9) {
      issues.add(new FeasibilityIssue(IssueSeverity.WARNING, "TEMPERATURE",
          "Discharge temperature " + String.format("%.1f", outletTemperatureC)
              + " C is within 10% of maximum " + String.format("%.1f", maxTempC) + " C."));
    }
  }

  /**
   * Check pressure ratio per stage.
   */
  private void checkPressureRatioPerStage() {
    int stages = mechanicalDesign.getNumberOfStages();
    double prPerStage = Math.pow(pressureRatio, 1.0 / stages);
    double maxPR = mechanicalDesign.getMaxPressureRatioPerStage();
    if (prPerStage > maxPR) {
      issues.add(new FeasibilityIssue(IssueSeverity.BLOCKER, "PRESSURE_RATIO",
          "Pressure ratio per stage " + String.format("%.2f", prPerStage) + " exceeds maximum "
              + String.format("%.2f", maxPR)
              + ". Need more stages or consider reciprocating compressor."));
    }
  }

  /**
   * Check impeller tip speed.
   */
  private void checkTipSpeed() {
    double tipSpeed = mechanicalDesign.getTipSpeed();
    double maxTipSpeed = 350.0; // m/s for steel impellers
    if (tipSpeed > maxTipSpeed) {
      issues.add(new FeasibilityIssue(IssueSeverity.BLOCKER, "TIP_SPEED",
          "Impeller tip speed " + String.format("%.0f", tipSpeed) + " m/s exceeds material limit "
              + String.format("%.0f", maxTipSpeed) + " m/s. Reduce speed or increase stages."));
    } else if (tipSpeed > maxTipSpeed * 0.9) {
      issues.add(new FeasibilityIssue(IssueSeverity.WARNING, "TIP_SPEED",
          "Impeller tip speed " + String.format("%.0f", tipSpeed)
              + " m/s is near material limit. Consider titanium impellers for margin."));
    }
  }

  /**
   * Check power is in a buildable range.
   */
  private void checkPowerRange() {
    if (shaftPowerKW < 50) {
      issues.add(new FeasibilityIssue(IssueSeverity.WARNING, "POWER",
          "Power " + String.format("%.0f", shaftPowerKW)
              + " kW is very low for centrifugal. Consider screw or reciprocating compressor."));
    } else if (shaftPowerKW > 100000) {
      issues.add(new FeasibilityIssue(IssueSeverity.WARNING, "POWER",
          "Power " + String.format("%.0f", shaftPowerKW)
              + " kW is very high. Limited number of suppliers. Consider parallel trains."));
    }
  }

  /**
   * Check volume flow is in a buildable range.
   */
  private void checkFlowRange() {
    if (volumeFlowM3hr < 100) {
      issues.add(new FeasibilityIssue(IssueSeverity.WARNING, "FLOW",
          "Inlet volume flow " + String.format("%.0f", volumeFlowM3hr)
              + " m3/hr is very low for centrifugal. Consider reciprocating compressor."));
    } else if (volumeFlowM3hr > 500000) {
      issues.add(new FeasibilityIssue(IssueSeverity.WARNING, "FLOW",
          "Inlet volume flow " + String.format("%.0f", volumeFlowM3hr)
              + " m3/hr is very high. Consider parallel trains or axial compressor."));
    }
  }

  /**
   * Check rotor dynamics (critical speed separation).
   */
  private void checkRotorDynamics() {
    double firstCritical = mechanicalDesign.getFirstCriticalSpeed();
    double operatingSpeed = compressor.getSpeed();
    if (firstCritical > 0 && operatingSpeed > 0) {
      double margin = Math.abs(firstCritical - operatingSpeed) / operatingSpeed * 100.0;
      if (margin < 15.0) {
        issues.add(new FeasibilityIssue(IssueSeverity.WARNING, "ROTOR_DYNAMICS",
            "Operating speed " + String.format("%.0f", operatingSpeed)
                + " RPM is within 15% of first critical speed "
                + String.format("%.0f", firstCritical)
                + " RPM. API 617 requires minimum 15% separation margin."));
      }
    }
  }

  /**
   * Check polytropic efficiency is realistic.
   */
  private void checkEfficiency() {
    if (polytropicEfficiency > 0.90) {
      issues.add(new FeasibilityIssue(IssueSeverity.WARNING, "EFFICIENCY",
          "Polytropic efficiency " + String.format("%.1f", polytropicEfficiency * 100)
              + "% is unrealistically high. Typical range is 75-85% for centrifugal."));
    } else if (polytropicEfficiency < 0.65 && polytropicEfficiency > 0) {
      issues.add(new FeasibilityIssue(IssueSeverity.WARNING, "EFFICIENCY",
          "Polytropic efficiency " + String.format("%.1f", polytropicEfficiency * 100)
              + "% is low. Check impeller design or consider different compressor type."));
    }
  }

  /**
   * Check cost estimate is in a reasonable range.
   */
  private void checkCostReasonableness() {
    double totalCost = costEstimate.getTotalCost();
    if (totalCost > 0 && shaftPowerKW > 0) {
      double costPerKW = totalCost / shaftPowerKW;
      if (costPerKW > 5000) {
        issues.add(new FeasibilityIssue(IssueSeverity.INFO, "COST",
            "Specific cost " + String.format("%.0f", costPerKW)
                + " USD/kW is high. Consider alternative compressor types or parallel trains."));
      } else if (costPerKW < 100) {
        issues.add(new FeasibilityIssue(IssueSeverity.INFO, "COST",
            "Specific cost " + String.format("%.0f", costPerKW)
                + " USD/kW is very low. Estimate may be optimistic."));
      }
    }
  }

  // ============================================================================
  // Supplier Database Loading
  // ============================================================================

  /**
   * Load supplier data from the CompressorSuppliers.csv resource file.
   *
   * @return list of all suppliers
   */
  private List<SupplierMatch> loadSupplierDatabase() {
    List<SupplierMatch> suppliers = new ArrayList<SupplierMatch>();
    try {
      InputStream is = getClass().getResourceAsStream("/designdata/CompressorSuppliers.csv");
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
   * Parse a single CSV line into a SupplierMatch.
   *
   * @param line CSV line
   * @return supplier data or null
   */
  private SupplierMatch parseSupplierLine(String line) {
    try {
      // Handle quoted CSV fields
      List<String> fields = parseCsvLine(line);
      if (fields.size() < 17) {
        return null;
      }

      SupplierMatch supplier = new SupplierMatch();
      supplier.setManufacturer(fields.get(1));
      supplier.setCompressorType(fields.get(2));
      supplier.setMinPowerKW(parseDouble(fields.get(3), 0));
      supplier.setMaxPowerKW(parseDouble(fields.get(4), Double.MAX_VALUE));
      supplier.setMinPressureRatioPerStage(parseDouble(fields.get(5), 0));
      supplier.setMaxPressureRatioPerStage(parseDouble(fields.get(6), 10));
      supplier.setMinFlowM3hr(parseDouble(fields.get(7), 0));
      supplier.setMaxFlowM3hr(parseDouble(fields.get(8), Double.MAX_VALUE));
      supplier.setMaxStages(parseInt(fields.get(9), 20));
      supplier.setMaxDischargePressureBara(parseDouble(fields.get(10), Double.MAX_VALUE));
      supplier.setMaxSpeedRPM(parseDouble(fields.get(11), Double.MAX_VALUE));
      supplier.setMinImpellerDiameterMM(parseDouble(fields.get(12), 0));
      supplier.setMaxImpellerDiameterMM(parseDouble(fields.get(13), Double.MAX_VALUE));
      supplier.setTypicalEfficiencyPct(parseDouble(fields.get(14), 80));
      supplier.setApplications(fields.get(15));
      supplier.setWebsite(fields.get(16));
      if (fields.size() > 17) {
        supplier.setNotes(fields.get(17));
      }

      return supplier;
    } catch (Exception ex) {
      return null;
    }
  }

  /**
   * Parse a quoted CSV line respecting double quotes.
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

  /**
   * Safely parse an int value.
   *
   * @param value string value
   * @param defaultValue default if parsing fails
   * @return parsed value
   */
  private int parseInt(String value, int defaultValue) {
    try {
      return Integer.parseInt(value.trim());
    } catch (NumberFormatException e) {
      return defaultValue;
    }
  }

  // ============================================================================
  // Results Access
  // ============================================================================

  /**
   * Returns whether the compressor design is feasible (no blockers).
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
   * Get the list of feasibility issues found.
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
   * Get the mechanical design calculations.
   *
   * @return mechanical design or null if report not yet generated
   */
  public CompressorMechanicalDesign getMechanicalDesign() {
    return mechanicalDesign;
  }

  /**
   * Get the cost estimate.
   *
   * @return cost estimate or null if report not yet generated
   */
  public CompressorCostEstimate getCostEstimate() {
    return costEstimate;
  }

  /**
   * Get the generated compressor chart.
   *
   * @return compressor chart or null if not generated
   */
  public CompressorChartInterface getGeneratedChart() {
    return generatedChart;
  }

  /**
   * Get overall feasibility verdict as a string.
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

    // Operating point
    Map<String, Object> operatingPoint = new LinkedHashMap<String, Object>();
    operatingPoint.put("inletPressure_bara", inletPressureBara);
    operatingPoint.put("outletPressure_bara", outletPressureBara);
    operatingPoint.put("pressureRatio", round(pressureRatio, 2));
    operatingPoint.put("inletTemperature_C", round(inletTemperatureC, 1));
    operatingPoint.put("outletTemperature_C", round(outletTemperatureC, 1));
    operatingPoint.put("massFlow_kghr", round(massFlowKghr, 0));
    operatingPoint.put("volumeFlow_m3hr", round(volumeFlowM3hr, 1));
    operatingPoint.put("shaftPower_kW", round(shaftPowerKW, 1));
    operatingPoint.put("polytropicHead_kJkg", round(polytropicHead, 2));
    operatingPoint.put("polytropicEfficiency", round(polytropicEfficiency, 4));
    operatingPoint.put("gasMolecularWeight_kgkmol", round(gasMolecularWeight, 2));
    operatingPoint.put("speed_rpm", compressor.getSpeed());
    report.put("operatingPoint", operatingPoint);

    // Mechanical design
    if (mechanicalDesign != null) {
      Map<String, Object> mechDesign = new LinkedHashMap<String, Object>();
      mechDesign.put("numberOfStages", mechanicalDesign.getNumberOfStages());
      mechDesign.put("headPerStage_kJkg", round(mechanicalDesign.getHeadPerStage(), 2));
      mechDesign.put("impellerDiameter_mm", round(mechanicalDesign.getImpellerDiameter(), 0));
      mechDesign.put("shaftDiameter_mm", round(mechanicalDesign.getShaftDiameter(), 0));
      mechDesign.put("tipSpeed_ms", round(mechanicalDesign.getTipSpeed(), 1));
      mechDesign.put("bearingSpan_mm", round(mechanicalDesign.getBearingSpan(), 0));
      mechDesign.put("casingType", mechanicalDesign.getCasingType().name());
      mechDesign.put("driverPower_kW", round(mechanicalDesign.getDriverPower(), 1));
      mechDesign.put("driverMargin", round(mechanicalDesign.getDriverMargin(), 2));
      mechDesign.put("maxContinuousSpeed_rpm", round(mechanicalDesign.getMaxContinuousSpeed(), 0));
      mechDesign.put("tripSpeed_rpm", round(mechanicalDesign.getTripSpeed(), 0));
      mechDesign.put("firstCriticalSpeed_rpm", round(mechanicalDesign.getFirstCriticalSpeed(), 0));
      mechDesign.put("designPressure_bara", round(mechanicalDesign.getDesignPressure(), 1));
      mechDesign.put("designTemperature_C", round(mechanicalDesign.getDesignTemperature(), 1));

      // Weights
      Map<String, Object> weights = new LinkedHashMap<String, Object>();
      weights.put("casingWeight_kg", round(mechanicalDesign.getCasingWeight(), 0));
      weights.put("rotorWeight_kg", round(mechanicalDesign.getRotorWeight(), 0));
      weights.put("bundleWeight_kg", round(mechanicalDesign.getBundleWeight(), 0));
      weights.put("totalSkidWeight_kg", round(mechanicalDesign.getWeightTotal(), 0));
      mechDesign.put("weights", weights);

      // Module dimensions
      Map<String, Object> module = new LinkedHashMap<String, Object>();
      module.put("length_m", round(mechanicalDesign.getModuleLength(), 1));
      module.put("width_m", round(mechanicalDesign.getModuleWidth(), 1));
      module.put("height_m", round(mechanicalDesign.getModuleHeight(), 1));
      mechDesign.put("moduleDimensions", module);

      report.put("mechanicalDesign", mechDesign);
    }

    // Cost estimation
    if (costEstimate != null) {
      Map<String, Object> cost = new LinkedHashMap<String, Object>();
      cost.put("compressorType", compressorType);
      cost.put("driverType", driverType);
      cost.put("purchasedEquipmentCost_USD", round(costEstimate.getPurchasedEquipmentCost(), 0));
      cost.put("bareModuleCost_USD", round(costEstimate.getBareModuleCost(), 0));
      cost.put("totalModuleCost_USD", round(costEstimate.getTotalCost(), 0));
      if (shaftPowerKW > 0) {
        cost.put("specificCost_USDperKW", round(costEstimate.getTotalCost() / shaftPowerKW, 0));
      }

      // OPEX
      Map<String, Object> opex = new LinkedHashMap<String, Object>();
      double annualEnergy =
          costEstimate.calcAnnualOperatingCost(annualOperatingHours, electricityRate, fuelRate);
      double annualMaintenance = costEstimate.calcAnnualMaintenanceCost();
      opex.put("annualEnergyCost_USD", round(annualEnergy, 0));
      opex.put("annualMaintenanceCost_USD", round(annualMaintenance, 0));
      opex.put("totalAnnualOPEX_USD", round(annualEnergy + annualMaintenance, 0));
      opex.put("operatingHoursPerYear", annualOperatingHours);
      opex.put("electricityRate_USDperKWh", electricityRate);
      cost.put("annualOperatingCost", opex);

      // Lifecycle (10-year simple)
      double lifecycleCost =
          costEstimate.getTotalCost() + (annualEnergy + annualMaintenance) * 10.0;
      cost.put("tenYearLifecycleCost_USD", round(lifecycleCost, 0));

      report.put("costEstimation", cost);
    }

    // Matching suppliers
    if (!matchingSuppliers.isEmpty()) {
      List<Map<String, Object>> supplierList = new ArrayList<Map<String, Object>>();
      for (SupplierMatch supplier : matchingSuppliers) {
        Map<String, Object> sup = new LinkedHashMap<String, Object>();
        sup.put("manufacturer", supplier.getManufacturer());
        sup.put("compressorType", supplier.getCompressorType());
        sup.put("powerRange_kW", supplier.getMinPowerKW() + " - " + supplier.getMaxPowerKW());
        sup.put("flowRange_m3hr", supplier.getMinFlowM3hr() + " - " + supplier.getMaxFlowM3hr());
        sup.put("maxDischargePressure_bara", supplier.getMaxDischargePressureBara());
        sup.put("maxStages", supplier.getMaxStages());
        sup.put("typicalEfficiency_pct", supplier.getTypicalEfficiencyPct());
        sup.put("applications", supplier.getApplications());
        sup.put("website", supplier.getWebsite());
        if (supplier.getNotes() != null && !supplier.getNotes().trim().isEmpty()) {
          sup.put("notes", supplier.getNotes());
        }
        supplierList.add(sup);
      }
      report.put("matchingSuppliers", supplierList);
      report.put("numberOfMatchingSuppliers", matchingSuppliers.size());
    } else {
      report.put("matchingSuppliers", new ArrayList<Object>());
      report.put("numberOfMatchingSuppliers", 0);
    }

    // Compressor curves info
    if (generatedChart != null) {
      Map<String, Object> curves = new LinkedHashMap<String, Object>();
      curves.put("generated", true);
      curves.put("template", curveTemplate);
      curves.put("numberOfSpeedCurves", numberOfSpeedCurves);
      double[] speeds = generatedChart.getSpeeds();
      if (speeds != null && speeds.length > 0) {
        curves.put("minSpeed_rpm", round(speeds[0], 0));
        curves.put("maxSpeed_rpm", round(speeds[speeds.length - 1], 0));
      }
      curves.put("chartCanBeAppliedToCompressor", true);
      report.put("compressorCurves", curves);
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

  /**
   * Apply the generated compressor chart to the compressor.
   *
   * <p>
   * After calling this method, the compressor will use chart-based calculations instead of
   * equation-based. This is useful for simulating realistic compressor behaviour over varying
   * operating conditions (e.g. declining reservoir pressure over years).
   * </p>
   */
  public void applyChartToCompressor() {
    if (generatedChart == null) {
      throw new IllegalStateException(
          "No chart generated. Call generateReport() with generateCurves=true first.");
    }
    compressor.setCompressorChart(generatedChart);
    compressor.getCompressorChart().setUseCompressorChart(true);
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
     * @param category category code (e.g. "TEMPERATURE", "TIP_SPEED")
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
   * Data class for a compressor supplier/manufacturer match.
   */
  public static class SupplierMatch {
    private String manufacturer = "";
    private String compressorType = "";
    private double minPowerKW = 0;
    private double maxPowerKW = Double.MAX_VALUE;
    private double minPressureRatioPerStage = 0;
    private double maxPressureRatioPerStage = 10;
    private double minFlowM3hr = 0;
    private double maxFlowM3hr = Double.MAX_VALUE;
    private int maxStages = 20;
    private double maxDischargePressureBara = Double.MAX_VALUE;
    private double maxSpeedRPM = Double.MAX_VALUE;
    private double minImpellerDiameterMM = 0;
    private double maxImpellerDiameterMM = Double.MAX_VALUE;
    private double typicalEfficiencyPct = 80;
    private String applications = "";
    private String website = "";
    private String notes = "";

    /**
     * Get manufacturer name.
     *
     * @return manufacturer
     */
    public String getManufacturer() {
      return manufacturer;
    }

    /**
     * Set manufacturer name.
     *
     * @param manufacturer manufacturer name
     */
    public void setManufacturer(String manufacturer) {
      this.manufacturer = manufacturer;
    }

    /**
     * Get compressor type.
     *
     * @return compressor type
     */
    public String getCompressorType() {
      return compressorType;
    }

    /**
     * Set compressor type.
     *
     * @param compressorType compressor type
     */
    public void setCompressorType(String compressorType) {
      this.compressorType = compressorType;
    }

    /**
     * Get minimum power capability.
     *
     * @return min power in kW
     */
    public double getMinPowerKW() {
      return minPowerKW;
    }

    /**
     * Set minimum power capability.
     *
     * @param minPowerKW min power in kW
     */
    public void setMinPowerKW(double minPowerKW) {
      this.minPowerKW = minPowerKW;
    }

    /**
     * Get maximum power capability.
     *
     * @return max power in kW
     */
    public double getMaxPowerKW() {
      return maxPowerKW;
    }

    /**
     * Set maximum power capability.
     *
     * @param maxPowerKW max power in kW
     */
    public void setMaxPowerKW(double maxPowerKW) {
      this.maxPowerKW = maxPowerKW;
    }

    /**
     * Get minimum pressure ratio per stage.
     *
     * @return min pressure ratio per stage
     */
    public double getMinPressureRatioPerStage() {
      return minPressureRatioPerStage;
    }

    /**
     * Set minimum pressure ratio per stage.
     *
     * @param ratio min pressure ratio per stage
     */
    public void setMinPressureRatioPerStage(double ratio) {
      this.minPressureRatioPerStage = ratio;
    }

    /**
     * Get maximum pressure ratio per stage.
     *
     * @return max pressure ratio per stage
     */
    public double getMaxPressureRatioPerStage() {
      return maxPressureRatioPerStage;
    }

    /**
     * Set maximum pressure ratio per stage.
     *
     * @param ratio max pressure ratio per stage
     */
    public void setMaxPressureRatioPerStage(double ratio) {
      this.maxPressureRatioPerStage = ratio;
    }

    /**
     * Get minimum flow capability.
     *
     * @return min flow in m3/hr
     */
    public double getMinFlowM3hr() {
      return minFlowM3hr;
    }

    /**
     * Set minimum flow capability.
     *
     * @param flow min flow in m3/hr
     */
    public void setMinFlowM3hr(double flow) {
      this.minFlowM3hr = flow;
    }

    /**
     * Get maximum flow capability.
     *
     * @return max flow in m3/hr
     */
    public double getMaxFlowM3hr() {
      return maxFlowM3hr;
    }

    /**
     * Set maximum flow capability.
     *
     * @param flow max flow in m3/hr
     */
    public void setMaxFlowM3hr(double flow) {
      this.maxFlowM3hr = flow;
    }

    /**
     * Get maximum number of stages.
     *
     * @return max stages
     */
    public int getMaxStages() {
      return maxStages;
    }

    /**
     * Set maximum number of stages.
     *
     * @param maxStages max stages
     */
    public void setMaxStages(int maxStages) {
      this.maxStages = maxStages;
    }

    /**
     * Get maximum discharge pressure.
     *
     * @return max discharge pressure in bara
     */
    public double getMaxDischargePressureBara() {
      return maxDischargePressureBara;
    }

    /**
     * Set maximum discharge pressure.
     *
     * @param pressure max discharge pressure in bara
     */
    public void setMaxDischargePressureBara(double pressure) {
      this.maxDischargePressureBara = pressure;
    }

    /**
     * Get maximum speed.
     *
     * @return max speed in RPM
     */
    public double getMaxSpeedRPM() {
      return maxSpeedRPM;
    }

    /**
     * Set maximum speed.
     *
     * @param rpm max speed
     */
    public void setMaxSpeedRPM(double rpm) {
      this.maxSpeedRPM = rpm;
    }

    /**
     * Get minimum impeller diameter.
     *
     * @return min impeller diameter in mm
     */
    public double getMinImpellerDiameterMM() {
      return minImpellerDiameterMM;
    }

    /**
     * Set minimum impeller diameter.
     *
     * @param diameter min impeller diameter in mm
     */
    public void setMinImpellerDiameterMM(double diameter) {
      this.minImpellerDiameterMM = diameter;
    }

    /**
     * Get maximum impeller diameter.
     *
     * @return max impeller diameter in mm
     */
    public double getMaxImpellerDiameterMM() {
      return maxImpellerDiameterMM;
    }

    /**
     * Set maximum impeller diameter.
     *
     * @param diameter max impeller diameter in mm
     */
    public void setMaxImpellerDiameterMM(double diameter) {
      this.maxImpellerDiameterMM = diameter;
    }

    /**
     * Get typical efficiency.
     *
     * @return typical polytropic efficiency in percent
     */
    public double getTypicalEfficiencyPct() {
      return typicalEfficiencyPct;
    }

    /**
     * Set typical efficiency.
     *
     * @param efficiency typical efficiency in percent
     */
    public void setTypicalEfficiencyPct(double efficiency) {
      this.typicalEfficiencyPct = efficiency;
    }

    /**
     * Get applications string (semicolon-separated).
     *
     * @return applications
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
     * Get manufacturer website.
     *
     * @return website
     */
    public String getWebsite() {
      return website;
    }

    /**
     * Set manufacturer website.
     *
     * @param website website URL
     */
    public void setWebsite(String website) {
      this.website = website;
    }

    /**
     * Get notes.
     *
     * @return notes
     */
    public String getNotes() {
      return notes;
    }

    /**
     * Set notes.
     *
     * @param notes notes
     */
    public void setNotes(String notes) {
      this.notes = notes;
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
      return manufacturer + " (" + compressorType + ", " + minPowerKW + "-" + maxPowerKW + " kW)";
    }
  }
}
