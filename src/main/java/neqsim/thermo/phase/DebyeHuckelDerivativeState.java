package neqsim.thermo.phase;

import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.component.ComponentInterface;

/**
 * Immutable derivative state for the extended Debye-Huckel Helmholtz contribution used by
 * {@link PhaseElectrolyteCPAMM}.
 *
 * <p>
 * All derivatives are generated from the same scalar expression at fixed total volume, temperature and component
 * amounts. Internal volume derivatives use cubic metres; callers convert to NeqSim's pressure-scaled volume coordinate
 * at the phase boundary.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
final class DebyeHuckelDerivativeState {
  /** Vacuum permittivity [F/m]. */
  private static final double VACUUM_PERMITTIVITY = 8.854187817e-12;
  /** Elementary charge [C]. */
  private static final double ELECTRON_CHARGE = 1.60217662e-19;
  /** Avogadro constant [1/mol]. */
  private static final double AVOGADRO = ThermodynamicConstantsInterface.avagadroNumber;
  /** Boltzmann constant [J/K]. */
  private static final double BOLTZMANN = ThermodynamicConstantsInterface.R / AVOGADRO;
  /** Helmholtz prefactor, {@code 1/(12*pi*N_A)}. */
  private static final double HELMHOLTZ_FACTOR = 1.0 / (12.0 * Math.PI * AVOGADRO);

  /** Scalar contribution. */
  private final double value;
  /** First temperature derivative. */
  private final double dT;
  /** Second temperature derivative. */
  private final double dTT;
  /** First SI-volume derivative. */
  private final double dV;
  /** Second SI-volume derivative. */
  private final double dVV;
  /** Third SI-volume derivative. */
  private final double dVVV;
  /** Mixed temperature/SI-volume derivative. */
  private final double dTV;
  /** Component amount derivatives at fixed temperature and total volume. */
  private final double[] dN;
  /** Component amount-temperature derivatives. */
  private final double[] dNT;
  /** Component amount-SI-volume derivatives. */
  private final double[] dNV;
  /** Component amount Hessian. */
  private final double[][] dNN;

  /**
   * Builds the complete derivative state for a phase.
   *
   * @param phase initialized electrolyte phase
   */
  DebyeHuckelDerivativeState(PhaseElectrolyteCPAMM phase) {
    int count = phase.getNumberOfComponents();
    dN = new double[count];
    dNT = new double[count];
    dNV = new double[count];
    dNN = new double[count][count];

    double temperature = phase.getTemperature();
    double volume = phase.getMolarVolume() * phase.getNumberOfMolesInPhase() * 1e-5;
    PrimitiveState primitive = new PrimitiveState(phase, temperature, volume);
    if (!primitive.isActive()) {
      value = 0.0;
      dT = 0.0;
      dTT = 0.0;
      dV = 0.0;
      dVV = 0.0;
      dVVV = 0.0;
      dTV = 0.0;
      return;
    }

    Kernel kernel = new Kernel(primitive.kappa, primitive.meanDiameter);
    value = -HELMHOLTZ_FACTOR * volume * kernel.value;

    double kappaT = primitive.kappa * primitive.logKappaT;
    double kappaTT = primitive.kappa * (primitive.logKappaTT + primitive.logKappaT * primitive.logKappaT);
    double kernelT = kernel.kappa * kappaT;
    double kernelTT = kernel.kappaKappa * kappaT * kappaT + kernel.kappa * kappaTT;
    dT = -HELMHOLTZ_FACTOR * volume * kernelT;
    dTT = -HELMHOLTZ_FACTOR * volume * kernelTT;

    double kappaV = primitive.kappa * primitive.logKappaV;
    double kappaVV = primitive.kappa * (primitive.logKappaVV + primitive.logKappaV * primitive.logKappaV);
    double kappaVVV = primitive.kappa * (primitive.logKappaVVV + 3.0 * primitive.logKappaV * primitive.logKappaVV
        + primitive.logKappaV * primitive.logKappaV * primitive.logKappaV);
    double kernelV = kernel.kappa * kappaV;
    double kernelVV = kernel.kappaKappa * kappaV * kappaV + kernel.kappa * kappaVV;
    double kernelVVV = kernel.kappaKappaKappa * kappaV * kappaV * kappaV + 3.0 * kernel.kappaKappa * kappaV * kappaVV
        + kernel.kappa * kappaVVV;
    dV = -HELMHOLTZ_FACTOR * (kernel.value + volume * kernelV);
    dVV = -HELMHOLTZ_FACTOR * (2.0 * kernelV + volume * kernelVV);
    dVVV = -HELMHOLTZ_FACTOR * (3.0 * kernelVV + volume * kernelVVV);

    double kappaTV = primitive.kappa * (primitive.logKappaT * primitive.logKappaV + primitive.logKappaTV);
    double kernelTV = kernel.kappaKappa * kappaT * kappaV + kernel.kappa * kappaTV;
    dTV = -HELMHOLTZ_FACTOR * (kernelT + volume * kernelTV);

    for (int i = 0; i < count; i++) {
      double kappaN = primitive.kappa * primitive.logKappaN[i];
      double kernelN = kernel.kappa * kappaN + kernel.diameter * primitive.meanDiameterN[i];
      dN[i] = -HELMHOLTZ_FACTOR * volume * kernelN;

      double kappaNT = primitive.kappa * (primitive.logKappaN[i] * primitive.logKappaT + primitive.logKappaNT[i]);
      double kernelNT = kernel.kappaKappa * kappaN * kappaT + kernel.kappa * kappaNT
          + kernel.kappaDiameter * primitive.meanDiameterN[i] * kappaT;
      dNT[i] = -HELMHOLTZ_FACTOR * volume * kernelNT;

      double kappaNV = primitive.kappa * (primitive.logKappaN[i] * primitive.logKappaV + primitive.logKappaNV[i]);
      double kernelNV = kernel.kappaKappa * kappaN * kappaV + kernel.kappa * kappaNV
          + kernel.kappaDiameter * primitive.meanDiameterN[i] * kappaV;
      dNV[i] = -HELMHOLTZ_FACTOR * (kernelN + volume * kernelNV);

      for (int j = 0; j < count; j++) {
        double kappaNj = primitive.kappa * primitive.logKappaN[j];
        double kappaNN = primitive.kappa
            * (primitive.logKappaN[i] * primitive.logKappaN[j] + primitive.logKappaNN[i][j]);
        double kernelNN = kernel.kappaKappa * kappaN * kappaNj + kernel.kappa * kappaNN
            + kernel.kappaDiameter * (kappaN * primitive.meanDiameterN[j] + primitive.meanDiameterN[i] * kappaNj)
            + kernel.diameterDiameter * primitive.meanDiameterN[i] * primitive.meanDiameterN[j]
            + kernel.diameter * primitive.meanDiameterNN[i][j];
        dNN[i][j] = -HELMHOLTZ_FACTOR * volume * kernelNN;
      }
    }
  }

  /** @return scalar extended Debye-Huckel contribution */
  double getValue() {
    return value;
  }

  /** @return first temperature derivative */
  double getDT() {
    return dT;
  }

  /** @return second temperature derivative */
  double getDTT() {
    return dTT;
  }

  /** @return first SI-volume derivative */
  double getDV() {
    return dV;
  }

  /** @return second SI-volume derivative */
  double getDVV() {
    return dVV;
  }

  /** @return third SI-volume derivative */
  double getDVVV() {
    return dVVV;
  }

  /** @return mixed temperature/SI-volume derivative */
  double getDTV() {
    return dTV;
  }

  /**
   * Gets a component amount derivative.
   *
   * @param component component index
   * @return derivative at fixed temperature and total volume
   */
  double getDN(int component) {
    return dN[component];
  }

  /**
   * Gets a component amount-temperature derivative.
   *
   * @param component component index
   * @return mixed derivative
   */
  double getDNT(int component) {
    return dNT[component];
  }

  /**
   * Gets a component amount-SI-volume derivative.
   *
   * @param component component index
   * @return mixed derivative
   */
  double getDNV(int component) {
    return dNV[component];
  }

  /**
   * Gets a component amount Hessian element.
   *
   * @param first first component index
   * @param second second component index
   * @return mixed amount derivative
   */
  double getDNN(int first, int second) {
    return dNN[first][second];
  }

  /**
   * Primitive electrolyte state and derivatives used by the Helmholtz kernel.
   *
   * @author Even Solbraa
   * @version 1.0
   */
  private static final class PrimitiveState {
    /** Debye screening parameter. */
    private final double kappa;
    /** Mean ion diameter. */
    private final double meanDiameter;
    /** First mean-diameter derivatives. */
    private final double[] meanDiameterN;
    /** Second mean-diameter derivatives. */
    private final double[][] meanDiameterNN;
    /** Log-kappa temperature derivative. */
    private final double logKappaT;
    /** Log-kappa second temperature derivative. */
    private final double logKappaTT;
    /** Log-kappa SI-volume derivative. */
    private final double logKappaV;
    /** Log-kappa second SI-volume derivative. */
    private final double logKappaVV;
    /** Log-kappa third SI-volume derivative. */
    private final double logKappaVVV;
    /** Log-kappa temperature-volume derivative. */
    private final double logKappaTV;
    /** Log-kappa component derivatives. */
    private final double[] logKappaN;
    /** Log-kappa component-temperature derivatives. */
    private final double[] logKappaNT;
    /** Log-kappa component-volume derivatives. */
    private final double[] logKappaNV;
    /** Log-kappa component Hessian. */
    private final double[][] logKappaNN;

    /**
     * Constructs primitive state derivatives.
     *
     * @param phase electrolyte phase
     * @param temperature temperature [K]
     * @param volume total SI volume [m3]
     */
    private PrimitiveState(PhaseElectrolyteCPAMM phase, double temperature, double volume) {
      int count = phase.getNumberOfComponents();
      meanDiameterN = new double[count];
      meanDiameterNN = new double[count][count];
      logKappaN = new double[count];
      logKappaNT = new double[count];
      logKappaNV = new double[count];
      logKappaNN = new double[count][count];

      double ionicStrength = 0.0;
      double diameterMoment = 0.0;
      double packingMoment = 0.0;
      double[] chargeSquared = new double[count];
      double[] diameterWeightedCharge = new double[count];
      double[] packingCoefficient = new double[count];
      for (int i = 0; i < count; i++) {
        ComponentInterface component = phase.getComponent(i);
        double charge = component.getIonicCharge();
        double sigma = component.getLennardJonesMolecularDiameter() * 1e-10;
        double amount = component.getNumberOfMolesInPhase();
        chargeSquared[i] = charge * charge;
        diameterWeightedCharge[i] = chargeSquared[i] * sigma;
        if (charge != 0.0) {
          packingCoefficient[i] = AVOGADRO * Math.PI / 6.0 * sigma * sigma * sigma;
        }
        ionicStrength += amount * chargeSquared[i];
        diameterMoment += amount * diameterWeightedCharge[i];
        packingMoment += amount * packingCoefficient[i];
      }

      if (ionicStrength < 1e-30 || volume < 1e-30 || temperature <= 0.0) {
        kappa = 0.0;
        meanDiameter = 0.0;
        logKappaT = 0.0;
        logKappaTT = 0.0;
        logKappaV = 0.0;
        logKappaVV = 0.0;
        logKappaVVV = 0.0;
        logKappaTV = 0.0;
        return;
      }

      meanDiameter = diameterMoment / ionicStrength;
      for (int i = 0; i < count; i++) {
        meanDiameterN[i] = (diameterWeightedCharge[i] - meanDiameter * chargeSquared[i]) / ionicStrength;
      }
      for (int i = 0; i < count; i++) {
        for (int j = 0; j < count; j++) {
          meanDiameterNN[i][j] = -(chargeSquared[i] * meanDiameterN[j] + chargeSquared[j] * meanDiameterN[i])
              / ionicStrength;
        }
      }

      SolventState solvent = new SolventState(phase, temperature);
      double eta = packingMoment / volume;
      double denominator = 1.0 + eta / 2.0;
      double dielectricFactor = (1.0 - eta) / denominator;
      double dielectricFactorEta = -1.5 / (denominator * denominator);
      double dielectricFactorEtaEta = 1.5 / (denominator * denominator * denominator);
      double mixturePermittivity = solvent.value * dielectricFactor;
      if (!(mixturePermittivity > 0.0) || !Double.isFinite(mixturePermittivity)) {
        kappa = 0.0;
        logKappaT = 0.0;
        logKappaTT = 0.0;
        logKappaV = 0.0;
        logKappaVV = 0.0;
        logKappaVVV = 0.0;
        logKappaTV = 0.0;
        return;
      }

      double kappaSquared = ELECTRON_CHARGE * ELECTRON_CHARGE * AVOGADRO * ionicStrength
          / (VACUUM_PERMITTIVITY * mixturePermittivity * BOLTZMANN * temperature * volume);
      kappa = Math.sqrt(kappaSquared);

      double dielectricT = solvent.dT * dielectricFactor;
      double dielectricTT = solvent.dTT * dielectricFactor;
      logKappaT = -0.5 * (1.0 / temperature + dielectricT / mixturePermittivity);
      logKappaTT = 0.5 * (1.0 / (temperature * temperature) - dielectricTT / mixturePermittivity
          + dielectricT * dielectricT / (mixturePermittivity * mixturePermittivity));

      double d = (1.0 - eta) * (2.0 + eta);
      double q = 3.0 * eta / d;
      double qEta = 3.0 * (2.0 + eta * eta) / (d * d);
      double qEtaEta = 6.0 * (2.0 + 6.0 * eta + eta * eta * eta) / (d * d * d);
      logKappaV = -(1.0 + q) / (2.0 * volume);
      logKappaVV = (1.0 + q + eta * qEta) / (2.0 * volume * volume);
      logKappaVVV = -(2.0 + 2.0 * q + 4.0 * eta * qEta + eta * eta * qEtaEta) / (2.0 * volume * volume * volume);
      logKappaTV = 0.0;

      double etaV = -eta / volume;
      double dielectricV = solvent.value * dielectricFactorEta * etaV;
      for (int i = 0; i < count; i++) {
        double etaN = packingCoefficient[i] / volume;
        double etaNV = -etaN / volume;
        double dielectricN = solvent.dN[i] * dielectricFactor + solvent.value * dielectricFactorEta * etaN;
        double dielectricNT = solvent.dNT[i] * dielectricFactor + solvent.dT * dielectricFactorEta * etaN;
        double dielectricNV = solvent.dN[i] * dielectricFactorEta * etaV
            + solvent.value * (dielectricFactorEtaEta * etaN * etaV + dielectricFactorEta * etaNV);
        logKappaN[i] = 0.5 * (chargeSquared[i] / ionicStrength - dielectricN / mixturePermittivity);
        logKappaNT[i] = -0.5 * (dielectricNT / mixturePermittivity
            - dielectricN * dielectricT / (mixturePermittivity * mixturePermittivity));
        logKappaNV[i] = -0.5 * (dielectricNV / mixturePermittivity
            - dielectricN * dielectricV / (mixturePermittivity * mixturePermittivity));
        for (int j = 0; j < count; j++) {
          double etaNj = packingCoefficient[j] / volume;
          double dielectricNN = solvent.dNN[i][j] * dielectricFactor + solvent.dN[i] * dielectricFactorEta * etaNj
              + solvent.dN[j] * dielectricFactorEta * etaN + solvent.value * dielectricFactorEtaEta * etaN * etaNj;
          logKappaNN[i][j] = 0.5 * (-chargeSquared[i] * chargeSquared[j] / (ionicStrength * ionicStrength)
              - dielectricNN / mixturePermittivity
              + dielectricN * (solvent.dN[j] * dielectricFactor + solvent.value * dielectricFactorEta * etaNj)
                  / (mixturePermittivity * mixturePermittivity));
        }
      }
    }

    /** @return true when the state has a finite ionic screening parameter */
    private boolean isActive() {
      return kappa > 0.0 && Double.isFinite(kappa);
    }
  }

  /**
   * Exact solvent-permittivity value, temperature derivatives and composition Hessian.
   *
   * @author Even Solbraa
   * @version 1.0
   */
  private static final class SolventState {
    /** Solvent permittivity. */
    private final double value;
    /** Temperature derivative. */
    private final double dT;
    /** Second temperature derivative. */
    private final double dTT;
    /** Composition derivatives. */
    private final double[] dN;
    /** Composition-temperature derivatives. */
    private final double[] dNT;
    /** Composition Hessian. */
    private final double[][] dNN;

    /**
     * Builds exact derivatives for the selected dielectric mixing rule.
     *
     * @param phase electrolyte phase
     * @param temperature temperature [K]
     */
    private SolventState(PhaseElectrolyteCPAMM phase, double temperature) {
      int count = phase.getNumberOfComponents();
      dN = new double[count];
      dNT = new double[count];
      dNN = new double[count][count];
      int solventCount = 0;
      for (int i = 0; i < count; i++) {
        if (phase.getComponent(i).getIonicCharge() == 0.0) {
          solventCount++;
        }
      }
      if (phase.getDielectricMixingRule() == PhaseElectrolyteCPAMM.DielectricMixingRule.OSTER && solventCount == 2) {
        OsterState state = new OsterState(phase, temperature);
        value = state.value;
        dT = state.dT;
        dTT = state.dTT;
        System.arraycopy(state.dN, 0, dN, 0, count);
        System.arraycopy(state.dNT, 0, dNT, 0, count);
        for (int i = 0; i < count; i++) {
          System.arraycopy(state.dNN[i], 0, dNN[i], 0, count);
        }
        return;
      }

      PhaseElectrolyteCPAMM.DielectricMixingRule rule = phase.getDielectricMixingRule();
      if (rule == PhaseElectrolyteCPAMM.DielectricMixingRule.OSTER) {
        rule = PhaseElectrolyteCPAMM.DielectricMixingRule.VOLUME_AVERAGE;
      }
      double totalWeight = 0.0;
      double transformedSum = 0.0;
      double transformedSumT = 0.0;
      double transformedSumTT = 0.0;
      double[] weight = new double[count];
      double[] transformed = new double[count];
      double[] transformedT = new double[count];
      for (int i = 0; i < count; i++) {
        ComponentInterface component = phase.getComponent(i);
        if (component.getIonicCharge() != 0.0) {
          continue;
        }
        double amount = component.getNumberOfMolesInPhase();
        weight[i] = rule == PhaseElectrolyteCPAMM.DielectricMixingRule.MOLAR_AVERAGE ? 1.0
            : positiveMolarVolume(component);
        double epsilon = component.getDielectricConstant(temperature);
        double epsilonT = component.getDielectricConstantdT(temperature);
        double epsilonTT = component.getDielectricConstantdTdT(temperature);
        if (rule == PhaseElectrolyteCPAMM.DielectricMixingRule.LOOYENGA) {
          transformed[i] = Math.pow(epsilon, 1.0 / 3.0);
          transformedT[i] = epsilonT / (3.0 * Math.pow(epsilon, 2.0 / 3.0));
          transformedSumTT += amount * weight[i] * (epsilonTT / (3.0 * Math.pow(epsilon, 2.0 / 3.0))
              - 2.0 * epsilonT * epsilonT / (9.0 * Math.pow(epsilon, 5.0 / 3.0)));
        } else if (rule == PhaseElectrolyteCPAMM.DielectricMixingRule.LICHTENECKER) {
          transformed[i] = Math.log(epsilon);
          transformedT[i] = epsilonT / epsilon;
          transformedSumTT += amount * weight[i] * (epsilonTT / epsilon - epsilonT * epsilonT / (epsilon * epsilon));
        } else {
          transformed[i] = epsilon;
          transformedT[i] = epsilonT;
          transformedSumTT += amount * weight[i] * epsilonTT;
        }
        totalWeight += amount * weight[i];
        transformedSum += amount * weight[i] * transformed[i];
        transformedSumT += amount * weight[i] * transformedT[i];
      }
      if (totalWeight < 1e-50) {
        value = 0.0;
        dT = 0.0;
        dTT = 0.0;
        return;
      }
      double reduced = transformedSum / totalWeight;
      double reducedT = transformedSumT / totalWeight;
      double reducedTT = transformedSumTT / totalWeight;
      double transformFirst = 1.0;
      double transformSecond = 0.0;
      if (rule == PhaseElectrolyteCPAMM.DielectricMixingRule.LOOYENGA) {
        value = reduced * reduced * reduced;
        transformFirst = 3.0 * reduced * reduced;
        transformSecond = 6.0 * reduced;
      } else if (rule == PhaseElectrolyteCPAMM.DielectricMixingRule.LICHTENECKER) {
        value = Math.exp(reduced);
        transformFirst = value;
        transformSecond = value;
      } else {
        value = reduced;
      }
      dT = transformFirst * reducedT;
      dTT = transformSecond * reducedT * reducedT + transformFirst * reducedTT;

      double[] reducedN = new double[count];
      double[] reducedNT = new double[count];
      for (int i = 0; i < count; i++) {
        if (weight[i] == 0.0) {
          continue;
        }
        reducedN[i] = weight[i] * (transformed[i] - reduced) / totalWeight;
        reducedNT[i] = weight[i] * (transformedT[i] - reducedT) / totalWeight;
        dN[i] = transformFirst * reducedN[i];
        dNT[i] = transformSecond * reducedN[i] * reducedT + transformFirst * reducedNT[i];
      }
      for (int i = 0; i < count; i++) {
        for (int j = 0; j < count; j++) {
          double reducedNN = -(weight[i] * reducedN[j] + weight[j] * reducedN[i]) / totalWeight;
          dNN[i][j] = transformSecond * reducedN[i] * reducedN[j] + transformFirst * reducedNN;
        }
      }
    }
  }

  /**
   * Exact binary Oster dielectric state.
   *
   * @author Even Solbraa
   * @version 1.0
   */
  private static final class OsterState {
    /** Oster interaction parameter retained from the scalar implementation. */
    private static final double OSTER_INTERACTION = -0.3;
    /** State value. */
    private final double value;
    /** Temperature derivative. */
    private final double dT;
    /** Second temperature derivative. */
    private final double dTT;
    /** Composition derivatives. */
    private final double[] dN;
    /** Composition-temperature derivatives. */
    private final double[] dNT;
    /** Composition Hessian. */
    private final double[][] dNN;

    /**
     * Builds the binary Oster state.
     *
     * @param phase electrolyte phase
     * @param temperature temperature [K]
     */
    private OsterState(PhaseElectrolyteCPAMM phase, double temperature) {
      int count = phase.getNumberOfComponents();
      dN = new double[count];
      dNT = new double[count];
      dNN = new double[count][count];
      int first = -1;
      int second = -1;
      for (int i = 0; i < count; i++) {
        if (phase.getComponent(i).getIonicCharge() == 0.0) {
          if (first < 0) {
            first = i;
          } else {
            second = i;
          }
        }
      }
      ComponentInterface a = phase.getComponent(first);
      ComponentInterface b = phase.getComponent(second);
      double weightA = positiveMolarVolume(a);
      double weightB = positiveMolarVolume(b);
      double totalWeight = a.getNumberOfMolesInPhase() * weightA + b.getNumberOfMolesInPhase() * weightB;
      double fraction = a.getNumberOfMolesInPhase() * weightA / totalWeight;
      double epsilonA = a.getDielectricConstant(temperature);
      double epsilonB = b.getDielectricConstant(temperature);
      double epsilonAT = a.getDielectricConstantdT(temperature);
      double epsilonBT = b.getDielectricConstantdT(temperature);
      double epsilonATT = a.getDielectricConstantdTdT(temperature);
      double epsilonBTT = b.getDielectricConstantdTdT(temperature);
      double h = fraction + OSTER_INTERACTION * fraction * (1.0 - fraction);
      double hFirst = 1.0 + OSTER_INTERACTION * (1.0 - 2.0 * fraction);
      double hSecond = -2.0 * OSTER_INTERACTION;
      double difference = epsilonA - epsilonB;
      double differenceT = epsilonAT - epsilonBT;
      value = epsilonB + h * difference;
      dT = epsilonBT + h * differenceT;
      dTT = epsilonBTT + h * (epsilonATT - epsilonBTT);
      double[] fractionN = new double[count];
      fractionN[first] = weightA * (1.0 - fraction) / totalWeight;
      fractionN[second] = -weightB * fraction / totalWeight;
      double[] weights = new double[count];
      weights[first] = weightA;
      weights[second] = weightB;
      for (int i = 0; i < count; i++) {
        dN[i] = hFirst * fractionN[i] * difference;
        dNT[i] = hFirst * fractionN[i] * differenceT;
        for (int j = 0; j < count; j++) {
          double fractionNN = -(weights[i] * fractionN[j] + weights[j] * fractionN[i]) / totalWeight;
          dNN[i][j] = (hSecond * fractionN[i] * fractionN[j] + hFirst * fractionNN) * difference;
        }
      }
    }
  }

  /**
   * Helmholtz screening kernel and partial derivatives.
   *
   * @author Even Solbraa
   * @version 1.0
   */
  private static final class Kernel {
    /** Kernel value. */
    private final double value;
    /** First kappa derivative. */
    private final double kappa;
    /** Second kappa derivative. */
    private final double kappaKappa;
    /** Third kappa derivative. */
    private final double kappaKappaKappa;
    /** First diameter derivative. */
    private final double diameter;
    /** Kappa-diameter derivative. */
    private final double kappaDiameter;
    /** Second diameter derivative. */
    private final double diameterDiameter;

    /**
     * Builds kernel derivatives.
     *
     * @param screening Debye screening parameter [1/m]
     * @param meanDiameter mean ion diameter [m]
     */
    private Kernel(double screening, double meanDiameter) {
      double chi = screening * meanDiameter;
      double tau = PhaseElectrolyteCPAMM.calcTauDH(chi);
      double tauFirst = PhaseElectrolyteCPAMM.calcTauDHprime(chi);
      double tauSecond = PhaseElectrolyteCPAMM.calcTauDHsecond(chi);
      double tauThird = PhaseElectrolyteCPAMM.calcTauDHthird(chi);
      double screeningSquared = screening * screening;
      value = screeningSquared * screening * tau;
      kappa = screeningSquared * (3.0 * tau + chi * tauFirst);
      kappaKappa = screening * (6.0 * tau + 6.0 * chi * tauFirst + chi * chi * tauSecond);
      kappaKappaKappa = 6.0 * tau + 18.0 * chi * tauFirst + 9.0 * chi * chi * tauSecond + chi * chi * chi * tauThird;
      diameter = screeningSquared * screeningSquared * tauFirst;
      kappaDiameter = screeningSquared * screening * (4.0 * tauFirst + chi * tauSecond);
      diameterDiameter = screeningSquared * screeningSquared * screening * tauSecond;
    }
  }

  /**
   * Returns a positive solvent weighting volume, preserving the scalar model's fallback.
   *
   * @param component solvent component
   * @return weighting volume
   */
  private static double positiveMolarVolume(ComponentInterface component) {
    double volume = component.getCriticalVolume();
    return volume > 0.0 ? volume : 18.0;
  }
}
