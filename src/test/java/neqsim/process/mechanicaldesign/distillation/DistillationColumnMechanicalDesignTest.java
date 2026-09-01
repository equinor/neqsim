package neqsim.process.mechanicaldesign.distillation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.absorber.AbsorptionColumn;
import neqsim.process.equipment.absorber.StrippingColumn;
import neqsim.process.equipment.distillation.DistillationColumn;
import neqsim.process.equipment.distillation.PackedColumn;

/**
 * Tests the shared mechanical-design contract across the gas-liquid column family.
 */
class DistillationColumnMechanicalDesignTest {

  /**
   * Confirms absorbers, strippers, packed contactors, and distillation columns share the capacity design API.
   */
  @Test
  void columnFamilyUsesSharedMechanicalDesign() {
    DistillationColumn distillation = new DistillationColumn("distillation", 3, false, false);
    AbsorptionColumn absorber = new AbsorptionColumn("absorber", 3);
    StrippingColumn stripper = new StrippingColumn("stripper", 3);
    PackedColumn packedContactor = new PackedColumn("packed contactor", false, false);

    assertInstanceOf(DistillationColumnMechanicalDesign.class, distillation.getMechanicalDesign());
    assertInstanceOf(DistillationColumnMechanicalDesign.class, absorber.getMechanicalDesign());
    assertInstanceOf(DistillationColumnMechanicalDesign.class, stripper.getMechanicalDesign());
    assertInstanceOf(DistillationColumnMechanicalDesign.class, packedContactor.getMechanicalDesign());

    DistillationColumnMechanicalDesign stripperDesign = (DistillationColumnMechanicalDesign) stripper
        .getMechanicalDesign();
    stripperDesign.setContactorInternalsType("valve");
    assertEquals("valve", stripperDesign.getContactorInternalsType());
  }
}
