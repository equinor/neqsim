package neqsim.process.safety.api14c;

import java.io.Serializable;
import java.util.Objects;

/**
 * Project evidence associating a tagged safety device with its protected equipment and, optionally, a shutdown
 * sequence. Multiple declarations can associate a shared device with different equipment or sequences.
 *
 * @author ESOL
 * @version 1.0
 */
public final class Api14cDeviceDeclaration implements Serializable {
  private static final long serialVersionUID = 1L;
  private final String equipmentTag;
  private final Api14cDeviceType type;
  private final String deviceTag;
  private final String evidenceReference;
  private final String sequenceId;

  /**
   * Declares an installed device from a project record.
   * 
   * @param equipmentTag protected equipment tag
   * @param type protective device function
   * @param deviceTag instrument or final element tag
   * @param evidenceReference source record for this declaration
   * @param sequenceId linked shutdown sequence ID, or null for an independent device
   */
  public Api14cDeviceDeclaration(String equipmentTag, Api14cDeviceType type, String deviceTag, String evidenceReference,
      String sequenceId) {
    this.equipmentTag = required(equipmentTag, "equipmentTag");
    this.type = Objects.requireNonNull(type, "type");
    this.deviceTag = required(deviceTag, "deviceTag");
    this.evidenceReference = required(evidenceReference, "evidenceReference");
    this.sequenceId = sequenceId == null ? "" : sequenceId.trim();
  }

  /**
   * @param text supplied identifier
   * @param field identifier description
   * @return trimmed nonblank value
   */
  private static String required(String text, String field) {
    if (text == null || text.trim().isEmpty()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return text.trim();
  }

  /** @return protected equipment tag */
  public String getEquipmentTag() {
    return equipmentTag;
  }

  /** @return protective device function */
  public Api14cDeviceType getType() {
    return type;
  }

  /** @return declared device tag */
  public String getDeviceTag() {
    return deviceTag;
  }

  /** @return source for the declaration */
  public String getEvidenceReference() {
    return evidenceReference;
  }

  /** @return linked sequence ID, empty when no sequence is declared */
  public String getSequenceId() {
    return sequenceId;
  }
}
