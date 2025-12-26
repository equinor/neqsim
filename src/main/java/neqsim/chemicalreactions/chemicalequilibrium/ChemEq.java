package neqsim.chemicalreactions.chemicalequilibrium;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import Jama.Matrix;
import neqsim.thermo.ThermodynamicConstantsInterface;

/**
 * <p>
 * ChemEq class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class ChemEq implements java.io.Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(ChemEq.class);

  /** Minimum moles to prevent log(0) and division by zero. */
  private static final double MIN_MOLES = 1e-30;

  int NSPEC = 10;
  int NELE = 3;
  double R = ThermodynamicConstantsInterface.R;
  double G_min = 0;
  double T = 3500;
  double P = 51;
  double n_t;
  double agemo = 0;
  // double[][] A_matrix = new double[NSPEC][NELE];
  // double n_mol[] = new double[NSPEC];
  // double chem_ref[] = new double[NSPEC];
  double[] d_n;
  double[] phi = new double[3];
  // double b_element[] = new double[NELE];

  double[][] A_matrix;
  double[] n_mol;
  double[] chem_ref;
  double[] b_element;

  int NNOT = 4;
  // Note: Loop variables i,j,k and sum/step should be local to methods, not instance fields
  double[] b_cal;
  double[] b_vector;
  double[] second_term;
  double[] chem_pot;
  double u_u;
  double[][] matrix;

  /**
   * <p>
   * Constructor for ChemEq.
   * </p>
   *
   * @deprecated This constructor is incomplete and may cause NPE. Use
   *             {@link #ChemEq(double, double, double[][], double[], double[], double[])} instead.
   */
  @Deprecated
  public ChemEq() {
    // Initialize arrays before use to prevent NPE
    chem_ref = new double[NSPEC];
    d_n = new double[NSPEC];
    phi = new double[NELE];

    for (int i = 0; i < NSPEC; i++) {
      chem_ref[i] = Math.log(P); // Changed from += to = since array is new
    }

    // Initialize phi with default values (bounds-checked)
    if (NELE >= 1) {
      phi[0] = -9.7851;
    }
    if (NELE >= 2) {
      phi[1] = -12.969;
    }
    if (NELE >= 3) {
      phi[2] = -15.222;
    }

    for (int j = 0; j < NSPEC; j++) {
      d_n[j] = 0;
    }
  }

  /**
   * <p>
   * Constructor for ChemEq.
   * </p>
   *
   * @param T a double
   * @param P a double
   * @param A_matrix an array of type double
   * @param n_mol an array of type double
   * @param chem_ref an array of type double
   * @param b_element an array of type double
   */
  public ChemEq(double T, double P, double[][] A_matrix, double[] n_mol, double[] chem_ref,
      double[] b_element) {
    this.T = T;
    this.P = P;
    this.A_matrix = A_matrix;
    this.n_mol = n_mol;
    this.chem_ref = chem_ref;
    this.b_element = b_element;

    NSPEC = n_mol.length;
    NELE = b_element.length;

    NNOT = NELE + 1; // Fix: was hardcoded as 4
    b_cal = new double[NELE];
    b_vector = new double[NNOT];
    second_term = new double[NELE];
    chem_pot = new double[NSPEC];
    matrix = new double[NNOT][NNOT];
    d_n = new double[NSPEC];
    phi = new double[NELE]; // Allocate phi with correct size

    for (int i = 0; i < n_mol.length; i++) {
      chem_ref[i] += Math.log(P);
    }

    // Initialize phi with default values (bounds-checked)
    if (NELE >= 1) {
      phi[0] = -9.7851;
    }
    if (NELE >= 2) {
      phi[1] = -12.969;
    }
    if (NELE >= 3) {
      phi[2] = -15.222;
    }

    for (int j = 0; j < NSPEC; j++) {
      d_n[j] = 0;
    }
  }

  /**
   * <p>
   * Constructor for ChemEq.
   * </p>
   *
   * @param A_matrix an array of type double
   */
  public ChemEq(double[][] A_matrix) {
    this.A_matrix = A_matrix;
  }

  /**
   * <p>
   * Constructor for ChemEq.
   * </p>
   *
   * @param T a double
   * @param P a double
   * @param A_matrix an array of type double
   */
  public ChemEq(double T, double P, double[][] A_matrix) {
    this.T = T;
    this.P = P;
    this.A_matrix = A_matrix;
  }

  /**
   * <p>
   * chemSolve.
   * </p>
   */
  public void chemSolve() {
    n_t = 0;

    for (int k = 0; k < NSPEC; k++) {
      n_t += n_mol[k];
    }
    logger.debug("n_total: " + n_t);

    for (int i = 0; i < NELE; i++) {
      second_term[i] = 0;
      b_cal[i] = 0;
    }

    for (int i = 0; i < NSPEC; i++) {
      // Protect against log(0) and n_t = 0
      double safeMoles = Math.max(MIN_MOLES, Math.abs(n_mol[i]));
      double safeNt = Math.max(MIN_MOLES, n_t);
      chem_pot[i] = chem_ref[i] + Math.log(safeMoles / safeNt);
      logger.debug("chempot: " + i + "  = " + chem_pot[i]);
    }

    double sum = 0;

    for (int j = 0; j < NELE; j++) {
      for (int i = 0; i < NELE; i++) {
        for (int k = 0; k < NSPEC; k++) {
          sum += A_matrix[i][k] * A_matrix[j][k] * n_mol[k];
        }

        matrix[j][i] = sum;
        sum = 0;
      }

      for (int k = 0; k < NSPEC; k++) {
        second_term[j] += A_matrix[j][k] * n_mol[k] * chem_pot[k];
      }

      for (int i = 0; i < NSPEC; i++) {
        b_cal[j] += A_matrix[j][i] * n_mol[i];
      }
      matrix[j][NELE] = b_cal[j];
      b_vector[j] = second_term[j] + b_element[j] - b_cal[j];
    }

    for (int j = 0; j < NELE; j++) {
      matrix[NELE][j] = b_cal[j];
    }

    matrix[NELE][NELE] = 0;
    b_vector[NNOT - 1] = 0;

    for (int i = 0; i < NSPEC; i++) {
      b_vector[NNOT - 1] += n_mol[i] * chem_pot[i];
    }

    double[][] btest = new double[NNOT][1];

    for (int i = 0; i < NNOT; i++) {
      btest[i][0] = b_vector[i];

      for (int j = 0; j < NNOT; j++) {
        logger.trace("matrix: " + i + " " + j + " " + matrix[i][j]);
      }
    }

    Matrix matrixA = new Matrix(matrix);
    Matrix matrixb = new Matrix(btest);
    Matrix solved = matrixA.solve(matrixb);
    // solved.print(5, 3); // Removed debug print

    for (int j = 0; j < NELE; j++) {
      b_vector[j] = solved.get(j, 0);
      phi[j] = solved.get(j, 0);
    }
    u_u = solved.get(NELE, 0);

    sum = 0;

    for (int j = 0; j < NSPEC; j++) {
      for (int k = 0; k < NELE; k++) {
        sum += A_matrix[k][j] * phi[k];
      }
      d_n[j] = n_mol[j] * (sum + u_u - chem_pot[j]);
      logger.debug("dn[" + j + "] = " + d_n[j]);
      sum = 0;
    }
  }

  /**
   * <p>
   * step.
   * </p>
   *
   * @return a double
   */
  public double step() {
    double step;

    int i;
    int check;
    double[] n_omega = new double[NSPEC];
    double[] chem_pot_omega = new double[NSPEC];
    double[] chem_pot = new double[NSPEC];

    check = 0;
    step = 1;

    for (i = 0; i < NSPEC; i++) {
      n_omega[i] = n_mol[i] + d_n[i];
      if (n_omega[i] < 0) {
        check = i;
        step = innerStep(i, n_omega, check, step);
        logger.debug("step2 ... " + step);
        return step;
      } else {
        // Protect against log(0)
        double safeOmega = Math.max(MIN_MOLES, n_omega[i]);
        double safeMoles = Math.max(MIN_MOLES, n_mol[i]);
        double safeNt = Math.max(MIN_MOLES, n_t);
        chem_pot_omega[i] = R * T * (chem_ref[i] + Math.log(safeOmega / safeNt));
        chem_pot[i] = R * T * (chem_ref[i] + Math.log(safeMoles / safeNt));
      }
    }

    double G_0;
    double G_1 = 0;
    for (i = 0; i < NSPEC; i++) {
      G_1 += chem_pot_omega[i] * d_n[i];
    }

    if (G_1 > 0) {
      G_0 = 0;
      for (i = 0; i < NSPEC; i++) {
        G_0 += chem_pot[i] * d_n[i];
      }
      // Protect against division by zero when G_0 ≈ G_1
      double denominator = G_0 - G_1;
      if (Math.abs(denominator) > 1e-30) {
        step = G_0 / denominator;
      }
      // System.out.println("step4 ... " + step);
    }

    step = innerStep(check, n_omega, check, step);
    logger.debug("step ... " + step);

    return step;
  }

  /**
   * <p>
   * innerStep.
   * </p>
   *
   * @param startIndex starting index for loop (fixes bug where instance field was used)
   * @param n_omega an array of type double
   * @param check a int
   * @param step a double
   * @return a double
   */
  public double innerStep(int startIndex, double[] n_omega, int check, double step) {
    if (check > 0) {
      // Use startIndex parameter instead of undefined instance field 'i'
      agemo = (-n_mol[startIndex] / d_n[startIndex]) * (1 - 0.01);
      for (int i = check; i < NSPEC; i++) {
        n_omega[i] = n_mol[i] + d_n[i];

        if (n_omega[i] < 0) {
          double tempStep = (-n_mol[i] / d_n[i]) * (1 - 0.01);
          if (tempStep < agemo) {
            agemo = tempStep;
          }
        }
      }

      step = agemo;

      if (step > 1) {
        step = 1;
      }
    }
    return step;
  }

  /**
   * <p>
   * solve.
   * </p>
   *
   * @param T a double
   * @param P a double
   * @param n_mol an array of type double
   * @param chem_ref an array of type double
   */
  public void solve(double T, double P, double[] n_mol, double[] chem_ref) {
    this.T = T;
    this.P = P;

    for (int i = 0; i < n_mol.length; i++) {
      logger.trace("n_mol[" + i + "] = " + n_mol[i]);
      this.n_mol[i] = n_mol[i];
      this.chem_ref[i] = chem_ref[i];
    }

    // beregner b (calculate element balance vector)
    double[][] nAr = new double[n_mol.length][1];

    for (int i = 0; i < n_mol.length; i++) {
      nAr[i][0] = n_mol[i];
    }

    Matrix matrixA = new Matrix(A_matrix);
    Matrix matrixnAr = new Matrix(nAr);
    Matrix solved = matrixA.times(matrixnAr);

    this.b_element = solved.transpose().getArrayCopy()[0];

    NSPEC = n_mol.length;
    NELE = A_matrix.length;

    NNOT = NELE + 1;
    b_cal = new double[NELE];
    b_vector = new double[NNOT];
    second_term = new double[NELE];
    chem_pot = new double[NSPEC];
    matrix = new double[NNOT][NNOT];

    // Allocate phi with correct size based on NELE
    phi = new double[NELE];

    // Initialize phi with default Lagrange multiplier estimates (bounds-checked)
    double[] defaultPhi = {-9.7851, -12.969, -15.222, -10.0, -10.0};
    for (int i = 0; i < NELE && i < defaultPhi.length; i++) {
      phi[i] = defaultPhi[i];
    }
    // For elements beyond the defaults, use -10.0
    for (int i = defaultPhi.length; i < NELE; i++) {
      phi[i] = -10.0;
    }

    for (int j = 0; j < NSPEC; j++) {
      d_n[j] = 0;
    }

    // Log element balance vector
    for (int i = 0; i < b_element.length; i++) {
      logger.debug("b_element[" + i + "] = " + b_element[i]);
    }

    solve();
  }

  /**
   * <p>
   * solve.
   * </p>
   */
  public void solve() {
    double error = 0;
    double Gibbs = 0;
    double step = 1.0; // Local step variable

    do {
      error = 0;
      chemSolve();

      for (int i = 0; i < NSPEC; i++) {
        logger.trace(n_mol[i] + "  prove korreksjon  " + step * d_n[i]);

        error += d_n[i] / n_mol[i];

        if (Math.abs(d_n[i] / n_mol[i]) > 0.00001) {
          step = step();
          Gibbs = 0;
          for (int j = 0; j < NSPEC; j++) {
            n_mol[j] += step * d_n[j];
            Gibbs += n_mol[j] * chem_pot[j];
          }
          logger.debug("Gibbs: " + Gibbs);
          solve();
          return;
        }
      }
    } while (error > 0.00005);

    for (int j = 0; j < NSPEC; j++) {
      logger.debug(" SVAR : " + n_mol[j] + "   " + (d_n[j] / n_mol[j]) + " GIBBS : " + Gibbs);
    }
  }
}
