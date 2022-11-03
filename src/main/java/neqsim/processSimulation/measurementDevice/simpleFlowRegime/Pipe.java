// Create a Pipe Object
package neqsim.processSimulation.measurementDevice.simpleFlowRegime;

public class Pipe {
  private String name = "Default severe slug pipe";
	private double internalDiameter = 0.05;
	private double leftLength = 167.0;
	private double rightLength = 7.7;
  private double angle = 2.0;
	final double pi = 3.1415926;

	// Default Constructor:
	Pipe(){
		this.setName(name);
		this.setInternalDiameter(internalDiameter);
		this.setLeftLength(leftLength);
		this.setRightLength(rightLength);
		this.setAngle(angle);
	}	

	// User Defined pipe parameters including pipe name (constructor):
	Pipe(String name, double internalDiameter, double leftLength, double rightLength, double angle){
		this.setName(name);
		this.setInternalDiameter(internalDiameter);
		this.setLeftLength(leftLength);
		this.setRightLength(rightLength);
		this.setAngle(angle);
	}

	// User Defined pipe parameters excluding pipe name (constructor):
	Pipe(double internalDiameter, double leftLength, double rightLength, double angle){
		this.setInternalDiameter(internalDiameter);
		this.setLeftLength(leftLength);
		this.setRightLength(rightLength);
		this.setAngle(angle);
	}
	
	// Encapsulation: Get and Set Methods. This keyword referes to the current object
	// 1. Pipe name encapsulation
	public void setName(String name) {
		this.name = name;
	}
	
	public String getName() {
		return name;
	}

	// 2. Pipe Internal Diameter encapsulation
	public void setInternalDiameter(double internalDiameter) {
		this.internalDiameter = internalDiameter;
	}
	
	public double getInternalDiameter() {
		return internalDiameter;
	}

	// 3. Pipe Internal Diameter encapsulation
	public void setLeftLength(double leftLength) {
		this.leftLength = leftLength;
	}
	
	public double getLeftLength() {
		return leftLength;
	}

	// 4. Pipe Right Length encapsulation
	public void setRightLength(double rightLength) {
		this.rightLength = rightLength;
	}
	
	public double getRightLength() {
		return rightLength;
	}

	// 4. Pipe Angle encapsulation
	public void setAngle(double angle) {
		this.angle = angle;
	}
	
	public double getAngle(String unit) {
		if (unit == "Degree"){
			return this.angle;
		}
		else if(unit == "Radian"){
			return this.angle*pi/180;
		}
		return this.angle;
	}

    public double getArea(){
        return pi*Math.pow(this.internalDiameter,2)/4;
    }
	
}
