package net.runelite.client.plugins.config;

import com.google.inject.Injector;
import java.io.File;
import java.util.Arrays;
import javax.inject.Inject;
import javax.swing.JLabel;
import net.runelite.client.ui.PluginPanel;

/**
 * Host replacement for RuneLite's plugin-hub browser panel.
 *
 * <p>The desktop panel downloads the hub manifest and lets the user install plugins
 * in-place. The port has no in-app hub installer: hub jars are sideloaded
 * ({@code adb push <jar> /sdcard/Android/data/org.runelite.mobile/files/plugins/}) and
 * dexed on the device by {@code MobilePluginHub} on the next launch. This panel says so
 * and lists the jars it can see, so the tab is useful instead of empty.
 *
 * <p>It replaces the upstream class (which is stripped from the asset dex) because that
 * one is built from {@code IconTextField}, {@code VirtualList} and a live HTTP client —
 * it would work on this port's Swing runtime but has nothing to install with.
 */
class PluginHubPanel extends PluginPanel
{
	private static final String PLUGINS_DIR = System.getProperty("user.home", ".") + File.separator + "plugins";

	@Inject
	PluginHubPanel(Injector injector)
	{
		super();
		javax.swing.JPanel wrapped = getWrappedPanel();
		wrapped.setLayout(new java.awt.FlowLayout(java.awt.FlowLayout.CENTER, 0, 0));
		wrapped.setOpaque(false);

		addLine("Plugin hub", true);
		addLine("Plugins from the hub are installed by sideloading, not from here.", false);
		addLine("adb push <jar> /sdcard/Android/data/org.runelite.mobile/files/plugins/", false);
		addLine("then relaunch the app: the jar is dexed on the device.", false);
		addLine(" ", false);
		addLine("Jars found in " + PLUGINS_DIR, true);
		String[] jars = new File(PLUGINS_DIR).list((dir, name) -> name.endsWith(".jar"));
		if (jars == null || jars.length == 0)
		{
			addLine("(none)", false);
		}
		else
		{
			Arrays.sort(jars);
			for (String jar : jars)
			{
				addLine(jar, false);
			}
		}
	}

	private void addLine(String text, boolean bold)
	{
		JLabel label = new JLabel(text);
		java.awt.Font font = javax.swing.UIManager.getFont("Label.font");
		if (font != null)
		{
			label.setFont(bold ? font.deriveFont(java.awt.Font.BOLD) : font);
		}
		java.awt.Color foreground = javax.swing.UIManager.getColor("Label.foreground");
		if (foreground != null)
		{
			label.setForeground(foreground);
		}
		// One line per row: a FlowLayout with a zero hgap forces every label onto its
		// own row without needing a text-metrics-aware layout pass.
		label.setPreferredSize(new java.awt.Dimension(PluginPanel.PANEL_WIDTH, label.getPreferredSize().height));
		getWrappedPanel().add(label);
	}
}
