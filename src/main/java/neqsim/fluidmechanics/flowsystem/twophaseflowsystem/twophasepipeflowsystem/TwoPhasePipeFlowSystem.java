package neqsim.fluidmechanics.flowsystem.twophaseflowsystem.twophasepipeflowsystem;

import java.util.UUID;
import neqsim.fluidmechanics.flownode.FlowPattern;
import neqsim.fluidmechanics.flownode.FlowPatternDetector;
import neqsim.fluidmechanics.flownode.FlowPatternModel;
import neqsim.fluidmechanics.flownode.HeatTransferCoefficientCalculator;
import neqsim.fluidmechanics.flownode.WallHeatTransferModel;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * Non-equilibrium two-phase pipe flow simulation system.
 *
 * <p>
 * This class implements steady-state and transient two-phase pipe flow simulation based on the
 * non-equilibrium thermodynamics approach described in Solbraa (2002). It supports multiple flow
 * patterns including stratified, annular, slug, droplet/mist, and bubble flow. The Krishna-Standart
 * film model is used for mass and heat transfer calculations at the gas-liquid interface.
 * </p>
 *
 * <h2>Key Features</h2>
 * <ul>
 * <li>Non-equilibrium mass transfer between phases using film theory</li>
 * <li>Interphase heat transfer with finite flux corrections</li>
 * <li>Momentum equations with wall and interphase friction for pressure drop</li>
 * <li>Transient simulation with time-stepping capabilities</li>
 * <li>Multiple flow pattern support with automatic flow regime transitions</li>
 * <li>Wall heat transfer models (constant temperature, constant flux, convective)</li>
 * </ul>
 *
 * <h2>Comparison with Other Pipe Models</h2>
 * <table border="1">
 * <caption>NeqSim Pipe Flow Model Comparison</caption>
 * <tr>
 * <th>Feature</th>
 * <th>TwoPhasePipeFlowSystem</th>
 * <th>TwoFluidPipe</th>
 * <th>PipeBeggsAndBrills</th>
 * </tr>
 * <tr>
 * <td>Approach</td>
 * <td>Non-equilibrium thermodynamics</td>
 * <td>Two-fluid mechanistic</td>
 * <td>Empirical correlation</td>
 * </tr>
 * <tr>
 * <td>Mass Transfer</td>
 * <td>Krishna-Standart film model</td>
 * <td>Optional flash</td>
 * <td>Equilibrium only</td>
 * </tr>
 * <tr>
 * <td>Heat Transfer</td>
 * <td>Interphase with finite flux</td>
 * <td>Joule-Thomson + wall</td>
 * <td>Not included</td>
 * </tr>
 * <tr>
 * <td>Flow Patterns</td>
 * <td>Stratified, annular, slug, droplet, bubble</td>
 * <td>Auto-detected via Taitel-Dukler</td>
 * <td>Segregated, intermittent, distributed</td>
 * </tr>
 * <tr>
 * <td>Transient</td>
 * <td>Yes (TDMA solver)</td>
 * <td>Yes (time integrator)</td>
 * <td>No (steady-state)</td>
 * </tr>
 * </table>
 *
 * <h2>Usage Example (Traditional API)</h2>
 * 
 * <pre>{@code
 * // Create two-phase fluid
 * SystemInterface fluid = new SystemSrkEos(295.3, 5.0);
 * fluid.addComponent("methane", 0.1, 0); // Gas phase
 * fluid.addComponent("water", 0.05, 1); // Liquid phase
 * fluid.createDatabase(true);
 * fluid.setMixingRule(2);
 *
 * // Configure pipe geometry
 * FlowSystemInterface pipe = new TwoPhasePipeFlowSystem();
 * pipe.setInletThermoSystem(fluid);
 * pipe.setInitialFlowPattern("stratified");
 * pipe.setNumberOfLegs(3);
 * pipe.setNumberOfNodesInLeg(10);
 *
 * // Set leg geometry
 * double[] height = {0, 0, 0, 0};
 * double[] length = {0.0, 100.0, 200.0, 300.0};
 * double[] outerTemp = {288.0, 288.0, 288.0, 288.0};
 * pipe.setLegHeights(height);
 * pipe.setLegPositions(length);
 * pipe.setLegOuterTemperatures(outerTemp);
 *
 * // Set pipe diameter
 * GeometryDefinitionInterface[] pipeGeometry = new PipeData[4];
 * for (int i = 0; i < 4; i++) {
 *   pipeGeometry[i] = new PipeData(0.1); // 100 mm diameter
 * }
 * pipe.setEquipmentGeometry(pipeGeometry);
 *
 * // Solve
 * pipe.createSystem();
 * pipe.init();
 * pipe.solveSteadyState(2);
 *
 * // Get results
 * double[] pressures = ((TwoPhasePipeFlowSystem) pipe).getPressureProfile();
 * double[] temperatures = ((TwoPhasePipeFlowSystem) pipe).getTemperatureProfile();
 * double pressureDrop = pressures[0] - pressures[pressures.length - 1];
 * }</pre>
 *
 * <h2>Usage Example (Builder API)</h2>
 * 
 * <pre>{@code
 * TwoPhasePipeFlowSystem pipe =
 *     TwoPhasePipeFlowSystem.builder().withFluid(thermoSystem).withDiameter(0.1, "m")
 *         .withLength(1000, "m").withNodes(100).withFlowPattern(FlowPattern.STRATIFIED).build();
 *
 * pipe.solveSteadyState(UUID.randomUUID());
 * double[] pressures = pipe.getPressureProfile();
 * }</pre>
 *
 * <h2>Solver Types</h2>
 * <p>
 * The solver type controls which conservation equations are solved:
 * </p>
 * <ul>
 * <li><b>SIMPLE</b> - Only mass and heat transfer via initProfiles(). Fast but no pressure drop
 * calculation. Use for quick phase equilibrium estimates.</li>
 * <li><b>DEFAULT</b> - Momentum (pressure drop), phase fraction, and energy equations. Includes
 * interphase mass and heat transfer. Good balance of completeness and performance.</li>
 * <li><b>FULL</b> - All equations including composition changes. Complete solution but slower. Use
 * when composition gradients along the pipe are important.</li>
 * </ul>
 * <p>
 * Set the solver type before calling solveSteadyState():
 * </p>
 * 
 * <pre>{@code
 * pipeSystem.setSolverType(TwoPhaseFixedStaggeredGridSolver.SolverType.DEFAULT);
 * pipeSystem.solveSteadyState(UUID.randomUUID());
 * }</pre>
 *
 * <h2>References</h2>
 * <ul>
 * <li>Solbraa, E. (2002) - Measurement and Modelling of Absorption of Carbon Dioxide into
 * Methyldiethanolamine Solutions at High Pressures. PhD Thesis, NTNU.</li>
 * <li>Krishna, R. and Standart, G.L. (1976) - A multicomponent film model incorporating a general
 * matrix method of solution to the Maxwell-Stefan equations.</li>
 * <li>Taitel, Y. and Dukler, A.E. (1976) - A model for predicting flow regime transitions in
 * horizontal and near horizontal gas-liquid flow.</li>
 * </ul>
 *
 * @author asmund
 * @version $Id: $Id
 * @see neqsim.process.equipment.pipeline.TwoFluidPipe
 * @see neqsim.process.equipment.pipeline.PipeBeggsAndBrills
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

  // ==================== PIPE GEOMETRY AND INCLINATION ====================

  /** Pipe inclination angle in radians (positive = upward flow). */
  private double inclination = 0.0;

  // ==================== MASS TRANSFER MODE ====================

  /** Mass transfer mode for non-equilibrium calculations. */
  private neqsim.fluidmechanics.flowsolver.twophaseflowsolver.twophasepipeflowsolver.TwoPhaseFixedStaggeredGridSolver.MassTransferMode massTransferMode =
      neqsim.fluidmechanics.flowsolver.twophaseflowsolver.twophasepipeflowsolver.TwoPhaseFixedStaggeredGridSolver.MassTransferMode.BIDIRECTIONAL;

  // ==================== SOLVER TYPE ====================

  /**
   * Solver type controlling which equations are solved. Default includes momentum (pressure drop),
   * phase fraction, and energy equations.
   */
  private neqsim.fluidmechanics.flowsolver.twophaseflowsolver.twophasepipeflowsolver.TwoPhaseFixedStaggeredGridSolver.SolverType solverTypeEnum =
      neqsim.fluidmechanics.flowsolver.twophaseflowsolver.twophasepipeflowsolver.TwoPhaseFixedStaggeredGridSolver.SolverType.DEFAULT;

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

  // ==================== STATIC FACTORY METHODS ====================

  /**
   * Creates a horizontal pipe with default settings.
   *
   * <p>
   * This is a convenience factory method for the most common use case: a horizontal pipe with
   * stratified flow pattern. The pipe is created, initialized, and ready for solving.
   * </p>
   *
   * <p>
   * Example usage:
   * </p>
   * 
   * <pre>
   * TwoPhasePipeFlowSystem pipe = TwoPhasePipeFlowSystem.horizontalPipe(fluid, 0.1, 1000, 100);
   * pipe.enableNonEquilibriumMassTransfer();
   * PipeFlowResult result = pipe.solve();
   * </pre>
   *
   * @param fluid the thermodynamic system (must have 2 phases)
   * @param diameterMeters pipe inner diameter in meters
   * @param lengthMeters pipe length in meters
   * @param nodes number of calculation nodes
   * @return a configured and initialized TwoPhasePipeFlowSystem
   * @throws IllegalArgumentException if fluid is null or parameters are invalid
   */
  public static TwoPhasePipeFlowSystem horizontalPipe(neqsim.thermo.system.SystemInterface fluid,
      double diameterMeters, double lengthMeters, int nodes) {
    validateFluid(fluid);
    validateGeometry(diameterMeters, lengthMeters, nodes);

    return builder().withFluid(fluid).withDiameter(diameterMeters, "m")
        .withLength(lengthMeters, "m").withNodes(nodes).horizontal()
        .withFlowPattern(FlowPattern.STRATIFIED).build();
  }

  /**
   * Creates a vertical pipe with specified flow direction.
   *
   * <p>
   * For vertical pipes, the flow pattern defaults to BUBBLE for upward flow and ANNULAR for
   * downward flow.
   * </p>
   *
   * @param fluid the thermodynamic system (must have 2 phases)
   * @param diameterMeters pipe inner diameter in meters
   * @param lengthMeters pipe length in meters
   * @param nodes number of calculation nodes
   * @param upwardFlow true for upward flow, false for downward flow
   * @return a configured and initialized TwoPhasePipeFlowSystem
   * @throws IllegalArgumentException if fluid is null or parameters are invalid
   */
  public static TwoPhasePipeFlowSystem verticalPipe(neqsim.thermo.system.SystemInterface fluid,
      double diameterMeters, double lengthMeters, int nodes, boolean upwardFlow) {
    validateFluid(fluid);
    validateGeometry(diameterMeters, lengthMeters, nodes);

    FlowPattern defaultPattern = upwardFlow ? FlowPattern.BUBBLE : FlowPattern.ANNULAR;

    return builder().withFluid(fluid).withDiameter(diameterMeters, "m")
        .withLength(lengthMeters, "m").withNodes(nodes).vertical(upwardFlow)
        .withFlowPattern(defaultPattern).build();
  }

  /**
   * Creates an inclined pipe with specified angle.
   *
   * @param fluid the thermodynamic system (must have 2 phases)
   * @param diameterMeters pipe inner diameter in meters
   * @param lengthMeters pipe length in meters
   * @param nodes number of calculation nodes
   * @param inclinationDegrees inclination angle in degrees (positive = upward)
   * @return a configured and initialized TwoPhasePipeFlowSystem
   * @throws IllegalArgumentException if fluid is null or parameters are invalid
   */
  public static TwoPhasePipeFlowSystem inclinedPipe(neqsim.thermo.system.SystemInterface fluid,
      double diameterMeters, double lengthMeters, int nodes, double inclinationDegrees) {
    validateFluid(fluid);
    validateGeometry(diameterMeters, lengthMeters, nodes);

    return builder().withFluid(fluid).withDiameter(diameterMeters, "m")
        .withLength(lengthMeters, "m").withNodes(nodes)
        .withInclination(inclinationDegrees, "degrees").withFlowPattern(FlowPattern.STRATIFIED)
        .build();
  }

  /**
   * Creates a subsea pipeline with typical seawater cooling conditions.
   *
   * <p>
   * This factory method sets up a horizontal pipe with convective boundary conditions typical of
   * subsea pipelines: seawater temperature and high external heat transfer coefficient.
   * </p>
   *
   * @param fluid the thermodynamic system (must have 2 phases)
   * @param diameterMeters pipe inner diameter in meters
   * @param lengthMeters pipe length in meters
   * @param nodes number of calculation nodes
   * @param seawaterTempCelsius seawater temperature in Celsius
   * @return a configured and initialized TwoPhasePipeFlowSystem
   * @throws IllegalArgumentException if fluid is null or parameters are invalid
   */
  public static TwoPhasePipeFlowSystem subseaPipe(neqsim.thermo.system.SystemInterface fluid,
      double diameterMeters, double lengthMeters, int nodes, double seawaterTempCelsius) {
    validateFluid(fluid);
    validateGeometry(diameterMeters, lengthMeters, nodes);

    // Typical subsea pipeline: high external heat transfer due to seawater convection
    double seawaterHeatTransferCoeff = 500.0; // W/(m²·K) - typical for flowing seawater

    return builder().withFluid(fluid).withDiameter(diameterMeters, "m")
        .withLength(lengthMeters, "m").withNodes(nodes).horizontal()
        .withFlowPattern(FlowPattern.STRATIFIED)
        .withConvectiveBoundary(seawaterTempCelsius, "C", seawaterHeatTransferCoeff).build();
  }

  /**
   * Creates a buried onshore pipeline with soil thermal conditions.
   *
   * <p>
   * This factory method sets up a horizontal pipe with convective boundary conditions typical of
   * buried onshore pipelines: ground temperature and low external heat transfer coefficient.
   * </p>
   *
   * @param fluid the thermodynamic system (must have 2 phases)
   * @param diameterMeters pipe inner diameter in meters
   * @param lengthMeters pipe length in meters
   * @param nodes number of calculation nodes
   * @param groundTempCelsius ground/soil temperature in Celsius
   * @return a configured and initialized TwoPhasePipeFlowSystem
   * @throws IllegalArgumentException if fluid is null or parameters are invalid
   */
  public static TwoPhasePipeFlowSystem buriedPipe(neqsim.thermo.system.SystemInterface fluid,
      double diameterMeters, double lengthMeters, int nodes, double groundTempCelsius) {
    validateFluid(fluid);
    validateGeometry(diameterMeters, lengthMeters, nodes);

    // Typical buried pipeline: low external heat transfer due to soil conduction
    double soilHeatTransferCoeff = 5.0; // W/(m²·K) - typical for buried pipe in soil

    return builder().withFluid(fluid).withDiameter(diameterMeters, "m")
        .withLength(lengthMeters, "m").withNodes(nodes).horizontal()
        .withFlowPattern(FlowPattern.STRATIFIED)
        .withConvectiveBoundary(groundTempCelsius, "C", soilHeatTransferCoeff).build();
  }

  /**
   * Validates the fluid system before creating a pipe.
   */
  private static void validateFluid(neqsim.thermo.system.SystemInterface fluid) {
    if (fluid == null) {
      throw new IllegalArgumentException("Fluid system cannot be null");
    }
  }

  /**
   * Validates geometry parameters.
   */
  private static void validateGeometry(double diameter, double length, int nodes) {
    if (diameter <= 0) {
      throw new IllegalArgumentException("Diameter must be positive, got: " + diameter);
    }
    if (length <= 0) {
      throw new IllegalArgumentException("Length must be positive, got: " + length);
    }
    if (nodes < 2) {
      throw new IllegalArgumentException("Number of nodes must be at least 2, got: " + nodes);
    }
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

    neqsim.fluidmechanics.flowsolver.twophaseflowsolver.twophasepipeflowsolver.TwoPhaseFixedStaggeredGridSolver solver =
        new neqsim.fluidmechanics.flowsolver.twophaseflowsolver.twophasepipeflowsolver.TwoPhaseFixedStaggeredGridSolver(
            this, getSystemLength(), this.getTotalNumberOfNodes(), false);
    solver.setMassTransferMode(massTransferMode);
    // Use the stored enum solver type (ignoring the legacy int type parameter)
    solver.setSolverType(solverTypeEnum);
    flowSolver = solver;
    flowSolver.solveTDMA();
    calcIdentifier = id;

    getTimeSeries().init(this);
    display.setNextData(this);
  }

  /**
   * <p>
   * Solve steady state using the configured solver type enum.
   * </p>
   *
   * @param id a {@link java.util.UUID} object
   */
  public void solveSteadyState(UUID id) {
    solveSteadyState(solverTypeEnum.getLegacyType(), id);
  }

  // ==================== SIMPLIFIED SOLVE API ====================

  /**
   * Solves the pipe flow system and returns a structured result object.
   *
   * <p>
   * This is the recommended method for new code. It solves the system using the configured solver
   * type and returns all results in a convenient {@link PipeFlowResult} container.
   * </p>
   *
   * <p>
   * Example usage:
   * </p>
   * 
   * <pre>
   * TwoPhasePipeFlowSystem pipe = TwoPhasePipeFlowSystem.horizontalPipe(fluid, 0.1, 1000, 100);
   * pipe.enableNonEquilibriumMassTransfer();
   * PipeFlowResult result = pipe.solve();
   *
   * System.out.println("Pressure drop: " + result.getTotalPressureDrop() + " bar");
   * System.out.println(result); // prints summary
   * </pre>
   *
   * @return a {@link PipeFlowResult} containing all simulation results
   */
  public PipeFlowResult solve() {
    solveSteadyState(UUID.randomUUID());
    return PipeFlowResult.fromPipeSystem(this);
  }

  /**
   * Solves the pipe flow system with non-equilibrium mass transfer enabled.
   *
   * <p>
   * This is a convenience method that enables non-equilibrium mass transfer, solves the system, and
   * returns the results. Equivalent to calling:
   * </p>
   * 
   * <pre>
   * pipe.enableNonEquilibriumMassTransfer();
   * return pipe.solve();
   * </pre>
   *
   * @return a {@link PipeFlowResult} containing all simulation results
   */
  public PipeFlowResult solveWithMassTransfer() {
    enableNonEquilibriumMassTransfer();
    return solve();
  }

  /**
   * Solves the pipe flow system with non-equilibrium heat and mass transfer enabled.
   *
   * <p>
   * This is a convenience method that enables both non-equilibrium mass and heat transfer, solves
   * the system, and returns the results.
   * </p>
   *
   * @return a {@link PipeFlowResult} containing all simulation results
   */
  public PipeFlowResult solveWithHeatAndMassTransfer() {
    enableNonEquilibriumMassTransfer();
    enableNonEquilibriumHeatTransfer();
    return solve();
  }

  /**
   * Solves the pipe flow system using legacy integer type parameter.
   *
   * @param type the solver type (deprecated - use {@link #setSolverType} instead)
   * @deprecated Use {@link #solve()} or {@link #solveSteadyState(UUID)} instead. Set the solver
   *             type using {@link #setSolverType} before calling. This method ignores the type
   *             parameter and uses the configured solver type enum.
   */
  @Deprecated
  public void solveSteadyState(int type) {
    solveSteadyState(type, UUID.randomUUID());
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
    neqsim.fluidmechanics.flowsolver.twophaseflowsolver.twophasepipeflowsolver.TwoPhaseFixedStaggeredGridSolver solver =
        new neqsim.fluidmechanics.flowsolver.twophaseflowsolver.twophasepipeflowsolver.TwoPhaseFixedStaggeredGridSolver(
            this, getSystemLength(), this.getTotalNumberOfNodes(), true);
    solver.setMassTransferMode(massTransferMode);
    // Use the stored enum solver type (ignoring the legacy int type parameter)
    solver.setSolverType(solverTypeEnum);
    solver.setTimeStep(timeStep);
    flowSolver = solver;

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
   * Solve transient using the configured solver type enum.
   * </p>
   *
   * @param id a {@link java.util.UUID} object
   */
  public void solveTransient(UUID id) {
    solveTransient(solverTypeEnum.getLegacyType(), id);
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
      patterns[i] = getFlowPatternAtNode(i).getName();
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
   * Sets the mass transfer mode for non-equilibrium calculations.
   * </p>
   *
   * @param mode the mass transfer mode to use
   */
  public void setMassTransferMode(
      neqsim.fluidmechanics.flowsolver.twophaseflowsolver.twophasepipeflowsolver.TwoPhaseFixedStaggeredGridSolver.MassTransferMode mode) {
    this.massTransferMode = mode;
  }

  /**
   * <p>
   * Gets the current mass transfer mode.
   * </p>
   *
   * @return the current mass transfer mode
   */
  public neqsim.fluidmechanics.flowsolver.twophaseflowsolver.twophasepipeflowsolver.TwoPhaseFixedStaggeredGridSolver.MassTransferMode getMassTransferMode() {
    return this.massTransferMode;
  }

  /**
   * <p>
   * Sets the solver type using the SolverType enum.
   * </p>
   *
   * <p>
   * Available solver types:
   * </p>
   * <ul>
   * <li>SIMPLE - Only mass and heat transfer via initProfiles(). Fast but no pressure drop.</li>
   * <li>DEFAULT - Momentum, phase fraction, and energy equations. Good balance of completeness and
   * performance.</li>
   * <li>FULL - All equations including composition. Complete solution but slower.</li>
   * </ul>
   *
   * @param type the solver type to use
   */
  public void setSolverType(
      neqsim.fluidmechanics.flowsolver.twophaseflowsolver.twophasepipeflowsolver.TwoPhaseFixedStaggeredGridSolver.SolverType type) {
    this.solverTypeEnum = type;
  }

  /**
   * <p>
   * Gets the current solver type enum.
   * </p>
   *
   * @return the current solver type
   */
  public neqsim.fluidmechanics.flowsolver.twophaseflowsolver.twophasepipeflowsolver.TwoPhaseFixedStaggeredGridSolver.SolverType getSolverType() {
    return this.solverTypeEnum;
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

  // ==================== INTERPHASE HEAT TRANSFER ====================

  /**
   * <p>
   * Calculates the liquid-side interphase heat transfer coefficient at a specific node.
   * </p>
   *
   * @param nodeNumber the node index
   * @return liquid-side heat transfer coefficient h_L (W/(m²·K))
   */
  public double getLiquidHeatTransferCoefficientAtNode(int nodeNumber) {
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
    double cpL = flowNode[nodeNumber].getBulkSystem().getPhase(1).getCp()
        / flowNode[nodeNumber].getBulkSystem().getPhase(1).getNumberOfMolesInPhase()
        / flowNode[nodeNumber].getBulkSystem().getPhase(1).getMolarMass();
    double kL =
        flowNode[nodeNumber].getBulkSystem().getPhase(1).getPhysicalProperties().getConductivity();

    // Protect against invalid values
    if (Double.isNaN(rhoL) || rhoL <= 0 || Double.isNaN(muL) || muL <= 0) {
      return 0.0;
    }

    return HeatTransferCoefficientCalculator.calculateLiquidHeatTransferCoefficient(pattern,
        diameter, holdup, usg, usl, rhoL, muL, cpL, kL);
  }

  /**
   * <p>
   * Calculates the gas-side interphase heat transfer coefficient at a specific node.
   * </p>
   *
   * @param nodeNumber the node index
   * @return gas-side heat transfer coefficient h_G (W/(m²·K))
   */
  public double getGasHeatTransferCoefficientAtNode(int nodeNumber) {
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
    double cpG = flowNode[nodeNumber].getBulkSystem().getPhase(0).getCp()
        / flowNode[nodeNumber].getBulkSystem().getPhase(0).getNumberOfMolesInPhase()
        / flowNode[nodeNumber].getBulkSystem().getPhase(0).getMolarMass();
    double kG =
        flowNode[nodeNumber].getBulkSystem().getPhase(0).getPhysicalProperties().getConductivity();

    // Protect against invalid values
    if (Double.isNaN(rhoG) || rhoG <= 0 || Double.isNaN(muG) || muG <= 0) {
      return 0.0;
    }

    return HeatTransferCoefficientCalculator.calculateGasHeatTransferCoefficient(pattern, diameter,
        holdup, usg, rhoG, muG, cpG, kG);
  }

  /**
   * <p>
   * Calculates the overall interphase heat transfer coefficient at a specific node.
   * </p>
   *
   * <p>
   * Uses the resistance in series model: 1/U = 1/h_L + 1/h_G
   * </p>
   *
   * @param nodeNumber the node index
   * @return overall interphase heat transfer coefficient (W/(m²·K))
   */
  public double getOverallInterphaseHeatTransferCoefficientAtNode(int nodeNumber) {
    double hL = getLiquidHeatTransferCoefficientAtNode(nodeNumber);
    double hG = getGasHeatTransferCoefficientAtNode(nodeNumber);
    return HeatTransferCoefficientCalculator.calculateOverallInterphaseCoefficient(hL, hG);
  }

  /**
   * <p>
   * Gets the liquid-side heat transfer coefficient profile along the pipe.
   * </p>
   *
   * @return an array of h_L values (W/(m²·K)) at each node
   */
  public double[] getLiquidHeatTransferCoefficientProfile() {
    double[] hL = new double[getTotalNumberOfNodes()];
    for (int i = 0; i < getTotalNumberOfNodes(); i++) {
      hL[i] = getLiquidHeatTransferCoefficientAtNode(i);
    }
    return hL;
  }

  /**
   * <p>
   * Gets the gas-side heat transfer coefficient profile along the pipe.
   * </p>
   *
   * @return an array of h_G values (W/(m²·K)) at each node
   */
  public double[] getGasHeatTransferCoefficientProfile() {
    double[] hG = new double[getTotalNumberOfNodes()];
    for (int i = 0; i < getTotalNumberOfNodes(); i++) {
      hG[i] = getGasHeatTransferCoefficientAtNode(i);
    }
    return hG;
  }

  /**
   * <p>
   * Gets the overall interphase heat transfer coefficient profile.
   * </p>
   *
   * @return an array of overall U values (W/(m²·K)) at each node
   */
  public double[] getOverallInterphaseHeatTransferCoefficientProfile() {
    double[] u = new double[getTotalNumberOfNodes()];
    for (int i = 0; i < getTotalNumberOfNodes(); i++) {
      u[i] = getOverallInterphaseHeatTransferCoefficientAtNode(i);
    }
    return u;
  }

  /**
   * <p>
   * Calculates the interphase heat flux at a specific node.
   * </p>
   *
   * <p>
   * q = U_interphase * (T_gas - T_liquid) in W/m²
   * </p>
   *
   * @param nodeNumber the node index
   * @return the interphase heat flux in W/m² (positive = heat from gas to liquid)
   */
  public double getInterphaseHeatFluxAtNode(int nodeNumber) {
    if (flowNode == null || flowNode[nodeNumber] == null) {
      return 0.0;
    }

    double uInterphase = getOverallInterphaseHeatTransferCoefficientAtNode(nodeNumber);
    double tGas = flowNode[nodeNumber].getBulkSystem().getPhase(0).getTemperature();
    double tLiquid = flowNode[nodeNumber].getBulkSystem().getPhase(1).getTemperature();

    return uInterphase * (tGas - tLiquid);
  }

  /**
   * <p>
   * Gets the interphase heat flux profile along the pipe.
   * </p>
   *
   * @return an array of interphase heat fluxes in W/m² at each node
   */
  public double[] getInterphaseHeatFluxProfile() {
    double[] flux = new double[getTotalNumberOfNodes()];
    for (int i = 0; i < getTotalNumberOfNodes(); i++) {
      flux[i] = getInterphaseHeatFluxAtNode(i);
    }
    return flux;
  }

  /**
   * <p>
   * Calculates the volumetric interphase heat transfer coefficient (U·a) at a node.
   * </p>
   *
   * <p>
   * This is the product of the interphase heat transfer coefficient and the interfacial area per
   * unit volume: U·a in W/(m³·K)
   * </p>
   *
   * @param nodeNumber the node index
   * @return volumetric heat transfer coefficient U·a (W/(m³·K))
   */
  public double getVolumetricHeatTransferCoefficientAtNode(int nodeNumber) {
    double u = getOverallInterphaseHeatTransferCoefficientAtNode(nodeNumber);
    double a = getSpecificInterfacialAreaAtNode(nodeNumber);
    return u * a;
  }

  /**
   * <p>
   * Gets the volumetric heat transfer coefficient (U·a) profile along the pipe.
   * </p>
   *
   * @return an array of U·a values (W/(m³·K)) at each node
   */
  public double[] getVolumetricHeatTransferCoefficientProfile() {
    double[] ua = new double[getTotalNumberOfNodes()];
    for (int i = 0; i < getTotalNumberOfNodes(); i++) {
      ua[i] = getVolumetricHeatTransferCoefficientAtNode(i);
    }
    return ua;
  }

  // ==================== DIMENSIONLESS NUMBERS ====================

  /**
   * <p>
   * Gets the Prandtl number profile for a specific phase.
   * </p>
   *
   * <p>
   * Pr = μ·Cp / k = ν / α (momentum diffusivity / thermal diffusivity)
   * </p>
   *
   * @param phaseIndex 0 for gas phase, 1 for liquid phase
   * @return an array of Prandtl numbers at each node
   */
  public double[] getPrandtlNumberProfile(int phaseIndex) {
    double[] pr = new double[getTotalNumberOfNodes()];
    for (int i = 0; i < getTotalNumberOfNodes(); i++) {
      double mu =
          flowNode[i].getBulkSystem().getPhase(phaseIndex).getPhysicalProperties().getViscosity();
      double cp = flowNode[i].getBulkSystem().getPhase(phaseIndex).getCp()
          / flowNode[i].getBulkSystem().getPhase(phaseIndex).getNumberOfMolesInPhase()
          / flowNode[i].getBulkSystem().getPhase(phaseIndex).getMolarMass();
      double k = flowNode[i].getBulkSystem().getPhase(phaseIndex).getPhysicalProperties()
          .getConductivity();
      if (k > 0) {
        pr[i] = mu * cp / k;
      }
    }
    return pr;
  }

  /**
   * <p>
   * Gets the Nusselt number profile for a specific phase.
   * </p>
   *
   * <p>
   * Nu = h·L / k (convective / conductive heat transfer)
   * </p>
   *
   * @param phaseIndex 0 for gas phase, 1 for liquid phase
   * @return an array of Nusselt numbers at each node
   */
  public double[] getNusseltNumberProfile(int phaseIndex) {
    double[] nu = new double[getTotalNumberOfNodes()];
    for (int i = 0; i < getTotalNumberOfNodes(); i++) {
      double h = (phaseIndex == 0) ? getGasHeatTransferCoefficientAtNode(i)
          : getLiquidHeatTransferCoefficientAtNode(i);
      double diameter = flowNode[i].getGeometry().getDiameter();
      double k = flowNode[i].getBulkSystem().getPhase(phaseIndex).getPhysicalProperties()
          .getConductivity();
      if (k > 0) {
        nu[i] = h * diameter / k;
      }
    }
    return nu;
  }

  /**
   * <p>
   * Gets the Schmidt number profile for a specific phase.
   * </p>
   *
   * <p>
   * Sc = μ / (ρ·D) = ν / D (momentum diffusivity / mass diffusivity)
   * </p>
   *
   * @param phaseIndex 0 for gas phase, 1 for liquid phase
   * @param diffusivity mass diffusivity in m²/s
   * @return an array of Schmidt numbers at each node
   */
  public double[] getSchmidtNumberProfile(int phaseIndex, double diffusivity) {
    double[] sc = new double[getTotalNumberOfNodes()];
    for (int i = 0; i < getTotalNumberOfNodes(); i++) {
      double mu =
          flowNode[i].getBulkSystem().getPhase(phaseIndex).getPhysicalProperties().getViscosity();
      double rho =
          flowNode[i].getBulkSystem().getPhase(phaseIndex).getPhysicalProperties().getDensity();
      if (diffusivity > 0 && rho > 0) {
        sc[i] = mu / (rho * diffusivity);
      }
    }
    return sc;
  }

  /**
   * <p>
   * Gets the Sherwood number profile for a specific phase.
   * </p>
   *
   * <p>
   * Sh = k·L / D (convective / diffusive mass transfer)
   * </p>
   *
   * @param phaseIndex 0 for gas phase, 1 for liquid phase
   * @param diffusivity mass diffusivity in m²/s
   * @return an array of Sherwood numbers at each node
   */
  public double[] getSherwoodNumberProfile(int phaseIndex, double diffusivity) {
    double[] sh = new double[getTotalNumberOfNodes()];
    for (int i = 0; i < getTotalNumberOfNodes(); i++) {
      double k = (phaseIndex == 0) ? getGasMassTransferCoefficientAtNode(i, diffusivity)
          : getLiquidMassTransferCoefficientAtNode(i, diffusivity);
      double diameter = flowNode[i].getGeometry().getDiameter();
      if (diffusivity > 0) {
        sh[i] = k * diameter / diffusivity;
      }
    }
    return sh;
  }

  /**
   * <p>
   * Gets the Stanton number (heat) profile for a specific phase.
   * </p>
   *
   * <p>
   * St = Nu / (Re·Pr) = h / (ρ·u·Cp)
   * </p>
   *
   * @param phaseIndex 0 for gas phase, 1 for liquid phase
   * @return an array of Stanton numbers at each node
   */
  public double[] getStantonNumberHeatProfile(int phaseIndex) {
    double[] st = new double[getTotalNumberOfNodes()];
    for (int i = 0; i < getTotalNumberOfNodes(); i++) {
      double h = (phaseIndex == 0) ? getGasHeatTransferCoefficientAtNode(i)
          : getLiquidHeatTransferCoefficientAtNode(i);
      double rho =
          flowNode[i].getBulkSystem().getPhase(phaseIndex).getPhysicalProperties().getDensity();
      double u = flowNode[i].getVelocity(phaseIndex);
      double cp = flowNode[i].getBulkSystem().getPhase(phaseIndex).getCp()
          / flowNode[i].getBulkSystem().getPhase(phaseIndex).getNumberOfMolesInPhase()
          / flowNode[i].getBulkSystem().getPhase(phaseIndex).getMolarMass();

      st[i] = HeatTransferCoefficientCalculator.calculateStantonNumber(h, rho, u, cp);
    }
    return st;
  }

  /**
   * <p>
   * Gets the Lewis number profile for a specific phase.
   * </p>
   *
   * <p>
   * Le = Sc / Pr = α / D (thermal diffusivity / mass diffusivity)
   * </p>
   *
   * @param phaseIndex 0 for gas phase, 1 for liquid phase
   * @param diffusivity mass diffusivity in m²/s
   * @return an array of Lewis numbers at each node
   */
  public double[] getLewisNumberProfile(int phaseIndex, double diffusivity) {
    double[] le = new double[getTotalNumberOfNodes()];
    double[] sc = getSchmidtNumberProfile(phaseIndex, diffusivity);
    double[] pr = getPrandtlNumberProfile(phaseIndex);
    for (int i = 0; i < getTotalNumberOfNodes(); i++) {
      if (pr[i] > 0) {
        le[i] = sc[i] / pr[i];
      }
    }
    return le;
  }

  // ==================== ENERGY BALANCE AND PHASE CHANGE ====================

  /**
   * <p>
   * Calculates the total interphase heat transfer rate along the pipe.
   * </p>
   *
   * @return total interphase heat transfer rate in W
   */
  public double getTotalInterphaseHeatTransferRate() {
    double total = 0.0;
    for (int i = 0; i < getTotalNumberOfNodes() - 1; i++) {
      double flux = getInterphaseHeatFluxAtNode(i);
      double area = flowNode[i].getInterphaseContactArea();
      total += flux * area;
    }
    return total;
  }

  /**
   * <p>
   * Calculates the overall energy balance for the pipe system.
   * </p>
   *
   * <p>
   * Returns the relative energy imbalance: (H_in - H_out - Q_wall) / H_in
   * </p>
   *
   * @return relative energy imbalance (0 = perfect balance)
   */
  public double getEnergyBalanceError() {
    // Inlet enthalpy
    double hIn = flowNode[0].getBulkSystem().getEnthalpy();

    // Outlet enthalpy
    int lastNode = getTotalNumberOfNodes() - 1;
    double hOut = flowNode[lastNode].getBulkSystem().getEnthalpy();

    // Wall heat loss
    double qWall = getTotalHeatLoss();

    if (Math.abs(hIn) > 1e-10) {
      return (hIn - hOut - qWall) / Math.abs(hIn);
    }
    return 0.0;
  }

  /**
   * <p>
   * Gets the cumulative energy loss profile along the pipe.
   * </p>
   *
   * @return an array of cumulative energy losses in J at each node
   */
  public double[] getCumulativeEnergyLossProfile() {
    double[] loss = new double[getTotalNumberOfNodes()];
    double cumulative = 0.0;
    double inletEnthalpy = flowNode[0].getBulkSystem().getEnthalpy();

    for (int i = 0; i < getTotalNumberOfNodes(); i++) {
      double currentEnthalpy = flowNode[i].getBulkSystem().getEnthalpy();
      loss[i] = inletEnthalpy - currentEnthalpy;
    }
    return loss;
  }

  /**
   * <p>
   * Estimates the condensation rate at a specific node.
   * </p>
   *
   * <p>
   * Condensation rate = Q_interphase / h_fg (kg/s per unit area)
   * </p>
   *
   * @param nodeNumber the node index
   * @return estimated condensation mass flux in kg/(m²·s), positive = condensation
   */
  public double getCondensationRateAtNode(int nodeNumber) {
    if (flowNode == null || flowNode[nodeNumber] == null) {
      return 0.0;
    }

    // Get interphase heat flux
    double qInterphase = getInterphaseHeatFluxAtNode(nodeNumber);

    // Estimate latent heat from enthalpy difference
    double hGas = flowNode[nodeNumber].getBulkSystem().getPhase(0).getEnthalpy()
        / flowNode[nodeNumber].getBulkSystem().getPhase(0).getNumberOfMolesInPhase()
        / flowNode[nodeNumber].getBulkSystem().getPhase(0).getMolarMass();
    double hLiq = flowNode[nodeNumber].getBulkSystem().getPhase(1).getEnthalpy()
        / flowNode[nodeNumber].getBulkSystem().getPhase(1).getNumberOfMolesInPhase()
        / flowNode[nodeNumber].getBulkSystem().getPhase(1).getMolarMass();

    double hfg = Math.abs(hGas - hLiq);

    if (hfg > 0) {
      return qInterphase / hfg; // kg/(m²·s)
    }
    return 0.0;
  }

  /**
   * <p>
   * Gets the condensation rate profile along the pipe.
   * </p>
   *
   * @return an array of condensation rates in kg/(m²·s) at each node
   */
  public double[] getCondensationRateProfile() {
    double[] rate = new double[getTotalNumberOfNodes()];
    for (int i = 0; i < getTotalNumberOfNodes(); i++) {
      rate[i] = getCondensationRateAtNode(i);
    }
    return rate;
  }

  /**
   * <p>
   * Calculates the total condensation rate along the entire pipe.
   * </p>
   *
   * @return total condensation rate in kg/s
   */
  public double getTotalCondensationRate() {
    double total = 0.0;
    for (int i = 0; i < getTotalNumberOfNodes() - 1; i++) {
      double rate = getCondensationRateAtNode(i);
      double area = flowNode[i].getInterphaseContactArea();
      total += rate * area;
    }
    return total;
  }

  /**
   * <p>
   * Gets the thermal conductivity profile for a specific phase.
   * </p>
   *
   * @param phaseIndex 0 for gas phase, 1 for liquid phase
   * @return an array of thermal conductivities in W/(m·K) at each node
   */
  public double[] getThermalConductivityProfile(int phaseIndex) {
    double[] k = new double[getTotalNumberOfNodes()];
    for (int i = 0; i < getTotalNumberOfNodes(); i++) {
      k[i] = flowNode[i].getBulkSystem().getPhase(phaseIndex).getPhysicalProperties()
          .getConductivity();
    }
    return k;
  }

  /**
   * <p>
   * Gets the surface tension profile along the pipe.
   * </p>
   *
   * @return an array of surface tensions in N/m at each node
   */
  public double[] getSurfaceTensionProfile() {
    double[] sigma = new double[getTotalNumberOfNodes()];
    for (int i = 0; i < getTotalNumberOfNodes(); i++) {
      sigma[i] = flowNode[i].getBulkSystem().getInterphaseProperties().getSurfaceTension(0, 1);
    }
    return sigma;
  }

  // ==================== MASS BALANCE ====================

  /**
   * <p>
   * Calculates the overall mass balance error for the pipe system.
   * </p>
   *
   * @return relative mass balance error (0 = perfect balance)
   */
  public double getMassBalanceError() {
    // Inlet mass flow
    double massIn = 0.0;
    for (int p = 0; p < 2; p++) {
      massIn += flowNode[0].getMassFlowRate(p);
    }

    // Outlet mass flow
    int lastNode = getTotalNumberOfNodes() - 1;
    double massOut = 0.0;
    for (int p = 0; p < 2; p++) {
      massOut += flowNode[lastNode].getMassFlowRate(p);
    }

    if (Math.abs(massIn) > 1e-20) {
      return (massIn - massOut) / massIn;
    }
    return 0.0;
  }

  /**
   * <p>
   * Gets the mass flow rate profile for a specific phase.
   * </p>
   *
   * @param phaseIndex 0 for gas phase, 1 for liquid phase
   * @return an array of mass flow rates in kg/s at each node
   */
  public double[] getMassFlowRateProfile(int phaseIndex) {
    double[] massFlow = new double[getTotalNumberOfNodes()];
    for (int i = 0; i < getTotalNumberOfNodes(); i++) {
      massFlow[i] = flowNode[i].getMassFlowRate(phaseIndex);
    }
    return massFlow;
  }

  /**
   * <p>
   * Gets the total (gas + liquid) mass flow rate profile.
   * </p>
   *
   * @return an array of total mass flow rates in kg/s at each node
   */
  public double[] getTotalMassFlowRateProfile() {
    double[] massFlow = new double[getTotalNumberOfNodes()];
    for (int i = 0; i < getTotalNumberOfNodes(); i++) {
      massFlow[i] = flowNode[i].getMassFlowRate(0) + flowNode[i].getMassFlowRate(1);
    }
    return massFlow;
  }

  /**
   * <p>
   * Gets the gas quality (vapor mass fraction) profile along the pipe.
   * </p>
   *
   * @return an array of gas quality values (0-1) at each node
   */
  public double[] getGasQualityProfile() {
    double[] quality = new double[getTotalNumberOfNodes()];
    for (int i = 0; i < getTotalNumberOfNodes(); i++) {
      double mG = flowNode[i].getMassFlowRate(0);
      double mL = flowNode[i].getMassFlowRate(1);
      double total = mG + mL;
      if (total > 0) {
        quality[i] = mG / total;
      }
    }
    return quality;
  }

  /**
   * <p>
   * Gets the mixture density profile along the pipe.
   * </p>
   *
   * <p>
   * ρ_mix = α·ρ_G + (1-α)·ρ_L
   * </p>
   *
   * @return an array of mixture densities in kg/m³ at each node
   */
  public double[] getMixtureDensityProfile() {
    double[] rhoMix = new double[getTotalNumberOfNodes()];
    for (int i = 0; i < getTotalNumberOfNodes(); i++) {
      double alpha = flowNode[i].getPhaseFraction(0); // Gas void fraction
      double rhoG = flowNode[i].getBulkSystem().getPhase(0).getPhysicalProperties().getDensity();
      double rhoL = flowNode[i].getBulkSystem().getPhase(1).getPhysicalProperties().getDensity();
      rhoMix[i] = alpha * rhoG + (1.0 - alpha) * rhoL;
    }
    return rhoMix;
  }

  /**
   * <p>
   * Gets the mixture velocity profile along the pipe.
   * </p>
   *
   * @return an array of mixture velocities in m/s at each node
   */
  public double[] getMixtureVelocityProfile() {
    double[] uMix = new double[getTotalNumberOfNodes()];
    for (int i = 0; i < getTotalNumberOfNodes(); i++) {
      uMix[i] = flowNode[i].getSuperficialVelocity(0) + flowNode[i].getSuperficialVelocity(1);
    }
    return uMix;
  }

  /**
   * <p>
   * Gets the slip ratio profile along the pipe.
   * </p>
   *
   * <p>
   * Slip ratio S = u_G / u_L
   * </p>
   *
   * @return an array of slip ratios at each node
   */
  public double[] getSlipRatioProfile() {
    double[] slip = new double[getTotalNumberOfNodes()];
    for (int i = 0; i < getTotalNumberOfNodes(); i++) {
      double uG = flowNode[i].getVelocity(0);
      double uL = flowNode[i].getVelocity(1);
      if (uL > 0) {
        slip[i] = uG / uL;
      } else {
        slip[i] = 1.0;
      }
    }
    return slip;
  }

  // ==================== FRICTION AND PRESSURE DROP ====================

  /**
   * <p>
   * Gets the wall friction factor profile for a specific phase.
   * </p>
   *
   * @param phaseIndex 0 for gas phase, 1 for liquid phase
   * @return an array of friction factors at each node
   */
  public double[] getWallFrictionFactorProfile(int phaseIndex) {
    double[] f = new double[getTotalNumberOfNodes()];
    for (int i = 0; i < getTotalNumberOfNodes(); i++) {
      f[i] = flowNode[i].getWallFrictionFactor(phaseIndex);
    }
    return f;
  }

  /**
   * <p>
   * Gets the interphase friction factor profile.
   * </p>
   *
   * @return an array of interphase friction factors at each node
   */
  public double[] getInterphaseFrictionFactorProfile() {
    double[] f = new double[getTotalNumberOfNodes()];
    for (int i = 0; i < getTotalNumberOfNodes(); i++) {
      f[i] = flowNode[i].getInterPhaseFrictionFactor();
    }
    return f;
  }

  /**
   * <p>
   * Gets the pressure gradient profile along the pipe.
   * </p>
   *
   * @return an array of pressure gradients in Pa/m at each node
   */
  public double[] getPressureGradientProfile() {
    double[] dPdx = new double[getTotalNumberOfNodes()];
    for (int i = 1; i < getTotalNumberOfNodes(); i++) {
      double p1 = flowNode[i - 1].getBulkSystem().getPressure() * 1e5; // Pa
      double p2 = flowNode[i].getBulkSystem().getPressure() * 1e5; // Pa
      double dx = flowNode[i].getGeometry().getNodeLength();
      if (dx > 0) {
        dPdx[i] = (p1 - p2) / dx;
      }
    }
    dPdx[0] = dPdx[1]; // Extrapolate to first node
    return dPdx;
  }

  // ==================== FLOW PATTERN TRANSITION LOGIC ====================

  /**
   * <p>
   * Updates flow patterns at all nodes based on current conditions.
   * </p>
   *
   * <p>
   * When automatic flow pattern detection is enabled, this method:
   * </p>
   * <ul>
   * <li>Detects the current flow pattern at each node using the selected model</li>
   * <li>Creates new flow node instances if the pattern has changed</li>
   * <li>Preserves thermodynamic and flow state during transition</li>
   * </ul>
   */
  public void updateFlowPatterns() {
    if (!automaticFlowPatternDetection) {
      return;
    }

    detectFlowPatterns();

    for (int i = 0; i < getTotalNumberOfNodes(); i++) {
      FlowPattern currentPattern = nodeFlowPatterns[i];
      FlowPattern previousPattern = FlowPattern.fromString(flowNode[i].getFlowNodeType());

      if (currentPattern != previousPattern) {
        transitionFlowNodeType(i, currentPattern);
      }
    }
  }

  /**
   * <p>
   * Transitions a flow node to a new flow pattern type.
   * </p>
   *
   * <p>
   * This method creates a new flow node of the appropriate type and transfers all relevant state
   * including temperature, pressure, composition, velocities, and phase fractions.
   * </p>
   *
   * @param nodeIndex the node index to transition
   * @param newPattern the new flow pattern
   */
  protected void transitionFlowNodeType(int nodeIndex, FlowPattern newPattern) {
    // Store current state
    neqsim.thermo.system.SystemInterface currentSystem = flowNode[nodeIndex].getBulkSystem();
    neqsim.fluidmechanics.geometrydefinitions.GeometryDefinitionInterface geometry =
        flowNode[nodeIndex].getGeometry();
    double[] velocities = new double[2];
    double[] phaseFractions = new double[2];

    for (int p = 0; p < 2; p++) {
      velocities[p] = flowNode[nodeIndex].getVelocity(p);
      phaseFractions[p] = flowNode[nodeIndex].getPhaseFraction(p);
    }

    // Create new node of appropriate type
    neqsim.fluidmechanics.flownode.FlowNodeInterface newNode =
        createFlowNode(newPattern, currentSystem.clone(), geometry);

    // Transfer state to new node
    for (int p = 0; p < 2; p++) {
      newNode.setVelocity(p, velocities[p]);
      newNode.setPhaseFraction(p, phaseFractions[p]);
    }

    // Initialize new node
    newNode.init();

    // Replace the old node
    flowNode[nodeIndex] = newNode;
  }

  /**
   * <p>
   * Creates a flow node of the specified flow pattern type.
   * </p>
   *
   * @param pattern the flow pattern
   * @param system the thermodynamic system
   * @param geometry the pipe geometry
   * @return the created flow node
   */
  protected neqsim.fluidmechanics.flownode.FlowNodeInterface createFlowNode(FlowPattern pattern,
      neqsim.thermo.system.SystemInterface system,
      neqsim.fluidmechanics.geometrydefinitions.GeometryDefinitionInterface geometry) {
    switch (pattern) {
      case ANNULAR:
        return new neqsim.fluidmechanics.flownode.twophasenode.twophasepipeflownode.AnnularFlow(
            system, geometry);
      case SLUG:
        return new neqsim.fluidmechanics.flownode.twophasenode.twophasepipeflownode.SlugFlowNode(
            system, geometry);
      case BUBBLE:
      case DISPERSED_BUBBLE:
        return new neqsim.fluidmechanics.flownode.twophasenode.twophasepipeflownode.BubbleFlowNode(
            system, geometry);
      case DROPLET:
        return new neqsim.fluidmechanics.flownode.twophasenode.twophasepipeflownode.DropletFlowNode(
            system, geometry);
      case STRATIFIED:
      case STRATIFIED_WAVY:
      case CHURN:
      default:
        return new neqsim.fluidmechanics.flownode.twophasenode.twophasepipeflownode.StratifiedFlowNode(
            system, geometry);
    }
  }

  /**
   * <p>
   * Gets the count of flow pattern transitions along the pipe.
   * </p>
   *
   * @return the number of transitions
   */
  public int getFlowPatternTransitionCount() {
    if (nodeFlowPatterns == null || nodeFlowPatterns.length < 2) {
      return 0;
    }

    int transitions = 0;
    for (int i = 1; i < nodeFlowPatterns.length; i++) {
      if (nodeFlowPatterns[i] != nodeFlowPatterns[i - 1]) {
        transitions++;
      }
    }
    return transitions;
  }

  /**
   * <p>
   * Gets the positions where flow pattern transitions occur.
   * </p>
   *
   * @return an array of node indices where transitions occur
   */
  public int[] getFlowPatternTransitionPositions() {
    if (nodeFlowPatterns == null || nodeFlowPatterns.length < 2) {
      return new int[0];
    }

    java.util.List<Integer> transitions = new java.util.ArrayList<>();
    for (int i = 1; i < nodeFlowPatterns.length; i++) {
      if (nodeFlowPatterns[i] != nodeFlowPatterns[i - 1]) {
        transitions.add(i);
      }
    }
    return transitions.stream().mapToInt(Integer::intValue).toArray();
  }

  // ==================== PRESSURE DROP CORRELATIONS ====================

  /**
   * <p>
   * Calculates the total pressure drop along the pipe.
   * </p>
   *
   * @return the total pressure drop in bar
   */
  public double getTotalPressureDrop() {
    double inletPressure = flowNode[0].getBulkSystem().getPressure();
    double outletPressure = flowNode[getTotalNumberOfNodes() - 1].getBulkSystem().getPressure();
    return inletPressure - outletPressure;
  }

  /**
   * <p>
   * Calculates the frictional pressure drop component.
   * </p>
   *
   * @return the frictional pressure drop in bar
   */
  public double getFrictionalPressureDrop() {
    double totalFrictional = 0.0;

    for (int i = 0; i < getTotalNumberOfNodes() - 1; i++) {
      // Two-phase friction factor
      double fG = flowNode[i].getWallFrictionFactor(0);
      double fL = flowNode[i].getWallFrictionFactor(1);

      double rhoG = flowNode[i].getBulkSystem().getPhase(0).getPhysicalProperties().getDensity();
      double rhoL = flowNode[i].getBulkSystem().getPhase(1).getPhysicalProperties().getDensity();
      double uG = flowNode[i].getVelocity(0);
      double uL = flowNode[i].getVelocity(1);
      double alpha = flowNode[i].getPhaseFraction(0);

      double diameter = flowNode[i].getGeometry().getDiameter();
      double dx = flowNode[i].getGeometry().getNodeLength();

      // Frictional pressure drop per phase
      double dpFricG = 2.0 * fG * rhoG * uG * uG / diameter * alpha * dx;
      double dpFricL = 2.0 * fL * rhoL * uL * uL / diameter * (1 - alpha) * dx;

      totalFrictional += (dpFricG + dpFricL);
    }

    return totalFrictional / 1e5; // Convert Pa to bar
  }

  /**
   * <p>
   * Calculates the gravitational pressure drop component.
   * </p>
   *
   * @return the gravitational pressure drop in bar
   */
  public double getGravitationalPressureDrop() {
    double totalGravity = 0.0;
    final double g = 9.81;

    for (int i = 0; i < getTotalNumberOfNodes() - 1; i++) {
      double rhoG = flowNode[i].getBulkSystem().getPhase(0).getPhysicalProperties().getDensity();
      double rhoL = flowNode[i].getBulkSystem().getPhase(1).getPhysicalProperties().getDensity();
      double alpha = flowNode[i].getPhaseFraction(0);
      double rhoMix = alpha * rhoG + (1 - alpha) * rhoL;

      // Height change between nodes
      double dz = 0.0;
      if (i + 1 < getTotalNumberOfNodes()) {
        dz = flowNode[i + 1].getVerticalPositionOfNode() - flowNode[i].getVerticalPositionOfNode();
      }

      totalGravity += rhoMix * g * dz;
    }

    return totalGravity / 1e5; // Convert Pa to bar
  }

  /**
   * <p>
   * Calculates the acceleration pressure drop component.
   * </p>
   *
   * @return the acceleration pressure drop in bar
   */
  public double getAccelerationPressureDrop() {
    // Change in momentum flux between inlet and outlet
    double rhoG_in = flowNode[0].getBulkSystem().getPhase(0).getPhysicalProperties().getDensity();
    double rhoL_in = flowNode[0].getBulkSystem().getPhase(1).getPhysicalProperties().getDensity();
    double uG_in = flowNode[0].getVelocity(0);
    double uL_in = flowNode[0].getVelocity(1);
    double alpha_in = flowNode[0].getPhaseFraction(0);

    int lastNode = getTotalNumberOfNodes() - 1;
    double rhoG_out =
        flowNode[lastNode].getBulkSystem().getPhase(0).getPhysicalProperties().getDensity();
    double rhoL_out =
        flowNode[lastNode].getBulkSystem().getPhase(1).getPhysicalProperties().getDensity();
    double uG_out = flowNode[lastNode].getVelocity(0);
    double uL_out = flowNode[lastNode].getVelocity(1);
    double alpha_out = flowNode[lastNode].getPhaseFraction(0);

    // Momentum flux = ρ * u² * A * phase fraction
    double momIn = alpha_in * rhoG_in * uG_in * uG_in + (1 - alpha_in) * rhoL_in * uL_in * uL_in;
    double momOut =
        alpha_out * rhoG_out * uG_out * uG_out + (1 - alpha_out) * rhoL_out * uL_out * uL_out;

    return (momOut - momIn) / 1e5; // Convert Pa to bar
  }

  /**
   * <p>
   * Gets the pressure drop breakdown as a formatted string.
   * </p>
   *
   * @return a string describing the pressure drop components
   */
  public String getPressureDropBreakdown() {
    double totalDP = getTotalPressureDrop();
    double fricDP = getFrictionalPressureDrop();
    double gravDP = getGravitationalPressureDrop();
    double accelDP = getAccelerationPressureDrop();

    return String.format(
        "Pressure Drop Breakdown:%n" + "  Total:        %.4f bar%n" + "  Frictional:   %.4f bar%n"
            + "  Gravitational: %.4f bar%n" + "  Acceleration: %.4f bar%n",
        totalDP, fricDP, gravDP, accelDP);
  }

  /**
   * <p>
   * Calculates the two-phase pressure drop using Lockhart-Martinelli correlation.
   * </p>
   *
   * <p>
   * Reference: Lockhart, R.W. and Martinelli, R.C. (1949). "Proposed Correlation of Data for
   * Isothermal Two-Phase, Two-Component Flow in Pipes." Chemical Engineering Progress, 45(1),
   * 39-48.
   * </p>
   *
   * @param nodeIndex the node index
   * @return the two-phase pressure gradient in Pa/m
   */
  public double getLockhartMartinelliPressureGradient(int nodeIndex) {
    double rhoG =
        flowNode[nodeIndex].getBulkSystem().getPhase(0).getPhysicalProperties().getDensity();
    double rhoL =
        flowNode[nodeIndex].getBulkSystem().getPhase(1).getPhysicalProperties().getDensity();
    double muG =
        flowNode[nodeIndex].getBulkSystem().getPhase(0).getPhysicalProperties().getViscosity();
    double muL =
        flowNode[nodeIndex].getBulkSystem().getPhase(1).getPhysicalProperties().getViscosity();
    double usgVal = flowNode[nodeIndex].getSuperficialVelocity(0);
    double uslVal = flowNode[nodeIndex].getSuperficialVelocity(1);
    double diameter = flowNode[nodeIndex].getGeometry().getDiameter();

    // Single-phase pressure gradients
    double reG = rhoG * usgVal * diameter / muG;
    double reL = rhoL * uslVal * diameter / muL;

    // Friction factors (Blasius)
    double fG = (reG > 0) ? 0.079 * Math.pow(reG, -0.25) : 0;
    double fL = (reL > 0) ? 0.079 * Math.pow(reL, -0.25) : 0;

    // Single-phase pressure gradients
    double dPdxG = 2.0 * fG * rhoG * usgVal * usgVal / diameter;
    double dPdxL = 2.0 * fL * rhoL * uslVal * uslVal / diameter;

    // Lockhart-Martinelli parameter
    double X = (dPdxL > 0 && dPdxG > 0) ? Math.sqrt(dPdxL / dPdxG) : 1.0;

    // Two-phase multiplier (Chisholm C parameter, turbulent-turbulent = 20)
    double C = 20.0;
    double phi2L = 1.0 + C / X + 1.0 / (X * X);

    return dPdxL * phi2L;
  }

  /**
   * <p>
   * Gets the Lockhart-Martinelli pressure gradient profile.
   * </p>
   *
   * @return an array of pressure gradients in Pa/m at each node
   */
  public double[] getLockhartMartinelliPressureGradientProfile() {
    double[] dPdx = new double[getTotalNumberOfNodes()];
    for (int i = 0; i < getTotalNumberOfNodes(); i++) {
      dPdx[i] = getLockhartMartinelliPressureGradient(i);
    }
    return dPdx;
  }

  // ==================== INCLINATION AND GEOMETRY ====================

  /**
   * Sets the pipe inclination angle.
   *
   * @param angle the inclination angle in radians (positive = upward flow)
   */
  public void setInclination(double angle) {
    this.inclination = angle;
  }

  /**
   * Sets the pipe inclination angle with unit.
   *
   * @param angle the inclination angle
   * @param unit the angle unit ("deg", "degrees", "rad", "radians")
   */
  public void setInclination(double angle, String unit) {
    switch (unit.toLowerCase()) {
      case "deg":
      case "degrees":
        this.inclination = Math.toRadians(angle);
        break;
      case "rad":
      case "radians":
      default:
        this.inclination = angle;
        break;
    }
  }

  /**
   * Gets the pipe inclination angle.
   *
   * @return the inclination angle in radians
   */
  public double getInclination() {
    return inclination;
  }

  /**
   * Gets the pipe inclination angle in degrees.
   *
   * @return the inclination angle in degrees
   */
  public double getInclinationDegrees() {
    return Math.toDegrees(inclination);
  }

  /**
   * Checks if the pipe is horizontal (inclination ≈ 0).
   *
   * @return true if horizontal
   */
  public boolean isHorizontal() {
    return Math.abs(inclination) < 1e-6;
  }

  /**
   * Checks if the pipe is vertical (|inclination| ≈ 90°).
   *
   * @return true if vertical
   */
  public boolean isVertical() {
    return Math.abs(Math.abs(inclination) - Math.PI / 2.0) < 1e-6;
  }

  /**
   * Checks if flow is upward (positive inclination).
   *
   * @return true if upward flow
   */
  public boolean isUpwardFlow() {
    return inclination > 1e-6;
  }

  /**
   * Checks if flow is downward (negative inclination).
   *
   * @return true if downward flow
   */
  public boolean isDownwardFlow() {
    return inclination < -1e-6;
  }

  /**
   * Gets the gravitational pressure gradient component at a node.
   *
   * @param nodeIndex the node index
   * @return the gravitational pressure gradient in Pa/m (positive for upward flow)
   */
  public double getGravitationalPressureGradient(int nodeIndex) {
    if (flowNode == null || flowNode[nodeIndex] == null) {
      return 0.0;
    }
    // Calculate mixture density from phase densities and holdup
    double[] gasDensity = getDensityProfile(0);
    double[] liquidDensity = getDensityProfile(1);
    double[] voidFraction = getVoidFractionProfile();
    double mixtureDensity = voidFraction[nodeIndex] * gasDensity[nodeIndex]
        + (1.0 - voidFraction[nodeIndex]) * liquidDensity[nodeIndex];
    return mixtureDensity * 9.81 * Math.sin(inclination);
  }

  /**
   * Gets the elevation profile along the pipe.
   *
   * @return an array of elevations in meters at each node
   */
  public double[] getElevationProfile() {
    double[] elevation = new double[getTotalNumberOfNodes()];
    double[] positions = getPositionProfile();
    for (int i = 0; i < getTotalNumberOfNodes(); i++) {
      elevation[i] = positions[i] * Math.sin(inclination);
    }
    return elevation;
  }

  // ==================== CSV EXPORT ====================

  /**
   * Exports all simulation results to a CSV file.
   *
   * <p>
   * The CSV file contains columns for position, temperature, pressure, velocity (gas/liquid), void
   * fraction, density, and other calculated properties.
   * </p>
   *
   * @param filePath the path to the output CSV file
   * @throws java.io.IOException if file writing fails
   */
  public void exportToCSV(String filePath) throws java.io.IOException {
    exportToCSV(filePath, ";");
  }

  /**
   * Exports all simulation results to a CSV file with a custom delimiter.
   *
   * @param filePath the path to the output CSV file
   * @param delimiter the column delimiter (e.g., "," or ";")
   * @throws java.io.IOException if file writing fails
   */
  public void exportToCSV(String filePath, String delimiter) throws java.io.IOException {
    try (java.io.PrintWriter writer =
        new java.io.PrintWriter(new java.io.BufferedWriter(new java.io.FileWriter(filePath)))) {

      // Write header
      String[] headers = {"Position [m]", "Elevation [m]", "Temperature [K]", "Pressure [Pa]",
          "Gas Velocity [m/s]", "Liquid Velocity [m/s]", "Superficial Gas Velocity [m/s]",
          "Superficial Liquid Velocity [m/s]", "Void Fraction [-]", "Liquid Holdup [-]",
          "Gas Density [kg/m3]", "Liquid Density [kg/m3]", "Mixture Density [kg/m3]",
          "Gas Viscosity [Pa.s]", "Liquid Viscosity [Pa.s]", "Reynolds Gas [-]",
          "Reynolds Liquid [-]", "Pressure Gradient [Pa/m]", "Flow Pattern"};
      writer.println(String.join(delimiter, headers));

      // Get all profiles
      double[] position = getPositionProfile();
      double[] elevation = getElevationProfile();
      double[] temperature = getTemperatureProfile();
      double[] pressure = getPressureProfile();
      double[] gasVelocity = getVelocityProfile(0);
      double[] liquidVelocity = getVelocityProfile(1);
      double[] usg = getSuperficialVelocityProfile(0);
      double[] usl = getSuperficialVelocityProfile(1);
      double[] voidFraction = getVoidFractionProfile();
      double[] liquidHoldup = getLiquidHoldupProfile();
      double[] gasDensity = getDensityProfile(0);
      double[] liquidDensity = getDensityProfile(1);
      double[] mixtureDensity = getMixtureDensityProfile();
      double[] gasViscosity = getViscosityProfile(0);
      double[] liquidViscosity = getViscosityProfile(1);
      double[] reGas = getReynoldsNumberProfile(0);
      double[] reLiquid = getReynoldsNumberProfile(1);
      double[] pressureGradient = getPressureGradientProfile();
      FlowPattern[] patterns = getFlowPatternProfile();

      // Write data rows
      for (int i = 0; i < getTotalNumberOfNodes(); i++) {
        String[] values = {String.format("%.6f", position[i]), String.format("%.6f", elevation[i]),
            String.format("%.4f", temperature[i]), String.format("%.2f", pressure[i]),
            String.format("%.6f", gasVelocity[i]), String.format("%.6f", liquidVelocity[i]),
            String.format("%.6f", usg[i]), String.format("%.6f", usl[i]),
            String.format("%.6f", voidFraction[i]), String.format("%.6f", liquidHoldup[i]),
            String.format("%.4f", gasDensity[i]), String.format("%.4f", liquidDensity[i]),
            String.format("%.4f", mixtureDensity[i]), String.format("%.8e", gasViscosity[i]),
            String.format("%.8e", liquidViscosity[i]), String.format("%.2f", reGas[i]),
            String.format("%.2f", reLiquid[i]), String.format("%.4f", pressureGradient[i]),
            patterns[i] != null ? patterns[i].getName() : "unknown"};
        writer.println(String.join(delimiter, values));
      }
    }
  }

  /**
   * Exports selected profiles to a CSV file.
   *
   * @param filePath the path to the output CSV file
   * @param profiles array of profile names to export (e.g., "position", "temperature", "pressure")
   * @throws java.io.IOException if file writing fails
   */
  public void exportProfilesToCSV(String filePath, String[] profiles) throws java.io.IOException {
    exportProfilesToCSV(filePath, profiles, ";");
  }

  /**
   * Exports selected profiles to a CSV file with a custom delimiter.
   *
   * @param filePath the path to the output CSV file
   * @param profiles array of profile names to export
   * @param delimiter the column delimiter
   * @throws java.io.IOException if file writing fails
   */
  public void exportProfilesToCSV(String filePath, String[] profiles, String delimiter)
      throws java.io.IOException {
    try (java.io.PrintWriter writer =
        new java.io.PrintWriter(new java.io.BufferedWriter(new java.io.FileWriter(filePath)))) {

      // Build header and collect data
      java.util.List<String> headers = new java.util.ArrayList<>();
      java.util.List<double[]> data = new java.util.ArrayList<>();

      for (String profile : profiles) {
        switch (profile.toLowerCase()) {
          case "position":
            headers.add("Position [m]");
            data.add(getPositionProfile());
            break;
          case "elevation":
            headers.add("Elevation [m]");
            data.add(getElevationProfile());
            break;
          case "temperature":
            headers.add("Temperature [K]");
            data.add(getTemperatureProfile());
            break;
          case "pressure":
            headers.add("Pressure [Pa]");
            data.add(getPressureProfile());
            break;
          case "gasvelocity":
          case "gas_velocity":
            headers.add("Gas Velocity [m/s]");
            data.add(getVelocityProfile(0));
            break;
          case "liquidvelocity":
          case "liquid_velocity":
            headers.add("Liquid Velocity [m/s]");
            data.add(getVelocityProfile(1));
            break;
          case "voidfraction":
          case "void_fraction":
            headers.add("Void Fraction [-]");
            data.add(getVoidFractionProfile());
            break;
          case "liquidholdup":
          case "liquid_holdup":
            headers.add("Liquid Holdup [-]");
            data.add(getLiquidHoldupProfile());
            break;
          case "gasdensity":
          case "gas_density":
            headers.add("Gas Density [kg/m3]");
            data.add(getDensityProfile(0));
            break;
          case "liquiddensity":
          case "liquid_density":
            headers.add("Liquid Density [kg/m3]");
            data.add(getDensityProfile(1));
            break;
          case "mixturedensity":
          case "mixture_density":
            headers.add("Mixture Density [kg/m3]");
            data.add(getMixtureDensityProfile());
            break;
          case "pressuregradient":
          case "pressure_gradient":
            headers.add("Pressure Gradient [Pa/m]");
            data.add(getPressureGradientProfile());
            break;
          default:
            // Unknown profile, skip
            break;
        }
      }

      // Write header
      writer.println(String.join(delimiter, headers));

      // Write data rows
      int numRows = getTotalNumberOfNodes();
      for (int i = 0; i < numRows; i++) {
        StringBuilder row = new StringBuilder();
        for (int j = 0; j < data.size(); j++) {
          if (j > 0) {
            row.append(delimiter);
          }
          row.append(String.format("%.6f", data.get(j)[i]));
        }
        writer.println(row.toString());
      }
    }
  }

  /**
   * Gets a summary report of the simulation results as a formatted string.
   *
   * @return a multi-line string containing the simulation summary
   */
  public String getSummaryReport() {
    StringBuilder sb = new StringBuilder();
    sb.append("=== Two-Phase Pipe Flow Simulation Summary ===\n\n");

    // Geometry
    sb.append("GEOMETRY:\n");
    sb.append(String.format("  Pipe Length: %.2f m\n", getSystemLength()));
    sb.append(String.format("  Inclination: %.2f degrees\n", getInclinationDegrees()));
    sb.append(String.format("  Number of Nodes: %d\n", getTotalNumberOfNodes()));
    sb.append(String.format("  Flow Direction: %s\n",
        isUpwardFlow() ? "Upward" : (isDownwardFlow() ? "Downward" : "Horizontal")));
    sb.append("\n");

    // Inlet conditions
    double[] temp = getTemperatureProfile();
    double[] pres = getPressureProfile();
    double[] usg = getSuperficialVelocityProfile(0);
    double[] usl = getSuperficialVelocityProfile(1);

    sb.append("INLET CONDITIONS:\n");
    sb.append(String.format("  Temperature: %.2f K (%.2f °C)\n", temp[0], temp[0] - 273.15));
    sb.append(String.format("  Pressure: %.2f Pa (%.2f bar)\n", pres[0], pres[0] / 1e5));
    sb.append(String.format("  Superficial Gas Velocity: %.4f m/s\n", usg[0]));
    sb.append(String.format("  Superficial Liquid Velocity: %.4f m/s\n", usl[0]));
    sb.append("\n");

    // Outlet conditions
    int n = getTotalNumberOfNodes() - 1;
    sb.append("OUTLET CONDITIONS:\n");
    sb.append(String.format("  Temperature: %.2f K (%.2f °C)\n", temp[n], temp[n] - 273.15));
    sb.append(String.format("  Pressure: %.2f Pa (%.2f bar)\n", pres[n], pres[n] / 1e5));
    sb.append(String.format("  Superficial Gas Velocity: %.4f m/s\n", usg[n]));
    sb.append(String.format("  Superficial Liquid Velocity: %.4f m/s\n", usl[n]));
    sb.append("\n");

    // Pressure drop
    sb.append("PRESSURE DROP:\n");
    double totalDp = pres[0] - pres[n];
    sb.append(String.format("  Total Pressure Drop: %.2f Pa (%.4f bar)\n", totalDp, totalDp / 1e5));
    sb.append(
        String.format("  Average Pressure Gradient: %.2f Pa/m\n", totalDp / getSystemLength()));
    sb.append("\n");

    // Temperature change
    sb.append("TEMPERATURE CHANGE:\n");
    double deltaT = temp[n] - temp[0];
    sb.append(String.format("  Temperature Change: %.2f K\n", deltaT));
    sb.append(String.format("  Heat Loss Rate: %.2f W\n", getTotalHeatLoss()));
    sb.append("\n");

    // Flow patterns
    FlowPattern[] patterns = getFlowPatternProfile();
    java.util.Map<FlowPattern, Integer> patternCounts = new java.util.LinkedHashMap<>();
    for (FlowPattern p : patterns) {
      patternCounts.merge(p, 1, Integer::sum);
    }
    sb.append("FLOW PATTERNS:\n");
    for (java.util.Map.Entry<FlowPattern, Integer> entry : patternCounts.entrySet()) {
      double percentage = 100.0 * entry.getValue() / patterns.length;
      sb.append(String.format("  %s: %.1f%%\n", entry.getKey().getName(), percentage));
    }

    return sb.toString();
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
