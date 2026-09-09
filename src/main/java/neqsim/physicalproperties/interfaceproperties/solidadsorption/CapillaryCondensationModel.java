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
 * For mesoporous materials (2-50 nm pores), capillary condensation occurs at pressures below the bulk saturation
 * pressure due to the curved meniscus in the pores.
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

  /** Relative saturation (activity) of each component in the analysed phase. */
  private double[] relativeSaturation;

  /** User overrides for saturation pressure (bar); NaN means "estimate". */
  private double[] pSatOverride;

  /** User overrides for liquid molar volume (m3/mol); NaN means "estimate". */
  private double[] liquidMolarVolumeOverride;

  /** User overrides for surface tension (N/m); NaN means "estimate". */
  private double[] surfaceTensionOverride;

  /** Flashed saturation mole fraction per component; NaN means "not supplied". */
  private double[] saturationMoleFraction;

  /** Basis used to evaluate the Kelvin driving force. */
  private RelativeSaturationBasis relativeSaturationBasis = RelativeSaturationBasis.FUGACITY;

  /** Whether calculation has been performed. */
  private boolean calculated = false;

  /** Whether the per-component fluid properties have been evaluated. */
  private boolean propertiesCalculated = false;

  /** Number of integration steps for pore size distribution. */
  private int integrationSteps = 100;

  /**
   * Pore radius below which the continuum Kelvin equation stops being valid (nm).
   *
   * <p>
   * Below roughly two nanometres the meniscus is only a few molecular diameters across. The Kelvin equation then
   * <em>over</em>-predicts the relative saturation at which the pore fills, so using it for a microporous sorbent is
   * non-conservative. Use {@link #microporeFillingFraction(double, double, double, double)} instead.
   * </p>
   */
  public static final double KELVIN_VALIDITY_RADIUS_NM = 2.0;

  /**
   * Dubinin-Radushkevich micropore volume filling fraction.
   *
   * <p>
   * In micropores the adsorbate fills the pore volume progressively rather than condensing at a sharp threshold:
   * </p>
   * $$\frac{W}{W_0} = \exp\left[-\left(\frac{RT\ln(1/a)}{\beta E_0}\right)^2\right]$$
   *
   * <p>
   * Filling starts at only a few percent of bulk saturation, one to two orders of magnitude below the Kelvin onset for
   * the same pore size. The characteristic energy must be fitted to a measured isotherm of the same adsorbate on the
   * same sorbent; it is not transferable between sorbents.
   * </p>
   *
   * @param relativeSaturation the adsorbate activity, 0 to 1
   * @param temperature the temperature in K, must be positive
   * @param characteristicEnergy the Dubinin characteristic energy in J/mol, must be positive
   * @param affinityCoefficient the affinity coefficient relative to the reference vapour, must be positive
   * @return fraction of micropore volume filled, between 0 and 1
   */
  public static double microporeFillingFraction(double relativeSaturation, double temperature,
      double characteristicEnergy, double affinityCoefficient) {
    if (temperature <= 0.0 || characteristicEnergy <= 0.0 || affinityCoefficient <= 0.0) {
      throw new IllegalArgumentException(
          "Temperature, characteristic energy and affinity coefficient must all be positive");
    }
    if (relativeSaturation <= 0.0) {
      return 0.0;
    }
    if (relativeSaturation >= 1.0) {
      return 1.0;
    }
    double adsorptionPotential = R * temperature * Math.log(1.0 / relativeSaturation);
    double exponent = adsorptionPotential / (affinityCoefficient * characteristicEnergy);
    return Math.exp(-exponent * exponent);
  }

  /**
   * Basis used to evaluate the relative saturation that drives the Kelvin equation.
   */
  public enum RelativeSaturationBasis {
    /**
     * Ideal partial pressure, \(a_i = y_i P / P_i^{sat}\). Valid only near atmospheric pressure. At elevated pressure
     * this over-predicts the driving force because it ignores the gas-phase fugacity coefficient; for methanol in rich
     * natural gas the over-prediction is a factor of about 1.5 at 40 bara and 2.9 at 100 bara.
     */
    PARTIAL_PRESSURE,
    /**
     * Fugacity ratio, \(a_i = y_i \varphi_i P / (P_i^{sat} \cdot \mathrm{Poy}_i)\), where the Poynting factor
     * \(\mathrm{Poy}_i = \exp\left(V_{m,i}(P - P_i^{sat})/RT\right)\) corrects the pure liquid reference to system
     * pressure. Far better than {@link #PARTIAL_PRESSURE} at elevated pressure, but it references a hypothetical
     * <em>pure</em> liquid. When the real equilibrium liquid is diluted by dissolved gas its activity is below one, so
     * this basis carries a bias; for methanol in rich natural gas at 70 bara it under-predicts the relative saturation
     * by roughly 14 percent and can therefore report a limit above bulk saturation. Prefer
     * {@link #SATURATION_MOLE_FRACTION} when a flash is available.
     */
    FUGACITY,
    /**
     * Exact ratio to the flashed saturation composition, \(a_i = y_i / y_i^{sat}\), where \(y_i^{sat}\) is supplied
     * through {@link CapillaryCondensationModel#setSaturationMoleFraction(int, double)} and is obtained by flashing the
     * gas against an excess of the condensable component at the same temperature and pressure. This references the real
     * equilibrium liquid, so it is free of the pure-liquid and Poynting approximations and cannot exceed one at bulk
     * saturation. This is the recommended basis for high-pressure guard bed work.
     */
    SATURATION_MOLE_FRACTION
  }

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
  public CapillaryCondensationModel() {
  }

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
      relativeSaturation = new double[numComp];
      if (pSatOverride == null || pSatOverride.length != numComp) {
        pSatOverride = newNaNArray(numComp);
        liquidMolarVolumeOverride = newNaNArray(numComp);
        surfaceTensionOverride = newNaNArray(numComp);
        saturationMoleFraction = newNaNArray(numComp);
      }
    }
  }

  /**
   * Create an array pre-filled with NaN, used to flag "no user override".
   *
   * @param length the array length
   * @return a new array of the requested length filled with NaN
   */
  private static double[] newNaNArray(int length) {
    double[] array = new double[length];
    for (int i = 0; i < length; i++) {
      array[i] = Double.NaN;
    }
    return array;
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
   * Uses Lee-Kesler for vapor pressure, Rackett for liquid molar volume, and Macleod-Sugden for surface tension via
   * FluidPropertyEstimator.
   * </p>
   *
   * @param phaseNum the phase number
   */
  private void calculateFluidProperties(int phaseNum) {
    int numComp = system.getPhase(phaseNum).getNumberOfComponents();

    for (int comp = 0; comp < numComp; comp++) {
      double[] props = FluidPropertyEstimator.estimateAllProperties(system, phaseNum, comp);
      pSat[comp] = Double.isNaN(pSatOverride[comp]) ? props[0] : pSatOverride[comp];
      liquidMolarVolume[comp] = Double.isNaN(liquidMolarVolumeOverride[comp]) ? props[1]
          : liquidMolarVolumeOverride[comp];
      surfaceTension[comp] = Double.isNaN(surfaceTensionOverride[comp]) ? props[2] : surfaceTensionOverride[comp];
    }
    propertiesCalculated = true;
  }

  /**
   * Evaluate the relative saturation (activity) of a component in the analysed phase.
   *
   * @param comp the component index
   * @param phaseNum the phase number
   * @return relative saturation, 1.0 at bulk saturation
   */
  private double calcRelativeSaturation(int comp, int phaseNum) {
    double y = system.getPhase(phaseNum).getComponent(comp).getx();
    double pressure = system.getPhase(phaseNum).getPressure();
    if (relativeSaturationBasis == RelativeSaturationBasis.SATURATION_MOLE_FRACTION) {
      if (Double.isNaN(saturationMoleFraction[comp]) || saturationMoleFraction[comp] <= 0.0) {
        logger.warn("No saturation mole fraction supplied for component {}; " + "falling back to the fugacity basis",
            system.getPhase(phaseNum).getComponent(comp).getName());
      } else {
        return y / saturationMoleFraction[comp];
      }
    }
    if (pSat[comp] <= 0.0) {
      return 0.0;
    }
    if (relativeSaturationBasis == RelativeSaturationBasis.PARTIAL_PRESSURE) {
      return y * pressure / pSat[comp];
    }
    double phi = system.getPhase(phaseNum).getComponent(comp).getFugacityCoefficient();
    if (!(phi > 0.0) || Double.isNaN(phi) || Double.isInfinite(phi)) {
      logger.warn(
          "Fugacity coefficient unavailable for component {}; " + "falling back to ideal partial pressure basis",
          system.getPhase(phaseNum).getComponent(comp).getName());
      return y * pressure / pSat[comp];
    }
    double poynting = Math.exp(
        liquidMolarVolume[comp] * (pressure - pSat[comp]) * 1e5 / (R * system.getPhase(phaseNum).getTemperature()));
    return y * phi * pressure / (pSat[comp] * poynting);
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
      double relativePressure = calcRelativeSaturation(comp, phaseNum);
      relativeSaturation[comp] = relativePressure;

      if (relativePressure >= 1.0) {
        // Bulk saturation reached: every pore fills, not zero condensate.
        kelvinRadius[comp] = Double.MAX_VALUE;
        condensateAmount[comp] = integrateOverPoreDistribution(comp, Double.MAX_VALUE, phaseNum);
        continue;
      }
      if (relativePressure <= 0) {
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
      kelvinRadius[comp] = -gf * gamma * vm * cosTheta / (R * temperature * Math.log(relativePressure)) * 1e9;

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
      throw new IllegalStateException("Capillary condensation not calculated. Call calcCapillaryCondensation() first.");
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
      throw new IllegalStateException("Capillary condensation not calculated. Call calcCapillaryCondensation() first.");
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
    if (!propertiesCalculated || pSat == null || pSat.length != system.getPhase(phaseNum).getNumberOfComponents()) {
      initializeArrays();
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

  /**
   * Get the basis used to evaluate the Kelvin driving force.
   *
   * @return the relative saturation basis
   */
  public RelativeSaturationBasis getRelativeSaturationBasis() {
    return relativeSaturationBasis;
  }

  /**
   * Set the basis used to evaluate the Kelvin driving force.
   *
   * @param basis the relative saturation basis, must not be null
   */
  public void setRelativeSaturationBasis(RelativeSaturationBasis basis) {
    if (basis == null) {
      throw new IllegalArgumentException("Relative saturation basis cannot be null");
    }
    this.relativeSaturationBasis = basis;
    this.calculated = false;
  }

  /**
   * Get the relative saturation (activity) of a component in the analysed phase.
   *
   * <p>
   * A value of 1.0 means the bulk gas is at saturation. Capillary condensation starts in a pore of radius r when this
   * value reaches the Kelvin onset for that radius, which is well below 1.0 for narrow pores.
   * </p>
   *
   * @param component the component index
   * @return relative saturation (dimensionless)
   */
  public double getRelativeSaturation(int component) {
    if (!calculated) {
      throw new IllegalStateException("Capillary condensation not calculated. Call calcCapillaryCondensation() first.");
    }
    return relativeSaturation[component];
  }

  /**
   * Get the relative saturation of a component by name.
   *
   * @param componentName the component name
   * @return relative saturation (dimensionless)
   */
  public double getRelativeSaturation(String componentName) {
    return getRelativeSaturation(system.getPhase(0).getComponent(componentName).getComponentNumber());
  }

  /**
   * Override the saturation pressure used for a component instead of the built-in estimate.
   *
   * <p>
   * Useful when a rigorous equation of state, for example CPA for associating components, or measured data provides a
   * better vapour pressure than the generalized correlations.
   * </p>
   *
   * @param component the component index
   * @param pSatBar saturation pressure in bar, or NaN to revert to the built-in estimate
   */
  public void setSaturationPressure(int component, double pSatBar) {
    initializeArrays();
    pSatOverride[component] = pSatBar;
    this.propertiesCalculated = false;
    this.calculated = false;
  }

  /**
   * Override the liquid molar volume used for a component instead of the built-in Rackett estimate.
   *
   * @param component the component index
   * @param vmM3PerMol liquid molar volume in m3/mol, or NaN to revert to the built-in estimate
   */
  public void setLiquidMolarVolume(int component, double vmM3PerMol) {
    initializeArrays();
    liquidMolarVolumeOverride[component] = vmM3PerMol;
    this.propertiesCalculated = false;
    this.calculated = false;
  }

  /**
   * Override the surface tension used for a component instead of the built-in Macleod-Sugden estimate.
   *
   * @param component the component index
   * @param sigmaNPerM surface tension in N/m, or NaN to revert to the built-in estimate
   */
  public void setSurfaceTension(int component, double sigmaNPerM) {
    initializeArrays();
    surfaceTensionOverride[component] = sigmaNPerM;
    this.propertiesCalculated = false;
    this.calculated = false;
  }

  /**
   * Supply the flashed saturation mole fraction of a component in the gas at system temperature and pressure.
   *
   * <p>
   * Obtain it by flashing the gas against an excess of the pure condensable component and reading the gas-phase mole
   * fraction. Supplying it enables the exact {@link RelativeSaturationBasis#SATURATION_MOLE_FRACTION} basis and caps
   * the reported contaminant limit at bulk saturation.
   * </p>
   *
   * @param component the component index
   * @param ySat saturation mole fraction in the gas phase, or NaN to clear
   */
  public void setSaturationMoleFraction(int component, double ySat) {
    initializeArrays();
    saturationMoleFraction[component] = ySat;
    this.calculated = false;
  }

  /**
   * Get the flashed saturation mole fraction supplied for a component.
   *
   * @param component the component index
   * @return the saturation mole fraction, or NaN when none was supplied
   */
  public double getSaturationMoleFraction(int component) {
    initializeArrays();
    return saturationMoleFraction[component];
  }

  /**
   * Calculate the maximum gas-phase mole fraction of a component that keeps a pore of the given radius free of
   * capillary condensate.
   *
   * <p>
   * This is the guard-bed contaminant limit: below this mole fraction the pore stays open, at or above it the pore
   * fills with liquid and the adsorption sites it serves are lost. The calculation inverts the relative saturation
   * relation used by {@link #calcCapillaryCondensation(int)}, so it is consistent with the configured
   * {@link RelativeSaturationBasis}.
   * </p>
   *
   * <p>
   * The fugacity coefficient is evaluated at the composition currently held by the system. For a strongly associating
   * contaminant such as methanol the fugacity coefficient depends on its own concentration, so the limit is a fixed
   * point: re-flash the system at the returned mole fraction and call this method again until it stops changing. Two or
   * three passes are normally enough.
   * </p>
   *
   * @param component the component index
   * @param poreRadiusNm the pore radius in nm, must be larger than the adsorbed layer thickness
   * @param phaseNum the phase number
   * @return maximum allowable mole fraction in the gas phase, or 1.0 when no pore of this size can condense
   */
  public double getMaxAllowableMoleFraction(int component, double poreRadiusNm, int phaseNum) {
    if (!propertiesCalculated || pSat == null || pSat.length != system.getPhase(phaseNum).getNumberOfComponents()) {
      initializeArrays();
      calculateFluidProperties(phaseNum);
    }
    double onset = getCondensationPressure(poreRadiusNm, component, phaseNum);
    if (onset <= 0.0 || onset >= 1.0) {
      return 1.0;
    }
    boolean haveSaturation = !Double.isNaN(saturationMoleFraction[component])
        && saturationMoleFraction[component] > 0.0;
    if (relativeSaturationBasis == RelativeSaturationBasis.SATURATION_MOLE_FRACTION && haveSaturation) {
      return onset * saturationMoleFraction[component];
    }
    double pressure = system.getPhase(phaseNum).getPressure();
    double limit;
    double phi = system.getPhase(phaseNum).getComponent(component).getFugacityCoefficient();
    if (relativeSaturationBasis == RelativeSaturationBasis.PARTIAL_PRESSURE || !(phi > 0.0) || Double.isNaN(phi)
        || Double.isInfinite(phi)) {
      limit = onset * pSat[component] / pressure;
    } else {
      double poynting = Math.exp(liquidMolarVolume[component] * (pressure - pSat[component]) * 1e5
          / (R * system.getPhase(phaseNum).getTemperature()));
      limit = onset * pSat[component] * poynting / (phi * pressure);
    }
    if (haveSaturation) {
      // A pore cannot tolerate more than bulk saturation, whatever the approximate basis says.
      limit = Math.min(limit, onset * saturationMoleFraction[component]);
    }
    return limit;
  }

  /**
   * Calculate the maximum gas-phase mole fraction of a named component that keeps a pore of the given radius free of
   * capillary condensate.
   *
   * @param componentName the component name
   * @param poreRadiusNm the pore radius in nm
   * @param phaseNum the phase number
   * @return maximum allowable mole fraction in the gas phase
   */
  public double getMaxAllowableMoleFraction(String componentName, double poreRadiusNm, int phaseNum) {
    return getMaxAllowableMoleFraction(system.getPhase(phaseNum).getComponent(componentName).getComponentNumber(),
        poreRadiusNm, phaseNum);
  }
}
