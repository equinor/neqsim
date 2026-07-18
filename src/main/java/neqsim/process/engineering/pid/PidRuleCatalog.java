package neqsim.process.engineering.pid;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Ordered collection of unique P&amp;ID synthesis rules. */
public final class PidRuleCatalog implements Serializable {
  private static final long serialVersionUID = 1000L;
  private final List<PidDesignRule> rules = new ArrayList<PidDesignRule>();
  private final Set<String> ruleIds = new LinkedHashSet<String>();

  public PidRuleCatalog add(PidDesignRule rule) {
    if (rule == null || rule.getId() == null || rule.getId().trim().isEmpty()) {
      throw new IllegalArgumentException("rule and rule id are required");
    }
    if (!ruleIds.add(rule.getId())) {
      throw new IllegalArgumentException("duplicate P&ID rule id: " + rule.getId());
    }
    rules.add(rule);
    Collections.sort(rules, new Comparator<PidDesignRule>() {
      @Override
      public int compare(PidDesignRule left, PidDesignRule right) {
        int byOrder = Integer.compare(left.getOrder(), right.getOrder());
        return byOrder != 0 ? byOrder : left.getId().compareTo(right.getId());
      }
    });
    return this;
  }

  public List<PidDesignRule> getRules() {
    return Collections.unmodifiableList(rules);
  }
}
