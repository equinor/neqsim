package neqsim.thermodynamicoperations.flashops.saturationops;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;

/**
 * Brackets the appearance or disappearance of one material phase with complete TP/VLLE flashes.
 *
 * <p>
 * Legacy bubble/dew solvers assume two interchangeable phase slots. Electrolyte systems instead have model-specific
 * aqueous roles and may contain gas, oil and aqueous phases simultaneously. This operation therefore varies one
 * intensive variable on isolated clones and delegates every state evaluation to the system's normal TP flash. It does
 * not replace phase stability or alter non-electrolyte flash paths. Reactive systems are rejected until their
 * phase-boundary contract can enforce elemental conservation and reaction residuals.
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
   * @param system non-reactive electrolyte-capable thermodynamic system
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
    if (system.isChemicalSystem()) {
      throw new IllegalArgumentException(
          "Reactive electrolyte phase boundaries require an elemental-balance contract and are not supported yet");
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
    Snapshot lower = evaluate(initialLowerBound);
    Snapshot upper = evaluate(initialUpperBound);
    if (lower.targetPresent == upper.targetPresent) {
      throw new IllegalArgumentException("Bounds do not bracket a " + targetPhase + " phase transition: lower="
          + lower.topology + ", upper=" + upper.topology);
    }

    int iterations = 0;
    while (upper.value - lower.value > absoluteTolerance && iterations < maximumIterations) {
      Snapshot middle = evaluate(0.5 * (lower.value + upper.value));
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
    retainConvergedState(retained.system);
    Diagnostics diagnostics = calculateDiagnostics(system);
    validateDiagnostics(diagnostics);

    return new ElectrolytePhaseBoundaryResult(specification, targetPhase, lower.value, upper.value, lower.targetPresent,
        retained.value, phaseFraction(system, targetPhase), iterations, flashEvaluations, lower.topology,
        upper.topology, diagnostics.materialBalanceResidual, diagnostics.normalizationResidual,
        diagnostics.aqueousChargeMolality, diagnostics.maximumIonLeakage, diagnostics.maximumLogFugacityResidual);
  }

  /** Evaluates one state on a fresh clone to protect the bracket against solver history. */
  private Snapshot evaluate(double value) {
    SystemInterface trial = system.clone();
    if (trial == null) {
      throw new IllegalStateException("Thermodynamic system could not be cloned");
    }
    setSpecification(trial, value);
    new neqsim.thermodynamicoperations.ThermodynamicOperations(trial).TPflash();
    flashEvaluations++;
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
    return new Diagnostics(maximumMaterialResidual, maximumNormalizationResidual, aqueousCharge, maximumIonLeakage,
        maximumLogFugacityResidual);
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

    private Diagnostics(double materialBalanceResidual, double normalizationResidual, double aqueousChargeMolality,
        double maximumIonLeakage, double maximumLogFugacityResidual) {
      this.materialBalanceResidual = materialBalanceResidual;
      this.normalizationResidual = normalizationResidual;
      this.aqueousChargeMolality = aqueousChargeMolality;
      this.maximumIonLeakage = maximumIonLeakage;
      this.maximumLogFugacityResidual = maximumLogFugacityResidual;
    }
  }
}
