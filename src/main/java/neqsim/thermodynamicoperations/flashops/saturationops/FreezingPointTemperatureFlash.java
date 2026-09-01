package neqsim.thermodynamicoperations.flashops.saturationops;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhaseSolidHelmholtzEos;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * freezingPointTemperatureFlash class.
 *
 * @author asmund
 * @version $Id: $Id
 */
public class FreezingPointTemperatureFlash extends ConstantDutyTemperatureFlash
    implements ThermodynamicConstantsInterface {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(FreezingPointTemperatureFlash.class);

  public boolean noFreezeFlash = true;
  public int Niterations = 0;
  public String name = "Frz";
  public String phaseName = "oil";
  private FreezingPointResult result = FreezingPointResult.failed(0, Double.NaN, "", "Calculation has not run.");

  /**
   * Constructor for freezingPointTemperatureFlash.
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   */
  public FreezingPointTemperatureFlash(SystemInterface system) {
    super(system);
  }

  /**
   * Constructor for freezingPointTemperatureFlash.
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   * @param Freeze a boolean
   */
  public FreezingPointTemperatureFlash(SystemInterface system, boolean Freeze) {
    super(system);
    noFreezeFlash = Freeze;
  }

  /**
   * Returns the outcome of the latest run.
   *
   * @return latest freezing-point result
   */
  public FreezingPointResult getResult() {
    return result;
  }

  /**
   * Returns the configured solid phase, including inactive phases retained in the system phase array.
   *
   * <p>
   * A fluid-only TP flash reduces {@code getNumberOfPhases()}, but the configured solid phase remains available in
   * {@code getPhases()}. Searching only active phases therefore fails during a freezing-point iteration.
   *
   * @return configured solid phase
   * @throws IllegalStateException if solid-phase checking has not been enabled
   */
  private PhaseInterface getConfiguredSolidPhase(SystemInterface targetSystem) {
    for (PhaseInterface phase : targetSystem.getPhases()) {
      if (phase != null && phase.getType() == PhaseType.SOLID) {
        return phase;
      }
    }
    throw new IllegalStateException(
        "Solid phase is not configured. Call setSolidPhaseCheck before the freezing-point flash.");
  }

  /**
   * calcFunc.
   *
   * @return a double
   */
  public double calcFunc() {
    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    PhaseInterface solidPhase = getConfiguredSolidPhase(system);
    // double deriv = 0, funkOld = 0;
    double funk = 0;
    int numbComponents = system.getPhases()[0].getNumberOfComponents();

    for (int k = 0; k < numbComponents; k++) {
      // logger.info("Checking all the components " + k);
      if (system.getPhase(0).getComponent(k).doSolidCheck()) {
        if (!(solidPhase instanceof PhaseSolidHelmholtzEos)) {
          ops.TPflash(false);
        }
        initializeCoexistingFluidPhase(system, solidPhase);
        initializeSolidPhase(system, solidPhase);
        funk = calculateLogEquilibriumResidual(system, k, solidPhase);
      }
    }
    return funk;
  }

  /** {@inheritDoc} */
  @Override
  public void run() {
    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    PhaseInterface solidPhase = getConfiguredSolidPhase(system);
    double originalTemperature = system.getTemperature();
    double maxTemperature = Double.NEGATIVE_INFINITY;
    int numbComponents = system.getPhases()[0].getNumberOfComponents();
    boolean solidForms = false;
    boolean calculationSucceeded = false;
    int totalIterations = 0;
    double controllingResidual = Double.NaN;
    String controllingComponent = "";
    String lastFailureReason = "No component was enabled for solid checking.";

    try {
      for (int componentNumber = 0; componentNumber < numbComponents; componentNumber++) {
        if (system.getPhase(0).getComponent(componentNumber).doSolidCheck()) {
          solidForms = true;
          String componentName = system.getPhase(0).getComponent(componentNumber).getComponentName();
          if (noFreezeFlash) {
            logger.info("Starting freezing-point search for {}", componentName);
          }
          system.setTemperature(originalTemperature);
          FreezingPointResult componentResult = solveComponentFreezingPoint(componentNumber, componentName);
          totalIterations += componentResult.getIterations();
          if (componentResult.isConverged()) {
            double componentTemperature = componentResult.getTemperature("K");
            if (componentTemperature > maxTemperature) {
              maxTemperature = componentTemperature;
              controllingResidual = componentResult.getResidual();
              controllingComponent = componentName;
            }
          } else {
            lastFailureReason = componentResult.getFailureReason();
          }
        }
      }

      if (solidForms && Double.isFinite(maxTemperature) && maxTemperature > 0.0) {
        system.setTemperature(maxTemperature);
        if (!(solidPhase instanceof PhaseSolidHelmholtzEos)) {
          ops.TPflash(false);
        }
        initializeCoexistingFluidPhase(system, solidPhase);
        initializeSolidPhase(system, solidPhase);
        result = FreezingPointResult.converged(maxTemperature, totalIterations, controllingResidual,
            controllingComponent);
        Niterations = totalIterations;
        calculationSucceeded = true;
        return;
      }

      Niterations = totalIterations;
      result = FreezingPointResult.failed(totalIterations, Double.NaN, controllingComponent, lastFailureReason);
      throw new IllegalStateException("Freezing-point flash did not converge. " + lastFailureReason);
    } finally {
      if (!calculationSucceeded) {
        system.setTemperature(originalTemperature);
        system.init(1);
      }
    }
  }

  /**
   * Solve the freezing point of one solid-forming component by bracket expansion and bisection.
   *
   * @param componentNumber component index
   * @param componentName component name used in diagnostics
   * @return converged or failed component result
   */
  private FreezingPointResult solveComponentFreezingPoint(int componentNumber, String componentName) {
    final double residualTolerance = 1.0e-10;
    final double temperatureTolerance = 1.0e-12;
    final int maximumBracketIterations = 50;
    final int maximumSolverIterations = 100;
    final double minimumTemperature = 0.5;
    final double maximumTemperature = 5000.0;
    double centerTemperature = system.getTemperature();
    double centerResidual = Double.NaN;
    int iterations = 0;

    try {
      centerResidual = evaluateResidualIfValid(componentNumber, centerTemperature);
      iterations++;
      if (Double.isFinite(centerResidual) && Math.abs(centerResidual) < residualTolerance) {
        return FreezingPointResult.converged(centerTemperature, iterations, centerResidual, componentName);
      }

      double lowerTemperature = Double.NaN;
      double upperTemperature = Double.NaN;
      double lowerResidual = Double.NaN;
      double upperResidual = Double.NaN;
      if (Double.isFinite(centerResidual)) {
        lowerTemperature = centerTemperature;
        upperTemperature = centerTemperature;
        lowerResidual = centerResidual;
        upperResidual = centerResidual;
      }
      double span = Math.max(0.1, centerTemperature * 0.01);
      boolean bracketed = false;
      for (int bracketIteration = 0; bracketIteration < maximumBracketIterations; bracketIteration++) {
        double candidateLowerTemperature = Math.max(minimumTemperature, centerTemperature - span);
        double candidateUpperTemperature = Math.min(maximumTemperature, centerTemperature + span);
        double candidateLowerResidual = evaluateResidualIfValid(componentNumber, candidateLowerTemperature);
        double candidateUpperResidual = evaluateResidualIfValid(componentNumber, candidateUpperTemperature);
        iterations += 2;
        if (Double.isFinite(candidateLowerResidual)) {
          lowerTemperature = candidateLowerTemperature;
          lowerResidual = candidateLowerResidual;
        }
        if (Double.isFinite(candidateUpperResidual)) {
          upperTemperature = candidateUpperTemperature;
          upperResidual = candidateUpperResidual;
        }
        if (Double.isFinite(lowerResidual) && Double.isFinite(upperResidual) && lowerResidual * upperResidual <= 0.0) {
          bracketed = true;
          break;
        }
        span *= 1.8;
      }
      if (!bracketed) {
        return FreezingPointResult.failed(iterations, centerResidual, componentName,
            "Could not bracket the freezing point of " + componentName + ".");
      }

      double trialTemperature = centerTemperature;
      double trialResidual = centerResidual;
      for (int solverIteration = 0; solverIteration < maximumSolverIterations; solverIteration++) {
        trialTemperature = 0.5 * (lowerTemperature + upperTemperature);
        trialResidual = evaluateResidualIfValid(componentNumber, trialTemperature);
        iterations++;
        if (!Double.isFinite(trialResidual)) {
          double lowerTrialTemperature = 0.75 * lowerTemperature + 0.25 * upperTemperature;
          double upperTrialTemperature = 0.25 * lowerTemperature + 0.75 * upperTemperature;
          double lowerTrialResidual = evaluateResidualIfValid(componentNumber, lowerTrialTemperature);
          double upperTrialResidual = evaluateResidualIfValid(componentNumber, upperTrialTemperature);
          iterations += 2;
          if (Double.isFinite(lowerTrialResidual) && (!Double.isFinite(upperTrialResidual)
              || Math.abs(lowerTrialResidual) <= Math.abs(upperTrialResidual))) {
            trialTemperature = lowerTrialTemperature;
            trialResidual = lowerTrialResidual;
          } else if (Double.isFinite(upperTrialResidual)) {
            trialTemperature = upperTrialTemperature;
            trialResidual = upperTrialResidual;
          } else {
            return FreezingPointResult.failed(iterations, Double.NaN, componentName,
                "No valid equilibrium state was found inside the freezing-point bracket for " + componentName + ".");
          }
        }
        if (Math.abs(trialResidual) < residualTolerance) {
          return FreezingPointResult.converged(trialTemperature, iterations, trialResidual, componentName);
        }
        if (upperTemperature - lowerTemperature < temperatureTolerance) {
          return FreezingPointResult.failed(iterations, trialResidual, componentName, "The freezing-point bracket for "
              + componentName
              + " collapsed without satisfying Gibbs equilibrium; the fluid residual is discontinuous or the requested density root is unavailable.");
        }
        if (lowerResidual * trialResidual <= 0.0) {
          upperTemperature = trialTemperature;
          upperResidual = trialResidual;
        } else {
          lowerTemperature = trialTemperature;
          lowerResidual = trialResidual;
        }
      }
      return FreezingPointResult.failed(iterations, trialResidual, componentName,
          "The bracketed freezing-point iteration for " + componentName + " did not converge.");
    } catch (RuntimeException exception) {
      return FreezingPointResult.failed(iterations, centerResidual, componentName,
          "The freezing-point evaluation for " + componentName + " failed: " + exception.getMessage());
    }
  }

  /**
   * Evaluate the solid-fluid equilibrium residual at a trial temperature.
   *
   * @param componentNumber component index
   * @param temperature trial temperature in K
   * @param solidPhase configured solid phase
   * @param ops thermodynamic operations bound to the system
   * @return dimensionless equilibrium residual
   */
  private double evaluateResidual(SystemInterface targetSystem, int componentNumber, double temperature,
      PhaseInterface solidPhase, ThermodynamicOperations ops) {
    targetSystem.setTemperature(temperature);
    if (!(solidPhase instanceof PhaseSolidHelmholtzEos)) {
      ops.TPflash(false);
    }
    initializeCoexistingFluidPhase(targetSystem, solidPhase);
    initializeSolidPhase(targetSystem, solidPhase);
    double residual = calculateLogEquilibriumResidual(targetSystem, componentNumber, solidPhase);
    if (!Double.isFinite(residual)) {
      throw new IllegalStateException("Equilibrium residual is not finite at " + temperature + " K.");
    }
    return residual;
  }

  /**
   * Explicitly initialize the fluid root that can coexist with a fundamental solid EOS.
   *
   * <p>
   * A pure-fluid TP flash can follow a metastable density root below the critical temperature. Solid-vapor equilibrium
   * requires the vapor root below the triple pressure, while solid-liquid equilibrium requires the dense root at and
   * above the triple pressure.
   * </p>
   *
   * @param targetSystem trial thermodynamic system
   * @param solidPhase configured solid phase
   */
  private void initializeCoexistingFluidPhase(SystemInterface targetSystem, PhaseInterface solidPhase) {
    if (!(solidPhase instanceof PhaseSolidHelmholtzEos)) {
      return;
    }
    PhaseSolidHelmholtzEos helmholtzSolid = (PhaseSolidHelmholtzEos) solidPhase;
    double triplePointPressure = helmholtzSolid.getSolidEquation().getTriplePointPressure();
    if (!(triplePointPressure > 0.0) || !Double.isFinite(triplePointPressure)) {
      return;
    }
    PhaseType fluidPhaseType = targetSystem.getPressure() < triplePointPressure ? PhaseType.GAS : PhaseType.LIQUID;
    PhaseInterface fluidPhase = targetSystem.getPhase(0);
    fluidPhase.init(targetSystem.getTotalNumberOfMoles(), fluidPhase.getNumberOfComponents(), 2, fluidPhaseType, 1.0);
  }

  /**
   * Evaluate a trial residual and mark thermodynamically invalid trial states as unavailable.
   *
   * @param componentNumber component index
   * @param temperature trial temperature in K
   * @return finite equilibrium residual, or {@link Double#NaN} when the trial state is invalid
   */
  private double evaluateResidualIfValid(int componentNumber, double temperature) {
    try {
      SystemInterface trialSystem = system.clone();
      PhaseInterface trialSolidPhase = getConfiguredSolidPhase(trialSystem);
      ThermodynamicOperations trialOperations = new ThermodynamicOperations(trialSystem);
      return evaluateResidual(trialSystem, componentNumber, temperature, trialSolidPhase, trialOperations);
    } catch (RuntimeException exception) {
      logger.debug("Skipping invalid freezing-point trial at {} K: {}", temperature, exception.getMessage());
      return Double.NaN;
    }
  }

  /**
   * Calculate the logarithmic form of the solid-fluid equilibrium residual.
   *
   * @param componentNumber component index
   * @param solidPhase configured and initialized solid phase
   * @return logarithm of overall composition minus the logarithm of the phase-weighted fugacity ratio
   */
  private double calculateLogEquilibriumResidual(SystemInterface targetSystem, int componentNumber,
      PhaseInterface solidPhase) {
    if (solidPhase instanceof PhaseSolidHelmholtzEos) {
      PhaseInterface fluidPhase = targetSystem.getPhase(0);
      double fluidMoles = fluidPhase.getNumberOfMolesInPhase();
      double solidMoles = solidPhase.getNumberOfMolesInPhase();
      if (!(fluidMoles > 0.0) || !(solidMoles > 0.0)) {
        throw new IllegalStateException("Pure-component Helmholtz equilibrium requires positive phase mole amounts.");
      }
      double fluidMolarGibbsEnergy = fluidPhase.getGibbsEnergy() / fluidMoles;
      double solidMolarGibbsEnergy = solidPhase.getGibbsEnergy() / solidMoles;
      return (fluidMolarGibbsEnergy - solidMolarGibbsEnergy) / (R * targetSystem.getTemperature());
    }
    double overallComposition = targetSystem.getPhase(0).getComponent(componentNumber).getz();
    if (!(overallComposition > 0.0)) {
      throw new IllegalStateException("Solid-forming component must have positive overall composition.");
    }
    solidPhase.getComponent(componentNumber).fugcoef(solidPhase);
    double solidLogFugacityCoefficient = solidPhase.getComponent(componentNumber).getLogFugacityCoefficient();
    double maximumLogTerm = Double.NEGATIVE_INFINITY;
    for (int phaseNumber = 0; phaseNumber < targetSystem.getNumberOfPhases(); phaseNumber++) {
      double beta = targetSystem.getPhase(phaseNumber).getBeta();
      if (beta > 0.0) {
        double logTerm = Math.log(beta) + solidLogFugacityCoefficient
            - targetSystem.getPhase(phaseNumber).getComponent(componentNumber).getLogFugacityCoefficient();
        maximumLogTerm = Math.max(maximumLogTerm, logTerm);
      }
    }
    if (!Double.isFinite(maximumLogTerm)) {
      throw new IllegalStateException("No active fluid phase has a finite fugacity contribution.");
    }
    double scaledSum = 0.0;
    for (int phaseNumber = 0; phaseNumber < targetSystem.getNumberOfPhases(); phaseNumber++) {
      double beta = targetSystem.getPhase(phaseNumber).getBeta();
      if (beta > 0.0) {
        double logTerm = Math.log(beta) + solidLogFugacityCoefficient
            - targetSystem.getPhase(phaseNumber).getComponent(componentNumber).getLogFugacityCoefficient();
        scaledSum += Math.exp(logTerm - maximumLogTerm);
      }
    }
    return Math.log(overallComposition) - maximumLogTerm - Math.log(scaledSum);
  }

  /**
   * Synchronize and initialize the configured solid phase at the current trial state.
   *
   * @param solidPhase configured solid phase retained outside the active fluid phase count
   */
  private void initializeSolidPhase(SystemInterface targetSystem, PhaseInterface solidPhase) {
    solidPhase.setTemperature(targetSystem.getTemperature());
    solidPhase.setPressure(targetSystem.getPressure());
    solidPhase.init(targetSystem.getTotalNumberOfMoles(), solidPhase.getNumberOfComponents(), 1, PhaseType.SOLID, 1.0);
  }

  /**
   * printToFile.
   *
   * @param name a {@link java.lang.String} object
   * @param FCompNames an array of {@link java.lang.String} objects
   * @param FCompTemp an array of type double
   */
  public void printToFile(String name, String[] FCompNames, double[] FCompTemp) {
    for (int n = 0; n < system.getPhases()[0].getNumberOfComponents(); n++) {
      name = name + "_" + system.getPhase(0).getComponent(n).getComponentName();
    }

    String myFile = "/java/" + name + ".frz";

    try (PrintWriter pr_writer = new PrintWriter(new FileWriter(myFile, true))) {
      pr_writer.println("name,freezeT,freezeP,z,iterations");
      pr_writer.flush();

      for (int k = 0; k < system.getPhases()[0].getNumberOfComponents(); k++) {
        // print line to output file
        pr_writer.println(FCompNames[k] + "," + Double.toString(FCompTemp[k]) + "," + system.getPressure() + ","
            + Double.toString(system.getPhases()[0].getComponent(k).getz()) + "," + Niterations);
        pr_writer.flush();
      }
    } catch (SecurityException ex) {
      logger.error("writeFile: caught security exception");
    } catch (IOException ioe) {
      logger.error("writeFile: caught i/o exception");
    }
  }
}
