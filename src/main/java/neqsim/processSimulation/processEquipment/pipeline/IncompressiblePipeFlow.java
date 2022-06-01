package neqsim.processSimulation.processEquipment.pipeline;

import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.processSimulation.processEquipment.stream.StreamInterface;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 * <p>
 * IncompressiblePipeFlow class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class IncompressiblePipeFlow extends AdiabaticPipe {
    private static final long serialVersionUID = 1000;

    Fittings fittings = new Fittings();
    private double totalEqLenth = 0;
    double momentum = 0;

    /**
     * <p>
     * Constructor for IncompressiblePipeFlow.
     * </p>
     */
    @Deprecated
    public IncompressiblePipeFlow() {
        super("IncompressiblePipeFlow");
    }

    /**
     * <p>
     * Constructor for IncompressiblePipeFlow.
     * </p>
     *
     * @param inStream a {@link neqsim.processSimulation.processEquipment.stream.StreamInterface}
     *        object
     */
    @Deprecated
    public IncompressiblePipeFlow(StreamInterface inStream) {
        this("IncompressiblePipeFlow", inStream);
    }

    /**
     * Constructor for IncompressiblePipeFlow.
     * 
     * @param name
     */
    public IncompressiblePipeFlow(String name) {
        super(name);
    }

    /**
     * * Constructor for IncompressiblePipeFlow.
     * 
     * @param name
     * @param inStream
     */
    public IncompressiblePipeFlow(String name, StreamInterface inStream) {
        super(name, inStream);
    }

    /**
     * <p>
     * addFittingFromDatabase.
     * </p>
     *
     * @param name a {@link java.lang.String} object
     */
    public void addFittingFromDatabase(String name) {
        fittings.add(name);
    }

    /**
     * <p>
     * addFitting.
     * </p>
     *
     * @param name a {@link java.lang.String} object
     * @param LdivD a double
     */
    public void addFitting(String name, double LdivD) {
        fittings.add(name, LdivD);
    }

    /** {@inheritDoc} */
    @Override
    public double calcPressureOut() {
        setTotalEqLenth(length);

        for (int i = 0; i < fittings.fittingList.size(); i++) {
            setTotalEqLenth(getTotalEqLenth() + fittings.getFittingsList().get(i).getLtoD());
        }

        double area = 3.14 / 4.0 * Math.pow(insideDiameter, 2.0);
        double velocity = 1.0 / system.getPhase(0).getPhysicalProperties().getDensity()
                / (system.getPhase(0).getNumberOfMolesInPhase()
                        * (system.getPhase(0).getMolarVolume() / 1e5))
                / area;

        momentum = system.getPhase(0).getPhysicalProperties().getDensity() * velocity * velocity;
        double reynoldsNumber = velocity * insideDiameter
                / system.getPhase(0).getPhysicalProperties().getKinematicViscosity();
        double frictionFactor = calcWallFrictionFactor(reynoldsNumber);

        double dp = -momentum * frictionFactor * getTotalEqLenth() / (2.0 * insideDiameter);
        dp += (getInletElevation() - getOutletElevation())
                * system.getPhase(0).getPhysicalProperties().getDensity()
                * neqsim.thermo.ThermodynamicConstantsInterface.gravity;

        // double dp = Math.pow(4.0 * system.getPhase(0).getNumberOfMolesInPhase() *
        // system.getPhase(0).getMolarMass()/thermo.ThermodynamicConstantsInterface.pi,
        // 2.0) * frictionFactor * length * system.getPhase(0).getZ() *
        // thermo.ThermodynamicConstantsInterface.R/system.getPhase(0).getMolarMass() *
        // system.getTemperature() / Math.pow(insideDiameter, 5.0);
        System.out.println("outpres " + ((system.getPressure() * 1e5 + dp) / 1.0e5) + " dp " + dp
                + " friction fact" + frictionFactor + " velocity " + velocity + " reynolds number "
                + reynoldsNumber + " equivalentLength " + getTotalEqLenth());

        return (system.getPressure() * 1e5 + dp) / 1.0e5;
    }

    /** {@inheritDoc} */
    @Override
    public void run() {
        system = inStream.getThermoSystem().clone();
        // system.setMultiPhaseCheck(true);
        if (setTemperature) {
            system.setTemperature(this.temperatureOut);
        }
        system.init(3);
        system.initPhysicalProperties();
        calcPressureOut();
        system.setPressure(calcPressureOut());

        ThermodynamicOperations testOps = new ThermodynamicOperations(system);
        testOps.TPflash();
        // system.setMultiPhaseCheck(false);
        outStream.setThermoSystem(system);
    }

    /**
     * <p>
     * main.
     * </p>
     *
     * @param name an array of {@link java.lang.String} objects
     */
    public static void main(String[] name) {
        neqsim.thermo.system.SystemInterface testSystem =
                new neqsim.thermo.system.SystemSrkEos((273.15 + 25.0), 10.00);
        testSystem.addComponent("water", 100.0 * 1e3, "kg/hr");
        testSystem.createDatabase(true);
        testSystem.setMixingRule(2);
        testSystem.initPhysicalProperties();
        Stream stream_1 = new Stream("Stream1", testSystem);

        IncompressiblePipeFlow pipe = new IncompressiblePipeFlow(stream_1);
        pipe.setLength(1000.0);
        pipe.setDiameter(0.25);
        pipe.setPipeWallRoughness(2e-5);
        pipe.addFittingFromDatabase("Standard elbow (R=1.5D), 90deg");

        IncompressiblePipeFlow pipe2 = new IncompressiblePipeFlow(pipe.getOutletStream());
        pipe2.setLength(1000.0);
        pipe2.setDiameter(0.25);
        pipe2.setPipeWallRoughness(2e-5);
        pipe2.setInletElevation(10);
        pipe2.setOutletElevation(0);

        neqsim.processSimulation.processSystem.ProcessSystem operations =
                new neqsim.processSimulation.processSystem.ProcessSystem();
        operations.add(stream_1);
        operations.add(pipe);
        operations.add(pipe2);
        operations.run();
        pipe.displayResult();
    }

    /**
     * <p>
     * Getter for the field <code>totalEqLenth</code>.
     * </p>
     *
     * @return the totalEqLenth
     */
    public double getTotalEqLenth() {
        return totalEqLenth;
    }

    /**
     * <p>
     * Setter for the field <code>totalEqLenth</code>.
     * </p>
     *
     * @param totalEqLenth the totalEqLenth to set
     */
    public void setTotalEqLenth(double totalEqLenth) {
        this.totalEqLenth = totalEqLenth;
    }
}
