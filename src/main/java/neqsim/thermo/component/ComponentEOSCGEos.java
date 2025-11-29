package neqsim.thermo.component;

/** Component implementation for EOS-CG mirroring the GERG-2008 component behaviour. */
public class ComponentEOSCGEos extends ComponentGERG2008Eos {
  private static final long serialVersionUID = 1000;

  public ComponentEOSCGEos(String name, double moles, double molesInPhase, int compIndex) {
    super(name, moles, molesInPhase, compIndex);
  }

  public ComponentEOSCGEos(int number, double TC, double PC, double M, double a, double moles) {
    super(number, TC, PC, M, a, moles);
  }

  @Override
  public ComponentEOSCGEos clone() {
    return (ComponentEOSCGEos) super.clone();
  }
}
