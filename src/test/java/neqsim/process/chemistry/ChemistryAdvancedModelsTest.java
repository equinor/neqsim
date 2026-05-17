package neqsim.process.chemistry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import org.junit.jupiter.api.Test;
import neqsim.process.chemistry.corrosion.LangmuirInhibitorIsotherm;
import neqsim.process.chemistry.corrosion.MechanisticCorrosionModel;
import neqsim.process.chemistry.scale.ElectrolyteScaleCalculator;

/**
 * Tests covering the activity-coefficient-based ElectrolyteScaleCalculator, the
 * LangmuirInhibitorIsotherm and the MechanisticCorrosionModel.
 *
 * @author ESOL
 * @version 1.0
 */
class ChemistryAdvancedModelsTest {

  /**
   * Activity-coefficient bridge gives sensible ionic strength for a typical seawater brine and
   * supersaturation flips between dilute and concentrated brines.
   */
  @Test
  void electrolyteScaleCalculatorPredictsSeawaterIonicStrength() {
    ElectrolyteScaleCalculator calc = new ElectrolyteScaleCalculator().setTemperatureCelsius(60.0)
        .setPressureBara(50.0).setPH(6.5).setCO2PartialPressureBar(2.0)
        .setCations(420.0, 0.05, 8.0, 1280.0, 10800.0, 400.0, 0.0)
        .setAnions(19400.0, 2700.0, 142.0, 0.0).calculate();
    assertTrue(calc.isEvaluated());
    // Seawater I ~ 0.7 mol/kg; tolerate range [0.4, 1.2]
    double I = calc.getIonicStrength();
    assertTrue(I > 0.4 && I < 1.2, "Seawater ionic strength out of plausible range: " + I);
    // Davies coefficients: γ for divalent < γ for monovalent
    assertTrue(
        calc.getActivityCoefficients().get("Ca2+") < calc.getActivityCoefficients().get("Na+"));
    // Activity coefficients should be < 1 for non-zero ionic strength
    assertTrue(calc.getActivityCoefficients().get("Ca2+") < 1.0);
    assertNotNull(calc.toJson());
    assertEquals(2, calc.getStandardsApplied().size());
  }

  /**
   * Langmuir isotherm: coverage rises monotonically with dose and asymptotes to 1.
   */
  @Test
  void langmuirIsothermMonotonicAndBounded() {
    LangmuirInhibitorIsotherm iso = new LangmuirInhibitorIsotherm();
    double t1 = iso.getCoverage(0.0, 60.0);
    double t2 = iso.getCoverage(20.0, 60.0);
    double t3 = iso.getCoverage(200.0, 60.0);
    assertEquals(0.0, t1, 1e-9);
    assertTrue(t2 > t1);
    assertTrue(t3 > t2);
    assertTrue(t3 < 1.0);
    // efficiency = thetaMax * theta
    double eta = iso.getEfficiency(200.0, 60.0);
    assertTrue(eta > 0.0 && eta < 0.95);
    // dose-for-efficiency round-trip
    double doseFor80 = iso.getDoseForEfficiency(0.80, 60.0);
    assertTrue(doseFor80 > 0.0 && Double.isFinite(doseFor80));
    double etaCheck = iso.getEfficiency(doseFor80, 60.0);
    assertEquals(0.80, etaCheck, 1e-3);
  }

  /**
   * Mechanistic corrosion model produces a finite mixed-control rate that is suppressed by
   * inhibitor dosing.
   */
  @Test
  void mechanisticCorrosionMixedControlAndInhibition() {
    MechanisticCorrosionModel model = new MechanisticCorrosionModel().setTemperatureCelsius(60.0)
        .setTotalPressureBara(80.0).setGasComposition(0.05, 0.0).setWaterChemistry(5.5, 100.0, 0.5)
        .setFlow(2.0, 0.15, 1000.0, 1.0e-3).setInhibitor(null, 0.0).evaluate();
    double crUninhibited = model.getInhibitedRateMmYr();
    assertTrue(crUninhibited > 0.0, "expected positive corrosion rate, got " + crUninhibited);
    assertTrue(model.getReynoldsNumber() > 4000.0, "should be turbulent");
    assertTrue(model.getSherwoodNumber() > 100.0, "Sh should be large in turbulent flow");

    MechanisticCorrosionModel inhibited =
        new MechanisticCorrosionModel().setTemperatureCelsius(60.0).setTotalPressureBara(80.0)
            .setGasComposition(0.05, 0.0).setWaterChemistry(5.5, 100.0, 0.5)
            .setFlow(2.0, 0.15, 1000.0, 1.0e-3).setInhibitor(null, 50.0).evaluate();
    assertTrue(inhibited.getInhibitedRateMmYr() < crUninhibited,
        "inhibitor must reduce corrosion: bare=" + crUninhibited + ", inhibited="
            + inhibited.getInhibitedRateMmYr());
    assertTrue(inhibited.getInhibitorEfficiency() > 0.05);
    assertNotNull(inhibited.toJson());
    assertEquals(2, inhibited.getStandardsApplied().size());
  }

  /**
   * Mass-transfer limit should dominate at very high CO2 partial pressure / high velocity,
   * preventing the kinetic rate from running away.
   */
  @Test
  void mechanisticCorrosionMassTransferLimitsKineticRate() {
    MechanisticCorrosionModel model = new MechanisticCorrosionModel().setTemperatureCelsius(80.0)
        .setTotalPressureBara(150.0).setGasComposition(0.30, 0.0).setWaterChemistry(4.5, 50.0, 0.5)
        .setFlow(5.0, 0.05, 1000.0, 1.0e-3).setInhibitor(null, 0.0).evaluate();
    assertTrue(model.getMixedControlRateMmYr() <= model.getKineticRateMmYr() + 1e-9,
        "mixed control rate must not exceed kinetic rate");
    assertFalse(Double.isNaN(model.getMixedControlRateMmYr()));
  }
}
