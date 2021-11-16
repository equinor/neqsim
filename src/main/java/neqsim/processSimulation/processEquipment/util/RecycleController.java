package neqsim.processSimulation.processEquipment.util;

import java.util.ArrayList;

public class RecycleController implements java.io.Serializable {
    ArrayList<Recycle> recycleArray = new ArrayList<Recycle>();
    ArrayList<Integer> priorityArray = new ArrayList<Integer>();
    private int currentPriorityLevel = 100;
    private int minimumPriorityLevel = 100;
    private int maximumPriorityLevel = 100;

    public RecycleController() {}

    public void init() {
        for (Recycle recyc : recycleArray) {
            if (recyc.getPriority() < minimumPriorityLevel)
                minimumPriorityLevel = recyc.getPriority();
            if (recyc.getPriority() > maximumPriorityLevel)
                maximumPriorityLevel = recyc.getPriority();
        }

        currentPriorityLevel = minimumPriorityLevel;
    }

    public void resetPriorityLevel() {
        currentPriorityLevel = minimumPriorityLevel;
    }

    public void addRecycle(Recycle recycle) {
        recycleArray.add(recycle);
        priorityArray.add(recycle.getPriority());
    }

    public boolean doSolveRecycle(Recycle recycle) {
        if (recycle.getPriority() == getCurrentPriorityLevel())
            return true;
        else
            return false;
    }

    public boolean isHighestPriority(Recycle recycle) {
        if (recycle.getPriority() == maximumPriorityLevel)
            return true;
        else
            return false;
    }

    public boolean solvedCurrentPriorityLevel() {
        for (Recycle recyc : recycleArray) {
            if (recyc.getPriority() == currentPriorityLevel) {
                if (!recyc.solved())
                    return false;
            }
        }
        return true;
    }

    public void nextPriorityLevel() {
        currentPriorityLevel = maximumPriorityLevel;
    }

    public boolean hasLoverPriorityLevel() {
        if (currentPriorityLevel > minimumPriorityLevel) {
            return true;
        } else
            return false;
    }

    public boolean hasHigherPriorityLevel() {
        if (currentPriorityLevel < maximumPriorityLevel) {
            return true;
        } else
            return false;
    }

    public boolean solvedAll() {
        for (Recycle recyc : recycleArray) {
            if (!recyc.solved())
                return false;
        }
        return true;
    }

    public void clear() {
        recycleArray.clear();
        priorityArray.clear();
    }

    public static void main(String[] args) {}

    public int getCurrentPriorityLevel() {
        return currentPriorityLevel;
    }

    public void setCurrentPriorityLevel(int currentPriorityLevel) {
        this.currentPriorityLevel = currentPriorityLevel;
    }
}
