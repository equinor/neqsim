package neqsim.process.equipment.distillation;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import neqsim.process.equipment.distillation.SarirAtmosphericFractionationCase.OperatingInputs;
import neqsim.process.equipment.heatexchanger.SarirAtmosphericCrudeHeatingCase;
import neqsim.process.equipment.heatexchanger.SarirAtmosphericCrudeHeatingCase.HeatingInputs;
import neqsim.thermo.characterization.SarirAtmosphericReference;

/** End-to-end qualification for the source-bounded Sarir heating and fractionation boundary. */
public class SarirAtmosphericCrudeHeatingFractionationIntegrationTest {
  private static final double[] SPECIFIC_GRAVITY = { 0.641826000, 0.671826000, 0.691826000, 0.711826000, 0.741826000,
      0.761826000, 0.781826000, 0.801826000, 0.821826000, 0.841826000, 0.861826000, 0.881826000, 0.901826000,
      0.921826000, 0.941826000, 0.961826000, 0.981826000, 1.021826000 };
  private static final double[] MOLAR_MASS_KG_PER_MOL = { 0.092957679997, 0.105352037330, 0.117746394663,
      0.136337930662, 0.161126645328, 0.179718181327, 0.204506895993, 0.223098431993, 0.241689967992, 0.272675861324,
      0.303661754657, 0.334647647989, 0.384225077321, 0.421408149319, 0.458591221318, 0.495774293317, 0.545351722649,
      0.743661439976 };

  /** Carry one qualified characterized state through both heaters and the atmospheric column. */
  @Test
  @Timeout(value = 240, unit = TimeUnit.SECONDS)
  public void solvedHeatingOutletFeedsQualifiedFractionation() {
    SarirAtmosphericCrudeHeatingCase heating = createHeatingCase();
    assertThrows(IllegalStateException.class, () -> SarirAtmosphericFractionationCase.createFromHeatingCase(
        "Sarir connected screen", heating, qualifiedColumnInputs()));

    UUID id = UUID.randomUUID();
    heating.run(id);
    double[] heatingComposition = heating.getColumnFeedStream().getThermoSystem().getMolarComposition();

    SarirAtmosphericFractionationCase fractionation = SarirAtmosphericFractionationCase.createFromHeatingCase(
        "Sarir connected screen", heating, qualifiedColumnInputs());
    assertEquals(SarirAtmosphericReference.getColumnCrudeFeedRateKgPerHour(),
        fractionation.getFeedStream().getFlowRate("kg/hr"), 1.0e-6);
    assertEquals(SarirAtmosphericReference.getColumnFeedTemperatureCelsius(),
        fractionation.getFeedStream().getTemperature("C"), 1.0e-9);
    assertEquals(SarirAtmosphericReference.getColumnFeedPressureKPa() / 100.0,
        fractionation.getFeedStream().getPressure("bara"), 1.0e-12);
    assertArrayEquals(heatingComposition, fractionation.getFeedStream().getThermoSystem().getMolarComposition(),
        1.0e-12);

    fractionation.run(id);

    assertTrue(fractionation.getColumn().solved(), fractionation.getColumn().getConvergenceDiagnostics());
    assertEquals(DistillationColumn.SolverType.MESH_RESIDUAL,
        fractionation.getColumn().getLastSolverTypeUsed());
    assertNotEquals(DistillationColumn.SolveStatus.FALLBACK_PRODUCTS,
        fractionation.getColumn().getLastSolveStatus());
    assertNotEquals(DistillationColumn.SolveStatus.FAILED, fractionation.getColumn().getLastSolveStatus());
    assertArrayEquals(heatingComposition, fractionation.getFeedStream().getThermoSystem().getMolarComposition(),
        1.0e-12);

    SarirAtmosphericFractionationResult result = SarirAtmosphericFractionationResult.evaluate(fractionation);
    assertEquals(SarirAtmosphericReference.getColumnCrudeFeedRateKgPerHour(), result.getFeedMassFlowKgPerHour(),
        1.0e-6);
    assertTrue(result.getMassClosureRelativeError() <= 5.0e-2);
    assertTrue(result.getColumnMassBalanceError() <= 5.0e-2);
    assertTrue(result.getColumnEnergyBalanceError() <= 5.0e-2);
    assertEquals(4, result.getProducts().length);
    assertEquals("Total Naphtha", result.getProducts()[0].getProductLabel());
    assertEquals("Residual", result.getProducts()[3].getProductLabel());
  }

  /** Reject drift from the published boundary after the qualified heating solve. */
  @Test
  public void changedHeatingBoundaryFailsClosed() {
    SarirAtmosphericCrudeHeatingCase heating = createHeatingCase();
    heating.run(UUID.randomUUID());
    heating.getColumnFeedStream().setPressure(2.50, "bara");

    assertThrows(IllegalStateException.class, () -> SarirAtmosphericFractionationCase.createFromHeatingCase(
        "Sarir connected screen", heating, qualifiedColumnInputs()));
  }

  private static SarirAtmosphericCrudeHeatingCase createHeatingCase() {
    HeatingInputs inputs = new HeatingInputs(300.0, 500.0, 0.10, 0.05, 0.85, 48.0e6, 2.75, 0.08, 423.15);
    return SarirAtmosphericCrudeHeatingCase.create("Sarir heating screen", SPECIFIC_GRAVITY,
        MOLAR_MASS_KG_PER_MOL, inputs);
  }

  private static OperatingInputs qualifiedColumnInputs() {
    return new OperatingInputs(1.20, SarirAtmosphericReference.getColumnFeedPressureKPa() / 100.0, 700.0, 1.0, 24,
        0.08, 15, 0.15);
  }
}
