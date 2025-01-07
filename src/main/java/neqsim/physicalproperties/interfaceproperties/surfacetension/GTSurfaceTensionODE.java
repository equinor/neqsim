package neqsim.physicalproperties.interfaceproperties.surfacetension;

import org.apache.commons.math3.ode.FirstOrderDifferentialEquations;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ejml.data.DMatrixRMaj;
import org.ejml.dense.row.CommonOps_DDRM;
import org.ejml.dense.row.NormOps_DDRM;
import org.ejml.dense.row.SingularOps_DDRM;
import org.ejml.dense.row.factory.DecompositionFactory_DDRM;
import org.ejml.interfaces.decomposition.SingularValueDecomposition;
import org.ejml.interfaces.decomposition.SingularValueDecomposition_F64;
import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * GTSurfaceTensionODE class.
 *
 * ODE-system for integrating the surface tension in cases where the a reference component number
 * mole density can be used as integration variable.
 *
 * This method can only be used when the reference component density varies monotonically over the
 * interface, and where there are no binary interaction parameters for the attractive parameter in
 * the EOS.
 * </p>
 *
 * @author Olaf Trygve Berglihn olaf.trygve.berglihn@sintef.no
 * @version $Id: $Id
 */
public class GTSurfaceTensionODE implements FirstOrderDifferentialEquations {
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(GTSurfaceTensionODE.class);

  private boolean initialized = false;
  private int ncomp; // Number of components.
  private SystemInterface sys; // Local work copy of flashed system.
  private double[] ci; // Influence parameter.
  private double[] mueq; // Chemical potentials at equilibrium.
  private double[] rho_ph1; // Density in bulk phase 1.
  private double[] rho_ph2; // Density in bulk phase 2.
  private double rhoref_span; // Span in reference component number density.
  private double[] rho_k; // Density at iteration k.
  private double t; // Temperature.
  private double[] p0; // Bulk pressure [Pa]
  private int refcomp; // Reference component index.
  private int[] algidx; // Index for non-reference components.
  private static final double Pa = 1e-5;
  private static final double m3 = 1e-5;
  private double yscale;
  public double normtol = 1e-11;
  public double reltol = 1e-6;
  public double abstol = 1e-6;
  public int maxit = 40;

  /**
   * <p>
   * Constructor for GTSurfaceTensionODE.
   * </p>
   *
   * @param flashedSystem a {@link neqsim.thermo.system.SystemInterface} object
   * @param phase1 a int
   * @param phase2 a int
   * @param referenceComponent a int
   * @param yscale a double
   */
  public GTSurfaceTensionODE(SystemInterface flashedSystem, int phase1, int phase2,
      int referenceComponent, double yscale) {
    int i;

    int idx = 0;
    // Setup local system clone and some parameters.
    this.yscale = yscale;
    this.sys = flashedSystem.clone();
    this.refcomp = referenceComponent;
    this.ncomp = this.sys.getPhase(0).getNumberOfComponents();
    this.t = this.sys.getPhase(0).getTemperature();
    this.rho_ph1 = new double[this.ncomp];
    this.rho_ph2 = new double[this.ncomp];
    this.rho_k = new double[this.ncomp];
    this.algidx = new int[this.ncomp - 1];
    this.p0 = new double[1];
    this.ci = new double[this.ncomp];
    this.mueq = new double[this.ncomp];

    for (i = 0; i < this.ncomp; i++) {
      if (i != this.refcomp) {
        this.algidx[idx] = i;
        idx++;
      }
    }

    /*
     * Get influence parameters and densities at phase equilibrium.
     */
    for (i = 0; i < this.ncomp; i++) {
      this.ci[i] = this.sys.getPhase(0).getComponent(i).getSurfaceTenisionInfluenceParameter(t);
      this.rho_ph1[i] = this.sys.getPhase(phase1).getComponent(i).getx()
          / this.sys.getPhase(phase1).getMolarVolume() / m3;
      this.rho_ph2[i] = this.sys.getPhase(phase2).getComponent(i).getx()
          / this.sys.getPhase(phase2).getMolarVolume() / m3;
      this.rho_k[i] = this.rho_ph1[i];
    }
    this.rhoref_span = Math.abs(this.rho_ph2[this.refcomp] - this.rho_ph1[this.refcomp]);
    this.sys.setBeta(1.0);
    this.sys.init(0);
    this.sys.setUseTVasIndependentVariables(true);
    this.sys.setNumberOfPhases(1);
    this.sys.getPhase(0).setTotalVolume(1.0);
    this.sys.useVolumeCorrection(false);
    this.sys.setEmptyFluid();
    double[] nv = new double[this.ncomp];
    for (i = 0; i < ncomp; i++) {
      nv[i] = this.rho_ph1[i] * Pa;
      // this.sys.addComponent(this.sys.getPhase(0).getComponent(i).getName(),
      // this.rho_ph1[i]*Pa);
    }
    this.sys.setMolarComposition(nv);
    this.sys.setMolarComposition(nv);
    this.sys.setMolarComposition(nv);
    this.sys.init_x_y();
    this.sys.setBeta(1.0);
    this.sys.init(3);
  }

  /**
   * Initialize equilibrium chemical potential, and derivative.
   */
  public void initmu() {
    int i;
    double maxerr = 0.;
    double[][] dmu_drho1 = new double[this.ncomp][this.ncomp];
    double[][] dmu_drho2 = new double[this.ncomp][this.ncomp];
    double[] mueq2 = new double[this.ncomp];
    double[] p0 = new double[1];

    GTSurfaceTensionUtils.mufun(this.sys, this.ncomp, this.t, this.rho_ph1, this.mueq, dmu_drho1,
        this.p0);
    GTSurfaceTensionUtils.mufun(this.sys, this.ncomp, this.t, this.rho_ph2, mueq2, dmu_drho2, p0);

    // Check flash equilibrium
    for (i = 0; i < this.ncomp; i++) {
      maxerr = Math.max(maxerr, Math.abs(this.mueq[i] / mueq2[i] - 1.0));
    }
    if (maxerr > this.reltol) {
      logger.error("Flash is not properly solved.  Maximum relative error in chemical potential:  "
          + maxerr + " > " + reltol);
      throw new RuntimeException("Flash not solved!");
    }
    this.initialized = true;
  }

  /** {@inheritDoc} */
  @Override
  public int getDimension() {
    return 1;
  }

  /** {@inheritDoc} */
  @Override
  public void computeDerivatives(double t, double[] y, double[] yDot) {
    double[] mu = new double[this.ncomp];
    double[][] dmu_drho = new double[this.ncomp][this.ncomp];
    double[] p = new double[1];
    double[] f = new double[this.ncomp];
    double[][] jac = new double[this.ncomp][this.ncomp];
    double[] rho = new double[this.ncomp];
    double delta_omega;
    double dsigma;
    double cij;
    double rho0;

    DMatrixRMaj dn_dnref;

    int j;
    if (!this.initialized) {
      this.initmu();
    }

    rho0 = this.rho_ph1[this.refcomp];
    rho[this.refcomp] = t * this.rhoref_span + rho0;
    for (int i = 0; i < this.ncomp - 1; i++) {
      rho[this.algidx[i]] = this.rho_k[this.algidx[i]];
    }
    // System.out.printf("t: %e, rho_ref: %e\n", t, rho[this.refcomp]);
    solveRho(rho, mu, dmu_drho, p, f, jac);
    for (int i = 0; i < this.ncomp; i++) {
      this.rho_k[i] = rho[i];
    }

    DMatrixRMaj df = new DMatrixRMaj(jac);
    DMatrixRMaj ms = new DMatrixRMaj(df.numRows, 1);
    SingularValueDecomposition<DMatrixRMaj> svd =
        DecompositionFactory_DDRM.svd(df.numRows, df.numCols, true, true, true);
    if (!svd.decompose(df)) {
      throw new RuntimeException("Decomposition failed");
    }
    dn_dnref =
        SingularOps_DDRM.nullSpace((SingularValueDecomposition_F64<DMatrixRMaj>) svd, ms, 1e-12); // UtilEjml.EPS);
    CommonOps_DDRM.divide(dn_dnref.get(this.refcomp, 0), dn_dnref);
    delta_omega = -(p[0] - this.p0[0]);
    for (int i = 0; i < this.ncomp; i++) {
      delta_omega += (mu[i] - this.mueq[i]) * rho[i];
    }

    dsigma = 0.0;
    for (int i = 0; i < this.ncomp; i++) {
      for (j = 0; j < this.ncomp; j++) {
        cij = Math.sqrt(this.ci[i] * this.ci[j]);
        dsigma += cij * dn_dnref.get(i, 0) * dn_dnref.get(j, 0);
      }
    }

    /*
     * If the discriminant becomes negative, this can be due to numerical problems when approaching
     * bulk. Assume the profile is sufficiently flat if the reference density has exceeded 90% of
     * the target bulk density. A better way is to use the approximations given by Davis,
     * Statistical mechanics of surfaces and thin films, VHC Publishers Inc, 1996.
     */
    if (delta_omega * dsigma < 0.0) {
      if (t > 0.9) {
        dsigma = 0.;
      } else {
        throw new RuntimeException("Negative discriminant");
      }
    } else {
      dsigma = Math.sqrt(2.0 * delta_omega * dsigma);
    }

    yDot[0] = dsigma * this.rhoref_span * this.yscale;
    // System.out.printf("t: %e, sigma: %e [J/m2]\n", t, y[0]/this.yscale);

    if (y[0] < 0.0) {
      y[0] = 0.0;
      // throw new RuntimeException("Negative surface tension.");
    }
  }

  /**
   * SolveRho. Solve for the equilibrium density in the interface. Solves the equilibrium relations
   * with the Newton-Raphson method.
   *
   * @param rho Number density [mol/m3]
   * @param mu Chemical potential [J/mol]
   * @param dmu_drho Chemical potential derivative with respect to mole numbers [J/mol^2]
   * @param p Pressure [Pa]
   * @param f Residual of equilibrium relations.
   * @param jac Jacobian of the equilibrium relations.
   */
  private void solveRho(double[] rho, double[] mu, double[][] dmu_drho, double[] p, double[] f,
      double[][] jac) {
    double normf;
    double norm0;
    double norm;
    double s;
    int i;
    int j;
    int iter;
    DMatrixRMaj A = new DMatrixRMaj(this.ncomp - 1, this.ncomp - 1);
    DMatrixRMaj b = new DMatrixRMaj(this.ncomp - 1, 1);
    DMatrixRMaj x = new DMatrixRMaj(this.ncomp - 1, 1);
    DMatrixRMaj x0 = new DMatrixRMaj(this.ncomp - 1, 1);
    DMatrixRMaj c = new DMatrixRMaj(this.ncomp - 1, 1);

    GTSurfaceTensionUtils.mufun(this.sys, this.ncomp, this.t, rho, mu, dmu_drho, p);
    fjacfun(mu, dmu_drho, f, jac);
    for (i = 0; i < this.ncomp - 1; i++) {
      int idx1;

      idx1 = this.algidx[i];
      b.set(i, 0, -f[idx1]);
      x0.set(i, 0, rho[idx1]);
      for (j = 0; j < this.ncomp - 1; j++) {
        int idx2;

        idx2 = this.algidx[j];
        A.set(i, j, jac[idx1][idx2]);
      }
    }
    normf = NormOps_DDRM.normP2(b);
    if (normf < this.abstol) {
      return;
    }

    CommonOps_DDRM.solve(A, b, x);
    for (i = 1; i < this.ncomp - 1; i++) {
      double xi;
      xi = x.get(i, 0);
      if (Double.isNaN(xi)) {
        throw new RuntimeException("Update is NaN");
      }
    }
    s = 0.8;
    norm = 1e16;
    for (iter = 0; iter < this.maxit; iter++) {
      CommonOps_DDRM.elementDiv(x, x0, c);
      norm0 = norm;
      norm = NormOps_DDRM.normP2(c);
      if (norm < norm0) {
        s = Math.min(0.8, 1.2 * s);
      }
      if (norm < this.normtol || normf < this.abstol || normf < this.reltol) {
        // System.out.printf("norm(delta_rho/rho_k): %e, norm(f): %e\n", norm, normf);
        break;
      }
      double delta;

      for (i = 0; i < this.ncomp - 1; i++) {
        delta = x.get(i, 0);
        if ((rho[this.algidx[i]] + s * delta) < 0) {
          s = Math.min(s, -0.5 * rho[this.algidx[i]] / delta);
          // System.out.printf("s: %e\n", s);
        }
      }
      for (i = 0; i < this.ncomp - 1; i++) {
        delta = x.get(i, 0);
        rho[this.algidx[i]] += s * delta;
        x0.set(i, 0, rho[this.algidx[i]]);
      }
      GTSurfaceTensionUtils.mufun(this.sys, this.ncomp, this.t, rho, mu, dmu_drho, p);

      fjacfun(mu, dmu_drho, f, jac);

      for (i = 0; i < this.ncomp - 1; i++) {
        int idx1;

        idx1 = this.algidx[i];
        b.set(i, 0, -f[idx1]);
        for (j = 0; j < this.ncomp - 1; j++) {
          int idx2;

          idx2 = this.algidx[j];
          A.set(i, j, jac[idx1][idx2]);
        }
      }
      CommonOps_DDRM.solve(A, b, x);
      normf = NormOps_DDRM.normP2(b);
    }
    if (iter >= this.maxit) {
      // System.out.printf("norm(f): %e\n", normf);
      for (i = 0; i < this.ncomp - 1; i++) {
        logger.info("f[" + i + "]: " + f[this.algidx[i]]);
      }
      throw new RuntimeException("Failed to solve for density");
    }
  }

  /**
   * Residual function for the algebraic equilibrium equations.
   *
   * @param mu an array of type double
   * @param dmu_drho an array of type double
   * @param f an array of type double
   * @param jac an array of type double
   */
  public void fjacfun(double[] mu, double[][] dmu_drho, double[] f, double[][] jac) {
    int i;
    int j;
    double delta_muref;
    double sqrtcref;

    double sqrtci;
    double scale;
    delta_muref = (this.mueq[this.refcomp] - mu[this.refcomp]);
    sqrtcref = Math.sqrt(this.ci[this.refcomp]);
    scale = 1.0 / sqrtcref;
    for (i = 0; i < this.ncomp; i++) {
      sqrtci = Math.sqrt(this.ci[i]);
      f[i] = scale * (sqrtci * delta_muref - sqrtcref * (this.mueq[i] - mu[i]));
      for (j = 0; j < this.ncomp; j++) {
        jac[i][j] = scale * (sqrtci * (-dmu_drho[this.refcomp][j]) - sqrtcref * (-dmu_drho[i][j]));
      }
    }
  }
}
