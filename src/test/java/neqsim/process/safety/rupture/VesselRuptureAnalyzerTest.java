package neqsim.process.safety.rupture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link VesselRuptureAnalyzer}.
 *
 * <p>
 * Validates the time-marching rupture screening: a vessel whose von Mises wall stress overtakes the temperature-derated
 * allowable stress is flagged as ruptured with a finite rupture time, while a mild duty stays intact and the minimum
 * margin is always tracked.
 *
 * @author ESOL
 * @version 1.0
 */
public class VesselRuptureAnalyzerTest {

  /** Build a high-pressure, fire-heated ramp that should cross the rupture limit. */
  private static VesselRuptureAnalyzer.VesselRuptureResult runSevereCase() {
    MaterialStrengthCurve material = MaterialStrengthCurve.forApi5LPipeGrade("X65");
    VesselRuptureAnalyzer analyzer = new VesselRuptureAnalyzer(0.5, 0.01, material);
    int n = 11;
    double[] time = new double[n];
    double[] pressure = new double[n];
    double[] metalT = new double[n];
    for (int i = 0; i < n; i++) {
      time[i] = i * 30.0;
      pressure[i] = (10.0 + i * 9.0) * 1.0e5;
      metalT[i] = 700.0 + 273.15;
    }
    return analyzer.analyze(time, pressure, metalT);
  }

  /** A severe pressure ramp at fire temperature must be flagged as ruptured. */
  @Test
  public void severeCaseRuptures() {
    VesselRuptureAnalyzer.VesselRuptureResult result = runSevereCase();
    assertTrue(result.ruptured, "Severe ramp should rupture");
    assertTrue(Double.isFinite(result.ruptureTimeS), "Rupture time should be finite");
    assertTrue(result.ruptureTimeS >= 0.0);
  }

  /** A mild low-pressure ambient duty must not rupture and must keep a positive margin. */
  @Test
  public void mildCaseSurvives() {
    MaterialStrengthCurve material = MaterialStrengthCurve.forApi5LPipeGrade("X65");
    VesselRuptureAnalyzer analyzer = new VesselRuptureAnalyzer(0.5, 0.02, material);
    int n = 6;
    double[] time = new double[n];
    double[] pressure = new double[n];
    double[] metalT = new double[n];
    for (int i = 0; i < n; i++) {
      time[i] = i * 30.0;
      pressure[i] = 20.0e5;
      metalT[i] = 300.0;
    }
    VesselRuptureAnalyzer.VesselRuptureResult result = analyzer.analyze(time, pressure, metalT);
    assertFalse(result.ruptured, "Mild duty should not rupture");
    assertTrue(result.minMarginPa > 0.0, "Margin should remain positive");
  }

  /** Wall stress must rise with internal pressure. */
  @Test
  public void wallStressIncreasesWithPressure() {
    MaterialStrengthCurve material = MaterialStrengthCurve.carbonSteel("CS", 360.0e6, 460.0e6);
    VesselRuptureAnalyzer analyzer = new VesselRuptureAnalyzer(0.5, 0.01, material);
    double low = analyzer.wallStressPa(20.0e5);
    double high = analyzer.wallStressPa(60.0e5);
    assertTrue(high > low);
    assertTrue(low > 0.0);
  }

  /** Allowable stress must fall as metal temperature rises. */
  @Test
  public void allowableStressFallsWithTemperature() {
    MaterialStrengthCurve material = MaterialStrengthCurve.carbonSteel("CS", 360.0e6, 460.0e6);
    VesselRuptureAnalyzer analyzer = new VesselRuptureAnalyzer(0.5, 0.01, material);
    double cold = analyzer.allowableStressPa(300.0);
    double hot = analyzer.allowableStressPa(900.0);
    assertTrue(cold > hot, "Hot allowable stress should be lower than cold");
  }

  /** The tensile-strength factor must be constrained to the (0, 1] interval. */
  @Test
  public void rejectsInvalidTensileFactor() {
    MaterialStrengthCurve material = MaterialStrengthCurve.carbonSteel("CS", 360.0e6, 460.0e6);
    VesselRuptureAnalyzer analyzer = new VesselRuptureAnalyzer(0.5, 0.01, material);
    assertThrows(IllegalArgumentException.class, () -> analyzer.setTensileStrengthFactor(0.0));
    assertThrows(IllegalArgumentException.class, () -> analyzer.setTensileStrengthFactor(1.5));
  }

  /**
   * Reproduces the LPG fire-protection application of Andreasen (2026), J. Loss Prev. Process Ind. 103, 106088,
   * &sect;4.4: a bare fire-exposed LPG vessel reaches the rupture limit (von Mises wall stress overtakes the
   * temperature-derated allowable stress) within minutes, whereas thermal insulation that keeps the wall metal cool
   * prevents rupture.
   */
  @Nested
  public class LpgFireProtection {
    /**
     * A bare LPG vessel with a hot unwetted wall and rising pressure must rupture inside the fire-exposure window.
     */
    @Test
    public void bareVesselRupturesInFire() {
      MaterialStrengthCurve steel = MaterialStrengthCurve.carbonSteel("CS", 245.0e6, 415.0e6);
      VesselRuptureAnalyzer analyzer = new VesselRuptureAnalyzer(0.5, 0.012, steel);
      int n = 21;
      double[] time = new double[n];
      double[] pressure = new double[n];
      double[] metalT = new double[n];
      for (int i = 0; i < n; i++) {
        double f = i / (double) (n - 1);
        time[i] = i * 30.0;
        pressure[i] = (18.0 + 8.0 * f) * 1.0e5;
        metalT[i] = 300.0 + 600.0 * f;
      }
      VesselRuptureAnalyzer.VesselRuptureResult result = analyzer.analyze(time, pressure, metalT);
      assertTrue(result.ruptured, "a bare fire-exposed LPG vessel must rupture");
      assertTrue(result.ruptureTimeS > 0.0 && result.ruptureTimeS <= time[n - 1],
          "rupture must occur inside the fire window, t = " + result.ruptureTimeS + " s");
    }

    /** An insulated LPG vessel whose wall metal stays cool must survive the same fire exposure. */
    @Test
    public void insulatedVesselSurvivesFire() {
      MaterialStrengthCurve steel = MaterialStrengthCurve.carbonSteel("CS", 245.0e6, 415.0e6);
      VesselRuptureAnalyzer analyzer = new VesselRuptureAnalyzer(0.5, 0.012, steel);
      int n = 21;
      double[] time = new double[n];
      double[] pressure = new double[n];
      double[] metalT = new double[n];
      for (int i = 0; i < n; i++) {
        double f = i / (double) (n - 1);
        time[i] = i * 30.0;
        pressure[i] = (18.0 + 5.0 * f) * 1.0e5;
        metalT[i] = 300.0 + 110.0 * f;
      }
      VesselRuptureAnalyzer.VesselRuptureResult result = analyzer.analyze(time, pressure, metalT);
      assertFalse(result.ruptured, "insulation that holds the wall cool must prevent rupture");
      assertTrue(result.minMarginPa > 0.0, "a protected vessel must keep a positive stress margin");
    }
  }
}
