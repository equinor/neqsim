package neqsim.process.measurementdevice;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.pipeline.PipeBeggsAndBrills;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * FlowInducedVibrationAnalyser class.
 * </p>
 *
 * @author SEROS
 * @version $Id: $Id
 */
public class FlowInducedVibrationAnalyser extends MeasurementDeviceBaseClass {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(WaterDewPointAnalyser.class);

  /**
   * Qualitative pipe-support-arrangement categories recognised by the Energy Institute LOF model.
   *
   * <p>
   * The LOF correlation classifies the support arrangement qualitatively (a measure of overall stiffness) rather than
   * by a precise support spacing. The order below runs from the stiffest (lowest predicted vibration) to the most
   * flexible (highest predicted vibration).
   * </p>
   */
  public static final String[] VALID_SUPPORT_ARRANGEMENTS = new String[] { "Stiff", "Medium stiff", "Medium",
      "Flexible" };

  /**
   * Informational support spacing in metres.
   *
   * <p>
   * <b>Not used by the LOF or FRMS correlations.</b> The Energy Institute LOF model selects its coefficients from the
   * qualitative {@link #supportArrangement} category, so this value is kept only for documentation / reporting (e.g. an
   * as-built spacing from a STID line list). To obtain a recommended support spacing use
   * {@code neqsim.process.mechanicaldesign.pipeline.TopsidePipingMechanicalDesignCalculator}.
   * </p>
   */
  private double supportDistance = 3;

  private Boolean calcSupportArrangement = false;

  private String supportArrangement = "Stiff"; // Consult with a mechanical engineer regarding
					       // either the support distance or
  // natural frequency of vibrations, especially if measurements have been taken

  private String method = "LOF"; // Likelihood of failure
  private PipeBeggsAndBrills pipe;
  private Boolean segmentSet = false;
  private int segment;
  private double FRMSConstant = 6.7;

  /**
   * <p>
   * Constructor for WaterDewPointAnalyser.
   * </p>
   *
   * @param pipe a {@link neqsim.process.equipment.pipeline.PipeBeggsAndBrills} object
   */
  public FlowInducedVibrationAnalyser(PipeBeggsAndBrills pipe) {
    this("Pipeline Flow Induced Vibration Analyzer", pipe);
  }

  /**
   * <p>
   * Constructor for FlowInducedVibrationAnalyser.
   * </p>
   *
   * @param name Name of FlowInducedVibrationAnalyser
   * @param pipe a {@link neqsim.process.equipment.pipeline.PipeBeggsAndBrills} object
   */
  public FlowInducedVibrationAnalyser(String name, PipeBeggsAndBrills pipe) {
    super(name, pipe.getName() + " FIV analyser");
    this.pipe = pipe;
  }

  /** {@inheritDoc} */
  @Override
  @ExcludeFromJacocoGeneratedReport
  public void displayResult() {
  }

  /**
   * {@inheritDoc}
   *
   * @throws IllegalStateException if the LOF method is requested but the pipe wall thickness is not a positive number.
   * The LOF correlation divides by the wall thickness (via the diameter-over-thickness ratio), so a zero or unset
   * thickness would otherwise yield a silent {@code NaN}/{@code Infinity}.
   */
  @Override
  public double getMeasuredValue(String unit) {
    if (!segmentSet) {
      segment = pipe.getNumberOfIncrements();
    }
    double mixDensity = pipe.getSegmentMixtureDensity(segment);
    double mixVelocity = pipe.getSegmentMixtureSuperficialVelocity(segment);
    double gasVelocity = pipe.getSegmentGasSuperficialVelocity(segment);
    double GVF = gasVelocity / mixVelocity;
    if (method.equals("LOF")) {
      if (!(pipe.getThickness() > 0.0)) {
	throw new IllegalStateException("FlowInducedVibrationAnalyser '" + getName()
	    + "': the LOF correlation requires a positive pipe wall thickness, but pipe '" + pipe.getName()
	    + "' has thickness " + pipe.getThickness() + " m. Set it with pipe.setThickness(wallThickness_m) "
	    + "(e.g. (nominalOD - scheduleID)/2 from the line list) before measuring.");
      }
      double FVF = 1.0;
      if (GVF > 0.88) {
	if (GVF > 0.99) {
	  FVF = Math.sqrt(pipe.getSegmentMixtureViscosity(segment) / Math.sqrt(0.001));
	} else {
	  FVF = -27.882 * GVF * GVF + 45.545 * GVF - 17.495;
	}
      } else if (GVF < 0.2) {
	FVF = 0.2 + 4 * GVF;
      }
      double externalDiamater = (pipe.getDiameter() + 2 * pipe.getThickness()) * 1000; // mm
      double alpha = 0.0;
      double betta = 0.0;
      if (supportArrangement.equals("Stiff")) {
	alpha = 446187 + 646 * externalDiamater + 9.17E-4 * externalDiamater * externalDiamater * externalDiamater;
	betta = 0.1 * Math.log(externalDiamater) - 1.3739;
      } else if (supportArrangement.equals("Medium stiff")) {
	alpha = 283921 + 370 * externalDiamater;
	betta = 0.1106 * Math.log(externalDiamater) - 1.501;
      } else if (supportArrangement.equals("Medium")) {
	alpha = 150412 + 209 * externalDiamater;
	betta = 0.0815 * Math.log(externalDiamater) - 1.3269;
      } else {
	alpha = 41.21 * Math.log(externalDiamater) + 49397;
	betta = 0.0815 * Math.log(externalDiamater) - 1.3842;
      }
      double diameterOverThickness = externalDiamater / (1000 * pipe.getThickness());
      double Fv = alpha * Math.pow(diameterOverThickness, betta);
      double LOF = mixDensity * mixVelocity * mixVelocity * FVF / Fv;
      return LOF;
    } else if (method.equals("FRMS")) {
      double C = Math.min(Math.min(1, 5 * (1 - GVF)), 5 * GVF) * FRMSConstant;
      return C * Math.pow(pipe.getDiameter(), 1.6) * Math.pow(pipe.getSegmentLiquidDensity(segment), 0.6)
	  * Math.pow(mixVelocity, 1.2);
    }
    return Double.NaN;
  }

  /**
   * <p>
   * Getter for the field <code>method</code>.
   * </p>
   *
   * @return a {@link java.lang.String} object
   */
  public String getMethod() {
    return method;
  }

  /**
   * <p>
   * Setter for the field <code>method</code>.
   * </p>
   *
   * @param method a {@link java.lang.String} object
   */
  public void setMethod(String method) {
    this.method = method;
  }

  /**
   * <p>
   * Setter for the field <code>segment</code>.
   * </p>
   *
   * @param segment a {@link java.lang.Double} object
   */
  public void setSegment(int segment) {
    this.segment = segment;
    this.segmentSet = true;
  }

  /**
   * <p>
   * setFRMSConstant.
   * </p>
   *
   * @param frms a double
   */
  public void setFRMSConstant(double frms) {
    this.FRMSConstant = frms;
  }

  /**
   * <p>
   * Setter for the <code>supportArrangement</code>.
   * </p>
   *
   * <p>
   * The Energy Institute LOF model selects its coefficients from a qualitative support-arrangement category, not from a
   * precise support spacing. Accepted values (case-insensitive, ignoring surrounding whitespace) are those in
   * {@link #VALID_SUPPORT_ARRANGEMENTS}: {@code "Stiff"}, {@code "Medium stiff"}, {@code "Medium"} and
   * {@code "Flexible"}. The value is normalised to its canonical spelling.
   * </p>
   *
   * @param arrangement a {@link java.lang.String} object
   * @throws IllegalArgumentException if {@code arrangement} is null or not one of the accepted categories
   */
  public void setSupportArrangement(String arrangement) {
    if (arrangement == null) {
      throw new IllegalArgumentException(
	  "supportArrangement can not be null. Valid values: " + validArrangementsString());
    }
    String trimmed = arrangement.trim();
    for (int i = 0; i < VALID_SUPPORT_ARRANGEMENTS.length; i++) {
      if (VALID_SUPPORT_ARRANGEMENTS[i].equalsIgnoreCase(trimmed)) {
	this.supportArrangement = VALID_SUPPORT_ARRANGEMENTS[i];
	return;
      }
    }
    throw new IllegalArgumentException(
	"supportArrangement '" + arrangement + "' is not recognised. Valid values: " + validArrangementsString());
  }

  /**
   * Build a human-readable list of the accepted support-arrangement categories.
   *
   * @return comma-separated, quoted list of valid arrangements
   */
  private static String validArrangementsString() {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < VALID_SUPPORT_ARRANGEMENTS.length; i++) {
      if (i > 0) {
	sb.append(", ");
      }
      sb.append('"').append(VALID_SUPPORT_ARRANGEMENTS[i]).append('"');
    }
    return sb.toString();
  }

  /**
   * <p>
   * Getter for the field <code>supportArrangement</code>.
   * </p>
   *
   * @return the qualitative support-arrangement category used by the LOF correlation
   */
  public String getSupportArrangement() {
    return supportArrangement;
  }

  /**
   * <p>
   * Setter for the <code>supportDistance</code>.
   * </p>
   *
   * <p>
   * <b>Informational only.</b> This value is not used by the LOF or FRMS correlations; the LOF model uses the
   * qualitative {@link #setSupportArrangement(String) support arrangement} instead. Use this field to record an
   * as-built spacing (e.g. from a STID line list) for reporting.
   * </p>
   *
   * @param distance support spacing in metres
   */
  public void setSupportDistance(Double distance) {
    this.supportDistance = distance;
  }

  /**
   * <p>
   * Getter for the field <code>supportDistance</code>.
   * </p>
   *
   * @return the informational support spacing in metres (not used by the LOF/FRMS correlations)
   */
  public double getSupportDistance() {
    return supportDistance;
  }
}
