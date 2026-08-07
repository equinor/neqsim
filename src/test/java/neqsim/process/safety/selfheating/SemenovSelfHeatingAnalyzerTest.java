package neqsim.process.safety.selfheating;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link SemenovSelfHeatingAnalyzer}.
 *
 * @author ESOL
 * @version 1.0
 */
public class SemenovSelfHeatingAnalyzerTest {

  /** Universal gas constant [J/(mol K)]. */
  private static final double R_GAS = 8.314462618;

  /** Activation energy used across the tests [J/mol]. */
  private static final double E = 110000.0;

  /** Volumetric heat-release pre-factor used across the tests [W/m3]. */
  private static final double P = 5.0e13;

  /**
   * Build an analyzer for a pool of the supplied volume and surface area.
   *
   * @param volumeM3 reacting volume in m3
   * @param surfaceAreaM2 heat-loss surface area in m2
   * @param temperatureK ambient temperature in K
   * @return a configured analyzer
   */
  private SemenovSelfHeatingAnalyzer analyzer(double volumeM3, double surfaceAreaM2, double temperatureK) {
    return new SemenovSelfHeatingAnalyzer().setBodySize(volumeM3, surfaceAreaM2).setHeatTransferCoefficient(10.0)
        .setActivationEnergy(E, "J/mol").setVolumetricHeatReleasePreFactor(P).setAmbientTemperature(temperatureK, "K");
  }

  /**
   * The critical Semenov parameter must equal 1/e.
   */
  @Test
  void criticalParameterIsInverseE() {
    assertEquals(1.0 / Math.E, SemenovSelfHeatingAnalyzer.PSI_CRIT, 1.0e-12);
    assertEquals(0.367879441, SemenovSelfHeatingAnalyzer.PSI_CRIT, 1.0e-9);
  }

  /**
   * At the reported critical ambient temperature the Semenov parameter must equal its critical value.
   */
  @Test
  void criticalTemperatureIsSelfConsistent() {
    SemenovSelfHeatingResult result = analyzer(0.01, 0.3, 400.0).analyze();
    double tCrit = result.getCriticalTemperatureK();
    assertFalse(Double.isNaN(tCrit), "critical temperature must be found");

    SemenovSelfHeatingResult atCritical = analyzer(0.01, 0.3, tCrit).analyze();
    assertEquals(1.0, atCritical.getPsiRatio(), 1.0e-4,
        "psi must equal its critical value at the critical temperature");
  }

  /**
   * The verdict must switch from subcritical to self-ignition across the critical temperature.
   */
  @Test
  void verdictSwitchesAcrossCriticalTemperature() {
    double tCrit = analyzer(0.01, 0.3, 400.0).analyze().getCriticalTemperatureK();

    assertEquals(SelfHeatingVerdict.SUBCRITICAL, analyzer(0.01, 0.3, tCrit - 40.0).analyze().getVerdict());
    assertEquals(SelfHeatingVerdict.SELF_IGNITION, analyzer(0.01, 0.3, tCrit + 10.0).analyze().getVerdict());
    assertTrue(analyzer(0.01, 0.3, tCrit - 40.0).analyze().getTemperatureMarginK() > 0.0,
        "temperature margin must be positive when subcritical");
  }

  /**
   * A bulkier body with less surface area per unit volume must be more prone to self-ignition.
   */
  @Test
  void higherVolumeToSurfaceRatioIsMoreHazardous() {
    double thin = analyzer(0.01, 1.0, 450.0).analyze().getPsi();
    double bulky = analyzer(0.01, 0.2, 450.0).analyze().getPsi();

    assertTrue(bulky > thin, "less surface area per unit volume must raise the criticality parameter");
    assertEquals(5.0, bulky / thin, 1.0e-9, "psi must scale inversely with surface area");
  }

  /**
   * Better surface cooling must raise the critical temperature.
   */
  @Test
  void betterCoolingRaisesCriticalTemperature() {
    double poorlyCooled = analyzer(0.01, 0.3, 400.0).setHeatTransferCoefficient(5.0).analyze()
        .getCriticalTemperatureK();
    double wellCooled = analyzer(0.01, 0.3, 400.0).setHeatTransferCoefficient(50.0).analyze().getCriticalTemperatureK();

    assertTrue(wellCooled > poorlyCooled, "stronger surface cooling must permit a hotter environment");
  }

  /**
   * The steady self-heating excess at criticality must be the Semenov temperature rise and must be small enough to
   * escape routine temperature monitoring.
   */
  @Test
  void criticalTemperatureRiseIsSmall() {
    SemenovSelfHeatingResult result = analyzer(0.01, 0.3, 450.0).analyze();
    double expected = R_GAS * 450.0 * 450.0 / E;

    assertEquals(expected, result.getCriticalTemperatureRiseK(), 1.0e-9);
    assertTrue(result.getCriticalTemperatureRiseK() < 30.0,
        "the critical self-heating excess should be only tens of kelvin");
  }

  /**
   * Missing mandatory inputs must be rejected.
   */
  @Test
  void missingInputsAreRejected() {
    assertThrows(IllegalStateException.class, () -> new SemenovSelfHeatingAnalyzer().setBodySize(0.01, 0.3).analyze(),
        "analysis without kinetics must fail");
    assertThrows(IllegalArgumentException.class, () -> new SemenovSelfHeatingAnalyzer().setBodySize(-1.0, 0.3),
        "a negative volume must be rejected");
    assertThrows(IllegalArgumentException.class, () -> new SemenovSelfHeatingAnalyzer().setHeatTransferCoefficient(0.0),
        "a zero heat-transfer coefficient must be rejected");
  }

  /**
   * The result must serialise to JSON for reporting.
   */
  @Test
  void resultSerialisesToJson() {
    String json = analyzer(0.01, 0.3, 450.0).analyze().toJson();
    assertTrue(json.contains("psi"), "JSON must contain the Semenov parameter");
    assertTrue(json.contains("verdict"), "JSON must contain the verdict");
  }
}
