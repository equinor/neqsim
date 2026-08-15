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
