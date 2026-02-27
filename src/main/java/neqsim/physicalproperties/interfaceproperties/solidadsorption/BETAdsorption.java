package neqsim.physicalproperties.interfaceproperties.solidadsorption;

import neqsim.thermo.system.SystemInterface;

/**
 * BET (Brunauer-Emmett-Teller) adsorption isotherm implementation.
 *
 * <p>
 * The BET isotherm models multilayer adsorption with the equation:
 * </p>
 * $$\frac{P}{q(P_0 - P)} = \frac{1}{q_m \cdot C} + \frac{C - 1}{q_m \cdot C} \cdot \frac{P}{P_0}$$
 *
 * <p>
 * Rearranged for direct calculation:
 * </p>
 * $$q = \frac{q_m \cdot C \cdot (P/P_0)}{(1 - P/P_0)(1 - P/P_0 + C \cdot P/P_0)}$$
 *
 * <p>
 * where:
 * </p>
 * <ul>
 * <li>$q$ = amount adsorbed (mol/kg)</li>
 * <li>$q_m$ = monolayer capacity (mol/kg)</li>
 * <li>$C$ = BET constant (related to heat of adsorption)</li>
 * <li>$P$ = pressure (bar)</li>
 * <li>$P_0$ = saturation pressure (bar)</li>
 * </ul>
 *
 * <p>
 * BET constant C is related to heats of adsorption:
 * </p>
 * $$C \approx \exp\left(\frac{E_1 - E_L}{RT}\right)$$
 *
 * <p>
 * where $E_1$ is the heat of adsorption for the first layer and $E_L$ is the heat of liquefaction.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class BETAdsorption extends AbstractAdsorptionModel {

  /** Serialization version UID. */
  private static final long serialVersionUID = 1003L;

  /** Monolayer capacity for each component (mol/kg). */
  private double[] qMonolayer;

  /** BET constants for each component (dimensionless). */
  private double[] cBET;

  /** Saturation pressures for each component (bar). */
  private double[] pSat;

  /** Number of adsorbed layers (for modified BET). */
  private int maxLayers = Integer.MAX_VALUE;

  /**
   * Default constructor.
   */
  public BETAdsorption() {
    super();
  }

  /**
   * Constructor with thermodynamic system.
   *
   * @param system the thermodynamic system
   */
  public BETAdsorption(SystemInterface system) {
    super(system);
    initializeParameterArrays();
  }

  /**
   * Initialize parameter arrays.
   */
  private void initializeParameterArrays() {
    if (system != null) {
      int numComp = system.getPhase(0).getNumberOfComponents();
      qMonolayer = new double[numComp];
      cBET = new double[numComp];
      pSat = new double[numComp];
    }
  }

  /** {@inheritDoc} */
  @Override
  public IsothermType getIsothermType() {
    return IsothermType.BET;
  }

  /** {@inheritDoc} */
  @Override
  protected void loadParameters(int phaseNum) {
    int numComp = system.getPhase(phaseNum).getNumberOfComponents();

    // Calculate saturation pressures using thermodynamics
    calculateSaturationPressures(phaseNum);

    for (int comp = 0; comp < numComp; comp++) {
      String componentName = system.getPhase(phaseNum).getComponent(comp).getComponentName();
      try (neqsim.util.database.NeqSimDataBase database = new neqsim.util.database.NeqSimDataBase();
          java.sql.ResultSet dataSet =
              database.getResultSet("SELECT * FROM adsorptionparameters WHERE name='"
                  + componentName + "' AND Solid='" + solidMaterial + "'")) {

        if (dataSet.next()) {
          try {
            // Try to read BET-specific parameters
            qMonolayer[comp] = Double.parseDouble(dataSet.getString("qmax"));
            cBET[comp] = Double.parseDouble(dataSet.getString("C_BET"));
          } catch (Exception ex) {
            // Estimate from DRA/Langmuir parameters
            double eps = Double.parseDouble(dataSet.getString("eps"));
            double z0 = Double.parseDouble(dataSet.getString("z0"));

            // Estimate monolayer capacity from z0
            double molarMass = system.getPhase(phaseNum).getComponent(comp).getMolarMass();
            qMonolayer[comp] = z0 * 1000.0 / molarMass * 0.4;

            // Estimate BET constant from adsorption energy
            double temperature = system.getPhase(phaseNum).getTemperature();
            cBET[comp] = Math.exp(eps * 1000.0 / (R * temperature));
          }
          logger.info("BET parameters loaded for " + componentName + ": qm=" + qMonolayer[comp]
              + ", C=" + cBET[comp]);
        } else {
          setDefaultParameters(comp, phaseNum);
        }
      } catch (Exception ex) {
        logger.info("Component not found in adsorption DB: " + componentName);
        setDefaultParameters(comp, phaseNum);
      }
    }
  }

  /**
   * Calculate saturation pressures for each component using Lee-Kesler correlation.
   *
   * @param phaseNum the phase number
   */
  private void calculateSaturationPressures(int phaseNum) {
    double[] estimated = FluidPropertyEstimator.estimateAllSaturationPressures(system, phaseNum);
    System.arraycopy(estimated, 0, pSat, 0, estimated.length);
  }

  /**
   * Set default BET parameters for a component.
   *
   * @param comp the component index
   * @param phaseNum the phase number
   */
  private void setDefaultParameters(int comp, int phaseNum) {
    double molarMass = system.getPhase(phaseNum).getComponent(comp).getMolarMass();
    qMonolayer[comp] = betSurfaceArea * 1e-3 / (molarMass * 0.162);
    cBET[comp] = 100.0;
    logger.info("Using default BET parameters for component " + comp);
  }

  /** {@inheritDoc} */
  @Override
  public void calcAdsorption(int phaseNum) {
    initializeArrays();
    initializeParameterArrays();
    loadParameters(phaseNum);

    int numComp = system.getPhase(phaseNum).getNumberOfComponents();

    for (int comp = 0; comp < numComp; comp++) {
      double partialPressure = getPartialPressure(phaseNum, comp);
      double x = partialPressure / pSat[comp];

      if (x >= 1.0) {
        // At or above saturation - maximum adsorption
        surfaceExcess[comp] = qMonolayer[comp] * maxLayers;
      } else if (x <= 0) {
        surfaceExcess[comp] = 0.0;
      } else {
        // Standard BET equation
        double c = cBET[comp];
        double numerator = qMonolayer[comp] * c * x;
        double denominator = (1.0 - x) * (1.0 - x + c * x);

        if (maxLayers < Integer.MAX_VALUE) {
          // Modified BET for limited layers
          int n = maxLayers;
          double xn = Math.pow(x, n);
          double xn1 = Math.pow(x, n + 1);
          numerator = qMonolayer[comp] * c * x * (1.0 - (n + 1) * xn + n * xn1);
          denominator = (1.0 - x) * (1.0 + (c - 1) * x - c * xn1);
        }

        surfaceExcess[comp] = numerator / denominator;

        // Ensure non-negative
        if (surfaceExcess[comp] < 0) {
          surfaceExcess[comp] = 0.0;
        }
      }
    }

    calculateSurfaceExcessMolFractions();
    calculated = true;
  }

  /**
   * Get the monolayer capacity for a component.
   *
   * @param component the component index
   * @return monolayer capacity in mol/kg
   */
  public double getMonolayerCapacity(int component) {
    return qMonolayer[component];
  }

  /**
   * Set the monolayer capacity for a component.
   *
   * @param component the component index
   * @param value monolayer capacity in mol/kg
   */
  public void setMonolayerCapacity(int component, double value) {
    qMonolayer[component] = value;
    calculated = false;
  }

  /**
   * Get the BET constant for a component.
   *
   * @param component the component index
   * @return BET constant (dimensionless)
   */
  public double getBETConstant(int component) {
    return cBET[component];
  }

  /**
   * Set the BET constant for a component.
   *
   * @param component the component index
   * @param value BET constant (dimensionless)
   */
  public void setBETConstant(int component, double value) {
    cBET[component] = value;
    calculated = false;
  }

  /**
   * Get the saturation pressure for a component.
   *
   * @param component the component index
   * @return saturation pressure in bar
   */
  public double getSaturationPressure(int component) {
    return pSat[component];
  }

  /**
   * Set the saturation pressure for a component.
   *
   * @param component the component index
   * @param value saturation pressure in bar
   */
  public void setSaturationPressure(int component, double value) {
    pSat[component] = value;
    calculated = false;
  }

  /**
   * Get the number of adsorbed layers for a component.
   *
   * @param component the component index
   * @return number of layers (estimated)
   */
  public double getNumberOfLayers(int component) {
    if (!calculated || qMonolayer[component] < 1e-20) {
      return 0.0;
    }
    return surfaceExcess[component] / qMonolayer[component];
  }

  /**
   * Set the maximum number of layers for modified BET.
   *
   * @param n maximum number of layers
   */
  public void setMaxLayers(int n) {
    this.maxLayers = n;
    this.calculated = false;
  }

  /**
   * Get the maximum number of layers for modified BET.
   *
   * @return maximum number of layers
   */
  public int getMaxLayers() {
    return maxLayers;
  }

  /**
   * Calculate the relative pressure (P/P0) for a component.
   *
   * @param phaseNum the phase number
   * @param component the component index
   * @return relative pressure (0 to 1)
   */
  public double getRelativePressure(int phaseNum, int component) {
    double partialPressure = getPartialPressure(phaseNum, component);
    return partialPressure / pSat[component];
  }

  /**
   * Calculate the BET surface area from adsorption data.
   *
   * <p>
   * Uses monolayer capacity and cross-sectional area to calculate:
   * </p>
   * $$A_{BET} = q_m \cdot N_A \cdot \sigma$$
   *
   * @param component the component index
   * @param crossSectionalArea molecular cross-sectional area in nm2
   * @return BET surface area in m2/g
   */
  public double calculateBETSurfaceArea(int component, double crossSectionalArea) {
    double na = 6.022e23;
    return qMonolayer[component] * na * crossSectionalArea * 1e-18;
  }
}
