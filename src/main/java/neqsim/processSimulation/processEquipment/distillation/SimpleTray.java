package neqsim.processSimulation.processEquipment.distillation;

import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.processSimulation.processEquipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 *
 * @author ESOL
 */
public class SimpleTray extends neqsim.processSimulation.processEquipment.mixer.Mixer
        implements TrayInterface {
    private static final long serialVersionUID = 1000;

    double heatInput = 0.0;
    private double temperature = Double.NaN, trayPressure = -1.0;

    public SimpleTray() {}

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

    public double calcMixStreamEnthalpy0() {
        double enthalpy = 0;

        for (int k = 0; k < streams.size(); k++) {
            streams.get(k).getThermoSystem().init(3);
            enthalpy += streams.get(k).getThermoSystem().getEnthalpy();
            // System.out.println("total enthalpy k : " + ((SystemInterface) ((Stream)
            // streams.get(k)).getThermoSystem()).getEnthalpy());
        }
        // System.out.println("total enthalpy of streams: " + enthalpy);
        return enthalpy;
    }

    @Override
    public double calcMixStreamEnthalpy() {
        double enthalpy = heatInput;
        if (isSetEnergyStream()) {
            enthalpy -= energyStream.getDuty();
        }

        for (int k = 0; k < streams.size(); k++) {
            streams.get(k).getThermoSystem().init(3);
            enthalpy += streams.get(k).getThermoSystem().getEnthalpy();
            // System.out.println("total enthalpy k : " + ((SystemInterface) ((Stream)
            // streams.get(k)).getThermoSystem()).getEnthalpy());
        }
        // System.out.println("total enthalpy of streams: " + enthalpy);
        return enthalpy;
    }

    public void run2() {
        super.run();
        temperature = mixedStream.getTemperature();
    }

    public void TPflash() {}

    @Override
    public void run() {
        double enthalpy = 0.0;
        double flowRate = ((Stream) streams.get(0)).getThermoSystem().getFlowRate("kg/hr");
        // ((Stream) streams.get(0)).getThermoSystem().display();
        SystemInterface thermoSystem2 = (SystemInterface) streams.get(0).getThermoSystem().clone();

        // System.out.println("total number of moles " +
        // thermoSystem2.getTotalNumberOfMoles());
        if (trayPressure > 0) {
            thermoSystem2.setPressure(trayPressure);
        }
        mixedStream.setThermoSystem(thermoSystem2);
        // thermoSystem2.display();
        ThermodynamicOperations testOps = new ThermodynamicOperations(thermoSystem2);
        if (streams.size() > 0) {
            mixedStream.getThermoSystem().setNumberOfPhases(2);
            mixedStream.getThermoSystem().reInitPhaseType();
            mixedStream.getThermoSystem().init(0);

            mixStream();
            if (trayPressure > 0)
                mixedStream.setPressure(trayPressure, "bara");
            enthalpy = calcMixStreamEnthalpy();
            // System.out.println("temp guess " + guessTemperature());
            if (!isSetOutTemperature()) {
                // mixedStream.getThermoSystem().setTemperature(guessTemperature());
            } else {
                mixedStream.setTemperature(getOutTemperature(), "K");
            }
            // System.out.println("filan temp " + mixedStream.getTemperature());
        }
        if (isSetOutTemperature()) {
            if (!Double.isNaN(getOutTemperature()))
                mixedStream.getThermoSystem().setTemperature(getOutTemperature());
            testOps.TPflash();
            mixedStream.getThermoSystem().init(2);
        } else {
            try {
                testOps.PHflash(enthalpy, 0);
            } catch (Exception e) {
                if (!Double.isNaN(getOutTemperature()))
                    mixedStream.getThermoSystem().setTemperature(getOutTemperature());
                testOps.TPflash();
            }
        }
        setTemperature(mixedStream.getTemperature());
    }

    @Override
    public void runTransient() {}

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

    @Override
    public void setPressure(double pres) {
        trayPressure = pres;
    }

    /**
     * @param temperature the temperature to set
     */
    @Override
    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    @Override
    public double guessTemperature() {
        if (Double.isNaN(temperature)) {
            double gtemp = 0;
            for (int k = 0; k < streams.size(); k++) {
                gtemp += streams.get(k).getThermoSystem().getTemperature()
                        * streams.get(k).getThermoSystem().getNumberOfMoles()
                        / mixedStream.getThermoSystem().getNumberOfMoles();
            }
            // System.out.println("guess temperature " + gtemp);
            return gtemp;
        } else {
            // System.out.println("temperature " + temperature);
            return temperature;
        }
    }
}
