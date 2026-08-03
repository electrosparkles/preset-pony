package com.electrosparkles.presetpony;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.event.ChangeListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * UI shell. "Amp" tab: main amp knobs + model/cabinet pickers + read-only
 * extra params. "Effects" tab: one panel per DSP slot, each with a model
 * picker (restricted to that slot's group + EMPTY) and 6 knob controls whose
 * type (slider/dropdown), range, label, and visibility all come from
 * EffectModel.knobs
 */
public class PresetPony extends JFrame {

    private static final int UNKNOWN_BYTE24 = 0;
    private MustangConnection conn;
    private CurrentPreset current;
    private boolean applyingProgrammatically = false;

    private final JLabel statusLabel = new JLabel("Not connected");
    private final JLabel presetLabel = new JLabel("Preset: -");
    private final JLabel ampModelLabel = new JLabel("Amp: -");

    private final JSlider volumeSlider = newKnobSlider();
    private final JSlider gainSlider = newKnobSlider();
    private final JSlider trebleSlider = newKnobSlider();
    private final JSlider middleSlider = newKnobSlider();
    private final JSlider bassSlider = newKnobSlider();
    private final JSlider presenceSlider = newKnobSlider();
    private final JLabel presenceNameLabel = new JLabel("Presence");
    private final JComboBox<CabinetModel> cabinetCombo = new JComboBox<>(CabinetModel.values());
    private final JComboBox<AmpModel> ampModelCombo = new JComboBox<>(AmpModel.values());

    private final JSlider gain2Slider = newKnobSlider();
    private final JLabel gain2NameLabel = new JLabel("Gain 2:");
    private final JLabel gain2ValueLabel = new JLabel("", SwingConstants.RIGHT);
    private final JSlider masterVolumeSlider = newKnobSlider();
    private final JSlider depthSlider = newKnobSlider();
    private final JSlider biasSlider = newKnobSlider();
    private final JComboBox<String> noiseGateCombo = new JComboBox<>(AmpKnobScale.NOISE_GATE_LABELS);
    private final JSlider thresholdSlider = new JSlider(0, 9, 0);
    private final JComboBox<String> sagCombo = new JComboBox<>(new String[]{"Less", "Match", "More"});
    private final JCheckBox brightnessCheck = new JCheckBox("On");
    private final JSlider usbGainSlider = newKnobSlider();

    @SuppressWarnings("unchecked")
    private final JComboBox<EffectModel>[] effectModelCombos = new JComboBox[4];
    private final JCheckBox[] effectEnabledChecks = new JCheckBox[4];
    private final JCheckBox[] fxLoopChecks = new JCheckBox[4];
    // Per slot, per knob (0-5): the live control widgets and which spec is currently active.
    private final JLabel[][] knobLabels = new JLabel[4][6];
    private final JSlider[][] knobSliders = new JSlider[4][6];
    @SuppressWarnings("unchecked")
    private final JComboBox<String>[][] knobDropdowns = new JComboBox[4][6];
    private final JCheckBox[][] knobToggles = new JCheckBox[4][6];
    private final JPanel[][] knobCardPanels = new JPanel[4][6];
    private final KnobSpec[][] activeKnobSpecs = new KnobSpec[4][6];

    private final DefaultListModel<String> presetListModel = new DefaultListModel<>();
    private final JList<String> presetList = new JList<>(presetListModel);
    private final JButton exportPresetButton;
    private JButton backupAllButton;
    private JButton exportCsvButton;
    private JButton connectButton;
    private JButton refreshButton;

    public PresetPony() {
        super("Preset Pony - Mustang v2 control");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                // Release the USB device handle cleanly on exit rather than relying on
                // process teardown to do it - matters if the OS/driver is slow to notice
                // an abandoned handle, which could otherwise make the amp appear "busy"
                // to the next app that tries to open it (e.g. Fuse itself, or Pony
                // relaunched) until a physical unplug/replug.
                if (conn != null) {
                    try {
                        conn.close();
                    } catch (Exception ignored) {
                    }
                }
            }
        });
        setLayout(new BorderLayout(12, 12));

        List<Image> icons = loadAppIcons();
        setIconImages(icons);
        if (!icons.isEmpty()) setIconImage(icons.get(0));

        JPanel top = new JPanel(new GridLayout(3, 1));
        top.add(statusLabel);
        top.add(presetLabel);
        top.add(ampModelLabel);
        top.setBorder(BorderFactory.createEmptyBorder(10, 10, 0, 10));
        add(top, BorderLayout.NORTH);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Amp", buildAmpPanel());
        tabs.addTab("Effects", buildEffectsPanel());
        tabs.addTab("Presets", buildPresetsPanel());
        tabs.addTab("About", buildAboutPanel());
        add(tabs, BorderLayout.CENTER);

        JPanel buttons = new JPanel();
        connectButton = new JButton("Connect");
        refreshButton = new JButton("Refresh from amp");
        exportPresetButton = new JButton("Export preset...");
        exportPresetButton.setEnabled(false);
        JButton importPresetButton = new JButton("Import preset...");
        buttons.add(connectButton);
        buttons.add(refreshButton);
        buttons.add(exportPresetButton);
        buttons.add(importPresetButton);
        add(buttons, BorderLayout.SOUTH);

        connectButton.addActionListener(e -> connectInBackground());
        refreshButton.addActionListener(e -> refreshInBackground());
        exportPresetButton.addActionListener(e -> exportPresetToFuse());
        importPresetButton.addActionListener(e -> importPresetFromFuse());

        setSlidersEnabled(false);
        setSize(560, 860);
        setLocationRelativeTo(null);
        setVisible(true);
    }

    // ================= Amp tab =================

    private JPanel buildAmpPanel() {
        JPanel container = new JPanel(new BorderLayout(8, 8));

        JPanel modelRow = new JPanel(new BorderLayout(6, 0));
        modelRow.add(new JLabel("Amp model"), BorderLayout.WEST);
        modelRow.add(ampModelCombo, BorderLayout.CENTER);
        modelRow.setBorder(BorderFactory.createEmptyBorder(6, 10, 0, 10));
        container.add(modelRow, BorderLayout.NORTH);

        JPanel knobs = new JPanel(new GridLayout(7, 1, 4, 4));
        knobs.setBorder(BorderFactory.createEmptyBorder(6, 20, 6, 20));
        knobs.add(labeledScaled("Volume", volumeSlider));
        knobs.add(labeledScaled("Gain", gainSlider));
        knobs.add(labeledScaled("Treble", trebleSlider));
        knobs.add(labeledScaled("Middle", middleSlider));
        knobs.add(labeledScaled("Bass", bassSlider));
        knobs.add(labeledScaled(presenceNameLabel, presenceSlider));

        JPanel cabinetRow = new JPanel(new BorderLayout(6, 0));
        cabinetRow.add(new JLabel("Cabinet"), BorderLayout.WEST);
        cabinetRow.add(cabinetCombo, BorderLayout.CENTER);
        knobs.add(cabinetRow);
        container.add(knobs, BorderLayout.CENTER);

        JPanel otherParams = new JPanel(new GridLayout(0, 2, 6, 2));
        otherParams.setBorder(BorderFactory.createTitledBorder("Other params"));
        otherParams.add(gain2NameLabel);
        otherParams.add(gain2Row());
        otherParams.add(new JLabel("Master volume:"));
        otherParams.add(compactSliderWithValue(masterVolumeSlider, AmpKnobScale::formatMainEq));
        otherParams.add(new JLabel("Noise gate:"));
        otherParams.add(noiseGateCombo);
        otherParams.add(new JLabel("Depth:"));
        otherParams.add(compactSliderWithValue(depthSlider, AmpKnobScale::formatDepthPercent));
        otherParams.add(new JLabel("Threshold:"));
        otherParams.add(compactSliderWithValue(thresholdSlider, String::valueOf));
        otherParams.add(new JLabel("Bias:"));
        otherParams.add(compactSliderWithValue(biasSlider, AmpKnobScale::formatBiasPercent));
        otherParams.add(new JLabel("Sag:"));
        otherParams.add(sagCombo);
        otherParams.add(new JLabel("Brightness:"));
        otherParams.add(brightnessCheck);
        otherParams.add(new JLabel("USB gain:"));
        otherParams.add(compactSliderWithValue(usbGainSlider, AmpKnobScale::formatUsbGainPercent));
        container.add(otherParams, BorderLayout.SOUTH);

        ChangeListener onAmpKnobChanged = ev -> {
            JSlider s = (JSlider) ev.getSource();
            if (applyingProgrammatically || s.getValueIsAdjusting() || current == null) return;
            writeAmpSettingsInBackground(buildAmpSettingsFromSliders());
        };
        for (JSlider s : new JSlider[]{volumeSlider, gainSlider, trebleSlider, middleSlider, bassSlider, presenceSlider,
                gain2Slider, masterVolumeSlider, biasSlider, usbGainSlider}) {
            s.addChangeListener(onAmpKnobChanged);
        }
        cabinetCombo.addActionListener(e -> {
            if (applyingProgrammatically || current == null) return;
            writeAmpSettingsInBackground(buildAmpSettingsFromSliders());
        });
        ampModelCombo.addActionListener(e -> {
            if (applyingProgrammatically) return;
            AmpModel model = (AmpModel) ampModelCombo.getSelectedItem();
            refreshBrightnessEnabled();
            refreshPresenceLabel();
            refreshGain2Display();
            refreshSagBiasEnabled();
            CabinetModel defaultCab = (model != null) ? model.defaultCabinet() : null;
            if (defaultCab != null && cabinetCombo.getSelectedItem() != defaultCab) {
                cabinetCombo.setSelectedItem(defaultCab); // fires cabinetCombo's own listener, which writes (covers both changes at once)
                return;
            }
            if (current == null) return;
            writeAmpSettingsInBackground(buildAmpSettingsFromSliders());
        });
        sagCombo.addActionListener(e -> {
            if (applyingProgrammatically || current == null) return;
            writeAmpSettingsInBackground(buildAmpSettingsFromSliders());
        });
        brightnessCheck.addActionListener(e -> {
            if (applyingProgrammatically || current == null) return;
            writeAmpSettingsInBackground(buildAmpSettingsFromSliders());
        });
        noiseGateCombo.addActionListener(e -> {
            if (applyingProgrammatically) return;
            int gateIndex = noiseGateCombo.getSelectedIndex();
            if (!AmpKnobScale.isCustomGate(gateIndex)) {
                // Named preset (Off/Low/Mid/High/Max) - auto-pair Threshold/Depth to the
                // confirmed default, same pattern as ampModelCombo -> cabinetCombo.
                // Suppress the two sliders' own change listeners while bulk-setting both,
                // so the single explicit write below carries the fully combined state
                // instead of firing twice with a half-updated value in between.
                applyingProgrammatically = true;
                thresholdSlider.setValue(AmpKnobScale.defaultThresholdForGate(gateIndex));
                depthSlider.setValue(AmpKnobScale.defaultDepthForGate(gateIndex));
                applyingProgrammatically = false;
            }
            // Custom (gateIndex==5): leave Threshold/Depth exactly as they are - selecting
            // Custom unlocks manual editing without resetting them
            refreshThresholdDepthEnabled();
            if (current == null) return;
            writeAmpSettingsInBackground(buildAmpSettingsFromSliders());
        });
        ChangeListener onThresholdDepthChanged = ev -> {
            if (applyingProgrammatically || ((JSlider) ev.getSource()).getValueIsAdjusting() || current == null) return;
            writeAmpSettingsInBackground(buildAmpSettingsFromSliders());
        };
        thresholdSlider.addChangeListener(onThresholdDepthChanged);
        depthSlider.addChangeListener(onThresholdDepthChanged);

        return container;
    }

    /** Threshold/Depth are only genuinely editable in Custom mode (gate index 5) -
     * which disables those two dials whenever the combo isn't on Custom.
     * Also respects the overall  connected/disconnected enabled state via noiseGateCombo.isEnabled(). */
    private void refreshThresholdDepthEnabled() {
        boolean enabled = noiseGateCombo.isEnabled() && AmpKnobScale.isCustomGate(noiseGateCombo.getSelectedIndex());
        thresholdSlider.setEnabled(enabled);
        depthSlider.setEnabled(enabled);
    }

    /** Brightness is a real on/off switch only on the amps AmpModel.SUPPORTS_BRIGHTNESS
     * lists (Fender '65 Twin Reverb, British '60s) - every other amp has no
     * Brightness control at all, so the checkbox is disabled for them rather than
     * offering a control that doesn't correspond to anything real on that amp. */
    private void refreshBrightnessEnabled() {
        AmpModel model = (AmpModel) ampModelCombo.getSelectedItem();
        boolean supported = model != null && AmpModel.SUPPORTS_BRIGHTNESS.contains(model);
        brightnessCheck.setEnabled(supported && ampModelCombo.isEnabled());
    }

    /** Presence is relabeled "Cut" on British '60s - same underlying wire byte, just a
     * different UI name (AmpModel.presenceUiLabel()).
     * */
    private void refreshPresenceLabel() {
        AmpModel model = (AmpModel) ampModelCombo.getSelectedItem();
        presenceNameLabel.setText(model != null ? model.presenceUiLabel() : "Presence");
    }

    /** Sag and Bias have no effect on Studio Preamp - not shown in Fuse's panel for
     * this model, even though Pony can still send changes for them.
     * Greyed out here to match Fuse rather than offering controls that don't do
     * anything real on this model - see AmpModel.STUDIO_PREAMP_HAS_NO_SAG_BIAS. */
    private void refreshSagBiasEnabled() {
        AmpModel model = (AmpModel) ampModelCombo.getSelectedItem();
        boolean supported = !(model != null && AmpModel.STUDIO_PREAMP_HAS_NO_SAG_BIAS.contains(model));
        sagCombo.setEnabled(supported && ampModelCombo.isEnabled());
        biasSlider.setEnabled(supported && ampModelCombo.isEnabled());
    }

    /** Builds the Gain 2 slider row for otherParams, mirroring compactSliderWithValue's
     * layout but with its own tracked name/value labels (rather than a fixed formatter
     * baked in at construction) - needed since Gain 2's label/scale ("Gain 2" 0.0-10.0,
     * vs "Blend" -50%/+50%) switches live with the selected amp model rather than being
     * fixed for the control's lifetime. See AmpModel.SUPPORTS_GAIN2_BLEND. */
    private JPanel gain2Row() {
        gain2ValueLabel.setPreferredSize(new Dimension(60, gain2ValueLabel.getPreferredSize().height));
        JPanel p = new JPanel(new BorderLayout(4, 0));
        p.add(gain2Slider, BorderLayout.CENTER);
        p.add(gain2ValueLabel, BorderLayout.EAST);
        gain2Slider.addChangeListener(e -> refreshGain2Display());
        refreshGain2Display();
        return p;
    }

    /** Updates Gain 2's name label ("Gain 2:" / "Blend:") and displayed value (0.0-10.0
     * / -50%..+50%) for the currently selected amp model - see
     * AmpModel.SUPPORTS_GAIN2_BLEND and AmpKnobScale.gain2Value/gain2BlendPercent.
     * Called whenever the amp model changes (the scale/label depends on it) and
     * whenever the slider itself moves. */
    private void refreshGain2Display() {
        AmpModel model = (AmpModel) ampModelCombo.getSelectedItem();
        boolean blend = model != null && AmpModel.SUPPORTS_GAIN2_BLEND.contains(model);
        gain2NameLabel.setText((model != null ? model.gain2UiLabel() : "Gain 2") + ":");
        int raw = gain2Slider.getValue();
        String text = blend ? AmpKnobScale.formatGain2BlendPercent(raw) : AmpKnobScale.formatGain2Value(raw);
        gain2ValueLabel.setText(text);
        gain2Slider.setToolTipText("raw " + raw + " -> " + text);
    }

    // ================= Effects tab =================

    private JPanel buildEffectsPanel() {
        JPanel effectsPanel = new JPanel(new GridLayout(4, 1, 8, 8));
        effectsPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        String[] slotNames = {"Slot 1 (distortion/dynamics)", "Slot 2 (modulation)", "Slot 3 (delay)", "Slot 4 (reverb)"};

        for (int slot = 0; slot < 4; slot++) {
            final int slotIndex = slot;
            JPanel slotPanel = new JPanel(new BorderLayout(4, 4));
            slotPanel.setBorder(BorderFactory.createTitledBorder(slotNames[slot]));

            List<EffectModel> validModels = new ArrayList<>();
            validModels.add(EffectModel.EMPTY);
            for (EffectModel m : EffectModel.values()) {
                if (m.dspSlotGroup == slot && !EffectModel.NOT_SUPPORTED_ON_MUSTANG_III_V2.contains(m)) validModels.add(m);
            }
            JComboBox<EffectModel> combo = new JComboBox<>(validModels.toArray(new EffectModel[0]));
            effectModelCombos[slot] = combo;

            JCheckBox enabledCheck = new JCheckBox("On", true);
            effectEnabledChecks[slot] = enabledCheck;

            JCheckBox fxLoopCheck = new JCheckBox("FX Loop (post-preamp)", false);
            fxLoopChecks[slot] = fxLoopCheck;

            JPanel headerRow = new JPanel(new BorderLayout(6, 0));
            headerRow.add(enabledCheck, BorderLayout.WEST);
            headerRow.add(combo, BorderLayout.CENTER);
            headerRow.add(fxLoopCheck, BorderLayout.EAST);
            slotPanel.add(headerRow, BorderLayout.NORTH);

            JPanel knobRow = new JPanel(new GridLayout(1, 6, 4, 2));
            for (int k = 0; k < 6; k++) {
                final int knobIndex = k;
                JLabel label = new JLabel(" ", SwingConstants.CENTER);
                label.setFont(label.getFont().deriveFont(10f));
                knobLabels[slot][k] = label;

                JSlider slider = newKnobSlider();
                knobSliders[slot][k] = slider;

                JComboBox<String> dropdown = new JComboBox<>();
                knobDropdowns[slot][k] = dropdown;

                JCheckBox toggle = new JCheckBox();
                knobToggles[slot][k] = toggle;

                JPanel cardPanel = new JPanel(new CardLayout());
                cardPanel.add(slider, "SLIDER");
                cardPanel.add(dropdown, "DROPDOWN");
                cardPanel.add(toggle, "TOGGLE");
                knobCardPanels[slot][k] = cardPanel;

                JPanel knobCell = new JPanel(new BorderLayout());
                knobCell.add(label, BorderLayout.NORTH);
                knobCell.add(cardPanel, BorderLayout.CENTER);
                knobRow.add(knobCell);

                ChangeListener knobChangeListener = ev -> {
                    KnobSpec liveSpec = activeKnobSpecs[slotIndex][knobIndex];
                    if (liveSpec != null && liveSpec.type() == KnobSpec.KnobControlType.SLIDER) {
                        int rawValue = slider.getValue();
                        knobLabels[slotIndex][knobIndex].setText(knobLabelText(liveSpec, rawValue));
                        slider.setToolTipText("raw " + rawValue + " -> " + knobDisplayValueText(liveSpec, rawValue));
                    }
                    if (applyingProgrammatically || slider.getValueIsAdjusting() || current == null) return;
                    writeEffectSettingsInBackground(slotIndex, buildEffectSettingsFromControls(slotIndex));
                };
                slider.addChangeListener(knobChangeListener);
                dropdown.addActionListener(e -> {
                    if (applyingProgrammatically || current == null) return;
                    writeEffectSettingsInBackground(slotIndex, buildEffectSettingsFromControls(slotIndex));
                });
                toggle.addActionListener(e -> {
                    if (applyingProgrammatically || current == null) return;
                    writeEffectSettingsInBackground(slotIndex, buildEffectSettingsFromControls(slotIndex));
                });
            }
            slotPanel.add(knobRow, BorderLayout.CENTER);

            enabledCheck.addActionListener(e -> {
                boolean on = enabledCheck.isSelected();
                combo.setEnabled(on);
                for (int k = 0; k < 6; k++) setKnobCellEnabled(slotIndex, k, on);
                if (applyingProgrammatically || current == null) return;
                writeEffectSettingsInBackground(slotIndex, buildEffectSettingsFromControls(slotIndex));
            });

            fxLoopCheck.addActionListener(e -> {
                if (applyingProgrammatically || current == null) return;
                writeEffectSettingsInBackground(slotIndex, buildEffectSettingsFromControls(slotIndex));
            });

            combo.addActionListener(e -> {
                EffectModel model = (EffectModel) combo.getSelectedItem();
                if (model == null) return;
                updateEmptySlotState(slotIndex, model);
                if (!applyingProgrammatically) {
                    // Model changed by the user - load that model's factory default knob
                    // values rather than leaving stale values from the previous model.
                    applyKnobSpecsForModel(slotIndex, model, model.defaultValues);
                }
                if (applyingProgrammatically || current == null) return;
                writeEffectSettingsInBackground(slotIndex, buildEffectSettingsFromControls(slotIndex));
            });

            effectsPanel.add(slotPanel);
        }
        return effectsPanel;
    }

    /** Applies a model's knob specs (labels/ranges/dropdown-vs-slider/visibility) and given values to slot's controls. */
    private void applyKnobSpecsForModel(int slot, EffectModel model, int[] values) {
        for (int k = 0; k < 6; k++) {
            KnobSpec spec = model.knobs[k];
            activeKnobSpecs[slot][k] = spec;
            knobCardPanels[slot][k].setVisible(spec.isUsed());

            CardLayout cl = (CardLayout) knobCardPanels[slot][k].getLayout();
            int appliedValue;
            if (spec.type() == KnobSpec.KnobControlType.DROPDOWN) {
                JComboBox<String> dd = knobDropdowns[slot][k];
                dd.removeAllItems();
                for (String option : spec.dropdownOptions()) dd.addItem(option);
                int idx = Math.max(0, Math.min(values[k], spec.dropdownOptions().length - 1));
                dd.setSelectedIndex(idx);
                cl.show(knobCardPanels[slot][k], "DROPDOWN");
                appliedValue = idx;
            } else if (spec.type() == KnobSpec.KnobControlType.TOGGLE) {
                boolean on = values[k] != 0;
                knobToggles[slot][k].setSelected(on);
                cl.show(knobCardPanels[slot][k], "TOGGLE");
                appliedValue = on ? 1 : 0;
            } else {
                JSlider slider = knobSliders[slot][k];
                slider.setMaximum(Math.max(1, spec.max()));
                int clamped = Math.max(0, Math.min(values[k], spec.max()));
                slider.setValue(clamped);
                cl.show(knobCardPanels[slot][k], "SLIDER");
                appliedValue = clamped;
                if (spec.isUsed()) {
                    slider.setToolTipText("raw " + clamped + " -> " + knobDisplayValueText(spec, clamped));
                }
            }
            knobLabels[slot][k].setText(knobLabelText(spec, appliedValue));
        }
    }

    /** Knob cell title: just the name on one line, or the name and its value stacked
     * on two lines (name above, value below) for sliders - centered over the slider
     * itself rather than run together on one line, which was squashing longer labels/
     * values together. Value is either the confirmed real-world scale (Pitch Shifter's
     * Pitch, or an effect-knob-scales.properties fact) where known, or a plain raw/max
     * percentage otherwise, since every other effect slider is a plain 0-100% control
     */
    private static String knobLabelText(KnobSpec spec, int rawValue) {
        if (!spec.isUsed()) return " ";
        if (spec.type() == KnobSpec.KnobControlType.SLIDER) {
            return twoLineCenteredHtml(spec.label(), knobDisplayValueText(spec, rawValue));
        }
        return spec.label();
    }

    private static String twoLineCenteredHtml(String topLine, String bottomLine) {
        return "<html><div style='text-align:center;'>" + topLine + "<br>" + bottomLine + "</div></html>";
    }

    /** Raw slider value as its display string: the confirmed real-world scale
     * (formatDisplayValue) where one is known, otherwise a plain raw/max percentage. */
    private static String knobDisplayValueText(KnobSpec spec, int rawValue) {
        if (spec.hasDisplayScale()) {
            return spec.formatDisplayValue(rawValue);
        }
        return String.format("%.1f%%", rawValue * 100.0 / spec.max());
    }

    /**
     * An EMPTY slot has no meaningful on/off or pre/post state - grey out (disable)
     * both checkboxes and force them unchecked, rather than showing a stale or
     * meaningless checked state left over from whatever was last selected there.
     */
    private void updateEmptySlotState(int slot, EffectModel model) {
        boolean hasRealEffect = (model != null) && (model != EffectModel.EMPTY);
        effectEnabledChecks[slot].setEnabled(hasRealEffect);
        fxLoopChecks[slot].setEnabled(hasRealEffect);
        if (!hasRealEffect) {
            effectEnabledChecks[slot].setSelected(false);
            fxLoopChecks[slot].setSelected(false);
        }
    }

    private JPanel buildPresetsPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        panel.add(new JLabel("Stored on the amp:"), BorderLayout.NORTH);
        presetList.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        panel.add(new JScrollPane(presetList), BorderLayout.CENTER);

        JButton switchButton = new JButton("Switch to preset");
        switchButton.setEnabled(false);
        JButton saveToSlotButton = new JButton("Save current to slot...");
        saveToSlotButton.setEnabled(false);
        backupAllButton = new JButton("Backup all to zip...");
        backupAllButton.setEnabled(false);
        exportCsvButton = new JButton("Export all to CSV...");
        exportCsvButton.setEnabled(false);

        JPanel presetActions = new JPanel(new GridLayout(2, 2, 8, 4));
        presetActions.add(switchButton);
        presetActions.add(saveToSlotButton);
        presetActions.add(backupAllButton);
        presetActions.add(exportCsvButton);
        panel.add(presetActions, BorderLayout.SOUTH);

        presetList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                switchButton.setEnabled(presetList.getSelectedIndex() >= 0 && conn != null);
                saveToSlotButton.setEnabled(presetList.getSelectedIndex() >= 0 && conn != null && current != null);
            }
        });

        switchButton.addActionListener(e -> {
            int slot = presetList.getSelectedIndex();
            if (slot < 0 || conn == null) return;
            statusLabel.setText("Switching to preset " + slot + "...");
            switchButton.setEnabled(false);
            new SwingWorker<CurrentPreset, Void>() {
                @Override
                protected CurrentPreset doInBackground() {
                    return conn.switchToPreset(slot);
                }

                @Override
                protected void done() {
                    try {
                        current = get();
                        statusLabel.setText("Connected");
                        populateFromCurrent();
                    } catch (Exception ex) {
                        statusLabel.setText("Error: " + ex.getMessage());
                    } finally {
                        switchButton.setEnabled(presetList.getSelectedIndex() >= 0);
                    }
                }
            }.execute();
        });

        backupAllButton.addActionListener(e -> backupAllPresetsToZip());
        exportCsvButton.addActionListener(e -> exportAllPresetsToCsv());

        saveToSlotButton.addActionListener(e -> {
            int slot = presetList.getSelectedIndex();
            if (slot < 0 || conn == null || current == null) return;

            String existingName = (slot < current.presetNames().size()) ? current.presetNames().get(slot) : "";
            String proposedName = JOptionPane.showInputDialog(this,
                    "Save the current live settings to slot " + slot + ".\n"
                            + "This will overwrite whatever is currently stored there"
                            + (existingName.isBlank() ? "." : " (\"" + existingName + "\").") + "\n\n"
                            + "Preset name:",
                    "Save to slot " + slot, JOptionPane.WARNING_MESSAGE);
            if (proposedName == null) return; // cancelled

            int confirm = JOptionPane.showConfirmDialog(this,
                    "Overwrite slot " + slot + " (\"" + existingName + "\") with \"" + proposedName + "\"?\n"
                            + "This cannot be undone.",
                    "Confirm overwrite", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (confirm != JOptionPane.YES_OPTION) return;

            statusLabel.setText("Saving to slot " + slot + "...");
            saveToSlotButton.setEnabled(false);
            new SwingWorker<CurrentPreset, Void>() {
                @Override
                protected CurrentPreset doInBackground() {
                    // Patch the name list locally so the read-back preset carries
                    // the updated name for this slot without needing a full refresh.
                    List<String> updatedNames = new ArrayList<>(current.presetNames());
                    while (updatedNames.size() <= slot) updatedNames.add("");
                    updatedNames.set(slot, proposedName);
                    return conn.saveToSlot(slot, proposedName, updatedNames);
                }

                @Override
                protected void done() {
                    try {
                        current = get();
                        statusLabel.setText("Saved to slot " + slot);
                        populateFromCurrent();
                    } catch (Exception ex) {
                        statusLabel.setText("Error: " + ex.getMessage());
                    } finally {
                        saveToSlotButton.setEnabled(presetList.getSelectedIndex() >= 0);
                    }
                }
            }.execute();
        });

        return panel;
    }

    // ================= About tab =================

    private JPanel buildAboutPanel() {
        JPanel panel = new JPanel(new BorderLayout(12, 12));
        panel.setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));

        JLabel titleLabel = new JLabel("Preset Pony");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 22f));

        JLabel versionLabel = new JLabel("Unofficial companion app for Fender Mustang amplifiers");

        String bodyHtml = "<html><body style='width:300px; font-family:sans-serif;'>"
                + "<p>Preset Pony connects to Fender Mustang III V2 amplifiers over USB to "
                + "read and write amp/effect settings, browse and switch stored presets, and "
                + "import/export presets and backups.</p>"
                + "<p>Mustang III V1 amplifiers use a similar but not identical protocol - some "
                + "V1 units may partially work, but this app is neither built for nor tested "
                + "against V1 hardware.</p>"
                + "<p><b>Disclaimer:</b> this is an independent, unofficial tool, not affiliated "
                + "with or endorsed by Fender. It communicates directly with your amp's USB "
                + "control interface. <b>Use it entirely at your own risk.</b> The developers "
                + "accept no responsibility or liability for any damage to your amplifier, "
                + "computer, or other equipment, or for any lost presets, arising from the use "
                + "of this software.</p>"
                + "</body></html>";
        JLabel bodyLabel = new JLabel(bodyHtml);

        JPanel textPanel = new JPanel();
        textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));
        textPanel.add(titleLabel);
        textPanel.add(Box.createVerticalStrut(4));
        textPanel.add(versionLabel);
        textPanel.add(Box.createVerticalStrut(16));
        textPanel.add(bodyLabel);

        java.util.List<Image> icons = loadAppIcons();
        if (!icons.isEmpty()) {
            Image best = icons.get(icons.size() - 1); // largest loaded size
            JLabel iconLabel = new JLabel(new ImageIcon(best.getScaledInstance(96, 96, Image.SCALE_SMOOTH)));
            iconLabel.setVerticalAlignment(SwingConstants.TOP);
            iconLabel.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 16));
            panel.add(iconLabel, BorderLayout.WEST);
        }

        panel.add(textPanel, BorderLayout.CENTER);
        return panel;
    }

    private void setKnobCellEnabled(int slot, int knobIndex, boolean enabled) {
        knobSliders[slot][knobIndex].setEnabled(enabled);
        knobDropdowns[slot][knobIndex].setEnabled(enabled);
        knobToggles[slot][knobIndex].setEnabled(enabled);
    }

    private static JSlider newKnobSlider() {
        JSlider s = new JSlider(0, 255, 0);
        s.setMajorTickSpacing(64);
        s.setPaintTicks(true);
        return s;
    }

    // Raw-to-display conversion formulas (main EQ 1.0-10.0, Bias %, USB Gain %) now live
    // in AmpKnobScale

    private static JPanel labeledScaled(String name, JSlider slider) {
        return labeledScaled(new JLabel(name), slider);
    }

    /** Same as the String overload, but takes an existing JLabel so callers (Presence,
     * relabeled "Cut" on British '60s - AmpModel.presenceUiLabel()) can update its text
     * later without rebuilding the panel. */
    private static JPanel labeledScaled(JLabel nameLabel, JSlider slider) {
        JLabel valueLabel = new JLabel(AmpKnobScale.formatMainEq(slider.getValue()), SwingConstants.RIGHT);
        JPanel titleRow = new JPanel(new BorderLayout());
        titleRow.add(nameLabel, BorderLayout.WEST);
        titleRow.add(valueLabel, BorderLayout.EAST);

        JPanel p = new JPanel(new BorderLayout());
        p.add(titleRow, BorderLayout.NORTH);
        p.add(slider, BorderLayout.CENTER);

        slider.addChangeListener(e -> {
            String scaled = AmpKnobScale.formatMainEq(slider.getValue());
            valueLabel.setText(scaled);
            slider.setToolTipText("raw " + slider.getValue() + " -> " + scaled);
        });
        slider.setToolTipText("raw " + slider.getValue() + " -> " + AmpKnobScale.formatMainEq(slider.getValue()));
        return p;
    }

    /**
     * Loads every bundled icon resolution (icons/icon_16.png ... icon_256.png) from the
     * classpath. Passing the whole list to the caller's setIconImages() (rather than a single
     * setIconImage()) lets Windows/Swing pick the resolution that matches the taskbar,
     * Alt-Tab, and title-bar contexts instead of scaling one image for all of them.
     * Fails safe: any resource that's missing (e.g. running from loose .class files
     * without the icons/ resource dir on the classpath) is just skipped, so a missing
     * icon set never prevents the app from starting giving the platform default.
     */
    private static java.util.List<Image> loadAppIcons() {
        java.util.List<Image> icons = new ArrayList<>();
        java.util.List<Integer> missing = new ArrayList<>();
        for (int size : new int[]{16, 24, 32, 48, 64, 128, 256}) {
            String resource = "/icons/icon_" + size + ".png";
            java.net.URL url = PresetPony.class.getResource(resource);
            if (url == null) {
                missing.add(size);
                continue;
            }
            try {
                BufferedImage img = ImageIO.read(url); // synchronous decode
                if (img != null) {
                    icons.add(img);
                } else {
                    missing.add(size); // treat undecodable as missing
                }
            } catch (IOException e) {
                throw new RuntimeException("Failed to read " + resource, e);
            }
        }
        if (icons.isEmpty()) {
            System.err.println("[icon] No /icons/icon_NN.png resources found on the classpath - "
                    + "falling back to the platform default icon. Check that the icons/ folder was "
                    + "copied into the compiled classes (or onto the jar) before packaging - run "
                    + "`jar tf <yourapp>.jar | findstr icons` to confirm what's actually inside it.");
        } else if (!missing.isEmpty()) {
            System.err.println("[icon] Missing sizes on classpath: " + missing + " - found " + icons.size()
                    + " of 7. App will still show an icon, just without every resolution Windows might want.");
        }
        return icons;
    }

    private static JPanel labeled(String name, JSlider slider) {
        JPanel p = new JPanel(new BorderLayout());
        p.add(new JLabel(name), BorderLayout.NORTH);
        p.add(slider, BorderLayout.CENTER);
        return p;
    }

    /**
     * Slider + trailing value label sized for the tight 2-column otherParams grid (as
     * opposed to labeledScaled's full-width knob row). formatter renders the raw 0-max
     * slider value - callers pass a scaled formatter (formatBiasPercent, etc.) where a
     * confirmed display scale exists, or String::valueOf for fields with no known scale
     * yet (Gain 2, Master Volume - Section 14.1/14.3).
     */
    private static JPanel compactSliderWithValue(JSlider slider, java.util.function.IntFunction<String> formatter) {
        JLabel valueLabel = new JLabel(formatter.apply(slider.getValue()), SwingConstants.RIGHT);
        valueLabel.setPreferredSize(new Dimension(60, valueLabel.getPreferredSize().height));
        JPanel p = new JPanel(new BorderLayout(4, 0));
        p.add(slider, BorderLayout.CENTER);
        p.add(valueLabel, BorderLayout.EAST);
        slider.addChangeListener(e -> {
            String text = formatter.apply(slider.getValue());
            valueLabel.setText(text);
            slider.setToolTipText("raw " + slider.getValue() + " -> " + text);
        });
        slider.setToolTipText("raw " + slider.getValue() + " -> " + formatter.apply(slider.getValue()));
        return p;
    }

    private void setSlidersEnabled(boolean enabled) {
        for (JSlider s : new JSlider[]{volumeSlider, gainSlider, trebleSlider, middleSlider, bassSlider, presenceSlider,
                gain2Slider, masterVolumeSlider, biasSlider, usbGainSlider}) {
            s.setEnabled(enabled);
        }
        cabinetCombo.setEnabled(enabled);
        ampModelCombo.setEnabled(enabled);
        sagCombo.setEnabled(enabled);
        noiseGateCombo.setEnabled(enabled);
        for (int slot = 0; slot < 4; slot++) {
            effectModelCombos[slot].setEnabled(enabled);
            effectEnabledChecks[slot].setEnabled(enabled);
            fxLoopChecks[slot].setEnabled(enabled);
            for (int k = 0; k < 6; k++) setKnobCellEnabled(slot, k, enabled);
        }
        refreshThresholdDepthEnabled();
        refreshBrightnessEnabled();
        refreshPresenceLabel();
        refreshGain2Display();
        refreshSagBiasEnabled();
    }

    // ================= Connect / Refresh =================

    private void connectInBackground() {
        // Guard against re-entrancy: without this, an impatient double-click while the
        // first Connect is still mid-handshake spins up a second SwingWorker racing the
        // same USB reads as the first - the exact "couldn't locate amp-settings block"
        // failure the packet-reading code already anticipates for genuine USB timing
        // hiccups, but for an avoidable reason instead.
        if (connectButton != null && !connectButton.isEnabled()) return;
        if (connectButton != null) connectButton.setEnabled(false);
        // Close any previous connection before opening a new one - otherwise a failed
        // Connect (e.g. the amp momentarily not responding) followed by the user simply
        // clicking Connect again silently abandons the old HidDevice/HidServices without
        // ever closing it. A few such retries can leave the amp appearing "busy" to the
        // OS/driver until it's physically unplugged and replugged.
        if (conn != null) {
            try {
                conn.close();
            } catch (Exception ignored) {
            }
            conn = null;
        }
        statusLabel.setText("Connecting...");
        new SwingWorker<CurrentPreset, Void>() {
            String error = null;

            @Override
            protected CurrentPreset doInBackground() {
                try {
                    conn = MustangConnection.connect();
                    return conn.readCurrentPreset();
                } catch (IllegalStateException e) {
                    error = e.getMessage();
                    return null;
                }
            }

            @Override
            protected void done() {
                try {
                    CurrentPreset preset = get();
                    if (preset == null) {
                        statusLabel.setText("Error: " + error);
                        return;
                    }
                    current = preset;
                    statusLabel.setText("Connected");
                    setSlidersEnabled(true);
                    populateFromCurrent();
                    exportPresetButton.setEnabled(true);
                    if (backupAllButton != null) {
                        backupAllButton.setEnabled(true);
                    }
                    if (exportCsvButton != null) {
                        exportCsvButton.setEnabled(true);
                    }
                } catch (Exception e) {
                    statusLabel.setText("Error: " + e.getMessage());
                } finally {
                    if (connectButton != null) connectButton.setEnabled(true);
                }
            }
        }.execute();
    }

    private void refreshInBackground() {
        if (conn == null) return;
        if (refreshButton != null && !refreshButton.isEnabled()) return;
        if (refreshButton != null) refreshButton.setEnabled(false);
        statusLabel.setText("Refreshing...");
        new SwingWorker<CurrentPreset, Void>() {
            @Override
            protected CurrentPreset doInBackground() {
                return conn.readCurrentPreset();
            }

            @Override
            protected void done() {
                try {
                    current = get();
                    statusLabel.setText("Connected");
                    populateFromCurrent();
                } catch (Exception e) {
                    statusLabel.setText("Error: " + e.getMessage());
                } finally {
                    if (refreshButton != null) refreshButton.setEnabled(true);
                }
            }
        }.execute();
    }

    private void populateFromCurrent() {
        String presetNumberText = (current.presetNumber() >= 0) ? String.valueOf(current.presetNumber()) : "imported";
        presetLabel.setText("Preset: " + presetNumberText + " - " + current.name());
        ampModelLabel.setText("Amp: " + current.amp().model());

        presetListModel.clear();
        List<String> names = current.presetNames();
        for (int i = 0; i < names.size(); i++) {
            presetListModel.addElement(i + ": " + names.get(i));
        }

        applyingProgrammatically = true;
        AmpSettings a = current.amp();
        ampModelCombo.setSelectedItem(a.model());
        refreshPresenceLabel();
        volumeSlider.setValue(a.volume());
        gainSlider.setValue(a.gain());
        trebleSlider.setValue(a.treble());
        middleSlider.setValue(a.middle());
        bassSlider.setValue(a.bass());
        presenceSlider.setValue(a.presence());
        cabinetCombo.setSelectedItem(a.cabinet());

        gain2Slider.setValue(a.gain2());
        refreshGain2Display();
        masterVolumeSlider.setValue(a.masterVolume());
        depthSlider.setValue(a.depth());
        biasSlider.setValue(a.bias());
        noiseGateCombo.setSelectedIndex(Math.max(0, Math.min(5, a.noiseGate())));
        thresholdSlider.setValue(a.threshold());
        refreshThresholdDepthEnabled();
        sagCombo.setSelectedIndex(a.sag());
        brightnessCheck.setSelected(a.brightness() != 0);
        refreshBrightnessEnabled();
        refreshSagBiasEnabled();
        usbGainSlider.setValue(a.usbGain());

        for (int slot = 0; slot < 4; slot++) {
            EffectSettings fx = current.effects()[slot];
            EffectModel model = (fx.model() != null) ? fx.model() : EffectModel.EMPTY;
            effectModelCombos[slot].setSelectedItem(model);
            effectEnabledChecks[slot].setSelected(fx.enabled());
            fxLoopChecks[slot].setSelected(fx.slot() >= 4);
            updateEmptySlotState(slot, model);
            applyKnobSpecsForModel(slot, model, new int[]{fx.knob1(), fx.knob2(), fx.knob3(), fx.knob4(), fx.knob5(), fx.knob6()});
        }
        applyingProgrammatically = false;
    }

    private AmpSettings buildAmpSettingsFromSliders() {
        AmpModel model = (AmpModel) ampModelCombo.getSelectedItem();
        // Brightness is a real control only for AmpModel.SUPPORTS_BRIGHTNESS amps - for
        // every other amp there's no Brightness switch, so pass through whatever
        // was last read rather than forcing the checkbox's (disabled, possibly stale)
        // state onto a byte that doesn't mean the same thing on that model.
        int brightness = (model != null && AmpModel.SUPPORTS_BRIGHTNESS.contains(model))
                ? (brightnessCheck.isSelected() ? 1 : 0)
                : current.amp().brightness();
        return new AmpSettings(
                model,
                volumeSlider.getValue(),
                gainSlider.getValue(),
                gain2Slider.getValue(),
                masterVolumeSlider.getValue(),
                trebleSlider.getValue(),
                middleSlider.getValue(),
                bassSlider.getValue(),
                presenceSlider.getValue(),
                UNKNOWN_BYTE24,
                depthSlider.getValue(),
                biasSlider.getValue(),
                noiseGateCombo.getSelectedIndex(),
                thresholdSlider.getValue(),
                (CabinetModel) cabinetCombo.getSelectedItem(),
                sagCombo.getSelectedIndex(),
                brightness,
                usbGainSlider.getValue()
        );
    }

    private int readKnobValue(int slot, int knobIndex) {
        KnobSpec spec = activeKnobSpecs[slot][knobIndex];
        if (spec != null && spec.type() == KnobSpec.KnobControlType.DROPDOWN) {
            return knobDropdowns[slot][knobIndex].getSelectedIndex();
        }
        if (spec != null && spec.type() == KnobSpec.KnobControlType.TOGGLE) {
            return knobToggles[slot][knobIndex].isSelected() ? 1 : 0;
        }
        return knobSliders[slot][knobIndex].getValue();
    }

    private EffectSettings buildEffectSettingsFromControls(int slot) {
        EffectModel model = (EffectModel) effectModelCombos[slot].getSelectedItem();
        int fxSlotId = slot + (fxLoopChecks[slot].isSelected() ? 4 : 0); // FxSlot.h: id >= 4 means post-preamp
        return new EffectSettings(
                fxSlotId,
                model,
                readKnobValue(slot, 0),
                readKnobValue(slot, 1),
                readKnobValue(slot, 2),
                readKnobValue(slot, 3),
                readKnobValue(slot, 4),
                readKnobValue(slot, 5),
                effectEnabledChecks[slot].isSelected()
        );
    }

    private void writeAmpSettingsInBackground(AmpSettings settings) {
        if (conn == null) {
            statusLabel.setText("Preview only (not connected) - change not sent");
            current = new CurrentPreset(current.presetNumber(), current.name(), settings, current.effects(), current.presetNames());
            return;
        }
        statusLabel.setText("Sending...");
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                conn.writeAmpSettings(settings);
                return null;
            }

            @Override
            protected void done() {
                current = new CurrentPreset(current.presetNumber(), current.name(), settings, current.effects(), current.presetNames());
                statusLabel.setText("Connected");
            }
        }.execute();
    }

    /** Current UI state (including unsent slider tweaks) as a preset snapshot. */
    private CurrentPreset buildPresetFromUi() {
        EffectSettings[] effects = new EffectSettings[4];
        for (int slot = 0; slot < 4; slot++) {
            effects[slot] = buildEffectSettingsFromControls(slot);
        }
        return new CurrentPreset(
                current.presetNumber(),
                current.name(),
                buildAmpSettingsFromSliders(),
                effects,
                current.presetNames()
        );
    }

    private void exportPresetToFuse() {
        if (current == null) {
            JOptionPane.showMessageDialog(this, "Connect and load a preset first.", "Export", JOptionPane.WARNING_MESSAGE);
            return;
        }

        CurrentPreset toExport = buildPresetFromUi();
        String suggested = FusePresetExporter.suggestFileName(toExport.name());

        Path defaultDir = Paths.get(System.getProperty("user.home"), "Documents", "Fender", "FUSE", "Presets");
        JFileChooser chooser = new JFileChooser(defaultDir.toFile());
        chooser.setDialogTitle("Export Fuse preset");
        chooser.setSelectedFile(new java.io.File(suggested));
        chooser.setFileFilter(new FileNameExtensionFilter("Fuse preset (*.fuse)", "fuse"));

        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }

        Path path = chooser.getSelectedFile().toPath();
        String fileName = path.getFileName().toString();
        if (!fileName.toLowerCase().endsWith(".fuse")) {
            path = path.resolveSibling(fileName + ".fuse");
        }

        try {
            Files.writeString(path, FusePresetExporter.toXml(toExport), StandardCharsets.UTF_8);
            statusLabel.setText("Exported to " + path.getFileName());
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Could not write file:\n" + ex.getMessage(),
                    "Export failed", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Imports a .fuse file and displays it for preview - works fully offline,
     * no amp connection needed. Sliders/combos/checkboxes ARE left enabled
     * after import, so the imported preset can be tweaked before ever
     * connecting: writeAmpSettingsInBackground()/writeEffectSettingsInBackground()
     * both already guard on conn == null and just update local preview state
     * ("Preview only (not connected) - change not sent") rather than attempting
     * a write, so there's no null-pointer risk. Connect afterward to actually
     * send changes to the amp.
     */
    private void importPresetFromFuse() {
        Path defaultDir = Paths.get(System.getProperty("user.home"), "Documents", "Fender", "FUSE");
        JFileChooser chooser = new JFileChooser(defaultDir.toFile());
        chooser.setDialogTitle("Import Fuse preset");
        chooser.setFileFilter(new FileNameExtensionFilter("Fuse preset (*.fuse)", "fuse"));

        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path path = chooser.getSelectedFile().toPath();

        try {
            CurrentPreset imported = FusePresetImporter.fromFile(path);
            current = imported;
            setSlidersEnabled(true);
            populateFromCurrent();
            statusLabel.setText("Imported " + path.getFileName()
                    + (conn == null ? " (preview only - changes won't be sent until you connect)" : ""));
        } catch (IllegalArgumentException | IOException ex) {
            JOptionPane.showMessageDialog(this, "Could not import file:\n" + ex.getMessage(),
                    "Import failed", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void backupAllPresetsToZip() {
        if (conn == null || current == null) {
            JOptionPane.showMessageDialog(this, "Connect to the amp first.", "Backup", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int proceed = JOptionPane.showConfirmDialog(this,
                "This reads all " + FusePresetBackup.SLOT_COUNT + " preset slots from the amp over USB.\n"
                        + "It typically takes 1–3 minutes — keep the amp plugged in and powered on.\n\n"
                        + "Continue?",
                "Backup all presets",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.INFORMATION_MESSAGE);
        if (proceed != JOptionPane.OK_OPTION) {
            return;
        }

        Path defaultDir = Paths.get(System.getProperty("user.home"), "Documents", "Fender", "FUSE", "Backups");
        JFileChooser chooser = new JFileChooser(defaultDir.toFile());
        chooser.setDialogTitle("Backup all presets");
        chooser.setSelectedFile(new java.io.File(FusePresetBackup.suggestZipFileName()));
        chooser.setFileFilter(new FileNameExtensionFilter("Mustang preset backup (*.zip)", "zip"));

        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }

        Path zipPath = chooser.getSelectedFile().toPath();
        String zipName = zipPath.getFileName().toString();
        if (!zipName.toLowerCase().endsWith(".zip")) {
            zipPath = zipPath.resolveSibling(zipName + ".zip");
        }

        final int returnToSlot = current.presetNumber();
        final Path targetZip = zipPath;

        JDialog progressDialog = new JDialog(this, "Backing up presets", true);
        progressDialog.setLayout(new BorderLayout(10, 10));
        JPanel progressNorth = new JPanel(new GridLayout(2, 1, 0, 4));
        progressNorth.add(new JLabel("Reading each slot from the amp — usually 1–3 min for 100 presets."));
        JLabel progressLabel = new JLabel("Starting...");
        progressNorth.add(progressLabel);
        progressDialog.add(progressNorth, BorderLayout.NORTH);
        JProgressBar progressBar = new JProgressBar(0, FusePresetBackup.SLOT_COUNT);
        progressBar.setStringPainted(true);
        progressDialog.add(progressBar, BorderLayout.CENTER);
        JButton cancelButton = new JButton("Cancel");
        progressDialog.add(cancelButton, BorderLayout.SOUTH);
        progressDialog.setSize(480, 150);
        progressDialog.setLocationRelativeTo(this);

        AtomicBoolean cancelled = new AtomicBoolean(false);
        cancelButton.addActionListener(e -> cancelled.set(true));

        exportPresetButton.setEnabled(false);
        if (backupAllButton != null) {
            backupAllButton.setEnabled(false);
        }
        if (exportCsvButton != null) {
            exportCsvButton.setEnabled(false);
        }

        SwingWorker<FusePresetBackup.BackupResult, Void> worker = new SwingWorker<>() {
            @Override
            protected FusePresetBackup.BackupResult doInBackground() throws Exception {
                return FusePresetBackup.exportAll(conn, targetZip, returnToSlot, new FusePresetBackup.Progress() {
                    @Override
                    public boolean isCancelled() {
                        return cancelled.get();
                    }

                    @Override
                    public void onSlot(int slot, int totalSlots, String presetName) {
                        int pct = (int) ((slot + 1) * 100L / totalSlots);
                        SwingUtilities.invokeLater(() -> {
                            progressBar.setValue(slot + 1);
                            progressBar.setString((slot + 1) + " / " + totalSlots);
                            progressLabel.setText("Slot " + slot + ": " + presetName);
                        });
                    }
                });
            }

            @Override
            protected void done() {
                progressDialog.dispose();
                exportPresetButton.setEnabled(true);
                if (backupAllButton != null) {
                    backupAllButton.setEnabled(true);
                }
                if (exportCsvButton != null) {
                    exportCsvButton.setEnabled(true);
                }
                try {
                    FusePresetBackup.BackupResult result = get();
                    try {
                        current = conn.readPresetAtSlot(returnToSlot, current.presetNames());
                        populateFromCurrent();
                    } catch (Exception ignored) {
                    }
                    String msg = result.cancelled()
                            ? "Backup cancelled.\nSaved " + result.slotsSucceeded() + " preset(s) before stop."
                            : "Backed up " + result.slotsSucceeded() + " / " + FusePresetBackup.SLOT_COUNT + " presets.";
                    if (!result.failedSlots().isEmpty()) {
                        msg += "\nFailed slots: " + result.failedSlots();
                    }
                    msg += "\n\n" + result.zipPath();
                    statusLabel.setText("Backup saved (" + result.slotsSucceeded() + " presets)");
                    JOptionPane.showMessageDialog(PresetPony.this, msg, "Backup complete",
                            result.failedSlots().isEmpty() ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.WARNING_MESSAGE);
                } catch (Exception ex) {
                    statusLabel.setText("Backup failed");
                    JOptionPane.showMessageDialog(PresetPony.this, ex.getMessage(), "Backup failed",
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        };

        worker.execute();
        progressDialog.setVisible(true);
    }

    private void exportAllPresetsToCsv() {
        if (conn == null || current == null) {
            JOptionPane.showMessageDialog(this, "Connect to the amp first.", "Export CSV", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int proceed = JOptionPane.showConfirmDialog(this,
                "This reads all " + PresetCsvBackup.SLOT_COUNT + " preset slots from the amp over USB.\n"
                        + "It typically takes 1–3 minutes — keep the amp plugged in and powered on.\n\n"
                        + "Continue?",
                "Export all presets to CSV",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.INFORMATION_MESSAGE);
        if (proceed != JOptionPane.OK_OPTION) {
            return;
        }

        Path defaultDir = Paths.get(System.getProperty("user.home"), "Documents", "Fender", "FUSE");
        JFileChooser chooser = new JFileChooser(defaultDir.toFile());
        chooser.setDialogTitle("Export all presets to CSV");
        chooser.setSelectedFile(new java.io.File(PresetCsvBackup.suggestCsvFileName()));
        chooser.setFileFilter(new FileNameExtensionFilter("CSV (*.csv)", "csv"));

        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }

        Path csvPath = chooser.getSelectedFile().toPath();
        String csvName = csvPath.getFileName().toString();
        if (!csvName.toLowerCase().endsWith(".csv")) {
            csvPath = csvPath.resolveSibling(csvName + ".csv");
        }

        final int returnToSlot = current.presetNumber();
        final Path targetCsv = csvPath;

        JDialog progressDialog = new JDialog(this, "Exporting presets to CSV", true);
        progressDialog.setLayout(new BorderLayout(10, 10));
        JPanel progressNorth = new JPanel(new GridLayout(2, 1, 0, 4));
        progressNorth.add(new JLabel("Reading each slot from the amp — usually 1–3 min for 100 presets."));
        JLabel progressLabel = new JLabel("Starting...");
        progressNorth.add(progressLabel);
        progressDialog.add(progressNorth, BorderLayout.NORTH);
        JProgressBar progressBar = new JProgressBar(0, PresetCsvBackup.SLOT_COUNT);
        progressBar.setStringPainted(true);
        progressDialog.add(progressBar, BorderLayout.CENTER);
        JButton cancelButton = new JButton("Cancel");
        progressDialog.add(cancelButton, BorderLayout.SOUTH);
        progressDialog.setSize(480, 150);
        progressDialog.setLocationRelativeTo(this);

        AtomicBoolean cancelled = new AtomicBoolean(false);
        cancelButton.addActionListener(e -> cancelled.set(true));

        exportPresetButton.setEnabled(false);
        if (backupAllButton != null) {
            backupAllButton.setEnabled(false);
        }
        if (exportCsvButton != null) {
            exportCsvButton.setEnabled(false);
        }

        SwingWorker<PresetCsvBackup.CsvBackupResult, Void> worker = new SwingWorker<>() {
            @Override
            protected PresetCsvBackup.CsvBackupResult doInBackground() throws Exception {
                return PresetCsvBackup.exportAll(conn, targetCsv, returnToSlot, new PresetCsvBackup.Progress() {
                    @Override
                    public boolean isCancelled() {
                        return cancelled.get();
                    }

                    @Override
                    public void onSlot(int slot, int totalSlots, String presetName) {
                        SwingUtilities.invokeLater(() -> {
                            progressBar.setValue(slot + 1);
                            progressBar.setString((slot + 1) + " / " + totalSlots);
                            progressLabel.setText("Slot " + slot + ": " + presetName);
                        });
                    }
                });
            }

            @Override
            protected void done() {
                progressDialog.dispose();
                exportPresetButton.setEnabled(true);
                if (backupAllButton != null) {
                    backupAllButton.setEnabled(true);
                }
                if (exportCsvButton != null) {
                    exportCsvButton.setEnabled(true);
                }
                try {
                    PresetCsvBackup.CsvBackupResult result = get();
                    try {
                        current = conn.readPresetAtSlot(returnToSlot, current.presetNames());
                        populateFromCurrent();
                    } catch (Exception ignored) {
                    }
                    String msg = result.cancelled()
                            ? "Export cancelled.\nSaved " + result.slotsSucceeded() + " preset(s) before stop."
                            : "Exported " + result.slotsSucceeded() + " / " + PresetCsvBackup.SLOT_COUNT + " presets.";
                    if (!result.failedSlots().isEmpty()) {
                        msg += "\nFailed slots: " + result.failedSlots();
                    }
                    msg += "\n\n" + result.csvPath();
                    statusLabel.setText("CSV saved (" + result.slotsSucceeded() + " presets)");
                    JOptionPane.showMessageDialog(PresetPony.this, msg, "Export complete",
                            result.failedSlots().isEmpty() ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.WARNING_MESSAGE);
                } catch (Exception ex) {
                    statusLabel.setText("CSV export failed");
                    JOptionPane.showMessageDialog(PresetPony.this, ex.getMessage(), "Export failed",
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        };

        worker.execute();
        progressDialog.setVisible(true);
    }

    private void writeEffectSettingsInBackground(int slotIndex, EffectSettings settings) {
        if (conn == null) {
            statusLabel.setText("Preview only (not connected) - change not sent");
            EffectSettings[] updated = current.effects().clone();
            updated[slotIndex] = settings;
            current = new CurrentPreset(current.presetNumber(), current.name(), current.amp(), updated, current.presetNames());
            return;
        }
        statusLabel.setText("Sending...");
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                conn.writeEffectSettings(slotIndex, settings);
                return null;
            }

            @Override
            protected void done() {
                EffectSettings[] updated = current.effects().clone();
                updated[slotIndex] = settings;
                current = new CurrentPreset(current.presetNumber(), current.name(), current.amp(), updated, current.presetNames());
                statusLabel.setText("Connected");
            }
        }.execute();
    }

    public static void main(String[] args) {
        EffectKnobScaleFacts.applyDefault();
        SwingUtilities.invokeLater(() -> new PresetPony().setVisible(true));
    }
}
