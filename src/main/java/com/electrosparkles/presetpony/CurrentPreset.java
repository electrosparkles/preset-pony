package com.electrosparkles.presetpony;

import java.util.List;

/** Everything read back from the amp's "load everything" burst for the currently-active preset. */
public record CurrentPreset(
        int presetNumber, // slot the current preset occupies (from the name packet's header slot byte)
        String name,
        AmpSettings amp,
        EffectSettings[] effects, // always length 4, slots 0-3
        List<String> presetNames // full stored-preset name list, indexed by slot
) {
}
