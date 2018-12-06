/*
 * TwoPhasePipeLine.java
 *
 * Created on 21. august 2001, 20:45
 */

package neqsim.processSimulation.processEquipment.pipeline;

import neqsim.fluidMechanics.flowSystem.twoPhaseFlowSystem.twoPhasePipeFlowSystem.TwoPhasePipeFlowSystem;
import neqsim.processSimulation.processEquipment.stream.Stream;

/**
 *
 * @author  esol
 * @version
 */
public class TwoPhasePipeLine extends Pipeline{

    private static final long serialVersionUID = 1000;
    
   
    /** Creates new TwoPhasePipeLine */
    public TwoPhasePipeLine() {
    }
    
    public TwoPhasePipeLine(Stream inStream) {
        super(inStream);
        pipe = new TwoPhasePipeFlowSystem();
    }
    
    public TwoPhasePipeLine(String name, Stream inStream) {
        super(name, inStream);
        pipe = new TwoPhasePipeFlowSystem();
    }
    
   
    
    public void createSystem(){
    }
    
    public void run(){
        super.run();
        pipe.solveSteadyState(2);
        pipe.print();
        pipe.getDisplay().createNetCdfFile(fileName);
    }
    
}
