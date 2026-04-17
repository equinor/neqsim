package neqsim.physicalproperties.interfaceproperties.solidadsorption;

import neqsim.thermo.system.SystemInterface;

/**
 * Sips (Langmuir-Freundlich) adsorption isotherm implementation.
 *
 * <p>
 * The Sips isotherm combines Langmuir saturation behavior with Freundlich heterogeneity:
 * </p>
 * $$q = q_m \cdot \frac{(K_S \cdot P)^{1/n}}{1 + (K_S \cdot P)^{1/n}}$$
 *
 * <p>
 * where:
 * </p>
 * <ul>
 * <li>$q$ = amount adsorbed (mol/kg)</li>
 * <li>$q_m$ = maximum adsorption capacity (mol/kg)</li>
 * <li>$K_S$ = Sips affinity constant (1/bar)</li>
 * <li>$n$ = heterogeneity parameter (dimensionless)</li>
 * <li>$P$ = partial pressure (bar)</li>
 * </ul>
 *
 * <p>
 * Special cases:
 * </p>
 * <ul>
 * <li>When $n = 1$: reduces to Langmuir isotherm</li>
 * <li>At low pressure: reduces to Freundlich isotherm</li>
 * <li>At high pressure: approaches saturation $q_m$</li>
 * </ul>
 *
 * <p>
 * Temperature dependence uses the van't Hoff equation for the affinity constant:
 * </p>
 * $$K_S(T) = K_S(T_{ref}) \cdot \exp\left(\frac{\Delta H_{ads}}{R} \cdot \left(\frac{1}{T} -
 * \frac{1}{T_{ref}}\right)\right)$$
 *
 * @author ESOL
 * @version 1.0
 */
public class SipsAdsorption extends AbstractAdsorptionModel {

  /** Serialization version UID. */
  private static final long serialVersionUID = 1006L;

  /** Maximum adsorption capacity for each component (mol/kg). */
  private double[] qmax;

  /** Sips affinity constants for each component (1/bar). */
  private double[] kSips;

  /** Heterogeneity parameters for each component (dimensionless). */
  private double[] nSips;

  /** Reference temperature for parameters (K). */
  private double[] tempRef;

  /** Heat of adsorption for temperature correction (J/mol). */
  private double[] heatOfAdsorption;

  /** Whether parameters have been manually set (skip DB loading). */
  private boolean parametersManuallySet = false;

  /**
   * Default constructor.
   */
  public SipsAdsorption() {
  }

  /**
   * Constructor with thermodynamic system.
   *
   * @param system the thermodynamic system
   */
  public SipsAdsorption(SystemInterface system) {
    super(system);
    initializeParameterArrays();
  }

  /**
   * Initialize parameter arrays for all components.
   */
  private void initializeParameterArrays() {
    if (system != null) {
      int numComp = system.getPhase(0).getNumberOfComponents();
      qmax = new double[numComp];
      kSips = new double[numComp];
      nSips = new double[numComp];
      tempRef = new double[numComp];
      heatOfAdsorption = new double[numComp];
      for (int i = 0; i < numComp; i++) {
        tempRef[i] = 298.15;
        nSips[i] = 1.0;
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public IsothermType getIsothermType() {
    return IsothermType.SIPS;
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
          try {
            qmax[comp] = Double.parseDouble(dataSet.getString("qmax"));
            kSips[comp] = Double.parseDouble(dataSet.getString("K_sips"));
            nSips[comp] = Double.parseDouble(dataSet.getString("n_sips"));
            tempRef[comp] = Double.parseDouble(dataSet.getString("TempRef"));
            heatOfAdsorption[comp] = Double.parseDouble(dataSet.getString("dH_ads"));
          } catch (Exception ex) {
            // Estimate from DRA parameters
            double eps = Double.parseDouble(dataSet.getString("eps"));
            double z0 = Double.parseDouble(dataSet.getString("z0"));
            double molarMass = system.getPhase(phaseNum).getComponent(comp).getMolarMass();
            qmax[comp] = z0 * 1000.0 / molarMass;
            kSips[comp] = Math.exp(eps / (R * 298.15));
            nSips[comp] = 1.2;
            tempRef[comp] = 298.15;
            heatOfAdsorption[comp] = -eps * 1000.0;
          }
          logger.info("Sips parameters loaded for " + componentName + ": qmax=" + qmax[comp]
              + ", Ks=" + kSips[comp] + ", n=" + nSips[comp]);
        } else {
          setDefaultParameters(comp);
        }
      } catch (Exception ex) {
        logger.info("Component not found in adsorption DB: " + componentName);
        setDefaultParameters(comp);
      }
    }
  }

  /**
   * Set default Sips parameters for a component.
   *
   * @param comp the component index
   */
  private void setDefaultParameters(int comp) {
    qmax[comp] = 5.0;
    kSips[comp] = 0.1;
    nSips[comp] = 1.2;
    tempRef[comp] = 298.15;
    heatOfAdsorption[comp] = -20000.0;
    logger.info("Using default Sips parameters for component " + comp);
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

      // Temperature-corrected Sips constant
      double kCorr = kSips[comp]
          * Math.exp(-heatOfAdsorption[comp] / R * (1.0 / temperature - 1.0 / tempRef[comp]));

      // Sips isotherm: q = qm * (Ks*P)^(1/n) / (1 + (Ks*P)^(1/n))
      if (partialPressure > 0 && nSips[comp] > 0) {
        double kpn = Math.pow(kCorr * partialPressure, 1.0 / nSips[comp]);
        surfaceExcess[comp] = qmax[comp] * kpn / (1.0 + kpn);
      } else {
        surfaceExcess[comp] = 0.0;
      }
    }

    calculateSurfaceExcessMolFractions();
    calculated = true;
  }

  /**
   * Calculate adsorption using extended Sips for multi-component systems.
   *
   * <p>
   * Extended Sips equation for competitive adsorption:
   * </p>
   * $$q_i = q_{m,i} \cdot \frac{(K_{S,i} \cdot P_i)^{1/n_i}}{1 + \sum_j (K_{S,j} \cdot
   * P_j)^{1/n_j}}$$
   *
   * @param phaseNum the phase number
   */
  public void calcExtendedSips(int phaseNum) {
    initializeArrays();
    if (!parametersManuallySet) {
      initializeParameterArrays();
      loadParameters(phaseNum);
    }

    double temperature = system.getPhase(phaseNum).getTemperature();
    int numComp = system.getPhase(phaseNum).getNumberOfComponents();

    // Calculate denominator sum
    double denomSum = 1.0;
    double[] kCorr = new double[numComp];
    double[] kpn = new double[numComp];

    for (int comp = 0; comp < numComp; comp++) {
      double partialPressure = getPartialPressure(phaseNum, comp);
      kCorr[comp] = kSips[comp]
          * Math.exp(-heatOfAdsorption[comp] / R * (1.0 / temperature - 1.0 / tempRef[comp]));

      if (partialPressure > 0 && nSips[comp] > 0) {
        kpn[comp] = Math.pow(kCorr[comp] * partialPressure, 1.0 / nSips[comp]);
        denomSum += kpn[comp];
      }
    }

    for (int comp = 0; comp < numComp; comp++) {
      surfaceExcess[comp] = qmax[comp] * kpn[comp] / denomSum;
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
   * Get the Sips affinity constant for a component.
   *
   * @param component the component index
   * @return Ks in 1/bar
   */
  public double getKSips(int component) {
    return kSips[component];
  }

  /**
   * Set the Sips affinity constant for a component.
   *
   * @param component the component index
   * @param value Ks in 1/bar
   */
  public void setKSips(int component, double value) {
    kSips[component] = value;
    calculated = false;
    parametersManuallySet = true;
  }

  /**
   * Get the heterogeneity parameter for a component.
   *
   * @param component the component index
   * @return n (dimensionless)
   */
  public double getNSips(int component) {
    return nSips[component];
  }

  /**
   * Set the heterogeneity parameter for a component.
   *
   * @param component the component index
   * @param value n (dimensionless)
   */
  public void setNSips(int component, double value) {
    nSips[component] = value;
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
