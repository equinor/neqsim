/*
 * Heater.java
 *
 * Created on 15. mars 2001, 14:17
 */
package neqsim.processSimulation.processEquipment.heatExchanger;

import neqsim.processSimulation.processEquipment.ProcessEquipmentBaseClass;
import neqsim.processSimulation.processEquipment.ProcessEquipmentInterface;
import neqsim.processSimulation.processEquipment.stream.EnergyStream;
import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.processSimulation.processEquipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 *
 * @author Even Solbraa
 * @version
 */
public class Heater extends ProcessEquipmentBaseClass implements ProcessEquipmentInterface, HeaterInterface {

    private static final long serialVersionUID = 1000;

    boolean setTemperature = false, setOutPressure = false;
    private StreamInterface outStream;
    StreamInterface inStream;
    SystemInterface system;
    protected double temperatureOut = 0, dT = 0.0, pressureOut = 0;
    private boolean setEnergyInput = false;
    private double energyInput = 0.0;
    private double pressureDrop = 0.0;
    private String temperatureUnit = "K";
    private String pressureUnit = "bara";
    double coolingMediumTemperature = 278.15;

    /**
     * Creates new Heater
     */
    public Heater() {
    }

    public Heater(StreamInterface inStream) {
        this.inStream = inStream;
        system = (SystemInterface) inStream.getThermoSystem().clone();
        outStream = new Stream(system);
    }

    public Heater(String name, StreamInterface inStream) {
        super(name);
        this.inStream = inStream;
        system = (SystemInterface) inStream.getThermoSystem().clone();
        outStream = new Stream(system);
    }

    public StreamInterface getInStream() {
        return inStream;
    }

    public void setdT(double dT) {
        this.dT = dT;
    }

    public StreamInterface getOutStream() {
        return outStream;
    }

    public void setOutPressure(double pressure) {
        setOutPressure = true;
        this.pressureOut = pressure;
    }

    public void setOutPressure(double pressure, String unit) {
        setOutPressure = true;
        this.pressureOut = pressure;
        this.pressureUnit = unit;
    }

    public void setOutTemperature(double temperature) {
        setTemperature = true;
        setEnergyInput = false;
        this.temperatureOut = temperature;
    }

    public void setOutTemperature(double temperature, String unit) {
        setTemperature = true;
        setEnergyInput = false;
        this.temperatureUnit = unit;
        this.temperatureOut = temperature;
    }

    public void setOutTP(double temperature, double pressure) {
        setTemperature = true;
        setEnergyInput = false;
        this.temperatureOut = temperature;
        setOutPressure = true;
        this.pressureOut = pressure;
    }

    public void run() {
        system = (SystemInterface) inStream.getThermoSystem().clone();
        system.init(3);
        double oldH = system.getEnthalpy();
        if (isSetEnergyStream()) {
            energyInput = -energyStream.getDuty();
        }
        double newEnthalpy = energyInput + oldH;
        system.setPressure(system.getPressure() - pressureDrop, pressureUnit);
        if (setOutPressure) {
            system.setPressure(pressureOut, pressureUnit);
        }
        ThermodynamicOperations testOps = new ThermodynamicOperations(system);
        if (getSpecification().equals("out stream")) {
            getOutStream().setFlowRate(getInStream().getFlowRate("kg/sec"), "kg/sec");
            getOutStream().run();
            temperatureOut = getOutStream().getTemperature();
            system = (SystemInterface) getOutStream().getThermoSystem().clone();
        } else if (setTemperature) {
            system.setTemperature(temperatureOut, temperatureUnit);
            testOps.TPflash();
        } else if (setEnergyInput || isSetEnergyStream()) {
            testOps.PHflash(newEnthalpy, 0);
        } else {
            // System.out.println("temperaturee out " + inStream.getTemperature());
            system.setTemperature(inStream.getTemperature() + dT, temperatureUnit);
            testOps.TPflash();
        }

        // system.setTemperature(temperatureOut);
        system.init(3);
        double newH = system.getEnthalpy();
        energyInput = newH - oldH;
        if (!isSetEnergyStream()) {
            getEnergyStream().setDuty(energyInput);
        }
        // system.setTemperature(temperatureOut);
        // testOps.TPflash();
        // system.setTemperature(temperatureOut);
        getOutStream().setThermoSystem(system);
    }

    public void displayResult() {
        // System.out.println("heater dH: " + energyInput);
        getOutStream().displayResult();
    }

    public String getName() {
        return name;
    }

    public void runTransient() {
        run();
    }

    public double getEnergyInput() {
        return energyInput;
    }

    public double getDuty() {
        return energyInput;
    }

    public void setEnergyInput(double energyInput) {
        this.energyInput = energyInput;
        setTemperature = false;
        setEnergyInput = true;
    }

    public void setDuty(double energyInput) {
        setEnergyInput(energyInput);
    }

    public boolean isSetEnergyInput() {
        return setEnergyInput;
    }

    public void setSetEnergyInput(boolean setEnergyInput) {
        this.setEnergyInput = setEnergyInput;
    }

    /**
     * @return the pressureDrop
     */
    public double getPressureDrop() {
        return pressureDrop;
    }

    /**
     * @param pressureDrop the pressureDrop to set
     */
    public void setPressureDrop(double pressureDrop) {
        this.pressureDrop = pressureDrop;
    }

    /**
     * @param outStream the outStream to set
     */
    public void setOutStream(Stream outStream) {
        this.outStream = outStream;
    }

    public double getEntropyProduction(String unit) {
        //
        double entrop = 0.0;

        inStream.run();
        inStream.getFluid().init(3);
        outStream.run();
        outStream.getFluid().init(3);

        entrop += outStream.getThermoSystem().getEntropy(unit) - inStream.getThermoSystem().getEntropy(unit);

        return entrop;
    }

    public double getExergyChange(String unit, double sourrondingTemperature) {
        double entrop = 0.0;

        inStream.run();
        inStream.getFluid().init(3);
        outStream.run();
        outStream.getFluid().init(3);

        entrop += outStream.getThermoSystem().getExergy(sourrondingTemperature, unit)
                - inStream.getThermoSystem().getExergy(sourrondingTemperature, unit);

        return entrop;

    }

}
