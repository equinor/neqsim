/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package neqsim.thermo.characterization;

/**
 *
 * @author ESOL
 */
public interface LumpingModelInterface {

    public void setNumberOfLumpedComponents(int numb);

    public String getName();

    public void generateLumpedComposition(Characterise charac);

    public int getNumberOfLumpedComponents();
    
    public double getFractionOfHeavyEnd(int i);
    
    public void setNumberOfPseudoComponents(int lumpedNumb);
    
    public int getNumberOfPseudoComponents();
    
    public String getLumpedComponentName(int i);
    
  	public String[] getLumpedComponentNames();
  	
}
