package neqsim.fluidmechanics.flowsystem.twophaseflowsystem.twophasepipeflowsystem;

import java.util.UUID;
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

  /**
   * <p>
   * Constructor for TwoPhasePipeFlowSystem.
   * </p>
   */
  public TwoPhasePipeFlowSystem() {}

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
      double surroundingTemp = flowNode[i].getGeometry().getSurroundingEnvironment().getTemperature();
      // Approximate overall heat transfer coefficient
      double uValue = 10.0; // W/(m²·K) - simplified
      totalHeatLoss += uValue * wallArea * (fluidTemp - surroundingTemp);
    }
    return totalHeatLoss;
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
