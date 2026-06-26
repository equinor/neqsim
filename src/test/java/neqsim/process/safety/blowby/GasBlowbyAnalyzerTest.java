package neqsim.process.safety.blowby;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Unit tests for {@link GasBlowbyAnalyzer}.
 *
 * @author ESOL
 * @version 1.0
 */
public class GasBlowbyAnalyzerTest {

  /**
   * A large HP-to-LP pressure drop should be choked and produce a finite, positive blowby rate.
   */
  @Test
  void chokedHighPressureDropProducesFiniteRate() {
    GasBlowbyResult result = new GasBlowbyAnalyzer().setUpstreamPressure(150.0, "bara")
        .setUpstreamTemperature(40.0, "C").setDownstreamPressure(10.0, "bara").setRestrictionDiameter(50.0, "mm")
        .setSpecificHeatRatio(1.3).setMolarMass(0.018, "kg/mol").analyze();

    assertTrue(result.isChoked(), "expected choked flow at high pressure drop");
    assertTrue(result.getBlowbyMassRateKgPerHr() > 0.0, "blowby rate should be positive");
    assertTrue(Double.isFinite(result.getBlowbyMassRateKgPerHr()), "blowby rate should be finite");
    assertTrue(result.getBlowbyStdVolRateSm3PerHr() > 0.0, "standard volumetric rate should be positive");
  }

  /**
   * When the downstream pressure is close to the upstream pressure, flow should be subcritical (not choked).
   */
  @Test
  void smallPressureDropIsSubcritical() {
    GasBlowbyResult result = new GasBlowbyAnalyzer().setUpstreamPressure(20.0, "bara").setUpstreamTemperature(25.0, "C")
        .setDownstreamPressure(19.0, "bara").setRestrictionDiameter(25.0, "mm").setSpecificHeatRatio(1.3)
        .setMolarMass(0.018, "kg/mol").analyze();

    assertFalse(result.isChoked(), "expected subcritical flow at small pressure drop");
    assertTrue(result.getBlowbyMassRateKgPerHr() > 0.0, "blowby rate should be positive");
  }

  /**
   * A relief capacity above the blowby rate is adequate; below it is inadequate.
   */
  @Test
  void reliefAdequacyVerdict() {
    GasBlowbyAnalyzer base = new GasBlowbyAnalyzer().setUpstreamPressure(100.0, "bara")
        .setUpstreamTemperature(40.0, "C").setDownstreamPressure(10.0, "bara").setRestrictionDiameter(40.0, "mm")
        .setSpecificHeatRatio(1.3).setMolarMass(0.018, "kg/mol");

    double rate = base.analyze().getBlowbyMassRateKgPerHr();

    GasBlowbyResult adequate = new GasBlowbyAnalyzer().setUpstreamPressure(100.0, "bara")
        .setUpstreamTemperature(40.0, "C").setDownstreamPressure(10.0, "bara").setRestrictionDiameter(40.0, "mm")
        .setSpecificHeatRatio(1.3).setMolarMass(0.018, "kg/mol").setDownstreamReliefCapacity(rate * 1.5, "kg/hr")
        .analyze();
    assertEquals(GasBlowbyAnalyzer.BlowbyVerdict.RELIEF_ADEQUATE, adequate.getVerdict());
    assertTrue(adequate.isReliefAdequate());

    GasBlowbyResult inadequate = new GasBlowbyAnalyzer().setUpstreamPressure(100.0, "bara")
        .setUpstreamTemperature(40.0, "C").setDownstreamPressure(10.0, "bara").setRestrictionDiameter(40.0, "mm")
        .setSpecificHeatRatio(1.3).setMolarMass(0.018, "kg/mol").setDownstreamReliefCapacity(rate * 0.5, "kg/hr")
        .analyze();
    assertEquals(GasBlowbyAnalyzer.BlowbyVerdict.RELIEF_INADEQUATE, inadequate.getVerdict());
    assertFalse(inadequate.isReliefAdequate());
  }

  /**
   * Without relief data the verdict is NO_RELIEF_DATA and the JSON serialises despite a NaN margin.
   */
  @Test
  void noReliefDataSerialisesWithNaNMargin() {
    GasBlowbyResult result = new GasBlowbyAnalyzer().setUpstreamPressure(80.0, "bara").setUpstreamTemperature(30.0, "C")
        .setDownstreamPressure(5.0, "bara").setRestrictionDiameter(30.0, "mm").setSpecificHeatRatio(1.3)
        .setMolarMass(0.018, "kg/mol").analyze();

    assertEquals(GasBlowbyAnalyzer.BlowbyVerdict.NO_RELIEF_DATA, result.getVerdict());
    assertFalse(result.isReliefDataProvided());
    String json = result.toJson();
    assertTrue(json.contains("blowbyMassRateKgPerHr"), "json should contain the blowby rate key");
  }

  /**
   * A NeqSim fluid should supply the specific-heat ratio and molar mass, producing a positive rate.
   */
  @Test
  void fluidDerivesPropertiesAndProducesRate() {
    SystemInterface gas = new SystemSrkEos(273.15 + 40.0, 100.0);
    gas.addComponent("methane", 0.9);
    gas.addComponent("ethane", 0.1);
    gas.setMixingRule("classic");

    GasBlowbyResult result = new GasBlowbyAnalyzer().setUpstreamPressure(100.0, "bara")
        .setUpstreamTemperature(40.0, "C").setDownstreamPressure(8.0, "bara").setRestrictionDiameter(40.0, "mm")
        .setGas(gas).analyze();

    assertTrue(result.getSpecificHeatRatio() > 1.0, "derived specific-heat ratio should exceed 1");
    assertTrue(result.getMolarMassKgPerMol() > 0.0, "derived molar mass should be positive");
    assertTrue(result.getBlowbyMassRateKgPerHr() > 0.0, "blowby rate should be positive");
  }

  /**
   * Non-physical configuration (downstream above upstream) is rejected.
   */
  @Test
  void rejectsDownstreamAboveUpstream() {
    GasBlowbyAnalyzer analyzer = new GasBlowbyAnalyzer().setUpstreamPressure(10.0, "bara")
        .setUpstreamTemperature(25.0, "C").setDownstreamPressure(20.0, "bara").setRestrictionDiameter(25.0, "mm")
        .setSpecificHeatRatio(1.3).setMolarMass(0.018, "kg/mol");
    assertThrows(IllegalStateException.class, new org.junit.jupiter.api.function.Executable() {
      @Override
      public void execute() {
        analyzer.analyze();
      }
    });
  }
}
