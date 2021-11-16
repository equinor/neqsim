package neqsim.processSimulation.processEquipment.stream;

public class EnergyStream implements java.io.Serializable, Cloneable {
    private double duty = 0.0;

    public static void main(String[] args) {}

    @Override
    public Object clone() {
        EnergyStream clonedStream = null;
        try {
            clonedStream = (EnergyStream) super.clone();
        } catch (Exception e) {
            e.printStackTrace(System.err);
        }
        return clonedStream;
    }

    public double getDuty() {
        return duty;
    }

    public void setDuty(double duty) {
        this.duty = duty;
    }
}
