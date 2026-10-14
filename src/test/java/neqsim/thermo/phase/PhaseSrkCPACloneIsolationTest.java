package neqsim.thermo.phase;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;
import org.ejml.data.DMatrixRMaj;
import org.ejml.simple.SimpleMatrix;
import org.junit.jupiter.api.Test;
import neqsim.thermo.component.ComponentSrkCPA;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermodynamicoperations.flashops.TPflash;

/**
 * Checks ownership of initialized default CPA association state without depending on thread scheduling.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
class PhaseSrkCPACloneIsolationTest {
  /**
   * Checks every initialized association workspace, including nested rows and matrix data, at the public clone
   * boundary.
   *
   * @throws Exception if reflective inspection of the owned workspaces fails
   */
  @Test
  void initializedPhaseClonePreservesValuesAndOwnsAssociationBuffers() throws Exception {
    PhaseSrkCPA source = initializedPhase();
    Map<String, Double> original = snapshot(source);
    PhaseSrkCPA copy = source.clone();
    assertEquals(original, snapshot(copy));
    assertNotNull(source.delta);
    assertNotNull(field(source, "hessianMatrix"));
    for (Field field : PhaseSrkCPA.class.getDeclaredFields()) {
      if (!isBuffer(field)) {
        continue;
      }
      field.setAccessible(true);
      assertOwned(field.get(source), field.get(copy), field.getName());
    }
    copy.delta[0][0] += 1.0;
    copy.Klkni[0][0][0] += 1.0;
    ((SimpleMatrix) field(copy, "hessianMatrix")).set(0, 0, 1.0);
    assertEquals(original, snapshot(source));
  }

  /**
   * Verifies component composition-derivative rows are detached while initialized site values remain available.
   */
  @Test
  void initializedComponentCloneOwnsCompositionDerivativeRows() {
    PhaseSrkCPA sourcePhase = initializedPhase();
    ComponentSrkCPA source = (ComponentSrkCPA) sourcePhase.getComponent("water");
    ComponentSrkCPA copy = source.clone();
    assertTrue(source.getXsitedni().length > 0);
    for (int site = 0; site < source.getXsitedni().length; site++) {
      assertNotSame(source.getXsitedni()[site], copy.getXsitedni()[site]);
      assertArrayEquals(source.getXsitedni()[site], copy.getXsitedni()[site], 0.0);
    }
    assertArrayEquals(source.getXsite(), copy.getXsite(), 0.0);
    double original = source.getXsitedni(0, 0);
    copy.setXsitedni(0, 0, original + 1.0);
    assertEquals(original, source.getXsitedni(0, 0), 0.0);
  }

  /**
   * Uses cached association derivatives immediately after cloning, before any initialization or new association solve.
   *
   * @throws Exception if reflective inspection of the factorization fails
   */
  @Test
  void cloneRebuildsPrivateFactorizationForImmediateDerivativeRequest() throws Exception {
    PhaseSrkCPA source = initializedPhase();
    Map<String, Double> original = snapshot(source);
    assertNotNull(field(source, "hessianLU"));
    assertNull(field(source, "hessianInvers"));
    PhaseSrkCPA copy = source.clone();
    assertNull(field(copy, "hessianLU"));
    copy.calcXsitedV();
    copy.initCPAMatrix(3);
    assertNotNull(field(copy, "hessianLU"));
    assertNotSame(field(source, "hessianLU"), field(copy, "hessianLU"));
    assertNotSame(field(source, "hessianLUinput"), field(copy, "hessianLUinput"));
    for (int component = 0; component < source.getNumberOfComponents(); component++) {
      ComponentSrkCPA first = (ComponentSrkCPA) source.getComponent(component);
      ComponentSrkCPA second = (ComponentSrkCPA) copy.getComponent(component);
      assertArrayEquals(first.getXsite(), second.getXsite(), 0.0);
      assertArrayEquals(first.getXsitedV(), second.getXsitedV(), 1.0e-12);
      assertArrayEquals(first.getXsitedT(), second.getXsitedT(), 1.0e-12);
      assertArrayEquals(first.getXsitedTdT(), second.getXsitedTdT(), 1.0e-12);
      for (int site = 0; site < first.getXsitedni().length; site++) {
        assertArrayEquals(first.getXsitedni()[site], second.getXsitedni()[site], 1.0e-12);
      }
    }
    assertEquals(source.getCp(), copy.getCp(), Math.abs(source.getCp()) * 1.0e-12);
    assertEquals(original, snapshot(source));
  }

  /**
   * Builds a configured, flashed and derivative-initialized default Statoil CPA liquid phase.
   *
   * @return initialized liquid with active water association sites
   */
  private PhaseSrkCPA initializedPhase() {
    SystemInterface fluid = new SystemSrkCPAstatoil(298.15, 60.0);
    fluid.addComponent("methane", 0.9);
    fluid.addComponent("water", 0.1);
    fluid.setMixingRule(10);
    new TPflash(fluid).run();
    fluid.init(3);
    assertEquals(2, fluid.getNumberOfPhases());
    PhaseSrkCPA phase = (PhaseSrkCPA) fluid.getPhase(1);
    assertTrue(phase.getTotalNumberOfAccociationSites() > 0);
    return phase;
  }

  /**
   * Identifies numerical buffers declared by the base CPA phase, excluding its transient LU input.
   *
   * @param field candidate field
   * @return true for an owned, cached array or matrix
   */
  private boolean isBuffer(Field field) {
    return !field.getName().equals("hessianLUinput")
        && (field.getType().isArray() || field.getType() == SimpleMatrix.class || field.getType() == DMatrixRMaj.class);
  }

  /**
   * Inspects a private CPA cache without changing its contents.
   *
   * @param phase phase to inspect
   * @param name field name
   * @return field value
   * @throws Exception if the named field is unavailable
   */
  private Object field(PhaseSrkCPA phase, String name) throws Exception {
    Field field = PhaseSrkCPA.class.getDeclaredField(name);
    field.setAccessible(true);
    return field.get(phase);
  }

  /**
   * Checks nested buffer identity as well as the storage underlying EJML matrix wrappers.
   *
   * @param source original buffer
   * @param copy copied buffer
   * @param name diagnostic path
   */
  private void assertOwned(Object source, Object copy, String name) {
    if (source == null) {
      assertNull(copy, name);
      return;
    }
    assertNotSame(source, copy, name);
    if (source instanceof SimpleMatrix) {
      assertNotSame(((SimpleMatrix) source).getDDRM().data, ((SimpleMatrix) copy).getDDRM().data, name);
    } else if (source instanceof DMatrixRMaj) {
      assertNotSame(((DMatrixRMaj) source).data, ((DMatrixRMaj) copy).data, name);
    } else if (source.getClass().getComponentType().isArray()) {
      assertEquals(Array.getLength(source), Array.getLength(copy), name);
      for (int i = 0; i < Array.getLength(source); i++) {
        assertOwned(Array.get(source, i), Array.get(copy, i), name + "." + i);
      }
    }
  }

  /**
   * Captures cached association buffers and site derivatives without invoking initialization.
   *
   * @param phase phase to inspect
   * @return detached cache values
   * @throws Exception if reflective access fails
   */
  private Map<String, Double> snapshot(PhaseSrkCPA phase) throws Exception {
    Map<String, Double> values = new LinkedHashMap<String, Double>();
    for (Field field : PhaseSrkCPA.class.getDeclaredFields()) {
      if (isBuffer(field)) {
        field.setAccessible(true);
        append(values, field.getName(), field.get(phase));
      }
    }
    for (int i = 0; i < phase.getNumberOfComponents(); i++) {
      ComponentSrkCPA component = (ComponentSrkCPA) phase.getComponent(i);
      append(values, "x." + i, component.getXsite());
      append(values, "xV." + i, component.getXsitedV());
      append(values, "xT." + i, component.getXsitedT());
      append(values, "xTT." + i, component.getXsitedTdT());
      append(values, "xn." + i, component.getXsitedni());
    }
    values.put("F", phase.getF());
    values.put("Ftt", phase.dFdTdT());
    values.put("cp", phase.getCp());
    values.put("cv", phase.getCv());
    return values;
  }

  /**
   * Flattens numeric arrays and matrices into named immutable scalar entries.
   *
   * @param values target snapshot
   * @param name buffer path
   * @param buffer nullable array, matrix or scalar
   */
  private void append(Map<String, Double> values, String name, Object buffer) {
    if (buffer == null) {
      return;
    }
    if (buffer instanceof SimpleMatrix) {
      append(values, name, ((SimpleMatrix) buffer).getDDRM().data);
    } else if (buffer instanceof DMatrixRMaj) {
      append(values, name, ((DMatrixRMaj) buffer).data);
    } else if (buffer.getClass().isArray()) {
      for (int i = 0; i < Array.getLength(buffer); i++) {
        append(values, name + "." + i, Array.get(buffer, i));
      }
    } else {
      values.put(name, ((Number) buffer).doubleValue());
    }
  }
}
