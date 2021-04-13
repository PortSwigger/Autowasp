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

import java.io.Serializable;
import java.util.HashMap;

public class ChecklistEntry implements Serializable {
	
	public String refNumber;
	public String category;
	public String testName;
	public boolean exclusion;
	public boolean testcaseCompleted;
	public String summaryHTML;
	public String howToTestHTML;
	public String referencesHTML;
	public final String url;
	// Pentester's comments and evidence are sent from the scanner findings to a particular checklist entry when the user maps a finding to an entry
	public String pentesterComments;
	public String evidence;
	public boolean testBoolean;

	// This constructor is used to create new checklist entry objects when fetching new checklist data from the web
	public ChecklistEntry(HashMap<String, String> tableElements, HashMap<String, String> contentElements, String url) {
		this.refNumber = tableElements.get("Reference Number");
		this.category = tableElements.get("Category");
		this.testName = tableElements.get("Test Name");
		this.summaryHTML = contentElements.get("summary");
		this.howToTestHTML = contentElements.get("how to test");
		this.referencesHTML = contentElements.get("references");
		this.exclusion = false;
		this.testcaseCompleted = false;
		this.url = url;  //URL is included for ease of creating the hyperlinks when writing to excel file
		this.pentesterComments = "";
		this.evidence = "";
		this.testBoolean = false;
	}
	
	// This constructor is used for re-creating the checklist entry objects when loading data from a local file
	public ChecklistEntry(String refNumber, String category, String testName, String summaryHTML, String howToTestHTML, String referencesHTML, String url) {
		this.refNumber = refNumber;
		this.category = category;
		this.testName = testName;
		this.summaryHTML = summaryHTML;
		this.howToTestHTML = howToTestHTML;
		this.referencesHTML = referencesHTML;
		this.exclusion = false;
		this.testcaseCompleted = false;
		this.url = url;  //URL is included for ease of creating the hyperlinks when writing to excel file
		this.pentesterComments = "";
		this.evidence = "";
	}
	
	// Used to clean the checklist entry objects to prevent null pointer exceptions when displaying the data in the UI
	public void cleanEntry() {
		if (refNumber == null) {
			this.refNumber = "NIL";
		}
		if (category == null) {
			this.category = "NIL";
		}
		if (testName == null) {
			this.testName = "NIL";
		}
		if (summaryHTML == null) {
			this.summaryHTML = "NIL";
		}
		if (howToTestHTML == null) {
			this.howToTestHTML = "NIL";
		}
		if (referencesHTML == null) {
			// This is an empty string because every references panel will have at least the link to the OWASP article
			this.referencesHTML = "";
		}
	}
	
	public String getRefNumber() {
		return this.refNumber;
	}
	
	public String getTestName() {
		return this.testName;
	}
	
	public Boolean isExcluded() {return this.exclusion;}

	public Boolean isTestcaseCompleted(){return this.testcaseCompleted;}

	public void setExclusion(Boolean value){
		this.exclusion = value;
	}

	public void setTestCaseCompleted(Boolean value){ this.testcaseCompleted = value;}

	public void setPenTesterComments(String comments) {
		// New comments are appended to prevent overwriting existing comments
		this.pentesterComments += comments;
	}
	
	public void setEvidence(String evidence) {
		// New evidence is appended to prevent overwriting existing evidence
		this.evidence += evidence;
	}

	public void clearComments(){
		this.pentesterComments = "";
	}

	public void clearEvidences(){
		this.evidence = "";
	}

	public Boolean getTestBool() { return this.testBoolean; }

	public void setTestBoolean(Boolean testBoolean) {
		this.testBoolean = testBoolean;
	}
}
