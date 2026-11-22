package neqsim.process.equipment.pipeline;

import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import neqsim.process.fielddevelopment.reservoir.VfpDeckReader;

/**
 * Immutable METRIC GAS/WGR/GOR-or-OGR VFPPROD table. Lookups use Sm3/day, bara and Sm3/Sm3. No clamping, extrapolation,
 * reference-condition conversion or hydraulic correlation is applied. BHP is interpreted as the pipeline inlet pressure
 * at the provider's datum.
 *
 * @author ESOL
 * @version 1.0
 */
public final class VfpTable implements Serializable {
  private static final long serialVersionUID = 1L;
  private static final String[] AXIS_NAMES = {"rate", "THP", "WGR", "gas ratio", "ALQ"};
  private final double[][] axes;
  private final double[][][][][] bhp;
  private final String gasRatioType;

  /**
   * Validates a parsed table and takes ownership of its unexposed arrays.
   *
   * @param parsed newly parsed deck, in METRIC units
   */
  private VfpTable(VfpDeckReader.ParsedTable parsed) {
    if (parsed.isInjection()) {
      throw new IllegalArgumentException("Unsupported VFPINJ; expected VFPPROD");
    }
    List<String> definitions = parsed.getDefinitions();
    String[] required = {"GAS", "WGR", null, "THP", null, "METRIC", "BHP"};
    for (int i = 0; i < required.length; i++) {
      if (required[i] != null && !required[i].equals(definitions.get(i))) {
        throw new IllegalArgumentException(
            "Unsupported VFP definition " + definitions.get(i) + "; expected " + required[i]);
      }
    }
    gasRatioType = definitions.get(2);
    if (!"GOR".equals(gasRatioType) && !"OGR".equals(gasRatioType)) {
      throw new IllegalArgumentException("Unsupported gas ratio " + gasRatioType + "; expected GOR or OGR");
    }
    if (!"".equals(definitions.get(4)) && !"GRAT".equals(definitions.get(4))) {
      throw new IllegalArgumentException("Unsupported ALQ definition " + definitions.get(4));
    }
    axes = parsed.getAxes();
    if (axes[4].length != 1) {
      throw new IllegalArgumentException("ALQ must have a single value");
    }
    for (int d = 0; d < axes.length; d++) {
      for (int i = 0; i < axes[d].length; i++) {
        if (!Double.isFinite(axes[d][i]) || axes[d][i] < 0 || (d == 1 && axes[d][i] == 0)
            || (i > 0 && axes[d][i] <= axes[d][i - 1])) {
          throw new IllegalArgumentException(
              AXIS_NAMES[d] + " axis must be finite, nonnegative and strictly increasing; THP must be positive");
        }
      }
    }
    if ("".equals(definitions.get(4)) && axes[4][0] != 0) {
      throw new IllegalArgumentException("Empty ALQ definition requires ALQ zero");
    }
    bhp = parsed.getBhp();
    for (double[][][][] rate : bhp) {
      for (double[][][] thp : rate) {
        for (double[][] water : thp) {
          for (double[] gas : water) {
            if (!Double.isFinite(gas[0]) || gas[0] <= 0) {
              throw new IllegalArgumentException("BHP must be finite positive pressure in bara");
            }
          }
        }
      }
    }
  }

  /**
   * Loads the first table from a UTF-8 Eclipse include/deck file.
   *
   * @param file file path
   * @return validated table, with pressures in bara
   * @throws IOException if reading fails
   * @throws IllegalArgumentException if definitions or records are unsupported
   */
  public static VfpTable fromFile(String file) throws IOException {
    return fromString(new String(Files.readAllBytes(Paths.get(file)), StandardCharsets.UTF_8));
  }

  /**
   * Loads the first table from Eclipse text.
   *
   * @param text deck text
   * @return validated table, with pressures in bara
   * @throws IllegalArgumentException if definitions or records are unsupported
   */
  public static VfpTable fromString(String text) {
    if (text == null || text.trim().isEmpty()) {
      throw new IllegalArgumentException("VFPPROD text is required");
    }
    return new VfpTable(VfpDeckReader.parse(text));
  }

  /** @return rate definition, always GAS (standard gas volume) */
  public String getFlowRateType() {
    return "GAS";
  }

  /** @return GOR (gas/oil) or OGR (oil/gas), both in Sm3/Sm3 */
  public String getGasRatioType() {
    return gasRatioType;
  }

  /**
   * Returns a copy of the rate axis without changing reference conditions.
   *
   * @param unit Sm3/day or MSm3/day
   * @return standard gas volume rates in the requested unit
   */
  public double[] getRateAxis(String unit) {
    double factor;
    if ("Sm3/day".equals(unit)) {
      factor = 1;
    } else if ("MSm3/day".equals(unit)) {
      factor = 1e-6;
    } else {
      throw new IllegalArgumentException("Unsupported rate unit " + unit + "; use Sm3/day or MSm3/day");
    }
    double[] result = axes[0].clone();
    for (int i = 0; i < result.length; i++) {
      result[i] *= factor;
    }
    return result;
  }

  /**
   * Returns a copy of the THP axis.
   *
   * @param unit bara, the supported absolute pressure unit
   * @return THP axis in bara
   */
  public double[] getThpAxis(String unit) {
    if (!"bara".equals(unit)) {
      throw new IllegalArgumentException("Unsupported THP unit " + unit + "; use bara");
    }
    return axes[1].clone();
  }

  /** @return copy of the WGR axis in Sm3 water/Sm3 gas */
  public double[] getWaterRatioAxis() {
    return axes[2].clone();
  }

  /** @return copy of the gas-ratio axis in Sm3/Sm3, in the declared GOR or OGR basis */
  public double[] getGasRatioAxis() {
    return axes[3].clone();
  }

  /** @return fixed ALQ value in Sm3/day for GRAT, or zero for no lift */
  public double getAlq() {
    return axes[4][0];
  }

  /**
   * Interpolates BHP linearly along each table axis.
   *
   * @param rate standard gas rate per line in Sm3/day
   * @param thp outlet pressure in bara
   * @param wgr standard water/gas volume ratio in Sm3/Sm3
   * @param gasRatio declared GOR or OGR in Sm3/Sm3 (no implicit reciprocation)
   * @param alq fixed table ALQ, Sm3/day for GRAT or zero
   * @return inlet pressure in bara
   * @throws IllegalArgumentException if any argument is outside its axis or non-finite
   */
  public double inletPressure(double rate, double thp, double wgr, double gasRatio, double alq) {
    int f = bracket(0, rate);
    int t = bracket(1, thp);
    int w = bracket(2, wgr);
    int g = bracket(3, gasRatio);
    bracket(4, alq);
    double fr = fraction(0, f, rate);
    double fw = fraction(2, w, wgr);
    double fg = fraction(3, g, gasRatio);
    double low = atThp(t, f, w, g, fr, fw, fg);
    double ft = fraction(1, t, thp);
    return ft == 0 ? low : low + ft * (atThp(t + 1, f, w, g, fr, fw, fg) - low);
  }

  /**
   * Solves the piecewise-linear BHP curve for THP, checking strict monotonicity at the query. Both increasing and
   * decreasing curves are supported; flat/nonmonotone curves are ambiguous.
   *
   * @param rate standard gas rate per line in Sm3/day
   * @param inletPressure inlet BHP in bara
   * @param wgr standard water/gas ratio in Sm3/Sm3
   * @param gasRatio declared GOR or OGR in Sm3/Sm3
   * @param alq fixed ALQ, Sm3/day for GRAT or zero
   * @return outlet THP in bara
   * @throws IllegalArgumentException if the target is outside the BHP range or the curve is not invertible
   */
  public double outletPressure(double rate, double inletPressure, double wgr, double gasRatio, double alq) {
    int f = bracket(0, rate);
    int w = bracket(2, wgr);
    int g = bracket(3, gasRatio);
    bracket(4, alq);
    double fr = fraction(0, f, rate);
    double fw = fraction(2, w, wgr);
    double fg = fraction(3, g, gasRatio);
    double[] pressures = new double[axes[1].length];
    int direction = 0;
    for (int t = 0; t < pressures.length; t++) {
      pressures[t] = atThp(t, f, w, g, fr, fw, fg);
      if (t > 0) {
        int sign = Double.compare(pressures[t], pressures[t - 1]);
        if (sign == 0 || (direction != 0 && sign != direction)) {
          throw new IllegalArgumentException("THP inversion requires a strictly monotonic BHP curve");
        }
        direction = sign;
      }
    }
    double min = Math.min(pressures[0], pressures[pressures.length - 1]);
    double max = Math.max(pressures[0], pressures[pressures.length - 1]);
    if (!Double.isFinite(inletPressure) || inletPressure < min || inletPressure > max) {
      throw new IllegalArgumentException(
          "BHP target " + inletPressure + " bara outside THP inversion limits [" + min + ", " + max + "] bara");
    }
    for (int t = 1; t < pressures.length; t++) {
      if (inletPressure >= Math.min(pressures[t - 1], pressures[t])
          && inletPressure <= Math.max(pressures[t - 1], pressures[t])) {
        return axes[1][t - 1]
            + (inletPressure - pressures[t - 1]) / (pressures[t] - pressures[t - 1]) * (axes[1][t] - axes[1][t - 1]);
      }
    }
    return axes[1][0];
  }

  /**
   * Calculates a fractional axis position for a previously bracketed value.
   *
   * @param dimension axis number
   * @param lower lower axis index
   * @param value coordinate in the axis units
   * @return dimensionless interpolation fraction, zero for singleton axes
   */
  private double fraction(int dimension, int lower, double value) {
    double[] axis = axes[dimension];
    return axis.length == 1 ? 0 : (value - axis[lower]) / (axis[lower + 1] - axis[lower]);
  }

  /**
   * Evaluates one THP slice using at most eight nonzero corners, without corner-array allocations.
   *
   * @param t THP index
   * @param f lower rate index
   * @param w lower WGR index
   * @param g lower gas-ratio index
   * @param fr rate fraction, dimensionless
   * @param fw WGR fraction, dimensionless
   * @param fg gas-ratio fraction, dimensionless
   * @return BHP in bara
   */
  private double atThp(int t, int f, int w, int g, double fr, double fw, double fg) {
    double result = 0;
    for (int corner = 0; corner < 8; corner++) {
      int df = corner & 1;
      int dw = (corner >> 1) & 1;
      int dg = (corner >> 2) & 1;
      double weight = (df == 0 ? 1 - fr : fr) * (dw == 0 ? 1 - fw : fw) * (dg == 0 ? 1 - fg : fg);
      if (weight != 0) {
        result += weight * bhp[f + df][t][w + dw][g + dg][0];
      }
    }
    return result;
  }

  /**
   * Finds the lower interpolation index, rejecting extrapolation.
   *
   * @param dimension axis index
   * @param value query in the axis's documented units
   * @return lower index, zero for a singleton axis
   */
  private int bracket(int dimension, double value) {
    double[] axis = axes[dimension];
    if (!Double.isFinite(value) || value < axis[0] || value > axis[axis.length - 1]) {
      throw new IllegalArgumentException(AXIS_NAMES[dimension] + " (" + (dimension == 3 ? gasRatioType : "deck units")
          + ") value " + value + " outside limits [" + axis[0] + ", " + axis[axis.length - 1] + "]");
    }
    if (axis.length == 1) {
      return 0;
    }
    int index = Arrays.binarySearch(axis, value);
    return index >= 0 ? Math.min(index, axis.length - 2) : -index - 2;
  }
}
