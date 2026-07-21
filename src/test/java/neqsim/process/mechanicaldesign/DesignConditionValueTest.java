package neqsim.process.mechanicaldesign;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Map;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.mechanicaldesign.DesignConditionValue.Type;

/** Tests typed design conditions and explicit SI/field-unit equivalence. */
class DesignConditionValueTest {
  @Test
  void typedValuesConvertToCanonicalUnits() {
    DesignConditionValue pressure = DesignConditionValue.of(Type.DESIGN_PRESSURE, 145.03773773020922, "psia");
    DesignConditionValue maxTemperature = DesignConditionValue.of(Type.MAX_DESIGN_TEMPERATURE, 212.0, "F");
    DesignConditionValue corrosion = DesignConditionValue.of(Type.CORROSION_ALLOWANCE, 0.125, "in");

    assertEquals(10.0, pressure.getCanonicalValue(), 1.0e-10);
    assertEquals(100.0, maxTemperature.getCanonicalValue(), 1.0e-10);
    assertEquals(3.175, corrosion.getCanonicalValue(), 1.0e-12);
    assertEquals(1.25, corrosion.getValue("cm"), 1.0e-12);
    assertEquals("bara", pressure.getCanonicalUnit());
    assertEquals("C", maxTemperature.getCanonicalUnit());
  }

  @Test
  void designConditionsExposeTypedDefensiveSnapshot() {
    DesignConditions conditions = new DesignConditions().setDesignPressure(145.03773773020922, "psia")
        .setMaxDesignTemperature(373.15, "K").setMinDesignTemperature(-50.0, "C")
        .setReliefSetPressure(10.0, "barg").setCorrosionAllowance(0.125, "in");

    assertEquals(10.0, conditions.getDesignPressure(), 1.0e-10);
    assertEquals(145.03773773020922, conditions.getDesignPressure("psia"), 1.0e-8);
    assertEquals(100.0, conditions.getMaxDesignTemperature(), 1.0e-10);
    assertEquals(212.0, conditions.getMaxDesignTemperature("F"), 1.0e-10);
    assertEquals(11.01325, conditions.getReliefSetPressure(), 1.0e-10);
    assertEquals(3.175, conditions.getCorrosionAllowance(), 1.0e-12);

    Map<Type, DesignConditionValue> snapshot = conditions.getConditions();
    assertEquals(5, snapshot.size());
    assertEquals(Type.MIN_DESIGN_TEMPERATURE,
        conditions.getCondition(Type.MIN_DESIGN_TEMPERATURE).get().getType());
    assertThrows(UnsupportedOperationException.class, snapshot::clear);
    assertFalse(new DesignConditions().getCondition(Type.DESIGN_PRESSURE).isPresent());
  }

  @Test
  void rejectsInvalidUnitsAndPhysicalValues() {
    assertThrows(IllegalArgumentException.class,
        () -> DesignConditionValue.of(Type.MAX_DESIGN_TEMPERATURE, -1.0, "K"));
    assertThrows(IllegalArgumentException.class,
        () -> DesignConditionValue.of(Type.CORROSION_ALLOWANCE, -1.0, "mm"));
    assertThrows(RuntimeException.class, () -> DesignConditionValue.of(Type.DESIGN_PRESSURE, 10.0, "unknown"));
    assertThrows(IllegalArgumentException.class, () -> new DesignConditions().setCondition(null));
  }

  @Test
  void mechanicalDesignUsesExplicitCanonicalUnits() {
    MechanicalDesign design = new MechanicalDesign(new Separator("unit test separator"));

    design.setMaxOperationPressure(145.03773773020922, "psia");
    design.setMinOperationPressure(0.0, "barg");
    design.setMaxOperationTemperature(212.0, "F");
    design.setMinOperationTemperature(32.0, "F");

    assertEquals(10.0, design.getMaxOperationPressure(), 1.0e-10);
    assertEquals(145.03773773020922, design.getMaxOperationPressure("psia"), 1.0e-8);
    assertEquals(1.01325, design.getMinOperationPressure(), 1.0e-10);
    assertEquals(373.15, design.getMaxOperationTemperature(), 1.0e-10);
    assertEquals(100.0, design.getMaxOperationTemperature("C"), 1.0e-10);
    assertEquals(273.15, design.getMinOperationTemperature(), 1.0e-10);
    assertEquals(11.0, design.getMaxDesignPressure(), 1.0e-10);

    MechanicalDesignResponse response = new MechanicalDesignResponse(design);
    assertEquals(100.0, response.getMaxOperatingTemperature(), 1.0e-10);
  }

  @Test
  void typedConditionSurvivesJavaSerialization() throws Exception {
    DesignConditions conditions = new DesignConditions().setDesignPressure(100.0, "barg")
        .setMaxDesignTemperature(350.0, "F").setCorrosionAllowance(0.125, "in");
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
      output.writeObject(conditions);
    }

    DesignConditions restored;
    try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      restored = (DesignConditions) input.readObject();
    }

    assertEquals(101.01325, restored.getDesignPressure(), 1.0e-10);
    assertEquals(176.66666666666666, restored.getMaxDesignTemperature(), 1.0e-10);
    assertEquals(3.175, restored.getCondition(Type.CORROSION_ALLOWANCE).get().getCanonicalValue(), 1.0e-12);
    assertTrue(restored.getConditions().containsKey(Type.DESIGN_PRESSURE));
  }
}
