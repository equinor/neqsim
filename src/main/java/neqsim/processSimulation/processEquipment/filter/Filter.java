package neqsim.processSimulation.processEquipment.filter;

import neqsim.processSimulation.processEquipment.ProcessEquipmentBaseClass;
import neqsim.processSimulation.processEquipment.ProcessEquipmentInterface;
import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.processSimulation.processEquipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 * <p>
 * Filter class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class Filter extends ProcessEquipmentBaseClass {
    private static final long serialVersionUID = 1000;

    private double deltaP = 0.01;
    protected StreamInterface outStream;
    protected StreamInterface inStream;
    private double Cv = 0.0;

    /**
     * <p>
     * Constructor for Filter.
     * </p>
     *
     * @param inStream a {@link neqsim.processSimulation.processEquipment.stream.StreamInterface}
     *        object
     */
    public Filter(StreamInterface inStream) {
        this.inStream = inStream;
        outStream = (Stream) inStream.clone();
    }

    /** {@inheritDoc} */
    @Override
    public void run() {
        SystemInterface system = inStream.getThermoSystem().clone();
        if (Math.abs(getDeltaP()) > 1e-10) {
            system.setPressure(inStream.getPressure() - getDeltaP());
            ThermodynamicOperations testOps = new ThermodynamicOperations(system);
            testOps.TPflash();
        }
        system.initProperties();
        outStream.setThermoSystem(system);
        Cv = Math.sqrt(deltaP) / inStream.getFlowRate("kg/hr");
    }

    /**
     * <p>
     * Getter for the field <code>deltaP</code>.
     * </p>
     *
     * @return a double
     */
    public double getDeltaP() {
        return deltaP;
    }

    /**
     * <p>
     * Setter for the field <code>deltaP</code>.
     * </p>
     *
     * @param deltaP a double
     */
    public void setDeltaP(double deltaP) {
        this.deltaP = deltaP;
        this.outStream.setPressure(this.inStream.getPressure() - deltaP);
    }

    /**
     * <p>
     * Setter for the field <code>deltaP</code>.
     * </p>
     *
     * @param deltaP a double
     * @param unit a {@link java.lang.String} object
     */
    public void setDeltaP(double deltaP, String unit) {
        this.deltaP = deltaP;
        this.outStream.setPressure(this.inStream.getPressure(unit) - deltaP, unit);
    }

    /**
     * <p>
     * Getter for the field <code>outStream</code>.
     * </p>
     *
     * @return a {@link neqsim.processSimulation.processEquipment.stream.StreamInterface} object
     */
    public StreamInterface getOutStream() {
        return outStream;
    }

    /** {@inheritDoc} */
    @Override
    public void runConditionAnalysis(ProcessEquipmentInterface refTEGabsorberloc) {
        double deltaP = inStream.getPressure("bara") - outStream.getPressure("bara");
        Cv = Math.sqrt(deltaP) / inStream.getFlowRate("kg/hr");
    }

    /**
     * <p>
     * getCvFactor.
     * </p>
     *
     * @return a double
     */
    public double getCvFactor() {
        return Cv;
    }

    /**
     * <p>
     * setCvFactor.
     * </p>
     *
     * @param pressureCoef a double
     */
    public void setCvFactor(double pressureCoef) {
        this.Cv = pressureCoef;
    }

    /** {@inheritDoc} */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        // TODO Auto-generated method stub
        return false;
    }
}
