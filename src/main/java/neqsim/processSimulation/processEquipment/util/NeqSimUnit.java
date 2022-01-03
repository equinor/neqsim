package neqsim.processSimulation.processEquipment.util;

import neqsim.fluidMechanics.flowNode.FlowNodeInterface;
import neqsim.fluidMechanics.flowNode.twoPhaseNode.twoPhasePipeFlowNode.AnnularFlow;
import neqsim.fluidMechanics.flowNode.twoPhaseNode.twoPhasePipeFlowNode.DropletFlowNode;
import neqsim.fluidMechanics.flowNode.twoPhaseNode.twoPhasePipeFlowNode.StratifiedFlowNode;
import neqsim.fluidMechanics.geometryDefinitions.pipe.PipeData;
import neqsim.processSimulation.processEquipment.ProcessEquipmentBaseClass;
import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;

/**
 * <p>NeqSimUnit class.</p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class NeqSimUnit extends ProcessEquipmentBaseClass {

    private static final long serialVersionUID = 1000;

    Stream inletStream;
    Stream outStream;
    SystemInterface thermoSystem;
    private String equipment = "pipeline";
    String flowPattern = "stratified";
    private double length = 1.0;
    public int numberOfNodes = 100;
    private double ID = 0.5;
    private double outerTemperature = 283.15;
    public double interfacialArea = 0.0;

    /**
     * <p>Constructor for NeqSimUnit.</p>
     *
     * @param inletStream a {@link neqsim.processSimulation.processEquipment.stream.Stream} object
     * @param equipment a {@link java.lang.String} object
     * @param flowPattern a {@link java.lang.String} object
     */
    public NeqSimUnit(Stream inletStream, String equipment, String flowPattern) {
        this.flowPattern = flowPattern;
        this.setEquipment(equipment);
        setInletStream(inletStream);
    }

    /**
     * <p>Setter for the field <code>inletStream</code>.</p>
     *
     * @param inletStream a {@link neqsim.processSimulation.processEquipment.stream.Stream} object
     */
    public void setInletStream(Stream inletStream) {
        this.inletStream = inletStream;

        thermoSystem = (SystemInterface) inletStream.getThermoSystem().clone();
        outStream = new Stream(thermoSystem);
    }

    /**
     * <p>Getter for the field <code>outStream</code>.</p>
     *
     * @return a {@link neqsim.processSimulation.processEquipment.stream.Stream} object
     */
    public Stream getOutStream() {
        return outStream;
    }

    /** {@inheritDoc} */
    @Override
	public void run() {
        thermoSystem = (SystemInterface) inletStream.getThermoSystem().clone();
        if (equipment.equals("pipeline") && flowPattern.equals("stratified")) {
            runStratified();
        } else if (equipment.equals("pipeline") && flowPattern.equals("annular")) {
            runAnnular();
        } else if (equipment.equals("pipeline") && flowPattern.equals("droplet")) {
            runDroplet();
        } else {
            runStratified();
        }
        // outStream.setThermoSystem(thermoSystem);
    }

    /**
     * <p>runDroplet.</p>
     */
    public void runDroplet() {
        PipeData pipe1 = new PipeData(getID(), 0.00025);
        FlowNodeInterface test = new DropletFlowNode(thermoSystem, pipe1);
        test.setInterphaseModelType(1);
        test.setLengthOfNode(getLength() / (numberOfNodes * 1.0));
        test.getGeometry().getSurroundingEnvironment().setTemperature(getOuterTemperature());

        test.getFluidBoundary().setHeatTransferCalc(false);
        test.getFluidBoundary().setMassTransferCalc(true);
        double length = 0;
        test.initFlowCalc();
        double[][] temperatures2 = new double[3][1000];
        int k = 0;
        interfacialArea = 0.0;
        for (int i = 0; i < numberOfNodes; i++) {
            interfacialArea = getInterfacialArea() + test.getInterphaseContactArea();
            length += test.getLengthOfNode();
            test.initFlowCalc();
            test.calcFluxes();
            if (i > 1 && (i % 1) == 0) {
                k++;
                // test.display("length " + length);
                // test.getBulkSystem().display("length " + length);
                // test.getInterphaseSystem().display("length " + length);
                // test.getFluidBounsdary().display("length " + length);
                // test.setLengthOfNode(0.000005 + test.getLengthOfNode() / 2.0);
                temperatures2[0][k] = length;
                temperatures2[1][k] = test.getGeometry().getInnerWallTemperature();
                // test.getFluidBoundary().display("test");
            }

            // test.getBulkSystem().display();
            test.update();
            // test.getFluidBoundary().display("length " + length);
            // test.getInterphaseSystem().display("length " + length);

            // test.getFluidBoundary().display("test");
        }

        for (int i = 0; i < k; i++) {
            // System.out.println("len temp " + temperatures2[0][i] + " " +
            // temperatures2[1][i]);
        }
        // test.display("length " + length);
        outStream.setThermoSystem(test.getBulkSystem());
    }

    /**
     * <p>runStratified.</p>
     */
    public void runStratified() {
        PipeData pipe1 = new PipeData(getID(), 0.00025);
        FlowNodeInterface test = new StratifiedFlowNode(thermoSystem, pipe1);
        test.setInterphaseModelType(1);
        test.setLengthOfNode(getLength() / (numberOfNodes * 1.0));
        test.getGeometry().getSurroundingEnvironment().setTemperature(getOuterTemperature());

        test.getFluidBoundary().setHeatTransferCalc(false);
        test.getFluidBoundary().setMassTransferCalc(true);
        double length = 0;
        test.initFlowCalc();
        double[][] temperatures2 = new double[3][1000];
        int k = 0;
        interfacialArea = 0.0;
        for (int i = 0; i < numberOfNodes; i++) {
            interfacialArea = getInterfacialArea() + test.getInterphaseContactArea();
            length += test.getLengthOfNode();
            test.initFlowCalc();
            test.calcFluxes();
            if (i > 1 && (i % 1) == 0) {
                k++;
                // test.display("length " + length);
                // test.getBulkSystem().display("length " + length);
                // test.getInterphaseSystem().display("length " + length);
                // test.getFluidBounsdary().display("length " + length);
                // test.setLengthOfNode(0.000005 + test.getLengthOfNode() / 2.0);
                temperatures2[0][k] = length;
                temperatures2[1][k] = test.getGeometry().getInnerWallTemperature();
                // test.getFluidBoundary().display("test");
            }

            // test.getBulkSystem().display();
            test.update();
            // test.getFluidBoundary().display("length " + length);
            // test.getInterphaseSystem().display("length " + length);

            // test.getFluidBoundary().display("test");
        }

        for (int i = 0; i < k; i++) {
            // System.out.println("len temp " + temperatures2[0][i] + " " +
            // temperatures2[1][i]);
        }
        // test.display("length " + length);
        outStream.setThermoSystem(test.getBulkSystem());
    }

    /**
     * <p>runAnnular.</p>
     */
    public void runAnnular() {
        PipeData pipe1 = new PipeData(getID(), 0.00025);
        FlowNodeInterface test = new AnnularFlow(thermoSystem, pipe1);
        test.setInterphaseModelType(1);
        test.setLengthOfNode(getLength() / (numberOfNodes * 1.0));
        test.getGeometry().getSurroundingEnvironment().setTemperature(getOuterTemperature());

        test.getFluidBoundary().setHeatTransferCalc(false);
        test.getFluidBoundary().setMassTransferCalc(true);
        double length = 0;
        test.initFlowCalc();
        double[][] temperatures2 = new double[3][1000];
        int k = 0;
        interfacialArea = 0.0;
        for (int i = 0; i < numberOfNodes; i++) {
            interfacialArea = getInterfacialArea() + test.getInterphaseContactArea();
            length += test.getLengthOfNode();
            test.initFlowCalc();
            test.calcFluxes();
            if (i > 1 && (i % 1) == 0) {
                k++;
                // test.display("length " + length);
                // test.getBulkSystem().display("length " + length);
                // test.getInterphaseSystem().display("length " + length);
                // test.getFluidBounsdary().display("length " + length);
                // test.setLengthOfNode(0.000005 + test.getLengthOfNode() / 2.0);
                temperatures2[0][k] = length;
                temperatures2[1][k] = test.getGeometry().getInnerWallTemperature();
                // test.getFluidBoundary().display("test");
            }

            // test.getBulkSystem().display();
            test.update();
            // test.getFluidBoundary().display("length " + length);
            // test.getInterphaseSystem().display("length " + length);

            // test.getFluidBoundary().display("test");
        }

        for (int i = 0; i < k; i++) {
            // System.out.println("len temp " + temperatures2[0][i] + " " +
            // temperatures2[1][i]);
        }
        // test.display("length " + length);
        outStream.setThermoSystem(test.getBulkSystem());
    }

    /**
     * <p>Getter for the field <code>length</code>.</p>
     *
     * @return a double
     */
    public double getLength() {
        return length;
    }

    /**
     * <p>Setter for the field <code>length</code>.</p>
     *
     * @param length a double
     */
    public void setLength(double length) {
        this.length = length;
    }

    /**
     * <p>getID.</p>
     *
     * @return a double
     */
    public double getID() {
        return ID;
    }

    /**
     * <p>setID.</p>
     *
     * @param iD a double
     */
    public void setID(double iD) {
        ID = iD;
    }

    /**
     * <p>Getter for the field <code>outerTemperature</code>.</p>
     *
     * @return a double
     */
    public double getOuterTemperature() {
        return outerTemperature;
    }

    /**
     * <p>Setter for the field <code>outerTemperature</code>.</p>
     *
     * @param outerTemperature a double
     */
    public void setOuterTemperature(double outerTemperature) {
        this.outerTemperature = outerTemperature;
    }

    /**
     * <p>Getter for the field <code>equipment</code>.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public String getEquipment() {
        return equipment;
    }

    /**
     * <p>Setter for the field <code>equipment</code>.</p>
     *
     * @param equipment a {@link java.lang.String} object
     */
    public void setEquipment(String equipment) {
        this.equipment = equipment;
    }

    /**
     * <p>Getter for the field <code>interfacialArea</code>.</p>
     *
     * @return a double
     */
    public double getInterfacialArea() {
        return interfacialArea;
    }

    /**
     * <p>Getter for the field <code>numberOfNodes</code>.</p>
     *
     * @return a int
     */
    public int getNumberOfNodes() {
        return numberOfNodes;
    }

    /**
     * <p>Setter for the field <code>numberOfNodes</code>.</p>
     *
     * @param numberOfNodes a int
     */
    public void setNumberOfNodes(int numberOfNodes) {
        this.numberOfNodes = numberOfNodes;
    }
}
