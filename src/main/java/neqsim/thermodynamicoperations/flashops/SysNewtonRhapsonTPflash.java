package neqsim.thermodynamicoperations.flashops;

import Jama.Matrix;
import neqsim.thermo.system.SystemInterface;

/**
 * Newton-Raphson solver for two-phase TP flash using Michelsen's u-variable formulation.
 *
 * <p>
 * Variables: u[i] = beta * y[i] (Michelsen 1982b). Residual: g[i] = ln(f_gas_i) - ln(f_liq_i).
 * Jacobian: Hessian of the reduced Gibbs energy Q(u). Uses Armijo backtracking line search on Q for
 * global convergence (Michelsen &amp; Mollerup 2007, Ch. 12).
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class SysNewtonRhapsonTPflash implements java.io.Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  int neq = 0;
  int iter = 0;
  Matrix Jac;
  Matrix fvec;
  Matrix u;
  SystemInterface system;
  int numberOfComponents;
  Matrix dx;
  /** Cached feed compositions (constant during flash). */
  private double[] z;

  /**
   * <p>
   * Constructor for sysNewtonRhapsonTPflash.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   * @param numberOfPhases a int
   * @param numberOfComponents a int
   */
  public SysNewtonRhapsonTPflash(SystemInterface system, int numberOfPhases,
      int numberOfComponents) {
    this.system = system;
    this.numberOfComponents = numberOfComponents;
    neq = numberOfComponents;
    Jac = new Matrix(neq, neq);
    fvec = new Matrix(neq, 1);
    u = new Matrix(neq, 1);
    setu();
    z = new double[numberOfComponents];
    for (int i = 0; i < numberOfComponents; i++) {
      z[i] = system.getPhase(0).getComponent(i).getz();
    }
  }

  /**
   * <p>
   * Setter for the field <code>fvec</code>.
   * </p>
   */
  public void setfvec() {
    for (int i = 0; i < numberOfComponents; i++) {
      fvec.set(i, 0,
          Math.log(system.getPhase(0).getComponent(i).getFugacityCoefficient()
              * system.getPhase(0).getComponent(i).getx())
              - Math.log(system.getPhase(1).getComponent(i).getFugacityCoefficient()
                  * system.getPhase(1).getComponent(i).getx()));
    }
  }

  /**
   * <p>
   * setJac.
   * </p>
   */
  public void setJac() {
    double dij;
    double tempJ;
    for (int i = 0; i < numberOfComponents; i++) {
      for (int j = 0; j < numberOfComponents; j++) {
        dij = i == j ? 1.0 : 0.0;
        tempJ = 1.0 / system.getBeta()
            * (dij / system.getPhase(0).getComponent(i).getx() - 1.0
                + system.getPhase(0).getComponent(i).getdfugdx(j))
            + 1.0 / (1.0 - system.getBeta()) * (dij / system.getPhase(1).getComponent(i).getx()
                - 1.0 + system.getPhase(1).getComponent(i).getdfugdx(j));
        Jac.set(i, j, tempJ);
      }
    }
  }

  /**
   * <p>
   * Setter for the field <code>u</code>.
   * </p>
   */
  public void setu() {
    for (int i = 0; i < numberOfComponents; i++) {
      u.set(i, 0, system.getBeta() * system.getPhase(0).getComponent(i).getx());
    }
  }

  /**
   * <p>
   * init.
   * </p>
   */
  public void init() {
    double temp = 0;

    for (int i = 0; i < numberOfComponents; i++) {
      temp += u.get(i, 0);
    }
    system.setBeta(temp);

    for (int i = 0; i < numberOfComponents; i++) {
      system.getPhase(0).getComponent(i).setx(u.get(i, 0) / system.getBeta());
      system.getPhase(1).getComponent(i).setx((z[i] - u.get(i, 0)) / (1.0 - system.getBeta()));
      system.getPhase(0).getComponent(i).setK(
          system.getPhase(0).getComponent(i).getx() / system.getPhase(1).getComponent(i).getx());
      system.getPhase(1).getComponent(i).setK(system.getPhase(0).getComponent(i).getK());
    }

    system.init(3);
  }

  /**
   * Compute Michelsen's reduced Gibbs energy Q(u). Q = sum[u_i * ln(y_i * phi_gas_i) + (z_i - u_i)
   * * ln(x_i * phi_liq_i)]. Gradient of Q equals fvec; Hessian of Q equals Jac.
   *
   * @return Q value
   */
  private double computeQ() {
    double q = 0.0;
    for (int i = 0; i < numberOfComponents; i++) {
      double yi = system.getPhase(0).getComponent(i).getx();
      double xi = system.getPhase(1).getComponent(i).getx();
      double lnFugGas = Math.log(yi * system.getPhase(0).getComponent(i).getFugacityCoefficient());
      double lnFugLiq = Math.log(xi * system.getPhase(1).getComponent(i).getFugacityCoefficient());
      q += u.get(i, 0) * lnFugGas + (z[i] - u.get(i, 0)) * lnFugLiq;
    }
    return q;
  }

  /**
   * Check physical feasibility of trial u values.
   *
   * @param uTrial trial u vector
   * @return true if all compositions and beta are in valid range
   */
  private boolean isFeasible(double[] uTrial) {
    double betaTrial = 0.0;
    for (int i = 0; i < numberOfComponents; i++) {
      if (uTrial[i] < 1e-15 || uTrial[i] > z[i] - 1e-15) {
        return false;
      }
      betaTrial += uTrial[i];
    }
    return betaTrial > 1e-12 && betaTrial < 1.0 - 1e-12;
  }

  /**
   * Set compositions from trial u vector and compute fugacities only (init level 1). Used for line
   * search Q evaluation at trial points.
   *
   * @param uTrial trial u vector
   */
  private void setTrialAndComputeFugacities(double[] uTrial) {
    double betaTrial = 0.0;
    for (int i = 0; i < numberOfComponents; i++) {
      betaTrial += uTrial[i];
    }
    system.setBeta(betaTrial);
    for (int i = 0; i < numberOfComponents; i++) {
      system.getPhase(0).getComponent(i).setx(uTrial[i] / betaTrial);
      system.getPhase(1).getComponent(i).setx((z[i] - uTrial[i]) / (1.0 - betaTrial));
    }
    system.init(1, 0);
    system.init(1, 1);
  }

  /**
   * <p>
   * solve.
   * </p>
   *
   * @return a double
   */
  public double solve() {
    iter++;
    init();

    double qCurrent = computeQ();
    setfvec();
    setJac();

    // Levenberg-Marquardt regularization for stability
    double jacTrace = 0.0;
    for (int i = 0; i < neq; i++) {
      jacTrace += Math.abs(Jac.get(i, i));
    }
    double lambda = 1e-8 * jacTrace / neq;
    for (int i = 0; i < neq; i++) {
      Jac.set(i, i, Jac.get(i, i) + lambda);
    }

    dx = Jac.solve(fvec);

    // Directional derivative: slope = fvec^T * dx = grad(Q)^T * (Jac^-1 * grad(Q)) > 0
    double slope = 0.0;
    for (int i = 0; i < neq; i++) {
      slope += fvec.get(i, 0) * dx.get(i, 0);
    }

    // Armijo backtracking line search on Michelsen Q function
    double alpha = 1.0;
    double c1 = 1e-4;
    int maxBacktrack = 8;
    double[] uTrial = new double[numberOfComponents];

    for (int bt = 0; bt < maxBacktrack; bt++) {
      for (int i = 0; i < numberOfComponents; i++) {
        uTrial[i] = u.get(i, 0) - alpha * dx.get(i, 0);
      }

      if (isFeasible(uTrial)) {
        try {
          setTrialAndComputeFugacities(uTrial);
          double qTrial = computeQ();
          // Armijo condition: Q_trial <= Q_current - c1 * alpha * slope
          if (qTrial <= qCurrent - c1 * alpha * slope) {
            break;
          }
        } catch (Exception ex) {
          // Cubic solver failed at trial point — try shorter step
        }
      }

      alpha *= 0.5;
    }

    // Update u with accepted step
    double stepNormSq = 0.0;
    double uNormSq = 0.0;
    for (int i = 0; i < numberOfComponents; i++) {
      double step = alpha * dx.get(i, 0);
      u.set(i, 0, u.get(i, 0) - step);
      stepNormSq += step * step;
      uNormSq += u.get(i, 0) * u.get(i, 0);
    }
    return Math.sqrt(stepNormSq) / Math.max(Math.sqrt(uNormSq), 1e-10);
  }
}
