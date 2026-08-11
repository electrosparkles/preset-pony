package com.electrosparkles.presetpony.ui.tabs;

import com.electrosparkles.presetpony.*;
import com.electrosparkles.presetpony.ui.ControlStateDelegate;
import com.electrosparkles.presetpony.ui.StatusUpdater;
import com.electrosparkles.presetpony.ui.TabPanel;
import com.electrosparkles.presetpony.ui.shared.KnobFactory;
import javax.swing.*;
import java.awt.*;

/**
 * Amp tab: amp model/cabinet picker, 6 main EQ knobs, read-only extra params.
 */
public class AmpTabPanel extends TabPanel {
    private final JPanel panel;

    private final JSlider volumeSlider = KnobFactory.newKnobSlider();
    private final JSlider gainSlider = KnobFactory.newKnobSlider();
    private final JSlider trebleSlider = KnobFactory.newKnobSlider();
    private final JSlider middleSlider = KnobFactory.newKnobSlider();
    private final JSlider bassSlider = KnobFactory.newKnobSlider();
    private final JSlider presenceSlider = KnobFactory.newKnobSlider();
    private final JLabel presenceNameLabel = new JLabel("Presence");
    private final JComboBox<CabinetModel> cabinetCombo = new JComboBox<>(CabinetModel.values());
    private final JComboBox<AmpModel> ampModelCombo = new JComboBox<>(AmpModel.values());

    private final JSlider gain2Slider = KnobFactory.newKnobSlider();
    private final JLabel gain2NameLabel = new JLabel("Gain 2:");
    private final JLabel gain2ValueLabel = new JLabel("", SwingConstants.RIGHT);
    private final JSlider masterVolumeSlider = KnobFactory.newKnobSlider();
    private final JSlider depthSlider = KnobFactory.newKnobSlider();
    private final JSlider biasSlider = KnobFactory.newKnobSlider();
    private final JComboBox<String> noiseGateCombo = new JComboBox<>(AmpKnobScale.NOISE_GATE_LABELS);
    private final JSlider thresholdSlider = new JSlider(0, 9, 0);
    private final JComboBox<String> sagCombo = new JComboBox<>(new String[]{"Less", "Match", "More"});
    private final JCheckBox brightnessCheck = new JCheckBox("On");
    private final JSlider usbGainSlider = KnobFactory.newKnobSlider();

    public AmpTabPanel(StatusUpdater statusUpdater, ControlStateDelegate controlDelegate) {
        super(statusUpdater, controlDelegate);
        panel = buildPanel();
    }

    @Override
    public JPanel getPanel() {
        return panel;
    }

    @Override
    public void refresh(CurrentPreset preset) {
        this.current = preset;
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

        applyingProgrammatically = false;
    }

    @Override
    public void setConnectionState(MustangConnection connection, boolean connected) {
        this.conn = connection;
        volumeSlider.setEnabled(connected);
        gainSlider.setEnabled(connected);
        trebleSlider.setEnabled(connected);
        middleSlider.setEnabled(connected);
        bassSlider.setEnabled(connected);
        presenceSlider.setEnabled(connected);
        gain2Slider.setEnabled(connected);
        masterVolumeSlider.setEnabled(connected);
        biasSlider.setEnabled(connected);
        usbGainSlider.setEnabled(connected);
        cabinetCombo.setEnabled(connected);
        ampModelCombo.setEnabled(connected);
        sagCombo.setEnabled(connected);
        noiseGateCombo.setEnabled(connected);
        refreshThresholdDepthEnabled();
        refreshBrightnessEnabled();
        refreshPresenceLabel();
        refreshGain2Display();
        refreshSagBiasEnabled();
    }

    private JPanel buildPanel() {
        JPanel container = new JPanel(new BorderLayout(8, 8));

        JPanel modelRow = new JPanel(new BorderLayout(6, 0));
        modelRow.add(new JLabel("Amp model"), BorderLayout.WEST);
        modelRow.add(ampModelCombo, BorderLayout.CENTER);
        modelRow.setBorder(BorderFactory.createEmptyBorder(6, 10, 0, 10));
        container.add(modelRow, BorderLayout.NORTH);

        JPanel knobs = new JPanel(new GridLayout(7, 1, 4, 4));
        knobs.setBorder(BorderFactory.createEmptyBorder(6, 20, 6, 20));
        knobs.add(KnobFactory.labeledScaled("Volume", volumeSlider));
        knobs.add(KnobFactory.labeledScaled("Gain", gainSlider));
        knobs.add(KnobFactory.labeledScaled("Treble", trebleSlider));
        knobs.add(KnobFactory.labeledScaled("Middle", middleSlider));
        knobs.add(KnobFactory.labeledScaled("Bass", bassSlider));
        knobs.add(KnobFactory.labeledScaled(presenceNameLabel, presenceSlider));

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
        otherParams.add(KnobFactory.compactSliderWithValue(masterVolumeSlider, AmpKnobScale::formatMainEq));
        otherParams.add(new JLabel("Noise gate:"));
        otherParams.add(noiseGateCombo);
        otherParams.add(new JLabel("Depth:"));
        otherParams.add(KnobFactory.compactSliderWithValue(depthSlider, AmpKnobScale::formatDepthPercent));
        otherParams.add(new JLabel("Threshold:"));
        otherParams.add(KnobFactory.compactSliderWithValue(thresholdSlider, String::valueOf));
        otherParams.add(new JLabel("Bias:"));
        otherParams.add(KnobFactory.compactSliderWithValue(biasSlider, AmpKnobScale::formatBiasPercent));
        otherParams.add(new JLabel("Sag:"));
        otherParams.add(sagCombo);
        otherParams.add(new JLabel("Brightness:"));
        otherParams.add(brightnessCheck);
        otherParams.add(new JLabel("USB gain:"));
        otherParams.add(KnobFactory.compactSliderWithValue(usbGainSlider, AmpKnobScale::formatUsbGainPercent));
        container.add(otherParams, BorderLayout.SOUTH);

        wireListeners();
        return container;
    }

    private void wireListeners() {
        javax.swing.event.ChangeListener onAmpKnobChanged = ev -> {
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

            // Apply factory defaults for the new amp model before anything else fires.
            // Done inside applyingProgrammatically so slider listeners don't each
            // trigger individual writes mid-application.
            if (model != null) {
                applyDefaultsToSliders(model);
            }

            CabinetModel defaultCab = (model != null) ? AmpFacts.defaultCabinet(model) : null;
            if (defaultCab != null && cabinetCombo.getSelectedItem() != defaultCab) {
                // Setting the cabinet will trigger the cabinet combo listener, which
                // fires the write - sliders already have defaults applied above.
                cabinetCombo.setSelectedItem(defaultCab);
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
                applyingProgrammatically = true;
                thresholdSlider.setValue(AmpKnobScale.defaultThresholdForGate(gateIndex));
                depthSlider.setValue(AmpKnobScale.defaultDepthForGate(gateIndex));
                applyingProgrammatically = false;
            }
            refreshThresholdDepthEnabled();
            if (current == null) return;
            writeAmpSettingsInBackground(buildAmpSettingsFromSliders());
        });
        javax.swing.event.ChangeListener onThresholdDepthChanged = ev -> {
            if (applyingProgrammatically || ((JSlider) ev.getSource()).getValueIsAdjusting() || current == null) return;
            writeAmpSettingsInBackground(buildAmpSettingsFromSliders());
        };
        thresholdSlider.addChangeListener(onThresholdDepthChanged);
        depthSlider.addChangeListener(onThresholdDepthChanged);
    }

    /**
     * Applies AmpFacts defaults for the given model to all sliders/combos,
     * suppressing listener writes via applyingProgrammatically.
     * Fields absent from the properties file (returned as -1) are left untouched,
     * preserving whatever value the sliders currently show.
     */
    private void applyDefaultsToSliders(AmpModel model) {
        AmpDefaults d = AmpFacts.defaultsFor(model);
        if (d == null) return; // file not loaded or incomplete entry - leave sliders as-is

        applyingProgrammatically = true;
        try {
            gainSlider.setValue(d.gain());
            volumeSlider.setValue(d.volume());
            trebleSlider.setValue(d.treble());
            middleSlider.setValue(d.middle());
            bassSlider.setValue(d.bass());

            if (d.presence() >= 0)     presenceSlider.setValue(d.presence());
            if (d.gain2() >= 0)        gain2Slider.setValue(d.gain2());
            if (d.masterVolume() >= 0) masterVolumeSlider.setValue(d.masterVolume());

            // noiseGate: set gate index, then let AmpKnobScale supply threshold/depth defaults
            // (same logic as the noiseGate combo listener)
            int gate = d.noiseGate();
            noiseGateCombo.setSelectedIndex(gate);
            if (!AmpKnobScale.isCustomGate(gate)) {
                thresholdSlider.setValue(AmpKnobScale.defaultThresholdForGate(gate));
                depthSlider.setValue(AmpKnobScale.defaultDepthForGate(gate));
            } else {
                thresholdSlider.setValue(d.threshold());
                depthSlider.setValue(d.depth());
            }
            refreshThresholdDepthEnabled();

            if (d.sag() >= 0)  sagCombo.setSelectedIndex(d.sag());
            if (d.bias() >= 0) biasSlider.setValue(d.bias());

            if (d.brightness() >= 0) {
                brightnessCheck.setSelected(d.brightness() != 0);
            }
        } finally {
            applyingProgrammatically = false;
        }
        refreshGain2Display();
    }

    private void refreshThresholdDepthEnabled() {
        boolean enabled = noiseGateCombo.isEnabled() && AmpKnobScale.isCustomGate(noiseGateCombo.getSelectedIndex());
        thresholdSlider.setEnabled(enabled);
        depthSlider.setEnabled(enabled);
    }

    private void refreshBrightnessEnabled() {
        AmpModel model = (AmpModel) ampModelCombo.getSelectedItem();
        boolean supported = model != null && AmpModel.SUPPORTS_BRIGHTNESS.contains(model);
        brightnessCheck.setEnabled(supported && ampModelCombo.isEnabled());
    }

    private void refreshPresenceLabel() {
        AmpModel model = (AmpModel) ampModelCombo.getSelectedItem();
        presenceNameLabel.setText(model != null ? model.presenceUiLabel() : "Presence");
    }

    private void refreshSagBiasEnabled() {
        AmpModel model = (AmpModel) ampModelCombo.getSelectedItem();
        boolean supported = !(model != null && AmpModel.STUDIO_PREAMP_HAS_NO_SAG_BIAS.contains(model));
        sagCombo.setEnabled(supported && ampModelCombo.isEnabled());
        biasSlider.setEnabled(supported && ampModelCombo.isEnabled());
    }

    private JPanel gain2Row() {
        gain2ValueLabel.setPreferredSize(new Dimension(60, gain2ValueLabel.getPreferredSize().height));
        JPanel p = new JPanel(new BorderLayout(4, 0));
        p.add(gain2Slider, BorderLayout.CENTER);
        p.add(gain2ValueLabel, BorderLayout.EAST);
        gain2Slider.addChangeListener(e -> refreshGain2Display());
        refreshGain2Display();
        return p;
    }

    private void refreshGain2Display() {
        AmpModel model = (AmpModel) ampModelCombo.getSelectedItem();
        boolean blend = model != null && AmpModel.SUPPORTS_GAIN2_BLEND.contains(model);
        gain2NameLabel.setText((model != null ? model.gain2UiLabel() : "Gain 2") + ":");
        int raw = gain2Slider.getValue();
        String text = blend ? AmpKnobScale.formatGain2BlendPercent(raw) : AmpKnobScale.formatGain2Value(raw);
        gain2ValueLabel.setText(text);
        gain2Slider.setToolTipText("raw " + raw + " -> " + text);
    }

    private AmpSettings buildAmpSettingsFromSliders() {
        AmpModel model = (AmpModel) ampModelCombo.getSelectedItem();
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
                0,
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
}
