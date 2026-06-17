package neqsim.process.equipment.separator.entrainment;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;
import com.google.gson.GsonBuilder;

/**
 * Models inlet device performance for separator and scrubber design.
 *
 * <p>
 * The inlet device is the first separation stage in a separator. It performs two functions:
 * </p>
 * <ol>
 * <li><b>Bulk liquid separation</b> — removes the liquid film and large droplets from the incoming
 * two-phase mixture.</li>
 * <li><b>DSD transformation</b> — changes the downstream droplet size distribution through
 * coalescence (beneficial) and/or re-atomization (detrimental).</li>
 * </ol>
 *
 * <p>
 * This class models six common inlet device types from the open literature:
 * </p>
 *
 * <table>
 * <caption>Inlet device types and their characteristics from open literature</caption>
 * <tr>
 * <th>Device</th>
 * <th>Bulk Eff.</th>
 * <th>DSD Effect</th>
 * <th>Momentum Rating</th>
 * <th>Reference</th>
 * </tr>
 * <tr>
 * <td>No device (pipe entry)</td>
 * <td>0%</td>
 * <td>None</td>
 * <td>Low</td>
 * <td>—</td>
 * </tr>
 * <tr>
 * <td>Deflector plate</td>
 * <td>50-70%</td>
 * <td>Moderate coalescence</td>
 * <td>Low</td>
 * <td>Arnold and Stewart (2008)</td>
 * </tr>
 * <tr>
 * <td>Half-pipe (diverter)</td>
 * <td>60-80%</td>
 * <td>Moderate coalescence</td>
 * <td>Medium</td>
 * <td>Arnold and Stewart (2008)</td>
 * </tr>
 * <tr>
 * <td>Inlet vane distributor</td>
 * <td>70-85%</td>
 * <td>Good coalescence, uniform flow</td>
 * <td>High</td>
 * <td>Verlaan (2001), Grevelink (2007)</td>
 * </tr>
 * <tr>
 * <td>Inlet cyclone</td>
 * <td>90-99%</td>
 * <td>Best coalescence, centrifugal</td>
 * <td>Very High</td>
 * <td>Hoffmann and Stein (2008)</td>
 * </tr>
 * <tr>
 * <td>Schoepentoeter</td>
 * <td>80-90%</td>
 * <td>Good momentum absorption</td>
 * <td>Very High</td>
 * <td>Grevelink (2007)</td>
 * </tr>
 * </table>
 *
 * <p>
 * <b>References:</b>
 * </p>
 * <ul>
 * <li>Arnold, K., Stewart, M. (2008), <i>Surface Production Operations</i>, Vol. 1, 3rd ed., Gulf
 * Professional Publishing.</li>
 * <li>Verlaan, C.C.J. (2001), "Performance of novel mist eliminators", PhD Thesis, Delft University
 * of Technology.</li>
 * <li>Grevelink, J.G.H. (2007), "Inlet devices for separation equipment", <i>Shell Global
 * Solutions</i> (public conference paper).</li>
 * <li>Hoffmann, A.C., Stein, L.E. (2008), <i>Gas Cyclones and Swirl Tubes</i>, 2nd ed.,
 * Springer.</li>
 * <li>Bothamley, M. (2013), "Gas/Liquid Separators: Quantifying Separation Performance", <i>Oil and
 * Gas Facilities</i>, SPE 0813-0034-OGF (Part 1-3).</li>
 * </ul>
 *
 * @author NeqSim team
 * @version 1.0
 */
public class InletDeviceModel implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /**
   * Types of separator inlet devices.
   */
  public enum InletDeviceType {
    /** No inlet device — direct pipe entry. */
    NONE("No device", 0.0, 1.0, 0.0),
    /** Flat deflector plate (baffle). */
    DEFLECTOR_PLATE("Deflector Plate", 0.60, 0.85, 0.5),
    /** Half-pipe (diverter) open at the bottom. */
    HALF_PIPE("Half Pipe Diverter", 0.70, 0.75, 1.0),
    /** Inlet vane distributor. */
    INLET_VANE("Inlet Vane Distributor", 0.80, 0.55, 1.5),
    /** Inlet cyclone (tangential or axial entry). */
    INLET_CYCLONE("Inlet Cyclone", 0.95, 0.35, 3.0),
    /** Schoepentoeter (proprietary-style momentum absorber). */
    SCHOEPENTOETER("Schoepentoeter", 0.85, 0.50, 2.5),
    /** Impingement plate. */
    IMPINGEMENT_PLATE("Impingement Plate", 0.65, 0.80, 0.8);

    private final String displayName;
    private final double typicalBulkEfficiency;
    private final double dsdMultiplier;
    private final double pressureDropCoefficient;

    InletDeviceType(String displayName, double typicalBulkEfficiency, double dsdMultiplier,
        double pressureDropCoefficient) {
      this.displayName = displayName;
      this.typicalBulkEfficiency = typicalBulkEfficiency;
      this.dsdMultiplier = dsdMultiplier;
      this.pressureDropCoefficient = pressureDropCoefficient;
    }

    /**
     * Gets the human-readable display name.
     *
     * @return display name
     */
    public String getDisplayName() {
      return displayName;
    }

    /**
     * Gets the typical bulk liquid removal efficiency.
     *
     * @return bulk efficiency [0-1]
     */
    public double getTypicalBulkEfficiency() {
      return typicalBulkEfficiency;
    }

    /**
     * Gets the DSD size multiplier (downstream d_50 = multiplier * upstream d_50). Values less than
     * 1 indicate coalescence (beneficial), greater than 1 indicates re-atomization (detrimental).
     *
     * @return DSD multiplier
     */
    public double getDsdMultiplier() {
      return dsdMultiplier;
    }

    /**
     * Gets the pressure drop velocity head coefficient. Delta_P = K * 0.5 * rho_mix * v_nozzle^2.
     *
     * @return pressure drop coefficient
     */
    public double getPressureDropCoefficient() {
      return pressureDropCoefficient;
    }
  }

  // -- Configuration --
  private InletDeviceType deviceType = InletDeviceType.NONE;
  private double inletNozzleDiameter = 0.2; // [m]
  private double inletMomentum; // [Pa] rho_mix * v_nozzle^2

  // -- Override parameters (0 = use defaults from device type) --
  private double overrideBulkEfficiency = 0.0;
  private double overrideDsdMultiplier = 0.0;

  // -- Results --
  private double bulkSeparationEfficiency;
  private double pressureDrop; // [Pa]
  private double nozzleVelocity; // [m/s]
  private double momentumFlux; // [Pa]
  private DropletSizeDistribution downstreamDSD;

  /**
   * Creates a new InletDeviceModel with no device (pipe entry).
   */
  public InletDeviceModel() {
    this.deviceType = InletDeviceType.NONE;
  }

  /**
   * Creates a new InletDeviceModel for the specified device type.
   *
   * @param deviceType the inlet device type
   */
  public InletDeviceModel(InletDeviceType deviceType) {
    this.deviceType = deviceType;
  }

  /**
   * Calculates the inlet device performance for the given flow conditions.
   *
   * <p>
   * The inlet device:
   * </p>
   * <ol>
   * <li>Computes the nozzle velocity and momentum flux from the inlet pipe flow.</li>
   * <li>Determines bulk liquid separation efficiency based on device type and momentum.</li>
   * <li>Transforms the incoming DSD based on the device's coalescence/re-atomization
   * behaviour.</li>
   * <li>Calculates pressure drop across the device.</li>
   * </ol>
   *
   * @param incomingDSD droplet size distribution from inlet pipe flow
   * @param gasDensity gas density [kg/m3]
   * @param liquidDensity liquid density [kg/m3]
   * @param gasVolumeFlow gas volume flow rate [m3/s] at actual conditions
   * @param liquidVolumeFlow liquid volume flow rate [m3/s] at actual conditions
   * @param surfaceTension gas-liquid surface tension [N/m]
   */
  public void calculate(DropletSizeDistribution incomingDSD, double gasDensity,
      double liquidDensity, double gasVolumeFlow, double liquidVolumeFlow, double surfaceTension) {

    // Nozzle area
    double nozzleArea = Math.PI * inletNozzleDiameter * inletNozzleDiameter / 4.0;
    double totalFlow = gasVolumeFlow + liquidVolumeFlow;
    nozzleVelocity = (nozzleArea > 0) ? totalFlow / nozzleArea : 0.0;

    // Mixture density at nozzle
    double gasVolFrac = (totalFlow > 0) ? gasVolumeFlow / totalFlow : 1.0;
    double mixDensity = gasVolFrac * gasDensity + (1.0 - gasVolFrac) * liquidDensity;

    // Momentum flux [Pa] = rho_mix * v^2
    momentumFlux = mixDensity * nozzleVelocity * nozzleVelocity;
    inletMomentum = momentumFlux;

    // Bulk separation efficiency
    bulkSeparationEfficiency = calcBulkEfficiency(momentumFlux);

    // Pressure drop
    pressureDrop = deviceType.getPressureDropCoefficient() * 0.5 * mixDensity * nozzleVelocity
        * nozzleVelocity;

    // Transform DSD
    downstreamDSD = transformDSD(incomingDSD, surfaceTension);
  }

  /**
   * Calculates the bulk liquid separation efficiency of the inlet device.
   *
   * <p>
   * The bulk efficiency depends on:
   * </p>
   * <ul>
   * <li>Device type (inherent design efficiency)</li>
   * <li>Momentum flux at the nozzle (too high = re-entrainment, too low = poor separation)</li>
   * </ul>
   *
   * <p>
   * Most inlet devices have an optimal momentum range. The Bothamley (2013) recommended momentum
   * ranges are:
   * </p>
   * <ul>
   * <li>Deflector plate: &lt; 1500 Pa (rho*v^2)</li>
   * <li>Half-pipe: &lt; 3500 Pa</li>
   * <li>Inlet vane: &lt; 6000 Pa</li>
   * <li>Inlet cyclone: &lt; 15000 Pa</li>
   * <li>Schoepentoeter: &lt; 9000 Pa</li>
   * </ul>
   *
   * @param momentum nozzle momentum flux [Pa]
   * @return bulk efficiency [0-1]
   */
  private double calcBulkEfficiency(double momentum) {
    if (overrideBulkEfficiency > 0) {
      return overrideBulkEfficiency;
    }

    double baseEfficiency = deviceType.getTypicalBulkEfficiency();

    // Momentum limits for different devices (Bothamley, 2013)
    double maxMomentum;
    switch (deviceType) {
      case DEFLECTOR_PLATE:
        maxMomentum = 1500.0;
        break;
      case HALF_PIPE:
        maxMomentum = 3500.0;
        break;
      case INLET_VANE:
        maxMomentum = 6000.0;
        break;
      case INLET_CYCLONE:
        maxMomentum = 15000.0;
        break;
      case SCHOEPENTOETER:
        maxMomentum = 9000.0;
        break;
      case IMPINGEMENT_PLATE:
        maxMomentum = 2000.0;
        break;
      default:
        return 0.0;
    }

    // Efficiency degrades above maximum recommended momentum
    if (momentum > maxMomentum) {
      double degradation = Math.min(1.0, (momentum - maxMomentum) / maxMomentum);
      return baseEfficiency * (1.0 - 0.5 * degradation); // Up to 50% degradation
    }

    // Efficiency improves somewhat with momentum (better impaction) up to optimum
    double optimalMomentum = maxMomentum * 0.6;
    if (momentum < optimalMomentum * 0.3) {
      // Very low momentum: poor separation
      return baseEfficiency * 0.5;
    }

    return baseEfficiency;
  }

  /**
   * Transforms the incoming DSD through the inlet device.
   *
   * <p>
   * The effect depends on device type:
   * </p>
   * <ul>
   * <li><b>No device</b>: passes DSD unchanged.</li>
   * <li><b>Deflector/Half-pipe</b>: removes largest droplets, moderate increase in d_50 through
   * coalescence on the wall and redistribution.</li>
   * <li><b>Inlet vane</b>: removes bulk liquid, coalesces small droplets, shifts DSD coarser
   * (beneficial for downstream gravity).</li>
   * <li><b>Inlet cyclone</b>: centrifugal separation, very effective at removing fine drops,
   * significantly reduces DSD downstream.</li>
   * <li><b>Schoepentoeter</b>: absorbs momentum, moderate coalescence, prevents re-entrainment from
   * liquid surface.</li>
   * </ul>
   *
   * @param incomingDSD upstream DSD from pipe flow
   * @param surfaceTension used for re-atomization estimate [N/m]
   * @return transformed downstream DSD
   */
  private DropletSizeDistribution transformDSD(DropletSizeDistribution incomingDSD,
      double surfaceTension) {
    if (incomingDSD == null || deviceType == InletDeviceType.NONE) {
      return incomingDSD;
    }

    double multiplier =
        (overrideDsdMultiplier > 0) ? overrideDsdMultiplier : deviceType.getDsdMultiplier();

    // Create new DSD with modified characteristic diameter
    double newD = incomingDSD.getCharacteristicDiameter() * multiplier;
    newD = Math.max(newD, 1e-6); // Minimum 1 um

    // Spread parameter may also change
    double newSpread = incomingDSD.getSpreadParameter();

    // Cyclone and vane devices produce a narrower (more uniform) DSD
    if (deviceType == InletDeviceType.INLET_CYCLONE || deviceType == InletDeviceType.INLET_VANE) {
      newSpread = Math.min(newSpread * 1.2, 4.0); // Tighter distribution
    }

    // Half-pipe and deflector can re-entrain, producing broader DSD
    if (deviceType == InletDeviceType.HALF_PIPE || deviceType == InletDeviceType.DEFLECTOR_PLATE) {
      if (momentumFlux > 3000) {
        // High momentum re-atomization
        newSpread = Math.max(newSpread * 0.8, 1.5); // Broader distribution
      }
    }

    if (incomingDSD.getType() == DropletSizeDistribution.DistributionType.ROSIN_RAMMLER) {
      return DropletSizeDistribution.rosinRammler(newD, newSpread);
    } else {
      return DropletSizeDistribution.logNormal(newD, newSpread);
    }
  }

  /**
   * Returns a JSON representation of the inlet device calculation results.
   *
   * @return JSON string
   */
  public String toJson() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("deviceType", deviceType.getDisplayName());
    result.put("inletNozzleDiameter_m", inletNozzleDiameter);
    result.put("nozzleVelocity_m_s", nozzleVelocity);
    result.put("momentumFlux_Pa", momentumFlux);
    result.put("bulkSeparationEfficiency", bulkSeparationEfficiency);
    result.put("pressureDrop_Pa", pressureDrop);
    if (downstreamDSD != null) {
      result.put("downstreamDSD_d50_um", downstreamDSD.getD50() * 1e6);
      result.put("downstreamDSD_type", downstreamDSD.getType().name());
    }
    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create()
        .toJson(result);
  }

  // ----- Getters and Setters -----

  /**
   * Gets the inlet device type.
   *
   * @return device type
   */
  public InletDeviceType getDeviceType() {
    return deviceType;
  }

  /**
   * Sets the inlet device type.
   *
   * @param deviceType the device type
   */
  public void setDeviceType(InletDeviceType deviceType) {
    this.deviceType = deviceType;
  }

  /**
   * Gets the inlet nozzle diameter.
   *
   * @return nozzle diameter [m]
   */
  public double getInletNozzleDiameter() {
    return inletNozzleDiameter;
  }

  /**
   * Sets the inlet nozzle diameter.
   *
   * @param diameter nozzle diameter [m]
   */
  public void setInletNozzleDiameter(double diameter) {
    this.inletNozzleDiameter = diameter;
  }

  /**
   * Gets the calculated bulk separation efficiency.
   *
   * @return bulk efficiency [0-1]
   */
  public double getBulkSeparationEfficiency() {
    return bulkSeparationEfficiency;
  }

  /**
   * Gets the calculated pressure drop across the inlet device.
   *
   * @return pressure drop [Pa]
   */
  public double getPressureDrop() {
    return pressureDrop;
  }

  /**
   * Gets the nozzle velocity at the inlet.
   *
   * @return nozzle velocity [m/s]
   */
  public double getNozzleVelocity() {
    return nozzleVelocity;
  }

  /**
   * Gets the nozzle momentum flux.
   *
   * @return momentum flux [Pa] (rho_mix * v^2)
   */
  public double getMomentumFlux() {
    return momentumFlux;
  }

  /**
   * Gets the downstream DSD after the inlet device.
   *
   * @return transformed DSD, or null if not calculated
   */
  public DropletSizeDistribution getDownstreamDSD() {
    return downstreamDSD;
  }

  /**
   * Overrides the default bulk separation efficiency for this device.
   *
   * @param efficiency custom bulk efficiency [0-1], set to 0 to use device type default
   */
  public void setOverrideBulkEfficiency(double efficiency) {
    this.overrideBulkEfficiency = efficiency;
  }

  /**
   * Overrides the default DSD transformation multiplier.
   *
   * @param multiplier custom multiplier, set to 0 to use device type default
   */
  public void setOverrideDsdMultiplier(double multiplier) {
    this.overrideDsdMultiplier = multiplier;
  }
}
