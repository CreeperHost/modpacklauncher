package net.creeperhost.creeperlauncher.os;

public enum OS {
    WIN,
    MAC,
    LINUX,
    UNKNOWN;

    private static final OS current;

    static {
        String osName = System.getProperty("os.name").toLowerCase();
        if (osName.contains("win")) {
            current = OS.WIN;
        } else if (osName.contains("mac")) {
            current = OS.MAC;
        } else if (osName.contains("linux")) {
            current = OS.LINUX;
        } else {
            current = OS.UNKNOWN;
        }
    }

    public static OS current() {
        return current;
    }
}