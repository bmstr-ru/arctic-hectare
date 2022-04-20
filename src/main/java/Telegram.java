import okhttp3.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class Telegram {

    private final Config.Telegram cfg;
    private final OkHttpClient httpClient;

    public Telegram() {
        this.cfg = Config.get().telegram;
        this.httpClient = new OkHttpClient();
    }

    public void notify(File file) throws IOException {
        sendPhoto(file, cfg.notificationChatId);
    }

    public void debug(File file) throws IOException {
        sendPhoto(file, cfg.debugChatId);
    }

    private void sendPhoto(File file, Long chatId) throws IOException {
        HttpUrl.Builder httpBuilder = HttpUrl.parse("https://api.telegram.org/bot" + cfg.token + "/sendPhoto").newBuilder();
        httpBuilder.addQueryParameter("chat_id", cfg.debugChatId.toString());
        httpBuilder.addQueryParameter("caption", file.getName());

        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("photo", file.getName(), RequestBody.create(Files.readAllBytes(file.toPath())))
                .build();
        Request request = new Request.Builder()
                .url(httpBuilder.build())
                .post(requestBody)
                .build();
        httpClient.newCall(request)
                .execute().close();
    }

    public void debug(String message) throws IOException {
        HttpUrl.Builder httpBuilder = HttpUrl.parse("https://api.telegram.org/bot" + cfg.token + "/sendMessage").newBuilder();
        httpBuilder.addQueryParameter("chat_id", cfg.debugChatId.toString());
        httpBuilder.addQueryParameter("text", message);

        httpClient.newCall(new Request.Builder()
                        .url(httpBuilder.build())
                        .build())
                .execute().close();
    }
}
