package net.creeperhost.creeperlauncher.cloudsaves;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.Protocol;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.event.ProgressEventType;
import com.amazonaws.event.ProgressListener;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.AmazonS3Exception;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.amazonaws.services.s3.transfer.Download;
import com.amazonaws.services.s3.transfer.TransferManager;
import com.amazonaws.services.s3.transfer.TransferManagerBuilder;
import com.amazonaws.services.s3.transfer.Upload;
import net.creeperhost.creeperlauncher.Constants;
import net.creeperhost.creeperlauncher.CreeperLogger;
import net.creeperhost.creeperlauncher.Settings;
import net.creeperhost.creeperlauncher.util.FileUtils;

import java.io.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class CloudSaveManager {
    private static boolean isSetup = false;
    private static AmazonS3 s3 = null;
    private static String bucket = "";
    private static TransferManager transferManager = null;
    public static List<Upload> currentUploads = Collections.synchronizedList(new ArrayList<>());
    public static List<Download> currentDownloads = Collections.synchronizedList(new ArrayList<>());

    public static void setup(String host, int port, String accessKeyId, String secretAccessKey, String bucketName) {
        bucket = bucketName;
        ClientConfiguration config = new ClientConfiguration();
        config.setProxyHost(host);
        config.setProxyPort(port);
        config.withProtocol(Protocol.HTTP);
        BasicAWSCredentials credentials =
                new BasicAWSCredentials(accessKeyId, secretAccessKey);

        s3 = AmazonS3ClientBuilder
                .standard()
                .withClientConfiguration(config)
                .withCredentials(new AWSStaticCredentialsProvider(credentials))
                .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration("http://"+host+":"+port, "us-east-2"))
                .build();
        if (!s3.doesBucketExistV2(bucket)) {
            s3.createBucket(bucket);
        }

        s3.listBuckets();

        transferManager = TransferManagerBuilder
                .standard()
                .withS3Client(s3)
                .build();

        isSetup = true;
    }

    public static CompletableFuture<Void> upload(Path path, String location, boolean blocking) {
        RunnableThrowable func = () -> {
            if (!isSetup) {
                throw new Exception("S3 is not set up yet");
            }

            File file = path.toFile();
            if (!file.exists()) {
                throw new FileNotFoundException();
            }

            actuallyUpload(file, location, blocking);
        };

        return CompletableFuture.runAsync(func);
    }

    public static List<S3ObjectSummary> listObjects()
    {
        CloudSaveManager.setup(Constants.S3_HOST, 8080, Constants.S3_KEY, Constants.S3_SECRET, Constants.S3_BUCKET);

        return s3.listObjects(bucket).getObjectSummaries();
    }

    private static void actuallyUpload(File file, String location, boolean blocking) throws Exception {
        if (file.isDirectory())
        {
            String dirLocation = location;
            if (dirLocation.endsWith("/") || dirLocation.isEmpty()) dirLocation += file.getName();
            dirLocation += "/";
            File[] fileList = file.listFiles();
            if (fileList != null) {
                for (File innerFile : fileList) {
                    actuallyUpload(innerFile, dirLocation, blocking);
                }
            }
        } else {
            String fileLocation = location;
            if (fileLocation.endsWith("/") || fileLocation.isEmpty())
                fileLocation += file.getName();
            uploadFile(file, fileLocation, blocking);
        }
    }

    private static void uploadFile(File file, String location, boolean blocking) throws Exception {
        System.out.println("Uploading " + file.getPath());
        ObjectMetadata objectMetadata = null;
        try {
            //System.out.println("Getting metadata for " + location);
            objectMetadata = s3.getObjectMetadata(bucket, location);
        } catch (AmazonS3Exception ignored) {}

        String fileHash = FileUtils.getHash(file, "SHA-256");
        if (objectMetadata != null) {
            if (fileHash.equals(objectMetadata.getUserMetaDataOf("ourhash"))) {
                CreeperLogger.INSTANCE.info("Not uploading " + file.getPath() + " as object exists on server");
                return;
            }
        }

        BufferedInputStream bufferedStream; //= new BufferedInputStream(new FileInputStream(file));


        if(file.isDirectory() && file.listFiles().length == 0)
        {
            bufferedStream = new BufferedInputStream(new ByteArrayInputStream(new byte[0]));
        }
        else
            {
                bufferedStream = new BufferedInputStream(new FileInputStream(file));
            }

        long fileSize = file.length();
        ObjectMetadata meta = new ObjectMetadata();
        meta.addUserMetadata("ourhash", fileHash);
        meta.addUserMetadata("ourlastmodified", String.valueOf(file.lastModified()));
        meta.setContentLength(fileSize);
        Upload upload = transferManager.upload(bucket, location, bufferedStream, meta);
        if (blocking) {
            upload.waitForUploadResult();
        } else {
            currentUploads.add(upload);
            upload.addProgressListener((ProgressListener) progressEvent -> {
                ProgressEventType type = progressEvent.getEventType();
                if (type == ProgressEventType.TRANSFER_CANCELED_EVENT || type == ProgressEventType.TRANSFER_COMPLETED_EVENT || type == ProgressEventType.TRANSFER_FAILED_EVENT)
                {
                    currentUploads.remove(upload);
                    System.out.println("Removed " + upload.getDescription() + " due to " + type.toString());
                }
            });
        }
    }

    public static void syncManual(File file, String location, boolean blocking, boolean client) throws Exception
    {
        CreeperLogger.INSTANCE.error("Uploading " + file.getPath());
        ObjectMetadata objectMetadata = null;
        try {
            //System.out.println("Getting metadata for " + location);
            objectMetadata = s3.getObjectMetadata(bucket, location);
        } catch (AmazonS3Exception ignored) {}

        String fileHash = FileUtils.getHash(file, "SHA-256");
        if (objectMetadata != null) {
            System.out.println("Client " + fileHash + " Server " + objectMetadata.getUserMetaDataOf("ourhash"));

            if (fileHash.equals(objectMetadata.getUserMetaDataOf("ourhash"))) {
                CreeperLogger.INSTANCE.info("Not uploading " + file.getPath() + " as object exists on server");
                return;
            } else
            {
                if(client)
                {
                    uploadFile(file, location, blocking);
                }
                else
                {
                    downloadFile(location, file, blocking);
                }
            }
        }
        else
        {
            uploadFile(file, location, blocking);
        }
    }

    public static void syncFile(File file, String location, boolean blocking) throws Exception
    {
        CreeperLogger.INSTANCE.error("Uploading " + file.getPath());
        ObjectMetadata objectMetadata = null;
        try {
            //System.out.println("Getting metadata for " + location);
            objectMetadata = s3.getObjectMetadata(bucket, location);
        } catch (AmazonS3Exception ignored) {}

        String fileHash = FileUtils.getHash(file, "SHA-256");
        if (objectMetadata != null) {
            System.out.println("Client " + fileHash + " Server " + objectMetadata.getUserMetaDataOf("ourhash"));

            if (fileHash.equals(objectMetadata.getUserMetaDataOf("ourhash"))) {
                CreeperLogger.INSTANCE.info("Not uploading " + file.getPath() + " as object exists on server");
                return;
            } else
            {
                long ourModifiedClient = FileUtils.getLastModified(file);
                long ourModifiedServer = Long.parseLong(objectMetadata.getUserMetaDataOf("ourlastmodified"));

                if(ourModifiedClient > ourModifiedServer)
                {
                    System.out.println("Client " + ourModifiedClient + " Server " + ourModifiedServer);
                    uploadFile(file, location, blocking);
                }
                else if(ourModifiedClient < ourModifiedServer)
                {
                    System.out.println("Client " + ourModifiedClient + " Server " + ourModifiedServer);
                    downloadFile(location, file, blocking);
                }
            }
        }
        else
        {
            uploadFile(file, location, blocking);
        }
    }

    public static CompletableFuture<Void> download(String location, Path path, boolean blocking) {
        RunnableThrowable func = () -> {
            if (!isSetup) {
                throw new Exception("S3 is not set up yet");
            }

            actuallyDownload(location, path.toFile(), blocking);
        };

        return CompletableFuture.runAsync(func);
    }

    private static void actuallyDownload(String location, File file, boolean blocking) throws Exception {
        if (!fileExists(location)) {
            ObjectListing objectListing = s3.listObjects(bucket, location);
            List<S3ObjectSummary> objectSummaries = objectListing.getObjectSummaries();
            if (objectSummaries.size() > 0)
            {
                // you're a directory Harry
                if (file.exists() && !file.isDirectory())
                {
                    throw new Exception("Cannot download directory " + location + " to file");
                }
                boolean doStuff = true;
                while (doStuff) {
                    objectSummaries = objectListing.getObjectSummaries();
                    for(S3ObjectSummary object: objectSummaries)
                    {
                        File downloadLocation = new File(file, object.getKey());
                        downloadFile(object.getKey(), downloadLocation, blocking);
                    }
                    doStuff = objectListing.isTruncated();
                    if (doStuff) {
                        objectListing = s3.listNextBatchOfObjects(objectListing);
                    }
                }
                return;
            }
            throw new Exception("Cannot download " + location + " as does not exist");
        }

        if (file.exists() && file.isDirectory())
        {
            file = new File(file, location.substring(location.lastIndexOf("/")));
        }
        downloadFile(location, file, blocking);
    }

    public static void deleteFile(String location)
    {
        try {
            System.out.println("deleting file " + location);
            s3.deleteObject(bucket, location);
        } catch (Throwable e)
        {
            e.printStackTrace();
        }
    }

    public static void downloadFile(String location, File file, boolean blocking) throws Exception {
        System.out.println("Downloading " + location);

        if (file.exists() && !file.isDirectory())
        {
            ObjectMetadata objectMetadata = null;
            try {
                //System.out.println("Getting metadata for " + location);
                objectMetadata = s3.getObjectMetadata(bucket, location);
            } catch (AmazonS3Exception ignored) {}

            if (objectMetadata != null) {
                if (FileUtils.getHash(file, "SHA-256").equals(objectMetadata.getUserMetaDataOf("ourhash"))) {
                    CreeperLogger.INSTANCE.info("Not downloading " + file.getPath() + " as object exists on client");
                    return;
                }
            }
        }

        Download download = transferManager.download(bucket, location, file);

        if (blocking)
        {
            download.waitForCompletion();
        } else {
            currentDownloads.add(download);
            download.addProgressListener((ProgressListener) progressEvent -> {
                ProgressEventType type = progressEvent.getEventType();
                if (type == ProgressEventType.TRANSFER_CANCELED_EVENT || type == ProgressEventType.TRANSFER_COMPLETED_EVENT || type == ProgressEventType.TRANSFER_FAILED_EVENT)
                {
                    currentUploads.remove(download);
                    System.out.println("Removed " + download.getDescription() + " due to " + type.toString());
                }
            });
        }



        /*ObjectMetadata objectMetadata = null;
        try {
            //System.out.println("Getting metadata for " + location);
            objectMetadata = s3.getObjectMetadata(bucket, location);
        } catch (AmazonS3Exception ignored) {}

        String fileHash = FileUtils.getHash(file, "SHA-256");
        if (objectMetadata != null && fileHash.equals(objectMetadata.getUserMetaDataOf("ourHash"))) {
            CreeperLogger.INSTANCE.info("Not downloading " + file.getPath() + " as object exists on client");
            return;
        }

        BufferedInputStream bufferedStream = new BufferedInputStream(new FileInputStream(file));
        long fileSize = file.length();
        ObjectMetadata meta = new ObjectMetadata();
        meta.addUserMetadata("ourHash", fileHash);
        meta.setContentLength(fileSize);
        Upload upload = transferManager.upload(bucket, location, bufferedStream, meta);
        if (blocking) {
            upload.waitForUploadResult();
        } else {
            currentUploads.add(upload);
            upload.addProgressListener((ProgressListener) progressEvent -> {
                ProgressEventType type = progressEvent.getEventType();
                if (type == ProgressEventType.TRANSFER_CANCELED_EVENT || type == ProgressEventType.TRANSFER_COMPLETED_EVENT || type == ProgressEventType.TRANSFER_FAILED_EVENT)
                {
                    currentUploads.remove(upload);
                    System.out.println("Removed " + upload.getDescription() + " due to " + type.toString());
                }
            });
        }*/
    }

    private static boolean fileExists(String location) {
        try {
            s3.getObjectMetadata(bucket, location);
        } catch (AmazonS3Exception e) {
            if (e.getStatusCode() == 404) {
                return false;
            }
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    public static boolean uploadsInProgress() {
        return isSetup && currentUploads.size() > 0;
    }

    public static boolean downloadsInProgress() {
        return isSetup && currentUploads.size() > 0;
    }

    public static boolean transfersInProgress() {
        return downloadsInProgress() || uploadsInProgress();
    }

    @FunctionalInterface
    public interface RunnableThrowable extends Runnable {
        @Override
        default void run() {
            try {
                runThrows();
            } catch (final Exception e) {
                throw new RuntimeException(e);
            }
        }

        void runThrows() throws Exception;
    }
}