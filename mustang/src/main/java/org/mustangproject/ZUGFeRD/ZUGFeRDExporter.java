package org.mustangproject.ZUGFeRD;
/**
 * Mustangproject's ZUGFeRD implementation
 * ZUGFeRD exporter
 * Licensed under the APLv2
 * @date 2014-05-10
 * @version 1.0
 * @author jstaerk
 * */


import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.GregorianCalendar;
import java.util.HashMap;

import javax.xml.transform.TransformerException;

import org.apache.jempbox.xmp.XMPMetadata;
import org.apache.jempbox.xmp.XMPSchemaBasic;
import org.apache.jempbox.xmp.XMPSchemaDublinCore;
import org.apache.jempbox.xmp.XMPSchemaPDF;
import org.apache.jempbox.xmp.pdfa.XMPSchemaPDFAId;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentCatalog;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDDocumentNameDictionary;
import org.apache.pdfbox.pdmodel.PDEmbeddedFilesNameTreeNode;
import org.apache.pdfbox.pdmodel.common.PDMetadata;
import org.apache.pdfbox.pdmodel.common.filespecification.PDComplexFileSpecification;
import org.apache.pdfbox.pdmodel.common.filespecification.PDEmbeddedFile;


public class ZUGFeRDExporter {
	/***
	 * You will need Apache PDFBox. To use the ZUGFeRD exporter, 
	   implement IZUGFeRDExportableTransaction in yourTransaction 
	   (which will require you to implement Product, Item and Contact)
	 then call
	 			doc = PDDocument.load(PDFfilename);
			// automatically add Zugferd to all outgoing invoices
			ZUGFeRDExporter ze = new ZUGFeRDExporter();
			ze.PDFmakeA3compliant(doc, "Your application name",
					System.getProperty("user.name"), true);
			ze.PDFattachZugferdFile(doc, yourTransaction);

			doc.save(PDFfilename);

	 * @author jstaerk
	 *
	 */

	


	
	//// MAIN CLASS
	
	private String conformanceLevel="U";
	private String versionStr="1.0";
	
	private String currencyFormat(BigDecimal value, char decimalDelimiter) {
		/*
		 * I needed 123,45, locale independent.I tried
		 * NumberFormat.getCurrencyInstance().format( 12345.6789 ); but that is
		 * locale specific.I also tried DecimalFormat df = new DecimalFormat(
		 * "0,00" ); df.setDecimalSeparatorAlwaysShown(true);
		 * df.setGroupingUsed(false); DecimalFormatSymbols symbols = new
		 * DecimalFormatSymbols(); symbols.setDecimalSeparator(',');
		 * symbols.setGroupingSeparator(' ');
		 * df.setDecimalFormatSymbols(symbols);
		 * 
		 * but that would not switch off grouping. Although I liked very much
		 * the (incomplete) "BNF diagram" in
		 * http://docs.oracle.com/javase/tutorial/i18n/format/decimalFormat.html
		 * in the end I decided to calculate myself and take eur+sparator+cents
		 * 
		 * This function will cut off, i.e. floor() subcent values Tests:
		 * System.err.println(utils.currencyFormat(new BigDecimal(0),
		 * ".")+"\n"+utils.currencyFormat(new BigDecimal("-1.10"),
		 * ",")+"\n"+utils.currencyFormat(new BigDecimal("-1.1"),
		 * ",")+"\n"+utils.currencyFormat(new BigDecimal("-1.01"),
		 * ",")+"\n"+utils.currencyFormat(new BigDecimal("20000123.3489"),
		 * ",")+"\n"+utils.currencyFormat(new BigDecimal("20000123.3419"),
		 * ",")+"\n"+utils.currencyFormat(new BigDecimal("12"), ","));
		 * 
		 * results 0.00 -1,10 -1,10 -1,01 20000123,34 20000123,34 12,00
		 */
		value=value.setScale( 2, BigDecimal.ROUND_HALF_UP ); // first, round so that e.g. 1.189999999999999946709294817992486059665679931640625 becomes 1.19  
		long totalCent = value.multiply(new BigDecimal(100)).intValue(); //now get the cents
		long eurOnly = value.longValue();
		long centOnly = Math.abs(totalCent % 100);
		StringBuffer res = new StringBuffer();
		res.append(eurOnly);
		res.append(decimalDelimiter);
		if (centOnly < 10) {
			res.append('0');
		}
		res.append(centOnly);
		return res.toString();
	}

	/**
	 	 All files are PDF/A-3, setConformance refers to the level conformance.
	 	 
	 	 PDF/A-3 has three coformance levels, called "A", "U" and "B". 

		 PDF/A-3-B where B means only visually
		 preservable, U -standard for Mustang- means visually and unicode
		 preservable and A means full compliance, i.e. visually,
		 unicode and structurally preservable and tagged PDF, i.e. useful metainformation for blind people.
		 
		 Feel free to pass "A" as new level if you know what you are doing :-)
		  

	 */
	public void setConformanceLevel(String newLevel) {
		conformanceLevel=newLevel;
	}
	
	
	/**
	 * Makes A PDF/A3a-compliant document from a PDF-A1 compliant document (on
	 * the metadata level, this will not e.g. convert graphics to JPG-2000)
	 * */
	public PDDocumentCatalog PDFmakeA3compliant(PDDocument doc, String producer, String creator, 
			boolean attachZugferdHeaders) throws IOException,
			TransformerException {
		String fullProducer=producer+"(via mustangproject.org "+versionStr+")";
		PDDocumentCatalog cat = doc.getDocumentCatalog();
		PDMetadata metadata = new PDMetadata(doc);
		cat.setMetadata(metadata);
		// we're using the jempbox org.apache.jempbox.xmp.XMPMetadata version,
		// not the xmpbox one
		XMPMetadata xmp = new XMPMetadata();

		XMPSchemaPDFAId pdfaid = new XMPSchemaPDFAId(xmp);
		pdfaid.setAbout(""); //$NON-NLS-1$
		xmp.addSchema(pdfaid);

		XMPSchemaDublinCore dc = xmp.addDublinCoreSchema();
		dc.addCreator(creator);
		dc.setAbout(""); //$NON-NLS-1$

		XMPSchemaBasic xsb = xmp.addBasicSchema();
		xsb.setAbout(""); //$NON-NLS-1$

		xsb.setCreatorTool(creator);
		xsb.setCreateDate(GregorianCalendar.getInstance());
		// PDDocumentInformation pdi=doc.getDocumentInformation();
		PDDocumentInformation pdi = new PDDocumentInformation();
		pdi.setProducer(fullProducer);
		pdi.setAuthor(creator);
		doc.setDocumentInformation(pdi);

		XMPSchemaPDF pdf = xmp.addPDFSchema();
		pdf.setProducer(fullProducer);
		pdf.setAbout(""); //$NON-NLS-1$

		/*
		// Mandatory: PDF/A3-a is tagged PDF which has to be expressed using a
		// MarkInfo dictionary (PDF A/3 Standard sec. 6.7.2.2)
		PDMarkInfo markinfo = new PDMarkInfo();
		markinfo.setMarked(true);
		doc.getDocumentCatalog().setMarkInfo(markinfo);
*/
/*
 * 	 
		To be on the safe side, we use level B without Markinfo because we can not 
		guarantee that the user  correctly tagged the templates for the PDF. 

 * */
		pdfaid.setConformance(conformanceLevel);//$NON-NLS-1$ //$NON-NLS-1$
		 
		pdfaid.setPart(3);

		if (attachZugferdHeaders) {
			addZugferdXMP(xmp); /*
								 * this is the only line where we do something
								 * Zugferd-specific, i.e. add PDF metadata
								 * specifically for Zugferd, not generically for
								 * a embedded file
								 */
		}

		metadata.importXMPMetadata(xmp);
		return cat;
	}
	
	private String getZugferdXMLForTransaction(IZUGFeRDExportableTransaction trans) {
		SimpleDateFormat zugferdDateFormat = new SimpleDateFormat("yyyyMMdd"); //$NON-NLS-1$
		String xml= "﻿<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" //$NON-NLS-1$
				
				+ "<rsm:CrossIndustryDocument xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xmlns:rsm=\"urn:ferd:CrossIndustryDocument:invoice:1p0\""
				+ " xsi:schemaLocation=\"urn:ferd:CrossIndustryDocument:invoice:1p0 ../Schema/ZUGFeRD_1p0.xsd\""
				+ " xmlns:ram=\"urn:un:unece:uncefact:data:standard:ReusableAggregateBusinessInformationEntity:12\""
				+ " xmlns:udt=\"urn:un:unece:uncefact:data:standard:UnqualifiedDataType:15\">\n" //$NON-NLS-1$
				+ "	<rsm:SpecifiedExchangedDocumentContext>\n" //$NON-NLS-1$
				+ "		<ram:TestIndicator><udt:Indicator>false</udt:Indicator></ram:TestIndicator>\n" //$NON-NLS-1$
				+ "		<ram:GuidelineSpecifiedDocumentContextParameter>\n" //$NON-NLS-1$
				+ "			<ram:ID>urn:ferd:invoice:rc:comfort</ram:ID>\n" //$NON-NLS-1$
				+ "		</ram:GuidelineSpecifiedDocumentContextParameter>\n" //$NON-NLS-1$
				+ "	</rsm:SpecifiedExchangedDocumentContext>\n" //$NON-NLS-1$
				+ "	<rsm:HeaderExchangedDocument>\n" //$NON-NLS-1$
				+ "		<ram:ID>"+trans.getNumber()+"</ram:ID>\n" //$NON-NLS-1$ //$NON-NLS-2$
				+ "		<ram:Name>RECHNUNG</ram:Name>\n" //$NON-NLS-1$
				+ "		<ram:TypeCode>380</ram:TypeCode>\n" //$NON-NLS-1$
				+ "		<ram:IssueDateTime><udt:DateTimeString format=\"102\">"+zugferdDateFormat.format(trans.getIssueDate())+"</udt:DateTimeString></ram:IssueDateTime>\n" //date format was 20130605 //$NON-NLS-1$ //$NON-NLS-2$
//				+ "		<IncludedNote>\n"
//				+ "			<Content>\n"
//				+ "Rechnung gemäß Bestellung Nr. 2013-471331 vom 01.03.2013.\n"
//				+ "\n"
//				+ "      </Content>\n"
//				+ "      </IncludedNote>\n"
//				+ "      <IncludedNote>\n"
//				+ "			<Content>\n"
//				+ "Es bestehen Rabatt- und Bonusvereinbarungen.\n"
//				+ "			</Content>\n"
//				+ "			<SubjectCode>AAK</SubjectCode>\n"
//				+ "		</IncludedNote>\n"
				+ "	</rsm:HeaderExchangedDocument>\n" //$NON-NLS-1$
				+ "	<rsm:SpecifiedSupplyChainTradeTransaction>\n" //$NON-NLS-1$
				+ "		<ram:ApplicableSupplyChainTradeAgreement>\n" //$NON-NLS-1$
//				+ "			<BuyerReference>AB-312</BuyerReference>\n"
				+ "			<ram:SellerTradeParty>\n" //$NON-NLS-1$
//				+ "				<GlobalID schemeID=\"0088\">4000001123452</GlobalID>\n"
				+ "				<ram:Name>"+trans.getOwnOrganisationName()+"</ram:Name>\n" //$NON-NLS-1$ //$NON-NLS-2$
				+ "				<ram:PostalTradeAddress>\n"
				+ "					<ram:PostcodeCode>"+trans.getOwnZIP()+"</ram:PostcodeCode>\n"
				+ "					<ram:LineOne>"+trans.getOwnStreet()+"</ram:LineOne>\n"
				+ "					<ram:CityName>"+trans.getOwnLocation()+"</ram:CityName>\n"
				+ "					<ram:CountryID>"+trans.getOwnCountry()+"</ram:CountryID>\n"
				+ "				</ram:PostalTradeAddress>\n"
				+ "				<ram:SpecifiedTaxRegistration>\n" //$NON-NLS-1$
				+ "					<ram:ID schemeID=\"FC\">"+trans.getOwnTaxID()+"</ram:ID>\n" //$NON-NLS-1$ //$NON-NLS-2$
				+ "				</ram:SpecifiedTaxRegistration>\n" //$NON-NLS-1$
				+ "				<ram:SpecifiedTaxRegistration>\n" //$NON-NLS-1$
				+ "					<ram:ID schemeID=\"VA\">"+trans.getOwnVATID()+"</ram:ID>\n" //$NON-NLS-1$ //$NON-NLS-2$
				+ "				</ram:SpecifiedTaxRegistration>\n" //$NON-NLS-1$
				+ "			</ram:SellerTradeParty>\n" //$NON-NLS-1$
				+ "			<ram:BuyerTradeParty>\n" //$NON-NLS-1$
//				+ "				<ID>GE2020211</ID>\n"
//				+ "				<GlobalID schemeID=\"0088\">4000001987658</GlobalID>\n"
				+ "				<ram:Name>"+trans.getRecipient().getName()+"</ram:Name>\n" //$NON-NLS-1$ //$NON-NLS-2$
//				+ "				<DefinedTradeContact>\n"
//				+ "					<PersonName>xxx</PersonName>\n"
//				+ "				</DefinedTradeContact>\n"
				+ "				<ram:PostalTradeAddress>\n" //$NON-NLS-1$
				+ "					<ram:PostcodeCode>"+trans.getRecipient().getZIP()+"</ram:PostcodeCode>\n" //$NON-NLS-1$ //$NON-NLS-2$
				+ "					<ram:LineOne>"+trans.getRecipient().getStreet()+"</ram:LineOne>\n" //$NON-NLS-1$ //$NON-NLS-2$
				+ "					<ram:CityName>"+trans.getRecipient().getLocation()+"</ram:CityName>\n" //$NON-NLS-1$ //$NON-NLS-2$
				+ "					<ram:CountryID>"+trans.getRecipient().getCountry()+"</ram:CountryID>\n" //$NON-NLS-1$ //$NON-NLS-2$
				+ "				</ram:PostalTradeAddress>\n" //$NON-NLS-1$
				+ "				<ram:SpecifiedTaxRegistration>\n" //$NON-NLS-1$
				+ "					<ram:ID schemeID=\"VA\">"+trans.getRecipient().getVATID()+"</ram:ID>\n" //$NON-NLS-1$ //$NON-NLS-2$
				+ "				</ram:SpecifiedTaxRegistration>\n" //$NON-NLS-1$
				+ "			</ram:BuyerTradeParty>\n" //$NON-NLS-1$
//				+ "			<BuyerOrderReferencedDocument>\n"
//				+ "				<IssueDateTime format=\"102\">20130301</IssueDateTime>\n"
//				+ "			<ID>2013-471331</ID>\n"
//				+ "			</BuyerOrderReferencedDocument>\n"
				+ "		</ram:ApplicableSupplyChainTradeAgreement>\n" //$NON-NLS-1$
				+ "		<ram:ApplicableSupplyChainTradeDelivery>\n"
				+ "			<ram:ActualDeliverySupplyChainEvent>\n"
				+ "				<ram:OccurrenceDateTime><udt:DateTimeString format=\"102\">"+zugferdDateFormat.format(trans.getDeliveryDate())+"</udt:DateTimeString></ram:OccurrenceDateTime>\n"
				+ "			</ram:ActualDeliverySupplyChainEvent>\n"
				/*
				+ "			<DeliveryNoteReferencedDocument>\n"
				+ "				<IssueDateTime format=\"102\">20130603</IssueDateTime>\n"
				+ "				<ID>2013-51112</ID>\n"
				+ "			</DeliveryNoteReferencedDocument>\n" */
				+ "		</ram:ApplicableSupplyChainTradeDelivery>\n"
				+ "		<ram:ApplicableSupplyChainTradeSettlement>\n" //$NON-NLS-1$
				+ "			<ram:PaymentReference>"+trans.getNumber()+"</ram:PaymentReference>\n" //$NON-NLS-1$ //$NON-NLS-2$
				+ "			<ram:InvoiceCurrencyCode>EUR</ram:InvoiceCurrencyCode>\n" //$NON-NLS-1$
				+ "			<ram:SpecifiedTradeSettlementPaymentMeans>\n" //$NON-NLS-1$
				+ "				<ram:TypeCode>42</ram:TypeCode>\n" //$NON-NLS-1$
				+ "				<ram:Information>Überweisung</ram:Information>\n" //$NON-NLS-1$
				+ "				<ram:PayeePartyCreditorFinancialAccount>\n" //$NON-NLS-1$
				+ "					<ram:IBANID>"+trans.getOwnIBAN()+"</ram:IBANID>\n" //$NON-NLS-1$ //$NON-NLS-2$
				+ "				</ram:PayeePartyCreditorFinancialAccount>\n" //$NON-NLS-1$
				+ "				<ram:PayeeSpecifiedCreditorFinancialInstitution>\n" //$NON-NLS-1$
				+ "					<ram:BICID>"+trans.getOwnBIC()+"</ram:BICID>\n" //$NON-NLS-1$ //$NON-NLS-2$
				+ "					<ram:Name>"+trans.getOwnBankName()+"</ram:Name>\n" //$NON-NLS-1$ //$NON-NLS-2$
				+ "				</ram:PayeeSpecifiedCreditorFinancialInstitution>\n" //$NON-NLS-1$
				+ "			</ram:SpecifiedTradeSettlementPaymentMeans>\n"; //$NON-NLS-1$
				
		
		/*
		HashMap<BigDecimal, BigDecimal> VATPercentAmountMap=trans.getVATPercentAmountMap();
		for (BigDecimal currentTaxPercent : VATPercentAmountMap.keySet()) {
			BigDecimal amount = VATPercentAmountMap.get(currentTaxPercent);
			if (amount != null) {
				xml += "			<ApplicableTradeTax>\n" //$NON-NLS-1$
								+ "				<CalculatedAmount currencyID=\"EUR\">"+currencyFormat(amount, '.')+"</CalculatedAmount>\n" //$NON-NLS-1$ //$NON-NLS-2$
								+ "				<TypeCode>VAT</TypeCode>\n" //$NON-NLS-1$
//								+ "				<BasisAmount currencyID=\"EUR\">129.37</BasisAmount>\n"
								+ "				<CategoryCode>S</CategoryCode>\n" //$NON-NLS-1$
								+ "				<ApplicablePercent>"+currentTaxPercent+"</ApplicablePercent>\n" //$NON-NLS-1$
								+ "			</ApplicableTradeTax>\n"; //$NON-NLS-1$


	
			}
		}*/
/*				xml+= "
				+ "			<SpecifiedTradeAllowanceCharge>\n"
				+ "				<ChargeIndicator>false</ChargeIndicator>\n"
				+ "				<BasisAmount currencyID=\"EUR\">10</BasisAmount>\n"
				+ "				<ActualAmount>1.00</ActualAmount>\n"
				+ "				<Reason>Sondernachlass</Reason>\n"
				+ "				<CategoryTradeTax>\n"
				+ "					<TypeCode>VAT</TypeCode>\n"
				+ "					<CategoryCode>S</CategoryCode>\n"
				+ "					<ApplicablePercent>19</ApplicablePercent>\n"
				+ "				</CategoryTradeTax>\n"
				+ "			</SpecifiedTradeAllowanceCharge>\n"
				+ "			<SpecifiedTradeAllowanceCharge>\n"
				+ "				<ChargeIndicator>false</ChargeIndicator>\n"
				+ "				<BasisAmount currencyID=\"EUR\">137.30</BasisAmount>\n"
				+ "				<ActualAmount>13.73</ActualAmount>\n"
				+ "				<Reason>Sondernachlass</Reason>\n"
				+ "				<CategoryTradeTax>\n"
				+ "					<TypeCode>VAT</TypeCode>\n"
				+ "					<CategoryCode>S</CategoryCode>\n"
				+ "					<ApplicablePercent>7</ApplicablePercent>\n"
				+ "				</CategoryTradeTax>\n"
				+ "							</SpecifiedTradeAllowanceCharge>\n"
				+ "			<SpecifiedLogisticsServiceCharge>\n"
				+ "				<Description>Versandkosten</Description>\n"
				+ "				<AppliedAmount>5.80</AppliedAmount>\n"
				+ "				<AppliedTradeTax>\n"
				+ "					<TypeCode>VAT</TypeCode>\n"
				+ "					<CategoryCode>S</CategoryCode>\n"
				+ "					<ApplicablePercent>7</ApplicablePercent>\n"
				+ "				</AppliedTradeTax>\n"
				+ "			</SpecifiedLogisticsServiceCharge>\n"*/
		
				xml=xml+ "			<ram:SpecifiedTradePaymentTerms>\n" //$NON-NLS-1$
//				+ "				<Description>Zahlbar innerhalb 30 Tagen netto bis 04.07.2013, 3% Skonto innerhalb 10 Tagen bis 15.06.2013</Description>\n"
				+ "				<ram:DueDateDateTime><udt:DateTimeString format=\"102\">"+zugferdDateFormat.format(trans.getDueDate())+"</udt:DateTimeString></ram:DueDateDateTime>\n"//20130704 //$NON-NLS-1$ //$NON-NLS-2$
				+ "			</ram:SpecifiedTradePaymentTerms>\n" //$NON-NLS-1$
				+ "			<ram:SpecifiedTradeSettlementMonetarySummation>\n" //$NON-NLS-1$
				+ "				<ram:LineTotalAmount currencyID=\"EUR\">"+currencyFormat(trans.getTotal(), '.')+"</ram:LineTotalAmount>\n" //$NON-NLS-1$ //$NON-NLS-2$
//				+ "				<ChargeTotalAmount currencyID=\"EUR\">5.80</ChargeTotalAmount>\n"
//				+ "				<AllowanceTotalAmount currencyID=\"EUR\">14.73</AllowanceTotalAmount>\n"
				+ "				<ram:TaxBasisTotalAmount currencyID=\"EUR\">"+currencyFormat(trans.getTotal(), '.')+"</ram:TaxBasisTotalAmount>\n" //$NON-NLS-1$ //$NON-NLS-2$
				+ "				<ram:TaxTotalAmount currencyID=\"EUR\">"+currencyFormat(trans.getTotalGross().subtract(trans.getTotal()), '.')+"</ram:TaxTotalAmount>\n" //$NON-NLS-1$ //$NON-NLS-2$
				+ "				<ram:GrandTotalAmount currencyID=\"EUR\">"+currencyFormat(trans.getTotalGross(), '.')+"</ram:GrandTotalAmount>\n" //$NON-NLS-1$ //$NON-NLS-2$
//				+ "				<TotalPrepaidAmount currencyID=\"EUR\">0.00</TotalPrepaidAmount>\n"
				+ "				<ram:DuePayableAmount currencyID=\"EUR\">"+currencyFormat(trans.getTotalGross(), '.')+"</ram:DuePayableAmount>\n" //$NON-NLS-1$ //$NON-NLS-2$
				+ "			</ram:SpecifiedTradeSettlementMonetarySummation>\n" //$NON-NLS-1$
				+ "		</ram:ApplicableSupplyChainTradeSettlement>\n"; //$NON-NLS-1$
//				+ "		<IncludedSupplyChainTradeLineItem>\n"
//				+ "			<AssociatedDocumentLineDocument>\n"
//				+ "				<IncludedNote>\n"
//				+ "					<Content>Wir erlauben uns Ihnen folgende Positionen aus der Lieferung Nr. 2013-51112 in Rechnung zu stellen:</Content>\n"
//				+ "				</IncludedNote>\n"
//				+ "			</AssociatedDocumentLineDocument>\n"
//				+ "		</IncludedSupplyChainTradeLineItem>\n";
				
				
				int lineID=0;
				for (IZUGFeRDExportableItem currentItem : trans.getZFItems()) {
					lineID++;
					xml=xml+ "		<ram:IncludedSupplyChainTradeLineItem>\n"+ //$NON-NLS-1$
					"			<ram:AssociatedDocumentLineDocument>\n" //$NON-NLS-1$
							+ "				<ram:LineID>"+lineID+"</ram:LineID>\n" //$NON-NLS-1$ //$NON-NLS-2$
							+ "			</ram:AssociatedDocumentLineDocument>\n" //$NON-NLS-1$
							
							+ "			<ram:SpecifiedSupplyChainTradeAgreement>\n" //$NON-NLS-1$
							+ "				<ram:GrossPriceProductTradePrice>\n" //$NON-NLS-1$
							+ "					<ram:ChargeAmount currencyID=\"EUR\">"+currencyFormat(currentItem.getPriceGross(),'.')+"</ram:ChargeAmount>\n" //$NON-NLS-1$ //$NON-NLS-2$
							+ "					<ram:BasisQuantity unitCode=\""+currentItem.getProduct().getUnit()+"\">1</ram:BasisQuantity>\n" //$NON-NLS-1$ //$NON-NLS-2$
//							+ "					<AppliedTradeAllowanceCharge>\n"
//							+ "						<ChargeIndicator>false</ChargeIndicator>\n"
//							+ "						<ActualAmount currencyID=\"EUR\">0.6667</ActualAmount>\n"
//							+ "						<Reason>Rabatt</Reason>\n"
//							+ "					</AppliedTradeAllowanceCharge>\n"
							+ "				</ram:GrossPriceProductTradePrice>\n" //$NON-NLS-1$
							+ "				<ram:NetPriceProductTradePrice>\n" //$NON-NLS-1$
							+ "					<ram:ChargeAmount currencyID=\"EUR\">"+currencyFormat(currentItem.getPrice(),'.')+"</ram:ChargeAmount>\n" //$NON-NLS-1$ //$NON-NLS-2$
							+ "					<ram:BasisQuantity unitCode=\""+currentItem.getProduct().getUnit()+"\">1</ram:BasisQuantity>\n" //$NON-NLS-1$ //$NON-NLS-2$
							+ "				</ram:NetPriceProductTradePrice>\n" //$NON-NLS-1$
							+ "			</ram:SpecifiedSupplyChainTradeAgreement>\n" //$NON-NLS-1$
							
							+ "			<ram:SpecifiedSupplyChainTradeDelivery>\n" //$NON-NLS-1$
							+ "				<ram:BilledQuantity unitCode=\""+currentItem.getProduct().getUnit()+"\">"+currentItem.getQuantity()+"</ram:BilledQuantity>\n" //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
							+ "			</ram:SpecifiedSupplyChainTradeDelivery>\n" //$NON-NLS-1$
							+ "			<ram:SpecifiedSupplyChainTradeSettlement>\n" //$NON-NLS-1$
							+ "				<ram:ApplicableTradeTax>\n" //$NON-NLS-1$
							+ "					<ram:TypeCode>VAT</ram:TypeCode>\n" //$NON-NLS-1$
							+ "					<ram:CategoryCode>S</ram:CategoryCode>\n" //$NON-NLS-1$
							+ "					<ram:ApplicablePercent>"+currentItem.getProduct().getVATPercent()+"</ram:ApplicablePercent>\n" //$NON-NLS-1$ //$NON-NLS-2$
							+ "				</ram:ApplicableTradeTax>\n" //$NON-NLS-1$
							+ "				<ram:SpecifiedTradeSettlementMonetarySummation>\n" //$NON-NLS-1$
							+ "					<ram:LineTotalAmount currencyID=\"EUR\">"+currencyFormat(currentItem.getTotalGross(),'.')+"</ram:LineTotalAmount>\n" //$NON-NLS-1$ //$NON-NLS-2$
							+ "				</ram:SpecifiedTradeSettlementMonetarySummation>\n" //$NON-NLS-1$
							+ "			</ram:SpecifiedSupplyChainTradeSettlement>\n" //$NON-NLS-1$
							+ "			<ram:SpecifiedTradeProduct>\n" //$NON-NLS-1$
//							+ "				<GlobalID schemeID=\"0160\">4012345001235</GlobalID>\n"
//							+ "				<SellerAssignedID>KR3M</SellerAssignedID>\n"
//							+ "				<BuyerAssignedID>55T01</BuyerAssignedID>\n"
							+ "				<ram:Name>"+currentItem.getProduct().getName()+"</ram:Name>\n" //$NON-NLS-1$ //$NON-NLS-2$
							+ "				<ram:Description>"+currentItem.getProduct().getDescription()+"</ram:Description>\n" //$NON-NLS-1$ //$NON-NLS-2$
							+ "			</ram:SpecifiedTradeProduct>\n" //$NON-NLS-1$
							+ "		</ram:IncludedSupplyChainTradeLineItem>\n"; //$NON-NLS-1$
							
								
					
				}
				
				
				xml=xml	+ "	</rsm:SpecifiedSupplyChainTradeTransaction>\n" //$NON-NLS-1$
				+ "</rsm:CrossIndustryDocument>"; //$NON-NLS-1$
				return xml;
	}

	/**
	 * embed the Zugferd XML structure in a file named ZUGFeRD-invoice.xml
	 * */
	public void PDFattachZugferdFile(PDDocument doc, IZUGFeRDExportableTransaction trans) throws IOException {

		// embedded files are stored in a named tree
		PDEmbeddedFilesNameTreeNode efTree = new PDEmbeddedFilesNameTreeNode();

		String filename="ZUGFeRD-invoice.xml"; //$NON-NLS-1$
		// first create the file specification, which holds the embedded file
		PDComplexFileSpecification fs = new PDComplexFileSpecification();
		fs.setFile(filename);

		COSDictionary dict = fs.getCOSDictionary();
		// Relation "Source" for linking with eg. catalog
		dict.setName("AFRelationship", "Alternative"); // as defined in Zugferd standard //$NON-NLS-1$ //$NON-NLS-2$

		dict.setString("UF", filename); //$NON-NLS-1$

		// create a dummy file stream, this would probably normally be a
		// FileInputStream
		
		byte[] zugferdRaw = getZugferdXMLForTransaction(trans).getBytes("UTF-8"); //$NON-NLS-1$
		  
		byte[] zugferdData;
		
        
        		
		if ((zugferdRaw[0]==(byte)0xEF)&&(zugferdRaw[1]==(byte)0xBB)&&(zugferdRaw[2]==(byte)0xBF)) {
			// I don't like BOMs, lets remove it
			zugferdData=new byte[zugferdRaw.length-3];
			System.arraycopy(zugferdRaw,3,zugferdData,0,zugferdRaw.length-3);
		}	else {
			zugferdData=zugferdRaw;			
		}
		  
		ByteArrayInputStream fakeFile = new ByteArrayInputStream(zugferdData);
		PDEmbeddedFile ef = new PDEmbeddedFile(doc, fakeFile);
		// now lets some of the optional parameters
		ef.setSubtype("text/xml");// as defined in Zugferd standard //$NON-NLS-1$
		ef.setSize(zugferdData.length);
		ef.setCreationDate(new GregorianCalendar());

		ef.setModDate(GregorianCalendar.getInstance());

		fs.setEmbeddedFile(ef);

		// now add the entry to the embedded file tree and set in the document.
		efTree.setNames(Collections.singletonMap(filename, fs));
		PDDocumentNameDictionary names = new PDDocumentNameDictionary(
				doc.getDocumentCatalog());
		names.setEmbeddedFiles(efTree);
		doc.getDocumentCatalog().setNames(names);
		// AF entry (Array) in catalog with the FileSpec
		COSArray cosArray = new COSArray();
		cosArray.add(fs);
		doc.getDocumentCatalog().getCOSDictionary().setItem("AF", cosArray); //$NON-NLS-1$

	}

/***
 * This will add both the RDF-indication which embedded file is Zugferd and the 
 * neccessary PDF/A schema extension description to be able to add this information to RDF 
 * @param metadata
 */
	private void addZugferdXMP(XMPMetadata metadata) {

		XMPSchemaZugferd zf = new XMPSchemaZugferd(metadata);
		zf.setAbout(""); //$NON-NLS-1$
		metadata.addSchema(zf);

		XMPSchemaPDFAExtensions pdfaex = new XMPSchemaPDFAExtensions(metadata);
		pdfaex.setAbout(""); //$NON-NLS-1$
		metadata.addSchema(pdfaex);

	}

}
