package neqsim.process.fielddevelopment.integrated;

import java.io.Serializable;
import java.util.Arrays;
import java.util.function.DoubleUnaryOperator;

/**
 * Finds the facility pressure that maximises the rate of a supply-limited, capacity-limited system.
 *
 * <p>
 * A gas system delivers more as the back-pressure (separator or compressor suction pressure) falls, while the
 * compression facility can handle less because the same suction duty needs more power. The best operating pressure is
 * where the two curves cross. This class solves that crossing by bisection and reports which side binds, which is the
 * value-chain question "is production limited by the reservoir and wells, or by the facility?".
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 * @see PressureCapacityCurve
 */
public final class SupplyCapacityBalance {

  /** Which side limits the rate. */
  public enum Binding {
    /** Reservoir and wells limit the rate; the facility has spare capacity. */
    SUPPLY,
    /** The facility limits the rate; the wells could deliver more. */
    FACILITY
  }

  /** Outcome of the balance. */
  public static final class Result implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final double pressure;
    private final double rate;
    private final Binding binding;

    /**
     * Creates a result.
     *
     * @param pressure operating pressure in bara
     * @param rate delivered rate
     * @param binding limiting side
     */
    Result(double pressure, double rate, Binding binding) {
      this.pressure = pressure;
      this.rate = rate;
      this.binding = binding;
    }

    /**
     * Returns the operating pressure.
     *
     * @return pressure in bara
     */
    public double getPressure() {
      return pressure;
    }

    /**
     * Returns the delivered rate.
     *
     * @return rate in the unit of the input functions
     */
    public double getRate() {
      return rate;
    }

    /**
     * Returns the limiting side.
     *
     * @return binding constraint
     */
    public Binding getBinding() {
      return binding;
    }
  }

  /** Utility class; not instantiable. */
  private SupplyCapacityBalance() {
  }

  /**
   * Maximises {@code min(supply(p), capacity(p))} over a pressure window.
   *
   * <p>
   * If supply is below capacity even at the lowest pressure, the wells limit the rate ({@link Binding#SUPPLY}, reported
   * at the minimum pressure). Otherwise the facility limits it: at the maximum pressure if capacity never catches up,
   * or at the interior crossing where both curves meet.
   * </p>
   *
   * @param supplyAtPressure deliverable rate at facility pressure; non-increasing in pressure
   * @param capacityAtPressure facility capacity at the same pressure; non-decreasing in pressure
   * @param minPressureBara lowest allowed pressure in bara
   * @param maxPressureBara highest allowed pressure in bara
   * @return operating pressure, rate and binding side
   */
  public static Result maximiseRate(DoubleUnaryOperator supplyAtPressure, DoubleUnaryOperator capacityAtPressure,
      double minPressureBara, double maxPressureBara) {
    if (minPressureBara >= maxPressureBara) {
      throw new IllegalArgumentException("minimum pressure must be below maximum pressure");
    }
    double supplyLow = supplyAtPressure.applyAsDouble(minPressureBara);
    double capLow = capacityAtPressure.applyAsDouble(minPressureBara);
    if (supplyLow <= capLow) {
      return new Result(minPressureBara, supplyLow, Binding.SUPPLY);
    }
    double supplyHigh = supplyAtPressure.applyAsDouble(maxPressureBara);
    double capHigh = capacityAtPressure.applyAsDouble(maxPressureBara);
    if (supplyHigh >= capHigh) {
      return new Result(maxPressureBara, capHigh, Binding.FACILITY);
    }
    double lo = minPressureBara;
    double hi = maxPressureBara;
    for (int i = 0; i < 80; i++) {
      double mid = 0.5 * (lo + hi);
      if (supplyAtPressure.applyAsDouble(mid) > capacityAtPressure.applyAsDouble(mid)) {
        lo = mid;
      } else {
        hi = mid;
      }
    }
    double p = 0.5 * (lo + hi);
    return new Result(p, Math.min(supplyAtPressure.applyAsDouble(p), capacityAtPressure.applyAsDouble(p)),
        Binding.FACILITY);
  }

  /**
   * Piecewise-linear facility capacity versus suction pressure, for example from repeated runs of a process model.
   */
  public static final class PressureCapacityCurve implements DoubleUnaryOperator, Serializable {
    private static final long serialVersionUID = 1000L;
    private final double[] pressure;
    private final double[] capacity;

    /**
     * Creates the curve.
     *
     * @param pressureBara pressures in bara, strictly ascending
     * @param capacityRate capacity at each pressure
     */
    public PressureCapacityCurve(double[] pressureBara, double[] capacityRate) {
      if (pressureBara.length != capacityRate.length || pressureBara.length < 2) {
        throw new IllegalArgumentException("need at least two (pressure, capacity) points of equal length");
      }
      for (int i = 1; i < pressureBara.length; i++) {
        if (pressureBara[i] <= pressureBara[i - 1]) {
          throw new IllegalArgumentException("pressures must be strictly ascending");
        }
      }
      this.pressure = Arrays.copyOf(pressureBara, pressureBara.length);
      this.capacity = Arrays.copyOf(capacityRate, capacityRate.length);
    }

    /**
     * Returns the capacity at a pressure, linearly interpolated and extrapolated from the end segments (never
     * negative).
     *
     * @param pressureBara pressure in bara
     * @return capacity rate
     */
    public double capacityAt(double pressureBara) {
      int n = pressure.length;
      int hi = Arrays.binarySearch(pressure, pressureBara);
      if (hi >= 0) {
        return Math.max(0.0, capacity[hi]);
      }
      hi = -hi - 1;
      if (hi == 0) {
        hi = 1;
      } else if (hi >= n) {
        hi = n - 1;
      }
      int lo = hi - 1;
      double t = (pressureBara - pressure[lo]) / (pressure[hi] - pressure[lo]);
      return Math.max(0.0, capacity[lo] + t * (capacity[hi] - capacity[lo]));
    }

    /** {@inheritDoc} */
    @Override
    public double applyAsDouble(double pressureBara) {
      return capacityAt(pressureBara);
    }
  }
}
