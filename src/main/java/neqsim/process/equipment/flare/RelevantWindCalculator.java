package neqsim.process.equipment.flare;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.google.gson.GsonBuilder;

/**
 * Relevant-wind calculator for flare thermal-radiation studies. Wind speed and direction strongly affect the location
 * and magnitude of the peak radiation footprint of a flare: wind bends the flame, moving the radiation centre and the
 * point of closest approach to people and equipment. Radiation studies therefore evaluate a "relevant" (design) wind
 * speed and the worst wind sector rather than just a single calm case.
 *
 * <p>
 * This utility:
 * </p>
 * <ul>
 * <li>scales a reference wind speed to the flare-tip elevation using the power-law wind profile V(z) = V<sub>ref</sub>
 * &middot; (z / z<sub>ref</sub>)<sup>&alpha;</sup>;</li>
 * <li>scans a supplied wind rose (sectors with mean speed and frequency) for the worst (highest) sector wind at the
 * flare elevation;</li>
 * <li>returns a frequency-weighted "relevant" wind speed, i.e. the speed whose cumulative exceedance frequency exceeds
 * a chosen design fraction.</li>
 * </ul>
 *
 * @author NeqSim
 * @version 1.0
 */
public class RelevantWindCalculator implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1L;

  /** Logger object for class. */
  private static final Logger logger = LogManager.getLogger(RelevantWindCalculator.class);

  /**
   * A single wind-rose sector entry.
   */
  public static class WindSector implements Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1L;

    /** Sector direction label (e.g. "N", "NE"). */
    private String direction;

    /** Mean wind speed at the reference height in m/s. */
    private double speedAtReference;

    /** Annual frequency of this sector (0-1). */
    private double frequency;

    /** Speed scaled to the flare elevation in m/s. */
    private double speedAtFlare;

    /**
     * Constructs a wind sector.
     *
     * @param direction sector direction label
     * @param speedAtReference mean wind speed at the reference height in m/s
     * @param frequency annual frequency of this sector (0-1)
     */
    public WindSector(String direction, double speedAtReference, double frequency) {
      this.direction = direction;
      this.speedAtReference = speedAtReference;
      this.frequency = frequency;
    }

    /**
     * Returns the sector direction label.
     *
     * @return direction label
     */
    public String getDirection() {
      return direction;
    }

    /**
     * Returns the mean wind speed at the reference height.
     *
     * @return speed at reference height in m/s
     */
    public double getSpeedAtReference() {
      return speedAtReference;
    }

    /**
     * Returns the annual frequency of this sector.
     *
     * @return frequency (0-1)
     */
    public double getFrequency() {
      return frequency;
    }

    /**
     * Returns the wind speed scaled to the flare elevation.
     *
     * @return speed at flare elevation in m/s
     */
    public double getSpeedAtFlare() {
      return speedAtFlare;
    }
  }

  /** Reference measurement height in meters. */
  private double referenceHeight = 10.0;

  /** Flare-tip elevation in meters. */
  private double flareTipElevation = 100.0;

  /** Power-law wind-shear exponent. */
  private double windShearExponent = 0.14;

  /** Design cumulative-exceedance fraction for the relevant wind (0-1). */
  private double designExceedanceFraction = 0.95;

  /** Wind-rose sectors. */
  private List<WindSector> sectors = new ArrayList<WindSector>();

  // ====================== Results ======================
  private double worstSectorSpeed = 0.0;
  private String worstSectorDirection = "";
  private double relevantWindSpeed = 0.0;

  /**
   * Default constructor for RelevantWindCalculator.
   */
  public RelevantWindCalculator() {
  }

  /**
   * Sets the elevation and wind-profile parameters.
   *
   * @param referenceHeightM reference measurement height in meters (must be &gt; 0)
   * @param flareTipElevationM flare-tip elevation in meters (must be &gt; 0)
   * @param shearExponent power-law wind-shear exponent (typically 0.10-0.30)
   */
  public void setProfile(double referenceHeightM, double flareTipElevationM, double shearExponent) {
    this.referenceHeight = referenceHeightM;
    this.flareTipElevation = flareTipElevationM;
    this.windShearExponent = shearExponent;
  }

  /**
   * Sets the design cumulative-exceedance fraction used for the relevant wind speed.
   *
   * @param fraction cumulative-exceedance fraction (0-1)
   */
  public void setDesignExceedanceFraction(double fraction) {
    this.designExceedanceFraction = fraction;
  }

  /**
   * Adds a wind-rose sector.
   *
   * @param direction sector direction label (e.g. "N")
   * @param speedAtReference mean wind speed at the reference height in m/s (must be &ge; 0)
   * @param frequency annual frequency of this sector (0-1)
   */
  public void addSector(String direction, double speedAtReference, double frequency) {
    sectors.add(new WindSector(direction, speedAtReference, frequency));
  }

  /**
   * Scales a reference wind speed to the flare elevation using the power-law profile.
   *
   * @param speedAtReference wind speed at the reference height in m/s
   * @return wind speed at the flare elevation in m/s
   */
  private double scaleToFlare(double speedAtReference) {
    return speedAtReference * Math.pow(flareTipElevation / referenceHeight, windShearExponent);
  }

  /**
   * Runs the relevant-wind calculation: scales each sector to the flare elevation, finds the worst sector, and computes
   * the frequency-weighted relevant wind speed.
   */
  public void calc() {
    worstSectorSpeed = 0.0;
    worstSectorDirection = "";
    double totalFrequency = 0.0;
    for (int i = 0; i < sectors.size(); i++) {
      WindSector s = sectors.get(i);
      s.speedAtFlare = scaleToFlare(s.speedAtReference);
      totalFrequency += s.frequency;
      if (s.speedAtFlare > worstSectorSpeed) {
        worstSectorSpeed = s.speedAtFlare;
        worstSectorDirection = s.direction;
      }
    }

    // Relevant wind: lowest sector speed whose cumulative frequency (from low speed up)
    // reaches the design exceedance fraction. Sort sectors by speed ascending.
    List<WindSector> sorted = new ArrayList<WindSector>(sectors);
    sorted.sort(new java.util.Comparator<WindSector>() {
      @Override
      public int compare(WindSector a, WindSector b) {
        return Double.compare(a.speedAtFlare, b.speedAtFlare);
      }
    });
    relevantWindSpeed = worstSectorSpeed;
    if (totalFrequency > 0.0) {
      double cumulative = 0.0;
      for (int i = 0; i < sorted.size(); i++) {
        cumulative += sorted.get(i).frequency / totalFrequency;
        if (cumulative >= designExceedanceFraction) {
          relevantWindSpeed = sorted.get(i).speedAtFlare;
          break;
        }
      }
    }

    logger.debug("Relevant wind: worst={} m/s ({}), relevant={} m/s", worstSectorSpeed, worstSectorDirection,
        relevantWindSpeed);
  }

  /**
   * Returns the worst (highest) sector wind speed at the flare elevation.
   *
   * @return worst sector wind speed in m/s
   */
  public double getWorstSectorSpeed() {
    return worstSectorSpeed;
  }

  /**
   * Returns the direction label of the worst sector.
   *
   * @return worst sector direction label
   */
  public String getWorstSectorDirection() {
    return worstSectorDirection;
  }

  /**
   * Returns the frequency-weighted relevant (design) wind speed at the flare elevation.
   *
   * @return relevant wind speed in m/s
   */
  public double getRelevantWindSpeed() {
    return relevantWindSpeed;
  }

  /**
   * Returns the list of wind-rose sectors (with scaled speeds populated after {@link #calc()}).
   *
   * @return list of wind sectors
   */
  public List<WindSector> getSectors() {
    return sectors;
  }

  /**
   * Serializes the calculation results to a pretty-printed JSON string.
   *
   * @return JSON representation of the results
   */
  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().create().toJson(this);
  }
}
