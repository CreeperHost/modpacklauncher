package net.creeperhost.minetogether.lib.util;

public enum BasicOS {
    WIN,
    MAC,
    LINUX,
    UNKNOWN;

    public static final BasicOS CURRENT;

    static {
        String osName = System.getProperty("os.name").toLowerCase();
        if (osName.contains("win")) {
            CURRENT = BasicOS.WIN;
        } else if (osName.contains("mac")) {
            CURRENT = BasicOS.MAC;
        } else if (osName.contains("linux")) {
            CURRENT = BasicOS.LINUX;
        } else {
            CURRENT = BasicOS.UNKNOWN;
        }
    }
}
