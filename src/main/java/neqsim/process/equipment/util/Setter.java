package neqsim.process.equipment.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.ProcessEquipmentBaseClass;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.pump.Pump;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ThrottlingValve;

/**
 * <p>
 * Setter class.
 * </p>
 */
public class Setter extends ProcessEquipmentBaseClass {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(Setter.class);

  // Declare targetEquipment as a list of ProcessEquipmentInterface
  private List<ProcessEquipmentInterface> targetEquipment = new ArrayList<>();

  // Store multiple parameters
  private List<Map<String, Object>> parameters = new ArrayList<>();

  /**
   * <p>
   * Constructor for Setter.
   * </p>
   */
  @Deprecated
  public Setter() {
    this("Setter");
  }

  /**
   * <p>
   * Constructor for Setter.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   */
  public Setter(String name) {
    super(name);
  }

  /**
   * Add a single target equipment to the list.
   *
   * @param equipment a {@link neqsim.process.equipment.ProcessEquipmentInterface} object
   */
  public void addTargetEquipment(ProcessEquipmentInterface equipment) {
    targetEquipment.add(equipment);
  }

  /**
   * Add a list of target equipment to the list.
   *
   * @param equipmentList a {@link java.util.List} of
   *        {@link neqsim.process.equipment.ProcessEquipmentInterface} objects
   */
  public void addTargetEquipment(List<ProcessEquipmentInterface> equipmentList) {
    targetEquipment.addAll(equipmentList);
  }

  /**
   * Add a parameter to the list of parameters.
   *
   * @param type a {@link java.lang.String} object representing the type
   * @param unit a {@link java.lang.String} object representing the unit
   * @param value a {@link double} representing the value
   */
  public void addParameter(String type, String unit, double value) {
    Map<String, Object> parameter = new HashMap<>();
    parameter.put("type", type);
    parameter.put("unit", unit);
    parameter.put("value", value);
    parameters.add(parameter);
  }

  /**
   * Get the list of parameters.
   *
   * @return a {@link java.util.List} of parameters
   */
  public List<Map<String, Object>> getParameters() {
    return parameters;
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    setCalculationIdentifier(id);

    // Iterate over targetEquipment and set the specification type
    for (ProcessEquipmentInterface equipment : targetEquipment) {
      try {
        logger.info("Running equipment: " + equipment.getName());

        // Apply each parameter to the equipment
        for (Map<String, Object> parameter : parameters) {
          String type = (String) parameter.get("type");
          String unit = (String) parameter.get("unit");
          double value = (double) parameter.get("value");

          if (equipment instanceof Stream) {
            Stream stream = (Stream) equipment;

            switch (type.toLowerCase()) {
              case "pressure":
                stream.setPressure(value, unit);
                logger.info(
                    "Set pressure to " + value + " " + unit + " for stream: " + stream.getName());
                break;

              case "temperature":
                stream.setTemperature(value, unit);
                logger.info("Set temperature to " + value + " " + unit + " for stream: "
                    + stream.getName());
                break;

              default:
                logger.warn(
                    "Unknown specification type: " + type + " for stream: " + stream.getName());
                break;
            }
          } else if (equipment instanceof Compressor) {
            Compressor comp1 = (Compressor) equipment;

            if ("pressure".equalsIgnoreCase(type)) {
              comp1.setOutletPressure(value, unit);
              logger.info("Set outlet pressure to " + value + " " + unit + " for compressor: "
                  + comp1.getName());
            } else {
              logger.warn(
                  "Unknown specification type: " + type + " for compressor: " + comp1.getName());
            }
          } else if (equipment instanceof Pump) {
            Pump pump1 = (Pump) equipment;
            if ("pressure".equalsIgnoreCase(type)) {
              pump1.setOutletPressure(value, unit);
              logger.info("Set outlet pressure to " + value + " " + unit + " for compressor: "
                  + pump1.getName());
            } else {
              logger.warn("Unknown specification type: " + type + " for pump1: " + pump1.getName());
            }
          } else if (equipment instanceof ThrottlingValve) {
            ThrottlingValve valve = (ThrottlingValve) equipment;

            if ("pressure".equalsIgnoreCase(type)) {
              valve.setOutletPressure(value, unit);
              logger.info("Set outlet pressure to " + value + " " + unit + " for throttling valve: "
                  + valve.getName());
            } else {
              logger.warn("Unknown specification type: " + type + " for throttling valve: "
                  + valve.getName());
            }
          } else if (equipment instanceof Heater) {
            Heater heater = (Heater) equipment;

            if ("temperature".equalsIgnoreCase(type)) {
              heater.setOutTemperature(value, unit);
              logger.info("Set outlet temperature to " + value + " " + unit + " for heater: "
                  + heater.getName());
            } else {
              logger
                  .warn("Unknown specification type: " + type + " for heater: " + heater.getName());
            }
          } else if (equipment instanceof Cooler) {
            Cooler cooler = (Cooler) equipment;

            if ("temperature".equalsIgnoreCase(type)) {
              cooler.setOutTemperature(value, unit);
              logger.info("Set outlet temperature to " + value + " " + unit + " for cooler: "
                  + cooler.getName());
            } else if ("pressure".equalsIgnoreCase(type)) {
              cooler.setOutPressure(value, unit);
              logger.info("Set outlet pressure to " + value + " " + unit + " for cooler: "
                  + cooler.getName());
            } else {
              logger
                  .warn("Unknown specification type: " + type + " for cooler: " + cooler.getName());
            }
          } else {
            logger.warn("Unsupported equipment type: " + equipment.getClass().getSimpleName()
                + " for equipment: " + equipment.getName());
          }
        }
      } catch (Exception ex) {
        logger.error("Error setting specification for equipment: " + equipment.getName(), ex);
      }
    }

  }

  public static void main(String[] args) {
    Setter setter = new Setter("Test Setter");

    // Add multiple parameters
    setter.addParameter("temperature", "C", 150.0);
    setter.addParameter("pressure", "bar", 5.0);

    // Add target equipment
    Stream stream = new Stream("Test Stream");
    setter.addTargetEquipment(stream);

    // Run the setter
    setter.run(UUID.randomUUID());
  }
}
