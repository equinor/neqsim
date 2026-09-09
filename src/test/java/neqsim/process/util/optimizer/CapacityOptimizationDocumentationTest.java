package neqsim.process.util.optimizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import neqsim.process.design.DesignOptimizer;
import neqsim.process.design.DesignResult;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.capacity.BottleneckResult;
import neqsim.process.equipment.capacity.CapacityConstrainedEquipment;
import neqsim.process.equipment.capacity.CapacityConstraint;
import neqsim.process.equipment.capacity.CapacityConstraint.ConstraintType;
import neqsim.process.equipment.capacity.EquipmentCapacityStrategyRegistry;
import neqsim.process.equipment.capacity.HeatExchangerCapacityStrategy;
import neqsim.process.equipment.capacity.StandardConstraintType;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.expander.Expander;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.heatexchanger.HeatExchanger;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.manifold.Manifold;
import neqsim.process.equipment.pipeline.PipeBeggsAndBrills;
import neqsim.process.equipment.pump.Pump;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.processmodel.ProcessModule;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.util.optimizer.PressureBoundaryOptimizer.LiftCurveTable;
import neqsim.process.util.optimizer.ProductionOptimizer.OptimizationResult;
import neqsim.thermo.system.SystemSrkEos;

/** Executable API and physical-unit regressions for capacity and pressure-boundary guides. */
class CapacityOptimizationDocumentationTest {
  private static final Logger logger = LogManager.getLogger(CapacityOptimizationDocumentationTest.class);

  private Stream gasFeed(double rate) {
    SystemSrkEos fluid = new SystemSrkEos(298.15, 50.0);
    fluid.addComponent("methane", 0.9);
    fluid.addComponent("ethane", 0.1);
    fluid.setMixingRule("classic");
    Stream feed = new Stream("Feed", fluid);
    feed.setFlowRate(rate, "kg/hr");
    feed.run();
    return feed;
  }

  private ProcessSystem valveProcess() {
    Stream feed = gasFeed(100.0);
    ThrottlingValve valve = new ThrottlingValve("valve", feed);
    valve.setOutletPressure(30.0, "bara");
    valve.addCapacityConstraint(new CapacityConstraint("installedMassFlow", "kg/hr", ConstraintType.HARD)
        .setDesignValue(400.0).setValueSupplier(() -> feed.getFlowRate("kg/hr")));
    Stream outlet = new Stream("outlet", valve.getOutletStream());
    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(valve);
    process.add(outlet);
    process.run();
    return process;
  }

  @Test
  void pressureBoundaryQuickStartAndInfeasibleColumnsAreExecutable() {
    ProcessSystem process = valveProcess();
    Stream feed = (Stream) process.getUnit("Feed");
    Stream outlet = (Stream) process.getUnit("outlet");
    PressureBoundaryOptimizer optimizer = new PressureBoundaryOptimizer(process, feed, outlet);
    optimizer.setRateUnit("kg/hr");
    optimizer.setMinFlowRate(10.0);
    optimizer.setMaxFlowRate(500.0);
    OptimizationResult result = optimizer.findMaxFlowRate(50.0, 30.0, "bara");
    assertTrue(result.isFeasible());
    assertEquals(400.0, result.getOptimalRate(), 0.5);
    assertEquals(0.0, result.getDecisionVariables().get("totalPower_kW"), 1e-12);
    assertNotNull(result.getBottleneck());
    LiftCurveTable table = optimizer.generateLiftCurveTable(new double[] { 40.0, 50.0, 60.0 },
        new double[] { 30.0, 35.0 }, "bara");
    assertEquals(3, table.countFeasiblePoints());
    assertEquals(400.0, table.getFlowRate(0, 0), 0.5);
    assertEquals(0.0, table.getPower(0, 0), 1e-12);
    assertTrue(Double.isNaN(table.getFlowRate(0, 1)));
    assertTrue(table.toEclipseFormat().contains("1*"));
    double[] curve = optimizer.generateCapacityCurve(60.0, new double[] { 30.0, 35.0 }, "bara");
    assertEquals(400.0, curve[0], 0.5);
    assertTrue(Double.isNaN(curve[1]));
    // The wrapper restores the original feed pressure, so replay the accepted point explicitly.
    feed.setPressure(50.0, "bara");
    feed.setFlowRate(result.getOptimalRate(), "kg/hr");
    process.run();
    assertEquals(feed.getFlowRate("kg/hr"), outlet.getFlowRate("kg/hr"), 1e-8);
  }

  @Test
  void infeasibleCapacityMatrixIsStrictJsonWithNullNumbers() {
    LiftCurveTable table = new LiftCurveTable("quoted \"table\"", new double[] { 50.0 }, new double[] { 30.0, 40.0 },
        new double[][] { { 400.0, Double.NaN } }, new double[][] { { 0.0, Double.POSITIVE_INFINITY } },
        new String[][] { { "valve", "INFEASIBLE" } }, "bara", "kg/hr");
    JsonReader reader = new JsonReader(new StringReader(table.toJson()));
    reader.setStrictness(Strictness.STRICT);
    JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
    assertEquals("quoted \"table\"", json.get("tableName").getAsString());
    assertEquals("kg/hr", json.get("rateUnit").getAsString());
    assertTrue(json.getAsJsonArray("flowRates").get(0).getAsJsonArray().get(1).isJsonNull());
    assertTrue(json.getAsJsonArray("powers").get(0).getAsJsonArray().get(1).isJsonNull());
    assertEquals(1, json.get("feasiblePoints").getAsInt());
  }

  @Test
  void minimumPowerUsesKilowattObjectiveAndExplicitCompressorSetpoint() {
    Stream feed = gasFeed(300.0);
    Compressor compressor = new Compressor("compressor", feed);
    compressor.setOutletPressure(100.0, "bara");
    compressor.setPolytropicEfficiency(0.75);
    compressor.setUsePolytropicCalc(true);
    compressor.getMechanicalDesign().setMaxDesignPower(1000.0);
    Cooler cooler = new Cooler("cooler", compressor.getOutletStream());
    cooler.setOutTemperature(40.0, "C");
    Stream outlet = new Stream("outlet", cooler.getOutletStream());
    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(compressor);
    process.add(cooler);
    process.add(outlet);
    process.run();
    PressureBoundaryOptimizer optimizer = new PressureBoundaryOptimizer(process, feed, outlet);
    optimizer.setAutoConfigureCompressors(false);
    optimizer.setMinFlowRate(10.0);
    optimizer.setMaxFlowRate(500.0);
    optimizer.setMaxPowerLimit(1000.0);
    optimizer.setMinSurgeMargin(0.1);
    optimizer.setMaxUtilization(1.0);
    optimizer.setSpeedLimits(0.0, Double.MAX_VALUE);
    optimizer.setTolerance(0.001);
    optimizer.setPressureTolerance(0.02);
    OptimizationResult result = optimizer.findMinimumPowerOperatingPoint(50.0, 100.0, "bara", 250.0);
    assertTrue(result.isFeasible());
    assertEquals(250.0, result.getOptimalRate(), 0.5);
    assertTrue(result.getObjectiveValues().get("totalPower") > 0.0);
    feed.setFlowRate(result.getOptimalRate(), "kg/hr");
    process.run();
    assertEquals(optimizer.calculateTotalPower(), result.getObjectiveValues().get("totalPower"), 1e-6);
  }

  @Test
  void minimumRetentionNpshAndApproachUseInverseUtilization() {
    CapacityConstraint retention = StandardConstraintType.SEPARATOR_OIL_RETENTION_TIME.createConstraint()
        .setMinValue(3.0).setCurrentValue(4.0);
    assertEquals("min", retention.getUnit());
    assertTrue(retention.isMinimumConstraint());
    assertEquals(0.75, retention.getUtilization(), 1e-12);
    assertFalse(retention.isViolated());
    retention.setCurrentValue(2.0);
    assertTrue(retention.isViolated());
    CapacityConstraint npsh = StandardConstraintType.PUMP_NPSH_MARGIN.createConstraint().setMinValue(1.0)
        .setCurrentValue(2.0);
    assertEquals(0.5, npsh.getUtilization(), 1e-12);
    npsh.setCurrentValue(0.5);
    assertTrue(npsh.isHardLimitExceeded());
    CapacityConstraint approach = StandardConstraintType.HEAT_EXCHANGER_APPROACH_TEMP.createConstraint()
        .setMinValue(5.0).setCurrentValue(10.0);
    assertEquals(0.5, approach.getUtilization(), 1e-12);
    assertEquals("approachTemp", approach.getName());
  }

  @Test
  void manifoldPipeAndExpanderExamplesUsePublicApisAndPhysicalUnits() {
    Stream feed = gasFeed(1000.0);
    PipeBeggsAndBrills pipe = new PipeBeggsAndBrills("Export Line", feed);
    pipe.setLength(5000.0);
    pipe.setDiameter(0.2032);
    pipe.setThickness(0.008);
    pipe.setSupportArrangement("Medium stiff");
    pipe.run();
    CapacityConstraint gradient = StandardConstraintType.PIPE_PRESSURE_DROP.createConstraint().setDesignValue(2.0)
        .setValueSupplier(() -> pipe.getPressureDrop() / (pipe.getLength() / 1000.0));
    CapacityConstraint erosional = StandardConstraintType.PIPE_EROSIONAL_VELOCITY.createConstraint()
        .setDesignValue(100.0).setValueSupplier(() -> 100.0 * pipe.getMixtureVelocity() / pipe.getErosionalVelocity());
    pipe.addCapacityConstraint(gradient);
    pipe.addCapacityConstraint(erosional);
    assertEquals(pipe.getPressureDrop() / 5.0, gradient.getCurrentValue(), 1e-12);
    assertEquals(pipe.getMixtureVelocity() / pipe.getErosionalVelocity(), erosional.getUtilization(), 1e-12);
    assertTrue(pipe.calculateLOF() >= 0.0);
    assertTrue(pipe.calculateFRMS() >= 0.0);
    assertTrue(pipe.calculateAIV() >= 0.0);
    assertTrue(pipe.calculateAIVLikelihoodOfFailure() >= 0.0);
    assertNotNull(pipe.getFIVAnalysisJson());
    pipe.setMaxDesignVelocity(15.0);
    pipe.setMaxDesignLOF(0.5);
    pipe.setMaxDesignFRMS(400.0);
    pipe.setMaxDesignAIV(10.0);
    assertEquals(0.5, pipe.getCapacityConstraints().get("LOF").getDesignValue(), 1e-12);
    Manifold manifold = new Manifold("Production Manifold");
    manifold.addStream(feed);
    manifold.addStream(feed.clone("Second feed"));
    manifold.setSplitFactors(new double[] { 0.4, 0.3, 0.3 });
    manifold.setMaxHeaderVelocityDesign(15.0);
    manifold.setMaxBranchVelocityDesign(15.0);
    manifold.setHeaderInnerDiameter(0.3);
    manifold.setBranchInnerDiameter(0.15);
    manifold.run();
    assertTrue(manifold.calculateHeaderLOF() >= 0.0);
    assertTrue(manifold.calculateHeaderFRMS() >= 0.0);
    assertTrue(manifold.calculateBranchLOF() >= 0.0);
    assertEquals(1000.0 * 2.0, manifold.getMixedStream().getFlowRate("kg/hr"), 1e-7);
    Expander expander = new Expander("X-100", feed);
    expander.setOutletPressure(20.0);
    expander.run();
    expander.setRatedRecoveredPower(5000.0);
    assertTrue(expander.getPower("kW") < 0.0);
    assertEquals(Math.abs(expander.getPower("kW")) / 5000.0,
        expander.getCapacityConstraints().get("recoveredPower").getUtilization(), 1e-12);
  }

  @Test
  void inheritedConstraintsAndModuleResultsKeepPercentAndUnits() {
    Stream feed = gasFeed(1000.0);
    RatedStream rated = new RatedStream("Rated line", feed);
    rated.setDesignFlowRate(1200.0);
    ProcessSystem system = new ProcessSystem();
    system.add(feed);
    system.add(rated);
    ProcessModule inner = new ProcessModule("Inner");
    inner.add(system);
    ProcessModule outer = new ProcessModule("Outer");
    outer.add(inner);
    outer.run();
    assertEquals(1, outer.getConstrainedEquipment().size());
    BottleneckResult bottleneck = outer.findBottleneck();
    assertTrue(bottleneck.hasBottleneck());
    assertEquals("Rated line", bottleneck.getEquipmentName());
    assertEquals("installedMassFlow", bottleneck.getConstraintName());
    assertEquals(100.0 * 1000.0 / 1200.0, outer.getCapacityUtilizationSummary().get("Rated line"), 1e-8);
    assertFalse(outer.isAnyEquipmentOverloaded());
    assertFalse(outer.isAnyHardLimitExceeded());
    assertNotNull(outer.getEquipmentNearCapacityLimit());
    DesignOptimizer design = DesignOptimizer.forProcess(outer);
    assertTrue(design.isModuleMode());
    assertEquals(outer, design.getModule());
    DesignResult validation = design.validate();
    assertFalse(validation.isConverged());
    assertEquals(DesignResult.ExecutionStatus.VALIDATED, validation.getExecutionStatus());
    ProcessOptimizationEngine engine = new ProcessOptimizationEngine(outer);
    engine.setFeedStreamName("Feed");
    engine.setOutletStreamName("Rated line");
    assertEquals("Feed", engine.getFeedStreamName());
    assertEquals("Rated line", engine.getOutletStreamName());
    assertEquals(25.0, engine.getOutletTemperature("C"), 1e-8);
    assertEquals(1000.0, engine.getOutletFlowRate("kg/hr"), 1e-8);
  }

  @Test
  void installedHeaterStrategyRetainsTheCompleteStrategyContract() {
    Stream feed = gasFeed(1000.0);
    Heater heater = new Heater("Process Heater", feed);
    heater.setOutTemperature(350.0);
    heater.run();
    InstalledHeaterDutyStrategy strategy = new InstalledHeaterDutyStrategy();
    assertTrue(strategy.supports(heater));
    assertEquals(5000.0, strategy.evaluateMaxCapacity(heater), 1e-12);
    assertEquals(Math.abs(heater.getDuty()) / 1000.0 / 5000.0, strategy.evaluateCapacity(heater), 1e-12);
    assertNotNull(strategy.getBottleneckConstraint(heater));
    assertTrue(strategy.getViolations(heater).isEmpty());
    assertTrue(strategy.isWithinHardLimits(heater));
    assertTrue(strategy.isWithinSoftLimits(heater));
    EquipmentCapacityStrategyRegistry registry = EquipmentCapacityStrategyRegistry.getInstance();
    try {
      registry.register(strategy);
      assertEquals(strategy, registry.findStrategy(heater));
    } finally {
      registry.unregister(strategy.getName());
    }
  }

  @Test
  void exporterReceivesBhpCellsRatherThanCapacityRates() {
    EclipseVFPExporter exporter = new EclipseVFPExporter(1);
    exporter.setDatumDepth(1500.0);
    exporter.setFlowRateType("GAS");
    exporter.setUnitSystem("METRIC");
    exporter.setFlowRates(new double[] { 10000.0, 20000.0 });
    exporter.setTHPs(new double[] { 30.0, 40.0 });
    exporter.setWaterCuts(new double[] { 0.0 });
    exporter.setGORs(new double[] { 0.0 });
    exporter.setALQs(new double[] { 0.0 });
    double[][][][][] bhp = new double[2][2][1][1][1];
    bhp[0][0][0][0][0] = 35.0;
    bhp[1][0][0][0][0] = 42.0;
    bhp[0][1][0][0][0] = 45.0;
    bhp[1][1][0][0][0] = 52.0;
    exporter.setBHPTable(bhp);
    String output = exporter.getVFPPRODString();
    assertTrue(output.contains("VFPPROD"));
    assertTrue(output.contains("35.00"));
    assertTrue(output.contains("52.00"));
  }

  /** Runs these documentation checks when a JUnit launcher is unavailable locally. */
  public static void main(String[] args) throws Exception {
    CapacityOptimizationDocumentationTest test = new CapacityOptimizationDocumentationTest();
    for (java.lang.reflect.Method method : CapacityOptimizationDocumentationTest.class.getDeclaredMethods()) {
      if (method.getAnnotation(Test.class) != null) {
        logger.info("Running {}", method.getName());
        method.invoke(test);
      }
    }
  }

  private static class RatedStream extends Stream implements CapacityConstrainedEquipment {
    private static final long serialVersionUID = 1L;

    RatedStream(String name, StreamInterface inlet) {
      super(name, inlet);
      addCapacityConstraint(new CapacityConstraint("installedMassFlow", "kg/hr", ConstraintType.HARD)
          .setDesignValue(12000.0).setValueSupplier(() -> getFlowRate("kg/hr")));
    }

    void setDesignFlowRate(double flowRate) {
      getCapacityConstraints().get("installedMassFlow").setDesignValue(flowRate);
    }
  }

  private static class InstalledHeaterDutyStrategy extends HeatExchangerCapacityStrategy {
    @Override
    public boolean supports(ProcessEquipmentInterface equipment) {
      return equipment instanceof Heater && equipment.getName().equals("Process Heater");
    }

    @Override
    public int getPriority() {
      return 100;
    }

    @Override
    public String getName() {
      return "InstalledHeaterDutyStrategy";
    }

    @Override
    public double evaluateCapacity(ProcessEquipmentInterface equipment) {
      return getConstraints(equipment).get("installedDuty").getUtilization();
    }

    @Override
    public double evaluateMaxCapacity(ProcessEquipmentInterface equipment) {
      return 5000.0;
    }

    @Override
    public Map<String, CapacityConstraint> getConstraints(ProcessEquipmentInterface equipment) {
      Heater heater = (Heater) equipment;
      Map<String, CapacityConstraint> constraints = new LinkedHashMap<>();
      constraints.put("installedDuty", new CapacityConstraint("installedDuty", "kW", ConstraintType.HARD)
          .setDesignValue(5000.0).setValueSupplier(() -> Math.abs(heater.getDuty()) / 1000.0));
      return constraints;
    }
  }
}
