package neqsim.process.diagnostics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link OscillationAnalyzer} on synthetic signals with known properties.
 *
 * @author ESOL
 * @version 1.0
 */
public class OscillationAnalyzerTest {
  private static final double DT = 10.0;
  private static final double PERIOD = 400.0;

  private static double[] noiseDrivenResonance(int n, double zeta, long seed) {
    double w0 = 2.0 * Math.PI / PERIOD;
    double[] x = new double[n];
    double pos = 0.0;
    double vel = 0.0;
    Random rnd = new Random(seed);
    double sub = DT / 20.0;
    for (int i = 0; i < n; i++) {
      for (int s = 0; s < 20; s++) {
        double force = rnd.nextGaussian() * 0.05 / Math.sqrt(sub);
        double acc = force - 2.0 * zeta * w0 * vel - w0 * w0 * pos;
        vel += acc * sub;
        pos += vel * sub;
      }
      x[i] = pos;
    }
    return x;
  }

  /**
   * A sine with constant amplitude is a limit cycle: period found, small envelope CV.
   */
  @Test
  public void detectsPeriodAndLimitCycle() {
    int n = 4096;
    double[] x = new double[n];
    Random rnd = new Random(1);
    for (int i = 0; i < n; i++) {
      x[i] = 0.4 * Math.sin(2.0 * Math.PI * i * DT / PERIOD) + 0.01 * rnd.nextGaussian() + 18.0;
    }
    OscillationAnalyzer a = new OscillationAnalyzer(DT);
    OscillationAnalyzer.Result r = a.analyze(x, 120.0, 1200.0);
    assertEquals(PERIOD, r.getPeakPeriodSeconds(), 12.0);
    assertEquals(0.4 / Math.sqrt(2.0), r.getBandRms(), 0.03);
    assertTrue(r.getEnvelopeCv() < 0.15);
    assertTrue(r.classify().contains("limit cycle"));
  }

  /**
   * A noise-driven lightly damped resonance has a Rayleigh-like envelope and a finite Q.
   */
  @Test
  public void detectsNoiseDrivenMode() {
    double[] x = noiseDrivenResonance(16384, 0.12, 7L);
    OscillationAnalyzer a = new OscillationAnalyzer(DT);
    a.setSegmentLength(1024);
    OscillationAnalyzer.Result r = a.analyze(x, 120.0, 1200.0);
    assertEquals(PERIOD, r.getPeakPeriodSeconds(), 45.0);
    assertTrue(r.getEnvelopeCv() > 0.30 && r.getEnvelopeCv() < 0.75, "cv " + r.getEnvelopeCv());
    assertTrue(r.getQ() > 2.5 && r.getQ() < 9.0, "Q " + r.getQ());
    assertTrue(r.classify().contains("noise-driven"));
  }

  /**
   * The phase of a leading signal is reported as positive.
   */
  @Test
  public void crossSpectrumPhaseAndCoherence() {
    int n = 4096;
    double[] x = new double[n];
    double[] y = new double[n];
    double phi = Math.toRadians(60.0);
    Random rnd = new Random(3);
    for (int i = 0; i < n; i++) {
      double t = i * DT;
      x[i] = Math.sin(2.0 * Math.PI * t / PERIOD) + 0.05 * rnd.nextGaussian();
      y[i] = 2.0 * Math.sin(2.0 * Math.PI * t / PERIOD + phi) + 0.05 * rnd.nextGaussian();
    }
    OscillationAnalyzer a = new OscillationAnalyzer(DT);
    OscillationAnalyzer.CrossResult c = a.crossSpectrum(x, y, 1.0 / PERIOD);
    assertEquals(60.0, c.getPhaseDegrees(), 6.0);
    assertTrue(c.getCoherence() > 0.95);
    assertEquals(2.0, c.getGain(), 0.2);
  }

  /**
   * A flow that follows pressure in phase gives an inflow phasor that leads the pressure by the volume term.
   */
  @Test
  public void inflowPhasorLeadsPressureWithGasVolume() {
    int n = 4096;
    double[] p = new double[n];
    double[] m = new double[n];
    double f0 = 1.0 / PERIOD;
    for (int i = 0; i < n; i++) {
      double t = i * DT;
      p[i] = 0.5 * Math.sin(2.0 * Math.PI * f0 * t);
      m[i] = 4.5 * p[i];
    }
    OscillationAnalyzer a = new OscillationAnalyzer(DT);
    double[] ph = a.inflowPhasor(p, m, f0, 250.0, 0.79);
    double w = 2.0 * Math.PI * f0;
    double expected = Math.toDegrees(Math.atan2(w * 250.0 * 0.79, 4.5));
    assertEquals(expected, ph[1], 3.0);
    assertEquals(Math.hypot(4.5, w * 250.0 * 0.79), ph[0], 0.15);
    assertTrue(ph[2] > 0.0 && ph[2] < PERIOD);
  }
}
