package neqsim.process.equipment.heatexchanger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.heatexchanger.SarirAtmosphericCrudeHeatingCase.HeatingInputs;
import neqsim.thermo.characterization.SarirAtmosphericReference;

/** Qualification tests for {@link SarirAtmosphericCrudeHeatingCase}. */
public class SarirAtmosphericCrudeHeatingCaseTest {
  private static final double[] SPECIFIC_GRAVITY = { 0.641826000, 0.671826000, 0.691826000, 0.711826000, 0.741826000,
      0.761826000, 0.781826000, 0.801826000, 0.821826000, 0.841826000, 0.861826000, 0.881826000, 0.901826000,
      0.921826000, 0.941826000, 0.961826000, 0.981826000, 1.021826000 };
  private static final double[] MOLAR_MASS_KG_PER_MOL = { 0.092957679997, 0.105352037330, 0.117746394663,
      0.136337930662, 0.161126645328, 0.179718181327, 0.204506895993, 0.223098431993, 0.241689967992, 0.272675861324,
      0.303661754657, 0.334647647989, 0.384225077321, 0.421408149319, 0.458591221318, 0.495774293317, 0.545351722649,
      0.743661439976 };

  /** Carry the constrained assay through both heaters to the published column-feed boundary. */
  @Test
  public void explicitInputsProduceQualifiedHeatingTrain() {
    HeatingInputs inputs = qualifiedInputs();
    SarirAtmosphericCrudeHeatingCase model = SarirAtmosphericCrudeHeatingCase.create("Sarir heating screen",
        SPECIFIC_GRAVITY, MOLAR_MASS_KG_PER_MOL, inputs);

    assertTrue(inputs == model.getHeatingInputs());
    assertEquals(2.48, inputs.getCrudeInletPressureBara(), 1.0e-12);
    assertEquals(SarirAtmosphericReference.getColumnCrudeFeedRateKgPerHour(),
        model.getCrudeInletStream().getFlowRate("kg/hr"), 1.0e-8);
    assertEquals(inputs.getCrudeInletTemperatureKelvin(), model.getCrudeInletStream().getTemperature("K"), 1.0e-12);

    model.run(UUID.randomUUID());
    assertQualifiedResult(model, inputs);

    double preheaterDutyW = model.getPreheater().getDuty("W");
    double firedDutyW = model.getFurnace().getFiredDuty("W");
    double fuelKgPerHour = model.getFurnace().getFuelConsumption("kg/hr");
    model.run(UUID.randomUUID());
    assertQualifiedResult(model, inputs);
    assertEquals(preheaterDutyW, model.getPreheater().getDuty("W"), Math.abs(preheaterDutyW) * 1.0e-12);
    assertEquals(firedDutyW, model.getFurnace().getFiredDuty("W"), Math.abs(firedDutyW) * 1.0e-12);
    assertEquals(fuelKgPerHour, model.getFurnace().getFuelConsumption("kg/hr"), Math.abs(fuelKgPerHour) * 1.0e-12);
  }

  /** Reject missing, nonphysical, or source-boundary-inconsistent assumptions before solving. */
  @Test
  public void invalidHeatingInputsFailClosed() {
    assertThrows(IllegalArgumentException.class, () -> new HeatingInputs(400.0, 390.0, 0.10, 0.05, 0.85, 48.0e6,
        2.75, 0.08, 423.15));
    assertThrows(IllegalArgumentException.class,
        () -> new HeatingInputs(300.0, 500.0, -0.10, 0.05, 0.85, 48.0e6, 2.75, 0.08, 423.15));
    assertThrows(IllegalArgumentException.class,
        () -> new HeatingInputs(300.0, 500.0, 0.10, 0.05, 0.0, 48.0e6, 2.75, 0.08, 423.15));
    assertThrows(IllegalArgumentException.class,
        () -> new HeatingInputs(300.0, 500.0, 0.10, 0.05, 1.01, 48.0e6, 2.75, 0.08, 423.15));
    assertThrows(IllegalArgumentException.class,
        () -> new HeatingInputs(300.0, 500.0, 0.10, 0.05, 0.85, Double.NaN, 2.75, 0.08, 423.15));
    assertThrows(IllegalArgumentException.class,
        () -> new HeatingInputs(300.0, 500.0, 0.10, 0.05, 0.85, 48.0e6, -1.0, 0.08, 423.15));
    assertThrows(IllegalArgumentException.class,
        () -> new HeatingInputs(300.0, 623.15, 0.10, 0.05, 0.85, 48.0e6, 2.75, 0.08, 423.15));

    assertThrows(IllegalArgumentException.class, () -> SarirAtmosphericCrudeHeatingCase.create(" ", SPECIFIC_GRAVITY,
        MOLAR_MASS_KG_PER_MOL, qualifiedInputs()));
    assertThrows(NullPointerException.class,
        () -> SarirAtmosphericCrudeHeatingCase.create("Sarir", SPECIFIC_GRAVITY, MOLAR_MASS_KG_PER_MOL, null));
  }

  private static void assertQualifiedResult(SarirAtmosphericCrudeHeatingCase model, HeatingInputs inputs) {
    double publishedFlow = SarirAtmosphericReference.getColumnCrudeFeedRateKgPerHour();
    double publishedTemperatureKelvin = SarirAtmosphericReference.getColumnFeedTemperatureCelsius() + 273.15;
    double publishedPressureBara = SarirAtmosphericReference.getColumnFeedPressureKPa() / 100.0;
    assertEquals(inputs.getPreheatTemperatureKelvin(), model.getPreheater().getOutletStream().getTemperature("K"),
        1.0e-7);
    assertEquals(publishedTemperatureKelvin, model.getColumnFeedStream().getTemperature("K"), 1.0e-7);
    assertEquals(publishedPressureBara, model.getColumnFeedStream().getPressure("bara"), 1.0e-10);
    assertEquals(publishedFlow, model.getColumnFeedStream().getFlowRate("kg/hr"), publishedFlow * 1.0e-10);
    assertArrayEquals(model.getCrudeInletStream().getThermoSystem().getMolarComposition(),
        model.getColumnFeedStream().getThermoSystem().getMolarComposition(), 1.0e-12);

    double preheaterDutyW = model.getPreheater().getDuty("W");
    double absorbedDutyW = model.getFurnace().getAbsorbedDuty("W");
    double firedDutyW = model.getFurnace().getFiredDuty("W");
    assertTrue(Double.isFinite(preheaterDutyW) && preheaterDutyW > 0.0);
    assertTrue(Double.isFinite(absorbedDutyW) && absorbedDutyW > 0.0);
    assertEquals(absorbedDutyW / inputs.getThermalEfficiency(), firedDutyW, firedDutyW * 1.0e-10);
    assertEquals(firedDutyW - absorbedDutyW, model.getFurnace().getStackLoss("W"), firedDutyW * 1.0e-10);
    assertEquals(firedDutyW / inputs.getFuelLowerHeatingValueJPerKg() * 3600.0,
        model.getFurnace().getFuelConsumption("kg/hr"), 1.0e-10);
    assertEquals(model.getFurnace().getFuelConsumption("kg/hr") * inputs.getFuelCO2FactorKgPerKg(),
        model.getFurnace().getCO2Emissions("kg/hr"), 1.0e-10);
    assertEquals(firedDutyW / 1.0e9 * inputs.getNoxFactorKgPerGJ() * 3600.0,
        model.getFurnace().getNOxEmissions("kg/hr"), 1.0e-12);
  }

  private static HeatingInputs qualifiedInputs() {
    return new HeatingInputs(300.0, 500.0, 0.10, 0.05, 0.85, 48.0e6, 2.75, 0.08, 423.15);
  }
}
