package neqsim.process.lng;

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
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.expander.Expander;
import neqsim.process.equipment.heatexchanger.LNGHeatExchanger;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.processmodel.ProcessSystem;

/**
 * Runnable LNG process template with consistent process-level performance metrics.
 *
 * <p>
 * The model wraps a {@link ProcessSystem} and the streams and rotating equipment needed to evaluate liquefaction
 * performance. It deliberately keeps thermodynamic and equipment calculations in the standard NeqSim unit operations;
 * this class only coordinates execution and calculates route-comparison metrics on a common basis.
 * </p>
 *
 * @author NeqSim contributors
 * @version 1.0
 */
public final class LNGProcessModel implements Serializable {
  private static final long serialVersionUID = 1L;

  /** Name of the liquid LNG output in the output-stream registry. */
  public static final String LNG_OUTPUT = "LNG";

  /** Name of the product-flash gas output in the output-stream registry. */
  public static final String FLASH_GAS_OUTPUT = "FLASH_GAS";

  /** Hours per year used for nameplate MTPA conversion. */
  private static final double OPERATING_HOURS_PER_YEAR = 8000.0;

  private final String name;
  private final LNGProcessCycle cycle;
  private final ProcessSystem processSystem;
  private final StreamInterface feedStream;
  private final StreamInterface productStream;
  private final StreamInterface flashGasStream;
  private final Map<String, StreamInterface> outputStreams = new LinkedHashMap<String, StreamInterface>();
  private final List<Compressor> compressors;
  private final List<Expander> expanders;
  private final List<LNGHeatExchanger> cryogenicHeatExchangers;

  private transient Result lastResult;
  private transient long lastRunTimeNanos;

  /**
   * Creates an LNG process model.
   *
   * @param name model name
   * @param cycle liquefaction cycle
   * @param processSystem configured process system
   * @param feedStream natural-gas feed
   * @param productStream flashed liquid LNG product
   * @param flashGasStream product-flash gas
   * @param compressors compressor list
   * @param expanders expander list
   * @param cryogenicHeatExchangers cryogenic exchanger list
   */
  LNGProcessModel(String name, LNGProcessCycle cycle, ProcessSystem processSystem, StreamInterface feedStream,
      StreamInterface productStream, StreamInterface flashGasStream, List<Compressor> compressors,
      List<Expander> expanders, List<LNGHeatExchanger> cryogenicHeatExchangers) {
    this.name = name;
    this.cycle = cycle;
    this.processSystem = processSystem;
    this.feedStream = feedStream;
    this.productStream = productStream;
    this.flashGasStream = flashGasStream;
    this.outputStreams.put(LNG_OUTPUT, productStream);
    this.outputStreams.put(FLASH_GAS_OUTPUT, flashGasStream);
    this.compressors = Collections.unmodifiableList(new ArrayList<Compressor>(compressors));
    this.expanders = Collections.unmodifiableList(new ArrayList<Expander>(expanders));
    this.cryogenicHeatExchangers = Collections
        .unmodifiableList(new ArrayList<LNGHeatExchanger>(cryogenicHeatExchangers));
  }

  /**
   * Runs the complete process and evaluates route-level performance.
   *
   * @return process result
   */
  public Result run() {
    long start = System.nanoTime();
    processSystem.run();
    lastRunTimeNanos = System.nanoTime() - start;
    lastResult = evaluate();
    return lastResult;
  }

  /**
   * Evaluates the current process state without rerunning equipment.
   *
   * @return process result
   */
  public Result evaluate() {
    double feedKgPerHour = feedStream.getFlowRate("kg/hr");
    double lngKgPerHour = productStream.getFlowRate("kg/hr");

    double compressorPowerKW = 0.0;
    for (Compressor compressor : compressors) {
      compressorPowerKW += Math.abs(compressor.getPower("kW"));
    }

    double expanderPowerKW = 0.0;
    for (Expander expander : expanders) {
      expanderPowerKW += Math.abs(expander.getPower("kW"));
    }

    double netPowerKW = Math.max(0.0, compressorPowerKW - expanderPowerKW);
    double specificEnergy = lngKgPerHour > 1.0e-12 ? netPowerKW / lngKgPerHour : Double.NaN;
    double lngYield = feedKgPerHour > 1.0e-12 ? lngKgPerHour / feedKgPerHour : Double.NaN;
    double mtpa = lngKgPerHour * OPERATING_HOURS_PER_YEAR / 1.0e9;

    double mita = Double.POSITIVE_INFINITY;
    double exchangerExergyDestructionKW = 0.0;
    for (LNGHeatExchanger exchanger : cryogenicHeatExchangers) {
      double candidate = exchanger.getMITA();
      if (Double.isFinite(candidate) && candidate < mita) {
        mita = candidate;
      }
      double exergy = exchanger.getTotalExergyDestruction();
      if (Double.isFinite(exergy) && exergy > 0.0) {
        exchangerExergyDestructionKW += exergy;
      }
    }
    if (!Double.isFinite(mita)) {
      mita = Double.NaN;
    }

    return new Result(name, cycle, feedKgPerHour, lngKgPerHour, lngYield, mtpa, compressorPowerKW, expanderPowerKW,
        netPowerKW, specificEnergy, productStream.getTemperature("C"), productStream.getPressure("bara"),
        productStream.getFluid().getDensity("kg/m3"), mita, exchangerExergyDestructionKW, lastRunTimeNanos / 1.0e6);
  }

  /** @return model name */
  public String getName() {
    return name;
  }

  /** @return liquefaction cycle */
  public LNGProcessCycle getCycle() {
    return cycle;
  }

  /** @return configured process system */
  public ProcessSystem getProcessSystem() {
    return processSystem;
  }

  /**
   * Returns every explicit unit operation in flowsheet order.
   *
   * <p>
   * The list includes route equipment and any connected upstream or downstream equipment, such as pipelines, columns,
   * compressor trains, separators, exchangers, valves, expanders, and recycles.
   * </p>
   *
   * @return immutable unit-operation list
   */
  public List<ProcessEquipmentInterface> getEquipment() {
    return Collections.unmodifiableList(new ArrayList<ProcessEquipmentInterface>(processSystem.getUnitOperations()));
  }

  /**
   * Returns all unit operations assignable to a requested equipment type.
   *
   * @param <T> equipment type
   * @param equipmentType requested class or interface
   * @return immutable matching equipment list in flowsheet order
   */
  public <T extends ProcessEquipmentInterface> List<T> getEquipment(Class<T> equipmentType) {
    if (equipmentType == null) {
      throw new IllegalArgumentException("equipmentType cannot be null");
    }
    List<T> matches = new ArrayList<T>();
    for (ProcessEquipmentInterface equipment : processSystem.getUnitOperations()) {
      if (equipmentType.isInstance(equipment)) {
        matches.add(equipmentType.cast(equipment));
      }
    }
    return Collections.unmodifiableList(matches);
  }

  /** @return natural-gas feed stream */
  public StreamInterface getFeedStream() {
    return feedStream;
  }

  /** @return flashed liquid LNG product stream */
  public StreamInterface getProductStream() {
    return productStream;
  }

  /** @return product-flash gas for fuel, boil-off, or recompression processing */
  public StreamInterface getFlashGasStream() {
    return flashGasStream;
  }

  /**
   * Returns a named process output stream.
   *
   * <p>
   * Built-in outputs are {@link #LNG_OUTPUT} and {@link #FLASH_GAS_OUTPUT}. Additional route-specific or downstream
   * products can be exposed with {@link #registerOutputStream(String, StreamInterface)}.
   * </p>
   *
   * @param outputName output name
   * @return output stream, or null when the name is not registered
   */
  public StreamInterface getOutputStream(String outputName) {
    return outputStreams.get(outputName);
  }

  /**
   * Returns all named process output streams.
   *
   * @return immutable output-name to stream map
   */
  public Map<String, StreamInterface> getOutputStreams() {
    return Collections.unmodifiableMap(outputStreams);
  }

  /**
   * Registers an additional process output for downstream connection.
   *
   * @param outputName unique output name
   * @param outputStream output stream
   * @return this model
   */
  public LNGProcessModel registerOutputStream(String outputName, StreamInterface outputStream) {
    if (outputName == null || outputName.trim().isEmpty()) {
      throw new IllegalArgumentException("outputName cannot be null or empty");
    }
    if (outputStream == null) {
      throw new IllegalArgumentException("outputStream cannot be null");
    }
    outputStreams.put(outputName, outputStream);
    return this;
  }

  /** @return immutable compressor list */
  public List<Compressor> getCompressors() {
    return compressors;
  }

  /** @return immutable expander list */
  public List<Expander> getExpanders() {
    return expanders;
  }

  /** @return immutable cryogenic exchanger list */
  public List<LNGHeatExchanger> getCryogenicHeatExchangers() {
    return cryogenicHeatExchangers;
  }

  /**
   * Appends downstream equipment to the integrated process system.
   *
   * <p>
   * This is useful for product pipelines, storage, loading, boil-off handling, and other equipment that should
   * participate in the same execution and capacity analysis as the LNG train. For upstream integration, use
   * {@link LNGProcessBuilder#setUpstreamProcess(ProcessSystem, StreamInterface)}. Measurement devices, controllers, and
   * other process elements can be added through {@link #getProcessSystem()} using the standard NeqSim APIs.
   * </p>
   *
   * @param equipment equipment connected to an existing model stream
   * @return this model
   */
  public LNGProcessModel addEquipment(ProcessEquipmentInterface equipment) {
    if (equipment == null) {
      throw new IllegalArgumentException("equipment cannot be null");
    }
    processSystem.add(equipment);
    return this;
  }

  /**
   * Evaluates process-wide equipment and pipeline capacity at the current converged state.
   *
   * <p>
   * The result delegates to the universal NeqSim capacity-constraint framework and therefore includes every registered
   * unit with enabled constraints. Call {@link #run()} first, and configure equipment design limits or apply
   * mechanical-design constraints before using this read-only evaluation.
   * </p>
   *
   * @return ranked capacity result for the complete integrated process
   */
  public CapacityResult evaluateCapacity() {
    return evaluateCapacity(0, 0);
  }

  /**
   * Runs, auto-sizes, activates mechanical-design constraints, reruns, and evaluates capacity.
   *
   * <p>
   * The workflow includes upstream equipment supplied through
   * {@link LNGProcessBuilder#setUpstreamProcess(ProcessSystem, StreamInterface)} and downstream equipment appended with
   * {@link #addEquipment(ProcessEquipmentInterface)}. Consequently, detailed pipe velocity, pressure-drop, volume-flow,
   * compressor, exchanger, separator, valve, and other available NeqSim constraints are ranked together.
   * </p>
   *
   * @param safetyFactor design-capacity multiplier, normally 1.1 to 1.3
   * @return ranked capacity result for the complete integrated process
   */
  public CapacityResult autoSizeAndEvaluateCapacity(double safetyFactor) {
    if (!Double.isFinite(safetyFactor) || safetyFactor <= 1.0) {
      throw new IllegalArgumentException("safetyFactor must be finite and greater than 1.0");
    }
    run();
    int sizedEquipment = processSystem.autoSizeEquipment(safetyFactor);
    int derivedConstraints = processSystem.applyMechanicalDesignCapacityConstraints();
    run();
    return evaluateCapacity(sizedEquipment, derivedConstraints);
  }

  /**
   * Builds a capacity result with workflow counters.
   *
   * @param sizedEquipment number of auto-sized units
   * @param derivedConstraints number of activated mechanical-design constraints
   * @return capacity result
   */
  private CapacityResult evaluateCapacity(int sizedEquipment, int derivedConstraints) {
    Map<String, Double> utilization = processSystem.getCapacityUtilizationSummary();
    List<Map.Entry<String, Double>> ranked = new ArrayList<Map.Entry<String, Double>>(utilization.entrySet());
    Collections.sort(ranked, new Comparator<Map.Entry<String, Double>>() {
      @Override
      public int compare(Map.Entry<String, Double> first, Map.Entry<String, Double> second) {
        return Double.compare(second.getValue(), first.getValue());
      }
    });
    Map<String, Double> rankedUtilization = new LinkedHashMap<String, Double>();
    for (Map.Entry<String, Double> entry : ranked) {
      rankedUtilization.put(entry.getKey(), entry.getValue());
    }

    return new CapacityResult(processSystem.findBottleneck(), rankedUtilization,
        processSystem.getEquipmentNearCapacityLimit(), processSystem.isAnyEquipmentOverloaded(),
        processSystem.isAnyHardLimitExceeded(), sizedEquipment, derivedConstraints,
        processSystem.getUtilizationSnapshotJson(), processSystem.getDesignReportJson());
  }

  /** @return result from the most recent run, or null before the first run */
  public Result getLastResult() {
    return lastResult;
  }

  /**
   * Returns the most recent result as JSON.
   *
   * @return JSON result, or null before the first run
   */
  public String toJson() {
    if (lastResult == null) {
      return null;
    }
    return new GsonBuilder().serializeSpecialFloatingPointValues().create().toJson(lastResult);
  }

  /**
   * Immutable process-wide capacity result for an integrated LNG flowsheet.
   *
   * @author NeqSim contributors
   * @version 1.0
   */
  public static final class CapacityResult implements Serializable {
    private static final long serialVersionUID = 1L;

    private final BottleneckResult bottleneck;
    private final Map<String, Double> rankedUtilizationPercent;
    private final List<String> equipmentNearCapacityLimit;
    private final boolean anyEquipmentOverloaded;
    private final boolean anyHardLimitExceeded;
    private final int autoSizedEquipmentCount;
    private final int derivedConstraintCount;
    private final String utilizationSnapshotJson;
    private final String designReportJson;

    /**
     * Creates a process-wide capacity result.
     *
     * @param bottleneck most limiting equipment constraint
     * @param rankedUtilizationPercent utilization by equipment, highest first
     * @param equipmentNearCapacityLimit equipment above its warning threshold
     * @param anyEquipmentOverloaded true when a design capacity is exceeded
     * @param anyHardLimitExceeded true when an absolute equipment limit is exceeded
     * @param autoSizedEquipmentCount number of units auto-sized by the workflow
     * @param derivedConstraintCount number of activated mechanical-design constraints
     * @param utilizationSnapshotJson complete machine-readable utilization snapshot
     * @param designReportJson complete auto-sizing and design report
     */
    private CapacityResult(BottleneckResult bottleneck, Map<String, Double> rankedUtilizationPercent,
        List<String> equipmentNearCapacityLimit, boolean anyEquipmentOverloaded, boolean anyHardLimitExceeded,
        int autoSizedEquipmentCount, int derivedConstraintCount, String utilizationSnapshotJson,
        String designReportJson) {
      this.bottleneck = bottleneck;
      this.rankedUtilizationPercent = Collections
          .unmodifiableMap(new LinkedHashMap<String, Double>(rankedUtilizationPercent));
      this.equipmentNearCapacityLimit = Collections.unmodifiableList(new ArrayList<String>(equipmentNearCapacityLimit));
      this.anyEquipmentOverloaded = anyEquipmentOverloaded;
      this.anyHardLimitExceeded = anyHardLimitExceeded;
      this.autoSizedEquipmentCount = autoSizedEquipmentCount;
      this.derivedConstraintCount = derivedConstraintCount;
      this.utilizationSnapshotJson = utilizationSnapshotJson;
      this.designReportJson = designReportJson;
    }

    /** @return most limiting equipment constraint, or an empty result */
    public BottleneckResult getBottleneck() {
      return bottleneck;
    }

    /** @return equipment utilization percentages ordered from highest to lowest */
    public Map<String, Double> getRankedUtilizationPercent() {
      return rankedUtilizationPercent;
    }

    /** @return immutable list of equipment above its warning threshold */
    public List<String> getEquipmentNearCapacityLimit() {
      return equipmentNearCapacityLimit;
    }

    /** @return true when any registered equipment exceeds design capacity */
    public boolean isAnyEquipmentOverloaded() {
      return anyEquipmentOverloaded;
    }

    /** @return true when any absolute equipment limit is exceeded */
    public boolean isAnyHardLimitExceeded() {
      return anyHardLimitExceeded;
    }

    /** @return number of units auto-sized by the capacity workflow */
    public int getAutoSizedEquipmentCount() {
      return autoSizedEquipmentCount;
    }

    /** @return number of activated mechanical-design constraints */
    public int getDerivedConstraintCount() {
      return derivedConstraintCount;
    }

    /** @return complete process utilization snapshot JSON */
    public String getUtilizationSnapshotJson() {
      return utilizationSnapshotJson;
    }

    /** @return complete auto-sizing and design report JSON */
    public String getDesignReportJson() {
      return designReportJson;
    }
  }

  /**
   * Immutable route-level LNG process result.
   *
   * @author NeqSim contributors
   * @version 1.0
   */
  public static final class Result implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String name;
    private final LNGProcessCycle cycle;
    private final double feedMassFlowKgPerHour;
    private final double lngMassFlowKgPerHour;
    private final double lngYield;
    private final double capacityMTPA;
    private final double compressorPowerKW;
    private final double expanderRecoveredPowerKW;
    private final double netPowerKW;
    private final double specificEnergyKWhPerKgLNG;
    private final double productTemperatureC;
    private final double productPressureBara;
    private final double productDensityKgPerM3;
    private final double minimumInternalTemperatureApproachC;
    private final double exchangerExergyDestructionKW;
    private final double runTimeMilliseconds;

    /**
     * Creates an evaluated process result.
     *
     * @param name model name
     * @param cycle process cycle
     * @param feedMassFlowKgPerHour feed rate in kg/h
     * @param lngMassFlowKgPerHour liquid LNG product rate in kg/h
     * @param lngYield liquid mass yield
     * @param capacityMTPA capacity at 8000 h/y in MTPA
     * @param compressorPowerKW total compressor power in kW
     * @param expanderRecoveredPowerKW recovered expander power in kW
     * @param netPowerKW net shaft power in kW
     * @param specificEnergyKWhPerKgLNG net specific energy in kWh/kg LNG
     * @param productTemperatureC product temperature in Celsius
     * @param productPressureBara product pressure in bara
     * @param productDensityKgPerM3 product density in kg/m3
     * @param minimumInternalTemperatureApproachC minimum exchanger approach in Celsius
     * @param exchangerExergyDestructionKW exchanger exergy destruction in kW
     * @param runTimeMilliseconds process execution time in milliseconds
     */
    Result(String name, LNGProcessCycle cycle, double feedMassFlowKgPerHour, double lngMassFlowKgPerHour,
        double lngYield, double capacityMTPA, double compressorPowerKW, double expanderRecoveredPowerKW,
        double netPowerKW, double specificEnergyKWhPerKgLNG, double productTemperatureC, double productPressureBara,
        double productDensityKgPerM3, double minimumInternalTemperatureApproachC, double exchangerExergyDestructionKW,
        double runTimeMilliseconds) {
      this.name = name;
      this.cycle = cycle;
      this.feedMassFlowKgPerHour = feedMassFlowKgPerHour;
      this.lngMassFlowKgPerHour = lngMassFlowKgPerHour;
      this.lngYield = lngYield;
      this.capacityMTPA = capacityMTPA;
      this.compressorPowerKW = compressorPowerKW;
      this.expanderRecoveredPowerKW = expanderRecoveredPowerKW;
      this.netPowerKW = netPowerKW;
      this.specificEnergyKWhPerKgLNG = specificEnergyKWhPerKgLNG;
      this.productTemperatureC = productTemperatureC;
      this.productPressureBara = productPressureBara;
      this.productDensityKgPerM3 = productDensityKgPerM3;
      this.minimumInternalTemperatureApproachC = minimumInternalTemperatureApproachC;
      this.exchangerExergyDestructionKW = exchangerExergyDestructionKW;
      this.runTimeMilliseconds = runTimeMilliseconds;
    }

    /** @return model name */
    public String getName() {
      return name;
    }

    /** @return process cycle */
    public LNGProcessCycle getCycle() {
      return cycle;
    }

    /** @return feed mass flow in kg/h */
    public double getFeedMassFlowKgPerHour() {
      return feedMassFlowKgPerHour;
    }

    /** @return liquid LNG mass flow in kg/h */
    public double getLNGMassFlowKgPerHour() {
      return lngMassFlowKgPerHour;
    }

    /** @return LNG liquid mass yield */
    public double getLNGYield() {
      return lngYield;
    }

    /** @return nameplate capacity in MTPA at 8000 operating hours per year */
    public double getCapacityMTPA() {
      return capacityMTPA;
    }

    /** @return total compressor power in kW */
    public double getCompressorPowerKW() {
      return compressorPowerKW;
    }

    /** @return recovered expander power in kW */
    public double getExpanderRecoveredPowerKW() {
      return expanderRecoveredPowerKW;
    }

    /** @return net shaft power in kW */
    public double getNetPowerKW() {
      return netPowerKW;
    }

    /** @return net specific energy in kWh/kg LNG */
    public double getSpecificEnergyKWhPerKgLNG() {
      return specificEnergyKWhPerKgLNG;
    }

    /** @return product temperature in Celsius */
    public double getProductTemperatureC() {
      return productTemperatureC;
    }

    /** @return product pressure in bara */
    public double getProductPressureBara() {
      return productPressureBara;
    }

    /** @return product density in kg/m3 */
    public double getProductDensityKgPerM3() {
      return productDensityKgPerM3;
    }

    /** @return minimum internal temperature approach in Celsius */
    public double getMinimumInternalTemperatureApproachC() {
      return minimumInternalTemperatureApproachC;
    }

    /** @return summed cryogenic-exchanger exergy destruction in kW */
    public double getExchangerExergyDestructionKW() {
      return exchangerExergyDestructionKW;
    }

    /** @return process execution time in milliseconds */
    public double getRunTimeMilliseconds() {
      return runTimeMilliseconds;
    }

    /**
     * Compares this result with the configured literature envelope.
     *
     * @return benchmark assessment
     */
    public LNGProcessBenchmark.Assessment assessBenchmark() {
      return LNGProcessBenchmark.assess(cycle, this);
    }
  }
}
