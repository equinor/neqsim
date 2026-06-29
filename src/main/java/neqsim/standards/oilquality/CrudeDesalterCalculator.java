package neqsim.standards.oilquality;

import java.io.Serializable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.google.gson.GsonBuilder;
import neqsim.process.equipment.stream.StreamInterface;

/**
 * Crude-oil electrostatic desalter performance screening calculator.
 *
 * <p>
 * Estimates the residual salt content of crude oil leaving a one- or two-stage electrostatic desalter using a
 * wash-water dilution model. Each stage mixes the incoming brine with fresh wash water; the residual brine carried with
 * the oil is diluted by the effective wash water:
 * </p>
 *
 * <ul>
 * <li>effective wash fraction = wash-water fraction &middot; stage efficiency;</li>
 * <li>per-stage dilution = residual brine / (residual brine + effective wash);</li>
 * <li>one stage: S_out = S_in &middot; dilution;</li>
 * <li>two stages: S_out = S_in &middot; dilution&sup2;;</li>
 * <li>removal efficiency = 1 - S_out / S_in.</li>
 * </ul>
 *
 * <p>
 * This screening model complements {@link Standard_ASTM_D3230}, which measures salt content in crude oil.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public class CrudeDesalterCalculator implements Serializable {
  private static final long serialVersionUID = 1L;

  /** Logger for this class. */
  private static final Logger logger = LogManager.getLogger(CrudeDesalterCalculator.class);

  // ===== Inputs =====
  /** Inlet salt content in PTB (pounds of salt per thousand barrels) or mg/kg. */
  private double inletSaltContent = 50.0;
  /** Wash-water fraction as a fraction of crude (0-1). */
  private double washWaterFraction = 0.06;
  /** Mixing-valve pressure drop in bar. */
  private double mixingValveDp = 1.5;
  /** Number of desalting stages (1 or 2). */
  private int numberOfStages = 1;
  /** Per-stage water-wash efficiency (0-1). */
  private double stageEfficiency = 0.9;
  /** Residual brine volume fraction carried with the oil (0-1). */
  private double residualBrineVolFraction = 0.003;

  // ===== Results =====
  /** Effective wash-water fraction after applying stage efficiency. */
  private double effectiveWashFraction;
  /** Per-stage dilution factor. */
  private double stageDilution;
  /** Outlet salt content in the same units as the inlet. */
  private double outletSaltContent;
  /** Overall salt-removal efficiency (0-1). */
  private double removalEfficiency;

  /**
   * Default constructor for CrudeDesalterCalculator.
   */
  public CrudeDesalterCalculator() {
  }

  /**
   * Sets the feed and wash conditions.
   *
   * @param inletSaltContentValue inlet salt content in PTB or mg/kg (must be &gt; 0)
   * @param washWaterFractionValue wash-water fraction of crude (0-1)
   * @param mixingValveDpBar mixing-valve pressure drop in bar (must be &ge; 0)
   */
  public void setFeedConditions(double inletSaltContentValue, double washWaterFractionValue, double mixingValveDpBar) {
    this.inletSaltContent = inletSaltContentValue;
    this.washWaterFraction = washWaterFractionValue;
    this.mixingValveDp = mixingValveDpBar;
  }

  /**
   * Sets the desalter stage configuration.
   *
   * @param numberOfStagesValue number of desalting stages (1 or 2)
   * @param stageEfficiencyValue per-stage water-wash efficiency (0-1)
   * @param residualBrineVolFractionValue residual brine volume fraction with the oil (0-1)
   */
  public void setStageConfiguration(int numberOfStagesValue, double stageEfficiencyValue,
      double residualBrineVolFractionValue) {
    this.numberOfStages = numberOfStagesValue;
    this.stageEfficiency = stageEfficiencyValue;
    this.residualBrineVolFraction = residualBrineVolFractionValue;
  }

  /**
   * Populates the feed conditions directly from NeqSim process crude and wash-water streams.
   *
   * <p>
   * Computes the wash-water fraction as the ratio of the wash-water volumetric flow to the crude volumetric flow (both
   * streams must already have been run/flashed) and seeds the inlet salt content from the supplied measured value. The
   * mixing-valve pressure drop and stage configuration are left unchanged so they can be configured separately via
   * {@link #setFeedConditions} and {@link #setStageConfiguration}.
   * </p>
   *
   * @param crudeStream the inlet crude-oil process stream (must not be null and must carry a fluid)
   * @param washWaterStream the wash-water process stream (must not be null and must carry a fluid)
   * @param inletSaltContentValue the measured inlet salt content in PTB (pounds of salt per thousand barrels)
   */
  public void fromStreams(StreamInterface crudeStream, StreamInterface washWaterStream, double inletSaltContentValue) {
    if (crudeStream == null || washWaterStream == null) {
      throw new IllegalArgumentException("crudeStream and washWaterStream cannot be null");
    }
    double crudeVolM3PerS = crudeStream.getFluid().getFlowRate("m3/sec");
    double washVolM3PerS = washWaterStream.getFluid().getFlowRate("m3/sec");
    this.inletSaltContent = inletSaltContentValue;
    if (crudeVolM3PerS > 0.0) {
      this.washWaterFraction = washVolM3PerS / crudeVolM3PerS;
    }
    logger.debug("Populated desalter from streams: S_in={} PTB, washWaterFraction={}", this.inletSaltContent,
        this.washWaterFraction);
  }

  /**
   * Runs the desalter performance screening calculation.
   */
  public void calcPerformance() {
    effectiveWashFraction = washWaterFraction * stageEfficiency;
    stageDilution = residualBrineVolFraction / (residualBrineVolFraction + effectiveWashFraction);

    if (numberOfStages >= 2) {
      outletSaltContent = inletSaltContent * stageDilution * stageDilution;
    } else {
      outletSaltContent = inletSaltContent * stageDilution;
    }

    removalEfficiency = 1.0 - outletSaltContent / Math.max(inletSaltContent, 1.0e-9);

    logger.debug("Desalter: effWash={}, dilution={}, Sout={}, removal={}", effectiveWashFraction, stageDilution,
        outletSaltContent, removalEfficiency);
  }

  /**
   * Returns the effective wash-water fraction.
   *
   * @return effective wash-water fraction
   */
  public double getEffectiveWashFraction() {
    return effectiveWashFraction;
  }

  /**
   * Returns the per-stage dilution factor.
   *
   * @return per-stage dilution factor
   */
  public double getStageDilution() {
    return stageDilution;
  }

  /**
   * Returns the outlet salt content.
   *
   * @return outlet salt content in the same units as the inlet
   */
  public double getOutletSaltContent() {
    return outletSaltContent;
  }

  /**
   * Returns the overall salt-removal efficiency.
   *
   * @return removal efficiency (0-1)
   */
  public double getRemovalEfficiency() {
    return removalEfficiency;
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
