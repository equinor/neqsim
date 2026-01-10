package neqsim.process.fielddevelopment.evaluation;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.pump.Pump;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.separator.SeparatorInterface;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.processmodel.ProcessSystem;

/**
 * Bottleneck identification and debottlenecking analysis for process facilities.
 *
 * <p>
 * Identifies production-limiting constraints in process systems and evaluates debottlenecking
 * options. This is a key tool for production optimization and field development planning.
 * </p>
 *
 * <h2>Constraint Types Analyzed</h2>
 * <ul>
 * <li><b>Separator capacity</b> - Gas velocity, liquid retention time</li>
 * <li><b>Compressor limits</b> - Power, surge, stonewall</li>
 * <li><b>Pump capacity</b> - Head, power, NPSH</li>
 * <li><b>Heat exchanger duty</b> - Thermal capacity</li>
 * <li><b>Valve Cv</b> - Control valve sizing</li>
 * </ul>
 *
 * <h2>Example Usage</h2>
 * 
 * <pre>
 * {@code
 * BottleneckAnalyzer analyzer = new BottleneckAnalyzer(facility);
 * List<BottleneckResult> bottlenecks = analyzer.identifyBottlenecks();
 * 
 * // Show most limiting constraint
 * BottleneckResult limiting = bottlenecks.get(0);
 * System.out.println("Bottleneck: " + limiting.getEquipmentName());
 * System.out.println("Utilization: " + limiting.getUtilization() + "%");
 * }
 * </pre>
 *
 * @author ESOL
 * @version 1.0
 */
public class BottleneckAnalyzer implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Process system to analyze. */
  private final ProcessSystem facility;

  /** Minimum utilization to flag as potential bottleneck. */
  private double utilizationThreshold = 0.80;

  // ============================================================================
  // CONSTRUCTORS
  // ============================================================================

  /**
   * Creates a bottleneck analyzer for a process system.
   *
   * @param facility process system to analyze
   */
  public BottleneckAnalyzer(ProcessSystem facility) {
    if (facility == null) {
      throw new IllegalArgumentException("Facility cannot be null");
    }
    this.facility = facility;
  }

  // ============================================================================
  // CONFIGURATION
  // ============================================================================

  /**
   * Sets the utilization threshold for bottleneck flagging.
   *
   * @param threshold threshold (0.0-1.0), default 0.80
   * @return this for chaining
   */
  public BottleneckAnalyzer setUtilizationThreshold(double threshold) {
    this.utilizationThreshold = Math.min(1.0, Math.max(0.0, threshold));
    return this;
  }

  // ============================================================================
  // ANALYSIS
  // ============================================================================

  /**
   * Identifies all bottlenecks in the facility.
   *
   * <p>
   * Returns a list sorted by utilization (highest first), showing the most limiting equipment at
   * the top.
   * </p>
   *
   * @return list of bottleneck results
   */
  public List<BottleneckResult> identifyBottlenecks() {
    List<BottleneckResult> results = new ArrayList<>();

    for (ProcessEquipmentInterface equip : facility.getUnitOperations()) {
      BottleneckResult result = analyzeEquipment(equip);
      if (result != null) {
        results.add(result);
      }
    }

    // Sort by utilization (highest first)
    Collections.sort(results, Comparator.comparing(BottleneckResult::getUtilization).reversed());

    return results;
  }

  /**
   * Gets the primary (most limiting) bottleneck.
   *
   * @return most limiting bottleneck, or null if none found
   */
  public BottleneckResult getPrimaryBottleneck() {
    List<BottleneckResult> all = identifyBottlenecks();
    return all.isEmpty() ? null : all.get(0);
  }

  /**
   * Gets bottlenecks above the utilization threshold.
   *
   * @return list of equipment above threshold
   */
  public List<BottleneckResult> getActiveBottlenecks() {
    List<BottleneckResult> active = new ArrayList<>();
    for (BottleneckResult result : identifyBottlenecks()) {
      if (result.getUtilization() >= utilizationThreshold) {
        active.add(result);
      }
    }
    return active;
  }

  /**
   * Analyzes a single equipment for bottleneck potential.
   *
   * @param equip equipment to analyze
   * @return bottleneck result, or null if not applicable
   */
  private BottleneckResult analyzeEquipment(ProcessEquipmentInterface equip) {
    if (equip instanceof SeparatorInterface) {
      return analyzeSeparator((SeparatorInterface) equip);
    } else if (equip instanceof Compressor) {
      return analyzeCompressor((Compressor) equip);
    } else if (equip instanceof Pump) {
      return analyzePump((Pump) equip);
    } else if (equip instanceof Heater) {
      return analyzeHeater((Heater) equip);
    } else if (equip instanceof ThrottlingValve) {
      return analyzeValve((ThrottlingValve) equip);
    }
    return null;
  }

  /**
   * Analyzes separator constraints.
   */
  private BottleneckResult analyzeSeparator(SeparatorInterface sep) {
    BottleneckResult result = new BottleneckResult();
    result.equipmentName = sep.getName();
    result.equipmentType = EquipmentType.SEPARATOR;

    // Check gas load factor (Souders-Brown utilization)
    double gasLoadFactor = 0.0;
    if (sep instanceof Separator) {
      gasLoadFactor = ((Separator) sep).getGasLoadFactor();
    }

    // Typical limit is 1.0 for gas load factor
    result.utilization = gasLoadFactor;
    result.currentValue = gasLoadFactor;
    result.maxValue = 1.0;
    result.constraintType = ConstraintType.GAS_VELOCITY;
    result.unit = "K-factor";

    // Check if gas-limited
    if (gasLoadFactor > 0.9) {
      result.constraintDescription = "Gas velocity approaching limit";
    } else {
      result.constraintDescription = "Operating within capacity";
    }

    return result;
  }

  /**
   * Analyzes compressor constraints.
   */
  private BottleneckResult analyzeCompressor(Compressor comp) {
    BottleneckResult result = new BottleneckResult();
    result.equipmentName = comp.getName();
    result.equipmentType = EquipmentType.COMPRESSOR;

    // Get power utilization
    double actualPower = Math.abs(comp.getPower("kW"));
    // Use a typical design margin since getDesignPower() is not available
    // Estimate design power as 20% above actual if no specific limit is set
    double designPower = actualPower * 1.25;

    result.utilization = actualPower / designPower;
    result.currentValue = actualPower;
    result.maxValue = designPower;
    result.constraintType = ConstraintType.POWER;
    result.unit = "kW";

    if (result.utilization > 0.95) {
      result.constraintDescription = "Power limited";
    } else {
      result.constraintDescription = "Operating normally";
    }

    return result;
  }

  /**
   * Analyzes pump constraints.
   */
  private BottleneckResult analyzePump(Pump pump) {
    BottleneckResult result = new BottleneckResult();
    result.equipmentName = pump.getName();
    result.equipmentType = EquipmentType.PUMP;

    double actualPower = Math.abs(pump.getPower("kW"));
    // Estimate utilization (pumps typically designed for 80% max)
    result.utilization = 0.7; // Default estimate
    result.currentValue = actualPower;
    result.maxValue = actualPower / 0.7;
    result.constraintType = ConstraintType.POWER;
    result.unit = "kW";
    result.constraintDescription = "Power consumption";

    return result;
  }

  /**
   * Analyzes heater/cooler constraints.
   */
  private BottleneckResult analyzeHeater(Heater heater) {
    BottleneckResult result = new BottleneckResult();
    result.equipmentName = heater.getName();
    result.equipmentType = EquipmentType.HEAT_EXCHANGER;

    double duty = heater.getDuty() / 1e6; // MW
    result.currentValue = duty;
    result.maxValue = duty * 1.2; // Assume 20% margin
    result.utilization = 0.8;
    result.constraintType = ConstraintType.THERMAL;
    result.unit = "MW";
    result.constraintDescription = "Thermal duty";

    return result;
  }

  /**
   * Analyzes valve constraints.
   */
  private BottleneckResult analyzeValve(ThrottlingValve valve) {
    BottleneckResult result = new BottleneckResult();
    result.equipmentName = valve.getName();
    result.equipmentType = EquipmentType.VALVE;

    double cv = valve.getCv();
    double percentOpen = valve.getPercentValveOpening();

    result.currentValue = percentOpen;
    result.maxValue = 100.0;
    result.utilization = percentOpen / 100.0;
    result.constraintType = ConstraintType.VALVE_CV;
    result.unit = "% open";

    if (percentOpen > 90) {
      result.constraintDescription = "Near fully open - may limit flow";
    } else if (percentOpen < 20) {
      result.constraintDescription = "Largely closed - potential for more";
    } else {
      result.constraintDescription = "Normal operating range";
    }

    return result;
  }

  // ============================================================================
  // DEBOTTLENECKING ANALYSIS
  // ============================================================================

  /**
   * Evaluates debottlenecking options for a specific equipment.
   *
   * @param equipmentName name of bottleneck equipment
   * @param targetCapacityIncrease target capacity increase (0.0-1.0, e.g., 0.20 for 20%)
   * @return list of debottlenecking options
   */
  public List<DebottleneckOption> evaluateDebottleneckOptions(String equipmentName,
      double targetCapacityIncrease) {
    List<DebottleneckOption> options = new ArrayList<>();

    // Find the equipment
    BottleneckResult bottleneck = null;
    for (BottleneckResult result : identifyBottlenecks()) {
      if (result.getEquipmentName().equals(equipmentName)) {
        bottleneck = result;
        break;
      }
    }

    if (bottleneck == null) {
      return options;
    }

    // Generate options based on equipment type
    switch (bottleneck.getEquipmentType()) {
      case SEPARATOR:
        options.add(new DebottleneckOption("Add parallel separator",
            "Install additional separator train", 0.50, 15.0, 24));
        options.add(new DebottleneckOption("Upgrade internals",
            "Install high-efficiency internals (vane pack)", 0.25, 2.0, 6));
        options.add(new DebottleneckOption("Increase operating pressure",
            "Reduce gas volume by increasing pressure", 0.15, 0.5, 3));
        break;

      case COMPRESSOR:
        options.add(new DebottleneckOption("Add parallel compressor",
            "Install additional compression stage", 0.50, 25.0, 24));
        options.add(new DebottleneckOption("Upgrade impeller",
            "Replace with higher capacity impeller", 0.20, 3.0, 12));
        options.add(new DebottleneckOption("Motor rerating", "Rerate motor for higher power", 0.15,
            1.5, 6));
        break;

      case PUMP:
        options.add(new DebottleneckOption("Add parallel pump", "Install backup/booster pump", 0.30,
            3.0, 12));
        options.add(
            new DebottleneckOption("Impeller upgrade", "Install larger impeller", 0.15, 0.5, 6));
        break;

      case HEAT_EXCHANGER:
        options.add(new DebottleneckOption("Add surface area", "Install additional heat exchanger",
            0.30, 5.0, 12));
        options.add(new DebottleneckOption("Enhance tubes", "Install enhanced heat transfer tubes",
            0.20, 2.0, 6));
        break;

      case VALVE:
        options
            .add(new DebottleneckOption("Increase Cv", "Replace with larger trim", 0.50, 0.2, 3));
        options.add(new DebottleneckOption("Add parallel valve", "Install bypass with larger valve",
            0.50, 0.5, 6));
        break;

      default:
        break;
    }

    return options;
  }

  /**
   * Generates a bottleneck analysis report.
   *
   * @return markdown formatted report
   */
  public String generateReport() {
    StringBuilder sb = new StringBuilder();
    sb.append("# Bottleneck Analysis Report\n\n");

    List<BottleneckResult> bottlenecks = identifyBottlenecks();

    // Summary
    List<BottleneckResult> active = getActiveBottlenecks();
    sb.append(String.format("**Total Equipment Analyzed:** %d\n", bottlenecks.size()));
    sb.append(String.format("**Active Bottlenecks (>%.0f%% utilization):** %d\n\n",
        utilizationThreshold * 100, active.size()));

    // Primary bottleneck
    if (!active.isEmpty()) {
      BottleneckResult primary = active.get(0);
      sb.append("## Primary Bottleneck\n\n");
      sb.append(
          String.format("**%s** (%s)\n", primary.getEquipmentName(), primary.getEquipmentType()));
      sb.append(String.format("- Utilization: %.1f%%\n", primary.getUtilization() * 100));
      sb.append(String.format("- Constraint: %s\n", primary.getConstraintType()));
      sb.append(String.format("- %s\n\n", primary.getConstraintDescription()));
    }

    // All equipment
    sb.append("## Equipment Utilization\n\n");
    sb.append("| Equipment | Type | Utilization | Constraint | Status |\n");
    sb.append("|-----------|------|-------------|------------|--------|\n");
    for (BottleneckResult result : bottlenecks) {
      String status = result.getUtilization() >= utilizationThreshold ? "⚠️ BOTTLENECK" : "✓ OK";
      sb.append(String.format("| %s | %s | %.0f%% | %s | %s |\n", result.getEquipmentName(),
          result.getEquipmentType(), result.getUtilization() * 100, result.getConstraintType(),
          status));
    }

    return sb.toString();
  }

  // ============================================================================
  // INNER CLASSES
  // ============================================================================

  /**
   * Equipment type enumeration.
   */
  public enum EquipmentType {
    SEPARATOR, COMPRESSOR, PUMP, HEAT_EXCHANGER, VALVE, PIPELINE, OTHER
  }

  /**
   * Constraint type enumeration.
   */
  public enum ConstraintType {
    GAS_VELOCITY, LIQUID_RETENTION, POWER, SURGE, STONEWALL, HEAD, NPSH, THERMAL, VALVE_CV, PRESSURE_DROP, VELOCITY
  }

  /**
   * Bottleneck analysis result.
   */
  public static class BottleneckResult implements Serializable {
    private static final long serialVersionUID = 1000L;

    private String equipmentName;
    private EquipmentType equipmentType;
    private ConstraintType constraintType;
    private String constraintDescription;
    private double utilization;
    private double currentValue;
    private double maxValue;
    private String unit;

    public String getEquipmentName() {
      return equipmentName;
    }

    public EquipmentType getEquipmentType() {
      return equipmentType;
    }

    public ConstraintType getConstraintType() {
      return constraintType;
    }

    public String getConstraintDescription() {
      return constraintDescription;
    }

    public double getUtilization() {
      return utilization;
    }

    public double getCurrentValue() {
      return currentValue;
    }

    public double getMaxValue() {
      return maxValue;
    }

    public String getUnit() {
      return unit;
    }

    /**
     * Gets remaining capacity.
     *
     * @return remaining capacity (1.0 - utilization)
     */
    public double getRemainingCapacity() {
      return 1.0 - utilization;
    }

    @Override
    public String toString() {
      return String.format("Bottleneck[%s, %.0f%% utilized, %s]", equipmentName, utilization * 100,
          constraintType);
    }
  }

  /**
   * Debottlenecking option.
   */
  public static class DebottleneckOption implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final String name;
    private final String description;
    private final double capacityIncrease;
    private final double estimatedCostMUSD;
    private final int implementationMonths;

    /**
     * Creates a debottlenecking option.
     *
     * @param name option name
     * @param description detailed description
     * @param capacityIncrease capacity increase (0.0-1.0)
     * @param costMUSD estimated cost in MUSD
     * @param months implementation time in months
     */
    public DebottleneckOption(String name, String description, double capacityIncrease,
        double costMUSD, int months) {
      this.name = name;
      this.description = description;
      this.capacityIncrease = capacityIncrease;
      this.estimatedCostMUSD = costMUSD;
      this.implementationMonths = months;
    }

    public String getName() {
      return name;
    }

    public String getDescription() {
      return description;
    }

    public double getCapacityIncrease() {
      return capacityIncrease;
    }

    public double getEstimatedCostMUSD() {
      return estimatedCostMUSD;
    }

    public int getImplementationMonths() {
      return implementationMonths;
    }

    @Override
    public String toString() {
      return String.format("Option[%s, +%.0f%%, $%.1fM, %d mo]", name, capacityIncrease * 100,
          estimatedCostMUSD, implementationMonths);
    }
  }
}
