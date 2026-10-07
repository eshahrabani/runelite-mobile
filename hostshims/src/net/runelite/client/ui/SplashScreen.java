package net.runelite.client.ui;

/**
 * Host replacement for RuneLite's {@code net.runelite.client.ui.SplashScreen}.
 *
 * <p>The upstream class is a Swing window shown on startup. There is no windowing here,
 * so every entry point is a static no-op; they exist purely so the retained client code
 * ({@code RuneLite}, {@code Updater}, {@code PluginManager}, …) links.
 */
public class SplashScreen
{
	public static void init()
	{
	}

	public static boolean isOpen()
	{
		return false;
	}

	public static void stage(double overallProgress, String action, String subAction)
	{
	}

	public static void stage(double overallProgress, double progress, String action,
		String subAction, int width, int height, boolean b)
	{
	}

	public static void stop()
	{
	}
}
