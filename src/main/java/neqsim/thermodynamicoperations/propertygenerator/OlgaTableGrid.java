package neqsim.thermodynamicoperations.propertygenerator;

import java.util.Arrays;

/**
 * One axis of an OLGA property table grid: a range or an explicit list, with optional phase-envelope refinement and
 * insertion of the standard-condition value.
 *
 * <p>
 * Envelope refinement: when the envelope maximum along the axis (cricondenbar for pressure, cricondentherm for
 * temperature) lies inside the range, a share gridFactor/(gridFactor+1) of the points is placed inside the envelope and
 * the rest outside, each part equidistant. Otherwise the axis stays equidistant. When the standard value lies inside
 * the range and is not a node, the axis is rebuilt with one point fewer and the standard value is inserted, so the
 * point count is unchanged.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
final class OlgaTableGrid {
  /** Relative tolerance for treating a value as an existing node. */
  private static final double NODE_TOLERANCE = 1.0e-9;

  private double minimum = Double.NaN;
  private double maximum = Double.NaN;
  private int count;
  private double[] explicitValues;
  private String lastMode = "equidistant";

  /**
   * Describes how the axis was last built.
   *
   * @return "explicit", "equidistant" or "envelope-refined", with "+ standard" when the standard value was inserted
   */
  String getLastMode() {
    return lastMode;
  }

  /**
   * Defines the axis as an equidistant range.
   *
   * @param min first value
   * @param max last value
   * @param numberOfPoints number of points
   */
  void setRange(double min, double max, int numberOfPoints) {
    this.minimum = min;
    this.maximum = max;
    this.count = numberOfPoints;
    this.explicitValues = null;
  }

  /**
   * Defines the axis as an explicit list of values.
   *
   * @param values axis values in any order
   * @return the sorted, de-duplicated values
   * @throws IllegalArgumentException if fewer than two distinct finite values are given
   */
  double[] setValues(double[] values) {
    if (values == null) {
      throw new IllegalArgumentException("Axis values must not be null");
    }
    double[] sorted = values.clone();
    Arrays.sort(sorted);
    double[] unique = new double[sorted.length];
    int n = 0;
    for (double value : sorted) {
      if (!Double.isFinite(value)) {
        throw new IllegalArgumentException("Axis values must be finite");
      }
      if (n == 0 || !sameNode(unique[n - 1], value)) {
        unique[n++] = value;
      }
    }
    if (n < 2) {
      throw new IllegalArgumentException("At least two distinct axis values are required");
    }
    explicitValues = Arrays.copyOf(unique, n);
    minimum = explicitValues[0];
    maximum = explicitValues[n - 1];
    count = n;
    return explicitValues.clone();
  }

  /**
   * Reports whether the axis has been defined.
   *
   * @return true after a range or list was set
   */
  boolean isDefined() {
    return count > 0;
  }

  /**
   * Builds the axis.
   *
   * @param refine true to apply envelope refinement to a range axis
   * @param gridFactor ratio of points inside to outside the envelope, 1 to 10
   * @param envelopeLow lowest envelope value along the axis, NaN when unknown
   * @param envelopeHigh envelope maximum along the axis, NaN when unknown
   * @param insertStandard true to insert the standard value when it lies inside the range
   * @param standardValue standard-condition value along the axis
   * @return ascending axis values
   */
  double[] build(boolean refine, double gridFactor, double envelopeLow, double envelopeHigh, boolean insertStandard,
      double standardValue) {
    if (explicitValues != null) {
      lastMode = "explicit";
      if (insertStandard && inside(standardValue) && !contains(explicitValues, standardValue)) {
        lastMode = "explicit + standard";
        return insert(explicitValues, standardValue);
      }
      return explicitValues.clone();
    }
    boolean refined = refine && refinedAxis(minimum, maximum, count, envelopeLow, envelopeHigh, gridFactor) != null;
    lastMode = refined ? "envelope-refined" : "equidistant";
    double[] axis = rangeAxis(count, refine, gridFactor, envelopeLow, envelopeHigh);
    if (!insertStandard || !inside(standardValue) || contains(axis, standardValue) || count < 3) {
      return axis;
    }
    lastMode = lastMode + " + standard";
    double[] reduced = rangeAxis(count - 1, refine, gridFactor, envelopeLow, envelopeHigh);
    if (!contains(reduced, standardValue)) {
      return insert(reduced, standardValue);
    }
    int nearest = 1;
    for (int i = 1; i < axis.length - 1; i++) {
      if (Math.abs(axis[i] - standardValue) < Math.abs(axis[nearest] - standardValue)) {
        nearest = i;
      }
    }
    axis[nearest] = standardValue;
    return axis;
  }

  /**
   * Builds a range axis with a given number of points.
   *
   * @param n number of points
   * @param refine true to apply envelope refinement
   * @param gridFactor ratio of points inside to outside the envelope
   * @param envelopeLow lowest envelope value along the axis
   * @param envelopeHigh envelope maximum along the axis
   * @return ascending axis values
   */
  private double[] rangeAxis(int n, boolean refine, double gridFactor, double envelopeLow, double envelopeHigh) {
    if (refine) {
      double[] refined = refinedAxis(minimum, maximum, n, envelopeLow, envelopeHigh, gridFactor);
      if (refined != null) {
        return refined;
      }
    }
    return uniform(minimum, maximum, n);
  }

  /**
   * Builds an equidistant axis.
   *
   * @param min first value
   * @param max last value
   * @param n number of points
   * @return axis values
   */
  static double[] uniform(double min, double max, int n) {
    double[] axis = new double[n];
    if (n == 1) {
      axis[0] = min;
      return axis;
    }
    double step = (max - min) / (n - 1.0);
    for (int i = 0; i < n; i++) {
      axis[i] = min + i * step;
    }
    axis[n - 1] = max;
    return axis;
  }

  /**
   * Builds an envelope-refined axis.
   *
   * @param min first value
   * @param max last value
   * @param n number of points
   * @param envelopeLow lowest envelope value along the axis
   * @param envelopeHigh envelope maximum along the axis
   * @param gridFactor ratio of points inside to outside the envelope
   * @return axis values, or null when refinement does not apply
   */
  static double[] refinedAxis(double min, double max, int n, double envelopeLow, double envelopeHigh,
      double gridFactor) {
    if (n < 4 || !Double.isFinite(envelopeHigh) || envelopeHigh <= min || envelopeHigh >= max) {
      return null;
    }
    double low = Double.isFinite(envelopeLow) ? Math.max(min, envelopeLow) : min;
    if (envelopeHigh - low <= 0.0) {
      return null;
    }
    double lowLength = low - min;
    double highLength = max - envelopeHigh;
    int outsideSegments = (lowLength > 0.0 ? 1 : 0) + 1;
    int inside = (int) Math.round(n * gridFactor / (gridFactor + 1.0));
    inside = Math.max(2, Math.min(inside, n - outsideSegments));
    int outside = n - inside;
    int below = 0;
    if (lowLength > 0.0) {
      below = (int) Math.round(outside * lowLength / (lowLength + highLength));
      below = Math.max(1, Math.min(below, outside - 1));
    }
    int above = outside - below;
    double[] axis = new double[n];
    int index = 0;
    for (int k = 0; k < below; k++) {
      axis[index++] = min + lowLength * k / below;
    }
    double[] insidePoints = uniform(low, envelopeHigh, inside);
    for (double value : insidePoints) {
      axis[index++] = value;
    }
    for (int k = 1; k <= above; k++) {
      axis[index++] = envelopeHigh + highLength * k / above;
    }
    axis[n - 1] = max;
    return axis;
  }

  /**
   * Checks whether a value lies inside the axis range.
   *
   * @param value candidate value
   * @return true when inside the closed range
   */
  private boolean inside(double value) {
    return Double.isFinite(value) && value >= minimum && value <= maximum;
  }

  /**
   * Checks whether two values denote the same node.
   *
   * @param a first value
   * @param b second value
   * @return true when equal within the node tolerance
   */
  private static boolean sameNode(double a, double b) {
    return Math.abs(a - b) <= NODE_TOLERANCE * Math.max(1.0, Math.max(Math.abs(a), Math.abs(b)));
  }

  /**
   * Checks whether an axis already holds a value.
   *
   * @param axis axis values
   * @param value candidate value
   * @return true when present
   */
  private static boolean contains(double[] axis, double value) {
    for (double node : axis) {
      if (sameNode(node, value)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Inserts a value into an ascending axis.
   *
   * @param axis ascending axis values
   * @param value value to insert
   * @return new ascending axis
   */
  private static double[] insert(double[] axis, double value) {
    double[] result = new double[axis.length + 1];
    int index = 0;
    boolean inserted = false;
    for (double node : axis) {
      if (!inserted && value < node) {
        result[index++] = value;
        inserted = true;
      }
      result[index++] = node;
    }
    if (!inserted) {
      result[index] = value;
    }
    return result;
  }
}
