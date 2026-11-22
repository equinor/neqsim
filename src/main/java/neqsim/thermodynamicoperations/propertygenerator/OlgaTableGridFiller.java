package neqsim.thermodynamicoperations.propertygenerator;

/**
 * Grid post-processing shared by the OLGA PVT table generators.
 *
 * <p>
 * An OLGA table has a gas column and a liquid column at every (pressure, temperature) node, but a flash only returns
 * the phases that actually exist. At a node where one phase is absent the generators have no value to write, and
 * writing zero is not an option: OLGA rejects the file outright with <em>OIL DENSITY IS ZERO AT ...</em> or the gas
 * equivalent, so a single-phase fluid produced a table that could not be loaded at all.
 * </p>
 *
 * <p>
 * The absent-phase nodes are therefore filled by nearest-neighbour extrapolation from the nodes where the phase does
 * exist, measured in grid-index space. This is the same convention commercial table generators use: outside the
 * two-phase region the absent phase's properties are held at the boundary value so the table stays loadable and
 * continuous, and OLGA never evaluates them for a phase whose mass fraction is zero.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
final class OlgaTableGridFiller {

  private OlgaTableGridFiller() {
  }

  /**
   * Replace values at absent-phase nodes by the nearest node where the phase exists.
   *
   * <p>
   * Distance is the Manhattan distance in grid indices, which makes the fill independent of the pressure and
   * temperature units and of the grid spacing.
   * </p>
   *
   * @param values property values indexed [pressure][temperature], modified in place
   * @param present true where the phase exists and the value is usable
   * @param fallback value written when the phase exists nowhere on the grid
   * @return number of nodes that were filled
   */
  static int fillAbsentNodes(double[][] values, boolean[][] present, double fallback) {
    if (values == null || present == null) {
      return 0;
    }
    int numberOfPressures = values.length;
    if (numberOfPressures == 0) {
      return 0;
    }
    int numberOfTemperatures = values[0].length;

    boolean anyPresent = false;
    for (int i = 0; i < numberOfPressures && !anyPresent; i++) {
      for (int j = 0; j < numberOfTemperatures; j++) {
        if (present[i][j] && isUsable(values[i][j])) {
          anyPresent = true;
          break;
        }
      }
    }

    int filled = 0;
    if (!anyPresent) {
      for (int i = 0; i < numberOfPressures; i++) {
        for (int j = 0; j < numberOfTemperatures; j++) {
          values[i][j] = fallback;
          filled++;
        }
      }
      return filled;
    }

    double[][] source = new double[numberOfPressures][numberOfTemperatures];
    for (int i = 0; i < numberOfPressures; i++) {
      System.arraycopy(values[i], 0, source[i], 0, numberOfTemperatures);
    }

    for (int i = 0; i < numberOfPressures; i++) {
      for (int j = 0; j < numberOfTemperatures; j++) {
        if (present[i][j] && isUsable(source[i][j])) {
          continue;
        }
        values[i][j] = nearestUsable(source, present, i, j);
        filled++;
      }
    }
    return filled;
  }

  /**
   * Fill absent-phase nodes along one grid axis so the column stays monotonic and continuous.
   *
   * <p>
   * Along each grid line, a gap between two nodes where the phase exists is linearly interpolated, and nodes beyond the
   * last existing node are extrapolated with the phase's own gradient there: density along pressure with dRho/dP,
   * enthalpy along temperature with Cp, entropy along temperature with Cp ln(T). A missing or non-positive gradient is
   * replaced by the secant through the two outermost existing nodes, and by a flat extension if that is not positive
   * either. Lines without any existing node are left for {@link #fillAbsentNodes(double[][], boolean[][], double)}.
   * </p>
   *
   * @param values property values indexed [pressure][temperature], modified in place
   * @param gradient gradient of the property along the axis at the same nodes, may be null
   * @param present true where the phase exists and the value is usable
   * @param coordinates axis coordinates, Pa for pressure and K for temperature
   * @param alongTemperature true to fill along temperature, false along pressure
   * @param logarithmic true to integrate the gradient against ln(coordinate), used for entropy
   * @param minimumFraction lower bound on an extrapolated value as a fraction of its anchor, or 0 for no bound
   * @return presence mask extended with the filled nodes
   */
  static boolean[][] fillAlongAxis(double[][] values, double[][] gradient, boolean[][] present, double[] coordinates,
      boolean alongTemperature, boolean logarithmic, double minimumFraction) {
    int numberOfPressures = values.length;
    int numberOfTemperatures = numberOfPressures == 0 ? 0 : values[0].length;
    boolean[][] filled = new boolean[numberOfPressures][numberOfTemperatures];
    for (int i = 0; i < numberOfPressures; i++) {
      for (int j = 0; j < numberOfTemperatures; j++) {
        filled[i][j] = present[i][j] && isUsable(values[i][j]);
      }
    }
    int lines = alongTemperature ? numberOfPressures : numberOfTemperatures;
    int length = alongTemperature ? numberOfTemperatures : numberOfPressures;
    for (int line = 0; line < lines; line++) {
      int[] anchors = new int[length];
      int anchorCount = 0;
      for (int m = 0; m < length; m++) {
        if (filled[row(line, m, alongTemperature)][col(line, m, alongTemperature)]) {
          anchors[anchorCount++] = m;
        }
      }
      if (anchorCount == 0 || anchorCount == length) {
        continue;
      }
      int first = anchors[0];
      int last = anchors[anchorCount - 1];
      double lowSlope = anchorSlope(values, gradient, coordinates, line, alongTemperature, logarithmic, anchors,
          anchorCount, true);
      double highSlope = anchorSlope(values, gradient, coordinates, line, alongTemperature, logarithmic, anchors,
          anchorCount, false);
      for (int a = 0; a < anchorCount - 1; a++) {
        int left = anchors[a];
        int right = anchors[a + 1];
        double leftValue = get(values, line, left, alongTemperature);
        double rightValue = get(values, line, right, alongTemperature);
        for (int m = left + 1; m < right; m++) {
          double weight = (coordinates[m] - coordinates[left]) / (coordinates[right] - coordinates[left]);
          set(values, line, m, alongTemperature, leftValue + weight * (rightValue - leftValue));
        }
      }
      for (int m = 0; m < first; m++) {
        set(values, line, m, alongTemperature, extrapolate(get(values, line, first, alongTemperature), lowSlope,
            coordinates[first], coordinates[m], logarithmic, minimumFraction));
      }
      for (int m = last + 1; m < length; m++) {
        set(values, line, m, alongTemperature, extrapolate(get(values, line, last, alongTemperature), highSlope,
            coordinates[last], coordinates[m], logarithmic, minimumFraction));
      }
      for (int m = 0; m < length; m++) {
        filled[row(line, m, alongTemperature)][col(line, m, alongTemperature)] = true;
      }
    }
    return filled;
  }

  /**
   * Determine the extrapolation slope at the first or last existing node of a line.
   *
   * @param values property values
   * @param gradient analytic gradient, may be null
   * @param coordinates axis coordinates
   * @param line line index
   * @param alongTemperature axis orientation
   * @param logarithmic true when the slope is per ln(coordinate)
   * @param anchors indices of existing nodes along the line
   * @param anchorCount number of existing nodes
   * @param low true for the first existing node, false for the last
   * @return non-negative slope
   */
  private static double anchorSlope(double[][] values, double[][] gradient, double[] coordinates, int line,
      boolean alongTemperature, boolean logarithmic, int[] anchors, int anchorCount, boolean low) {
    int anchor = low ? anchors[0] : anchors[anchorCount - 1];
    if (gradient != null) {
      double slope = gradient[row(line, anchor, alongTemperature)][col(line, anchor, alongTemperature)];
      if (isUsable(slope) && slope > 0.0) {
        return slope;
      }
    }
    if (anchorCount >= 2) {
      int other = low ? anchors[1] : anchors[anchorCount - 2];
      double run = logarithmic ? Math.log(coordinates[other] / coordinates[anchor])
          : coordinates[other] - coordinates[anchor];
      double secant = (get(values, line, other, alongTemperature) - get(values, line, anchor, alongTemperature)) / run;
      if (isUsable(secant) && secant > 0.0) {
        return secant;
      }
    }
    return 0.0;
  }

  /**
   * Extrapolate from an anchor with a constant slope.
   *
   * @param anchorValue value at the anchor
   * @param slope slope per coordinate unit, or per ln(coordinate)
   * @param anchorCoordinate anchor coordinate
   * @param coordinate target coordinate
   * @param logarithmic true when the slope is per ln(coordinate)
   * @param minimumFraction lower bound as a fraction of the anchor value, or 0 for none
   * @return extrapolated value
   */
  private static double extrapolate(double anchorValue, double slope, double anchorCoordinate, double coordinate,
      boolean logarithmic, double minimumFraction) {
    double run = logarithmic ? Math.log(coordinate / anchorCoordinate) : coordinate - anchorCoordinate;
    double value = anchorValue + slope * run;
    if (minimumFraction > 0.0) {
      value = Math.max(value, minimumFraction * anchorValue);
    }
    return value;
  }

  /**
   * Map a line and position to a pressure index.
   *
   * @param line line index
   * @param position position along the line
   * @param alongTemperature axis orientation
   * @return pressure index
   */
  private static int row(int line, int position, boolean alongTemperature) {
    return alongTemperature ? line : position;
  }

  /**
   * Map a line and position to a temperature index.
   *
   * @param line line index
   * @param position position along the line
   * @param alongTemperature axis orientation
   * @return temperature index
   */
  private static int col(int line, int position, boolean alongTemperature) {
    return alongTemperature ? position : line;
  }

  /**
   * Read a value by line and position.
   *
   * @param values property values
   * @param line line index
   * @param position position along the line
   * @param alongTemperature axis orientation
   * @return value
   */
  private static double get(double[][] values, int line, int position, boolean alongTemperature) {
    return values[row(line, position, alongTemperature)][col(line, position, alongTemperature)];
  }

  /**
   * Write a value by line and position.
   *
   * @param values property values
   * @param line line index
   * @param position position along the line
   * @param alongTemperature axis orientation
   * @param value value to write
   */
  private static void set(double[][] values, int line, int position, boolean alongTemperature, double value) {
    values[row(line, position, alongTemperature)][col(line, position, alongTemperature)] = value;
  }

  /**
   * Apply the gradient-based monotonic fill to a density, enthalpy or entropy column.
   *
   * <p>
   * Density is filled along pressure with its dRho/dP column, enthalpy and entropy along temperature with the phase
   * heat capacity column. Other columns are returned unchanged for nearest-neighbour filling.
   * </p>
   *
   * @param keyword column keyword
   * @param keywords all column keywords of the table
   * @param props all column values indexed [column][pressure][temperature]
   * @param present phase-existence mask of the column
   * @param pressuresPa grid pressures [Pa]
   * @param temperaturesK grid temperatures [K]
   * @return presence mask extended with the nodes filled here
   */
  static boolean[][] monotonicFill(String keyword, String[] keywords, double[][][] props, boolean[][] present,
      double[] pressuresPa, double[] temperaturesK) {
    String gradientKeyword;
    boolean alongTemperature;
    boolean logarithmic = false;
    double minimumFraction = 0.0;
    if (keyword.startsWith("RO")) {
      gradientKeyword = "DRO" + keyword.substring(2) + "DP";
      alongTemperature = false;
      minimumFraction = 0.1;
    } else if (keyword.startsWith("SE")) {
      gradientKeyword = "CP" + keyword.substring(2);
      alongTemperature = true;
      logarithmic = true;
    } else if ("HG".equals(keyword) || "HHL".equals(keyword) || "HWT".equals(keyword)) {
      gradientKeyword = "CP" + keyword.substring(1);
      alongTemperature = true;
    } else {
      return present;
    }
    int column = -1;
    int gradientColumn = -1;
    for (int k = 0; k < keywords.length; k++) {
      if (keyword.equals(keywords[k])) {
        column = k;
      }
      if (gradientKeyword.equals(keywords[k])) {
        gradientColumn = k;
      }
    }
    if (column < 0) {
      return present;
    }
    double[][] gradient = gradientColumn < 0 ? null : props[gradientColumn];
    return fillAlongAxis(props[column], gradient, present, alongTemperature ? temperaturesK : pressuresPa,
        alongTemperature, logarithmic, minimumFraction);
  }

  /**
   * Find the value at the nearest grid node where the phase exists.
   *
   * @param source unmodified property values
   * @param present true where the phase exists
   * @param pressureIndex node pressure index
   * @param temperatureIndex node temperature index
   * @return nearest usable value
   */
  private static double nearestUsable(double[][] source, boolean[][] present, int pressureIndex, int temperatureIndex) {
    double best = 0.0;
    int bestDistance = Integer.MAX_VALUE;
    for (int i = 0; i < source.length; i++) {
      for (int j = 0; j < source[i].length; j++) {
        if (!present[i][j] || !isUsable(source[i][j])) {
          continue;
        }
        int distance = Math.abs(i - pressureIndex) + Math.abs(j - temperatureIndex);
        if (distance < bestDistance) {
          bestDistance = distance;
          best = source[i][j];
        }
      }
    }
    return best;
  }

  /**
   * Check that a value can be written to an OLGA table.
   *
   * @param value candidate value
   * @return true when the value is finite
   */
  private static boolean isUsable(double value) {
    return !Double.isNaN(value) && !Double.isInfinite(value);
  }
}
