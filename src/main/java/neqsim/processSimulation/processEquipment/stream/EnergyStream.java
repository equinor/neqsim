package neqsim.processSimulation.processEquipment.stream;

import java.io.Serializable;

import neqsim.thermo.system.SystemInterface;

public class EnergyStream implements Serializable, Cloneable {

    private double duty = 0.0;

    public static void main(String[] args) {
        // TODO Auto-generated method stub

    }

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
