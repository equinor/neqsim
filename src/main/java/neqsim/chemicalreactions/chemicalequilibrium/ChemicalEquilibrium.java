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
    n_t = system.getPhase(phasenumb).getNumberOfMolesInPhase();

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
        M_matrix[i][k] = kronDelt
            / system.getPhase(phasenumb).getComponents()[components[i].getComponentNumber()]
                .getNumberOfMolesInPhase();
        // +system.getPhase(phasenumb).getComponent(i).logfugcoefdNi(system.getPhase(phasenumb),k);

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
      chem_pot[i] = chem_ref[i]
          + Math.log(system.getPhase(phasenumb).getComponents()[components[i].getComponentNumber()]
              .getNumberOfMolesInPhase())
          - Math.log(n_t) + logactivity;
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

    // Check for near-singular matrix and apply regularization if needed
    boolean needsRegularization = (y_solve < 1e-38 && y_solve > -1e-38) || y_solve < -1e70
        || Double.isNaN(y_solve) || Double.isInfinite(y_solve);

    if (needsRegularization) {
      // Apply Tikhonov regularization to stabilize the matrix
      double regParam = 1e-10;
      for (int reg = 0; reg < NELE; reg++) {
        A_solve.set(reg, reg, A_solve.get(reg, reg) + regParam);
      }
      y_solve = A_solve.det();
    }

    // try catch block added by Neeraj
    try {
      x_solve = A_solve.solve(b_solve);

      // Validate the solution - check for NaN/Inf
      boolean validSolution = true;
      for (int row = 0; row <= NELE; row++) {
        if (Double.isNaN(x_solve.get(row, 0)) || Double.isInfinite(x_solve.get(row, 0))) {
          validSolution = false;
          break;
        }
      }

      if (!validSolution) {
        // Use a small perturbation approach - set Lagrange multipliers to zero
        for (int row = 0; row <= NELE; row++) {
          x_solve.set(row, 0, 0.0);
        }
      }
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
      // Initialize x_solve to zeros as fallback
      x_solve = new Matrix(NELE + 1, 1);
    }
    // d_n_t = x_solve.get(NELE,0)*n_t;

    // Equation 3.115 from Smith & Missen
    // dn = M^{-1} * (A^T * pi - mu) + n * s
    // where pi are Lagrange multipliers and s is the scaling factor
    try {
      dn_matrix = M_Jama_matrix.inverse()
          .times((A_Jama_matrix.transpose().times(x_solve.getMatrix(0, NELE - 1, 0, 0)))
              .minus(chem_pot_Jama_Matrix.transpose()))
          .plus(new Matrix(n_mol, 1).transpose().times(x_solve.get(NELE, 0)));

      // Validate dn_matrix - limit extreme changes
      double maxRelativeChange = 10.0; // Maximum 1000% change in one step
      for (int i = 0; i < NSPEC; i++) {
        double dn = dn_matrix.get(i, 0);
        if (Double.isNaN(dn) || Double.isInfinite(dn)) {
          dn_matrix.set(i, 0, 0.0);
        } else if (n_mol[i] > 1e-20 && Math.abs(dn) > maxRelativeChange * n_mol[i]) {
          // Limit extreme steps
          dn_matrix.set(i, 0, Math.signum(dn) * maxRelativeChange * n_mol[i]);
        }
      }

      d_n = dn_matrix.transpose().getArray()[0];
    } catch (Exception ex) {
      logger.error("Error in dn_matrix calculation: " + ex.getMessage());
      // Set zero step as fallback
      dn_matrix = new Matrix(NSPEC, 1);
      d_n = new double[NSPEC];
    }
  }

  /**
   * <p>
   * updateMoles.
   * </p>
   */
  public void updateMoles() {
    upMoles++;
    // Minimum mole threshold to prevent numerical issues with log calculations
    double minMoles = 1e-20;
    // double changeMoles = 0.0;
    for (int i = 0; i < components.length; i++) {
      double currentMoles =
          system.getPhase(phasenumb).getComponents()[components[i].getComponentNumber()]
              .getNumberOfMolesInPhase();
      double targetMoles = n_mol[i];

      // Ensure target moles is above minimum threshold
      if (targetMoles < minMoles) {
        targetMoles = minMoles;
      }

      // Only update if there's a meaningful change
      double deltaMoles = targetMoles - currentMoles;
      if (Math.abs(deltaMoles) > 1e-30) {
        system.addComponent(components[i].getComponentNumber(), deltaMoles, phasenumb);
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
   * Solves the chemical equilibrium using the Lagrangian method from Smith & Missen with
   * Greiner-Rand step size control.
   *
   * @return a boolean indicating if convergence was achieved
   */
  public boolean solve() {
    double error = 1e10;
    double errOld = 1e10;
    int p = 0;
    double maxError = 1e-6;
    double maxErrorRelaxed = maxError;
    upMoles = 0;
    int stagnationCount = 0;

    // Damping factor - start with moderate step and adjust
    double damping = 0.5;

    try {
      do {
        p++;
        errOld = error;
        this.chemSolve();

        // Calculate equilibrium residual: ||μ - A^T*π|| / NSPEC
        // At equilibrium, each species should satisfy: μ_i = sum_j(A_ji * π_j)
        error = calcEquilibriumResidual();

        // Get the step size from the Greiner-Rand method
        double step1 = step();

        // Validate and clamp step
        if (Double.isNaN(step1) || Double.isInfinite(step1) || step1 <= 0) {
          step1 = 0.1;
        } else if (step1 > 1.0) {
          step1 = 1.0;
        }

        // Apply damping for robustness
        step1 *= damping;
        if (step1 < 1e-10) {
          step1 = 1e-10;
        }

        // Line search: try progressively smaller steps if needed
        boolean stepAccepted = false;
        int lineSearchIter = 0;
        double[] n_mol_new = new double[NSPEC];
        double newError = error;

        // Save the original moles before trying any steps (BUG FIX: must save BEFORE the loop)
        double[] n_mol_original = new double[NSPEC];
        for (int i = 0; i < NSPEC; i++) {
          n_mol_original[i] = system.getPhase(phasenumb)
              .getComponent(components[i].getComponentNumber()).getNumberOfMolesInPhase();
        }

        while (!stepAccepted && lineSearchIter < 10) {
          lineSearchIter++;

          // Calculate new moles with step FROM ORIGINAL (not current, which may have been modified)
          for (int i = 0; i < NSPEC; i++) {
            double dn = dn_matrix.get(i, 0);
            n_mol_new[i] = n_mol_original[i] + dn * step1;

            // Ensure non-negative moles with minimum threshold
            if (n_mol_new[i] < 1e-20) {
              n_mol_new[i] = 1e-20;
            }
          }

          // Verify element balance before accepting step
          boolean elementBalanceOK = checkElementBalance(n_mol_new);

          if (elementBalanceOK) {
            // Tentatively accept and check if error decreases
            for (int i = 0; i < NSPEC; i++) {
              n_mol[i] = n_mol_new[i];
            }
            updateMoles();
            system.init(1, phasenumb);
            calcRefPot();

            // Recalculate error with new moles
            this.chemSolve();
            newError = calcEquilibriumResidual();

            // Accept if error decreased or we've tried enough times
            if (newError < error * 1.5 || lineSearchIter >= 6) {
              stepAccepted = true;
              error = newError;
            } else {
              // Revert to original moles before trying smaller step
              for (int i = 0; i < NSPEC; i++) {
                n_mol[i] = n_mol_original[i];
              }
              updateMoles();
              system.init(1, phasenumb);
              calcRefPot();
              step1 *= 0.5;
            }
          } else {
            // Element balance violated - reduce step size
            step1 *= 0.5;
          }
        }

        if (stepAccepted) {
          // Adaptive damping based on convergence behavior
          if (error > errOld * 0.95 && p > 3) {
            stagnationCount++;
            damping *= 0.9;
            if (damping < 0.05) {
              damping = 0.05;
            }
          } else if (error < errOld * 0.5) {
            // Good progress - increase damping
            damping = Math.min(0.9, damping * 1.2);
            stagnationCount = 0;
          } else if (error < errOld) {
            stagnationCount = 0;
          }
        } else {
          // Could not find acceptable step - increase stagnation counter
          stagnationCount++;
          damping *= 0.8;
          if (damping < 0.05) {
            damping = 0.05;
          }
        }

        // If stagnating for too long, break out
        if (stagnationCount > 30) {
          break;
        }

        // Relax tolerance after many iterations
        if (p == 80) {
          maxErrorRelaxed = maxError * 10;
        }
        if (p == 120) {
          maxErrorRelaxed = maxError * 100;
        }

      } while ((error > maxErrorRelaxed && p < 150) || p < 3);
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
      return false;
    }

    if (p >= 145) {
      logger.debug("iter " + p + " residual " + error + " P " + system.getPressure());
    }

    system.init(1, phasenumb);
    return error < maxError * 10; // Allow 10x tolerance for "converged" status
  }

  /**
   * Calculates the equilibrium residual based on the KKT conditions. At equilibrium: μ_i = A^T * π
   * for all species The residual is ||μ - A^T*π|| normalized by number of species.
   *
   * @return the normalized equilibrium residual
   */
  private double calcEquilibriumResidual() {
    // Check all required matrices are initialized
    if (x_solve == null || chem_pot == null || A_Jama_matrix == null || NSPEC <= 0) {
      return 1e10;
    }

    double residual = 0.0;
    try {
      // Calculate A^T * π (Lagrange multipliers)
      Matrix AtPi = A_Jama_matrix.transpose().times(x_solve.getMatrix(0, NELE - 1, 0, 0));

      for (int i = 0; i < NSPEC; i++) {
        double diff = chem_pot[i] - AtPi.get(i, 0);
        // Guard against NaN/Inf in chemical potentials
        if (Double.isNaN(diff) || Double.isInfinite(diff)) {
          return 1e10;
        }
        residual += diff * diff;
      }
      residual = Math.sqrt(residual / NSPEC);

      // Guard against NaN result
      if (Double.isNaN(residual) || Double.isInfinite(residual)) {
        return 1e10;
      }
    } catch (Exception ex) {
      residual = 1e10;
    }

    return residual;
  }

  /**
   * Checks if element balance is maintained within tolerance. The element balance constraint is A *
   * n = b.
   * 
   * @param n_trial Trial mole numbers to check
   * @return true if element balance is satisfied within tolerance
   */
  private boolean checkElementBalance(double[] n_trial) {
    // Use a more relaxed tolerance since numerical errors accumulate
    // and we're using minimum mole thresholds
    double tolerance = 1e-3;

    // Calculate A * n_trial and compare with b_element
    for (int j = 0; j < NELE; j++) {
      double elementSum = 0.0;
      for (int i = 0; i < NSPEC; i++) {
        elementSum += A_matrix[j][i] * n_trial[i];
      }
      double absError = Math.abs(elementSum - b_element[j]);
      double relError = absError;
      if (b_element[j] > 1e-10) {
        relError = absError / b_element[j];
      }
      // Accept if either absolute or relative error is small
      if (relError > tolerance && absError > 1e-10) {
        return false;
      }
    }
    return true;
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
          chem_pot[i] = R * system.getPhase(phasenumb).getTemperature()
              * (chem_ref[i] + Math.log(
                  system.getPhase(phasenumb).getComponents()[components[i].getComponentNumber()]
                      .getNumberOfMolesInPhase())
                  - Math.log(n_t) + logactivityVec[i]);
          // system.getPhase(phasenumb).getActivityCoefficient(components[i].getComponentNumber(),components[waterNumb].getComponentNumber())));
          // System.out.println("solvent activ: "+ i + " " +
          // system.getPhases()[1].getComponents()[components[i].getComponentNumber()].getFugacityCoefficient()
          // / chem_pot_pure[i]);
        } else {
          chem_pot[i] = R * system.getPhase(phasenumb).getTemperature()
              * (chem_ref[i] + Math.log(
                  system.getPhase(phasenumb).getComponents()[components[i].getComponentNumber()]
                      .getNumberOfMolesInPhase())
                  - Math.log(n_t) + logactivityVec[i]);
          // system.getPhase(phasenumb).getActivityCoefficient(components[i].getComponentNumber(),components[waterNumb].getComponentNumber())));
          // System.out.println("solute activ : " + i + " " +
          // system.getPhases()[1].getComponents()[components[i].getComponentNumber()].getFugacityCoefficient()
          // / chem_pot_dilute[i]);
        }
        chem_pot_omega[i] = R * system.getPhase(phasenumb).getTemperature()
            * (chem_ref[i] + Math.log(n_omega[i]) - Math.log(n_t) + logactivityVec[i]);
      }
    }
    // Added by Neeraj
    Alambda_matrix = A_Jama_matrix.transpose().times(x_solve.getMatrix(0, NELE - 1, 0, 0));

    G_1 = 0.0;
    for (i = 0; i < NSPEC; i++) {
      // G_1 += chem_pot_omega[i] * d_n[i];
      // Added by Neeraj
      G_1 += (chem_pot_omega[i] - Alambda_matrix.get(i, 0)) * d_n[i] * (1 / n_omega[i] - 1 / n_t);
    }
    // System.out.println("G1 " +G_1);

    if (G_1 > 0) {
      G_0 = 0.0;
      for (i = 0; i < NSPEC; i++) {
        // G_0 += chem_pot[i]*d_n[i];
        // Added by Neeraj
        G_0 += (chem_pot[i] - Alambda_matrix.get(i, 0)) * d_n[i]
            * (1 / system.getPhase(phasenumb).getComponents()[components[i].getComponentNumber()]
                .getNumberOfMolesInPhase() - 1 / n_t);
        // G_0 +=
        // (chem_pot[i]-Alambda_matrix.get(i,0))*d_n[i]*(M_Jama_matrix.get(i,i)-1/n_t);
      }
      // Guard against division by zero
      double denominator = G_0 - G_1;
      if (Math.abs(denominator) > 1e-30) {
        step = G_0 / denominator;
      }
      // System.out.println("step G " + step);
    }

    step = innerStep(i, n_omega, check, step, false);
    // System.out.println("step ... " + step);

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
  public double innerStep(int speciesIndex, double[] n_omega, int check, double step,
      boolean test) {
    if (test) {
      // Guard against division by zero
      if (Math.abs(d_n[speciesIndex]) > 1e-30) {
        agemo = (-n_mol[speciesIndex] / d_n[speciesIndex]) * (1.0 - 0.03);
      } else {
        agemo = 1.0; // Default step if change is negligible
      }

      // Use separate loop variable to avoid overwriting method parameter
      for (int j = check; j < NSPEC; j++) {
        n_omega[j] = n_mol[j] + d_n[j];

        if (n_omega[j] < 0 && Math.abs(d_n[j]) > 1e-30) {
          double tempStep = (-n_mol[j] / d_n[j]) * (1.0 - 0.03);
          if (tempStep < agemo) {
            agemo = tempStep;
          }
        }
      }

      step = agemo;

      if (step > 1) {
        step = 1.0;
      }
      // Ensure minimum step size to prevent stagnation
      if (step < 1e-6 && step >= 0) {
        step = 1e-6;
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
}
