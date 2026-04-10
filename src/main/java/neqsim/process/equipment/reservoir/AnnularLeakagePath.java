package neqsim.process.equipment.reservoir;

import java.io.Serializable;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.ProcessEquipmentBaseClass;
import neqsim.thermo.system.SystemInterface;

/**
 * Models fluid leakage through compromised well cement or micro-annulus between zones.
 *
 * <p>
 * This class calculates leakage flow through two mechanisms:
 * </p>
 * <ul>
 * <li><b>Channel flow (cubic law):</b> Flow through a narrow gap between casing and formation,
 * applicable when debonding creates a micro-annulus. The volumetric flow rate follows: q = w *
 * delta^3 / (12 * mu) * dP/L</li>
 * <li><b>Porous cement (Darcy flow):</b> Flow through degraded or poorly-placed cement with
 * non-zero permeability: q = k * A / (mu * L) * dP</li>
 * </ul>
 *
 * <p>
 * The class can be wired into a ProcessSystem as a parallel flow path alongside the main wellbore
 * to quantify the behind-casing contribution to out-of-zone injection.
 * </p>
 *
 * <h2>Usage Example</h2>
 *
 * <pre>{@code
 * AnnularLeakagePath leakage = new AnnularLeakagePath("cement leak");
 * leakage.setPathGeometry(1500.0, 1600.0, 0.10, 0.001); // 100m path, 0.1m wide, 1mm gap
 * leakage.setFluid(reservoirFluid.clone());
 * leakage.calculate(350.0, 250.0); // 350 bara source, 250 bara sink
 *
 * System.out.println("Channel leak rate: " + leakage.getChannelLeakageRate("m3/day") + " m3/day");
 * System.out.println("Cement leak rate: " + leakage.getCementLeakageRate("m3/day") + " m3/day");
 * }</pre>
 *
 * @author ESOL
 * @version 1.0
 */
public class AnnularLeakagePath extends ProcessEquipmentBaseClass {
  private static final long serialVersionUID = 1000L;
  private static final Logger logger = LogManager.getLogger(AnnularLeakagePath.class);

  /**
   * Leakage mechanism type.
   */
  public enum LeakageMechanism {
    /** Flow through micro-annulus gap (cubic law). */
    CHANNEL_FLOW,
    /** Flow through porous cement (Darcy). */
    POROUS_CEMENT,
    /** Combined channel + cement flow. */
    COMBINED
  }

  private LeakageMechanism mechanism = LeakageMechanism.COMBINED;

  // Path geometry
  private double depthTop = 0.0; // m - top of leakage path
  private double depthBottom = 0.0; // m - bottom of leakage path
  private double pathLength = 0.0; // m - total path length (depthBottom - depthTop)
  private double channelWidth = 0.0; // m - circumferential width of channel
  private double channelGap = 0.0; // m - radial aperture of micro-annulus

  // Cement properties
  private double cementPermeability = 1e-6; // mD (intact cement ~0.001 mD, degraded ~0.1-10 mD)
  private double cementCrossSectionArea = 0.0; // m² - cement cross-section area

  // Fluid properties (fallback if no SystemInterface set)
  private double fluidViscosity = 1.0; // cP
  private double fluidDensity = 1000.0; // kg/m³

  // Pressure conditions
  private double sourcePressure = 0.0; // bara
  private double sinkPressure = 0.0; // bara

  // Results
  private double channelLeakageRate = 0.0; // m³/s
  private double cementLeakageRate = 0.0; // m³/s
  private double totalLeakageRate = 0.0; // m³/s

  // NeqSim fluid
  private SystemInterface fluid = null;

  /**
   * Create an annular leakage path model.
   *
   * @param name equipment name
   */
  public AnnularLeakagePath(String name) {
    super(name);
  }

  /**
   * Set the leakage path geometry.
   *
   * @param depthTop top depth of leakage path (m TVD)
   * @param depthBottom bottom depth of leakage path (m TVD)
   * @param width circumferential width of channel (m), typically 0.01 - 0.3
   * @param gap radial aperture of micro-annulus (m), typically 0.0001 - 0.005
   */
  public void setPathGeometry(double depthTop, double depthBottom, double width, double gap) {
    this.depthTop = depthTop;
    this.depthBottom = depthBottom;
    this.pathLength = Math.abs(depthBottom - depthTop);
    this.channelWidth = width;
    this.channelGap = gap;
    // Default cement area: annular ring around wellbore
    this.cementCrossSectionArea = width * gap;
  }

  /**
   * Set cement permeability for the porous flow model.
   *
   * @param permeability cement permeability value
   * @param unit permeability unit ("mD", "D", "m2")
   */
  public void setCementPermeability(double permeability, String unit) {
    if ("D".equalsIgnoreCase(unit)) {
      this.cementPermeability = permeability * 1000.0;
    } else if ("m2".equalsIgnoreCase(unit)) {
      this.cementPermeability = permeability / 9.869e-16;
    } else {
      this.cementPermeability = permeability; // mD
    }
  }

  /**
   * Set the cement annulus cross-section area for Darcy flow.
   *
   * @param area cross-section area (m²)
   */
  public void setCementCrossSectionArea(double area) {
    this.cementCrossSectionArea = area;
  }

  /**
   * Set the fluid for viscosity calculation.
   *
   * @param fluid NeqSim fluid system
   */
  public void setFluid(SystemInterface fluid) {
    this.fluid = fluid;
  }

  /**
   * Set fluid viscosity directly (used when no SystemInterface is available).
   *
   * @param viscosity dynamic viscosity (cP)
   */
  public void setFluidViscosity(double viscosity) {
    this.fluidViscosity = viscosity;
  }

  /**
   * Set fluid density directly.
   *
   * @param density density (kg/m³)
   */
  public void setFluidDensity(double density) {
    this.fluidDensity = density;
  }

  /**
   * Set the leakage mechanism to use.
   *
   * @param mechanism leakage mechanism
   */
  public void setLeakageMechanism(LeakageMechanism mechanism) {
    this.mechanism = mechanism;
  }

  /**
   * Calculate leakage flow rates between source and sink pressures.
   *
   * @param sourcePressureBara source zone pressure (bara)
   * @param sinkPressureBara sink zone pressure (bara)
   */
  public void calculate(double sourcePressureBara, double sinkPressureBara) {
    this.sourcePressure = sourcePressureBara;
    this.sinkPressure = sinkPressureBara;

    double deltaPpascal = (sourcePressure - sinkPressure) * 1e5; // bar to Pa
    double viscosityPas = getEffectiveViscosity() * 1e-3; // cP to Pa·s

    if (pathLength <= 0 || viscosityPas <= 0) {
      logger.warn("Invalid path geometry or viscosity for leakage calculation");
      channelLeakageRate = 0.0;
      cementLeakageRate = 0.0;
      totalLeakageRate = 0.0;
      return;
    }

    // Channel flow: cubic law
    if (mechanism == LeakageMechanism.CHANNEL_FLOW || mechanism == LeakageMechanism.COMBINED) {
      if (channelWidth > 0 && channelGap > 0) {
        // q = w * delta^3 / (12 * mu) * (dP / L)
        channelLeakageRate = channelWidth * Math.pow(channelGap, 3.0) / (12.0 * viscosityPas)
            * (deltaPpascal / pathLength);
      } else {
        channelLeakageRate = 0.0;
      }
    } else {
      channelLeakageRate = 0.0;
    }

    // Porous cement: Darcy flow
    if (mechanism == LeakageMechanism.POROUS_CEMENT || mechanism == LeakageMechanism.COMBINED) {
      if (cementPermeability > 0 && cementCrossSectionArea > 0) {
        double kSI = cementPermeability * 9.869e-16; // mD to m²
        // q = k * A / (mu * L) * dP
        cementLeakageRate =
            kSI * cementCrossSectionArea / (viscosityPas * pathLength) * deltaPpascal;
      } else {
        cementLeakageRate = 0.0;
      }
    } else {
      cementLeakageRate = 0.0;
    }

    totalLeakageRate = channelLeakageRate + cementLeakageRate;
  }

  /**
   * Get effective viscosity from NeqSim fluid or fallback value.
   *
   * @return viscosity in cP
   */
  private double getEffectiveViscosity() {
    if (fluid != null) {
      try {
        fluid.initProperties();
        if (fluid.hasPhaseType("aqueous")) {
          return fluid.getPhase("aqueous").getViscosity("cP");
        } else if (fluid.hasPhaseType("oil")) {
          return fluid.getPhase("oil").getViscosity("cP");
        } else if (fluid.getNumberOfPhases() > 0) {
          return fluid.getPhase(0).getViscosity("cP");
        }
      } catch (Exception e) {
        logger.debug("Could not get viscosity from fluid, using fallback: " + e.getMessage());
      }
    }
    return fluidViscosity;
  }

  /**
   * Get channel (micro-annulus) leakage rate.
   *
   * @param unit flow rate unit ("m3/s", "m3/day", "l/min")
   * @return channel leakage rate
   */
  public double getChannelLeakageRate(String unit) {
    return convertFlowRate(channelLeakageRate, unit);
  }

  /**
   * Get cement porous-flow leakage rate.
   *
   * @param unit flow rate unit ("m3/s", "m3/day", "l/min")
   * @return cement leakage rate
   */
  public double getCementLeakageRate(String unit) {
    return convertFlowRate(cementLeakageRate, unit);
  }

  /**
   * Get total leakage rate (channel + cement).
   *
   * @param unit flow rate unit ("m3/s", "m3/day", "l/min")
   * @return total leakage rate
   */
  public double getTotalLeakageRate(String unit) {
    return convertFlowRate(totalLeakageRate, unit);
  }

  /**
   * Get the dominant leakage mechanism based on calculated rates.
   *
   * @return dominant mechanism description
   */
  public String getDominantMechanism() {
    if (channelLeakageRate > cementLeakageRate * 10) {
      return "Channel flow dominant (>90%)";
    } else if (cementLeakageRate > channelLeakageRate * 10) {
      return "Cement porous flow dominant (>90%)";
    } else {
      return "Mixed flow (channel + cement)";
    }
  }

  /**
   * Get the path length.
   *
   * @return path length (m)
   */
  public double getPathLength() {
    return pathLength;
  }

  /**
   * Convert flow rate from m³/s to the requested unit.
   *
   * @param rateM3s rate in m³/s
   * @param unit target unit
   * @return converted rate
   */
  private double convertFlowRate(double rateM3s, String unit) {
    if ("m3/day".equalsIgnoreCase(unit)) {
      return rateM3s * 86400.0;
    } else if ("l/min".equalsIgnoreCase(unit)) {
      return rateM3s * 60000.0;
    } else if ("bbl/day".equalsIgnoreCase(unit)) {
      return rateM3s * 86400.0 / 0.158987;
    } else {
      return rateM3s; // m³/s
    }
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    if (sourcePressure > 0 || sinkPressure > 0) {
      calculate(sourcePressure, sinkPressure);
    }
  }
}
