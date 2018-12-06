/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package neqsim.processSimulation.processEquipment.separator.sectionType;

import neqsim.processSimulation.mechanicalDesign.separator.sectionType.SepDesignSection;
import neqsim.processSimulation.processEquipment.separator.Separator;

/**
 *
 * @author esol
 */
public class SeparatorSection {

    private static final long serialVersionUID = 1000;

    private double efficiency = 0.95;
    public Separator separator = null;
    private boolean calcEfficiency = false;
    private double pressureDrop = 33 / 5.0 * 1e-3;//bar
    private String name = "1";
    String type;
    public double outerDiameter = 1.0;
    public SepDesignSection mechanicalDesign = null;

    public SeparatorSection(String type, Separator sep) {
        this.type = type;
        this.separator = sep;
        mechanicalDesign = new SepDesignSection(this);
    }

    public SeparatorSection(String name, String type, Separator sep) {
        this(type, sep);
        this.name = name;
    }

    public double calcEfficiency() {
        return efficiency;
    }

    /**
     * @return the efficiency
     */
    public double getEfficiency() {
        if (isCalcEfficiency()) {
            return calcEfficiency();
        }
        return efficiency;
    }

    /**
     * @param efficiency the efficiency to set
     */
    public void setEfficiency(double efficiency) {
        this.efficiency = efficiency;
    }

    /**
     * @return the calcEfficiency
     */
    public boolean isCalcEfficiency() {
        return calcEfficiency;
    }

    /**
     * @param calcEfficiency the calcEfficiency to set
     */
    public void setCalcEfficiency(boolean calcEfficiency) {
        this.calcEfficiency = calcEfficiency;
    }

    public double getMinimumLiquidSealHeight() {
        return getPresureDrop() * 1e5 / neqsim.thermo.ThermodynamicConstantsInterface.gravity / (getSeparator().getThermoSystem().getPhase(1).getPhysicalProperties().getDensity() - getSeparator().getThermoSystem().getPhase(0).getPhysicalProperties().getDensity());

    }

    /**
     * @return the presureDrop
     */
    public double getPresureDrop() {
        return pressureDrop;
    }

    /**
     * @param presureDrop the presureDrop to set
     */
    public void setPresureDrop(double presureDrop) {
        this.pressureDrop = presureDrop;
    }

    /**
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * @param name the name to set
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * @return the mechanicalDesign
     */
    public SepDesignSection getMechanicalDesign() {
        return mechanicalDesign;
    }

    /**
     * @param mechanicalDesign the mechanicalDesign to set
     */
    public void setMechanicalDesign(SepDesignSection mechanicalDesign) {
        this.mechanicalDesign = mechanicalDesign;
    }

    /**
     * @return the separator
     */
    public Separator getSeparator() {
        return separator;
    }

    /**
     * @param separator the separator to set
     */
    public void setSeparator(Separator separator) {
        this.separator = separator;
    }

    /**
     * @return the outerDiameter
     */
    public double getOuterDiameter() {
        return outerDiameter;
    }

    /**
     * @param outerDiameter the outerDiameter to set
     */
    public void setOuterDiameter(double outerDiameter) {
        this.outerDiameter = outerDiameter;
    }
}
