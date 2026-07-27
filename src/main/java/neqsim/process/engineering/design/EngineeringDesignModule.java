package neqsim.process.engineering.design;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import neqsim.process.engineering.calculation.EngineeringCalculationContext;
import neqsim.process.engineering.designcase.EngineeringCaseRunReport;
import neqsim.process.processmodel.ProcessSystem;

/** One discipline calculation participating in the iterative engineering design loop. */
public interface EngineeringDesignModule extends Serializable {
  String getId();

  /**
   * Module IDs whose selected updates must be available before this module is evaluated in an iteration.
   *
   * @return dependency IDs; empty by default
   */
  default List<String> getDependencies() {
    return Collections.emptyList();
  }

  EngineeringDesignModuleResult evaluate(ProcessSystem process, EngineeringCaseRunReport caseReport,
      EngineeringDesignState state, EngineeringCalculationContext context);
}
