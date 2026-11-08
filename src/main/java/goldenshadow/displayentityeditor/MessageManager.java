package goldenshadow.displayentityeditor;

import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MessageManager {
    private final Map<String, Object> messages;

    public MessageManager() throws IOException {
        try (InputStream stream = MessageManager.class.getResourceAsStream("/messages.yml")) {
            if (stream == null) {
                throw new IOException("JAR 내부의 messages.yml을 찾지 못했습니다.");
            }
            Map<String, Object> loaded = new Yaml().load(stream);
            if (loaded == null || loaded.isEmpty()) {
                throw new IOException("내장 메시지가 비어 있습니다.");
            }
            messages = Map.copyOf(loaded);
        }
    }

    public String getString(String key) {
        Object value = messages.get(key);
        return value instanceof String text ? text : "";
    }

    public List<String> getList(String key) {
        List<String> result = new ArrayList<>();
        if (messages.get(key) instanceof List<?> list) {
            for (Object value : list) {
                if (value instanceof String text) result.add(text);
            }
        }
        return result;
    }

    public boolean isMessageMapComplete() {
        return true;
    }
}