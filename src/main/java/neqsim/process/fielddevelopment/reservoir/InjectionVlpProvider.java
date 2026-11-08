package neqsim.process.fielddevelopment.reservoir;

import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Lift performance of an injection well (water or gas) for VFPINJ tables: the bottom-hole pressure needed to inject a
 * surface rate against a given wellhead pressure.
 *
 * <p>
 * The tubing survey is marched from the wellhead to the datum. Each sub-segment is flashed at its mean temperature and
 * a predicted mean pressure, so density and viscosity follow the real fluid (a compressible gas or water with a
 * salinity correction). Friction uses the Darcy-Weisbach equation with the Haaland friction factor and the hydrostatic
 * term uses the true vertical depth difference, so deviated and extended-reach wells are handled. The temperature is
 * interpolated linearly with true vertical depth between the wellhead and bottom-hole values.
 * </p>
 *
 * <p>
 * The rate is the surface volume rate in Sm3/day of the injected phase, converted to mass with the density of the
 * template fluid at 15 C and 1.01325 bara. Water ratios, gas ratios and lift quantity are ignored. The provider keeps
 * one working fluid and is not thread safe.
 * </p>
 *
 * <p>
 * This implementation is final so a subclass cannot expose a partially constructed provider through finalization after
 * input validation or fluid initialization fails. Implement {@link VlpProvider} for a custom injection model.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public final class InjectionVlpProvider implements VlpProvider {
  private static final double GRAVITY = 9.80665;
  private static final double STANDARD_TEMPERATURE_K = 288.15;
  private static final double STANDARD_PRESSURE_BARA = 1.01325;

  private final SystemInterface work;
  private final double[] md;
  private final double[] tvd;
  private final double[] innerDiameter;
  private final double standardDensity;
  private double wellheadTemperatureC = 10.0;
  private double bottomholeTemperatureC = 90.0;
  private double roughnessM = 4.5e-5;
  private double maxSegmentLengthM = 250.0;
  private double densityFactor = 1.0;
  private int evaluations = 0;

  /**
   * Creates an injection provider for a surveyed tubing string.
   *
   * @param template injected fluid, a fully specified system with a mixing rule (for example pure water with CPA); the
   * system is cloned
   * @param md measured depth along the tubing from the wellhead, strictly increasing, starting at 0, in m
   * @param tvd true vertical depth from the wellhead in m, one value per measured depth
   * @param innerDiameter inner diameter in m for each interval between survey points (length md.length - 1)
   * @throws IllegalArgumentException if the survey arrays are inconsistent
   */
  public InjectionVlpProvider(SystemInterface template, double[] md, double[] tvd, double[] innerDiameter) {
    if (md == null || tvd == null || innerDiameter == null || md.length < 2 || md.length != tvd.length
        || innerDiameter.length != md.length - 1) {
      throw new IllegalArgumentException("Survey needs at least two points and one diameter per interval");
    }
    for (int i = 1; i < md.length; i++) {
      if (!(md[i] > md[i - 1]) || tvd[i] < tvd[i - 1] - 1e-9 || innerDiameter[i - 1] <= 0.0) {
        throw new IllegalArgumentException("Survey must have increasing MD, non-decreasing TVD and positive diameters");
      }
    }
    this.work = template.clone();
    this.md = md.clone();
    this.tvd = tvd.clone();
    this.innerDiameter = innerDiameter.clone();
    this.standardDensity = flashDensity(STANDARD_TEMPERATURE_K, STANDARD_PRESSURE_BARA);
  }

  /**
   * Creates a vertical injection well.
   *
   * @param template injected fluid (see the survey constructor)
   * @param tvd true vertical depth in m
   * @param innerDiameter tubing inner diameter in m
   * @throws IllegalArgumentException if depth or diameter is not positive
   */
  public InjectionVlpProvider(SystemInterface template, double tvd, double innerDiameter) {
    this(template, new double[] {0.0, tvd}, new double[] {0.0, tvd}, new double[] {innerDiameter});
  }

  /**
   * Sets the flowing temperatures.
   *
   * @param wellheadC wellhead temperature in C
   * @param bottomholeC bottom-hole temperature in C
   * @return this provider
   */
  public InjectionVlpProvider setTemperatures(double wellheadC, double bottomholeC) {
    this.wellheadTemperatureC = wellheadC;
    this.bottomholeTemperatureC = bottomholeC;
    return this;
  }

  /**
   * Sets the absolute wall roughness.
   *
   * @param roughness roughness in m
   * @return this provider
   */
  public InjectionVlpProvider setRoughness(double roughness) {
    this.roughnessM = roughness;
    return this;
  }

  /**
   * Sets the longest sub-segment used for the pressure march.
   *
   * @param lengthM maximum sub-segment length in m
   * @return this provider
   */
  public InjectionVlpProvider setMaxSegmentLength(double lengthM) {
    this.maxSegmentLengthM = Math.max(10.0, lengthM);
    return this;
  }

  /**
   * Sets a multiplier on the flashed density, for example 1.026 for seawater-type brine when the template is pure
   * water. The standard density used for the mass rate is scaled in the same way.
   *
   * @param factor density multiplier (positive)
   * @return this provider
   */
  public InjectionVlpProvider setDensityFactor(double factor) {
    this.densityFactor = factor;
    return this;
  }

  /**
   * Number of bottom-hole pressure evaluations so far.
   *
   * @return evaluation count
   */
  public int getEvaluations() {
    return evaluations;
  }

  /** {@inheritDoc} */
  @Override
  public double bottomHolePressure(double rate, double outletPressure, double waterRatio, double gasRatio, double alq) {
    evaluations++;
    if (!(rate > 0.0) || !(outletPressure > 0.0)) {
      return Double.NaN;
    }
    double massRate = rate * standardDensity * densityFactor / 86400.0;
    double totalTvd = tvd[tvd.length - 1];
    double p = outletPressure;
    for (int i = 1; i < md.length; i++) {
      double length = md[i] - md[i - 1];
      double dz = tvd[i] - tvd[i - 1];
      double d = innerDiameter[i - 1];
      int n = Math.max(1, (int) Math.ceil(length / maxSegmentLengthM));
      double area = Math.PI * d * d / 4.0;
      for (int k = 0; k < n; k++) {
        double zMid = tvd[i - 1] + dz * (k + 0.5) / n;
        double tempC = wellheadTemperatureC
            + (bottomholeTemperatureC - wellheadTemperatureC) * (totalTvd > 0.0 ? zMid / totalTvd : 0.0);
        double dp = 0.0;
        double pMid = p;
        for (int pass = 0; pass < 2; pass++) {
          double[] props = flashProperties(273.15 + tempC, pMid);
          double rho = props[0] * densityFactor;
          double mu = props[1];
          double velocity = massRate / (rho * area);
          double re = rho * velocity * d / mu;
          double friction = haaland(re, d);
          double dpFriction = friction * (length / n) / d * rho * velocity * velocity / 2.0;
          dp = (rho * GRAVITY * dz / n + dpFriction) / 1.0e5;
          pMid = p + 0.5 * dp;
        }
        p += dp;
      }
    }
    return p;
  }

  /**
   * Haaland explicit friction factor with laminar fallback.
   *
   * @param re Reynolds number
   * @param d inner diameter in m
   * @return Darcy friction factor
   */
  private double haaland(double re, double d) {
    if (re < 2300.0) {
      return 64.0 / Math.max(re, 1.0);
    }
    double term = Math.pow(roughnessM / d / 3.7, 1.11) + 6.9 / re;
    double inv = -1.8 * Math.log10(term);
    return 1.0 / (inv * inv);
  }

  /**
   * Flashes the working fluid and returns density and viscosity.
   *
   * @param temperatureK temperature in K
   * @param pressureBara pressure in bara
   * @return array with density in kg/m3 and viscosity in Pa s
   */
  private double[] flashProperties(double temperatureK, double pressureBara) {
    work.setTemperature(temperatureK);
    work.setPressure(pressureBara);
    new ThermodynamicOperations(work).TPflash();
    work.initProperties();
    return new double[] {work.getDensity("kg/m3"), work.getViscosity("kg/msec")};
  }

  /**
   * Density of the working fluid at a state.
   *
   * @param temperatureK temperature in K
   * @param pressureBara pressure in bara
   * @return density in kg/m3
   */
  private double flashDensity(double temperatureK, double pressureBara) {
    return flashProperties(temperatureK, pressureBara)[0];
  }
}
