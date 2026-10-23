package neqsim.thermo.phase;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import org.ejml.data.DMatrixRMaj;
import org.ejml.simple.SimpleMatrix;
import org.junit.jupiter.api.Test;
import neqsim.thermo.component.ComponentSrkCPA;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermodynamicoperations.flashops.TPflash;

/**
 * Verifies ownership of initialized ordinary CPA phase association state.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
class PhaseSrkCPACloneIsolationTest {
  /**
   * Checks that cloned numerical workspaces and their nested storage are independent.
   *
   * @throws Exception if reflective inspection of the owned workspaces fails
   */
  @Test
  void initializedPhaseCloneOwnsAssociationBuffers() throws Exception {
    PhaseSrkCPA source = initializedPhase();
    PhaseSrkCPA copy = source.clone();
    assertNotNull(source.delta);
    assertNotNull(field(source, "hessianMatrix"));
    for (Field field : PhaseSrkCPA.class.getDeclaredFields()) {
      if (!isOwnedBuffer(field)) {
        continue;
      }
      field.setAccessible(true);
      assertOwned(field.get(source), field.get(copy), field.getName());
    }

    double sourceDelta = source.delta[0][0];
    double sourceKlkni = source.Klkni[0][0][0];
    double sourceHessian = ((SimpleMatrix) field(source, "hessianMatrix")).get(0, 0);
    copy.delta[0][0] += 1.0;
    copy.Klkni[0][0][0] += 1.0;
    ((SimpleMatrix) field(copy, "hessianMatrix")).set(0, 0, sourceHessian + 1.0);
    assertEquals(sourceDelta, source.delta[0][0], 0.0);
    assertEquals(sourceKlkni, source.Klkni[0][0][0], 0.0);
    assertEquals(sourceHessian, ((SimpleMatrix) field(source, "hessianMatrix")).get(0, 0), 0.0);
  }

  /**
   * Checks that a clone rebuilds a private LU factorization before using cached derivatives.
   *
   * @throws Exception if reflective inspection of the factorization fails
   */
  @Test
  void cloneRebuildsPrivateFactorizationForImmediateDerivativeRequest() throws Exception {
    PhaseSrkCPA source = initializedPhase();
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
      assertArrayEquals(first.getXsitedVdV(), second.getXsitedVdV(), 1.0e-12);
      assertArrayEquals(first.getXsitedVdVdV(), second.getXsitedVdVdV(), 1.0e-12);
      assertArrayEquals(first.getXsitedT(), second.getXsitedT(), 1.0e-12);
      assertArrayEquals(first.getXsitedTdV(), second.getXsitedTdV(), 1.0e-12);
      assertArrayEquals(first.getXsitedTdT(), second.getXsitedTdT(), 1.0e-12);
      for (int site = 0; site < first.getXsitedni().length; site++) {
        assertArrayEquals(first.getXsitedni()[site], second.getXsitedni()[site], 1.0e-12);
      }
    }
    assertEquals(source.getCp(), copy.getCp(), Math.abs(source.getCp()) * 1.0e-12);
  }

  /**
   * Builds a configured, flashed and derivative-initialized ordinary CPA liquid phase.
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
   * Identifies numerical buffers owned by the ordinary CPA phase.
   *
   * @param field candidate field
   * @return true for an owned array or matrix buffer
   */
  private boolean isOwnedBuffer(Field field) {
    return !field.getName().equals("hessianLUinput")
        && (field.getType().isArray() || field.getType() == SimpleMatrix.class || field.getType() == DMatrixRMaj.class);
  }

  /**
   * Reads a private field without changing its contents.
   *
   * @param phase phase to inspect
   * @param name field name
   * @return field value
   * @throws Exception if the field is unavailable
   */
  private Object field(PhaseSrkCPA phase, String name) throws Exception {
    Field field = PhaseSrkCPA.class.getDeclaredField(name);
    field.setAccessible(true);
    return field.get(phase);
  }

  /**
   * Checks nested array identity and EJML matrix backing storage.
   *
   * @param source source buffer
   * @param copy cloned buffer
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
}
