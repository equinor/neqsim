package neqsim.process.fielddevelopment.wellplanning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Compiles and runs the code snippets of docs/fielddevelopment/WELL_PLANNING_PHYSICS.md.
 *
 * @author NeqSim
 * @version 1.0
 */
public class WellPlanningDocExamplesTest {

  /**
   * Drift-flux tubing hydraulics with a Ramey temperature profile (documentation snippet 1).
   */
  @Test
  public void driftFluxExample() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 80.0, 200.0);
    fluid.addComponent("methane", 0.35);
    fluid.addComponent("ethane", 0.05);
    fluid.addComponent("propane", 0.04);
    fluid.addComponent("n-butane", 0.03);
    fluid.addComponent("n-hexane", 0.08);
    fluid.addComponent("n-decane", 0.45);
    fluid.setMixingRule("classic");

    WellboreThermalModel thermal = new WellboreThermalModel().setSurfaceTemperatureC(5.0).setGeothermalGradient(0.03)
        .setTotalDepth(2500.0).setMassFlowRate(8.0).setInletTemperatureC(80.0);
    DriftFluxWellboreHydraulics well = new DriftFluxWellboreHydraulics(fluid).setMassFlowRate(8.0)
        .setTubing(0.1, 4.5e-5).setStraightWell(2500.0, 0.0, 25).setThermalModel(thermal);
    DriftFluxWellboreHydraulics.Result r = well.calculateFromWellhead(30.0);
    double bhp = r.getBottomholePressureBara();
    boolean loading = r.isLiquidLoadingRisk();
    double erosionRatio = r.getMaxErosionalRatio();
    assertTrue(bhp > 120.0 && bhp < 200.0);
    assertTrue(!loading || r.getMinTurnerRatio() < 1.0);
    assertTrue(erosionRatio < 1.0);
  }

  /**
   * Gas-condensate inflow with banking and non-Darcy flow (documentation snippet 2).
   */
  @Test
  public void gasCondensateExample() {
    SystemInterface gc = new SystemSrkEos(273.15 + 110.0, 300.0);
    gc.addComponent("methane", 0.75);
    gc.addComponent("ethane", 0.08);
    gc.addComponent("propane", 0.05);
    gc.addComponent("n-butane", 0.03);
    gc.addComponent("n-hexane", 0.04);
    gc.addComponent("n-octane", 0.03);
    gc.addComponent("nC10", 0.02);
    gc.setMixingRule("classic");

    GasCondensateInflowModel inflow = new GasCondensateInflowModel(gc, 110.0).setReservoirPressure(450.0)
        .setFormation(20.0, 40.0).setGeometry(500.0, 0.108, 0.0).setRelativePermeability(0.2, 0.1, 1.0, 1.0, 3.0, 3.0);
    inflow.useGeertsmaBeta(0.15);
    GasCondensateInflowModel.Result res = inflow.calculate(100.0);
    double q = res.getGasRateSm3PerDay();
    double impairment = res.getBankingImpairment();
    double dewPoint = res.getDewPointBara();
    assertTrue(q > 1.0e6);
    assertTrue(impairment < 1.0 && dewPoint > 150.0);
  }

  /**
   * Annular pressure build-up and tubing movement (documentation snippet 3).
   */
  @Test
  public void apbAndTubingMovementExample() {
    AnnularPressureBuildup.Result apb = new AnnularPressureBuildup().setAnnulusLength(1500.0)
        .setGeometry(0.1397, 0.1215, 0.2204, 0.2445).setFluidProperties(3.0e-4, 4.5e-5).setTemperatureChange(40.0)
        .setInitialPressure(10.0).calculate();
    assertTrue(apb.getDeltaPressureBar() > 50.0 && apb.getDeltaPressureBar() < apb.getRigidLimitBar());
    assertNotNull(apb.toAnnulusReading("A", 200.0));

    TubingMovementCalculator.Result mv = new TubingMovementCalculator().setLength(3000.0)
        .setGeometry(0.1143, 0.0985, 0.12, 0.1594).setLoadChange(150.0, 20.0, 120.0, 20.0, 40.0).calculate();
    assertEquals(mv.getPistonDeltaLM() + mv.getBalloonDeltaLM() + mv.getTemperatureDeltaLM(), mv.getFreeDeltaLM(),
        1.0e-12);
  }

  /**
   * Hydrate-inhibitor requirement along the well (documentation snippet 4).
   */
  @Test
  public void chemicalTreatmentExample() {
    SystemInterface fluid = new SystemSrkCPAstatoil(293.15, 100.0);
    fluid.addComponent("methane", 0.85);
    fluid.addComponent("ethane", 0.07);
    fluid.addComponent("propane", 0.03);
    fluid.addComponent("n-butane", 0.01);
    fluid.addComponent("water", 0.05);
    fluid.setMixingRule(10);

    WellChemicalTreatment treatment = new WellChemicalTreatment(fluid)
        .setInhibitor(WellChemicalTreatment.HydrateInhibitor.MEG, 0.9).setSubcoolingMargin(3.0).setWaterRate(2000.0)
        .addStation("tree shut-in", 120.0, 8.0);
    WellChemicalTreatment.Result r = treatment.evaluate();
    assertEquals("tree shut-in", r.getGoverningStation());
    assertTrue(r.getLeanSolutionKgPerHour() > 0.0);
  }

  /**
   * Nodal analysis with a linear inflow and a tabulated outflow (documentation snippet 5).
   */
  @Test
  public void nodalExample() {
    NodalAnalysis.InflowCurve ipr = NodalAnalysis.linearInflow(300.0, 2.0);
    NodalAnalysis.OutflowCurve vlp = NodalAnalysis.tabulatedOutflow(new double[] {1.0, 100.0, 400.0},
        new double[] {85.0, 140.0, 300.0});
    NodalAnalysis.Result result = new NodalAnalysis(ipr, vlp).setRateRange(1.0, 400.0).solve();
    assertNotNull(result.getOperatingPoint());
    assertTrue(result.getOperatingPoint().getRate() > 100.0);
  }
}
