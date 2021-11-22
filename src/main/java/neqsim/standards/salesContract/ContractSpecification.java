/*
 * ContractSpecification.java
 *
 * Created on 15. juni 2004, 22:59
 */

package neqsim.standards.salesContract;

import neqsim.standards.StandardInterface;

/**
 *
 * @author ESOL
 */
public class ContractSpecification {
    private static final long serialVersionUID = 1000;

    StandardInterface standard = null;
    private String name = "";
    String description = "dew point temperature specification";
    private String country = "";
    private String terminal = "";
    private double minValue = 0;
    private double maxValue = 0;
    private double referenceTemperatureMeasurement = 0, referenceTemperatureCombustion = 0;
    private double referencePressure = 0;
    private String unit = "", comments = "";

    /** Creates a new instance of ContractSpecification */
    public ContractSpecification() {}

    public ContractSpecification(String name, String description, String country, String terminal,
            StandardInterface standard, double minValue, double maxValue, String unit,
            double referenceTemperature, double referenceTemperatureComb, double referencePressure,
            String comments) {
        this.name = name;
        this.country = country;
        this.terminal = terminal;
        this.description = description;
        this.standard = standard;
        this.unit = unit;
        this.setReferenceTemperatureMeasurement(referenceTemperature);
        this.setReferenceTemperatureCombustion(referenceTemperatureComb);
        this.setReferencePressure(referencePressure);
        this.setComments(comments);
        this.setMinValue(minValue);
        this.setMaxValue(maxValue);
    }

    /**
     * Getter for property standard.
     * 
     * @return Value of property standard.
     *
     */
    public neqsim.standards.StandardInterface getStandard() {
        return standard;
    }

    /**
     * Setter for property standard.
     * 
     * @param standard New value of property standard.
     *
     */
    public void setStandard(neqsim.standards.StandardInterface standard) {
        this.standard = standard;
    }

    /**
     * Getter for property description.
     * 
     * @return Value of property description.
     *
     */
    public java.lang.String getDescription() {
        return description;
    }

    /**
     * Setter for property description.
     * 
     * @param description New value of property description.
     *
     */
    public void setDescription(java.lang.String description) {
        this.description = description;
    }

    /**
     * Getter for property specification.
     * 
     * @return Value of property specification.
     *
     */

    public double getMinValue() {
        return minValue;
    }

    public void setMinValue(double minValue) {
        this.minValue = minValue;
    }

    public double getMaxValue() {
        return maxValue;
    }

    public void setMaxValue(double maxValue) {
        this.maxValue = maxValue;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public double getReferenceTemperatureMeasurement() {
        return referenceTemperatureMeasurement;
    }

    public void setReferenceTemperatureMeasurement(double referenceTemperature) {
        this.referenceTemperatureMeasurement = referenceTemperature;
    }

    public double getReferencePressure() {
        return referencePressure;
    }

    public void setReferencePressure(double referencePressure) {
        this.referencePressure = referencePressure;
    }

    public String getComments() {
        return comments;
    }

    public void setComments(String comments) {
        this.comments = comments;
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
     * @return the referenceTemperatureCombustion
     */
    public double getReferenceTemperatureCombustion() {
        return referenceTemperatureCombustion;
    }

    /**
     * @param referenceTemperatureCombustion the referenceTemperatureCombustion to set
     */
    public void setReferenceTemperatureCombustion(double referenceTemperatureCombustion) {
        this.referenceTemperatureCombustion = referenceTemperatureCombustion;
    }

    /**
     * @return the country
     */
    public String getCountry() {
        return country;
    }

    /**
     * @param country the country to set
     */
    public void setCountry(String country) {
        this.country = country;
    }

    /**
     * @return the terminal
     */
    public String getTerminal() {
        return terminal;
    }

    /**
     * @param terminal the terminal to set
     */
    public void setTerminal(String terminal) {
        this.terminal = terminal;
    }
}
