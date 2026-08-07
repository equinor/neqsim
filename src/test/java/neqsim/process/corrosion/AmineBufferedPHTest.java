package neqsim.process.corrosion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link AmineBufferedPH} and {@link BufferAmine}.
 *
 * @author ESOL
 * @version 1.0
 */
public class AmineBufferedPHTest {

  /**
   * The amine correlations must reproduce the accepted literature pKa values at 25 C. This is what validates the
   * concentration-basis offsets applied to the reaction-database coefficients.
   */
  @Test
  void amineCorrelationsMatchLiteraturePKaAt25C() {
    assertEquals(8.92, BufferAmine.DEA.getPKa(298.15), 0.05, "DEA pKa at 25 C");
    assertEquals(8.52, BufferAmine.MDEA.getPKa(298.15), 0.05, "MDEA pKa at 25 C");
  }

  /**
   * The water ion-product correlation must reproduce the accepted neutral pH at low and high temperature. The
   * high-temperature value is the reason a hot-system pH cannot be judged against pH 7.
   */
  @Test
  void neutralPHFollowsTheWaterIonProduct() {
    assertEquals(7.00, AmineBufferedPH.neutralPH(25.0), 0.02, "neutral pH at 25 C");
    assertEquals(6.13, AmineBufferedPH.neutralPH(100.0), 0.05, "neutral pH at 100 C");
    assertEquals(5.85, AmineBufferedPH.neutralPH(150.0), 0.05, "neutral pH at 150 C");
    assertTrue(AmineBufferedPH.neutralPH(150.0) < AmineBufferedPH.neutralPH(25.0),
        "water becomes neutral at a lower pH as it gets hotter");
  }

  /**
   * Amine pKa must fall monotonically with temperature.
   */
  @Test
  void pKaFallsWithTemperature() {
    for (int i = 0; i < BufferAmine.values().length; i++) {
      BufferAmine amine = BufferAmine.values()[i];
      double cold = amine.getPKa(298.15);
      double warm = amine.getPKa(373.15);
      double hot = amine.getPKa(423.15);
      assertTrue(warm < cold, amine + " pKa must fall from 25 to 100 C");
      assertTrue(hot < warm, amine + " pKa must fall from 100 to 150 C");
    }
  }

  /**
   * For a buffered fluid the pH shift must equal the pKa shift exactly, which is the Henderson-Hasselbalch result for a
   * base-to-acid ratio fixed by mass balance.
   */
  @Test
  void pHShiftEqualsPKaShift() {
    AmineBufferedPHResult r = new AmineBufferedPH().setAmine(BufferAmine.DEA).setMeasuredPH(8.7, 20.0)
        .setOperatingTemperature(150.0).calculate();

    double expectedShift = BufferAmine.DEA.getPKa(423.15) - BufferAmine.DEA.getPKa(293.15);
    assertEquals(expectedShift, r.getPHShift(), 1.0e-9);
    assertEquals(8.7 + expectedShift, r.getOperatingPH(), 1.0e-9);
    assertTrue(r.getPHShift() < 0.0, "heating a buffered amine fluid must lower its pH");
  }

  /**
   * A fluid that looks comfortably alkaline in the laboratory can retain very little alkaline margin at the hot
   * surface. This is the central result of the calculation.
   */
  @Test
  void laboratoryPHOverstatesTheMarginAtTemperature() {
    AmineBufferedPHResult r = new AmineBufferedPH().setAmine(BufferAmine.DEA).setMeasuredPH(8.7, 20.0)
        .setOperatingTemperature(150.0).calculate();

    assertTrue(r.getMarginAtMeasurement() > 1.5,
        "pH 8.7 at 20 C looks comfortably alkaline, margin was " + r.getMarginAtMeasurement());
    assertTrue(r.getMarginAtOperating() < 1.0,
        "the same fluid must retain little margin at 150 C, margin was " + r.getMarginAtOperating());
    assertTrue(r.getMarginLoss() > 1.0, "more than a full pH unit of margin must be lost on heating");
    assertEquals(AlkalineMarginVerdict.INSUFFICIENT, r.getVerdict());
  }

  /**
   * Raising the control target from the lower end of a 9-11 band to 10 must materially restore the margin at
   * temperature. This quantifies why a stricter pH target is worth the dosing effort.
   */
  @Test
  void raisingTheControlTargetRestoresMarginAtTemperature() {
    AmineBufferedPH calc = new AmineBufferedPH().setAmine(BufferAmine.DEA).setOperatingTemperature(150.0);

    double atLowPH = calc.setMeasuredPH(8.7, 20.0).calculate().getMarginAtOperating();
    double atTarget = calc.setMeasuredPH(10.0, 20.0).calculate().getMarginAtOperating();

    assertEquals(1.3, atTarget - atLowPH, 1.0e-6, "the margin gain must equal the control-target increase");
    assertTrue(atTarget > 1.5, "a pH 10 target must restore an adequate margin, was " + atTarget);
    assertTrue(atLowPH < 1.0, "a pH 8.7 fluid must screen as short of margin, was " + atLowPH);
  }

  /**
   * The margin at operating temperature must drive the verdict bands.
   */
  @Test
  void verdictBandsFollowTheOperatingMargin() {
    assertEquals(AlkalineMarginVerdict.ROBUST, AmineBufferedPH.classify(2.4));
    assertEquals(AlkalineMarginVerdict.ADEQUATE, AmineBufferedPH.classify(1.7));
    assertEquals(AlkalineMarginVerdict.MARGINAL, AmineBufferedPH.classify(1.2));
    assertEquals(AlkalineMarginVerdict.INSUFFICIENT, AmineBufferedPH.classify(0.4));
  }

  /**
   * A cooler operating temperature must retain more margin than a hotter one for the same laboratory pH.
   */
  @Test
  void coolerServiceRetainsMoreMargin() {
    AmineBufferedPH calc = new AmineBufferedPH().setAmine(BufferAmine.DEA).setMeasuredPH(9.0, 20.0);
    double warm = calc.setOperatingTemperature(100.0).calculate().getMarginAtOperating();
    double hot = calc.setOperatingTemperature(150.0).calculate().getMarginAtOperating();
    assertTrue(warm > hot, "a cooler surface must retain more alkaline margin");
  }

  /**
   * A declared glycol content and an extrapolated temperature must both raise warnings, and the ideal-solution basis
   * must always be disclosed.
   */
  @Test
  void limitationsAreAlwaysDisclosed() {
    AmineBufferedPHResult plain = new AmineBufferedPH().setMeasuredPH(9.0, 20.0).setOperatingTemperature(150.0)
        .calculate();
    boolean basisNoted = false;
    for (int i = 0; i < plain.getWarnings().size(); i++) {
      if (plain.getWarnings().get(i).contains("activity coefficients")) {
        basisNoted = true;
      }
    }
    assertTrue(basisNoted, "the ideal-solution basis must always be disclosed");

    AmineBufferedPHResult glycol = new AmineBufferedPH().setMeasuredPH(9.0, 20.0).setOperatingTemperature(170.0)
        .setGlycolMassFraction(0.45).calculate();
    boolean glycolNoted = false;
    boolean rangeNoted = false;
    for (int i = 0; i < glycol.getWarnings().size(); i++) {
      String w = glycol.getWarnings().get(i);
      if (w.contains("dielectric")) {
        glycolNoted = true;
      }
      if (w.contains("above the range")) {
        rangeNoted = true;
      }
    }
    assertTrue(glycolNoted, "an unmodelled glycol co-solvent effect must be flagged");
    assertTrue(rangeNoted, "extrapolation beyond the correlation range must be flagged");
  }

  /**
   * Missing or invalid inputs must be rejected.
   */
  @Test
  void invalidInputsAreRejected() {
    assertThrows(IllegalStateException.class, () -> new AmineBufferedPH().setOperatingTemperature(150.0).calculate(),
        "a missing measured pH must fail");
    assertThrows(IllegalStateException.class, () -> new AmineBufferedPH().setMeasuredPH(9.0, 20.0).calculate(),
        "a missing operating temperature must fail");
    assertThrows(IllegalArgumentException.class, () -> new AmineBufferedPH().setMeasuredPH(15.0, 20.0),
        "an out-of-range pH must be rejected");
    assertThrows(IllegalArgumentException.class, () -> new AmineBufferedPH().setGlycolMassFraction(1.5),
        "an out-of-range glycol fraction must be rejected");
    assertThrows(IllegalArgumentException.class, () -> new AmineBufferedPH().setAmine(null),
        "a null amine must be rejected");
  }

  /**
   * The result must serialise to JSON for reporting.
   */
  @Test
  void resultSerialisesToJson() {
    String json = new AmineBufferedPH().setAmine(BufferAmine.DEA).setMeasuredPH(8.7, 20.0)
        .setOperatingTemperature(150.0).calculate().toJson();
    assertTrue(json.contains("marginAtOperating"), "JSON must contain the operating-temperature margin");
    assertTrue(json.contains("operatingPH"), "JSON must contain the in-situ pH");
  }
}
