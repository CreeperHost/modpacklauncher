package net.creeperhost.creeperlauncher.mod;

import com.google.common.hash.HashCode;
import com.google.gson.JsonSyntaxException;
import net.creeperhost.creeperlauncher.Constants;
import net.creeperhost.creeperlauncher.api.DownloadableFile;
import net.creeperhost.creeperlauncher.api.handlers.ModFile;
import net.creeperhost.creeperlauncher.pack.IPack;
import net.creeperhost.creeperlauncher.util.GsonUtils;
import net.creeperhost.creeperlauncher.util.WebUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class Mod {
    int id;
    String name;
    int installs;
    String status;
    String message;
    ArrayList<Version> versions;

    public static class Version {
        String version;
        String path;
        String name;
        String url;
        String sha1;
        String updated;
        long size;
        boolean clientOnly;
        long id;
        String type;
        ArrayList<Target> targets;
        ArrayList<Integer> dependencies;
        private transient Mod parentMod;
        private transient boolean existsOnDisk;

        public DownloadableFile getDownloadableFile(IPack instance) {
            ArrayList<HashCode> hashes = new ArrayList<>();

            if (!sha1.isEmpty()) {
                HashCode code = HashCode.fromString(sha1);
                hashes.add(code);
            }

            return new DownloadableFile(version, instance.getDir().resolve(path).resolve(name), url, hashes, size, id, name, type, updated);
        }

        public List<Version> getDependencies(List<ModFile> existingFiles, List<Version> fileCandidates) {
            if (fileCandidates == null) {
                fileCandidates = new ArrayList<>();
                fileCandidates.add(this);
            }
            ArrayList<Version> dependTemp = new ArrayList<>();
            dependLoop:
            for (Integer dependInt: dependencies) {
                for(Version candVer: fileCandidates) {
                    if (candVer.parentMod.id == dependInt) {
                        continue dependLoop;
                    }
                }
                Mod otherMod = getFromAPI(dependInt);
                if (otherMod != null) {
                    Version versionMatching = otherMod.findVersionMatching(existingFiles, targets);
                    dependTemp.add(versionMatching);
                    dependTemp.addAll(versionMatching.getDependencies(existingFiles, fileCandidates));
                }
            }
            return dependTemp;
        }

        public void setParent(Mod mod) {
            parentMod = mod;
        }

        public static class Target {
            String version;
            String name;
            String type;
        }
    }

    private Version findVersionMatching(List<ModFile> existingFiles, ArrayList<Version.Target> targets) {
        for (Version tempVer: versions) {
            Optional<ModFile> first = existingFiles.stream().filter(mod -> mod.getName().equals(tempVer.name)).findFirst();
            if (first.isPresent()) {
                tempVer.existsOnDisk = true;
                return tempVer;
            }
        }

        targets.stream().filter(target -> target.type.equals("game")).sorted((a, b) -> {
            String aStr = a.version;
            String bStr = b.version;
            if (a.equals(b)) return 0;
            String aOnlyNumbers = aStr.replaceAll("[^0-9.]", "");
            String bOnlyNumbers = bStr.replaceAll("[^0-9.]", "");
            String[] splita = aOnlyNumbers.split("\\.");
            String[] splitb = bOnlyNumbers.split("\\.");
            for (int i = 0, splitaLength = splita.length; i < splitaLength; i++) {
                if(i > splitb.length - 1) return 1;
                int partIntA = -1;
                int partIntB = -1;
                try {
                    partIntA = Integer.parseInt(splita[i]);
                } catch (Throwable ignored) {}
                try {
                    partIntB = Integer.parseInt(splitb[i]);
                } catch (Throwable ignored) {}
                int res = Integer.compare(partIntA, partIntB);
                if (res != 0) return res;
            }
            return splitb.length > splita.length ? -1 : 0;
        }).forEach(System.out::println);

        return null;
    }

    public Version getVersion(int id) {
        for (Version version: versions) {
            if (version.id == id) {
                version.setParent(this);
                return version;
            }
        }
        return null;
    }

    public static Mod getFromAPI(int id) {
        String resp = WebUtils.getAPIResponse(Constants.MOD_API + id);
        try {
            Mod mod = GsonUtils.GSON.fromJson(resp, Mod.class);
            if (mod.status.equals("error")) {
                return null;
            }
            return mod;
        } catch (JsonSyntaxException e) {
            return null;
        }
    }
}
