package neqsim.process.util.optimization;

import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.manifold.Manifold;
import neqsim.process.equipment.pipeline.PipeBeggsAndBrills;
import neqsim.process.equipment.pump.Pump;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.measurementdevice.FlowInducedVibrationAnalyser;
import neqsim.process.measurementdevice.MeasurementDeviceInterface;
import neqsim.process.processmodel.ProcessSystem;

/**
 * Standard optimization objectives commonly used in process optimization.
 *
 * <p>
 * These objectives can be used directly or as templates for custom objectives.
 * </p>
 *
 * @author ASMF
 * @version 1.0
 */
public enum StandardObjective implements ObjectiveFunction {

  /**
   * Maximize total feed throughput.
   */
  MAXIMIZE_THROUGHPUT {
    @Override
    public String getName() {
      return "Throughput";
    }

    @Override
    public Direction getDirection() {
      return Direction.MAXIMIZE;
    }

    @Override
    public double evaluate(ProcessSystem process) {
      // Find feed stream (first stream in process)
      for (ProcessEquipmentInterface unit : process.getUnitOperations()) {
        if (unit instanceof StreamInterface) {
          return ((StreamInterface) unit).getFlowRate("kg/hr");
        }
      }
      return 0.0;
    }

    @Override
    public String getUnit() {
      return "kg/hr";
    }
  },

  /**
   * Minimize total power consumption (compressors + pumps).
   */
  MINIMIZE_POWER {
    @Override
    public String getName() {
      return "Total Power";
    }

    @Override
    public Direction getDirection() {
      return Direction.MINIMIZE;
    }

    @Override
    public double evaluate(ProcessSystem process) {
      double totalPower = 0.0;
      for (ProcessEquipmentInterface unit : process.getUnitOperations()) {
        if (unit instanceof Compressor) {
          // getPower("kW") returns power in kW
          totalPower += ((Compressor) unit).getPower("kW");
        } else if (unit instanceof Pump) {
          // getPower("kW") returns power in kW
          totalPower += ((Pump) unit).getPower("kW");
        }
      }
      return totalPower;
    }

    @Override
    public String getUnit() {
      return "kW";
    }
  },

  /**
   * Minimize total heating duty.
   */
  MINIMIZE_HEATING_DUTY {
    @Override
    public String getName() {
      return "Heating Duty";
    }

    @Override
    public Direction getDirection() {
      return Direction.MINIMIZE;
    }

    @Override
    public double evaluate(ProcessSystem process) {
      double totalDuty = 0.0;
      for (ProcessEquipmentInterface unit : process.getUnitOperations()) {
        if (unit instanceof Heater) {
          double duty = ((Heater) unit).getDuty();
          if (duty > 0) {
            totalDuty += duty;
          }
        }
      }
      return totalDuty / 1000.0; // Convert to kW
    }

    @Override
    public String getUnit() {
      return "kW";
    }
  },

  /**
   * Minimize total cooling duty.
   */
  MINIMIZE_COOLING_DUTY {
    @Override
    public String getName() {
      return "Cooling Duty";
    }

    @Override
    public Direction getDirection() {
      return Direction.MINIMIZE;
    }

    @Override
    public double evaluate(ProcessSystem process) {
      double totalDuty = 0.0;
      for (ProcessEquipmentInterface unit : process.getUnitOperations()) {
        if (unit instanceof Cooler) {
          double duty = Math.abs(((Cooler) unit).getDuty());
          totalDuty += duty;
        }
      }
      return totalDuty / 1000.0; // Convert to kW
    }

    @Override
    public String getUnit() {
      return "kW";
    }
  },

  /**
   * Minimize total energy consumption (power + heating + cooling).
   */
  MINIMIZE_TOTAL_ENERGY {
    @Override
    public String getName() {
      return "Total Energy";
    }

    @Override
    public Direction getDirection() {
      return Direction.MINIMIZE;
    }

    @Override
    public double evaluate(ProcessSystem process) {
      double power = MINIMIZE_POWER.evaluate(process);
      double heating = MINIMIZE_HEATING_DUTY.evaluate(process);
      double cooling = MINIMIZE_COOLING_DUTY.evaluate(process);
      return power + heating + cooling;
    }

    @Override
    public String getUnit() {
      return "kW";
    }
  },

  /**
   * Maximize specific production (throughput per unit power).
   */
  MAXIMIZE_SPECIFIC_PRODUCTION {
    @Override
    public String getName() {
      return "Specific Production";
    }

    @Override
    public Direction getDirection() {
      return Direction.MAXIMIZE;
    }

    @Override
    public double evaluate(ProcessSystem process) {
      double throughput = MAXIMIZE_THROUGHPUT.evaluate(process);
      double power = MINIMIZE_POWER.evaluate(process);
      if (power < 1.0) {
        power = 1.0; // Avoid division by zero
      }
      return throughput / power;
    }

    @Override
    public String getUnit() {
      return "kg/kWh";
    }
  },

  /**
   * Maximize liquid recovery (for separation processes).
   */
  MAXIMIZE_LIQUID_RECOVERY {
    @Override
    public String getName() {
      return "Liquid Recovery";
    }

    @Override
    public Direction getDirection() {
      return Direction.MAXIMIZE;
    }

    @Override
    public double evaluate(ProcessSystem process) {
      // This requires process-specific implementation
      // Default: return 0 if not applicable
      return 0.0;
    }

    @Override
    public String getUnit() {
      return "%";
    }
  };

  /**
   * Create an objective for a specific stream's flow rate.
   *
   * @param streamName name of the stream to maximize
   * @return objective function
   * @throws IllegalArgumentException if streamName is null or empty
   */
  public static ObjectiveFunction maximizeStreamFlow(String streamName) {
    if (streamName == null || streamName.trim().isEmpty()) {
      throw new IllegalArgumentException("Stream name cannot be null or empty");
    }
    return ObjectiveFunction.create("Maximize " + streamName + " Flow", process -> {
      ProcessEquipmentInterface unit = process.getUnit(streamName);
      if (unit instanceof StreamInterface) {
        return ((StreamInterface) unit).getFlowRate("kg/hr");
      }
      return 0.0;
    }, Direction.MAXIMIZE, "kg/hr");
  }

  /**
   * Create an objective for a specific compressor's power.
   *
   * @param compressorName name of the compressor
   * @return objective function
   * @throws IllegalArgumentException if compressorName is null or empty
   */
  public static ObjectiveFunction minimizeCompressorPower(String compressorName) {
    if (compressorName == null || compressorName.trim().isEmpty()) {
      throw new IllegalArgumentException("Compressor name cannot be null or empty");
    }
    return ObjectiveFunction.create("Minimize " + compressorName + " Power", process -> {
      ProcessEquipmentInterface unit = process.getUnit(compressorName);
      if (unit instanceof Compressor) {
        // Return power in kW to match declared unit
        return ((Compressor) unit).getPower("kW");
      }
      return 0.0;
    }, Direction.MINIMIZE, "kW");
  }

  /**
   * Create an objective for a specific pump's power.
   *
   * @param pumpName name of the pump
   * @return objective function
   * @throws IllegalArgumentException if pumpName is null or empty
   */
  public static ObjectiveFunction minimizePumpPower(String pumpName) {
    if (pumpName == null || pumpName.trim().isEmpty()) {
      throw new IllegalArgumentException("Pump name cannot be null or empty");
    }
    return ObjectiveFunction.create("Minimize " + pumpName + " Power", process -> {
      ProcessEquipmentInterface unit = process.getUnit(pumpName);
      if (unit instanceof Pump) {
        return ((Pump) unit).getPower("kW");
      }
      return 0.0;
    }, Direction.MINIMIZE, "kW");
  }

  /**
   * Create an objective for maximizing pump efficiency.
   *
   * @param pumpName name of the pump
   * @return objective function
   * @throws IllegalArgumentException if pumpName is null or empty
   */
  public static ObjectiveFunction maximizePumpEfficiency(String pumpName) {
    if (pumpName == null || pumpName.trim().isEmpty()) {
      throw new IllegalArgumentException("Pump name cannot be null or empty");
    }
    return ObjectiveFunction.create("Maximize " + pumpName + " Efficiency", process -> {
      ProcessEquipmentInterface unit = process.getUnit(pumpName);
      if (unit instanceof Pump) {
        return ((Pump) unit).getIsentropicEfficiency() * 100.0; // Return as percentage
      }
      return 0.0;
    }, Direction.MAXIMIZE, "%");
  }

  /**
   * Create an objective for maximizing pump NPSH margin.
   *
   * <p>
   * NPSH margin = NPSHa / NPSHr. Higher values indicate more cavitation safety.
   * </p>
   *
   * @param pumpName name of the pump
   * @return objective function
   * @throws IllegalArgumentException if pumpName is null or empty
   */
  public static ObjectiveFunction maximizePumpNPSHMargin(String pumpName) {
    if (pumpName == null || pumpName.trim().isEmpty()) {
      throw new IllegalArgumentException("Pump name cannot be null or empty");
    }
    return ObjectiveFunction.create("Maximize " + pumpName + " NPSH Margin", process -> {
      ProcessEquipmentInterface unit = process.getUnit(pumpName);
      if (unit instanceof Pump) {
        Pump pump = (Pump) unit;
        double npsha = pump.getNPSHAvailable();
        double npshr = pump.getNPSHRequired();
        return npshr > 0 ? npsha / npshr : 10.0; // Return ratio, high value if no NPSHr
      }
      return 1.0;
    }, Direction.MAXIMIZE, "ratio");
  }

  /**
   * Create an objective for a specific stream's flow rate.
   *
   * @param streamName name of the stream to minimize
   * @param unit flow rate unit (e.g., "kg/hr", "Sm3/hr")
   * @return objective function
   * @throws IllegalArgumentException if streamName is null or empty
   */
  public static ObjectiveFunction minimizeStreamFlow(String streamName, String unit) {
    if (streamName == null || streamName.trim().isEmpty()) {
      throw new IllegalArgumentException("Stream name cannot be null or empty");
    }
    final String flowUnit = unit != null ? unit : "kg/hr";
    return ObjectiveFunction.create("Minimize " + streamName + " Flow", process -> {
      ProcessEquipmentInterface streamUnit = process.getUnit(streamName);
      if (streamUnit instanceof StreamInterface) {
        return ((StreamInterface) streamUnit).getFlowRate(flowUnit);
      }
      return 0.0;
    }, Direction.MINIMIZE, flowUnit);
  }

  // ============================================================================
  // Flow Induced Vibration (FIV) Objectives
  // ============================================================================

  /**
   * Create an objective for minimizing FIV LOF (Likelihood of Failure) on a pipeline.
   *
   * <p>
   * LOF values: &lt;0.5 (low risk), 0.5-1.0 (medium risk), &gt;1.0 (high risk). Minimizing LOF
   * reduces vibration-induced fatigue failure risk.
   * </p>
   *
   * @param fivAnalyzerName name of the FlowInducedVibrationAnalyser measurement device
   * @return objective function
   * @throws IllegalArgumentException if fivAnalyzerName is null or empty
   */
  public static ObjectiveFunction minimizeFIV_LOF(String fivAnalyzerName) {
    if (fivAnalyzerName == null || fivAnalyzerName.trim().isEmpty()) {
      throw new IllegalArgumentException("FIV analyzer name cannot be null or empty");
    }
    return ObjectiveFunction.create("Minimize " + fivAnalyzerName + " LOF", process -> {
      MeasurementDeviceInterface device = process.getMeasurementDevice(fivAnalyzerName);
      if (device != null && device instanceof FlowInducedVibrationAnalyser) {
        return device.getMeasuredValue("");
      }
      return Double.MAX_VALUE; // Return high value if not found
    }, Direction.MINIMIZE, "LOF");
  }

  /**
   * Create an objective for minimizing FRMS (Flow-induced vibration RMS) on a pipeline.
   *
   * <p>
   * FRMS measures vibration intensity. Lower values indicate safer operation.
   * </p>
   *
   * @param fivAnalyzerName name of the FlowInducedVibrationAnalyser measurement device
   * @return objective function
   * @throws IllegalArgumentException if fivAnalyzerName is null or empty
   */
  public static ObjectiveFunction minimizeFIV_FRMS(String fivAnalyzerName) {
    if (fivAnalyzerName == null || fivAnalyzerName.trim().isEmpty()) {
      throw new IllegalArgumentException("FIV analyzer name cannot be null or empty");
    }
    return ObjectiveFunction.create("Minimize " + fivAnalyzerName + " FRMS", process -> {
      MeasurementDeviceInterface device = process.getMeasurementDevice(fivAnalyzerName);
      if (device != null && device instanceof FlowInducedVibrationAnalyser) {
        return device.getMeasuredValue("");
      }
      return Double.MAX_VALUE; // Return high value if not found
    }, Direction.MINIMIZE, "FRMS");
  }

  /**
   * Create a pipeline FIV analyzer objective directly from a pipeline.
   *
   * <p>
   * This creates a temporary FlowInducedVibrationAnalyser and evaluates it. For repeated
   * optimization, prefer adding the analyzer to the process and using minimizeFIV_LOF().
   * </p>
   *
   * @param pipelineName name of the PipeBeggsAndBrills pipeline
   * @param method "LOF" for Likelihood of Failure, "FRMS" for RMS method
   * @param supportArrangement for LOF: "Stiff", "Medium stiff", "Medium", or other
   * @return objective function
   * @throws IllegalArgumentException if pipelineName is null or empty
   */
  public static ObjectiveFunction minimizePipelineVibration(String pipelineName, String method,
      String supportArrangement) {
    if (pipelineName == null || pipelineName.trim().isEmpty()) {
      throw new IllegalArgumentException("Pipeline name cannot be null or empty");
    }
    final String vibMethod = method != null ? method : "LOF";
    final String support = supportArrangement != null ? supportArrangement : "Stiff";

    return ObjectiveFunction.create("Minimize " + pipelineName + " " + vibMethod, process -> {
      ProcessEquipmentInterface unit = process.getUnit(pipelineName);
      if (unit instanceof PipeBeggsAndBrills) {
        PipeBeggsAndBrills pipe = (PipeBeggsAndBrills) unit;
        FlowInducedVibrationAnalyser fiv = new FlowInducedVibrationAnalyser("temp_fiv", pipe);
        fiv.setMethod(vibMethod);
        if (vibMethod.equals("LOF")) {
          fiv.setSupportArrangement(support);
        }
        return fiv.getMeasuredValue("");
      }
      return Double.MAX_VALUE;
    }, Direction.MINIMIZE, vibMethod);
  }

  // ============================================================================
  // Manifold Objectives
  // ============================================================================

  /**
   * Create an objective for maximizing manifold throughput.
   *
   * @param manifoldName name of the manifold
   * @return objective function
   * @throws IllegalArgumentException if manifoldName is null or empty
   */
  public static ObjectiveFunction maximizeManifoldThroughput(String manifoldName) {
    if (manifoldName == null || manifoldName.trim().isEmpty()) {
      throw new IllegalArgumentException("Manifold name cannot be null or empty");
    }
    return ObjectiveFunction.create("Maximize " + manifoldName + " Throughput", process -> {
      ProcessEquipmentInterface unit = process.getUnit(manifoldName);
      if (unit instanceof Manifold) {
        Manifold manifold = (Manifold) unit;
        StreamInterface mixedStream = manifold.getMixedStream();
        if (mixedStream != null && mixedStream.getThermoSystem() != null) {
          return mixedStream.getFlowRate("kg/hr");
        }
      }
      return 0.0;
    }, Direction.MAXIMIZE, "kg/hr");
  }

  /**
   * Create an objective for minimizing pressure drop across a manifold.
   *
   * <p>
   * Calculates the maximum pressure drop from any inlet to the mixed outlet.
   * </p>
   *
   * @param manifoldName name of the manifold
   * @return objective function
   * @throws IllegalArgumentException if manifoldName is null or empty
   */
  public static ObjectiveFunction minimizeManifoldPressureDrop(String manifoldName) {
    if (manifoldName == null || manifoldName.trim().isEmpty()) {
      throw new IllegalArgumentException("Manifold name cannot be null or empty");
    }
    return ObjectiveFunction.create("Minimize " + manifoldName + " ΔP", process -> {
      ProcessEquipmentInterface unit = process.getUnit(manifoldName);
      if (unit instanceof Manifold) {
        Manifold manifold = (Manifold) unit;
        StreamInterface mixedStream = manifold.getMixedStream();
        if (mixedStream != null && mixedStream.getThermoSystem() != null) {
          // Pressure drop is calculated internally in manifold/mixer
          // Return outlet pressure (lower is worse, but we minimize pressure loss)
          return mixedStream.getPressure("bara");
        }
      }
      return 0.0;
    }, Direction.MAXIMIZE, "bara"); // Maximize outlet pressure = minimize pressure drop
  }

  /**
   * Create an objective for balancing manifold split ratios.
   *
   * <p>
   * Returns the standard deviation of split ratios. Lower values indicate more balanced
   * distribution.
   * </p>
   *
   * @param manifoldName name of the manifold
   * @return objective function
   * @throws IllegalArgumentException if manifoldName is null or empty
   */
  public static ObjectiveFunction minimizeManifoldImbalance(String manifoldName) {
    if (manifoldName == null || manifoldName.trim().isEmpty()) {
      throw new IllegalArgumentException("Manifold name cannot be null or empty");
    }
    return ObjectiveFunction.create("Minimize " + manifoldName + " Imbalance", process -> {
      ProcessEquipmentInterface unit = process.getUnit(manifoldName);
      if (unit instanceof Manifold) {
        Manifold manifold = (Manifold) unit;
        int numOutputs = manifold.getNumberOfOutputStreams();
        if (numOutputs <= 1) {
          return 0.0;
        }

        // Calculate actual flow distribution
        double[] flows = new double[numOutputs];
        double totalFlow = 0.0;
        for (int i = 0; i < numOutputs; i++) {
          StreamInterface splitStream = manifold.getSplitStream(i);
          if (splitStream != null && splitStream.getThermoSystem() != null) {
            flows[i] = splitStream.getFlowRate("kg/hr");
            totalFlow += flows[i];
          }
        }

        if (totalFlow <= 0) {
          return 0.0;
        }

        // Calculate standard deviation of flow fractions
        double meanFraction = 1.0 / numOutputs;
        double sumSqDiff = 0.0;
        for (int i = 0; i < numOutputs; i++) {
          double fraction = flows[i] / totalFlow;
          double diff = fraction - meanFraction;
          sumSqDiff += diff * diff;
        }
        return Math.sqrt(sumSqDiff / numOutputs);
      }
      return Double.MAX_VALUE;
    }, Direction.MINIMIZE, "σ");
  }

  /**
   * Create an objective for minimizing manifold header LOF (Likelihood of Failure).
   *
   * <p>
   * LOF values: &lt;0.5 (low risk), 0.5-1.0 (medium risk), &gt;1.0 (high risk). This uses the
   * manifold's built-in FIV calculations based on header geometry and flow.
   * </p>
   *
   * @param manifoldName name of the manifold
   * @return objective function
   * @throws IllegalArgumentException if manifoldName is null or empty
   */
  public static ObjectiveFunction minimizeManifoldHeaderLOF(String manifoldName) {
    if (manifoldName == null || manifoldName.trim().isEmpty()) {
      throw new IllegalArgumentException("Manifold name cannot be null or empty");
    }
    return ObjectiveFunction.create("Minimize " + manifoldName + " Header LOF", process -> {
      ProcessEquipmentInterface unit = process.getUnit(manifoldName);
      if (unit instanceof Manifold) {
        Manifold manifold = (Manifold) unit;
        double lof = manifold.calculateHeaderLOF();
        return Double.isFinite(lof) ? lof : Double.MAX_VALUE;
      }
      return Double.MAX_VALUE;
    }, Direction.MINIMIZE, "LOF");
  }

  /**
   * Create an objective for minimizing manifold branch LOF.
   *
   * @param manifoldName name of the manifold
   * @return objective function
   * @throws IllegalArgumentException if manifoldName is null or empty
   */
  public static ObjectiveFunction minimizeManifoldBranchLOF(String manifoldName) {
    if (manifoldName == null || manifoldName.trim().isEmpty()) {
      throw new IllegalArgumentException("Manifold name cannot be null or empty");
    }
    return ObjectiveFunction.create("Minimize " + manifoldName + " Branch LOF", process -> {
      ProcessEquipmentInterface unit = process.getUnit(manifoldName);
      if (unit instanceof Manifold) {
        Manifold manifold = (Manifold) unit;
        double lof = manifold.calculateBranchLOF();
        return Double.isFinite(lof) ? lof : Double.MAX_VALUE;
      }
      return Double.MAX_VALUE;
    }, Direction.MINIMIZE, "LOF");
  }

  /**
   * Create an objective for minimizing manifold header FRMS.
   *
   * <p>
   * FRMS measures vibration intensity. Lower values indicate safer operation.
   * </p>
   *
   * @param manifoldName name of the manifold
   * @return objective function
   * @throws IllegalArgumentException if manifoldName is null or empty
   */
  public static ObjectiveFunction minimizeManifoldHeaderFRMS(String manifoldName) {
    if (manifoldName == null || manifoldName.trim().isEmpty()) {
      throw new IllegalArgumentException("Manifold name cannot be null or empty");
    }
    return ObjectiveFunction.create("Minimize " + manifoldName + " Header FRMS", process -> {
      ProcessEquipmentInterface unit = process.getUnit(manifoldName);
      if (unit instanceof Manifold) {
        Manifold manifold = (Manifold) unit;
        double frms = manifold.calculateHeaderFRMS();
        return Double.isFinite(frms) ? frms : Double.MAX_VALUE;
      }
      return Double.MAX_VALUE;
    }, Direction.MINIMIZE, "FRMS");
  }

  /**
   * Create an objective for keeping manifold header velocity below erosional limits.
   *
   * <p>
   * Returns the ratio of header velocity to erosional velocity. Values &gt;0.8 indicate risk.
   * </p>
   *
   * @param manifoldName name of the manifold
   * @return objective function
   * @throws IllegalArgumentException if manifoldName is null or empty
   */
  public static ObjectiveFunction minimizeManifoldVelocityRatio(String manifoldName) {
    if (manifoldName == null || manifoldName.trim().isEmpty()) {
      throw new IllegalArgumentException("Manifold name cannot be null or empty");
    }
    return ObjectiveFunction.create("Minimize " + manifoldName + " Velocity Ratio", process -> {
      ProcessEquipmentInterface unit = process.getUnit(manifoldName);
      if (unit instanceof Manifold) {
        Manifold manifold = (Manifold) unit;
        double headerVel = manifold.getHeaderVelocity();
        double erosionalVel = manifold.getErosionalVelocity();
        if (erosionalVel > 0) {
          return headerVel / erosionalVel;
        }
      }
      return Double.MAX_VALUE;
    }, Direction.MINIMIZE, "V/Ve");
  }
}
