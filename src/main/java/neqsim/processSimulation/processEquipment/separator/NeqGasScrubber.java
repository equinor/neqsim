/*
 * Separator.java
 *
 * Created on 12. mars 2001, 19:48
 */
package neqsim.processSimulation.processEquipment.separator;

import java.util.ArrayList;

import neqsim.processSimulation.mechanicalDesign.separator.GasScrubberMechanicalDesign;
import neqsim.processSimulation.processEquipment.separator.sectionType.SeparatorSection;
import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;

/**
 * @author  Even Solbraa
 * @version
 */
public class NeqGasScrubber extends Separator {

    private static final long serialVersionUID = 1000;

    SystemInterface thermoSystem, gasSystem, waterSystem, liquidSystem, thermoSystemCloned;
    ArrayList<SeparatorSection> scrubberSection = null;
    Stream inletStream;
    Stream gasOutStream;
    Stream liquidOutStream;
    String name = new String();

    /**
     * Creates new Separator
     */
    public NeqGasScrubber() {
        super();
        mechanicalDesign = new GasScrubberMechanicalDesign(this);
        this.setOrientation("vertical");
    }

    public NeqGasScrubber(Stream inletStream) {
        this();
        this.setInletStream(inletStream);
    }

    public NeqGasScrubber(String name, Stream inletStream) {
        this();
        this.name = name;
        this.setInletStream(inletStream);
    }

    @Override
    public void setName(String name) {
        this.name = name;
    }

    public void setInletStream(Stream inletStream) {
        this.inletStream = inletStream;

        thermoSystem = (SystemInterface) inletStream.getThermoSystem().clone();
        gasSystem = thermoSystem.phaseToSystem(thermoSystem.getPhases()[0]);
        gasOutStream = new Stream(gasSystem);

        thermoSystem = (SystemInterface) inletStream.getThermoSystem().clone();
        liquidSystem = thermoSystem.phaseToSystem(thermoSystem.getPhases()[1]);
        liquidOutStream = new Stream(liquidSystem);
    }

    public void addScrubberSection(String type) {
        scrubberSection.add(new SeparatorSection(type, this));
    }

    @Override
    public Stream getLiquidOutStream() {
        return liquidOutStream;
    }

    @Override
    public Stream getGasOutStream() {
        return gasOutStream;
    }

    @Override
    public Stream getGas() {
        return getGasOutStream();
    }

    @Override
    public Stream getLiquid() {
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
    public void displayResult() {
    }

    @Override
    public String getName() {
        return name;
    }

    public void runTransient() {
    }
}
