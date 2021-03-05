package net.creeperhost.creeperlauncher.minetogether.cloudsaves;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.Protocol;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.event.ProgressEventType;
import com.amazonaws.event.ProgressListener;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.*;
import com.amazonaws.services.s3.transfer.*;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import net.covers1624.quack.util.HashUtils;
import net.creeperhost.creeperlauncher.Constants;
import net.creeperhost.creeperlauncher.util.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class CloudSaveManager {
    private static final Logger LOGGER = LogManager.getLogger();

    private static boolean isSetup = false;
    private static AmazonS3 s3 = null;
    private static String bucket = "";
    private static TransferManager transferManager = null;
    public static List<Upload> currentUploads = Collections.synchronizedList(new ArrayList<>());
    public static List<Download> currentDownloads = Collections.synchronizedList(new ArrayList<>());

    public static void setup(String host, int port, String accessKeyId, String secretAccessKey, String bucketName) {
        if (isSetup) return;
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

    public static void main(String args[])
    {
        CloudSaveManager.setup(Constants.S3_HOST, 8080, Constants.S3_KEY, Constants.S3_SECRET, Constants.S3_BUCKET);
        String out = s3.getObjectAsString(bucket, "e8d3ea26-2b7b-4f80-8120-7e788392618a/instance.jsonnfoidsnfios");
        System.out.println(out);
    }

    public static HashMap<String,S3ObjectSummary> listObjects()
    {
        return listObjects(null);
    }

    public static HashMap<String, S3ObjectSummary> listObjects(String prefix)
    {
        CloudSaveManager.setup(Constants.S3_HOST, 8080, Constants.S3_KEY, Constants.S3_SECRET, Constants.S3_BUCKET);

        HashMap<String, S3ObjectSummary> tempList = new HashMap<>();

        String nextMarker = null;
        boolean doStuff = true;

        while (doStuff) {
            ListObjectsRequest request = new ListObjectsRequest(bucket, prefix, nextMarker, null, 1000);
            ObjectListing objectListing = s3.listObjects(request);
            tempList.putAll(objectListing.getObjectSummaries().stream().collect(Collectors.toMap(S3ObjectSummary::getKey, object -> object)));
            nextMarker = objectListing.getNextMarker();
            doStuff = objectListing.isTruncated();
        }

        return tempList;
    }

    private static void uploadFile(Path file, String location, boolean blocking, String existingEtag) throws Exception {
        LOGGER.debug("Uploading {}", file);
        ObjectMetadata objectMetadata = null;
        try {
            //System.out.println("Getting metadata for " + location);
            if (Files.exists(file) && !Files.isDirectory(file))
            {
                objectMetadata = s3.getObjectMetadata(bucket, location);
            }

        } catch (AmazonS3Exception ignored) {}

        HashCode fileHash = HashUtils.hash(Hashing.sha256(), file);
        if (objectMetadata != null) {
            if (HashUtils.equals(fileHash, objectMetadata.getUserMetaDataOf("ourhash"))) {
                LOGGER.debug("Not uploading {} as object exists on server", file);
                return;
            }
        }

        BufferedInputStream bufferedStream;


        if(Files.isDirectory(file) && FileUtils.listDir(file).isEmpty())
        {
            bufferedStream = new BufferedInputStream(new ByteArrayInputStream(new byte[0]));
        }
        else
        {
            bufferedStream = new BufferedInputStream(Files.newInputStream(file));
        }

        long fileSize = Files.size(file);
        ObjectMetadata meta = new ObjectMetadata();
        meta.addUserMetadata("ourhash", fileHash.toString());
        meta.addUserMetadata("ourlastmodified", String.valueOf(FileUtils.getLastModified(file)));
        meta.setContentLength(fileSize);
        Upload upload = transferManager.upload(bucket, location.replace(" ", ""), bufferedStream, meta);
        if (blocking) {
            upload.waitForUploadResult();
        } else {
            currentUploads.add(upload);
            upload.addProgressListener((ProgressListener) progressEvent -> {
                ProgressEventType type = progressEvent.getEventType();
                if (type == ProgressEventType.TRANSFER_CANCELED_EVENT || type == ProgressEventType.TRANSFER_COMPLETED_EVENT || type == ProgressEventType.TRANSFER_FAILED_EVENT)
                {
                    currentUploads.remove(upload);
                    LOGGER.debug("Removed {} due to {}", upload.getDescription(), type.toString());
                }
            });
        }
    }

    public static List<UUID> getPrefixes()
    {
        Pattern pattern = Pattern.compile(".*([a-z0-9A-Z]{8}-[a-z0-9A-Z]{4}-[a-z0-9A-Z]{4}-[a-z0-9A-Z]{4}-[a-z0-9A-Z]{12}).*");
        List<UUID> uuidList = new ArrayList<>();

        for(Map.Entry<String, S3ObjectSummary> map : listObjects().entrySet())
        {
            String sub = map.getKey().substring(0, 38);
            Matcher matcher = pattern.matcher(sub);

            if (matcher.find())
            {
//                System.out.println(matcher.group(1));
                try
                {
                    UUID uuid = UUID.fromString(matcher.group(1));
                    boolean exists = false;

                    for (UUID uuid1 : uuidList)
                    {
                        if (uuid1.toString().equals(uuid.toString()))
                        {
                            exists = true;
                            break;
                        }
                    }
                    if (exists)
                    {
                        continue;
                    }
                    uuidList.add(uuid);
                } catch (Exception ignored) {}
            }
        }
        return uuidList;
    }

    public static void syncManual(Path file, String location, boolean blocking, boolean client, HashMap<String, S3ObjectSummary> existing) throws Exception
    {
        LOGGER.debug("Uploading {}", file);
        ObjectMetadata objectMetadata = null;
        try {
            S3ObjectSummary summary = existing.get(location);
            if (Files.exists(file) && summary != null) {
                if(summary.getSize() <= 5242500 && HashUtils.equals(HashUtils.hash(Hashing.md5(), file), summary.getETag())) {
                    LOGGER.debug("Not syncing {} as object exists on server", file);
                    return;
                }
                objectMetadata = s3.getObjectMetadata(bucket, location);
            }
            //System.out.println("Getting metadata for " + location);
        } catch (AmazonS3Exception ignored) {}

        HashCode fileHash = HashUtils.hash(Hashing.sha256(), file);
        if (objectMetadata != null) {
            LOGGER.debug("Client {} Server {}", fileHash, objectMetadata.getUserMetaDataOf("ourhash"));

            if (HashUtils.equals(fileHash, objectMetadata.getUserMetaDataOf("ourhash"))) {
                LOGGER.debug("Not syncing {} as object exists on server", file);
                return;
            } else
            {
                if(client)
                {
                    uploadFile(file, location, blocking, null);
                }
                else
                {
                    downloadFile(location, file, blocking, null);
                }
            }
        }
        else
        {
            uploadFile(file, location, blocking, null);
        }
    }

    public static void syncFile(Path file, String location, boolean blocking, HashMap<String, S3ObjectSummary> existingObjects) throws Exception
    {
        location = location.replace("\\", "/");
        LOGGER.debug("Uploading {}", file.toAbsolutePath());
        ObjectMetadata objectMetadata = null;
        try {
            if (Files.exists(file) && existingObjects.containsKey(location))
            {
                S3ObjectSummary summary = existingObjects.get(location);
                if (summary.getSize() <= 5242500 && HashUtils.equals(HashUtils.hash(Hashing.md5(), file), summary.getETag())) {
                    LOGGER.debug("Not syncing {} as object exists on server", file);
                    return;
                }
                objectMetadata = s3.getObjectMetadata(bucket, location);
            }
            //System.out.println("Getting metadata for " + location);
        } catch (AmazonS3Exception ignored) {}

        HashCode fileHash = HashUtils.hash(Hashing.sha256(), file);
        if (objectMetadata != null) {
            if (HashUtils.equals(fileHash, objectMetadata.getUserMetaDataOf("ourhash"))) {
                LOGGER.debug("Not uploading {} as object exists on server", file);
                return;
            } else
            {
                long ourModifiedClient = FileUtils.getLastModified(file);
                long ourModifiedServer = Long.parseLong(objectMetadata.getUserMetaDataOf("ourlastmodified"));

                LOGGER.debug("Client {} Server {}", ourModifiedClient, ourModifiedServer);
                if(ourModifiedClient > ourModifiedServer)
                {
                    uploadFile(file, location, blocking, null);
                }
                else if(ourModifiedClient < ourModifiedServer)
                {
                    downloadFile(location, file, blocking, null);
                }
            }
        }
        else
        {
            uploadFile(file, location, blocking, null);
        }
    }

    public static String getFile(String path) throws AmazonS3Exception
    {
        CloudSaveManager.setup(Constants.S3_HOST, 8080, Constants.S3_KEY, Constants.S3_SECRET, Constants.S3_BUCKET);
        return s3.getObjectAsString(bucket, path);
    }

    public static void deleteFile(String location)
    {
        try {
            LOGGER.debug("deleting file {}", location);
            s3.deleteObject(bucket, urlEncodeParts(location));
        } catch (Throwable e)
        {
            LOGGER.warn("Error deleting file from bucket. {}", location, e);
        }
    }

    public static void downloadFile(String location, Path file, boolean blocking, String eTag) throws Exception {
        LOGGER.debug("Downloading {}", location);

        if (Files.exists(file) && !Files.isDirectory(file))
        {
            if (eTag != null)
            {
                if (HashUtils.equals(HashUtils.hash(Hashing.md5(), file), eTag))
                {
                    LOGGER.debug("Not downloading {} as object exists on client", file);
                    return;
                }
            }
            ObjectMetadata objectMetadata = null;
            try {
                //System.out.println("Getting metadata for " + location);
                objectMetadata = s3.getObjectMetadata(bucket, location);
            } catch (AmazonS3Exception ignored) {}

            if (objectMetadata != null) {
                if (HashUtils.equals(HashUtils.hash(Hashing.md5(), file), objectMetadata.getUserMetaDataOf("ourhash"))) {
                    LOGGER.debug("Not downloading {} as object exists on client", file);
                    return;
                }
            }
        }

        Download download = transferManager.download(bucket, urlEncodeParts(location), file.toFile());

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
                    LOGGER.debug("Removed {} due to {}", download.getDescription(), type.toString());
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
            s3.getObjectMetadata(bucket, urlEncodeParts(location));
        } catch (AmazonS3Exception e) {
            if (e.getStatusCode() == 404) {
                return false;
            }
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    public static String fileToLocation(Path file, Path baseLocation) {
        return urlEncodeParts(baseLocation.relativize(file).toString());

    }

    public static String urlEncodeParts(String string) {
        String collect = Arrays.stream(string.replace("\\", "/").split("/")).map((conn) -> URLEncoder.encode(conn, StandardCharsets.UTF_8) + "/").collect(Collectors.joining());
        return collect.substring(0, collect.length() - 1);
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
