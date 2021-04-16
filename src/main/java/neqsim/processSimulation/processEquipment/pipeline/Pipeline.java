/*
 * Pipeline.java
 *
 * Created on 14. mars 2001, 22:30
 */
package neqsim.processSimulation.processEquipment.pipeline;

import neqsim.fluidMechanics.flowSystem.FlowSystemInterface;
import neqsim.fluidMechanics.geometryDefinitions.GeometryDefinitionInterface;
import neqsim.fluidMechanics.geometryDefinitions.pipe.PipeData;
import neqsim.processSimulation.mechanicalDesign.pipeline.PipelineMechanicalDeisgn;
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
public class Pipeline extends ProcessEquipmentBaseClass implements ProcessEquipmentInterface, PipeLineInterface {

    private static final long serialVersionUID = 1000;

    protected String fileName = "c:/test5.nc";
    protected StreamInterface outStream;
    protected StreamInterface inStream;
    protected FlowSystemInterface pipe;
    protected SystemInterface system;
    String flowPattern = "stratified";
    double[] times;
    boolean equilibriumHeatTransfer = true, equilibriumMassTransfer = false;
    // default variables
    int numberOfLegs = 1, numberOfNodesInLeg = 30;
    double[] legHeights = { 0, 0 };// ,0,0,0};
    double[] legPositions = { 0.0, 1.0 };// 10.0,20.0,30.0,40.0};
    double[] pipeDiameters = { 0.1507588, 0.1507588 };// , 1.207588, 1.207588, 1.207588};
    double[] outerTemperature = { 278.0, 278.0 };// , 278.0, 278.0, 278.0};
    double[] pipeWallRoughness = { 1e-5, 1e-5 };// , 1e-5, 1e-5, 1e-5};
    double[] outerHeatTransferCoeffs = { 1e-5, 1e-5 };// , 1e-5, 1e-5, 1e-5};
    double[] wallHeatTransferCoeffs = { 1e-5, 1e-5 };// , 1e-5, 1e-5, 1e-5};

    /**
     * Creates new Pipeline
     */
    public Pipeline() {
        mechanicalDesign = new PipelineMechanicalDeisgn(this);
    }

    public Pipeline(StreamInterface inStream) {
        this();
        this.inStream = inStream;
        outStream = (Stream) inStream.clone();
    }

    public Pipeline(String name, StreamInterface inStream) {
        this();
        this.name = name;
        this.inStream = inStream;
        outStream = (Stream) inStream.clone();
    }

    public void setOutputFileName(String name) {
        this.fileName = name;
    }

    public StreamInterface getOutStream() {
        return outStream;
    }

    public void setNumberOfLegs(int number) {
        this.numberOfLegs = number;
    }

    public void setNumberOfNodesInLeg(int number) {
        this.numberOfNodesInLeg = number;
    }

    public void setHeightProfile(double[] heights) {
        if (heights.length != this.numberOfLegs + 1) {
            System.out.println("Wrong number of heights specified.");
            System.out.println("Number of heights must be number of legs + 1 ");
            System.out.println(
                    "Remember to specify number of legs first (default 5) - than set the leg heights (default 6).");
            return;
        }
        legHeights = new double[heights.length];
        System.arraycopy(heights, 0, legHeights, 0, legHeights.length);
    }

    public void setLegPositions(double[] positions) {
        if (positions.length != this.numberOfLegs + 1) {
            System.out.println("Wrong number of legpositions specified.");
            System.out.println("Number of heights must be number of legs + 1 ");
            System.out.println(
                    "Remember to specify number of legs first (default 5) - than set the leg heights (default 6).");
            return;
        }
        legPositions = new double[positions.length];
        System.arraycopy(positions, 0, legPositions, 0, legPositions.length);
    }

    public void setPipeDiameters(double[] diameter) {
        if (diameter.length != this.numberOfLegs + 1) {
            System.out.println("Wrong number of diameters specified.");
            System.out.println("Number of diameters must be number of legs + 1 ");
            System.out.println(
                    "Remember to specify number of legs first (default 5) - than set the leg heights (default 6).");
            return;
        }
        pipeDiameters = new double[diameter.length];
        System.arraycopy(diameter, 0, pipeDiameters, 0, pipeDiameters.length);
    }

    public void setPipeOuterHeatTransferCoefficients(double[] heatCoefs) {
        if (heatCoefs.length != this.numberOfLegs + 1) {
            System.out.println("Wrong number of diameters specified.");
            System.out.println("Number of diameters must be number of legs + 1 ");
            System.out.println(
                    "Remember to specify number of legs first (default 5) - than set the leg heights (default 6).");
            return;
        }
        outerHeatTransferCoeffs = new double[heatCoefs.length];
        System.arraycopy(heatCoefs, 0, outerHeatTransferCoeffs, 0, outerHeatTransferCoeffs.length);
    }

    public void setPipeWallHeatTransferCoefficients(double[] heatCoefs) {
        if (heatCoefs.length != this.numberOfLegs + 1) {
            System.out.println("Wrong number of diameters specified.");
            System.out.println("Number of diameters must be number of legs + 1 ");
            System.out.println(
                    "Remember to specify number of legs first (default 5) - than set the leg heights (default 6).");
            return;
        }
        wallHeatTransferCoeffs = new double[heatCoefs.length];
        System.arraycopy(heatCoefs, 0, wallHeatTransferCoeffs, 0, wallHeatTransferCoeffs.length);
    }

    public void setPipeWallRoughness(double[] rough) {
        if (rough.length != this.numberOfLegs + 1) {
            System.out.println("Wrong number of roghuness points specified.");
            System.out.println("Number of heights must be number of legs + 1 ");
            System.out.println(
                    "Remember to specify number of legs first (default 5) - than set the leg heights (default 6).");
            return;
        }
        pipeWallRoughness = new double[rough.length];
        System.arraycopy(rough, 0, pipeWallRoughness, 0, pipeWallRoughness.length);
    }

    public void setOuterTemperatures(double[] outerTemp) {
        if (outerTemp.length != this.numberOfLegs + 1) {
            System.out.println("Wrong number of outer temperature points specified.");
            System.out.println("Number of heights must be number of legs + 1 ");
            System.out.println(
                    "Remember to specify number of legs first (default 5) - than set the leg heights (default 6).");
            return;
        }
        outerTemperature = new double[outerTemp.length];
        System.arraycopy(outerTemp, 0, outerTemperature, 0, outerTemperature.length);
    }

    public void setEquilibriumMassTransfer(boolean test) {
        equilibriumMassTransfer = test;
    }

    public void setEquilibriumHeatTransfer(boolean test) {
        equilibriumHeatTransfer = test;
    }

    public void run() {
        system = inStream.getThermoSystem();
        GeometryDefinitionInterface[] pipeGemometry = new PipeData[numberOfLegs + 1];
        for (int i = 0; i < pipeDiameters.length; i++) {
            pipeGemometry[i] = new PipeData(pipeDiameters[i], pipeWallRoughness[i]);
        }
        pipe.setInletThermoSystem(system);
        pipe.setNumberOfLegs(numberOfLegs);
        pipe.setNumberOfNodesInLeg(numberOfNodesInLeg);
        pipe.setEquipmentGeometry(pipeGemometry);
        pipe.setLegOuterTemperatures(outerTemperature);
        pipe.setLegHeights(legHeights);
        pipe.setLegOuterHeatTransferCoefficients(outerHeatTransferCoeffs);
        pipe.setLegWallHeatTransferCoefficients(wallHeatTransferCoeffs);
        pipe.setLegPositions(legPositions);
        pipe.setInitialFlowPattern(flowPattern);
        pipe.createSystem();
        pipe.setEquilibriumMassTransfer(equilibriumMassTransfer);
        pipe.setEquilibriumHeatTransfer(equilibriumHeatTransfer);
        pipe.init();
    }

    public void runTransient() {
        pipe.solveTransient(2);
        pipe.getDisplay().createNetCdfFile(fileName);
    }

    public void displayResult() {
    }

    public FlowSystemInterface getPipe() {
        return pipe;
    }

    public void setInitialFlowPattern(String flowPattern) {
        this.flowPattern = flowPattern;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /**
     * Getter for property times.
     *
     * @return Value of property times.
     */
    public double[] getTimes() {
        return this.times;
    }

    public double getSuperficialVelocity(int phase, int node) {
        try {
            return outStream.getThermoSystem().getPhase(phase).getNumberOfMolesInPhase()
                    * outStream.getThermoSystem().getPhase(phase).getMolarMass()
                    / outStream.getThermoSystem().getPhase(phase).getPhysicalProperties().getDensity()
                    / (3.14 * pipeDiameters[node] * pipeDiameters[node] / 4.0);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
        }
        return 0.0;
    }

    /**
     * Setter for property times.
     *
     * @param times New value of property times.
     */
    public void setTimeSeries(double[] times, SystemInterface[] systems, int timestepininterval) {
        this.times = times;
        pipe.getTimeSeries().setTimes(times);
        pipe.getTimeSeries().setInletThermoSystems(systems);
        pipe.getTimeSeries().setNumberOfTimeStepsInInterval(timestepininterval);
    }

    public double getEntropyProduction(String unit) {
        return outStream.getThermoSystem().getEntropy(unit) - inStream.getThermoSystem().getEntropy(unit);
    }
}
