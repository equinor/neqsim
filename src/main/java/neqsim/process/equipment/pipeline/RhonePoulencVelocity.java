package neqsim.process.equipment.pipeline;

import java.io.Serializable;

/**
 * Rhone-Poulenc maximum velocity calculation for gas pipes.
 *
 * <p>
 * The Rhone-Poulenc curves are empirical correlations widely used in the European oil and gas
 * industry for determining maximum allowable gas velocities in pipes to prevent erosion and
 * vibration. They relate maximum velocity to gas density through a power-law relationship.
 * </p>
 *
 * <p>
 * The general correlation is:
 * </p>
 *
 * $$V_{max} = C \cdot \rho^{-n}$$
 *
 * <p>
 * where:
 * </p>
 * <ul>
 * <li>$V_{max}$ is the maximum allowable velocity in m/s</li>
 * <li>$\rho$ is the gas density in kg/m3</li>
 * <li>$C$ is the service-dependent constant</li>
 * <li>$n$ is the density exponent (nominally 0.44)</li>
 * </ul>
 *
 * <p>
 * The class supports multiple service types with different velocity limits:
 * </p>
 *
 * <table>
 * <caption>Service type parameters for Rhone-Poulenc velocity calculation</caption>
 * <tr>
 * <th>Service Type</th>
 * <th>C Factor</th>
 * <th>V_max upper (m/s)</th>
 * <th>V_min lower (m/s)</th>
 * </tr>
 * <tr>
 * <td>Non-corrosive gas</td>
 * <td>60</td>
 * <td>60</td>
 * <td>3</td>
 * </tr>
 * <tr>
 * <td>Corrosive gas (CO2/H2S)</td>
 * <td>30</td>
 * <td>30</td>
 * <td>2</td>
 * </tr>
 * </table>
 *
 * <p>
 * Alternatively, the class provides tabulated data points from the standard Rhone-Poulenc curves
 * with log-log interpolation for higher accuracy.
 * </p>
 *
 * <p>
 * Comparison with API RP 14E: The API RP 14E formula uses $V_e = C / \sqrt{\rho}$ (exponent =
 * 0.5), while Rhone-Poulenc uses an exponent of approximately 0.44, giving a less aggressive
 * velocity reduction at higher densities.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 * @see AdiabaticPipe#getRhonePoulencMaxVelocity()
 * @see PipeBeggsAndBrills#getRhonePoulencMaxVelocity()
 */
public class RhonePoulencVelocity implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /**
   * Service type for Rhone-Poulenc velocity calculation.
   *
   * <p>
   * Determines the C-factor, velocity bounds, and whether corrosion allowances apply.
   * </p>
   */
  public enum ServiceType {
    /**
     * Non-corrosive gas service (dry gas, sweet gas). Uses C=60, V_max=60 m/s, V_min=3 m/s.
     */
    NON_CORROSIVE_GAS(60.0, 0.44, 60.0, 3.0),

    /**
     * Corrosive gas service (wet gas, sour gas with CO2/H2S). Uses C=30, V_max=30 m/s, V_min=2
     * m/s.
     */
    CORROSIVE_GAS(30.0, 0.44, 30.0, 2.0);

    /** The C-factor in the correlation V_max = C * rho^(-n). */
    private final double cFactor;

    /** The density exponent n. */
    private final double exponent;

    /** Upper velocity limit in m/s. */
    private final double maxVelocityLimit;

    /** Lower velocity limit in m/s. */
    private final double minVelocityLimit;

    /**
     * Construct a ServiceType enum value.
     *
     * @param cFactor the C-factor constant
     * @param exponent the density exponent
     * @param maxVelocityLimit upper velocity limit in m/s
     * @param minVelocityLimit lower velocity limit in m/s
     */
    ServiceType(double cFactor, double exponent, double maxVelocityLimit,
        double minVelocityLimit) {
      this.cFactor = cFactor;
      this.exponent = exponent;
      this.maxVelocityLimit = maxVelocityLimit;
      this.minVelocityLimit = minVelocityLimit;
    }

    /**
     * Get the C-factor for this service type.
     *
     * @return C-factor value
     */
    public double getCFactor() {
      return cFactor;
    }

    /**
     * Get the density exponent for this service type.
     *
     * @return density exponent
     */
    public double getExponent() {
      return exponent;
    }

    /**
     * Get the upper velocity limit for this service type.
     *
     * @return maximum velocity limit in m/s
     */
    public double getMaxVelocityLimit() {
      return maxVelocityLimit;
    }

    /**
     * Get the lower velocity limit for this service type.
     *
     * @return minimum velocity limit in m/s
     */
    public double getMinVelocityLimit() {
      return minVelocityLimit;
    }
  }

  /**
   * Tabulated gas density values (kg/m3) from the standard Rhone-Poulenc curve for non-corrosive
   * gas service. Used for log-log interpolation.
   */
  private static final double[] TABLE_DENSITY_NON_CORROSIVE =
      {1.0, 2.0, 5.0, 10.0, 20.0, 50.0, 100.0, 200.0, 500.0};

  /**
   * Tabulated maximum velocity values (m/s) corresponding to the density points for non-corrosive
   * gas service. Used for log-log interpolation.
   */
  private static final double[] TABLE_VELOCITY_NON_CORROSIVE =
      {60.0, 45.0, 30.0, 22.0, 16.0, 11.0, 8.0, 5.5, 3.5};

  /**
   * Tabulated gas density values (kg/m3) from the standard Rhone-Poulenc curve for corrosive gas
   * service. Used for log-log interpolation.
   */
  private static final double[] TABLE_DENSITY_CORROSIVE =
      {1.0, 2.0, 5.0, 10.0, 20.0, 50.0, 100.0, 200.0, 500.0};

  /**
   * Tabulated maximum velocity values (m/s) corresponding to the density points for corrosive gas
   * service. Used for log-log interpolation.
   */
  private static final double[] TABLE_VELOCITY_CORROSIVE =
      {30.0, 23.0, 15.0, 11.0, 8.0, 5.5, 4.0, 3.0, 2.0};

  /** The selected service type. */
  private ServiceType serviceType = ServiceType.NON_CORROSIVE_GAS;

  /** Whether to use tabulated data with log-log interpolation (true) or power-law formula. */
  private boolean useInterpolation = false;

  /** Optional custom C-factor (overrides service type default if positive). */
  private double customCFactor = -1.0;

  /** Optional custom exponent (overrides service type default if positive). */
  private double customExponent = -1.0;

  /**
   * Construct a RhonePoulencVelocity calculator with default non-corrosive gas settings.
   */
  public RhonePoulencVelocity() {
    this(ServiceType.NON_CORROSIVE_GAS);
  }

  /**
   * Construct a RhonePoulencVelocity calculator with the specified service type.
   *
   * @param serviceType the gas service type determining velocity limits
   */
  public RhonePoulencVelocity(ServiceType serviceType) {
    this.serviceType = serviceType;
  }

  /**
   * Calculate the maximum allowable gas velocity using the Rhone-Poulenc power-law formula.
   *
   * <p>
   * The formula is: $V_{max} = C \cdot \rho^{-n}$, bounded by upper and lower velocity limits.
   * </p>
   *
   * @param gasDensity gas density in kg/m3 (must be positive)
   * @return maximum allowable velocity in m/s
   * @throws IllegalArgumentException if gasDensity is not positive
   */
  public double getMaxVelocity(double gasDensity) {
    if (gasDensity <= 0) {
      throw new IllegalArgumentException("Gas density must be positive, got: " + gasDensity);
    }

    if (useInterpolation) {
      return getMaxVelocityInterpolated(gasDensity);
    }

    double cFactor = customCFactor > 0 ? customCFactor : serviceType.getCFactor();
    double exponent = customExponent > 0 ? customExponent : serviceType.getExponent();

    double velocity = cFactor * Math.pow(gasDensity, -exponent);

    // Apply bounds
    velocity = Math.min(velocity, serviceType.getMaxVelocityLimit());
    velocity = Math.max(velocity, serviceType.getMinVelocityLimit());

    return velocity;
  }

  /**
   * Calculate the maximum allowable gas velocity using log-log interpolation of tabulated
   * Rhone-Poulenc curve data points.
   *
   * <p>
   * This method provides higher accuracy than the power-law approximation by interpolating between
   * standard data points from the published Rhone-Poulenc curves.
   * </p>
   *
   * @param gasDensity gas density in kg/m3 (must be positive)
   * @return maximum allowable velocity in m/s
   * @throws IllegalArgumentException if gasDensity is not positive
   */
  public double getMaxVelocityInterpolated(double gasDensity) {
    if (gasDensity <= 0) {
      throw new IllegalArgumentException("Gas density must be positive, got: " + gasDensity);
    }

    double[] densities;
    double[] velocities;

    if (serviceType == ServiceType.CORROSIVE_GAS) {
      densities = TABLE_DENSITY_CORROSIVE;
      velocities = TABLE_VELOCITY_CORROSIVE;
    } else {
      densities = TABLE_DENSITY_NON_CORROSIVE;
      velocities = TABLE_VELOCITY_NON_CORROSIVE;
    }

    return logLogInterpolate(densities, velocities, gasDensity);
  }

  /**
   * Perform log-log interpolation between tabulated data points.
   *
   * <p>
   * If the input value is outside the table range, log-log extrapolation is used based on the
   * nearest two data points, bounded by the service type velocity limits.
   * </p>
   *
   * @param xTable array of x values (must be sorted ascending)
   * @param yTable array of corresponding y values
   * @param x the x value to interpolate at
   * @return interpolated y value
   */
  private double logLogInterpolate(double[] xTable, double[] yTable, double x) {
    int n = xTable.length;
    double logX = Math.log(x);

    // Below table range - extrapolate from first two points
    if (x <= xTable[0]) {
      double logX0 = Math.log(xTable[0]);
      double logX1 = Math.log(xTable[1]);
      double logY0 = Math.log(yTable[0]);
      double logY1 = Math.log(yTable[1]);
      double slope = (logY1 - logY0) / (logX1 - logX0);
      double logY = logY0 + slope * (logX - logX0);
      double velocity = Math.exp(logY);
      return Math.min(velocity, serviceType.getMaxVelocityLimit());
    }

    // Above table range - extrapolate from last two points
    if (x >= xTable[n - 1]) {
      double logXn2 = Math.log(xTable[n - 2]);
      double logXn1 = Math.log(xTable[n - 1]);
      double logYn2 = Math.log(yTable[n - 2]);
      double logYn1 = Math.log(yTable[n - 1]);
      double slope = (logYn1 - logYn2) / (logXn1 - logXn2);
      double logY = logYn1 + slope * (logX - logXn1);
      double velocity = Math.exp(logY);
      return Math.max(velocity, serviceType.getMinVelocityLimit());
    }

    // Within table range - interpolate
    for (int i = 0; i < n - 1; i++) {
      if (x >= xTable[i] && x <= xTable[i + 1]) {
        double logX0 = Math.log(xTable[i]);
        double logX1 = Math.log(xTable[i + 1]);
        double logY0 = Math.log(yTable[i]);
        double logY1 = Math.log(yTable[i + 1]);
        double slope = (logY1 - logY0) / (logX1 - logX0);
        double logY = logY0 + slope * (logX - logX0);
        return Math.exp(logY);
      }
    }

    // Should not reach here; return power-law fallback
    return serviceType.getCFactor() * Math.pow(x, -serviceType.getExponent());
  }

  /**
   * Static convenience method to calculate Rhone-Poulenc maximum velocity for non-corrosive gas
   * service using the power-law formula.
   *
   * @param gasDensity gas density in kg/m3
   * @return maximum allowable velocity in m/s
   */
  public static double getMaxVelocityNonCorrosive(double gasDensity) {
    return new RhonePoulencVelocity(ServiceType.NON_CORROSIVE_GAS).getMaxVelocity(gasDensity);
  }

  /**
   * Static convenience method to calculate Rhone-Poulenc maximum velocity for corrosive gas service
   * using the power-law formula.
   *
   * @param gasDensity gas density in kg/m3
   * @return maximum allowable velocity in m/s
   */
  public static double getMaxVelocityCorrosive(double gasDensity) {
    return new RhonePoulencVelocity(ServiceType.CORROSIVE_GAS).getMaxVelocity(gasDensity);
  }

  /**
   * Get the service type.
   *
   * @return the configured service type
   */
  public ServiceType getServiceType() {
    return serviceType;
  }

  /**
   * Set the service type.
   *
   * @param serviceType the service type to use
   */
  public void setServiceType(ServiceType serviceType) {
    this.serviceType = serviceType;
  }

  /**
   * Check whether tabulated interpolation is enabled.
   *
   * @return true if using log-log interpolation of tabulated data
   */
  public boolean isUseInterpolation() {
    return useInterpolation;
  }

  /**
   * Set whether to use tabulated interpolation or power-law formula.
   *
   * @param useInterpolation true to use log-log interpolation, false for power-law formula
   */
  public void setUseInterpolation(boolean useInterpolation) {
    this.useInterpolation = useInterpolation;
  }

  /**
   * Set a custom C-factor that overrides the service type default.
   *
   * @param cFactor custom C-factor (use negative value to revert to service type default)
   */
  public void setCustomCFactor(double cFactor) {
    this.customCFactor = cFactor;
  }

  /**
   * Get the currently effective C-factor.
   *
   * @return the C-factor in use (custom if set, otherwise service type default)
   */
  public double getEffectiveCFactor() {
    return customCFactor > 0 ? customCFactor : serviceType.getCFactor();
  }

  /**
   * Set a custom density exponent that overrides the service type default.
   *
   * @param exponent custom exponent (use negative value to revert to service type default)
   */
  public void setCustomExponent(double exponent) {
    this.customExponent = exponent;
  }

  /**
   * Get the currently effective density exponent.
   *
   * @return the exponent in use (custom if set, otherwise service type default)
   */
  public double getEffectiveExponent() {
    return customExponent > 0 ? customExponent : serviceType.getExponent();
  }

  /**
   * Get description of the Rhone-Poulenc velocity method and its current parameters.
   *
   * @return descriptive string including service type, C-factor, and exponent
   */
  public String getDescription() {
    StringBuilder sb = new StringBuilder();
    sb.append("Rhone-Poulenc Max Velocity Method");
    sb.append(" [Service: ").append(serviceType.name());
    sb.append(", C=").append(String.format("%.1f", getEffectiveCFactor()));
    sb.append(", n=").append(String.format("%.2f", getEffectiveExponent()));
    sb.append(", V_max=").append(String.format("%.0f", serviceType.getMaxVelocityLimit()));
    sb.append(" m/s, V_min=").append(String.format("%.0f", serviceType.getMinVelocityLimit()));
    sb.append(" m/s]");
    if (useInterpolation) {
      sb.append(" (using tabulated interpolation)");
    }
    return sb.toString();
  }
}
