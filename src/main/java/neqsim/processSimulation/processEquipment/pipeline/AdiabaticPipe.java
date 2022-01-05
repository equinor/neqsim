package neqsim.processSimulation.processEquipment.pipeline;

import neqsim.fluidMechanics.flowSystem.FlowSystemInterface;
import neqsim.processSimulation.mechanicalDesign.pipeline.PipelineMechanicalDeisgn;
import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.processSimulation.processEquipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 * <p>
 * AdiabaticPipe class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class AdiabaticPipe extends Pipeline {
    private static final long serialVersionUID = 1000;

    double inletPressure = 0;
    boolean setTemperature = false, setPressureOut = false;
    protected double temperatureOut = 270, pressureOut = 0.0;
    double length = 100.0;
    double insideDiameter = 0.1;
    double velocity = 1.0;
    double pipeWallRoughness = 1e-5;
    private double inletElevation = 0;
    private double outletElevation = 0;
    double dH = 0.0;
    String flowPattern = "unknown";
    String pipeSpecification = "AP02";

    /**
     * <p>
     * Constructor for AdiabaticPipe.
     * </p>
     */
    public AdiabaticPipe() {
        mechanicalDesign = new PipelineMechanicalDeisgn(this);
    }

    /**
     * <p>
     * Setter for the field <code>pipeSpecification</code>.
     * </p>
     *
     * @param nominalDiameter a double
     * @param pipeSec a {@link java.lang.String} object
     */
    public void setPipeSpecification(double nominalDiameter, String pipeSec) {
        pipeSpecification = pipeSec;
        insideDiameter = nominalDiameter / 1000.0;
    }

    /**
     * <p>
     * Constructor for AdiabaticPipe.
     * </p>
     *
     * @param inStream a {@link neqsim.processSimulation.processEquipment.stream.StreamInterface}
     *        object
     */
    public AdiabaticPipe(StreamInterface inStream) {
        this.inStream = inStream;
        outStream = (Stream) inStream.clone();
    }

    /** {@inheritDoc} */
    @Override
    public void setName(String name) {
        this.name = name;
    }

    /** {@inheritDoc} */
    @Override
    public StreamInterface getOutStream() {
        return outStream;
    }

    /**
     * <p>
     * setOutTemperature.
     * </p>
     *
     * @param temperature a double
     */
    public void setOutTemperature(double temperature) {
        setTemperature = true;
        this.temperatureOut = temperature;
    }

    /**
     * <p>
     * setOutPressure.
     * </p>
     *
     * @param pressure a double
     */
    public void setOutPressure(double pressure) {
        setPressureOut = true;
        this.pressureOut = pressure;
    }

    /**
     * <p>
     * calcWallFrictionFactor.
     * </p>
     *
     * @param reynoldsNumber a double
     * @return a double
     */
    public double calcWallFrictionFactor(double reynoldsNumber) {
        double relativeRoughnes = getPipeWallRoughness() / insideDiameter;
        if (Math.abs(reynoldsNumber) < 2000) {
            flowPattern = "laminar";
            return 64.0 / reynoldsNumber;
        } else {
            flowPattern = "turbulent";
            return Math.pow(
                    (1.0 / (-1.8 * Math
                            .log10(6.9 / reynoldsNumber + Math.pow(relativeRoughnes / 3.7, 1.11)))),
                    2.0);
        }
    }

    /**
     * <p>
     * calcPressureOut.
     * </p>
     *
     * @return a double
     */
    public double calcPressureOut() {
        double area = Math.PI / 4.0 * Math.pow(insideDiameter, 2.0);
        velocity = system.getPhase(0).getTotalVolume() / area / 1.0e5;
        double reynoldsNumber = velocity * insideDiameter
                / system.getPhase(0).getPhysicalProperties().getKinematicViscosity();
        double frictionFactor = calcWallFrictionFactor(reynoldsNumber);
        double dp = Math
                .pow(4.0 * system.getPhase(0).getNumberOfMolesInPhase()
                        * system.getPhase(0).getMolarMass()
                        / neqsim.thermo.ThermodynamicConstantsInterface.pi, 2.0)
                * frictionFactor * length * system.getPhase(0).getZ()
                * neqsim.thermo.ThermodynamicConstantsInterface.R
                / system.getPhase(0).getMolarMass() * system.getTemperature()
                / Math.pow(insideDiameter, 5.0);
        // \\System.out.println("friction fact" + frictionFactor + " velocity " +
        // velocity + " reynolds number " + reynoldsNumber);
        System.out.println("dp gravity "
                + system.getDensity("kg/m3") * neqsim.thermo.ThermodynamicConstantsInterface.gravity
                        * (inletElevation - outletElevation) / 1.0e5);
        double dp_gravity =
                system.getDensity("kg/m3") * neqsim.thermo.ThermodynamicConstantsInterface.gravity
                        * (inletElevation - outletElevation);
        return Math.sqrt(Math.pow(inletPressure * 1e5, 2.0) - dp) / 1.0e5 + dp_gravity / 1.0e5;
    }

    /**
     * <p>
     * calcFlow.
     * </p>
     *
     * @return a double
     */
    public double calcFlow() {
        double averagePressue = (inletPressure + pressureOut) / 2.0;
        system.setPressure(averagePressue);
        system.init(1);
        system.initPhysicalProperties();

        double area = Math.PI / 4.0 * Math.pow(insideDiameter, 2.0);
        double presdrop2 = Math.pow(inletPressure * 1e2, 2.0) - Math.pow(pressureOut * 1e2, 2.0);
        double gasGravity = system.getMolarMass() / 0.028;
        double oldReynold = 0;
        double reynoldsNumber = -1000.0;
        double flow = 0;
        do {
            oldReynold = reynoldsNumber;
            velocity = system.getPhase(0).getTotalVolume() / area / 1.0e5;
            reynoldsNumber = velocity * insideDiameter
                    / system.getPhase(0).getPhysicalProperties().getKinematicViscosity();
            double frictionFactor = calcWallFrictionFactor(reynoldsNumber) * 4.0;
            double temp = Math.sqrt(presdrop2 * Math.pow(insideDiameter * 1000.0, 5.0)
                    / (gasGravity * system.getPhase(0).getZ() * system.getTemperature()
                            * frictionFactor * length / 1000.0));
            flow = 1.1494e-3 * 288.15 / (system.getPressure() * 100) * temp;
            system.setTotalFlowRate(flow / 1e6, "MSm^3/day");
            system.init(1);
            // System.out.println("flow " + flow + " velocity " + velocity);
        } while (Math.abs(reynoldsNumber - oldReynold) / reynoldsNumber > 1e-3);
        return flow;
    }

    /** {@inheritDoc} */
    @Override
    public void run() {
        system = (SystemInterface) inStream.getThermoSystem().clone();
        inletPressure = system.getPressure();
        // system.setMultiPhaseCheck(true);
        if (setTemperature) {
            system.setTemperature(this.temperatureOut);
        }

        double oldPressure = 0.0;
        int iter = 0;
        if (!setPressureOut) {
            do {
                iter++;
                oldPressure = system.getPressure();
                system.init(3);
                system.initPhysicalProperties();
                system.setPressure(calcPressureOut());
            } while (Math.abs(system.getPressure() - oldPressure) > 1e-2 && iter < 25);
        } else {
            calcFlow();
            system.setPressure(pressureOut);
            system.init(3);
        }
        ThermodynamicOperations testOps = new ThermodynamicOperations(system);
        testOps.TPflash();
        if (setPressureOut) {
            inStream.getThermoSystem().setTotalFlowRate(system.getFlowRate("kg/sec"), "kg/sec");
        }
        // system.setMultiPhaseCheck(false);
        outStream.setThermoSystem(system);
    }

    /** {@inheritDoc} */
    @Override
    public void displayResult() {
        system.display();
    }

    /** {@inheritDoc} */
    @Override
    public String getName() {
        return name;
    }

    /** {@inheritDoc} */
    @Override
    public void runTransient() {
        run();
    }

    /** {@inheritDoc} */
    @Override
    public FlowSystemInterface getPipe() {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public void setInitialFlowPattern(String flowPattern) {}

    /**
     * <p>
     * Getter for the field <code>length</code>.
     * </p>
     *
     * @return the length
     */
    public double getLength() {
        return length;
    }

    /**
     * <p>
     * Setter for the field <code>length</code>.
     * </p>
     *
     * @param length the length to set
     */
    public void setLength(double length) {
        this.length = length;
    }

    /**
     * <p>
     * getDiameter.
     * </p>
     *
     * @return the diameter
     */
    public double getDiameter() {
        return insideDiameter;
    }

    /**
     * <p>
     * setDiameter.
     * </p>
     *
     * @param diameter the diameter to set
     */
    public void setDiameter(double diameter) {
        this.insideDiameter = diameter;
    }

    /**
     * <p>
     * Getter for the field <code>pipeWallRoughness</code>.
     * </p>
     *
     * @return the pipeWallRoughness
     */
    public double getPipeWallRoughness() {
        return pipeWallRoughness;
    }

    /**
     * <p>
     * Setter for the field <code>pipeWallRoughness</code>.
     * </p>
     *
     * @param pipeWallRoughness the pipeWallRoughness to set
     */
    public void setPipeWallRoughness(double pipeWallRoughness) {
        this.pipeWallRoughness = pipeWallRoughness;
    }

    /**
     * <p>
     * Getter for the field <code>inletElevation</code>.
     * </p>
     *
     * @return the inletElevation
     */
    public double getInletElevation() {
        return inletElevation;
    }

    /**
     * <p>
     * Setter for the field <code>inletElevation</code>.
     * </p>
     *
     * @param inletElevation the inletElevation to set
     */
    public void setInletElevation(double inletElevation) {
        this.inletElevation = inletElevation;
    }

    /**
     * <p>
     * Getter for the field <code>outletElevation</code>.
     * </p>
     *
     * @return the outletElevation
     */
    public double getOutletElevation() {
        return outletElevation;
    }

    /**
     * <p>
     * Setter for the field <code>outletElevation</code>.
     * </p>
     *
     * @param outletElevation the outletElevation to set
     */
    public void setOutletElevation(double outletElevation) {
        this.outletElevation = outletElevation;
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
                new neqsim.thermo.system.SystemSrkEos((273.15 + 5.0), 220.00);
        testSystem.addComponent("methane", 24.0, "MSm^3/day");
        testSystem.createDatabase(true);
        testSystem.setMixingRule(2);
        testSystem.init(0);

        Stream stream_1 = new Stream("Stream1", testSystem);

        AdiabaticPipe pipe = new AdiabaticPipe(stream_1);
        pipe.setLength(700000.0);
        pipe.setDiameter(0.7112);
        pipe.setPipeWallRoughness(5e-6);
        pipe.setOutPressure(112.0);

        neqsim.processSimulation.processSystem.ProcessSystem operations =
                new neqsim.processSimulation.processSystem.ProcessSystem();
        operations.add(stream_1);
        operations.add(pipe);
        operations.run();
        pipe.displayResult();
    }
}
