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

    @Override
	public double getEntropyProduction(String unit) {
        //
        double entrop = 0.0;

        inStream.run();
        inStream.getFluid().init(3);
        getOutStream().run();
        getOutStream().getFluid().init(3);

        double heatTransferEntropyProd = coolingMediumTemperature * getDuty();
        System.out.println("heat entropy " + heatTransferEntropyProd);
        entrop += getOutStream().getThermoSystem().getEntropy(unit) - inStream.getThermoSystem().getEntropy(unit);

        return entrop;
    }

}
