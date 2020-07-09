package net.creeperhost.creeperlauncher.api.handlers;

import net.creeperhost.creeperlauncher.Constants;
import net.creeperhost.creeperlauncher.api.data.StoreAuthDetailsData;

public class StoreAuthDetailsHandler implements IMessageHandler<StoreAuthDetailsData>
{
    @Override
    public void handle(StoreAuthDetailsData data)
    {
        Constants.SECRET = data.mpSecret;
        Constants.KEY = data.mpKey;
    }
}
