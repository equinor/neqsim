package neqsim.process.fielddevelopment.wellplanning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import neqsim.process.fielddevelopment.wellplanning.NodalAnalysis.InflowCurve;
import neqsim.process.fielddevelopment.wellplanning.NodalAnalysis.Intersection;
import neqsim.process.fielddevelopment.wellplanning.NodalAnalysis.OutflowCurve;
import neqsim.process.fielddevelopment.wellplanning.NodalAnalysis.Result;
import neqsim.thermo.system.SystemInterface;

/**
 * Tests of {@link NodalAnalysis} against closed-form intersections and the drift-flux and gas-condensate adapters.
 *
 * @author NeqSim
 * @version 1.0
 */
public class NodalAnalysisTest {

  /**
   * Linear inflow and outflow intersect at q = (pr - a) / (b + 1/PI).
   */
  @Test
  public void linearIntersectionMatchesClosedForm() {
    InflowCurve ipr = NodalAnalysis.linearInflow(300.0, 2.0);
    OutflowCurve vlp = new OutflowCurve() {
      @Override
      public double requiredBottomholePressureBara(double rate) {
        return 80.0 + 0.5 * rate;
      }
    };
    Result r = new NodalAnalysis(ipr, vlp).setRateRange(0.1, 1000.0).solve();
    assertNotNull(r.getOperatingPoint());
    assertEquals(220.0, r.getOperatingPoint().getRate(), 1.0e-6);
    assertEquals(190.0, r.getOperatingPoint().getBottomholePressureBara(), 1.0e-6);
    assertTrue(r.getOperatingPoint().isStable());
    assertEquals(1, r.getIntersections().size());
  }

  /**
   * Vogel inflow against a constant required pressure gives the Vogel rate.
   */
  @Test
  public void vogelAgainstConstantPressure() {
    InflowCurve ipr = NodalAnalysis.vogelInflow(250.0, 1000.0);
    assertEquals(250.0, ipr.bottomholePressureBara(0.0), 1.0e-9);
    assertEquals(0.0, ipr.bottomholePressureBara(1000.0), 1.0e-9);
    double x = 100.0 / 250.0;
    double expected = 1000.0 * (1.0 - 0.2 * x - 0.8 * x * x);
    OutflowCurve vlp = new OutflowCurve() {
      @Override
      public double requiredBottomholePressureBara(double rate) {
        return 100.0;
      }
    };
    Result r = new NodalAnalysis(ipr, vlp).setRateRange(1.0, 999.0).solve();
    assertEquals(expected, r.getOperatingPoint().getRate(), 1.0e-5);
  }

  /**
   * A U-shaped outflow (liquid loading) gives an unstable low-rate and a stable high-rate intersection; the operating
   * point is the stable one.
   */
  @Test
  public void uShapedOutflowHasUnstableLowRateIntersection() {
    InflowCurve ipr = NodalAnalysis.linearInflow(300.0, 10.0);
    OutflowCurve vlp = new OutflowCurve() {
      @Override
      public double requiredBottomholePressureBara(double rate) {
        return 100.0 + 0.5 * rate + 2000.0 / rate;
      }
    };
    Result r = new NodalAnalysis(ipr, vlp).setRateRange(0.5, 400.0).setScanPoints(800).solve();
    List<Intersection> x = r.getIntersections();
    assertEquals(2, x.size());
    assertFalse(x.get(0).isStable());
    assertTrue(x.get(1).isStable());
    assertEquals(x.get(1).getRate(), r.getOperatingPoint().getRate(), 0.0);
    double q1 = x.get(0).getRate();
    double q2 = x.get(1).getRate();
    for (double q : new double[] {q1, q2}) {
      assertEquals(300.0 - q / 10.0, 100.0 + 0.5 * q + 2000.0 / q, 1.0e-6);
    }
    assertTrue(q1 < 20.0 && q2 > 100.0);
  }

  /**
   * No intersection when the outflow always exceeds the inflow.
   */
  @Test
  public void noOperatingPointWhenOutflowTooHigh() {
    InflowCurve ipr = NodalAnalysis.linearInflow(100.0, 5.0);
    OutflowCurve vlp = NodalAnalysis.tabulatedOutflow(new double[] {1.0, 100.0}, new double[] {150.0, 400.0});
    Result r = new NodalAnalysis(ipr, vlp).setRateRange(1.0, 100.0).solve();
    assertNull(r.getOperatingPoint());
    assertTrue(r.getIntersections().isEmpty());
  }

  /**
   * Sensitivities: higher wellhead pressure and higher skin (lower productivity) lower the rate monotonically.
   */
  @Test
  public void sensitivitySweepsAreMonotonic() {
    InflowCurve ipr = NodalAnalysis.linearInflow(300.0, 2.0);
    double[][] whp = NodalAnalysis.sweepOutflow(ipr, new NodalAnalysis.OutflowFactory() {
      @Override
      public OutflowCurve create(final double value) {
        return new OutflowCurve() {
          @Override
          public double requiredBottomholePressureBara(double rate) {
            return value + 50.0 + 0.5 * rate;
          }
        };
      }
    }, new double[] {10.0, 30.0, 60.0}, 0.1, 1000.0);
    assertTrue(whp[0][1] > whp[1][1] && whp[1][1] > whp[2][1]);
    assertEquals((300.0 - 60.0) / 1.0, whp[0][1], 1.0e-6);
    double[][] pi = NodalAnalysis.sweepInflow(new NodalAnalysis.InflowFactory() {
      @Override
      public InflowCurve create(double value) {
        return NodalAnalysis.linearInflow(300.0, value);
      }
    }, new OutflowCurve() {
      @Override
      public double requiredBottomholePressureBara(double rate) {
        return 80.0 + 0.5 * rate;
      }
    }, new double[] {1.0, 2.0, 4.0}, 0.1, 1000.0);
    assertTrue(pi[0][1] < pi[1][1] && pi[1][1] < pi[2][1]);
  }

  /**
   * Drift-flux outflow with a linear inflow: at the solution the tubing and reservoir bottom-hole pressures agree.
   */
  @Test
  public void driftFluxOutflowSolution() {
    SystemInterface oil = DriftFluxWellboreHydraulicsTest.oilFluid();
    DriftFluxWellboreHydraulics hyd = new DriftFluxWellboreHydraulics(oil).setTubing(0.1, 4.5e-5)
        .setStraightWell(2500.0, 0.0, 12).setLinearTemperature(40.0, 90.0);
    InflowCurve ipr = NodalAnalysis.linearInflow(250.0, 0.3);
    OutflowCurve vlp = NodalAnalysis.fromDriftFlux(hyd, 30.0, 1.0);
    Result r = new NodalAnalysis(ipr, vlp).setRateRange(1.0, 60.0).setScanPoints(20).solve();
    Intersection op = r.getOperatingPoint();
    assertNotNull(op);
    hyd.setMassFlowRate(5.0);
    vlp.requiredBottomholePressureBara(12.0);
    assertEquals(5.0, hyd.getMassFlowRate(), 0.0);
    hyd.setMassFlowRate(op.getRate());
    assertEquals(op.getBottomholePressureBara(), hyd.calculateFromWellhead(30.0).getBottomholePressureBara(), 0.1);
    assertEquals(250.0 - op.getRate() / 0.3, op.getBottomholePressureBara(), 1.0e-6);
  }

  /**
   * The gas-condensate adapter inverts the model IPR.
   */
  @Test
  public void gasCondensateAdapterInvertsInflow() {
    SystemInterface gc = DriftFluxWellboreHydraulicsTest.condensateFluid();
    GasCondensateInflowModel m = new GasCondensateInflowModel(gc, 110.0).setReservoirPressure(450.0)
        .setFormation(20.0, 40.0).setGeometry(500.0, 0.108, 0.0).setIntegrationSteps(8);
    double q = m.calculate(300.0).getGasRateSm3PerDay();
    assertEquals(300.0, NodalAnalysis.fromGasCondensate(m).bottomholePressureBara(q), 0.5);
  }
}
