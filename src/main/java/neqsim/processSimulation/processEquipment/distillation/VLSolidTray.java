/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package neqsim.processSimulation.processEquipment.distillation;

import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.processSimulation.processEquipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 *
 * @author ESOL
 */
public class VLSolidTray extends SimpleTray implements TrayInterface {

    private static final long serialVersionUID = 1000;

    double heatInput = 0.0;
    private double temperature = 273.15;

    public VLSolidTray() {
    }

    @Override
	public void init() {
        int pp = 0;
        if (streams.size() == 3) {
            pp = 1;
        }
        for (int k = pp; k < streams.size(); k++) {
            (streams.get(k).getThermoSystem()).setTemperature(temperature);
        }

    }

    @Override
	public void setHeatInput(double heatinp) {
        this.heatInput = heatinp;
    }

    @Override
	public double calcMixStreamEnthalpy() {
        double enthalpy = heatInput;

        for (int k = 0; k < streams.size(); k++) {
            streams.get(k).getThermoSystem().init(3);
            enthalpy += streams.get(k).getThermoSystem().getEnthalpy();
            System.out.println("total enthalpy k : "
                    + streams.get(k).getThermoSystem().getEnthalpy());
        }
        System.out.println("total enthalpy of streams: " + enthalpy);
        return enthalpy;
    }

    @Override
	public void run() {
        double enthalpy = 0.0;

//        ((Stream) streams.get(0)).getThermoSystem().display();

        SystemInterface thermoSystem2 = (SystemInterface) streams.get(0).getThermoSystem().clone();
        // System.out.println("total number of moles " +
        // thermoSystem2.getTotalNumberOfMoles());
        mixedStream.setThermoSystem(thermoSystem2);
        // thermoSystem2.display();
        ThermodynamicOperations testOps = new ThermodynamicOperations(thermoSystem2);
        if (streams.size() > 0) {
            // mixedStream.getThermoSystem().setSolidPhaseCheck("CO2");
            mixedStream.getThermoSystem().setNumberOfPhases(2);
            mixedStream.getThermoSystem().reInitPhaseType();
            mixedStream.getThermoSystem().init(0);

            mixStream();

            enthalpy = calcMixStreamEnthalpy();
            // mixedStream.getThermoSystem().display();
            // System.out.println("temp guess " + guessTemperature());
            mixedStream.getThermoSystem().setSolidPhaseCheck("CO2");
            mixedStream.getThermoSystem().setTemperature(guessTemperature());
            testOps.PHsolidFlash(enthalpy);
            mixedStream.getThermoSystem().display();
            // System.out.println("filan temp " + mixedStream.getTemperature());
        } else {
            testOps.TPflash();
        }
        mixedStream.getThermoSystem().setSolidPhaseCheck(false);
        // System.out.println("enthalpy: " +
        // mixedStream.getThermoSystem().getEnthalpy());
        // System.out.println("enthalpy: " + enthalpy);
        // System.out.println("temperature: " +
        // mixedStream.getThermoSystem().getTemperature());

        // System.out.println("beta " + mixedStream.getThermoSystem().getBeta());
        // outStream.setThermoSystem(mixedStream.getThermoSystem());
    }

    @Override
	public void runTransient() {
    }

    @Override
	public Stream getGasOutStream() {
        return new Stream("", mixedStream.getThermoSystem().phaseToSystem(0));
    }

    @Override
	public Stream getLiquidOutStream() {
        return new Stream("", mixedStream.getThermoSystem().phaseToSystem(1));
    }

    /**
     * @return the temperature
     */
    @Override
	public double getTemperature() {
        return temperature;
    }

    /**
     * @param temperature the temperature to set
     */
    @Override
	public void setTemperature(double temperature) {
        this.temperature = temperature;
    }
}
