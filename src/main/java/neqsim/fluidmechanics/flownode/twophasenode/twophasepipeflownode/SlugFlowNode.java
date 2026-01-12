package neqsim.fluidmechanics.flownode.twophasenode.twophasepipeflownode;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.fluidmechanics.flownode.FlowNodeInterface;
import neqsim.fluidmechanics.flownode.fluidboundary.interphasetransportcoefficient.interphasetwophase.interphasepipeflow.InterphaseSlugFlow;
import neqsim.fluidmechanics.flownode.twophasenode.TwoPhaseFlowNode;
import neqsim.fluidmechanics.geometrydefinitions.GeometryDefinitionInterface;
import neqsim.fluidmechanics.geometrydefinitions.pipe.PipeData;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * SlugFlowNode class.
 * </p>
 * 
 * <p>
 * Represents a flow node for slug flow regime in two-phase pipe flow. Slug flow is characterized by
 * alternating liquid slugs and elongated gas bubbles (Taylor bubbles). This regime is common in
 * horizontal and near-horizontal pipelines at intermediate gas and liquid flow rates.
 * </p>
 * 
 * <p>
 * The model accounts for:
 * <ul>
 * <li>Slug body - liquid-rich region with dispersed gas bubbles</li>
 * <li>Taylor bubble - elongated gas bubble with falling liquid film</li>
 * <li>Mixing zone at slug front and back</li>
 * <li>Enhanced heat and mass transfer due to slug passage</li>
 * </ul>
 *
 * @author esol
 * @version 1.0
 */
public class SlugFlowNode extends TwoPhaseFlowNode {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(SlugFlowNode.class);

  /** Slug frequency in Hz. */
  private double slugFrequency = 0.0;

  /** Slug length to diameter ratio. */
  private double slugLengthRatio = 30.0;

  /** Slug translational velocity. */
  private double slugTranslationalVelocity = 0.0;

  /**
   * <p>
   * Constructor for SlugFlowNode.
   * </p>
   */
  public SlugFlowNode() {
    this.flowNodeType = "slug";
  }

  /**
   * <p>
   * Constructor for SlugFlowNode.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   * @param pipe a {@link neqsim.fluidmechanics.geometrydefinitions.GeometryDefinitionInterface}
   *        object
   */
  public SlugFlowNode(SystemInterface system, GeometryDefinitionInterface pipe) {
    super(system, pipe);
    this.flowNodeType = "slug";
    this.interphaseTransportCoefficient = new InterphaseSlugFlow(this);
    this.fluidBoundary =
        new neqsim.fluidmechanics.flownode.fluidboundary.heatmasstransfercalc.nonequilibriumfluidboundary.filmmodelboundary.KrishnaStandartFilmModel(
            this);
  }

  /**
   * <p>
   * Constructor for SlugFlowNode.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   * @param interphaseSystem a {@link neqsim.thermo.system.SystemInterface} object
   * @param pipe a {@link neqsim.fluidmechanics.geometrydefinitions.GeometryDefinitionInterface}
   *        object
   */
  public SlugFlowNode(SystemInterface system, SystemInterface interphaseSystem,
      GeometryDefinitionInterface pipe) {
    super(system, pipe);
    this.flowNodeType = "slug";
    this.interphaseTransportCoefficient = new InterphaseSlugFlow(this);
    this.fluidBoundary =
        new neqsim.fluidmechanics.flownode.fluidboundary.heatmasstransfercalc.nonequilibriumfluidboundary.filmmodelboundary.KrishnaStandartFilmModel(
            this);
  }

  /** {@inheritDoc} */
  @Override
  public SlugFlowNode clone() {
    SlugFlowNode clonedSystem = null;
    try {
      clonedSystem = (SlugFlowNode) super.clone();
    } catch (Exception ex) {
      logger.error(ex.getMessage());
    }

    return clonedSystem;
  }

  /** {@inheritDoc} */
  @Override
  public void init() {
    inclination = 0.0;
    this.calcContactLength();
    this.calcSlugCharacteristics();
    super.init();
  }

  /**
   * <p>
   * Calculates slug flow characteristics including frequency and translational velocity.
   * </p>
   * 
   * <p>
   * Uses correlations from Gregory and Scott (1969) and Bendiksen (1984).
   * </p>
   */
  public void calcSlugCharacteristics() {
    // Guard against uninitialized pipe or zero diameter
    if (pipe == null || pipe.getDiameter() <= 0) {
      slugFrequency = 0.0;
      slugTranslationalVelocity = 0.0;
      return;
    }

    // Mixture velocity
    double vmix = getVelocity(0) * phaseFraction[0] + getVelocity(1) * phaseFraction[1];

    // Guard against NaN velocities
    if (Double.isNaN(vmix)) {
      vmix = 0.0;
    }

    // Froude number for horizontal flow
    double froude = vmix / Math.sqrt(gravity * pipe.getDiameter());

    // Slug frequency correlation (Gregory and Scott, 1969)
    // f = 0.0226 * (vmix / D) * (19.75/vmix + vmix)^1.2
    if (vmix > 0.01) {
      slugFrequency = 0.0226 * (vmix / pipe.getDiameter()) * Math.pow(19.75 / vmix + vmix, 1.2);
    } else {
      slugFrequency = 0.0;
    }

    // Slug translational velocity (Bendiksen, 1984)
    // Vt = Co * Vmix + Vd
    double distributionCoeff = 1.2; // Distribution coefficient for turbulent flow
    double driftVelocity = 0.0; // Drift velocity for horizontal flow
    if (froude < 3.5) {
      driftVelocity = 0.54 * Math.sqrt(gravity * pipe.getDiameter());
    }
    slugTranslationalVelocity = distributionCoeff * vmix + driftVelocity;
  }

  /** {@inheritDoc} */
  @Override
  public double calcContactLength() {
    // For slug flow, the contact lengths are based on average holdup over the slug unit
    // The interphase contact area is the pipe cross-section (gas-liquid interface at ends)
    // plus the film-bubble interface in the Taylor bubble region

    // Average liquid holdup in slug unit
    double liquidHoldupSlug = 0.85; // Liquid holdup in slug body
    double liquidHoldupFilm = phaseFraction[1]; // Liquid holdup in film region
    double slugUnitFraction = 0.6; // Fraction of unit occupied by slug body

    double avgLiquidHoldup =
        slugUnitFraction * liquidHoldupSlug + (1 - slugUnitFraction) * liquidHoldupFilm;

    // Wall contact lengths based on average holdup
    // Simplified geometry assuming cylindrical pipe
    double phaseAngle =
        pi * avgLiquidHoldup + Math.pow(3.0 * pi / 2.0, 1.0 / 3.0) * (1.0 - 2.0 * avgLiquidHoldup
            + Math.pow(avgLiquidHoldup, 1.0 / 3.0) - Math.pow(1 - avgLiquidHoldup, 1.0 / 3.0));

    wallContactLength[1] = phaseAngle * pipe.getDiameter();
    wallContactLength[0] = pi * pipe.getDiameter() - wallContactLength[1];

    // Interphase contact length - enhanced due to Taylor bubble interface
    // In slug flow, there's significant gas-liquid contact at both the
    // stratified film region and at slug front/back
    double enhancementFactor = 1.5; // Account for irregular interfaces
    interphaseContactLength[0] = enhancementFactor * pipe.getDiameter() * Math.sin(phaseAngle);
    interphaseContactLength[1] = interphaseContactLength[0];

    return wallContactLength[0];
  }

  /** {@inheritDoc} */
  @Override
  public double calcGasLiquidContactArea() {
    // Contact area includes both the liquid film interface and slug front/back
    interphaseContactArea = pipe.getNodeLength() * interphaseContactLength[0];

    // Add contribution from slug front mixing zone
    double slugFrontContribution = 0.1 * pi * Math.pow(pipe.getDiameter() / 2.0, 2.0);
    interphaseContactArea += slugFrontContribution;

    return interphaseContactArea;
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * For slug flow, the interfacial area per unit volume is a weighted average of Taylor bubble film
   * interface and slug body interface: a = a_Taylor * f_bubble + a_slug * (1-f_bubble)
   * </p>
   */
  @Override
  protected double calcGeometricInterfacialAreaPerVolume() {
    double diameter = pipe.getDiameter();
    if (diameter <= 0) {
      return 0.0;
    }

    // Fraction of slug unit occupied by Taylor bubble vs slug body
    double slugUnitFraction = 0.6; // Fraction occupied by slug body
    double bubbleFraction = 1.0 - slugUnitFraction;

    // Interfacial area in Taylor bubble region (annular-like film)
    double alphaFilm = phaseFraction[1] * 0.3; // Film holdup in bubble region
    double aTaylor = 4.0 / diameter; // Simplified annular-like interface

    // Interfacial area in slug body (dispersed bubbles)
    double alphaSlug = 0.15; // Typical gas holdup in slug body
    double bubbleDiameter = 0.005; // Typical bubble size in slug body
    double aSlug = 6.0 * alphaSlug / bubbleDiameter;

    // Weighted average
    return aTaylor * bubbleFraction + aSlug * slugUnitFraction;
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * For slug flow, uses enhanced model accounting for slug frequency and mixing effects.
   * </p>
   */
  @Override
  protected double calcEmpiricalInterfacialAreaPerVolume() {
    double baseArea = calcGeometricInterfacialAreaPerVolume();

    // Slug mixing enhancement - higher frequency leads to more interfacial renewal
    double mixingEnhancement = 1.0;
    if (slugFrequency > 0) {
      // Enhancement factor based on slug frequency
      mixingEnhancement = 1.0 + 0.5 * Math.min(slugFrequency, 2.0);
    }

    return baseArea * mixingEnhancement;
  }

  /** {@inheritDoc} */
  @Override
  public FlowNodeInterface getNextNode() {
    SlugFlowNode newNode = this.clone();
    return newNode;
  }

  /**
   * <p>
   * Getter for slug frequency.
   * </p>
   *
   * @return slug frequency in Hz
   */
  public double getSlugFrequency() {
    return slugFrequency;
  }

  /**
   * <p>
   * Setter for slug frequency.
   * </p>
   *
   * @param frequency slug frequency in Hz
   */
  public void setSlugFrequency(double frequency) {
    this.slugFrequency = frequency;
  }

  /**
   * <p>
   * Getter for slug length ratio.
   * </p>
   *
   * @return slug length to diameter ratio
   */
  public double getSlugLengthRatio() {
    return slugLengthRatio;
  }

  /**
   * <p>
   * Setter for slug length ratio.
   * </p>
   *
   * @param ratio slug length to diameter ratio
   */
  public void setSlugLengthRatio(double ratio) {
    this.slugLengthRatio = ratio;
  }

  /**
   * <p>
   * Getter for slug translational velocity.
   * </p>
   *
   * @return slug translational velocity in m/s
   */
  public double getSlugTranslationalVelocity() {
    return slugTranslationalVelocity;
  }

  /**
   * <p>
   * main.
   * </p>
   *
   * @param args an array of {@link java.lang.String} objects
   */
  @SuppressWarnings("unused")
  @ExcludeFromJacocoGeneratedReport
  public static void main(String[] args) {
    SystemInterface testSystem = new neqsim.thermo.system.SystemSrkCPAstatoil(325.3, 100.0);
    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    PipeData pipe1 = new PipeData(0.1, 0.0001);

    testSystem.addComponent("methane", 0.5, "MSm3/day", 0);
    testSystem.addComponent("water", 10.0, "kg/hr", 1);
    testSystem.createDatabase(true);
    testSystem.setMixingRule(10);
    testSystem.initPhysicalProperties();

    testOps.TPflash();

    SlugFlowNode testNode = new SlugFlowNode(testSystem, pipe1);
    testNode.setVelocityIn(0.5);
    testNode.init();
    testNode.initFlowCalc();

    System.out.println("Slug frequency: " + testNode.getSlugFrequency() + " Hz");
    System.out.println(
        "Slug translational velocity: " + testNode.getSlugTranslationalVelocity() + " m/s");
    System.out.println("Interphase contact area: " + testNode.getInterphaseContactArea() + " m2");
  }
}
