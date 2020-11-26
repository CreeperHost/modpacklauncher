package net.creeperhost.creeperlauncher.api.handlers.friends;

import net.creeperhost.creeperlauncher.Settings;
import net.creeperhost.creeperlauncher.api.data.friends.AddFriendData;
import net.creeperhost.creeperlauncher.api.handlers.IMessageHandler;
import net.creeperhost.creeperlauncher.chat.Friends;
import net.creeperhost.creeperlauncher.chat.Handler;

public class AddFriendHandler implements IMessageHandler<AddFriendData> {
    @Override
    public void handle(AddFriendData data) {
        Friends.getProfile(data.userHash).whenComplete((userProfile, throwable) -> {
            if(userProfile != null){
                String ourFriendCode = userProfile.friendCode;
                String ourName = userProfile.display;
                Friends.getProfile(data.targetHash).whenComplete((targetProfile, throwable1) -> {
                    if(targetProfile != null){
                        if(Handler.isConnected()){
                            Handler.INSTANCE.ctcpRequest(targetProfile.chat.hash.medium, "FRIENDACC " + ourFriendCode + " " + ourName);
                        }
                        Friends.addFriend(data.userHash, targetProfile.friendCode, targetProfile.display);
                        Settings.webSocketAPI.sendMessage(new AddFriendData.Reply(data));
                    }
                });
            }
        });
    }
}
