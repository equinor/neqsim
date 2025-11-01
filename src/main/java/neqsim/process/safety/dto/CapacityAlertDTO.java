package neqsim.process.safety.dto;

import java.io.Serializable;

/**
 * Represents a validation alert indicating that a disposal unit is overloaded in a load case.
 */
public class CapacityAlertDTO implements Serializable {
  private static final long serialVersionUID = 1L;

  private final String loadCaseName;
  private final String disposalUnitName;
  private final String message;

  public CapacityAlertDTO(String loadCaseName, String disposalUnitName, String message) {
    this.loadCaseName = loadCaseName;
    this.disposalUnitName = disposalUnitName;
    this.message = message;
  }

  public String getLoadCaseName() {
    return loadCaseName;
  }

  public String getDisposalUnitName() {
    return disposalUnitName;
  }

  public String getMessage() {
    return message;
  }
}
