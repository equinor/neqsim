package neqsim.process.mechanicaldesign.pump;

import java.io.Serializable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.google.gson.GsonBuilder;
import neqsim.process.equipment.pump.Pump;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;

/**
 * Standalone pump hydraulics and net positive suction head (NPSH) calculator.
 *
 * <p>
 * Computes pump hydraulic and brake power from flow, head, density and efficiency, and screens the suction side for
 * cavitation by comparing the available NPSH against the required NPSH:
 * </p>
 *
 * <ul>
 * <li>NPSHa = (p_suction - p_vapour) &middot; 1e5 / (&rho; &middot; g) + static head - friction loss;</li>
 * <li>NPSH margin = NPSHa - NPSHr;</li>
 * <li>cavitation risk flagged when the margin falls below a threshold;</li>
 * <li>hydraulic power = &rho; &middot; g &middot; Q &middot; H, brake power = hydraulic / efficiency.</li>
 * </ul>
 *
 * <p>
 * This is a companion screening calculator to {@code PumpMechanicalDesign} and the {@code Pump.getNPSHAvailable()} /
 * {@code Pump.getNPSHRequired()} accessors.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public class PumpHydraulicsNpshCalculator implements Serializable {
  private static final long serialVersionUID = 1L;

  /** Logger for this class. */
  private static final Logger logger = LogManager.getLogger(PumpHydraulicsNpshCalculator.class);

  /** Standard gravitational acceleration in m/s2. */
  private static final double GRAVITY = 9.80665;

  // ===== Inputs =====
  /** Volumetric flow rate in m3/h. */
  private double volumetricFlow = 100.0;
  /** Pump head in m. */
  private double head = 80.0;
  /** Fluid density in kg/m3. */
  private double density = 850.0;
  /** Pump hydraulic efficiency (0-1). */
  private double pumpEfficiency = 0.72;
  /** Suction absolute pressure in bar. */
  private double suctionPressure = 3.0;
  /** Fluid vapour pressure in bar (absolute). */
  private double vaporPressure = 1.0;
  /** Static suction head in m (positive for flooded suction). */
  private double staticHead = 2.0;
  /** Suction-line friction loss in m. */
  private double frictionLoss = 0.5;
  /** Required NPSH in m. */
  private double npshRequired = 3.0;
  /** Cavitation-margin threshold in m. */
  private double marginThreshold = 1.0;

  // ===== Results =====
  /** Available NPSH in m. */
  private double npshAvailable;
  /** NPSH margin (available minus required) in m. */
  private double npshMargin;
  /** True when the NPSH margin is below the threshold. */
  private boolean cavitationRisk;
  /** Hydraulic power in W. */
  private double hydraulicPower;
  /** Brake (shaft) power in W. */
  private double brakePower;

  /**
   * Default constructor for PumpHydraulicsNpshCalculator.
   */
  public PumpHydraulicsNpshCalculator() {
  }

  /**
   * Sets the duty point.
   *
   * @param volumetricFlowM3H volumetric flow rate in m3/h (must be &gt; 0)
   * @param headM pump head in m (must be &gt; 0)
   * @param densityKgM3 fluid density in kg/m3 (must be &gt; 0)
   * @param efficiency pump hydraulic efficiency (0-1, must be &gt; 0)
   */
  public void setDutyPoint(double volumetricFlowM3H, double headM, double densityKgM3, double efficiency) {
    this.volumetricFlow = volumetricFlowM3H;
    this.head = headM;
    this.density = densityKgM3;
    this.pumpEfficiency = efficiency;
  }

  /**
   * Sets the suction conditions and NPSH criteria.
   *
   * @param suctionPressureBara suction absolute pressure in bar (must be &gt; 0)
   * @param vaporPressureBara fluid vapour pressure in bar absolute (must be &ge; 0)
   * @param staticHeadM static suction head in m
   * @param frictionLossM suction-line friction loss in m (must be &ge; 0)
   * @param npshRequiredM required NPSH in m (must be &ge; 0)
   * @param marginThresholdM cavitation-margin threshold in m (must be &ge; 0)
   */
  public void setSuctionConditions(double suctionPressureBara, double vaporPressureBara, double staticHeadM,
      double frictionLossM, double npshRequiredM, double marginThresholdM) {
    this.suctionPressure = suctionPressureBara;
    this.vaporPressure = vaporPressureBara;
    this.staticHead = staticHeadM;
    this.frictionLoss = frictionLossM;
    this.npshRequired = npshRequiredM;
    this.marginThreshold = marginThresholdM;
  }

  /**
   * Populates the duty point directly from a NeqSim process {@link Pump}.
   *
   * <p>
   * Reads the volumetric flow, density and suction pressure from the pump inlet stream and derives the pump head from
   * the pressure rise across the pump (head = (p_out - p_in) &middot; 1e5 / (&rho; &middot; g)). When the pump exposes
   * a positive isentropic efficiency it is used as the pump efficiency. The suction-side NPSH criteria (vapour
   * pressure, static head, friction loss, required NPSH and margin threshold) are left unchanged so they can be
   * configured separately via {@link #setSuctionConditions}.
   * </p>
   *
   * @param pump the process pump supplying the duty point (must not be null and must have inlet and outlet streams)
   */
  public void fromPump(Pump pump) {
    if (pump == null) {
      throw new IllegalArgumentException("pump cannot be null");
    }
    StreamInterface inlet = pump.getInletStream();
    StreamInterface outlet = pump.getOutletStream();
    if (inlet == null || outlet == null) {
      throw new IllegalArgumentException("pump must have inlet and outlet streams");
    }
    SystemInterface fluid = inlet.getFluid();
    double rho = fluid.getDensity("kg/m3");
    this.density = rho;
    this.volumetricFlow = fluid.getFlowRate("m3/sec") * 3600.0;
    double inletPressureBara = inlet.getPressure("bara");
    double outletPressureBara = outlet.getPressure("bara");
    this.suctionPressure = inletPressureBara;
    this.head = (outletPressureBara - inletPressureBara) * 1.0e5 / Math.max(rho * GRAVITY, 1.0e-9);
    double efficiency = pump.getIsentropicEfficiency();
    if (efficiency > 0.0 && efficiency <= 1.0) {
      this.pumpEfficiency = efficiency;
    }
    logger.debug("Populated pump hydraulics from pump: Q={} m3/h, H={} m, rho={} kg/m3, p_suction={} bara",
        this.volumetricFlow, this.head, this.density, this.suctionPressure);
  }

  /**
   * Runs the pump hydraulics and NPSH screening calculation.
   */
  public void calcHydraulics() {
    double pressureHead = (suctionPressure - vaporPressure) * 1.0e5 / (density * GRAVITY);
    npshAvailable = pressureHead + staticHead - frictionLoss;
    npshMargin = npshAvailable - npshRequired;
    cavitationRisk = npshMargin < marginThreshold;

    double flowM3S = volumetricFlow / 3600.0;
    hydraulicPower = density * GRAVITY * flowM3S * head;
    brakePower = hydraulicPower / Math.max(pumpEfficiency, 1.0e-9);

    logger.debug("Pump hydraulics: NPSHa={} m, margin={} m, cav={}, Phyd={} W, Pbrake={} W", npshAvailable, npshMargin,
        cavitationRisk, hydraulicPower, brakePower);
  }

  /**
   * Returns the available NPSH.
   *
   * @return available NPSH in m
   */
  public double getNpshAvailable() {
    return npshAvailable;
  }

  /**
   * Returns the NPSH margin.
   *
   * @return NPSH margin in m
   */
  public double getNpshMargin() {
    return npshMargin;
  }

  /**
   * Returns whether there is a cavitation risk.
   *
   * @return true when the NPSH margin is below the threshold
   */
  public boolean isCavitationRisk() {
    return cavitationRisk;
  }

  /**
   * Returns the hydraulic power.
   *
   * @return hydraulic power in W
   */
  public double getHydraulicPower() {
    return hydraulicPower;
  }

  /**
   * Returns the brake (shaft) power.
   *
   * @return brake power in W
   */
  public double getBrakePower() {
    return brakePower;
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
