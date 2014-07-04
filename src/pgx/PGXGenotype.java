package pgx;

/**
 * Simple class to store genotypes and the status of the genotype, including whether it was
 * directly measured or inferred from missing data. For example, most reference calls 
 * (if not all) are inferred when no genotype is present in a VCF file.
 * 
 * @author rammar
 */
public class PGXGenotype {
	
	private String genotype;
	private boolean isInferred;
	private int coverage= 0;
	
	
	/**
	 * Create the genotype.
	 * @param genotype The genotype String
	 * @param isInferred true if inferred, false if measured directly from seq output
	 * @param coverage the sequence coverage for this genotype observation
	 */
	public PGXGenotype(String genotype, boolean isInferred, int coverage) {
		this.genotype= genotype;
		this.isInferred= isInferred;
		
		// Only accept coverage that is initialized. Coverage == -1 indicates
		// coverage is absent from the VCF (for example, from array data).
		if (coverage > 0) {
			this.coverage= coverage;
		}
	}
	
	
	/**
	 * Get the genotype String.
	 * @return the genotype String
	 */
	public String getGenotype() {
		return this.genotype;
	}
	
	
	/**
	 * Return true if genotype is inferred; false if directly measure from seq output.
	 * @return true if genotype is inferred; false if directly measure from seq output.
	 */
	public boolean getInferredStatus() {
		return this.isInferred;
	}
	
	
	/**
	 * Get the sequence coverage for this genotype
	 * @return the sequence coverage for this genotype
	 */
	public int getCoverage() {
		return this.coverage;
	}
}
