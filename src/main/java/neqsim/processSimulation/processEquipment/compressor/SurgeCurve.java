package neqsim.processSimulation.processEquipment.compressor;

public class SurgeCurve {
	
	double[] flow;
	double[] head;
	
	public SurgeCurve() {
		flow = new double[] {453.2, 600.0, 750.0};
		head = new double[] {1000.0, 900.0, 800.0};
	}
	
	public SurgeCurve(double[] flow, double[] head) {
		this.flow = flow;
		this.head = head;
	}
	
	public static void main(String[] args) {
		// TODO Auto-generated method stub

	}

}
