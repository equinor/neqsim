package neqsim.process.safety;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.controllerdevice.ControllerDeviceInterface;
import neqsim.process.equipment.ProcessEquipmentBaseClass;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.processmodel.ProcessSystem;

/**
 * Immutable description of a process safety scenario.
 *
 * <p>The scenario captures a set of perturbations such as blocked outlets or loss of utilities
 * along with optional custom manipulators. The perturbations are applied to a scenario specific
 * copy of a {@link ProcessSystem} prior to execution.</p>
 */
public final class ProcessSafetyScenario implements Serializable {
  private static final long serialVersionUID = 1L;
  private static final Logger logger = LogManager.getLogger(ProcessSafetyScenario.class);

  private final String name;
  private final List<String> blockedOutletUnits;
  private final List<String> utilityLossUnits;
  private final Map<String, Double> controllerSetPointOverrides;
  private final Map<String, Consumer<ProcessEquipmentInterface>> customManipulators;

  private ProcessSafetyScenario(Builder builder) {
    this.name = builder.name;
    this.blockedOutletUnits = Collections.unmodifiableList(new ArrayList<>(builder.blockedOutletUnits));
    this.utilityLossUnits = Collections.unmodifiableList(new ArrayList<>(builder.utilityLossUnits));
    this.controllerSetPointOverrides =
        Collections.unmodifiableMap(new LinkedHashMap<>(builder.controllerSetPointOverrides));
    this.customManipulators = Collections.unmodifiableMap(new LinkedHashMap<>(builder.customManipulators));
  }

  public String getName() {
    return name;
  }

  public List<String> getBlockedOutletUnits() {
    return blockedOutletUnits;
  }

  public List<String> getUtilityLossUnits() {
    return utilityLossUnits;
  }

  public Map<String, Double> getControllerSetPointOverrides() {
    return controllerSetPointOverrides;
  }

  public Map<String, Consumer<ProcessEquipmentInterface>> getCustomManipulators() {
    return customManipulators;
  }

  /**
   * Apply the configured perturbations to the provided {@link ProcessSystem} instance.
   *
   * @param processSystem process system to manipulate
   */
  public void applyTo(ProcessSystem processSystem) {
    Objects.requireNonNull(processSystem, "processSystem");

    for (String unitName : blockedOutletUnits) {
      ProcessEquipmentInterface unit = processSystem.getUnit(unitName);
      if (unit == null) {
        logger.warn("Unable to block outlet for unit '{}' because it was not found in scenario '{}'.",
            unitName, name);
        continue;
      }
      unit.setSpecification("BLOCKED_OUTLET");
      unit.setRegulatorOutSignal(0.0);
      deactivateController(unit);
      markUnitInactive(unit);
    }

    for (String unitName : utilityLossUnits) {
      ProcessEquipmentInterface unit = processSystem.getUnit(unitName);
      if (unit == null) {
        logger.warn("Unable to mark utility loss for unit '{}' because it was not found in scenario '{}'.",
            unitName, name);
        continue;
      }
      unit.setSpecification("UTILITY_LOSS");
      unit.setRegulatorOutSignal(0.0);
      deactivateController(unit);
      markUnitInactive(unit);
    }

    controllerSetPointOverrides.forEach((unitName, setPoint) -> {
      ProcessEquipmentInterface unit = processSystem.getUnit(unitName);
      if (unit == null) {
        logger.warn("Unable to override controller set point for unit '{}' in scenario '{}' because the"
            + " unit was not found.", unitName, name);
        return;
      }
      ControllerDeviceInterface controller = unit.getController();
      if (controller == null) {
        logger.warn("Unit '{}' in scenario '{}' has no controller to override.", unitName, name);
        return;
      }
      controller.setControllerSetPoint(setPoint);
    });

    customManipulators.forEach((unitName, manipulator) -> {
      ProcessEquipmentInterface unit = processSystem.getUnit(unitName);
      if (unit == null) {
        logger.warn("Unable to apply custom manipulator for unit '{}' in scenario '{}' because the unit"
            + " was not found.", unitName, name);
        return;
      }
      manipulator.accept(unit);
    });
  }

  private void deactivateController(ProcessEquipmentInterface unit) {
    ControllerDeviceInterface controller = unit.getController();
    if (controller != null && controller.isActive()) {
      controller.setActive(false);
    }
  }

  private void markUnitInactive(ProcessEquipmentInterface unit) {
    if (unit instanceof ProcessEquipmentBaseClass) {
      ((ProcessEquipmentBaseClass) unit).isActive(false);
    }
  }

  /**
   * Returns the combined set of unit names affected by the scenario.
   *
   * @return affected unit names
   */
  public Set<String> getTargetUnits() {
    LinkedHashSet<String> targets = new LinkedHashSet<>();
    targets.addAll(blockedOutletUnits);
    targets.addAll(utilityLossUnits);
    targets.addAll(controllerSetPointOverrides.keySet());
    targets.addAll(customManipulators.keySet());
    return Collections.unmodifiableSet(targets);
  }

  public static Builder builder(String name) {
    return new Builder(name);
  }

  /** Builder for {@link ProcessSafetyScenario}. */
  public static final class Builder {
    private final String name;
    private final Set<String> blockedOutletUnits = new LinkedHashSet<>();
    private final Set<String> utilityLossUnits = new LinkedHashSet<>();
    private final Map<String, Double> controllerSetPointOverrides = new LinkedHashMap<>();
    private final Map<String, Consumer<ProcessEquipmentInterface>> customManipulators =
        new LinkedHashMap<>();

    private Builder(String name) {
      this.name = Objects.requireNonNull(name, "name");
    }

    public Builder blockOutlet(String unitName) {
      Objects.requireNonNull(unitName, "unitName");
      blockedOutletUnits.add(unitName);
      return this;
    }

    public Builder blockOutlets(Collection<String> unitNames) {
      Objects.requireNonNull(unitNames, "unitNames");
      unitNames.stream().filter(Objects::nonNull).forEach(blockedOutletUnits::add);
      return this;
    }

    public Builder utilityLoss(String unitName) {
      Objects.requireNonNull(unitName, "unitName");
      utilityLossUnits.add(unitName);
      return this;
    }

    public Builder utilityLosses(Collection<String> unitNames) {
      Objects.requireNonNull(unitNames, "unitNames");
      unitNames.stream().filter(Objects::nonNull).forEach(utilityLossUnits::add);
      return this;
    }

    public Builder controllerSetPoint(String unitName, double setPoint) {
      Objects.requireNonNull(unitName, "unitName");
      controllerSetPointOverrides.put(unitName, setPoint);
      return this;
    }

    public Builder customManipulator(String unitName, Consumer<ProcessEquipmentInterface> manipulator) {
      Objects.requireNonNull(unitName, "unitName");
      Objects.requireNonNull(manipulator, "manipulator");
      customManipulators.put(unitName, manipulator);
      return this;
    }

    public ProcessSafetyScenario build() {
      return new ProcessSafetyScenario(this);
    }
  }
}
