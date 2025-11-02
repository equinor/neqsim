import neqsim.process.equipment.distillation.DistillationColumn;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

public class DebugColumn {
    public static void main(String[] args) {
        SystemInterface simpleSystem = new SystemSrkEos(298.15, 5.0);
        simpleSystem.addComponent("methane", 1.0);
        simpleSystem.addComponent("ethane", 1.0);
        simpleSystem.createDatabase(true);
        simpleSystem.setMixingRule("classic");

        Stream feed = new Stream("feed", simpleSystem);
        feed.run();

        DistillationColumn column = new DistillationColumn("test column", 1, true, true);
        column.addFeedStream(feed, 1);
        column.runBroyden(java.util.UUID.randomUUID());

        System.out.println("Temperature residual: " + column.getLastTemperatureResidual());
        System.out.println("Mass residual: " + column.getLastMassResidual());
        System.out.println("Energy residual: " + column.getLastEnergyResidual());
        System.out.println("Temperature tolerance: " + column.getTemperatureTolerance());
        System.out.println("Mass tolerance: " + column.getMassBalanceTolerance());
        System.out.println("Energy tolerance: " + column.getEnthalpyBalanceTolerance());
        System.out.println("Solved: " + column.solved());
    }
}