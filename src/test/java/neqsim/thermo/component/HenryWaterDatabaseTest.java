package neqsim.thermo.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

/**
 * Independent source-value, standard-state and missing-data regressions for issue 4044.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class HenryWaterDatabaseTest {
  /** Checks every imported species against the source molality/pressure convention. */
  @Test
  void importedRowsReproduceSourcesAndDerivatives() {
    JsonObject source = JsonParser.parseReader(
        new InputStreamReader(getClass().getResourceAsStream("/data/HenryWaterSource.json"), StandardCharsets.UTF_8))
        .getAsJsonObject();
    int count = 0;
    for (JsonElement entry : source.getAsJsonArray("rows")) {
      JsonObject row = entry.getAsJsonObject();
      String name = row.get("name").getAsString();
      ComponentSrk component = new ComponentSrk(name, 1.0, 1.0, 0);
      assertEquals(row.get("cas").getAsString(), component.getCASnumber(), name);
      assertTrue(component.hasHenryCorrelation(), name);
      double solubility = row.get("Hsbp_mol_kg_atm").getAsDouble();
      double slope = row.get("B_K").getAsDouble();
      for (double temperature : new double[] {288.15, 298.15, 308.15}) {
        double expected = 1.01325 / (solubility * Math.exp(slope * (1.0 / temperature - 1.0 / 298.15)));
        assertEquals(expected, component.getHenryCoef(temperature), expected * 1.0e-11, name);
        double step = 0.001;
        double difference = (component.getHenryCoef(temperature + step) - component.getHenryCoef(temperature - step))
            / (2.0 * step);
        assertEquals(difference, component.getHenryCoefdT(temperature),
            Math.max(1.0e-10, Math.abs(difference) * 1.0e-7), name);
      }
      count++;
    }
    assertEquals(76, count);
  }

  /** Missing and overflow placeholders must never look like a measured finite constant. */
  @Test
  void missingSentinelAndInvalidInputsAreExplicit() {
    ComponentSrk component = new ComponentSrk("methane", 1.0, 1.0, 0);
    for (double[] parameters : new double[][] {{0, 0, 0, 0}, {900, 0, 0, 0}, {Double.NaN, 0, 0, 0},
        {Double.POSITIVE_INFINITY, 0, 0, 0}}) {
      component.setHenryCoefParameter(parameters);
      assertFalse(component.hasHenryCorrelation());
      assertTrue(Double.isNaN(component.getHenryCoef(298.15)));
      assertTrue(Double.isNaN(component.getHenryCoefdT(298.15)));
    }
    component.setHenryCoefParameter(new double[] {10, -1000, 0, 0});
    for (double temperature : new double[] {0, -1, Double.NaN, Double.POSITIVE_INFINITY}) {
      assertTrue(Double.isNaN(component.getHenryCoef(temperature)));
      assertTrue(Double.isNaN(component.getHenryCoefdT(temperature)));
    }
    component.setHenryCoefParameter(new double[] {0, 0, 0, 1000});
    assertTrue(Double.isNaN(component.getHenryCoef(298.15)));
    component.setHenryCoefParameter(new double[] {-1000, 0, 0, 0});
    assertTrue(Double.isNaN(component.getHenryCoef(298.15)));
  }

  /** Published gases must be distinct and of the correct physical magnitude. */
  @Test
  void reportedBadFamiliesAreReplaced() {
    assertEquals(1.01325 / 0.0014, new ComponentSrk("methane", 1, 1, 0).getHenryCoef(298.15), 1.0e-8);
    double co = new ComponentSrk("CO", 1, 1, 0).getHenryCoef(298.15);
    double co2 = new ComponentSrk("CO2", 1, 1, 0).getHenryCoef(298.15);
    double h2s = new ComponentSrk("H2S", 1, 1, 0).getHenryCoef(298.15);
    assertTrue(co > 20 * co2);
    assertTrue(h2s > 5 && h2s < 20);
    assertFalse(new ComponentSrk("water", 1, 1, 0).hasHenryCorrelation());
    assertFalse(new ComponentSrk("Na+", 1, 1, 0).hasHenryCorrelation());
  }

  /** Generic GE and Pitzer must differ by precisely the water standard-state conversion. */
  @Test
  void standardStatesAndFailClosedBehaviorAgree() {
    ComponentGeNRTL ge = new ComponentGeNRTL("COS", 1, 1, 0);
    ComponentGePitzer pitzer = new ComponentGePitzer("COS", 1, 1, 0);
    double molalityReference = ge.getHenryCoef(298.15);
    assertEquals(molalityReference / IapwsHenryLaw.WATER_MOLAR_MASS_KG_PER_MOL, ge.getEffectiveHenryCoefficient(298.15),
        1.0e-9);
    assertEquals(molalityReference, pitzer.getEffectiveHenryCoefficient(298.15), 1.0e-9);
    ge.setHenryCoefParameter(new double[] {Math.log(1.0e11 / 1.802) - 0.001 * 298.15, 0, 0, 0.001});
    assertEquals(ComponentGE.INSOLUBLE_HENRY_COEFFICIENT, ge.getEffectiveHenryCoefficient(298.15));
    assertEquals(0.0, ge.getLnHenryCoefficientTemperatureDerivative(298.15));
    ge.setHenryCoefParameter(new double[4]);
    assertEquals(ComponentGE.INSOLUBLE_HENRY_COEFFICIENT, ge.getEffectiveHenryCoefficient(298.15));
    assertEquals(0.0, ge.getLnHenryCoefficientTemperatureDerivative(298.15));
  }
}
