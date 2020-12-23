package net.creeperhost.creeperlauncher.api.handlers.other;

import net.creeperhost.creeperlauncher.Constants;
import net.creeperhost.creeperlauncher.Settings;
import net.creeperhost.creeperlauncher.api.data.other.FileHashData;
import net.creeperhost.creeperlauncher.api.handlers.IMessageHandler;
import net.creeperhost.creeperlauncher.util.FileUtils;

import java.nio.file.Path;

public class FileHashHandler implements IMessageHandler<FileHashData> {
    @Override
    public void handle(FileHashData data) {
        Path file = Settings.getInstanceLocOr(Constants.INSTANCES_FOLDER_LOC).resolve(data.uuid).resolve(data.filePath);
        Settings.webSocketAPI.sendMessage(new FileHashData.Reply(data, FileUtils.getHash(file, "MD5"), FileUtils.getHash(file, "SHA-256")));
    }
}
