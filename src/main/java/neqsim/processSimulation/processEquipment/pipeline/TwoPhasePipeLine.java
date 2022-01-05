/*
 * TwoPhasePipeLine.java
 *
 * Created on 21. august 2001, 20:45
 */
package neqsim.processSimulation.processEquipment.pipeline;

import neqsim.fluidMechanics.flowSystem.twoPhaseFlowSystem.twoPhasePipeFlowSystem.TwoPhasePipeFlowSystem;
import neqsim.processSimulation.processEquipment.stream.Stream;

/**
 * <p>
 * TwoPhasePipeLine class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class TwoPhasePipeLine extends Pipeline {
    private static final long serialVersionUID = 1000;

    /**
     * <p>
     * Constructor for TwoPhasePipeLine.
     * </p>
     */
    public TwoPhasePipeLine() {}

    /**
     * <p>
     * Constructor for TwoPhasePipeLine.
     * </p>
     *
     * @param inStream a {@link neqsim.processSimulation.processEquipment.stream.Stream} object
     */
    public TwoPhasePipeLine(Stream inStream) {
        super(inStream);
        pipe = new TwoPhasePipeFlowSystem();
    }

    /**
     * <p>
     * Constructor for TwoPhasePipeLine.
     * </p>
     *
     * @param name a {@link java.lang.String} object
     * @param inStream a {@link neqsim.processSimulation.processEquipment.stream.Stream} object
     */
    public TwoPhasePipeLine(String name, Stream inStream) {
        super(name, inStream);
        pipe = new TwoPhasePipeFlowSystem();
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
        pipe.solveSteadyState(2);
        pipe.print();
        pipe.getDisplay().createNetCdfFile(fileName);
    }
}
