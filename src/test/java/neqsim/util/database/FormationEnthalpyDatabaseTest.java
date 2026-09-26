package neqsim.util.database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.sql.ResultSet;
import org.junit.jupiter.api.Test;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.component.ComponentSrk;

/** Reviewed values and provenance must survive both database loading routes. */
class FormationEnthalpyDatabaseTest {
  @Test
  void standardAndExtendedDatabasesAgreeOnReviewedFormationData() throws Exception {
    try {
      for (boolean extended : new boolean[] {false, true}) {
        NeqSimDataBase.useExtendedComponentDatabase(extended);
        try (NeqSimDataBase database = new NeqSimDataBase();
            ResultSet rows = database.getResultSet("SELECT NAME,ENTHALPYOFFORMATION,FORMATIONENTHALPYSOURCE FROM COMP "
                + "WHERE FORMATIONENTHALPYSOURCE IS NOT NULL AND FORMATIONENTHALPYSOURCE<>''")) {
          int reviewed = 0;
          while (rows.next()) {
            ComponentInterface component = new ComponentSrk(rows.getString("NAME"), 1.0, 1.0, 0);
            assertTrue(component.hasIdealGasEnthalpyOfFormation(), rows.getString("NAME"));
            assertEquals(rows.getDouble("ENTHALPYOFFORMATION"), component.getHID(298.15, true), 1.0e-8);
            assertEquals(rows.getString("FORMATIONENTHALPYSOURCE"), component.getFormationEnthalpySource());
            reviewed++;
          }
          assertEquals(13, reviewed);
        }
        assertEquals(0.0, new ComponentSrk("helium", 1.0, 1.0, 0).getHID(298.15, true), 1.0e-8);
        assertEquals(-241826.4, new ComponentSrk("water", 1.0, 1.0, 0).getHID(298.15, true), 1.0e-8);
        assertFalse(new ComponentSrk("default", 1.0, 1.0, 0).hasIdealGasEnthalpyOfFormation());
      }
    } finally {
      NeqSimDataBase.useExtendedComponentDatabase(false);
    }
  }
}
