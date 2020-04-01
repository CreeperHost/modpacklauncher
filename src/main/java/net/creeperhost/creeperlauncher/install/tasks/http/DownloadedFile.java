package net.creeperhost.creeperlauncher.install.tasks.http;

import java.nio.file.Path;

public class DownloadedFile
{
    private final Path destination;
    private final long size;
    private final String checksum;

    public DownloadedFile(Path destination, long size, String checksum)
    {
        this.destination = destination;
        this.size = size;
        this.checksum = checksum;
    }

    public Path getDestination()
    {
        return destination;
    }

    public long getSize()
    {
        return size;
    }

    public String getChecksum()
    {
        return checksum;
    }
}
