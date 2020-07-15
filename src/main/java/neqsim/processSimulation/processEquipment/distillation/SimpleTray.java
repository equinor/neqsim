/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package neqsim.processSimulation.processEquipment.distillation;

import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.processSimulation.processEquipment.stream.StreamInterface;

/**
 *
 * @author ESOL
 */
public class SimpleTray extends neqsim.processSimulation.processEquipment.mixer.Mixer implements TrayInterface {

    private static final long serialVersionUID = 1000;

    double heatInput = 0.0;
    private double temperature = 273.15;

    public SimpleTray() {
    }

    public void init() {
         int pp=0;
        if(streams.size()==3) {
            pp=1;
         }
        for (int k =pp; k < streams.size(); k++) {
            (((StreamInterface) streams.get(k)).getThermoSystem()).setTemperature(temperature);
        }

    }

    public void setHeatInput(double heatinp) {
        this.heatInput = heatinp;
    }

    public double calcMixStreamEnthalpy() {
        double enthalpy = heatInput;
        if(isSetEnergyStream()) {
        	enthalpy-=energyStream.getDuty();
        }
       
        for (int k=0; k < streams.size(); k++) {
            ((StreamInterface) streams.get(k)).getThermoSystem().init(3);
            enthalpy += ((StreamInterface) streams.get(k)).getThermoSystem().getEnthalpy();
           // System.out.println("total enthalpy k : " + ((SystemInterface) ((Stream) streams.get(k)).getThermoSystem()).getEnthalpy());
        }
     //   System.out.println("total enthalpy of streams: " + enthalpy);
        return enthalpy;
    }

    public void run(){
      super.run();
      temperature = mixedStream.getTemperature(); 
    }

    public void runTransient() {
    }

    public Stream getGasOutStream() {
        return new Stream("", mixedStream.getThermoSystem().phaseToSystem(0));
    }

    public Stream getLiquidOutStream() {
        return new Stream("", mixedStream.getThermoSystem().phaseToSystem(1));
    }

    /**
     * @return the temperature
     */
    public double getTemperature() {
        return temperature;
    }

    /**
     * @param temperature the temperature to set
     */
    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

      
}
