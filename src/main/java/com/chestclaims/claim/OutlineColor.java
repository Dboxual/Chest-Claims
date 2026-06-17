package com.chestclaims.claim;

import org.bukkit.Material;

public enum OutlineColor {

    GREEN ("Green",  "50,205,50",   Material.LIME_WOOL),
    BLUE  ("Blue",   "30,100,255",  Material.BLUE_WOOL),
    RED   ("Red",    "255,50,50",   Material.RED_WOOL),
    YELLOW("Yellow", "255,220,0",   Material.YELLOW_WOOL),
    PURPLE("Purple", "160,0,230",   Material.PURPLE_WOOL),
    AQUA  ("Aqua",   "0,200,255",   Material.CYAN_WOOL),
    WHITE ("White",  "255,255,255", Material.WHITE_WOOL);

    public final String displayName;
    public final String rgb;
    public final Material material;

    OutlineColor(String displayName, String rgb, Material material) {
        this.displayName = displayName;
        this.rgb         = rgb;
        this.material    = material;
    }

    public static OutlineColor fromRgb(String rgb) {
        if (rgb == null) return AQUA;
        for (OutlineColor c : values()) {
            if (c.rgb.equals(rgb)) return c;
        }
        return AQUA;
    }
}
