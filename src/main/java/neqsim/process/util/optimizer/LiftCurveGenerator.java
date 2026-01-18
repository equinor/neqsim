package neqsim.process.util.optimizer;

import java.io.Serializable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.processmodel.ProcessModel;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;

/**
 * Generates lift curves (VLP tables) for reservoir simulator integration.
 *
 * <p>
 * This class generates flow rate vs. pressure tables for use in Eclipse and other reservoir
 * simulators. It sweeps through specified THP (outlet pressure) values and calculates the required
 * BHP (inlet pressure) for each flow rate.
 * </p>
 *
 * <h2>Modes of Operation</h2>
 * <ul>
 * <li><b>BHP calculation mode</b> - Given flow rates and THP values, calculate required BHP</li>
 * <li><b>Flow calculation mode</b> - Given BHP and THP values, calculate achievable flow rate</li>
 * </ul>
 *
 * <h2>Example Usage</h2>
 * 
 * <pre>
 * // Create process system with pipeline
 * ProcessSystem process = new ProcessSystem();
 * process.add(inlet);
 * process.add(pipe);
 * 
 * // Create generator
 * LiftCurveGenerator generator = new LiftCurveGenerator(process, "inlet", "pipeline");
 * generator.setMaxVelocity(20.0); // m/s constraint
 * 
 * // Define operating envelope
 * double[] flowRates = {1000, 5000, 10000, 20000, 30000}; // kg/hr
 * double[] thpValues = {20, 40, 60, 80}; // bara
 * 
 * // Generate table
 * LiftCurveTable table = generator.generateTable(flowRates, thpValues, "bara", "kg/hr");
 * 
 * // Export to Eclipse format
 * String eclipseFormat = table.toEclipseFormat();
 * </pre>
 *
 * @author ESOL
 * @version 1.0
 */
public class LiftCurveGenerator implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Logger object for class. */
  private static final Logger logger = LogManager.getLogger(LiftCurveGenerator.class);

  /** The underlying flow rate optimizer. */
  private FlowRateOptimizer optimizer;

  /** ProcessSystem being analyzed (if in process system mode). */
  private ProcessSystem processSystem;

  /** ProcessModel being analyzed (if in process model mode). */
  private ProcessModel processModel;

  /** Base fluid for calculations. */
  private SystemInterface baseFluid;

  /** Maximum velocity constraint in m/s. */
  private double maxVelocity = Double.MAX_VALUE;

  /** Whether to check capacity constraints. */
  private boolean checkConstraints = true;

  /** Table name for generated tables. */
  private String tableName = "VLP";

  /**
   * Creates a lift curve generator for a ProcessSystem.
   *
   * @param processSystem the process system to analyze
   * @param inletStreamName name of the inlet stream
   * @param outletStreamName name of the outlet stream
   */
  public LiftCurveGenerator(ProcessSystem processSystem, String inletStreamName,
      String outletStreamName) {
    this.processSystem = processSystem;
    this.optimizer = new FlowRateOptimizer(processSystem, inletStreamName, outletStreamName);
  }

  /**
   * Creates a lift curve generator for a ProcessModel.
   *
   * @param processModel the process model to analyze
   * @param inletStreamName name of the inlet stream
   * @param outletStreamName name of the outlet stream
   */
  public LiftCurveGenerator(ProcessModel processModel, String inletStreamName,
      String outletStreamName) {
    this.processModel = processModel;
    this.optimizer = new FlowRateOptimizer(processModel, inletStreamName, outletStreamName);
  }

  /**
   * Generates a lift curve table with BHP values for given flow rates and THP values.
   *
   * <p>
   * For each combination of flow rate and THP, this method calculates the required BHP (inlet
   * pressure) to achieve that operating point. If the point is infeasible (e.g., due to constraint
   * violations), the BHP is set to NaN.
   * </p>
   *
   * @param flowRates array of flow rates to evaluate
   * @param thpValues array of THP (outlet pressure) values
   * @param pressureUnit unit for pressure values
   * @param flowRateUnit unit for flow rates
   * @return generated lift curve table
   */
  public LiftCurveTable generateTable(double[] flowRates, double[] thpValues, String pressureUnit,
      String flowRateUnit) {
    logger.info("Generating lift curve table: {} flow rates x {} THP values", flowRates.length,
        thpValues.length);

    LiftCurveTable table = new LiftCurveTable(flowRates.length, thpValues.length);
    table.setFlowRates(flowRates.clone());
    table.setThpValues(thpValues.clone());
    table.setPressureUnit(pressureUnit);
    table.setFlowRateUnit(flowRateUnit);
    table.setTableName(tableName);

    // Configure optimizer
    optimizer.setMaxVelocity(maxVelocity);
    optimizer.setCheckCapacityConstraints(checkConstraints);

    int feasibleCount = 0;
    int totalCount = flowRates.length * thpValues.length;

    // Calculate BHP for each flow rate and THP combination
    for (int i = 0; i < flowRates.length; i++) {
      for (int j = 0; j < thpValues.length; j++) {
        double flowRate = flowRates[i];
        double thp = thpValues[j];

        try {
          // Use optimizer to find required inlet pressure
          FlowRateOptimizationResult result =
              optimizer.findInletPressure(flowRate, flowRateUnit, thp, pressureUnit);

          if (result.isFeasible()) {
            table.setBHP(i, j, result.getInletPressure());
            feasibleCount++;
            logger.debug("Flow={} {}, THP={} {} -> BHP={} {}", flowRate, flowRateUnit, thp,
                pressureUnit, result.getInletPressure(), pressureUnit);
          } else {
            table.setBHP(i, j, Double.NaN);
            logger.debug("Flow={} {}, THP={} {} -> INFEASIBLE: {}", flowRate, flowRateUnit, thp,
                pressureUnit, result.getInfeasibilityReason());
          }
        } catch (Exception e) {
          logger.warn("Error calculating BHP for flow={}, THP={}: {}", flowRate, thp,
              e.getMessage());
          table.setBHP(i, j, Double.NaN);
        }
      }
    }

    logger.info("Lift curve generation complete: {}/{} points feasible ({}%)", feasibleCount,
        totalCount, String.format("%.1f", 100.0 * feasibleCount / totalCount));

    return table;
  }

  /**
   * Generates a lift curve table by calculating flow rates for given BHP and THP combinations.
   *
   * <p>
   * This is the inverse of {@link #generateTable} - for each combination of BHP (inlet pressure)
   * and THP (outlet pressure), it calculates the achievable flow rate.
   * </p>
   *
   * @param bhpValues array of BHP (inlet pressure) values
   * @param thpValues array of THP (outlet pressure) values
   * @param pressureUnit unit for pressure values
   * @param flowRateUnit unit for output flow rates
   * @return generated table with flow rates instead of BHP
   */
  public LiftCurveTable generateFlowRateTable(double[] bhpValues, double[] thpValues,
      String pressureUnit, String flowRateUnit) {
    logger.info("Generating flow rate table: {} BHP values x {} THP values", bhpValues.length,
        thpValues.length);

    LiftCurveTable table = new LiftCurveTable(bhpValues.length, thpValues.length);
    table.setFlowRates(bhpValues.clone()); // BHP as row headers
    table.setThpValues(thpValues.clone());
    table.setPressureUnit(pressureUnit);
    table.setFlowRateUnit(flowRateUnit);
    table.setTableName(tableName + "_FlowRate");

    // Configure optimizer
    optimizer.setMaxVelocity(maxVelocity);
    optimizer.setCheckCapacityConstraints(checkConstraints);

    int feasibleCount = 0;

    // Calculate flow rate for each BHP and THP combination
    for (int i = 0; i < bhpValues.length; i++) {
      for (int j = 0; j < thpValues.length; j++) {
        double bhp = bhpValues[i];
        double thp = thpValues[j];

        // Skip if BHP <= THP (no positive flow possible)
        if (bhp <= thp) {
          table.setBHP(i, j, Double.NaN);
          continue;
        }

        try {
          // Use optimizer to find flow rate
          FlowRateOptimizationResult result = optimizer.findFlowRate(bhp, thp, pressureUnit);

          if (result.isFeasible()) {
            // Store flow rate in the BHP matrix (reusing the structure)
            table.setBHP(i, j, result.getFlowRate());
            feasibleCount++;
            logger.debug("BHP={} {}, THP={} {} -> Flow={} {}", bhp, pressureUnit, thp, pressureUnit,
                result.getFlowRate(), result.getFlowRateUnit());
          } else {
            table.setBHP(i, j, Double.NaN);
            logger.debug("BHP={} {}, THP={} {} -> INFEASIBLE: {}", bhp, pressureUnit, thp,
                pressureUnit, result.getInfeasibilityReason());
          }
        } catch (Exception e) {
          logger.warn("Error calculating flow for BHP={}, THP={}: {}", bhp, thp, e.getMessage());
          table.setBHP(i, j, Double.NaN);
        }
      }
    }

    logger.info("Flow rate table generation complete: {}/{} points feasible", feasibleCount,
        bhpValues.length * thpValues.length);

    return table;
  }

  /**
   * Generates a table with automatic range determination based on equipment limits.
   *
   * @param numFlowPoints number of flow rate points
   * @param numPressurePoints number of pressure points
   * @param minTHP minimum THP value
   * @param maxTHP maximum THP value
   * @param pressureUnit pressure unit
   * @param flowRateUnit flow rate unit
   * @return generated table
   */
  public LiftCurveTable generateTableAutoRange(int numFlowPoints, int numPressurePoints,
      double minTHP, double maxTHP, String pressureUnit, String flowRateUnit) {
    // Generate evenly spaced THP values
    double[] thpValues = new double[numPressurePoints];
    for (int j = 0; j < numPressurePoints; j++) {
      thpValues[j] = minTHP + j * (maxTHP - minTHP) / (numPressurePoints - 1);
    }

    // Generate flow rates based on base fluid flow rate
    double baseFlow = optimizer.getInitialFlowGuess();
    double minFlow = baseFlow * 0.01;
    double maxFlow = baseFlow * 10.0;

    double[] flowRates = new double[numFlowPoints];
    // Use logarithmic spacing for flow rates
    double logMin = Math.log10(minFlow);
    double logMax = Math.log10(maxFlow);
    for (int i = 0; i < numFlowPoints; i++) {
      double logFlow = logMin + i * (logMax - logMin) / (numFlowPoints - 1);
      flowRates[i] = Math.pow(10, logFlow);
    }

    return generateTable(flowRates, thpValues, pressureUnit, flowRateUnit);
  }

  // ============ Getters and Setters ============

  /**
   * Gets the maximum velocity constraint.
   *
   * @return maximum velocity in m/s
   */
  public double getMaxVelocity() {
    return maxVelocity;
  }

  /**
   * Sets the maximum velocity constraint.
   *
   * @param maxVelocity maximum velocity in m/s
   */
  public void setMaxVelocity(double maxVelocity) {
    this.maxVelocity = maxVelocity;
    if (optimizer != null) {
      optimizer.setMaxVelocity(maxVelocity);
    }
  }

  /**
   * Checks if constraints are being checked.
   *
   * @return true if checking constraints
   */
  public boolean isCheckConstraints() {
    return checkConstraints;
  }

  /**
   * Sets whether to check constraints.
   *
   * @param checkConstraints true to check constraints
   */
  public void setCheckConstraints(boolean checkConstraints) {
    this.checkConstraints = checkConstraints;
    if (optimizer != null) {
      optimizer.setCheckCapacityConstraints(checkConstraints);
    }
  }

  /**
   * Gets the table name for generated tables.
   *
   * @return table name
   */
  public String getTableName() {
    return tableName;
  }

  /**
   * Sets the table name for generated tables.
   *
   * @param tableName table name
   */
  public void setTableName(String tableName) {
    this.tableName = tableName;
  }

  /**
   * Gets the underlying optimizer.
   *
   * @return the optimizer
   */
  public FlowRateOptimizer getOptimizer() {
    return optimizer;
  }

  /**
   * Sets optimizer parameters.
   *
   * @param maxIterations maximum iterations
   * @param tolerance convergence tolerance
   */
  public void setOptimizerParameters(int maxIterations, double tolerance) {
    optimizer.setMaxIterations(maxIterations);
    optimizer.setTolerance(tolerance);
  }

  /**
   * Sets flow rate limits for the optimizer.
   *
   * @param minFlowRate minimum flow rate in kg/hr
   * @param maxFlowRate maximum flow rate in kg/hr
   */
  public void setFlowRateLimits(double minFlowRate, double maxFlowRate) {
    optimizer.setMinFlowRate(minFlowRate);
    optimizer.setMaxFlowRate(maxFlowRate);
  }
}
