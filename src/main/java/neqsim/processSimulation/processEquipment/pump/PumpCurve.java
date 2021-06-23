package neqsim.processSimulation.processEquipment.pump;

public class PumpCurve implements java.io.Serializable {
    private static final long serialVersionUID = 1000;
    public double[] flow;
    public double[] head;
    public double[] polytropicEfficiency;
    public double speed = 1000.0;

    public PumpCurve() {
        flow = new double[] { 453.2, 600.0, 750.0 };
        head = new double[] { 1000.0, 900.0, 800.0 };
        polytropicEfficiency = new double[] { 78.0, 79.0, 78.0 };
    }

    public PumpCurve(double speed, double[] flow, double[] head, double[] polytropicEfficiency) {
        this.speed = speed;
        this.flow = flow;
        this.head = head;
        this.polytropicEfficiency = polytropicEfficiency;
    }

    public static void main(String[] args) {
        // TODO Auto-generated method stub
    }
}
