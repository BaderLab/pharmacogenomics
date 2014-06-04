package pgx;

import com.itextpdf.awt.PdfGraphics2D;
import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Rectangle;
import com.itextpdf.text.pdf.PdfContentByte;
import com.itextpdf.text.pdf.PdfTemplate;
import com.itextpdf.text.pdf.PdfWriter;
import java.awt.Graphics2D;
import java.io.FileOutputStream;
import java.io.OutputStream;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;

/**
 * Exports a list of PGx JPanels to a multi-page PDF document.
 * 
 * @author rammar
 */
public class PGXPDFExporter {
	
	/* A4 paper dimensions to be preserved, but I need more pixels to display
	 * the results properly. In A4, width is length/sqrt(2). */
	//private static final float pageWidth= PageSize.A4.getWidth();
	//private static final float pageHeight= PageSize.A4.getHeight();
	private static final float pageHeight= 1400.0f;
	private static final float pageWidth= pageHeight/(float)Math.sqrt(2.0);
	
	private JTabbedPane tabs;
	private OutputStream os;
	
	/**
	 * Create a new PDF report for the list of panels. 
	 * Page order is based on order of panels in list.
	 * @param panels a list of JPanels
	 * @params filename the output pdf filename
	 */
	public PGXPDFExporter(JTabbedPane tabs, String filename) {
		this.tabs= tabs;
		
		try {
			this.os= new FileOutputStream(filename);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	
	/**
	 * Count the number of pages required given the dimensions of this JPanel.
	 * @return the number of pages
	 */
	private int getNumPages(JPanel jp) {
		/* Use the preferred size to get the entire panel height, rather
		* than the panel height within the frame. Use ceiling function to
		* map real page number to next whole page number. */
		return (int) Math.ceil(jp.getPreferredSize().height / pageHeight); // int divided by float
	}
	
	
	/**
	 * Create the multipage PDF report from the internal list of JPanels.
	 * @throws DocumentException 
	 */
	public void createMultipagePDF() throws DocumentException {
		// Document defaults to A4, so specify the current dimensions
		Document doc= new Document(new Rectangle(pageWidth, pageHeight));
		PdfWriter writer= PdfWriter.getInstance(doc, os);
		doc.open();
		PdfContentByte cb= writer.getDirectContent();
		
		// Iterate over tabs
		for (int i= 0; i != tabs.getTabCount(); ++i) {
			JPanel jp= (JPanel) tabs.getComponent(i);
			// Iterate over pages
			for (int currentPage= 0; currentPage < getNumPages(jp); ++currentPage) {
				doc.newPage(); // not needed for page 1, needed for >1

				PdfTemplate template= cb.createTemplate(pageWidth, pageHeight);
				Graphics2D g2d= new PdfGraphics2D(template, pageWidth, pageHeight * (currentPage + 1));
				jp.printAll(g2d);
				g2d.dispose();

				cb.addTemplate(template, 0, 0);
			}
		}
		
		doc.close();
	}
	
}
