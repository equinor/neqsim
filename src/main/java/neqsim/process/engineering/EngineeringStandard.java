package neqsim.process.engineering;

import java.io.Serializable;
import java.util.Objects;

/** A versioned standard referenced by an engineering design basis or requirement. */
public final class EngineeringStandard implements Serializable {
  private static final long serialVersionUID = 1000L;
  private final String code;
  private final String edition;
  private final String title;
  private final String application;

  /**
   * Creates a standard reference.
   *
   * @param code standard identifier
   * @param edition edition, year, or consolidated version
   * @param title standard title
   * @param application how the standard is used by the generated model
   */
  public EngineeringStandard(String code, String edition, String title, String application) {
    this.code = requireText(code, "code");
    this.edition = requireText(edition, "edition");
    this.title = requireText(title, "title");
    this.application = requireText(application, "application");
  }

  private static String requireText(String value, String name) {
    Objects.requireNonNull(value, name);
    if (value.trim().isEmpty()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
    return value;
  }

  /** @return standard code */
  public String getCode() {
    return code;
  }

  /** @return edition or consolidated version */
  public String getEdition() {
    return edition;
  }

  /** @return standard title */
  public String getTitle() {
    return title;
  }

  /** @return application within this engineering model */
  public String getApplication() {
    return application;
  }

  /** @return code and edition suitable for compact exchange metadata */
  public String getReference() {
    return code + ":" + edition;
  }
}
