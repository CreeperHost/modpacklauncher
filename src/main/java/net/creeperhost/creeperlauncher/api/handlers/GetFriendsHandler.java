package net.creeperhost.creeperlauncher.api.handlers;

import net.creeperhost.creeperlauncher.CreeperLauncher;
import net.creeperhost.creeperlauncher.Settings;
import net.creeperhost.creeperlauncher.api.data.GetFriendsData;
import net.creeperhost.creeperlauncher.api.data.GetJavasData;
import net.creeperhost.creeperlauncher.chat.Friends;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

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
                            Friends.Friend friend = friendCompletableFuture.get();
                            friends.add(friend);
                        }
                        List<Friends.Friend> requests = new ArrayList<>();
                        for (CompletableFuture<Friends.Friend> friendCompletableFuture : requestComList) {
                            Friends.Friend friend = friendCompletableFuture.get();
                            requests.add(friend);
                        }
                        listFriendResponse.friends = friends;
                        listFriendResponse.requests = requests;
                        Settings.webSocketAPI.sendMessage(new GetFriendsData.Reply(data, listFriendResponse));
                    } catch(Exception ignored) {}
                });
            }
        });
    }
}
