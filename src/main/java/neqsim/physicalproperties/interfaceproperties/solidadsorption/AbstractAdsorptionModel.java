package neqsim.physicalproperties.interfaceproperties.solidadsorption;

import java.io.Serializable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;

/**
 * Abstract base class for adsorption models.
 *
 * <p>
 * Provides common functionality for all adsorption isotherm implementations including:
 * </p>
 * <ul>
 * <li>Database parameter loading</li>
 * <li>Surface excess calculations</li>
 * <li>Multi-component handling</li>
 * <li>Adsorbent material properties</li>
 * </ul>
 *
 * @author ESOL
 * @version 1.0
 */
public abstract class AbstractAdsorptionModel implements AdsorptionInterface, Serializable {

  /** Serialization version UID. */
  private static final long serialVersionUID = 1001L;

  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(AbstractAdsorptionModel.class);

  /** The thermodynamic system being modeled. */
  protected SystemInterface system;

  /** Name/identifier of the solid adsorbent material. */
  protected String solidMaterial = "AC";

  /** Surface excess for each component (mol/kg adsorbent). */
  protected double[] surfaceExcess;

  /** Mole fractions in adsorbed phase. */
  protected double[] surfaceExcessMolFraction;

  /** Total surface excess of all components (mol/kg adsorbent). */
  protected double totalSurfaceExcess;

  /** BET surface area of adsorbent (m2/g). */
  protected double betSurfaceArea = 1000.0;

  /** Total pore volume (cm3/g). */
  protected double poreVolume = 0.45;

  /** Mean pore radius (nm). */
  protected double meanPoreRadius = 1.5;

  /** Whether adsorption has been calculated. */
  protected boolean calculated = false;

  /**
   * Default constructor.
   */
  public AbstractAdsorptionModel() {}

  /**
   * Constructor with thermodynamic system.
   *
   * @param system the thermodynamic system containing the gas/fluid phase
   */
  public AbstractAdsorptionModel(SystemInterface system) {
    this.system = system;
    initializeArrays();
  }

  /**
   * Initialize arrays based on number of components.
   */
  protected void initializeArrays() {
    if (system != null) {
      int numComp = system.getPhase(0).getNumberOfComponents();
      surfaceExcess = new double[numComp];
      surfaceExcessMolFraction = new double[numComp];
    }
  }

  /**
   * Get the isotherm type implemented by this class.
   *
   * @return the isotherm type enum value
   */
  @Override
  public abstract IsothermType getIsothermType();

  /**
   * Load model parameters from the database for all components.
   *
   * @param phaseNum the phase number to use for component lookup
   */
  protected abstract void loadParameters(int phaseNum);

  /** {@inheritDoc} */
  @Override
  public void setSolidMaterial(String solidM) {
    this.solidMaterial = solidM;
    this.calculated = false;
  }

  /**
   * Get the current solid adsorbent material name.
   *
   * @return the solid material identifier
   */
  public String getSolidMaterial() {
    return solidMaterial;
  }

  /** {@inheritDoc} */
  @Override
  public double getSurfaceExcess(int component) {
    if (!calculated) {
      throw new IllegalStateException("Adsorption not calculated. Call calcAdsorption() first.");
    }
    if (component < 0 || component >= surfaceExcess.length) {
      throw new IndexOutOfBoundsException(
          "Component index " + component + " out of range [0, " + (surfaceExcess.length - 1) + "]");
    }
    return surfaceExcess[component];
  }

  /** {@inheritDoc} */
  @Override
  public double getSurfaceExcess(String componentName) {
    int componentNumber = system.getPhase(0).getComponent(componentName).getComponentNumber();
    return getSurfaceExcess(componentNumber);
  }

  /**
   * Get the total surface excess of all components.
   *
   * @return total surface excess in mol/kg adsorbent
   */
  @Override
  public double getTotalSurfaceExcess() {
    return totalSurfaceExcess;
  }

  /**
   * Get the mole fraction of a component in the adsorbed phase.
   *
   * @param component the component index
   * @return mole fraction in adsorbed phase (0 to 1)
   */
  public double getAdsorbedPhaseMoleFraction(int component) {
    if (!calculated) {
      throw new IllegalStateException("Adsorption not calculated. Call calcAdsorption() first.");
    }
    return surfaceExcessMolFraction[component];
  }

  /**
   * Get the adsorption selectivity between two components.
   *
   * <p>
   * Selectivity is defined as:
   * </p>
   * $$\alpha_{i/j} = \frac{x_i / y_i}{x_j / y_j}$$
   *
   * <p>
   * where x is adsorbed phase mole fraction and y is gas phase mole fraction.
   * </p>
   *
   * @param component1 index of first component
   * @param component2 index of second component (reference)
   * @param phaseNum the bulk phase number
   * @return selectivity (dimensionless)
   */
  public double getSelectivity(int component1, int component2, int phaseNum) {
    if (!calculated) {
      throw new IllegalStateException("Adsorption not calculated. Call calcAdsorption() first.");
    }

    double y1 = system.getPhase(phaseNum).getComponent(component1).getx();
    double y2 = system.getPhase(phaseNum).getComponent(component2).getx();

    if (y1 < 1e-20 || y2 < 1e-20 || surfaceExcessMolFraction[component2] < 1e-20) {
      return Double.NaN;
    }

    return (surfaceExcessMolFraction[component1] / y1)
        / (surfaceExcessMolFraction[component2] / y2);
  }

  /**
   * Calculate normalized mole fractions in adsorbed phase.
   */
  protected void calculateSurfaceExcessMolFractions() {
    totalSurfaceExcess = 0.0;
    for (int comp = 0; comp < surfaceExcess.length; comp++) {
      totalSurfaceExcess += surfaceExcess[comp];
    }

    if (totalSurfaceExcess > 1e-20) {
      for (int comp = 0; comp < surfaceExcess.length; comp++) {
        surfaceExcessMolFraction[comp] = surfaceExcess[comp] / totalSurfaceExcess;
      }
    } else {
      for (int comp = 0; comp < surfaceExcess.length; comp++) {
        surfaceExcessMolFraction[comp] = 0.0;
      }
    }
  }

  /**
   * Get the BET surface area of the adsorbent.
   *
   * @return surface area in m2/g
   */
  public double getBetSurfaceArea() {
    return betSurfaceArea;
  }

  /**
   * Set the BET surface area of the adsorbent.
   *
   * @param area surface area in m2/g
   */
  public void setBetSurfaceArea(double area) {
    this.betSurfaceArea = area;
  }

  /**
   * Get the total pore volume of the adsorbent.
   *
   * @return pore volume in cm3/g
   */
  public double getPoreVolume() {
    return poreVolume;
  }

  /**
   * Set the total pore volume of the adsorbent.
   *
   * @param volume pore volume in cm3/g
   */
  public void setPoreVolume(double volume) {
    this.poreVolume = volume;
  }

  /**
   * Get the mean pore radius of the adsorbent.
   *
   * @return pore radius in nm
   */
  public double getMeanPoreRadius() {
    return meanPoreRadius;
  }

  /**
   * Set the mean pore radius of the adsorbent.
   *
   * @param radius pore radius in nm
   */
  public void setMeanPoreRadius(double radius) {
    this.meanPoreRadius = radius;
  }

  /**
   * Check if adsorption calculation has been performed.
   *
   * @return true if calcAdsorption has been called
   */
  @Override
  public boolean isCalculated() {
    return calculated;
  }

  /**
   * Get the thermodynamic system.
   *
   * @return the system interface
   */
  public SystemInterface getSystem() {
    return system;
  }

  /**
   * Set the thermodynamic system.
   *
   * @param system the thermodynamic system
   */
  public void setSystem(SystemInterface system) {
    this.system = system;
    initializeArrays();
    this.calculated = false;
  }

  /**
   * Get component partial pressure.
   *
   * @param phaseNum the phase number
   * @param compNum the component number
   * @return partial pressure in bar
   */
  protected double getPartialPressure(int phaseNum, int compNum) {
    return system.getPhase(phaseNum).getComponent(compNum).getx()
        * system.getPhase(phaseNum).getPressure();
  }

  /**
   * Get component fugacity.
   *
   * @param phaseNum the phase number
   * @param compNum the component number
   * @return fugacity in bar
   */
  protected double getFugacity(int phaseNum, int compNum) {
    return system.getPhase(phaseNum).getComponent(compNum).getx()
        * system.getPhase(phaseNum).getComponent(compNum).getFugacityCoefficient()
        * system.getPhase(phaseNum).getPressure();
  }
}
