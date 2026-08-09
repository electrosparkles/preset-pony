package com.electrosparkles.presetpony.ui.tabs;

import com.electrosparkles.presetpony.*;
import com.electrosparkles.presetpony.AppVersion;
import com.electrosparkles.presetpony.ui.ControlStateDelegate;
import com.electrosparkles.presetpony.ui.StatusUpdater;
import com.electrosparkles.presetpony.ui.TabPanel;
import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * About tab: info display with app icon, preferences cache clear option.
 */
public class AboutTabPanel extends TabPanel {
    private final JPanel panel;

    public AboutTabPanel(StatusUpdater statusUpdater, ControlStateDelegate controlDelegate) {
        super(statusUpdater, controlDelegate);
        panel = buildPanel();
    }

    @Override
    public JPanel getPanel() {
        return panel;
    }

    @Override
    public void refresh(CurrentPreset preset) {
        // No-op
    }

    @Override
    public void setConnectionState(MustangConnection connection, boolean connected) {
        // No-op
    }

    private JPanel buildPanel() {
        JPanel panel = new JPanel(new BorderLayout(12, 12));
        panel.setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));

        JLabel titleLabel = new JLabel("Preset Pony");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 22f));

        JLabel versionLabel = new JLabel("v" + AppVersion.get() + "  —  Unofficial companion app for Fender Mustang amplifiers");

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
        textPanel.add(Box.createVerticalGlue()); // Push everything up

        List<Image> icons = loadAppIcons();
        if (!icons.isEmpty()) {
            Image best = icons.get(icons.size() - 1);
            JLabel iconLabel = new JLabel(new ImageIcon(best.getScaledInstance(96, 96, Image.SCALE_SMOOTH)));
            iconLabel.setVerticalAlignment(SwingConstants.TOP);
            iconLabel.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 16));
            panel.add(iconLabel, BorderLayout.WEST);
        }

        panel.add(textPanel, BorderLayout.CENTER);

        // Preferences panel at bottom

        JPanel prefsPanel = new JPanel(new BorderLayout(6, 0));
        prefsPanel.setBorder(BorderFactory.createTitledBorder("Preferences"));
        
        // Wrap text using HTML (Swing JLabel supports this)
        String text = "Minimal preferences saved in JDK storage: pedalboard sets folder location.";
        JLabel prefsLabel = new JLabel("<html>" + text.replace("\n", "<br>") + "</html>");
        prefsLabel.setFont(prefsLabel.getFont().deriveFont(11f));
        prefsLabel.setVerticalAlignment(SwingConstants.TOP);
        
        JButton clearCacheButton = new JButton("Clear cache");
        clearCacheButton.addActionListener(e -> clearPreferencesCache());
        
        // Put label in CENTER, button stays at EAST
        prefsPanel.add(prefsLabel, BorderLayout.CENTER);
        prefsPanel.add(clearCacheButton, BorderLayout.EAST);
        
        panel.add(prefsPanel, BorderLayout.SOUTH);

        return panel;
    }

    private void clearPreferencesCache() {
        try {
            Preferences prefs = Preferences.userNodeForPackage(AppSettings.class);
            prefs.clear();
            JOptionPane.showMessageDialog(panel,
                    "Preferences cache cleared. The pedalboard sets folder location will be reset on next startup.",
                    "Cache cleared", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(panel,
                    "Could not clear preferences: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static List<Image> loadAppIcons() {
        List<Image> icons = new ArrayList<>();
        List<Integer> missing = new ArrayList<>();
        for (int size : new int[]{16, 24, 32, 48, 64, 128, 256}) {
            String resource = "/icons/icon_" + size + ".png";
            java.net.URL url = AboutTabPanel.class.getResource(resource);
            if (url == null) {
                missing.add(size);
                continue;
            }
            try {
                BufferedImage img = ImageIO.read(url);
                if (img != null) {
                    icons.add(img);
                } else {
                    missing.add(size);
                }
            } catch (IOException e) {
                throw new RuntimeException("Failed to read " + resource, e);
            }
        }
        return icons;
    }
}
