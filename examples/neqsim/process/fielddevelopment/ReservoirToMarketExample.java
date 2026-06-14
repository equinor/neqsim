package neqsim.process.fielddevelopment;

import java.util.Map;
import neqsim.process.fielddevelopment.integrated.GasLiftNetworkOptimizer;
import neqsim.process.fielddevelopment.integrated.GasLiftPerformanceCurve;
import neqsim.process.fielddevelopment.integrated.IntegratedProductionModel;
import neqsim.process.fielddevelopment.integrated.IntegratedSolveResult;
import neqsim.process.fielddevelopment.integrated.MaterialBalanceGasDrive;
import neqsim.process.fielddevelopment.integrated.OilTankDrive;
import neqsim.process.fielddevelopment.integrated.ProductionProfile;
import neqsim.process.fielddevelopment.integrated.ReservoirToMarketOptimizer;
import neqsim.process.fielddevelopment.integrated.WellDeliverabilityCurve;
import neqsim.process.fielddevelopment.integrated.WellTestMatcher;

/**
 * End-to-end "reservoir to market" example.
 *
 * <p>
 * This example demonstrates the integrated production-modelling stack in
 * {@code neqsim.process.fielddevelopment.integrated}, coupling the four classic production layers
 * in the spirit of Petex IPM (MBAL + PROSPER + GAP) and Schlumberger Pipesim:
 * </p>
 * <ol>
 * <li><b>Reservoir</b> - material-balance gas drive and an undersaturated oil tank that deplete
 * with cumulative production.</li>
 * <li><b>Wells</b> - fast deliverability surrogates, including one matched from well-test
 * data.</li>
 * <li><b>Flowlines / gathering network</b> - tie-ins balanced by a global Newton network
 * solve.</li>
 * <li><b>Market / facility</b> - an export boundary with a throughput-capacity constraint and a
 * simple revenue objective.</li>
 * </ol>
 *
 * <p>
 * It then runs a multi-year production profile, allocates a limited lift-gas budget across the
 * wells, and optimises the choke settings to maximise revenue subject to the facility capacity.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public final class ReservoirToMarketExample {

  /**
   * Private constructor to prevent instantiation of this example utility class.
   */
  private ReservoirToMarketExample() {}

  /**
   * Runs the reservoir-to-market demonstration.
   *
   * @param args command-line arguments (unused)
   */
  public static void main(String[] args) {
    // ---------------------------------------------------------------------
    // 1. Reservoir drives (MBAL-style)
    // ---------------------------------------------------------------------
    // Gas reservoir: 5e9 Sm3 GIIP, initial 250 bara, z = 0.90.
    MaterialBalanceGasDrive gasRes = new MaterialBalanceGasDrive(250.0, 5.0e9, 0.90);
    // Oil reservoir: 8e6 Sm3 STOIIP, initial 220 bara, depletes 120 bar over the life,
    // abandon at 90 bara.
    OilTankDrive oilRes = new OilTankDrive(220.0, 8.0e6, 120.0, 90.0);

    // ---------------------------------------------------------------------
    // 2. Well deliverability surrogates (PROSPER-style IPR/VLP)
    // ---------------------------------------------------------------------
    // Gas well from a Vogel-shaped deliverability (AOFP 2.0e6 Sm3/day at 250 bara shut-in).
    WellDeliverabilityCurve gasWellCurve =
        WellDeliverabilityCurve.fromVogel(2.0e6, gasRes.getReservoirPressure());

    // Oil well matched from a 3-point well test (rate, flowing pressure).
    WellTestMatcher matcher = new WellTestMatcher();
    matcher.addTestPoint(1500.0, 180.0).addTestPoint(2600.0, 150.0).addTestPoint(3400.0, 120.0);
    WellTestMatcher.MatchResult match = matcher.fitVogel();
    System.out.println(
        "Well-test match: model=" + match.getModel() + " pr=" + round(match.getReservoirPressure())
            + " bara, AOFP=" + round(match.getDeliverabilityParameter()) + " Sm3/day, RMS="
            + round(match.getRmsError()) + " Sm3/day");
    WellDeliverabilityCurve oilWellCurve = match.getCurve();

    // ---------------------------------------------------------------------
    // 3. Integrated model: wells -> flowlines -> export (GAP-style network)
    // ---------------------------------------------------------------------
    IntegratedProductionModel model = new IntegratedProductionModel("DemoField");
    model.setExportPressure(40.0);
    model.setHydrocarbonPrice(0.5); // currency per Sm3
    model.setEnergyIntensity(0.05); // kWh per Sm3
    model.setEmissionIntensity(0.18); // kg CO2 per Sm3
    model.addWell("GAS-1", gasRes, gasWellCurve);
    model.addWell("OIL-1", oilRes, oilWellCurve);

    IntegratedSolveResult res = model.solve();
    System.out.println("\nInitial balanced solution (converged=" + res.isConverged() + "):");
    for (Map.Entry<String, Double> e : res.getWellRates().entrySet()) {
      System.out.println("  " + e.getKey() + ": " + round(e.getValue()) + " Sm3/day");
    }
    System.out.println("  Field rate: " + round(res.getFieldRate()) + " Sm3/day");
    System.out.println("  Revenue:    " + round(res.getRevenue()) + " /day");
    System.out.println("  JSON: " + model.toJson());

    // ---------------------------------------------------------------------
    // 4. Multi-year production profile (reservoir depletion)
    // ---------------------------------------------------------------------
    ProductionProfile profile = model.runProfile(10.0, 1.0);
    System.out.println("\nProduction profile (peak " + round(profile.getPeakRate())
        + " Sm3/day, cumulative " + round(profile.getCumulativeProduction()) + " Sm3):");
    for (ProductionProfile.Point p : profile.getPoints()) {
      System.out
          .println("  t=" + round(p.getTimeYears()) + " yr  rate=" + round(p.getRateSm3PerDay())
              + " Sm3/day  pRes=" + round(p.getReservoirPressureBara()) + " bara");
    }

    // ---------------------------------------------------------------------
    // 5. Gas-lift allocation across a small fleet (equal-slope optimum)
    // ---------------------------------------------------------------------
    GasLiftNetworkOptimizer lift = new GasLiftNetworkOptimizer();
    lift.addWell("GL-A", new GasLiftPerformanceCurve(800.0, 30.0, 0.02, 200000.0));
    lift.addWell("GL-B", new GasLiftPerformanceCurve(500.0, 45.0, 0.04, 200000.0));
    lift.addWell("GL-C", new GasLiftPerformanceCurve(1200.0, 20.0, 0.015, 200000.0));
    GasLiftNetworkOptimizer.AllocationResult alloc = lift.allocate(150000.0);
    System.out.println("\nGas-lift allocation (total lift used " + round(alloc.getTotalLift())
        + " Sm3/day, total oil " + round(alloc.getTotalOil()) + " Sm3/day):");
    for (Map.Entry<String, Double> e : alloc.getLiftRates().entrySet()) {
      System.out.println("  " + e.getKey() + ": lift=" + round(e.getValue()) + " Sm3/day  oil="
          + round(alloc.getOilRates().get(e.getKey())) + " Sm3/day");
    }

    // ---------------------------------------------------------------------
    // 6. Reservoir-to-market optimisation (choke settings vs facility capacity)
    // ---------------------------------------------------------------------
    // Reset the reservoirs by rebuilding the model (drives were depleted by the profile run).
    IntegratedProductionModel optModel = new IntegratedProductionModel("DemoField");
    optModel.setExportPressure(40.0).setHydrocarbonPrice(0.5);
    optModel.addWell("GAS-1", new MaterialBalanceGasDrive(250.0, 5.0e9, 0.90),
        WellDeliverabilityCurve.fromVogel(2.0e6, 250.0));
    optModel.addWell("OIL-1", new OilTankDrive(220.0, 8.0e6, 120.0, 90.0), oilWellCurve);

    ReservoirToMarketOptimizer optimizer = new ReservoirToMarketOptimizer(optModel)
        .setObjective(ReservoirToMarketOptimizer.Objective.REVENUE).setFacilityCapacity(1.0e6);
    ReservoirToMarketOptimizer.OptimizationResult opt = optimizer.optimize();
    System.out.println("\nReservoir-to-market optimisation:");
    System.out.println("  " + opt.toJson());
  }

  /**
   * Rounds a value to three significant figures for display.
   *
   * @param v value to round
   * @return rounded value
   */
  private static double round(double v) {
    if (Double.isNaN(v) || Double.isInfinite(v)) {
      return v;
    }
    java.math.BigDecimal bd = new java.math.BigDecimal(v);
    bd = bd.round(new java.math.MathContext(4));
    return bd.doubleValue();
  }
}
