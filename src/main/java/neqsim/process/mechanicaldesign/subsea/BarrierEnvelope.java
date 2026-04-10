package neqsim.process.mechanicaldesign.subsea;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents a well barrier envelope per NORSOK D-010 Section 5.
 *
 * <p>
 * A barrier envelope is an ordered collection of barrier elements that together form a
 * pressure-containing boundary between the reservoir and the environment. Per NORSOK D-010, every
 * well must maintain at least two independent barrier envelopes (primary and secondary) at all
 * times.
 * </p>
 *
 * <p>
 * For production wells (NORSOK D-010 Table 20):
 * </p>
 * <ul>
 * <li>Primary: tubing, DHSV, tubing hanger, Xmas tree</li>
 * <li>Secondary: production casing, casing cement, wellhead, casing hanger</li>
 * </ul>
 *
 * <p>
 * For injection wells (NORSOK D-010 Tables 36-37):
 * </p>
 * <ul>
 * <li>Primary: tubing, packer, ISV, tubing hanger, Xmas tree</li>
 * <li>Secondary: production casing, casing cement, wellhead, casing hanger</li>
 * </ul>
 *
 * @author ESOL
 * @version 1.0
 * @see BarrierElement
 * @see WellBarrierSchematic
 */
public class BarrierEnvelope implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Envelope name (e.g., "Primary", "Secondary"). */
  private final String name;

  /** Ordered list of barrier elements forming this envelope. */
  private final List<BarrierElement> elements;

  /**
   * Create a barrier envelope.
   *
   * @param name envelope name (e.g., "Primary", "Secondary")
   */
  public BarrierEnvelope(String name) {
    this.name = name;
    this.elements = new ArrayList<BarrierElement>();
  }

  /**
   * Add a barrier element to this envelope.
   *
   * @param element the barrier element
   */
  public void addElement(BarrierElement element) {
    elements.add(element);
  }

  /**
   * Get the envelope name.
   *
   * @return envelope name
   */
  public String getName() {
    return name;
  }

  /**
   * Get all elements in this envelope (unmodifiable).
   *
   * @return list of barrier elements
   */
  public List<BarrierElement> getElements() {
    return Collections.unmodifiableList(elements);
  }

  /**
   * Get the number of elements in this envelope.
   *
   * @return element count
   */
  public int getElementCount() {
    return elements.size();
  }

  /**
   * Get the number of functional (intact or degraded) elements.
   *
   * @return count of functional elements
   */
  public int getFunctionalElementCount() {
    int count = 0;
    for (BarrierElement el : elements) {
      if (el.isFunctional()) {
        count++;
      }
    }
    return count;
  }

  /**
   * Get the number of verified elements.
   *
   * @return count of verified elements
   */
  public int getVerifiedElementCount() {
    int count = 0;
    for (BarrierElement el : elements) {
      if (el.isVerified()) {
        count++;
      }
    }
    return count;
  }

  /**
   * Check if this envelope is intact (all elements functional).
   *
   * @return true if all elements are functional
   */
  public boolean isIntact() {
    if (elements.isEmpty()) {
      return false;
    }
    for (BarrierElement el : elements) {
      if (!el.isFunctional()) {
        return false;
      }
    }
    return true;
  }

  /**
   * Check if this envelope has the minimum required elements.
   *
   * <p>
   * Per NORSOK D-010, each envelope must have at least 2 independent elements.
   * </p>
   *
   * @param minElements minimum number of functional elements required
   * @return true if enough functional elements exist
   */
  public boolean meetsMinimum(int minElements) {
    return getFunctionalElementCount() >= minElements;
  }

  /**
   * Check if a specific element type exists in this envelope.
   *
   * @param type element type to check for
   * @return true if at least one element of this type exists
   */
  public boolean hasElementType(BarrierElement.ElementType type) {
    for (BarrierElement el : elements) {
      if (el.getType() == type) {
        return true;
      }
    }
    return false;
  }

  /**
   * Get all elements of a specific type.
   *
   * @param type element type to search for
   * @return list of matching elements
   */
  public List<BarrierElement> getElementsByType(BarrierElement.ElementType type) {
    List<BarrierElement> result = new ArrayList<BarrierElement>();
    for (BarrierElement el : elements) {
      if (el.getType() == type) {
        result.add(el);
      }
    }
    return result;
  }

  /**
   * Get failed elements in this envelope.
   *
   * @return list of failed elements
   */
  public List<BarrierElement> getFailedElements() {
    List<BarrierElement> failed = new ArrayList<BarrierElement>();
    for (BarrierElement el : elements) {
      if (el.getStatus() == BarrierElement.Status.FAILED) {
        failed.add(el);
      }
    }
    return failed;
  }

  @Override
  public String toString() {
    return name + " envelope: " + getFunctionalElementCount() + "/" + elements.size()
        + " functional";
  }
}
