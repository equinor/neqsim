/*
 * PhaseGEVanLaarAcid.java
 */

package neqsim.thermo.phase;

import neqsim.thermo.ThermodynamicModelSettings;
import neqsim.thermo.component.ComponentGEInterface;
import neqsim.thermo.component.ComponentGEVanLaarAcid;
import neqsim.thermo.mixingrule.MixingRuleTypeInterface;
import neqsim.util.exception.IsNaNException;
import neqsim.util.exception.TooManyIterationsException;

/**
 * <p>
 * PhaseGEVanLaarAcid class.
 * </p>
 *
 * <p>
 * Excess-Gibbs-energy (activity-coefficient) liquid phase for the
 * water-nitric-acid-sulfuric-acid
 * system, using the Van Laar model of Taleb, Ponche and Mirabel (1996). The
 * phase holds
 * {@link neqsim.thermo.component.ComponentGEVanLaarAcid} components, each of
 * which evaluates its
 * activity coefficient and pure-component vapour pressure from
 * {@link neqsim.thermo.util.empiric.NitricSulfuricAcidVaporPressure}.
 * </p>
 *
 * @author NeqSim
 * @version $Id: $Id
 */
public class PhaseGEVanLaarAcid extends PhaseGE {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * Molar excess Gibbs energy contribution G^E/(R T n), updated on each
   * evaluation.
   */
  private double excessGibbsEnergy = 0.0;

  /**
   * <p>
   * Constructor for PhaseGEVanLaarAcid.
   * </p>
   */
  public PhaseGEVanLaarAcid() {
    componentArray = new ComponentGEVanLaarAcid[ThermodynamicModelSettings.MAX_NUMBER_OF_COMPONENTS];
  }

  /** {@inheritDoc} */
  @Override
  public void addComponent(String name, double moles, double molesInPhase, int compNumber) {
    super.addComponent(name, molesInPhase, compNumber);
    componentArray[compNumber] = new ComponentGEVanLaarAcid(name, moles, molesInPhase, compNumber);
  }

  /** {@inheritDoc} */
  @Override
  public void setMixingRule(MixingRuleTypeInterface mr) {
    super.setMixingRule(mr);
  }

  /** {@inheritDoc} */
  @Override
  public void setAlpha(double[][] alpha) {
    throw new UnsupportedOperationException("Unimplemented method 'setAlpha'");
  }

  /** {@inheritDoc} */
  @Override
  public void setDij(double[][] Dij) {
    throw new UnsupportedOperationException("Unimplemented method 'setDij'");
  }

  /** {@inheritDoc} */
  @Override
  public void setDijT(double[][] DijT) {
    throw new UnsupportedOperationException("Unimplemented method 'setDijT'");
  }

  /** {@inheritDoc} */
  @Override
  public double getExcessGibbsEnergy() {
    return excessGibbsEnergy;
  }

  /** {@inheritDoc} */
  @Override
  public double getExcessGibbsEnergy(PhaseInterface phase, int numberOfComponents,
      double temperature, double pressure, PhaseType pt) {
    double sum = 0.0;
    for (int i = 0; i < numberOfComponents; i++) {
      double gammaI = ((ComponentGEInterface) componentArray[i]).getGamma(phase, numberOfComponents,
          temperature, pressure, pt, null, null, null, null);
      sum += phase.getComponent(i).getx() * Math.log(gammaI);
    }
    excessGibbsEnergy = sum;
    return R * temperature * numberOfMolesInPhase * sum;
  }

  /** {@inheritDoc} */
  @Override
  public double getGibbsEnergy() {
    return R * temperature * numberOfMolesInPhase * (excessGibbsEnergy + Math.log(pressure));
  }

  /** {@inheritDoc} */
  @Override
  public double molarVolume(double pressure, double temperature, double A, double B, PhaseType pt)
      throws IsNaNException, TooManyIterationsException {
    throw new UnsupportedOperationException("Unimplemented method 'molarVolume'");
  }
}
