package net.creeperhost.creeperlauncher.util;

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
import java.util.zip.GZIPInputStream;

public class FileUtils
{
    public static List<File> unTar(final File inputFile, final File outputDir) throws IOException, ArchiveException
    {
        final List<File> untaredFiles = new LinkedList<File>();
        final InputStream is = new FileInputStream(inputFile);
        final TarArchiveInputStream debInputStream = (TarArchiveInputStream) new ArchiveStreamFactory().createArchiveInputStream("tar", is);
        TarArchiveEntry entry = null;
        while ((entry = (TarArchiveEntry) debInputStream.getNextEntry()) != null)
        {
            final File outputFile = new File(outputDir, entry.getName());
            if (entry.isDirectory())
            {
                if (!outputFile.exists())
                {
                    if (!outputFile.mkdirs())
                    {
                        throw new IllegalStateException(String.format("Couldn't create directory %s.", outputFile.getAbsolutePath()));
                    }
                }
            } else
            {
                final OutputStream outputFileStream = new FileOutputStream(outputFile);
                IOUtils.copy(debInputStream, outputFileStream);
                outputFileStream.close();
            }
            untaredFiles.add(outputFile);
        }
        debInputStream.close();

        inputFile.delete();
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

    public static <FileSystem> void fileFromZip(File zip, File dest, String fileName) throws IOException
    {
        try (java.nio.file.FileSystem fileSystem = (java.nio.file.FileSystem) FileSystems.newFileSystem(zip.toPath(), null))
        {
            Path fileToExtract = ((java.nio.file.FileSystem) fileSystem).getPath(fileName);
            Files.copy(fileToExtract, dest.toPath());
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

    public static void setFilePermissions(File file)
    {
        try
        {
            Files.setPosixFilePermissions(file.toPath(), PosixFilePermissions.fromString("rwxr-xr-x"));
        } catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    public static String getHash(File file, String hashType)
    {
        try {
            return hashToString(createChecksum(file, hashType));
        } catch (Exception e) {
            return "error - " + e.getMessage();
        }
    }

    private static byte[] createChecksum(File file, String hashType) throws Exception {
        InputStream fis =  new FileInputStream(file);

        byte[] buffer = new byte[4096];
        MessageDigest complete = MessageDigest.getInstance(hashType);
        int numRead;

        do {
            numRead = fis.read(buffer);
            if (numRead > 0) {
                complete.update(buffer, 0, numRead);
            }
        } while (numRead != -1);

        fis.close();
        return complete.digest();
    }

    // see this How-to for a faster way to convert
    // a byte array to a HEX string
    private static String hashToString(byte[] b) throws Exception {
        StringBuilder result = new StringBuilder();

        for (byte value : b) {
            result.append(Integer.toString((value & 0xff) + 0x100, 16).substring(1));
        }
        return result.toString();
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

    private static HashMap<Pair<Path, Path>, IOException> moveDirectory(Path in, Path out, boolean failFast) {
        HashMap<Pair<Path, Path>, IOException> errors = new HashMap<>();
        if (!in.toFile().getName().equals(out.toFile().getName()))
        {
            out = out.resolve(in.toFile().getName());
        }
        if (in.getFileSystem() == out.getFileSystem())
        {
            try {
                Files.move(in, out);
            } catch (IOException e) {
                errors.put(new Pair<>(in, out), e);
            }
            return errors;
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
                        errors.put(new Pair<>(path, outFile), e);
                        if (failFast)
                            return FileVisitResult.TERMINATE;
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
        return move(in, out, true);
    }

    public static HashMap<Pair<Path, Path>, IOException> move(Path in, Path out, boolean failFast)
    {
        if (in.toFile().isDirectory())
        {
            return moveDirectory(in, out, failFast);
        }
        HashMap<Pair<Path, Path>, IOException> errors = new HashMap<>();
        try
        {
            File outFile = out.toFile();
            if (outFile.exists() && outFile.isDirectory())
            {
                out = out.resolve(in.toFile().getName());
            }
            Files.move(in, out);
        } catch (IOException e) {
            errors.put(new Pair<>(in, out), e);
        }
        return errors;
    }
}
