package neqsim.thermo.component;

import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhasePitzer;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.phase.PitzerElectrostaticMixing;

/**
 * Component class for the Pitzer model.
 *
 * @author Even Solbraa
 */
public class ComponentGePitzer extends ComponentGE {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Setup-time mirror of the phase's sparse neutral-interaction gate. */
  private boolean neutralPitzerInteractionsActive;

  /**
   * Updates the setup-time fast gate for neutral Pitzer interactions.
   *
   * @param active whether the owning Pitzer phase has any neutral interactions
   */
  public void setNeutralPitzerInteractionsActive(boolean active) {
    neutralPitzerInteractionsActive = active;
  }

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
    getGamma(phase, phase.getNumberOfComponents(), phase.getTemperature(), phase.getPressure(), phase.getType());
    // Pitzer's implemented solvent activity is water-specific. Other database solvents need an aqueous Henry
    // reference; hydrocarbons have no Pitzer neutral-interaction parameters and are represented as effectively
    // insoluble instead of being evaluated with the water osmotic coefficient.
    if (Math.abs(getIonicCharge()) < 0.5 && !"water".equalsIgnoreCase(getComponentName())) {
      double henryCoefficient = getEffectiveHenryCoefficient(phase);
      // Pitzer neutral activities and the database Henry constants are on the molality
      // scale, while the common phase-equilibrium kernel evaluates x_i * phi_i * P.
      // Convert m_i * gamma_i * H_i to that mole-fraction representation explicitly.
      double moleFraction = getx();
      double molalityPerMoleFraction = moleFraction > 0.0 ? getMolality(phase) / moleFraction : Double.NaN;
      if (!(molalityPerMoleFraction > 0.0) || !Double.isFinite(molalityPerMoleFraction)) {
        molalityPerMoleFraction = 1.0;
      }
      fugacityCoefficient = gamma * henryCoefficient * molalityPerMoleFraction / phase.getPressure();
      gammaRefCor = gamma;
      return fugacityCoefficient;
    }
    return super.fugcoef(phase);
  }

  /**
   * Returns the Pitzer molality-scale Henry reference.
   *
   * <p>
   * IAPWS publishes {@code kH = f/x}. Pitzer neutral activities use molality, so {@code Hm = kH * Mwater}; the existing
   * {@code m/x} factor in {@link #fugcoef(PhaseInterface)} maps this back to the common mole-fraction fugacity kernel.
   * The constant conversion does not change the logarithmic temperature derivative.
   * </p>
   *
   * @param phase aqueous Pitzer phase
   * @return effective molality-scale Henry coefficient in bar kg/mol
   */
  @Override
  protected double getEffectiveHenryCoefficient(PhaseInterface phase) {
    double coefficient = super.getEffectiveHenryCoefficient(phase);
    if (phase.hasComponent("water") && IapwsHenryLaw.isSupportedSpecies(getComponentName())) {
      return coefficient * IapwsHenryLaw.WATER_MOLAR_MASS_KG_PER_MOL;
    }
    return coefficient;
  }

  /** {@inheritDoc} */
  @Override
  protected boolean isHenryCoefficientCapped(double henryCoefficient) {
    return super.isHenryCoefficientCapped(henryCoefficient) || isHydrocarbon() || isIsTBPfraction();
  }

  /** {@inheritDoc} */
  @Override
  public boolean isHydrocarbon() {
    return super.isHydrocarbon() || hasHydrocarbonFormula();
  }

  /**
   * Check the database molecular formula for a pure hydrocarbon.
   *
   * <p>
   * Some GE initialization paths classify a normal database component as {@code normal} instead of {@code HC}. The
   * formula check keeps Pitzer aqueous-reference and parameter-topology decisions independent of that initialization
   * detail.
   * </p>
   *
   * @return {@code true} when the formula contains carbon and hydrogen only
   */
  private boolean hasHydrocarbonFormula() {
    String formula = getFormulae();
    if (formula == null || formula.isEmpty()) {
      return false;
    }
    boolean carbon = false;
    boolean hydrogen = false;
    for (int index = 0; index < formula.length(); index++) {
      char character = formula.charAt(index);
      if (character == 'C') {
        carbon = true;
      } else if (character == 'H') {
        hydrogen = true;
      } else if (!Character.isDigit(character)) {
        return false;
      }
    }
    return carbon && hydrogen;
  }

  /** {@inheritDoc} */
  @Override
  public double getGamma(PhaseInterface phase, int numberOfComponents, double temperature, double pressure,
      PhaseType pt, double[][] HValpha, double[][] HVgij, double[][] intparam, String[][] mixRule) {
    return getGamma(phase, numberOfComponents, temperature, pressure, pt);
  }

  /**
   * Calculate activity coefficient using the Pitzer model.
   *
   * <p>
   * For ions (charge != 0): computes the single-ion activity coefficient from the Pitzer Debye-Huckel term and binary
   * cation-anion interaction parameters. For the solvent (water, referenceStateType = "solvent"): computes the activity
   * coefficient from the Pitzer osmotic coefficient using the Harvie and Weare (1980) mixed-electrolyte framework. For
   * other neutral species (dissolved gases): applies the explicitly configured lambda, zeta, mu, and eta families, with
   * an exact gamma = 1.0 legacy fallback when that sparse layer is empty.
   * </p>
   *
   * @param phase phase object
   * @param numberOfComponents number of components in phase
   * @param temperature temperature in Kelvin
   * @param pressure pressure in bara
   * @param pt phase type
   * @return activity coefficient (dimensionless)
   */
  public double getGamma(PhaseInterface phase, int numberOfComponents, double temperature, double pressure,
      PhaseType pt) {
    double charge = getIonicCharge();
    PhasePitzer pitz = (PhasePitzer) phase;

    // Solvent (water): compute gamma from Pitzer osmotic coefficient
    if (Math.abs(charge) < 0.5 && "water".equalsIgnoreCase(getComponentName())
        && "solvent".equals(referenceStateType)) {
      return getWaterGamma(phase, numberOfComponents, temperature);
    }

    // Non-ionic, non-solvent species use the sparse neutral Pitzer layer when configured.
    if (Math.abs(charge) < 0.5) {
      lngamma = neutralPitzerInteractionsActive
          ? pitz.getNeutralPitzerLogGammaContribution(componentNumber, temperature)
          : 0.0;
      gamma = Math.exp(lngamma);
      return gamma;
    }

    // --- Ion activity coefficient (extended Pitzer formulation) ---
    // Pitzer (1991) Eq. 8-3-2: ln(gamma_M) = z_M^2 * F + sum_a m_a*(2*B_Ma + Z*C_Ma)
    // F = -Aphi*[sqrtI/(1+b*sqrtI) + (2/b)*ln(1+b*sqrtI)] + sum_c sum_a m_c*m_a*B'_ca
    // Handles PHREEQC's charge-dependent alpha defaults, including beta2 for both
    // 2-1 CaCl2 and 2-2 electrolytes.
    double I = pitz.getIonicStrength();
    double sqrtI = Math.sqrt(I);
    // debyeHuckelAphi() returns Agamma = 3*Aphi; convert to Aphi for Pitzer formula
    double Aphi = debyeHuckelAphi(temperature) / 3.0;
    double b = 1.2;
    // Pitzer DH function: F = -Aphi * [sqrtI/(1+b*sqrtI) + (2/b)*ln(1+b*sqrtI)]
    double fDH = -Aphi * (sqrtI / (1.0 + b * sqrtI) + (2.0 / b) * Math.log(1.0 + b * sqrtI));

    // Z = sum of m_i * |z_i| over all ions (needed for C term)
    double Zsum = 0.0;
    for (int j = 0; j < numberOfComponents; j++) {
      double chargej = phase.getComponent(j).getIonicCharge();
      if (Math.abs(chargej) > 0.5) {
        Zsum += phase.getComponent(j).getMolality(phase) * Math.abs(chargej);
      }
    }

    // B' contribution to F: sum_c sum_a m_c * m_a * B'_ca
    double fBprime = 0.0;
    double sum = 0.0;
    boolean phreeqcCommonIonTerms = pitz.isPhreeqcCommonIonTermsActive();
    double phreeqcCommonIonContribution = phreeqcCommonIonTerms
        ? phreeqcCommonIonContribution(phase, pitz, numberOfComponents, temperature, I, sqrtI, charge)
        : 0.0;
    for (int j = 0; j < numberOfComponents; j++) {
      if (j == componentNumber) {
        continue;
      }
      double chargej = phase.getComponent(j).getIonicCharge();
      if (chargej * charge >= 0) {
        continue;
      }
      double m_j = phase.getComponent(j).getMolality(phase);
      double beta0 = pitz.getBeta0ij(componentNumber, j, temperature);
      double beta1 = pitz.getBeta1ij(componentNumber, j, temperature);
      double CphiVal = pitz.getCphiij(componentNumber, j, temperature);

      double alpha1 = Math.abs(charge) >= 1.5 && Math.abs(chargej) >= 1.5 ? 1.4 : 2.0;
      boolean isTwoTwo = Math.abs(charge) >= 1.5 && Math.abs(chargej) >= 1.5;

      double x1 = alpha1 * sqrtI;
      double g1 = 0.0;
      double gp1 = 0.0;
      if (x1 > 1e-12) {
        g1 = 2.0 * (1.0 - (1.0 + x1) * Math.exp(-x1)) / (x1 * x1);
        gp1 = -2.0 * (1.0 - (1.0 + x1 + x1 * x1 / 2.0) * Math.exp(-x1)) / (x1 * x1);
      }
      double Bval = beta0 + beta1 * g1;
      // B'(I) = dB/dI for the F-term (Pitzer 1991 Eq. 8-2-8)
      double Bprime = (I > 1e-12) ? beta1 * gp1 / I : 0.0;

      if (isTwoTwo || pitz.isNonTwoTwoBeta2Active()) {
        double beta2val = pitz.getBeta2ij(componentNumber, j, temperature);
        if (Math.abs(beta2val) > 1e-20) {
          double x2 = pitz.getPitzerAlpha2(componentNumber, j) * sqrtI;
          double g2 = 0.0;
          double gp2 = 0.0;
          if (x2 > 1e-12) {
            g2 = 2.0 * (1.0 - (1.0 + x2) * Math.exp(-x2)) / (x2 * x2);
            gp2 = -2.0 * (1.0 - (1.0 + x2 + x2 * x2 / 2.0) * Math.exp(-x2)) / (x2 * x2);
          }
          Bval += beta2val * g2;
          if (I > 1e-12) {
            Bprime += beta2val * gp2 / I;
          }
        }
      }

      // C_ca = Cphi_ca / (2 * sqrt(|z_c * z_a|)) per Pitzer 1991 Eq. 8-2-10
      double Cval = CphiVal / (2.0 * Math.sqrt(Math.abs(charge * chargej)));
      // Binary contribution: m_a * (2*B + Z*C)
      sum += m_j * (2.0 * Bval + Zsum * Cval);

      // B' contribution to F: accumulate m_i * m_j * B'_ij
      // (only count each pair once — this ion paired with opposite-sign ion j)
      double m_this = getMolality(phase);
      if (!phreeqcCommonIonTerms) {
        fBprime += m_this * m_j * Bprime;
      }
    }

    // Theta and psi mixing terms for same-sign ion interactions
    double fEthetaPrime = 0.0;
    double[] electrostaticMixing = null;
    boolean hasElectrostaticMixing = pitz.hasUnequalChargeSameSignPair();
    if (hasElectrostaticMixing) {
      for (int i = 0; i < numberOfComponents; i++) {
        double chargei = phase.getComponent(i).getIonicCharge();
        if (Math.abs(chargei) < 0.5) {
          continue;
        }
        double m_i = phase.getComponent(i).getMolality(phase);
        for (int j = i + 1; j < numberOfComponents; j++) {
          double chargej = phase.getComponent(j).getIonicCharge();
          if (chargei * chargej <= 0 || Math.abs(chargei - chargej) < 1.0e-12) {
            continue;
          }
          if (electrostaticMixing == null) {
            electrostaticMixing = new double[2];
          }
          PitzerElectrostaticMixing.calculate(chargei, chargej, I, Aphi, electrostaticMixing);
          fEthetaPrime += m_i * phase.getComponent(j).getMolality(phase) * electrostaticMixing[1];
        }
      }
    }

    for (int j = 0; j < numberOfComponents; j++) {
      if (j == componentNumber) {
        continue;
      }
      double chargej = phase.getComponent(j).getIonicCharge();
      // Same sign as this ion: cation-cation or anion-anion
      if (Math.abs(chargej) < 0.5 || chargej * charge <= 0) {
        continue;
      }
      double m_j = phase.getComponent(j).getMolality(phase);
      double thetaij = pitz.getThetaij(componentNumber, j, temperature);
      double eTheta = 0.0;
      if (hasElectrostaticMixing && Math.abs(charge - chargej) >= 1.0e-12) {
        if (electrostaticMixing == null) {
          electrostaticMixing = new double[2];
        }
        PitzerElectrostaticMixing.calculate(charge, chargej, I, Aphi, electrostaticMixing);
        eTheta = electrostaticMixing[0];
      }
      sum += m_j * 2.0 * (thetaij + eTheta);

      // Psi ternary terms: sum over opposite-sign ions
      for (int k = 0; k < numberOfComponents; k++) {
        double chargek = phase.getComponent(k).getIonicCharge();
        if (chargek * charge >= 0 || Math.abs(chargek) < 0.5) {
          continue;
        }
        double m_k = phase.getComponent(k).getMolality(phase);
        double psiijk = pitz.getPsiijk(componentNumber, j, k, temperature);
        sum += m_j * m_k * psiijk;
      }
    }

    // ln(gamma_M) = z_M^2 * F + binary/mixing terms
    double F = fDH + fBprime + fEthetaPrime;
    lngamma = charge * charge * F + sum + phreeqcCommonIonContribution;
    if (neutralPitzerInteractionsActive) {
      lngamma += pitz.getNeutralPitzerLogGammaContribution(componentNumber, temperature);
    }
    gamma = Math.exp(lngamma);
    return gamma;
  }

  /** Calculates PHREEQC's common B-prime and C0 contributions to one ion's ln(gamma). */
  private static double phreeqcCommonIonContribution(PhaseInterface phase, PhasePitzer pitz, int numberOfComponents,
      double temperature, double ionicStrength, double squareRootIonicStrength, double targetCharge) {
    double fBprime = 0.0;
    double cphiCommonSum = 0.0;
    for (int cation = 0; cation < numberOfComponents; cation++) {
      double cationCharge = phase.getComponent(cation).getIonicCharge();
      if (cationCharge <= 0.0) {
        continue;
      }
      double cationMolality = phase.getComponent(cation).getMolality(phase);
      for (int anion = 0; anion < numberOfComponents; anion++) {
        double anionCharge = phase.getComponent(anion).getIonicCharge();
        if (anionCharge >= 0.0) {
          continue;
        }
        double anionMolality = phase.getComponent(anion).getMolality(phase);
        fBprime += cationMolality * anionMolality * phreeqcBinaryBprime(pitz, cation, anion, temperature, ionicStrength,
            squareRootIonicStrength, cationCharge, anionCharge);
        cphiCommonSum += cationMolality * anionMolality * pitz.getCphiij(cation, anion, temperature)
            / (2.0 * Math.sqrt(Math.abs(cationCharge * anionCharge)));
      }
    }
    return targetCharge * targetCharge * fBprime + Math.abs(targetCharge) * cphiCommonSum;
  }

  /** Calculates one binary pair's contribution to PHREEQC's common B-prime term. */
  private static double phreeqcBinaryBprime(PhasePitzer phase, int first, int second, double temperature,
      double ionicStrength, double squareRootIonicStrength, double firstCharge, double secondCharge) {
    double alpha1 = Math.abs(firstCharge) >= 1.5 && Math.abs(secondCharge) >= 1.5 ? 1.4 : 2.0;
    double x1 = alpha1 * squareRootIonicStrength;
    double derivative = 0.0;
    if (x1 > 1.0e-12 && ionicStrength > 1.0e-12) {
      double gp1 = -2.0 * (1.0 - (1.0 + x1 + x1 * x1 / 2.0) * Math.exp(-x1)) / (x1 * x1);
      derivative = phase.getBeta1ij(first, second, temperature) * gp1 / ionicStrength;
    }
    double beta2Value = phase.getBeta2ij(first, second, temperature);
    double x2 = phase.getPitzerAlpha2(first, second) * squareRootIonicStrength;
    if (Math.abs(beta2Value) > 1.0e-20 && x2 > 1.0e-12 && ionicStrength > 1.0e-12) {
      double gp2 = -2.0 * (1.0 - (1.0 + x2 + x2 * x2 / 2.0) * Math.exp(-x2)) / (x2 * x2);
      derivative += beta2Value * gp2 / ionicStrength;
    }
    return derivative;
  }

  /**
   * Compute the water (solvent) activity coefficient from the Pitzer osmotic coefficient.
   *
   * <p>
   * Uses the Harvie and Weare (1980) mixed-electrolyte framework for the osmotic coefficient:
   * </p>
   *
   * <pre>
   * phi - 1 = (2 / sum_m) * [-Aphi * I^(3/2) / (1 + b*sqrt(I))
   *            + sum_c sum_a m_c * m_a * (Bphi_ca + Z * C_ca)]
   * </pre>
   *
   * <p>
   * Water activity: ln(a_w) = -phi * M_w * sum_m / 1000
   * </p>
   *
   * <p>
   * Activity coefficient (Raoult convention): gamma_w = a_w / x_w
   * </p>
   *
   * @param phase the Pitzer phase
   * @param numberOfComponents number of components
   * @param TK temperature in Kelvin
   * @return water activity coefficient (mole fraction basis, Raoult convention)
   */
  private double getWaterGamma(PhaseInterface phase, int numberOfComponents, double TK) {
    PhasePitzer pitz = (PhasePitzer) phase;
    double I = pitz.getIonicStrength();
    double sqrtI = Math.sqrt(I);
    // debyeHuckelAphi() returns Agamma = 3*Aphi; convert to Aphi
    double Aphi = debyeHuckelAphi(TK) / 3.0;
    double b = 1.2;

    // Preserve the legacy ion-only denominator until a neutral Pitzer dataset is explicitly active.
    double sumMolalities = 0.0;
    for (int k = 0; k < numberOfComponents; k++) {
      boolean isWater = "water".equalsIgnoreCase(phase.getComponent(k).getComponentName());
      if (phase.getComponent(k).getIonicCharge() != 0 || (neutralPitzerInteractionsActive && !isWater)) {
        sumMolalities += phase.getComponent(k).getMolality(phase);
      }
    }

    if (sumMolalities < 1e-10) {
      gamma = 1.0;
      lngamma = 0.0;
      return gamma;
    }

    // Debye-Hückel contribution: f^phi = -Aphi * I^(3/2) / (1 + b*sqrt(I))
    double fPhi = -Aphi * Math.pow(I, 1.5) / (1.0 + b * sqrtI);

    // Z = sum |z_i| * m_i (over all ions)
    double Z = 0.0;
    for (int k = 0; k < numberOfComponents; k++) {
      Z += Math.abs(phase.getComponent(k).getIonicCharge()) * phase.getComponent(k).getMolality(phase);
    }

    // Binary cation-anion interaction sum
    double binarySum = 0.0;
    for (int ic = 0; ic < numberOfComponents; ic++) {
      double zc = phase.getComponent(ic).getIonicCharge();
      if (zc <= 0) {
        continue;
      }
      double mc = phase.getComponent(ic).getMolality(phase);

      for (int ia = 0; ia < numberOfComponents; ia++) {
        double za = phase.getComponent(ia).getIonicCharge();
        if (za >= 0) {
          continue;
        }
        double ma = phase.getComponent(ia).getMolality(phase);

        double beta0 = pitz.getBeta0ij(ic, ia, TK);
        double beta1 = pitz.getBeta1ij(ic, ia, TK);
        double Cphi = pitz.getCphiij(ic, ia, TK);

        double alpha1 = Math.abs(zc) >= 1.5 && Math.abs(za) >= 1.5 ? 1.4 : 2.0;

        // B^phi_ca = beta0 + beta1 * exp(-alpha1 * sqrt(I))
        double BphiCA = beta0 + beta1 * Math.exp(-alpha1 * sqrtI);

        boolean isTwoTwo = Math.abs(zc) >= 1.5 && Math.abs(za) >= 1.5;
        if (isTwoTwo || pitz.isNonTwoTwoBeta2Active()) {
          double beta2val = pitz.getBeta2ij(ic, ia, TK);
          if (Math.abs(beta2val) > 1e-20) {
            BphiCA += beta2val * Math.exp(-pitz.getPitzerAlpha2(ic, ia) * sqrtI);
          }
        }

        // C_ca = Cphi / (2 * sqrt(|zc * za|))
        double absZcZa = Math.abs(zc * za);
        double CCA = (absZcZa > 0) ? Cphi / (2.0 * Math.sqrt(absZcZa)) : 0.0;

        binarySum += mc * ma * (BphiCA + Z * CCA);
      }
    }

    // Theta and psi contributions to osmotic coefficient
    double thetaPsiSum = 0.0;
    double[] electrostaticMixing = null;
    boolean hasElectrostaticMixing = pitz.hasUnequalChargeSameSignPair();
    // Cation-cation theta + psi
    for (int ic1 = 0; ic1 < numberOfComponents; ic1++) {
      double zc1 = phase.getComponent(ic1).getIonicCharge();
      if (zc1 <= 0) {
        continue;
      }
      double mc1 = phase.getComponent(ic1).getMolality(phase);
      for (int ic2 = ic1 + 1; ic2 < numberOfComponents; ic2++) {
        double zc2 = phase.getComponent(ic2).getIonicCharge();
        if (zc2 <= 0) {
          continue;
        }
        double mc2 = phase.getComponent(ic2).getMolality(phase);
        double thetaCC = pitz.getThetaij(ic1, ic2, TK);
        double electrostaticPhi = 0.0;
        if (hasElectrostaticMixing && Math.abs(zc1 - zc2) >= 1.0e-12) {
          if (electrostaticMixing == null) {
            electrostaticMixing = new double[2];
          }
          PitzerElectrostaticMixing.calculate(zc1, zc2, I, Aphi, electrostaticMixing);
          electrostaticPhi = electrostaticMixing[0] + I * electrostaticMixing[1];
        }
        thetaPsiSum += mc1 * mc2 * (thetaCC + electrostaticPhi);
        // Psi cation-cation-anion
        for (int ia = 0; ia < numberOfComponents; ia++) {
          double za = phase.getComponent(ia).getIonicCharge();
          if (za >= 0) {
            continue;
          }
          double ma = phase.getComponent(ia).getMolality(phase);
          thetaPsiSum += mc1 * mc2 * ma * pitz.getPsiijk(ic1, ic2, ia, TK);
        }
      }
    }
    // Anion-anion theta + psi
    for (int ia1 = 0; ia1 < numberOfComponents; ia1++) {
      double za1 = phase.getComponent(ia1).getIonicCharge();
      if (za1 >= 0) {
        continue;
      }
      double ma1 = phase.getComponent(ia1).getMolality(phase);
      for (int ia2 = ia1 + 1; ia2 < numberOfComponents; ia2++) {
        double za2 = phase.getComponent(ia2).getIonicCharge();
        if (za2 >= 0) {
          continue;
        }
        double ma2 = phase.getComponent(ia2).getMolality(phase);
        double thetaAA = pitz.getThetaij(ia1, ia2, TK);
        double electrostaticPhi = 0.0;
        if (hasElectrostaticMixing && Math.abs(za1 - za2) >= 1.0e-12) {
          if (electrostaticMixing == null) {
            electrostaticMixing = new double[2];
          }
          PitzerElectrostaticMixing.calculate(za1, za2, I, Aphi, electrostaticMixing);
          electrostaticPhi = electrostaticMixing[0] + I * electrostaticMixing[1];
        }
        thetaPsiSum += ma1 * ma2 * (thetaAA + electrostaticPhi);
        // Psi anion-anion-cation
        for (int ic = 0; ic < numberOfComponents; ic++) {
          double zc = phase.getComponent(ic).getIonicCharge();
          if (zc <= 0) {
            continue;
          }
          double mc = phase.getComponent(ic).getMolality(phase);
          thetaPsiSum += ma1 * ma2 * mc * pitz.getPsiijk(ia1, ia2, ic, TK);
        }
      }
    }

    double neutralSum = neutralPitzerInteractionsActive ? pitz.getNeutralPitzerOsmoticContribution(TK) : 0.0;
    // Osmotic coefficient: phi - 1 = (2/sumM) * interaction sum
    double phi = 1.0 + (2.0 / sumMolalities) * (fPhi + binarySum + thetaPsiSum + neutralSum);

    // Water activity: ln(a_w) = -phi * M_w * sumM / 1000
    double Mw = 18.015; // molar mass of water in g/mol
    double lnaw = -phi * Mw * sumMolalities / 1000.0;
    double aw = Math.exp(lnaw);

    // Convert to mole fraction activity coefficient: gamma_w = a_w / x_w
    double xw = getx();
    if (xw < 1e-10) {
      gamma = 1.0;
      lngamma = 0.0;
    } else {
      gamma = aw / xw;
      lngamma = Math.log(gamma);
    }

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
   * Uses the Bradley-Pitzer (1979) correlation for the osmotic coefficient Debye-Hückel parameter. The Aphi value is in
   * ln-based units (Aphi = Agamma/3). Water density uses the IAPWS simplified equation valid at pressures typical for
   * oilfield brine (liquid state).
   * </p>
   *
   * @param TK temperature in Kelvin
   * @return Aphi in ln-based units (dimensionless)
   */
  private double debyeHuckelAphi(double TK) {
    // Water density (kg/m³) — Kell (1975) polynomial valid 0-150 °C at ~1 atm
    // with pressure boost for liquid water above 100°C
    double TC = TK - 273.15;
    double rho = 999.83 + 5.0948e-2 * TC - 7.5722e-3 * TC * TC + 3.8907e-5 * TC * TC * TC - 1.2e-7 * TC * TC * TC * TC;
    // Above 100°C, the Kell formula under-predicts liquid density under pressure.
    // Apply approximate correction: in pressurized systems, liquid density stays higher
    // than atmospheric-boiling values. Use linear interpolation toward IAPWS values.
    if (TC > 100.0) {
      // IAPWS approximate saturated-liquid density at elevated T (100-300°C):
      // rho_sat ~ 958 - 1.08*(T-100) - 0.0028*(T-100)^2
      double dT = TC - 100.0;
      rho = 958.0 - 1.08 * dT - 0.0028 * dT * dT;
    }
    if (rho < 700.0) {
      rho = 700.0;
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
