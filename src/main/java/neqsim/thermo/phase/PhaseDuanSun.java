package neqsim.thermo.phase;

import neqsim.thermo.component.ComponentGeDuanSun;
import neqsim.util.exception.IsNaNException;
import neqsim.util.exception.TooManyIterationsException;

/**
 * <p>
 * PhaseDuanSun class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class PhaseDuanSun extends PhaseGE {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  double[][] alpha;
  String[][] mixRule;
  double[][] intparam;
  double[][] Dij;
  double GE = 0.0;

  /**
   * <p>
   * Constructor for PhaseDuanSun.
   * </p>
   */
  public PhaseDuanSun() {}

  /** {@inheritDoc} */
  @Override
  public void addComponent(String name, double moles, double molesInPhase, int compNumber) {
    super.addComponent(name, molesInPhase, compNumber);
    componentArray[compNumber] = new ComponentGeDuanSun(name, moles, molesInPhase, compNumber);
  }

  /** {@inheritDoc} */
  @Override
  public void setMixingRule(int type) {
    super.setMixingRule(type);
    this.alpha = mixSelect.getNRTLalpha();
    this.Dij = mixSelect.getNRTLDij();
  }

  /** {@inheritDoc} */
  @Override
  public void setAlpha(double[][] alpha) {
    for (int i = 0; i < alpha.length; i++) {
      System.arraycopy(alpha[i], 0, this.alpha[i], 0, alpha[0].length);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void setDij(double[][] Dij) {
    for (int i = 0; i < Dij.length; i++) {
      System.arraycopy(Dij[i], 0, this.Dij[i], 0, Dij[0].length);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void setDijT(double[][] DijT) {
    throw new UnsupportedOperationException("Unimplemented method 'setDijT'");
  }

  /** {@inheritDoc} */
  @Override
  public double getExcessGibbsEnergy() {
    // double GE = getExcessGibbsEnergy(this, numberOfComponents, temperature,
    // pressure, pt);
    return GE;
  }

  /** {@inheritDoc} */
  @Override
  public double getExcessGibbsEnergy(PhaseInterface phase, int numberOfComponents,
      double temperature, double pressure, PhaseType pt) {
    GE = 0;
    double salinity = 0.0;
    // double k=0.0;
    // salinity=salinity+phase.getComponent("Na+").getNumberOfMolesInPhase()/(phase.getComponent("water").getNumberOfmoles()*phase.getComponent("water").getMolarMass());

    // for (int i=2;i<numberOfComponents;i++) {
    // salinity=salinity+phase.getComponent(i).getNumberOfMolesInPhase()/(phase.getComponent("water").getNumberOfmoles()*phase.getComponent("water").getMolarMass());
    // }
    for (int i = 0; i < numberOfComponents; i++) {
      if (phase.getComponent(i).isIsIon()) {
        salinity = salinity + phase.getComponent(i).getNumberOfMolesInPhase()
            / (phase.getComponent("water").getNumberOfMolesInPhase()
                * phase.getComponent("water").getMolarMass());
      }
    }
    // for (int i=0; i < numberOfComponents; i++) {
    // if(phase.getComponent(i).isIsIon()) {
    // salinity=salinity+phase.getComponent(i).getNumberOfMolesInPhase()/(phase.getComponent("water").getNumberOfmoles()*phase.getComponent("water").getMolarMass());
    // phase.getComponent("Na+").getNumberOfmoles()
    // }
    // }

    // salinity=salinity+phase.getComponent("Na+").getNumberOfmoles()/(phase.getComponent("water").getNumberOfmoles()*phase.getComponent("water").getMolarMass());

    for (int i = 0; i < numberOfComponents; i++) {
      // GE += phase.getComponent(i).getx()*Math.log(((ComponentGeDuanSun)
      // componentArray[i]).getGammaNRTL(phase, numberOfComponents, temperature, pressure,
      // pt, alpha, Dij));
      GE += phase.getComponent(i).getx() * Math.log(((ComponentGeDuanSun) componentArray[i])
          .getGammaPitzer(phase, numberOfComponents, temperature, pressure, pt, salinity));
    }

    return R * temperature * numberOfMolesInPhase * GE;
  }

  /** {@inheritDoc} */
  @Override
  public double getGibbsEnergy() {
    return R * temperature * numberOfMolesInPhase * (GE + Math.log(pressure));
  }

  /** {@inheritDoc} */
  @Override
  public double molarVolume(double pressure, double temperature, double A, double B, PhaseType pt)
      throws IsNaNException, TooManyIterationsException {
    throw new UnsupportedOperationException("Unimplemented method 'molarVolume'");
  }
}
