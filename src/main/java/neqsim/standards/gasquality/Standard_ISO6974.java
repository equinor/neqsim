package neqsim.standards.gasquality;

import java.util.HashMap;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;

/**
 * Implementation of ISO 6974 - Natural gas - Determination of composition and associated
 * uncertainty by gas chromatography.
 *
 * <p>
 * ISO 6974 consists of multiple parts:
 * </p>
 * <ul>
 * <li>Part 1: General guidelines and calculation of composition</li>
 * <li>Part 2: Uncertainty calculations (propagation of GC measurement uncertainty)</li>
 * <li>Part 3: Measurement using two integrating systems</li>
 * <li>Part 4: Using two columns via a single detector</li>
 * <li>Part 5: Isothermal method for nitrogen, CO2, C1-C5 and C6+</li>
 * <li>Part 6: Using three capillary columns</li>
 * </ul>
 *
 * <p>
 * This implementation focuses on Parts 1 and 2: normalising the reported GC composition and
 * propagating measurement uncertainties through to derived physical properties (calorific value,
 * Wobbe index, relative density). The uncertainty propagation follows the GUM (Guide to the
 * expression of Uncertainty in Measurement) approach outlined in ISO 6974-2.
 * </p>
 *
 * <p>
 * Typical expanded uncertainties (k=2, 95% confidence) for pipeline-quality natural gas by GC:
 * </p>
 * <ul>
 * <li>Methane: +/- 0.15 mol%</li>
 * <li>Ethane: +/- 0.03 mol%</li>
 * <li>Propane: +/- 0.02 mol%</li>
 * <li>CO2, N2: +/- 0.03 mol%</li>
 * <li>C4+: +/- 0.01 mol%</li>
 * </ul>
 *
 * @author ESOL
 * @version 1.0
 */
public class Standard_ISO6974 extends GasChromotograpyhBase {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  private static final Logger logger = LogManager.getLogger(Standard_ISO6974.class);

  /** Default coverage factor for expanded uncertainty (k=2 gives ~95% confidence). */
  private double coverageFactor = 2.0;

  /**
   * Standard uncertainties (1 sigma) for each component in mol fraction. These are typical values
   * for a well-maintained GC; users should override with their own calibration data.
   */
  private Map<String, Double> componentStdUncertainty = new HashMap<String, Double>();

  /**
   * Normalised composition in mol fraction. After normalisation the sum is exactly 1.0.
   */
  private Map<String, Double> normalisedComposition = new HashMap<String, Double>();

  /** Expanded uncertainties of the normalised composition in mol%. */
  private Map<String, Double> expandedUncertainties = new HashMap<String, Double>();

  /** Normalisation factor applied (sum of raw mol fractions before normalisation). */
  private double normalisationFactor = 1.0;

  /** Whether normalisation was required (raw sum differs from 1 by more than tolerance). */
  private boolean normalisationApplied = false;

  /** Tolerance for raw composition sum deviating from 1.0. */
  private double normalisationTolerance = 0.005;

  /** Calculated expanded uncertainty of superior calorific value in MJ/m3. */
  private double uncertaintyGCV = 0.0;

  /** Calculated expanded uncertainty of Wobbe index in MJ/m3. */
  private double uncertaintyWobbe = 0.0;

  /** Calculated expanded uncertainty of relative density (dimensionless). */
  private double uncertaintyRelativeDensity = 0.0;

  /** ISO 6976 instance for property calculations. */
  private Standard_ISO6976 iso6976;

  /**
   * Constructor for Standard_ISO6974.
   *
   * @param thermoSystem a {@link neqsim.thermo.system.SystemInterface} object
   */
  public Standard_ISO6974(SystemInterface thermoSystem) {
    super(thermoSystem);
    setName("ISO6974");
    this.standardDescription = "ISO 6974 - GC composition with uncertainty";
    iso6976 = new Standard_ISO6976(thermoSystem);
    iso6976.setReferenceType("volume");
    initDefaultUncertainties();
  }

  /**
   * Initializes default standard uncertainties for common natural gas components. Values are
   * approximate 1-sigma values in mol fraction. Users should set their own values from calibration
   * certificates via {@link #setComponentUncertainty(String, double)}.
   */
  private void initDefaultUncertainties() {
    componentStdUncertainty.put("methane", 0.0015);
    componentStdUncertainty.put("ethane", 0.00030);
    componentStdUncertainty.put("propane", 0.00020);
    componentStdUncertainty.put("i-butane", 0.00010);
    componentStdUncertainty.put("n-butane", 0.00010);
    componentStdUncertainty.put("i-pentane", 0.00005);
    componentStdUncertainty.put("n-pentane", 0.00005);
    componentStdUncertainty.put("n-hexane", 0.00005);
    componentStdUncertainty.put("nitrogen", 0.00030);
    componentStdUncertainty.put("CO2", 0.00030);
    componentStdUncertainty.put("H2S", 0.00005);
    componentStdUncertainty.put("water", 0.00010);
    componentStdUncertainty.put("hydrogen", 0.00010);
    componentStdUncertainty.put("helium", 0.00005);
  }

  /**
   * Sets the standard uncertainty (1 sigma) for a specific component.
   *
   * @param componentName name of the component
   * @param stdUncertaintyMolFraction standard uncertainty in mol fraction
   */
  public void setComponentUncertainty(String componentName, double stdUncertaintyMolFraction) {
    componentStdUncertainty.put(componentName, stdUncertaintyMolFraction);
  }

  /**
   * Sets the coverage factor for expanded uncertainty.
   *
   * @param k coverage factor (default 2.0 for ~95% confidence)
   */
  public void setCoverageFactor(double k) {
    this.coverageFactor = k;
  }

  /**
   * Sets the normalisation tolerance.
   *
   * @param tol tolerance as fraction (default 0.005, i.e., 0.5%)
   */
  public void setNormalisationTolerance(double tol) {
    this.normalisationTolerance = tol;
  }

  /** {@inheritDoc} */
  @Override
  public void calculate() {
    // Step 1: Normalise the composition per ISO 6974-1
    normaliseComposition();

    // Step 2: Calculate physical properties using ISO 6976
    iso6976.setThermoSystem(thermoSystem);
    iso6976.calculate();

    // Step 3: Propagate uncertainties per ISO 6974-2
    propagateUncertainties();
  }

  /**
   * Normalises the raw GC composition so the mole fractions sum to exactly 1.0.
   *
   * <p>
   * Per ISO 6974-1 clause 8: when the sum of the reported mole fractions differs from 1.0, the
   * results should be normalised. The normalisation factor is the sum of raw mole fractions. If the
   * deviation exceeds the tolerance, the analysis should be investigated.
   * </p>
   */
  private void normaliseComposition() {
    double rawSum = 0.0;
    int nComp = thermoSystem.getPhase(0).getNumberOfComponents();
    for (int i = 0; i < nComp; i++) {
      rawSum += thermoSystem.getPhase(0).getComponent(i).getz();
    }

    normalisationFactor = rawSum;
    normalisationApplied = Math.abs(rawSum - 1.0) > normalisationTolerance;

    normalisedComposition.clear();
    for (int i = 0; i < nComp; i++) {
      String name = thermoSystem.getPhase(0).getComponent(i).getName();
      double rawZ = thermoSystem.getPhase(0).getComponent(i).getz();
      double normZ = (rawSum > 0.0) ? rawZ / rawSum : rawZ;
      normalisedComposition.put(name, normZ);
    }
  }

  /**
   * Propagates measurement uncertainties through to physical property calculations using a
   * numerical perturbation (finite difference) approach consistent with the GUM framework.
   *
   * <p>
   * For each component i, the sensitivity coefficient for property P is estimated by central
   * difference. The combined standard uncertainty is calculated by root-sum-squares of
   * sensitivity-weighted component uncertainties.
   * </p>
   */
  private void propagateUncertainties() {
    try {
      double sumSqGCV = 0.0;
      double sumSqWobbe = 0.0;
      double sumSqRD = 0.0;

      int nComp = thermoSystem.getPhase(0).getNumberOfComponents();

      for (int i = 0; i < nComp; i++) {
        String name = thermoSystem.getPhase(0).getComponent(i).getName();
        double z = thermoSystem.getPhase(0).getComponent(i).getz();
        double u = getStdUncertainty(name);

        if (u <= 0.0 || z <= 0.0) {
          continue;
        }

        // Perturbation step
        double delta = u;
        if (delta > z * 0.5) {
          delta = z * 0.5;
        }

        // Forward perturbation
        SystemInterface pertSys = thermoSystem.clone();
        pertSys.getPhase(0).getComponent(i).setz(z + delta);
        renormaliseAfterPerturbation(pertSys, i, z + delta);
        Standard_ISO6976 pertCalc = new Standard_ISO6976(pertSys);
        pertCalc.setReferenceType("volume");
        pertCalc.calculate();
        double gcvPlus = pertCalc.getValue("SuperiorCalorificValue");
        double wobbePlus = pertCalc.getValue("SuperiorWobbeIndex");
        double rdPlus = pertCalc.getValue("RelativeDensity");

        // Backward perturbation
        pertSys = thermoSystem.clone();
        pertSys.getPhase(0).getComponent(i).setz(z - delta);
        renormaliseAfterPerturbation(pertSys, i, z - delta);
        pertCalc = new Standard_ISO6976(pertSys);
        pertCalc.setReferenceType("volume");
        pertCalc.calculate();
        double gcvMinus = pertCalc.getValue("SuperiorCalorificValue");
        double wobbeMinus = pertCalc.getValue("SuperiorWobbeIndex");
        double rdMinus = pertCalc.getValue("RelativeDensity");

        // Sensitivity coefficients (central difference)
        double sensGCV = (gcvPlus - gcvMinus) / (2.0 * delta);
        double sensWobbe = (wobbePlus - wobbeMinus) / (2.0 * delta);
        double sensRD = (rdPlus - rdMinus) / (2.0 * delta);

        sumSqGCV += sensGCV * sensGCV * u * u;
        sumSqWobbe += sensWobbe * sensWobbe * u * u;
        sumSqRD += sensRD * sensRD * u * u;

        // Store expanded uncertainty for this component
        expandedUncertainties.put(name, coverageFactor * u * 100.0); // in mol%
      }

      uncertaintyGCV = coverageFactor * Math.sqrt(sumSqGCV);
      uncertaintyWobbe = coverageFactor * Math.sqrt(sumSqWobbe);
      uncertaintyRelativeDensity = coverageFactor * Math.sqrt(sumSqRD);

    } catch (Exception ex) {
      logger.error("Uncertainty propagation failed", ex);
    }
  }

  /**
   * Renormalises remaining components after perturbing component at index pertIndex.
   *
   * @param sys the perturbed system
   * @param pertIndex index of the perturbed component
   * @param newZ new mol fraction for the perturbed component
   */
  private void renormaliseAfterPerturbation(SystemInterface sys, int pertIndex, double newZ) {
    int nComp = sys.getPhase(0).getNumberOfComponents();
    double sumOthers = 0.0;
    for (int j = 0; j < nComp; j++) {
      if (j != pertIndex) {
        sumOthers += sys.getPhase(0).getComponent(j).getz();
      }
    }
    double remaining = 1.0 - newZ;
    if (sumOthers > 0.0 && remaining > 0.0) {
      double scale = remaining / sumOthers;
      for (int j = 0; j < nComp; j++) {
        if (j != pertIndex) {
          double oldZ = sys.getPhase(0).getComponent(j).getz();
          sys.getPhase(0).getComponent(j).setz(oldZ * scale);
        }
      }
    }
  }

  /**
   * Gets the standard uncertainty for a component.
   *
   * @param componentName name of the component
   * @return standard uncertainty in mol fraction, or a default small value
   */
  private double getStdUncertainty(String componentName) {
    Double u = componentStdUncertainty.get(componentName);
    return (u != null) ? u.doubleValue() : 0.00005;
  }

  /** {@inheritDoc} */
  @Override
  public double getValue(String returnParameter) {
    if ("normalisationFactor".equals(returnParameter)) {
      return normalisationFactor;
    }
    if ("uncertaintyGCV".equals(returnParameter) || "U_GCV".equals(returnParameter)) {
      return uncertaintyGCV;
    }
    if ("uncertaintyWobbe".equals(returnParameter) || "U_Wobbe".equals(returnParameter)) {
      return uncertaintyWobbe;
    }
    if ("uncertaintyRelativeDensity".equals(returnParameter) || "U_RD".equals(returnParameter)) {
      return uncertaintyRelativeDensity;
    }
    if ("coverageFactor".equals(returnParameter)) {
      return coverageFactor;
    }
    // Delegate component lookups to parent
    return super.getValue(returnParameter);
  }

  /** {@inheritDoc} */
  @Override
  public double getValue(String returnParameter, String returnUnit) {
    if (returnParameter.startsWith("uncertainty") || returnParameter.startsWith("U_")
        || "normalisationFactor".equals(returnParameter)
        || "coverageFactor".equals(returnParameter)) {
      return getValue(returnParameter);
    }
    return super.getValue(returnParameter, returnUnit);
  }

  /** {@inheritDoc} */
  @Override
  public String getUnit(String returnParameter) {
    if ("uncertaintyGCV".equals(returnParameter) || "U_GCV".equals(returnParameter)) {
      return "MJ/m3";
    }
    if ("uncertaintyWobbe".equals(returnParameter) || "U_Wobbe".equals(returnParameter)) {
      return "MJ/m3";
    }
    if ("uncertaintyRelativeDensity".equals(returnParameter) || "U_RD".equals(returnParameter)) {
      return "-";
    }
    if ("normalisationFactor".equals(returnParameter) || "coverageFactor".equals(returnParameter)) {
      return "-";
    }
    return super.getUnit(returnParameter);
  }

  /** {@inheritDoc} */
  @Override
  public boolean isOnSpec() {
    // Check that normalisation factor is within tolerance
    boolean normOk = Math.abs(normalisationFactor - 1.0) <= normalisationTolerance;
    // Check that methane uncertainty is reasonable (less than 0.30 mol% expanded)
    Double uMethane = expandedUncertainties.get("methane");
    boolean methaneOk = (uMethane == null) || (uMethane < 0.30);
    return normOk && methaneOk;
  }

  /**
   * Returns the normalised composition map.
   *
   * @return map of component name to normalised mol fraction
   */
  public Map<String, Double> getNormalisedComposition() {
    return new HashMap<String, Double>(normalisedComposition);
  }

  /**
   * Returns the expanded uncertainty map for composition.
   *
   * @return map of component name to expanded uncertainty in mol%
   */
  public Map<String, Double> getExpandedUncertainties() {
    return new HashMap<String, Double>(expandedUncertainties);
  }

  /**
   * Returns whether normalisation was applied.
   *
   * @return true if the raw sum deviated from 1.0 by more than the tolerance
   */
  public boolean isNormalisationApplied() {
    return normalisationApplied;
  }
}
