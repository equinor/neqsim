/*
 * Separator.java
 *
 * Created on 12. mars 2001, 19:48
 */
package neqsim.processSimulation.processEquipment.splitter;

import neqsim.processSimulation.processEquipment.ProcessEquipmentBaseClass;
import neqsim.processSimulation.processEquipment.ProcessEquipmentInterface;
import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.processSimulation.processEquipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 *
 * @author Even Solbraa
 * @version
 */
public class Splitter extends ProcessEquipmentBaseClass implements ProcessEquipmentInterface, SplitterInterface {

    private static final long serialVersionUID = 1000;

    SystemInterface thermoSystem, gasSystem, waterSystem, liquidSystem, thermoSystemCloned;
    StreamInterface inletStream;
    StreamInterface[] splitStream;
    String name = new String();
    protected int splitNumber;
    double[] splitFactor = new double[1];

    /**
     * Creates new Separator
     */
    public Splitter() {
    }

    public Splitter(StreamInterface inletStream) {
        this.setInletStream(inletStream);
    }

    public Splitter(String name, StreamInterface inletStream, int i) {
        this(inletStream);
        this.name = name;
        setSplitNumber(i);
        splitFactor = new double[i];
    }

    public void setSplitNumber(int i) {
        splitNumber = i;
        this.setInletStream(inletStream);
        splitFactor = new double[splitNumber];
    }

    public void setSplitFactors(double[] splitFact) {
        splitFactor = splitFact;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setInletStream(StreamInterface inletStream) {
        this.inletStream = inletStream;
        splitStream = new Stream[splitNumber];
        try {
            for (int i = 0; i < splitNumber; i++) {
                System.out.println("splitting...." + i);
                splitStream[i] = new Stream("Split Stream", (SystemInterface) inletStream.getThermoSystem().clone());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public Stream getSplitStream(int i) {
        return (Stream) splitStream[i];
    }

    public void run() {
        for (int i = 0; i < splitNumber; i++) {
            thermoSystem = (SystemInterface) inletStream.getThermoSystem().clone();
            splitStream[i].setThermoSystem(thermoSystem);
            for (int j = 0; j < inletStream.getThermoSystem().getPhase(0).getNumberOfComponents(); j++) {
            	int index = inletStream.getThermoSystem().getPhase(0).getComponent(j).getComponentNumber();
                double moles = inletStream.getThermoSystem().getPhase(0).getComponent(j).getNumberOfmoles();
                splitStream[i].getThermoSystem().addComponent(index, moles * splitFactor[i] - moles);
            }
            ThermodynamicOperations thermoOps = new ThermodynamicOperations(splitStream[i].getThermoSystem());
            thermoOps.TPflash();
        }
    }

    public void displayResult() {
    }

    public String getName() {
        return name;
    }

    public void runTransient(double dt) {
    }

}
