package net.creeperhost.creeperlauncher.api.data.other;

import net.creeperhost.creeperlauncher.api.data.BaseData;

public class ClientLaunchData extends BaseData {
    public static class Reply extends BaseData {
        String type;
        String message;
        Object clientData;
        public Reply(String messageType, String message){
             this(messageType, message, null);
        }
        public Reply(String messageType, String message, Object clientData)
        {
            this.type = messageType;
            this.message = message;
            this.clientData = clientData;
        }
    }
}
