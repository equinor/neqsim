package neqsim.thermo.characterization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Test class for TBP fraction input methods.
 */
public class TBPFractionFromBoilingPointTest {

  @Test
  void testAddTBPfractionFromBoilingPoint() {
    SystemInterface thermoSystem = new SystemSrkEos(298.0, 10.0);
    
    // Test with known values for C7 fraction
    // Boiling point: ~371.6 K, Molar mass: 0.109 kg/mol
    thermoSystem.addTBPfractionFromBoilingPoint("C7_test", 1.0, 371.6, 0.109);
    
    // Verify component was added
    assertEquals(1, thermoSystem.getNumberOfComponents());
    assertEquals("C7_test_PC", thermoSystem.getComponent(0).getComponentName());
    
    // Check that calculated properties are reasonable
    double calculatedDensity = thermoSystem.getComponent(0).getNormalLiquidDensity();
    // For C7, density should be around 0.68-0.72 g/cm³
    assertEquals(0.7, calculatedDensity, 0.1, "Calculated density should be reasonable for C7");
    
    // Check critical properties were calculated
    double TC = thermoSystem.getComponent(0).getTC();
    double PC = thermoSystem.getComponent(0).getPC();
    
    // Critical temperature for C7 should be around 540 K
    assertEquals(540.0, TC, 50.0, "Critical temperature should be reasonable");
    // Critical pressure for C7 should be around 27 bar
    assertEquals(27.0, PC, 10.0, "Critical pressure should be reasonable");
  }

  @Test 
  void testCompareWithTraditionalMethod() {
    SystemInterface thermoSystem1 = new SystemSrkEos(298.0, 10.0);
    SystemInterface thermoSystem2 = new SystemSrkEos(298.0, 10.0);
    
    // Add same component using both methods
    thermoSystem1.addTBPfraction("C7_traditional", 1.0, 0.109, 0.6912);
    thermoSystem2.addTBPfractionFromBoilingPoint("C7_new", 1.0, 371.6, 0.109);
    
    // Results should be similar (within reasonable tolerance)
    double tc1 = thermoSystem1.getComponent(0).getTC();
    double tc2 = thermoSystem2.getComponent(0).getTC();
    double pc1 = thermoSystem1.getComponent(0).getPC();
    double pc2 = thermoSystem2.getComponent(0).getPC();
    
    // Allow for some difference due to calculation method differences
    assertEquals(tc1, tc2, 50.0, "Critical temperatures should be similar");
    assertEquals(pc1, pc2, 10.0, "Critical pressures should be similar");
  }
}
