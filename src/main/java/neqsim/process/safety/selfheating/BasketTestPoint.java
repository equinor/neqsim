package neqsim.process.safety.selfheating;

import java.io.Serializable;

/**
 * A single hot-storage ("basket") test observation: one sample size taken to its critical oven temperature.
 *
 * <p>
 * In a standard basket test (EN 15188, ASTM E2021) a sample of the material is held in a wire-mesh basket of known size
 * inside an oven, and the oven temperature is bracketed to find the lowest temperature at which the sample runs away
 * rather than settling to a steady temperature. Repeating this for several basket sizes yields the data set that
 * {@link BasketTestRegression} converts into Arrhenius parameters.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class BasketTestPoint implements Serializable {
  private static final long serialVersionUID = 1L;

  private final SelfHeatingGeometry geometry;
  private final double characteristicDimensionM;
  private final double criticalTemperatureK;

  /**
   * Construct a basket-test observation.
   *
   * @param geometry basket shape; must not be null
   * @param characteristicDimension characteristic half-dimension of the basket, matching the convention of
   * {@link SelfHeatingGeometry#getDimensionDescription()}; must be positive
   * @param dimensionUnit length unit of the dimension ("m", "cm", "mm" or "in")
   * @param criticalTemperature lowest oven temperature at which the sample ran away
   * @param temperatureUnit temperature unit ("K" or "C")
   * @throws IllegalArgumentException if the geometry is null, the dimension is not positive, a unit is unsupported, or
   * the critical temperature is not positive
   */
  public BasketTestPoint(SelfHeatingGeometry geometry, double characteristicDimension, String dimensionUnit,
      double criticalTemperature, String temperatureUnit) {
    if (geometry == null) {
      throw new IllegalArgumentException("Geometry must not be null");
    }
    this.geometry = geometry;
    this.characteristicDimensionM = PorousMediaSelfHeatingAnalyzer.toMetres(characteristicDimension, dimensionUnit);
    this.criticalTemperatureK = new neqsim.util.unit.TemperatureUnit(criticalTemperature, temperatureUnit)
        .getValue("K");
    if (!(this.criticalTemperatureK > 0.0)) {
      throw new IllegalArgumentException("Critical temperature must be positive");
    }
  }

  /**
   * Gets the basket shape.
   *
   * @return the geometry
   */
  public SelfHeatingGeometry getGeometry() {
    return geometry;
  }

  /**
   * Gets the characteristic half-dimension of the basket.
   *
   * @return characteristic dimension in m
   */
  public double getCharacteristicDimensionM() {
    return characteristicDimensionM;
  }

  /**
   * Gets the measured critical oven temperature.
   *
   * @return critical temperature in K
   */
  public double getCriticalTemperatureK() {
    return criticalTemperatureK;
  }
}
