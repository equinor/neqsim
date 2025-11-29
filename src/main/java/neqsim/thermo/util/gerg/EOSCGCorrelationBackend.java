package neqsim.thermo.util.gerg;

import org.netlib.util.StringW;
import org.netlib.util.doubleW;
import org.netlib.util.intW;

/**
 * EOS-CG correlation wrapper implemented using the EOSCGModel (based on GERG-2008 functional form).
 */
public class EOSCGCorrelationBackend {
  private final EOSCGModel model = new EOSCGModel();
  private boolean initialized;

  /** Initialize EOS-CG parameters. */
  public void setup() {
    if (initialized) {
      return;
    }
    model.SetupEOSCG();
    initialized = true;
  }

  public void pressure(double temperature, double density, double[] composition, doubleW p,
      doubleW z) {
    validateSetup();
    model.PressureEOSCG(temperature, density, composition, p, z);
  }

  public void molarMass(double[] composition, doubleW mm) {
    validateSetup();
    model.MolarMassEOSCG(composition, mm);
  }

  public void density(int flag, double temperature, double pressure, double[] composition,
      doubleW D, intW ierr, StringW herr) {
    validateSetup();
    model.DensityEOSCG(flag, temperature, pressure, composition, D, ierr, herr);
  }

  public void properties(double temperature, double density, double[] composition, doubleW p,
      doubleW z, doubleW dpdd, doubleW d2pdd2, doubleW d2pdtd, doubleW dpdt, doubleW u, doubleW h,
      doubleW s, doubleW cv, doubleW cp, doubleW w, doubleW g, doubleW jt, doubleW kappa,
      doubleW A) {
    validateSetup();
    model.PropertiesEOSCG(temperature, density, composition, p, z, dpdd, d2pdd2, d2pdtd, dpdt, u, h,
        s, cv, cp, w, g, jt, kappa, A);
  }

  public void alpha0(double temperature, double density, double[] composition, doubleW[] a0) {
    validateSetup();
    model.Alpha0EOSCG(temperature, density, composition, a0);
  }

  public void alphar(int itau, int idelta, double temperature, double density, double[] composition,
      doubleW[][] ar) {
    validateSetup();
    model.AlpharEOSCG(itau, idelta, temperature, density, composition, ar);
  }

  private void validateSetup() {
    if (!initialized) {
      setup();
    }
  }
}
