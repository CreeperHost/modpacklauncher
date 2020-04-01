package net.creeperhost.creeperlauncher.install.tasks.http;

import java.nio.file.Path;
import java.security.MessageDigest;

public interface IHttpClient
{
    String makeRequest(String url);

    public DownloadedFile doDownload(String url, Path destination, IProgressUpdater progressWatcher, MessageDigest digest, long maxSpeed) throws Throwable;
}
