package neqsim.physicalproperties.interfaceproperties.solidadsorption;

import neqsim.thermo.system.SystemInterface;

/**
 * Freundlich adsorption isotherm implementation.
 *
 * <p>
 * The Freundlich isotherm is an empirical model for heterogeneous surfaces:
 * </p>
 * $$q = K_F \cdot P^{1/n}$$
 *
 * <p>
 * where:
 * </p>
 * <ul>
 * <li>$q$ = amount adsorbed (mol/kg)</li>
 * <li>$K_F$ = Freundlich capacity constant (mol/kg/bar^(1/n))</li>
 * <li>$n$ = Freundlich intensity parameter (dimensionless, n &gt; 1)</li>
 * <li>$P$ = partial pressure or fugacity (bar)</li>
 * </ul>
 *
 * <p>
 * Characteristics:
 * </p>
 * <ul>
 * <li>No saturation plateau (unlimited adsorption at high pressure)</li>
 * <li>Heterogeneous surface energy distribution</li>
 * <li>Reduces to Henry's law when n = 1</li>
 * <li>Higher n indicates more favorable adsorption</li>
 * </ul>
 *
 * <p>
 * Temperature dependence is modeled with an Arrhenius-type equation:
 * </p>
 * $$K_F(T) = K_F(T_{ref}) \cdot \exp\left(\frac{\Delta H_{ads}}{R} \cdot \left(\frac{1}{T} -
 * \frac{1}{T_{ref}}\right)\right)$$
 *
 * @author ESOL
 * @version 1.0
 */
public class FreundlichAdsorption extends AbstractAdsorptionModel {

  /** Serialization version UID. */
  private static final long serialVersionUID = 1005L;

  /** Freundlich capacity constants for each component (mol/kg/bar^(1/n)). */
  private double[] kFreundlich;

  /** Freundlich intensity parameters for each component (dimensionless). */
  private double[] nFreundlich;

  /** Reference temperature for parameters (K). */
  private double[] tempRef;

  /** Heat of adsorption for temperature correction (J/mol). */
  private double[] heatOfAdsorption;

  /**
   * Default constructor.
   */
  public FreundlichAdsorption() {
    super();
  }

  /**
   * Constructor with thermodynamic system.
   *
   * @param system the thermodynamic system
   */
  public FreundlichAdsorption(SystemInterface system) {
    super(system);
    initializeParameterArrays();
  }

  /**
   * Initialize parameter arrays for all components.
   */
  private void initializeParameterArrays() {
    if (system != null) {
      int numComp = system.getPhase(0).getNumberOfComponents();
      kFreundlich = new double[numComp];
      nFreundlich = new double[numComp];
      tempRef = new double[numComp];
      heatOfAdsorption = new double[numComp];
      for (int i = 0; i < numComp; i++) {
        tempRef[i] = 298.15;
        nFreundlich[i] = 1.0;
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public IsothermType getIsothermType() {
    return IsothermType.FREUNDLICH;
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
            kFreundlich[comp] = Double.parseDouble(dataSet.getString("K_freundlich"));
            nFreundlich[comp] = Double.parseDouble(dataSet.getString("n_freundlich"));
            tempRef[comp] = Double.parseDouble(dataSet.getString("TempRef"));
            heatOfAdsorption[comp] = Double.parseDouble(dataSet.getString("dH_ads"));
          } catch (Exception ex) {
            // Estimate from DRA parameters
            double eps = Double.parseDouble(dataSet.getString("eps"));
            double z0 = Double.parseDouble(dataSet.getString("z0"));
            double molarMass = system.getPhase(phaseNum).getComponent(comp).getMolarMass();
            kFreundlich[comp] = z0 * 1000.0 / molarMass * 0.5;
            nFreundlich[comp] = 1.0 + eps / 5.0;
            tempRef[comp] = 298.15;
            heatOfAdsorption[comp] = -eps * 1000.0;
          }
          logger.info("Freundlich parameters loaded for " + componentName + ": KF="
              + kFreundlich[comp] + ", n=" + nFreundlich[comp]);
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
   * Set default Freundlich parameters for a component.
   *
   * @param comp the component index
   */
  private void setDefaultParameters(int comp) {
    kFreundlich[comp] = 2.0;
    nFreundlich[comp] = 2.0;
    tempRef[comp] = 298.15;
    heatOfAdsorption[comp] = -20000.0;
    logger.info("Using default Freundlich parameters for component " + comp);
  }

  /** {@inheritDoc} */
  @Override
  public void calcAdsorption(int phaseNum) {
    initializeArrays();
    initializeParameterArrays();
    loadParameters(phaseNum);

    double temperature = system.getPhase(phaseNum).getTemperature();
    int numComp = system.getPhase(phaseNum).getNumberOfComponents();

    for (int comp = 0; comp < numComp; comp++) {
      double partialPressure = getPartialPressure(phaseNum, comp);

      // Temperature-corrected Freundlich constant
      double kCorr = kFreundlich[comp]
          * Math.exp(-heatOfAdsorption[comp] / R * (1.0 / temperature - 1.0 / tempRef[comp]));

      // Freundlich isotherm: q = KF * P^(1/n)
      if (partialPressure > 0 && nFreundlich[comp] > 0) {
        surfaceExcess[comp] = kCorr * Math.pow(partialPressure, 1.0 / nFreundlich[comp]);
      } else {
        surfaceExcess[comp] = 0.0;
      }
    }

    calculateSurfaceExcessMolFractions();
    calculated = true;
  }

  /**
   * Get the Freundlich capacity constant for a component.
   *
   * @param component the component index
   * @return KF in mol/kg/bar^(1/n)
   */
  public double getKFreundlich(int component) {
    return kFreundlich[component];
  }

  /**
   * Set the Freundlich capacity constant for a component.
   *
   * @param component the component index
   * @param value KF in mol/kg/bar^(1/n)
   */
  public void setKFreundlich(int component, double value) {
    kFreundlich[component] = value;
    calculated = false;
  }

  /**
   * Get the Freundlich intensity parameter for a component.
   *
   * @param component the component index
   * @return n (dimensionless)
   */
  public double getNFreundlich(int component) {
    return nFreundlich[component];
  }

  /**
   * Set the Freundlich intensity parameter for a component.
   *
   * @param component the component index
   * @param value n (dimensionless, should be &gt; 1 for favorable adsorption)
   */
  public void setNFreundlich(int component, double value) {
    nFreundlich[component] = value;
    calculated = false;
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
  }

  /**
   * Get the reference temperature for parameters.
   *
   * @param component the component index
   * @return reference temperature in K
   */
  public double getTempRef(int component) {
    return tempRef[component];
  }

  /**
   * Set the reference temperature for parameters.
   *
   * @param component the component index
   * @param value reference temperature in K
   */
  public void setTempRef(int component, double value) {
    tempRef[component] = value;
    calculated = false;
  }

  /**
   * Check if adsorption is favorable (n &gt; 1).
   *
   * @param component the component index
   * @return true if adsorption is favorable
   */
  public boolean isFavorableAdsorption(int component) {
    return nFreundlich[component] > 1.0;
  }
}
