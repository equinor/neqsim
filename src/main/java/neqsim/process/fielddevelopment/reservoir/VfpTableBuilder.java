package neqsim.process.fielddevelopment.reservoir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.util.optimizer.EclipseVFPExporter;

/**
 * Builds deliverable VFPPROD and VFPINJ tables for Eclipse and OPM Flow from a pluggable lift model.
 *
 * <p>
 * The builder owns everything around the physics: table axes, evaluation of a {@link VlpProvider} at every coordinate,
 * rate-cap handling, repair of infeasible points, physical sanity enforcement, diagnostics and the deck text with a
 * provenance header. The deck formatting and unit conversion is done by {@link EclipseVFPExporter}.
 * </p>
 *
 * <p>
 * Typical gas-well use:
 * </p>
 *
 * <pre>{@code
 * VfpTableBuilder builder = VfpTableBuilder.gasWell(1).setDatumDepth(2500.0);
 * builder.setRates(VfpTableBuilder.linearAxis(0.2e6, 6.0e6, 8));
 * builder.setOutletPressures(VfpTableBuilder.linearAxis(20.0, 90.0, 5));
 * builder.setWaterRatios(new double[] {1.0e-6, 2.0e-4, 1.0e-3});
 * VfpTableBuilder.Result result = builder.build(new TubingVlpProvider(factory, 2500.0, 0.1));
 * result.writeDeck(java.nio.file.Paths.get("vfp1.inc"));
 * }</pre>
 *
 * <p>
 * Sanity enforcement is applied in this order and every repair is counted and reported in the result warnings: flowing
 * pressure not below the outlet pressure, flowing pressure increasing with outlet pressure (slope at least
 * {@link #setMinThpSlope(double)}), and non-decreasing flowing pressure with rate above the minimum of each rate curve
 * (gas-well curves are J-shaped, so the loading side below the minimum is left alone). A rate limit is not
 * representable by a smooth VFP; {@link #setRateCap(RateCap, double)} replaces the pressure above the cap by a steep
 * penalty slope and records the cap in the deck header.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class VfpTableBuilder {
  private static final Logger logger = LogManager.getLogger(VfpTableBuilder.class);

  /**
   * Maximum deliverable rate as function of the table coordinate, for example a compressor capacity limit at a suction
   * pressure.
   */
  @FunctionalInterface
  public interface RateCap {
    /**
     * Gives the maximum rate for a table coordinate.
     *
     * @param outletPressure outlet pressure (THP axis value) in bara
     * @param waterRatio water ratio of the coordinate
     * @param gasRatio gas ratio of the coordinate
     * @param alq artificial lift quantity of the coordinate
     * @return maximum rate in Sm3/day of the table rate basis; must be positive
     */
    double maxRate(double outletPressure, double waterRatio, double gasRatio, double alq);
  }

  /** Conditioning of the rate dependence of the flowing pressure. */
  public enum RateMonotoneMode {
    /** Leave the rate dependence as computed. */
    OFF,
    /** Non-decreasing above the minimum of each rate curve; the liquid-loading side is kept (default). */
    ABOVE_MINIMUM,
    /** Non-decreasing over the whole rate axis, as OPM Flow needs to avoid rate/THP control flipping. */
    FULL
  }

  /** Handling of coordinates where the provider returns NaN or throws. */
  public enum FailurePolicy {
    /** Fail the build with the coordinate of the first infeasible point. */
    FAIL,
    /** Extrapolate linearly in rate from the two previous valid points of the same curve. */
    EXTRAPOLATE_RATE
  }

  private final boolean injection;
  private int tableNumber = 1;
  private double datumDepth = 0.0;
  private String rateBasis;
  private String waterRatioType = "WGR";
  private String gasRatioType = "OGR";
  private String alqType = "";
  private String unitSystem = "METRIC";
  private String title = "VFP table";
  private final List<String> provenance = new ArrayList<String>();
  private double[] rates;
  private double[] outletPressures;
  private double[] waterRatios = {0.0};
  private double[] gasRatios = {0.0};
  private double[] alqs = {0.0};
  private RateCap rateCap;
  private double capPenaltyBar = 0.0;
  private FailurePolicy failurePolicy = FailurePolicy.FAIL;
  private boolean enforceThpMonotone = true;
  private double minThpSlope = 0.5;
  private RateMonotoneMode rateMonotoneMode = RateMonotoneMode.ABOVE_MINIMUM;
  private double zeroRatioFloor = 0.0;
  private double continuityToleranceBar = 1.0;
  private double repairWarnBar = 0.5;

  /**
   * Creates a builder.
   *
   * @param injection true for VFPINJ
   * @param rateBasis rate basis keyword
   */
  private VfpTableBuilder(boolean injection, String rateBasis) {
    this.injection = injection;
    this.rateBasis = rateBasis;
  }

  /**
   * Creates a builder for a gas-well VFPPROD table: GAS rate, WGR and OGR axes.
   *
   * @param tableNumber VFP table number (positive)
   * @return new builder
   */
  public static VfpTableBuilder gasWell(int tableNumber) {
    VfpTableBuilder b = new VfpTableBuilder(false, "GAS");
    b.tableNumber = tableNumber;
    b.waterRatioType = "WGR";
    b.gasRatioType = "OGR";
    return b;
  }

  /**
   * Creates a builder for an oil-well VFPPROD table: OIL rate, WCT and GOR axes.
   *
   * @param tableNumber VFP table number (positive)
   * @return new builder
   */
  public static VfpTableBuilder oilWell(int tableNumber) {
    VfpTableBuilder b = new VfpTableBuilder(false, "OIL");
    b.tableNumber = tableNumber;
    b.waterRatioType = "WCT";
    b.gasRatioType = "GOR";
    return b;
  }

  /**
   * Creates a builder for a liquid-rate VFPPROD table: LIQ rate, WCT and GLR axes.
   *
   * @param tableNumber VFP table number (positive)
   * @return new builder
   */
  public static VfpTableBuilder liquidWell(int tableNumber) {
    VfpTableBuilder b = new VfpTableBuilder(false, "LIQ");
    b.tableNumber = tableNumber;
    b.waterRatioType = "WCT";
    b.gasRatioType = "GLR";
    return b;
  }

  /**
   * Creates a builder for a VFPINJ table.
   *
   * @param tableNumber VFP table number (positive)
   * @param phase injected phase: GAS, WAT or OIL
   * @return new builder
   */
  public static VfpTableBuilder injector(int tableNumber, String phase) {
    VfpTableBuilder b = new VfpTableBuilder(true, phase.toUpperCase(Locale.ROOT));
    b.tableNumber = tableNumber;
    return b;
  }

  /**
   * Evenly spaced axis.
   *
   * @param min first value
   * @param max last value
   * @param n number of points (at least 2)
   * @return axis values
   */
  public static double[] linearAxis(double min, double max, int n) {
    double[] axis = new double[n];
    for (int i = 0; i < n; i++) {
      axis[i] = n == 1 ? min : min + (max - min) * i / (n - 1);
    }
    return axis;
  }

  /**
   * Geometrically spaced axis, denser at low values; useful for rate axes that must resolve liquid loading.
   *
   * @param min first value (positive)
   * @param max last value
   * @param n number of points (at least 2)
   * @return axis values
   */
  public static double[] geometricAxis(double min, double max, int n) {
    double[] axis = new double[n];
    double ratio = n == 1 ? 1.0 : Math.pow(max / min, 1.0 / (n - 1));
    for (int i = 0; i < n; i++) {
      axis[i] = min * Math.pow(ratio, i);
    }
    return axis;
  }

  /**
   * Rate axis from a low rate to the design maximum extended by an extrapolation factor so that the simulator never
   * extrapolates the table at late-life or high-rate conditions.
   *
   * @param minRate lowest rate
   * @param designMaxRate highest rate expected in the study
   * @param n number of points
   * @param extrapolationFactor factor applied to the design maximum, for example 1.3
   * @return rate axis
   */
  public static double[] rateAxis(double minRate, double designMaxRate, int n, double extrapolationFactor) {
    return linearAxis(minRate, designMaxRate * extrapolationFactor, n);
  }

  /**
   * Outlet (THP) pressure axis extended downwards for late-life low-pressure operation and upwards for shut-in side
   * margin.
   *
   * @param minPressure lowest pressure expected in the study (bara)
   * @param maxPressure highest pressure expected in the study (bara)
   * @param n number of points
   * @param lowerExtension fraction by which the lower end is extended downwards, for example 0.3 (floor 1 bara)
   * @param upperExtension fraction by which the upper end is extended upwards, for example 0.1
   * @return pressure axis
   */
  public static double[] pressureAxis(double minPressure, double maxPressure, int n, double lowerExtension,
      double upperExtension) {
    return linearAxis(Math.max(1.0, minPressure * (1.0 - lowerExtension)), maxPressure * (1.0 + upperExtension), n);
  }

  /**
   * Sets the table number.
   *
   * @param number positive table number
   * @return this builder
   */
  public VfpTableBuilder setTableNumber(int number) {
    this.tableNumber = number;
    return this;
  }

  /**
   * Sets the datum depth of the flowing pressure.
   *
   * @param depthTvdM datum depth in m TVD
   * @return this builder
   */
  public VfpTableBuilder setDatumDepth(double depthTvdM) {
    this.datumDepth = depthTvdM;
    return this;
  }

  /**
   * Sets the table title written as the first comment of the deck.
   *
   * @param title title text
   * @return this builder
   */
  public VfpTableBuilder setTitle(String title) {
    this.title = title;
    return this;
  }

  /**
   * Adds a provenance line (model, correlation, calibration source, date) to the deck header.
   *
   * @param line single line of text
   * @return this builder
   */
  public VfpTableBuilder addProvenance(String line) {
    provenance.add(line);
    return this;
  }

  /**
   * Sets the rate basis.
   *
   * @param basis OIL, LIQ or GAS for production; OIL, WAT or GAS for injection
   * @return this builder
   */
  public VfpTableBuilder setRateBasis(String basis) {
    this.rateBasis = basis.toUpperCase(Locale.ROOT);
    return this;
  }

  /**
   * Sets the water ratio definition of the production table.
   *
   * @param type WCT, WOR or WGR
   * @return this builder
   */
  public VfpTableBuilder setWaterRatioType(String type) {
    this.waterRatioType = type.toUpperCase(Locale.ROOT);
    return this;
  }

  /**
   * Sets the gas ratio definition of the production table.
   *
   * @param type GOR, GLR or OGR
   * @return this builder
   */
  public VfpTableBuilder setGasRatioType(String type) {
    this.gasRatioType = type.toUpperCase(Locale.ROOT);
    return this;
  }

  /**
   * Enables gas lift as the artificial lift quantity.
   *
   * @param type GRAT for gas lift rate in Sm3/day, or empty string for no artificial lift
   * @return this builder
   */
  public VfpTableBuilder setAlqType(String type) {
    this.alqType = type.toUpperCase(Locale.ROOT);
    return this;
  }

  /**
   * Sets the deck unit system.
   *
   * @param system METRIC or FIELD
   * @return this builder
   */
  public VfpTableBuilder setUnitSystem(String system) {
    this.unitSystem = system.toUpperCase(Locale.ROOT);
    return this;
  }

  /**
   * Sets the rate axis.
   *
   * @param axis strictly increasing rates in Sm3/day of the rate basis
   * @return this builder
   */
  public VfpTableBuilder setRates(double[] axis) {
    this.rates = axis.clone();
    return this;
  }

  /**
   * Sets the outlet pressure (THP) axis.
   *
   * @param axis strictly increasing absolute pressures in bara
   * @return this builder
   */
  public VfpTableBuilder setOutletPressures(double[] axis) {
    this.outletPressures = axis.clone();
    return this;
  }

  /**
   * Sets the water ratio axis (production only).
   *
   * @param axis strictly increasing values in the selected water ratio unit
   * @return this builder
   */
  public VfpTableBuilder setWaterRatios(double[] axis) {
    this.waterRatios = axis.clone();
    return this;
  }

  /**
   * Sets the gas ratio axis (production only).
   *
   * @param axis strictly increasing values in the selected gas ratio unit
   * @return this builder
   */
  public VfpTableBuilder setGasRatios(double[] axis) {
    this.gasRatios = axis.clone();
    return this;
  }

  /**
   * Sets the artificial lift axis (production only); requires {@link #setAlqType(String)} GRAT for non-zero values.
   *
   * @param axis strictly increasing gas lift rates in Sm3/day
   * @return this builder
   */
  public VfpTableBuilder setAlq(double[] axis) {
    this.alqs = axis.clone();
    return this;
  }

  /**
   * Limits the deliverable rate and replaces the pressure above the limit by a penalty slope.
   *
   * <p>
   * For a coordinate with cap {@code Qc}, the flowing pressure at rate {@code Q > Qc} becomes
   * {@code BHP(Qc) + penalty * (Q / Qc - 1)}. The simulator then sees a steeply rising lift curve instead of a rate it
   * could produce. The cap is recorded in the deck header and in {@link Result#getCapPointCount()}.
   * </p>
   *
   * @param cap rate cap function
   * @param penaltyBarPerUnitOverCap bar added per 100 percent of rate above the cap (for example 200)
   * @return this builder
   */
  public VfpTableBuilder setRateCap(RateCap cap, double penaltyBarPerUnitOverCap) {
    this.rateCap = cap;
    this.capPenaltyBar = penaltyBarPerUnitOverCap;
    return this;
  }

  /**
   * Limits the deliverable rate to a constant value.
   *
   * @param maxRate rate cap in Sm3/day of the rate basis
   * @param penaltyBarPerUnitOverCap bar added per 100 percent of rate above the cap
   * @return this builder
   * @see #setRateCap(RateCap, double)
   */
  public VfpTableBuilder setRateCap(final double maxRate, double penaltyBarPerUnitOverCap) {
    return setRateCap(new RateCap() {
      @Override
      public double maxRate(double outletPressure, double waterRatio, double gasRatio, double alq) {
        return maxRate;
      }
    }, penaltyBarPerUnitOverCap);
  }

  /**
   * Selects what happens at infeasible coordinates.
   *
   * @param policy FAIL (default) or EXTRAPOLATE_RATE
   * @return this builder
   */
  public VfpTableBuilder setFailurePolicy(FailurePolicy policy) {
    this.failurePolicy = policy;
    return this;
  }

  /**
   * Enables or disables enforcement of flowing pressure increasing with outlet pressure.
   *
   * @param enforce true to enforce (default)
   * @return this builder
   */
  public VfpTableBuilder setEnforceThpMonotone(boolean enforce) {
    this.enforceThpMonotone = enforce;
    return this;
  }

  /**
   * Sets the minimum slope d(BHP)/d(THP) in bar per bar used by the THP monotonicity enforcement.
   *
   * @param slope minimum slope (default 0.5)
   * @return this builder
   */
  public VfpTableBuilder setMinThpSlope(double slope) {
    this.minThpSlope = slope;
    return this;
  }

  /**
   * Enables or disables non-decreasing flowing pressure with rate above the curve minimum.
   *
   * @param enforce true for {@link RateMonotoneMode#ABOVE_MINIMUM} (default), false for {@link RateMonotoneMode#OFF}
   * @return this builder
   */
  public VfpTableBuilder setEnforceRateMonotone(boolean enforce) {
    this.rateMonotoneMode = enforce ? RateMonotoneMode.ABOVE_MINIMUM : RateMonotoneMode.OFF;
    return this;
  }

  /**
   * Selects how the rate dependence of the flowing pressure is conditioned. OPM Flow can stall on a table that is
   * U-shaped in rate because the well flips between rate and THP control; use {@link RateMonotoneMode#FULL} for such
   * decks and keep the raw curve for the minimum stable rate report.
   *
   * @param mode OFF, ABOVE_MINIMUM (default) or FULL
   * @return this builder
   */
  public VfpTableBuilder setRateMonotoneMode(RateMonotoneMode mode) {
    this.rateMonotoneMode = mode;
    return this;
  }

  /**
   * Replaces exact zero water and gas ratios by a small positive value when calling the provider. Correlations with a
   * phase-count switch show a pressure jump between exactly dry gas and gas with a trace of liquid; the deck axis keeps
   * the value 0.
   *
   * @param floor positive floor in the ratio unit, or 0 to disable (default)
   * @return this builder
   */
  public VfpTableBuilder setZeroRatioFloor(double floor) {
    this.zeroRatioFloor = floor;
    return this;
  }

  /**
   * Sets the pressure difference that triggers the dry-gas continuity warning.
   *
   * @param bar difference in bar (default 1)
   * @return this builder
   */
  public VfpTableBuilder setContinuityTolerance(double bar) {
    this.continuityToleranceBar = bar;
    return this;
  }

  /**
   * Builds the table.
   *
   * @param provider lift model
   * @return result with table, diagnostics and deck text
   * @throws IllegalArgumentException if the axes are missing or not strictly increasing
   * @throws IllegalStateException if a coordinate is infeasible and cannot be repaired by the failure policy
   */
  public Result build(VlpProvider provider) {
    checkAxis("rate", rates, false);
    checkAxis("outlet pressure", outletPressures, true);
    double[] water = injection ? new double[] {0.0} : waterRatios;
    double[] gas = injection ? new double[] {0.0} : gasRatios;
    double[] lift = injection ? new double[] {0.0} : alqs;
    checkAxis("water ratio", water, false);
    checkAxis("gas ratio", gas, false);
    checkAxis("alq", lift, false);
    int nf = rates.length;
    int nt = outletPressures.length;
    int nw = water.length;
    int ng = gas.length;
    int na = lift.length;
    double[][][][][] bhp = new double[nf][nt][nw][ng][na];
    List<String> warnings = new ArrayList<String>();
    int[] counters = new int[4];
    final int capped = 0;
    final int extrapolated = 1;
    final int provided = 2;
    final int failed = 3;
    double minCap = Double.POSITIVE_INFINITY;
    double maxCap = 0.0;
    double rateFloor = 1.0e-3 * rates[nf - 1];

    probeContinuity(provider, water, gas, lift, warnings);

    for (int t = 0; t < nt; t++) {
      for (int w = 0; w < nw; w++) {
        for (int g = 0; g < ng; g++) {
          for (int a = 0; a < na; a++) {
            double cap = Double.POSITIVE_INFINITY;
            if (rateCap != null) {
              cap = rateCap.maxRate(outletPressures[t], water[w], gas[g], lift[a]);
              if (!(cap > 0.0)) {
                throw new IllegalArgumentException("Rate cap must be positive at THP " + outletPressures[t]);
              }
              minCap = Math.min(minCap, cap);
              maxCap = Math.max(maxCap, cap);
            }
            double capBhp = Double.NaN;
            for (int f = 0; f < nf; f++) {
              double q = rates[f];
              double value;
              if (q > cap) {
                if (Double.isNaN(capBhp)) {
                  capBhp = evaluate(provider, cap, outletPressures[t], water[w], gas[g], lift[a]);
                }
                value = capBhp + capPenaltyBar * (q / cap - 1.0);
                counters[capped]++;
              } else {
                value = evaluate(provider, Math.max(q, rateFloor), outletPressures[t], water[w], gas[g], lift[a]);
              }
              if (Double.isNaN(value) || Double.isInfinite(value)) {
                counters[failed]++;
              } else {
                counters[provided]++;
              }
              bhp[f][t][w][g][a] = value;
            }
          }
        }
      }
    }

    if (counters[failed] > 0) {
      repairInfeasible(bhp, counters, extrapolated, warnings);
    }

    int[] repairs = enforceSanity(bhp, warnings);
    if (counters[capped] > 0) {
      warnings.add(String.format(Locale.ROOT,
          "Rate cap applied at %d coordinates (cap range %.6g to %.6g Sm3/d, penalty %.4g bar per 100%% over cap)",
          counters[capped], minCap, maxCap, capPenaltyBar));
    }
    return new Result(this, water, gas, lift, bhp, warnings, counters[capped], counters[extrapolated], repairs[0],
        repairs[1], repairs[2], minCap, maxCap);
  }

  /**
   * Evaluates the provider at one coordinate, applying the zero-ratio floor and converting failures to NaN.
   *
   * @param provider lift model
   * @param rate rate in Sm3/day
   * @param thp outlet pressure in bara
   * @param wr water ratio
   * @param gr gas ratio
   * @param alq artificial lift quantity
   * @return flowing pressure in bara or NaN
   */
  private double evaluate(VlpProvider provider, double rate, double thp, double wr, double gr, double alq) {
    double w = wr < zeroRatioFloor ? zeroRatioFloor : wr;
    double g = gr < zeroRatioFloor ? zeroRatioFloor : gr;
    try {
      return provider.bottomHolePressure(rate, thp, w, g, alq);
    } catch (RuntimeException ex) {
      logger.debug("VLP provider failed at rate {} thp {}: {}", rate, thp, ex.toString());
      return Double.NaN;
    }
  }

  /**
   * Compares the provider at an exact zero ratio with a tiny positive ratio and records a warning for a jump.
   *
   * @param provider lift model
   * @param water water ratio axis
   * @param gas gas ratio axis
   * @param lift artificial lift axis
   * @param warnings list that receives the warnings
   */
  private void probeContinuity(VlpProvider provider, double[] water, double[] gas, double[] lift,
      List<String> warnings) {
    if (injection || zeroRatioFloor > 0.0) {
      return;
    }
    double q = rates[rates.length / 2];
    double thp = outletPressures[outletPressures.length / 2];
    double eps = 1.0e-6;
    if (water[0] == 0.0 && water.length > 1) {
      double b0 = evaluate(provider, q, thp, 0.0, gas[0], lift[0]);
      double b1 = evaluate(provider, q, thp, eps, gas[0], lift[0]);
      if (!Double.isNaN(b0) && !Double.isNaN(b1) && Math.abs(b0 - b1) > continuityToleranceBar) {
        warnings.add(String.format(Locale.ROOT,
            "Dry-gas discontinuity: BHP differs by %.2f bar between water ratio 0 and %.0e at rate %.4g, THP %.4g; "
                + "use a small positive first water ratio or setZeroRatioFloor",
            b1 - b0, eps, q, thp));
      }
    }
    if (gas[0] == 0.0 && gas.length > 1) {
      double b0 = evaluate(provider, q, thp, water[0], 0.0, lift[0]);
      double b1 = evaluate(provider, q, thp, water[0], eps, lift[0]);
      if (!Double.isNaN(b0) && !Double.isNaN(b1) && Math.abs(b0 - b1) > continuityToleranceBar) {
        warnings.add(String.format(Locale.ROOT,
            "Zero gas-ratio discontinuity: BHP differs by %.2f bar between gas ratio 0 and %.0e at rate %.4g, "
                + "THP %.4g; use a small positive first gas ratio or setZeroRatioFloor",
            b1 - b0, eps, q, thp));
      }
    }
  }

  /**
   * Applies the failure policy to infeasible coordinates.
   *
   * @param bhp pressure table that may contain NaN
   * @param counters build counters; the extrapolated counter is updated
   * @param extrapolated index of the extrapolated counter
   * @param warnings list that receives the warning
   * @throws IllegalStateException if the policy is FAIL or a curve has fewer than two valid points below
   */
  private void repairInfeasible(double[][][][][] bhp, int[] counters, int extrapolated, List<String> warnings) {
    int nf = bhp.length;
    for (int t = 0; t < bhp[0].length; t++) {
      for (int w = 0; w < bhp[0][0].length; w++) {
        for (int g = 0; g < bhp[0][0][0].length; g++) {
          for (int a = 0; a < bhp[0][0][0][0].length; a++) {
            for (int f = 0; f < nf; f++) {
              double v = bhp[f][t][w][g][a];
              if (!Double.isNaN(v) && !Double.isInfinite(v)) {
                continue;
              }
              if (failurePolicy == FailurePolicy.FAIL || f < 2) {
                throw new IllegalStateException(String.format(Locale.ROOT,
                    "Infeasible VFP coordinate: rate %.6g, outlet pressure %.6g, water ratio %.6g, gas ratio %.6g, "
                        + "alq %.6g (policy %s)",
                    rates[f], outletPressures[t], waterRatios.length > w && !injection ? waterRatios[w] : 0.0,
                    gasRatios.length > g && !injection ? gasRatios[g] : 0.0,
                    alqs.length > a && !injection ? alqs[a] : 0.0, failurePolicy));
              }
              double slope = (bhp[f - 1][t][w][g][a] - bhp[f - 2][t][w][g][a]) / (rates[f - 1] - rates[f - 2]);
              bhp[f][t][w][g][a] = bhp[f - 1][t][w][g][a] + slope * (rates[f] - rates[f - 1]);
              counters[extrapolated]++;
            }
          }
        }
      }
    }
    warnings.add(counters[extrapolated] + " infeasible coordinates were extrapolated in rate from the previous two "
        + "points of the same curve");
  }

  /**
   * Enforces the physical sanity rules on the table in place.
   *
   * @param bhp pressure table [rate][THP][water][gas][ALQ]
   * @param warnings list that receives one warning per rule that changed points
   * @return counts of changed points: {raised to outlet pressure, THP monotonicity, rate monotonicity}
   */
  private int[] enforceSanity(double[][][][][] bhp, List<String> warnings) {
    int nf = bhp.length;
    int nt = bhp[0].length;
    int nw = bhp[0][0].length;
    int ng = bhp[0][0][0].length;
    int na = bhp[0][0][0][0].length;
    int floorCount = 0;
    int thpCount = 0;
    int rateCount = 0;
    double floorMax = 0.0;
    double thpMax = 0.0;
    double rateMax = 0.0;
    for (int f = 0; f < nf; f++) {
      for (int t = 0; t < nt; t++) {
        for (int w = 0; w < nw; w++) {
          for (int g = 0; g < ng; g++) {
            for (int a = 0; a < na; a++) {
              if (bhp[f][t][w][g][a] < outletPressures[t]) {
                floorMax = Math.max(floorMax, outletPressures[t] - bhp[f][t][w][g][a]);
                bhp[f][t][w][g][a] = outletPressures[t];
                floorCount++;
              }
            }
          }
        }
      }
    }
    for (int pass = 0; pass < 6; pass++) {
      boolean changed = false;
      if (enforceThpMonotone) {
        for (int f = 0; f < nf; f++) {
          for (int w = 0; w < nw; w++) {
            for (int g = 0; g < ng; g++) {
              for (int a = 0; a < na; a++) {
                for (int t = 1; t < nt; t++) {
                  double min = bhp[f][t - 1][w][g][a] + minThpSlope * (outletPressures[t] - outletPressures[t - 1]);
                  if (bhp[f][t][w][g][a] < min) {
                    thpMax = Math.max(thpMax, min - bhp[f][t][w][g][a]);
                    bhp[f][t][w][g][a] = min;
                    thpCount++;
                    changed = true;
                  }
                }
              }
            }
          }
        }
      }
      if (rateMonotoneMode != RateMonotoneMode.OFF) {
        for (int t = 0; t < nt; t++) {
          for (int w = 0; w < nw; w++) {
            for (int g = 0; g < ng; g++) {
              for (int a = 0; a < na; a++) {
                int imin = 0;
                if (rateMonotoneMode == RateMonotoneMode.ABOVE_MINIMUM) {
                  for (int f = 1; f < nf; f++) {
                    if (bhp[f][t][w][g][a] < bhp[imin][t][w][g][a]) {
                      imin = f;
                    }
                  }
                }
                for (int f = imin + 1; f < nf; f++) {
                  if (bhp[f][t][w][g][a] < bhp[f - 1][t][w][g][a]) {
                    rateMax = Math.max(rateMax, bhp[f - 1][t][w][g][a] - bhp[f][t][w][g][a]);
                    bhp[f][t][w][g][a] = bhp[f - 1][t][w][g][a];
                    rateCount++;
                    changed = true;
                  }
                }
              }
            }
          }
        }
      }
      if (!changed) {
        break;
      }
    }
    addRepairWarning(warnings, "BHP raised to the outlet pressure", floorCount, floorMax);
    addRepairWarning(warnings, "BHP raised to restore increase with outlet pressure", thpCount, thpMax);
    addRepairWarning(warnings,
        rateMonotoneMode == RateMonotoneMode.FULL ? "BHP raised to restore non-decreasing rate dependence"
            : "BHP raised to restore non-decreasing rate dependence above the minimum",
        rateCount, rateMax);
    return new int[] {floorCount, thpCount, rateCount};
  }

  /**
   * Adds a repair warning when points were changed.
   *
   * @param warnings list that receives the warning
   * @param what description of the rule
   * @param count number of changed points
   * @param maxBar largest change in bar
   */
  private void addRepairWarning(List<String> warnings, String what, int count, double maxBar) {
    if (count > 0) {
      warnings.add(String.format(Locale.ROOT, "%s at %d points (largest change %.3f bar)%s", what, count, maxBar,
          maxBar > repairWarnBar ? " - check the lift model, the change is larger than expected" : ""));
    }
  }

  /**
   * Validates a table axis.
   *
   * @param name axis name for the message
   * @param axis axis values
   * @param positive true if values must be above zero, false if zero is allowed
   * @throws IllegalArgumentException if the axis is missing, not finite or not strictly increasing
   */
  private static void checkAxis(String name, double[] axis, boolean positive) {
    if (axis == null || axis.length == 0) {
      throw new IllegalArgumentException("Axis " + name + " must be set");
    }
    for (int i = 0; i < axis.length; i++) {
      boolean ok = !Double.isNaN(axis[i]) && !Double.isInfinite(axis[i]) && (positive ? axis[i] > 0.0 : axis[i] >= 0.0)
          && (i == 0 || axis[i] > axis[i - 1]);
      if (!ok) {
        throw new IllegalArgumentException("Axis " + name + " must be finite, strictly increasing"
            + (positive ? " and positive" : " and non-negative") + " (index " + i + ")");
      }
    }
  }

  /**
   * Result of a table build: axes, flowing pressures, diagnostics and deck output.
   */
  public static class Result {
    private final boolean injection;
    private final int tableNumber;
    private final double datumDepth;
    private final String title;
    private final String rateBasis;
    private final String waterRatioType;
    private final String gasRatioType;
    private final String alqType;
    private final String unitSystem;
    private final List<String> provenance;
    private final double[] rates;
    private final double[] outletPressures;
    private final double[] waterRatios;
    private final double[] gasRatios;
    private final double[] alqs;
    private final double[][][][][] bhp;
    private final List<String> warnings;
    private final int capPointCount;
    private final int extrapolatedCount;
    private final int floorRepairs;
    private final int thpRepairs;
    private final int rateRepairs;
    private final double minCap;
    private final double maxCap;

    /**
     * Creates a result from a finished build.
     *
     * @param b builder that holds the configuration
     * @param water water ratio axis
     * @param gas gas ratio axis
     * @param lift artificial lift axis
     * @param bhp flowing pressures [rate][THP][water][gas][ALQ]
     * @param warnings diagnostics collected during the build
     * @param capPointCount coordinates above the rate cap
     * @param extrapolatedCount coordinates filled by extrapolation
     * @param floorRepairs points raised to the outlet pressure
     * @param thpRepairs points changed for THP monotonicity
     * @param rateRepairs points changed for rate monotonicity
     * @param minCap smallest rate cap or infinity
     * @param maxCap largest rate cap or zero
     */
    Result(VfpTableBuilder b, double[] water, double[] gas, double[] lift, double[][][][][] bhp, List<String> warnings,
        int capPointCount, int extrapolatedCount, int floorRepairs, int thpRepairs, int rateRepairs, double minCap,
        double maxCap) {
      this.injection = b.injection;
      this.tableNumber = b.tableNumber;
      this.datumDepth = b.datumDepth;
      this.title = b.title;
      this.rateBasis = b.rateBasis;
      this.waterRatioType = b.waterRatioType;
      this.gasRatioType = b.gasRatioType;
      this.alqType = b.alqType;
      this.unitSystem = b.unitSystem;
      this.provenance = new ArrayList<String>(b.provenance);
      this.rates = b.rates.clone();
      this.outletPressures = b.outletPressures.clone();
      this.waterRatios = water.clone();
      this.gasRatios = gas.clone();
      this.alqs = lift.clone();
      this.bhp = bhp;
      this.warnings = warnings;
      this.capPointCount = capPointCount;
      this.extrapolatedCount = extrapolatedCount;
      this.floorRepairs = floorRepairs;
      this.thpRepairs = thpRepairs;
      this.rateRepairs = rateRepairs;
      this.minCap = minCap;
      this.maxCap = maxCap;
    }

    /**
     * Tells whether this is a VFPINJ table.
     *
     * @return true for injection
     */
    public boolean isInjection() {
      return injection;
    }

    /**
     * Gets the rate axis.
     *
     * @return copy of the rate axis in Sm3/day
     */
    public double[] getRates() {
      return rates.clone();
    }

    /**
     * Gets the outlet pressure (THP) axis.
     *
     * @return copy of the axis in bara
     */
    public double[] getOutletPressures() {
      return outletPressures.clone();
    }

    /**
     * Gets the water ratio axis.
     *
     * @return copy of the axis
     */
    public double[] getWaterRatios() {
      return waterRatios.clone();
    }

    /**
     * Gets the gas ratio axis.
     *
     * @return copy of the axis
     */
    public double[] getGasRatios() {
      return gasRatios.clone();
    }

    /**
     * Gets the artificial lift axis.
     *
     * @return copy of the axis
     */
    public double[] getAlq() {
      return alqs.clone();
    }

    /**
     * Gets one flowing pressure.
     *
     * @param f rate index
     * @param t outlet pressure index
     * @param w water ratio index
     * @param g gas ratio index
     * @param a artificial lift index
     * @return flowing pressure at datum in bara
     */
    public double getBhp(int f, int t, int w, int g, int a) {
      return bhp[f][t][w][g][a];
    }

    /**
     * Gets the full table.
     *
     * @return deep copy in order [rate][THP][water ratio][gas ratio][ALQ], bara
     */
    public double[][][][][] getBhpArray() {
      double[][][][][] copy = new double[bhp.length][][][][];
      for (int f = 0; f < bhp.length; f++) {
        copy[f] = new double[bhp[f].length][][][];
        for (int t = 0; t < bhp[f].length; t++) {
          copy[f][t] = new double[bhp[f][t].length][][];
          for (int w = 0; w < bhp[f][t].length; w++) {
            copy[f][t][w] = new double[bhp[f][t][w].length][];
            for (int g = 0; g < bhp[f][t][w].length; g++) {
              copy[f][t][w][g] = bhp[f][t][w][g].clone();
            }
          }
        }
      }
      return copy;
    }

    /**
     * Multilinear interpolation of the table, the way a simulator reads it. Coordinates outside the axes are clamped to
     * the axis ends, so check {@link #isInsideAxes(double, double, double, double, double)} first.
     *
     * @param rate rate in Sm3/day
     * @param thp outlet pressure in bara
     * @param waterRatio water ratio
     * @param gasRatio gas ratio
     * @param alq artificial lift quantity
     * @return interpolated flowing pressure in bara
     */
    public double interpolate(double rate, double thp, double waterRatio, double gasRatio, double alq) {
      double[][] axes = {rates, outletPressures, waterRatios, gasRatios, alqs};
      double[] x = {rate, thp, waterRatio, gasRatio, alq};
      int[] idx = new int[5];
      double[] wt = new double[5];
      for (int d = 0; d < 5; d++) {
        double[] ax = axes[d];
        if (ax.length == 1) {
          idx[d] = 0;
          wt[d] = 0.0;
          continue;
        }
        double xv = Math.max(ax[0], Math.min(ax[ax.length - 1], x[d]));
        int i = Arrays.binarySearch(ax, xv);
        if (i < 0) {
          i = -i - 2;
        }
        i = Math.max(0, Math.min(ax.length - 2, i));
        idx[d] = i;
        wt[d] = (xv - ax[i]) / (ax[i + 1] - ax[i]);
      }
      double sum = 0.0;
      for (int corner = 0; corner < 32; corner++) {
        double weight = 1.0;
        int[] k = new int[5];
        for (int d = 0; d < 5; d++) {
          boolean upper = (corner >> d & 1) == 1;
          if (axes[d].length == 1) {
            if (upper) {
              weight = 0.0;
              break;
            }
            k[d] = 0;
          } else {
            weight *= upper ? wt[d] : 1.0 - wt[d];
            k[d] = idx[d] + (upper ? 1 : 0);
          }
        }
        if (weight != 0.0) {
          sum += weight * bhp[k[0]][k[1]][k[2]][k[3]][k[4]];
        }
      }
      return sum;
    }

    /**
     * Checks that a coordinate lies within the axis ranges, i.e. the simulator would not extrapolate.
     *
     * @param rate rate in Sm3/day
     * @param thp outlet pressure in bara
     * @param waterRatio water ratio
     * @param gasRatio gas ratio
     * @param alq artificial lift quantity
     * @return true if every coordinate is inside its axis range
     */
    public boolean isInsideAxes(double rate, double thp, double waterRatio, double gasRatio, double alq) {
      return inside(rates, rate) && inside(outletPressures, thp) && inside(waterRatios, waterRatio)
          && inside(gasRatios, gasRatio) && inside(alqs, alq);
    }

    /**
     * Checks a value against the end points of an axis.
     *
     * @param axis axis values
     * @param v value to test
     * @return true if the value lies within the axis range
     */
    private static boolean inside(double[] axis, double v) {
      return v >= axis[0] && v <= axis[axis.length - 1];
    }

    /**
     * Gets the diagnostics collected during the build: repairs, rate caps, discontinuity probes.
     *
     * @return unmodifiable list of warning texts; empty when nothing was repaired
     */
    public List<String> getWarnings() {
      return java.util.Collections.unmodifiableList(warnings);
    }

    /**
     * Number of coordinates above the rate cap that carry the penalty slope.
     *
     * @return cap point count
     */
    public int getCapPointCount() {
      return capPointCount;
    }

    /**
     * Number of infeasible coordinates filled by extrapolation in rate.
     *
     * @return extrapolated point count
     */
    public int getExtrapolatedCount() {
      return extrapolatedCount;
    }

    /**
     * Total number of points changed by the sanity enforcement.
     *
     * @return repair count
     */
    public int getRepairCount() {
      return floorRepairs + thpRepairs + rateRepairs;
    }

    /**
     * Creates the exporter configured with this table, for callers who want the exporter API directly.
     *
     * @return configured exporter (bara and Sm3/day input units)
     */
    public EclipseVFPExporter toExporter() {
      EclipseVFPExporter exporter = new EclipseVFPExporter(tableNumber);
      exporter.setDatumDepth(datumDepth);
      exporter.setFlowRateType(rateBasis);
      exporter.setUnitSystem(unitSystem);
      exporter.setTableTitle(title);
      exporter.setFlowRates(rates);
      exporter.setTHPs(outletPressures);
      if (!injection) {
        exporter.setWaterCutType(waterRatioType);
        exporter.setGORType(gasRatioType);
        exporter.setALQType(alqType);
        exporter.setWaterCuts(waterRatios);
        exporter.setGORs(gasRatios);
        exporter.setALQs(alqs);
      }
      exporter.setBHPTable(bhp);
      return exporter;
    }

    /**
     * Formats the table as a deck keyword with a provenance header.
     *
     * @return VFPPROD or VFPINJ text, ready for an INCLUDE file
     * @throws IllegalArgumentException if the exporter rejects the table definition
     */
    public String toDeckString() {
      StringBuilder out = new StringBuilder();
      out.append("-- ").append(clean(title)).append('\n');
      out.append("-- Built by NeqSim VfpTableBuilder; flowing pressure at datum ").append(datumDepth)
          .append(" m TVD, bara and Sm3/day before deck unit conversion\n");
      for (String line : provenance) {
        out.append("-- ").append(clean(line)).append('\n');
      }
      out.append("-- Axes: rate ").append(rates.length).append(" [").append(rates[0]).append(" .. ")
          .append(rates[rates.length - 1]).append("], THP ").append(outletPressures.length).append(" [")
          .append(outletPressures[0]).append(" .. ").append(outletPressures[outletPressures.length - 1]).append("]");
      if (!injection) {
        out.append(", ").append(waterRatioType).append(' ').append(waterRatios.length).append(", ").append(gasRatioType)
            .append(' ').append(gasRatios.length).append(", ALQ ").append(alqs.length);
      }
      out.append('\n');
      if (capPointCount > 0) {
        out.append("-- RATE CAP applied: cap range ").append(minCap).append(" .. ").append(maxCap).append(" Sm3/d, ")
            .append(capPointCount).append(" coordinates carry the penalty slope\n");
      }
      for (String w : warnings) {
        out.append("-- NOTE: ").append(clean(w)).append('\n');
      }
      EclipseVFPExporter exporter = toExporter();
      out.append(injection ? exporter.getVFPINJString() : exporter.getVFPPRODString());
      return out.toString();
    }

    /**
     * Writes the deck keyword to a file.
     *
     * @param path output file
     * @throws IOException if the file cannot be written
     */
    public void writeDeck(Path path) throws IOException {
      Files.write(path, toDeckString().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Makes text safe for a single deck comment line.
     *
     * @param text text that may contain line breaks
     * @return text on one line, empty for null
     */
    private static String clean(String text) {
      return text == null ? "" : text.replace('\n', ' ').replace('\r', ' ');
    }
  }
}
