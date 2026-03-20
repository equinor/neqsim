/*
 * PTPhaseEnvelopeMichelsen.java
 *
 * Unified phase envelope calculation using the Michelsen continuation method. Replaces the
 * fragmented implementations (PTphaseEnvelope, PTphaseEnvelopeMay, PTphaseEnvelopeNew,
 * PTphaseEnvelopeNew2, PTphaseEnvelopeNew3) with a single robust algorithm.
 *
 * Algorithm: Michelsen (1980) natural parameter continuation with: - 3rd-order polynomial predictor
 * through last 4 converged points - Adaptive step size control based on Newton iteration count -
 * Automatic specification variable selection (most sensitive variable) - Iterative (non-recursive)
 * restart with automatic branch switching - Critical point detection using K-value convergence
 * monitoring - Dynamic ArrayList storage for arbitrary-length envelopes
 *
 * References: - Michelsen, M.L. (1980). "Calculation of phase envelopes and critical points for
 * multicomponent mixtures." Fluid Phase Equilibria, 4, 1-10. - Michelsen, M.L. and Mollerup, J.M.
 * (2004). "Thermodynamic Models: Fundamentals &amp; Computational Aspects." Tie-Line Publications.
 */

package neqsim.thermodynamicoperations.phaseenvelopeops.multicomponentenvelopeops;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.BaseOperation;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Unified PT phase envelope calculation using the Michelsen continuation method.
 *
 * <p>
 * This implementation traces the two-phase boundary (bubble and dew point curves) in
 * pressure-temperature space for multicomponent mixtures. The algorithm uses a predictor-corrector
 * approach with natural parameter continuation to follow the envelope through the critical point
 * region.
 * </p>
 *
 * <p>
 * The algorithm traces from one end of the envelope (dew or bubble side at low pressure), through
 * the cricondenbar and cricondentherm, across the critical point, and back down the other side. If
 * the trace crashes before completing the full envelope, it automatically restarts from the
 * opposite side to fill in the gap.
 * </p>
 *
 * <p>
 * Key improvements over legacy implementations:
 * </p>
 * <ul>
 * <li>Non-recursive restart (no stack overflow risk)</li>
 * <li>Dynamic ArrayList storage (no fixed 10,000-point limit)</li>
 * <li>Configurable step limits and pressure bounds</li>
 * <li>K-value reset and Tmin stopping criterion for restart branch</li>
 * <li>Clean separated data output for bubble and dew point branches</li>
 * </ul>
 *
 * @author asmund
 * @version 2.0
 */
public class PTPhaseEnvelopeMichelsen extends BaseOperation {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  private static final Logger logger = LogManager.getLogger(PTPhaseEnvelopeMichelsen.class);

  /** Constant in the Wilson K-value correlation (5.373). */
  private static final double WILSON_CONST = 5.373;
  /** Maximum Newton iterations for Wilson temperature estimate. */
  private static final int MAX_WILSON_ITERATIONS = 1000;
  /** Maximum number of attempts when estimating the first point. */
  private static final int FIRST_POINT_ATTEMPTS = 5;
  /** Temperature step (K) used when searching for the first point. */
  private static final double FIRST_POINT_STEP = 2.0;
  /** Maximum envelope points per branch to prevent infinite loops. */
  private static final int MAX_ENVELOPE_ITERATIONS = 9980;
  /** Maximum points per quality line. */
  private static final int MAX_QUALITY_LINE_POINTS = 5000;

  // --- Configuration ---
  private double maxPressure = 1000.0;
  private double minPressure = 1.0;
  private double lowPres = 1.0;
  private double dTmax = 10.0;
  private double dPmax = 10.0;
  private double phaseFraction = 1e-10;
  private boolean bubblePointFirst = true;

  // --- System reference ---
  private SystemInterface system;

  // --- State tracking ---
  /** True while adding points to dew lists; false for bubble lists. */
  private boolean isDewPhase = true;
  /** Starting component index for Wilson estimate. */
  private int speceq = 0;

  // --- Results: dynamic storage ---
  private ArrayList<Double> dewPointTemperatures = new ArrayList<Double>();
  private ArrayList<Double> dewPointPressures = new ArrayList<Double>();
  private ArrayList<Double> bubblePointTemperatures = new ArrayList<Double>();
  private ArrayList<Double> bubblePointPressures = new ArrayList<Double>();
  private ArrayList<Double> dewPointEnthalpies = new ArrayList<Double>();
  private ArrayList<Double> bubblePointEnthalpies = new ArrayList<Double>();
  private ArrayList<Double> dewPointDensities = new ArrayList<Double>();
  private ArrayList<Double> bubblePointDensities = new ArrayList<Double>();
  private ArrayList<Double> dewPointEntropies = new ArrayList<Double>();
  private ArrayList<Double> bubblePointEntropies = new ArrayList<Double>();

  // --- Output arrays (built after run) ---
  private double[] dewTempArray = new double[0];
  private double[] dewPresArray = new double[0];
  private double[] dewEnthalpyArray = new double[0];
  private double[] dewDensityArray = new double[0];
  private double[] dewEntropyArray = new double[0];
  private double[] bubTempArray = new double[0];
  private double[] bubPresArray = new double[0];
  private double[] bubEnthalpyArray = new double[0];
  private double[] bubDensityArray = new double[0];
  private double[] bubEntropyArray = new double[0];

  // --- Critical point and characteristic points ---
  private double[] cricondenTherm = new double[3];
  private double[] cricondenBar = new double[3];
  private double[] cricondenThermX = new double[100];
  private double[] cricondenThermY = new double[100];
  private double[] cricondenBarX = new double[100];
  private double[] cricondenBarY = new double[100];

  // --- Saved first-pass data for merging after restart ---
  private double[] cricondenThermFirst = new double[3];
  private double[] cricondenBarFirst = new double[3];
  private double[] cricondenThermXFirst = new double[100];
  private double[] cricondenThermYFirst = new double[100];
  private double[] cricondenBarXFirst = new double[100];
  private double[] cricondenBarYFirst = new double[100];

  // --- Quality line data (keyed by "qualityT_0.5", "qualityP_0.5", etc.) ---
  private Map<String, double[]> qualityLineData = new HashMap<String, double[]>();
  /** Traced quality line beta values. */
  private double[] qualityBetaValues = new double[0];

  /**
   * Default constructor.
   */
  public PTPhaseEnvelopeMichelsen() {}

  /**
   * Constructor for PTPhaseEnvelopeMichelsen.
   *
   * @param system the thermodynamic system
   * @param name output file name (unused, kept for API compatibility)
   * @param phaseFraction initial phase fraction (near 0 = bubble, near 1 = dew)
   * @param lowPres starting low pressure in bara
   * @param bubfirst if true, trace bubble point curve first
   */
  public PTPhaseEnvelopeMichelsen(SystemInterface system, String name, double phaseFraction,
      double lowPres, boolean bubfirst) {
    this.system = system;
    this.phaseFraction = phaseFraction;
    this.lowPres = lowPres;
    this.bubblePointFirst = bubfirst;
  }

  /** {@inheritDoc} */
  @Override
  public SystemInterface getThermoSystem() {
    return system;
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * Traces the phase envelope using a two-pass approach. The primary pass traces from the starting
   * side (dew or bubble) through the full envelope. If it crashes, a second pass traces from the
   * opposite side to fill in the gap. This avoids the recursive restart used in legacy
   * implementations.
   * </p>
   */
  @Override
  public void run() {
    double initialTemp = system.getTemperature();
    double initialPres = system.getPressure();

    // isDewPhase determines which list receives each traced point.
    // When starting from dew side (bubblePointFirst=false, phaseFraction~1),
    // points go to dew lists. At CP, they switch to bubble lists.
    isDewPhase = true;

    boolean needRestart = false;
    double restartTmin = 0.0;

    // === Two-pass loop: primary trace + optional restart ===
    for (int pass = 0; pass < 2; pass++) {
      if (pass == 1) {
        if (!needRestart) {
          break;
        }

        // Save first-pass cricondentherm/bar for later merge
        cricondenThermFirst = cricondenTherm.clone();
        cricondenBarFirst = cricondenBar.clone();
        cricondenThermXFirst = cricondenThermX.clone();
        cricondenThermYFirst = cricondenThermY.clone();
        cricondenBarXFirst = cricondenBarX.clone();
        cricondenBarYFirst = cricondenBarY.clone();

        // Reset tracking for second pass
        cricondenTherm = new double[3];
        cricondenBar = new double[3];
        cricondenThermX = new double[100];
        cricondenThermY = new double[100];
        cricondenBarX = new double[100];
        cricondenBarY = new double[100];

        // Flip conditions for second pass
        phaseFraction = 1.0 - phaseFraction;
        bubblePointFirst = !bubblePointFirst;
        isDewPhase = false;

        // Reset K-values using Wilson correlation
        resetKValuesWithWilson();
      }

      // === Standard initialization ===
      speceq = 0;
      system.init(0);

      for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
        if (system.getComponent(i).getz() < 1e-10) {
          continue;
        }
        if (system.getPhase(0).getComponent(i).getIonicCharge() == 0) {
          if (bubblePointFirst && system.getPhase(0).getComponent(speceq).getTC() > system
              .getPhase(0).getComponent(i).getTC()) {
            speceq = system.getPhase(0).getComponent(i).getComponentNumber();
          }
          if (!bubblePointFirst && system.getPhase(0).getComponent(speceq).getTC() < system
              .getPhase(0).getComponent(i).getTC()) {
            speceq = system.getPhase(0).getComponent(i).getComponentNumber();
          }
        }
      }

      // Estimate initial temperature using Wilson correlation
      double temp = tempKWilson(phaseFraction, lowPres);
      if (Double.isNaN(temp)) {
        temp = system.getPhase(0).getComponent(speceq).getTC() - 20.0;
      }
      system.setTemperature(temp);
      system.setPressure(lowPres);

      // Converge first point using saturation flash
      ThermodynamicOperations testOps = new ThermodynamicOperations(system);
      boolean firstPointConverged = false;
      for (int attempt = 0; attempt < FIRST_POINT_ATTEMPTS; attempt++) {
        try {
          if (phaseFraction < 0.5) {
            temp += attempt * FIRST_POINT_STEP;
            system.setTemperature(temp);
            testOps.bubblePointTemperatureFlash();
          } else {
            temp += attempt * FIRST_POINT_STEP;
            system.setTemperature(temp);
            testOps.dewPointTemperatureFlash();
          }
        } catch (Exception ex) {
          continue;
        }
        double tempNy = system.getTemperature();
        if (!Double.isNaN(tempNy)) {
          temp = tempNy;
          firstPointConverged = true;
          break;
        }
      }
      if (!firstPointConverged) {
        logger.warn("Could not converge first envelope point for pass={}, beta={}", pass,
            phaseFraction);
        continue;
      }

      // Set up for continuation
      system.setBeta(phaseFraction);
      system.setPressure(lowPres);
      system.setTemperature(temp);

      SysNewtonRhapsonPhaseEnvelope nonLinSolver =
          new SysNewtonRhapsonPhaseEnvelope(system, 2, system.getPhase(0).getNumberOfComponents());
      nonLinSolver.dTmax = this.dTmax;
      nonLinSolver.dPmax = this.dPmax;
      nonLinSolver.setu();
      boolean passedCricoT = false;

      // === Main continuation loop ===
      int np;
      for (np = 1; np < MAX_ENVELOPE_ITERATIONS; np++) {
        try {
          nonLinSolver.calcInc(np);
          nonLinSolver.solve(np);
        } catch (Exception e0) {
          if (pass == 0) {
            // Primary trace crashed: schedule restart from opposite side
            needRestart = true;
            if (np > 2) {
              // Use recent stored temperature as Tmin for second pass
              ArrayList<Double> tempList =
                  isDewPhase ? dewPointTemperatures : bubblePointTemperatures;
              if (!tempList.isEmpty()) {
                restartTmin = tempList.get(tempList.size() - 1);
              } else {
                restartTmin = system.getTemperature();
              }
            } else {
              restartTmin = system.getTemperature();
            }
          }
          np = np - 1;
          break;
        }

        double currentT = system.getTemperature();
        double currentP = system.getPressure();

        // === Critical point detection via K-value convergence ===
        double Kvallc = system.getPhase(0).getComponent(nonLinSolver.lc).getx()
            / system.getPhase(1).getComponent(nonLinSolver.lc).getx();
        double Kvalhc = system.getPhase(0).getComponent(nonLinSolver.hc).getx()
            / system.getPhase(1).getComponent(nonLinSolver.hc).getx();

        if (!nonLinSolver.etterCP) {
          if (Kvallc < 1.05 && Kvalhc > 0.95) {
            nonLinSolver.npCrit = np;
            system.invertPhaseTypes();
            nonLinSolver.etterCP = true;
            isDewPhase = !isDewPhase;
            nonLinSolver.calcCrit();
          }
        }

        // === Cricondentherm tracking (maximum temperature) ===
        if (currentT > cricondenTherm[0]) {
          cricondenTherm[1] = currentP;
          cricondenTherm[0] = currentT;
          for (int ii = 0; ii < nonLinSolver.numberOfComponents; ii++) {
            cricondenThermX[ii] = system.getPhase(1).getComponent(ii).getx();
            cricondenThermY[ii] = system.getPhase(0).getComponent(ii).getx();
          }
        } else {
          nonLinSolver.ettercricoT = true;
          passedCricoT = true;
        }

        // === Cricondenbar tracking (maximum pressure) ===
        if (currentP > cricondenBar[1]) {
          cricondenBar[0] = currentT;
          cricondenBar[1] = currentP;
          for (int ii = 0; ii < nonLinSolver.numberOfComponents; ii++) {
            cricondenBarX[ii] = system.getPhase(1).getComponent(ii).getx();
            cricondenBarY[ii] = system.getPhase(0).getComponent(ii).getx();
          }
        }

        // === Exit criteria ===
        if (currentP < minPressure && passedCricoT) {
          break;
        }
        if (currentP > maxPressure) {
          break;
        }
        if (pass == 1 && restartTmin > 0 && currentT > restartTmin) {
          break;
        }

        // === Store the point ===
        if (currentT > 1e-6 && currentP > 1e-6 && !Double.isNaN(currentT)
            && !Double.isNaN(currentP)) {
          double enthalpy =
              system.getPhase(1).getEnthalpy() / system.getPhase(1).getNumberOfMolesInPhase()
                  / system.getPhase(1).getMolarMass() / 1e3;
          double density = system.getPhase(1).getDensity();
          double entropy =
              system.getPhase(1).getEntropy() / system.getPhase(1).getNumberOfMolesInPhase()
                  / system.getPhase(1).getMolarMass() / 1e3;

          if (isDewPhase) {
            dewPointTemperatures.add(currentT);
            dewPointPressures.add(currentP);
            dewPointEnthalpies.add(enthalpy);
            dewPointDensities.add(density);
            dewPointEntropies.add(entropy);
          } else {
            bubblePointTemperatures.add(currentT);
            bubblePointPressures.add(currentP);
            bubblePointEnthalpies.add(enthalpy);
            bubblePointDensities.add(density);
            bubblePointEntropies.add(entropy);
          }
        }
      }

      // Set critical point on the system
      system.setTemperature(system.getTC());
      system.setPressure(system.getPC());
    }

    // === Merge cricondentherm/bar from first and second passes ===
    if (needRestart) {
      if (cricondenThermFirst[0] > cricondenTherm[0]) {
        cricondenTherm = cricondenThermFirst;
        cricondenThermX = cricondenThermXFirst;
        cricondenThermY = cricondenThermYFirst;
      }
      if (cricondenBarFirst[1] > cricondenBar[1]) {
        cricondenBar = cricondenBarFirst;
        cricondenBarX = cricondenBarXFirst;
        cricondenBarY = cricondenBarYFirst;
      }
    }

    // Validate final cricondenbar and cricondentherm
    if (!Double.isFinite(cricondenBar[0]) || !Double.isFinite(cricondenBar[1])
        || (cricondenBar[0] == 0.0 && cricondenBar[1] == 0.0)) {
      cricondenBar[0] = initialTemp;
      cricondenBar[1] = initialPres;
    }
    if (!Double.isFinite(cricondenTherm[0]) || !Double.isFinite(cricondenTherm[1])
        || (cricondenTherm[0] == 0.0 && cricondenTherm[1] == 0.0)) {
      cricondenTherm[0] = initialTemp;
      cricondenTherm[1] = initialPres;
    }

    // Convert ArrayLists to output arrays
    buildOutputArrays();
  }

  /**
   * Check whether the phase envelope is closed, meaning both dew and bubble branches have been
   * successfully traced with at least 3 points each.
   *
   * @return true if both branches have at least 3 points
   */
  public boolean isEnvelopeClosed() {
    return dewTempArray.length >= 3 && bubTempArray.length >= 3;
  }

  /**
   * Trace quality lines (constant vapor fraction curves) inside the two-phase region. Each quality
   * line is traced using the same continuation method as the phase boundary, but with a fixed molar
   * vapor fraction beta. At each point, the volume fraction and mass fraction are also computed.
   *
   * <p>
   * Results are stored internally and accessible via {@link #get(String)} with keys:
   * </p>
   * <ul>
   * <li>{@code "qualityT_X"} - temperatures (K) along quality line at molar fraction X</li>
   * <li>{@code "qualityP_X"} - pressures (bara) along quality line at molar fraction X</li>
   * <li>{@code "qualityVolFrac_X"} - volume vapor fractions along the line</li>
   * <li>{@code "qualityMassFrac_X"} - mass vapor fractions along the line</li>
   * </ul>
   *
   * @param betaValues array of molar vapor fractions to trace (between 0 and 1 exclusive)
   */
  public void calcQualityLines(double[] betaValues) {
    this.qualityBetaValues = betaValues.clone();
    double maxEnvelopeP = Math.max(cricondenBar[1], 1.0);

    for (double beta : betaValues) {
      if (beta <= 0.0 || beta >= 1.0) {
        continue;
      }

      SystemInterface clonedSystem = system.clone();

      // Estimate initial temperature at low pressure for this beta
      double temp = tempKWilsonForSystem(clonedSystem, beta, lowPres);
      if (Double.isNaN(temp)) {
        continue;
      }
      clonedSystem.setTemperature(temp);
      clonedSystem.setPressure(lowPres);

      // Converge first point using saturation flash
      ThermodynamicOperations flashOps = new ThermodynamicOperations(clonedSystem);
      boolean converged = false;
      for (int attempt = 0; attempt < FIRST_POINT_ATTEMPTS; attempt++) {
        try {
          double tempAttempt = temp + attempt * FIRST_POINT_STEP;
          clonedSystem.setTemperature(tempAttempt);
          if (beta < 0.5) {
            flashOps.bubblePointTemperatureFlash();
          } else {
            flashOps.dewPointTemperatureFlash();
          }
        } catch (Exception ex) {
          continue;
        }
        double tempNy = clonedSystem.getTemperature();
        if (!Double.isNaN(tempNy)) {
          temp = tempNy;
          converged = true;
          break;
        }
      }
      if (!converged) {
        logger.debug("Could not converge first quality line point for beta={}", beta);
        continue;
      }

      clonedSystem.setBeta(beta);
      clonedSystem.setPressure(lowPres);
      clonedSystem.setTemperature(temp);

      SysNewtonRhapsonPhaseEnvelope solver = new SysNewtonRhapsonPhaseEnvelope(clonedSystem, 2,
          clonedSystem.getPhase(0).getNumberOfComponents());
      solver.dTmax = this.dTmax;
      solver.dPmax = this.dPmax;
      solver.setu();

      ArrayList<Double> qTemps = new ArrayList<Double>();
      ArrayList<Double> qPress = new ArrayList<Double>();
      ArrayList<Double> qVolFracs = new ArrayList<Double>();
      ArrayList<Double> qMassFracs = new ArrayList<Double>();

      boolean pastPeak = false;
      double peakP = 0.0;

      for (int np = 1; np < MAX_QUALITY_LINE_POINTS; np++) {
        try {
          solver.calcInc(np);
          solver.solve(np);
        } catch (Exception e) {
          break;
        }

        double currentT = clonedSystem.getTemperature();
        double currentP = clonedSystem.getPressure();

        if (Double.isNaN(currentT) || Double.isNaN(currentP) || currentT < 1e-6
            || currentP < 1e-6) {
          break;
        }

        // Track pressure peak for exit criteria
        if (currentP > peakP) {
          peakP = currentP;
        } else {
          pastPeak = true;
        }

        // Exit if pressure exceeds envelope maximum
        if (currentP > maxEnvelopeP * 1.05) {
          break;
        }
        // Exit if pressure drops below minimum after passing peak
        if (pastPeak && currentP < minPressure) {
          break;
        }

        qTemps.add(currentT);
        qPress.add(currentP);

        // Compute volume fraction: betaV = beta * Vm_vap / (beta * Vm_vap + (1-beta) * Vm_liq)
        double mwVap = clonedSystem.getPhase(0).getMolarMass();
        double mwLiq = clonedSystem.getPhase(1).getMolarMass();
        double densVap = clonedSystem.getPhase(0).getDensity();
        double densLiq = clonedSystem.getPhase(1).getDensity();

        double volFrac = 0.5;
        if (densVap > 1e-10 && densLiq > 1e-10) {
          double vmVap = mwVap / densVap;
          double vmLiq = mwLiq / densLiq;
          volFrac = beta * vmVap / (beta * vmVap + (1.0 - beta) * vmLiq);
        }
        qVolFracs.add(volFrac);

        // Compute mass fraction: betaW = beta * Mw_vap / (beta * Mw_vap + (1-beta) * Mw_liq)
        double massFrac = beta * mwVap / (beta * mwVap + (1.0 - beta) * mwLiq);
        qMassFracs.add(massFrac);
      }

      if (!qTemps.isEmpty()) {
        String betaKey = formatBetaKey(beta);
        qualityLineData.put("qualityT_" + betaKey, toDoubleArray(qTemps));
        qualityLineData.put("qualityP_" + betaKey, toDoubleArray(qPress));
        qualityLineData.put("qualityVolFrac_" + betaKey, toDoubleArray(qVolFracs));
        qualityLineData.put("qualityMassFrac_" + betaKey, toDoubleArray(qMassFracs));
      }
    }
  }

  /**
   * Format a beta value as a clean string key (e.g. 0.5 becomes "0.5", 0.25 becomes "0.25").
   *
   * @param beta the molar vapor fraction
   * @return formatted string key
   */
  private String formatBetaKey(double beta) {
    if (beta == (int) beta) {
      return String.valueOf((int) beta);
    }
    String s = String.valueOf(beta);
    // Remove trailing zeros but keep at least one decimal
    while (s.endsWith("0") && !s.endsWith(".0")) {
      s = s.substring(0, s.length() - 1);
    }
    return s;
  }

  /**
   * Estimate the initial temperature using Wilson correlation for a given system.
   *
   * @param sys the thermodynamic system to use
   * @param beta overall vapor fraction
   * @param pressure pressure in bara
   * @return estimated temperature in Kelvin
   */
  private double tempKWilsonForSystem(SystemInterface sys, double beta, double pressure) {
    int numberOfComponents = sys.getPhase(0).getNumberOfComponents();
    int lcIdx = 0;
    int hcIdx = 0;
    double minTc = 1e10;
    double maxTc = 0;

    for (int i = 0; i < numberOfComponents; i++) {
      if (sys.getPhase(0).getComponent(i).getTC() > maxTc) {
        maxTc = sys.getPhase(0).getComponent(i).getTC();
        hcIdx = i;
      }
      if (sys.getPhase(0).getComponent(i).getTC() < minTc) {
        minTc = sys.getPhase(0).getComponent(i).getTC();
        lcIdx = i;
      }
    }

    int refIdx = beta <= 0.5 ? lcIdx : hcIdx;
    double refTc = sys.getPhase(0).getComponent(refIdx).getTC();
    double refPc = sys.getPhase(0).getComponent(refIdx).getPC();
    double refAc = sys.getPhase(0).getComponent(refIdx).getAcentricFactor();

    double lnPr = Math.log(pressure / refPc);
    double tEst = refTc * WILSON_CONST * (1 + refAc) / (WILSON_CONST * (1 + refAc) - lnPr);
    double tOld = 0;

    try {
      double[] kw = new double[numberOfComponents];
      for (int iter = 0; iter < MAX_WILSON_ITERATIONS; iter++) {
        double f = 0;
        double df = 0;
        for (int j = 0; j < numberOfComponents; j++) {
          kw[j] = sys.getPhase(0).getComponent(j).getPC() / pressure
              * Math.exp(WILSON_CONST * (1.0 + sys.getPhase(0).getComponent(j).getAcentricFactor())
                  * (1.0 - sys.getPhase(0).getComponent(j).getTC() / tEst));
        }
        for (int j = 0; j < numberOfComponents; j++) {
          double zj = sys.getPhase(0).getComponent(j).getz();
          double tc = sys.getPhase(0).getComponent(j).getTC();
          double ac = sys.getPhase(0).getComponent(j).getAcentricFactor();
          if (beta < 0.5) {
            f += zj * kw[j];
            df += zj * kw[j] * WILSON_CONST * (1 + ac) * tc / (tEst * tEst);
          } else {
            f += zj / kw[j];
            df -= zj / kw[j] * WILSON_CONST * (1 + ac) * tc / (tEst * tEst);
          }
        }
        f -= 1.0;
        if (Math.abs(f / df) > 0.1 * tEst) {
          tEst -= 0.001 * f / df;
        } else {
          tEst -= f / df;
        }
        if (Math.abs(tEst - tOld) < 1e-5) {
          return tEst;
        }
        tOld = tEst;
      }
    } catch (Exception ex) {
      lnPr = Math.log(pressure / refPc);
      tEst = refTc * WILSON_CONST * (1 + refAc) / (WILSON_CONST * (1 + refAc) - lnPr);
    }

    if (Double.isNaN(tEst) || Double.isInfinite(tEst)) {
      tEst = refTc * WILSON_CONST * (1 + refAc)
          / (WILSON_CONST * (1 + refAc) - Math.log(pressure / refPc));
    }
    return tEst;
  }

  /**
   * Get the list of quality line beta values that were traced.
   *
   * @return array of molar vapor fraction values
   */
  public double[] getQualityBetaValues() {
    return qualityBetaValues;
  }

  /**
   * Get data for a specific quality line.
   *
   * @param beta the molar vapor fraction of the quality line
   * @return array of [temperatures(K), pressures(bara), volumeFractions, massFractions], or null if
   *         not traced
   */
  public double[][] getQualityLine(double beta) {
    String key = formatBetaKey(beta);
    double[] qT = qualityLineData.get("qualityT_" + key);
    double[] qP = qualityLineData.get("qualityP_" + key);
    double[] qV = qualityLineData.get("qualityVolFrac_" + key);
    double[] qM = qualityLineData.get("qualityMassFrac_" + key);
    if (qT == null) {
      return null;
    }
    return new double[][] {qT, qP, qV, qM};
  }

  /**
   * Reset K-values on the system using the Wilson correlation at the configured low pressure.
   * Called before the restart pass to ensure fresh initial estimates after a crash.
   */
  private void resetKValuesWithWilson() {
    double restartTemp = tempKWilson(phaseFraction, lowPres);
    if (Double.isNaN(restartTemp)) {
      restartTemp = system.getPhase(0).getComponent(speceq).getTC() - 20.0;
    }
    for (int ic = 0; ic < system.getPhase(0).getNumberOfComponents(); ic++) {
      double Kwil = system.getPhase(0).getComponent(ic).getPC() / lowPres
          * Math.exp(WILSON_CONST * (1.0 + system.getPhase(0).getComponent(ic).getAcentricFactor())
              * (1.0 - system.getPhase(0).getComponent(ic).getTC() / restartTemp));
      system.getPhase(0).getComponent(ic).setK(Kwil);
      system.getPhase(1).getComponent(ic).setK(Kwil);
    }
  }

  /**
   * Convert all ArrayList results to primitive arrays for efficient access via get().
   */
  private void buildOutputArrays() {
    dewTempArray = toDoubleArray(dewPointTemperatures);
    dewPresArray = toDoubleArray(dewPointPressures);
    dewEnthalpyArray = toDoubleArray(dewPointEnthalpies);
    dewDensityArray = toDoubleArray(dewPointDensities);
    dewEntropyArray = toDoubleArray(dewPointEntropies);

    bubTempArray = toDoubleArray(bubblePointTemperatures);
    bubPresArray = toDoubleArray(bubblePointPressures);
    bubEnthalpyArray = toDoubleArray(bubblePointEnthalpies);
    bubDensityArray = toDoubleArray(bubblePointDensities);
    bubEntropyArray = toDoubleArray(bubblePointEntropies);
  }

  /**
   * Convert an ArrayList of Double to a primitive double array.
   *
   * @param list the list to convert
   * @return primitive double array
   */
  private double[] toDoubleArray(ArrayList<Double> list) {
    double[] result = new double[list.size()];
    for (int i = 0; i < list.size(); i++) {
      result[i] = list.get(i).doubleValue();
    }
    return result;
  }

  /**
   * Estimate the initial temperature using the Wilson correlation.
   *
   * <p>
   * Iteratively solves for the temperature at which the Wilson K-value correlation satisfies the
   * Rachford-Rice equation for the given phase fraction and pressure.
   * </p>
   *
   * @param beta overall vapor fraction
   * @param P pressure in bara
   * @return estimated temperature in Kelvin
   */
  private double tempKWilson(double beta, double P) {
    int numberOfComponents = system.getPhase(0).getNumberOfComponents();
    int lc = 0;
    int hc = 0;
    double min = 1e10;
    double max = 0;

    for (int i = 0; i < numberOfComponents; i++) {
      if (system.getPhase(0).getComponent(i).getTC() > max) {
        max = system.getPhase(0).getComponent(i).getTC();
        hc = i;
      }
      if (system.getPhase(0).getComponent(i).getTC() < min) {
        min = system.getPhase(0).getComponent(i).getTC();
        lc = i;
      }
    }

    double initTc;
    double initPc;
    double initAc;
    if (beta <= 0.5) {
      initTc = system.getPhase(0).getComponent(lc).getTC();
      initPc = system.getPhase(0).getComponent(lc).getPC();
      initAc = system.getPhase(0).getComponent(lc).getAcentricFactor();
    } else {
      initTc = system.getPhase(0).getComponent(hc).getTC();
      initPc = system.getPhase(0).getComponent(hc).getPC();
      initAc = system.getPhase(0).getComponent(hc).getAcentricFactor();
    }

    double lnPratio = Math.log(P / initPc);
    double Tstart = initTc * WILSON_CONST * (1 + initAc) / (WILSON_CONST * (1 + initAc) - lnPratio);
    double Tstartold = 0;

    try {
      double[] Kwil = new double[numberOfComponents];
      for (int i = 0; i < MAX_WILSON_ITERATIONS; i++) {
        double initT = 0;
        double dinitT = 0;
        for (int j = 0; j < numberOfComponents; j++) {
          Kwil[j] = system.getPhase(0).getComponent(j).getPC() / P
              * Math
                  .exp(WILSON_CONST * (1.0 + system.getPhase(0).getComponent(j).getAcentricFactor())
                      * (1.0 - system.getPhase(0).getComponent(j).getTC() / Tstart));
        }

        for (int j = 0; j < numberOfComponents; j++) {
          if (beta < 0.5) {
            initT += system.getPhase(0).getComponent(j).getz() * Kwil[j];
            dinitT += system.getPhase(0).getComponent(j).getz() * Kwil[j] * WILSON_CONST
                * (1 + system.getPhase(0).getComponent(j).getAcentricFactor())
                * system.getPhase(0).getComponent(j).getTC() / (Tstart * Tstart);
          } else {
            initT += system.getPhase(0).getComponent(j).getz() / Kwil[j];
            dinitT -= system.getPhase(0).getComponent(j).getz() / Kwil[j] * WILSON_CONST
                * (1 + system.getPhase(0).getComponent(j).getAcentricFactor())
                * system.getPhase(0).getComponent(j).getTC() / (Tstart * Tstart);
          }
        }

        initT -= 1.0;
        if (Math.abs(initT / dinitT) > 0.1 * Tstart) {
          Tstart -= 0.001 * initT / dinitT;
        } else {
          Tstart -= initT / dinitT;
        }
        if (Math.abs(Tstart - Tstartold) < 1e-5) {
          return Tstart;
        }
        Tstartold = Tstart;
      }
    } catch (Exception ex) {
      lnPratio = Math.log(P / initPc);
      Tstart = initTc * WILSON_CONST * (1 + initAc) / (WILSON_CONST * (1 + initAc) - lnPratio);
    }

    if (Double.isNaN(Tstart) || Double.isInfinite(Tstart)) {
      lnPratio = Math.log(P / initPc);
      Tstart = initTc * WILSON_CONST * (1 + initAc) / (WILSON_CONST * (1 + initAc) - lnPratio);
    }
    return Tstart;
  }

  // ==================== Configuration setters ====================

  /**
   * Set the maximum pressure limit for the phase envelope.
   *
   * @param maxPressure maximum pressure in bara
   */
  public void setMaxPressure(double maxPressure) {
    this.maxPressure = maxPressure;
  }

  /**
   * Set the minimum pressure limit for the phase envelope.
   *
   * @param minPressure minimum pressure in bara
   */
  public void setMinPressure(double minPressure) {
    this.minPressure = minPressure;
  }

  /**
   * Set the maximum temperature step per iteration.
   *
   * @param dTmax max temperature change in K per step
   */
  public void setDTmax(double dTmax) {
    this.dTmax = dTmax;
  }

  /**
   * Set the maximum pressure step per iteration.
   *
   * @param dPmax max pressure change in bar per step
   */
  public void setDPmax(double dPmax) {
    this.dPmax = dPmax;
  }

  // ==================== Result getters ====================

  /**
   * Get the dew point temperatures in Kelvin.
   *
   * @return array of dew point temperatures
   */
  public double[] getDewPointTemperatures() {
    return dewTempArray;
  }

  /**
   * Get the dew point pressures in bara.
   *
   * @return array of dew point pressures
   */
  public double[] getDewPointPressures() {
    return dewPresArray;
  }

  /**
   * Get the bubble point temperatures in Kelvin.
   *
   * @return array of bubble point temperatures
   */
  public double[] getBubblePointTemperatures() {
    return bubTempArray;
  }

  /**
   * Get the bubble point pressures in bara.
   *
   * @return array of bubble point pressures
   */
  public double[] getBubblePointPressures() {
    return bubPresArray;
  }

  /**
   * Get the cricondentherm values.
   *
   * @return array [T(K), P(bara), 0] at max temperature on envelope
   */
  public double[] getCricondenTherm() {
    return cricondenTherm;
  }

  /**
   * Get the cricondenbar values.
   *
   * @return array [T(K), P(bara), 0] at max pressure on envelope
   */
  public double[] getCricondenBar() {
    return cricondenBar;
  }

  /**
   * Get the critical point temperature.
   *
   * @return critical temperature in Kelvin
   */
  public double getCriticalTemperature() {
    return system.getTC();
  }

  /**
   * Get the critical point pressure.
   *
   * @return critical pressure in bara
   */
  public double getCriticalPressure() {
    return system.getPC();
  }

  // ==================== OperationInterface implementation ====================

  /** {@inheritDoc} */
  @Override
  public void displayResult() {
    // No GUI display - use get() methods to retrieve data
  }

  /** {@inheritDoc} */
  @Override
  public double[][] getPoints(int i) {
    return new double[][] {dewTempArray, dewPresArray, bubTempArray, bubPresArray};
  }

  /** {@inheritDoc} */
  @Override
  public double[] get(String name) {
    if (name.equals("dewT")) {
      return dewTempArray;
    }
    if (name.equals("dewP")) {
      return dewPresArray;
    }
    if (name.equals("bubT")) {
      return bubTempArray;
    }
    if (name.equals("bubP")) {
      return bubPresArray;
    }
    if (name.equals("dewH")) {
      return dewEnthalpyArray;
    }
    if (name.equals("dewDens")) {
      return dewDensityArray;
    }
    if (name.equals("dewS")) {
      return dewEntropyArray;
    }
    if (name.equals("bubH")) {
      return bubEnthalpyArray;
    }
    if (name.equals("bubDens")) {
      return bubDensityArray;
    }
    if (name.equals("bubS")) {
      return bubEntropyArray;
    }
    if (name.equals("cricondentherm")) {
      return cricondenTherm;
    }
    if (name.equals("cricondenthermX")) {
      return cricondenThermX;
    }
    if (name.equals("cricondenthermY")) {
      return cricondenThermY;
    }
    if (name.equals("cricondenbar")) {
      return cricondenBar;
    }
    if (name.equals("cricondenbarX")) {
      return cricondenBarX;
    }
    if (name.equals("cricondenbarY")) {
      return cricondenBarY;
    }
    if (name.equals("dewT2") || name.equals("dewP2") || name.equals("bubT2")
        || name.equals("bubP2")) {
      // Return null to match legacy PTphaseEnvelope behavior when no second pass exists.
      // The Michelsen method merges all points into dewT/dewP/bubT/bubP, so separate
      // "pass 2" arrays are not applicable. Returning null ensures downstream consumers
      // (e.g., NeqSimAPI's SPhaseopt.calculate_cricondenbar) correctly fall back to dewP.
      return null;
    }
    if (name.equals("criticalPoint1")) {
      return new double[] {system.getTC(), system.getPC()};
    }
    if (name.equals("criticalPoint2")) {
      return new double[] {0, 0};
    }
    // Quality line keys: qualityT_X, qualityP_X, qualityVolFrac_X, qualityMassFrac_X
    if (name.startsWith("quality")) {
      return qualityLineData.get(name);
    }
    return null;
  }

  /** {@inheritDoc} */
  @Override
  public String[][] getResultTable() {
    return null;
  }

  /** {@inheritDoc} */
  @Override
  public void printToFile(String name) {}
}
