package neqsim.fluidmechanics.flowsystem.twophaseflowsystem.twophasepipeflowsystem;

import java.util.UUID;
import neqsim.fluidmechanics.flownode.FlowPattern;
import neqsim.fluidmechanics.flownode.FlowPatternDetector;
import neqsim.fluidmechanics.flownode.FlowPatternModel;
import neqsim.fluidmechanics.flownode.WallHeatTransferModel;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * TwoPhasePipeFlowSystem class for non-equilibrium two-phase pipe flow simulation.
 * </p>
 *
 * <p>
 * This class implements steady-state and transient two-phase pipe flow simulation based on the
 * non-equilibrium thermodynamics approach described in Solbraa (2002). It supports multiple flow
 * patterns including stratified, annular, slug, droplet/mist, and bubble flow. The Krishna-Standart
 * film model is used for mass and heat transfer calculations at the gas-liquid interface.
 * </p>
 *
 * <p>
 * Key features:
 * </p>
 * <ul>
 * <li>Non-equilibrium mass transfer between phases using film theory</li>
 * <li>Interphase heat transfer with finite flux corrections</li>
 * <li>Momentum equations with wall and interphase friction</li>
 * <li>Transient simulation with time-stepping capabilities</li>
 * <li>Multiple flow pattern support with automatic flow regime transitions</li>
 * </ul>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class TwoPhasePipeFlowSystem
    extends neqsim.fluidmechanics.flowsystem.twophaseflowsystem.TwoPhaseFlowSystem {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /** Time step for transient simulations in seconds. */
  private double timeStep = 0.1;

  /** Total simulation time for transient simulations in seconds. */
  private double simulationTime = 10.0;

  /** Number of time steps completed. */
  private int numberOfTimeSteps = 0;

  /** Current simulation time in seconds. */
  private double currentTime = 0.0;

  // ==================== FLOW PATTERN DETECTION ====================

  /** Flow pattern prediction model. */
  private FlowPatternModel flowPatternModel = FlowPatternModel.MANUAL;

  /** Whether automatic flow pattern detection is enabled. */
  private boolean automaticFlowPatternDetection = false;

  /** Current flow patterns at each node. */
  private FlowPattern[] nodeFlowPatterns;

  // ==================== WALL HEAT TRANSFER ====================

  /** Wall heat transfer model. */
  private WallHeatTransferModel wallHeatTransferModel = WallHeatTransferModel.CONVECTIVE_BOUNDARY;

  /** Constant wall temperature in Kelvin (for CONSTANT_WALL_TEMPERATURE model). */
  private double constantWallTemperature = 288.15;

  /** Constant heat flux in W/m² (for CONSTANT_HEAT_FLUX model). */
  private double constantHeatFlux = 0.0;

  /** Overall heat transfer coefficient in W/(m²·K) (for CONVECTIVE_BOUNDARY model). */
  private double overallHeatTransferCoefficient = 10.0;

  /** Ambient temperature in Kelvin (for CONVECTIVE_BOUNDARY model). */
  private double ambientTemperature = 288.15;

  /**
   * <p>
   * Constructor for TwoPhasePipeFlowSystem.
   * </p>
   */
  public TwoPhasePipeFlowSystem() {}

  /**
   * <p>
   * Creates a new builder for configuring a TwoPhasePipeFlowSystem.
   * </p>
   *
   * <p>
   * Example usage:
   * </p>
   * 
   * <pre>
   * TwoPhasePipeFlowSystem pipe =
   *     TwoPhasePipeFlowSystem.builder().withFluid(thermoSystem).withDiameter(0.1, "m")
   *         .withLength(1000, "m").withNodes(100).withFlowPattern(FlowPattern.STRATIFIED).build();
   * </pre>
   *
   * @return a new TwoPhasePipeFlowSystemBuilder
   */
  public static TwoPhasePipeFlowSystemBuilder builder() {
    return TwoPhasePipeFlowSystemBuilder.create();
  }

  /** {@inheritDoc} */
  @Override
  public void createSystem() {
    // thermoSystem.init(1);
    flowLeg = new neqsim.fluidmechanics.flowleg.pipeleg.PipeLeg[this.getNumberOfLegs()];

    for (int i = 0; i < getNumberOfLegs(); i++) {
      flowLeg[i] = new neqsim.fluidmechanics.flowleg.pipeleg.PipeLeg();
    }

    flowNode = new neqsim.fluidmechanics.flownode.FlowNodeInterface[totalNumberOfNodes];
    if (initFlowPattern.equals("stratified")) {
      flowNode[0] =
          new neqsim.fluidmechanics.flownode.twophasenode.twophasepipeflownode.StratifiedFlowNode(
              thermoSystem, equipmentGeometry[0]);
    } else if (initFlowPattern.equals("annular")) {
      flowNode[0] =
          new neqsim.fluidmechanics.flownode.twophasenode.twophasepipeflownode.AnnularFlow(
              thermoSystem, equipmentGeometry[0]);
    } else if (initFlowPattern.equals("slug")) {
      flowNode[0] =
          new neqsim.fluidmechanics.flownode.twophasenode.twophasepipeflownode.SlugFlowNode(
              thermoSystem, equipmentGeometry[0]);
    } else if (initFlowPattern.equals("droplet") || initFlowPattern.equals("mist")) {
      flowNode[0] =
          new neqsim.fluidmechanics.flownode.twophasenode.twophasepipeflownode.DropletFlowNode(
              thermoSystem, equipmentGeometry[0]);
    } else if (initFlowPattern.equals("bubble")) {
      flowNode[0] =
          new neqsim.fluidmechanics.flownode.twophasenode.twophasepipeflownode.BubbleFlowNode(
              thermoSystem, equipmentGeometry[0]);
    } else {
      flowNode[0] =
          new neqsim.fluidmechanics.flownode.twophasenode.twophasepipeflownode.StratifiedFlowNode(
              thermoSystem, equipmentGeometry[0]);
    }
    flowNode[totalNumberOfNodes - 1] = flowNode[0].getNextNode();

    super.createSystem();
    this.setNodes();
  }

  /** {@inheritDoc} */
  @Override
  public void init() {
    for (int j = 0; j < getTotalNumberOfNodes(); j++) {
      flowNode[j].initFlowCalc();
      flowNode[j].init();
    }

    for (int j = 0; j < getTotalNumberOfNodes(); j++) {
      for (int phaseNum = 0; phaseNum < 2; phaseNum++) {
        flowNode[j].setVelocityOut(phaseNum, this.flowNode[j].getVelocity(phaseNum));
      }
    }

    for (int k = 1; k < getTotalNumberOfNodes(); k++) {
      for (int phaseNum = 0; phaseNum < 2; phaseNum++) {
        this.flowNode[k].setVelocityIn(phaseNum, this.flowNode[k - 1].getVelocityOut(phaseNum));
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public void solveSteadyState(int type, UUID id) {
    double[] times = {0.0};
    display =
        new neqsim.fluidmechanics.util.fluidmechanicsvisualization.flowsystemvisualization.twophaseflowvisualization.twophasepipeflowvisualization.TwoPhasePipeFlowVisualization(
            this.getTotalNumberOfNodes(), 1);
    getTimeSeries().setTimes(times);
    neqsim.thermo.system.SystemInterface[] systems = {flowNode[0].getBulkSystem()};
    getTimeSeries().setInletThermoSystems(systems);
    getTimeSeries().setNumberOfTimeStepsInInterval(1);
    double[] outletFlowRates = {0.0, 0.0};
    getTimeSeries().setOutletMolarFlowRate(outletFlowRates);

    flowSolver =
        new neqsim.fluidmechanics.flowsolver.twophaseflowsolver.twophasepipeflowsolver.TwoPhaseFixedStaggeredGridSolver(
            this, getSystemLength(), this.getTotalNumberOfNodes(), false);
    flowSolver.setSolverType(type);
    flowSolver.solveTDMA();
    calcIdentifier = id;

    getTimeSeries().init(this);
    display.setNextData(this);
  }

  /** {@inheritDoc} */
  @Override
  public void solveTransient(int type, UUID id) {
    // Initialize time series for storing results
    double[] times = new double[(int) (simulationTime / timeStep) + 1];
    for (int i = 0; i < times.length; i++) {
      times[i] = i * timeStep;
    }

    display =
        new neqsim.fluidmechanics.util.fluidmechanicsvisualization.flowsystemvisualization.twophaseflowvisualization.twophasepipeflowvisualization.TwoPhasePipeFlowVisualization(
            this.getTotalNumberOfNodes(), times.length);
    getTimeSeries().setTimes(times);
    neqsim.thermo.system.SystemInterface[] systems = {flowNode[0].getBulkSystem()};
    getTimeSeries().setInletThermoSystems(systems);
    getTimeSeries().setNumberOfTimeStepsInInterval(times.length);
    double[] outletFlowRates = {0.0, 0.0};
    getTimeSeries().setOutletMolarFlowRate(outletFlowRates);

    // Create solver in dynamic mode
    flowSolver =
        new neqsim.fluidmechanics.flowsolver.twophaseflowsolver.twophasepipeflowsolver.TwoPhaseFixedStaggeredGridSolver(
            this, getSystemLength(), this.getTotalNumberOfNodes(), true);
    flowSolver.setSolverType(type);
    flowSolver.setTimeStep(timeStep);

    // Store initial state
    getTimeSeries().init(this);
    display.setNextData(this);

    // Time stepping loop
    currentTime = 0.0;
    numberOfTimeSteps = 0;

    while (currentTime < simulationTime) {
      flowSolver.solveTDMA();
      currentTime += timeStep;
      numberOfTimeSteps++;

      // Store results at each time step
      getTimeSeries().init(this);
      display.setNextData(this);
    }

    calcIdentifier = id;
  }

  /**
   * <p>
   * Sets the time step for transient simulations.
   * </p>
   *
   * @param timeStep the time step in seconds
   */
  public void setTimeStep(double timeStep) {
    this.timeStep = timeStep;
  }

  /**
   * <p>
   * Gets the time step for transient simulations.
   * </p>
   *
   * @return the time step in seconds
   */
  public double getTimeStep() {
    return timeStep;
  }

  /**
   * <p>
   * Sets the total simulation time for transient simulations.
   * </p>
   *
   * @param simulationTime the total simulation time in seconds
   */
  public void setSimulationTime(double simulationTime) {
    this.simulationTime = simulationTime;
  }

  /**
   * <p>
   * Gets the total simulation time for transient simulations.
   * </p>
   *
   * @return the total simulation time in seconds
   */
  public double getSimulationTime() {
    return simulationTime;
  }

  /**
   * <p>
   * Gets the number of time steps completed.
   * </p>
   *
   * @return the number of time steps
   */
  public int getNumberOfTimeSteps() {
    return numberOfTimeSteps;
  }

  /**
   * <p>
   * Gets the current simulation time.
   * </p>
   *
   * @return the current time in seconds
   */
  public double getCurrentTime() {
    return currentTime;
  }

  /**
   * <p>
   * Enables non-equilibrium mass transfer calculation using the Krishna-Standart film model. When
   * enabled, mass transfer fluxes are calculated based on driving forces (chemical potential
   * differences) and mass transfer coefficients.
   * </p>
   */
  public void enableNonEquilibriumMassTransfer() {
    setEquilibriumMassTransfer(false);
  }

  /**
   * <p>
   * Enables non-equilibrium heat transfer calculation. When enabled, heat transfer between phases
   * is calculated using heat transfer coefficients and temperature differences.
   * </p>
   */
  public void enableNonEquilibriumHeatTransfer() {
    setEquilibriumHeatTransfer(false);
  }

  /**
   * <p>
   * Calculates the total mass transfer rate for a component along the pipe.
   * </p>
   *
   * @param componentIndex the component index
   * @return the total molar mass transfer rate in mol/s
   */
  public double getTotalMassTransferRate(int componentIndex) {
    return getTotalMolarMassTransferRate(componentIndex);
  }

  /**
   * <p>
   * Calculates the total heat transfer rate from the pipe to surroundings.
   * </p>
   *
   * @return the total heat transfer rate in W
   */
  public double getTotalHeatLoss() {
    double totalHeatLoss = 0.0;
    for (int i = 0; i < getTotalNumberOfNodes() - 1; i++) {
      double nodeLength = flowNode[i].getGeometry().getNodeLength();
      double diameter = flowNode[i].getGeometry().getDiameter();
      double wallArea = Math.PI * diameter * nodeLength;
      double fluidTemp = flowNode[i].getBulkSystem().getTemperature();
      double surroundingTemp =
          flowNode[i].getGeometry().getSurroundingEnvironment().getTemperature();
      // Approximate overall heat transfer coefficient
      double uValue = 10.0; // W/(m²·K) - simplified
      totalHeatLoss += uValue * wallArea * (fluidTemp - surroundingTemp);
    }
    return totalHeatLoss;
  }

  // ==================== PROFILE OUTPUT METHODS ====================

  /**
   * <p>
   * Gets the temperature profile along the pipe.
   * </p>
   *
   * @return an array of temperatures in Kelvin at each node
   */
  public double[] getTemperatureProfile() {
    double[] temperatures = new double[getTotalNumberOfNodes()];
    for (int i = 0; i < getTotalNumberOfNodes(); i++) {
      temperatures[i] = flowNode[i].getBulkSystem().getTemperature();
    }
    return temperatures;
  }

  /**
   * <p>
   * Gets the pressure profile along the pipe.
   * </p>
   *
   * @return an array of pressures in bar at each node
   */
  public double[] getPressureProfile() {
    double[] pressures = new double[getTotalNumberOfNodes()];
    for (int i = 0; i < getTotalNumberOfNodes(); i++) {
      pressures[i] = flowNode[i].getBulkSystem().getPressure();
    }
    return pressures;
  }

  /**
   * <p>
   * Gets the position profile along the pipe.
   * </p>
   *
   * @return an array of positions in meters from the inlet
   */
  public double[] getPositionProfile() {
    double[] positions = new double[getTotalNumberOfNodes()];
    double cumulativeLength = 0.0;
    for (int i = 0; i < getTotalNumberOfNodes(); i++) {
      positions[i] = cumulativeLength;
      cumulativeLength += flowNode[i].getGeometry().getNodeLength();
    }
    return positions;
  }

  /**
   * <p>
   * Gets the void fraction (gas volume fraction) profile along the pipe.
   * </p>
   *
   * @return an array of void fractions at each node
   */
  public double[] getVoidFractionProfile() {
    double[] voidFractions = new double[getTotalNumberOfNodes()];
    for (int i = 0; i < getTotalNumberOfNodes(); i++) {
      voidFractions[i] = flowNode[i].getPhaseFraction(0);
    }
    return voidFractions;
  }

  /**
   * <p>
   * Gets the liquid holdup profile along the pipe.
   * </p>
   *
   * @return an array of liquid holdups at each node
   */
  public double[] getLiquidHoldupProfile() {
    double[] holdups = new double[getTotalNumberOfNodes()];
    for (int i = 0; i < getTotalNumberOfNodes(); i++) {
      holdups[i] = flowNode[i].getPhaseFraction(1);
    }
    return holdups;
  }

  /**
   * <p>
   * Gets the velocity profile for a specific phase along the pipe.
   * </p>
   *
   * @param phaseIndex 0 for gas phase, 1 for liquid phase
   * @return an array of velocities in m/s at each node
   */
  public double[] getVelocityProfile(int phaseIndex) {
    double[] velocities = new double[getTotalNumberOfNodes()];
    for (int i = 0; i < getTotalNumberOfNodes(); i++) {
      velocities[i] = flowNode[i].getVelocity(phaseIndex);
    }
    return velocities;
  }

  /**
   * <p>
   * Gets the superficial velocity profile for a specific phase along the pipe.
   * </p>
   *
   * @param phaseIndex 0 for gas phase, 1 for liquid phase
   * @return an array of superficial velocities in m/s at each node
   */
  public double[] getSuperficialVelocityProfile(int phaseIndex) {
    double[] velocities = new double[getTotalNumberOfNodes()];
    for (int i = 0; i < getTotalNumberOfNodes(); i++) {
      velocities[i] = flowNode[i].getSuperficialVelocity(phaseIndex);
    }
    return velocities;
  }

  /**
   * <p>
   * Gets the gas phase composition profile for all components.
   * </p>
   *
   * @return a 2D array [componentIndex][nodeIndex] of mole fractions
   */
  public double[][] getGasCompositionProfile() {
    int numComponents = flowNode[0].getBulkSystem().getPhase(0).getNumberOfComponents();
    double[][] composition = new double[numComponents][getTotalNumberOfNodes()];
    for (int i = 0; i < getTotalNumberOfNodes(); i++) {
      for (int j = 0; j < numComponents; j++) {
        composition[j][i] = flowNode[i].getBulkSystem().getPhase(0).getComponent(j).getx();
      }
    }
    return composition;
  }

  /**
   * <p>
   * Gets the liquid phase composition profile for all components.
   * </p>
   *
   * @return a 2D array [componentIndex][nodeIndex] of mole fractions
   */
  public double[][] getLiquidCompositionProfile() {
    int numComponents = flowNode[0].getBulkSystem().getPhase(1).getNumberOfComponents();
    double[][] composition = new double[numComponents][getTotalNumberOfNodes()];
    for (int i = 0; i < getTotalNumberOfNodes(); i++) {
      for (int j = 0; j < numComponents; j++) {
        composition[j][i] = flowNode[i].getBulkSystem().getPhase(1).getComponent(j).getx();
      }
    }
    return composition;
  }

  /**
   * <p>
   * Gets the interfacial area profile along the pipe.
   * </p>
   *
   * @return an array of interfacial areas in m² at each node
   */
  public double[] getInterfacialAreaProfile() {
    double[] areas = new double[getTotalNumberOfNodes()];
    for (int i = 0; i < getTotalNumberOfNodes(); i++) {
      areas[i] = flowNode[i].getInterphaseContactArea();
    }
    return areas;
  }

  /**
   * <p>
   * Gets the density profile for a specific phase along the pipe.
   * </p>
   *
   * @param phaseIndex 0 for gas phase, 1 for liquid phase
   * @return an array of densities in kg/m³ at each node
   */
  public double[] getDensityProfile(int phaseIndex) {
    double[] densities = new double[getTotalNumberOfNodes()];
    for (int i = 0; i < getTotalNumberOfNodes(); i++) {
      densities[i] =
          flowNode[i].getBulkSystem().getPhase(phaseIndex).getPhysicalProperties().getDensity();
    }
    return densities;
  }

  /**
   * <p>
   * Gets the viscosity profile for a specific phase along the pipe.
   * </p>
   *
   * @param phaseIndex 0 for gas phase, 1 for liquid phase
   * @return an array of dynamic viscosities in Pa·s at each node
   */
  public double[] getViscosityProfile(int phaseIndex) {
    double[] viscosities = new double[getTotalNumberOfNodes()];
    for (int i = 0; i < getTotalNumberOfNodes(); i++) {
      viscosities[i] =
          flowNode[i].getBulkSystem().getPhase(phaseIndex).getPhysicalProperties().getViscosity();
    }
    return viscosities;
  }

  /**
   * <p>
   * Gets the mass transfer profile for a specific component.
   * </p>
   *
   * @param componentName the name of the component
   * @return an array of cumulative mass transfer rates in mol/s at each node
   */
  public double[] getMassTransferProfile(String componentName) {
    int componentIndex =
        flowNode[0].getBulkSystem().getPhase(0).getComponent(componentName).getComponentNumber();
    return getMassTransferProfile(componentIndex);
  }

  /**
   * <p>
   * Gets the mass transfer profile for a specific component.
   * </p>
   *
   * @param componentIndex the component index
   * @return an array of cumulative mass transfer rates in mol/s at each node
   */
  public double[] getMassTransferProfile(int componentIndex) {
    double[] massTransfer = new double[getTotalNumberOfNodes()];
    double cumulativeTransfer = 0.0;
    for (int i = 0; i < getTotalNumberOfNodes(); i++) {
      double flux = flowNode[i].getFluidBoundary().getInterphaseMolarFlux(componentIndex);
      double area = flowNode[i].getInterphaseContactArea();
      cumulativeTransfer += flux * area;
      massTransfer[i] = cumulativeTransfer;
    }
    return massTransfer;
  }

  /**
   * <p>
   * Calculates the component mass balance error.
   * </p>
   *
   * @param componentName the name of the component
   * @return the relative mass balance error (0 = perfect balance)
   */
  public double getComponentMassBalance(String componentName) {
    int compIndex =
        flowNode[0].getBulkSystem().getPhase(0).getComponent(componentName).getComponentNumber();

    // Inlet moles (use getNumberOfMolesInPhase since it represents molar flow in the flow node)
    double inletMoles = flowNode[0].getBulkSystem().getPhase(0).getComponent(compIndex).getx()
        * flowNode[0].getBulkSystem().getPhase(0).getNumberOfMolesInPhase()
        + flowNode[0].getBulkSystem().getPhase(1).getComponent(compIndex).getx()
            * flowNode[0].getBulkSystem().getPhase(1).getNumberOfMolesInPhase();

    // Outlet moles
    int lastNode = getTotalNumberOfNodes() - 1;
    double outletMoles =
        flowNode[lastNode].getBulkSystem().getPhase(0).getComponent(compIndex).getx()
            * flowNode[lastNode].getBulkSystem().getPhase(0).getNumberOfMolesInPhase()
            + flowNode[lastNode].getBulkSystem().getPhase(1).getComponent(compIndex).getx()
                * flowNode[lastNode].getBulkSystem().getPhase(1).getNumberOfMolesInPhase();

    if (Math.abs(inletMoles) > 1e-20) {
      return (inletMoles - outletMoles) / inletMoles;
    }
    return 0.0;
  }

  /**
   * <p>
   * Gets the Reynolds number profile for a specific phase.
   * </p>
   *
   * @param phaseIndex 0 for gas phase, 1 for liquid phase
   * @return an array of Reynolds numbers at each node
   */
  public double[] getReynoldsNumberProfile(int phaseIndex) {
    double[] reynolds = new double[getTotalNumberOfNodes()];
    for (int i = 0; i < getTotalNumberOfNodes(); i++) {
      reynolds[i] = flowNode[i].getReynoldsNumber(phaseIndex);
    }
    return reynolds;
  }

  /**
   * <p>
   * Gets the enthalpy profile for a specific phase along the pipe.
   * </p>
   *
   * @param phaseIndex 0 for gas phase, 1 for liquid phase
   * @return an array of specific enthalpies in J/kg at each node
   */
  public double[] getEnthalpyProfile(int phaseIndex) {
    double[] enthalpy = new double[getTotalNumberOfNodes()];
    for (int i = 0; i < getTotalNumberOfNodes(); i++) {
      double H = flowNode[i].getBulkSystem().getPhase(phaseIndex).getEnthalpy();
      double moles = flowNode[i].getBulkSystem().getPhase(phaseIndex).getNumberOfMolesInPhase();
      double molarMass = flowNode[i].getBulkSystem().getPhase(phaseIndex).getMolarMass();
      if (moles > 0 && molarMass > 0) {
        enthalpy[i] = H / (moles * molarMass); // J/kg
      } else {
        enthalpy[i] = 0.0;
      }
    }
    return enthalpy;
  }

  /**
   * <p>
   * Gets the total mixture enthalpy profile along the pipe.
   * </p>
   *
   * @return an array of total enthalpies in J at each node
   */
  public double[] getTotalEnthalpyProfile() {
    double[] enthalpy = new double[getTotalNumberOfNodes()];
    for (int i = 0; i < getTotalNumberOfNodes(); i++) {
      enthalpy[i] = flowNode[i].getBulkSystem().getEnthalpy();
    }
    return enthalpy;
  }

  /**
   * <p>
   * Gets the heat capacity (Cp) profile for a specific phase along the pipe.
   * </p>
   *
   * @param phaseIndex 0 for gas phase, 1 for liquid phase
   * @return an array of heat capacities in J/(mol·K) at each node
   */
  public double[] getHeatCapacityProfile(int phaseIndex) {
    double[] cp = new double[getTotalNumberOfNodes()];
    for (int i = 0; i < getTotalNumberOfNodes(); i++) {
      cp[i] = flowNode[i].getBulkSystem().getPhase(phaseIndex).getCp();
    }
    return cp;
  }

  /**
   * <p>
   * Gets the flow pattern at each node along the pipe.
   * </p>
   *
   * @return an array of FlowPattern enums at each node
   */
  public FlowPattern[] getFlowPatternProfile() {
    FlowPattern[] patterns = new FlowPattern[getTotalNumberOfNodes()];
    for (int i = 0; i < getTotalNumberOfNodes(); i++) {
      patterns[i] = getFlowPatternAtNode(i);
    }
    return patterns;
  }

  /**
   * <p>
   * Gets the flow pattern names at each node along the pipe.
   * </p>
   *
   * @return an array of flow pattern names at each node
   */
  public String[] getFlowPatternNameProfile() {
    String[] patterns = new String[getTotalNumberOfNodes()];
    for (int i = 0; i < getTotalNumberOfNodes(); i++) {
      patterns[i] = getFlowPatternAtNode(i).getDisplayName();
    }
    return patterns;
  }

  // ==================== FLOW PATTERN DETECTION METHODS ====================

  /**
   * <p>
   * Enables automatic flow pattern detection using the specified model.
   * </p>
   *
   * @param enabled true to enable automatic detection, false to use manual specification
   */
  public void enableAutomaticFlowPatternDetection(boolean enabled) {
    this.automaticFlowPatternDetection = enabled;
    if (enabled && flowPatternModel == FlowPatternModel.MANUAL) {
      flowPatternModel = FlowPatternModel.TAITEL_DUKLER;
    }
  }

  /**
   * <p>
   * Sets the flow pattern prediction model.
   * </p>
   *
   * @param model the flow pattern model to use
   */
  public void setFlowPatternModel(FlowPatternModel model) {
    this.flowPatternModel = model;
    if (model != FlowPatternModel.MANUAL) {
      this.automaticFlowPatternDetection = true;
    }
  }

  /**
   * <p>
   * Gets the current flow pattern model.
   * </p>
   *
   * @return the flow pattern model
   */
  public FlowPatternModel getFlowPatternModel() {
    return flowPatternModel;
  }

  /**
   * <p>
   * Detects and updates flow patterns at all nodes.
   * </p>
   */
  public void detectFlowPatterns() {
    if (nodeFlowPatterns == null) {
      nodeFlowPatterns = new FlowPattern[getTotalNumberOfNodes()];
    }

    for (int i = 0; i < getTotalNumberOfNodes(); i++) {
      nodeFlowPatterns[i] = detectFlowPatternAtNode(i);
    }
  }

  /**
   * <p>
   * Detects the flow pattern at a specific node.
   * </p>
   *
   * @param nodeIndex the node index
   * @return the detected flow pattern
   */
  public FlowPattern detectFlowPatternAtNode(int nodeIndex) {
    if (flowPatternModel == FlowPatternModel.MANUAL) {
      return FlowPattern.fromString(initFlowPattern);
    }

    // Get properties from node
    double usg = flowNode[nodeIndex].getSuperficialVelocity(0);
    double usl = flowNode[nodeIndex].getSuperficialVelocity(1);
    double rhoG =
        flowNode[nodeIndex].getBulkSystem().getPhase(0).getPhysicalProperties().getDensity();
    double rhoL =
        flowNode[nodeIndex].getBulkSystem().getPhase(1).getPhysicalProperties().getDensity();
    double muG =
        flowNode[nodeIndex].getBulkSystem().getPhase(0).getPhysicalProperties().getViscosity();
    double muL =
        flowNode[nodeIndex].getBulkSystem().getPhase(1).getPhysicalProperties().getViscosity();
    double sigma =
        flowNode[nodeIndex].getBulkSystem().getInterphaseProperties().getSurfaceTension(0, 1);
    double diameter = flowNode[nodeIndex].getGeometry().getDiameter();
    // Use horizontal pipe assumption (0 inclination) - leg heights can be used to estimate later
    double inclination = 0.0;

    return FlowPatternDetector.detectFlowPattern(flowPatternModel, usg, usl, rhoG, rhoL, muG, muL,
        sigma, diameter, inclination);
  }

  /**
   * <p>
   * Gets the flow pattern at a specific node.
   * </p>
   *
   * @param nodeIndex the node index
   * @return the flow pattern at the node
   */
  public FlowPattern getFlowPatternAtNode(int nodeIndex) {
    if (nodeFlowPatterns == null || nodeFlowPatterns[nodeIndex] == null) {
      return FlowPattern.fromString(initFlowPattern);
    }
    return nodeFlowPatterns[nodeIndex];
  }

  /**
   * <p>
   * Gets the flow pattern profile along the pipe.
   * </p>
   *
   * @return an array of flow patterns at each node
   */
  public FlowPattern[] getFlowPatternProfile() {
    if (nodeFlowPatterns == null) {
      detectFlowPatterns();
    }
    return nodeFlowPatterns.clone();
  }

  // ==================== WALL HEAT TRANSFER METHODS ====================

  /**
   * <p>
   * Sets the wall heat transfer model.
   * </p>
   *
   * @param model the wall heat transfer model
   */
  public void setWallHeatTransferModel(WallHeatTransferModel model) {
    this.wallHeatTransferModel = model;
  }

  /**
   * <p>
   * Gets the wall heat transfer model.
   * </p>
   *
   * @return the wall heat transfer model
   */
  public WallHeatTransferModel getWallHeatTransferModel() {
    return wallHeatTransferModel;
  }

  /**
   * <p>
   * Sets the constant wall temperature for CONSTANT_WALL_TEMPERATURE model.
   * </p>
   *
   * @param temperature the wall temperature in Kelvin
   */
  public void setConstantWallTemperature(double temperature) {
    this.constantWallTemperature = temperature;
  }

  /**
   * <p>
   * Sets the constant wall temperature with unit.
   * </p>
   *
   * @param temperature the wall temperature
   * @param unit the temperature unit ("K", "C", or "F")
   */
  public void setConstantWallTemperature(double temperature, String unit) {
    switch (unit.toUpperCase()) {
      case "C":
        this.constantWallTemperature = temperature + 273.15;
        break;
      case "F":
        this.constantWallTemperature = (temperature - 32) * 5.0 / 9.0 + 273.15;
        break;
      case "K":
      default:
        this.constantWallTemperature = temperature;
        break;
    }
  }

  /**
   * <p>
   * Gets the constant wall temperature.
   * </p>
   *
   * @return the wall temperature in Kelvin
   */
  public double getConstantWallTemperature() {
    return constantWallTemperature;
  }

  /**
   * <p>
   * Sets the constant heat flux for CONSTANT_HEAT_FLUX model.
   * </p>
   *
   * @param heatFlux the heat flux in W/m² (positive = heat into fluid)
   */
  public void setConstantHeatFlux(double heatFlux) {
    this.constantHeatFlux = heatFlux;
  }

  /**
   * <p>
   * Gets the constant heat flux.
   * </p>
   *
   * @return the heat flux in W/m²
   */
  public double getConstantHeatFlux() {
    return constantHeatFlux;
  }

  /**
   * <p>
   * Sets the overall heat transfer coefficient for CONVECTIVE_BOUNDARY model.
   * </p>
   *
   * @param coefficient the overall U-value in W/(m²·K)
   */
  public void setOverallHeatTransferCoefficient(double coefficient) {
    this.overallHeatTransferCoefficient = coefficient;
  }

  /**
   * <p>
   * Gets the overall heat transfer coefficient.
   * </p>
   *
   * @return the U-value in W/(m²·K)
   */
  public double getOverallHeatTransferCoefficient() {
    return overallHeatTransferCoefficient;
  }

  /**
   * <p>
   * Sets the ambient temperature for CONVECTIVE_BOUNDARY model.
   * </p>
   *
   * @param temperature the ambient temperature in Kelvin
   */
  public void setAmbientTemperature(double temperature) {
    this.ambientTemperature = temperature;
  }

  /**
   * <p>
   * Sets the ambient temperature with unit.
   * </p>
   *
   * @param temperature the ambient temperature
   * @param unit the temperature unit ("K", "C", or "F")
   */
  public void setAmbientTemperature(double temperature, String unit) {
    switch (unit.toUpperCase()) {
      case "C":
        this.ambientTemperature = temperature + 273.15;
        break;
      case "F":
        this.ambientTemperature = (temperature - 32) * 5.0 / 9.0 + 273.15;
        break;
      case "K":
      default:
        this.ambientTemperature = temperature;
        break;
    }
  }

  /**
   * <p>
   * Gets the ambient temperature.
   * </p>
   *
   * @return the ambient temperature in Kelvin
   */
  public double getAmbientTemperature() {
    return ambientTemperature;
  }

  /**
   * <p>
   * Calculates the wall heat flux at a specific node based on the heat transfer model.
   * </p>
   *
   * @param nodeIndex the node index
   * @return the heat flux in W/m² (positive = heat into fluid)
   */
  public double calculateWallHeatFlux(int nodeIndex) {
    switch (wallHeatTransferModel) {
      case ADIABATIC:
        return 0.0;

      case CONSTANT_HEAT_FLUX:
        return constantHeatFlux;

      case CONSTANT_WALL_TEMPERATURE:
        // Use internal heat transfer coefficient
        double hInternal = estimateInternalHeatTransferCoefficient(nodeIndex);
        double tFluid = flowNode[nodeIndex].getBulkSystem().getTemperature();
        return hInternal * (constantWallTemperature - tFluid);

      case CONVECTIVE_BOUNDARY:
      default:
        double tFluid2 = flowNode[nodeIndex].getBulkSystem().getTemperature();
        return overallHeatTransferCoefficient * (ambientTemperature - tFluid2);
    }
  }

  /**
   * <p>
   * Estimates the internal heat transfer coefficient at a node using Nusselt correlations.
   * </p>
   *
   * @param nodeIndex the node index
   * @return the heat transfer coefficient in W/(m²·K)
   */
  private double estimateInternalHeatTransferCoefficient(int nodeIndex) {
    // Use Dittus-Boelter correlation for turbulent flow
    // Nu = 0.023 * Re^0.8 * Pr^0.4
    double reL = flowNode[nodeIndex].getReynoldsNumber(1);
    double reG = flowNode[nodeIndex].getReynoldsNumber(0);
    double re = Math.max(reL, reG);

    double cpL = flowNode[nodeIndex].getBulkSystem().getPhase(1).getCp();
    double muL =
        flowNode[nodeIndex].getBulkSystem().getPhase(1).getPhysicalProperties().getViscosity();
    double kL =
        flowNode[nodeIndex].getBulkSystem().getPhase(1).getPhysicalProperties().getConductivity();

    double pr = cpL * muL / kL;
    pr = Math.max(0.5, Math.min(pr, 2000)); // Limit Prandtl number

    double nu;
    if (re > 10000) {
      // Turbulent - Dittus-Boelter
      nu = 0.023 * Math.pow(re, 0.8) * Math.pow(pr, 0.4);
    } else if (re > 2300) {
      // Transition - linear interpolation
      double nuLam = 3.66;
      double nuTurb = 0.023 * Math.pow(10000, 0.8) * Math.pow(pr, 0.4);
      double f = (re - 2300) / (10000 - 2300);
      nu = nuLam + f * (nuTurb - nuLam);
    } else {
      // Laminar - constant Nusselt for pipe
      nu = 3.66;
    }

    double diameter = flowNode[nodeIndex].getGeometry().getDiameter();
    return nu * kL / diameter;
  }

  // ==================== INTERFACIAL AREA AND MASS TRANSFER ====================

  /**
   * <p>
   * Calculates the specific interfacial area (per unit volume) at a specific node using
   * flow-pattern-specific correlations.
   * </p>
   *
   * <p>
   * This differs from {@link #getInterfacialAreaProfile()} which returns the absolute interfacial
   * area in m². This method returns the interfacial area per unit volume (a = A/V) which is useful
   * for mass transfer calculations.
   * </p>
   *
   * @param nodeNumber the node index
   * @return specific interfacial area (1/m)
   */
  public double getSpecificInterfacialAreaAtNode(int nodeNumber) {
    if (flowNode == null || flowNode[nodeNumber] == null) {
      return 0.0;
    }

    FlowPattern pattern = getFlowPatternAtNode(nodeNumber);
    double diameter = flowNode[nodeNumber].getGeometry().getDiameter();
    double holdup = flowNode[nodeNumber].getPhaseFraction(1); // Liquid holdup

    // Get fluid properties
    double rhoG = flowNode[nodeNumber].getBulkSystem().getPhase(0).getDensity();
    double rhoL = flowNode[nodeNumber].getBulkSystem().getPhase(1).getDensity();
    double sigma = flowNode[nodeNumber].getInterphaseContactArea();

    // Get velocities
    double usg = flowNode[nodeNumber].getSuperficialVelocity(0);
    double usl = flowNode[nodeNumber].getSuperficialVelocity(1);

    // Use default surface tension if not available
    if (sigma <= 0) {
      sigma = 0.03; // Default 30 mN/m for hydrocarbon systems
    }

    return neqsim.fluidmechanics.flownode.InterfacialAreaCalculator
        .calculateInterfacialArea(pattern, diameter, holdup, rhoG, rhoL, usg, usl, sigma);
  }

  /**
   * <p>
   * Gets the specific interfacial area (per unit volume) profile along the pipe.
   * </p>
   *
   * <p>
   * This differs from {@link #getInterfacialAreaProfile()} which returns absolute areas in m². This
   * method returns specific interfacial areas (1/m) useful for mass transfer calculations.
   * </p>
   *
   * @return an array of specific interfacial areas (1/m) at each node
   */
  public double[] getSpecificInterfacialAreaProfile() {
    double[] areas = new double[getTotalNumberOfNodes()];
    for (int i = 0; i < getTotalNumberOfNodes(); i++) {
      areas[i] = getSpecificInterfacialAreaAtNode(i);
    }
    return areas;
  }

  /**
   * <p>
   * Calculates the liquid-side mass transfer coefficient at a specific node.
   * </p>
   *
   * @param nodeNumber the node index
   * @param diffusivity liquid diffusivity in m²/s
   * @return liquid-side mass transfer coefficient k_L (m/s)
   */
  public double getLiquidMassTransferCoefficientAtNode(int nodeNumber, double diffusivity) {
    if (flowNode == null || flowNode[nodeNumber] == null) {
      return 0.0;
    }

    FlowPattern pattern = getFlowPatternAtNode(nodeNumber);
    double diameter = flowNode[nodeNumber].getGeometry().getDiameter();
    double holdup = flowNode[nodeNumber].getPhaseFraction(1);

    double usg = flowNode[nodeNumber].getSuperficialVelocity(0);
    double usl = flowNode[nodeNumber].getSuperficialVelocity(1);
    double rhoL =
        flowNode[nodeNumber].getBulkSystem().getPhase(1).getPhysicalProperties().getDensity();
    double muL =
        flowNode[nodeNumber].getBulkSystem().getPhase(1).getPhysicalProperties().getViscosity();

    // Protect against invalid values
    if (Double.isNaN(rhoL) || rhoL <= 0 || Double.isNaN(muL) || muL <= 0) {
      return 0.0;
    }

    return neqsim.fluidmechanics.flownode.MassTransferCoefficientCalculator
        .calculateLiquidMassTransferCoefficient(pattern, diameter, holdup, usg, usl, rhoL, muL,
            diffusivity);
  }

  /**
   * <p>
   * Calculates the gas-side mass transfer coefficient at a specific node.
   * </p>
   *
   * @param nodeNumber the node index
   * @param diffusivity gas diffusivity in m²/s
   * @return gas-side mass transfer coefficient k_G (m/s)
   */
  public double getGasMassTransferCoefficientAtNode(int nodeNumber, double diffusivity) {
    if (flowNode == null || flowNode[nodeNumber] == null) {
      return 0.0;
    }

    FlowPattern pattern = getFlowPatternAtNode(nodeNumber);
    double diameter = flowNode[nodeNumber].getGeometry().getDiameter();
    double holdup = flowNode[nodeNumber].getPhaseFraction(1);

    double usg = flowNode[nodeNumber].getSuperficialVelocity(0);
    double rhoG =
        flowNode[nodeNumber].getBulkSystem().getPhase(0).getPhysicalProperties().getDensity();
    double muG =
        flowNode[nodeNumber].getBulkSystem().getPhase(0).getPhysicalProperties().getViscosity();

    // Protect against invalid values
    if (Double.isNaN(rhoG) || rhoG <= 0 || Double.isNaN(muG) || muG <= 0) {
      return 0.0;
    }

    return neqsim.fluidmechanics.flownode.MassTransferCoefficientCalculator
        .calculateGasMassTransferCoefficient(pattern, diameter, holdup, usg, rhoG, muG,
            diffusivity);
  }

  /**
   * <p>
   * Gets the liquid-side mass transfer coefficient profile along the pipe.
   * </p>
   *
   * @param diffusivity liquid diffusivity in m²/s
   * @return an array of k_L values (m/s) at each node
   */
  public double[] getLiquidMassTransferCoefficientProfile(double diffusivity) {
    double[] kL = new double[getTotalNumberOfNodes()];
    for (int i = 0; i < getTotalNumberOfNodes(); i++) {
      kL[i] = getLiquidMassTransferCoefficientAtNode(i, diffusivity);
    }
    return kL;
  }

  /**
   * <p>
   * Gets the gas-side mass transfer coefficient profile along the pipe.
   * </p>
   *
   * @param diffusivity gas diffusivity in m²/s
   * @return an array of k_G values (m/s) at each node
   */
  public double[] getGasMassTransferCoefficientProfile(double diffusivity) {
    double[] kG = new double[getTotalNumberOfNodes()];
    for (int i = 0; i < getTotalNumberOfNodes(); i++) {
      kG[i] = getGasMassTransferCoefficientAtNode(i, diffusivity);
    }
    return kG;
  }

  /**
   * <p>
   * Calculates the overall volumetric mass transfer coefficient k_L·a at a node.
   * </p>
   *
   * <p>
   * This is the product of the liquid-side mass transfer coefficient and the interfacial area per
   * unit volume, commonly used in mass transfer calculations:
   * </p>
   *
   * <pre>
   * ṁ = k_L·a · V · ΔC
   * </pre>
   *
   * @param nodeNumber the node index
   * @param diffusivity liquid diffusivity in m²/s
   * @return volumetric mass transfer coefficient k_L·a (1/s)
   */
  public double getVolumetricMassTransferCoefficientAtNode(int nodeNumber, double diffusivity) {
    double kL = getLiquidMassTransferCoefficientAtNode(nodeNumber, diffusivity);
    double a = getSpecificInterfacialAreaAtNode(nodeNumber);
    return kL * a;
  }

  /**
   * <p>
   * Gets the volumetric mass transfer coefficient (k_L·a) profile along the pipe.
   * </p>
   *
   * @param diffusivity liquid diffusivity in m²/s
   * @return an array of k_L·a values (1/s) at each node
   */
  public double[] getVolumetricMassTransferCoefficientProfile(double diffusivity) {
    double[] kLa = new double[getTotalNumberOfNodes()];
    for (int i = 0; i < getTotalNumberOfNodes(); i++) {
      kLa[i] = getVolumetricMassTransferCoefficientAtNode(i, diffusivity);
    }
    return kLa;
  }

  /**
   * <p>
   * Gets the wall heat flux profile along the pipe.
   * </p>
   *
   * @return an array of heat fluxes in W/m² at each node
   */
  public double[] getWallHeatFluxProfile() {
    double[] heatFlux = new double[getTotalNumberOfNodes()];
    for (int i = 0; i < getTotalNumberOfNodes(); i++) {
      heatFlux[i] = calculateWallHeatFlux(i);
    }
    return heatFlux;
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
    // Initierer et nyt rorsystem
    neqsim.fluidmechanics.flowsystem.FlowSystemInterface pipe = new TwoPhasePipeFlowSystem();

    // Definerer termodyanmikken5 - initierer et system som benytter SRK tilstandsligning
    neqsim.thermo.system.SystemInterface testSystem =
        new neqsim.thermo.system.SystemSrkEos(295.3, 5.0);

    // med trykk 305.3 K og 125 bar - // gjor termodynamiske Flash rutiner tilgjengelige
    neqsim.thermodynamicoperations.ThermodynamicOperations testOps =
        new neqsim.thermodynamicoperations.ThermodynamicOperations(testSystem);
    testSystem.addComponent("methane", 0.11152181, 0);
    // testSystem.addComponent("ethane", 0.0011152181, 0);
    testSystem.addComponent("water", 0.04962204876, 1);
    testSystem.createDatabase(true);
    testSystem.setMixingRule(2);
    // benytter klassiske blandingsregler

    pipe.setInletThermoSystem(testSystem); // setter termodyanmikken for rorsystemet
    pipe.setNumberOfLegs(5); // deler inn roret i et gitt antall legger
    pipe.setNumberOfNodesInLeg(10); // setter antall nodepunkter (beregningspunkter/grid) pr.
                                    // leg
    double[] height = {0, 0, 0, 0, 0, 0};
    double[] length = {0.0, 1.7, 3.5, 5.0, 7.5, 10.4};
    double[] outerTemperature = {278.0, 278.0, 278.0, 278.0, 278.0, 278.0}; // , 278.0, 275.0,
                                                                            // 275.0, 275.0,
                                                                            // 275.0};
    double[] roughness = {1.0e-5, 1.0e-5, 1.0e-5, 1.0e-5, 1.0e-5, 1.0e-5};
    double[] outHeatCoef = {5.0, 5.0, 5.0, 5.0, 5.0, 5.0};
    double[] wallHeacCoef = {15.0, 15.0, 15.0, 15.0, 15.0, 15.0};

    pipe.setLegHeights(height); // setter inn hoyde for hver leg-ende
    pipe.setLegPositions(length); // setter avstand til hver leg-ende
    pipe.setLegOuterTemperatures(outerTemperature);
    pipe.setLegWallHeatTransferCoefficients(wallHeacCoef);
    pipe.setLegOuterHeatTransferCoefficients(outHeatCoef);

    // Definerer geometrien for roret
    neqsim.fluidmechanics.geometrydefinitions.GeometryDefinitionInterface[] pipeGemometry =
        new neqsim.fluidmechanics.geometrydefinitions.pipe.PipeData[6];
    double[] pipeDiameter = {0.02588, 0.02588, 0.02588, 0.02588, 0.02588, 0.02588};
    for (int i = 0; i < pipeDiameter.length; i++) {
      pipeGemometry[i] =
          new neqsim.fluidmechanics.geometrydefinitions.pipe.PipeData(pipeDiameter[i]);
    }
    pipe.setEquipmentGeometry(pipeGemometry); // setter inn rorgeometrien for hver leg
    // utforer beregninger
    pipe.createSystem();
    pipe.init();

    pipe.solveSteadyState(2);
    pipe.getNode(30).display();
    // pipe.calcFluxes();
    // pipe.getDisplay().displayResult("temperature");
    // pipe.displayResults();
    // testOps.TPflash();
    // testOps.displayResult();
  }
}
