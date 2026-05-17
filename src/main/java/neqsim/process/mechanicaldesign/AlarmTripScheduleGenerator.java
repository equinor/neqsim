package neqsim.process.mechanicaldesign;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.processmodel.ProcessSystem;

/**
 * Auto-generates alarm and trip setpoints from process design envelopes.
 *
 * <p>
 * Walks all unit operations in a {@link ProcessSystem} and generates alarm/trip setpoints based on
 * operating conditions and design margins per IEC 61511 and NORSOK I-001/I-002 practice. For each
 * measurable variable (pressure, temperature, level, flow), the generator produces LO, HI, and
 * HIHI/LOLO setpoints from operating values and equipment design limits.
 * </p>
 *
 * <p>
 * Usage:
 * </p>
 *
 * <pre>
 * AlarmTripScheduleGenerator gen = new AlarmTripScheduleGenerator(process);
 * gen.generate();
 * String json = gen.toJson();
 * </pre>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class AlarmTripScheduleGenerator implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** The process system. */
  private ProcessSystem processSystem;

  /** Generated alarm/trip entries. */
  private List<AlarmTripEntry> entries = new ArrayList<AlarmTripEntry>();

  /** Service type classification. */
  public enum ServiceType {
    /** Pressure measurement. */
    PRESSURE,
    /** Temperature measurement. */
    TEMPERATURE,
    /** Level measurement. */
    LEVEL,
    /** Flow measurement. */
    FLOW
  }

  /** Alarm priority classification. */
  public enum AlarmPriority {
    /** Low priority. */
    LOW,
    /** Medium priority. */
    MEDIUM,
    /** High priority. */
    HIGH,
    /** Emergency / safety critical. */
    EMERGENCY
  }

  /**
   * Creates an alarm/trip schedule generator.
   *
   * @param processSystem the process system to analyze
   */
  public AlarmTripScheduleGenerator(ProcessSystem processSystem) {
    this.processSystem = processSystem;
  }

  /**
   * Generates alarm and trip setpoints for all applicable equipment.
   */
  public void generate() {
    entries.clear();

    for (int i = 0; i < processSystem.getUnitOperations().size(); i++) {
      ProcessEquipmentInterface equip = processSystem.getUnitOperations().get(i);

      if (equip instanceof Separator) {
        generateSeparatorAlarms((Separator) equip);
      } else if (equip instanceof Compressor) {
        generateCompressorAlarms((Compressor) equip);
      } else if (equip instanceof Heater) {
        generateHeaterAlarms((Heater) equip);
      } else if (equip instanceof ThrottlingValve) {
        generateValveAlarms((ThrottlingValve) equip);
      }
    }
  }

  /**
   * Generates alarms for a separator.
   *
   * @param sep the separator
   */
  private void generateSeparatorAlarms(Separator sep) {
    String tag = sep.getName();
    double opPressure = sep.getFluid() != null ? sep.getFluid().getPressure() : 0;

    // Pressure alarms
    if (opPressure > 0) {
      entries.add(new AlarmTripEntry(tag, "PT", ServiceType.PRESSURE, "LO", opPressure * 0.85,
          "bara", AlarmPriority.MEDIUM, "Alarm", "Low pressure warning"));
      entries.add(new AlarmTripEntry(tag, "PT", ServiceType.PRESSURE, "HI", opPressure * 1.05,
          "bara", AlarmPriority.HIGH, "Alarm", "High pressure alarm"));
      entries.add(new AlarmTripEntry(tag, "PT", ServiceType.PRESSURE, "HIHI", opPressure * 1.10,
          "bara", AlarmPriority.EMERGENCY, "Trip", "High-high pressure trip (ESD)"));
    }

    // Level alarms (percentage of normal operating level)
    entries.add(new AlarmTripEntry(tag, "LT", ServiceType.LEVEL, "LOLO", 10.0, "%",
        AlarmPriority.EMERGENCY, "Trip", "Low-low level trip - pump protection"));
    entries.add(new AlarmTripEntry(tag, "LT", ServiceType.LEVEL, "LO", 25.0, "%",
        AlarmPriority.MEDIUM, "Alarm", "Low level alarm"));
    entries.add(new AlarmTripEntry(tag, "LT", ServiceType.LEVEL, "HI", 75.0, "%",
        AlarmPriority.HIGH, "Alarm", "High level alarm"));
    entries.add(new AlarmTripEntry(tag, "LT", ServiceType.LEVEL, "HIHI", 90.0, "%",
        AlarmPriority.EMERGENCY, "Trip", "High-high level trip - liquid carryover"));
  }

  /**
   * Generates alarms for a compressor.
   *
   * @param comp the compressor
   */
  private void generateCompressorAlarms(Compressor comp) {
    String tag = comp.getName();

    // Suction pressure
    double suctionP = 0;
    if (comp.getInletStream() != null && comp.getInletStream().getFluid() != null) {
      suctionP = comp.getInletStream().getFluid().getPressure();
    }
    if (suctionP > 0) {
      entries.add(new AlarmTripEntry(tag, "PT-suction", ServiceType.PRESSURE, "LO", suctionP * 0.90,
          "bara", AlarmPriority.HIGH, "Alarm", "Low suction pressure"));
      entries
          .add(new AlarmTripEntry(tag, "PT-suction", ServiceType.PRESSURE, "LOLO", suctionP * 0.80,
              "bara", AlarmPriority.EMERGENCY, "Trip", "Low-low suction pressure trip"));
    }

    // Discharge pressure
    double dischargeP = 0;
    if (comp.getOutletStream() != null && comp.getOutletStream().getFluid() != null) {
      dischargeP = comp.getOutletStream().getFluid().getPressure();
    }
    if (dischargeP > 0) {
      entries.add(new AlarmTripEntry(tag, "PT-discharge", ServiceType.PRESSURE, "HI",
          dischargeP * 1.05, "bara", AlarmPriority.HIGH, "Alarm", "High discharge pressure"));
      entries.add(
          new AlarmTripEntry(tag, "PT-discharge", ServiceType.PRESSURE, "HIHI", dischargeP * 1.10,
              "bara", AlarmPriority.EMERGENCY, "Trip", "High-high discharge pressure trip"));
    }

    // Discharge temperature
    double dischargeTempC = 0;
    if (comp.getOutletStream() != null) {
      dischargeTempC = comp.getOutletStream().getTemperature() - 273.15;
    }
    if (dischargeTempC > 0) {
      entries.add(new AlarmTripEntry(tag, "TT-discharge", ServiceType.TEMPERATURE, "HI",
          dischargeTempC + 15.0, "degC", AlarmPriority.HIGH, "Alarm",
          "High discharge temperature"));
      entries.add(new AlarmTripEntry(tag, "TT-discharge", ServiceType.TEMPERATURE, "HIHI",
          dischargeTempC + 30.0, "degC", AlarmPriority.EMERGENCY, "Trip",
          "High-high discharge temperature trip"));
    }
  }

  /**
   * Generates alarms for a heater or cooler.
   *
   * @param heater the heater or cooler
   */
  private void generateHeaterAlarms(Heater heater) {
    String tag = heater.getName();
    double outTempC = 0;
    if (heater.getOutletStream() != null) {
      outTempC = heater.getOutletStream().getTemperature() - 273.15;
    }
    if (outTempC > 0) {
      entries.add(new AlarmTripEntry(tag, "TT-outlet", ServiceType.TEMPERATURE, "LO",
          outTempC - 10.0, "degC", AlarmPriority.MEDIUM, "Alarm", "Low outlet temperature"));
      entries.add(new AlarmTripEntry(tag, "TT-outlet", ServiceType.TEMPERATURE, "HI",
          outTempC + 10.0, "degC", AlarmPriority.HIGH, "Alarm", "High outlet temperature"));
    }
  }

  /**
   * Generates alarms for a valve.
   *
   * @param valve the throttling valve
   */
  private void generateValveAlarms(ThrottlingValve valve) {
    String tag = valve.getName();
    double downstreamP = 0;
    if (valve.getOutletStream() != null && valve.getOutletStream().getFluid() != null) {
      downstreamP = valve.getOutletStream().getFluid().getPressure();
    }
    if (downstreamP > 0) {
      entries.add(new AlarmTripEntry(tag, "PT-downstream", ServiceType.PRESSURE, "LO",
          downstreamP * 0.85, "bara", AlarmPriority.MEDIUM, "Alarm", "Low downstream pressure"));
      entries.add(new AlarmTripEntry(tag, "PT-downstream", ServiceType.PRESSURE, "HI",
          downstreamP * 1.10, "bara", AlarmPriority.HIGH, "Alarm", "High downstream pressure"));
    }
  }

  /**
   * Gets all generated alarm/trip entries.
   *
   * @return list of alarm/trip entries
   */
  public List<AlarmTripEntry> getEntries() {
    return new ArrayList<AlarmTripEntry>(entries);
  }

  /**
   * Gets the total number of generated entries.
   *
   * @return count of entries
   */
  public int getEntryCount() {
    return entries.size();
  }

  /**
   * Gets entries for a specific equipment.
   *
   * @param equipmentName equipment tag name
   * @return filtered entries for that equipment
   */
  public List<AlarmTripEntry> getEntriesForEquipment(String equipmentName) {
    List<AlarmTripEntry> filtered = new ArrayList<AlarmTripEntry>();
    for (AlarmTripEntry e : entries) {
      if (e.getEquipmentTag().equals(equipmentName)) {
        filtered.add(e);
      }
    }
    return filtered;
  }

  /**
   * Exports the alarm/trip schedule to JSON.
   *
   * @return JSON string
   */
  public String toJson() {
    JsonObject root = new JsonObject();
    root.addProperty("totalAlarms", entries.size());

    int alarmCount = 0;
    int tripCount = 0;
    for (AlarmTripEntry e : entries) {
      if ("Trip".equals(e.getActionType())) {
        tripCount++;
      } else {
        alarmCount++;
      }
    }
    JsonObject summary = new JsonObject();
    summary.addProperty("alarms", alarmCount);
    summary.addProperty("trips", tripCount);
    root.add("summary", summary);

    JsonArray arr = new JsonArray();
    for (AlarmTripEntry e : entries) {
      JsonObject o = new JsonObject();
      o.addProperty("equipmentTag", e.getEquipmentTag());
      o.addProperty("instrumentTag", e.getInstrumentTag());
      o.addProperty("serviceType", e.getServiceType().name());
      o.addProperty("setpointType", e.getSetpointType());
      o.addProperty("setpointValue", e.getSetpointValue());
      o.addProperty("unit", e.getUnit());
      o.addProperty("priority", e.getPriority().name());
      o.addProperty("actionType", e.getActionType());
      o.addProperty("description", e.getDescription());
      arr.add(o);
    }
    root.add("alarmSchedule", arr);

    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create()
        .toJson(root);
  }

  /**
   * Represents a single alarm or trip setpoint entry.
   *
   * @author esol
   * @version 1.0
   */
  public static class AlarmTripEntry implements Serializable {
    private static final long serialVersionUID = 1000L;
    private String equipmentTag;
    private String instrumentTag;
    private ServiceType serviceType;
    private String setpointType;
    private double setpointValue;
    private String unit;
    private AlarmPriority priority;
    private String actionType;
    private String description;

    /**
     * Creates an alarm/trip entry.
     *
     * @param equipmentTag equipment tag name
     * @param instrumentTag instrument tag (e.g. PT, TT, LT, FT)
     * @param serviceType service type enum
     * @param setpointType setpoint category (LO, HI, LOLO, HIHI)
     * @param setpointValue numeric setpoint value
     * @param unit engineering unit
     * @param priority alarm priority
     * @param actionType action (Alarm or Trip)
     * @param description textual description
     */
    public AlarmTripEntry(String equipmentTag, String instrumentTag, ServiceType serviceType,
        String setpointType, double setpointValue, String unit, AlarmPriority priority,
        String actionType, String description) {
      this.equipmentTag = equipmentTag;
      this.instrumentTag = instrumentTag;
      this.serviceType = serviceType;
      this.setpointType = setpointType;
      this.setpointValue = setpointValue;
      this.unit = unit;
      this.priority = priority;
      this.actionType = actionType;
      this.description = description;
    }

    /**
     * Gets the equipment tag.
     *
     * @return equipment tag name
     */
    public String getEquipmentTag() {
      return equipmentTag;
    }

    /**
     * Gets the instrument tag.
     *
     * @return instrument tag
     */
    public String getInstrumentTag() {
      return instrumentTag;
    }

    /**
     * Gets the service type.
     *
     * @return service type enum
     */
    public ServiceType getServiceType() {
      return serviceType;
    }

    /**
     * Gets the setpoint type.
     *
     * @return setpoint type string (LO, HI, LOLO, HIHI)
     */
    public String getSetpointType() {
      return setpointType;
    }

    /**
     * Gets the setpoint value.
     *
     * @return numeric setpoint
     */
    public double getSetpointValue() {
      return setpointValue;
    }

    /**
     * Gets the engineering unit.
     *
     * @return unit string
     */
    public String getUnit() {
      return unit;
    }

    /**
     * Gets the alarm priority.
     *
     * @return priority enum
     */
    public AlarmPriority getPriority() {
      return priority;
    }

    /**
     * Gets the action type.
     *
     * @return action type string ("Alarm" or "Trip")
     */
    public String getActionType() {
      return actionType;
    }

    /**
     * Gets the description.
     *
     * @return description text
     */
    public String getDescription() {
      return description;
    }
  }
}
