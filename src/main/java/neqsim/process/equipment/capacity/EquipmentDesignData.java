package neqsim.process.equipment.capacity;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.pump.Pump;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.processmodel.ProcessSystem;

/**
 * Applies equipment design capacity data from JSON input to process equipment.
 *
 * <p>
 * This utility reads a {@code designCapacities} JSON object keyed by equipment name, sets design
 * values (e.g., internal diameter, separator length, rated power, max duty) on the corresponding
 * equipment if not already configured, and tags each capacity constraint's data source for
 * traceability in utilization reports.
 * </p>
 *
 * <p>
 * Supported equipment types and properties:
 * </p>
 *
 * <table>
 * <caption>Design capacity properties by equipment type</caption>
 * <tr>
 * <th>Equipment</th>
 * <th>JSON Properties</th>
 * </tr>
 * <tr>
 * <td>Separator / Scrubber</td>
 * <td>internalDiameter [m], separatorLength [m], designGasLoadFactor [m/s]</td>
 * </tr>
 * <tr>
 * <td>Compressor</td>
 * <td>maxSpeed [RPM], ratedPower [kW]</td>
 * </tr>
 * <tr>
 * <td>Heater / Cooler</td>
 * <td>maxDesignDuty [W], maxDesignDutyKW [kW], maxDesignDutyMW [MW]</td>
 * </tr>
 * <tr>
 * <td>Pump</td>
 * <td>maxDesignPower [kW], maxDesignVolumeFlow [m3/hr]</td>
 * </tr>
 * </table>
 *
 * <p>
 * Example JSON:
 * </p>
 *
 * <pre>
 * {
 *   "designCapacities": {
 *     "HP Separator": {
 *       "internalDiameter": 2.0,
 *       "separatorLength": 6.0,
 *       "designGasLoadFactor": 0.08
 *     },
 *     "Export Compressor": {
 *       "ratedPower": 5000,
 *       "maxSpeed": 12000
 *     },
 *     "Gas Cooler": {
 *       "maxDesignDutyMW": 15.0
 *     }
 *   }
 * }
 * </pre>
 *
 * @author ESOL
 * @version 1.0
 */
public final class EquipmentDesignData {

  /** Data source label applied to constraints populated from this utility. */
  public static final String DATA_SOURCE_DESIGN_CAPACITIES = "designCapacities";

  /** Data source label for equipment properties already set before this utility runs. */
  public static final String DATA_SOURCE_EQUIPMENT = "equipment";

  /** Data source label when no design data is available. */
  public static final String DATA_SOURCE_NOT_SET = "not_set";

  /**
   * Private constructor for utility class.
   */
  private EquipmentDesignData() {}

  /**
   * Applies design capacity data from a JSON object to a process system's equipment.
   *
   * <p>
   * For each equipment name in the JSON, looks up the equipment in the process and applies the
   * design properties. Properties are only set if they are not already configured on the equipment
   * (i.e., still at default or zero values). After applying, tags the capacity constraints with the
   * data source for utilization report traceability.
   * </p>
   *
   * @param process the process system containing the equipment
   * @param designCapacities JSON object keyed by equipment name, with property objects as values
   * @return a report of applied changes, keyed by equipment name
   */
  public static Map<String, ApplyResult> apply(ProcessSystem process,
      JsonObject designCapacities) {
    Map<String, ApplyResult> results = new LinkedHashMap<String, ApplyResult>();

    if (designCapacities == null || process == null) {
      return results;
    }

    for (Map.Entry<String, JsonElement> entry : designCapacities.entrySet()) {
      String equipName = entry.getKey();
      if (!entry.getValue().isJsonObject()) {
        results.put(equipName, new ApplyResult(equipName, "skipped", "Value is not a JSON object"));
        continue;
      }

      ProcessEquipmentInterface equipment = findEquipment(process, equipName);
      if (equipment == null) {
        results.put(equipName,
            new ApplyResult(equipName, "not_found", "Equipment not found in process"));
        continue;
      }

      JsonObject props = entry.getValue().getAsJsonObject();
      ApplyResult result = new ApplyResult(equipName, "applied", "");

      if (equipment instanceof Separator) {
        applySeparatorDesign((Separator) equipment, props, result);
      } else if (equipment instanceof Compressor) {
        applyCompressorDesign((Compressor) equipment, props, result);
      } else if (equipment instanceof Heater) {
        applyHeaterDesign((Heater) equipment, props, result);
      } else if (equipment instanceof Pump) {
        applyPumpDesign((Pump) equipment, props, result);
      } else {
        result.status = "unsupported";
        result.message = "Equipment type not supported for design capacity: "
            + equipment.getClass().getSimpleName();
      }

      results.put(equipName, result);
    }

    // After applying design data, tag all constraints with data source info
    tagConstraintDataSources(process, designCapacities);

    return results;
  }

  /**
   * Tags capacity constraints on all equipment with their data source.
   *
   * <p>
   * Equipment that received design values from the JSON input gets tagged as "designCapacities".
   * Equipment that already had values set gets tagged as "equipment". Equipment with no design data
   * gets "not_set".
   * </p>
   *
   * @param process the process system
   * @param designCapacities the design capacities JSON (used to determine which equipment was
   *        configured)
   */
  public static void tagConstraintDataSources(ProcessSystem process,
      JsonObject designCapacities) {
    for (int i = 0; i < process.getUnitOperations().size(); i++) {
      ProcessEquipmentInterface equip = process.getUnitOperations().get(i);
      if (!(equip instanceof CapacityConstrainedEquipment)) {
        continue;
      }

      CapacityConstrainedEquipment constrained = (CapacityConstrainedEquipment) equip;
      Map<String, CapacityConstraint> constraints = constrained.getCapacityConstraints();
      boolean hasDesignInput =
          designCapacities != null && designCapacities.has(equip.getName());

      for (CapacityConstraint constraint : constraints.values()) {
        if ("not_set".equals(constraint.getDataSource())) {
          // Tag based on whether design data was provided and whether the constraint has a
          // meaningful design value
          if (hasDesignInput) {
            constraint.setDataSource(DATA_SOURCE_DESIGN_CAPACITIES);
          } else if (constraint.getDesignValue() > 0
              && constraint.getDesignValue() < Double.MAX_VALUE) {
            constraint.setDataSource(DATA_SOURCE_EQUIPMENT);
          }
        }
      }
    }
  }

  /**
   * Applies separator design capacity properties.
   *
   * @param sep the separator
   * @param props JSON properties
   * @param result the apply result to populate
   */
  private static void applySeparatorDesign(Separator sep, JsonObject props, ApplyResult result) {
    if (props.has("internalDiameter")) {
      double value = props.get("internalDiameter").getAsDouble();
      if (value > 0) {
        sep.setInternalDiameter(value);
        result.addApplied("internalDiameter", value, "m");
      }
    }

    if (props.has("separatorLength")) {
      double value = props.get("separatorLength").getAsDouble();
      if (value > 0) {
        sep.setSeparatorLength(value);
        result.addApplied("separatorLength", value, "m");
      }
    }

    if (props.has("designGasLoadFactor")) {
      double value = props.get("designGasLoadFactor").getAsDouble();
      if (value > 0) {
        sep.setDesignGasLoadFactor(value);
        result.addApplied("designGasLoadFactor", value, "m/s");
      }
    }
  }

  /**
   * Applies compressor design capacity properties.
   *
   * @param comp the compressor
   * @param props JSON properties
   * @param result the apply result to populate
   */
  private static void applyCompressorDesign(Compressor comp, JsonObject props,
      ApplyResult result) {
    if (props.has("maxSpeed")) {
      double value = props.get("maxSpeed").getAsDouble();
      if (value > 0) {
        comp.setMaximumSpeed(value);
        result.addApplied("maxSpeed", value, "RPM");
      }
    }

    if (props.has("ratedPower")) {
      double value = props.get("ratedPower").getAsDouble();
      if (value > 0) {
        // ratedPower in kW — set on driver or mechanical design
        if (comp.getDriver() != null) {
          comp.getDriver().setRatedPower(value);
        } else {
          comp.getMechanicalDesign().maxDesignPower = value;
        }
        result.addApplied("ratedPower", value, "kW");
      }
    }
  }

  /**
   * Applies heater/cooler design capacity properties.
   *
   * @param heater the heater
   * @param props JSON properties
   * @param result the apply result to populate
   */
  private static void applyHeaterDesign(Heater heater, JsonObject props, ApplyResult result) {
    if (props.has("maxDesignDutyMW")) {
      double valueMW = props.get("maxDesignDutyMW").getAsDouble();
      if (valueMW > 0) {
        heater.setMaxDesignDuty(valueMW, "MW");
        result.addApplied("maxDesignDuty", valueMW, "MW");
      }
    } else if (props.has("maxDesignDutyKW")) {
      double valueKW = props.get("maxDesignDutyKW").getAsDouble();
      if (valueKW > 0) {
        heater.setMaxDesignDuty(valueKW, "kW");
        result.addApplied("maxDesignDuty", valueKW, "kW");
      }
    } else if (props.has("maxDesignDuty")) {
      double valueW = props.get("maxDesignDuty").getAsDouble();
      if (valueW > 0) {
        heater.setMaxDesignDuty(valueW);
        result.addApplied("maxDesignDuty", valueW, "W");
      }
    }
  }

  /**
   * Applies pump design capacity properties.
   *
   * @param pump the pump
   * @param props JSON properties
   * @param result the apply result to populate
   */
  private static void applyPumpDesign(Pump pump, JsonObject props, ApplyResult result) {
    if (props.has("maxDesignPower")) {
      double value = props.get("maxDesignPower").getAsDouble();
      if (value > 0) {
        pump.getMechanicalDesign().maxDesignPower = value * 1000.0; // kW to W
        result.addApplied("maxDesignPower", value, "kW");
      }
    }

    if (props.has("maxDesignVolumeFlow")) {
      double value = props.get("maxDesignVolumeFlow").getAsDouble();
      if (value > 0) {
        pump.getMechanicalDesign().setMaxDesignVolumeFlow(value);
        result.addApplied("maxDesignVolumeFlow", value, "m3/hr");
      }
    }
  }

  /**
   * Finds equipment by name in a process system.
   *
   * @param process the process system
   * @param name the equipment name
   * @return the equipment or null if not found
   */
  private static ProcessEquipmentInterface findEquipment(ProcessSystem process, String name) {
    for (int i = 0; i < process.getUnitOperations().size(); i++) {
      ProcessEquipmentInterface equip = process.getUnitOperations().get(i);
      if (name.equals(equip.getName())) {
        return equip;
      }
    }
    return null;
  }

  /**
   * Result of applying design capacity data to a single piece of equipment.
   *
   * @author ESOL
   * @version 1.0
   */
  public static class ApplyResult {
    /** Equipment name. */
    public final String equipmentName;

    /** Status: "applied", "not_found", "unsupported", "skipped". */
    public String status;

    /** Additional message or error detail. */
    public String message;

    /** List of properties that were applied. */
    public final List<AppliedProperty> appliedProperties;

    /**
     * Creates a new apply result.
     *
     * @param equipmentName the equipment name
     * @param status the status
     * @param message the message
     */
    public ApplyResult(String equipmentName, String status, String message) {
      this.equipmentName = equipmentName;
      this.status = status;
      this.message = message;
      this.appliedProperties = new ArrayList<AppliedProperty>();
    }

    /**
     * Records a property that was applied.
     *
     * @param property the property name
     * @param value the value
     * @param unit the unit
     */
    public void addApplied(String property, double value, String unit) {
      appliedProperties.add(new AppliedProperty(property, value, unit));
    }

    /**
     * Converts this result to a JSON object.
     *
     * @return the JSON representation
     */
    public JsonObject toJson() {
      JsonObject json = new JsonObject();
      json.addProperty("equipmentName", equipmentName);
      json.addProperty("status", status);
      if (message != null && !message.isEmpty()) {
        json.addProperty("message", message);
      }
      if (!appliedProperties.isEmpty()) {
        JsonObject applied = new JsonObject();
        for (AppliedProperty prop : appliedProperties) {
          JsonObject propJson = new JsonObject();
          propJson.addProperty("value", prop.value);
          propJson.addProperty("unit", prop.unit);
          applied.add(prop.property, propJson);
        }
        json.add("appliedProperties", applied);
      }
      return json;
    }
  }

  /**
   * A single applied design property.
   *
   * @author ESOL
   * @version 1.0
   */
  public static class AppliedProperty {
    /** Property name. */
    public final String property;

    /** Value that was set. */
    public final double value;

    /** Unit of the value. */
    public final String unit;

    /**
     * Creates a new applied property record.
     *
     * @param property the property name
     * @param value the value
     * @param unit the unit
     */
    public AppliedProperty(String property, double value, String unit) {
      this.property = property;
      this.value = value;
      this.unit = unit;
    }
  }
}
