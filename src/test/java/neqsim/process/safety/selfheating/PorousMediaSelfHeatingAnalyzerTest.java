package neqsim.process.safety.selfheating;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link PorousMediaSelfHeatingAnalyzer}.
 *
 * @author ESOL
 * @version 1.0
 */
public class PorousMediaSelfHeatingAnalyzerTest {

  /** Universal gas constant [J/(mol K)]. */
  private static final double R_GAS = 8.314462618;

  /** Reference activation energy used across the tests [J/mol]. */
  private static final double E = 110000.0;

  /** Reference volumetric heat-release pre-factor used across the tests [W/m3]. */
  private static final double P = 5.0e13;

  /** Reference effective thermal conductivity used across the tests [W/(m K)]. */
  private static final double LAMBDA = 0.09;

  /**
   * Build an analyzer with the reference kinetics and the supplied size and temperature.
   *
   * @param geometry body shape
   * @param dimensionM characteristic half-dimension in m
   * @param boundaryTemperatureK boundary temperature in K
   * @return a configured analyzer
   */
  private PorousMediaSelfHeatingAnalyzer analyzer(SelfHeatingGeometry geometry, double dimensionM,
      double boundaryTemperatureK) {
    return new PorousMediaSelfHeatingAnalyzer().setGeometry(geometry).setCharacteristicDimension(dimensionM, "m")
        .setEffectiveThermalConductivity(LAMBDA).setActivationEnergy(E, "J/mol").setVolumetricHeatReleasePreFactor(P)
        .setBoundaryTemperature(boundaryTemperatureK, "K");
  }

  /**
   * Evaluate the Frank-Kamenetskii parameter directly from its definition, as an independent check on the logarithmic
   * implementation used inside the analyzer.
   *
   * @param dimensionM characteristic half-dimension in m
   * @param temperatureK boundary temperature in K
   * @return the criticality parameter delta
   */
  private double deltaDirect(double dimensionM, double temperatureK) {
    return (E * P * dimensionM * dimensionM) / (LAMBDA * R_GAS * temperatureK * temperatureK)
        * Math.exp(-E / (R_GAS * temperatureK));
  }

  /**
   * The published critical values of the Frank-Kamenetskii parameter must be carried by the geometry enum.
   */
  @Test
  void criticalDeltaValuesMatchLiterature() {
    assertEquals(0.878, SelfHeatingGeometry.SLAB.getDeltaCrit(), 1.0e-3);
    assertEquals(2.000, SelfHeatingGeometry.INFINITE_CYLINDER.getDeltaCrit(), 1.0e-3);
    assertEquals(3.322, SelfHeatingGeometry.SPHERE.getDeltaCrit(), 1.0e-3);
  }

  /**
   * The logarithmic evaluation inside the analyzer must reproduce a direct evaluation of the defining expression.
   */
  @Test
  void criticalityParameterMatchesDirectEvaluation() {
    double dimension = 0.05;
    double temperature = 430.0;
    PorousMediaSelfHeatingResult result = analyzer(SelfHeatingGeometry.SLAB, dimension, temperature).analyze();
    double expected = deltaDirect(dimension, temperature);
    assertEquals(expected, result.getDelta(), expected * 1.0e-9, "delta must match the direct expression");
  }

  /**
   * At the reported critical boundary temperature the criticality parameter must equal its critical value.
   */
  @Test
  void criticalTemperatureIsSelfConsistent() {
    PorousMediaSelfHeatingResult result = analyzer(SelfHeatingGeometry.SPHERE, 0.05, 400.0).analyze();
    double tCrit = result.getCriticalTemperatureK();
    assertFalse(Double.isNaN(tCrit), "critical temperature must be found");

    PorousMediaSelfHeatingResult atCritical = analyzer(SelfHeatingGeometry.SPHERE, 0.05, tCrit).analyze();
    assertEquals(1.0, atCritical.getDeltaRatio(), 1.0e-4,
        "delta must equal deltaCrit at the reported critical temperature");
  }

  /**
   * At the reported critical half-dimension the criticality parameter must equal its critical value.
   */
  @Test
  void criticalDimensionIsSelfConsistent() {
    double temperature = 420.0;
    PorousMediaSelfHeatingResult result = analyzer(SelfHeatingGeometry.SLAB, 0.05, temperature).analyze();
    double rCrit = result.getCriticalDimensionM();
    assertTrue(rCrit > 0.0, "critical dimension must be positive");

    PorousMediaSelfHeatingResult atCritical = analyzer(SelfHeatingGeometry.SLAB, rCrit, temperature).analyze();
    assertEquals(1.0, atCritical.getDeltaRatio(), 1.0e-6,
        "delta must equal deltaCrit at the reported critical dimension");
  }

  /**
   * The criticality parameter must scale with the square of the characteristic dimension. This is the size effect that
   * a lumped adiabatic screening cannot represent.
   */
  @Test
  void criticalityScalesWithSquareOfDimension() {
    double small = analyzer(SelfHeatingGeometry.SLAB, 0.02, 420.0).analyze().getDelta();
    double large = analyzer(SelfHeatingGeometry.SLAB, 0.04, 420.0).analyze().getDelta();
    assertEquals(4.0, large / small, 1.0e-6, "doubling the size must quadruple delta");
  }

  /**
   * The same material at the same temperature must be safe in a thin layer and unsafe in a thick one. This is the
   * central engineering conclusion of the model.
   */
  @Test
  void thinLayerIsSafeWhileThickLayerSelfIgnites() {
    double temperature = 430.0;
    PorousMediaSelfHeatingResult reference = analyzer(SelfHeatingGeometry.SLAB, 0.05, temperature).analyze();
    double rCrit = reference.getCriticalDimensionM();

    PorousMediaSelfHeatingResult thin = analyzer(SelfHeatingGeometry.SLAB, 0.25 * rCrit, temperature).analyze();
    PorousMediaSelfHeatingResult thick = analyzer(SelfHeatingGeometry.SLAB, 2.0 * rCrit, temperature).analyze();

    assertEquals(SelfHeatingVerdict.SUBCRITICAL, thin.getVerdict(), "a quarter-critical layer must be subcritical");
    assertFalse(thin.isSelfIgnitionPredicted());
    assertEquals(SelfHeatingVerdict.SELF_IGNITION, thick.getVerdict(), "a double-critical layer must self-ignite");
    assertTrue(thick.isSelfIgnitionPredicted());
    assertTrue(thick.getDimensionMarginM() < 0.0, "dimension margin must be negative when supercritical");
  }

  /**
   * A case just below the critical size must be reported as marginal rather than safe.
   */
  @Test
  void nearCriticalCaseIsFlaggedMarginal() {
    double temperature = 430.0;
    double rCrit = analyzer(SelfHeatingGeometry.SLAB, 0.05, temperature).analyze().getCriticalDimensionM();
    // delta scales with r^2, so 0.95 * rCrit gives a delta ratio of about 0.90.
    PorousMediaSelfHeatingResult near = analyzer(SelfHeatingGeometry.SLAB, 0.95 * rCrit, temperature).analyze();
    assertEquals(SelfHeatingVerdict.MARGINAL, near.getVerdict());
    assertTrue(near.getDeltaRatio() < 1.0, "a marginal case must still be below the critical ratio");
  }

  /**
   * Raising the boundary temperature must reduce the permissible layer thickness.
   */
  @Test
  void criticalDimensionFallsAsTemperatureRises() {
    double cool = analyzer(SelfHeatingGeometry.SLAB, 0.05, 400.0).analyze().getCriticalDimensionM();
    double hot = analyzer(SelfHeatingGeometry.SLAB, 0.05, 450.0).analyze().getCriticalDimensionM();
    assertTrue(hot < cool, "hotter surfaces must permit thinner layers");
  }

  /**
   * A body of fixed size must be subcritical below and supercritical above its critical temperature.
   */
  @Test
  void verdictSwitchesAcrossCriticalTemperature() {
    PorousMediaSelfHeatingResult reference = analyzer(SelfHeatingGeometry.INFINITE_CYLINDER, 0.06, 400.0).analyze();
    double tCrit = reference.getCriticalTemperatureK();

    assertEquals(SelfHeatingVerdict.SUBCRITICAL,
        analyzer(SelfHeatingGeometry.INFINITE_CYLINDER, 0.06, tCrit - 40.0).analyze().getVerdict());
    assertEquals(SelfHeatingVerdict.SELF_IGNITION,
        analyzer(SelfHeatingGeometry.INFINITE_CYLINDER, 0.06, tCrit + 10.0).analyze().getVerdict());
    assertTrue(
        analyzer(SelfHeatingGeometry.INFINITE_CYLINDER, 0.06, tCrit - 40.0).analyze().getTemperatureMarginK() > 0.0,
        "temperature margin must be positive when subcritical");
  }

  /**
   * The pipe-insulation convenience configuration must set the slab geometry, use the full insulation thickness as the
   * characteristic dimension, and record its conservatism.
   */
  @Test
  void pipeInsulationConfigurationIsConservativeAndDocumented() {
    PorousMediaSelfHeatingAnalyzer analyzer = new PorousMediaSelfHeatingAnalyzer()
        .setEffectiveThermalConductivity(LAMBDA).setActivationEnergy(E, "J/mol").setVolumetricHeatReleasePreFactor(P)
        .forPipeInsulation(50.0, "mm", 180.0, "C");

    PorousMediaSelfHeatingResult result = analyzer.analyze();

    assertEquals(SelfHeatingGeometry.SLAB, result.getGeometry());
    assertEquals(0.05, result.getCharacteristicDimensionM(), 1.0e-12);
    assertEquals(453.15, result.getBoundaryTemperatureK(), 1.0e-6);
    boolean noted = false;
    for (int i = 0; i < result.getWarnings().size(); i++) {
      if (result.getWarnings().get(i).contains("conservative")) {
        noted = true;
      }
    }
    assertTrue(noted, "the conservative bounding assumption must be recorded in the warnings");
  }

  /**
   * The Frank-Kamenetskii temperature scale must be small, which is why self-heating escapes routine temperature
   * monitoring until it runs away.
   */
  @Test
  void temperatureScaleIsSmall() {
    PorousMediaSelfHeatingResult result = analyzer(SelfHeatingGeometry.SLAB, 0.05, 430.0).analyze();
    double expected = R_GAS * 430.0 * 430.0 / E;
    assertEquals(expected, result.getFkTemperatureScaleK(), 1.0e-9);
    assertTrue(result.getFkTemperatureScaleK() < 30.0, "the self-heating temperature scale should be a few kelvin");
  }

  /**
   * The result must serialise to JSON for reporting.
   */
  @Test
  void resultSerialisesToJson() {
    String json = analyzer(SelfHeatingGeometry.SLAB, 0.05, 430.0).analyze().toJson();
    assertTrue(json.contains("delta"), "JSON must contain the criticality parameter");
    assertTrue(json.contains("verdict"), "JSON must contain the verdict");
  }

  /**
   * Missing mandatory inputs must be rejected rather than silently defaulted.
   */
  @Test
  void missingInputsAreRejected() {
    assertThrows(IllegalStateException.class,
        () -> new PorousMediaSelfHeatingAnalyzer().setCharacteristicDimension(0.05, "m").analyze(),
        "analysis without kinetics must fail");
    assertThrows(IllegalArgumentException.class,
        () -> new PorousMediaSelfHeatingAnalyzer().setCharacteristicDimension(-1.0, "m"),
        "a negative dimension must be rejected");
    assertThrows(IllegalArgumentException.class,
        () -> new PorousMediaSelfHeatingAnalyzer().setActivationEnergy(100.0, "cal/mol"),
        "an unsupported energy unit must be rejected");
  }

  /**
   * Supplying kinetics as separate pre-exponential, heat of reaction and loading must give the same result as supplying
   * the combined volumetric pre-factor.
   */
  @Test
  void separateKineticInputsMatchCombinedPreFactor() {
    double preExponential = 2.5e8;
    double heatOfReaction = 2.0e7;
    double loading = 200.0;

    PorousMediaSelfHeatingResult combined = analyzer(SelfHeatingGeometry.SLAB, 0.05, 430.0)
        .setVolumetricHeatReleasePreFactor(preExponential * heatOfReaction * loading).analyze();
    PorousMediaSelfHeatingResult separate = analyzer(SelfHeatingGeometry.SLAB, 0.05, 430.0)
        .setKinetics(preExponential, heatOfReaction, loading).analyze();

    assertEquals(combined.getDelta(), separate.getDelta(), combined.getDelta() * 1.0e-12);
  }
}
