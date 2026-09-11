package neqsim.process.util.optimizer;

import java.io.BufferedWriter;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import neqsim.process.equipment.capacity.CapacityConstraint;
import neqsim.process.equipment.capacity.CapacityConstraint.ConstraintSeverity;
import neqsim.process.equipment.capacity.CapacityConstraint.ConstraintType;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.network.NetworkDecisionVariable;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.processmodel.ProcessModel;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.util.optimizer.ProcessModelCompiledEvaluationPlan.EvaluationResult;
import neqsim.process.util.optimizer.ProcessModelOperatingActionEvaluator.HydraulicLimitRole;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Standalone raw-budget qualification for a compiled 162-unit, 20-area evaluation plan.
 *
 * <p>
 * The fixture extends the maintained large multi-area steady-state shape with an explicit feed-rate action, exact
 * installed export capacity, and a hard mass-closure boundary. It reports construction-independent compilation and
 * candidate wall time, main-thread allocation, strict JSON size, coverage, convergence, restoration, and mass closure.
 * No CI wall-time assertion or speedup claim is made.
 * </p>
 *
 * <p>
 * Arguments: output JSON path, area count, thermal stages per area. Defaults produce 162 units in 20 areas:
 * {@code compiled-process-model-evaluation-plan.json 20 2}.
 * </p>
 */
public final class CompiledProcessModelEvaluationPlanBenchmark {
  /** Exact source label for the maintained fixture. */
  private static final String FIXTURE_PROVENANCE = "#3154 maintained multi-area compiled-plan fixture";

  /** Mutable fixture used only by one serialized benchmark path. */
  private static final class Fixture {
    private final ProcessModel model = new ProcessModel();
    private final List<ProcessSystem> areas = new ArrayList<ProcessSystem>();
    private final List<StreamInterface> products = new ArrayList<StreamInterface>();
    private Stream feed;
    private Separator exportSeparator;

    /** Returns total terminal and removed-liquid product mass flow. */
    private double productMassFlow() {
      double total = 0.0;
      for (StreamInterface product : products) {
        total += product.getFlowRate("kg/hr");
      }
      return total;
    }

    /** Returns total ProcessSystem unit count. */
    private int unitCount() {
      int total = 0;
      for (ProcessSystem area : areas) {
        total += area.getUnitOperations().size();
      }
      return total;
    }
  }

  /** Builds the maintained multi-area serial process. */
  private static Fixture createFixture(int areaCount, int stagesPerArea) {
    if (areaCount < 6 || stagesPerArea < 1) {
      throw new IllegalArgumentException("Compiled-plan fixture requires at least six areas and one thermal stage");
    }
    Fixture fixture = new Fixture();
    fixture.model.setMaxIterations(8);
    StreamInterface current = null;
    for (int areaIndex = 0; areaIndex < areaCount; areaIndex++) {
      ProcessSystem area = new ProcessSystem("area " + areaIndex);
      fixture.areas.add(area);
      if (current == null) {
        fixture.feed = new Stream("feed", fluid());
        fixture.feed.setFlowRate(12000.0, "kg/hr");
        area.add(fixture.feed);
        current = fixture.feed;
      }
      Separator inletSeparator = new Separator("area " + areaIndex + " inlet separator", current);
      area.add(inletSeparator);
      fixture.products.add(inletSeparator.getLiquidOutStream());
      Compressor compressor = new Compressor("area " + areaIndex + " compressor", inletSeparator.getGasOutStream());
      compressor.setOutletPressure(90.0, "bara");
      compressor.setIsentropicEfficiency(0.75);
      area.add(compressor);
      current = compressor.getOutletStream();
      for (int stage = 0; stage < stagesPerArea; stage++) {
        Heater heater = new Heater("area " + areaIndex + " heater " + stage, current);
        heater.setOutTemperature(328.15 + stage);
        area.add(heater);
        Cooler cooler = new Cooler("area " + areaIndex + " cooler " + stage, heater.getOutletStream());
        cooler.setOutTemperature(298.15 + stage);
        area.add(cooler);
        ThrottlingValve valve = new ThrottlingValve("area " + areaIndex + " valve " + stage, cooler.getOutletStream());
        valve.setOutletPressure(90.0 - 10.0 * (stage + 1.0) / stagesPerArea, "bara");
        area.add(valve);
        current = valve.getOutletStream();
      }
      if (areaIndex == areaCount - 1) {
        fixture.exportSeparator = new Separator("export separator", current);
        area.add(fixture.exportSeparator);
        fixture.products.add(fixture.exportSeparator.getGasOutStream());
        fixture.products.add(fixture.exportSeparator.getLiquidOutStream());
      }
      fixture.model.add(area.getName(), area);
    }
    fixture.model.setUseOptimizedExecution(true);
    return fixture;
  }

  /** Creates the normalized 12-component rich-gas feed. */
  private static SystemInterface fluid() {
    SystemInterface fluid = new SystemSrkEos(313.15, 80.0);
    String[] names = { "nitrogen", "CO2", "methane", "ethane", "propane", "i-butane", "n-butane", "i-pentane",
        "n-pentane", "n-hexane", "n-heptane", "n-octane" };
    double[] fractions = { 0.01, 0.02, 0.65, 0.10, 0.06, 0.02, 0.03, 0.015, 0.015, 0.03, 0.03, 0.02 };
    for (int index = 0; index < names.length; index++) {
      fluid.addComponent(names[index], fractions[index]);
    }
    fluid.setMixingRule(2);
    fluid.setMultiPhaseCheck(true);
    return fluid;
  }

  /** Creates the compiled plan authority and exact expected coverage. */
  private static ProcessModelOperatingActionSetEvaluator createEvaluator(final Fixture fixture) {
    String exportArea = "area " + (fixture.areas.size() - 1);
    CapacityConstraint exportCapacity = new CapacityConstraint("installed export mass rate", "kg/hr",
        ConstraintType.HARD).setDesignValue(14000.0).setSeverity(ConstraintSeverity.HARD)
        .setDataSource("maintained #3154 export rating").setConfidence(0.95).setValidityRange(10000.0, 14000.0)
        .setValueSupplier(() -> fixture.productMassFlow());
    fixture.exportSeparator.clearCapacityConstraints();
    fixture.exportSeparator.addCapacityConstraint(exportCapacity);

    ProcessModelSimulationEvaluator simulation = new ProcessModelSimulationEvaluator(fixture.model);
    simulation.setIncludeStrategyCapacityConstraints(false);
    simulation.addObjective("terminal production", model -> fixture.productMassFlow(),
        ProcessModelSimulationEvaluator.ObjectiveDefinition.Direction.MAXIMIZE);
    ProcessBoundaryConstraintEvidence.Metadata massClosure = new ProcessBoundaryConstraintEvidence.Metadata(
        "whole-plant-mass-closure", exportArea, "whole plant", ProcessBoundaryConstraintEvidence.Kind.EXPORT_CAPACITY,
        ProcessBoundaryConstraintEvidence.FlowDirection.NOT_APPLICABLE, NetworkDecisionVariable.RateBasis.MASS,
        FIXTURE_PROVENANCE, 1.0, null, null, ProcessBoundaryConstraintEvidence.ApplicabilityStatus.APPLICABLE,
        "feed minus products", "steady-state component and total material balance", null, -1);
    simulation.addBoundaryConstraint("whole plant mass residual", massClosure,
        model -> ProcessBoundaryConstraintEvidence.Sample
            .available(fixture.feed.getFlowRate("kg/hr") - fixture.productMassFlow()),
        ProcessModelSimulationEvaluator.ConstraintDefinition.Type.EQUALITY, 0.0, Double.POSITIVE_INFINITY, 0.05,
        "kg/hr", true, 10.0, 12000.0);
    ProcessModelOperatingAction feedAction = ProcessModelOperatingAction.continuous("feed-mass-rate",
        "Plant feed mass rate", "area 0::feed.flowRate", 10000.0, 13000.0, "kg/hr",
        "maintained #3154 large-case feed envelope");
    return new ProcessModelOperatingActionSetEvaluator("large-case-action-set", "Large-case feed action",
        FIXTURE_PROVENANCE, simulation, Arrays.asList(feedAction))
        .requireHydraulicConstraint(HydraulicLimitRole.PIPELINE_HYDRAULICS, exportArea, "export separator",
            "installed export mass rate", "maintained #3154 export capacity");
  }

  /** Returns main-thread allocated bytes when the JVM exposes them. */
  private static long allocatedBytes() {
    java.lang.management.ThreadMXBean bean = ManagementFactory.getThreadMXBean();
    if (bean instanceof com.sun.management.ThreadMXBean) {
      com.sun.management.ThreadMXBean allocationBean = (com.sun.management.ThreadMXBean) bean;
      if (allocationBean.isThreadAllocatedMemorySupported() && allocationBean.isThreadAllocatedMemoryEnabled()) {
        return allocationBean.getThreadAllocatedBytes(Thread.currentThread().getId());
      }
    }
    return -1L;
  }

  /** Returns a non-negative allocation delta or -1 when unavailable. */
  private static long allocationDelta(long before, long after) {
    return before < 0L || after < 0L ? -1L : after - before;
  }

  /**
   * Runs one compilation and one changed candidate, then writes raw acceptance evidence.
   *
   * @param args output path, area count, and thermal stages per area
   * @throws Exception if construction, simulation, acceptance, restoration, or output fails
   */
  public static void main(String[] args) throws Exception {
    Path output = Paths.get(args.length > 0 ? args[0] : "compiled-process-model-evaluation-plan.json");
    int areaCount = args.length > 1 ? Integer.parseInt(args[1]) : 20;
    int stagesPerArea = args.length > 2 ? Integer.parseInt(args[2]) : 2;
    Fixture fixture = createFixture(areaCount, stagesPerArea);
    ProcessModelOperatingActionSetEvaluator evaluator = createEvaluator(fixture);

    long compileAllocatedBefore = allocatedBytes();
    long compileStart = System.nanoTime();
    ProcessModelCompiledEvaluationPlan plan = ProcessModelCompiledEvaluationPlan.compile("large-case-plan",
        "Compiled large ProcessModel evaluation", FIXTURE_PROVENANCE, "runtime current-master source", evaluator);
    long compileElapsed = System.nanoTime() - compileStart;
    long compileAllocatedAfter = allocatedBytes();

    long evaluationAllocatedBefore = allocatedBytes();
    long evaluationStart = System.nanoTime();
    EvaluationResult result = plan.evaluate(new double[] { 12120.0 });
    long evaluationElapsed = System.nanoTime() - evaluationStart;
    long evaluationAllocatedAfter = allocatedBytes();
    String resultJson = result.toJson();

    double restoredFeed = fixture.feed.getFlowRate("kg/hr");
    double restoredProducts = fixture.productMassFlow();
    double restoredMassResidual = restoredFeed - restoredProducts;
    JsonObject report = new JsonObject();
    report.addProperty("fixture", "compiled-process-model-evaluation-plan");
    report.addProperty("provenance", FIXTURE_PROVENANCE);
    report.addProperty("areas", fixture.areas.size());
    report.addProperty("units", fixture.unitCount());
    report.addProperty("compileElapsedNs", compileElapsed);
    report.addProperty("compileMainThreadAllocatedBytes",
        allocationDelta(compileAllocatedBefore, compileAllocatedAfter));
    report.addProperty("evaluationElapsedNs", evaluationElapsed);
    report.addProperty("evaluationMainThreadAllocatedBytes",
        allocationDelta(evaluationAllocatedBefore, evaluationAllocatedAfter));
    report.addProperty("resultJsonBytes", resultJson.getBytes(StandardCharsets.UTF_8).length);
    report.addProperty("expectedInstalledCapacityRows", plan.getExpectedInstalledCapacityIdentities().size());
    report.addProperty("expectedBoundaryRows", plan.getExpectedBoundaryIdentities().size());
    report.addProperty("candidateAccepted", result.isAccepted());
    report.addProperty("candidateConverged",
        result.getCandidateEvidence() != null && result.getCandidateEvidence().isCandidateSimulationConverged());
    report.addProperty("baselineRestored",
        result.getCandidateEvidence() != null && result.getCandidateEvidence().isBaselineRestored());
    report.addProperty("baselineReconverged",
        result.getCandidateEvidence() != null && result.getCandidateEvidence().isBaselineSimulationConverged());
    report.addProperty("restoredFeedKgHr", restoredFeed);
    report.addProperty("restoredProductsKgHr", restoredProducts);
    report.addProperty("restoredMassResidualKgHr", restoredMassResidual);
    report.addProperty("javaVersion", System.getProperty("java.version"));
    report.addProperty("osName", System.getProperty("os.name"));
    report.addProperty("availableProcessors", Runtime.getRuntime().availableProcessors());
    report.addProperty("maxHeapBytes", Runtime.getRuntime().maxMemory());
    report.addProperty("mainThreadAllocationOnly", true);
    report.addProperty("wallTimeAcceptanceAssertion", false);

    Path parent = output.toAbsolutePath().getParent();
    if (parent != null) {
      Files.createDirectories(parent);
    }
    try (BufferedWriter writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8)) {
      new GsonBuilder().setPrettyPrinting().create().toJson(report, writer);
    }
    if (fixture.areas.size() < 6 || fixture.unitCount() < 150 || !result.isAccepted()
        || Math.abs(restoredMassResidual) > 0.05) {
      throw new IllegalStateException("Compiled large-case acceptance failed: " + report);
    }
  }

  /** Prevents instantiation. */
  private CompiledProcessModelEvaluationPlanBenchmark() {
  }
}
