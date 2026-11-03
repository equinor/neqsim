package neqsim.thermo.component.attractiveeosterm;

import neqsim.thermo.component.ComponentEosInterface;

/**
 * ISO 18453 water attraction function and its branch-local temperature derivatives.
 *
 * @author esol
 * @version $Id: $Id
 */
public class AttractiveTermGERG extends AttractiveTermPr {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Ice/liquid-water coefficient boundary specified by ISO 18453, in kelvin. */
  private static final double WATER_TRIPLE_POINT = 273.16;
  /** Attraction coefficients over liquid water. */
  protected double[] parametersGERG = {0.905436, -0.213781, 0.26005};
  /** Attraction coefficients over ice. */
  protected double[] parametersSolidGERG = {0.106025, 2.683845, -4.75638};

  /**
   * Constructor for AttractiveTermGERG.
   *
   * @param component a {@link neqsim.thermo.component.ComponentEosInterface} object
   */
  public AttractiveTermGERG(ComponentEosInterface component) {
    super(component);
    if (component.getName().equals("water")) {
      System.arraycopy(component.getMatiascopemanParams(), 0, this.parameters, 0,
          component.getMatiascopemanParams().length);
      System.arraycopy(component.getMatiascopemanSolidParams(), 0, this.parametersSolid, 0,
          component.getMatiascopemanSolidParams().length);
    }
  }

  /**
   * AttractiveTermGERG.
   *
   * @return a {@link java.lang.Object} object
   */
  public Object AttractiveTermGERG() {
    AttractiveTermGERG attractiveTerm = null;
    try {
      attractiveTerm = (AttractiveTermGERG) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }

    return attractiveTerm;
  }

  /** {@inheritDoc} */
  @Override
  public double alpha(double temperature) {
    if (getComponent().getName().equals("water")) {
      selectWaterParameters(temperature);
      double u = 1.0 - Math.sqrt(temperature / getComponent().getTC());
      double polynomial = waterPolynomial(u);
      return polynomial * polynomial;
    } else {
      return super.alpha(temperature);
    }
  }

  /** {@inheritDoc} */
  @Override
  public double aT(double temperature) {
    if (getComponent().getName().equals("water")) {
      return getComponent().geta() * alpha(temperature);
    } else {
      return super.aT(temperature);
    }
  }

  /**
   * Returns the first temperature derivative through the legacy GERG entry point.
   *
   * @param temperature temperature in kelvin
   * @return first alpha derivative in inverse kelvin
   */
  public double diffalphaTGERG(double temperature) {
    return diffalphaT(temperature);
  }

  /**
   * Returns the second temperature derivative through the legacy GERG entry point.
   *
   * @param temperature temperature in kelvin
   * @return second alpha derivative in inverse kelvin squared
   */
  public double diffdiffalphaTGERG(double temperature) {
    return diffdiffalphaT(temperature);
  }

  /** {@inheritDoc} */
  @Override
  public double diffalphaT(double temperature) {
    if (!getComponent().getName().equals("water")) {
      return super.diffalphaT(temperature);
    }
    selectWaterParameters(temperature);
    double sqrtTr = Math.sqrt(temperature / getComponent().getTC());
    double u = 1.0 - sqrtTr;
    double duDt = -sqrtTr / (2.0 * temperature);
    return 2.0 * waterPolynomial(u) * waterPolynomialSlope(u) * duDt;
  }

  /** {@inheritDoc} */
  @Override
  public double diffdiffalphaT(double temperature) {
    if (!getComponent().getName().equals("water")) {
      return super.diffdiffalphaT(temperature);
    }
    selectWaterParameters(temperature);
    double sqrtTr = Math.sqrt(temperature / getComponent().getTC());
    double u = 1.0 - sqrtTr;
    double duDt = -sqrtTr / (2.0 * temperature);
    double d2uDt2 = sqrtTr / (4.0 * temperature * temperature);
    double slope = waterPolynomialSlope(u);
    double curvature = 2.0 * parameters[1] + 12.0 * parameters[2] * u * u;
    double first = slope * duDt;
    double second = curvature * duDt * duDt + slope * d2uDt2;
    return 2.0 * (first * first + waterPolynomial(u) * second);
  }

  /**
   * Selects the same ice or liquid-water coefficients for values and derivatives.
   *
   * @param temperature temperature in kelvin
   */
  private void selectWaterParameters(double temperature) {
    double[] selected = temperature < WATER_TRIPLE_POINT ? parametersSolidGERG : parametersGERG;
    System.arraycopy(selected, 0, parameters, 0, selected.length);
  }

  /**
   * Evaluates the unsquared ISO polynomial in the square-root temperature coordinate.
   *
   * @param u one minus the square root of reduced temperature
   * @return unsquared attraction polynomial
   */
  private double waterPolynomial(double u) {
    double u2 = u * u;
    return 1.0 + parameters[0] * u + parameters[1] * u2 + parameters[2] * u2 * u2;
  }

  /**
   * Differentiates the unsquared polynomial with respect to its temperature coordinate.
   *
   * @param u one minus the square root of reduced temperature
   * @return polynomial derivative with respect to u
   */
  private double waterPolynomialSlope(double u) {
    return parameters[0] + 2.0 * parameters[1] * u + 4.0 * parameters[2] * u * u * u;
  }

  /** {@inheritDoc} */
  @Override
  public double diffaT(double temperature) {
    if (getComponent().getName().equals("water")) {
      return getComponent().geta() * diffalphaTGERG(temperature);
    } else {
      return super.diffaT(temperature);
    }
  }

  /** {@inheritDoc} */
  @Override
  public double diffdiffaT(double temperature) {
    if (getComponent().getName().equals("water")) {
      return getComponent().geta() * diffdiffalphaTGERG(temperature);
    } else {
      return super.diffdiffaT(temperature);
    }
  }
}
