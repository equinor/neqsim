/*
 * Heater.java
 *
 * Created on 15. mars 2001, 14:17
 */
package neqsim.processSimulation.processEquipment.heatExchanger;

import neqsim.processSimulation.processEquipment.stream.StreamInterface;

/**
 *
 * @author Even Solbraa
 * @version
 */
public class Cooler extends Heater {

    private static final long serialVersionUID = 1000;

    public Cooler() {
        super();
    }

    public Cooler(StreamInterface inStream) {
        super(inStream);
    }

    public Cooler(String name, StreamInterface inStream) {
        super(name, inStream);
    }

}
