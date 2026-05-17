package neqsim.process.equipment.lng;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Multi-zone heat transfer model for LNG cargo tanks.
 *
 * <p>
 * Commercial LNG ageing simulators (Cargo Expert, LNGMAP, MACS III) use 4-6 heat transfer zones
 * instead of a single U*A value. Each zone has its own U-value, area, and boundary temperature:
 * </p>
 * <ul>
 * <li><b>Bottom slab:</b> contact with double-bottom ballast tanks (sea water temperature)</li>
 * <li><b>Sidewalls:</b> contact with adjacent ballast/void spaces (ambient temperature)</li>
 * <li><b>Roof/dome:</b> exposed to weather deck (ambient + solar gain)</li>
 * <li><b>Cofferdam:</b> shared wall between adjacent cargo tanks (adjacent cargo temperature)</li>
 * <li><b>Internal supports:</b> conduction through support structures</li>
 * </ul>
 *
 * <p>
 * Total heat ingress Q_total = sum_i(U_i * A_i * (T_boundary_i - T_LNG)).
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public class TankHeatTransferModel implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1021L;

  /** List of heat transfer zones. */
  private List<HeatTransferZone> zones;

  /** Optional tank geometry for auto zone calculation. */
  private TankGeometry tankGeometry;

  /** Solar absorptivity of deck surface (0-1). Typical: 0.3-0.6 for painted steel. */
  private double solarAbsorptivity = 0.4;

  /** Wind-enhanced convection factor. Applied to roof/exposed zones. */
  private double windConvectionFactor = 1.0;

  /**
   * Default constructor.
   */
  public TankHeatTransferModel() {
    this.zones = new ArrayList<HeatTransferZone>();
  }

  /**
   * Constructor with tank geometry, auto-creates standard zones.
   *
   * @param geometry tank geometry definition
   * @param ambientTemperature ambient air/sea temperature (K)
   */
  public TankHeatTransferModel(TankGeometry geometry, double ambientTemperature) {
    this();
    this.tankGeometry = geometry;
    createStandardZones(geometry, ambientTemperature);
  }

  /**
   * Create standard heat transfer zones from tank geometry.
   *
   * @param geometry tank geometry
   * @param ambientTemp ambient temperature (K)
   */
  private void createStandardZones(TankGeometry geometry, double ambientTemp) {
    zones.clear();

    double uInsulation = geometry.getInsulationUValue();

    // Bottom slab — boundary is sea water (approx ambient - 5K for submerged hull)
    double seaWaterTemp = ambientTemp - 5.0;
    zones.add(new HeatTransferZone("bottom", uInsulation, geometry.getBottomArea(), seaWaterTemp));

    // Sidewalls — boundary is ambient air (through ballast/void spaces)
    zones.add(
        new HeatTransferZone("sidewalls", uInsulation, geometry.getSidewallArea(), ambientTemp));

    // Roof/dome — boundary is ambient + solar gain
    zones.add(new HeatTransferZone("roof", uInsulation * 1.1, geometry.getRoofArea(), ambientTemp));
  }

  /**
   * Add a custom heat transfer zone.
   *
   * @param name zone name
   * @param uValue overall heat transfer coefficient (W/(m2*K))
   * @param area heat transfer area (m2)
   * @param boundaryTemperature boundary temperature (K)
   */
  public void addZone(String name, double uValue, double area, double boundaryTemperature) {
    zones.add(new HeatTransferZone(name, uValue, area, boundaryTemperature));
  }

  /**
   * Calculate total heat ingress to the LNG.
   *
   * @param lngTemperature current LNG temperature (K)
   * @return total heat ingress (W)
   */
  public double calculateTotalHeatIngress(double lngTemperature) {
    double totalQ = 0.0;
    for (HeatTransferZone zone : zones) {
      double q = zone.uValue * zone.area * (zone.boundaryTemperature - lngTemperature);
      if (q > 0) {
        totalQ += q;
      }
    }
    return totalQ;
  }

  /**
   * Calculate heat ingress per zone.
   *
   * @param lngTemperature current LNG temperature (K)
   * @return map of zone name to heat ingress (W)
   */
  public Map<String, Double> calculateZoneHeatIngress(double lngTemperature) {
    Map<String, Double> zoneQ = new LinkedHashMap<String, Double>();
    for (HeatTransferZone zone : zones) {
      double q = zone.uValue * zone.area * (zone.boundaryTemperature - lngTemperature);
      zoneQ.put(zone.name, Math.max(0, q));
    }
    return zoneQ;
  }

  /**
   * Calculate heat distribution to layers based on zone positions.
   *
   * <p>
   * Bottom zone heat goes primarily to bottom layer, sidewall heat distributed by wetted area
   * fraction, roof heat goes to top layer via vapor space.
   * </p>
   *
   * @param lngTemperature bulk LNG temperature (K)
   * @param numLayers number of liquid layers
   * @return array of heat per layer (W), index 0 = bottom
   */
  public double[] calculateLayerHeatDistribution(double lngTemperature, int numLayers) {
    if (numLayers <= 0) {
      return new double[0];
    }
    double[] layerQ = new double[numLayers];

    for (HeatTransferZone zone : zones) {
      double q = zone.uValue * zone.area * (zone.boundaryTemperature - lngTemperature);
      if (q <= 0) {
        continue;
      }

      if ("bottom".equals(zone.name)) {
        // Bottom heat goes to bottom layer
        layerQ[0] += q;
      } else if ("roof".equals(zone.name) || "dome".equals(zone.name)) {
        // Roof heat goes to top layer (through vapor space)
        layerQ[numLayers - 1] += q * 0.3; // 30% reaches top liquid via radiation/convection
      } else {
        // Sidewall heat distributed proportionally across all layers
        double perLayer = q / numLayers;
        for (int i = 0; i < numLayers; i++) {
          layerQ[i] += perLayer;
        }
      }
    }
    return layerQ;
  }

  /**
   * Update boundary temperatures for time-varying conditions.
   *
   * @param ambientTemperature new ambient temperature (K)
   * @param solarRadiation solar radiation on deck (W/m2)
   * @param seaWaterTemperature sea water temperature (K)
   */
  public void updateBoundaryConditions(double ambientTemperature, double solarRadiation,
      double seaWaterTemperature) {
    for (HeatTransferZone zone : zones) {
      if ("bottom".equals(zone.name)) {
        zone.boundaryTemperature = seaWaterTemperature;
      } else if ("roof".equals(zone.name) || "dome".equals(zone.name)) {
        // Roof temperature = ambient + solar gain
        zone.boundaryTemperature = ambientTemperature + solarAbsorptivity * solarRadiation / 10.0;
      } else if ("cofferdam".equals(zone.name)) {
        // Cofferdam unchanged (set by adjacent tank)
      } else {
        zone.boundaryTemperature = ambientTemperature;
      }
    }
  }

  /**
   * Get the list of zones.
   *
   * @return list of heat transfer zones
   */
  public List<HeatTransferZone> getZones() {
    return zones;
  }

  /**
   * Get solar absorptivity.
   *
   * @return absorptivity (0-1)
   */
  public double getSolarAbsorptivity() {
    return solarAbsorptivity;
  }

  /**
   * Set solar absorptivity.
   *
   * @param solarAbsorptivity absorptivity (0-1)
   */
  public void setSolarAbsorptivity(double solarAbsorptivity) {
    this.solarAbsorptivity = solarAbsorptivity;
  }

  /**
   * Get wind convection factor.
   *
   * @return convection factor
   */
  public double getWindConvectionFactor() {
    return windConvectionFactor;
  }

  /**
   * Set wind convection factor.
   *
   * @param factor convection enhancement factor
   */
  public void setWindConvectionFactor(double factor) {
    this.windConvectionFactor = factor;
  }

  /**
   * Get tank geometry.
   *
   * @return tank geometry or null
   */
  public TankGeometry getTankGeometry() {
    return tankGeometry;
  }

  /**
   * A single heat transfer zone with its own U-value, area, and boundary condition.
   */
  public static class HeatTransferZone implements Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1022L;

    /** Zone name (e.g., "bottom", "sidewalls", "roof"). */
    private String name;

    /** Overall heat transfer coefficient (W/(m2*K)). */
    private double uValue;

    /** Heat transfer area (m2). */
    private double area;

    /** Boundary temperature (K). */
    private double boundaryTemperature;

    /**
     * Constructor for HeatTransferZone.
     *
     * @param name zone name
     * @param uValue overall heat transfer coefficient (W/(m2*K))
     * @param area heat transfer area (m2)
     * @param boundaryTemperature boundary temperature (K)
     */
    public HeatTransferZone(String name, double uValue, double area, double boundaryTemperature) {
      this.name = name;
      this.uValue = uValue;
      this.area = area;
      this.boundaryTemperature = boundaryTemperature;
    }

    /**
     * Get zone name.
     *
     * @return zone name
     */
    public String getName() {
      return name;
    }

    /**
     * Get U-value.
     *
     * @return U-value (W/(m2*K))
     */
    public double getUValue() {
      return uValue;
    }

    /**
     * Set U-value.
     *
     * @param uValue U-value (W/(m2*K))
     */
    public void setUValue(double uValue) {
      this.uValue = uValue;
    }

    /**
     * Get area.
     *
     * @return area (m2)
     */
    public double getArea() {
      return area;
    }

    /**
     * Set area.
     *
     * @param area area (m2)
     */
    public void setArea(double area) {
      this.area = area;
    }

    /**
     * Get boundary temperature.
     *
     * @return boundary temperature (K)
     */
    public double getBoundaryTemperature() {
      return boundaryTemperature;
    }

    /**
     * Set boundary temperature.
     *
     * @param boundaryTemperature boundary temperature (K)
     */
    public void setBoundaryTemperature(double boundaryTemperature) {
      this.boundaryTemperature = boundaryTemperature;
    }
  }
}
