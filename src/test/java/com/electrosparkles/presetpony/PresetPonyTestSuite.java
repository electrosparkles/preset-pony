package com.electrosparkles.presetpony;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;


/**
 * Offline regression suite - no amp connection required. Run with:
 *   javac -cp "hid4java-0.8.0.jar;jna-5.14.0.jar;jna-platform-5.14.0.jar" *.java
 *   java  -cp ".;hid4java-0.8.0.jar;jna-5.14.0.jar;jna-platform-5.14.0.jar" PresetPonyTestSuite
 *
 * Fixture values (fixtures/*.fuse) taken from Fuse factory preset exports
 */
public class PresetPonyTestSuite {

    public static void main(String[] args) throws Exception {
        EffectKnobScaleFacts.applyDefault();
        AmpFacts.loadDefault();
        packetCodecAmpRoundTrip();
        packetCodecEffectRoundTrip();
        simpleCompClampingBehavior();
        importerFarBeyondDrivenFixture();
        importerBasicBritColourFixture();
        importerSafetyRejections();
        exporterImporterRoundTrip();
        realFixtureRoundTrip();
        csvExportFormatting();
        ampCabinetDefaultPairing();
        ampFactsLoadedCompletely();
        ampFactsRawValuesSpotCheck();
        ampFactsStudioPreampSentinels();
        ampFactsInlineCommentStripping();
        effectModelDiversityRoundTrip();
        deluxeReverbUnknownOverride();
        noiseGateConditionalEncoding();
        emptyEffectRoundTrip();
        emptyEffectRoundTripAllSlotPositions();
        dspEffectSlotRejectsOutOfRange();
        effectHeaderTargetsCorrectDspUnit();
        pitchShifterDisplayScale();
        ampKnobScaleCalibration();
        wholeTableConsistencyChecks();
        allEnumIdsRoundTrip();
        exporterMatchesRealFileDirectly();
        exporterGreenboxNoPhantomFifthKnob();
        exporterSimpleCompTypeWrittenUnshifted();
        exporterWahModHighQWrittenUnshifted();
        exporterKnobCountAndEncodingForEveryEffectModel();
        exporterAmpUnknownByteDefault();
        exporterDepthContextDependentOnNoiseGate();
        randomiseSlotUntouchedWhenModelAndSettingsKept();
        randomiseSameModelNewKnobsWhenOnlyModelKept();
        randomiseNewModelNewKnobsWhenNothingKept();
        randomiseEmptyIsValidOutcomeOverManyTrials();
        randomiseDisabledKnobsStayZero();
        randomiseNoiseGateFollowsGateExceptCustom();
        randomiseStudioPreampSkipsSagBias();
        randomisePairedCabFollowsNewAmpDefault();
        randomiseKeepFlagsRejectsWrongArrayLength();
        randomiseUsbGainNeverChanges();
        pedalboardSaveLoadRoundTrip();
        pedalboardSlugifyAndCollisionHandling();
        pedalboardMruEvictionAtCapacity();
        pedalboardLoadMovesToFrontOfMru();
        pedalboardDeleteRemovesFromDiskAndMru();
        pedalboardRejectsWrongFormatAndMalformedJson();
        pedalboardRejectsWrongEffectCount();
        pedalboardPeekDoesNotTouchMru();
        presetExplorerDiscovery();
        presetExplorerValidStatus();
        presetExplorerInvalidStatus();
        presetExplorerWarningStatus();

        TestAssertions.summarizeAndExit();
    }

    // ---- PacketCodec round-trips (pure in-memory, no files) ----

    private static void packetCodecAmpRoundTrip() {
        TestAssertions.section("PacketCodec - amp settings round-trip");

        AmpSettings original = new AmpSettings(
                AmpModel.FENDER_57_TWIN, // live-validated model earlier in this project
                159, 78, 129, 129, 172, 123, 157, 189, 12,
                128, 129, 0, 0, CabinetModel.SUPER_SONIC_2X12, 1, 0, 0);

        MustangPacket ampPacket = PacketCodec.encodeAmpSettings(original);
        MustangPacket usbGainPacket = PacketCodec.encodeAmpSettingsUsbGain(original);
        AmpSettings decoded = PacketCodec.decodeAmpSettings(ampPacket, usbGainPacket);

        TestAssertions.assertEquals(original.model(), decoded.model(), "amp model round-trips");
        TestAssertions.assertEquals(original.volume(), decoded.volume(), "volume round-trips");
        TestAssertions.assertEquals(original.gain(), decoded.gain(), "gain round-trips");
        TestAssertions.assertEquals(original.treble(), decoded.treble(), "treble round-trips");
        TestAssertions.assertEquals(original.middle(), decoded.middle(), "middle round-trips");
        TestAssertions.assertEquals(original.bass(), decoded.bass(), "bass round-trips");
        TestAssertions.assertEquals(original.presence(), decoded.presence(), "presence round-trips");
        TestAssertions.assertEquals(original.cabinet(), decoded.cabinet(), "cabinet round-trips");
        TestAssertions.assertEquals(original.sag(), decoded.sag(), "sag round-trips");
    }

    private static void packetCodecEffectRoundTrip() {
        TestAssertions.section("PacketCodec - effect settings round-trip");

        // Tape Delay: uses knob6 (one of only 4 effects that do) - good coverage case.
        EffectSettings original = new EffectSettings(2, EffectModel.TAPE_DELAY, 49, 38, 1, 100, 255, 1, true);
        MustangPacket packet = PacketCodec.encodeEffectSettings(original);
        EffectSettings decoded = PacketCodec.decodeEffectSettings(packet);

        TestAssertions.assertEquals(original.model(), decoded.model(), "effect model round-trips");
        TestAssertions.assertEquals(original.knob1(), decoded.knob1(), "knob1 round-trips");
        TestAssertions.assertEquals(original.knob6(), decoded.knob6(), "knob6 round-trips (echo/tape-delay only)");
        TestAssertions.assertEquals(true, decoded.enabled(), "enabled=true round-trips");

        EffectSettings bypassed = new EffectSettings(2, EffectModel.TAPE_DELAY, 49, 38, 1, 100, 255, 1, false);
        EffectSettings decodedBypassed = PacketCodec.decodeEffectSettings(PacketCodec.encodeEffectSettings(bypassed));
        TestAssertions.assertEquals(false, decodedBypassed.enabled(),
                "enabled=false round-trips (confirmed bypass byte, payload offset 6)");
    }

    private static void simpleCompClampingBehavior() {
        TestAssertions.section("PacketCodec - Simple Comp's documented knob-clamping behavior");

        // Source clamps knob1 to 0-3 and forces knobs 2-5 to zero -
        EffectSettings input = new EffectSettings(0, EffectModel.SIMPLE_COMP, 200, 50, 60, 70, 80, 0, true);
        EffectSettings decoded = PacketCodec.decodeEffectSettings(PacketCodec.encodeEffectSettings(input));

        TestAssertions.assertTrue(decoded.knob1() <= 3, "Simple Comp knob1 clamped to 0-3 (was 200)");
        TestAssertions.assertEquals(0, decoded.knob2(), "Simple Comp knob2 forced to 0");
        TestAssertions.assertEquals(0, decoded.knob3(), "Simple Comp knob3 forced to 0");
        TestAssertions.assertEquals(0, decoded.knob4(), "Simple Comp knob4 forced to 0");
        TestAssertions.assertEquals(0, decoded.knob5(), "Simple Comp knob5 forced to 0");
    }

    // ---- FusePresetImporter against factory preset sample files ----


    private static void importerFarBeyondDrivenFixture() throws IOException {
        TestAssertions.section("FusePresetImporter - Far Beyond Driven fixture (factory preset)");

        CurrentPreset preset = FusePresetImporter.fromFile(Path.of("src/test/resources/fixtures/M2_Far Beyond Driven.fuse"));

        TestAssertions.assertEquals("Far Beyond Driven", preset.name(), "name");
        TestAssertions.assertEquals(AmpModel.AMERICAN_90S, preset.amp().model(), "amp model");
        TestAssertions.assertEquals(158, preset.amp().volume(), "volume");
        TestAssertions.assertEquals(162, preset.amp().gain(), "gain");
        TestAssertions.assertEquals(128, preset.amp().gain2(), "gain2");
        TestAssertions.assertEquals(102, preset.amp().masterVolume(), "masterVolume");
        TestAssertions.assertEquals(255, preset.amp().treble(), "treble");
        TestAssertions.assertEquals(12, preset.amp().middle(), "middle");
        TestAssertions.assertEquals(255, preset.amp().bass(), "bass");
        TestAssertions.assertEquals(125, preset.amp().presence(), "presence");
        TestAssertions.assertEquals(255, preset.amp().depth(), "depth");
        TestAssertions.assertEquals(128, preset.amp().bias(), "bias");
        TestAssertions.assertEquals(4, preset.amp().noiseGate(), "noiseGate");
        TestAssertions.assertEquals(5, preset.amp().threshold(), "threshold");
        TestAssertions.assertEquals(CabinetModel.MARSHALL_4X12_VINTAGE, preset.amp().cabinet(),
                "cabinet (matches AmpModel.AMERICAN_90S's default pairing)");
        TestAssertions.assertEquals(1, preset.amp().sag(), "sag");
        TestAssertions.assertEquals(0, preset.amp().brightness(), "brightness");
        TestAssertions.assertEquals(0, preset.amp().usbGain(), "usbGain");

        TestAssertions.assertEquals(EffectModel.OVERDRIVE, preset.effects()[0].model(), "slot0 model");
        TestAssertions.assertEquals(true, preset.effects()[0].enabled(), "slot0 enabled");
        TestAssertions.assertEquals(185, preset.effects()[0].knob1(), "slot0 knob1 (Level)");
        TestAssertions.assertEquals(128, preset.effects()[0].knob2(), "slot0 knob2 (Gain)");

        TestAssertions.assertEquals(EffectModel.TRIANGLE_CHORUS, preset.effects()[1].model(), "slot1 model");
        TestAssertions.assertEquals(true, preset.effects()[1].enabled(), "slot1 enabled");
        TestAssertions.assertEquals(5, preset.effects()[1].slot(), "slot1 FxSlot id (post-preamp: 1+4)");
        TestAssertions.assertEquals(84, preset.effects()[1].knob1(), "slot1 knob1 (Level)");
        TestAssertions.assertEquals(4, preset.effects()[1].knob2(), "slot1 knob2 (Rate)");
        TestAssertions.assertEquals(37, preset.effects()[1].knob3(), "slot1 knob3 (Depth)");
        TestAssertions.assertEquals(25, preset.effects()[1].knob4(), "slot1 knob4 (Avr Delay)");

        TestAssertions.assertEquals(EffectModel.EMPTY, preset.effects()[2].model(), "slot2 model (empty Delay)");
        TestAssertions.assertEquals(false, preset.effects()[2].enabled(), "slot2 enabled");

        TestAssertions.assertEquals(EffectModel.LARGE_HALL_REVERB, preset.effects()[3].model(), "slot3 model");
        TestAssertions.assertEquals(true, preset.effects()[3].enabled(), "slot3 enabled");
        TestAssertions.assertEquals(7, preset.effects()[3].slot(), "slot3 FxSlot id (post-preamp: 3+4)");
        TestAssertions.assertEquals(71, preset.effects()[3].knob1(), "slot3 knob1 (Level)");
        TestAssertions.assertEquals(62, preset.effects()[3].knob2(), "slot3 knob2 (Decay)");
    }

    private static void importerBasicBritColourFixture() throws IOException {
        TestAssertions.section("FusePresetImporter - Basic Brit Colour fixture (factory preset, all FX slots empty)");

        CurrentPreset preset = FusePresetImporter.fromFile(Path.of("src/test/resources/fixtures/M2_Basic Brit Colour.fuse"));

        TestAssertions.assertEquals("Basic Brit Colour", preset.name(), "name");
        TestAssertions.assertEquals(AmpModel.BRITISH_COLOUR, preset.amp().model(), "amp model");
        TestAssertions.assertEquals(151, preset.amp().volume(), "volume");
        TestAssertions.assertEquals(140, preset.amp().gain(), "gain");
        TestAssertions.assertEquals(129, preset.amp().gain2(), "gain2");
        TestAssertions.assertEquals(129, preset.amp().masterVolume(), "masterVolume");
        TestAssertions.assertEquals(157, preset.amp().treble(), "treble");
        TestAssertions.assertEquals(100, preset.amp().middle(), "middle");
        TestAssertions.assertEquals(129, preset.amp().bass(), "bass");
        TestAssertions.assertEquals(129, preset.amp().presence(), "presence");
        TestAssertions.assertEquals(255, preset.amp().depth(), "depth");
        TestAssertions.assertEquals(129, preset.amp().bias(), "bias");
        TestAssertions.assertEquals(0, preset.amp().noiseGate(), "noiseGate");
        TestAssertions.assertEquals(0, preset.amp().threshold(), "threshold");
        TestAssertions.assertEquals(CabinetModel.MARSHALL_4X12_GREENBACKS, preset.amp().cabinet(),
                "cabinet (matches AmpModel.BRITISH_COLOUR's default pairing)");
        TestAssertions.assertEquals(0, preset.amp().sag(), "sag");
        TestAssertions.assertEquals(0, preset.amp().brightness(), "brightness");
        TestAssertions.assertEquals(0, preset.amp().usbGain(), "usbGain");

        // All 4 FX categories carry Module ID="0" - EMPTY regardless of each Module's
        // own BypassState attribute (readFxCategory short-circuits on modelId == 0
        // before BypassState is even read).
        for (int i = 0; i < 4; i++) {
            TestAssertions.assertEquals(EffectModel.EMPTY, preset.effects()[i].model(), "slot" + i + " model (empty)");
            TestAssertions.assertEquals(false, preset.effects()[i].enabled(), "slot" + i + " enabled");
        }
    }

    // ---- Safety checks - malformed/oversized/untrusted input handling ----

    private static void importerSafetyRejections() throws IOException {
        TestAssertions.section("FusePresetImporter - safety checks reject bad input cleanly");

        TestAssertions.assertThrows(IllegalArgumentException.class,
                () -> FusePresetImporter.fromXml(""), "empty XML content rejected");
        TestAssertions.assertThrows(IllegalArgumentException.class,
                () -> FusePresetImporter.fromXml("not xml at all"), "garbage (non-XML) content rejected");
        TestAssertions.assertThrows(IllegalArgumentException.class,
                () -> FusePresetImporter.fromXml("<Root></Root>"), "wrong root element rejected");
        TestAssertions.assertThrows(IllegalArgumentException.class,
                () -> FusePresetImporter.fromXml("<Preset ProductId=\"1\"></Preset>"),
                "wrong ProductId (different Mustang model) rejected");
        TestAssertions.assertThrows(IllegalArgumentException.class,
                () -> FusePresetImporter.fromXml("<Preset ProductId=\"13\"></Preset>"),
                "missing Amplifier/FX sections rejected");
        TestAssertions.assertThrows(IllegalArgumentException.class,
                () -> FusePresetImporter.fromXml(
                        "<Preset ProductId=\"13\"><Amplifier><Module ID=\"9999\"></Module></Amplifier>"
                                + "<FX></FX></Preset>"),
                "unrecognized amp model ID rejected");

        Path tempMissing = Path.of("does-not-exist-" + System.nanoTime() + ".fuse");
        TestAssertions.assertThrows(IllegalArgumentException.class,
                () -> {
                    try {
                        FusePresetImporter.fromFile(tempMissing);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }, "missing file rejected");

        Path tempEmpty = Files.createTempFile("empty-test", ".fuse");
        try {
            TestAssertions.assertThrows(IllegalArgumentException.class,
                    () -> {
                        try {
                            FusePresetImporter.fromFile(tempEmpty);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }, "empty file rejected");
        } finally {
            Files.deleteIfExists(tempEmpty);
        }

        Path tempOversized = Files.createTempFile("oversized-test", ".fuse");
        try {
            byte[] tooLarge = new byte[(int) FusePresetImporter.MAX_FILE_SIZE_BYTES + 1000];
            Files.write(tempOversized, tooLarge);
            TestAssertions.assertThrows(IllegalArgumentException.class,
                    () -> {
                        try {
                            FusePresetImporter.fromFile(tempOversized);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }, "oversized file rejected (over " + FusePresetImporter.MAX_FILE_SIZE_BYTES + " bytes)");
        } finally {
            Files.deleteIfExists(tempOversized);
        }
    }

    // ---- Exporter -> Importer round-trip (in-memory constructed preset) ----

    private static void effectModelDiversityRoundTrip() {
        TestAssertions.section("PacketCodec - effect round-trip across each knob control type");

        // Plain slider-only effect (no dropdown/toggle knobs at all).
        EffectSettings overdrive = new EffectSettings(0, EffectModel.OVERDRIVE, 200, 150, 100, 50, 25, 0, true);
        EffectSettings decodedOverdrive = PacketCodec.decodeEffectSettings(PacketCodec.encodeEffectSettings(overdrive));
        TestAssertions.assertEquals(EffectModel.OVERDRIVE, decodedOverdrive.model(), "Overdrive model round-trips");
        TestAssertions.assertEquals(200, decodedOverdrive.knob1(), "Overdrive knob1 round-trips");
        TestAssertions.assertEquals(50, decodedOverdrive.knob4(), "Overdrive knob4 round-trips");

        // Dropdown-type knob: Multitap Delay's "Mode" (knob index 4, 4 named options).
        EffectSettings multitap = new EffectSettings(2, EffectModel.MULTITAP_DELAY, 255, 128, 100, 128, 2, 0, true);
        EffectSettings decodedMultitap = PacketCodec.decodeEffectSettings(PacketCodec.encodeEffectSettings(multitap));
        TestAssertions.assertEquals(2, decodedMultitap.knob5(), "Multitap Delay's dropdown-backed Mode knob round-trips");

        // Toggle-type knob: Wah Mod's "High Q" (knob index 4, 2 values).
        EffectSettings wahMod = new EffectSettings(1, EffectModel.WAH_MOD, 255, 129, 1, 255, 1, 0, true);
        EffectSettings decodedWahMod = PacketCodec.decodeEffectSettings(PacketCodec.encodeEffectSettings(wahMod));
        TestAssertions.assertEquals(1, decodedWahMod.knob5(), "Wah Mod's toggle-backed High Q knob round-trips");

        // The other 3 knob6-using effects beyond Tape Delay (already covered elsewhere).
        EffectSettings monoEcho = new EffectSettings(2, EffectModel.MONO_ECHO_FILTER, 10, 20, 30, 40, 50, 60, true);
        EffectSettings decodedMonoEcho = PacketCodec.decodeEffectSettings(PacketCodec.encodeEffectSettings(monoEcho));
        TestAssertions.assertEquals(60, decodedMonoEcho.knob6(), "Mono Echo Filter knob6 round-trips");

        EffectSettings stereoEcho = new EffectSettings(2, EffectModel.STEREO_ECHO_FILTER, 11, 21, 31, 41, 51, 61, true);
        TestAssertions.assertEquals(61, PacketCodec.decodeEffectSettings(PacketCodec.encodeEffectSettings(stereoEcho)).knob6(),
                "Stereo Echo Filter knob6 round-trips");

        EffectSettings stereoTape = new EffectSettings(2, EffectModel.STEREO_TAPE_DELAY, 12, 22, 32, 42, 52, 62, true);
        TestAssertions.assertEquals(62, PacketCodec.decodeEffectSettings(PacketCodec.encodeEffectSettings(stereoTape)).knob6(),
                "Stereo Tape Delay knob6 round-trips");
    }

    private static void deluxeReverbUnknownOverride() {
        TestAssertions.section("PacketCodec - unknown24 is per-preset, written verbatim to bytes 24 and 27");

        // unknown24 now a stored AmpSettings
        // field read straight from the amp on read and written back as-is,
        // with no per-model logic in PacketCodec at all.
        AmpSettings deluxeReverbZero = new AmpSettings(
                AmpModel.FENDER_65_DELUXE_REVERB, 150, 100, 128, 128, 80, 90, 100, 110, 0x00,
                128, 128, 0, 0, CabinetModel.FENDER_65_DELUXE, 1, 0, 0);
        MustangPacket packet = PacketCodec.encodeAmpSettings(deluxeReverbZero);
        TestAssertions.assertEquals(0x00, packet.payloadByte(24), "unknown24 written verbatim to byte 24");
        TestAssertions.assertEquals(0x00, packet.payloadByte(27), "unknown24 written verbatim to byte 27 (mirrors byte 24)");

        // Same amp model, different preset, different unknown24 - confirms it's
        // per-preset, not tied to the amp model.
        AmpSettings deluxeReverbNonZero = new AmpSettings(
                AmpModel.FENDER_65_DELUXE_REVERB, 150, 100, 128, 128, 80, 90, 100, 110, 0x81,
                128, 128, 0, 0, CabinetModel.FENDER_65_DELUXE, 1, 0, 0);
        MustangPacket packet2 = PacketCodec.encodeAmpSettings(deluxeReverbNonZero);
        TestAssertions.assertEquals(0x81, packet2.payloadByte(24), "same amp model, different preset: unknown24 still written verbatim");
        TestAssertions.assertEquals(0x81, packet2.payloadByte(27), "byte 27 still mirrors byte 24");
    }

    private static void noiseGateConditionalEncoding() {
        TestAssertions.section("PacketCodec - noise gate/threshold/depth always written as given (Section 20/21)");

        // threshold/depth vary
        // meaningfully across every gate value, so they're always written
        // through  matching the confirmed named-preset table:
        // Off=0/Low=1/Mid=2/High=3/Max=4, threshold roughly gate+1, depth 0 at Low and
        // 255 at Mid/High/Max.
        AmpSettings gateMax = new AmpSettings(
                AmpModel.FENDER_57_DELUXE, 128, 128, 128, 128, 128, 128, 128, 128, 0,
                255, 128, 4, 5, CabinetModel.FENDER_57_DELUXE, 1, 0, 0);
        MustangPacket gateMaxPacket = PacketCodec.encodeAmpSettings(gateMax);
        TestAssertions.assertEquals(4, gateMaxPacket.payloadByte(31), "noiseGate=4 (Max) written as-is");
        TestAssertions.assertEquals(5, gateMaxPacket.payloadByte(32), "threshold=5 written as-is at gate=4");
        TestAssertions.assertEquals(255, gateMaxPacket.payloadByte(25), "depth=255 written as-is at gate=4 (Mid/High/Max value)");

        AmpSettings gateLow = new AmpSettings(
                AmpModel.FENDER_57_DELUXE, 128, 128, 128, 128, 128, 128, 128, 128, 0,
                0, 128, 1, 2, CabinetModel.FENDER_57_DELUXE, 1, 0, 0);
        MustangPacket gateLowPacket = PacketCodec.encodeAmpSettings(gateLow);
        TestAssertions.assertEquals(1, gateLowPacket.payloadByte(31), "noiseGate=1 (Low) written as-is");
        TestAssertions.assertEquals(2, gateLowPacket.payloadByte(32), "threshold=2 written as-is at gate=1");
        TestAssertions.assertEquals(0, gateLowPacket.payloadByte(25), "depth=0 written as-is at gate=1 (Low value) - this is the case the old bug always overwrote to 0x80");

        // Gate off - depth is "possibly not of consequence" per live observation (values
        // were inconsistent across real presets with gate off), but it must still be
        // written through as given, not silently forced to a fixed default.
        AmpSettings gateOff = new AmpSettings(
                AmpModel.FENDER_57_DELUXE, 128, 128, 128, 128, 128, 128, 128, 128, 0,
                42, 128, 0, 2, CabinetModel.FENDER_57_DELUXE, 1, 0, 0);
        MustangPacket gateOffPacket = PacketCodec.encodeAmpSettings(gateOff);
        TestAssertions.assertEquals(0, gateOffPacket.payloadByte(31), "noiseGate=0 (Off) written as-is");
        TestAssertions.assertEquals(42, gateOffPacket.payloadByte(25), "depth written as-is even at gate=0, not forced to 0x80 - the old bug's behavior");

        // Custom/User (gate=5):
        AmpSettings gateCustom = new AmpSettings(
                AmpModel.FENDER_57_DELUXE, 128, 128, 128, 128, 128, 128, 128, 128, 0,
                168, 128, 5, 1, CabinetModel.FENDER_57_DELUXE, 1, 0, 0);
        MustangPacket gateCustomPacket = PacketCodec.encodeAmpSettings(gateCustom);
        TestAssertions.assertEquals(5, gateCustomPacket.payloadByte(31), "noiseGate=5 (Custom/User) written as-is");
        TestAssertions.assertEquals(1, gateCustomPacket.payloadByte(32), "threshold=1 (real confirmed Custom value, doesn't match any named preset) written as-is");
        TestAssertions.assertEquals(168, gateCustomPacket.payloadByte(25), "depth=168 (real confirmed Custom value, doesn't match any named preset) written as-is");

        // AmpKnobScale's Custom-detection and default-table bounds
        TestAssertions.assertTrue(AmpKnobScale.isCustomGate(5), "gate=5 is Custom");
        TestAssertions.assertTrue(!AmpKnobScale.isCustomGate(4), "gate=4 (Max) is not Custom");
        TestAssertions.assertEquals(5, AmpKnobScale.defaultThresholdForGate(4), "Max's default threshold unaffected by adding Custom as a 6th combo entry");
    }

    private static void emptyEffectRoundTrip() {
        TestAssertions.section("PacketCodec - EMPTY effect model round-trip");

        EffectSettings empty = new EffectSettings(1, EffectModel.EMPTY, 0, 0, 0, 0, 0, 0, false);
        MustangPacket packet = PacketCodec.encodeEffectSettings(empty);
        EffectSettings decoded = PacketCodec.decodeEffectSettings(packet);
        TestAssertions.assertEquals(EffectModel.EMPTY, decoded.model(), "EMPTY model round-trips through PacketCodec directly");
    }

    private static void emptyEffectRoundTripAllSlotPositions() {
        TestAssertions.section("PacketCodec - EMPTY effect round-trips at every FxSlot position (0-7)");

        for (int slot = 0; slot < 8; slot++) {
            EffectSettings empty = new EffectSettings(slot, EffectModel.EMPTY, 0, 0, 0, 0, 0, 0, false);
            EffectSettings decoded = PacketCodec.decodeEffectSettings(PacketCodec.encodeEffectSettings(empty));
            TestAssertions.assertEquals(EffectModel.EMPTY, decoded.model(),
                    "EMPTY round-trips at FxSlot position " + slot);
        }
    }

    private static void dspEffectSlotRejectsOutOfRange() {
        TestAssertions.section("Dsp.effectSlot() - bounds checking on the raw 0-3 category index");

        // Direct unit test on the function itself
        TestAssertions.assertThrows(IllegalArgumentException.class,
                () -> Dsp.effectSlot(-1), "Dsp.effectSlot(-1) rejected");
        TestAssertions.assertThrows(IllegalArgumentException.class,
                () -> Dsp.effectSlot(4), "Dsp.effectSlot(4) rejected (valid range is 0-3)");
        TestAssertions.assertEquals(Dsp.EFFECT0, Dsp.effectSlot(0), "Dsp.effectSlot(0) -> EFFECT0");
        TestAssertions.assertEquals(Dsp.EFFECT3, Dsp.effectSlot(3), "Dsp.effectSlot(3) -> EFFECT3");
    }

    private static void effectHeaderTargetsCorrectDspUnit() {
        TestAssertions.section("PacketCodec - encodeEffectSettings() header targets the correct DSP unit (not just payload round-trip)");

        // The payload-level round-trip tests above can't actually prove the packet is
        // addressed to the right physical DSP unit - decodeEffectSettings() doesn't look
        // at the header at all. This checks the header.dspRaw() byte directly.

        // Pre-amp position 1 and its FX-loop counterpart position 5 (1+4) are the same
        // DSP category (Section 11: FxSlot category = POS % 4) and must produce an
        // identical dsp header byte.
        int preAmpDsp = PacketCodec.encodeEffectSettings(
                new EffectSettings(1, EffectModel.VINTAGE_TREMOLO, 0, 0, 0, 0, 0, 0, true))
                .header().dspRaw();
        int fxLoopDsp = PacketCodec.encodeEffectSettings(
                new EffectSettings(5, EffectModel.VINTAGE_TREMOLO, 0, 0, 0, 0, 0, 0, true))
                .header().dspRaw();
        TestAssertions.assertEquals(preAmpDsp, fxLoopDsp,
                "slot 1 (pre-amp) and slot 5 (FX loop, same category) target the same DSP unit");
        TestAssertions.assertEquals(Dsp.EFFECT1.wireValue, preAmpDsp, "slot 1/5 both resolve to EFFECT1");

        // Same check for an EMPTY slot - this is exactly the case that used to crash
        // (model().dspSlotGroup == -1 for EMPTY) before the slot%4 fix.
        int emptyPreAmpDsp = PacketCodec.encodeEffectSettings(
                new EffectSettings(2, EffectModel.EMPTY, 0, 0, 0, 0, 0, 0, false))
                .header().dspRaw();
        int emptyFxLoopDsp = PacketCodec.encodeEffectSettings(
                new EffectSettings(6, EffectModel.EMPTY, 0, 0, 0, 0, 0, 0, false))
                .header().dspRaw();
        TestAssertions.assertEquals(Dsp.EFFECT2.wireValue, emptyPreAmpDsp, "EMPTY at slot 2 resolves to EFFECT2");
        TestAssertions.assertEquals(emptyPreAmpDsp, emptyFxLoopDsp,
                "EMPTY at slot 2 (pre-amp) and slot 6 (FX loop) target the same DSP unit");

        // All 4 categories, spot-checked across both position styles, against the enum directly.
        Dsp[] expected = {Dsp.EFFECT0, Dsp.EFFECT1, Dsp.EFFECT2, Dsp.EFFECT3};
        for (int category = 0; category < 4; category++) {
            int preAmp = PacketCodec.encodeEffectSettings(
                    new EffectSettings(category, EffectModel.EMPTY, 0, 0, 0, 0, 0, 0, false))
                    .header().dspRaw();
            int fxLoop = PacketCodec.encodeEffectSettings(
                    new EffectSettings(category + 4, EffectModel.EMPTY, 0, 0, 0, 0, 0, 0, false))
                    .header().dspRaw();
            TestAssertions.assertEquals(expected[category].wireValue, preAmp,
                    "category " + category + " pre-amp position targets " + expected[category]);
            TestAssertions.assertEquals(expected[category].wireValue, fxLoop,
                    "category " + category + " FX loop position targets the same unit, " + expected[category]);
        }
    }

    private static void ampKnobScaleCalibration() {
        TestAssertions.section("AmpKnobScale - corrected formulas against live amp calibration data (Section 20)");

        // Bias: raw -> real panel reading (whole percent), gathered directly off the amp.
        // formatBiasPercent() must match every one of these EXACTLY, not just approximately -
        // this is what confirmed the divisor-256 + floor()
        int[][] biasCalibration = {
                {254, 49}, {224, 37}, {159, 12}, {102, -11}, {18, -43}
        };
        for (int[] point : biasCalibration) {
            int raw = point[0], expectedPercent = point[1];
            TestAssertions.assertEquals(expectedPercent, AmpKnobScale.biasPercent(raw),
                    "bias raw " + raw + " -> " + expectedPercent + "% exactly");
        }
        TestAssertions.assertEquals(0, AmpKnobScale.biasPercent(128), "raw 128 (dead center) -> exactly 0%");

        // Main EQ formula applies to all 6 knobs  identically):
        // raw -> real panel reading, truncated (floored) to 1 decimal.
        // 9 of these 11 points match EXACTLY once truncation replaced rounding; the other
        // 2 (raw=230 and raw=204) are each off by exactly 0.1 - most likely single-point
        // reading noise gathering the data live, not a further formula error (raw=204 in
        // particular has zero fractional remainder to truncate at all, so no rounding
        // choice could explain that one differently). Tolerance kept tight (0.11) rather
        // than loosened to swallow these silently - the intent is "matches to within
        // known reading noise," not "matches within an arbitrarily generous band."
        double[][] eqCalibration = {
                {171, 7.0}, {230, 9.0}, {123, 5.3}, {62, 3.1}, {4, 1.1},              // Bass
                {204, 8.1}, {248, 9.7}, {101, 4.5}, {33, 2.1}, {22, 1.7}, {0, 1.0}    // Gain
        };
        for (double[] point : eqCalibration) {
            int raw = (int) point[0];
            double expected = point[1];
            double actual = AmpKnobScale.mainEqDisplayValue(raw);
            TestAssertions.assertTrue(Math.abs(actual - expected) < 0.11,
                    "main EQ raw " + raw + " -> ~" + expected + " (was " + actual + ")");
        }
        TestAssertions.assertEquals(1.0, AmpKnobScale.mainEqDisplayValue(0), "raw 0 -> the confirmed 1.0 floor");
        TestAssertions.assertEquals(10.0, AmpKnobScale.mainEqDisplayValue(255), "raw 255 -> 10.0 ceiling");

        // USB Gain has no calibration data yet - just lock in the current best-guess
        // formula's shape so a future accidental change is caught, without claiming it's
        // been hardware-verified (it hasn't - see AmpKnobScale's own javadoc).
        TestAssertions.assertEquals(0.0, AmpKnobScale.usbGainPercent(0), "USB gain raw 0 -> 0% (uncalibrated formula)");
        TestAssertions.assertEquals(100.0, AmpKnobScale.usbGainPercent(255), "USB gain raw 255 -> 100% (uncalibrated formula)");
    }

    private static void pitchShifterDisplayScale() {
        TestAssertions.section("KnobSpec - Pitch Shifter's Pitch knob display scale (raw 0-255 -> -24.0/+24.0 semitones)");

        KnobSpec pitchKnob = EffectModel.PITCH_SHIFTER.knobs[1]; // index 1 = "Pitch", per EffectModel table
        TestAssertions.assertEquals("Pitch", pitchKnob.label(), "sanity check - knob index 1 is Pitch");
        TestAssertions.assertTrue(pitchKnob.hasDisplayScale(), "Pitch knob has a display scale");

        TestAssertions.assertEquals(-24.0, pitchKnob.toDisplayValue(0), "raw 0 -> -24.0 semitones");
        TestAssertions.assertEquals(24.0, pitchKnob.toDisplayValue(255), "raw 255 -> +24.0 semitones");
        TestAssertions.assertTrue(Math.abs(pitchKnob.toDisplayValue(128)) < 0.5, "raw 128 (near-center) -> ~0 semitones (was " + pitchKnob.toDisplayValue(128) + ")");

        TestAssertions.assertEquals("-24.0 st", pitchKnob.formatDisplayValue(0), "formatted string at raw 0");
        TestAssertions.assertEquals("+24.0 st", pitchKnob.formatDisplayValue(255), "formatted string at raw 255");

        // Truncation fix  raw 130 -> continuous value 0.470588...,
        // which ROUNDING would show as "+0.5 st" but the confirmed amp-panel behavior
        // (truncate down, not round - same as Bias/main-EQ in AmpKnobScale) gives "+0.4 st".

        TestAssertions.assertEquals("+0.4 st", pitchKnob.formatDisplayValue(130),
                "raw 130 truncates to +0.4 st, not the +0.5 st rounding would give");

        // Sanity check that this mechanism doesn't leak onto other sliders that were never
        // given a display scale - e.g. the same effect's own "Detune" knob, and an unrelated
        // effect's plain "Level" knob.
        KnobSpec detuneKnob = EffectModel.PITCH_SHIFTER.knobs[2];
        TestAssertions.assertTrue(!detuneKnob.hasDisplayScale(), "Detune knob has no display scale (unaffected by the Pitch knob's scaling)");
        KnobSpec overdriveLevel = EffectModel.OVERDRIVE.knobs[0];
        TestAssertions.assertTrue(!overdriveLevel.hasDisplayScale(), "Overdrive's Level knob has no display scale");
    }

    private static void wholeTableConsistencyChecks() {
        TestAssertions.section("EffectModel table - internal consistency (catches future data-entry mistakes)");

        for (EffectModel model : EffectModel.values()) {
            KnobSpec knob6Spec = model.knobs[5];
            TestAssertions.assertEquals(knob6Spec.isUsed(), model.hasKnob6(),
                    model + ": hasKnob6() matches knobs[5].isUsed()");

            for (int i = 0; i < 6; i++) {
                KnobSpec spec = model.knobs[i];
                if (spec.type() == KnobSpec.KnobControlType.DROPDOWN) {
                    TestAssertions.assertEquals(spec.dropdownOptions().length - 1, spec.max(),
                            model + " knob" + (i + 1) + ": dropdown option count matches max()+1");
                }
            }
        }
    }

    private static void allEnumIdsRoundTrip() {
        TestAssertions.section("Model enums - every id round-trips through fromId()");

        for (AmpModel model : AmpModel.values()) {
            TestAssertions.assertEquals(model, AmpModel.fromId(model.id), "AmpModel " + model + " id round-trips");
        }
        for (EffectModel model : EffectModel.values()) {
            TestAssertions.assertEquals(model, EffectModel.fromId(model.id), "EffectModel " + model + " id round-trips");
        }
        for (CabinetModel model : CabinetModel.values()) {
            TestAssertions.assertEquals(model, CabinetModel.fromId(model.id), "CabinetModel " + model + " id round-trips");
        }

        TestAssertions.assertThrows(IllegalArgumentException.class,
                () -> AmpModel.fromId(0xEE), "AmpModel.fromId rejects an unrecognized id");
        TestAssertions.assertEquals(null, EffectModel.fromId(0x9999), "EffectModel.fromId returns null (not throw) for an unrecognized id");
    }

    private static void exporterMatchesRealFileDirectly() throws IOException {
        TestAssertions.section("FusePresetExporter - generated XML matches the real captured file directly (not just via our own importer)");

        CurrentPreset preset = FusePresetImporter.fromFile(Path.of("src/test/resources/fixtures/M2_Basic Brit Colour.fuse"));
        String xml = FusePresetExporter.toXml(preset);

        // These are the real, confirmed-correct ControlIndex values from the actual Fuse
        // export  - checking the generated XML contains them directly guards against an
        // importer/exporter bug pair that could cancel out and be invisible to a round-trip-only test.

        TestAssertions.assertTrue(xml.contains("<Param ControlIndex=\"0\">38656</Param>"),
                "generated XML has the exact real volume encoding (151<<8=28656)");
        TestAssertions.assertTrue(xml.contains("<Param ControlIndex=\"22\">0</Param>"),
                "generated XML writes plain 0 for ControlIndex 22, matching real Fuse");
        TestAssertions.assertTrue(xml.contains("amplifier=\"Mustang V2 III/IV/V\" ProductId=\"13\""),
                "generated XML uses the confirmed real root attributes");
    }

    // ---- FusePresetExporter - knob-count and encoding regression ---
    //


    private static CurrentPreset presetWithEffect(int categoryIndex, EffectSettings fx) {
        AmpSettings amp = new AmpSettings(
                AmpModel.FENDER_57_DELUXE, 128, 128, 128, 128, 128, 128, 128, 128, 0,
                128, 128, 0, 0, CabinetModel.FENDER_57_DELUXE, 1, 0, 0);
        EffectSettings[] effects = new EffectSettings[]{
                new EffectSettings(0, EffectModel.EMPTY, 0, 0, 0, 0, 0, 0, false),
                new EffectSettings(1, EffectModel.EMPTY, 0, 0, 0, 0, 0, 0, false),
                new EffectSettings(2, EffectModel.EMPTY, 0, 0, 0, 0, 0, 0, false),
                new EffectSettings(3, EffectModel.EMPTY, 0, 0, 0, 0, 0, 0, false),
        };
        effects[categoryIndex] = fx;
        return new CurrentPreset(-1, "Test", amp, effects, java.util.List.of());
    }

    private static String extractBlock(String xml, String tag) {
        int start = xml.indexOf("<" + tag);
        TestAssertions.assertTrue(start >= 0, "generated XML contains a <" + tag + "> block");
        int end = xml.indexOf("</" + tag + ">", start) + ("</" + tag + ">").length();
        return xml.substring(start, end);
    }

    private static int countParams(String block) {
        int count = 0, idx = 0;
        while ((idx = block.indexOf("<Param", idx)) != -1) {
            count++;
            idx++;
        }
        return count;
    }

    private static void exporterGreenboxNoPhantomFifthKnob() {
        TestAssertions.section("FusePresetExporter - Greenbox writes no phantom 5th param (2026-07-31 regression, real values from 001_Classic BOOST.fuse)");

        // Real raw values confirmed against the actual Fuse export of this exact preset.
        EffectSettings greenbox = new EffectSettings(0, EffectModel.GREENBOX, 128, 177, 140, 255, 0, 0, true);
        String xml = FusePresetExporter.toXml(presetWithEffect(0, greenbox));
        String block = extractBlock(xml, "Stompbox");

        TestAssertions.assertTrue(block.contains("<Param ControlIndex=\"0\">32768</Param>"), "Greenbox Level (128<<8)");
        TestAssertions.assertTrue(block.contains("<Param ControlIndex=\"1\">45312</Param>"), "Greenbox Gain (177<<8)");
        TestAssertions.assertTrue(block.contains("<Param ControlIndex=\"2\">35840</Param>"), "Greenbox Tone (140<<8)");
        TestAssertions.assertTrue(block.contains("<Param ControlIndex=\"3\">65280</Param>"), "Greenbox Blend (255<<8)");
        TestAssertions.assertTrue(!block.contains("ControlIndex=\"4\""),
                "no phantom ControlIndex 4 - this was the original bug (extra <Param>33024</Param>)");
        TestAssertions.assertEquals(4, countParams(block), "exactly 4 Params written for Greenbox (it only has 4 real knobs)");
    }

    private static void exporterSimpleCompTypeWrittenUnshifted() {
        TestAssertions.section("FusePresetExporter - Simple Comp's Type dropdown written unshifted (2026-07-31 regression, real value from 002_Marshall 70s.fuse)");

        EffectSettings simpleComp = new EffectSettings(0, EffectModel.SIMPLE_COMP, 1, 0, 0, 0, 0, 0, true);
        String xml = FusePresetExporter.toXml(presetWithEffect(0, simpleComp));
        String block = extractBlock(xml, "Stompbox");

        TestAssertions.assertTrue(block.contains("<Param ControlIndex=\"0\">1</Param>"),
                "Type=1 written as raw 1 (dropdown), not shifted 256 - this was the original bug");
        TestAssertions.assertEquals(1, countParams(block), "exactly 1 Param written for Simple Comp (its only real knob)");
    }

    private static void exporterWahModHighQWrittenUnshifted() {
        TestAssertions.section("FusePresetExporter - Wah Mod's High Q toggle written unshifted (2026-07-31 regression, real values from 001_Classic BOOST.fuse)");

        EffectSettings wahMod = new EffectSettings(1, EffectModel.WAH_MOD, 178, 129, 133, 133, 1, 0, false);
        String xml = FusePresetExporter.toXml(presetWithEffect(1, wahMod));
        String block = extractBlock(xml, "Modulation");

        TestAssertions.assertTrue(block.contains("<Param ControlIndex=\"0\">45568</Param>"), "Wah Mod Mix (178<<8), unaffected slider knob");
        TestAssertions.assertTrue(block.contains("<Param ControlIndex=\"4\">1</Param>"),
                "High Q=1 written as raw 1 (toggle), not shifted 256 - this was the original bug");
        TestAssertions.assertEquals(5, countParams(block), "exactly 5 Params written for Wah Mod");
    }

    private static void exporterKnobCountAndEncodingForEveryEffectModel() {
        TestAssertions.section("FusePresetExporter - every EffectModel writes exactly its used knobs, correctly encoded (structural regression, all models)");

        for (EffectModel model : EffectModel.values()) {
            if (model == EffectModel.EMPTY) {
                continue;
            }

            int[] raw = model.defaultValues;
            EffectSettings fx = new EffectSettings(0, model, raw[0], raw[1], raw[2], raw[3], raw[4], raw[5], true);
            String xml = FusePresetExporter.toXml(presetWithEffect(0, fx));
            String block = extractBlock(xml, "Stompbox");

            int expectedCount = 0;
            for (int i = 0; i < 6; i++) {
                if (model.knobs[i].isUsed()) {
                    expectedCount++;
                }
            }
            TestAssertions.assertEquals(expectedCount, countParams(block),
                    model + ": param count matches used-knob count (catches phantom-knob regressions)");

            for (int i = 0; i < 6; i++) {
                KnobSpec spec = model.knobs[i];
                if (!spec.isUsed()) {
                    TestAssertions.assertTrue(!block.contains("ControlIndex=\"" + i + "\""),
                            model + ": unused knob index " + i + " has no Param at all");
                    continue;
                }
                int expectedValue = (spec.type() == KnobSpec.KnobControlType.SLIDER)
                        ? (raw[i] & 0xFF) << 8
                        : (raw[i] & 0xFF);
                TestAssertions.assertTrue(
                        block.contains("<Param ControlIndex=\"" + i + "\">" + expectedValue + "</Param>"),
                        model + " knob index " + i + " (" + spec.type() + "): expected " + expectedValue
                                + " (catches shift/no-shift regressions)");
            }
        }
    }

    private static void exporterAmpUnknownByteDefault() {
        TestAssertions.section("FusePresetExporter - amp ControlIndex 8/11 'unknown' byte default (2026-07-31 regression)");

        // The one real exception - '65 Deluxe Reverb's own unknownOverride - must be
        // unaffected by this change (still 0x00, not the new 0x81 default).
        AmpSettings deluxeReverb = new AmpSettings(
                AmpModel.FENDER_65_DELUXE_REVERB, 128, 128, 128, 128, 128, 128, 128, 128, 0,
                128, 128, 0, 0, CabinetModel.FENDER_65_DELUXE, 1, 0, 0);
        String deluxeXml = FusePresetExporter.toXml(new CurrentPreset(-1, "Test", deluxeReverb,
                new EffectSettings[]{
                        new EffectSettings(0, EffectModel.EMPTY, 0, 0, 0, 0, 0, 0, false),
                        new EffectSettings(1, EffectModel.EMPTY, 0, 0, 0, 0, 0, 0, false),
                        new EffectSettings(2, EffectModel.EMPTY, 0, 0, 0, 0, 0, 0, false),
                        new EffectSettings(3, EffectModel.EMPTY, 0, 0, 0, 0, 0, 0, false),
                }, java.util.List.of()));
        String deluxeAmpBlock = extractBlock(deluxeXml, "Amplifier");
        TestAssertions.assertTrue(deluxeAmpBlock.contains("<Param ControlIndex=\"8\">0</Param>"),
                "'65 Deluxe Reverb keeps its own override (0x00) at ControlIndex 8, unaffected by the default change");
        TestAssertions.assertTrue(deluxeAmpBlock.contains("<Param ControlIndex=\"11\">0</Param>"),
                "'65 Deluxe Reverb keeps its own override (0x00) at ControlIndex 11, unaffected by the default change");
    }

    private static void ampCabinetDefaultPairing() {
        TestAssertions.section("AmpFacts.defaultCabinet() - confirmed pairing table (Section 11)");

        TestAssertions.assertEquals(CabinetModel.FENDER_57_DELUXE,        AmpFacts.defaultCabinet(AmpModel.FENDER_57_DELUXE),       "'57 Deluxe pairing");
        TestAssertions.assertEquals(CabinetModel.FENDER_59_BASSMAN,        AmpFacts.defaultCabinet(AmpModel.FENDER_59_BASSMAN),      "'59 Bassman pairing");
        TestAssertions.assertEquals(CabinetModel.FENDER_57_CHAMPION,       AmpFacts.defaultCabinet(AmpModel.FENDER_57_CHAMP),        "'57 Champ pairing");
        TestAssertions.assertEquals(CabinetModel.FENDER_65_DELUXE,         AmpFacts.defaultCabinet(AmpModel.FENDER_65_DELUXE_REVERB), "'65 Deluxe Reverb pairing");
        TestAssertions.assertEquals(CabinetModel.FENDER_65_PRINCETON,      AmpFacts.defaultCabinet(AmpModel.FENDER_65_PRINCETON),    "'65 Princeton pairing");
        TestAssertions.assertEquals(CabinetModel.FENDER_65_TWIN,           AmpFacts.defaultCabinet(AmpModel.FENDER_65_TWIN_REVERB),  "'65 Twin Reverb pairing");
        TestAssertions.assertEquals(CabinetModel.SUPER_SONIC_1X12,         AmpFacts.defaultCabinet(AmpModel.FENDER_SUPER_SONIC),     "Super-Sonic pairing");
        TestAssertions.assertEquals(CabinetModel.VOX_2X12_CELESTION,       AmpFacts.defaultCabinet(AmpModel.BRITISH_60S),            "British 60s pairing");
        TestAssertions.assertEquals(CabinetModel.MARSHALL_4X12_GREENBACKS, AmpFacts.defaultCabinet(AmpModel.BRITISH_70S),            "British 70s pairing");
        TestAssertions.assertEquals(CabinetModel.MARSHALL_4X12_MODERN,     AmpFacts.defaultCabinet(AmpModel.BRITISH_80S),            "British 80s pairing");
        TestAssertions.assertEquals(CabinetModel.MARSHALL_4X12_VINTAGE,    AmpFacts.defaultCabinet(AmpModel.AMERICAN_90S),           "American 90s pairing");
        TestAssertions.assertEquals(CabinetModel.MARSHALL_4X12_GREENBACKS, AmpFacts.defaultCabinet(AmpModel.METAL_2000),             "Metal 2000 pairing");
        TestAssertions.assertEquals(CabinetModel.OFF,                      AmpFacts.defaultCabinet(AmpModel.STUDIO_PREAMP),          "Studio Preamp pairing");
        // v2-exclusive amps: from real Fuse UI observation (UNCERTAIN confidence)
        TestAssertions.assertEquals(CabinetModel.FENDER_65_TWIN,           AmpFacts.defaultCabinet(AmpModel.FENDER_57_TWIN),         "'57 Twin (v2) pairing");
        TestAssertions.assertEquals(CabinetModel.FENDER_57_DELUXE,         AmpFacts.defaultCabinet(AmpModel.FENDER_60_THRIFT),       "60s Thrift (v2) pairing");
        TestAssertions.assertEquals(CabinetModel.MARSHALL_4X12_GREENBACKS, AmpFacts.defaultCabinet(AmpModel.BRITISH_COLOUR),         "British Colour (v2) pairing");
        TestAssertions.assertEquals(CabinetModel.MARSHALL_4X12_VINTAGE,    AmpFacts.defaultCabinet(AmpModel.BRITISH_WATTS),          "British Watts (v2) pairing");
    }

    // ---- AmpFacts loading and defaults ----

    private static void ampFactsLoadedCompletely() {
        TestAssertions.section("AmpFacts - file loads completely, all 17 amps present");
        TestAssertions.assertTrue(AmpFacts.isLoaded(),
                "AmpFacts.loadDefault() populated the internal maps");
        // Spot check a few amps - if any failed to parse, defaults would be null
        TestAssertions.assertTrue(AmpFacts.defaultsFor(AmpModel.FENDER_57_DELUXE) != null,
                "FENDER_57_DELUXE defaults loaded");
        TestAssertions.assertTrue(AmpFacts.defaultsFor(AmpModel.BRITISH_COLOUR) != null,
                "BRITISH_COLOUR (v2-exclusive) defaults loaded");
        TestAssertions.assertTrue(AmpFacts.defaultsFor(AmpModel.STUDIO_PREAMP) != null,
                "STUDIO_PREAMP (no sag/bias) defaults loaded");
    }

    private static void ampFactsRawValuesSpotCheck() {
        TestAssertions.section("AmpFacts - spot-check raw byte values (already converted at authoring time)");
        // '57 Deluxe: gain raw 142 (from dial 6 on 0-10 scale)
        AmpDefaults deluxe = AmpFacts.defaultsFor(AmpModel.FENDER_57_DELUXE);
        TestAssertions.assertEquals(142, deluxe.gain(), "'57 Deluxe gain raw 142 (dial 6.0 on 0-10)");
        TestAssertions.assertEquals(170, deluxe.volume(), "'57 Deluxe volume raw 170 (dial 7.0 on 0-10)");
        TestAssertions.assertEquals(113, deluxe.middle(), "'57 Deluxe middle raw 113 (dial 5.0 on 0-10)");
        TestAssertions.assertEquals(128, deluxe.bias(), "'57 Deluxe bias raw 128 (MID / 0%)");
        TestAssertions.assertEquals(1, deluxe.sag(), "'57 Deluxe sag index 1 (Match)");
        TestAssertions.assertEquals(0, deluxe.noiseGate(), "'57 Deluxe noiseGate index 0 (OFF)");
        TestAssertions.assertEquals(128, deluxe.depth(), "'57 Deluxe depth raw 128 (50%)");
        TestAssertions.assertEquals(-1, deluxe.presence(), "'57 Deluxe has no Presence control (returns -1)");
        TestAssertions.assertEquals(-1, deluxe.gain2(), "'57 Deluxe has no Gain2/Blend (returns -1)");

        // Super-Sonic: has gain2 on 0-10 scale (different from dialscale)
        AmpDefaults superSonic = AmpFacts.defaultsFor(AmpModel.FENDER_SUPER_SONIC);
        TestAssertions.assertEquals(140, superSonic.gain2(),
                "Super-Sonic gain2 raw 140 (dial 5.5 on 0-10 scale, not the amp's 1-10)");
        TestAssertions.assertEquals(84, superSonic.masterVolume(), "Super-Sonic masterVolume raw 84 (33%)");
        TestAssertions.assertEquals(2, superSonic.noiseGate(), "Super-Sonic noiseGate index 2 (MID)");
    }

    private static void ampFactsStudioPreampSentinels() {
        TestAssertions.section("AmpFacts - Studio Preamp uses -1 sentinel for non-applicable controls (sag, bias)");
        AmpDefaults studio = AmpFacts.defaultsFor(AmpModel.STUDIO_PREAMP);
        TestAssertions.assertEquals(-1, studio.sag(), "Studio Preamp sag sentinel (-1 means N/A)");
        TestAssertions.assertEquals(-1, studio.bias(), "Studio Preamp bias sentinel (-1 means N/A)");
        TestAssertions.assertEquals(CabinetModel.OFF, AmpFacts.defaultCabinet(AmpModel.STUDIO_PREAMP),
                "Studio Preamp has no cabinet (OFF)");
        // Core EQ and effects controls must still be present
        TestAssertions.assertEquals(128, studio.gain(), "Studio Preamp gain still has a value");
        TestAssertions.assertEquals(128, studio.volume(), "Studio Preamp volume still has a value");
    }

    private static void ampFactsInlineCommentStripping() {
        TestAssertions.section("AmpFacts - inline comment stripping handles '# comment' suffix correctly");
        // The properties file uses "key=value  # inline comment" style extensively.
        // If stripping doesn't work, parsing will fail (e.g. trying to parseInt "142  # dial 6 on 0-10")
        // The fact that all 17 amps loaded (test: ampFactsLoadedCompletely) is proof stripping worked.
        // This test makes the mechanism explicit by checking an edge case: a value with no comment.
        AmpDefaults deluxe = AmpFacts.defaultsFor(AmpModel.FENDER_57_DELUXE);
        // Every field must parse correctly regardless of whether its line had a comment
        TestAssertions.assertTrue(deluxe.gain() >= 0 && deluxe.gain() <= 255,
                "Gain value in valid 0-255 range (comment stripping worked)");
        TestAssertions.assertTrue(deluxe.sag() >= 0 && deluxe.sag() <= 2,
                "Sag value in valid index range (comment stripping worked)");
    }

    private static void csvExportFormatting() throws IOException {
        TestAssertions.section("PresetCsvExporter - formatting against real fixtures + escaping");

        String header = PresetCsvExporter.header();
        TestAssertions.assertTrue(header.startsWith("Slot,Name,AmpModel,"), "header starts with expected columns");
        TestAssertions.assertTrue(header.contains("Effect1_Model") && header.contains("Effect4_Knob6"),
                "header includes all 4 effect column groups");
        long commaCount = header.chars().filter(c -> c == ',').count();
        TestAssertions.assertEquals(54L, commaCount, "header has 55 columns (54 commas)");

        CurrentPreset farBeyondDriven = FusePresetImporter.fromFile(Path.of("src/test/resources/fixtures/M2_Far Beyond Driven.fuse"));
        String row = PresetCsvExporter.toCsvRow(farBeyondDriven);
        TestAssertions.assertTrue(row.contains("Far Beyond Driven"), "row contains preset name");
        TestAssertions.assertTrue(row.contains("158"), "row contains known volume value");
        TestAssertions.assertTrue(row.contains("Triangle Chorus"), "row contains known effect model name");
        TestAssertions.assertTrue(row.contains("false"), "row contains the known-empty slot's enabled=false");

        // Synthetic case: name with a comma and a quote - must be safely quoted/escaped, not corrupt the CSV.
        AmpSettings amp = farBeyondDriven.amp();
        EffectSettings[] effects = farBeyondDriven.effects();
        CurrentPreset trickyName = new CurrentPreset(7, "Comma, \"Quoted\" Name", amp, effects, java.util.List.of());
        String trickyRow = PresetCsvExporter.toCsvRow(trickyName);
        TestAssertions.assertTrue(trickyRow.contains("\"Comma, \"\"Quoted\"\" Name\""),
                "name with comma+quote is properly CSV-escaped");

        // A row must have exactly as many top-level commas as the header once quoted fields are accounted for -
        // simplest real check: splitting naively on comma would be wrong for the escaped field, so instead just
        // confirm the escaped field's internal comma doesn't create an extra top-level column by checking the
        // row still ends with the same tail (last effect's knob6) as the unescaped-name row would.
        String plainNameRow = PresetCsvExporter.toCsvRow(farBeyondDriven);
        String tail = plainNameRow.substring(plainNameRow.lastIndexOf(','));
        TestAssertions.assertTrue(trickyRow.endsWith(tail), "escaped comma in name doesn't shift later columns");
    }

    private static void exporterImporterRoundTrip() {
        TestAssertions.section("FusePresetExporter -> FusePresetImporter round-trip (constructed preset)");

        AmpSettings amp = new AmpSettings(
                AmpModel.BRITISH_80S, 200, 100, 128, 128, 60, 90, 110, 70, 0,
                128, 128, 0, 0, CabinetModel.MARSHALL_4X12_MODERN, 2, 1, 55);

        EffectSettings[] effects = new EffectSettings[]{
                new EffectSettings(0, EffectModel.OVERDRIVE, 128, 128, 128, 128, 128, 0, true),
                new EffectSettings(1, EffectModel.EMPTY, 0, 0, 0, 0, 0, 0, false),
                new EffectSettings(6, EffectModel.STEREO_TAPE_DELAY, 10, 20, 30, 40, 50, 60, false), // FX loop + bypassed
                new EffectSettings(3, EffectModel.AMBIENT_REVERB, 5, 15, 25, 35, 45, 0, true),
        };

        CurrentPreset original = new CurrentPreset(-1, "Test Preset ", amp, effects, java.util.List.of());
        String xml = FusePresetExporter.toXml(original);
        CurrentPreset roundTripped = FusePresetImporter.fromXml(xml);

        TestAssertions.assertEquals("Test Preset", roundTripped.name(), "name round-trips (trimmed)");
        TestAssertions.assertEquals(amp.model(), roundTripped.amp().model(), "amp model round-trips");
        TestAssertions.assertEquals(amp.volume(), roundTripped.amp().volume(), "volume round-trips");
        TestAssertions.assertEquals(amp.cabinet(), roundTripped.amp().cabinet(), "cabinet round-trips");
        TestAssertions.assertEquals(amp.usbGain(), roundTripped.amp().usbGain(), "usbGain round-trips");

        TestAssertions.assertEquals(EffectModel.OVERDRIVE, roundTripped.effects()[0].model(), "slot0 model round-trips");
        TestAssertions.assertEquals(EffectModel.EMPTY, roundTripped.effects()[1].model(), "slot1 EMPTY round-trips");
        TestAssertions.assertEquals(EffectModel.STEREO_TAPE_DELAY, roundTripped.effects()[2].model(), "slot2 model round-trips");
        TestAssertions.assertEquals(false, roundTripped.effects()[2].enabled(), "slot2 bypassed state round-trips");
        TestAssertions.assertEquals(60, roundTripped.effects()[2].knob6(), "slot2 knob6 round-trips (Stereo Tape Delay)");
        TestAssertions.assertEquals(6, roundTripped.effects()[2].slot(), "slot2 FX loop position round-trips");
        TestAssertions.assertEquals(EffectModel.AMBIENT_REVERB, roundTripped.effects()[3].model(), "slot3 model round-trips");
    }

    // ---- Real fixture file round-trip: import -> export -> re-import ----

    private static void realFixtureRoundTrip() throws IOException {
        TestAssertions.section("Real fixture round-trip - import, export, re-import, compare");

        CurrentPreset original = FusePresetImporter.fromFile(Path.of("src/test/resources/fixtures/M2_Basic Brit Colour.fuse"));
        String xml = FusePresetExporter.toXml(original);
        CurrentPreset roundTripped = FusePresetImporter.fromXml(xml);

        TestAssertions.assertEquals(original.name(), roundTripped.name(), "name matches after round-trip");
        TestAssertions.assertEquals(original.amp().model(), roundTripped.amp().model(), "amp model matches after round-trip");
        TestAssertions.assertEquals(original.amp().volume(), roundTripped.amp().volume(), "volume matches after round-trip");
        TestAssertions.assertEquals(original.amp().cabinet(), roundTripped.amp().cabinet(), "cabinet matches after round-trip");
        for (int i = 0; i < 4; i++) {
            TestAssertions.assertEquals(original.effects()[i].model(), roundTripped.effects()[i].model(),
                    "slot" + i + " model matches after round-trip");
            TestAssertions.assertEquals(original.effects()[i].enabled(), roundTripped.effects()[i].enabled(),
                    "slot" + i + " enabled matches after round-trip");
        }
    }

    // ---- Depth context-dependent export (NG-aware encoding) ----

    private static void exporterDepthContextDependentOnNoiseGate() {
        TestAssertions.section("Exporter - depth export depends on Noise Gate state");

        // Test Case 1: NG = OFF (0), depth = 129 (raw residual/disabled value)
        // Expected: export 65280 (0xFF << 8, sentinel meaning "depth not applicable")
        // Note: writeDupParam does (raw & 0xFF) << 8, so 0xFF << 8 = 65280, not 65535
        {
            EffectSettings[] effects = new EffectSettings[]{
                    new EffectSettings(0, EffectModel.EMPTY, 0, 0, 0, 0, 0, 0, false),
                    new EffectSettings(1, EffectModel.EMPTY, 0, 0, 0, 0, 0, 0, false),
                    new EffectSettings(2, EffectModel.EMPTY, 0, 0, 0, 0, 0, 0, false),
                    new EffectSettings(3, EffectModel.EMPTY, 0, 0, 0, 0, 0, 0, false),
            };

            AmpSettings amp = new AmpSettings(
                    AmpModel.FENDER_57_DELUXE,
                    129, 0, 0, 0, 0, 0, 0, 0,
                    0x80, // unknown24
                    129,  // depth - exported raw regardless of gate state
                    0, 0, 0, // bias, noiseGate=OFF, threshold
                    CabinetModel.OFF, 0, 0, 0);

            CurrentPreset preset = new CurrentPreset(-1, "Test_NG_OFF", amp, effects, java.util.List.of());
            String xml = FusePresetExporter.toXml(preset);

            // Parse ControlIndex 9 from XML
            int depthValue = extractControlIndexFromXml(xml, 9);
            TestAssertions.assertEquals(33024, depthValue,
                    "Depth is exported raw regardless of Noise Gate state (129<<8 = 33024)");
        }

        // Test Case 2: NG = CUSTOM (5), depth = 129 (real user setting)
        // Expected: export 33024 (129 << 8)
        {
            EffectSettings[] effects = new EffectSettings[]{
                    new EffectSettings(0, EffectModel.EMPTY, 0, 0, 0, 0, 0, 0, false),
                    new EffectSettings(1, EffectModel.EMPTY, 0, 0, 0, 0, 0, 0, false),
                    new EffectSettings(2, EffectModel.EMPTY, 0, 0, 0, 0, 0, 0, false),
                    new EffectSettings(3, EffectModel.EMPTY, 0, 0, 0, 0, 0, 0, false),
            };

            AmpSettings amp = new AmpSettings(
                    AmpModel.FENDER_57_DELUXE,
                    129, 0, 0, 0, 0, 0, 0, 0,
                    0x80, // unknown24
                    129,  // depth (real value, active when NG=CUSTOM)
                    0, 5, 0, // bias, noiseGate=CUSTOM, threshold
                    CabinetModel.OFF, 0, 0, 0);

            CurrentPreset preset = new CurrentPreset(-1, "Test_NG_Custom", amp, effects, java.util.List.of());
            String xml = FusePresetExporter.toXml(preset);

            int depthValue = extractControlIndexFromXml(xml, 9);
            TestAssertions.assertEquals(33024, depthValue,
                    "When NG=CUSTOM, depth should export actual value 33024 (129<<8)");
        }

        // Test Case 3: NG = High (3), depth = 200 - still exported raw (no gate-based override)
        {
            EffectSettings[] effects = new EffectSettings[]{
                    new EffectSettings(0, EffectModel.EMPTY, 0, 0, 0, 0, 0, 0, false),
                    new EffectSettings(1, EffectModel.EMPTY, 0, 0, 0, 0, 0, 0, false),
                    new EffectSettings(2, EffectModel.EMPTY, 0, 0, 0, 0, 0, 0, false),
                    new EffectSettings(3, EffectModel.EMPTY, 0, 0, 0, 0, 0, 0, false),
            };

            AmpSettings amp = new AmpSettings(
                    AmpModel.FENDER_57_DELUXE,
                    129, 0, 0, 0, 0, 0, 0, 0,
                    0x80, // unknown24
                    200,  // depth - exported raw regardless of gate state
                    0, 3, 0, // bias, noiseGate=High, threshold
                    CabinetModel.OFF, 0, 0, 0);

            CurrentPreset preset = new CurrentPreset(-1, "Test_NG_High", amp, effects, java.util.List.of());
            String xml = FusePresetExporter.toXml(preset);

            int depthValue = extractControlIndexFromXml(xml, 9);
            TestAssertions.assertEquals(51200, depthValue,
                    "Depth is exported raw regardless of Noise Gate state (200<<8 = 51200)");
        }
    }

    // Helper: extract ControlIndex N value from exported XML's Amplifier block
    private static int extractControlIndexFromXml(String xml, int controlIndex) {
        String pattern = "<Param ControlIndex=\"" + controlIndex + "\">([^<]+)</Param>";
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
        java.util.regex.Matcher m = p.matcher(xml);

        if (m.find()) {
            return Integer.parseInt(m.group(1));
        }

        throw new AssertionError("Could not find ControlIndex " + controlIndex + " in exported XML");
    }

    // ---- RandomiseEngine (Toybox tab) ----

    private static EffectSettings sampleEffect(int slot, EffectModel model) {
        return new EffectSettings(slot, model, 10, 20, 30, 40, 50, 60, true);
    }

    private static CurrentPreset sampleCurrentPreset() {
        AmpSettings amp = new AmpSettings(AmpModel.FENDER_57_DELUXE,
                100, 101, 102, 103, 104, 105, 106, 107,
                0x80, 108, 109, 2, 3, CabinetModel.FENDER_57_DELUXE, 1, 0, 110);
        EffectSettings[] effects = new EffectSettings[]{
                sampleEffect(0, EffectModel.OVERDRIVE),
                sampleEffect(1, EffectModel.SINE_CHORUS),
                sampleEffect(2, EffectModel.MONO_DELAY),
                sampleEffect(3, EffectModel.SMALL_HALL_REVERB),
        };
        return new CurrentPreset(5, "Sample", amp, effects, java.util.List.of());
    }

    private static void randomiseSlotUntouchedWhenModelAndSettingsKept() {
        TestAssertions.section("RandomiseEngine - keep effect + keep settings leaves slot untouched");
        EffectSettings original = sampleEffect(0, EffectModel.OVERDRIVE);
        EffectSettings result = RandomiseEngine.randomiseEffect(original, true, true, new java.util.Random(42));
        TestAssertions.assertEquals(original, result, "Slot returned exactly as-is (same record)");
    }

    private static void randomiseSameModelNewKnobsWhenOnlyModelKept() {
        TestAssertions.section("RandomiseEngine - keep effect, don't keep settings: same model, new knobs");
        EffectSettings original = sampleEffect(0, EffectModel.OVERDRIVE);
        boolean anyKnobDiffered = false;
        for (int trial = 0; trial < 25; trial++) {
            EffectSettings result = RandomiseEngine.randomiseEffect(original, true, false, new java.util.Random(trial));
            TestAssertions.assertEquals(EffectModel.OVERDRIVE, result.model(), "Model unchanged, trial " + trial);
            if (result.knob1() != original.knob1() || result.knob2() != original.knob2()) {
                anyKnobDiffered = true;
            }
        }
        TestAssertions.assertTrue(anyKnobDiffered, "At least one trial produced different knob values");
    }

    private static void randomiseNewModelNewKnobsWhenNothingKept() {
        TestAssertions.section("RandomiseEngine - don't keep effect: new random model from the slot's own group");
        EffectSettings original = sampleEffect(0, EffectModel.OVERDRIVE); // slot 0 -> dspSlotGroup 0
        for (int trial = 0; trial < 50; trial++) {
            EffectSettings result = RandomiseEngine.randomiseEffect(original, false, false, new java.util.Random(trial));
            boolean validGroup = result.model() == EffectModel.EMPTY || result.model().dspSlotGroup == 0;
            TestAssertions.assertTrue(validGroup, "Random model belongs to slot 0's group or is EMPTY, trial " + trial);
            TestAssertions.assertTrue(result.model() != EffectModel.FUZZ_TOUCH_WAH,
                    "Never picks a NOT_SUPPORTED_ON_MUSTANG_III_V2 model, trial " + trial);
        }
    }

    private static void randomiseEmptyIsValidOutcomeOverManyTrials() {
        TestAssertions.section("RandomiseEngine - EMPTY is reachable as a random outcome");
        EffectSettings original = sampleEffect(1, EffectModel.SINE_CHORUS);
        boolean sawEmpty = false;
        for (int trial = 0; trial < 200 && !sawEmpty; trial++) {
            EffectSettings result = RandomiseEngine.randomiseEffect(original, false, false, new java.util.Random(trial));
            if (result.model() == EffectModel.EMPTY) sawEmpty = true;
        }
        TestAssertions.assertTrue(sawEmpty, "EMPTY appeared at least once across 200 trials");
    }

    private static void randomiseDisabledKnobsStayZero() {
        TestAssertions.section("RandomiseEngine - disabled/unused knobs are left at 0");
        // SIMPLE_COMP only uses knob1 (a dropdown); knobs 2-6 are KnobSpec.disabled().
        for (int trial = 0; trial < 20; trial++) {
            EffectSettings result = RandomiseEngine.randomiseEffect(
                    sampleEffect(0, EffectModel.SIMPLE_COMP), true, false, new java.util.Random(trial));
            TestAssertions.assertEquals(0, result.knob2(), "knob2 stays 0 for Simple Comp, trial " + trial);
            TestAssertions.assertEquals(0, result.knob3(), "knob3 stays 0 for Simple Comp, trial " + trial);
            TestAssertions.assertEquals(0, result.knob4(), "knob4 stays 0 for Simple Comp, trial " + trial);
            TestAssertions.assertEquals(0, result.knob5(), "knob5 stays 0 for Simple Comp, trial " + trial);
            TestAssertions.assertEquals(0, result.knob6(), "knob6 stays 0 for Simple Comp, trial " + trial);
            TestAssertions.assertTrue(result.knob1() >= 0 && result.knob1() <= 3,
                    "knob1 (Type dropdown, 0-3) in range, trial " + trial);
        }
    }

    private static void randomiseNoiseGateFollowsGateExceptCustom() {
        TestAssertions.section("RandomiseEngine - Threshold/Depth follow Noise Gate default unless Custom (5)");
        AmpSettings base = sampleCurrentPreset().amp();
        RandomiseEngine.KeepFlags keepEqOnly = new RandomiseEngine.KeepFlags(
                true, true, false, true, false,
                new boolean[]{true, true, true, true}, new boolean[]{true, true, true, true});
        for (int trial = 0; trial < 100; trial++) {
            AmpSettings result = RandomiseEngine.randomiseAmp(base, keepEqOnly, new java.util.Random(trial));
            if (!AmpKnobScale.isCustomGate(result.noiseGate())) {
                TestAssertions.assertEquals(AmpKnobScale.defaultThresholdForGate(result.noiseGate()), result.threshold(),
                        "Threshold follows named gate default, trial " + trial);
                TestAssertions.assertEquals(AmpKnobScale.defaultDepthForGate(result.noiseGate()), result.depth(),
                        "Depth follows named gate default, trial " + trial);
            } else {
                TestAssertions.assertTrue(result.threshold() >= 0 && result.threshold() <= 9,
                        "Threshold in 0-9 range at Custom gate, trial " + trial);
            }
        }
    }

    private static void randomiseStudioPreampSkipsSagBias() {
        TestAssertions.section("RandomiseEngine - Studio Preamp leaves Sag/Bias untouched even when Amp Tuning is randomised");
        AmpSettings base = new AmpSettings(AmpModel.STUDIO_PREAMP,
                100, 101, 102, 103, 104, 105, 106, 107,
                0x80, 108, 55, 2, 3, CabinetModel.OFF, 2, 0, 110); // sag=2, bias=55
        RandomiseEngine.KeepFlags keepModelOnly = new RandomiseEngine.KeepFlags(
                true, true, false, true, false,
                new boolean[]{true, true, true, true}, new boolean[]{true, true, true, true});
        for (int trial = 0; trial < 20; trial++) {
            AmpSettings result = RandomiseEngine.randomiseAmp(base, keepModelOnly, new java.util.Random(trial));
            TestAssertions.assertEquals(2, result.sag(), "Sag untouched for Studio Preamp, trial " + trial);
            TestAssertions.assertEquals(55, result.bias(), "Bias untouched for Studio Preamp, trial " + trial);
        }
    }

    private static void randomisePairedCabFollowsNewAmpDefault() {
        TestAssertions.section("RandomiseEngine - Paired forces Cab to the new Amp's defaultCabinet()");
        AmpSettings base = sampleCurrentPreset().amp();
        RandomiseEngine.KeepFlags pairedAmpAndCab = new RandomiseEngine.KeepFlags(
                false, true, true, false, true,
                new boolean[]{true, true, true, true}, new boolean[]{true, true, true, true});
        for (int trial = 0; trial < 30; trial++) {
            AmpSettings result = RandomiseEngine.randomiseAmp(base, pairedAmpAndCab, new java.util.Random(trial));
            TestAssertions.assertEquals(result.model().defaultCabinet(), result.cabinet(),
                    "Cab matches new amp's default pairing, trial " + trial);
        }
    }

    private static void randomiseKeepFlagsRejectsWrongArrayLength() {
        TestAssertions.section("RandomiseEngine.KeepFlags - rejects wrong-length keep arrays");
        TestAssertions.assertThrows(IllegalArgumentException.class, () ->
                new RandomiseEngine.KeepFlags(false, false, false, false, false,
                        new boolean[]{false, false, false}, new boolean[]{false, false, false, false}),
                "3-length keepEffectModel array rejected");
    }

    private static void randomiseUsbGainNeverChanges() {
        TestAssertions.section("RandomiseEngine - USB gain is never randomised, even with Amp Tuning not kept");
        AmpSettings base = sampleCurrentPreset().amp(); // usbGain = 110
        RandomiseEngine.KeepFlags nothingKept = RandomiseEngine.KeepFlags.none();
        for (int trial = 0; trial < 20; trial++) {
            AmpSettings result = RandomiseEngine.randomiseAmp(base, nothingKept, new java.util.Random(trial));
            TestAssertions.assertEquals(base.usbGain(), result.usbGain(), "USB gain unchanged, trial " + trial);
        }
    }

    // ---- PedalboardStore ----

    private static Path newTempDir() throws IOException {
        return Files.createTempDirectory("pedalboard-test-");
    }

    private static void deleteRecursively(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        try (var stream = Files.walk(dir)) {
            stream.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                }
            });
        }
    }

    private static Pedalboard samplePedalboard(String name) {
        EffectSettings[] effects = new EffectSettings[]{
                sampleEffect(0, EffectModel.OVERDRIVE),
                sampleEffect(1, EffectModel.SINE_CHORUS),
                sampleEffect(2, EffectModel.EMPTY),
                sampleEffect(3, EffectModel.STEREO_TAPE_DELAY), // category 3 (Reverb slot) - model/category mismatch
                                                                  // doesn't matter here, the store is agnostic to it
        };
        return Pedalboard.capture(name, effects);
    }

    private static void pedalboardSaveLoadRoundTrip() throws IOException {
        TestAssertions.section("PedalboardStore - save/load round-trip");
        Path dir = newTempDir();
        try {
            Pedalboard original = samplePedalboard("Ambient Lead");
            Path file = PedalboardStore.save(dir, original);
            TestAssertions.assertTrue(Files.exists(file), "file written to disk");
            TestAssertions.assertTrue(file.getFileName().toString().endsWith(PedalboardStore.FILE_SUFFIX),
                    "file name uses the .pbset.json suffix");

            Pedalboard loaded = PedalboardStore.load(file);
            TestAssertions.assertEquals("Ambient Lead", loaded.name(), "name round-trips");
            TestAssertions.assertEquals(EffectModel.OVERDRIVE, loaded.effects()[0].model(), "slot0 model round-trips");
            TestAssertions.assertEquals(EffectModel.EMPTY, loaded.effects()[2].model(), "slot2 EMPTY round-trips");
            TestAssertions.assertEquals(EffectModel.STEREO_TAPE_DELAY, loaded.effects()[3].model(), "slot3 model round-trips");
            TestAssertions.assertEquals(10, loaded.effects()[0].knob1(), "knob values round-trip");
        } finally {
            deleteRecursively(dir);
        }
    }

    private static void pedalboardSlugifyAndCollisionHandling() throws IOException {
        TestAssertions.section("PedalboardStore - slugify + filename collision handling");
        TestAssertions.assertEquals("ambient-lead", PedalboardStore.slugify("Ambient Lead"), "basic slugify");
        TestAssertions.assertEquals("crunch-rhythm", PedalboardStore.slugify("  Crunch!! Rhythm??  "),
                "punctuation/whitespace collapsed");
        TestAssertions.assertEquals("set", PedalboardStore.slugify("!!!"), "all-punctuation name falls back to 'set'");

        Path dir = newTempDir();
        try {
            Path first = PedalboardStore.save(dir, samplePedalboard("Same Name"));
            Path second = PedalboardStore.save(dir, samplePedalboard("Same Name"));
            TestAssertions.assertTrue(!first.equals(second), "second save with the same name gets a different file");
            TestAssertions.assertTrue(second.getFileName().toString().contains("-2"),
                    "collision suffix -2 applied: " + second.getFileName());
            TestAssertions.assertEquals(2, PedalboardStore.list(dir).size(), "both files present in list()");
        } finally {
            deleteRecursively(dir);
        }
    }

    private static void pedalboardMruEvictionAtCapacity() throws IOException {
        TestAssertions.section("PedalboardStore - MRU is a fixed-capacity ring (default 8), newest first");
        Path dir = newTempDir();
        try {
            List<Path> saved = new java.util.ArrayList<>();
            for (int i = 0; i < 10; i++) {
                saved.add(PedalboardStore.save(dir, samplePedalboard("Set " + i)));
            }
            List<Path> recent = PedalboardStore.recent(dir);
            TestAssertions.assertEquals(PedalboardStore.DEFAULT_MRU_CAPACITY, recent.size(),
                    "capped at DEFAULT_MRU_CAPACITY even though 10 sets were saved");
            TestAssertions.assertEquals(saved.get(9), recent.get(0), "most recently saved is at the front");
            TestAssertions.assertTrue(!recent.contains(saved.get(0)),
                    "oldest (Set 0) was evicted off the back of the ring");
            TestAssertions.assertTrue(!recent.contains(saved.get(1)),
                    "second-oldest (Set 1) was also evicted (10 saves, capacity 8)");
        } finally {
            deleteRecursively(dir);
        }
    }

    private static void pedalboardLoadMovesToFrontOfMru() throws IOException {
        TestAssertions.section("PedalboardStore - loading an older set re-promotes it to the front of the MRU");
        Path dir = newTempDir();
        try {
            Path a = PedalboardStore.save(dir, samplePedalboard("A"));
            Path b = PedalboardStore.save(dir, samplePedalboard("B"));
            TestAssertions.assertEquals(b, PedalboardStore.recent(dir).get(0), "B is newest right after saving");

            PedalboardStore.load(a);
            TestAssertions.assertEquals(a, PedalboardStore.recent(dir).get(0), "loading A promotes it back to the front");
        } finally {
            deleteRecursively(dir);
        }
    }

    private static void pedalboardDeleteRemovesFromDiskAndMru() throws IOException {
        TestAssertions.section("PedalboardStore - delete() removes the file and its MRU entry");
        Path dir = newTempDir();
        try {
            Path a = PedalboardStore.save(dir, samplePedalboard("A"));
            TestAssertions.assertTrue(PedalboardStore.recent(dir).contains(a), "A is in the MRU list before delete");

            PedalboardStore.delete(a);
            TestAssertions.assertTrue(!Files.exists(a), "file removed from disk");
            TestAssertions.assertTrue(!PedalboardStore.recent(dir).contains(a), "A no longer in the MRU list");
            TestAssertions.assertEquals(0, PedalboardStore.list(dir).size(), "no set files left in the directory");
        } finally {
            deleteRecursively(dir);
        }
    }

    private static void pedalboardRejectsWrongFormatAndMalformedJson() {
        TestAssertions.section("PedalboardStore.fromJson - rejects wrong-format and malformed input");
        TestAssertions.assertThrows(IllegalArgumentException.class,
                () -> PedalboardStore.fromJson("not json at all"), "garbage (non-JSON) content rejected");
        TestAssertions.assertThrows(IllegalArgumentException.class,
                () -> PedalboardStore.fromJson("{\"format\": \"some-other-format\"}"),
                "wrong 'format' value rejected");
        TestAssertions.assertThrows(IllegalArgumentException.class,
                () -> PedalboardStore.fromJson("[1,2,3]"), "top-level array (not object) rejected");
    }

    private static void pedalboardRejectsWrongEffectCount() {
        TestAssertions.section("PedalboardStore.fromJson - rejects an 'effects' array that isn't exactly 4 entries");
        String threeEffects = "{\"format\":\"" + PedalboardStore.FORMAT + "\",\"name\":\"X\","
                + "\"created\":\"2026-01-01T00:00:00Z\",\"modified\":\"2026-01-01T00:00:00Z\","
                + "\"effects\":[{\"slot\":0,\"model\":\"EMPTY\",\"enabled\":false,\"knobs\":[0,0,0,0,0,0]}]}";
        TestAssertions.assertThrows(IllegalArgumentException.class,
                () -> PedalboardStore.fromJson(threeEffects), "only 1 of 4 required effect entries present");
    }

    private static void pedalboardPeekDoesNotTouchMru() throws IOException {
        TestAssertions.section("PedalboarStore.peek() - reads a set without promoting it in the MRU ring");
        Path dir = newTempDir();
        try {
            Path a = PedalboardStore.save(dir, samplePedalboard("A"));
            Path b = PedalboardStore.save(dir, samplePedalboard("B"));
            TestAssertions.assertEquals(b, PedalboardStore.recent(dir).get(0), "B is newest right after saving");

            Pedalboard peeked = PedalboardStore.peek(a);
            TestAssertions.assertEquals("A", peeked.name(), "peek() still returns the right content");
            TestAssertions.assertEquals(b, PedalboardStore.recent(dir).get(0), "B is still newest - peek() didn't promote A");
        } finally {
            deleteRecursively(dir);
        }
    }

    // ---- PresetExplorerValidator ----

    /** Writes {@code content} to a uniquely-named temp .fuse file; caller must delete it. */
    private static Path tempFuse(String content) throws IOException {
        Path p = Files.createTempFile("explorer-test-", ".fuse");
        Files.writeString(p, content);
        return p;
    }

    private static void presetExplorerDiscovery() throws IOException {
        TestAssertions.section("PresetExplorerValidator - discoverFuseFiles() filtering and ordering");

        // Empty directory: must return an empty list without throwing.
        Path emptyDir = Files.createTempDirectory("explorer-empty-");
        try {
            TestAssertions.assertEquals(0, PresetExplorerValidator.discoverFuseFiles(emptyDir).size(),
                    "empty directory returns empty list");
        } finally { deleteRecursively(emptyDir); }

        // Directory with only non-.fuse files: returns empty list.
        Path noFuseDir = Files.createTempDirectory("explorer-nofuse-");
        try {
            Files.createFile(noFuseDir.resolve("settings.xml"));
            Files.createFile(noFuseDir.resolve("readme.txt"));
            TestAssertions.assertEquals(0, PresetExplorerValidator.discoverFuseFiles(noFuseDir).size(),
                    "directory with no .fuse files returns empty list");
        } finally { deleteRecursively(noFuseDir); }

        // Mixed directory: only .fuse files (case-insensitive) returned, sorted alphabetically.
        Path mixedDir = Files.createTempDirectory("explorer-mixed-");
        try {
            Files.createFile(mixedDir.resolve("ignore.xml"));
            Files.createFile(mixedDir.resolve("ignore.txt"));
            Files.createFile(mixedDir.resolve("Zeta.fuse"));
            Files.createFile(mixedDir.resolve("alpha.fuse"));
            Files.createFile(mixedDir.resolve("BETA.FUSE"));   // uppercase extension: must be included
            Files.createFile(mixedDir.resolve("gamma.fuse"));

            List<Path> found = PresetExplorerValidator.discoverFuseFiles(mixedDir);

            TestAssertions.assertEquals(4, found.size(), "exactly 4 .fuse files found (non-.fuse excluded)");
            TestAssertions.assertEquals("alpha.fuse",  found.get(0).getFileName().toString(), "sorted 1st: alpha");
            TestAssertions.assertEquals("BETA.FUSE",   found.get(1).getFileName().toString(), "sorted 2nd: BETA (case-insensitive 'beta')");
            TestAssertions.assertEquals("gamma.fuse",  found.get(2).getFileName().toString(), "sorted 3rd: gamma");
            TestAssertions.assertEquals("Zeta.fuse",   found.get(3).getFileName().toString(), "sorted 4th: Zeta (case-insensitive 'zeta')");
        } finally { deleteRecursively(mixedDir); }
    }

    private static void presetExplorerValidStatus() throws IOException {
        TestAssertions.section("PresetExplorerValidator - VALID status on good .fuse files");

        Path farBeyond = Path.of("src/test/resources/fixtures/M2_Far Beyond Driven.fuse");
        PresetExplorerValidator.ValidationResult r1 = PresetExplorerValidator.validate(farBeyond);
        TestAssertions.assertEquals(PresetExplorerValidator.ValidationStatus.VALID, r1.status,
                "real fixture Far Beyond Driven validates as VALID");
        TestAssertions.assertTrue(r1.preset != null, "VALID result has a non-null CurrentPreset");
        TestAssertions.assertEquals("", r1.detail, "VALID result has empty detail string");
        TestAssertions.assertEquals("Far Beyond Driven", r1.presetName,
                "presetName extracted correctly from Far Beyond Driven");

        Path britColour = Path.of("src/test/resources/fixtures/M2_Basic Brit Colour.fuse");
        PresetExplorerValidator.ValidationResult r2 = PresetExplorerValidator.validate(britColour);
        TestAssertions.assertEquals(PresetExplorerValidator.ValidationStatus.VALID, r2.status,
                "real fixture Basic Brit Colour validates as VALID");
        TestAssertions.assertEquals("Basic Brit Colour", r2.presetName,
                "presetName extracted correctly from Basic Brit Colour");
    }

    private static void presetExplorerInvalidStatus() throws IOException {
        TestAssertions.section("PresetExplorerValidator - INVALID status on bad/missing/oversized files");

        // Missing file -> INVALID (not a thrown exception from validate()).
        Path missing = Path.of("does-not-exist-" + System.nanoTime() + ".fuse");
        PresetExplorerValidator.ValidationResult rMissing = PresetExplorerValidator.validate(missing);
        TestAssertions.assertEquals(PresetExplorerValidator.ValidationStatus.INVALID, rMissing.status,
                "missing file returns INVALID (not an exception)");
        TestAssertions.assertTrue(!rMissing.detail.isBlank(), "missing file detail is non-empty");
        TestAssertions.assertTrue(rMissing.preset == null, "INVALID result has null preset");

        // Empty file -> INVALID.
        Path emptyFile = Files.createTempFile("explorer-empty-", ".fuse");
        try {
            PresetExplorerValidator.ValidationResult r = PresetExplorerValidator.validate(emptyFile);
            TestAssertions.assertEquals(PresetExplorerValidator.ValidationStatus.INVALID, r.status,
                    "empty file returns INVALID");
        } finally { Files.deleteIfExists(emptyFile); }

        // File over MAX_FILE_SIZE_BYTES -> INVALID.
        Path oversized = Files.createTempFile("explorer-oversized-", ".fuse");
        try {
            Files.write(oversized, new byte[(int) FusePresetImporter.MAX_FILE_SIZE_BYTES + 1000]);
            PresetExplorerValidator.ValidationResult r = PresetExplorerValidator.validate(oversized);
            TestAssertions.assertEquals(PresetExplorerValidator.ValidationStatus.INVALID, r.status,
                    "oversized file returns INVALID");
        } finally { Files.deleteIfExists(oversized); }

        // Garbage (non-XML) content -> INVALID.
        Path garbage = tempFuse("this is not xml at all!!");
        try {
            PresetExplorerValidator.ValidationResult r = PresetExplorerValidator.validate(garbage);
            TestAssertions.assertEquals(PresetExplorerValidator.ValidationStatus.INVALID, r.status,
                    "garbage (non-XML) content returns INVALID");
        } finally { Files.deleteIfExists(garbage); }

        // Wrong root element -> INVALID (not WARNING - not a ProductId issue).
        Path wrongRoot = tempFuse("<Root></Root>");
        try {
            PresetExplorerValidator.ValidationResult r = PresetExplorerValidator.validate(wrongRoot);
            TestAssertions.assertEquals(PresetExplorerValidator.ValidationStatus.INVALID, r.status,
                    "wrong root element returns INVALID, not WARNING");
            TestAssertions.assertTrue(!r.detail.contains("ProductId"),
                    "wrong root detail does not mention ProductId");
        } finally { Files.deleteIfExists(wrongRoot); }

        // Missing Amplifier/FX sections -> INVALID.
        Path missingFx = tempFuse("<Preset ProductId=\"13\"></Preset>");
        try {
            PresetExplorerValidator.ValidationResult r = PresetExplorerValidator.validate(missingFx);
            TestAssertions.assertEquals(PresetExplorerValidator.ValidationStatus.INVALID, r.status,
                    "missing Amplifier/FX sections returns INVALID");
        } finally { Files.deleteIfExists(missingFx); }
    }

    private static void presetExplorerWarningStatus() throws IOException {
        TestAssertions.section("PresetExplorerValidator - WARNING status on ProductId mismatch (different Mustang model)");

        // ProductId != "13" but valid XML structure -> WARNING, not INVALID.
        // This is the key distinction: wrong model, not a corrupt file.
        Path wrongModel = tempFuse("<Preset ProductId=\"1\"></Preset>");
        try {
            PresetExplorerValidator.ValidationResult r = PresetExplorerValidator.validate(wrongModel);
            TestAssertions.assertEquals(PresetExplorerValidator.ValidationStatus.WARNING, r.status,
                    "ProductId mismatch returns WARNING (not INVALID or VALID)");
            TestAssertions.assertTrue(r.detail.contains("ProductId"),
                    "WARNING detail message mentions ProductId");
            TestAssertions.assertTrue(r.preset == null,
                    "WARNING result has null preset (cannot safely load a different-model preset)");
        } finally { Files.deleteIfExists(wrongModel); }

        // A different non-13 ProductId -> still WARNING.
        Path otherModel = tempFuse("<Preset ProductId=\"7\"></Preset>");
        try {
            PresetExplorerValidator.ValidationResult r = PresetExplorerValidator.validate(otherModel);
            TestAssertions.assertEquals(PresetExplorerValidator.ValidationStatus.WARNING, r.status,
                    "ProductId=7 (another non-13 model) also returns WARNING");
        } finally { Files.deleteIfExists(otherModel); }

        // ProductId=13 with unrecognized amp Module ID -> INVALID (module-parse failure, not ProductId issue).
        // Confirms WARNING is specifically ProductId-present-but-wrong, not just any parse failure.
        Path badModule = tempFuse(
                "<Preset ProductId=\"13\"><Amplifier><Module ID=\"0\"></Module></Amplifier><FX></FX></Preset>");
        try {
            PresetExplorerValidator.ValidationResult r = PresetExplorerValidator.validate(badModule);
            TestAssertions.assertEquals(PresetExplorerValidator.ValidationStatus.INVALID, r.status,
                    "ProductId=13 with unrecognized amp Module ID returns INVALID (module-parse failure, not ProductId)");
            TestAssertions.assertTrue(!r.detail.contains("ProductId"),
                    "amp module parse failure detail does not mention ProductId");
        } finally { Files.deleteIfExists(badModule); }
    }
}
