package net.creeperhost.creeperlauncher.api.handlers;

public class ModFile {
    private String name;
    private String version;
    private long size;
    private String sha1;
    private boolean expected;
    private boolean exists;
    private transient int hashCode;
    public ModFile(String name, String version, long size, String sha1) {
        this.name = name;
        this.version = version;
        this.size = size;
        this.sha1 = sha1;
        this.hashCode = this.name.toLowerCase().hashCode();
    }

    public ModFile setExpected(boolean expected)  {
        this.expected = expected;
        return this;
    }

    public ModFile setExists(boolean exists) {
        this.exists = exists;
        return this;
    }

    public String getName() {
        return name;
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public boolean equals(Object o) {
        return o != null && o.getClass() == this.getClass() && o.hashCode() == this.hashCode();
    }
}
