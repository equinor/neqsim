package neqsim.process.engineering;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import com.google.gson.GsonBuilder;
import neqsim.process.controllerdevice.ControllerDeviceBaseClass;
import neqsim.process.engineering.dexpi.DexpiEngineeringExporter;
import neqsim.process.engineering.pid.HeatExchangerControllerTuning;
import neqsim.process.engineering.pid.PidDesignModel;
import neqsim.process.engineering.pid.PidNativeControlSystem;
import neqsim.process.engineering.pid.PidSimulationBindings;
import neqsim.process.engineering.pid.ProfessionalPidPackageExporter;
import neqsim.process.equipment.heatexchanger.DynamicHeatExchanger;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.measurementdevice.MeasurementDeviceInterface;
import neqsim.process.measurementdevice.PressureTransmitter;
import neqsim.process.measurementdevice.TemperatureTransmitter;
import neqsim.process.measurementdevice.TransientMeasurementDevice;
import neqsim.process.measurementdevice.VolumeFlowTransmitter;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.processmodel.dexpi.Dexpi20PlantExportMetadata;

/**
 * Automatic actual-instrument, native P&amp;ID/DEXPI and thermal-control-study handoff for a two-circuit gas cooler.
 *
 * <p>
 * Source equipment and connected material streams remain authoritative. The workflow creates nine actual measurements
 * and one native outlet-temperature PID, acting on an existing utility inlet valve. It never invents pressure-relief
 * protection, fail action, line design or engineering approval. Full dynamic policy stays in the coordinated native
 * model/canonical sidecar; DEXPI retains the typed plant and signal references.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class HeatExchangerAutomationWorkflow {
  /** Prevents utility construction. */
  private HeatExchangerAutomationWorkflow() {
  }

  /**
   * Instruments an existing, configured thermal model without changing its material connections.
   *
   * @param process composable source process, ordered feed/valve/exchanger for transient execution
   * @param exchangerTag actual {@link DynamicHeatExchanger} name
   * @param valveTag existing standard throttling valve directly feeding exchanger side 1
   * @param projectId controlled project identity
   * @param settings explicit tag allocation, setpoint and initial PID policy
   * @return governed project retaining the actual source process
   * @throws IllegalArgumentException if source identities, connectivity, tags or settings are invalid
   */
  public static EngineeringProject instrument(ProcessSystem process, String exchangerTag, String valveTag,
      String projectId, Settings settings) {
    if (process == null || settings == null || projectId == null || projectId.trim().isEmpty()
        || !(process.getUnit(exchangerTag) instanceof DynamicHeatExchanger) || process.getUnit(valveTag) == null
        || process.getUnit(valveTag).getClass() != ThrottlingValve.class) {
      throw new IllegalArgumentException(
          "An explicit project, configured DynamicHeatExchanger and native valve are required");
    }
    DynamicHeatExchanger exchanger = (DynamicHeatExchanger) process.getUnit(exchangerTag);
    ThrottlingValve valve = (ThrottlingValve) process.getUnit(valveTag);
    if (exchanger.getInStream(1) != valve.getOutletStream() || valve.getController() != null
        || process.getUnitOperations().indexOf(valve) >= process.getUnitOperations().indexOf(exchanger)) {
      throw new IllegalArgumentException("An unassigned valve must directly feed side 1 before the exchanger");
    }
    String hotIn = Integer.toString(settings.processLoop);
    String hotOut = Integer.toString(settings.processLoop + 1);
    String coldIn = Integer.toString(settings.utilityLoop);
    String coldOut = Integer.toString(settings.utilityLoop + 1);
    List<MeasurementDeviceInterface> measurements = new ArrayList<MeasurementDeviceInterface>();
    measurements.add(new TemperatureTransmitter("TT-" + hotIn, exchanger.getInStream(0)));
    measurements.add(new PressureTransmitter("PT-" + hotIn, exchanger.getInStream(0)));
    TemperatureTransmitter controlled = new TemperatureTransmitter("TT-" + hotOut, exchanger.getOutStream(0));
    measurements.add(controlled);
    measurements.add(new PressureTransmitter("PT-" + hotOut, exchanger.getOutStream(0)));
    measurements.add(new TemperatureTransmitter("TT-" + coldIn, exchanger.getInStream(1)));
    measurements.add(new PressureTransmitter("PT-" + coldIn, exchanger.getInStream(1)));
    measurements.add(new VolumeFlowTransmitter("FT-" + coldIn, exchanger.getInStream(1)));
    measurements.add(new TemperatureTransmitter("TT-" + coldOut, exchanger.getOutStream(1)));
    measurements.add(new PressureTransmitter("PT-" + coldOut, exchanger.getOutStream(1)));
    String controllerTag = "TIC-" + hotOut;
    if (process.getUnit(controllerTag) != null || measurementNamed(process, controllerTag)) {
      throw new IllegalArgumentException("Controller tag already exists: " + controllerTag);
    }
    for (MeasurementDeviceInterface measurement : measurements) {
      if (process.getUnit(measurement.getName()) != null || measurementNamed(process, measurement.getName())) {
        throw new IllegalArgumentException("Instrument tag already exists: " + measurement.getName());
      }
    }
    List<neqsim.process.controllerdevice.ControllerDeviceInterface> existingControllers = new ArrayList<neqsim.process.controllerdevice.ControllerDeviceInterface>(
        process.getControllerDevices());
    for (neqsim.process.equipment.ProcessEquipmentInterface unit : process.getUnitOperations()) {
      existingControllers.addAll(unit.getControllers());
    }
    for (neqsim.process.controllerdevice.ControllerDeviceInterface controller : existingControllers) {
      boolean collision = controllerTag.equals(controller.getName());
      for (MeasurementDeviceInterface measurement : measurements) {
        collision |= measurement.getName().equals(controller.getName());
      }
      if (collision) {
        throw new IllegalArgumentException("Controller tag already exists: " + controllerTag);
      }
    }
    // Resolve the thermal setup before mutating the instrument/control inventory.
    process.run();
    for (MeasurementDeviceInterface measurement : measurements) {
      measurement.setUnit(measurement instanceof TemperatureTransmitter ? "C"
          : measurement instanceof PressureTransmitter ? "bara" : "kg/hr");
      ((TransientMeasurementDevice) measurement).setTransientSamplingEnabled(true);
      process.add(measurement);
    }
    ControllerDeviceBaseClass controller = new ControllerDeviceBaseClass(controllerTag);
    controller.setTransmitter(controlled);
    controller.setControllerSetPoint(settings.setPointC, "C");
    controller.setControllerParameters(settings.kp, settings.tiSeconds, settings.tdSeconds);
    controller.setReverseActing(false);
    controller.setOutputLimits(5.0, 100.0);
    valve.setController(controller);
    valve.setMinimumValveOpening(5.0);
    valve.setMaximumValveOpening(100.0);
    valve.setPhysicalStepIdempotencyEnabled(true);
    valve.setCalculateSteadyState(false);
    return NorsokOffshoreEngineeringBuilder.from("Two-circuit exchanger automation", process, "2.0.1")
        .projectId(projectId).registerProposedInstruments(false).build().setRevision("A");
  }

  /**
   * Exports the actual current control graph, four-port native drawings, DEXPI and optional tuning evidence.
   *
   * @param project actual source project after any deliberately applied tuning revision
   * @param metadata controlled native DEXPI provenance
   * @param directory new output directory
   * @param tuning executed tuning evidence, or null when no tuning study has been run
   * @return atomically published package directory
   * @throws IOException if export or publication fails
   * @throws IllegalArgumentException if current source inputs no longer match attached tuning evidence
   */
  public static Path export(EngineeringProject project, Dexpi20PlantExportMetadata metadata, Path directory,
      HeatExchangerControllerTuning.Result tuning) throws IOException {
    if (project == null || metadata == null || directory == null || Files.exists(directory)) {
      throw new IllegalArgumentException("A governed project, metadata and new output directory are required");
    }
    PidDesignModel pid = PidNativeControlSystem.fromProcess(project);
    PidSimulationBindings bindings = PidNativeControlSystem.bind(project, pid);
    Map<String, Object> tuningReceipt = tuning == null ? null
        : HeatExchangerControllerTuning.exportEvidence(project, pid, bindings, tuning);
    Path target = directory.toAbsolutePath().normalize();
    Path parent = target.getParent();
    if (parent == null) {
      throw new IllegalArgumentException("Output must be a new child directory");
    }
    Files.createDirectories(parent);
    Path staging = Files.createTempDirectory(parent, ".neqsim-heat-exchanger-");
    try {
      Path prepared = ProfessionalPidPackageExporter.export(project, pid, bindings, metadata,
          staging.resolve("package"));
      Map<String, Object> thermal = new LinkedHashMap<String, Object>();
      for (neqsim.process.equipment.ProcessEquipmentInterface unit : project.getEngineeringProcessSystem()
          .getUnitOperations()) {
        if (unit instanceof DynamicHeatExchanger) {
          thermal.put(unit.getName(), ((DynamicHeatExchanger) unit).getThermalConfiguration());
        }
      }
      writeJson(prepared.resolve("heat-exchanger-thermal-models.json"), thermal);
      writeJson(prepared.resolve("native-automation-registers.json"),
          PidNativeControlSystem.automationRegisters(project, pid, bindings));
      if (tuningReceipt != null) {
        writeJson(prepared.resolve("heat-exchanger-controller-tuning.json"), tuningReceipt);
      }
      DexpiEngineeringExporter.refreshPackageManifest(prepared);
      bindings.assertCurrent(project, pid);
      try {
        Files.move(prepared, target, StandardCopyOption.ATOMIC_MOVE);
      } catch (AtomicMoveNotSupportedException ex) {
        Files.move(prepared, target);
      }
      return target;
    } finally {
      try (Stream<Path> paths = Files.walk(staging)) {
        List<Path> removal = new ArrayList<Path>();
        paths.sorted(Comparator.reverseOrder()).forEach(removal::add);
        for (Path path : removal) {
          Files.deleteIfExists(path);
        }
      }
    }
  }

  /**
   * Checks an actual measurement identity without inferring it from process equipment tags.
   *
   * @param process actual process
   * @param name exact measurement name
   * @return true when an actual measurement already has that name
   */
  private static boolean measurementNamed(ProcessSystem process, String name) {
    for (MeasurementDeviceInterface measurement : process.getMeasurementDevices()) {
      if (name.equals(measurement.getName())) {
        return true;
      }
    }
    return false;
  }

  /**
   * Writes detached, unit-explicit companion data without extending the DEXPI schema.
   *
   * @param path companion file
   * @param value detached data
   * @throws IOException if writing fails
   */
  private static void writeJson(Path path, Object value) throws IOException {
    Files.write(path, new GsonBuilder().setPrettyPrinting().create().toJson(value).getBytes(StandardCharsets.UTF_8));
  }

  /**
   * Explicit instrument numbering and initial native PID inputs; these are study settings, not approved design data.
   *
   * @author Even Solbraa
   * @version 1.0
   */
  public static final class Settings {
    private final int processLoop;
    private final int utilityLoop;
    private final double setPointC;
    private final double kp;
    private final double tiSeconds;
    private final double tdSeconds;

    /**
     * Allocates inlet/inlet+1 process and water tags, with TIC on the controlled process outlet.
     *
     * @param firstProcessLoop process inlet loop number, e.g. 101
     * @param firstUtilityLoop utility inlet loop number, e.g. 201
     * @param targetC controlled outlet temperature in C
     * @param proportionalGain nonnegative output-per-K gain
     * @param integralSeconds positive integral time in seconds
     * @param derivativeSeconds nonnegative derivative time in seconds
     */
    public Settings(int firstProcessLoop, int firstUtilityLoop, double targetC, double proportionalGain,
        double integralSeconds, double derivativeSeconds) {
      if (firstProcessLoop < 1 || firstUtilityLoop < 1 || firstProcessLoop > 99998 || firstUtilityLoop > 99998
          || Math.abs(firstProcessLoop - firstUtilityLoop) <= 1 || !Double.isFinite(targetC)
          || !Double.isFinite(proportionalGain) || proportionalGain < 0.0 || !Double.isFinite(integralSeconds)
          || integralSeconds <= 0.0 || !Double.isFinite(derivativeSeconds) || derivativeSeconds < 0.0) {
        throw new IllegalArgumentException("Distinct valid tag ranges and finite PID/setpoint settings are required");
      }
      processLoop = firstProcessLoop;
      utilityLoop = firstUtilityLoop;
      setPointC = targetC;
      kp = proportionalGain;
      tiSeconds = integralSeconds;
      tdSeconds = derivativeSeconds;
    }
  }
}
