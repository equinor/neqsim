/*
 * ThrottelValve.java
 *
 * Created on 22. august 2001, 17:20
 */
package neqsim.processSimulation.processEquipment.valve;

import neqsim.processSimulation.processEquipment.ProcessEquipmentBaseClass;
import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.processSimulation.processEquipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 *
 * @author esol
 * @version
 */
public class ThrottlingValve extends ProcessEquipmentBaseClass implements ValveInterface {

    private static final long serialVersionUID = 1000;

    protected String name = new String();
    private boolean valveCvSet = false, isoThermal = false;
    SystemInterface thermoSystem;
    StreamInterface inletStream;
    StreamInterface outStream;
    double pressure = 0.0;
    private double Cv = 1.0;
    private double maxMolarFlow = 1000.0;
    private double minMolarFlow = 0.0;
    private double percentValveOpening = 100.0;
    double molarFlow = 0.0;

    /**
     * Creates new ThrottelValve
     */
    public ThrottlingValve() {
    }

    public ThrottlingValve(StreamInterface inletStream) {
        setInletStream(inletStream);
    }

    public ThrottlingValve(String name, StreamInterface inletStream) {
        this.name = name;
        setInletStream(inletStream);
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setInletStream(StreamInterface inletStream) {
        this.inletStream = inletStream;

        thermoSystem = (SystemInterface) inletStream.getThermoSystem().clone();
        outStream = new Stream(thermoSystem);
    }
    
    public double getOutletPressure() {
    	return this.pressure;
    }
    
    public SystemInterface getThermoSystem() {
    	return thermoSystem;
    }
    
    public double getInletPressure() {
    	return inletStream.getThermoSystem().getPressure();
    }

    public void setOutletPressure(double pressure) {
        this.pressure = pressure;
        getOutStream().getThermoSystem().setPressure(pressure);
    }

    public StreamInterface getOutStream() {
        return outStream;
    }

    public void run() {
        
        //System.out.println("valve running..");
        //outStream.setSpecification(inletStream.getSpecification());
        thermoSystem = (SystemInterface) inletStream.getThermoSystem().clone();
        ThermodynamicOperations thermoOps = new ThermodynamicOperations(thermoSystem);
        thermoSystem.init(3);
        double enthalpy = thermoSystem.getEnthalpy();
        thermoSystem.setPressure(pressure);
        if(specification.equals("out stream")) {
            thermoSystem.setPressure(outStream.getPressure());
        }
       // System.out.println("enthalpy inn.." + enthalpy);
        //thermoOps.PHflash(enthalpy, 0);
        if (isIsoThermal()) {
            thermoOps.TPflash();
        } else {
            thermoOps.PHflash(enthalpy, 0);
        }
        outStream.setThermoSystem(thermoSystem);
      //  System.out.println("Total volume flow " + outStream.getThermoSystem().getVolume());
      //  System.out.println("density valve " + inletStream.getThermoSystem().getDensity());

        if (!valveCvSet) {
            Cv = inletStream.getThermoSystem().getTotalNumberOfMoles() / (getPercentValveOpening() / 100.0 * Math.sqrt((inletStream.getThermoSystem().getPressure() - outStream.getThermoSystem().getPressure()) / thermoSystem.getDensity()));
        }
        molarFlow = getCv() * getPercentValveOpening() / 100.0 * Math.sqrt((inletStream.getThermoSystem().getPressure() - outStream.getThermoSystem().getPressure()) / thermoSystem.getDensity());
//molarFlow=inletStream.getThermoSystem().getTotalNumberOfMoles();
       // System.out.println("molar flow " + molarFlow);

        inletStream.getThermoSystem().setTotalNumberOfMoles(molarFlow);
        inletStream.getThermoSystem().init(1);
        //inletStream.run();

        outStream.setThermoSystem((SystemInterface) thermoSystem.clone());
        outStream.getThermoSystem().setTotalNumberOfMoles(molarFlow);
        outStream.getThermoSystem().init(1);
        //outStream.run();
//        Cv = inletStream.getThermoSystem().getTotalNumberOfMoles()/Math.sqrt(inletStream.getPressure()-outStream.getPressure());
//        molarFlow = inletStream.getThermoSystem().getTotalNumberOfMoles();
    }

    public void displayResult() {
        thermoSystem.display(getName());
    }

    public String getName() {
        return name;
    }

    public void runTransient(double dt) {
        runController(dt);

        thermoSystem = (SystemInterface) inletStream.getThermoSystem().clone();
        ThermodynamicOperations thermoOps = new ThermodynamicOperations(thermoSystem);
        thermoSystem.init(3);

        double enthalpy = thermoSystem.getEnthalpy();
        thermoSystem.setPressure(getOutStream().getThermoSystem().getPressure());
        //System.out.println("enthalpy inn.." + enthalpy);
        if (isIsoThermal()) {
            thermoOps.TPflash();
        } else {
            thermoOps.PHflash(enthalpy, 0);
        }
        outStream.setThermoSystem(thermoSystem);
        // if(getPercentValveOpening()<99){
        molarFlow = getCv() * getPercentValveOpening() / 100.0 * Math.sqrt((inletStream.getThermoSystem().getPressure() - outStream.getThermoSystem().getPressure()) / thermoSystem.getDensity());
       // System.out.println("molar flow " + molarFlow);
       // System.out.println("Cv " + getCv());
       // System.out.println("density " + inletStream.getThermoSystem().getDensity());
//
        //       8   } else {
        //      molarFlow=inletStream.getThermoSystem().getTotalNumberOfMoles();
//        }

        inletStream.getThermoSystem().setTotalNumberOfMoles(molarFlow);
        inletStream.getThermoSystem().init(1);
        inletStream.run();

        outStream.getThermoSystem().setTotalNumberOfMoles(molarFlow);
        outStream.getThermoSystem().init(1);
        outStream.run();

     //   System.out.println("delta p valve " + (inletStream.getThermoSystem().getPressure() - outStream.getThermoSystem().getPressure()));
     //   System.out.println("total molar flow out " + molarFlow);
     //   System.out.println("Total volume flow " + outStream.getThermoSystem().getVolume());
    }

    public void runController(double dt) {
        if (hasController) {
            getController().run(this.percentValveOpening, dt);
            this.percentValveOpening = getController().getResponse();
            if (this.percentValveOpening > 100) {
                this.percentValveOpening = 100;
            }
            if (this.percentValveOpening < 0) {
                this.percentValveOpening = 1e-10;
            }
            System.out.println("valve opening " + this.percentValveOpening + " %");
        }
    }

    public double getCv() {
        return Cv;
    }

    public void setCv(double Cv) {
        this.Cv = Cv;
        valveCvSet = true;
    }

    public double getPercentValveOpening() {
        return percentValveOpening;
    }

    public void setPercentValveOpening(double percentValveOpening) {
        this.percentValveOpening = percentValveOpening;
    }

    public boolean isValveCvSet() {
        return valveCvSet;
    }

    public void setValveCvSet(boolean valveCvSet) {
        this.valveCvSet = valveCvSet;
    }

    public boolean isIsoThermal() {
        return isoThermal;
    }

    public void setIsoThermal(boolean isoThermal) {
        this.isoThermal = isoThermal;
    }
}
