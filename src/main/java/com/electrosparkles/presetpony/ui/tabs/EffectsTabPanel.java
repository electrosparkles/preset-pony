package com.electrosparkles.presetpony.ui.tabs;

import com.electrosparkles.presetpony.*;
import com.electrosparkles.presetpony.ui.ControlStateDelegate;
import com.electrosparkles.presetpony.ui.StatusUpdater;
import com.electrosparkles.presetpony.ui.TabPanel;
import com.electrosparkles.presetpony.ui.components.EffectSlotPanel;
import javax.swing.*;

/**
 * Effects tab: 4 effect slots using EffectSlotPanel component.
 */
public class EffectsTabPanel extends TabPanel {
    private final JPanel panel;
    private final EffectSlotPanel[] slotPanels = new EffectSlotPanel[4];

    public EffectsTabPanel(StatusUpdater statusUpdater, ControlStateDelegate controlDelegate) {
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
        for (int slot = 0; slot < 4; slot++) {
            slotPanels[slot].updateFromSettings(current.effects()[slot]);
        }
    }

    @Override
    public void setConnectionState(MustangConnection connection, boolean connected) {
        this.conn = connection;
        for (EffectSlotPanel slot : slotPanels) {
            slot.setControlsEnabled(connected);
        }
    }

    private JPanel buildPanel() {
        JPanel effectsPanel = new JPanel(new java.awt.GridLayout(4, 1, 8, 8));
        effectsPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        String[] slotNames = {"Slot 1 (distortion/dynamics)", "Slot 2 (modulation)", "Slot 3 (delay)", "Slot 4 (reverb)"};

        for (int slot = 0; slot < 4; slot++) {
            slotPanels[slot] = new EffectSlotPanel(slot, slotNames[slot], conn, current);
            final int slotIndex = slot;
            slotPanels[slot].setOnEffectChanged(settings -> {
                if (current == null) return;
                writeEffectSettingsInBackground(slotIndex, settings);
            });
            effectsPanel.add(slotPanels[slot]);
        }
        return effectsPanel;
    }
}
