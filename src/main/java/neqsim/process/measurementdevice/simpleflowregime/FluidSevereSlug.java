package neqsim.process.measurementdevice.simpleflowregime;

import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * <p>
 * FluidSevereSlug class.
 * </p>
 *
 * @author ESOL
 * @version $Id: $Id
 */
public class FluidSevereSlug {
  private double liqDensity = 1000.0;
  private double liqVisc = 0.001;
  private double molecularWeight = 0.029;
  private double gasConstant = 8314 / molecularWeight * 1000;

  private double oilDensity = 0;
  private double waterDensity = 0;
  private double waterWtFraction = 0;

  private double oilViscosity = 0;
  private double waterViscosity = 0;
  private double oilWtFraction = 0;

  private int phaseNumber;

  FluidSevereSlug(SystemInterface fluid) {
    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.TPflash();
    fluid.initProperties();
    if ((fluid.getNumberOfPhases()) < 2) {
      System.out.println("There is only one phase");
    }
    if (fluid.hasPhaseType("oil")) {
      phaseNumber = fluid.getPhaseNumberOfPhase("oil");
      oilDensity = fluid.getPhase("oil").getDensity("kg/m3");
      oilViscosity = fluid.getPhase("oil").getViscosity("kg/msec");
      oilWtFraction = fluid.getWtFraction(phaseNumber) * 100;
    }
    if (fluid.hasPhaseType("aqueous")) {
      phaseNumber = fluid.getPhaseNumberOfPhase("aqueous");
      waterDensity = fluid.getPhase("aqueous").getDensity("kg/m3");
      waterViscosity = fluid.getPhase("aqueous").getViscosity("kg/msec");
      waterWtFraction = fluid.getWtFraction(phaseNumber) * 100;
    }

    this.liqDensity = (waterWtFraction / 100) * waterDensity + (oilWtFraction / 100) * oilDensity;
    this.liqVisc = (waterWtFraction / 100) * waterViscosity + (oilWtFraction / 100) * oilViscosity;
    this.molecularWeight = fluid.getPhase("gas").getMolarMass();
  }

  FluidSevereSlug(double liqDensity, double liqVisc, double molecularWeight) {
    this.setLiqDensity(liqDensity);
    this.setLiqVisc(liqVisc);
    this.setMolecularWeight(molecularWeight);
    this.gasConstant = 8314 / (molecularWeight * 1000);
  }

  /**
   * <p>
   * Setter for the field <code>liqDensity</code>.
   * </p>
   *
   * @param liqDensity a double
   */
  public void setLiqDensity(double liqDensity) {
    this.liqDensity = liqDensity;
  }

  /**
   * <p>
   * Getter for the field <code>liqDensity</code>.
   * </p>
   *
   * @return a double
   */
  public double getLiqDensity() {
    return liqDensity;
  }

  /**
   * <p>
   * Setter for the field <code>liqVisc</code>.
   * </p>
   *
   * @param liqVisc a double
   */
  public void setLiqVisc(double liqVisc) {
    this.liqVisc = liqVisc;
  }

  /**
   * <p>
   * Getter for the field <code>liqVisc</code>.
   * </p>
   *
   * @return a double
   */
  public double getliqVisc() {
    return liqVisc;
  }

  /**
   * <p>
   * Setter for the field <code>molecularWeight</code>.
   * </p>
   *
   * @param molecularWeight a double
   */
  public void setMolecularWeight(double molecularWeight) {
    this.molecularWeight = molecularWeight;
  }

  /**
   * <p>
   * Getter for the field <code>molecularWeight</code>.
   * </p>
   *
   * @return a double
   */
  public double getMolecularWeight() {
    return molecularWeight;
  }

  /**
   * <p>
   * Getter for the field <code>gasConstant</code>.
   * </p>
   *
   * @return a double
   */
  public double getGasConstant() {
    return gasConstant;
  }
}
