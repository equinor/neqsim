package neqsim.process.diagnostics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for {@link ValveSeatLeakageAssessment}.
 *
 * @author ESOL
 * @version 1.0
 */
public class ValveSeatLeakageAssessmentTest {
  /** Universal gas constant, J/(mol K). */
  private static final double R = 8.314462618;

  /** Test gas shared by the cases. */
  private SystemInterface gas;

  /** Builds a lean associated gas for each test. */
  @BeforeEach
  public void setUp() {
    gas = new SystemSrkEos(313.15, 10.0);
    gas.addComponent("methane", 0.90);
    gas.addComponent("ethane", 0.06);
    gas.addComponent("propane", 0.03);
    gas.addComponent("nitrogen", 0.01);
    gas.setMixingRule("classic");
    // getMolarMass() returns 0 until the system is initialised.
    gas.init(0);
  }

  /**
   * The build-up mode must reproduce the ideal-gas accumulation dm/dt = V M/(R T) dP/dt, because the cavity is near
   * atmospheric where Z is close to unity.
   */
  @Test
  public void buildUpModeMatchesIdealGasAccumulation() {
    double volume = 0.0265;
    double riseBar = 4.2;
    double durationS = 60.0;
    double tempK = 313.15;

    ValveSeatLeakageAssessment assessment = new ValveSeatLeakageAssessment("WB-20-0049", gas).setTemperature(tempK, "K")
        .setUpstreamPressure(60.0, "bara").setPressureBuildUpTest(volume, riseBar, durationS);
    ValveSeatLeakageAssessment.Result result = assessment.calculate();

    double molarMassKgPerMol = gas.getMolarMass("gr/mol") / 1000.0;
    double expected = volume * molarMassKgPerMol / (R * tempK) * (riseBar * 1.0e5 / durationS);

    assertEquals(expected, result.getLeakRateKgPerSec(), 0.03 * expected,
        "real-gas accumulation must stay within 3% of the ideal-gas form near 1 bar");
    assertTrue(result.getLeakRateSm3PerHour() > 0.0);
    assertEquals(ValveSeatLeakageAssessment.TestMode.PRESSURE_BUILD_UP, result.getTestMode());
  }

  /** A standing 0.3 barg at an open bleed is well above the critical ratio, so it is subsonic. */
  @Test
  public void standingBleedAtLowPressureIsSubsonic() {
    ValveSeatLeakageAssessment.Result result = new ValveSeatLeakageAssessment("WB-20-0033", gas)
        .setTemperature(40.0, "C").setUpstreamPressure(60.0, "bara").setStandingBleedTest(0.3, 0.006).calculate();

    assertFalse(result.isBleedChoked(), "0.3 barg to atmosphere is far above the critical ratio");
    assertTrue(result.getLeakRateKgPerSec() > 0.0);
    assertEquals(ValveSeatLeakageAssessment.TestMode.STANDING_BLEED, result.getTestMode());
  }

  /** The same leak needs a smaller gap when the driving pressure is higher. */
  @Test
  public void equivalentGapShrinksWithUpstreamPressure() {
    double lowPressureGap = gapAt(50.0);
    double highPressureGap = gapAt(345.0);

    assertTrue(highPressureGap < lowPressureGap,
        "a higher driving pressure passes the same mass through a smaller gap");
    assertTrue(highPressureGap > 0.0);
  }

  /** A sub-millimetre gap must screen as weepage, and a wide-open seat as a gross leak. */
  @Test
  public void severityBandsSeparateWeepageFromGrossLeak() {
    ValveSeatLeakageAssessment.Result weepage = new ValveSeatLeakageAssessment("weepage", gas).setTemperature(40.0, "C")
        .setUpstreamPressure(300.0, "bara").setPressureBuildUpTest(0.0265, 4.2, 60.0).calculate();
    assertEquals(ValveSeatLeakageAssessment.Severity.SEAT_WEEPAGE, weepage.getSeverity());

    ValveSeatLeakageAssessment.Result gross = new ValveSeatLeakageAssessment("gross", gas).setTemperature(40.0, "C")
        .setUpstreamPressure(300.0, "bara").setPressureBuildUpTest(0.0265, 4.2, 0.05).calculate();
    assertEquals(ValveSeatLeakageAssessment.Severity.GROSS_LEAK, gross.getSeverity());
  }

  /** Any measurable leak disqualifies the valve as a positive-isolation barrier. */
  @Test
  public void measuredLeakNeverCreditsPositiveIsolation() {
    ValveSeatLeakageAssessment.Result result = new ValveSeatLeakageAssessment("NP-23-0060", gas)
        .setTemperature(40.0, "C").setUpstreamPressure(325.0, "bara").setPressureBuildUpTest(0.0133, 0.1, 600.0)
        .calculate();

    assertFalse(result.providesPositiveIsolation());
    assertTrue(result.toJson().contains("equivalentGapDiameterMm"));
  }

  /** Misconfiguration must fail loudly rather than return a fabricated number. */
  @Test
  public void invalidConfigurationIsRejected() {
    assertThrows(IllegalStateException.class,
        () -> new ValveSeatLeakageAssessment("x", gas).setUpstreamPressure(50.0, "bara").calculate());
    assertThrows(IllegalStateException.class,
        () -> new ValveSeatLeakageAssessment("x", gas).setPressureBuildUpTest(0.01, 1.0, 60.0).calculate());
    assertThrows(IllegalArgumentException.class,
        () -> new ValveSeatLeakageAssessment("x", gas).setPressureBuildUpTest(-1.0, 1.0, 60.0));
    assertThrows(IllegalArgumentException.class,
        () -> new ValveSeatLeakageAssessment("x", gas).setDischargeCoefficient(1.5));
    assertThrows(IllegalArgumentException.class,
        () -> new ValveSeatLeakageAssessment("x", gas).setTemperature(40.0, "F"));
  }

  /**
   * Evaluates the equivalent gap diameter at a given upstream pressure.
   *
   * @param upstreamBara the upstream pressure, in bara
   * @return the equivalent gap diameter, in mm
   */
  private double gapAt(double upstreamBara) {
    return new ValveSeatLeakageAssessment("gap", gas).setTemperature(40.0, "C")
        .setUpstreamPressure(upstreamBara, "bara").setPressureBuildUpTest(0.0265, 4.2, 60.0).calculate()
        .getEquivalentGapDiameterMm();
  }
}
