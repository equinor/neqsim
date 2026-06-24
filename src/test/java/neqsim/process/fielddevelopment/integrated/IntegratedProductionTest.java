package neqsim.process.fielddevelopment.integrated;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the integrated production-modelling stack (reservoir, wells, flowlines, network solve, gas-lift
 * allocation, well-test matching, and reservoir-to-market optimisation).
 *
 * <p>
 * The tests use analytic / data-driven deliverability curves ({@code fromVogel} / {@code fromArrays}) rather than
 * {@code fromWellSystem} so they are fast, deterministic, and do not run a full thermodynamic flash.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
class IntegratedProductionTest {

  /** Numerical tolerance for rate comparisons in Sm3/day. */
  private static final double RATE_TOL = 1.0e-6;

  /**
   * A Vogel deliverability curve must be strictly decreasing in back pressure and reach zero at the shut-in pressure.
   */
  @Test
  void testDeliverabilityCurveMonotonic() {
    WellDeliverabilityCurve curve = WellDeliverabilityCurve.fromVogel(2.0e6, 250.0);
    assertEquals(250.0, curve.getShutInPressure(), 1.0e-9);
    assertTrue(curve.getAbsoluteOpenFlowPotential() > 1.9e6);
    double previous = Double.MAX_VALUE;
    for (double p = 0.0; p <= 250.0; p += 25.0) {
      double q = curve.rateAt(p);
      assertTrue(q >= -RATE_TOL, "rate must be non-negative at p=" + p);
      assertTrue(q <= previous + RATE_TOL, "rate must be non-increasing at p=" + p);
      previous = q;
    }
    assertEquals(0.0, curve.rateAt(250.0), RATE_TOL);
    assertTrue(curve.slopeAt(125.0) < 0.0, "slope must be negative");
  }

  /**
   * A tabulated curve must interpolate linearly between sample points.
   */
  @Test
  void testDeliverabilityCurveInterpolation() {
    WellDeliverabilityCurve curve = WellDeliverabilityCurve.fromArrays(new double[] { 40.0, 90.0, 140.0 },
        new double[] { 3000.0, 1500.0, 0.0 });
    assertEquals(2250.0, curve.rateAt(65.0), 1.0e-6);
    assertEquals(750.0, curve.rateAt(115.0), 1.0e-6);
    assertEquals(0.0, curve.rateAt(140.0), RATE_TOL);
  }

  /**
   * A single well tied to an export sink must converge and produce a positive rate between the reservoir and export
   * pressures.
   */
  @Test
  void testNetworkSolveSingleWell() {
    WellDeliverabilityCurve curve = WellDeliverabilityCurve.fromVogel(1.0e6, 200.0);
    NetworkNewtonSolver solver = new NetworkNewtonSolver();
    solver.addNode(NetworkNode.reservoir("RES", 200.0));
    solver.addNode(NetworkNode.manifold("WH", 80.0));
    solver.addNode(NetworkNode.sink("EXP", 40.0));
    solver.addBranch(new WellBranch("well", "RES", "WH", curve, 200.0));
    solver.addBranch(new FlowlineBranch("line", "WH", "EXP", 0.0, 1.0e-9, 0.0));
    solver.setTolerance(10.0);
    NetworkNewtonSolver.NetworkSolutionResult result = solver.solve();
    assertTrue(result.isConverged(), "network must converge, method=" + result.getMethod());
    double q = result.getBranchFlows().get("line");
    assertTrue(q > 0.0, "flow must be positive");
    double whp = result.getNodePressures().get("WH");
    assertTrue(whp > 40.0 && whp < 200.0, "wellhead pressure between export and reservoir");
  }

  /**
   * Closing the export boundary down to the reservoir pressure must drive the field rate to zero.
   */
  @Test
  void testNetworkNoFlowWhenSinkAtReservoirPressure() {
    WellDeliverabilityCurve curve = WellDeliverabilityCurve.fromVogel(1.0e6, 200.0);
    NetworkNewtonSolver solver = new NetworkNewtonSolver();
    solver.addNode(NetworkNode.reservoir("RES", 200.0));
    solver.addNode(NetworkNode.manifold("WH", 199.0));
    solver.addNode(NetworkNode.sink("EXP", 200.0));
    solver.addBranch(new WellBranch("well", "RES", "WH", curve, 200.0));
    solver.addBranch(new FlowlineBranch("line", "WH", "EXP", 0.0, 1.0e-9, 0.0));
    solver.setMinPressure(200.0);
    solver.setTolerance(1.0);
    NetworkNewtonSolver.NetworkSolutionResult result = solver.solve();
    assertEquals(0.0, result.getBranchFlows().get("line"), 1.0);
  }

  /**
   * A gas material-balance drive must lose pressure monotonically as it produces.
   */
  @Test
  void testGasDriveDepletion() {
    MaterialBalanceGasDrive drive = new MaterialBalanceGasDrive(250.0, 5.0e9, 0.90);
    assertEquals(250.0, drive.getReservoirPressure(), 1.0e-9);
    assertEquals(5.0e9, drive.getInPlaceVolume(), 1.0);
    drive.produce(1.0e9, 365.0);
    double pAfter = drive.getReservoirPressure();
    assertTrue(pAfter < 250.0, "pressure must drop after production");
    assertEquals(1.0e9, drive.getCumulativeProduction(), 1.0);
  }

  /**
   * An oil tank drive must deplete and never fall below abandonment pressure.
   */
  @Test
  void testOilTankDriveDepletion() {
    OilTankDrive drive = new OilTankDrive(220.0, 8.0e6, 120.0, 90.0);
    assertEquals(220.0, drive.getReservoirPressure(), 1.0e-9);
    drive.produce(8.0e6, 3650.0);
    assertTrue(drive.getReservoirPressure() >= 90.0 - 1.0e-6, "pressure must respect abandonment floor");
  }

  /**
   * Gas-lift allocation must honour the gas budget and apply the equal-slope (Lagrangian) optimality condition across
   * wells.
   */
  @Test
  void testGasLiftAllocationHonoursBudget() {
    GasLiftNetworkOptimizer opt = new GasLiftNetworkOptimizer();
    opt.addWell("A", new GasLiftPerformanceCurve(800.0, 30.0, 0.02, 200000.0));
    opt.addWell("B", new GasLiftPerformanceCurve(500.0, 45.0, 0.04, 200000.0));
    opt.addWell("C", new GasLiftPerformanceCurve(1200.0, 20.0, 0.015, 200000.0));
    GasLiftNetworkOptimizer.AllocationResult result = opt.allocate(150000.0);
    assertTrue(result.getTotalLift() <= 150000.0 * 1.001, "must not exceed budget");
    assertTrue(result.getTotalLift() > 0.0, "must allocate some gas");
    assertTrue(result.getTotalOil() > 2500.0, "total oil must exceed combined base rates");
    assertEquals(3, result.getLiftRates().size());
  }

  /**
   * With an unlimited gas budget, each well should be driven towards its own optimal lift rate.
   */
  @Test
  void testGasLiftUnlimitedReachesOptimum() {
    GasLiftPerformanceCurve curve = new GasLiftPerformanceCurve(800.0, 30.0, 0.02, 200000.0);
    GasLiftNetworkOptimizer opt = new GasLiftNetworkOptimizer();
    opt.addWell("A", curve);
    GasLiftNetworkOptimizer.AllocationResult result = opt.allocate(1.0e9);
    assertEquals(curve.optimalLiftRate(), result.getLiftRates().get("A"), curve.optimalLiftRate() * 0.05);
  }

  /**
   * A Vogel well-test match must recover the reservoir pressure and reproduce the measured rates with a small RMS
   * error.
   */
  @Test
  void testWellTestMatcherVogelRoundTrip() {
    // Synthesise three points from a known Vogel well: pr = 200, AOFP = 3000.
    double pr = 200.0;
    double aofp = 3000.0;
    WellDeliverabilityCurve truth = WellDeliverabilityCurve.fromVogel(aofp, pr);
    WellTestMatcher matcher = new WellTestMatcher();
    double[] testP = new double[] { 160.0, 120.0, 80.0 };
    for (int i = 0; i < testP.length; i++) {
      matcher.addTestPoint(truth.rateAt(testP[i]), testP[i]);
    }
    WellTestMatcher.MatchResult m = matcher.fitVogel();
    assertEquals("Vogel", m.getModel());
    assertNotNull(m.getCurve());
    assertEquals(pr, m.getReservoirPressure(), 20.0);
    assertTrue(m.getRmsError() < 0.05 * aofp, "RMS error must be small, was " + m.getRmsError());
  }

  /**
   * A linear productivity-index match must reproduce the straight-line IPR.
   */
  @Test
  void testWellTestMatcherProductivityIndex() {
    WellTestMatcher matcher = new WellTestMatcher();
    // Linear IPR: q = J (pr - pwf), pr = 300, J = 20 Sm3/day/bar.
    matcher.addTestPoint(20.0 * (300.0 - 250.0), 250.0);
    matcher.addTestPoint(20.0 * (300.0 - 200.0), 200.0);
    matcher.addTestPoint(20.0 * (300.0 - 150.0), 150.0);
    WellTestMatcher.MatchResult m = matcher.fitProductivityIndex();
    assertEquals("PI", m.getModel());
    assertTrue(m.getRmsError() < 50.0, "PI fit must be tight, was " + m.getRmsError());
  }

  /**
   * The integrated model must solve a balanced two-well field and expose per-well rates, revenue, energy, and
   * emissions.
   */
  @Test
  void testIntegratedModelSolve() {
    IntegratedProductionModel model = new IntegratedProductionModel("Field");
    model.setExportPressure(40.0);
    model.setHydrocarbonPrice(0.5);
    model.setEnergyIntensity(0.05);
    model.setEmissionIntensity(0.18);
    model.addWell("GAS-1", new MaterialBalanceGasDrive(250.0, 5.0e9, 0.90),
        WellDeliverabilityCurve.fromVogel(2.0e6, 250.0));
    model.addWell("OIL-1", new OilTankDrive(220.0, 8.0e6, 120.0, 90.0),
        WellDeliverabilityCurve.fromVogel(4000.0, 220.0));
    IntegratedSolveResult res = model.solve();
    assertTrue(res.isConverged(), "model must converge, method=" + res.getMethod());
    assertTrue(res.getFieldRate() > 0.0);
    assertEquals(2, res.getWellRates().size());
    assertEquals(res.getFieldRate() * 0.5, res.getRevenue(), 1.0e-3);
    assertEquals(res.getFieldRate() * 0.18, res.getEmissionsKgPerDay(), 1.0e-3);
    assertNotNull(model.toJson());
  }

  /**
   * Running a production profile must produce a declining field rate as the reservoirs deplete.
   */
  @Test
  void testIntegratedModelProfileDeclines() {
    IntegratedProductionModel model = new IntegratedProductionModel("Field");
    model.setExportPressure(40.0).setHydrocarbonPrice(0.5);
    model.addWell("GAS-1", new MaterialBalanceGasDrive(250.0, 2.0e9, 0.90),
        WellDeliverabilityCurve.fromVogel(2.0e6, 250.0));
    ProductionProfile profile = model.runProfile(8.0, 1.0);
    assertNotNull(profile);
    assertTrue(profile.getPoints().size() >= 8);
    double firstRate = profile.getPoints().get(0).getRateSm3PerDay();
    double lastRate = profile.getPoints().get(profile.getPoints().size() - 1).getRateSm3PerDay();
    assertTrue(lastRate < firstRate, "rate must decline over field life");
    assertTrue(profile.getCumulativeProduction() > 0.0);
    assertTrue(profile.getPeakRate() >= firstRate - RATE_TOL);
  }

  /**
   * The reservoir-to-market optimiser must respect a binding facility capacity and never throw.
   */
  @Test
  void testReservoirToMarketCapacityConstraint() {
    IntegratedProductionModel model = new IntegratedProductionModel("Field");
    model.setExportPressure(40.0).setHydrocarbonPrice(0.5);
    model.addWell("GAS-1", new MaterialBalanceGasDrive(250.0, 5.0e9, 0.90),
        WellDeliverabilityCurve.fromVogel(2.0e6, 250.0));
    model.addWell("OIL-1", new OilTankDrive(220.0, 8.0e6, 120.0, 90.0),
        WellDeliverabilityCurve.fromVogel(4000.0, 220.0));
    // Capacity well below the unconstrained field rate.
    double capacity = 500000.0;
    ReservoirToMarketOptimizer optimizer = new ReservoirToMarketOptimizer(model)
        .setObjective(ReservoirToMarketOptimizer.Objective.REVENUE).setFacilityCapacity(capacity);
    ReservoirToMarketOptimizer.OptimizationResult opt = optimizer.optimize();
    assertNotNull(opt);
    assertTrue(opt.getFieldRate() <= capacity * 1.01, "field rate must respect capacity");
    assertFalse(opt.getChokeSettings().isEmpty());
    for (Map.Entry<String, Double> e : opt.getChokeSettings().entrySet()) {
      assertTrue(e.getValue() >= 0.0 && e.getValue() <= 1.0, "choke in [0,1] for " + e.getKey());
    }
    assertNotNull(opt.toJson());
  }
}
