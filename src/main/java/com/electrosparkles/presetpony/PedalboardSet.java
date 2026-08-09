package com.electrosparkles.presetpony;

import java.time.Instant;

/**
 * A saved, amp-independent set of the 4 effect slots (Stomp/Mod/Delay/Reverb) — see
 * docs/pedalboard-sets-plan.md. Deliberately carries no AmpSettings/CabinetModel at all:
 * applying a set only ever writes the 4 effect slots, leaving whatever amp/cab is
 * currently loaded untouched — the same "touch only some categories" principle
 * RandomiseEngine.KeepFlags already established for the Toybox tab.
 */
public record PedalboardSet(
        String name,
        EffectSettings[] effects, // always length 4, slots 0-3 (Stomp/Mod/Delay/Reverb)
        Instant created,
        Instant modified
) {
    public PedalboardSet {
        if (effects == null || effects.length != 4) {
            throw new IllegalArgumentException("effects must be length 4 (Stomp/Mod/Delay/Reverb)");
        }
    }

    /** Snapshots the given live effect slots into a brand-new named set (created == modified == now). */
    public static PedalboardSet capture(String name, EffectSettings[] currentEffects) {
        if (currentEffects == null || currentEffects.length != 4) {
            throw new IllegalArgumentException("currentEffects must be length 4 (Stomp/Mod/Delay/Reverb)");
        }
        Instant now = Instant.now();
        EffectSettings[] copy = new EffectSettings[4];
        System.arraycopy(currentEffects, 0, copy, 0, 4);
        return new PedalboardSet(name, copy, now, now);
    }

    /** Same set, new effect content, modified bumped to now (created untouched). */
    public PedalboardSet withEffects(EffectSettings[] newEffects) {
        return new PedalboardSet(name, newEffects, created, Instant.now());
    }

    /** Same set, renamed, modified bumped to now. */
    public PedalboardSet withName(String newName) {
        return new PedalboardSet(newName, effects, created, Instant.now());
    }
}
