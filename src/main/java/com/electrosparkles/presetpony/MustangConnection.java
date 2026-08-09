package com.electrosparkles.presetpony;

import org.hid4java.HidDevice;
import org.hid4java.HidManager;
import org.hid4java.HidServices;
import org.hid4java.HidServicesSpecification;

import java.util.ArrayList;
import java.util.List;

/** High-level connection to a Fender Mustang III v2 over HID. */
public class MustangConnection implements AutoCloseable {

    private static final int VENDOR_ID = 0x1ED8;
    private static final int PRODUCT_ID = 0x0016;
    private static final int REPORT_LENGTH = 64;

    private final HidServices hidServices;
    private final HidDevice device;

    /** Raw 48-byte amp payload from the most recent readCurrentPreset() - decodeAmpSettings()
     * only surfaces the currently-named fields, so this exists purely for byte-level diagnostic
     * diffing (British '60s Cut investigation, docs/british-60s-depth-cut-wire-test-plan.md). */
    private byte[] lastAmpPayloadRaw;

    private MustangConnection(HidServices hidServices, HidDevice device) {
        this.hidServices = hidServices;
        this.device = device;
    }

    /** Finds and opens the amp, then performs the required init handshake. */
    public static MustangConnection connect() {
        HidServicesSpecification spec = new HidServicesSpecification();
        HidServices hidServices = HidManager.getHidServices(spec);
        hidServices.start();

        HidDevice device = hidServices.getAttachedHidDevices().stream()
                .filter(d -> d.getVendorId() == VENDOR_ID && d.getProductId() == PRODUCT_ID)
                .findFirst()
                .orElse(null);

        if (device == null) {
            hidServices.stop();
            throw new IllegalStateException("Mustang not found (VID 0x1ED8 / PID 0x0016). Is it plugged in?");
        }

        try {
            device.open();
            MustangConnection conn = new MustangConnection(hidServices, device);
            conn.sendAndDiscardAck(PacketCodec.encodeInit0());
            conn.sendAndDiscardAck(PacketCodec.encodeInit1());
            return conn;
        } catch (RuntimeException | Error e) {
            device.close();
            hidServices.stop();
            throw e;
        }
    }

    /**
     * Sends the "load everything" command and decodes the current preset.
     * Re-sends the init0/init1 handshake first, even though connect() already
     * did it once - empirically, a bare "load" without a fresh handshake
     * right before it can return an incomplete/empty burst (seen after
     * changing the amp model via the panel, then pressing Refresh without
     * reconnecting). Cheap (~a few hundred ms) and makes Refresh behave
     * identically to a fresh Connect.
     */
    public CurrentPreset readCurrentPreset() {
        try {
            return readCurrentPresetOnce();
        } catch (IllegalStateException firstFailure) {
            try {
                Thread.sleep(400);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            return readCurrentPresetOnce();
        }
    }

    /**
     * Reads and discards any packets already sitting in the device's queue
     * before we start a new operation. Root-caused a bug where an earlier
     * read that ended early (see the tolerant-timeout loop below) left
     * trailing packets from the OLD burst still queued; the next
     * readCurrentPreset() would then read those stale packets first,
     * shifting every subsequent packet boundary and producing corrupted
     * data (observed live: a preset name read back as "j" instead of
     * "Classic") or, depending on where the shift landed, the
     * "couldn't locate amp-settings block" exception.
     */
    private void drainStalePackets() {
        int consecutiveEmpty = 0;
        while (consecutiveEmpty < 2) {
            byte[] buf = new byte[REPORT_LENGTH];
            int read = device.read(buf, 50);
            if (read <= 0) {
                consecutiveEmpty++;
            } else {
                consecutiveEmpty = 0;
            }
        }
    }

    private CurrentPreset readCurrentPresetOnce() {
        drainStalePackets();

        sendAndDiscardAck(PacketCodec.encodeInit0());
        sendAndDiscardAck(PacketCodec.encodeInit1());

        write(PacketCodec.encodeLoadCommand());

        List<MustangPacket> packets = new ArrayList<>();
        int consecutiveEmptyReads = 0;
        while (consecutiveEmptyReads < 3) { // tolerate brief pauses rather than stopping on the first gap
            byte[] buf = new byte[REPORT_LENGTH];
            int read = device.read(buf, 500);
            if (read <= 0) {
                consecutiveEmptyReads++;
                continue;
            }
            consecutiveEmptyReads = 0;
            packets.add(MustangPacket.fromBytes(buf));
        }

        int ampIdx = -1;
        for (int i = 0; i < packets.size(); i++) {
            if (packets.get(i).header().dspRaw() == Dsp.AMP.wireValue) {
                ampIdx = i;
                break;
            }
        }
        if (ampIdx < 1 || ampIdx + 6 >= packets.size()) {
            throw new IllegalStateException("Couldn't locate amp-settings block (received "
                    + packets.size() + " packets, amp tag found at index " + ampIdx
                    + "). Try Refresh again - this is usually a one-off USB timing issue.");
        }

        String name = PacketCodec.decodeName(packets.get(ampIdx - 1));
        int presetNumber = packets.get(ampIdx - 1).header().slot();
        MustangPacket ampPacket = packets.get(ampIdx);
        lastAmpPayloadRaw = ampPacket.payload();

        List<String> presetNames = new ArrayList<>();
        for (int i = 0; i < ampIdx - 1; i++) {
            MustangPacket p = packets.get(i);
            if (p.header().dspRaw() == Dsp.OP_SAVE_EFFECT_NAME.wireValue) { // 0x04 - reused by firmware for preset-list entries too
                int slot = p.header().slot();
                while (presetNames.size() <= slot) presetNames.add("");
                presetNames.set(slot, PacketCodec.decodeName(p));
            }
        }

        MustangPacket usbGainPacket = null;
        for (int i = ampIdx + 5; i < Math.min(ampIdx + 8, packets.size()); i++) {
            if (packets.get(i).header().dspRaw() == Dsp.USB_GAIN.wireValue) {
                usbGainPacket = packets.get(i);
                break;
            }
        }
        if (usbGainPacket == null) {
            usbGainPacket = packets.get(ampIdx + 5); // fallback - see requirements doc appendix on this quirk
        }

        AmpSettings amp = PacketCodec.decodeAmpSettings(ampPacket, usbGainPacket);

        EffectSettings[] effects = new EffectSettings[4];
        for (int slot = 0; slot < 4; slot++) {
            effects[slot] = PacketCodec.decodeEffectSettings(packets.get(ampIdx + 1 + slot));
        }

        return new CurrentPreset(presetNumber, name, amp, effects, presetNames);
    }

    /** Writes amp settings live (Mustang::set_amplifier equivalent). */
    /**
     * Switches the amp's currently-active preset to the given slot
     * (Mustang::load_memory_bank equivalent - serializeLoadSlotCommand /
     * DSP::opSelectMemBank). Re-reads the full current-preset state
     * afterward so the caller gets back the newly-active preset's data.
     */
    public CurrentPreset switchToPreset(int slot) {
        drainStalePackets();
        sendAndDiscardAck(PacketCodec.encodeLoadSlotCommand(slot));
        return readCurrentPreset();
    }

    /**
     * Reads one stored preset after selecting its slot — the fast path used by Plug's
     * {@code loadBankData()}: send load-slot, then read ~7 packets (name, amp, 4 FX,
     * USB gain). Does <strong>not</strong> repeat init handshake or the full
     * "load everything" ({@code 0xff 0xc1}) burst. Use for bulk backup; use
     * {@link #readCurrentPreset()} when you also need the 100-name list or a fresh sync.
     *
     * @param presetNames optional name list from an earlier full read; used only as fallback
     *                    if the slot's name packet is empty
     */
    public CurrentPreset readPresetAtSlot(int slot, List<String> presetNames) {
        drainStalePackets();
        write(PacketCodec.encodeLoadSlotCommand(slot));
        List<MustangPacket> packets = readUntilQuiet(250);
        if (packets.size() < 7) {
            throw new IllegalStateException("Slot " + slot + ": expected at least 7 packets after load-slot, got "
                    + packets.size());
        }
        return decodeSlotLoadPreset(packets, slot, presetNames);
    }

    private CurrentPreset decodeSlotLoadPreset(List<MustangPacket> packets, int slot, List<String> presetNames) {
        String name = PacketCodec.decodeName(packets.get(0));
        if (name.isBlank() && presetNames != null && slot < presetNames.size()) {
            name = presetNames.get(slot);
        }

        MustangPacket ampPacket = packets.get(1);
        MustangPacket usbGainPacket = packets.get(6);
        if (usbGainPacket.header().dspRaw() != Dsp.USB_GAIN.wireValue) {
            for (int i = 2; i < Math.min(packets.size(), 8); i++) {
                if (packets.get(i).header().dspRaw() == Dsp.USB_GAIN.wireValue) {
                    usbGainPacket = packets.get(i);
                    break;
                }
            }
        }

        AmpSettings amp = PacketCodec.decodeAmpSettings(ampPacket, usbGainPacket);
        EffectSettings[] effects = new EffectSettings[4];
        for (int i = 0; i < 4; i++) {
            effects[i] = PacketCodec.decodeEffectSettings(packets.get(2 + i));
        }

        List<String> names = presetNames != null ? presetNames : List.of();
        return new CurrentPreset(slot, name, amp, effects, names);
    }

    /** Collects packets until two consecutive empty reads (same end condition as a full burst). */
    private List<MustangPacket> readUntilQuiet(int timeoutMs) {
        List<MustangPacket> packets = new ArrayList<>();
        int consecutiveEmpty = 0;
        while (consecutiveEmpty < 2) {
            byte[] buf = new byte[REPORT_LENGTH];
            int read = device.read(buf, timeoutMs);
            if (read <= 0) {
                consecutiveEmpty++;
            } else {
                consecutiveEmpty = 0;
                packets.add(MustangPacket.fromBytes(buf));
            }
        }
        return packets;
    }

    /**
     * Saves the currently-active (live edit buffer) preset to a stored slot,
     * under the given name (Mustang::save_on_amp equivalent), then reads
     * the slot back to confirm the amp has committed the save.
     *
     * Matches Plug's save_on_amp exactly: send name packet, then loadBankData(slot).
     * The read-back is not just for confirmation — it forces the amp to complete
     * the save before it can respond to the load command, eliminating the race
     * condition where the amp showed * (modified, unsaved) after Pony reported
     * success. Returns the confirmed preset as read back from the amp.
     */
    public CurrentPreset saveToSlot(int slot, String name, List<String> presetNames) {
        if (slot < 0 || slot > 99) {
            throw new IllegalArgumentException("Slot must be 0-99, got " + slot);
        }
        if (name == null) {
            name = "";
        }
        drainStalePackets();
        sendAndDiscardAck(PacketCodec.encodeName(slot, name));
        return readPresetAtSlot(slot, presetNames);
    }

    public void writeAmpSettings(AmpSettings settings) {
        drainStalePackets();
        sendAndDiscardAck(PacketCodec.encodeAmpSettings(settings));
        sendAndDiscardAck(PacketCodec.encodeApplyCommand());
        sendAndDiscardAck(PacketCodec.encodeAmpSettingsUsbGain(settings));
        sendAndDiscardAck(PacketCodec.encodeApplyCommand());
    }

    /** Raw 48-byte amp payload from the most recent readCurrentPreset() call - see the
     * field javadoc above. Diagnostic use only (byte-level diffing); the main app should
     * keep using the decoded AmpSettings from CurrentPreset. */
    public byte[] lastAmpPayloadRaw() {
        return lastAmpPayloadRaw == null ? null : lastAmpPayloadRaw.clone();
    }

    /**
     * Test-only hook for the British '60s Depth/Cut byte-sweep
     * (docs/british-60s-depth-cut-wire-test-plan.md) - encodes settings normally,
     * then overwrites specific payload offsets before sending. Lets a small
     * standalone harness (SpecificByteSweepTool) probe individual bytes directly
     * without needing any USB capture tooling at all, since we're the one
     * generating the write instead of trying to observe Fuse's. Not called by
     * the main UI - single-variable diagnostic use only.
     */
    public void writeAmpSettingsWithOverride(AmpSettings settings, java.util.Map<Integer, Integer> payloadByteOverrides) {
        drainStalePackets();
        MustangPacket p = PacketCodec.encodeAmpSettings(settings);
        for (var e : payloadByteOverrides.entrySet()) {
            p.setPayloadByte(e.getKey(), e.getValue());
        }
        sendAndDiscardAck(p);
        sendAndDiscardAck(PacketCodec.encodeApplyCommand());
        sendAndDiscardAck(PacketCodec.encodeAmpSettingsUsbGain(settings));
        sendAndDiscardAck(PacketCodec.encodeApplyCommand());
    }

    /**
     * Writes effect settings live for one slot (Mustang::set_effect equivalent).
     * Slot index is passed explicitly (not derived from the model) because
     * EffectModel.EMPTY has no valid DSP group of its own - any slot can be empty.
     * Matches the source exactly: settings are only re-sent when enabled AND
     * the model isn't EMPTY; otherwise the slot is just cleared/silenced.
     */
    public void writeEffectSettings(int slotIndex, EffectSettings settings) {
        drainStalePackets();
        sendAndDiscardAck(PacketCodec.encodeClearEffectSettings(slotIndex));
        sendAndDiscardAck(PacketCodec.encodeApplyCommand());

        if (settings.enabled() && settings.model() != EffectModel.EMPTY) {
            sendAndDiscardAck(PacketCodec.encodeEffectSettings(settings));
            sendAndDiscardAck(PacketCodec.encodeApplyCommand());
        }
    }

    private void write(MustangPacket packet) {
        device.write(packet.toBytes(), MustangPacket.PACKET_SIZE, (byte) 0x00);
    }

    private void sendAndDiscardAck(MustangPacket packet) {
        write(packet);
        byte[] ack = new byte[REPORT_LENGTH];
        device.read(ack, 1000);
    }

    @Override
    public void close() {
        device.close();
        hidServices.stop();
        try {
            Thread.sleep(500); // Give scanner thread time to exit cleanly
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}
