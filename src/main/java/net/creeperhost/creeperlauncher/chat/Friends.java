package net.creeperhost.creeperlauncher.chat;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.annotations.SerializedName;
import net.creeperhost.creeperlauncher.util.GsonUtils;
import net.creeperhost.creeperlauncher.util.MiscUtils;
import net.creeperhost.creeperlauncher.util.WebUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class Friends {

    private static final Logger LOGGER = LogManager.getLogger();

    public class Friend {
        public int id;
        public String name;
        public boolean accepted;
        public String hash;
        public UserProfile profile;
        public boolean online;
        public String currentPackID;
        public String currentPack;
    }
    public class ListFriendResponse {
        public String status;
        public List<Friend> friends;
        public List<Friend> requests;
    }
    public class UserProfile {
        public class Hash{
            @SerializedName(value="long")
            public String longS;
            public String medium;
            @SerializedName(value="short")
            public String shortS;
        }
        public class Chat {
            public Hash hash;
            public boolean online;
        }
        public Hash hash;
        public Chat chat;
        public String friendCode;
        public String display;
        public boolean premium;
    }
    private static HashMap<String, String> seenModpacks = new HashMap<>();

    public static CompletableFuture<ListFriendResponse> getFriends(String target){
        CompletableFuture<ListFriendResponse> response = new CompletableFuture<>();
        CompletableFuture.runAsync(() -> {
            String resp = WebUtils.postWebResponse("https://api.creeper.host/minetogether/listfriend", "{\"hash\": \"" + target + "\"}", "application/json");
            JsonElement respEl = new JsonParser().parse(resp);
            ListFriendResponse listFriendResponse = GsonUtils.GSON.fromJson(respEl, ListFriendResponse.class);
            response.complete(listFriendResponse);
        });
        return response;
    }

    public static CompletableFuture<Boolean> addFriend(String hash, String code, String display){
        CompletableFuture<Boolean> response = new CompletableFuture<>();
        CompletableFuture.runAsync(() -> {
            String resp = WebUtils.postWebResponse("https://api.creeper.host/minetogether/requestfriend", "{\"hash\": \"" + hash + "\", \"target\": \"" + code + "\", \"display\": \"" + display + "\"}", "application/json");
            JsonElement respEl = new JsonParser().parse(resp);
            response.complete(respEl.getAsJsonObject().get("status").getAsString().equalsIgnoreCase("success"));
        });
        return response;
    }

    public static CompletableFuture<UserProfile> getProfile(String hash){
        CompletableFuture<UserProfile> response = new CompletableFuture<>();
        CompletableFuture.runAsync(() -> {
            String resp = WebUtils.postWebResponse("https://api.creeper.host/minetogether/profile", "{\"target\": \"" + hash + "\"}", "application/json");
            if(resp.equals("error")) {
                LOGGER.error("Error getting profile for hash: {}", hash);
                response.completeExceptionally(new RuntimeException(resp));
            } else {
                JsonElement respEl = new JsonParser().parse(resp);
                UserProfile userProfile = GsonUtils.GSON.fromJson(respEl.getAsJsonObject().getAsJsonObject("profileData").getAsJsonObject(hash.toUpperCase()), UserProfile.class);
                response.complete(userProfile);
            }
        });
        return response;
    }

    public static CompletableFuture<Friend> getFriendWithProfile(Friend friend){
        CompletableFuture<Friend> response = new CompletableFuture<>();
        getProfile(friend.hash).whenComplete((userProfile, throwable) -> {
            friend.profile = userProfile;
            response.complete(friend);
        });
        return response;
    }

    public static CompletableFuture<String> getTwitchPackFromID(String id){
        CompletableFuture<String> response = new CompletableFuture<>();
        CompletableFuture.runAsync(() -> {
            String resp = WebUtils.getWebResponse("https://creeperhost.net/json/modpacks/twitch/" + id);
            JsonElement respEl = new JsonParser().parse(resp);
            if(respEl.getAsJsonObject().has("name")){
                response.complete(respEl.getAsJsonObject().get("name").getAsString());
            } else {
                response.complete(null);
            }
        });
        return response;
    }

    public static CompletableFuture<String> getCHModpackFromID(String id){
        CompletableFuture<String> response = new CompletableFuture<>();
        CompletableFuture.runAsync(() -> {
            String resp = WebUtils.getWebResponse("https://creeperhost.net/json/modpacks/modpacksch/" + id);
            JsonElement respEl = new JsonParser().parse(resp);
            if(respEl.getAsJsonObject().has("name")){
                response.complete(respEl.getAsJsonObject().get("name").getAsString());
            } else {
                response.complete(null);
            }
        });
        return response;
    }

    public static CompletableFuture<Friend> getFriendWithWhoisAndProfile(Friend friend){
        CompletableFuture<Friend> response = new CompletableFuture<>();
        getProfile(friend.hash).whenComplete((userProfile, throwable) -> {
            friend.profile = userProfile;
            if(Handler.isConnected()){
                Handler.INSTANCE.doWhoisWithFuture(friend.profile.chat.hash.medium).whenComplete((whoisEvent, throwable1) -> {
                    friend.online = whoisEvent.isExists();
                    String realName = whoisEvent.getRealname();
                    JsonObject respEl;
                    try {
                        respEl = new JsonParser().parse(realName).getAsJsonObject();
                    } catch(Exception ignored){
                        response.complete(friend);
                        return;
                    }
                    friend.currentPack = "";
                    if(respEl.has("b")){
                        friend.currentPack = respEl.get("b").getAsString();
                    } else if(respEl.has("p")) {
                        friend.currentPack = respEl.get("p").getAsString();
                    }
                    if(friend.currentPack.length() == 0){
                        response.complete(friend);
                        return;
                    }
                    if(seenModpacks.containsKey(friend.currentPack)){
                        friend.currentPackID = friend.currentPack;
                        friend.currentPack = seenModpacks.get(friend.currentPack);
                    } else {
                        if(MiscUtils.isInt(friend.currentPack)){
                            getTwitchPackFromID(friend.currentPack).whenComplete((s, throwable2) -> {
                                if(s != null){
                                    seenModpacks.put(friend.currentPack, s);
                                    friend.currentPack = s;
                                    response.complete(friend);
                                }
                                response.complete(friend);
                            });
                        } else {
                            String fixedID = friend.currentPack.replaceAll("/\\\\u003/", "=");
                            getCHModpackFromID(fixedID).whenComplete((s, throwable2) -> {
                                if(s != null){
                                    seenModpacks.put(friend.currentPack, s);
                                    friend.currentPack = s;
                                    friend.currentPackID = fixedID;
                                }
                                response.complete(friend);
                            });
                        }
                    }
                });
            } else {
                response.complete(friend);
            }
        });
        return response;
    }

}
