package net.creeperhost.creeperlauncher.install.tasks.http;

import net.creeperhost.creeperlauncher.util.MiscUtils;
import okhttp3.*;
import okio.*;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.WeakHashMap;

public class OkHttpClientImpl implements IHttpClient
{
    private static OkHttpClient client;
    private static long maxSpeed = 0;
    private static Throttler speed = new Throttler();
    private static WeakHashMap<Request, ResponseHandlers> requestMap = new WeakHashMap<>();
    {
        OkHttpClient.Builder builder = new OkHttpClient.Builder();
        builder.addInterceptor((chain) ->
        {
            Request request = chain.request();
            Response originalResponse = chain.proceed(request);
            return originalResponse.newBuilder()
                    .body(new ProgressResponseBody(originalResponse.body(), requestMap.get(request)))
                    .build();
        });
        client = builder.build();
    }

    @Override
    public String makeRequest(String url)
    {
        return null;
    }

    byte[] tempBytes = new byte[8192];

    /* returns when done */
    @Override
    public DownloadedFile doDownload(String url, Path destination, IProgressUpdater progressWatcher, MessageDigest digest, long maxSpeed) throws IOException
    {

        Request request = new Request.Builder().url(url).build();
        this.maxSpeed = maxSpeed;
        requestMap.put(request, new ResponseHandlers(progressWatcher, (source, bytes) ->
        {
            //int read = source.read(tempBytes);
            byte[] _bytes = source.readByteArray();
            if (digest != null) digest.update(_bytes);
        }));

        Response response = client.newCall(request).execute();

        BufferedSink sink = Okio.buffer(Okio.sink(destination.toFile()));
        sink.writeAll(response.body().source());
        sink.close();

        response.close();

        return new DownloadedFile(destination, 0, digest == null ? null : MiscUtils.byteArrayToHex(digest.digest()));
    }

    private static class ProgressResponseBody extends ResponseBody
    {

        private final ResponseBody responseBody;
        private final ResponseHandlers responseHandlers;
        private BufferedSource bufferedSource;

        public ProgressResponseBody(ResponseBody responseBody, ResponseHandlers progressListener)
        {
            this.responseBody = responseBody;
            this.responseHandlers = progressListener;
        }

        @Override
        public MediaType contentType()
        {
            return responseBody.contentType();
        }

        @Override
        public long contentLength()
        {
            return responseBody.contentLength();
        }

        @Override
        public BufferedSource source()
        {
            if (bufferedSource == null)
            {
                bufferedSource = Okio.buffer(source(responseBody.source()));
            }
            return bufferedSource;
        }

        private Source source(Source source)
        {
            if (maxSpeed > 0)
            {
                speed.bytesPerSecond((maxSpeed / 8) * 2);//Some reason it always limits to 50% for me, so we multiply!
            } else
            {
                speed.bytesPerSecond(Long.MAX_VALUE);
            }
            return new ForwardingSource(speed.source(source))
            {
                long totalBytesRead = 0L;

                @Override
                public long read(@NotNull Buffer sink, long byteCount) throws IOException
                {
                    long bytesRead = super.read(sink, byteCount);
                    BufferedSource peek = sink.peek();
                    responseHandlers.handler.handleDigest(peek, byteCount);

                    totalBytesRead += bytesRead != -1 ? bytesRead : 0;

                    long totalBytes = responseBody.contentLength();

                    if (responseHandlers.updater != null)
                        responseHandlers.updater.update(totalBytesRead, bytesRead, totalBytes, totalBytes == totalBytesRead);

                    return bytesRead;
                }
            };
        }
    }

    private class ResponseHandlers
    {
        IProgressUpdater updater;
        IDigestHandler handler;

        public ResponseHandlers(IProgressUpdater updater, IDigestHandler handler)
        {
            this.updater = updater;
            this.handler = handler;
        }
    }

    private interface IDigestHandler
    {
        void handleDigest(BufferedSource source, long bytes) throws IOException;
    }
}
