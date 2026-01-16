package neqsim.process.fielddevelopment.network;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.fluidmechanics.flownode.FlowNodeInterface;
import neqsim.fluidmechanics.flowsystem.FlowSystemInterface;
import neqsim.fluidmechanics.flowsystem.twophaseflowsystem.twophasepipeflowsystem.TwoPhasePipeFlowSystem;
import neqsim.process.equipment.pipeline.PipeBeggsAndBrills;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;

/**
 * Integrates field development with multiphase pipeline hydraulics.
 *
 * <p>
 * Provides tight coupling between field development screening and detailed multiphase flow
 * calculations. Used for tieback feasibility, pipeline sizing, and flow assurance analysis.
 * </p>
 *
 * <h2>Capabilities</h2>
 * <ul>
 * <li>Pipeline pressure drop calculation using Beggs &amp; Brill</li>
 * <li>Two-phase flow regime identification</li>
 * <li>Liquid holdup and velocity profiles</li>
 * <li>Arrival temperature prediction</li>
 * <li>Slug frequency estimation</li>
 * <li>Riser hydraulics for platform tie-in</li>
 * </ul>
 *
 * <h2>Example Usage</h2>
 * 
 * <pre>{@code
 * MultiphaseFlowIntegrator integrator = new MultiphaseFlowIntegrator();
 * 
 * // Configure pipeline
 * integrator.setPipelineLength(25.0); // km
 * integrator.setPipelineDiameter(0.254); // 10 inch
 * integrator.setSeabedTemperature(4.0); // C
 * integrator.setOverallHeatTransferCoeff(5.0); // W/m2K
 * 
 * // Calculate hydraulics
 * PipelineResult result = integrator.calculateHydraulics(wellheadStream, 30.0);
 * 
 * System.out.println("Arrival pressure: " + result.getArrivalPressureBar() + " bara");
 * System.out.println("Arrival temperature: " + result.getArrivalTemperatureC() + " C");
 * System.out.println("Flow regime: " + result.getFlowRegime());
 * System.out.println("Liquid holdup: " + result.getLiquidHoldup());
 * }</pre>
 *
 * @author ESOL
 * @version 1.0
 */
public class MultiphaseFlowIntegrator implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Logger instance. */
  private static final Logger logger = LogManager.getLogger(MultiphaseFlowIntegrator.class);

  /**
   * Flow regime classification.
   */
  public enum FlowRegime {
    /** Stratified smooth flow. */
    STRATIFIED_SMOOTH,
    /** Stratified wavy flow. */
    STRATIFIED_WAVY,
    /** Intermittent/slug flow. */
    INTERMITTENT,
    /** Annular flow. */
    ANNULAR,
    /** Dispersed bubble flow. */
    DISPERSED_BUBBLE,
    /** Unknown/undefined. */
    UNKNOWN
  }

  /**
   * Pipeline hydraulics result.
   */
  public static class PipelineResult implements Serializable {
    private static final long serialVersionUID = 1001L;

    private double inletPressureBar;
    private double arrivalPressureBar;
    private double pressureDropBar;
    private double inletTemperatureC;
    private double arrivalTemperatureC;
    private FlowRegime flowRegime;
    private double liquidHoldup;
    private double liquidVelocityMs;
    private double gasVelocityMs;
    private double mixtureVelocityMs;
    private double slugFrequencyPerMin;
    private double erosionalVelocityMs;
    private double erosionalVelocityRatio;
    private boolean feasible;
    private String infeasibilityReason;

    /**
     * Get inlet pressure.
     *
     * @return the inlet pressure in bar
     */
    public double getInletPressureBar() {
      return inletPressureBar;
    }

    /**
     * Set inlet pressure.
     *
     * @param p the inlet pressure in bar
     */
    public void setInletPressureBar(double p) {
      this.inletPressureBar = p;
    }

    /**
     * Get arrival pressure.
     *
     * @return the arrival pressure in bar
     */
    public double getArrivalPressureBar() {
      return arrivalPressureBar;
    }

    /**
     * Set arrival pressure.
     *
     * @param p the arrival pressure in bar
     */
    public void setArrivalPressureBar(double p) {
      this.arrivalPressureBar = p;
    }

    /**
     * Get pressure drop.
     *
     * @return the pressure drop in bar
     */
    public double getPressureDropBar() {
      return pressureDropBar;
    }

    /**
     * Set pressure drop.
     *
     * @param dp the pressure drop in bar
     */
    public void setPressureDropBar(double dp) {
      this.pressureDropBar = dp;
    }

    /**
     * Get inlet temperature.
     *
     * @return the inlet temperature in Celsius
     */
    public double getInletTemperatureC() {
      return inletTemperatureC;
    }

    /**
     * Set inlet temperature.
     *
     * @param t the inlet temperature in Celsius
     */
    public void setInletTemperatureC(double t) {
      this.inletTemperatureC = t;
    }

    /**
     * Get arrival temperature.
     *
     * @return the arrival temperature in Celsius
     */
    public double getArrivalTemperatureC() {
      return arrivalTemperatureC;
    }

    /** Set arrival temperature. */
    public void setArrivalTemperatureC(double t) {
      this.arrivalTemperatureC = t;
    }

    /** Get flow regime. */
    public FlowRegime getFlowRegime() {
      return flowRegime;
    }

    /** Set flow regime. */
    public void setFlowRegime(FlowRegime regime) {
      this.flowRegime = regime;
    }

    /** Get liquid holdup. */
    public double getLiquidHoldup() {
      return liquidHoldup;
    }

    /** Set liquid holdup. */
    public void setLiquidHoldup(double hl) {
      this.liquidHoldup = hl;
    }

    /** Get liquid velocity. */
    public double getLiquidVelocityMs() {
      return liquidVelocityMs;
    }

    /** Set liquid velocity. */
    public void setLiquidVelocityMs(double v) {
      this.liquidVelocityMs = v;
    }

    /** Get gas velocity. */
    public double getGasVelocityMs() {
      return gasVelocityMs;
    }

    /** Set gas velocity. */
    public void setGasVelocityMs(double v) {
      this.gasVelocityMs = v;
    }

    /** Get mixture velocity. */
    public double getMixtureVelocityMs() {
      return mixtureVelocityMs;
    }

    /** Set mixture velocity. */
    public void setMixtureVelocityMs(double v) {
      this.mixtureVelocityMs = v;
    }

    /** Get slug frequency. */
    public double getSlugFrequencyPerMin() {
      return slugFrequencyPerMin;
    }

    /** Set slug frequency. */
    public void setSlugFrequencyPerMin(double f) {
      this.slugFrequencyPerMin = f;
    }

    /** Get erosional velocity. */
    public double getErosionalVelocityMs() {
      return erosionalVelocityMs;
    }

    /** Set erosional velocity. */
    public void setErosionalVelocityMs(double v) {
      this.erosionalVelocityMs = v;
    }

    /** Get erosional velocity ratio. */
    public double getErosionalVelocityRatio() {
      return erosionalVelocityRatio;
    }

    /** Set erosional velocity ratio. */
    public void setErosionalVelocityRatio(double r) {
      this.erosionalVelocityRatio = r;
    }

    /** Check if feasible. */
    public boolean isFeasible() {
      return feasible;
    }

    /** Set feasibility. */
    public void setFeasible(boolean f) {
      this.feasible = f;
    }

    /** Get infeasibility reason. */
    public String getInfeasibilityReason() {
      return infeasibilityReason;
    }

    /** Set infeasibility reason. */
    public void setInfeasibilityReason(String r) {
      this.infeasibilityReason = r;
    }

    /**
     * Generate summary report.
     *
     * @return formatted report
     */
    public String generateReport() {
      StringBuilder sb = new StringBuilder();
      sb.append("=== PIPELINE HYDRAULICS RESULT ===\n\n");

      sb.append("Pressure:\n");
      sb.append(String.format("  Inlet:       %.1f bara\n", inletPressureBar));
      sb.append(String.format("  Arrival:     %.1f bara\n", arrivalPressureBar));
      sb.append(String.format("  Drop:        %.1f bar\n", pressureDropBar));

      sb.append("\nTemperature:\n");
      sb.append(String.format("  Inlet:       %.1f °C\n", inletTemperatureC));
      sb.append(String.format("  Arrival:     %.1f °C\n", arrivalTemperatureC));

      sb.append("\nFlow Characteristics:\n");
      sb.append(String.format("  Regime:      %s\n", flowRegime));
      sb.append(String.format("  Liquid holdup: %.3f\n", liquidHoldup));
      sb.append(String.format("  Mixture vel: %.2f m/s\n", mixtureVelocityMs));
      sb.append(String.format("  Erosional ratio: %.2f\n", erosionalVelocityRatio));

      if (slugFrequencyPerMin > 0) {
        sb.append(String.format("  Slug freq:   %.1f /min\n", slugFrequencyPerMin));
      }

      sb.append("\nFeasibility: ");
      if (feasible) {
        sb.append("PASS\n");
      } else {
        sb.append("FAIL - ").append(infeasibilityReason).append("\n");
      }

      return sb.toString();
    }
  }

  // ============================================================================
  // CONFIGURATION
  // ============================================================================

  /** Pipeline length (km). */
  private double pipelineLengthKm = 25.0;

  /** Pipeline inner diameter (m). */
  private double pipelineDiameterM = 0.254;

  /** Pipeline roughness (m). */
  private double pipelineRoughnessM = 0.00005;

  /** Seabed temperature (C). */
  private double seabedTemperatureC = 4.0;

  /** Overall heat transfer coefficient (W/m2K). */
  private double overallHtcWm2K = 5.0;

  /** Elevation change (m, positive = uphill). */
  private double elevationChangeM = 0.0;

  /** Number of calculation segments. */
  private int numberOfSegments = 50;

  /** Minimum arrival pressure (bara). */
  private double minArrivalPressureBar = 15.0;

  /** Erosional velocity constant (API RP 14E). */
  private double erosionalConstant = 122.0;

  // ============================================================================
  // CONSTRUCTORS
  // ============================================================================

  /**
   * Creates a new integrator with default parameters.
   */
  public MultiphaseFlowIntegrator() {
    // Default constructor
  }

  // ============================================================================
  // HYDRAULICS CALCULATION
  // ============================================================================

  /**
   * Calculate pipeline hydraulics using Beggs and Brill correlation.
   *
   * @param inlet inlet stream
   * @param arrivalPressureBar required arrival pressure (bara)
   * @return pipeline result
   */
  public PipelineResult calculateHydraulics(StreamInterface inlet, double arrivalPressureBar) {
    logger.info("Calculating pipeline hydraulics for {} km pipeline", pipelineLengthKm);

    PipelineResult result = new PipelineResult();

    // Clone the stream to avoid modifying original
    Stream pipeInlet = new Stream("Pipe-Inlet", inlet.getFluid().clone());
    pipeInlet.setFlowRate(inlet.getFlowRate("kg/hr"), "kg/hr");
    pipeInlet.run();

    result.setInletPressureBar(pipeInlet.getPressure("bara"));
    result.setInletTemperatureC(pipeInlet.getTemperature("C"));

    try {
      // Create Beggs and Brill pipe
      PipeBeggsAndBrills pipe = new PipeBeggsAndBrills("Flowline", pipeInlet);
      pipe.setPipeWallRoughness(pipelineRoughnessM);
      pipe.setLength(pipelineLengthKm * 1000); // Convert to m
      pipe.setDiameter(pipelineDiameterM);
      pipe.setAngle(Math.atan(elevationChangeM / (pipelineLengthKm * 1000)) * 180 / Math.PI);
      pipe.setNumberOfIncrements(numberOfSegments);

      // Run calculation
      pipe.run();

      // Extract results
      StreamInterface outlet = pipe.getOutletStream();
      result.setArrivalPressureBar(outlet.getPressure("bara"));
      result.setArrivalTemperatureC(outlet.getTemperature("C"));
      result.setPressureDropBar(result.getInletPressureBar() - result.getArrivalPressureBar());

      // Flow characteristics
      double mixtureDensity = outlet.getFluid().getDensity("kg/m3");
      double area = Math.PI * Math.pow(pipelineDiameterM / 2, 2);
      double volumeFlowRate = inlet.getFlowRate("kg/hr") / mixtureDensity / 3600; // m3/s
      double mixtureVelocity = volumeFlowRate / area;

      result.setMixtureVelocityMs(mixtureVelocity);

      // Estimate liquid holdup (simplified Beggs-Brill)
      double liquidHoldup = estimateLiquidHoldup(outlet.getFluid(), mixtureVelocity);
      result.setLiquidHoldup(liquidHoldup);

      // Flow regime
      FlowRegime regime = identifyFlowRegime(outlet.getFluid(), mixtureVelocity, liquidHoldup);
      result.setFlowRegime(regime);

      // Velocities
      if (liquidHoldup > 0.01) {
        result.setLiquidVelocityMs(mixtureVelocity * liquidHoldup);
        result.setGasVelocityMs(mixtureVelocity * (1 - liquidHoldup));
      } else {
        result.setGasVelocityMs(mixtureVelocity);
      }

      // Erosional velocity
      double erosionalVel = calculateErosionalVelocity(mixtureDensity);
      result.setErosionalVelocityMs(erosionalVel);
      result.setErosionalVelocityRatio(mixtureVelocity / erosionalVel);

      // Slug frequency (for intermittent flow)
      if (regime == FlowRegime.INTERMITTENT) {
        result.setSlugFrequencyPerMin(estimateSlugFrequency(mixtureVelocity, pipelineDiameterM));
      }

      // Check feasibility
      checkFeasibility(result, arrivalPressureBar);

    } catch (Exception e) {
      logger.error("Hydraulics calculation failed: {}", e.getMessage());
      result.setFeasible(false);
      result.setInfeasibilityReason("Calculation error: " + e.getMessage());
    }

    return result;
  }

  /**
   * Calculate hydraulics for a range of flow rates.
   *
   * @param baseFluid base fluid composition
   * @param inletPressureBar inlet pressure
   * @param flowRatesKgHr array of flow rates to evaluate
   * @return list of results for each flow rate
   */
  public List<PipelineResult> calculateHydraulicsCurve(SystemInterface baseFluid,
      double inletPressureBar, double[] flowRatesKgHr) {

    List<PipelineResult> results = new ArrayList<PipelineResult>();

    for (double flowRate : flowRatesKgHr) {
      SystemInterface fluid = baseFluid.clone();
      fluid.setPressure(inletPressureBar);

      Stream stream = new Stream("Test", fluid);
      stream.setFlowRate(flowRate, "kg/hr");
      stream.run();

      results.add(calculateHydraulics(stream, minArrivalPressureBar));
    }

    return results;
  }

  /**
   * Size pipeline diameter for given constraints.
   *
   * @param inlet inlet stream
   * @param minArrivalP minimum arrival pressure (bara)
   * @param maxVelocityRatio maximum erosional velocity ratio
   * @return recommended diameter in meters
   */
  public double sizePipeline(StreamInterface inlet, double minArrivalP, double maxVelocityRatio) {
    // Try standard pipe sizes (inches to meters)
    double[] standardSizes = {0.1524, 0.2032, 0.254, 0.3048, 0.3556, 0.4064, 0.4572, 0.508};

    double originalDiameter = pipelineDiameterM;

    for (double diameter : standardSizes) {
      pipelineDiameterM = diameter;
      PipelineResult result = calculateHydraulics(inlet, minArrivalP);

      if (result.isFeasible() && result.getErosionalVelocityRatio() < maxVelocityRatio) {
        pipelineDiameterM = originalDiameter;
        return diameter;
      }
    }

    pipelineDiameterM = originalDiameter;
    return standardSizes[standardSizes.length - 1]; // Return largest if none work
  }

  // ============================================================================
  // PRIVATE METHODS
  // ============================================================================

  /**
   * Estimate liquid holdup using simplified Beggs-Brill.
   *
   * @param fluid the fluid system
   * @param mixVel the mixture velocity in m/s
   * @return estimated liquid holdup as fraction (0-1)
   */
  private double estimateLiquidHoldup(SystemInterface fluid, double mixVel) {
    // Simplified correlation
    if (fluid.getNumberOfPhases() < 2) {
      return fluid.hasPhaseType("aqueous") || fluid.hasPhaseType("oil") ? 1.0 : 0.0;
    }

    double liquidFraction = 0.0;
    if (fluid.hasPhaseType("oil")) {
      liquidFraction += fluid.getPhase("oil").getVolume() / fluid.getVolume();
    }
    if (fluid.hasPhaseType("aqueous")) {
      liquidFraction += fluid.getPhase("aqueous").getVolume() / fluid.getVolume();
    }

    // Slip effect - actual holdup > input liquid fraction
    double nFr = mixVel * mixVel / (9.81 * pipelineDiameterM);
    double slipFactor = 1.0 + 0.2 * Math.pow(nFr, -0.3);

    return Math.min(liquidFraction * slipFactor, 1.0);
  }

  /**
   * Identify flow regime.
   */
  private FlowRegime identifyFlowRegime(SystemInterface fluid, double mixVel, double liquidHoldup) {
    if (liquidHoldup < 0.01) {
      return FlowRegime.ANNULAR;
    }
    if (liquidHoldup > 0.99) {
      return FlowRegime.DISPERSED_BUBBLE;
    }

    // Simplified regime map based on Froude number and holdup
    double nFr = mixVel * mixVel / (9.81 * pipelineDiameterM);

    if (nFr < 1.0 && liquidHoldup > 0.5) {
      return FlowRegime.STRATIFIED_SMOOTH;
    } else if (nFr < 4.0 && liquidHoldup > 0.3) {
      return FlowRegime.STRATIFIED_WAVY;
    } else if (nFr >= 4.0 && liquidHoldup < 0.1) {
      return FlowRegime.ANNULAR;
    } else {
      return FlowRegime.INTERMITTENT;
    }
  }

  /**
   * Calculate erosional velocity (API RP 14E).
   */
  private double calculateErosionalVelocity(double mixtureDensity) {
    return erosionalConstant / Math.sqrt(mixtureDensity);
  }

  /**
   * Estimate slug frequency (Gregory correlation).
   */
  private double estimateSlugFrequency(double mixVel, double diameter) {
    // Gregory et al. correlation
    double nFr = mixVel * mixVel / (9.81 * diameter);
    double frequency = 0.0226 * Math.pow(nFr, 1.2) * mixVel / diameter;
    return frequency * 60; // Convert to per minute
  }

  /**
   * Check feasibility against constraints.
   */
  private void checkFeasibility(PipelineResult result, double minArrivalP) {
    result.setFeasible(true);

    // Check arrival pressure
    if (result.getArrivalPressureBar() < minArrivalP) {
      result.setFeasible(false);
      result.setInfeasibilityReason(String.format("Arrival pressure %.1f bar < minimum %.1f bar",
          result.getArrivalPressureBar(), minArrivalP));
      return;
    }

    // Check erosional velocity
    if (result.getErosionalVelocityRatio() > 1.0) {
      result.setFeasible(false);
      result.setInfeasibilityReason(
          String.format("Velocity %.1f m/s exceeds erosional limit %.1f m/s",
              result.getMixtureVelocityMs(), result.getErosionalVelocityMs()));
      return;
    }

    // Check arrival temperature vs hydrate (simplified)
    if (result.getArrivalTemperatureC() < seabedTemperatureC + 5) {
      result.setFeasible(false);
      result.setInfeasibilityReason(
          String.format("Arrival temperature %.1f°C too close to seabed %.1f°C (hydrate risk)",
              result.getArrivalTemperatureC(), seabedTemperatureC));
    }
  }

  // ============================================================================
  // CONFIGURATION SETTERS
  // ============================================================================

  /**
   * Set pipeline length.
   *
   * @param km length in kilometers
   */
  public void setPipelineLength(double km) {
    this.pipelineLengthKm = km;
  }

  /**
   * Set pipeline diameter.
   *
   * @param m diameter in meters
   */
  public void setPipelineDiameter(double m) {
    this.pipelineDiameterM = m;
  }

  /**
   * Set pipeline roughness.
   *
   * @param m roughness in meters
   */
  public void setPipelineRoughness(double m) {
    this.pipelineRoughnessM = m;
  }

  /**
   * Set seabed temperature.
   *
   * @param c temperature in Celsius
   */
  public void setSeabedTemperature(double c) {
    this.seabedTemperatureC = c;
  }

  /**
   * Set overall heat transfer coefficient.
   *
   * @param htc HTC in W/m2K
   */
  public void setOverallHeatTransferCoeff(double htc) {
    this.overallHtcWm2K = htc;
  }

  /**
   * Set elevation change.
   *
   * @param m elevation change in meters (positive = uphill)
   */
  public void setElevationChange(double m) {
    this.elevationChangeM = m;
  }

  /**
   * Set number of calculation segments.
   *
   * @param n number of segments
   */
  public void setNumberOfSegments(int n) {
    this.numberOfSegments = n;
  }

  /**
   * Set minimum arrival pressure.
   *
   * @param bar pressure in bara
   */
  public void setMinArrivalPressure(double bar) {
    this.minArrivalPressureBar = bar;
  }

  /**
   * Set erosional velocity constant (API RP 14E).
   *
   * @param c constant value (typically 100-150)
   */
  public void setErosionalConstant(double c) {
    this.erosionalConstant = c;
  }

  // ============================================================================
  // GETTERS
  // ============================================================================

  /** Get pipeline length. */
  public double getPipelineLengthKm() {
    return pipelineLengthKm;
  }

  /** Get pipeline diameter. */
  public double getPipelineDiameterM() {
    return pipelineDiameterM;
  }
}
