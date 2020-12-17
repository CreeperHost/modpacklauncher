package net.creeperhost.creeperlauncher.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;

public class GsonUtils {

    public static Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping()
            .registerTypeAdapter(Artifact.class, new Artifact.Adapter())
            .registerTypeAdapter(Path.class, new PathTypeAdapter())
            .create();

    private static final class PathTypeAdapter extends TypeAdapter<Path> {

        @Override
        public void write(JsonWriter out, Path value) throws IOException {
            if (value == null) {
                out.nullValue();
                return;
            }
            if (value.getFileSystem() != FileSystems.getDefault()) {
                throw new RuntimeException("Only default FileSystem can be serialized.");
            }
            out.value(value.toAbsolutePath().toString());
        }

        @Override
        public Path read(JsonReader in) throws IOException {
            if (in.peek() == JsonToken.NULL) {
                in.nextNull();
                return null;
            }
            return Paths.get(in.nextString());
        }
    }

}
