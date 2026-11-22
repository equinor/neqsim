package neqsim.util.database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Neutral DEA identity must agree across supported database modes.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class DeaIdentityDataTest {
  /**
   * Both database modes must supply the sourced neutral identity and consistent mass conversion.
   *
   * @throws Exception if the bundled database cannot be queried
   */
  @Test
  void identityAndMassConversionAreConsistent() throws Exception {
    try {
      for (boolean extended : new boolean[] {false, true}) {
        NeqSimDataBase.useExtendedComponentDatabase(extended);
        try (NeqSimDataBase database = new NeqSimDataBase();
            ResultSet row = database.getResultSet("SELECT * FROM comp WHERE name='DEA'")) {
          row.next();
          assertEquals("111-42-2", row.getString("CASnumber"));
          assertEquals("C4H11NO2", row.getString("FORMULA"));
          assertEquals("ZBCBWPMODOFKDW-UHFFFAOYSA-N", row.getString("InChIKey"));
          assertEquals(105.1356, row.getDouble("MOLARMASS"), 1.0e-8);
        }
        SystemSrkEos fluid = new SystemSrkEos(298.15, 1.01325);
        fluid.addComponent("DEA", 2.0);
        fluid.init(0);
        assertEquals(0.1051356, fluid.getComponent("DEA").getMolarMass(), 1.0e-10);
        assertEquals(0.2102712, fluid.getMass("kg"), 1.0e-10);
      }
    } finally {
      NeqSimDataBase.useExtendedComponentDatabase(false);
    }
  }

  /**
   * Correcting a molecular identity must refresh its Henry source disposition without admitting unqualified data.
   *
   * @throws Exception if the bundled manifest cannot be read
   */
  @Test
  void henryDispositionUsesTheCorrectedIdentity() throws Exception {
    JsonObject source;
    try (InputStreamReader reader = new InputStreamReader(
        getClass().getResourceAsStream("/data/HenryWaterCandidateDispositions.json"), StandardCharsets.UTF_8)) {
      source = JsonParser.parseReader(reader).getAsJsonObject();
    }
    JsonObject dea = null;
    for (JsonElement entry : source.getAsJsonArray("rows")) {
      JsonObject row = entry.getAsJsonObject();
      if ("DEA".equals(row.get("name").getAsString())) {
        dea = row;
      }
    }
    assertNotNull(dea, "An exact-CAS source record must not remain classified as unmatched");
    assertEquals("111-42-2", dea.get("cas").getAsString());
    assertEquals("M Q V", dea.get("source_types").getAsString());
    assertEquals("held_reactive_amine_joint_model_requalification", dea.get("coverage_status").getAsString());
    SystemSrkEos fluid = new SystemSrkEos(298.15, 1.01325);
    fluid.addComponent("DEA", 1.0);
    assertFalse(fluid.getComponent("DEA").hasHenryCorrelation(),
        "Source discovery must not silently activate an unqualified reactive-amine correlation");
  }
}
