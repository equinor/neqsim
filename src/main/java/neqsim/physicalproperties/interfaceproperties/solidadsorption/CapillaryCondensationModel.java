package neqsim.physicalproperties.interfaceproperties.solidadsorption;

import java.io.Serializable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.system.SystemInterface;

/**
 * Capillary condensation model for mesoporous materials.
 *
 * <p>
 * Models capillary condensation in pores using the Kelvin equation:
 * </p>
 * $$\ln\left(\frac{P}{P_0}\right) = -\frac{2 \gamma V_m \cos\theta}{r \cdot R \cdot T}$$
 *
 * <p>
 * The critical (Kelvin) radius below which condensation occurs:
 * </p>
 * $$r_K = -\frac{2 \gamma V_m \cos\theta}{R T \ln(P/P_0)}$$
 *
 * <p>
 * For mesoporous materials (2-50 nm pores), capillary condensation occurs at pressures below the
 * bulk saturation pressure due to the curved meniscus in the pores.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class CapillaryCondensationModel implements Serializable, ThermodynamicConstantsInterface {

  /** Serialization version UID. */
  private static final long serialVersionUID = 1004L;

  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(CapillaryCondensationModel.class);

  /** The thermodynamic system. */
  private SystemInterface system;

  /** Pore type (cylindrical, slit, spherical). */
  private PoreType poreType = PoreType.CYLINDRICAL;

  /** Minimum pore radius (nm). */
  private double minPoreRadius = 1.0;

  /** Maximum pore radius (nm). */
  private double maxPoreRadius = 25.0;

  /** Mean pore radius (nm). */
  private double meanPoreRadius = 5.0;

  /** Pore radius standard deviation (nm). */
  private double poreRadiusStdDev = 2.0;

  /** Total pore volume (cm3/g). */
  private double totalPoreVolume = 0.5;

  /** Contact angle (radians). */
  private double contactAngle = 0.0;

  /** Adsorbed layer thickness (nm). */
  private double adsorbedLayerThickness = 0.35;

  /** Kelvin radii for each component (nm). */
  private double[] kelvinRadius;

  /** Capillary condensate amount for each component (mol/kg). */
  private double[] condensateAmount;

  /** Saturation pressures (bar). */
  private double[] pSat;

  /** Liquid molar volumes (m3/mol). */
  private double[] liquidMolarVolume;

  /** Surface tensions (N/m). */
  private double[] surfaceTension;

  /** Whether calculation has been performed. */
  private boolean calculated = false;

  /** Number of integration steps for pore size distribution. */
  private int integrationSteps = 100;

  /**
   * Enumeration of pore types.
   */
  public enum PoreType {
    /** Cylindrical pores (e.g., MCM-41, zeolites). */
    CYLINDRICAL(2.0),
    /** Slit-shaped pores (e.g., activated carbons). */
    SLIT(1.0),
    /** Spherical pores (e.g., cage-type zeolites). */
    SPHERICAL(2.0),
    /** Ink-bottle pores with constricted openings. */
    INK_BOTTLE(2.0);

    private final double geometryFactor;

    PoreType(double geometryFactor) {
      this.geometryFactor = geometryFactor;
    }

    /**
     * Get the geometry factor for Kelvin equation.
     *
     * @return geometry factor
     */
    public double getGeometryFactor() {
      return geometryFactor;
    }
  }

  /**
   * Default constructor.
   */
  public CapillaryCondensationModel() {}

  /**
   * Constructor with thermodynamic system.
   *
   * @param system the thermodynamic system
   */
  public CapillaryCondensationModel(SystemInterface system) {
    this.system = system;
    initializeArrays();
  }

  /**
   * Initialize arrays based on number of components.
   */
  private void initializeArrays() {
    if (system != null) {
      int numComp = system.getPhase(0).getNumberOfComponents();
      kelvinRadius = new double[numComp];
      condensateAmount = new double[numComp];
      pSat = new double[numComp];
      liquidMolarVolume = new double[numComp];
      surfaceTension = new double[numComp];
    }
  }

  /**
   * Set the thermodynamic system.
   *
   * @param system the thermodynamic system
   */
  public void setSystem(SystemInterface system) {
    this.system = system;
    initializeArrays();
    calculated = false;
  }

  /**
   * Calculate fluid properties needed for Kelvin equation using correlations.
   *
   * <p>
   * Uses Lee-Kesler for vapor pressure, Rackett for liquid molar volume, and Macleod-Sugden for
   * surface tension via FluidPropertyEstimator.
   * </p>
   *
   * @param phaseNum the phase number
   */
  private void calculateFluidProperties(int phaseNum) {
    int numComp = system.getPhase(phaseNum).getNumberOfComponents();

    for (int comp = 0; comp < numComp; comp++) {
      double[] props = FluidPropertyEstimator.estimateAllProperties(system, phaseNum, comp);
      pSat[comp] = props[0];
      liquidMolarVolume[comp] = props[1];
      surfaceTension[comp] = props[2];
    }
  }

  /**
   * Calculate capillary condensation for all components.
   *
   * @param phaseNum the phase number
   */
  public void calcCapillaryCondensation(int phaseNum) {
    initializeArrays();
    calculateFluidProperties(phaseNum);

    int numComp = system.getPhase(phaseNum).getNumberOfComponents();
    double temperature = system.getPhase(phaseNum).getTemperature();

    for (int comp = 0; comp < numComp; comp++) {
      double partialPressure = system.getPhase(phaseNum).getComponent(comp).getx()
          * system.getPhase(phaseNum).getPressure();

      double relativePressure = partialPressure / pSat[comp];

      if (relativePressure >= 1.0 || relativePressure <= 0) {
        kelvinRadius[comp] = Double.MAX_VALUE;
        condensateAmount[comp] = 0.0;
        continue;
      }

      // Calculate Kelvin radius using modified Kelvin equation
      double cosTheta = Math.cos(contactAngle);
      double gamma = surfaceTension[comp];
      double vm = liquidMolarVolume[comp];
      double gf = poreType.getGeometryFactor();

      // r_k = -gf * gamma * Vm * cos(theta) / (R * T * ln(P/P0))
      kelvinRadius[comp] =
          -gf * gamma * vm * cosTheta / (R * temperature * Math.log(relativePressure)) * 1e9;

      // Apply correction for adsorbed layer thickness
      double effectiveRadius = kelvinRadius[comp] + adsorbedLayerThickness;

      // Calculate condensate amount by integrating over pore size distribution
      condensateAmount[comp] = integrateOverPoreDistribution(comp, effectiveRadius, phaseNum);
    }

    calculated = true;
  }

  /**
   * Integrate condensate volume over pore size distribution.
   *
   * @param comp the component index
   * @param criticalRadius the critical radius for condensation (nm)
   * @param phaseNum the phase number
   * @return condensate amount in mol/kg
   */
  private double integrateOverPoreDistribution(int comp, double criticalRadius, int phaseNum) {
    double totalCondensate = 0.0;

    double dr = (maxPoreRadius - minPoreRadius) / integrationSteps;

    for (int i = 0; i < integrationSteps; i++) {
      double r = minPoreRadius + (i + 0.5) * dr;

      if (r < criticalRadius) {
        // This pore is filled with condensate
        double poreVolumeFraction = getPoreVolumeFraction(r) * dr;
        double poreVolumePerGram = totalPoreVolume * poreVolumeFraction;
        totalCondensate += poreVolumePerGram;
      }
    }

    // Convert cm3/g to mol/kg
    double vm = liquidMolarVolume[comp];
    if (vm > 1e-20) {
      return totalCondensate * 1e-3 / vm;
    }
    return 0.0;
  }

  /**
   * Get pore volume fraction at a given radius using log-normal distribution.
   *
   * @param r the pore radius (nm)
   * @return differential pore volume fraction (1/nm)
   */
  private double getPoreVolumeFraction(double r) {
    // Log-normal distribution
    double lnR = Math.log(r);
    double lnMean = Math.log(meanPoreRadius);
    double lnSigma = Math.log(1.0 + poreRadiusStdDev / meanPoreRadius);

    double exponent = -Math.pow(lnR - lnMean, 2.0) / (2.0 * lnSigma * lnSigma);
    return Math.exp(exponent) / (r * lnSigma * Math.sqrt(2.0 * Math.PI));
  }

  /**
   * Get the Kelvin radius for a component.
   *
   * @param component the component index
   * @return Kelvin radius in nm
   */
  public double getKelvinRadius(int component) {
    if (!calculated) {
      throw new IllegalStateException(
          "Capillary condensation not calculated. Call calcCapillaryCondensation() first.");
    }
    return kelvinRadius[component];
  }

  /**
   * Get the Kelvin radius for a component by name.
   *
   * @param componentName the component name
   * @return Kelvin radius in nm
   */
  public double getKelvinRadius(String componentName) {
    int compNum = system.getPhase(0).getComponent(componentName).getComponentNumber();
    return getKelvinRadius(compNum);
  }

  /**
   * Get the capillary condensate amount for a component.
   *
   * @param component the component index
   * @return condensate amount in mol/kg
   */
  public double getCondensateAmount(int component) {
    if (!calculated) {
      throw new IllegalStateException(
          "Capillary condensation not calculated. Call calcCapillaryCondensation() first.");
    }
    return condensateAmount[component];
  }

  /**
   * Get the capillary condensate amount for a component by name.
   *
   * @param componentName the component name
   * @return condensate amount in mol/kg
   */
  public double getCondensateAmount(String componentName) {
    int compNum = system.getPhase(0).getComponent(componentName).getComponentNumber();
    return getCondensateAmount(compNum);
  }

  /**
   * Calculate the pressure at which capillary condensation begins in a given pore.
   *
   * @param poreRadius the pore radius (nm)
   * @param component the component index
   * @param phaseNum the phase number
   * @return relative pressure P/P0 for condensation
   */
  public double getCondensationPressure(double poreRadius, int component, int phaseNum) {
    if (pSat == null || surfaceTension == null || liquidMolarVolume == null) {
      calculateFluidProperties(phaseNum);
    }

    double temperature = system.getPhase(phaseNum).getTemperature();
    double cosTheta = Math.cos(contactAngle);
    double gamma = surfaceTension[component];
    double vm = liquidMolarVolume[component];
    double gf = poreType.getGeometryFactor();

    // Effective radius accounting for adsorbed layer
    double rEff = poreRadius - adsorbedLayerThickness;
    if (rEff <= 0) {
      return 0.0;
    }

    // P/P0 = exp(-gf * gamma * Vm * cos(theta) / (R * T * r))
    double exponent = -gf * gamma * vm * cosTheta / (R * temperature * rEff * 1e-9);
    return Math.exp(exponent);
  }

  // Getters and setters

  /**
   * Get the pore type.
   *
   * @return the pore type
   */
  public PoreType getPoreType() {
    return poreType;
  }

  /**
   * Set the pore type.
   *
   * @param poreType the pore type
   */
  public void setPoreType(PoreType poreType) {
    this.poreType = poreType;
    this.calculated = false;
  }

  /**
   * Get the minimum pore radius.
   *
   * @return minimum pore radius in nm
   */
  public double getMinPoreRadius() {
    return minPoreRadius;
  }

  /**
   * Set the minimum pore radius.
   *
   * @param radius minimum pore radius in nm
   */
  public void setMinPoreRadius(double radius) {
    this.minPoreRadius = radius;
    this.calculated = false;
  }

  /**
   * Get the maximum pore radius.
   *
   * @return maximum pore radius in nm
   */
  public double getMaxPoreRadius() {
    return maxPoreRadius;
  }

  /**
   * Set the maximum pore radius.
   *
   * @param radius maximum pore radius in nm
   */
  public void setMaxPoreRadius(double radius) {
    this.maxPoreRadius = radius;
    this.calculated = false;
  }

  /**
   * Get the mean pore radius.
   *
   * @return mean pore radius in nm
   */
  public double getMeanPoreRadius() {
    return meanPoreRadius;
  }

  /**
   * Set the mean pore radius.
   *
   * @param radius mean pore radius in nm
   */
  public void setMeanPoreRadius(double radius) {
    this.meanPoreRadius = radius;
    this.calculated = false;
  }

  /**
   * Get the pore radius standard deviation.
   *
   * @return pore radius standard deviation in nm
   */
  public double getPoreRadiusStdDev() {
    return poreRadiusStdDev;
  }

  /**
   * Set the pore radius standard deviation.
   *
   * @param stdDev pore radius standard deviation in nm
   */
  public void setPoreRadiusStdDev(double stdDev) {
    this.poreRadiusStdDev = stdDev;
    this.calculated = false;
  }

  /**
   * Get the total pore volume.
   *
   * @return total pore volume in cm3/g
   */
  public double getTotalPoreVolume() {
    return totalPoreVolume;
  }

  /**
   * Set the total pore volume.
   *
   * @param volume total pore volume in cm3/g
   */
  public void setTotalPoreVolume(double volume) {
    this.totalPoreVolume = volume;
    this.calculated = false;
  }

  /**
   * Get the contact angle.
   *
   * @return contact angle in radians
   */
  public double getContactAngle() {
    return contactAngle;
  }

  /**
   * Set the contact angle.
   *
   * @param angle contact angle in radians
   */
  public void setContactAngle(double angle) {
    this.contactAngle = angle;
    this.calculated = false;
  }

  /**
   * Get the adsorbed layer thickness.
   *
   * @return adsorbed layer thickness in nm
   */
  public double getAdsorbedLayerThickness() {
    return adsorbedLayerThickness;
  }

  /**
   * Set the adsorbed layer thickness.
   *
   * @param thickness adsorbed layer thickness in nm
   */
  public void setAdsorbedLayerThickness(double thickness) {
    this.adsorbedLayerThickness = thickness;
    this.calculated = false;
  }

  /**
   * Check if calculation has been performed.
   *
   * @return true if calcCapillaryCondensation has been called
   */
  public boolean isCalculated() {
    return calculated;
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
   * Get the liquid molar volume for a component.
   *
   * @param component the component index
   * @return liquid molar volume in m3/mol
   */
  public double getLiquidMolarVolume(int component) {
    return liquidMolarVolume[component];
  }

  /**
   * Get the surface tension for a component.
   *
   * @param component the component index
   * @return surface tension in N/m
   */
  public double getSurfaceTension(int component) {
    return surfaceTension[component];
  }
}
