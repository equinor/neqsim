package neqsim.processSimulation.processEquipment.util;

import java.util.ArrayList;

/**
 * <p>RecycleController class.</p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class RecycleController implements java.io.Serializable {

    ArrayList<Recycle> recycleArray = new ArrayList<Recycle>();
    ArrayList<Integer> priorityArray = new ArrayList<Integer>();
    private int currentPriorityLevel = 100;
    private int minimumPriorityLevel = 100;
    private int maximumPriorityLevel = 100;

    /**
     * <p>Constructor for RecycleController.</p>
     */
    public RecycleController() {
    }

    /**
     * <p>init.</p>
     */
    public void init() {
        for (Recycle recyc : recycleArray) {
            if (recyc.getPriority() < minimumPriorityLevel)
                minimumPriorityLevel = recyc.getPriority();
            if (recyc.getPriority() > maximumPriorityLevel)
                maximumPriorityLevel = recyc.getPriority();

        }

        currentPriorityLevel = minimumPriorityLevel;

    }

    /**
     * <p>resetPriorityLevel.</p>
     */
    public void resetPriorityLevel() {
        currentPriorityLevel = minimumPriorityLevel;
    }

    /**
     * <p>addRecycle.</p>
     *
     * @param recycle a {@link neqsim.processSimulation.processEquipment.util.Recycle} object
     */
    public void addRecycle(Recycle recycle) {
        recycleArray.add(recycle);
        priorityArray.add(recycle.getPriority());
    }

    /**
     * <p>doSolveRecycle.</p>
     *
     * @param recycle a {@link neqsim.processSimulation.processEquipment.util.Recycle} object
     * @return a boolean
     */
    public boolean doSolveRecycle(Recycle recycle) {
        if (recycle.getPriority() == getCurrentPriorityLevel())
            return true;
        else
            return false;
    }

    /**
     * <p>isHighestPriority.</p>
     *
     * @param recycle a {@link neqsim.processSimulation.processEquipment.util.Recycle} object
     * @return a boolean
     */
    public boolean isHighestPriority(Recycle recycle) {
        if (recycle.getPriority() == maximumPriorityLevel)
            return true;
        else
            return false;
    }

    /**
     * <p>solvedCurrentPriorityLevel.</p>
     *
     * @return a boolean
     */
    public boolean solvedCurrentPriorityLevel() {
        for (Recycle recyc : recycleArray) {
            if (recyc.getPriority() == currentPriorityLevel) {
                if (!recyc.solved())
                    return false;
            }
        }
        return true;
    }

    /**
     * <p>nextPriorityLevel.</p>
     */
    public void nextPriorityLevel() {
        currentPriorityLevel = maximumPriorityLevel;
    }

    /**
     * <p>hasLoverPriorityLevel.</p>
     *
     * @return a boolean
     */
    public boolean hasLoverPriorityLevel() {
        if (currentPriorityLevel > minimumPriorityLevel) {
            return true;
        } else
            return false;
    }

    /**
     * <p>hasHigherPriorityLevel.</p>
     *
     * @return a boolean
     */
    public boolean hasHigherPriorityLevel() {
        if (currentPriorityLevel < maximumPriorityLevel) {
            return true;
        } else
            return false;
    }

    /**
     * <p>solvedAll.</p>
     *
     * @return a boolean
     */
    public boolean solvedAll() {
        for (Recycle recyc : recycleArray) {
            if (!recyc.solved())
                return false;
        }
        return true;
    }

    /**
     * <p>clear.</p>
     */
    public void clear() {
        recycleArray.clear();
        priorityArray.clear();
    }

    /**
     * <p>main.</p>
     *
     * @param args an array of {@link java.lang.String} objects
     */
    public static void main(String[] args) {
    }

    /**
     * <p>Getter for the field <code>currentPriorityLevel</code>.</p>
     *
     * @return a int
     */
    public int getCurrentPriorityLevel() {
        return currentPriorityLevel;
    }

    /**
     * <p>Setter for the field <code>currentPriorityLevel</code>.</p>
     *
     * @param currentPriorityLevel a int
     */
    public void setCurrentPriorityLevel(int currentPriorityLevel) {
        this.currentPriorityLevel = currentPriorityLevel;
    }
}
