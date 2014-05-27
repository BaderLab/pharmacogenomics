package pgx;

import com.jidesoft.swing.ButtonStyle;
import com.jidesoft.swing.JideButton;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.math.BigDecimal;
import java.net.URL;
import java.net.URLEncoder;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.ScrollPaneConstants;
import net.miginfocom.swing.MigLayout;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.ut.biolab.medsavant.MedSavantClient;
import org.ut.biolab.medsavant.client.project.ProjectController;
import org.ut.biolab.medsavant.client.util.ClientMiscUtils;
import org.ut.biolab.medsavant.client.util.MedSavantWorker;
import org.ut.biolab.medsavant.client.view.component.ProgressWheel;
import org.ut.biolab.medsavant.client.view.dialog.IndividualSelector;
import org.ut.biolab.medsavant.client.view.login.LoginController;
import org.ut.biolab.medsavant.client.view.util.DialogUtils;
import org.ut.biolab.medsavant.client.view.util.ViewUtil;
import org.ut.biolab.medsavant.shared.appdevapi.AppColors;
import org.ut.biolab.medsavant.shared.appdevapi.DBAnnotationColumns;
import org.ut.biolab.medsavant.shared.appdevapi.Variant;
import org.ut.biolab.medsavant.shared.db.TableSchema;
import org.ut.biolab.medsavant.shared.format.CustomField;
import org.ut.biolab.medsavant.shared.serverapi.AnnotationManagerAdapter;
import pgx.localDB.PGXDBFunctions;
import pgx.localDB.PGXDBFunctions.PGXMarker;

/**
 * Default panel for Pharmacogenomics app.
 * @author rammar
 */
public class PGXPanel {

	private static Log log= LogFactory.getLog(MedSavantClient.class);
	private static final int CHOOSE_PATIENT_BUTTON_WIDTH= 250;
	private static final int SIDE_PANE_WIDTH= 380;
	private static final int SIDE_PANE_WIDTH_OFFSET= 20;
	private static final String baseDBSNPUrl= "http://www.ncbi.nlm.nih.gov/SNP/snp_ref.cgi?searchType=adhoc_search&rs=";
	private static final String basePubmedUrl= "http://www.ncbi.nlm.nih.gov/pubmed/";
	private static final Color DEFAULT_LABEL_COLOUR= (new JTextField()).getForeground();
	
	private static List<String> afColumnNames;	
	
	/* Patient information. */
	private String currentHospitalID;
	private String currentDNAID;
	private PGXAnalysis currentPGXAnalysis;
	
	/* UI components. */
	private JPanel appView;
	private JScrollPane patientSidePane;
	private JPanel patientSideJP;
	private JScrollPane reportPane;
	private JideButton choosePatientButton;
	private IndividualSelector patientSelector;
	private JLabel status;
	private ProgressWheel statusWheel;
	private JPanel reportInitJP;
	private JLabel reportStartLabel;
	private JCheckBox assumeRefCheckBox;
	
	
	public PGXPanel() {
		setupApp();
	}
	
	
	/**
	 * Get the main PGx panel view.
	 * @return the main PGx JPanel
	 */
	public JPanel getView() {
		return appView;
	}
	
	
	/**
	 * Set up the main app.
	 */
	private void setupApp() {
		// initialize and set up the main app JPanel
		appView= new JPanel();
		appView.setLayout(new MigLayout("insets 0px, gapx 0px"));
		
		// Create and add components to the patient side panel
		initPatientSidePanel();
		initReportPanel();
		
		// Add all the components to the main app view
		appView.add(patientSidePane);
		appView.add(reportPane);
		
		// set the preferred size once the component is displayed.
		appView.addComponentListener(new ComponentListener()
			{				
				@Override
				public void componentShown(ComponentEvent e) {
					Dimension d= appView.getSize();
					reportPane.setPreferredSize(new Dimension(d.width - SIDE_PANE_WIDTH, d.height));
					reportPane.setMinimumSize(new Dimension(d.width - SIDE_PANE_WIDTH, d.height));
					reportPane.setMaximumSize(new Dimension(d.width - SIDE_PANE_WIDTH, d.height));
					appView.updateUI();
				}
				
				@Override
				public void componentResized(ComponentEvent e) {
					componentShown(e);
				}
				
				@Override public void componentHidden(ComponentEvent e) {}
				@Override public void componentMoved(ComponentEvent e) {}
			}
		);
	}
	
	
	/**
	 * Action to perform when choose patient button is clicked.
	 * @return the ActionListener for this button
	 */
	private ActionListener choosePatientAction() {
		// create an anonymous class
		ActionListener outputAL= new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent ae) {
				/* Show the patient selector window and get the patient selected
				 * by user. */
				patientSelector.setVisible(true);
				Set<String> selectedIndividuals= patientSelector.getHospitalIDsOfSelectedIndividuals();
				
				/* Once the user has made a patient hospital ID selection, get 
				 * the DNA ID so we can retrieve the patient's variants. */
				if (patientSelector.hasMadeSelection()) {
					currentHospitalID= selectedIndividuals.iterator().next();
					currentDNAID= patientSelector.getDNAIDsOfSelectedIndividuals().iterator().next();
					
					if (currentDNAID != null) {
						choosePatientButton.setText(currentHospitalID);
					} else { // can't find this individual's DNA ID - may be a DB error
						errorDialog("Can't find a DNA ID for " + currentHospitalID);
					}
				}
				
				/* Perform a new pharmacogenomic analysis for this DNA ID. */
				analyzePatient();
			}
		};
		
		return outputAL;
	}
	
	
	/**
	 * Initialize the patient side panel.
	 */
	private void initPatientSidePanel() {		
		patientSideJP= new JPanel();
		
		// the patient selector dialog
		patientSelector= new IndividualSelector(true);
		
		// the choose patient button
		choosePatientButton= new JideButton("Choose Patient");
		choosePatientButton.setButtonStyle(JideButton.TOOLBAR_STYLE);
		choosePatientButton.setOpaque(true);
		choosePatientButton.setFont(new Font(choosePatientButton.getFont().getName(),
			Font.PLAIN, 18));
		choosePatientButton.setMinimumSize(new Dimension(
			CHOOSE_PATIENT_BUTTON_WIDTH, choosePatientButton.getHeight()));
		choosePatientButton.addActionListener(choosePatientAction());
		
		// The status message
		status= new JLabel();
		status.setFont(new Font(status.getFont().getName(), Font.PLAIN, 15));
		statusWheel= new ProgressWheel();
		statusWheel.setIndeterminate(true);
		// hide for now
		status.setVisible(false);
		statusWheel.setVisible(false);
		
		// Checkbox governing assumptions about missing markers
		assumeRefCheckBox= new JCheckBox("Treat missing markers as Reference calls", true);
		assumeRefCheckBox.addActionListener(toggleReferenceAction());
		
		/* Layout notes:
		 * Create a bit of inset spacing top and left, no space between 
		 * components unless explicitly specified.
		 * Also, the components will not be centred within the panel, unless the
		 * entire panel is filled widthwise - this means that if you add a small
		 * button, when you centre it, it won't be centred in the panel unless
		 * you have a full-panel-width component below/above it. I'm specifying 
		 * "fillx" for the layout to solve this issue. */
		patientSideJP.setLayout(new MigLayout("insets 10 10 0 0, gapy 0px, fillx"));
		//patientSideJP.setBackground(ViewUtil.getSidebarColor());
		patientSideJP.setBackground(AppColors.HummingBird);
		// Add a light border only on the right side.
		patientSideJP.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, Color.LIGHT_GRAY));
		patientSideJP.setMinimumSize(new Dimension(SIDE_PANE_WIDTH, 0)); // minimum width for panel
		
		patientSideJP.add(choosePatientButton, "alignx center, wrap");
		patientSideJP.add(new JLabel("This app uses the CPIC guidelines"), "alignx center, gapy 20px, wrap");
		patientSideJP.add(new JLabel("BETA TESTING!!!"), "alignx center, gapy 20px, wrap");
		patientSideJP.add(assumeRefCheckBox, "alignx center, gapy 20px, wrap");
		patientSideJP.add(status, "alignx center, gapy 50px, wrap");
		patientSideJP.add(statusWheel, "alignx center, wrap");
		
		// initialize the scroll pane and set size constraints
		patientSidePane= new JScrollPane();
		patientSidePane.setBorder(BorderFactory.createEmptyBorder());
		patientSidePane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		patientSidePane.setMinimumSize(new Dimension(SIDE_PANE_WIDTH, 0)); // minimum width
		patientSidePane.setPreferredSize(new Dimension(SIDE_PANE_WIDTH, 
			patientSideJP.getMaximumSize().height)); // preferred height
		patientSidePane.setViewportView(patientSideJP);
	}
	
	
	/**
	 * Initialize the report panel.
	 */
	private void initReportPanel() {
		reportInitJP= new JPanel();
		reportStartLabel= new JLabel("Choose a patient to start a pharmacogenomic analysis.");
		
		reportStartLabel.setFont(new Font(reportStartLabel.getFont().getName(), Font.PLAIN, 14));
		reportStartLabel.setForeground(Color.DARK_GRAY);
		
		reportInitJP.setLayout(new MigLayout("align 50% 50%"));
		reportInitJP.add(reportStartLabel);
		
		reportPane= new JScrollPane();
		reportPane.setBorder(BorderFactory.createEmptyBorder());
		//reportPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		reportPane.setViewportView(reportInitJP);
		
		/* NOTE: reportPane size preferences are set upon appView component 
		 * shown or resizing actions. */
	}
	
	
	/**
	 * Blank report panel to display when a new analysis is being run.
	 */
	private void clearReportPanel() {
		reportStartLabel.setText("Obtaining pharmacogenomic report for " + 
			this.currentHospitalID + "...");
		reportPane.setViewportView(reportInitJP);
	}
	
	
	/** 
	 * Perform a new pharmacogenomic analysis for this DNA ID in a background
	 * thread and display a progress status message. 
	 */
	private void analyzePatient() {
		// Update status message
		status.setText("Performing pharmacogenomic analysis...");
		status.setVisible(true);
		statusWheel.setVisible(true);
		
		// Clear the report panel to avoid confusing this patient for the previous one
		clearReportPanel();
		
		/* Background task. */
		MedSavantWorker pgxAnalysisThread= new MedSavantWorker<Object>(PGXPanel.class.getCanonicalName()) {
			@Override
			protected Object doInBackground() throws Exception {
				/* Create and perform a new analysis. */
				try {
					currentPGXAnalysis= new PGXAnalysis(currentDNAID, assumeRefCheckBox.isSelected());
				} catch (SQLException se) {
					errorDialog(se.getMessage());
					se.printStackTrace();
				}

				return null;
			}

			@Override protected void showSuccess(Object t) {
				status.setText("Analysis complete.");
				statusWheel.setVisible(false);
				
				/* Update the report pane. */
				updateReportPane();
			}
		};
		
		// Execute thread
		pgxAnalysisThread.execute();
	}
	
	
	/**
	 * Update the report panel.
	 */
	private void updateReportPane() {
		JTabbedPane tabs= ViewUtil.getMSTabedPane();
		
		/* Create a summary tab. */
		JPanel summary= new JPanel();
		summary.setLayout(new MigLayout("gapx 30px"));
		summary.add(createLabel("Gene", true, 20));
		summary.add(createLabel("Diplotype", true, 20));
		summary.add(createLabel("Therapeutic class", true, 20), "wrap");
		for (PGXGene pg : currentPGXAnalysis.getGenes()) {
			summary.add(createLabel(pg.getGene(), false, 20));
			summary.add(createLabel(pg.getDiplotype(), false, 20));
			summary.add(createLabel(pg.getMetabolizerClass(), false, 20), "wrap");
		}
		tabs.addTab("Summary", summary);

		/* For each PGx gene, create a separate tab. */
		for (PGXGene pg : currentPGXAnalysis.getGenes()) {
			JPanel reportJP= new JPanel();
			reportJP.setLayout(new MigLayout("gapx 30px"));
			
			reportJP.add(createLabel("Diplotype", true, 22));
			reportJP.add(createLabel(pg.getDiplotype(), false, 22), "wrap");
			
			reportJP.add(createLabel("Therapeutic class", true, 22));
			reportJP.add(createLabel(pg.getMetabolizerClass(), false, 22), "wrap");
			
			/* Add pubmed links. */
			reportJP.add(createLabel("Publications", true, 22), "gapy 30px, aligny top");
			List<String> pubmedIDs= PGXDBFunctions.getPubMedIDs(pg.getGene());
			for (int i= 0; i != pubmedIDs.size(); ++i) {
				JButton jb= getURLButton(pubmedIDs.get(i), basePubmedUrl, pubmedIDs.get(i), true);
				jb.setFont(new Font(jb.getFont().getName(), Font.PLAIN, 22));
				String constraintText= "gapy 30px, aligny top, ";
				// Set some spacing constraints for the pubmed links
				if (i == 0 && pubmedIDs.size() > 1) {
					constraintText += "split"; // if there are more than 1 IDs, stick them together visually
				} else if (i == pubmedIDs.size() - 1) {
					constraintText += "wrap";
				}
				reportJP.add(jb, constraintText);
			}
			
			/* Add a subpanel of tabs. */
			final JTabbedPane subtabs= ViewUtil.getMSTabedPane();
			// span the entire panel width minus 50 pixels to make up for the gapx inset
			subtabs.setPreferredSize(new Dimension(
				reportPane.getSize().width - 50, subtabs.getPreferredSize().height));
			
			/* Subpanel describing the individual's haplotypes/markers for this individual. */
			// phased genotype status
			JPanel geneSummaryJP= new JPanel();
			geneSummaryJP.setLayout(new MigLayout("gapx 20px"));
			String phasedTextAddition= "";
			if (!pg.isPhased())
				phasedTextAddition= "NOT ";
			geneSummaryJP.add(createLabel("Genotypes are " + phasedTextAddition +
				"phased.", true, 20), "alignx center, span");
			
			// haplotype details - only add if the haplotypes exist (if genotypes
			// are phased).
			if (pg.isPhased()) {
				geneSummaryJP.add(createLabel("Haplotype #1", true, 16));
				geneSummaryJP.add(createLabel(pg.getMaternalHaplotype(), false, 16), "gapy 20px, wrap");
				geneSummaryJP.add(createLabel("Haplotype #2", true, 16));
				geneSummaryJP.add(createLabel(pg.getPaternalHaplotype(), false, 16), "wrap");
				geneSummaryJP.add(createLabel("Haplotype #1 activity", true, 16));
				geneSummaryJP.add(createLabel(pg.getMaternalActivity(), false, 16), "wrap");
				geneSummaryJP.add(createLabel("Haplotype #2 activity", true, 16));
				geneSummaryJP.add(createLabel(pg.getPaternalActivity(), false, 16), "wrap");
			}
			geneSummaryJP.revalidate();
			subtabs.addTab(pg.getGene() + " summary", geneSummaryJP);
			
			/* Subpanel displaying all detected variants. */
			JPanel hapDetails= new JPanel();
			hapDetails.setLayout(new MigLayout("gapx 30px"));
			String column1Heading= "Haplotype #1";
			String column2Heading= "Haplotype #2";
			if (!pg.isPhased()) {
				// display unphased message again
				hapDetails.add(createLabel("Genotypes are " + phasedTextAddition +
				"phased.", true, 20), "alignx center, span");
				column1Heading= "Unphased genotype #1";
				column2Heading= "Unphased genotype #2";
			}			
			hapDetails.add(createLabel("Marker ID", true, 16));
			hapDetails.add(createLabel(column1Heading, true, 16));
			hapDetails.add(createLabel(column2Heading, true, 16));
			hapDetails.add(createLabel("Observed/Inferred genotype", true, 16), "wrap");
					
			// Haplotype 1 and 2 have the same rsIDs
			Map<String, PGXGenotype> hap1Genotypes= pg.getMaternalGenotypes();
			Map<String, PGXGenotype> hap2Genotypes= pg.getPaternalGenotypes();
			
			List<String> allRsIDs= new ArrayList<String>(hap1Genotypes.keySet());
			Collections.sort(allRsIDs); // sort the list of markers
			for (String rsID : allRsIDs) {
				PGXGenotype genotype1= hap1Genotypes.get(rsID);
				PGXGenotype genotype2= hap2Genotypes.get(rsID);
				
				String genotypeStatus= "observed";
				Color fontColour= AppColors.Salem;
				if (genotype1.getInferredStatus() || genotype2.getInferredStatus()) {
					genotypeStatus= "inferred as reference";
					fontColour= DEFAULT_LABEL_COLOUR;
				}
				
				hapDetails.add(createLabel(rsID, false, 16, hapDetails.getBackground(), fontColour));
				hapDetails.add(createLabel(genotype1.getGenotype(), false, 16, hapDetails.getBackground(), fontColour));
				hapDetails.add(createLabel(genotype2.getGenotype(), false, 16, hapDetails.getBackground(), fontColour));
				hapDetails.add(createLabel(genotypeStatus, false, 16, hapDetails.getBackground(), fontColour), "wrap");
			}
			subtabs.addTab("Haplotype details", hapDetails);
			
			/* Subpanel describing all the markers tested for this gene. */
			JPanel testedMarkersJP= new JPanel();
			testedMarkersJP.setLayout(new MigLayout("gapy 0px, gapx 30px")); // don't use fillx property here
			try {
				makeJPanelRow(testedMarkersJP, Arrays.asList(new String[]
					{"Marker ID", "Chromosome", "Position", "Reference nucleotide",
					"Alternate nucleotide"}), false);
				for (PGXMarker pgxm : PGXDBFunctions.getMarkerInfo(pg.getGene())) {
					makeJPanelRow(testedMarkersJP, Arrays.asList(new String[]
						{pgxm.markerID,	pgxm.chromosome, pgxm.position, pgxm.ref,
						pgxm.alt}), true);
				}
			} catch (Exception e) {
				errorDialog(e.getMessage());
				e.printStackTrace();
			}
			subtabs.addTab("Tested markers for " + pg.getGene(), testedMarkersJP); 
			
			/* Subpanel showing all the novel Variants for this gene. */
			JPanel novelVariantsJP= getNovelVariantsPanel(pg);
			subtabs.addTab("Novel variants", novelVariantsJP);
			
			/* Add subtabs to the main report panel. */
			reportJP.add(subtabs, "gapy 100px, span"); // need span here for column formatting of diplotype and metabolizer fields
			
			/* Add the main report panel for this gene to the tabs. */
			tabs.addTab(pg.getGene(), reportJP);
		}
		
		reportPane.setViewportView(tabs);
	}
	
	
	private JPanel getNovelVariantsPanel(PGXGene pg) {
		int FONT_SIZE= 14;
		
		/* Subpanel showing all the novel Variants for this gene. */
		JPanel novelVariantsJP= new JPanel();
		novelVariantsJP.setLayout(new MigLayout("fillx"));
		
		/* Get the names of the allele frequency columns, if not done already. */
		if (afColumnNames == null) {
			afColumnNames= new LinkedList<String>();
			
			TableSchema ts= ProjectController.getInstance().getCurrentVariantTableSchema();
			AnnotationManagerAdapter am= MedSavantClient.AnnotationManagerAdapter;
			Map<String, Set<CustomField>> fieldMap= null;
			try {
				fieldMap= 
					am.getAnnotationFieldsByTag(LoginController.getInstance().getSessionID(), true);
			} catch (Exception e) {
				errorDialog(e.getMessage());
				e.printStackTrace();
			}
			Set<CustomField> columnNames= fieldMap.get(CustomField.ALLELE_FREQUENCY_TAG);
			
			for (CustomField cf : columnNames) {
				//DbColumn afColumn= ts.getDBColumn(cf.getColumnName());
				afColumnNames.add(cf.getAlias());
			}
		}
		
		/* Short message describing how these variants are selected. */
		novelVariantsJP.add(createLabel(
			"Novel variants are non-synonymous mutations with allele frequencies " +
			"<= 0.05 (or N/A) across all available AF databases"
			, false, FONT_SIZE), "alignx center, span");
		
		/* Create the table header. */
		if (pg.getNovelVariants().size() > 0) {
			novelVariantsJP.add(createLabel("Chrom", true, FONT_SIZE));
			novelVariantsJP.add(createLabel("Position", true, FONT_SIZE));
			novelVariantsJP.add(createLabel("Effect", true, FONT_SIZE));
			novelVariantsJP.add(createLabel("Zygosity", true, FONT_SIZE));
			for (String afName : afColumnNames) {
				novelVariantsJP.add(createLabel(afName, true, FONT_SIZE));
			}
			novelVariantsJP.add(createLabel("dbSNP", true, FONT_SIZE), "wrap");
		} else { // no novel variants
			novelVariantsJP.add(createLabel("No rare novel variants detected.", true, 18), "alignx center");
		}
		
		/* Output the variant rows. */
		for (Variant var : pg.getNovelVariants()) {
			novelVariantsJP.add(createLabel(var.getChromosome(), false, FONT_SIZE));
			novelVariantsJP.add(createLabel(Long.toString(var.getStart()), false, FONT_SIZE));
			novelVariantsJP.add(createLabel(var.getMutationType(), false, FONT_SIZE));
			novelVariantsJP.add(createLabel(var.getZygosity(), false, FONT_SIZE));
			for (String afName : afColumnNames) {
				BigDecimal afValue= (BigDecimal) var.getColumn(afName);
				String afValueString= "N/A";
				if (afValue != null) {
					afValueString= afValue.toString();
				}
				novelVariantsJP.add(createLabel(afValueString, false, FONT_SIZE));
			}
			novelVariantsJP.add(createLabel(
				(String) var.getColumn(DBAnnotationColumns.DBSNP_TEXT), false, FONT_SIZE), "wrap");
		}
		
		return novelVariantsJP;
	}
	
	
	/**
	 * Create an error dialog and output the error to the log.
	 * @param errorMessage the error message to display.
	 */
	private void errorDialog(String errorMessage) {
		DialogUtils.displayError("Oops!", errorMessage);
		log.error("[" + this.getClass().getSimpleName() + "]: " + errorMessage);
	}
	
	
	/**
	 * Add a row of JLabels with the text from the input list.
	 * @param container the parent container where the JLabels will be added
	 * @param textList the list of text
	 * @param markerIDFirst true if the first element of textList is an dbSNP ID (rsID), false otherwise
	 * @param bg the background colour for this row
	 * @precondition the container is using MigLayout layout; method doesn't check
	 */
	private void makeJPanelRow(JComponent container, List<String> textList, 
		boolean markerIDFirst, Color bg) {
		
		int fontSize= 16;
		boolean isBold= false;
		
		/* Header row of the table is in bold. */
		if (!markerIDFirst)
			isBold= true;
		
		for (int i= 0; i != textList.size(); ++i) {
			if (markerIDFirst && i == 0) {
				JButton jb= getURLButton(textList.get(i), baseDBSNPUrl, textList.get(i), false);
				jb.setFont(new Font(jb.getFont().getName(), Font.PLAIN, fontSize));
				jb.setBackground(bg);
				container.add(jb);
			} else if (i < textList.size() - 1) {
				container.add(createLabel(textList.get(i), isBold, fontSize, bg, DEFAULT_LABEL_COLOUR));
			} else { // wrap at the end of this JPanel row
				container.add(createLabel(textList.get(i), isBold, fontSize, bg, DEFAULT_LABEL_COLOUR), "wrap");
			}
		}
	}
	
	
	/**
	 * Add a row of JLabels with the text from the input list with the background colour of
	 * the container.
	 */
	private void makeJPanelRow(JComponent container, List<String> textList, boolean markerIDFirst) {
		makeJPanelRow(container, textList, markerIDFirst, container.getBackground());
	}
	
	
	/**
	 * Create a button that opens a URL in a web browser when clicked.
	 * @param buttonText Button text
	 * @param baseURL URL linked from the button
	 * @param appendToURL Append text to the URL
	 * @param doEncode encode the text using the UTF-8
	 * @return a JideButton that opens the URL in a web browser
	 */
	private JideButton getURLButton(String buttonText, final String baseURL, 
		final String appendToURL, final boolean doEncode) {
		
		final String URL_CHARSET = "UTF-8";
		
		JideButton urlButton= new JideButton(buttonText);
		urlButton.setButtonStyle(ButtonStyle.HYPERLINK_STYLE);
		urlButton.setForeground(Color.BLUE);
		urlButton.setToolTipText("Lookup " + buttonText + " on the web");
		urlButton.addActionListener(new ActionListener() 
		{
			@Override
			public void actionPerformed(ActionEvent ae) {
				try {
					URL url;
					if (doEncode)
						url = new URL(baseURL + URLEncoder.encode(appendToURL, URL_CHARSET));
					else
						url = new URL(baseURL + appendToURL);
					
					java.awt.Desktop.getDesktop().browse(url.toURI());
				} catch (Exception ex) {
					ClientMiscUtils.reportError("Problem launching website: %s", ex);
				}
			}
		});
		
		return urlButton;
	}
	
	
	/**
	 * Action to perform when reference genotype checkbox is toggled.
	 * @return the ActionListener for this checkbox
	 */
	private ActionListener toggleReferenceAction() {
		// create an anonymous class
		ActionListener outputAL= new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent ae) {	
				// only restart an analysis if a patient has already been selected
				if (currentDNAID != null) {				
					/* Perform a new pharmacogenomic analysis for this DNA ID
					 * with the currently selected options. */
					analyzePatient();
				}
			}
		};
		
		return outputAL;
	}
	
	
	/**
	 * Create a custom text field that looks like a JLabel and isn't editable.
	 * @param text the field text
	 * @param isBold true if bold; false otherwise
	 * @param size font size
	 * @param background the background color (default is white)
	 * @return the text field object
	 */
	private JTextField createLabel(String text, boolean isBold, int size, Color background, Color foreground) {
		JTextField output= new JTextField(text);
		int fontStyle= Font.PLAIN;
		if (isBold)
			fontStyle= Font.BOLD;
		output.setFont(new Font(output.getFont().getName(), fontStyle, size));
		output.setEditable(false);
		output.setBackground(background); // instead of the default white
		output.setForeground(foreground);
		output.setBorder(BorderFactory.createEmptyBorder()); // eliminate the border
		return output;
	}
	
	
	/**
	 * Create a custom text field using createLabel() with a default background colour.
	 */
	private JTextField createLabel(String text, boolean isBold, int size) {
		return createLabel(text, isBold, size, this.reportInitJP.getBackground(), DEFAULT_LABEL_COLOUR);
	}
}
