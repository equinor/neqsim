package neqsim.processSimulation.processEquipment.heatExchanger;

import neqsim.processSimulation.processEquipment.stream.StreamInterface;

/**
 * <p>
 * Cooler class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class Cooler extends Heater {
    private static final long serialVersionUID = 1000;

    @Deprecated
    public Cooler(StreamInterface stream) {
        setInletStream(stream);
    }

    /**
     * <p>
     * Constructor for Cooler.
     * </p>
     *
     * @param name a {@link java.lang.String} object
     * @param inStream a {@link neqsim.processSimulation.processEquipment.stream.StreamInterface}
     *        object
     */
    public Cooler(String name, StreamInterface inStream) {
        super(name, inStream);
    }

    /** {@inheritDoc} */
    @Override
    public double getEntropyProduction(String unit) {
        double entrop = 0.0;

        inStream.run();
        inStream.getFluid().init(3);
        getOutletStream().run();
        getOutletStream().getFluid().init(3);

        double heatTransferEntropyProd = coolingMediumTemperature * getDuty();
        System.out.println("heat entropy " + heatTransferEntropyProd);
        entrop += getOutletStream().getThermoSystem().getEntropy(unit)
                - inStream.getThermoSystem().getEntropy(unit);

        return entrop;
    }
}
