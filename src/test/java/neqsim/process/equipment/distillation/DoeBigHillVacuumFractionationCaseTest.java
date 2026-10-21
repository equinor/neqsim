package neqsim.process.equipment.distillation;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import neqsim.process.equipment.distillation.DoeBigHillVacuumFractionationCase.OperatingInputs;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.characterization.DoeBigHillSweetAssay;
import neqsim.thermo.characterization.OilAssayCharacterisation;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/** Qualification tests for {@link DoeBigHillVacuumFractionationCase}. */
public class DoeBigHillVacuumFractionationCaseTest {
  private static final double STANDARD_FEED_FLOW_KG_PER_HOUR = 1000.0;

  @Test
  public void explicitInputsCreateUnsolvedSubAtmosphericColumnHandoff() {
    OperatingInputs inputs = qualifiedInputs();
    DoeBigHillVacuumFractionationCase model = DoeBigHillVacuumFractionationCase.create("Big Hill vacuum screen",
        STANDARD_FEED_FLOW_KG_PER_HOUR, inputs);
    Stream feed = model.getFeedStream();
    DistillationColumn column = model.getColumn();

    assertSame(inputs, model.getOperatingInputs());
    assertEquals(inputs.getFeedTrayIndex(), column.getFeedTrayNumber(feed));
    assertEquals(STANDARD_FEED_FLOW_KG_PER_HOUR, feed.getFlowRate("kg/hr"), 1.0e-9);
    assertEquals(inputs.getFeedTemperatureKelvin(), feed.getTemperature("K"), 1.0e-9);
    assertEquals(inputs.getFeedPressureBara(), feed.getPressure("bara"), 1.0e-12);

    assertEquals(3, feed.getThermoSystem().getNumberOfComponents());
    assertPositiveFiniteComponent(feed.getThermoSystem().getComponent("DOE_BH_650_850_PC"));
    assertPositiveFiniteComponent(feed.getThermoSystem().getComponent("DOE_BH_850_1050_PC"));
    assertPositiveFiniteComponent(feed.getThermoSystem().getComponent("DOE_BH_1050_PLUS_PC"));

    assertEquals(12, inputs.getSimpleTrayCount());
    assertEquals(4, inputs.getFeedTrayIndex());
    assertEquals(640.0, inputs.getFeedTemperatureKelvin(), 0.0);
    assertEquals(0.12, inputs.getFeedPressureBara(), 0.0);
    assertEquals(0.08, inputs.getTopPressureBara(), 0.0);
    assertEquals(0.16, inputs.getBottomPressureBara(), 0.0);
    assertEquals(700.0, inputs.getReboilerTemperatureKelvin(), 0.0);
    assertEquals(0.5, inputs.getCondenserRefluxRatio(), 0.0);

    assertEquals(DistillationColumn.SolverType.MESH_RESIDUAL, column.getSolverType());
    assertEquals(5.0e-2, column.getMassBalanceTolerance(), 0.0);
    assertEquals(5.0e-2, column.getEnthalpyBalanceTolerance(), 0.0);
    assertEquals(0.20, column.getTemperatureTolerance(), 0.0);
    assertEquals(700.0, column.getReboiler().getOutTemperature(), 0.0);
    assertFalse(column.solved());
  }

  @Test
  public void repeatedConstructionPreservesTheQualifiedFeedDefinition() {
    DoeBigHillVacuumFractionationCase first = DoeBigHillVacuumFractionationCase.create("First", 250.0,
        qualifiedInputs());
    DoeBigHillVacuumFractionationCase second = DoeBigHillVacuumFractionationCase.create("Second", 250.0,
        qualifiedInputs());

    assertEquals(first.getFeedStream().getThermoSystem().getNumberOfComponents(),
        second.getFeedStream().getThermoSystem().getNumberOfComponents());
    for (int i = 0; i < first.getFeedStream().getThermoSystem().getNumberOfComponents(); i++) {
      ComponentInterface firstComponent = first.getFeedStream().getThermoSystem().getComponent(i);
      ComponentInterface secondComponent = second.getFeedStream().getThermoSystem().getComponent(i);
      assertEquals(firstComponent.getComponentName(), secondComponent.getComponentName());
      assertEquals(firstComponent.getz(), secondComponent.getz(), 0.0);
    }
  }

  @Test
  public void fullSlateHandoffClonesAndPreservesTheDoeAssayIdentity() {
    SystemInterface system = new SystemSrkEos(650.0, 1.5);
    OilAssayCharacterisation assay = DoeBigHillSweetAssay.create(system);
    assay.apply();
    system.setMixingRule("classic");

    Stream atmosphericBottoms = new Stream("Modeled Big Hill atmospheric bottoms", system);
    atmosphericBottoms.setFlowRate(1800.0, "kg/hr");
    atmosphericBottoms.setTemperature(650.0, "K");
    atmosphericBottoms.setPressure(1.5, "bara");
    atmosphericBottoms.run();
    double[] sourceComposition =
        atmosphericBottoms.getThermoSystem().getMolarComposition().clone();

    DoeBigHillVacuumFractionationCase model =
        DoeBigHillVacuumFractionationCase.createFromAtmosphericBottoms(
            "Connected Big Hill vacuum screen", atmosphericBottoms, qualifiedInputs());

    assertNotSame(atmosphericBottoms.getThermoSystem(),
        model.getFeedStream().getThermoSystem());
    assertEquals(atmosphericBottoms.getThermoSystem().getNumberOfComponents(),
        model.getFeedStream().getThermoSystem().getNumberOfComponents());
    assertArrayEquals(sourceComposition,
        model.getFeedStream().getThermoSystem().getMolarComposition(), 1.0e-12);
    assertEquals(1800.0, model.getFeedStream().getFlowRate("kg/hr"), 1.0e-9);
    assertEquals(qualifiedInputs().getFeedTemperatureKelvin(),
        model.getFeedStream().getTemperature("K"), 1.0e-9);
    assertEquals(qualifiedInputs().getFeedPressureBara(),
        model.getFeedStream().getPressure("bara"), 1.0e-12);
  }

  @Test
  public void invalidSourceUnreportedInputsFailClosed() {
    assertThrows(IllegalArgumentException.class,
        () -> DoeBigHillVacuumFractionationCase.create(" ", 100.0, qualifiedInputs()));
    assertThrows(IllegalArgumentException.class,
        () -> DoeBigHillVacuumFractionationCase.create("Vacuum", 0.0, qualifiedInputs()));
    assertThrows(IllegalArgumentException.class,
        () -> DoeBigHillVacuumFractionationCase.create("Vacuum", Double.NaN, qualifiedInputs()));
    assertThrows(NullPointerException.class, () -> DoeBigHillVacuumFractionationCase.create("Vacuum", 100.0, null));

    assertThrows(IllegalArgumentException.class, () -> new OperatingInputs(1, 1, 640.0, 0.12, 0.08, 0.16, 700.0, 0.5));
    assertThrows(IllegalArgumentException.class,
        () -> new OperatingInputs(101, 4, 640.0, 0.12, 0.08, 0.16, 700.0, 0.5));
    assertThrows(IllegalArgumentException.class, () -> new OperatingInputs(12, 0, 640.0, 0.12, 0.08, 0.16, 700.0, 0.5));
    assertThrows(IllegalArgumentException.class,
        () -> new OperatingInputs(12, 13, 640.0, 0.12, 0.08, 0.16, 700.0, 0.5));
    assertThrows(IllegalArgumentException.class,
        () -> new OperatingInputs(12, 4, Double.NaN, 0.12, 0.08, 0.16, 700.0, 0.5));
    assertThrows(IllegalArgumentException.class, () -> new OperatingInputs(12, 4, 640.0, 0.12, 0.16, 0.16, 700.0, 0.5));
    assertThrows(IllegalArgumentException.class, () -> new OperatingInputs(12, 4, 640.0, 0.07, 0.08, 0.16, 700.0, 0.5));
    assertThrows(IllegalArgumentException.class, () -> new OperatingInputs(12, 4, 640.0, 0.17, 0.08, 0.16, 700.0, 0.5));
    assertThrows(IllegalArgumentException.class, () -> new OperatingInputs(12, 4, 640.0, 0.12, 0.08,
        DoeBigHillVacuumFractionationCase.STANDARD_ATMOSPHERE_BARA, 700.0, 0.5));
    assertThrows(IllegalArgumentException.class, () -> new OperatingInputs(12, 4, 640.0, 0.12, 0.08, 0.16, 640.0, 0.5));
    assertThrows(IllegalArgumentException.class,
        () -> new OperatingInputs(12, 4, 640.0, 0.12, 0.08, 0.16, 700.0, Double.NaN));
  }

  private static OperatingInputs qualifiedInputs() {
    return new OperatingInputs(12, 4, 640.0, 0.12, 0.08, 0.16, 700.0, 0.5);
  }

  private static void assertPositiveFiniteComponent(ComponentInterface component) {
    assertNotNull(component);
    assertTrue(Double.isFinite(component.getNumberOfmoles()));
    assertTrue(component.getNumberOfmoles() > 0.0);
    assertTrue(Double.isFinite(component.getMolarMass()));
    assertTrue(component.getMolarMass() > 0.0);
  }
}
