package com.electrosparkles.presetpony;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Parses Fender Fuse-compatible {@code .fuse} XML back into a {@link CurrentPreset}.
 * Reverse of {@link FusePresetExporter}, using the same confirmed format
 *  including the corrected plain raw&lt;&lt;8
 * encoding
 *
 * Safety: file size is capped and the XML parser is
 * configured to reject DTDs/external entities
 *
 * Amp-specific "specific bytes" (payload offsets 12-14, 18, 22 in the wire
 * protocol) are NOT read from the file's individual Param values - they're
 * derived from the recognized AmpModel instead, since they're meant to be a
 * fixed function of the model, not independently-stored data. This also
 * means a file with a tampered/corrupted specific-byte value can't silently
 * produce an inconsistent AmpSettings.
 */
public final class FusePresetImporter {

    /** Real exported files are a few KB; this is a generous sanity ceiling, not a tight limit. */
    public static final long MAX_FILE_SIZE_BYTES = 1_000_000;

    private static final int[] AMP_CATEGORY_SLOT_GROUP = {0, 1, 2, 3};
    private static final String[] FX_TAGS = {"Stompbox", "Modulation", "Delay", "Reverb"};

    private FusePresetImporter() {
    }

    public static CurrentPreset fromFile(Path path) throws IOException {
        if (!Files.exists(path)) {
            throw new IllegalArgumentException("File does not exist: " + path);
        }
        long size = Files.size(path);
        if (size == 0) {
            throw new IllegalArgumentException("File is empty: " + path);
        }
        if (size > MAX_FILE_SIZE_BYTES) {
            throw new IllegalArgumentException("File is " + size + " bytes - too large to be a valid single .fuse "
                    + "preset (expected a few KB). Refusing to parse. If this is a Fuse backup ZIP, extract an "
                    + "individual preset file from Presets/ first.");
        }
        String xml = Files.readString(path, StandardCharsets.UTF_8);
        try {
            return fromXml(xml);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Couldn't import " + path.getFileName() + ": " + e.getMessage(), e);
        }
    }

    public static CurrentPreset fromXml(String xml) {
        if (xml == null || xml.isBlank()) {
            throw new IllegalArgumentException("Empty file content");
        }
        xml = stripBomAndLeadingWhitespace(xml);

        Document doc;
        try {
            doc = parseSecurely(xml);
        } catch (ParserConfigurationException | SAXException | IOException e) {
            throw new IllegalArgumentException("Not valid XML: " + e.getMessage(), e);
        }

        Element root = doc.getDocumentElement();
        if (root == null || !"Preset".equals(root.getTagName())) {
            String actual = (root != null) ? root.getTagName() : "(none)";
            throw new IllegalArgumentException("Not a Fuse preset file - root element is <" + actual
                    + ">, expected <Preset>. (Metadata sidecars like AmpData/M2_NN.fuse and "
                    + "SystemSettings.fuse are not tone presets - see fuse-preset-format.md Section 2.3/2.4.)");
        }

        String productId = root.getAttribute("ProductId");
        if (!productId.isBlank() && !"13".equals(productId)) {
            throw new IllegalArgumentException("Unsupported ProductId=\"" + productId + "\" - this app only "
                    + "supports Mustang III v2 presets (ProductId=\"13\"). A file from a different Mustang model "
                    + "would be misinterpreted (amp/effect IDs differ between models).");
        }

        Element amplifierEl = childElement(root, "Amplifier");
        Element fxEl = childElement(root, "FX");
        if (amplifierEl == null || fxEl == null) {
            throw new IllegalArgumentException("Missing required <Amplifier> or <FX> section - not a complete preset file");
        }

        Element infoEl = firstDescendant(root, "Info");
        String name = (infoEl != null) ? infoEl.getAttribute("name") : "";

        AmpSettings amp = readAmp(amplifierEl, readUsbGain(root));
        EffectSettings[] effects = new EffectSettings[4];
        for (int i = 0; i < 4; i++) {
            effects[i] = readFxCategory(fxEl, FX_TAGS[i], AMP_CATEGORY_SLOT_GROUP[i]);
        }

        // presetNumber=-1 and empty presetNames signal "not from a live amp slot" -
        // this preset hasn't been assigned a slot yet (that only happens on save-to-slot).
        return new CurrentPreset(-1, name, amp, effects, java.util.List.of());
    }

    private static AmpSettings readAmp(Element amplifierEl, int usbGain) {
        Element module = childElement(amplifierEl, "Module");
        if (module == null) {
            throw new IllegalArgumentException("<Amplifier> has no <Module>");
        }

        int modelId = requireIntAttr(module, "ID", "Amplifier Module");
        AmpModel model;
        try {
            model = AmpModel.fromId(modelId);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unrecognized amp model ID " + modelId + " in file", e);
        }

        return new AmpSettings(
                model,
                dupParam(module, 0),
                dupParam(module, 1),
                dupParam(module, 2),
                dupParam(module, 3),
                dupParam(module, 4),
                dupParam(module, 5),
                dupParam(module, 6),
                dupParam(module, 7),
                dupParam(module, 8),   // unknown24 - per-preset stored byte, feeds usb packet bytes 24/27
                dupParam(module, 9),   // depth
                dupParam(module, 10),  // bias
                plainParam(module, 15),                          // noiseGate
                plainParam(module, 16),                          // threshold
                CabinetModel.fromId(plainParam(module, 17)),      // cabinet
                plainParam(module, 19),                          // sag
                plainParam(module, 20),                          // brightness
                usbGain
        );
    }

    private static int readUsbGain(Element root) {
        Element usbGainEl = childElement(root, "UsbGain");
        if (usbGainEl == null) return 0;
        try {
            return Integer.parseInt(usbGainEl.getTextContent().trim()) & 0xFF;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static EffectSettings readFxCategory(Element fxEl, String tag, int slotGroup) {
        Element category = childElement(fxEl, tag);
        if (category == null) {
            return new EffectSettings(slotGroup, EffectModel.EMPTY, 0, 0, 0, 0, 0, 0, false);
        }
        Element module = childElement(category, "Module");
        if (module == null) {
            return new EffectSettings(slotGroup, EffectModel.EMPTY, 0, 0, 0, 0, 0, 0, false);
        }

        int modelId = requireIntAttr(module, "ID", "<" + tag + "> Module");
        int pos = attrIntOrDefault(module, "POS", slotGroup);

        if (modelId == 0) {
            return new EffectSettings(pos, EffectModel.EMPTY, 0, 0, 0, 0, 0, 0, false);
        }

        EffectModel model = EffectModel.fromId(modelId);
        if (model == null) {
            throw new IllegalArgumentException("Unrecognized effect model ID " + modelId + " in <" + tag + ">");
        }

        boolean enabled = !"0".equals(module.getAttribute("BypassState"));

        return new EffectSettings(
                pos,
                model,
                dupParam(module, 0),
                dupParam(module, 1),
                dupParam(module, 2),
                dupParam(module, 3),
                dupParam(module, 4),
                dupParam(module, 5),
                enabled
        );
    }

    /**
     * Real-world .fuse files (Windows-created) commonly start with a UTF-8
     * byte-order-mark, and some editors/tools leave leading whitespace before
     * the XML declaration - both are strictly forbidden by XML ("Content is
     * not allowed in prolog")
     * Stripping both here makes import robust
     */
    private static String stripBomAndLeadingWhitespace(String xml) {
        if (!xml.isEmpty() && xml.charAt(0) == '\uFEFF') {
            xml = xml.substring(1);
        }
        return xml.stripLeading();
    }

    // ---- XML helpers ----

    private static Document parseSecurely(String xml) throws ParserConfigurationException, SAXException, IOException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        // XXE protection - these files may come from untrusted sources (community downloads).
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);

        DocumentBuilder builder = factory.newDocumentBuilder();
        builder.setErrorHandler(null); // suppress console warnings; we surface errors via exceptions ourselves
        return builder.parse(new InputSource(new StringReader(xml)));
    }

    private static Element childElement(Element parent, String tagName) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node n = children.item(i);
            if (n.getNodeType() == Node.ELEMENT_NODE && n.getNodeName().equals(tagName)) {
                return (Element) n;
            }
        }
        return null;
    }

    private static Element firstDescendant(Element root, String tagName) {
        NodeList found = root.getElementsByTagName(tagName);
        return (found.getLength() > 0) ? (Element) found.item(0) : null;
    }

    private static int requireIntAttr(Element el, String attr, String context) {
        String value = el.getAttribute(attr);
        if (value.isBlank()) {
            throw new IllegalArgumentException(context + " is missing required attribute \"" + attr + "\"");
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(context + " has non-numeric \"" + attr + "\": " + value);
        }
    }

    private static int attrIntOrDefault(Element el, String attr, int defaultValue) {
        String value = el.getAttribute(attr);
        if (value.isBlank()) return defaultValue;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /** Reads a Param by ControlIndex, decodes the confirmed plain raw&lt;&lt;8 encoding. Missing = 0. */
    private static int dupParam(Element module, int controlIndex) {
        Integer stored = paramValue(module, controlIndex);
        return (stored == null) ? 0 : (stored >> 8) & 0xFF;
    }

    /** Reads a Param by ControlIndex as a plain (non-shifted) integer. Missing = 0. */
    private static int plainParam(Element module, int controlIndex) {
        Integer value = paramValue(module, controlIndex);
        return (value == null) ? 0 : value & 0xFF;
    }

    private static Integer paramValue(Element module, int controlIndex) {
        NodeList params = module.getElementsByTagName("Param");
        for (int i = 0; i < params.getLength(); i++) {
            Element p = (Element) params.item(i);
            if (String.valueOf(controlIndex).equals(p.getAttribute("ControlIndex"))) {
                try {
                    return Integer.parseInt(p.getTextContent().trim());
                } catch (NumberFormatException e) {
                    return null;
                }
            }
        }
        return null;
    }
}
