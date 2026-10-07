package net.runelite.client.ui;

import com.google.inject.Injector;
import javax.inject.Inject;
import java.util.function.BiConsumer;

/**
 * Host replacement for RuneLite's {@code net.runelite.client.ui.ClientToolbar}.
 *
 * <p>On the desktop this class adds an icon to the Swing toolbar and swaps the toolbar's
 * panel. There is no Swing toolbar here: the three public entry points hand the button to
 * {@link #navigationListener}, a hook the Android host installs
 * ({@code org.runelite.mobile.host.PluginPanelRegistry}) so the native side panel can list
 * plugins that registered a panel and route "open" to their config tab (plan C3/D6).
 *
 * <p>The hook is a plain {@code BiConsumer} field rather than a direct reference because
 * this class lives in the RuneLite (asset) dex and the host lives in the app dex: the
 * child loader can see the parent, never the other way round.
 *
 * <p>Guice constructs this class, so the constructor takes a {@code Injector} rather than
 * the upstream {@code ClientUI}.
 */
public class ClientToolbar
{
	/**
	 * Installed by the host; called as {@code (action, button)} with {@code action} one of
	 * {@code "add"}, {@code "remove"}, {@code "open"}. Null until the host starts.
	 */
	public static volatile BiConsumer<String, Object> navigationListener;

	private final Injector injector;

	@Inject
	public ClientToolbar(Injector injector)
	{
		this.injector = injector;
	}

	public void addNavigation(NavigationButton button)
	{
		notify("add", button);
	}

	public void removeNavigation(NavigationButton button)
	{
		notify("remove", button);
	}

	public void openPanel(NavigationButton button)
	{
		notify("open", button);
	}

	private static void notify(String action, Object button)
	{
		BiConsumer<String, Object> listener = navigationListener;
		if (listener != null)
		{
			listener.accept(action, button);
		}
	}
}
