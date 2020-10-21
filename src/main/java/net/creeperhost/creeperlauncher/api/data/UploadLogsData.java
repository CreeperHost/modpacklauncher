package net.creeperhost.creeperlauncher.api.data;

public class UploadLogsData extends BaseData {
    public String frontendLogs;
    public String uiVersion;
    public UploadLogsData()
    {
        type = "uploadLogs";
    }
}
