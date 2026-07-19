package neqsim.process.lng;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import com.google.gson.GsonBuilder;
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

  /** Hours per year used for nameplate MTPA conversion. */
  private static final double OPERATING_HOURS_PER_YEAR = 8000.0;

  private final String name;
  private final LNGProcessCycle cycle;
  private final ProcessSystem processSystem;
  private final StreamInterface feedStream;
  private final StreamInterface productStream;
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
   * @param compressors compressor list
   * @param expanders expander list
   * @param cryogenicHeatExchangers cryogenic exchanger list
   */
  LNGProcessModel(String name, LNGProcessCycle cycle, ProcessSystem processSystem, StreamInterface feedStream,
      StreamInterface productStream, List<Compressor> compressors, List<Expander> expanders,
      List<LNGHeatExchanger> cryogenicHeatExchangers) {
    this.name = name;
    this.cycle = cycle;
    this.processSystem = processSystem;
    this.feedStream = feedStream;
    this.productStream = productStream;
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

  /** @return natural-gas feed stream */
  public StreamInterface getFeedStream() {
    return feedStream;
  }

  /** @return flashed liquid LNG product stream */
  public StreamInterface getProductStream() {
    return productStream;
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
