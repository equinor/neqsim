package neqsim.processSimulation.processEquipment.reservoir;


import neqsim.processSimulation.processEquipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 * <p>Well class.</p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class Well implements java.io.Serializable {

    private StreamInterface stream = null;
    private String name;
    double x, y, z;

    /**
     * <p>Constructor for Well.</p>
     */
    public Well() {
    }

    /**
     * <p>Constructor for Well.</p>
     *
     * @param name a {@link java.lang.String} object
     */
    public Well(String name) {
        this.setName(name);
    }

    /**
     * <p>Getter for the field <code>stream</code>.</p>
     *
     * @return a {@link neqsim.processSimulation.processEquipment.stream.StreamInterface} object
     */
    public StreamInterface getStream() {
        return stream;
    }

    /**
     * <p>Setter for the field <code>stream</code>.</p>
     *
     * @param stream a {@link neqsim.processSimulation.processEquipment.stream.StreamInterface} object
     */
    public void setStream(StreamInterface stream) {
        this.stream = stream;
    }

    /**
     * <p>getGOR.</p>
     *
     * @return a double
     */
    public double getGOR() {
        SystemInterface locStream = (SystemInterface) (stream.getFluid()).clone();
        locStream.setTemperature(288.15);
        locStream.setPressure(1.01325);
        ThermodynamicOperations ops = new ThermodynamicOperations(locStream);
        ops.TPflash();
        double GOR = Double.NaN;
        if (locStream.hasPhaseType("gas") && locStream.hasPhaseType("oil")) {
            GOR = locStream.getPhase("gas").getVolume("m3") / locStream.getPhase("oil").getVolume("m3");
        }
        return GOR;
    }

    /**
     * <p>getStdGasProduction.</p>
     *
     * @return a double
     */
    public double getStdGasProduction() {
        SystemInterface locStream = (SystemInterface) (stream.getFluid()).clone();
        locStream.setTemperature(288.15);
        locStream.setPressure(1.01325);
        ThermodynamicOperations ops = new ThermodynamicOperations(locStream);
        ops.TPflash();
        double volume = Double.NaN;
        if (locStream.hasPhaseType("gas")) {
            volume = locStream.getPhase("gas").getVolume("m3");
        }
        return volume;
    }

    /**
     * <p>getStdOilProduction.</p>
     *
     * @return a double
     */
    public double getStdOilProduction() {
        SystemInterface locStream = (SystemInterface) (stream.getFluid()).clone();
        locStream.setTemperature(288.15);
        locStream.setPressure(1.01325);
        ThermodynamicOperations ops = new ThermodynamicOperations(locStream);
        ops.TPflash();
        double volume = Double.NaN;
        if (locStream.hasPhaseType("oil")) {
            volume = locStream.getPhase("oil").getVolume("m3");
        }
        return volume;
    }

    /**
     * <p>Getter for the field <code>name</code>.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public String getName() {
        return name;
    }

    /**
     * <p>Setter for the field <code>name</code>.</p>
     *
     * @param name a {@link java.lang.String} object
     */
    public void setName(String name) {
        this.name = name;
    }
}
