package neqsim.process.mechanicaldesign.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import neqsim.util.database.NeqSimProcessDesignDataBase;
import org.junit.jupiter.api.Test;
import neqsim.process.mechanicaldesign.pipeline.PipelineMechanicalDesignDataSource.PipeDesignFactors;

/**
 * Regression coverage for database wall-tolerance conventions.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class PipelineFabricationToleranceTest extends neqsim.NeqSimTest {
  /** Verify the ordinary company-data path and the pressure/thickness inverse. */
  @Test
  void companyToleranceIsConvertedBeforeDesignCalculations() {
    PipelineMechanicalDesignDataSource source = new PipelineMechanicalDesignDataSource();
    for (String company : new String[] {"default", "Equinor"}) {
      PipeMechanicalDesignCalculator calculator = new PipeMechanicalDesignCalculator(0.508, 15.0, 20.0);
      source.loadIntoCalculator("X65", company, calculator);
      assertEquals(0.875, calculator.getFabricationTolerance(), 1e-12, company);
      double nominalWall = calculator.calculateMinimumWallThickness();
      calculator.setNominalWallThickness(nominalWall);
      assertEquals(15.0, calculator.calculateMAOP(), 1e-9);
      assertTrue(nominalWall < 0.03, "Nominal wall must not be inflated by dividing by the tolerance fraction");
    }
  }

  /** Verify both fraction-style standards and the formerly factor-style manifold record. */
  @Test
  void standardsAndCompanyRowsUseTheSameToleranceConvention() {
    PipelineMechanicalDesignDataSource source = new PipelineMechanicalDesignDataSource();
    for (String[] selection : new String[][] {{"API-5L", "Pipeline"}, {"ISO-3183", "Pipeline"},
        {"DNV-ST-F101", "Manifold"}}) {
      PipeDesignFactors factors = new PipeDesignFactors();
      factors.fabricationTolerance = 1.0;
      source.loadFromStandardsTable(selection[0], selection[1], factors);
      assertEquals(0.875, factors.fabricationTolerance, 1e-12, selection[0]);
    }
  }

  /**
   * Explicit unit tags disambiguate fractions, percentages, and remaining-wall factors.
   *
   * @throws java.sql.SQLException if the database connection cannot be closed
   */
  @Test
  void explicitUnitsAreValidatedRatherThanInferredFromMagnitude() throws java.sql.SQLException {
    PipelineMechanicalDesignDataSource source = new PipelineMechanicalDesignDataSource();
    String company = "__PIPE_WALL_TOLERANCE_TEST__";
    try (NeqSimProcessDesignDataBase database = new NeqSimProcessDesignDataBase()) {
      for (String[] testCase : new String[][] {{"0.2", "negative_wall_tolerance_fraction", "0.8"},
          {"0.9", "remaining_wall_factor", "0.9"}, {"20.0", "%", "0.8"}, {"0.125", "-", "0.875"}}) {
        database.execute("DELETE FROM TechnicalRequirements_Process WHERE Company='" + company + "'");
        database.execute(
            "INSERT INTO TechnicalRequirements_Process (ID,EQUIPMENTTYPE,SPECIFICATION,MINVALUE,MAXVALUE,UNIT,Company) "
                + "VALUES (91037,'Pipeline','fabricationTolerance','" + testCase[0] + "','" + testCase[0] + "','"
                + testCase[1] + "','" + company + "')");
        assertEquals(Double.parseDouble(testCase[2]), source.loadDesignFactors(company).fabricationTolerance, 1e-12);
      }
      database.execute("UPDATE TechnicalRequirements_Process SET UNIT='ambiguous' WHERE Company='" + company + "'");
      assertThrows(IllegalArgumentException.class, () -> source.loadDesignFactors(company));
      database
          .execute("UPDATE TechnicalRequirements_Process SET UNIT='negative_wall_tolerance_fraction',MAXVALUE='1.0' "
              + "WHERE Company='" + company + "'");
      assertThrows(IllegalArgumentException.class, () -> source.loadDesignFactors(company));
    } finally {
      try (NeqSimProcessDesignDataBase database = new NeqSimProcessDesignDataBase()) {
        database.execute("DELETE FROM TechnicalRequirements_Process WHERE Company='" + company + "'");
      }
    }
  }
}
