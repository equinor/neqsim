package neqsim.process.equipment.util;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.ProcessEquipmentBaseClass;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * SetPoint class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class Setter extends ProcessEquipmentBaseClass {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(Setter.class);

  // Declare targetEquipment as a list of ProcessEquipmentInterface
  private List<ProcessEquipmentInterface> targetEquipment = new ArrayList<>();

  // New fields
  private String type;
  private String unit;
  private double value;

  /**
   * <p>
   * Constructor for SetPoint.
   * </p>
   */
  @Deprecated
  public Setter() {
    this("Setter");
  }

  /**
   * <p>
   * Constructor for SetPoint.
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
   * Get the list of target equipment.
   *
   * @return a {@link java.util.List} of {@link neqsim.process.equipment.ProcessEquipmentInterface}
   */
  public List<ProcessEquipmentInterface> getTargetEquipment() {
    return targetEquipment;
  }

  /**
   * Common setter for type, unit, and value.
   *
   * @param type a {@link java.lang.String} object representing the type
   * @param unit a {@link java.lang.String} object representing the unit
   * @param value a {@link double} representing the value
   */
  public void setParameter(String type, String unit, double value) {
    this.type = type;
    this.unit = unit;
    this.value = value;
  }

  /**
   * Get the type.
   *
   * @return a {@link java.lang.String} object representing the type
   */
  public String getType() {
    return type;
  }

  /**
   * Get the unit.
   *
   * @return a {@link java.lang.String} object representing the unit
   */
  public String getUnit() {
    return unit;
  }

  /**
   * Get the value.
   *
   * @return a {@link double} representing the value
   */
  public double getValue() {
    return value;
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    setCalculationIdentifier(id);

    // Iterate over targetEquipment and set the specification type
    for (ProcessEquipmentInterface equipment : targetEquipment) {
      try {
        logger.info("Running equipment: " + equipment.getName());

        // Check the type of equipment and apply the appropriate setter
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
              logger.info(
                  "Set temperature to " + value + " " + unit + " for stream: " + stream.getName());
              break;

            default:
              logger
                  .warn("Unknown specification type: " + type + " for stream: " + stream.getName());
              break;
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
            logger.warn("Unknown specification type: " + type + " for heater: " + heater.getName());
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
            logger.warn("Unknown specification type: " + type + " for cooler: " + cooler.getName());
          }
        } else {
          logger.warn("Unsupported equipment type: " + equipment.getClass().getSimpleName()
              + " for equipment: " + equipment.getName());
        }
      } catch (Exception ex) {
        logger.error("Error setting specification for equipment: " + equipment.getName(), ex);
      }
    }
  }
}
