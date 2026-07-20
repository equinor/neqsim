package neqsim.process.equipment.filter;

import java.io.Serializable;
import java.util.Arrays;

/**
 * Tabulated clean-filter differential-pressure versus actual volumetric-flow curve.
 *
 * <p>
 * This data object supports supplier curves and laboratory measurements such as ISO 3968 differential-pressure versus
 * flow characterization. Values within the range are linearly interpolated. The first segment is extended to zero
 * flow and the last segment is linearly extrapolated above the tested range.
 * </p>
 *
 * @author esol
 * @version 1.0
 */
public class FilterPressureDropCurve implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  private double[] flowRatesM3Hr = new double[0];
  private double[] pressureDropsBar = new double[0];
  private String testStandard = "";

  /** Empty pressure-drop curve. */
  public FilterPressureDropCurve() {}

  /**
   * Creates a pressure-drop curve.
   *
   * @param flowRatesM3Hr strictly increasing actual volumetric flow in m3/hr
   * @param pressureDropsBar non-negative pressure drops in bar
   */
  public FilterPressureDropCurve(double[] flowRatesM3Hr, double[] pressureDropsBar) {
    setPoints(flowRatesM3Hr, pressureDropsBar);
  }

  /**
   * Replaces all curve points.
   *
   * @param flowRatesM3Hr strictly increasing actual volumetric flow in m3/hr
   * @param pressureDropsBar non-negative pressure drops in bar
   */
  public final void setPoints(double[] flowRatesM3Hr, double[] pressureDropsBar) {
    validatePoints(flowRatesM3Hr, pressureDropsBar);
    this.flowRatesM3Hr = Arrays.copyOf(flowRatesM3Hr, flowRatesM3Hr.length);
    this.pressureDropsBar = Arrays.copyOf(pressureDropsBar, pressureDropsBar.length);
  }

  /**
   * Interpolates pressure drop at an actual volumetric flow.
   *
   * @param flowRateM3Hr actual volumetric flow in m3/hr
   * @return clean pressure drop in bar
   */
  public double getPressureDrop(double flowRateM3Hr) {
    if (flowRatesM3Hr.length == 0 || flowRateM3Hr <= 0.0) {
      return 0.0;
    }
    if (flowRateM3Hr <= flowRatesM3Hr[0]) {
      return pressureDropsBar[0] * flowRateM3Hr / flowRatesM3Hr[0];
    }
    for (int i = 1; i < flowRatesM3Hr.length; i++) {
      if (flowRateM3Hr <= flowRatesM3Hr[i]) {
        return interpolate(flowRateM3Hr, i - 1, i);
      }
    }
    int last = flowRatesM3Hr.length - 1;
    if (last == 0) {
      return pressureDropsBar[0] * flowRateM3Hr / flowRatesM3Hr[0];
    }
    return interpolate(flowRateM3Hr, last - 1, last);
  }

  /** @return number of supplied curve points */
  public int size() {
    return flowRatesM3Hr.length;
  }

  /** @return defensive copy of actual volumetric-flow points in m3/hr */
  public double[] getFlowRatesM3Hr() {
    return Arrays.copyOf(flowRatesM3Hr, flowRatesM3Hr.length);
  }

  /** @return defensive copy of differential-pressure points in bar */
  public double[] getPressureDropsBar() {
    return Arrays.copyOf(pressureDropsBar, pressureDropsBar.length);
  }

  /**
   * Records the standard or supplier method used to obtain the curve.
   *
   * @param testStandard reference such as {@code ISO 3968:2017}
   */
  public void setTestStandard(String testStandard) {
    this.testStandard = testStandard == null ? "" : testStandard;
  }

  /** @return recorded test standard or an empty string */
  public String getTestStandard() {
    return testStandard;
  }

  private double interpolate(double flowRateM3Hr, int lower, int upper) {
    double fraction = (flowRateM3Hr - flowRatesM3Hr[lower])
        / (flowRatesM3Hr[upper] - flowRatesM3Hr[lower]);
    return Math.max(0.0,
        pressureDropsBar[lower] + fraction * (pressureDropsBar[upper] - pressureDropsBar[lower]));
  }

  private void validatePoints(double[] flows, double[] pressureDrops) {
    if (flows == null || pressureDrops == null || flows.length == 0 || flows.length != pressureDrops.length) {
      throw new IllegalArgumentException("Flow rates and pressure drops must have the same non-zero length");
    }
    double previous = 0.0;
    double previousPressureDrop = 0.0;
    for (int i = 0; i < flows.length; i++) {
      if (!Double.isFinite(flows[i]) || flows[i] <= 0.0 || (i > 0 && flows[i] <= previous)) {
        throw new IllegalArgumentException("Flow rates must be finite, positive, and strictly increasing");
      }
      if (!Double.isFinite(pressureDrops[i]) || pressureDrops[i] < 0.0
          || (i > 0 && pressureDrops[i] < previousPressureDrop)) {
        throw new IllegalArgumentException("Pressure drops must be finite, non-negative, and non-decreasing");
      }
      previous = flows[i];
      previousPressureDrop = pressureDrops[i];
    }
  }
}
