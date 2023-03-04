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

package autowasp.checklist;

import autowasp.Autowasp;
import autowasp.logger.entryTable.LoggerEntry;
import autowasp.logger.instancesTable.InstanceEntry;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Tag;
import org.jsoup.select.Elements;

import java.io.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.TimeUnit;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Hyperlink;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.common.usermodel.HyperlinkType;

/* Notes:
 * 1. The URL for the OWASP content page is currently pointing to our own branch of OWASP's repository. 
 * 2. The "fetch checklist" function currently takes around 3 minutes to fetch the data.
 */

public class ChecklistLogic implements Serializable {
	
	private final Autowasp extender;
	private Document anyPage;
	public final String GITHUB_REPO_URL = "https://github.com/OWASP/wstg/blob/master/document/4-Web_Application_Security_Testing/README.md";

	public ChecklistLogic(Autowasp extender) {
		this.extender = extender;
	}

	// Returns a list containing the URLs of all the test articles in order of reference number
	public List<String> scrapeArticleURLs() {
		// Get the URLs located within the main content page, which link to each individual section's content pages
		List<String> sectionContentPageURLs = scrapePageURLs(GITHUB_REPO_URL);
		sectionContentPageURLs.remove(0);  //Removes the link to the introduction page
		
		// Get the URLs for every individual article within each individual section's content page
		List<String> articleURLs = new ArrayList<>();
		for (String url : sectionContentPageURLs) {
			List<String> sectionArticleURLs = scrapePageURLs(url);
			articleURLs.addAll(sectionArticleURLs);
		}

		// Cleans the list of URLs to exclude external links and links to headers within the pages
		for (int i = 0; i < articleURLs.size(); i++) {
			// do another check to remove "sub" article of a test cases
			String[] array = articleURLs.get(i).split("/");
			int subArticleIndex = array[array.length-1].indexOf(".");
			// Condition 1 filters out external URLs,
			// while condition 2 filters out anchor URLs that link to headers within the article,
			// condition 3 filters out README.md and
			// condition 4 filters out sub-articles
			if (!articleURLs.get(i).contains("https://github.com") || articleURLs.get(i).contains("#") || articleURLs.get(i).contains("README.md") || articleURLs.get(i).contains("00")|| subArticleIndex==2) {
				articleURLs.remove(i);
				i--;
			}
		}
		return articleURLs;
	}

	// A general method to scrape all the URLs that exist on a page and return a list containing them
	public List<String> scrapePageURLs(String anyURL) {
		// Get the web page specified by the URL as a JSoup Document object
		try {
			anyPage = Jsoup.connect(anyURL).get();
			// Get the HTML elements of the page body
			Elements pageElements = anyPage.getElementsByTag("Article").get(0).children();

			// Get the list of URLs in the page body

			return pageElements.select("a[href]").eachAttr("abs:href");
		}		
		catch (IOException e) {
			extender.stderr.println("Error 1, Github page not found. Cancel fetch");
			extender.callbacks.issueAlert("Error 1, Github page not found. Cancel fetch");
			extender.extenderPanelUI.scanStatusLabel.setText("Error 1, Github page not found. Cancel fetch");
		}
		return null;

	}
	
	// Gets the Reference Number, Category, and Title of the article saved in a hash map
	public HashMap<String, String> getTableElements(String anyURL) {
		// Gets the web page specified by the URL as a JSoup Document object
		try {
			anyPage = Jsoup.connect(anyURL).get();
			// Get the HTML element of the article that contains the article's file path. Inspect the HTML elements of any article to see this
			Elements filePathElements = anyPage.getElementById("blob-path").children();
			Elements filePathElements2 = anyPage.getElementsByTag("td");

			// Get the required information from the HTML elements and save them into a hash map
			String refNumber = filePathElements2.first().text(); // get the ref number from table td tag

			String category = filePathElements.get(6).text().split("-", 2)[1].replace("_", " ");
			String testName = filePathElements.get(8).text().split("-", 2)[1].replace("_", " ");
			testName = testName.split("[.]", 2)[0]; // To get rid of the .md extension

			HashMap<String, String> tableElements = new HashMap<>();
			tableElements.put("Reference Number", refNumber);
			tableElements.put("Category", category);
			tableElements.put("Test Name", testName);
			return tableElements;
		}		
		catch (IOException e) {
			extender.stderr.println("Error 2, table element not found. Cancel Fetch");
			extender.callbacks.issueAlert("Error 2, table element not found. Cancel Fetch");
			extender.extenderPanelUI.scanStatusLabel.setText("Error 2, table element not found. Cancel Fetch");
		}
		return null;
	}
	
	/* Gets the "Summary", "How To Test", and "References" sections of the article saved in a hash map, with HTML format preserved to be rendered
	within the Burp UI elements */
	public HashMap<String, String> getContentElements(String anyURL) {
		// Gets the web page specified by the URL as a JSoup Document object
		try {
			anyPage = Jsoup.connect(anyURL).get();
			Element article = anyPage.getElementsByTag("Article").get(0);
			article.append("<h2>Ending marker</h2>");  // To provide an ending marker to stop the while loop later
			Elements articleElements = article.children();

			// replace img tags to href as extender does not pull images
			Elements img = article.getElementsByTag("img");
			for (Element e : img){
				String absoluteUrl = e.absUrl("src");
				Element newElement = new Element(Tag.valueOf("a"), "");
				newElement.attr("href", absoluteUrl);
				newElement.append("Refer to image here");
				e.replaceWith(newElement);
			}

			// Iterates through the list of HTML elements contained within the article, identifying each section title and its
			// corresponding body elements, saving the title (String) as the key and the body (String, HTML form) as the value in a hash map
			// State 0: A new header is reached
			// State 1: Going through the body elements
			// State 2: Meets the next header, saves current header and paragraphs into hash map, then move back to state 0
			int index = 0;
			int state = 0;
			String currentHeader = "";
			StringBuilder currentParagraphs = new StringBuilder();
			HashMap<String, String> contentElements = new HashMap<>();

			while (index < articleElements.size()) {
				switch (state) {
					case 0:
						currentHeader = articleElements.get(index).text().toLowerCase();
						state = 1;
						index++;
						break;
					case 1:
						if (articleElements.get(index).tagName().equals("h2")) {
							state = 2;
						}
						else {
							currentParagraphs.append(articleElements.get(index).toString());
							index++;
						}
						break;
					case 2:
						contentElements.put(currentHeader, currentParagraphs.toString());
						currentHeader = "";
						currentParagraphs = new StringBuilder();
						state = 0;
						break;
				}
			}

			return contentElements;
		}		
		catch (IOException e) {
			extender.stderr.println("Error 3, Content page not found. Cancel fetch");
			extender.callbacks.issueAlert("Error 3, Content page not found. Cancel fetch");
			extender.extenderPanelUI.scanStatusLabel.setText("Error 3, Content page not found. Cancel fetch");
		}
		return null;
	}

	// Saves a local copy of the checklist in a file called OWASPChecklistData.txt at the directory dictated by the user
	public void saveLocalCopy(String absoluteFilePath) throws IOException {
		FileOutputStream fileOutputStream = null;
		try{
			fileOutputStream = new FileOutputStream(absoluteFilePath + File.separator + "OWASP_WSTG_local");
			ObjectOutputStream outputStream = new ObjectOutputStream(fileOutputStream);
			for (ChecklistEntry entry : extender.checklistLog) {
				if (entry.exclusion = true) {
					ChecklistEntry tempChecklistEntry = entry;
					tempChecklistEntry.exclusion = false;
					outputStream.writeObject(tempChecklistEntry);
				} else {
					outputStream.writeObject(entry);
				}
			}
			outputStream.close();

			extender.extenderPanelUI.scanStatusLabel.setText("File saved to " + absoluteFilePath + File.separator + "OWASP_WSTG_local");
			extender.callbacks.issueAlert("File saved to " + absoluteFilePath + File.separator + "OWASP_WSTG_local");
			File savedFile = new File(absoluteFilePath + File.separator + "OWASP_WSTG_local");
		}

		finally {
			if (fileOutputStream !=null) {
				safeClose(fileOutputStream);
			}
		}
	}

	// Re-creates the checklistLog containing ChecklistEntry objects from the uploaded local file
	public void loadLocalCopy() {
		boolean eof = false;
		ChecklistEntry checklistEntryTemp;
		extender.checklistLog.clear();
		extender.checkListHashMap.clear();

		List<ChecklistEntry> checklistEntryList = new List<ChecklistEntry>() {
			@Override
			public int size() {
				return 0;
			}

			@Override
			public boolean isEmpty() {
				return false;
			}

			@Override
			public boolean contains(Object o) {
				return false;
			}

			@Override
			public Iterator<ChecklistEntry> iterator() {
				return null;
			}

			@Override
			public Object[] toArray() {
				return new Object[0];
			}

			@Override
			public <T> T[] toArray(T[] a) {
				return null;
			}

			@Override
			public boolean add(ChecklistEntry checklistEntry) {
				return false;
			}

			@Override
			public boolean remove(Object o) {
				return false;
			}

			@Override
			public boolean containsAll(Collection<?> c) {
				return false;
			}

			@Override
			public boolean addAll(Collection<? extends ChecklistEntry> c) {
				return false;
			}

			@Override
			public boolean addAll(int index, Collection<? extends ChecklistEntry> c) {
				return false;
			}

			@Override
			public boolean removeAll(Collection<?> c) {
				return false;
			}

			@Override
			public boolean retainAll(Collection<?> c) {
				return false;
			}

			@Override
			public void clear() {

			}

			@Override
			public boolean equals(Object o) {
				return false;
			}

			@Override
			public int hashCode() {
				return 0;
			}

			@Override
			public ChecklistEntry get(int index) {
				return null;
			}

			@Override
			public ChecklistEntry set(int index, ChecklistEntry element) {
				return null;
			}

			@Override
			public void add(int index, ChecklistEntry element) {

			}

			@Override
			public ChecklistEntry remove(int index) {
				return null;
			}

			@Override
			public int indexOf(Object o) {
				return 0;
			}

			@Override
			public int lastIndexOf(Object o) {
				return 0;
			}

			@Override
			public ListIterator<ChecklistEntry> listIterator() {
				return null;
			}

			@Override
			public ListIterator<ChecklistEntry> listIterator(int index) {
				return null;
			}

			@Override
			public List<ChecklistEntry> subList(int fromIndex, int toIndex) {
				return null;
			}
		};

		InputStream inputStream = getClass().getResourceAsStream("/OWASP_WSTG_local_06Apr2021");
		try{
			ObjectInputStream objectInputStream = new ObjectInputStream(inputStream);

			while(!eof){
				try{
					checklistEntryTemp = (ChecklistEntry) objectInputStream.readObject();
					checklistEntryList.add(checklistEntryTemp);
					extender.checkListHashMap.put(checklistEntryTemp.refNumber, checklistEntryTemp);
					extender.checklistLogic.loadNewChecklistEntry(checklistEntryTemp);
				} catch (EOFException e) {
					eof = true;
				}
			}
			extender.loggerTable.generateWSTGList();
			objectInputStream.close();
		} catch (IOException e) {
			extender.stdout.println("Cannot read local import file");
		}
		catch (ClassNotFoundException e) {
			extender.stdout.println("LoggerEntry class not found");
		}
		finally {
			if (inputStream != null){
				safeClose((FileInputStream) inputStream);
			}
		}
	}

	// Saves a local excel file at the directory specified by the user
	@SuppressWarnings("resource")
	public void saveToExcelFile(String absoluteFilePath) {
		// Populate your excel checklist data
		for (LoggerEntry findingEntry: extender.loggerList){
			// Identify findings that was mapped to OWASP checklist
			if (findingEntry.getChecklistIssue() == null || findingEntry.getChecklistIssue().isEmpty() || findingEntry.checklistIssue.equals("N.A.")) {
				continue;
			}
			// Extract the refID using substring
			int cutIndex = findingEntry.getChecklistIssue().indexOf(" -");
			String findingRefID = findingEntry.getChecklistIssue().substring(0,cutIndex);
			ChecklistEntry checklistEntry = extender.checkListHashMap.get(findingRefID);

			// Retrieve findings entry comments, evidences and instances that are not false positive.
			StringBuilder comments = new StringBuilder();
			comments.append(findingEntry.getPenTesterComments());
			comments.append("\nAffected Instance include(s):\n");
			for (InstanceEntry instanceEntry : findingEntry.getInstanceList()) {
				// Processed instances url that are not marked as false positive
				if (!instanceEntry.getConfidence().equals("False Positive")) {
					comments.append(instanceEntry.getUrl()).append(" - (").append(instanceEntry.getConfidence()).append(")\n");
				}
			}
			String evidence = "";
			evidence += findingEntry.getEvidence();

			// Append break line in case of more findings mapped to similar issue
			comments.append("\n\n");
			evidence += "\n\n";
			checklistEntry.setPenTesterComments(comments.toString());
			checklistEntry.setEvidence(evidence);
		}

		// Create a new workbook object and a new sheet
		XSSFWorkbook checklistWorkbook = new XSSFWorkbook();
		XSSFSheet checklistSheet = checklistWorkbook.createSheet("OWASP Checklist");

		// Create the style object for the headers (first row)
		XSSFCellStyle headerStyle = checklistWorkbook.createCellStyle();
		XSSFFont headerFont = checklistWorkbook.createFont();
		headerFont.setBold(true);
		headerStyle.setFont(headerFont);
		headerStyle.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());
		headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

		// Create the style object for the URL column
		XSSFCellStyle urlStyle = checklistWorkbook.createCellStyle();
		urlStyle.setWrapText(true);
		XSSFFont urlFont = checklistWorkbook.createFont();
		urlFont.setUnderline(XSSFFont.U_SINGLE);
		urlFont.setColor(IndexedColors.BLUE.getIndex());
		urlStyle.setFont(urlFont);

		// Create the style object for the pentester comments and evidence cells
		XSSFCellStyle cellStyle = checklistWorkbook.createCellStyle();
		cellStyle.setWrapText(true);

		// Create and style the headers row
		XSSFRow columnHeadersRow = checklistSheet.createRow(0);
		String[] headerArray = new String[]{"Reference Number", "Category", "Test Name", "Pentester Comments", "Evidence", "URL"};
		for (int i = 0; i < 6; i++) {
			XSSFCell cell = columnHeadersRow.createCell(i);
			cell.setCellValue(headerArray[i]);
			cell.setCellStyle(headerStyle);
		}

		// Create and style the content rows
		int rowNum = 0;
		for (int i = 0; i < extender.checklistLog.size(); i++) {
			ChecklistEntry entry = extender.checklistLog.get(i);
			String[] contentArray = new String[]{entry.refNumber, entry.category, entry.testName,
					entry.pentesterComments.trim(), entry.evidence.trim(), entry.url};
			entry.clearComments();
			entry.clearEvidences();

			// append "-" to rows that have no entry
			if (contentArray[3].equals("")){
				contentArray[3] = "N.A.";
			}
			if (contentArray[4].equals("")){
				contentArray[4] = "N.A.";
			}

			XSSFRow row = checklistSheet.createRow(++rowNum);
			for (int j = 0; j < 6; j++) {
				XSSFCell cell = row.createCell(j);
				cell.setCellValue(contentArray[j]);
				// Checks if the cell is the URL cell, which needs to be styled differently
				if (j != 5) {
					cell.setCellStyle(cellStyle);
				}
				else {
					cell.setCellStyle(urlStyle);
					// Adds the hyperlink to the URL cell
					CreationHelper helper = checklistWorkbook.getCreationHelper();
					Hyperlink articleLink = helper.createHyperlink(HyperlinkType.URL);
					articleLink.setAddress(entry.url);
					cell.setHyperlink(articleLink);
				}
			}
			// Sets cell height to excel default value so that word wrap doesn't make the rows super tall
			row.setHeight((short)-1);
		}

		// Auto-size every column first
		for (int i = 0; i <= columnHeadersRow.getPhysicalNumberOfCells(); i++) {
				checklistSheet.autoSizeColumn(i);
		}

		// Then set a fixed column width of 100 characters (apparently the setColumnWidth method uses 1/256 of a character width as a measurement unit)
		checklistSheet.setColumnWidth(3, 25600);
		checklistSheet.setColumnWidth(4, 25600);

		// Writes the workbook object into an actual excel file. File.separator is used to ensure cross OS compatibility
		try {
			FileOutputStream excelWriter = new FileOutputStream(new File(absoluteFilePath + File.separator + "OWASP Checklist.xlsx"));
			checklistWorkbook.write(excelWriter);
			excelWriter.close();
			extender.callbacks.issueAlert("Excel report generated!");
			extender.extenderPanelUI.scanStatusLabel.setText("Excel report generated!");
		} catch (IOException e) {
			extender.callbacks.issueAlert("Error, file not found");
		}
	}
	
	// Constructs a new ChecklistEntry object and adds it to the checklistLog using the setValueAt() method
	public void logNewChecklistEntry(String url) {

		int row = this.extender.checklistLog.size();
		try {
			TimeUnit.MILLISECONDS.sleep(1000);
		} catch (InterruptedException e) {
			extender.stdout.println("Error in fetching the url: " + url);
		}
		ChecklistEntry checklistEntry = new ChecklistEntry(this.getTableElements(url), this.getContentElements(url), url);
		checklistEntry.cleanEntry();
		extender.checklistTableModel.addValueAt(checklistEntry, row, row);
		extender.checkListHashMap.put(checklistEntry.refNumber,checklistEntry);
	}
	
	// Adds a ChecklistEntry object created from a local saved file to the checklistLog using the setValueAt() method
	public void loadNewChecklistEntry(ChecklistEntry entry) {
		int row = this.extender.checklistLog.size();
		extender.checklistTableModel.addValueAt(entry, row, row);
	}

	// Logic to calculate file hash
	public String toHash(File chosenFile) throws NoSuchAlgorithmException {
		MessageDigest md;
		String result ="";
		int readCount;
		FileInputStream fis = null;
		try {
			md = MessageDigest.getInstance("SHA-256");
			fis = new FileInputStream(chosenFile);
			byte[] dataBytes = new byte[1024];

			while ((readCount = fis.read(dataBytes)) != -1) {
				md.update(dataBytes, 0, readCount);
			}
			safeClose(fis);
			byte[] mdbytes = md.digest();

			// convert the byte to hex format method
			StringBuilder sb = new StringBuilder();
			for (byte mdbyte : mdbytes) {
				sb.append(Integer.toString((mdbyte & 0xff) + 0x100, 16).substring(1));
			}
			result = sb.toString();
		}
		catch(IOException ioe){
			extender.stderr.println("Error exception at toHash");
		}
		finally {
			safeClose(fis);
		}

		return result;
	}

	// Method for save closing of FileOutputStream
	public void safeClose(FileOutputStream fos) {
		if (fos != null) {
			try {
				fos.close();
			} catch (IOException e) {
				extender.stdout.println("FileOutputStream cannot perform safeClose");
			}
		}
	}

	private void safeClose(FileInputStream fis){
		if (fis !=null){
			try{
				fis.close();
			} catch (IOException e) {
				extender.stdout.println("FileInputStream cannot perform safeClose");
			}

		}
	}
}

