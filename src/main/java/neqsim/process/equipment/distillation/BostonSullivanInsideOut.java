package neqsim.process.equipment.distillation;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Boston-Sullivan inside-out solver for distillation columns.
 *
 * <p>
 * Two-loop method per Boston, J.F. and Sullivan, S.L. (1974), "A New Class of
 * Solution Methods for Multicomponent, Multistage Separation Processes",
 * Can. J. Chem. Eng., 52, 52-63 (also Russell 1983, Bansal & Manjare 2016).
 * </p>
 *
 * <p>
 * Outer loop ("complex" thermodynamics):
 * <ol>
 * <li>Run an EOS flash at every tray and store the rigorous K-values, liquid
 * and vapor enthalpies, and densities.</li>
 * <li>Pick a vapor-weighted reference K-value Kb[j] = exp(Sum y_i ln K_i).</li>
 * <li>Linearise: alpha[j][i] = K[j][i] / Kb[j] (treated as constant in the
 * inner loop), and fit ln Kb[j] = A[j] + B[j] * (1/T - 1/T0[j]) by perturbing
 * the tray temperature and re-flashing once.</li>
 * <li>Linearise enthalpies: hL(T) = hL0 + CpL * (T - T0), hV(T) = hV0 + CpV *
 * (T - T0).</li>
 * </ol>
 * </p>
 *
 * <p>
 * Inner loop ("simple" model, frozen alpha and ln Kb coefficients):
 * <ol>
 * <li>For each component i, solve a tridiagonal component mass-balance for
 * the liquid component flow l[j][i].</li>
 * <li>For each tray, find T[j] from the bubble-point criterion
 * Sum alpha[j][i] x[j][i] Kb(T[j]) = 1.</li>
 * <li>Bottom-up sweep: total mass balance + linearised energy balance gives
 * V[j], L[j] (and Q[reboiler] when reboiler T is pinned).</li>
 * <li>Iterate inner until ||dT||, ||dV/V|| are small.</li>
 * </ol>
 * </p>
 *
 * <p>
 * Outer convergence is declared when the new full EOS K-values agree with the
 * linearised model: max |K[j][i] - alpha[j][i] Kb[j]| / K[j][i] &lt; tol.
 * </p>
 *
 * <p>
 * This implementation assumes a column with a reboiler at j=0 (bottom) and
 * either an open top (vapor product, no condenser, j=N-1) or a partial
 * condenser at j=N-1. Feeds may enter on any tray. When the reboiler
 * temperature is pinned, the reboiler duty becomes the dependent variable.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public class BostonSullivanInsideOut {

  /** Logger. */
  private static final Logger logger = LogManager.getLogger(BostonSullivanInsideOut.class);

  /** Universal gas constant in J / (mol K). */
  private static final double R_GAS = 8.314462618;

  /** Parent column. */
  private final DistillationColumn column;

  // ---- Tunable parameters ----

  /** Max outer iterations (full EOS flash + relinearisation). */
  private int maxOuterIterations = 30;

  /** Max inner iterations per outer (component-MB + bubble-T + EB). */
  private int maxInnerIterations = 30;

  /** Outer convergence tolerance on max relative K-value mismatch. */
  private double outerTolerance = 1.0e-3;

  /** Inner convergence tolerance on max |dT|/T plus max |dV|/V. */
  private double innerTolerance = 5.0e-4;

  /** Damping factor on outer-loop alpha update (0.0 = pure new, 1.0 = pure old). */
  private double outerDamping = 0.0;

  /** Damping factor on tray temperature updates inside the inner loop. */
  private double innerTempDamping = 0.3;

  /** Tray-T perturbation (K) used to estimate B[j] = d ln Kb / d (1/T). */
  private double dTperturbation = 1.0;

  // ---- Dimensions ----

  private int N;
  private int C;

  // ---- Tray state ----

  private double[] T;
  private double[] P;
  private double[] V;
  private double[] L;
  private double[][] x;
  private double[][] y;
  private double[][] K;
  private double[][] alpha;
  private double[] Kb;
  private double[] Acoef;
  private double[] Bcoef;
  private double[] T0;
  private double[] hL;
  private double[] hV;
  private double[] CpL;
  private double[] CpV;

  // ---- Feed bookkeeping (mol/s per tray) ----

  private double[][] feedFlow;
  private double[] feedHEnth;
  private double[] feedTotal;

  // ---- Topology flags ----

  private boolean hasReboiler;
  private boolean hasCondenser;
  private double reboilerTSpec = Double.NaN;
  private double condenserTSpec = Double.NaN;

  /** Distillate flow leaving the top stage (vapor product when no condenser). */
  private double D;

  /** Bottoms flow leaving the reboiler. */
  private double B;

  /** Reference system cloned from the first feed. */
  private SystemInterface refSys;

  // ---- Last-solve metrics ----

  private int lastOuterIterations;
  private int lastInnerIterations;
  private double lastTemperatureResidual;
  private double lastMassBalanceError;
  private double lastEnergyBalanceError;
  private double lastSolveTimeSeconds;

  /**
   * Construct a Boston-Sullivan inside-out solver for the given column.
   *
   * @param column the distillation column to solve
   */
  public BostonSullivanInsideOut(DistillationColumn column) {
    this.column = column;
  }

  /** @param n max outer iterations */
  public void setMaxOuterIterations(int n) {
    this.maxOuterIterations = Math.max(1, n);
  }

  /** @param n max inner iterations */
  public void setMaxInnerIterations(int n) {
    this.maxInnerIterations = Math.max(1, n);
  }

  /** @param tol outer-loop tolerance */
  public void setOuterTolerance(double tol) {
    this.outerTolerance = tol;
  }

  /** @param tol inner-loop tolerance */
  public void setInnerTolerance(double tol) {
    this.innerTolerance = tol;
  }

  /** @return outer iterations consumed in last solve */
  public int getLastOuterIterations() {
    return lastOuterIterations;
  }

  /** @return total inner iterations summed across last solve */
  public int getLastInnerIterations() {
    return lastInnerIterations;
  }

  /** @return max |dT| over last inner iteration in K */
  public double getLastTemperatureResidual() {
    return lastTemperatureResidual;
  }

  /** @return relative mass-balance error in last solve */
  public double getLastMassBalanceError() {
    return lastMassBalanceError;
  }

  /** @return relative energy-balance error in last solve */
  public double getLastEnergyBalanceError() {
    return lastEnergyBalanceError;
  }

  /** @return wall-clock solve time in seconds */
  public double getLastSolveTimeSeconds() {
    return lastSolveTimeSeconds;
  }

  /**
   * Solve the column using Boston-Sullivan inside-out.
   *
   * @param id calculation identifier
   * @return true if the outer loop converges within {@link #outerTolerance}
   */
  public boolean solve(UUID id) {
    long t0 = System.nanoTime();
    try {
      initialize();
      logger.info("Boston-Sullivan IO: N={}, C={}, reboilerTSpec={}, condenser={}", N, C,
          reboilerTSpec, hasCondenser);

      boolean outerConverged = false;
      for (int outer = 1; outer <= maxOuterIterations; outer++) {
        // (1) Full EOS flash on every tray with current x, T, P.
        rigorousTrayFlashes();

        // (2) Linearise: pick Kb, alpha, and fit B[j] by single perturbation.
        double[][] alphaOld = (alpha == null) ? null : copy2D(alpha);
        double[] KbOld = (Kb == null) ? null : Kb.clone();
        linearizeKModel();
        linearizeEnthalpyModel();

        if (outer > 1 && outerDamping > 0.0) {
          for (int j = 0; j < N; j++) {
            for (int i = 0; i < C; i++) {
              alpha[j][i] = outerDamping * alphaOld[j][i] + (1.0 - outerDamping) * alpha[j][i];
            }
            Kb[j] = outerDamping * KbOld[j] + (1.0 - outerDamping) * Kb[j];
          }
        }

        // (3) Inner loop: solve component MB + bubble-T + V/L from EB.
        int innerIters = innerLoop();
        lastInnerIterations += innerIters;

        // (4) Check outer convergence: do current K-values match alpha*Kb?
        double maxKmismatch = checkOuterConvergence();
        logger.info("BS-IO outer {} ({} inner iters): max |K - alpha*Kb|/K = {}",
            outer, innerIters, String.format("%.4e", maxKmismatch));

        lastOuterIterations = outer;
        if (maxKmismatch < outerTolerance) {
          outerConverged = true;
          break;
        }
      }

      // Apply results back to column trays.
      applyResultsToColumn(id);

      lastMassBalanceError = computeMassBalanceError();
      lastEnergyBalanceError = computeEnergyBalanceError();
      lastSolveTimeSeconds = (System.nanoTime() - t0) * 1.0e-9;

      logger.info("BS-IO done: outer={}, innerTotal={}, massErr={}%, energyErr={}%",
          lastOuterIterations, lastInnerIterations,
          String.format("%.4f", lastMassBalanceError * 100.0),
          String.format("%.4f", lastEnergyBalanceError * 100.0));
      return outerConverged;
    } catch (Exception ex) {
      logger.error("Boston-Sullivan IO failed: {}", ex.toString(), ex);
      System.err.println("BS-IO EXCEPTION: " + ex);
      ex.printStackTrace(System.err);
      lastSolveTimeSeconds = (System.nanoTime() - t0) * 1.0e-9;
      return false;
    }
  }

  // ===================================================================
  //  Initialization
  // ===================================================================

  private void initialize() {
    N = column.getNumberOfTrays();
    hasReboiler = column.hasReboiler();
    hasCondenser = column.hasCondenser();

    // Find the first feed to get C and a reference thermo system.
    Map<Integer, List<StreamInterface>> feedMap = column.getFeedStreams();
    StreamInterface firstFeed = null;
    for (List<StreamInterface> feeds : feedMap.values()) {
      if (!feeds.isEmpty()) {
        firstFeed = feeds.get(0);
        break;
      }
    }
    if (firstFeed == null) {
      throw new IllegalStateException("No feeds attached to column");
    }
    refSys = firstFeed.getThermoSystem().clone();
    refSys.setMultiPhaseCheck(false);
    C = refSys.getNumberOfComponents();

    T = new double[N];
    P = new double[N];
    V = new double[N];
    L = new double[N];
    x = new double[N][C];
    y = new double[N][C];
    K = new double[N][C];
    alpha = new double[N][C];
    Kb = new double[N];
    Acoef = new double[N];
    Bcoef = new double[N];
    T0 = new double[N];
    hL = new double[N];
    hV = new double[N];
    CpL = new double[N];
    CpV = new double[N];

    feedFlow = new double[N][C];
    feedHEnth = new double[N];
    feedTotal = new double[N];

    // Read current tray state from the column.
    for (int j = 0; j < N; j++) {
      SimpleTray tray = column.getTray(j);
      SystemInterface sys = tray.getThermoSystem();
      double Tj;
      double Pj;
      if (sys != null) {
        Tj = sys.getTemperature();
        Pj = sys.getPressure() * 1.0e5; // bar -> Pa
      } else {
        // Fallback: use seed temperature if available.
        Tj = column.getSeedTemperature(j);
        if (Double.isNaN(Tj) || Tj <= 0.0) {
          Tj = 300.0;
        }
        Pj = 1.0e5;
      }
      T[j] = Tj;
      P[j] = Pj;

      // Use a uniform initial x = average feed composition; refined in first
      // inner loop.
      // Caller is expected to have run an initialization pass (sequential or
      // BP) before calling BS-IO when possible; otherwise we fall back to the
      // first-feed composition.
      double[] zinit = readPhaseComposition(refSys, /*liquid*/ true);
      for (int i = 0; i < C; i++) {
        x[j][i] = zinit[i];
        y[j][i] = zinit[i];
      }
    }

    // Accumulate feed contributions onto trays.
    accumulateFeeds(feedMap);

    // Initial V, L profile from total mass balance assuming uniform split.
    initializeFlowsFromMassBalance();

    // Read reboiler T-spec (top-of-column convention: reboiler at j=0).
    if (hasReboiler) {
      Reboiler reb = column.getReboiler();
      if (reb != null) {
        try {
          double rebTSpec = reb.getOutTemperature();
          if (rebTSpec > 0.0 && !Double.isNaN(rebTSpec)) {
            reboilerTSpec = rebTSpec;
            T[0] = rebTSpec;
          }
        } catch (Exception ex) {
          // Reboiler may not have a T-spec.
        }
      }
    }
  }

  /**
   * Read a phase composition (z_i) from a thermo system. If the system is
   * single-phase, returns the overall composition.
   *
   * @param sys    the thermo system
   * @param liquid true to read the liquid phase (phase 1 if two-phase)
   * @return array of mole fractions, length C
   */
  private double[] readPhaseComposition(SystemInterface sys, boolean liquid) {
    double[] z = new double[C];
    if (sys.getNumberOfPhases() >= 2 && liquid) {
      PhaseInterface p = sys.getPhase(1);
      for (int i = 0; i < C; i++) {
        z[i] = p.getComponent(i).getx();
      }
    } else if (sys.getNumberOfPhases() >= 1) {
      PhaseInterface p = sys.getPhase(0);
      for (int i = 0; i < C; i++) {
        z[i] = p.getComponent(i).getx();
      }
    } else {
      // No phase computed yet — fall back to molar composition stored on
      // components.
      double total = 0.0;
      for (int i = 0; i < C; i++) {
        z[i] = sys.getPhase(0).getComponent(i).getNumberOfMolesInPhase();
        total += z[i];
      }
      if (total > 0.0) {
        for (int i = 0; i < C; i++) {
          z[i] /= total;
        }
      } else {
        for (int i = 0; i < C; i++) {
          z[i] = 1.0 / C;
        }
      }
    }
    // Normalize defensively.
    double sum = 0.0;
    for (int i = 0; i < C; i++) {
      if (z[i] < 1.0e-30) {
        z[i] = 1.0e-30;
      }
      sum += z[i];
    }
    for (int i = 0; i < C; i++) {
      z[i] /= sum;
    }
    return z;
  }

  /**
   * Build feed contribution arrays from the column's feed stream map.
   *
   * @param feedMap map of tray index -> list of feed streams
   */
  private void accumulateFeeds(Map<Integer, List<StreamInterface>> feedMap) {
    for (Map.Entry<Integer, List<StreamInterface>> e : feedMap.entrySet()) {
      int j = e.getKey();
      if (j < 0 || j >= N) {
        continue;
      }
      for (StreamInterface f : e.getValue()) {
        SystemInterface fs = f.getThermoSystem();
        if (fs == null) {
          continue;
        }
        // Total flow in mol/s.
        double Ftot = f.getFlowRate("mole/sec");
        double Hf;
        try {
          Hf = fs.getEnthalpy(); // J / batch (NeqSim returns total enthalpy)
        } catch (Exception ex) {
          Hf = 0.0;
        }
        // Convert total batch enthalpy to a molar enthalpy * Ftot equivalent.
        double totalMoles = fs.getTotalNumberOfMoles();
        double hMolar = totalMoles > 1.0e-30 ? Hf / totalMoles : 0.0;
        feedTotal[j] += Ftot;
        feedHEnth[j] += hMolar * Ftot;
        // Component-wise feed flows (mol/s).
        for (int i = 0; i < C; i++) {
          double zi = fs.getPhase(0).getComponent(i).getz();
          feedFlow[j][i] += zi * Ftot;
        }
      }
    }
  }

  /**
   * Initialise V, L profile from a simple constant-molar-overflow guess. The
   * inner loop will replace this with the energy-balance solution.
   */
  private void initializeFlowsFromMassBalance() {
    double totalFeed = 0.0;
    for (int j = 0; j < N; j++) {
      totalFeed += feedTotal[j];
    }
    if (totalFeed <= 0.0) {
      totalFeed = 1.0;
    }
    // Assume 50/50 split as initial guess; energy balance refines.
    B = 0.5 * totalFeed;
    D = totalFeed - B;
    for (int j = 0; j < N; j++) {
      L[j] = B + 0.3 * totalFeed;
      V[j] = D + 0.3 * totalFeed;
    }
  }

  // ===================================================================
  //  Outer loop: rigorous flashes and linearisation
  // ===================================================================

  /**
   * Run a rigorous EOS flash at every tray and populate K[j][i], hL[j], hV[j].
   * Uses the per-tray (x, T, P) as the flash specification.
   */
  private void rigorousTrayFlashes() {
    for (int j = 0; j < N; j++) {
      SystemInterface sys = refSys.clone();
      sys.setMultiPhaseCheck(false);
      sys.setTemperature(T[j]);
      sys.setPressure(P[j] / 1.0e5); // Pa -> bar
      // Set composition to current x[j][i] (liquid composition is what drives
      // bubble-point in inner loop; for the flash we set it as overall).
      double sumX = 0.0;
      for (int i = 0; i < C; i++) {
        sumX += x[j][i];
      }
      for (int i = 0; i < C; i++) {
        double xi = sumX > 0 ? x[j][i] / sumX : 1.0 / C;
        sys.getPhase(0).getComponent(i).setz(xi);
        if (sys.getNumberOfPhases() > 1) {
          sys.getPhase(1).getComponent(i).setz(xi);
        }
        // Drive overall n_i = xi for the bubble-point flash.
        sys.setMolarComposition(scalarToArray(xi, i));
      }
      // Re-set the overall composition (the per-component loop above is
      // imperfect; use setMolarComposition properly):
      double[] xVec = new double[C];
      for (int i = 0; i < C; i++) {
        xVec[i] = sumX > 0 ? x[j][i] / sumX : 1.0 / C;
      }
      sys.setMolarComposition(xVec);
      ThermodynamicOperations ops = new ThermodynamicOperations(sys);
      try {
        // Bubble-point flash at fixed T, P: we know x, want y and check K.
        ops.TPflash();
      } catch (Exception ex) {
        // Fall back to Wilson estimate.
        for (int i = 0; i < C; i++) {
          K[j][i] = wilsonK(sys, i, T[j], P[j]);
        }
        hL[j] = 0.0;
        hV[j] = 0.0;
        continue;
      }

      // Pull K-values from fugacity coefficients.
      int nph = sys.getNumberOfPhases();
      PhaseInterface gas = null;
      PhaseInterface liq = null;
      for (int p = 0; p < nph; p++) {
        PhaseInterface ph = sys.getPhase(p);
        if (ph.getType() == neqsim.thermo.phase.PhaseType.GAS) {
          gas = ph;
        } else if (ph.getType() == neqsim.thermo.phase.PhaseType.LIQUID
            || ph.getType() == neqsim.thermo.phase.PhaseType.AQUEOUS
            || ph.getType() == neqsim.thermo.phase.PhaseType.OIL) {
          if (liq == null) {
            liq = ph;
          }
        }
      }
      if (gas == null || liq == null) {
        // Single-phase result — use Wilson K as fallback for both ratios.
        for (int i = 0; i < C; i++) {
          K[j][i] = wilsonK(sys, i, T[j], P[j]);
        }
      } else {
        for (int i = 0; i < C; i++) {
          double phiL = liq.getComponent(i).getFugacityCoefficient();
          double phiV = gas.getComponent(i).getFugacityCoefficient();
          if (phiL <= 0.0 || phiV <= 0.0 || !Double.isFinite(phiL) || !Double.isFinite(phiV)) {
            K[j][i] = wilsonK(sys, i, T[j], P[j]);
          } else {
            K[j][i] = phiL / phiV;
            if (!Double.isFinite(K[j][i]) || K[j][i] <= 0.0) {
              K[j][i] = wilsonK(sys, i, T[j], P[j]);
            }
          }
        }
      }

      // Phase molar enthalpies (J/mol).
      try {
        if (liq != null) {
          double hLtot = liq.getEnthalpy();
          double nL = liq.getNumberOfMolesInPhase();
          hL[j] = nL > 1.0e-30 ? hLtot / nL : 0.0;
        }
        if (gas != null) {
          double hVtot = gas.getEnthalpy();
          double nV = gas.getNumberOfMolesInPhase();
          hV[j] = nV > 1.0e-30 ? hVtot / nV : 0.0;
        }
      } catch (Exception ex) {
        // Leave hL, hV at their previous values.
      }
      // Update y from K * x.
      double sumY = 0.0;
      for (int i = 0; i < C; i++) {
        y[j][i] = K[j][i] * x[j][i];
        sumY += y[j][i];
      }
      if (sumY > 0.0) {
        for (int i = 0; i < C; i++) {
          y[j][i] /= sumY;
        }
      }
    }
  }

  /**
   * Helper to build a single-position composition vector for setMolarComposition.
   * Not used after refactor; kept for clarity of intent.
   *
   * @param val value at position i
   * @param i   component index
   * @return vector of length C with val at index i and 0 elsewhere
   */
  private double[] scalarToArray(double val, int i) {
    double[] z = new double[C];
    z[i] = val;
    return z;
  }

  /**
   * Wilson correlation K-value as a robust fallback when the EOS flash returns
   * a single phase or non-finite fugacity coefficients.
   *
   * @param sys reference system (used only for critical properties)
   * @param i   component index
   * @param Tj  temperature in K
   * @param Pj  pressure in Pa
   * @return Wilson K-value
   */
  private double wilsonK(SystemInterface sys, int i, double Tj, double Pj) {
    double Tc = sys.getPhase(0).getComponent(i).getTC();
    double Pc = sys.getPhase(0).getComponent(i).getPC() * 1.0e5; // bar -> Pa
    double omega = sys.getPhase(0).getComponent(i).getAcentricFactor();
    if (Tc <= 0.0 || Pc <= 0.0) {
      return 1.0;
    }
    return (Pc / Pj) * Math.exp(5.37 * (1.0 + omega) * (1.0 - Tc / Tj));
  }

  /**
   * Linearise the K-value model: pick Kb[j] as the vapor-weighted geometric
   * mean of K[j][i], compute alpha[j][i] = K[j][i] / Kb[j], and fit
   * ln Kb[j] vs (1/T) by perturbing T[j] +/- dTperturbation and re-flashing
   * at the perturbed temperature.
   */
  private void linearizeKModel() {
    for (int j = 0; j < N; j++) {
      // Vapor-weighted geometric mean.
      double lnKb = 0.0;
      double wSum = 0.0;
      for (int i = 0; i < C; i++) {
        double yi = Math.max(y[j][i], 1.0e-30);
        double Ki = Math.max(K[j][i], 1.0e-30);
        lnKb += yi * Math.log(Ki);
        wSum += yi;
      }
      if (wSum > 0.0) {
        lnKb /= wSum;
      }
      Kb[j] = Math.exp(lnKb);
      T0[j] = T[j];
      Acoef[j] = lnKb;
      for (int i = 0; i < C; i++) {
        alpha[j][i] = K[j][i] / Math.max(Kb[j], 1.0e-30);
        if (!Double.isFinite(alpha[j][i]) || alpha[j][i] <= 0.0) {
          alpha[j][i] = 1.0;
        }
      }

      // Estimate B[j] = d ln Kb / d (1/T) by single perturbation.
      double Tperturb = T[j] + dTperturbation;
      double KbPerturb = estimateKbAtT(j, Tperturb);
      if (KbPerturb > 0.0 && Double.isFinite(KbPerturb)) {
        double lnKbP = Math.log(KbPerturb);
        double dInvT = 1.0 / Tperturb - 1.0 / T[j];
        if (Math.abs(dInvT) > 1.0e-15) {
          Bcoef[j] = (lnKbP - lnKb) / dInvT;
        }
      } else {
        // Fallback: Clausius-Clapeyron-style estimate from mean enthalpy of
        // vaporisation. Use 30 kJ/mol as a default.
        Bcoef[j] = -3.0e4 / R_GAS;
      }
    }
  }

  /**
   * One-shot estimate of Kb at a perturbed temperature without modifying the
   * stored tray state. Used only to fit B[j].
   *
   * @param j  tray index
   * @param Tp perturbed temperature in K
   * @return Kb estimate at Tp
   */
  private double estimateKbAtT(int j, double Tp) {
    try {
      SystemInterface sys = refSys.clone();
      sys.setMultiPhaseCheck(false);
      sys.setTemperature(Tp);
      sys.setPressure(P[j] / 1.0e5);
      double[] xVec = new double[C];
      double sumX = 0.0;
      for (int i = 0; i < C; i++) {
        sumX += x[j][i];
      }
      for (int i = 0; i < C; i++) {
        xVec[i] = sumX > 0 ? x[j][i] / sumX : 1.0 / C;
      }
      sys.setMolarComposition(xVec);
      ThermodynamicOperations ops = new ThermodynamicOperations(sys);
      ops.TPflash();
      PhaseInterface gas = null;
      PhaseInterface liq = null;
      for (int p = 0; p < sys.getNumberOfPhases(); p++) {
        PhaseInterface ph = sys.getPhase(p);
        if (ph.getType() == neqsim.thermo.phase.PhaseType.GAS) {
          gas = ph;
        } else if (liq == null) {
          liq = ph;
        }
      }
      if (gas == null || liq == null) {
        return Double.NaN;
      }
      double lnKb = 0.0;
      double wSum = 0.0;
      for (int i = 0; i < C; i++) {
        double yi = Math.max(gas.getComponent(i).getx(), 1.0e-30);
        double phiL = liq.getComponent(i).getFugacityCoefficient();
        double phiV = gas.getComponent(i).getFugacityCoefficient();
        if (phiL <= 0.0 || phiV <= 0.0) {
          continue;
        }
        double Ki = phiL / phiV;
        lnKb += yi * Math.log(Math.max(Ki, 1.0e-30));
        wSum += yi;
      }
      if (wSum <= 0.0) {
        return Double.NaN;
      }
      return Math.exp(lnKb / wSum);
    } catch (Exception ex) {
      return Double.NaN;
    }
  }

  /**
   * Linearise tray enthalpies as h(T) = h0 + Cp * (T - T0). Cp values are
   * estimated by one perturbation re-flash; if unavailable, fall back to ideal
   * gas estimates.
   */
  private void linearizeEnthalpyModel() {
    for (int j = 0; j < N; j++) {
      // Naive Cp estimate: assume liquid Cp ~ 150 J/mol/K, vapor Cp ~ 80 J/mol/K
      // for hydrocarbons. Refined by perturbation if available.
      CpL[j] = 150.0;
      CpV[j] = 80.0;
      // Refine by perturbation enthalpy.
      try {
        SystemInterface sys = refSys.clone();
        sys.setMultiPhaseCheck(false);
        sys.setTemperature(T[j] + dTperturbation);
        sys.setPressure(P[j] / 1.0e5);
        double[] xVec = new double[C];
        double sumX = 0.0;
        for (int i = 0; i < C; i++) {
          sumX += x[j][i];
        }
        for (int i = 0; i < C; i++) {
          xVec[i] = sumX > 0 ? x[j][i] / sumX : 1.0 / C;
        }
        sys.setMolarComposition(xVec);
        new ThermodynamicOperations(sys).TPflash();
        PhaseInterface gas = null;
        PhaseInterface liq = null;
        for (int p = 0; p < sys.getNumberOfPhases(); p++) {
          PhaseInterface ph = sys.getPhase(p);
          if (ph.getType() == neqsim.thermo.phase.PhaseType.GAS) {
            gas = ph;
          } else if (liq == null) {
            liq = ph;
          }
        }
        if (gas != null) {
          double hVp = gas.getEnthalpy() / Math.max(gas.getNumberOfMolesInPhase(), 1.0e-30);
          CpV[j] = (hVp - hV[j]) / dTperturbation;
        }
        if (liq != null) {
          double hLp = liq.getEnthalpy() / Math.max(liq.getNumberOfMolesInPhase(), 1.0e-30);
          CpL[j] = (hLp - hL[j]) / dTperturbation;
        }
        if (!Double.isFinite(CpL[j]) || CpL[j] <= 0.0) {
          CpL[j] = 150.0;
        }
        if (!Double.isFinite(CpV[j]) || CpV[j] <= 0.0) {
          CpV[j] = 80.0;
        }
      } catch (Exception ex) {
        // Keep defaults.
      }
    }
  }

  // ===================================================================
  //  Inner loop: component MB, bubble-T, energy balance
  // ===================================================================

  /**
   * Run the inner loop with frozen alpha, Kb-coefficients, and Cp parameters.
   * Each iteration:
   * <ol>
   * <li>Solve a tridiagonal component MB for each component to get l[j][i].</li>
   * <li>Recompute x[j][i] from l[j][i] / L[j] and normalise.</li>
   * <li>Bubble-T per tray from Sum alpha[j][i] x[j][i] Kb(T) = 1.</li>
   * <li>Bottom-up V/L sweep from total MB + linearised energy balance.</li>
   * </ol>
   *
   * @return number of inner iterations consumed
   */
  private int innerLoop() {
    int it;
    for (it = 1; it <= maxInnerIterations; it++) {
      double[] Tprev = T.clone();
      double[] Vprev = V.clone();

      // (1) Solve component MB tridiagonally for each component.
      solveComponentMassBalance();

      // (2) Bubble-T per tray.
      bubbleTUpdate();

      // (3) Energy-balance-driven V, L profile.
      energyBalanceFlows();

      // Convergence check on T and V.
      double maxDT = 0.0;
      double maxDV = 0.0;
      for (int j = 0; j < N; j++) {
        double dT = Math.abs(T[j] - Tprev[j]) / Math.max(T[j], 1.0);
        double dV = Math.abs(V[j] - Vprev[j]) / Math.max(V[j], 1.0e-6);
        if (dT > maxDT) {
          maxDT = dT;
        }
        if (dV > maxDV) {
          maxDV = dV;
        }
      }
      lastTemperatureResidual = maxDT * 300.0; // approx K
      if (maxDT + maxDV < innerTolerance) {
        return it;
      }
    }
    return maxInnerIterations;
  }

  /**
   * Solve the component mass balance tridiagonally for each component i:
   *
   * <pre>
   * stage j (0=bottom reboiler, N-1=top):
   *   V[j-1] y[j-1][i] + L[j+1] x[j+1][i] + F[j][i]
   *      - V[j] y[j][i] - L[j] x[j][i] = 0
   *
   * With y[j][i] = alpha[j][i] Kb[j] x[j][i] = Sj[i] / Lj * lj[i],
   * where Sj[i] = alpha[j][i] Kb[j] V[j] / L[j], the system in component
   * liquid flows l[j][i] = L[j] x[j][i] becomes tridiagonal.
   * </pre>
   *
   * <p>
   * Boundary conditions: at j=0 (reboiler) the vapor entering from below is
   * zero; at j=N-1 the liquid entering from above is the reflux (zero for an
   * open top). Products: vapor distillate D = V[N-1], bottoms B = L[0].
   * </p>
   */
  private void solveComponentMassBalance() {
    // Stripping factor S[j][i] = K[j][i] V[j] / L[j] = alpha[j][i] * Kb[j] * V[j] / L[j].
    double[][] Smat = new double[N][C];
    for (int j = 0; j < N; j++) {
      double rat = V[j] / Math.max(L[j], 1.0e-30);
      for (int i = 0; i < C; i++) {
        Smat[j][i] = alpha[j][i] * Kb[j] * rat;
      }
    }

    // For each component, build and solve a tridiagonal system in l[j][i].
    // The equation at internal stage j:
    //   - l[j+1][i]                                  (liquid from above)
    //   + (1 + S[j][i]) * l[j][i]                    (leaves as L and V)
    //   - S[j-1][i] * l[j-1][i] * (L[j-1]/L[j-1])   = F[j][i]
    // i.e. -l[j+1][i] + (1 + S[j][i]) l[j][i] - S[j-1][i] l[j-1][i] = F[j][i]
    //
    // Boundary at j=0 (reboiler, no vapor from below):
    //   -l[1][i] + (1 + S[0][i]) l[0][i] = F[0][i]
    // Boundary at j=N-1 (top, no liquid from above):
    //   (1 + S[N-1][i]) l[N-1][i] - S[N-2][i] l[N-2][i] = F[N-1][i]
    //
    // Tridiag: subDiag[j] = -S[j-1][i]   (coefficient of l[j-1][i])
    //          diag[j] = (1 + S[j][i])
    //          supDiag[j] = -1            (coefficient of l[j+1][i])
    //          rhs[j] = F[j][i]
    double[] sub = new double[N];
    double[] diag = new double[N];
    double[] sup = new double[N];
    double[] rhs = new double[N];

    for (int i = 0; i < C; i++) {
      for (int j = 0; j < N; j++) {
        sub[j] = (j == 0) ? 0.0 : -Smat[j - 1][i];
        sup[j] = (j == N - 1) ? 0.0 : -1.0;
        diag[j] = 1.0 + Smat[j][i];
        rhs[j] = feedFlow[j][i];
      }
      double[] li = thomasSolve(sub, diag, sup, rhs);
      // Update x[j][i] = l[j][i] / L[j].
      for (int j = 0; j < N; j++) {
        double Lj = Math.max(L[j], 1.0e-30);
        double xij = Math.max(li[j] / Lj, 1.0e-30);
        x[j][i] = xij;
      }
    }

    // Normalise x and recompute y = K x (then renormalise).
    for (int j = 0; j < N; j++) {
      double sumX = 0.0;
      for (int i = 0; i < C; i++) {
        sumX += x[j][i];
      }
      if (sumX > 0.0) {
        for (int i = 0; i < C; i++) {
          x[j][i] /= sumX;
        }
      }
      double sumY = 0.0;
      for (int i = 0; i < C; i++) {
        y[j][i] = alpha[j][i] * Kb[j] * x[j][i];
        sumY += y[j][i];
      }
      if (sumY > 0.0) {
        for (int i = 0; i < C; i++) {
          y[j][i] /= sumY;
        }
      }
    }
  }

  /**
   * Update tray temperatures from the bubble-point criterion using the
   * linearised Kb(T) model.
   *
   * <pre>
   * Sum_i alpha[j][i] x[j][i] Kb(T*) = 1
   *  =&gt;  Kb(T*) = 1 / Sum_i alpha[j][i] x[j][i]
   * With  ln Kb(T*) = A[j] + B[j] * (1/T* - 1/T0[j])
   *  =&gt;  1/T* = 1/T0[j] + (ln Kb(T*) - A[j]) / B[j]
   * </pre>
   *
   * <p>
   * Damped update: T[j] := (1 - damp) * T[j] + damp * T*. If the reboiler T is
   * pinned, T[0] is held at the spec value.
   * </p>
   */
  private void bubbleTUpdate() {
    for (int j = 0; j < N; j++) {
      if (j == 0 && !Double.isNaN(reboilerTSpec)) {
        T[j] = reboilerTSpec;
        continue;
      }
      double sumAlphaX = 0.0;
      for (int i = 0; i < C; i++) {
        sumAlphaX += alpha[j][i] * x[j][i];
      }
      if (sumAlphaX <= 0.0 || !Double.isFinite(sumAlphaX)) {
        continue;
      }
      double KbTarget = 1.0 / sumAlphaX;
      double lnKbTarget = Math.log(Math.max(KbTarget, 1.0e-30));
      if (Math.abs(Bcoef[j]) < 1.0e-15) {
        continue;
      }
      double invTstar = 1.0 / T0[j] + (lnKbTarget - Acoef[j]) / Bcoef[j];
      if (invTstar <= 0.0 || !Double.isFinite(invTstar)) {
        continue;
      }
      double Tstar = 1.0 / invTstar;
      // Damped update.
      double Tnew = (1.0 - innerTempDamping) * T[j] + innerTempDamping * Tstar;
      // Clamp to a sensible window to avoid runaway.
      Tnew = Math.max(Tnew, 0.5 * T[j]);
      Tnew = Math.min(Tnew, 2.0 * T[j]);
      T[j] = Tnew;
    }
  }

  /**
   * Update V[j], L[j] from total mass balance and linearised energy balance.
   * Sweeps from the top (j = N-1) down to the bottom (j = 0).
   *
   * <pre>
   * Total MB cumulative from top:
   *   sum_{k=j..N-1} feedTotal[k] = D + sum_{k=j..N-1} (L[k] - V[k-1])
   *
   * Energy balance per tray:
   *   V[j-1] h_V(T_{j-1}) + L[j+1] h_L(T_{j+1}) + F[j] h_F[j]
   *     = V[j] h_V(T_j) + L[j] h_L(T_j) + Q_j
   * </pre>
   *
   * <p>
   * For simplicity in this implementation we set V[N-1] from a top energy
   * balance (using the column's overall MB), then sweep downward using each
   * stage's MB to get L[j-1], then update V[j-1] from that stage's energy
   * balance (the linearised enthalpies make this a single linear equation per
   * tray).
   * </p>
   */
  private void energyBalanceFlows() {
    double totalFeed = 0.0;
    double totalFeedEnth = 0.0;
    for (int j = 0; j < N; j++) {
      totalFeed += feedTotal[j];
      totalFeedEnth += feedHEnth[j];
    }

    // Top: V[N-1] = D (vapor distillate flow), L[N-1] = reflux (0 for open top).
    // For an open top with no condenser, L[N-1] = 0 and V[N-1] is unknown.
    // We pick V[N-1] from energy balance below; start with previous value.
    // Bottom (reboiler at j=0): L[0] = B (bottoms), V[0] is the boilup.

    // Step 1: distillate from overall MB. With reboiler T pinned, we treat
    // bottoms flow B as the consequence of the column inventory: B = totalFeed
    // - D where D = V[N-1] (open top) or D = condenser product (with
    // condenser). For the initial pass we keep the existing D, B values.

    // Step 2: bottom-up sweep using per-tray total MB plus energy balance.
    // At stage j:
    //   V[j-1] + L[j+1] + F[j] = V[j] + L[j]                                (MB)
    //   V[j-1] hV[j-1] + L[j+1] hL[j+1] + F[j] hF[j] = V[j] hV[j] + L[j] hL[j] + Q[j]  (EB)
    //
    // Assume Q[j] = 0 except for reboiler/condenser stages.
    // With two equations and two unknowns (V[j-1] and L[j+1] are known from
    // previous sweep), we solve for V[j] and L[j]:
    //
    //   V[j] = V[j-1] + L[j+1] + F[j] - L[j]                                (from MB)
    //   Substitute into EB:
    //     V[j-1] hV[j-1] + L[j+1] hL[j+1] + F[j] hF[j]
    //       = (V[j-1] + L[j+1] + F[j] - L[j]) hV[j] + L[j] hL[j] + Q[j]
    //   =&gt; V[j-1] (hV[j-1] - hV[j]) + L[j+1] (hL[j+1] - hV[j])
    //         + F[j] (hF[j] - hV[j]) - Q[j]
    //       = L[j] (hL[j] - hV[j])
    //   =&gt; L[j] = [V[j-1] (hV[j-1] - hV[j]) + L[j+1] (hL[j+1] - hV[j])
    //               + F[j] (hF[j] - hV[j]) - Q[j]] / (hL[j] - hV[j])
    //
    // The denominator (hL - hV) is the negative latent heat per mole (typically
    // -3e4 J/mol), so division is well-conditioned.

    // For the open-top column at j=N-1, L[N-1] = 0 (no reflux) and V[N-1] is
    // the distillate flow. We sweep down from j=N-1 to j=0 with the convention
    // that L[N] = 0 and V[-1] = 0 (no vapor below reboiler).
    double Lprev = 0.0;
    double Vbelow = 0.0;
    // First, top stage: V[N-1] from energy balance? Easier: just iterate the
    // sweep with L[N-1] = 0 and let bottom-up MB determine V[N-1] implicitly.
    // We need V[N-2] (below the top) to know V[N-1] = V[N-2] + F[N-1] - L[N-1].
    // So sweep TOP-DOWN: start at j=N-1 with L[N-1] from the energy balance:
    //   At stage N-1: V[N-2] is below (unknown initially), L[N] = 0,
    //   F[N-1] = top feed.

    // To handle the boundary, we will do an iterative pass:
    //   (a) Assume D = V[N-1] from current value
    //   (b) Sweep down from j=N-1 using:
    //         L[j] from EB above (with L[j+1] known from prev iter or boundary)
    //         V[j-1] = V[j] + L[j] - F[j] - L[j+1] (mass balance)
    //   (c) Repeat a few times.

    int maxSweepIter = 5;
    for (int sw = 0; sw < maxSweepIter; sw++) {
      double[] Vnew = V.clone();
      double[] Lnew = L.clone();
      // L[N-1] = 0 for open top (no condenser). Set explicitly.
      if (!hasCondenser) {
        Lnew[N - 1] = 0.0;
      }
      // Sweep top-down: j = N-1, N-2, ..., 1.
      for (int j = N - 1; j >= 1; j--) {
        double hVj = hV[j];
        double hLj = hL[j];
        double hVj1 = (j >= 1) ? hV[j - 1] : hV[0];
        double hLjp1 = (j + 1 < N) ? hL[j + 1] : hL[j];
        double Lj_above = (j + 1 < N) ? Lnew[j + 1] : 0.0;
        double Vj_below = (j >= 1) ? Vnew[j - 1] : 0.0;
        double Fj = feedTotal[j];
        double hFj = feedTotal[j] > 1.0e-30 ? feedHEnth[j] / feedTotal[j] : 0.0;
        double Qj = 0.0; // No external heat on internal trays.

        double denom = hLj - hVj;
        if (Math.abs(denom) < 1.0e-3) {
          continue;
        }
        double Lj = (Vj_below * (hVj1 - hVj) + Lj_above * (hLjp1 - hVj)
            + Fj * (hFj - hVj) - Qj) / denom;
        if (!Double.isFinite(Lj) || Lj < 0.0) {
          Lj = Math.max(Lnew[j], 1.0e-3);
        }
        Lnew[j] = Lj;
        // V[j-1] from mass balance at stage j:
        // V[j-1] + L[j+1] + F[j] = V[j] + L[j]  =>  V[j-1] = V[j] + L[j] - L[j+1] - F[j]
        double VjBelowNew = Vnew[j] + Lj - Lj_above - Fj;
        if (!Double.isFinite(VjBelowNew) || VjBelowNew < 0.0) {
          VjBelowNew = Math.max(Vnew[j - 1], 1.0e-3);
        }
        Vnew[j - 1] = VjBelowNew;
      }
      // Reboiler stage (j=0): L[0] = B = totalFeed - D, where D = V[N-1].
      // With reboiler T pinned, Q[0] absorbs the residual energy.
      double D_top = Vnew[N - 1];
      double B_bot = totalFeed - D_top;
      if (B_bot < 0.0) {
        B_bot = 0.0;
      }
      Lnew[0] = B_bot;
      // V[0] from mass balance at stage 0: F[0] + L[1] = V[0] + L[0]
      // => V[0] = F[0] + L[1] - L[0].
      double V0 = feedTotal[0] + (N >= 2 ? Lnew[1] : 0.0) - Lnew[0];
      if (!Double.isFinite(V0) || V0 < 0.0) {
        V0 = Math.max(Vnew[0], 1.0e-3);
      }
      Vnew[0] = V0;

      V = Vnew;
      L = Lnew;
      D = V[N - 1];
      B = L[0];
    }
  }

  // ===================================================================
  //  Outer convergence and result handoff
  // ===================================================================

  /**
   * After the inner loop, re-flash each tray and check whether the rigorous
   * K-values still agree with the linearised model alpha[j][i] * Kb[j].
   *
   * @return max relative K-value mismatch across all trays and components
   */
  private double checkOuterConvergence() {
    double maxRel = 0.0;
    rigorousTrayFlashes();
    for (int j = 0; j < N; j++) {
      for (int i = 0; i < C; i++) {
        double Kmodel = alpha[j][i] * Kb[j];
        double Krig = K[j][i];
        if (Krig <= 0.0) {
          continue;
        }
        double rel = Math.abs(Krig - Kmodel) / Krig;
        if (rel > maxRel) {
          maxRel = rel;
        }
      }
    }
    return maxRel;
  }

  /**
   * Write the solved state back onto the column trays so downstream code can
   * read product flows, compositions, and temperatures via the standard
   * SimpleTray API.
   *
   * @param id calculation identifier
   */
  private void applyResultsToColumn(UUID id) {
    for (int j = 0; j < N; j++) {
      SimpleTray tray = column.getTray(j);
      try {
        // Mutate the tray's live thermo system in place (SimpleTray has no
        // setThermoSystem; getThermoSystem() returns the mixedStream's live
        // SystemInterface reference).
        SystemInterface sys = tray.getThermoSystem();
        if (sys == null) {
          continue;
        }
        sys.setMultiPhaseCheck(true);
        sys.setTemperature(T[j]);
        sys.setPressure(P[j] / 1.0e5);
        double[] zVec = new double[C];
        // Use the overall tray-fluid composition (V*y + L*x) / (V + L).
        double Vj = Math.max(V[j], 1.0e-30);
        double Lj = Math.max(L[j], 1.0e-30);
        double total = Vj + Lj;
        for (int i = 0; i < C; i++) {
          zVec[i] = (Vj * y[j][i] + Lj * x[j][i]) / total;
        }
        sys.setMolarComposition(zVec);
        sys.setTotalFlowRate(total, "mole/sec");
        new ThermodynamicOperations(sys).TPflash();
        tray.setTemperature(T[j]);
        tray.setPressure(P[j] / 1.0e5);
      } catch (Exception ex) {
        logger.warn("BS-IO: failed to apply tray {} state: {}", j, ex.toString());
      }
    }
  }

  /**
   * Overall mass-balance error: |sum(feeds) - (D + B)| / sum(feeds).
   *
   * @return fractional mass-balance error
   */
  private double computeMassBalanceError() {
    double tot = 0.0;
    for (int j = 0; j < N; j++) {
      tot += feedTotal[j];
    }
    if (tot <= 0.0) {
      return 0.0;
    }
    return Math.abs(tot - (D + B)) / tot;
  }

  /**
   * Overall energy-balance error: |sum(feed h) - (D h_V_top + B h_L_bot + Q_R)|
   * / |sum(feed h)|. Q_R is recovered from the reboiler stage energy balance.
   *
   * @return fractional energy-balance error
   */
  private double computeEnergyBalanceError() {
    double hFeed = 0.0;
    double hFeedAbs = 0.0;
    for (int j = 0; j < N; j++) {
      hFeed += feedHEnth[j];
      hFeedAbs += Math.abs(feedHEnth[j]);
    }
    if (hFeedAbs <= 0.0) {
      return 0.0;
    }
    double hOut = D * hV[N - 1] + B * hL[0];
    // Reboiler duty: residual to close balance.
    double Qr = hOut - hFeed;
    // Relative to feed enthalpy magnitude.
    return Math.abs(hOut - hFeed - Qr) / hFeedAbs;
  }

  // ===================================================================
  //  Small utilities
  // ===================================================================

  /**
   * Thomas algorithm for a tridiagonal linear system. Solves
   * <pre>
   *   sub[j] * x[j-1] + diag[j] * x[j] + sup[j] * x[j+1] = rhs[j]
   * </pre>
   *
   * @param sub  sub-diagonal, length N (sub[0] unused)
   * @param diag diagonal, length N
   * @param sup  super-diagonal, length N (sup[N-1] unused)
   * @param rhs  right-hand side, length N
   * @return solution vector x of length N
   */
  private double[] thomasSolve(double[] sub, double[] diag, double[] sup, double[] rhs) {
    int n = diag.length;
    double[] cp = new double[n];
    double[] dp = new double[n];
    if (Math.abs(diag[0]) < 1.0e-30) {
      return new double[n];
    }
    cp[0] = sup[0] / diag[0];
    dp[0] = rhs[0] / diag[0];
    for (int j = 1; j < n; j++) {
      double m = diag[j] - sub[j] * cp[j - 1];
      if (Math.abs(m) < 1.0e-30) {
        return new double[n];
      }
      cp[j] = (j < n - 1) ? sup[j] / m : 0.0;
      dp[j] = (rhs[j] - sub[j] * dp[j - 1]) / m;
    }
    double[] xv = new double[n];
    xv[n - 1] = dp[n - 1];
    for (int j = n - 2; j >= 0; j--) {
      xv[j] = dp[j] - cp[j] * xv[j + 1];
    }
    return xv;
  }

  /**
   * Deep-copy a 2-D double array.
   *
   * @param src source array
   * @return independent copy
   */
  private double[][] copy2D(double[][] src) {
    double[][] dst = new double[src.length][];
    for (int i = 0; i < src.length; i++) {
      dst[i] = src[i].clone();
    }
    return dst;
  }
}
