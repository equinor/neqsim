package neqsim.process.equipment.valve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.chemistry.scale.ScaleKinetics;
import neqsim.process.chemistry.scale.ValveScaleDrift;
import neqsim.process.equipment.stream.Stream;

/**
 * Regression tests for scale-induced valve fouling and the {@link ValveScaleDrift} coupling that turns a deposit growth
 * rate into a drift of the effective flow coefficient and valve opening.
 */
public class ValveScaleDriftTest {

  private ThrottlingValve buildLiquidValve() {
    neqsim.thermo.system.SystemInterface fluid = new neqsim.thermo.system.SystemSrkEos((273.15 + 25.0), 20.0);
    fluid.addComponent("water", 1.0);
    fluid.setMixingRule(2);

    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(20000.0, "kg/hr");
    feed.setPressure(20.0, "bara");
    feed.setTemperature(25.0, "C");
    feed.run();

    ThrottlingValve valve = new ThrottlingValve("LV", feed);
    valve.setOutletPressure(10.0);
    valve.setPercentValveOpening(100.0);
    valve.setCalculateSteadyState(false);
    valve.run();
    return valve;
  }

  @Test
  void testFoulingFractionAccessorsAndClamping() {
    ThrottlingValve valve = buildLiquidValve();
    double cleanKv = valve.getKv();
    double cleanCv = valve.getCv("US");

    assertEquals(0.0, valve.getFoulingFraction(), 1e-12);
    assertEquals(cleanKv, valve.getEffectiveKv(), 1e-9);
    assertEquals(cleanCv, valve.getEffectiveCv(), 1e-9);

    valve.setFoulingFraction(0.4);
    assertEquals(0.4, valve.getFoulingFraction(), 1e-12);
    assertEquals(cleanKv * 0.6, valve.getEffectiveKv(), 1e-9);
    assertEquals(cleanCv * 0.6, valve.getEffectiveCv(), 1e-9);

    // Clamping
    valve.setFoulingFraction(-0.2);
    assertEquals(0.0, valve.getFoulingFraction(), 1e-12);
    valve.setFoulingFraction(1.5);
    assertTrue(valve.getFoulingFraction() < 1.0 && valve.getFoulingFraction() > 0.999);
  }

  @Test
  void testFoulingReducesFlow() {
    ThrottlingValve valve = buildLiquidValve();
    // Kv is sized on the first steady-state run; the transient path lets the effective Kv throttle the flow.
    valve.setFoulingFraction(0.0);
    valve.runTransient(0.1);
    double cleanFlow = valve.getOutletStream().getFlowRate("kg/hr");

    valve.setFoulingFraction(0.5);
    valve.runTransient(0.1);
    double fouledFlow = valve.getOutletStream().getFlowRate("kg/hr");

    assertTrue(fouledFlow < cleanFlow, "Fouled valve must pass less flow than clean valve");
    // For an incompressible liquid, flow is proportional to the effective Kv.
    assertEquals(0.5, fouledFlow / cleanFlow, 0.05);
  }

  @Test
  void testDepositAccumulationAndAreaLoss() {
    ThrottlingValve valve = buildLiquidValve();
    ValveScaleDrift drift = new ValveScaleDrift(valve);
    drift.setPortDiameter(0.05); // 50 mm port
    drift.setGrowthRateMmPerYear(20.0);

    // Advance exactly one year → deposit thickness equals the annual growth rate.
    drift.advance(365.25);
    assertEquals(20.0, drift.getDepositThicknessMm(), 1e-6);
    assertEquals(365.25, drift.getElapsedDays(), 1e-6);

    // Open diameter 50 - 2*20 = 10 mm → area fraction (10/50)^2 = 0.04.
    assertEquals(0.04, drift.getOpenAreaFraction(), 1e-6);
    assertEquals(0.96, drift.getFoulingFraction(), 1e-6);
    // The valve was updated by advance().
    assertEquals(0.96, valve.getFoulingFraction(), 1e-6);
    assertEquals(valve.getKv() * 0.04, drift.getEffectiveKv(), 1e-9);
  }

  @Test
  void testTimeToPlug() {
    ThrottlingValve valve = buildLiquidValve();
    ValveScaleDrift drift = new ValveScaleDrift(valve);
    drift.setPortDiameter(0.05); // half-port = 25 mm
    drift.setGrowthRateMmPerYear(20.0);

    // Full closure when deposit reaches 25 mm → 25/20 yr = 1.25 yr = 456.56 days.
    assertEquals(456.5625, drift.getTimeToPlugDays(), 1e-2);
  }

  @Test
  void testOpeningDriftIsMonotonicAndPins() {
    ThrottlingValve valve = buildLiquidValve();
    ValveScaleDrift drift = new ValveScaleDrift(valve);
    drift.setPortDiameter(0.05);
    drift.setGrowthRateMmPerYear(20.0);

    double cleanOpening = 42.0; // the level valve held ~42% when clean
    double previous = drift.predictOpeningPercent(cleanOpening);
    assertEquals(cleanOpening, previous, 1e-9); // no deposit yet

    // Analytic pin time for a linear characteristic (evaluated on the fresh, undeposited state):
    // t_pin = (d0/2)*(1 - sqrt(0.42)) / growth.
    double predicted = drift.predictTimeToPinDays(cleanOpening);
    assertTrue(predicted > 0.0 && predicted < 400.0);

    boolean pinned = false;
    for (int day = 1; day <= 400; day++) {
      drift.advance(1.0);
      double opening = drift.predictOpeningPercent(cleanOpening);
      assertTrue(opening >= previous - 1e-9, "Required opening must not decrease as deposit grows");
      previous = opening;
      if (opening >= 100.0) {
        pinned = true;
        break;
      }
    }
    assertTrue(pinned, "Valve opening must eventually drift to 100% (loss of control)");
  }

  @Test
  void testKineticsSuppliesGrowthRate() {
    ThrottlingValve valve = buildLiquidValve();
    ScaleKinetics kinetics = new ScaleKinetics();
    kinetics.setSaturationIndex(1.0); // supersaturated → non-zero growth
    kinetics.setSurfaceReaction(5.0, 2.0);
    kinetics.evaluate();

    ValveScaleDrift drift = new ValveScaleDrift(valve);
    drift.setPortDiameter(0.05);
    drift.setKinetics(kinetics);

    drift.advance(30.0);
    assertTrue(drift.getDepositThicknessMm() > 0.0, "Supersaturated kinetics must produce deposit growth");
    assertTrue(valve.getFoulingFraction() > 0.0);
  }

  @Test
  void testReset() {
    ThrottlingValve valve = buildLiquidValve();
    ValveScaleDrift drift = new ValveScaleDrift(valve);
    drift.setPortDiameter(0.05);
    drift.setGrowthRateMmPerYear(20.0);
    drift.advance(100.0);
    assertTrue(valve.getFoulingFraction() > 0.0);

    drift.reset();
    assertEquals(0.0, drift.getDepositThicknessMm(), 1e-12);
    assertEquals(0.0, drift.getElapsedDays(), 1e-12);
    assertEquals(0.0, valve.getFoulingFraction(), 1e-12);
  }
}
