/*
 * SampleValue.java
 *
 * Created on 22. januar 2001, 23:01
 */

package neqsim.statistics.parameterFitting;

import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 *
 * @author  Even Solbraa
 * @version
 */
public class SampleValue extends Object implements Cloneable{

    private static final long serialVersionUID = 1000;
    
    protected  FunctionInterface testFunction;
    double sampleValue=0;
    double[] dependentValues;
    String reference = "unknown";
    String description = "unknown";
/** Standard deviation of function value
 */
    double standardDeviation=0.0001;
    public SystemInterface system;
    public ThermodynamicOperations thermoOps;
    /** Standard deviation of dependent variables
     */
    double[] standardDeviations;
    
    /** Creates new SampleValue */
    public SampleValue() {
    }
    
    public SampleValue(double sampleValue, double standardDeviation, double[] dependentValues) {
        this.dependentValues = new double[dependentValues.length];
        this.sampleValue = sampleValue;
        this.standardDeviation = standardDeviation;
        System.arraycopy(dependentValues, 0, this.dependentValues, 0, dependentValues.length);
    }
    
    public SampleValue(double sampleValue, double standardDeviation, double[] dependentValues, double[] standardDeviations){
        this(sampleValue, standardDeviation, dependentValues);
        this.standardDeviations = standardDeviations;
    }
    
    
    public Object clone(){
        SampleValue clonedValue = null;
        try{
            clonedValue = (SampleValue) super.clone();
        }
        catch(Exception e)
        {
            e.printStackTrace(System.err);
        }
        // this was modified 20.05.2002
        //clonedValue.system = (SystemInterface) system.clone();
        clonedValue.testFunction = (FunctionInterface) testFunction.clone();
        clonedValue.dependentValues = this.dependentValues.clone();
        System.arraycopy(dependentValues,0,clonedValue.dependentValues,0,dependentValues.length);
        
        return clonedValue;
    }
    
    public void setThermodynamicSystem(SystemInterface system){
        this.system = system;//(SystemInterface) system.clone();
        thermoOps = new ThermodynamicOperations(system);
        this.getFunction().setThermodynamicSystem(this.system);
    }
     
    public void setFunction(BaseFunction function){
        testFunction = function;
    }
    
    public FunctionInterface getFunction(){
        return testFunction;
    }
    
    public double getStandardDeviation(){
        return standardDeviation;
    }
    
    public double getStandardDeviation(int i){
        return standardDeviations[i];
    }
    
    public double getSampleValue(){
        return sampleValue;
    }
    
    public double[] getDependentValues(){
        return dependentValues;
    }
    
    public double getDependentValue(int i){
        return dependentValues[i];
    }
    
    public void setDependentValues(double[] vals){
        System.arraycopy(vals, 0, this.dependentValues, 0, dependentValues.length);
    }
    
    public void setDependentValue(int i, double val){
        this.dependentValues[i] = val;
    }
    
    /** Getter for property reference.
     * @return Value of property reference.
     */
    public java.lang.String getReference() {
        return reference;
    }
    
    /** Setter for property reference.
     * @param reference New value of property reference.
     */
    public void setReference(java.lang.String reference) {
        this.reference = reference;
    }
    
    /** Getter for property description.
     * @return Value of property description.
     */
    public java.lang.String getDescription() {
        return description;
    }
    
    /** Setter for property description.
     * @param description New value of property description.
     */
    public void setDescription(java.lang.String description) {
        this.description = description;
    }
    
}
