package neqsim.process.safety.risk;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.google.gson.GsonBuilder;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.failure.ReliabilityDataSource;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.util.optimizer.ProductionImpactAnalyzer;
import neqsim.process.util.optimizer.ProductionImpactResult;

/**
 * Risk Matrix for equipment failure analysis.
 *
 * <p>
 * Combines probability (from reliability data) with consequence (from NeqSim simulation) to create
 * a visual risk matrix. Calculates economic cost of different failure scenarios.
 * </p>
 *
 * <h2>Risk Categories</h2>
 * <ul>
 * <li><b>Probability</b>: Very Low (1) to Very High (5) based on failure frequency</li>
 * <li><b>Consequence</b>: Negligible (1) to Catastrophic (5) based on production loss</li>
 * <li><b>Risk Level</b>: Low (green), Medium (yellow), High (orange), Critical (red)</li>
 * </ul>
 *
 * <h2>Example Usage</h2>
 * 
 * <pre>
 * {@code
 * RiskMatrix matrix = new RiskMatrix(processSystem);
 * matrix.setProductPrice(500.0, "USD/tonne");
 * matrix.setDowntimeCostPerHour(10000.0);
 * 
 * // Build risk matrix for all equipment
 * matrix.buildRiskMatrix();
 * 
 * // Get risk assessment for specific equipment
 * RiskAssessment risk = matrix.getRiskAssessment("HP Compressor");
 * System.out.println("Risk Level: " + risk.getRiskLevel());
 * System.out.println("Annual Cost: $" + risk.getAnnualRiskCost());
 * 
 * // Get matrix data for visualization
 * String json = matrix.toJson();
 * }
 * </pre>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class RiskMatrix implements Serializable {

  private static final long serialVersionUID = 1000L;

  /** Logger. */
  private static final Logger logger = LogManager.getLogger(RiskMatrix.class);

  /** Process system for simulation. */
  private ProcessSystem processSystem;

  /** Production impact analyzer. */
  private transient ProductionImpactAnalyzer impactAnalyzer;

  /** Reliability data source. */
  private transient ReliabilityDataSource reliabilitySource;

  /** Risk assessments for each equipment. */
  private Map<String, RiskAssessment> riskAssessments;

  /** Feed stream name. */
  private String feedStreamName;

  /** Product stream name. */
  private String productStreamName;

  /** Product price (per kg). */
  private double productPricePerKg = 0.5; // Default ~$500/tonne

  /** Downtime cost per hour (fixed costs, personnel, etc.). */
  private double downtimeCostPerHour = 5000.0;

  /** Hours of operation per year. */
  private double operatingHoursPerYear = 8000.0;

  /**
   * Probability categories (1-5).
   */
  public enum ProbabilityCategory {
    /** Very Low: < 0.1 failures/year. */
    VERY_LOW(1, "Very Low", 0.0, 0.1),
    /** Low: 0.1 - 0.5 failures/year. */
    LOW(2, "Low", 0.1, 0.5),
    /** Medium: 0.5 - 1.0 failures/year. */
    MEDIUM(3, "Medium", 0.5, 1.0),
    /** High: 1.0 - 2.0 failures/year. */
    HIGH(4, "High", 1.0, 2.0),
    /** Very High: > 2.0 failures/year. */
    VERY_HIGH(5, "Very High", 2.0, Double.MAX_VALUE);

    private final int level;
    private final String name;
    private final double minFreq;
    private final double maxFreq;

    ProbabilityCategory(int level, String name, double minFreq, double maxFreq) {
      this.level = level;
      this.name = name;
      this.minFreq = minFreq;
      this.maxFreq = maxFreq;
    }

    public int getLevel() {
      return level;
    }

    public String getName() {
      return name;
    }

    /**
     * Gets category from failure frequency.
     *
     * @param failuresPerYear failures per year
     * @return probability category
     */
    public static ProbabilityCategory fromFrequency(double failuresPerYear) {
      for (ProbabilityCategory cat : values()) {
        if (failuresPerYear >= cat.minFreq && failuresPerYear < cat.maxFreq) {
          return cat;
        }
      }
      return VERY_HIGH;
    }
  }

  /**
   * Consequence categories (1-5).
   */
  public enum ConsequenceCategory {
    /** Negligible: < 5% production loss. */
    NEGLIGIBLE(1, "Negligible", 0.0, 5.0),
    /** Minor: 5-20% production loss. */
    MINOR(2, "Minor", 5.0, 20.0),
    /** Moderate: 20-50% production loss. */
    MODERATE(3, "Moderate", 20.0, 50.0),
    /** Major: 50-80% production loss. */
    MAJOR(4, "Major", 50.0, 80.0),
    /** Catastrophic: > 80% production loss (plant stop). */
    CATASTROPHIC(5, "Catastrophic", 80.0, 100.1);

    private final int level;
    private final String name;
    private final double minLoss;
    private final double maxLoss;

    ConsequenceCategory(int level, String name, double minLoss, double maxLoss) {
      this.level = level;
      this.name = name;
      this.minLoss = minLoss;
      this.maxLoss = maxLoss;
    }

    public int getLevel() {
      return level;
    }

    public String getName() {
      return name;
    }

    /**
     * Gets category from production loss percentage.
     *
     * @param lossPercent production loss percentage (0-100)
     * @return consequence category
     */
    public static ConsequenceCategory fromProductionLoss(double lossPercent) {
      for (ConsequenceCategory cat : values()) {
        if (lossPercent >= cat.minLoss && lossPercent < cat.maxLoss) {
          return cat;
        }
      }
      return CATASTROPHIC;
    }
  }

  /**
   * Risk level categories.
   */
  public enum RiskLevel {
    /** Low risk - acceptable. */
    LOW("Low", "green", 1, 4),
    /** Medium risk - monitor and review. */
    MEDIUM("Medium", "yellow", 5, 9),
    /** High risk - action required. */
    HIGH("High", "orange", 10, 15),
    /** Critical risk - immediate action. */
    CRITICAL("Critical", "red", 16, 25);

    private final String name;
    private final String color;
    private final int minScore;
    private final int maxScore;

    RiskLevel(String name, String color, int minScore, int maxScore) {
      this.name = name;
      this.color = color;
      this.minScore = minScore;
      this.maxScore = maxScore;
    }

    public String getName() {
      return name;
    }

    public String getColor() {
      return color;
    }

    /**
     * Gets risk level from risk score.
     *
     * @param score risk score (probability * consequence)
     * @return risk level
     */
    public static RiskLevel fromScore(int score) {
      for (RiskLevel level : values()) {
        if (score >= level.minScore && score <= level.maxScore) {
          return level;
        }
      }
      return CRITICAL;
    }
  }

  /**
   * Risk assessment for a single equipment.
   */
  public static class RiskAssessment implements Serializable {
    private static final long serialVersionUID = 1L;

    private String equipmentName;
    private String equipmentType;

    // Probability data
    private double failuresPerYear;
    private double mtbf;
    private double mttr;
    private ProbabilityCategory probabilityCategory;

    // Consequence data
    private double productionLossPercent;
    private double productionLossKgHr;
    private ConsequenceCategory consequenceCategory;

    // Risk calculation
    private int riskScore;
    private RiskLevel riskLevel;

    // Cost data
    private double costPerFailure;
    private double annualRiskCost;
    private double expectedDowntimeHoursYear;

    /**
     * Creates a risk assessment.
     *
     * @param equipmentName the equipment name
     */
    public RiskAssessment(String equipmentName) {
      this.equipmentName = equipmentName;
    }

    // Getters
    public String getEquipmentName() {
      return equipmentName;
    }

    public String getEquipmentType() {
      return equipmentType;
    }

    public double getFailuresPerYear() {
      return failuresPerYear;
    }

    public double getMtbf() {
      return mtbf;
    }

    public double getMttr() {
      return mttr;
    }

    public ProbabilityCategory getProbabilityCategory() {
      return probabilityCategory;
    }

    public double getProductionLossPercent() {
      return productionLossPercent;
    }

    public double getProductionLossKgHr() {
      return productionLossKgHr;
    }

    public ConsequenceCategory getConsequenceCategory() {
      return consequenceCategory;
    }

    public int getRiskScore() {
      return riskScore;
    }

    public RiskLevel getRiskLevel() {
      return riskLevel;
    }

    public double getCostPerFailure() {
      return costPerFailure;
    }

    public double getAnnualRiskCost() {
      return annualRiskCost;
    }

    public double getExpectedDowntimeHoursYear() {
      return expectedDowntimeHoursYear;
    }

    // Setters
    void setEquipmentType(String type) {
      this.equipmentType = type;
    }

    void setProbabilityData(double failuresPerYear, double mtbf, double mttr) {
      this.failuresPerYear = failuresPerYear;
      this.mtbf = mtbf;
      this.mttr = mttr;
      this.probabilityCategory = ProbabilityCategory.fromFrequency(failuresPerYear);
      this.expectedDowntimeHoursYear = failuresPerYear * mttr;
    }

    void setConsequenceData(double lossPercent, double lossKgHr) {
      this.productionLossPercent = lossPercent;
      this.productionLossKgHr = lossKgHr;
      this.consequenceCategory = ConsequenceCategory.fromProductionLoss(lossPercent);
    }

    void calculateRisk() {
      this.riskScore = probabilityCategory.getLevel() * consequenceCategory.getLevel();
      this.riskLevel = RiskLevel.fromScore(riskScore);
    }

    void setCostData(double costPerFailure, double annualCost) {
      this.costPerFailure = costPerFailure;
      this.annualRiskCost = annualCost;
    }

    /**
     * Converts to map for JSON serialization.
     *
     * @return map representation
     */
    public Map<String, Object> toMap() {
      Map<String, Object> map = new HashMap<String, Object>();
      map.put("equipmentName", equipmentName);
      map.put("equipmentType", equipmentType);

      // Probability
      Map<String, Object> prob = new HashMap<String, Object>();
      prob.put("failuresPerYear", failuresPerYear);
      prob.put("mtbf_hours", mtbf);
      prob.put("mttr_hours", mttr);
      prob.put("category", probabilityCategory.getName());
      prob.put("level", probabilityCategory.getLevel());
      map.put("probability", prob);

      // Consequence
      Map<String, Object> cons = new HashMap<String, Object>();
      cons.put("productionLossPercent", productionLossPercent);
      cons.put("productionLossKgHr", productionLossKgHr);
      cons.put("category", consequenceCategory.getName());
      cons.put("level", consequenceCategory.getLevel());
      map.put("consequence", cons);

      // Risk
      Map<String, Object> risk = new HashMap<String, Object>();
      risk.put("score", riskScore);
      risk.put("level", riskLevel.getName());
      risk.put("color", riskLevel.getColor());
      map.put("risk", risk);

      // Cost
      Map<String, Object> cost = new HashMap<String, Object>();
      cost.put("costPerFailure", costPerFailure);
      cost.put("annualRiskCost", annualRiskCost);
      cost.put("expectedDowntimeHoursYear", expectedDowntimeHoursYear);
      map.put("cost", cost);

      // Matrix position (for plotting)
      map.put("matrixX", probabilityCategory.getLevel());
      map.put("matrixY", consequenceCategory.getLevel());

      return map;
    }

    @Override
    public String toString() {
      return String.format("%s: P=%s, C=%s, Risk=%s (score=%d), Annual Cost=$%.0f", equipmentName,
          probabilityCategory.getName(), consequenceCategory.getName(), riskLevel.getName(),
          riskScore, annualRiskCost);
    }
  }

  /**
   * Creates a risk matrix for the process system.
   *
   * @param processSystem the process system
   */
  public RiskMatrix(ProcessSystem processSystem) {
    this.processSystem = processSystem;
    this.riskAssessments = new HashMap<String, RiskAssessment>();
    this.reliabilitySource = ReliabilityDataSource.getInstance();
    autoDetectStreams();
  }

  private void autoDetectStreams() {
    List<ProcessEquipmentInterface> units = processSystem.getUnitOperations();
    for (ProcessEquipmentInterface unit : units) {
      if (unit instanceof neqsim.process.equipment.stream.StreamInterface) {
        if (feedStreamName == null) {
          feedStreamName = unit.getName();
        }
        productStreamName = unit.getName();
      }
    }
  }

  // Configuration methods

  /**
   * Sets the feed stream name.
   *
   * @param name the feed stream name
   * @return this matrix for chaining
   */
  public RiskMatrix setFeedStreamName(String name) {
    this.feedStreamName = name;
    return this;
  }

  /**
   * Sets the product stream name.
   *
   * @param name the product stream name
   * @return this matrix for chaining
   */
  public RiskMatrix setProductStreamName(String name) {
    this.productStreamName = name;
    return this;
  }

  /**
   * Sets the product price.
   *
   * @param price the price
   * @param unit the unit (e.g., "USD/tonne", "USD/kg", "NOK/Sm3")
   * @return this matrix for chaining
   */
  public RiskMatrix setProductPrice(double price, String unit) {
    if (unit.toLowerCase().contains("tonne") || unit.toLowerCase().contains("ton")) {
      this.productPricePerKg = price / 1000.0;
    } else if (unit.toLowerCase().contains("kg")) {
      this.productPricePerKg = price;
    } else if (unit.toLowerCase().contains("sm3") || unit.toLowerCase().contains("m3")) {
      // Assume gas ~0.8 kg/Sm3
      this.productPricePerKg = price / 0.8;
    } else {
      this.productPricePerKg = price;
    }
    return this;
  }

  /**
   * Sets the downtime cost per hour (fixed costs during shutdown).
   *
   * @param costPerHour cost per hour
   * @return this matrix for chaining
   */
  public RiskMatrix setDowntimeCostPerHour(double costPerHour) {
    this.downtimeCostPerHour = costPerHour;
    return this;
  }

  /**
   * Sets operating hours per year.
   *
   * @param hours operating hours
   * @return this matrix for chaining
   */
  public RiskMatrix setOperatingHoursPerYear(double hours) {
    this.operatingHoursPerYear = hours;
    return this;
  }

  /**
   * Adds custom equipment risk data (overrides database).
   *
   * @param equipmentName equipment name
   * @param failuresPerYear expected failures per year
   * @param mttr mean time to repair in hours
   * @return this matrix for chaining
   */
  public RiskMatrix addEquipmentRisk(String equipmentName, double failuresPerYear, double mttr) {
    RiskAssessment assessment = riskAssessments.get(equipmentName);
    if (assessment == null) {
      assessment = new RiskAssessment(equipmentName);
      riskAssessments.put(equipmentName, assessment);
    }
    double mtbf = failuresPerYear > 0 ? 8760.0 / failuresPerYear : 1000000;
    assessment.setProbabilityData(failuresPerYear, mtbf, mttr);
    return this;
  }

  // Analysis methods

  /**
   * Builds the risk matrix by analyzing all equipment.
   */
  public void buildRiskMatrix() {
    // Initialize impact analyzer
    if (impactAnalyzer == null) {
      impactAnalyzer = new ProductionImpactAnalyzer(processSystem);
      impactAnalyzer.setFeedStreamName(feedStreamName);
      impactAnalyzer.setProductStreamName(productStreamName);
    }

    // Analyze each equipment
    for (ProcessEquipmentInterface unit : processSystem.getUnitOperations()) {
      // Skip streams
      if (unit instanceof neqsim.process.equipment.stream.StreamInterface) {
        continue;
      }

      String name = unit.getName();
      analyzeEquipment(name, guessEquipmentType(unit));
    }
  }

  /**
   * Analyzes a specific equipment.
   *
   * @param equipmentName equipment name
   * @param equipmentType equipment type for reliability lookup
   */
  public void analyzeEquipment(String equipmentName, String equipmentType) {
    RiskAssessment assessment = riskAssessments.get(equipmentName);
    if (assessment == null) {
      assessment = new RiskAssessment(equipmentName);
      riskAssessments.put(equipmentName, assessment);
    }
    assessment.setEquipmentType(equipmentType);

    // Get probability data from reliability database (if not already set)
    if (assessment.getMtbf() == 0) {
      ReliabilityDataSource.ReliabilityData relData =
          reliabilitySource.getReliabilityData(equipmentType, "General");
      if (relData != null) {
        assessment.setProbabilityData(relData.getFailuresPerYear(), relData.getMtbf(),
            relData.getMttr());
      } else {
        // Default values
        assessment.setProbabilityData(0.5, 17520, 48);
      }
    }

    // Get consequence data from NeqSim simulation
    try {
      ProductionImpactResult impact = impactAnalyzer.analyzeFailureImpact(equipmentName);
      assessment.setConsequenceData(impact.getPercentLoss(), impact.getAbsoluteLoss());
    } catch (Exception e) {
      logger.warn("Could not analyze impact for {}: {}", equipmentName, e.getMessage());
      assessment.setConsequenceData(50.0, 0.0); // Default moderate
    }

    // Calculate risk
    assessment.calculateRisk();

    // Calculate costs
    calculateCost(assessment);
  }

  private void calculateCost(RiskAssessment assessment) {
    // Cost per failure = (lost production during repair) + (fixed downtime costs)
    double lostProductionCost =
        assessment.getProductionLossKgHr() * assessment.getMttr() * productPricePerKg;
    double fixedDowntimeCost = assessment.getMttr() * downtimeCostPerHour;
    double costPerFailure = lostProductionCost + fixedDowntimeCost;

    // Annual cost = cost per failure * failures per year
    double annualCost = costPerFailure * assessment.getFailuresPerYear();

    assessment.setCostData(costPerFailure, annualCost);
  }

  private String guessEquipmentType(ProcessEquipmentInterface unit) {
    String className = unit.getClass().getSimpleName().toLowerCase();
    if (className.contains("compressor")) {
      return "Compressor";
    } else if (className.contains("pump")) {
      return "Pump";
    } else if (className.contains("separator") || className.contains("scrubber")) {
      return "Separator";
    } else if (className.contains("heat") || className.contains("cooler")
        || className.contains("heater")) {
      return "HeatExchanger";
    } else if (className.contains("valve")) {
      return "Valve";
    } else if (className.contains("turbine") || className.contains("expander")) {
      return "Turbine";
    } else if (className.contains("column") || className.contains("distillation")) {
      return "Distillation";
    }
    return "General";
  }

  // Getter methods

  /**
   * Gets risk assessment for specific equipment.
   *
   * @param equipmentName equipment name
   * @return risk assessment or null
   */
  public RiskAssessment getRiskAssessment(String equipmentName) {
    return riskAssessments.get(equipmentName);
  }

  /**
   * Gets all risk assessments.
   *
   * @return map of equipment name to risk assessment
   */
  public Map<String, RiskAssessment> getRiskAssessments() {
    return new HashMap<String, RiskAssessment>(riskAssessments);
  }

  /**
   * Gets risk assessments sorted by risk score (highest first).
   *
   * @return sorted list of risk assessments
   */
  public List<RiskAssessment> getRiskAssessmentsSortedByRisk() {
    List<RiskAssessment> list = new ArrayList<RiskAssessment>(riskAssessments.values());
    list.sort(Comparator.comparingInt(RiskAssessment::getRiskScore).reversed());
    return list;
  }

  /**
   * Gets risk assessments sorted by annual cost (highest first).
   *
   * @return sorted list of risk assessments
   */
  public List<RiskAssessment> getRiskAssessmentsSortedByCost() {
    List<RiskAssessment> list = new ArrayList<RiskAssessment>(riskAssessments.values());
    list.sort(Comparator.comparingDouble(RiskAssessment::getAnnualRiskCost).reversed());
    return list;
  }

  /**
   * Gets equipment in a specific risk level.
   *
   * @param level the risk level
   * @return list of equipment names
   */
  public List<String> getEquipmentByRiskLevel(RiskLevel level) {
    List<String> result = new ArrayList<String>();
    for (RiskAssessment assessment : riskAssessments.values()) {
      if (assessment.getRiskLevel() == level) {
        result.add(assessment.getEquipmentName());
      }
    }
    return result;
  }

  /**
   * Gets total annual risk cost.
   *
   * @return total annual cost
   */
  public double getTotalAnnualRiskCost() {
    double total = 0;
    for (RiskAssessment assessment : riskAssessments.values()) {
      total += assessment.getAnnualRiskCost();
    }
    return total;
  }

  /**
   * Gets risk matrix data for visualization.
   *
   * @return list of matrix points (x=probability level, y=consequence level)
   */
  public List<Map<String, Object>> getMatrixData() {
    List<Map<String, Object>> data = new ArrayList<Map<String, Object>>();
    for (RiskAssessment assessment : riskAssessments.values()) {
      Map<String, Object> point = new HashMap<String, Object>();
      point.put("name", assessment.getEquipmentName());
      point.put("x", assessment.getProbabilityCategory().getLevel());
      point.put("y", assessment.getConsequenceCategory().getLevel());
      point.put("score", assessment.getRiskScore());
      point.put("color", assessment.getRiskLevel().getColor());
      point.put("annualCost", assessment.getAnnualRiskCost());
      data.add(point);
    }
    return data;
  }

  /**
   * Generates a summary map.
   *
   * @return summary map
   */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<String, Object>();

    // Summary
    Map<String, Object> summary = new HashMap<String, Object>();
    summary.put("totalEquipment", riskAssessments.size());
    summary.put("totalAnnualRiskCost", getTotalAnnualRiskCost());
    summary.put("criticalCount", getEquipmentByRiskLevel(RiskLevel.CRITICAL).size());
    summary.put("highCount", getEquipmentByRiskLevel(RiskLevel.HIGH).size());
    summary.put("mediumCount", getEquipmentByRiskLevel(RiskLevel.MEDIUM).size());
    summary.put("lowCount", getEquipmentByRiskLevel(RiskLevel.LOW).size());
    map.put("summary", summary);

    // Equipment list sorted by risk
    List<Map<String, Object>> equipment = new ArrayList<Map<String, Object>>();
    for (RiskAssessment assessment : getRiskAssessmentsSortedByRisk()) {
      equipment.add(assessment.toMap());
    }
    map.put("equipment", equipment);

    // Matrix data for plotting
    map.put("matrixData", getMatrixData());

    // Category definitions
    Map<String, Object> categories = new HashMap<String, Object>();

    List<Map<String, Object>> probCats = new ArrayList<Map<String, Object>>();
    for (ProbabilityCategory cat : ProbabilityCategory.values()) {
      Map<String, Object> c = new HashMap<String, Object>();
      c.put("level", cat.getLevel());
      c.put("name", cat.getName());
      probCats.add(c);
    }
    categories.put("probability", probCats);

    List<Map<String, Object>> consCats = new ArrayList<Map<String, Object>>();
    for (ConsequenceCategory cat : ConsequenceCategory.values()) {
      Map<String, Object> c = new HashMap<String, Object>();
      c.put("level", cat.getLevel());
      c.put("name", cat.getName());
      consCats.add(c);
    }
    categories.put("consequence", consCats);

    map.put("categories", categories);

    return map;
  }

  /**
   * Serializes to JSON.
   *
   * @return JSON string
   */
  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create()
        .toJson(toMap());
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("╔══════════════════════════════════════════════════════════════════╗\n");
    sb.append("║                    RISK MATRIX SUMMARY                          ║\n");
    sb.append("╠══════════════════════════════════════════════════════════════════╣\n");
    sb.append(String.format("║ Total Annual Risk Cost: $%,.0f%n", getTotalAnnualRiskCost()));
    sb.append(String.format("║ Critical Risk: %d  High: %d  Medium: %d  Low: %d%n",
        getEquipmentByRiskLevel(RiskLevel.CRITICAL).size(),
        getEquipmentByRiskLevel(RiskLevel.HIGH).size(),
        getEquipmentByRiskLevel(RiskLevel.MEDIUM).size(),
        getEquipmentByRiskLevel(RiskLevel.LOW).size()));
    sb.append("╠══════════════════════════════════════════════════════════════════╣\n");
    sb.append("║ Equipment Risk Ranking (by score):                              ║\n");
    sb.append("╟──────────────────────────────────────────────────────────────────╢\n");

    for (RiskAssessment assessment : getRiskAssessmentsSortedByRisk()) {
      String marker;
      switch (assessment.getRiskLevel()) {
        case CRITICAL:
          marker = "🔴";
          break;
        case HIGH:
          marker = "🟠";
          break;
        case MEDIUM:
          marker = "🟡";
          break;
        default:
          marker = "🟢";
      }
      sb.append(String.format("║ %s %-25s P:%d C:%d Score:%-2d Cost:$%,.0f%n", marker,
          assessment.getEquipmentName(), assessment.getProbabilityCategory().getLevel(),
          assessment.getConsequenceCategory().getLevel(), assessment.getRiskScore(),
          assessment.getAnnualRiskCost()));
    }
    sb.append("╚══════════════════════════════════════════════════════════════════╝\n");
    return sb.toString();
  }
}
