package neqsim.processSimulation.processSystem;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.processSimulation.SimulationBaseClass;
import neqsim.processSimulation.processEquipment.ProcessEquipmentInterface;
import neqsim.processSimulation.processEquipment.util.Recycle;

/**

A class representing a process module class that can contain unit operations and other modules.
Module will be runnning until all recycles in this module are solved.
If no recycle in the module then run only once. 

@author [seros]

@version 1.0

*/

public class ProcessModule extends SimulationBaseClass {

    private static final long serialVersionUID = 1000;
    private static final Logger logger = LogManager.getLogger(ProcessModule.class);

    private int unitIndex = 0;

    private final List<ProcessEquipmentInterface> recycleModules = new ArrayList<>();

    private List<ProcessSystem> addedUnitOperations = new ArrayList<>();
    private List<Integer> operationsIndex = new ArrayList<>();

    private List<ProcessModule> addedModules = new ArrayList<>();
    private List<Integer> modulesIndex = new ArrayList<>();

    private boolean solved = false;

    /**

    Default constructor that sets the name to "Default Module Name".

    */

    public ProcessModule() {
        this("Default Module Name");
    }

    /**

    Constructor that takes a name as a parameter.

    @param name the name of the process module

    */

    public ProcessModule(String name) {
        super(name);
    }

    /**

    Add a unit operation to the process module.

    @param processSystem the process system that contains the unit operations to be added.

    */

    public void add(ProcessSystem processSystem) {
        addedUnitOperations.add(processSystem);
        operationsIndex.add(unitIndex++);
    }

    /**

    Get the list of added unit operations.

    @return the list of added unit operations

    */

    public List<ProcessSystem> getAddedUnitOperations() {
        return addedUnitOperations;
    }

    /**

    Get the list of operations index. 
    The operations index is used to follow the correct order of calculations.

    @return the list of operations index

    */

    public List<Integer> getOperationsIndex() {
        return operationsIndex;
    }

    /**

    Add a process module to the process module.

    @param module the process module to be added

    */

    public void add(ProcessModule module) {
        addedModules.add(module);
        modulesIndex.add(unitIndex++);
    }
    
    /**

    Get the list of added process modules.

    @return the list of added process modules

    */

    public List<ProcessModule> getAddedModules() {
        return addedModules;
    }

    /**

    Get the list of module index. The module index is used to follow the correct order of calculations. 

    @return the list of module index

    */

    public List<Integer> getModulesIndex() {
        return modulesIndex;
    }

    /**

    Run the current process module.

    @param id the UUID of the process module

    */

    @Override
    public void run(UUID id) {
        logger.info("Running module " + getName());
        checkModulesRecycles();
        int iteration = 0;
        do {
            for (int i = 0; i < unitIndex; i++) {
                if (operationsIndex.contains(i)) {
                    int index = operationsIndex.indexOf(i);
                    for (ProcessEquipmentInterface unitOperation : addedUnitOperations.get(index).getUnitOperations()) {
                        unitOperation.run();
                    }
                } else if (modulesIndex.contains(i)) {
                    int index = modulesIndex.indexOf(i);
                    addedModules.get(index).run();
                }
            }
            iteration++;
            logger.info("Iteration : " + iteration + "  module : " + getName() + " ");
        } while (!recyclesSolved() && iteration <= 100);
        logger.info("Finished running module " + getName());
        solved = true;
    }

    /**

    Adds all recycle operations from addedUnitOperations to recycleModules list.

    */
    public void checkModulesRecycles() {
        for (ProcessSystem operation : addedUnitOperations) {
            for (ProcessEquipmentInterface unitOperation : operation.getUnitOperations()) {
                if (unitOperation instanceof Recycle) {
                    recycleModules.add((Recycle) unitOperation);
                }
            }
        }
    }

    /**

    Checks if all recycle operations in recycleModules are solved.
    @return true if all recycle operations are solved, false otherwise
    */

    public boolean recyclesSolved() {
        for (ProcessEquipmentInterface recycle : recycleModules) {
            if (!recycle.solved()) {
                return false;
            }
        }
        return true;
    }

    /**

    Returns whether or not the module has been solved.
    @return true if the module has been solved, false otherwise
    */

    @Override
    public boolean solved() {
        return solved;
    }

}