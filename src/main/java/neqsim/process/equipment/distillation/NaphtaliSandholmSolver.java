package neqsim.process.equipment.distillation;

import java.util.List;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Simultaneous Newton solver for distillation-column MESH equations.
 *
 * <p>
 * The implementation follows the Naphtali-Sandholm linearization idea: each tray contributes a
 * block of variables consisting of liquid component flows, tray temperature, and vapor flow. The
 * residual block contains component material balances, one energy or fixed-temperature equation,
 * and one phase-equilibrium summation equation. The finite-difference Jacobian is assembled only
 * for neighboring tray blocks and solved with a guarded Newton step.
 * </p>
 *
 * @author esol
 * @version 1.0
 */
final class NaphtaliSandholmSolver {
  /** Logger for solver diagnostics. */
  private static final Logger logger = LogManager.getLogger(NaphtaliSandholmSolver.class);
  /** Minimum molar flow used to avoid singular composition states. */
  private static final double MIN_FLOW = 1.0e-12;
  /** Minimum positive K-value used when an EOS flash cannot provide one. */
  private static final double MIN_K_VALUE = 1.0e-8;
  /** Maximum positive K-value accepted from a flash before clipping. */
  private static final double MAX_K_VALUE = 1.0e8;
  /** Relative finite-difference perturbation for flow and temperature variables. */
  private static final double RELATIVE_PERTURBATION = 1.0e-4;
  /** Small diagonal regularization used when the numerical Jacobian is nearly singular. */
  private static final double JACOBIAN_REGULARIZATION = 1.0e-8;
  /** Maximum Newton temperature move in one iteration, in Kelvin. */
  private static final double MAX_TEMPERATURE_STEP = 8.0;
  /** Maximum relative flow move in one iteration. */
  private static final double MAX_RELATIVE_FLOW_STEP = 0.65;
  /** Lower temperature bound used by the guarded Newton update. */
  private static final double MIN_TEMPERATURE = 50.0;
  /** Upper temperature bound used by the guarded Newton update. */
  private static final double MAX_TEMPERATURE = 1000.0;

  /** Column being solved. */
  private final DistillationColumn column;
  /** Calculation identifier propagated to accepted tray streams. */
  private UUID calculationIdentifier;
  /** Number of trays in the active column. */
  private int trayCount;
  /** Number of components in the active fluid. */
  private int componentCount;
  /** Number of variables in each tray block. */
  private int variablesPerTray;
  /** Total number of Newton variables and residual equations. */
  private int totalVariables;
  /** Component names in solver order. */
  private String[] componentNames;
  /** Reference fluid cloned from the first feed stream. */
  private SystemInterface referenceSystem;
  /** Tray temperatures in Kelvin. */
  private double[] temperatures;
  /** Tray pressures in bara. */
  private double[] pressures;
  /** Vapor molar flow leaving each tray in mol/hr. */
  private double[] vaporFlows;
  /** Liquid component molar flows leaving each tray in mol/hr. */
  private double[][] liquidComponentFlows;
  /** Feed component molar flows entering each tray in mol/hr. */
  private double[][] feedComponentFlows;
  /** Total feed enthalpy entering each tray in the EOS enthalpy basis. */
  private double[] feedEnthalpies;
  /** Heat input for each tray in the same sign convention as {@link SimpleTray#heatInput}. */
  private double[] trayHeatInputs;
  /** Fixed tray-temperature specifications. NaN means energy balance is active. */
  private double[] fixedTemperatures;
  /** Equilibrium K-values for each tray and component. */
  private double[][] kValues;
  /** Liquid molar enthalpy estimate for each tray. */
  private double[] liquidMolarEnthalpies;
  /** Vapor molar enthalpy estimate for each tray. */
  private double[] vaporMolarEnthalpies;
  /** Characteristic molar flow used for residual scaling. */
  private double flowScale = 1.0;
  /** Maximum Newton iterations. */
  private int maxIterations = 8;
  /** Target scaled residual norm. */
  private double residualTolerance = 1.0;
  /** Iterations performed by the latest solve. */
  private int lastIterations = 0;
  /** Latest scaled residual norm. */
  private double lastResidualNorm = Double.NaN;
  /** Latest average accepted tray-temperature move. */
  private double lastTemperatureResidual = Double.NaN;
  /** Latest external mass-balance error estimate. */
  private double lastMassBalanceError = Double.NaN;
  /** Latest scaled energy residual estimate. */
  private double lastEnergyResidual = Double.NaN;
  /** Whether the latest solve accepted a residual-improving state. */
  private boolean accepted = false;

  /**
   * Create a solver for a column.
   *
   * @param column column to solve
   */
  NaphtaliSandholmSolver(DistillationColumn column) {
    this.column = column;
  }

  /**
   * Configure the maximum number of Newton iterations.
   *
   * @param maxIterations maximum iteration count, values below one are clipped to one
   */
  void setMaxIterations(int maxIterations) {
    this.maxIterations = Math.max(1, maxIterations);
  }

  /**
   * Configure the scaled MESH residual tolerance.
   *
   * @param residualTolerance positive scaled residual tolerance
   */
  void setResidualTolerance(double residualTolerance) {
    if (Double.isFinite(residualTolerance) && residualTolerance > 0.0) {
      this.residualTolerance = residualTolerance;
    }
  }

  /**
   * Solve the column and write the accepted tray state back to the column.
   *
   * @param id calculation identifier
   * @return {@code true} when a residual-improving state was accepted
   */
  boolean solve(UUID id) {
    calculationIdentifier = id;
    accepted = false;
    initializeFromColumn();
    evaluateAllThermo();

    double[] residual = computeResidual();
    double initialNorm = vectorNorm(residual);
    double bestNorm = initialNorm;
    StateSnapshot bestState = createSnapshot();
    StateSnapshot initialState = createSnapshot();

    if (!Double.isFinite(initialNorm)) {
      restoreSnapshot(initialState);
      return false;
    }

    for (int iteration = 1; iteration <= maxIterations; iteration++) {
      lastIterations = iteration;
      if (bestNorm <= residualTolerance) {
        break;
      }

      double[][] jacobian = computeJacobian(residual);
      double[] correction = solveBlockTridiagonal(jacobian, residual);
      if (correction == null) {
        correction = solveDenseLinearSystem(jacobian, residual);
      }
      if (correction == null || !isFiniteVector(correction)) {
        logger.debug("Naphtali-Sandholm linear solve failed for column {} at iteration {}",
            column.getName(), Integer.valueOf(iteration));
        break;
      }

      double trustScale = applyTrustRegion(correction);
      double trialNorm = lineSearch(correction, vectorNorm(residual));
      residual = computeResidual();
      double currentNorm = vectorNorm(residual);
      if (Double.isFinite(trialNorm) && trialNorm < currentNorm) {
        currentNorm = trialNorm;
      }

      if (Double.isFinite(currentNorm) && currentNorm < bestNorm) {
        bestNorm = currentNorm;
        bestState = createSnapshot();
      }

      logger.debug("Naphtali-Sandholm iteration {} residual={} trustScale={}",
          Integer.valueOf(iteration), Double.valueOf(currentNorm), Double.valueOf(trustScale));

      if (!Double.isFinite(currentNorm) || currentNorm >= bestNorm * 1.0e6) {
        restoreSnapshot(bestState);
        break;
      }

      if (currentNorm <= residualTolerance || Math.abs(initialNorm - bestNorm) <= 1.0e-12) {
        break;
      }
    }

    accepted = bestNorm <= residualTolerance || bestNorm < initialNorm * 0.999;
    if (accepted) {
      restoreSnapshot(bestState);
    } else {
      restoreSnapshot(initialState);
      bestNorm = initialNorm;
      evaluateAllThermo();
      lastResidualNorm = bestNorm;
      lastMassBalanceError = computeExternalMassBalanceError();
      lastEnergyResidual = computeEnergyResidualNorm();
      return false;
    }
    evaluateAllThermo();
    lastResidualNorm = bestNorm;
    lastMassBalanceError = computeExternalMassBalanceError();
    lastEnergyResidual = computeEnergyResidualNorm();
    applyStateToColumn();
    return accepted;
  }

  /**
   * Get the latest iteration count.
   *
   * @return latest iteration count
   */
  int getLastIterations() {
    return lastIterations;
  }

  /**
   * Get the latest scaled MESH residual norm.
   *
   * @return latest residual norm
   */
  double getLastResidualNorm() {
    return lastResidualNorm;
  }

  /**
   * Get the latest average tray-temperature residual.
   *
   * @return latest temperature residual in Kelvin
   */
  double getLastTemperatureResidual() {
    return lastTemperatureResidual;
  }

  /**
   * Get the latest external mass-balance error.
   *
   * @return relative external mass-balance error
   */
  double getLastMassBalanceError() {
    return lastMassBalanceError;
  }

  /**
   * Get the latest scaled energy residual.
   *
   * @return scaled energy residual norm
   */
  double getLastEnergyResidual() {
    return lastEnergyResidual;
  }

  /**
   * Check whether the latest solve accepted a Newton-refined state.
   *
   * @return {@code true} when a residual-improving state was accepted
   */
  boolean wasAccepted() {
    return accepted;
  }

  /** Initialize solver arrays from the current column state. */
  private void initializeFromColumn() {
    trayCount = column.numberOfTrays;
    componentNames = resolveComponentNames();
    componentCount = componentNames.length;
    variablesPerTray = componentCount + 2;
    totalVariables = trayCount * variablesPerTray;
    referenceSystem = resolveReferenceSystem();

    temperatures = new double[trayCount];
    pressures = new double[trayCount];
    vaporFlows = new double[trayCount];
    liquidComponentFlows = new double[trayCount][componentCount];
    feedComponentFlows = new double[trayCount][componentCount];
    feedEnthalpies = new double[trayCount];
    trayHeatInputs = new double[trayCount];
    fixedTemperatures = new double[trayCount];
    kValues = new double[trayCount][componentCount];
    liquidMolarEnthalpies = new double[trayCount];
    vaporMolarEnthalpies = new double[trayCount];

    double totalFeedFlow = initializeFeeds();
    flowScale = Math.max(1.0, totalFeedFlow / Math.max(1, trayCount));
    initializeTrayVariables(totalFeedFlow);
  }

  /**
   * Resolve the component name order from products, trays, or feeds.
   *
   * @return component names for the solver state vector
   */
  private String[] resolveComponentNames() {
    String[] names = componentNames(column.getGasOutStream());
    if (names.length > 0) {
      return names;
    }
    names = componentNames(column.getLiquidOutStream());
    if (names.length > 0) {
      return names;
    }
    for (int trayIndex = 0; trayIndex < column.trays.size(); trayIndex++) {
      SimpleTray tray = column.trays.get(trayIndex);
      names = componentNames(tray.getGasOutStream());
      if (names.length > 0) {
        return names;
      }
      names = componentNames(tray.getLiquidOutStream());
      if (names.length > 0) {
        return names;
      }
    }
    SystemInterface system = resolveReferenceSystem();
    return system == null ? new String[0] : system.getCompNames();
  }

  /**
   * Resolve a reference system from the first available feed or product stream.
   *
   * @return cloned reference system
   */
  private SystemInterface resolveReferenceSystem() {
    for (int trayIndex = 0; trayIndex < column.numberOfTrays; trayIndex++) {
      List<StreamInterface> feeds = column.getExternalFeedStreams(trayIndex);
      for (StreamInterface feed : feeds) {
        if (feed != null && feed.getThermoSystem() != null) {
          return feed.getThermoSystem().clone();
        }
      }
    }
    if (column.getGasOutStream() != null && column.getGasOutStream().getThermoSystem() != null) {
      return column.getGasOutStream().getThermoSystem().clone();
    }
    return column.getLiquidOutStream().getThermoSystem().clone();
  }

  /**
   * Get component names from a stream.
   *
   * @param stream stream to inspect
   * @return component names, or an empty array when unavailable
   */
  private String[] componentNames(StreamInterface stream) {
    try {
      if (stream == null || stream.getThermoSystem() == null
          || stream.getThermoSystem().getNumberOfComponents() == 0) {
        return new String[0];
      }
      return stream.getThermoSystem().getCompNames();
    } catch (RuntimeException exception) {
      return new String[0];
    }
  }

  /**
   * Initialize feed flow and enthalpy arrays.
   *
   * @return total feed molar flow in mol/hr
   */
  private double initializeFeeds() {
    double totalFeedFlow = 0.0;
    for (int trayIndex = 0; trayIndex < trayCount; trayIndex++) {
      List<StreamInterface> feeds = column.getExternalFeedStreams(trayIndex);
      for (StreamInterface feed : feeds) {
        if (feed == null || feed.getThermoSystem() == null) {
          continue;
        }
        double feedFlow = safeFlow(feed, "mol/hr");
        totalFeedFlow += feedFlow;
        for (int componentIndex = 0; componentIndex < componentCount; componentIndex++) {
          feedComponentFlows[trayIndex][componentIndex] +=
              componentFlow(feed, componentNames[componentIndex]);
        }
        feedEnthalpies[trayIndex] += streamEnthalpy(feed);
      }
    }
    return Math.max(totalFeedFlow, MIN_FLOW);
  }

  /**
   * Initialize tray temperatures, pressures, liquid component flows, and vapor flows.
   *
   * @param totalFeedFlow total external feed flow in mol/hr
   */
  private void initializeTrayVariables(double totalFeedFlow) {
    double[] totalFeedComposition = totalFeedComposition();
    for (int trayIndex = 0; trayIndex < trayCount; trayIndex++) {
      SimpleTray tray = column.trays.get(trayIndex);
      StreamInterface liquid = tray.getLiquidOutStream();
      StreamInterface vapor = tray.getGasOutStream();
      temperatures[trayIndex] = safeTemperature(tray);
      pressures[trayIndex] = safePressure(tray);
      vaporFlows[trayIndex] = Math.max(MIN_FLOW, safeFlow(vapor, "mol/hr"));
      trayHeatInputs[trayIndex] = tray.heatInput;
      fixedTemperatures[trayIndex] =
          tray.isSetOutTemperature() && Double.isFinite(tray.getOutTemperature())
              ? tray.getOutTemperature()
              : Double.NaN;

      double liquidTotal = 0.0;
      for (int componentIndex = 0; componentIndex < componentCount; componentIndex++) {
        double flow = componentFlow(liquid, componentNames[componentIndex]);
        liquidComponentFlows[trayIndex][componentIndex] = Math.max(0.0, flow);
        liquidTotal += Math.max(0.0, flow);
      }
      if (liquidTotal <= MIN_FLOW) {
        double seedLiquid = Math.max(MIN_FLOW, totalFeedFlow / Math.max(1, trayCount) * 0.5);
        for (int componentIndex = 0; componentIndex < componentCount; componentIndex++) {
          liquidComponentFlows[trayIndex][componentIndex] =
              seedLiquid * totalFeedComposition[componentIndex];
        }
      }
      if (vaporFlows[trayIndex] <= MIN_FLOW) {
        vaporFlows[trayIndex] = Math.max(MIN_FLOW, totalFeedFlow / Math.max(1, trayCount) * 0.5);
      }
    }
  }

  /**
   * Calculate the overall feed composition.
   *
   * @return normalized feed composition
   */
  private double[] totalFeedComposition() {
    double[] composition = new double[componentCount];
    double total = 0.0;
    for (int trayIndex = 0; trayIndex < trayCount; trayIndex++) {
      for (int componentIndex = 0; componentIndex < componentCount; componentIndex++) {
        composition[componentIndex] += feedComponentFlows[trayIndex][componentIndex];
        total += feedComponentFlows[trayIndex][componentIndex];
      }
    }
    if (total <= MIN_FLOW) {
      double equalFraction = componentCount == 0 ? 0.0 : 1.0 / componentCount;
      for (int componentIndex = 0; componentIndex < componentCount; componentIndex++) {
        composition[componentIndex] = equalFraction;
      }
      return composition;
    }
    for (int componentIndex = 0; componentIndex < componentCount; componentIndex++) {
      composition[componentIndex] /= total;
    }
    return composition;
  }

  /**
   * Get a tray temperature with fallbacks.
   *
   * @param tray tray to inspect
   * @return finite tray temperature in Kelvin
   */
  private double safeTemperature(SimpleTray tray) {
    double temperature = tray.getTemperature();
    if (Double.isFinite(temperature)) {
      return temperature;
    }
    try {
      temperature = tray.getThermoSystem().getTemperature();
    } catch (RuntimeException exception) {
      temperature = referenceSystem.getTemperature();
    }
    return Double.isFinite(temperature) ? temperature : referenceSystem.getTemperature();
  }

  /**
   * Get a tray pressure with fallbacks.
   *
   * @param tray tray to inspect
   * @return finite tray pressure in bara
   */
  private double safePressure(SimpleTray tray) {
    if (tray.trayPressure > 0.0 && Double.isFinite(tray.trayPressure)) {
      return tray.trayPressure;
    }
    try {
      double pressure = tray.getThermoSystem().getPressure();
      if (Double.isFinite(pressure) && pressure > 0.0) {
        return pressure;
      }
    } catch (RuntimeException exception) {
      logger.debug("Could not read tray pressure for {}", tray.getName(), exception);
    }
    return referenceSystem.getPressure();
  }

  /** Evaluate K-values and phase enthalpies for every tray. */
  private void evaluateAllThermo() {
    for (int trayIndex = 0; trayIndex < trayCount; trayIndex++) {
      evaluateThermoForTray(trayIndex);
    }
  }

  /**
   * Evaluate K-values and phase enthalpies for one tray.
   *
   * @param trayIndex tray index
   */
  private void evaluateThermoForTray(int trayIndex) {
    double liquidFlow = liquidFlow(trayIndex);
    double[] liquidComposition = liquidComposition(trayIndex, liquidFlow);
    SystemInterface system = createSystem(liquidComposition, temperatures[trayIndex],
        pressures[trayIndex], Math.max(1.0, liquidFlow + vaporFlows[trayIndex]));
    try {
      system.setNumberOfPhases(2);
      ThermodynamicOperations operations = new ThermodynamicOperations(system);
      operations.TPflash();
      system.init(3);
    } catch (RuntimeException exception) {
      logger.debug("Naphtali-Sandholm tray flash failed on tray {}", Integer.valueOf(trayIndex),
          exception);
      system.init(1);
    }

    PhaseInterface gasPhase = findPhase(system, "gas");
    PhaseInterface liquidPhase = findLiquidPhase(system);
    for (int componentIndex = 0; componentIndex < componentCount; componentIndex++) {
      kValues[trayIndex][componentIndex] = computeKValue(system, gasPhase, liquidPhase,
          componentIndex, liquidComposition[componentIndex]);
    }
    liquidMolarEnthalpies[trayIndex] = phaseMolarEnthalpy(liquidPhase, system);
    vaporMolarEnthalpies[trayIndex] = phaseMolarEnthalpy(gasPhase, system);
  }

  /**
   * Compute a bounded K-value for a component.
   *
   * @param system tray thermodynamic system
   * @param gasPhase gas phase, or null if absent
   * @param liquidPhase liquid phase, or null if absent
   * @param componentIndex component index
   * @param liquidFraction liquid mole fraction used as fallback denominator
   * @return bounded positive K-value
   */
  private double computeKValue(SystemInterface system, PhaseInterface gasPhase,
      PhaseInterface liquidPhase, int componentIndex, double liquidFraction) {
    double value = Double.NaN;
    try {
      if (gasPhase != null && liquidPhase != null) {
        double x = Math.max(MIN_FLOW, liquidPhase.getComponent(componentIndex).getx());
        double y = Math.max(MIN_FLOW, gasPhase.getComponent(componentIndex).getx());
        value = y / x;
      }
    } catch (RuntimeException exception) {
      value = Double.NaN;
    }
    if (!Double.isFinite(value) || value <= 0.0) {
      try {
        value = system.getComponent(componentIndex).getK();
      } catch (RuntimeException exception) {
        value = Double.NaN;
      }
    }
    if (!Double.isFinite(value) || value <= 0.0) {
      value = Math.max(MIN_K_VALUE, 1.0 + 0.1 * (0.5 - liquidFraction));
    }
    return Math.max(MIN_K_VALUE, Math.min(MAX_K_VALUE, value));
  }

  /**
   * Find a phase by phase type name.
   *
   * @param system system to inspect
   * @param phaseTypeName phase type name
   * @return matching phase, or null when absent
   */
  private PhaseInterface findPhase(SystemInterface system, String phaseTypeName) {
    for (int phaseIndex = 0; phaseIndex < system.getNumberOfPhases(); phaseIndex++) {
      if (phaseTypeName.equals(system.getPhase(phaseIndex).getPhaseTypeName())) {
        return system.getPhase(phaseIndex);
      }
    }
    return null;
  }

  /**
   * Find the best liquid-like phase in a system.
   *
   * @param system system to inspect
   * @return liquid-like phase, or null when absent
   */
  private PhaseInterface findLiquidPhase(SystemInterface system) {
    for (int phaseIndex = 0; phaseIndex < system.getNumberOfPhases(); phaseIndex++) {
      String typeName = system.getPhase(phaseIndex).getPhaseTypeName();
      if ("oil".equals(typeName) || "liquid".equals(typeName) || "aqueous".equals(typeName)) {
        return system.getPhase(phaseIndex);
      }
    }
    for (int phaseIndex = 0; phaseIndex < system.getNumberOfPhases(); phaseIndex++) {
      if (!"gas".equals(system.getPhase(phaseIndex).getPhaseTypeName())) {
        return system.getPhase(phaseIndex);
      }
    }
    return null;
  }

  /**
   * Compute molar enthalpy for a phase with system fallback.
   *
   * @param phase phase to inspect
   * @param system fallback system
   * @return molar enthalpy estimate
   */
  private double phaseMolarEnthalpy(PhaseInterface phase, SystemInterface system) {
    try {
      if (phase != null && phase.getNumberOfMolesInPhase() > MIN_FLOW) {
        return phase.getEnthalpy() / phase.getNumberOfMolesInPhase();
      }
    } catch (RuntimeException exception) {
      logger.debug("Could not read phase molar enthalpy", exception);
    }
    try {
      double moles = Math.max(MIN_FLOW, system.getTotalNumberOfMoles());
      return system.getEnthalpy() / moles;
    } catch (RuntimeException exception) {
      return 0.0;
    }
  }

  /**
   * Build the full scaled MESH residual vector.
   *
   * @return residual vector
   */
  private double[] computeResidual() {
    double[] residual = new double[totalVariables];
    for (int trayIndex = 0; trayIndex < trayCount; trayIndex++) {
      double[] trayResidual = computeResidualForTray(trayIndex);
      System.arraycopy(trayResidual, 0, residual, trayIndex * variablesPerTray, variablesPerTray);
    }
    return residual;
  }

  /**
   * Build the scaled MESH residual for one tray.
   *
   * @param trayIndex tray index
   * @return tray residual block
   */
  private double[] computeResidualForTray(int trayIndex) {
    double[] residual = new double[variablesPerTray];
    double liquidFlow = liquidFlow(trayIndex);

    for (int componentIndex = 0; componentIndex < componentCount; componentIndex++) {
      double leaving = liquidComponentFlows[trayIndex][componentIndex]
          + vaporComponentFlow(trayIndex, componentIndex);
      double entering = feedComponentFlows[trayIndex][componentIndex];
      if (trayIndex < trayCount - 1) {
        entering += liquidComponentFlows[trayIndex + 1][componentIndex];
      }
      if (trayIndex > 0) {
        entering += vaporComponentFlow(trayIndex - 1, componentIndex);
      }
      residual[componentIndex] = (leaving - entering) / flowScale;
    }

    if (Double.isNaN(fixedTemperatures[trayIndex])) {
      residual[componentCount] = energyResidualForTray(trayIndex);
    } else {
      residual[componentCount] =
          (temperatures[trayIndex] - fixedTemperatures[trayIndex]) / temperatureScale();
    }

    double sumKx = 0.0;
    for (int componentIndex = 0; componentIndex < componentCount; componentIndex++) {
      double x = liquidComponentFlows[trayIndex][componentIndex] / Math.max(MIN_FLOW, liquidFlow);
      sumKx += kValues[trayIndex][componentIndex] * x;
    }
    residual[componentCount + 1] = sumKx - 1.0;
    return residual;
  }

  /**
   * Compute a scaled energy residual for one tray.
   *
   * @param trayIndex tray index
   * @return scaled energy residual
   */
  private double energyResidualForTray(int trayIndex) {
    double outlet = liquidFlow(trayIndex) * liquidMolarEnthalpies[trayIndex]
        + vaporFlows[trayIndex] * vaporMolarEnthalpies[trayIndex];
    double inlet = feedEnthalpies[trayIndex];
    if (trayIndex < trayCount - 1) {
      inlet += liquidFlow(trayIndex + 1) * liquidMolarEnthalpies[trayIndex + 1];
    }
    if (trayIndex > 0) {
      inlet += vaporFlows[trayIndex - 1] * vaporMolarEnthalpies[trayIndex - 1];
    }
    double scale =
        Math.max(1.0, Math.abs(outlet) + Math.abs(inlet) + Math.abs(trayHeatInputs[trayIndex]));
    return (outlet - inlet - trayHeatInputs[trayIndex]) / scale;
  }

  /**
   * Compute a finite-difference Jacobian with block-tridiagonal sparsity.
   *
   * @param baseResidual residual vector at the base state
   * @return finite-difference Jacobian matrix
   */
  private double[][] computeJacobian(double[] baseResidual) {
    double[][] jacobian = new double[totalVariables][totalVariables];
    for (int trayIndex = 0; trayIndex < trayCount; trayIndex++) {
      for (int variableIndex = 0; variableIndex < variablesPerTray; variableIndex++) {
        int columnIndex = trayIndex * variablesPerTray + variableIndex;
        double originalValue = getVariable(trayIndex, variableIndex);
        double perturbation = perturbationFor(originalValue, variableIndex);
        setVariable(trayIndex, variableIndex, originalValue + perturbation);
        evaluateThermoForTray(trayIndex);

        int firstAffectedTray = Math.max(0, trayIndex - 1);
        int lastAffectedTray = Math.min(trayCount - 1, trayIndex + 1);
        for (int affectedTray =
            firstAffectedTray; affectedTray <= lastAffectedTray; affectedTray++) {
          double[] perturbed = computeResidualForTray(affectedTray);
          int rowBase = affectedTray * variablesPerTray;
          for (int equationIndex = 0; equationIndex < variablesPerTray; equationIndex++) {
            jacobian[rowBase + equationIndex][columnIndex] =
                (perturbed[equationIndex] - baseResidual[rowBase + equationIndex]) / perturbation;
          }
        }

        setVariable(trayIndex, variableIndex, originalValue);
        evaluateThermoForTray(trayIndex);
      }
    }
    for (int index = 0; index < totalVariables; index++) {
      jacobian[index][index] += JACOBIAN_REGULARIZATION;
    }
    return jacobian;
  }

  /**
   * Compute a variable perturbation.
   *
   * @param value current variable value
   * @param variableIndex variable index within tray block
   * @return finite-difference perturbation
   */
  private double perturbationFor(double value, int variableIndex) {
    double minimum = variableIndex == componentCount ? 1.0e-3 : 1.0e-8;
    return Math.max(minimum, Math.abs(value) * RELATIVE_PERTURBATION);
  }

  /**
   * Get one Newton variable.
   *
   * @param trayIndex tray index
   * @param variableIndex variable index within tray block
   * @return variable value
   */
  private double getVariable(int trayIndex, int variableIndex) {
    if (variableIndex < componentCount) {
      return liquidComponentFlows[trayIndex][variableIndex];
    }
    if (variableIndex == componentCount) {
      return temperatures[trayIndex];
    }
    return vaporFlows[trayIndex];
  }

  /**
   * Set one Newton variable with physical guards.
   *
   * @param trayIndex tray index
   * @param variableIndex variable index within tray block
   * @param value new variable value
   */
  private void setVariable(int trayIndex, int variableIndex, double value) {
    if (variableIndex < componentCount) {
      liquidComponentFlows[trayIndex][variableIndex] =
          Math.max(MIN_FLOW, finiteOr(value, MIN_FLOW));
    } else if (variableIndex == componentCount) {
      temperatures[trayIndex] = Math.max(MIN_TEMPERATURE,
          Math.min(MAX_TEMPERATURE, finiteOr(value, temperatures[trayIndex])));
    } else {
      vaporFlows[trayIndex] = Math.max(MIN_FLOW, finiteOr(value, MIN_FLOW));
    }
  }

  /**
   * Apply a trust region by scaling the correction vector in-place.
   *
   * @param correction Newton correction vector
   * @return scale factor applied to the correction
   */
  private double applyTrustRegion(double[] correction) {
    double scale = 1.0;
    for (int trayIndex = 0; trayIndex < trayCount; trayIndex++) {
      int base = trayIndex * variablesPerTray;
      for (int componentIndex = 0; componentIndex < componentCount; componentIndex++) {
        double current = Math.max(MIN_FLOW, liquidComponentFlows[trayIndex][componentIndex]);
        double maxStep = current * MAX_RELATIVE_FLOW_STEP;
        scale = Math.min(scale,
            maxStep / Math.max(maxStep, Math.abs(correction[base + componentIndex])));
      }
      scale = Math.min(scale, MAX_TEMPERATURE_STEP
          / Math.max(MAX_TEMPERATURE_STEP, Math.abs(correction[base + componentCount])));
      double vaporMaxStep = Math.max(MIN_FLOW, vaporFlows[trayIndex]) * MAX_RELATIVE_FLOW_STEP;
      scale = Math.min(scale,
          vaporMaxStep / Math.max(vaporMaxStep, Math.abs(correction[base + componentCount + 1])));
    }
    for (int index = 0; index < correction.length; index++) {
      correction[index] *= scale;
    }
    return scale;
  }

  /**
   * Backtracking line search for a Newton correction.
   *
   * @param correction trust-region-limited correction vector
   * @param currentNorm current residual norm
   * @return first residual-improving trial norm, or the current norm if no trial improved it
   */
  private double lineSearch(double[] correction, double currentNorm) {
    StateSnapshot baseState = createSnapshot();
    StateSnapshot bestState = baseState;
    double bestNorm = currentNorm;
    double[] stepLengths = new double[] {1.0, 0.5, 0.25, 0.125, 0.0625};
    for (int stepIndex = 0; stepIndex < stepLengths.length; stepIndex++) {
      restoreSnapshot(baseState);
      applyUpdate(correction, stepLengths[stepIndex]);
      evaluateAllThermo();
      double norm = vectorNorm(computeResidual());
      if (Double.isFinite(norm) && norm < bestNorm) {
        bestNorm = norm;
        bestState = createSnapshot();
        break;
      }
    }
    restoreSnapshot(bestState);
    evaluateAllThermo();
    return bestNorm;
  }

  /**
   * Apply a Newton correction to the state vector.
   *
   * @param correction correction vector
   * @param stepLength scalar step length
   */
  private void applyUpdate(double[] correction, double stepLength) {
    double temperatureMove = 0.0;
    for (int trayIndex = 0; trayIndex < trayCount; trayIndex++) {
      int base = trayIndex * variablesPerTray;
      for (int componentIndex = 0; componentIndex < componentCount; componentIndex++) {
        setVariable(trayIndex, componentIndex, liquidComponentFlows[trayIndex][componentIndex]
            + stepLength * correction[base + componentIndex]);
      }
      double oldTemperature = temperatures[trayIndex];
      setVariable(trayIndex, componentCount,
          temperatures[trayIndex] + stepLength * correction[base + componentCount]);
      temperatureMove += Math.abs(temperatures[trayIndex] - oldTemperature);
      setVariable(trayIndex, componentCount + 1,
          vaporFlows[trayIndex] + stepLength * correction[base + componentCount + 1]);
    }
    lastTemperatureResidual = temperatureMove / Math.max(1, trayCount);
  }

  /**
   * Solve the block-tridiagonal Newton system.
   *
   * @param jacobian full sparse-banded Jacobian matrix
   * @param residual residual vector
   * @return Newton correction solving J dx = -F, or null if block elimination fails
   */
  private double[] solveBlockTridiagonal(double[][] jacobian, double[] residual) {
    int blockSize = variablesPerTray;
    double[][][] lower = new double[trayCount][blockSize][blockSize];
    double[][][] diagonal = new double[trayCount][blockSize][blockSize];
    double[][][] upper = new double[trayCount][blockSize][blockSize];
    double[][] rhs = new double[trayCount][blockSize];

    for (int trayIndex = 0; trayIndex < trayCount; trayIndex++) {
      int rowBase = trayIndex * blockSize;
      for (int row = 0; row < blockSize; row++) {
        rhs[trayIndex][row] = -residual[rowBase + row];
        for (int columnIndex = 0; columnIndex < blockSize; columnIndex++) {
          diagonal[trayIndex][row][columnIndex] =
              jacobian[rowBase + row][trayIndex * blockSize + columnIndex];
          if (trayIndex > 0) {
            lower[trayIndex][row][columnIndex] =
                jacobian[rowBase + row][(trayIndex - 1) * blockSize + columnIndex];
          }
          if (trayIndex < trayCount - 1) {
            upper[trayIndex][row][columnIndex] =
                jacobian[rowBase + row][(trayIndex + 1) * blockSize + columnIndex];
          }
        }
      }
    }

    double[][][] modifiedDiagonal = new double[trayCount][blockSize][blockSize];
    double[][] modifiedRhs = new double[trayCount][blockSize];
    copyBlock(diagonal[0], modifiedDiagonal[0]);
    System.arraycopy(rhs[0], 0, modifiedRhs[0], 0, blockSize);

    for (int trayIndex = 1; trayIndex < trayCount; trayIndex++) {
      double[][] inversePrevious = invertBlock(modifiedDiagonal[trayIndex - 1]);
      if (inversePrevious == null) {
        return null;
      }
      double[][] factor = multiplyBlocks(lower[trayIndex], inversePrevious);
      double[][] factorUpper = multiplyBlocks(factor, upper[trayIndex - 1]);
      for (int row = 0; row < blockSize; row++) {
        for (int columnIndex = 0; columnIndex < blockSize; columnIndex++) {
          modifiedDiagonal[trayIndex][row][columnIndex] =
              diagonal[trayIndex][row][columnIndex] - factorUpper[row][columnIndex];
        }
      }
      double[] factorRhs = multiplyBlockVector(factor, modifiedRhs[trayIndex - 1]);
      for (int row = 0; row < blockSize; row++) {
        modifiedRhs[trayIndex][row] = rhs[trayIndex][row] - factorRhs[row];
      }
    }

    double[][] solutionBlocks = new double[trayCount][blockSize];
    double[][] inverseLast = invertBlock(modifiedDiagonal[trayCount - 1]);
    if (inverseLast == null) {
      return null;
    }
    solutionBlocks[trayCount - 1] = multiplyBlockVector(inverseLast, modifiedRhs[trayCount - 1]);
    for (int trayIndex = trayCount - 2; trayIndex >= 0; trayIndex--) {
      double[] upperContribution =
          multiplyBlockVector(upper[trayIndex], solutionBlocks[trayIndex + 1]);
      double[] adjustedRhs = new double[blockSize];
      for (int row = 0; row < blockSize; row++) {
        adjustedRhs[row] = modifiedRhs[trayIndex][row] - upperContribution[row];
      }
      double[][] inverse = invertBlock(modifiedDiagonal[trayIndex]);
      if (inverse == null) {
        return null;
      }
      solutionBlocks[trayIndex] = multiplyBlockVector(inverse, adjustedRhs);
    }

    double[] solution = new double[totalVariables];
    for (int trayIndex = 0; trayIndex < trayCount; trayIndex++) {
      System.arraycopy(solutionBlocks[trayIndex], 0, solution, trayIndex * blockSize, blockSize);
    }
    return solution;
  }

  /**
   * Copy one dense block into another.
   *
   * @param source source block
   * @param target target block
   */
  private void copyBlock(double[][] source, double[][] target) {
    for (int row = 0; row < source.length; row++) {
      System.arraycopy(source[row], 0, target[row], 0, source[row].length);
    }
  }

  /**
   * Invert a small dense block using Gauss-Jordan elimination.
   *
   * @param block block to invert
   * @return inverse block, or null when singular
   */
  private double[][] invertBlock(double[][] block) {
    int size = block.length;
    double[][] augmented = new double[size][2 * size];
    for (int row = 0; row < size; row++) {
      for (int columnIndex = 0; columnIndex < size; columnIndex++) {
        augmented[row][columnIndex] = block[row][columnIndex];
      }
      augmented[row][size + row] = 1.0;
    }
    for (int pivotIndex = 0; pivotIndex < size; pivotIndex++) {
      int pivotRow = pivotIndex;
      double pivotMagnitude = Math.abs(augmented[pivotIndex][pivotIndex]);
      for (int row = pivotIndex + 1; row < size; row++) {
        double candidate = Math.abs(augmented[row][pivotIndex]);
        if (candidate > pivotMagnitude) {
          pivotMagnitude = candidate;
          pivotRow = row;
        }
      }
      if (!Double.isFinite(pivotMagnitude) || pivotMagnitude < 1.0e-20) {
        return null;
      }
      if (pivotRow != pivotIndex) {
        double[] temp = augmented[pivotIndex];
        augmented[pivotIndex] = augmented[pivotRow];
        augmented[pivotRow] = temp;
      }
      double pivot = augmented[pivotIndex][pivotIndex];
      for (int columnIndex = 0; columnIndex < 2 * size; columnIndex++) {
        augmented[pivotIndex][columnIndex] /= pivot;
      }
      for (int row = 0; row < size; row++) {
        if (row == pivotIndex) {
          continue;
        }
        double factor = augmented[row][pivotIndex];
        for (int columnIndex = 0; columnIndex < 2 * size; columnIndex++) {
          augmented[row][columnIndex] -= factor * augmented[pivotIndex][columnIndex];
        }
      }
    }
    double[][] inverse = new double[size][size];
    for (int row = 0; row < size; row++) {
      System.arraycopy(augmented[row], size, inverse[row], 0, size);
    }
    return inverse;
  }

  /**
   * Multiply two dense blocks.
   *
   * @param left left block
   * @param right right block
   * @return matrix product
   */
  private double[][] multiplyBlocks(double[][] left, double[][] right) {
    int size = left.length;
    double[][] product = new double[size][size];
    for (int row = 0; row < size; row++) {
      for (int columnIndex = 0; columnIndex < size; columnIndex++) {
        double value = 0.0;
        for (int inner = 0; inner < size; inner++) {
          value += left[row][inner] * right[inner][columnIndex];
        }
        product[row][columnIndex] = value;
      }
    }
    return product;
  }

  /**
   * Multiply a dense block by a vector.
   *
   * @param block dense block
   * @param vector vector
   * @return product vector
   */
  private double[] multiplyBlockVector(double[][] block, double[] vector) {
    int size = vector.length;
    double[] product = new double[size];
    for (int row = 0; row < size; row++) {
      double value = 0.0;
      for (int columnIndex = 0; columnIndex < size; columnIndex++) {
        value += block[row][columnIndex] * vector[columnIndex];
      }
      product[row] = value;
    }
    return product;
  }

  /**
   * Solve a dense linear system as fallback for block elimination.
   *
   * @param jacobian coefficient matrix
   * @param residual residual vector
   * @return correction vector, or null when singular
   */
  private double[] solveDenseLinearSystem(double[][] jacobian, double[] residual) {
    int size = residual.length;
    double[][] augmented = new double[size][size + 1];
    for (int row = 0; row < size; row++) {
      for (int columnIndex = 0; columnIndex < size; columnIndex++) {
        augmented[row][columnIndex] = jacobian[row][columnIndex];
      }
      augmented[row][size] = -residual[row];
    }
    for (int pivotIndex = 0; pivotIndex < size; pivotIndex++) {
      int pivotRow = pivotIndex;
      double pivotMagnitude = Math.abs(augmented[pivotIndex][pivotIndex]);
      for (int row = pivotIndex + 1; row < size; row++) {
        double candidate = Math.abs(augmented[row][pivotIndex]);
        if (candidate > pivotMagnitude) {
          pivotMagnitude = candidate;
          pivotRow = row;
        }
      }
      if (!Double.isFinite(pivotMagnitude) || pivotMagnitude < 1.0e-20) {
        return null;
      }
      if (pivotRow != pivotIndex) {
        double[] temp = augmented[pivotIndex];
        augmented[pivotIndex] = augmented[pivotRow];
        augmented[pivotRow] = temp;
      }
      double pivot = augmented[pivotIndex][pivotIndex];
      for (int columnIndex = pivotIndex; columnIndex <= size; columnIndex++) {
        augmented[pivotIndex][columnIndex] /= pivot;
      }
      for (int row = 0; row < size; row++) {
        if (row == pivotIndex) {
          continue;
        }
        double factor = augmented[row][pivotIndex];
        for (int columnIndex = pivotIndex; columnIndex <= size; columnIndex++) {
          augmented[row][columnIndex] -= factor * augmented[pivotIndex][columnIndex];
        }
      }
    }
    double[] solution = new double[size];
    for (int row = 0; row < size; row++) {
      solution[row] = augmented[row][size];
    }
    return solution;
  }

  /** Write the accepted solver state to the column trays. */
  private void applyStateToColumn() {
    for (int trayIndex = 0; trayIndex < trayCount; trayIndex++) {
      SimpleTray tray = column.trays.get(trayIndex);
      double liquidFlow = liquidFlow(trayIndex);
      double[] x = liquidComposition(trayIndex, liquidFlow);
      double[] y = vaporComposition(trayIndex, liquidFlow);
      double[] z = overallComposition(trayIndex, liquidFlow, y);
      double totalFlow = Math.max(MIN_FLOW, liquidFlow + vaporFlows[trayIndex]);

      SystemInterface traySystem =
          createSystem(z, temperatures[trayIndex], pressures[trayIndex], totalFlow);
      flashSystemSafely(traySystem);
      tray.getOutletStream().setThermoSystem(traySystem);
      tray.getOutletStream().setCalculationIdentifier(calculationIdentifier);
      tray.setTemperature(temperatures[trayIndex]);
      tray.invalidateOutStreamCache();

      StreamInterface gasStream = createPhaseStream("naphtali gas " + trayIndex, y,
          temperatures[trayIndex], pressures[trayIndex], vaporFlows[trayIndex]);
      StreamInterface liquidStream = createPhaseStream("naphtali liquid " + trayIndex, x,
          temperatures[trayIndex], pressures[trayIndex], liquidFlow);
      tray.setCachedGasOutStream(gasStream);
      tray.setCachedLiquidOutStream(liquidStream);
    }
  }

  /**
   * Create a stream for a solved phase composition and flow.
   *
   * @param name stream name
   * @param composition molar composition
   * @param temperature temperature in Kelvin
   * @param pressure pressure in bara
   * @param flow molar flow in mol/hr
   * @return stream containing the requested phase state
   */
  private StreamInterface createPhaseStream(String name, double[] composition, double temperature,
      double pressure, double flow) {
    SystemInterface system =
        createSystem(composition, temperature, pressure, Math.max(MIN_FLOW, flow));
    system.setNumberOfPhases(1);
    system.init(0);
    system.init(1);
    Stream stream = new Stream(name, system);
    stream.setFlowRate(Math.max(0.0, flow), "mol/hr");
    stream.setCalculationIdentifier(calculationIdentifier);
    return stream;
  }

  /**
   * Create a thermodynamic system from composition and state variables.
   *
   * @param composition molar composition
   * @param temperature temperature in Kelvin
   * @param pressure pressure in bara
   * @param totalMoles total moles represented by the state
   * @return configured thermodynamic system
   */
  private SystemInterface createSystem(double[] composition, double temperature, double pressure,
      double totalMoles) {
    SystemInterface system = referenceSystem.clone();
    system.setTemperature(temperature);
    system.setPressure(pressure);
    system.setTotalNumberOfMoles(Math.max(MIN_FLOW, totalMoles / 3600.0));
    system.setMolarComposition(normalized(composition));
    system.init(0);
    return system;
  }

  /**
   * Flash a tray system without allowing failures to escape the solver.
   *
   * @param system system to flash
   */
  private void flashSystemSafely(SystemInterface system) {
    try {
      system.setNumberOfPhases(2);
      ThermodynamicOperations operations = new ThermodynamicOperations(system);
      operations.TPflash();
      system.initProperties();
    } catch (RuntimeException exception) {
      logger.debug("Naphtali-Sandholm final tray flash failed", exception);
      system.init(2);
    }
  }

  /**
   * Get liquid flow from component flows.
   *
   * @param trayIndex tray index
   * @return total liquid molar flow in mol/hr
   */
  private double liquidFlow(int trayIndex) {
    double total = 0.0;
    for (int componentIndex = 0; componentIndex < componentCount; componentIndex++) {
      total += Math.max(0.0, liquidComponentFlows[trayIndex][componentIndex]);
    }
    return Math.max(MIN_FLOW, total);
  }

  /**
   * Get a liquid mole-fraction vector.
   *
   * @param trayIndex tray index
   * @param liquidFlow total liquid molar flow
   * @return normalized liquid composition
   */
  private double[] liquidComposition(int trayIndex, double liquidFlow) {
    double[] composition = new double[componentCount];
    for (int componentIndex = 0; componentIndex < componentCount; componentIndex++) {
      composition[componentIndex] =
          liquidComponentFlows[trayIndex][componentIndex] / Math.max(MIN_FLOW, liquidFlow);
    }
    return normalized(composition);
  }

  /**
   * Get a vapor component flow from the equilibrium relation.
   *
   * @param trayIndex tray index
   * @param componentIndex component index
   * @return vapor component molar flow in mol/hr
   */
  private double vaporComponentFlow(int trayIndex, int componentIndex) {
    double liquidFlow = liquidFlow(trayIndex);
    double x = liquidComponentFlows[trayIndex][componentIndex] / Math.max(MIN_FLOW, liquidFlow);
    return vaporFlows[trayIndex] * kValues[trayIndex][componentIndex] * x;
  }

  /**
   * Get a normalized vapor composition from K-values and liquid composition.
   *
   * @param trayIndex tray index
   * @param liquidFlow total liquid molar flow
   * @return normalized vapor composition
   */
  private double[] vaporComposition(int trayIndex, double liquidFlow) {
    double[] composition = new double[componentCount];
    for (int componentIndex = 0; componentIndex < componentCount; componentIndex++) {
      double x = liquidComponentFlows[trayIndex][componentIndex] / Math.max(MIN_FLOW, liquidFlow);
      composition[componentIndex] = kValues[trayIndex][componentIndex] * x;
    }
    return normalized(composition);
  }

  /**
   * Get an overall tray composition from liquid and vapor outlets.
   *
   * @param trayIndex tray index
   * @param liquidFlow total liquid molar flow
   * @param vaporComposition normalized vapor composition
   * @return normalized overall composition
   */
  private double[] overallComposition(int trayIndex, double liquidFlow, double[] vaporComposition) {
    double[] composition = new double[componentCount];
    double total = Math.max(MIN_FLOW, liquidFlow + vaporFlows[trayIndex]);
    for (int componentIndex = 0; componentIndex < componentCount; componentIndex++) {
      composition[componentIndex] = (liquidComponentFlows[trayIndex][componentIndex]
          + vaporFlows[trayIndex] * vaporComposition[componentIndex]) / total;
    }
    return normalized(composition);
  }

  /**
   * Normalize a composition vector.
   *
   * @param values unnormalized values
   * @return normalized values
   */
  private double[] normalized(double[] values) {
    double[] normalized = new double[values.length];
    double sum = 0.0;
    for (int index = 0; index < values.length; index++) {
      normalized[index] = Math.max(0.0, finiteOr(values[index], 0.0));
      sum += normalized[index];
    }
    if (sum <= MIN_FLOW) {
      double equalFraction = values.length == 0 ? 0.0 : 1.0 / values.length;
      for (int index = 0; index < values.length; index++) {
        normalized[index] = equalFraction;
      }
      return normalized;
    }
    for (int index = 0; index < values.length; index++) {
      normalized[index] /= sum;
    }
    return normalized;
  }

  /**
   * Get a stream flow with exception handling.
   *
   * @param stream stream to inspect
   * @param unit flow unit
   * @return finite flow, or zero when unavailable
   */
  private double safeFlow(StreamInterface stream, String unit) {
    try {
      double flow = stream.getFlowRate(unit);
      return Double.isFinite(flow) ? Math.max(0.0, flow) : 0.0;
    } catch (RuntimeException exception) {
      return 0.0;
    }
  }

  /**
   * Get component flow from a stream.
   *
   * @param stream stream to inspect
   * @param componentName component name
   * @return component molar flow in mol/hr
   */
  private double componentFlow(StreamInterface stream, String componentName) {
    try {
      double flow = stream.getFluid().getComponent(componentName).getTotalFlowRate("mol/hr");
      return Double.isFinite(flow) ? Math.max(0.0, flow) : 0.0;
    } catch (RuntimeException exception) {
      return 0.0;
    }
  }

  /**
   * Get total stream enthalpy with exception handling.
   *
   * @param stream stream to inspect
   * @return stream enthalpy in the active EOS basis
   */
  private double streamEnthalpy(StreamInterface stream) {
    try {
      SystemInterface system = stream.getThermoSystem().clone();
      system.init(3);
      double enthalpy = system.getEnthalpy();
      return Double.isFinite(enthalpy) ? enthalpy : 0.0;
    } catch (RuntimeException exception) {
      return 0.0;
    }
  }

  /**
   * Compute the external mass-balance error for the accepted state.
   *
   * @return relative mass-balance error
   */
  private double computeExternalMassBalanceError() {
    double feed = 0.0;
    for (int trayIndex = 0; trayIndex < trayCount; trayIndex++) {
      for (int componentIndex = 0; componentIndex < componentCount; componentIndex++) {
        feed += feedComponentFlows[trayIndex][componentIndex];
      }
    }
    double products = vaporFlows[trayCount - 1] + liquidFlow(0);
    return Math.abs(products - feed) / Math.max(MIN_FLOW, feed);
  }

  /**
   * Compute the maximum absolute energy residual over all trays.
   *
   * @return scaled energy residual norm
   */
  private double computeEnergyResidualNorm() {
    double norm = 0.0;
    for (int trayIndex = 0; trayIndex < trayCount; trayIndex++) {
      norm = Math.max(norm, Math.abs(energyResidualForTray(trayIndex)));
    }
    return norm;
  }

  /**
   * Get the temperature scale used for fixed-temperature residuals.
   *
   * @return temperature scale in Kelvin
   */
  private double temperatureScale() {
    return Math.max(1.0, Math.abs(referenceSystem.getTemperature()));
  }

  /**
   * Compute the infinity norm of a vector.
   *
   * @param values vector values
   * @return infinity norm
   */
  private double vectorNorm(double[] values) {
    double norm = 0.0;
    for (int index = 0; index < values.length; index++) {
      if (!Double.isFinite(values[index])) {
        return Double.POSITIVE_INFINITY;
      }
      norm = Math.max(norm, Math.abs(values[index]));
    }
    return norm;
  }

  /**
   * Check if every vector entry is finite.
   *
   * @param values values to inspect
   * @return {@code true} when every entry is finite
   */
  private boolean isFiniteVector(double[] values) {
    for (int index = 0; index < values.length; index++) {
      if (!Double.isFinite(values[index])) {
        return false;
      }
    }
    return true;
  }

  /**
   * Replace a non-finite value with a fallback.
   *
   * @param value value to inspect
   * @param fallback fallback value
   * @return value when finite, otherwise fallback
   */
  private double finiteOr(double value, double fallback) {
    return Double.isFinite(value) ? value : fallback;
  }

  /**
   * Create a full snapshot of mutable solver state.
   *
   * @return state snapshot
   */
  private StateSnapshot createSnapshot() {
    return new StateSnapshot(copy(liquidComponentFlows), temperatures.clone(), vaporFlows.clone(),
        copy(kValues), liquidMolarEnthalpies.clone(), vaporMolarEnthalpies.clone(),
        lastTemperatureResidual);
  }

  /**
   * Restore a full solver state snapshot.
   *
   * @param snapshot state snapshot to restore
   */
  private void restoreSnapshot(StateSnapshot snapshot) {
    liquidComponentFlows = copy(snapshot.liquidComponentFlows);
    temperatures = snapshot.temperatures.clone();
    vaporFlows = snapshot.vaporFlows.clone();
    kValues = copy(snapshot.kValues);
    liquidMolarEnthalpies = snapshot.liquidMolarEnthalpies.clone();
    vaporMolarEnthalpies = snapshot.vaporMolarEnthalpies.clone();
    lastTemperatureResidual = snapshot.temperatureResidual;
  }

  /**
   * Copy a two-dimensional array.
   *
   * @param values values to copy
   * @return copied values
   */
  private double[][] copy(double[][] values) {
    double[][] copied = new double[values.length][];
    for (int index = 0; index < values.length; index++) {
      copied[index] = values[index].clone();
    }
    return copied;
  }

  /** Immutable snapshot of mutable solver arrays. */
  private static final class StateSnapshot {
    /** Liquid component flows. */
    private final double[][] liquidComponentFlows;
    /** Tray temperatures. */
    private final double[] temperatures;
    /** Vapor flows. */
    private final double[] vaporFlows;
    /** K-values. */
    private final double[][] kValues;
    /** Liquid molar enthalpies. */
    private final double[] liquidMolarEnthalpies;
    /** Vapor molar enthalpies. */
    private final double[] vaporMolarEnthalpies;
    /** Temperature residual. */
    private final double temperatureResidual;

    /**
     * Create a snapshot.
     *
     * @param liquidComponentFlows liquid component flows
     * @param temperatures tray temperatures
     * @param vaporFlows vapor flows
     * @param kValues K-values
     * @param liquidMolarEnthalpies liquid molar enthalpies
     * @param vaporMolarEnthalpies vapor molar enthalpies
     * @param temperatureResidual temperature residual
     */
    private StateSnapshot(double[][] liquidComponentFlows, double[] temperatures,
        double[] vaporFlows, double[][] kValues, double[] liquidMolarEnthalpies,
        double[] vaporMolarEnthalpies, double temperatureResidual) {
      this.liquidComponentFlows = liquidComponentFlows;
      this.temperatures = temperatures;
      this.vaporFlows = vaporFlows;
      this.kValues = kValues;
      this.liquidMolarEnthalpies = liquidMolarEnthalpies;
      this.vaporMolarEnthalpies = vaporMolarEnthalpies;
      this.temperatureResidual = temperatureResidual;
    }
  }
}
