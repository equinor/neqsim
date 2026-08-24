package neqsim.thermo.system;

import neqsim.thermo.phase.PhaseSolidHelmholtzEos;
import neqsim.thermo.util.solid.ArgonSolidHelmholtzEquation;
import neqsim.thermo.util.solid.SolidHelmholtzState;

/**
 * Pure solid-argon system using the Maltby-Hammer-Wilhelmsen Helmholtz equation.
 *
 * <p>
 * The solid reference state follows Equations 21-23 of the publication. The two unreported reference constants are
 * recovered from the authors' Table 8 sample calculation; NeqSim's GERG-2008 phase cannot supply them because it uses a
 * different absolute enthalpy and entropy convention.
 * </p>
 *
 * @author esol
 * @version 1.0
 */
public class SystemArgonSolidHelmholtzEos extends SystemSolidHelmholtzEos {
  private static final long serialVersionUID = 1000L;
  private static final double PUBLISHED_REFERENCE_LIQUID_GIBBS_ENERGY = -9503.637837073084;
  private static final double PUBLISHED_REFERENCE_LIQUID_ENTROPY = 53.165638529275164;

  /** Construct a solid-argon system at 70 K and 1 MPa. */
  public SystemArgonSolidHelmholtzEos() {
    this(70.0, 10.0);
  }

  /**
   * Construct a solid-argon system.
   *
   * @param temperature temperature in K, above zero and no greater than 300 K
   * @param pressure pressure in bara, above zero and no greater than 160000 bara
   */
  public SystemArgonSolidHelmholtzEos(double temperature, double pressure) {
    super(temperature, pressure, "argon", createCalibratedSolidEquation());
    modelName = "Argon-Solid-Helmholtz-EOS";
  }

  /**
   * Return the calibrated argon equation owned by the solid phase.
   *
   * @return calibrated solid-argon Helmholtz equation
   */
  public ArgonSolidHelmholtzEquation getArgonSolidEquation() {
    PhaseSolidHelmholtzEos phase = (PhaseSolidHelmholtzEos) getPhase(0);
    return (ArgonSolidHelmholtzEquation) phase.getSolidEquation();
  }

  /** Apply the published absolute energy and entropy reference at the triple point. */
  private static ArgonSolidHelmholtzEquation createCalibratedSolidEquation() {
    ArgonSolidHelmholtzEquation rawEquation = new ArgonSolidHelmholtzEquation();
    SolidHelmholtzState rawSolidState = rawEquation.evaluate(ArgonSolidHelmholtzEquation.TRIPLE_POINT_TEMPERATURE,
        ArgonSolidHelmholtzEquation.TRIPLE_POINT_PRESSURE);

    double gibbsShift = PUBLISHED_REFERENCE_LIQUID_GIBBS_ENERGY - rawSolidState.getGibbsEnergy();
    double entropyShift = PUBLISHED_REFERENCE_LIQUID_ENTROPY - rawSolidState.getEntropy()
        - ArgonSolidHelmholtzEquation.TRIPLE_POINT_ENTROPY_OF_MELTING;
    return new ArgonSolidHelmholtzEquation(gibbsShift, entropyShift);
  }

  /** {@inheritDoc} */
  @Override
  public SystemArgonSolidHelmholtzEos clone() {
    return (SystemArgonSolidHelmholtzEos) super.clone();
  }
}
