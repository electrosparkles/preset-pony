package com.electrosparkles.presetpony;

/**
 * Writes a {@link CurrentPreset} to Fender Fuse-compatible {@code .fuse} XML.
 */
public final class FusePresetExporter {

    private FusePresetExporter() {}

    /** Windows / Fuse-safe filename derived from the preset name. */
    public static String suggestFileName(String presetName) {
        return sanitizeBaseName(presetName) + ".fuse";
    }

    /** Safe base name without extension (for zip entries: {@code 000_Name.fuse}). */
    public static String sanitizeBaseName(String presetName) {
        if (presetName == null || presetName.isBlank()) {
            return "preset";
        }
        String base = presetName.strip();
        base = base.replaceAll("[\\\\/:*?\"<>|]", "_");
        base = base.replaceAll("\\s+", " ").strip();
        while (base.endsWith(".") || base.endsWith(" ")) {
            base = base.substring(0, base.length() - 1).strip();
        }
        if (base.isEmpty()) {
            base = "preset";
        }
        if (base.length() > 200) {
            base = base.substring(0, 200).strip();
        }
        if (base.toLowerCase().endsWith(".fuse")) {
            base = base.substring(0, base.length() - 5);
        }
        return base;
    }

    /** Zip entry path for a numbered backup slot. */
    public static String backupEntryPath(int slot, String presetName) {
        return String.format("presets/%02d_%s.fuse", slot, sanitizeBaseName(presetName));
    }

    public static String toXml(CurrentPreset preset) {
        return toXml(preset, "Mustang App", "");
    }

    public static String toXml(CurrentPreset preset, String author, String description) {
        StringBuilder sb = new StringBuilder(4096);
        sb.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");
        sb.append("<Preset amplifier=\"Mustang V2 III/IV/V\" ProductId=\"13\">\n");

        writeAmp(sb, preset.amp());
        writeFx(sb, preset.effects());

        // Static defaults copied from a real Fuse backup (Classic preset) — harmless padding.
        sb.append("  <Band Type=\"0\" Repeat=\"0\">\n");
        sb.append("    <SongFile Location=\"6\">No Band</SongFile>\n");
        sb.append("    <AudioMix>0</AudioMix>\n");
        sb.append("    <Balance>29127</Balance>\n");
        sb.append("    <Speed>100</Speed>\n");
        sb.append("    <Pitch>0</Pitch>\n");
        sb.append("    <Tempo />\n");
        sb.append("    <Transpose />\n");
        sb.append("    <DrumSolo />\n");
        sb.append("    <CountIn />\n");
        sb.append("  </Band>\n");

        sb.append("  <FUSE>\n");
        sb.append("    <Info name=\"").append(escAttr(preset.name().strip())).append("\" author=\"")
                .append(escAttr(author)).append("\" rating=\"0\" genre1=\"-1\" genre2=\"-1\" genre3=\"-1\" tags=\"\" fenderid=\"0\"");
        if (description != null && !description.isBlank()) {
            sb.append(">").append(escText(description)).append("</Info>\n");
        } else {
            sb.append("></Info>\n");
        }
        sb.append("    <PedalColors>\n");
        sb.append("      <Color ID=\"1\">14</Color>\n");
        sb.append("      <Color ID=\"2\">1</Color>\n");
        sb.append("      <Color ID=\"3\">2</Color>\n");
        sb.append("      <Color ID=\"4\">10</Color>\n");
        sb.append("    </PedalColors>\n");
        sb.append("  </FUSE>\n");

        sb.append("  <FirstExpressionPedal VolumeModeBehavior=\"1\" ExpressionModeBehavior=\"6\" ");
        sb.append("HeelSetting=\"0\" ToeSetting=\"65280\" PedalMode=\"0\" ");
        sb.append("BypassEffectWhenVolumeMode=\"1\" VolumeSwitchRevert=\"0\" ");
        sb.append("DefaultPedalState=\"0\" PedalOverrideState=\"0\" ParameterIndex=\"0\" />\n");

        sb.append("  <UsbGain>").append(preset.amp().usbGain()).append("</UsbGain>\n");
        sb.append("</Preset>\n");
        return sb.toString();
    }

    private static void writeAmp(StringBuilder sb, AmpSettings ampSettings) {
        // ControlIndex 8 and 11: per-preset stored value
        // Varies per preset even for the same amp model; must be read from the amp
        // rather than hardcoded.
        int[] spec = ampSettings.model().specificBytes;

        sb.append("  <Amplifier>\n");
        sb.append("    <Module ID=\"").append(ampSettings.model().id).append("\" POS=\"0\" BypassState=\"1\">\n");

        writeDupParam(sb, 0, ampSettings.volume());
        writeDupParam(sb, 1, ampSettings.gain());
        writeDupParam(sb, 2, ampSettings.gain2());
        writeDupParam(sb, 3, ampSettings.masterVolume());
        writeDupParam(sb, 4, ampSettings.treble());
        writeDupParam(sb, 5, ampSettings.middle());
        writeDupParam(sb, 6, ampSettings.bass());
        writeDupParam(sb, 7, ampSettings.presence());
        writeDupParam(sb, 8, ampSettings.unknown24());
        writeDupParam(sb, 9, ampSettings.depth());
        writeDupParam(sb, 10, ampSettings.bias());
        writeDupParam(sb, 11, ampSettings.unknown24());

        writePlainParam(sb, 12, spec[0]);
        writePlainParam(sb, 13, spec[1]);
        writePlainParam(sb, 14, spec[2]);
        writePlainParam(sb, 15, ampSettings.noiseGate());
        writePlainParam(sb, 16, ampSettings.threshold());
        writePlainParam(sb, 17, ampSettings.cabinet().id);
        writePlainParam(sb, 18, spec[3]);
        writePlainParam(sb, 19, ampSettings.sag());
        writePlainParam(sb, 20, ampSettings.brightness() != 0 ? 1 : 0);
        writePlainParam(sb, 21, 1);
        sb.append("      <Param ControlIndex=\"22\">0</Param>\n");

        sb.append("    </Module>\n");
        sb.append("  </Amplifier>\n");
    }

    private static void writeFx(StringBuilder sb, EffectSettings[] effects) {
        sb.append("  <FX>\n");
        writeFxCategory(sb, "Stompbox", 1, effects[0]);
        writeFxCategory(sb, "Modulation", 2, effects[1]);
        writeFxCategory(sb, "Delay", 3, effects[2]);
        writeFxCategory(sb, "Reverb", 4, effects[3]);
        sb.append("  </FX>\n");
    }

    private static void writeFxCategory(StringBuilder sb, String tag, int categoryId, EffectSettings fx) {
        sb.append("    <").append(tag).append(" ID=\"").append(categoryId).append("\">\n");
        if (fx == null || fx.model() == null || fx.model() == EffectModel.EMPTY) {
            int pos = (fx != null) ? fx.slot() : 0;
            sb.append("      <Module ID=\"0\" POS=\"").append(pos).append("\" BypassState=\"1\"></Module>\n");
        } else {
            writeEffectModule(sb, fx); // handles BypassState from fx.enabled() itself - a disabled-but-not-empty effect keeps its real Module/Params
        }
        sb.append("    </").append(tag).append(">\n");
    }

    private static void writeEffectModule(StringBuilder sb, EffectSettings fx) {
        EffectModel model = fx.model();
        String bypass = fx.enabled() ? "1" : "0";

        sb.append("      <Module ID=\"").append(model.id).append("\" POS=\"")
                .append(fx.slot()).append("\" BypassState=\"").append(bypass).append("\">\n");

        int[] knobValues = { fx.knob1(), fx.knob2(), fx.knob3(), fx.knob4(), fx.knob5(), fx.knob6() };


    /**
     * Only writes a Param for knobs the model actually uses (per KnobSpec.isUsed()),
     * and only shifts the raw value (raw<<8) for SLIDER knobs. TOGGLE and DROPDOWN
     * knobs are written as the plain, unshifted raw ordinal/boolean, matching how
     * real Fuse exports them - this covers Simple Comp's Type dropdown, Wah Mod /
     * Touch Wah's "High Q" toggle, Ring Mod/Phaser's Shape dropdown, Multitap
     * Delay's Mode dropdown, and Diatonic Pitch Shifter's Pitch/Key/Scale dropdowns.
     */

        for (int i = 0; i <= 5; i++) {
            KnobSpec spec = model.knobs[i];
            if (!spec.isUsed()) {
                continue;
            }
            if (spec.type() == KnobSpec.KnobControlType.SLIDER) {
                writeDupParam(sb, i, knobValues[i], "        ");
            } else {
                writePlainParam(sb, i, knobValues[i], "        ");
            }
        }

        sb.append("      </Module>\n");
    }

    private static void writeDupParam(StringBuilder sb, int index, int raw) {
        writeDupParam(sb, index, raw, "      ");
    }

    private static void writeDupParam(StringBuilder sb, int index, int raw, String indent) {
        // Confirmed against two real Fuse export files: this is a plain raw<<8 shift,
        // NOT (raw<<8)|raw - the low byte is always 0 in practice.
        int v = raw & 0xFF;
        sb.append(indent).append("<Param ControlIndex=\"").append(index).append("\">")
                .append(v << 8).append("</Param>\n");
    }

    private static void writePlainParam(StringBuilder sb, int index, int raw) {
        writePlainParam(sb, index, raw, "      ");
    }

    private static void writePlainParam(StringBuilder sb, int index, int raw, String indent) {
        sb.append(indent).append("<Param ControlIndex=\"").append(index).append("\">")
                .append(raw & 0xFF).append("</Param>\n");
    }

    private static String escAttr(String s) {
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&' -> out.append("&amp;");
                case '"' -> out.append("&quot;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                default -> out.append(c);
            }
        }
        return out.toString();
    }

    private static String escText(String s) {
        return escAttr(s);
    }
}
