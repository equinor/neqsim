package neqsim.process.util.optimization;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.pump.Pump;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.util.optimization.ProductionOptimizer.ConstraintSeverity;
import neqsim.process.util.optimization.ProductionOptimizer.OptimizationConfig;
import neqsim.process.util.optimization.ProductionOptimizer.OptimizationConstraint;
import neqsim.process.util.optimization.ProductionOptimizer.OptimizationObjective;
import neqsim.process.util.optimization.ProductionOptimizer.ObjectiveType;
import neqsim.process.util.optimization.ProductionOptimizer.IterationRecord;
import neqsim.process.util.optimization.ProductionOptimizer.OptimizationResult;
import neqsim.process.util.optimization.ProductionOptimizer.OptimizationSummary;
import neqsim.process.util.optimization.ProductionOptimizer.ScenarioRequest;
import neqsim.process.util.optimization.ProductionOptimizer.ScenarioResult;
import neqsim.process.util.optimization.ProductionOptimizer.ScenarioComparisonResult;
import neqsim.process.util.optimization.ProductionOptimizer.ScenarioKpi;
import neqsim.process.util.optimization.ProductionOptimizer.EquipmentConstraintRule;
import neqsim.process.util.optimization.ProductionOptimizer.CapacityRule;
import neqsim.process.util.optimization.ProductionOptimizer.CapacityRange;
import neqsim.process.util.optimization.ProductionOptimizer.ConstraintDirection;
import neqsim.process.util.optimization.ProductionOptimizer.SearchMode;
import neqsim.process.util.optimization.ProductionOptimizer.ManipulatedVariable;
import neqsim.process.util.optimization.ProductionOptimizer.UtilizationSeries;
import neqsim.process.util.optimization.ProductionOptimizationSpecLoader;
import neqsim.thermo.system.SystemSrkEos;

public class ProductionOptimizerTest {

  @Test
  public void testOptimizationCollectsUtilizationsAndConstraints() {
    SystemSrkEos testSystem = new SystemSrkEos(298.15, 10.0);
    testSystem.addComponent("methane", 1200.0);

    Stream inletStream = new Stream("inlet stream", testSystem);
    inletStream.setFlowRate(500.0, "kg/hr");

    Compressor compressor = new Compressor("compressor", inletStream);
    compressor.setOutletPressure(50.0);
    compressor.getMechanicalDesign().setMaxDesignPower(5000.0);

    Separator separator = new Separator("separator", compressor.getOutletStream());
    separator.getMechanicalDesign().setMaxDesignGassVolumeFlow(10_000.0);

    ProcessSystem process = new ProcessSystem();
    process.add(inletStream);
    process.add(compressor);
    process.add(separator);

    ProductionOptimizer optimizer = new ProductionOptimizer();
    OptimizationConfig config = new OptimizationConfig(100.0, 5_000.0).rateUnit("kg/hr")
        .tolerance(10.0).defaultUtilizationLimit(5.0)
        .utilizationLimitForName(compressor.getName(), 5.0);

    OptimizationObjective minimizePower = new OptimizationObjective("compressor power",
        proc -> compressor.getPower(), -1.0);

    OptimizationConstraint softConstraint = OptimizationConstraint.lessThan(
        "soft compressor load",
        proc -> compressor.getCapacityDuty() / compressor.getCapacityMax(), 0.01,
        ConstraintSeverity.SOFT, 10.0, "Prefer low compressor utilization for testing");

    OptimizationConstraint hardConstraint = OptimizationConstraint.lessThan("max units",
        proc -> proc.getUnitOperations().size(), 10.0, ConstraintSeverity.HARD, 0.0,
        "Keep overall system size bounded");

    OptimizationResult result = optimizer.optimize(process, inletStream, config,
        Collections.singletonList(minimizePower), Arrays.asList(softConstraint, hardConstraint));

    Assertions.assertTrue(result.isFeasible(), "Feasible solution should respect hard limits");
    Assertions.assertNotNull(result.getBottleneck(), "Bottleneck should be identified");
    Assertions.assertEquals(compressor.getName(), result.getBottleneck().getName());
    Assertions.assertFalse(result.getUtilizationRecords().isEmpty(),
        "Utilization records should be collected for reporting");
    Assertions.assertEquals(2, result.getConstraintStatuses().size());

    boolean softViolated = result.getConstraintStatuses().stream()
        .anyMatch(status -> status.getName().equals("soft compressor load") && status.violated());
    Assertions.assertTrue(softViolated,
        "Soft constraint should be tracked as violated without blocking feasibility");
  }

  @Test
  public void testGoldenSectionSearchTracksHistory() {
    SystemSrkEos testSystem = new SystemSrkEos(298.15, 10.0);
    testSystem.addComponent("methane", 800.0);

    Stream inletStream = new Stream("inlet stream", testSystem);
    inletStream.setFlowRate(300.0, "kg/hr");

    Compressor compressor = new Compressor("compressor", inletStream);
    compressor.setOutletPressure(30.0);
    compressor.getMechanicalDesign().setMaxDesignPower(2_000.0);

    Separator separator = new Separator("separator", compressor.getOutletStream());
    separator.getMechanicalDesign().setMaxDesignGassVolumeFlow(8_000.0);

    ProcessSystem process = new ProcessSystem();
    process.add(inletStream);
    process.add(compressor);
    process.add(separator);

    ProductionOptimizer optimizer = new ProductionOptimizer();
    OptimizationConfig config = new OptimizationConfig(50.0, 2_000.0).rateUnit("kg/hr")
        .searchMode(ProductionOptimizer.SearchMode.GOLDEN_SECTION_SCORE).tolerance(5.0)
        .defaultUtilizationLimit(1.5);

    OptimizationObjective minimizePower = new OptimizationObjective("power", proc -> {
      compressor.run();
      return -compressor.getPower();
    }, 1.0);

    OptimizationConstraint keepUnitsReasonable = OptimizationConstraint.lessThan("max units",
        proc -> proc.getUnitOperations().size(), 20.0, ConstraintSeverity.HARD, 0.0,
        "Ensure system remains small");

    OptimizationResult result = optimizer.optimize(process, inletStream, config,
        Collections.singletonList(minimizePower), Collections.singletonList(keepUnitsReasonable));

    Assertions.assertNotNull(result.getIterationHistory(), "Iteration history should be kept");
    Assertions.assertFalse(result.getIterationHistory().isEmpty(),
        "Golden section search should record iterations");
    Assertions.assertTrue(result.getIterationHistory().stream().anyMatch(IterationRecord::isFeasible),
        "At least one feasible point should be found");
    Assertions.assertTrue(result.getIterations() <= config.getMaxIterations(),
        "Iteration count should not exceed configured maximum");
  }

  @Test
  public void testUtilizationMarginsAndCapacityUncertaintyAreApplied() {
    SystemSrkEos system = new SystemSrkEos(298.15, 8.0);
    system.addComponent("methane", 600.0);

    Stream inlet = new Stream("inlet", system);
    inlet.setFlowRate(200.0, "kg/hr");

    Compressor compressor = new Compressor("compressor", inlet);
    compressor.setOutletPressure(20.0);
    compressor.getMechanicalDesign().setMaxDesignPower(1_000.0);

    ProcessSystem process = new ProcessSystem();
    process.add(inlet);
    process.add(compressor);

    ProductionOptimizer optimizer = new ProductionOptimizer();
    OptimizationConfig config = new OptimizationConfig(50.0, 1_000.0).rateUnit("kg/hr")
        .defaultUtilizationLimit(5.0)
        .utilizationLimitForName(compressor.getName(), 1.0)
        .utilizationMarginFraction(0.1).capacityUncertaintyFraction(0.2);

    OptimizationResult result = optimizer.optimize(process, inlet, config, Collections.emptyList(),
        Collections.emptyList());

    Assertions.assertFalse(result.getUtilizationRecords().isEmpty());

    double appliedLimit = result.getUtilizationRecords().get(0).getUtilizationLimit();
    Assertions.assertEquals(0.9, appliedLimit, 1e-6, "Margin should reduce utilization cap");
  }

  @Test
  public void testScenarioComparisonReturnsNamedResults() {
    SystemSrkEos baseSystem = new SystemSrkEos(298.15, 9.0);
    baseSystem.addComponent("methane", 900.0);

    Stream baseStream = new Stream("base", baseSystem);
    baseStream.setFlowRate(400.0, "kg/hr");

    Compressor baseCompressor = new Compressor("compressor", baseStream);
    baseCompressor.setOutletPressure(30.0);
    baseCompressor.getMechanicalDesign().setMaxDesignPower(2_500.0);

    ProcessSystem baseProcess = new ProcessSystem();
    baseProcess.add(baseStream);
    baseProcess.add(baseCompressor);

    SystemSrkEos debottleneckSystem = baseSystem.clone();
    Stream debottleneckStream = new Stream("debottleneck", debottleneckSystem);
    debottleneckStream.setFlowRate(400.0, "kg/hr");
    Compressor debottleneckCompressor = new Compressor("compressor", debottleneckStream);
    debottleneckCompressor.setOutletPressure(30.0);
    debottleneckCompressor.getMechanicalDesign().setMaxDesignPower(3_500.0);
    ProcessSystem debottleneckProcess = new ProcessSystem();
    debottleneckProcess.add(debottleneckStream);
    debottleneckProcess.add(debottleneckCompressor);

    ProductionOptimizer optimizer = new ProductionOptimizer();
    OptimizationConfig baseConfig = new OptimizationConfig(100.0, 2_000.0).rateUnit("kg/hr");
    OptimizationConfig debottleneckConfig = new OptimizationConfig(100.0, 2_000.0)
        .rateUnit("kg/hr");

    ScenarioRequest baseScenario = new ScenarioRequest("base", baseProcess, baseStream, baseConfig,
        Collections.emptyList(), Collections.emptyList());
    ScenarioRequest debottleneckScenario = new ScenarioRequest("debottleneck", debottleneckProcess,
        debottleneckStream, debottleneckConfig, Collections.emptyList(), Collections.emptyList());

    List<ScenarioResult> results = optimizer.optimizeScenarios(
        Arrays.asList(baseScenario, debottleneckScenario));

    Assertions.assertEquals(2, results.size(), "Both scenarios should be evaluated");
    Map<String, ScenarioResult> byName = results.stream()
        .collect(java.util.stream.Collectors.toMap(ScenarioResult::getName, r -> r));
    Assertions.assertTrue(byName.containsKey("base"));
    Assertions.assertTrue(byName.containsKey("debottleneck"));

    double baseRate = byName.get("base").getResult().getOptimalRate();
    double debottleneckRate = byName.get("debottleneck").getResult().getOptimalRate();
    Assertions.assertTrue(debottleneckRate >= baseRate,
        "Debottlenecking should not reduce optimal rate");
  }

  @Test
  public void testScenarioComparisonReportsKpisAndDeltas() {
    SystemSrkEos baseSystem = new SystemSrkEos(298.15, 9.0);
    baseSystem.addComponent("methane", 900.0);

    Stream baseStream = new Stream("base", baseSystem);
    baseStream.setFlowRate(400.0, "kg/hr");

    Compressor baseCompressor = new Compressor("compressor", baseStream);
    baseCompressor.setOutletPressure(30.0);
    baseCompressor.getMechanicalDesign().setMaxDesignPower(2_500.0);

    ProcessSystem baseProcess = new ProcessSystem();
    baseProcess.add(baseStream);
    baseProcess.add(baseCompressor);

    SystemSrkEos debottleneckSystem = baseSystem.clone();
    Stream debottleneckStream = new Stream("debottleneck", debottleneckSystem);
    debottleneckStream.setFlowRate(400.0, "kg/hr");
    Compressor debottleneckCompressor = new Compressor("compressor", debottleneckStream);
    debottleneckCompressor.setOutletPressure(30.0);
    debottleneckCompressor.getMechanicalDesign().setMaxDesignPower(3_500.0);
    ProcessSystem debottleneckProcess = new ProcessSystem();
    debottleneckProcess.add(debottleneckStream);
    debottleneckProcess.add(debottleneckCompressor);

    ProductionOptimizer optimizer = new ProductionOptimizer();
    OptimizationConfig config = new OptimizationConfig(100.0, 2_000.0).rateUnit("kg/hr");

    ScenarioRequest baseScenario = new ScenarioRequest("base", baseProcess, baseStream, config,
        Collections.emptyList(), Collections.emptyList());
    ScenarioRequest debottleneckScenario = new ScenarioRequest("debottleneck", debottleneckProcess,
        debottleneckStream, config, Collections.emptyList(), Collections.emptyList());

    List<ScenarioKpi> kpis = Arrays.asList(ScenarioKpi.optimalRate("kg/hr"), ScenarioKpi.score());

    ScenarioComparisonResult comparison =
        optimizer.compareScenarios(Arrays.asList(baseScenario, debottleneckScenario), kpis);

    Assertions.assertEquals("base", comparison.getBaselineScenario(),
        "First scenario should be treated as baseline");
    Assertions.assertEquals(2, comparison.getScenarioResults().size());

    double baseOptimal = comparison.getKpiValues().get("base").get("optimalRate");
    double debottleneckOptimal = comparison.getKpiValues().get("debottleneck").get("optimalRate");

    Assertions.assertTrue(debottleneckOptimal >= baseOptimal,
        "Debottlenecked scenario should not reduce optimal rate");

    double debottleneckDelta = comparison.getKpiDeltas().get("debottleneck").get("optimalRate");
    Assertions.assertEquals(debottleneckOptimal - baseOptimal, debottleneckDelta, 1e-9,
        "Delta should track change versus baseline");

    String table = ProductionOptimizer.formatScenarioComparisonTable(comparison, kpis);
    Assertions.assertTrue(table.contains("debottleneck"), "Table should contain scenario names");
    Assertions.assertTrue(table.contains("optimalRate"), "Table should print KPI names");
  }

  @Test
  public void testSpecLoaderSupportsAdvancedScenarios() throws Exception {
    SystemSrkEos baseSystem = new SystemSrkEos(298.15, 11.0);
    baseSystem.addComponent("methane", 1_000.0);
    Stream feed1 = new Stream("feed1", baseSystem);
    feed1.setFlowRate(180.0, "kg/hr");
    Compressor compressor1 = new Compressor("compressor", feed1);
    compressor1.setOutletPressure(40.0);
    compressor1.getMechanicalDesign().setMaxDesignPower(2_200.0);
    ProcessSystem baseProcess = new ProcessSystem();
    baseProcess.add(feed1);
    baseProcess.add(compressor1);

    SystemSrkEos upgradeSystem = baseSystem.clone();
    Stream feed2 = new Stream("feed2", upgradeSystem);
    feed2.setFlowRate(180.0, "kg/hr");
    Compressor compressor2 = new Compressor("compressor", feed2);
    compressor2.setOutletPressure(42.0);
    compressor2.getMechanicalDesign().setMaxDesignPower(2_800.0);
    ProcessSystem upgradeProcess = new ProcessSystem();
    upgradeProcess.add(feed2);
    upgradeProcess.add(compressor2);

    Map<String, ProcessSystem> processes = new HashMap<>();
    processes.put("base", baseProcess);
    processes.put("upgrade", upgradeProcess);
    Map<String, StreamInterface> feeds = new HashMap<>();
    feeds.put("feed1", feed1);
    feeds.put("feed2", feed2);

    Map<String, java.util.function.ToDoubleFunction<ProcessSystem>> metrics = new HashMap<>();
    metrics.put("throughput", proc -> proc == baseProcess ? feed1.getFlowRate("kg/hr")
        : feed2.getFlowRate("kg/hr"));
    metrics.put("compressorUtil", proc -> {
      Compressor compressor = (Compressor) proc.getUnitOperations().stream()
          .filter(Compressor.class::isInstance).findFirst().orElse(null);
      if (compressor == null) {
        return 0.0;
      }
      compressor.run();
      return compressor.getPower() / compressor.getMechanicalDesign().maxDesignPower;
    });

    String yaml = String.join(System.lineSeparator(), "scenarios:", "- name: base",
        "  process: base", "  feedStream: feed1", "  lowerBound: 100.0",
        "  upperBound: 320.0", "  rateUnit: kg/hr", "  capacityPercentile: 0.9",
        "  objectives:", "    - name: rate", "      metric: throughput", "      weight: 1.0",
        "      type: MAXIMIZE", "    - name: compressorUtilPenalty", "      metric: compressorUtil",
        "      weight: -0.1", "      type: MAXIMIZE", "  constraints:",
        "    - name: utilizationCap", "      metric: compressorUtil", "      limit: 0.95",
        "      direction: LESS_THAN", "      severity: HARD", "      penaltyWeight: 0.0",
        "      description: Keep compressor within design", "- name: upgrade",
        "  process: upgrade", "  lowerBound: 120.0", "  upperBound: 340.0",
        "  rateUnit: kg/hr", "  searchMode: PARTICLE_SWARM_SCORE",
        "  utilizationMarginFraction: 0.05", "  capacityPercentile: 0.9", "  variables:",
        "    - name: feed2Variable", "      stream: feed2", "      lowerBound: 120.0",
        "      upperBound: 340.0", "      unit: kg/hr", "  objectives:", "    - name: rate",
        "      metric: throughput", "      weight: 1.0", "      type: MAXIMIZE",
        "  constraints:", "    - name: utilizationCap", "      metric: compressorUtil",
        "      limit: 0.95", "      direction: LESS_THAN", "      severity: HARD",
        "      penaltyWeight: 0.0", "      description: Keep compressor within design");

    Path specFile = Files.createTempFile("optimization", ".yaml");
    Files.writeString(specFile, yaml);

    List<ProductionOptimizer.ScenarioRequest> scenarios = ProductionOptimizationSpecLoader
        .load(specFile, processes, feeds, metrics);

    ProductionOptimizer optimizer = new ProductionOptimizer();
    List<ProductionOptimizer.ScenarioResult> results = optimizer.optimizeScenarios(scenarios);

    Assertions.assertEquals(2, results.size(), "Both scenarios should be optimized from spec");
    Map<String, ProductionOptimizer.ScenarioResult> resultsByName = results.stream()
        .collect(java.util.stream.Collectors.toMap(ProductionOptimizer.ScenarioResult::getName,
            r -> r));

    ProductionOptimizer.OptimizationResult upgradeResult = resultsByName.get("upgrade")
        .getResult();
    Assertions.assertFalse(upgradeResult.getDecisionVariables().isEmpty(),
        "Variable-driven scenario should expose chosen decision values");
    Assertions.assertTrue(upgradeResult.getDecisionVariables().containsKey("feed2Variable"));
    Assertions.assertTrue(resultsByName.get("base").getResult().getConstraintStatuses().stream()
        .anyMatch(status -> status.getName().equals("utilizationCap")),
        "Constraints defined in spec should be evaluated");
    Files.deleteIfExists(specFile);
  }

  @Test
  public void testEquipmentConstraintRulesAndCapacityRanges() {
    SystemSrkEos system = new SystemSrkEos(298.15, 15.0);
    system.addComponent("methane", 1_200.0);

    Stream inlet = new Stream("inlet", system);
    inlet.setFlowRate(300.0, "kg/hr");

    Compressor compressor = new Compressor("compressor", inlet);
    compressor.setOutletPressure(80.0);
    compressor.getMechanicalDesign().setMaxDesignPower(4_000.0);

    Separator separator = new Separator("separator", compressor.getOutletStream());
    separator.getMechanicalDesign().setMaxDesignGassVolumeFlow(15_000.0);

    ProcessSystem process = new ProcessSystem();
    process.add(inlet);
    process.add(compressor);
    process.add(separator);

    ProductionOptimizer optimizer = new ProductionOptimizer();
    EquipmentConstraintRule maxPressureRatio = new EquipmentConstraintRule(Compressor.class,
        "pressure ratio", unit -> ((Compressor) unit).getOutStream().getPressure()
            / ((Compressor) unit).getInletStream().getPressure(), 20.0,
        ConstraintDirection.LESS_THAN, ConstraintSeverity.HARD, 0.0,
        "Prevent excessive pressure ratio");

    OptimizationConfig config = new OptimizationConfig(100.0, 2_000.0).rateUnit("kg/hr")
        .capacityRangeForType(Compressor.class, new CapacityRange(0.7, 0.8, 0.9))
        .capacityRuleForType(Compressor.class,
            new CapacityRule(unit -> ((Compressor) unit).getPower(),
                unit -> ((Compressor) unit).getMechanicalDesign().maxDesignPower))
        .equipmentConstraintRule(maxPressureRatio).capacityPercentile(0.9);

    OptimizationObjective maximizeRate = new OptimizationObjective("throughput",
        proc -> inlet.getFlowRate("kg/hr"), 1.0, ObjectiveType.MAXIMIZE);

    OptimizationResult result = optimizer.optimize(process, inlet, config,
        Collections.singletonList(maximizeRate), Collections.emptyList());

    boolean pressureConstraintApplied = result.getConstraintStatuses().stream()
        .anyMatch(status -> status.getName().contains("pressure ratio"));
    Assertions.assertTrue(pressureConstraintApplied, "Equipment rule should be evaluated");
    Assertions.assertFalse(result.getUtilizationRecords().isEmpty(),
        "Utilization records should be populated");
    double appliedCapacity = result.getUtilizationRecords().stream()
        .filter(rec -> rec.getEquipmentName().equals(compressor.getName())).findFirst().get()
        .getCapacityMax();
    Assertions.assertTrue(appliedCapacity > 0.0);
    Assertions.assertTrue(appliedCapacity < compressor.getMechanicalDesign().maxDesignPower,
        "Capacity percentile should downrate design capacity");
  }

  @Test
  public void testNelderMeadSearchWithCaching() {
    SystemSrkEos system = new SystemSrkEos(298.15, 12.0);
    system.addComponent("methane", 700.0);

    Stream inlet = new Stream("inlet", system);
    inlet.setFlowRate(250.0, "kg/hr");

    Compressor compressor = new Compressor("compressor", inlet);
    compressor.setOutletPressure(40.0);
    compressor.getMechanicalDesign().setMaxDesignPower(1_800.0);

    ProcessSystem process = new ProcessSystem();
    process.add(inlet);
    process.add(compressor);

    ProductionOptimizer optimizer = new ProductionOptimizer();
    OptimizationConfig config = new OptimizationConfig(50.0, 1_500.0).rateUnit("kg/hr")
        .searchMode(ProductionOptimizer.SearchMode.NELDER_MEAD_SCORE).tolerance(1.0)
        .defaultUtilizationLimit(1.5).enableCaching(true);

    OptimizationObjective minimizePower = new OptimizationObjective("power",
        proc -> compressor.getPower(), 1.0, ObjectiveType.MINIMIZE);

    OptimizationResult result = optimizer.optimize(process, inlet, config,
        Collections.singletonList(minimizePower), Collections.emptyList());

    Assertions.assertFalse(result.getIterationHistory().isEmpty());
    Assertions.assertTrue(result.getIterations() <= config.getMaxIterations());
  }

  @Test
  public void testUtilizationReportHelper() {
    List<ProductionOptimizer.UtilizationRecord> records = Arrays.asList(
        new ProductionOptimizer.UtilizationRecord("pump", 100.0, 200.0, 0.5, 0.9));
    String report = ProductionOptimizer.formatUtilizationTable(records);
    Assertions.assertTrue(report.contains("pump"));
    Assertions.assertTrue(report.contains("Capacity"));
  }

  @Test
  public void testParticleSwarmSearchCarriesUtilizationSnapshots() {
    SystemSrkEos system = new SystemSrkEos(298.15, 8.0);
    system.addComponent("methane", 700.0);

    Stream inlet = new Stream("inlet", system);
    inlet.setFlowRate(200.0, "kg/hr");

    Compressor compressor = new Compressor("compressor", inlet);
    compressor.setOutletPressure(15.0);
    compressor.getMechanicalDesign().setMaxDesignPower(1_500.0);

    ProcessSystem process = new ProcessSystem();
    process.add(inlet);
    process.add(compressor);

    ProductionOptimizer optimizer = new ProductionOptimizer();
    OptimizationConfig config = new OptimizationConfig(50.0, 1_500.0).rateUnit("kg/hr")
        .searchMode(SearchMode.PARTICLE_SWARM_SCORE).swarmSize(6).maxIterations(6)
        .defaultUtilizationLimit(3.0);

    OptimizationObjective maximizeRate = new OptimizationObjective("throughput",
        proc -> inlet.getFlowRate("kg/hr"), 1.0, ObjectiveType.MAXIMIZE);

    OptimizationResult result = optimizer.optimize(process, inlet, config,
        Collections.singletonList(maximizeRate), Collections.emptyList());

    Assertions.assertFalse(result.getIterationHistory().isEmpty(),
        "Particle swarm should record iteration history");
    Assertions.assertTrue(result.getIterationHistory().stream()
        .allMatch(record -> !record.getUtilizations().isEmpty()),
        "Each iteration should contain utilization snapshots");
  }

  @Test
  public void testDistillationFsCapacityRuleApplied() {
    class DummyColumn extends neqsim.process.equipment.distillation.DistillationColumn {
      private static final long serialVersionUID = 1L;
      private final double fsFactor;

      DummyColumn(String name, double fsFactor) {
        super(name, 1, false, false);
        this.fsFactor = fsFactor;
      }

      @Override
      public double getFsFactor() {
        return fsFactor;
      }

      @Override
      public void run(UUID id) {}

      @Override
      public void run() {}
    }

    SystemSrkEos system = new SystemSrkEos(298.15, 7.5);
    system.addComponent("methane", 500.0);
    Stream inlet = new Stream("inlet", system);
    inlet.setFlowRate(120.0, "kg/hr");

    DummyColumn column = new DummyColumn("test column", 1.2);

    ProcessSystem process = new ProcessSystem();
    process.add(inlet);
    process.add(column);

    ProductionOptimizer optimizer = new ProductionOptimizer();
    OptimizationConfig config = new OptimizationConfig(100.0, 100.0).rateUnit("kg/hr")
        .columnFsFactorLimit(1.0).utilizationMarginFraction(0.0);

    OptimizationResult result = optimizer.optimize(process, inlet, config, Collections.emptyList(),
        Collections.emptyList());

    Assertions.assertFalse(result.getUtilizationRecords().isEmpty(),
        "Column capacity rule should produce utilization records");
    Assertions.assertEquals(column.getName(), result.getBottleneck().getName());
    Assertions.assertEquals(1.2, result.getUtilizationRecords().get(0).getCapacityDuty(), 1e-6);
    Assertions.assertEquals(1.0, result.getUtilizationRecords().get(0).getCapacityMax(), 1e-6);
  }

  @Test
  public void testSeparatorCoverageAndCapacitySpread() {
    SystemSrkEos system = new SystemSrkEos(298.15, 8.0);
    system.addComponent("methane", 200.0);

    Stream inlet = new Stream("inlet", system);
    inlet.setFlowRate(50.0, "kg/hr");

    Separator separator = new Separator("separator", inlet);
    separator.setLiquidLevel(0.7);

    ProcessSystem process = new ProcessSystem();
    process.add(inlet);
    process.add(separator);

    ProductionOptimizer optimizer = new ProductionOptimizer();
    OptimizationConfig config = new OptimizationConfig(10.0, 100.0).rateUnit("kg/hr")
        .capacityRangeSpreadFraction(0.2).capacityPercentile(0.9).defaultUtilizationLimit(1.0);

    OptimizationResult result = optimizer.optimize(process, inlet, config, Collections.emptyList(),
        Collections.emptyList());

    Assertions.assertEquals(1, result.getUtilizationRecords().size(),
        "Separator should be covered by default capacity rules");
    ProductionOptimizer.UtilizationRecord record = result.getUtilizationRecords().get(0);
    Assertions.assertEquals(0.7, record.getCapacityDuty(), 1e-6,
        "Separator duty should reflect liquid level fraction");
    Assertions.assertEquals(1.16, record.getCapacityMax(), 1e-6,
        "Spread percentile should scale separator design capacity");
    Assertions.assertEquals(0.7 / 1.16, record.getUtilization(), 1e-6,
        "Utilization should respect percentile-adjusted capacity");
  }

  @Test
  public void testMultiVariableOptimizationAcrossFeeds() {
    SystemSrkEos baseSystem = new SystemSrkEos(298.15, 20.0);
    baseSystem.addComponent("methane", 1_500.0);

    Stream feedA = new Stream("feedA", baseSystem);
    feedA.setFlowRate(200.0, "kg/hr");
    Stream feedB = new Stream("feedB", baseSystem.clone());
    feedB.setFlowRate(150.0, "kg/hr");

    Compressor compA = new Compressor("compA", feedA);
    compA.setOutletPressure(60.0);
    compA.getMechanicalDesign().setMaxDesignPower(3_000.0);

    Compressor compB = new Compressor("compB", feedB);
    compB.setOutletPressure(55.0);
    compB.getMechanicalDesign().setMaxDesignPower(2_500.0);

    ProcessSystem process = new ProcessSystem();
    process.add(feedA);
    process.add(feedB);
    process.add(compA);
    process.add(compB);

    ProductionOptimizer optimizer = new ProductionOptimizer();
    OptimizationConfig config = new OptimizationConfig(100.0, 800.0).rateUnit("kg/hr")
        .searchMode(SearchMode.PARTICLE_SWARM_SCORE).swarmSize(6).defaultUtilizationLimit(5.0);

    ManipulatedVariable varA = new ManipulatedVariable("feedA", 50.0, 300.0, "kg/hr",
        (proc, value) -> feedA.setFlowRate(value, "kg/hr"));
    ManipulatedVariable varB = new ManipulatedVariable("feedB", 50.0, 300.0, "kg/hr",
        (proc, value) -> feedB.setFlowRate(value, "kg/hr"));

    OptimizationObjective throughput = new OptimizationObjective("total throughput",
        proc -> feedA.getFlowRate("kg/hr") + feedB.getFlowRate("kg/hr"), 1.0,
        ObjectiveType.MAXIMIZE);

    OptimizationResult result = optimizer.optimize(process, Arrays.asList(varA, varB), config,
        Collections.singletonList(throughput), Collections.emptyList());

    Assertions.assertFalse(result.getDecisionVariables().isEmpty(),
        "Decision variables should be tracked");
    Assertions.assertEquals(2, result.getDecisionVariables().size(),
        "Both feed variables should be tracked");
    Assertions.assertFalse(result.getIterationHistory().isEmpty(),
        "Iterations should be recorded for diagnostics");
    Assertions.assertTrue(result.getDecisionVariables().get("feedA") >= 50.0);
    Assertions.assertTrue(result.getDecisionVariables().get("feedB") >= 50.0);
  }

  @Test
  public void testQuickOptimizeProducesSummary() {
    SystemSrkEos system = new SystemSrkEos(298.15, 18.0);
    system.addComponent("methane", 900.0);

    Stream inlet = new Stream("inlet", system);
    inlet.setFlowRate(180.0, "kg/hr");

    Compressor compressor = new Compressor("compressor", inlet);
    compressor.setOutletPressure(45.0);
    compressor.getMechanicalDesign().setMaxDesignPower(2_000.0);

    ProcessSystem process = new ProcessSystem();
    process.add(inlet);
    process.add(compressor);

    ProductionOptimizer optimizer = new ProductionOptimizer();
    OptimizationConstraint keepSmall = OptimizationConstraint.lessThan("unit count",
        proc -> proc.getUnitOperations().size(), 10.0, ConstraintSeverity.HARD, 0.0,
        "Sanity check");

    OptimizationSummary summary =
        optimizer.quickOptimize(process, inlet, "kg/hr", Collections.singletonList(keepSmall));

    Assertions.assertNotNull(summary.getLimitingEquipment());
    Assertions.assertTrue(summary.getUtilizationLimit() >= summary.getUtilization());
    Assertions.assertTrue(summary.getUtilizationMargin() >= 0.0);
    Assertions.assertEquals("kg/hr", summary.getRateUnit());
    Assertions.assertFalse(summary.getConstraints().isEmpty(),
        "Constraints should be included in the summary");
  }

  @Test
  public void testUtilizationSeriesAndTimelineHighlightBottlenecks() {
    SystemSrkEos system = new SystemSrkEos(298.15, 22.0);
    system.addComponent("methane", 1_100.0);

    Stream inlet = new Stream("inlet", system);
    inlet.setFlowRate(220.0, "kg/hr");

    Compressor compressor = new Compressor("compressor", inlet);
    compressor.setOutletPressure(60.0);
    compressor.getMechanicalDesign().setMaxDesignPower(2_800.0);

    ProcessSystem process = new ProcessSystem();
    process.add(inlet);
    process.add(compressor);

    ProductionOptimizer optimizer = new ProductionOptimizer();
    OptimizationConfig config = new OptimizationConfig(50.0, 900.0).rateUnit("kg/hr")
        .searchMode(SearchMode.GOLDEN_SECTION_SCORE).tolerance(2.0)
        .defaultUtilizationLimit(2.0);

    OptimizationObjective objective = new OptimizationObjective("throughput",
        proc -> inlet.getFlowRate("kg/hr"), 1.0, ObjectiveType.MAXIMIZE);

    OptimizationResult result = optimizer.optimize(process, inlet, config,
        Collections.singletonList(objective), Collections.emptyList());

    List<UtilizationSeries> series = ProductionOptimizer
        .buildUtilizationSeries(result.getIterationHistory());
    Assertions.assertFalse(series.isEmpty(), "Series data should be produced");
    boolean bottleneckTracked = series.stream()
        .anyMatch(s -> s.getBottleneckFlags().stream().anyMatch(Boolean::booleanValue));
    Assertions.assertTrue(bottleneckTracked, "Bottleneck flags should be propagated");

    String timeline = ProductionOptimizer.formatUtilizationTimeline(result.getIterationHistory());
    Assertions.assertTrue(timeline.contains("compressor"));
    Assertions.assertTrue(timeline.contains("Iteration"));
  }
}
