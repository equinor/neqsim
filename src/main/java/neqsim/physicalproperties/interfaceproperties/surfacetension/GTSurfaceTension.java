/*
 * GTSurfaceTension.java
 *
 * Created on 13. august 2001, 13:14
 */

package neqsim.physicalproperties.interfaceproperties.surfacetension;

import org.apache.commons.math3.ode.nonstiff.DormandPrince54Integrator;
import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * GTSurfaceTension class. Calculates the surfacetension using the Gradient Theory for mixtures. The
 * method assumes the number of components to be two or more, and that the species set is equal and
 * in the same component order for both phases.
 *
 * Near a fluid-fluid interface, we consider the variation of densities of chemical species,
 * \f$\boldsymbol{n}(z)\f$, as function of position, \f$z\f$, where element \f$k\f$ of the vector
 * \f$\boldsymbol{n}(z)\f$ is the number density of chemical species \f$k\f$ in the mixture. The
 * surface tension is a functional of these densities as explained below.
 *
 * The density profiles near the interface are such that the total Helmholz energy of the system is
 * minimized. The effect of spatial density variations on the Helmholz energy is approximated by a
 * second order Taylor series expansion around the homogeneous fluid of the same composition.
 *
 * \f{equation}{ H=\int_{-\infty}^{\infty} h \, dz=\int_{-\infty}^{\infty} \left( h_0 +
 * \frac{1}{2}\boldsymbol{n_z}^T \boldsymbol{C} \boldsymbol{n_z} \right) dz \f}
 *
 * where \f$z\f$ is the spatial coordinate normal to the interface, \f$h_0\f$ is the Helmholz energy
 * of the homogeneous fluid, \f$\boldsymbol{n_z}=\frac{d\boldsymbol{n}}{dz}\f$ is the local density
 * gradient and the matrix of influence parameters \f$\boldsymbol{C}\f$ is the hessian of \f$h\f$
 * with respect to \f$\boldsymbol{n_z}\f$. The influence parameters, \f$\boldsymbol{C}\f$, are
 * normally assumed to be very weak functions of composition, often taken as constant through the
 * interface.
 *
 * The integral is at its minimum when the densities satisfy the Euler-Lagrange equation
 *
 * \f{equation}{ \boldsymbol{C}\frac{d^2\boldsymbol{n}}{dz^2} = \mu_0 \left( \boldsymbol{n} \right)
 * - \mu_{\infty} \equiv \Delta \mu \left( \boldsymbol{n} \right) \f}
 *
 * where \f$\mu_0\f$ is the chemical potential of the homogeneous fluid and \f$\mu_{\infty}\f$ is
 * the chemical potential far from the interface (on either side, as the two fluids in contact are
 * assumed to be at equilibrium). Having solved equation for the density profiles,
 * \f$\boldsymbol{n}\left(z\right)\f$, the surface tension can be calculated from: \f{equation}{
 * \sigma = \int_{-\infty}^{\infty} \boldsymbol{n_z}^T \boldsymbol{C} \boldsymbol{n_z} \, dz \f}
 *
 * The class uses two different methods based on the value of this.useFullGT. If this variable is
 * set to 0, the surface tension is calculated by solving set of differential algebraic equations
 * using the component density of the highest boiling component in the mixture as the integration
 * variable. This method can only be used if the component density varies monotonically over the
 * interface and the binary interaction parameter for the influence parameter is such that
 *
 * \f{equation}{ \begin{cases} \beta_{ij} = 1 &amp; i = j\\ \beta_{ij} = 0 &amp; i\neq j \end{cases}
 * \f}
 *
 * If these conditions are satisfied, this method is robust and has an acceptable numerical cost
 * (order of seconds for calculation).
 *
 * For the general case, where the monotonicity can not be guaranteed or where \f$\beta_{ij}\f$ is
 * nonzero for \f$i\neq j\f$ the full set of equations for the gradient theory must solved in the
 * domain of the interface thickness. This is significantly more numerically intensive but handles
 * the general case. To calculate the surface tension by this method, set the variable
 * this.useFullGT=1
 * </p>
 *
 * @author Olaf Trygve Berglihn olaf.trygve.berglihn@sintef.no
 * @author John C. Morud john.c.morud@sintef.no
 * @version $Id: $Id
 */
public class GTSurfaceTension extends SurfaceTension {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  int useFullGT = 1; // 1 will use full gradient theory 0 - will use ODE solver and one component
                     // assumed linear

  /**
   * <p>
   * Constructor for GTSurfaceTension.
   * </p>
   */
  public GTSurfaceTension() {}

  /**
   * <p>
   * Constructor for GTSurfaceTension.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   */
  public GTSurfaceTension(SystemInterface system) {
    super(system);
  }

  /** {@inheritDoc} */
  @Override
  public double calcSurfaceTension(int interface1, int interface2) {
    SystemInterface localSystem;
    double surftens = 0.0;
    int refcompIndex = 0;

    localSystem = this.system.clone();
    switch (useFullGT) {
      case 1:
        surftens = solveFullDensityProfile(localSystem, interface1, interface2);
        break;
      default:
        refcompIndex = this.getComponentWithHighestBoilingpoint();
        surftens = solveWithRefcomp(localSystem, interface1, interface2, refcompIndex);
    }
    return surftens;
  }

  /**
   * <p>
   * solveWithRefcomp. Solve for the surface tension by integration in a reference component density
   * for cases with no binary interaction parameter in the influcence parameter.
   *
   * The pertinent equations for calculating the interfacial tension according to Gradient Theory
   * can be represented by the system of equations below, providing that the reference component
   * number density varies monotonically through the interface, and that the joint influence
   * parameter \f$c_{ij}\f$ can be described by a quadratic mixing rule, without a binary
   * interaction parameter. The transformation of the integration variable from spacial position in
   * the interface to the number density of a reference component simplifies the differential
   * algebraic problem to an initial value problem with finite bounds. It is usually not known in
   * advance whether the chosen reference component number density varies monotonically over the
   * interface, and caution should be used in interpreting the results.
   *
   * \f{gather}{ \sigma = \int_{\rho_\text{ref}^\text{vap}}^{\rho_\text{ref}^\text{liq}}
   * \sqrt{2\Delta\Omega(\boldsymbol{\rho})\sum_i\sum_j c_{ij}
   * \frac{\partial\rho_i}{\partial\rho_\text{ref}} \frac{\partial\rho_j}{\partial\rho_\text{ref}}}
   * \mathrm{d}\rho_\text{ref}\\ 0 = \sqrt{c_i}\left(\mu_\text{ref}^\text{eq}
   * -\mu_\text{ref}(\boldsymbol{\rho})\right) -\sqrt{c_\text{ref}}
   * \left(\mu_i^\text{eq}-\mu_i(\boldsymbol{\rho})\right)\\ c_i = \left(A_it_i +
   * B_i\right)a_ib_i^{\frac{2}{3}}\\ t_i = 1-T/T_{C,i}\\ A_i = \frac{1}{\xi_1 + \xi_2\omega_i}\\
   * B_i = \frac{1}{\xi_3 + \xi_4\omega_i}\\ c_{ij} = \sqrt{c_ic_j} \f}
   *
   * Here \f$\sigma\f$ is the interfacial tension, \f$\rho\f$ is the number density, \f$\Omega\f$ is
   * the grand canonical potential, \f$\mu_i\f$ is the chemical potential of component, \f$i\f$, and
   * \f$c_i\f$ and \f$c_{ij}\f$ is the influence and cross-influence parameter. The parameters
   * \f$\xi\f$ must be fitted to the equation of state used.
   *
   * Since the monotonicity of a reference component in general can not be guaranteed, a more
   * general and safer approach would be to solve the system in the spacial domain. This implies
   * solving a boundary value problem with infinite bounds. Although asymptotic arguments leads to
   * simplifications, solving this problem induces a higher numerical cost and complexity.
   *
   * This method solves the surface tension integral using a reference component density as the
   * integration variable as described above. The equilibrium relations are solved as a set of
   * algebraic equations for each evaluation of the differential. The resulting ordinary
   * differential equation is integrated with a non-stiff Runge-Kutta class Dormand Prince method,
   * similar to the MATLAB routine ode45.
   * </p>
   *
   * @param system NeqSIM system interface
   * @param interface1 Index of the phase to consider for the surface
   * @param interface2 Index of the phase to consider for the surface
   * @param refcompIndex Index of the reference component to use when using a component density as
   *        integration variable.
   * @return Surface tension in units of N/m.
   */
  public static double solveWithRefcomp(SystemInterface system, int interface1, int interface2,
      int refcompIndex) {
    GTSurfaceTensionODE odesystem;
    double yscale = 20.0;
    double t, t0, minstep, maxstep, reltol, abstol;
    double[] y0 = new double[1];
    double[] y = new double[1];
    double surftens;

    /*
     * Setup system of ODE-equations. Abscissa variable number density of the reference component is
     * scaled to the range [0,1]. The ordinate variable surface tension is scaled scaled according
     * to the variable yscale.
     */
    odesystem = new GTSurfaceTensionODE(system, interface1, interface2, refcompIndex, yscale);
    t0 = 0;
    t = 1.0;
    y0[0] = 0.0;

    /*
     * Tolerances for the integrator. Note that you can adjust the odesystem.reltol and
     * odesystem.normtol in order to control the internal Newton-Rhapson solver if the odesystem.
     * Tolerances for the odesystem.abstol and .reltol should be less than the integrator tolerances
     * for stability. Default Newton-Rhapson values are odesystem.normtol = 1e-10; odesystem.reltol
     * = 1e-8;
     */
    odesystem.normtol = 1e-10;
    odesystem.reltol = 1e-8;
    minstep = 1e-6;
    maxstep = 1e-2;
    reltol = 1e-4;
    abstol = 1e-4;
    t0 = 1e-6;
    DormandPrince54Integrator integrator =
        new DormandPrince54Integrator(minstep, maxstep, reltol, abstol);
    integrator.integrate(odesystem, t0, y0, t, y);
    surftens = y[0] / yscale;

    return surftens;
  }

  /**
   * <p>
   * solveFullDensityProfile. Calculate the surface tension for the general case by integration of
   * the component density profiles.
   *
   * This method can be used in the general case, including when binary interaction influence
   * parameters \f$\beta_{ij}\f$ are nonzero.
   *
   * On the top level, we solve the Euler-Lagrange equation with Newtons method. Consider solving
   * Equation for \f$\delta\mu\f$ as a Dirichlet problem on a finite domain \f$-L &lt; z &lt; L\f$
   * around the interface. The distance \f$L\f$ should be large enough that it does not disturb the
   * results. The boundary conditions are the homogeneous densities of the two fluids in contact, as
   * calculated by a flash calculation.
   *
   * We approximate the solution on a equi-spaced grid with \f$2^N+1\f$ points where \f$N\f$ is an
   * integer. Using a Finite Difference approximation, the equation for \f$\delta\mu\f$ can then be
   * written as an equation system for the internal grid points, \f$i=2,3,...,2^N\f$:
   *
   * \f{equation}{ \mathbf{0}=\mathbf{F}\left( \mathbf{n}_i \right) \equiv \Delta \mu \left(
   * \mathbf{n}_i \right) - \mathbf{C} \frac{\mathbf{n}_{i-1}
   * -2\mathbf{n}_i+\mathbf{n}_{i+1}}{\Delta z^2}, \quad i=2,3,...,2^N \f}
   *
   * Applying Newtons method on this equation yields the iteration formula for the Newton step:
   * \f{eqnarray}{ \mathbf{J}_i\Delta \mathbf{n}_i - \mathbf{C} \frac{\Delta \mathbf{n}_{i-1}
   * -2\Delta \mathbf{n}_i +\Delta \mathbf{n}_{i+1}}{\Delta z^2} &amp;=&amp; - \mathbf{F}_i\\
   * \mathbf{n}_i &amp;:=&amp; \mathbf{n}_i + \beta \Delta \mathbf{n}_i \f} where
   * \f$\mathbf{J}=\frac{\partial \Delta \mu}{\partial \mathbf{n}_i}\f$ is a Jacobian matrix and
   * \f$\mathbf{F}_i=\mathbf{F}\left( \mathbf{n}_i \right)\f$ is the residual. \f$\beta \le 1\f$ is
   * a damping factor. This is a block tridiagonal system that can be solved using a band solver or
   * a sparse matrix solver.
   * </p>
   *
   * @param system NeqSIM system interface
   * @param interface1 Index of the phase to consider for the surface
   * @param interface2 Index of the phase to consider for the surface
   * @return Surface tension in units of N/m.
   */
  public static double solveFullDensityProfile(SystemInterface system, int interface1,
      int interface2) {
    GTSurfaceTensionFullGT FullGT;

    FullGT = new GTSurfaceTensionFullGT(system, interface1, interface2);
    return FullGT.runcase();
  }
}
