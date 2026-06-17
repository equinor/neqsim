package neqsim.process.equipment.expander;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.compressor.CompressorChartKhader2015;
import neqsim.thermo.system.SystemInterface;

/**
 * TurboExpanderMapIngestion is a utility (P4) for building auditable performance maps from
 * digitised OEM data sheets (for example the Atlas Copco Rotoflow ACR00162 design and Case B
 * curves). It turns tabulated points into a
 * {@link neqsim.process.equipment.compressor.CompressorChartKhader2015} for the compressor side and
 * an {@link ExpanderChartKhader} for the expander side, and lets the two certified design points be
 * registered as anchors so the resulting model can be checked against the OEM curves.
 *
 * <p>
 * The class deliberately does no curve fitting of its own: it forwards the digitised arrays to the
 * existing Khader chart classes (which perform the Mach-number / sound-speed normalisation) and
 * stores the named anchor operating points so callers can validate the assembled map.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public class TurboExpanderMapIngestion implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Logger object for class. */
  static final Logger logger = LogManager.getLogger(TurboExpanderMapIngestion.class);

  /**
   * AnchorPoint captures a single certified operating point used to validate an assembled map.
   */
  public static class AnchorPoint implements Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1000L;
    /** Human-readable label, e.g. "Design 1998" or "Case B". */
    private final String label;
    /** Velocity ratio U/C at the anchor point. */
    private final double velocityRatio;
    /** IGV opening (fraction of maximum area) at the anchor point. */
    private final double igvOpening;
    /** Certified expander isentropic efficiency at the anchor point. */
    private final double expanderEfficiency;

    /**
     * Constructs an anchor point.
     *
     * @param label human-readable label
     * @param velocityRatio velocity ratio U/C
     * @param igvOpening IGV opening (fraction of maximum area)
     * @param expanderEfficiency certified expander isentropic efficiency (0..1)
     */
    public AnchorPoint(String label, double velocityRatio, double igvOpening,
        double expanderEfficiency) {
      this.label = label;
      this.velocityRatio = velocityRatio;
      this.igvOpening = igvOpening;
      this.expanderEfficiency = expanderEfficiency;
    }

    /**
     * Get the label.
     *
     * @return the anchor label
     */
    public String getLabel() {
      return label;
    }

    /**
     * Get the velocity ratio.
     *
     * @return the velocity ratio U/C
     */
    public double getVelocityRatio() {
      return velocityRatio;
    }

    /**
     * Get the IGV opening.
     *
     * @return the IGV opening (fraction of maximum area)
     */
    public double getIgvOpening() {
      return igvOpening;
    }

    /**
     * Get the certified expander efficiency.
     *
     * @return the certified expander isentropic efficiency
     */
    public double getExpanderEfficiency() {
      return expanderEfficiency;
    }
  }

  /** Registered anchor points used for validation. */
  private final List<AnchorPoint> anchors = new ArrayList<>();

  /** Reference fluid the OEM map was digitised on. */
  private SystemInterface referenceFluid = null;

  /** Compressor impeller outer diameter in m. */
  private double compressorImpellerDiameter = 0.3;

  /** Expander impeller outer diameter in m. */
  private double expanderImpellerDiameter = 0.424;

  /**
   * Default constructor.
   */
  public TurboExpanderMapIngestion() {}

  /**
   * Constructs a map-ingestion utility with a reference fluid and impeller diameters.
   *
   * @param referenceFluid the reference fluid the OEM curves were digitised on
   * @param compressorImpellerDiameter the compressor impeller outer diameter in m
   * @param expanderImpellerDiameter the expander impeller outer diameter in m
   */
  public TurboExpanderMapIngestion(SystemInterface referenceFluid,
      double compressorImpellerDiameter, double expanderImpellerDiameter) {
    this.referenceFluid = referenceFluid;
    this.compressorImpellerDiameter = compressorImpellerDiameter;
    this.expanderImpellerDiameter = expanderImpellerDiameter;
  }

  /**
   * Register a certified anchor operating point used to validate the assembled expander map.
   *
   * @param label human-readable label (e.g. "Design 1998", "Case B")
   * @param velocityRatio velocity ratio U/C at the anchor point
   * @param igvOpening IGV opening (fraction of maximum area)
   * @param expanderEfficiency certified expander isentropic efficiency (0..1)
   */
  public void addAnchorPoint(String label, double velocityRatio, double igvOpening,
      double expanderEfficiency) {
    anchors.add(new AnchorPoint(label, velocityRatio, igvOpening, expanderEfficiency));
  }

  /**
   * Get the registered anchor points.
   *
   * @return the list of anchor points
   */
  public List<AnchorPoint> getAnchorPoints() {
    return anchors;
  }

  /**
   * Build a Khader-style compressor chart from digitised OEM map points.
   *
   * @param processFluid the actual process fluid the chart will run on
   * @param chartConditions array with temperature [C], pressure [bara], (optionally density,
   *        molecular weight) of the reference conditions
   * @param speed array of speed lines in rpm
   * @param flow 2-D array of volumetric flows in m3/hr (one row per speed, strictly increasing per
   *        row)
   * @param head 2-D array of polytropic heads (one row per speed)
   * @param flowPolyEff 2-D array of flows for the efficiency curve (one row per speed)
   * @param polyEff 2-D array of polytropic efficiencies (one row per speed)
   * @return the assembled compressor chart
   */
  public CompressorChartKhader2015 buildCompressorChart(SystemInterface processFluid,
      double[] chartConditions, double[] speed, double[][] flow, double[][] head,
      double[][] flowPolyEff, double[][] polyEff) {
    CompressorChartKhader2015 chart;
    if (referenceFluid != null) {
      chart =
          new CompressorChartKhader2015(processFluid, referenceFluid, compressorImpellerDiameter);
    } else {
      chart = new CompressorChartKhader2015(processFluid, compressorImpellerDiameter);
    }
    chart.setHeadUnit("kJ/kg");
    chart.setCurves(chartConditions, speed, flow, head, flowPolyEff, polyEff);
    return chart;
  }

  /**
   * Build a Khader-style expander chart from digitised OEM map points.
   *
   * @param igvPositions array of IGV positions (fraction of maximum area, 0..1)
   * @param uc 2-D array of velocity ratios U/C (one row per IGV position)
   * @param eta 2-D array of isentropic efficiencies (one row per IGV position)
   * @param headDropKjPerKg 2-D array of isentropic stage head drops in kJ/kg (one row per IGV
   *        position)
   * @return the assembled expander chart
   */
  public ExpanderChartKhader buildExpanderChart(double[] igvPositions, double[][] uc,
      double[][] eta, double[][] headDropKjPerKg) {
    ExpanderChartKhader chart = new ExpanderChartKhader(referenceFluid, expanderImpellerDiameter);
    chart.setCurves(igvPositions, uc, eta, headDropKjPerKg);
    return chart;
  }

  /**
   * Validate an assembled expander chart against the registered anchor points.
   *
   * @param chart the expander chart to validate
   * @param tolerance the allowed absolute efficiency deviation (e.g. 0.02 for 2 efficiency points)
   * @return {@code true} if every anchor point is reproduced within the tolerance
   */
  public boolean validateExpanderChart(ExpanderChartKhader chart, double tolerance) {
    if (chart == null || !chart.isMapDefined()) {
      return false;
    }
    boolean ok = true;
    for (int i = 0; i < anchors.size(); i++) {
      AnchorPoint a = anchors.get(i);
      double predicted = chart.getEfficiency(a.getVelocityRatio(), a.getIgvOpening());
      double deviation = Math.abs(predicted - a.getExpanderEfficiency());
      if (deviation > tolerance) {
        ok = false;
        logger.warn("Anchor '" + a.getLabel() + "' deviates by " + deviation + " (predicted "
            + predicted + ", certified " + a.getExpanderEfficiency() + ")");
      }
    }
    return ok;
  }

  /**
   * Get the reference fluid.
   *
   * @return the reference fluid, or {@code null} if none is set
   */
  public SystemInterface getReferenceFluid() {
    return referenceFluid;
  }

  /**
   * Set the reference fluid the OEM curves were digitised on.
   *
   * @param referenceFluid the reference fluid
   */
  public void setReferenceFluid(SystemInterface referenceFluid) {
    this.referenceFluid = referenceFluid;
  }

  /**
   * Get the compressor impeller outer diameter.
   *
   * @return the compressor impeller outer diameter in m
   */
  public double getCompressorImpellerDiameter() {
    return compressorImpellerDiameter;
  }

  /**
   * Set the compressor impeller outer diameter.
   *
   * @param compressorImpellerDiameter the compressor impeller outer diameter in m
   */
  public void setCompressorImpellerDiameter(double compressorImpellerDiameter) {
    this.compressorImpellerDiameter = compressorImpellerDiameter;
  }

  /**
   * Get the expander impeller outer diameter.
   *
   * @return the expander impeller outer diameter in m
   */
  public double getExpanderImpellerDiameter() {
    return expanderImpellerDiameter;
  }

  /**
   * Set the expander impeller outer diameter.
   *
   * @param expanderImpellerDiameter the expander impeller outer diameter in m
   */
  public void setExpanderImpellerDiameter(double expanderImpellerDiameter) {
    this.expanderImpellerDiameter = expanderImpellerDiameter;
  }
}
