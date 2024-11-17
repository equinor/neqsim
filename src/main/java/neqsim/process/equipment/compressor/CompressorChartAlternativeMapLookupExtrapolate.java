package neqsim.process.equipment.compressor;

import java.util.ArrayList;
import org.apache.commons.math3.analysis.interpolation.SplineInterpolator;
import org.apache.commons.math3.analysis.polynomials.PolynomialSplineFunction;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * This class is an implementation of the compressor chart class that uses Fan laws and "double"
 * interpolation to navigate the compressor map (as opposed to the standard class using reduced
 * variables according to Fan laws).
 *
 * @author asmund
 * @version $Id: $Id
 */
public class CompressorChartAlternativeMapLookupExtrapolate
    extends CompressorChartAlternativeMapLookup {
  private static final long serialVersionUID = 1000;
  static Logger logger = LogManager.getLogger(CompressorChartAlternativeMapLookupExtrapolate.class);


  /**
   * <p>
   * Constructor for CompressorChartAlternativeMapLookupExtrapolate.
   * </p>
   */
  public CompressorChartAlternativeMapLookupExtrapolate() {}

  @Override
  public double getPolytropicHead(double flow, double speed) {
    ArrayList<Double> closestRefSpeeds = getClosestRefSpeeds(speed);
    SplineInterpolator interpolator = new SplineInterpolator();
    ArrayList<Double> interpolatedHeads = new ArrayList<>();

    for (double refSpeed : closestRefSpeeds) {
      CompressorCurve curve = getCurveAtRefSpeed(refSpeed);
      PolynomialSplineFunction spline = interpolator.interpolate(curve.flow, curve.head);

      double headValue = extrapolateOrInterpolate(flow, curve.flow, curve.head, spline);
      interpolatedHeads.add(headValue);
    }

    return interpolatedHeads.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
  }

  @Override
  public double getPolytropicEfficiency(double flow, double speed) {
    ArrayList<Double> closestRefSpeeds = getClosestRefSpeeds(speed);
    SplineInterpolator interpolator = new SplineInterpolator();
    ArrayList<Double> interpolatedEfficiencies = new ArrayList<>();

    for (double refSpeed : closestRefSpeeds) {
      CompressorCurve curve = getCurveAtRefSpeed(refSpeed);
      PolynomialSplineFunction spline =
          interpolator.interpolate(curve.flow, curve.polytropicEfficiency);

      double efficiencyValue =
          extrapolateOrInterpolate(flow, curve.flow, curve.polytropicEfficiency, spline);
      interpolatedEfficiencies.add(efficiencyValue);
    }

    return interpolatedEfficiencies.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
  }

  private double extrapolateOrInterpolate(double flow, double[] flowData, double[] valueData,
      PolynomialSplineFunction spline) {
    double[] knots = spline.getKnots();
    if (flow < knots[0]) {
      // Linear extrapolation below range using the first two points
      double slope = (valueData[1] - valueData[0]) / (flowData[1] - flowData[0]);
      return valueData[0] + slope * (flow - flowData[0]);
    } else if (flow > knots[knots.length - 1]) {
      // Linear extrapolation above range using the last two points
      int last = flowData.length - 1;
      double slope =
          (valueData[last] - valueData[last - 1]) / (flowData[last] - flowData[last - 1]);
      return valueData[last] + slope * (flow - flowData[last]);
    }
    // Interpolate within the range
    return spline.value(flow);
  }

}
