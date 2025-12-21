package neqsim.chemicalreactions.chemicalequilibrium;

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
  // Note: These instance variables for loop counters (i, j, k) are not thread-safe.
  // TODO: Refactor to use local variables in methods for better thread safety.
  int i;
  int j;
  int k;
  // int zeile_matrix, spalte_matrix, zeile_b, spalte_b;
  double[] b_cal;
  double[] b_vector;
  double[] second_term;
  double[] chem_pot;
  double u_u;
  double sum;
  double step;
  double[][] matrix;

  /**
   * <p>
   * Constructor for ChemEq.
   * </p>
   */
  public ChemEq() {
    for (int i = 0; i < 10; i++) {
      chem_ref[i] += Math.log(P);
    }

    phi = new double[3];
    phi[0] = -9.7851;
    phi[1] = -12.969;
    phi[2] = -15.222;

    for (j = 0; j < NSPEC; j++) {
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

    NNOT = 4;
    b_cal = new double[NELE];
    b_vector = new double[NNOT];
    second_term = new double[NELE];
    chem_pot = new double[NSPEC];
    matrix = new double[NNOT][NNOT];
    d_n = new double[NSPEC];
    for (int i = 0; i < n_mol.length; i++) {
      chem_ref[i] += Math.log(P);
    }

    phi[0] = -9.7851;
    phi[1] = -12.969;
    phi[2] = -15.222;

    for (j = 0; j < NSPEC; j++) {
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

    for (k = 0; k < NSPEC; k++) {
      n_t += n_mol[k];
    }

    // Guard against zero total moles
    if (n_t < 1e-30) {
      n_t = 1e-30;
    }

    for (i = 0; i < NELE; i++) {
      second_term[i] = 0;
      b_cal[i] = 0;
    }

    for (i = 0; i < NSPEC; i++) {
      // Guard against log of zero/negative
      double moleFraction = Math.abs(n_mol[i] / n_t);
      if (moleFraction < 1e-30) {
        moleFraction = 1e-30;
      }
      chem_pot[i] = chem_ref[i] + Math.log(moleFraction);
    }

    sum = 0;

    for (j = 0; j < NELE; j++) {
      for (i = 0; i < NELE; i++) {
        for (k = 0; k < NSPEC; k++) {
          sum += A_matrix[i][k] * A_matrix[j][k] * n_mol[k];
        }

        matrix[j][i] = sum;
        sum = 0;
      }

      for (k = 0; k < NSPEC; k++) {
        second_term[j] += A_matrix[j][k] * n_mol[k] * chem_pot[k];
      }

      for (i = 0; i < NSPEC; i++) {
        b_cal[j] += A_matrix[j][i] * n_mol[i];
      }
      matrix[j][NELE] = b_cal[j];
      b_vector[j] = second_term[j] + b_element[j] - b_cal[j];
    }

    for (j = 0; j < NELE; j++) {
      matrix[NELE][j] = b_cal[j];
    }

    matrix[NELE][NELE] = 0;
    b_vector[NNOT - 1] = 0;

    for (i = 0; i < NSPEC; i++) {
      b_vector[NNOT - 1] += n_mol[i] * chem_pot[i];
    }

    double[][] btest = new double[NNOT][1];

    for (i = 0; i < NNOT; i++) {
      btest[i][0] = b_vector[i];
    }

    Matrix matrixA = new Matrix(matrix);
    Matrix matrixb = new Matrix(btest);
    Matrix solved = matrixA.solve(matrixb);

    for (j = 0; j < NELE; j++) {
      b_vector[j] = solved.get(j, 0);
      phi[j] = solved.get(j, 0);
    }
    u_u = solved.get(NELE, 0);

    sum = 0;

    for (j = 0; j < NSPEC; j++) {
      for (k = 0; k < NELE; k++) {
        sum += A_matrix[k][j] * phi[k];
      }
      d_n[j] = n_mol[j] * (sum + u_u - chem_pot[j]);
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
        step = innerStep(n_omega, check, step);
        System.out.println("step2 ... " + step);
        return step;
      } else {
        chem_pot_omega[i] = R * T * (chem_ref[i] + Math.log(n_omega[i] / n_t));
        chem_pot[i] = R * T * (chem_ref[i] + Math.log(n_mol[i] / n_t));
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
      step = G_0 / (G_0 - G_1);
      // System.out.println("step4 ... " + step);
    }

    step = innerStep(n_omega, check, step);
    System.out.println("step ... " + step);

    return step;
  }

  /**
   * <p>
   * innerStep.
   * </p>
   *
   * @param n_omega an array of type double
   * @param check a int
   * @param step a double
   * @return a double
   */
  public double innerStep(double[] n_omega, int check, double step) {
    if (check > 0) {
      agemo = (-n_mol[i] / d_n[i]) * (1 - 0.01);
      for (i = check; i < NSPEC; i++) {
        n_omega[i] = n_mol[i] + d_n[i];

        if (n_omega[i] < 0) {
          step = (-n_mol[i] / d_n[i]) * (1 - 0.01);
          if (step < agemo) {
            agemo = step;
          }
        }
      }

      step = agemo;

      if (step > 1) {
        step = 1;
      }
    }
    // System.out.println("step5 ... " + step);
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

    for (i = 0; i < n_mol.length; i++) {
      System.out.println(n_mol[i]);
      this.n_mol[i] = n_mol[i];
      this.chem_ref[i] = chem_ref[i];
    }

    // beregner b
    double[][] nAr = new double[n_mol.length][1];

    for (i = 0; i < n_mol.length; i++) {
      nAr[i][0] = n_mol[i];
    }

    Matrix matrixA = new Matrix(A_matrix);
    Matrix matrixnAr = new Matrix(nAr);
    Matrix solved = matrixA.times(matrixnAr);

    this.b_element = solved.transpose().getArrayCopy()[0];

    NSPEC = n_mol.length;
    NELE = A_matrix.length;

    NNOT = NELE + 1; // 4;
    b_cal = new double[NELE];
    b_vector = new double[NNOT];
    second_term = new double[NELE];
    chem_pot = new double[NSPEC];
    matrix = new double[NNOT][NNOT];

    for (int i = 0; i < 10; i++) {
      // chem_ref[i] = chem_ref[i]+Math.log(this.P);
    }

    phi = new double[NELE];
    // Initialize phi values - these may need adjustment based on system
    for (int idx = 0; idx < NELE && idx < 5; idx++) {
      if (idx == 0) {
        phi[idx] = -9.7851;
      } else if (idx == 1) {
        phi[idx] = -12.969;
      } else if (idx == 2) {
        phi[idx] = -15.222;
      } else {
        phi[idx] = -10;
      }
    }

    for (j = 0; j < NSPEC; j++) {
      d_n[j] = 0;
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

    do {
      error = 0;
      chemSolve();

      for (i = 0; i < NSPEC; i++) {
        // Guard against division by zero
        if (Math.abs(n_mol[i]) > 1e-30) {
          error += Math.abs(d_n[i] / n_mol[i]);
        }

        if (Math.abs(n_mol[i]) > 1e-30 && Math.abs(d_n[i] / n_mol[i]) > 0.00001) {
          step = step();
          Gibbs = 0;
          for (int ii = 0; ii < NSPEC; ii++) {
            n_mol[ii] += step * d_n[ii];
            if (n_mol[ii] < 1e-30) {
              n_mol[ii] = 1e-30; // Prevent negative/zero moles
            }
            Gibbs += n_mol[ii] * chem_pot[ii];
          }
          solve();
          return;
        }
      }
    } while (error > 0.00005);
  }
}
