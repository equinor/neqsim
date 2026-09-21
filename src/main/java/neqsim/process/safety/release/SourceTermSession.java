package neqsim.process.safety.release;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.processmodel.ProcessModel;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;

/**
 * Synchronous source-term sampling of a caller-owned process. Runs preserve process orchestration; release calculations
 * use cloned fluid snapshots. Ordinary sources are hypothetical; explicitly registered {@link ReleaseInventory} units
 * remove their own inventory through native process transient execution.
 *
 * <p>
 * The caller must serialize all access to the underlying process, including external updates. Session synchronization
 * alone cannot protect a process modified through another reference. Callback delivery is synchronous and supplies
 * backpressure; no network runtime is required.
 * </p>
 */
public final class SourceTermSession {
  private static final Logger logger = LogManager.getLogger(SourceTermSession.class);
  /** Area key used when connecting a single ProcessSystem. */
  public static final String SINGLE_AREA = "process";
  private final String scenarioId;
  private final ProcessSystem process;
  private final ProcessModel model;
  private final Clock clock;
  private final Map<String, Source> sources = new LinkedHashMap<String, Source>();
  private final List<Consumer<SourceTermFrame>> consumers = new ArrayList<Consumer<SourceTermFrame>>();
  private final Map<String, UUID> capturedAreaIds = new LinkedHashMap<String, UUID>();
  private final Map<StreamInterface, String> capturedStreamInputs = new IdentityHashMap<StreamInterface, String>();
  private long sequence;
  private long deliveryFailures;
  private double coherentTime;
  private boolean initialized;
  private boolean faulted;
  private boolean busy;

  /**
   * Connects a process system, using the UTC system clock.
   *
   * @param scenarioId stable scenario identity
   * @param process caller-owned process
   */
  public SourceTermSession(String scenarioId, ProcessSystem process) {
    this(scenarioId, process, Clock.systemUTC());
  }

  /**
   * Connects a process model, using the UTC system clock.
   *
   * @param scenarioId stable scenario identity
   * @param model caller-owned model
   */
  public SourceTermSession(String scenarioId, ProcessModel model) {
    this(scenarioId, model, Clock.systemUTC());
  }

  /**
   * Connects a process with an explicit timestamp clock.
   *
   * @param scenarioId stable scenario identity
   * @param process caller-owned process
   * @param clock generation clock, independent of simulation time
   */
  public SourceTermSession(String scenarioId, ProcessSystem process, Clock clock) {
    text(scenarioId, "scenarioId");
    this.scenarioId = scenarioId;
    this.process = Objects.requireNonNull(process, "process");
    this.model = null;
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  /**
   * Connects a model with an explicit timestamp clock.
   *
   * @param scenarioId stable scenario identity
   * @param model caller-owned model
   * @param clock generation clock, independent of simulation time
   */
  public SourceTermSession(String scenarioId, ProcessModel model, Clock clock) {
    text(scenarioId, "scenarioId");
    this.scenarioId = scenarioId;
    this.model = Objects.requireNonNull(model, "model");
    this.process = null;
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  /**
   * Registers a hypothetical opening sampling the named unit's fluid in a single process. For a stream this is stream
   * fluid; for a separator this is its vessel fluid.
   *
   * @param sourceId stable unique source identity
   * @param unitName equipment name in the process
   * @param diameterM opening diameter in m
   * @param dischargeCoefficient coefficient in (0,1]
   * @param backPressurePa absolute receiving pressure in Pa
   * @param releaseModel explicitly selected release model
   * @throws IllegalArgumentException for invalid geometry, duplicate source or unknown unit
   */
  public synchronized void addSource(String sourceId, String unitName, double diameterM, double dischargeCoefficient,
      double backPressurePa, ReleaseFlowModel releaseModel) {
    if (process == null) {
      throw new IllegalArgumentException("A ProcessModel requires an explicit area name");
    }
    addSource(sourceId, SINGLE_AREA, unitName, -1, diameterM, dischargeCoefficient, backPressurePa, releaseModel);
  }

  /**
   * Registers an area-qualified source and freezes its process/equipment ownership.
   *
   * @param sourceId stable unique source identity
   * @param areaName area key, or SINGLE_AREA for a single process
   * @param unitName equipment name within the area
   * @param outletIndex -1 to sample the unit fluid; otherwise zero-based getOutletStreams index
   * @param diameterM opening diameter in m
   * @param dischargeCoefficient coefficient in (0,1]
   * @param backPressurePa absolute receiving pressure in Pa
   * @param releaseModel explicitly selected release model
   * @throws IllegalArgumentException for invalid configuration or unknown source
   */
  public synchronized void addSource(String sourceId, String areaName, String unitName, int outletIndex,
      double diameterM, double dischargeCoefficient, double backPressurePa, ReleaseFlowModel releaseModel) {
    idle();
    text(sourceId, "sourceId");
    text(areaName, "areaName");
    text(unitName, "unitName");
    ReleaseFlowRequest.positive(diameterM, "diameter");
    ReleaseFlowRequest.positive(dischargeCoefficient, "dischargeCoefficient");
    ReleaseFlowRequest.positive(backPressurePa, "backPressure");
    double areaM2 = Math.PI * diameterM * diameterM / 4.0;
    ReleaseFlowRequest.positive(areaM2 * dischargeCoefficient, "effectiveArea");
    if (dischargeCoefficient > 1.0 || outletIndex < -1 || releaseModel == null || sources.containsKey(sourceId)) {
      throw new IllegalArgumentException("Invalid or duplicate source configuration");
    }
    ProcessSystem area = areas().get(areaName);
    ProcessEquipmentInterface unit = area == null ? null : area.getUnit(unitName);
    if (unit == null || (outletIndex >= 0 && outletIndex >= unit.getOutletStreams().size())) {
      throw new IllegalArgumentException("Unknown area, unit or outlet");
    }
    sources.put(sourceId, new Source(sourceId, areaName, unitName, outletIndex, diameterM, dischargeCoefficient,
        backPressurePa, releaseModel, area, unit));
  }

  /**
   * Registers the physical opening owned by a ReleaseInventory in a single process.
   *
   * @param sourceId stable unique source identity
   * @param unitName name of the ReleaseInventory unit
   * @throws IllegalArgumentException for an unknown inventory or an area-based model
   */
  public synchronized void addInventorySource(String sourceId, String unitName) {
    if (process == null) {
      throw new IllegalArgumentException("A ProcessModel requires an explicit area name");
    }
    addInventorySource(sourceId, SINGLE_AREA, unitName);
  }

  /**
   * Registers an inventory's own release geometry and model without creating a second withdrawal. Frames report
   * instantaneous end-of-step rates; cumulative balances belong to the inventory. Use
   * ReleaseInventory.setReleaseEnabled to isolate the physical opening.
   *
   * @param sourceId stable unique source identity
   * @param areaName process area, or SINGLE_AREA for a single process
   * @param unitName name of the ReleaseInventory unit
   * @throws IllegalArgumentException for unknown or duplicate ownership
   */
  public synchronized void addInventorySource(String sourceId, String areaName, String unitName) {
    idle();
    ProcessSystem area = areas().get(areaName);
    ProcessEquipmentInterface unit = area == null ? null : area.getUnit(unitName);
    if (!(unit instanceof ReleaseInventory)) {
      throw new IllegalArgumentException("A ReleaseInventory unit is required");
    }
    for (Source existing : sources.values()) {
      if (existing.inventorySource && existing.unit == unit) {
        throw new IllegalArgumentException("Physical inventory opening already registered");
      }
    }
    ReleaseInventory inventory = (ReleaseInventory) unit;
    ReleaseFlowRequest request = inventory.getReleaseRequest();
    addSource(sourceId, areaName, unitName, -1, request.getDiameterM(), request.getDischargeCoefficient(),
        request.getBackPressurePa(), inventory.getReleaseModel());
    sources.get(sourceId).inventorySource = true;
  }

  /**
   * Enables or disables source-frame export. This does not change physical isolation or inventory.
   *
   * @param sourceId registered source identity
   * @param enabled true to calculate source terms
   * @throws IllegalArgumentException for unknown source
   */
  public synchronized void setEnabled(String sourceId, boolean enabled) {
    idle();
    Source source = sources.get(sourceId);
    if (source == null) {
      throw new IllegalArgumentException("Unknown source: " + sourceId);
    }
    source.enabled = enabled;
  }

  /**
   * Registers a synchronous listener. Runtime exceptions are counted and logged; remaining delivery continues.
   *
   * @param consumer completed-frame listener; callbacks must not mutate or step the process
   */
  public synchronized void subscribe(Consumer<SourceTermFrame> consumer) {
    idle();
    consumers.add(Objects.requireNonNull(consumer, "consumer"));
  }

  /**
   * Removes a listener before the next batch.
   *
   * @param consumer previously registered listener
   * @return true if removed
   */
  public synchronized boolean unsubscribe(Consumer<SourceTermFrame> consumer) {
    idle();
    return consumers.remove(consumer);
  }

  /** @return number of failed callback invocations; frames are still returned to the caller */
  public synchronized long getDeliveryFailureCount() {
    return deliveryFailures;
  }

  /** @return true if a failed owned process run requires recovery before further stepping or capture */
  public synchronized boolean isFaulted() {
    return faulted;
  }

  /**
   * Runs the existing steady-state orchestration and captures current sources. A ProcessModel retains its cross-area
   * convergence algorithm and actual per-area calculation IDs. This can recover a faulted session only after the caller
   * has restored/reinitialized physical state.
   *
   * @return immutable ordered batch, including diagnostic frames on calculation failure
   */
  public synchronized List<SourceTermFrame> runSteadyState() {
    enter();
    UUID id = UUID.randomUUID();
    try {
      Map<String, UUID> expected;
      try {
        if (process != null) {
          process.run(id);
        } else {
          model.run();
          if (!model.isModelConverged()) {
            throw new IllegalStateException("ProcessModel did not converge");
          }
        }
        expected = currentIds();
        for (ProcessSystem area : areas().values()) {
          if (!area.getRunStatus().isCompleted() || !area.getRunStatus().isSuccess()) {
            throw new IllegalStateException("Area steady-state run did not succeed");
          }
        }
        validateProcess(expected);
        if (process != null && !id.equals(process.getCalculationIdentifier())) {
          throw new IllegalStateException("Process did not complete requested calculation");
        }
      } catch (RuntimeException ex) {
        faulted = true;
        return deliver(
            unavailable(id, "STEADY_STATE", SourceTermFrame.Status.INVALID, "PROCESS_RUN_FAILED", explanation(ex)));
      }
      faulted = false;
      return captureValidated(id, expected, "STEADY_STATE");
    } finally {
      busy = false;
    }
  }

  /**
   * Advances real process transient dynamics by exactly dt using existing equipment configuration. A failed step may
   * partially mutate the process; this session then rejects further steps/capture.
   *
   * @param dt timestep in seconds, finite and greater than zero
   * @return immutable ordered batch, including diagnostic frames if the process step fails
   * @throws IllegalArgumentException for invalid dt
   * @throws IllegalStateException before successful initialization or after a failed owned run
   */
  public synchronized List<SourceTermFrame> step(double dt) {
    ReleaseFlowRequest.positive(dt, "dt");
    ready();
    enter();
    UUID id = UUID.randomUUID();
    try {
      Map<String, UUID> expected = new LinkedHashMap<String, UUID>();
      try {
        double start = alignedTime();
        double target = start + dt;
        if (!Double.isFinite(target) || target <= start) {
          throw new IllegalStateException("Timestep cannot be represented at the current process time");
        }
        if (Math.abs(start - coherentTime) > 1e-9 * Math.max(1.0, Math.abs(start))) {
          throw new IllegalStateException("Process advanced outside the session; capture that step first");
        }
        if (process != null) {
          process.runTransient(dt, id);
        } else {
          model.runTransient(dt, id);
        }
        for (String name : areas().keySet()) {
          expected.put(name, id);
        }
        validateProcess(expected);
        double end = alignedTime();
        if (Math.abs(end - target) > 1e-9 * Math.max(1.0, Math.abs(target))) {
          throw new IllegalStateException("Transient clocks did not advance by dt");
        }
      } catch (RuntimeException ex) {
        faulted = true;
        return deliver(
            unavailable(id, "TRANSIENT", SourceTermFrame.Status.INVALID, "PROCESS_STEP_FAILED", explanation(ex)));
      }
      return captureValidated(id, expected, "TRANSIENT");
    } finally {
      busy = false;
    }
  }

  /**
   * Samples an externally completed process step without running it. The caller supplies the UUID of each successfully
   * completed area calculation and owns convergence checks. This does not infer successful external execution from a
   * retained old UUID after an external failure.
   *
   * @param expectedAreaCalculations exact area keys and successful calculation UUIDs; SINGLE_AREA for one process
   * @return ordered frames, or STALE frames when current ownership, clocks or UUIDs do not match
   * @throws IllegalArgumentException for null map or null UUID entries
   * @throws IllegalStateException after an owned run failure until recovery
   */
  public synchronized List<SourceTermFrame> capture(Map<String, UUID> expectedAreaCalculations) {
    if (faulted) {
      throw new IllegalStateException("Restore process state and rerun steady state before capturing");
    }
    Objects.requireNonNull(expectedAreaCalculations, "expectedAreaCalculations");
    Map<String, UUID> expected = new LinkedHashMap<String, UUID>(expectedAreaCalculations);
    for (Map.Entry<String, UUID> entry : expected.entrySet()) {
      if (entry.getKey() == null || entry.getValue() == null) {
        throw new IllegalArgumentException("Area names and calculation UUIDs are required");
      }
    }
    enter();
    UUID id = UUID.randomUUID();
    try {
      try {
        validateProcess(expected);
        validateUnchangedCapturedInputs(expected);
      } catch (RuntimeException ex) {
        return deliver(
            unavailable(id, "EXTERNAL_CAPTURE", SourceTermFrame.Status.STALE, "PROCESS_STATE_STALE", explanation(ex)));
      }
      return captureValidated(id, expected, "EXTERNAL_CAPTURE");
    } finally {
      busy = false;
    }
  }

  private List<SourceTermFrame> captureValidated(UUID id, Map<String, UUID> expected, String mode) {
    coherentTime = alignedTime();
    initialized = true;
    List<Snapshot> snapshots = new ArrayList<Snapshot>();
    // Snapshot every source before any potentially expensive release-model call.
    for (Source source : sources.values()) {
      Snapshot snapshot = new Snapshot(source, metadata(source, mode));
      snapshots.add(snapshot);
      if (!source.enabled) {
        snapshot.status = SourceTermFrame.Status.DISABLED;
        snapshot.code = "OPENING_DISABLED";
        snapshot.message = "Hypothetical opening disabled by caller";
        continue;
      }
      try {
        ProcessSystem area = areas().get(source.areaName);
        if (area != source.area || area.getUnit(source.unitName) != source.unit
            || !expected.get(source.areaName).equals(source.unit.getCalculationIdentifier()) || !source.unit.isActive()
            || source.unit.isLockedInactive()) {
          throw new IllegalStateException("Source ownership, activity or calculation UUID is stale");
        }
        // Direct streams expose a useful input-change cache. Equipment-owned phase outlets do not
        // necessarily refresh that cache, so use their owning equipment calculation contract.
        if (source.unit instanceof StreamInterface && source.unit.needRecalculation()) {
          throw new IllegalStateException("Stream inputs changed or have no current calculable state");
        }
        if (source.inventorySource
            && Math.abs(source.unit.getTime() - coherentTime) > 1e-9 * Math.max(1.0, Math.abs(coherentTime))) {
          throw new IllegalStateException("Inventory and process clocks differ");
        }
        if (source.inventorySource && !((ReleaseInventory) source.unit).isReleaseEnabled()) {
          snapshot.status = SourceTermFrame.Status.DISABLED;
          snapshot.code = "INVENTORY_OPENING_CLOSED";
          snapshot.message = "Physical inventory opening isolated by caller";
          continue;
        }
        SystemInterface fluid;
        if (source.outletIndex < 0) {
          fluid = source.unit.getFluid();
        } else {
          StreamInterface outlet = source.unit.getOutletStreams().get(source.outletIndex);
          fluid = outlet.getFluid();
          snapshot.provenance.put("outletName", outlet.getName());
          if (outlet.getCalculationIdentifier() != null) {
            snapshot.provenance.put("outletCalculationId", outlet.getCalculationIdentifier().toString());
          }
        }
        snapshot.request = new ReleaseFlowRequest(fluid, source.diameter, source.coefficient, source.backPressure);
      } catch (RuntimeException ex) {
        snapshot.status = SourceTermFrame.Status.STALE;
        snapshot.code = "SOURCE_STATE_STALE";
        snapshot.message = explanation(ex);
      }
    }
    rememberStreamInputs(expected);
    List<SourceTermFrame> frames = new ArrayList<SourceTermFrame>();
    for (Snapshot snapshot : snapshots) {
      SourceTermFrame frame;
      if (snapshot.status != null) {
        frame = failed(snapshot.source, id, snapshot.status, snapshot.code, snapshot.message, snapshot.provenance);
      } else {
        try {
          ReleaseFlowResult result = snapshot.source.releaseModel.calculate(snapshot.request);
          frame = SourceTermFrame.calculated(scenarioId, snapshot.source.id, id, nextSequence(), coherentTime,
              clock.instant(), snapshot.request, result, snapshot.provenance);
        } catch (RuntimeException ex) {
          frame = failed(snapshot.source, id, SourceTermFrame.Status.INVALID, "SOURCE_CALCULATION_FAILED",
              explanation(ex), snapshot.provenance);
        }
      }
      frames.add(frame);
    }
    return deliver(frames);
  }

  private Map<String, ProcessSystem> areas() {
    Map<String, ProcessSystem> areas = new LinkedHashMap<String, ProcessSystem>();
    if (process != null) {
      areas.put(SINGLE_AREA, process);
    } else {
      for (String name : model.getProcessSystemNames()) {
        areas.put(name, model.get(name));
      }
    }
    return areas;
  }

  private Map<String, UUID> currentIds() {
    Map<String, UUID> ids = new LinkedHashMap<String, UUID>();
    for (Map.Entry<String, ProcessSystem> entry : areas().entrySet()) {
      ids.put(entry.getKey(), entry.getValue().getCalculationIdentifier());
    }
    return ids;
  }

  private void validateProcess(Map<String, UUID> expected) {
    Map<String, ProcessSystem> areas = areas();
    if (areas.isEmpty() || !areas.keySet().equals(expected.keySet()) || Thread.currentThread().isInterrupted()) {
      throw new IllegalStateException("Area set mismatch, empty process model or interrupted calculation");
    }
    for (Map.Entry<String, ProcessSystem> entry : areas.entrySet()) {
      UUID id = expected.get(entry.getKey());
      if (id == null || !id.equals(entry.getValue().getCalculationIdentifier()) || !entry.getValue().solved()) {
        throw new IllegalStateException("Area not solved with expected calculation UUID: " + entry.getKey());
      }
    }
    alignedTime();
  }

  private void validateUnchangedCapturedInputs(Map<String, UUID> expected) {
    for (Map.Entry<String, ProcessSystem> entry : areas().entrySet()) {
      if (!Objects.equals(capturedAreaIds.get(entry.getKey()), expected.get(entry.getKey()))) {
        continue;
      }
      for (ProcessEquipmentInterface unit : entry.getValue().getUnitOperations()) {
        if (unit instanceof StreamInterface
            && !Objects.equals(capturedStreamInputs.get(unit), streamInputSignature((StreamInterface) unit))) {
          throw new IllegalStateException(
              "Stream inputs changed since the same calculation was captured: " + unit.getName());
        }
      }
    }
  }

  private void rememberStreamInputs(Map<String, UUID> expected) {
    capturedAreaIds.clear();
    capturedAreaIds.putAll(expected);
    capturedStreamInputs.clear();
    for (ProcessSystem area : areas().values()) {
      for (ProcessEquipmentInterface unit : area.getUnitOperations()) {
        if (unit instanceof StreamInterface) {
          capturedStreamInputs.put((StreamInterface) unit, streamInputSignature((StreamInterface) unit));
        }
      }
    }
  }

  private static String streamInputSignature(StreamInterface stream) {
    SystemInterface fluid = stream.getFluid();
    if (fluid == null) {
      return "NO_FLUID";
    }
    StringBuilder signature = new StringBuilder();
    signature.append(stream.getSpecification()).append('|').append(fluid.getTemperature()).append('|')
        .append(fluid.getPressure()).append('|').append(fluid.getTotalNumberOfMoles());
    for (int i = 0; i < fluid.getNumberOfComponents(); i++) {
      signature.append('|').append(fluid.getComponent(i).getComponentName()).append('=')
          .append(fluid.getComponent(i).getz());
    }
    return signature.toString();
  }

  private double alignedTime() {
    Double first = null;
    for (ProcessSystem area : areas().values()) {
      double time = area.getTime();
      if (!Double.isFinite(time) || time < 0.0
          || (first != null && Math.abs(time - first) > 1e-9 * Math.max(1.0, Math.abs(first)))) {
        throw new IllegalStateException("Area clocks must be finite, nonnegative and aligned");
      }
      first = time;
    }
    if (first == null) {
      throw new IllegalStateException("At least one process area required");
    }
    return first;
  }

  private Map<String, String> metadata(Source source, String mode) {
    Map<String, String> provenance = new LinkedHashMap<String, String>();
    provenance.put("processType", process == null ? "ProcessModel" : "ProcessSystem");
    provenance.put("area", source.areaName);
    provenance.put("equipment", source.unitName);
    provenance.put("samplingPoint", source.outletIndex < 0 ? "EQUIPMENT_FLUID" : "OUTLET_" + source.outletIndex);
    provenance.put("mode", mode);
    provenance.put("releaseBasis", source.inventorySource ? "COUPLED_RIGID_ADIABATIC_GAS_INVENTORY"
        : "HYPOTHETICAL_OPENING_NO_INVENTORY_FEEDBACK");
    if (source.inventorySource) {
      ReleaseInventory.Balance balance = ((ReleaseInventory) source.unit).getBalance();
      provenance.put("inventoryTimeS", Double.toString(balance.getTimeS()));
      provenance.put("inventoryVolumeM3", Double.toString(balance.getVolumeM3()));
      provenance.put("cumulativeReleasedMassKg", Double.toString(balance.getReleasedMassKg()));
      provenance.put("cumulativeReleasedEnthalpyJ", Double.toString(balance.getReleasedEnergyJ()));
      provenance.put("inventoryIntegrator", "EXPLICIT_EULER_VOLUME_INTERNAL_ENERGY_V1");
      provenance.put("inventoryMaxSubstepS", Double.toString(((ReleaseInventory) source.unit).getMaxSubstepS()));
      provenance.put("inventoryLastSubsteps", Integer.toString(balance.getSubsteps()));
      provenance.put("inventoryVolumeEnergySolves",
          Integer.toString(((ReleaseInventory) source.unit).getLastVolumeEnergySolves()));
      provenance.put("rateTimeBasis", "INSTANTANEOUS_AT_FRAME_TIME");
    }
    UUID areaId = source.area.getCalculationIdentifier();
    UUID unitId = source.unit.getCalculationIdentifier();
    if (areaId != null) {
      provenance.put("areaCalculationId", areaId.toString());
    }
    if (unitId != null) {
      provenance.put("equipmentCalculationId", unitId.toString());
    }
    return provenance;
  }

  private List<SourceTermFrame> unavailable(UUID id, String mode, SourceTermFrame.Status status, String code,
      String message) {
    List<SourceTermFrame> frames = new ArrayList<SourceTermFrame>();
    for (Source source : sources.values()) {
      frames.add(failed(source, id, source.enabled ? status : SourceTermFrame.Status.DISABLED,
          source.enabled ? code : "OPENING_DISABLED",
          source.enabled ? message : "Hypothetical opening disabled by caller", metadata(source, mode)));
    }
    return frames;
  }

  private SourceTermFrame failed(Source source, UUID id, SourceTermFrame.Status status, String code, String message,
      Map<String, String> provenance) {
    return SourceTermFrame.unavailable(scenarioId, source.id, id, nextSequence(), coherentTime, clock.instant(), status,
        code, message, provenance);
  }

  private List<SourceTermFrame> deliver(List<SourceTermFrame> frames) {
    List<SourceTermFrame> batch = Collections.unmodifiableList(new ArrayList<SourceTermFrame>(frames));
    for (SourceTermFrame frame : batch) {
      for (Consumer<SourceTermFrame> consumer : consumers) {
        try {
          consumer.accept(frame);
        } catch (RuntimeException ex) {
          deliveryFailures++;
          logger.warn("Source-term callback failed for sequence {}: {}", frame.getSequence(), explanation(ex));
        }
      }
    }
    return batch;
  }

  private long nextSequence() {
    if (sequence == Long.MAX_VALUE) {
      throw new IllegalStateException("Source-term sequence exhausted; start a new session");
    }
    return sequence++;
  }

  private void ready() {
    if (!initialized || faulted) {
      throw new IllegalStateException("Successful initialization/recovery required before stepping");
    }
  }

  private void idle() {
    if (busy) {
      throw new IllegalStateException("Source-term callbacks must not reenter or mutate the session");
    }
  }

  private void enter() {
    idle();
    if (sources.isEmpty()) {
      throw new IllegalStateException("Register at least one source");
    }
    busy = true;
  }

  private static void text(String value, String name) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException(name + " required");
    }
  }

  private static String explanation(RuntimeException ex) {
    return ex.getMessage() == null || ex.getMessage().trim().isEmpty() ? ex.getClass().getSimpleName()
        : ex.getMessage();
  }

  private static final class Source {
    private final String id;
    private final String areaName;
    private final String unitName;
    private final int outletIndex;
    private final double diameter;
    private final double coefficient;
    private final double backPressure;
    private final ReleaseFlowModel releaseModel;
    private final ProcessSystem area;
    private final ProcessEquipmentInterface unit;
    private boolean enabled = true;
    private boolean inventorySource;

    private Source(String id, String areaName, String unitName, int outletIndex, double diameter, double coefficient,
        double backPressure, ReleaseFlowModel releaseModel, ProcessSystem area, ProcessEquipmentInterface unit) {
      this.id = id;
      this.areaName = areaName;
      this.unitName = unitName;
      this.outletIndex = outletIndex;
      this.diameter = diameter;
      this.coefficient = coefficient;
      this.backPressure = backPressure;
      this.releaseModel = releaseModel;
      this.area = area;
      this.unit = unit;
    }
  }

  private static final class Snapshot {
    private final Source source;
    private final Map<String, String> provenance;
    private ReleaseFlowRequest request;
    private SourceTermFrame.Status status;
    private String code;
    private String message;

    private Snapshot(Source source, Map<String, String> provenance) {
      this.source = source;
      this.provenance = provenance;
    }
  }
}
