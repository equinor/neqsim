/*
 * OnePhasePipeLine.java
 *
 * Created on 21. august 2001, 20:44
 */

package neqsim.processSimulation.processEquipment.pipeline;

import neqsim.fluidMechanics.flowSystem.onePhaseFlowSystem.pipeFlowSystem.PipeFlowSystem;
import neqsim.processSimulation.processEquipment.stream.Stream;

/**
 *
 * @author esol
 * @version
 */
public class OnePhasePipeLine extends Pipeline {

    private static final long serialVersionUID = 1000;

    /** Creates new OnePhasePipeLine */
    public OnePhasePipeLine() {
    }

    public OnePhasePipeLine(Stream inStream) {
        super(inStream);
        pipe = new PipeFlowSystem();
    }

    public void createSystem() {

    }

    @Override
	public void run() {
        super.run();
        pipe.solveSteadyState(10);
        //pipe.print();
        // pipe.getDisplay().createNetCdfFile(fileName);
        outStream.setThermoSystem(pipe.getNode(pipe.getTotalNumberOfNodes() - 1).getBulkSystem());
    }

    @Override
	public void runTransient() {
        super.runTransient();
    }
}
