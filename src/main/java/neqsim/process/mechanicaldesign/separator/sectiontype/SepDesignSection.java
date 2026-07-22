package neqsim.process.mechanicaldesign.separator.sectiontype;

import neqsim.process.equipment.separator.sectiontype.SeparatorSection;

/**
 * SepDesignSection class.
 *
 * @author esol
 * @version $Id: $Id
 */
public class SepDesignSection {
  SeparatorSection separatorSection = null;
  public double totalWeight = 1.0;
  public double totalHeight = 1.0;
  public int ANSIclass = 300;
  public String nominalSize = "";

  /**
   * Constructor for SepDesignSection.
   *
   * @param separatorSection a {@link neqsim.process.equipment.separator.sectiontype.SeparatorSection} object
   */
  public SepDesignSection(SeparatorSection separatorSection) {
    this.separatorSection = separatorSection;
  }

  /**
   * calcDesign.
   */
  public void calcDesign() {
    totalWeight = 1.0;
    totalHeight = 1.0;
  }

  /**
   * Getter for the field <code>totalWeight</code>.
   *
   * @return the totalWeight
   */
  public double getTotalWeight() {
    return totalWeight;
  }

  /**
   * Setter for the field <code>totalWeight</code>.
   *
   * @param totalWeight the totalWeight to set
   */
  public void setTotalWeight(double totalWeight) {
    this.totalWeight = totalWeight;
  }

  /**
   * Getter for the field <code>totalHeight</code>.
   *
   * @return the totalHeight
   */
  public double getTotalHeight() {
    return totalHeight;
  }

  /**
   * Setter for the field <code>totalHeight</code>.
   *
   * @param totalHeight the totalHeight to set
   */
  public void setTotalHeight(double totalHeight) {
    this.totalHeight = totalHeight;
  }

  /**
   * getANSIclass.
   *
   * @return the ANSIclass
   */
  public int getANSIclass() {
    return ANSIclass;
  }

  /**
   * setANSIclass.
   *
   * @param ANSIclass the ANSIclass to set
   */
  public void setANSIclass(int ANSIclass) {
    this.ANSIclass = ANSIclass;
  }

  /**
   * Getter for the field <code>nominalSize</code>.
   *
   * @return the nominalSize
   */
  public String getNominalSize() {
    return nominalSize;
  }

  /**
   * Setter for the field <code>nominalSize</code>.
   *
   * @param nominalSize the nominalSize to set
   */
  public void setNominalSize(String nominalSize) {
    this.nominalSize = nominalSize;
  }
}
