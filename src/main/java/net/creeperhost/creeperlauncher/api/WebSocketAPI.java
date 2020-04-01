package net.creeperhost.creeperlauncher.api;

import net.creeperhost.creeperlauncher.Constants;
import net.creeperhost.creeperlauncher.CreeperLogger;
import net.creeperhost.creeperlauncher.api.data.BaseData;
import net.creeperhost.creeperlauncher.util.GsonUtils;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

public class WebSocketAPI extends WebSocketServer
{
    public WebSocketAPI(InetSocketAddress address)
    {
        super(address);
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake)
    {
        CreeperLogger.INSTANCE.info("Front end connected: " + conn.getRemoteSocketAddress());
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote)
    {
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
            CreeperLogger.INSTANCE.error("an error occurred on connection " + conn.getRemoteSocketAddress() + ":" + ex);
        } catch (NullPointerException ignored)
        {
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
        getConnections().forEach((client) -> client.send(GsonUtils.GSON.toJson(data)));
    }
}