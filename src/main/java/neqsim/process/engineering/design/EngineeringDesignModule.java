package neqsim.process.engineering.design;

import java.io.Serializable;
import neqsim.process.engineering.calculation.EngineeringCalculationContext;
import neqsim.process.engineering.designcase.EngineeringCaseRunReport;
import neqsim.process.processmodel.ProcessSystem;

/** One discipline calculation participating in the iterative engineering design loop. */
public interface EngineeringDesignModule extends Serializable {
  String getId();

  EngineeringDesignModuleResult evaluate(ProcessSystem process, EngineeringCaseRunReport caseReport,
      EngineeringDesignState state, EngineeringCalculationContext context);
}
