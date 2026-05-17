package neqsim.thermo.phase;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.component.ComponentElectrolyteCPAAdvanced;
import neqsim.thermo.util.constants.IonParametersAdvanced;

/**
 * Phase class for the e-CPA-Advanced electrolyte equation of state.
 *
 * <p>
 * Implements an advanced electrolyte CPA model with the following Helmholtz energy contributions:
 * </p>
 *
 * <pre>
 * A_res = A_SRK + A_CPA + A_DH + A_Born_adv + A_SR_adv + A_IP
 * </pre>
 *
 * <p>
 * Key improvements over the standard e-CPA model:
 * </p>
 * <ul>
 * <li>Ion-specific short-range parameters (W) instead of universal linear correlations</li>
 * <li>Temperature-dependent Born radii with physical constraints from hydration data</li>
 * <li>Quadratic temperature dependence in short-range interaction energies</li>
 * <li>Ion-pair formation for 2:2 and select 2:1 electrolyte systems</li>
 * </ul>
 *
 * <h2>References</h2>
 * <ul>
 * <li>Maribo-Mogensen et al., Ind. Eng. Chem. Res. 2012, 51, 5353-5363</li>
 * <li>Furst and Renon, AIChE J. 1993, 39(2), 335-343</li>
 * <li>Born, Z. Physik 1920, 1, 45-48</li>
 * <li>Debye and Huckel, Phys. Z. 1923, 24, 185-206</li>
 * </ul>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class PhaseElectrolyteCPAAdvanced extends PhaseElectrolyteCPAstatoil {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;
  /** Logger object for class. */
  private static final Logger logger = LogManager.getLogger(PhaseElectrolyteCPAAdvanced.class);

  /**
   * Flag to enable/disable the advanced short-range term. Disabled by default until ion-specific W
   * parameters are fitted in the correct NeqSim unit system (wij ~ 1e-5).
   */
  private double advSROn = 0.0;
  /**
   * Flag to enable/disable the phase-level Born replacement. Disabled because the advanced Born
   * radii are now applied through the component-level XBorni override and the overridden
   * calcBornX() method, which makes the parent's Born term automatically use advanced radii.
   */
  private double advBornOn = 0.0;
  /** Flag to enable/disable ion-pair formation. */
  private double ionPairOn = 1.0;

  /** Cached advanced short-range Helmholtz energy and derivatives. */
  private double fAdvSR = 0.0;
  private double fAdvSRdT = 0.0;
  private double fAdvSRdV = 0.0;
  private double fAdvSRdTdT = 0.0;
  private double fAdvSRdTdV = 0.0;
  private double fAdvSRdVdV = 0.0;
  private double fAdvSRdVdVdV = 0.0;

  /** Cached advanced Born solvation energy and derivatives. */
  private double fAdvBorn = 0.0;
  private double fAdvBorndT = 0.0;
  private double fAdvBorndTdT = 0.0;

  /** Ion-pair correction to ionic strength. */
  private double ionPairFractionReduction = 0.0;

  /**
   * Constructor for PhaseElectrolyteCPAAdvanced.
   */
  public PhaseElectrolyteCPAAdvanced() {
  }

  /** {@inheritDoc} */
  @Override
  public void addComponent(String name, double moles, double molesInPhase, int compNumber) {
    super.addComponent(name, moles, molesInPhase, compNumber);
    componentArray[compNumber] =
        new ComponentElectrolyteCPAAdvanced(name, moles, molesInPhase, compNumber);
  }

  /** {@inheritDoc} */
  @Override
  public PhaseElectrolyteCPAAdvanced clone() {
    PhaseElectrolyteCPAAdvanced clonedPhase = null;
    try {
      clonedPhase = (PhaseElectrolyteCPAAdvanced) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }
    return clonedPhase;
  }

  /**
   * Calculate the Born X parameter using advanced Born cavity radii.
   *
   * <p>
   * The parent implementation hardcodes bornX = sum(n_i * z_i^2 / sigma_i), using the Lennard-Jones
   * diameter. This override computes bornX directly from the temperature-dependent Born cavity
   * radii in {@link IonParametersAdvanced}, without depending on component Finit() having been
   * called (avoiding the init-order chicken-and-egg problem).
   * </p>
   *
   * <p>
   * For ions without advanced parameters, the calculation falls back to the parent's formula using
   * the Lennard-Jones diameter.
   * </p>
   *
   * @return bornX sum for the phase
   */
  @Override
  public double calcBornX() {
    double ans = 0.0;
    for (int i = 0; i < numberOfComponents; i++) {
      double z = componentArray[i].getIonicCharge();
      if (z != 0) {
        String name = componentArray[i].getComponentName();
        if (IonParametersAdvanced.hasIonData(name)) {
          double rBorn = IonParametersAdvanced.calcBornRadius(name, temperature);
          ans += componentArray[i].getNumberOfMolesInPhase() * z * z / (2.0 * rBorn * 1e-10);
        } else if (componentArray[i].getLennardJonesMolecularDiameter() > 0) {
          ans += componentArray[i].getNumberOfMolesInPhase() * z * z
              / (componentArray[i].getLennardJonesMolecularDiameter() * 1e-10);
        }
      }
    }
    return ans;
  }

  /**
   * Calculate the composition-weighted short-range interaction parameter for a component using
   * ion-specific parameters from {@link IonParametersAdvanced}.
   *
   * <p>
   * For ions with advanced parameters, this replaces the universal linear correlation (W = a*sigma
   * + b) with ion-specific W(T) = W0 + WT*(T-298.15) + WTT*(T-298.15)^2 for their interaction with
   * water. For non-water solvents and ions without advanced parameters, the parent mixing rule is
   * used.
   * </p>
   *
   * @param compNumb component index
   * @param phase phase object
   * @param temperature temperature in Kelvin
   * @param pressure pressure in bara
   * @param numbcomp number of components
   * @return composition-weighted W parameter for component compNumb
   */
  @Override
  public double calcWi(int compNumb, PhaseInterface phase, double temperature, double pressure,
      int numbcomp) {
    String ionName = componentArray[compNumb].getComponentName();

    if (componentArray[compNumb].getIonicCharge() != 0
        && IonParametersAdvanced.hasIonData(ionName)) {
      double wAdvanced = IonParametersAdvanced.calcW(ionName, temperature);
      double Wi = 0.0;
      for (int j = 0; j < numbcomp; j++) {
        if (componentArray[j].getComponentName().equals("water")) {
          Wi += componentArray[j].getNumberOfMolesInPhase() * wAdvanced;
        } else {
          Wi += componentArray[j].getNumberOfMolesInPhase()
              * electrolyteMixingRule.getWij(compNumb, j, temperature);
        }
      }
      return -2.0 * Wi;
    }

    return super.calcWi(compNumb, phase, temperature, pressure, numbcomp);
  }

  /**
   * Calculate the temperature derivative of the composition-weighted short-range interaction
   * parameter using ion-specific parameters from {@link IonParametersAdvanced}.
   *
   * @param compNumb component index
   * @param phase phase object
   * @param temperature temperature in Kelvin
   * @param pressure pressure in bara
   * @param numbcomp number of components
   * @return temperature derivative of composition-weighted W parameter
   */
  @Override
  public double calcWiT(int compNumb, PhaseInterface phase, double temperature, double pressure,
      int numbcomp) {
    String ionName = componentArray[compNumb].getComponentName();

    if (componentArray[compNumb].getIonicCharge() != 0
        && IonParametersAdvanced.hasIonData(ionName)) {
      double wAdvancedT = IonParametersAdvanced.calcWdT(ionName, temperature);
      double WiT = 0.0;
      for (int j = 0; j < numbcomp; j++) {
        if (componentArray[j].getComponentName().equals("water")) {
          WiT += componentArray[j].getNumberOfMolesInPhase() * wAdvancedT;
        } else {
          WiT += componentArray[j].getNumberOfMolesInPhase()
              * electrolyteMixingRule.getWijT(compNumb, j, temperature);
        }
      }
      return -2.0 * WiT;
    }

    return super.calcWiT(compNumb, phase, temperature, pressure, numbcomp);
  }

  /**
   * Initialize volume-dependent quantities including advanced terms.
   */
  @Override
  public void volInit() {
    super.volInit();
    // Calculate advanced terms after parent initialization
    calcAdvancedSRTerms();
    calcAdvancedBornTerms();
    calcIonPairReduction();
  }

  // =========================================================================
  // Advanced Short-Range Ion-Solvent Interaction Term
  //
  // A_SR_adv = (1/V) * sum_i sum_j n_i * n_j * W_ij(T)
  //
  // where W_ij(T) = W0_ij + WT_ij*(T-Tref) + WTT_ij*(T-Tref)^2
  // and the sum runs over all ion-solvent pairs (i=ion, j=solvent)
  //
  // Key difference from parent: Each ion has its OWN W parameters
  // instead of a universal linear correlation.
  // =========================================================================

  /**
   * Calculate the advanced short-range term and all required derivatives.
   *
   * <p>
   * This replaces the universal SR correlation with ion-specific parameters from
   * IonParametersAdvanced. The term accounts for non-electrostatic ion-solvent interactions
   * including hydration effects, cavity formation, and dispersion forces.
   * </p>
   */
  private void calcAdvancedSRTerms() {
    double volume = getMolarVolume() * numberOfMolesInPhase * 1e-5; // m3
    if (volume < 1e-50) {
      fAdvSR = 0.0;
      fAdvSRdT = 0.0;
      fAdvSRdV = 0.0;
      fAdvSRdTdT = 0.0;
      fAdvSRdTdV = 0.0;
      fAdvSRdVdV = 0.0;
      fAdvSRdVdVdV = 0.0;
      return;
    }

    double sumW = 0.0;
    double sumWdT = 0.0;
    double sumWdTdT = 0.0;

    // Sum over all ion-solvent pairs
    for (int i = 0; i < numberOfComponents; i++) {
      if (componentArray[i].getIonicCharge() == 0) {
        continue; // skip solvents in ion loop
      }

      String ionName = componentArray[i].getComponentName();
      IonParametersAdvanced.AdvancedIonData ionData = IonParametersAdvanced.getIonData(ionName);

      if (ionData == null) {
        continue; // No advanced parameters for this ion
      }

      double ni = componentArray[i].getNumberOfMolesInPhase();

      // W_ij(T) for this ion
      double wij = ionData.w0 + ionData.wT * (temperature - IonParametersAdvanced.T_REF)
          + ionData.wTT * Math.pow(temperature - IonParametersAdvanced.T_REF, 2.0);
      double wijdT = ionData.wT + 2.0 * ionData.wTT * (temperature - IonParametersAdvanced.T_REF);
      double wijdTdT = 2.0 * ionData.wTT;

      // Sum over solvent molecules
      for (int j = 0; j < numberOfComponents; j++) {
        if (componentArray[j].getIonicCharge() != 0) {
          continue; // only solvent partners
        }
        double nj = componentArray[j].getNumberOfMolesInPhase();
        sumW += ni * nj * wij;
        sumWdT += ni * nj * wijdT;
        sumWdTdT += ni * nj * wijdTdT;
      }
    }

    // A_SR_adv = sumW / V
    fAdvSR = sumW / volume;

    // dA/dT = sumWdT / V
    fAdvSRdT = sumWdT / volume;

    // d2A/dT2 = sumWdTdT / V
    fAdvSRdTdT = sumWdTdT / volume;

    // dA/dV = -sumW / V^2 (since A = sumW/V, dA/dV = -sumW/V^2)
    fAdvSRdV = -sumW / (volume * volume) * 1e-5;

    // d2A/dTdV = -sumWdT / V^2
    fAdvSRdTdV = -sumWdT / (volume * volume) * 1e-5;

    // d2A/dV2 = 2*sumW / V^3
    fAdvSRdVdV = 2.0 * sumW / (volume * volume * volume) * 1e-10;

    // d3A/dV3 = -6*sumW / V^4
    fAdvSRdVdVdV = -6.0 * sumW / (volume * volume * volume * volume) * 1e-15;
  }

  // =========================================================================
  // Advanced Born Solvation Term
  //
  // F_Born_adv = A_Born_adv / (R*T)
  // = -(NA * e^2) / (8*pi*eps0*R*T)
  // * sum_i n_i * z_i^2 / R_B,i(T)
  // * (1 - 1/epsilon_r)
  //
  // where R_B,i(T) = R_B0_i + R_BT_i * (T - T_ref)
  //
  // The temperature-dependent Born radius captures the thermal expansion
  // of the solvation cavity and the restructuring of the hydration shell.
  //
  // Note: All F values are REDUCED (dimensionless), i.e. divided by RT,
  // consistent with NeqSim's convention where getF() returns A_res/(RT).
  // =========================================================================

  /**
   * Calculate the advanced Born solvation term with temperature-dependent Born radii.
   *
   * <p>
   * This extends the standard Born term by allowing Born radii to vary with temperature. The
   * physical basis is that the ion-solvent cavity expands with temperature as the hydration shell
   * becomes more loosely structured.
   * </p>
   *
   * <p>
   * All values are in reduced form (divided by RT) consistent with NeqSim conventions.
   * </p>
   */
  private void calcAdvancedBornTerms() {
    // Physical constants
    double NA = avagadroNumber;
    double e2 = electronCharge * electronCharge;

    // Reduced prefactor: -NA*e^2 / (8*pi*eps0*R*T) — dimensionless per (mol/m)
    // This matches the parent FBorn convention which uses 1/(4*pi*eps0*R*T) with bornX
    double prefactor = -NA * e2 / (8.0 * Math.PI * vacumPermittivity * R * temperature);

    double epsInv = 1.0 / diElectricConstant;
    double solvationFactor = 1.0 - epsInv;

    // dε/dT for Born T-derivative
    double depsdT = diElectricConstantdT;
    double depsdTdT = diElectricConstantdTdT;

    double sumBorn = 0.0;
    double sumBorndT_rpart = 0.0; // contribution from dR_B/dT
    double sumBorndTdT_rpart = 0.0;

    for (int i = 0; i < numberOfComponents; i++) {
      if (componentArray[i].getIonicCharge() == 0) {
        continue;
      }

      String ionName = componentArray[i].getComponentName();
      IonParametersAdvanced.AdvancedIonData ionData = IonParametersAdvanced.getIonData(ionName);

      double ni = componentArray[i].getNumberOfMolesInPhase();
      double zi2 = Math.pow(componentArray[i].getIonicCharge(), 2.0);

      double rBorn;
      double rBorndT;
      if (ionData != null) {
        rBorn =
            (ionData.rBorn0 + ionData.rBornT * (temperature - IonParametersAdvanced.T_REF)) * 1e-10; // convert
                                                                                                     // Angstrom
                                                                                                     // to
                                                                                                     // m
        rBorndT = ionData.rBornT * 1e-10;
      } else {
        // Fallback to standard Born radius from LJ diameter
        double sigma = componentArray[i].getLennardJonesMolecularDiameter() * 1e-10;
        if (componentArray[i].getIonicCharge() > 0) {
          rBorn = 0.5 * sigma + 0.1e-10;
        } else {
          rBorn = 0.5 * sigma + 0.85e-10;
        }
        rBorndT = 0.0;
      }

      if (rBorn > 0) {
        sumBorn += ni * zi2 / rBorn;
        // d(1/R)/dT = -R'/R^2
        sumBorndT_rpart += ni * zi2 * (-rBorndT / (rBorn * rBorn));
        sumBorndTdT_rpart += ni * zi2 * (2.0 * rBorndT * rBorndT / (rBorn * rBorn * rBorn));
      }
    }

    // F_Born = prefactor * sumBorn * solvationFactor
    // where prefactor already includes 1/(R*T)
    fAdvBorn = prefactor * sumBorn * solvationFactor;

    // dF/dT has three contributions:
    // 1. Through 1/T in prefactor: -F/T
    // 2. Through ε(T): prefactor * sumBorn * dsolvdT
    // 3. Through R_B(T): prefactor * sumBorndT_rpart * solvationFactor
    double dsolvdT = depsdT / (diElectricConstant * diElectricConstant);
    fAdvBorndT = -fAdvBorn / temperature
        + prefactor * (sumBorn * dsolvdT + sumBorndT_rpart * solvationFactor);

    // d2F/dT2 (including all cross terms and second derivatives)
    double dsolvdTdT = depsdTdT / (diElectricConstant * diElectricConstant)
        - 2.0 * depsdT * depsdT / (diElectricConstant * diElectricConstant * diElectricConstant);

    // Second derivative of prefactor wrt T: d²(1/T)/dT² = 2/T³
    // So d²F/dT² = 2F/T² - (2/T) * [terms from first derivative excluding -F/T]
    // + prefactor * [second derivative terms]
    double termsFirstOrder = prefactor * (sumBorn * dsolvdT + sumBorndT_rpart * solvationFactor);
    fAdvBorndTdT = 2.0 * fAdvBorn / (temperature * temperature)
        - 2.0 * termsFirstOrder / temperature + prefactor * (sumBorn * dsolvdTdT
            + 2.0 * sumBorndT_rpart * dsolvdT + sumBorndTdT_rpart * solvationFactor);
  }

  /**
   * Calculate ion-pair formation and its effect on ionic strength.
   *
   * <p>
   * For 2:2 electrolyte pairs (e.g., MgSO4, CaSO4), explicit ion-pair formation reduces the
   * effective number of free ions. This correction is applied to the long-range (DH) term by
   * reducing the ion concentration used in the shielding parameter calculation.
   * </p>
   */
  private void calcIonPairReduction() {
    ionPairFractionReduction = 0.0;
    // Ion pairing only active if explicitly enabled
    if (ionPairOn < 0.5) {
      return;
    }

    // Find all cation-anion pairs that have ion-pair data
    for (int i = 0; i < numberOfComponents; i++) {
      if (componentArray[i].getIonicCharge() <= 0) {
        continue; // only cations
      }
      for (int j = 0; j < numberOfComponents; j++) {
        if (componentArray[j].getIonicCharge() >= 0) {
          continue; // only anions
        }

        String catName = componentArray[i].getComponentName();
        String anName = componentArray[j].getComponentName();

        IonParametersAdvanced.IonPairData ipData =
            IonParametersAdvanced.getIonPairData(catName, anName);
        if (ipData == null) {
          continue;
        }

        double kIP = IonParametersAdvanced.calcIonPairConstant(catName, anName, temperature);
        if (kIP < 1e-10) {
          continue;
        }

        // Simple Bjerrum-type correction:
        // alpha_IP = 1 - 1/(1 + K_IP * c_counter)
        // where c_counter is the concentration of the counter-ion
        double volume = getMolarVolume() * numberOfMolesInPhase * 1e-5; // m3
        if (volume > 1e-50) {
          double cAnion = componentArray[j].getNumberOfMolesInPhase() / (volume * 1000.0); // mol/L
          double alpha = kIP * cAnion / (1.0 + kIP * cAnion);
          // Limit alpha to avoid instability
          alpha = Math.min(alpha, 0.95);
          ionPairFractionReduction = Math.max(ionPairFractionReduction, alpha);
        }
      }
    }
  }

  // =========================================================================
  // Override Helmholtz energy and derivatives to include advanced terms
  // =========================================================================

  /** {@inheritDoc} */
  @Override
  public double getF() {
    // Parent Born now uses advanced radii via overridden calcBornX().
    // Only add advanced SR if enabled.
    return super.getF() + fAdvSR * advSROn;
  }

  /** {@inheritDoc} */
  @Override
  public double dFdT() {
    return super.dFdT() + fAdvSRdT * advSROn;
  }

  /** {@inheritDoc} */
  @Override
  public double dFdTdT() {
    return super.dFdTdT() + fAdvSRdTdT * advSROn;
  }

  /** {@inheritDoc} */
  @Override
  public double dFdV() {
    return super.dFdV() + fAdvSRdV * advSROn;
  }

  /** {@inheritDoc} */
  @Override
  public double dFdVdV() {
    return super.dFdVdV() + fAdvSRdVdV * advSROn;
  }

  /** {@inheritDoc} */
  @Override
  public double dFdVdVdV() {
    return super.dFdVdVdV() + fAdvSRdVdVdV * advSROn;
  }

  /** {@inheritDoc} */
  @Override
  public double dFdTdV() {
    return super.dFdTdV() + fAdvSRdTdV * advSROn;
  }

  // =========================================================================
  // Configuration methods
  // =========================================================================

  /**
   * Enable or disable the advanced short-range term.
   *
   * @param on 1.0 to enable, 0.0 to disable
   */
  public void setAdvancedSROn(double on) {
    this.advSROn = on;
  }

  /**
   * Enable or disable the advanced Born term.
   *
   * @param on 1.0 to enable, 0.0 to disable
   */
  public void setAdvancedBornOn(double on) {
    this.advBornOn = on;
  }

  /**
   * Enable or disable ion-pair formation.
   *
   * @param on 1.0 to enable, 0.0 to disable
   */
  public void setIonPairOn(double on) {
    this.ionPairOn = on;
  }

  /**
   * Get the fraction of ions forming ion pairs.
   *
   * @return ion pair fraction reduction (0 to 0.95)
   */
  public double getIonPairFractionReduction() {
    return ionPairFractionReduction;
  }

  /**
   * Get the advanced Born solvation contribution to Helmholtz energy.
   *
   * @return F_Born_adv
   */
  public double getAdvancedBornEnergy() {
    return fAdvBorn;
  }

  /**
   * Get the advanced short-range contribution to Helmholtz energy.
   *
   * @return F_SR_adv
   */
  public double getAdvancedSREnergy() {
    return fAdvSR;
  }
}
