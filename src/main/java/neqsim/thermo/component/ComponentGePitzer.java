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
    // Debye-Hückel constant for natural logarithm units
    double A = 1.17593;
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
      double beta0 = pitz.getBeta0ij(componentNumber, j);
      double beta1 = pitz.getBeta1ij(componentNumber, j);
      double g = 0.0;
      double x = alpha * sqrtI;
      if (x > 1e-12) {
        g = 2.0 * (1.0 - (1.0 + x) * Math.exp(-x)) / (x * x);
      }
      double B = beta0 + beta1 * g;
      double Cphi = pitz.getCphiij(componentNumber, j);
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
}

