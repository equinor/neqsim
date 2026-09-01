package neqsim.process.chemistry.electrochlorination;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Benchmarks the seawater conductivity model against the UNESCO/PSS-78 reference points.
 *
 * @author NeqSim
 * @version 1.0
 */
public class SeawaterElectrolyteConductivityTest {
  @Test
  void standardSeawaterReproducesReferenceConductivity() {
    SeawaterElectrolyteConductivity c = new SeawaterElectrolyteConductivity().setSalinityPsu(35.0)
        .setTemperatureCelsius(15.0).setPressureBara(1.01325).calculate();
    assertEquals(4.2914, c.getConductivitySPerM(), 0.005,
        "C(35,15,0) must equal the UNESCO reference conductivity 4.2914 S/m");
  }

  @Test
  void conductivityAtZeroCelsiusMatchesUnescoTable() {
    SeawaterElectrolyteConductivity c = new SeawaterElectrolyteConductivity().setSalinityPsu(35.0)
        .setTemperatureCelsius(0.0).setPressureBara(1.01325).calculate();
    assertEquals(2.906, c.getConductivitySPerM(), 0.03, "C(35,0,0) is 2.906 S/m (conductivity ratio 0.6772)");
  }

  @Test
  void conductivityAtTwentyFiveCelsiusMatchesUnescoTable() {
    SeawaterElectrolyteConductivity c = new SeawaterElectrolyteConductivity().setSalinityPsu(35.0)
        .setTemperatureCelsius(25.0).setPressureBara(1.01325).calculate();
    assertEquals(5.313, c.getConductivitySPerM(), 0.03, "C(35,25,0) is 5.313 S/m (conductivity ratio 1.2381)");
  }

  @Test
  void conductivityIncreasesWithTemperatureAndSalinity() {
    double cold = new SeawaterElectrolyteConductivity().setTemperatureCelsius(5.0).calculate().getConductivitySPerM();
    double warm = new SeawaterElectrolyteConductivity().setTemperatureCelsius(20.0).calculate().getConductivitySPerM();
    assertTrue(warm > cold);
    double fresh = new SeawaterElectrolyteConductivity().setSalinityPsu(30.0).setTemperatureCelsius(10.0).calculate()
        .getConductivitySPerM();
    double saline = new SeawaterElectrolyteConductivity().setSalinityPsu(35.0).setTemperatureCelsius(10.0).calculate()
        .getConductivitySPerM();
    assertTrue(saline > fresh);
  }

  @Test
  void resistivityIsReciprocalOfConductivity() {
    SeawaterElectrolyteConductivity c = new SeawaterElectrolyteConductivity().setTemperatureCelsius(8.0).calculate();
    assertEquals(1.0 / c.getConductivitySPerM(), c.getResistivityOhmM(), 1.0e-9);
  }

  @Test
  void ohmicVoltageScalesWithCurrentGapAndElements() {
    SeawaterElectrolyteConductivity c = new SeawaterElectrolyteConductivity().setTemperatureCelsius(8.0).calculate();
    double v1 = c.ohmicVoltage(199.5, 0.005, 0.20, 1);
    double v4 = c.ohmicVoltage(199.5, 0.005, 0.20, 4);
    assertEquals(4.0 * v1, v4, 1.0e-9);
    assertTrue(v1 > 0.0);
  }

  @Test
  void coldWaterRaisesOhmicVoltageRelativeToDesignTemperature() {
    SeawaterElectrolyteConductivity cold = new SeawaterElectrolyteConductivity().setTemperatureCelsius(6.0).calculate();
    double ratio = cold.ohmicVoltageRatioVersusTemperature(15.0);
    assertTrue(ratio > 1.15, "cooling from 15 C to 6 C must raise the ohmic voltage by more than 15%, got " + ratio);
    assertTrue(ratio < 1.6);
  }
}
