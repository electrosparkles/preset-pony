package com.electrosparkles.presetpony.ui.components;

import com.electrosparkles.presetpony.*;
import com.electrosparkles.presetpony.ui.shared.KnobFactory;
import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Reusable effect slot editor (one of the 4 identical slots in Effects tab).
 * Encapsulates: model combo, On/Off checkbox, FX Loop checkbox, 6 knobs (slider/dropdown/toggle).
 */
public class EffectSlotPanel extends JPanel {
    private final int slotIndex;
    private final String slotName;
    
    private final JComboBox<EffectModel> modelCombo;
    private final JCheckBox enabledCheck;
    private final JCheckBox fxLoopCheck;
    
    private final JLabel[] knobLabels = new JLabel[6];
    private final JSlider[] knobSliders = new JSlider[6];
    private final JComboBox<String>[] knobDropdowns = new JComboBox[6];
    private final JCheckBox[] knobToggles = new JCheckBox[6];
    private final JPanel[] knobCardPanels = new JPanel[6];
    private final KnobSpec[] activeKnobSpecs = new KnobSpec[6];
    
    private Consumer<EffectSettings> onEffectChanged;
    private boolean applyingProgrammatically = false;
    
    public EffectSlotPanel(int slotIndex, String slotName, MustangConnection conn, CurrentPreset current) {
        super(new BorderLayout(4, 4));
        setBorder(BorderFactory.createTitledBorder(slotName));
        
        this.slotIndex = slotIndex;
        this.slotName = slotName;
        
        // Header: On checkbox + model combo + FX Loop checkbox
        List<EffectModel> validModels = new ArrayList<>();
        validModels.add(EffectModel.EMPTY);
        for (EffectModel m : EffectModel.values()) {
            if (m.dspSlotGroup == slotIndex && !EffectModel.NOT_SUPPORTED_ON_MUSTANG_III_V2.contains(m)) {
                validModels.add(m);
            }
        }
        modelCombo = new JComboBox<>(validModels.toArray(new EffectModel[0]));
        enabledCheck = new JCheckBox("On", true);
        fxLoopCheck = new JCheckBox("FX Loop (post-preamp)", false);
        
        JPanel headerRow = new JPanel(new BorderLayout(6, 0));
        headerRow.add(enabledCheck, BorderLayout.WEST);
        headerRow.add(modelCombo, BorderLayout.CENTER);
        headerRow.add(fxLoopCheck, BorderLayout.EAST);
        add(headerRow, BorderLayout.NORTH);
        
        // Knob row: 6 cells, each with label + slider/dropdown/toggle
        JPanel knobRow = new JPanel(new GridLayout(1, 6, 4, 2));
        for (int k = 0; k < 6; k++) {
            final int knobIndex = k;
            
            JLabel label = new JLabel(" ", SwingConstants.CENTER);
            label.setFont(label.getFont().deriveFont(10f));
            knobLabels[k] = label;
            
            JSlider slider = KnobFactory.newKnobSlider();
            knobSliders[k] = slider;
            
            JComboBox<String> dropdown = new JComboBox<>();
            knobDropdowns[k] = dropdown;
            
            JCheckBox toggle = new JCheckBox();
            knobToggles[k] = toggle;
            
            JPanel cardPanel = new JPanel(new CardLayout());
            cardPanel.add(slider, "SLIDER");
            cardPanel.add(dropdown, "DROPDOWN");
            cardPanel.add(toggle, "TOGGLE");
            knobCardPanels[k] = cardPanel;
            
            JPanel knobCell = new JPanel(new BorderLayout());
            knobCell.add(label, BorderLayout.NORTH);
            knobCell.add(cardPanel, BorderLayout.CENTER);
            knobRow.add(knobCell);
            
            // Listeners for this knob
            slider.addChangeListener(e -> {
                KnobSpec spec = activeKnobSpecs[knobIndex];
                if (spec != null && spec.type() == KnobSpec.KnobControlType.SLIDER) {
                    int rawValue = slider.getValue();
                    knobLabels[knobIndex].setText(KnobFactory.knobLabelText(spec, rawValue));
                    slider.setToolTipText("raw " + rawValue + " -> " + KnobFactory.knobDisplayValueText(spec, rawValue));
                }
                if (!applyingProgrammatically && !slider.getValueIsAdjusting() && onEffectChanged != null) {
                    onEffectChanged.accept(buildSettingsFromControls());
                }
            });
            
            dropdown.addActionListener(e -> {
                if (!applyingProgrammatically && onEffectChanged != null) {
                    onEffectChanged.accept(buildSettingsFromControls());
                }
            });
            
            toggle.addActionListener(e -> {
                if (!applyingProgrammatically && onEffectChanged != null) {
                    onEffectChanged.accept(buildSettingsFromControls());
                }
            });
        }
        add(knobRow, BorderLayout.CENTER);
        
        // Header checkbox listeners
        enabledCheck.addActionListener(e -> {
            boolean on = enabledCheck.isSelected();
            modelCombo.setEnabled(on);
            for (int k = 0; k < 6; k++) setKnobCellEnabled(k, on);
            if (!applyingProgrammatically && onEffectChanged != null) {
                onEffectChanged.accept(buildSettingsFromControls());
            }
        });
        
        fxLoopCheck.addActionListener(e -> {
            if (!applyingProgrammatically && onEffectChanged != null) {
                onEffectChanged.accept(buildSettingsFromControls());
            }
        });
        
        modelCombo.addActionListener(e -> {
            EffectModel model = (EffectModel) modelCombo.getSelectedItem();
            if (model == null) return;
            updateEmptySlotState(model);
            if (!applyingProgrammatically) {
                applyKnobSpecsForModel(model, model.defaultValues);
            }
            if (!applyingProgrammatically && onEffectChanged != null) {
                onEffectChanged.accept(buildSettingsFromControls());
            }
        });
    }
    
    public void updateFromSettings(EffectSettings fx) {
        applyingProgrammatically = true;
        
        EffectModel model = (fx.model() != null) ? fx.model() : EffectModel.EMPTY;
        modelCombo.setSelectedItem(model);
        enabledCheck.setSelected(fx.enabled());
        fxLoopCheck.setSelected(fx.slot() >= 4);
        updateEmptySlotState(model);
        applyKnobSpecsForModel(model, new int[]{fx.knob1(), fx.knob2(), fx.knob3(), fx.knob4(), fx.knob5(), fx.knob6()});
        
        applyingProgrammatically = false;
    }
    
    public EffectSettings buildSettingsFromControls() {
        EffectModel model = (EffectModel) modelCombo.getSelectedItem();
        int fxSlotId = slotIndex + (fxLoopCheck.isSelected() ? 4 : 0);
        return new EffectSettings(
                fxSlotId,
                model,
                readKnobValue(0),
                readKnobValue(1),
                readKnobValue(2),
                readKnobValue(3),
                readKnobValue(4),
                readKnobValue(5),
                enabledCheck.isSelected()
        );
    }
    
    public void setOnEffectChanged(Consumer<EffectSettings> listener) {
        this.onEffectChanged = listener;
    }
    
    public void setControlsEnabled(boolean enabled) {
        enabledCheck.setEnabled(enabled);
        modelCombo.setEnabled(enabled && enabledCheck.isSelected());
        fxLoopCheck.setEnabled(enabled && enabledCheck.isSelected());
        for (int k = 0; k < 6; k++) {
            setKnobCellEnabled(k, enabled && enabledCheck.isSelected());
        }
    }
    
    // ===== Private helpers =====
    
    private void applyKnobSpecsForModel(EffectModel model, int[] values) {
        for (int k = 0; k < 6; k++) {
            KnobSpec spec = model.knobs[k];
            activeKnobSpecs[k] = spec;
            knobCardPanels[k].setVisible(spec.isUsed());
            
            CardLayout cl = (CardLayout) knobCardPanels[k].getLayout();
            int appliedValue;
            if (spec.type() == KnobSpec.KnobControlType.DROPDOWN) {
                JComboBox<String> dd = knobDropdowns[k];
                dd.removeAllItems();
                for (String option : spec.dropdownOptions()) dd.addItem(option);
                int idx = Math.max(0, Math.min(values[k], spec.dropdownOptions().length - 1));
                dd.setSelectedIndex(idx);
                cl.show(knobCardPanels[k], "DROPDOWN");
                appliedValue = idx;
            } else if (spec.type() == KnobSpec.KnobControlType.TOGGLE) {
                boolean on = values[k] != 0;
                knobToggles[k].setSelected(on);
                cl.show(knobCardPanels[k], "TOGGLE");
                appliedValue = on ? 1 : 0;
            } else {
                JSlider slider = knobSliders[k];
                slider.setMaximum(Math.max(1, spec.max()));
                int clamped = Math.max(0, Math.min(values[k], spec.max()));
                slider.setValue(clamped);
                cl.show(knobCardPanels[k], "SLIDER");
                appliedValue = clamped;
                if (spec.isUsed()) {
                    slider.setToolTipText("raw " + clamped + " -> " + KnobFactory.knobDisplayValueText(spec, clamped));
                }
            }
            knobLabels[k].setText(KnobFactory.knobLabelText(spec, appliedValue));
        }
    }
    
    private void updateEmptySlotState(EffectModel model) {
        boolean hasRealEffect = (model != null) && (model != EffectModel.EMPTY);
        enabledCheck.setEnabled(hasRealEffect);
        fxLoopCheck.setEnabled(hasRealEffect);
        if (!hasRealEffect) {
            enabledCheck.setSelected(false);
            fxLoopCheck.setSelected(false);
        }
    }
    
    private int readKnobValue(int knobIndex) {
        KnobSpec spec = activeKnobSpecs[knobIndex];
        if (spec != null && spec.type() == KnobSpec.KnobControlType.DROPDOWN) {
            return knobDropdowns[knobIndex].getSelectedIndex();
        }
        if (spec != null && spec.type() == KnobSpec.KnobControlType.TOGGLE) {
            return knobToggles[knobIndex].isSelected() ? 1 : 0;
        }
        return knobSliders[knobIndex].getValue();
    }
    
    private void setKnobCellEnabled(int knobIndex, boolean enabled) {
        knobSliders[knobIndex].setEnabled(enabled);
        knobDropdowns[knobIndex].setEnabled(enabled);
        knobToggles[knobIndex].setEnabled(enabled);
    }
}
