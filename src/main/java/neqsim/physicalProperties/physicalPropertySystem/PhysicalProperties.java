/*
 * PhysicalProperties.java
 *
 * Created on 29. oktober 2000, 16:13
 */

package neqsim.physicalProperties.physicalPropertySystem;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.phase.PhaseInterface;

/**
 * <p>
 * Abstract PhysicalProperties class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public abstract class PhysicalProperties
    implements PhysicalPropertiesInterface, ThermodynamicConstantsInterface {
  /** {@inheritDoc} */
  @Override
  public void setMixingRule(
      neqsim.physicalProperties.mixingRule.PhysicalPropertyMixingRuleInterface mixingRule) {
    this.mixingRule = mixingRule;
  }

  private static final long serialVersionUID = 1000;
  static Logger logger = LogManager.getLogger(PhysicalProperties.class);

  public PhaseInterface phase;
  protected int binaryDiffusionCoefficientMethod;
  protected int multicomponentDiffusionMethod;
  private neqsim.physicalProperties.mixingRule.PhysicalPropertyMixingRuleInterface mixingRule =
      null;
  public neqsim.physicalProperties.physicalPropertyMethods.methodInterface.ConductivityInterface conductivityCalc;
  public neqsim.physicalProperties.physicalPropertyMethods.methodInterface.ViscosityInterface viscosityCalc;
  public neqsim.physicalProperties.physicalPropertyMethods.methodInterface.DiffusivityInterface diffusivityCalc;
  public neqsim.physicalProperties.physicalPropertyMethods.methodInterface.DensityInterface densityCalc;
  public double kinematicViscosity = 0;
  public double density = 0;
  public double viscosity = 0;
  public double conductivity = 0;
  private double[] waxViscosityParameter = {37.82, 83.96, 8.559e6};

  /**
   * <p>
   * Constructor for PhysicalProperties.
   * </p>
   */
  public PhysicalProperties() {}

  /**
   * <p>
   * Constructor for PhysicalProperties.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   */
  public PhysicalProperties(PhaseInterface phase) {
    this.phase = phase;
  }

  /**
   * <p>
   * Constructor for PhysicalProperties.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param binaryDiffusionCoefficientMethod a int
   * @param multicomponentDiffusionMethod a int
   */
  public PhysicalProperties(PhaseInterface phase, int binaryDiffusionCoefficientMethod,
      int multicomponentDiffusionMethod) {
    this.phase = phase;
    this.binaryDiffusionCoefficientMethod = binaryDiffusionCoefficientMethod;
    this.multicomponentDiffusionMethod = multicomponentDiffusionMethod;
  }

  /** {@inheritDoc} */
  @Override
  public PhysicalProperties clone() {
    PhysicalProperties properties = null;

    try {
      properties = (PhysicalProperties) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }
    properties.densityCalc = densityCalc.clone();
    properties.diffusivityCalc = diffusivityCalc.clone();
    properties.viscosityCalc = viscosityCalc.clone();
    properties.conductivityCalc = conductivityCalc.clone();
    if (mixingRule != null) {
      properties.mixingRule = mixingRule.clone();
    }
    return properties;
  }

  /** {@inheritDoc} */
  @Override
  public PhaseInterface getPhase() {
    return phase;
  }

  /** {@inheritDoc} */
  @Override
  public neqsim.physicalProperties.mixingRule.PhysicalPropertyMixingRuleInterface getMixingRule() {
    return mixingRule;
  }

  /** {@inheritDoc} */
  @Override
  public void setMixingRuleNull() {
    setMixingRule(null);
  }

  /** {@inheritDoc} */
  @Override
  public neqsim.physicalProperties.physicalPropertyMethods.methodInterface.ViscosityInterface getViscosityModel() {
    return viscosityCalc;
  }

  /** {@inheritDoc} */
  @Override
  public void setDensityModel(String model) {
    if ("Peneloux volume shift".equals(model)) {
      densityCalc =
          new neqsim.physicalProperties.physicalPropertyMethods.liquidPhysicalProperties.density.Density(
              this);
    } else if ("Costald".equals(model)) {
      densityCalc =
          new neqsim.physicalProperties.physicalPropertyMethods.liquidPhysicalProperties.density.Costald(
              this);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void setConductivityModel(String model) {
    if ("PFCT".equals(model)) {
      conductivityCalc =
          new neqsim.physicalProperties.physicalPropertyMethods.commonPhasePhysicalProperties.conductivity.PFCTConductivityMethodMod86(
              this);
    } else if ("polynom".equals(model)) {
      conductivityCalc =
          new neqsim.physicalProperties.physicalPropertyMethods.liquidPhysicalProperties.conductivity.Conductivity(
              this);
    } else if ("Chung".equals(model)) {
      conductivityCalc =
          new neqsim.physicalProperties.physicalPropertyMethods.gasPhysicalProperties.conductivity.ChungConductivityMethod(
              this);
    } else {
      conductivityCalc =
          new neqsim.physicalProperties.physicalPropertyMethods.commonPhasePhysicalProperties.conductivity.PFCTConductivityMethodMod86(
              this);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void setViscosityModel(String model) {
    if ("polynom".equals(model)) {
      viscosityCalc =
          new neqsim.physicalProperties.physicalPropertyMethods.liquidPhysicalProperties.viscosity.Viscosity(
              this);
    } else if ("friction theory".equals(model)) {
      viscosityCalc =
          new neqsim.physicalProperties.physicalPropertyMethods.commonPhasePhysicalProperties.viscosity.FrictionTheoryViscosityMethod(
              this);
    } else if ("LBC".equals(model)) {
      viscosityCalc =
          new neqsim.physicalProperties.physicalPropertyMethods.commonPhasePhysicalProperties.viscosity.LBCViscosityMethod(
              this);
    } else if ("PFCT".equals(model)) {
      viscosityCalc =
          new neqsim.physicalProperties.physicalPropertyMethods.commonPhasePhysicalProperties.viscosity.PFCTViscosityMethodMod86(
              this);
    } else if ("PFCT-Heavy-Oil".equals(model)) {
      viscosityCalc =
          new neqsim.physicalProperties.physicalPropertyMethods.commonPhasePhysicalProperties.viscosity.PFCTViscosityMethodHeavyOil(
              this);
    }
  }

  /**
   * <p>
   * setDiffusionCoefficientModel.
   * </p>
   *
   * @param model a {@link java.lang.String} object
   */
  public void setDiffusionCoefficientModel(String model) {
    if ("CSP".equals(model)) {
      diffusivityCalc =
          new neqsim.physicalProperties.physicalPropertyMethods.commonPhasePhysicalProperties.diffusivity.CorrespondingStatesDiffusivity(
              this);
    } else if ("Wilke Lee".equals(model)) {
      diffusivityCalc =
          new neqsim.physicalProperties.physicalPropertyMethods.gasPhysicalProperties.diffusivity.WilkeLeeDiffusivity(
              this);
    } else if ("Siddiqi Lucas".equals(model)) {
      diffusivityCalc =
          new neqsim.physicalProperties.physicalPropertyMethods.liquidPhysicalProperties.diffusivity.SiddiqiLucasMethod(
              this);
    } else if ("Alkanol amine".equals(model)) {
      diffusivityCalc =
          new neqsim.physicalProperties.physicalPropertyMethods.liquidPhysicalProperties.diffusivity.AmineDiffusivity(
              this);
    }
  }

  /** {@inheritDoc} */
  @Override
  public neqsim.physicalProperties.physicalPropertyMethods.methodInterface.ConductivityInterface getConductivityModel() {
    return conductivityCalc;
  }

  /** {@inheritDoc} */
  @Override
  public void setBinaryDiffusionCoefficientMethod(int i) {
    binaryDiffusionCoefficientMethod = i;
  }

  /** {@inheritDoc} */
  @Override
  public void setMulticomponentDiffusionMethod(int i) {
    multicomponentDiffusionMethod = i;
  }

  /**
   * <p>
   * calcKinematicViscosity.
   * </p>
   *
   * @return a double
   */
  public double calcKinematicViscosity() {
    kinematicViscosity = viscosity / phase.getDensity();
    return kinematicViscosity;
  }

  /**
   * <p>
   * setPhases.
   * </p>
   */
  public void setPhases() {
    conductivityCalc.setPhase(this);
    densityCalc.setPhase(this);
    viscosityCalc.setPhase(this);
    diffusivityCalc.setPhase(this);
  }

  /** {@inheritDoc} */
  @Override
  public void setPhase(PhaseInterface phase) {
    this.phase = phase;
    this.setPhases();
  }

  /** {@inheritDoc} */
  @Override
  public void init(PhaseInterface phase) {
    this.phase = phase;
    this.setPhases();
    try {
      density = densityCalc.calcDensity();
      viscosity = viscosityCalc.calcViscosity();
      kinematicViscosity = this.calcKinematicViscosity();
      diffusivityCalc.calcDiffusionCoefficients(binaryDiffusionCoefficientMethod,
          multicomponentDiffusionMethod);
      // diffusivityCalc.calcEffectiveDiffusionCoefficients();
      conductivity = conductivityCalc.calcConductivity();
    } catch (Exception ex) {
      // might be a chance that entering here ends in an infinite loop...
      phase.resetPhysicalProperties();
      phase.initPhysicalProperties();
    }
  }

  /** {@inheritDoc} */
  @Override
  public void init(PhaseInterface phase, String type) {
    if (type.equals("density")) {
      density = densityCalc.calcDensity();
    } else if (type.equals("viscosity")) {
      viscosity = viscosityCalc.calcViscosity();
    } else if (type.equals("conductivity")) {
      conductivity = conductivityCalc.calcConductivity();
    } else {
      init(phase);
    }
  }

  /** {@inheritDoc} */
  @Override
  public double getViscosityOfWaxyOil(double waxVolumeFraction, double shareRate) {
    return viscosity * (Math.exp(waxViscosityParameter[0] * waxVolumeFraction)
        + waxViscosityParameter[1] * waxVolumeFraction / Math.sqrt(shareRate)
        + waxViscosityParameter[2] * Math.pow(waxVolumeFraction, 4.0) / shareRate);
  }

  /** {@inheritDoc} */
  @Override
  public double getViscosity() {
    if (viscosity < 0) {
      return 1e-5;
    }
    return viscosity;
  }

  /** {@inheritDoc} */
  @Override
  public double getPureComponentViscosity(int i) {
    return viscosityCalc.getPureComponentViscosity(i);
  }

  /** {@inheritDoc} */
  @Override
  public double getConductivity() {
    if (conductivity < 0) {
      return 1e-5;
    }
    return conductivity;
  }

  /** {@inheritDoc} */
  @Override
  public double getDensity() {
    return density;
  }

  /** {@inheritDoc} */
  @Override
  public double calcDensity() {
    return densityCalc.calcDensity();
  }

  /** {@inheritDoc} */
  @Override
  public double getKinematicViscosity() {
    if (kinematicViscosity < 0) {
      return 1e-5;
    }
    return kinematicViscosity;
  }

  /**
   * {@inheritDoc}
   *
   * @param i a int
   * @param j a int
   * @return a double
   */
  public double getDiffusionCoefficient(int i, int j) {
    return diffusivityCalc.getMaxwellStefanBinaryDiffusionCoefficient(i, j);
  }

  /** {@inheritDoc} */
  @Override
  public double getDiffusionCoefficient(String comp1, String comp2) {
    return diffusivityCalc.getMaxwellStefanBinaryDiffusionCoefficient(
        phase.getComponent(comp1).getComponentNumber(),
        phase.getComponent(comp2).getComponentNumber());
  }

  /** {@inheritDoc} */
  @Override
  public double getFickDiffusionCoefficient(int i, int j) {
    return diffusivityCalc.getFickBinaryDiffusionCoefficient(i, j);
  }

  /** {@inheritDoc} */
  @Override
  public void calcEffectiveDiffusionCoefficients() {
    this.init(phase);
    diffusivityCalc.calcEffectiveDiffusionCoefficients();
  }

  /** {@inheritDoc} */
  @Override
  public double getEffectiveDiffusionCoefficient(int i) {
    return diffusivityCalc.getEffectiveDiffusionCoefficient(i);
  }

  /** {@inheritDoc} */
  @Override
  public double getEffectiveDiffusionCoefficient(String compName) {
    return diffusivityCalc
        .getEffectiveDiffusionCoefficient(phase.getComponent(compName).getComponentNumber());
  }

  /** {@inheritDoc} */
  @Override
  public double getEffectiveSchmidtNumber(int i) {
    return getKinematicViscosity() / diffusivityCalc.getEffectiveDiffusionCoefficient(i);
  }

  /**
   * <p>
   * Getter for the field <code>waxViscosityParameter</code>.
   * </p>
   *
   * @return the waxViscosityParameter
   */
  public double[] getWaxViscosityParameter() {
    return waxViscosityParameter;
  }

  /**
   * <p>
   * Setter for the field <code>waxViscosityParameter</code>.
   * </p>
   *
   * @param waxViscosityParameter the waxViscosityParameter to set
   */
  public void setWaxViscosityParameter(double[] waxViscosityParameter) {
    this.waxViscosityParameter = waxViscosityParameter;
  }

  /**
   * <p>
   * Setter for the field <code>waxViscosityParameter</code>.
   * </p>
   *
   * @param paramNumber a int
   * @param waxViscosityParameter a double
   */
  public void setWaxViscosityParameter(int paramNumber, double waxViscosityParameter) {
    this.waxViscosityParameter[paramNumber] = waxViscosityParameter;
  }
}
