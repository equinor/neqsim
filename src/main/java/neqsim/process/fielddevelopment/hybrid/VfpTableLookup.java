package neqsim.process.fielddevelopment.hybrid;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import neqsim.process.fielddevelopment.reservoir.VfpDeckReader;
import neqsim.process.fielddevelopment.reservoir.VfpTableBuilder;
import neqsim.process.fielddevelopment.reservoir.VlpProvider;

/**
 * Lift curve lookup in a METRIC {@code VFPPROD} table, read the way Eclipse and OPM Flow read it: multilinear
 * interpolation in rate, outlet pressure, water ratio and gas ratio, with linear extrapolation outside the axes.
 *
 * <p>
 * The table may use any of the rate types OIL, LIQ or GAS, water ratios WCT, WOR or WGR and gas ratios GOR, GLR or OGR.
 * {@link #bottomHolePressure(double, double, double, double, double)} takes coordinates on the table's own basis;
 * {@link #onOilBasis()} gives a provider on the oil rate, water cut and gas-oil ratio basis used by the hybrid
 * production model. The same class serves well tubing tables (flowing bottom-hole pressure against wellhead pressure)
 * and network branch tables (branch inlet pressure against outlet node pressure).
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public final class VfpTableLookup implements VlpProvider {
  private final int tableNumber;
  private final double datumDepth;
  private final String rateType;
  private final String waterRatioType;
  private final String gasRatioType;
  private final double[][] axes;
  private final double[][][][][] bhp;
  private boolean extrapolate = true;
  private long lookups = 0;
  private long outsideAxes = 0;

  /**
   * Creates a lookup from a parsed table.
   *
   * @param parsed parsed VFPPROD table in METRIC units
   * @throws IllegalArgumentException if the table is an injection table, not METRIC or uses an unsupported basis
   */
  private VfpTableLookup(VfpDeckReader.ParsedTable parsed) {
    if (parsed.isInjection()) {
      throw new IllegalArgumentException("VFPINJ tables are not supported; expected VFPPROD");
    }
    List<String> definitions = parsed.getDefinitions();
    if (definitions.size() < 7) {
      throw new IllegalArgumentException("VFPPROD header needs seven definitions");
    }
    rateType = definitions.get(0);
    waterRatioType = definitions.get(1);
    gasRatioType = definitions.get(2);
    if (!"OIL".equals(rateType) && !"LIQ".equals(rateType) && !"GAS".equals(rateType)) {
      throw new IllegalArgumentException("Unsupported rate type " + rateType);
    }
    if (!"WCT".equals(waterRatioType) && !"WOR".equals(waterRatioType) && !"WGR".equals(waterRatioType)) {
      throw new IllegalArgumentException("Unsupported water ratio type " + waterRatioType);
    }
    if (!"GOR".equals(gasRatioType) && !"GLR".equals(gasRatioType) && !"OGR".equals(gasRatioType)) {
      throw new IllegalArgumentException("Unsupported gas ratio type " + gasRatioType);
    }
    if (!"METRIC".equals(definitions.get(5))) {
      throw new IllegalArgumentException("Only METRIC tables are supported, got " + definitions.get(5));
    }
    tableNumber = parsed.getTableNumber();
    datumDepth = parsed.getDatumDepth();
    axes = parsed.getAxes();
    bhp = parsed.getBhp();
    for (int d = 0; d < axes.length; d++) {
      for (int i = 1; i < axes[d].length; i++) {
        if (!(axes[d][i] > axes[d][i - 1])) {
          throw new IllegalArgumentException("VFP axis " + d + " must be strictly increasing");
        }
      }
    }
  }

  /**
   * Parses the first VFPPROD keyword of a deck text.
   *
   * @param deckText text holding a VFPPROD keyword in METRIC units
   * @return the lookup
   * @throws IllegalArgumentException if the keyword is missing or unsupported
   */
  public static VfpTableLookup fromDeck(String deckText) {
    return new VfpTableLookup(VfpDeckReader.parse(deckText));
  }

  /**
   * Reads the first VFPPROD keyword of a file.
   *
   * @param file include file with a VFPPROD keyword in METRIC units
   * @return the lookup
   * @throws IOException if the file cannot be read
   */
  public static VfpTableLookup fromFile(Path file) throws IOException {
    return fromDeck(new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
  }

  /**
   * Creates a lookup from a table built by {@link VfpTableBuilder}.
   *
   * @param result built table
   * @return the lookup
   */
  public static VfpTableLookup fromResult(VfpTableBuilder.Result result) {
    return fromDeck(result.toDeckString());
  }

  /**
   * Selects linear extrapolation outside the axes (the simulator behaviour, default) or clamping to the axis ends.
   *
   * @param extrapolate true to extrapolate linearly, false to clamp
   * @return this lookup
   */
  public VfpTableLookup setExtrapolate(boolean extrapolate) {
    this.extrapolate = extrapolate;
    return this;
  }

  /**
   * Gets the table number.
   *
   * @return table number of the VFPPROD keyword
   */
  public int getTableNumber() {
    return tableNumber;
  }

  /**
   * Gets the datum depth of the table.
   *
   * @return datum depth in m
   */
  public double getDatumDepth() {
    return datumDepth;
  }

  /**
   * Gets the rate type of the table.
   *
   * @return OIL, LIQ or GAS
   */
  public String getRateType() {
    return rateType;
  }

  /**
   * Gets the water ratio type of the table.
   *
   * @return WCT, WOR or WGR
   */
  public String getWaterRatioType() {
    return waterRatioType;
  }

  /**
   * Gets the gas ratio type of the table.
   *
   * @return GOR, GLR or OGR
   */
  public String getGasRatioType() {
    return gasRatioType;
  }

  /**
   * Gets a copy of one axis.
   *
   * @param dimension 0 rate, 1 outlet pressure, 2 water ratio, 3 gas ratio, 4 artificial lift quantity
   * @return axis values in METRIC deck units
   */
  public double[] getAxis(int dimension) {
    return axes[dimension].clone();
  }

  /**
   * Number of lookups made so far.
   *
   * @return lookup count
   */
  public long getLookups() {
    return lookups;
  }

  /**
   * Number of lookups with at least one coordinate outside the table axes.
   *
   * @return count of extrapolated or clamped lookups
   */
  public long getOutsideAxesCount() {
    return outsideAxes;
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * Coordinates are on the basis of the table itself.
   * </p>
   */
  @Override
  public double bottomHolePressure(double rate, double outletPressure, double waterRatio, double gasRatio, double alq) {
    double[] x = {rate, outletPressure, waterRatio, gasRatio, alq};
    int[] index = new int[5];
    double[] weight = new double[5];
    boolean outside = false;
    for (int d = 0; d < 5; d++) {
      double[] axis = axes[d];
      if (axis.length == 1) {
        index[d] = 0;
        weight[d] = 0.0;
        continue;
      }
      double value = x[d];
      if (value < axis[0] || value > axis[axis.length - 1]) {
        outside = true;
        if (!extrapolate) {
          value = Math.max(axis[0], Math.min(axis[axis.length - 1], value));
        }
      }
      int i = 0;
      while (i < axis.length - 2 && value >= axis[i + 1]) {
        i++;
      }
      index[d] = i;
      weight[d] = (value - axis[i]) / (axis[i + 1] - axis[i]);
    }
    lookups++;
    if (outside) {
      outsideAxes++;
    }
    double sum = 0.0;
    for (int corner = 0; corner < 32; corner++) {
      double w = 1.0;
      int[] k = new int[5];
      boolean skip = false;
      for (int d = 0; d < 5; d++) {
        boolean upper = (corner >> d & 1) == 1;
        if (axes[d].length == 1) {
          if (upper) {
            skip = true;
            break;
          }
          k[d] = 0;
        } else {
          w *= upper ? weight[d] : 1.0 - weight[d];
          k[d] = index[d] + (upper ? 1 : 0);
        }
      }
      if (!skip && w != 0.0) {
        sum += w * bhp[k[0]][k[1]][k[2]][k[3]][k[4]];
      }
    }
    return sum;
  }

  /**
   * Looks up the inlet pressure for surface phase rates, converting them to the basis of the table.
   *
   * @param oilRate stock-tank oil rate in Sm3/day
   * @param waterRate water rate in Sm3/day
   * @param gasRate gas rate in Sm3/day
   * @param outletPressure outlet pressure in bara
   * @param alq artificial lift quantity in the unit of the table
   * @return inlet (bottom-hole or branch inlet) pressure in bara
   */
  public double pressureForPhaseRates(double oilRate, double waterRate, double gasRate, double outletPressure,
      double alq) {
    double liquid = oilRate + waterRate;
    double flow;
    if ("OIL".equals(rateType)) {
      flow = oilRate;
    } else if ("LIQ".equals(rateType)) {
      flow = liquid;
    } else {
      flow = gasRate;
    }
    double waterRatio;
    if ("WCT".equals(waterRatioType)) {
      waterRatio = ratio(waterRate, liquid);
    } else if ("WOR".equals(waterRatioType)) {
      waterRatio = ratio(waterRate, oilRate);
    } else {
      waterRatio = ratio(waterRate, gasRate);
    }
    double gasRatio;
    if ("GOR".equals(gasRatioType)) {
      gasRatio = ratio(gasRate, oilRate);
    } else if ("GLR".equals(gasRatioType)) {
      gasRatio = ratio(gasRate, liquid);
    } else {
      gasRatio = ratio(oilRate, gasRate);
    }
    return bottomHolePressure(flow, outletPressure, waterRatio, gasRatio, alq);
  }

  /**
   * Divides two rates, returning zero for a zero denominator.
   *
   * @param numerator numerator rate
   * @param denominator denominator rate
   * @return ratio, or 0 when the denominator is not positive
   */
  private static double ratio(double numerator, double denominator) {
    return denominator > 0.0 ? numerator / denominator : 0.0;
  }

  /**
   * Gives a provider on the basis used by the hybrid production model: oil rate in Sm3/day, water cut as fraction of
   * the liquid and gas-oil ratio in Sm3/Sm3, whatever the basis of the table.
   *
   * @return provider on the oil rate, water cut and gas-oil ratio basis
   */
  public VlpProvider onOilBasis() {
    return new VlpProvider() {
      @Override
      public double bottomHolePressure(double rate, double outletPressure, double waterRatio, double gasRatio,
          double alq) {
        double water = waterRatio < 1.0 ? rate * waterRatio / (1.0 - waterRatio) : 0.0;
        return pressureForPhaseRates(rate, water, rate * gasRatio, outletPressure, alq);
      }
    };
  }
}
