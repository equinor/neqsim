package neqsim.process.fielddevelopment.wellplanning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import neqsim.process.fielddevelopment.wellplanning.GasCondensateInflowModel.Result;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Tests of {@link GasCondensateInflowModel}: dry-gas pseudo-pressure limit, Forchheimer limits and banking behaviour.
 *
 * @author NeqSim
 * @version 1.0
 */
public class GasCondensateInflowModelTest {

  /**
   * Builds a lean dry gas.
   *
   * @return fluid
   */
  private static SystemInterface dryGas() {
    SystemInterface f = new SystemSrkEos(273.15 + 100.0, 300.0);
    f.addComponent("methane", 0.9);
    f.addComponent("ethane", 0.1);
    f.setMixingRule("classic");
    return f;
  }

  /**
   * Builds a synthetic retrograde gas-condensate (dew point about 215 bara at 110 degrees Celsius).
   *
   * @return fluid
   */
  private static SystemInterface condensate() {
    SystemInterface f = new SystemSrkEos(273.15 + 110.0, 300.0);
    f.addComponent("methane", 0.75);
    f.addComponent("ethane", 0.08);
    f.addComponent("propane", 0.05);
    f.addComponent("n-butane", 0.03);
    f.addComponent("n-hexane", 0.04);
    f.addComponent("n-octane", 0.03);
    f.addComponent("nC10", 0.02);
    f.setMixingRule("classic");
    return f;
  }

  /**
   * Dry gas equals the real-gas pseudo-pressure solution q = pi k h Tsc dm / (psc T (ln(re/rw) - 0.75 + s)).
   */
  @Test
  public void dryGasMatchesPseudoPressureSolution() {
    SystemInterface gas = dryGas();
    double tRes = 373.15;
    double k = 5.0;
    double h = 30.0;
    double re = 500.0;
    double rw = 0.1;
    double skin = 2.0;
    double pe = 300.0;
    double pwf = 150.0;
    GasCondensateInflowModel m = new GasCondensateInflowModel(gas, 100.0).setReservoirPressure(pe).setFormation(k, h)
        .setGeometry(re, rw, skin).setIntegrationSteps(40);
    Result r = m.calculate(pwf);
    int n = 300;
    double dp = (pe - pwf) / n;
    double dm = 0.0;
    double prev = integrand(gas, pwf, tRes);
    for (int i = 1; i <= n; i++) {
      double cur = integrand(gas, pwf + i * dp, tRes);
      dm += 0.5 * (prev + cur) * dp * 1.0e5 * 1.0e5;
      prev = cur;
    }
    double kSi = k * 9.869233e-16;
    double expected = Math.PI * kSi * h * 288.15 / (101325.0 * tRes) * dm / (Math.log(re / rw) - 0.75 + skin) * 86400.0;
    assertEquals(expected, r.getGasRateSm3PerDay(), 0.005 * expected);
    assertEquals(1.0, r.getBankingImpairment(), 1.0e-12);
    assertFalse(r.isBankingActive());
    assertEquals(0.0, r.getDewPointBara(), 1.0e-9);
    assertEquals(r.getDarcyRateSm3PerDay(), r.getGasRateSm3PerDay(), 1.0e-9 * expected);
  }

  /**
   * Pseudo-pressure integrand 2 / (mu Z) with pressure in Pa units handled by the caller.
   *
   * @param base fluid
   * @param pBar pressure in bara
   * @param tK temperature in K
   * @return 2 p / (mu Z) in 1/(Pa s) times Pa
   */
  private static double integrand(SystemInterface base, double pBar, double tK) {
    SystemInterface f = base.clone();
    f.setPressure(pBar);
    f.setTemperature(tK);
    new ThermodynamicOperations(f).TPflash();
    f.initProperties();
    PhaseInterface ph = f.getPhase(0);
    double zEff = pBar * 1.0e5 * ph.getMolarMass() / (ph.getDensity("kg/m3") * 8.314462618 * tK);
    return 2.0 * pBar / (ph.getViscosity("kg/msec") * zEff);
  }

  /**
   * Rate is proportional to the gas endpoint relative permeability in single-phase flow.
   */
  @Test
  public void rateScalesWithGasEndpointPermeability() {
    GasCondensateInflowModel m = new GasCondensateInflowModel(dryGas(), 100.0).setReservoirPressure(300.0)
        .setFormation(5.0, 30.0);
    double q1 = m.calculate(200.0).getGasRateSm3PerDay();
    m.setRelativePermeability(0.2, 0.1, 0.5, 1.0, 3.0, 3.0);
    double q2 = m.calculate(200.0).getGasRateSm3PerDay();
    assertEquals(0.5, q2 / q1, 1.0e-9);
  }

  /**
   * The Forchheimer term lowers the rate, satisfies the inflow quadratic, and in the inertia-dominated limit the rate
   * falls as 1/sqrt(beta).
   */
  @Test
  public void forchheimerLimits() {
    GasCondensateInflowModel m = new GasCondensateInflowModel(dryGas(), 100.0).setReservoirPressure(300.0)
        .setFormation(50.0, 30.0).setGeometry(500.0, 0.1, 0.0);
    Result darcy = m.calculate(150.0);
    assertEquals(1.0, darcy.getNonDarcyFactor(), 1.0e-12);
    m.setForchheimerBeta(1.0e9);
    Result mild = m.calculate(150.0);
    assertTrue(mild.getGasRateSm3PerDay() < darcy.getGasRateSm3PerDay());
    assertTrue(mild.getNonDarcyFactor() < 1.0 && mild.getNonDarcyFactor() > 0.1);
    assertEquals(0.0, mild.getNonDarcyResidual(), 1.0e-9);
    m.setForchheimerBeta(1.0e14);
    double q1 = m.calculate(150.0).getGasRateSm3PerDay();
    m.setForchheimerBeta(1.0e16);
    double q2 = m.calculate(150.0).getGasRateSm3PerDay();
    assertEquals(10.0, q1 / q2, 0.3);
  }

  /**
   * Geertsma coefficient has the expected order of magnitude and scales with permeability and porosity.
   */
  @Test
  public void geertsmaBetaScaling() {
    GasCondensateInflowModel m = new GasCondensateInflowModel(dryGas(), 100.0).setFormation(10.0, 30.0);
    double b10 = m.useGeertsmaBeta(0.2);
    m.setFormation(40.0, 30.0);
    double b40 = m.useGeertsmaBeta(0.2);
    assertEquals(0.5, b40 / b10, 1.0e-9);
    assertTrue(b10 > 1.0e8 && b10 < 1.0e10);
    assertTrue(m.useGeertsmaBeta(0.1) > b40);
  }

  /**
   * Condensate banking: no impairment above the dew point, growing impairment and positive skin below it.
   */
  @Test
  public void condensateBankingImpairmentGrowsBelowDewPoint() {
    GasCondensateInflowModel m = new GasCondensateInflowModel(condensate(), 110.0).setReservoirPressure(450.0)
        .setFormation(20.0, 40.0).setGeometry(500.0, 0.108, 0.0);
    double pDew = m.getDewPointPressureBara();
    assertTrue(pDew > 180.0 && pDew < 260.0);
    Result above = m.calculate(300.0);
    assertEquals(1.0, above.getBankingImpairment(), 1.0e-12);
    assertFalse(above.isBankingActive());
    Result a = m.calculate(180.0);
    Result b = m.calculate(130.0);
    Result c = m.calculate(80.0);
    assertTrue(a.isBankingActive());
    assertTrue(a.getBankingImpairment() < 1.0);
    assertTrue(b.getBankingImpairment() < a.getBankingImpairment());
    assertTrue(c.getBankingImpairment() < b.getBankingImpairment());
    assertTrue(c.getBankingSkin() > b.getBankingSkin() && b.getBankingSkin() > a.getBankingSkin());
    double sTotal = Math.log(500.0 / 0.108) - 0.75;
    assertEquals(sTotal * (1.0 / c.getBankingImpairment() - 1.0), c.getBankingSkin(), 1.0e-9);
    assertTrue(c.getGasRateSm3PerDay() > b.getGasRateSm3PerDay() && b.getGasRateSm3PerDay() > a.getGasRateSm3PerDay());
    assertTrue(c.getWellKrg() < 1.0);
    assertTrue(c.getWellOilSaturation() > 0.0);
    assertEquals(c.getCondensateYieldSm3PerSm3() * c.getSeparatorGasRateSm3PerDay(), c.getCondensateRateSm3PerDay(),
        1.0e-6 * c.getCondensateRateSm3PerDay());
    assertTrue(c.getCondensateYieldSm3PerSm3() > 1.0e-4 && c.getCondensateYieldSm3PerSm3() < 5.0e-3);
  }

  /**
   * Higher drawdown always yields higher rate (monotonic IPR) and the pressure-for-rate inverse is consistent.
   */
  @Test
  public void iprIsMonotonicAndInvertible() {
    GasCondensateInflowModel m = new GasCondensateInflowModel(condensate(), 110.0).setReservoirPressure(450.0)
        .setFormation(20.0, 40.0).setGeometry(500.0, 0.108, 0.0);
    double[][] ipr = m.generateIpr(5);
    for (int i = 1; i < ipr.length; i++) {
      assertTrue(ipr[i][0] < ipr[i - 1][0]);
      assertTrue(ipr[i][1] > ipr[i - 1][1]);
    }
    double target = ipr[2][1];
    double pwf = m.flowingPressureForRate(target);
    assertEquals(ipr[2][0], pwf, 0.5);
    assertThrows(IllegalArgumentException.class, () -> m.calculate(460.0));
  }
}
