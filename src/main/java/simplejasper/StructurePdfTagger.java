/*
 * JasperReports - Free Java Reporting Library.
 * Copyright (C) 2001 - 2025 Cloud Software Group, Inc. All rights reserved.
 * http://www.jaspersoft.com
 *
 * Unless you have purchased a commercial license agreement from Jaspersoft,
 * the following license terms apply:
 *
 * This program is part of JasperReports.
 *
 * JasperReports is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * JasperReports is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with JasperReports. If not, see <http://www.gnu.org/licenses/>.
 */

/*
 * Contributors:
 * Adrian Jackson - iapetus@users.sourceforge.net
 * David Taylor - exodussystems@users.sourceforge.net
 * Lars Kristensen - llk@users.sourceforge.net
 * Ling Li - lonecatz@users.sourceforge.net
 * Martin Clough - mtclough@users.sourceforge.net
 */

/*
 * Modified in November 2026 from net.sf.jasperreports.pdf.JRPdfExporterTagHelper of
 * JasperReports 7.0.7 to implement the PdfTagger interface of JasperReports 7.1.1.
 */
package simplejasper;

import java.util.Stack;

import net.sf.jasperreports.crosstabs.JRCellContents;
import net.sf.jasperreports.engine.JRPrintElement;
import net.sf.jasperreports.engine.JRPrintFrame;
import net.sf.jasperreports.engine.JRPrintImage;
import net.sf.jasperreports.engine.JRPrintHyperlink;
import net.sf.jasperreports.engine.JRPrintText;
import net.sf.jasperreports.engine.export.PdfConstants;
import net.sf.jasperreports.engine.util.StyledTextListWriter;
import net.sf.jasperreports.export.AccessibilityUtil;
import net.sf.jasperreports.export.type.AccessibilityTagEnum;
import net.sf.jasperreports.pdf.JRPdfExporter;
import net.sf.jasperreports.pdf.PdfExporterConfiguration;
import net.sf.jasperreports.pdf.common.PdfProducer;
import net.sf.jasperreports.pdf.common.PdfStructure;
import net.sf.jasperreports.pdf.common.PdfStructureEntry;
import net.sf.jasperreports.pdf.common.PdfTagger;

/**
 * Writes the structure tree of a tagged PDF: the reading order and the role of each element that a screen
 * reader relies on, and that PDF/A-1a requires.
 * <p/>
 * Since 7.0.8 the open source JasperReports Library no longer ships a working tagger: its default
 * DefaultPdfTaggerFactory raises "export.pdf.tags.not.supported" for tagged exports, and the working
 * implementation is only available in JasperReports Library Professional, as the documentation of
 * net.sf.jasperreports.export.pdf.tagger.factory states.
 */
public class StructurePdfTagger implements PdfTagger, StyledTextListWriter
{
	protected PdfProducer pdfProducer;
	protected PdfStructure pdfStructure;

	protected PdfStructureEntry documentTag;
	protected Stack<PdfStructureEntry> tagStack;
	protected Stack<AccessibilityTagEnum> headerStack;
	protected PdfStructureEntry currentLinkTag;
	protected PdfStructureEntry elementLinkTag;
	protected boolean firstLinkParagraph;
	protected boolean firstTextParagraph;
	protected boolean isTagEmpty = true;
	protected int crtCrosstabRowY = -1;
	protected boolean insideCrosstabCellFrame;
	protected boolean isDataCellPrinted;

	protected boolean isTagged = true;
	protected boolean isArtifactText;
	protected boolean isArtifactSpan;
	protected String language;

	protected PdfStructureEntry chunkParagraphTag;
	protected PdfStructureEntry lastChunkTag;
	protected AccessibilityTagEnum lastChunkAccessibilityTag;
	protected JRPrintHyperlink lastChunkHyperlink;

	public StructurePdfTagger()
	{
	}
	
	
	/**
	 *
	 */
	@Override
	public void setLanguage(String language)
	{
		this.language = language;
	}
	
	/**
	 *
	 */
	@Override
	public void setPdfProducer(PdfProducer pdfProducer)
	{
		this.pdfProducer = pdfProducer;
		this.pdfStructure = pdfProducer.getPdfStructure();
		
	}
	
	/**
	 *
	 */
	@Override
	public void init()
	{
		if (isTagged)
		{
			documentTag = pdfStructure.createDocumentTag(language);
			
			tagStack = new Stack<>();
			tagStack.push(documentTag);
			
			headerStack = new Stack<>();
		}
	}
	
	@Override
	public void beginArtifact()
	{
		if (isTagged)
		{
			pdfStructure.beginArtifact();
		}
	}

	@Override
	public void endArtifact()
	{
		if (isTagged)
		{
			pdfStructure.endArtifact();
		}
	}

	@Override
	public void startPage()
	{
		crtCrosstabRowY = -1;
		insideCrosstabCellFrame = false;
		isDataCellPrinted = false;
		
	}
	
	@Override
	public void endPage()
	{
		if (isTagged)
		{
			if (crtCrosstabRowY >= 0) //crosstab still open
			{
				//end the current row
				tagStack.pop();
				
				//end the table
				tagStack.pop();
			}
		}
	}

	@Override
	public void startElement(JRPrintElement element)
	{
		firstTextParagraph = true;

		if (isTagged)
		{
			JRPrintFrame frame = element instanceof JRPrintFrame ? (JRPrintFrame) element : null;
			
			boolean isCellContentsFrame =
				frame != null
				&& frame.getPropertiesMap().hasProperties()
				&& frame.getPropertiesMap().getProperty(JRCellContents.PROPERTY_TYPE) != null;
			
			if (isCellContentsFrame)
			{
				insideCrosstabCellFrame = true;

				if (crtCrosstabRowY >= 0) //crosstab already started
				{
					if (JRCellContents.TYPE_DATA.equals(frame.getPropertiesMap().getProperty(JRCellContents.PROPERTY_TYPE)))
					{
						isDataCellPrinted = true;
					}

					if (crtCrosstabRowY != frame.getY())
					{
						//this is the first cell on a new row

						//end the current row
						tagStack.pop();
						
						if (
							isDataCellPrinted
							&& (JRCellContents.TYPE_CROSSTAB_TITLE.equals(frame.getPropertiesMap().getProperty(JRCellContents.PROPERTY_TYPE))
								|| JRCellContents.TYPE_CROSSTAB_HEADER.equals(frame.getPropertiesMap().getProperty(JRCellContents.PROPERTY_TYPE))
								|| JRCellContents.TYPE_COLUMN_HEADER.equals(frame.getPropertiesMap().getProperty(JRCellContents.PROPERTY_TYPE)))
							)
						{
							//end the table
							tagStack.pop();

							//start table
							createTableStartTag();
							//start crosstab
							isDataCellPrinted = false;
						}

						//start the new row
						createTrStartTag();

						//keep crosstab open, but mark new row position and frame depth
						crtCrosstabRowY = frame.getY();
					}
				}
				else
				{
					//start table and first row
					createTableStartTag();
					createTrStartTag();
					//start crosstab
					crtCrosstabRowY = frame.getY();
					isDataCellPrinted = false;
				}
			}
			else
			{
				if (crtCrosstabRowY >= 0) //crosstab already started
				{
					if (!insideCrosstabCellFrame)
					{
						//normal element outside crosstab
						
						//end the current row
						tagStack.pop();
						
						//end the table
						tagStack.pop();
						
						//end crosstab
						crtCrosstabRowY = -1;
					}
				}
//				else
//				{
//						//normal element outside crosstab
//				}
			}
			
			createStartTags(element);
		}
	}
	
	@Override
	public void endElement(JRPrintElement element)
	{
		if (isTagged)
		{
			JRPrintFrame frame = element instanceof JRPrintFrame ? (JRPrintFrame) element : null;
			
			boolean isCellContentsFrame =
				frame != null
				&& frame.getPropertiesMap().hasProperties()
				&& frame.getPropertiesMap().getProperty(JRCellContents.PROPERTY_TYPE) != null;
				
			if (isCellContentsFrame)
			{
				insideCrosstabCellFrame = false;
			}
			
			createEndTags(element);
		}
	}

	@Override
	public void startImage(JRPrintImage printImage, float llx, float lly, float urx, float ury)
	{
		if (isTagged)
		{
			PdfStructureEntry parent = tagStack.peek();
			
			PdfStructureEntry imageTag = null;

			if (printImage.getLinkType() == null)
			{
				imageTag = pdfStructure.beginTag(parent, "Figure");
			}
			else
			{
				imageTag = pdfStructure.createTag(parent, "Figure");
				currentLinkTag = pdfStructure.beginTag(imageTag, "Link");
			}

			imageTag.setBBox(llx, lly, urx, ury);

			if (printImage.getHyperlinkTooltip() != null)
			{
				imageTag.putString("Alt", printImage.getHyperlinkTooltip());
			}
		}
	}

	@Override
	public void endImage()
	{
		if (isTagged)
		{
			pdfStructure.endTag();
			currentLinkTag = null;
			isTagEmpty = false;
		}
	}

	@Override
	public PdfStructureEntry getCurrentLinkTag()
	{
		return currentLinkTag;
	}

	protected boolean isFirstTextParagraph()
	{
		return firstTextParagraph;
	}

	@Override
	public boolean isFirstLinkParagraph()
	{
		return firstLinkParagraph;
	}

	@Override
	public void startText(JRPrintText textElement, String actualText, boolean styledTextChunkTags)
	{
		if (isTagged)
		{
			if (textElement.hasProperties() && textElement.getPropertiesMap().containsProperty(AccessibilityUtil.PROPERTY_TAG_ATTRIBUTE_ACTUAL_TEXT))
			{
				//FIXME the actual text repeats for each paragraph in multi-paragraph text elements, which is not good;
				// the actual text should be an attribute of the paragraph in styled text
				actualText = textElement.getPropertiesMap().getProperty(AccessibilityUtil.PROPERTY_TAG_ATTRIBUTE_ACTUAL_TEXT);
			}

			if (isArtifactText)
			{
				if (actualText != null)
				{
					pdfStructure.beginSpan(actualText);
					isArtifactSpan = true;
				}
			}
			else
			{
				PdfStructureEntry parent = tagStack.peek();

				boolean isLink = textElement.getLinkType() != null;

				if (isLink)
				{
					if (elementLinkTag == null)
					{
						if (headerStack.size() == 0)
						{
							parent = pdfStructure.createTag(parent, "Text");
						}
						elementLinkTag = pdfStructure.createTag(parent, "Link");
						firstLinkParagraph = true;
					}
					else
					{
						firstLinkParagraph = false;
					}
					currentLinkTag = elementLinkTag;
				}

				String tagName = isLink || headerStack.size() > 0 ? null : "Text";
				PdfStructureEntry mcParent = isLink ? elementLinkTag : parent;

				PdfStructureEntry textEntry;
				if (styledTextChunkTags)
				{
					// the chunks of the paragraph open their own marked content, so the paragraph
					// tag only groups the structure elements that getStyledTextChunkTag creates
					textEntry = tagName == null ? mcParent : pdfStructure.createTag(mcParent, tagName);
					chunkParagraphTag = textEntry;
					if (actualText != null)
					{
						textEntry.putString("ActualText", actualText);
					}
				}
				else
				{
					textEntry =
						actualText == null
						? pdfStructure.beginTag(mcParent, tagName)
						: pdfStructure.beginTag(mcParent, tagName, actualText);
				}

				if (textElement.hasProperties())
				{
					if (textElement.getPropertiesMap().containsProperty(PdfExporterConfiguration.PROPERTY_TAG_LANGUAGE))
					{
						textEntry.putString("Lang", textElement.getPropertiesMap().getProperty(PdfExporterConfiguration.PROPERTY_TAG_LANGUAGE));
					}
				}
			}
		}
	}

	@Override
	public void endText()
	{
		if (isTagged)
		{
			if (isArtifactText)
			{
				if (isArtifactSpan)
				{
					pdfStructure.endSpan();
					isArtifactSpan = false;
				}
			}
			else
			{
				if (chunkParagraphTag == null)
				{
					pdfStructure.endTag();
				}
				chunkParagraphTag = null;
				lastChunkTag = null;
				lastChunkAccessibilityTag = null;
				lastChunkHyperlink = null;
				if (elementLinkTag == null)
				{
					currentLinkTag = null;
				}
				isTagEmpty = false;
			}
		}
		
		firstTextParagraph = false;
	}

	protected void createStartTags(JRPrintElement element)
	{
		if (element.hasProperties())
		{
			String prop = element.getPropertiesMap().getProperty(PdfConstants.PROPERTY_TAG_TABLE);
			if (PdfConstants.TAG_START.equals(prop) || PdfConstants.TAG_FULL.equals(prop))
			{
				createTableStartTag();
			}
			
			prop = element.getPropertiesMap().getProperty(PdfConstants.PROPERTY_TAG_TR);
			if (PdfConstants.TAG_START.equals(prop) || PdfConstants.TAG_FULL.equals(prop))
			{
				createTrStartTag();
			}
			
			prop = element.getPropertiesMap().getProperty(PdfConstants.PROPERTY_TAG_TH);
			if (PdfConstants.TAG_START.equals(prop) || PdfConstants.TAG_FULL.equals(prop))
			{
				createThStartTag(element, "Column");
			}

			prop = element.getPropertiesMap().getProperty(PdfConstants.PROPERTY_TAG_TD);
			if (PdfConstants.TAG_START.equals(prop) || PdfConstants.TAG_FULL.equals(prop))
			{
				createTdStartTag(element);
			}
			
			prop = element.getPropertiesMap().getProperty(JRCellContents.PROPERTY_TYPE);
			if (JRCellContents.TYPE_CROSSTAB_TITLE.equals(prop))
			{
				createThStartTag(element, "Both");
			}
			else if (JRCellContents.TYPE_CROSSTAB_HEADER.equals(prop))
			{
				createThStartTag(element, "Both");
			}
			else if (JRCellContents.TYPE_COLUMN_HEADER.equals(prop))
			{
				createThStartTag(element, "Column");
			}
			else if (JRCellContents.TYPE_ROW_HEADER.equals(prop))
			{
				createThStartTag(element, "Row");
			}
			if (JRCellContents.TYPE_DATA.equals(prop))
			{
				createTdStartTag(element);
			}
			
			prop = element.getPropertiesMap().getProperty(PROPERTY_TAG_L);
			if (PdfConstants.TAG_START.equals(prop) || PdfConstants.TAG_FULL.equals(prop))
			{
				createListStartTag();
			}
			
			prop = element.getPropertiesMap().getProperty(PROPERTY_TAG_LI);
			if (PdfConstants.TAG_START.equals(prop) || PdfConstants.TAG_FULL.equals(prop))
			{
				createStartTag(element, "LI");
			}

			prop = element.getPropertiesMap().getProperty(PROPERTY_TAG_LBL);
			if (PdfConstants.TAG_START.equals(prop) || PdfConstants.TAG_FULL.equals(prop))
			{
				createStartTag(element, "Lbl");
			}

			prop = element.getPropertiesMap().getProperty(PROPERTY_TAG_LBODY);
			if (PdfConstants.TAG_START.equals(prop) || PdfConstants.TAG_FULL.equals(prop))
			{
				createStartTag(element, "LBody");
			}

			createStartHeadingTags(element, PROPERTY_TAG_H1, AccessibilityTagEnum.H1);
			createStartHeadingTags(element, PROPERTY_TAG_H2, AccessibilityTagEnum.H2);
			createStartHeadingTags(element, PROPERTY_TAG_H3, AccessibilityTagEnum.H3);
			createStartHeadingTags(element, PROPERTY_TAG_H4, AccessibilityTagEnum.H4);
			createStartHeadingTags(element, PROPERTY_TAG_H5, AccessibilityTagEnum.H5);
			createStartHeadingTags(element, PROPERTY_TAG_H6, AccessibilityTagEnum.H6);

			prop = element.getPropertiesMap().getProperty(PROPERTY_TAG_REFERENCE);
			if (PdfConstants.TAG_START.equals(prop) || PdfConstants.TAG_FULL.equals(prop))
			{
				createStartTag(element, "Reference");
			}

			prop = element.getPropertiesMap().getProperty(PROPERTY_TAG_NOTE);
			if (PdfConstants.TAG_START.equals(prop) || PdfConstants.TAG_FULL.equals(prop))
			{
				createStartTag(element, "Note");
			}

			if (AccessibilityTagEnum.ARTIFACT.getName().equals(
					element.getPropertiesMap().getProperty(AccessibilityUtil.PROPERTY_ACCESSIBILITY_TAG)))
			{
				isArtifactText = true;
				pdfStructure.beginArtifact();
			}
		}
	}


	protected void createStartHeadingTags(JRPrintElement element, String pdfTagProp, AccessibilityTagEnum accessibilityTag)
	{
		String accessibilityTagPropValue = element.getPropertiesMap().getProperty(AccessibilityUtil.PROPERTY_ACCESSIBILITY_TAG);
		String pdfTagPropValue = element.getPropertiesMap().getProperty(pdfTagProp);
		if (
			accessibilityTag.getName().equals(accessibilityTagPropValue)
			|| PdfConstants.TAG_START.equals(pdfTagPropValue) || PdfConstants.TAG_FULL.equals(pdfTagPropValue)
			)
		{
			PdfStructureEntry headingTag = pdfStructure.createTag(tagStack.peek(), accessibilityTag.name());
			tagStack.push(headingTag);
			headerStack.push(accessibilityTag);
			isTagEmpty = true;
		}
	}


	protected void createTableStartTag()
	{
		PdfStructureEntry tableTag = pdfStructure.createTag(documentTag, "Table");
		tableTag.putArray("K");
		tagStack.push(tableTag);
	}
		
	
	protected void createTrStartTag()
	{
		PdfStructureEntry tableRowTag = pdfStructure.createTag(tagStack.peek(), "TR");
		tableRowTag.putArray("K");
		tagStack.push(tableRowTag);
	}
		
	
	protected void createThStartTag(JRPrintElement element, String scope)
	{
		PdfStructureEntry tableHeaderTag = pdfStructure.createTag(tagStack.peek(), "TH");
		tableHeaderTag.putArray("K");
		tagStack.push(tableHeaderTag);
		isTagEmpty = true;
		
		createSpanTags(element, tableHeaderTag);
		tableHeaderTag.setScope(scope);
	}

	
	protected void createTdStartTag(JRPrintElement element)
	{
		PdfStructureEntry tableCellTag = pdfStructure.createTag(tagStack.peek(), "TD");
		tableCellTag.putArray("K");
		tagStack.push(tableCellTag);
		isTagEmpty = true;
		
		createSpanTags(element, tableCellTag);
	}
		

	protected void createSpanTags(JRPrintElement element, PdfStructureEntry parentTag)
	{
		Integer colSpan = null;
		try	{
			String colSpanProp = element.getPropertiesMap().getProperty(PdfConstants.PROPERTY_TAG_COLSPAN);
			if (colSpanProp != null) {
				colSpan = Integer.valueOf(colSpanProp);
			}
		} catch (NumberFormatException e) {}
		
		if (colSpan == null) {
			try	{
				String colSpanProp = element.getPropertiesMap().getProperty(JRCellContents.PROPERTY_COLUMN_SPAN);
				if (colSpanProp != null) {
					colSpan = Integer.valueOf(colSpanProp);
				}
			} catch (NumberFormatException ex) {}
		}
		
		Integer rowSpan = null;
		try {
			String rowSpanProp = element.getPropertiesMap().getProperty(PdfConstants.PROPERTY_TAG_ROWSPAN);
			if (rowSpanProp != null) {
				rowSpan = Integer.valueOf(rowSpanProp);
			}
		} catch (NumberFormatException e) {}
		
		if (rowSpan == null) {
			try {
				String rowSpanProp = element.getPropertiesMap().getProperty(JRCellContents.PROPERTY_ROW_SPAN);
				if (rowSpanProp != null) {
					rowSpan = Integer.valueOf(rowSpanProp);
				}
			} catch (NumberFormatException ex) {}			
		}
		
		if (colSpan != null && colSpan > 1 || rowSpan != null && rowSpan > 1)
		{
			parentTag.setSpan(colSpan == null ? 0 : colSpan, rowSpan == null ? 0 : rowSpan);
		}
	}


	protected void createListStartTag()
	{
		PdfStructureEntry listTag = pdfStructure.createTag(tagStack.peek(), "L");
		listTag.putArray("K");
		tagStack.push(listTag);
	}
		
	
	protected void createStartTag(JRPrintElement element, String pdfTag)
	{
		PdfStructureEntry tag = pdfStructure.createTag(tagStack.peek(), pdfTag);
		tag.putArray("K");
		tagStack.push(tag);
		isTagEmpty = true;
	}
	
	
	protected void createEndTags(JRPrintElement element)// throws DocumentException, IOException, JRException
	{
		if (isArtifactText)
		{
			isArtifactText = false;
			pdfStructure.endArtifact();
		}

		elementLinkTag = null;
		currentLinkTag = null;

		if (element.hasProperties())
		{
			createEndTag(element, PROPERTY_TAG_NOTE);
			createEndTag(element, PROPERTY_TAG_REFERENCE);
			
			createEndHeadingTags(element, PROPERTY_TAG_H6, AccessibilityTagEnum.H6);
			createEndHeadingTags(element, PROPERTY_TAG_H5, AccessibilityTagEnum.H5);
			createEndHeadingTags(element, PROPERTY_TAG_H4, AccessibilityTagEnum.H4);
			createEndHeadingTags(element, PROPERTY_TAG_H3, AccessibilityTagEnum.H3);
			createEndHeadingTags(element, PROPERTY_TAG_H2, AccessibilityTagEnum.H2);
			createEndHeadingTags(element, PROPERTY_TAG_H1, AccessibilityTagEnum.H1);

			createEndTag(element, PROPERTY_TAG_LBODY);
			createEndTag(element, PROPERTY_TAG_LBL);
			createEndTag(element, PROPERTY_TAG_LI);

			String prop = element.getPropertiesMap().getProperty(PROPERTY_TAG_L);
			if (PdfConstants.TAG_END.equals(prop) || PdfConstants.TAG_FULL.equals(prop))
			{
				tagStack.pop();
			}

			prop = element.getPropertiesMap().getProperty(JRCellContents.PROPERTY_TYPE);
			if (
				prop != null 
				&& (JRCellContents.TYPE_CROSSTAB_TITLE.equals(prop) 
					|| JRCellContents.TYPE_CROSSTAB_HEADER.equals(prop)
					|| JRCellContents.TYPE_COLUMN_HEADER.equals(prop)
					|| JRCellContents.TYPE_ROW_HEADER.equals(prop)
					|| JRCellContents.TYPE_DATA.equals(prop)))
			{
				if (isTagEmpty)
				{
					pdfStructure.beginTag(tagStack.peek(), "Span");
					pdfStructure.endTag();
				}

				tagStack.pop();
			}
			
			createEndTag(element, PdfConstants.PROPERTY_TAG_TD);
			createEndTag(element, PdfConstants.PROPERTY_TAG_TH);
			
			prop = element.getPropertiesMap().getProperty(PdfConstants.PROPERTY_TAG_TR);
			if (PdfConstants.TAG_END.equals(prop) || PdfConstants.TAG_FULL.equals(prop))
			{
				tagStack.pop();
			}

			prop = element.getPropertiesMap().getProperty(PdfConstants.PROPERTY_TAG_TABLE);
			if (PdfConstants.TAG_END.equals(prop) || PdfConstants.TAG_FULL.equals(prop))
			{
				tagStack.pop();
			}
		}
//		else
//		{
//			//check to see if this is the first element without tag properties after a table
//			if (tagStack.size() > 2) // check to see if we are at least inside a table row (table/tr)
//			{
//				PdfStructureElement tag = tagStack.peek();
//				if (
//					PdfName.TABLEROW.equals(tag.get(PdfName.S))
//					&& PdfName.TABLE.equals(tag.getParent().get(PdfName.S))
//					)
//				{
//					// take out both row and table tags
//					tagStack.pop();
//					tagStack.pop();
//				}
//			}
//		}
	}


	protected void createEndHeadingTags(JRPrintElement element, String pdfTagProp, AccessibilityTagEnum accessibilityTag)
	{
		String accessibilityTagPropValue = element.getPropertiesMap().getProperty(AccessibilityUtil.PROPERTY_ACCESSIBILITY_TAG);
		String pdfTagPropValue = element.getPropertiesMap().getProperty(pdfTagProp);
		if (
			accessibilityTag.getName().equals(accessibilityTagPropValue)
			|| PdfConstants.TAG_END.equals(pdfTagPropValue) || PdfConstants.TAG_FULL.equals(pdfTagPropValue)
			)
		{
			if (isTagEmpty)
			{
				pdfStructure.beginTag(tagStack.peek(), "Span");//FIXMENOW still needed?
				pdfStructure.endTag();
			}

			tagStack.pop();
			headerStack.pop();
		}
	}
	
	protected void createEndTag(JRPrintElement element, String pdfTagProp)
	{
		String prop = element.getPropertiesMap().getProperty(pdfTagProp);
		if (PdfConstants.TAG_END.equals(prop) || PdfConstants.TAG_FULL.equals(prop))
		{
			if (isTagEmpty)
			{
				pdfStructure.beginTag(tagStack.peek(), "Span");
				pdfStructure.endTag();
			}
			
			tagStack.pop();
		}
	}
	
	@Override
	public void startUl() 
	{
		createListStartTag();
	}

	@Override
	public void endUl() 
	{
		tagStack.pop();
	}

	@Override
	public void startOl(String type, int cutStart) 
	{
		createListStartTag();
	}

	@Override
	public void endOl() 
	{
		tagStack.pop();
	}

	@Override
	public void startLi(boolean noBullet) 
	{
		createStartTag(null, "LI");
		createStartTag(null, "LBody");
	}

	@Override
	public void endLi() 
	{
		if (isTagEmpty)
		{
			pdfStructure.beginTag(tagStack.peek(), "Span");
			pdfStructure.endTag();
		}

		tagStack.pop(); // pop LBody
		tagStack.pop(); // pop LI
	}

	@Override
	public StyledTextListWriter getListWriter()
	{
		return isTagged ? this : null;
	}

	@Override
	public PdfStructureEntry getStyledTextChunkTag(AccessibilityTagEnum accessibilityTag, JRPrintHyperlink hyperlink)
	{
		if (chunkParagraphTag == null)
		{
			return null;
		}
		if (lastChunkTag != null
			&& accessibilityTag == lastChunkAccessibilityTag
			&& JRPdfExporter.isSameStyledTextHyperlink(hyperlink, lastChunkHyperlink))
		{
			return lastChunkTag;
		}
		PdfStructureEntry parent = chunkParagraphTag;
		if (accessibilityTag == AccessibilityTagEnum.REFERENCE || accessibilityTag == AccessibilityTagEnum.NOTE)
		{
			parent = pdfStructure.createTag(parent, accessibilityTag == AccessibilityTagEnum.REFERENCE ? "Reference" : "Note");
		}
		lastChunkTag = hyperlink != null
			? pdfStructure.createTag(parent, "Link")
			: parent == chunkParagraphTag ? pdfStructure.createTag(parent, "Span") : parent;
		lastChunkAccessibilityTag = accessibilityTag;
		lastChunkHyperlink = hyperlink;
		return lastChunkTag;
	}

	@Override
	public PdfStructureEntry getCurrentContentEntry()
	{
		return tagStack == null || tagStack.isEmpty() ? null : tagStack.peek();
	}

	@Override
	public PdfStructureEntry getPageStructureEntry(int pdfPage)
	{
		// the classic producer ignores the structure destination of local page links
		return null;
	}
}
