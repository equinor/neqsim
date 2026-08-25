package neqsim.thermo.phase;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.physicalproperties.system.PhysicalPropertyModel;
import neqsim.thermo.component.ComponentGEInterface;
import neqsim.thermo.component.ComponentGePitzer;
import neqsim.thermo.mixingrule.MixingRuleTypeInterface;
import neqsim.util.exception.IsNaNException;
import neqsim.util.exception.TooManyIterationsException;

/**
 * Phase implementation for the Pitzer activity coefficient model.
 *
 * @author esol
 */
public class PhasePitzer extends PhaseGE {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  private static final Logger logger = LogManager.getLogger(PhasePitzer.class);
  /** Stable identity for the built-in legacy Pitzer parameter table. */
  public static final String DEFAULT_PARAMETER_DATASET_ID = "neqsim-legacy-pitzer-parameters-v1";
  /** Molality below which an ion is inactive for parameter-coverage auditing. */
  private static final double ACTIVE_ION_MOLALITY = 1.0e-8;
  private static final int TEMPERATURE_BETA0 = 1;
  private static final int TEMPERATURE_BETA1 = 2;
  private static final int TEMPERATURE_CPHI = 3;
  private static final int TEMPERATURE_BETA2 = 4;
  private static final int TEMPERATURE_THETA = 5;
  private static final int TEMPERATURE_PSI = 6;
  static final int NEUTRAL_FAMILY_LAMBDA = 7;
  static final int NEUTRAL_FAMILY_ZETA = 8;
  static final int NEUTRAL_FAMILY_MU = 9;
  static final int NEUTRAL_FAMILY_ETA = 10;

  private double[][] beta0;
  private double[][] beta1;
  private double[][] cphi;
  /** Second virial coefficient for 2-2 electrolytes (Harvie &amp; Weare 1984). */
  private double[][] beta2;
  /** Cation-cation or anion-anion mixing parameter theta (Harvie &amp; Weare 1984). */
  private double[][] theta;
  /**
   * Ternary mixing parameter psi (cation-cation-anion or anion-anion-cation).
   *
   * <p>
   * Only rows containing defined tuples are allocated. A dense tensor at the global 200-component limit would require
   * about 64 MB for every Pitzer phase, including short-lived reference phases.
   * </p>
   */
  private double[][][] psi;

  /** T-dependent coefficients for beta0: beta0(T) = beta0_25 + T1*(1/T-1/Tr) + T2*ln(T/Tr). */
  private double[][] beta0T1;
  private double[][] beta0T2;
  /** T-dependent coefficients for beta1. */
  private double[][] beta1T1;
  private double[][] beta1T2;
  /** T-dependent coefficients for Cphi. */
  private double[][] cphiT1;
  private double[][] cphiT2;
  /** Sparse opt-in PHREEQC six-term temperature functions, keyed by parameter family and tuple. */
  private Map<Long, PitzerTemperatureFunction> temperatureFunctions = new HashMap<Long, PitzerTemperatureFunction>();
  /** Fast gate that avoids map lookup for legacy parameter datasets. */
  private boolean hasTemperatureFunctions;
  /** Sparse opt-in neutral-solute interaction families, keyed without tuple allocation. */
  private Map<Long, PitzerNeutralInteraction> neutralInteractions = new HashMap<Long, PitzerNeutralInteraction>();
  /** Fast gate keeping legacy Pitzer and every non-Pitzer path out of neutral interaction loops. */
  private boolean hasNeutralInteractions;
  /** Enables PHREEQC common F and C0/Cphi ion terms for explicitly mapped datasets only. */
  private boolean phreeqcCommonIonTermsActive;
  /** Fast gate for a qualified beta2 row outside the legacy 2-2 branch. */
  private boolean nonTwoTwoBeta2Active;
  /** Whether the active neutral-solute topology has passed its fail-closed parameter audit. */
  private transient boolean neutralParameterCoverageValidated;
  /** Whether parameters have been loaded from database. */
  private boolean parametersLoaded = false;
  /** Prefer the complete bundled PHREEQC catalog when it covers the active topology. */
  private boolean usePhreeqcCatalogByDefault = true;
  /** Keep EOS-role hydrocarbons outside only the automatically selected aqueous-neutral topology. */
  private boolean excludeHydrocarbonsFromNeutralPitzerTopology;
  /** Whether immutable parameter arrays are shared with a clone until the next setter call. */
  private boolean parameterStorageShared;
  /** Stable identity of the selected parameter dataset. */
  private String parameterDatasetId = DEFAULT_PARAMETER_DATASET_ID;
  /** Explicitly defined cation-anion binary parameter pairs. */
  private Set<String> definedBinaryPairs = new HashSet<String>();
  /** Explicitly defined same-sign theta parameter pairs. */
  private Set<String> definedThetaPairs = new HashSet<String>();
  /** Explicitly defined ternary psi parameter tuples. */
  private Set<String> definedPsiTuples = new HashSet<String>();
  /** Revision incremented whenever a parameter definition changes. */
  private long parameterDefinitionRevision = 0L;
  /** Last active-topology fingerprint audited for parameter coverage. */
  private transient long cachedCoverageFingerprint;
  /** Parameter-definition revision associated with the cached coverage. */
  private transient long cachedCoverageRevision = Long.MIN_VALUE;
  /** Cached immutable coverage result. */
  private transient PitzerParameterCoverage cachedCoverage;
  /** Whether the current initialized primary-salt state passed the coverage gate. */
  private transient boolean parameterCoverageValidated;
  /** Cached component-topology state: zero unknown, one no unequal same-sign pair, two pair present. */
  private transient byte unequalChargeSameSignPairState;

  /** Constructor for PhasePitzer. */
  public PhasePitzer() {
    setPpm(PhysicalPropertyModel.SALT_WATER);
    int max = componentArray.length;
    beta0 = new double[max][max];
    beta1 = new double[max][max];
    cphi = new double[max][max];
    beta2 = new double[max][max];
    theta = new double[max][max];
    psi = new double[max][][];
    beta0T1 = new double[max][max];
    beta0T2 = new double[max][max];
    beta1T1 = new double[max][max];
    beta1T2 = new double[max][max];
    cphiT1 = new double[max][max];
    cphiT2 = new double[max][max];
  }

  /** {@inheritDoc} */
  @Override
  public PhasePitzer clone() {
    ensureDefinitionSets();
    ensureTemperatureFunctions();
    ensureNeutralInteractions();
    PhasePitzer clonedPhase = (PhasePitzer) super.clone();
    parameterStorageShared = true;
    clonedPhase.parameterStorageShared = true;
    clonedPhase.definedBinaryPairs = new HashSet<String>(definedBinaryPairs);
    clonedPhase.definedThetaPairs = new HashSet<String>(definedThetaPairs);
    clonedPhase.definedPsiTuples = new HashSet<String>(definedPsiTuples);
    clonedPhase.temperatureFunctions = new HashMap<Long, PitzerTemperatureFunction>(temperatureFunctions);
    clonedPhase.neutralInteractions = new HashMap<Long, PitzerNeutralInteraction>(neutralInteractions);
    clonedPhase.cachedCoverageFingerprint = 0L;
    clonedPhase.cachedCoverageRevision = Long.MIN_VALUE;
    clonedPhase.cachedCoverage = null;
    clonedPhase.parameterCoverageValidated = false;
    clonedPhase.neutralParameterCoverageValidated = false;
    clonedPhase.unequalChargeSameSignPairState = unequalChargeSameSignPairState;
    return clonedPhase;
  }

  /** {@inheritDoc} */
  @Override
  public void init(double totalNumberOfMoles, int numberOfComponents, int initType, PhaseType pt, double beta) {
    if (initType == 0) {
      parameterCoverageValidated = false;
      neutralParameterCoverageValidated = false;
    }
    super.init(totalNumberOfMoles, numberOfComponents, initType, pt, beta);
  }

  /** {@inheritDoc} */
  @Override
  public void addComponent(String name, double moles, double molesInPhase, int compNumber) {
    super.addComponent(name, molesInPhase, compNumber);
    componentArray[compNumber] = new ComponentGePitzer(name, moles, molesInPhase, compNumber);
    ((ComponentGePitzer) componentArray[compNumber]).setNeutralPitzerInteractionsActive(hasNeutralInteractions);
    unequalChargeSameSignPairState = 0;
    neutralParameterCoverageValidated = false;
  }

  /**
   * Returns whether the component topology contains a same-sign pair with unequal ionic charges.
   *
   * <p>
   * The result depends only on immutable component charge identities, not on phase composition, so it is cached after
   * the first query and invalidated only when a component is added. Ordinary binary electrolytes use this fast path to
   * avoid entering the higher-order electrostatic kernel.
   * </p>
   *
   * @return {@code true} when an unequal-charge cation-cation or anion-anion pair exists
   */
  public boolean hasUnequalChargeSameSignPair() {
    if (unequalChargeSameSignPairState == 0) {
      unequalChargeSameSignPairState = 1;
      for (int i = 0; i < numberOfComponents; i++) {
        double chargei = componentArray[i].getIonicCharge();
        if (Math.abs(chargei) < 0.5) {
          continue;
        }
        for (int j = i + 1; j < numberOfComponents; j++) {
          double chargej = componentArray[j].getIonicCharge();
          if (chargei * chargej > 0.0 && Math.abs(chargei - chargej) >= 1.0e-12) {
            unequalChargeSameSignPairState = 2;
            return true;
          }
        }
      }
    }
    return unequalChargeSameSignPairState == 2;
  }

  /** {@inheritDoc} */
  @Override
  public double getExcessGibbsEnergy(PhaseInterface phase, int numberOfComponents, double temperature, double pressure,
      PhaseType pt) {
    if (!parametersLoaded) {
      loadParametersFromDatabase();
    }
    validateParameterCoverageOncePerState();
    double GE = 0.0;
    for (int i = 0; i < numberOfComponents; i++) {
      GE += phase.getComponent(i).getx() * Math
          .log(((ComponentGePitzer) componentArray[i]).getGamma(phase, numberOfComponents, temperature, pressure, pt));
    }
    return R * temperature * numberOfMolesInPhase * GE;
  }

  /** {@inheritDoc} */
  @Override
  public void setMixingRule(MixingRuleTypeInterface mr) {
    super.setMixingRule(mr);
  }

  /** {@inheritDoc} */
  @Override
  public void setAlpha(double[][] alpha) {
    // Not used in Pitzer model
  }

  /** {@inheritDoc} */
  @Override
  public void setDij(double[][] Dij) {
    // Not used in Pitzer model
  }

  /** {@inheritDoc} */
  @Override
  public void setDijT(double[][] DijT) {
    // Not used in Pitzer model
  }

  /**
   * Set binary Pitzer parameters.
   *
   * @param i component i
   * @param j component j
   * @param b0 beta0 parameter
   * @param b1 beta1 parameter
   * @param c cPhi parameter
   */
  public void setBinaryParameters(int i, int j, double b0, double b1, double c) {
    ensureOwnedParameterStorage();
    beta0[i][j] = b0;
    beta0[j][i] = b0;
    beta1[i][j] = b1;
    beta1[j][i] = b1;
    cphi[i][j] = c;
    cphi[j][i] = c;
    ensureDefinitionSets();
    definedBinaryPairs.add(pairKey(componentName(i), componentName(j)));
    invalidateCoverageCache();
  }

  /**
   * Set T-dependent coefficients for beta0 (Silvester-Pitzer form).
   *
   * @param i component index i
   * @param j component index j
   * @param t1 coefficient for (1/T - 1/Tr) term
   * @param t2 coefficient for ln(T/Tr) term
   */
  public void setBeta0T(int i, int j, double t1, double t2) {
    ensureOwnedParameterStorage();
    beta0T1[i][j] = t1;
    beta0T1[j][i] = t1;
    beta0T2[i][j] = t2;
    beta0T2[j][i] = t2;
  }

  /**
   * Set T-dependent coefficients for beta1 (Silvester-Pitzer form).
   *
   * @param i component index i
   * @param j component index j
   * @param t1 coefficient for (1/T - 1/Tr) term
   * @param t2 coefficient for ln(T/Tr) term
   */
  public void setBeta1T(int i, int j, double t1, double t2) {
    ensureOwnedParameterStorage();
    beta1T1[i][j] = t1;
    beta1T1[j][i] = t1;
    beta1T2[i][j] = t2;
    beta1T2[j][i] = t2;
  }

  /**
   * Set T-dependent coefficients for Cphi (Silvester-Pitzer form).
   *
   * @param i component index i
   * @param j component index j
   * @param t1 coefficient for (1/T - 1/Tr) term
   * @param t2 coefficient for ln(T/Tr) term
   */
  public void setCphiT(int i, int j, double t1, double t2) {
    ensureOwnedParameterStorage();
    cphiT1[i][j] = t1;
    cphiT1[j][i] = t1;
    cphiT2[i][j] = t2;
    cphiT2[j][i] = t2;
  }

  /**
   * Sets six-term temperature functions for a cation-anion binary tuple.
   *
   * <p>
   * Each coefficient array is ordered {@code a0..a5}; the function is
   * {@code a0 + a1(1/T-1/Tr) + a2 ln(T/Tr) + a3(T-Tr) + a4(T^2-Tr^2)
   * + a5(1/T^2-1/Tr^2)}. This method defines beta0, beta1, and NeqSim Cphi as one coherent binary tuple. Use
   * {@link #setPhreeqcBinaryTemperatureCoefficients} when the source fields are PHREEQC {@code -B0}, {@code -B1}, and
   * {@code -C0}.
   * </p>
   *
   * @param i component index i
   * @param j component index j
   * @param referenceTemperature reference temperature in K
   * @param beta0Coefficients beta0 coefficients in six-term order
   * @param beta1Coefficients beta1 coefficients in six-term order
   * @param cphiCoefficients NeqSim Cphi coefficients in six-term order
   */
  public void setBinaryTemperatureCoefficients(int i, int j, double referenceTemperature, double[] beta0Coefficients,
      double[] beta1Coefficients, double[] cphiCoefficients) {
    PitzerTemperatureFunction beta0Function = new PitzerTemperatureFunction(referenceTemperature, beta0Coefficients);
    PitzerTemperatureFunction beta1Function = new PitzerTemperatureFunction(referenceTemperature, beta1Coefficients);
    PitzerTemperatureFunction cphiFunction = new PitzerTemperatureFunction(referenceTemperature, cphiCoefficients);
    setBinaryParameters(i, j, beta0Coefficients[0], beta1Coefficients[0], cphiCoefficients[0]);
    setTemperatureFunction(pairTemperatureKey(TEMPERATURE_BETA0, i, j), beta0Function);
    setTemperatureFunction(pairTemperatureKey(TEMPERATURE_BETA1, i, j), beta1Function);
    setTemperatureFunction(pairTemperatureKey(TEMPERATURE_CPHI, i, j), cphiFunction);
  }

  /**
   * Sets PHREEQC six-term {@code -B0}, {@code -B1}, and {@code -C0} functions.
   *
   * <p>
   * PHREEQC stores {@code -C0} as the Pitzer {@code Cphi} parameter and divides it by {@code 2*sqrt(abs(zM*zX))} inside
   * the thermodynamic sums. NeqSim applies the same normalization to its Cphi value, so every {@code -C0} coefficient
   * is passed unchanged. Callers must not pre-divide or multiply PHREEQC values by the charge normalization.
   * </p>
   *
   * @param i component index i
   * @param j component index j
   * @param referenceTemperature reference temperature in K
   * @param beta0Coefficients PHREEQC {@code -B0} coefficients in {@code a0..a5} order
   * @param beta1Coefficients PHREEQC {@code -B1} coefficients in {@code a0..a5} order
   * @param c0Coefficients PHREEQC {@code -C0} coefficients in {@code a0..a5} order
   */
  public void setPhreeqcBinaryTemperatureCoefficients(int i, int j, double referenceTemperature,
      double[] beta0Coefficients, double[] beta1Coefficients, double[] c0Coefficients) {
    setBinaryTemperatureCoefficients(i, j, referenceTemperature, beta0Coefficients, beta1Coefficients, c0Coefficients);
  }

  /**
   * Sets a PHREEQC six-term beta2 temperature function.
   *
   * @param i component index i
   * @param j component index j
   * @param referenceTemperature reference temperature in K
   * @param coefficients six coefficients in PHREEQC order
   */
  public void setBeta2TemperatureCoefficients(int i, int j, double referenceTemperature, double[] coefficients) {
    PitzerTemperatureFunction function = new PitzerTemperatureFunction(referenceTemperature, coefficients);
    setBeta2(i, j, coefficients[0]);
    setTemperatureFunction(pairTemperatureKey(TEMPERATURE_BETA2, i, j), function);
  }

  /**
   * Sets a PHREEQC six-term theta temperature function.
   *
   * @param i component index i
   * @param j component index j
   * @param referenceTemperature reference temperature in K
   * @param coefficients six coefficients in PHREEQC order
   */
  public void setThetaTemperatureCoefficients(int i, int j, double referenceTemperature, double[] coefficients) {
    PitzerTemperatureFunction function = new PitzerTemperatureFunction(referenceTemperature, coefficients);
    setTheta(i, j, coefficients[0]);
    setTemperatureFunction(pairTemperatureKey(TEMPERATURE_THETA, i, j), function);
  }

  /**
   * Sets a PHREEQC six-term psi temperature function.
   *
   * @param i first same-sign component index
   * @param j second same-sign component index
   * @param k opposite-sign component index
   * @param referenceTemperature reference temperature in K
   * @param coefficients six coefficients in PHREEQC order
   */
  public void setPsiTemperatureCoefficients(int i, int j, int k, double referenceTemperature, double[] coefficients) {
    PitzerTemperatureFunction function = new PitzerTemperatureFunction(referenceTemperature, coefficients);
    setPsi(i, j, k, coefficients[0]);
    setTemperatureFunction(tripleTemperatureKey(TEMPERATURE_PSI, i, j, k), function);
  }

  /**
   * Sets a constant Pitzer lambda parameter containing at least one neutral solute.
   *
   * @param first first component index
   * @param second second component index; may be an ion or another neutral solute
   * @param value lambda parameter on the source dataset's molality convention
   */
  public void setLambda(int first, int second, double value) {
    setLambdaTemperatureCoefficients(first, second, 298.15, new double[] { value, 0.0, 0.0, 0.0, 0.0, 0.0 });
  }

  /**
   * Sets a PHREEQC six-term lambda temperature function containing at least one neutral solute.
   *
   * @param first first component index
   * @param second second component index; may be an ion or another neutral solute
   * @param referenceTemperature reference temperature in K
   * @param coefficients six coefficients in PHREEQC order
   */
  public void setLambdaTemperatureCoefficients(int first, int second, double referenceTemperature,
      double[] coefficients) {
    requireComponentIndex(first);
    requireComponentIndex(second);
    if (!isNeutralSolute(first) && !isNeutralSolute(second)) {
      throw new IllegalArgumentException("Pitzer lambda requires at least one neutral non-water solute");
    }
    if ((!isNeutralSolute(first) && Math.abs(getComponent(first).getIonicCharge()) < 0.5)
        || (!isNeutralSolute(second) && Math.abs(getComponent(second).getIonicCharge()) < 0.5)) {
      throw new IllegalArgumentException("Pitzer lambda does not permit water");
    }
    setNeutralInteraction(NEUTRAL_FAMILY_LAMBDA, new int[] { first, second },
        new PitzerTemperatureFunction(referenceTemperature, coefficients));
  }

  /**
   * Sets a constant neutral-cation-anion Pitzer zeta parameter.
   *
   * @param neutral neutral non-water component index
   * @param cation cation component index
   * @param anion anion component index
   * @param value zeta parameter
   */
  public void setZeta(int neutral, int cation, int anion, double value) {
    setZetaTemperatureCoefficients(neutral, cation, anion, 298.15, new double[] { value, 0.0, 0.0, 0.0, 0.0, 0.0 });
  }

  /**
   * Sets a PHREEQC six-term neutral-cation-anion zeta temperature function.
   *
   * @param neutral neutral non-water component index
   * @param cation cation component index
   * @param anion anion component index
   * @param referenceTemperature reference temperature in K
   * @param coefficients six coefficients in PHREEQC order
   */
  public void setZetaTemperatureCoefficients(int neutral, int cation, int anion, double referenceTemperature,
      double[] coefficients) {
    requireNeutralSolute(neutral, "zeta");
    requireChargeSign(cation, 1, "zeta cation");
    requireChargeSign(anion, -1, "zeta anion");
    setNeutralInteraction(NEUTRAL_FAMILY_ZETA, new int[] { neutral, cation, anion },
        new PitzerTemperatureFunction(referenceTemperature, coefficients));
  }

  /**
   * Sets a constant neutral-neutral-neutral Pitzer mu parameter.
   *
   * @param first first neutral non-water component index
   * @param second second neutral non-water component index
   * @param third third neutral non-water component index
   * @param value mu parameter
   */
  public void setMu(int first, int second, int third, double value) {
    setMuTemperatureCoefficients(first, second, third, 298.15, new double[] { value, 0.0, 0.0, 0.0, 0.0, 0.0 });
  }

  /**
   * Sets a PHREEQC six-term neutral-neutral-neutral mu temperature function.
   *
   * @param first first neutral non-water component index
   * @param second second neutral non-water component index
   * @param third third neutral non-water component index
   * @param referenceTemperature reference temperature in K
   * @param coefficients six coefficients in PHREEQC order
   */
  public void setMuTemperatureCoefficients(int first, int second, int third, double referenceTemperature,
      double[] coefficients) {
    requireNeutralSolute(first, "mu");
    requireNeutralSolute(second, "mu");
    requireNeutralSolute(third, "mu");
    setNeutralInteraction(NEUTRAL_FAMILY_MU, new int[] { first, second, third },
        new PitzerTemperatureFunction(referenceTemperature, coefficients));
  }

  /**
   * Sets a constant neutral/same-sign-ion Pitzer eta parameter.
   *
   * @param neutral neutral non-water component index
   * @param firstIon first ionic component index
   * @param secondIon second same-sign ionic component index
   * @param value eta parameter
   */
  public void setEta(int neutral, int firstIon, int secondIon, double value) {
    setEtaTemperatureCoefficients(neutral, firstIon, secondIon, 298.15,
        new double[] { value, 0.0, 0.0, 0.0, 0.0, 0.0 });
  }

  /**
   * Sets a PHREEQC six-term neutral/same-sign-ion eta temperature function.
   *
   * @param neutral neutral non-water component index
   * @param firstIon first ionic component index
   * @param secondIon second same-sign ionic component index
   * @param referenceTemperature reference temperature in K
   * @param coefficients six coefficients in PHREEQC order
   */
  public void setEtaTemperatureCoefficients(int neutral, int firstIon, int secondIon, double referenceTemperature,
      double[] coefficients) {
    requireNeutralSolute(neutral, "eta");
    requireIon(firstIon, "eta");
    requireIon(secondIon, "eta");
    if (getComponent(firstIon).getIonicCharge() * getComponent(secondIon).getIonicCharge() <= 0.0) {
      throw new IllegalArgumentException("Pitzer eta requires two ions with the same charge sign");
    }
    setNeutralInteraction(NEUTRAL_FAMILY_ETA, new int[] { neutral, firstIon, secondIon },
        new PitzerTemperatureFunction(referenceTemperature, coefficients));
  }

  /**
   * Gets a temperature-adjusted neutral-ion or neutral-neutral lambda parameter.
   *
   * @param first first component index
   * @param second second component index
   * @param temperature temperature in K
   * @return lambda value, or zero when the tuple is not defined
   */
  public double getLambda(int first, int second, double temperature) {
    return getNeutralInteractionValue(NEUTRAL_FAMILY_LAMBDA, first, second, -1, temperature);
  }

  /** Gets a temperature-adjusted zeta parameter, or zero when absent. */
  public double getZeta(int neutral, int cation, int anion, double temperature) {
    return getNeutralInteractionValue(NEUTRAL_FAMILY_ZETA, neutral, cation, anion, temperature);
  }

  /** Gets a temperature-adjusted mu parameter, or zero when absent. */
  public double getMu(int first, int second, int third, double temperature) {
    return getNeutralInteractionValue(NEUTRAL_FAMILY_MU, first, second, third, temperature);
  }

  /** Gets a temperature-adjusted eta parameter, or zero when absent. */
  public double getEta(int neutral, int firstIon, int secondIon, double temperature) {
    return getNeutralInteractionValue(NEUTRAL_FAMILY_ETA, neutral, firstIon, secondIon, temperature);
  }

  /**
   * Reports whether the sparse neutral-solute interaction layer is active.
   *
   * @return {@code true} after at least one lambda, zeta, mu, or eta tuple is defined
   */
  public boolean hasNeutralPitzerInteractions() {
    return hasNeutralInteractions;
  }

  /**
   * Reports whether the selected dataset uses PHREEQC's common ion-activity terms.
   *
   * <p>
   * The opt-in keeps the historical NeqSim Pitzer dataset numerically unchanged. Explicit PHREEQC datasets evaluate the
   * common binary-derivative F sum over every cation-anion pair and add
   * {@code |z_i| * sum_c sum_a m_c m_a C0_ca/(2 sqrt(|z_c z_a|))} to each ion's natural-log activity coefficient.
   * </p>
   *
   * @return {@code true} for an explicitly mapped PHREEQC dataset
   */
  public boolean isPhreeqcCommonIonTermsActive() {
    return phreeqcCommonIonTermsActive;
  }

  /**
   * Calculates all neutral-family contributions to one component's ln(gamma).
   *
   * @param componentIndex target component index
   * @param temperature temperature in K
   * @return neutral-family contribution to ln(gamma)
   */
  public double getNeutralPitzerLogGammaContribution(int componentIndex, double temperature) {
    if (!hasNeutralInteractions) {
      return 0.0;
    }
    validateNeutralParameterCoverageOncePerState();
    double contribution = 0.0;
    for (PitzerNeutralInteraction interaction : neutralInteractions.values()) {
      contribution += interaction.logGammaContribution(componentIndex, this, temperature);
    }
    return contribution;
  }

  /**
   * Calculates all neutral-family contributions to PHREEQC's osmotic sum.
   *
   * @param temperature temperature in K
   * @return contribution before the common {@code 2/sum(m)} factor
   */
  public double getNeutralPitzerOsmoticContribution(double temperature) {
    if (!hasNeutralInteractions) {
      return 0.0;
    }
    validateNeutralParameterCoverageOncePerState();
    double contribution = 0.0;
    for (PitzerNeutralInteraction interaction : neutralInteractions.values()) {
      contribution += interaction.osmoticContribution(this, temperature);
    }
    return contribution;
  }

  /**
   * Loads Pitzer binary parameters from the PitzerParameters database table.
   *
   * <p>
   * Matches ion names to components present in this phase and sets beta0, beta1, Cphi and their temperature-dependent
   * coefficients.
   * </p>
   */
  public void loadParametersFromDatabase() {
    if (usePhreeqcCatalogByDefault && PitzerParameterDatasets.tryApplyCompletePhreeqcPitzerCatalog(this)) {
      return;
    }
    try (neqsim.util.database.NeqSimDataBase database = new neqsim.util.database.NeqSimDataBase();
        java.sql.ResultSet dataSet = database.getResultSet("SELECT * FROM pitzerparameters")) {
      while (dataSet.next()) {
        String ion1Name = dataSet.getString("ion1").trim();
        String ion2Name = dataSet.getString("ion2").trim();

        int idx1 = -1;
        int idx2 = -1;
        for (int k = 0; k < numberOfComponents; k++) {
          String compName = getComponent(k).getComponentName();
          if (compName.equals(ion1Name)) {
            idx1 = k;
          }
          if (compName.equals(ion2Name)) {
            idx2 = k;
          }
        }
        if (idx1 < 0 || idx2 < 0) {
          continue;
        }

        double b0 = dataSet.getDouble("beta0_25");
        double b1 = dataSet.getDouble("beta1_25");
        double cp = dataSet.getDouble("Cphi_25");
        setBinaryParameters(idx1, idx2, b0, b1, cp);

        // Load beta2 for 2-2 electrolytes
        try {
          double b2 = dataSet.getDouble("beta2_25");
          if (Math.abs(b2) > 1e-20) {
            beta2[idx1][idx2] = b2;
            beta2[idx2][idx1] = b2;
          }
        } catch (Exception ex2) {
          // Column may not exist in older databases
        }

        double b0t1 = dataSet.getDouble("beta0_T1");
        double b0t2 = dataSet.getDouble("beta0_T2");
        double b1t1 = dataSet.getDouble("beta1_T1");
        double b1t2 = dataSet.getDouble("beta1_T2");
        double ct1 = dataSet.getDouble("Cphi_T1");
        double ct2 = dataSet.getDouble("Cphi_T2");

        beta0T1[idx1][idx2] = b0t1;
        beta0T1[idx2][idx1] = b0t1;
        beta0T2[idx1][idx2] = b0t2;
        beta0T2[idx2][idx1] = b0t2;
        beta1T1[idx1][idx2] = b1t1;
        beta1T1[idx2][idx1] = b1t1;
        beta1T2[idx1][idx2] = b1t2;
        beta1T2[idx2][idx1] = b1t2;
        cphiT1[idx1][idx2] = ct1;
        cphiT1[idx2][idx1] = ct1;
        cphiT2[idx1][idx2] = ct2;
        cphiT2[idx2][idx1] = ct2;
      }
      parametersLoaded = true;
    } catch (Exception ex) {
      parametersLoaded = true; // prevent infinite retries
      logger.error("Failed to load Pitzer parameters from database", ex);
    }
  }

  /**
   * Get T-dependent beta0 parameter.
   *
   * @param i component index i
   * @param j component index j
   * @param TK temperature in Kelvin
   * @return beta0 at temperature T
   */
  public double getBeta0ij(int i, int j, double TK) {
    PitzerTemperatureFunction function = getPairTemperatureFunction(TEMPERATURE_BETA0, i, j);
    if (function != null) {
      return function.valueAt(TK);
    }
    double b0_25 = beta0[i][j];
    double t1 = beta0T1[i][j];
    double t2 = beta0T2[i][j];
    if (Math.abs(t1) < 1e-20 && Math.abs(t2) < 1e-20) {
      return b0_25;
    }
    // Silvester-Pitzer form: beta0(T) = beta0_25 + t1*(1/T - 1/Tr) + t2*ln(T/Tr)
    return b0_25 + t1 * (1.0 / TK - 1.0 / 298.15) + t2 * Math.log(TK / 298.15);
  }

  /**
   * Get T-dependent beta1 parameter.
   *
   * @param i component index i
   * @param j component index j
   * @param TK temperature in Kelvin
   * @return beta1 at temperature T
   */
  public double getBeta1ij(int i, int j, double TK) {
    PitzerTemperatureFunction function = getPairTemperatureFunction(TEMPERATURE_BETA1, i, j);
    if (function != null) {
      return function.valueAt(TK);
    }
    double b1_25 = beta1[i][j];
    double t1 = beta1T1[i][j];
    double t2 = beta1T2[i][j];
    if (Math.abs(t1) < 1e-20 && Math.abs(t2) < 1e-20) {
      return b1_25;
    }
    return b1_25 + t1 * (1.0 / TK - 1.0 / 298.15) + t2 * Math.log(TK / 298.15);
  }

  /**
   * Get T-dependent Cphi parameter.
   *
   * @param i component index i
   * @param j component index j
   * @param TK temperature in Kelvin
   * @return Cphi at temperature T
   */
  public double getCphiij(int i, int j, double TK) {
    PitzerTemperatureFunction function = getPairTemperatureFunction(TEMPERATURE_CPHI, i, j);
    if (function != null) {
      return function.valueAt(TK);
    }
    double c_25 = cphi[i][j];
    double t1 = cphiT1[i][j];
    double t2 = cphiT2[i][j];
    if (Math.abs(t1) < 1e-20 && Math.abs(t2) < 1e-20) {
      return c_25;
    }
    return c_25 + t1 * (1.0 / TK - 1.0 / 298.15) + t2 * Math.log(TK / 298.15);
  }

  /**
   * Returns whether parameters have been loaded from database.
   *
   * @return true if loaded
   */
  public boolean isParametersLoaded() {
    return parametersLoaded;
  }

  /**
   * Configures automatic use of the bundled PHREEQC catalog.
   *
   * <p>
   * New Pitzer phases prefer the catalog by default. When its explicit rows do not cover the complete active topology,
   * loading falls back to the legacy binary table; the existing mixed-topology coverage gate still rejects missing
   * theta or psi terms. Change this policy only before parameters are first evaluated.
   * </p>
   *
   * @param useCatalog {@code true} to prefer the complete PHREEQC topology, {@code false} for legacy loading
   * @throws IllegalStateException if parameter evaluation already selected a dataset
   */
  public void setUsePhreeqcCatalogByDefault(boolean useCatalog) {
    if (parametersLoaded) {
      throw new IllegalStateException("Pitzer parameter dataset has already been selected");
    }
    usePhreeqcCatalogByDefault = useCatalog;
  }

  /**
   * Reports the automatic dataset-selection policy.
   *
   * @return {@code true} when the complete PHREEQC catalog is preferred
   */
  public boolean isUsePhreeqcCatalogByDefault() {
    return usePhreeqcCatalogByDefault;
  }

  /**
   * Gets the stable identity of the selected Pitzer parameter dataset.
   *
   * @return parameter dataset identity
   */
  public String getParameterDatasetId() {
    return parameterDatasetId;
  }

  /**
   * Sets the identity used to report a manually configured Pitzer parameter dataset.
   *
   * <p>
   * This method changes provenance metadata only. Callers remain responsible for defining all binary and mixed-ion
   * interactions with the parameter setter methods.
   * </p>
   *
   * @param datasetId non-empty stable parameter dataset identity
   */
  public void setParameterDatasetId(String datasetId) {
    if (datasetId == null || datasetId.trim().isEmpty()) {
      throw new IllegalArgumentException("Pitzer parameter dataset identity must not be empty");
    }
    parameterDatasetId = datasetId.trim();
    invalidateCoverageCache();
  }

  /**
   * Audits whether all Pitzer interactions required by the active primary-salt species are explicitly defined.
   *
   * <p>
   * An explicitly configured zero is treated as defined; an absent database or setter entry is reported as missing.
   * Ions below {@value #ACTIVE_ION_MOLALITY} mol/kg are excluded so numerical trace material does not change the active
   * parameter topology.
   * </p>
   *
   * @return immutable deterministic parameter-coverage diagnostic
   */
  public PitzerParameterCoverage getPitzerParameterCoverage() {
    if (definedBinaryPairs == null || definedThetaPairs == null || definedPsiTuples == null) {
      ensureDefinitionSets();
      parametersLoaded = false;
    }
    if (!parametersLoaded) {
      loadParametersFromDatabase();
    }

    long fingerprint = activeTopologyFingerprint();
    if (cachedCoverage != null && fingerprint == cachedCoverageFingerprint
        && parameterDefinitionRevision == cachedCoverageRevision) {
      return cachedCoverage;
    }

    List<Integer> cationIndexes = activeIonIndexes(true);
    List<Integer> anionIndexes = activeIonIndexes(false);
    List<String> activeCations = componentNames(cationIndexes);
    List<String> activeAnions = componentNames(anionIndexes);
    List<String> missingBinary = new ArrayList<String>();
    List<String> missingTheta = new ArrayList<String>();
    List<String> missingPsi = new ArrayList<String>();

    for (int cation : cationIndexes) {
      for (int anion : anionIndexes) {
        String key = pairKey(componentName(cation), componentName(anion));
        if (!definedBinaryPairs.contains(key)) {
          missingBinary.add(key);
        }
      }
    }
    findMissingMixedInteractions(cationIndexes, anionIndexes, missingTheta, missingPsi);
    findMissingMixedInteractions(anionIndexes, cationIndexes, missingTheta, missingPsi);

    cachedCoverage = new PitzerParameterCoverage(parameterDatasetId, activeCations, activeAnions, missingBinary,
        missingTheta, missingPsi);
    cachedCoverageFingerprint = fingerprint;
    cachedCoverageRevision = parameterDefinitionRevision;
    return cachedCoverage;
  }

  /**
   * Requires complete Pitzer parameter coverage for the current active ionic topology.
   *
   * @throws IllegalStateException when one or more required interactions are absent
   */
  public void requireCompletePitzerParameterCoverage() {
    PitzerParameterCoverage coverage = getPitzerParameterCoverage();
    if (!coverage.isComplete()) {
      throw new IllegalStateException(coverage.formatDiagnostic());
    }
  }

  /**
   * Get beta2 parameter for ion pairs that define the second exponential term.
   *
   * @param i component index i
   * @param j component index j
   * @return beta2 parameter
   */
  public double getBeta2ij(int i, int j) {
    return beta2[i][j];
  }

  /**
   * Gets the temperature-adjusted beta2 parameter.
   *
   * @param i component index i
   * @param j component index j
   * @param temperature temperature in K
   * @return beta2 parameter at temperature
   */
  public double getBeta2ij(int i, int j, double temperature) {
    PitzerTemperatureFunction function = getPairTemperatureFunction(TEMPERATURE_BETA2, i, j);
    return function == null ? beta2[i][j] : function.valueAt(temperature);
  }

  /**
   * Set beta2 parameter for ion pairs that define the second exponential term.
   *
   * @param i component index i
   * @param j component index j
   * @param value beta2 value
   */
  public void setBeta2(int i, int j, double value) {
    ensureOwnedParameterStorage();
    beta2[i][j] = value;
    beta2[j][i] = value;
    double firstCharge = Math.abs(getComponent(i).getIonicCharge());
    double secondCharge = Math.abs(getComponent(j).getIonicCharge());
    if (!(firstCharge >= 1.5 && secondCharge >= 1.5) && Math.abs(value) > 1.0e-20) {
      nonTwoTwoBeta2Active = true;
    }
  }

  /**
   * Reports whether a qualified dataset activates beta2 outside the legacy 2-2 branch.
   *
   * @return {@code true} when a non-2-2 beta2 row was configured
   */
  public boolean isNonTwoTwoBeta2Active() {
    return nonTwoTwoBeta2Active;
  }

  /**
   * Returns the PHREEQC default alpha1 coefficient for an opposite-sign ion pair.
   *
   * <p>
   * PHREEQC uses 1.4 for 2-2 pairs and 2.0 for pairs containing a monovalent ion or an ion of charge magnitude above
   * two. Dataset-specific {@code ALPHAS} overrides are not currently exposed; qualified datasets must therefore use
   * these defaults.
   * </p>
   *
   * @param i first ion component index
   * @param j second ion component index
   * @return alpha1 in (kg/mol)<sup>1/2</sup>
   */
  public double getPitzerAlpha1(int i, int j) {
    double firstCharge = Math.abs(getComponent(i).getIonicCharge());
    double secondCharge = Math.abs(getComponent(j).getIonicCharge());
    return firstCharge >= 1.5 && firstCharge < 2.5 && secondCharge >= 1.5 && secondCharge < 2.5 ? 1.4 : 2.0;
  }

  /**
   * Returns the PHREEQC default alpha2 coefficient for an opposite-sign ion pair.
   *
   * <p>
   * PHREEQC assigns 12.0 when either ion is monovalent and also for 2-2 pairs; pairs containing no monovalent ion and
   * not exactly 2-2 use 50.0. This is significant for CaCl2: its qualified PHREEQC {@code B2} row is a 2-1 term with
   * alpha2=12 and must not be discarded by a 2-2-only branch.
   * </p>
   *
   * @param i first ion component index
   * @param j second ion component index
   * @return alpha2 in (kg/mol)<sup>1/2</sup>
   */
  public double getPitzerAlpha2(int i, int j) {
    double firstCharge = Math.abs(getComponent(i).getIonicCharge());
    double secondCharge = Math.abs(getComponent(j).getIonicCharge());
    boolean containsMonovalent = firstCharge < 1.5 || secondCharge < 1.5;
    boolean isTwoTwo = firstCharge >= 1.5 && firstCharge < 2.5 && secondCharge >= 1.5 && secondCharge < 2.5;
    return containsMonovalent || isTwoTwo ? 12.0 : 50.0;
  }

  /**
   * Get theta mixing parameter for same-sign ion pair.
   *
   * @param i component index i
   * @param j component index j
   * @return theta parameter
   */
  public double getThetaij(int i, int j) {
    return theta[i][j];
  }

  /**
   * Gets the temperature-adjusted theta parameter.
   *
   * @param i component index i
   * @param j component index j
   * @param temperature temperature in K
   * @return theta parameter at temperature
   */
  public double getThetaij(int i, int j, double temperature) {
    PitzerTemperatureFunction function = getPairTemperatureFunction(TEMPERATURE_THETA, i, j);
    return function == null ? theta[i][j] : function.valueAt(temperature);
  }

  /**
   * Set theta mixing parameter for same-sign ion pair.
   *
   * @param i component index i
   * @param j component index j
   * @param value theta value
   */
  public void setTheta(int i, int j, double value) {
    ensureOwnedParameterStorage();
    theta[i][j] = value;
    theta[j][i] = value;
    ensureDefinitionSets();
    definedThetaPairs.add(pairKey(componentName(i), componentName(j)));
    invalidateCoverageCache();
  }

  /**
   * Get psi ternary mixing parameter.
   *
   * @param i component index i
   * @param j component index j
   * @param k component index k
   * @return psi parameter
   */
  public double getPsiijk(int i, int j, int k) {
    return getPsiValue(i, j, k);
  }

  /**
   * Gets the temperature-adjusted psi parameter.
   *
   * @param i first component index
   * @param j second component index
   * @param k third component index
   * @param temperature temperature in K
   * @return psi parameter at temperature
   */
  public double getPsiijk(int i, int j, int k, double temperature) {
    PitzerTemperatureFunction function = getTripleTemperatureFunction(TEMPERATURE_PSI, i, j, k);
    return function == null ? getPsiValue(i, j, k) : function.valueAt(temperature);
  }

  /**
   * Set psi ternary mixing parameter.
   *
   * @param i component index i
   * @param j component index j
   * @param k component index k
   * @param value psi value
   */
  public void setPsi(int i, int j, int k, double value) {
    ensureOwnedParameterStorage();
    setPsiValue(i, j, k, value);
    setPsiValue(j, i, k, value);
    setPsiValue(i, k, j, value);
    setPsiValue(j, k, i, value);
    setPsiValue(k, i, j, value);
    setPsiValue(k, j, i, value);
    ensureDefinitionSets();
    definedPsiTuples.add(psiKey(componentName(i), componentName(j), componentName(k)));
    invalidateCoverageCache();
  }

  /**
   * Get beta0 parameter.
   *
   * @param i component index i
   * @param j component index j
   * @return beta0 parameter for components i and j
   */
  public double getBeta0ij(int i, int j) {
    return beta0[i][j];
  }

  /**
   * Get beta1 parameter.
   *
   * @param i component index i
   * @param j component index j
   * @return beta1 parameter for components i and j
   */
  public double getBeta1ij(int i, int j) {
    return beta1[i][j];
  }

  /**
   * Get Cphi parameter.
   *
   * @param i component index i
   * @param j component index j
   * @return Cphi parameter for components i and j
   */
  public double getCphiij(int i, int j) {
    return cphi[i][j];
  }

  /**
   * Calculate ionic strength.
   *
   * @return ionic strength
   */
  public double getIonicStrength() {
    double ionStrength = 0.0;
    for (int i = 0; i < numberOfComponents; i++) {
      ionStrength += getComponent(i).getMolality(this) * Math.pow(getComponent(i).getIonicCharge(), 2.0);
    }
    return 0.5 * ionStrength;
  }

  /**
   * Get mass of solvent in kilograms.
   *
   * @return solvent mass
   */
  public double getSolventWeight() {
    double moles = 0.0;
    for (int i = 0; i < numberOfComponents; i++) {
      if (getComponent(i).getComponentName().equals("water")) {
        moles += getComponent(i).getNumberOfMolesInPhase() * getComponent(i).getMolarMass();
      }
    }
    return moles;
  }

  /** {@inheritDoc} */
  @Override
  public double getActivityCoefficient(int k) {
    return ((ComponentGEInterface) getComponent(k)).getGamma();
  }

  /** {@inheritDoc} */
  @Override
  public double getOsmoticCoefficientOfWater() {
    return getPitzerOsmoticCoefficient();
  }

  /** {@inheritDoc} */
  @Override
  public double getOsmoticCoefficientOfWaterMolality() {
    return getPitzerOsmoticCoefficient();
  }

  /** {@inheritDoc} */
  @Override
  public double getOsmoticCoefficient(int waterComponentNumber) {
    return getPitzerOsmoticCoefficient();
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * Computes brine density as a function of temperature, pressure, and salinity using the Rowe-Chou (1970) correlation
   * for NaCl-equivalent brine, extended with pressure correction. Fall-back is pure water density from Kell (1975).
   * Much more accurate than the inherited hard-coded 997 kg/m3.
   * </p>
   */
  @Override
  public double getDensity() {
    double physicalPropertyDensity = 0.0;
    try {
      physicalPropertyDensity = new neqsim.physicalproperties.methods.liquidphysicalproperties.density.Water(
          getPhysicalProperties()).calcDensity();
    } catch (Exception ex) {
      logger.debug("Could not calculate Pitzer salt-water physical-property density", ex);
    }
    if (physicalPropertyDensity > 0.0 && !Double.isNaN(physicalPropertyDensity)
        && !Double.isInfinite(physicalPropertyDensity)) {
      return physicalPropertyDensity;
    }

    double TC = temperature - 273.15;
    if (TC < 0.0) {
      TC = 0.0;
    }
    if (TC > 300.0) {
      TC = 300.0;
    }

    // Pure water density (Kell 1975 polynomial, 0-150°C at ~1 atm)
    double rhoW;
    if (TC <= 100.0) {
      rhoW = 999.83 + 5.0948e-2 * TC - 7.5722e-3 * TC * TC + 3.8907e-5 * TC * TC * TC - 1.2e-7 * TC * TC * TC * TC;
    } else {
      // IAPWS approximate saturated-liquid density above 100°C
      double dT = TC - 100.0;
      rhoW = 958.0 - 1.08 * dT - 0.0028 * dT * dT;
    }
    if (rhoW < 700.0) {
      rhoW = 700.0;
    }

    // Calculate NaCl-equivalent salinity (total dissolved solids in kg/m³)
    // S = sum(m_ion * MW_ion) / (sum(m_ion * MW_ion) + 1 kg water) * 1e6 [ppm]
    double ionMassKg = 0.0;
    double waterMassKg = 0.0;
    for (int i = 0; i < numberOfComponents; i++) {
      if (Math.abs(getComponent(i).getIonicCharge()) > 0.5) {
        ionMassKg += getComponent(i).getNumberOfMolesInPhase() * getComponent(i).getMolarMass();
      }
      if (getComponent(i).getComponentName().equals("water")) {
        waterMassKg += getComponent(i).getNumberOfMolesInPhase() * getComponent(i).getMolarMass();
      }
    }
    double totalMass = ionMassKg + waterMassKg;
    double S = (totalMass > 1e-20) ? ionMassKg / totalMass : 0.0; // mass fraction of salts

    // Brine density correction: rho_brine ≈ rho_water + S * (800 + 0.4*S*1e6)
    // Simplified Rowe-Chou type: rho_b = rhoW / (1 - S*(0.668 + 0.44*S + 1e-6*S*TC*TC))
    // This approximation gives ~1020 kg/m³ for 3 wt% NaCl at 25°C, ~1200 kg/m³ for 26 wt%
    double rhoBrine;
    if (S > 1e-10) {
      // McCain (1991) correlation adapted for NaCl brines
      rhoBrine = rhoW + 668.0 * S + 440.0 * S * S;
    } else {
      rhoBrine = rhoW;
    }

    // Pressure correction: approximate compressibility of brine
    // dp in bar from atmospheric; compressibility ~4.5e-5 /bar for water, less for brine
    double pressureBar = pressure;
    if (pressureBar > 1.013) {
      double kappa = 4.5e-5 / (1.0 + 3.0 * S); // brine is less compressible
      rhoBrine *= (1.0 + kappa * (pressureBar - 1.013));
    }

    return rhoBrine;
  }

  /** {@inheritDoc} */
  @Override
  public double molarVolume(double pressure, double temperature, double A, double B, PhaseType pt)
      throws IsNaNException, TooManyIterationsException {
    return getMass() / getDensity() / numberOfMolesInPhase;
  }

  /** {@inheritDoc} */
  @Override
  public double getHresTP() {
    return getGresTP() + temperature * getSresTP();
  }

  /** {@inheritDoc} */
  @Override
  public double getHresdP() {
    return 0.0;
  }

  /** {@inheritDoc} */
  @Override
  public double getSresTV() {
    return getSresTP();
  }

  /** {@inheritDoc} */
  @Override
  public double getSresTP() {
    double temperatureStep = getTemperatureDerivativeStep();
    double gPlus = getExcessGibbsEnergyAtTemperature(temperature + temperatureStep);
    double gMinus = getExcessGibbsEnergyAtTemperature(temperature - temperatureStep);
    restoreActivityCoefficientsAtCurrentTemperature();
    return -(gPlus - gMinus) / (2.0 * temperatureStep);
  }

  /** {@inheritDoc} */
  @Override
  public double getGresTP() {
    return getExcessGibbsEnergyAtTemperature(temperature);
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * Calculates the excess heat capacity via finite-difference temperature derivatives of the Pitzer excess Gibbs
   * energy, including temperature-dependent Debye-Huckel and binary interaction parameters when available.
   * </p>
   */
  @Override
  public double getCpres() {
    double temperatureStep = getTemperatureDerivativeStep();
    double gPlus = getExcessGibbsEnergyAtTemperature(temperature + temperatureStep);
    double g0 = getExcessGibbsEnergyAtTemperature(temperature);
    double gMinus = getExcessGibbsEnergyAtTemperature(temperature - temperatureStep);
    restoreActivityCoefficientsAtCurrentTemperature();
    double secondDerivative = (gPlus - 2.0 * g0 + gMinus) / (temperatureStep * temperatureStep);
    return -temperature * secondDerivative;
  }

  /** {@inheritDoc} */
  @Override
  public double getCvres() {
    return getCpres();
  }

  /** {@inheritDoc} */
  @Override
  public double getCp() {
    // Calculate the ideal heat-capacity contribution on a molar basis using
    // the pure-component liquid heat capacities, then scale by the phase mole
    // count and add the residual term. This mirrors the default Phase
    // implementation without multiplying by the phase moles twice.
    double cpIdeal = 0.0;
    for (int i = 0; i < numberOfComponents; i++) {
      cpIdeal += componentArray[i].getx() * componentArray[i].getPureComponentCpLiquid(temperature);
    }
    return cpIdeal * numberOfMolesInPhase + getCpres();
  }

  /** {@inheritDoc} */
  @Override
  public double getEnthalpy() {
    return getHID() * numberOfMolesInPhase + getHresTP();
  }

  /** {@inheritDoc} */
  @Override
  public double getEntropy() {
    double idealEntropy = 0.0;
    double idealMixingEntropy = 0.0;
    for (int i = 0; i < numberOfComponents; i++) {
      idealEntropy += componentArray[i].getx() * componentArray[i].getIdEntropy(temperature);
      if (componentArray[i].getx() > 1e-100) {
        idealMixingEntropy += -R * componentArray[i].getx() * Math.log(componentArray[i].getx());
      }
    }
    return idealEntropy * numberOfMolesInPhase + idealMixingEntropy * numberOfMolesInPhase + getSresTP();
  }

  /** {@inheritDoc} */
  @Override
  public double getCv() {
    return getCp();
  }

  /**
   * Calculates the Pitzer osmotic coefficient for the aqueous phase.
   *
   * @return osmotic coefficient of water on the molality scale
   */
  private double getPitzerOsmoticCoefficient() {
    if (!parametersLoaded) {
      loadParametersFromDatabase();
    }
    validateParameterCoverageOncePerState();

    double ionicStrength = getIonicStrength();
    double sqrtIonicStrength = Math.sqrt(ionicStrength);
    double aPhi = getDebyeHuckelAphi(temperature);
    double b = 1.2;

    double sumMolalities = 0.0;
    double zSum = 0.0;
    for (int i = 0; i < numberOfComponents; i++) {
      double charge = getComponent(i).getIonicCharge();
      if (Math.abs(charge) > 0.5) {
        double molality = getComponent(i).getMolality(this);
        sumMolalities += molality;
        zSum += Math.abs(charge) * molality;
      } else if (hasNeutralInteractions && isNeutralSolute(i)) {
        sumMolalities += getComponent(i).getMolality(this);
      }
    }

    if (sumMolalities < 1.0e-12) {
      return 1.0;
    }

    double fPhi = -aPhi * Math.pow(ionicStrength, 1.5) / (1.0 + b * sqrtIonicStrength);
    double binarySum = 0.0;
    for (int cation = 0; cation < numberOfComponents; cation++) {
      double cationCharge = getComponent(cation).getIonicCharge();
      if (cationCharge <= 0.0) {
        continue;
      }
      double cationMolality = getComponent(cation).getMolality(this);
      for (int anion = 0; anion < numberOfComponents; anion++) {
        double anionCharge = getComponent(anion).getIonicCharge();
        if (anionCharge >= 0.0) {
          continue;
        }
        double anionMolality = getComponent(anion).getMolality(this);
        double bPhi = getBphi(cation, anion, ionicStrength);
        double c = getCphiij(cation, anion, temperature) / (2.0 * Math.sqrt(Math.abs(cationCharge * anionCharge)));
        binarySum += cationMolality * anionMolality * (bPhi + zSum * c);
      }
    }

    double thetaPsiSum = getThetaPsiOsmoticContribution(ionicStrength, aPhi);
    double neutralSum = hasNeutralInteractions ? getNeutralPitzerOsmoticContribution(temperature) : 0.0;
    return 1.0 + (2.0 / sumMolalities) * (fPhi + binarySum + thetaPsiSum + neutralSum);
  }

  /**
   * Calculates the Pitzer Debye-Huckel A-phi coefficient.
   *
   * @param temperatureKelvin temperature in K
   * @return Debye-Huckel A-phi coefficient
   */
  private double getDebyeHuckelAphi(double temperatureKelvin) {
    double temperatureCelsius = temperatureKelvin - 273.15;
    double densityWater = 999.83 + 5.0948e-2 * temperatureCelsius - 7.5722e-3 * temperatureCelsius * temperatureCelsius
        + 3.8907e-5 * Math.pow(temperatureCelsius, 3.0) - 1.2e-7 * Math.pow(temperatureCelsius, 4.0);
    if (temperatureCelsius > 100.0) {
      double deltaTemperature = temperatureCelsius - 100.0;
      densityWater = 958.0 - 1.08 * deltaTemperature - 0.0028 * deltaTemperature * deltaTemperature;
    }
    if (densityWater < 700.0) {
      densityWater = 700.0;
    }

    double dielectricConstantWater = 87.740 - 0.40008 * temperatureCelsius
        + 9.398e-4 * temperatureCelsius * temperatureCelsius - 1.410e-6 * Math.pow(temperatureCelsius, 3.0);
    if (dielectricConstantWater < 20.0) {
      dielectricConstantWater = 20.0;
    }

    return 1.4006e6 * Math.sqrt(densityWater / 1000.0) / Math.pow(dielectricConstantWater * temperatureKelvin, 1.5);
  }

  /**
   * Calculates the Pitzer B-phi interaction function for an ion pair.
   *
   * @param cation cation component index
   * @param anion anion component index
   * @param ionicStrength ionic strength in mol/kg
   * @return B-phi interaction function
   */
  private double getBphi(int cation, int anion, double ionicStrength) {
    double cationCharge = Math.abs(getComponent(cation).getIonicCharge());
    double anionCharge = Math.abs(getComponent(anion).getIonicCharge());
    double alpha1 = cationCharge >= 1.5 && anionCharge >= 1.5 ? 1.4 : 2.0;
    double sqrtIonicStrength = Math.sqrt(ionicStrength);
    double bPhi = getBeta0ij(cation, anion, temperature)
        + getBeta1ij(cation, anion, temperature) * Math.exp(-alpha1 * sqrtIonicStrength);
    boolean isTwoTwo = cationCharge >= 1.5 && anionCharge >= 1.5;
    if (isTwoTwo || nonTwoTwoBeta2Active) {
      double beta2Value = getBeta2ij(cation, anion, temperature);
      if (Math.abs(beta2Value) > 1.0e-20) {
        bPhi += beta2Value * Math.exp(-getPitzerAlpha2(cation, anion) * sqrtIonicStrength);
      }
    }
    return bPhi;
  }

  /**
   * Calculates theta and psi contributions to the osmotic coefficient.
   *
   * @param ionicStrength molal ionic strength in mol/kg
   * @param aPhi Pitzer Debye-Huckel osmotic coefficient parameter
   * @return theta, psi, and nonsymmetric electrostatic contribution to the Pitzer osmotic-coefficient sum
   */
  private double getThetaPsiOsmoticContribution(double ionicStrength, double aPhi) {
    double thetaPsiSum = 0.0;
    double[] electrostaticMixing = null;
    boolean hasElectrostaticMixing = hasUnequalChargeSameSignPair();
    for (int cation1 = 0; cation1 < numberOfComponents; cation1++) {
      if (getComponent(cation1).getIonicCharge() <= 0.0) {
        continue;
      }
      double molalityCation1 = getComponent(cation1).getMolality(this);
      for (int cation2 = cation1 + 1; cation2 < numberOfComponents; cation2++) {
        if (getComponent(cation2).getIonicCharge() <= 0.0) {
          continue;
        }
        double molalityCation2 = getComponent(cation2).getMolality(this);
        double electrostaticPhi = 0.0;
        double cation2Charge = getComponent(cation2).getIonicCharge();
        if (hasElectrostaticMixing && Math.abs(getComponent(cation1).getIonicCharge() - cation2Charge) >= 1.0e-12) {
          if (electrostaticMixing == null) {
            electrostaticMixing = new double[2];
          }
          PitzerElectrostaticMixing.calculate(getComponent(cation1).getIonicCharge(), cation2Charge, ionicStrength,
              aPhi, electrostaticMixing);
          electrostaticPhi = electrostaticMixing[0] + ionicStrength * electrostaticMixing[1];
        }
        thetaPsiSum += molalityCation1 * molalityCation2
            * (getThetaij(cation1, cation2, temperature) + electrostaticPhi);
        for (int anion = 0; anion < numberOfComponents; anion++) {
          if (getComponent(anion).getIonicCharge() >= 0.0) {
            continue;
          }
          thetaPsiSum += molalityCation1 * molalityCation2 * getComponent(anion).getMolality(this)
              * getPsiijk(cation1, cation2, anion, temperature);
        }
      }
    }

    for (int anion1 = 0; anion1 < numberOfComponents; anion1++) {
      if (getComponent(anion1).getIonicCharge() >= 0.0) {
        continue;
      }
      double molalityAnion1 = getComponent(anion1).getMolality(this);
      for (int anion2 = anion1 + 1; anion2 < numberOfComponents; anion2++) {
        if (getComponent(anion2).getIonicCharge() >= 0.0) {
          continue;
        }
        double molalityAnion2 = getComponent(anion2).getMolality(this);
        double electrostaticPhi = 0.0;
        double anion2Charge = getComponent(anion2).getIonicCharge();
        if (hasElectrostaticMixing && Math.abs(getComponent(anion1).getIonicCharge() - anion2Charge) >= 1.0e-12) {
          if (electrostaticMixing == null) {
            electrostaticMixing = new double[2];
          }
          PitzerElectrostaticMixing.calculate(getComponent(anion1).getIonicCharge(), anion2Charge, ionicStrength, aPhi,
              electrostaticMixing);
          electrostaticPhi = electrostaticMixing[0] + ionicStrength * electrostaticMixing[1];
        }
        thetaPsiSum += molalityAnion1 * molalityAnion2 * (getThetaij(anion1, anion2, temperature) + electrostaticPhi);
        for (int cation = 0; cation < numberOfComponents; cation++) {
          if (getComponent(cation).getIonicCharge() <= 0.0) {
            continue;
          }
          thetaPsiSum += molalityAnion1 * molalityAnion2 * getComponent(cation).getMolality(this)
              * getPsiijk(anion1, anion2, cation, temperature);
        }
      }
    }
    return thetaPsiSum;
  }

  /**
   * Calculates total excess Gibbs energy at a trial temperature without changing phase composition.
   *
   * @param trialTemperature temperature in K for the derivative evaluation
   * @return total excess Gibbs energy in J for this phase
   */
  private double getExcessGibbsEnergyAtTemperature(double trialTemperature) {
    return getExcessGibbsEnergy(this, numberOfComponents, trialTemperature, pressure, getType());
  }

  /**
   * Restores component activity coefficients at the current phase temperature after derivative calls.
   */
  private void restoreActivityCoefficientsAtCurrentTemperature() {
    getExcessGibbsEnergyAtTemperature(temperature);
  }

  /**
   * Gets the centered finite-difference step used for temperature derivatives.
   *
   * @return temperature derivative step in K
   */
  private double getTemperatureDerivativeStep() {
    double step = Math.max(1.0e-3, Math.min(0.05, temperature * 1.0e-4));
    if (temperature - step <= 1.0) {
      step = Math.max(1.0e-6, 0.5 * (temperature - 1.0));
    }
    return step;
  }

  /**
   * Finds active ions of one charge sign.
   *
   * @param positive {@code true} for cations, {@code false} for anions
   * @return component indexes whose molality exceeds the active-ion threshold
   */
  private List<Integer> activeIonIndexes(boolean positive) {
    List<Integer> indexes = new ArrayList<Integer>();
    double activeMoles = ACTIVE_ION_MOLALITY * getSolventWeight();
    for (int i = 0; i < numberOfComponents; i++) {
      double charge = getComponent(i).getIonicCharge();
      boolean requestedSign = positive ? charge > 0.0 : charge < 0.0;
      if (requestedSign && isPrimarySaltCoverageSpecies(componentName(i))
          && getComponent(i).getNumberOfMolesInPhase() > activeMoles) {
        indexes.add(i);
      }
    }
    return indexes;
  }

  /**
   * Reports whether an ion belongs to the primary salt-parameter coverage gate.
   *
   * <p>
   * Acid-base species generated inside the reaction solver are excluded because their intermediate trial molalities do
   * not represent a stable input-brine topology. Their reaction and interaction coverage requires a separate
   * model-specific reaction-dataset gate.
   * </p>
   *
   * @param componentName component name
   * @return {@code true} when the component is covered by this primary-salt audit
   */
  private static boolean isPrimarySaltCoverageSpecies(String componentName) {
    return !"H3O+".equals(componentName) && !"OH-".equals(componentName) && !"HCO3-".equals(componentName)
        && !"CO3--".equals(componentName);
  }

  /**
   * Converts component indexes to component names.
   *
   * @param indexes component indexes
   * @return component names
   */
  private List<String> componentNames(List<Integer> indexes) {
    List<String> names = new ArrayList<String>();
    for (int index : indexes) {
      names.add(componentName(index));
    }
    return names;
  }

  /**
   * Adds absent theta and psi definitions for all same-sign pairs and opposite-sign ions.
   *
   * @param sameSignIndexes indexes of ions with one charge sign
   * @param oppositeSignIndexes indexes of ions with the opposite charge sign
   * @param missingTheta destination for missing theta keys
   * @param missingPsi destination for missing psi keys
   */
  private void findMissingMixedInteractions(List<Integer> sameSignIndexes, List<Integer> oppositeSignIndexes,
      List<String> missingTheta, List<String> missingPsi) {
    for (int first = 0; first < sameSignIndexes.size(); first++) {
      String firstName = componentName(sameSignIndexes.get(first));
      for (int second = first + 1; second < sameSignIndexes.size(); second++) {
        String secondName = componentName(sameSignIndexes.get(second));
        String thetaKey = pairKey(firstName, secondName);
        if (!definedThetaPairs.contains(thetaKey)) {
          missingTheta.add(thetaKey);
        }
        for (int opposite : oppositeSignIndexes) {
          String tupleKey = psiKey(firstName, secondName, componentName(opposite));
          if (!definedPsiTuples.contains(tupleKey)) {
            missingPsi.add(tupleKey);
          }
        }
      }
    }
  }

  /**
   * Computes a no-allocation fingerprint of the active primary-salt topology.
   *
   * @return deterministic active-topology fingerprint
   */
  private long activeTopologyFingerprint() {
    long fingerprint = 0xcbf29ce484222325L;
    double activeMoles = ACTIVE_ION_MOLALITY * getSolventWeight();
    for (int i = 0; i < numberOfComponents; i++) {
      double charge = getComponent(i).getIonicCharge();
      if (charge != 0.0 && isPrimarySaltCoverageSpecies(componentName(i))
          && getComponent(i).getNumberOfMolesInPhase() > activeMoles) {
        fingerprint ^= i + 1L;
        fingerprint *= 0x100000001b3L;
        fingerprint ^= charge > 0.0 ? 1L : 2L;
        fingerprint *= 0x100000001b3L;
      }
    }
    return fingerprint;
  }

  /**
   * Gets a component name for parameter-key construction.
   *
   * @param index component index
   * @return component name
   */
  private String componentName(int index) {
    return getComponent(index).getComponentName();
  }

  /**
   * Creates an order-independent binary or theta parameter key.
   *
   * @param first first species name
   * @param second second species name
   * @return canonical parameter key
   */
  private static String pairKey(String first, String second) {
    return first.compareTo(second) <= 0 ? first + "|" + second : second + "|" + first;
  }

  /**
   * Creates a psi key while retaining the role of the same-sign and opposite-sign species.
   *
   * @param first first same-sign species
   * @param second second same-sign species
   * @param opposite opposite-sign species
   * @return canonical ternary parameter key
   */
  private static String psiKey(String first, String second, String opposite) {
    return pairKey(first, second) + "|" + opposite;
  }

  /** Stores a sparse temperature function and enables the fast gate. */
  private void setTemperatureFunction(long key, PitzerTemperatureFunction function) {
    ensureTemperatureFunctions();
    temperatureFunctions.put(key, function);
    hasTemperatureFunctions = true;
  }

  /**
   * Audits neutral Pitzer families against the active opt-in topology.
   *
   * <p>
   * Once any neutral family is configured, lambda is required for every active neutral-neutral pair (with repetition)
   * and neutral-ion pair, and zeta for every active neutral-cation-anion tuple. Mu and eta are audited across their
   * active topology only when that higher-order family is present in the selected dataset. An explicitly defined zero
   * counts as a definition.
   * </p>
   *
   * @return immutable coverage diagnostic
   */
  public PitzerNeutralParameterCoverage auditNeutralPitzerParameterCoverage() {
    ensureDefinitionSets();
    ensureNeutralInteractions();
    List<Integer> neutrals = new ArrayList<Integer>();
    List<Integer> cations = new ArrayList<Integer>();
    List<Integer> anions = new ArrayList<Integer>();
    List<String> neutralNames = new ArrayList<String>();
    double activeMoles = ACTIVE_ION_MOLALITY * getSolventWeight();
    for (int index = 0; index < numberOfComponents; index++) {
      if (getComponent(index).getNumberOfMolesInPhase() <= activeMoles) {
        continue;
      }
      double charge = getComponent(index).getIonicCharge();
      if (Math.abs(charge) < 0.5) {
        if (isNeutralSolute(index)) {
          neutrals.add(index);
          neutralNames.add(componentName(index));
        }
      } else if (charge > 0.0) {
        cations.add(index);
      } else {
        anions.add(index);
      }
    }

    List<String> missingLambda = new ArrayList<String>();
    List<String> missingZeta = new ArrayList<String>();
    List<String> missingMu = new ArrayList<String>();
    List<String> missingEta = new ArrayList<String>();
    for (int neutralPosition = 0; neutralPosition < neutrals.size(); neutralPosition++) {
      int neutral = neutrals.get(neutralPosition);
      for (int secondNeutral = neutralPosition; secondNeutral < neutrals.size(); secondNeutral++) {
        addMissingPairIfAbsent(NEUTRAL_FAMILY_LAMBDA, neutral, neutrals.get(secondNeutral), missingLambda);
      }
      for (int cation : cations) {
        addMissingPairIfAbsent(NEUTRAL_FAMILY_LAMBDA, neutral, cation, missingLambda);
      }
      for (int anion : anions) {
        addMissingPairIfAbsent(NEUTRAL_FAMILY_LAMBDA, neutral, anion, missingLambda);
      }
      for (int cation : cations) {
        for (int anion : anions) {
          addMissingTripleIfAbsent(NEUTRAL_FAMILY_ZETA, neutral, cation, anion, missingZeta);
        }
      }
    }

    if (hasNeutralFamily(NEUTRAL_FAMILY_MU)) {
      for (int first = 0; first < neutrals.size(); first++) {
        for (int second = first; second < neutrals.size(); second++) {
          for (int third = second; third < neutrals.size(); third++) {
            addMissingTripleIfAbsent(NEUTRAL_FAMILY_MU, neutrals.get(first), neutrals.get(second), neutrals.get(third),
                missingMu);
          }
        }
      }
    }
    if (hasNeutralFamily(NEUTRAL_FAMILY_ETA)) {
      for (int neutral : neutrals) {
        addMissingEtaPairs(neutral, cations, missingEta);
        addMissingEtaPairs(neutral, anions, missingEta);
      }
    }

    return new PitzerNeutralParameterCoverage(parameterDatasetId, neutralNames, missingLambda, missingZeta, missingMu,
        missingEta);
  }

  /** Throws a deterministic diagnostic when the active neutral topology is incomplete. */
  public void requireCompleteNeutralPitzerParameterCoverage() {
    PitzerNeutralParameterCoverage coverage = auditNeutralPitzerParameterCoverage();
    if (!coverage.isComplete()) {
      throw new IllegalStateException(coverage.formatDiagnostic());
    }
  }

  private void addMissingEtaPairs(int neutral, List<Integer> ions, List<String> missing) {
    for (int first = 0; first < ions.size(); first++) {
      for (int second = first + 1; second < ions.size(); second++) {
        addMissingTripleIfAbsent(NEUTRAL_FAMILY_ETA, neutral, ions.get(first), ions.get(second), missing);
      }
    }
  }

  private void addMissingPairIfAbsent(int family, int first, int second, List<String> missing) {
    if (!neutralInteractions.containsKey(pairTemperatureKey(family, first, second))) {
      missing.add(pairKey(componentName(first), componentName(second)));
    }
  }

  private void addMissingTripleIfAbsent(int family, int first, int second, int third, List<String> missing) {
    if (!neutralInteractions.containsKey(tripleTemperatureKey(family, first, second, third))) {
      missing.add(psiKey(componentName(first), componentName(second), componentName(third)));
    }
  }

  private boolean hasNeutralFamily(int family) {
    for (PitzerNeutralInteraction interaction : neutralInteractions.values()) {
      if (interaction.getFamily() == family) {
        return true;
      }
    }
    return false;
  }

  private void setNeutralInteraction(int family, int[] componentIndexes, PitzerTemperatureFunction function) {
    ensureNeutralInteractions();
    long key = componentIndexes.length == 2 ? pairTemperatureKey(family, componentIndexes[0], componentIndexes[1])
        : tripleTemperatureKey(family, componentIndexes[0], componentIndexes[1], componentIndexes[2]);
    neutralInteractions.put(key, new PitzerNeutralInteraction(family, componentIndexes, function));
    hasNeutralInteractions = true;
    for (int index = 0; index < numberOfComponents; index++) {
      ((ComponentGePitzer) componentArray[index]).setNeutralPitzerInteractionsActive(true);
    }
    neutralParameterCoverageValidated = false;
    invalidateCoverageCache();
  }

  private double getNeutralInteractionValue(int family, int first, int second, int third, double temperature) {
    if (!hasNeutralInteractions) {
      return 0.0;
    }
    ensureNeutralInteractions();
    long key = third < 0 ? pairTemperatureKey(family, first, second)
        : tripleTemperatureKey(family, first, second, third);
    PitzerNeutralInteraction interaction = neutralInteractions.get(key);
    return interaction == null ? 0.0 : interaction.valueAt(temperature);
  }

  private void requireNeutralSolute(int index, String family) {
    requireComponentIndex(index);
    if (!isNeutralSolute(index)) {
      throw new IllegalArgumentException("Pitzer " + family + " requires a neutral non-water solute");
    }
  }

  private void requireIon(int index, String family) {
    requireComponentIndex(index);
    if (Math.abs(getComponent(index).getIonicCharge()) < 0.5) {
      throw new IllegalArgumentException("Pitzer " + family + " requires an ionic species");
    }
  }

  private void requireChargeSign(int index, int sign, String role) {
    requireComponentIndex(index);
    if (sign * getComponent(index).getIonicCharge() <= 0.0) {
      throw new IllegalArgumentException("Pitzer " + role + " has the wrong charge sign");
    }
  }

  private void requireComponentIndex(int index) {
    if (index < 0 || index >= numberOfComponents) {
      throw new IllegalArgumentException("Pitzer component index out of range: " + index);
    }
  }

  private boolean isNeutralSolute(int index) {
    return Math.abs(getComponent(index).getIonicCharge()) < 0.5 && !"water".equalsIgnoreCase(componentName(index))
        && (!excludeHydrocarbonsFromNeutralPitzerTopology || !getComponent(index).isHydrocarbon());
  }

  private void validateNeutralParameterCoverageOncePerState() {
    if (hasNeutralInteractions && !neutralParameterCoverageValidated) {
      requireCompleteNeutralPitzerParameterCoverage();
      neutralParameterCoverageValidated = true;
    }
  }

  /** Ensures sparse neutral-interaction state exists for older serialized objects. */
  private void ensureNeutralInteractions() {
    if (neutralInteractions == null) {
      neutralInteractions = new HashMap<Long, PitzerNeutralInteraction>();
      hasNeutralInteractions = false;
    }
  }

  /** Marks a package-owned coherent parameter dataset as complete without loading the legacy table. */
  void markManualParameterDatasetLoaded() {
    parametersLoaded = true;
    parameterCoverageValidated = false;
    neutralParameterCoverageValidated = false;
  }

  /**
   * Configures whether the automatic catalog selection omits EOS-role hydrocarbons from its neutral topology.
   *
   * @param exclude {@code true} for automatic aqueous-role selection, {@code false} for explicit neutral datasets
   */
  void setExcludeHydrocarbonsFromNeutralPitzerTopology(boolean exclude) {
    excludeHydrocarbonsFromNeutralPitzerTopology = exclude;
    neutralParameterCoverageValidated = false;
  }

  /** Enables PHREEQC's common binary-derivative and C0/Cphi terms for a package-owned dataset. */
  void enablePhreeqcCommonIonTerms() {
    phreeqcCommonIonTermsActive = true;
  }

  /** Returns a binary temperature function without key allocation for legacy datasets. */
  private PitzerTemperatureFunction getPairTemperatureFunction(int family, int first, int second) {
    if (!hasTemperatureFunctions) {
      return null;
    }
    ensureTemperatureFunctions();
    return temperatureFunctions.get(pairTemperatureKey(family, first, second));
  }

  /** Returns a ternary temperature function without key allocation for legacy datasets. */
  private PitzerTemperatureFunction getTripleTemperatureFunction(int family, int first, int second, int third) {
    if (!hasTemperatureFunctions) {
      return null;
    }
    ensureTemperatureFunctions();
    return temperatureFunctions.get(tripleTemperatureKey(family, first, second, third));
  }

  /** Ensures temperature-function state exists for objects read from older serialized forms. */
  private void ensureTemperatureFunctions() {
    if (temperatureFunctions == null) {
      temperatureFunctions = new HashMap<Long, PitzerTemperatureFunction>();
      hasTemperatureFunctions = false;
    }
  }

  /** Builds an order-independent allocation-free binary temperature-function key. */
  private static long pairTemperatureKey(int family, int first, int second) {
    int low = Math.min(first, second);
    int high = Math.max(first, second);
    return ((long) family << 54) | ((long) low << 18) | (long) high;
  }

  /** Builds a permutation-independent allocation-free ternary temperature-function key. */
  private static long tripleTemperatureKey(int family, int first, int second, int third) {
    int low = Math.min(first, Math.min(second, third));
    int high = Math.max(first, Math.max(second, third));
    int middle = first + second + third - low - high;
    return ((long) family << 54) | ((long) low << 36) | ((long) middle << 18) | (long) high;
  }

  /** Ensures definition sets exist for objects read from older serialized forms. */
  private void ensureDefinitionSets() {
    if (definedBinaryPairs == null) {
      definedBinaryPairs = new HashSet<String>();
    }
    if (definedThetaPairs == null) {
      definedThetaPairs = new HashSet<String>();
    }
    if (definedPsiTuples == null) {
      definedPsiTuples = new HashSet<String>();
    }
    if (parameterDatasetId == null || parameterDatasetId.trim().isEmpty()) {
      parameterDatasetId = DEFAULT_PARAMETER_DATASET_ID;
    }
  }

  /** Invalidates the cached coverage result after a parameter-definition change. */
  private void invalidateCoverageCache() {
    parameterDefinitionRevision++;
    cachedCoverageFingerprint = 0L;
    cachedCoverageRevision = Long.MIN_VALUE;
    cachedCoverage = null;
    parameterCoverageValidated = false;
  }

  /** Validates coverage once after each level-zero phase-state initialization. */
  private void validateParameterCoverageOncePerState() {
    if (!parameterCoverageValidated) {
      if (hasMixedPrimarySaltTopology()) {
        requireCompletePitzerParameterCoverage();
      }
      parameterCoverageValidated = true;
    }
  }

  /**
   * Detects whether the state needs same-sign and ternary mixed-salt parameters.
   *
   * <p>
   * Legacy single-cation/single-anion calculations retain their established binary behavior. The explicit coverage API
   * can still audit their binary row.
   * </p>
   *
   * @return {@code true} when more than one active cation or anion is present
   */
  private boolean hasMixedPrimarySaltTopology() {
    int cations = 0;
    int anions = 0;
    double activeMoles = ACTIVE_ION_MOLALITY * getSolventWeight();
    for (int i = 0; i < numberOfComponents; i++) {
      double charge = getComponent(i).getIonicCharge();
      if (charge == 0.0 || !isPrimarySaltCoverageSpecies(componentName(i))
          || getComponent(i).getNumberOfMolesInPhase() <= activeMoles) {
        continue;
      }
      if (charge > 0.0) {
        cations++;
      } else {
        anions++;
      }
      if (cations > 1 || anions > 1) {
        return true;
      }
    }
    return false;
  }

  /**
   * Reads one sparse ternary parameter.
   *
   * @param first first component index
   * @param second second component index
   * @param third third component index
   * @return defined value, or zero when the tuple has no allocated row
   */
  private double getPsiValue(int first, int second, int third) {
    if (psi == null || psi[first] == null || psi[first][second] == null) {
      return 0.0;
    }
    return psi[first][second][third];
  }

  /**
   * Writes one sparse ternary parameter, allocating only its required row.
   *
   * @param first first component index
   * @param second second component index
   * @param third third component index
   * @param value parameter value
   */
  private void setPsiValue(int first, int second, int third, double value) {
    if (psi == null) {
      psi = new double[componentArray.length][][];
    }
    if (psi[first] == null) {
      psi[first] = new double[componentArray.length][];
    }
    if (psi[first][second] == null) {
      psi[first][second] = new double[componentArray.length];
    }
    psi[first][second][third] = value;
  }

  /**
   * Deep-copies a two-dimensional parameter matrix, preserving unallocated sparse rows.
   *
   * @param source source matrix
   * @return independent matrix copy
   */
  private static double[][] cloneMatrix(double[][] source) {
    if (source == null) {
      return null;
    }
    double[][] copy = source.clone();
    for (int i = 0; i < source.length; i++) {
      copy[i] = source[i] == null ? null : source[i].clone();
    }
    return copy;
  }

  /**
   * Deep-copies a three-dimensional parameter tensor, preserving unallocated sparse rows.
   *
   * @param source source tensor
   * @return independent tensor copy
   */
  private static double[][][] cloneTensor(double[][][] source) {
    if (source == null) {
      return null;
    }
    double[][][] copy = source.clone();
    for (int i = 0; i < source.length; i++) {
      copy[i] = cloneMatrix(source[i]);
    }
    return copy;
  }

  /**
   * Detaches copy-on-write parameter storage before a setter mutates it.
   *
   * <p>
   * Read-only clones share the large psi tensor without copying. The first parameter mutation creates independent
   * matrices and a tensor, preserving clone independence without charging ordinary flash clones.
   * </p>
   */
  private void ensureOwnedParameterStorage() {
    if (!parameterStorageShared) {
      return;
    }
    beta0 = cloneMatrix(beta0);
    beta1 = cloneMatrix(beta1);
    cphi = cloneMatrix(cphi);
    beta2 = cloneMatrix(beta2);
    theta = cloneMatrix(theta);
    psi = cloneTensor(psi);
    beta0T1 = cloneMatrix(beta0T1);
    beta0T2 = cloneMatrix(beta0T2);
    beta1T1 = cloneMatrix(beta1T1);
    beta1T2 = cloneMatrix(beta1T2);
    cphiT1 = cloneMatrix(cphiT1);
    cphiT2 = cloneMatrix(cphiT2);
    parameterStorageShared = false;
  }
}
