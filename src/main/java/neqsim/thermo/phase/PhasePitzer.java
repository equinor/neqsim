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

  private double[][] beta0;
  private double[][] beta1;
  private double[][] cphi;
  /** Second virial coefficient for 2-2 electrolytes (Harvie &amp; Weare 1984). */
  private double[][] beta2;
  /** Cation-cation or anion-anion mixing parameter theta (Harvie &amp; Weare 1984). */
  private double[][] theta;
  /** Ternary mixing parameter psi (cation-cation-anion or anion-anion-cation). */
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
  private Map<Long, PitzerTemperatureFunction> temperatureFunctions =
      new HashMap<Long, PitzerTemperatureFunction>();
  /** Fast gate that avoids map lookup for legacy parameter datasets. */
  private boolean hasTemperatureFunctions;
  /** Whether parameters have been loaded from database. */
  private boolean parametersLoaded = false;
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
    setPhysicalPropertyModel(PhysicalPropertyModel.SALT_WATER);
    int max = componentArray.length;
    beta0 = new double[max][max];
    beta1 = new double[max][max];
    cphi = new double[max][max];
    beta2 = new double[max][max];
    theta = new double[max][max];
    psi = new double[max][max][max];
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
    PhasePitzer clonedPhase = (PhasePitzer) super.clone();
    parameterStorageShared = true;
    clonedPhase.parameterStorageShared = true;
    clonedPhase.definedBinaryPairs = new HashSet<String>(definedBinaryPairs);
    clonedPhase.definedThetaPairs = new HashSet<String>(definedThetaPairs);
    clonedPhase.definedPsiTuples = new HashSet<String>(definedPsiTuples);
    clonedPhase.temperatureFunctions =
        new HashMap<Long, PitzerTemperatureFunction>(temperatureFunctions);
    clonedPhase.cachedCoverageFingerprint = 0L;
    clonedPhase.cachedCoverageRevision = Long.MIN_VALUE;
    clonedPhase.cachedCoverage = null;
    clonedPhase.parameterCoverageValidated = false;
    clonedPhase.unequalChargeSameSignPairState = unequalChargeSameSignPairState;
    return clonedPhase;
  }

  /** {@inheritDoc} */
  @Override
  public void init(double totalNumberOfMoles, int numberOfComponents, int initType, PhaseType pt, double beta) {
    if (initType == 0) {
      parameterCoverageValidated = false;
    }
    super.init(totalNumberOfMoles, numberOfComponents, initType, pt, beta);
  }

  /** {@inheritDoc} */
  @Override
  public void addComponent(String name, double moles, double molesInPhase, int compNumber) {
    super.addComponent(name, molesInPhase, compNumber);
    componentArray[compNumber] = new ComponentGePitzer(name, moles, molesInPhase, compNumber);
    unequalChargeSameSignPairState = 0;
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
   * + a5(1/T^2-1/Tr^2)}. This method defines beta0, beta1, and NeqSim Cphi as one
   * coherent binary tuple. Use {@link #setPhreeqcBinaryTemperatureCoefficients} when the source
   * fields are PHREEQC {@code -B0}, {@code -B1}, and {@code -C0}.
   * </p>
   *
   * @param i component index i
   * @param j component index j
   * @param referenceTemperature reference temperature in K
   * @param beta0Coefficients beta0 coefficients in six-term order
   * @param beta1Coefficients beta1 coefficients in six-term order
   * @param cphiCoefficients NeqSim Cphi coefficients in six-term order
   */
  public void setBinaryTemperatureCoefficients(int i, int j, double referenceTemperature,
      double[] beta0Coefficients, double[] beta1Coefficients, double[] cphiCoefficients) {
    PitzerTemperatureFunction beta0Function =
        new PitzerTemperatureFunction(referenceTemperature, beta0Coefficients);
    PitzerTemperatureFunction beta1Function =
        new PitzerTemperatureFunction(referenceTemperature, beta1Coefficients);
    PitzerTemperatureFunction cphiFunction =
        new PitzerTemperatureFunction(referenceTemperature, cphiCoefficients);
    setBinaryParameters(i, j, beta0Coefficients[0], beta1Coefficients[0], cphiCoefficients[0]);
    setTemperatureFunction(pairTemperatureKey(TEMPERATURE_BETA0, i, j), beta0Function);
    setTemperatureFunction(pairTemperatureKey(TEMPERATURE_BETA1, i, j), beta1Function);
    setTemperatureFunction(pairTemperatureKey(TEMPERATURE_CPHI, i, j), cphiFunction);
  }

  /**
   * Sets PHREEQC six-term {@code -B0}, {@code -B1}, and {@code -C0} functions.
   *
   * <p>
   * PHREEQC stores {@code -C0} as the Pitzer {@code Cphi} parameter and divides it by
   * {@code 2*sqrt(abs(zM*zX))} inside the thermodynamic sums. NeqSim applies the same normalization
   * to its Cphi value, so every {@code -C0} coefficient is passed unchanged. Callers must not
   * pre-divide or multiply PHREEQC values by the charge normalization.
   * </p>
   *
   * @param i component index i
   * @param j component index j
   * @param referenceTemperature reference temperature in K
   * @param beta0Coefficients PHREEQC {@code -B0} coefficients in {@code a0..a5} order
   * @param beta1Coefficients PHREEQC {@code -B1} coefficients in {@code a0..a5} order
   * @param c0Coefficients PHREEQC {@code -C0} coefficients in {@code a0..a5} order
   */
  public void setPhreeqcBinaryTemperatureCoefficients(int i, int j,
      double referenceTemperature, double[] beta0Coefficients, double[] beta1Coefficients,
      double[] c0Coefficients) {
    setBinaryTemperatureCoefficients(i, j, referenceTemperature, beta0Coefficients,
        beta1Coefficients, c0Coefficients);
  }

  /**
   * Sets a PHREEQC six-term beta2 temperature function.
   *
   * @param i component index i
   * @param j component index j
   * @param referenceTemperature reference temperature in K
   * @param coefficients six coefficients in PHREEQC order
   */
  public void setBeta2TemperatureCoefficients(int i, int j, double referenceTemperature,
      double[] coefficients) {
    PitzerTemperatureFunction function =
        new PitzerTemperatureFunction(referenceTemperature, coefficients);
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
  public void setThetaTemperatureCoefficients(int i, int j, double referenceTemperature,
      double[] coefficients) {
    PitzerTemperatureFunction function =
        new PitzerTemperatureFunction(referenceTemperature, coefficients);
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
  public void setPsiTemperatureCoefficients(int i, int j, int k, double referenceTemperature,
      double[] coefficients) {
    PitzerTemperatureFunction function =
        new PitzerTemperatureFunction(referenceTemperature, coefficients);
    setPsi(i, j, k, coefficients[0]);
    setTemperatureFunction(tripleTemperatureKey(TEMPERATURE_PSI, i, j, k), function);
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
    PitzerTemperatureFunction function =
        getPairTemperatureFunction(TEMPERATURE_CPHI, i, j);
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
   * Get beta2 parameter for 2-2 electrolytes.
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
   * Set beta2 parameter for 2-2 electrolytes.
   *
   * @param i component index i
   * @param j component index j
   * @param value beta2 value
   */
  public void setBeta2(int i, int j, double value) {
    ensureOwnedParameterStorage();
    beta2[i][j] = value;
    beta2[j][i] = value;
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
    PitzerTemperatureFunction function =
        getPairTemperatureFunction(TEMPERATURE_THETA, i, j);
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
    return psi[i][j][k];
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
    PitzerTemperatureFunction function =
        getTripleTemperatureFunction(TEMPERATURE_PSI, i, j, k);
    return function == null ? psi[i][j][k] : function.valueAt(temperature);
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
    psi[i][j][k] = value;
    psi[j][i][k] = value;
    psi[i][k][j] = value;
    psi[j][k][i] = value;
    psi[k][i][j] = value;
    psi[k][j][i] = value;
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
    return 1.0 + (2.0 / sumMolalities) * (fPhi + binarySum + thetaPsiSum);
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
    double cationCharge = getComponent(cation).getIonicCharge();
    double anionCharge = getComponent(anion).getIonicCharge();
    boolean is22 = Math.abs(cationCharge) >= 1.5 && Math.abs(anionCharge) >= 1.5;
    double sqrtIonicStrength = Math.sqrt(ionicStrength);
    double bPhi = getBeta0ij(cation, anion, temperature)
        + getBeta1ij(cation, anion, temperature) * Math.exp((is22 ? -1.4 : -2.0) * sqrtIonicStrength);
    if (is22) {
      bPhi += getBeta2ij(cation, anion, temperature) * Math.exp(-12.0 * sqrtIonicStrength);
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
        thetaPsiSum += molalityCation1 * molalityCation2 * (getThetaij(cation1, cation2, temperature) + electrostaticPhi);
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

  /** Returns a binary temperature function without key allocation for legacy datasets. */
  private PitzerTemperatureFunction getPairTemperatureFunction(int family, int first,
      int second) {
    if (!hasTemperatureFunctions) {
      return null;
    }
    ensureTemperatureFunctions();
    return temperatureFunctions.get(pairTemperatureKey(family, first, second));
  }

  /** Returns a ternary temperature function without key allocation for legacy datasets. */
  private PitzerTemperatureFunction getTripleTemperatureFunction(int family, int first,
      int second, int third) {
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
    return ((long) family << 54) | ((long) low << 36) | ((long) middle << 18)
        | (long) high;
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
   * Deep-copies a two-dimensional parameter matrix.
   *
   * @param source source matrix
   * @return independent matrix copy
   */
  private static double[][] cloneMatrix(double[][] source) {
    double[][] copy = source.clone();
    for (int i = 0; i < source.length; i++) {
      copy[i] = source[i].clone();
    }
    return copy;
  }

  /**
   * Deep-copies a three-dimensional parameter tensor.
   *
   * @param source source tensor
   * @return independent tensor copy
   */
  private static double[][][] cloneTensor(double[][][] source) {
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
