package neqsim.processSimulation.mechanicalDesign.separator.sectionType;

import neqsim.processSimulation.processEquipment.separator.sectionType.SeparatorSection;

/**
 * <p>
 * SepDesignSection class.
 * </p>
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
     * <p>
     * Constructor for SepDesignSection.
     * </p>
     *
     * @param separatorSection a
     *        {@link neqsim.processSimulation.processEquipment.separator.sectionType.SeparatorSection}
     *        object
     */
    public SepDesignSection(SeparatorSection separatorSection) {
        this.separatorSection = separatorSection;
    }

    /**
     * <p>
     * calcDesign.
     * </p>
     */
    public void calcDesign() {
        totalWeight = 1.0;
        totalHeight = 1.0;
    }

    /**
     * <p>
     * Getter for the field <code>totalWeight</code>.
     * </p>
     *
     * @return the totalWeight
     */
    public double getTotalWeight() {
        return totalWeight;
    }

    /**
     * <p>
     * Setter for the field <code>totalWeight</code>.
     * </p>
     *
     * @param totalWeight the totalWeight to set
     */
    public void setTotalWeight(double totalWeight) {
        this.totalWeight = totalWeight;
    }

    /**
     * <p>
     * Getter for the field <code>totalHeight</code>.
     * </p>
     *
     * @return the totalHeight
     */
    public double getTotalHeight() {
        return totalHeight;
    }

    /**
     * <p>
     * Setter for the field <code>totalHeight</code>.
     * </p>
     *
     * @param totalHeight the totalHeight to set
     */
    public void setTotalHeight(double totalHeight) {
        this.totalHeight = totalHeight;
    }

    /**
     * <p>
     * getANSIclass.
     * </p>
     *
     * @return the ANSIclass
     */
    public int getANSIclass() {
        return ANSIclass;
    }

    /**
     * <p>
     * setANSIclass.
     * </p>
     *
     * @param ANSIclass the ANSIclass to set
     */
    public void setANSIclass(int ANSIclass) {
        this.ANSIclass = ANSIclass;
    }

    /**
     * <p>
     * Getter for the field <code>nominalSize</code>.
     * </p>
     *
     * @return the nominalSize
     */
    public String getNominalSize() {
        return nominalSize;
    }

    /**
     * <p>
     * Setter for the field <code>nominalSize</code>.
     * </p>
     *
     * @param nominalSize the nominalSize to set
     */
    public void setNominalSize(String nominalSize) {
        this.nominalSize = nominalSize;
    }
}
