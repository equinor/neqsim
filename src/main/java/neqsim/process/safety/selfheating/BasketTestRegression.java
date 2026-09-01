package neqsim.process.safety.selfheating;

import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Fits Arrhenius self-heating parameters from hot-storage ("basket") test data.
 *
 * <p>
 * The Frank-Kamenetskii and Semenov criticality models need an apparent activation energy {@code E} and a volumetric
 * heat-release pre-exponential factor {@code P = A * Q * rho}. Neither can be obtained from equilibrium thermodynamics
 * — a Gibbs-energy calculation will happily report that an organic liquid is fully oxidised to carbon dioxide and water
 * at room temperature, which says nothing about whether it self-heats. These parameters are measured, and the standard
 * measurement is the basket test.
 * </p>
 *
 * <p>
 * Rearranging the criticality condition {@code delta = deltaCrit} into a straight line gives
 * </p>
 *
 * <p>
 * {@code ln(deltaCrit * Tc^2 / r^2) = ln(E * P / (lambda * R)) - E / (R * Tc)}
 * </p>
 *
 * <p>
 * so a plot of {@code ln(deltaCrit * Tc^2 / r^2)} against {@code 1 / Tc} for a series of basket sizes is linear with
 * slope {@code -E / R}. The activation energy follows from the slope alone; splitting the intercept into {@code P}
 * additionally requires the effective thermal conductivity of the sample.
 * </p>
 *
 * <p>
 * Usage example:
 * </p>
 *
 * <pre>
 * BasketTestRegression fit = new BasketTestRegression();
 * fit.setEffectiveThermalConductivity(0.09);
 * fit.addPoint(SelfHeatingGeometry.CUBE, 25.0, "mm", 168.0, "C");
 * fit.addPoint(SelfHeatingGeometry.CUBE, 50.0, "mm", 149.0, "C");
 * fit.addPoint(SelfHeatingGeometry.CUBE, 100.0, "mm", 132.0, "C");
 * BasketTestRegressionResult result = fit.regress();
 * </pre>
 *
 * <p>
 * References: EN 15188, <i>Determination of the spontaneous ignition behaviour of dust accumulations</i>; ASTM E2021;
 * Bowes, <i>Self-Heating: Evaluating and Controlling the Hazards</i>, 1984.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class BasketTestRegression {
  private static final Logger logger = LogManager.getLogger(BasketTestRegression.class);

  /** Universal gas constant [J/(mol K)]. */
  private static final double R_GAS = 8.314462618;

  /** Minimum number of distinct sample sizes recommended for a defensible fit. */
  private static final int RECOMMENDED_POINT_COUNT = 3;

  private final List<BasketTestPoint> points = new ArrayList<BasketTestPoint>();
  private double effectiveConductivityWPerMK = Double.NaN;

  /**
   * Create an empty basket-test regression.
   */
  public BasketTestRegression() {
  }

  /**
   * Add a basket-test observation.
   *
   * @param point the observation; must not be null
   * @return this regression for chaining
   * @throws IllegalArgumentException if the point is null
   */
  public BasketTestRegression addPoint(BasketTestPoint point) {
    if (point == null) {
      throw new IllegalArgumentException("Basket-test point must not be null");
    }
    points.add(point);
    return this;
  }

  /**
   * Add a basket-test observation from raw values.
   *
   * @param geometry basket shape; must not be null
   * @param characteristicDimension characteristic half-dimension of the basket; must be positive
   * @param dimensionUnit length unit of the dimension ("m", "cm", "mm" or "in")
   * @param criticalTemperature lowest oven temperature at which the sample ran away
   * @param temperatureUnit temperature unit ("K" or "C")
   * @return this regression for chaining
   * @throws IllegalArgumentException if any argument is invalid
   */
  public BasketTestRegression addPoint(SelfHeatingGeometry geometry, double characteristicDimension,
      String dimensionUnit, double criticalTemperature, String temperatureUnit) {
    return addPoint(
        new BasketTestPoint(geometry, characteristicDimension, dimensionUnit, criticalTemperature, temperatureUnit));
  }

  /**
   * Set the effective thermal conductivity of the tested sample, required to split the regression intercept into a
   * volumetric heat-release pre-exponential factor.
   *
   * @param wPerMK effective thermal conductivity [W/(m K)]; must be positive
   * @return this regression for chaining
   * @throws IllegalArgumentException if the value is not positive
   */
  public BasketTestRegression setEffectiveThermalConductivity(double wPerMK) {
    if (!(wPerMK > 0.0)) {
      throw new IllegalArgumentException("Effective thermal conductivity must be positive");
    }
    this.effectiveConductivityWPerMK = wPerMK;
    return this;
  }

  /**
   * Perform the least-squares fit.
   *
   * @return an immutable regression result
   * @throws IllegalStateException if fewer than two points were supplied or all points share the same temperature
   */
  public BasketTestRegressionResult regress() {
    if (points.size() < 2) {
      throw new IllegalStateException("At least two basket-test points are required to fit an activation energy");
    }
    List<String> warnings = new ArrayList<String>();
    if (points.size() < RECOMMENDED_POINT_COUNT) {
      warnings.add("Only " + points.size() + " basket sizes supplied; at least " + RECOMMENDED_POINT_COUNT
          + " are recommended for a defensible fit and a meaningful coefficient of determination");
    }

    int n = points.size();
    double[] x = new double[n];
    double[] y = new double[n];
    for (int i = 0; i < n; i++) {
      BasketTestPoint p = points.get(i);
      double tc = p.getCriticalTemperatureK();
      x[i] = 1.0 / tc;
      y[i] = Math.log(p.getGeometry().getDeltaCrit() * tc * tc
          / (p.getCharacteristicDimensionM() * p.getCharacteristicDimensionM()));
    }

    double sumX = 0.0;
    double sumY = 0.0;
    for (int i = 0; i < n; i++) {
      sumX += x[i];
      sumY += y[i];
    }
    double meanX = sumX / n;
    double meanY = sumY / n;

    double sxx = 0.0;
    double sxy = 0.0;
    for (int i = 0; i < n; i++) {
      sxx += (x[i] - meanX) * (x[i] - meanX);
      sxy += (x[i] - meanX) * (y[i] - meanY);
    }
    if (!(sxx > 0.0)) {
      throw new IllegalStateException("All basket-test points share the same critical temperature; cannot fit a slope");
    }

    double slope = sxy / sxx;
    double intercept = meanY - slope * meanX;

    double ssTot = 0.0;
    double ssRes = 0.0;
    for (int i = 0; i < n; i++) {
      double fitted = intercept + slope * x[i];
      ssTot += (y[i] - meanY) * (y[i] - meanY);
      ssRes += (y[i] - fitted) * (y[i] - fitted);
    }
    double rSquared = ssTot > 0.0 ? 1.0 - ssRes / ssTot : Double.NaN;

    double activationEnergy = -slope * R_GAS;
    if (!(activationEnergy > 0.0)) {
      warnings.add("Fitted activation energy is not positive; the critical temperature should fall as basket size "
          + "increases, so check the ordering and units of the input data");
    }

    boolean conductivityProvided = effectiveConductivityWPerMK > 0.0 && !Double.isNaN(effectiveConductivityWPerMK);
    double volumetricPreFactor = Double.NaN;
    if (conductivityProvided && activationEnergy > 0.0) {
      volumetricPreFactor = Math.exp(intercept) * effectiveConductivityWPerMK * R_GAS / activationEnergy;
    } else if (!conductivityProvided) {
      warnings.add("Effective thermal conductivity not supplied; the volumetric heat-release pre-factor could not be "
          + "separated from the regression intercept");
    }

    logger.info("Basket-test regression on {} points: E={} J/mol, P={} W/m3, R2={}", n, activationEnergy,
        volumetricPreFactor, rSquared);

    return new BasketTestRegressionResult(n, slope, intercept, rSquared, activationEnergy, volumetricPreFactor,
        effectiveConductivityWPerMK, conductivityProvided, warnings);
  }
}
