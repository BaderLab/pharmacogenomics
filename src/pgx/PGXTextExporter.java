package pgx;

import java.io.FileNotFoundException;
import java.io.PrintWriter;
import org.apache.commons.lang3.StringUtils;

/**
 * Exports a list of pharmacogenomic diplotypes to a text document.
 * 
 * @author rammar
 */
public class PGXTextExporter {
	
	private PGXAnalysis analysis;
	private String filename;
	
	
	/**
	 * Create a new text output for the individual. 
	 * @param analysis the PGx analysis
	 * @params filename the output text filename
	 */
	public PGXTextExporter(PGXAnalysis analysis, String filename) {
		this.analysis= analysis;
		this.filename= filename;
	}
	
	
	/**
	 * Output the text report.
	 * NOTE: As per collaborator request, this file has no header.
	 */
	public void createTextReport() throws FileNotFoundException {
		PrintWriter pw= new PrintWriter(filename);
		
		// Iterate over all pgx genes and output the relevant pgx details.
		for (PGXGene pg : analysis.getGenes()) {
			String[] columns= new String[] {pg.getGene(), pg.getDiplotype(), pg.getMetabolizerClass()};
			String line= StringUtils.join(columns, "\t");
			pw.println(line);
		}
		
		pw.close();
	}
	
}
