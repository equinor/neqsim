package neqsim.process.util.optimizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.ToDoubleFunction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import neqsim.process.equipment.capacity.CapacityConstraint;
import neqsim.process.equipment.capacity.CapacityConstraint.ConstraintSeverity;
import neqsim.process.equipment.capacity.CapacityConstraint.ConstraintType;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.processmodel.ProcessModel;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for {@link ProcessModelThroughputOptimizer}.
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
class ProcessModelThroughputOptimizerTest {

  /** Temporary directory for CSV export tests. */
  @TempDir
  Path temporaryDirectory;

  /**
   * Test fixture holding a two-area process model.
   *
   * @author NeqSim Development Team
   * @version 1.0
   */
  private static final class ModelFixture {
    /** Feed stream used as producer control. */
    private final Stream feed;

    /** Separator used for installed capacity constraints. */
    private final Separator separator;

    /** Full process model. */
    private final ProcessModel model;

    /**
     * Creates a fixture.
     *
     * @param feed feed stream
     * @param separator separator
     * @param model process model
     */
    private ModelFixture(Stream feed, Separator separator, ProcessModel model) {
      this.feed = feed;
      this.separator = separator;
      this.model = model;
    }
  }

  /**
   * Creates a simple gas fluid.
   *
   * @param flowRate flow rate
   * @return configured fluid
   */
  private SystemInterface createFluid(double flowRate) {
    SystemInterface fluid = new SystemSrkEos(298.15, 50.0);
    fluid.addComponent("methane", 0.90);
    fluid.addComponent("ethane", 0.10);
    fluid.setMixingRule("classic");
    fluid.setTotalFlowRate(flowRate, "kg/hr");
    return fluid;
  }

  /**
   * Creates a small two-area process model.
   *
   * @return model fixture
   */
  private ModelFixture createModelFixture() {
    Stream feed = new Stream("feed", createFluid(10000.0));
    ThrottlingValve choke = new ThrottlingValve("choke", feed);
    choke.setOutletPressure(30.0, "bara");
    Separator separator = new Separator("separator", choke.getOutletStream());

    ProcessSystem wells = new ProcessSystem("wells");
    wells.add(feed);
    wells.add(choke);

    ProcessSystem separation = new ProcessSystem("separation");
    separation.add(separator);

    ProcessModel model = new ProcessModel();
    model.add("wells", wells);
    model.add("separation", separation);
    return new ModelFixture(feed, separator, model);
  }

  /**
   * Adds an installed capacity constraint to the separator.
   *
   * @param fixture model fixture
   * @param designValue design flow capacity
   */
  private void addSeparatorCapacity(final ModelFixture fixture, double designValue) {
    CapacityConstraint installedCapacity = new CapacityConstraint("installedGasCapacity", "kg/hr", ConstraintType.HARD)
        .setDesignValue(designValue).setMaxValue(designValue * 1.1).setSeverity(ConstraintSeverity.HARD)
        .setDataSource("installedDataSheet").setConfidence(0.95).setValidityRange(8000.0, designValue)
        .setValueSupplier(new DoubleSupplier() {
          /** {@inheritDoc} */
          @Override
          public double getAsDouble() {
            return fixture.feed.getFlowRate("kg/hr");
          }
        });
    fixture.separator.clearCapacityConstraints();
    fixture.separator.addCapacityConstraint(installedCapacity);
  }

  /**
   * Adds a synthetic minimum headroom constraint that decreases as production rises.
   *
   * @param fixture model fixture
   * @param minimumHeadroom minimum permitted headroom
   */
  private void addMinimumHeadroomCapacity(final ModelFixture fixture, double minimumHeadroom) {
    CapacityConstraint availableHeadroom = new CapacityConstraint("availableHeadroom", "kg/hr", ConstraintType.HARD)
        .setMinValue(minimumHeadroom).setSeverity(ConstraintSeverity.HARD).setDataSource("operatingEnvelope")
        .setValueSupplier(new DoubleSupplier() {
          /** {@inheritDoc} */
          @Override
          public double getAsDouble() {
            return 50000.0 - fixture.feed.getFlowRate("kg/hr");
          }
        });
    fixture.separator.clearCapacityConstraints();
    fixture.separator.addCapacityConstraint(availableHeadroom);
  }

  /** Adds two capacity limits whose utilization order changes as throughput rises. */
  private void addSwitchingCapacityConstraints(final ModelFixture fixture) {
    CapacityConstraint exportCapacity = new CapacityConstraint("exportCapacity", "kg/hr", ConstraintType.HARD)
        .setDesignValue(15000.0).setDataSource("exportNomination")
        .setValueSupplier(() -> fixture.feed.getFlowRate("kg/hr"));
    CapacityConstraint compressorHeadroom = new CapacityConstraint("compressorHeadroom", "kg/hr", ConstraintType.HARD)
        .setDesignValue(20000.0).setDataSource("compressorMap")
        .setValueSupplier(() -> 24000.0 - fixture.feed.getFlowRate("kg/hr"));
    fixture.separator.clearCapacityConstraints();
    fixture.separator.addCapacityConstraint(exportCapacity);
    fixture.separator.addCapacityConstraint(compressorHeadroom);
  }

  /**
   * Creates a throughput optimizer with feed scaling and gas export objective.
   *
   * @param fixture model fixture
   * @return configured throughput optimizer
   */
  private ProcessModelThroughputOptimizer createOptimizer(ModelFixture fixture) {
    return new ProcessModelThroughputOptimizer(fixture.model)
        .addProducer("feed", "wells::feed.flowRate", 1.0, 2.0, "kg/hr")
        .setObjective("exportGas", new ToDoubleFunction<ProcessModel>() {
          /** {@inheritDoc} */
          @Override
          public double applyAsDouble(ProcessModel model) {
            return model.getVariableValue("separation::separator.gasOutStream.flowRate", "kg/hr");
          }
        }, "kg/hr");
  }

  /**
   * Verifies scalar throughput search and active bottleneck reporting.
   */
  @Test
  void findMaximumThroughputReturnsCaseTableAndBottleneck() {
    ModelFixture fixture = createModelFixture();
    addSeparatorCapacity(fixture, 15000.0);

    ProcessModelThroughputResult result = createOptimizer(fixture).findMaximumThroughput(1.0, 2.0, 0.01);

    assertNotNull(result.getBestFeasibleCase());
    assertNotNull(result.getFirstInfeasibleCase());
    assertTrue(result.getCaseRows().size() >= 3, "binary search should record a case table");
    assertEquals(1.5, result.getOptimalMultiplier(), 0.02);
    assertEquals("separation", result.getFirstInfeasibleCase().getActiveArea());
    assertEquals("separator", result.getFirstInfeasibleCase().getActiveEquipment());
    assertEquals("installedGasCapacity", result.getFirstInfeasibleCase().getActiveConstraint());
    ThroughputCaseRow best = result.getBestFeasibleCase();
    ThroughputCaseRow firstInfeasible = result.getFirstInfeasibleCase();
    assertEquals("installedDataSheet", best.getDataSource());
    assertTrue(best.hasConfidence());
    assertEquals(0.95, best.getConfidence(), 0.0);
    assertTrue(best.hasValidityRange());
    assertEquals(8000.0, best.getValidityMinimum(), 0.0);
    assertEquals(15000.0, best.getValidityMaximum(), 0.0);
    assertTrue(best.isCurrentValueWithinValidityRange());
    assertFalse(firstInfeasible.isCurrentValueWithinValidityRange());
    assertTrue(result.toJson().contains("\"confidence\": 0.95"));
    assertTrue(result.toJson().contains("\"currentValueWithinValidityRange\": false"));
  }

  /** Verifies the case table preserves emerging and active capacity rankings at each operating point. */
  @Test
  void throughputCasesRetainSwitchingCapacityRankings() {
    ModelFixture fixture = createModelFixture();
    addSwitchingCapacityConstraints(fixture);

    ProcessModelThroughputResult result = createOptimizer(fixture).findMaximumThroughput(1.0, 2.0, 0.01);

    ThroughputCaseRow lowRate = result.getCaseRows().get(0);
    ThroughputCaseRow highRate = result.getCaseRows().get(1);
    assertEquals("compressorHeadroom", lowRate.getRankedCapacityConstraints().get(0).getConstraintName());
    assertEquals("exportCapacity", highRate.getRankedCapacityConstraints().get(0).getConstraintName());
    assertEquals(lowRate.getActiveConstraint(), lowRate.getRankedCapacityConstraints().get(0).getConstraintName());
    assertEquals(highRate.getActiveConstraint(), highRate.getRankedCapacityConstraints().get(0).getConstraintName());
    assertEquals(2.0 / 3.0, lowRate.getRankedCapacityConstraints().get(1).getUtilization(), 1.0e-12,
        "later optimizer cases must not mutate the lower-rate snapshot");
    assertThrows(UnsupportedOperationException.class,
        () -> lowRate.getRankedCapacityConstraints().add(ProcessModelSimulationEvaluator.BottleneckStatus.none()));
    String json = result.toJson();
    assertTrue(json.contains("\"rankedCapacityConstraints\""));
    assertTrue(json.contains("\"evidenceApplicability\""));
  }

  /**
   * Verifies installed capacity CSV loading and result CSV export.
   *
   * @throws Exception if file operations fail
   */
  @Test
  void loadInstalledCapacitiesFromCsvAndExportCaseTable() throws Exception {
    ModelFixture fixture = createModelFixture();
    Path capacityFile = temporaryDirectory.resolve("installed_capacity.csv");
    List<String> rows = Arrays.asList(
        "area,equipment,constraint,currentValueAddress,designValue,maxValue,unit,severity,enabled",
        "separation,separator,installedGasCapacity,wells::feed.flowRate,15000,16500,kg/hr,HARD,true");
    Files.write(capacityFile, rows, StandardCharsets.UTF_8);

    ProcessModelThroughputOptimizer optimizer = createOptimizer(fixture);
    List<InstalledCapacityTableLoader.InstalledCapacityRecord> records = optimizer
        .loadInstalledCapacities(capacityFile);
    ProcessModelThroughputResult result = optimizer.findMaximumThroughput(1.0, 2.0, 0.01);
    Path caseTable = temporaryDirectory.resolve("throughput_trace.csv");
    result.exportToCSV(caseTable);

    assertEquals(1, records.size());
    assertTrue(fixture.separator.getCapacityConstraints().containsKey("installedGasCapacity"));
    assertEquals(1.5, result.getOptimalMultiplier(), 0.02);
    String csv = new String(Files.readAllBytes(caseTable), StandardCharsets.UTF_8);
    assertTrue(csv.contains("hasConfidence,confidence,hasValidityRange,validityMinimum,validityMaximum,"
        + "currentValueWithinValidityRange"));
    assertTrue(result.toJson().contains("\"hasConfidence\": false"));
    assertTrue(result.toJson().contains("\"confidence\": null"));
    assertFalse(result.getBestFeasibleCase().hasConfidence());
    assertTrue(Double.isNaN(result.getBestFeasibleCase().getConfidence()));
  }

  /**
   * Verifies finite, correctly signed engineering margins for minimum-directed bottlenecks.
   *
   * @throws Exception if CSV export fails
   */
  @Test
  void minimumConstraintProducesDirectedThroughputMargins() throws Exception {
    ModelFixture fixture = createModelFixture();
    addMinimumHeadroomCapacity(fixture, 35000.0);

    ProcessModelThroughputResult result = createOptimizer(fixture).findMaximumThroughput(1.0, 2.0, 0.01);
    ThroughputCaseRow best = result.getBestFeasibleCase();
    ThroughputCaseRow firstLimit = result.getFirstInfeasibleCase();

    assertNotNull(best);
    assertNotNull(firstLimit);
    assertEquals(1.5, result.getOptimalMultiplier(), 0.02);
    assertTrue(best.isMinimumConstraint());
    assertTrue(firstLimit.isMinimumConstraint());
    assertEquals("operatingEnvelope", best.getDataSource());
    assertEquals("operatingEnvelope", firstLimit.getDataSource());
    assertEquals(35000.0, best.getDesignValue(), 1.0e-12);
    assertTrue(best.getCapacityMargin() >= 0.0, "safe minimum constraint must have non-negative margin");
    assertTrue(firstLimit.getCapacityMargin() < 0.0, "violated minimum constraint must have negative margin");
    assertTrue(Double.isFinite(best.getCapacityMargin()));
    assertTrue(Double.isFinite(firstLimit.getCapacityMargin()));

    Path caseTable = temporaryDirectory.resolve("minimum_constraint_trace.csv");
    result.exportToCSV(caseTable);
    String csv = new String(Files.readAllBytes(caseTable), StandardCharsets.UTF_8);
    assertTrue(csv.contains("minimumConstraint,dataSource"));
    assertTrue(csv.contains("operatingEnvelope"));
    assertTrue(result.toJson().contains("\"minimumConstraint\": true"));
    assertTrue(result.toJson().contains("\"dataSource\": \"operatingEnvelope\""));
  }

  /**
   * Verifies malformed manually constructed row evidence cannot leak non-finite JSON or CSV output.
   */
  @Test
  void throughputRowNormalizesMalformedEvidenceToUnset() {
    ThroughputCaseRow row = new ThroughputCaseRow(1, 1.0, new java.util.LinkedHashMap<String, Double>(), 10000.0, true,
        true, "separation", "separator", "installedGasCapacity", 1.0, 12000.0, 12000.0, false, "manual", true,
        Double.NaN, true, 8000.0, Double.POSITIVE_INFINITY, 0.0, 0.0, "kg/hr", null, 0L);

    assertFalse(row.hasConfidence());
    assertTrue(Double.isNaN(row.getConfidence()));
    assertFalse(row.hasValidityRange());
    assertTrue(Double.isNaN(row.getValidityMinimum()));
    assertTrue(Double.isNaN(row.getValidityMaximum()));
    assertFalse(row.isCurrentValueWithinValidityRange());
    assertTrue(row.toMap().get("confidence") == null);
    assertTrue(row.toMap().get("validityMinimum") == null);
    assertTrue(row.toMap().get("validityMaximum") == null);
    assertTrue(row.toMap().get("currentValueWithinValidityRange") == null);
  }

  /** Verifies row applicability is derived from the row's current value and retained bounds. */
  @Test
  void throughputRowDerivesValidityApplicabilityFromSnapshot() {
    ThroughputCaseRow row = new ThroughputCaseRow(1, 1.0, new java.util.LinkedHashMap<String, Double>(), 10000.0, true,
        true, "separation", "separator", "installedGasCapacity", 10.0 / 12.0, 10000.0, 12000.0, false, "manual", true,
        0.95, true, 8000.0, 12000.0, 2000.0, 2.0 / 12.0, "kg/hr", null, 0L);

    assertTrue(row.hasValidityRange());
    assertTrue(row.isCurrentValueWithinValidityRange());
    assertEquals(Boolean.TRUE, row.toMap().get("currentValueWithinValidityRange"));
  }

  /**
   * Verifies custom scenario multiplier setters for Chapter-15-style input objects.
   */
  @Test
  void customProducerMultiplierSetterCanDriveScenarioInputs() {
    final ModelFixture fixture = createModelFixture();
    addSeparatorCapacity(fixture, 15000.0);
    final AtomicReference<Double> lastMultiplier = new AtomicReference<Double>();

    ProcessModelThroughputOptimizer optimizer = new ProcessModelThroughputOptimizer(fixture.model)
        .addProducerMultiplier("producer scenario multiplier", 1.0, 2.0, new BiConsumer<ProcessModel, Double>() {
          /** {@inheritDoc} */
          @Override
          public void accept(ProcessModel model, Double multiplier) {
            lastMultiplier.set(multiplier);
            model.setVariableValue("wells::feed.flowRate", 10000.0 * multiplier.doubleValue(), "kg/hr");
          }
        }).setObjective("exportGas", new ToDoubleFunction<ProcessModel>() {
          /** {@inheritDoc} */
          @Override
          public double applyAsDouble(ProcessModel model) {
            return model.getVariableValue("separation::separator.gasOutStream.flowRate", "kg/hr");
          }
        }, "kg/hr");

    ProcessModelThroughputResult result = optimizer.findMaximumThroughput(1.0, 2.0, 0.01);

    assertNotNull(lastMultiplier.get());
    assertEquals(1.5, result.getOptimalMultiplier(), 0.02);
    assertTrue(result.getBestFeasibleCase().getProducerMultipliers().containsKey("producer scenario multiplier"));
  }
}
