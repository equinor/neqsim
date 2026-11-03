package neqsim.process.fielddevelopment.wellplanning;

import java.io.Serializable;

/**
 * Wellbore thermal model for producers and injectors using the Ramey (1962) relaxation-distance solution with the Hasan
 * and Kabir (1994) transient time function for heat conduction in the formation.
 *
 * <p>
 * For a linear geothermal profile T_f(z) = T_s + g z and a relaxation distance A (m) the flowing-fluid temperature is
 * </p>
 * <ul>
 * <li>production (fluid flows up from the inlet depth L): T(z) = T_f(z) + g A + (T_in - T_f(L) - g A) exp(-(L -
 * z)/A)</li>
 * <li>injection (fluid flows down from the surface): T(z) = T_f(z) - g A + (T_in - T_f(0) + g A) exp(-z/A)</li>
 * </ul>
 * <p>
 * with A = w cp (k_e + r_to U f(t)) / (2 pi r_to U k_e). Joule-Thomson and friction heating are not included; use a
 * NeqSim enthalpy flash for these effects. The shut-in method is a lumped single-exponential approximation. Validation
 * is by analytic limits (zero and infinite rate) only.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public class WellboreThermalModel implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Flow direction in the wellbore. */
  public enum FlowDirection {
    /** Fluid flows upward from the inlet depth to the surface. */
    PRODUCTION,
    /** Fluid flows downward from the surface to the inlet depth. */
    INJECTION
  }

  private FlowDirection direction = FlowDirection.PRODUCTION;
  private double surfaceTemperatureK = 283.15;
  private double geothermalGradientKPerM = 0.03;
  private double totalDepthM = 3000.0;
  private double massFlowKgPerS = 20.0;
  private double fluidCpJPerKgK = 2500.0;
  private double overallUWPerM2K = 15.0;
  private double tubingOuterRadiusM = 0.0572;
  private double tubingInnerRadiusM = 0.0507;
  private double wellboreRadiusM = 0.15;
  private double formationConductivityWPerMK = 2.2;
  private double formationDiffusivityM2PerS = 1.0e-6;
  private double producingTimeS = 30.0 * 86400.0;
  private double inletTemperatureK = 373.15;
  private double fluidVolumetricHeatCapacity = 2.5e6;
  private double steelVolumetricHeatCapacity = 3.8e6;

  /**
   * Creates a thermal model with generic default parameters.
   */
  public WellboreThermalModel() {
  }

  /**
   * Sets the flow direction.
   *
   * @param direction PRODUCTION or INJECTION
   * @return this model
   */
  public WellboreThermalModel setFlowDirection(FlowDirection direction) {
    this.direction = direction;
    return this;
  }

  /**
   * Sets the undisturbed surface (mudline) temperature.
   *
   * @param temperatureC surface temperature in degrees Celsius
   * @return this model
   */
  public WellboreThermalModel setSurfaceTemperatureC(double temperatureC) {
    this.surfaceTemperatureK = temperatureC + 273.15;
    return this;
  }

  /**
   * Sets the linear geothermal gradient.
   *
   * @param gradientKPerM temperature increase with true vertical depth in K/m
   * @return this model
   */
  public WellboreThermalModel setGeothermalGradient(double gradientKPerM) {
    this.geothermalGradientKPerM = gradientKPerM;
    return this;
  }

  /**
   * Sets the true vertical depth of the inlet (producer) or end point (injector).
   *
   * @param depthM true vertical depth in m
   * @return this model
   */
  public WellboreThermalModel setTotalDepth(double depthM) {
    this.totalDepthM = depthM;
    return this;
  }

  /**
   * Sets the mass flow rate.
   *
   * @param massFlowKgPerS mass flow in kg/s (zero gives a fluid at formation temperature)
   * @return this model
   */
  public WellboreThermalModel setMassFlowRate(double massFlowKgPerS) {
    this.massFlowKgPerS = massFlowKgPerS;
    return this;
  }

  /**
   * Sets the mean fluid heat capacity used in the relaxation distance.
   *
   * @param cpJPerKgK heat capacity in J/(kg K)
   * @return this model
   */
  public WellboreThermalModel setFluidHeatCapacity(double cpJPerKgK) {
    this.fluidCpJPerKgK = cpJPerKgK;
    return this;
  }

  /**
   * Sets the overall heat-transfer coefficient from the flowing fluid to the cement/formation interface, referenced to
   * the tubing outer radius.
   *
   * @param uWPerM2K overall coefficient in W/(m2 K)
   * @return this model
   */
  public WellboreThermalModel setOverallHeatTransferCoefficient(double uWPerM2K) {
    this.overallUWPerM2K = uWPerM2K;
    return this;
  }

  /**
   * Sets tubing radii.
   *
   * @param innerRadiusM tubing inner radius in m
   * @param outerRadiusM tubing outer radius in m
   * @return this model
   */
  public WellboreThermalModel setTubingRadii(double innerRadiusM, double outerRadiusM) {
    this.tubingInnerRadiusM = innerRadiusM;
    this.tubingOuterRadiusM = outerRadiusM;
    return this;
  }

  /**
   * Sets the wellbore (borehole) radius at the cement/formation interface.
   *
   * @param radiusM borehole radius in m
   * @return this model
   */
  public WellboreThermalModel setWellboreRadius(double radiusM) {
    this.wellboreRadiusM = radiusM;
    return this;
  }

  /**
   * Sets formation thermal properties.
   *
   * @param conductivityWPerMK formation conductivity in W/(m K)
   * @param diffusivityM2PerS formation thermal diffusivity in m2/s
   * @return this model
   */
  public WellboreThermalModel setFormationProperties(double conductivityWPerMK, double diffusivityM2PerS) {
    this.formationConductivityWPerMK = conductivityWPerMK;
    this.formationDiffusivityM2PerS = diffusivityM2PerS;
    return this;
  }

  /**
   * Sets the production or injection time used in the transient time function.
   *
   * @param days time since start of flow in days
   * @return this model
   */
  public WellboreThermalModel setFlowingTimeDays(double days) {
    this.producingTimeS = days * 86400.0;
    return this;
  }

  /**
   * Sets the fluid temperature at the inlet end: bottom-hole for a producer, surface injection temperature for an
   * injector.
   *
   * @param temperatureC inlet temperature in degrees Celsius
   * @return this model
   */
  public WellboreThermalModel setInletTemperatureC(double temperatureC) {
    this.inletTemperatureK = temperatureC + 273.15;
    return this;
  }

  /**
   * Sets volumetric heat capacities used by the lumped shut-in model.
   *
   * @param fluidJPerM3K fluid rho cp in J/(m3 K)
   * @param steelJPerM3K steel rho cp in J/(m3 K)
   * @return this model
   */
  public WellboreThermalModel setShutInHeatCapacities(double fluidJPerM3K, double steelJPerM3K) {
    this.fluidVolumetricHeatCapacity = fluidJPerM3K;
    this.steelVolumetricHeatCapacity = steelJPerM3K;
    return this;
  }

  /**
   * Computes the dimensionless time function f(t) of Hasan and Kabir for transient radial conduction in the formation.
   *
   * @return time function f(t_D), t_D = alpha t / r_wb^2
   */
  public double getTimeFunction() {
    double td = formationDiffusivityM2PerS * producingTimeS / (wellboreRadiusM * wellboreRadiusM);
    if (td < 1.0e-10) {
      return 0.0;
    }
    if (td <= 1.5) {
      return 1.1281 * Math.sqrt(td) * (1.0 - 0.3 * Math.sqrt(td));
    }
    return (0.4063 + 0.5 * Math.log(td)) * (1.0 + 0.6 / td);
  }

  /**
   * Returns the relaxation distance A of the Ramey solution.
   *
   * @return relaxation distance in m (zero if the flow rate is zero)
   */
  public double getRelaxationDistance() {
    double w = Math.abs(massFlowKgPerS);
    double ke = formationConductivityWPerMK;
    double rU = tubingOuterRadiusM * overallUWPerM2K;
    return w * fluidCpJPerKgK * (ke + rU * getTimeFunction()) / (2.0 * Math.PI * rU * ke);
  }

  /**
   * Returns the undisturbed formation temperature.
   *
   * @param depthM true vertical depth in m
   * @return formation temperature in K
   */
  public double getFormationTemperatureK(double depthM) {
    return surfaceTemperatureK + geothermalGradientKPerM * depthM;
  }

  /**
   * Returns the flowing fluid temperature at a depth.
   *
   * @param depthM true vertical depth in m, between 0 and the total depth
   * @return fluid temperature in K
   */
  public double temperatureAt(double depthM) {
    double a = getRelaxationDistance();
    double g = geothermalGradientKPerM;
    double tf = getFormationTemperatureK(depthM);
    if (a < 1.0e-9) {
      return tf;
    }
    if (direction == FlowDirection.PRODUCTION) {
      double tfBottom = getFormationTemperatureK(totalDepthM);
      return tf + g * a + (inletTemperatureK - tfBottom - g * a) * Math.exp(-(totalDepthM - depthM) / a);
    }
    double tfTop = getFormationTemperatureK(0.0);
    return tf - g * a + (inletTemperatureK - tfTop + g * a) * Math.exp(-depthM / a);
  }

  /**
   * Returns the flowing temperature at several depths.
   *
   * @param depthsM true vertical depths in m
   * @return temperatures in K
   */
  public double[] temperatureProfile(double[] depthsM) {
    double[] t = new double[depthsM.length];
    for (int i = 0; i < t.length; i++) {
      t[i] = temperatureAt(depthsM[i]);
    }
    return t;
  }

  /**
   * Computes the overall heat-transfer coefficient referenced to the tubing outer radius from a series of radial
   * conduction layers.
   *
   * @param innerFilmCoefficient fluid-side film coefficient in W/(m2 K), at the tubing inner radius
   * @param radii increasing layer boundary radii in m, starting at the tubing inner radius (length n+1)
   * @param conductivities layer thermal conductivities in W/(m K) (length n), for example tubing wall, insulation,
   * annulus fluid, casing and cement
   * @param outerIndex index into radii of the tubing outer radius used as reference
   * @return overall coefficient in W/(m2 K) based on radii[outerIndex]
   * @throws IllegalArgumentException if array lengths or radii are inconsistent
   */
  public static double overallHeatTransferCoefficient(double innerFilmCoefficient, double[] radii,
      double[] conductivities, int outerIndex) {
    if (radii.length != conductivities.length + 1 || outerIndex < 0 || outerIndex >= radii.length) {
      throw new IllegalArgumentException("radii must have one more entry than conductivities and outerIndex in range");
    }
    double resistancePerLength = 1.0 / (2.0 * Math.PI * radii[0] * innerFilmCoefficient);
    for (int i = 0; i < conductivities.length; i++) {
      if (radii[i + 1] <= radii[i]) {
        throw new IllegalArgumentException("radii must be strictly increasing");
      }
      resistancePerLength += Math.log(radii[i + 1] / radii[i]) / (2.0 * Math.PI * conductivities[i]);
    }
    return 1.0 / (2.0 * Math.PI * radii[outerIndex] * resistancePerLength);
  }

  /**
   * Returns the lumped shut-in time constant of the fluid-filled tubing.
   *
   * @return time constant in s
   */
  public double getShutInTimeConstant() {
    double ke = formationConductivityWPerMK;
    double rto = tubingOuterRadiusM;
    double uEff = 1.0 / (1.0 / overallUWPerM2K + rto * getTimeFunction() / ke);
    double capPerLength = Math.PI
        * (fluidVolumetricHeatCapacity * tubingInnerRadiusM * tubingInnerRadiusM + steelVolumetricHeatCapacity
            * (tubingOuterRadiusM * tubingOuterRadiusM - tubingInnerRadiusM * tubingInnerRadiusM));
    return capPerLength / (2.0 * Math.PI * rto * uEff);
  }

  /**
   * Returns the shut-in temperature of the fluid at a depth after a given shut-in time.
   *
   * @param depthM true vertical depth in m
   * @param initialTemperatureK flowing temperature at the moment of shut-in in K
   * @param shutInTimeHours time since shut-in in hours
   * @return temperature in K, decaying exponentially to the formation temperature
   */
  public double shutInTemperatureK(double depthM, double initialTemperatureK, double shutInTimeHours) {
    double tf = getFormationTemperatureK(depthM);
    return tf + (initialTemperatureK - tf) * Math.exp(-shutInTimeHours * 3600.0 / getShutInTimeConstant());
  }

  /**
   * Returns the time for the shut-in fluid to cool to a threshold temperature, for example a hydrate formation
   * temperature.
   *
   * @param depthM true vertical depth in m
   * @param initialTemperatureK flowing temperature at the moment of shut-in in K
   * @param thresholdTemperatureK threshold temperature in K
   * @return cooldown time in hours; zero if already below the threshold and positive infinity if the formation
   * temperature is above the threshold (never reached)
   */
  public double timeToReachTemperatureHours(double depthM, double initialTemperatureK, double thresholdTemperatureK) {
    double tf = getFormationTemperatureK(depthM);
    if (initialTemperatureK <= thresholdTemperatureK) {
      return 0.0;
    }
    if (tf >= thresholdTemperatureK) {
      return Double.POSITIVE_INFINITY;
    }
    double ratio = (thresholdTemperatureK - tf) / (initialTemperatureK - tf);
    return -getShutInTimeConstant() * Math.log(ratio) / 3600.0;
  }
}
