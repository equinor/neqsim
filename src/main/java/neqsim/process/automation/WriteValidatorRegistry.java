package neqsim.process.automation;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import neqsim.process.equipment.ProcessEquipmentInterface;

/**
 * Registry of {@link WriteValidator} instances keyed by equipment class. The registry is consulted
 * by {@link ProcessAutomation#setVariableValueValidated(String, double, String)} and
 * {@link ProcessAutomation#setValuesTransactional(java.util.Map, String)} before any write is
 * applied.
 *
 * <p>
 * The registry supports multiple validators per equipment class (all are evaluated; the first
 * {@link WriteValidationResult.Severity#ERROR ERROR} result stops the check). Subclass dispatch
 * walks the equipment class hierarchy, so a validator registered for a base equipment class is also
 * invoked for any subclass that does not register its own validator.
 * </p>
 *
 * <p>
 * Construct a registry with the default validator set via {@link #createDefault()}; this populates
 * validators for {@code Compressor}, {@code Pump}, {@code Separator}, {@code ThrottlingValve},
 * {@code Heater}, and {@code Cooler}.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class WriteValidatorRegistry implements Serializable {
  private static final long serialVersionUID = 1000L;

  private final Map<Class<? extends ProcessEquipmentInterface>, List<WriteValidator>> validators =
      new ConcurrentHashMap<Class<? extends ProcessEquipmentInterface>, List<WriteValidator>>();

  /**
   * Creates an empty registry. Use {@link #createDefault()} for the standard NeqSim set.
   */
  public WriteValidatorRegistry() {
    // intentionally empty
  }

  /**
   * Creates a registry pre-populated with the default validators for compressors, pumps,
   * separators, throttling valves and heaters/coolers.
   *
   * @return a registry ready for production use
   */
  public static WriteValidatorRegistry createDefault() {
    WriteValidatorRegistry reg = new WriteValidatorRegistry();
    reg.register(new DefaultWriteValidators.CompressorWriteValidator());
    reg.register(new DefaultWriteValidators.PumpWriteValidator());
    reg.register(new DefaultWriteValidators.SeparatorWriteValidator());
    reg.register(new DefaultWriteValidators.ThrottlingValveWriteValidator());
    reg.register(new DefaultWriteValidators.HeaterWriteValidator());
    reg.register(new DefaultWriteValidators.CoolerWriteValidator());
    return reg;
  }

  /**
   * Registers a validator. Multiple validators may be registered for the same equipment class; all
   * are evaluated in registration order.
   *
   * @param validator the validator to register; must not be null
   */
  public void register(WriteValidator validator) {
    if (validator == null) {
      throw new IllegalArgumentException("validator must not be null");
    }
    Class<? extends ProcessEquipmentInterface> key = validator.getEquipmentClass();
    List<WriteValidator> list = validators.get(key);
    if (list == null) {
      list = Collections.synchronizedList(new ArrayList<WriteValidator>());
      validators.put(key, list);
    }
    list.add(validator);
  }

  /**
   * Removes all validators registered for the given equipment class.
   *
   * @param equipmentClass the equipment class whose validators should be removed
   */
  public void unregister(Class<? extends ProcessEquipmentInterface> equipmentClass) {
    validators.remove(equipmentClass);
  }

  /**
   * Returns the validators applicable to the given equipment, walking the class hierarchy. The
   * most-specific class's validators come first; base-class validators come last.
   *
   * @param equipment the target equipment
   * @return an unmodifiable list of applicable validators (possibly empty)
   */
  public List<WriteValidator> getValidatorsFor(ProcessEquipmentInterface equipment) {
    if (equipment == null) {
      return Collections.emptyList();
    }
    List<WriteValidator> out = new ArrayList<WriteValidator>();
    Class<?> cls = equipment.getClass();
    while (cls != null && ProcessEquipmentInterface.class.isAssignableFrom(cls)) {
      List<WriteValidator> list = validators.get(cls);
      if (list != null) {
        synchronized (list) {
          out.addAll(list);
        }
      }
      cls = cls.getSuperclass();
    }
    return Collections.unmodifiableList(out);
  }

  /**
   * Runs every applicable validator against the proposed write. The first
   * {@link WriteValidationResult.Severity#ERROR ERROR} result is returned immediately; otherwise
   * the most severe non-error result is returned (or {@link WriteValidationResult#ok()} when all
   * validators pass).
   *
   * @param equipment the target equipment; never null
   * @param propertyPath the local property path on the equipment
   * @param value the proposed value
   * @param unit the unit of measure of {@code value}, or null for the property's default unit
   * @return the aggregated validation result; never null
   */
  public WriteValidationResult validate(ProcessEquipmentInterface equipment, String propertyPath,
      double value, String unit) {
    if (equipment == null) {
      throw new IllegalArgumentException("equipment must not be null");
    }
    List<WriteValidator> applicable = getValidatorsFor(equipment);
    WriteValidationResult highest = WriteValidationResult.ok();
    for (WriteValidator v : applicable) {
      WriteValidationResult r = v.validate(equipment, propertyPath, value, unit);
      if (r == null) {
        continue;
      }
      if (r.getSeverity() == WriteValidationResult.Severity.ERROR) {
        return r;
      }
      if (r.getSeverity() == WriteValidationResult.Severity.WARNING) {
        highest = r;
      }
    }
    return highest;
  }

  /**
   * Returns the equipment classes for which at least one validator is registered. Used by tests and
   * diagnostics.
   *
   * @return an unmodifiable snapshot of the keys
   */
  public Map<Class<? extends ProcessEquipmentInterface>, Integer> getRegisteredClasses() {
    Map<Class<? extends ProcessEquipmentInterface>, Integer> out =
        new LinkedHashMap<Class<? extends ProcessEquipmentInterface>, Integer>();
    for (Map.Entry<Class<? extends ProcessEquipmentInterface>, List<WriteValidator>> e : validators
        .entrySet()) {
      out.put(e.getKey(), e.getValue().size());
    }
    return Collections.unmodifiableMap(out);
  }
}
