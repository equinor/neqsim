package neqsim.process.equipment.distillation.internals;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.distillation.RateBasedPackedColumn;

/**
 * Regression tests for unambiguous packing selection and reference-data precedence.
 *
 * @author NeqSim
 * @version 1.0
 */
public class PackingSelectionRegressionTest {
  /** Preserve reference estimates instead of silently overwriting them with CSV rows. */
  @Test
  public void testBuiltInReferenceDataIsPreserved() {
    String[] names = {"Pall-Ring-25", "Pall-Ring-38", "Pall-Ring-50", "Raschig-Ring-25"};
    double[] factors = {157.0, 92.0, 66.0, 580.0};
    for (int i = 0; i < names.length; i++) {
      PackingSpecification specification = PackingSpecificationLibrary.get(names[i]);
      Assertions.assertEquals(factors[i], specification.getPackingFactor(), 1.0e-12, names[i]);
      Assertions.assertTrue(specification.getSource().startsWith("built-in"));
    }
  }

  /** Resolve both materials without allowing the plastic CSV row to replace metal. */
  @Test
  public void testMaterialQualifiedNamesRemainDistinct() {
    PackingSpecification metal = PackingSpecificationLibrary.get("Pall-Ring-50-metal");
    PackingSpecification plastic = PackingSpecificationLibrary.get("Pall-Ring-50-plastic");
    Assertions.assertNotNull(metal);
    Assertions.assertNotNull(plastic);
    Assertions.assertEquals("metal", metal.getMaterial());
    Assertions.assertEquals("plastic", plastic.getMaterial());
    Assertions.assertSame(metal, PackingSpecificationLibrary.get("pall ring 50"));
    Assertions.assertEquals(120.0, metal.getSpecificSurfaceArea(), 1.0e-12);
    Assertions.assertEquals(111.1, plastic.getSpecificSurfaceArea(), 1.0e-12);
    Assertions.assertEquals(0.033, plastic.getCriticalSurfaceTension(), 1.0e-12);
  }

  /** Keep unqualified placeholder rows out of the selectable engineering data. */
  @Test
  public void testPlaceholdersAreNotRegistered() {
    String[] placeholders = {"Mellapak250X", "PallRingPacking", "RachigRingPacking", "BerlSaddlePacking"};
    for (String name : placeholders) {
      Assertions.assertNull(PackingSpecificationLibrary.get(name), name);
    }
  }

  /** A mistyped requested packing must leave the previous selection unchanged. */
  @Test
  public void testUnknownExplicitSelectionFails() {
    RateBasedPackedColumn column = new RateBasedPackedColumn("packing selection");
    PackingSpecification original = column.getPackingSpecification();
    Assertions.assertThrows(IllegalArgumentException.class, () -> column.setPackingType("Mellapak-250Z"));
    Assertions.assertSame(original, column.getPackingSpecification());
    PackingHydraulicsCalculator calculator = new PackingHydraulicsCalculator();
    calculator.setPackingPreset("IMTP-50");
    Assertions.assertThrows(IllegalArgumentException.class, () -> calculator.setPackingPreset("IMTP-51"));
    Assertions.assertEquals("IMTP-50", calculator.getPackingName());
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> calculator.setStructuredPackingPreset("Mellapak-250Z"));
    Assertions.assertEquals("IMTP-50", calculator.getPackingName());
  }

  /** A default calculator must use the same complete reference data as an explicit selection. */
  @Test
  public void testDefaultHydraulicsMatchesReferenceSelection() {
    PackingHydraulicsCalculator defaults = new PackingHydraulicsCalculator();
    PackingHydraulicsCalculator explicit = new PackingHydraulicsCalculator();
    explicit.setPackingPreset("Pall-Ring-50");
    Assertions.assertEquals(explicit.getVoidFraction(), defaults.getVoidFraction(), 1.0e-12);
    Assertions.assertEquals(0.96, defaults.getVoidFraction(), 1.0e-12);
  }
}
