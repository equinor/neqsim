package neqsim.process.mechanicaldesign.designstandards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

/**
 * Plate allowables must come from the requested specification and grade.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class MaterialPlateLookupTest {
  /** Both bundled grades and divisions must resolve with psi-to-MPa conversion. */
  @Test
  void readsBundledPlateGrades() {
    MaterialPlateDesignStandard standard = new MaterialPlateDesignStandard("ASME", null);
    assertEquals(13800.0 * 0.00689475729, standard.getDivisionClass(), 1.0e-9);
    standard.readMaterialDesignStandard("Carbon Steel Plates and Sheets", "SA-516", "60", 1);
    assertEquals(15000.0 * 0.00689475729, standard.getDivisionClass(), 1.0e-9);
    standard.readMaterialDesignStandard("Carbon Steel Plates and Sheets", "SA-516", "60", 2);
    assertEquals(20000.0 * 0.00689475729, standard.getDivisionClass(), 1.0e-9);
  }

  /** A failed reload must not keep a previously loaded allowable. */
  @Test
  void missingGradeInvalidatesAllowable() {
    MaterialPlateDesignStandard standard = new MaterialPlateDesignStandard("ASME", null);
    assertThrows(IllegalArgumentException.class,
        () -> standard.readMaterialDesignStandard("Carbon Steel Plates and Sheets", "SA-516", "999", 1));
    assertThrows(IllegalStateException.class, () -> standard.getDivisionClass());
    assertThrows(IllegalArgumentException.class,
        () -> standard.readMaterialDesignStandard("not-a-material' OR ID=1 -- ", "not-a-spec", "999", 1));
    assertThrows(IllegalStateException.class, () -> standard.getDivisionClass());
  }
}
