package neqsim.process.util.optimizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
    CapacityConstraint installedCapacity =
        new CapacityConstraint("installedGasCapacity", "kg/hr", ConstraintType.HARD)
            .setDesignValue(designValue).setMaxValue(designValue * 1.1)
            .setSeverity(ConstraintSeverity.HARD).setValueSupplier(new DoubleSupplier() {
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

    ProcessModelThroughputResult result =
        createOptimizer(fixture).findMaximumThroughput(1.0, 2.0, 0.01);

    assertNotNull(result.getBestFeasibleCase());
    assertNotNull(result.getFirstInfeasibleCase());
    assertTrue(result.getCaseRows().size() >= 3, "binary search should record a case table");
    assertEquals(1.5, result.getOptimalMultiplier(), 0.02);
    assertEquals("separation", result.getFirstInfeasibleCase().getActiveArea());
    assertEquals("separator", result.getFirstInfeasibleCase().getActiveEquipment());
    assertEquals("installedGasCapacity", result.getFirstInfeasibleCase().getActiveConstraint());
    assertTrue(result.toJson().contains("caseRows"));
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
    List<InstalledCapacityTableLoader.InstalledCapacityRecord> records =
        optimizer.loadInstalledCapacities(capacityFile);
    ProcessModelThroughputResult result = optimizer.findMaximumThroughput(1.0, 2.0, 0.01);
    Path caseTable = temporaryDirectory.resolve("throughput_trace.csv");
    result.exportToCSV(caseTable);

    assertEquals(1, records.size());
    assertTrue(fixture.separator.getCapacityConstraints().containsKey("installedGasCapacity"));
    assertEquals(1.5, result.getOptimalMultiplier(), 0.02);
    assertTrue(new String(Files.readAllBytes(caseTable), StandardCharsets.UTF_8)
        .contains("activeConstraint"));
  }

  /**
   * Verifies custom scenario multiplier setters for Chapter-15-style input objects.
   */
  @Test
  void customProducerMultiplierSetterCanDriveScenarioInputs() {
    final ModelFixture fixture = createModelFixture();
    addSeparatorCapacity(fixture, 15000.0);
    final AtomicReference<Double> lastMultiplier = new AtomicReference<Double>();

    ProcessModelThroughputOptimizer optimizer =
        new ProcessModelThroughputOptimizer(fixture.model).addProducerMultiplier(
            "producer scenario multiplier", 1.0, 2.0, new BiConsumer<ProcessModel, Double>() {
              /** {@inheritDoc} */
              @Override
              public void accept(ProcessModel model, Double multiplier) {
                lastMultiplier.set(multiplier);
                model.setVariableValue("wells::feed.flowRate", 10000.0 * multiplier.doubleValue(),
                    "kg/hr");
              }
            }).setObjective("exportGas", new ToDoubleFunction<ProcessModel>() {
              /** {@inheritDoc} */
              @Override
              public double applyAsDouble(ProcessModel model) {
                return model.getVariableValue("separation::separator.gasOutStream.flowRate",
                    "kg/hr");
              }
            }, "kg/hr");

    ProcessModelThroughputResult result = optimizer.findMaximumThroughput(1.0, 2.0, 0.01);

    assertNotNull(lastMultiplier.get());
    assertEquals(1.5, result.getOptimalMultiplier(), 0.02);
    assertTrue(result.getBestFeasibleCase().getProducerMultipliers()
        .containsKey("producer scenario multiplier"));
  }
}
