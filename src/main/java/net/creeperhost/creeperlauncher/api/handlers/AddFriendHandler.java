package net.creeperhost.creeperlauncher.api.handlers;

import com.google.gson.reflect.TypeToken;
import net.creeperhost.creeperlauncher.Settings;
import net.creeperhost.creeperlauncher.api.data.AddFriendData;
import net.creeperhost.creeperlauncher.api.data.BlockFriendData;
import net.creeperhost.creeperlauncher.chat.Friends;
import net.creeperhost.creeperlauncher.chat.Handler;
import net.creeperhost.creeperlauncher.util.GsonUtils;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

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
                    }
                });
            }
        });
    }
}
