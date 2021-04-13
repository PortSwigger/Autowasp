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

import javax.swing.*;
import javax.swing.table.TableColumn;
import javax.swing.table.TableModel;

@SuppressWarnings("serial")
public class ChecklistTable extends JTable {
	
	private final Autowasp extender;
	
	public ChecklistTable(TableModel tableModel, Autowasp extender) {
		super(tableModel);
		this.extender = extender;
		//Even parameters are default values for each column, odd parameters are max value for that column
		//Just need to make sure that the max value >= Default values or things are gonna be abit messy.
		setColumnWidths(200, 300, 200, 300, 1350, 1800, 500, 600, 200, 300);
	}

	public void setColumnWidths(int... widths) {
		for (int i = 0; i < widths.length; i += 2) {
			if ((i / 2) < columnModel.getColumnCount()) {
				columnModel.getColumn(i / 2).setPreferredWidth(widths[i]);
				columnModel.getColumn(i / 2).setMaxWidth(widths[i + 1]);
			}
			else continue;
		}
	}

	@Override
	public void changeSelection(int row, int col, boolean toggle, boolean extend) {
		ChecklistEntry checklistEntry = extender.checklistLog.get(row);
		
		// Sets the text for each of the bottom tab panes. Setting the caret position to 0 makes sure that the user starts reading from the top
		extender.extenderPanelUI.summaryTextPane.setText(checklistEntry.summaryHTML);
		extender.extenderPanelUI.summaryTextPane.setCaretPosition(0);
		extender.extenderPanelUI.howToTestTextPane.setText(checklistEntry.howToTestHTML.replaceAll("\n", "").replaceAll("\r", ""));
		extender.extenderPanelUI.howToTestTextPane.setCaretPosition(0);
		extender.extenderPanelUI.referencesTextPane.setText("<a href=" + checklistEntry.url + "><b>Link to Article</b></a>\n" + checklistEntry.referencesHTML);
		extender.extenderPanelUI.referencesTextPane.setCaretPosition(0);
		
		super.changeSelection(row, col, toggle, extend);
	}
}
