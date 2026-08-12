package neqsim.process.corrosion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Tests of the alkaline-reserve (buffer capacity) calculation on {@link AmineBufferedPH}.
 *
 * <p>
 * The reference case is a DEA-buffered WHRU heating-medium loop controlled on a laboratory pH of 8.7 measured at 20
 * &deg;C and running against a 150 &deg;C wall, with 310 mg/L of organic acids reported and a formic/acetic average
 * molar mass of 52.03 g/mol.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class AlkalineReserveTest {
  private static final double ACID_MG_PER_L = 310.0;
  private static final double ACID_MOLAR_MASS = 52.03;

  /**
   * Build the reference WHRU heating-medium case.
   *
   * @return a configured calculator
   */
  private AmineBufferedPH referenceCase() {
    AmineBufferedPH calc = new AmineBufferedPH();
    calc.setAmine(BufferAmine.DEA);
    calc.setMeasuredPH(8.7, 20.0);
    calc.setOperatingTemperature(150.0);
    return calc;
  }

  /** The Henderson-Hasselbalch free-base fraction must be 0.5 at the pKa and monotonic in pH. */
  @Test
  public void testFreeBaseFraction() {
    assertEquals(0.5, AmineBufferedPH.freeBaseFraction(9.0, 9.0), 1e-12);
    assertTrue(AmineBufferedPH.freeBaseFraction(10.0, 9.0) > 0.9);
    assertTrue(AmineBufferedPH.freeBaseFraction(8.0, 9.0) < 0.1);
    assertTrue(AmineBufferedPH.freeBaseFraction(9.5, 9.0) > AmineBufferedPH.freeBaseFraction(9.0, 9.0));
  }

  /** The zero-margin pH must reproduce a zero alkaline margin at the operating temperature. */
  @Test
  public void testZeroMarginPHIsSelfConsistent() {
    AmineBufferedPH calc = referenceCase();
    double phZero = calc.findMeasuredPHAtZeroMargin();

    assertTrue(phZero > 7.0, "the control floor must sit above the ambient neutral point");
    assertTrue(phZero < 8.7, "the as-found fluid must still have a positive margin");

    AmineBufferedPH check = referenceCase();
    check.setMeasuredPH(phZero, 20.0);
    assertEquals(0.0, check.calculate().getMarginAtOperating(), 1e-9);
  }

  /** The spent fraction and the remaining capacity must reproduce the reference case. */
  @Test
  public void testReserveOnReferenceCase() {
    AlkalineReserveResult r = referenceCase().calculateAlkalineReserve(ACID_MG_PER_L, ACID_MOLAR_MASS);

    assertEquals(BufferAmine.DEA, r.getAmine());
    assertEquals(8.7, r.getMeasuredPH(), 1e-12);
    assertEquals(150.0, r.getOperatingTemperatureC(), 1e-12);

    assertTrue(r.getFreeBaseFractionAsFound() > r.getFreeBaseFractionAtZeroMargin(),
        "the as-found fluid must hold more free base than the end point");
    assertTrue(r.getReserveSpentFraction() > 0.0 && r.getReserveSpentFraction() < 1.0);
    assertEquals(1.0, r.getReserveSpentFraction() + r.getReserveRemainingFraction(), 1e-12);

    assertTrue(r.getRemainingAcidCapacityMgPerL() > 0.0);
    assertTrue(r.getRemainingAcidCapacityMgPerL() < ACID_MG_PER_L,
        "a mostly spent buffer must have less capacity left than it has already absorbed");

    // The molar capacity must be consistent with the mass capacity through the acid molar mass.
    assertEquals(r.getRemainingAcidCapacityMgPerL() / ACID_MOLAR_MASS, r.getRemainingAcidCapacityMmolPerL(), 1e-6);

    assertTrue(r.getDerivedAmineInventoryMmolPerL() > 0.0);
    assertTrue(r.getWarnings().size() > 0, "the ideal-solution basis must be reported");
    assertTrue(r.toJson().contains("reserveSpentFraction"));
  }

  /** The spent fraction must not depend on the acid load, since the amine concentration cancels out. */
  @Test
  public void testSpentFractionIsIndependentOfAcidLoad() {
    AlkalineReserveResult withAcid = referenceCase().calculateAlkalineReserve(ACID_MG_PER_L, ACID_MOLAR_MASS);
    AlkalineReserveResult withoutAcid = referenceCase().calculateAlkalineReserve();

    assertEquals(withAcid.getReserveSpentFraction(), withoutAcid.getReserveSpentFraction(), 1e-12);
    assertEquals(withAcid.getMeasuredPHAtZeroMargin(), withoutAcid.getMeasuredPHAtZeroMargin(), 1e-12);
    assertTrue(Double.isNaN(withoutAcid.getRemainingAcidCapacityMgPerL()));
  }

  /** A fluid dosed further above the end point must have more reserve left. */
  @Test
  public void testHigherPHLeavesMoreReserve() {
    AmineBufferedPH low = referenceCase();
    AmineBufferedPH high = referenceCase();
    high.setMeasuredPH(9.5, 20.0);

    assertTrue(high.calculateAlkalineReserve().getReserveSpentFraction() < low.calculateAlkalineReserve()
        .getReserveSpentFraction());
  }

  /** Missing or non-physical inputs must fail loudly rather than return a poison value. */
  @Test
  public void testInvalidInputs() {
    AmineBufferedPH noOperating = new AmineBufferedPH();
    noOperating.setMeasuredPH(8.7, 20.0);
    assertThrows(IllegalStateException.class, () -> noOperating.findMeasuredPHAtZeroMargin());

    AmineBufferedPH noPH = new AmineBufferedPH();
    noPH.setOperatingTemperature(150.0);
    assertThrows(IllegalStateException.class, () -> noPH.calculateAlkalineReserve());

    AmineBufferedPH ok = referenceCase();
    assertThrows(IllegalArgumentException.class, () -> ok.calculateAlkalineReserve(-1.0, ACID_MOLAR_MASS));
    assertThrows(IllegalArgumentException.class, () -> ok.calculateAlkalineReserve(ACID_MG_PER_L, 0.0));
  }
}
