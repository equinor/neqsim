package neqsim.process.engineering.pid;

import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Project-specific tagging and proposal policy used during P&amp;ID synthesis. */
public final class PidDesignBasis implements Serializable {
  private static final long serialVersionUID = 1000L;
  private final String profileId;
  private final String areaCode;
  private int firstSequenceNumber = 1;
  private int sequenceWidth = 3;
  private boolean proposeControlFunctions = true;
  private boolean proposeSafeguarding = true;
  private final Map<String, String> attributes = new LinkedHashMap<String, String>();

  public PidDesignBasis(String profileId, String areaCode) {
    this.profileId = requireText(profileId, "profileId");
    this.areaCode = requireText(areaCode, "areaCode");
  }

  private static String requireText(String value, String name) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
    return value.trim();
  }

  public PidDesignBasis setFirstSequenceNumber(int value) {
    if (value < 1) {
      throw new IllegalArgumentException("firstSequenceNumber must be positive");
    }
    firstSequenceNumber = value;
    return this;
  }

  public PidDesignBasis setSequenceWidth(int value) {
    if (value < 2 || value > 8) {
      throw new IllegalArgumentException("sequenceWidth must be between 2 and 8");
    }
    sequenceWidth = value;
    return this;
  }

  public PidDesignBasis setProposeControlFunctions(boolean value) {
    proposeControlFunctions = value;
    return this;
  }

  public PidDesignBasis setProposeSafeguarding(boolean value) {
    proposeSafeguarding = value;
    return this;
  }

  public PidDesignBasis attribute(String name, String value) {
    attributes.put(requireText(name, "attribute name"), requireText(value, "attribute value"));
    return this;
  }

  public String getProfileId() {
    return profileId;
  }

  public String getAreaCode() {
    return areaCode;
  }

  public int getFirstSequenceNumber() {
    return firstSequenceNumber;
  }

  public int getSequenceWidth() {
    return sequenceWidth;
  }

  public boolean isProposeControlFunctions() {
    return proposeControlFunctions;
  }

  public boolean isProposeSafeguarding() {
    return proposeSafeguarding;
  }

  public Map<String, String> getAttributes() {
    return Collections.unmodifiableMap(attributes);
  }
}
