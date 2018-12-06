/*
 * TimeSeries.java
 *
 * Created on 18. juni 2001, 19:24
 */

package neqsim.fluidMechanics.util.timeSeries;

import neqsim.fluidMechanics.flowSystem.FlowSystemInterface;
import neqsim.thermo.system.SystemInterface;

/**
 *
 * @author  esol
 * @version
 */
public class TimeSeries implements java.io.Serializable{

    private static final long serialVersionUID = 1000;
    
    protected double[] timeSeries, outletMolarFlowRate, outletMolarFlowRates;
    protected SystemInterface[] inletThermoSystem;
    protected SystemInterface[] thermoSystems;
    protected int numberOfTimeStepsInInterval;
    protected double[] times, timeSteps;
    
    
    /** Creates new TimeSeries */
    public TimeSeries() {
        this.timeSeries = new double[1];
    }
    
    public void setTimes(double[] times){
        this.timeSeries = times;
    }
    
    public void setInletThermoSystems(SystemInterface[] inletThermoSystem){
        this.inletThermoSystem = inletThermoSystem;
    }
    
    public void setOutletMolarFlowRate(double[] outletMolarFlowRate){
        this.outletMolarFlowRate = outletMolarFlowRate;
    }
    
    public double[] getOutletMolarFlowRates(){
        return this.outletMolarFlowRates;
    }
    
    public void setNumberOfTimeStepsInInterval(int numberOfTimeStepsInInterval){
        this.numberOfTimeStepsInInterval = numberOfTimeStepsInInterval;
    }
    
    public void init(FlowSystemInterface flowSystem){
        int p=0;
        thermoSystems = new SystemInterface[(timeSeries.length-1)*numberOfTimeStepsInInterval];
        outletMolarFlowRates = new double[(timeSeries.length-1)*numberOfTimeStepsInInterval];
        timeSteps = new double[(timeSeries.length-1)*numberOfTimeStepsInInterval];
        times = new double[(timeSeries.length-1)*numberOfTimeStepsInInterval];
        
        System.out.println("times " + inletThermoSystem.length);
        double temp=0;
        for(int k=0;k<timeSeries.length-1;k++){
            double stepLength = (timeSeries[k+1]-timeSeries[k])/(double)numberOfTimeStepsInInterval;
            for(int i=0;i<numberOfTimeStepsInInterval;i++){
                timeSteps[p] = stepLength;
                temp += stepLength;
                times[p] =  temp;
                if(Double.isNaN(outletMolarFlowRate[0])){
                    outletMolarFlowRates[p] = outletMolarFlowRate[k];
                }
                thermoSystems[p++] = (SystemInterface) inletThermoSystem[k].clone();
            }
        }
    }
    
    public SystemInterface[] getThermoSystem(){
        return thermoSystems;
    }
    
    public double[] getTimeStep(){
        return timeSteps;
    }
    
    public double[] getTime(){
        return times;
    }
    
    public double getTime(int i){
        return times[i];
    }
    
}
