package neqsim.process.fielddevelopment.lifecycle;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Independent host-routing and greenfield options evaluated for one discovery or area. */
public final class AreaDevelopmentPortfolio implements Serializable {
  private static final long serialVersionUID = 1000L;
  private final String areaName;
  private final List<AreaDevelopmentOption> options = new ArrayList<AreaDevelopmentOption>();

  /** Creates an empty area-development portfolio. */
  public AreaDevelopmentPortfolio(String areaName) {
    if (areaName == null || areaName.trim().isEmpty()) {
      throw new IllegalArgumentException("area name is required");
    }
    this.areaName = areaName;
  }

  /** Adds an independently assembled greenfield or host-routing option. */
  public AreaDevelopmentPortfolio addOption(AreaDevelopmentOption option) {
    if (option == null) {
      throw new IllegalArgumentException("area-development option is required");
    }
    for (AreaDevelopmentOption existing : options) {
      if (existing.getName().equals(option.getName())) {
        throw new IllegalArgumentException("duplicate area-development option: " + option.getName());
      }
      if (existing.getLifecycleConcept().getModel() == option.getLifecycleConcept().getModel()) {
        throw new IllegalArgumentException("each area-development option must own an independent mutable model");
      }
    }
    options.add(option);
    return this;
  }

  /** Returns area or discovery name. */
  public String getAreaName() {
    return areaName;
  }

  /** Returns immutable development options. */
  public List<AreaDevelopmentOption> getOptions() {
    return Collections.unmodifiableList(options);
  }
}
