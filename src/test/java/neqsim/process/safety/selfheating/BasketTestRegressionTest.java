package neqsim.process.safety.selfheating;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link BasketTestRegression}.
 *
 * <p>
 * The central test is a round trip: synthetic critical temperatures are generated from known kinetics using
 * {@link PorousMediaSelfHeatingAnalyzer}, then fed back through the regression, which must recover the kinetics it
 * started from. This validates the criticality model and the regression against each other.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class BasketTestRegressionTest {

  /** Activation energy used to generate the synthetic data [J/mol]. */
  private static final double TRUE_E = 110000.0;

  /** Volumetric heat-release pre-factor used to generate the synthetic data [W/m3]. */
  private static final double TRUE_P = 5.0e13;

  /** Effective thermal conductivity of the synthetic sample [W/(m K)]. */
  private static final double LAMBDA = 0.09;

  /**
   * Compute the critical oven temperature for a basket of the given size from the known kinetics.
   *
   * @param geometry basket shape
   * @param dimensionM characteristic half-dimension in m
   * @return the critical temperature in K
   */
  private double syntheticCriticalTemperature(SelfHeatingGeometry geometry, double dimensionM) {
    return new PorousMediaSelfHeatingAnalyzer().setGeometry(geometry).setCharacteristicDimension(dimensionM, "m")
        .setEffectiveThermalConductivity(LAMBDA).setActivationEnergy(TRUE_E, "J/mol")
        .setVolumetricHeatReleasePreFactor(TRUE_P).setBoundaryTemperature(400.0, "K").analyze()
        .getCriticalTemperatureK();
  }

  /**
   * The regression must recover the activation energy and volumetric pre-factor used to generate the data.
   */
  @Test
  void regressionRecoversKnownKinetics() {
    SelfHeatingGeometry geometry = SelfHeatingGeometry.CUBE;
    double[] sizes = new double[] { 0.0125, 0.025, 0.05, 0.1 };

    BasketTestRegression regression = new BasketTestRegression().setEffectiveThermalConductivity(LAMBDA);
    for (int i = 0; i < sizes.length; i++) {
      regression.addPoint(geometry, sizes[i], "m", syntheticCriticalTemperature(geometry, sizes[i]), "K");
    }

    BasketTestRegressionResult result = regression.regress();

    assertEquals(TRUE_E, result.getActivationEnergyJPerMol(), TRUE_E * 1.0e-4, "activation energy must be recovered");
    assertEquals(TRUE_P, result.getVolumetricPreFactorWPerM3(), TRUE_P * 1.0e-3,
        "volumetric pre-factor must be recovered");
    assertEquals(1.0, result.getRSquared(), 1.0e-8, "a synthetic data set must fit perfectly");
    assertEquals(sizes.length, result.getPointCount());
    assertTrue(result.isConductivityProvided());
  }

  /**
   * Mixing basket shapes must still recover the same kinetics, because the shape enters only through its critical
   * Frank-Kamenetskii value.
   */
  @Test
  void regressionHandlesMixedGeometries() {
    BasketTestRegression regression = new BasketTestRegression().setEffectiveThermalConductivity(LAMBDA);
    regression.addPoint(SelfHeatingGeometry.CUBE, 0.025, "m",
        syntheticCriticalTemperature(SelfHeatingGeometry.CUBE, 0.025), "K");
    regression.addPoint(SelfHeatingGeometry.SPHERE, 0.05, "m",
        syntheticCriticalTemperature(SelfHeatingGeometry.SPHERE, 0.05), "K");
    regression.addPoint(SelfHeatingGeometry.INFINITE_CYLINDER, 0.1, "m",
        syntheticCriticalTemperature(SelfHeatingGeometry.INFINITE_CYLINDER, 0.1), "K");

    BasketTestRegressionResult result = regression.regress();
    assertEquals(TRUE_E, result.getActivationEnergyJPerMol(), TRUE_E * 1.0e-4);
    assertEquals(TRUE_P, result.getVolumetricPreFactorWPerM3(), TRUE_P * 1.0e-3);
  }

  /**
   * The critical temperature must fall as basket size increases, which is the qualitative signature of self-heating
   * that the test method relies on.
   */
  @Test
  void criticalTemperatureFallsWithIncreasingBasketSize() {
    double small = syntheticCriticalTemperature(SelfHeatingGeometry.CUBE, 0.0125);
    double medium = syntheticCriticalTemperature(SelfHeatingGeometry.CUBE, 0.05);
    double large = syntheticCriticalTemperature(SelfHeatingGeometry.CUBE, 0.2);

    assertTrue(medium < small, "larger baskets must ignite at lower oven temperatures");
    assertTrue(large < medium, "larger baskets must ignite at lower oven temperatures");
  }

  /**
   * A fit built from small laboratory baskets must extrapolate to a much lower critical temperature at plant scale.
   * This extrapolation is the entire purpose of the test method.
   */
  @Test
  void fittedKineticsExtrapolateToPlantScale() {
    SelfHeatingGeometry geometry = SelfHeatingGeometry.CUBE;
    double[] labSizes = new double[] { 0.0125, 0.025, 0.05 };

    BasketTestRegression regression = new BasketTestRegression().setEffectiveThermalConductivity(LAMBDA);
    double smallestLabCriticalTemperature = syntheticCriticalTemperature(geometry, labSizes[0]);
    for (int i = 0; i < labSizes.length; i++) {
      regression.addPoint(geometry, labSizes[i], "m", syntheticCriticalTemperature(geometry, labSizes[i]), "K");
    }

    BasketTestRegressionResult fit = regression.regress();
    PorousMediaSelfHeatingResult plantScale = fit.createAnalyzer(SelfHeatingGeometry.SLAB, 100.0, "mm", 200.0, "C")
        .analyze();

    assertTrue(plantScale.getCriticalTemperatureK() < smallestLabCriticalTemperature,
        "a thick plant-scale layer must be critical at a lower temperature than a small laboratory basket");
    assertEquals(SelfHeatingGeometry.SLAB, plantScale.getGeometry());
  }

  /**
   * Fewer than two sizes cannot define a slope, and identical temperatures cannot either.
   */
  @Test
  void degenerateDataSetsAreRejected() {
    BasketTestRegression single = new BasketTestRegression().addPoint(SelfHeatingGeometry.CUBE, 0.05, "m", 150.0, "C");
    assertThrows(IllegalStateException.class, single::regress, "a single point cannot define a slope");

    BasketTestRegression flat = new BasketTestRegression().addPoint(SelfHeatingGeometry.CUBE, 0.05, "m", 150.0, "C")
        .addPoint(SelfHeatingGeometry.CUBE, 0.1, "m", 150.0, "C");
    assertThrows(IllegalStateException.class, flat::regress, "identical temperatures cannot define a slope");
  }

  /**
   * Omitting the effective thermal conductivity must still yield an activation energy, but must warn that the
   * volumetric pre-factor could not be separated from the intercept.
   */
  @Test
  void missingConductivityStillYieldsActivationEnergy() {
    SelfHeatingGeometry geometry = SelfHeatingGeometry.CUBE;
    BasketTestRegression regression = new BasketTestRegression();
    regression.addPoint(geometry, 0.025, "m", syntheticCriticalTemperature(geometry, 0.025), "K");
    regression.addPoint(geometry, 0.05, "m", syntheticCriticalTemperature(geometry, 0.05), "K");
    regression.addPoint(geometry, 0.1, "m", syntheticCriticalTemperature(geometry, 0.1), "K");

    BasketTestRegressionResult result = regression.regress();

    assertEquals(TRUE_E, result.getActivationEnergyJPerMol(), TRUE_E * 1.0e-4);
    assertTrue(Double.isNaN(result.getVolumetricPreFactorWPerM3()),
        "the pre-factor must be unresolved without a conductivity");
    assertTrue(result.getWarnings().size() > 0, "a warning must be recorded");
    assertThrows(IllegalStateException.class,
        () -> result.createAnalyzer(SelfHeatingGeometry.SLAB, 50.0, "mm", 180.0, "C"),
        "an incomplete fit must not silently build an analyzer");
  }

  /**
   * Only two sizes must fit but must warn that more are recommended.
   */
  @Test
  void twoPointFitIsAllowedButWarned() {
    SelfHeatingGeometry geometry = SelfHeatingGeometry.CUBE;
    BasketTestRegressionResult result = new BasketTestRegression().setEffectiveThermalConductivity(LAMBDA)
        .addPoint(geometry, 0.025, "m", syntheticCriticalTemperature(geometry, 0.025), "K")
        .addPoint(geometry, 0.1, "m", syntheticCriticalTemperature(geometry, 0.1), "K").regress();

    assertEquals(TRUE_E, result.getActivationEnergyJPerMol(), TRUE_E * 1.0e-4);
    boolean warned = false;
    for (int i = 0; i < result.getWarnings().size(); i++) {
      if (result.getWarnings().get(i).contains("recommended")) {
        warned = true;
      }
    }
    assertTrue(warned, "a two-point fit must carry a recommendation warning");
  }

  /**
   * The result must serialise to JSON for reporting.
   */
  @Test
  void resultSerialisesToJson() {
    SelfHeatingGeometry geometry = SelfHeatingGeometry.CUBE;
    String json = new BasketTestRegression().setEffectiveThermalConductivity(LAMBDA)
        .addPoint(geometry, 0.025, "m", syntheticCriticalTemperature(geometry, 0.025), "K")
        .addPoint(geometry, 0.05, "m", syntheticCriticalTemperature(geometry, 0.05), "K")
        .addPoint(geometry, 0.1, "m", syntheticCriticalTemperature(geometry, 0.1), "K").regress().toJson();

    assertTrue(json.contains("activationEnergyJPerMol"), "JSON must contain the fitted activation energy");
  }
}
