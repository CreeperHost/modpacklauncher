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
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
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

    public static <FileSystem> void removeFileFromZip(File zip, String fileName) throws IOException
    {
        try (java.nio.file.FileSystem fileSystem = (java.nio.file.FileSystem) FileSystems.newFileSystem(zip.toPath(), null))
        {
            Path fileToRemove = ((java.nio.file.FileSystem) fileSystem).getPath(fileName);
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

    private static String hashToString(byte[] b) {
        StringBuilder result = new StringBuilder();

        for (byte value : b) {
            result.append(Integer.toString((value & 0xff) + 0x100, 16).substring(1));
        }
        return result.toString();
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
}
