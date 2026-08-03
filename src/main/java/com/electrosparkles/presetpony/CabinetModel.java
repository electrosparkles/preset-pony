package com.electrosparkles.presetpony;

/**
 * Cabinet models. IDs confirmed from offa/plug
 * Names/descriptions from the Fender Mustang Amps and Fuse Fandom wiki
 * (https://fender-mustang-amps-and-fuse.fandom.com/wiki/Cabinets_%26_Speakers),
 */
public enum CabinetModel {
    OFF(0x00, "Off", "No cabinet simulation"),
    FENDER_57_DELUXE(0x01, "Fender '57 Deluxe", "One 12\" Jensen P12Q"),
    FENDER_59_BASSMAN(0x02, "Fender '59 Bassman", "Four 10\" Jensen P10R"),
    FENDER_65_DELUXE(0x03, "Fender '65 Deluxe", "One 12\" Jensen C12N / Oxford 12T6 / JBL D120F"),
    FENDER_65_PRINCETON(0x04, "Fender '65 Princeton", "One 10\" Jensen C10N or C10R / Oxford 10L5 or 10J4"),
    FENDER_57_CHAMPION(0x05, "Fender '57 Champion", "One 6\" Champion 600"),
    MARSHALL_4X12_MODERN(0x06, "4x12 Modern", "Marshall 1960M w/ Celestion G12T-75s - emphasizes treble & bass"),
    VOX_2X12_CELESTION(0x07, "2x12 Celestion", "VOX AC30 cabinet w/ two 12\" Celestion G12"),
    MARSHALL_4X12_GREENBACKS(0x08, "4x12 Greenbacks", "Marshall 1960AX w/ four 12\" Celestion Greenbacks"),
    FENDER_65_TWIN(0x09, "Fender '65 Twin", "Two 12\" Jensen C12N / Oxford 12T6 / JBL D120F"),
    MARSHALL_4X12_VINTAGE(0x0a, "4x12 Vintage", "Marshall 1960AV w/ four 12\" Celestion Vintage 30s - emphasizes mids"),
    SUPER_SONIC_2X12(0x0b, "Super-Sonic 2x12", "Two 12\" Celestion Vintage 30s"),
    SUPER_SONIC_1X12(0x0c, "Super-Sonic 1x12", "One 12\" Eminence Lightning Bolt");


    // The following 4 are NOT in offa/plug's but
    // sourced from a second independent reverse-engineering project
    // (jtangelder/refuse's PROTOCOL.md).
    // They are not present in the Mustang v2 III, nor in Fuse when connected to it

    /**
     * TWIN_2X12(0x0d, "2x12 '57 Twin", "Paired default for Fender '57 Twin (v2) - unconfirmed against primary source"),
     * THRIFT_2X12(0x0e, "2x12 60s Thrift", "Paired default for '60s Thrift (v2) - unconfirmed against primary source"),
     * BRITISH_WATTS_4X12(0x0f, "4x12 British Watts", "Paired default for British Watts (v2) - unconfirmed against primary source"),
     * BRITISH_COLOUR_4X12(0x10, "4x12 British Colour", "Paired default for British Colour (v2) - unconfirmed against primary source");
     **/

    public final int id;
    public final String displayName;
    public final String description;

    CabinetModel(int id, String displayName, String description) {
        this.id = id;
        this.displayName = displayName;
        this.description = description;
    }

    public static CabinetModel fromId(int id) {
        for (CabinetModel c : values()) {
            if (c.id == id) return c;
        }
        throw new IllegalArgumentException("Unknown cabinet id: 0x" + Integer.toHexString(id));
    }

    @Override
    public String toString() {
        return displayName;
    }
}
