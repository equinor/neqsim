package neqsim.process.mechanicaldesign.designstandards;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable mapping from one standard edition to existing NeqSim calculations and review workflows. */
public final class StandardRequirementPack implements Serializable {
  private static final long serialVersionUID = 1000L;
  private final StandardEdition edition;
  private final String methodVersion;
  private final List<StandardRequirementCapability> capabilities;

  StandardRequirementPack(StandardType standardType, String methodVersion,
      List<StandardRequirementCapability> capabilities) {
    edition = StandardEdition.defaultEdition(standardType);
    if (methodVersion == null || methodVersion.trim().isEmpty()) {
      throw new IllegalArgumentException("methodVersion cannot be null or blank");
    }
    this.methodVersion = methodVersion.trim();
    if (capabilities == null || capabilities.isEmpty()) {
      throw new IllegalArgumentException("capabilities cannot be null or empty");
    }
    this.capabilities = Collections
        .unmodifiableList(new ArrayList<StandardRequirementCapability>(capabilities));
  }

  /** @return explicit standard and edition basis */
  public StandardEdition getEdition() {
    return edition;
  }

  /** @return version of the NeqSim mapping, independent of the publisher edition */
  public String getMethodVersion() {
    return methodVersion;
  }

  /** @return immutable mapped capabilities */
  public List<StandardRequirementCapability> getCapabilities() {
    return capabilities;
  }
}
