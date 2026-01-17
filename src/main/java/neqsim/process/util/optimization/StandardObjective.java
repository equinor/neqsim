package neqsim.process.util.optimization;

import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.pump.Pump;
import neqsim.process.equipment.stream.StreamInterface;
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
          // getPower() returns Watts, convert to kW
          totalPower += ((Compressor) unit).getPower("kW");
        } else if (unit instanceof Pump) {
          // getPower() returns Watts, convert to kW
          totalPower += ((Pump) unit).getPower() / 1000.0;
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
   */
  public static ObjectiveFunction maximizeStreamFlow(String streamName) {
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
   */
  public static ObjectiveFunction minimizeCompressorPower(String compressorName) {
    return ObjectiveFunction.create("Minimize " + compressorName + " Power", process -> {
      ProcessEquipmentInterface unit = process.getUnit(compressorName);
      if (unit instanceof Compressor) {
        return ((Compressor) unit).getPower();
      }
      return 0.0;
    }, Direction.MINIMIZE, "kW");
  }
}
