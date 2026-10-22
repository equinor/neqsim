package neqsim.physicalproperties.interfaceproperties.surfacetension;

import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.system.SystemInterface;

/**
 * GTSurfaceTensionUtils class.
 *
 * <p>
 * Collection of general utility functions used by the gradient theory classes.
 *
 * @author Olaf Trygve Berglihn olaf.trygve.berglihn@sintef.no
 * @version $Id: $Id
 */
public class GTSurfaceTensionUtils {
  private static final double Pa = 1e-5;
  private static final double m3 = 1e-5;

  /**
   * Calculate chemical potential, chemical potential derivative, and pressure.
   * <p>
   * Note that the volume/pressure units used in NeqSim renders the number density incorrect by a factor of 1e5. When
   * selecting the volume to unity to avoid caring about volume derivatives when using number density as the free
   * variable, the mole number input to NeqSIM must be scaled by a factor 1e-5. The chemical potential is unaffected by
   * this as it is a intensive property. The chemical potential derivative will scale inversely proportional with the
   * mole numbers according to the Euler homogeneity of the Gibbs energy function.
   * </p>
   *
   * @param sys a {@link neqsim.thermo.system.SystemInterface} object
   * @param ncomp a int
   * @param t a double
   * @param rho an array of type double
   * @param mu an array of type double
   * @param dmu_drho an array of type double
   * @param p an array of type double
   */
  public static void mufun(SystemInterface sys, int ncomp, double t, double[] rho, double[] mu, double[][] dmu_drho,
      double[] p) {
    double v = 1.0;
    double n;
    int i;
    int j;
    PhaseInterface phase;

    double[] nv = new double[ncomp];
    phase = sys.getPhase(0);
    sys.setTemperature(t);
    phase.setTotalVolume(v);

    for (i = 0; i < ncomp; i++) {
      // ComponentInterface component = phase.getComponent(i);
      n = rho[i] * Pa;
      nv[i] = n;
    }
    validateDensities(rho);

    // Set the composition multiple times to overcome round off error
    // in NeqSIM due to n += (n_new - nold)
    // sys.setMolarComposition(nv);
    // sys.setMolarComposition(nv);
    // sys.setMolarComposition(nv);

    sys.setMolarFlowRates(nv);
    // sys.setMolarFlowRates(nv);
    // sys.setMolarFlowRates(nv);

    sys.init_x_y();
    sys.setBeta(1.0);
    sys.init(3);
    validateState(sys.getPhase(0));

    for (i = 0; i < ncomp; i++) {
      mu[i] = sys.getPhase(0).getComponent(i).getChemicalPotential(sys.getPhase(0));
      requireFinite(mu[i], "chemical potential");
      for (j = 0; j < ncomp; j++) {
        dmu_drho[i][j] = sys.getPhase(0).getComponent(i).getChemicalPotentialdNTV(j, sys.getPhase(0)) * Pa;
        requireFinite(dmu_drho[i][j], "chemical-potential derivative");
      }
    }
    p[0] = sys.getPhase(0).getPressure() / Pa;
    requireFinite(p[0], "profile pressure");
  }

  /**
   * Rejects a density step outside the physical domain before modifying the local fluid.
   *
   * @param densities component molar densities in mol/m3
   * @throws IllegalStateException if any density is invalid or the total is zero
   */
  static void validateDensities(double[] densities) {
    double total = 0.0;
    for (double density : densities) {
      if (!Double.isFinite(density) || density < 0.0) {
        throw new IllegalStateException("Density profile contains a negative or non-finite component density");
      }
      total += density;
    }
    if (!Double.isFinite(total) || total <= 0.0) {
      throw new IllegalStateException("Density profile has invalid total molar density");
    }
  }

  /**
   * Checks the initialized constant-volume state, allowing negative interfacial pressure inside a spinodal.
   *
   * @param phase local constant-volume phase
   * @throws IllegalStateException if the state is non-finite or exceeds the EOS covolume
   */
  static void validateState(PhaseInterface phase) {
    double volume = phase.getTotalVolume();
    double covolume = phase.getB();
    if (!Double.isFinite(volume) || !Double.isFinite(covolume) || volume <= 0.0 || covolume < 0.0 || volume <= covolume
        || !Double.isFinite(phase.getPressure())) {
      throw new IllegalStateException("Density profile is outside the finite EOS volume domain");
    }
  }

  /**
   * Rejects a non-finite thermodynamic value or linear-solver update.
   *
   * @param value value to check
   * @param quantity diagnostic quantity name
   * @throws IllegalStateException if the value is not finite
   */
  static void requireFinite(double value, String quantity) {
    if (!Double.isFinite(value)) {
      throw new IllegalStateException("Density profile has non-finite " + quantity);
    }
  }

  /**
   * Rejects paths that follow another density branch instead of approaching the requested second bulk phase. The
   * quadrature stops one grid interval before bulk, so the allowed endpoint error is two intervals of the larger bulk
   * total molar density.
   *
   * @param system original flashed fluid
   * @param first first bulk phase index
   * @param second second bulk phase index
   * @param density final component molar densities in mol/m3
   * @param points number of density-path grid points
   * @throws IllegalStateException if the path does not approach the requested bulk phase
   */
  static void validateEndpoint(SystemInterface system, int first, int second, double[] density, int points) {
    double scale = Math.max(1e5 / system.getPhase(first).getMolarVolume(),
        1e5 / system.getPhase(second).getMolarVolume());
    for (int i = 0; i < density.length; i++) {
      double target = 1e5 * system.getPhase(second).getComponent(i).getx() / system.getPhase(second).getMolarVolume();
      if (!Double.isFinite(density[i]) || Math.abs(density[i] - target) > 2.0 * scale / points) {
        throw new IllegalStateException("Density path does not approach the requested bulk phase");
      }
    }
  }

  private GTSurfaceTensionUtils() {
  }
}
