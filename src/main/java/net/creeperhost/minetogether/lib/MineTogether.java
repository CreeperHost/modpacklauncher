package net.creeperhost.minetogether.lib;

import net.creeperhost.minetogether.lib.util.WebUtils;

public class MineTogether {
    public static String key;
    public static String secret;
    public static String sessionString;

    public static void init(String userAgent, String key, String secret, String sessionString) {
        WebUtils.userAgent = userAgent;
        MineTogether.key = key;
        MineTogether.secret = secret;
        MineTogether.sessionString = sessionString;
    }
}
