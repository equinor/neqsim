/*
 * ContractSpecification.java
 *
 * Created on 15. juni 2004, 22:59
 */

package neqsim.standards.salesContract;

import neqsim.standards.StandardInterface;

/**
 * <p>ContractSpecification class.</p>
 *
 * @author ESOL
 * @version $Id: $Id
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

    /**
     * Creates a new instance of ContractSpecification
     */
    public ContractSpecification() {
    }

    /**
     * <p>Constructor for ContractSpecification.</p>
     *
     * @param name a {@link java.lang.String} object
     * @param description a {@link java.lang.String} object
     * @param country a {@link java.lang.String} object
     * @param terminal a {@link java.lang.String} object
     * @param standard a {@link neqsim.standards.StandardInterface} object
     * @param minValue a double
     * @param maxValue a double
     * @param unit a {@link java.lang.String} object
     * @param referenceTemperature a double
     * @param referenceTemperatureComb a double
     * @param referencePressure a double
     * @param comments a {@link java.lang.String} object
     */
    public ContractSpecification(String name, String description, String country, String terminal,
            StandardInterface standard, double minValue, double maxValue, String unit, double referenceTemperature,
            double referenceTemperatureComb, double referencePressure, String comments) {
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
     */
    public neqsim.standards.StandardInterface getStandard() {
        return standard;
    }

    /**
     * Setter for property standard.
     *
     * @param standard New value of property standard.
     */
    public void setStandard(neqsim.standards.StandardInterface standard) {
        this.standard = standard;
    }

    /**
     * Getter for property description.
     *
     * @return Value of property description.
     */
    public java.lang.String getDescription() {
        return description;
    }

    /**
     * Setter for property description.
     *
     * @param description New value of property description.
     */
    public void setDescription(java.lang.String description) {
        this.description = description;
    }

    /**
     * Getter for property specification.
     *
     * @return Value of property specification.
     */
    public double getMinValue() {
        return minValue;
    }

    /**
     * <p>Setter for the field <code>minValue</code>.</p>
     *
     * @param minValue a double
     */
    public void setMinValue(double minValue) {
        this.minValue = minValue;
    }

    /**
     * <p>Getter for the field <code>maxValue</code>.</p>
     *
     * @return a double
     */
    public double getMaxValue() {
        return maxValue;
    }

    /**
     * <p>Setter for the field <code>maxValue</code>.</p>
     *
     * @param maxValue a double
     */
    public void setMaxValue(double maxValue) {
        this.maxValue = maxValue;
    }

    /**
     * <p>Getter for the field <code>unit</code>.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public String getUnit() {
        return unit;
    }

    /**
     * <p>Setter for the field <code>unit</code>.</p>
     *
     * @param unit a {@link java.lang.String} object
     */
    public void setUnit(String unit) {
        this.unit = unit;
    }

    /**
     * <p>Getter for the field <code>referenceTemperatureMeasurement</code>.</p>
     *
     * @return a double
     */
    public double getReferenceTemperatureMeasurement() {
        return referenceTemperatureMeasurement;
    }

    /**
     * <p>Setter for the field <code>referenceTemperatureMeasurement</code>.</p>
     *
     * @param referenceTemperature a double
     */
    public void setReferenceTemperatureMeasurement(double referenceTemperature) {
        this.referenceTemperatureMeasurement = referenceTemperature;
    }

    /**
     * <p>Getter for the field <code>referencePressure</code>.</p>
     *
     * @return a double
     */
    public double getReferencePressure() {
        return referencePressure;
    }

    /**
     * <p>Setter for the field <code>referencePressure</code>.</p>
     *
     * @param referencePressure a double
     */
    public void setReferencePressure(double referencePressure) {
        this.referencePressure = referencePressure;
    }

    /**
     * <p>Getter for the field <code>comments</code>.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public String getComments() {
        return comments;
    }

    /**
     * <p>Setter for the field <code>comments</code>.</p>
     *
     * @param comments a {@link java.lang.String} object
     */
    public void setComments(String comments) {
        this.comments = comments;
    }

    /**
     * <p>Getter for the field <code>name</code>.</p>
     *
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * <p>Setter for the field <code>name</code>.</p>
     *
     * @param name the name to set
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * <p>Getter for the field <code>referenceTemperatureCombustion</code>.</p>
     *
     * @return the referenceTemperatureCombustion
     */
    public double getReferenceTemperatureCombustion() {
        return referenceTemperatureCombustion;
    }

    /**
     * <p>Setter for the field <code>referenceTemperatureCombustion</code>.</p>
     *
     * @param referenceTemperatureCombustion the referenceTemperatureCombustion to
     *                                       set
     */
    public void setReferenceTemperatureCombustion(double referenceTemperatureCombustion) {
        this.referenceTemperatureCombustion = referenceTemperatureCombustion;
    }

    /**
     * <p>Getter for the field <code>country</code>.</p>
     *
     * @return the country
     */
    public String getCountry() {
        return country;
    }

    /**
     * <p>Setter for the field <code>country</code>.</p>
     *
     * @param country the country to set
     */
    public void setCountry(String country) {
        this.country = country;
    }

    /**
     * <p>Getter for the field <code>terminal</code>.</p>
     *
     * @return the terminal
     */
    public String getTerminal() {
        return terminal;
    }

    /**
     * <p>Setter for the field <code>terminal</code>.</p>
     *
     * @param terminal the terminal to set
     */
    public void setTerminal(String terminal) {
        this.terminal = terminal;
    }

}
