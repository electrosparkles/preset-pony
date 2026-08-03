package com.electrosparkles.presetpony;

/**
 * Formats presets as CSV rows - one row per preset (wide format), for
 * browsing/scanning a whole preset library in a spreadsheet. Raw knob
 * values throughout
 */
public final class PresetCsvExporter {

    private PresetCsvExporter() {
    }

    public static String header() {
        StringBuilder sb = new StringBuilder();
        sb.append("Slot,Name,AmpModel,Volume,Gain,Gain2,MasterVolume,Treble,Middle,Bass,Presence,")
                .append("Depth,Bias,NoiseGate,Threshold,Cabinet,Sag,Brightness,UsbGain");
        for (int i = 1; i <= 4; i++) {
            sb.append(",Effect").append(i).append("_Model")
                    .append(",Effect").append(i).append("_FxSlotPos")
                    .append(",Effect").append(i).append("_Enabled")
                    .append(",Effect").append(i).append("_Knob1")
                    .append(",Effect").append(i).append("_Knob2")
                    .append(",Effect").append(i).append("_Knob3")
                    .append(",Effect").append(i).append("_Knob4")
                    .append(",Effect").append(i).append("_Knob5")
                    .append(",Effect").append(i).append("_Knob6");
        }
        return sb.toString();
    }

    public static String toCsvRow(CurrentPreset preset) {
        AmpSettings a = preset.amp();
        StringBuilder sb = new StringBuilder();

        sb.append(preset.presetNumber()).append(',');
        sb.append(escape(preset.name())).append(',');
        sb.append(escape(a.model().displayName)).append(',');
        sb.append(a.volume()).append(',').append(a.gain()).append(',').append(a.gain2()).append(',')
                .append(a.masterVolume()).append(',').append(a.treble()).append(',').append(a.middle()).append(',')
                .append(a.bass()).append(',').append(a.presence()).append(',').append(a.depth()).append(',')
                .append(a.bias()).append(',').append(a.noiseGate()).append(',').append(a.threshold()).append(',')
                .append(escape(a.cabinet().displayName)).append(',').append(a.sag()).append(',')
                .append(a.brightness()).append(',').append(a.usbGain());

        for (EffectSettings fx : preset.effects()) {
            String modelName = (fx.model() != null) ? fx.model().displayName : "";
            sb.append(',').append(escape(modelName))
                    .append(',').append(fx.slot())
                    .append(',').append(fx.enabled())
                    .append(',').append(fx.knob1())
                    .append(',').append(fx.knob2())
                    .append(',').append(fx.knob3())
                    .append(',').append(fx.knob4())
                    .append(',').append(fx.knob5())
                    .append(',').append(fx.knob6());
        }

        return sb.toString();
    }

    /** RFC4180-style quoting: quote if the field contains a comma, quote, or newline; double up internal quotes. */
    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        boolean needsQuoting = value.contains(",") || value.contains("\"")
                || value.contains("\n") || value.contains("\r");
        if (!needsQuoting) {
            return value;
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
