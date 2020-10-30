package net.creeperhost.creeperlauncher.api.data.other;

import net.creeperhost.creeperlauncher.api.data.BaseData;

public class ClientLaunchData extends BaseData {
    public static class Reply extends BaseData {
        String messageType;
        String message;
        Object clientData;
        public Reply(String messageType, String message){
             this(messageType, message, null);
        }
        public Reply(String messageType, Object clientData){
            this(messageType, null, clientData);
        }
        public Reply(String messageType, String message, Object clientData)
        {
            this.messageType = messageType;
            this.message = message;
            this.clientData = clientData;
            this.type = "clientLaunchData";
        }
    }
}
