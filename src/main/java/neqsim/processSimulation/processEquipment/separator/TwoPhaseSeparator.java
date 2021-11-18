/*
 * Separator.java
 *
 * Created on 12. mars 2001, 19:48
 */

package neqsim.processSimulation.processEquipment.separator;

import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.processSimulation.processEquipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;

/**
 * @author Even Solbraa
 * @version
 */
public class TwoPhaseSeparator extends Separator {
    private static final long serialVersionUID = 1000;

    SystemInterface thermoSystem, gasSystem, waterSystem, liquidSystem, thermoSystemCloned;
    StreamInterface inletStream;
    StreamInterface gasOutStream;
    StreamInterface liquidOutStream;
    String name = new String();

    /** Creates new Separator */
    public TwoPhaseSeparator() {}

    public TwoPhaseSeparator(StreamInterface inletStream) {
        this.setInletStream(inletStream);
    }

    public TwoPhaseSeparator(String name, StreamInterface inletStream) {
        this.name = name;
        this.setInletStream(inletStream);
    }

    @Override
    public void setName(String name) {
        this.name = name;
    }

    @Override
    public void setInletStream(StreamInterface inletStream) {
        this.inletStream = inletStream;

        thermoSystem = (SystemInterface) inletStream.getThermoSystem().clone();
        gasSystem = thermoSystem.phaseToSystem(thermoSystem.getPhases()[0]);
        gasOutStream = new Stream(gasSystem);

        thermoSystem = (SystemInterface) inletStream.getThermoSystem().clone();
        liquidSystem = thermoSystem.phaseToSystem(thermoSystem.getPhases()[1]);
        liquidOutStream = new Stream(liquidSystem);
    }

    @Override
    public StreamInterface getLiquidOutStream() {
        return liquidOutStream;
    }

    @Override
    public StreamInterface getGasOutStream() {
        return gasOutStream;
    }

    @Override
    public StreamInterface getGas() {
        return getGasOutStream();
    }

    @Override
    public StreamInterface getLiquid() {
        return getLiquidOutStream();
    }

    @Override
    public void run() {
        thermoSystem = (SystemInterface) inletStream.getThermoSystem().clone();
        gasSystem = thermoSystem.phaseToSystem(thermoSystem.getPhases()[0]);
        gasSystem.setNumberOfPhases(1);
        gasOutStream.setThermoSystem(gasSystem);

        thermoSystem = (SystemInterface) inletStream.getThermoSystem().clone();
        liquidSystem = thermoSystem.phaseToSystem(thermoSystem.getPhases()[1]);
        liquidSystem.setNumberOfPhases(1);
        liquidOutStream.setThermoSystem(liquidSystem);
    }

    @Override
    public void displayResult() {}

    @Override
    public String getName() {
        return name;
    }

    public void runTransient() {}
}
