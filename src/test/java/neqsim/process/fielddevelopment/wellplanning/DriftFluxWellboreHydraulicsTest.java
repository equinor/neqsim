package neqsim.process.fielddevelopment.wellplanning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import neqsim.process.equipment.pipeline.PipeBeggsAndBrills;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.fielddevelopment.wellplanning.DriftFluxWellboreHydraulics.FlowRegime;
import neqsim.process.fielddevelopment.wellplanning.DriftFluxWellboreHydraulics.Result;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Tests of {@link DriftFluxWellboreHydraulics} against analytic limits and Beggs and Brill.
 *
 * <p>
 * Evidence is analytic-limit and cross-correlation only; no field data is used.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public class DriftFluxWellboreHydraulicsTest {

  /**
   * Builds a synthetic black-oil-like fluid.
   *
   * @return fluid
   */
  static SystemInterface oilFluid() {
    SystemInterface f = new SystemSrkEos(273.15 + 80, 200.0);
    f.addComponent("methane", 0.35);
    f.addComponent("ethane", 0.05);
    f.addComponent("propane", 0.04);
    f.addComponent("n-butane", 0.03);
    f.addComponent("n-hexane", 0.08);
    f.addComponent("n-decane", 0.45);
    f.setMixingRule("classic");
    return f;
  }

  /**
   * Builds a synthetic lean gas-condensate fluid.
   *
   * @return fluid
   */
  static SystemInterface condensateFluid() {
    SystemInterface f = new SystemSrkEos(273.15 + 80, 300.0);
    f.addComponent("methane", 0.80);
    f.addComponent("ethane", 0.07);
    f.addComponent("propane", 0.04);
    f.addComponent("n-butane", 0.02);
    f.addComponent("n-hexane", 0.03);
    f.addComponent("n-octane", 0.03);
    f.addComponent("nC10", 0.01);
    f.setMixingRule("classic");
    return f;
  }

  /**
   * Flashes a clone and returns density and viscosity of the first phase.
   *
   * @param base fluid
   * @param pBar pressure in bara
   * @param tK temperature in K
   * @return array of density (kg/m3) and viscosity (Pa s)
   */
  private static double[] props(SystemInterface base, double pBar, double tK) {
    SystemInterface f = base.clone();
    f.setPressure(pBar);
    f.setTemperature(tK);
    new ThermodynamicOperations(f).TPflash();
    f.initProperties();
    return new double[] {f.getPhase(0).getDensity("kg/m3"), f.getPhase(0).getViscosity("kg/msec")};
  }

  /**
   * Single-phase liquid with negligible rate reproduces the static column rho g L.
   */
  @Test
  public void staticLiquidColumnMatchesRhoGL() {
    SystemInterface dec = new SystemSrkEos(273.15 + 40, 50.0);
    dec.addComponent("n-decane", 1.0);
    dec.setMixingRule("classic");
    DriftFluxWellboreHydraulics m = new DriftFluxWellboreHydraulics(dec).setMassFlowRate(0.01).setTubing(0.1, 4.5e-5)
        .setStraightWell(2000.0, 0.0, 10).setLinearTemperature(40.0, 40.0);
    Result r = m.calculateFromWellhead(50.0);
    double rho = props(dec, 50.0 + 0.5 * r.getTotalPressureDropBar(), 313.15)[0];
    double expected = rho * 9.80665 * 2000.0 / 1.0e5;
    assertEquals(expected, r.getTotalPressureDropBar(), 0.005 * expected);
    assertEquals(FlowRegime.SINGLE_PHASE_LIQUID, r.getFlowRegime()[0]);
    assertEquals(1.0, r.getLiquidHoldup()[0], 1.0e-12);
  }

  /**
   * A horizontal single-phase liquid line reproduces Darcy-Weisbach with the Haaland friction factor and raises the
   * near-horizontal warning.
   */
  @Test
  public void horizontalLiquidMatchesDarcyWeisbach() {
    SystemInterface dec = new SystemSrkEos(273.15 + 40, 30.0);
    dec.addComponent("n-decane", 1.0);
    dec.setMixingRule("classic");
    double d = 0.1;
    double mdot = 6.0;
    DriftFluxWellboreHydraulics m = new DriftFluxWellboreHydraulics(dec).setMassFlowRate(mdot).setTubing(d, 4.5e-5)
        .setStraightWell(1000.0, 90.0, 4).setLinearTemperature(40.0, 40.0);
    Result r = m.calculateFromBottomhole(30.0);
    double[] p = props(dec, 30.0, 313.15);
    double v = mdot / p[0] / (Math.PI * d * d / 4.0);
    double re = p[0] * v * d / p[1];
    double f = Math.pow(-1.8 * Math.log10(Math.pow(4.5e-5 / d / 3.7, 1.11) + 6.9 / re), -2.0);
    double expected = f * p[0] * v * v / (2.0 * d) * 1000.0 / 1.0e5;
    assertEquals(expected, r.getTotalPressureDropBar(), 0.02 * expected);
    assertEquals(0.0, r.getHydrostaticDropBar(), 1.0e-9);
    assertFalse(r.getWarnings().isEmpty());
  }

  /**
   * Dry gas matches an independent fine-step integration of gravity plus friction.
   */
  @Test
  public void dryGasMatchesIndependentIntegration() {
    SystemInterface gas = new SystemSrkEos(273.15 + 60, 100.0);
    gas.addComponent("methane", 0.9);
    gas.addComponent("ethane", 0.1);
    gas.setMixingRule("classic");
    double d = 0.1;
    double mdot = 6.0;
    double len = 2500.0;
    double tw = 40.0;
    double tb = 90.0;
    DriftFluxWellboreHydraulics m = new DriftFluxWellboreHydraulics(gas).setMassFlowRate(mdot).setTubing(d, 4.5e-5)
        .setStraightWell(len, 0.0, 25).setLinearTemperature(tw, tb);
    Result r = m.calculateFromWellhead(60.0);
    int n = 250;
    double dz = len / n;
    double p = 60.0;
    for (int i = 0; i < n; i++) {
      double tK = 273.15 + tw + (tb - tw) * (i + 0.5) / n;
      double pm = p + 0.05;
      for (int it = 0; it < 3; it++) {
        double[] pr = props(gas, pm, tK);
        double v = mdot / pr[0] / (Math.PI * d * d / 4.0);
        double re = pr[0] * v * d / pr[1];
        double f = Math.pow(-1.8 * Math.log10(Math.pow(4.5e-5 / d / 3.7, 1.11) + 6.9 / re), -2.0);
        double grad = pr[0] * 9.80665 + f * pr[0] * v * v / (2.0 * d);
        double pEnd = p + grad * dz / 1.0e5;
        pm = 0.5 * (p + pEnd);
        if (it == 2) {
          p = pEnd;
        }
      }
    }
    assertEquals(p, r.getBottomholePressureBara(), 0.01 * p);
    assertEquals(FlowRegime.SINGLE_PHASE_GAS, r.getFlowRegime()[0]);
  }

  /**
   * Oil well: the drift-flux and Beggs and Brill pressure traverses agree within a few percent of the gravity-dominated
   * drop.
   */
  @Test
  public void oilCaseAgreesWithBeggsAndBrill() {
    SystemInterface oil = oilFluid();
    DriftFluxWellboreHydraulics m = new DriftFluxWellboreHydraulics(oil).setMassFlowRate(8.0).setTubing(0.1, 4.5e-5)
        .setStraightWell(2500.0, 0.0, 40).setLinearTemperature(40.0, 90.0);
    Result r = m.calculateFromWellhead(30.0);
    SystemInterface g = oil.clone();
    g.setTemperature(273.15 + 65.0);
    g.setPressure(r.getBottomholePressureBara());
    g.setTotalFlowRate(8.0, "kg/sec");
    Stream s = new Stream("in", g);
    s.run();
    PipeBeggsAndBrills bb = new PipeBeggsAndBrills("bb", s);
    bb.setLength(2500.0);
    bb.setElevation(2500.0);
    bb.setDiameter(0.1);
    bb.setPipeWallRoughness(4.5e-5);
    bb.setNumberOfIncrements(40);
    bb.run();
    double bbWhp = bb.getOutletStream().getPressure();
    assertEquals(30.0, bbWhp, 0.04 * r.getTotalPressureDropBar());
    assertTrue(r.getHydrostaticDropBar() > 10.0 * r.getFrictionDropBar());
    assertTrue(r.getRegimeCounts().containsKey(FlowRegime.SINGLE_PHASE_LIQUID));
    assertTrue(r.getRegimeCounts().containsKey(FlowRegime.BUBBLE) || r.getRegimeCounts().containsKey(FlowRegime.SLUG));
  }

  /**
   * Gas-condensate well: annular regime, low holdup, friction-dominated; Beggs and Brill gives a larger drop (known
   * over-prediction of holdup in wet gas).
   */
  @Test
  public void gasCondensateIsAnnularAndBelowBeggsAndBrill() {
    SystemInterface gc = condensateFluid();
    DriftFluxWellboreHydraulics m = new DriftFluxWellboreHydraulics(gc).setMassFlowRate(12.0).setTubing(0.1, 4.5e-5)
        .setStraightWell(3000.0, 0.0, 30).setLinearTemperature(50.0, 110.0);
    Result r = m.calculateFromWellhead(80.0);
    assertEquals(FlowRegime.ANNULAR, r.getFlowRegime()[0]);
    for (double hl : r.getLiquidHoldup()) {
      assertTrue(hl < 0.2 && hl > 0.0);
    }
    assertTrue(r.getFrictionDropBar() > 0.5 * r.getHydrostaticDropBar());
    SystemInterface g = gc.clone();
    g.setTemperature(273.15 + 80.0);
    g.setPressure(r.getBottomholePressureBara());
    g.setTotalFlowRate(12.0, "kg/sec");
    Stream s = new Stream("in", g);
    s.run();
    PipeBeggsAndBrills bb = new PipeBeggsAndBrills("bb", s);
    bb.setLength(3000.0);
    bb.setElevation(3000.0);
    bb.setDiameter(0.1);
    bb.setPipeWallRoughness(4.5e-5);
    bb.setNumberOfIncrements(30);
    bb.run();
    double bbDrop = r.getBottomholePressureBara() - bb.getOutletStream().getPressure();
    assertTrue(bbDrop > r.getTotalPressureDropBar());
    assertTrue(r.getTotalPressureDropBar() > 0.5 * bbDrop);
  }

  /**
   * Liquid loading flag: a low gas rate falls below the Turner velocity and leaves the annular regime; the Turner ratio
   * scales with rate.
   */
  @Test
  public void liquidLoadingFlaggedAtLowRate() {
    SystemInterface gc = condensateFluid();
    DriftFluxWellboreHydraulics m = new DriftFluxWellboreHydraulics(gc).setTubing(0.1, 4.5e-5)
        .setStraightWell(3000.0, 0.0, 12).setLinearTemperature(50.0, 110.0);
    Result high = m.setMassFlowRate(6.0).calculateFromWellhead(80.0);
    Result low = m.setMassFlowRate(0.5).calculateFromWellhead(80.0);
    assertFalse(high.isLiquidLoadingRisk());
    assertTrue(low.isLiquidLoadingRisk());
    assertTrue(low.getMinTurnerRatio() < 1.0);
    assertTrue(low.getFlowRegime()[0] != FlowRegime.ANNULAR);
    assertTrue(low.getLiquidHoldup()[0] > high.getLiquidHoldup()[0]);
    assertTrue(low.getWarnings().toString().contains("liquid loading"));
    double ratio = high.getTurnerRatio()[0] / low.getTurnerRatio()[0];
    assertTrue(ratio > 8.0 && ratio < 16.0);
  }

  /**
   * Erosional velocity ratio is proportional to velocity: doubling the rate roughly doubles the ratio.
   */
  @Test
  public void erosionalRatioScalesWithRate() {
    SystemInterface gc = condensateFluid();
    DriftFluxWellboreHydraulics m = new DriftFluxWellboreHydraulics(gc).setTubing(0.1, 4.5e-5)
        .setStraightWell(1000.0, 0.0, 4).setLinearTemperature(50.0, 60.0);
    double r1 = m.setMassFlowRate(6.0).calculateFromWellhead(100.0).getMaxErosionalRatio();
    double r2 = m.setMassFlowRate(12.0).calculateFromWellhead(100.0).getMaxErosionalRatio();
    assertEquals(2.0, r2 / r1, 0.1);
    assertTrue(m.setMassFlowRate(30.0).setErosionalCFactor(100.0).calculateFromWellhead(100.0).getWarnings().toString()
        .contains("erosional"));
  }

  /**
   * Marching up from the computed bottom-hole pressure returns the wellhead pressure.
   */
  @Test
  public void bottomholeAndWellheadMarchingAreConsistent() {
    SystemInterface oil = oilFluid();
    DriftFluxWellboreHydraulics m = new DriftFluxWellboreHydraulics(oil).setMassFlowRate(8.0).setTubing(0.1, 4.5e-5)
        .setStraightWell(2500.0, 0.0, 25).setLinearTemperature(40.0, 90.0);
    Result down = m.calculateFromWellhead(30.0);
    Result up = m.calculateFromBottomhole(down.getBottomholePressureBara());
    assertEquals(30.0, up.getWellheadPressureBara(), 0.3);
  }

  /**
   * A deviated well has hydrostatic head scaled by cos(inclination) for the same measured depth.
   */
  @Test
  public void deviatedWellScalesHydrostaticByCosine() {
    SystemInterface dec = new SystemSrkEos(273.15 + 40, 50.0);
    dec.addComponent("n-decane", 1.0);
    dec.setMixingRule("classic");
    DriftFluxWellboreHydraulics m = new DriftFluxWellboreHydraulics(dec).setMassFlowRate(0.01).setTubing(0.1, 4.5e-5)
        .setLinearTemperature(40.0, 40.0);
    double vertical = m.setStraightWell(2000.0, 0.0, 8).calculateFromWellhead(50.0).getHydrostaticDropBar();
    double deviated = m.setStraightWell(2000.0, 60.0, 8).calculateFromWellhead(50.0).getHydrostaticDropBar();
    assertEquals(0.5, deviated / vertical, 0.005);
  }

  /**
   * Ramey thermal model can drive the temperature profile.
   */
  @Test
  public void thermalModelCouplingChangesTraverse() {
    SystemInterface oil = oilFluid();
    WellboreThermalModel tm = new WellboreThermalModel().setSurfaceTemperatureC(5.0).setGeothermalGradient(0.03)
        .setTotalDepth(2500.0).setMassFlowRate(8.0).setInletTemperatureC(80.0);
    DriftFluxWellboreHydraulics m = new DriftFluxWellboreHydraulics(oil).setMassFlowRate(8.0).setTubing(0.1, 4.5e-5)
        .setStraightWell(2500.0, 0.0, 10).setThermalModel(tm);
    Result r = m.calculateFromWellhead(30.0);
    double[] t = r.getSegmentTemperatureK();
    assertTrue(t[t.length - 1] > t[0]);
    assertEquals(tm.temperatureAt(2500.0 * 0.95), t[t.length - 1], 1.0e-6);
  }
}
