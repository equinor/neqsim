package neqsim.process.engineering;

import neqsim.process.equipment.ProcessEquipmentInterface;

/** Extension point for project, company, or equipment-specific engineering rule packs. */
public interface EngineeringRule {
  /**
   * Tests whether the rule applies to an equipment item.
   *
   * @param equipment process equipment
   * @return true when the rule should be applied
   */
  boolean supports(ProcessEquipmentInterface equipment);

  /**
   * Adds traceable requirements or metadata to the project.
   *
   * @param project engineering project under construction
   * @param equipment supported process equipment
   */
  void apply(EngineeringProject project, ProcessEquipmentInterface equipment);
}
