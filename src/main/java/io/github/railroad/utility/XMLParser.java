package io.github.railroad.utility;

import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.StringReader;

public class XMLParser {
    private static final DocumentBuilder DOCUMENT_BUILDER;

    static {
        try {
            DOCUMENT_BUILDER = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        } catch (ParserConfigurationException exception) {
            throw new RuntimeException("Failed to create xml document builder", exception);
        }
    }

    public static Document parseXML(String xml) {
        try {
            Document document = DOCUMENT_BUILDER.parse(new InputSource(new StringReader(xml)));
            document.getDocumentElement().normalize();

            return document;
        } catch (IOException | SAXException exception) {
            throw new RuntimeException("Failed to parse xml", exception);
        }
    }

    public static Document parseFromURL(String url) {
        try {
            return DOCUMENT_BUILDER.parse(url);
        } catch (IOException | SAXException exception) {
            throw new RuntimeException("Failed to parse xml from url", exception);
        }
    }
}
