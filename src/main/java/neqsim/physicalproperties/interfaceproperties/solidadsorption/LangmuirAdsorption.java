package neqsim.physicalproperties.interfaceproperties.solidadsorption;

import neqsim.thermo.system.SystemInterface;

/**
 * Langmuir adsorption isotherm implementation.
 *
 * <p>
 * The Langmuir isotherm models monolayer adsorption on a homogeneous surface with the equation:
 * </p>
 * $$q = q_{max} \cdot \frac{K \cdot P}{1 + K \cdot P}$$
 *
 * <p>
 * where:
 * </p>
 * <ul>
 * <li>$q$ = amount adsorbed (mol/kg)</li>
 * <li>$q_{max}$ = maximum adsorption capacity (mol/kg)</li>
 * <li>$K$ = Langmuir equilibrium constant (1/bar)</li>
 * <li>$P$ = partial pressure (bar)</li>
 * </ul>
 *
 * <p>
 * The model assumes:
 * </p>
 * <ul>
 * <li>Homogeneous surface with identical adsorption sites</li>
 * <li>No lateral interactions between adsorbed molecules</li>
 * <li>Monolayer coverage only</li>
 * </ul>
 *
 * @author ESOL
 * @version 1.0
 */
public class LangmuirAdsorption extends AbstractAdsorptionModel {

  /** Serialization version UID. */
  private static final long serialVersionUID = 1002L;

  /** Maximum adsorption capacity for each component (mol/kg). */
  private double[] qmax;

  /** Langmuir equilibrium constants (1/bar). */
  private double[] kLangmuir;

  /** Reference temperature for parameters (K). */
  private double[] tempRef;

  /** Heat of adsorption for temperature correction (J/mol). */
  private double[] heatOfAdsorption;

  /** Whether parameters have been manually set (skip DB loading). */
  private boolean parametersManuallySet = false;

  /**
   * Default constructor.
   */
  public LangmuirAdsorption() {
  }

  /**
   * Constructor with thermodynamic system.
   *
   * @param system the thermodynamic system
   */
  public LangmuirAdsorption(SystemInterface system) {
    super(system);
    initializeParameterArrays();
  }

  /**
   * Initialize parameter arrays.
   */
  private void initializeParameterArrays() {
    if (system != null) {
      int numComp = system.getPhase(0).getNumberOfComponents();
      qmax = new double[numComp];
      kLangmuir = new double[numComp];
      tempRef = new double[numComp];
      heatOfAdsorption = new double[numComp];
      for (int i = 0; i < numComp; i++) {
        tempRef[i] = 298.15;
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public IsothermType getIsothermType() {
    return IsothermType.LANGMUIR;
  }

  /** {@inheritDoc} */
  @Override
  protected void loadParameters(int phaseNum) {
    int numComp = system.getPhase(phaseNum).getNumberOfComponents();

    for (int comp = 0; comp < numComp; comp++) {
      String componentName = system.getPhase(phaseNum).getComponent(comp).getComponentName();
      try (neqsim.util.database.NeqSimDataBase database = new neqsim.util.database.NeqSimDataBase();
          java.sql.ResultSet dataSet =
              database.getResultSet("SELECT * FROM adsorptionparameters WHERE name='"
                  + componentName + "' AND Solid='" + solidMaterial + "'")) {

        if (dataSet.next()) {
          // Try to read Langmuir-specific parameters if available
          try {
            qmax[comp] = Double.parseDouble(dataSet.getString("qmax"));
            kLangmuir[comp] = Double.parseDouble(dataSet.getString("K_langmuir"));
            tempRef[comp] = Double.parseDouble(dataSet.getString("TempRef"));
            heatOfAdsorption[comp] = Double.parseDouble(dataSet.getString("dH_ads"));
          } catch (Exception ex) {
            // Fall back to DRA parameters and convert
            double eps = Double.parseDouble(dataSet.getString("eps"));
            double z0 = Double.parseDouble(dataSet.getString("z0"));
            // Estimate Langmuir parameters from DRA
            qmax[comp] = z0 * 1000.0 / system.getPhase(0).getComponent(comp).getMolarMass();
            kLangmuir[comp] = Math.exp(eps / (R * 298.15));
            tempRef[comp] = 298.15;
            heatOfAdsorption[comp] = -eps * 1000.0;
          }
          logger.info("Langmuir parameters loaded for " + componentName + ": qmax=" + qmax[comp]
              + ", K=" + kLangmuir[comp]);
        } else {
          // Use default parameters
          setDefaultParameters(comp);
        }
      } catch (Exception ex) {
        logger.info("Component not found in adsorption DB: " + componentName);
        setDefaultParameters(comp);
      }
    }
  }

  /**
   * Set default Langmuir parameters for a component.
   *
   * @param comp the component index
   */
  private void setDefaultParameters(int comp) {
    qmax[comp] = 5.0;
    kLangmuir[comp] = 0.1;
    tempRef[comp] = 298.15;
    heatOfAdsorption[comp] = -20000.0;
    logger.info("Using default Langmuir parameters for component " + comp);
  }

  /** {@inheritDoc} */
  @Override
  public void calcAdsorption(int phaseNum) {
    initializeArrays();
    if (!parametersManuallySet) {
      initializeParameterArrays();
      loadParameters(phaseNum);
    }

    double temperature = system.getPhase(phaseNum).getTemperature();
    int numComp = system.getPhase(phaseNum).getNumberOfComponents();

    for (int comp = 0; comp < numComp; comp++) {
      double partialPressure = getPartialPressure(phaseNum, comp);

      // Temperature-corrected Langmuir constant using van't Hoff equation
      double kCorr = kLangmuir[comp]
          * Math.exp(-heatOfAdsorption[comp] / R * (1.0 / temperature - 1.0 / tempRef[comp]));

      // Langmuir isotherm equation
      double coverage = kCorr * partialPressure / (1.0 + kCorr * partialPressure);
      surfaceExcess[comp] = qmax[comp] * coverage;
    }

    calculateSurfaceExcessMolFractions();
    calculated = true;
  }

  /**
   * Calculate adsorption using extended Langmuir for multi-component systems.
   *
   * <p>
   * Uses the extended Langmuir equation for competitive adsorption:
   * </p>
   * $$q_i = q_{max,i} \cdot \frac{K_i \cdot P_i}{1 + \sum_j K_j \cdot P_j}$$
   *
   * @param phaseNum the phase number
   */
  public void calcExtendedLangmuir(int phaseNum) {
    initializeArrays();
    if (!parametersManuallySet) {
      initializeParameterArrays();
      loadParameters(phaseNum);
    }

    double temperature = system.getPhase(phaseNum).getTemperature();
    int numComp = system.getPhase(phaseNum).getNumberOfComponents();

    // Calculate denominator sum first
    double denomSum = 1.0;
    double[] kCorr = new double[numComp];
    double[] partialPressures = new double[numComp];

    for (int comp = 0; comp < numComp; comp++) {
      partialPressures[comp] = getPartialPressure(phaseNum, comp);
      kCorr[comp] = kLangmuir[comp]
          * Math.exp(-heatOfAdsorption[comp] / R * (1.0 / temperature - 1.0 / tempRef[comp]));
      denomSum += kCorr[comp] * partialPressures[comp];
    }

    // Calculate individual coverages
    for (int comp = 0; comp < numComp; comp++) {
      double coverage = kCorr[comp] * partialPressures[comp] / denomSum;
      surfaceExcess[comp] = qmax[comp] * coverage;
    }

    calculateSurfaceExcessMolFractions();
    calculated = true;
  }

  /**
   * Get the maximum adsorption capacity for a component.
   *
   * @param component the component index
   * @return qmax in mol/kg
   */
  public double getQmax(int component) {
    return qmax[component];
  }

  /**
   * Set the maximum adsorption capacity for a component.
   *
   * @param component the component index
   * @param value qmax in mol/kg
   */
  public void setQmax(int component, double value) {
    qmax[component] = value;
    calculated = false;
    parametersManuallySet = true;
  }

  /**
   * Get the Langmuir constant for a component.
   *
   * @param component the component index
   * @return K in 1/bar
   */
  public double getKLangmuir(int component) {
    return kLangmuir[component];
  }

  /**
   * Set the Langmuir constant for a component.
   *
   * @param component the component index
   * @param value K in 1/bar
   */
  public void setKLangmuir(int component, double value) {
    kLangmuir[component] = value;
    calculated = false;
    parametersManuallySet = true;
  }

  /**
   * Get the heat of adsorption for a component.
   *
   * @param component the component index
   * @return heat of adsorption in J/mol (negative for exothermic)
   */
  public double getHeatOfAdsorption(int component) {
    return heatOfAdsorption[component];
  }

  /**
   * Set the heat of adsorption for a component.
   *
   * @param component the component index
   * @param value heat of adsorption in J/mol
   */
  public void setHeatOfAdsorption(int component, double value) {
    heatOfAdsorption[component] = value;
    calculated = false;
    parametersManuallySet = true;
  }

  /**
   * Calculate the fractional surface coverage (theta) for a component.
   *
   * @param component the component index
   * @return fractional coverage (0 to 1)
   */
  public double getCoverage(int component) {
    if (!calculated) {
      throw new IllegalStateException("Adsorption not calculated. Call calcAdsorption() first.");
    }
    if (qmax[component] < 1e-20) {
      return 0.0;
    }
    return surfaceExcess[component] / qmax[component];
  }
}
