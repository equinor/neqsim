package neqsim.process.fielddevelopment.wellplanning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import neqsim.process.fielddevelopment.wellplanning.TubingMovementCalculator.PackerMode;
import neqsim.process.fielddevelopment.wellplanning.TubingMovementCalculator.Result;
import neqsim.process.fielddevelopment.wellplanning.TubingMovementCalculator.Triaxial;

/**
 * Closed-form hand-calculation tests of {@link TubingMovementCalculator}.
 *
 * @author NeqSim
 * @version 1.0
 */
public class TubingMovementCalculatorTest {
  private static final double OD = 0.1143;
  private static final double ID = 0.0985;
  private static final double DP_BORE = 0.12;
  private static final double AS = Math.PI / 4.0 * (OD * OD - ID * ID);
  private static final double AI = Math.PI / 4.0 * ID * ID;
  private static final double AP = Math.PI / 4.0 * DP_BORE * DP_BORE;
  private static final double E = 207.0e9;

  /**
   * Creates the reference calculator.
   *
   * @return calculator with 3000 m of 4.5 in tubing
   */
  private static TubingMovementCalculator base() {
    return new TubingMovementCalculator().setLength(3000.0).setGeometry(OD, ID, DP_BORE, 0.1594).setSteel(207.0, 0.3,
        1.2e-5);
  }

  /**
   * Temperature only: free elongation alpha L dT and fixed-end force -E A alpha dT.
   */
  @Test
  public void temperatureOnlyFreeAndFixed() {
    Result free = base().setLoadChange(0, 0, 0, 0, 50.0).calculate();
    assertEquals(1.2e-5 * 3000.0 * 50.0, free.getFreeDeltaLM(), 1.0e-12);
    assertEquals(0.0, free.getAxialForceChangeN(), 0.0);
    Result fixed = base().setPackerMode(PackerMode.FIXED, 0.0).setLoadChange(0, 0, 0, 0, 50.0).calculate();
    assertEquals(-E * AS * 1.2e-5 * 50.0, fixed.getAxialForceChangeN(), 1.0e-6 * E * AS * 1.2e-5 * 50.0);
    assertEquals(0.0, fixed.getMovementAtPackerM(), 0.0);
    assertEquals(fixed.getAxialForceChangeN() / AS, fixed.getAxialStressChangePa(), 1.0e-3);
  }

  /**
   * Piston effect: dL1 = -L ((Ap - Ai) dPi) / (E As) with the seal bore larger than the tubing bore.
   */
  @Test
  public void pistonEffectMatchesHandCalculation() {
    Result r = base().setLoadChange(100.0, 0.0, 0.0, 0.0, 0.0).calculate();
    double f = (AP - AI) * 100.0e5;
    assertEquals(f, r.getPistonForceN(), 1.0e-6);
    assertEquals(-3000.0 * f / (E * AS), r.getPistonDeltaLM(), 1.0e-12);
    assertTrue(r.getPistonDeltaLM() < 0.0);
  }

  /**
   * Ballooning: internal pressure shortens the string, annulus pressure lengthens it.
   */
  @Test
  public void balloonEffectMatchesHandCalculation() {
    double r2 = OD * OD / (ID * ID);
    Result r = base().setLoadChange(0.0, 0.0, 100.0, 0.0, 0.0).calculate();
    assertEquals(-2.0 * 0.3 * 3000.0 * 100.0e5 / (E * (r2 - 1.0)), r.getBalloonDeltaLM(), 1.0e-12);
    Result o = base().setLoadChange(0.0, 0.0, 0.0, 100.0, 0.0).calculate();
    assertEquals(2.0 * 0.3 * 3000.0 * r2 * 100.0e5 / (E * (r2 - 1.0)), o.getBalloonDeltaLM(), 1.0e-12);
  }

  /**
   * The free length change is the sum of the effects and a fixed packer force is consistent with it.
   */
  @Test
  public void combinedLoadSumsAndRestrains() {
    TubingMovementCalculator c = base().setLoadChange(150.0, 20.0, 120.0, 20.0, 40.0);
    Result free = c.calculate();
    assertEquals(free.getPistonDeltaLM() + free.getBalloonDeltaLM() + free.getTemperatureDeltaLM(),
        free.getFreeDeltaLM(), 1.0e-12);
    Result fixed = c.setPackerMode(PackerMode.FIXED, 0.0).calculate();
    assertEquals(-E * AS * free.getFreeDeltaLM() / 3000.0, fixed.getAxialForceChangeN(),
        1.0e-9 * Math.abs(fixed.getAxialForceChangeN()));
  }

  /**
   * Limited stroke restrains only the movement beyond the stroke.
   */
  @Test
  public void limitedStrokeRestrainsExcessOnly() {
    TubingMovementCalculator c = base().setLoadChange(0, 0, 0, 0, 50.0);
    Result within = c.setPackerMode(PackerMode.LIMITED_STROKE, 2.5).calculate();
    assertEquals(0.0, within.getAxialForceChangeN(), 0.0);
    assertEquals(1.8, within.getMovementAtPackerM(), 1.0e-12);
    Result beyond = c.setPackerMode(PackerMode.LIMITED_STROKE, 1.0).calculate();
    assertEquals(-E * AS * (1.8 - 1.0) / 3000.0, beyond.getAxialForceChangeN(),
        1.0e-9 * Math.abs(beyond.getAxialForceChangeN()));
    assertEquals(1.0, beyond.getMovementAtPackerM(), 1.0e-12);
  }

  /**
   * Buckling thresholds follow the Dawson-Paslay relation and helical buckling shortens the string by r^2 F^2 / (8 E I
   * w).
   */
  @Test
  public void bucklingThresholdsAndShortening() {
    TubingMovementCalculator c = base().setFluidDensities(800.0, 1000.0).setLoadChange(0, 0, 0, 0, 0.0);
    Result none = c.calculate();
    assertFalse(none.isSinusoidalBuckled());
    double fs = none.getSinusoidalBucklingForceN();
    double fh = none.getHelicalBucklingForceN();
    assertEquals(2.0 * Math.sqrt(2.0), fh / fs, 1.0e-12);
    double inertia = Math.PI / 64.0 * (Math.pow(OD, 4.0) - Math.pow(ID, 4.0));
    double r = 0.5 * (0.1594 - OD);
    assertEquals(2.0 * Math.sqrt(E * inertia * none.getEffectiveWeightNPerM() / r), fs, 1.0e-9 * fs);
    Result sin = c.setSetDownForce(1.2 * fs).calculate();
    assertTrue(sin.isSinusoidalBuckled());
    assertFalse(sin.isHelicalBuckled());
    assertEquals(0.0, sin.getBucklingDeltaLM(), 0.0);
    Result hel = c.setSetDownForce(1.5 * fh).calculate();
    assertTrue(hel.isHelicalBuckled());
    double expected = -r * r * Math.pow(1.5 * fh, 2.0) / (8.0 * E * inertia * hel.getEffectiveWeightNPerM());
    assertEquals(expected, hel.getBucklingDeltaLM(), 1.0e-9 * Math.abs(expected));
    assertTrue(hel.getFreeDeltaLM() < 0.0);
  }

  /**
   * Triaxial check: uniaxial tension, Lame hoop stress and the von Mises design factor.
   */
  @Test
  public void vonMisesMatchesClosedForm() {
    TubingMovementCalculator c = base();
    Triaxial uni = c.vonMises(0.0, 0.0, 500.0e3, 552.0);
    assertEquals(500.0e3 / AS, uni.getVonMisesPa(), 1.0e-6 * uni.getVonMisesPa());
    assertEquals(552.0e6 / (500.0e3 / AS), uni.getDesignFactor(), 1.0e-6 * uni.getDesignFactor());
    Triaxial p = c.vonMises(400.0, 100.0, 0.0, 552.0);
    double a = ID / 2.0;
    double b = OD / 2.0;
    double hoop = (400.0e5 * (b * b + a * a) - 2.0 * 100.0e5 * b * b) / (b * b - a * a);
    assertEquals(hoop, p.getHoopStressPa(), 1.0e-9 * hoop);
    assertEquals(-400.0e5, p.getRadialStressPa(), 1.0e-6);
    double axial = (400.0e5 * AI - 100.0e5 * Math.PI * b * b) / AS;
    assertEquals(axial, p.getAxialStressPa(), 1.0e-6 * Math.abs(axial));
    double vm = Math
        .sqrt(axial * axial + hoop * hoop + 400.0e5 * 400.0e5 - axial * hoop + hoop * 400.0e5 + 400.0e5 * axial);
    assertEquals(vm, p.getVonMisesPa(), 1.0e-9 * vm);
  }
}
