package net.creeperhost.creeperlauncher.api;

import java.net.BindException;
import net.creeperhost.creeperlauncher.Constants;
import net.creeperhost.creeperlauncher.CreeperLauncher;
import net.creeperhost.creeperlauncher.CreeperLogger;
import net.creeperhost.creeperlauncher.Settings;
import net.creeperhost.creeperlauncher.api.data.BaseData;
import net.creeperhost.creeperlauncher.util.GsonUtils;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

public class WebSocketAPI extends WebSocketServer
{
    public WebSocketAPI(InetSocketAddress address)
    {
        super(address);
    }

    int connections = 0;

    private ConcurrentLinkedQueue<String> notConnectedQueue = new ConcurrentLinkedQueue<>();

    public static Random random = new Random();

    public static int generateRandomPort() {
        return random.nextInt(9999) + 10000;
    }

    public static String generateSecret() {
        return UUID.randomUUID().toString();
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake)
    {
        if (CreeperLauncher.defaultWebsocketPort && !CreeperLauncher.isDevMode)
        {
            conn.send("{\"port\": \"" + CreeperLauncher.websocketPort + "\", \"secret\": \"" + CreeperLauncher.websocketSecret + "\"}");
            conn.close();
            CreeperLogger.INSTANCE.info("Front end connected: " + conn.getRemoteSocketAddress() + " - sending our socket and secret and relaunching websocket");
            CreeperLauncher.defaultWebsocketPort = false;
            Settings.webSocketAPI = new WebSocketAPI(new InetSocketAddress(InetAddress.getLoopbackAddress(), CreeperLauncher.websocketPort));
            Settings.webSocketAPI.start();
            try {
                stop();
            } catch (Exception ignored) {}
            return;
        } else {
            CreeperLauncher.websocketDisconnect = false;
        }

        CreeperLogger.INSTANCE.info("Front end connected: " + conn.getRemoteSocketAddress());
        connections++;
        notConnectedQueue.forEach(conn::send);
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote)
    {
        connections--;
        if (connections == 0) CreeperLauncher.websocketDisconnect = true;
        CreeperLogger.INSTANCE.info("closed " + conn.getRemoteSocketAddress() + " with exit code " + code + " additional info: " + reason);
    }

    @Override
    public void onMessage(WebSocket conn, String message)
    {
        WebSocketMessengerHandler.handleMessage(message);
    }

    @Override
    public void onMessage(WebSocket conn, ByteBuffer message)
    {
    }

    @Override
    public void onError(WebSocket conn, Exception ex)
    {
        try
        {
            CreeperLauncher.websocketDisconnect = true;
            CreeperLogger.INSTANCE.error("an error occurred on connection " + conn.getRemoteSocketAddress() + ":" + ex, ex);
        } catch (NullPointerException ignored)
        {
            if(ex instanceof BindException) {
                CreeperLauncher.websocketPort = generateRandomPort();
                CreeperLogger.INSTANCE.info("New Port: " + CreeperLauncher.websocketPort);
                Settings.webSocketAPI = new WebSocketAPI(new InetSocketAddress(InetAddress.getLoopbackAddress(), CreeperLauncher.websocketPort));
                Settings.webSocketAPI.start();
                try {
                    stop();
                } catch (Exception ignoredTwo) {}
            }
        }
    }

    @Override
    public void onStart()
    {
        CreeperLogger.INSTANCE.info("Server started successfully - " + Constants.APPVERSION);
    }

    // TODO: ensure thread safety
    public void sendMessage(BaseData data)
    {
        String s = GsonUtils.GSON.toJson(data);
        if (getConnections().isEmpty())
        {
            notConnectedQueue.add(s);
        } else {
            getConnections().forEach((client) -> client.send(s));
        }
    }
}
