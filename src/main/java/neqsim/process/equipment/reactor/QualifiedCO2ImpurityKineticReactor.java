package neqsim.process.equipment.reactor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import neqsim.chemicalreactions.chemicalreaction.ChemicalReactionValidationStatus;
import neqsim.process.equipment.stream.StreamInterface;

/**
 * Fail-closed qualified execution path for {@link CO2ImpurityKineticReactor}.
 *
 * <p>
 * The parent reactor retains illustrative R1-R8 constants for experimental screening. This subclass preserves that
 * implementation while requiring caller-supplied {@link KineticReactionQualification} metadata for every configured
 * reaction family before execution. It does not qualify the built-in constants or supply new kinetic evidence.
 * </p>
 *
 * @author NeqSim Team
 * @version 1.0
 */
public class QualifiedCO2ImpurityKineticReactor extends CO2ImpurityKineticReactor implements Serializable {
  private static final long serialVersionUID = 1000L;
  private static final String[] HOMOGENEOUS_REACTION_IDS = { "R1", "R2", "R3A", "R3B", "R4", "R5", "R6", "R7" };

  private final Map<String, KineticReactionQualification> qualifications = new LinkedHashMap<>();

  /**
   * Construct a qualified reactor without an inlet stream.
   *
   * @param name reactor name
   */
  public QualifiedCO2ImpurityKineticReactor(String name) {
    super(name);
  }

  /**
   * Construct a qualified reactor with an inlet stream.
   *
   * @param name reactor name
   * @param inlet inlet stream
   */
  public QualifiedCO2ImpurityKineticReactor(String name, StreamInterface inlet) {
    super(name, inlet);
  }

  /**
   * Register evidence metadata for one reaction parameterization.
   *
   * <p>
   * Supported identifiers are R1, R2, R3A, R3B, R4-R7, R8CS, R8SS, and R8. R8 resolves to the family selected by the
   * current wall material. The qualification reaction name must equal the resolved identifier so that evidence cannot
   * be silently attached to a different parameter set.
   * </p>
   *
   * @param reactionId reaction parameter identifier
   * @param qualification immutable evidence metadata
   */
  public void setReactionQualification(String reactionId, KineticReactionQualification qualification) {
    if (qualification == null) {
      throw new IllegalArgumentException("reaction qualification cannot be null");
    }
    String normalizedId = normalizeReactionId(reactionId);
    if (!normalizedId.equals(qualification.getReactionName().trim().toUpperCase(Locale.ROOT))) {
      throw new IllegalArgumentException(
          "qualification reaction name must equal resolved reaction identifier " + normalizedId);
    }
    qualifications.put(normalizedId, qualification);
  }

  /**
   * Return registered evidence for one reaction parameterization.
   *
   * @param reactionId reaction parameter identifier
   * @return registered qualification, or {@code null} when none exists
   */
  public KineticReactionQualification getReactionQualification(String reactionId) {
    return qualifications.get(normalizeReactionId(reactionId));
  }

  /**
   * Return the reaction identifiers required by the current material selection.
   *
   * @return defensive ordered copy of eight homogeneous IDs and the selected R8 family
   */
  public String[] getRequiredReactionIds() {
    String[] required = Arrays.copyOf(HOMOGENEOUS_REACTION_IDS, HOMOGENEOUS_REACTION_IDS.length + 1);
    required[required.length - 1] = selectedR8Id();
    return required;
  }

  /**
   * Evaluate every required parameterization and explain why qualified execution is allowed or blocked.
   *
   * @param temperatureK temperature [K]
   * @param pressureBara absolute pressure [bara]
   * @return immutable source-ordered qualification report
   */
  public CO2ImpurityKineticsQualificationReport getQualificationReport(double temperatureK, double pressureBara) {
    requireFinitePositive(temperatureK, "temperature");
    requireFinitePositive(pressureBara, "pressure");
    List<CO2ImpurityKineticsQualificationReport.Entry> entries = new ArrayList<>();
    for (String reactionId : getRequiredReactionIds()) {
      KineticReactionQualification qualification = qualifications.get(reactionId);
      CO2ImpurityKineticsQualificationReport.QualificationState state;
      if (qualification == null) {
        state = CO2ImpurityKineticsQualificationReport.QualificationState.MISSING;
      } else if (qualification.getValidationStatus() != ChemicalReactionValidationStatus.VALIDATED) {
        state = CO2ImpurityKineticsQualificationReport.QualificationState.NOT_VALIDATED;
      } else if (!qualification.isWithinRange(temperatureK, pressureBara)) {
        state = CO2ImpurityKineticsQualificationReport.QualificationState.OUT_OF_RANGE;
      } else {
        state = CO2ImpurityKineticsQualificationReport.QualificationState.QUALIFIED;
      }
      entries.add(new CO2ImpurityKineticsQualificationReport.Entry(reactionId, state));
    }
    return new CO2ImpurityKineticsQualificationReport(temperatureK, pressureBara, getMaterial(), entries);
  }

  /**
   * Identify missing, unvalidated, or out-of-range reaction parameterizations.
   *
   * @param temperatureK temperature [K]
   * @param pressureBara absolute pressure [bara]
   * @return ordered identifiers that cannot be used for qualified execution at the supplied state
   */
  public String[] getUnqualifiedReactionIds(double temperatureK, double pressureBara) {
    return getQualificationReport(temperatureK, pressureBara).getBlockedReactionIds();
  }

  /**
   * Fail closed unless every selected parameterization is independently validated at the supplied state.
   *
   * @param temperatureK temperature [K]
   * @param pressureBara absolute pressure [bara]
   * @throws IllegalStateException when any required parameterization is missing, unvalidated, or out of range
   */
  public void requireValidatedKineticsAt(double temperatureK, double pressureBara) {
    String[] unqualified = getUnqualifiedReactionIds(temperatureK, pressureBara);
    if (unqualified.length > 0) {
      throw new IllegalStateException(
          "CO2 impurity kinetics are not qualified at the requested state: " + String.join(", ", unqualified));
    }
  }

  /**
   * Configure Arrhenius constants and invalidate evidence bound to the replaced parameter pair.
   *
   * @param reactionId reaction identifier
   * @param preExponentialFactor pre-exponential factor, non-negative
   * @param activationEnergyKJPerMol activation energy [kJ/mol]
   */
  @Override
  public void setReactionConstants(String reactionId, double preExponentialFactor, double activationEnergyKJPerMol) {
    String normalizedId = normalizeReactionId(reactionId);
    super.setReactionConstants(reactionId, preExponentialFactor, activationEnergyKJPerMol);
    qualifications.remove(normalizedId);
  }

  /**
   * Execute only after all reaction parameterizations are qualified at the inlet state.
   *
   * @param id calculation identifier
   */
  @Override
  public void run(UUID id) {
    if (getInletStream() == null) {
      throw new IllegalStateException("qualified CO2 impurity reactor requires a connected inlet stream");
    }
    requireValidatedKineticsAt(getInletStream().getThermoSystem().getTemperature(),
        getInletStream().getThermoSystem().getPressure());
    super.run(id);
  }

  private String normalizeReactionId(String reactionId) {
    if (reactionId == null) {
      throw new IllegalArgumentException("reaction identifier cannot be null");
    }
    String normalizedId = reactionId.trim().toUpperCase(Locale.ROOT);
    if ("R8".equals(normalizedId)) {
      return selectedR8Id();
    }
    for (String supportedId : HOMOGENEOUS_REACTION_IDS) {
      if (supportedId.equals(normalizedId)) {
        return normalizedId;
      }
    }
    if ("R8CS".equals(normalizedId) || "R8SS".equals(normalizedId)) {
      return normalizedId;
    }
    throw new IllegalArgumentException("unsupported reaction identifier: " + reactionId);
  }

  private String selectedR8Id() {
    return "carbon_steel".equals(getMaterial()) || "magnetite".equals(getMaterial()) ? "R8CS" : "R8SS";
  }

  private static void requireFinitePositive(double value, String name) {
    if (!Double.isFinite(value) || value <= 0.0) {
      throw new IllegalArgumentException(name + " must be finite and positive");
    }
  }
}
