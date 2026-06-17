package neqsim.standards.oilquality;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;

/**
 * Estimated Smoke Point - ASTM D1322 (screening correlation).
 *
 * <p>
 * The smoke point is the maximum flame height in millimetres at which a kerosene / jet fuel burns
 * without smoking. It is a measure of the burning quality and is controlled in aviation turbine
 * fuel specifications (e.g. Jet A-1 requires a smoke point of at least 25 mm). A high smoke point
 * indicates a paraffinic, low-aromatic fuel.
 * </p>
 *
 * <p>
 * <b>This class returns an estimate, not a laboratory measurement.</b> The smoke point tracks
 * aromaticity, which is captured here through the estimated aniline point from
 * {@link Standard_ASTM_D611}. A more paraffinic fuel has a higher aniline point and a higher smoke
 * point:
 * </p>
 *
 * <pre>
 * {@code
 * SmokePoint[mm] = sp0 + sp1 * anilinePoint[C]
 * }
 * </pre>
 *
 * <p>
 * with defaults {@code sp0 = 8.5}, {@code sp1 = 0.325}, anchored to: paraffinic kerosene (aniline
 * point &asymp; 60 &deg;C &rarr; smoke point &asymp; 28 mm) and aromatic kerosene (aniline point
 * &asymp; 20 &deg;C &rarr; smoke point &asymp; 15 mm). The coefficients are configurable so the
 * correlation can be calibrated to measured assay data. For rigorous work, calibrate against ASTM
 * D1322 laboratory data.
 * </p>
 *
 * <p>
 * Usage example:
 * </p>
 *
 * <pre>
 * {@code
 * Standard_ASTM_D1322 smoke = new Standard_ASTM_D1322(jetFluid);
 * smoke.calculate();
 * double smokePointMm = smoke.getValue("smokePoint", "mm");
 * }
 * </pre>
 *
 * @author ESOL
 * @version 1.0
 */
public class Standard_ASTM_D1322 extends neqsim.standards.Standard {

  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Logger object for class. */
  private static final Logger logger = LogManager.getLogger(Standard_ASTM_D1322.class);

  /** Smoke point in millimetres. */
  private double smokePointMm = Double.NaN;

  /** Aniline point in Celsius used in the correlation. */
  private double anilinePointC = Double.NaN;

  /** Correlation intercept coefficient. */
  private double sp0 = 8.5;

  /** Correlation aniline-point coefficient. */
  private double sp1 = 0.325;

  /** Optional minimum smoke-point specification limit in mm (NaN = no limit). */
  private double minSmokeSpecMm = Double.NaN;

  /**
   * Constructor for Standard_ASTM_D1322.
   *
   * @param thermoSystem a {@link neqsim.thermo.system.SystemInterface} object representing the fuel
   */
  public Standard_ASTM_D1322(SystemInterface thermoSystem) {
    super("Standard_ASTM_D1322", "ASTM D1322 - Estimated Smoke Point", thermoSystem);
  }

  /** {@inheritDoc} */
  @Override
  public void calculate() {
    try {
      Standard_ASTM_D611 d611 = new Standard_ASTM_D611(thermoSystem);
      d611.calculate();
      anilinePointC = d611.getValue("anilinePoint");

      if (Double.isNaN(anilinePointC)) {
        logger.error("Smoke point input unavailable (aniline point)");
        return;
      }

      smokePointMm = sp0 + sp1 * anilinePointC;
      if (smokePointMm < 0.0) {
        smokePointMm = 0.0;
      }
    } catch (Exception ex) {
      logger.error("Smoke point calculation failed: {}", ex.getMessage());
    }
  }

  /** {@inheritDoc} */
  @Override
  public double getValue(String returnParameter, String returnUnit) {
    return getValue(returnParameter);
  }

  /** {@inheritDoc} */
  @Override
  public double getValue(String returnParameter) {
    if ("smokePoint".equalsIgnoreCase(returnParameter) || "SP".equalsIgnoreCase(returnParameter)) {
      return smokePointMm;
    } else if ("anilinePoint".equalsIgnoreCase(returnParameter)
        || "AP".equalsIgnoreCase(returnParameter)) {
      return anilinePointC;
    } else {
      logger.error("returnParameter not supported: {}", returnParameter);
      return Double.NaN;
    }
  }

  /** {@inheritDoc} */
  @Override
  public String getUnit(String returnParameter) {
    if ("anilinePoint".equalsIgnoreCase(returnParameter)
        || "AP".equalsIgnoreCase(returnParameter)) {
      return "C";
    }
    return "mm";
  }

  /** {@inheritDoc} */
  @Override
  public boolean isOnSpec() {
    if (Double.isNaN(smokePointMm)) {
      return false;
    }
    if (Double.isNaN(minSmokeSpecMm)) {
      return true;
    }
    return smokePointMm >= minSmokeSpecMm;
  }

  /**
   * Sets the correlation coefficients used to estimate the smoke point.
   *
   * @param newSp0 intercept coefficient (default 8.5)
   * @param newSp1 aniline-point coefficient (default 0.325)
   */
  public void setCorrelationCoefficients(double newSp0, double newSp1) {
    this.sp0 = newSp0;
    this.sp1 = newSp1;
  }

  /**
   * Sets an optional minimum smoke-point specification limit in millimetres used by
   * {@link #isOnSpec()}.
   *
   * @param minSmokeMm minimum allowed smoke point in mm
   */
  public void setMinSmokeSpec(double minSmokeMm) {
    this.minSmokeSpecMm = minSmokeMm;
  }

  /**
   * Clears any previously configured minimum smoke-point specification limit.
   */
  public void clearMinSmokeSpec() {
    this.minSmokeSpecMm = Double.NaN;
  }
}
