/*
 * PhaseHydrate.java
 *
 * Created on 18. august 2001, 12:50
 */

package neqsim.thermo.phase;

import neqsim.thermo.component.ComponentHydrate;
import neqsim.thermo.component.ComponentHydrateGF;
import neqsim.thermo.component.ComponentHydratePVTsim;
import neqsim.thermo.mixingrule.MixingRuleTypeInterface;
import neqsim.thermo.mixingrule.MixingRulesInterface;

/**
 * <p>
 * PhaseHydrate class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class PhaseHydrate extends Phase {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  String hydrateModel = "PVTsimHydrateModel";

  /**
   * <p>
   * Constructor for PhaseHydrate.
   * </p>
   */
  public PhaseHydrate() {
    setType(PhaseType.HYDRATE);
  }

  /**
   * <p>
   * Constructor for PhaseHydrate.
   * </p>
   *
   * @param fluidModel a {@link java.lang.String} object
   */
  public PhaseHydrate(String fluidModel) {
    if (fluidModel.isEmpty()) {
      hydrateModel = "PVTsimHydrateModel";
    } else if (fluidModel.equals("CPAs-SRK-EOS-statoil") || fluidModel.equals("CPAs-SRK-EOS")
        || fluidModel.equals("CPA-SRK-EOS")) {
      hydrateModel = "CPAHydrateModel";
    } else {
      hydrateModel = "PVTsimHydrateModel";
    }
  }

  /** {@inheritDoc} */
  @Override
  public PhaseHydrate clone() {
    PhaseHydrate clonedPhase = null;
    try {
      clonedPhase = (PhaseHydrate) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }

    return clonedPhase;
  }

  /** {@inheritDoc} */
  @Override
  public double molarVolume(double pressure, double temperature, double A, double B, PhaseType pt)
      throws neqsim.util.exception.IsNaNException,
      neqsim.util.exception.TooManyIterationsException {
    double sum = 1.0;
    int hydrateStructure = ((ComponentHydrate) getComponent(0)).getHydrateStructure();
    for (int j = 0; j < 2; j++) {
      for (int i = 0; i < numberOfComponents; i++) {
        sum += ((ComponentHydrate) getComponent(i)).getCavprwat(hydrateStructure, j)
            * ((ComponentHydrate) getComponent(i)).calcYKI(hydrateStructure, j, this);
      }
    }
    return sum / (((ComponentHydrate) getComponent(0)).getMolarVolumeHydrate(hydrateStructure,
        temperature));
    // return 1.0;
  }

  /** {@inheritDoc} */
  @Override
  public void addComponent(String name, double moles, double molesInPhase, int compNumber) {
    super.addComponent(name, molesInPhase, compNumber);
    // componentArray[compNumber] = new ComponentHydrateStatoil(name, moles, molesInPhase,
    // compNumber);
    if (hydrateModel.equals("CPAHydrateModel")) {
      componentArray[compNumber] = new ComponentHydrateGF(name, moles, molesInPhase, compNumber);
      // System.out.println("hydrate model: CPA-EoS hydrate model selected");
    } else {
      componentArray[compNumber] =
          new ComponentHydratePVTsim(name, moles, molesInPhase, compNumber);
      // System.out.println("hydrate model: standard PVTsim hydrate model selected");
    }
    // componentArray[compNumber] = new ComponentHydrateBallard(name, moles, molesInPhase,
    // compNumber);
    // componentArray[compNumber] = new ComponentHydratePVTsim(name, moles, molesInPhase,
    // compNumber);
  }

  /** {@inheritDoc} */
  @Override
  public void init(double totalNumberOfMoles, int numberOfComponents, int initType, PhaseType pt,
      double beta) {
    super.init(totalNumberOfMoles, numberOfComponents, initType, pt, beta);
    setType(PhaseType.HYDRATE);
  }

  /** {@inheritDoc} */
  @Override
  public MixingRulesInterface getMixingRule() {
    return null;
  }

  /** {@inheritDoc} */
  @Override
  public void setMixingRuleGEModel(String name) {}

  /**
   * {@inheritDoc}
   *
   * <p>
   * Not relevant for PhaseHydrate
   * </p>
   */
  @Override
  public void resetMixingRule(MixingRuleTypeInterface mr) {}

  /**
   * {@inheritDoc}
   *
   * <p>
   * Not relevant for PhaseHydrate
   * </p>
   */
  @Override
  public void setMixingRule(MixingRuleTypeInterface mr) {}

  /**
   * <p>
   * setSolidRefFluidPhase.
   * </p>
   *
   * @param refPhase a {@link neqsim.thermo.phase.PhaseInterface} object
   */
  public void setSolidRefFluidPhase(PhaseInterface refPhase) {
    for (int i = 0; i < numberOfComponents; i++) {
      if (componentArray[i].getName().equals("water")) {
        ((ComponentHydrate) componentArray[i]).setSolidRefFluidPhase(refPhase);
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public double getSoundSpeed() {
    return Double.NaN;
  }
}
