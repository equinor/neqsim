package neqsim.process.fielddevelopment.integrated;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.reservoir.WellSystem;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.fielddevelopment.integrated.ChokeAndGasLiftAllocationOptimizer.AllocationResult;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Unit tests for the choke-and-gas-lift optimisation stack (NIP-1..NIP-4): the {@link GasLiftPerformanceCurve}
 * {@code fromWellSystem} bridge, the {@link ChokeableGasLiftWell} descriptor, the
 * {@link ChokeAndGasLiftAllocationOptimizer} solver, and the {@link StrupeOkeReport} deliverable.
 *
 * @author NeqSim
 * @version 1.0
 */
class ChokeGasLiftAllocationTest {

  /**
   * Builds a simple runnable oil well system for the fromWellSystem bridge test.
   *
   * @return a configured well system
   */
  private WellSystem simpleWell() {
    SystemInterface fluid = new SystemSrkEos(373.15, 250.0);
    fluid.addComponent("methane", 40.0);
    fluid.addComponent("ethane", 5.0);
    fluid.addComponent("propane", 3.0);
    fluid.addComponent("n-heptane", 52.0);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);
    Stream reservoirStream = new Stream("reservoir", fluid);
    reservoirStream.setFlowRate(5000.0, "Sm3/day");
    reservoirStream.run();

    WellSystem well = new WellSystem("Producer");
    well.setReservoirStream(reservoirStream);
    well.setIPRModel(WellSystem.IPRModel.PRODUCTION_INDEX);
    well.setProductionIndex(0.0005, "Sm3/day/bar2");
    well.setTubingLength(2500.0, "m");
    well.setTubingDiameter(4.0, "in");
    well.setWellheadPressure(50.0, "bara");
    return well;
  }

  /**
   * NIP-1: the fromWellSystem bridge must anchor the base rate on a rigorous well solve and fit the parametric GLPC so
   * it passes through the base point and peaks at the specified optimum lift.
   */
  @Test
  void testGlpcFromWellSystem() {
    WellSystem well = simpleWell();
    well.run();
    double base = well.getOperatingFlowRate("Sm3/day");
    assertTrue(base > 0.0, "well must produce a positive base rate");

    double optimumLift = 60000.0;
    double peakOil = base * 1.30;
    GasLiftPerformanceCurve curve = GasLiftPerformanceCurve.fromWellSystem(well, optimumLift, peakOil, 150000.0);
    assertNotNull(curve);
    assertEquals(base, curve.oilRateAt(0.0), 1.0e-6, "curve must pass through the base rate at zero lift");
    assertEquals(peakOil, curve.oilRateAt(optimumLift), 1.0e-3, "curve must reach the peak at the optimum lift");
    assertEquals(optimumLift, curve.optimalLiftRate(), 1.0, "curve must peak at the optimum lift");
    assertTrue(curve.incrementalSlope(1000.0) > 0.0, "slope must be positive below the optimum");
  }

  /**
   * NIP-1: the uplift convenience form must derive the peak from the base rate and the uplift fraction.
   */
  @Test
  void testGlpcFromWellSystemUplift() {
    WellSystem well = simpleWell();
    well.run();
    double base = well.getOperatingFlowRate("Sm3/day");
    GasLiftPerformanceCurve curve = GasLiftPerformanceCurve.fromWellSystemUplift(well, 0.25, 50000.0, 150000.0);
    assertEquals(base * 1.25, curve.oilRateAt(50000.0), 1.0e-3);
  }

  /**
   * NIP-2: choke opening must scale deliverability linearly and a force-shut well must produce nothing.
   */
  @Test
  void testChokeableWellScaling() {
    GasLiftPerformanceCurve curve = new GasLiftPerformanceCurve(1000.0, 0.0, 0.0, 100000.0);
    ChokeableGasLiftWell well = new ChokeableGasLiftWell("W1", curve).setMaxChokeFraction(0.5).setGor(500.0)
        .setWaterCut(0.4);
    assertEquals(1000.0, well.oilRate(0.5, 0.0), 1.0e-9, "full opening delivers the full GLPC rate");
    assertEquals(500.0, well.oilRate(0.25, 0.0), 1.0e-9, "half of the max opening delivers half the rate");
    // Produced gas = oil*GOR + lift; produced water = oil*wc/(1-wc).
    assertEquals(1000.0 * 500.0 + 20000.0, well.producedGasRate(1000.0, 20000.0), 1.0e-6);
    assertEquals(1000.0 * 0.4 / 0.6, well.producedWaterRate(1000.0), 1.0e-6);

    well.setForcedShut(true, "sand production");
    assertEquals(0.0, well.oilRate(0.5, 0.0), 1.0e-9);
    assertTrue(well.isForcedShut());
    assertEquals("sand production", well.getShutReason());
  }

  /**
   * NIP-3: with no facility constraints, every open well should be opened to its maximum choke and the shut well
   * excluded.
   */
  @Test
  void testAllocationUnconstrained() {
    ChokeableGasLiftWell w1 = new ChokeableGasLiftWell("A", new GasLiftPerformanceCurve(1000.0, 0.0, 0.0, 100000.0))
        .setMaxChokeFraction(0.6);
    ChokeableGasLiftWell w2 = new ChokeableGasLiftWell("B", new GasLiftPerformanceCurve(800.0, 0.0, 0.0, 100000.0))
        .setMaxChokeFraction(0.5);
    ChokeableGasLiftWell w3 = new ChokeableGasLiftWell("C", new GasLiftPerformanceCurve(1200.0, 0.0, 0.0, 100000.0))
        .setForcedShut(true, "lost communication");

    ChokeAndGasLiftAllocationOptimizer opt = new ChokeAndGasLiftAllocationOptimizer().addWell(w1).addWell(w2)
        .addWell(w3);
    AllocationResult r = opt.optimize();
    assertTrue(r.isFeasible());
    assertEquals(1800.0, r.getTotalOil(), 1.0e-6, "shut well contributes nothing");
    assertEquals(0.6, r.getWells().get("A").getChokeFraction(), 1.0e-9);
    assertEquals(0.0, r.getWells().get("C").getOilRate(), 1.0e-9);
    assertEquals("shut", r.getWells().get("C").getBindingConstraint());
    assertNotNull(r.toJson());
  }

  /**
   * NIP-3: a total gas-handling ceiling must be enforced by choking back the highest-GOR well first.
   */
  @Test
  void testAllocationGasConstraint() {
    // Two wells, equal oil; A has high GOR (cheap to choke for gas relief), B low GOR.
    ChokeableGasLiftWell a = new ChokeableGasLiftWell("A", new GasLiftPerformanceCurve(1000.0, 0.0, 0.0, 100000.0))
        .setMaxChokeFraction(1.0).setGor(2000.0);
    ChokeableGasLiftWell b = new ChokeableGasLiftWell("B", new GasLiftPerformanceCurve(1000.0, 0.0, 0.0, 100000.0))
        .setMaxChokeFraction(1.0).setGor(500.0);
    // Unconstrained gas = 1000*2000 + 1000*500 = 2.5e6. Cap at 1.5e6.
    ChokeAndGasLiftAllocationOptimizer opt = new ChokeAndGasLiftAllocationOptimizer().addWell(a).addWell(b)
        .setGasHandlingLimit(1.5e6);
    AllocationResult r = opt.optimize();
    assertTrue(r.getTotalGas() <= 1.5e6 + 1.0, "gas ceiling must be respected");
    assertTrue(r.isGasFeasible());
    // The high-GOR well A must be choked back more than the low-GOR well B.
    assertTrue(r.getWells().get("A").getChokeFraction() < r.getWells().get("B").getChokeFraction(),
        "high-GOR well should be choked back first");
  }

  /**
   * NIP-3: a per-well produced-gas ceiling must limit that well without touching the others.
   */
  @Test
  void testPerWellGasCeiling() {
    ChokeableGasLiftWell a = new ChokeableGasLiftWell("A", new GasLiftPerformanceCurve(1000.0, 0.0, 0.0, 100000.0))
        .setMaxChokeFraction(1.0).setGor(1000.0).setGasHandlingLimit(500000.0);
    ChokeAndGasLiftAllocationOptimizer opt = new ChokeAndGasLiftAllocationOptimizer().addWell(a);
    AllocationResult r = opt.optimize();
    assertTrue(r.getWells().get("A").getGasRate() <= 500000.0 + 1.0);
    assertEquals("gas_ceiling", r.getWells().get("A").getBindingConstraint());
  }

  /**
   * NIP-4: the strupe/oke report must rank wells by uplift and classify actions relative to the current setting.
   */
  @Test
  void testStrupeOkeReport() {
    ChokeableGasLiftWell a = new ChokeableGasLiftWell("A", new GasLiftPerformanceCurve(1000.0, 0.0, 0.0, 100000.0))
        .setMaxChokeFraction(1.0).setCurrentChokeFraction(0.2); // recommend OPEN
    ChokeableGasLiftWell b = new ChokeableGasLiftWell("B", new GasLiftPerformanceCurve(1000.0, 0.0, 0.0, 100000.0))
        .setMaxChokeFraction(1.0).setGor(3000.0).setCurrentChokeFraction(1.0); // choked back for gas -> CHOKE_BACK
    ChokeableGasLiftWell c = new ChokeableGasLiftWell("C", new GasLiftPerformanceCurve(1000.0, 0.0, 0.0, 100000.0))
        .setForcedShut(true, "life extension");

    ChokeAndGasLiftAllocationOptimizer opt = new ChokeAndGasLiftAllocationOptimizer().addWell(a).addWell(b).addWell(c)
        .setGasHandlingLimit(1.0e6);
    AllocationResult r = opt.optimize();
    List<ChokeableGasLiftWell> fleet = java.util.Arrays.asList(a, b, c);
    StrupeOkeReport report = StrupeOkeReport.build(fleet, r);
    assertNotNull(report.toTable());
    assertNotNull(report.toJson());
    assertEquals(3, report.getRecommendations().size());
    // Ranked by uplift descending: A (opening up) should be first.
    assertEquals("A", report.getRecommendations().get(0).getWellName());
    assertEquals(StrupeOkeReport.Action.OPEN, report.getRecommendations().get(0).getAction());
    // C is force-shut.
    boolean foundShut = false;
    for (StrupeOkeReport.Recommendation rec : report.getRecommendations()) {
      if (rec.getWellName().equals("C")) {
        assertEquals(StrupeOkeReport.Action.SHUT, rec.getAction());
        assertEquals("life extension", rec.getLockReason());
        foundShut = true;
      }
    }
    assertTrue(foundShut);
    assertFalse(report.getActionSummary().isEmpty());
  }
}
