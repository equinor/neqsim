package neqsim.process.mechanicaldesign.subsea;

import java.io.Serializable;
import neqsim.process.equipment.subsea.UmbilicalFluid;

/**
 * Hydraulics of a liquid-filled umbilical tube or hose: steady friction pressure drop, hydrostatic head, flow capacity
 * for a given pressure budget, and the dynamic response of a long control line.
 *
 * <p>
 * Steady flow uses the Darcy-Weisbach equation with the laminar friction factor 64/Re, the Haaland explicit form of
 * Colebrook for turbulent flow and a linear blend over 2300 &lt; Re &lt; 4000. Line response is modelled as a
 * distributed resistance-compliance line: the laminar resistance {@code R = 128 mu L / (pi D^4)} and the compliance of
 * the liquid (bulk modulus) in series with the elastic expansion of the tube wall. A pressure step at one end of the
 * closed line is solved with the exact diffusion series, and the acoustic transit time L/c is reported as a lower
 * bound.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public final class UmbilicalHydraulicsCalculator {

  /** Gravitational acceleration in m/s2. */
  public static final double GRAVITY = 9.80665;

  /** Default absolute roughness of drawn steel tube in mm. */
  public static final double DEFAULT_ROUGHNESS_MM = 0.002;

  /** Poisson ratio used for the tube wall expansion. */
  private static final double POISSON_RATIO = 0.3;

  /**
   * Result of a steady line calculation.
   */
  public static class LineResult implements Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1000L;

    /** Mean velocity in m/s. */
    private final double velocityMs;
    /** Reynolds number. */
    private final double reynolds;
    /** Darcy friction factor. */
    private final double frictionFactor;
    /** Friction pressure drop in bar. */
    private final double frictionDropBar;
    /** Hydrostatic pressure change over the elevation drop in bar. */
    private final double hydrostaticBar;
    /** Flow regime description. */
    private final String regime;

    /**
     * Constructor.
     *
     * @param velocityMs mean velocity in m/s
     * @param reynolds Reynolds number
     * @param frictionFactor Darcy friction factor
     * @param frictionDropBar friction pressure drop in bar
     * @param hydrostaticBar hydrostatic pressure change in bar
     * @param regime flow regime
     */
    public LineResult(double velocityMs, double reynolds, double frictionFactor, double frictionDropBar,
        double hydrostaticBar, String regime) {
      this.velocityMs = velocityMs;
      this.reynolds = reynolds;
      this.frictionFactor = frictionFactor;
      this.frictionDropBar = frictionDropBar;
      this.hydrostaticBar = hydrostaticBar;
      this.regime = regime;
    }

    /**
     * Get mean velocity.
     *
     * @return velocity in m/s
     */
    public double getVelocityMs() {
      return velocityMs;
    }

    /**
     * Get Reynolds number.
     *
     * @return Reynolds number
     */
    public double getReynolds() {
      return reynolds;
    }

    /**
     * Get Darcy friction factor.
     *
     * @return friction factor
     */
    public double getFrictionFactor() {
      return frictionFactor;
    }

    /**
     * Get friction pressure drop along the line.
     *
     * @return pressure drop in bar
     */
    public double getFrictionDropBar() {
      return frictionDropBar;
    }

    /**
     * Get the hydrostatic pressure gain over the elevation drop (positive when the delivery point is below the supply
     * point).
     *
     * @return hydrostatic pressure change in bar
     */
    public double getHydrostaticBar() {
      return hydrostaticBar;
    }

    /**
     * Get the supply pressure needed for a given delivery pressure.
     *
     * @param deliveryPressureBara pressure required at the delivery end in bara
     * @return required supply pressure in bara
     */
    public double getRequiredSupplyPressureBara(double deliveryPressureBara) {
      return deliveryPressureBara + frictionDropBar - hydrostaticBar;
    }

    /**
     * Get the delivery pressure obtained from a given supply pressure.
     *
     * @param supplyPressureBara supply pressure in bara
     * @return delivery pressure in bara
     */
    public double getDeliveryPressureBara(double supplyPressureBara) {
      return supplyPressureBara - frictionDropBar + hydrostaticBar;
    }

    /**
     * Get the flow regime.
     *
     * @return laminar, transitional or turbulent
     */
    public String getRegime() {
      return regime;
    }
  }

  /**
   * Result of a line response calculation.
   */
  public static class ResponseResult implements Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1000L;

    /** Acoustic velocity in the fluid-filled tube in m/s. */
    private final double acousticVelocityMs;
    /** Acoustic transit time in s. */
    private final double transitTimeS;
    /** Diffusion time constant in s. */
    private final double timeConstantS;
    /** Time for the far end of the closed line to reach the target fraction in s. */
    private final double timeToFractionS;
    /** Target fraction of the step. */
    private final double fraction;
    /** True if inertia can be neglected (diffusion dominated). */
    private final boolean diffusionDominated;

    /**
     * Constructor.
     *
     * @param acousticVelocityMs acoustic velocity in m/s
     * @param transitTimeS acoustic transit time in s
     * @param timeConstantS diffusion time constant in s
     * @param timeToFractionS time to reach the fraction in s
     * @param fraction target fraction
     * @param diffusionDominated true if the diffusion model is valid
     */
    public ResponseResult(double acousticVelocityMs, double transitTimeS, double timeConstantS, double timeToFractionS,
        double fraction, boolean diffusionDominated) {
      this.acousticVelocityMs = acousticVelocityMs;
      this.transitTimeS = transitTimeS;
      this.timeConstantS = timeConstantS;
      this.timeToFractionS = timeToFractionS;
      this.fraction = fraction;
      this.diffusionDominated = diffusionDominated;
    }

    /**
     * Get acoustic velocity.
     *
     * @return velocity in m/s
     */
    public double getAcousticVelocityMs() {
      return acousticVelocityMs;
    }

    /**
     * Get acoustic transit time.
     *
     * @return transit time in s
     */
    public double getTransitTimeS() {
      return transitTimeS;
    }

    /**
     * Get the diffusion time constant 4RC/pi^2.
     *
     * @return time constant in s
     */
    public double getTimeConstantS() {
      return timeConstantS;
    }

    /**
     * Get the time for the far end to reach the target fraction of the step.
     *
     * @return time in s, never less than the acoustic transit time
     */
    public double getTimeToFractionS() {
      return timeToFractionS;
    }

    /**
     * Get the target fraction.
     *
     * @return fraction of the pressure step
     */
    public double getFraction() {
      return fraction;
    }

    /**
     * Check whether the diffusion model is valid (time constant at least five transit times).
     *
     * @return true if inertia is negligible
     */
    public boolean isDiffusionDominated() {
      return diffusionDominated;
    }
  }

  /**
   * Private constructor for utility class.
   */
  private UmbilicalHydraulicsCalculator() {
  }

  /**
   * Darcy friction factor.
   *
   * @param reynolds Reynolds number
   * @param relativeRoughness absolute roughness divided by inner diameter
   * @return Darcy friction factor
   */
  public static double frictionFactor(double reynolds, double relativeRoughness) {
    if (reynolds <= 0.0) {
      return 0.0;
    }
    if (reynolds < 2300.0) {
      return 64.0 / reynolds;
    }
    double turbulentAt4000 = haaland(4000.0, relativeRoughness);
    if (reynolds < 4000.0) {
      double laminarAt2300 = 64.0 / 2300.0;
      double w = (reynolds - 2300.0) / 1700.0;
      return laminarAt2300 + w * (turbulentAt4000 - laminarAt2300);
    }
    return haaland(reynolds, relativeRoughness);
  }

  /**
   * Haaland explicit friction factor.
   *
   * @param reynolds Reynolds number
   * @param relativeRoughness relative roughness
   * @return Darcy friction factor
   */
  private static double haaland(double reynolds, double relativeRoughness) {
    double term = Math.pow(relativeRoughness / 3.7, 1.11) + 6.9 / reynolds;
    double inv = -1.8 * Math.log10(term);
    return 1.0 / (inv * inv);
  }

  /**
   * Steady flow in a liquid-filled line.
   *
   * @param fluid line fluid
   * @param innerDiameterMm inner diameter in mm
   * @param lengthM line length in m
   * @param flowM3PerS volumetric flow in m3/s (sign ignored)
   * @param temperatureK fluid temperature in K
   * @param roughnessMm absolute wall roughness in mm
   * @param elevationDropM elevation of the supply end above the delivery end in m (positive when the delivery point is
   * deeper)
   * @return steady flow result
   */
  public static LineResult calculate(UmbilicalFluid fluid, double innerDiameterMm, double lengthM, double flowM3PerS,
      double temperatureK, double roughnessMm, double elevationDropM) {
    double d = innerDiameterMm / 1000.0;
    double area = Math.PI * d * d / 4.0;
    double velocity = Math.abs(flowM3PerS) / area;
    double mu = fluid.getViscosityPas(temperatureK);
    double re = fluid.getDensityKgM3() * velocity * d / mu;
    double f = frictionFactor(re, roughnessMm / innerDiameterMm);
    double dpPa = f * lengthM / d * fluid.getDensityKgM3() * velocity * velocity / 2.0;
    double hydrostaticPa = fluid.getDensityKgM3() * GRAVITY * elevationDropM;
    String regime = re < 2300.0 ? "laminar" : (re < 4000.0 ? "transitional" : "turbulent");
    return new LineResult(velocity, re, f, dpPa / 1.0e5, hydrostaticPa / 1.0e5, regime);
  }

  /**
   * Flow rate that gives a specified friction pressure drop.
   *
   * @param fluid line fluid
   * @param innerDiameterMm inner diameter in mm
   * @param lengthM line length in m
   * @param frictionDropBar allowed friction pressure drop in bar
   * @param temperatureK fluid temperature in K
   * @param roughnessMm absolute wall roughness in mm
   * @return volumetric flow in m3/s
   */
  public static double flowForFrictionDrop(UmbilicalFluid fluid, double innerDiameterMm, double lengthM,
      double frictionDropBar, double temperatureK, double roughnessMm) {
    if (frictionDropBar <= 0.0) {
      return 0.0;
    }
    double lo = 0.0;
    double hi = 1.0;
    for (int i = 0; i < 200; i++) {
      double mid = 0.5 * (lo + hi);
      double dp = calculate(fluid, innerDiameterMm, lengthM, mid, temperatureK, roughnessMm, 0.0).getFrictionDropBar();
      if (dp > frictionDropBar) {
        hi = mid;
      } else {
        lo = mid;
      }
    }
    return 0.5 * (lo + hi);
  }

  /**
   * Volume compliance of a liquid-filled tube per unit pressure, from liquid compressibility and elastic expansion of
   * the wall (thin-wall closed-end tube).
   *
   * @param fluid line fluid
   * @param innerDiameterMm inner diameter in mm
   * @param wallThicknessMm wall thickness in mm
   * @param wallModulusPa elastic modulus of the wall in Pa (about 200e9 for steel, much lower for reinforced
   * thermoplastic hose)
   * @param lengthM line length in m
   * @return compliance in m3/Pa
   */
  public static double lineCompliance(UmbilicalFluid fluid, double innerDiameterMm, double wallThicknessMm,
      double wallModulusPa, double lengthM) {
    double d = innerDiameterMm / 1000.0;
    double t = wallThicknessMm / 1000.0;
    double volume = Math.PI * d * d / 4.0 * lengthM;
    double wallStrainPerPa = d / (t * wallModulusPa)
        * ((1.0 - POISSON_RATIO / 2.0) + (1.0 - 2.0 * POISSON_RATIO) / 4.0);
    return volume * (1.0 / fluid.getBulkModulusPa() + wallStrainPerPa);
  }

  /**
   * Response of the far end of a closed liquid-filled line to a pressure step at the supply end.
   *
   * @param fluid line fluid
   * @param innerDiameterMm inner diameter in mm
   * @param wallThicknessMm wall thickness in mm
   * @param wallModulusPa elastic modulus of the wall in Pa
   * @param lengthM line length in m
   * @param temperatureK fluid temperature in K
   * @param fraction target fraction of the step (0 &lt; fraction &lt; 1), for example 0.9
   * @return response result
   */
  public static ResponseResult stepResponse(UmbilicalFluid fluid, double innerDiameterMm, double wallThicknessMm,
      double wallModulusPa, double lengthM, double temperatureK, double fraction) {
    if (fraction <= 0.0 || fraction >= 1.0) {
      throw new IllegalArgumentException("fraction must be between 0 and 1");
    }
    double d = innerDiameterMm / 1000.0;
    double mu = fluid.getViscosityPas(temperatureK);
    double resistance = 128.0 * mu * lengthM / (Math.PI * Math.pow(d, 4));
    double compliance = lineCompliance(fluid, innerDiameterMm, wallThicknessMm, wallModulusPa, lengthM);
    double tau = 4.0 * resistance * compliance / (Math.PI * Math.PI);

    double effectiveBulk = (Math.PI * d * d / 4.0 * lengthM) / compliance;
    double c = Math.sqrt(effectiveBulk / fluid.getDensityKgM3());
    double transit = lengthM / c;

    double lo = 0.0;
    double hi = 40.0 * tau;
    for (int i = 0; i < 100; i++) {
      double mid = 0.5 * (lo + hi);
      if (closedEndStep(mid / tau) < fraction) {
        lo = mid;
      } else {
        hi = mid;
      }
    }
    double tDiff = 0.5 * (lo + hi);
    return new ResponseResult(c, transit, tau, Math.max(tDiff, transit), fraction, tau >= 5.0 * transit);
  }

  /**
   * Normalised pressure at the closed end of a line after a step at the other end.
   *
   * @param theta time divided by the diffusion time constant 4RC/pi^2
   * @return fraction of the step reached, between 0 and 1
   */
  private static double closedEndStep(double theta) {
    double sum = 0.0;
    for (int n = 0; n < 2000; n++) {
      double k = 2.0 * n + 1.0;
      double term = Math.exp(-k * k * theta) / k * (n % 2 == 0 ? 1.0 : -1.0);
      sum += term;
      if (Math.abs(term) < 1.0e-14) {
        break;
      }
    }
    return 1.0 - 4.0 / Math.PI * sum;
  }

  /**
   * Quasi-steady time to displace a given volume (for example an actuator stroke) through the line with a fixed
   * available pressure difference.
   *
   * @param fluid line fluid
   * @param innerDiameterMm inner diameter in mm
   * @param lengthM line length in m
   * @param volumeM3 volume to deliver in m3
   * @param availableDropBar friction pressure difference available over the line in bar
   * @param temperatureK fluid temperature in K
   * @param roughnessMm absolute wall roughness in mm
   * @return time in s, infinity if no pressure difference is available
   */
  public static double volumeDeliveryTimeS(UmbilicalFluid fluid, double innerDiameterMm, double lengthM,
      double volumeM3, double availableDropBar, double temperatureK, double roughnessMm) {
    double q = flowForFrictionDrop(fluid, innerDiameterMm, lengthM, availableDropBar, temperatureK, roughnessMm);
    return q > 0.0 ? volumeM3 / q : Double.POSITIVE_INFINITY;
  }
}
