package neqsim.process.safety.leakdetection;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.pipeline.PipeLineInterface;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;

/**
 * Mass-balance leak-detection sensitivity model for process systems and pipelines.
 *
 * <p>
 * The detector estimates the minimum detectable leak rate from inlet/outlet flow uncertainty plus
 * optional linepack uncertainty caused by pressure and temperature measurement uncertainty. It is a
 * screening model for deciding whether instrumented mass balance can meet a required leak
 * sensitivity before detailed dynamic leak detection is configured.
 * </p>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class MassBalanceLeakDetector implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Inlet measurement stream. */
  private final StreamInterface inletStream;
  /** Outlet measurement stream. */
  private final StreamInterface outletStream;
  /** Linepack volume in m3. */
  private double linepackVolumeM3 = 0.0;
  /** Relative flow meter uncertainty as fraction of reading. */
  private double flowMeasurementUncertaintyFraction = 0.01;
  /** Pressure uncertainty in bara. */
  private double pressureUncertaintyBara = 0.0;
  /** Temperature uncertainty in K. */
  private double temperatureUncertaintyK = 0.0;
  /** Persistence or averaging window in seconds. */
  private double detectionWindowS = 60.0;
  /** Confidence multiplier, normally 2 or 3 sigma. */
  private double confidenceMultiplier = 3.0;

  /**
   * Creates a detector between two stream measurements.
   *
   * @param inletStream inlet measurement stream
   * @param outletStream outlet measurement stream
   * @throws IllegalArgumentException if either stream is null
   */
  public MassBalanceLeakDetector(StreamInterface inletStream, StreamInterface outletStream) {
    if (inletStream == null || outletStream == null) {
      throw new IllegalArgumentException("inletStream and outletStream must not be null");
    }
    this.inletStream = inletStream;
    this.outletStream = outletStream;
  }

  /**
   * Creates a detector using the first and last pipeline in a process system.
   *
   * @param process process system containing at least one pipeline
   * @throws IllegalArgumentException if no pipeline with inlet and outlet streams is found
   */
  public MassBalanceLeakDetector(ProcessSystem process) {
    this(getFirstPipeline(process).getInletStream(), getLastPipeline(process).getOutletStream());
  }

  /**
   * Creates a detector for one pipeline.
   *
   * @param pipe pipeline with inlet and outlet streams
   * @return detector configured with the pipe inlet and outlet streams
   * @throws IllegalArgumentException if {@code pipe} is null
   */
  public static MassBalanceLeakDetector fromPipe(PipeLineInterface pipe) {
    if (pipe == null) {
      throw new IllegalArgumentException("pipe must not be null");
    }
    MassBalanceLeakDetector detector = new MassBalanceLeakDetector(pipe.getInletStream(),
        pipe.getOutletStream());
    double diameterM = pipe.getDiameter();
    double lengthM = pipe.getLength();
    if (diameterM > 0.0 && lengthM > 0.0) {
      detector.setLinepackVolumeM3(Math.PI * Math.pow(diameterM / 2.0, 2.0) * lengthM);
    }
    return detector;
  }

  /**
   * Sets the linepack volume included in uncertainty calculations.
   *
   * @param linepackVolumeM3 linepack volume in m3
   * @return this detector for chaining
   */
  public MassBalanceLeakDetector setLinepackVolumeM3(double linepackVolumeM3) {
    this.linepackVolumeM3 = Math.max(0.0, linepackVolumeM3);
    return this;
  }

  /**
   * Sets relative flow measurement uncertainty.
   *
   * @param uncertaintyFraction relative uncertainty as fraction of reading
   * @return this detector for chaining
   */
  public MassBalanceLeakDetector setFlowMeasurementUncertaintyFraction(
      double uncertaintyFraction) {
    this.flowMeasurementUncertaintyFraction = Math.max(0.0, uncertaintyFraction);
    return this;
  }

  /**
   * Sets pressure measurement uncertainty.
   *
   * @param pressureUncertaintyBara pressure uncertainty in bara
   * @return this detector for chaining
   */
  public MassBalanceLeakDetector setPressureUncertaintyBara(double pressureUncertaintyBara) {
    this.pressureUncertaintyBara = Math.max(0.0, pressureUncertaintyBara);
    return this;
  }

  /**
   * Sets temperature measurement uncertainty.
   *
   * @param temperatureUncertaintyK temperature uncertainty in K
   * @return this detector for chaining
   */
  public MassBalanceLeakDetector setTemperatureUncertaintyK(double temperatureUncertaintyK) {
    this.temperatureUncertaintyK = Math.max(0.0, temperatureUncertaintyK);
    return this;
  }

  /**
   * Sets the detection averaging or persistence window.
   *
   * @param detectionWindowS detection window in s
   * @return this detector for chaining
   */
  public MassBalanceLeakDetector setDetectionWindowS(double detectionWindowS) {
    this.detectionWindowS = Math.max(1.0, detectionWindowS);
    return this;
  }

  /**
   * Sets the statistical confidence multiplier.
   *
   * @param confidenceMultiplier confidence multiplier, normally 2 or 3
   * @return this detector for chaining
   */
  public MassBalanceLeakDetector setConfidenceMultiplier(double confidenceMultiplier) {
    this.confidenceMultiplier = Math.max(0.0, confidenceMultiplier);
    return this;
  }

  /**
   * Calculates the minimum detectable leak rate.
   *
   * @return leak sensitivity result
   */
  public LeakDetectionSensitivityResult calculateSensitivity() {
    double inletFlowKgPerS = getFlowRateKgPerS(inletStream);
    double outletFlowKgPerS = getFlowRateKgPerS(outletStream);
    double measuredImbalanceKgPerS = inletFlowKgPerS - outletFlowKgPerS;
    double flowUncertaintyKgPerS = confidenceMultiplier * Math.sqrt(
        Math.pow(inletFlowKgPerS * flowMeasurementUncertaintyFraction, 2.0)
            + Math.pow(outletFlowKgPerS * flowMeasurementUncertaintyFraction, 2.0));
    double linepackUncertaintyKgPerS = calculateLinepackUncertaintyKgPerS();
    double combinedUncertaintyKgPerS = Math.sqrt(Math.pow(flowUncertaintyKgPerS, 2.0)
        + Math.pow(linepackUncertaintyKgPerS, 2.0));
    double minimumDetectableLeakRateKgPerS = Math.abs(measuredImbalanceKgPerS)
        + combinedUncertaintyKgPerS;
    double referenceFlowKgPerS = Math.max(Math.abs(inletFlowKgPerS), Math.abs(outletFlowKgPerS));
    double minimumDetectableLeakFraction = referenceFlowKgPerS > 0.0
        ? minimumDetectableLeakRateKgPerS / referenceFlowKgPerS : Double.NaN;

    return new LeakDetectionSensitivityResult(inletStream.getName(), outletStream.getName(),
        inletFlowKgPerS, outletFlowKgPerS, measuredImbalanceKgPerS, flowUncertaintyKgPerS,
        linepackUncertaintyKgPerS, combinedUncertaintyKgPerS, minimumDetectableLeakRateKgPerS,
        minimumDetectableLeakFraction, detectionWindowS, confidenceMultiplier);
  }

  /**
   * Finds the first pipeline in a process system.
   *
   * @param process process system
   * @return first pipeline
   * @throws IllegalArgumentException if no pipeline is found
   */
  private static PipeLineInterface getFirstPipeline(ProcessSystem process) {
    List<ProcessEquipmentInterface> units = validateProcessAndGetUnits(process);
    for (ProcessEquipmentInterface unit : units) {
      if (unit instanceof PipeLineInterface) {
        return (PipeLineInterface) unit;
      }
    }
    throw new IllegalArgumentException("process must contain at least one pipeline");
  }

  /**
   * Finds the last pipeline in a process system.
   *
   * @param process process system
   * @return last pipeline
   * @throws IllegalArgumentException if no pipeline is found
   */
  private static PipeLineInterface getLastPipeline(ProcessSystem process) {
    List<ProcessEquipmentInterface> units = validateProcessAndGetUnits(process);
    for (int index = units.size() - 1; index >= 0; index--) {
      ProcessEquipmentInterface unit = units.get(index);
      if (unit instanceof PipeLineInterface) {
        return (PipeLineInterface) unit;
      }
    }
    throw new IllegalArgumentException("process must contain at least one pipeline");
  }

  /**
   * Validates process input and returns its units.
   *
   * @param process process system
   * @return process unit operations
   * @throws IllegalArgumentException if process is null
   */
  private static List<ProcessEquipmentInterface> validateProcessAndGetUnits(ProcessSystem process) {
    if (process == null) {
      throw new IllegalArgumentException("process must not be null");
    }
    return process.getUnitOperations();
  }

  /**
   * Gets stream mass flow rate.
   *
   * @param stream stream to read
   * @return mass flow rate in kg/s
   */
  private double getFlowRateKgPerS(StreamInterface stream) {
    try {
      return stream.getFlowRate("kg/sec");
    } catch (RuntimeException ex) {
      return Double.NaN;
    }
  }

  /**
   * Calculates linepack uncertainty contribution as an equivalent leak rate.
   *
   * @return linepack uncertainty in kg/s
   */
  private double calculateLinepackUncertaintyKgPerS() {
    if (linepackVolumeM3 <= 0.0) {
      return 0.0;
    }
    SystemInterface fluid = inletStream.getThermoSystem();
    if (fluid == null) {
      return 0.0;
    }
    try {
      double densityKgPerM3 = fluid.getDensity("kg/m3");
      double pressureBara = Math.max(1.0e-9, inletStream.getPressure("bara"));
      double temperatureK = Math.max(1.0e-9, inletStream.getTemperature("K"));
      double relativePressureUncertainty = pressureUncertaintyBara / pressureBara;
      double relativeTemperatureUncertainty = temperatureUncertaintyK / temperatureK;
      double relativeLinepackUncertainty = Math.sqrt(Math.pow(relativePressureUncertainty, 2.0)
          + Math.pow(relativeTemperatureUncertainty, 2.0));
      double linepackMassKg = densityKgPerM3 * linepackVolumeM3;
      return confidenceMultiplier * linepackMassKg * relativeLinepackUncertainty
          / detectionWindowS;
    } catch (RuntimeException ex) {
      return 0.0;
    }
  }

  /** Result from a mass-balance leak sensitivity calculation. */
  public static class LeakDetectionSensitivityResult implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final String inletStreamName;
    private final String outletStreamName;
    private final double inletFlowKgPerS;
    private final double outletFlowKgPerS;
    private final double measuredImbalanceKgPerS;
    private final double flowUncertaintyKgPerS;
    private final double linepackUncertaintyKgPerS;
    private final double combinedUncertaintyKgPerS;
    private final double minimumDetectableLeakRateKgPerS;
    private final double minimumDetectableLeakFraction;
    private final double detectionWindowS;
    private final double confidenceMultiplier;

    /**
     * Creates a leak detection sensitivity result.
     *
     * @param inletStreamName inlet stream name
     * @param outletStreamName outlet stream name
     * @param inletFlowKgPerS inlet mass flow rate in kg/s
     * @param outletFlowKgPerS outlet mass flow rate in kg/s
     * @param measuredImbalanceKgPerS measured mass imbalance in kg/s
     * @param flowUncertaintyKgPerS flow measurement uncertainty in kg/s
     * @param linepackUncertaintyKgPerS linepack uncertainty in kg/s
     * @param combinedUncertaintyKgPerS combined uncertainty in kg/s
     * @param minimumDetectableLeakRateKgPerS minimum detectable leak rate in kg/s
     * @param minimumDetectableLeakFraction minimum detectable leak fraction of reference flow
     * @param detectionWindowS detection window in s
     * @param confidenceMultiplier confidence multiplier
     */
    public LeakDetectionSensitivityResult(String inletStreamName, String outletStreamName,
        double inletFlowKgPerS, double outletFlowKgPerS, double measuredImbalanceKgPerS,
        double flowUncertaintyKgPerS, double linepackUncertaintyKgPerS,
        double combinedUncertaintyKgPerS, double minimumDetectableLeakRateKgPerS,
        double minimumDetectableLeakFraction, double detectionWindowS, double confidenceMultiplier) {
      this.inletStreamName = inletStreamName;
      this.outletStreamName = outletStreamName;
      this.inletFlowKgPerS = inletFlowKgPerS;
      this.outletFlowKgPerS = outletFlowKgPerS;
      this.measuredImbalanceKgPerS = measuredImbalanceKgPerS;
      this.flowUncertaintyKgPerS = flowUncertaintyKgPerS;
      this.linepackUncertaintyKgPerS = linepackUncertaintyKgPerS;
      this.combinedUncertaintyKgPerS = combinedUncertaintyKgPerS;
      this.minimumDetectableLeakRateKgPerS = minimumDetectableLeakRateKgPerS;
      this.minimumDetectableLeakFraction = minimumDetectableLeakFraction;
      this.detectionWindowS = detectionWindowS;
      this.confidenceMultiplier = confidenceMultiplier;
    }

    /**
     * Gets minimum detectable leak rate.
     *
     * @return minimum detectable leak rate in kg/s
     */
    public double getMinimumDetectableLeakRateKgPerS() {
      return minimumDetectableLeakRateKgPerS;
    }

    /**
     * Gets minimum detectable leak fraction.
     *
     * @return minimum detectable leak fraction of reference flow
     */
    public double getMinimumDetectableLeakFraction() {
      return minimumDetectableLeakFraction;
    }

    /**
     * Converts the result to an ordered map.
     *
     * @return ordered map for reporting
     */
    public Map<String, Object> toMap() {
      Map<String, Object> map = new LinkedHashMap<String, Object>();
      map.put("method", "mass-balance leak detection sensitivity");
      map.put("inletStreamName", inletStreamName);
      map.put("outletStreamName", outletStreamName);
      map.put("inletFlowKgPerS", inletFlowKgPerS);
      map.put("outletFlowKgPerS", outletFlowKgPerS);
      map.put("measuredImbalanceKgPerS", measuredImbalanceKgPerS);
      map.put("flowUncertaintyKgPerS", flowUncertaintyKgPerS);
      map.put("linepackUncertaintyKgPerS", linepackUncertaintyKgPerS);
      map.put("combinedUncertaintyKgPerS", combinedUncertaintyKgPerS);
      map.put("minimumDetectableLeakRateKgPerS", minimumDetectableLeakRateKgPerS);
      map.put("minimumDetectableLeakFraction", minimumDetectableLeakFraction);
      map.put("detectionWindowS", detectionWindowS);
      map.put("confidenceMultiplier", confidenceMultiplier);
      return map;
    }

    /**
     * Converts the result to pretty-printed JSON.
     *
     * @return JSON representation of the result
     */
    public String toJson() {
      Gson gson = new GsonBuilder().serializeSpecialFloatingPointValues().setPrettyPrinting()
          .create();
      return gson.toJson(toMap());
    }
  }
}