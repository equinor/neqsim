/*
 * PhysicalProperties.java
 *
 * Created on 29. oktober 2000, 16:13
 */

package neqsim.physicalproperties.system;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.physicalproperties.PhysicalPropertyType;
import neqsim.physicalproperties.methods.commonphasephysicalproperties.conductivity.PFCTConductivityMethodMod86;
import neqsim.physicalproperties.methods.commonphasephysicalproperties.diffusivity.CorrespondingStatesDiffusivity;
import neqsim.physicalproperties.methods.commonphasephysicalproperties.viscosity.FrictionTheoryViscosityMethod;
import neqsim.physicalproperties.methods.commonphasephysicalproperties.viscosity.KTAViscosityMethod;
import neqsim.physicalproperties.methods.commonphasephysicalproperties.viscosity.KTAViscosityMethodMod;
import neqsim.physicalproperties.methods.commonphasephysicalproperties.viscosity.LBCViscosityMethod;
import neqsim.physicalproperties.methods.commonphasephysicalproperties.viscosity.MethaneViscosityMethod;
import neqsim.physicalproperties.methods.commonphasephysicalproperties.viscosity.MuznyModViscosityMethod;
import neqsim.physicalproperties.methods.commonphasephysicalproperties.viscosity.MuznyViscosityMethod;
import neqsim.physicalproperties.methods.commonphasephysicalproperties.viscosity.PFCTViscosityMethodHeavyOil;
import neqsim.physicalproperties.methods.commonphasephysicalproperties.viscosity.PFCTViscosityMethodMod86;
import neqsim.physicalproperties.methods.gasphysicalproperties.conductivity.ChungConductivityMethod;
import neqsim.physicalproperties.methods.gasphysicalproperties.diffusivity.WilkeLeeDiffusivity;
import neqsim.physicalproperties.methods.liquidphysicalproperties.density.Costald;
import neqsim.physicalproperties.methods.liquidphysicalproperties.diffusivity.AmineDiffusivity;
import neqsim.physicalproperties.methods.liquidphysicalproperties.diffusivity.SiddiqiLucasMethod;
import neqsim.physicalproperties.methods.methodinterface.ConductivityInterface;
import neqsim.physicalproperties.methods.methodinterface.DensityInterface;
import neqsim.physicalproperties.methods.methodinterface.DiffusivityInterface;
import neqsim.physicalproperties.methods.methodinterface.ViscosityInterface;
import neqsim.physicalproperties.mixingrule.PhysicalPropertyMixingRuleInterface;
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
public abstract class PhysicalProperties implements Cloneable, ThermodynamicConstantsInterface {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(PhysicalProperties.class);

  protected PhaseInterface phase;
  protected int binaryDiffusionCoefficientMethod;
  protected int multicomponentDiffusionMethod;
  private PhysicalPropertyMixingRuleInterface mixingRule = null;
  public ConductivityInterface conductivityCalc;
  public ViscosityInterface viscosityCalc;
  public DiffusivityInterface diffusivityCalc;
  public DensityInterface densityCalc;
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

  /**
   * <p>
   * Getter for property <code>phase</code>.
   * </p>
   *
   * @return a {@link neqsim.thermo.phase.PhaseInterface} object
   */
  public PhaseInterface getPhase() {
    return phase;
  }

  /**
   * <p>
   * getMixingRule.
   * </p>
   *
   * @return a {@link neqsim.physicalproperties.mixingrule.PhysicalPropertyMixingRuleInterface}
   *         object
   */
  public PhysicalPropertyMixingRuleInterface getMixingRule() {
    return mixingRule;
  }

  /**
   * <p>
   * setMixingRule.
   * </p>
   *
   * @param mixingRule a
   *        {@link neqsim.physicalproperties.mixingrule.PhysicalPropertyMixingRuleInterface} object
   */
  public void setMixingRule(PhysicalPropertyMixingRuleInterface mixingRule) {
    this.mixingRule = mixingRule;
  }

  /**
   * <p>
   * setMixingRuleNull.
   * </p>
   */
  public void setMixingRuleNull() {
    setMixingRule(null);
  }

  /**
   * <p>
   * getViscosityModel.
   * </p>
   *
   * @return a {@link neqsim.physicalproperties.methods.methodinterface.ViscosityInterface} object
   */
  public ViscosityInterface getViscosityModel() {
    return viscosityCalc;
  }

  /**
   * <p>
   * setDensityModel.
   * </p>
   *
   * @param model a {@link java.lang.String} object
   */
  public void setDensityModel(String model) {
    if ("Peneloux volume shift".equals(model)) {
      densityCalc =
          new neqsim.physicalproperties.methods.liquidphysicalproperties.density.Density(this);
    } else if ("Costald".equals(model)) {
      densityCalc = new Costald(this);
    }
  }

  /**
   * <p>
   * setConductivityModel.
   * </p>
   *
   * @param model a {@link java.lang.String} object
   */
  public void setConductivityModel(String model) {
    if ("PFCT".equals(model)) {
      conductivityCalc = new PFCTConductivityMethodMod86(this);
    } else if ("polynom".equals(model)) {
      conductivityCalc =
          new neqsim.physicalproperties.methods.liquidphysicalproperties.conductivity.Conductivity(
              this);
    } else if ("Chung".equals(model)) {
      conductivityCalc = new ChungConductivityMethod(this);
    } else {
      conductivityCalc = new PFCTConductivityMethodMod86(this);
    }
  }

  /**
   * <p>
   * setViscosityModel.
   * </p>
   *
   * @param model a {@link java.lang.String} object
   */
  public void setViscosityModel(String model) {
    if ("polynom".equals(model)) {
      viscosityCalc =
          new neqsim.physicalproperties.methods.liquidphysicalproperties.viscosity.Viscosity(this);
    } else if ("friction theory".equals(model)) {
      viscosityCalc = new FrictionTheoryViscosityMethod(this);
    } else if ("LBC".equals(model)) {
      viscosityCalc = new LBCViscosityMethod(this);
    } else if ("PFCT".equals(model)) {
      viscosityCalc = new PFCTViscosityMethodMod86(this);
    } else if ("PFCT-Heavy-Oil".equals(model)) {
      viscosityCalc = new PFCTViscosityMethodHeavyOil(this);
    } else if ("KTA".equals(model)) {
      viscosityCalc = new KTAViscosityMethod(this);
    } else if ("KTA_mod".equals(model)) {
      viscosityCalc = new KTAViscosityMethodMod(this);
    } else if ("Muzny".equals(model)) {
      viscosityCalc = new MuznyViscosityMethod(this);
    } else if ("Muzny_mod".equals(model)) {
      viscosityCalc = new MuznyModViscosityMethod(this);
    } else if ("MethaneModel".equals(model)) {
      viscosityCalc = new MethaneViscosityMethod(this);
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

  /**
   * <p>
   * getConductivityModel.
   * </p>
   *
   * @return a {@link neqsim.physicalproperties.methods.methodinterface.ConductivityInterface}
   *         object
   */
  public ConductivityInterface getConductivityModel() {
    return conductivityCalc;
  }

  /**
   * <p>
   * setBinaryDiffusionCoefficientMethod.
   * </p>
   *
   * @param i a int
   */
  public void setBinaryDiffusionCoefficientMethod(int i) {
    binaryDiffusionCoefficientMethod = i;
  }

  /**
   * <p>
   * setMulticomponentDiffusionMethod.
   * </p>
   *
   * @param i a int
   */
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

  /**
   * <p>
   * Setter for property <code>phase</code>. Will also set the phase for all physicalpropertymethods
   * using setPhases. Safe to call from constructor.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   */
  public void setPhase(PhaseInterface phase) {
    this.phase = phase;
    this.setPhases();
  }

  /**
   * <p>
   * Initialize / calculate all physical properties of phase.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   */
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

  /**
   * <p>
   * Initialize / calculate a specific physical property of phase.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param ppt PhysicalPropertyType enum object.
   */
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

  /**
   * <p>
   * Initialize / calculate a specific physical property of phase.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param name Name of physical property.
   */
  public void init(PhaseInterface phase, String name) {
    init(phase, PhysicalPropertyType.byName(name));
  }

  /**
   * <p>
   * getViscosityOfWaxyOil.
   * </p>
   *
   * @param waxVolumeFraction a double
   * @param shareRate a double
   * @return a double
   */
  public double getViscosityOfWaxyOil(double waxVolumeFraction, double shareRate) {
    return viscosity * (Math.exp(waxViscosityParameter[0] * waxVolumeFraction)
        + waxViscosityParameter[1] * waxVolumeFraction / Math.sqrt(shareRate)
        + waxViscosityParameter[2] * Math.pow(waxVolumeFraction, 4.0) / shareRate);
  }

  /**
   * <p>
   * getViscosity.
   * </p>
   *
   * @return a double
   */
  public double getViscosity() {
    if (viscosity < 0) {
      return 1e-5;
    }
    return viscosity;
  }

  /**
   * <p>
   * getPureComponentViscosity.
   * </p>
   *
   * @param i a int
   * @return a double
   */
  public double getPureComponentViscosity(int i) {
    return viscosityCalc.getPureComponentViscosity(i);
  }

  /**
   * <p>
   * getConductivity.
   * </p>
   *
   * @return a double
   */
  public double getConductivity() {
    if (conductivity < 0) {
      return 1e-5;
    }
    return conductivity;
  }

  /**
   * <p>
   * calcDensity.
   * </p>
   *
   * @return a double
   */
  public double calcDensity() {
    return densityCalc.calcDensity();
  }

  /**
   * <p>
   * getDensity.
   * </p>
   *
   * @return a double
   */
  public double getDensity() {
    return density;
  }

  /**
   * <p>
   * getKinematicViscosity.
   * </p>
   *
   * @return a double
   */
  public double getKinematicViscosity() {
    if (kinematicViscosity < 0) {
      return 1e-5;
    }
    return kinematicViscosity;
  }

  /**
   * <p>
   * getDiffusionCoefficient.
   * </p>
   *
   * @param i a int
   * @param j a int
   * @return a double
   */
  public double getDiffusionCoefficient(int i, int j) {
    return diffusivityCalc.getMaxwellStefanBinaryDiffusionCoefficient(i, j);
  }

  /**
   * <p>
   * getDiffusionCoefficient.
   * </p>
   *
   * @param comp1 a {@link java.lang.String} object
   * @param comp2 a {@link java.lang.String} object
   * @return a double
   */
  public double getDiffusionCoefficient(String comp1, String comp2) {
    return diffusivityCalc.getMaxwellStefanBinaryDiffusionCoefficient(
        phase.getComponent(comp1).getComponentNumber(),
        phase.getComponent(comp2).getComponentNumber());
  }

  /**
   * <p>
   * getFickDiffusionCoefficient.
   * </p>
   *
   * @param i a int
   * @param j a int
   * @return a double
   */
  public double getFickDiffusionCoefficient(int i, int j) {
    return diffusivityCalc.getFickBinaryDiffusionCoefficient(i, j);
  }

  /**
   * <p>
   * calcEffectiveDiffusionCoefficients.
   * </p>
   */
  public void calcEffectiveDiffusionCoefficients() {
    this.init(phase);
    diffusivityCalc.calcEffectiveDiffusionCoefficients();
  }

  /**
   * <p>
   * getEffectiveDiffusionCoefficient.
   * </p>
   *
   * @param i a int
   * @return a double
   */
  public double getEffectiveDiffusionCoefficient(int i) {
    return diffusivityCalc.getEffectiveDiffusionCoefficient(i);
  }

  /**
   * <p>
   * getEffectiveDiffusionCoefficient.
   * </p>
   *
   * @param compName a {@link java.lang.String} object
   * @return a double
   */
  public double getEffectiveDiffusionCoefficient(String compName) {
    return diffusivityCalc
        .getEffectiveDiffusionCoefficient(phase.getComponent(compName).getComponentNumber());
  }

  /**
   * <p>
   * getEffectiveSchmidtNumber.
   * </p>
   *
   * @param i a int
   * @return a double
   */
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
