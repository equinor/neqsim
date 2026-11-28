package neqsim.process.equipment.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import neqsim.process.equipment.pipeline.twophasepipe.TwoFluidSection;
import neqsim.process.equipment.pipeline.twophasepipe.closure.GeometryCalculator;
import neqsim.process.equipment.pipeline.twophasepipe.closure.GeometryCalculator.StratifiedGeometry;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for {@link StratifiedLiquidMomentum}: analytical limits and a cross-check against the converged steady state of
 * {@link TwoFluidPipe}.
 *
 * @author NeqSim development team
 * @version 1.0
 */
class StratifiedLiquidMomentumTest {
  /** Logger. */
  private static final Logger logger = LogManager.getLogger(StratifiedLiquidMomentumTest.class);

  /** With no pressure gradient, no gas flow and a level liquid surface the liquid is at rest. */
  @Test
  void liquidAtRestWithoutDrivingForce() {
    double u = StratifiedLiquidMomentum.liquidVelocity(0.02, 1.0, 45e-6, 0.0, 100.0, 550.0, 1.5e-5, 2.0e-4, 0.0);
    assertEquals(0.0, u, 1e-8);
    assertEquals(0.0, StratifiedLiquidMomentum.liquidVelocity(0.0, 1.0, 45e-6, 5.0, 100.0, 550.0, 1.5e-5, 2.0e-4, 0),
        0.0);
  }

  /**
   * A viscous film draining down a slope under stagnant, light gas is laminar; with the Fanning factor 16/Re the force
   * balance has the closed form u = rho g sin(theta) A_L D_hL / (8 mu S_L).
   */
  @Test
  void laminarDrainageMatchesClosedForm() {
    double holdup = 0.01;
    double diameter = 0.5;
    double rhoL = 800.0;
    double muL = 0.5;
    double slope = -0.002;
    StratifiedGeometry g = new GeometryCalculator().calculateFromHoldup(holdup, diameter);
    double expected = (rhoL - 1.0e-3) * 9.81 * (-slope) * g.liquidArea * g.liquidHydraulicDiameter
        / (8.0 * muL * g.liquidWettedPerimeter);
    double u = StratifiedLiquidMomentum.liquidVelocity(holdup, diameter, 45e-6, 0.0, 1.0e-3, rhoL, 1.5e-5, muL, slope);
    assertTrue(rhoL * u * g.liquidHydraulicDiameter / muL < 100.0, "case must be laminar");
    assertEquals(expected, u, 0.02 * expected);
  }

  /** Gas drag moves liquid uphill only above a critical gas velocity, and the velocity grows with the gas rate. */
  @Test
  void uphillTransportNeedsEnoughGasDrag() {
    double slope = Math.sin(Math.toRadians(1.0));
    double previous = Double.NEGATIVE_INFINITY;
    double atRest = 0;
    double fast = 0;
    for (double uG = 0.0; uG <= 12.0; uG += 1.0) {
      double u = StratifiedLiquidMomentum.liquidVelocity(0.02, 1.0, 45e-6, uG, 100.0, 550.0, 1.5e-5, 2.0e-4, slope);
      assertTrue(u >= previous - 1e-9, "liquid velocity must not decrease with gas velocity");
      previous = u;
      if (uG == 0.0) {
        atRest = u;
      }
      fast = u;
    }
    assertTrue(atRest < 0.0, "without gas the film drains back downhill");
    assertTrue(fast > 0.0, "strong gas flow carries the film uphill");
    assertTrue(fast < 12.0, "the film is slower than the gas");
  }

  /**
   * Cross-check against TwoFluidPipe: for stratified steady flow the holdup at which the combined momentum equation
   * carries the same liquid rate must agree with the converged TwoFluidPipe holdup. Measured 2026-11-27: within 5-11 %
   * on eight stratified cases (0.5 and 1.0 m, 60 and 100 bara, -1 to +0.5 degrees); this test repeats four of them.
   */
  @Test
  @Timeout(value = 5, unit = TimeUnit.MINUTES)
  void holdupAgreesWithTwoFluidPipeInStratifiedFlow() {
    double[][] cases = {{40, 0.5, 0.0, 60, 0.6}, {40, 0.5, -1.0, 60, 2.0}, {150, 1.0, -0.3, 100, 0.6},
        {80, 0.5, 0.5, 60, 0.6}};
    for (double[] c : cases) {
      SystemInterface fluid = new SystemSrkEos(288.15, c[3]);
      fluid.addComponent("methane", 90.0 - c[4]);
      fluid.addComponent("ethane", 6.0);
      fluid.addComponent("propane", 3.0);
      fluid.addComponent("n-heptane", c[4] * 0.6);
      fluid.addComponent("nC10", c[4] * 0.4);
      fluid.setMixingRule("classic");
      fluid.setMultiPhaseCheck(true);
      Stream feed = new Stream("feed", fluid);
      feed.setFlowRate(c[0], "kg/sec");
      feed.setTemperature(15.0, "C");
      feed.setPressure(c[3], "bara");
      feed.run();
      TwoFluidPipe pipe = new TwoFluidPipe("reference", feed);
      int n = 40;
      double length = 8000.0;
      pipe.setLength(length);
      pipe.setDiameter(c[1]);
      pipe.setRoughness(45e-6);
      pipe.setNumberOfSections(n);
      double slope = Math.sin(Math.toRadians(c[2]));
      double[] z = new double[n];
      for (int i = 0; i < n; i++) {
        z[i] = slope * (i + 0.5) * length / n;
      }
      pipe.setElevationProfile(z);
      pipe.setHeatTransferCoefficient(0.0);
      pipe.setOutletPressure(c[3], "bara");
      pipe.setSteadyStateMaxWallClockTime(120.0);
      pipe.run();
      assertTrue(pipe.getSteadyStateConvergenceReport().isConverged());
      TwoFluidSection[] s = pipe.getSectionSnapshots();
      int i = n / 2;
      double usl = s[i].getSuperficialLiquidVelocity();
      double usg = s[i].getSuperficialGasVelocity();
      double low = 1e-7;
      double high = 0.9;
      for (int it = 0; it < 60; it++) {
        double mid = Math.sqrt(low * high);
        double u = StratifiedLiquidMomentum.liquidVelocity(mid, c[1], 45e-6, usg / (1 - mid), s[i].getGasDensity(),
            s[i].getLiquidDensity(), s[i].getGasViscosity(), s[i].getLiquidViscosity(), slope);
        if (u * mid > usl) {
          high = mid;
        } else {
          low = mid;
        }
      }
      double holdup = Math.sqrt(low * high);
      double reference = s[i].getLiquidHoldup();
      logger.info("{} kg/s, D {} m, {} deg: regime {}, TwoFluidPipe holdup {}, force-balance holdup {}", c[0], c[1],
          c[2], s[i].getFlowRegime(), reference, holdup);
      assertTrue(String.valueOf(s[i].getFlowRegime()).startsWith("STRATIFIED"), "reference case must be stratified");
      assertEquals(reference, holdup, 0.15 * reference, "holdup for " + c[0] + " kg/s at " + c[2] + " deg");
    }
  }
}
