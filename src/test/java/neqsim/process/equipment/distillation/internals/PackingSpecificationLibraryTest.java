package neqsim.process.equipment.distillation.internals;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests for packed-column packing specification registry and hydraulic integration.
 */
public class PackingSpecificationLibraryTest {

  @Test
  public void testBuiltInPackingLookupAliases() {
    PackingSpecification pallRing = PackingSpecificationLibrary.get("pall ring 50");
    Assertions.assertNotNull(pallRing);
    Assertions.assertEquals("Pall-Ring-50", pallRing.getName());
    Assertions.assertTrue(pallRing.getSpecificSurfaceArea() > 0.0);

    PackingSpecification structured = PackingSpecificationLibrary.get("Mellapak250Y");
    Assertions.assertNotNull(structured);
    Assertions.assertTrue(structured.isStructured());
    Assertions.assertTrue(structured.getVoidFraction() > 0.9);
  }

  @Test
  public void testCsvPackingDataLoads() {
    Assertions.assertTrue(PackingSpecificationLibrary.contains("Mellapak250X"));
    Assertions.assertTrue(PackingSpecificationLibrary.contains("IMTP 70"));
    Assertions.assertTrue(PackingSpecificationLibrary.getPackingNames().size() >= 15);
  }

  @Test
  public void testHydraulicsCalculatorUsesPackingSpecification() {
    PackingHydraulicsCalculator calculator = new PackingHydraulicsCalculator();
    calculator.setPackingPreset("IMTP-50");
    Assertions.assertEquals("IMTP-50", calculator.getPackingName());
    Assertions.assertEquals("random", calculator.getPackingCategory());

    calculator.setColumnDiameter(1.0);
    calculator.setPackedHeight(4.0);
    calculator.setVaporMassFlow(2.0);
    calculator.setLiquidMassFlow(4.0);
    calculator.setVaporDensity(25.0);
    calculator.setLiquidDensity(900.0);
    calculator.setVaporViscosity(1.2e-5);
    calculator.setLiquidViscosity(8.0e-4);
    calculator.setVaporDiffusivity(1.5e-5);
    calculator.setLiquidDiffusivity(1.0e-9);
    calculator.setSurfaceTension(0.025);
    calculator.calculate();

    Assertions.assertTrue(calculator.getWettedArea() > 0.0);
    Assertions.assertTrue(calculator.getKGa() > 0.0);
    Assertions.assertTrue(calculator.getKLa() > 0.0);
  }

  @Test
  public void testStructuredPackingUsesEquivalentDiameter() {
    PackingHydraulicsCalculator calculator = new PackingHydraulicsCalculator();
    calculator.setStructuredPackingPreset("Mellapak-250Y");
    calculator.setColumnDiameter(1.0);
    calculator.setPackedHeight(4.0);
    calculator.setVaporMassFlow(2.0);
    calculator.setLiquidMassFlow(4.0);
    calculator.setVaporDensity(25.0);
    calculator.setLiquidDensity(900.0);
    calculator.setVaporViscosity(1.2e-5);
    calculator.setLiquidViscosity(8.0e-4);
    calculator.setVaporDiffusivity(1.5e-5);
    calculator.setLiquidDiffusivity(1.0e-9);
    calculator.setSurfaceTension(0.025);
    calculator.calculate();

    Assertions.assertTrue(Double.isFinite(calculator.getKGa()));
    Assertions.assertTrue(Double.isFinite(calculator.getKLa()));
    Assertions.assertTrue(calculator.getKGa() > 0.0);
    Assertions.assertTrue(calculator.getKLa() > 0.0);
  }
}
