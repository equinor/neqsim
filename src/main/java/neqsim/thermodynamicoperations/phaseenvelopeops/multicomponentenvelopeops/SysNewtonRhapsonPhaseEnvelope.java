package neqsim.thermodynamicoperations.phaseenvelopeops.multicomponentenvelopeops;

import static neqsim.util.util.ArrayUtils.clearArray;
import static neqsim.util.util.ArrayUtils.clearMatrixArray;
import static neqsim.util.util.ArrayUtils.copyArray;
import static neqsim.util.util.ArrayUtils.copyMatrixArray;
import static neqsim.util.util.ArrayUtils.extractColumn;
import static neqsim.util.util.ArrayUtils.infinityNorm;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.mathlib.linearalgebra.EjmlLinearAlgebra;
import neqsim.mathlib.linearalgebra.LinearAlgebraOperations;
import neqsim.mathlib.nonlinearsolver.NewtonRhapson;
import neqsim.thermo.system.SystemInterface;

/**
 * Continuation solver for phase-envelope calculations. Working state is stored in primitive arrays and linear systems
 * are solved with EJML. Serialized state from the former Jama-backed layout is not compatible.
 *
 * @author asmund
 * @version $Id: $Id
 */
public class SysNewtonRhapsonPhaseEnvelope implements java.io.Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1001L;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(SysNewtonRhapsonPhaseEnvelope.class);

  double sumx = 0;
  double sumy = 0;
  int neq = 0;
  int iter = 0;
  int iter2 = 0;
  int ic02p = -100;
  int ic03p = -100;
  int testcrit = 0;
  int npCrit = 0;
  double beta = 0;
  double ds = 0;
  double dTmax = 10;
  double dPmax = 10;
  double TC1 = 0;
  double TC2 = 0;
  double PC1 = 0;
  double PC2 = 0;
  double specVal = 0.0;
  int lc = 0;
  int hc = 0;
  double sumlnKvals;
  double[][] Jac;
  double[] fvec;
  double[] u;
  double[] uold;
  double[] uolder;
  double[] ucrit;
  double[] ucritold;
  double[][] Xgij;
  SystemInterface system;
  int numberOfComponents;
  int speceq = 0;
  double[][] a = new double[4][4];
  double[] dxds = null;
  double[] s = new double[4];
  double[] xg;
  double[] xcoef;
  private static final LinearAlgebraOperations ALGEBRA = new EjmlLinearAlgebra();
  NewtonRhapson solver;
  boolean etterCP = false;
  boolean etterCP2 = false;
  boolean calcCP = false;
  boolean ettercricoT = false;
  double[] xcoefOld;
  double sign = 1.0;
  double dt;
  double dp;
  double norm;
  double vol = Math.pow(10, 5);
  double volold = Math.pow(10, 5);
  double volold2 = Math.pow(10, 5);

  /**
   * Constructor for sysNewtonRhapsonPhaseEnvelope.
   */
  public SysNewtonRhapsonPhaseEnvelope() {
  }

  /**
   * Constructor for sysNewtonRhapsonPhaseEnvelope.
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   * @param numberOfPhases a int
   * @param numberOfComponents a int
   */
  public SysNewtonRhapsonPhaseEnvelope(SystemInterface system, int numberOfPhases, int numberOfComponents) {
    this.system = system;
    this.numberOfComponents = numberOfComponents;
    neq = numberOfComponents + 2;
    Jac = new double[neq][neq]; // this is the jacobian of the system of equations
    fvec = new double[neq]; // this is the system of equations
    u = new double[neq]; // this is the vector of variables
    Xgij = new double[neq][4];
    setu();
    uold = copyArray(u);
    findSpecEqInit();
    solver = new NewtonRhapson();
    solver.setOrder(3);
  }

  /**
   * setfvec22.
   */
  public void setfvec22() {
    for (int i = 0; i < numberOfComponents; i++) {
      fvec[i] = Math
          .log(system.getPhase(0).getComponent(i).getFugacityCoefficient() * system.getPhase(0).getComponent(i).getx())
          - Math.log(
              system.getPhase(1).getComponent(i).getFugacityCoefficient() * system.getPhase(1).getComponent(i).getx());
    }
    double fsum = 0.0;
    for (int i = 0; i < numberOfComponents; i++) {
      fsum += system.getPhase(0).getComponent(i).getx() - system.getPhase(1).getComponent(i).getx();
    }
    fvec[numberOfComponents] = fsum;
    fvec[numberOfComponents] = sumy - sumx;
    fvec[numberOfComponents + 1] = u[speceq] - specVal;
  }

  /**
   * Setter for the field <code>fvec</code>.
   */
  public void setfvec() {
    for (int i = 0; i < numberOfComponents; i++) {
      fvec[i] = u[i] + system.getPhase(0).getComponent(i).getLogFugacityCoefficient()
          - system.getPhase(1).getComponent(i).getLogFugacityCoefficient();
    }
    fvec[numberOfComponents] = sumy - sumx;
    fvec[numberOfComponents + 1] = u[speceq] - specVal;
  }

  /**
   * findSpecEqInit.
   */
  public void findSpecEqInit() {
    speceq = 0;
    int speceqmin = 0;

    system.getPhase(0).getComponents()[numberOfComponents - 1].getPC();
    system.getPhase(0).getComponents()[numberOfComponents - 1].getTC();
    system.getPhase(0).getComponents()[numberOfComponents - 1].getAcentricFactor();

    for (int i = 0; i < numberOfComponents; i++) {
      if (system.getComponent(i).getz() < 1e-10) {
        continue;
      }
      if (system.getPhase(0).getComponent(i).getTC() > system.getPhase(0).getComponent(speceq).getTC()) {
        speceq = system.getPhase(0).getComponent(i).getComponentNumber();
        specVal = u[i];
        hc = i;
      }
      if (system.getPhase(0).getComponent(i).getTC() < system.getPhase(0).getComponent(speceqmin).getTC()) {
        speceqmin = system.getPhase(0).getComponent(i).getComponentNumber();
        lc = i;
      }
    }
  }

  /**
   * findSpecEq.
   */
  public void findSpecEq() {
    double max = 0;
    double max2 = 0.;
    int speceq2 = 0;

    for (int i = 0; i < numberOfComponents + 2; i++) {
      double testVal = Math.abs((Math.exp(u[i]) - Math.exp(uold[i])) / Math.exp(uold[i]));

      // the most sensitive variable is calculated not by the difference,
      // but from the sensibility vector in the original Michelsen code

      if (testVal > max) {
        speceq = i;
        specVal = u[i];
        max = testVal;
      }

      testVal = Math.abs(dxds[i]);

      if (testVal > max2) {
        speceq2 = i;
        // double specVal2 = u.get(i, 0);
        max2 = testVal;
      }
    }

    if (Double.isNaN(dxds[1])) {
      speceq2 = numberOfComponents + 3;
    }

    if (speceq != speceq2) {
      speceq = speceq2;
    }
  }

  /**
   * useAsSpecEq.
   *
   * @param i a int
   */
  public void useAsSpecEq(int i) {
    speceq = i;
    specVal = u[i];
    logger.debug("Enforced specification variable {} at {}", speceq, specVal);
  }

  /**
   * calc_x_y.
   */
  public final void calc_x_y() {
    sumx = 0;
    sumy = 0;
    for (int j = 0; j < system.getNumberOfPhases(); j++) {
      for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
        if (j == 0) {
          sumy += system.getPhase(j).getComponent(i).getK() * system.getPhase(j).getComponent(i).getz()
              / (1.0 - system.getBeta(0) + system.getBeta(0) * system.getPhase(0).getComponent(i).getK());
        }
        if (j == 1) {
          sumx += system.getPhase(0).getComponent(i).getz()
              / (1.0 - system.getBeta(0) + system.getBeta(0) * system.getPhase(0).getComponent(i).getK());
        }
      }
    }
  }

  /**
   * setJac2.
   */
  public void setJac2() {
    clearMatrixArray(Jac);
    double dij = 0.0;
    double[] dxidlnk = new double[numberOfComponents];
    double[] dyidlnk = new double[numberOfComponents];
    double tempJ = 0.0;
    for (int i = 0; i < numberOfComponents; i++) {
      dxidlnk[i] = -system.getBeta() * system.getPhase(1).getComponent(i).getx()
          * system.getPhase(0).getComponent(i).getx() / system.getPhase(0).getComponent(i).getz();
      dyidlnk[i] = system.getPhase(0).getComponent(i).getx() + system.getPhase(1).getComponent(i).getK() * dxidlnk[i];
    }
    for (int i = 0; i < numberOfComponents; i++) {
      double dlnxdlnK = -1.0 / (1.0 + system.getBeta() * system.getPhase(0).getComponent(i).getK() - system.getBeta())
          * system.getBeta() * system.getPhase(0).getComponent(i).getK();
      double dlnydlnK = 1.0
          - 1.0 / (system.getPhase(0).getComponent(i).getK() * system.getBeta() + 1 - system.getBeta())
              * system.getBeta() * system.getPhase(0).getComponent(i).getK();
      for (int j = 0; j < numberOfComponents; j++) {
        dij = i == j ? 1.0 : 0.0; // Kroneckers deltaget
        tempJ = -dij + dij * dlnydlnK - dij * dlnxdlnK;
        Jac[i][j] = tempJ;
      }
      tempJ = system.getTemperature()
          * (system.getPhase(0).getComponent(i).getdfugdt() - system.getPhase(1).getComponent(i).getdfugdt());
      Jac[i][numberOfComponents] = tempJ;
      tempJ = system.getPressure()
          * (system.getPhase(0).getComponent(i).getdfugdp() - system.getPhase(1).getComponent(i).getdfugdp());
      Jac[i][numberOfComponents + 1] = tempJ;
      Jac[numberOfComponents][i] = dyidlnk[i] - dxidlnk[i];
    }
    Jac[numberOfComponents + 1][speceq] = 1.0;
  }

  /**
   * setJac.
   */
  public void setJac() {
    clearMatrixArray(Jac);
    double dij = 0.0;
    double[] dxidlnk = new double[numberOfComponents];
    double[] dyidlnk = new double[numberOfComponents];
    double tempJ = 0.0;
    for (int i = 0; i < numberOfComponents; i++) {
      dxidlnk[i] = -system.getPhase(1).getComponent(i).getz()
          * Math.pow(system.getPhase(0).getComponent(i).getK() * system.getBeta() + 1.0 - system.getBeta(), -2.0)
          * system.getBeta() * system.getPhase(1).getComponent(i).getK();
      dyidlnk[i] = system.getPhase(1).getComponent(i).getz()
          / (system.getPhase(0).getComponent(i).getK() * system.getBeta() + 1.0 - system.getBeta())
          * system.getPhase(1).getComponent(i).getK()
          - system.getPhase(0).getComponent(i).getK() * system.getPhase(1).getComponent(i).getz()
              / Math.pow(1.0 - system.getBeta() + system.getBeta() * system.getPhase(0).getComponent(i).getK(), 2.0)
              * system.getBeta() * system.getPhase(0).getComponent(i).getK();
    }
    for (int i = 0; i < numberOfComponents; i++) {
      for (int j = 0; j < numberOfComponents; j++) {
        dij = i == j ? 1.0 : 0.0; // Kroneckers delta
        tempJ = dij + system.getPhase(0).getComponent(i).getdfugdx(j) * dyidlnk[j]
            - system.getPhase(1).getComponent(i).getdfugdx(j) * dxidlnk[j];
        Jac[i][j] = tempJ;
      }
      tempJ = system.getTemperature()
          * (system.getPhase(0).getComponent(i).getdfugdt() - system.getPhase(1).getComponent(i).getdfugdt());
      Jac[i][numberOfComponents] = tempJ;
      tempJ = system.getPressure()
          * (system.getPhase(0).getComponent(i).getdfugdp() - system.getPhase(1).getComponent(i).getdfugdp());
      Jac[i][numberOfComponents + 1] = tempJ;
      Jac[numberOfComponents][i] = dyidlnk[i] - dxidlnk[i];
      Jac[numberOfComponents + 1][i] = 0.0;
    }
    Jac[numberOfComponents + 1][speceq] = 1.0;
  }

  /**
   * Setter for the field <code>u</code>.
   */
  public void setu() {
    for (int i = 0; i < numberOfComponents; i++) {
      u[i] = Math.log(system.getPhase(0).getComponent(i).getK());
    }
    u[numberOfComponents] = Math.log(system.getTemperature());
    u[numberOfComponents + 1] = Math.log(system.getPressure());
  }

  /**
   * init.
   */
  public void init() {
    for (int i = 0; i < numberOfComponents; i++) {
      system.getPhase(0).getComponent(i).setK(Math.exp(u[i]));
      system.getPhase(1).getComponent(i).setK(Math.exp(u[i]));
    }
    system.setTemperature(Math.exp(u[numberOfComponents]));
    system.setPressure(Math.exp(u[numberOfComponents + 1]));

    calc_x_y();
    system.calc_x_y();
    system.init(3);
  }

  /**
   * calcInc.
   *
   * @param np a int
   */
  public void calcInc(int np) {
    // First we need the sensitivity vector dX/dS
    // calculates the sensitivity vector and stores the xgij matrix

    init();

    if (np < 5) {
      // for the first five points use as spec eq the nc+1
      // which is pressure

      setu();
      speceq = numberOfComponents + 1;
      specVal = u[speceq];
      setJac();
      clearArray(fvec);
      fvec[speceq] = 1.0;
      dxds = ALGEBRA.solve(Jac, fvec);
      double dp = 0.1;
      ds = dp / dxds[numberOfComponents + 1];

      for (int row = 0; row < neq; row++) {
        Xgij[row][np - 1] = u[row];
      }
      u = ALGEBRA.add(u, ALGEBRA.scale(dxds, ds));
      specVal = u[speceq];
    } else {
      // for the rest of the points use as spec eq the most sensitive variable
      int speceqOld = speceq;
      findSpecEq();
      if (speceq == numberOfComponents + 3) {
        speceq = speceqOld;
      }

      int intsign = Math.round(Math.round(dxds[speceq] * 100000000));
      int sign1 = Integer.signum(intsign);
      ds = sign1 * ds;

      setfvec();
      setJac();
      clearArray(fvec);
      fvec[numberOfComponents + 1] = 1.0;

      // calculate the dxds of the system
      dxds = ALGEBRA.solve(Jac, fvec);

      // manipulate stepsize according to the number of iterations of the previous
      // point
      if (iter > 6) {
        ds *= 0.5;
      } else {
        if (iter < 3) {
          ds *= 1.1;
        }
        if (iter == 3) {
          ds *= 1.0;
        }
        if (iter == 4) {
          ds *= 0.9;
        }
        if (iter > 4) {
          ds *= 0.7;
        }
      }

      // Now we check wheater this ds is greater than dTmax and dPmax.
      intsign = Math.round(Math.round(ds * 100000000));
      int sign2 = Integer.signum(intsign);

      if ((1 + dTmax / system.getTemperature()) < Math.exp(dxds[numberOfComponents] * ds)) {
        // logger.info("too high dT");
        ds = Math.log(1 + dTmax / system.getTemperature()) / dxds[numberOfComponents];
      } else if ((1 - dTmax / system.getTemperature()) > Math.exp(dxds[numberOfComponents] * ds)) {
        // logger.info("too low dT");
        ds = Math.log(1 - dTmax / system.getTemperature()) / dxds[numberOfComponents];
      } else if ((1 + dPmax / system.getPressure()) < Math.exp(dxds[numberOfComponents + 1] * ds)) {
        // logger.info("too low dP");
        ds = Math.log(1 + dPmax / system.getPressure()) / dxds[numberOfComponents + 1];
      } else if ((1 - dPmax / system.getPressure()) > Math.exp(dxds[numberOfComponents + 1] * ds)) {
        // logger.info("too low dP");
        ds = Math.log(1 - dPmax / system.getPressure()) / dxds[numberOfComponents + 1];
      }
      ds = sign2 * Math.abs(ds);

      // store the values of the solution for the last 5 iterations
      for (int row = 0; row < neq; row++) {
        System.arraycopy(Xgij[row], 1, Xgij[row], 0, 3);
        Xgij[row][3] = u[row];
      }
      System.arraycopy(Xgij[speceq], 0, s, 0, 4);

      // calculate next u
      calcInc2(np);
    }
    // since you are calculating the next point the previous iterations should be
    // zero
    iter = 0;
    iter2 = 0;
  }

  /**
   * calcInc2.
   *
   * @param np a int
   */
  public void calcInc2(int np) {
    // Here we calcualte the estimate of the next point from the polynomial.
    for (int i = 0; i < 4; i++) {
      a[i][0] = 1.0;
      a[i][1] = s[i];
      a[i][2] = s[i] * s[i];
      a[i][3] = a[i][2] * s[i];
    }

    // finds the estimate of the next point of the envelope that corresponds
    // to the most specified equation
    double sny;
    sny = ds * dxds[speceq] + s[3];
    specVal = sny;

    // finds the estimate of the next point of the envelope that corresponds
    // to all the equations
    for (int j = 0; j < neq; j++) {
      xg = copyArray(Xgij[j]);
      try {
        xcoef = ALGEBRA.solve(a, xg);
      } catch (Exception ex) {
        // Repeated specification values can make the cubic history singular. The local tangent remains a valid
        // predictor and does not borrow another variable's polynomial coefficients.
        u = ALGEBRA.add(extractColumn(Xgij, 3), ALGEBRA.scale(dxds, ds));
        specVal = u[speceq];
        return;
      }
      u[j] = xcoef[0] + sny * (xcoef[1] + sny * (xcoef[2] + sny * xcoef[3]));
    }
    xcoefOld = copyArray(xcoef);
  }

  /**
   * Calculate the critical point using polynomial interpolation followed by Newton refinement on the augmented
   * criticality system. The specification equation is replaced with the criticality constraint sum(ln Ki)^2 = 0 (all
   * K-values equal to unity at the critical point).
   *
   * <p>
   * Step 1: Cubic polynomial interpolation at s=0 gives an initial estimate (existing Michelsen approach). Step 2:
   * Newton iterations on the augmented system [fugacity equations; material balance; sum(ln Ki)^2 = 0] refine the
   * estimate to higher precision.
   * </p>
   *
   * <p>
   * Reference: Cismondi &amp; Michelsen, "Global calculation of phase equilibrium", Fluid Phase Equilibria, 259,
   * 228-234 (2007).
   * </p>
   */
  public void calcCrit() {
    SystemInterface continuationSystem = system;
    double savedSumx = sumx;
    double savedSumy = sumy;
    system = continuationSystem.clone();
    try {
      refineCriticalPoint();
      continuationSystem.setTC(system.getTC());
      continuationSystem.setPC(system.getPC());
    } finally {
      system = continuationSystem;
      sumx = savedSumx;
      sumy = savedSumy;
    }
  }

  /** Refine on a private system so trial critical states cannot contaminate the accepted continuation point. */
  private void refineCriticalPoint() {
    // Save all state that will be modified during refinement
    double[][] aa = copyMatrixArray(a);
    double[] ss = copyArray(s);
    double[][] xx = copyMatrixArray(Xgij);
    double[] uu = copyArray(u);
    int savedSpeceq = speceq;
    double savedSpecVal = specVal;

    // Step 1: Polynomial interpolation at s = 0 (K-values = 1)
    for (int i = 0; i < 4; i++) {
      a[i][0] = 1.0;
      a[i][1] = s[i];
      a[i][2] = s[i] * s[i];
      a[i][3] = a[i][2] * s[i];
    }

    double sny = 0.0;
    try {
      for (int j = 0; j < neq; j++) {
        xg = copyArray(Xgij[j]);
        try {
          xcoef = ALGEBRA.solve(a, xg);
        } catch (Exception ex) {
          xcoef = copyArray(xcoefOld);
        }
        u[j] = xcoef[0] + sny * (xcoef[1] + sny * (xcoef[2] + sny * xcoef[3]));
      }
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }

    // Store polynomial estimate as baseline
    double bestTC = Math.exp(u[numberOfComponents]);
    double bestPC = Math.exp(u[numberOfComponents + 1]);
    double bestSumLnK2 = 0.0;
    for (int i = 0; i < numberOfComponents; i++) {
      bestSumLnK2 += u[i] * u[i];
    }

    // Step 2: Newton refinement on augmented system (multicomponent only).
    // For pure components (nc == 1), the criticality row [2*ln(K1), 0, 0] becomes
    // nearly zero at the CP, making the Jacobian singular. Skip Newton for nc == 1.
    if (numberOfComponents > 1) {
      double polyTC = bestTC;
      double polyPC = bestPC;
      try {
        for (int critIter = 0; critIter < 10; critIter++) {
          // Update system state from current u
          for (int i = 0; i < numberOfComponents; i++) {
            system.getPhase(0).getComponent(i).setK(Math.exp(u[i]));
            system.getPhase(1).getComponent(i).setK(Math.exp(u[i]));
          }
          system.setTemperature(Math.exp(u[numberOfComponents]));
          system.setPressure(Math.exp(u[numberOfComponents + 1]));
          calc_x_y();
          system.calc_x_y();
          system.init(3);

          // Build residual vector
          for (int i = 0; i < numberOfComponents; i++) {
            fvec[i] = u[i] + system.getPhase(0).getComponent(i).getLogFugacityCoefficient()
                - system.getPhase(1).getComponent(i).getLogFugacityCoefficient();
          }
          fvec[numberOfComponents] = sumy - sumx;

          double sumLnK2 = 0.0;
          for (int i = 0; i < numberOfComponents; i++) {
            sumLnK2 += u[i] * u[i];
          }
          fvec[numberOfComponents + 1] = sumLnK2;

          // Check convergence
          if (ALGEBRA.euclideanNorm(fvec) < 1e-10) {
            bestTC = Math.exp(u[numberOfComponents]);
            bestPC = Math.exp(u[numberOfComponents + 1]);
            break;
          }

          // Build Jacobian: standard rows + criticality gradient row
          setJac();
          // Override last row: d(sum(ln Ki)^2)/d(ln Kj) = 2*ln(Kj)
          for (int j = 0; j < numberOfComponents; j++) {
            Jac[numberOfComponents + 1][j] = 2.0 * u[j];
          }
          Jac[numberOfComponents + 1][numberOfComponents] = 0.0;
          Jac[numberOfComponents + 1][numberOfComponents + 1] = 0.0;

          // Solve Newton step
          double[] dx = ALGEBRA.solve(Jac, fvec);

          // Guard against divergence: per-component check prevents large jumps
          // that could satisfy sum(ln Ki)^2 while moving T/P far from the true CP
          double dxNorm = ALGEBRA.euclideanNorm(dx);
          if (Double.isNaN(dxNorm)) {
            break;
          }
          boolean anyComponentTooLarge = false;
          for (int j = 0; j < neq; j++) {
            if (Math.abs(dx[j]) > 0.5) {
              anyComponentTooLarge = true;
              break;
            }
          }
          if (anyComponentTooLarge) {
            break;
          }

          u = ALGEBRA.subtract(u, dx);

          // Track best estimate: accept only if closer to criticality AND
          // T/P remain within 10% of the polynomial estimate (sanity check)
          double newSumLnK2 = 0.0;
          for (int i = 0; i < numberOfComponents; i++) {
            newSumLnK2 += u[i] * u[i];
          }
          double newTC = Math.exp(u[numberOfComponents]);
          double newPC = Math.exp(u[numberOfComponents + 1]);
          double tDeviation = Math.abs(newTC - polyTC) / polyTC;
          double pDeviation = Math.abs(newPC - polyPC) / polyPC;

          if (newSumLnK2 < bestSumLnK2 && tDeviation < 0.10 && pDeviation < 0.20) {
            bestTC = newTC;
            bestPC = newPC;
            bestSumLnK2 = newSumLnK2;
          }

          if (dxNorm < 1e-10) {
            break;
          }
        }
      } catch (Exception ex) {
        logger.debug("CP Newton refinement failed, using polynomial estimate");
      }
    }

    system.setTC(bestTC);
    system.setPC(bestPC);

    // Restore all state
    a = copyMatrixArray(aa);
    s = copyArray(ss);
    Xgij = copyMatrixArray(xx);
    u = copyArray(uu);
    speceq = savedSpeceq;
    specVal = savedSpecVal;
  }

  /**
   * Getter for the field <code>npCrit</code>.
   *
   * @return a int
   */
  public int getNpCrit() {
    return npCrit;
  }

  /**
   * sign.
   *
   * @param a a double
   * @param b a double
   * @return a double
   */
  public double sign(double a, double b) {
    a = Math.abs(a);
    b = b >= 0 ? 1.0 : -1.0;
    return a * b;
  }

  /**
   * Solve a continuation point with bounded Newton iterations and step-size retries. A point is accepted only when both
   * its correction and equilibrium residual are small. Failed attempts do not update the converged history.
   *
   * @param np point number along the envelope
   * @throws IllegalStateException if no finite, nontrivial equilibrium point converges after 15 step reductions
   */
  public void solve(int np) {
    double[] previous = extractColumn(Xgij, Math.min(np - 1, 3));
    for (int retry = 0; retry <= 15; retry++) {
      if (retry > 0) {
        ds *= 0.5;
        u = ALGEBRA.add(previous, ALGEBRA.scale(dxds, ds));
        specVal = u[speceq];
        if (np >= 5) {
          calcInc2(np);
        }
      }
      if (!Double.isFinite(specVal) || Math.abs(specVal - previous[speceq]) < 1.0e-12) {
        break;
      }
      iter = 0;
      try {
        init();
        setfvec();
        for (int localIter = 0; localIter < 50; localIter++) {
          iter++;
          setJac();
          double[] correction = ALGEBRA.solve(Jac, fvec);
          double correctionNorm = ALGEBRA.euclideanNorm(correction);
          double residualNorm = ALGEBRA.euclideanNorm(fvec);
          if (!Double.isFinite(correctionNorm) || !Double.isFinite(residualNorm)) {
            break;
          }
          // Limit each logarithmic correction before retrying an overly ambitious continuation step.
          double damping = Math.min(1.0, 1.0 / Math.max(1.0, infinityNorm(correction)));
          u = ALGEBRA.subtract(u, ALGEBRA.scale(correction, damping));
          init();
          setfvec();
          if (correctionNorm < 1.0e-5 && ALGEBRA.euclideanNorm(fvec) < 1.0e-8 && isPhysicalPoint()
              && (np == 1 || Math.abs(system.getTemperature() - Math.exp(previous[numberOfComponents])) <= 3.0 * dTmax
                  && Math.abs(system.getPressure() - Math.exp(previous[numberOfComponents + 1])) <= 3.0 * dPmax)) {
            norm = correctionNorm;
            uold = copyArray(u);
            return;
          }
        }
      } catch (RuntimeException ex) {
        logger.debug("Envelope point {} retry {} failed: {}", np, retry, ex.getMessage());
      }
    }
    u = copyArray(previous);
    init();
    throw new IllegalStateException("Phase envelope point " + np + " did not converge after 15 step reductions");
  }

  /**
   * Reject non-finite states and trivial equilibrium solutions. Pure-component saturation requires two distinct volume
   * roots because K=1 holds even for a single phase.
   *
   * @return true if the current equilibrium point can be retained
   */
  private boolean isPhysicalPoint() {
    if (!Double.isFinite(system.getTemperature()) || system.getTemperature() <= 0.0
        || !Double.isFinite(system.getPressure()) || system.getPressure() <= 0.0) {
      return false;
    }
    double largestLogK = 0.0;
    for (int i = 0; i < numberOfComponents; i++) {
      largestLogK = Math.max(largestLogK, Math.abs(u[i]));
    }
    if (numberOfComponents == 1) {
      // A volume iteration may stall at a spinodal with distinct volumes and a small fugacity residual.
      // Such a volume does not solve the EOS at the specified pressure and is not a saturation point.
      for (int phase = 0; phase < 2; phase++) {
        if (system.getPhase(phase) instanceof neqsim.thermo.phase.PhaseEosInterface) {
          double calculatedPressure = ((neqsim.thermo.phase.PhaseEosInterface) system.getPhase(phase)).calcPressure();
          if (!Double.isFinite(calculatedPressure)
              || Math.abs(calculatedPressure - system.getPressure()) > 1.0e-6 * system.getPressure()) {
            return false;
          }
        }
      }
      double firstVolume = system.getPhase(0).getMolarVolume();
      double secondVolume = system.getPhase(1).getMolarVolume();
      return Double.isFinite(firstVolume) && Double.isFinite(secondVolume) && firstVolume > 0.0 && secondVolume > 0.0
          && Math.abs(firstVolume - secondVolume) > 1.0e-6 * Math.max(firstVolume, secondVolume);
    }
    return largestLogK > 1.0e-6;
  }
}
