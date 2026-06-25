package neqsim.process.logic.action;

import neqsim.process.equipment.splitter.Splitter;
import neqsim.process.logic.LogicAction;

/**
 * Action to set splitter split factors.
 *
 * @author ESOL
 * @version 1.0
 */
public class SetSplitterAction implements LogicAction {
  private final Splitter splitter;
  private final double[] splitFactors;
  private boolean executed = false;

  /**
   * Creates a set splitter action.
   *
   * @param splitter splitter to configure
   * @param splitFactors new split factors
   */
  public SetSplitterAction(Splitter splitter, double[] splitFactors) {
    this.splitter = splitter;
    this.splitFactors = splitFactors.clone();
  }

  @Override
  public void execute() {
    if (!executed) {
      splitter.setSplitFactors(splitFactors);
      executed = true;
    }
  }

  @Override
  public String getDescription() {
    StringBuilder sb = new StringBuilder("Set splitter " + splitter.getName() + " to [");
    for (int i = 0; i < splitFactors.length; i++) {
      if (i > 0) {
        sb.append(", ");
      }
      sb.append(String.format("%.2f", splitFactors[i]));
    }
    sb.append("]");
    return sb.toString();
  }

  @Override
  public boolean isComplete() {
    return executed; // Instantaneous action
  }

  @Override
  public String getTargetName() {
    return splitter.getName();
  }
}
