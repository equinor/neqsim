package neqsim.process.processmodel.dexpi;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable source evidence for one DEXPI instrumentation-loop grouping occurrence.
 *
 * <p>
 * The record preserves explicit source-document membership without constructing live controllers, inferring control
 * intent, verifying loop function, or classifying safeguards.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public final class DexpiInstrumentationLoopInfo implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Immutable evidence for one source loop-membership occurrence. */
  public static final class Member implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String memberId;
    private final boolean resolved;
    private final String elementName;
    private final String memberComponentClass;
    private final String memberComponentName;
    private final String memberTagName;

    /**
     * Creates evidence for one source loop-membership occurrence.
     *
     * @param memberId referenced source identity, or an empty string when absent
     * @param resolved whether the reference resolves to a source element
     * @param elementName resolved XML element name, or an empty string
     */
    public Member(String memberId, boolean resolved, String elementName) {
      this(memberId, resolved, elementName, "", "", "");
    }

    /**
     * Creates evidence with explicit metadata from the resolved member element.
     *
     * @param memberId referenced source identity, or an empty string when absent
     * @param resolved whether the reference resolves to a source element
     * @param elementName resolved XML element name, or an empty string
     * @param memberComponentClass explicit member component class, or an empty string
     * @param memberComponentName explicit member component name, or an empty string
     * @param memberTagName explicit member tag name, or an empty string
     */
    public Member(String memberId, boolean resolved, String elementName, String memberComponentClass,
        String memberComponentName, String memberTagName) {
      this.memberId = normalize(memberId);
      this.resolved = resolved;
      this.elementName = normalize(elementName);
      this.memberComponentClass = normalize(memberComponentClass);
      this.memberComponentName = normalize(memberComponentName);
      this.memberTagName = normalize(memberTagName);
    }

    /** @return referenced source identity, or an empty string when absent */
    public String getMemberId() {
      return memberId;
    }

    /** @return whether the member reference resolves to a source element */
    public boolean isResolved() {
      return resolved;
    }

    /** @return resolved member XML element name, or an empty string */
    public String getElementName() {
      return elementName;
    }

    /** @return explicit member component class, or an empty string */
    public String getMemberComponentClass() {
      return memberComponentClass;
    }

    /** @return explicit member component name, or an empty string */
    public String getMemberComponentName() {
      return memberComponentName;
    }

    /** @return explicit member tag name, or an empty string */
    public String getMemberTagName() {
      return memberTagName;
    }

    private Map<String, Object> toMap() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("memberId", memberId);
      result.put("resolved", Boolean.valueOf(resolved));
      result.put("elementName", elementName);
      result.put("memberComponentClass", memberComponentClass);
      result.put("memberComponentName", memberComponentName);
      result.put("memberTagName", memberTagName);
      return result;
    }
  }

  private final String id;
  private final String componentClass;
  private final String loopNumber;
  private final List<Member> members;

  /**
   * Creates immutable evidence for one instrumentation-loop grouping.
   *
   * @param id source XML identity, or an empty string when absent
   * @param componentClass source component class
   * @param loopNumber explicit source loop-number metadata, or an empty string when absent
   * @param members membership occurrences in source-document order
   */
  public DexpiInstrumentationLoopInfo(String id, String componentClass, String loopNumber, List<Member> members) {
    this.id = normalize(id);
    this.componentClass = normalize(componentClass);
    this.loopNumber = normalize(loopNumber);
    this.members = Collections.unmodifiableList(new ArrayList<Member>(members));
  }

  /** @return source XML identity, or an empty string when absent */
  public String getId() {
    return id;
  }

  /** @return source component class */
  public String getComponentClass() {
    return componentClass;
  }

  /** @return explicit source loop-number metadata, or an empty string when absent */
  public String getLoopNumber() {
    return loopNumber;
  }

  /** @return immutable membership occurrences in source-document order */
  public List<Member> getMembers() {
    return members;
  }

  Map<String, Object> toMap() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("id", id);
    result.put("componentClass", componentClass);
    result.put("loopNumber", loopNumber);
    result.put("memberCount", Integer.valueOf(members.size()));
    List<Map<String, Object>> memberMaps = new ArrayList<Map<String, Object>>();
    for (Member member : members) {
      memberMaps.add(member.toMap());
    }
    result.put("members", memberMaps);
    return result;
  }

  private static String normalize(String value) {
    return value == null ? "" : value;
  }
}
