package neqsim.thermo.phase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import neqsim.thermo.component.ComponentSrkCPA;

public class PhaseSrkCPAArrayScalingTest {
  private static class DummyPhaseSrkCPA extends PhaseSrkCPA {
    private static final long serialVersionUID = 1L;

    @Override
    public void addComponent(String name, double moles, double molesInPhase, int compNumber) {
      super.addComponent(name, moles, compNumber);
      ComponentSrkCPA comp = new ComponentSrkCPA(compNumber, 200.0, 50.0, 16.0, 0.1, moles, this);
      comp.setNumberOfAssociationSites(1);
      comp.resizeXsitedni(getNumberOfComponents());
      componentArray[compNumber] = comp;
      for (int i = 0; i < getNumberOfComponents(); i++) {
        ((ComponentSrkCPA) componentArray[i]).resizeXsitedni(getNumberOfComponents());
      }
    }
  }

  @Test
  public void testXsitedniScales() {
    int n = 120;
    DummyPhaseSrkCPA phase = new DummyPhaseSrkCPA();
    for (int i = 0; i < n; i++) {
      phase.addComponent("c" + i, 1.0, 1.0, i);
    }
    for (int i = 0; i < n; i++) {
      ComponentSrkCPA comp = (ComponentSrkCPA) phase.componentArray[i];
      assertEquals(n, comp.getXsitedni()[0].length);
    }
  }
}
