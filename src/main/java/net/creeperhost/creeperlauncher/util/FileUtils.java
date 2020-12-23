package net.creeperhost.creeperlauncher.util;

import net.creeperhost.creeperlauncher.CreeperLogger;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.tika.detect.Detector;
import org.apache.tika.io.IOUtils;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AutoDetectParser;

import java.io.*;
import java.nio.file.FileSystem;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class FileUtils
{
    public static List<Path> unTar(InputStream is, final Path outputDir) throws IOException
    {
        final List<Path> untaredFiles = new LinkedList<>();
        try (TarArchiveInputStream tarStream = new TarArchiveInputStream(is)) {
            ArchiveEntry entry;
            while ((entry = tarStream.getNextEntry()) != null)
            {
                final Path outputFile = outputDir.resolve(entry.getName());
                if (entry.isDirectory()) continue;

                FileUtils.createDirectories(outputFile.getParent());
                Files.copy(tarStream, outputFile);
                untaredFiles.add(outputFile);
            }
        }
        return untaredFiles;
    }

    public static void fileFromZip(Path zip, Path dest, String fileName) throws IOException
    {
        try (java.nio.file.FileSystem fileSystem = FileSystems.newFileSystem(zip, null))
        {
            Path fileToExtract = fileSystem.getPath(fileName);
            Files.copy(fileToExtract, dest, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static boolean copyDirectory(Path sourceDir, Path destinationDir) throws IOException
    {
        AtomicBoolean error = new AtomicBoolean(false);
        Files.walk(sourceDir).forEach(sourcePath -> {
            try {
                Path targetPath = destinationDir.resolve(sourceDir.relativize(sourcePath));
                Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ex) {
                CreeperLogger.INSTANCE.error("File copy I/O error: ", ex);
                error.set(true);
            }
        });
        return !error.get();
    }

    public static HashMap<String, Exception> extractZip2ElectricBoogaloo(Path launcherFile, Path destination)
    {
        return extractZip2ElectricBoogaloo(launcherFile, destination, true);
    }

    public static HashMap<String, Exception> extractZip2ElectricBoogaloo(Path launcherFile, Path destination, boolean continueOnError)
    {
        HashMap<String, Exception> errors = new HashMap<>();
        try (ZipFile zipFile = new ZipFile(launcherFile.toFile())) {
            ArrayList<String> entries = new ArrayList<>();
            zipFile.stream().map(ZipEntry::getName).forEach(entries::add);
            for(String ze : entries) {
                CreeperLogger.INSTANCE.debug("Extracting '" + ze + "'...");
                ZipEntry entry = zipFile.getEntry(ze);
                try {
                    Path DestFile = destination.resolve(entry.getName());
                    if (entry.isDirectory())
                    {
                        DestFile.getParent().toFile().mkdir();
                        continue;
                    }
                    DestFile.getParent().toFile().mkdirs();
                    InputStream inputStream = zipFile.getInputStream(entry);
                    byte[] bytes = inputStream.readAllBytes();
                    CreeperLogger.INSTANCE.debug("Writing to " + DestFile);
                    Files.write(DestFile, bytes);
                    inputStream.close();
                } catch (Exception e) {
                    CreeperLogger.INSTANCE.debug("Failed extracting file " + entry.getName(), e);
                    errors.put(entry.getName(), e);
                    if (!continueOnError) {
                        return errors;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return errors;
    }

    public static void deleteDirectory(Path file)
    {
        if (Files.notExists(file)) return;
        try {
            Files.walk(file)
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);

        } catch (IOException e) {
            CreeperLogger.INSTANCE.error("Failed to delete directory. " + file, e);
        }
    }

    public static String getMimeType(String jarResource)
    {
        InputStream input = MiscUtils.class.getResourceAsStream(jarResource);
        if (input != null)
        {
            try (InputStream is = input; BufferedInputStream bis = new BufferedInputStream(is))
            {
                AutoDetectParser parser = new AutoDetectParser();
                Detector detector = parser.getDetector();
                Metadata md = new Metadata();
                md.add(Metadata.RESOURCE_NAME_KEY, jarResource);
                MediaType mediaType = detector.detect(bis, md);
                return mediaType.toString();
            } catch (IOException e)
            {
                e.printStackTrace();
            }
        }
        return "application/octet-stream";
    }

    public static void setFilePermissions(Path file)
    {
        try
        {
            Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rwxr-xr-x"));
        } catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    public static long getLastModified(Path file)
    {
        if (file != null) {
            try {
                return Files.getLastModifiedTime(file).toMillis();
            } catch (IOException ignored) {
            }
        }

        return 0L;
    }

    public static String getHash(Path file, String hashType)
    {
        try {
            return hashToString(createChecksum(file, hashType));
        } catch (Exception e) {
            return "error - " + e.getMessage();
        }
    }

    private static byte[] createChecksum(Path file, String hashType) throws Exception {
        try (InputStream is = Files.newInputStream(file)) {

            byte[] buffer = new byte[4096];
            MessageDigest complete = MessageDigest.getInstance(hashType);
            int numRead;

            do {
                numRead = is.read(buffer);
                if (numRead > 0) {
                    complete.update(buffer, 0, numRead);
                }
            }
            while (numRead != -1);
            return complete.digest();
        }
    }

    private static String hashToString(byte[] b) {
        StringBuilder result = new StringBuilder();

        for (byte value : b) {
            result.append(Integer.toString((value & 0xff) + 0x100, 16).substring(1));
        }
        return result.toString();
    }

    //I hate this but its the only way I can get it to work right now
    public static boolean removeMeta(Path file)
    {
        if(!Files.exists(file)) return false;
        try (FileSystem fileSystem = FileSystems.newFileSystem(file, null))
        {
            Path root = fileSystem.getPath("/");
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    try {
                        if (file.startsWith("/META-INF")) {
                            Files.deleteIfExists(file);
                        }
                    } catch (Exception e) {
                        CreeperLogger.INSTANCE.error("Failed to delete entry from META-INF", e);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });

//            FileUtils.deleteDirectory(meta);
            return true;
        } catch (IOException e) {
            CreeperLogger.INSTANCE.error("Failed to remove meta from " + file, e);
        }
        return false;
    }

    public static boolean mergeJars(Path input, Path output) {
        if(input == null || output == null) return false;
        AtomicBoolean flag = new AtomicBoolean(true);

        try (FileSystem fs = FileSystems.newFileSystem(output, null)) {
            Path dstRoot = fs.getPath("/");
            try (FileSystem srcFs = FileSystems.newFileSystem(input, null)) {
                Path srcRoot = srcFs.getPath("/");
                Files.walkFileTree(srcRoot, new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                        Path dst = dstRoot.resolve(srcRoot.relativize(file));
                        try {
                            Files.createDirectories(dst.getParent());
                            Files.copy(file, dst, StandardCopyOption.REPLACE_EXISTING);
                        } catch (Exception e) {
                            flag.set(false);
                            e.printStackTrace();
                        }
                        return FileVisitResult.CONTINUE;
                    }
                });
            }
        } catch (Exception e) {
            flag.set(false);
            e.printStackTrace();
        }
        return flag.get();
    }

    public static void createDirectories(Path dir)
    {
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            CreeperLogger.INSTANCE.error("Failed to create directories.", e);
        }
    }

    public static List<Path> listDir(Path dir) {
        try (Stream<Path> stream = Files.walk(dir, 1)) {
            return stream.filter(e -> !e.equals(dir))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            CreeperLogger.INSTANCE.error("Failed to list directory. " + dir.toAbsolutePath(), e);
            return Collections.emptyList();
        }
    }

    private static HashMap<Pair<Path, Path>, IOException> moveDirectory(Path in, Path out, boolean replaceExisting, boolean failFast) {
        HashMap<Pair<Path, Path>, IOException> errors = new HashMap<>();
        if (!in.getFileName().toString().equals(out.getFileName().toString()))
        {
            out = out.resolve(in.getFileName().toString());
        }
        if (replaceExisting && Files.exists(out))
        {
            if (Files.isDirectory(out))
            {
                FileUtils.deleteDirectory(out);
            } else {
                try {
                    Files.deleteIfExists(out);
                } catch (IOException e) {
                    // shrug
                }
            }
        }

        try {
            Files.move(in, out);
            return errors;
        } catch (IOException e) {
            CreeperLogger.INSTANCE.warning("Could not move " + in + " to " + out + " - trying another method", e);
        }

        try {
            Path finalOut = out;
            Files.walkFileTree(in, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path path, BasicFileAttributes basicFileAttributes) throws IOException {

                    Path relative = in.getParent().relativize(path);
                    if (in.getFileName().toString().equals(finalOut.getFileName().toString())) {
                        relative = in.relativize(path);
                    }
                    Path outFile = finalOut.resolve(relative);
                    Files.createDirectories(outFile.getParent());
                    try {
                        Files.move(path, outFile);
                    } catch (IOException e) {
                        boolean copyFailed = true;
                        try {
                            Files.copy(path, outFile); // try and copy anyway
                            copyFailed = false;
                        } catch (IOException e2) {
                            errors.put(new Pair<>(path, outFile), e2);
                            if (failFast)
                                return FileVisitResult.TERMINATE;
                        }

                        if (!copyFailed)
                        {
                            try {
                                Files.delete(path); // try to delete even if we couldn't move, but if we could copy
                            } catch (Exception ignored) {
                                // shrug
                            }
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path directory, IOException ioException) throws IOException
                {
                    List<Path> list = FileUtils.listDir(directory);
                    if (list.isEmpty())
                    {
                        try {
                            Files.delete(directory);
                        } catch (Exception ignored) {
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }

        return errors;
    }

    public static HashMap<Pair<Path, Path>, IOException> move(Path in, Path out)
    {
        return move(in, out, false, true);
    }

    public static HashMap<Pair<Path, Path>, IOException> move(Path in, Path out, boolean replaceExisting, boolean failFast)
    {
        if (Files.isDirectory(in))
        {
            return moveDirectory(in, out, replaceExisting, failFast);
        }
        HashMap<Pair<Path, Path>, IOException> errors = new HashMap<>();
        try
        {
            if (Files.exists(out) && Files.isDirectory(out))
            {
                out = out.resolve(in.getFileName().toString());
            }
            if (replaceExisting) {
                Files.move(in, out, StandardCopyOption.REPLACE_EXISTING);
            } else {
                Files.move(in, out);
            }
        } catch (IOException e) {
            errors.put(new Pair<>(in, out), e);
        }
        return errors;
    }
}
