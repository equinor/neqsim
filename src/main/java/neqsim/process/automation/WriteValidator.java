package neqsim.process.automation;

import neqsim.process.equipment.ProcessEquipmentInterface;

/**
 * Strategy interface for validating writes to a specific equipment class before they are applied to
 * the simulation. Concrete implementations check that a proposed value is physically meaningful and
 * consistent with the equipment's current state (for example: outlet pressure greater than inlet
 * pressure for a compressor, efficiency in {@code [0,1]} for a pump, or opening percentage in
 * {@code [0,100]} for a control valve).
 *
 * <p>
 * Validators are registered in {@link WriteValidatorRegistry} and invoked by
 * {@link ProcessAutomation#setVariableValueValidated(String, double, String)} and
 * {@link ProcessAutomation#setValuesTransactional(java.util.Map, String)} before any write is
 * applied. A {@link WriteValidationResult} with {@code valid == false} causes the entire
 * transactional batch to be aborted and rolled back.
 * </p>
 *
 * <p>
 * Implementations must be stateless and thread-safe; they may be invoked concurrently by multiple
 * agents sharing the same registry.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public interface WriteValidator {

  /**
   * Returns the equipment class this validator applies to. The registry uses
   * {@link Class#isAssignableFrom(Class)} for dispatch, so a validator registered for a base class
   * will also be invoked for its subclasses unless a more specific validator is registered.
   *
   * @return the equipment class this validator applies to; must not be null
   */
  Class<? extends ProcessEquipmentInterface> getEquipmentClass();

  /**
   * Validates a proposed write to the given equipment.
   *
   * @param equipment the target equipment instance; never null
   * @param propertyPath the local property path on the equipment (the portion of the address after
   *        the unit name, for example {@code "outletPressure"} or
   *        {@code "feedStream.temperature"}); never null
   * @param value the proposed numerical value
   * @param unit the unit of measure of {@code value}, or {@code null} for the property's default
   *        unit
   * @return a non-null {@link WriteValidationResult}; use {@link WriteValidationResult#ok()} for a
   *         passing check
   */
  WriteValidationResult validate(ProcessEquipmentInterface equipment, String propertyPath,
      double value, String unit);
}
