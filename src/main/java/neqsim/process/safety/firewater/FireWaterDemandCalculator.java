package neqsim.process.safety.firewater;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Fire water (deluge / water spray) demand calculator for offshore and onshore process areas.
 *
 * <p>
 * Sizes the fire water demand for an area following the application-rate philosophy used by NORSOK S-001, ISO 13702 and
 * NFPA 15: a <i>general area</i> coverage rate applied over the protected deck or ground area, plus optional
 * <i>dedicated object</i> coverage applied to the wetted surface of individual items of equipment, plus optional foam
 * (AFFF) concentrate for pool-fire scenarios.
 * </p>
 *
 * <p>
 * Typical minimum application rates (verify against the governing project standard before use):
 * </p>
 * <ul>
 * <li>10 (l/min)/m<sup>2</sup> — process areas and equipment surfaces (NORSOK S-001)</li>
 * <li>20 (l/min)/m<sup>2</sup> — wellhead areas, riser balconies, turret manifolds</li>
 * <li>10.2 (l/min)/m<sup>2</sup> — exposure protection of vessel shells (NFPA 15, 0.25 gpm/ft²)</li>
 * <li>6 (l/min)/m<sup>2</sup> — enclosed utility/machinery rooms with sprinkler protection</li>
 * </ul>
 *
 * <p>
 * The calculator is deliberately a <b>screening</b> tool: it establishes the water quantity that a coverage philosophy
 * implies, so that it can be compared against installed pump and ring-main capacity. It does not perform network
 * hydraulics; use it together with {@link DelugeNozzleLayout} for the nozzle net and
 * {@link FireWaterCoverageAssessment} for the feasibility gate against an existing supply.
 * </p>
 *
 * @author esol
 * @version 1.0
 */
public class FireWaterDemandCalculator implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** NORSOK S-001 minimum density for process areas and equipment surfaces, (l/min)/m2. */
  public static final double NORSOK_PROCESS_AREA_LPM_M2 = 10.0;

  /** NORSOK S-001 minimum density for wellhead areas and riser balconies, (l/min)/m2. */
  public static final double NORSOK_WELLHEAD_LPM_M2 = 20.0;

  /** NFPA 15 exposure-protection density for vessel shells, (l/min)/m2 (0.25 gpm/ft2). */
  public static final double NFPA15_VESSEL_EXPOSURE_LPM_M2 = 10.2;

  /** A single dedicated-object contribution to the demand. */
  public static class ProtectedObject implements Serializable {
    private static final long serialVersionUID = 1000L;

    /** Object identifier, typically the equipment tag. */
    public final String tag;
    /** Wetted (exposed) surface area of the object in m2. */
    public final double surfaceAreaM2;
    /** Application rate dedicated to this object in (l/min)/m2. */
    public final double rateLpmPerM2;

    /**
     * Create a dedicated-object protection record.
     *
     * @param tag equipment tag or identifier, must not be null
     * @param surfaceAreaM2 exposed surface area in m2, must be greater than zero
     * @param rateLpmPerM2 application rate in (l/min)/m2, must be greater than zero
     */
    public ProtectedObject(String tag, double surfaceAreaM2, double rateLpmPerM2) {
      this.tag = tag;
      this.surfaceAreaM2 = surfaceAreaM2;
      this.rateLpmPerM2 = rateLpmPerM2;
    }

    /**
     * Water demand of this object.
     *
     * @return demand in l/min
     */
    public double demandLpm() {
      return surfaceAreaM2 * rateLpmPerM2;
    }
  }

  private final double protectedAreaM2;
  private double areaRateLpmPerM2 = NORSOK_PROCESS_AREA_LPM_M2;
  private double durationMin = 30.0;
  private double foamConcentratePercent = 0.0;
  private double simultaneousAreaFactor = 1.0;
  private final List<ProtectedObject> objects = new ArrayList<ProtectedObject>();

  /**
   * Create a demand calculator for a protected area.
   *
   * @param protectedAreaM2 plan area to be covered by general area protection in m2; must be zero or greater (zero is
   * allowed when only dedicated object protection is required)
   */
  public FireWaterDemandCalculator(double protectedAreaM2) {
    if (protectedAreaM2 < 0.0) {
      throw new IllegalArgumentException("protectedAreaM2 must be >= 0, got " + protectedAreaM2);
    }
    this.protectedAreaM2 = protectedAreaM2;
  }

  /**
   * Set the general area application rate.
   *
   * @param rateLpmPerM2 application rate in (l/min)/m2, must be zero or greater. Set to zero to model a philosophy with
   * dedicated object protection only.
   * @return this calculator, for chaining
   */
  public FireWaterDemandCalculator setAreaRate(double rateLpmPerM2) {
    if (rateLpmPerM2 < 0.0) {
      throw new IllegalArgumentException("rateLpmPerM2 must be >= 0, got " + rateLpmPerM2);
    }
    this.areaRateLpmPerM2 = rateLpmPerM2;
    return this;
  }

  /**
   * Set the required application duration used to compute the water volume.
   *
   * @param durationMin duration in minutes, must be greater than zero
   * @return this calculator, for chaining
   */
  public FireWaterDemandCalculator setDurationMin(double durationMin) {
    if (durationMin <= 0.0) {
      throw new IllegalArgumentException("durationMin must be > 0, got " + durationMin);
    }
    this.durationMin = durationMin;
    return this;
  }

  /**
   * Set the foam concentrate induction rate for AFFF systems.
   *
   * @param percent concentrate percentage of the water rate, typically 1 or 3; zero disables foam
   * @return this calculator, for chaining
   */
  public FireWaterDemandCalculator setFoamConcentratePercent(double percent) {
    if (percent < 0.0 || percent > 10.0) {
      throw new IllegalArgumentException("foam concentrate percent must be 0..10, got " + percent);
    }
    this.foamConcentratePercent = percent;
    return this;
  }

  /**
   * Set a factor accounting for simultaneous operation of adjacent deluge sections.
   *
   * <p>
   * Many fire-area philosophies release every deluge valve covering a fire area on a confirmed fire signal. Use a
   * factor greater than 1.0 to represent the additional sections that open together with the section under study.
   * </p>
   *
   * @param factor multiplier on the total demand, must be 1.0 or greater
   * @return this calculator, for chaining
   */
  public FireWaterDemandCalculator setSimultaneousAreaFactor(double factor) {
    if (factor < 1.0) {
      throw new IllegalArgumentException("simultaneousAreaFactor must be >= 1, got " + factor);
    }
    this.simultaneousAreaFactor = factor;
    return this;
  }

  /**
   * Add a dedicated-object protection contribution.
   *
   * @param tag equipment tag or identifier
   * @param surfaceAreaM2 exposed surface in m2
   * @param rateLpmPerM2 dedicated application rate in (l/min)/m2
   * @return this calculator, for chaining
   */
  public FireWaterDemandCalculator addObject(String tag, double surfaceAreaM2, double rateLpmPerM2) {
    objects.add(new ProtectedObject(tag, surfaceAreaM2, rateLpmPerM2));
    return this;
  }

  /**
   * Add a horizontal cylindrical vessel or shell-and-tube exchanger as a dedicated object, using its shell geometry to
   * estimate the exposed surface.
   *
   * <p>
   * The surface is taken as the cylindrical shell plus two flat ends, which is a conservative screening estimate for a
   * shell-and-tube exchanger or a horizontal drum.
   * </p>
   *
   * @param tag equipment tag
   * @param shellOuterDiameterM shell outside diameter in m, must be greater than zero
   * @param tangentLengthM shell length in m, must be greater than zero
   * @param rateLpmPerM2 dedicated application rate in (l/min)/m2
   * @return this calculator, for chaining
   */
  public FireWaterDemandCalculator addHorizontalVessel(String tag, double shellOuterDiameterM, double tangentLengthM,
      double rateLpmPerM2) {
    if (shellOuterDiameterM <= 0.0 || tangentLengthM <= 0.0) {
      throw new IllegalArgumentException("vessel dimensions must be > 0");
    }
    double shell = Math.PI * shellOuterDiameterM * tangentLengthM;
    double ends = 2.0 * Math.PI * shellOuterDiameterM * shellOuterDiameterM / 4.0;
    return addObject(tag, shell + ends, rateLpmPerM2);
  }

  /**
   * Get the general area water demand.
   *
   * @return area demand in l/min
   */
  public double areaDemandLpm() {
    return protectedAreaM2 * areaRateLpmPerM2;
  }

  /**
   * Get the summed dedicated-object water demand.
   *
   * @return object demand in l/min
   */
  public double objectDemandLpm() {
    double sum = 0.0;
    for (int i = 0; i < objects.size(); i++) {
      sum += objects.get(i).demandLpm();
    }
    return sum;
  }

  /**
   * Get the total water demand including the simultaneous-section factor.
   *
   * @return total demand in l/min
   */
  public double totalDemandLpm() {
    return (areaDemandLpm() + objectDemandLpm()) * simultaneousAreaFactor;
  }

  /**
   * Get the total water demand in volumetric units.
   *
   * @return total demand in m3/h
   */
  public double totalDemandM3PerHour() {
    return totalDemandLpm() * 60.0 / 1000.0;
  }

  /**
   * Get the water volume consumed over the required duration.
   *
   * @return water volume in m3
   */
  public double waterVolumeM3() {
    return totalDemandLpm() * durationMin / 1000.0;
  }

  /**
   * Get the foam concentrate volume consumed over the required duration.
   *
   * @return foam concentrate volume in m3, zero when foam is disabled
   */
  public double foamConcentrateVolumeM3() {
    return waterVolumeM3() * foamConcentratePercent / 100.0;
  }

  /**
   * Get the protected plan area.
   *
   * @return protected area in m2
   */
  public double getProtectedAreaM2() {
    return protectedAreaM2;
  }

  /**
   * Get the general area application rate.
   *
   * @return application rate in (l/min)/m2
   */
  public double getAreaRateLpmPerM2() {
    return areaRateLpmPerM2;
  }

  /**
   * Get the required application duration.
   *
   * @return duration in minutes
   */
  public double getDurationMin() {
    return durationMin;
  }

  /**
   * Get the dedicated-object contributions.
   *
   * @return an unmodifiable-style copy of the object list
   */
  public List<ProtectedObject> getObjects() {
    return new ArrayList<ProtectedObject>(objects);
  }

  /**
   * Ratio of the dedicated-object demand to the full general-area demand.
   *
   * <p>
   * This is the headline number when comparing selective protection of critical equipment against blanket coverage of a
   * whole area.
   * </p>
   *
   * @return object demand divided by area demand, or {@link Double#NaN} when the area demand is zero
   */
  public double objectToAreaDemandRatio() {
    double area = areaDemandLpm();
    if (area <= 0.0) {
      return Double.NaN;
    }
    return objectDemandLpm() / area;
  }

  /**
   * Serialise the demand breakdown to JSON for agent and report consumption.
   *
   * @return pretty-printed JSON document
   */
  public String toJson() {
    JsonObject root = new JsonObject();
    root.addProperty("schemaVersion", "1.0");
    root.addProperty("protectedAreaM2", protectedAreaM2);
    root.addProperty("areaRateLpmPerM2", areaRateLpmPerM2);
    root.addProperty("areaDemandLpm", areaDemandLpm());
    JsonArray objArr = new JsonArray();
    for (int i = 0; i < objects.size(); i++) {
      ProtectedObject o = objects.get(i);
      JsonObject jo = new JsonObject();
      jo.addProperty("tag", o.tag);
      jo.addProperty("surfaceAreaM2", o.surfaceAreaM2);
      jo.addProperty("rateLpmPerM2", o.rateLpmPerM2);
      jo.addProperty("demandLpm", o.demandLpm());
      objArr.add(jo);
    }
    root.add("objects", objArr);
    root.addProperty("objectDemandLpm", objectDemandLpm());
    root.addProperty("simultaneousAreaFactor", simultaneousAreaFactor);
    root.addProperty("totalDemandLpm", totalDemandLpm());
    root.addProperty("totalDemandM3PerHour", totalDemandM3PerHour());
    root.addProperty("durationMin", durationMin);
    root.addProperty("waterVolumeM3", waterVolumeM3());
    root.addProperty("foamConcentratePercent", foamConcentratePercent);
    root.addProperty("foamConcentrateVolumeM3", foamConcentrateVolumeM3());
    root.addProperty("objectToAreaDemandRatio", objectToAreaDemandRatio());
    return new GsonBuilder().setPrettyPrinting().create().toJson(root);
  }
}
