package neqsim.process.ml;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.processmodel.ProcessSystem;

/**
 * Physics-grounded reward functions for reinforcement learning process optimization.
 *
 * <p>
 * Provides pre-built, composable reward functions for common process engineering optimization
 * objectives. Each reward component is physically meaningful and grounded in engineering metrics
 * (energy efficiency, product quality, throughput, safety compliance). Rewards are designed to be
 * combined with configurable weights for multi-objective optimization.
 * </p>
 *
 * <h2>Available Reward Components:</h2>
 * <ul>
 * <li>{@link #energyEfficiency(ProcessSystem)} — Minimizes total energy consumption (compressor
 * power + heater/cooler duty)</li>
 * <li>{@link #productQuality(ProcessSystem, String, String, double)} — Tracks a product stream
 * property toward a target setpoint</li>
 * <li>{@link #throughput(ProcessSystem, String)} — Maximizes production throughput</li>
 * <li>{@link #constraintSatisfaction(ProcessSystem)} — Penalizes operating envelope violations</li>
 * <li>{@link #specificEnergy(ProcessSystem, String)} — Minimizes energy per unit of product</li>
 * </ul>
 *
 * <h2>Usage Example:</h2>
 *
 * <pre>
 * {@code
 * ProcessRewardFunction reward = new ProcessRewardFunction(process);
 * reward.addEnergyMinimization(1.0);
 * reward.addProductQualityTarget("export gas", "methane_molfrac", 0.95, 10.0);
 * reward.addThroughputMaximization("export gas", 0.5);
 * reward.addConstraintPenalty(100.0);
 *
 * double r = reward.compute();
 * String breakdown = reward.getBreakdownJson();
 * }
 * </pre>
 *
 * @author ESOL
 * @version 1.0
 */
public class ProcessRewardFunction implements Serializable {
  private static final long serialVersionUID = 1000L;

  private final ProcessSystem process;
  private final List<RewardComponent> components;
  private Map<String, Double> lastBreakdown;

  /**
   * Create a reward function for a process system.
   *
   * @param process the process system to evaluate
   */
  public ProcessRewardFunction(ProcessSystem process) {
    this.process = process;
    this.components = new ArrayList<RewardComponent>();
    this.lastBreakdown = new LinkedHashMap<String, Double>();
  }

  /**
   * Add energy minimization component. Sums compressor power and heater/cooler absolute duty,
   * normalized by a reference scale, and applies a negative reward.
   *
   * @param weight reward weight (typically 0.1 to 10.0)
   * @return this for chaining
   */
  public ProcessRewardFunction addEnergyMinimization(double weight) {
    components.add(new RewardComponent("energy_minimization", weight) {
      @Override
      double computeRaw() {
        return -energyEfficiency(process);
      }
    });
    return this;
  }

  /**
   * Add product quality tracking toward a target value. Uses quadratic penalty for deviation from
   * the setpoint.
   *
   * @param streamName name of the product stream equipment
   * @param propertyName property to track (e.g., "temperature", "methane_molfrac")
   * @param target target value in physical units
   * @param weight reward weight
   * @return this for chaining
   */
  public ProcessRewardFunction addProductQualityTarget(String streamName, String propertyName,
      double target, double weight) {
    components.add(new RewardComponent("quality_" + streamName + "_" + propertyName, weight) {
      @Override
      double computeRaw() {
        return -productQuality(process, streamName, propertyName, target);
      }
    });
    return this;
  }

  /**
   * Add throughput maximization for a product stream.
   *
   * @param streamName name of the product stream equipment
   * @param weight reward weight
   * @return this for chaining
   */
  public ProcessRewardFunction addThroughputMaximization(String streamName, double weight) {
    components.add(new RewardComponent("throughput_" + streamName, weight) {
      @Override
      double computeRaw() {
        return throughput(process, streamName);
      }
    });
    return this;
  }

  /**
   * Add constraint satisfaction penalty. Penalizes streams with non-physical values (negative T, P,
   * compositions outside [0,1]).
   *
   * @param weight penalty weight (typically 50-200)
   * @return this for chaining
   */
  public ProcessRewardFunction addConstraintPenalty(double weight) {
    components.add(new RewardComponent("constraint_penalty", weight) {
      @Override
      double computeRaw() {
        return -constraintSatisfaction(process);
      }
    });
    return this;
  }

  /**
   * Add specific energy minimization (energy per unit product).
   *
   * @param productStreamName name of the product stream
   * @param weight reward weight
   * @return this for chaining
   */
  public ProcessRewardFunction addSpecificEnergyMinimization(String productStreamName,
      double weight) {
    components.add(new RewardComponent("specific_energy_" + productStreamName, weight) {
      @Override
      double computeRaw() {
        return -specificEnergy(process, productStreamName);
      }
    });
    return this;
  }

  /**
   * Compute the total reward as the weighted sum of all components.
   *
   * @return total scalar reward
   */
  public double compute() {
    lastBreakdown.clear();
    double total = 0.0;
    for (RewardComponent comp : components) {
      double raw = comp.computeRaw();
      double weighted = comp.weight * raw;
      lastBreakdown.put(comp.name, weighted);
      total += weighted;
    }
    lastBreakdown.put("total", total);
    return total;
  }

  /**
   * Get the breakdown of the last reward computation.
   *
   * @return map of component name to weighted reward value
   */
  public Map<String, Double> getLastBreakdown() {
    return new LinkedHashMap<String, Double>(lastBreakdown);
  }

  /**
   * Get the last breakdown as JSON string.
   *
   * @return JSON formatted breakdown
   */
  public String getBreakdownJson() {
    StringBuilder sb = new StringBuilder();
    sb.append("{\n");
    int i = 0;
    for (Map.Entry<String, Double> entry : lastBreakdown.entrySet()) {
      if (i > 0) {
        sb.append(",\n");
      }
      sb.append("  \"").append(entry.getKey()).append("\": ")
          .append(String.format("%.6f", entry.getValue()));
      i++;
    }
    sb.append("\n}");
    return sb.toString();
  }

  // ── Static reward computation methods ────────────────────────────────

  /**
   * Compute total energy consumption of the process normalized to MW.
   *
   * <p>
   * Sums compressor power (positive = consumed), heater duty (positive = consumed), and cooler duty
   * (absolute = energy removed that must be supplied by cooling utility). Returns the total in MW.
   * </p>
   *
   * @param proc the process system
   * @return total energy consumption in MW (always non-negative)
   */
  public static double energyEfficiency(ProcessSystem proc) {
    double totalEnergyW = 0.0;
    List<ProcessEquipmentInterface> units = proc.getUnitOperations();
    if (units == null) {
      return 0.0;
    }

    for (ProcessEquipmentInterface unit : units) {
      if (unit instanceof Compressor) {
        Compressor comp = (Compressor) unit;
        double power = comp.getPower();
        if (!Double.isNaN(power) && !Double.isInfinite(power)) {
          totalEnergyW += Math.abs(power);
        }
      } else if (unit instanceof Heater) {
        Heater heater = (Heater) unit;
        double duty = heater.getDuty();
        if (!Double.isNaN(duty) && !Double.isInfinite(duty)) {
          totalEnergyW += Math.abs(duty);
        }
      } else if (unit instanceof Cooler) {
        Cooler cooler = (Cooler) unit;
        double duty = cooler.getDuty();
        if (!Double.isNaN(duty) && !Double.isInfinite(duty)) {
          totalEnergyW += Math.abs(duty);
        }
      }
    }

    return totalEnergyW / 1.0e6; // Convert W to MW
  }

  /**
   * Compute squared deviation of a product stream property from its target.
   *
   * @param proc the process system
   * @param streamName equipment name of the product stream
   * @param propertyName "temperature" (K), "pressure" (bar), or component name for mole fraction
   * @param target target value
   * @return squared normalized deviation (0 at target, increases with distance)
   */
  public static double productQuality(ProcessSystem proc, String streamName, String propertyName,
      double target) {
    ProcessEquipmentInterface unit = proc.getUnit(streamName);
    if (unit == null) {
      return 1.0; // Max penalty if stream not found
    }

    List<StreamInterface> outlets = unit.getOutletStreams();
    if (outlets == null || outlets.isEmpty()) {
      // The unit itself might be a stream
      if (unit instanceof StreamInterface) {
        return computePropertyDeviation((StreamInterface) unit, propertyName, target);
      }
      return 1.0;
    }

    return computePropertyDeviation(outlets.get(0), propertyName, target);
  }

  /**
   * Compute mass throughput of a product stream in kg/s.
   *
   * @param proc the process system
   * @param streamName equipment name
   * @return mass flow rate in kg/s, or 0 if not found
   */
  public static double throughput(ProcessSystem proc, String streamName) {
    ProcessEquipmentInterface unit = proc.getUnit(streamName);
    if (unit == null) {
      return 0.0;
    }

    List<StreamInterface> outlets = unit.getOutletStreams();
    StreamInterface stream = null;

    if (outlets != null && !outlets.isEmpty()) {
      stream = outlets.get(0);
    } else if (unit instanceof StreamInterface) {
      stream = (StreamInterface) unit;
    }

    if (stream == null || stream.getThermoSystem() == null) {
      return 0.0;
    }

    double flow = stream.getThermoSystem().getFlowRate("kg/sec");
    return (!Double.isNaN(flow) && !Double.isInfinite(flow)) ? flow : 0.0;
  }

  /**
   * Compute total constraint violation penalty.
   *
   * <p>
   * Checks all streams for non-physical values: T &lt; 0 K, P &lt; 0, compositions outside [0,1],
   * NaN values. Returns the sum of squared violations.
   * </p>
   *
   * @param proc the process system
   * @return total violation penalty (0 = no violations)
   */
  public static double constraintSatisfaction(ProcessSystem proc) {
    double penalty = 0.0;
    List<ProcessEquipmentInterface> units = proc.getUnitOperations();
    if (units == null) {
      return 0.0;
    }

    for (ProcessEquipmentInterface unit : units) {
      List<StreamInterface> outlets = unit.getOutletStreams();
      if (outlets == null) {
        continue;
      }
      for (StreamInterface stream : outlets) {
        if (stream == null || stream.getThermoSystem() == null) {
          continue;
        }

        double t = stream.getThermoSystem().getTemperature();
        double p = stream.getThermoSystem().getPressure();

        if (Double.isNaN(t) || Double.isInfinite(t) || t <= 0.0) {
          penalty += 1.0;
        }
        if (Double.isNaN(p) || Double.isInfinite(p) || p <= 0.0) {
          penalty += 1.0;
        }

        // Check compositions
        int numPhases = stream.getThermoSystem().getNumberOfPhases();
        for (int phase = 0; phase < numPhases; phase++) {
          double sum = 0.0;
          for (int comp = 0; comp < stream.getThermoSystem().getPhase(phase)
              .getNumberOfComponents(); comp++) {
            double x = stream.getThermoSystem().getPhase(phase).getComponent(comp).getx();
            if (Double.isNaN(x) || x < -0.001 || x > 1.001) {
              penalty += 0.1;
            }
            sum += x;
          }
          if (Math.abs(sum - 1.0) > 0.01) {
            penalty += 0.5;
          }
        }
      }
    }
    return penalty;
  }

  /**
   * Compute specific energy consumption (energy per unit of product).
   *
   * @param proc the process system
   * @param productStreamName product stream name
   * @return specific energy in MJ/kg, or a large number if no throughput
   */
  public static double specificEnergy(ProcessSystem proc, String productStreamName) {
    double energy = energyEfficiency(proc); // MW
    double flow = throughput(proc, productStreamName); // kg/s
    if (flow < 1.0e-6) {
      return 1000.0; // Large penalty for zero throughput
    }
    return energy / flow; // MW / (kg/s) = MJ/kg
  }

  /**
   * Compute deviation of a stream property from target.
   *
   * @param stream the stream
   * @param propertyName "temperature", "pressure", or component name for mole fraction
   * @param target target value
   * @return squared normalized deviation
   */
  private static double computePropertyDeviation(StreamInterface stream, String propertyName,
      double target) {
    if (stream == null || stream.getThermoSystem() == null) {
      return 1.0;
    }

    double actual = Double.NaN;
    double scale = 1.0;

    if ("temperature".equalsIgnoreCase(propertyName)) {
      actual = stream.getThermoSystem().getTemperature();
      scale = 100.0; // Normalize by ~100K
    } else if ("pressure".equalsIgnoreCase(propertyName)) {
      actual = stream.getThermoSystem().getPressure();
      scale = target > 0 ? target : 1.0;
    } else {
      // Treat as component name — look up mole fraction in first phase
      int numPhases = stream.getThermoSystem().getNumberOfPhases();
      if (numPhases > 0) {
        try {
          int compIdx =
              stream.getThermoSystem().getPhase(0).getComponent(propertyName).getComponentNumber();
          actual = stream.getThermoSystem().getPhase(0).getComponent(compIdx).getx();
          scale = 1.0;
        } catch (Exception e) {
          return 1.0;
        }
      }
    }

    if (Double.isNaN(actual) || Double.isInfinite(actual)) {
      return 1.0;
    }

    double normalizedError = (actual - target) / scale;
    return normalizedError * normalizedError;
  }

  /**
   * Abstract base class for reward components.
   */
  private abstract static class RewardComponent implements Serializable {
    private static final long serialVersionUID = 1000L;

    /** Component name. */
    final String name;
    /** Weight for this component. */
    final double weight;

    /**
     * Constructor.
     *
     * @param name component name
     * @param weight component weight
     */
    RewardComponent(String name, double weight) {
      this.name = name;
      this.weight = weight;
    }

    /**
     * Compute the raw (unweighted) reward value.
     *
     * @return raw reward
     */
    abstract double computeRaw();
  }
}
