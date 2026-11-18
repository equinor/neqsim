package neqsim.process.equipment.compressor;

import org.apache.commons.math3.analysis.UnivariateFunction;
import org.apache.commons.math3.analysis.interpolation.LinearInterpolator;
import neqsim.process.equipment.pipeline.AdiabaticPipe;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Steady, rated-capacity vent study for one tandem compressor seal end. Solves both cavity pressures and interstage
 * transfer from net incoming loads and installed pressure/flow tables. Each set of tables applies to one gas,
 * temperature, receiving pressure and equipment condition. No extrapolation or implicit protection action is credited.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
public final class DryGasSealVentStudy {
  private final Vent primary;
  private final Vent secondary;
  private final double minTransfer;
  private final double maxTransfer;
  private final UnivariateFunction sealDifferential;

  /**
   * An installed vent assembly at fixed receiving conditions. Each row gives cavity pressure and simultaneous flows to
   * the intended vent and, optionally, through the separation seal to the bearing housing.
   *
   * @author NeqSim contributors
   * @version 1.0
   */
  public static final class Vent {
    private final double minFlow;
    private final double maxFlow;
    private final UnivariateFunction pressure;
    private final UnivariateFunction bearingFlow;

    /** Creates a completely blocked assembly with no pressure/flow relation. */
    private Vent() {
      minFlow = 0.0;
      maxFlow = 0.0;
      pressure = null;
      bearingFlow = null;
    }

    /**
     * Defines an installed vent without a bearing-side outlet.
     *
     * @param cavityBara strictly increasing cavity pressures at the declared receiver condition
     * @param ventKgS corresponding nonnegative outlet mass flows
     */
    public Vent(double[] cavityBara, double[] ventKgS) {
      this(cavityBara, ventKgS, new double[ventKgS.length]);
    }

    /**
     * Defines parallel atmospheric vent and bearing-housing outlets for a separation-seal case.
     *
     * @param cavityBara common upstream absolute pressure for both outlet paths
     * @param ventKgS flow to the intended vent at each pressure in kg/s
     * @param bearingKgS flow to the bearing housing at each pressure in kg/s
     */
    public Vent(double[] cavityBara, double[] ventKgS, double[] bearingKgS) {
      if (cavityBara.length < 2 || cavityBara.length != ventKgS.length || cavityBara.length != bearingKgS.length) {
        throw new IllegalArgumentException("Vent rating requires at least two matching rows");
      }
      double[] total = new double[cavityBara.length];
      for (int i = 0; i < total.length; i++) {
        positive(cavityBara[i], "cavity pressure");
        nonnegative(ventKgS[i], "vent flow");
        nonnegative(bearingKgS[i], "bearing flow");
        total[i] = nonnegative(ventKgS[i] + bearingKgS[i], "total outlet flow");
        if (i > 0 && (cavityBara[i] <= cavityBara[i - 1] || total[i] <= total[i - 1] || ventKgS[i] < ventKgS[i - 1]
            || bearingKgS[i] < bearingKgS[i - 1])) {
          throw new IllegalArgumentException("Vent pressure and total flow must increase and branch flows cannot fall");
        }
      }
      minFlow = total[0];
      maxFlow = total[total.length - 1];
      pressure = new LinearInterpolator().interpolate(total, cavityBara);
      bearingFlow = new LinearInterpolator().interpolate(total, bearingKgS);
    }

    /**
     * Rates a horizontal, constant-bore gas line using the isothermal pressure-squared equation, receiver gas
     * properties and the Darcy friction factor used by AdiabaticPipe. Flashes an owned copy at each rating pressure.
     * The low-Mach approximation excludes choking, phase changes, heat transfer and varying gas composition.
     *
     * @param gas vent gas at the rating temperature, for example a process stream's thermodynamic system
     * @param receiverBara fixed absolute outlet pressure in bara
     * @param equivalentLengthM pipe and fittings equivalent length in metres
     * @param boreM inside diameter in metres
     * @param roughnessM absolute wall roughness in metres
     * @param massRatesKgS strictly increasing rating points starting at zero, in kg/s
     * @return vent characteristic at this receiver pressure and gas composition
     * @throws IllegalArgumentException for unsupported gas states, geometry or outlet Mach above 0.3
     */
    public static Vent fromGasPipe(SystemInterface gas, double receiverBara, double equivalentLengthM, double boreM,
        double roughnessM, double[] massRatesKgS) {
      positive(receiverBara, "receiver pressure");
      positive(equivalentLengthM, "equivalent length");
      positive(boreM, "bore");
      nonnegative(roughnessM, "roughness");
      if (gas == null || gas.isForcePhaseTypes() || gas.isChemicalSystem() || gas.doSolidPhaseCheck()
          || gas.getHydrateCheck() || roughnessM >= boreM || massRatesKgS.length < 2 || massRatesKgS[0] != 0.0) {
        throw new IllegalArgumentException(
            "Supply an equilibrium gas, valid roughness and rating points starting at zero");
      }
      SystemInterface fluid = gas.clone();
      fluid.setMultiPhaseCheck(true);
      flashGas(fluid, receiverBara);
      fluid.initProperties();
      double density = positive(fluid.getDensity("kg/m3"), "gas density");
      double viscosity = positive(fluid.getViscosity("Pas"), "gas viscosity");
      double soundSpeed = positive(fluid.getSoundSpeed(), "gas sound speed");
      double area = Math.PI * boreM * boreM / 4.0;
      double receiverPa = receiverBara * 1e5;
      double[] pressures = new double[massRatesKgS.length];
      pressures[0] = receiverBara;
      AdiabaticPipe pipe = new AdiabaticPipe("Vent rating");
      pipe.setDiameter(boreM);
      pipe.setPipeWallRoughness(roughnessM);
      for (int i = 1; i < pressures.length; i++) {
        double flux = positive(massRatesKgS[i], "rating flow") / area;
        if (massRatesKgS[i] <= massRatesKgS[i - 1] || flux / (density * soundSpeed) > 0.3) {
          throw new IllegalArgumentException(
              "Use increasing rating flows within the low-Mach pipe range (Mach <= 0.3)");
        }
        double darcy = pipe.calcWallFrictionFactor(flux * boreM / viscosity);
        double pressureSquaredLoss = darcy * equivalentLengthM / boreM * flux * flux * receiverPa / density;
        pressures[i] = Math.sqrt(receiverPa * receiverPa + pressureSquaredLoss) / 1e5;
        flashGas(fluid, pressures[i]);
      }
      return new Vent(pressures, massRatesKgS);
    }

    /**
     * Verifies the equilibrium gas phase at a pipe rating point.
     *
     * @param fluid owned fluid at the case temperature
     * @param pressureBara test pressure in bara
     */
    private static void flashGas(SystemInterface fluid, double pressureBara) {
      fluid.setPressure(pressureBara, "bara");
      new ThermodynamicOperations(fluid).TPflash();
      if (fluid.getNumberOfPhases() != 1 || fluid.getPhase(0).getType() != PhaseType.GAS) {
        throw new IllegalArgumentException("Pipe rating requires a single gas phase at every sampled pressure");
      }
    }

    /**
     * Creates an isolated outlet. Pressure must then be determined through the other seal cavity.
     *
     * @return blocked vent
     */
    public static Vent blocked() {
      return new Vent();
    }

    /**
     * Evaluates an in-range outlet characteristic, allowing endpoint arithmetic roundoff.
     *
     * @param curve installed characteristic
     * @param flow already bracketed by the solver in kg/s
     * @return interpolated value
     */
    private double at(UnivariateFunction curve, double flow) {
      return curve.value(Math.min(maxFlow, Math.max(minFlow, flow)));
    }
  }

  /**
   * Constructs one hydraulic configuration. Use signed reverse data when reverse interstage transfer is credible.
   *
   * @param primary primary vent to flare, with a single outlet
   * @param secondary secondary vent, optionally including a bearing-side path
   * @param transferKgS increasing signed interstage flow, positive from primary to secondary
   * @param differentialBar increasing signed primary minus secondary pressure at each flow, including (0,0). Supply
   * only (0,0) for a gas-tight stage.
   */
  public DryGasSealVentStudy(Vent primary, Vent secondary, double[] transferKgS, double[] differentialBar) {
    if (primary == null || secondary == null || transferKgS.length == 0 || transferKgS.length != differentialBar.length
        || primary.bearingFlow != null && primary.bearingFlow.value(primary.maxFlow) != 0.0) {
      throw new IllegalArgumentException(
          "Supply a single-outlet primary vent, secondary assembly and matching seal table");
    }
    boolean includesOrigin = false;
    for (int i = 0; i < transferKgS.length; i++) {
      if (!Double.isFinite(transferKgS[i]) || !Double.isFinite(differentialBar[i])
          || i > 0 && (transferKgS[i] <= transferKgS[i - 1] || differentialBar[i] <= differentialBar[i - 1])) {
        throw new IllegalArgumentException(
            "Seal flow and differential pressure must be finite and strictly increasing");
      }
      includesOrigin |= transferKgS[i] == 0.0 && differentialBar[i] == 0.0;
    }
    if (!includesOrigin) {
      throw new IllegalArgumentException("Seal table must include zero flow at zero differential pressure");
    }
    this.primary = primary;
    this.secondary = secondary;
    minTransfer = transferKgS[0];
    maxTransfer = transferKgS[transferKgS.length - 1];
    sealDifferential = transferKgS.length == 1 ? null
        : new LinearInterpolator().interpolate(transferKgS, differentialBar);
  }

  /**
   * Calculates one operating or failure case. The leakage load is specified at the study conditions. Supply values are
   * the parts entering each cavity after other splits, not total panel flows.
   *
   * @param sourceBara available pressure upstream of the primary face in bara
   * @param processLeakKgS process-origin gas entering the primary cavity in kg/s
   * @param primaryPurgeKgS non-process gas entering the primary cavity in kg/s
   * @param secondaryPurgeKgS non-process gas entering the secondary cavity in kg/s
   * @return steady pressures, branch rates and process-origin gas rates
   * @throws IllegalStateException when the rated network cannot resolve a physical steady state
   */
  public Result evaluate(double sourceBara, double processLeakKgS, double primaryPurgeKgS, double secondaryPurgeKgS) {
    positive(sourceBara, "source pressure");
    nonnegative(processLeakKgS, "process leakage");
    nonnegative(primaryPurgeKgS, "primary purge");
    nonnegative(secondaryPurgeKgS, "secondary purge");
    double primaryLoad = nonnegative(processLeakKgS + primaryPurgeKgS, "primary load");
    nonnegative(primaryLoad + secondaryPurgeKgS, "total load");
    double low = Math.max(minTransfer, Math.max(primaryLoad - primary.maxFlow, secondary.minFlow - secondaryPurgeKgS));
    double high = Math.min(maxTransfer, Math.min(primaryLoad - primary.minFlow, secondary.maxFlow - secondaryPurgeKgS));
    if (low > high || primary.pressure == null && secondary.pressure == null
        || sealDifferential == null && (primary.pressure == null || secondary.pressure == null)) {
      throw new IllegalStateException("No resolved steady state within the supplied seal and vent capacities");
    }
    double transfer = low;
    if (sealDifferential != null && primary.pressure != null && secondary.pressure != null) {
      transfer = solveTransfer(low, high, primaryLoad, secondaryPurgeKgS);
    }
    double flare = primaryLoad - transfer;
    double secondaryOut = secondaryPurgeKgS + transfer;
    double p1;
    double p2;
    if (primary.pressure == null) {
      p2 = secondary.at(secondary.pressure, secondaryOut);
      p1 = p2 + sealDifferential.value(transfer);
    } else {
      p1 = primary.at(primary.pressure, flare);
      p2 = secondary.pressure == null ? p1 - sealDifferential.value(transfer)
          : secondary.at(secondary.pressure, secondaryOut);
    }
    if (!Double.isFinite(p1) || !Double.isFinite(p2) || p1 <= 0.0 || p2 <= 0.0
        || (processLeakKgS > 0.0 && p1 >= sourceBara)) {
      throw new IllegalStateException(
          "Cavity pressure incompatible with absolute pressure or imposed forward primary leakage");
    }
    double bearing = secondary.pressure == null ? 0.0 : secondary.at(secondary.bearingFlow, secondaryOut);
    // For reverse transfer the secondary inflow is pure purge. Forward transfer carries the primary cavity blend.
    double processToSecondary = transfer > 0.0 ? processLeakKgS * (transfer / primaryLoad) : 0.0;
    double processToBearing = secondaryOut > 0.0 ? processToSecondary * (bearing / secondaryOut) : 0.0;
    return new Result(p1, p2, transfer, flare, secondaryOut - bearing, bearing, processLeakKgS - processToSecondary,
        processToSecondary - processToBearing, processToBearing);
  }

  /**
   * Solves the monotone interstage pressure balance within the intersection of all three rated flow ranges.
   *
   * @param low minimum admissible transfer in kg/s
   * @param high maximum admissible transfer in kg/s
   * @param primaryLoad primary inflow in kg/s
   * @param secondaryLoad secondary inflow in kg/s
   * @return pressure-balanced transfer in kg/s
   */
  private double solveTransfer(double low, double high, double primaryLoad, double secondaryLoad) {
    UnivariateFunction residual = q -> primary.at(primary.pressure, primaryLoad - q)
        - secondary.at(secondary.pressure, secondaryLoad + q) - sealDifferential.value(q);
    // The pressure residual is linear between knots and strictly decreases. Bisection also works at knots.
    double toleranceBar = 1.0e-10;
    if (residual.value(low) < -toleranceBar || residual.value(high) > toleranceBar) {
      throw new IllegalStateException(
          "Cavity pressure balance is outside the rated flow ranges (check reverse flow and receiver pressure)");
    }
    for (int i = 0; i < 160; i++) {
      double mid = low + 0.5 * (high - low);
      double residualBar = residual.value(mid);
      if (Math.abs(residualBar) <= toleranceBar) {
        return mid;
      }
      if (residualBar > 0.0) {
        low = mid;
      } else {
        high = mid;
      }
    }
    throw new IllegalStateException("Vent network pressure balance did not converge");
  }

  /**
   * Checks a nonnegative finite input.
   *
   * @param value input value
   * @param name quantity name
   * @return checked value
   */
  private static double nonnegative(double value, String name) {
    if (!Double.isFinite(value) || value < 0.0) {
      throw new IllegalArgumentException(name + " must be finite and nonnegative");
    }
    return value;
  }

  /**
   * Checks a positive finite input.
   *
   * @param value input value
   * @param name quantity name
   * @return checked value
   */
  private static double positive(double value, String name) {
    if (!Double.isFinite(value) || value <= 0.0) {
      throw new IllegalArgumentException(name + " must be finite and positive");
    }
    return value;
  }

  /**
   * Immutable steady result. Process-origin flow tracks the tagged leakage regardless of its methane content.
   *
   * @author NeqSim contributors
   * @version 1.0
   */
  public static final class Result {
    /** Primary vent cavity absolute pressure, bara. */
    public final double primaryBara;
    /** Secondary vent cavity absolute pressure, bara. */
    public final double secondaryBara;
    /** Signed flow through secondary seal, kg/s, positive towards the secondary vent. */
    public final double transferKgS;
    /** Total to primary vent/flare, kg/s. */
    public final double flareKgS;
    /** Total to secondary vent/atmosphere, kg/s. */
    public final double atmosphereKgS;
    /** Total to bearing housing, kg/s. */
    public final double bearingKgS;
    /** Process-origin part of flare flow, kg/s. */
    public final double flareProcessKgS;
    /** Process-origin part of atmospheric vent flow, kg/s. */
    public final double atmosphereProcessKgS;
    /** Process-origin part entering the bearing housing, kg/s. */
    public final double bearingProcessKgS;

    /**
     * Stores the solved balances.
     *
     * @param primaryBara primary cavity pressure, bara
     * @param secondaryBara secondary cavity pressure, bara
     * @param transferKgS signed interstage rate, kg/s
     * @param flareKgS primary vent rate, kg/s
     * @param atmosphereKgS secondary vent rate, kg/s
     * @param bearingKgS bearing-side rate, kg/s
     * @param flareProcessKgS process gas to flare, kg/s
     * @param atmosphereProcessKgS process gas to atmosphere, kg/s
     * @param bearingProcessKgS process gas to bearing housing, kg/s
     */
    private Result(double primaryBara, double secondaryBara, double transferKgS, double flareKgS, double atmosphereKgS,
        double bearingKgS, double flareProcessKgS, double atmosphereProcessKgS, double bearingProcessKgS) {
      this.primaryBara = primaryBara;
      this.secondaryBara = secondaryBara;
      this.transferKgS = transferKgS;
      this.flareKgS = flareKgS;
      this.atmosphereKgS = atmosphereKgS;
      this.bearingKgS = bearingKgS;
      this.flareProcessKgS = flareProcessKgS;
      this.atmosphereProcessKgS = atmosphereProcessKgS;
      this.bearingProcessKgS = bearingProcessKgS;
    }

    /**
     * Smallest remaining pressure margin among the two vent pressure ratings and both secondary-seal loading
     * directions. A negative result identifies an exceeded limit. Bearing leakage and separation-seal loading require
     * separate comparisons to their project limits.
     *
     * @param primaryLimitBara maximum primary cavity pressure in bara
     * @param secondaryLimitBara maximum secondary cavity pressure in bara
     * @param forwardLimitBar maximum positive differential (primary minus secondary) in bar
     * @param reverseLimitBar allowable magnitude of reverse loading in bar
     * @return smallest signed margin in bar
     */
    public double minimumPressureMarginBar(double primaryLimitBara, double secondaryLimitBara, double forwardLimitBar,
        double reverseLimitBar) {
      double dp = primaryBara - secondaryBara;
      return Math.min(
          Math.min(positive(primaryLimitBara, "primary limit") - primaryBara,
              positive(secondaryLimitBara, "secondary limit") - secondaryBara),
          Math.min(nonnegative(forwardLimitBar, "forward limit") - dp,
              nonnegative(reverseLimitBar, "reverse limit") + dp));
    }
  }
}
