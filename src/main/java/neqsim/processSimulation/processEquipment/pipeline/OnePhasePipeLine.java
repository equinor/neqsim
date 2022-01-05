/*
 * OnePhasePipeLine.java
 *
 * Created on 21. august 2001, 20:44
 */
package neqsim.processSimulation.processEquipment.pipeline;

import neqsim.fluidMechanics.flowSystem.onePhaseFlowSystem.pipeFlowSystem.PipeFlowSystem;
import neqsim.processSimulation.processEquipment.stream.Stream;

/**
 * <p>
 * OnePhasePipeLine class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class OnePhasePipeLine extends Pipeline {
    private static final long serialVersionUID = 1000;

    /**
     * <p>
     * Constructor for OnePhasePipeLine.
     * </p>
     */
    public OnePhasePipeLine() {}

    /**
     * <p>
     * Constructor for OnePhasePipeLine.
     * </p>
     *
     * @param inStream a {@link neqsim.processSimulation.processEquipment.stream.Stream} object
     */
    public OnePhasePipeLine(Stream inStream) {
        super(inStream);
        pipe = new PipeFlowSystem();
    }

    /**
     * <p>
     * createSystem.
     * </p>
     */
    public void createSystem() {}

    /** {@inheritDoc} */
    @Override
    public void run() {
        super.run();
        pipe.solveSteadyState(10);
        // pipe.print();
        // pipe.getDisplay().createNetCdfFile(fileName);
        outStream.setThermoSystem(pipe.getNode(pipe.getTotalNumberOfNodes() - 1).getBulkSystem());
    }

    /** {@inheritDoc} */
    @Override
    public void runTransient() {
        super.runTransient();
    }
}
