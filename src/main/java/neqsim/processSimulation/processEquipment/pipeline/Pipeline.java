/*
 * Pipeline.java
 *
 * Created on 14. mars 2001, 22:30
 */
package neqsim.processSimulation.processEquipment.pipeline;

import neqsim.fluidMechanics.flowSystem.FlowSystemInterface;
import neqsim.fluidMechanics.geometryDefinitions.GeometryDefinitionInterface;
import neqsim.fluidMechanics.geometryDefinitions.pipe.PipeData;
import neqsim.processSimulation.mechanicalDesign.pipeline.PipelineMechanicalDesign;
import neqsim.processSimulation.processEquipment.TwoPortEquipment;
import neqsim.processSimulation.processEquipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * Pipeline class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class Pipeline extends TwoPortEquipment implements PipeLineInterface {
    private static final long serialVersionUID = 1000;

    protected String fileName = "c:/test5.nc";
    protected FlowSystemInterface pipe;
    protected SystemInterface system;
    String flowPattern = "stratified";
    double[] times;
    boolean equilibriumHeatTransfer = true, equilibriumMassTransfer = false;
    // default variables
    int numberOfLegs = 1, numberOfNodesInLeg = 30;
    double[] legHeights = {0, 0};// ,0,0,0};
    double[] legPositions = {0.0, 1.0};// 10.0,20.0,30.0,40.0};
    double[] pipeDiameters = {0.1507588, 0.1507588};// , 1.207588, 1.207588, 1.207588};
    double[] outerTemperature = {278.0, 278.0};// , 278.0, 278.0, 278.0};
    double[] pipeWallRoughness = {1e-5, 1e-5};// , 1e-5, 1e-5, 1e-5};
    double[] outerHeatTransferCoeffs = {1e-5, 1e-5};// , 1e-5, 1e-5, 1e-5};
    double[] wallHeatTransferCoeffs = {1e-5, 1e-5};// , 1e-5, 1e-5, 1e-5};

    /**
     * <p>
     * Constructor for Pipeline.
     * </p>
     */
    @Deprecated
    public Pipeline() {
        this("Pipeline");
    }

    /**
     * <p>
     * Constructor for Pipeline.
     * </p>
     *
     * @param inStream a {@link neqsim.processSimulation.processEquipment.stream.StreamInterface}
     *        object
     */
    @Deprecated
    public Pipeline(StreamInterface inStream) {
        this("Pipeline", inStream);
    }

    /**
     * <p>
     * Constructor for Pipeline.
     * </p>
     *
     * @param name a {@link java.lang.String} object
     */
    public Pipeline(String name) {
        super(name);
    }

    /**
     * <p>
     * Constructor for Pipeline.
     * </p>
     *
     * @param name a {@link java.lang.String} object
     * @param inStream a {@link neqsim.processSimulation.processEquipment.stream.StreamInterface}
     *        object
     */
    public Pipeline(String name, StreamInterface inStream) {
        super(name, inStream);
    }

    public PipelineMechanicalDesign getMechanicalDesign() {
        return new PipelineMechanicalDesign(this);
    }

    /** {@inheritDoc} */
    @Override
    public void setOutputFileName(String name) {
        this.fileName = name;
    }

    /**
     * <p>
     * Getter for the field <code>outStream</code>.
     * </p>
     *
     * @return a {@link neqsim.processSimulation.processEquipment.stream.StreamInterface} object
     */
    @Deprecated
    public StreamInterface getOutStream() {
        return outStream;
    }

    /** {@inheritDoc} */
    @Override
    public StreamInterface getOutletStream() {
        return outStream;
    }

    /** {@inheritDoc} */
    @Override
    public void setNumberOfLegs(int number) {
        this.numberOfLegs = number;
    }

    /** {@inheritDoc} */
    @Override
    public void setNumberOfNodesInLeg(int number) {
        this.numberOfNodesInLeg = number;
    }

    /** {@inheritDoc} */
    @Override
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

    /** {@inheritDoc} */
    @Override
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

    /** {@inheritDoc} */
    @Override
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

    /**
     * <p>
     * setPipeOuterHeatTransferCoefficients.
     * </p>
     *
     * @param heatCoefs an array of {@link double} objects
     */
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

    /**
     * <p>
     * setPipeWallHeatTransferCoefficients.
     * </p>
     *
     * @param heatCoefs an array of {@link double} objects
     */
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

    /** {@inheritDoc} */
    @Override
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

    /** {@inheritDoc} */
    @Override
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

    /**
     * <p>
     * Setter for the field <code>equilibriumMassTransfer</code>.
     * </p>
     *
     * @param test a boolean
     */
    public void setEquilibriumMassTransfer(boolean test) {
        equilibriumMassTransfer = test;
    }

    /**
     * <p>
     * Setter for the field <code>equilibriumHeatTransfer</code>.
     * </p>
     *
     * @param test a boolean
     */
    public void setEquilibriumHeatTransfer(boolean test) {
        equilibriumHeatTransfer = test;
    }

    /** {@inheritDoc} */
    @Override
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

    /** {@inheritDoc} */
    @Override
    public void runTransient(double dt) {
        pipe.solveTransient(2);
        pipe.getDisplay().createNetCdfFile(fileName);
    }

    /** {@inheritDoc} */
    @Override
    public void displayResult() {}

    /** {@inheritDoc} */
    @Override
    public FlowSystemInterface getPipe() {
        return pipe;
    }

    /** {@inheritDoc} */
    @Override
    public void setInitialFlowPattern(String flowPattern) {
        this.flowPattern = flowPattern;
    }

    /**
     * Getter for property times.
     *
     * @return Value of property times.
     */
    public double[] getTimes() {
        return this.times;
    }

    /**
     * <p>
     * getSuperficialVelocity.
     * </p>
     *
     * @param phase a int
     * @param node a int
     * @return a double
     */
    public double getSuperficialVelocity(int phase, int node) {
        try {
            return outStream.getThermoSystem().getPhase(phase).getNumberOfMolesInPhase()
                    * outStream.getThermoSystem().getPhase(phase).getMolarMass()
                    / outStream.getThermoSystem().getPhase(phase).getPhysicalProperties()
                            .getDensity()
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
     * @param systems an array of {@link neqsim.thermo.system.SystemInterface} objects
     * @param timestepininterval a int
     */
    public void setTimeSeries(double[] times, SystemInterface[] systems, int timestepininterval) {
        this.times = times;
        pipe.getTimeSeries().setTimes(times);
        pipe.getTimeSeries().setInletThermoSystems(systems);
        pipe.getTimeSeries().setNumberOfTimeStepsInInterval(timestepininterval);
    }

    /** {@inheritDoc} */
    @Override
    public double getEntropyProduction(String unit) {
        return outStream.getThermoSystem().getEntropy(unit)
                - inStream.getThermoSystem().getEntropy(unit);
    }
}
