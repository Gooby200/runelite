package net.runelite.client.plugins.pushover;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("Pushover Notifications")
public interface PushoverConfig extends Config {
	@ConfigItem(
			keyName = "apiToken",
			name = "Pushover API Token",
			description = "Your Pushover app token"
	)
	default String apiToken() {
		return "";
	}

	@ConfigItem(
			keyName = "userKey",
			name = "Pushover User Key",
			description = "Your Pushover user key"
	)
	default String userKey() {
		return "";
	}

	@ConfigItem(
			keyName = "notifyOnPublic",
			name = "Notify on Public Chat",
			description = "Send notifications for public chat messages"
	)
	default boolean notifyOnPublic() {
		return false;
	}

	@ConfigItem(
			keyName = "notifyOnPrivate",
			name = "Notify on Private Chat",
			description = "Send notifications for private chat messages"
	)
	default boolean notifyOnPrivate() {
		return false;
	}

	@ConfigItem(
			keyName = "notifyOnLogout",
			name = "Notify on logout",
			description = "Send notifications when you logout"
	)
	default boolean notifyOnLogout() {
		return false;
	}

	@ConfigItem(
			keyName = "spamPreventSeconds",
			name = "Prevent Spam for X Seconds",
			description = "Doesn't send another notification to prevent spam for specified seconds"
	)
	default int spamPreventSeconds() {
		return 120;
	}

	@ConfigItem(
			keyName = "messagePriority",
			name = "Message priority",
			description = "Sets the message priority"
	)
	default String messagePriority() {
		return "0";
	}
}
