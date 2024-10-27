/*
 * PhysicalProperties.java
 *
 * Created on 29. oktober 2000, 16:13
 */

package neqsim.physicalproperties.physicalpropertysystem;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.physicalproperties.PhysicalPropertyType;
import neqsim.physicalproperties.physicalpropertymethods.commonphasephysicalproperties.conductivity.PFCTConductivityMethodMod86;
import neqsim.physicalproperties.physicalpropertymethods.commonphasephysicalproperties.diffusivity.CorrespondingStatesDiffusivity;
import neqsim.physicalproperties.physicalpropertymethods.commonphasephysicalproperties.viscosity.FrictionTheoryViscosityMethod;
import neqsim.physicalproperties.physicalpropertymethods.commonphasephysicalproperties.viscosity.LBCViscosityMethod;
import neqsim.physicalproperties.physicalpropertymethods.commonphasephysicalproperties.viscosity.PFCTViscosityMethodHeavyOil;
import neqsim.physicalproperties.physicalpropertymethods.commonphasephysicalproperties.viscosity.PFCTViscosityMethodMod86;
import neqsim.physicalproperties.physicalpropertymethods.gasphysicalproperties.conductivity.ChungConductivityMethod;
import neqsim.physicalproperties.physicalpropertymethods.gasphysicalproperties.diffusivity.WilkeLeeDiffusivity;
import neqsim.physicalproperties.physicalpropertymethods.liquidphysicalproperties.diffusivity.AmineDiffusivity;
import neqsim.physicalproperties.physicalpropertymethods.liquidphysicalproperties.diffusivity.SiddiqiLucasMethod;
import neqsim.physicalproperties.physicalpropertymethods.methodinterface.ConductivityInterface;
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
  private static final long serialVersionUID = 1000;
  static Logger logger = LogManager.getLogger(PhysicalProperties.class);

  protected PhaseInterface phase;
  protected int binaryDiffusionCoefficientMethod;
  protected int multicomponentDiffusionMethod;
  private neqsim.physicalproperties.mixingrule.PhysicalPropertyMixingRuleInterface mixingRule =
      null;
  public neqsim.physicalproperties.physicalpropertymethods.methodinterface.ConductivityInterface conductivityCalc;
  public neqsim.physicalproperties.physicalpropertymethods.methodinterface.ViscosityInterface viscosityCalc;
  public neqsim.physicalproperties.physicalpropertymethods.methodinterface.DiffusivityInterface diffusivityCalc;
  public neqsim.physicalproperties.physicalpropertymethods.methodinterface.DensityInterface densityCalc;
  public double kinematicViscosity = 0;
  public double density = 0;
  public double viscosity = 0;
  public double conductivity = 0;
  private double[] waxViscosityParameter = {37.82, 83.96, 8.559e6};

  /**
   * <p>
   * Constructor for PhysicalProperties.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   */
  public PhysicalProperties(PhaseInterface phase) {
    setPhase(phase);
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
    this(phase);
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
  public neqsim.physicalproperties.mixingrule.PhysicalPropertyMixingRuleInterface getMixingRule() {
    return mixingRule;
  }

  /** {@inheritDoc} */
  @Override
  public void setMixingRule(
      neqsim.physicalproperties.mixingrule.PhysicalPropertyMixingRuleInterface mixingRule) {
    this.mixingRule = mixingRule;
  }

  /** {@inheritDoc} */
  @Override
  public void setMixingRuleNull() {
    setMixingRule(null);
  }

  /** {@inheritDoc} */
  @Override
  public neqsim.physicalproperties.physicalpropertymethods.methodinterface.ViscosityInterface getViscosityModel() {
    return viscosityCalc;
  }

  /** {@inheritDoc} */
  @Override
  public void setDensityModel(String model) {
    if ("Peneloux volume shift".equals(model)) {
      densityCalc =
          new neqsim.physicalproperties.physicalpropertymethods.liquidphysicalproperties.density.Density(
              this);
    } else if ("Costald".equals(model)) {
      densityCalc =
          new neqsim.physicalproperties.physicalpropertymethods.liquidphysicalproperties.density.Costald(
              this);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void setConductivityModel(String model) {
    if ("PFCT".equals(model)) {
      conductivityCalc = new PFCTConductivityMethodMod86(this);
    } else if ("polynom".equals(model)) {
      conductivityCalc =
          new neqsim.physicalproperties.physicalpropertymethods.liquidphysicalproperties.conductivity.Conductivity(
              this);
    } else if ("Chung".equals(model)) {
      conductivityCalc = new ChungConductivityMethod(this);
    } else {
      conductivityCalc = new PFCTConductivityMethodMod86(this);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void setViscosityModel(String model) {
    if ("polynom".equals(model)) {
      viscosityCalc =
          new neqsim.physicalproperties.physicalpropertymethods.liquidphysicalproperties.viscosity.Viscosity(
              this);
    } else if ("friction theory".equals(model)) {
      viscosityCalc = new FrictionTheoryViscosityMethod(this);
    } else if ("LBC".equals(model)) {
      viscosityCalc = new LBCViscosityMethod(this);
    } else if ("PFCT".equals(model)) {
      viscosityCalc = new PFCTViscosityMethodMod86(this);
    } else if ("PFCT-Heavy-Oil".equals(model)) {
      viscosityCalc = new PFCTViscosityMethodHeavyOil(this);
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
      diffusivityCalc = new CorrespondingStatesDiffusivity(this);
    } else if ("Wilke Lee".equals(model)) {
      diffusivityCalc = new WilkeLeeDiffusivity(this);
    } else if ("Siddiqi Lucas".equals(model)) {
      diffusivityCalc = new SiddiqiLucasMethod(this);
    } else if ("Alkanol amine".equals(model)) {
      diffusivityCalc = new AmineDiffusivity(this);
    }
  }

  /** {@inheritDoc} */
  @Override
  public ConductivityInterface getConductivityModel() {
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
   * Set phase information for all physical property calc methods, i.e., subclasses of
   * physicalpropertymethods using setPhase(this). NB! Safe even if calc methods are null, e.g.,
   * from constructors.
   * </p>
   */
  public void setPhases() {
    // Check for null to make it safe to call this function from subclass constructors.
    if (conductivityCalc != null) {
      conductivityCalc.setPhase(this);
    }
    if (densityCalc != null) {
      densityCalc.setPhase(this);
    }
    if (viscosityCalc != null) {
      viscosityCalc.setPhase(this);
    }
    if (diffusivityCalc != null) {
      diffusivityCalc.setPhase(this);
    }
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
    this.setPhase(phase);
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
  public void init(PhaseInterface phase, PhysicalPropertyType ppt) {
    switch (ppt) {
      case MASS_DENSITY:
        densityCalc.setPhase(this);
        density = densityCalc.calcDensity();
        break;
      case DYNAMIC_VISCOSITY:
        viscosityCalc.setPhase(this);
        viscosity = viscosityCalc.calcViscosity();
        break;
      case THERMAL_CONDUCTIVITY:
        conductivityCalc.setPhase(this);
        conductivity = conductivityCalc.calcConductivity();
        break;
      // case DIFFUSIVITY:
      // diffusivityCalc.setPhase(this);
      // diffusivity = diffusivityCalc.calcDiffusionCoefficients();
      default:
        init(phase);
        break;
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

  /** {@inheritDoc} */
  @Override
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
