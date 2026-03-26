package neqsim.process.mechanicaldesign;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import neqsim.process.alarm.AlarmConfig;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.separator.ThreePhaseSeparator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.measurementdevice.LevelTransmitter;
import neqsim.process.measurementdevice.MeasurementDeviceInterface;
import neqsim.process.measurementdevice.PressureTransmitter;
import neqsim.process.measurementdevice.TemperatureTransmitter;
import neqsim.process.measurementdevice.VolumeFlowTransmitter;
import neqsim.process.processmodel.ProcessSystem;

/**
 * Generates an instrument schedule (instrument index) from a process simulation and optionally
 * registers live measurement devices on the {@link ProcessSystem}.
 *
 * <p>
 * This class bridges the gap between engineering deliverables and dynamic simulation
 * instrumentation by:
 * </p>
 * <ul>
 * <li>Walking all equipment in the process and determining required measurements</li>
 * <li>Assigning ISA-5.1 compliant instrument tag numbers (PT-101, TT-201, LT-301, FT-401)</li>
 * <li>Creating real {@link MeasurementDeviceInterface} objects connected to process streams</li>
 * <li>Configuring {@link AlarmConfig} thresholds derived from operating conditions</li>
 * <li>Optionally registering the instruments on the ProcessSystem for transient simulation</li>
 * <li>Producing a complete instrument schedule as JSON for engineering deliverables</li>
 * </ul>
 *
 * <p>
 * The generated instruments are simulation-ready: they can be used with
 * {@link neqsim.process.controllerdevice.ControllerDeviceBaseClass} for PID control, connected to
 * plant historian data via tags, or evaluated during transient runs.
 * </p>
 *
 * <p>
 * Usage:
 * </p>
 *
 * <pre>
 * InstrumentScheduleGenerator gen = new InstrumentScheduleGenerator(process);
 * gen.setRegisterOnProcess(true); // instruments become live in the simulation
 * gen.generate();
 * String json = gen.toJson();
 * List&lt;MeasurementDeviceInterface&gt; devices = gen.getCreatedDevices();
 * </pre>
 *
 * @author Even Solbraa
 * @version 1.0
 * @see AlarmConfig
 * @see MeasurementDeviceInterface
 * @see neqsim.process.util.DynamicProcessHelper
 */
public class InstrumentScheduleGenerator implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** ISA tag series starting numbers for each measurement type. */
  private static final int PRESSURE_TAG_START = 100;
  private static final int TEMPERATURE_TAG_START = 200;
  private static final int LEVEL_TAG_START = 300;
  private static final int FLOW_TAG_START = 400;

  /** The process system to instrument. */
  private final ProcessSystem processSystem;

  /** Whether to register created devices on the ProcessSystem. */
  private boolean registerOnProcess = false;

  /** Generated instrument entries. */
  private final List<InstrumentEntry> entries = new ArrayList<InstrumentEntry>();

  /** Created measurement device objects keyed by tag number. */
  private final Map<String, MeasurementDeviceInterface> createdDevices =
      new LinkedHashMap<String, MeasurementDeviceInterface>();

  /** Tag counters per type. */
  private int pressureTagNum = PRESSURE_TAG_START;
  private int temperatureTagNum = TEMPERATURE_TAG_START;
  private int levelTagNum = LEVEL_TAG_START;
  private int flowTagNum = FLOW_TAG_START;

  /** Whether generation has been run. */
  private boolean generated = false;

  /**
   * Measurement variable classification per ISA-5.1.
   */
  public enum MeasuredVariable {
    /** Pressure measurement. */
    PRESSURE("PT", "Pressure Transmitter", "AI"),
    /** Temperature measurement. */
    TEMPERATURE("TT", "Temperature Transmitter", "AI"),
    /** Level measurement. */
    LEVEL("LT", "Level Transmitter", "AI"),
    /** Flow measurement. */
    FLOW("FT", "Flow Transmitter", "AI");

    private final String tagPrefix;
    private final String instrumentType;
    private final String ioType;

    /**
     * Constructor.
     *
     * @param tagPrefix ISA-5.1 prefix (PT, TT, LT, FT)
     * @param instrumentType human-readable instrument type
     * @param ioType I/O type (AI, AO, DI, DO)
     */
    MeasuredVariable(String tagPrefix, String instrumentType, String ioType) {
      this.tagPrefix = tagPrefix;
      this.instrumentType = instrumentType;
      this.ioType = ioType;
    }

    /**
     * Get the ISA-5.1 tag prefix.
     *
     * @return tag prefix
     */
    public String getTagPrefix() {
      return tagPrefix;
    }

    /**
     * Get the instrument type description.
     *
     * @return instrument type
     */
    public String getInstrumentType() {
      return instrumentType;
    }

    /**
     * Get the I/O type.
     *
     * @return I/O type (AI, AO, DI, DO)
     */
    public String getIoType() {
      return ioType;
    }
  }

  /**
   * SIL rating classification per IEC 61508 / IEC 61511.
   */
  public enum SilRating {
    /** No SIL requirement. */
    NONE("None"),
    /** Safety Integrity Level 1. */
    SIL_1("SIL 1"),
    /** Safety Integrity Level 2. */
    SIL_2("SIL 2"),
    /** Safety Integrity Level 3. */
    SIL_3("SIL 3");

    private final String displayName;

    /**
     * Constructor.
     *
     * @param displayName display name
     */
    SilRating(String displayName) {
      this.displayName = displayName;
    }

    /**
     * Get the display name.
     *
     * @return display name
     */
    public String getDisplayName() {
      return displayName;
    }
  }

  /**
   * Creates an instrument schedule generator for the given process system.
   *
   * @param processSystem the process system (must have been run)
   * @throws IllegalArgumentException if processSystem is null
   */
  public InstrumentScheduleGenerator(ProcessSystem processSystem) {
    if (processSystem == null) {
      throw new IllegalArgumentException("processSystem must not be null");
    }
    this.processSystem = processSystem;
  }

  /**
   * Sets whether created measurement devices should be registered on the ProcessSystem. When true,
   * instruments become live and participate in transient simulations.
   *
   * @param registerOnProcess true to add devices to the process
   */
  public void setRegisterOnProcess(boolean registerOnProcess) {
    this.registerOnProcess = registerOnProcess;
  }

  /**
   * Gets whether instruments will be registered on the process.
   *
   * @return true if instruments are registered on the process
   */
  public boolean isRegisterOnProcess() {
    return registerOnProcess;
  }

  /**
   * Generates the instrument schedule by walking all equipment in the process system. Creates
   * measurement devices with ISA-5.1 tags and alarm configurations.
   */
  public void generate() {
    entries.clear();
    createdDevices.clear();
    pressureTagNum = PRESSURE_TAG_START;
    temperatureTagNum = TEMPERATURE_TAG_START;
    levelTagNum = LEVEL_TAG_START;
    flowTagNum = FLOW_TAG_START;

    for (int i = 0; i < processSystem.getUnitOperations().size(); i++) {
      ProcessEquipmentInterface equip = processSystem.getUnitOperations().get(i);

      if (equip instanceof Separator) {
        instrumentSeparator((Separator) equip);
      } else if (equip instanceof Compressor) {
        instrumentCompressor((Compressor) equip);
      } else if (equip instanceof Heater) {
        instrumentHeater((Heater) equip);
      } else if (equip instanceof Cooler) {
        instrumentCooler((Cooler) equip);
      } else if (equip instanceof ThrottlingValve) {
        instrumentValve((ThrottlingValve) equip);
      } else if (equip instanceof Stream) {
        instrumentStream((Stream) equip);
      }
    }

    generated = true;
  }

  /**
   * Instruments a separator with PT, TT, LT (and FT on gas outlet).
   *
   * @param sep the separator
   */
  private void instrumentSeparator(Separator sep) {
    String equipTag = sep.getName();
    StreamInterface gasOut = sep.getGasOutStream();
    StreamInterface liqOut = sep.getLiquidOutStream();

    // Pressure transmitter on gas outlet
    if (gasOut != null && gasOut.getFluid() != null) {
      double opP = gasOut.getFluid().getPressure("bara");
      if (opP > 0) {
        String tag = nextTag(MeasuredVariable.PRESSURE);
        PressureTransmitter pt = new PressureTransmitter(tag, gasOut);
        pt.setUnit("bara");
        pt.setTag(tag);
        pt.setMaximumValue(opP * 1.5);
        pt.setMinimumValue(0.0);
        configureAlarm(pt, opP * 0.85, opP * 0.80, opP * 1.05, opP * 1.10, "bara");
        registerDevice(tag, pt);
        entries.add(new InstrumentEntry(tag, equipTag, equipTag + " pressure",
            MeasuredVariable.PRESSURE, "bara", 0.0, opP * 1.5, opP, opP * 0.85, opP * 1.05,
            opP * 0.80, opP * 1.10, SilRating.SIL_2, pt));
      }
    }

    // Temperature transmitter on gas outlet
    if (gasOut != null && gasOut.getFluid() != null) {
      double opTC = gasOut.getTemperature() - 273.15;
      String tag = nextTag(MeasuredVariable.TEMPERATURE);
      TemperatureTransmitter tt = new TemperatureTransmitter(tag, gasOut);
      tt.setUnit("C");
      tt.setTag(tag);
      tt.setMaximumValue(opTC + 100.0);
      tt.setMinimumValue(-50.0);
      configureAlarm(tt, opTC - 20.0, opTC - 40.0, opTC + 20.0, opTC + 40.0, "C");
      registerDevice(tag, tt);
      entries.add(new InstrumentEntry(tag, equipTag, equipTag + " temperature",
          MeasuredVariable.TEMPERATURE, "C", -50.0, opTC + 100.0, opTC, opTC - 20.0, opTC + 20.0,
          opTC - 40.0, opTC + 40.0, SilRating.NONE, tt));
    }

    // Level transmitter on vessel
    String ltTag = nextTag(MeasuredVariable.LEVEL);
    LevelTransmitter lt = new LevelTransmitter(ltTag, sep);
    lt.setTag(ltTag);
    lt.setMaximumValue(1.0);
    lt.setMinimumValue(0.0);
    configureAlarm(lt, 0.25, 0.10, 0.75, 0.90, "frac");
    registerDevice(ltTag, lt);
    entries.add(
        new InstrumentEntry(ltTag, equipTag, equipTag + " liquid level", MeasuredVariable.LEVEL,
            "frac", 0.0, 1.0, 0.50, 0.25, 0.75, 0.10, 0.90, SilRating.SIL_2, lt));

    // Water level transmitter for three-phase separators
    if (sep instanceof ThreePhaseSeparator) {
      String wltTag = nextTag(MeasuredVariable.LEVEL);
      LevelTransmitter wlt = new LevelTransmitter(wltTag, sep);
      wlt.setTag(wltTag);
      registerDevice(wltTag, wlt);
      entries.add(
          new InstrumentEntry(wltTag, equipTag, equipTag + " water level", MeasuredVariable.LEVEL,
              "frac", 0.0, 1.0, 0.30, 0.15, 0.60, 0.05, 0.80, SilRating.SIL_1, wlt));
    }

    // Flow transmitter on gas outlet
    if (gasOut != null) {
      String ftTag = nextTag(MeasuredVariable.FLOW);
      VolumeFlowTransmitter ft = new VolumeFlowTransmitter(ftTag, gasOut);
      ft.setUnit("kg/hr");
      ft.setTag(ftTag);
      registerDevice(ftTag, ft);
      double flowRate = gasOut.getFlowRate("kg/hr");
      entries.add(new InstrumentEntry(ftTag, equipTag, equipTag + " gas outlet flow",
          MeasuredVariable.FLOW, "kg/hr", 0.0, flowRate * 1.5, flowRate, flowRate * 0.70,
          flowRate * 1.10, flowRate * 0.50, flowRate * 1.20, SilRating.NONE, ft));
    }
  }

  /**
   * Instruments a compressor with suction/discharge PT and discharge TT.
   *
   * @param comp the compressor
   */
  private void instrumentCompressor(Compressor comp) {
    String equipTag = comp.getName();

    // Suction pressure
    if (comp.getInletStream() != null && comp.getInletStream().getFluid() != null) {
      double suctionP = comp.getInletStream().getFluid().getPressure("bara");
      if (suctionP > 0) {
        String tag = nextTag(MeasuredVariable.PRESSURE);
        PressureTransmitter pt = new PressureTransmitter(tag, comp.getInletStream());
        pt.setUnit("bara");
        pt.setTag(tag);
        configureAlarm(pt, suctionP * 0.90, suctionP * 0.80, suctionP * 1.10, suctionP * 1.15,
            "bara");
        registerDevice(tag, pt);
        entries.add(new InstrumentEntry(tag, equipTag, equipTag + " suction pressure",
            MeasuredVariable.PRESSURE, "bara", 0.0, suctionP * 2.0, suctionP, suctionP * 0.90,
            suctionP * 1.10, suctionP * 0.80, suctionP * 1.15, SilRating.SIL_2, pt));
      }
    }

    // Discharge pressure
    if (comp.getOutletStream() != null && comp.getOutletStream().getFluid() != null) {
      double dischargeP = comp.getOutletStream().getFluid().getPressure("bara");
      if (dischargeP > 0) {
        String tag = nextTag(MeasuredVariable.PRESSURE);
        PressureTransmitter pt = new PressureTransmitter(tag, comp.getOutletStream());
        pt.setUnit("bara");
        pt.setTag(tag);
        configureAlarm(pt, dischargeP * 0.85, dischargeP * 0.80, dischargeP * 1.05,
            dischargeP * 1.10, "bara");
        registerDevice(tag, pt);
        entries.add(new InstrumentEntry(tag, equipTag, equipTag + " discharge pressure",
            MeasuredVariable.PRESSURE, "bara", 0.0, dischargeP * 1.5, dischargeP, dischargeP * 0.85,
            dischargeP * 1.05, dischargeP * 0.80, dischargeP * 1.10, SilRating.SIL_2, pt));
      }
    }

    // Discharge temperature
    if (comp.getOutletStream() != null) {
      double dischTC = comp.getOutletStream().getTemperature() - 273.15;
      String tag = nextTag(MeasuredVariable.TEMPERATURE);
      TemperatureTransmitter tt = new TemperatureTransmitter(tag, comp.getOutletStream());
      tt.setUnit("C");
      tt.setTag(tag);
      configureAlarm(tt, dischTC - 20.0, dischTC - 40.0, dischTC + 15.0, dischTC + 30.0, "C");
      registerDevice(tag, tt);
      entries.add(new InstrumentEntry(tag, equipTag, equipTag + " discharge temperature",
          MeasuredVariable.TEMPERATURE, "C", -50.0, dischTC + 100.0, dischTC, dischTC - 20.0,
          dischTC + 15.0, dischTC - 40.0, dischTC + 30.0, SilRating.SIL_1, tt));
    }
  }

  /**
   * Instruments a heater with outlet TT.
   *
   * @param heater the heater
   */
  private void instrumentHeater(Heater heater) {
    String equipTag = heater.getName();
    if (heater.getOutletStream() == null) {
      return;
    }
    double outTC = heater.getOutletStream().getTemperature() - 273.15;
    String tag = nextTag(MeasuredVariable.TEMPERATURE);
    TemperatureTransmitter tt = new TemperatureTransmitter(tag, heater.getOutletStream());
    tt.setUnit("C");
    tt.setTag(tag);
    configureAlarm(tt, outTC - 10.0, outTC - 20.0, outTC + 10.0, outTC + 20.0, "C");
    registerDevice(tag, tt);
    entries.add(new InstrumentEntry(tag, equipTag, equipTag + " outlet temperature",
        MeasuredVariable.TEMPERATURE, "C", -50.0, outTC + 100.0, outTC, outTC - 10.0, outTC + 10.0,
        outTC - 20.0, outTC + 20.0, SilRating.NONE, tt));
  }

  /**
   * Instruments a cooler with outlet TT.
   *
   * @param cooler the cooler
   */
  private void instrumentCooler(Cooler cooler) {
    String equipTag = cooler.getName();
    if (cooler.getOutletStream() == null) {
      return;
    }
    double outTC = cooler.getOutletStream().getTemperature() - 273.15;
    String tag = nextTag(MeasuredVariable.TEMPERATURE);
    TemperatureTransmitter tt = new TemperatureTransmitter(tag, cooler.getOutletStream());
    tt.setUnit("C");
    tt.setTag(tag);
    configureAlarm(tt, outTC - 10.0, outTC - 20.0, outTC + 10.0, outTC + 20.0, "C");
    registerDevice(tag, tt);
    entries.add(new InstrumentEntry(tag, equipTag, equipTag + " outlet temperature",
        MeasuredVariable.TEMPERATURE, "C", -50.0, outTC + 100.0, outTC, outTC - 10.0, outTC + 10.0,
        outTC - 20.0, outTC + 20.0, SilRating.NONE, tt));
  }

  /**
   * Instruments a valve with downstream PT.
   *
   * @param valve the throttling valve
   */
  private void instrumentValve(ThrottlingValve valve) {
    String equipTag = valve.getName();
    if (valve.getOutletStream() == null || valve.getOutletStream().getFluid() == null) {
      return;
    }
    double downstreamP = valve.getOutletStream().getFluid().getPressure("bara");
    if (downstreamP > 0) {
      String tag = nextTag(MeasuredVariable.PRESSURE);
      PressureTransmitter pt = new PressureTransmitter(tag, valve.getOutletStream());
      pt.setUnit("bara");
      pt.setTag(tag);
      configureAlarm(pt, downstreamP * 0.85, downstreamP * 0.75, downstreamP * 1.10,
          downstreamP * 1.15, "bara");
      registerDevice(tag, pt);
      entries.add(new InstrumentEntry(tag, equipTag, equipTag + " downstream pressure",
          MeasuredVariable.PRESSURE, "bara", 0.0, downstreamP * 2.0, downstreamP,
          downstreamP * 0.85, downstreamP * 1.10, downstreamP * 0.75, downstreamP * 1.15,
          SilRating.NONE, pt));
    }
  }

  /**
   * Instruments a feed stream with PT, TT, and FT.
   *
   * @param stream the stream
   */
  private void instrumentStream(Stream stream) {
    String equipTag = stream.getName();
    if (stream.getFluid() == null) {
      return;
    }

    // Pressure transmitter
    double opP = stream.getFluid().getPressure("bara");
    if (opP > 0) {
      String ptTag = nextTag(MeasuredVariable.PRESSURE);
      PressureTransmitter pt = new PressureTransmitter(ptTag, stream);
      pt.setUnit("bara");
      pt.setTag(ptTag);
      registerDevice(ptTag, pt);
      entries.add(new InstrumentEntry(ptTag, equipTag, equipTag + " pressure",
          MeasuredVariable.PRESSURE, "bara", 0.0, opP * 1.5, opP, opP * 0.90, opP * 1.10,
          opP * 0.80, opP * 1.15, SilRating.NONE, pt));
    }

    // Temperature transmitter
    double opTC = stream.getTemperature() - 273.15;
    String ttTag = nextTag(MeasuredVariable.TEMPERATURE);
    TemperatureTransmitter tt = new TemperatureTransmitter(ttTag, stream);
    tt.setUnit("C");
    tt.setTag(ttTag);
    registerDevice(ttTag, tt);
    entries.add(new InstrumentEntry(ttTag, equipTag, equipTag + " temperature",
        MeasuredVariable.TEMPERATURE, "C", -50.0, opTC + 100.0, opTC, opTC - 15.0, opTC + 15.0,
        opTC - 30.0, opTC + 30.0, SilRating.NONE, tt));

    // Flow transmitter
    String ftTag = nextTag(MeasuredVariable.FLOW);
    VolumeFlowTransmitter ft = new VolumeFlowTransmitter(ftTag, stream);
    ft.setUnit("kg/hr");
    ft.setTag(ftTag);
    registerDevice(ftTag, ft);
    double flowRate = stream.getFlowRate("kg/hr");
    entries.add(new InstrumentEntry(ftTag, equipTag, equipTag + " flow", MeasuredVariable.FLOW,
        "kg/hr", 0.0, flowRate * 1.5, flowRate, flowRate * 0.80, flowRate * 1.10, flowRate * 0.60,
        flowRate * 1.20, SilRating.NONE, ft));
  }

  /**
   * Generates the next ISA-5.1 tag number for the given variable type.
   *
   * @param variable the measured variable type
   * @return tag number string (e.g. "PT-101", "TT-201")
   */
  private String nextTag(MeasuredVariable variable) {
    switch (variable) {
      case PRESSURE:
        return variable.getTagPrefix() + "-" + (pressureTagNum++);
      case TEMPERATURE:
        return variable.getTagPrefix() + "-" + (temperatureTagNum++);
      case LEVEL:
        return variable.getTagPrefix() + "-" + (levelTagNum++);
      case FLOW:
        return variable.getTagPrefix() + "-" + (flowTagNum++);
      default:
        return variable.getTagPrefix() + "-000";
    }
  }

  /**
   * Configures alarm thresholds on a measurement device.
   *
   * @param device the measurement device
   * @param lo low alarm limit
   * @param lolo low-low alarm limit
   * @param hi high alarm limit
   * @param hihi high-high alarm limit
   * @param unit engineering unit
   */
  private void configureAlarm(MeasurementDeviceInterface device, double lo, double lolo, double hi,
      double hihi, String unit) {
    AlarmConfig config = AlarmConfig.builder().lowLimit(lo).lowLowLimit(lolo).highLimit(hi)
        .highHighLimit(hihi).unit(unit).deadband(0.5).delay(3.0).build();
    device.setAlarmConfig(config);
  }

  /**
   * Registers a device in the internal map and optionally on the ProcessSystem.
   *
   * @param tag instrument tag
   * @param device the measurement device
   */
  private void registerDevice(String tag, MeasurementDeviceInterface device) {
    createdDevices.put(tag, device);
    if (registerOnProcess) {
      processSystem.add(device);
    }
  }

  /**
   * Gets all generated instrument entries.
   *
   * @return unmodifiable list of entries
   */
  public List<InstrumentEntry> getEntries() {
    return Collections.unmodifiableList(entries);
  }

  /**
   * Gets the total number of instruments.
   *
   * @return instrument count
   */
  public int getInstrumentCount() {
    return entries.size();
  }

  /**
   * Gets entries for a specific equipment.
   *
   * @param equipmentTag equipment tag name
   * @return filtered list of entries for that equipment
   */
  public List<InstrumentEntry> getEntriesForEquipment(String equipmentTag) {
    List<InstrumentEntry> filtered = new ArrayList<InstrumentEntry>();
    for (InstrumentEntry entry : entries) {
      if (entry.getEquipmentTag().equals(equipmentTag)) {
        filtered.add(entry);
      }
    }
    return filtered;
  }

  /**
   * Gets entries filtered by measured variable type.
   *
   * @param variable the measured variable type
   * @return filtered entries
   */
  public List<InstrumentEntry> getEntriesByType(MeasuredVariable variable) {
    List<InstrumentEntry> filtered = new ArrayList<InstrumentEntry>();
    for (InstrumentEntry entry : entries) {
      if (entry.getMeasuredVariable() == variable) {
        filtered.add(entry);
      }
    }
    return filtered;
  }

  /**
   * Gets a created measurement device by its tag number.
   *
   * @param tag ISA-5.1 tag (e.g. "PT-101")
   * @return the measurement device, or null if not found
   */
  public MeasurementDeviceInterface getDevice(String tag) {
    return createdDevices.get(tag);
  }

  /**
   * Gets all created measurement devices.
   *
   * @return unmodifiable list of devices
   */
  public List<MeasurementDeviceInterface> getCreatedDevices() {
    return Collections
        .unmodifiableList(new ArrayList<MeasurementDeviceInterface>(createdDevices.values()));
  }

  /**
   * Checks whether generation has been run.
   *
   * @return true if generate() has been called
   */
  public boolean isGenerated() {
    return generated;
  }

  /**
   * Gets instrument count by measured variable type.
   *
   * @param variable the measured variable type
   * @return count of instruments of that type
   */
  public int getCountByType(MeasuredVariable variable) {
    int count = 0;
    for (InstrumentEntry entry : entries) {
      if (entry.getMeasuredVariable() == variable) {
        count++;
      }
    }
    return count;
  }

  /**
   * Produces a JSON representation of the complete instrument schedule.
   *
   * @return JSON string
   */
  public String toJson() {
    JsonObject root = new JsonObject();
    root.addProperty("totalInstruments", entries.size());
    root.addProperty("registeredOnProcess", registerOnProcess);

    // Summary by type
    JsonObject summary = new JsonObject();
    summary.addProperty("pressureTransmitters", getCountByType(MeasuredVariable.PRESSURE));
    summary.addProperty("temperatureTransmitters", getCountByType(MeasuredVariable.TEMPERATURE));
    summary.addProperty("levelTransmitters", getCountByType(MeasuredVariable.LEVEL));
    summary.addProperty("flowTransmitters", getCountByType(MeasuredVariable.FLOW));

    int silCount = 0;
    for (InstrumentEntry e : entries) {
      if (e.getSilRating() != SilRating.NONE) {
        silCount++;
      }
    }
    summary.addProperty("safetyInstruments", silCount);
    root.add("summary", summary);

    // I/O count
    JsonObject ioCount = new JsonObject();
    int ai = 0;
    int ao = 0;
    int di = 0;
    for (InstrumentEntry e : entries) {
      String io = e.getMeasuredVariable().getIoType();
      if ("AI".equals(io)) {
        ai++;
      } else if ("AO".equals(io)) {
        ao++;
      } else if ("DI".equals(io)) {
        di++;
      }
    }
    ioCount.addProperty("AI", ai);
    ioCount.addProperty("AO", ao);
    ioCount.addProperty("DI", di);
    root.add("ioCount", ioCount);

    // Instrument schedule
    JsonArray arr = new JsonArray();
    for (InstrumentEntry e : entries) {
      JsonObject obj = new JsonObject();
      obj.addProperty("tagNumber", e.getTagNumber());
      obj.addProperty("equipmentTag", e.getEquipmentTag());
      obj.addProperty("serviceDescription", e.getServiceDescription());
      obj.addProperty("instrumentType", e.getMeasuredVariable().getInstrumentType());
      obj.addProperty("measuredVariable", e.getMeasuredVariable().name());
      obj.addProperty("unit", e.getUnit());
      obj.addProperty("rangeMin", e.getRangeMin());
      obj.addProperty("rangeMax", e.getRangeMax());
      obj.addProperty("normalValue", e.getNormalValue());
      obj.addProperty("alarmLow", e.getAlarmLow());
      obj.addProperty("alarmHigh", e.getAlarmHigh());
      obj.addProperty("tripLow", e.getTripLow());
      obj.addProperty("tripHigh", e.getTripHigh());
      obj.addProperty("silRating", e.getSilRating().getDisplayName());
      obj.addProperty("ioType", e.getMeasuredVariable().getIoType());
      obj.addProperty("hasLiveDevice", e.getDevice() != null);
      arr.add(obj);
    }
    root.add("instrumentSchedule", arr);

    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create()
        .toJson(root);
  }

  /**
   * Represents a single instrument in the instrument schedule.
   *
   * @author esol
   * @version 1.0
   */
  public static class InstrumentEntry implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final String tagNumber;
    private final String equipmentTag;
    private final String serviceDescription;
    private final MeasuredVariable measuredVariable;
    private final String unit;
    private final double rangeMin;
    private final double rangeMax;
    private final double normalValue;
    private final double alarmLow;
    private final double alarmHigh;
    private final double tripLow;
    private final double tripHigh;
    private final SilRating silRating;
    private final transient MeasurementDeviceInterface device;

    /**
     * Creates an instrument entry.
     *
     * @param tagNumber ISA-5.1 tag number
     * @param equipmentTag associated equipment tag
     * @param serviceDescription service description
     * @param measuredVariable measured variable type
     * @param unit engineering unit
     * @param rangeMin instrument range minimum
     * @param rangeMax instrument range maximum
     * @param normalValue normal operating value
     * @param alarmLow low alarm setpoint
     * @param alarmHigh high alarm setpoint
     * @param tripLow low-low trip setpoint
     * @param tripHigh high-high trip setpoint
     * @param silRating SIL rating
     * @param device the live measurement device (may be null)
     */
    public InstrumentEntry(String tagNumber, String equipmentTag, String serviceDescription,
        MeasuredVariable measuredVariable, String unit, double rangeMin, double rangeMax,
        double normalValue, double alarmLow, double alarmHigh, double tripLow, double tripHigh,
        SilRating silRating, MeasurementDeviceInterface device) {
      this.tagNumber = tagNumber;
      this.equipmentTag = equipmentTag;
      this.serviceDescription = serviceDescription;
      this.measuredVariable = measuredVariable;
      this.unit = unit;
      this.rangeMin = rangeMin;
      this.rangeMax = rangeMax;
      this.normalValue = normalValue;
      this.alarmLow = alarmLow;
      this.alarmHigh = alarmHigh;
      this.tripLow = tripLow;
      this.tripHigh = tripHigh;
      this.silRating = silRating;
      this.device = device;
    }

    /**
     * Gets the ISA-5.1 tag number.
     *
     * @return tag number (e.g. "PT-101")
     */
    public String getTagNumber() {
      return tagNumber;
    }

    /**
     * Gets the associated equipment tag.
     *
     * @return equipment tag
     */
    public String getEquipmentTag() {
      return equipmentTag;
    }

    /**
     * Gets the service description.
     *
     * @return service description
     */
    public String getServiceDescription() {
      return serviceDescription;
    }

    /**
     * Gets the measured variable type.
     *
     * @return measured variable
     */
    public MeasuredVariable getMeasuredVariable() {
      return measuredVariable;
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
     * Gets the instrument range minimum.
     *
     * @return range minimum
     */
    public double getRangeMin() {
      return rangeMin;
    }

    /**
     * Gets the instrument range maximum.
     *
     * @return range maximum
     */
    public double getRangeMax() {
      return rangeMax;
    }

    /**
     * Gets the normal operating value.
     *
     * @return normal value
     */
    public double getNormalValue() {
      return normalValue;
    }

    /**
     * Gets the low alarm setpoint.
     *
     * @return low alarm value
     */
    public double getAlarmLow() {
      return alarmLow;
    }

    /**
     * Gets the high alarm setpoint.
     *
     * @return high alarm value
     */
    public double getAlarmHigh() {
      return alarmHigh;
    }

    /**
     * Gets the low-low trip setpoint.
     *
     * @return trip low value
     */
    public double getTripLow() {
      return tripLow;
    }

    /**
     * Gets the high-high trip setpoint.
     *
     * @return trip high value
     */
    public double getTripHigh() {
      return tripHigh;
    }

    /**
     * Gets the SIL rating.
     *
     * @return SIL rating
     */
    public SilRating getSilRating() {
      return silRating;
    }

    /**
     * Gets the live measurement device. May be null if not created.
     *
     * @return the measurement device, or null
     */
    public MeasurementDeviceInterface getDevice() {
      return device;
    }
  }
}
