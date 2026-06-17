package neqsim.process.fielddevelopment.network;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Describes a multi-segment subsea tieback route for screening studies.
 *
 * <p>
 * The network keeps a lightweight topology model for early field-development decisions. It is not a
 * detailed hydraulic simulator; instead it provides route metadata and equivalent hydraulic
 * properties that existing screening calculations can use while preserving the segment breakdown
 * for reporting.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public final class TiebackRouteNetwork implements Serializable {
  private static final long serialVersionUID = 1000L;

  private final String name;
  private final String hostHubName;
  private final List<RouteSegment> segments;

  /**
   * Segment type used to classify route-network parts.
   */
  public enum SegmentType {
    /** Main production flowline segment. */
    FLOWLINE,
    /** Vertical or steep riser segment to a host or hub. */
    RISER,
    /** Shared corridor used by more than one discovery or phase. */
    SHARED_CORRIDOR,
    /** Branch line from a satellite, template, or drill centre. */
    BRANCH,
    /** Tie-in spool or short link into a manifold or host hub. */
    HUB_TIE_IN
  }

  /**
   * Creates a route network from a builder.
   *
   * @param builder route-network builder with validated segment data
   */
  private TiebackRouteNetwork(Builder builder) {
    this.name = builder.name;
    this.hostHubName = builder.hostHubName;
    this.segments = Collections.unmodifiableList(new ArrayList<RouteSegment>(builder.segments));
  }

  /**
   * Creates a builder for a route network.
   *
   * @param name route-network name; non-null and non-empty is recommended for reporting
   * @return builder instance
   */
  public static Builder builder(String name) {
    return new Builder(name);
  }

  /**
   * Gets the route-network name.
   *
   * @return route-network name
   */
  public String getName() {
    return name;
  }

  /**
   * Gets the host hub name.
   *
   * @return host hub name, or an empty string if no hub was specified
   */
  public String getHostHubName() {
    return hostHubName;
  }

  /**
   * Gets the immutable segment list.
   *
   * @return route segments in flow order where possible
   */
  public List<RouteSegment> getSegments() {
    return segments;
  }

  /**
   * Gets the total installed length including branches.
   *
   * @return installed route length in kilometres
   */
  public double getInstalledLengthKm() {
    double length = 0.0;
    for (RouteSegment segment : segments) {
      length += segment.getLengthKm();
    }
    return length;
  }

  /**
   * Gets the equivalent hydraulic length of the main export path.
   *
   * @return main-route length in kilometres excluding branch-only segments when possible
   */
  public double getScreeningLengthKm() {
    double length = 0.0;
    for (RouteSegment segment : segments) {
      if (segment.getType() != SegmentType.BRANCH) {
        length += segment.getLengthKm();
      }
    }
    return length > 0.0 ? length : getInstalledLengthKm();
  }

  /**
   * Gets the shared corridor length.
   *
   * @return shared corridor length in kilometres
   */
  public double getSharedCorridorLengthKm() {
    double length = 0.0;
    for (RouteSegment segment : segments) {
      if (segment.isShared() || segment.getType() == SegmentType.SHARED_CORRIDOR) {
        length += segment.getLengthKm();
      }
    }
    return length;
  }

  /**
   * Counts branch segments.
   *
   * @return number of branch segments
   */
  public int getBranchCount() {
    return countSegments(SegmentType.BRANCH);
  }

  /**
   * Counts riser segments.
   *
   * @return number of riser segments
   */
  public int getRiserCount() {
    return countSegments(SegmentType.RISER);
  }

  /**
   * Gets the deepest water depth along the route.
   *
   * @return maximum water depth in metres
   */
  public double getMaxWaterDepthM() {
    double depth = 0.0;
    for (RouteSegment segment : segments) {
      depth = Math.max(depth, segment.getInletWaterDepthM());
      depth = Math.max(depth, segment.getOutletWaterDepthM());
    }
    return depth;
  }

  /**
   * Gets the equivalent pipeline diameter for hydraulic screening.
   *
   * @return length-weighted main-route diameter in inches
   */
  public double getEquivalentDiameterInches() {
    double weighted = 0.0;
    double length = 0.0;
    for (RouteSegment segment : segments) {
      if (segment.getType() != SegmentType.BRANCH && segment.getDiameterInches() > 0.0) {
        weighted += segment.getDiameterInches() * segment.getLengthKm();
        length += segment.getLengthKm();
      }
    }
    if (length <= 0.0) {
      return 0.0;
    }
    return weighted / length;
  }

  /**
   * Gets the representative seabed temperature for hydraulic screening.
   *
   * @return length-weighted seabed temperature in Celsius
   */
  public double getEquivalentSeabedTemperatureC() {
    double weighted = 0.0;
    double length = 0.0;
    for (RouteSegment segment : segments) {
      if (segment.getType() != SegmentType.BRANCH && segment.getLengthKm() > 0.0) {
        weighted += segment.getSeabedTemperatureC() * segment.getLengthKm();
        length += segment.getLengthKm();
      }
    }
    if (length <= 0.0) {
      return 4.0;
    }
    return weighted / length;
  }

  /**
   * Gets the representative heat-transfer coefficient for hydraulic screening.
   *
   * @return length-weighted heat-transfer coefficient in W/m2K
   */
  public double getEquivalentHeatTransferCoefficientWm2K() {
    double weighted = 0.0;
    double length = 0.0;
    for (RouteSegment segment : segments) {
      if (segment.getType() != SegmentType.BRANCH && segment.getLengthKm() > 0.0) {
        weighted += segment.getHeatTransferCoefficientWm2K() * segment.getLengthKm();
        length += segment.getLengthKm();
      }
    }
    if (length <= 0.0) {
      return 0.0;
    }
    return weighted / length;
  }

  /**
   * Gets the net elevation change for the main route.
   *
   * @return net elevation change in metres, positive for uphill flow toward the host
   */
  public double getNetElevationChangeM() {
    if (segments.isEmpty()) {
      return 0.0;
    }
    RouteSegment firstMain = null;
    RouteSegment lastMain = null;
    for (RouteSegment segment : segments) {
      if (segment.getType() != SegmentType.BRANCH) {
        if (firstMain == null) {
          firstMain = segment;
        }
        lastMain = segment;
      }
    }
    if (firstMain == null || lastMain == null) {
      return 0.0;
    }
    return firstMain.getInletWaterDepthM() - lastMain.getOutletWaterDepthM();
  }

  /**
   * Builds a compact route-network summary for tables and notes.
   *
   * @return human-readable route summary
   */
  public String getSummary() {
    StringBuilder summary = new StringBuilder();
    summary.append(name).append(": ");
    summary.append(String.format("%.1f km main / %.1f km installed", getScreeningLengthKm(),
        getInstalledLengthKm()));
    if (getBranchCount() > 0) {
      summary.append(String.format(", %d branches", getBranchCount()));
    }
    if (getRiserCount() > 0) {
      summary.append(String.format(", %d risers", getRiserCount()));
    }
    if (getSharedCorridorLengthKm() > 0.0) {
      summary.append(String.format(", %.1f km shared", getSharedCorridorLengthKm()));
    }
    if (!hostHubName.isEmpty()) {
      summary.append(", hub ").append(hostHubName);
    }
    return summary.toString();
  }

  /**
   * Counts segments of a given type.
   *
   * @param type segment type to count
   * @return number of matching segments
   */
  private int countSegments(SegmentType type) {
    int count = 0;
    for (RouteSegment segment : segments) {
      if (segment.getType() == type) {
        count++;
      }
    }
    return count;
  }

  /**
   * Builder for tieback route networks.
   */
  public static final class Builder {
    private final String name;
    private String hostHubName = "";
    private final List<RouteSegment> segments = new ArrayList<RouteSegment>();

    /**
     * Creates a new builder.
     *
     * @param name route-network name
     */
    private Builder(String name) {
      this.name = name == null ? "Route network" : name;
    }

    /**
     * Sets the host hub name.
     *
     * @param hostHubName host hub name; null values are stored as an empty string
     * @return this builder
     */
    public Builder hostHub(String hostHubName) {
      this.hostHubName = hostHubName == null ? "" : hostHubName;
      return this;
    }

    /**
     * Adds a generic route segment.
     *
     * @param name segment name
     * @param type segment type
     * @param lengthKm segment length in kilometres; must be non-negative
     * @param diameterInches inner diameter in inches; must be non-negative
     * @param inletWaterDepthM inlet water depth in metres; must be non-negative
     * @param outletWaterDepthM outlet water depth in metres; must be non-negative
     * @param seabedTemperatureC representative seabed temperature in Celsius
     * @param heatTransferCoefficientWm2K heat-transfer coefficient in W/m2K; zero means adiabatic
     * @param shared true if this segment is shared by several discoveries or phases
     * @return this builder
     */
    public Builder addSegment(String name, SegmentType type, double lengthKm, double diameterInches,
        double inletWaterDepthM, double outletWaterDepthM, double seabedTemperatureC,
        double heatTransferCoefficientWm2K, boolean shared) {
      segments.add(new RouteSegment(name, type, lengthKm, diameterInches, inletWaterDepthM,
          outletWaterDepthM, seabedTemperatureC, heatTransferCoefficientWm2K, shared));
      return this;
    }

    /**
     * Adds a main flowline segment.
     *
     * @param name segment name
     * @param lengthKm length in kilometres
     * @param diameterInches diameter in inches
     * @param waterDepthM representative water depth in metres
     * @return this builder
     */
    public Builder addFlowline(String name, double lengthKm, double diameterInches,
        double waterDepthM) {
      return addSegment(name, SegmentType.FLOWLINE, lengthKm, diameterInches, waterDepthM,
          waterDepthM, 4.0, 5.0, false);
    }

    /**
     * Adds a shared corridor segment.
     *
     * @param name segment name
     * @param lengthKm length in kilometres
     * @param diameterInches diameter in inches
     * @param waterDepthM representative water depth in metres
     * @return this builder
     */
    public Builder addSharedCorridor(String name, double lengthKm, double diameterInches,
        double waterDepthM) {
      return addSegment(name, SegmentType.SHARED_CORRIDOR, lengthKm, diameterInches, waterDepthM,
          waterDepthM, 4.0, 5.0, true);
    }

    /**
     * Adds a riser segment.
     *
     * @param name segment name
     * @param lengthKm length in kilometres
     * @param diameterInches diameter in inches
     * @param seabedDepthM water depth at riser base in metres
     * @return this builder
     */
    public Builder addRiser(String name, double lengthKm, double diameterInches,
        double seabedDepthM) {
      return addSegment(name, SegmentType.RISER, lengthKm, diameterInches, seabedDepthM, 0.0, 4.0,
          8.0, false);
    }

    /**
     * Adds a branch segment.
     *
     * @param name segment name
     * @param lengthKm length in kilometres
     * @param diameterInches diameter in inches
     * @param waterDepthM representative water depth in metres
     * @return this builder
     */
    public Builder addBranch(String name, double lengthKm, double diameterInches,
        double waterDepthM) {
      return addSegment(name, SegmentType.BRANCH, lengthKm, diameterInches, waterDepthM,
          waterDepthM, 4.0, 5.0, false);
    }

    /**
     * Builds an immutable route network.
     *
     * @return route network
     */
    public TiebackRouteNetwork build() {
      return new TiebackRouteNetwork(this);
    }
  }

  /**
   * One route-network segment with screening-level geometry and thermal data.
   */
  public static final class RouteSegment implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final String name;
    private final SegmentType type;
    private final double lengthKm;
    private final double diameterInches;
    private final double inletWaterDepthM;
    private final double outletWaterDepthM;
    private final double seabedTemperatureC;
    private final double heatTransferCoefficientWm2K;
    private final boolean shared;

    /**
     * Creates a route segment.
     *
     * @param name segment name
     * @param type segment type
     * @param lengthKm length in kilometres
     * @param diameterInches diameter in inches
     * @param inletWaterDepthM inlet water depth in metres
     * @param outletWaterDepthM outlet water depth in metres
     * @param seabedTemperatureC seabed temperature in Celsius
     * @param heatTransferCoefficientWm2K heat-transfer coefficient in W/m2K
     * @param shared true if shared by multiple fields or phases
     */
    private RouteSegment(String name, SegmentType type, double lengthKm, double diameterInches,
        double inletWaterDepthM, double outletWaterDepthM, double seabedTemperatureC,
        double heatTransferCoefficientWm2K, boolean shared) {
      this.name = name == null ? "segment" : name;
      this.type = type == null ? SegmentType.FLOWLINE : type;
      this.lengthKm = Math.max(0.0, lengthKm);
      this.diameterInches = Math.max(0.0, diameterInches);
      this.inletWaterDepthM = Math.max(0.0, inletWaterDepthM);
      this.outletWaterDepthM = Math.max(0.0, outletWaterDepthM);
      this.seabedTemperatureC = seabedTemperatureC;
      this.heatTransferCoefficientWm2K = Math.max(0.0, heatTransferCoefficientWm2K);
      this.shared = shared;
    }

    /**
     * Gets the segment name.
     *
     * @return segment name
     */
    public String getName() {
      return name;
    }

    /**
     * Gets the segment type.
     *
     * @return segment type
     */
    public SegmentType getType() {
      return type;
    }

    /**
     * Gets segment length.
     *
     * @return length in kilometres
     */
    public double getLengthKm() {
      return lengthKm;
    }

    /**
     * Gets segment diameter.
     *
     * @return diameter in inches
     */
    public double getDiameterInches() {
      return diameterInches;
    }

    /**
     * Gets inlet water depth.
     *
     * @return inlet water depth in metres
     */
    public double getInletWaterDepthM() {
      return inletWaterDepthM;
    }

    /**
     * Gets outlet water depth.
     *
     * @return outlet water depth in metres
     */
    public double getOutletWaterDepthM() {
      return outletWaterDepthM;
    }

    /**
     * Gets seabed temperature.
     *
     * @return seabed temperature in Celsius
     */
    public double getSeabedTemperatureC() {
      return seabedTemperatureC;
    }

    /**
     * Gets heat-transfer coefficient.
     *
     * @return heat-transfer coefficient in W/m2K
     */
    public double getHeatTransferCoefficientWm2K() {
      return heatTransferCoefficientWm2K;
    }

    /**
     * Checks whether the segment is shared.
     *
     * @return true if shared by multiple discoveries or phases
     */
    public boolean isShared() {
      return shared;
    }
  }
}
