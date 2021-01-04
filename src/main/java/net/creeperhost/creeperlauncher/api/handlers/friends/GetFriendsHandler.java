package net.creeperhost.creeperlauncher.api.handlers.friends;

import net.creeperhost.creeperlauncher.CreeperLogger;
import net.creeperhost.creeperlauncher.Settings;
import net.creeperhost.creeperlauncher.api.data.friends.GetFriendsData;
import net.creeperhost.creeperlauncher.api.handlers.IMessageHandler;
import net.creeperhost.creeperlauncher.chat.Friends;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class GetFriendsHandler implements IMessageHandler<GetFriendsData> {
    @Override
    public void handle(GetFriendsData data) {
        Friends.getFriends(data.hash).whenComplete((listFriendResponse, throwable) -> {
            if(listFriendResponse != null){
                List<CompletableFuture<Friends.Friend>> friendComList = new ArrayList<>();
                for(Friends.Friend f : listFriendResponse.friends){
                    friendComList.add(Friends.getFriendWithWhoisAndProfile(f));
                }
                List<CompletableFuture<Friends.Friend>> requestComList = new ArrayList<>();
                for(Friends.Friend f : listFriendResponse.requests){
                    requestComList.add(Friends.getFriendWithWhoisAndProfile(f));
                }
                CompletableFuture.allOf(friendComList.toArray(new CompletableFuture[0])).whenComplete((unused, throwable1) -> {
                    try {
                        List<Friends.Friend> friends = new ArrayList<>();
                        for (CompletableFuture<Friends.Friend> friendCompletableFuture : friendComList) {
                            if(!friendCompletableFuture.isCompletedExceptionally()){
                                Friends.Friend friend = friendCompletableFuture.get();
                                friends.add(friend);
                            }
                        }
                        List<Friends.Friend> requests = new ArrayList<>();
                        for (CompletableFuture<Friends.Friend> friendCompletableFuture : requestComList) {
                            if(!friendCompletableFuture.isCompletedExceptionally()){
                                Friends.Friend friend = friendCompletableFuture.get();
                                requests.add(friend);
                            }
                        }
                        listFriendResponse.friends = friends;
                        listFriendResponse.requests = requests;
                    } catch(Exception ignored) {
                        CreeperLogger.INSTANCE.error("Error sorting profiles", ignored);
                        ignored.printStackTrace();
                    }
                    Settings.webSocketAPI.sendMessage(new GetFriendsData.Reply(data, listFriendResponse));
                });
            } else {
                Settings.webSocketAPI.sendMessage(new GetFriendsData.Reply(data, null));
            }
        });
    }
}
