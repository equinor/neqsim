package neqsim.process.equipment.reactor;

import java.io.Serializable;
import neqsim.chemicalreactions.chemicalreaction.ChemicalReactionValidationStatus;

/**
 * Scientific qualification metadata for a {@link KineticReaction}.
 *
 * <p>
 * This class keeps kinetic parameters separate from their evidence. It records the public source, validation status,
 * and the temperature/pressure range for which a kinetic reaction has been qualified. A reaction model can therefore be
 * reused in process and pipeline calculations without silently treating an unvalidated parameter set as generally
 * applicable.
 * </p>
 *
 * @author NeqSim Team
 * @version 1.0
 */
public class KineticReactionQualification implements Serializable {
  private static final long serialVersionUID = 1000L;

  private final String reactionName;
  private final String sourceCitation;
  private final String sourceIdentifier;
  private final ChemicalReactionValidationStatus validationStatus;
  private final double minimumTemperatureK;
  private final double maximumTemperatureK;
  private final double minimumPressureBara;
  private final double maximumPressureBara;
  private final String limitations;

  /**
   * Create immutable kinetic-reaction qualification metadata.
   *
   * @param reactionName reaction identifier or name
   * @param sourceCitation human-readable primary-source citation
   * @param sourceIdentifier DOI, report identifier, or stable public URL
   * @param validationStatus validation status for this kinetic parameterization
   * @param minimumTemperatureK minimum qualified temperature [K]
   * @param maximumTemperatureK maximum qualified temperature [K]
   * @param minimumPressureBara minimum qualified pressure [bara]
   * @param maximumPressureBara maximum qualified pressure [bara]
   * @param limitations concise limitations or evidence boundary
   */
  public KineticReactionQualification(String reactionName, String sourceCitation, String sourceIdentifier,
      ChemicalReactionValidationStatus validationStatus, double minimumTemperatureK, double maximumTemperatureK,
      double minimumPressureBara, double maximumPressureBara, String limitations) {
    this.reactionName = requireText(reactionName, "reaction name");
    this.sourceCitation = requireText(sourceCitation, "source citation");
    this.sourceIdentifier = requireText(sourceIdentifier, "source identifier");
    if (validationStatus == null) {
      throw new IllegalArgumentException("validation status cannot be null");
    }
    validateRange(minimumTemperatureK, maximumTemperatureK, "temperature");
    validateRange(minimumPressureBara, maximumPressureBara, "pressure");
    this.validationStatus = validationStatus;
    this.minimumTemperatureK = minimumTemperatureK;
    this.maximumTemperatureK = maximumTemperatureK;
    this.minimumPressureBara = minimumPressureBara;
    this.maximumPressureBara = maximumPressureBara;
    this.limitations = limitations == null ? "" : limitations.trim();
  }

  /**
   * Check whether a thermodynamic state lies inside the declared qualification range.
   *
   * @param temperatureK temperature [K]
   * @param pressureBara absolute pressure [bara]
   * @return true when both temperature and pressure are inside the inclusive ranges
   */
  public boolean isWithinRange(double temperatureK, double pressureBara) {
    validateFinite(temperatureK, "temperature");
    validateFinite(pressureBara, "pressure");
    return temperatureK >= minimumTemperatureK && temperatureK <= maximumTemperatureK
        && pressureBara >= minimumPressureBara && pressureBara <= maximumPressureBara;
  }

  /**
   * Fail closed unless the parameterization is validated and the state is within range.
   *
   * @param temperatureK temperature [K]
   * @param pressureBara absolute pressure [bara]
   * @throws IllegalStateException when validation or range requirements are not met
   */
  public void requireValidatedAt(double temperatureK, double pressureBara) {
    if (validationStatus != ChemicalReactionValidationStatus.VALIDATED) {
      throw new IllegalStateException(
          "Kinetic reaction '" + reactionName + "' is not independently validated: " + validationStatus);
    }
    if (!isWithinRange(temperatureK, pressureBara)) {
      throw new IllegalStateException(
          "Kinetic reaction '" + reactionName + "' is outside its qualified temperature/pressure range");
    }
  }

  /** @return reaction name. */
  public String getReactionName() {
    return reactionName;
  }

  /** @return human-readable source citation. */
  public String getSourceCitation() {
    return sourceCitation;
  }

  /** @return DOI, report identifier, or stable source URL. */
  public String getSourceIdentifier() {
    return sourceIdentifier;
  }

  /** @return validation status. */
  public ChemicalReactionValidationStatus getValidationStatus() {
    return validationStatus;
  }

  /** @return minimum qualified temperature [K]. */
  public double getMinimumTemperatureK() {
    return minimumTemperatureK;
  }

  /** @return maximum qualified temperature [K]. */
  public double getMaximumTemperatureK() {
    return maximumTemperatureK;
  }

  /** @return minimum qualified pressure [bara]. */
  public double getMinimumPressureBara() {
    return minimumPressureBara;
  }

  /** @return maximum qualified pressure [bara]. */
  public double getMaximumPressureBara() {
    return maximumPressureBara;
  }

  /** @return declared model limitations. */
  public String getLimitations() {
    return limitations;
  }

  private static String requireText(String value, String name) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException(name + " cannot be empty");
    }
    return value.trim();
  }

  private static void validateRange(double minimum, double maximum, String name) {
    validateFinite(minimum, name + " minimum");
    validateFinite(maximum, name + " maximum");
    if (minimum <= 0.0 || maximum < minimum) {
      throw new IllegalArgumentException(name + " range must be positive and ordered");
    }
  }

  private static void validateFinite(double value, String name) {
    if (!Double.isFinite(value)) {
      throw new IllegalArgumentException(name + " must be finite");
    }
  }
}
