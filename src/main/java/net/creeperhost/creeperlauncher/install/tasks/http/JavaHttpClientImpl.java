package net.creeperhost.creeperlauncher.install.tasks.http;

import net.creeperhost.creeperlauncher.util.WebUtils;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicInteger;

public class JavaHttpClientImpl implements IHttpClient
{
    @Override
    public String makeRequest(String url)
    {
        return null;
    }

    @Override
    public DownloadedFile doDownload(String url, Path destination, IProgressUpdater progressWatcher, MessageDigest digest, long maxSpeed) throws IOException, ExecutionException, InterruptedException
    {
        final HttpClient client = HttpClient.newBuilder().executor(Runnable::run).build(); // always create
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .build();

        long fileSize = WebUtils.getFileSize(new URL(url));

        PathBodyHandlerProgress pathBodyHandlerProgress = new PathBodyHandlerProgress(destination, progressWatcher, digest, fileSize);

        Path send = client.sendAsync(request, pathBodyHandlerProgress).get().body(); // not really async - our client will run async things on same thread. bit of a hack, but async just froze.

        return new DownloadedFile(send, pathBodyHandlerProgress.wrapper.downloadedBytes.get(), "");
    }

    private void pushProgress(long totalRead, long delta, long contentLength, boolean done, IProgressUpdater progressWatcher)
    {
        if (progressWatcher != null) progressWatcher.update(totalRead, delta, contentLength, done);
    }

    class PathBodyHandlerProgress implements HttpResponse.BodyHandler<Path>
    {
        private final HttpResponse.BodyHandler<Path> pathBodyHandler;
        private BodySubscriberWrapper wrapper;
        private final MessageDigest messageDigest;
        private final IProgressUpdater progressWatcher;
        private final long fileSize;

        PathBodyHandlerProgress(Path destination, IProgressUpdater progressWatcher, MessageDigest messageDigest, long fileSize)
        {
            this.messageDigest = messageDigest;
            this.progressWatcher = progressWatcher;
            this.fileSize = fileSize;
            pathBodyHandler = HttpResponse.BodyHandlers.ofFile(destination);
        }

        @Override
        public HttpResponse.BodySubscriber<Path> apply(HttpResponse.ResponseInfo responseInfo)
        {
            return wrapper = new BodySubscriberWrapper(pathBodyHandler.apply(responseInfo), progressWatcher, messageDigest, fileSize);
        }
    }

    class BodySubscriberWrapper implements HttpResponse.BodySubscriber<Path>
    {

        private final HttpResponse.BodySubscriber<Path> delegate;
        private final IProgressUpdater progressWatcher;
        private final MessageDigest digest;
        public AtomicInteger downloadedBytes = new AtomicInteger();
        private boolean canChecksum;
        private long fileSize;

        BodySubscriberWrapper(HttpResponse.BodySubscriber<Path> delegate, IProgressUpdater progressWatcher, MessageDigest digest, long fileSize)
        {
            canChecksum = digest != null;
            this.fileSize = fileSize;
            this.digest = digest;
            this.delegate = delegate;
            this.progressWatcher = progressWatcher;
        }

        @Override
        public CompletionStage<Path> getBody()
        {
            return delegate.getBody();
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription)
        {
            delegate.onSubscribe(subscription);
            pushProgress(downloadedBytes.get(), 0, fileSize, false, progressWatcher);
        }

        @Override
        public void onNext(List<ByteBuffer> item)
        {
            if (canChecksum)
            {
                for (ByteBuffer bb : item)
                {
                    digest.update(bb);
                    bb.rewind();
                }
            }
            int sum = item.stream().mapToInt(Buffer::remaining).sum();
            pushProgress(downloadedBytes.get(), sum, fileSize, false, progressWatcher);
            delegate.onNext(item);
        }

        @Override
        public void onError(Throwable throwable)
        {
            delegate.onError(throwable);
        }

        @Override
        public void onComplete()
        {
            delegate.onComplete();
            pushProgress(downloadedBytes.get(), 0, fileSize, true, progressWatcher);
        }
    }
}
