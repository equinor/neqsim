package neqsim.thermo.characterization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.system.SystemInterface;

/**
 * Test class for the TBP boiling point correlation implementation in TBP fraction handling.
 */
public class TBPBoilingPointCorrelationTest {

  private SystemInterface testSystem;

  @BeforeEach
  void setUp() {
    testSystem = new neqsim.thermo.system.SystemSrkEos(298.15, 1.0);
    testSystem.getCharacterization().setTBPModel("PedersenPR2"); // to ensure to call Soreide
  }

  @Test
  void testTBPBoilingPointCorrelationDensityCalculation() {
    // Test data from literature - typical petroleum fraction benzene CORRECT ANSWER 0.87 (not so
    // good estimation from TBP model)
    double boilingPoint = 80 + 273.15; // K
    double molarMass = 0.078; // kg/mol


    // Calculate density using TBP boiling point correlation
    double calculatedDensity = testSystem.calculateDensityFromBoilingPoint(molarMass, boilingPoint);

    // Verify density is in reasonable range for petroleum fractions
    assertTrue(calculatedDensity > 0.8, "Density should be greater than 0.8");
    assertTrue(calculatedDensity < 0.9, "Density should be less than 0.9");
  }


  @Test
  void testTBPBoilingPointCorrelationDensityCalculation2() {
    // Test data from literature - typical petroleum fraction nC10 CORRECT ANSWER 0.73
    double boilingPoint = 174 + 273.15; // K
    double molarMass = 0.14229; // kg/mol


    // Calculate density using TBP boiling point correlation
    double calculatedDensity = testSystem.calculateDensityFromBoilingPoint(molarMass, boilingPoint);

    // Verify density is in reasonable range for petroleum fractions
    assertTrue(calculatedDensity > 0.7, "Density should be greater than 0.6");
    assertTrue(calculatedDensity < 0.85, "Density should be less than 0.95");
  }

  @Test
  void testaddTBPfraction2UsesStandardMethod() {
    // Record initial component count
    int initialComponentCount = testSystem.getNumberOfComponents();

    // Add TBP fraction using boiling point method
    double boilingPoint = 400.0; // K
    double molarMass = 0.120; // kg/mol
    double moles = 1.0;

    testSystem.getCharacterization().setTBPModel("PedersenPR2"); // to ensure to call Soreide
    testSystem.addTBPfraction2("TestFraction", moles, molarMass, boilingPoint);


    // Verify component was added
    assertEquals(initialComponentCount + 1, testSystem.getNumberOfComponents(),
        "Component should have been added to system");

    // Verify the component has the correct properties
    ComponentInterface addedComponent =
        testSystem.getComponent(testSystem.getNumberOfComponents() - 1);
    assertEquals("TestFraction_PC", addedComponent.getName(), "Component name should match");
    assertEquals(moles, addedComponent.getNumberOfmoles(), 1e-10, "Moles should match");
    assertEquals(molarMass, addedComponent.getMolarMass(), 1e-10, "Molar mass should match");
  }

  @Test
  void testTBPBoilingPointCorrelationMolarCalculation2() {
    // Test data from literature - typical petroleum fraction nC10 CORRECT ANSWER 0.73
    double boilingPoint = 80 + 273.15; // K
    double density = 0.83; // kg/mol


    // Calculate density using TBP boiling point correlation
    double calculatedMolarmass =
        testSystem.calculateMolarMassFromDensityAndBoilingPoint(density, boilingPoint);

    // Verify density is in reasonable range for petroleum fractions
    assertTrue(calculatedMolarmass > 0.07, "");
    assertTrue(calculatedMolarmass < 0.08, "");
  }


  @Test
  void testaddTBPfraction3UsesStandardMethod() {
    // Record initial component count
    int initialComponentCount = testSystem.getNumberOfComponents();

    // Add TBP fraction using density and boiling point
    double boilingPoint = 400.0; // K
    double density = 0.75; // g/cm3
    double moles = 1.0;

    testSystem.addTBPfraction3("TestFraction3", moles, density, boilingPoint);

    // Verify component was added
    assertEquals(initialComponentCount + 1, testSystem.getNumberOfComponents(),
        "Component should have been added to system");

    // Verify the component has the correct properties
    ComponentInterface addedComponent =
        testSystem.getComponent(testSystem.getNumberOfComponents() - 1);
    assertEquals("TestFraction3_PC", addedComponent.getName(), "Component name should match");
    assertEquals(moles, addedComponent.getNumberOfmoles(), 1e-10, "Moles should match");
    // Optionally check molar mass is reasonable
    assertTrue(addedComponent.getMolarMass() > 0.05 && addedComponent.getMolarMass() < 0.5,
        "Molar mass should be in a reasonable range");
  }

  @Test
  void testTBPBoilingPointCorrelationMolarMassCalculation2() {
    // Test data from literature - typical petroleum fraction nC10
    double boilingPoint = 174 + 273.15; // K
    double density = 0.73; // g/cm3 (correct for nC10)

    //Calculate molar mass using TBP correlation
    double calculatedMolarMass = ((neqsim.thermo.system.SystemThermo) testSystem)
    .calculateMolarMassFromDensityAndBoilingPoint(density, boilingPoint);

    //Verify molar mass is in reasonable range for nC10 (should be close to 0.142 kg/mol)
    assertTrue(calculatedMolarMass > 0.13, "Molar mass should be greater than 0.13");
    assertTrue(calculatedMolarMass < 0.15, "Molar mass should be less than 0.15");
  }

  @Test
  void testaddTBPfraction4UsesStandardMethod() {
    // Record initial component count
    int initialComponentCount = testSystem.getNumberOfComponents();

    // Add TBP fraction using boiling point method
    double boilingPoint = 400.0; // K
    double molarMass = 0.120; // kg/mol
    double density = 0.75; //
    double moles = 1.0;

    testSystem.addTBPfraction4("TestFraction", moles, molarMass, density, boilingPoint);

    // Verify component was added
    assertEquals(initialComponentCount + 1, testSystem.getNumberOfComponents(),
        "Component should have been added to system");

    // Verify the component has the correct properties
    ComponentInterface addedComponent =
        testSystem.getComponent(testSystem.getNumberOfComponents() - 1);
    assertEquals("TestFraction_PC", addedComponent.getName(), "Component name should match");
    assertEquals(moles, addedComponent.getNumberOfmoles(), 1e-10, "Moles should match");
    assertEquals(molarMass, addedComponent.getMolarMass(), 1e-10, "Molar mass should match");
  }


  
}
