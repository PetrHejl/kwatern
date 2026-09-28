package me.hejl.gramps.xml;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

/**
 * Writes a Gramps XML document that uses every element of the RELAX NG schema, each with all of its
 * attributes. Optional and repeated content appears once, and every alternative of a choice is included,
 * so the result exercises the whole vocabulary rather than being a realistic tree.
 */
final class SchemaSample {

    private static final String RNG = "http://relaxng.org/ns/structure/1.0";
    private static final int MAX_DEPTH = 30;

    private final Map<String, Element> defines = new HashMap<>();
    private final Set<String> vocabulary = new TreeSet<>();
    private int ids;

    /** The sample document and the vocabulary it covers ({@code element} and {@code element@attribute}). */
    record Sample(String xml, Set<String> vocabulary) {}

    static Sample generate(Path schema) throws IOException {
        try {
            var factory = DocumentBuilderFactory.newDefaultNSInstance();
            Element grammar =
                    factory.newDocumentBuilder().parse(schema.toFile()).getDocumentElement();
            return new SchemaSample().generate(grammar);
        } catch (ParserConfigurationException | SAXException e) {
            throw new IOException("Cannot read schema " + schema, e);
        }
    }

    private Sample generate(Element grammar) {
        for (Element define : children(grammar, "define")) {
            defines.put(define.getAttribute("name"), define);
        }
        Element root =
                children(children(grammar, "start").getFirst(), "element").getFirst();
        var xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        element(root, xml, 0, " xmlns=\"" + grammar.getAttribute("ns") + "\"");
        return new Sample(xml.toString(), vocabulary);
    }

    private void element(Element element, StringBuilder xml, int depth, String extra) {
        String name = element.getAttribute("name");
        vocabulary.add(name);
        Map<String, String> attributes = new LinkedHashMap<>();
        attributes(element, attributes);
        xml.append('<').append(name).append(extra);
        attributes.forEach((attribute, value) -> {
            vocabulary.add(name + "@" + attribute);
            xml.append(' ').append(attribute).append("=\"").append(value).append('"');
        });
        xml.append('>');
        if (depth < MAX_DEPTH) {
            content(element, xml, depth);
        }
        xml.append("</").append(name).append(">\n");
    }

    /** Collects the attributes of an element pattern, not descending into child elements. */
    private void attributes(Element pattern, Map<String, String> attributes) {
        for (Element child : children(pattern, null)) {
            switch (child.getLocalName()) {
                case "attribute" -> attributes.put(child.getAttribute("name"), value(child));
                case "element", "text", "data", "value", "empty" -> {}
                case "ref" -> attributes(define(child), attributes);
                default -> attributes(child, attributes);
            }
        }
    }

    /** Writes the child elements and text of an element pattern. */
    private void content(Element pattern, StringBuilder xml, int depth) {
        for (Element child : children(pattern, null)) {
            switch (child.getLocalName()) {
                case "element" -> element(child, xml, depth + 1, "");
                case "attribute", "empty" -> {}
                case "ref" -> content(define(child), xml, depth);
                case "text" -> xml.append('1');
                case "data" -> xml.append(sample(child.getAttribute("type")));
                case "value" -> xml.append(child.getTextContent().strip());
                case "choice" -> {
                    List<Element> alternatives = children(child, null);
                    if (alternatives.stream().allMatch(a -> a.getLocalName().equals("value"))) {
                        xml.append(alternatives.getFirst().getTextContent().strip());
                    } else {
                        content(child, xml, depth);
                    }
                }
                default -> content(child, xml, depth);
            }
        }
    }

    /** A plausible value for an attribute: its first allowed value, or one matching its data type. */
    private String value(Element attribute) {
        Element value = first(attribute, "value");
        if (value != null) {
            return value.getTextContent().strip();
        }
        Element data = first(attribute, "data");
        if (data != null) {
            return sample(data.getAttribute("type"));
        }
        return switch (attribute.getAttribute("name")) {
            case "val", "start", "stop" -> "1850";
            case "cformat" -> "Julian";
            default -> "1";
        };
    }

    private String sample(String type) {
        return switch (type) {
            case "ID" -> "_id" + ++ids;
            case "IDREF" -> "_ref";
            case "date" -> "2000-01-01";
            default -> "1";
        };
    }

    /** The first descendant with the given name, following references. */
    private Element first(Element pattern, String name) {
        for (Element child : children(pattern, null)) {
            if (child.getLocalName().equals(name)) {
                return child;
            }
            Element found = first(child.getLocalName().equals("ref") ? define(child) : child, name);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private Element define(Element ref) {
        Element define = defines.get(ref.getAttribute("name"));
        if (define == null) {
            throw new IllegalStateException("Undefined pattern " + ref.getAttribute("name"));
        }
        return define;
    }

    /** Child elements in the RELAX NG namespace, optionally only those with the given name. */
    private static List<Element> children(Node parent, String name) {
        List<Element> result = new ArrayList<>();
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element e
                    && RNG.equals(e.getNamespaceURI())
                    && (name == null || name.equals(e.getLocalName()))) {
                result.add(e);
            }
        }
        return result;
    }
}
