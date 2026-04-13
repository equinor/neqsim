package neqsim.process.equipment.separator.entrainment;

import java.io.Serializable;

/**
 * Represents a droplet (or bubble) size distribution for separator internals calculations.
 *
 * <p>
 * Supports Rosin-Rammler and log-normal distributions, which are the two most widely used
 * representations in the open literature for liquid droplet and bubble size distributions in
 * gas-liquid and liquid-liquid separation.
 * </p>
 *
 * <p>
 * <b>Rosin-Rammler (Weibull) distribution</b>:
 * </p>
 *
 * $$ F(d) = 1 - \exp\left(-\left(\frac{d}{d_{63.2}}\right)^q\right) $$
 *
 * <p>
 * where $d_{63.2}$ is the characteristic diameter (63.2% undersize) and $q$ is the spread
 * parameter. Typical values: $q = 2.0$–$4.0$ for pipe-flow atomization, $q = 2.5$–$3.5$ for
 * nozzle-generated sprays. See Lefebvre and McDonell (2017), <i>Atomization and Sprays</i>.
 * </p>
 *
 * <p>
 * <b>Log-normal distribution</b>:
 * </p>
 *
 * $$ F(d) = \Phi\left(\frac{\ln(d) - \mu}{\sigma}\right) $$
 *
 * <p>
 * where $\mu = \ln(d_{50})$ and $\sigma$ is the geometric standard deviation. Typical values:
 * $\sigma = 0.5$–$1.5$. See Azzopardi (2011), <i>Gas-Liquid Two-Phase Flow</i>, Springer.
 * </p>
 *
 * @author NeqSim team
 * @version 1.0
 */
public class DropletSizeDistribution implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /**
   * Enumeration of supported distribution types.
   */
  public enum DistributionType {
    /** Rosin-Rammler (Weibull) distribution. */
    ROSIN_RAMMLER,
    /** Log-normal distribution. */
    LOG_NORMAL
  }

  /** Distribution type. */
  private DistributionType type;

  /**
   * Characteristic diameter [m]. For Rosin-Rammler: d_63.2 (63.2% cumulative undersize), for
   * log-normal: d_50 (mass-median diameter).
   */
  private double characteristicDiameter;

  /**
   * Spread parameter. For Rosin-Rammler: q (shape parameter, typically 2.0-4.0). For log-normal:
   * sigma (geometric standard deviation, typically 0.5-1.5).
   */
  private double spreadParameter;

  /** Number of discrete size classes for numerical integration. */
  private int numberOfClasses = 50;

  /**
   * Creates a Rosin-Rammler droplet size distribution.
   *
   * @param characteristicDiameter d_63.2 characteristic diameter [m]
   * @param spreadParameter q shape parameter (typically 2.0-4.0)
   * @return a new DropletSizeDistribution
   */
  public static DropletSizeDistribution rosinRammler(double characteristicDiameter,
      double spreadParameter) {
    DropletSizeDistribution dsd = new DropletSizeDistribution();
    dsd.type = DistributionType.ROSIN_RAMMLER;
    dsd.characteristicDiameter = characteristicDiameter;
    dsd.spreadParameter = spreadParameter;
    return dsd;
  }

  /**
   * Creates a log-normal droplet size distribution.
   *
   * @param medianDiameter d_50 mass-median diameter [m]
   * @param geometricStdDev sigma geometric standard deviation (typically 0.5-1.5)
   * @return a new DropletSizeDistribution
   */
  public static DropletSizeDistribution logNormal(double medianDiameter, double geometricStdDev) {
    DropletSizeDistribution dsd = new DropletSizeDistribution();
    dsd.type = DistributionType.LOG_NORMAL;
    dsd.characteristicDiameter = medianDiameter;
    dsd.spreadParameter = geometricStdDev;
    return dsd;
  }

  /**
   * Creates a Rosin-Rammler distribution estimated from maximum stable droplet diameter using the
   * Hinze (1955) correlation for turbulent breakup.
   *
   * <p>
   * The maximum stable droplet diameter in a turbulent pipe flow is estimated as:
   * </p>
   *
   * $$ d_{max} = C \cdot We^{-3/5} \cdot D $$
   *
   * <p>
   * where $We = \rho_c V^2 D / \sigma$ is the Weber number, $D$ is the pipe diameter, and $C
   * \approx 0.725$ (Hinze, 1955). The d_63.2 is estimated as $d_{max} / 3$ based on typical
   * pipe-flow data (Azzopardi, 2011).
   * </p>
   *
   * @param continuousDensity density of continuous phase [kg/m3]
   * @param velocity mixture velocity [m/s]
   * @param pipeDiameter pipe internal diameter [m]
   * @param surfaceTension interfacial tension [N/m]
   * @param spreadParameter q (Rosin-Rammler spread, default 2.6 if &lt;= 0)
   * @return a new DropletSizeDistribution
   */
  public static DropletSizeDistribution fromHinzeCorrelation(double continuousDensity,
      double velocity, double pipeDiameter, double surfaceTension, double spreadParameter) {
    double effectiveSpread = (spreadParameter <= 0) ? 2.6 : spreadParameter;
    double weberNumber = continuousDensity * velocity * velocity * pipeDiameter / surfaceTension;
    double hinzeConstant = 0.725;
    double dMax = hinzeConstant * Math.pow(weberNumber, -0.6) * pipeDiameter;
    // d_63.2 is approximately d_max / 3 for typical pipe flows
    double d632 = dMax / 3.0;
    return rosinRammler(d632, effectiveSpread);
  }

  /**
   * Returns the cumulative volume fraction smaller than diameter d: F(d).
   *
   * @param d droplet diameter [m]
   * @return cumulative volume fraction [0-1]
   */
  public double cumulativeFraction(double d) {
    if (d <= 0.0) {
      return 0.0;
    }
    if (type == DistributionType.ROSIN_RAMMLER) {
      return 1.0 - Math.exp(-Math.pow(d / characteristicDiameter, spreadParameter));
    } else {
      // Log-normal CDF using error function approximation
      double z = (Math.log(d) - Math.log(characteristicDiameter)) / spreadParameter;
      return 0.5 * (1.0 + erf(z / Math.sqrt(2.0)));
    }
  }

  /**
   * Returns the volume-weighted probability density function at diameter d.
   *
   * @param d droplet diameter [m]
   * @return probability density [1/m]
   */
  public double volumePDF(double d) {
    if (d <= 0.0) {
      return 0.0;
    }
    if (type == DistributionType.ROSIN_RAMMLER) {
      double ratio = d / characteristicDiameter;
      return (spreadParameter / characteristicDiameter) * Math.pow(ratio, spreadParameter - 1.0)
          * Math.exp(-Math.pow(ratio, spreadParameter));
    } else {
      double z = (Math.log(d) - Math.log(characteristicDiameter)) / spreadParameter;
      return 1.0 / (d * spreadParameter * Math.sqrt(2.0 * Math.PI)) * Math.exp(-0.5 * z * z);
    }
  }

  /**
   * Returns discrete size classes for numerical integration of grade efficiency.
   *
   * <p>
   * Each element is a double[3] containing {lowerBound, midpoint, volumeFraction}. The size range
   * spans from d_min to d_max chosen to cover 0.1% to 99.9% of the distribution.
   * </p>
   *
   * @return array of size class descriptors
   */
  public double[][] getDiscreteClasses() {
    double dMin = inverseCDF(0.001);
    double dMax = inverseCDF(0.999);
    if (dMin <= 0.0) {
      dMin = characteristicDiameter * 0.01;
    }
    double[][] classes = new double[numberOfClasses][3];
    double logMin = Math.log(dMin);
    double logMax = Math.log(dMax);
    double step = (logMax - logMin) / numberOfClasses;

    for (int i = 0; i < numberOfClasses; i++) {
      double lower = Math.exp(logMin + i * step);
      double upper = Math.exp(logMin + (i + 1) * step);
      double mid = Math.exp(logMin + (i + 0.5) * step);
      double frac = cumulativeFraction(upper) - cumulativeFraction(lower);
      classes[i][0] = lower;
      classes[i][1] = mid;
      classes[i][2] = frac;
    }
    return classes;
  }

  /**
   * Computes the inverse CDF (quantile function) for a given cumulative fraction.
   *
   * @param f cumulative fraction [0-1]
   * @return droplet diameter [m]
   */
  public double inverseCDF(double f) {
    if (f <= 0.0) {
      return 0.0;
    }
    if (f >= 1.0) {
      return characteristicDiameter * 10.0;
    }
    if (type == DistributionType.ROSIN_RAMMLER) {
      return characteristicDiameter * Math.pow(-Math.log(1.0 - f), 1.0 / spreadParameter);
    } else {
      // Inverse of log-normal CDF
      double z = Math.sqrt(2.0) * inverseErf(2.0 * f - 1.0);
      return Math.exp(Math.log(characteristicDiameter) + spreadParameter * z);
    }
  }

  /**
   * Gets the volume-median diameter (d_50).
   *
   * @return d_50 [m]
   */
  public double getD50() {
    return inverseCDF(0.5);
  }

  /**
   * Gets the Sauter mean diameter (d_32), computed by numerical integration.
   *
   * @return d_32 [m]
   */
  public double getSauterMeanDiameter() {
    double[][] classes = getDiscreteClasses();
    double sumD3 = 0.0;
    double sumD2 = 0.0;
    for (double[] cls : classes) {
      double d = cls[1];
      double frac = cls[2];
      sumD3 += frac * d * d * d;
      sumD2 += frac * d * d;
    }
    return (sumD2 > 0.0) ? sumD3 / sumD2 : characteristicDiameter;
  }

  /**
   * Error function approximation (Abramowitz and Stegun, formula 7.1.26). Maximum error: 1.5e-7.
   *
   * @param x input value
   * @return erf(x)
   */
  static double erf(double x) {
    boolean negative = (x < 0);
    double absX = Math.abs(x);
    double t = 1.0 / (1.0 + 0.3275911 * absX);
    double poly = t * (0.254829592
        + t * (-0.284496736 + t * (1.421413741 + t * (-1.453152027 + t * 1.061405429))));
    double result = 1.0 - poly * Math.exp(-absX * absX);
    return negative ? -result : result;
  }

  /**
   * Inverse error function approximation. Uses rational approximation from Winitzki (2008).
   *
   * @param x input value in range (-1, 1)
   * @return inverseErf(x)
   */
  static double inverseErf(double x) {
    if (x <= -1.0) {
      return -6.0;
    }
    if (x >= 1.0) {
      return 6.0;
    }
    double a = 0.147;
    double lnTerm = Math.log(1.0 - x * x);
    double piInv = 2.0 / (Math.PI * a);
    double halfLn = lnTerm / 2.0;
    double sqrtTerm = Math.sqrt(Math.pow(piInv + halfLn, 2) - lnTerm / a);
    double result = Math.sqrt(sqrtTerm - piInv - halfLn);
    return (x >= 0) ? result : -result;
  }

  /**
   * Gets the distribution type.
   *
   * @return distribution type
   */
  public DistributionType getType() {
    return type;
  }

  /**
   * Gets the characteristic diameter [m].
   *
   * @return characteristicDiameter [m]
   */
  public double getCharacteristicDiameter() {
    return characteristicDiameter;
  }

  /**
   * Sets the characteristic diameter [m].
   *
   * @param characteristicDiameter [m]
   */
  public void setCharacteristicDiameter(double characteristicDiameter) {
    this.characteristicDiameter = characteristicDiameter;
  }

  /**
   * Gets the spread parameter.
   *
   * @return spreadParameter
   */
  public double getSpreadParameter() {
    return spreadParameter;
  }

  /**
   * Sets the spread parameter.
   *
   * @param spreadParameter the spread parameter
   */
  public void setSpreadParameter(double spreadParameter) {
    this.spreadParameter = spreadParameter;
  }

  /**
   * Gets the number of discrete size classes.
   *
   * @return numberOfClasses
   */
  public int getNumberOfClasses() {
    return numberOfClasses;
  }

  /**
   * Sets the number of discrete size classes for numerical integration.
   *
   * @param numberOfClasses number of classes (default 50)
   */
  public void setNumberOfClasses(int numberOfClasses) {
    this.numberOfClasses = numberOfClasses;
  }
}
