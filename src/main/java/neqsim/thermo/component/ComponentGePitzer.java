package neqsim.thermo.component;

import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhasePitzer;
import neqsim.thermo.phase.PhaseType;

/**
 * Component class for the Pitzer model.
 *
 * @author Even Solbraa
 */
public class ComponentGePitzer extends ComponentGE {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * Constructor for ComponentGePitzer.
   *
   * @param name Name of component
   * @param moles total number of moles
   * @param molesInPhase moles in phase
   * @param compIndex component index
   */
  public ComponentGePitzer(String name, double moles, double molesInPhase, int compIndex) {
    super(name, moles, molesInPhase, compIndex);
  }

  /** {@inheritDoc} */
  @Override
  public double fugcoef(PhaseInterface phase) {
    getGamma(phase, phase.getNumberOfComponents(), phase.getTemperature(), phase.getPressure(),
        phase.getType());
    return super.fugcoef(phase);
  }

  /** {@inheritDoc} */
  @Override
  public double getGamma(PhaseInterface phase, int numberOfComponents, double temperature,
      double pressure, PhaseType pt, double[][] HValpha, double[][] HVgij, double[][] intparam,
      String[][] mixRule) {
    return getGamma(phase, numberOfComponents, temperature, pressure, pt);
  }

  /**
   * Calculate activity coefficient using the Pitzer model.
   *
   * @param phase phase object
   * @param numberOfComponents number of components in phase
   * @param temperature temperature
   * @param pressure pressure
   * @param pt phase type
   * @return activity coefficient
   */
  public double getGamma(PhaseInterface phase, int numberOfComponents, double temperature,
      double pressure, PhaseType pt) {
    PhasePitzer pitz = (PhasePitzer) phase;
    double I = pitz.getIonicStrength();
    double sqrtI = Math.sqrt(I);
    // Debye-Hückel constant for natural logarithm units, T-dependent
    double A = debyeHuckelAphi(temperature);
    double b = 1.2;
    double alpha = 2.0;
    double f = -A * Math.pow(getIonicCharge(), 2.0) * sqrtI / (1.0 + b * sqrtI);
    double sum = 0.0;
    for (int j = 0; j < numberOfComponents; j++) {
      if (j == componentNumber) {
        continue;
      }
      if (phase.getComponent(j).getIonicCharge() * getIonicCharge() >= 0) {
        continue;
      }
      double m_j = phase.getComponent(j).getMolality(phase);
      double beta0 = pitz.getBeta0ij(componentNumber, j, temperature);
      double beta1 = pitz.getBeta1ij(componentNumber, j, temperature);
      double g = 0.0;
      double x = alpha * sqrtI;
      if (x > 1e-12) {
        g = 2.0 * (1.0 - (1.0 + x) * Math.exp(-x)) / (x * x);
      }
      double B = beta0 + beta1 * g;
      double Cphi = pitz.getCphiij(componentNumber, j, temperature);
      sum += m_j * (2.0 * B + getIonicCharge() * phase.getComponent(j).getIonicCharge() * Cphi);
    }
    lngamma = f + sum;
    gamma = Math.exp(lngamma);
    return gamma;
  }

  /** {@inheritDoc} */
  @Override
  public double getMolality(PhaseInterface phase) {
    if (phase instanceof PhasePitzer) {
      double solventWeight = ((PhasePitzer) phase).getSolventWeight();
      if (solventWeight > 0) {
        return getNumberOfMolesInPhase() / solventWeight;
      }
    }
    return 0.0;
  }

  /**
   * Calculates the Pitzer Debye-Hückel Aphi parameter as a function of temperature.
   *
   * <p>
   * Uses the Bradley-Pitzer (1979) correlation for the osmotic coefficient Debye-Hückel parameter.
   * The Aphi value is in ln-based units (Aphi = Agamma/3).
   * </p>
   *
   * @param TK temperature in Kelvin
   * @return Aphi in ln-based units (dimensionless)
   */
  private double debyeHuckelAphi(double TK) {
    // Water density (kg/m³) from Kell (1975)
    double TC = TK - 273.15;
    double rho = 999.83 + 5.0948e-2 * TC - 7.5722e-3 * TC * TC + 3.8907e-5 * TC * TC * TC
        - 1.2e-7 * TC * TC * TC * TC;
    if (rho < 850.0) {
      rho = 850.0;
    }
    double rhoGcm3 = rho / 1000.0;

    // Dielectric constant of water from Archer & Wang (1990)
    double eps = 87.740 - 0.40008 * TC + 9.398e-4 * TC * TC - 1.410e-6 * TC * TC * TC;
    if (eps < 20.0) {
      eps = 20.0;
    }

    // Aphi = (1/3) * (2*pi*NA*rho/1000)^0.5 * (e^2/(4*pi*eps0*eps*kT))^1.5
    // Simplified: Aphi = 1.4006e6 * sqrt(rho_g/cm3) / (eps*T)^1.5
    // This gives Aphi in ln-based units (= Agamma/3 for osmotic coefficient)
    double epsT = eps * TK;
    double Aphi = 1.4006e6 * Math.sqrt(rhoGcm3) / Math.pow(epsT, 1.5);

    // Convert to Agamma for activity coefficient: Agamma = 3*Aphi
    return 3.0 * Aphi;
  }
}

