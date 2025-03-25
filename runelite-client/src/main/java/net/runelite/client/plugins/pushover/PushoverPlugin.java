package net.runelite.client.plugins.pushover;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import okhttp3.*;

import javax.inject.Inject;
import java.io.IOException;

@Slf4j
@PluginDescriptor(
	name = "Pushover Notification"
)
public class PushoverPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private PushoverConfig config;
	private long lastNotificationTime = 0;
	private static final OkHttpClient httpClient = new OkHttpClient();

	@Override
	protected void startUp() throws Exception
	{
		log.info("Pushover Notification started!");
	}

	@Override
	protected void shutDown() throws Exception
	{
		log.info("Pushover Notification stopped!");
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged gameStateChanged)
	{
		if (gameStateChanged.getGameState() == GameState.LOGIN_SCREEN)
		{
			if (config.notifyOnLogout()) {
				sendPushoverNotification("Logout Notificatiom", "You have been logged out");
			}
		}
	}

	@Subscribe
	public void onChatMessage(ChatMessage event) {
		ChatMessageType type = event.getType();
		String sender = event.getName();
		String message = event.getMessage();

		// Only notify on private messages or public messages if enabled
		if (config.notifyOnPrivate() && (type == ChatMessageType.PRIVATECHAT || type == ChatMessageType.FRIENDSCHAT)) {
			sendPushoverNotification("Private message from " + sender, message);
		} else if (config.notifyOnPublic() && type == ChatMessageType.PUBLICCHAT) {
			sendPushoverNotification("Public message", sender + ": " + message);
		}
	}

	private void sendPushoverNotification(String title, String message) {
		if (config.apiToken().isEmpty() || config.userKey().isEmpty()) {
			return;
		}

		long currentTime = System.currentTimeMillis();
		if (currentTime - lastNotificationTime < (config.spamPreventSeconds() * 1000)) {
			return; // Prevent spam, only send once every 2 minutes
		}

		lastNotificationTime = currentTime;

		RequestBody body = new FormBody.Builder()
				.add("token", config.apiToken())
				.add("user", config.userKey())
				.add("title", title)
				.add("message", message)
				.add("priority", config.messagePriority())
				.build();

		Request request = new Request.Builder()
				.url("https://api.pushover.net/1/messages.json")
				.post(body)
				.build();

		httpClient.newCall(request).enqueue(new Callback() {
			@Override
			public void onFailure(Call call, IOException e) {
				e.printStackTrace();
			}

			@Override
			public void onResponse(Call call, Response response) throws IOException {
				response.close();
			}
		});
	}

	@Provides
	PushoverConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(PushoverConfig.class);
	}
}
