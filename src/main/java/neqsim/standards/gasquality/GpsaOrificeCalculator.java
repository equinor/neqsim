package neqsim.standards.gasquality;

import java.io.Serializable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.google.gson.GsonBuilder;

/**
 * General differential-pressure (orifice plate) metering calculator covering liquid/NGL and steam services that are not
 * handled by {@link Standard_AGA3} (which is specific to natural gas custody transfer). The calculations follow the
 * GPSA Engineering Data Book orifice metering procedure and the ISO 5167 / API 14.3 concentric orifice equation:
 *
 * <p>
 * W = C<sub>d</sub> &middot; E<sub>v</sub> &middot; Y &middot; (&pi;/4) d&sup2; &middot; &radic;(2 &middot; &Delta;P
 * &middot; &rho;<sub>1</sub>)
 * </p>
 *
 * <p>
 * where E<sub>v</sub> = 1/&radic;(1 - &beta;<sup>4</sup>) is the velocity-of-approach factor, Y is the expansion factor
 * (Y = 1 for incompressible liquid, and the compressible expansion factor for steam/vapour), and &rho;<sub>1</sub> is
 * the upstream density.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public class GpsaOrificeCalculator implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1L;

  /** Logger object for class. */
  private static final Logger logger = LogManager.getLogger(GpsaOrificeCalculator.class);

  /** Metered fluid service category. */
  public enum FluidService {
    /** Incompressible liquid or NGL (Y = 1). */
    LIQUID,
    /** Compressible steam or vapour (Y from isentropic exponent). */
    STEAM
  }

  /** Orifice bore diameter in meters. */
  private double orificeDiameter = 0.0508;

  /** Pipe internal diameter in meters. */
  private double pipeDiameter = 0.1023;

  /** Differential pressure across the orifice in Pa. */
  private double differentialPressure = 25000.0;

  /** Upstream static pressure in Pa absolute. */
  private double upstreamPressure = 1.0e6;

  /** Upstream fluid density in kg/m3. */
  private double upstreamDensity = 800.0;

  /** Discharge coefficient. */
  private double dischargeCoefficient = 0.61;

  /** Isentropic exponent (only used for steam/vapour expansion factor). */
  private double isentropicExponent = 1.3;

  /** Fluid service. */
  private FluidService fluidService = FluidService.LIQUID;

  // ====================== Results ======================
  private double betaRatio = 0.0;
  private double velocityOfApproachFactor = 0.0;
  private double expansionFactor = 1.0;
  private double massFlowRate = 0.0;
  private double volumetricFlowRate = 0.0;

  /**
   * Default constructor for GpsaOrificeCalculator.
   */
  public GpsaOrificeCalculator() {
  }

  /**
   * Sets the orifice and pipe geometry.
   *
   * @param orificeDiameterM orifice bore diameter in meters (must be &gt; 0)
   * @param pipeDiameterM pipe internal diameter in meters (must be &gt; orifice diameter)
   */
  public void setGeometry(double orificeDiameterM, double pipeDiameterM) {
    this.orificeDiameter = orificeDiameterM;
    this.pipeDiameter = pipeDiameterM;
  }

  /**
   * Sets the flowing conditions.
   *
   * @param differentialPressurePa differential pressure across the orifice in Pa (must be &ge; 0)
   * @param upstreamPressurePa upstream static pressure in Pa absolute (must be &gt; 0)
   * @param upstreamDensityKgM3 upstream density in kg/m3 (must be &gt; 0)
   */
  public void setFlowConditions(double differentialPressurePa, double upstreamPressurePa, double upstreamDensityKgM3) {
    this.differentialPressure = differentialPressurePa;
    this.upstreamPressure = upstreamPressurePa;
    this.upstreamDensity = upstreamDensityKgM3;
  }

  /**
   * Sets the fluid service and isentropic exponent.
   *
   * @param service the fluid service (LIQUID or STEAM, must not be null)
   * @param isentropicExp isentropic exponent (used only for STEAM, must be &gt; 1)
   */
  public void setFluidService(FluidService service, double isentropicExp) {
    this.fluidService = service;
    this.isentropicExponent = isentropicExp;
  }

  /**
   * Sets the discharge coefficient.
   *
   * @param cd discharge coefficient (typically 0.6-0.62, must be &gt; 0)
   */
  public void setDischargeCoefficient(double cd) {
    this.dischargeCoefficient = cd;
  }

  /**
   * Computes the expansion factor Y. For liquids Y = 1; for steam/vapour the ISO 5167 compressible-flow expansion
   * factor is used.
   *
   * @return expansion factor (dimensionless)
   */
  private double computeExpansionFactor() {
    if (fluidService == FluidService.LIQUID) {
      return 1.0;
    }
    double pressureRatio = (upstreamPressure - differentialPressure) / upstreamPressure;
    if (pressureRatio < 0.0) {
      pressureRatio = 0.0;
    }
    double beta4 = Math.pow(betaRatio, 4);
    double term = (isentropicExponent / (isentropicExponent - 1.0)) * Math.pow(pressureRatio, 2.0 / isentropicExponent)
        * (1.0 - Math.pow(pressureRatio, (isentropicExponent - 1.0) / isentropicExponent)) / (1.0 - pressureRatio);
    double y = Math.sqrt(term * (1.0 - beta4) / (1.0 - beta4 * Math.pow(pressureRatio, 2.0 / isentropicExponent)));
    if (Double.isNaN(y) || y <= 0.0) {
      y = 1.0;
    }
    return y;
  }

  /**
   * Runs the orifice mass and volumetric flow calculation.
   */
  public void calcFlow() {
    betaRatio = orificeDiameter / pipeDiameter;
    velocityOfApproachFactor = 1.0 / Math.sqrt(1.0 - Math.pow(betaRatio, 4));
    expansionFactor = computeExpansionFactor();

    double orificeArea = Math.PI / 4.0 * orificeDiameter * orificeDiameter;
    massFlowRate = dischargeCoefficient * velocityOfApproachFactor * expansionFactor * orificeArea
        * Math.sqrt(2.0 * differentialPressure * upstreamDensity);
    volumetricFlowRate = upstreamDensity > 0.0 ? massFlowRate / upstreamDensity : 0.0;

    logger.debug("GPSA orifice ({}): beta={}, Y={}, W={} kg/s", fluidService, betaRatio, expansionFactor, massFlowRate);
  }

  /**
   * Sizes the orifice bore required to produce a target mass flow at the configured conditions. Updates the orifice
   * diameter and re-runs the flow calculation.
   *
   * @param targetMassFlowKgS target mass flow rate in kg/s (must be &gt; 0)
   * @return the required orifice bore diameter in meters
   */
  public double sizeOrificeForFlow(double targetMassFlowKgS) {
    // Iterate because beta (and Y) depend on the bore.
    double bore = 0.5 * pipeDiameter;
    for (int i = 0; i < 50; i++) {
      orificeDiameter = bore;
      calcFlow();
      if (massFlowRate <= 0.0) {
        break;
      }
      double ratio = targetMassFlowKgS / massFlowRate;
      // mass flow scales ~ with bore^2; adjust bore by sqrt(ratio).
      double newBore = bore * Math.sqrt(ratio);
      if (newBore > 0.98 * pipeDiameter) {
        newBore = 0.98 * pipeDiameter;
      }
      if (Math.abs(newBore - bore) < 1e-7) {
        bore = newBore;
        break;
      }
      bore = newBore;
    }
    orificeDiameter = bore;
    calcFlow();
    return orificeDiameter;
  }

  /**
   * Returns the calculated mass flow rate.
   *
   * @return mass flow rate in kg/s
   */
  public double getMassFlowRate() {
    return massFlowRate;
  }

  /**
   * Returns the calculated volumetric flow rate at flowing conditions.
   *
   * @return volumetric flow rate in m3/s
   */
  public double getVolumetricFlowRate() {
    return volumetricFlowRate;
  }

  /**
   * Returns the beta ratio (d/D).
   *
   * @return beta ratio (dimensionless)
   */
  public double getBetaRatio() {
    return betaRatio;
  }

  /**
   * Returns the expansion factor used in the calculation.
   *
   * @return expansion factor (dimensionless)
   */
  public double getExpansionFactor() {
    return expansionFactor;
  }

  /**
   * Returns the orifice bore diameter (may have been updated by {@link #sizeOrificeForFlow(double)}).
   *
   * @return orifice bore diameter in meters
   */
  public double getOrificeDiameter() {
    return orificeDiameter;
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
