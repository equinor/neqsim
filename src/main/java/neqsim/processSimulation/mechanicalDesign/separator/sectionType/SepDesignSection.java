/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package neqsim.processSimulation.mechanicalDesign.separator.sectionType;

import neqsim.processSimulation.processEquipment.separator.sectionType.SeparatorSection;

/**
 *
 * @author esol
 */
public class SepDesignSection {

    private static final long serialVersionUID = 1000;

    SeparatorSection separatorSection = null;
    public double totalWeight = 1.0;
    public double totalHeight = 1.0;
    public int ANSIclass = 300;
    public String nominalSize = "";

    public SepDesignSection(SeparatorSection separatorSection) {
        this.separatorSection = separatorSection;
    }

    public void calcDesign() {
        totalWeight = 1.0;
        totalHeight = 1.0;
    }

    /**
     * @return the totalWeight
     */
    public double getTotalWeight() {
        return totalWeight;
    }

    /**
     * @param totalWeight the totalWeight to set
     */
    public void setTotalWeight(double totalWeight) {
        this.totalWeight = totalWeight;
    }

    /**
     * @return the totalHeight
     */
    public double getTotalHeight() {
        return totalHeight;
    }

    /**
     * @param totalHeight the totalHeight to set
     */
    public void setTotalHeight(double totalHeight) {
        this.totalHeight = totalHeight;
    }

    /**
     * @return the ANSIclass
     */
    public int getANSIclass() {
        return ANSIclass;
    }

    /**
     * @param ANSIclass the ANSIclass to set
     */
    public void setANSIclass(int ANSIclass) {
        this.ANSIclass = ANSIclass;
    }

   
    /**
     * @return the nominalSize
     */
    public String getNominalSize() {
        return nominalSize;
    }

    /**
     * @param nominalSize the nominalSize to set
     */
    public void setNominalSize(String nominalSize) {
        this.nominalSize = nominalSize;
    }
}
