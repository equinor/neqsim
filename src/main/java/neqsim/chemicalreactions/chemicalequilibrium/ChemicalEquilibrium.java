package neqsim.chemicalreactions.chemicalequilibrium;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import Jama.Matrix;
import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * ChemicalEquilibrium class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class ChemicalEquilibrium implements java.io.Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(ChemicalEquilibrium.class);

  // ============ STABILITY CONSTANTS ============
  /** Minimum moles to prevent log(0) and division by zero. */
  private static final double MIN_MOLES = 1e-30;

  /** Maximum iterations allowed for chemical equilibrium solver. */
  private static final int MAX_ITERATIONS = 50;

  /** Counter for consecutive non-improving iterations to detect stagnation. */
  private static final int STAGNATION_LIMIT = 10;

  /**
   * Flag to enable fugacity coefficient derivatives in M_matrix. When true, uses init(3) for
   * derivative calculations and includes dln(fugacity)/dN terms for more accurate Newton steps.
   * Default is false for backward compatibility and performance.
   */
  private boolean useFugacityDerivatives = false;

  SystemInterface system;
  double[] nVector;
  double[] n_mol;
  double d_n_t = 0;
  int NSPEC = 2;
  int NELE = 2;
  double R = ThermodynamicConstantsInterface.R;
  Matrix x_solve;
  double y_solve;
  double n_t = 0.0;
  double agemo = 0;
  double kronDelt = 0;

  ComponentInterface[] components;
  double[][] M_matrix = new double[NSPEC][NSPEC];
  Matrix M_Jama_matrix;
  Matrix A_Jama_matrix;
  Matrix nmu;
  Matrix AMA_matrix; // = new double[NELE][NELE];
  Matrix dn_matrix;
  Matrix AMU_matrix;
  Matrix Alambda_matrix;
  double[] d_n = new double[NSPEC];
  double[] logactivityVec = new double[NSPEC];
  double[] n0;
  double[][] A_matrix;
  double[] chem_ref;
  int waterNumb = 0;
  int upMoles = 0;
  // double chem_pot_dilute[];
  // double chem_pot_pure[];
  double[] b_element;
  Matrix b_matrix;

  Matrix A_solve;
  Matrix b_solve;
  double[] chem_pot;
  Matrix chem_pot_Jama_Matrix;
  int phasenumb = 1;

  /**
   * <p>
   * Constructor for ChemicalEquilibrium.
   * </p>
   *
   * @param A_matrix an array of type double
   * @param b_element an array of type double
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   * @param components an array of {@link neqsim.thermo.component.ComponentInterface} objects
   * @param phaseNum a int
   */
  public ChemicalEquilibrium(double[][] A_matrix, double[] b_element, SystemInterface system,
      ComponentInterface[] components, int phaseNum) {
    this.system = system;
    phasenumb = phaseNum;
    this.A_matrix = A_matrix;
    this.b_element = b_element;
    this.components = components;
    NSPEC = components.length; // Number of Species
    NELE = b_element.length; // Number of elements
    n_mol = new double[components.length];
    logactivityVec = new double[NSPEC];
    this.chem_ref = new double[components.length];
    A_solve = new Matrix(NELE + 1, NELE + 1);
    b_solve = new Matrix(NELE + 1, 1);
    chem_ref = new double[NSPEC];
    chem_pot = new double[NSPEC];
    // chem_pot_dilute = new double[NSPEC];
    // chem_pot_pure = new double[NSPEC];
    M_matrix = new double[NSPEC][NSPEC];
    d_n = new double[NSPEC];

    for (int i = 0; i < components.length; i++) {
      if (components[i].getComponentName().equals("water")) {
        waterNumb = i;
        break;
      }
    }
    system.init(1, phasenumb);
    calcRefPot();
    for (int j = 0; j < NSPEC; j++) {
      d_n[j] = 0;
    }
  }

  /**
   * <p>
   * calcRefPot.
   * </p>
   */
  public void calcRefPot() {
    for (int i = 0; i < components.length; i++) {
      // calculates the reduced chemical potential mu/RT
      this.chem_ref[i] =
          components[i].getReferencePotential() / (R * system.getPhase(phasenumb).getTemperature());
      logactivityVec[i] = 0.0;
      if (components[i].calcActivity()) {
        logactivityVec[i] = system.getPhase(phasenumb).getLogActivityCoefficient(
            components[i].getComponentNumber(), components[waterNumb].getComponentNumber());
        // System.out.println("activity " + Math.exp(logactivityVec[i]) + " " +
        // components[i].getComponentName());
      }
    }
  }

  /**
   * <p>
   * chemSolve.
   * </p>
   */
  public void chemSolve() {
    // Protect against n_t = 0 which would cause division by zero in chem_pot calculation
    n_t = Math.max(MIN_MOLES, system.getPhase(phasenumb).getNumberOfMolesInPhase());

    // If using fugacity derivatives, need init(3) for derivative calculations
    if (useFugacityDerivatives) {
      try {
        system.init(3, phasenumb);
      } catch (Exception ex) {
        logger.debug("Failed to init(3) for derivatives, falling back to simple M_matrix");
      }
    }

    for (int i = 0; i < NSPEC; i++) {
      n_mol[i] = system.getPhase(phasenumb).getComponents()[components[i].getComponentNumber()]
          .getNumberOfMolesInPhase();

      for (int k = 0; k < NSPEC; k++) {
        if (k == i) {
          kronDelt = 1.0;
        } else {
          kronDelt = 0.0;
        }
        // definition of M_matrix changed by Neeraj. Initially only 1st term was
        // included
        // Protect against division by zero using MIN_MOLES
        double molesForDiv = Math.max(MIN_MOLES,
            system.getPhase(phasenumb).getComponents()[components[i].getComponentNumber()]
                .getNumberOfMolesInPhase());
        M_matrix[i][k] = kronDelt / molesForDiv;

        // Add fugacity coefficient derivative if enabled
        // This gives the full Hessian for more accurate Newton steps
        if (useFugacityDerivatives) {
          try {
            int compNumI = components[i].getComponentNumber();
            int compNumK = components[k].getComponentNumber();
            double dfugdN = system.getPhase(phasenumb).getComponent(compNumI).getdfugdn(compNumK);
            if (!Double.isNaN(dfugdN) && !Double.isInfinite(dfugdN)) {
              M_matrix[i][k] += dfugdN;
            }
          } catch (Exception ex) {
            // Derivative not available, use ideal term only
          }
        }

        // System.out.println("dfugdn "
        // +system.getPhase(phasenumb).getComponent(i).logfugcoefdNi(this.system.getPhase(phasenumb),
        // i));
        // if (i == k) System.out.println("n "
        // +system.getPhase(phasenumb).getComponents()[components[i].getComponentNumber()].getNumberOfMolesInPhase()
        // );
      }
    }
    // printComp();

    M_Jama_matrix = new Matrix(M_matrix);
    A_Jama_matrix = new Matrix(A_matrix);
    b_matrix = new Matrix(b_element, 1);

    // M_Jama_matrix.print(10, 10);
    // Following 5 statements added by Neeraj
    // A_Jama_matrix.print(5,2);
    // ystem.out.println("rank of A "+A_Jama_matrix.rank());
    // System.out.println("number of rows in A "+A_Jama_matrix.getRowDimension());
    // if(A_Jama_matrix.rank()<A_Jama_matrix.getRowDimension())
    // System.out.println("Rank of Matrix A low: Numerical errors may occur ");
    double logactivity = 0.0;
    for (int i = 0; i < NSPEC; i++) {
      logactivity = logactivityVec[i];
      // system.getPhase(phasenumb).getActivityCoefficient(components[i].getComponentNumber(),
      // components[waterNumb].getComponentNumber());

      // calculates the reduced chemical potential mu/RT
      // Protect against log(0) by ensuring minimum moles
      double molesInPhase = Math.max(MIN_MOLES,
          system.getPhase(phasenumb).getComponents()[components[i].getComponentNumber()]
              .getNumberOfMolesInPhase());
      chem_pot[i] = chem_ref[i] + Math.log(molesInPhase) - Math.log(n_t) + logactivity;
      // System.out.println("chem ref pot " + chem_pot[i]);
    }

    chem_pot_Jama_Matrix = new Matrix(chem_pot, 1);

    AMA_matrix = A_Jama_matrix.times(M_Jama_matrix.inverse().times(A_Jama_matrix.transpose()));
    AMU_matrix =
        A_Jama_matrix.times(M_Jama_matrix.inverse().times(chem_pot_Jama_Matrix.transpose()));
    Matrix nmol = new Matrix(n_mol, 1);
    nmu = nmol.times(chem_pot_Jama_Matrix.transpose());
    // AMA_matrix.pr
    // Added by Neeraj
    // Matrix bm_matrix = (A_Jama_matrix.times(nmol.transpose()).transpose());
    // ((b_matrix.minus(bm_matrix)).times(R*system.getTemperature()).transpose()).print(10,10);
    // AMU_matrix.print(20,20);

    A_solve.setMatrix(0, NELE - 1, 0, NELE - 1, AMA_matrix);
    A_solve.setMatrix(0, NELE - 1, NELE, NELE, b_matrix.transpose());
    A_solve.setMatrix(NELE, NELE, 0, NELE - 1, b_matrix);
    A_solve.set(NELE, NELE, 0.0);

    // A_solve.print(10,20);
    // System.out.println("Rank of A_solve "+A_solve.rank());
    // Term subtracted from AMU_matrix -- Neeraj
    // b_solve.setMatrix(0,NELE-1,0,0,
    // AMU_matrix.minus((b_matrix.minus(bm_matrix)).times(R*system.getTemperature()).transpose()));
    // Commented out by Neeraj
    b_solve.setMatrix(0, NELE - 1, 0, 0, AMU_matrix);
    b_solve.setMatrix(NELE, NELE, 0, 0, nmu);
    // b_solve.print(10,5);
    // System.out.println("det "+A_solve.det());

    // y_solve added by Neeraj
    // M_Jama_matrix.print(5,5);
    y_solve = A_solve.det();
    // System.out.println("Determinant "+y_solve);
    if ((y_solve < 1e-38 && y_solve > -1e-38) || y_solve < -1e70) {
      // A_solve.print(5,5);
      y_solve = AMA_matrix.det();
      // System.out.println("AMA det "+y_solve);
      y_solve = A_solve.rank();
      // System.out.println("Rank " + y_solve);
      // M_Jama_matrix.print(5,5);
      // b_solve.print(5,5);
      // System.out.println("det A " + A_solve.rank());
    }

    // try catch block added by Neeraj
    // Enhanced with SVD pseudo-inverse fallback for rank-deficient matrices
    try {
      x_solve = A_solve.solve(b_solve);

      // Check if solution is valid (no NaN or Inf)
      boolean validSolution = true;
      for (int i = 0; i <= NELE && validSolution; i++) {
        double val = x_solve.get(i, 0);
        if (Double.isNaN(val) || Double.isInfinite(val)) {
          validSolution = false;
        }
      }

      if (!validSolution) {
        // Try pseudo-inverse for numerically unstable cases
        x_solve = solveLeastSquares(A_solve, b_solve);
      }
    } catch (Exception ex) {
      // Matrix is singular or near-singular, use pseudo-inverse
      try {
        x_solve = solveLeastSquares(A_solve, b_solve);
      } catch (Exception ex2) {
        logger.error("Both regular and least-squares solve failed: " + ex2.getMessage());
        x_solve = new Matrix(NELE + 1, 1); // Zero solution as fallback
      }
    }
    // d_n_t = x_solve.get(NELE,0)*n_t;

    // Equation 3.115
    dn_matrix = M_Jama_matrix.inverse()
        .times((A_Jama_matrix.transpose().times(x_solve.getMatrix(0, NELE - 1, 0, 0)))
            .minus(chem_pot_Jama_Matrix.transpose()))
        .plus(new Matrix(n_mol, 1).transpose().times(x_solve.get(NELE, 0)));
    d_n = dn_matrix.transpose().getArray()[0];
  }

  /**
   * <p>
   * updateMoles.
   * </p>
   */
  public void updateMoles() {
    upMoles++;
    // double changeMoles = 0.0;
    for (int i = 0; i < components.length; i++) {
      if (n_mol[i] > 0) {
        system.addComponent(components[i].getComponentNumber(),
            (n_mol[i]
                - system.getPhase(phasenumb).getComponents()[components[i].getComponentNumber()]
                    .getNumberOfMolesInPhase()),
            phasenumb);
      } else {
        system.addComponent(components[i].getComponentNumber(),
            (-0.99 * system.getPhase(phasenumb).getComponents()[components[i].getComponentNumber()]
                .getNumberOfMolesInPhase()),
            phasenumb);
      }

      // changeMoles += n_mol[i] -
      // system.getPhase(phasenumb).getComponents()[components[i].getComponentNumber()]
      // .getNumberOfMolesInPhase();
    }
    system.initBeta(); // this was added for mass trans calc
    system.init_x_y();
  }

  /**
   * <p>
   * solve.
   * </p>
   *
   * @return a boolean
   */
  public boolean solve() {
    double error = 1e10;
    double errOld = 1e10;
    double thisError = 0;
    int p = 0;
    double maxError = 1e-8;
    upMoles = 0;
    int stagnationCount = 0;
    double bestError = Double.MAX_VALUE;

    try {
      do {
        p++;
        errOld = error;
        error = 0.0;
        this.chemSolve();

        // Early exit if chemSolve produced invalid results
        if (dn_matrix == null) {
          logger.warn("Chemical equilibrium: dn_matrix is null at iteration " + p);
          break;
        }

        double step1 = step();

        for (int i = 0; i < NSPEC; i++) {
          double molesInPhase =
              system.getPhase(phasenumb).getComponents()[components[i].getComponentNumber()]
                  .getNumberOfMolesInPhase();

          // Skip if moles are too small to avoid division issues
          if (molesInPhase < MIN_MOLES) {
            continue;
          }

          double dnValue = dn_matrix.get(i, 0);

          // Check for NaN or Infinite values
          if (Double.isNaN(dnValue) || Double.isInfinite(dnValue)) {
            logger.debug("Chemical equilibrium: NaN/Inf detected in dn_matrix at iteration " + p);
            error = Double.NaN;
            break;
          }

          if (Math.abs(dnValue) / molesInPhase > 1e-15) {
            thisError = Math.abs(dnValue) / molesInPhase;
            error += Math.abs(thisError);
            n_mol[i] = dnValue * step1 + molesInPhase;
          }
        }

        // Exit immediately if NaN detected
        if (Double.isNaN(error) || Double.isInfinite(error)) {
          logger.debug("Chemical equilibrium: NaN/Inf error at iteration " + p + ", P="
              + system.getPressure() + " bara, T=" + system.getTemperature() + " K");
          break;
        }

        // Track best error and detect stagnation
        if (error < bestError) {
          bestError = error;
          stagnationCount = 0;
        } else {
          stagnationCount++;
        }

        // Exit if solver is stagnating (not making progress)
        if (stagnationCount >= STAGNATION_LIMIT) {
          logger.debug(
              "Chemical equilibrium: stagnation detected at iteration " + p + ", error=" + error);
          break;
        }

        if (error <= errOld) {
          updateMoles();
          system.init(1, phasenumb);
          calcRefPot();
        }

        // Gradually relax tolerance for difficult convergence cases
        if (p > 15) {
          maxError *= 1.5;
        }
      } while (((errOld > maxError && Math.abs(error) > maxError) && p < MAX_ITERATIONS) || p < 2);
    } catch (Exception ex) {
      logger.error("Chemical equilibrium solver exception: " + ex.getMessage(), ex);
      return false;
    }

    if (p >= MAX_ITERATIONS) {
      logger.debug("Chemical equilibrium: max iterations (" + MAX_ITERATIONS + ") reached"
          + ", error=" + error + ", P=" + system.getPressure() + " bara" + ", T="
          + system.getTemperature() + " K");
    }

    // Always try to reinitialize system even if convergence failed
    try {
      system.init(1, phasenumb);
    } catch (Exception ex) {
      logger.debug("Chemical equilibrium: failed to reinitialize system: " + ex.getMessage());
    }

    // Ensure ionic species have physically reasonable values
    // Do this regardless of convergence status, as the solver may converge
    // with unrealistic ion concentrations
    enforceMinimumIonConcentrations();

    boolean converged = !Double.isNaN(error) && !Double.isInfinite(error) && error < maxError;
    return converged;
  }

  /**
   * Enforce minimum physically reasonable concentrations for ionic species.
   * 
   * <p>
   * When the chemical equilibrium solver fails to converge, ionic species like H3O+ and OH- can be
   * left at unrealistically low values. This method checks the water auto-ionization equilibrium
   * (Kw = [H3O+][OH-]) and corrects unrealistic values while respecting legitimate acidic or
   * alkaline conditions.
   * </p>
   * 
   * <p>
   * The method only intervenes when both H3O+ and OH- are unrealistically low (violating Kw), not
   * when the solution is legitimately acidic (high H3O+, low OH-) or alkaline (low H3O+, high OH-).
   * </p>
   */
  private void enforceMinimumIonConcentrations() {
    // Find H3O+, OH-, and water indices
    int h3oIndex = -1;
    int ohIndex = -1;
    int waterIndex = -1;

    for (int i = 0; i < components.length; i++) {
      String name = components[i].getComponentName();
      if (name.equals("H3O+")) {
        h3oIndex = i;
      } else if (name.equals("OH-")) {
        ohIndex = i;
      } else if (name.equals("water")) {
        waterIndex = i;
      }
    }

    if (h3oIndex < 0 || waterIndex < 0) {
      return; // No H3O+ or water in system
    }

    double totalMoles = system.getPhase(phasenumb).getNumberOfMolesInPhase();
    if (totalMoles <= 0) {
      return;
    }

    double currentH3OMoles =
        system.getPhase(phasenumb).getComponents()[components[h3oIndex].getComponentNumber()]
            .getNumberOfMolesInPhase();
    double currentH3OMoleFraction = currentH3OMoles / totalMoles;

    double currentOHMoles = 0;
    double currentOHMoleFraction = 0;
    if (ohIndex >= 0) {
      currentOHMoles =
          system.getPhase(phasenumb).getComponents()[components[ohIndex].getComponentNumber()]
              .getNumberOfMolesInPhase();
      currentOHMoleFraction = currentOHMoles / totalMoles;
    }

    // Water auto-ionization: Kw = [H3O+][OH-] ≈ 10^-14 at 25°C
    // In mole fraction terms for aqueous phase (~55.5 mol/L water):
    // x(H3O+) * x(OH-) ≈ (10^-14) / (55.5)^2 ≈ 3.2e-18
    // Minimum physically reasonable: either x(H3O+) or x(OH-) should be > ~10^-15
    // for extreme pH cases (pH 0-1 or pH 13-14)

    double minIonMoleFraction = 1e-15; // Absolute minimum for any ion
    double neutralH3OMoleFraction = 1e-9; // ~pH 7 in mole fraction terms

    // Check if the water equilibrium is violated (both ions unrealistically low)
    boolean h3oTooLow = currentH3OMoleFraction < minIonMoleFraction;
    boolean ohTooLow = ohIndex >= 0 && currentOHMoleFraction < minIonMoleFraction;

    // Only intervene if BOTH are too low - this indicates solver failure
    // If only one is low, it could be a legitimate acidic/alkaline solution
    if (h3oTooLow && ohTooLow) {
      // Both are unrealistically low - solver failed to establish water equilibrium
      // Set to neutral pH as a reasonable default
      double targetH3OMoles = neutralH3OMoleFraction * totalMoles;
      double molesToAdd = targetH3OMoles - currentH3OMoles;

      if (molesToAdd > 0) {
        system.addComponent(components[h3oIndex].getComponentNumber(), molesToAdd, phasenumb);

        // Set OH- to maintain Kw ≈ 10^-14 (x_H3O * x_OH ≈ 3.2e-18)
        if (ohIndex >= 0) {
          double targetOHMoleFraction = 3.2e-18 / neutralH3OMoleFraction;
          double targetOHMoles = targetOHMoleFraction * totalMoles;
          if (targetOHMoles > currentOHMoles) {
            system.addComponent(components[ohIndex].getComponentNumber(),
                targetOHMoles - currentOHMoles, phasenumb);
          }
        }

        reinitializeAfterIonAdjustment();
        logger.debug("Chemical equilibrium: enforced water equilibrium (both ions were too low)"
            + ", old x(H3O+)=" + currentH3OMoleFraction + ", new x(H3O+)="
            + neutralH3OMoleFraction);
      }
    } else if (h3oTooLow && !ohTooLow) {
      // Only H3O+ is too low but OH- is reasonable - could be alkaline solution
      // Calculate what H3O+ should be based on Kw and current OH-
      // x_H3O = Kw_molfrac / x_OH ≈ 3.2e-18 / x_OH
      double targetH3OMoleFraction = 3.2e-18 / currentOHMoleFraction;

      // Only enforce if H3O+ is MUCH lower than what Kw predicts (factor of 1000)
      if (currentH3OMoleFraction < targetH3OMoleFraction * 1e-3) {
        double targetH3OMoles = targetH3OMoleFraction * totalMoles;
        double molesToAdd = targetH3OMoles - currentH3OMoles;
        if (molesToAdd > 0) {
          system.addComponent(components[h3oIndex].getComponentNumber(), molesToAdd, phasenumb);
          reinitializeAfterIonAdjustment();
          logger
              .debug("Chemical equilibrium: adjusted H3O+ for alkaline solution" + ", old x(H3O+)="
                  + currentH3OMoleFraction + ", new x(H3O+)=" + targetH3OMoleFraction);
        }
      }
    }
    // If OH- is too low but H3O+ is reasonable (acidic solution), don't intervene
    // The solution is legitimately acidic
  }

  /**
   * Reinitialize the system after ion concentration adjustment.
   */
  private void reinitializeAfterIonAdjustment() {
    try {
      system.initBeta();
      system.init_x_y();
      system.init(1, phasenumb);
    } catch (Exception ex) {
      logger.debug(
          "Chemical equilibrium: failed to reinitialize after ion adjustment: " + ex.getMessage());
    }
  }

  /**
   * Check if fugacity coefficient derivatives are used in the M_matrix calculation.
   *
   * @return true if fugacity derivatives are enabled
   */
  public boolean isUseFugacityDerivatives() {
    return useFugacityDerivatives;
  }

  /**
   * Enable or disable fugacity coefficient derivatives in M_matrix calculation.
   *
   * <p>
   * When enabled, the solver uses init(3, phase) to calculate fugacity derivatives and includes
   * dln(fugacity)/dN terms in the M_matrix for more accurate Newton steps. This can improve
   * convergence for non-ideal mixtures but is computationally more expensive.
   * </p>
   *
   * @param useFugacityDerivatives true to enable, false to disable
   */
  public void setUseFugacityDerivatives(boolean useFugacityDerivatives) {
    this.useFugacityDerivatives = useFugacityDerivatives;
  }

  /**
   * <p>
   * printComp.
   * </p>
   */
  public void printComp() {
    for (int j = 0; j < NSPEC; j++) {
      System.out.println(" SVAR : " + n_mol[j]);
      double activity = system.getPhase(phasenumb).getActivityCoefficient(
          components[j].getComponentNumber(), components[waterNumb].getComponentNumber());
      System.out.println("act " + activity + " comp " + components[j].getComponentName());
    }
  }

  /**
   * <p>
   * getMoles.
   * </p>
   *
   * @return an array of type double
   */
  public double[] getMoles() {
    return n_mol;
  }

  /**
   * <p>
   * step.
   * </p>
   *
   * @return a double
   */
  public double step() {
    double step = 1.0;
    int i;
    int check = 0;
    double[] n_omega = new double[NSPEC];
    double[] chem_pot_omega = new double[NSPEC];
    double[] chem_pot = new double[NSPEC];
    double G_1 = 0.0;

    double G_0 = 0.0;
    for (i = 0; i < NSPEC; i++) {
      n_omega[i] = n_mol[i] + d_n[i];
      // System.out.println("nomega " + n_omega[i] );
      if (n_omega[i] < 0) {
        check = i;

        step = innerStep(i, n_omega, check, step, true);
        // System.out.println("step2 ... " + step);
        return step;
      } else {
        // chem_pot_omega[i] = R*T*(chem_ref[i]+ Math.log(n_omega[i]/n_t) +
        // Math.log(system.getPhases()[1].getComponents()[components[i].getComponentNumber()].getFugacityCoefficient()
        // / chem_pot_pure[i]));
        // chem_pot[i] = R*T*(chem_ref[i] + Math.log(n_mol[i]/n_t)+
        // Math.log(system.getPhases()[1].getComponents()[components[i].getComponentNumber()].getFugacityCoefficient()
        // / chem_pot_pure[i]));

        if (system.getPhase(phasenumb).getComponents()[components[i].getComponentNumber()]
            .getReferenceStateType().equals("solvent")) {
          // Protect against log(0) with MIN_MOLES
          double molesInPhase = Math.max(MIN_MOLES,
              system.getPhase(phasenumb).getComponents()[components[i].getComponentNumber()]
                  .getNumberOfMolesInPhase());
          chem_pot[i] = R * system.getPhase(phasenumb).getTemperature()
              * (chem_ref[i] + Math.log(molesInPhase) - Math.log(n_t) + logactivityVec[i]);
          // system.getPhase(phasenumb).getActivityCoefficient(components[i].getComponentNumber(),components[waterNumb].getComponentNumber())));
          // System.out.println("solvent activ: "+ i + " " +
          // system.getPhases()[1].getComponents()[components[i].getComponentNumber()].getFugacityCoefficient()
          // / chem_pot_pure[i]);
        } else {
          // Protect against log(0) with MIN_MOLES
          double molesInPhase = Math.max(MIN_MOLES,
              system.getPhase(phasenumb).getComponents()[components[i].getComponentNumber()]
                  .getNumberOfMolesInPhase());
          chem_pot[i] = R * system.getPhase(phasenumb).getTemperature()
              * (chem_ref[i] + Math.log(molesInPhase) - Math.log(n_t) + logactivityVec[i]);
          // system.getPhase(phasenumb).getActivityCoefficient(components[i].getComponentNumber(),components[waterNumb].getComponentNumber())));
          // System.out.println("solute activ : " + i + " " +
          // system.getPhases()[1].getComponents()[components[i].getComponentNumber()].getFugacityCoefficient()
          // / chem_pot_dilute[i]);
        }
        // Protect n_omega against log(0) with MIN_MOLES
        double n_omega_safe = Math.max(MIN_MOLES, n_omega[i]);
        chem_pot_omega[i] = R * system.getPhase(phasenumb).getTemperature()
            * (chem_ref[i] + Math.log(n_omega_safe) - Math.log(n_t) + logactivityVec[i]);
      }
    }
    // Added by Neeraj
    Alambda_matrix = A_Jama_matrix.transpose().times(x_solve.getMatrix(0, NELE - 1, 0, 0));

    G_1 = 0.0;
    for (i = 0; i < NSPEC; i++) {
      // G_1 += chem_pot_omega[i] * d_n[i];
      // Added by Neeraj
      // Protect against division by zero
      double n_omega_safe = Math.max(MIN_MOLES, n_omega[i]);
      G_1 += (chem_pot_omega[i] - Alambda_matrix.get(i, 0)) * d_n[i] * (1 / n_omega_safe - 1 / n_t);
    }
    // System.out.println("G1 " +G_1);

    if (G_1 > 0) {
      G_0 = 0.0;
      for (i = 0; i < NSPEC; i++) {
        // G_0 += chem_pot[i]*d_n[i];
        // Added by Neeraj
        // Protect against division by zero
        double molesInPhase = Math.max(MIN_MOLES,
            system.getPhase(phasenumb).getComponents()[components[i].getComponentNumber()]
                .getNumberOfMolesInPhase());
        G_0 += (chem_pot[i] - Alambda_matrix.get(i, 0)) * d_n[i] * (1 / molesInPhase - 1 / n_t);
        // G_0 +=
        // (chem_pot[i]-Alambda_matrix.get(i,0))*d_n[i]*(M_Jama_matrix.get(i,i)-1/n_t);
      }
      // Protect against division by zero when G_0 ≈ G_1
      double denominator = G_0 - G_1;
      if (Math.abs(denominator) > 1e-30) {
        step = G_0 / denominator;
      }
      // System.out.println("step G " + step);
    }

    step = innerStep(i, n_omega, check, step, false);
    // System.out.println("step ... " + step);

    // BUG FIX: Return the calculated step instead of always 1.0
    // The calculated step provides damping to prevent overshooting during Newton iterations
    return step;
  }

  /**
   * <p>
   * innerStep.
   * </p>
   *
   * @param i a int
   * @param n_omega an array of type double
   * @param check a int
   * @param step a double
   * @param test a boolean
   * @return a double
   */
  public double innerStep(int i, double[] n_omega, int check, double step, boolean test) {
    if (test) {
      agemo = (-n_mol[i] / d_n[i]) * (1.0 - 0.03);

      for (i = check; i < NSPEC; i++) {
        n_omega[i] = n_mol[i] + d_n[i];

        if (n_omega[i] < 0) {
          step = (-n_mol[i] / d_n[i]) * (1.0 - 0.03);
          if (step < agemo) {
            agemo = step;
          }
        }
      }

      step = agemo;

      if (step > 1) {
        step = 1.0;
      }
    }
    return step;
  }

  // Method added by Neeraj
  /*
   * public double step(){ double step=1.0; int i, check=0; double[] F = new double[NSPEC]; double[]
   * F_omega = new double[NSPEC]; double[] chem_pot = new double[NSPEC]; double[] n_omega = new
   * double[NSPEC];
   *
   * Matrix F_matrix, F_omega_matrix, fs_matrix, f_matrix, f_omega_matrix; double fs,f,f_omega;
   *
   * for(i = 0;i<NSPEC;i++){ n_omega[i] = n_mol[i]+d_n[i]; if (n_omega[i]<0){ check = i; return
   * step; } else { if(system.getPhase(phasenumb).getComponents()[components[i].
   * getComponentNumber()].getReferenceStateType().equals("solvent")){ F[i] =
   * (chem_ref[i]/(R*system.getPhase(phasenumb).getTemperature()) +
   * Math.log(system.getPhase(phasenumb).getComponents()[components[i].
   * getComponentNumber()].getNumberOfMolesInPhase()) - Math.log(n_t) + Math.log(activityVec[i])); }
   * else{ F[i] = (chem_ref[i]/(R*system.getPhase(phasenumb).getTemperature()) +
   * Math.log(system.getPhase(phasenumb).getComponents()[components[i].
   * getComponentNumber()].getNumberOfMolesInPhase()) - Math.log(n_t) + Math.log(activityVec[i])); }
   * double temp = (chem_ref[i]/(R*system.getPhase(phasenumb).getTemperature()) +
   * Math.log(n_omega[i]) - Math.log(n_t) + Math.log(activityVec[i]));
   * System.out.println("temp "+activityVec[i]);
   * system.addComponent(components[i].getComponentNumber(), d_n[i], phasenumb); calcRefPot();
   * F_omega[i] = (chem_ref[i]/(R*system.getPhase(phasenumb).getTemperature()) +
   * Math.log(n_omega[i]) - Math.log(n_t) + Math.log(activityVec[i]));
   * System.out.println("F "+activityVec[i]);
   * system.addComponent(components[i].getComponentNumber(), -d_n[i], phasenumb); calcRefPot(); } }
   *
   * F_matrix = new Matrix(F,1); //F_matrix.print(5,5); F_omega_matrix = new Matrix(F_omega,1);
   *
   * //F_matrix = F_matrix.minus((A_Jama_matrix.transpose().times(x_solve.getMatrix(0,NELE-1,0,
   * 0))).transpose()); //F_omega_matrix =
   * F_omega_matrix.minus((A_Jama_matrix.transpose().times(x_solve.getMatrix(0,
   * NELE-1,0,0))).transpose());
   *
   * fs_matrix = F_matrix.transpose().times(F_matrix); fs = (-1)*fs_matrix.get(0,0); f_matrix =
   * F_matrix.times(F_matrix.transpose()); f = 0.5*f_matrix.get(0,0); f_omega_matrix =
   * F_omega_matrix.times(F_omega_matrix.transpose()); f_omega = 0.5*f_omega_matrix.get(0,0);
   *
   * step = (-1)*fs/(2*(f_omega-f-fs)); //System.out.println("f "+f);
   * //System.out.println("f_omega "+f_omega); //System.out.println("fs "+fs);
   * //System.out.println("step " + step); //if (step > 0.5) step = 0.5; return step; }
   */

  /**
   * Solve least-squares problem using SVD pseudo-inverse.
   * 
   * <p>
   * For rank-deficient or ill-conditioned matrices, this provides a more robust solution than
   * direct inversion. Uses SVD decomposition: A = U * S * V^T, then x = V * S^(-1) * U^T * b.
   * </p>
   *
   * @param A the coefficient matrix
   * @param b the right-hand side vector
   * @return the least-squares solution x
   */
  private Matrix solveLeastSquares(Matrix A, Matrix b) {
    Jama.SingularValueDecomposition svd = A.svd();
    Matrix U = svd.getU();
    Matrix S = svd.getS();
    Matrix V = svd.getV();

    // Compute pseudo-inverse of S (diagonal matrix)
    int n = S.getColumnDimension();
    double tol = 1e-12 * svd.norm2(); // Tolerance for singular values
    Matrix Sinv = new Matrix(n, n);
    for (int i = 0; i < n; i++) {
      double sval = S.get(i, i);
      if (Math.abs(sval) > tol) {
        Sinv.set(i, i, 1.0 / sval);
      } else {
        Sinv.set(i, i, 0.0); // Truncate small singular values
      }
    }

    // x = V * Sinv * U^T * b
    return V.times(Sinv.times(U.transpose().times(b)));
  }
}
