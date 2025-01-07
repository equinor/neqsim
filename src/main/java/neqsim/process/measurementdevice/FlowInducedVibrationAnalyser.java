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
  public void displayResult() {}

  /** {@inheritDoc} */
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
      double FVF = 1.0;
      if (GVF > 0.88) {
        if (GVF > 0.99) {
          FVF = Math.sqrt(pipe.getSegmentMixtureViscosity(segment) / Math.sqrt(0.001));
        } else {
          FVF = -27.882 * GVF * GVF + 45.545 * GVF - 17.495;
        }
      }
      double externalDiamater = (pipe.getDiameter() + 2 * pipe.getThickness()) * 1000;// mm
      double alpha = 0.0;
      double betta = 0.0;
      if (supportArrangement.equals("Stiff")) {
        alpha = 446187 + 646 * externalDiamater
            + 9.17E-4 * externalDiamater * externalDiamater * externalDiamater;
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
      if (GVF < 0.8) {
        return GVF;
      } else {
        return 1 + 5 * (1 - GVF) * Math.pow(pipe.getDiameter(), 1.6) * FRMSConstant
            * Math.pow(pipe.getSegmentLiquidDensity(segment), 0.6) * Math.pow(mixVelocity, 1.2);
      }
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
   * @param arrangement a {@link java.lang.String} object
   */
  public void setSupportArrangement(String arrangement) {
    this.supportArrangement = arrangement;
  }

  /**
   * <p>
   * Setter for the <code>supportDistance</code>.
   * </p>
   *
   * @param distance a {@link java.lang.Double} object
   */
  public void setSupportDistance(Double distance) {
    this.supportDistance = distance;
  }
}
