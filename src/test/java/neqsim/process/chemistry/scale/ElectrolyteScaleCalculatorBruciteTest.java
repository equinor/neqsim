package neqsim.process.chemistry.scale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Tests the brucite Mg(OH)2 saturation index used to screen cathodic scaling in seawater electrolysis
 * (electrochlorination) cells.
 *
 * @author NeqSim
 * @version 1.0
 */
public class ElectrolyteScaleCalculatorBruciteTest {
  /** North Sea / Norwegian Sea surface seawater at S = 35 psu (mg/L). */
  private static ElectrolyteScaleCalculator seawater(double tC, double pH) {
    return new ElectrolyteScaleCalculator().setTemperatureCelsius(tC).setPressureBara(1.013).setPH(pH)
        .setCations(412.0, 0.02, 7.9, 1290.0, 10780.0, 399.0, 0.002).setAnions(19350.0, 2710.0, 142.0, 0.0);
  }

  @Test
  void bulkSeawaterIsUndersaturatedInBrucite() {
    ElectrolyteScaleCalculator calc = seawater(10.0, 8.1).calculate();
    double si = calc.getBruciteSaturationIndex();
    assertTrue(si < 0.0, "bulk seawater at pH 8.1 must be undersaturated in brucite, SI = " + si);
    assertTrue(si < -3.0, "bulk seawater brucite SI should be well below zero, SI = " + si);
  }

  @Test
  void cathodeBoundaryLayerIsSupersaturatedInBrucite() {
    ElectrolyteScaleCalculator calc = seawater(10.0, 10.5).calculate();
    assertTrue(calc.getBruciteSaturationIndex() > 0.0,
        "cathodic boundary layer at pH 10.5 must be supersaturated in brucite");
  }

  @Test
  void bruciteIndexRisesTwoDecadesPerPhUnit() {
    double siLow = seawater(10.0, 9.0).calculate().getBruciteSaturationIndex();
    double siHigh = seawater(10.0, 10.0).calculate().getBruciteSaturationIndex();
    assertEquals(2.0, siHigh - siLow, 0.05, "Mg(OH)2 SI must rise by 2 log units per pH unit (two hydroxide ions)");
  }

  @Test
  void hydroxideActivityMatchesWaterIonProduct() {
    ElectrolyteScaleCalculator calc = seawater(25.0, 14.0).calculate();
    assertEquals(1.0, calc.getHydroxideActivity(), 0.05,
        "a(OH-) at pH 14 and 25 C must be 1.0 because pKw(25 C) = 14.0");
  }

  @Test
  void bruciteSolubilityIsRetrograde() {
    double siCold = seawater(5.0, 10.0).calculate().getBruciteSaturationIndex();
    double siWarm = seawater(40.0, 10.0).calculate().getBruciteSaturationIndex();
    assertTrue(siWarm > siCold,
        "brucite solubility is retrograde: SI must increase with temperature (" + siCold + " -> " + siWarm + ")");
  }

  @Test
  void bruciteIndexIsReportedInTheMap() {
    ElectrolyteScaleCalculator calc = seawater(10.0, 10.5).calculate();
    assertTrue(calc.toMap().containsKey("bruciteSaturationIndex"));
    assertTrue(calc.toJson().contains("bruciteSaturationIndex"));
  }
}
