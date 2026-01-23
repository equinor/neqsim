/*
 * SystemElectrolyteCPAMM.java
 *
 * Thermodynamic system using the Maribo-Mogensen electrolyte CPA model.
 */

package neqsim.thermo.system;

import neqsim.thermo.mixingrule.HVMixingRulesInterface;
import neqsim.thermo.phase.PhaseElectrolyteCPAMM;
import neqsim.thermo.phase.PhaseEos;
import neqsim.thermo.util.constants.IonParametersMM;

/**
 * Thermodynamic system class using the Maribo-Mogensen electrolyte CPA (e-CPA) equation of state.
 *
 * <p>
 * This model is based on the PhD thesis: "Development of an Electrolyte CPA Equation of State for
 * Mixed Solvent Electrolytes" by Bjørn Maribo-Mogensen, Technical University of Denmark, 2014.
 * </p>
 *
 * <p>
 * The residual Helmholtz free energy consists of:
 * </p>
 * <ul>
 * <li>SRK cubic equation of state term</li>
 * <li>CPA association term for hydrogen bonding</li>
 * <li>Debye-Hückel term for long-range electrostatic interactions</li>
 * <li>Born solvation term for ion hydration</li>
 * </ul>
 *
 * <p>
 * Key differences from the standard electrolyte CPA in NeqSim:
 * </p>
 * <ul>
 * <li>Uses Debye-Hückel instead of MSA for long-range electrostatics (simpler, faster)</li>
 * <li>Empirical Born radius correlations for cations and anions</li>
 * <li>Temperature-dependent ion-solvent interaction parameters</li>
 * </ul>
 *
 * <p>
 * Example usage:
 * </p>
 *
 * <pre>
 * SystemInterface system = new SystemElectrolyteCPAMM(298.15, 1.0);
 * system.addComponent("water", 1.0);
 * system.addComponent("Na+", 0.1);
 * system.addComponent("Cl-", 0.1);
 * system.setMixingRule(10); // Electrolyte CPA mixing rule
 * system.init(0);
 * system.init(1);
 * </pre>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class SystemElectrolyteCPAMM extends SystemSrkCPA {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /**
   * Constructor for SystemElectrolyteCPAMM with default conditions (298.15 K, 1 bar).
   */
  public SystemElectrolyteCPAMM() {
    this(298.15, 1.0);
  }

  /**
   * Constructor for SystemElectrolyteCPAMM.
   *
   * @param T temperature in Kelvin
   * @param P pressure in bar (absolute)
   */
  public SystemElectrolyteCPAMM(double T, double P) {
    super(T, P);
    modelName = "Electrolyte-CPA-MM";
    attractiveTermNumber = 0;

    for (int i = 0; i < numberOfPhases; i++) {
      phaseArray[i] = new PhaseElectrolyteCPAMM();
      phaseArray[i].setTemperature(T);
      phaseArray[i].setPressure(P);
    }

    // Disable volume correction for electrolyte systems by default
    this.useVolumeCorrection(false);
  }

  /**
   * Constructor for SystemElectrolyteCPAMM with specified number of phases.
   *
   * @param T temperature in Kelvin
   * @param P pressure in bar (absolute)
   * @param checkForSolids whether to include solid phase check
   */
  public SystemElectrolyteCPAMM(double T, double P, boolean checkForSolids) {
    this(T, P);
    setNumberOfPhases(2);
    if (checkForSolids) {
      solidPhaseCheck = true;
    }
  }

  /** {@inheritDoc} */
  @Override
  public SystemElectrolyteCPAMM clone() {
    SystemElectrolyteCPAMM clonedSystem = null;
    try {
      clonedSystem = (SystemElectrolyteCPAMM) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }
    return clonedSystem;
  }

  /**
   * Enable or disable the Debye-Hückel electrostatic term.
   *
   * @param on true to enable, false to disable
   */
  public void setDebyeHuckelOn(boolean on) {
    for (int i = 0; i < numberOfPhases; i++) {
      if (phaseArray[i] instanceof PhaseElectrolyteCPAMM) {
        ((PhaseElectrolyteCPAMM) phaseArray[i]).setDebyeHuckelOn(on);
      }
    }
  }

  /**
   * Enable or disable the Born solvation term.
   *
   * @param on true to enable, false to disable
   */
  public void setBornOn(boolean on) {
    for (int i = 0; i < numberOfPhases; i++) {
      if (phaseArray[i] instanceof PhaseElectrolyteCPAMM) {
        ((PhaseElectrolyteCPAMM) phaseArray[i]).setBornOn(on);
      }
    }
  }

  /**
   * Get the Debye screening length for the specified phase.
   *
   * @param phaseNumber phase index
   * @return Debye length in meters
   */
  public double getDebyeLength(int phaseNumber) {
    if (phaseArray[phaseNumber] instanceof PhaseElectrolyteCPAMM) {
      return ((PhaseElectrolyteCPAMM) phaseArray[phaseNumber]).getDebyeLength();
    }
    return Double.NaN;
  }

  /**
   * Get the solvent permittivity (dielectric constant) for the specified phase.
   *
   * @param phaseNumber phase index
   * @return solvent permittivity (dimensionless)
   */
  public double getSolventPermittivity(int phaseNumber) {
    if (phaseArray[phaseNumber] instanceof PhaseElectrolyteCPAMM) {
      return ((PhaseElectrolyteCPAMM) phaseArray[phaseNumber]).getSolventPermittivity();
    }
    return Double.NaN;
  }

  /**
   * Get the mixture permittivity including ion effects for the specified phase.
   *
   * @param phaseNumber phase index
   * @return mixture permittivity (dimensionless)
   */
  public double getMixturePermittivity(int phaseNumber) {
    if (phaseArray[phaseNumber] instanceof PhaseElectrolyteCPAMM) {
      return ((PhaseElectrolyteCPAMM) phaseArray[phaseNumber]).getMixturePermittivity();
    }
    return Double.NaN;
  }

  /**
   * Set the dielectric constant mixing rule for all phases.
   *
   * <p>
   * Available mixing rules:
   * </p>
   * <ul>
   * <li>{@code MOLAR_AVERAGE} - Simple molar-weighted average (default)</li>
   * <li>{@code VOLUME_AVERAGE} - Volume-weighted average, better for water-glycol</li>
   * <li>{@code LOOYENGA} - Theoretical basis for polar mixtures</li>
   * <li>{@code OSTER} - Designed for water-alcohol mixtures</li>
   * <li>{@code LICHTENECKER} - Logarithmic mixing rule</li>
   * </ul>
   *
   * @param rule the dielectric mixing rule to use
   */
  public void setDielectricMixingRule(PhaseElectrolyteCPAMM.DielectricMixingRule rule) {
    for (int i = 0; i < numberOfPhases; i++) {
      if (phaseArray[i] instanceof PhaseElectrolyteCPAMM) {
        ((PhaseElectrolyteCPAMM) phaseArray[i]).setDielectricMixingRule(rule);
      }
    }
  }

  /**
   * Set the dielectric constant mixing rule by name for all phases.
   *
   * @param ruleName the name of the mixing rule: "MOLAR_AVERAGE", "VOLUME_AVERAGE", "LOOYENGA",
   *        "OSTER", or "LICHTENECKER"
   */
  public void setDielectricMixingRule(String ruleName) {
    PhaseElectrolyteCPAMM.DielectricMixingRule rule =
        PhaseElectrolyteCPAMM.DielectricMixingRule.valueOf(ruleName.toUpperCase());
    setDielectricMixingRule(rule);
  }

  /**
   * Enable or disable the short-range ion-solvent term.
   *
   * @param on true to enable, false to disable
   */
  public void setShortRangeOn(boolean on) {
    for (int i = 0; i < numberOfPhases; i++) {
      if (phaseArray[i] instanceof PhaseElectrolyteCPAMM) {
        ((PhaseElectrolyteCPAMM) phaseArray[i]).setShortRangeOn(on);
      }
    }
  }

  /**
   * Initialize Huron-Vidal parameters for ion-solvent interactions.
   *
   * <p>
   * This method sets up the NRTL parameters in the Huron-Vidal mixing rule using the ion-solvent
   * interaction parameters from the Maribo-Mogensen thesis (Table 6.11). Call this method AFTER
   * setting mixing rule 4 or 7 (Huron-Vidal).
   * </p>
   *
   * <p>
   * The ion-solvent interaction energy follows: τ_iw = u0_iw + uT_iw × (T - 298.15) where u0 and uT
   * are from IonParametersMM.
   * </p>
   *
   * @param alphaValue the NRTL non-randomness parameter (typically 0.2 for electrolytes)
   */
  public void initHuronVidalIonParameters(double alphaValue) {
    for (int phaseNum = 0; phaseNum < numberOfPhases; phaseNum++) {
      if (!(phaseArray[phaseNum] instanceof PhaseEos)) {
        continue;
      }
      PhaseEos phase = (PhaseEos) phaseArray[phaseNum];
      if (!(phase.getEosMixingRule() instanceof HVMixingRulesInterface)) {
        continue;
      }
      HVMixingRulesInterface hvRule = (HVMixingRulesInterface) phase.getEosMixingRule();

      int nComp = phase.getNumberOfComponents();
      for (int i = 0; i < nComp; i++) {
        String name_i = phase.getComponent(i).getComponentName();
        int charge_i = (int) Math.round(phase.getComponent(i).getIonicCharge());

        for (int j = 0; j < nComp; j++) {
          String name_j = phase.getComponent(j).getComponentName();
          int charge_j = (int) Math.round(phase.getComponent(j).getIonicCharge());

          // Set ion-solvent parameters (ion i with solvent j)
          if (charge_i != 0 && charge_j == 0) {
            IonParametersMM.IonData ionData = IonParametersMM.getIonData(name_i);
            if (ionData != null) {
              // Get solvent-specific parameters if available
              double u0 = IonParametersMM.getU0(name_i, name_j);
              double uT = IonParametersMM.getUT(name_i, name_j);

              // Set Huron-Vidal parameters: Dij = u0, DijT = uT
              hvRule.setHVDijParameter(i, j, u0);
              hvRule.setHVDijTParameter(i, j, uT);
              hvRule.setHValphaParameter(i, j, alphaValue);
            }
          }
          // Set solvent-ion parameters (solvent i with ion j)
          else if (charge_i == 0 && charge_j != 0) {
            IonParametersMM.IonData ionData = IonParametersMM.getIonData(name_j);
            if (ionData != null) {
              // Get solvent-specific parameters if available
              double u0 = IonParametersMM.getU0(name_j, name_i);
              double uT = IonParametersMM.getUT(name_j, name_i);

              // Set Huron-Vidal parameters: Dij = u0, DijT = uT
              hvRule.setHVDijParameter(i, j, u0);
              hvRule.setHVDijTParameter(i, j, uT);
              hvRule.setHValphaParameter(i, j, alphaValue);
            }
          }
        }
      }
    }
  }

  /**
   * Initialize Huron-Vidal parameters with default alpha = 0.2.
   */
  public void initHuronVidalIonParameters() {
    initHuronVidalIonParameters(0.2);
  }
}
