package neqsim.process.equipment.network;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Map;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.reservoir.SimpleReservoir;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/** Regression tests for the physical state and evidence returned by coupled network optimization. */
class LoopedPipeNetworkOperatingPointTest {
  @Test
  void coupledSearchReplaysTheSelectedPointAndTransfersTheWholeFluid() {
    Fixture fixture = new Fixture();
    Map<String, Double> result = fixture.network.runCoupled();

    assertEquals(1.0, result.get("converged"));
    assertTrue(result.get("arrivalPressure_bara") > 30.0,
        "The selected pressure must differ from the rejected final pressure trial");
    assertOperatingPoint(fixture, result);
    assertArrayEquals(new double[] { 0.9, 0.1 }, fixture.feed.getFluid().getMolarComposition(), 1e-10);
    assertEquals(313.15, fixture.feed.getTemperature("K"), 1e-10);

    // A separately built topside at the reported outlet state must reproduce the selected evidence.
    Stream independentFeed = new Stream("independent feed",
        fixture.network.getOutletStream("platform").getFluid().clone());
    Separator independentSeparator = separator(independentFeed);
    Compressor independentCompressor = compressor(independentSeparator.getGasOutStream(), 150.0);
    ProcessSystem independentTopside = new ProcessSystem();
    independentTopside.add(independentFeed);
    independentTopside.add(independentSeparator);
    independentTopside.add(independentCompressor);
    independentTopside.run();
    assertEquals(result.get("separatorUtilization"), independentSeparator.getCapacityUtilization(), 1e-9);
    assertEquals(result.get("compressorPower_MW"), independentCompressor.getPower("MW"), 1e-8);
  }

  @Test
  void fullFieldEvidenceAndPricedRevenueDescribeTheFinalState() {
    Fixture fixture = new Fixture();
    fixture.network.setWellPrice("choke", 0.25);
    Map<String, Object> result = fixture.network.optimizeFullField(5, 0.01);

    assertEquals(1.0, (Double) result.get("converged"));
    assertEquals((Double) result.get("totalFlow_kghr"), fixture.feed.getFlowRate("kg/hr"), 1e-5);
    assertEquals((Double) result.get("arrivalPressure_bara"), fixture.feed.getPressure("bara"), 1e-10);
    assertEquals((Double) result.get("compressorPower_MW"), fixture.compressor.getPower("MW"), 1e-8);
    assertEquals((Double) result.get("separatorUtilization"), fixture.separator.getCapacityUtilization(), 1e-9);
    assertTrue(fixture.network.isTopsideFeasible());
    assertEquals(fixture.network.getPipeFlowRate("choke") * 0.25, (Double) result.get("revenue_usd_hr"), 1e-5,
        "Unpriced IPR flow is not revenue");
    Map<String, double[]> allocation = fixture.network.getWellAllocationResults();
    assertEquals(fixture.network.getPipeFlowRate("choke"), allocation.get("choke")[0], 1e-5);
    assertEquals((Double) result.get("revenue_usd_hr"), allocation.get("choke")[1], 1e-5);
    assertEquals(0.0, allocation.get("ipr")[1], 1e-10);
  }

  @Test
  void forecastSelectsFeasibleTopsidePointsAndIntegratesOnlyTheirRates() {
    Fixture fixture = new Fixture();
    fixture.network.setWellPrice("choke", 0.25);
    Map<String, double[]> result = fixture.network.productionForecastWithOptimization("res",
        new double[] { 250.0, 230.0 }, new double[] { 0.0, 1.0 }, 3, 0.01);

    for (int point = 0; point < 2; point++) {
      assertEquals(1.0, result.get("feasible")[point]);
      assertTrue(result.get("compressor_power_MW")[point] <= fixture.network.getMaxCompressorPowerMW());
      assertTrue(result.get("separator_util_pct")[point] <= 100 * fixture.network.getMaxSeparatorUtilization());
      assertTrue(Double.isFinite(result.get("rate_kghr")[point]));
      assertEquals(result.get("rate_kghr")[point] * 0.25, result.get("revenue_usd_hr")[point], 1e-5);
    }
    assertEquals(result.get("arrival_pressure_bara")[1], fixture.feed.getPressure("bara"), 1e-10);
    assertEquals(result.get("rate_kghr")[1], fixture.feed.getFlowRate("kg/hr"), 1e-5);
    assertEquals(result.get("compressor_power_MW")[1], fixture.compressor.getPower("MW"), 1e-8);
    assertEquals(0.5 * (result.get("rate_kghr")[0] + result.get("rate_kghr")[1]) * 8760.0,
        result.get("cumulative_kg")[1], 1e-3);
  }

  @Test
  void infeasibleForecastRowsDoNotClaimProductionOrAccumulateReserves() {
    Fixture fixture = new Fixture();
    fixture.network.setMaxCompressorPowerMW(1e-6);
    Map<String, double[]> result = fixture.network.productionForecastWithOptimization("res",
        new double[] { 250.0, 230.0 }, new double[] { 0.0, 1.0 }, 1, 0.01);

    assertArrayEquals(new double[] { 0.0, 0.0 }, result.get("feasible"), 1e-10);
    for (int point = 0; point < 2; point++) {
      assertTrue(Double.isNaN(result.get("rate_kghr")[point]));
      assertTrue(Double.isNaN(result.get("revenue_usd_hr")[point]));
      assertTrue(Double.isNaN(result.get("cumulative_kg")[point]));
      assertTrue(result.get("compressor_power_MW")[point] > fixture.network.getMaxCompressorPowerMW());
    }
    assertEquals(70.0, fixture.feed.getPressure("bara"), 1e-10,
        "No feasible candidate restores and evaluates the original arrival pressure");
    assertEquals(result.get("compressor_power_MW")[1], fixture.compressor.getPower("MW"), 1e-8);
  }

  @Test
  void topsideFeasibilityUsesAggregateCompressorPowerAndRejectsInvalidEvidence() {
    Fixture fixture = new Fixture();
    fixture.network.runCoupled();
    Compressor secondStage = compressor(fixture.compressor.getOutletStream(), 180.0);
    fixture.topside.add(secondStage);
    fixture.topside.run();
    double firstPower = fixture.compressor.getPower("MW");
    double secondPower = secondStage.getPower("MW");
    fixture.network
        .setMaxCompressorPowerMW(Math.max(firstPower, secondPower) + 0.5 * Math.min(firstPower, secondPower));
    assertFalse(fixture.network.isTopsideFeasible(),
        "Each compressor fits individually, but their total exceeds the limit");
    fixture.network.setMaxCompressorPowerMW(firstPower + secondPower + 1.0);
    assertTrue(fixture.network.isTopsideFeasible());

    fixture.topside.add(new Compressor("failed compressor", secondStage.getOutletStream()) {
      private static final long serialVersionUID = 1L;

      @Override
      public double getPower(String unit) {
        return Double.NaN;
      }
    });
    assertFalse(fixture.network.isTopsideFeasible(), "Nonfinite power cannot establish feasibility");
  }

  @Test
  void solvedNodeCompositionAndTemperatureArePreservedInTheOutletStream() {
    SystemInterface template = fluid(298.15, 0.9);
    LoopedPipeNetwork network = new LoopedPipeNetwork("compositional outlet");
    network.setFluidTemplate(template);
    network.setSolverType(LoopedPipeNetwork.SolverType.NEWTON_RAPHSON);
    network.setTolerance(0.1);
    network.setMaxIterations(200);
    network.addSourceNode("source", 120.0, 0.0);
    network.addFixedPressureSinkNode("sink", 80.0);
    network.addPipe("source", "sink", "pipe", 10000.0, 0.25, 0.00005);
    network.setNodeFluid("source", fluid(333.15, 0.7));
    network.setCompositionalHydraulicsEnabled(true);
    network.run();

    assertTrue(network.isConverged());
    SystemInterface nodeFluid = network.getNodeFluid("sink");
    StreamInterface outlet = network.getOutletStream("sink");
    assertArrayEquals(new double[] { 0.7, 0.3 }, outlet.getFluid().getMolarComposition(), 1e-8);
    assertEquals(nodeFluid.getTemperature(), outlet.getTemperature("K"), 1e-8);
    assertEquals(network.getNodePressure("sink"), outlet.getPressure("bara"), 1e-10);
    assertEquals(network.getPipeFlowRate("pipe"), outlet.getFlowRate("kg/hr"), 1e-5);
  }

  @Test
  void transientStepReportsTheEndStateAndAccountsForActualReservoirWithdrawal() {
    Fixture fixture = new Fixture();
    SimpleReservoir reservoir = reservoir();
    reservoir.addGasProducer("producer");
    fixture.network.attachReservoir("res", reservoir, "gas");
    double initialInventory = reservoir.getReservoirFluid().getMass("kg");
    double initialPressure = reservoir.getReservoirFluid().getPressure("bara");
    Map<String, Object> result = fixture.network.runTransientCoupled(7 * 24 * 3600.0, 0, 0.01);
    double inventoryLoss = initialInventory - reservoir.getReservoirFluid().getMass("kg");

    assertEquals(inventoryLoss, (Double) result.get("produced_kg"), inventoryLoss * 1e-8);
    assertTrue((Double) result.get("pressure_res_bara") < initialPressure);
    assertEquals((Double) result.get("pressure_res_bara"), fixture.network.getNodePressure("res"), 1e-10);
    assertEquals((Double) result.get("totalFlow_kghr"), fixture.feed.getFlowRate("kg/hr"), 1e-5);
    assertEquals((Double) result.get("compressorPower_MW"), fixture.compressor.getPower("MW"), 1e-8);
    assertTrue(Math.abs((Double) result.get("totalFlow_kghr") - (Double) result.get("initialFlow_kghr")) > 1.0);
    fixture.network.run();
    assertEquals((Double) result.get("totalFlow_kghr"), fixture.network.getTotalSinkFlow() * 3600.0, 1e-4,
        "A fresh solve at the reported depleted pressure must reproduce the end rate");
  }

  @Test
  void coupledForecastCumulativeMassMatchesReservoirInventoryAndPeriodDuration() {
    Fixture fixture = new Fixture();
    fixture.network.setTopsideModel(null, null);
    SimpleReservoir reservoir = reservoir();
    reservoir.addGasProducer("producer");
    fixture.network.attachReservoir("res", reservoir, "gas");
    double initialInventory = reservoir.getReservoirFluid().getMass("kg");
    double intervalYears = 0.03;
    Map<String, double[]> result = fixture.network
        .productionForecastCoupled(new double[] { 0.0, intervalYears, 2 * intervalYears }, 0, 0.01);
    double inventoryLoss = initialInventory - reservoir.getReservoirFluid().getMass("kg");

    assertEquals(inventoryLoss, result.get("cumulative_kg")[2], inventoryLoss * 1e-8);
    assertTrue(result.get("rate_kghr")[0] > result.get("rate_kghr")[1]);
    assertTrue(result.get("rate_kghr")[1] > result.get("rate_kghr")[2]);
    double intervalHours = intervalYears * 365.25 * 24.0;
    assertEquals(result.get("period_rate_kghr")[1] * intervalHours, result.get("cumulative_kg")[1], 1e-5);
    assertEquals((result.get("period_rate_kghr")[1] + result.get("period_rate_kghr")[2]) * intervalHours,
        result.get("cumulative_kg")[2], 1e-5);
    assertEquals(result.get("rate_kghr")[2], fixture.network.getTotalSinkFlow() * 3600.0, 1e-5);
    assertEquals(result.get("pressure_res_bara")[2], fixture.network.getNodePressure("res"), 1e-10);
  }

  @Test
  void infeasibleInitialTopsideDoesNotDepleteTheReservoir() {
    Fixture fixture = new Fixture();
    fixture.network.setMaxCompressorPowerMW(1e-6);
    SimpleReservoir reservoir = reservoir();
    reservoir.addGasProducer("producer");
    fixture.network.attachReservoir("res", reservoir, "gas");
    double initialInventory = reservoir.getReservoirFluid().getMass("kg");
    Map<String, Object> result = fixture.network.runTransientCoupled(24 * 3600.0, 0, 0.01);

    assertEquals(0.0, (Double) result.get("periodFeasible"));
    assertEquals(0.0, (Double) result.get("produced_kg"));
    assertEquals(initialInventory, reservoir.getReservoirFluid().getMass("kg"), 1e-6);
  }

  @Test
  void aReservoirWithTwoAttachedWellsIsAdvancedOncePerSubstep() {
    Fixture fixture = new Fixture();
    fixture.network.setTopsideModel(null, null);
    fixture.network.addSourceNode("res2", 250.0, 0.0);
    fixture.network.addWellIPR("res2", "bhp", "ipr2", 5e-6, false);
    SimpleReservoir reservoir = reservoir();
    reservoir.addGasProducer("producer one");
    reservoir.addGasProducer("producer two");
    fixture.network.attachReservoir("res", reservoir, "gas", 0);
    fixture.network.attachReservoir("res2", reservoir, "gas", 1);
    fixture.network.run();
    double initialRateKgS = fixture.network.getTotalSinkFlow();
    double initialInventory = reservoir.getReservoirFluid().getMass("kg");
    double duration = 2 * 24 * 3600.0;
    Map<String, Object> result = fixture.network.runTransientCoupled(duration, 0, 0.01);
    double inventoryLoss = initialInventory - reservoir.getReservoirFluid().getMass("kg");

    assertEquals(duration, reservoir.getTime(), 1e-9);
    assertEquals(initialRateKgS * duration, inventoryLoss, inventoryLoss * 1e-6);
    assertEquals(inventoryLoss, (Double) result.get("produced_kg"), inventoryLoss * 1e-8);
    assertEquals((Double) result.get("pressure_res_bara"), (Double) result.get("pressure_res2_bara"), 1e-10);
  }

  private static SimpleReservoir reservoir() {
    SystemInterface reservoirFluid = fluid(373.15, 0.9);
    reservoirFluid.setPressure(250.0);
    SimpleReservoir reservoir = new SimpleReservoir("reservoir inventory");
    reservoir.setReservoirFluid(reservoirFluid, 1e7, 0.0, 0.0);
    return reservoir;
  }

  private static void assertOperatingPoint(Fixture fixture, Map<String, Double> result) {
    assertTrue(fixture.network.isConverged());
    assertTrue(fixture.network.isTopsideFeasible());
    assertEquals(0.0, fixture.network.getMassBalanceError(), 1e-6);
    assertEquals(result.get("arrivalPressure_bara"), fixture.network.getNodePressure("platform"), 1e-10);
    assertEquals(result.get("arrivalPressure_bara"), fixture.feed.getPressure("bara"), 1e-10);
    assertEquals(result.get("totalFlow_kghr"), fixture.feed.getFlowRate("kg/hr"), 1e-5);
    assertEquals(result.get("totalFlow_kghr"), fixture.compressor.getOutletStream().getFlowRate("kg/hr"), 1e-5);
    assertEquals(result.get("compressorPower_MW"), fixture.compressor.getPower("MW"), 1e-8);
    assertEquals(result.get("separatorUtilization"), fixture.separator.getCapacityUtilization(), 1e-9);
    assertTrue(result.get("compressorPower_MW") > 0.0);
  }

  private static SystemInterface fluid(double temperature, double methaneFraction) {
    SystemInterface fluid = new SystemSrkEos(temperature, 50.0);
    fluid.addComponent("methane", methaneFraction);
    fluid.addComponent("ethane", 1.0 - methaneFraction);
    fluid.setMixingRule("classic");
    return fluid;
  }

  private static Separator separator(StreamInterface inlet) {
    Separator separator = new Separator("separator", inlet);
    separator.setInternalDiameter(5.0);
    separator.setSeparatorLength(15.0);
    return separator;
  }

  private static Compressor compressor(StreamInterface inlet, double outletPressure) {
    Compressor compressor = new Compressor("compressor " + outletPressure, inlet);
    compressor.setOutletPressure(outletPressure);
    compressor.setUsePolytropicCalc(true);
    compressor.setPolytropicEfficiency(0.78);
    return compressor;
  }

  private static final class Fixture {
    private final LoopedPipeNetwork network = new LoopedPipeNetwork("coupled operating point");
    private final Stream feed = new Stream("feed", fluid(350.0, 0.2));
    private final Separator separator = separator(feed);
    private final Compressor compressor = compressor(separator.getGasOutStream(), 150.0);
    private final ProcessSystem topside = new ProcessSystem();

    private Fixture() {
      network.setFluidTemplate(fluid(318.15, 0.9));
      network.setSolverType(LoopedPipeNetwork.SolverType.NEWTON_RAPHSON);
      network.setTolerance(0.001);
      network.setMaxIterations(200);
      network.addSourceNode("res", 250.0, 0.0);
      network.addJunctionNode("bhp");
      network.addJunctionNode("wh");
      network.addFixedPressureSinkNode("platform", 70.0);
      network.addWellIPR("res", "bhp", "ipr", 5e-6, false);
      network.addChoke("bhp", "wh", "choke", 4000.0, 20.0);
      network.addPipe("wh", "platform", "pipe", 10000.0, 0.25, 0.00005);
      network.getNode("platform").setTemperature(313.15);
      topside.add(feed);
      topside.add(separator);
      topside.add(compressor);
      network.setTopsideModel(topside, "platform");
      network.setMaxSeparatorUtilization(0.99);
      network.setMaxCompressorPowerMW(2.0);
      network.setMaxCouplingIterations(7);
    }
  }
}
