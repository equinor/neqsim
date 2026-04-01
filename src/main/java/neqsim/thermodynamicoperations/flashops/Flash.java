/*
 * Flash.java
 *
 * Created on 2. oktober 2000, 22:22
 */

package neqsim.thermodynamicoperations.flashops;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import Jama.Matrix;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.BaseOperation;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * Abstract base class for all flash classes.
 *
 * @author Even Solbraa
 */
public abstract class Flash extends BaseOperation {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(Flash.class);

  SystemInterface system;
  SystemInterface minimumGibbsEnergySystem;

  public double[] minGibsPhaseLogZ;
  public double[] minGibsLogFugCoef;
  int i = 0;
  int j = 0;
  int iterations = 0;
  int maxNumberOfIterations = 50;
  double gibbsEnergy = 0;
  double gibbsEnergyOld = 0;
  double Kold = 0;
  double deviation = 0;
  double g0 = 0;
  double g1 = 0;
  double[] lnOldOldOldK;
  double[] lnOldOldK;
  double[] lnK;
  double[] lnOldK;
  double[] oldoldDeltalnK;
  double[] oldDeltalnK;
  double[] deltalnK;
  double[] tm;
  double tmLimit = -1e-8;
  private static final double LOG_MIN_EXP = Math.log(Double.MIN_NORMAL);
  private static final double LOG_MAX_EXP = Math.log(Double.MAX_VALUE);

  protected double safeExp(double value) {
    if (Double.isNaN(value)) {
      return Double.NaN;
    }
    if (value < LOG_MIN_EXP) {
      return Double.MIN_NORMAL;
    }
    if (value > LOG_MAX_EXP) {
      return Double.MAX_VALUE;
    }
    return Math.exp(value);
  }

  int lowestGibbsEnergyPhase = 0;
  SysNewtonRhapsonTPflash secondOrderSolver;
  /** Set true to do solid phase check and calculations */
  protected boolean solidCheck = false;
  protected boolean stabilityCheck = false;
  protected boolean findLowestGibbsPhaseIsChecked = false;

  /**
   * <p>
   * findLowestGibbsEnergyPhase.
   * </p>
   *
   * @return a int
   */
  public int findLowestGibbsEnergyPhase() {
    if (!findLowestGibbsPhaseIsChecked) {
      minimumGibbsEnergySystem = system.clone();
      minimumGibbsEnergySystem.init(0);
      if (minimumGibbsEnergySystem.getTotalNumberOfMoles() < 1e-20) {
        minimumGibbsEnergySystem.setTotalNumberOfMoles(1.0);
      }
      minimumGibbsEnergySystem.init(1);
      if ((minimumGibbsEnergySystem.getPhase(0).getGibbsEnergy()
          * (1.0 - Math.signum(minimumGibbsEnergySystem.getPhase(0).getGibbsEnergy())
              * 1e-8)) < minimumGibbsEnergySystem.getPhase(1).getGibbsEnergy()) {
        lowestGibbsEnergyPhase = 0;
      } else {
        lowestGibbsEnergyPhase = 1;
      }
      findLowestGibbsPhaseIsChecked = true;
    }
    return lowestGibbsEnergyPhase;
  }

  /**
   * Retry stability analysis with amplified Wilson K-values using an independent system clone.
   * Called when the standard stability analysis declares the system stable, to catch near-critical
   * instability (e.g. near the cricondenbar where Wilson K values approach 1 and standard trials
   * converge to trivial solutions).
   *
   * <p>
   * Only runs when Wilson K-values indicate near-critical conditions (trial sums close to 1.0),
   * avoiding false positives in clearly non-critical systems.
   *
   * @return true if instability was found (K-values on system are updated)
   */
  private boolean amplifiedKStabilityRetry() {
    int numComp = system.getPhase(0).getNumberOfComponents();
    double tempK = system.getTemperature();
    double presBar = system.getPressure();

    // Detect trivial solution: standard analysis set tm ≈ 0 instead of finding
    // a meaningful stationary point. Bypass all pre-screening gates in this case.
    // Exception: nearly-pure systems (max z > 0.999) legitimately have tm ≈ 0
    // because a single dominant component is genuinely stable — not a trivial
    // convergence failure — so skip the bypass for those systems.
    double maxZ = 0.0;
    for (int i = 0; i < numComp; i++) {
      double zi = system.getPhase(0).getComponent(i).getz();
      if (zi > maxZ) {
        maxZ = zi;
      }
    }
    boolean nearlyPure = maxZ > 0.999;
    boolean trivialSolution =
        !nearlyPure && ((Math.abs(tm[0]) < 1e-12) || (Math.abs(tm[1]) < 1e-12));

    double sumwVapor = 0.0;
    double sumwLiquid = 0.0;
    double[] wilsonK = new double[numComp];
    for (int i = 0; i < numComp; i++) {
      double zi = system.getPhase(0).getComponent(i).getz();
      if (zi < 1e-100 || system.getPhase(0).getComponent(i).getIonicCharge() != 0) {
        wilsonK[i] = 1.0;
        continue;
      }
      double tc = system.getPhase(0).getComponent(i).getTC();
      double pc = system.getPhase(0).getComponent(i).getPC();
      double omega = system.getPhase(0).getComponent(i).getAcentricFactor();
      wilsonK[i] = (pc / presBar) * Math.exp(5.373 * (1.0 + omega) * (1.0 - tc / tempK));
      sumwVapor += zi * wilsonK[i];
      if (wilsonK[i] > 1e-30) {
        sumwLiquid += zi / wilsonK[i];
      }
    }
    // Only retry when Wilson K sums indicate proximity to a phase boundary.
    // At the bubble point sum(z*K)=1; at the dew point sum(z/K)=1.
    // Use range [0.7, 2.4] to catch near-cricondenbar (sumw ≈ 0.7-1.8)
    // while excluding supercritical single-component systems (e.g. CO2 at
    // 100 bar, 298K gives sumwVapor ≈ 0.65) and well-separated phases.
    boolean nearBubblePoint = (sumwVapor > 0.7 && sumwVapor < 2.4);
    boolean nearDewPoint = (sumwLiquid > 0.7 && sumwLiquid < 2.4);
    // Also retry when standard stability analysis produced marginal tm values
    // (|tm| < 0.1). This catches cases where Wilson K sums are outside [0.7, 2.4]
    // due to highly asymmetric K-values (e.g. methane/n-heptane) but the stability
    // result is genuinely uncertain. Wilson K is only an approximation — the actual
    // EOS K-values can differ significantly for asymmetric systems.
    boolean tmNearZero = (Math.abs(tm[0]) < 0.1 || Math.abs(tm[1]) < 0.1);
    if (!trivialSolution && !nearBubblePoint && !nearDewPoint && !tmNearZero) {
      return false;
    }
    // Skip retry for near-pure component systems: for a single component,
    // sumwVapor * sumwLiquid = K * (1/K) = 1. For multicomponent mixtures
    // near a phase boundary, this product is >> 1 due to K-value spread.
    // A threshold of 2.0 reliably discriminates the two cases.
    // However, when Wilson K ≈ 1 for all components (very near-critical),
    // sumwVapor * sumwLiquid can drop below 2.0 even for multicomponent
    // mixtures. Detect this and skip the filter when K-values show no spread.
    double maxLnK = 0.0;
    for (int i = 0; i < numComp; i++) {
      double zi = system.getPhase(0).getComponent(i).getz();
      if (zi > 1e-100 && system.getPhase(0).getComponent(i).getIonicCharge() == 0) {
        maxLnK = Math.max(maxLnK, Math.abs(Math.log(wilsonK[i])));
      }
    }
    boolean wilsonKNearUnity = maxLnK < 0.5;
    if (sumwVapor * sumwLiquid < 2.0 && !wilsonKNearUnity) {
      return false;
    }

    // Compute reference chemical potentials (d vector)
    double[] d = new double[numComp];
    for (int i = 0; i < numComp; i++) {
      d[i] = minGibsPhaseLogZ[i] + minGibsLogFugCoef[i];
    }

    // Reuse the existing clone from findLowestGibbsEnergyPhase() instead of creating
    // a second clone. stabilityAnalysis() has already run on this system, but
    // amplifiedKStabilityRetry sets its own trial compositions and re-initializes,
    // so the prior state doesn't matter. The calling code (stabilityCheck) always
    // reinitializes the original system after this method returns.
    SystemInterface testSystem = minimumGibbsEnergySystem;

    // Determine if we need composition-perturbation trials.
    // When Wilson K ≈ 1 for all components (near-critical), simple amplification
    // of ln(K) ≈ 0 produces trivial initial guesses. In this case, use
    // heavy/light component weighting to generate non-trivial trial compositions.
    boolean useCompositionPerturbation = wilsonKNearUnity || trivialSolution;

    // Number of trials: 2 standard (amplified K vapor/liquid) + 2 perturbation
    // (heavy-enriched / light-enriched) when near-critical or trivial solution
    // + 1 near-pure heaviest component trial for dew-point detection
    int numTrials = useCompositionPerturbation ? 5 : 2;

    for (int trial = 0; trial < numTrials; trial++) {
      double[] Wi = new double[numComp];
      double[] logWi = new double[numComp];
      double[] oldlogw = new double[numComp];

      double sumwTrial = 0.0;

      if (trial < 2) {
        // Standard amplified Wilson K-value trials
        for (int i = 0; i < numComp; i++) {
          double zi = testSystem.getPhase(0).getComponent(i).getz();
          if (zi < 1e-100 || testSystem.getPhase(0).getComponent(i).getIonicCharge() != 0) {
            Wi[i] = 1e-50;
            logWi[i] = Math.log(Wi[i]);
            oldlogw[i] = logWi[i];
            sumwTrial += Wi[i];
            continue;
          }
          double tc = testSystem.getPhase(0).getComponent(i).getTC();
          double pc = testSystem.getPhase(0).getComponent(i).getPC();
          double omega = testSystem.getPhase(0).getComponent(i).getAcentricFactor();
          double lnKwilson = Math.log(pc / presBar) + 5.373 * (1.0 + omega) * (1.0 - tc / tempK);
          // Use stronger amplification factor (4.0 instead of 2.5) when near-critical
          double ampFactor = useCompositionPerturbation ? 4.0 : 2.5;
          double amplifiedLnK = ampFactor * lnKwilson;

          if (trial == 0) {
            Wi[i] = zi * Math.exp(amplifiedLnK);
          } else {
            Wi[i] = zi * Math.exp(-amplifiedLnK);
          }
          logWi[i] = Math.log(Wi[i]);
          oldlogw[i] = logWi[i];
          sumwTrial += Wi[i];
        }
      } else if (trial <= 3) {
        // Composition-perturbation trials for near-critical systems.
        // Trial 2: heavy-enriched (weight by Tc — higher Tc components get more)
        // Trial 3: light-enriched (weight by 1/Tc — lower Tc components get more)
        double maxTc = 0.0;
        for (int i = 0; i < numComp; i++) {
          double tc = testSystem.getPhase(0).getComponent(i).getTC();
          if (tc > maxTc) {
            maxTc = tc;
          }
        }
        if (maxTc < 1.0) {
          maxTc = 1.0;
        }

        for (int i = 0; i < numComp; i++) {
          double zi = testSystem.getPhase(0).getComponent(i).getz();
          if (zi < 1e-100 || testSystem.getPhase(0).getComponent(i).getIonicCharge() != 0) {
            Wi[i] = 1e-50;
            logWi[i] = Math.log(Wi[i]);
            oldlogw[i] = logWi[i];
            sumwTrial += Wi[i];
            continue;
          }
          double tc = testSystem.getPhase(0).getComponent(i).getTC();
          double tcNorm = tc / maxTc;
          if (trial == 2) {
            // Heavy-enriched: boost components with high Tc (liquid-like)
            Wi[i] = zi * Math.pow(tcNorm, 3.0);
          } else {
            // Light-enriched: boost components with low Tc (vapor-like)
            Wi[i] = zi * Math.pow(1.0 / (tcNorm + 0.01), 3.0);
          }
          logWi[i] = Math.log(Wi[i]);
          oldlogw[i] = logWi[i];
          sumwTrial += Wi[i];
        }
      } else {
        // Trial 4: near-pure heaviest component trial for dew-point detection.
        int heaviestIdx = 0;
        double heaviestTc = 0.0;
        for (int i = 0; i < numComp; i++) {
          double zi = testSystem.getPhase(0).getComponent(i).getz();
          if (zi < 1e-100) {
            continue;
          }
          double tc = testSystem.getPhase(0).getComponent(i).getTC();
          if (tc > heaviestTc) {
            heaviestTc = tc;
            heaviestIdx = i;
          }
        }
        for (int i = 0; i < numComp; i++) {
          if (testSystem.getPhase(0).getComponent(i).getz() < 1e-100
              || testSystem.getPhase(0).getComponent(i).getIonicCharge() != 0) {
            Wi[i] = 1e-50;
          } else if (i == heaviestIdx) {
            Wi[i] = 1.0;
          } else {
            Wi[i] = 1e-6;
          }
          logWi[i] = Math.log(Wi[i]);
          oldlogw[i] = logWi[i];
          sumwTrial += Wi[i];
        }
      }

      // Normalize and set trial composition on phase 1 of the test system
      for (int i = 0; i < numComp; i++) {
        testSystem.getPhase(1).getComponent(i).setx(Wi[i] / sumwTrial);
      }

      // Successive substitution iteration with DEM acceleration
      int maxiter = 100;
      int accelInterval = 5;
      double err;
      Matrix f = new Matrix(numComp, 1);
      double fNorm = 1e10;
      double fNormOld;
      boolean converged = false;
      double[] prevDelta = new double[numComp];
      for (int iter = 1; iter <= maxiter; iter++) {
        err = 0.0;
        System.arraycopy(logWi, 0, oldlogw, 0, numComp);

        testSystem.init(1, 1);
        fNormOld = fNorm;
        for (int i = 0; i < numComp; i++) {
          f.set(i, 0, Math.sqrt(Wi[i]) * (Math.log(Wi[i])
              + testSystem.getPhase(1).getComponent(i).getLogFugacityCoefficient() - d[i]));
        }
        fNorm = f.norm2();
        if (fNorm > fNormOld && iter > 3) {
          if (iter > 10) {
            break;
          }
        }

        // Standard successive substitution step
        for (int i = 0; i < numComp; i++) {
          logWi[i] = d[i] - testSystem.getPhase(1).getComponent(i).getLogFugacityCoefficient();
          Wi[i] = safeExp(logWi[i]);
        }

        double[] curDelta = new double[numComp];
        for (int i = 0; i < numComp; i++) {
          curDelta[i] = logWi[i] - oldlogw[i];
        }

        // DEM acceleration every accelInterval steps
        if (iter % accelInterval == 0 && fNorm < fNormOld && iter > accelInterval) {
          double dot1 = 0.0;
          double dot2 = 0.0;
          for (int i = 0; i < numComp; i++) {
            dot1 += curDelta[i] * prevDelta[i];
            dot2 += prevDelta[i] * prevDelta[i];
          }
          if (dot2 > 1e-20) {
            double lambda = dot1 / dot2;
            if (lambda > 0.0 && lambda < 1.0) {
              double accelFactor = lambda / (1.0 - lambda);
              for (int i = 0; i < numComp; i++) {
                logWi[i] += accelFactor * curDelta[i];
                Wi[i] = safeExp(logWi[i]);
              }
            }
          }
        }

        // Track SSI step delta for next acceleration.
        // Use the raw curDelta history term, not the accelerated correction.
        for (int i = 0; i < numComp; i++) {
          prevDelta[i] = curDelta[i];
          err += Math.abs(curDelta[i]);
        }

        sumwTrial = 0.0;
        for (int i = 0; i < numComp; i++) {
          sumwTrial += Wi[i];
        }
        for (int i = 0; i < numComp; i++) {
          testSystem.getPhase(1).getComponent(i).setx(Wi[i] / sumwTrial);
        }

        if (f.norm1() < 1e-6 && err < 1e-6) {
          converged = true;
          break;
        }

        // Early termination: if sum(Wi) is well below 1 after enough iterations,
        // the system is clearly stable — no phase split. Skip remaining iterations.
        if (iter > 5 && sumwTrial < 0.8) {
          break;
        }
      }

      // Compute tm = 1 - sum(Wi)
      double tmVal = 1.0;
      for (int i = 0; i < numComp; i++) {
        tmVal -= Wi[i];
      }

      // If first standard trial converged to clearly stable (tmVal > 0.1),
      // skip the second standard trial — both directions agree on stability.
      if (trial == 0 && converged && tmVal > 0.1) {
        // Jump to perturbation trials if near-critical, otherwise done
        if (useCompositionPerturbation) {
          trial = 1; // Will increment to 2 on next loop iteration
          continue;
        } else {
          break;
        }
      }

      // Only accept instability if SS converged and tm is clearly negative.
      // Non-converged results are unreliable and may give spurious instability.
      double tmThreshold = -1e-4;
      if (converged && tmVal < tmThreshold) {
        // Verify non-trivial: trial composition different from feed
        double dot = 0.0;
        double nW = 0.0;
        double nF = 0.0;
        for (int i = 0; i < numComp; i++) {
          double xT = testSystem.getPhase(1).getComponent(i).getx();
          double xF = testSystem.getPhase(0).getComponent(i).getz();
          dot += xT * xF;
          nW += xT * xT;
          nF += xF * xF;
        }
        double cos = dot / (Math.sqrt(nW) * Math.sqrt(nF) + 1e-100);
        if (cos < 0.9999) {
          // Found genuine instability — set K-values on the system
          for (int i = 0; i < numComp; i++) {
            double xTrial = testSystem.getPhase(1).getComponent(i).getx();
            double zFeed = testSystem.getPhase(0).getComponent(i).getz();
            if (zFeed > 1e-100 && xTrial > 1e-100) {
              double kVal = zFeed / xTrial;
              system.getPhase(0).getComponent(i).setK(kVal);
              system.getPhase(1).getComponent(i).setK(kVal);
            }
          }
          return true;
        }
      }
    }
    return false;
  }

  /**
   * <p>
   * stabilityAnalysis.
   * </p>
   *
   * @throws neqsim.util.exception.IsNaNException if any.
   * @throws neqsim.util.exception.TooManyIterationsException if any.
   */
  public void stabilityAnalysis() throws neqsim.util.exception.IsNaNException,
      neqsim.util.exception.TooManyIterationsException {
    double[] logWi = new double[system.getPhases()[0].getNumberOfComponents()];
    double[] deltalogWi = new double[system.getPhases()[0].getNumberOfComponents()];
    double[] oldDeltalogWi = new double[system.getPhases()[0].getNumberOfComponents()];
    double[] oldoldDeltalogWi = new double[system.getPhases()[0].getNumberOfComponents()];
    double[][] Wi = new double[2][system.getPhases()[0].getNumberOfComponents()];

    boolean secondOrderStabilityAnalysis = false;
    double[] oldlogw = new double[system.getPhases()[0].getNumberOfComponents()];
    double[] oldoldlogw = new double[system.getPhases()[0].getNumberOfComponents()];
    double[] oldoldoldlogw = new double[system.getPhases()[0].getNumberOfComponents()];
    double[] d = new double[system.getPhases()[0].getNumberOfComponents()];
    double[][] x = new double[2][system.getPhases()[0].getNumberOfComponents()];
    double[] error = new double[2];
    tm = new double[2];
    double[] alpha = null;
    Matrix f = new Matrix(system.getPhases()[0].getNumberOfComponents(), 1);
    Matrix df = null;
    // Reduced from 100 - Wilson K-value initialization converges faster
    int maxiterations = 50;
    // Acceleration interval reduced from 7 to 5 for faster convergence
    int accelerateInterval = 5;
    double fNorm = 1.0e10;
    double fNormOld = 0.0;
    for (int i = 0; i < system.getPhases()[0].getNumberOfComponents(); i++) {
      d[i] = minGibsPhaseLogZ[i] + minGibsLogFugCoef[i];
    }

    SystemInterface clonedSystem = minimumGibbsEnergySystem;
    double[] sumw = new double[2];
    sumw[1] = 0.0;
    sumw[0] = 0.0;
    for (int i = 0; i < clonedSystem.getPhase(0).getNumberOfComponents(); i++) {
      // Skip ions in sumw calculation - they don't participate in VLE
      if (clonedSystem.getPhase(0).getComponent(i).getK() < 1e-30) {
        continue;
      }
      sumw[1] += clonedSystem.getPhase(0).getComponent(i).getz()
          / clonedSystem.getPhase(0).getComponent(i).getK();
      if (clonedSystem.getPhase(0).getComponent(i).getz() > 0) {
        sumw[0] += clonedSystem.getPhase(0).getComponent(i).getK()
            * clonedSystem.getPhase(0).getComponent(i).getz();
      }
    }

    int start = 0;
    int end = 1;
    int mult = 1;

    if (lowestGibbsEnergyPhase == 0) {
      start = end;
      end = 0;
      mult = -1;
    }

    for (int i = 0; i < clonedSystem.getPhase(0).getNumberOfComponents(); i++) {
      // For ions (K < 1e-30), keep them entirely in liquid phase
      if (clonedSystem.getPhase(0).getComponent(i).getK() < 1e-30) {
        clonedSystem.getPhase(1).getComponent(i)
            .setx(clonedSystem.getPhase(0).getComponent(i).getz());
        clonedSystem.getPhase(0).getComponent(i).setx(1e-50);
        continue;
      }
      clonedSystem.getPhase(1).getComponent(i).setx(clonedSystem.getPhase(0).getComponent(i).getz()
          / clonedSystem.getPhase(0).getComponent(i).getK() / sumw[1]);
      clonedSystem.getPhase(0).getComponent(i).setx(clonedSystem.getPhase(0).getComponent(i).getK()
          * clonedSystem.getPhase(0).getComponent(i).getz() / sumw[0]);
    }

    for (int j = start; j >= end; j = j + mult) {
      for (int i = 0; i < clonedSystem.getPhases()[0].getNumberOfComponents(); i++) {
        Wi[j][i] = clonedSystem.getPhase(j).getComponent(i).getx();
        logWi[i] = Math.log(Wi[j][i]);
      }
      iterations = 0;
      fNorm = 1.0e10;
      boolean acceleration = true;
      double olderror = 1.0e10;

      do {
        iterations++;
        error[j] = 0.0;

        for (int i = 0; i < clonedSystem.getPhases()[0].getNumberOfComponents(); i++) {
          oldoldoldlogw[i] = oldoldlogw[i];
          oldoldlogw[i] = oldlogw[i];
          oldlogw[i] = logWi[i];

          oldoldDeltalogWi[i] = oldoldlogw[i] - oldoldoldlogw[i];
          oldDeltalogWi[i] = oldlogw[i] - oldoldlogw[i];
        }

        if ((iterations <= maxiterations - 10)
            || !system.isImplementedCompositionDeriativesofFugacity()) {
          try {
            clonedSystem.init(1, j);
          } catch (Exception e) {
            logger.error(e.toString());
            throw e;
          }
          fNormOld = fNorm;
          for (int i = 0; i < clonedSystem.getPhases()[0].getNumberOfComponents(); i++) {
            f.set(i, 0, Math.sqrt(Wi[j][i]) * (Math.log(Wi[j][i])
                + clonedSystem.getPhase(j).getComponent(i).getLogFugacityCoefficient() - d[i]));
          }
          fNorm = f.norm2();
          if (fNorm > fNormOld && iterations > 3 && (iterations - 1) % accelerateInterval != 0) {
            if (iterations > 10) {
              break;
            }
          }
          if (iterations % accelerateInterval == 0 && fNorm < fNormOld
              && !secondOrderStabilityAnalysis && acceleration) {
            // DEM acceleration (Michelsen 1982b, Risnes et al. 1981)
            // Dominant eigenvalue estimate: λ = (Δg_n · Δg_{n-1}) / (Δg_{n-1} · Δg_{n-1})
            double prod1 = 0.0;
            double prod2 = 0.0;
            for (i = 0; i < clonedSystem.getPhases()[0].getNumberOfComponents(); i++) {
              prod1 += deltalogWi[i] * oldDeltalogWi[i];
              prod2 += oldDeltalogWi[i] * oldDeltalogWi[i];
            }

            if (prod2 > 1e-20) {
              double lambda = prod1 / prod2;
              // Only accelerate if 0 < λ < 1 (convergent regime)
              if (lambda > 0.0 && lambda < 1.0) {
                double accelFactor = lambda / (1.0 - lambda);
                for (i = 0; i < clonedSystem.getPhases()[0].getNumberOfComponents(); i++) {
                  logWi[i] += accelFactor * deltalogWi[i];
                  error[j] += Math.abs((logWi[i] - oldlogw[i]) / oldlogw[i]);
                  Wi[j][i] = safeExp(logWi[i]);
                }
              }
            }
            if (error[j] > olderror) {
              acceleration = false;
            }
          } else {
            // successive substitution
            for (int i = 0; i < clonedSystem.getPhases()[0].getNumberOfComponents(); i++) {
              logWi[i] =
                  d[i] - clonedSystem.getPhase(j).getComponent(i).getLogFugacityCoefficient();
              error[j] += Math.abs((logWi[i] - oldlogw[i]) / oldlogw[i]);
              Wi[j][i] = safeExp(logWi[i]);
            }
          }
        } else {
          if (!secondOrderStabilityAnalysis) {
            alpha = new double[system.getPhases()[0].getNumberOfComponents()];
            df = new Matrix(system.getPhases()[0].getNumberOfComponents(),
                system.getPhases()[0].getNumberOfComponents());
            secondOrderStabilityAnalysis = true;
          }

          clonedSystem.init(3, j);
          for (int i = 0; i < clonedSystem.getPhases()[0].getNumberOfComponents(); i++) {
            alpha[i] = 2.0 * Math.sqrt(Wi[j][i]);
          }

          for (int i = 0; i < clonedSystem.getPhases()[0].getNumberOfComponents(); i++) {
            f.set(i, 0, Math.sqrt(Wi[j][i]) * (Math.log(Wi[j][i])
                + clonedSystem.getPhase(j).getComponent(i).getLogFugacityCoefficient() - d[i]));
            for (int k = 0; k < clonedSystem.getPhases()[0].getNumberOfComponents(); k++) {
              // Adaptive second-order damping: use unit diagonal near convergence,
              // and add limited damping only when residuals are still large.
              double diagDamping = (error[j] > 1.0e-2) ? 0.5 : 0.0;
              double kronDelt = (i == k) ? (1.0 + diagDamping) : 0.0;
              df.set(i, k, kronDelt + Math.sqrt(Wi[j][k] * Wi[j][i])
                  * clonedSystem.getPhase(j).getComponent(i).getdfugdn(k));
            }
          }

          Matrix dx = df.solve(f).times(-1.0);
          for (int i = 0; i < clonedSystem.getPhases()[0].getNumberOfComponents(); i++) {
            Wi[j][i] = Math.pow((alpha[i] + dx.get(i, 0)) / 2.0, 2.0);
            logWi[i] = Math.log(Wi[j][i]);
            error[j] += Math.abs((logWi[i] - oldlogw[i]) / oldlogw[i]);
          }

        }

        sumw[j] = 0.0;
        for (int i = 0; i < clonedSystem.getPhases()[0].getNumberOfComponents(); i++) {
          sumw[j] += Wi[j][i];
        }

        for (int i = 0; i < clonedSystem.getPhases()[0].getNumberOfComponents(); i++) {
          deltalogWi[i] = logWi[i] - oldlogw[i];
          clonedSystem.getPhase(j).getComponent(i).setx(Wi[j][i] / sumw[j]);
        }
        olderror = error[j];
      } while ((f.norm1() > 1e-3 && error[j] > 1e-3 && iterations < maxiterations)
          || (iterations % accelerateInterval) == 0 || iterations < 3);

      if (iterations >= maxiterations) {
        throw new neqsim.util.exception.TooManyIterationsException("too many iterations", null,
            maxiterations);
      }

      tm[j] = 1.0;
      for (int i = 0; i < clonedSystem.getPhases()[0].getNumberOfComponents(); i++) {
        tm[j] -= Wi[j][i];
        x[j][i] = clonedSystem.getPhase(j).getComponent(i).getx();
      }

      if (tm[j] < tmLimit && error[j] < 1e-6) {
        break;
      } else {
        tm[j] = 1.0;
      }
    }

    // Improved trivial solution detection using cosine similarity.
    // Use strict compatibility gating: enable non-trivial filtering for enhanced/LLE-auto
    // contexts, keep legacy tm-reset behavior for baseline default mode.
    boolean useNonTrivialFiltering = system.doEnhancedMultiPhaseCheck()
        || system.doCheckForLiquidLiquidSplit() || shouldRunAutomaticLLECheck();
    if (useNonTrivialFiltering) {
      for (int trialPhase = 0; trialPhase < 2; trialPhase++) {
        double dotProduct = 0.0;
        double normW = 0.0;
        double normFeed = 0.0;
        for (int i = 0; i < clonedSystem.getPhase(0).getNumberOfComponents(); i++) {
          double xTrial = x[trialPhase][i];
          double xFeed =
              minimumGibbsEnergySystem.getPhase(lowestGibbsEnergyPhase).getComponent(i).getx();
          dotProduct += xTrial * xFeed;
          normW += xTrial * xTrial;
          normFeed += xFeed * xFeed;
        }
        double cosineSimilarity = dotProduct / (Math.sqrt(normW) * Math.sqrt(normFeed) + 1e-100);
        // If cosine similarity > 0.9999, compositions are nearly identical (trivial solution).
        if (cosineSimilarity > 0.9999) {
          tm[trialPhase] = 0.0;
        }
      }
    } else {
      tm[0] = 0.0;
      tm[1] = 0.0;
    }

    if (((tm[0] < tmLimit) || (tm[1] < tmLimit))
        && !(Double.isNaN(tm[0]) || (Double.isNaN(tm[1])))) {
      for (int i = 0; i < clonedSystem.getPhases()[0].getNumberOfComponents(); i++) {
        if (system.getPhase(0).getComponent(i).getx() < 1e-100) {
          continue;
        }
        if (tm[0] < tmLimit) {
          system.getPhases()[1].getComponent(i).setK(clonedSystem.getPhase(0).getComponent(i).getx()
              / clonedSystem.getPhase(1).getComponent(i).getx());
          system.getPhases()[0].getComponent(i).setK(clonedSystem.getPhase(0).getComponent(i).getx()
              / clonedSystem.getPhase(1).getComponent(i).getx());
        } else if (tm[1] < tmLimit) {
          system.getPhases()[1].getComponent(i).setK(clonedSystem.getPhase(0).getComponent(i).getx()
              / clonedSystem.getPhase(1).getComponent(i).getx());
          system.getPhases()[0].getComponent(i).setK(clonedSystem.getPhase(0).getComponent(i).getx()
              / clonedSystem.getPhase(1).getComponent(i).getx());
        } else {
          logger.info("error in stability analysis");
          system.init(0);
        }

        if (Double.isNaN(tm[j])) {
          tm[j] = 0;
        }
      }
    }
  }

  /**
   * Tries pure-component trial phases to detect liquid-liquid equilibrium (LLE) instability.
   *
   * <p>
   * The standard {@link #stabilityAnalysis()} uses Wilson K-value based initial guesses, which
   * assume gas-liquid equilibrium (GLE). At temperatures well below the critical temperature of the
   * lightest component, Wilson K-values converge to values less than 1 for all components, making
   * them ineffective for detecting LLE. This method complements the standard analysis by
   * initializing trial phases as nearly-pure heaviest and lightest components, which is the same
   * approach used by {@link TPmultiflash}.
   * </p>
   *
   * @return true if LLE instability was detected and K-values have been set on the system
   */
  protected boolean pureComponentStabilityTrials() {
    int numComp = system.getPhase(0).getNumberOfComponents();
    if (numComp <= 1) {
      return false;
    }

    // Reference chemical potentials from the lowest Gibbs energy phase
    double[] d = new double[numComp];
    for (int ic = 0; ic < numComp; ic++) {
      d[ic] = minGibsPhaseLogZ[ic] + minGibsLogFugCoef[ic];
    }

    SystemInterface clonedSystem = minimumGibbsEnergySystem;
    int trialPhaseIdx = 1; // Use phase index 1 for trial composition

    // Find the heaviest and lightest hydrocarbon components for targeted trials
    double mMax = 0.0;
    double mMin = 1e10;
    int heavyComp = -1;
    int lightComp = -1;
    for (int ic = 0; ic < numComp; ic++) {
      if (clonedSystem.getPhase(0).getComponent(ic).getz() < 1e-50) {
        continue;
      }
      double mw = clonedSystem.getPhase(0).getComponent(ic).getMolarMass();
      if (mw > mMax) {
        mMax = mw;
        heavyComp = ic;
      }
      if (mw < mMin) {
        mMin = mw;
        lightComp = ic;
      }
    }

    // Try heaviest and lightest components as pure trial phases (like TPmultiflash)
    int[] trialComponents =
        (heavyComp == lightComp) ? new int[] {heavyComp} : new int[] {heavyComp, lightComp};
    for (int ti = 0; ti < trialComponents.length; ti++) {
      int jc = trialComponents[ti];

      // Set trial phase to nearly pure component jc
      double[] logWi = new double[numComp];
      for (int ic = 0; ic < numComp; ic++) {
        double nomb = (ic == jc) ? 1.0 : 1.0e-12;
        if (clonedSystem.getPhase(0).getComponent(ic).getz() < 1e-100) {
          nomb = 0.0;
        }
        logWi[ic] = (nomb > 1e-100) ? Math.log(nomb) : -10000.0;
        clonedSystem.getPhase(trialPhaseIdx).getComponent(ic).setx(nomb > 0 ? nomb : 1e-50);
      }

      // Ensure the trial phase uses the liquid root of the cubic EOS.
      // After stabilityAnalysis(), the trial phase may have been left as GAS type.
      // For LLE detection, we need the liquid root.
      clonedSystem.setPhaseType(trialPhaseIdx, PhaseType.LIQUID);

      // Successive substitution iteration with DEM acceleration
      int maxIter = 80;
      int accelInterval = 5;
      double err = 1.0e10;
      boolean converged = false;
      double[] oldLogWi = new double[numComp];
      double[] prevDelta = new double[numComp];
      for (int iter = 0; iter < maxIter; iter++) {
        double errOld = err;
        err = 0.0;
        System.arraycopy(logWi, 0, oldLogWi, 0, numComp);

        try {
          clonedSystem.init(1, trialPhaseIdx);
        } catch (Exception ex) {
          break;
        }

        // Standard SSI step
        for (int ic = 0; ic < numComp; ic++) {
          if (clonedSystem.getPhase(0).getComponent(ic).getz() > 1e-100 && !Double.isInfinite(
              clonedSystem.getPhase(trialPhaseIdx).getComponent(ic).getLogFugacityCoefficient())) {
            logWi[ic] = d[ic]
                - clonedSystem.getPhase(trialPhaseIdx).getComponent(ic).getLogFugacityCoefficient();
          }
        }

        // DEM acceleration (Michelsen 1982b) every accelInterval steps
        if (iter > 0 && iter % accelInterval == 0 && err < errOld) {
          double dot1 = 0.0;
          double dot2 = 0.0;
          for (int ic = 0; ic < numComp; ic++) {
            double curDelta = logWi[ic] - oldLogWi[ic];
            dot1 += curDelta * prevDelta[ic];
            dot2 += prevDelta[ic] * prevDelta[ic];
          }
          if (dot2 > 1e-20) {
            double lambda = dot1 / dot2;
            if (lambda > 0.0 && lambda < 1.0) {
              double accelFactor = lambda / (1.0 - lambda);
              for (int ic = 0; ic < numComp; ic++) {
                logWi[ic] += accelFactor * (logWi[ic] - oldLogWi[ic]);
              }
            }
          }
        }

        // Track step delta for next acceleration
        for (int ic = 0; ic < numComp; ic++) {
          prevDelta[ic] = logWi[ic] - oldLogWi[ic];
          err += Math.abs(prevDelta[ic]);
        }

        // Update trial phase composition
        double sumW = 0.0;
        for (int ic = 0; ic < numComp; ic++) {
          double wi = safeExp(logWi[ic]);
          sumW += wi;
        }
        for (int ic = 0; ic < numComp; ic++) {
          double wi = safeExp(logWi[ic]);
          clonedSystem.getPhase(trialPhaseIdx).getComponent(ic).setx(wi / sumW);
        }

        if (err < 1e-9) {
          converged = true;
          break;
        }
        if (iter > 10 && err > errOld * 2.0) {
          break; // diverging badly
        }
        // Early termination: clearly stable (sum(Wi) well below 1)
        if (iter > 5 && sumW < 0.8) {
          break;
        }
      }

      if (!converged) {
        logger.debug("Pure-component trial (comp {}) did not converge after {} iterations", jc,
            maxIter);
        continue;
      }

      // Calculate tangent plane distance
      double tmVal = 1.0;
      for (int ic = 0; ic < numComp; ic++) {
        if (clonedSystem.getPhase(0).getComponent(ic).getz() > 1e-100) {
          tmVal -= safeExp(logWi[ic]);
        }
      }

      // Check trivial solution using L1-norm (same approach as TPmultiflash)
      // Cosine similarity fails for binary LLE where compositions are nearly
      // co-directional in the 2D simplex despite being genuinely different phases.
      double xL1Diff = 0.0;
      for (int ic = 0; ic < numComp; ic++) {
        double xTrial = clonedSystem.getPhase(trialPhaseIdx).getComponent(ic).getx();
        double xFeed = clonedSystem.getPhase(0).getComponent(ic).getx();
        xL1Diff += Math.abs(xTrial - xFeed);
      }
      if (xL1Diff < 1e-4) {
        continue; // trivial solution
      }

      if (tmVal < tmLimit) {
        // Instability detected — set K-values from trial composition
        logger.debug("LLE instability detected via pure-component trial (comp {})", jc);
        for (int ic = 0; ic < numComp; ic++) {
          double xTrial = clonedSystem.getPhase(trialPhaseIdx).getComponent(ic).getx();
          double xFeed = clonedSystem.getPhase(0).getComponent(ic).getx();
          double kVal = (xFeed > 1e-100 && xTrial > 1e-100) ? xFeed / xTrial : 1.0;
          system.getPhase(0).getComponent(ic).setK(kVal);
          system.getPhase(1).getComponent(ic).setK(kVal);
        }
        return true;
      } else {
        logger.debug("Pure-component trial (comp {}) converged: tmVal={}, xL1Diff={}", jc, tmVal,
            xL1Diff);
      }
    }
    return false;
  }

  /**
   * Determine whether automatic LLE supplementary stability checks should be enabled.
   *
   * <p>
   * Automatic checks are enabled for systems where LLE/VLLE is common even if users have not
   * explicitly turned on {@code setCheckForLiquidLiquidSplit(true)}.
   * </p>
   *
   * @return true if automatic LLE checks should run
   */
  protected boolean shouldRunAutomaticLLECheck() {
    if (system == null || system.getPhase(0).getNumberOfComponents() <= 1) {
      return false;
    }
    String modelName = system.getModelName() == null ? "" : system.getModelName();
    String lowerModelName = modelName.toLowerCase();
    if (modelName.contains("CPA") || lowerModelName.contains("electrolyte")) {
      return true;
    }
    // Keep water-triggered automatic LLE checks compatibility-safe by requiring either
    // enhanced mode or explicit user opt-in for LLE checks.
    if (system.hasComponent("water")) {
      return system.doEnhancedMultiPhaseCheck() || system.doCheckForLiquidLiquidSplit();
    }
    return system.isChemicalSystem() && system.doEnhancedMultiPhaseCheck();
  }

  /**
   * <p>
   * stabilityCheck.
   * </p>
   *
   * @return a boolean
   */
  public boolean stabilityCheck() {
    boolean stable = false;
    lowestGibbsEnergyPhase = findLowestGibbsEnergyPhase();
    if (system.getPhase(lowestGibbsEnergyPhase).getNumberOfComponents() > 1) {
      try {
        stabilityAnalysis();
      } catch (Exception ex) {
        logger.debug("Stability analysis did not converge: {}", ex.getMessage());
      }
    }
    if (tm[0] > tmLimit && tm[1] > tmLimit && !system.isChemicalSystem()
        || system.getPhase(0).getNumberOfComponents() == 1) {
      // Standard analysis declares stable. Try supplementary stability trials.
      boolean retryFoundInstability = false;
      boolean ambiguousStability = Math.abs(tm[0]) < 5e-2 || Math.abs(tm[1]) < 5e-2;
      boolean doLLESupplementaryCheck =
          system.doCheckForLiquidLiquidSplit() || shouldRunAutomaticLLECheck();
      // Amplified K-value trials catch near-critical VLE instability (near cricondenbar)
      if ((tm[0] < 0.5 || tm[1] < 0.5 || ambiguousStability)
          && !system.getModelName().contains("CPA")) {
        retryFoundInstability = amplifiedKStabilityRetry();
      }
      // Pure-component trials catch LLE instability (Wilson K fails at T << Tc)
      if (!retryFoundInstability && doLLESupplementaryCheck) {
        retryFoundInstability = pureComponentStabilityTrials();
      }
      if (retryFoundInstability) {
        // Supplementary trial found instability — calculate beta and init for two-phase
        RachfordRice rachfordRice = new RachfordRice();
        try {
          system.setBeta(rachfordRice.calcBeta(system.getKvector(), system.getzvector()));
        } catch (Exception ex) {
          if (!Double.isNaN(rachfordRice.getBeta()[0])) {
            system.setBeta(rachfordRice.getBeta()[0]);
          } else {
            system.setBeta(Double.NaN);
          }
        }
        system.calc_x_y();
        system.init(1);
        stable = false;
      } else {
        stable = true;
        system.init(0);
        system.setNumberOfPhases(1);
        if (lowestGibbsEnergyPhase == 0) {
          system.setPhaseType(0, PhaseType.GAS);
        } else {
          system.setPhaseType(0, PhaseType.LIQUID);
        }
        system.init(1);
        if (solidCheck && !system.doMultiPhaseCheck()) {
          this.solidPhaseFlash();
        }
      }
    } else {
      RachfordRice rachfordRice = new RachfordRice();
      try {
        system.setBeta(
            rachfordRice.calcBeta(system.getKvector(), minimumGibbsEnergySystem.getzvector()));
      } catch (Exception ex) {
        if (!Double.isNaN(rachfordRice.getBeta()[0])) {
          system.setBeta(rachfordRice.getBeta()[0]);
        } else {
          system.setBeta(Double.NaN);
        }
        logger.error(ex.getMessage(), ex);
      }
      system.calc_x_y();
      system.init(1);
    }

    return stable;
  }

  /** {@inheritDoc} */
  @Override
  @ExcludeFromJacocoGeneratedReport
  public void displayResult() {
    system.display();
  }

  /**
   * <p>
   * solidPhaseFlash.
   * </p>
   */
  public void solidPhaseFlash() {
    boolean solidPhase = false;
    double frac = 0;
    int solid = 0;
    double[] tempVar = new double[system.getPhases()[0].getNumberOfComponents()];

    if (!system.hasSolidPhase()) {
      system.setNumberOfPhases(system.getNumberOfPhases() + 1);
      system.setPhaseIndex(system.getNumberOfPhases() - 1, 3);
    }
    system.init(1);

    for (int k = 0; k < system.getPhase(0).getNumberOfComponents(); k++) {
      if (system.getPhase(0).getComponent(k).doSolidCheck()) {
        tempVar[k] = system.getPhase(0).getComponent(k).getz();
        for (int i = 0; i < system.getNumberOfPhases() - 1; i++) {
          tempVar[k] -= system.getBeta(i)
              * system.getPhase(PhaseType.SOLID).getComponent(k).getFugacityCoefficient()
              / system.getPhase(i).getComponent(k).getFugacityCoefficient();
        }

        if (tempVar[k] > 0.0 && tempVar[k] > frac) {
          solidPhase = true;
          solid = k;
          frac = tempVar[k];
          for (int p = 0; p < system.getPhases()[0].getNumberOfComponents(); p++) {
            system.getPhase(PhaseType.SOLID).getComponent(p).setx(1.0e-20);
          }
          system.getPhase(PhaseType.SOLID).getComponents()[solid].setx(1.0);
        }
        // logger.info("tempVar: " + tempVar[k]);
      }
    }

    if (solidPhase) {
      if (frac < system.getPhases()[0].getComponents()[solid].getz() + 1e10) {
        for (int i = 0; i < system.getNumberOfPhases() - 1; i++) {
          // system.getPhases()[i].getComponents()[solid].setx(1.0e-10);
        }
        system.init(1);
        // logger.info("solid phase will form..." + system.getNumberOfPhases());
        // logger.info("freezing component " + solid);
        system.setBeta(system.getNumberOfPhases() - 1, frac);
        system.initBeta();
        system.setBeta(system.getNumberOfPhases() - 1,
            system.getPhase(PhaseType.SOLID).getComponent(solid).getNumberOfmoles()
                / system.getNumberOfMoles());
        // double phasetot=0.0;
        // for(int ph=0;ph<system.getNumberOfPhases();ph++){
        // phasetot += system.getPhase(ph).getBeta();
        // }
        // for(int ph=0;ph<system.getNumberOfPhases();ph++){
        // system.setBeta(ph, system.getPhase(ph).getBeta()/phasetot);
        // }
        system.init(1);
        // for(int ph=0;ph<system.getNumberOfPhases();ph++){
        // logger.info("beta " + system.getPhase(ph).getBeta());
        // }
        // TPmultiflash operation = new TPmultiflash(system, true);
        // operation.run();
        SolidFlash solflash = new SolidFlash(system);
        solflash.setSolidComponent(solid);
        solflash.run();
      } else {
        // logger.info("all liquid will freeze out - removing liquid phase..");
        // int phasesNow = system.getNumberOfPhases()-1;
        // system.init(0);
        // system.setNumberOfPhases(phasesNow);
        // system.setNumberOfPhases(system.getNumberOfPhases()-1);
        // system.setPhaseIndex(system.getNumberOfPhases()-1, 3);
        // system.setBeta(1-system.getPhases()[0].getComponents()[solid].getz());
        // system.init(1);
        // system.init_x_y();
        // system.init(1);
        // solidPhaseFlash();
        // solid-vapor flash
      }
    } else {
      // system.setPhaseIndex(system.getNumberOfPhases() - 1,
      // system.getNumberOfPhases() - 1);
      system.setNumberOfPhases(system.getNumberOfPhases() - 1);
      // logger.info("no solid phase will form..");
    }
  }

  /** {@inheritDoc} */
  @Override
  public void printToFile(String name) {}

  /** {@inheritDoc} */
  @Override
  public double[][] getPoints(int i) {
    return null;
  }

  /** {@inheritDoc} */
  @Override
  public String[][] getResultTable() {
    return null;
  }

  /** {@inheritDoc} */
  @Override
  public void addData(String name, double[][] data) {}
}
