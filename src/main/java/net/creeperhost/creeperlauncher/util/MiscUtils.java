package net.creeperhost.creeperlauncher.util;

import com.sun.jna.platform.win32.Advapi32Util;
import com.sun.jna.platform.win32.Win32Exception;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.platform.win32.WinReg;
import net.creeperhost.creeperlauncher.CreeperLauncher;
import net.creeperhost.creeperlauncher.os.OSUtils;
import org.apache.tika.io.IOUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MiscUtils
{
    public static final DateFormat ISO_8601 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");

    public static String byteArrayToHex(byte[] a)
    {
        StringBuilder sb = new StringBuilder(a.length * 2);
        for (byte b : a)
            sb.append(String.format("%02x", b));
        return sb.toString();
    }

    public static long unixtime()
    {
        return System.currentTimeMillis() / 1000L;
    }

    public static String getDateAndTime()
    {
        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ENGLISH);
        LocalDateTime now = LocalDateTime.now();

        return dateTimeFormatter.format(now);
    }

    private static String[] javaRegLocationsAdoptOpenJDK = new String[] {"SOFTWARE\\AdoptOpenJDK\\JDK", "SOFTWARE\\AdoptOpenJDK\\JRE"};
    private static String[] javaRegLocationsOracle = new String[] {"SOFTWARE\\JavaSoft\\Java Development Kit", "SOFTWARE\\JavaSoft\\Java Runtime Environment"};
    private static String[] linuxJavaPathLocations = new String[] {"/usr/lib/jvm", "/usr/java"};

    public static void updateJavaVersions()
    {
        HashMap<String, String> versions = new HashMap<>();
        switch(OSUtils.getOs()) {
            case WIN:
                versions.put("Mojang Built-in", "");
                for (String location : javaRegLocationsOracle)
                {
                    if (Advapi32Util.registryKeyExists(WinReg.HKEY_LOCAL_MACHINE, location))
                    {
                        WinReg.HKEYByReference key = Advapi32Util.registryGetKey(WinReg.HKEY_LOCAL_MACHINE, location, WinNT.KEY_READ);
                        String[] children = Advapi32Util.registryGetKeys(key.getValue());
                        for (String child : children) {
                            String javaHome = null;
                            try {
                                javaHome = Advapi32Util.registryGetStringValue(WinReg.HKEY_LOCAL_MACHINE, location + "\\" + child, "JavaHome");
                            } catch (Win32Exception ignored) {
                            }
                            if (javaHome != null) {
                                versions.put("Oracle " + child + " " + (location.contains("Development") ? "JDK" : "JRE") + " (64bit)", Path.of(javaHome, "bin" + File.separator + "java.exe").toString());
                            }
                        }
                    }
                }
                for (String location : javaRegLocationsAdoptOpenJDK)
                {
                    if (Advapi32Util.registryKeyExists(WinReg.HKEY_LOCAL_MACHINE, location))
                    {
                        WinReg.HKEYByReference key = Advapi32Util.registryGetKey(WinReg.HKEY_LOCAL_MACHINE, location, WinNT.KEY_READ);
                        String[] children = Advapi32Util.registryGetKeys(key.getValue());
                        for (String child : children) {
                            String javaHome = null;
                            WinReg.HKEYByReference keyTypes = Advapi32Util.registryGetKey(WinReg.HKEY_LOCAL_MACHINE, location + "\\" + child, WinNT.KEY_READ);
                            String[] childrenTypes = Advapi32Util.registryGetKeys(keyTypes.getValue());
                            for (String childType : childrenTypes) {
                                try {
                                    javaHome = Advapi32Util.registryGetStringValue(WinReg.HKEY_LOCAL_MACHINE, location + "\\" + child + "\\" + childType + "\\MSI", "Path");
                                } catch (Win32Exception ignored) {
                                }
                                if (javaHome != null) {
                                    versions.put("AdoptOpenJDK " + child + "_" + childType + " " + location.substring(location.lastIndexOf("\\") + 1) + " (64bit)", Path.of(javaHome, "bin" + File.separator + "java.exe").toString());
                                }
                            }
                        }
                    }
                }
                String java = findExecutableOnPath("java.exe");
                if (!java.isEmpty())
                {
                    File javaFile = new File(java);
                    String ver = versionFromFile(javaFile);
                    if (!ver.equals("Unknown")) versions.put(ver, java);
                }
                break;
            case MAC:
                versions.put("Mojang Built-in", "");
                String javaMac = findExecutableOnPath("java");
                if (!javaMac.isEmpty())
                {
                    File javaFile = new File(javaMac);
                    String ver = versionFromFile(javaFile);
                    if (!ver.equals("Unknown")) versions.put(ver, javaMac);
                }
                break;
            case LINUX:
                versions = scanPath(linuxJavaPathLocations);
                String javaLinux = findExecutableOnPath("java");
                if (!javaLinux.isEmpty())
                {
                    File javaFile = new File(javaLinux);
                    String ver = versionFromFile(javaFile);
                    if (!ver.equals("Unknown")) versions.put(ver, javaLinux);
                }
                break;
            case UNKNOWN:
                break;
        }

        CreeperLauncher.javaVersions = versions;
    }

    private static HashMap<String, String> scanPath(String[] paths)
    {
        HashMap<String, String> versions = new HashMap<>();
        for (String location : paths) {
            Path path = Path.of(location);
            File file = path.toFile();
            if(!file.isDirectory()) continue;
            File[] files = file.listFiles();
            if (files == null) continue;
            for(File javaDir : files)
            {
                javaDir = unsymlink(file);
                if (javaDir == null) continue;
                String javaVersionName = javaDir.getName();
                if (!javaVersionName.matches("(.*)\\d+(.*)")) continue;
                File javaExe = new File(javaDir, "bin/java");
                if (!javaExe.exists() || !javaExe.canExecute()) javaExe = new File(javaDir, "bin/java.exe");
                if (!javaExe.exists() || !javaExe.canExecute()) continue;
                versions.put(javaVersionName, javaExe.getAbsolutePath());
            }
        }
        return versions;
    }

    private static File unsymlink(File file) {
        int i = 0;
        for(; i < 5 && Files.isSymbolicLink(file.toPath()); i++)
        {
            try {
                file = new File(file.getCanonicalPath());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (i == 5 && Files.isSymbolicLink(file.toPath())) return null;
        return file;
    }

    private static String versionFromFile(File file) {
        File unsymlink = unsymlink(file);
        if (unsymlink != null) file = unsymlink;
        File parent = file.getParentFile();
        if (!parent.getName().equals("bin")) return "Unknown";
        File versionDir = file.getParentFile();
        String version = versionDir.getName();
        if (version.matches("(.*)\\d+(.*)")) return version;
        return versionFromExecutable(file.getPath());
    }

    private static String findExecutableOnPath(String name) {
        for (String dirname : System.getenv("PATH").split(File.pathSeparator)) {
            File file = new File(dirname, name);
            if (file.isFile() && file.canExecute()) {
                return file.getAbsolutePath();
            }
        }
        return "";
    }

    private static String execAndFullOutput(String ...args) {
        ProcessBuilder builder = new ProcessBuilder();
        builder.redirectErrorStream(true);
        builder.command(args);
        try {
            Process start = builder.start();
            byte[] bytes = IOUtils.toByteArray(start.getInputStream());
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    private static final Pattern p = Pattern.compile("\\w+ version \"(.*?)\"");

    private static String versionFromExecutable(String path)
    {
        String execResult = execAndFullOutput(path, "-version");
        Matcher m = p.matcher(execResult);
        if (m.find())
        {
            return m.group(1);
        }
        return "Unknown";
    }

    public static boolean isInt(String in){
        try {
            Integer.parseInt(in);
        }catch(Exception ignored) {
            return false;
        }
        return true;
    }
}
