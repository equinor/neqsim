package neqsim.processSimulation.processEquipment.distillation;

import neqsim.processSimulation.processEquipment.ProcessEquipmentInterface;
import neqsim.processSimulation.processEquipment.stream.StreamInterface;

/**
 *
 * @author ESOL
 */
public interface TrayInterface extends ProcessEquipmentInterface {
    @Override
    public void run();

    public void addStream(StreamInterface newStream);

    @Override
    public void setName(String name);

    @Override
    public String getName();

    public void setHeatInput(double heatinp);

    public void runTransient();
}
