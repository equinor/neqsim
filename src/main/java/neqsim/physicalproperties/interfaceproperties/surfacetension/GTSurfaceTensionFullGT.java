package neqsim.physicalproperties.interfaceproperties.surfacetension;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;
import no.uib.cipr.matrix.BandMatrix;
import no.uib.cipr.matrix.DenseMatrix;

/**
 * <p>
 * GTSurfaceTensionFullGT class. Solving for the surface tension by direct Newton method. <br>
 * TODO: Make use of binary interaction parameter for the influence parameter \f$\beta_{ij}\f$ when
 * this becomes available in NeqSIM API.
 * </p>
 *
 * @author Olaf Trygve Berglihn olaf.trygve.berglihn@sintef.no
 * @author John C. Morud john.c.morud@sintef.no
 * @version $Id: $Id
 */
public class GTSurfaceTensionFullGT {
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(GTSurfaceTensionFullGT.class);

  private int ncomp; // Number of components.
  private SystemInterface sys; // Local work copy of flashed system.
  private double[] ci; // Influence parameter.
  private double[] mueq; // Chemical potentials at equilibrium.
  private double[] rho_ph1; // Density in bulk phase 1.
  private double[] rho_ph2; // Density in bulk phase 2.
  private double t; // Temperature.
  private double[] p0; // Bulk pressure [Pa]
  private static final double Pa = 1e-5; // Scaling factor for NeqSIM conversion.
  private static final double m3 = 1e-5; // Scaling factor for NeqSIM conversion.
  public double normtol = 1e-11;
  public double reltol = 1e-6;
  public double abstol = 1e-6;
  public int maxit = 40;
  private static final boolean NDEBUG = true; // Set to false for debug mode.
  private static final boolean DEBUGPLOT = false; // Set to true for profile plot.

  /**
   * <p>
   * Constructor for GTSurfaceTensionFullGT.
   * </p>
   *
   * @param flashedSystem a {@link neqsim.thermo.system.SystemInterface} object
   * @param phase1 a int
   * @param phase2 a int
   */
  public GTSurfaceTensionFullGT(SystemInterface flashedSystem, int phase1, int phase2) {
    int i = 0;

    // Setup local system clone and some parameters.
    this.sys = flashedSystem.clone();

    this.ncomp = this.sys.getPhase(0).getNumberOfComponents();
    this.t = this.sys.getPhase(0).getTemperature();
    this.rho_ph1 = new double[this.ncomp];
    this.rho_ph2 = new double[this.ncomp];
    this.p0 = new double[1];
    this.ci = new double[this.ncomp];
    this.mueq = new double[this.ncomp];

    boolean hasAddedComp = false;
    for (i = 0; i < this.ncomp; i++) {
      if (this.sys.getPhase(phase1).getComponent(i).getx() < Double.MIN_VALUE) {
        this.sys.addComponent(i, this.sys.getTotalNumberOfMoles() / 1e10);
        hasAddedComp = true;
      }
    }
    if (hasAddedComp) {
      neqsim.thermodynamicoperations.ThermodynamicOperations ops =
          new neqsim.thermodynamicoperations.ThermodynamicOperations(this.sys);
      ops.TPflash();
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
    }
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
    }
    this.sys.setTotalFlowRate(1.0, "mol/sec");
    // added by Even S 18/02/2020 (can not set molar composition if total flow is zero
    this.sys.setMolarComposition(nv);
    this.sys.init_x_y();
    this.sys.setBeta(1.0);
    this.sys.init(3);
  }

  /**
   * Calculation of the interfacial tension.
   *
   * @return Interfacial tension in N/m.
   */
  public double runcase() {
    int i;
    int j;
    int k;
    double[][] cij = new double[ncomp][ncomp];
    double[] delta_mu = new double[this.ncomp];
    double[][] dmu_drho = new double[this.ncomp][this.ncomp];
    double sigma = 0.0;
    double std; // Width of integrand

    int Nlevel = 3; // Hard coded
    // int NN;
    int N1;
    int N2;
    int Ngrid = (1 << Nlevel) + 1; // 2^(Nlevel) + 1
    double[][] rhomat = new double[Ngrid][this.ncomp];
    double[][] rhotmp;
    double[] xgrid;

    int Nrefinements = 7;
    double StopTolerance = 0.01; // Stop if rel change smaller than this
    double sigma_old = 9.9e99;

    // Geometry
    double nm = 1.0; // Use nm as length scale
    double L = 3 * nm; // Domain is -L < z < L

    // Newton parameters
    int N_Newton = 20; // Max Newton iterations per level
    double maxRelChange = 0.5; // 5; %Only allow relative change in solution below this
    boolean highOrder = true; // Use deferred correction approach
    boolean directMethod = true; // Use direct solver (May not be implemented in Java.)

    // For grid remeshing:
    double H; // Mesh spacing
    double Lnew; // Half-width of domain
    double[] xgridNew; // Grid
    double alpha; // Interpolation weight
    double[][] drhodz; // Density gradient
    int Nhalf; // Half the number of intervals
    int Nideal; // Ideal number of grid points, current spacing
    int NgridNew; // Doubled mesh
    int kk;

    // NN=2^Nlevel+1;
    // rhomat = new double[2^Nlevel+1][this.ncomp]; //Starting size
    initmu(sys, ncomp, t, rho_ph1, rho_ph2, mueq, p0, reltol);

    // -----------------------------
    // Set up initial solution estimate
    N2 = 1 << (Nlevel - 1); // 2^(Nlevel-1) // N2=(int)Math.pow(2,(Nlevel-1));
    for (i = 0; i < N2; i++) {
      rhomat[i] = this.rho_ph1.clone();
    }

    N1 = N2;
    N2 = 1 + (1 << Nlevel); // (int)(Math.pow(2,Nlevel))+1;
    for (i = N1; i < N2; i++) {
      rhomat[i] = this.rho_ph2.clone();
    }

    // -----------------------------
    // Get interaction parameters (Already done???) FIXME!!
    for (i = 0; i < ncomp; i++) {
      // this.sys.getPhase(0).getComponent(i-1).getSurfaceTenisionInfluenceParameter(this.sys.getPhase(0).getTemperature());
      this.ci[i] = this.sys.getPhase(0).getComponent(i).getSurfaceTenisionInfluenceParameter(t);
    }

    // TODO: Change to \f$c_{ij} = (1-\beta_{ij})\sqrt{c_ic_j}\f$ when a
    // NeqSIM function for evaluating \f$\beta_{ij}\f$ becomes available.
    for (i = 0; i < ncomp; i++) {
      for (j = i; j < ncomp; j++) {
        cij[i][j] = 1.e18 * Math.sqrt(this.ci[i] * this.ci[j]); // Use nm as unit for length
        cij[j][i] = cij[i][j];
      }
    }
    // ----------------------------

    // Test the delta_mu routine
    delta_mu(this.sys, this.ncomp, this.t, this.mueq, this.rho_ph1, delta_mu, dmu_drho);
    xgrid = linspace(-L, L, Ngrid);

    for (i = 0; i < Nrefinements; i++) {
      sigma = Newton(cij, L, N_Newton, maxRelChange, highOrder, directMethod, rhomat, sys, ncomp, t,
          mueq);
      if (!NDEBUG && DEBUGPLOT) {
        debugPlot(xgrid, rhomat); // create a plot using xaxis and yvalues
      }
      // Calculate size of interesting region
      drhodz = new double[rhomat.length - 1][ncomp];
      // sigma=sigmaCalc(xgrid[1]-xgrid[0],rhomat, cij, false, drhodz);
      sigma = sigmaCalc(xgrid[1] - xgrid[0], rhomat, cij, false, drhodz, ncomp);
      std = calc_std_integral(xgrid, cij, drhodz); // Width of integrand

      // Termination
      if (i > 0 && Math.abs(sigma - sigma_old) < StopTolerance * sigma) {
        break;
      } else {
        sigma_old = sigma;
      }

      // Remesh
      // N = size(rhotmp, 2);
      H = 2.0 * L / (Ngrid - 1.0); // Grid size
      Nhalf = (int) Math.round(Math.ceil(6.0 * std / H)); // Set domain to +/- 6 times std
      Nideal = 2 * Nhalf + 1; // Ideal number of grid points, current H
      Lnew = Nhalf * H; // New half-width

      NgridNew = 2 * Nideal - 1; // Double #grid points
      xgridNew = linspace(-Lnew, Lnew, NgridNew); // New mesh

      kk = 0;
      rhotmp = new double[NgridNew][this.ncomp];

      // Interpolate old solution to new:
      for (j = 0; j < rhotmp.length; j++) {
        if (xgridNew[j] < xgrid[0]) { // Pad if outside left boundary
          rhotmp[j] = this.rho_ph1.clone();
        } else if (xgridNew[j] > xgrid[xgrid.length - 1]) { // Pad right
          rhotmp[j] = this.rho_ph2.clone();
        } else { // Interpolate known region
          while (xgridNew[j] > xgrid[kk + 1]) { // Find correct interval (Should be safe)
            kk++;
          }

          alpha = (xgrid[kk + 1] - xgridNew[j]) / (xgrid[kk + 1] - xgrid[kk]);
          for (k = 0; k < this.ncomp; k++) { // Linear interpolation
            rhotmp[j][k] = alpha * rhomat[kk][k] + (1 - alpha) * rhomat[kk + 1][k];
          }
        }
      }

      // Update the current guess
      rhotmp[0] = this.rho_ph1.clone();
      rhotmp[rhotmp.length - 1] = this.rho_ph2.clone();
      L = Lnew;

      rhomat = rhotmp;
      Ngrid = NgridNew;
      xgrid = xgridNew;
      if (!NDEBUG && DEBUGPLOT) {
        debugPlot(xgrid, rhomat);
      }
      // End Remesh
    }
    return sigma;
  }

  /**
   * <p>
   * Newton. Calculate surface tension by full gradient method and Newtons method
   *
   * The routine solves the Finite Difference equations for the full Gradient Theory by Newtons
   * method. Note that the length coordinate is in nm-units. Method: 1. Calculate delta_mu and its
   * Jacobian 2. Call routine "directsolve" for Newton step 3. Dampen the step if relative step too
   * large 4. Iterate until convergence or max #iterations (N_Newton)
   * </p>
   *
   * @param cij an array of type double
   * @param L a double
   * @param N_Newton a int
   * @param allowedRelChange a double
   * @param highOrder a boolean
   * @param directMethod a boolean
   * @param rhomat an array of type double
   * @param sys a {@link neqsim.thermo.system.SystemInterface} object
   * @param ncomp a int
   * @param t a double
   * @param mueq an array of type double
   * @return sigma The surface tension [N/m]
   */
  public static double Newton(double[][] cij, double L, int N_Newton, double allowedRelChange,
      boolean highOrder, boolean directMethod, double[][] rhomat, SystemInterface sys, int ncomp,
      double t, double[] mueq) {
    int i;
    int j;
    int k;
    int NewtonStep;
    int Ngrid = rhomat.length;
    double H = 2.0 * L / (Ngrid - 1); // Grid spacing
    double[][][] Jac = new double[Ngrid][ncomp][ncomp];
    double[][] dmu = new double[Ngrid][ncomp];
    double[][] rres = new double[Ngrid][ncomp];
    double[][] rrho_prev = new double[Ngrid][ncomp];
    double[][] drhodz = new double[Ngrid - 1][ncomp];
    double maxrelchange;
    double urel_Newton;
    double sigma = 0.0;

    double[] xgrid = linspace(-L, L, Ngrid);
    double tmp;
    double sum_ztmp;
    double sum_tmp;
    double cg;
    int icorr;

    // Process initial array
    for (i = 0; i < Ngrid; i++) {
      delta_mu(sys, ncomp, t, mueq, rhomat[i], dmu[i], Jac[i]);
      // rres[i]=Matrix.subtract(dmu[i],Matrix.multiply(Jac[i],rhomat[i]));
      for (j = 0; j < ncomp; j++) {
        rres[i][j] = dmu[i][j];
        for (k = 0; k < ncomp; k++) {
          rres[i][j] -= Jac[i][j][k] * rhomat[i][k];
        }
      }
    }

    // FIXME: Add deferred correction here
    if (highOrder) {
      for (i = 1; i < Ngrid - 1; i++) { // Inner grid points
        for (j = 0; j < ncomp; j++) {
          rres[i][j] += (dmu[i - 1][j] - 2.0 * dmu[i][j] + dmu[i + 1][j]) / 12.0;
        }
      }
    }

    for (NewtonStep = 0; NewtonStep < N_Newton; NewtonStep++) { // for NewtonStep=1:N_Newton
      // rrho_prev = rhomat.clone(); // rrho_prev = rrho{1}; %Previous solution. Clone
      // not a good idea...
      for (i = 0; i < Ngrid; i++) {
        for (j = 0; j < ncomp; j++) {
          rrho_prev[i][j] = rhomat[i][j];
        }
      }

      // if directMethod
      directsolve(rres, Jac, cij, H, Ngrid, rhomat, ncomp);

      // %Limit the Newton step by max relative change in solution
      maxrelchange = 0.0;
      for (i = 1; i < Ngrid - 1; i++) { // Inner grid points
        for (j = 0; j < ncomp; j++) { // Each specie
          if (Math.abs(rhomat[i][j] - rrho_prev[i][j]) > maxrelchange * Math.abs(rrho_prev[i][j])) {
            maxrelchange = Math.abs(rhomat[i][j] - rrho_prev[i][j]) / Math.abs(rrho_prev[i][j]);
          }
        }
      }
      urel_Newton = Math.min(allowedRelChange / maxrelchange, 1.0);

      // Update solution
      for (i = 1; i < Ngrid - 1; i++) { // Inner grid points
        for (j = 0; j < ncomp; j++) { // Each specie
          rhomat[i][j] = urel_Newton * rhomat[i][j] + (1.0 - urel_Newton) * rrho_prev[i][j];
        }
      }

      if (urel_Newton * maxrelchange < 0.00001) { // Convergence test
        // FIXME: Hardcoded tolerance
        break;
      }

      sigma = sigmaCalc(H, rhomat, cij, false, drhodz, ncomp);

      // System.out.printf("Sigma=%.12f\n",sigma);
      if (!NDEBUG) {
        logger.info("Sigma= " + sigma);
      }

      sum_ztmp = 0.0;
      sum_tmp = 0.0;
      for (i = 0; i < drhodz.length; i++) {
        tmp = 0.0;
        for (j = 0; j < ncomp; j++) {
          for (k = 0; k < ncomp; k++) {
            tmp += drhodz[i][j] * cij[j][k] * drhodz[i][k];
          }
        }
        sum_ztmp += 0.5 * (xgrid[i + 1] + xgrid[i]) * tmp;
        sum_tmp += tmp;
      }
      cg = sum_ztmp / sum_tmp; // Center of gravity

      icorr = (int) Math.round(cg / H); // Predicted shift to keep centered.
      if (icorr > 0) { // Shift left. Fill in with right boundary values
        for (i = 1; i < Ngrid - icorr; i++) {
          rhomat[i] = rhomat[i + icorr];
        }
        for (i = Ngrid - icorr; i < Ngrid - 1; i++) {
          rhomat[i] = rhomat[Ngrid - 1].clone();
        }
      } else if (icorr < 0) { // Shift right
        icorr = -icorr;
        for (i = Ngrid - 2; i >= icorr; i--) {
          rhomat[i] = rhomat[i - icorr];
        }
        for (i = 1; i < icorr; i++) {
          rhomat[i] = rhomat[0].clone();
        }
      }

      // Update the linearization
      for (i = 1; i < Ngrid - 1; i++) { // Inner grid points
        delta_mu(sys, ncomp, t, mueq, rhomat[i], dmu[i], Jac[i]);

        for (j = 0; j < ncomp; j++) {
          rres[i][j] = dmu[i][j];
          for (k = 0; k < ncomp; k++) {
            rres[i][j] -= Jac[i][j][k] * rhomat[i][k];
          }
        }
      }

      // %Deferred correction
      if (highOrder) {
        for (i = 1; i < Ngrid - 1; i++) { // Inner grid points
          for (j = 0; j < ncomp; j++) {
            rres[i][j] += (dmu[i - 1][j] - 2.0 * dmu[i][j] + dmu[i + 1][j]) / 12.0;
          }
        }
      }
    }

    return sigma;
  }

  /**
   * <p>
   * directsolve. Solve linear system for Full Gradient method
   * </p>
   *
   * @param rres an array of type double
   * @param JJ an array of type double
   * @param C an array of type double
   * @param H a double
   * @param Ngrid a int
   * @param rhomat an array of type double
   * @param ncomp a int
   */
  public static void directsolve(double[][] rres, double[][][] JJ, double[][] C, double H,
      int Ngrid, double[][] rhomat, int ncomp) {
    int i;
    int j;
    int k;
    double H2 = H * H;
    double bbtmp;
    int Neq = (Ngrid - 2) * ncomp;
    int iglob;
    int skip;
    int kl = 2 * ncomp + 1;
    int ku = 2 * ncomp + 1;

    BandMatrix Jac = new BandMatrix(Neq, kl, ku);
    DenseMatrix bb = new DenseMatrix(Neq, 1);

    // Construct right hand side
    iglob = 0; // Global index
    for (i = 1; i < Ngrid - 1; i++) { // Inner grid points
      for (j = 0; j < ncomp; j++) { // Each specie
        bbtmp = rres[i][j];
        for (k = 0; k < ncomp; k++) { // Each specie
          // bb[i][j] = rhomat[i][j] + JJ[i][j][k]*rhomat[i][k] -
          // C[j][k]*(rhomat[i][k-1]-2*rhomat[i][k]+rhomat[i][k+1])/(H*H) //Loop k
          bbtmp += JJ[i][j][k] * rhomat[i][k]
              - C[j][k] * (rhomat[i - 1][k] - 2 * rhomat[i][k] + rhomat[i + 1][k]) / H2;
        }
        bb.set(iglob++, 0, -bbtmp);
      }
    }

    // Add block by block to the jacobian
    i = 1; // Grid point 2 is the first unknown
    for (j = 0; j < ncomp; j++) { // Each specie
      for (k = 0; k < ncomp; k++) { // Each specie
        // Jac[j][k]=JJ[i][j][k]+2.0/H2*C[j][k];
        // Jac[j][k+ncomp] = -1.0/H2*C[j][k];
        Jac.set(j, k, JJ[i][j][k] + 2.0 / H2 * C[j][k]);
        Jac.set(j, k + ncomp, -1.0 / H2 * C[j][k]);
      }
    }

    for (i = 2; i < Ngrid - 2; i++) { // Inner grid points
      skip = ncomp * (i - 1);
      for (j = 0; j < ncomp; j++) { // Each specie
        for (k = 0; k < ncomp; k++) { // Each specie
          // Jac[j+skip][k+skip-ncomp] = -1.0/H2*C[j][k];
          // Jac[j+skip][k+skip]=JJ[i][j][k]+2.0/H2*C[j][k];
          // Jac[j+skip][k+skip+ncomp] = -1.0/H2*C[j][k];
          Jac.set(j + skip, k + skip - ncomp, -1.0 / H2 * C[j][k]);
          Jac.set(j + skip, k + skip, JJ[i][j][k] + 2.0 / H2 * C[j][k]);
          Jac.set(j + skip, k + skip + ncomp, -1.0 / H2 * C[j][k]);
        }
      }
    }

    i = Ngrid - 2; // Last unknown grid point
    skip = ncomp * (i - 1);
    for (j = 0; j < ncomp; j++) { // Each specie
      for (k = 0; k < ncomp; k++) { // Each specie
        // Jac[j+skip][k+skip-ncomp] = -1.0/H2*C[j][k];
        // Jac[j+skip][k+skip]=JJ[i][j][k]+2.0/H2*C[j][k];
        Jac.set(j + skip, k + skip - ncomp, -1.0 / H2 * C[j][k]);
        Jac.set(j + skip, k + skip, JJ[i][j][k] + 2.0 / H2 * C[j][k]);
      }
    }

    DenseMatrix drho = new DenseMatrix(Neq, 1);
    Jac.solve(bb, drho);

    iglob = 0;
    for (i = 1; i < Ngrid - 1; i++) { // Inner grid points
      for (j = 0; j < ncomp; j++) { // Each specie
        bbtmp = drho.get(iglob++, 0); // FIXME: Only debugging
        rhomat[i][j] += bbtmp; // drho.get(iglob++);
      }
    }
  }

  /**
   * <p>
   * sigmaCalc. Calculates the interface tension
   *
   * The following integral is solved with the trapezoidal method: \f{equation}{ \sigma =
   * \int_{-\infty}^{\infty} \boldsymbol{n_z}^T \boldsymbol{C} \boldsymbol{n_z} \, dz \f}
   * </p>
   *
   * @param h a double
   * @param rrho an array of type double
   * @param C an array of type double
   * @param highOrder a boolean
   * @param drhodz an array of type double
   * @param ncomp a int
   * @return sigma The surface tension [N/m]
   */
  public static double sigmaCalc(double h, double[][] rrho, double[][] C, boolean highOrder,
      double[][] drhodz, int ncomp) {
    int i;
    int j;
    int k;
    double drho2; // For each pair of species j,k, drho2=sum_i{drhodz[i][j]*drhodz[i][k]}
    double sigma;

    for (i = 0; i < rrho.length - 1; i++) {
      for (j = 0; j < ncomp; j++) {
        drhodz[i][j] = (rrho[i + 1][j] - rrho[i][j]) / h;
      }
    }

    // drhodz[i][j]*C(j,k)*drhodz[i][k]
    sigma = 0.0;
    for (j = 0; j < ncomp; j++) {
      for (k = 0; k < ncomp; k++) {
        drho2 = 0.0;
        for (i = 0; i < rrho.length - 1; i++) {
          drho2 += drhodz[i][j] * drhodz[i][k];
        }
        sigma += C[j][k] * drho2;
      }
    }
    sigma *= h * 1.0e-9; // Multiply grid spacing, convert for nm-unit

    // FIXME: Add Richardson interpolation
    return sigma;
  }

  /**
   * <p>
   * calc_std_integral. Calculate width of interface region (length scale)
   *
   * Estimate the width of interface by calculating the second moment of the surface tension
   * integrand. Used to adjust the domain size
   * </p>
   *
   * @param z an array of type double
   * @param C an array of type double
   * @param drhodz an array of type double
   * @return Interface width (length scale) [nm]
   */
  public double calc_std_integral(double[] z, double[][] C, double[][] drhodz) {
    double h1 = z[1] - z[0];
    double mean;
    int Ngrid = drhodz.length + 1;
    int j;

    int m;
    int n;
    double zdum = 0.0;

    double z2dum = 0.0;
    double sumdum = 0.0;
    double dum;
    for (j = 0; j < Ngrid - 1; j++) {
      dum = 0.0;
      for (m = 0; m < ncomp; m++) {
        for (n = 0; n < ncomp; n++) {
          dum = drhodz[j][m] * C[m][n] * drhodz[j][n];
        }
      }
      sumdum += dum * h1;
      zdum += dum * (z[j + 1] * z[j + 1] - z[j] * z[j]) / 2.0;
      z2dum += dum * (z[j + 1] * z[j + 1] * z[j + 1] - z[j] * z[j] * z[j]) / 3.0;
    }

    mean = zdum / sumdum;
    return Math.sqrt(z2dum / sumdum - mean * mean);
  }

  /**
   * <p>
   * delta_mu. Calculate \f$\Delta\mu=\mu-\mu_0\f$ and its number density derivative.
   * </p>
   *
   * @param sys a {@link neqsim.thermo.system.SystemInterface} object
   * @param ncomp a int
   * @param t a double
   * @param mueq an array of type double
   * @param rho an array of type double
   * @param delta_mu an array of type double
   * @param dmu_drho an array of type double
   */
  public static void delta_mu(SystemInterface sys, int ncomp, double t, double[] mueq, double[] rho,
      double[] delta_mu, double[][] dmu_drho) {
    int i;
    double[] pdummy = new double[ncomp];
    double[] mu = new double[ncomp];

    GTSurfaceTensionUtils.mufun(sys, ncomp, t, rho, mu, dmu_drho, pdummy);

    for (i = 0; i < ncomp; i++) {
      delta_mu[i] = mu[i] - mueq[i];
    }
  }

  /**
   * <p>
   * linspace. Make an array of double with N values linearly spaced between a and b.
   * </p>
   *
   * @param a start of range
   * @param b end of range
   * @param N number of values.
   * @return an array of type double
   */
  public static double[] linspace(double a, double b, int N) {
    double[] x = new double[N];
    double dx = (b - a) / (N - 1);
    int i;

    for (i = 0; i < N; i++) {
      x[i] = a + i * dx;
    }
    return x;
  }

  /**
   * <p>
   * debugPlot. Plotting of density profiles y over the domain x.
   * </p>
   *
   * @param x abscissa values
   * @param y array of ordinate value arrays.
   */
  public static void debugPlot(double[] x, double[][] y) {
    int N = y.length;
    int M = y[0].length;
    int i;
    int j;
    double[] yy = new double[N];

    for (j = 0; j < M; j++) {
      for (i = 0; i < N; i++) {
        yy[i] = Math.log10(y[i][j]);
      }
      if (j == 0) {
        // plot(x, yy); // removed EasyJccKit.jar
      } else {
        // addPlot(x, yy); // removed EasyJccKit.jar
      }
    }
  }

  /**
   * <p>
   * initmu. Initialize equilibrium chemical potential, and derivative and test that the bulk
   * equilibrium is satisfied.
   * </p>
   *
   * @param sys a {@link neqsim.thermo.system.SystemInterface} object
   * @param ncomp a int
   * @param t a double
   * @param rho_ph1 an array of type double
   * @param rho_ph2 an array of type double
   * @param mueq an array of type double
   * @param p0 an array of type double
   * @param reltol a double
   */
  public static void initmu(SystemInterface sys, int ncomp, double t, double[] rho_ph1,
      double[] rho_ph2, double[] mueq, double[] p0, double reltol) {
    int i;
    double maxerr = 0.;
    double[][] dmu_drho1 = new double[ncomp][ncomp];
    double[][] dmu_drho2 = new double[ncomp][ncomp];
    double[] mueq2 = new double[ncomp];

    GTSurfaceTensionUtils.mufun(sys, ncomp, t, rho_ph1, mueq, dmu_drho1, p0);
    GTSurfaceTensionUtils.mufun(sys, ncomp, t, rho_ph2, mueq2, dmu_drho2, p0);

    // Check flash equilibrium
    for (i = 0; i < ncomp; i++) {
      maxerr = Math.max(maxerr, Math.abs(mueq[i] / mueq2[i] - 1.0));
    }
    if (maxerr > reltol) {
      logger.warn("Flash is not properly solved.  Maximum relative error in chemical potential:  "
          + maxerr + " > " + reltol);
      throw new RuntimeException("Flash not solved!");
    }
  }
}
