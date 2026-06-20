package neqsim.thermo.component;

import neqsim.thermo.ThermodynamicModelSettings;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhaseUMRCPAvolcor;

/**
 * <p>
 * ComponentUMRCPAvolcor class.
 * </p>
 *
 * <p>
 * Volume-translated UMR-CPA component. It combines the UMR-CPA physical and association term (inherited from
 * {@link ComponentUMRCPA}) with the consistent Peneloux volume translation of {@link ComponentPRvolcor}. The
 * translation parameter <i>c</i> and its mole-number derivatives (C<sub>i</sub>, C<sub>ij</sub>, C<sub>iT</sub>) are
 * injected into the reduced residual Helmholtz energy mole-derivatives, so the translation propagates consistently into
 * the fugacity coefficient, partial molar volumes and all caloric derivatives.
 * </p>
 *
 * <p>
 * The translation acts only on the cubic (physical) part of the Helmholtz energy (Option A): the per-component <i>c</i>
 * equals the inherited UMR-CPA Peneloux shift, which is the PR shift for non-associating compounds and zero for
 * associating compounds until a dedicated UMR-CPA Rackett Z is regressed. The Wertheim association term is left
 * unchanged.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class ComponentUMRCPAvolcor extends ComponentUMRCPA {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /** Per-component volume translation parameter c [m^3/mol]. */
  private double c;
  /** Temperature derivative of the volume translation parameter dc/dT. */
  private double cT;
  /**
   * Mole-number derivatives of the extensive translation parameter C with respect to component j.
   */
  public double[] Cij = new double[ThermodynamicModelSettings.MAX_NUMBER_OF_COMPONENTS];
  /** First mole-number derivative of the extensive translation parameter C. */
  public double Ci = 0;
  /** Cross temperature/mole-number derivative of the extensive translation parameter C. */
  private double CiT = 0;

  /**
   * <p>
   * Constructor for ComponentUMRCPAvolcor.
   * </p>
   *
   * @param name Name of component.
   * @param moles Total number of moles of component.
   * @param molesInPhase Number of moles in phase.
   * @param compIndex Index number of component in phase object component array.
   */
  public ComponentUMRCPAvolcor(String name, double moles, double molesInPhase, int compIndex) {
    super(name, moles, molesInPhase, compIndex);
    c = calcc();
    cT = calccT();
  }

  /**
   * <p>
   * Constructor for ComponentUMRCPAvolcor.
   * </p>
   *
   * @param number a int. Not used.
   * @param TC Critical temperature [K]
   * @param PC Critical pressure [bara]
   * @param M Molar mass
   * @param a Acentric factor
   * @param moles Total number of moles of component.
   */
  public ComponentUMRCPAvolcor(int number, double TC, double PC, double M, double a, double moles) {
    super(number, TC, PC, M, a, moles);
    c = calcc();
    cT = calccT();
  }

  /**
   * <p>
   * Calculate the per-component volume translation parameter c.
   * </p>
   *
   * @return the volume translation parameter c [m^3/mol]
   */
  public double calcc() {
    return getVolumeCorrection();
  }

  /**
   * <p>
   * Calculate the temperature derivative of the volume translation parameter.
   * </p>
   *
   * @return dc/dT
   */
  public double calccT() {
    return super.getVolumeCorrectionT();
  }

  /** {@inheritDoc} */
  @Override
  public void init(double temp, double pres, double totMoles, double beta, int initType) {
    super.init(temp, pres, totMoles, beta, initType);
    c = calcc();
    cT = calccT();
  }

  /**
   * <p>
   * Getter for the volume translation parameter c.
   * </p>
   *
   * @return c [m^3/mol]
   */
  public double getc() {
    return c;
  }

  /**
   * <p>
   * Getter for the temperature derivative of the volume translation parameter.
   * </p>
   *
   * @return dc/dT
   */
  public double getcT() {
    return cT;
  }

  /** {@inheritDoc} */
  @Override
  public void Finit(PhaseInterface phase, double temp, double pres, double totMoles, double beta,
      int numberOfComponents, int initType) {
    super.Finit(phase, temp, pres, totMoles, beta, numberOfComponents, initType);
    Ci = ((PhaseUMRCPAvolcor) phase).calcCi(componentNumber, phase, temp, pres, numberOfComponents);
    if (initType >= 2) {
      CiT = ((PhaseUMRCPAvolcor) phase).calcCiT(componentNumber, phase, temp, pres, numberOfComponents);
    }
    if (initType >= 3) {
      for (int j = 0; j < numberOfComponents; j++) {
	Cij[j] = ((PhaseUMRCPAvolcor) phase).calcCij(componentNumber, j, phase, temp, pres, numberOfComponents);
      }
    }
  }

  /**
   * <p>
   * Getter for the first mole-number derivative of C.
   * </p>
   *
   * @return C_i
   */
  public double getCi() {
    return Ci;
  }

  /**
   * <p>
   * Getter for the cross temperature/mole-number derivative of C.
   * </p>
   *
   * @return C_iT
   */
  public double getCiT() {
    return CiT;
  }

  /**
   * <p>
   * Getter for the second mole-number derivative of C with respect to component j.
   * </p>
   *
   * @param j component index
   * @return C_ij
   */
  public double getCij(int j) {
    return Cij[j];
  }

  /** {@inheritDoc} */
  @Override
  public double dFdN(PhaseInterface phase, int numberOfComponents, double temperature, double pressure) {
    // super.dFdN() already contains the untranslated PR cubic term (Fn + FB*Bi + FD*Ai) plus the
    // CPA association contribution. Add the consistent volume-translation cross term FC*Ci.
    return super.dFdN(phase, numberOfComponents, temperature, pressure) + ((PhaseUMRCPAvolcor) phase).FC() * getCi();
  }

  /** {@inheritDoc} */
  @Override
  public double dFdNdV(PhaseInterface phase, int numberOfComponents, double temperature, double pressure) {
    return super.dFdNdV(phase, numberOfComponents, temperature, pressure) + ((PhaseUMRCPAvolcor) phase).FCV() * getCi();
  }

  /** {@inheritDoc} */
  @Override
  public double dFdNdT(PhaseInterface phase, int numberOfComponents, double temperature, double pressure) {
    PhaseUMRCPAvolcor pv = (PhaseUMRCPAvolcor) phase;
    double locCT = pv.getCT();
    double locFnC = pv.FnC();
    double locFBC = pv.FBC();
    double locFCD = pv.FCD();
    double locFC = pv.FC();
    double locFCT = pv.FTC();
    double locFCC = pv.FCC();
    double deltaC = locFnC * locCT + locFBC * locCT * getBi() + locFCD * locCT * getAi()
	+ (locFCT + locFCC * locCT + locFCD * phase.getAT()) * getCi() + locFC * getCiT();
    return super.dFdNdT(phase, numberOfComponents, temperature, pressure) + deltaC;
  }

  /** {@inheritDoc} */
  @Override
  public double dFdNdN(int j, PhaseInterface phase, int numberOfComponents, double temperature, double pressure) {
    PhaseUMRCPAvolcor pv = (PhaseUMRCPAvolcor) phase;
    double locFnC = pv.FnC();
    double locFBC = pv.FBC();
    double locFCD = pv.FCD();
    double locFC = pv.FC();
    double locFCC = pv.FCC();
    ComponentEosInterface[] compArray = (ComponentEosInterface[]) phase.getcomponentArray();
    double locCj = ((ComponentUMRCPAvolcor) compArray[j]).getCi();
    double deltaC = locFnC * locCj + locFBC * locCj * getBi()
	+ (locFnC + locFCC * locCj + locFBC * compArray[j].getBi() + locFCD * compArray[j].getAi()) * getCi()
	+ locFC * getCij(j) + locFCD * locCj * getAi();
    return super.dFdNdN(j, phase, numberOfComponents, temperature, pressure) + deltaC;
  }
}
