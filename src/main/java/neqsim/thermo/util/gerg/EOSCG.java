package neqsim.thermo.util.gerg;

import org.netlib.util.StringW;
import org.netlib.util.doubleW;
import org.netlib.util.intW;

/**
 * Standalone EOS-CG correlation adapter.
 *
 * <p>This class no longer delegates to the GERG-2008 implementation; every public entry point is
 * routed to a dedicated EOS-CG correlation backend that must be populated with the specific
 * functional form and parameters from the EOS-CG publication.</p>
 */
public class EOSCG {
  /** Dedicated EOS-CG correlation backend. */
  private final EOSCGCorrelationBackend correlations;

  public EOSCG() {
    this(new EOSCGCorrelationBackend());
  }

  public EOSCG(EOSCGCorrelationBackend correlations) {
    this.correlations = correlations;
  }

  /** Initialize EOS-CG parameters. */
  public void setup() {
    correlations.setup();
  }

  public void pressure(double temperature, double density, double[] composition, doubleW p,
      doubleW z) {
    correlations.pressure(temperature, density, composition, p, z);
  }

  public void molarMass(double[] composition, doubleW mm) {
    correlations.molarMass(composition, mm);
  }

  public void density(int flag, double temperature, double pressure, double[] composition,
      doubleW D, intW ierr, StringW herr) {
    correlations.density(flag, temperature, pressure, composition, D, ierr, herr);
  }

  public void properties(double temperature, double density, double[] composition, doubleW p,
      doubleW z, doubleW dpdd, doubleW d2pdd2, doubleW d2pdtd, doubleW dpdt, doubleW u, doubleW h,
      doubleW s, doubleW cv, doubleW cp, doubleW w, doubleW g, doubleW jt, doubleW kappa,
      doubleW A) {
    correlations.properties(temperature, density, composition, p, z, dpdd, d2pdd2, d2pdtd, dpdt, u,
        h, s, cv, cp, w, g, jt, kappa, A);
  }

  public void alpha0(double temperature, double density, double[] composition, doubleW[] a0) {
    correlations.alpha0(temperature, density, composition, a0);
  }

  public void alphar(int itau, int idelta, double temperature, double density,
      double[] composition, doubleW[][] ar) {
    correlations.alphar(itau, idelta, temperature, density, composition, ar);
  }
}
