package neqsim.pvtsimulation.flowassurance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for the coupled {@link MultiMineralScaleEquilibrium} solid-precipitation equilibrium solver.
 *
 * <p>
 * Covers coupled sulphate competition (barite vs celestite for a shared sulphate pool), sulphate mass conservation, the
 * Gibbs complementarity condition (SI = 0 for precipitated minerals), the extended B-dot high-salinity activity option,
 * and the second-order pressure correction.
 * </p>
 */
public class MultiMineralScaleEquilibriumTest {

  /**
   * Builds a sulphate-limited scaling brine where barite and celestite must compete for a shared, sub-stoichiometric
   * sulphate pool.
   *
   * @return a configured predictor
   */
  private ScalePredictionCalculator sulphateLimitedBrine() {
    ScalePredictionCalculator calc = new ScalePredictionCalculator();
    calc.setTemperatureCelsius(70.0);
    calc.setPressureBara(100.0);
    calc.setCalciumConcentration(1000.0);
    calc.setBariumConcentration(1000.0);
    calc.setStrontiumConcentration(3000.0);
    calc.setIronConcentration(0.0);
    calc.setMagnesiumConcentration(0.0);
    calc.setSodiumConcentration(0.0);
    calc.setBicarbonateConcentration(200.0);
    calc.setSulphateConcentration(1500.0); // sub-stoichiometric relative to Ba + Sr
    calc.setTotalDissolvedSolids(60000.0);
    calc.setCO2PartialPressure(1.0);
    calc.enableAutoPH();
    return calc;
  }

  @Test
  @DisplayName("Coupled solver: precipitated minerals reach SI = 0 (Gibbs complementarity)")
  void testEquilibriumSaturationIndexZero() {
    MultiMineralScaleEquilibrium eq = new MultiMineralScaleEquilibrium(sulphateLimitedBrine());
    eq.solve();

    for (MultiMineralScaleEquilibrium.MineralResult r : eq.getResults().values()) {
      if (r.getPrecipitatedMolPerL() > 1.0e-12) {
        assertEquals(0.0, r.getFinalSI(), 1.0e-3,
            r.getName() + " has solid present so its final SI must be ~0, got " + r.getFinalSI());
      } else {
        assertTrue(r.getFinalSI() <= 1.0e-3,
            r.getName() + " did not precipitate so SI must be <= 0, got " + r.getFinalSI());
      }
    }
  }

  @Test
  @DisplayName("Coupled solver: sulphate mass is conserved across all sulphate minerals")
  void testSulphateMassConservation() {
    ScalePredictionCalculator calc = sulphateLimitedBrine();
    MultiMineralScaleEquilibrium eq = new MultiMineralScaleEquilibrium(calc);
    eq.solve();

    double initialFreeSO4 = calc.getFreeSulphateMolPerL();
    double residualSO4 = eq.getResidualFreeIonMolPerL("SO4--");
    double precipSulphate = eq.getPrecipitatedMolPerL("BaSO4") + eq.getPrecipitatedMolPerL("SrSO4")
        + eq.getPrecipitatedMolPerL("CaSO4");

    assertEquals(initialFreeSO4 - residualSO4, precipSulphate, 1.0e-9,
        "Precipitated sulphate must equal the drop in free sulphate");
  }

  @Test
  @DisplayName("Coupled solver: barite competition lowers celestite precipitation vs decoupled estimate")
  void testBariteOutcompetesCelestite() {
    ScalePredictionCalculator calc = sulphateLimitedBrine();
    MultiMineralScaleEquilibrium eq = new MultiMineralScaleEquilibrium(calc);
    eq.solve();

    double coupledSrMol = eq.getPrecipitatedMolPerL("SrSO4");

    // Decoupled estimate (as ScaleMassCalculator would do): SrSO4 sees the full sulphate pool and
    // ignores that barite consumes sulphate first.
    double cSr = calc.getTotalStrontiumMolPerL();
    double cSO4 = calc.getFreeSulphateMolPerL();
    double siSr = calc.getSrSO4SaturationIndex();
    double decoupledSrMol = 0.0;
    if (siSr > 0) {
      double fraction = 1.0 - Math.pow(10.0, -siSr);
      decoupledSrMol = Math.min(cSr, cSO4) * Math.min(fraction, 0.99);
    }

    assertTrue(coupledSrMol < decoupledSrMol,
        "Coupled celestite precipitation (" + coupledSrMol + " mol/L) must be less than the decoupled " + "estimate ("
            + decoupledSrMol + " mol/L) because barite consumes sulphate first");

    // The decoupled model predicts celestite scaling; the coupled model shows barite removes the
    // sulphate first, leaving celestite undersaturated so it does not scale.
    assertTrue(siSr > 0, "Decoupled celestite SI must be positive so the two models genuinely disagree");
    assertTrue(decoupledSrMol > 0, "Decoupled model predicts celestite scaling");

    // Barite carries solid so it sits at SI = 0; celestite has no solid so it must be undersaturated.
    assertEquals(0.0, eq.getResults().get("BaSO4").getFinalSI(), 1.0e-3);
    assertTrue(eq.getResults().get("SrSO4").getFinalSI() <= 1.0e-3,
        "Suppressed celestite must be undersaturated at equilibrium");
  }

  @Test
  @DisplayName("Coupled solver: sulphate-rich brine co-precipitates barite and celestite, both at SI = 0")
  void testCoPrecipitationBariteCelestite() {
    ScalePredictionCalculator calc = new ScalePredictionCalculator();
    calc.setTemperatureCelsius(70.0);
    calc.setPressureBara(100.0);
    calc.setCalciumConcentration(500.0);
    calc.setBariumConcentration(100.0);
    calc.setStrontiumConcentration(2500.0);
    calc.setBicarbonateConcentration(100.0);
    calc.setSulphateConcentration(4000.0); // sulphate in excess of Ba + Sr
    calc.setTotalDissolvedSolids(60000.0);
    calc.setCO2PartialPressure(1.0);
    calc.enableAutoPH();

    MultiMineralScaleEquilibrium eq = new MultiMineralScaleEquilibrium(calc);
    eq.solve();

    assertTrue(eq.getPrecipitatedMolPerL("BaSO4") > 1.0e-9, "Barite should precipitate");
    assertTrue(eq.getPrecipitatedMolPerL("SrSO4") > 1.0e-9,
        "Celestite should co-precipitate when sulphate is in excess");
    assertEquals(0.0, eq.getResults().get("BaSO4").getFinalSI(), 1.0e-3, "Barite with solid must be at SI = 0");
    assertEquals(0.0, eq.getResults().get("SrSO4").getFinalSI(), 1.0e-3, "Celestite with solid must be at SI = 0");
  }

  @Test
  @DisplayName("Coupled solver: no mineral precipitates more than its limiting ion allows")
  void testPrecipitationBoundedByAvailableIons() {
    ScalePredictionCalculator calc = sulphateLimitedBrine();
    MultiMineralScaleEquilibrium eq = new MultiMineralScaleEquilibrium(calc);
    eq.solve();

    assertTrue(eq.getPrecipitatedMolPerL("BaSO4") <= calc.getTotalBariumMolPerL() + 1.0e-12,
        "Barite precipitation cannot exceed available barium");
    assertTrue(eq.getPrecipitatedMolPerL("SrSO4") <= calc.getTotalStrontiumMolPerL() + 1.0e-12,
        "Celestite precipitation cannot exceed available strontium");
    assertTrue(eq.getResidualFreeIonMolPerL("SO4--") >= 0.0, "Residual sulphate must be non-negative");
    assertTrue(eq.getResidualFreeIonMolPerL("Ba++") >= 0.0, "Residual barium must be non-negative");
  }

  @Test
  @DisplayName("Benchmark: single-mineral barite equilibrium matches the decoupled estimate")
  void testSingleMineralBariteBenchmark() {
    // Only barium and sulphate present -> no competition, coupled == decoupled.
    ScalePredictionCalculator calc = new ScalePredictionCalculator();
    calc.setTemperatureCelsius(60.0);
    calc.setPressureBara(1.013);
    calc.setCalciumConcentration(0.0);
    calc.setBariumConcentration(200.0);
    calc.setStrontiumConcentration(0.0);
    calc.setIronConcentration(0.0);
    calc.setBicarbonateConcentration(0.0);
    calc.setSulphateConcentration(2000.0);
    calc.setTotalDissolvedSolids(30000.0);
    calc.setCO2PartialPressure(0.0);

    MultiMineralScaleEquilibrium eq = new MultiMineralScaleEquilibrium(calc);
    eq.solve();

    double coupledBaMol = eq.getPrecipitatedMolPerL("BaSO4");

    double cBa = calc.getTotalBariumMolPerL();
    double cSO4 = calc.getFreeSulphateMolPerL();
    double siBa = calc.getBaSO4SaturationIndex();
    double fraction = 1.0 - Math.pow(10.0, -siBa);
    double decoupledBaMol = Math.min(cBa, cSO4) * Math.min(fraction, 0.99);

    assertTrue(siBa > 0, "Barite should be supersaturated in this benchmark");
    // Within 10% of the decoupled estimate (they differ only by the exact vs approximate root).
    assertEquals(decoupledBaMol, coupledBaMol, 0.10 * decoupledBaMol + 1.0e-6,
        "Single-mineral coupled result should track the decoupled estimate");
    // Equilibrium: barite SI back to ~0.
    assertEquals(0.0, eq.getResults().get("BaSO4").getFinalSI(), 1.0e-3);
  }

  @Test
  @DisplayName("Activity model: Davies and B-dot give different high-salinity activity and precipitation")
  void testActivityModelSelection() {
    ScalePredictionCalculator calcDavies = highSalinityBrine();
    ScalePredictionCalculator calcBdot = highSalinityBrine();

    MultiMineralScaleEquilibrium daviesEq = new MultiMineralScaleEquilibrium(calcDavies)
        .setActivityModel(MultiMineralScaleEquilibrium.ActivityModel.DAVIES);
    MultiMineralScaleEquilibrium bdotEq = new MultiMineralScaleEquilibrium(calcBdot)
        .setActivityModel(MultiMineralScaleEquilibrium.ActivityModel.BDOT);
    daviesEq.solve();
    bdotEq.solve();

    double gammaDavies = daviesEq.getDivalentActivityCoefficientUsed();
    double gammaBdot = bdotEq.getDivalentActivityCoefficientUsed();
    double daviesMass = daviesEq.getTotalScaleMassMgPerL();
    double bdotMass = bdotEq.getTotalScaleMassMgPerL();

    assertTrue(daviesMass >= 0.0 && !Double.isNaN(daviesMass), "Davies total mass must be finite");
    assertTrue(bdotMass >= 0.0 && !Double.isNaN(bdotMass), "B-dot total mass must be finite");
    // Davies is known to diverge above I ~ 0.5 mol/kg; B-dot stays bounded. The two models must give
    // a materially different divalent activity coefficient at this ionic strength.
    assertTrue(Math.abs(gammaDavies - gammaBdot) > 1.0e-3, "At high ionic strength Davies (" + gammaDavies
        + ") and B-dot (" + gammaBdot + ") divalent activity coefficients must differ");
    assertTrue(gammaBdot > 0.0 && gammaBdot < 5.0,
        "B-dot divalent activity coefficient should stay physically bounded, got " + gammaBdot);
  }

  @Test
  @DisplayName("Second-order pressure correction changes barite Ksp at high pressure")
  void testSecondOrderPressureCorrection() {
    ScalePredictionCalculator calc = new ScalePredictionCalculator();
    calc.setTemperatureCelsius(80.0);
    calc.setPressureBara(400.0);
    calc.setBariumConcentration(200.0);
    calc.setSulphateConcentration(2000.0);
    calc.setTotalDissolvedSolids(50000.0);

    double kspFirstOrder = calc.getKspBarite();
    calc.setSecondOrderPressureCorrection(true);
    double kspSecondOrder = calc.getKspBarite();

    assertFalse(Double.isNaN(kspSecondOrder), "Second-order Ksp must be finite");
    assertTrue(Math.abs(kspSecondOrder - kspFirstOrder) > 0.0,
        "Second-order compressibility term must change the pressure-corrected Ksp at 400 bara");
  }

  @Test
  @DisplayName("JSON report is produced and contains total scale mass")
  void testJsonReport() {
    MultiMineralScaleEquilibrium eq = new MultiMineralScaleEquilibrium(sulphateLimitedBrine());
    String json = eq.toJson();
    assertTrue(json.contains("totalScaleMass_mgL"), "JSON must report total scale mass");
    assertTrue(json.contains("residualFreeIons_molL"), "JSON must report residual free ions");
  }

  /**
   * Builds a high-salinity scaling brine (ionic strength well above the Davies validity limit).
   *
   * @return a configured predictor
   */
  private ScalePredictionCalculator highSalinityBrine() {
    ScalePredictionCalculator calc = new ScalePredictionCalculator();
    calc.setTemperatureCelsius(90.0);
    calc.setPressureBara(200.0);
    calc.setCalciumConcentration(15000.0);
    calc.setBariumConcentration(400.0);
    calc.setStrontiumConcentration(800.0);
    calc.setBicarbonateConcentration(200.0);
    calc.setSulphateConcentration(600.0);
    calc.setTotalDissolvedSolids(200000.0);
    calc.setCO2PartialPressure(2.0);
    calc.enableAutoPH();
    return calc;
  }
}
