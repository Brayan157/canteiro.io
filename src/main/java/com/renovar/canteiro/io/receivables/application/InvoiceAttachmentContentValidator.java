package com.renovar.canteiro.io.receivables.application;

import com.renovar.canteiro.io.receivables.domain.InvoiceAttachmentType;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.XMLConstants;
import javax.xml.parsers.SAXParserFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;

/** Structural validation only: not fiscal authorization, signature verification or a malware scanner. */
final class InvoiceAttachmentContentValidator {
    private InvoiceAttachmentContentValidator() { }

    static void validate(InvoiceAttachmentType type, byte[] content) {
        try {
            if (type == InvoiceAttachmentType.PDF) validatePdf(content);
            else validateXml(content);
        } catch (IOException | SAXException exception) {
            // Never propagate parser messages, file contents, paths or external identifiers to the API/logs.
            throw new IllegalArgumentException("Invoice attachment content is not a supported valid " + type.name() + " document");
        }
    }

    private static void validatePdf(byte[] content) throws IOException {
        if (content.length < 5 || content[0] != '%' || content[1] != 'P' || content[2] != 'D'
                || content[3] != 'F' || content[4] != '-') {
            throw new IOException("Invalid PDF signature");
        }
        try (PDDocument document = Loader.loadPDF(content)) {
            if (document.isEncrypted() || document.getNumberOfPages() < 1) {
                throw new IOException("Unsupported PDF");
            }
        }
    }

    private static void validateXml(byte[] content) throws IOException, SAXException {
        try {
            SAXParserFactory factory = SAXParserFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setXIncludeAware(false);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            var reader = factory.newSAXParser().getXMLReader();
            reader.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            reader.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            DefaultHandler handler = new DefaultHandler() {
                private int depth;
                private int elements;

                @Override
                public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
                    if (++depth > 128 || ++elements > 100_000 || attributes.getLength() > 100) {
                        throw new SAXException("XML structural limit exceeded");
                    }
                    if ("http://www.w3.org/2001/XInclude".equals(uri)) throw new SAXException("XInclude is not supported");
                }

                @Override
                public void endElement(String uri, String localName, String qName) { depth--; }

                @Override
                public void error(org.xml.sax.SAXParseException exception) throws SAXException { throw exception; }

                @Override
                public void fatalError(org.xml.sax.SAXParseException exception) throws SAXException { throw exception; }
            };
            reader.setEntityResolver((publicId, systemId) -> { throw new SAXException("External entities are not supported"); });
            reader.setContentHandler(handler);
            reader.setErrorHandler(handler);
            reader.parse(new InputSource(new ByteArrayInputStream(content)));
        } catch (javax.xml.parsers.ParserConfigurationException exception) {
            throw new IllegalStateException("Secure XML validation is unavailable");
        }
    }
}
