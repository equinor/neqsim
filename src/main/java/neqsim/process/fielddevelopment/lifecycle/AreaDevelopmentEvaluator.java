package neqsim.process.fielddevelopment.lifecycle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import neqsim.process.fielddevelopment.lifecycle.AreaDevelopmentResult.OptionResult;

/** Executes and ranks greenfield and multi-host routing alternatives on a common basis. */
public final class AreaDevelopmentEvaluator {

  /** Evaluates every independent option and ranks eligible options by descending after-tax NPV. */
  public AreaDevelopmentResult evaluate(AreaDevelopmentPortfolio portfolio) {
    if (portfolio == null || portfolio.getOptions().isEmpty()) {
      throw new IllegalArgumentException("area-development portfolio must contain at least one option");
    }
    FieldLifecycleEvaluator lifecycleEvaluator = new FieldLifecycleEvaluator();
    List<OptionResult> results = new ArrayList<OptionResult>();
    for (AreaDevelopmentOption option : portfolio.getOptions()) {
      results.add(new OptionResult(option, lifecycleEvaluator.evaluate(option.getLifecycleConcept())));
    }
    Collections.sort(results, new Comparator<OptionResult>() {
      @Override
      public int compare(OptionResult first, OptionResult second) {
        if (first.isEligible() != second.isEligible()) {
          return first.isEligible() ? -1 : 1;
        }
        return Double.compare(second.getLifecycleResult().getNpvMusd(), first.getLifecycleResult().getNpvMusd());
      }
    });
    return new AreaDevelopmentResult(portfolio.getAreaName(), results);
  }
}

