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
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.jar.JarFile;
import java.util.zip.GZIPInputStream;

public class FileUtils
{
    public static List<Path> unTar(InputStream is, final Path outputDir) throws IOException
    {
        final List<Path> untaredFiles = new LinkedList<>();
        try (TarArchiveInputStream tarStream = new TarArchiveInputStream(is)) {
            ArchiveEntry entry = null;
            while ((entry = tarStream.getNextEntry()) != null)
            {
                final Path outputFile = outputDir.resolve(entry.getName());
                if (entry.isDirectory())
                {
                    FileUtils.createDirectories(outputDir);
                    continue;
                }
                Files.copy(tarStream, outputFile);
                untaredFiles.add(outputFile);
            }
        }
        return untaredFiles;
    }

    public static void unGzip(File input, File output) throws IOException
    {
        byte[] buffer = new byte[1024];
        GZIPInputStream inputStream = new GZIPInputStream(new FileInputStream(input));
        FileOutputStream outputStream = new FileOutputStream(output);

        int i;
        while ((i = inputStream.read(buffer)) > 0)
        {
            outputStream.write(buffer, 0, i);
        }

        inputStream.close();
        outputStream.close();
    }

    public static void fileFromZip(Path zip, Path dest, String fileName) throws IOException
    {
        try (java.nio.file.FileSystem fileSystem = FileSystems.newFileSystem(zip, null))
        {
            Path fileToExtract = fileSystem.getPath(fileName);
            Files.copy(fileToExtract, dest, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static <FileSystem> void removeFileFromZip(File zip, String fileName) throws IOException
    {
        try (java.nio.file.FileSystem fileSystem = FileSystems.newFileSystem(zip.toPath(), null))
        {
            Path fileToRemove = fileSystem.getPath(fileName);
            deleteDirectory(fileToRemove.toFile());
        }
    }

    public static boolean deleteDirectory(File file)
    {
        try
        {
            Files.walk(file.toPath())
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);

        } catch (IOException e)
        {
            e.printStackTrace();
        }
        return file.delete();
    }

    public static void merge(List<File> files, File out)
    {
        try
        {
            FileSystem outSystem = FileSystems.newFileSystem(out.toPath(), null);
            files.forEach(file ->
            {
                try
                {
                    FileSystem fileSystem = FileSystems.newFileSystem(file.toPath(), null);
                    fileSystem.getRootDirectories().forEach(path ->
                    {
                        try
                        {
                            Files.copy(path.toFile().toPath(), outSystem.getPath(path.toFile().getPath()), StandardCopyOption.REPLACE_EXISTING);
                        } catch (IOException e)
                        {
                            e.printStackTrace();
                        }
                    });
                } catch (IOException e)
                {
                    e.printStackTrace();
                }
            });
        } catch (Exception e)
        {
            e.printStackTrace();
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

    public static Long getLastModified(File file)
    {
        if (file != null)
            return file.lastModified();

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
    public static boolean removeMeta(File file)
    {
        if(!file.exists()) return false;
        try (FileSystem fileSystem = FileSystems.newFileSystem(file.toPath(), null))
        {
            Path root = fileSystem.getPath("/");
            Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException
                {
                    try
                    {
                        if(file.startsWith("/META-INF")) {
                            CreeperLogger.INSTANCE.error(file.toString());
                            Files.delete(file);
                        }
                    }
                    catch (Exception e)
                    {
                        e.printStackTrace();
                    }
                    return FileVisitResult.CONTINUE;
                }
            });

//            FileUtils.deleteDirectory(meta);
            return true;
        } catch (IOException e) { e.printStackTrace(); }
        return false;
    }

    public static boolean mergeJars(File input, File output)
    {
        if(input == null || output == null) return false;
        AtomicBoolean flag = new AtomicBoolean(true);

        try (FileSystem fs = FileSystems.newFileSystem(output.toPath(), null))
        {
            FileSystem tempFS = FileSystems.newFileSystem(input.toPath(), null);
            Path root = tempFS.getPath("/");
            Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException
                {
                    try
                    {
                        //Make sure to create the parents as java is dumb...
                        Files.createDirectories(fs.getPath(file.getParent().toString()));
                        Files.copy(tempFS.getPath(file.toString()), fs.getPath(file.toString()), StandardCopyOption.REPLACE_EXISTING);
                    }
                    catch (Exception e)
                    {
                        flag.set(false);
                        e.printStackTrace();
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (Exception e)
        {
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

    public static void deleteDirectory(Path directory)
    {

        if (Files.exists(directory))
        {
            try
            {
                Files.walkFileTree(directory, new SimpleFileVisitor<>()
                {
                    @Override
                    public FileVisitResult visitFile(Path path, BasicFileAttributes basicFileAttributes) throws IOException
                    {
                        Files.delete(path);
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult postVisitDirectory(Path directory, IOException ioException) throws IOException
                    {
                        Files.delete(directory);
                        return FileVisitResult.CONTINUE;
                    }
                });
            }
            catch (Exception ignored)
            {

            }
        }
    }

    private static HashMap<Pair<Path, Path>, IOException> moveDirectory(Path in, Path out, boolean replaceExisting, boolean failFast) {
        HashMap<Pair<Path, Path>, IOException> errors = new HashMap<>();
        if (!in.toFile().getName().equals(out.toFile().getName()))
        {
            out = out.resolve(in.toFile().getName());
        }
        File outFile = out.toFile();
        if (replaceExisting && outFile.exists())
        {
            if (outFile.isDirectory())
            {
                FileUtils.deleteDirectory(out.toFile());
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
                    if (in.toFile().getName().equals(finalOut.toFile().getName())) {
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
                    String[] list = directory.toFile().list();
                    if (list == null || list.length == 0)
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
        if (in.toFile().isDirectory())
        {
            return moveDirectory(in, out, replaceExisting, failFast);
        }
        HashMap<Pair<Path, Path>, IOException> errors = new HashMap<>();
        try
        {
            File outFile = out.toFile();
            if (outFile.exists() && outFile.isDirectory())
            {
                out = out.resolve(in.toFile().getName());
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
