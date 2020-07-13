package net.creeperhost.creeperlauncher.api.data;

import java.util.HashMap;

public class GetJavasData extends BaseData {
    public static class Reply {
        HashMap<String, String> javas;
        public Reply(HashMap<String, String> javas) {
            this.javas = javas;
        }
    }
}
