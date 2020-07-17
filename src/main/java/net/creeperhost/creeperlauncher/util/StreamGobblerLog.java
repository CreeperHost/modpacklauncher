package net.creeperhost.creeperlauncher.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class StreamGobblerLog {
    public static CompletableFuture<Boolean> redirectToLogger(final InputStream inputStream, final Consumer<String> logLineConsumer) {
        return CompletableFuture.supplyAsync(() -> {
            try (
                    InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
                    BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
            ) {
                String line = null;
                while((line = bufferedReader.readLine()) != null) {
                    logLineConsumer.accept(line);
                }
                return true;
            } catch (IOException e) {
                return false;
            }
        });
    }
}