package neqsim.processSimulation.processEquipment.separator.sectionType;

import neqsim.processSimulation.mechanicalDesign.separator.sectionType.SepDesignSection;
import neqsim.processSimulation.processEquipment.separator.Separator;

/**
 * <p>
 * SeparatorSection class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class SeparatorSection {
    private static final long serialVersionUID = 1000;

    private double efficiency = 0.95;
    public Separator separator = null;
    private boolean calcEfficiency = false;
    private double pressureDrop = 33 / 5.0 * 1e-3;// bar
    protected String name = "1";
    String type;
    public double outerDiameter = 1.0;
    public SepDesignSection mechanicalDesign = null;

    /**
     * <p>
     * Constructor for SeparatorSection.
     * </p>
     *
     * @param type a {@link java.lang.String} object
     * @param sep a {@link neqsim.processSimulation.processEquipment.separator.Separator} object
     */
    public SeparatorSection(String type, Separator sep) {
        this.type = type;
        this.separator = sep;
        mechanicalDesign = new SepDesignSection(this);
    }

    /**
     * <p>
     * Constructor for SeparatorSection.
     * </p>
     *
     * @param name a {@link java.lang.String} object
     * @param type a {@link java.lang.String} object
     * @param sep a {@link neqsim.processSimulation.processEquipment.separator.Separator} object
     */
    public SeparatorSection(String name, String type, Separator sep) {
        this(type, sep);
        this.name = name;
    }

    /**
     * <p>
     * calcEfficiency.
     * </p>
     *
     * @return a double
     */
    public double calcEfficiency() {
        return efficiency;
    }

    /**
     * <p>
     * Getter for the field <code>efficiency</code>.
     * </p>
     *
     * @return the efficiency
     */
    public double getEfficiency() {
        if (isCalcEfficiency()) {
            return calcEfficiency();
        }
        return efficiency;
    }

    /**
     * <p>
     * Setter for the field <code>efficiency</code>.
     * </p>
     *
     * @param efficiency the efficiency to set
     */
    public void setEfficiency(double efficiency) {
        this.efficiency = efficiency;
    }

    /**
     * <p>
     * isCalcEfficiency.
     * </p>
     *
     * @return the calcEfficiency
     */
    public boolean isCalcEfficiency() {
        return calcEfficiency;
    }

    /**
     * <p>
     * Setter for the field <code>calcEfficiency</code>.
     * </p>
     *
     * @param calcEfficiency the calcEfficiency to set
     */
    public void setCalcEfficiency(boolean calcEfficiency) {
        this.calcEfficiency = calcEfficiency;
    }

    /**
     * <p>
     * getMinimumLiquidSealHeight.
     * </p>
     *
     * @return a double
     */
    public double getMinimumLiquidSealHeight() {
        return getPresureDrop() * 1e5 / neqsim.thermo.ThermodynamicConstantsInterface.gravity
                / (getSeparator().getThermoSystem().getPhase(1).getPhysicalProperties().getDensity()
                        - getSeparator().getThermoSystem().getPhase(0).getPhysicalProperties()
                                .getDensity());

    }

    /**
     * <p>
     * getPresureDrop.
     * </p>
     *
     * @return the presureDrop
     */
    public double getPresureDrop() {
        return pressureDrop;
    }

    /**
     * <p>
     * setPresureDrop.
     * </p>
     *
     * @param presureDrop the presureDrop to set
     */
    public void setPresureDrop(double presureDrop) {
        this.pressureDrop = presureDrop;
    }

    /**
     * <p>
     * Getter for the field <code>name</code>.
     * </p>
     *
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * <p>
     * Setter for the field <code>name</code>.
     * </p>
     *
     * @param name the name to set
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * <p>
     * Getter for the field <code>mechanicalDesign</code>.
     * </p>
     *
     * @return the mechanicalDesign
     */
    public SepDesignSection getMechanicalDesign() {
        return mechanicalDesign;
    }

    /**
     * <p>
     * Setter for the field <code>mechanicalDesign</code>.
     * </p>
     *
     * @param mechanicalDesign the mechanicalDesign to set
     */
    public void setMechanicalDesign(SepDesignSection mechanicalDesign) {
        this.mechanicalDesign = mechanicalDesign;
    }

    /**
     * <p>
     * Getter for the field <code>separator</code>.
     * </p>
     *
     * @return the separator
     */
    public Separator getSeparator() {
        return separator;
    }

    /**
     * <p>
     * Setter for the field <code>separator</code>.
     * </p>
     *
     * @param separator the separator to set
     */
    public void setSeparator(Separator separator) {
        this.separator = separator;
    }

    /**
     * <p>
     * Getter for the field <code>outerDiameter</code>.
     * </p>
     *
     * @return the outerDiameter
     */
    public double getOuterDiameter() {
        return outerDiameter;
    }

    /**
     * <p>
     * Setter for the field <code>outerDiameter</code>.
     * </p>
     *
     * @param outerDiameter the outerDiameter to set
     */
    public void setOuterDiameter(double outerDiameter) {
        this.outerDiameter = outerDiameter;
    }
}
