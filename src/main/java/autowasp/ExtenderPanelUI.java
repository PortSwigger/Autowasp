/*
 * Copyright (c) 2021 Government Technology Agency
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package autowasp;

import burp.IMessageEditor;

import java.awt.*;

import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.HyperlinkEvent;
import java.io.IOException;
import java.io.File;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class ExtenderPanelUI implements Runnable{

	private final Autowasp extender;
    private JSplitPane gtScannerSplitPane;
    public IMessageEditor requestViewer;
    public IMessageEditor responseViewer;
    private JFileChooser destDirChooser;  //This file chooser is now used to choose the directories for both the scan report and the OWASP checklist files
    public JLabel scanStatusLabel;
    private JTextField hostField;

    // Checklist UI
    public JTextPane summaryTextPane;
    public JEditorPane howToTestTextPane;
    public JTextPane referencesTextPane;
    JButton enableScanningButton;
    private JButton generateWebChecklistButton;
    private Thread thread;
    public final AtomicBoolean running = new AtomicBoolean(false);
    public JButton cancelFetchButton;
    private JButton saveLocalCopyButton;
    private JButton generateLocalChecklistButton;
    private JButton generateExcelReportButton;
    private JFileChooser fileChooser;
    private File checklistDestDir;
    private final boolean selfUpdateLocal = false;
    
    // Loggers UI
	private JTabbedPane bottomModulesTabs;
    public JTextPane penTesterCommentBox;
    public JTextPane evidenceBox;
    private JButton loadProjectButton;
    public JButton deleteEntryButton;
    public JButton deleteInstanceButton;

    ExtenderPanelUI(Autowasp extender) {
		this.extender = extender;
	}

	@Override
	public void run() {
		 // scanner split pane
        gtScannerSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        bottomModulesTabs = new JTabbedPane();
        
        setupTopPanel();
        setupCheckListPanel();
        setupLoggerPanel();
        
        // Consolidate all modular tabs and set to the scanner bottom pane
        gtScannerSplitPane.setRightComponent(bottomModulesTabs);    
        extender.gtScannerSplitPane = gtScannerSplitPane;		
	}

	// This method setup the top panel view of Autowasp
	private void setupTopPanel() {
        //JPanel topPanel = new JPanel(new BorderLayout(10, 10));
        JPanel topPanel = new JPanel(new GridLayout(4, 0));
        topPanel.setBorder(new EmptyBorder(0, 0, 10, 0));

        JPanel setupPanel = new JPanel(new FlowLayout(FlowLayout.LEADING, 10, 10));
        setupPanel.add(new JLabel("Target:", SwingConstants.LEFT), BorderLayout.LINE_START);
        hostField = new JTextField("", 15);
        JButton addToScopeButton = new JButton("Add Target to Scope");
        addToScopeButton.addActionListener(e -> {
            // Filter the URL and add to scope
            String url = hostField.getText();
            String sHost;
            String host;
            String sHost2;
            String host2;
            if(url.length() == 0) {
                return;
            }
            if(!url.contains("://")) {
                sHost = "https://" + url;
                host = "http://" + url;
                if (!url.contains("www.")){
                    sHost2 = "https://www." + url;
                    host2 = "http://www." + url;
                }
                else{
                    sHost2 = sHost;
                    host2 = host;
                }
            }
            else {
                if (!url.contains("https://")){
                    host = url;
                    String tmp = url.substring(7);
                    sHost = "https://" + tmp;
                    if (!url.contains("www.")){
                        sHost2 = "https://www." + tmp;
                        host2 = "http://www." + tmp;
                    }
                    else{
                        sHost2 = sHost;
                        host2 = host;
                    }
                }
                else {
                    sHost = url;
                    String tmp = url.substring(8);
                    host = "http://" + tmp;
                    if (!url.contains("www.")){
                        sHost2 = "https://www." + tmp;
                        host2 = "http://www." + tmp;
                    }
                    else{
                        sHost2 = sHost;
                        host2 = host;
                    }
                }
            }
            try {
                extender.callbacks.includeInScope(new URL(sHost));
                extender.callbacks.includeInScope(new URL(host));
                extender.callbacks.includeInScope(new URL(sHost2));
                extender.callbacks.includeInScope(new URL(host2));
                scanStatusLabel.setText("Target added to scope: " + url);
                if (!enableScanningButton.isEnabled()){
                    // Automatically extract scan related to the newly added domain
                    extender.scannerLogic.extractExistingScan();
                }
                this.hostField.setText("");
            } catch (MalformedURLException e1) {
                extender.stdout.println("Exception occurred at setupTopPanel");
}
        });
        enableScanningButton = new JButton("Enable Burp Scanner logging");
        enableScanningButton.addActionListener(e -> {
            extender.scannerLogic.extractExistingScan();
            enableScanningButton.setEnabled(false);
            scanStatusLabel.setText("Extracted Scanner Logs. Passive Scanner logging enabled");
            extender.callbacks.issueAlert("Extracted Scanner Logs. Passive Scanner logging enabled");
        });

        // Status bar
        JPanel scanStatusPanel = new JPanel(new FlowLayout(FlowLayout.LEADING, 10, 10));
        scanStatusPanel.add(new JLabel("Status: ", SwingConstants.LEFT));
        scanStatusLabel = new JLabel("Ready to scan", SwingConstants.LEFT);

        //Checklist Panel: [Generate Checklist from Web] [Cancel Fetch] [Generate Checklist from Local Copy] [Save Local Copy]
        JPanel testingPanel = new JPanel(new FlowLayout(FlowLayout.LEADING, 10, 10));
        testingPanel.add(new JLabel("OWASP CheckList:", SwingConstants.LEFT), BorderLayout.LINE_START);
        fileChooser = new JFileChooser();
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);  //Allows user to load local copy of checklist from a txt file

        //On clicking, fetches checklist data from the web and displays it
        generateWebChecklistButton = new JButton("Fetch WSTG Checklist");
        generateWebChecklistButton.addActionListener(e -> {

            extender.callbacks.issueAlert("Fetching checklist now");
            scanStatusLabel.setText("Fetching checklist now");
            generateLocalChecklistButton.setEnabled(false);
            cancelFetchButton.setEnabled(true);
            generateWebChecklistButton.setEnabled(false);
            extender.checklistLog.clear(); //Clears the current checklistLog so there won't be duplicates even if the user clicks on fetch checklist multiple times
            running.set(true);
            Runnable runnable = () -> {
                int counter = 1;
                List<String> articleURLs;
                articleURLs  = extender.checklistLogic.scrapeArticleURLs();

                while(running.get() && counter < articleURLs.size()){
                    for (String url : articleURLs){
                        if (running.get()){
                            try{
                                Thread.sleep(500);
                                extender.checklistLogic.logNewChecklistEntry(url);
                                scanStatusLabel.setText("Fetching " + counter + " out of " + articleURLs.size());
                                counter++;
                            }
                            catch(InterruptedException e1){
                                Thread.currentThread().interrupt();
                            }
                        }
                        else{
                            // need to force stop the logging as new checklist entry here.
                            extender.checklistLog.clear(); //Clears the current checklistLog so there won't be duplicates even if the user clicks on fetch checklist multiple times
                            break;
                        }
                    }
                    Thread.currentThread().interrupt();
                    break;
                }
                cancelFetchButton.setEnabled(false);
                generateExcelReportButton.setEnabled(true);
                saveLocalCopyButton.setEnabled(true); // For updating local checklist during development phase
                scanStatusLabel.setText("Checklist successfully generated from the web");
                extender.callbacks.issueAlert("Checklist successfully generated from the web");
                extender.loggerTable.generateWSTGList();
                Thread.currentThread().interrupt();
            };
            thread = new Thread(runnable);
            thread.start();
        });

        //On clicking, cancel fetch checklist from web
        cancelFetchButton = new JButton("Cancel Fetch");
        cancelFetchButton.addActionListener(e -> {
            generateWebChecklistButton.setEnabled(true);
            generateLocalChecklistButton.setEnabled(true);
            generateExcelReportButton.setEnabled(false);
            saveLocalCopyButton.setEnabled(false);
            cancelFetchButton.setEnabled(false);
            running.set(false);
            Thread.currentThread().interrupt();
            extender.callbacks.issueAlert("Fetch checklist cancelled");
            scanStatusLabel.setText("Fetch checklist cancelled");
        });

        //On clicking, opens a file chooser for the user to upload a local copy of the checklist
        generateLocalChecklistButton = new JButton("Upload Local WSTG Checklist");
        generateLocalChecklistButton.addActionListener(e -> {
            generateLocalChecklistButton.setEnabled(false);
            generateWebChecklistButton.setEnabled(false);
            generateExcelReportButton.setEnabled(true);
            extender.checklistLogic.loadLocalCopy();
            scanStatusLabel.setText("Local checklist uploaded to Autowasp.");
        });
        
        //On clicking, opens a file chooser for the user to save a local copy of the checklist in a text file. Note that the contents of the file will be in HTML syntax
        saveLocalCopyButton = new JButton("Save a Local WSTG Checklist");
        saveLocalCopyButton.addActionListener(e -> {
            //Ensures that the user can only try to save a local copy after fetching data from the web/uploading from an existing local copy
            if (extender.checklistLog.size() == 0) {
                scanStatusLabel.setText("Please fetch the checklist from the web first");
                extender.callbacks.issueAlert("Please fetch the checklist from the web first");
            }
            else {
                final int userOption = destDirChooser.showSaveDialog(null);  //Returns the integer representation of the user's choice

                if (userOption == JFileChooser.APPROVE_OPTION) {
                    checklistDestDir = destDirChooser.getSelectedFile();
                    try {
                        extender.checklistLogic.saveLocalCopy(checklistDestDir.getAbsolutePath());
                    } catch (IOException ioException) {
                        extender.stdout.println("IOException at setupTopPanel - saveLocalCopyButton" );
                    }
                    extender.callbacks.issueAlert("Local checklist saved to "+ checklistDestDir.getAbsolutePath());
                    scanStatusLabel.setText("Local checklist saved to "+ checklistDestDir.getAbsolutePath());
                }
            }
        });
        
        // On clicking, opens a file chooser for the user to save a local copy of the checklist in an excel file
        generateExcelReportButton = new JButton("Generate Excel Report");
        generateExcelReportButton.addActionListener(e -> {
            // Ensures that the user can only try to generate an excel file after fetching data from the web/uploading an existing local copy
            if (extender.checklistLog.size() == 0) {
                scanStatusLabel.setText("Please fetch the checklist from the web first");
                extender.callbacks.issueAlert("Please fetch the checklist from the web first");
            }
            else {
                final int userOption = destDirChooser.showSaveDialog(null);  // Returns the integer representation of the user's choice

                if (userOption == JFileChooser.APPROVE_OPTION) {
                    checklistDestDir = destDirChooser.getSelectedFile();
                    extender.checklistLogic.saveToExcelFile(checklistDestDir.getAbsolutePath());
                }
            }
        });
                
        destDirChooser = new JFileChooser();
		destDirChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		destDirChooser.setApproveButtonText("Select");
		
		// On clicking, saving the project config to an XML file.
        JButton saveCurrentProjectButton = new JButton("Save Project");
		saveCurrentProjectButton.addActionListener(e -> {
            final int userOption = destDirChooser.showSaveDialog(null);  //Returns the integer representation of the user's choice

            if (userOption == JFileChooser.APPROVE_OPTION) {
                checklistDestDir = destDirChooser.getSelectedFile();
                try {
                    extender.projectWorkspace.saveFile(checklistDestDir.getAbsolutePath());
                } catch (IOException ioException) {
                    extender.stdout.println("IOException at setupTopPanel - saveCurrentProjectButton" );
                }
            }
        });
		
		loadProjectButton = new JButton("Load Project");
		loadProjectButton.addActionListener(e -> {

            final int userOption = fileChooser.showOpenDialog(null);  //Returns the integer representation of the user's choice

            if (userOption == JFileChooser.APPROVE_OPTION) {
                File chosenFile = fileChooser.getSelectedFile();

                if (!chosenFile.getAbsolutePath().contains("autowasp_project.ser")){ //Note that this contain string must be the same as the save file name though.
                    scanStatusLabel.setText("Error, this is not the correct project file");
                    extender.callbacks.issueAlert("Error, this is not the correct project file");
                }else{
                Runnable runnable = () -> {
                    extender.projectWorkspace.readFromFile(chosenFile.getAbsolutePath());
                    loadProjectButton.setEnabled(false);
                };
                Thread thread = new Thread(runnable);
                 thread.start();
                }
            }
        });

        //Misc Panel: [Delete Entries] [Delete Instance]  [Save Project] [Load Project]
        JPanel miscPanel = new JPanel(new FlowLayout(FlowLayout.LEADING, 10, 10));
        miscPanel.add(new JLabel("Misc Actions:", SwingConstants.LEFT), BorderLayout.LINE_START);

        deleteEntryButton = new JButton("Delete Entry");
        deleteEntryButton.addActionListener(e -> extender.loggerTable.deleteEntry());

        deleteInstanceButton = new JButton("Delete Instance");
        deleteInstanceButton.addActionListener(e -> extender.instanceTable.deleteInstance());

        setupPanel.add(hostField);
        setupPanel.add(addToScopeButton);
        setupPanel.add(enableScanningButton);

        scanStatusPanel.add(scanStatusLabel);

        testingPanel.add(generateWebChecklistButton);
        testingPanel.add(cancelFetchButton);
        testingPanel.add(generateLocalChecklistButton);
        if (selfUpdateLocal){
            testingPanel.add(saveLocalCopyButton);
        }
        testingPanel.add(generateExcelReportButton);
        testingPanel.add(saveCurrentProjectButton);
        miscPanel.add(deleteEntryButton);
        miscPanel.add(deleteInstanceButton);
        miscPanel.add(saveCurrentProjectButton);
        miscPanel.add(loadProjectButton);
        //default both delete buttons are disabled.
        disabledInitialButtons();

        // Add setup panel and status panel to top panel
        topPanel.add(setupPanel);
        topPanel.add(scanStatusPanel);
        topPanel.add(testingPanel);
        topPanel.add(miscPanel);
        gtScannerSplitPane.setLeftComponent(topPanel);
	}

	// This method setup the logger functionality tab
	private void setupLoggerPanel() {
		// Logger Tab
		JTabbedPane loggerTab = new JTabbedPane();
        JTabbedPane instanceLogTab = new JTabbedPane();
        JSplitPane internalLoggerSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        JSplitPane instancesLogsSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        JSplitPane internalPenTesterCommentsSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        JSplitPane internalEvidencesSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        
        // Setting up JTable
        JScrollPane loggerScrollPane = new JScrollPane(extender.loggerTable);
        loggerScrollPane.setPreferredSize(new Dimension(300, 200));
        loggerScrollPane.setBorder(new EmptyBorder(0, 0, 10, 0));
        JScrollPane instancesScrollPane = new JScrollPane(extender.instanceTable);
        instancesScrollPane.setPreferredSize(new Dimension(700, 200));
        instancesScrollPane.setBorder(new EmptyBorder(0, 0, 10, 0));

        
        // Upper half - store Pen testers comments and evidences log
        // Comments Pane
        penTesterCommentBox = new JTextPane();
        penTesterCommentBox.setContentType("text/plain");
        penTesterCommentBox.setEditable(true);
        JScrollPane penTesterCommentBoxScrollPane = new JScrollPane(penTesterCommentBox);
        JPanel commentsPanel = new JPanel(new FlowLayout(FlowLayout.LEADING, 10, 10));
        JButton clearCommentsButton = new JButton("Clear Comments");
        clearCommentsButton.addActionListener(e -> penTesterCommentBox.setText(""));
        JButton saveCommentsButton = new JButton("Save Comments");
        saveCommentsButton.addActionListener(e -> {
            //Added a line break to create a space between pentester comments and affected instances
            extender.loggerTable.modifyComments(penTesterCommentBox.getText().trim() + "\n");
        });
        commentsPanel.add(saveCommentsButton);
        commentsPanel.add(clearCommentsButton);
        internalPenTesterCommentsSplitPane.setTopComponent(commentsPanel);
        internalPenTesterCommentsSplitPane.setBottomComponent(penTesterCommentBoxScrollPane);
        
        // Evidence Pane
        evidenceBox = new JTextPane();
        evidenceBox.setContentType("text/plain");
        evidenceBox.setEditable(true);
        JScrollPane evidenceBoxScrollPane = new JScrollPane(evidenceBox);
        JPanel evidencePanel = new JPanel(new FlowLayout(FlowLayout.LEADING, 10, 10));
        JButton clearEvidencesButton = new JButton("Clear Evidence");
        clearEvidencesButton.addActionListener(e -> evidenceBox.setText(""));
        JButton saveEvidencesButton = new JButton("Save Evidence");
        saveEvidencesButton.addActionListener(e -> extender.loggerTable.modifyEvidence(evidenceBox.getText().trim()));
        evidencePanel.add(saveEvidencesButton);
        evidencePanel.add(clearEvidencesButton);
        internalEvidencesSplitPane.setTopComponent(evidencePanel);
        internalEvidencesSplitPane.setBottomComponent(evidenceBoxScrollPane);

        // Lower half - Instances Tab
        requestViewer = extender.callbacks.createMessageEditor(extender, false);
        responseViewer = extender.callbacks.createMessageEditor(extender, false);
        instanceLogTab.add("Request", requestViewer.getComponent());
        instanceLogTab.add("Response", responseViewer.getComponent());
        instancesLogsSplitPane.setLeftComponent(instancesScrollPane);
        instancesLogsSplitPane.setRightComponent(instanceLogTab);
        
        // Consolidate the final tabs for logger feature
        loggerTab.addTab("Affected Instances", instancesLogsSplitPane);
        loggerTab.addTab("Pen Tester Comments", internalPenTesterCommentsSplitPane);
        loggerTab.addTab("Evidence", internalEvidencesSplitPane);

        internalLoggerSplitPane.setTopComponent(loggerScrollPane);
        internalLoggerSplitPane.setBottomComponent(loggerTab);
        bottomModulesTabs.add("Logger", internalLoggerSplitPane);
	}

    // This method setup the OWASP checklist functionality tab
    private void setupCheckListPanel() {
        //Checklist internal panel
        JSplitPane internalChecklistSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);

        summaryTextPane = new JTextPane();
        summaryTextPane.setEditable(false);
        summaryTextPane.setContentType("text/html");
        summaryTextPane.putClientProperty("html.disable", null);
        summaryTextPane.addHyperlinkListener(e -> {
            if(e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                if(Desktop.isDesktopSupported()) {
                    try {
                        Desktop.getDesktop().browse(e.getURL().toURI());
                    }
                    catch (IOException | URISyntaxException e1) {
                        extender.stdout.println("Exception occurred at setupCheckListPanel");
                    }
                }
            }
        }
        );

        
        howToTestTextPane = new JEditorPane();
        howToTestTextPane.setEditable(false);
        howToTestTextPane.setContentType("text/html");
        howToTestTextPane.putClientProperty("html.disable", null);
        howToTestTextPane.addHyperlinkListener(e -> {
            if(e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                if(Desktop.isDesktopSupported()) {
                    try {
                        Desktop.getDesktop().browse(e.getURL().toURI());
                    }
                    catch (IOException | URISyntaxException e1) {
                        extender.stdout.println("Exception occurred at setupCheckListPanel");
                    }
                }
            }
        }
        );
        
        referencesTextPane = new JTextPane();
        referencesTextPane.setEditable(false);
        referencesTextPane.setContentType("text/html");
        referencesTextPane.putClientProperty("html.disable", null);
        referencesTextPane.addHyperlinkListener(e -> {
            if(e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                if(Desktop.isDesktopSupported()) {
                    try {
                        Desktop.getDesktop().browse(e.getURL().toURI());
                    }
                    catch (IOException | URISyntaxException e1) {
                        extender.stdout.println("Exception occurred at setupCheckListPanel");
                    }
                }
            }
        }
        );
        
        JScrollPane checklistScrollPane = new JScrollPane(extender.checklistTable);
        checklistScrollPane.setPreferredSize(new Dimension(300, 200));
        checklistScrollPane.setBorder(new EmptyBorder(0, 0, 10, 0));
        JScrollPane summaryScrollPane = new JScrollPane(summaryTextPane);
        JScrollPane howToTestScrollPane = new JScrollPane(howToTestTextPane);
        JScrollPane referencesScrollPane = new JScrollPane(referencesTextPane);     
        JTabbedPane checklistBottomTabs = new JTabbedPane();
        checklistBottomTabs.add("Summary", summaryScrollPane);
        checklistBottomTabs.add("How to test", howToTestScrollPane);
        checklistBottomTabs.add("References", referencesScrollPane);
        internalChecklistSplitPane.setLeftComponent(checklistScrollPane);
        internalChecklistSplitPane.setRightComponent(checklistBottomTabs);
        bottomModulesTabs.addTab("OWASP Testing Checklist", internalChecklistSplitPane);
        gtScannerSplitPane.setRightComponent(bottomModulesTabs);

	}

	// Initial buttons to set to disable by default
	public void disabledInitialButtons(){
        this.deleteEntryButton.setEnabled(false);
        this.deleteInstanceButton.setEnabled(false);
        generateExcelReportButton.setEnabled(false);
        saveLocalCopyButton.setEnabled(false);
        cancelFetchButton.setEnabled(false);
    }

    // To allow instance deletion button only
    public void deleteEntryButtonEnabled(){
        this.deleteEntryButton.setEnabled(true);
        this.deleteInstanceButton.setEnabled(false);
    }

    // To allow entry deletion button only
    public void deleteInstanceButtonEnabled(){
        this.deleteEntryButton.setEnabled(false);
        this.deleteInstanceButton.setEnabled(true);
    }

}
