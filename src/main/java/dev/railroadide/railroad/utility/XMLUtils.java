package dev.railroadide.railroad.utility;

import com.google.gson.JsonObject;
import dev.railroadide.railroad.Railroad;
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

/**
 * Utility class for parsing XML files and converting them to JSON.
 */
public final class XMLUtils {
    private static final DocumentBuilder DOCUMENT_BUILDER;

    static {
        try {
            DOCUMENT_BUILDER = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        } catch (ParserConfigurationException exception) {
            throw new RuntimeException("Failed to create xml document builder", exception);
        }
    }

    private XMLUtils() {
        throw new UnsupportedOperationException("Instantiated utility class");
    }

    /**
     * Parses an XML string and returns a Document object.
     *
     * @param xml the XML string to parse
     * @return the parsed Document object
     */
    public static Document parseXML(String xml) {
        try {
            Document document = DOCUMENT_BUILDER.parse(new InputSource(new StringReader(xml)));
            document.getDocumentElement().normalize();

            return document;
        } catch (IOException | SAXException exception) {
            throw new RuntimeException("Failed to parse xml", exception);
        }
    }

    /**
     * Parses an XML document from a URL and returns a Document object.
     *
     * @param url the URL of the XML document to parse
     * @return the parsed Document object
     */
    public static Document parseFromURL(String url) {
        try {
            return DOCUMENT_BUILDER.parse(url);
        } catch (IOException | SAXException exception) {
            throw new RuntimeException("Failed to parse xml from url", exception);
        }
    }

    /**
     * Converts an XML string to a JSON object of the specified type.
     *
     * @param xml the XML string to convert
     * @param type the class of the JSON object to return
     * @param <T> the type of the JSON object
     * @return the converted JSON object
     */
    public static <T> T xmlToJson(String xml, Class<T> type) {
        return Railroad.GSON.fromJson(XML.toJSONObject(xml).toString(), type);
    }

    /**
     * Converts an XML string to a JSON object.
     *
     * @param xml the XML string to convert
     * @return the converted JSON object
     */
    public static JsonObject xmlToJson(String xml) {
        return xmlToJson(xml, JsonObject.class);
    }

    /**
     * Converts an XML file to a JSON object of the specified type.
     *
     * @param file the XML file to convert
     * @param type the class of the JSON object to return
     * @param <T> the type of the JSON object
     * @return the converted JSON object
     */
    public static <T> T xmlToJson(File file, Class<T> type) {
        return xmlToJson(file.toPath(), type);
    }

    /**
     * Converts an XML file to a JSON object of the specified type.
     *
     * @param path the path to the XML file to convert
     * @param type the class of the JSON object to return
     * @param <T> the type of the JSON object
     * @return the converted JSON object
     */
    public static <T> T xmlToJson(Path path, Class<T> type) {
        try {
            return xmlToJson(Files.readString(path), type);
        } catch (IOException exception) {
            throw new RuntimeException("Failed to read file", exception);
        }
    }
}
