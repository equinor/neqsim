package neqsim.process.mechanicaldesign.valve;

import java.awt.BorderLayout;
import java.awt.Container;
import java.util.Map;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import neqsim.process.costestimation.valve.ValveCostEstimate;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.equipment.valve.ValveInterface;
import neqsim.process.mechanicaldesign.MechanicalDesign;
import neqsim.process.mechanicaldesign.designstandards.ValveDesignStandard;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * ValveMechanicalDesign class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class ValveMechanicalDesign extends MechanicalDesign {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  neqsim.process.mechanicaldesign.valve.ControlValveSizing controlValveSizing =
      new neqsim.process.mechanicaldesign.valve.ControlValveSizing();
  double valveCvMax = 1.0;
  double valveWeight = 100.0;
  double inletPressure = 0.0;
  double outletPressure = 0.0;
  double dP = 0.0;
  double diameter = 0.1;
  double diameterInlet = 0.1;
  double diameterOutlet = 0.1;
  double xT = 0.137;
  double FL = 1.0;
  double FD = 1.0;
  boolean allowChoked = true;
  boolean allowLaminar = true;
  boolean fullOutput = true;

  /**
   * <p>
   * Constructor for ValveMechanicalDesign.
   * </p>
   *
   * @param equipment a {@link neqsim.process.equipment.ProcessEquipmentInterface} object
   */
  public ValveMechanicalDesign(ProcessEquipmentInterface equipment) {
    super(equipment);
    costEstimate = new ValveCostEstimate(this);
    controlValveSizing.setMethod("API"); // IEC / ANSI / / API
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
    SystemInterface fluid = getProcessEquipment().getFluid();
    if (getProcessEquipment().getFluid().hasPhaseType(PhaseType.GAS)) {
      Map<String, Object> result = ControlValveSizing.sizeControlValveGas(fluid.getTemperature("K"),
          fluid.getMolarMass("gr/mol"), fluid.getViscosity("kg/msec"), fluid.getGamma2(),
          fluid.getZ(), ((ValveInterface) getProcessEquipment()).getInletPressure() * 1e5,
          ((ValveInterface) getProcessEquipment()).getOutletPressure() * 1e5,
          fluid.getFlowRate("Sm3/sec"), diameterInlet, diameterOutlet, diameter, FL, FD, xT,
          allowChoked, allowLaminar, fullOutput);
      this.valveCvMax = (double) result.get("Cv");
    } else {
      Map<String, Object> result = ControlValveSizing.sizeControlValveLiquid(
          fluid.getDensity("kg/m3"), 1.0 * 1e5, fluid.getPC() * 1e5, fluid.getViscosity("kg/msec"),
          ((ValveInterface) getProcessEquipment()).getInletPressure() * 1e5,
          ((ValveInterface) getProcessEquipment()).getOutletPressure() * 1e5,
          fluid.getFlowRate("kg/hr") / fluid.getDensity("kg/m3"), diameterInlet, diameterOutlet,
          diameter, FL, FD, allowChoked, allowLaminar, fullOutput);
      this.valveCvMax = (double) result.get("Cv");
    }
    valveWeight = valveCvMax * 100.0;
    setWeightTotal(valveWeight);
  }

  public ControlValveSizing getControlValveSizing() {
    return new ControlValveSizing();
  }

  /** {@inheritDoc} */
  @Override
  @ExcludeFromJacocoGeneratedReport
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

  public void setControlValveSizing(
      neqsim.process.mechanicaldesign.valve.ControlValveSizing controlValveSizing) {
    this.controlValveSizing = controlValveSizing;
  }
}
