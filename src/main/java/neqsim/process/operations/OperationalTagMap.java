package neqsim.process.operations;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import neqsim.process.automation.ProcessAutomation;
import neqsim.process.automation.SimulationVariable;
import neqsim.process.automation.SimulationVariable.VariableType;
import neqsim.process.measurementdevice.InstrumentTagRole;
import neqsim.process.measurementdevice.MeasurementDeviceInterface;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.util.validation.ValidationResult;

/**
 * Plant-agnostic map from logical operating tags to NeqSim measurement devices and automation
 * addresses.
 *
 * <p>
 * The map intentionally delegates field-data writes to existing measurement devices and model
 * writes to {@link ProcessAutomation}. It does not replace the NeqSim instrumentation model; it
 * only provides a reusable bridge for P&amp;ID and tagreader workflows.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class OperationalTagMap implements Serializable {
  private static final long serialVersionUID = 1L;

  private final Map<String, OperationalTagBinding> bindings =
      new LinkedHashMap<String, OperationalTagBinding>();

  /**
   * Adds a binding to the map.
   *
   * @param binding binding to add
   * @return this map for fluent setup
   * @throws IllegalArgumentException if the binding is null or the logical tag already exists
   */
  public OperationalTagMap addBinding(OperationalTagBinding binding) {
    if (binding == null) {
      throw new IllegalArgumentException("binding must not be null");
    }
    if (bindings.containsKey(binding.getLogicalTag())) {
      throw new IllegalArgumentException("Duplicate logical tag: " + binding.getLogicalTag());
    }
    bindings.put(binding.getLogicalTag(), binding);
    return this;
  }

  /**
   * Returns a binding by logical tag.
   *
   * @param logicalTag logical tag name
   * @return binding, or null if not found
   */
  public OperationalTagBinding getBinding(String logicalTag) {
    return bindings.get(logicalTag);
  }

  /**
   * Returns all bindings in insertion order.
   *
   * @return unmodifiable binding list
   */
  public List<OperationalTagBinding> getBindings() {
    return Collections.unmodifiableList(new ArrayList<OperationalTagBinding>(bindings.values()));
  }

  /**
   * Checks whether a logical tag is registered.
   *
   * @param logicalTag logical tag name
   * @return true when the map contains the tag
   */
  public boolean containsLogicalTag(String logicalTag) {
    return bindings.containsKey(logicalTag);
  }

  /**
   * Applies field data values to the process through measurement devices and automation addresses.
   *
   * <p>
   * Values may be keyed by public logical tag or by private historian tag. For bindings with role
   * {@link InstrumentTagRole#INPUT}, automation addresses are written through
   * {@link ProcessAutomation#setVariableValue(String, double, String)} and measurement-device field
   * values are pushed through {@link ProcessSystem#applyFieldInputs()}.
   * </p>
   *
   * @param process process system to update
   * @param fieldData values keyed by logical tag or historian tag
   * @return map of logical tags that were applied or stored
   */
  public Map<String, Double> applyFieldData(ProcessSystem process, Map<String, Double> fieldData) {
    if (process == null) {
      throw new IllegalArgumentException("process must not be null");
    }
    if (fieldData == null) {
      throw new IllegalArgumentException("fieldData must not be null");
    }

    Map<String, Double> measurementFieldData = new HashMap<String, Double>();
    Map<String, Double> applied = new LinkedHashMap<String, Double>();
    ProcessAutomation automation = process.getAutomation();

    for (OperationalTagBinding binding : bindings.values()) {
      Double value = findValue(binding, fieldData);
      if (value == null) {
        continue;
      }
      if (binding.hasHistorianTag()) {
        measurementFieldData.put(binding.getHistorianTag(), value);
      }
      if (binding.getRole() == InstrumentTagRole.INPUT && binding.hasAutomationAddress()) {
        automation.setVariableValue(binding.getAutomationAddress(), value, binding.getUnit());
      }
      applied.put(binding.getLogicalTag(), value);
    }

    if (!measurementFieldData.isEmpty()) {
      process.setFieldData(measurementFieldData);
      process.applyFieldInputs();
    }
    return applied;
  }

  /**
   * Reads current model values for all resolvable bindings.
   *
   * @param process process system to read
   * @return map of logical tag to model value
   */
  public Map<String, Double> readValues(ProcessSystem process) {
    if (process == null) {
      throw new IllegalArgumentException("process must not be null");
    }
    Map<String, Double> values = new LinkedHashMap<String, Double>();
    ProcessAutomation automation = process.getAutomation();

    for (OperationalTagBinding binding : bindings.values()) {
      if (binding.hasAutomationAddress()) {
        values.put(binding.getLogicalTag(),
            automation.getVariableValue(binding.getAutomationAddress(), binding.getUnit()));
      } else if (binding.hasHistorianTag()) {
        MeasurementDeviceInterface device = process.getMeasurementDeviceByTag(
            binding.getHistorianTag());
        if (device != null) {
          String unit = binding.getUnit().isEmpty() ? device.getUnit() : binding.getUnit();
          values.put(binding.getLogicalTag(), device.getMeasuredValue(unit));
        }
      }
    }
    return values;
  }

  /**
   * Validates that bindings can be resolved against the supplied process.
   *
   * @param process process system used for validation
   * @return validation result with actionable issues
   */
  public ValidationResult validate(ProcessSystem process) {
    ValidationResult result = new ValidationResult("OperationalTagMap");
    if (process == null) {
      result.addError("process", "Process system is null", "Provide a ProcessSystem instance.");
      return result;
    }
    if (bindings.isEmpty()) {
      result.addWarning("tag-map", "No operational tag bindings are configured.",
          "Add logical bindings before applying plant data or running scenarios.");
      return result;
    }

    Set<String> historianTags = new HashSet<String>();
    ProcessAutomation automation = process.getAutomation();
    for (OperationalTagBinding binding : bindings.values()) {
      validateBinding(process, automation, binding, historianTags, result);
    }
    return result;
  }

  /**
   * Finds a value using logical tag first and historian tag second.
   *
   * @param binding binding that defines accepted keys
   * @param fieldData source data map
   * @return matched value, or null when no value is present
   */
  private Double findValue(OperationalTagBinding binding, Map<String, Double> fieldData) {
    if (fieldData.containsKey(binding.getLogicalTag())) {
      return fieldData.get(binding.getLogicalTag());
    }
    if (binding.hasHistorianTag() && fieldData.containsKey(binding.getHistorianTag())) {
      return fieldData.get(binding.getHistorianTag());
    }
    return null;
  }

  /**
   * Validates one binding against measurement-device and automation registries.
   *
   * @param process process system containing measurement devices
   * @param automation process automation facade
   * @param binding binding to validate
   * @param historianTags set of previously seen historian tags
   * @param result validation result to update
   */
  private void validateBinding(ProcessSystem process, ProcessAutomation automation,
      OperationalTagBinding binding, Set<String> historianTags, ValidationResult result) {
    if (!binding.hasHistorianTag() && !binding.hasAutomationAddress()) {
      result.addError("tag-map", "Binding " + binding.getLogicalTag()
          + " has neither historian tag nor automation address.",
          "Set a historian tag, automation address, or both.");
    }

    if (binding.hasHistorianTag()) {
      if (!historianTags.add(binding.getHistorianTag())) {
        result.addWarning("tag-map", "Historian tag " + binding.getHistorianTag()
            + " is used by more than one logical binding.",
            "Check that duplicated tags are intentional aliases.");
      }
      if (process.getMeasurementDeviceByTag(binding.getHistorianTag()) == null) {
        result.addWarning("measurement", "No measurement device carries tag "
            + binding.getHistorianTag() + ".",
            "Attach the tag to a MeasurementDeviceInterface or use an automation address.");
      }
    }

    if (binding.hasAutomationAddress()) {
      SimulationVariable variable = findAutomationVariable(automation, binding.getAutomationAddress());
      if (variable == null) {
        result.addError("automation", "Automation address " + binding.getAutomationAddress()
            + " could not be resolved.",
            "Use process.getAutomation().getVariableList(unitName) to discover valid addresses.");
      } else if (binding.getRole() == InstrumentTagRole.INPUT
          && variable.getType() != VariableType.INPUT) {
        result.addError("automation", "Automation address " + binding.getAutomationAddress()
            + " is not writable.", "Bind INPUT tags to variables of type INPUT.");
      }
    }

    if (binding.getUnit().isEmpty()) {
      result.addWarning("unit", "Binding " + binding.getLogicalTag() + " has no unit.",
          "Set an explicit engineering unit to avoid ambiguous historian data.");
    }
  }

  /**
   * Finds an automation variable descriptor by address.
   *
   * @param automation automation facade
   * @param address dot-notation automation address
   * @return variable descriptor, or null when not found
   */
  private SimulationVariable findAutomationVariable(ProcessAutomation automation, String address) {
    String unitName = extractUnitName(address);
    if (unitName.isEmpty()) {
      return null;
    }
    try {
      List<SimulationVariable> variables = automation.getVariableList(unitName);
      for (SimulationVariable variable : variables) {
        if (address.equals(variable.getAddress())) {
          return variable;
        }
      }
      return null;
    } catch (IllegalArgumentException ex) {
      return null;
    }
  }

  /**
   * Extracts the unit name from a ProcessAutomation address.
   *
   * @param address address such as {@code "Area::Valve.percentValveOpening"}
   * @return unit name, preserving the optional area prefix
   */
  private String extractUnitName(String address) {
    if (address == null) {
      return "";
    }
    int areaIndex = address.indexOf("::");
    int searchStart = areaIndex >= 0 ? areaIndex + 2 : 0;
    int dotIndex = address.indexOf('.', searchStart);
    if (dotIndex < 0) {
      return "";
    }
    return address.substring(0, dotIndex);
  }
}