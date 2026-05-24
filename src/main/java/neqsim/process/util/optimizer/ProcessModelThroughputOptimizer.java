package neqsim.process.util.optimizer;

import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.ToDoubleFunction;
import neqsim.process.processmodel.ProcessModel;

/**
 * Convenience optimizer for full {@link ProcessModel} throughput-to-bottleneck studies.
 *
 * <p>
 * This class wraps {@link ProcessModelSimulationEvaluator} for the common engineering workflow: map
 * producer/feed controls, load installed equipment capacities, scale production, and return a case
 * table showing the active bottleneck at each iteration.
 * </p>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class ProcessModelThroughputOptimizer implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Process model under study. */
  private final ProcessModel processModel;

  /** Producer controls scaled by the optimizer. */
  private final List<ProducerControl> producerControls = new ArrayList<ProducerControl>();

  /** Objective name. */
  private String objectiveName = "objective";

  /** Objective unit. */
  private String objectiveUnit = "";

  /** Objective evaluator. */
  private transient ToDoubleFunction<ProcessModel> objectiveEvaluator;

  /** Maximum binary-search iterations. */
  private int maxIterations = 60;

  /** Whether to add equipment capacity constraints automatically. */
  private boolean includeEquipmentCapacityConstraints = true;

  /** Whether to include strategy-generated constraints in addition to installed direct limits. */
  private boolean includeStrategyCapacityConstraints = false;

  /**
   * Producer or feed control definition.
   *
   * @author NeqSim Development Team
   * @version 1.0
   */
  public static class ProducerControl implements Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1000L;

    /** Control name. */
    private String name;

    /** Area-qualified automation address. */
    private String address;

    /** Unit used when reading or setting address values. */
    private String unit;

    /** Base value multiplied in scalar throughput studies. */
    private double baseValue = Double.NaN;

    /** Lower allowed multiplier. */
    private double lowerMultiplier;

    /** Upper allowed multiplier. */
    private double upperMultiplier;

    /** Optional custom multiplier setter. */
    private transient BiConsumer<ProcessModel, Double> multiplierSetter;

    /** Default constructor for serialization frameworks. */
    public ProducerControl() {}

    /**
     * Creates an automation-addressed producer control.
     *
     * @param name control name
     * @param address area-qualified automation address
     * @param baseValue base value before multiplier scaling
     * @param lowerMultiplier lower allowed multiplier
     * @param upperMultiplier upper allowed multiplier
     * @param unit unit used when reading and setting the value
     */
    public ProducerControl(String name, String address, double baseValue, double lowerMultiplier,
        double upperMultiplier, String unit) {
      this.name = name;
      this.address = address;
      this.baseValue = baseValue;
      this.lowerMultiplier = lowerMultiplier;
      this.upperMultiplier = upperMultiplier;
      this.unit = unit;
    }

    /**
     * Creates a custom producer multiplier control.
     *
     * @param name control name
     * @param lowerMultiplier lower allowed multiplier
     * @param upperMultiplier upper allowed multiplier
     * @param multiplierSetter custom multiplier setter
     */
    public ProducerControl(String name, double lowerMultiplier, double upperMultiplier,
        BiConsumer<ProcessModel, Double> multiplierSetter) {
      this.name = name;
      this.address = name;
      this.unit = "-";
      this.lowerMultiplier = lowerMultiplier;
      this.upperMultiplier = upperMultiplier;
      this.multiplierSetter = multiplierSetter;
    }

    /**
     * Gets the control name.
     *
     * @return control name
     */
    public String getName() {
      return name;
    }

    /**
     * Gets the automation address.
     *
     * @return automation address
     */
    public String getAddress() {
      return address;
    }

    /**
     * Gets the unit.
     *
     * @return unit
     */
    public String getUnit() {
      return unit;
    }

    /**
     * Gets the base value.
     *
     * @return base value
     */
    public double getBaseValue() {
      return baseValue;
    }

    /**
     * Gets the lower multiplier.
     *
     * @return lower multiplier
     */
    public double getLowerMultiplier() {
      return lowerMultiplier;
    }

    /**
     * Gets the upper multiplier.
     *
     * @return upper multiplier
     */
    public double getUpperMultiplier() {
      return upperMultiplier;
    }

    /**
     * Checks whether this control uses a custom setter.
     *
     * @return true when a custom setter is configured
     */
    public boolean hasCustomSetter() {
      return multiplierSetter != null;
    }

    /**
     * Resolves the base value from the process model when needed.
     *
     * @param model process model
     */
    private void resolveBaseValue(ProcessModel model) {
      if (!hasCustomSetter() && Double.isNaN(baseValue)) {
        baseValue = model.getVariableValue(address, unit);
      }
    }

    /**
     * Applies a multiplier to the target control.
     *
     * @param model process model
     * @param multiplier multiplier to apply
     */
    private void applyMultiplier(ProcessModel model, double multiplier) {
      if (hasCustomSetter()) {
        multiplierSetter.accept(model, Double.valueOf(multiplier));
      } else {
        model.setVariableValue(address, baseValue * multiplier, unit);
      }
    }
  }

  /**
   * Creates a throughput optimizer for a process model.
   *
   * @param processModel process model
   */
  public ProcessModelThroughputOptimizer(ProcessModel processModel) {
    if (processModel == null) {
      throw new IllegalArgumentException("ProcessModel cannot be null");
    }
    this.processModel = processModel;
  }

  /**
   * Adds a producer/feed control using its current model value as base value.
   *
   * @param name control name
   * @param address area-qualified automation address
   * @param lowerMultiplier lower allowed multiplier
   * @param upperMultiplier upper allowed multiplier
   * @param unit unit used when reading and setting the value
   * @return this optimizer for chaining
   */
  public ProcessModelThroughputOptimizer addProducer(String name, String address,
      double lowerMultiplier, double upperMultiplier, String unit) {
    return addProducer(name, address, Double.NaN, lowerMultiplier, upperMultiplier, unit);
  }

  /**
   * Adds a producer/feed control with an explicit base value.
   *
   * @param name control name
   * @param address area-qualified automation address
   * @param baseValue base value before multiplier scaling
   * @param lowerMultiplier lower allowed multiplier
   * @param upperMultiplier upper allowed multiplier
   * @param unit unit used when setting the value
   * @return this optimizer for chaining
   */
  public ProcessModelThroughputOptimizer addProducer(String name, String address, double baseValue,
      double lowerMultiplier, double upperMultiplier, String unit) {
    producerControls
        .add(new ProducerControl(name, address, baseValue, lowerMultiplier, upperMultiplier, unit));
    return this;
  }

  /**
   * Adds a custom producer multiplier setter.
   *
   * @param name control name
   * @param lowerMultiplier lower allowed multiplier
   * @param upperMultiplier upper allowed multiplier
   * @param multiplierSetter custom setter receiving the model and multiplier
   * @return this optimizer for chaining
   */
  public ProcessModelThroughputOptimizer addProducerMultiplier(String name, double lowerMultiplier,
      double upperMultiplier, BiConsumer<ProcessModel, Double> multiplierSetter) {
    producerControls
        .add(new ProducerControl(name, lowerMultiplier, upperMultiplier, multiplierSetter));
    return this;
  }

  /**
   * Sets the throughput objective.
   *
   * @param name objective name
   * @param evaluator objective evaluator
   * @param unit objective unit
   * @return this optimizer for chaining
   */
  public ProcessModelThroughputOptimizer setObjective(String name,
      ToDoubleFunction<ProcessModel> evaluator, String unit) {
    this.objectiveName = name;
    this.objectiveEvaluator = evaluator;
    this.objectiveUnit = unit;
    return this;
  }

  /**
   * Loads installed capacity constraints from a CSV file.
   *
   * @param filePath CSV file path
   * @return loaded capacity records
   * @throws IOException if reading fails
   */
  public List<InstalledCapacityTableLoader.InstalledCapacityRecord> loadInstalledCapacities(
      String filePath) throws IOException {
    return loadInstalledCapacities(Paths.get(filePath));
  }

  /**
   * Loads installed capacity constraints from a CSV file.
   *
   * @param filePath CSV file path
   * @return loaded capacity records
   * @throws IOException if reading fails
   */
  public List<InstalledCapacityTableLoader.InstalledCapacityRecord> loadInstalledCapacities(
      Path filePath) throws IOException {
    return InstalledCapacityTableLoader.load(processModel, filePath);
  }

  /**
   * Finds the maximum feasible scalar throughput multiplier.
   *
   * @param lowerMultiplier lower search multiplier
   * @param upperMultiplier upper search multiplier
   * @param tolerance multiplier tolerance for binary search
   * @return throughput optimization result with all evaluated cases
   */
  public ProcessModelThroughputResult findMaximumThroughput(double lowerMultiplier,
      double upperMultiplier, double tolerance) {
    validateSetup(tolerance);
    resolveBaseValues();

    double lower = Math.max(lowerMultiplier, getMaximumProducerLowerMultiplier());
    double upper = Math.min(upperMultiplier, getMinimumProducerUpperMultiplier());
    if (upper < lower) {
      throw new IllegalArgumentException("No overlap between requested and producer bounds");
    }

    ProcessModelSimulationEvaluator evaluator = createScalarEvaluator(lower, upper);
    ProcessModelThroughputResult result = new ProcessModelThroughputResult(
        "ProcessModel throughput-to-bottleneck", objectiveName, objectiveUnit);

    int caseNumber = 1;
    ThroughputCaseRow lowerCase = evaluateCase(evaluator, caseNumber++, lower);
    result.addCase(lowerCase);
    result.setLowerBoundFeasible(lowerCase.isFeasible());
    if (!lowerCase.isFeasible()) {
      return result;
    }

    ThroughputCaseRow upperCase = evaluateCase(evaluator, caseNumber++, upper);
    result.addCase(upperCase);
    result.setUpperBoundFeasible(upperCase.isFeasible());
    if (upperCase.isFeasible()) {
      return result;
    }

    double feasibleMultiplier = lower;
    double infeasibleMultiplier = upper;
    int iteration = 0;
    while (Math.abs(infeasibleMultiplier - feasibleMultiplier) > tolerance
        && iteration < maxIterations) {
      double trialMultiplier = 0.5 * (feasibleMultiplier + infeasibleMultiplier);
      ThroughputCaseRow trialCase = evaluateCase(evaluator, caseNumber++, trialMultiplier);
      result.addCase(trialCase);
      if (trialCase.isFeasible()) {
        feasibleMultiplier = trialMultiplier;
      } else {
        infeasibleMultiplier = trialMultiplier;
      }
      iteration++;
    }
    return result;
  }

  /**
   * Gets producer controls.
   *
   * @return copy of producer controls
   */
  public List<ProducerControl> getProducerControls() {
    return new ArrayList<ProducerControl>(producerControls);
  }

  /**
   * Gets the maximum iteration count.
   *
   * @return maximum iteration count
   */
  public int getMaxIterations() {
    return maxIterations;
  }

  /**
   * Sets the maximum iteration count.
   *
   * @param maxIterations maximum iteration count
   * @return this optimizer for chaining
   */
  public ProcessModelThroughputOptimizer setMaxIterations(int maxIterations) {
    this.maxIterations = maxIterations;
    return this;
  }

  /**
   * Checks whether equipment capacity constraints are automatically added.
   *
   * @return true when enabled
   */
  public boolean isIncludeEquipmentCapacityConstraints() {
    return includeEquipmentCapacityConstraints;
  }

  /**
   * Sets whether equipment capacity constraints are automatically added.
   *
   * @param includeEquipmentCapacityConstraints true to include equipment capacity constraints
   * @return this optimizer for chaining
   */
  public ProcessModelThroughputOptimizer setIncludeEquipmentCapacityConstraints(
      boolean includeEquipmentCapacityConstraints) {
    this.includeEquipmentCapacityConstraints = includeEquipmentCapacityConstraints;
    return this;
  }

  /**
   * Checks whether strategy-generated capacity constraints are included.
   *
   * @return true when generic strategy constraints are included with installed direct limits
   */
  public boolean isIncludeStrategyCapacityConstraints() {
    return includeStrategyCapacityConstraints;
  }

  /**
   * Sets whether strategy-generated capacity constraints are included.
   *
   * <p>
   * The default is false so fixed-equipment throughput studies are controlled by explicit installed
   * limits loaded from design data or attached directly to equipment. Set this to true for
   * screening studies where generic equipment strategy constraints should also participate.
   * </p>
   *
   * @param includeStrategyCapacityConstraints true to include strategy-generated constraints
   * @return this optimizer for chaining
   */
  public ProcessModelThroughputOptimizer setIncludeStrategyCapacityConstraints(
      boolean includeStrategyCapacityConstraints) {
    this.includeStrategyCapacityConstraints = includeStrategyCapacityConstraints;
    return this;
  }

  /**
   * Validates optimizer setup.
   *
   * @param tolerance multiplier tolerance
   */
  private void validateSetup(double tolerance) {
    if (producerControls.isEmpty()) {
      throw new IllegalStateException("At least one producer control must be configured");
    }
    if (objectiveEvaluator == null) {
      throw new IllegalStateException("An objective must be configured before optimization");
    }
    if (tolerance <= 0.0) {
      throw new IllegalArgumentException("Tolerance must be positive");
    }
  }

  /**
   * Resolves base values for automation-addressed controls.
   */
  private void resolveBaseValues() {
    for (ProducerControl producerControl : producerControls) {
      producerControl.resolveBaseValue(processModel);
    }
  }

  /**
   * Creates a scalar evaluator with one throughput multiplier variable.
   *
   * @param lower lower multiplier bound
   * @param upper upper multiplier bound
   * @return configured evaluator
   */
  private ProcessModelSimulationEvaluator createScalarEvaluator(double lower, double upper) {
    ProcessModelSimulationEvaluator evaluator = new ProcessModelSimulationEvaluator(processModel);
    evaluator.setIncludeStrategyCapacityConstraints(includeStrategyCapacityConstraints);
    evaluator.addParameterWithSetter("throughputMultiplier",
        new BiConsumer<ProcessModel, Double>() {
          /** {@inheritDoc} */
          @Override
          public void accept(ProcessModel model, Double multiplier) {
            applyScalarMultiplier(model, multiplier.doubleValue());
          }
        }, lower, upper, "-");
    evaluator.addObjective(objectiveName, objectiveEvaluator,
        ProcessModelSimulationEvaluator.ObjectiveDefinition.Direction.MAXIMIZE);
    if (includeEquipmentCapacityConstraints) {
      evaluator.addEquipmentCapacityConstraints();
    }
    return evaluator;
  }

  /**
   * Evaluates one multiplier case.
   *
   * @param evaluator configured evaluator
   * @param caseNumber case sequence number
   * @param multiplier throughput multiplier
   * @return evaluated case row
   */
  private ThroughputCaseRow evaluateCase(ProcessModelSimulationEvaluator evaluator, int caseNumber,
      double multiplier) {
    ProcessModelSimulationEvaluator.EvaluationResult evaluation =
        evaluator.evaluate(new double[] {multiplier});
    return ThroughputCaseRow.fromEvaluation(caseNumber, multiplier,
        createProducerMultiplierMap(multiplier), evaluation);
  }

  /**
   * Applies a scalar multiplier to all producer controls.
   *
   * @param model process model
   * @param multiplier scalar multiplier
   */
  private void applyScalarMultiplier(ProcessModel model, double multiplier) {
    for (ProducerControl producerControl : producerControls) {
      producerControl.applyMultiplier(model, multiplier);
    }
  }

  /**
   * Creates producer multiplier metadata for reporting.
   *
   * @param multiplier scalar multiplier
   * @return producer multiplier map
   */
  private Map<String, Double> createProducerMultiplierMap(double multiplier) {
    Map<String, Double> multipliers = new LinkedHashMap<String, Double>();
    for (ProducerControl producerControl : producerControls) {
      multipliers.put(producerControl.getName(), Double.valueOf(multiplier));
    }
    return multipliers;
  }

  /**
   * Gets the most restrictive lower multiplier across producer controls.
   *
   * @return maximum producer lower multiplier
   */
  private double getMaximumProducerLowerMultiplier() {
    double lower = -Double.MAX_VALUE;
    for (ProducerControl producerControl : producerControls) {
      lower = Math.max(lower, producerControl.getLowerMultiplier());
    }
    return lower;
  }

  /**
   * Gets the most restrictive upper multiplier across producer controls.
   *
   * @return minimum producer upper multiplier
   */
  private double getMinimumProducerUpperMultiplier() {
    double upper = Double.MAX_VALUE;
    for (ProducerControl producerControl : producerControls) {
      upper = Math.min(upper, producerControl.getUpperMultiplier());
    }
    return upper;
  }
}
