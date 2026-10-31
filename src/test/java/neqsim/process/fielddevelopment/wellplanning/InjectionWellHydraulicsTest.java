package neqsim.process.fielddevelopment.wellplanning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import neqsim.process.fielddevelopment.wellplanning.InjectionWellHydraulics.OperatingPoint;
import neqsim.process.fielddevelopment.wellplanning.InjectionWellHydraulics.Result;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Analytic-limit tests of {@link InjectionWellHydraulics}.
 *
 * @author NeqSim
 * @version 1.0
 */
public class InjectionWellHydraulicsTest {

  /**
   * Builds injection water with CPA.
   *
   * @return fluid
   */
  private static SystemInterface water() {
    SystemInterface f = new SystemSrkCPAstatoil(293.15, 100.0);
    f.addComponent("water", 1.0);
    f.setMixingRule(10);
    return f;
  }

  /**
   * Builds dry injection gas.
   *
   * @return fluid
   */
  private static SystemInterface gas() {
    SystemInterface f = new SystemSrkEos(293.15, 100.0);
    f.addComponent("methane", 0.95);
    f.addComponent("ethane", 0.05);
    f.setMixingRule("classic");
    return f;
  }

  /**
   * Flashes a clone and returns the density of the first phase.
   *
   * @param base fluid
   * @param pBar pressure in bara
   * @param tK temperature in K
   * @return density in kg/m3
   */
  private static double density(SystemInterface base, double pBar, double tK) {
    SystemInterface f = base.clone();
    f.setPressure(pBar);
    f.setTemperature(tK);
    new ThermodynamicOperations(f).TPflash();
    f.initProperties();
    return f.getPhase(0).getDensity("kg/m3");
  }

  /**
   * Static water column: bottom-hole pressure exceeds wellhead pressure by rho g H.
   */
  @Test
  public void staticWaterColumn() {
    InjectionWellHydraulics m = new InjectionWellHydraulics(water()).setMassFlowRate(0.01).setTubing(0.1, 4.5e-5)
        .setStraightWell(2000.0, 0.0, 8).setLinearTemperature(20.0, 20.0);
    Result r = m.calculateFromWellhead(100.0);
    double rho = density(water(), 100.0 + 0.5 * (r.getBottomholePressureBara() - 100.0), 293.15);
    double expected = rho * 9.80665 * 2000.0 / 1.0e5;
    assertEquals(expected, r.getBottomholePressureBara() - 100.0, 0.005 * expected);
    assertTrue(r.getFrictionLossBar() < 0.01);
    assertFalse(r.isTwoPhase());
    Result up = m.calculateFromBottomhole(r.getBottomholePressureBara());
    assertEquals(100.0, up.getWellheadPressureBara(), 0.2);
  }

  /**
   * A horizontal water line loses pressure by Darcy-Weisbach friction in the flow direction.
   */
  @Test
  public void horizontalFrictionMatchesDarcyWeisbach() {
    double d = 0.1;
    double mdot = 20.0;
    InjectionWellHydraulics m = new InjectionWellHydraulics(water()).setMassFlowRate(mdot).setTubing(d, 4.5e-5)
        .setStraightWell(1000.0, 90.0, 4).setLinearTemperature(20.0, 20.0);
    Result r = m.calculateFromWellhead(150.0);
    SystemInterface f = water();
    f.setPressure(150.0);
    f.setTemperature(293.15);
    new ThermodynamicOperations(f).TPflash();
    f.initProperties();
    double rho = f.getPhase(0).getDensity("kg/m3");
    double mu = f.getPhase(0).getViscosity("kg/msec");
    double v = mdot / rho / (Math.PI * d * d / 4.0);
    double re = rho * v * d / mu;
    double fr = Math.pow(-1.8 * Math.log10(Math.pow(4.5e-5 / d / 3.7, 1.11) + 6.9 / re), -2.0);
    double loss = fr * rho * v * v / (2.0 * d) * 1000.0 / 1.0e5;
    assertEquals(loss, 150.0 - r.getBottomholePressureBara(), 0.02 * loss);
    assertEquals(0.0, r.getGravityGainBar(), 1.0e-9);
  }

  /**
   * Gas injection: the density rises with depth, so the hydrostatic gain exceeds the constant wellhead-density
   * estimate, and matches an independent fine-step integration.
   */
  @Test
  public void gasColumnMatchesIndependentIntegration() {
    SystemInterface g = gas();
    double len = 3000.0;
    InjectionWellHydraulics m = new InjectionWellHydraulics(g).setMassFlowRate(0.5).setTubing(0.15, 4.5e-5)
        .setStraightWell(len, 0.0, 30).setLinearTemperature(20.0, 20.0);
    Result r = m.calculateFromWellhead(100.0);
    int n = 300;
    double dz = len / n;
    double p = 100.0;
    for (int i = 0; i < n; i++) {
      double tK = 293.15;
      double pm = p;
      for (int it = 0; it < 3; it++) {
        double rho = density(g, pm, tK);
        double pEnd = p + rho * 9.80665 * dz / 1.0e5;
        pm = 0.5 * (p + pEnd);
        if (it == 2) {
          p = pEnd;
        }
      }
    }
    assertEquals(p, r.getBottomholePressureBara(), 0.01 * p);
    double rhoTop = density(g, 100.0, 293.15);
    assertTrue(r.getBottomholePressureBara() - 100.0 > rhoTop * 9.80665 * len / 1.0e5 * 1.1);
    double[] dens = r.getDensityKgPerM3();
    assertTrue(dens[dens.length - 1] > 0.0);
  }

  /**
   * Cooled-rock fracture pressure reduction equals alpha E dT / (1 - nu) of the fracture gradient pressure.
   */
  @Test
  public void thermalFracturePressureReduction() {
    InjectionWellHydraulics m = new InjectionWellHydraulics(water()).setMassFlowRate(10.0).setTubing(0.1, 4.5e-5)
        .setStraightWell(2000.0, 0.0, 4).setLinearTemperature(20.0, 20.0)
        .setFractureData(0.17, 90.0, 1.0e-5, 15.0, 0.25);
    double expected = 0.17 * 2000.0 - 1.0e-5 * 15.0e9 / 0.75 * 70.0 / 1.0e5;
    assertEquals(expected, m.getEffectiveFracturePressureBara(293.15), 1.0e-9);
    assertEquals(0.17 * 2000.0, m.getEffectiveFracturePressureBara(363.15), 1.0e-9);
    Result r = m.calculateFromWellhead(100.0);
    assertEquals(m.getEffectiveFracturePressureBara(293.15), r.getFracturePressureBara(), 1.0e-9);
    assertEquals(r.getFracturePressureBara() - r.getBottomholePressureBara(), r.getFractureMarginBar(), 1.0e-9);
  }

  /**
   * Ramey thermal model in injection mode warms the fluid with depth and feeds the fluid properties.
   */
  @Test
  public void thermalModelDrivesInjectionTemperature() {
    WellboreThermalModel tm = new WellboreThermalModel().setFlowDirection(WellboreThermalModel.FlowDirection.INJECTION)
        .setSurfaceTemperatureC(5.0).setGeothermalGradient(0.035).setTotalDepth(2500.0).setMassFlowRate(20.0)
        .setInletTemperatureC(15.0);
    InjectionWellHydraulics m = new InjectionWellHydraulics(water()).setMassFlowRate(20.0).setTubing(0.1, 4.5e-5)
        .setStraightWell(2500.0, 0.0, 5).setThermalModel(tm);
    Result r = m.calculateFromWellhead(120.0);
    double[] t = r.getTemperatureK();
    assertEquals(288.15, t[0], 1.0e-9);
    assertTrue(t[t.length - 1] > t[0]);
    assertTrue(t[t.length - 1] < tm.getFormationTemperatureK(2500.0));
  }

  /**
   * Matrix operating point: at the solution the Darcy rate at the tubing bottom-hole pressure equals the injected rate,
   * the rate rises with wellhead pressure, and the fracture cap is applied when exceeded.
   */
  @Test
  public void matrixOperatingPoint() {
    InjectionWellHydraulics m = new InjectionWellHydraulics(water()).setTubing(0.1, 4.5e-5)
        .setStraightWell(2000.0, 0.0, 6).setLinearTemperature(20.0, 60.0)
        .setReservoir(200.0, 20.0, 30.0, 300.0, 0.108, 0.0).setReservoirViscosity(5.0e-4);
    OperatingPoint low = m.findOperatingPoint(30.0);
    OperatingPoint high = m.findOperatingPoint(80.0);
    assertTrue(high.getMassRateKgPerS() > low.getMassRateKgPerS());
    assertTrue(low.getBottomholePressureBara() > 200.0);
    assertEquals(low.getMassRateKgPerS(), m.matrixMassRateKgPerS(low.getBottomholePressureBara()),
        0.01 * low.getMassRateKgPerS());
    assertFalse(low.isFractureLimited());
    m.setFractureData(0.15, 90.0, 1.0e-5, 15.0, 0.25);
    OperatingPoint frac = m.findOperatingPoint(80.0);
    assertTrue(frac.isFractureLimited());
    assertTrue(frac.getCappedMassRateKgPerS() < frac.getMassRateKgPerS());
    assertFalse(frac.getWarnings().isEmpty());
    double darcy = 2.0 * Math.PI * 20.0 * 9.869233e-16 * 30.0 * (frac.getFracturePressureBara() - 200.0) * 1.0e5
        / (5.0e-4 * (Math.log(300.0 / 0.108) - 0.75));
    double rhoFrac = density(water(), frac.getFracturePressureBara(), 333.15);
    assertEquals(darcy * rhoFrac, frac.getCappedMassRateKgPerS(), 0.01 * darcy * rhoFrac);
  }
}
