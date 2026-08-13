package com.electrosparkles.presetpony.ui.tabs;

import com.electrosparkles.presetpony.*;
import com.electrosparkles.presetpony.ui.ControlStateDelegate;
import com.electrosparkles.presetpony.ui.StatusUpdater;
import com.electrosparkles.presetpony.ui.TabPanel;
import javax.swing.*;
import java.awt.*;

/**
 * Toybox tab: randomization engine with keep flags and dump display.
 */
public class ToyboxTabPanel extends TabPanel {
    private final JPanel panel;

    private final JCheckBox keepAmpModelCheck = new JCheckBox("Amp (model)");
    private final JCheckBox keepAmpEqCheck = new JCheckBox("Amp EQ");
    private final JCheckBox keepAmpTuningCheck = new JCheckBox("Amp tuning");
    private final JCheckBox keepCabCheck = new JCheckBox("Cab");
    private final JCheckBox pairedCabCheck = new JCheckBox("Paired (cab follows amp default)", true);
    private final JCheckBox[] keepEffectModelChecks = new JCheckBox[4];
    private final JCheckBox[] keepEffectSettingsChecks = new JCheckBox[4];
    private final JTextArea toyboxDumpArea = new JTextArea();
    private final JButton randomiseButton;

    public ToyboxTabPanel(StatusUpdater statusUpdater, ControlStateDelegate controlDelegate) {
        super(statusUpdater, controlDelegate);
        randomiseButton = new JButton("Randomise!");
        panel = buildPanel();
    }

    @Override
    public JPanel getPanel() {
        return panel;
    }

    @Override
    public void refresh(CurrentPreset preset) {
        this.current = preset;
        // Dump is only updated on doRandomise(), not on passive refresh
    }

    @Override
    public void setConnectionState(MustangConnection connection, boolean connected) {
        this.conn = connection;
        randomiseButton.setEnabled(true); // Always available, even offline
    }

    private JPanel buildPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JLabel hintLabel = new JLabel("<html>Randomise your amp, cab, EQ, and effects. Check anything you want to keep, then hit Randomise. Applied live if connected. Adjust amp volume as needed before touching master volume</html>");
        hintLabel.setFont(hintLabel.getFont().deriveFont(10f));
        hintLabel.setForeground(Color.GRAY);
        hintLabel.setBorder(BorderFactory.createEmptyBorder(0, 2, 6, 2));

        JPanel keepPanel = new JPanel();
        keepPanel.setLayout(new BoxLayout(keepPanel, BoxLayout.Y_AXIS));
        keepPanel.setBorder(BorderFactory.createTitledBorder("Keep current"));
        for (JCheckBox cb : new JCheckBox[]{keepAmpModelCheck, keepAmpEqCheck, keepAmpTuningCheck}) {
            JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
            row.add(cb);
            keepPanel.add(row);
        }

        JPanel cabRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        cabRow.add(keepCabCheck);
        cabRow.add(pairedCabCheck);
        keepPanel.add(cabRow);

        String[] slotNames = {"Stomp", "Mod", "Delay", "Reverb"};
        for (int slot = 0; slot < 4; slot++) {
            JCheckBox modelCheck = new JCheckBox("Keep " + slotNames[slot] + " effect");
            JCheckBox settingsCheck = new JCheckBox("Keep " + slotNames[slot] + " settings");
            settingsCheck.setEnabled(false);
            keepEffectModelChecks[slot] = modelCheck;
            keepEffectSettingsChecks[slot] = settingsCheck;
            modelCheck.addActionListener(e -> {
                boolean on = modelCheck.isSelected();
                settingsCheck.setEnabled(on);
                if (!on) settingsCheck.setSelected(false);
            });
            JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
            row.add(modelCheck);
            row.add(settingsCheck);
            keepPanel.add(row);
        }
        JPanel northPanel = new JPanel(new BorderLayout());
        northPanel.add(hintLabel, BorderLayout.NORTH);
        northPanel.add(keepPanel, BorderLayout.CENTER);
        panel.add(northPanel, BorderLayout.NORTH);

        toyboxDumpArea.setEditable(false);
        toyboxDumpArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        panel.add(new JScrollPane(toyboxDumpArea), BorderLayout.CENTER);

        randomiseButton.addActionListener(e -> doRandomise());
        panel.add(randomiseButton, BorderLayout.SOUTH);

        return panel;
    }

    private void doRandomise() {
        if (current == null) {
            JOptionPane.showMessageDialog(panel, "Connect (or import a preset) first.", "Randomise", JOptionPane.WARNING_MESSAGE);
            return;
        }
        RandomiseEngine.KeepFlags keep = buildKeepFlagsFromToybox();
        CurrentPreset randomised = RandomiseEngine.randomise(current, keep, new java.util.Random());

        randomiseButton.setEnabled(false);
        updateStatus(conn == null ? "Randomising (preview only - not connected)..." : "Randomising...");

        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                if (conn != null) {
                    conn.writeAmpSettings(randomised.amp());
                    for (int slot = 0; slot < 4; slot++) {
                        conn.writeEffectSettings(slot, randomised.effects()[slot]);
                    }
                }
                return null;
            }

            @Override
            protected void done() {
                current = randomised;
                updateToyboxDump(keep);
                updateStatus(conn == null ? "Preview only (not connected) - change not sent" : "Connected");
                randomiseButton.setEnabled(true);
            }
        }.execute();
    }

    private void updateToyboxDump(RandomiseEngine.KeepFlags keep) {
        AmpSettings a = current.amp();
        String[] slotNames = {"Stomp", "Mod", "Delay", "Reverb"};
        StringBuilder sb = new StringBuilder();

        sb.append("Amp: ").append(a.model()).append(keep.keepAmpModel() ? " (kept)" : "")
                .append("   Cab: ").append(a.cabinet()).append(keep.keepCab() ? " (kept)" : "").append('\n');
        sb.append("Effects: ");
        boolean anyEffect = false;
        for (int slot = 0; slot < 4; slot++) {
            EffectSettings fx = current.effects()[slot];
            EffectModel model = (fx.model() != null) ? fx.model() : EffectModel.EMPTY;
            if (model == EffectModel.EMPTY) continue;
            if (anyEffect) sb.append(", ");
            sb.append(model).append(fx.enabled() ? "" : " (off)");
            anyEffect = true;
        }
        if (!anyEffect) sb.append("(none)");
        sb.append("\n\n");

        sb.append("-- Amp EQ --\n");
        String eqSuffix = keep.keepAmpEq() ? " (kept)" : "";
        sb.append("Volume:      ").append(AmpKnobScale.formatMainEq(a.volume())).append(eqSuffix).append('\n');
        sb.append("Gain:        ").append(AmpKnobScale.formatMainEq(a.gain())).append(eqSuffix).append('\n');
        sb.append("Treble:      ").append(AmpKnobScale.formatMainEq(a.treble())).append(eqSuffix).append('\n');
        sb.append("Middle:      ").append(AmpKnobScale.formatMainEq(a.middle())).append(eqSuffix).append('\n');
        sb.append("Bass:        ").append(AmpKnobScale.formatMainEq(a.bass())).append(eqSuffix).append('\n');
        sb.append(a.model().presenceUiLabel()).append(": ").append(AmpKnobScale.formatMainEq(a.presence())).append(eqSuffix).append('\n');

        sb.append("\n-- Amp tuning (page 2) --\n");
        String tuningSuffix = keep.keepAmpTuning() ? " (kept)" : "";
        sb.append(a.model().gain2UiLabel()).append(": ").append(AmpKnobScale.formatGain2Value(a.gain2())).append(tuningSuffix).append('\n');
        sb.append("Bias:        ").append(AmpKnobScale.formatBiasPercent(a.bias())).append(tuningSuffix).append('\n');
        sb.append("Sag:         ").append(new String[]{"Less", "Match", "More"}[a.sag()]).append(tuningSuffix).append('\n');
        sb.append("Brightness:  ").append(a.brightness() != 0 ? "On" : "Off").append(tuningSuffix).append('\n');
        sb.append("Noise gate:  ").append(AmpKnobScale.NOISE_GATE_LABELS[a.noiseGate()]).append(tuningSuffix).append('\n');
        sb.append("Threshold:   ").append(a.threshold()).append(tuningSuffix).append('\n');
        sb.append("Depth:       ").append(AmpKnobScale.formatDepthPercent(a.depth())).append(tuningSuffix).append('\n');
        sb.append("USB gain:    ").append(AmpKnobScale.formatUsbGainPercent(a.usbGain())).append(tuningSuffix).append('\n');
        sb.append("Master vol:  ").append(AmpKnobScale.formatMainEq(a.masterVolume())).append(tuningSuffix).append('\n');

        for (int slot = 0; slot < 4; slot++) {
            EffectSettings fx = current.effects()[slot];
            EffectModel model = (fx.model() != null) ? fx.model() : EffectModel.EMPTY;
            boolean keptModel = keep.keepEffectModel()[slot];
            boolean keptSettings = keptModel && keep.keepEffectSettings()[slot];
            sb.append("\n-- ").append(slotNames[slot]).append(": ").append(model).append(keptModel ? " (kept)" : "").append(" --\n");
            for (int k = 0; k < 6; k++) {
                KnobSpec spec = model.knobs[k];
                if (!spec.isUsed()) continue;
                int raw = switch (k) {
                    case 0 -> fx.knob1();
                    case 1 -> fx.knob2();
                    case 2 -> fx.knob3();
                    case 3 -> fx.knob4();
                    case 4 -> fx.knob5();
                    default -> fx.knob6();
                };
                sb.append("  ").append(spec.label()).append(": ").append(formatKnobValue(spec, raw))
                        .append(keptSettings ? " (kept)" : "").append('\n');
            }
        }
        toyboxDumpArea.setText(sb.toString());
        toyboxDumpArea.setCaretPosition(0);
    }

    private String formatKnobValue(KnobSpec spec, int rawValue) {
        if (spec.hasDisplayScale()) {
            return spec.formatDisplayValue(rawValue);
        }
        return String.format("%.1f%%", rawValue * 100.0 / spec.max());
    }

    private RandomiseEngine.KeepFlags buildKeepFlagsFromToybox() {
        boolean[] keepModel = new boolean[4];
        boolean[] keepSettings = new boolean[4];
        for (int slot = 0; slot < 4; slot++) {
            keepModel[slot] = keepEffectModelChecks[slot].isSelected();
            keepSettings[slot] = keepEffectSettingsChecks[slot].isSelected();
        }
        return new RandomiseEngine.KeepFlags(
                keepAmpModelCheck.isSelected(),
                keepAmpEqCheck.isSelected(),
                keepAmpTuningCheck.isSelected(),
                keepCabCheck.isSelected(),
                pairedCabCheck.isSelected(),
                keepModel,
                keepSettings
        );
    }
}
