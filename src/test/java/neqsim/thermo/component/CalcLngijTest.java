package neqsim.thermo.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;
import neqsim.thermo.phase.PhaseEos;
import neqsim.thermo.phase.PhaseSrkEos;

/** Tests for calc_lngij implementations. */
public class CalcLngijTest {

  private PhaseSrkEos createPhase(double V, double B, ComponentEos comp0, ComponentEos comp1)
      throws Exception {
    PhaseSrkEos phase = new PhaseSrkEos();
    phase.componentArray[0] = comp0;
    phase.componentArray[1] = comp1;
    phase.numberOfComponents = 2;
    phase.setTotalVolume(V);
    phase.setConstantPhaseVolume(true);
    Field bField = PhaseEos.class.getDeclaredField("loc_B");
    bField.setAccessible(true);
    bField.setDouble(phase, B);
    return phase;
  }

  @Test
  public void testComponentUMRCPA() throws Exception {
    ComponentUMRCPA comp0 = new ComponentUMRCPA(0, 100, 10, 0, 0, 1);
    ComponentSrkCPA comp1 = new ComponentSrkCPA(1, 100, 10, 0, 0, 1, new PhaseSrkEos());
    comp0.Bi = 0.2;
    comp0.Bij[1] = 0.05;
    comp1.Bi = 0.3;
    PhaseSrkEos phase = createPhase(2.0, 0.5, comp0, comp1);
    double expected = 2.0 * comp0.getBij(1) * (10.0 * phase.getTotalVolume() - phase.getB())
        / ((8.0 * phase.getTotalVolume() - phase.getB())
            * (4.0 * phase.getTotalVolume() - phase.getB()));
    assertEquals(expected, comp0.calc_lngij(1, phase), 1e-12);
  }

  @Test
  public void testComponentElectrolyteCPA() throws Exception {
    ComponentElectrolyteCPA comp0 = new ComponentElectrolyteCPA(0, 100, 10, 0, 0, 1);
    ComponentSrkCPA comp1 = new ComponentSrkCPA(1, 100, 10, 0, 0, 1, new PhaseSrkEos());
    comp0.Bi = 0.2;
    comp0.Bij[1] = 0.05;
    comp1.Bi = 0.3;
    PhaseSrkEos phase = createPhase(3.0, 0.7, comp0, comp1);
    double expected = 2.0 * comp0.getBij(1) * (10.0 * phase.getTotalVolume() - phase.getB())
        / ((8.0 * phase.getTotalVolume() - phase.getB())
            * (4.0 * phase.getTotalVolume() - phase.getB()));
    assertEquals(expected, comp0.calc_lngij(1, phase), 1e-12);
  }

  @Test
  public void testComponentElectrolyteCPAstatoil() throws Exception {
    ComponentElectrolyteCPAstatoil comp0 =
        new ComponentElectrolyteCPAstatoil(0, 100, 10, 0, 0, 1);
    ComponentSrkCPA comp1 = new ComponentSrkCPA(1, 100, 10, 0, 0, 1, new PhaseSrkEos());
    comp0.Bi = 0.2;
    comp0.Bij[1] = 0.05;
    comp1.Bi = 0.3;
    PhaseSrkEos phase = createPhase(4.0, 0.9, comp0, comp1);
    double temp = phase.getTotalVolume() - 0.475 * phase.getB();
    double expected = 0.475 * comp0.getBij(1) * 0 / (phase.getTotalVolume() - 0.475 * phase.getB())
        - 0.475 * comp0.getBi() * 1.0 / (temp * temp)
            * (-0.475 * ((ComponentEosInterface) phase.getComponent(1)).getBi());
    assertEquals(expected, comp0.calc_lngij(1, phase), 1e-12);
  }

  @Test
  public void testComponentSrkCPAs() throws Exception {
    ComponentSrkCPAs comp0 = new ComponentSrkCPAs(0, 100, 10, 0, 0, 1, new PhaseSrkEos());
    ComponentSrkCPA comp1 = new ComponentSrkCPA(1, 100, 10, 0, 0, 1, new PhaseSrkEos());
    comp0.Bi = 0.2;
    comp0.Bij[1] = 0.05;
    comp1.Bi = 0.3;
    PhaseSrkEos phase = createPhase(5.0, 1.1, comp0, comp1);
    double temp = phase.getTotalVolume() - 0.475 * phase.getB();
    double expected = (0.475 * comp0.getBij(1) * temp
        + 0.475 * ((ComponentEosInterface) phase.getComponent(1)).getBi() * 0.475 * comp0.getBi())
        / (temp * temp);
    assertEquals(expected, comp0.calc_lngij(1, phase), 1e-12);
  }

  @Test
  public void testComponentSrkCPA() throws Exception {
    ComponentSrkCPA comp0 = new ComponentSrkCPA(0, 100, 10, 0, 0, 1, new PhaseSrkEos());
    ComponentSrkCPA comp1 = new ComponentSrkCPA(1, 100, 10, 0, 0, 1, new PhaseSrkEos());
    comp0.Bi = 0.2;
    comp0.Bij[1] = 0.05;
    comp1.Bi = 0.3;
    PhaseSrkEos phase = createPhase(6.0, 1.3, comp0, comp1);
    double temp = (10.0 * phase.getTotalVolume() - phase.getB())
        / ((8.0 * phase.getTotalVolume() - phase.getB())
            * (4.0 * phase.getTotalVolume() - phase.getB()));
    double temp1 = (8.0 * phase.getTotalVolume() - phase.getB());
    double temp2 = (4.0 * phase.getTotalVolume() - phase.getB());
    double temp3 = (10.0 * phase.getTotalVolume() - phase.getB());
    double tempj = (-((ComponentEosInterface) phase.getComponent(1)).getBi() * temp1 * temp2
        + ((ComponentEosInterface) phase.getComponent(1)).getBi() * temp3 * (temp1 + temp2)) / temp1
        / temp1 / temp2 / temp2;
    double expected = 2.0 * (comp0.getBij(1) * temp + comp0.getBi() * tempj);
    assertEquals(expected, comp0.calc_lngij(1, phase), 1e-12);
  }
}

