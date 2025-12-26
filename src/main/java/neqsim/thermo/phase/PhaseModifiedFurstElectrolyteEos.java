/*
 * PhaseModifiedFurstElectrolyteEos.java
 *
 * Created on 26. februar 2001, 17:54
 */

package neqsim.thermo.phase;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.component.ComponentModifiedFurstElectrolyteEos;

/**
 * <p>
 * PhaseModifiedFurstElectrolyteEos class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class PhaseModifiedFurstElectrolyteEos extends PhaseSrkEos {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(PhaseModifiedFurstElectrolyteEos.class);

  /**
   * Enum for selecting dielectric constant mixing rules for mixed solvents.
   *
   * <p>
   * <b>Warning:</b> Only MOLAR_AVERAGE is fully thermodynamically consistent. VOLUME_AVERAGE and
   * LOOYENGA provide correct dielectric constant values but have incomplete composition derivatives
   * (dε/dn), which may cause issues with fugacity coefficient calculations and phase equilibrium.
   * Use MOLAR_AVERAGE (default) for rigorous thermodynamic calculations.
   * </p>
   */
  public enum DielectricMixingRule {
    /** Molar average: eps_mix = sum(x_i * eps_i). Standard NeqSim approach. Fully consistent. */
    MOLAR_AVERAGE,
    /**
     * Volume average: eps_mix = sum(phi_i * eps_i). Better for water-glycol mixtures (2.4% avg
     * error vs 4.2% for molar). <b>Warning:</b> Composition derivatives not implemented - use for
     * property estimation only, not phase equilibrium.
     */
    VOLUME_AVERAGE,
    /**
     * Looyenga: eps_mix^(1/3) = sum(phi_i * eps_i^(1/3)). Theoretical basis for polar mixtures.
     * <b>Warning:</b> Composition derivatives not implemented - use for property estimation only,
     * not phase equilibrium.
     */
    LOOYENGA
  }

  /** The dielectric constant mixing rule to use for mixed solvents. */
  private DielectricMixingRule dielectricMixingRule = DielectricMixingRule.MOLAR_AVERAGE;

  double gammaold = 0;
  double alphaLRdTdV = 0;
  double W = 0;
  double WT = 0;
  double WTT = 0;
  double eps = 0;
  double epsdV = 0;
  double epsdVdV = 0;
  double epsIonic = 0;
  double bornX = 0;
  double epsIonicdV = 0;
  double epsIonicdVdV = 0;
  double alphaLR2 = 0;
  double alphaLRdT = 0.0;
  double alphaLRdTdT = 0.0;
  double alphaLRdV = 0.0;
  double XLR = 0;
  double XLRdT = 0;
  double shieldingParameterdT = 0;
  double solventDiElectricConstant = 0;
  double solventDiElectricConstantdT = 0.0;
  double solventDiElectricConstantdTdT = 0;
  double shieldingParameter = 0;
  double gamma = 0;
  double diElectricConstantdV = 0;
  double diElectricConstantdVdV = 0;
  double alphaLRdVdV = 0;
  double diElectricConstantdT = 0;
  double diElectricConstantdTdT = 0.0;
  double diElectricConstantdTdV = 0;
  neqsim.thermo.mixingrule.ElectrolyteMixingRulesInterface electrolyteMixingRule;
  double sr2On = 1.0;
  double lrOn = 1.0;
  double bornOn = 1.0;
  // double gammLRdV=0.0;
  // PhaseInterface[] refPhase; // = new PhaseInterface[10];

  /**
   * <p>
   * Constructor for PhaseModifiedFurstElectrolyteEos.
   * </p>
   */
  public PhaseModifiedFurstElectrolyteEos() {
    electrolyteMixingRule = mixSelect.getElectrolyteMixingRule(this);
  }

  /**
   * <p>
   * Getter for the field <code>electrolyteMixingRule</code>.
   * </p>
   *
   * @return a {@link neqsim.thermo.mixingrule.ElectrolyteMixingRulesInterface} object
   */
  public neqsim.thermo.mixingrule.ElectrolyteMixingRulesInterface getElectrolyteMixingRule() {
    return electrolyteMixingRule;
  }

  /**
   * <p>
   * reInitFurstParam.
   * </p>
   */
  public void reInitFurstParam() {
    for (int k = 0; k < numberOfComponents; k++) {
      ((ComponentModifiedFurstElectrolyteEos) componentArray[k]).initFurstParam();
    }
    electrolyteMixingRule = mixSelect.getElectrolyteMixingRule(this);
  }

  /** {@inheritDoc} */
  @Override
  public void init(double totalNumberOfMoles, int numberOfComponents, int initType, PhaseType pt,
      double beta) {
    super.init(totalNumberOfMoles, numberOfComponents, initType, pt, beta);
    if (initType == 0) {
      electrolyteMixingRule = mixSelect.getElectrolyteMixingRule(this);
    }
  }

  /** {@inheritDoc} */
  @Override
  public PhaseModifiedFurstElectrolyteEos clone() {
    PhaseModifiedFurstElectrolyteEos clonedPhase = null;
    try {
      clonedPhase = (PhaseModifiedFurstElectrolyteEos) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }
    // clonedPhase.electrolyteMixingRule =
    // (thermo.mixingRule.ElectrolyteMixingRulesInterface)
    // electrolyteMixingRule.clone();

    return clonedPhase;
  }

  /**
   * <p>
   * volInit.
   * </p>
   */
  public void volInit() {
    W = electrolyteMixingRule.calcW(this, temperature, pressure, numberOfComponents);
    WT = electrolyteMixingRule.calcWT(this, temperature, pressure, numberOfComponents);
    WTT = electrolyteMixingRule.calcWTT(this, temperature, pressure, numberOfComponents);
    eps = calcEps();
    epsdV = calcEpsV();
    epsdVdV = calcEpsVV();
    epsIonic = calcEpsIonic();
    epsIonicdV = calcEpsIonicdV();
    epsIonicdVdV = calcEpsIonicdVdV();
    solventDiElectricConstant = calcSolventDiElectricConstant(temperature);
    solventDiElectricConstantdT = calcSolventDiElectricConstantdT(temperature);
    solventDiElectricConstantdTdT = calcSolventDiElectricConstantdTdT(temperature);

    diElectricConstant = calcDiElectricConstant(temperature);
    diElectricConstantdT = calcDiElectricConstantdT(temperature);
    diElectricConstantdTdT = calcDiElectricConstantdTdT(temperature);
    diElectricConstantdV = calcDiElectricConstantdV(temperature);
    diElectricConstantdVdV = calcDiElectricConstantdVdV(temperature);
    diElectricConstantdTdV = calcDiElectricConstantdTdV(temperature);
    alphaLR2 = electronCharge * electronCharge * avagadroNumber
        / (vacumPermittivity * diElectricConstant * R * temperature);
    alphaLRdT = -electronCharge * electronCharge * avagadroNumber
        / (vacumPermittivity * diElectricConstant * R * temperature * temperature)
        - electronCharge * electronCharge * avagadroNumber
            / (vacumPermittivity * diElectricConstant * diElectricConstant * R * temperature)
            * diElectricConstantdT;
    alphaLRdV = -electronCharge * electronCharge * avagadroNumber
        / (vacumPermittivity * diElectricConstant * diElectricConstant * R * temperature)
        * diElectricConstantdV;
    alphaLRdTdT = 2.0 * electronCharge * electronCharge * avagadroNumber
        / (vacumPermittivity * diElectricConstant * R * Math.pow(temperature, 3.0))
        + electronCharge * electronCharge * avagadroNumber
            / (vacumPermittivity * diElectricConstant * diElectricConstant * R * temperature
                * temperature)
            * diElectricConstantdT
        - electronCharge * electronCharge * avagadroNumber
            / (vacumPermittivity * diElectricConstant * diElectricConstant * R * temperature)
            * diElectricConstantdTdT
        + electronCharge * electronCharge * avagadroNumber
            / (vacumPermittivity * diElectricConstant * diElectricConstant * R * temperature
                * temperature)
            * diElectricConstantdT
        + 2.0 * electronCharge * electronCharge * avagadroNumber
            / (vacumPermittivity * Math.pow(diElectricConstant, 3.0) * R * temperature)
            * Math.pow(diElectricConstantdT, 2.0);
    alphaLRdTdV = electronCharge * electronCharge * avagadroNumber
        / (vacumPermittivity * diElectricConstant * diElectricConstant * R * temperature
            * temperature)
        * diElectricConstantdV
        + 2.0 * electronCharge * electronCharge * avagadroNumber
            / (vacumPermittivity * diElectricConstant * diElectricConstant * diElectricConstant * R
                * temperature)
            * diElectricConstantdT * diElectricConstantdV
        - electronCharge * electronCharge * avagadroNumber
            / (vacumPermittivity * diElectricConstant * diElectricConstant * R * temperature)
            * diElectricConstantdTdV;
    alphaLRdVdV = -electronCharge * electronCharge * avagadroNumber
        / (vacumPermittivity * diElectricConstant * diElectricConstant * R * temperature)
        * diElectricConstantdVdV
        + 2.0 * electronCharge * electronCharge * avagadroNumber
            / (vacumPermittivity * Math.pow(diElectricConstant, 3.0) * R * temperature)
            * diElectricConstantdV * diElectricConstantdV;
    shieldingParameter = calcShieldingParameter();
    shieldingParameterdT = calcShieldingParameterdT();
    // gammLRdV = calcGammaLRdV();
    XLR = calcXLR();
    XLRdT = calcXLRdT();
    bornX = calcBornX();
  }

  /** {@inheritDoc} */
  @Override
  public void addComponent(String name, double moles, double molesInPhase, int compNumber) {
    super.addComponent(name, molesInPhase, compNumber);
    componentArray[compNumber] =
        new ComponentModifiedFurstElectrolyteEos(name, moles, molesInPhase, compNumber);
  }

  /**
   * <p>
   * calcSolventDiElectricConstant.
   * </p>
   *
   * <p>
   * Calculates the dielectric constant of the solvent mixture using the selected mixing rule. For
   * mixed solvents like water-glycol, the VOLUME_AVERAGE rule gives better accuracy (2.4% avg
   * error) compared to MOLAR_AVERAGE (4.2% avg error) based on Ma et al. (2010) data for EG-water.
   * </p>
   *
   * @param temperature a double
   * @return a double
   */
  public double calcSolventDiElectricConstant(double temperature) {
    switch (dielectricMixingRule) {
      case VOLUME_AVERAGE:
        return calcSolventDiElectricConstantVolumeAvg(temperature);
      case LOOYENGA:
        return calcSolventDiElectricConstantLooyenga(temperature);
      case MOLAR_AVERAGE:
      default:
        return calcSolventDiElectricConstantMolarAvg(temperature);
    }
  }

  /**
   * Calculate solvent dielectric constant using molar average mixing rule. This is the original
   * NeqSim approach: eps_mix = sum(x_i * eps_i)
   *
   * @param temperature Temperature in K
   * @return Molar-average dielectric constant
   */
  private double calcSolventDiElectricConstantMolarAvg(double temperature) {
    double ans1 = 0.0;
    double ans2 = 1e-50;
    for (int i = 0; i < numberOfComponents; i++) {
      if (componentArray[i].getIonicCharge() == 0) {
        ans1 += componentArray[i].getNumberOfMolesInPhase()
            * componentArray[i].getDiElectricConstant(temperature);
        ans2 += componentArray[i].getNumberOfMolesInPhase();
      }
    }
    return ans1 / ans2;
  }

  /**
   * Calculate solvent dielectric constant using volume average mixing rule. eps_mix = sum(phi_i *
   * eps_i) where phi_i = x_i*V_i/sum(x_j*V_j)
   *
   * <p>
   * This gives better accuracy for water-glycol mixtures (2.4% vs 4.2% for molar average).
   * </p>
   *
   * @param temperature Temperature in K
   * @return Volume-average dielectric constant
   */
  private double calcSolventDiElectricConstantVolumeAvg(double temperature) {
    double totalVolume = 1e-50;
    double weightedSum = 0.0;

    // First pass: calculate total molar volume
    for (int i = 0; i < numberOfComponents; i++) {
      if (componentArray[i].getIonicCharge() == 0) {
        double moles = componentArray[i].getNumberOfMolesInPhase();
        // Use critical volume as approximation for molar volume (Vc in L/mol)
        double molarVolume = componentArray[i].getCriticalVolume();
        totalVolume += moles * molarVolume;
      }
    }

    // Second pass: calculate volume-weighted average
    for (int i = 0; i < numberOfComponents; i++) {
      if (componentArray[i].getIonicCharge() == 0) {
        double moles = componentArray[i].getNumberOfMolesInPhase();
        double molarVolume = componentArray[i].getCriticalVolume();
        double volumeFraction = (moles * molarVolume) / totalVolume;
        weightedSum += volumeFraction * componentArray[i].getDiElectricConstant(temperature);
      }
    }
    return weightedSum;
  }

  /**
   * Calculate solvent dielectric constant using Looyenga mixing rule. eps_mix^(1/3) = sum(phi_i *
   * eps_i^(1/3))
   *
   * <p>
   * This has theoretical basis for polar molecule mixtures.
   * </p>
   *
   * @param temperature Temperature in K
   * @return Looyenga-rule dielectric constant
   */
  private double calcSolventDiElectricConstantLooyenga(double temperature) {
    double totalVolume = 1e-50;
    double looyengaSum = 0.0;

    // First pass: calculate total molar volume
    for (int i = 0; i < numberOfComponents; i++) {
      if (componentArray[i].getIonicCharge() == 0) {
        double moles = componentArray[i].getNumberOfMolesInPhase();
        double molarVolume = componentArray[i].getCriticalVolume();
        totalVolume += moles * molarVolume;
      }
    }

    // Second pass: calculate Looyenga sum
    for (int i = 0; i < numberOfComponents; i++) {
      if (componentArray[i].getIonicCharge() == 0) {
        double moles = componentArray[i].getNumberOfMolesInPhase();
        double molarVolume = componentArray[i].getCriticalVolume();
        double volumeFraction = (moles * molarVolume) / totalVolume;
        double eps_i = componentArray[i].getDiElectricConstant(temperature);
        looyengaSum += volumeFraction * Math.pow(eps_i, 1.0 / 3.0);
      }
    }
    return Math.pow(looyengaSum, 3.0);
  }

  /**
   * Set the dielectric constant mixing rule for mixed solvents.
   *
   * <p>
   * <b>Warning:</b> Only MOLAR_AVERAGE is thermodynamically consistent with complete composition
   * derivatives. VOLUME_AVERAGE and LOOYENGA are suitable for dielectric constant estimation but
   * may cause fugacity coefficient inconsistencies in phase equilibrium calculations.
   * </p>
   *
   * @param rule The mixing rule to use (MOLAR_AVERAGE, VOLUME_AVERAGE, or LOOYENGA)
   */
  public void setDielectricMixingRule(DielectricMixingRule rule) {
    this.dielectricMixingRule = rule;
  }

  /**
   * Get the current dielectric constant mixing rule.
   *
   * @return The current mixing rule
   */
  public DielectricMixingRule getDielectricMixingRule() {
    return dielectricMixingRule;
  }

  /**
   * <p>
   * calcSolventDiElectricConstantdT.
   * </p>
   *
   * @param temperature a double
   * @return a double
   */
  public double calcSolventDiElectricConstantdT(double temperature) {
    double ans1 = 0.0;
    double ans2 = 1e-50;
    for (int i = 0; i < numberOfComponents; i++) {
      if (componentArray[i].getIonicCharge() == 0) {
        ans1 += componentArray[i].getNumberOfMolesInPhase()
            * componentArray[i].getDiElectricConstantdT(temperature);
        ans2 += componentArray[i].getNumberOfMolesInPhase();
      }
    }
    return ans1 / ans2;
  }

  /**
   * <p>
   * calcSolventDiElectricConstantdTdT.
   * </p>
   *
   * @param temperature a double
   * @return a double
   */
  public double calcSolventDiElectricConstantdTdT(double temperature) {
    double ans1 = 0.0;
    double ans2 = 1e-50;
    for (int i = 0; i < numberOfComponents; i++) {
      if (componentArray[i].getIonicCharge() == 0) {
        ans1 += componentArray[i].getNumberOfMolesInPhase()
            * componentArray[i].getDiElectricConstantdTdT(temperature);
        ans2 += componentArray[i].getNumberOfMolesInPhase();
      }
    }
    return ans1 / ans2;
  }

  /**
   * <p>
   * calcEps.
   * </p>
   *
   * @return a double
   */
  public double calcEps() {
    double eps = 0.0;
    for (int i = 0; i < numberOfComponents; i++) {
      eps += avagadroNumber * pi / 6.0 * componentArray[i].getNumberOfMolesInPhase()
          * Math.pow(componentArray[i].getLennardJonesMolecularDiameter() * 1e-10, 3.0) * 1.0
          / (numberOfMolesInPhase * getMolarVolume() * 1e-5);
    }
    return eps;
  }

  /**
   * <p>
   * calcEpsV.
   * </p>
   *
   * @return a double
   */
  public double calcEpsV() {
    return -getEps() / (getMolarVolume() * 1e-5 * numberOfMolesInPhase);
  }

  /**
   * <p>
   * calcEpsVV.
   * </p>
   *
   * @return a double
   */
  public double calcEpsVV() {
    return 2.0 * getEps() / Math.pow(getMolarVolume() * 1e-5 * numberOfMolesInPhase, 2.0);
  }

  /**
   * <p>
   * calcEpsIonic.
   * </p>
   *
   * @return a double
   */
  public double calcEpsIonic() {
    double epsIonicLoc = 0.0;
    for (int i = 0; i < numberOfComponents; i++) {
      if (componentArray[i].getIonicCharge() != 0) {
        epsIonicLoc += avagadroNumber * pi / 6.0 * componentArray[i].getNumberOfMolesInPhase()
            * Math.pow(componentArray[i].getLennardJonesMolecularDiameter() * 1e-10, 3.0)
            / (numberOfMolesInPhase * getMolarVolume() * 1e-5);
      }
    }
    return epsIonicLoc;
  }

  /**
   * <p>
   * calcEpsIonicdV.
   * </p>
   *
   * @return a double
   */
  public double calcEpsIonicdV() {
    return -getEpsIonic() / (getMolarVolume() * 1e-5 * numberOfMolesInPhase);
  }

  /**
   * <p>
   * calcEpsIonicdVdV.
   * </p>
   *
   * @return a double
   */
  public double calcEpsIonicdVdV() {
    return 2.0 * getEpsIonic() / Math.pow(getMolarVolume() * 1e-5 * numberOfMolesInPhase, 2.0);
  }

  /** {@inheritDoc} */
  @Override
  public double getF() {
    return super.getF() + FSR2() * sr2On + FLR() * lrOn + FBorn() * bornOn;
  }

  /** {@inheritDoc} */
  @Override
  public double dFdT() {
    return super.dFdT() + dFSR2dT() * sr2On + dFLRdT() * lrOn + dFBorndT() * bornOn;
  }

  /** {@inheritDoc} */
  @Override
  public double dFdTdV() {
    return super.dFdTdV() + dFSR2dTdV() * sr2On + dFLRdTdV() * lrOn;
  }

  /** {@inheritDoc} */
  @Override
  public double dFdV() {
    return super.dFdV() + dFSR2dV() * sr2On + dFLRdV() * lrOn;
  }

  /** {@inheritDoc} */
  @Override
  public double dFdVdV() {
    return super.dFdVdV() + dFSR2dVdV() * sr2On + dFLRdVdV() * lrOn;
  }

  /** {@inheritDoc} */
  @Override
  public double dFdVdVdV() {
    return super.dFdVdVdV() + dFSR2dVdVdV() * sr2On + dFLRdVdVdV() * lrOn;
  }

  /** {@inheritDoc} */
  @Override
  public double dFdTdT() {
    return super.dFdTdT() + dFSR2dTdT() * sr2On + dFLRdTdT() * lrOn + dFBorndTdT() * bornOn;
  }

  /**
   * <p>
   * calcXLR.
   * </p>
   *
   * @return a double
   */
  public double calcXLR() {
    double ans = 0.0;
    for (int i = 0; i < numberOfComponents; i++) {
      if (componentArray[i].getIonicCharge() != 0) {
        ans += componentArray[i].getNumberOfMolesInPhase()
            * Math.pow(componentArray[i].getIonicCharge(), 2.0) * getShieldingParameter()
            / (1.0 + getShieldingParameter() * componentArray[i].getLennardJonesMolecularDiameter()
                * 1e-10);
      }
    }
    return ans;
  }

  /**
   * Calculates the temperature derivative of XLR using the chain rule. XLR depends on gamma
   * (shielding parameter) which depends on temperature.
   *
   * @return dXLR/dT
   */
  public double calcXLRdT() {
    double ans = 0.0;
    double dgammadT = shieldingParameterdT;
    for (int i = 0; i < numberOfComponents; i++) {
      if (componentArray[i].getIonicCharge() != 0) {
        double zi2 = Math.pow(componentArray[i].getIonicCharge(), 2.0);
        double ni = componentArray[i].getNumberOfMolesInPhase();
        double sigma = componentArray[i].getLennardJonesMolecularDiameter() * 1e-10;
        double denom = 1.0 + getShieldingParameter() * sigma;
        // d/dT of [ni * zi² * γ / (1 + γσ)] using chain rule
        // = ni * zi² * dγ/dT * (1 + γσ - γσ) / (1 + γσ)²
        // = ni * zi² * dγ/dT / (1 + γσ)²
        ans += ni * zi2 * dgammadT / (denom * denom);
      }
    }
    return ans;
  }

  /**
   * Calculates the temperature derivative of the shielding parameter using implicit
   * differentiation. The shielding parameter gamma satisfies: f(γ) = 4γ²/NA - αLR2 * Σ(ni/V *
   * zi²/(1+γσi)²) = 0
   *
   * By implicit differentiation: dγ/dT = -∂f/∂T / (∂f/∂γ)
   *
   * @return dγ/dT
   */
  public double calcShieldingParameterdT() {
    if (shieldingParameter < 1e-10) {
      return 0.0; // No ions in system
    }

    double volumeFactor = getMolarVolume() * numberOfMolesInPhase * 1e-5;

    // Calculate ∂f/∂γ (same as df in calcShieldingParameter)
    double dfdgamma = 8.0 * shieldingParameter / avagadroNumber;
    for (int i = 0; i < numberOfComponents; i++) {
      if (componentArray[i].getIonicCharge() != 0) {
        double zi2 = Math.pow(componentArray[i].getIonicCharge(), 2.0);
        double ni = componentArray[i].getNumberOfMolesInPhase();
        double sigma = componentArray[i].getLennardJonesMolecularDiameter() * 1e-10;
        dfdgamma += 2.0 * getAlphaLR2() * ni / volumeFactor * zi2 * sigma
            / Math.pow(1.0 + shieldingParameter * sigma, 3.0);
      }
    }

    // Calculate ∂f/∂T: only αLR2 depends on T in the implicit equation
    // f = 4γ²/NA - αLR2 * sum(...)
    // ∂f/∂T = -dαLR2/dT * sum(ni/V * zi²/(1+γσi)²)
    double sum = 0.0;
    for (int i = 0; i < numberOfComponents; i++) {
      if (componentArray[i].getIonicCharge() != 0) {
        double zi2 = Math.pow(componentArray[i].getIonicCharge(), 2.0);
        double ni = componentArray[i].getNumberOfMolesInPhase();
        double sigma = componentArray[i].getLennardJonesMolecularDiameter() * 1e-10;
        sum += ni / volumeFactor * zi2 / Math.pow(1.0 + shieldingParameter * sigma, 2.0);
      }
    }
    double dfdT = -alphaLRdT * sum;

    // dγ/dT = -∂f/∂T / (∂f/∂γ)
    if (Math.abs(dfdgamma) < 1e-50) {
      return 0.0;
    }
    return -dfdT / dfdgamma;
  }

  /**
   * <p>
   * calcGammaLRdV.
   * </p>
   *
   * @return a double
   */
  public double calcGammaLRdV() {
    if (pt == PhaseType.GAS) {
      return 0.0;
    }
    // return 0.0; // problem ved ren komponent
    return 1.0 / (8.0 * getShieldingParameter())
        * (4.0 * Math.pow(getShieldingParameter(), 2.0) / getAlphaLR2() * alphaLRdV
            + 4.0 * Math.pow(getShieldingParameter(), 2.0)
                / (numberOfMolesInPhase * getMolarVolume() * 1e-5));
    // // Math.pow(getIonicCharge()/(1.0+((PhaseModifiedFurstElectrolyteEos)
    // phase).getShieldingParameter()*getLennardJonesMolecularDiameter()*1e-10),2.0)
    // + alphai*temp) /(8.0*((PhaseModifiedFurstElectrolyteEos)
    // phase).getShieldingParameter());
  }

  /**
   * <p>
   * calcShieldingParameter.
   * </p>
   *
   * @return a double
   */
  public double calcShieldingParameter() {
    // if(pt==1) return 0.0;
    double df = 0;
    double f = 0;
    int ions = 0;
    int iterations = 0;
    gamma = 1e10;
    do {
      iterations++;
      gammaold = gamma;
      ions = 0;
      f = 4.0 * Math.pow(gamma, 2) / avagadroNumber;
      df = 8.0 * gamma / avagadroNumber;
      for (int i = 0; i < numberOfComponents; i++) {
        if (componentArray[i].getIonicCharge() != 0) {
          ions++;
          f += -getAlphaLR2() * componentArray[i].getNumberOfMolesInPhase()
              / (getMolarVolume() * numberOfMolesInPhase * 1e-5)
              * Math.pow(componentArray[i].getIonicCharge()
                  / (1.0 + gamma * componentArray[i].getLennardJonesMolecularDiameter() * 1e-10),
                  2.0);
          df += 2.0 * getAlphaLR2() * componentArray[i].getNumberOfMolesInPhase()
              / (getMolarVolume() * numberOfMolesInPhase * 1e-5)
              * Math.pow(componentArray[i].getIonicCharge(), 2.0)
              * (componentArray[i].getLennardJonesMolecularDiameter() * 1e-10) / (Math.pow(
                  1.0 + gamma * componentArray[i].getLennardJonesMolecularDiameter() * 1e-10, 3.0));
        }
      }
      gamma = ions > 0 ? gammaold - 0.8 * f / df : 0;
    } while ((Math.abs(f) > 1e-10 && iterations < 1000) || iterations < 3);
    // gamma = 1e9;
    // System.out.println("gamma " +gamma + " iterations " + iterations);
    return gamma;
  }

  // public double calcShieldingParameter2(){
  // if(pt==1) return 0.0;

  // double df=0, f=0;
  // int ions=0;
  // int iterations=0;
  // gamma=1e10;
  // do{
  // iterations++;
  // gammaold = gamma;
  // ions=0;
  // f = 4.0*Math.pow(gamma,2)/avagadroNumber;
  // df = 8.0*gamma/avagadroNumber;
  // for(int i=0;i<numberOfComponents;i++){
  // if(componentArray[i].getIonicCharge()!=0){
  // ions++;
  // f += -
  // getAlphaLR2()*componentArray[i].getNumberOfMolesInPhase()/(molarVolume*numberOfMolesInPhase*1e-5)*Math.pow(componentArray[i].getIonicCharge(),2.0)/Math.pow((1.0+gamma*componentArray[i].getLennardJonesMolecularDiameter()*1e-10),3.0);
  // df +=
  // getAlphaLR2()*componentArray[i].getNumberOfMolesInPhase()/(molarVolume*numberOfMolesInPhase*1e-5)*Math.pow(componentArray[i].getIonicCharge(),2.0)*(componentArray[i].getLennardJonesMolecularDiameter()*1e-10)/Math.pow(1.0+gamma*componentArray[i].getLennardJonesMolecularDiameter()*1e-10,4.0);
  // }
  // }
  // gamma = ions>0 ? gammaold - 0.8*f/df : 0;
  // }
  // while((Math.abs(f)>1e-10 && iterations<1000) || iterations<5);
  // //System.out.println("gama " + gamma*1e-10);
  // return gamma;
  // }

  /** {@inheritDoc} */
  @Override
  public double molarVolume(double pressure, double temperature, double A, double B, PhaseType pt)
      throws neqsim.util.exception.IsNaNException,
      neqsim.util.exception.TooManyIterationsException {
    // double BonV = phase== 0 ?
    // 2.0/(2.0+temperature/getPseudoCriticalTemperature()):0.1*pressure*getB()/(numberOfMolesInPhase*temperature*R);
    double BonV = pt == PhaseType.LIQUID ? 0.99 : 1e-5;

    if (BonV < 0) {
      BonV = 1.0e-6;
    }
    if (BonV > 1.0) {
      BonV = 1.0 - 1.0e-6;
    }
    double BonVold = BonV;
    double Btemp = 0;
    double h = 0;
    double dh = 0;
    double dhh = 0;
    double d1 = 0;
    double d2 = 0;
    Btemp = getB();
    if (Btemp <= 0) {
      logger.info("b negative in volume calc");
    }
    setMolarVolume(1.0 / BonV * Btemp / numberOfMolesInPhase);
    int iterations = 0;
    int maxIterations = 1000;
    do {
      iterations++;
      this.volInit();
      BonVold = BonV;
      h = BonV - Btemp / numberOfMolesInPhase * dFdV()
          - pressure * Btemp / (numberOfMolesInPhase * R * temperature);
      dh = 1.0 + Btemp / Math.pow(BonV, 2.0) * (Btemp / numberOfMolesInPhase * dFdVdV());
      dhh = -2.0 * Btemp / Math.pow(BonV, 3.0) * (Btemp / numberOfMolesInPhase * dFdVdV())
          - Math.pow(Btemp, 2.0) / Math.pow(BonV, 4.0)
              * (Btemp / numberOfMolesInPhase * dFdVdVdV());

      d1 = -h / dh;
      d2 = -dh / dhh;

      if (Math.abs(d1 / d2) <= 1.0) {
        BonV += d1 * (1.0 + 0.5 * d1 / d2);
      } else if (d1 / d2 < -1) {
        BonV += d1 * (1.0 + 0.5 * -1.0);
      } else if (d1 / d2 > 1) {
        BonV += d2;
        double hnew = h + d2 * -h / d1;
        if (Math.abs(hnew) > Math.abs(h)) {
          logger.info("volume correction needed....");
          BonV = pt == PhaseType.GAS ? 2.0 / (2.0 + temperature / getPseudoCriticalTemperature())
              : pressure * getB() / (numberOfMolesInPhase * temperature * R);
        }
      }

      if (BonV > 1) {
        BonV = 1.0 - 1.0e-6;
        BonVold = 10;
      }
      if (BonV < 0) {
        BonV = 1.0e-6;
        BonVold = 10;
      }

      setMolarVolume(1.0 / BonV * Btemp / numberOfMolesInPhase);
      Z = pressure * getMolarVolume() / (R * temperature);
    } while (Math.abs((BonV - BonVold) / BonV) > 1.0e-10 && iterations < maxIterations);
    this.volInit();
    if (iterations >= maxIterations) {
      logger.warn("molarVolume did not converge after {} iterations for phase {}. Using last"
          + " valid estimate to continue calculations.", iterations, pt);
      BonV = Math.max(Math.min(BonV, 1.0 - 1.0e-6), 1.0e-8);
      setMolarVolume(1.0 / BonV * Btemp / numberOfMolesInPhase);
      Z = pressure * getMolarVolume() / (R * temperature);
      return getMolarVolume();
    }
    if (Double.isNaN(getMolarVolume())) {
      throw new neqsim.util.exception.IsNaNException(this, "molarVolume", "Molar volume");
    }

    // if(pt==0) System.out.println("density " + getDensity()); //"BonV: " +
    // BonV + " "+" itert: " + iterations +" " + " phase " + pt+ " " + h + "
    // " +dh + " B " + Btemp + " D " + Dtemp + " gv" + gV() + " fv " + fv() + " fvv"
    // + fVV());

    return getMolarVolume();
  }

  /**
   * <p>
   * calcW.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param temperature a double
   * @param pressure a double
   * @param numbcomp a int
   * @return a double
   */
  public double calcW(PhaseInterface phase, double temperature, double pressure, int numbcomp) {
    W = electrolyteMixingRule.calcW(phase, temperature, pressure, numbcomp);
    return W;
  }

  /**
   * <p>
   * calcWi.
   * </p>
   *
   * @param compNumb a int
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param temperature a double
   * @param pressure a double
   * @param numbcomp a int
   * @return a double
   */
  public double calcWi(int compNumb, PhaseInterface phase, double temperature, double pressure,
      int numbcomp) {
    return electrolyteMixingRule.calcWi(compNumb, phase, temperature, pressure, numbcomp);
  }

  /**
   * <p>
   * calcWiT.
   * </p>
   *
   * @param compNumb a int
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param temperature a double
   * @param pressure a double
   * @param numbcomp a int
   * @return a double
   */
  public double calcWiT(int compNumb, PhaseInterface phase, double temperature, double pressure,
      int numbcomp) {
    return electrolyteMixingRule.calcWiT(compNumb, phase, temperature, pressure, numbcomp);
  }

  /**
   * <p>
   * calcWij.
   * </p>
   *
   * @param compNumb a int
   * @param compNumbj a int
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param temperature a double
   * @param pressure a double
   * @param numbcomp a int
   * @return a double
   */
  public double calcWij(int compNumb, int compNumbj, PhaseInterface phase, double temperature,
      double pressure, int numbcomp) {
    return electrolyteMixingRule.calcWij(compNumb, compNumbj, phase, temperature, pressure,
        numbcomp);
  }

  /** {@inheritDoc} */
  @Override
  public double calcDiElectricConstant(double temperature) {
    return 1.0 + (getSolventDiElectricConstant() - 1.0) * (1.0 - getEpsIonic())
        / (1.0 + getEpsIonic() / 2.0);
  }

  /**
   * <p>
   * calcDiElectricConstantdV.
   * </p>
   *
   * @param temperature a double
   * @return a double
   */
  public double calcDiElectricConstantdV(double temperature) {
    double X = (1.0 - getEpsIonic()) / (1.0 + getEpsIonic() / 2.0);
    // double Y= getSolventDiElectricConstant(); 10-2002
    double Y = getSolventDiElectricConstant() - 1.0;
    double dXdf = getEpsIonicdV() * -3.0 / 2.0 / Math.pow(getEpsIonic() / 2.0 + 1.0, 2.0);
    double dYdf = 0;
    return dYdf * X + Y * dXdf;
  }

  /**
   * <p>
   * calcDiElectricConstantdVdV.
   * </p>
   *
   * @param temperature a double
   * @return a double
   */
  public double calcDiElectricConstantdVdV(double temperature) {
    double Y = getSolventDiElectricConstant() - 1.0;
    double dXdf = getEpsIonicdVdV() * -3.0 / 2.0 / Math.pow(getEpsIonic() / 2.0 + 1.0, 2.0)
        + getEpsIonicdV() * getEpsIonicdV() * 3.0 / 2.0 / Math.pow(getEpsIonic() / 2.0 + 1.0, 3.0);
    return Y * dXdf; // + Y*dXdf;
  }

  /** {@inheritDoc} */
  @Override
  public double calcDiElectricConstantdT(double temperature) {
    double X = (1.0 - getEpsIonic()) / (1.0 + getEpsIonic() / 2.0);
    double Y = getSolventDiElectricConstant() - 1.0;
    double dXdf = 0;
    double dYdf = getSolventDiElectricConstantdT();
    return dYdf * X + Y * dXdf;
  }

  /** {@inheritDoc} */
  @Override
  public double calcDiElectricConstantdTdT(double temperature) {
    return getSolventDiElectricConstantdTdT() * (1.0 - epsIonic) / (1.0 + epsIonic / 2.0);
  }

  /**
   * <p>
   * calcDiElectricConstantdTdV.
   * </p>
   *
   * @param temperature a double
   * @return a double
   */
  public double calcDiElectricConstantdTdV(double temperature) {
    double Y = getSolventDiElectricConstantdT();
    double dXdf = getEpsIonicdV() * -3.0 / 2.0 / Math.pow(getEpsIonic() / 2.0 + 1.0, 2.0);
    return Y * dXdf;
  }

  /**
   * <p>
   * calcBornX.
   * </p>
   *
   * @return a double
   */
  public double calcBornX() {
    double ans = 0.0;
    for (int i = 0; i < numberOfComponents; i++) {
      if (componentArray[i].getLennardJonesMolecularDiameter() > 0) {
        ans += componentArray[i].getNumberOfMolesInPhase()
            * Math.pow(componentArray[i].getIonicCharge(), 2.0)
            / (componentArray[i].getLennardJonesMolecularDiameter() * 1e-10);
      }
    }
    return ans;
  }

  // Long Range term equations and derivatives

  /**
   * <p>
   * FLR. MSA long-range ion-ion interaction contribution to Helmholtz free energy.
   * </p>
   *
   * @return a double
   */
  public double FLR() {
    double ans = 0.0;
    ans -= (1.0 / (4.0 * pi) * getAlphaLR2() * getXLR());
    ans += (numberOfMolesInPhase * getMolarVolume() * 1e-5 * Math.pow(getShieldingParameter(), 3.0))
        / (3.0 * pi * avagadroNumber);
    return ans;
  }

  /**
   * <p>
   * dFLRdT. Temperature derivative of the long-range MSA contribution at constant V.
   * </p>
   * FLR = -αLR*XLR/(4π) + n*V*γ³/(3π*NA) * 1e-5
   * 
   * dFLR/dT|V = -1/(4π) * (dαLR/dT * XLR + αLR * dXLR/dT) + n*V*1e-5/(3π*NA) * 3γ² * dγ/dT
   * 
   * Note: This derivative is computed at constant V, which is correct for the Helmholtz free energy
   * formulation. The relationship between constant-P and constant-V derivatives is handled through
   * the pressure equation of state: dF/dT|P = dF/dT|V + (dF/dV)*dV/dT|P. Numerical tests at
   * constant P will show ~15% difference which is expected behavior.
   *
   * @return a double
   */
  public double dFLRdT() {
    // Term 1: d/dT of [-αLR*XLR/(4π)] = -1/(4π) * (dαLR/dT * XLR + αLR * dXLR/dT)
    double term1 = -1.0 / (4.0 * pi) * (alphaLRdT * getXLR() + getAlphaLR2() * XLRdT);

    // Term 2: d/dT of [n*V*γ³*1e-5/(3π*NA)] at constant V
    // The dependence through γ: d/dT|V = n*V*1e-5/(3π*NA) * 3γ² * dγ/dT
    double term2 = numberOfMolesInPhase * getMolarVolume() * 1e-5 / (3.0 * pi * avagadroNumber)
        * 3.0 * Math.pow(getShieldingParameter(), 2.0) * shieldingParameterdT;

    return (term1 + term2);
  }

  /**
   * <p>
   * dFLRdTdV.
   * </p>
   *
   * @return a double
   */
  public double dFLRdTdV() {
    return (dFdAlphaLR() * alphaLRdTdV) * 1e-5;
  }

  /**
   * <p>
   * dFLRdTdT. Second temperature derivative of the long-range MSA contribution.
   * </p>
   * Note: This is a simplified implementation that includes the main terms. The full second
   * derivative would require d²γ/dT² and d²XLR/dT² which are not yet implemented.
   *
   * @return a double
   */
  public double dFLRdTdT() {
    // Simplified: only includes d²αLR/dT² * XLR term
    // Full implementation would need d²γ/dT², d²XLR/dT², and cross terms
    double term1 = -1.0 / (4.0 * pi) * alphaLRdTdT * getXLR();
    // Add the cross term: -2/(4π) * dαLR/dT * dXLR/dT
    double crossTerm = -2.0 / (4.0 * pi) * alphaLRdT * XLRdT;
    return (term1 + crossTerm);
  }

  /**
   * <p>
   * dFLRdV. Volume derivative of FLR.
   * </p>
   *
   * @return a double
   */
  public double dFLRdV() {
    return (FLRV() + dFdAlphaLR() * alphaLRdV) * 1e-5;
  }

  /**
   * <p>
   * dFLRdVdV. Second volume derivative of FLR.
   * </p>
   *
   * @return a double
   */
  public double dFLRdVdV() {
    return (dFdAlphaLR() * alphaLRdVdV) * 1e-10;
  }

  /**
   * <p>
   * dFLRdVdVdV.
   * </p>
   *
   * @return a double
   */
  public double dFLRdVdVdV() {
    return 0.0;
  }

  // first order derivatives

  /**
   * <p>
   * FLRXLR. Derivative of FLR with respect to XLR.
   * </p>
   *
   * @return a double
   */
  public double FLRXLR() {
    return -getAlphaLR2() / (4.0 * pi);
  }

  /**
   * <p>
   * FLRGammaLR. Derivative of FLR with respect to gamma (shielding parameter).
   * </p>
   *
   * @return a double
   */
  public double FLRGammaLR() {
    return 3.0 * numberOfMolesInPhase * getMolarVolume() * 1e-5
        * Math.pow(getShieldingParameter(), 2.0) / (3.0 * pi * avagadroNumber);
  }

  /**
   * <p>
   * dFdAlphaLR. Derivative of FLR with respect to alphaLR.
   * </p>
   *
   * @return a double
   */
  public double dFdAlphaLR() {
    return -1.0 / (4.0 * pi) * XLR;
  }

  /**
   * <p>
   * dFdAlphaLRdV.
   * </p>
   *
   * @return a double
   */
  public double dFdAlphaLRdV() {
    return 0.0;
  }

  /**
   * <p>
   * dFdAlphaLRdX.
   * </p>
   *
   * @return a double
   */
  public double dFdAlphaLRdX() {
    return -1.0 / (4.0 * pi);
  }

  /**
   * <p>
   * dFdAlphaLRdGamma.
   * </p>
   *
   * @return a double
   */
  public double dFdAlphaLRdGamma() {
    return 0;
  }

  /**
   * <p>
   * FLRV. Volume derivative helper for FLR.
   * </p>
   *
   * @return a double
   */
  public double FLRV() {
    return Math.pow(getShieldingParameter(), 3.0) / (3.0 * pi * avagadroNumber);
  }

  /**
   * <p>
   * FLRVV.
   * </p>
   *
   * @return a double
   */
  public double FLRVV() {
    return 0.0;
  }

  // second order derivatives

  /**
   * <p>
   * dFdAlphaLRdAlphaLR.
   * </p>
   *
   * @return a double
   */
  public double dFdAlphaLRdAlphaLR() {
    return 0.0;
  }

  /**
   * <p>
   * XLRdndn.
   * </p>
   *
   * @param i a int
   * @param j a int
   * @return a double
   */
  public double XLRdndn(int i, int j) {
    return 0.0;
  }

  /**
   * <p>
   * XLRdGammaLR.
   * </p>
   *
   * @return a double
   */
  public double XLRdGammaLR() {
    // if(pt==1) return 0.0;
    double ans = 0.0;
    double ans2 = 0.0;
    for (int i = 0; i < numberOfComponents; i++) {
      ans -= componentArray[i].getLennardJonesMolecularDiameter() * 1e-10
          * componentArray[i].getNumberOfMolesInPhase()
          * Math.pow(componentArray[i].getIonicCharge(), 2.0) * getShieldingParameter()
          / Math.pow(1.0 + getShieldingParameter()
              * componentArray[i].getLennardJonesMolecularDiameter() * 1e-10, 2.0);
      ans2 += componentArray[i].getNumberOfMolesInPhase()
          * Math.pow(componentArray[i].getIonicCharge(), 2.0) / (1.0 + getShieldingParameter()
              * componentArray[i].getLennardJonesMolecularDiameter() * 1e-10);
    }
    return ans2 + ans;
  }

  /**
   * <p>
   * XBorndndn.
   * </p>
   *
   * @param i a int
   * @param j a int
   * @return a double
   */
  public double XBorndndn(int i, int j) {
    return 0.0;
  }

  // Short Range term equations and derivatives
  /**
   * <p>
   * FSR2. Short-range ion-solvent and ion-ion interaction contribution to Helmholtz free energy.
   * </p>
   *
   * @return a double
   */
  public double FSR2() {
    return getW() / (getMolarVolume() * 1e-5 * numberOfMolesInPhase * (1.0 - eps));
  }

  /**
   * <p>
   * dFSR2dT.
   * </p>
   *
   * @return a double
   */
  public double dFSR2dT() {
    return FSR2W() * WT;
  }

  /**
   * <p>
   * dFSR2dTdT.
   * </p>
   *
   * @return a double
   */
  public double dFSR2dTdT() {
    return FSR2W() * WTT;
  }

  /**
   * <p>
   * dFSR2dV.
   * </p>
   *
   * @return a double
   */
  public double dFSR2dV() {
    return (FSR2V() + FSR2eps() * getEpsdV()) * 1e-5;
  }

  /**
   * <p>
   * dFSR2dTdV.
   * </p>
   *
   * @return a double
   */
  public double dFSR2dTdV() {
    return (FSR2VW() * WT + FSR2epsW() * epsdV * WT) * 1e-5;
  }

  /**
   * <p>
   * dFSR2dVdV.
   * </p>
   *
   * @return a double
   */
  public double dFSR2dVdV() {
    return (FSR2VV() + 2.0 * FSR2epsV() * getEpsdV() + FSR2epseps() * Math.pow(getEpsdV(), 2.0)
        + FSR2eps() * getEpsdVdV()) * 1e-10;
  }

  /**
   * <p>
   * dFSR2dVdVdV.
   * </p>
   *
   * @return a double
   */
  public double dFSR2dVdVdV() {
    return (FSR2VVV() + 3 * FSR2epsepsV() * Math.pow(getEpsdV(), 2.0) + 3 * FSR2VVeps() * getEpsdV()
        + FSR2epsepseps() * Math.pow(getEpsdV(), 3.0)) * 1e-15;
  }

  // first order derivatives
  /**
   * <p>
   * FSR2W. Partial derivative of FSR2 with respect to W.
   * </p>
   *
   * @return a double
   */
  public double FSR2W() {
    return 1.0 / (getMolarVolume() * 1e-5 * numberOfMolesInPhase * (1.0 - eps));
  }

  /**
   * <p>
   * FSR2V. Partial derivative of FSR2 with respect to V.
   * </p>
   *
   * @return a double
   */
  public double FSR2V() {
    return -W / (Math.pow(getMolarVolume() * 1e-5 * numberOfMolesInPhase, 2.0) * (1.0 - eps));
  }

  /**
   * <p>
   * FSR2T.
   * </p>
   *
   * @return a double
   */
  public double FSR2T() {
    return 0.0;
  }

  /**
   * <p>
   * FSR2n.
   * </p>
   *
   * @return a double
   */
  public double FSR2n() {
    return 0;
  }

  /**
   * <p>
   * FSR2eps. Partial derivative of FSR2 with respect to epsilon.
   * </p>
   *
   * @return a double
   */
  public double FSR2eps() {
    return W / ((getMolarVolume() * 1e-5 * numberOfMolesInPhase) * Math.pow(1.0 - eps, 2.0));
  }

  // second order derivatives

  /**
   * <p>
   * FSR2nn.
   * </p>
   *
   * @return a double
   */
  public double FSR2nn() {
    return 0;
  }

  /**
   * <p>
   * FSR2nT.
   * </p>
   *
   * @return a double
   */
  public double FSR2nT() {
    return 0;
  }

  /**
   * <p>
   * FSR2nV.
   * </p>
   *
   * @return a double
   */
  public double FSR2nV() {
    return 0;
  }

  /**
   * <p>
   * FSR2neps.
   * </p>
   *
   * @return a double
   */
  public double FSR2neps() {
    return 0;
  }

  /**
   * <p>
   * FSR2nW.
   * </p>
   *
   * @return a double
   */
  public double FSR2nW() {
    return 0;
  }

  /**
   * <p>
   * FSR2Tn.
   * </p>
   *
   * @return a double
   */
  public double FSR2Tn() {
    return 0;
  }

  /**
   * <p>
   * FSR2TT.
   * </p>
   *
   * @return a double
   */
  public double FSR2TT() {
    return 0;
  }

  /**
   * <p>
   * FSR2TV.
   * </p>
   *
   * @return a double
   */
  public double FSR2TV() {
    return 0;
  }

  /**
   * <p>
   * FSR2Teps.
   * </p>
   *
   * @return a double
   */
  public double FSR2Teps() {
    return 0;
  }

  /**
   * <p>
   * FSR2TW.
   * </p>
   *
   * @return a double
   */
  public double FSR2TW() {
    return 0;
  }

  /**
   * <p>
   * FSR2VV. Second partial derivative of FSR2 with respect to V.
   * </p>
   *
   * @return a double
   */
  public double FSR2VV() {
    return 2.0 * W / (Math.pow(getMolarVolume() * 1e-5 * numberOfMolesInPhase, 3.0) * (1.0 - eps));
  }

  /**
   * <p>
   * FSR2epsV. Mixed partial derivative of FSR2 with respect to epsilon and V.
   * </p>
   *
   * @return a double
   */
  public double FSR2epsV() {
    return -W / (Math.pow(getMolarVolume() * 1e-5 * numberOfMolesInPhase, 2.0)
        * Math.pow((1.0 - eps), 2.0));
  }

  /**
   * <p>
   * FSR2epsW. Mixed partial derivative of FSR2 with respect to epsilon and W.
   * </p>
   *
   * @return a double
   */
  public double FSR2epsW() {
    return 1.0 / (getMolarVolume() * 1e-5 * numberOfMolesInPhase * Math.pow(1.0 - eps, 2.0));
  }

  /**
   * <p>
   * FSR2WW.
   * </p>
   *
   * @return a double
   */
  public double FSR2WW() {
    return 0.0;
  }

  /**
   * <p>
   * FSR2VW. Mixed partial derivative of FSR2 with respect to V and W.
   * </p>
   *
   * @return a double
   */
  public double FSR2VW() {
    return -1.0 / (Math.pow(getMolarVolume() * 1e-5 * numberOfMolesInPhase, 2.0) * (1.0 - eps));
  }

  /**
   * <p>
   * FSR2epseps. Second partial derivative of FSR2 with respect to epsilon.
   * </p>
   *
   * @return a double
   */
  public double FSR2epseps() {
    return 2.0 * W / ((getMolarVolume() * 1e-5 * numberOfMolesInPhase) * Math.pow(1.0 - eps, 3.0));
  }

  /**
   * <p>
   * FSR2VVV. Third partial derivative of FSR2 with respect to V.
   * </p>
   *
   * @return a double
   */
  public double FSR2VVV() {
    return -6.0 * W / (Math.pow(getMolarVolume() * 1e-5 * numberOfMolesInPhase, 4.0) * (1.0 - eps));
  }

  // third order derivatives
  /**
   * <p>
   * FSR2epsepsV.
   * </p>
   *
   * @return a double
   */
  public double FSR2epsepsV() {
    return -2.0 * W / (Math.pow(getMolarVolume() * 1e-5 * numberOfMolesInPhase, 2.0)
        * Math.pow((1 - eps), 3.0));
  }

  /**
   * <p>
   * FSR2VVeps.
   * </p>
   *
   * @return a double
   */
  public double FSR2VVeps() {
    return 2.0 * W / (Math.pow(getMolarVolume() * 1e-5 * numberOfMolesInPhase, 3.0)
        * Math.pow((1 - eps), 2.0));
  }

  /**
   * <p>
   * FSR2epsepseps.
   * </p>
   *
   * @return a double
   */
  public double FSR2epsepseps() {
    return 6.0 * W / ((getMolarVolume() * 1e-5 * numberOfMolesInPhase) * Math.pow(1.0 - eps, 4.0));
  }

  // Born term equations and derivatives
  /**
   * <p>
   * FBorn. Born solvation contribution to Helmholtz free energy.
   * </p>
   *
   * @return a double
   */
  public double FBorn() {
    return (avagadroNumber * electronCharge * electronCharge
        / (4.0 * pi * vacumPermittivity * R * temperature))
        * (1.0 / getSolventDiElectricConstant() - 1.0) * bornX;
  }

  /**
   * <p>
   * dFBorndT.
   * </p>
   *
   * @return a double
   */
  public double dFBorndT() {
    return (FBornT() + FBornD() * solventDiElectricConstantdT);
  }

  /**
   * <p>
   * dFBorndTdT.
   * </p>
   *
   * @return a double
   */
  public double dFBorndTdT() {
    return (FBornTT() + FBornTD() * solventDiElectricConstantdT);
  }

  // first order derivatives
  /**
   * <p>
   * FBornT. Temperature derivative of Born term.
   * </p>
   *
   * @return a double
   */
  public double FBornT() {
    return -(avagadroNumber * electronCharge * electronCharge
        / (4.0 * pi * vacumPermittivity * R * temperature * temperature))
        * (1.0 / getSolventDiElectricConstant() - 1.0) * bornX;
  }

  /**
   * <p>
   * FBornX. Derivative of Born term with respect to bornX sum.
   * </p>
   *
   * @return a double
   */
  public double FBornX() {
    return (avagadroNumber * electronCharge * electronCharge
        / (4.0 * pi * vacumPermittivity * R * temperature))
        * (1.0 / getSolventDiElectricConstant() - 1.0);
  }

  /**
   * <p>
   * FBornD. Derivative of Born term with respect to dielectric constant.
   * </p>
   *
   * @return a double
   */
  public double FBornD() {
    return -(avagadroNumber * electronCharge * electronCharge
        / (4.0 * pi * vacumPermittivity * R * temperature)) * 1.0
        / Math.pow(getSolventDiElectricConstant(), 2.0) * bornX;
  }

  // second order derivatives

  /**
   * <p>
   * FBornTT. Second temperature derivative of Born term.
   * </p>
   *
   * @return a double
   */
  public double FBornTT() {
    return 2.0
        * (avagadroNumber * electronCharge * electronCharge
            / (4.0 * pi * vacumPermittivity * R * temperature * temperature * temperature))
        * (1.0 / getSolventDiElectricConstant() - 1.0) * bornX;
  }

  /**
   * <p>
   * FBornTD. Mixed temperature-dielectric derivative of Born term.
   * </p>
   *
   * @return a double
   */
  public double FBornTD() {
    return (avagadroNumber * electronCharge * electronCharge
        / (4.0 * pi * vacumPermittivity * R * temperature * temperature)) * 1.0
        / Math.pow(getSolventDiElectricConstant(), 2.0) * bornX;
  }

  /**
   * <p>
   * FBornTX. Mixed temperature-bornX derivative of Born term.
   * </p>
   *
   * @return a double
   */
  public double FBornTX() {
    return -(avagadroNumber * electronCharge * electronCharge
        / (4.0 * pi * vacumPermittivity * R * temperature * temperature))
        * (1.0 / getSolventDiElectricConstant() - 1.0);
  }

  /**
   * <p>
   * FBornDD. Second dielectric derivative of Born term.
   * </p>
   *
   * @return a double
   */
  public double FBornDD() {
    return 2.0
        * (avagadroNumber * electronCharge * electronCharge
            / (4.0 * pi * vacumPermittivity * R * temperature))
        * 1.0 / Math.pow(getSolventDiElectricConstant(), 3.0) * bornX;
  }

  /**
   * <p>
   * FBornDX. Mixed dielectric-bornX derivative of Born term.
   * </p>
   *
   * @return a double
   */
  public double FBornDX() {
    return -(avagadroNumber * electronCharge * electronCharge
        / (4.0 * pi * vacumPermittivity * R * temperature)) * 1.0
        / Math.pow(getSolventDiElectricConstant(), 2.0);
  }

  /**
   * <p>
   * FBornXX.
   * </p>
   *
   * @return a double
   */
  public double FBornXX() {
    return 0.0;
  }

  /**
   * <p>
   * Getter for the field <code>eps</code>.
   * </p>
   *
   * @return a double
   */
  public double getEps() {
    return eps;
  }

  /**
   * <p>
   * Getter for the field <code>epsIonic</code>.
   * </p>
   *
   * @return a double
   */
  public double getEpsIonic() {
    return epsIonic;
  }

  /**
   * <p>
   * Getter for the field <code>epsIonicdV</code>.
   * </p>
   *
   * @return a double
   */
  public double getEpsIonicdV() {
    return epsIonicdV;
  }

  /**
   * <p>
   * Getter for the field <code>epsdV</code>.
   * </p>
   *
   * @return a double
   */
  public double getEpsdV() {
    return epsdV;
  }

  /**
   * <p>
   * Getter for the field <code>epsdVdV</code>.
   * </p>
   *
   * @return a double
   */
  public double getEpsdVdV() {
    return epsdVdV;
  }

  /**
   * <p>
   * Getter for the field <code>solventDiElectricConstant</code>.
   * </p>
   *
   * @return a double
   */
  public double getSolventDiElectricConstant() {
    return solventDiElectricConstant;
  }

  /**
   * <p>
   * Getter for the field <code>solventDiElectricConstantdT</code>.
   * </p>
   *
   * @return a double
   */
  public double getSolventDiElectricConstantdT() {
    return solventDiElectricConstantdT;
  }

  /**
   * <p>
   * Getter for the field <code>solventDiElectricConstantdTdT</code>.
   * </p>
   *
   * @return a double
   */
  public double getSolventDiElectricConstantdTdT() {
    return solventDiElectricConstantdTdT;
  }

  /**
   * <p>
   * Getter for the field <code>alphaLR2</code>.
   * </p>
   *
   * @return a double
   */
  public double getAlphaLR2() {
    return alphaLR2;
  }

  /**
   * <p>
   * getW.
   * </p>
   *
   * @return a double
   */
  public double getW() {
    return W;
  }

  /**
   * <p>
   * getWT.
   * </p>
   *
   * @return a double
   */
  public double getWT() {
    return WT;
  }

  /**
   * <p>
   * Getter for the field <code>diElectricConstantdT</code>.
   * </p>
   *
   * @return a double
   */
  public double getDiElectricConstantdT() {
    return diElectricConstantdT;
  }

  /**
   * <p>
   * Getter for the field <code>diElectricConstantdV</code>.
   * </p>
   *
   * @return a double
   */
  public double getDiElectricConstantdV() {
    return diElectricConstantdV;
  }

  /**
   * <p>
   * getXLR.
   * </p>
   *
   * @return a double
   */
  public double getXLR() {
    return XLR;
  }

  /**
   * <p>
   * Getter for the field <code>shieldingParameter</code>.
   * </p>
   *
   * @return a double
   */
  public double getShieldingParameter() {
    return shieldingParameter;
  }

  /**
   * <p>
   * getAlphaLRT.
   * </p>
   *
   * @return a double
   */
  public double getAlphaLRT() {
    return alphaLRdT;
  }

  /**
   * <p>
   * getAlphaLRV.
   * </p>
   *
   * @return a double
   */
  public double getAlphaLRV() {
    return alphaLRdV;
  }

  /**
   * <p>
   * getDielectricT.
   * </p>
   *
   * @return a double
   */
  public double getDielectricT() {
    return diElectricConstantdT;
  }

  /**
   * <p>
   * getDielectricV.
   * </p>
   *
   * @return a double
   */
  public double getDielectricV() {
    return diElectricConstantdV;
  }

  /**
   * <p>
   * getDielectricConstant.
   * </p>
   *
   * @return a double
   */
  public double getDielectricConstant() {
    return diElectricConstant;
  }

  /**
   * <p>
   * setFurstIonicCoefficient.
   * </p>
   *
   * @param params an array of type double
   */
  public void setFurstIonicCoefficient(double[] params) {}

  /**
   * <p>
   * Getter for the field <code>epsIonicdVdV</code>.
   * </p>
   *
   * @return a double
   */
  public double getEpsIonicdVdV() {
    return epsIonicdVdV;
  }
}
