package net.creeperhost.creeperlauncher.util;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public class DownloadUtils
{
    public static boolean downloadFile(File target, String url)
    {
        try
        {
            URLConnection connection = getConnection(url);
            if (connection != null && connection.getInputStream() != null)
            {
                Files.copy(connection.getInputStream(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
                return true;
            }
        } catch (IOException ignored)
        {
        }
        return false;
    }

    private static URLConnection getConnection(String address)
    {
        try
        {
            int MAX = 3;
            URL url = new URL(address);
            URLConnection connection = null;
            for (int x = 0; x < MAX; x++)
            {
                connection = url.openConnection();
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                if (connection instanceof HttpURLConnection)
                {
                    HttpURLConnection hcon = (HttpURLConnection) connection;
                    hcon.setInstanceFollowRedirects(false);
                    int res = hcon.getResponseCode();
                    if (res == HttpURLConnection.HTTP_MOVED_PERM || res == HttpURLConnection.HTTP_MOVED_TEMP)
                    {
                        String location = hcon.getHeaderField("Location");
                        hcon.disconnect();
                        url = new URL(url, location);
                    } else
                    {
                        break;
                    }
                } else
                {
                    break;
                }
            }
            return connection;
        } catch (IOException e)
        {
            e.printStackTrace();
            return null;
        }
    }
}
