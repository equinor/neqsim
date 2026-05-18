package neqsim.process.equipment.distillation;

import java.util.Arrays;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;

/**
 * Matrix inside-out warm-start solver for distillation columns.
 *
 * <p>
 * The solver uses cached K-values and cached temperature derivatives to build one tridiagonal
 * component-balance system per component. The tridiagonal systems update liquid component traffic,
 * vapor traffic follows from cached equilibrium ratios, and tray temperatures are corrected with a
 * Newton step on the bubble-sum residual. No rigorous flash is performed inside this solver;
 * callers should finish with a rigorous column solver before accepting final products.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class DistillationColumnMatrixSolver {
  /** Logger for matrix inside-out diagnostics. */
  private static final Logger logger = LogManager.getLogger(DistillationColumnMatrixSolver.class);
  /** Smallest component or phase flow retained in matrix calculations. */
  private static final double MIN_FLOW = 1.0e-20;
  /** Smallest valid K-value retained in the cache. */
  private static final double MIN_K_VALUE = 1.0e-12;
  /** Largest valid K-value retained in the cache. */
  private static final double MAX_K_VALUE = 1.0e12;
  /** Smallest usable derivative of ln(K) with respect to temperature. */
  private static final double MIN_DLN_K_DT = 1.0e-5;
  /** Largest derivative magnitude allowed in the K-value cache. */
  private static final double MAX_ABS_DLN_K_DT = 0.25;
  /** Largest absolute temperature correction per matrix iteration in Kelvin. */
  private static final double MAX_TEMPERATURE_STEP = 8.0;
  /** Lower temperature bound in Kelvin. */
  private static final double MIN_TEMPERATURE = 50.0;
  /** Upper temperature bound in Kelvin. */
  private static final double MAX_TEMPERATURE = 1000.0;

  /** Column being solved. */
  private final DistillationColumn column;
  /** Number of trays in the column. */
  private int numberOfTrays;
  /** Number of components in the thermodynamic system. */
  private int numberOfComponents;
  /** Component names in the matrix order. */
  private String[] componentNames;
  /** Liquid phase index on each tray. */
  private int[] liquidPhaseIndices;
  /** Vapor phase index on each tray. */
  private int[] vaporPhaseIndices;

  /** Lower diagonal for the Thomas algorithm. */
  private double[] lowerDiagonal;
  /** Main diagonal for the Thomas algorithm. */
  private double[] mainDiagonal;
  /** Upper diagonal for the Thomas algorithm. */
  private double[] upperDiagonal;
  /** Right-hand side for the Thomas algorithm. */
  private double[] rightHandSide;
  /** Modified upper diagonal for the Thomas algorithm. */
  private double[] modifiedUpper;
  /** Modified right-hand side for the Thomas algorithm. */
  private double[] modifiedRightHandSide;
  /** Solution vector for the current component balance. */
  private double[] tridiagonalSolution;

  /** Cached external feed component flows indexed by tray and component. */
  private double[][] feedComponentFlows;
  /** Cached feed vapor flow by tray. */
  private double[] feedVaporFlows;
  /** Cached feed liquid flow by tray. */
  private double[] feedLiquidFlows;
  /** Cached K-values indexed by tray and component. */
  private double[][] kValues;
  /** Cached derivative of ln(K) with respect to tray temperature. */
  private double[][] dLnKdTemperature;
  /** Previous tray temperatures used for derivative updates. */
  private double[] previousTemperatures;

  /** Maximum number of matrix warm-start iterations. */
  private int maxIterations = 12;
  /** Average temperature-residual target for the matrix stage. */
  private double tolerance = 5.0e-2;
  /** Damping factor for composition, flow, and temperature updates. */
  private double dampingFactor = 0.35;
  /** Iterations used by the latest matrix stage. */
  private int lastIterationCount = 0;
  /** Latest matrix-stage average temperature residual. */
  private double lastTemperatureResidual = Double.POSITIVE_INFINITY;
  /** Latest matrix-stage wall time in seconds. */
  private double lastSolveTimeSeconds = 0.0;
  /** Whether the latest matrix stage reached its residual target. */
  private boolean lastConverged = false;

  /**
   * Construct a matrix inside-out solver for a column.
   *
   * @param column column to warm start
   */
  public DistillationColumnMatrixSolver(DistillationColumn column) {
    this.column = column;
  }

  /**
   * Set the maximum number of matrix iterations.
   *
   * @param maxIterations maximum iterations; values below one are clamped to one
   */
  public void setMaxIterations(int maxIterations) {
    this.maxIterations = Math.max(1, maxIterations);
  }

  /**
   * Set the matrix-stage temperature residual target.
   *
   * @param tolerance average tray-temperature residual target in Kelvin
   */
  public void setTolerance(double tolerance) {
    if (Double.isFinite(tolerance) && tolerance > 0.0) {
      this.tolerance = tolerance;
    }
  }

  /**
   * Set the matrix-stage damping factor.
   *
   * @param dampingFactor damping factor between 0.05 and 0.9
   */
  public void setDampingFactor(double dampingFactor) {
    if (Double.isFinite(dampingFactor)) {
      this.dampingFactor = Math.max(0.05, Math.min(0.9, dampingFactor));
    }
  }

  /**
   * Get the iteration count from the latest matrix solve.
   *
   * @return matrix iteration count
   */
  public int getLastIterationCount() {
    return lastIterationCount;
  }

  /**
   * Get the latest matrix-stage temperature residual.
   *
   * @return average tray-temperature residual in Kelvin
   */
  public double getLastTemperatureResidual() {
    return lastTemperatureResidual;
  }

  /**
   * Get the latest matrix-stage wall time.
   *
   * @return solve time in seconds
   */
  public double getLastSolveTimeSeconds() {
    return lastSolveTimeSeconds;
  }

  /**
   * Check whether the latest matrix stage reached its residual target.
   *
   * @return {@code true} if the matrix residual target was reached
   */
  public boolean hasConverged() {
    return lastConverged;
  }

  /**
   * Run matrix inside-out iterations and update tray traffic estimates.
   *
   * @param id calculation identifier assigned to generated outlet streams
   * @return {@code true} when the matrix state is finite and suitable as a warm start
   */
  public boolean solve(UUID id) {
    long startTime = System.nanoTime();
    resetResultMetrics();
    if (!initialise()) {
      lastSolveTimeSeconds = (System.nanoTime() - startTime) / 1.0e9;
      return false;
    }

    double previousResidual = Double.POSITIVE_INFINITY;
    boolean accepted = false;
    for (int iteration = 1; iteration <= maxIterations; iteration++) {
      updatePhaseIndices();
      updateKValueCache();

      double[][] liquidComponentFlows = solveComponentBalances();
      if (liquidComponentFlows == null) {
        break;
      }

      double residual = updateColumnState(liquidComponentFlows, id);
      if (!Double.isFinite(residual)) {
        break;
      }

      lastIterationCount = iteration;
      lastTemperatureResidual = residual;
      column.setError(residual);
      accepted = true;

      if (residual <= tolerance) {
        lastConverged = true;
        break;
      }

      updateDamping(residual, previousResidual);
      previousResidual = residual;
    }

    updateColumnProducts(id);
    lastSolveTimeSeconds = (System.nanoTime() - startTime) / 1.0e9;
    logger.debug("Matrix inside-out warm start iterations={} residual={} converged={}",
        lastIterationCount, lastTemperatureResidual, lastConverged);
    return accepted && Double.isFinite(lastTemperatureResidual);
  }

  /** Reset metrics from the previous matrix solve. */
  private void resetResultMetrics() {
    lastIterationCount = 0;
    lastTemperatureResidual = Double.POSITIVE_INFINITY;
    lastSolveTimeSeconds = 0.0;
    lastConverged = false;
  }

  /**
   * Initialise dimensions, caches, and external feed terms.
   *
   * @return {@code true} if matrix arrays could be initialised
   */
  private boolean initialise() {
    numberOfTrays = column.trays.size();
    if (numberOfTrays < 2) {
      return false;
    }

    SystemInterface referenceSystem = findReferenceSystem();
    if (referenceSystem == null || referenceSystem.getNumberOfComponents() == 0) {
      return false;
    }
    numberOfComponents = referenceSystem.getNumberOfComponents();
    componentNames = referenceSystem.getComponentNames();

    liquidPhaseIndices = new int[numberOfTrays];
    vaporPhaseIndices = new int[numberOfTrays];
    lowerDiagonal = new double[numberOfTrays];
    mainDiagonal = new double[numberOfTrays];
    upperDiagonal = new double[numberOfTrays];
    rightHandSide = new double[numberOfTrays];
    modifiedUpper = new double[numberOfTrays];
    modifiedRightHandSide = new double[numberOfTrays];
    tridiagonalSolution = new double[numberOfTrays];
    feedComponentFlows = new double[numberOfTrays][numberOfComponents];
    feedVaporFlows = new double[numberOfTrays];
    feedLiquidFlows = new double[numberOfTrays];
    kValues = new double[numberOfTrays][numberOfComponents];
    dLnKdTemperature = new double[numberOfTrays][numberOfComponents];
    previousTemperatures = new double[numberOfTrays];

    for (int trayIndex = 0; trayIndex < numberOfTrays; trayIndex++) {
      Arrays.fill(kValues[trayIndex], 1.0);
      Arrays.fill(dLnKdTemperature[trayIndex], 2.0e-2);
      previousTemperatures[trayIndex] = Double.NaN;
    }

    cacheFeedTerms();
    return true;
  }

  /**
   * Find a tray thermodynamic system with component definitions.
   *
   * @return reference thermodynamic system, or {@code null} if none exists
   */
  private SystemInterface findReferenceSystem() {
    for (int trayIndex = 0; trayIndex < column.trays.size(); trayIndex++) {
      SystemInterface system = column.trays.get(trayIndex).getThermoSystem();
      if (system != null && system.getNumberOfComponents() > 0) {
        return system;
      }
    }
    return null;
  }

  /** Cache all external feed terms used by the component-balance equations. */
  private void cacheFeedTerms() {
    for (int trayIndex = 0; trayIndex < numberOfTrays; trayIndex++) {
      feedVaporFlows[trayIndex] = getFeedVaporFlow(trayIndex);
      feedLiquidFlows[trayIndex] = getFeedLiquidFlow(trayIndex);
      for (int componentIndex = 0; componentIndex < numberOfComponents; componentIndex++) {
        feedComponentFlows[trayIndex][componentIndex] =
            getFeedComponentFlow(trayIndex, componentIndex);
      }
    }
  }

  /** Update phase-index caches for all trays. */
  private void updatePhaseIndices() {
    for (int trayIndex = 0; trayIndex < numberOfTrays; trayIndex++) {
      SystemInterface system = column.trays.get(trayIndex).getThermoSystem();
      vaporPhaseIndices[trayIndex] = findPhaseIndex(system, "gas");
      liquidPhaseIndices[trayIndex] = findLiquidPhaseIndex(system);
    }
  }

  /** Update cached K-values and ln(K)-temperature derivatives from current tray states. */
  private void updateKValueCache() {
    for (int trayIndex = 0; trayIndex < numberOfTrays; trayIndex++) {
      SystemInterface system = column.trays.get(trayIndex).getThermoSystem();
      double temperature = system.getTemperature();
      double previousTemperature = previousTemperatures[trayIndex];
      for (int componentIndex = 0; componentIndex < numberOfComponents; componentIndex++) {
        double oldK = kValues[trayIndex][componentIndex];
        double newK = readKValue(system, trayIndex, componentIndex, oldK);
        kValues[trayIndex][componentIndex] = newK;

        if (Double.isFinite(previousTemperature)
            && Math.abs(temperature - previousTemperature) > 1.0e-6 && oldK > MIN_K_VALUE) {
          double derivative =
              (Math.log(newK) - Math.log(oldK)) / (temperature - previousTemperature);
          if (Double.isFinite(derivative) && Math.abs(derivative) <= MAX_ABS_DLN_K_DT
              && Math.abs(derivative) >= MIN_DLN_K_DT) {
            dLnKdTemperature[trayIndex][componentIndex] = derivative;
          }
        }

        if (!Double.isFinite(dLnKdTemperature[trayIndex][componentIndex])
            || Math.abs(dLnKdTemperature[trayIndex][componentIndex]) < MIN_DLN_K_DT) {
          dLnKdTemperature[trayIndex][componentIndex] = estimateKDerivative(newK);
        }
      }
      previousTemperatures[trayIndex] = temperature;
    }
  }

  /**
   * Solve component material balances for all components.
   *
   * @return liquid component flows indexed by tray and component, or {@code null} if singular
   */
  private double[][] solveComponentBalances() {
    double[] vaporFlows = getTrayVaporFlows();
    double[] liquidFlows = getTrayLiquidFlows();
    double[][] liquidComponentFlows = new double[numberOfTrays][numberOfComponents];

    for (int componentIndex = 0; componentIndex < numberOfComponents; componentIndex++) {
      buildComponentBalance(componentIndex, vaporFlows, liquidFlows);
      if (!solveTridiagonalSystem()) {
        return null;
      }
      for (int trayIndex = 0; trayIndex < numberOfTrays; trayIndex++) {
        double oldFlow = getLiquidComponentFlow(trayIndex, componentIndex);
        double solvedFlow = Math.max(MIN_FLOW, tridiagonalSolution[trayIndex]);
        liquidComponentFlows[trayIndex][componentIndex] = blend(solvedFlow, oldFlow, dampingFactor);
      }
    }
    return liquidComponentFlows;
  }

  /**
   * Build one component balance tridiagonal system.
   *
   * @param componentIndex component index
   * @param vaporFlows vapor traffic by tray
   * @param liquidFlows liquid traffic by tray
   */
  private void buildComponentBalance(int componentIndex, double[] vaporFlows,
      double[] liquidFlows) {
    for (int trayIndex = 0; trayIndex < numberOfTrays; trayIndex++) {
      double currentStripping =
          getStrippingFactor(trayIndex, componentIndex, vaporFlows, liquidFlows);
      double previousStripping =
          trayIndex > 0 ? getStrippingFactor(trayIndex - 1, componentIndex, vaporFlows, liquidFlows)
              : 0.0;

      lowerDiagonal[trayIndex] = trayIndex > 0 ? -previousStripping : 0.0;
      mainDiagonal[trayIndex] = 1.0 + currentStripping;
      upperDiagonal[trayIndex] = trayIndex < numberOfTrays - 1 ? -1.0 : 0.0;
      rightHandSide[trayIndex] = Math.max(0.0, feedComponentFlows[trayIndex][componentIndex]);
    }
  }

  /**
   * Solve the current tridiagonal system using the Thomas algorithm.
   *
   * @return {@code true} if the system was solved with finite pivots
   */
  private boolean solveTridiagonalSystem() {
    double pivot = mainDiagonal[0];
    if (!isUsablePivot(pivot)) {
      return false;
    }
    modifiedUpper[0] = upperDiagonal[0] / pivot;
    modifiedRightHandSide[0] = rightHandSide[0] / pivot;

    for (int row = 1; row < numberOfTrays; row++) {
      pivot = mainDiagonal[row] - lowerDiagonal[row] * modifiedUpper[row - 1];
      if (!isUsablePivot(pivot)) {
        return false;
      }
      modifiedUpper[row] = row < numberOfTrays - 1 ? upperDiagonal[row] / pivot : 0.0;
      modifiedRightHandSide[row] =
          (rightHandSide[row] - lowerDiagonal[row] * modifiedRightHandSide[row - 1]) / pivot;
    }

    tridiagonalSolution[numberOfTrays - 1] = modifiedRightHandSide[numberOfTrays - 1];
    for (int row = numberOfTrays - 2; row >= 0; row--) {
      tridiagonalSolution[row] =
          modifiedRightHandSide[row] - modifiedUpper[row] * tridiagonalSolution[row + 1];
    }
    return true;
  }

  /**
   * Update tray compositions, cached outlet streams, and temperatures from matrix flows.
   *
   * @param liquidComponentFlows matrix liquid component flows
   * @param id calculation identifier assigned to generated outlet streams
   * @return average tray-temperature residual in Kelvin
   */
  private double updateColumnState(double[][] liquidComponentFlows, UUID id) {
    double[][] vaporComponentFlows = calculateVaporComponentFlows(liquidComponentFlows);
    double temperatureResidual = 0.0;

    for (int trayIndex = 0; trayIndex < numberOfTrays; trayIndex++) {
      SimpleTray tray = column.trays.get(trayIndex);
      SystemInterface system = tray.getThermoSystem();
      double oldTemperature = system.getTemperature();
      double newTemperature = tray.isSetOutTemperature() ? oldTemperature
          : estimateUpdatedTemperature(trayIndex, oldTemperature, liquidComponentFlows[trayIndex]);
      temperatureResidual += Math.abs(newTemperature - oldTemperature);

      system.setTemperature(newTemperature);
      tray.setTemperature(newTemperature);
      updateTraySystemComposition(system, trayIndex, liquidComponentFlows[trayIndex],
          vaporComponentFlows[trayIndex]);

      StreamInterface liquidStream = createOutletStream(system, liquidPhaseIndices[trayIndex],
          liquidComponentFlows[trayIndex], id);
      StreamInterface vaporStream = createOutletStream(system, vaporPhaseIndices[trayIndex],
          vaporComponentFlows[trayIndex], id);
      tray.setCachedLiquidOutStream(liquidStream);
      tray.setCachedGasOutStream(vaporStream);
    }
    return temperatureResidual / Math.max(1, numberOfTrays);
  }

  /**
   * Calculate vapor component flows from cached stripping factors.
   *
   * @param liquidComponentFlows liquid component flows indexed by tray and component
   * @return vapor component flows indexed by tray and component
   */
  private double[][] calculateVaporComponentFlows(double[][] liquidComponentFlows) {
    double[] vaporFlows = getTrayVaporFlows();
    double[] liquidFlows = getTrayLiquidFlows();
    double[][] vaporComponentFlows = new double[numberOfTrays][numberOfComponents];

    for (int trayIndex = 0; trayIndex < numberOfTrays; trayIndex++) {
      for (int componentIndex = 0; componentIndex < numberOfComponents; componentIndex++) {
        double stripping = getStrippingFactor(trayIndex, componentIndex, vaporFlows, liquidFlows);
        double oldFlow = getVaporComponentFlow(trayIndex, componentIndex);
        double newFlow =
            Math.max(MIN_FLOW, stripping * liquidComponentFlows[trayIndex][componentIndex]);
        vaporComponentFlows[trayIndex][componentIndex] = blend(newFlow, oldFlow, dampingFactor);
      }
    }
    return vaporComponentFlows;
  }

  /**
   * Estimate a tray temperature update from the cached K-value derivative model.
   *
   * @param trayIndex tray index
   * @param oldTemperature current tray temperature in Kelvin
   * @param liquidComponentFlows liquid component flows on the tray
   * @return updated tray temperature in Kelvin
   */
  private double estimateUpdatedTemperature(int trayIndex, double oldTemperature,
      double[] liquidComponentFlows) {
    double totalLiquid = sum(liquidComponentFlows);
    if (totalLiquid <= MIN_FLOW) {
      return oldTemperature;
    }

    double bubbleResidual = -1.0;
    double derivative = 0.0;
    for (int componentIndex = 0; componentIndex < numberOfComponents; componentIndex++) {
      double x = liquidComponentFlows[componentIndex] / totalLiquid;
      double k = kValues[trayIndex][componentIndex];
      bubbleResidual += k * x;
      derivative += x * k * dLnKdTemperature[trayIndex][componentIndex];
    }

    if (!Double.isFinite(derivative) || Math.abs(derivative) < MIN_DLN_K_DT) {
      derivative = Math.max(MIN_DLN_K_DT, (bubbleResidual + 1.0) * 2.0e-2);
    }

    double step = -bubbleResidual / derivative;
    step = Math.max(-MAX_TEMPERATURE_STEP, Math.min(MAX_TEMPERATURE_STEP, step));
    return Math.max(MIN_TEMPERATURE,
        Math.min(MAX_TEMPERATURE, oldTemperature + dampingFactor * step));
  }

  /**
   * Update the tray thermodynamic system composition for diagnostics and later K-cache updates.
   *
   * @param system tray thermodynamic system
   * @param trayIndex tray index
   * @param liquidComponentFlows liquid component flows
   * @param vaporComponentFlows vapor component flows
   */
  private void updateTraySystemComposition(SystemInterface system, int trayIndex,
      double[] liquidComponentFlows, double[] vaporComponentFlows) {
    int liquidPhaseIndex = liquidPhaseIndices[trayIndex];
    int vaporPhaseIndex = vaporPhaseIndices[trayIndex];
    double totalLiquid = sum(liquidComponentFlows);
    double totalVapor = sum(vaporComponentFlows);
    double totalFlow = totalLiquid + totalVapor;

    for (int componentIndex = 0; componentIndex < numberOfComponents; componentIndex++) {
      double liquidFlow = liquidComponentFlows[componentIndex];
      double vaporFlow = vaporComponentFlows[componentIndex];
      double componentTotal = liquidFlow + vaporFlow;
      system.getComponent(componentIndex).setNumberOfmoles(componentTotal);
      system.getComponent(componentIndex)
          .setz(totalFlow > MIN_FLOW ? componentTotal / totalFlow : 0.0);
      if (liquidPhaseIndex >= 0) {
        setPhaseComponent(system, liquidPhaseIndex, componentIndex, liquidFlow, totalLiquid);
      }
      if (vaporPhaseIndex >= 0) {
        setPhaseComponent(system, vaporPhaseIndex, componentIndex, vaporFlow, totalVapor);
      }
    }
    system.setTotalNumberOfMoles(Math.max(0.0, totalFlow));
    try {
      system.init(0);
      system.init(1);
    } catch (RuntimeException exception) {
      logger.debug("Matrix inside-out tray system init failed on tray {}", trayIndex, exception);
    }
  }

  /**
   * Set one component amount and mole fraction in a phase.
   *
   * @param system thermodynamic system
   * @param phaseIndex phase index
   * @param componentIndex component index
   * @param componentFlow component flow assigned to the phase
   * @param phaseFlow total phase flow
   */
  private void setPhaseComponent(SystemInterface system, int phaseIndex, int componentIndex,
      double componentFlow, double phaseFlow) {
    double boundedFlow = Math.max(0.0, componentFlow);
    system.getPhase(phaseIndex).getComponent(componentIndex).setNumberOfMolesInPhase(boundedFlow);
    system.getPhase(phaseIndex).getComponent(componentIndex).setNumberOfmoles(boundedFlow);
    system.getPhase(phaseIndex).getComponent(componentIndex)
        .setx(phaseFlow > MIN_FLOW ? boundedFlow / phaseFlow : 0.0);
  }

  /**
   * Create an outlet stream from a component-flow vector.
   *
   * @param template tray system used as a thermodynamic template
   * @param phaseIndex preferred phase index in the template
   * @param componentFlows component flows assigned to the outlet
   * @param id calculation identifier assigned to the stream
   * @return outlet stream for the matrix-updated phase
   */
  private StreamInterface createOutletStream(SystemInterface template, int phaseIndex,
      double[] componentFlows, UUID id) {
    SystemInterface outletSystem = createOutletSystemTemplate(template, phaseIndex);
    double totalFlow = sum(componentFlows);
    outletSystem.setNumberOfPhases(1);
    for (int componentIndex = 0; componentIndex < numberOfComponents; componentIndex++) {
      double componentFlow = Math.max(0.0, componentFlows[componentIndex]);
      outletSystem.getComponent(componentIndex).setNumberOfmoles(componentFlow);
      outletSystem.getComponent(componentIndex)
          .setz(totalFlow > MIN_FLOW ? componentFlow / totalFlow : 0.0);
      outletSystem.getPhase(0).getComponent(componentIndex).setNumberOfMolesInPhase(componentFlow);
      outletSystem.getPhase(0).getComponent(componentIndex).setNumberOfmoles(componentFlow);
      outletSystem.getPhase(0).getComponent(componentIndex)
          .setx(totalFlow > MIN_FLOW ? componentFlow / totalFlow : 0.0);
    }
    outletSystem.setTotalNumberOfMoles(Math.max(0.0, totalFlow));
    outletSystem.setTemperature(template.getTemperature());
    outletSystem.setPressure(template.getPressure());
    try {
      outletSystem.init(0);
      outletSystem.init(1);
    } catch (RuntimeException exception) {
      logger.debug("Matrix inside-out outlet init failed", exception);
    }
    StreamInterface stream = new Stream("", outletSystem);
    stream.setCalculationIdentifier(id);
    return stream;
  }

  /**
   * Create a single-phase outlet template from a tray phase when possible.
   *
   * @param template tray thermodynamic system
   * @param phaseIndex preferred phase index
   * @return cloned outlet thermodynamic system
   */
  private SystemInterface createOutletSystemTemplate(SystemInterface template, int phaseIndex) {
    if (phaseIndex >= 0) {
      try {
        return template.phaseToSystem(phaseIndex);
      } catch (RuntimeException exception) {
        logger.debug("Matrix inside-out phase extraction failed", exception);
      }
    }
    return template.clone();
  }

  /**
   * Update public column product streams after a matrix stage.
   *
   * @param id calculation identifier assigned to the products
   */
  private void updateColumnProducts(UUID id) {
    if (numberOfTrays <= 0) {
      return;
    }
    column.gasOutStream.setThermoSystem(
        column.trays.get(numberOfTrays - 1).getGasOutStream().getThermoSystem().clone());
    column.liquidOutStream
        .setThermoSystem(column.trays.get(0).getLiquidOutStream().getThermoSystem().clone());
    column.gasOutStream.setCalculationIdentifier(id);
    column.liquidOutStream.setCalculationIdentifier(id);
  }

  /**
   * Read a K-value from the tray state, falling back to previous cached values when necessary.
   *
   * @param system tray thermodynamic system
   * @param trayIndex tray index
   * @param componentIndex component index
   * @param fallbackValue fallback K-value
   * @return bounded K-value
   */
  private double readKValue(SystemInterface system, int trayIndex, int componentIndex,
      double fallbackValue) {
    int vaporPhaseIndex = vaporPhaseIndices[trayIndex];
    int liquidPhaseIndex = liquidPhaseIndices[trayIndex];
    if (vaporPhaseIndex >= 0 && liquidPhaseIndex >= 0) {
      double x = system.getPhase(liquidPhaseIndex).getComponent(componentIndex).getx();
      double y = system.getPhase(vaporPhaseIndex).getComponent(componentIndex).getx();
      if (x > MIN_FLOW && y >= 0.0) {
        return boundKValue(y / x);
      }
    }
    double kValue = system.getComponent(componentIndex).getK();
    if (Double.isFinite(kValue) && kValue > 0.0) {
      return boundKValue(kValue);
    }
    return boundKValue(fallbackValue);
  }

  /**
   * Estimate a default K-value derivative for a component.
   *
   * @param kValue current K-value
   * @return derivative of ln(K) with respect to temperature
   */
  private double estimateKDerivative(double kValue) {
    double volatilityWeight = 1.0 / (1.0 + Math.abs(Math.log(boundKValue(kValue))));
    return Math.max(MIN_DLN_K_DT, Math.min(MAX_ABS_DLN_K_DT, 2.0e-2 * volatilityWeight));
  }

  /**
   * Calculate a stripping factor for a component on a tray.
   *
   * @param trayIndex tray index
   * @param componentIndex component index
   * @param vaporFlows vapor traffic by tray
   * @param liquidFlows liquid traffic by tray
   * @return stripping factor K V/L
   */
  private double getStrippingFactor(int trayIndex, int componentIndex, double[] vaporFlows,
      double[] liquidFlows) {
    double liquidFlow = Math.max(MIN_FLOW, liquidFlows[trayIndex]);
    double strippingFactor =
        kValues[trayIndex][componentIndex] * Math.max(0.0, vaporFlows[trayIndex]) / liquidFlow;
    return Double.isFinite(strippingFactor) ? Math.max(0.0, strippingFactor) : 0.0;
  }

  /**
   * Get current tray vapor traffic.
   *
   * @return vapor traffic by tray
   */
  private double[] getTrayVaporFlows() {
    double[] vaporFlows = new double[numberOfTrays];
    for (int trayIndex = 0; trayIndex < numberOfTrays; trayIndex++) {
      int vaporPhaseIndex = vaporPhaseIndices[trayIndex];
      if (vaporPhaseIndex >= 0) {
        vaporFlows[trayIndex] = Math.max(MIN_FLOW, column.trays.get(trayIndex).getThermoSystem()
            .getPhase(vaporPhaseIndex).getNumberOfMolesInPhase());
      } else {
        vaporFlows[trayIndex] = Math.max(MIN_FLOW, feedVaporFlows[trayIndex]);
      }
    }
    return vaporFlows;
  }

  /**
   * Get current tray liquid traffic.
   *
   * @return liquid traffic by tray
   */
  private double[] getTrayLiquidFlows() {
    double[] liquidFlows = new double[numberOfTrays];
    for (int trayIndex = 0; trayIndex < numberOfTrays; trayIndex++) {
      int liquidPhaseIndex = liquidPhaseIndices[trayIndex];
      if (liquidPhaseIndex >= 0) {
        liquidFlows[trayIndex] = Math.max(MIN_FLOW, column.trays.get(trayIndex).getThermoSystem()
            .getPhase(liquidPhaseIndex).getNumberOfMolesInPhase());
      } else {
        liquidFlows[trayIndex] = Math.max(MIN_FLOW, feedLiquidFlows[trayIndex]);
      }
    }
    return liquidFlows;
  }

  /**
   * Get current liquid component flow from a tray phase.
   *
   * @param trayIndex tray index
   * @param componentIndex component index
   * @return liquid component flow
   */
  private double getLiquidComponentFlow(int trayIndex, int componentIndex) {
    int liquidPhaseIndex = liquidPhaseIndices[trayIndex];
    if (liquidPhaseIndex < 0) {
      return MIN_FLOW;
    }
    return Math.max(MIN_FLOW, column.trays.get(trayIndex).getThermoSystem()
        .getPhase(liquidPhaseIndex).getComponent(componentIndex).getNumberOfMolesInPhase());
  }

  /**
   * Get current vapor component flow from a tray phase.
   *
   * @param trayIndex tray index
   * @param componentIndex component index
   * @return vapor component flow
   */
  private double getVaporComponentFlow(int trayIndex, int componentIndex) {
    int vaporPhaseIndex = vaporPhaseIndices[trayIndex];
    if (vaporPhaseIndex < 0) {
      return MIN_FLOW;
    }
    return Math.max(MIN_FLOW, column.trays.get(trayIndex).getThermoSystem()
        .getPhase(vaporPhaseIndex).getComponent(componentIndex).getNumberOfMolesInPhase());
  }

  /**
   * Get vapor flow in external feeds to one tray.
   *
   * @param trayIndex tray index
   * @return vapor feed flow in mol/sec
   */
  private double getFeedVaporFlow(int trayIndex) {
    double vaporFlow = 0.0;
    for (StreamInterface feed : column.getFeedStreams(trayIndex)) {
      SystemInterface feedSystem = feed.getThermoSystem();
      if (feedSystem != null && feedSystem.hasPhaseType("gas")) {
        vaporFlow += feedSystem.getPhase("gas").getFlowRate("mole/sec");
      }
    }
    return Math.max(0.0, vaporFlow);
  }

  /**
   * Get liquid flow in external feeds to one tray.
   *
   * @param trayIndex tray index
   * @return liquid feed flow in mol/sec
   */
  private double getFeedLiquidFlow(int trayIndex) {
    double liquidFlow = 0.0;
    for (StreamInterface feed : column.getFeedStreams(trayIndex)) {
      SystemInterface feedSystem = feed.getThermoSystem();
      if (feedSystem == null) {
        continue;
      }
      double totalFlow = feedSystem.getFlowRate("mole/sec");
      if (feedSystem.hasPhaseType("gas")) {
        totalFlow -= feedSystem.getPhase("gas").getFlowRate("mole/sec");
      }
      if (Double.isFinite(totalFlow) && totalFlow > 0.0) {
        liquidFlow += totalFlow;
      }
    }
    return Math.max(0.0, liquidFlow);
  }

  /**
   * Get one component flow in external feeds to a tray.
   *
   * @param trayIndex tray index
   * @param componentIndex component index
   * @return component flow in mol/sec
   */
  private double getFeedComponentFlow(int trayIndex, int componentIndex) {
    double componentFlow = 0.0;
    String componentName = componentNames[componentIndex];
    for (StreamInterface feed : column.getFeedStreams(trayIndex)) {
      SystemInterface feedSystem = feed.getThermoSystem();
      if (feedSystem == null || feedSystem.getComponent(componentName) == null) {
        continue;
      }
      double flow = feedSystem.getComponent(componentName).getFlowRate("mole/sec");
      if (Double.isFinite(flow) && flow > 0.0) {
        componentFlow += flow;
      }
    }
    return componentFlow;
  }

  /**
   * Find the phase index for a named phase type.
   *
   * @param system thermodynamic system
   * @param phaseTypeName phase type name to find
   * @return phase index, or {@code -1} if absent
   */
  private int findPhaseIndex(SystemInterface system, String phaseTypeName) {
    int numberOfPhases = Math.max(1, system.getNumberOfPhases());
    for (int phaseIndex = 0; phaseIndex < numberOfPhases; phaseIndex++) {
      if (phaseTypeName.equals(system.getPhase(phaseIndex).getPhaseTypeName())) {
        return phaseIndex;
      }
    }
    return -1;
  }

  /**
   * Find a liquid-like phase index.
   *
   * @param system thermodynamic system
   * @return liquid phase index, or {@code -1} if absent
   */
  private int findLiquidPhaseIndex(SystemInterface system) {
    int numberOfPhases = Math.max(1, system.getNumberOfPhases());
    for (int phaseIndex = 0; phaseIndex < numberOfPhases; phaseIndex++) {
      String phaseTypeName = system.getPhase(phaseIndex).getPhaseTypeName();
      if ("liquid".equals(phaseTypeName) || "oil".equals(phaseTypeName)
          || "aqueous".equals(phaseTypeName)) {
        return phaseIndex;
      }
    }
    for (int phaseIndex = 0; phaseIndex < numberOfPhases; phaseIndex++) {
      if (!"gas".equals(system.getPhase(phaseIndex).getPhaseTypeName())) {
        return phaseIndex;
      }
    }
    return -1;
  }

  /**
   * Adapt the damping factor based on residual progress.
   *
   * @param residual current residual
   * @param previousResidual previous residual
   */
  private void updateDamping(double residual, double previousResidual) {
    if (!Double.isFinite(previousResidual)) {
      return;
    }
    if (residual > previousResidual * 1.10) {
      dampingFactor = Math.max(0.15, dampingFactor * 0.6);
    } else if (residual < previousResidual * 0.75) {
      dampingFactor = Math.min(0.75, dampingFactor * 1.15);
    }
  }

  /**
   * Check whether a tridiagonal pivot is usable.
   *
   * @param pivot candidate pivot
   * @return {@code true} if the pivot is finite and nonzero
   */
  private boolean isUsablePivot(double pivot) {
    return Double.isFinite(pivot) && Math.abs(pivot) > 1.0e-14;
  }

  /**
   * Bound a K-value to finite positive limits.
   *
   * @param kValue candidate K-value
   * @return bounded K-value
   */
  private double boundKValue(double kValue) {
    if (!Double.isFinite(kValue) || kValue <= 0.0) {
      return 1.0;
    }
    return Math.max(MIN_K_VALUE, Math.min(MAX_K_VALUE, kValue));
  }

  /**
   * Blend a new value with an old value.
   *
   * @param newValue new value
   * @param oldValue old value
   * @param weight weight on the new value
   * @return blended value
   */
  private double blend(double newValue, double oldValue, double weight) {
    if (!Double.isFinite(newValue)) {
      return Double.isFinite(oldValue) ? oldValue : 0.0;
    }
    if (!Double.isFinite(oldValue)) {
      return newValue;
    }
    return weight * newValue + (1.0 - weight) * oldValue;
  }

  /**
   * Sum values in an array.
   *
   * @param values values to sum
   * @return sum of finite values
   */
  private double sum(double[] values) {
    double total = 0.0;
    for (int index = 0; index < values.length; index++) {
      if (Double.isFinite(values[index])) {
        total += values[index];
      }
    }
    return total;
  }
}
