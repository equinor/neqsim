package neqsim.process.safety.depressurization;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.DoubleUnaryOperator;
import com.google.gson.GsonBuilder;
import neqsim.process.util.fire.ReliefValveSizing;
import neqsim.thermo.ThermodynamicConstantsInterface;

/**
 * Simultaneous fixed-opening gas discharge through individual inlet lines, devices and tailpipes into one header and
 * one rated flare tip. Pipes use the horizontal isothermal Darcy pressure-squared relation at constant gas properties
 * and specified Darcy factor. The tip uses a supplied quadratic pressure characteristic up to its rated flow. The
 * solution compares available capacity with demand; it does not prescribe the demand as the discharge.
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class SteadyFlareGasNetwork {
  private final Map<String, Branch> branches = new LinkedHashMap<>();
  private final String headerTag;
  private final String tipTag;
  private final GasLine header;
  private final double staticBara;
  private final double ratedTipBara;
  private final double ratedFlow;
  private final double temperatureK;
  private final double molarMass;
  private final double z;
  private final double gamma;
  private final double pOverDensity;

  /**
   * Build a common-gas network with an explicit zero-flow and rated tip boundary.
   *
   * @param headerTag common header tag
   * @param header horizontal common pipe from junction to tip
   * @param tipTag tip tag
   * @param staticBara network zero-flow absolute backpressure, bara
   * @param ratedTipBara absolute tip inlet pressure at rated flow, bara
   * @param ratedFlowKgS maximum characterized tip flow, kg/s
   * @param temperatureK isothermal gas temperature, K
   * @param molarMassKgMol gas molar mass, kg/mol
   * @param z constant gas compressibility
   * @param gamma gas heat capacity ratio, greater than one
   */
  public SteadyFlareGasNetwork(String headerTag, Pipe header, String tipTag, double staticBara, double ratedTipBara,
      double ratedFlowKgS, double temperatureK, double molarMassKgMol, double z, double gamma) {
    this.headerTag = tag(headerTag);
    this.tipTag = tag(tipTag);
    this.staticBara = positive(staticBara, "static pressure");
    this.ratedTipBara = positive(ratedTipBara, "rated tip pressure");
    this.ratedFlow = positive(ratedFlowKgS, "rated tip flow");
    this.temperatureK = positive(temperatureK, "gas temperature");
    this.molarMass = positive(molarMassKgMol, "gas molar mass");
    this.z = positive(z, "gas compressibility");
    this.gamma = positive(gamma, "gas heat capacity ratio");
    if (ratedTipBara < staticBara || gamma <= 1.0 || this.tipTag.equals(this.headerTag)) {
      throw new IllegalArgumentException(
          "Require distinct header and tip tags, nondecreasing tip pressure and gamma > 1");
    }
    pOverDensity = positive(z * ThermodynamicConstantsInterface.R * temperatureK / molarMassKgMol,
        "pressure to density ratio");
    this.header = new GasLine(Objects.requireNonNull(header, "header pipe"));
  }

  /**
   * Connect a PSV at fixed lift or a fixed-opening BDV to the common header.
   *
   * @param deviceTag unique device tag identifying the branch
   * @param sourceBara absolute stagnation pressure in the source, bara
   * @param inlet pipe from source to device inlet
   * @param areaM2 open nozzle flow area, m2 (zero for a closed device)
   * @param cd discharge coefficient
   * @param tailpipe pipe from discharge flange to the header junction
   * @param maxOutletBara optional device-specific absolute outlet pressure limit, bara, or null if unavailable
   * @return this network
   */
  public SteadyFlareGasNetwork addDevice(String deviceTag, double sourceBara, Pipe inlet, double areaM2, double cd,
      Pipe tailpipe, Double maxOutletBara) {
    String key = tag(deviceTag);
    if (branches.containsKey(key) || key.equals(headerTag) || key.equals(tipTag)) {
      throw new IllegalArgumentException("Duplicate network tag: " + key);
    }
    if (maxOutletBara != null) {
      positive(maxOutletBara, "outlet pressure limit");
    }
    if (positive(cd, "discharge coefficient") > 1.0) {
      throw new IllegalArgumentException("Discharge coefficient exceeds one");
    }
    if (!Double.isFinite(areaM2) || areaM2 < 0.0) {
      throw new IllegalArgumentException("Nozzle opening area must be finite and nonnegative");
    }
    branches.put(key, new Branch(positive(sourceBara, "source pressure"), Objects.requireNonNull(inlet, "inlet pipe"),
        areaM2, cd, Objects.requireNonNull(tailpipe, "tailpipe"), maxOutletBara));
    return this;
  }

  /**
   * Compare the specified simultaneous demands with steady branch capacities.
   *
   * @param demandsKgS required gas mass flow for every device at this operating point
   * @return converged low-Mach pipe rating, or an exception identifying the rejected basis
   */
  public Result rate(Map<String, Double> demandsKgS) {
    return rate(demandsKgS, 1e-8, 100);
  }

  /**
   * Solve with explicit absolute mass residual tolerance and iteration bound.
   *
   * @param demandsKgS required gas rates by device tag (kg/s)
   * @param toleranceKgS absolute mass-flow tolerance for the header and each nozzle (kg/s)
   * @param iterations maximum bisection iterations for each balance
   * @return rating including local flange pressures and residuals
   */
  public Result rate(Map<String, Double> demandsKgS, double toleranceKgS, int iterations) {
    positive(toleranceKgS, "mass-flow tolerance");
    if (iterations < 1 || branches.isEmpty() || demandsKgS == null || !branches.keySet().equals(demandsKgS.keySet())) {
      throw new IllegalArgumentException("Supply iterations, at least one branch and a demand for every device tag");
    }
    for (Double demand : demandsKgS.values()) {
      if (demand == null || !Double.isFinite(demand) || demand < 0.0) {
        throw new IllegalArgumentException("Every demand must be finite and nonnegative");
      }
    }
    double total = balancedFlow(null, toleranceKgS, iterations);
    double tip = tipPressure(total);
    double junction = junctionPressure(total);
    double headerMach = header.checkedMach(total, tip);
    Map<String, DeviceRating> ratings = new LinkedHashMap<>();
    double sum = 0.0;
    for (Map.Entry<String, Branch> entry : branches.entrySet()) {
      Branch branch = entry.getValue();
      double flow = branch.flow(junction, toleranceKgS / (2.0 * branches.size()), iterations);
      double upstream = branch.inlet.downstream(branch.source, flow);
      double outlet = branch.tail.upstream(junction, flow);
      double inletMach = branch.inlet.checkedMach(flow, upstream);
      double tailMach = branch.tail.checkedMach(flow, junction);
      double preOpening = flow == 0.0 ? junction : preOpeningPressure(branch, toleranceKgS, iterations);
      ratings.put(entry.getKey(), new DeviceRating(branch, demandsKgS.get(entry.getKey()), flow, upstream, outlet,
          junction, staticBara, preOpening, inletMach, tailMach));
      sum += flow;
    }
    return new Result(this, junction, tip, total, total - sum, headerMach, ratings);
  }

  /**
   * Calculate and qualify the all-other-devices-operating baseline for a device before opening.
   *
   * @param closed device held closed
   * @param tolerance absolute mass residual, kg/s
   * @param iterations iteration budget for each balance
   * @return qualified local pre-opening pressure, bara
   */
  private double preOpeningPressure(Branch closed, double tolerance, int iterations) {
    double total = balancedFlow(closed, tolerance, iterations);
    double junction = junctionPressure(total);
    header.checkedMach(total, tipPressure(total));
    for (Branch other : branches.values()) {
      if (other != closed) {
        double flow = other.flow(junction, tolerance / (2.0 * branches.size()), iterations);
        other.inlet.checkedMach(flow, other.inlet.downstream(other.source, flow));
        other.tail.checkedMach(flow, junction);
      }
    }
    return junction;
  }

  /**
   * Balance the header with an optional device held closed for a steady pre-opening comparison.
   *
   * @param closed branch held closed, or null for the specified openings
   * @param tolerance absolute mass residual in kg/s
   * @param iterations iteration budget for each balance
   * @return balanced total gas flow in kg/s
   */
  private double balancedFlow(Branch closed, double tolerance, int iterations) {
    DoubleUnaryOperator balance = total -> {
      double junction = junctionPressure(total);
      double sum = 0.0;
      for (Branch branch : branches.values()) {
        if (branch != closed) {
          sum += branch.flow(junction, tolerance / (2.0 * branches.size()), iterations);
        }
      }
      return total - sum;
    };
    return root(balance, ratedFlow, tolerance, iterations, "tip rating or header balance");
  }

  /**
   * @return tip pressure in bara at the trial flow
   * @param total trial gas flow, kg/s
   */
  private double tipPressure(double total) {
    double fraction = total / ratedFlow;
    return staticBara + (ratedTipBara - staticBara) * fraction * fraction;
  }

  /**
   * @return junction pressure in bara at the trial flow
   * @param total trial gas flow, kg/s
   */
  private double junctionPressure(double total) {
    return header.upstream(tipPressure(total), total);
  }

  /**
   * Solve a nondecreasing flow residual on a closed positive interval.
   *
   * @param residual signed trial minus available flow (kg/s)
   * @param high upper flow bound (kg/s)
   * @param tolerance absolute residual tolerance (kg/s)
   * @param iterations iteration budget
   * @param context label for a rejected calculation
   * @return balanced flow in kg/s
   */
  private static double root(DoubleUnaryOperator residual, double high, double tolerance, int iterations,
      String context) {
    double low = 0.0;
    if (Math.abs(residual.applyAsDouble(low)) <= tolerance) {
      return low;
    }
    double upperResidual = residual.applyAsDouble(high);
    if (Math.abs(upperResidual) <= tolerance) {
      return high;
    }
    if (!(upperResidual > 0.0)) {
      throw new IllegalStateException("No solution within " + context);
    }
    for (int i = 0; i < iterations; i++) {
      double mid = low + (high - low) / 2.0;
      double value = residual.applyAsDouble(mid);
      if (Math.abs(value) <= tolerance) {
        return mid;
      }
      if (value < 0.0) {
        low = mid;
      } else {
        high = mid;
      }
    }
    throw new IllegalStateException("Did not converge: " + context);
  }

  /**
   * @param value numeric input
   * @param label input description
   * @return positive finite input
   */
  private static double positive(double value, String label) {
    if (!Double.isFinite(value) || value <= 0.0) {
      throw new IllegalArgumentException("Expected finite positive " + label);
    }
    return value;
  }

  /**
   * @param value network element identifier
   * @return trimmed tag
   */
  private static String tag(String value) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException("Missing network tag");
    }
    return value.trim();
  }

  /**
   * Constant-bore horizontal pipe specified by its Darcy friction factor and equivalent length (including fittings).
   */
  public static final class Pipe {
    public final double boreM;
    public final double equivalentLengthM;
    public final double darcyFrictionFactor;
    private final transient double area;
    private final transient double resistance;

    /**
     * @param boreM internal diameter, m
     * @param equivalentLengthM equivalent length, m; zero represents negligible loss
     * @param darcy Darcy friction factor, dimensionless
     */
    public Pipe(double boreM, double equivalentLengthM, double darcy) {
      this.boreM = positive(boreM, "pipe bore");
      this.darcyFrictionFactor = positive(darcy, "Darcy friction factor");
      if (!Double.isFinite(equivalentLengthM) || equivalentLengthM < 0.0) {
        throw new IllegalArgumentException("Equivalent length must be finite and nonnegative");
      }
      this.equivalentLengthM = equivalentLengthM;
      area = positive(Math.PI * boreM * boreM / 4.0, "pipe area");
      resistance = darcy * equivalentLengthM / boreM / (area * area);
    }
  }

  /** A pipe evaluated at the common gas properties. */
  private final class GasLine {
    private final Pipe pipe;
    private final double loss;

    /** @param pipe installed geometry */
    GasLine(Pipe pipe) {
      this.pipe = pipe;
      loss = pipe.resistance * pOverDensity / 1e10;
    }

    /**
     * @param pressure outlet bara
     * @param flow mass flow, kg/s
     * @return inlet bara
     */
    double upstream(double pressure, double flow) {
      return Math.hypot(pressure, Math.sqrt(loss) * flow);
    }

    /**
     * @param pressure source bara
     * @param flow mass flow, kg/s
     * @return inlet flange bara, or zero when the trial exceeds the available pressure
     */
    double downstream(double pressure, double flow) {
      return Math.sqrt(Math.max(0.0, pressure * pressure - loss * flow * flow));
    }

    /**
     * @param flow mass flow, kg/s
     * @param outletBara pipe outlet pressure
     * @return outlet Mach on the accepted low-Mach basis
     */
    double checkedMach(double flow, double outletBara) {
      double mach = flow * Math.sqrt(pOverDensity / gamma) / (pipe.area * outletBara * 1e5);
      if (!Double.isFinite(mach) || mach > 0.3) {
        throw new IllegalStateException("Pipe outlet outside the low-Mach model: " + mach);
      }
      return mach;
    }
  }

  /** One fully specified, fixed-opening forward discharge path. */
  private final class Branch {
    private final double source;
    private final GasLine inlet;
    private final double area;
    private final double cd;
    private final GasLine tail;
    private final Double limit;

    /**
     * @param source source bara
     * @param inlet inlet pipe
     * @param area open area, m2
     * @param cd discharge coefficient
     * @param tail tailpipe
     * @param limit maximum permitted outlet bara, if supplied
     */
    Branch(double source, Pipe inlet, double area, double cd, Pipe tail, Double limit) {
      this.source = source;
      this.inlet = new GasLine(inlet);
      this.area = area;
      this.cd = cd;
      this.tail = new GasLine(tail);
      this.limit = limit;
    }

    /**
     * @param upstream upstream bara
     * @param downstream downstream bara
     * @return forward nozzle capacity, kg/s
     */
    double capacity(double upstream, double downstream) {
      return ReliefValveSizing.calculateMassFlowCapacity(area, upstream * 1e5, 0.0, downstream * 1e5, temperatureK,
          molarMass, z, gamma, cd);
    }

    /**
     * @param junction header junction bara
     * @param tolerance absolute mass residual, kg/s
     * @param iterations iteration budget
     * @return balanced branch capacity, kg/s
     */
    double flow(double junction, double tolerance, int iterations) {
      if (area == 0.0 || source <= junction) {
        return 0.0;
      }
      double freeCapacity = capacity(source, junction);
      return root(q -> q - capacity(inlet.downstream(source, q), tail.upstream(junction, q)), freeCapacity, tolerance,
          iterations, "device capacity");
    }
  }

  /** Local steady gas rating for a single device, with pressures in bara and differences in bar. */
  public static final class DeviceRating {
    public final double sourceBara;
    public final double openAreaM2;
    public final double dischargeCoefficient;
    public final Pipe inletPipe;
    public final Pipe tailpipe;
    public final double demandKgS;
    public final double availableKgS;
    public final double capacityMarginKgS;
    public final double inletBara;
    public final double outletBara;
    public final double inletLossBar;
    public final double tailpipeLossBar;
    public final double outletRiseAboveStaticBar;
    /** Local pressure with this device closed and all others at their specified openings. */
    public final double steadyPreOpeningBara;
    /** Increase above the steady pre-opening baseline for this device. */
    public final double incrementalBackpressureBar;
    public final Double maxOutletBara;
    public final Double outletPressureMarginBar;
    public final double inletPipeMach;
    public final double tailpipeMach;

    private DeviceRating(Branch branch, double demand, double flow, double inlet, double outlet, double junction,
        double staticPressure, double preOpening, double inletMach, double tailMach) {
      sourceBara = branch.source;
      openAreaM2 = branch.area;
      dischargeCoefficient = branch.cd;
      inletPipe = branch.inlet.pipe;
      tailpipe = branch.tail.pipe;
      maxOutletBara = branch.limit;
      demandKgS = demand;
      availableKgS = flow;
      capacityMarginKgS = flow - demand;
      inletBara = inlet;
      outletBara = outlet;
      inletLossBar = sourceBara - inlet;
      tailpipeLossBar = outlet - junction;
      outletRiseAboveStaticBar = outlet - staticPressure;
      steadyPreOpeningBara = preOpening;
      incrementalBackpressureBar = outlet - preOpening;
      outletPressureMarginBar = maxOutletBara == null ? null : maxOutletBara - outlet;
      inletPipeMach = inletMach;
      tailpipeMach = tailMach;
    }
  }

  /** Converged network conditions and independently addressable device ratings. */
  public static final class Result {
    public final String headerTag;
    public final String tipTag;
    public final Pipe headerPipe;
    public final double ratedTipBara;
    public final double ratedFlowKgS;
    public final double temperatureK;
    public final double molarMassKgMol;
    public final double compressibility;
    public final double heatCapacityRatio;
    public final double zeroFlowStaticBara;
    public final double headerBara;
    public final double tipBara;
    public final double totalKgS;
    public final double massResidualKgS;
    public final double headerMach;
    public final Map<String, DeviceRating> devices;

    private Result(SteadyFlareGasNetwork network, double junction, double tipPressure, double total, double residual,
        double mach, Map<String, DeviceRating> devices) {
      headerTag = network.headerTag;
      tipTag = network.tipTag;
      headerPipe = network.header.pipe;
      ratedTipBara = network.ratedTipBara;
      ratedFlowKgS = network.ratedFlow;
      temperatureK = network.temperatureK;
      molarMassKgMol = network.molarMass;
      compressibility = network.z;
      heatCapacityRatio = network.gamma;
      zeroFlowStaticBara = network.staticBara;
      headerBara = junction;
      tipBara = tipPressure;
      totalKgS = total;
      massResidualKgS = residual;
      headerMach = mach;
      this.devices = Collections.unmodifiableMap(devices);
    }

    /** @return tagged gas rating as JSON, with explicit null for a missing device limit */
    public String toJson() {
      return new GsonBuilder().serializeNulls().create().toJson(this);
    }
  }
}
