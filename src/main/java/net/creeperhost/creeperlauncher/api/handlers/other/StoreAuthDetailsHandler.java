package net.creeperhost.creeperlauncher.api.handlers.other;

import net.creeperhost.creeperlauncher.Constants;
import net.creeperhost.creeperlauncher.api.data.other.StoreAuthDetailsData;
import net.creeperhost.creeperlauncher.api.handlers.IMessageHandler;

public class StoreAuthDetailsHandler implements IMessageHandler<StoreAuthDetailsData>
{
    @Override
    public void handle(StoreAuthDetailsData data)
    {
        Constants.SECRET = data.mpSecret;
        Constants.KEY = data.mpKey;
        Constants.MT_HASH = data.mtHash;
        Constants.S3_BUCKET = data.s3Bucket;
        Constants.S3_HOST = data.s3Host;
        Constants.S3_KEY = data.s3Key;
        Constants.S3_SECRET = data.s3Secret;
    }
}
