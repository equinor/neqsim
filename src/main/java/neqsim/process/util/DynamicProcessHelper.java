package neqsim.process.util;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import neqsim.process.controllerdevice.ControllerDeviceBaseClass;
import neqsim.process.controllerdevice.ControllerDeviceInterface;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.separator.ThreePhaseSeparator;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.measurementdevice.LevelTransmitter;
import neqsim.process.measurementdevice.MeasurementDeviceInterface;
import neqsim.process.measurementdevice.PressureTransmitter;
import neqsim.process.measurementdevice.TemperatureTransmitter;
import neqsim.process.measurementdevice.VolumeFlowTransmitter;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.processmodel.dexpi.DexpiInstrumentInfo;
import neqsim.process.processmodel.dexpi.DexpiXmlReader;
import neqsim.process.processmodel.dexpi.DexpiXmlReaderException;
import neqsim.process.processmodel.dexpi.DexpiXmlWriter;

/**
 * Helper that converts a sized steady-state {@link ProcessSystem} into a dynamic simulation by
 * auto-creating transmitters and PID controllers with sensible defaults. The generated instruments
 * and controllers are added to the process and wired to the appropriate equipment.
 *
 * <p>
 * Typical usage:
 * </p>
 *
 * <pre>
 * // 1. Build and run steady-state process
 * ProcessSystem process = new ProcessSystem();
 * process.add(feed);
 * process.add(valve);
 * process.add(separator);
 * process.add(gasValve);
 * process.add(liqValve);
 * process.run();
 *
 * // 2. Convert to dynamic in one call
 * DynamicProcessHelper helper = new DynamicProcessHelper(process);
 * helper.setDefaultTimeStep(1.0);
 * helper.instrumentAndControl();
 *
 * // 3. Run transient loop
 * for (int i = 0; i &lt; 600; i++) {
 *   process.runTransient();
 * }
 * </pre>
 *
 * @author NeqSim
 * @version 1.0
 */
public class DynamicProcessHelper {
  private final ProcessSystem process;
  private double defaultTimeStep = 1.0;

  /** Default PID proportional gain for pressure controllers. */
  private double pressureKp = 0.5;
  /** Default PID integral time (seconds) for pressure controllers. */
  private double pressureTi = 50.0;

  /** Default PID proportional gain for level controllers. */
  private double levelKp = 2.0;
  /** Default PID integral time (seconds) for level controllers. */
  private double levelTi = 200.0;

  /** Default PID proportional gain for flow controllers. */
  private double flowKp = 0.2;
  /** Default PID integral time (seconds) for flow controllers. */
  private double flowTi = 100.0;

  /** Default PID proportional gain for temperature controllers. */
  private double temperatureKp = 1.0;
  /** Default PID integral time (seconds) for temperature controllers. */
  private double temperatureTi = 120.0;

  /** Generated transmitters keyed by tag name. */
  private final Map<String, MeasurementDeviceInterface> transmitters =
      new LinkedHashMap<String, MeasurementDeviceInterface>();

  /** Generated controllers keyed by tag name. */
  private final Map<String, ControllerDeviceInterface> controllers =
      new LinkedHashMap<String, ControllerDeviceInterface>();

  /**
   * Creates a helper for the given process system.
   *
   * @param process a {@link ProcessSystem} that has been run in steady-state with sized equipment
   */
  public DynamicProcessHelper(ProcessSystem process) {
    this.process = process;
  }

  /**
   * Sets the transient time step in seconds (default 1.0).
   *
   * @param dt time step in seconds (must be positive)
   */
  public void setDefaultTimeStep(double dt) {
    this.defaultTimeStep = dt;
  }

  /**
   * Gets the default time step.
   *
   * @return time step in seconds
   */
  public double getDefaultTimeStep() {
    return defaultTimeStep;
  }

  /**
   * Sets PID tuning parameters for pressure controllers.
   *
   * @param kp proportional gain
   * @param ti integral time in seconds
   */
  public void setPressureTuning(double kp, double ti) {
    this.pressureKp = kp;
    this.pressureTi = ti;
  }

  /**
   * Sets PID tuning parameters for level controllers.
   *
   * @param kp proportional gain
   * @param ti integral time in seconds
   */
  public void setLevelTuning(double kp, double ti) {
    this.levelKp = kp;
    this.levelTi = ti;
  }

  /**
   * Sets PID tuning parameters for flow controllers.
   *
   * @param kp proportional gain
   * @param ti integral time in seconds
   */
  public void setFlowTuning(double kp, double ti) {
    this.flowKp = kp;
    this.flowTi = ti;
  }

  /**
   * Sets PID tuning parameters for temperature controllers.
   *
   * @param kp proportional gain
   * @param ti integral time in seconds
   */
  public void setTemperatureTuning(double kp, double ti) {
    this.temperatureKp = kp;
    this.temperatureTi = ti;
  }

  /**
   * Scans the process for equipment and auto-creates typical transmitters and PID controllers. Adds
   * them to the process, wires controllers to valves, and switches all equipment to dynamic
   * (non-steady-state) mode.
   *
   * <p>
   * The method handles:
   * </p>
   * <ul>
   * <li>Separators: PT on gas outlet, LT on vessel, controllers on downstream gas/liquid
   * valves</li>
   * <li>ThreePhaseSeparators: additional water LT and controller</li>
   * <li>Compressors: PT and TT on discharge</li>
   * <li>Heaters/Coolers: TT on outlet</li>
   * <li>Valves: FT on inlet if no controller already assigned</li>
   * </ul>
   */
  public void instrumentAndControl() {
    List<ProcessEquipmentInterface> units =
        new ArrayList<ProcessEquipmentInterface>(process.getUnitOperations());

    // Find valves downstream of separators for control
    Map<String, ThrottlingValve> gasValves = new LinkedHashMap<String, ThrottlingValve>();
    Map<String, ThrottlingValve> liquidValves = new LinkedHashMap<String, ThrottlingValve>();
    Map<String, ThrottlingValve> waterValves = new LinkedHashMap<String, ThrottlingValve>();

    // First pass: identify separator-valve pairings by stream identity
    for (ProcessEquipmentInterface unit : units) {
      if (unit instanceof Separator) {
        Separator sep = (Separator) unit;
        StreamInterface gasOut;
        StreamInterface liqOut;
        try {
          gasOut = sep.getGasOutStream();
          liqOut = sep.getLiquidOutStream();
        } catch (Exception ex) {
          // Separator has no inlet wired (e.g. orphan vessel in JSON build) — skip pairing.
          continue;
        }
        StreamInterface waterOut = null;
        if (sep instanceof ThreePhaseSeparator) {
          waterOut = ((ThreePhaseSeparator) sep).getWaterOutStream();
        }

        for (ProcessEquipmentInterface other : units) {
          if (other instanceof ThrottlingValve) {
            ThrottlingValve v = (ThrottlingValve) other;
            StreamInterface vIn = v.getInletStream();
            if (vIn == gasOut) {
              gasValves.put(sep.getName(), v);
            } else if (vIn == liqOut) {
              liquidValves.put(sep.getName(), v);
            } else if (waterOut != null && vIn == waterOut) {
              waterValves.put(sep.getName(), v);
            }
          }
        }
      }
    }

    // Second pass: instrument each equipment type
    for (ProcessEquipmentInterface unit : units) {
      if (unit instanceof Separator) {
        instrumentSeparator((Separator) unit, gasValves, liquidValves, waterValves);
      } else if (unit instanceof Compressor) {
        instrumentCompressor((Compressor) unit);
      } else if (unit instanceof Heater || unit instanceof Cooler) {
        instrumentHeatExchanger(unit);
      }
    }

    // Switch all equipment to dynamic mode
    for (ProcessEquipmentInterface unit : process.getUnitOperations()) {
      unit.setCalculateSteadyState(false);
    }

    process.setTimeStep(defaultTimeStep);
  }

  /**
   * Instruments a separator with pressure and level transmitters and creates PID controllers on
   * downstream valves.
   *
   * @param sep the separator to instrument
   * @param gasValves map of separator name to downstream gas valve
   * @param liquidValves map of separator name to downstream liquid valve
   * @param waterValves map of separator name to downstream water valve
   */
  private void instrumentSeparator(Separator sep, Map<String, ThrottlingValve> gasValves,
      Map<String, ThrottlingValve> liquidValves, Map<String, ThrottlingValve> waterValves) {

    String tag = sep.getName();

    StreamInterface sepGasOut;
    try {
      sepGasOut = sep.getGasOutStream();
    } catch (Exception ex) {
      // Separator was built without an inlet (e.g. orphan vessel in JSON build). Skip
      // dynamic instrumentation rather than NPE — the dynamic step itself will surface
      // a clear IllegalStateException with remediation if/when this separator is run.
      return;
    }

    // Pressure transmitter on gas outlet
    PressureTransmitter pt = new PressureTransmitter("PT-" + tag, sepGasOut);
    pt.setUnit("bara");
    addTransmitter("PT-" + tag, pt);

    // Level transmitter
    LevelTransmitter lt = new LevelTransmitter("LT-" + tag, sep);
    addTransmitter("LT-" + tag, lt);

    // Temperature transmitter on gas outlet
    TemperatureTransmitter tt = new TemperatureTransmitter("TT-" + tag, sepGasOut);
    tt.setUnit("C");
    addTransmitter("TT-" + tag, tt);

    // Pressure controller on gas valve
    ThrottlingValve gasValve = gasValves.get(tag);
    if (gasValve != null && !gasValve.hasController) {
      double setpoint = sepGasOut.getPressure("bara");
      ControllerDeviceInterface pc =
          createPIDController("PC-" + tag, pt, setpoint, pressureKp, pressureTi, 0.0, true);
      gasValve.setController(pc);
      addController("PC-" + tag, pc);
    }

    // Level controller on liquid valve
    ThrottlingValve liqValve = liquidValves.get(tag);
    if (liqValve != null && !liqValve.hasController) {
      double levelSetpoint = sep.getLiquidLevel() * 100.0;
      ControllerDeviceInterface lc =
          createPIDController("LC-" + tag, lt, levelSetpoint, levelKp, levelTi, 0.0, false);
      liqValve.setController(lc);
      addController("LC-" + tag, lc);
    }

    // Three-phase: water level controller
    if (sep instanceof ThreePhaseSeparator) {
      ThrottlingValve waterValve = waterValves.get(tag);
      if (waterValve != null && !waterValve.hasController) {
        ControllerDeviceInterface wlc =
            createPIDController("WLC-" + tag, lt, 50.0, levelKp, levelTi, 0.0, false);
        waterValve.setController(wlc);
        addController("WLC-" + tag, wlc);
      }
    }
  }

  /**
   * Instruments a compressor with pressure and temperature transmitters on the discharge.
   *
   * @param comp the compressor to instrument
   */
  private void instrumentCompressor(Compressor comp) {
    String tag = comp.getName();

    PressureTransmitter pt = new PressureTransmitter("PT-" + tag, comp.getOutletStream());
    pt.setUnit("bara");
    addTransmitter("PT-" + tag, pt);

    TemperatureTransmitter tt = new TemperatureTransmitter("TT-" + tag, comp.getOutletStream());
    tt.setUnit("C");
    addTransmitter("TT-" + tag, tt);
  }

  /**
   * Instruments a heater or cooler with a temperature transmitter on the outlet.
   *
   * @param unit the heater or cooler equipment
   */
  private void instrumentHeatExchanger(ProcessEquipmentInterface unit) {
    String tag = unit.getName();
    StreamInterface outStream = null;
    if (unit instanceof Heater) {
      outStream = ((Heater) unit).getOutletStream();
    } else if (unit instanceof Cooler) {
      outStream = ((Cooler) unit).getOutletStream();
    }
    if (outStream != null) {
      TemperatureTransmitter tt = new TemperatureTransmitter("TT-" + tag, outStream);
      tt.setUnit("C");
      addTransmitter("TT-" + tag, tt);
    }
  }

  /**
   * Creates a PID controller connected to a transmitter with the given tuning.
   *
   * @param name controller tag name
   * @param transmitter measurement input
   * @param setpoint controller set point
   * @param kp proportional gain
   * @param ti integral time in seconds
   * @param td derivative time in seconds
   * @param reverseActing true if controller output increases when measurement is above setpoint
   * @return configured PID controller
   */
  private ControllerDeviceInterface createPIDController(String name,
      MeasurementDeviceInterface transmitter, double setpoint, double kp, double ti, double td,
      boolean reverseActing) {
    ControllerDeviceBaseClass pid = new ControllerDeviceBaseClass(name);
    pid.setTransmitter(transmitter);
    pid.setControllerSetPoint(setpoint);
    pid.setControllerParameters(kp, ti, td);
    pid.setReverseActing(reverseActing);
    return pid;
  }

  /**
   * Adds a transmitter to the internal registry and to the process.
   *
   * @param tag transmitter tag name
   * @param device the transmitter
   */
  private void addTransmitter(String tag, MeasurementDeviceInterface device) {
    transmitters.put(tag, device);
    process.add(device);
  }

  /**
   * Adds a controller to the internal registry.
   *
   * @param tag controller tag name
   * @param controller the controller
   */
  private void addController(String tag, ControllerDeviceInterface controller) {
    controllers.put(tag, controller);
  }

  /**
   * Returns all generated transmitters keyed by tag name (e.g. "PT-HP sep", "LT-HP sep").
   *
   * @return map of tag name to transmitter
   */
  public Map<String, MeasurementDeviceInterface> getTransmitters() {
    return transmitters;
  }

  /**
   * Returns all generated controllers keyed by tag name (e.g. "PC-HP sep", "LC-HP sep").
   *
   * @return map of tag name to controller
   */
  public Map<String, ControllerDeviceInterface> getControllers() {
    return controllers;
  }

  /**
   * Gets a specific transmitter by tag name.
   *
   * @param tag the transmitter tag (e.g. "PT-HP sep")
   * @return the transmitter, or null if not found
   */
  public MeasurementDeviceInterface getTransmitter(String tag) {
    return transmitters.get(tag);
  }

  /**
   * Gets a specific controller by tag name.
   *
   * @param tag the controller tag (e.g. "PC-HP sep")
   * @return the controller, or null if not found
   */
  public ControllerDeviceInterface getController(String tag) {
    return controllers.get(tag);
  }

  /**
   * Creates a flow transmitter on a stream and a flow controller that drives a valve. This is a
   * convenience method for adding flow control to any valve in the process.
   *
   * @param tag ISA tag for the flow loop (e.g. "FIC-101")
   * @param valve the valve to control
   * @param stream the stream to measure flow on (typically the valve inlet)
   * @param flowSetpoint the desired flow rate
   * @param flowUnit the unit for flow measurement (e.g. "kg/hr")
   * @return the flow controller
   */
  public ControllerDeviceInterface addFlowController(String tag, ThrottlingValve valve,
      StreamInterface stream, double flowSetpoint, String flowUnit) {
    VolumeFlowTransmitter ft = new VolumeFlowTransmitter("FT-" + tag, stream);
    ft.setUnit(flowUnit);
    ft.setMaximumValue(flowSetpoint * 2.0);
    ft.setMinimumValue(0.0);
    addTransmitter("FT-" + tag, ft);

    ControllerDeviceInterface fc =
        createPIDController("FC-" + tag, ft, flowSetpoint, flowKp, flowTi, 0.0, true);
    valve.setController(fc);
    addController("FC-" + tag, fc);
    return fc;
  }

  /**
   * Creates a temperature transmitter and a temperature controller that drives a heater or cooler
   * duty. This is a convenience method for adding temperature control.
   *
   * @param tag ISA tag for the temperature loop (e.g. "TIC-101")
   * @param heatExchanger the heater or cooler to control
   * @param outletStream the outlet stream to measure temperature on
   * @param tempSetpointC the desired outlet temperature in Celsius
   * @return the temperature controller
   */
  public ControllerDeviceInterface addTemperatureController(String tag,
      ProcessEquipmentInterface heatExchanger, StreamInterface outletStream, double tempSetpointC) {
    TemperatureTransmitter tt = new TemperatureTransmitter("TT-" + tag, outletStream);
    tt.setUnit("C");
    addTransmitter("TT-" + tag, tt);

    ControllerDeviceInterface tc = createPIDController("TC-" + tag, tt, tempSetpointC,
        temperatureKp, temperatureTi, 0.0, false);
    heatExchanger.setController(tc);
    addController("TC-" + tag, tc);
    return tc;
  }

  /**
   * Exports the process and its instruments to a DEXPI XML file. The generated transmitters and
   * controllers are included as {@code ProcessInstrumentationFunction} and
   * {@code InstrumentationLoopFunction} elements.
   *
   * @param file output DEXPI XML file
   * @throws IOException if writing fails
   */
  public void exportDexpi(File file) throws IOException {
    DexpiXmlWriter.write(process, file, transmitters, controllers);
  }

  /**
   * Reads instrument metadata from a DEXPI XML file. Returns structured {@link DexpiInstrumentInfo}
   * records describing the P&amp;ID instrumentation found in the file.
   *
   * <p>
   * Note that live transmitter and controller objects are not created because they require
   * connected process streams. Use the returned info to identify which instruments exist in the
   * P&amp;ID.
   * </p>
   *
   * @param file DEXPI XML file to read
   * @return list of instrument info records
   * @throws IOException if the file cannot be read
   * @throws DexpiXmlReaderException if the file cannot be parsed
   */
  public List<DexpiInstrumentInfo> readDexpiInstruments(File file)
      throws IOException, DexpiXmlReaderException {
    return DexpiXmlReader.readInstruments(file);
  }
}
