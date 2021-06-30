package net.creeperhost.minetogether.lib.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tika.io.IOUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

public class MiscUtils
{
    private static final Logger LOGGER = LogManager.getLogger();
    public static final DateFormat ISO_8601 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");

    public static CompletableFuture<?> allFutures(ArrayList<CompletableFuture<?>> futures)
    {
        CompletableFuture<Void> combinedFuture = CompletableFuture.allOf(
                futures.toArray(new CompletableFuture[0])).exceptionally((t) ->
                {
                    LOGGER.warn("Future failed.", t);
                    return null;
                }
        );
        futures.forEach((x) ->
        {
            x.exceptionally((t) ->
            {
                combinedFuture.completeExceptionally(t);
                return null;
            });
        });
        return combinedFuture;
    }
    public static int getRandomNumber(int min, int max) {
        return (int) ((Math.random() * (max - min)) + min);
    }

    public static long unixtime()
    {
        return System.currentTimeMillis() / 1000L;
    }

    public static String getDateAndTime()
    {
        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ENGLISH);
        LocalDateTime now = LocalDateTime.now();

        return dateTimeFormatter.format(now);
    }

    private static String execAndFullOutput(String ...args) {
        ProcessBuilder builder = new ProcessBuilder();
        builder.redirectErrorStream(true);
        builder.command(args);
        try {
            Process start = builder.start();
            byte[] bytes = IOUtils.toByteArray(start.getInputStream());
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    private static final Pattern p = Pattern.compile("\\w+ version \"(.*?)\"");

    public static boolean isInt(String in){
        try {
            Integer.parseInt(in);
        }catch(Exception ignored) {
            return false;
        }
        return true;
    }
}
