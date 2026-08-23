package neqsim.process.util.optimizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.capacity.BottleneckResult;
import neqsim.process.equipment.capacity.CapacityConstraint;
import neqsim.process.equipment.capacity.CapacityConstraint.ConstraintSeverity;
import neqsim.process.equipment.capacity.CapacityConstraint.ConstraintType;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.pipeline.PipeBeggsAndBrills;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.splitter.Splitter;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.equipment.util.Recycle;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.processmodel.ProcessModel;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Executable S/M baseline for the industrial plant optimization campaign.
 *
 * <p>
 * The harness intentionally changes no production execution or solver behavior. It builds deterministic synthetic
 * public-data-free cases, exercises the modes frozen in the industrial optimization baseline, and emits raw JSON when
 * {@code -Dneqsim.optimization.baseline.output=<path>} is supplied. Metrics that current public APIs cannot attribute
 * are represented by explicit unavailable entries instead of numeric zeroes.
 * </p>
 *
 * <p>
 * Timings are observations of the executing host, not performance acceptance thresholds. End-to-end performance claims
 * require at least five measured forks and remain coordinated with issue #2939.
 * </p>
 */
@Tag("slow")
class IndustrialPlantOptimizationBaselineTest {
  private static final Gson PRETTY_GSON = new GsonBuilder().setPrettyPrinting().create();
  private static final String SCHEMA_VERSION = "1.0";

  /** Holds the mutable decision point and installed constraint for case S. */
  private static final class SmallFixture {
    private final ProcessSystem process;
    private final Stream feed;
    private final Compressor compressor;
    private final CapacityConstraint installedPower;

    private SmallFixture(ProcessSystem process, Stream feed, Compressor compressor, CapacityConstraint installedPower) {
      this.process = process;
      this.feed = feed;
      this.compressor = compressor;
      this.installedPower = installedPower;
    }
  }

  /** Holds the mutable decision points and recycle topology for case M. */
  private static final class MediumFixture {
    private final ProcessSystem process;
    private final Stream feed;
    private final Splitter trainSplitter;
    private final PipeBeggsAndBrills feedPipe;
    private final Recycle recycle;
    private CapacityConstraint installedPipeVelocity;

    private MediumFixture(ProcessSystem process, Stream feed, Splitter trainSplitter, PipeBeggsAndBrills feedPipe,
        Recycle recycle) {
      this.process = process;
      this.feed = feed;
      this.trainSplitter = trainSplitter;
      this.feedPipe = feedPipe;
      this.recycle = recycle;
    }
  }

  /** Executes and optionally persists the frozen S/M evidence. */
  @Test
  void executeSmallAndMediumBaseline() throws IOException {
    JsonObject report = new JsonObject();
    report.addProperty("schemaVersion", SCHEMA_VERSION);
    report.addProperty("neqsimCommit", System.getProperty("neqsim.optimization.baseline.commit", "UNSPECIFIED"));
    report.add("environment", environment());
    report.add("measurementCoverage", measurementCoverage());

    JsonArray cases = new JsonArray();
    cases.add(executeSmallCase());
    cases.add(executeMediumCase());
    report.add("cases", cases);

    assertEquals(2, report.getAsJsonArray("cases").size());
    assertEquals("S", report.getAsJsonArray("cases").get(0).getAsJsonObject().get("caseId").getAsString());
    assertEquals("M", report.getAsJsonArray("cases").get(1).getAsJsonObject().get("caseId").getAsString());

    String output = System.getProperty("neqsim.optimization.baseline.output");
    if (output != null && !output.trim().isEmpty()) {
      Path path = Paths.get(output);
      if (path.getParent() != null) {
        Files.createDirectories(path.getParent());
      }
      Files.write(path, PRETTY_GSON.toJson(report).getBytes(StandardCharsets.UTF_8));
    }
  }

  /** Executes guide-equivalent small-process modes, including a fail-closed external proposal. */
  private JsonObject executeSmallCase() {
    SmallFixture fixture = buildSmallCase();
    JsonObject result = caseHeader("S", "guide-small", fixture.process, 1, 0, 1,
        "Synthetic SRK gas; rates on mass basis in kg/hr; pressure in bara; power in kW");
    JsonArray modes = new JsonArray();

    fixture.process.setProfilingEnabled(true);
    modes.add(runAndRecord(fixture.process, "cold", 1, fixture.feed, null));
    double coldOutlet = fixture.compressor.getOutletStream().getFlowRate("kg/hr");
    modes.add(runAndRecord(fixture.process, "unchanged", 1, fixture.feed, coldOutlet));

    fixture.feed.setFlowRate(5050.0, "kg/hr");
    modes.add(runAndRecord(fixture.process, "nearby-state", 1, fixture.feed, null));

    double currentPower = fixture.compressor.getPower("kW");
    assertTrue(Double.isFinite(currentPower) && currentPower > 0.0, "small-case compressor power must be finite");
    fixture.installedPower.setDesignValue(currentPower * 0.95).setMaxValue(currentPower * 0.95);
    JsonObject constrained = runAndRecord(fixture.process, "constraint-change", 1, fixture.feed, null);
    assertTrue(constrained.getAsJsonObject("bottleneck").get("utilization").getAsDouble() > 1.0,
        "installed compressor limit should bind in case S");
    modes.add(constrained);

    ProcessModel model = new ProcessModel();
    model.add("guide", fixture.process);
    ProcessModelSimulationEvaluator evaluator = new ProcessModelSimulationEvaluator(model);
    evaluator.addParameter("guide::Well Feed.flowRate", 1000.0, 20000.0, "kg/hr");
    double flowBeforeInvalid = fixture.feed.getFlowRate("kg/hr");
    IllegalArgumentException invalid = assertThrows(IllegalArgumentException.class,
        () -> evaluator.evaluate(new double[] { Double.NaN }));
    assertEquals(flowBeforeInvalid, fixture.feed.getFlowRate("kg/hr"), 0.0,
        "non-finite proposal must not mutate the live feed");
    JsonObject invalidRecord = new JsonObject();
    invalidRecord.addProperty("mode", "invalid-candidate");
    invalidRecord.addProperty("outcome", "REJECTED_BEFORE_MUTATION");
    invalidRecord.addProperty("finiteEvidence", false);
    invalidRecord.addProperty("restoration", "NOT_REQUIRED_STATE_UNCHANGED");
    invalidRecord.addProperty("rejectionReason", invalid.getMessage());
    modes.add(invalidRecord);

    result.add("modes", modes);
    return result;
  }

  /** Executes all applicable frozen modes on the 27-unit, three-train recycle case. */
  private JsonObject executeMediumCase() {
    MediumFixture fixture = buildMediumCase();
    int unitCount = fixture.process.getUnitOperations().size();
    assertTrue(unitCount >= 25 && unitCount <= 50, "case M must contain 25-50 units, was " + unitCount);
    assertTrue(fixture.process.hasRecycles(), "case M must contain a recycle");

    JsonObject result = caseHeader("M", "three-train-tail-recycle", fixture.process, 1, 1, 1,
        "Synthetic SRK rich gas; rates on mass basis in kg/hr; pressure in bara; velocity in m/s");
    JsonArray modes = new JsonArray();

    fixture.process.setProfilingEnabled(true);
    JsonObject cold = runAndRecord(fixture.process, "cold", 1, fixture.feed, null);
    modes.add(cold);
    double coldProduct = cold.get("productMassRateKgPerHr").getAsDouble();
    String coldBottleneck = bottleneckIdentity(cold);

    for (int repetition = 1; repetition <= 5; repetition++) {
      modes.add(runAndRecord(fixture.process, "unchanged", repetition, fixture.feed, coldProduct));
    }

    fixture.feed.setFlowRate(90900.0, "kg/hr");
    modes.add(runAndRecord(fixture.process, "nearby-state", 1, fixture.feed, null));

    double currentVelocity = fixture.feedPipe.getMixtureVelocity();
    assertTrue(Double.isFinite(currentVelocity) && currentVelocity > 0.0, "case M feed-pipe velocity must be finite");
    fixture.installedPipeVelocity = new CapacityConstraint("installedMixtureVelocity", "m/s", ConstraintType.HARD)
        .setDesignValue(currentVelocity * 0.95).setMaxValue(currentVelocity * 0.95).setSeverity(ConstraintSeverity.HARD)
        .setDataSource("synthetic benchmark design basis").setConfidence(1.0).setValidityRange(0.0, 50.0)
        .setDescription("Frozen case-M feed-pipe velocity limit")
        .setValueSupplier(() -> fixture.feedPipe.getMixtureVelocity());
    fixture.feedPipe.addCapacityConstraint(fixture.installedPipeVelocity);
    fixture.feedPipe.setCapacityAnalysisEnabled(true);
    JsonObject constraintChange = runAndRecord(fixture.process, "constraint-change", 1, fixture.feed, null);
    assertEquals("M Feed Pipe", constraintChange.getAsJsonObject("bottleneck").get("equipment").getAsString());
    modes.add(constraintChange);

    fixture.trainSplitter.setSplitFactors(new double[] { 0.5, 0.5, 0.0 });
    JsonObject lineUp = runAndRecord(fixture.process, "discrete-line-up", 1, fixture.feed, null);
    lineUp.addProperty("availabilityAction", "train-3 unavailable through zero split allocation");
    modes.add(lineUp);

    fixture.trainSplitter.setSplitFactors(new double[] { 1.0 / 3.0, 1.0 / 3.0, 1.0 / 3.0 });
    fixture.feed.setFlowRate(90000.0, "kg/hr");
    JsonObject restored = runAndRecord(fixture.process, "restored-line-up", 1, fixture.feed, coldProduct);
    restored.addProperty("restoration", "FULL_REPLAY_COMPLETED");
    assertTrue(restored.get("repeatabilityAbsoluteDifferenceKgPerHr").getAsDouble() <= 0.1,
        "restored product boundary must return within 0.1 kg/hr of the cold state");
    modes.add(restored);

    JsonObject transition = new JsonObject();
    transition.addProperty("status", "PARTIAL");
    transition.addProperty("observedInitialBottleneck", coldBottleneck);
    transition.addProperty("observedConstraintChangeBottleneck", bottleneckIdentity(constraintChange));
    transition.addProperty("missing",
        "ordered piping/compressor/separator shift under one continuous variable requires stable plant constraint identity");
    transition.addProperty("owner",
        "#3154 next increment; execution counters and cache attribution coordinate with #2939");
    result.add("bottleneckTransition", transition);
    result.add("modes", modes);
    return result;
  }

  /** Builds the small case from the documented Production Optimization Guide. */
  private SmallFixture buildSmallCase() {
    SystemInterface fluid = new SystemSrkEos(298.15, 50.0);
    fluid.addComponent("methane", 0.85);
    fluid.addComponent("ethane", 0.08);
    fluid.addComponent("propane", 0.05);
    fluid.addComponent("n-butane", 0.02);
    fluid.setMixingRule("classic");

    ProcessSystem process = new ProcessSystem("guide-small");
    Stream feed = new Stream("Well Feed", fluid);
    feed.setFlowRate(5000.0, "kg/hr");
    Separator separator = new Separator("HP Separator", feed);
    separator.setInternalDiameter(1.5);
    Compressor compressor = new Compressor("Gas Compressor", separator.getGasOutStream());
    compressor.setOutletPressure(100.0, "bara");
    compressor.setIsentropicEfficiency(0.78);

    process.add(feed);
    process.add(separator);
    process.add(compressor);

    CapacityConstraint installedPower = new CapacityConstraint("installedPower", "kW", ConstraintType.HARD)
        .setDesignValue(165.0).setMaxValue(165.0).setSeverity(ConstraintSeverity.HARD)
        .setDataSource("synthetic benchmark design basis").setConfidence(1.0).setValidityRange(0.0, 5000.0)
        .setDescription("Frozen guide-case installed compressor power")
        .setValueSupplier(() -> compressor.getPower("kW"));
    compressor.addCapacityConstraint(installedPower);
    return new SmallFixture(process, feed, compressor, installedPower);
  }

  /** Builds a deterministic three-train compression process with one tail recycle. */
  private MediumFixture buildMediumCase() {
    ProcessSystem process = new ProcessSystem("three-train-tail-recycle");
    process.setUseOptimizedExecution(true);

    Stream feed = new Stream("M Feed", richGas());
    feed.setFlowRate(90000.0, "kg/hr");
    feed.setTemperature(25.0, "C");
    feed.setPressure(80.0, "bara");
    process.add(feed);

    PipeBeggsAndBrills feedPipe = pipe("M Feed Pipe", feed, 250.0, 0.60);
    process.add(feedPipe);
    Splitter trainSplitter = new Splitter("M Train Splitter", feedPipe.getOutletStream(), 3);
    trainSplitter.setSplitFactors(new double[] { 1.0 / 3.0, 1.0 / 3.0, 1.0 / 3.0 });
    process.add(trainSplitter);

    StreamInterface[] trainOutlets = new StreamInterface[3];
    for (int train = 0; train < 3; train++) {
      String prefix = "M Train " + (train + 1);
      PipeBeggsAndBrills inletPipe = pipe(prefix + " Inlet Pipe", trainSplitter.getSplitStream(train), 150.0, 0.45);
      Separator inletSeparator = new Separator(prefix + " Inlet Separator", inletPipe.getOutletStream());
      inletSeparator.setInternalDiameter(1.8);
      Compressor compressor = new Compressor(prefix + " Compressor", inletSeparator.getGasOutStream());
      compressor.setOutletPressure(120.0, "bara");
      compressor.setIsentropicEfficiency(0.78);
      Cooler cooler = new Cooler(prefix + " Aftercooler", compressor.getOutletStream());
      cooler.setOutTemperature(308.15);
      Separator scrubber = new Separator(prefix + " Scrubber", cooler.getOutletStream());
      scrubber.setInternalDiameter(1.5);
      PipeBeggsAndBrills outletPipe = pipe(prefix + " Outlet Pipe", scrubber.getGasOutStream(), 100.0, 0.45);

      process.add(inletPipe);
      process.add(inletSeparator);
      process.add(compressor);
      process.add(cooler);
      process.add(scrubber);
      process.add(outletPipe);
      trainOutlets[train] = outletPipe.getOutletStream();
    }

    Mixer exportMixer = new Mixer("M Export Mixer");
    for (StreamInterface outlet : trainOutlets) {
      exportMixer.addStream(outlet);
    }
    process.add(exportMixer);

    Splitter tailSplitter = new Splitter("M Tail Splitter", exportMixer.getOutletStream(), 2);
    tailSplitter.setSplitFactors(new double[] { 0.95, 0.05 });
    process.add(tailSplitter);
    PipeBeggsAndBrills exportPipe = pipe("M Export Pipe", tailSplitter.getSplitStream(0), 1000.0, 0.55);
    process.add(exportPipe);
    Separator exportSeparator = new Separator("M Export Separator", exportPipe.getOutletStream());
    exportSeparator.setInternalDiameter(2.5);
    process.add(exportSeparator);

    ThrottlingValve recycleValve = new ThrottlingValve("M Recycle Valve", tailSplitter.getSplitStream(1));
    recycleValve.setOutletPressure(80.0, "bara");
    process.add(recycleValve);
    Recycle recycle = new Recycle("M Tail Recycle");
    recycle.addStream(recycleValve.getOutletStream());
    recycle.setOutletStream(new Stream("M Recycle Tear", richGas()));
    recycle.setTolerance(1.0e-5);
    process.add(recycle);
    exportMixer.addStream(recycle.getOutletStream());

    return new MediumFixture(process, feed, trainSplitter, feedPipe, recycle);
  }

  /** Creates one short, deterministic Beggs-Brill pipe. */
  private PipeBeggsAndBrills pipe(String name, StreamInterface inlet, double lengthMetres, double diameterMetres) {
    PipeBeggsAndBrills pipe = new PipeBeggsAndBrills(name, inlet);
    pipe.setLength(lengthMetres);
    pipe.setDiameter(diameterMetres);
    pipe.setPipeWallRoughness(15.0e-6);
    pipe.setElevation(0.0);
    pipe.setNumberOfIncrements(2);
    return pipe;
  }

  /** Creates the fixed synthetic rich-gas slate for case M. */
  private SystemInterface richGas() {
    SystemInterface fluid = new SystemSrkEos(298.15, 80.0);
    fluid.addComponent("nitrogen", 0.01);
    fluid.addComponent("CO2", 0.02);
    fluid.addComponent("methane", 0.70);
    fluid.addComponent("ethane", 0.12);
    fluid.addComponent("propane", 0.07);
    fluid.addComponent("i-butane", 0.02);
    fluid.addComponent("n-butane", 0.03);
    fluid.addComponent("n-pentane", 0.02);
    fluid.addComponent("n-hexane", 0.01);
    fluid.setMixingRule("classic");
    return fluid;
  }

  /** Runs one process mode and records only public, attributable observations. */
  private JsonObject runAndRecord(ProcessSystem process, String mode, int repetition, Stream feed,
      Double repeatabilityReference) {
    long heapBefore = usedHeapBytes();
    UUID calculationId = UUID
        .nameUUIDFromBytes((process.getName() + "/" + mode + "/" + repetition).getBytes(StandardCharsets.UTF_8));
    process.run(calculationId);
    long heapAfter = usedHeapBytes();
    assertTrue(process.getRunStatus().isSuccess(), mode + " run status must be successful");
    assertTrue(process.solved(), mode + " process must be solved");

    JsonObject record = new JsonObject();
    record.addProperty("mode", mode);
    record.addProperty("repetition", repetition);
    record.addProperty("outcome", process.getRunStatus().isSuccess() ? "SUCCESS" : "FAILED");
    record.addProperty("processSolved", process.solved());
    record.addProperty("calculationIdentity", calculationId.toString());
    record.addProperty("elapsedMs", process.getLastRunElapsedMs());
    record.addProperty("usedHeapBytesBefore", heapBefore);
    record.addProperty("usedHeapBytesAfter", heapAfter);
    record.addProperty("usedHeapDeltaBytes", heapAfter - heapBefore);
    record.addProperty("feedMassRateKgPerHr", feed.getFlowRate("kg/hr"));
    double productRate = productMassRate(process);
    record.addProperty("productMassRateKgPerHr", productRate);
    if (repeatabilityReference == null) {
      record.add("repeatabilityAbsoluteDifferenceKgPerHr", com.google.gson.JsonNull.INSTANCE);
    } else {
      record.addProperty("repeatabilityAbsoluteDifferenceKgPerHr", Math.abs(productRate - repeatabilityReference));
    }
    record.add("executionWork", executionWork(process));
    JsonObject massBalance = massBalance(process);
    assertTrue(massBalance.get("maximumAbsoluteError").getAsDouble() <= 0.1,
        mode + " maximum unit mass-balance residual must be <= 0.1 kg/hr");
    record.add("massBalance", massBalance);
    record.add("bottleneck", bottleneck(process));
    record.add("runStatus", JsonParser.parseString(process.getRunStatusJson()));
    String utilization = process.getUtilizationSnapshotJson();
    record.addProperty("serializedObservationBytes", utilization.getBytes(StandardCharsets.UTF_8).length);
    record.addProperty("utilizationSnapshotSchemaVersion",
        JsonParser.parseString(utilization).getAsJsonObject().get("schemaVersion").getAsString());
    return record;
  }

  /** Returns metadata and topology counts for one case. */
  private JsonObject caseHeader(String id, String identity, ProcessSystem process, int areaCount, int recycleCount,
      int boundaryCount, String assumptions) {
    JsonObject result = new JsonObject();
    result.addProperty("caseSchemaVersion", SCHEMA_VERSION);
    result.addProperty("caseId", id);
    result.addProperty("identity", identity);
    result.addProperty("seed", 3154);
    result.addProperty("unitCount", process.getUnitOperations().size());
    result.addProperty("areaCount", areaCount);
    result.addProperty("connectionCount", process.getConnections().size());
    result.addProperty("recycleCount", recycleCount);
    result.addProperty("boundaryCount", boundaryCount);
    result.addProperty("decisionVariableCount", 1);
    result.addProperty("objectiveCount", 1);
    result.addProperty("assumptionsAndUnits", assumptions);
    result.addProperty("compatibilityRisk", "test-and-documentation-only; no runtime behavior change");
    return result;
  }

  /** Aggregates execution call counts from ProcessSystem profiling. */
  private JsonObject executionWork(ProcessSystem process) {
    JsonObject result = new JsonObject();
    JsonObject equipment = new JsonObject();
    int calls = 0;
    for (Map.Entry<String, double[]> entry : process.getExecutionProfile().entrySet()) {
      JsonObject unit = new JsonObject();
      unit.addProperty("elapsedMs", entry.getValue()[0]);
      unit.addProperty("calls", (int) entry.getValue()[1]);
      equipment.add(entry.getKey(), unit);
      calls += (int) entry.getValue()[1];
    }
    result.addProperty("equipmentCalls", calls);
    result.add("equipment", equipment);
    result.add("areaRuns", unavailable("ProcessSystem has no public attributable area-run counter"));
    result.add("flashAndPropertyWork", unavailable("no public per-candidate flash/property counter on this baseline"));
    return result;
  }

  /** Returns the largest finite unit-level mass-balance residual. */
  private JsonObject massBalance(ProcessSystem process) {
    double maximumAbsolute = 0.0;
    double maximumPercent = 0.0;
    String worstUnit = null;
    for (Map.Entry<String, ProcessSystem.MassBalanceResult> entry : process.checkMassBalance("kg/hr").entrySet()) {
      double absolute = Math.abs(entry.getValue().getAbsoluteError());
      if (Double.isFinite(absolute) && absolute >= maximumAbsolute) {
        maximumAbsolute = absolute;
        maximumPercent = Math.abs(entry.getValue().getPercentError());
        worstUnit = entry.getKey();
      }
    }
    JsonObject result = new JsonObject();
    result.addProperty("maximumAbsoluteError", maximumAbsolute);
    result.addProperty("maximumPercentError", maximumPercent);
    result.addProperty("unit", "kg/hr");
    if (worstUnit == null) {
      result.add("worstUnit", com.google.gson.JsonNull.INSTANCE);
    } else {
      result.addProperty("worstUnit", worstUnit);
    }
    result.add("energyBalance", unavailable("no whole-ProcessSystem energy-balance residual API on this baseline"));
    return result;
  }

  /** Returns detailed bottleneck identity for the computed state. */
  private JsonObject bottleneck(ProcessSystem process) {
    BottleneckResult bottleneck = process.findBottleneck();
    JsonObject result = new JsonObject();
    if (bottleneck == null || !bottleneck.hasBottleneck()) {
      result.addProperty("status", "UNAVAILABLE");
      result.addProperty("reason", "no enabled capacity constraint produced a finite utilization");
      return result;
    }
    result.addProperty("status", "AVAILABLE");
    result.addProperty("equipment", bottleneck.getEquipmentName());
    result.addProperty("constraint", bottleneck.getConstraintName());
    result.addProperty("utilization", bottleneck.getUtilization());
    result.addProperty("utilizationPercent", bottleneck.getUtilizationPercent());
    result.addProperty("finiteEvidence", Double.isFinite(bottleneck.getUtilization()));
    CapacityConstraint constraint = bottleneck.getConstraint();
    double currentValue = constraint.getCurrentValue();
    double designValue = constraint.getDisplayDesignValue();
    result.addProperty("currentValue", currentValue);
    result.addProperty("designValue", designValue);
    result.addProperty("signedPhysicalMargin",
        constraint.isMinimumConstraint() ? currentValue - designValue : designValue - currentValue);
    result.addProperty("unit", constraint.getUnit());
    result.addProperty("type", constraint.getType().name());
    result.addProperty("severity", constraint.getSeverity().name());
    result.addProperty("provenance", constraint.getDataSource());
    result.addProperty("confidenceAvailable", constraint.hasConfidence());
    if (constraint.hasConfidence()) {
      result.addProperty("confidence", constraint.getConfidence());
    }
    result.addProperty("validityRangeAvailable", constraint.hasValidityRange());
    if (constraint.hasValidityRange()) {
      result.addProperty("validityMinimum", constraint.getValidityMinimum());
      result.addProperty("validityMaximum", constraint.getValidityMaximum());
      result.addProperty("withinValidityRange", constraint.isCurrentValueWithinValidityRange());
    }
    return result;
  }

  /** Returns the bottleneck identity without treating missing evidence as a numeric value. */
  private String bottleneckIdentity(JsonObject record) {
    JsonObject bottleneck = record.getAsJsonObject("bottleneck");
    if (!"AVAILABLE".equals(bottleneck.get("status").getAsString())) {
      return "UNAVAILABLE";
    }
    return bottleneck.get("equipment").getAsString() + "/" + bottleneck.get("constraint").getAsString();
  }

  /** Reads the mass rate leaving the last separator gas outlet. */
  private double productMassRate(ProcessSystem process) {
    ProcessEquipmentInterface exportSeparator = process.getUnit("M Export Separator");
    if (exportSeparator instanceof Separator) {
      return ((Separator) exportSeparator).getGasOutStream().getFlowRate("kg/hr");
    }
    ProcessEquipmentInterface guideCompressor = process.getUnit("Gas Compressor");
    if (guideCompressor instanceof Compressor) {
      return ((Compressor) guideCompressor).getOutletStream().getFlowRate("kg/hr");
    }
    throw new IllegalStateException("Case has no declared product boundary");
  }

  /** Records runtime environment without inventing unavailable CPU metadata. */
  private JsonObject environment() {
    JsonObject result = new JsonObject();
    result.addProperty("javaVersion", System.getProperty("java.version"));
    result.addProperty("javaVendor", System.getProperty("java.vendor"));
    result.addProperty("jvmName", System.getProperty("java.vm.name"));
    result.addProperty("osName", System.getProperty("os.name"));
    result.addProperty("osVersion", System.getProperty("os.version"));
    result.addProperty("osArchitecture", System.getProperty("os.arch"));
    result.addProperty("availableProcessors", Runtime.getRuntime().availableProcessors());
    result.addProperty("maximumHeapBytes", Runtime.getRuntime().maxMemory());
    result.addProperty("inputArguments", ManagementFactory.getRuntimeMXBean().getInputArguments().toString());
    result.add("cpuModel", unavailable("not exposed by a portable Java 8 runtime API; capture in run ledger"));
    return result;
  }

  /** Documents availability of every frozen measurement family. */
  private JsonObject measurementCoverage() {
    JsonObject result = new JsonObject();
    result.addProperty("convergenceAndRunStatus", "AVAILABLE");
    result.addProperty("equipmentExecutionCallsAndTiming", "AVAILABLE");
    result.addProperty("massBalance", "AVAILABLE");
    result.addProperty("utilizationAndBottleneck", "AVAILABLE");
    result.addProperty("resultSize", "AVAILABLE");
    result.addProperty("usedHeapBeforeAfterProxy", "AVAILABLE");
    result.add("areaExecutionCounts", unavailable("no public ProcessSystem area counter"));
    result.add("flashAndPropertyWork", unavailable("no public attributable counter"));
    result.add("cacheHitsMissesInvalidations", unavailable("optimizer cache is not used by this solve-only baseline"));
    result.add("allocatedBytes", unavailable("no portable Java 8 per-run allocation counter configured"));
    result.add("peakUsedHeap",
        unavailable("no sampling profiler configured; before/after used heap is retained as proxy"));
    return result;
  }

  /** Creates a schema-consistent unavailable observation. */
  private JsonObject unavailable(String reason) {
    JsonObject result = new JsonObject();
    result.addProperty("status", "UNAVAILABLE");
    result.addProperty("reason", reason);
    return result;
  }

  /** Reads current used heap; this is a before/after proxy, not allocated bytes or peak heap. */
  private long usedHeapBytes() {
    return Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
  }
}
