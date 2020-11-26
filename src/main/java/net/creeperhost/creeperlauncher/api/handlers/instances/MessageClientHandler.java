package net.creeperhost.creeperlauncher.api.handlers.instances;

import com.google.gson.JsonObject;
import net.creeperhost.creeperlauncher.CreeperLauncher;
import net.creeperhost.creeperlauncher.CreeperLogger;
import net.creeperhost.creeperlauncher.Instances;
import net.creeperhost.creeperlauncher.api.data.instances.MessageClientData;
import net.creeperhost.creeperlauncher.api.handlers.IMessageHandler;
import net.creeperhost.creeperlauncher.pack.LocalInstance;

import java.io.OutputStream;
import java.util.UUID;

public class MessageClientHandler implements IMessageHandler<MessageClientData> {
    @Override
    public void handle(MessageClientData data) {
        try {
            LocalInstance instance = Instances.getInstance(UUID.fromString(data.uuid));
            if (instance.loadingModSocket != null && instance.loadingModSocket.isConnected())
            {
                OutputStream outputStream = instance.loadingModSocket.getOutputStream();
                JsonObject jsonObject = new JsonObject();
                jsonObject.addProperty("instance", data.uuid);
                jsonObject.addProperty("message", data.message);
                outputStream.write((jsonObject.toString()+"\n").getBytes());
            }
        } catch (Throwable e) {
            CreeperLogger.INSTANCE.warning("Error sending message to Minecraft client", e);
        }
    }
}
