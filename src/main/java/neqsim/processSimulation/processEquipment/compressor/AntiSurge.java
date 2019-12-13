package neqsim.processSimulation.processEquipment.compressor;

public class AntiSurge {

	private boolean isActive = false;
	private boolean isSurge = false;
	private double surgeControlFactor = 1.05;
	private double currentSurgeFraction = 0.0;

	public boolean isActive() {
		return isActive;
	}

	public void setActive(boolean isActive) {
		this.isActive = isActive;
	}

	public boolean isSurge() {
		return isSurge;
	}

	public void setSurge(boolean isSurge) {
		this.isSurge = isSurge;
	}

	public double getSurgeControlFactor() {
		return surgeControlFactor;
	}

	public void setSurgeControlFactor(double antiSurgeSafetyFactor) {
		this.surgeControlFactor = antiSurgeSafetyFactor;
	}

	public double getCurrentSurgeFraction() {
		return currentSurgeFraction;
	}

	public void setCurrentSurgeFraction(double currentSurgeFraction) {
		this.currentSurgeFraction = currentSurgeFraction;
	}
	
}
