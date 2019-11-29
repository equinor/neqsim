package neqsim.processSimulation.processEquipment.compressor;

public class CompressorCurve {
	
	double[] flow;
	double[] head;
	double[] polytropicEfficiency ;
	double speed = 1000.0;
	
	public CompressorCurve() {
		flow = new double[] {453.2, 600.0, 750.0};
		head = new double[] {1000.0, 900.0, 800.0};
		polytropicEfficiency = new double[] {78.0, 79.0, 78.0};
	}
	
	public CompressorCurve(double speed, double[] flow, double[] head, double[] polytropicEfficiency ) {
		this.speed = speed;
		this.flow = flow;
		this.head = head;
		this.polytropicEfficiency = polytropicEfficiency;
	}
	
	public static void main(String[] args) {
		// TODO Auto-generated method stub

	}

}
