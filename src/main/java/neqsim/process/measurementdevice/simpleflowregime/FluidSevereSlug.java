package neqsim.process.measurementdevice.simpleflowregime;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Lumped fluid description used by {@link SevereSlugAnalyser}.
 *
 * <p>
 * The severe-slug model treats the liquid as one incompressible phase and the gas as an isothermal ideal gas. For an
 * oil-water-gas fluid the oil and aqueous phases are lumped into one liquid using their in-situ volume fractions
 * (no-slip between the liquids): density is the volume-weighted average and viscosity is the volume-weighted linear
 * average. The linear viscosity average is a simple placeholder and does not represent emulsion behaviour.
 * </p>
 *
 * @author ESOL
 * @version $Id: $Id
 */
public class FluidSevereSlug {
  /** Logger object for class. */
  private static final Logger logger = LogManager.getLogger(FluidSevereSlug.class);

  private double liqDensity = 1000.0;
  private double liqVisc = 0.001;
  private double molecularWeight = 0.029;
  private double gasConstant = ThermodynamicConstantsInterface.R / molecularWeight;

  private double oilDensity = 0;
  private double waterDensity = 0;
  private double oilViscosity = 0;
  private double waterViscosity = 0;
  private double waterVolumeFraction = 0;

  /**
   * Create the lumped fluid from a NeqSim fluid. The fluid is flashed at its own temperature and pressure.
   *
   * @param fluid fluid with a set flow rate; must contain at least one liquid phase (oil and/or aqueous)
   */
  public FluidSevereSlug(SystemInterface fluid) {
    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.TPflash();
    fluid.initProperties();
    if (fluid.getNumberOfPhases() < 2) {
      logger.warn("Severe slug fluid has only one phase; severe slugging needs gas and liquid");
    }
    double oilVolume = 0.0;
    double waterVolume = 0.0;
    if (fluid.hasPhaseType("oil")) {
      oilDensity = fluid.getPhase("oil").getDensity("kg/m3");
      oilViscosity = fluid.getPhase("oil").getViscosity("kg/msec");
      oilVolume = fluid.getPhase("oil").getFlowRate("m3/sec");
    }
    if (fluid.hasPhaseType("aqueous")) {
      waterDensity = fluid.getPhase("aqueous").getDensity("kg/m3");
      waterViscosity = fluid.getPhase("aqueous").getViscosity("kg/msec");
      waterVolume = fluid.getPhase("aqueous").getFlowRate("m3/sec");
    }

    double liquidVolume = oilVolume + waterVolume;
    if (liquidVolume > 0.0) {
      waterVolumeFraction = waterVolume / liquidVolume;
      this.liqDensity = waterVolumeFraction * waterDensity + (1.0 - waterVolumeFraction) * oilDensity;
      this.liqVisc = waterVolumeFraction * waterViscosity + (1.0 - waterVolumeFraction) * oilViscosity;
    } else {
      logger.warn("Severe slug fluid has no liquid phase; default liquid properties are kept");
    }
    if (fluid.hasPhaseType("gas")) {
      setMolecularWeight(fluid.getPhase("gas").getMolarMass());
    }
  }

  /**
   * Create the lumped fluid from explicit properties.
   *
   * @param liqDensity liquid density in kg/m3
   * @param liqVisc liquid dynamic viscosity in Pa s
   * @param molecularWeight gas molar mass in kg/mol
   */
  public FluidSevereSlug(double liqDensity, double liqVisc, double molecularWeight) {
    this.setLiqDensity(liqDensity);
    this.setLiqVisc(liqVisc);
    this.setMolecularWeight(molecularWeight);
  }

  /**
   * Setter for the field <code>liqDensity</code>.
   *
   * @param liqDensity liquid density in kg/m3
   */
  public void setLiqDensity(double liqDensity) {
    if (!(liqDensity > 0.0) || Double.isInfinite(liqDensity)) {
      throw new IllegalArgumentException("liqDensity must be finite and > 0");
    }
    this.liqDensity = liqDensity;
  }

  /**
   * Getter for the field <code>liqDensity</code>.
   *
   * @return liquid density in kg/m3
   */
  public double getLiqDensity() {
    return liqDensity;
  }

  /**
   * Setter for the field <code>liqVisc</code>.
   *
   * @param liqVisc liquid dynamic viscosity in Pa s
   */
  public void setLiqVisc(double liqVisc) {
    if (!(liqVisc > 0.0) || Double.isInfinite(liqVisc)) {
      throw new IllegalArgumentException("liqVisc must be finite and > 0");
    }
    this.liqVisc = liqVisc;
  }

  /**
   * Getter for the field <code>liqVisc</code>.
   *
   * @return liquid dynamic viscosity in Pa s
   */
  public double getliqVisc() {
    return liqVisc;
  }

  /**
   * Setter for the field <code>molecularWeight</code>. The specific gas constant is updated consistently.
   *
   * @param molecularWeight gas molar mass in kg/mol
   */
  public void setMolecularWeight(double molecularWeight) {
    if (!(molecularWeight > 0.0) || Double.isInfinite(molecularWeight)) {
      throw new IllegalArgumentException("molecularWeight must be finite and > 0");
    }
    this.molecularWeight = molecularWeight;
    this.gasConstant = ThermodynamicConstantsInterface.R / molecularWeight;
  }

  /**
   * Getter for the field <code>molecularWeight</code>.
   *
   * @return gas molar mass in kg/mol
   */
  public double getMolecularWeight() {
    return molecularWeight;
  }

  /**
   * Getter for the field <code>gasConstant</code>.
   *
   * @return specific gas constant R/M in J/(kg K)
   */
  public double getGasConstant() {
    return gasConstant;
  }

  /**
   * Get the oil density found by the flash.
   *
   * @return oil density in kg/m3, or 0 when no oil phase was present or the explicit constructor was used
   */
  public double getOilDensity() {
    return oilDensity;
  }

  /**
   * Get the aqueous density found by the flash.
   *
   * @return aqueous density in kg/m3, or 0 when no aqueous phase was present or the explicit constructor was used
   */
  public double getWaterDensity() {
    return waterDensity;
  }

  /**
   * Get the aqueous volume fraction of the lumped liquid (water cut at flowing conditions).
   *
   * @return water cut as a fraction between 0 and 1
   */
  public double getWaterVolumeFraction() {
    return waterVolumeFraction;
  }
}
