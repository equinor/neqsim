package neqsim.fluidmechanics.flownode.twophasenode;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.fluidmechanics.flownode.FlowNode;
import neqsim.fluidmechanics.flownode.fluidboundary.heatmasstransfercalc.InterfacialAreaModel;
import neqsim.fluidmechanics.geometrydefinitions.GeometryDefinitionInterface;
import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * Abstract TwoPhaseFlowNode class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public abstract class TwoPhaseFlowNode extends FlowNode {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(TwoPhaseFlowNode.class);

  /** Interfacial area model selection. */
  protected InterfacialAreaModel interfacialAreaModel = InterfacialAreaModel.GEOMETRIC;

  /** Interfacial area per unit volume (1/m). */
  protected double interfacialAreaPerVolume = 0.0;

  /** User-defined interfacial area per unit volume (1/m) for USER_DEFINED model. */
  protected double userDefinedInterfacialAreaPerVolume = 100.0;

  // public double[] molarMassTransferFlux;
  // public double[] molarMassTransfer;

  /**
   * <p>
   * Constructor for TwoPhaseFlowNode.
   * </p>
   */
  public TwoPhaseFlowNode() {}

  /**
   * <p>
   * Constructor for TwoPhaseFlowNode.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   * @param pipe a {@link neqsim.fluidmechanics.geometrydefinitions.GeometryDefinitionInterface}
   *        object
   */
  public TwoPhaseFlowNode(SystemInterface system, GeometryDefinitionInterface pipe) {
    super(system, pipe);

    // gasPrandtlNumber = new
    // double[getBulkSystem().getPhases()[0].getNumberOfComponents()][getBulkSystem().getPhases()[0].getNumberOfComponents()];
    // liquidPrandtlNumber = new
    // double[getBulkSystem().getPhases()[0].getNumberOfComponents()][getBulkSystem().getPhases()[0].getNumberOfComponents()];
    // molarMassTransferFlux = new
    // double[getBulkSystem().getPhases()[0].getNumberOfComponents()];
    // molarMassTransfer = new
    // double[getBulkSystem().getPhases()[0].getNumberOfComponents()];
    getBulkSystem().initBeta();
    getBulkSystem().init_x_y();
  }

  /** {@inheritDoc} */
  @Override
  public TwoPhaseFlowNode clone() {
    TwoPhaseFlowNode clonedSystem = null;
    try {
      clonedSystem = (TwoPhaseFlowNode) super.clone();
    } catch (Exception ex) {
      logger.error(ex.getMessage());
    }

    // clonedSystem.molarMassTransferFlux = (double[])
    // clonedSystem.molarMassTransferFlux.clone();
    // clonedSystem.molarMassTransfer = (double[])
    // clonedSystem.molarMassTransferFlux.clone();
    clonedSystem.fluidBoundary = fluidBoundary.clone();
    return clonedSystem;
  }

  /**
   * <p>
   * initVelocity.
   * </p>
   *
   * @return a double
   */
  public double initVelocity() {
    getBulkSystem().init(1);
    getBulkSystem().initPhysicalProperties();
    molarFlowRate[1] = getBulkSystem().getPhases()[1].getNumberOfMolesInPhase();
    molarFlowRate[0] = getBulkSystem().getPhases()[0].getNumberOfMolesInPhase();
    massFlowRate[1] = molarFlowRate[1] * getBulkSystem().getPhases()[1].getMolarMass();
    massFlowRate[0] = molarFlowRate[0] * getBulkSystem().getPhases()[0].getMolarMass();
    volumetricFlowRate[0] =
        massFlowRate[0] / getBulkSystem().getPhases()[0].getPhysicalProperties().getDensity();
    volumetricFlowRate[1] =
        massFlowRate[1] / getBulkSystem().getPhases()[1].getPhysicalProperties().getDensity();
    velocity[0] = volumetricFlowRate[0] / (phaseFraction[0] * pipe.getArea());
    velocity[1] = volumetricFlowRate[1] / (phaseFraction[1] * pipe.getArea());
    // System.out.println("velocity " + velocity[0] + " velocity " +velocity[1]);
    return velocity[1];
  }

  /** {@inheritDoc} */
  @Override
  public void initFlowCalc() {
    initVelocity();
    init();

    phaseFraction[0] = getBulkSystem().getBeta();
    phaseFraction[1] = 1.0 - phaseFraction[0];
    double f = 0;

    double fOld = 0;
    double betaOld = 0;
    // double df = 0
    int iterations = 0;
    double step = 100.0;

    do {
      iterations++;
      fOld = f;

      initVelocity();
      init();

      double Fg = 0.5 * bulkSystem.getPhases()[0].getPhysicalProperties().getDensity()
          * wallFrictionFactor[0] * Math.pow(velocity[0], 2.0) * wallContactLength[0]
          / (pipe.getArea() * 4.0);
      double Fl = 0.5 * bulkSystem.getPhases()[1].getPhysicalProperties().getDensity()
          * wallFrictionFactor[1] * Math.pow(velocity[1], 2.0) * wallContactLength[1]
          / (pipe.getArea() * 4.0);
      double Fi = 0.5 * bulkSystem.getPhases()[0].getPhysicalProperties().getDensity()
          * interphaseFrictionFactor[0] * Math.pow(velocity[0] - velocity[1], 2.0)
          * interphaseContactLength[0] / (pipe.getArea() * 4.0);

      f = -phaseFraction[0] * Fl + (1 - phaseFraction[0]) * Fg + Fi
          + (1.0 - phaseFraction[0]) * phaseFraction[0]
              * (bulkSystem.getPhases()[1].getPhysicalProperties().getDensity()
                  - bulkSystem.getPhases()[0].getPhysicalProperties().getDensity())
              * gravity * inclination;

      /*
       * df = -Fl - Fg + (bulkSystem.getPhases()[1].getPhysicalProperties().getDensity() -
       * bulkSystem.getPhases()[0].getPhysicalProperties().getDensity()) * gravity * inclination -
       * Math.pow(phaseFraction[0], 2.0)
       * (bulkSystem.getPhases()[1].getPhysicalProperties().getDensity() -
       * bulkSystem.getPhases()[0].getPhysicalProperties().getDensity()) gravity * inclination;
       */

      if (f > 0) {
        phaseFraction[0] += (betaOld - phaseFraction[0]);
        if (fOld < 0) {
          step *= 5.0;
        }
      } else {
        betaOld = phaseFraction[0];
        phaseFraction[0] -= phaseFraction[0] / step;
      }
      phaseFraction[1] = 1.0 - phaseFraction[0];
      // System.out.println("f " + f + " iterations " + iterations + " beta " + phaseFraction[0]);
    }
    // while (Math.abs(f) > 1e-6 && iterations < 100);
    while (Math.abs((f - fOld) / f) > 1e-8 && iterations < 100);

    if (iterations == 10000) {
      System.out.println("error in void init calc");
    }
    // System.out.println("f " + f + " iterations " + iterations + " beta " +
    // phaseFraction[0]);
    this.init();
  }

  /**
   * <p>
   * calcHydraulicDiameter.
   * </p>
   *
   * @return a double
   */
  public double calcHydraulicDiameter() {
    hydraulicDiameter[0] = 4.0 * phaseFraction[0] * pipe.getArea()
        / (wallContactLength[0] + interphaseContactLength[0]);
    hydraulicDiameter[1] = 4.0 * phaseFraction[1] * pipe.getArea() / wallContactLength[1];
    return hydraulicDiameter[0];
  }

  /**
   * <p>
   * calcReynoldNumber.
   * </p>
   *
   * @return a double
   */
  public double calcReynoldNumber() {
    reynoldsNumber[1] = velocity[1] * hydraulicDiameter[1]
        * bulkSystem.getPhases()[1].getPhysicalProperties().getDensity()
        / bulkSystem.getPhases()[1].getPhysicalProperties().getViscosity();
    reynoldsNumber[0] = velocity[0] * hydraulicDiameter[0]
        * bulkSystem.getPhases()[0].getPhysicalProperties().getDensity()
        / bulkSystem.getPhases()[0].getPhysicalProperties().getViscosity();
    return reynoldsNumber[1];
  }

  /**
   * <p>
   * calcWallFrictionFactor.
   * </p>
   *
   * @return a double
   */
  public double calcWallFrictionFactor() {
    for (int i = 0; i < 2; i++) {
      wallFrictionFactor[i] = Math.pow(
          (1.0 / (-1.8 * Math.log10(6.9 / getReynoldsNumber(i)
              * Math.pow(pipe.getRelativeRoughnes(this.getHydraulicDiameter(i)) / 3.7, 1.11)))),
          2.0);
    }
    return wallFrictionFactor[0];
  }

  /** {@inheritDoc} */
  @Override
  public void setFluxes(double[] dn) {
    for (int i = 0; i < getBulkSystem().getPhases()[0].getNumberOfComponents(); i++) {
      getBulkSystem().getPhases()[0].addMoles(i, -dn[i]);
      getBulkSystem().getPhases()[1].addMoles(i, +dn[i]);

      getInterphaseSystem().getPhases()[0].addMoles(i, -dn[i]);
      getInterphaseSystem().getPhases()[1].addMoles(i, +dn[i]);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void updateMolarFlow() {
    for (int phaseNum = 0; phaseNum < 2; phaseNum++) {
      double targetMolarFlow = molarFlowRate[phaseNum];
      if (!Double.isFinite(targetMolarFlow) || targetMolarFlow < 0.0) {
        continue;
      }

      double currentMolesInPhase = getBulkSystem().getPhase(phaseNum).getNumberOfMolesInPhase();
      if (!Double.isFinite(currentMolesInPhase) || currentMolesInPhase <= 0.0) {
        continue;
      }

      if (targetMolarFlow > 1e-100) {
        double scale = targetMolarFlow / currentMolesInPhase;
        if (!Double.isFinite(scale) || scale < 0.0) {
          continue;
        }

        for (int i = 0; i < getBulkSystem().getPhases()[0].getNumberOfComponents(); i++) {
          double currentMoles = getBulkSystem().getPhase(phaseNum).getComponent(i)
              .getNumberOfMolesInPhase();
          if (!Double.isFinite(currentMoles) || currentMoles <= 0.0) {
            continue;
          }
          double delta = currentMoles * (scale - 1.0);
          if (Double.isFinite(delta) && Math.abs(delta) > 0.0) {
            getBulkSystem().getPhase(phaseNum).addMoles(i, delta);
          }
        }
      }
    }
    getBulkSystem().initBeta();
    getBulkSystem().init_x_y();
    getBulkSystem().init(1);
  }

  /**
   * <p>
   * calcContactLength.
   * </p>
   *
   * @return a double
   */
  public double calcContactLength() {
    return 0;
  }

  /** {@inheritDoc} */
  @Override
  public void init() {
    super.init();
    massFlowRate[0] =
        velocity[0] * getBulkSystem().getPhases()[0].getPhysicalProperties().getDensity()
            * pipe.getArea() * phaseFraction[0];
    massFlowRate[1] =
        velocity[1] * getBulkSystem().getPhases()[1].getPhysicalProperties().getDensity()
            * pipe.getArea() * phaseFraction[1];
    molarFlowRate[0] = massFlowRate[0] / getBulkSystem().getPhases()[0].getMolarMass();
    molarFlowRate[1] = massFlowRate[1] / getBulkSystem().getPhases()[1].getMolarMass();
    superficialVelocity[0] = velocity[0] * phaseFraction[0];
    superficialVelocity[1] = velocity[1] * phaseFraction[1];
    volumetricFlowRate[0] = superficialVelocity[0] * pipe.getArea();
    volumetricFlowRate[1] = superficialVelocity[1] * pipe.getArea();
    this.updateMolarFlow();

    this.calcHydraulicDiameter();
    this.calcReynoldNumber();
    interphaseContactArea = this.calcGasLiquidContactArea();
    this.calcInterfacialAreaPerVolume();

    wallFrictionFactor[0] = interphaseTransportCoefficient.calcWallFrictionFactor(0, this);
    wallFrictionFactor[1] = interphaseTransportCoefficient.calcWallFrictionFactor(1, this);

    interphaseFrictionFactor[0] =
        interphaseTransportCoefficient.calcInterPhaseFrictionFactor(0, this);
    interphaseFrictionFactor[1] =
        interphaseTransportCoefficient.calcInterPhaseFrictionFactor(0, this);
  }

  /**
   * <p>
   * calcGasLiquidContactArea.
   * </p>
   *
   * @return a double
   */
  public double calcGasLiquidContactArea() {
    interphaseContactArea = pipe.getNodeLength() * interphaseContactLength[0];
    return interphaseContactArea;
  }

  /**
   * <p>
   * Calculates the interfacial area per unit volume (a) based on the selected model.
   * </p>
   *
   * <p>
   * The interfacial area per unit volume is critical for mass transfer calculations: N_i = k_L * a
   * * ΔC_i
   * </p>
   *
   * @return the interfacial area per unit volume in 1/m
   */
  public double calcInterfacialAreaPerVolume() {
    switch (interfacialAreaModel) {
      case GEOMETRIC:
        interfacialAreaPerVolume = calcGeometricInterfacialAreaPerVolume();
        break;
      case EMPIRICAL_CORRELATION:
        interfacialAreaPerVolume = calcEmpiricalInterfacialAreaPerVolume();
        break;
      case USER_DEFINED:
        interfacialAreaPerVolume = userDefinedInterfacialAreaPerVolume;
        break;
      default:
        interfacialAreaPerVolume = calcGeometricInterfacialAreaPerVolume();
    }
    return interfacialAreaPerVolume;
  }

  /**
   * <p>
   * Calculates the interfacial area per unit volume using geometric model.
   * </p>
   *
   * <p>
   * For stratified flow: a = S_i / A (interface chord length / cross-sectional area)
   * </p>
   *
   * @return the geometric interfacial area per unit volume in 1/m
   */
  protected double calcGeometricInterfacialAreaPerVolume() {
    // Default geometric calculation based on interface chord length
    if (pipe.getArea() > 0 && interphaseContactLength[0] > 0) {
      return interphaseContactLength[0] / pipe.getArea();
    }
    return 0.0;
  }

  /**
   * <p>
   * Calculates the interfacial area per unit volume using empirical correlations.
   * </p>
   *
   * <p>
   * This method should be overridden by specific flow pattern classes.
   * </p>
   *
   * @return the empirical interfacial area per unit volume in 1/m
   */
  protected double calcEmpiricalInterfacialAreaPerVolume() {
    // Default: fall back to geometric calculation
    return calcGeometricInterfacialAreaPerVolume();
  }

  /**
   * <p>
   * Gets the current interfacial area per unit volume.
   * </p>
   *
   * @return the interfacial area per unit volume in 1/m
   */
  public double getInterfacialAreaPerVolume() {
    return interfacialAreaPerVolume;
  }

  /**
   * <p>
   * Sets the interfacial area model.
   * </p>
   *
   * @param model the interfacial area model to use
   */
  public void setInterfacialAreaModel(InterfacialAreaModel model) {
    this.interfacialAreaModel = model;
  }

  /**
   * <p>
   * Gets the current interfacial area model.
   * </p>
   *
   * @return the current interfacial area model
   */
  public InterfacialAreaModel getInterfacialAreaModel() {
    return interfacialAreaModel;
  }

  /**
   * <p>
   * Sets the user-defined interfacial area per unit volume for USER_DEFINED model.
   * </p>
   *
   * @param areaPerVolume the interfacial area per unit volume in 1/m
   */
  public void setUserDefinedInterfacialAreaPerVolume(double areaPerVolume) {
    this.userDefinedInterfacialAreaPerVolume = areaPerVolume;
  }

  // ==================== PHASE TRANSITION HANDLING ====================

  /**
   * Minimum phase fraction threshold to avoid numerical singularities. When a phase fraction drops
   * below this threshold, special handling is required for near single-phase conditions.
   */
  public static final double MIN_PHASE_FRACTION = 1.0e-10;

  /**
   * Initial nucleation phase fraction. When condensation/boiling is initiated, this is the initial
   * fraction of the emerging phase.
   */
  public static final double NUCLEATION_PHASE_FRACTION = 1.0e-6;

  /**
   * <p>
   * Checks if the system is effectively single-phase gas (no liquid present).
   * </p>
   *
   * @return true if gas fraction is greater than (1 - MIN_PHASE_FRACTION)
   */
  public boolean isEffectivelySinglePhaseGas() {
    return phaseFraction[0] > (1.0 - MIN_PHASE_FRACTION) || phaseFraction[1] < MIN_PHASE_FRACTION;
  }

  /**
   * <p>
   * Checks if the system is effectively single-phase liquid (no gas present).
   * </p>
   *
   * @return true if liquid fraction is greater than (1 - MIN_PHASE_FRACTION)
   */
  public boolean isEffectivelySinglePhaseLiquid() {
    return phaseFraction[1] > (1.0 - MIN_PHASE_FRACTION) || phaseFraction[0] < MIN_PHASE_FRACTION;
  }

  /**
   * <p>
   * Checks if the conditions are favorable for condensation (droplet formation from gas phase).
   * This occurs when the gas is supersaturated - temperature is below dew point.
   * </p>
   *
   * @return true if condensation initiation is expected
   */
  public boolean isCondensationLikely() {
    if (!isEffectivelySinglePhaseGas()) {
      return false;
    }
    // Check if temperature is below dew point (supersaturated gas)
    try {
      // Clone system to avoid modifying the original
      neqsim.thermo.system.SystemInterface tempSystem = getBulkSystem().clone();
      neqsim.thermodynamicoperations.ThermodynamicOperations ops =
          new neqsim.thermodynamicoperations.ThermodynamicOperations(tempSystem);
      ops.dewPointTemperatureFlash();
      double dewPointTemp = tempSystem.getTemperature();
      return getBulkSystem().getTemperature() < dewPointTemp;
    } catch (Exception e) {
      logger.debug("Dew point calculation failed: " + e.getMessage());
      return false;
    }
  }

  /**
   * <p>
   * Checks if the conditions are favorable for bubble nucleation (gas formation from liquid phase).
   * This occurs when the liquid is superheated - temperature is above bubble point.
   * </p>
   *
   * @return true if bubble nucleation is expected
   */
  public boolean isBubbleNucleationLikely() {
    if (!isEffectivelySinglePhaseLiquid()) {
      return false;
    }
    // Check if temperature is above bubble point (superheated liquid)
    try {
      // Clone system to avoid modifying the original
      neqsim.thermo.system.SystemInterface tempSystem = getBulkSystem().clone();
      neqsim.thermodynamicoperations.ThermodynamicOperations ops =
          new neqsim.thermodynamicoperations.ThermodynamicOperations(tempSystem);
      ops.bubblePointTemperatureFlash();
      double bubblePointTemp = tempSystem.getTemperature();
      return getBulkSystem().getTemperature() > bubblePointTemp;
    } catch (Exception e) {
      logger.debug("Bubble point calculation failed: " + e.getMessage());
      return false;
    }
  }

  /**
   * <p>
   * Initiates condensation by setting up the liquid phase with nucleation phase fraction. This is
   * used when transitioning from single-phase gas to two-phase flow.
   * </p>
   *
   * <p>
   * The initial droplet size is calculated based on homogeneous nucleation theory or set to a
   * default value.
   * </p>
   */
  public void initiateCondensation() {
    // Set minimum liquid fraction to enable two-phase calculations
    phaseFraction[1] = NUCLEATION_PHASE_FRACTION;
    phaseFraction[0] = 1.0 - NUCLEATION_PHASE_FRACTION;

    // Perform flash to equilibrate phases
    try {
      neqsim.thermodynamicoperations.ThermodynamicOperations ops =
          new neqsim.thermodynamicoperations.ThermodynamicOperations(getBulkSystem());
      ops.TPflash();
      getBulkSystem().init(3);

      // Update phase fractions from flash result
      double flashedGasFraction = getBulkSystem().getVolumeFraction(0);
      if (flashedGasFraction < 1.0 - MIN_PHASE_FRACTION) {
        phaseFraction[0] = flashedGasFraction;
        phaseFraction[1] = 1.0 - flashedGasFraction;
      }
    } catch (Exception e) {
      logger.warn("Flash calculation failed during condensation initiation: " + e.getMessage());
    }

    logger.info("Condensation initiated - liquid fraction: " + phaseFraction[1]);
  }

  /**
   * <p>
   * Initiates bubble nucleation by setting up the gas phase with nucleation phase fraction. This is
   * used when transitioning from single-phase liquid to two-phase flow.
   * </p>
   */
  public void initiateBubbleNucleation() {
    // Set minimum gas fraction to enable two-phase calculations
    phaseFraction[0] = NUCLEATION_PHASE_FRACTION;
    phaseFraction[1] = 1.0 - NUCLEATION_PHASE_FRACTION;

    // Perform flash to equilibrate phases
    try {
      neqsim.thermodynamicoperations.ThermodynamicOperations ops =
          new neqsim.thermodynamicoperations.ThermodynamicOperations(getBulkSystem());
      ops.TPflash();
      getBulkSystem().init(3);

      // Update phase fractions from flash result
      double flashedGasFraction = getBulkSystem().getVolumeFraction(0);
      if (flashedGasFraction > MIN_PHASE_FRACTION) {
        phaseFraction[0] = flashedGasFraction;
        phaseFraction[1] = 1.0 - flashedGasFraction;
      }
    } catch (Exception e) {
      logger.warn("Flash calculation failed during bubble nucleation: " + e.getMessage());
    }

    logger.info("Bubble nucleation initiated - gas fraction: " + phaseFraction[0]);
  }

  /**
   * <p>
   * Enforces minimum phase fraction limits to avoid numerical singularities in two-phase
   * calculations. This is called during initialization to ensure stable calculations.
   * </p>
   */
  public void enforceMinimumPhaseFractions() {
    // Enforce minimum gas phase fraction
    if (phaseFraction[0] < MIN_PHASE_FRACTION) {
      phaseFraction[0] = MIN_PHASE_FRACTION;
      phaseFraction[1] = 1.0 - MIN_PHASE_FRACTION;
    }
    // Enforce minimum liquid phase fraction
    if (phaseFraction[1] < MIN_PHASE_FRACTION) {
      phaseFraction[1] = MIN_PHASE_FRACTION;
      phaseFraction[0] = 1.0 - MIN_PHASE_FRACTION;
    }
  }

  /**
   * <p>
   * Checks phase equilibrium and initiates phase transitions if necessary. This method should be
   * called at each node to handle condensation or bubble nucleation.
   * </p>
   *
   * @return true if a phase transition was initiated
   */
  public boolean checkAndInitiatePhaseTransition() {
    if (isCondensationLikely()) {
      initiateCondensation();
      return true;
    }
    if (isBubbleNucleationLikely()) {
      initiateBubbleNucleation();
      return true;
    }
    return false;
  }

  /**
   * <p>
   * Gets the initial droplet/bubble diameter for nucleation based on critical nucleation theory.
   * </p>
   *
   * @param isDroplet true for condensation (droplets), false for bubbles
   * @return the initial nucleation diameter in meters
   */
  public double getNucleationDiameter(boolean isDroplet) {
    // Critical nucleation diameter from classical nucleation theory:
    // d_crit = 4 * σ / Δp where σ is surface tension and Δp is supersaturation pressure
    double surfaceTension = getBulkSystem().getInterphaseProperties().getSurfaceTension(0, 1);

    // Estimate supersaturation from pressure difference
    double saturationPressure;
    try {
      // Clone system to avoid modifying the original
      neqsim.thermo.system.SystemInterface tempSystem = getBulkSystem().clone();
      neqsim.thermodynamicoperations.ThermodynamicOperations ops =
          new neqsim.thermodynamicoperations.ThermodynamicOperations(tempSystem);
      if (isDroplet) {
        ops.dewPointPressureFlash();
      } else {
        ops.bubblePointPressureFlash();
      }
      saturationPressure = tempSystem.getPressure();
    } catch (Exception e) {
      // Default to a reasonable nucleation diameter (1 micron)
      return 1.0e-6;
    }

    double pressureDiff = Math.abs(getBulkSystem().getPressure() - saturationPressure) * 1e5; // Pa
    if (pressureDiff < 1.0) {
      pressureDiff = 1.0; // Prevent division by zero
    }

    double criticalDiameter = 4.0 * surfaceTension / pressureDiff;

    // Ensure reasonable bounds (1 nm to 1 mm)
    return Math.max(1.0e-9, Math.min(1.0e-3, criticalDiameter));
  }

  /** {@inheritDoc} */
  @Override
  public void calcFluxes() {
    if (bulkSystem.isChemicalSystem()) {
      // getBulkSystem().getChemicalReactionOperations().setSystem(getBulkSystem());
      // getOperations().chemicalEquilibrium();
    }
    fluidBoundary.solve();
    fluidBoundary.calcFluxes();
  }

  /** {@inheritDoc} */
  @Override
  public void update() {
    // System.out.println("reac heat " +
    // getBulkSystem().getChemicalReactionOperations().getDeltaReactionHeat());
    double heatFluxGas = getFluidBoundary().getInterphaseHeatFlux(0);
    // getInterphaseTransportCoefficient().calcInterphaseHeatTransferCoefficient(0,
    // getPrandtlNumber(0),
    // this) *
    // (getInterphaseSystem().getPhase(0).getTemperature()
    // -
    // getBulkSystem().getPhase(0).getTemperature())
    // *
    // getInterphaseContactArea();

    double heatFluxLiquid = getFluidBoundary().getInterphaseHeatFlux(1);
    // getInterphaseTransportCoefficient().calcInterphaseHeatTransferCoefficient(1,
    // getPrandtlNumber(1),
    // this) *
    // (getInterphaseSystem().getPhase(1).getTemperature()
    // -
    // getBulkSystem().getPhase(1).getTemperature())
    // *
    // getInterphaseContactArea();
    // System.out.println("heat flux local " + heatFluxLiquid);
    // double liquid_dT =
    // -this.flowDirection[1]*heatFlux/1000.0/getBulkSystem().getPhase(1).getCp();

    double liquid_dT = this.flowDirection[1] * heatFluxLiquid * getGeometry().getNodeLength()
        / getVelocity(1) / getBulkSystem().getPhase(1).getCp();
    double gas_dT = this.flowDirection[0] * heatFluxGas * getGeometry().getNodeLength()
        / getVelocity(0) / getBulkSystem().getPhase(0).getCp();
    liquid_dT -= 0 * getInterphaseTransportCoefficient().calcWallHeatTransferCoefficient(1, this)
        * (getBulkSystem().getPhase(1).getTemperature() - pipe.getInnerWallTemperature())
        * getWallContactLength(1) * getGeometry().getNodeLength()
        / getBulkSystem().getPhase(1).getCp();
    gas_dT -= 0 * getInterphaseTransportCoefficient().calcWallHeatTransferCoefficient(0, this)
        * (getBulkSystem().getPhase(0).getTemperature() - pipe.getInnerWallTemperature())
        * getWallContactLength(0) * getGeometry().getNodeLength()
        / getBulkSystem().getPhase(0).getCp();
    // liquid_dT +=
    // getInterphaseTransportCoefficient().calcWallHeatTransferCoefficient(0, this)*
    // (getBulkSystem().getPhase(0).getTemperature()-pipe.getOuterTemperature())*
    // getWallContactLength(0) *
    // getGeometry().getNodeLength()/getBulkSystem().getPhase(0).getCp();
    // liquid_dT +=
    // 10*this.flowDirection[1]*getBulkSystem().getChemicalReactionOperations().getDeltaReactionHeat()/getBulkSystem().getPhase(1).getCp();
    // System.out.println("Cp " + getBulkSystem().getPhase(1).getCp());
    // System.out.println("liq dT " + liquid_dT);
    // System.out.println("gas dT " + gas_dT);
    double fluxwallinternal =
        getInterphaseTransportCoefficient().calcWallHeatTransferCoefficient(1, this)
            * (getBulkSystem().getPhase(1).getTemperature() - pipe.getInnerWallTemperature())
            * getWallContactLength(1) * getGeometry().getNodeLength()
            + getInterphaseTransportCoefficient().calcWallHeatTransferCoefficient(0, this)
                * (getBulkSystem().getPhase(0).getTemperature() - pipe.getInnerWallTemperature())
                * getWallContactLength(0) * getGeometry().getNodeLength();

    double JolprK = 3.14 * 0.2032 * 0.0094 * getGeometry().getNodeLength() * 7500 * 500;
    double fluxOut = -50.0 * 3.14 * (0.2032 + 0.01) * getGeometry().getNodeLength()
        * (pipe.getInnerWallTemperature() - pipe.getSurroundingEnvironment().getTemperature());
    double dTwall = 0 * (fluxOut + fluxwallinternal) / JolprK;
    pipe.setInnerWallTemperature(pipe.getInnerWallTemperature() + dTwall);

    getBulkSystem().getPhase(1)
        .setTemperature(getBulkSystem().getPhase(1).getTemperature() + liquid_dT);
    getBulkSystem().getPhase(0)
        .setTemperature(getBulkSystem().getPhase(0).getTemperature() + gas_dT);

    // System.out.println("pipe wall temperature " + pipe.getTemperature());
    // System.out.println("liquid velocity " + getSuperficialVelocity(1));
    // System.out.println("gas velocity " + getSuperficialVelocity(0));

    for (int componentNumber = 0; componentNumber < getBulkSystem().getPhases()[0]
        .getNumberOfComponents(); componentNumber++) {
      double liquidMolarRate =
          getFluidBoundary().getInterphaseMolarFlux(componentNumber) * getInterphaseContactArea(); // getInterphaseContactLength(0)*getGeometry().getNodeLength();

      double gasMolarRate =
          -getFluidBoundary().getInterphaseMolarFlux(componentNumber) * getInterphaseContactArea(); // getInterphaseContactLength(0)*getGeometry().getNodeLength();

      // System.out.println("liquidMolarRate" + liquidMolarRate);
      getBulkSystem().getPhase(0).addMoles(componentNumber, this.flowDirection[0] * gasMolarRate);
      getBulkSystem().getPhase(1).addMoles(componentNumber,
          this.flowDirection[1] * liquidMolarRate);
    }

    getBulkSystem().initBeta();
    getBulkSystem().init_x_y();
    getBulkSystem().init(3);

    if (bulkSystem.isChemicalSystem()) {
      getBulkSystem().getChemicalReactionOperations().setSystem(getBulkSystem());
      getOperations().chemicalEquilibrium();
    }
  }

  /**
   * <p>
   * update.
   * </p>
   *
   * @param deltaTime a double
   */
  public void update(double deltaTime) {
    for (int componentNumber = 0; componentNumber < getBulkSystem().getPhases()[0]
        .getNumberOfComponents(); componentNumber++) {
      double liquidMolarRate =
          getFluidBoundary().getInterphaseMolarFlux(componentNumber) * getInterphaseContactArea(); // getInterphaseContactLength(0)*getGeometry().getNodeLength();

      double gasMolarRate =
          -getFluidBoundary().getInterphaseMolarFlux(componentNumber) * getInterphaseContactArea(); // getInterphaseContactLength(0)*getGeometry().getNodeLength();

      getBulkSystem().getPhase(0).addMoles(componentNumber,
          this.flowDirection[0] * gasMolarRate * deltaTime);
      getBulkSystem().getPhase(1).addMoles(componentNumber,
          this.flowDirection[1] * liquidMolarRate * deltaTime);
    }

    getBulkSystem().initBeta();
    getBulkSystem().init_x_y();
    getBulkSystem().initProperties();
  }
}
