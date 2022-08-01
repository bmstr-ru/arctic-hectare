import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class Config {

    public Telegram telegram;
    public Coordinates coordinates;
    public Gosuslugi gosuslugi;
    public Area area;

    private static Config INSTANCE;
    private static final String CONFIG_FILE = "config.yaml";

    public static Config get() {
        if (INSTANCE == null) {
            try {
                Yaml yaml = new Yaml();
                if (new File(CONFIG_FILE).exists()) {
                    INSTANCE = yaml.loadAs(new String(Files.readAllBytes(Paths.get(CONFIG_FILE))), Config.class);
                } else if (Config.class.getClassLoader().getResource(CONFIG_FILE) != null) {
                    InputStream inputStream = Config.class.getClassLoader().getResourceAsStream(CONFIG_FILE);
                    INSTANCE = yaml.loadAs(inputStream, Config.class);
                } else {
                    throw new RuntimeException("Cannot find config file " + CONFIG_FILE);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return INSTANCE;
    }

    static class Telegram {
        public String token;
        public Long debugChatId;
        public Long notificationChatId;
    }

    public static class Coordinates {
        public String latitude;
        public String longtitude;
    }

    public static class Gosuslugi {
        public String username;
        public String password;
        public List<Question> quest;
    }

    public static class Question {
        public String question;
        public String answer;
    }

    public static class Area {
        public String id;
    }
}
