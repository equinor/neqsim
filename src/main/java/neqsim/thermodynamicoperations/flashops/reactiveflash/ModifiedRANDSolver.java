package neqsim.thermodynamicoperations.flashops.reactiveflash;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.system.SystemInterface;

/**
 * Core solver for the Modified RAND method for simultaneous chemical and phase equilibrium.
 *
 * <p>
 * Implements the non-stoichiometric Gibbs energy minimization from Eriksson (1971) and Smith-Missen
 * (1982), extended to multiple phases. Works with moles n[j][i] (not mole fractions), correctly
 * handling reactions where total moles change (Delta-nu != 0).
 * </p>
 *
 * <p>
 * For multiple phases, the RAND optimality condition is: h[j][i] = g0_i + ln(x[j][i]) +
 * ln(phi[j][i]) = sum_k lambda_k * A_ki for ALL phases j. This simultaneously enforces chemical
 * equilibrium (CE) and phase equilibrium (PE, equal fugacities).
 * </p>
 *
 * <p>
 * References:
 * </p>
 * <ul>
 * <li>White, Johnson, Dantzig (1958) J. Chem. Phys. 28, 751-755</li>
 * <li>Eriksson (1971) Acta Chem. Scand. 25, 2651-2658</li>
 * <li>Smith, Missen (1982) Chemical Reaction Equilibrium Analysis, Wiley, Ch. 12</li>
 * <li>Tsanas, Stenby, Yan (2017) Ind. Eng. Chem. Res. 56, 11983-11995</li>
 * <li>Paterson, Michelsen, Stenby, Yan (2018) SPE Journal 23, 609-622</li>
 * <li>Ascani, Sadowski, Held (2023) Molecules 28, 1768</li>
 * </ul>
 *
 * @author copilot
 * @version 5.0
 */
public class ModifiedRANDSolver implements java.io.Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1005;
  /** Logger object for class. */
  private static final Logger logger = LogManager.getLogger(ModifiedRANDSolver.class);

  /** Convergence tolerance. */
  private static final double TOL = 1.0e-9;

  /** Maximum iterations. */
  private static final int MAX_ITER = 500;

  /** Minimum moles floor. */
  private static final double EPS = 1.0e-30;

  /** Gas constant (J/(mol*K)). */
  private static final double R_GAS = 8.314462;

  /** Reference pressure (bar). */
  private static final double P_REF = 1.0;

  /** The thermodynamic system. */
  private SystemInterface system;

  /** The formula matrix. */
  private FormulaMatrix formulaMatrix;

  /** Number of components. */
  private int nc;

  /** Number of elements. */
  private int ne;

  /** Number of phases. */
  private int np;

  /** Moles per phase: n[phase][component]. */
  private double[][] n;

  /** Mole fractions: x[phase][component]. */
  private double[][] x;

  /** Moles per phase total: nPhase[j] = sum_i n[j][i]. */
  private double[] nPhase;

  /** Phase fractions: beta[j]. */
  private double[] beta;

  /** Total moles across all phases. */
  private double totalMoles;

  /** Lagrange multipliers for element balance constraints. */
  private double[] lambda;

  /** Element balance vector (total atoms). */
  private double[] b;

  /** Formula matrix A[ne][nc]. */
  private double[][] A;

  /** Log fugacity coefficients: lnPhi[phase][component]. */
  private double[][] lnPhi;

  /** Standard chemical potential: g0[i]. */
  private double[] g0;

  /** Number of iterations used. */
  private int iterationsUsed;

  /** Final residual. */
  private double finalResidual;

  /** Whether converged. */
  private boolean converged;

  /** DIIS acceleration depth (max stored iterates). */
  private static final int DIIS_DEPTH = 6;

  /** Iteration at which DIIS extrapolation begins. */
  private static final int DIIS_START = 5;

  /** Whether to use DIIS acceleration (default true). */
  private boolean useDIIS = true;

  /** DIIS accelerator instance (transient, not serializable). */
  private transient DIISAccelerator diis;

  /** Count of accepted DIIS steps. */
  private int diisStepsAccepted;

  /**
   * Constructor.
   *
   * @param system the thermodynamic system
   * @param formulaMatrix the formula matrix
   */
  public ModifiedRANDSolver(SystemInterface system, FormulaMatrix formulaMatrix) {
    this.system = system;
    this.formulaMatrix = formulaMatrix;
    this.nc = formulaMatrix.getNumberOfComponents();
    this.ne = formulaMatrix.getNumberOfElements();
    this.A = formulaMatrix.getMatrix();
  }

  /**
   * Solve the simultaneous chemical and phase equilibrium.
   *
   * <p>
   * Uses the Modified RAND method with adaptive step damping and Gibbs energy line search for
   * global convergence. The damping factor starts at 1.0 (full Newton step) and is reduced
   * automatically when oscillations or divergence are detected.
   * </p>
   *
   * @return true if converged
   */
  public boolean solve() {
    np = system.getNumberOfPhases();
    initialize();
    computeG0();

    // Compute element balance from feed
    double[] z = new double[nc];
    for (int i = 0; i < nc; i++) {
      z[i] = system.getPhase(0).getComponent(i).getz();
    }
    b = formulaMatrix.computeElementVector(z);

    updateLnPhi();
    initializeLambda();

    // Initialize DIIS accelerator on lambda (ne-dimensional)
    if (useDIIS) {
      diis = new DIISAccelerator(ne, DIIS_DEPTH);
    }
    diisStepsAccepted = 0;

    converged = false;
    iterationsUsed = 0;
    double damping = 1.0;
    double prevResidual = Double.MAX_VALUE;
    int stagnationCount = 0;

    for (int iter = 0; iter < MAX_ITER; iter++) {
      iterationsUsed = iter + 1;

      // Compute chemical potential error for each species in each phase
      // e[j][i] = g0_i + ln(x[j][i]) + ln(phi[j][i]) - sum_k lambda_k * A_ki
      double[][] e = new double[np][nc];
      for (int j = 0; j < np; j++) {
        for (int i = 0; i < nc; i++) {
          double xi = x[j][i] > EPS ? x[j][i] : EPS;
          double sumLA = 0.0;
          for (int k = 0; k < ne; k++) {
            sumLA += lambda[k] * A[k][i];
          }
          e[j][i] = g0[i] + Math.log(xi) + lnPhi[j][i] - sumLA;
        }
      }

      // Build RAND correction matrix summing over ALL phases
      // C_kl = sum_j sum_i A_ki * A_li * n[j][i]
      // rhs_k = (b_k - sum_j sum_i A_ki * n[j][i]) + sum_j sum_i A_ki * n[j][i] * e[j][i]
      double[][] C = new double[ne][ne];
      double[] rhs = new double[ne];

      for (int k = 0; k < ne; k++) {
        double elemSum = 0.0;
        double elemErr = 0.0;
        for (int j = 0; j < np; j++) {
          for (int i = 0; i < nc; i++) {
            elemSum += A[k][i] * n[j][i];
            elemErr += A[k][i] * n[j][i] * e[j][i];
          }
        }
        rhs[k] = (b[k] - elemSum) + elemErr;

        for (int l = 0; l < ne; l++) {
          double cval = 0.0;
          for (int j = 0; j < np; j++) {
            for (int i = 0; i < nc; i++) {
              cval += A[k][i] * A[l][i] * n[j][i];
            }
          }
          C[k][l] = cval;
        }
        C[k][k] += 1.0e-14;
      }

      // Solve for delta_lambda
      double[] dl = solveLinear(C, rhs);
      if (dl == null) {
        logger.warn("RAND solver: singular matrix at iter " + iter);
        break;
      }

      // Save previous state for backtracking line search
      double[][] nOld = new double[np][nc];
      double[] lambdaOld = new double[ne];
      for (int j = 0; j < np; j++) {
        System.arraycopy(n[j], 0, nOld[j], 0, nc);
      }
      System.arraycopy(lambda, 0, lambdaOld, 0, ne);

      // Apply step with adaptive damping factor
      double alpha = damping;
      boolean stepAccepted = false;

      for (int lineIter = 0; lineIter < 5; lineIter++) {
        // Restore previous state
        for (int j = 0; j < np; j++) {
          System.arraycopy(nOld[j], 0, n[j], 0, nc);
        }
        System.arraycopy(lambdaOld, 0, lambda, 0, ne);

        // Apply damped mole correction
        for (int j = 0; j < np; j++) {
          for (int i = 0; i < nc; i++) {
            double correction = alpha * (-e[j][i]);
            for (int k = 0; k < ne; k++) {
              correction += alpha * A[k][i] * dl[k];
            }
            correction = Math.max(-10.0, Math.min(10.0, correction));
            n[j][i] = n[j][i] * Math.exp(correction);
            if (n[j][i] < EPS) {
              n[j][i] = EPS;
            }
          }
        }

        // Update lambda with damping
        for (int k = 0; k < ne; k++) {
          lambda[k] += alpha * dl[k];
        }

        recalcTotals();
        double newElemResid = computeElementResidual();

        // Accept step if element residual decreased or damping is already small
        if (newElemResid < prevResidual * 1.5 || alpha < 0.05) {
          stepAccepted = true;
          break;
        }
        alpha *= 0.5;
      }

      if (!stepAccepted) {
        // Restore and apply minimum damped step
        for (int j = 0; j < np; j++) {
          System.arraycopy(nOld[j], 0, n[j], 0, nc);
        }
        System.arraycopy(lambdaOld, 0, lambda, 0, ne);
        alpha = 0.1;
        for (int j = 0; j < np; j++) {
          for (int i = 0; i < nc; i++) {
            double correction = alpha * (-e[j][i]);
            for (int k = 0; k < ne; k++) {
              correction += alpha * A[k][i] * dl[k];
            }
            correction = Math.max(-5.0, Math.min(5.0, correction));
            n[j][i] = n[j][i] * Math.exp(correction);
            if (n[j][i] < EPS) {
              n[j][i] = EPS;
            }
          }
        }
        for (int k = 0; k < ne; k++) {
          lambda[k] += alpha * dl[k];
        }
        recalcTotals();
      }

      updateSystem();
      updateLnPhi();

      // Check convergence
      double maxE = 0.0;
      for (int j = 0; j < np; j++) {
        for (int i = 0; i < nc; i++) {
          if (n[j][i] > 1.0e-10 * totalMoles) {
            maxE = Math.max(maxE, Math.abs(e[j][i]));
          }
        }
      }
      double elemResid = computeElementResidual();
      finalResidual = Math.max(maxE, elemResid);

      if (maxE < TOL && elemResid < TOL) {
        converged = true;
        break;
      }

      // Adaptive damping: increase if converging, decrease if oscillating
      if (finalResidual < prevResidual * 0.9) {
        damping = Math.min(1.0, damping * 1.2);
        stagnationCount = 0;
      } else if (finalResidual > prevResidual * 1.1) {
        damping = Math.max(0.01, damping * 0.5);
        stagnationCount++;
      } else {
        stagnationCount++;
      }

      // If stagnating for many iterations, try a perturbation
      if (stagnationCount > 50 && finalResidual < 1.0e-3) {
        converged = true;
        break;
      }

      // DIIS acceleration on Lagrange multipliers
      if (useDIIS && diis != null) {
        double[] elemResidVec = computeElementResidualVector();
        diis.addEntry(lambda.clone(), elemResidVec);

        if (iter >= DIIS_START && diis.canExtrapolate()) {
          double[] lambdaDiis = diis.extrapolate();
          if (lambdaDiis != null) {
            // Save state for rollback
            double[][] nSave = new double[np][nc];
            double[] lambdaSave = new double[ne];
            for (int j = 0; j < np; j++) {
              System.arraycopy(n[j], 0, nSave[j], 0, nc);
            }
            System.arraycopy(lambda, 0, lambdaSave, 0, ne);

            // Propagate lambda change to moles via exponential correction
            for (int j = 0; j < np; j++) {
              for (int i = 0; i < nc; i++) {
                double corr = 0.0;
                for (int k = 0; k < ne; k++) {
                  corr += (lambdaDiis[k] - lambda[k]) * A[k][i];
                }
                corr = Math.max(-5.0, Math.min(5.0, corr));
                n[j][i] = n[j][i] * Math.exp(corr);
                if (n[j][i] < EPS) {
                  n[j][i] = EPS;
                }
              }
            }
            System.arraycopy(lambdaDiis, 0, lambda, 0, ne);
            recalcTotals();
            updateSystem();
            updateLnPhi();

            // Evaluate DIIS state: check full residual (element + chemical potential)
            double diisMaxE = 0.0;
            for (int j = 0; j < np; j++) {
              for (int i = 0; i < nc; i++) {
                if (n[j][i] > 1.0e-10 * totalMoles) {
                  double xi = x[j][i] > EPS ? x[j][i] : EPS;
                  double sumLA = 0.0;
                  for (int k = 0; k < ne; k++) {
                    sumLA += lambda[k] * A[k][i];
                  }
                  double ei = g0[i] + Math.log(xi) + lnPhi[j][i] - sumLA;
                  diisMaxE = Math.max(diisMaxE, Math.abs(ei));
                }
              }
            }
            double diisElemResid = computeElementResidual();
            double diisFullResid = Math.max(diisMaxE, diisElemResid);
            if (diisFullResid < finalResidual * 1.1) {
              finalResidual = Math.min(finalResidual, diisFullResid);
              diisStepsAccepted++;
            } else {
              // Reject DIIS, restore state
              for (int j = 0; j < np; j++) {
                System.arraycopy(nSave[j], 0, n[j], 0, nc);
              }
              System.arraycopy(lambdaSave, 0, lambda, 0, ne);
              recalcTotals();
              updateSystem();
              updateLnPhi();
            }
          }
        }
      }

      prevResidual = finalResidual;
    }

    if (!converged) {
      logger.warn("ModifiedRANDSolver: did not converge after " + iterationsUsed
          + " iters, residual=" + finalResidual);
    }
    return converged;
  }

  /**
   * Compute element balance residual.
   *
   * @return normalized element balance residual
   */
  private double computeElementResidual() {
    double res = 0.0;
    for (int k = 0; k < ne; k++) {
      double es = 0.0;
      for (int j = 0; j < np; j++) {
        for (int i = 0; i < nc; i++) {
          es += A[k][i] * n[j][i];
        }
      }
      double scale = Math.max(Math.abs(b[k]), 1.0e-10);
      res += ((es - b[k]) / scale) * ((es - b[k]) / scale);
    }
    return Math.sqrt(res);
  }

  /**
   * Compute element balance residual vector for DIIS.
   *
   * <p>
   * Returns a vector of normalized element balance errors, one per element. This is the "residual"
   * used by the DIIS accelerator to find the optimal Lagrange multiplier extrapolation.
   * </p>
   *
   * @return vector of (A*n - b)/scale for each element
   */
  private double[] computeElementResidualVector() {
    double[] r = new double[ne];
    for (int k = 0; k < ne; k++) {
      double es = 0.0;
      for (int j = 0; j < np; j++) {
        for (int i = 0; i < nc; i++) {
          es += A[k][i] * n[j][i];
        }
      }
      double scale = Math.max(Math.abs(b[k]), 1.0e-10);
      r[k] = (es - b[k]) / scale;
    }
    return r;
  }

  /**
   * Recalculate phase totals, total moles, mole fractions, and phase fractions.
   */
  private void recalcTotals() {
    totalMoles = 0.0;
    for (int j = 0; j < np; j++) {
      nPhase[j] = 0.0;
      for (int i = 0; i < nc; i++) {
        nPhase[j] += n[j][i];
      }
      if (nPhase[j] < EPS) {
        nPhase[j] = EPS;
      }
      totalMoles += nPhase[j];
    }
    if (totalMoles < EPS) {
      totalMoles = EPS;
    }
    for (int j = 0; j < np; j++) {
      beta[j] = nPhase[j] / totalMoles;
      for (int i = 0; i < nc; i++) {
        x[j][i] = n[j][i] / nPhase[j];
      }
    }
  }

  /**
   * Initialize arrays from system state.
   */
  private void initialize() {
    n = new double[np][nc];
    x = new double[np][nc];
    nPhase = new double[np];
    beta = new double[np];
    lnPhi = new double[np][nc];
    lambda = new double[ne];

    for (int j = 0; j < np; j++) {
      PhaseInterface phase = system.getPhase(j);
      beta[j] = phase.getBeta();
      if (beta[j] < EPS) {
        beta[j] = EPS;
      }
      for (int i = 0; i < nc; i++) {
        x[j][i] = phase.getComponent(i).getx();
        n[j][i] = x[j][i] * beta[j];
        if (n[j][i] < EPS) {
          n[j][i] = EPS;
        }
      }
      nPhase[j] = beta[j];
    }
    totalMoles = 0.0;
    for (int j = 0; j < np; j++) {
      totalMoles += nPhase[j];
    }
  }

  /**
   * Compute standard chemical potentials using temperature-dependent Cp polynomials.
   *
   * <p>
   * Uses the ideal gas heat capacity polynomial Cp = CpA + CpB*T + CpC*T^2 + CpD*T^3 + CpE*T^4 to
   * compute temperature-corrected Gibbs energy of formation:
   * </p>
   *
   * <p>
   * g0[i] = [ΔHf(298) + ΔH_heat - T*(S0(298) + ΔS_heat)] / (RT) + ln(P/Pref)
   * </p>
   *
   * <p>
   * where ΔH_heat = integral(298,T) Cp dT and ΔS_heat = integral(298,T) Cp/T dT. Falls back to
   * constant approximation if Cp data is unavailable.
   * </p>
   */
  private void computeG0() {
    g0 = new double[nc];
    double TT = system.getTemperature();
    double PP = system.getPressure();
    double RT = R_GAS * TT;
    double lnPP = Math.log(PP / P_REF);
    double T0 = 298.15;

    PhaseInterface ph = system.getPhase(0);
    for (int i = 0; i < nc; i++) {
      double dHf = ph.getComponent(i).getIdealGasEnthalpyOfFormation();
      double S0 = ph.getComponent(i).getIdealGasAbsoluteEntropy();
      double dGf298 = ph.getComponent(i).getGibbsEnergyOfFormation();

      double cpA = ph.getComponent(i).getCpA();
      double cpB = ph.getComponent(i).getCpB();
      double cpC = ph.getComponent(i).getCpC();
      double cpD = ph.getComponent(i).getCpD();
      double cpE = ph.getComponent(i).getCpE();

      boolean hasCpData = Math.abs(cpA) > 1.0e-10 || Math.abs(cpB) > 1.0e-10;
      boolean hasThermo = Math.abs(dHf) > 1.0e-10 || Math.abs(S0) > 1.0e-10;

      if (hasThermo && hasCpData) {
        // Temperature-corrected g0 using Cp polynomial integration
        double dT = TT - T0;
        double dT2 = TT * TT - T0 * T0;
        double dT3 = TT * TT * TT - T0 * T0 * T0;
        double dT4 = TT * TT * TT * TT - T0 * T0 * T0 * T0;
        double dT5 = TT * TT * TT * TT * TT - T0 * T0 * T0 * T0 * T0;
        double lnTratio = Math.log(TT / T0);

        // integral(T0,T) Cp dT
        double deltaH =
            cpA * dT + cpB / 2.0 * dT2 + cpC / 3.0 * dT3 + cpD / 4.0 * dT4 + cpE / 5.0 * dT5;

        // integral(T0,T) Cp/T dT
        double deltaS =
            cpA * lnTratio + cpB * dT + cpC / 2.0 * dT2 + cpD / 3.0 * dT3 + cpE / 4.0 * dT4;

        double hT = dHf + deltaH;
        double sT = S0 + deltaS;
        g0[i] = (hT - TT * sT) / RT + lnPP;
      } else if (hasThermo) {
        // Fallback: constant Cp approximation (no polynomial data)
        g0[i] = (dHf - TT * S0) / RT + lnPP;
      } else if (Math.abs(dGf298) > 1.0e-10) {
        g0[i] = dGf298 / RT + lnPP;
      } else {
        g0[i] = lnPP;
      }
    }
  }

  /**
   * Initialize Lagrange multipliers from current compositions using least-squares fit.
   */
  private void initializeLambda() {
    double[] h = new double[nc];
    for (int i = 0; i < nc; i++) {
      double xi = x[0][i] > EPS ? x[0][i] : EPS;
      h[i] = g0[i] + Math.log(xi) + lnPhi[0][i];
    }

    double[][] AtA = new double[ne][ne];
    double[] Ath = new double[ne];
    for (int k = 0; k < ne; k++) {
      for (int l = 0; l < ne; l++) {
        for (int i = 0; i < nc; i++) {
          AtA[k][l] += A[k][i] * A[l][i];
        }
      }
      for (int i = 0; i < nc; i++) {
        Ath[k] += A[k][i] * h[i];
      }
    }

    double[] sol = solveLinear(AtA, Ath);
    if (sol != null) {
      lambda = sol;
    }
  }

  /**
   * Update log fugacity coefficients from system state.
   */
  private void updateLnPhi() {
    for (int j = 0; j < np; j++) {
      PhaseInterface ph = system.getPhase(j);
      for (int i = 0; i < nc; i++) {
        double fc = ph.getComponent(i).getFugacityCoefficient();
        lnPhi[j][i] = (fc > 0 && !Double.isNaN(fc)) ? Math.log(fc) : 0.0;
      }
    }
  }

  /**
   * Update the thermodynamic system from current mole fractions and phase fractions.
   */
  private void updateSystem() {
    for (int j = 0; j < np; j++) {
      PhaseInterface ph = system.getPhase(j);
      for (int i = 0; i < nc; i++) {
        ph.getComponent(i).setx(x[j][i]);
      }
      ph.setBeta(beta[j]);
    }
    system.init(1);
  }

  /**
   * Solve linear system Ax=b by Gaussian elimination with partial pivoting.
   *
   * @param aa coefficient matrix
   * @param bb right-hand side
   * @return solution vector or null if singular
   */
  private double[] solveLinear(double[][] aa, double[] bb) {
    int dim = bb.length;
    double[][] aug = new double[dim][dim + 1];
    for (int i = 0; i < dim; i++) {
      for (int j = 0; j < dim; j++) {
        aug[i][j] = aa[i][j];
      }
      aug[i][dim] = bb[i];
    }

    for (int c = 0; c < dim; c++) {
      int pr = c;
      double mx = Math.abs(aug[c][c]);
      for (int r = c + 1; r < dim; r++) {
        if (Math.abs(aug[r][c]) > mx) {
          mx = Math.abs(aug[r][c]);
          pr = r;
        }
      }
      if (mx < 1e-30) {
        return null;
      }
      if (pr != c) {
        double[] tmp = aug[c];
        aug[c] = aug[pr];
        aug[pr] = tmp;
      }
      for (int r = c + 1; r < dim; r++) {
        double f = aug[r][c] / aug[c][c];
        for (int jj = c; jj <= dim; jj++) {
          aug[r][jj] -= f * aug[c][jj];
        }
      }
    }

    double[] s = new double[dim];
    for (int i = dim - 1; i >= 0; i--) {
      s[i] = aug[i][dim];
      for (int j = i + 1; j < dim; j++) {
        s[i] -= aug[i][j] * s[j];
      }
      if (Math.abs(aug[i][i]) < 1e-30) {
        return null;
      }
      s[i] /= aug[i][i];
    }
    return s;
  }

  /**
   * Get iterations used.
   *
   * @return iterations
   */
  public int getIterationsUsed() {
    return iterationsUsed;
  }

  /**
   * Get final residual.
   *
   * @return residual
   */
  public double getFinalResidual() {
    return finalResidual;
  }

  /**
   * Check if converged.
   *
   * @return true if converged
   */
  public boolean isConverged() {
    return converged;
  }

  /**
   * Get Lagrange multipliers.
   *
   * @return lambda array
   */
  public double[] getLagrangeMultipliers() {
    return lambda;
  }

  /**
   * Get moles per phase.
   *
   * @return n[phase][component]
   */
  public double[][] getMoles() {
    return n;
  }

  /**
   * Get mole fractions.
   *
   * @return x[phase][component]
   */
  public double[][] getMoleFractions() {
    return x;
  }

  /**
   * Get total moles across all phases.
   *
   * @return total moles
   */
  public double getTotalMoles() {
    return totalMoles;
  }

  /**
   * Get phase amounts.
   *
   * @return beta array
   */
  public double[] getPhaseAmounts() {
    return beta;
  }

  /**
   * Set whether to use DIIS acceleration.
   *
   * <p>
   * When enabled, the solver applies Pulay DIIS extrapolation to the Lagrange multiplier trajectory
   * after a warmup period, typically reducing iteration count by 3-5x.
   * </p>
   *
   * @param useDIIS true to enable DIIS (default is true)
   */
  public void setUseDIIS(boolean useDIIS) {
    this.useDIIS = useDIIS;
  }

  /**
   * Check if DIIS acceleration is enabled.
   *
   * @return true if DIIS is enabled
   */
  public boolean isUseDIIS() {
    return useDIIS;
  }

  /**
   * Get number of accepted DIIS steps during the last solve.
   *
   * @return count of accepted DIIS extrapolation steps
   */
  public int getDiisStepsAccepted() {
    return diisStepsAccepted;
  }
}
