package io.github.railroad.utility;

import com.google.gson.JsonObject;
import io.github.railroad.Railroad;
import org.json.XML;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;

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

    public static <T> T xmlToJson(String xml, Class<T> type) {
        return Railroad.GSON.fromJson(XML.toJSONObject(xml).toString(), type);
    }

    public static JsonObject xmlToJson(String xml) {
        return xmlToJson(xml, JsonObject.class);
    }

    public static <T> T xmlToJson(File file, Class<T> type) {
        return xmlToJson(file.toPath(), type);
    }

    public static <T> T xmlToJson(Path path, Class<T> type) {
        try {
            return xmlToJson(Files.readString(path), type);
        } catch (IOException exception) {
            throw new RuntimeException("Failed to read file", exception);
        }
    }
}
