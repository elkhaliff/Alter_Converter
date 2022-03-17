package converter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class Node {
    private String name;
    private Node parent;
    private String value;

    private final Map<String, String> attributes = new LinkedHashMap<>();
    private final List<Node> children = new ArrayList<>();

    public Node() {
        this(null);
    }

    public Node(String name) {
        this(name, null);
    }

    public Node(String name, Node parent) {
        this.name = name;
        this.parent = parent;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Node getParent() {
        return parent;
    }

    public void setAttribute(String key, String value) {
        attributes.put(key, value);
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public Node addChild(String name) {
        return addChild(new Node(name));
    }

    public Node addChild(Node child) {
        child.setParent(this);
        children.add(child);
        return child;
    }

    public Node removeChild(Node child) {
        int i = children.indexOf(child);
        if (i < 0) {
            return null;
        }
        child.setParent(null);
        return children.remove(i);
    }

    public void setParent(Node parent) {
        this.parent = parent;
    }

    public String getPath() {
        StringBuilder path = new StringBuilder();
        Node node = this;
        boolean first = true;
        while (node != null) {
            if (node.getName() != null) {
                if (first) {
                    first = false;
                } else {
                    path.insert(0, ", ");
                }
                path.insert(0, node.getName());
            }
            node = node.getParent();
        }
        return path.toString();
    }

    public boolean hasChildren() {
        return !children.isEmpty();
    }

    public Map<String, Node> getChildrenMap() {
        Map<String, Node> map = new LinkedHashMap<>();
        for (Node child : children) {
            map.put(child.getName(), child);
        }
        return map;
    }

    @Override
    public String toString() {
        StringBuilder out = new StringBuilder();

        if (getName() != null) {
            out.append(String.format("Element:\npath = %s\n", getPath()));

            if (value == null) {
                if (children.isEmpty()) {
                    out.append("value = null\n");
                }
            } else {
                out.append(String.format("value = \"%s\"\n", value));
            }

            if (!attributes.isEmpty()) {
                out.append("attributes:\n");
                for (Map.Entry<String, String> attr : attributes.entrySet()) {
                    out.append(String.format("%s = \"%s\"\n", attr.getKey(), attr.getValue() == null ? "" : attr.getValue()));
                }
            }
        }

        for (Node child : children) {
            out.append("\n").append(child);
        }

        return out.toString();
    }
}

class XmlReader {
    private static final Pattern PATTERN_XML_BEGINNING = Pattern.compile("(?s)\\A\\s*<\\s*[a-z_]\\w+");
    private static final Pattern PATTERN_OPENING_TAG = Pattern.compile("(?is)\\s*<\\s*([a-z_]\\w+)\\s*([a-z_]\\w+\\s*=\\s*\".*?\")*\\s*(>|/>)");
    private static final Pattern PATTERN_ATTRIBUTE = Pattern.compile("(?is)([a-z_]\\w+)\\s*=\\s*\"(.*?)\"");

    public static Node read(String src) {
        Node root = new Node();
        readTags(src, root, 0);
        return root;
    }

    private static int readTags(String src, Node parent, int start) {
        Matcher tagMatcher = PATTERN_OPENING_TAG.matcher(src);
        Matcher attrMatcher;
        Matcher enclosingTagMatcher;
        Node element;

        int i = start;
        while (tagMatcher.find(i)) {
            element = parent.addChild(tagMatcher.group(1));

            if (tagMatcher.group(2) != null) {
                attrMatcher = PATTERN_ATTRIBUTE.matcher(tagMatcher.group(2));
                while (attrMatcher.find()) {
                    element.setAttribute(attrMatcher.group(1), attrMatcher.group(2));
                }
            }

            i = tagMatcher.end();
            if (">".equals(tagMatcher.group(3))) {
                enclosingTagMatcher = Pattern
                        .compile(String.format("(?s)(.*?)<\\s*\\/%s\\s*>", element.getName()))
                        .matcher(src);

                if (isXml(src, i)) {
                    i = readTags(src, element, i);
                }

                if (!enclosingTagMatcher.find(i)) {
                    throw new RuntimeException("Enclosing tag expected.");
                }

                if (!element.hasChildren()) {
                    element.setValue(enclosingTagMatcher.group(1));
                }

                i = enclosingTagMatcher.end();
            }
        }

        return i;
    }

    public static boolean isXml(String src) {
        return isXml(src, 0);
    }

    private static boolean isXml(String src, int start) {
        return PATTERN_XML_BEGINNING.matcher(src.substring(start)).find();
    }
}

class JsonReader {
    private static final Pattern PATTERN_JSON_BEGINNING = Pattern.compile("(?s)^\\s*\\{\\s*[\"}]");
    private static final Pattern PATTERN_JSON_OBJECT_OPEN = Pattern.compile("(?s)^\\s*\\{\\s*");
    private static final Pattern PATTERN_JSON_OBJECT_CLOSE = Pattern.compile("(?s)^\\s*}\\s*,?");
    private static final Pattern PATTERN_JSON_OBJECT_ATTR_NAME = Pattern.compile("(?s)^\\s*\"(.*?)\"\\s*:\\s*");
    private static final Pattern PATTERN_JSON_OBJECT_ATTR_VALUE = Pattern.compile("(?s)^\\s*(\"(.*?)\"|(\\d+\\.?\\d*)|(null)),?");

    private static final Pattern PATTERN_XML_ATTRIBUTE = Pattern.compile("(?i)^[#@][a-z_][.\\w]*");
    private static final Pattern PATTERN_XML_IDENTIFIER = Pattern.compile("(?i)^[a-z_][.\\w]*");

    public static Node read(String src) {
        Node root = new Node();
        readObject(src, root, 0);
        return root;
    }

    private static int readObject(String src, Node parent, int start) {
        Matcher objectOpenMatcher = PATTERN_JSON_OBJECT_OPEN
                .matcher(src)
                .region(start, src.length())
                .useAnchoringBounds(true);

        if (!objectOpenMatcher.find()) {
            return start;
        }

        int index = objectOpenMatcher.end();

        Matcher attributeMatcher = PATTERN_JSON_OBJECT_ATTR_NAME
                .matcher(src)
                .useAnchoringBounds(true)
                .region(index, src.length());

        Matcher valueMatcher = PATTERN_JSON_OBJECT_ATTR_VALUE
                .matcher(src)
                .useAnchoringBounds(true);

        Matcher objectCloseMatcher = PATTERN_JSON_OBJECT_CLOSE
                .matcher(src)
                .useAnchoringBounds(true);

        Node node;
        while (attributeMatcher.find()) {
            index = attributeMatcher.end();
            node = new Node(attributeMatcher.group(1));
            if (isJson(src, index)) {
                index = readObject(src, node, index);

                if (isXmlAttributes(node)) {
                    Node child;
                    for (Map.Entry<String, Node> elem : node.getChildrenMap().entrySet()) {
                        child = elem.getValue();
                        if (elem.getKey().charAt(0) == '#') {
                            if (child.hasChildren()) {
                                node.removeChild(child);
                                for (Node subChild: child.getChildrenMap().values()) {
                                    node.addChild(subChild);
                                }
                            } else {
                                child = node.removeChild(elem.getValue());
                                node.setValue(child.getValue());
                            }
                        } else {
                            child = node.removeChild(elem.getValue());
                            node.setAttribute(child.getName().substring(1), child.getValue());
                        }
                    }

                } else {
                    Map<String, Node> childrenMap = node.getChildrenMap();
                    for (Map.Entry<String, Node> elem : childrenMap.entrySet()) {
                        if (isValidXmlAttribute(elem.getKey())) {
                            if (childrenMap.containsKey(elem.getKey().substring(1))) {
                                node.removeChild(elem.getValue());
                            } else {
                                elem.getValue().setName(elem.getValue().getName().substring(1));
                            }
                        } else if (!isValidXmlIdentifier(elem.getKey())) {
                            node.removeChild(elem.getValue());
                        }
                    }
                    if (!node.hasChildren()) {
                        node.setValue("");
                    }
                }

            } else {
                valueMatcher.region(index, src.length());
                if (!valueMatcher.find()) {
                    throw new RuntimeException("Attribute value expected.");
                }

                if (valueMatcher.group(2) != null) { // string
                    node.setValue(valueMatcher.group(2));

                } else if (valueMatcher.group(3) != null) { // number
                    node.setValue(valueMatcher.group(3));

                } else if (valueMatcher.group(4) != null) { // null
                    node.setValue(null);

                } else {
                    throw new RuntimeException("Unknown attribute value.");

                }
                index = valueMatcher.end();
            }
            attributeMatcher.region(index, src.length());

            parent.addChild(node);
        }

        objectCloseMatcher.region(index, src.length());
        if (!objectCloseMatcher.find()) {
            throw new RuntimeException("Object end expected.");
        }

        return objectCloseMatcher.end();
    }

    public static boolean isJson(String src) {
        return isJson(src, 0);
    }

    private static boolean isJson(String src, int start) {
        return PATTERN_JSON_BEGINNING
                .matcher(src)
                .region(start, src.length())
                .useAnchoringBounds(true)
                .find();
    }

    private static boolean isValidXmlAttribute(String name) {
        return name != null && PATTERN_XML_ATTRIBUTE.matcher(name).matches();
    }

    private static boolean isValidXmlIdentifier(String name) {
        return name != null && PATTERN_XML_IDENTIFIER.matcher(name).matches();
    }

    private static boolean isXmlAttributes(Node node) {
        Map<String, Node> map = node.getChildrenMap();
        if (!map.containsKey("#" + node.getName())) {
            return false;
        }
        for (Map.Entry<String, Node> elem : map.entrySet()) {
            if (!isValidXmlAttribute(elem.getKey())) {
                return false;
            }
            if (elem.getKey().charAt(1) == '@' && elem.getValue().hasChildren()) {
                return false;
            }
        }
        return true;
    }
}

public class Main1 {
    public static void main(String[] args) throws IOException {
        //String src = Files.readString(Paths.get("test.txt"));
        String src = Files.readString(Paths.get("d:\\test\\test.txt"));
        if (XmlReader.isXml(src)) {
            System.out.println(XmlReader.read(src));
        } else if (JsonReader.isJson(src)) {
            System.out.println(JsonReader.read(src));
        }
    }
}
