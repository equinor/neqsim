package neqsim.processSimulation.mechanicalDesign.valve;

import java.awt.BorderLayout;
import java.awt.Container;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import neqsim.processSimulation.costEstimation.valve.ValveCostEstimate;
import neqsim.processSimulation.mechanicalDesign.MechanicalDesign;
import neqsim.processSimulation.mechanicalDesign.designStandards.ValveDesignStandard;
import neqsim.processSimulation.processEquipment.ProcessEquipmentInterface;
import neqsim.processSimulation.processEquipment.valve.ThrottlingValve;;

/**
 * <p>
 * ValveMechanicalDesign class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class ValveMechanicalDesign extends MechanicalDesign {
  private static final long serialVersionUID = 1000;

  double valveCvMax = 1.0;
  double valveWeight = 100.0;
  double inletPressure = 0.0;
  double outletPressure = 0.0;
  double dP = 0.0;

  /**
   * <p>
   * Constructor for ValveMechanicalDesign.
   * </p>
   *
   * @param equipment a {@link neqsim.processSimulation.processEquipment.ProcessEquipmentInterface}
   *        object
   */
  public ValveMechanicalDesign(ProcessEquipmentInterface equipment) {
    super(equipment);
    costEstimate = new ValveCostEstimate(this);
  }

  /** {@inheritDoc} */
  @Override
  public void readDesignSpecifications() {
    super.readDesignSpecifications();

    if (getDesignStandard().containsKey("valve design codes")) {
      System.out.println("valve code standard: "
          + getDesignStandard().get("valve design codes").getStandardName());
      valveCvMax =
          ((ValveDesignStandard) getDesignStandard().get("valve design codes")).getValveCvMax();
    } else {
      System.out.println("no valve code standard specified......using default");
    }
  }

  /** {@inheritDoc} */
  @Override
  public void calcDesign() {
    super.calcDesign();
    ThrottlingValve valve1 = (ThrottlingValve) getProcessEquipment();
    inletPressure = valve1.getInletPressure();
    outletPressure = valve1.getOutletPressure();
    dP = inletPressure - outletPressure;

    valveCvMax = valve1.getThermoSystem().getVolume("m3/hr")
        * Math.sqrt(valve1.getThermoSystem().getDensity("kg/m3") / 1000.0 / dP);
    valveWeight = valveCvMax * 100.0;
  }

  /** {@inheritDoc} */
  @Override
  public void displayResults() {
    JFrame dialog = new JFrame("Unit design " + getProcessEquipment().getName());
    Container dialogContentPane = dialog.getContentPane();
    dialogContentPane.setLayout(new BorderLayout());

    String[] names = {"Name", "Value", "Unit"};

    String[][] table = new String[16][3]; // createTable(getProcessEquipment().getName());

    table[1][0] = "Valve weight [kg]";
    table[1][1] = Double.toString(valveWeight);
    table[1][2] = "kg";

    table[2][0] = "Valve Cv";
    table[2][1] = Double.toString(valveCvMax);
    table[2][2] = "-";

    table[3][0] = "Inlet pressure [bar]";
    table[3][1] = Double.toString(inletPressure);
    table[3][2] = "bar";

    table[4][0] = "outlet pressure [bar]";
    table[4][1] = Double.toString(outletPressure);
    table[4][2] = "bar";

    JTable Jtab = new JTable(table, names);
    JScrollPane scrollpane = new JScrollPane(Jtab);
    dialogContentPane.add(scrollpane);
    dialog.setSize(800, 600); // pack();
    // dialog.pack();
    dialog.setVisible(true);
  }
}
