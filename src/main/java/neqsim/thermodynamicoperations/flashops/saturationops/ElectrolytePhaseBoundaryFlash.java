package neqsim.thermodynamicoperations.flashops.saturationops;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPitzer;

/**
 * Brackets the appearance or disappearance of one material phase with complete TP/VLLE flashes.
 *
 * <p>
 * Legacy bubble/dew solvers assume two interchangeable phase slots. Electrolyte systems instead have model-specific
 * aqueous roles and may contain gas, oil and aqueous phases simultaneously. This operation therefore varies one
 * intensive variable on isolated clones and delegates every state evaluation to the system's normal TP flash. It does
 * not replace phase stability or alter non-electrolyte flash paths. Reactive systems retain the same phase-topology
 * contract and add direct elemental-balance and reaction-residual gates.
 * </p>
 */
public final class ElectrolytePhaseBoundaryFlash {
  private static final double MATERIAL_PHASE_FRACTION = 1.0e-10;
  private static final double COMPONENT_TRACE = 1.0e-20;
  private static final double MATERIAL_BALANCE_TOLERANCE = 1.0e-7;
  private static final double NORMALIZATION_TOLERANCE = 1.0e-10;
  private static final double CHARGE_TOLERANCE_MOLAL = 1.0e-8;
  private static final double ION_LEAKAGE_TOLERANCE = 1.0e-30;
  private static final double LOG_FUGACITY_TOLERANCE = 1.0e-5;
  private static final double ELEMENT_BALANCE_TOLERANCE_MOLES = 1.0e-8;
  private static final double REACTION_LOG_RESIDUAL_TOLERANCE = 2.0e-6;
  private static final int CONTINUATION_STEPS = 8;

  private final SystemInterface system;
  private final ElectrolytePhaseBoundaryResult.Specification specification;
  private final PhaseType targetPhase;
  private final double initialLowerBound;
  private final double initialUpperBound;
  private final double absoluteTolerance;
  private final int maximumIterations;
  private int flashEvaluations;

  /**
   * Creates a bracketed phase-boundary calculation.
   *
   * @param system electrolyte-capable thermodynamic system
   * @param specification pressure or temperature scan
   * @param targetPhase phase whose material appearance/disappearance is sought
   * @param lowerBound lower pressure in bara or temperature in K
   * @param upperBound upper pressure in bara or temperature in K
   * @param absoluteTolerance final maximum bracket width in the scanned units
   * @param maximumIterations maximum bisection iterations
   */
  public ElectrolytePhaseBoundaryFlash(SystemInterface system,
      ElectrolytePhaseBoundaryResult.Specification specification, PhaseType targetPhase, double lowerBound,
      double upperBound, double absoluteTolerance, int maximumIterations) {
    if (system == null || specification == null || targetPhase == null) {
      throw new IllegalArgumentException("System, specification and target phase are required");
    }
    if (!Double.isFinite(lowerBound) || !Double.isFinite(upperBound) || lowerBound <= 0.0 || upperBound <= lowerBound) {
      throw new IllegalArgumentException("Phase-boundary bounds must be finite, positive and ordered");
    }
    if (!Double.isFinite(absoluteTolerance) || absoluteTolerance <= 0.0
        || absoluteTolerance >= upperBound - lowerBound) {
      throw new IllegalArgumentException("Absolute tolerance must be positive and smaller than the bracket");
    }
    if (maximumIterations <= 0) {
      throw new IllegalArgumentException("Maximum iterations must be positive");
    }
    if (system.isChemicalSystem() && !(system instanceof SystemPitzer)) {
      throw new IllegalArgumentException("Reactive phase boundaries are currently qualified only for the Pitzer "
          + "hybrid path; electrolyte-EOS and other reactive models require conservative multiphase reaction coupling");
    }
    this.system = system;
    this.specification = specification;
    this.targetPhase = targetPhase;
    this.initialLowerBound = lowerBound;
    this.initialUpperBound = upperBound;
    this.absoluteTolerance = absoluteTolerance;
    this.maximumIterations = maximumIterations;
  }

  /**
   * Solves the bracket and leaves the supplied system at the target-present endpoint.
   *
   * @return immutable boundary and equilibrium diagnostics
   */
  public ElectrolytePhaseBoundaryResult solve() {
    Snapshot lower = evaluate(initialLowerBound, system, null);
    Snapshot upper = evaluate(initialUpperBound, system, lower.system);
    if (lower.targetPresent == upper.targetPresent) {
      throw new IllegalArgumentException("Bounds do not bracket a " + targetPhase + " phase transition: lower="
          + lower.topology + ", upper=" + upper.topology);
    }

    int iterations = 0;
    while (upper.value - lower.value > absoluteTolerance && iterations < maximumIterations) {
      SystemInterface continuationSeed = lower.targetPresent ? lower.system : upper.system;
      Snapshot middle = evaluate(0.5 * (lower.value + upper.value), system, continuationSeed);
      if (middle.targetPresent == lower.targetPresent) {
        lower = middle;
      } else {
        upper = middle;
      }
      iterations++;
    }
    if (upper.value - lower.value > absoluteTolerance) {
      throw new IllegalStateException("Phase boundary did not converge in " + maximumIterations
          + " iterations; bracket width=" + (upper.value - lower.value));
    }

    Snapshot retained = lower.targetPresent ? lower : upper;
    Diagnostics diagnostics = calculateDiagnostics(retained.system);
    validateDiagnostics(diagnostics);
    retainConvergedState(retained.system);

    return new ElectrolytePhaseBoundaryResult(specification, targetPhase, lower.value, upper.value, lower.targetPresent,
        retained.value, phaseFraction(system, targetPhase), iterations, flashEvaluations, lower.topology,
        upper.topology, diagnostics.materialBalanceResidual, diagnostics.normalizationResidual,
        diagnostics.aqueousChargeMolality, diagnostics.maximumIonLeakage, diagnostics.maximumLogFugacityResidual,
        diagnostics.maximumElementBalanceResidual, diagnostics.maximumReactionLogResidual);
  }

  /**
   * Evaluates one state from a cold feed clone, with bounded converged-endpoint fallbacks.
   *
   * <p>
   * The cold feed remains the primary seed so a prior topology cannot silently determine the result. Some hybrid EOS-GE
   * states cannot initialize finite fugacity coefficients directly after a large specification change. In that case,
   * and only in that case, the operation first retries from a defensively cloned converged target-present endpoint. If
   * the direct endpoint jump also fails, eight evenly spaced continuation flashes bridge the specification change. All
   * attempted complete TP flashes are included in the reported evaluation count.
   * </p>
   */
  private Snapshot evaluate(double value, SystemInterface primarySeed, SystemInterface fallbackSeed) {
    try {
      return evaluateFromSeed(value, primarySeed);
    } catch (RuntimeException primaryFailure) {
      if (fallbackSeed == null) {
        throw primaryFailure;
      }
      try {
        return evaluateFromSeed(value, fallbackSeed);
      } catch (RuntimeException fallbackFailure) {
        try {
          return evaluateByContinuation(value, fallbackSeed);
        } catch (RuntimeException continuationFailure) {
          continuationFailure.addSuppressed(primaryFailure);
          continuationFailure.addSuppressed(fallbackFailure);
          throw continuationFailure;
        }
      }
    }
  }

  /** Bridges one failed direct endpoint jump with a fixed number of complete TP flashes. */
  private Snapshot evaluateByContinuation(double value, SystemInterface seed) {
    double seedValue = getSpecification(seed);
    SystemInterface continuationSeed = seed;
    Snapshot snapshot = null;
    for (int step = 1; step <= CONTINUATION_STEPS; step++) {
      double intermediateValue = step == CONTINUATION_STEPS ? value
          : seedValue + (value - seedValue) * step / CONTINUATION_STEPS;
      snapshot = evaluateFromSeed(intermediateValue, continuationSeed);
      continuationSeed = snapshot.system;
    }
    return snapshot;
  }

  /** Evaluates one state on a fresh clone of the selected seed. */
  private Snapshot evaluateFromSeed(double value, SystemInterface seed) {
    SystemInterface trial = seed.clone();
    if (trial == null) {
      throw new IllegalStateException("Thermodynamic system could not be cloned");
    }
    setSpecification(trial, value);
    flashEvaluations++;
    new neqsim.thermodynamicoperations.ThermodynamicOperations(trial).TPflash();
    return new Snapshot(value, phaseFraction(trial, targetPhase) > MATERIAL_PHASE_FRACTION, topology(trial), trial);
  }

  /**
   * Transfers the already converged endpoint without repeating a history-sensitive TP flash.
   *
   * <p>
   * Active and inactive phase mappings are copied so hybrid EOS-GE creation-order roles remain complete. Phase objects
   * are cloned before insertion, preventing the result retained by the caller from sharing mutable state with a bracket
   * snapshot.
   * </p>
   *
   * @param retainedSystem successfully flashed endpoint
   */
  private void retainConvergedState(SystemInterface retainedSystem) {
    int maximumPhases = retainedSystem.getMaxNumberOfPhases();
    system.setMaxNumberOfPhases(maximumPhases);
    for (int mappingIndex = 0; mappingIndex < maximumPhases; mappingIndex++) {
      int creationOrderSlot = retainedSystem.getPhaseIndex(mappingIndex);
      PhaseInterface retainedPhase = retainedSystem.getPhase(mappingIndex);
      PhaseInterface copiedPhase = retainedPhase.clone();
      if (copiedPhase == null) {
        throw new IllegalStateException("Retained phase could not be cloned");
      }
      system.setPhase(copiedPhase, creationOrderSlot);
    }
    for (int mappingIndex = 0; mappingIndex < maximumPhases; mappingIndex++) {
      system.setPhaseIndex(mappingIndex, retainedSystem.getPhaseIndex(mappingIndex));
    }
    system.setNumberOfPhases(retainedSystem.getNumberOfPhases());
    system.setTemperature(retainedSystem.getTemperature());
    system.setPressure(retainedSystem.getPressure());
    for (int mappingIndex = 0; mappingIndex < maximumPhases; mappingIndex++) {
      system.setBeta(mappingIndex, retainedSystem.getBeta(mappingIndex));
      system.getPhase(mappingIndex).setTemperature(retainedSystem.getPhase(mappingIndex).getTemperature());
      system.getPhase(mappingIndex).setPressure(retainedSystem.getPhase(mappingIndex).getPressure());
    }
    for (int phase = 0; phase < system.getNumberOfPhases(); phase++) {
      system.setPhaseType(phase, retainedSystem.getPhase(phase).getType());
    }
  }

  /** Returns the selected intensive variable. */
  private double getSpecification(SystemInterface targetSystem) {
    return specification == ElectrolytePhaseBoundaryResult.Specification.PRESSURE ? targetSystem.getPressure()
        : targetSystem.getTemperature();
  }

  /** Applies the selected intensive variable. */
  private void setSpecification(SystemInterface targetSystem, double value) {
    if (specification == ElectrolytePhaseBoundaryResult.Specification.PRESSURE) {
      targetSystem.setPressure(value);
    } else {
      targetSystem.setTemperature(value);
    }
  }

  /** Returns the total active fraction of one phase type. */
  private static double phaseFraction(SystemInterface targetSystem, PhaseType type) {
    double fraction = 0.0;
    for (int phase = 0; phase < targetSystem.getNumberOfPhases(); phase++) {
      if (targetSystem.getPhase(phase).getType() == type) {
        fraction += targetSystem.getBeta(phase);
      }
    }
    return fraction;
  }

  /** Returns a deterministic active-phase topology label. */
  private static String topology(SystemInterface targetSystem) {
    List<String> phases = new ArrayList<String>();
    for (int phase = 0; phase < targetSystem.getNumberOfPhases(); phase++) {
      if (targetSystem.getBeta(phase) > MATERIAL_PHASE_FRACTION) {
        phases.add(targetSystem.getPhase(phase).getType().toString());
      }
    }
    Collections.sort(phases);
    return phases.toString();
  }

  /** Calculates balance, normalization, charge, ion-confinement and fugacity diagnostics. */
  private static Diagnostics calculateDiagnostics(SystemInterface targetSystem) {
    double betaSum = 0.0;
    double maximumNormalizationResidual = 0.0;
    for (int phaseIndex = 0; phaseIndex < targetSystem.getNumberOfPhases(); phaseIndex++) {
      PhaseInterface phase = targetSystem.getPhase(phaseIndex);
      double moleFractionSum = 0.0;
      for (int component = 0; component < phase.getNumberOfComponents(); component++) {
        moleFractionSum += phase.getComponent(component).getx();
      }
      maximumNormalizationResidual = Math.max(maximumNormalizationResidual, Math.abs(1.0 - moleFractionSum));
      betaSum += targetSystem.getBeta(phaseIndex);
    }
    maximumNormalizationResidual = Math.max(maximumNormalizationResidual, Math.abs(1.0 - betaSum));

    double maximumMaterialResidual = 0.0;
    double maximumLogFugacityResidual = 0.0;
    int components = targetSystem.getPhase(0).getNumberOfComponents();
    for (int component = 0; component < components; component++) {
      double calculatedOverallFraction = 0.0;
      for (int phase = 0; phase < targetSystem.getNumberOfPhases(); phase++) {
        calculatedOverallFraction += targetSystem.getBeta(phase)
            * targetSystem.getPhase(phase).getComponent(component).getx();
      }
      ComponentInterface reference = targetSystem.getPhase(0).getComponent(component);
      maximumMaterialResidual = Math.max(maximumMaterialResidual,
          Math.abs(reference.getz() - calculatedOverallFraction));

      if (!reference.isIsIon() && reference.getIonicCharge() == 0.0 && reference.getz() > COMPONENT_TRACE) {
        double minimumLogFugacity = Double.POSITIVE_INFINITY;
        double maximumLogFugacity = Double.NEGATIVE_INFINITY;
        int materialPhases = 0;
        for (int phase = 0; phase < targetSystem.getNumberOfPhases(); phase++) {
          PhaseInterface activePhase = targetSystem.getPhase(phase);
          ComponentInterface activeComponent = activePhase.getComponent(component);
          if (targetSystem.getBeta(phase) > MATERIAL_PHASE_FRACTION && activeComponent.getx() > COMPONENT_TRACE
              && Double.isFinite(activeComponent.getFugacityCoefficient())
              && activeComponent.getFugacityCoefficient() > 0.0) {
            double logFugacity = Math.log(activeComponent.getx() * activeComponent.getFugacityCoefficient());
            minimumLogFugacity = Math.min(minimumLogFugacity, logFugacity);
            maximumLogFugacity = Math.max(maximumLogFugacity, logFugacity);
            materialPhases++;
          }
        }
        if (materialPhases > 1) {
          maximumLogFugacityResidual = Math.max(maximumLogFugacityResidual, maximumLogFugacity - minimumLogFugacity);
        }
      }
    }

    double aqueousCharge = 0.0;
    double maximumIonLeakage = 0.0;
    for (int phaseIndex = 0; phaseIndex < targetSystem.getNumberOfPhases(); phaseIndex++) {
      PhaseInterface phase = targetSystem.getPhase(phaseIndex);
      for (int component = 0; component < phase.getNumberOfComponents(); component++) {
        ComponentInterface ion = phase.getComponent(component);
        if (ion.isIsIon() || ion.getIonicCharge() != 0.0) {
          if (phase.getType() == PhaseType.AQUEOUS) {
            aqueousCharge += ion.getMolality(phase) * ion.getIonicCharge();
          } else {
            maximumIonLeakage = Math.max(maximumIonLeakage, ion.getx());
          }
        }
      }
    }
    double maximumElementBalanceResidual = targetSystem.isChemicalSystem()
        ? targetSystem.getChemicalReactionOperations().getMaximumAbsoluteElementBalanceResidual()
        : 0.0;
    double maximumReactionLogResidual = targetSystem.isChemicalSystem()
        ? targetSystem.getChemicalReactionOperations().getMaximumAbsoluteReactionLogResidual()
        : 0.0;
    return new Diagnostics(maximumMaterialResidual, maximumNormalizationResidual, aqueousCharge, maximumIonLeakage,
        maximumLogFugacityResidual, maximumElementBalanceResidual, maximumReactionLogResidual);
  }

  /** Fails closed when the retained boundary state violates the electrolyte acceptance contract. */
  private static void validateDiagnostics(Diagnostics diagnostics) {
    if (!Double.isFinite(diagnostics.materialBalanceResidual)
        || diagnostics.materialBalanceResidual > MATERIAL_BALANCE_TOLERANCE) {
      throw new IllegalStateException(
          "Electrolyte phase-boundary material balance failed: " + diagnostics.materialBalanceResidual);
    }
    if (!Double.isFinite(diagnostics.normalizationResidual)
        || diagnostics.normalizationResidual > NORMALIZATION_TOLERANCE) {
      throw new IllegalStateException(
          "Electrolyte phase-boundary normalization failed: " + diagnostics.normalizationResidual);
    }
    if (!Double.isFinite(diagnostics.aqueousChargeMolality)
        || Math.abs(diagnostics.aqueousChargeMolality) > CHARGE_TOLERANCE_MOLAL) {
      throw new IllegalStateException(
          "Electrolyte phase-boundary electroneutrality failed: " + diagnostics.aqueousChargeMolality + " mol/kg");
    }
    if (!Double.isFinite(diagnostics.maximumIonLeakage) || diagnostics.maximumIonLeakage > ION_LEAKAGE_TOLERANCE) {
      throw new IllegalStateException(
          "Electrolyte phase-boundary ion confinement failed: " + diagnostics.maximumIonLeakage);
    }
    if (!Double.isFinite(diagnostics.maximumLogFugacityResidual)
        || diagnostics.maximumLogFugacityResidual > LOG_FUGACITY_TOLERANCE) {
      throw new IllegalStateException(
          "Electrolyte phase-boundary fugacity closure failed: " + diagnostics.maximumLogFugacityResidual);
    }
    if (!Double.isFinite(diagnostics.maximumElementBalanceResidual)
        || diagnostics.maximumElementBalanceResidual > ELEMENT_BALANCE_TOLERANCE_MOLES) {
      throw new IllegalStateException(
          "Electrolyte phase-boundary elemental balance failed: " + diagnostics.maximumElementBalanceResidual);
    }
    if (!Double.isFinite(diagnostics.maximumReactionLogResidual)
        || diagnostics.maximumReactionLogResidual > REACTION_LOG_RESIDUAL_TOLERANCE) {
      throw new IllegalStateException(
          "Electrolyte phase-boundary reaction closure failed: " + diagnostics.maximumReactionLogResidual);
    }
  }

  /** One isolated TP-flash classification. */
  private static final class Snapshot {
    private final double value;
    private final boolean targetPresent;
    private final String topology;
    private final SystemInterface system;

    private Snapshot(double value, boolean targetPresent, String topology, SystemInterface system) {
      this.value = value;
      this.targetPresent = targetPresent;
      this.topology = topology;
      this.system = system;
    }
  }

  /** Retained-state acceptance diagnostics. */
  private static final class Diagnostics {
    private final double materialBalanceResidual;
    private final double normalizationResidual;
    private final double aqueousChargeMolality;
    private final double maximumIonLeakage;
    private final double maximumLogFugacityResidual;
    private final double maximumElementBalanceResidual;
    private final double maximumReactionLogResidual;

    private Diagnostics(double materialBalanceResidual, double normalizationResidual, double aqueousChargeMolality,
        double maximumIonLeakage, double maximumLogFugacityResidual, double maximumElementBalanceResidual,
        double maximumReactionLogResidual) {
      this.materialBalanceResidual = materialBalanceResidual;
      this.normalizationResidual = normalizationResidual;
      this.aqueousChargeMolality = aqueousChargeMolality;
      this.maximumIonLeakage = maximumIonLeakage;
      this.maximumLogFugacityResidual = maximumLogFugacityResidual;
      this.maximumElementBalanceResidual = maximumElementBalanceResidual;
      this.maximumReactionLogResidual = maximumReactionLogResidual;
    }
  }
}
