package net.runelite.client.plugins.goblintrainer;

import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HWND;
import net.runelite.api.Client;

public class WindowUtils {
    public static String getClientHandle(Client client) {
        if (client.getLocalPlayer() == null) {
            System.err.println("Player not logged in, cannot determine window title.");
            return "UnknownHandle";
        }

        String username = client.getLocalPlayer().getName();
        String windowTitle = "RuneLite - " + username;

        HWND runeliteHwnd = User32.INSTANCE.FindWindow(null, windowTitle);
        if (runeliteHwnd == null) {
            System.err.println("RuneLite window not found for user: " + username);
            return "UnknownHandle";
        }

        // First SunAwtCanvas (child of RuneLite)
        HWND firstCanvas = User32.INSTANCE.FindWindowEx(runeliteHwnd, null, "SunAwtCanvas", null);
        if (firstCanvas == null) {
            System.err.println("First SunAwtCanvas (child) not found inside RuneLite.");
            return "UnknownHandle";
        }

        // Second SunAwtCanvas (grandchild - actual Java game client)
        HWND gameClientHwnd = User32.INSTANCE.FindWindowEx(firstCanvas, null, "SunAwtCanvas", null);
        if (gameClientHwnd != null) {
            System.out.println("Found Java applet (grandchild SunAwtCanvas) inside RuneLite for user: " + username);
            return String.valueOf(gameClientHwnd.getPointer());
        }

        System.err.println("Grandchild SunAwtCanvas (Java client) not found inside RuneLite.");
        return "UnknownHandle";
    }

}
