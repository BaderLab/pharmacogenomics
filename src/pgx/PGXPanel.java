package pgx;

import com.itextpdf.text.DocumentException;
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
import java.rmi.RemoteException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.ScrollPaneConstants;
import javax.swing.filechooser.FileNameExtensionFilter;
import net.miginfocom.swing.MigLayout;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.ut.biolab.medsavant.MedSavantClient;
import org.ut.biolab.medsavant.client.project.ProjectController;
import org.ut.biolab.medsavant.client.util.ClientMiscUtils;
import org.ut.biolab.medsavant.client.util.MedSavantWorker;
import org.ut.biolab.medsavant.client.view.MedSavantFrame;
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
import org.ut.biolab.medsavant.shared.model.SessionExpiredException;
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
	private static final Color DEFAULT_SUBHEADING_DARK_BLUE= new Color(26, 13, 171);
	private static final String CANCEL_TEXT= "Cancel";
	private static final String REFRESH_TEXT= "Refresh";
	
	private static List<String> afColumnNames;
	
	private CountDownLatch cancelLatch= new CountDownLatch(1);
	
	/* Patient information. */
	private String currentHospitalID;
	private String currentDNAID;
	private PGXAnalysis currentPGXAnalysis;
	private MedSavantWorker pgxAnalysisThread;
	
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
	private JButton cancelOrRefresh;
	private JButton exportToPDFButton;
	private JTabbedPane tabs;
	
	
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
					currentHospitalID= patientSelector.getHospitalIDsOfSelectedIndividuals().iterator().next();
					String newDNAID= patientSelector.getDNAIDsOfSelectedIndividuals().iterator().next();
					
					if (newDNAID != null) {
						currentDNAID= newDNAID;
						choosePatientButton.setText(currentHospitalID);						
					} else { // can't find this individual's DNA ID - may be a DB error
						errorDialog("Can't find a DNA ID for " + currentHospitalID);
					}
				}
				
				/* Prevent further patient selection while an analysis thread is
				 * running. */
				choosePatientButton.setEnabled(false);
				
				/* Perform a new pharmacogenomic analysis for this DNA ID. */
				analyzePatient();
			}
		};
		
		return outputAL;
	}
	
	
	/**
	 * Action to perform when cancel/refresh button is clicked.
	 * @return the ActionListener for this button
	 */
	private ActionListener cancelOrRefreshAction() {
		// create an anonymous class
		ActionListener outputAL= new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent ae) {
				// Check if PGx analysis thread is running or not; could also check
				// what the button text is, but this is better.
				
				if (!pgxAnalysisThread.isDone()) { // cancel action
				
				/* If the cancel button is pressed immediately after the analysis
				 * is started, the analysis is null, and we need to wait for it to
				 * be initialized before cancelling it. This is done via a CountDownLatch.
				 * However, the latch is locking, so the swing (UI) thread will
				 * wait until the analysis is initialized before executing the
				 * cancel() method. Instead, we'll submit the cancellation request
				 * via a separate cancellation thread, which will wait for the 
				 * analysis to be initialized. */
					MedSavantWorker cancellationThread= new MedSavantWorker<Object>(PGXPanel.class.getCanonicalName()) {			
						@Override
						protected Object doInBackground() throws SQLException, RemoteException,
							SessionExpiredException, PGXException {

								// Intermediate UI updates to show that analysis is being cancelled
								status.setText("Cancelling...");
								cancelOrRefresh.setEnabled(false);
							
								// Cancel the analysis thread
								pgxAnalysisThread.cancel(true);

								// Cancel the analysis, once it's been initialized - check
								// the CountDownLatch first.
								try {
									cancelLatch.await();
									currentPGXAnalysis.cancel();
								} catch (InterruptedException ie) {
									errorDialog(ie.getMessage());
									ie.printStackTrace();
								}
								
							return null;
						}

						@Override protected void showSuccess(Object t) {
							// UI cancellation details
							cancelReportPanel();
							cancelOrRefresh.setEnabled(true);
							cancelOrRefresh.setText(REFRESH_TEXT);
							status.setText("Analysis cancelled.");
							statusWheel.setVisible(false);
							choosePatientButton.setEnabled(true);
						}
					};
		
					// Execute thread
					cancellationThread.execute();
					
				} else { // refresh action
					analyzePatient();
				}
			}
		};
		
		return outputAL;
	}
	
	
	/**
	 * Action to perform when export to PDF button is clicked.
	 * @return the ActionListener for this button
	 */
	private ActionListener exportToPDFAction() {
		// create an anonymous class
		ActionListener outputAL= new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent ae) {
				status.setText("Exporting PDF...");
				statusWheel.setVisible(true);
				
				// Get the user's desired filename
				JFileChooser chooser = new JFileChooser();
				FileNameExtensionFilter filter = new FileNameExtensionFilter("PDF file", "pdf");
				chooser.setFileFilter(filter);
				final int chooserValue= chooser.showSaveDialog(MedSavantFrame.getInstance());
				final String chooserFileName= chooser.getSelectedFile().getPath() + ".pdf";
				
				MedSavantWorker pdfThread= new MedSavantWorker<Object>(PGXPanel.class.getCanonicalName()) {			
					@Override
					protected Object doInBackground() {						
						if(chooserValue == JFileChooser.APPROVE_OPTION) {
							
							// Create and output the PDF report
							PGXPDFExporter pdfExporter= new PGXPDFExporter(tabs, chooserFileName);
							try {
								pdfExporter.createMultipagePDF();
							} catch (DocumentException de) {
								errorDialog("Error producing PDF report: " + de.getMessage());
								de.printStackTrace();
							}
						}
						
						return null;
					}

					@Override protected void showSuccess(Object t) {
						status.setText("PDF export complete.");
						statusWheel.setVisible(false);
					}
				};

				// Execute thread
				pdfThread.execute();
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
		
		// Cancel or Refresh button
		cancelOrRefresh= new JButton();
		cancelOrRefresh.setVisible(false);
		cancelOrRefresh.addActionListener(cancelOrRefreshAction());
		
		// Export to PDF button
		exportToPDFButton= new JButton("Export to PDF");
		exportToPDFButton.setVisible(false);
		exportToPDFButton.addActionListener(exportToPDFAction());
		
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
		patientSideJP.add(cancelOrRefresh, "alignx center, wrap");
		patientSideJP.add(exportToPDFButton, "alignx center, gapy 40px, wrap");
		
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
		reportInitJP.setBackground(Color.WHITE);
		reportInitJP.add(reportStartLabel);
				
		reportPane= new JScrollPane();
		reportPane.setBorder(BorderFactory.createEmptyBorder());
		//reportPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		reportPane.setViewportView(reportInitJP);		
		
		/* NOTE: reportPane size preferences are set upon appView component 
		 * shown or resizing actions. */
	}
	
	
	/**
	 * Report panel to display when a new analysis is being run.
	 */
	private void analysisRunningReportPanel() {
		reportStartLabel.setText("Obtaining pharmacogenomic report for " + 
			this.currentHospitalID + "...");
		reportPane.setViewportView(reportInitJP);
	}
	
	
	/**
	 * Report panel to display when analysis has been cancelled.
	 */
	private void cancelReportPanel() {
		reportStartLabel.setText("Analysis cancelled.");
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
		
		// Update cancel button
		cancelOrRefresh.setText(CANCEL_TEXT);
		cancelOrRefresh.setVisible(true);
		
		exportToPDFButton.setVisible(false);
		
		// Clear the report panel to avoid confusing this patient for the previous one
		analysisRunningReportPanel();
		
		/* Background task. */
		pgxAnalysisThread= new MedSavantWorker<Object>(PGXPanel.class.getCanonicalName()) {			
			@Override
			protected Object doInBackground() throws SQLException, RemoteException,
				SessionExpiredException, PGXException {
				
				/* Create and perform a new analysis. Uses a CountDownLatch to 
				 * ensure that currentPGXAnalysis is initilized before I can
				 * do anything with it (for example, cancel it). */
				try {
					currentPGXAnalysis= new PGXAnalysis(currentDNAID, assumeRefCheckBox.isSelected());
					cancelLatch.countDown();
				} catch (Exception e) {
					errorDialog(e.getMessage());
					e.printStackTrace();
				}

				return null;
			}

			@Override
			protected void showSuccess(Object t) {
				status.setText("Analysis complete.");
				statusWheel.setVisible(false);
				choosePatientButton.setEnabled(true);
				cancelOrRefresh.setText(REFRESH_TEXT);
				exportToPDFButton.setVisible(true);
				
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
		tabs= ViewUtil.getMSTabedPane();
		
		/* Create a summary tab. */
		JPanel summary= new JPanel();
		summary.setBackground(Color.WHITE);
		summary.setLayout(new MigLayout("gapx 30px"));
		summary.add(createLabel("Patient Hospital ID", true, 20));
		summary.add(createLabel(this.currentHospitalID, false, 20), "wrap");
		summary.add(createLabel("Patient DNA ID", true, 20));
		summary.add(createLabel(this.currentDNAID, false, 20), "wrap");
		summary.add(createLabel("Gene", true, 20), "gapy 20px");
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
			reportJP.setBackground(Color.WHITE);
			reportJP.setLayout(new MigLayout("gapx 30px"));
			
			reportJP.add(createLabel("Gene", true, 22));
			reportJP.add(createLabel(pg.getGene(), false, 22), "wrap");
			
			reportJP.add(createLabel("Diplotype", true, 22));
			reportJP.add(createLabel(pg.getDiplotype(), false, 22), "wrap");
			
			reportJP.add(createLabel("Therapeutic class", true, 22));
			reportJP.add(createLabel(pg.getMetabolizerClass(), false, 22), "gapafter 30px, wrap");
			
			/* Add pubmed links. */
			reportJP.add(createLabel("Publications", true, 22), "aligny top");
			List<String> pubmedIDs= PGXDBFunctions.getPubMedIDs(pg.getGene());
			for (int i= 0; i != pubmedIDs.size(); ++i) {
				String pubmedButtonText= "Guidelines";
				if (pubmedIDs.size() > 1) {
					pubmedButtonText += " #" + (i + 1);
				}
				JButton jb= getURLButton(pubmedButtonText, basePubmedUrl, pubmedIDs.get(i), true);
				jb.setFont(new Font(jb.getFont().getName(), Font.PLAIN, 22));
				String constraintText= "aligny top, ";
				// Set some spacing constraints for the pubmed links
				if (i == 0 && pubmedIDs.size() > 1) {
					constraintText += "split"; // if there are more than 1 IDs, stick them together visually
				} else if (i == pubmedIDs.size() - 1) {
					constraintText += "wrap";
				}
				reportJP.add(jb, constraintText);
			}
			
			/* Add genotype phase status. */
			String phasedTextAddition= "";
			if (!pg.isPhased())
				phasedTextAddition= "NOT ";
			reportJP.add(createLabel("Genotypes are " + phasedTextAddition + "phased.",
				false, 22), "span");
			
			// No longer implementing the subpanels
			/* Add a subpanel of tabs. */
			/*
			final JTabbedPane subtabs= ViewUtil.getMSTabedPane();
			// span the entire panel width minus 50 pixels to make up for the gapx inset
			subtabs.setPreferredSize(new Dimension(
				reportPane.getSize().width - 50, subtabs.getPreferredSize().height));
			*/
			
			/* Subpanel describing the individual's haplotypes/markers for this individual. */
			JPanel geneSummaryJP= new JPanel();
			geneSummaryJP.setBackground(Color.WHITE);
			geneSummaryJP.setLayout(new MigLayout("gapx 20px"));
			addHaplotypes(geneSummaryJP, pg);
			geneSummaryJP.revalidate();
			//subtabs.addTab(pg.getGene() + " summary", geneSummaryJP);
			
			/* Subpanel displaying all detected variants. */
			JPanel hapDetailsJP= new JPanel();
			hapDetailsJP.setBackground(Color.WHITE);
			hapDetailsJP.setLayout(new MigLayout("gapx 15px"));
			addHaplotypeDetails(hapDetailsJP, pg);
			//subtabs.addTab("Haplotype details", hapDetailsJP);
			
			/* Subpanel describing all the markers tested for this gene. */
			JPanel testedMarkersJP= new JPanel();
			testedMarkersJP.setBackground(Color.WHITE);
			testedMarkersJP.setLayout(new MigLayout("gapy 0px, gapx 30px")); // don't use fillx property here
			addTestedMarkers(testedMarkersJP, pg);
			//subtabs.addTab("Tested markers for " + pg.getGene(), testedMarkersJP); 
			
			/* Subpanel showing all the novel Variants for this gene. */
			JPanel novelVariantsJP= getNovelVariantsPanel(pg);
			//subtabs.addTab("Novel variants", novelVariantsJP);
			
			/* Add subpanels to the main report panel. */
			reportJP.add(createLabel("Haplotype summary", true, 22,
				reportJP.getBackground(), DEFAULT_SUBHEADING_DARK_BLUE), "gapy 40px, span");
			reportJP.add(geneSummaryJP, "span");
			reportJP.add(createLabel("Genotype summary ", true, 22,
				reportJP.getBackground(), DEFAULT_SUBHEADING_DARK_BLUE), "span");
			reportJP.add(hapDetailsJP, "span");
			reportJP.add(createLabel("Novel variants (not part of guidelines)",
				true, 22, reportJP.getBackground(), DEFAULT_SUBHEADING_DARK_BLUE), "span");
			reportJP.add(novelVariantsJP, "span");			
			
			/* Add subtabs to the main report panel. */
			//reportJP.add(subtabs, "gapy 100px, span"); // need span here for column formatting of diplotype and metabolizer fields			
			
			/* Add the main report panel for this gene to the tabs. */
			tabs.addTab(pg.getGene(), reportJP);
		}
		
		reportPane.setViewportView(tabs);
	}
	
	
	/**
	 * Add haplotypes to specified JPanel.
	 * @param jp JPanel where haplotypes are being appended
	 * @param pg the PGXGene object
	 */
	private void addHaplotypes(JPanel jp, PGXGene pg) {
		// haplotype details - only add if the haplotypes exist (if genotypes
		// are phased).
		if (pg.isPhased()) {
			jp.add(createLabel("Haplotype #1", true, 16));
			jp.add(createLabel(pg.getMaternalHaplotype(), false, 16), "wrap");
			jp.add(createLabel("Haplotype #2", true, 16));
			jp.add(createLabel(pg.getPaternalHaplotype(), false, 16), "wrap");
			jp.add(createLabel("Haplotype #1 activity", true, 16));
			jp.add(createLabel(pg.getMaternalActivity(), false, 16), "wrap");
			jp.add(createLabel("Haplotype #2 activity", true, 16));
			jp.add(createLabel(pg.getPaternalActivity(), false, 16), "wrap");
		}
	}
	
	
	/**
	 * Add haplotype details to specified JPanel.
	 * @param jp JPanel where haplotype details (genotypes) are being appended
	 * @param pg the PGXGene object
	 */
	private void addHaplotypeDetails(JPanel jp, PGXGene pg) {
		int FONT_SIZE= 16;
		
		String column1Heading= "Haplotype #1";
		String column2Heading= "Haplotype #2";
		if (!pg.isPhased()) {
			column1Heading= "Unphased genotype #1";
			column2Heading= "Unphased genotype #2";
		}			
		jp.add(createLabel("Marker ID", true, FONT_SIZE));
		jp.add(createLabel(column1Heading, true, FONT_SIZE));
		jp.add(createLabel(column2Heading, true, FONT_SIZE));
		jp.add(createLabel("Observed/Inferred genotype", true, FONT_SIZE));
		jp.add(createLabel("Chr", true, FONT_SIZE));
		jp.add(createLabel("Position", true, FONT_SIZE));
		jp.add(createLabel("Ref", true, FONT_SIZE));
		jp.add(createLabel("Alt", true, FONT_SIZE), "wrap");
		
		Map<String, PGXMarker> markerLookup= new HashMap<String, PGXMarker>();
		try {
			markerLookup= PGXDBFunctions.getMarkerInfoMap(pg.getGene());
		} catch (Exception e) {
			errorDialog(e.getMessage());
			e.printStackTrace();
		}
		
		Map<String, PGXGenotype> hap1Genotypes= pg.getMaternalGenotypes();
		Map<String, PGXGenotype> hap2Genotypes= pg.getPaternalGenotypes();
		// Haplotype 1 and 2 have the same marker IDs
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
			
			JButton markerURLButton= getURLButton(rsID, baseDBSNPUrl, rsID, false);
			markerURLButton.setFont(new Font(markerURLButton.getFont().getName(), Font.PLAIN, FONT_SIZE));
			markerURLButton.setForeground(fontColour);
			jp.add(markerURLButton);
			jp.add(createLabel(genotype1.getGenotype(), false, FONT_SIZE, jp.getBackground(), fontColour));
			jp.add(createLabel(genotype2.getGenotype(), false, FONT_SIZE, jp.getBackground(), fontColour));
			jp.add(createLabel(genotypeStatus, false, FONT_SIZE, jp.getBackground(), fontColour));
			// NOTE: All markers MUST be in the markerlookup map, so not checking
			// for rsID presence as a key.
			jp.add(createLabel(markerLookup.get(rsID).chromosome, false, FONT_SIZE, jp.getBackground(), fontColour));
			jp.add(createLabel(markerLookup.get(rsID).position, false, FONT_SIZE, jp.getBackground(), fontColour));
			jp.add(createLabel(markerLookup.get(rsID).ref, false, FONT_SIZE, jp.getBackground(), fontColour));
			jp.add(createLabel(markerLookup.get(rsID).alt, false, FONT_SIZE, jp.getBackground(), fontColour), "wrap");
		}
	}
	
	
	/**
	 * Add tested marker details to specified JPanel.
	 * @param jp JPanel where tested marker details (genotypes) are being appended
	 * @param pg the PGXGene object
	 */
	private void addTestedMarkers(JPanel jp, PGXGene pg) {
		try {
			makeJPanelRow(jp, Arrays.asList(new String[]
				{"Marker ID", "Chromosome", "Position", "Reference nucleotide",
				"Alternate nucleotide"}), false);
			for (PGXMarker pgxm : PGXDBFunctions.getMarkerInfo(pg.getGene())) {
				makeJPanelRow(jp, Arrays.asList(new String[]
					{pgxm.markerID,	pgxm.chromosome, pgxm.position, pgxm.ref,
					pgxm.alt}), true);
			}
		} catch (Exception e) {
			errorDialog(e.getMessage());
			e.printStackTrace();
		}
	}
	
	
	private JPanel getNovelVariantsPanel(PGXGene pg) {
		int FONT_SIZE= 14;
		
		/* Subpanel showing all the novel Variants for this gene. */
		JPanel novelVariantsJP= new JPanel();
		novelVariantsJP.setBackground(Color.WHITE);
		novelVariantsJP.setLayout(new MigLayout("fillx, gapx 15px"));
		
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
			
			String rsID= (String) var.getColumn(DBAnnotationColumns.DBSNP_TEXT);
			JButton markerURLButton= getURLButton(rsID, baseDBSNPUrl, rsID, false);
			markerURLButton.setFont(new Font(markerURLButton.getFont().getName(), Font.PLAIN, FONT_SIZE));
			novelVariantsJP.add(markerURLButton, "wrap");
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
