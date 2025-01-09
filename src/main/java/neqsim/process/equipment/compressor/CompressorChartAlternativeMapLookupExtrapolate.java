package neqsim.process.equipment.compressor;

import java.util.ArrayList;
import org.apache.commons.math3.analysis.interpolation.SplineInterpolator;
import org.apache.commons.math3.analysis.polynomials.PolynomialSplineFunction;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * <p>
 * CompressorChartAlternativeMapLookupExtrapolate class.
 * </p>
 *
 * @author ASMF
 */
public class CompressorChartAlternativeMapLookupExtrapolate
    extends CompressorChartAlternativeMapLookup {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(CompressorChartAlternativeMapLookupExtrapolate.class);

  /**
   * {@inheritDoc}
   *
   * <p>
   * Retrieves the closest reference speeds to the given speed from the compressor chart values. The
   * method returns a list containing one or two speeds: - If the given speed matches a reference
   * speed, the list contains only that speed. - If the given speed is between two reference speeds,
   * the list contains both speeds. - If the given speed is less than the lowest reference speed,
   * the list contains the lowest reference speed. - If the given speed is greater than the highest
   * reference speed, the list contains the highest reference speed.
   * </p>
   */
  @Override
  public ArrayList<Double> getClosestRefSpeeds(double speed) {
    ArrayList<Double> closestSpeeds = new ArrayList<>();
    for (CompressorCurve curve : chartValues) {
      closestSpeeds.add(curve.speed);
    }

    if (closestSpeeds.isEmpty()) {
      throw new IllegalStateException(
          "No reference speeds available. Ensure chartValues is populated.");
    }

    closestSpeeds.sort(Double::compareTo);

    ArrayList<Double> result = new ArrayList<>();
    for (int i = 0; i < closestSpeeds.size(); i++) {
      if (speed == closestSpeeds.get(i)) {
        result.add(speed);
        return result;
      }
      if (speed < closestSpeeds.get(i)) {
        if (i > 0) {
          result.add(closestSpeeds.get(i - 1));
        }
        result.add(closestSpeeds.get(i));
        return result;
      }
    }

    // Speed is greater than the highest reference speed
    result.add(closestSpeeds.get(closestSpeeds.size() - 1));
    return result;
  }

  /**
   * {@inheritDoc}
   *
   * Calculates the polytropic head for a given flow and speed by interpolating or extrapolating
   * between reference compressor curves.
   */
  @Override
  public double getPolytropicHead(double flow, double speed) {
    ArrayList<Double> closestRefSpeeds = getClosestRefSpeeds(speed);
    SplineInterpolator interpolator = new SplineInterpolator();
    ArrayList<Double> interpolatedHeads = new ArrayList<>();
    ArrayList<Double> speeds = new ArrayList<>();

    for (double refSpeed : closestRefSpeeds) {
      CompressorCurve curve = getCurveAtRefSpeed(refSpeed);
      PolynomialSplineFunction spline = interpolator.interpolate(curve.flow, curve.head);

      double headValue = extrapolateOrInterpolate(flow, curve.flow, curve.head, spline);
      interpolatedHeads.add(headValue);
      speeds.add(refSpeed);
    }

    if (interpolatedHeads.size() == 1) {
      return interpolatedHeads.get(0);
    }

    double speed1 = speeds.get(0);
    double speed2 = speeds.get(1);
    double head1 = interpolatedHeads.get(0);
    double head2 = interpolatedHeads.get(1);

    return extrapolateOrInterpolateSpeed(speed, speed1, speed2, head1, head2);
  }

  /**
   * {@inheritDoc}
   *
   * Calculates the polytropic efficiency for a given flow and speed by interpolating or
   * extrapolating between reference compressor curves.
   */
  @Override
  public double getPolytropicEfficiency(double flow, double speed) {
    ArrayList<Double> closestRefSpeeds = getClosestRefSpeeds(speed);

    if (closestRefSpeeds.isEmpty()) {
      throw new IllegalArgumentException(
          "No valid reference speeds found for the given speed: " + speed);
    }

    SplineInterpolator interpolator = new SplineInterpolator();
    ArrayList<Double> interpolatedEfficiencies = new ArrayList<>();
    ArrayList<Double> speeds = new ArrayList<>();

    for (double refSpeed : closestRefSpeeds) {
      CompressorCurve curve = getCurveAtRefSpeed(refSpeed);

      if (curve.flow.length == 0 || curve.polytropicEfficiency.length == 0) {
        throw new IllegalArgumentException("Invalid curve data for speed: " + refSpeed);
      }

      PolynomialSplineFunction spline =
          interpolator.interpolate(curve.flow, curve.polytropicEfficiency);
      double efficiencyValue =
          extrapolateOrInterpolate(flow, curve.flow, curve.polytropicEfficiency, spline);
      interpolatedEfficiencies.add(efficiencyValue);
      speeds.add(refSpeed);
    }

    if (interpolatedEfficiencies.size() == 1) {
      return interpolatedEfficiencies.get(0);
    }

    double speed1 = speeds.get(0);
    double speed2 = speeds.get(1);
    double eff1 = interpolatedEfficiencies.get(0);
    double eff2 = interpolatedEfficiencies.get(1);

    return extrapolateOrInterpolateSpeed(speed, speed1, speed2, eff1, eff2);
  }

  /**
   * Extrapolates or interpolates a value based on the given flow using the provided flow data,
   * value data, and polynomial spline function.
   *
   * @param flow the flow value for which the corresponding value needs to be determined
   * @param flowData an array of flow data points
   * @param valueData an array of value data points corresponding to the flow data points
   * @param spline a polynomial spline function created from the flow and value data points
   * @return the extrapolated or interpolated value corresponding to the given flow
   */
  private double extrapolateOrInterpolate(double flow, double[] flowData, double[] valueData,
      PolynomialSplineFunction spline) {
    double[] knots = spline.getKnots();

    if (flow < knots[0]) {
      logger.debug("Extrapolating below range: flow={}, knots[0]={}", flow, knots[0]);
      double slope = (valueData[1] - valueData[0]) / (flowData[1] - flowData[0]);
      return valueData[0] + slope * (flow - flowData[0]);
    } else if (flow > knots[knots.length - 1]) {
      logger.debug("Extrapolating above range: flow={}, knots[last]={}", flow,
          knots[knots.length - 1]);
      int last = flowData.length - 1;
      double slope =
          (valueData[last] - valueData[last - 1]) / (flowData[last] - flowData[last - 1]);
      return valueData[last] + slope * (flow - flowData[last]);
    }

    logger.debug("Interpolating within range: flow={}", flow);
    return spline.value(flow);
  }

  /**
   * Extrapolates or interpolates a value based on the given speed and two reference speeds with
   * their corresponding values.
   * 
   * @param speed the speed at which to extrapolate or interpolate the value
   * @param speed1 the first reference speed
   * @param speed2 the second reference speed
   * @param value1 the value corresponding to the first reference speed
   * @param value2 the value corresponding to the second reference speed
   * @return the extrapolated or interpolated value at the given speed
   */
  private double extrapolateOrInterpolateSpeed(double speed, double speed1, double speed2,
      double value1, double value2) {
    if (speed < speed1) {
      // Extrapolate below the range
      double slope = (value2 - value1) / (speed2 - speed1);
      return value1 + slope * (speed - speed1);
    } else if (speed > speed2) {
      // Extrapolate above the range
      double slope = (value2 - value1) / (speed2 - speed1);
      return value2 + slope * (speed - speed2);
    }
    return linearInterpolate(speed, speed1, speed2, value1, value2); // Interpolate within the range
  }

  /**
   * Performs linear interpolation to estimate the value of y at a given x, based on two known
   * points (x1, y1) and (x2, y2).
   *
   * @param x the x-value at which to interpolate
   * @param x1 the x-value of the first known point
   * @param x2 the x-value of the second known point
   * @param y1 the y-value of the first known point
   * @param y2 the y-value of the second known point
   * @return the interpolated y-value at the given x
   */
  private double linearInterpolate(double x, double x1, double x2, double y1, double y2) {
    return y1 + (y2 - y1) * (x - x1) / (x2 - x1);
  }
}
