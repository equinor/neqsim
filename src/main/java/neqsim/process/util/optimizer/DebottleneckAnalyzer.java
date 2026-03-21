package neqsim.process.util.optimizer;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.GsonBuilder;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.capacity.BottleneckResult;
import neqsim.process.equipment.capacity.CapacityConstrainedEquipment;
import neqsim.process.equipment.capacity.CapacityConstraint;
import neqsim.process.processmodel.ProcessSystem;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Systematically identifies and ranks equipment bottlenecks in a process system.
 *
 * <p>
 * Scans all equipment implementing {@link CapacityConstrainedEquipment}, collects utilization data,
 * ranks equipment by how close they are to design limits, and provides actionable suggestions for
 * debottlenecking.
 * </p>
 *
 * <p>
 * Builds on the existing capacity constraint framework in
 * {@code neqsim.process.equipment.capacity}.
 * </p>
 *
 * <p>
 * Usage example:
 * </p>
 *
 * <pre>
 * {@code
 * DebottleneckAnalyzer analyzer = new DebottleneckAnalyzer(process);
 * analyzer.setWarningThreshold(0.85);
 * analyzer.analyze();
 * List<EquipmentStatus> ranked = analyzer.getRankedEquipment();
 * String primary = analyzer.getPrimaryBottleneck();
 * String json = analyzer.toJson();
 * }
 * </pre>
 *
 * @author ESOL
 * @version 1.0
 */
public class DebottleneckAnalyzer implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(DebottleneckAnalyzer.class);

  private final ProcessSystem processSystem;
  private double warningThreshold = 0.85;
  private double criticalThreshold = 0.95;

  private final List<EquipmentStatus> rankedEquipment = new ArrayList<>();
  private boolean analyzed = false;

  /**
   * Status report for a single piece of equipment.
   */
  public static class EquipmentStatus implements Serializable {
    private static final long serialVersionUID = 1L;

    /** Equipment name. */
    public final String name;
    /** Equipment type (class simple name). */
    public final String type;
    /** Maximum utilization across all constraints (0.0-1.0+). */
    public final double maxUtilization;
    /** Name of the most limiting constraint. */
    public final String limitingConstraint;
    /** Current value of the limiting constraint. */
    public final double currentValue;
    /** Design limit of the limiting constraint. */
    public final double designLimit;
    /** Status: OK, WARNING, CRITICAL, OVERLOADED. */
    public final String status;
    /** Debottleneck suggestion. */
    public final String suggestion;

    /**
     * Creates an equipment status entry.
     *
     * @param name equipment name
     * @param type equipment type
     * @param maxUtil maximum utilization
     * @param limitingConstraint name of limiting constraint
     * @param currentValue current value
     * @param designLimit design limit
     * @param status status string
     * @param suggestion debottleneck suggestion
     */
    public EquipmentStatus(String name, String type, double maxUtil, String limitingConstraint,
        double currentValue, double designLimit, String status, String suggestion) {
      this.name = name;
      this.type = type;
      this.maxUtilization = maxUtil;
      this.limitingConstraint = limitingConstraint;
      this.currentValue = currentValue;
      this.designLimit = designLimit;
      this.status = status;
      this.suggestion = suggestion;
    }
  }

  /**
   * Creates a debottleneck analyzer for the given process system.
   *
   * @param processSystem the process system to analyze
   */
  public DebottleneckAnalyzer(ProcessSystem processSystem) {
    this.processSystem = processSystem;
  }

  /**
   * Sets the warning threshold (fraction).
   *
   * @param threshold warning threshold (default 0.85 = 85%)
   */
  public void setWarningThreshold(double threshold) {
    this.warningThreshold = threshold;
    this.analyzed = false;
  }

  /**
   * Sets the critical threshold (fraction).
   *
   * @param threshold critical threshold (default 0.95 = 95%)
   */
  public void setCriticalThreshold(double threshold) {
    this.criticalThreshold = threshold;
    this.analyzed = false;
  }

  /**
   * Runs the debottleneck analysis.
   */
  public void analyze() {
    rankedEquipment.clear();

    for (ProcessEquipmentInterface equip : processSystem.getUnitOperations()) {
      if (equip instanceof CapacityConstrainedEquipment) {
        CapacityConstrainedEquipment constrained = (CapacityConstrainedEquipment) equip;
        if (!constrained.isCapacityAnalysisEnabled()) {
          continue;
        }

        double maxUtil = constrained.getMaxUtilization();
        if (Double.isNaN(maxUtil)) {
          continue;
        }

        CapacityConstraint bottleneckConstraint = constrained.getBottleneckConstraint();
        String constraintName = "N/A";
        double currentVal = 0.0;
        double designLim = 0.0;

        if (bottleneckConstraint != null) {
          constraintName = bottleneckConstraint.getName();
          currentVal = bottleneckConstraint.getCurrentValue();
          designLim = bottleneckConstraint.getDesignValue();
        }

        String status;
        if (maxUtil > 1.0) {
          status = "OVERLOADED";
        } else if (maxUtil >= criticalThreshold) {
          status = "CRITICAL";
        } else if (maxUtil >= warningThreshold) {
          status = "WARNING";
        } else {
          status = "OK";
        }

        String suggestion = generateSuggestion(equip, constraintName, maxUtil);

        rankedEquipment.add(new EquipmentStatus(equip.getName(), equip.getClass().getSimpleName(),
            maxUtil, constraintName, currentVal, designLim, status, suggestion));
      }
    }

    // Sort by utilization, highest first
    Collections.sort(rankedEquipment, new Comparator<EquipmentStatus>() {
      @Override
      public int compare(EquipmentStatus a, EquipmentStatus b) {
        return Double.compare(b.maxUtilization, a.maxUtilization);
      }
    });

    analyzed = true;
  }

  /**
   * Generates a debottleneck suggestion for the given equipment.
   *
   * @param equip the equipment
   * @param constraintName the limiting constraint name
   * @param utilization current utilization
   * @return suggestion text
   */
  private String generateSuggestion(ProcessEquipmentInterface equip, String constraintName,
      double utilization) {
    if (utilization < warningThreshold) {
      return "No action required";
    }

    String type = equip.getClass().getSimpleName();
    String lowerConstraint = constraintName.toLowerCase();

    if (type.contains("Compressor")) {
      if (lowerConstraint.contains("surge")) {
        return "Consider anti-surge recycle valve or variable speed drive";
      } else if (lowerConstraint.contains("power")) {
        return "Consider larger driver or parallel compression stage";
      } else if (lowerConstraint.contains("speed")) {
        return "Evaluate driver upgrade or add parallel train";
      }
      return "Evaluate compressor performance map and consider rewheel or parallel unit";
    } else if (type.contains("Separator")) {
      if (lowerConstraint.contains("gas") || lowerConstraint.contains("load")) {
        return "Consider larger vessel diameter or demisting upgrade";
      } else if (lowerConstraint.contains("liquid") || lowerConstraint.contains("residence")) {
        return "Consider longer vessel or additional separation stage";
      }
      return "Evaluate separator internals upgrade or parallel vessel";
    } else if (type.contains("HeatExchanger") || type.contains("Cooler")
        || type.contains("Heater")) {
      if (lowerConstraint.contains("duty")) {
        return "Consider additional heat transfer area or parallel exchanger";
      } else if (lowerConstraint.contains("approach")) {
        return "Increase UA value via tube insert or additional area";
      }
      return "Evaluate tube cleaning, enhanced surfaces, or parallel unit";
    } else if (type.contains("Valve")) {
      return "Consider larger valve Cv or parallel bypass";
    } else if (type.contains("Pump")) {
      if (lowerConstraint.contains("npsh")) {
        return "Raise suction vessel elevation or reduce line losses";
      }
      return "Consider larger impeller or parallel pump";
    } else if (type.contains("Pipe")) {
      return "Consider larger pipe diameter or looped pipeline";
    }

    return "Review design basis and consider equipment upgrade";
  }

  /**
   * Gets the primary bottleneck equipment name.
   *
   * @return name of the equipment with highest utilization, or empty string if none
   */
  public String getPrimaryBottleneck() {
    if (!analyzed) {
      analyze();
    }
    if (rankedEquipment.isEmpty()) {
      return "";
    }
    return rankedEquipment.get(0).name;
  }

  /**
   * Gets all equipment ranked by utilization (highest first).
   *
   * @return list of EquipmentStatus ordered by utilization
   */
  public List<EquipmentStatus> getRankedEquipment() {
    if (!analyzed) {
      analyze();
    }
    return Collections.unmodifiableList(rankedEquipment);
  }

  /**
   * Gets only equipment above the warning threshold.
   *
   * @return list of equipment that are at warning level or above
   */
  public List<EquipmentStatus> getConstrainedEquipment() {
    if (!analyzed) {
      analyze();
    }
    List<EquipmentStatus> constrained = new ArrayList<>();
    for (EquipmentStatus es : rankedEquipment) {
      if (es.maxUtilization >= warningThreshold) {
        constrained.add(es);
      }
    }
    return constrained;
  }

  /**
   * Gets the number of overloaded equipment items.
   *
   * @return count of equipment exceeding design limit
   */
  public int getOverloadedCount() {
    if (!analyzed) {
      analyze();
    }
    int count = 0;
    for (EquipmentStatus es : rankedEquipment) {
      if (es.maxUtilization > 1.0) {
        count++;
      }
    }
    return count;
  }

  /**
   * Gets the overall process capacity utilization (based on highest equipment utilization).
   *
   * @return maximum utilization fraction across all equipment
   */
  public double getOverallUtilization() {
    if (!analyzed) {
      analyze();
    }
    if (rankedEquipment.isEmpty()) {
      return 0.0;
    }
    return rankedEquipment.get(0).maxUtilization;
  }

  /**
   * Returns the analysis results as a JSON string.
   *
   * @return JSON representation of debottleneck analysis
   */
  public String toJson() {
    if (!analyzed) {
      analyze();
    }

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("analysisType", "Debottleneck Analysis");
    result.put("warningThreshold", warningThreshold);
    result.put("criticalThreshold", criticalThreshold);
    result.put("totalEquipmentAnalyzed", rankedEquipment.size());
    result.put("overloadedCount", getOverloadedCount());
    result.put("overallUtilization", getOverallUtilization());

    if (!rankedEquipment.isEmpty()) {
      result.put("primaryBottleneck", rankedEquipment.get(0).name);
    }

    List<Map<String, Object>> equipmentList = new ArrayList<>();
    for (EquipmentStatus es : rankedEquipment) {
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("name", es.name);
      m.put("type", es.type);
      m.put("utilization", es.maxUtilization);
      m.put("limitingConstraint", es.limitingConstraint);
      m.put("currentValue", es.currentValue);
      m.put("designLimit", es.designLimit);
      m.put("status", es.status);
      m.put("suggestion", es.suggestion);
      equipmentList.add(m);
    }
    result.put("equipment", equipmentList);

    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create()
        .toJson(result);
  }
}
