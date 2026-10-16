package net.runelite.client.ui;

import com.google.inject.Injector;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.TrayIcon;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.TreeSet;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import net.runelite.client.ui.components.PluginErrorPanel;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.SwingUtil;
import org.runelite.mobile.bridge.AWTBridge;
import org.runelite.mobile.bridge.UiBridge;

/**
 * Host replacement for RuneLite's {@code net.runelite.client.ui.ClientUI}.
 *
 * <p>On the desktop this class owns the whole application frame: the {@code ContainableFrame},
 * the game canvas, the Swing sidebar and the system tray. The port has none of those
 * (the game draws into the Android surface through {@code Callbacks.draw}, and the tray
 * does not exist), so this class keeps exactly two jobs:
 *
 * <ol>
 * <li><b>The queries the retained client code makes</b>: surface size
 *     ({@code getWidth}/{@code getHeight} answer the game frame size, not the frame's own),
 *     cursor, insets, graphics configuration. Those keep {@code Hooks} and
 *     {@code OverlayRenderer} working unchanged.</li>
 * <li><b>The sidebar host</b>: it builds RuneLite's real {@link ClientToolbarPanel} +
 *     {@link JTabbedPane} sidebar, publishes that tree to the app through
 *     {@link UiBridge#setSidebarRoot}, and mirrors upstream's navigation logic
 *     ({@code addNavigation}/{@code removeNavigation}/{@code openPanel}, the selected-tab
 *     history, activation of the selected {@link PluginPanel}). The app paints this tree
 *     into its drawer and feeds touches back in, so the port's drawer <em>is</em> RuneLite's
 *     sidebar — same widgets, same plugin panels, same activation contract.</li>
 * </ol>
 *
 * <p>The constructor takes an {@link Injector} because Guice constructs this class and the
 * upstream parameters ({@code RuneLiteConfig}, {@code MouseManager}, {@code Client}, …) are
 * asset-dex types the app classloader cannot resolve.
 *
 * <p><b>{@code @Singleton} is load-bearing</b>: upstream's {@code ClientUI} carries it too, and
 * without the scope Guice hands {@code ClientToolbar} (and every other injection point) a
 * <em>fresh</em> instance — the sidebar tree built by {@link #init()} would then be empty while
 * the nav buttons landed on an uninitialised twin and threw
 * {@code NullPointerException} on a null {@code JTabbedPane}.
 */
@Singleton
public class ClientUI extends javax.swing.JFrame implements UiBridge.SidebarActivation
{
	public static final BufferedImage ICON_128 = loadIcon(128, "runelite_128.png");
	public static final BufferedImage ICON_16 = loadIcon(16, "runelite_16.png");

	/** Device-debug logging (sidebar composition and geometry). Off in shipped builds. */
	private static final boolean DEBUG_SIDEBAR = false;

	private static final int DEFAULT_WIDTH = 765;
	private static final int DEFAULT_HEIGHT = 503;

	/**
	 * The sidebar's logical width: {@code PluginPanel.PANEL_WIDTH} (242) plus the tab
	 * strip. The app's integer UI scale is derived from this (see
	 * {@code SwingSidebarView}), so it is the one knob that decides how large the
	 * sidebar renders on a given drawer width.
	 */
	public static final int SIDEBAR_LOGICAL_WIDTH = 272;

	private final Injector injector;
	private GraphicsConfiguration graphicsConfiguration;

	/** The live sidebar host; the conformance pass reaches it reflectively. */
	private static volatile ClientUI instance;

	private JPanel root;
	private JTabbedPane sidebar;
	private ClientToolbarPanel toolbarPanel;
	private JButton sidebarNavBtn;

	private final TreeSet<NavigationButton> sidebarEntries = new TreeSet<>(NavigationButton.COMPARATOR);
	private final Deque<HistoryEntry> selectedTabHistory = new ArrayDeque<>();
	private NavigationButton selectedTab;
	private boolean sidebarAvailable = true;
	private BufferedImage sidebarOpenIcon;
	private BufferedImage sidebarCloseIcon;

	@Inject
	public ClientUI(Injector injector)
	{
		super();
		this.injector = injector;
	}

	/**
	 * Builds the sidebar and publishes it as the app's stage root. Must run on the Swing
	 * dispatch thread (the host does that) because it touches Swing state.
	 */
	public void init()
	{
		// The RuneScape fonts are registered by FontManager on first use; wire them into
		// the theme so every widget the sidebar builds measures with the real font.
		javax.swing.UIManager.setDefaultFontSupplier(FontManager::getRunescapeSmallFont);

		sidebarOpenIcon = loadIcon(16, "open_rs.png");
		sidebarCloseIcon = ImageUtil.flipImage(sidebarOpenIcon, true, false);

		root = new JPanel(new BorderLayout());
		root.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		root.setOpaque(true);
		root.setName("RuneLiteSidebar");

		sidebar = new JTabbedPane(JTabbedPane.RIGHT);
		sidebar.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		sidebar.setOpaque(true);
		sidebar.addChangeListener(e -> onTabChanged());
		sidebar.addMouseListener(new TabPopupListener());

		toolbarPanel = new ClientToolbarPanel(false);
		// Upstream anchors the strip of panel-less navigation buttons (and the toggle)
		// to the end of the tab strip through this client property.
		sidebar.putClientProperty("JTabbedPane.trailingComponent", toolbarPanel.createSidebarPanel());

		sidebarNavBtn = toolbarPanel.add(NavigationButton.builder()
			.priority(Integer.MIN_VALUE)
			.icon(sidebarCloseIcon)
			.tooltip("Close sidebar")
			.onClick(UiBridge::closeSidebarRequested)
			.build(), true);

		root.add(sidebar, BorderLayout.CENTER);
		root.setPreferredSize(new Dimension(SIDEBAR_LOGICAL_WIDTH, 600));

		UiBridge.setSidebarRoot(root);
		UiBridge.setSidebarActivation(this);
		instance = this;
		if (DEBUG_SIDEBAR)
		{
			System.err.println("ClientUI sidebar built: tabs=" + sidebar.getTabCount()
				+ " rootPref=" + root.getPreferredSize() + " strip=" + sidebar.getPreferredSize());
		}
	}

	/**
	 * Called by the app when the drawer opens or closes: the selected panel's
	 * {@code Activatable} contract runs here, never on the drawer's own event.
	 */
	@Override
	public void sidebarVisibilityChanged(boolean visible)
	{
		NavigationButton nav = selectedTab;
		if (nav == null)
		{
			if (visible && !sidebarEntries.isEmpty())
			{
				openPanel(sidebarEntries.first(), true);
			}
			return;
		}
		PluginPanel panel = nav.getPanel();
		if (panel == null)
		{
			return;
		}
		if (DEBUG_SIDEBAR && visible)
		{
			dumpSidebar(nav);
		}
		try
		{
			if (visible)
			{
				SwingUtil.activate(panel);
			}
			else
			{
				SwingUtil.deactivate(panel);
			}
		}
		catch (Throwable t)
		{
			installErrorPanel(nav, t);
		}
	}

	// ------------------------------------------------------------ navigation

	/** Adds a plugin's navigation button: a sidebar tab when it has a panel, else a strip button. */
	void addNavigation(NavigationButton navBtn)
	{
		if (navBtn == null)
		{
			return;
		}
		if (navBtn.getPanel() == null)
		{
			toolbarPanel.add(navBtn, true);
			return;
		}
		if (!sidebarEntries.add(navBtn))
		{
			return;
		}
		Component panelComponent = panelComponentOf(navBtn);
		ImageIcon icon = iconOf(navBtn);
		int index = sidebarEntries.headSet(navBtn).size();
		sidebar.insertTab(navBtn.getTooltip(), icon, panelComponent, navBtn.getTooltip(), index);
		if (DEBUG_SIDEBAR)
		{
			System.err.println("ClientUI sidebar tab " + index + ": " + navBtn.getTooltip()
				+ " icon=" + (icon == null ? "none" : icon.getIconWidth() + "x" + icon.getIconHeight())
				+ " panel=" + panelComponent.getClass().getName() + " tabs=" + sidebar.getTabCount());
		}
		if (sidebar.getTabCount() == 1)
		{
			// The desktop sidebar starts with no panel open; keep that behaviour so the
			// drawer can open onto the strip alone.
			sidebar.setSelectedIndex(-1);
		}
	}

	void removeNavigation(NavigationButton navBtn)
	{
		if (navBtn == null)
		{
			return;
		}
		if (navBtn.getPanel() == null)
		{
			toolbarPanel.remove(navBtn);
			sidebarEntries.remove(navBtn);
			return;
		}

		boolean wasSelected = !selectedTabHistory.isEmpty()
			&& navBtn.equals(selectedTabHistory.peekLast().navBtn);
		selectedTabHistory.removeIf(entry -> navBtn.equals(entry.navBtn));
		Component panelComponent = panelComponentOf(navBtn);
		sidebar.remove(panelComponent);

		if (wasSelected)
		{
			HistoryEntry previous = selectedTabHistory.isEmpty()
				? new HistoryEntry(true, null)
				: selectedTabHistory.removeLast();
			openPanel(previous.navBtn, previous.sidebarOpen);
		}
		sidebarEntries.remove(navBtn);
	}

	/**
	 * Selects {@code nav}'s tab (or clears the selection when {@code nav} is null) and
	 * activates/deactivates the panels involved, exactly like the desktop sidebar.
	 */
	void openPanel(NavigationButton nav, boolean selected)
	{
		if (nav != null && !sidebarEntries.contains(nav))
		{
			return;
		}
		int index = nav == null ? -1 : sidebarEntries.headSet(nav).size();
		sidebar.setSelectedIndex(index);
		if (selected)
		{
			activateSelected();
		}
		pushHistory();
	}

	/** Index of the currently selected tab, or -1 (the desktop's "strip only" state). */
	int getCurrentPanelIndex()
	{
		return sidebar == null ? -1 : sidebar.getSelectedIndex();
	}

	/** The panel of the currently selected tab, or null. */
	PluginPanel getCurrentPanel()
	{
		NavigationButton nav = selectedTab;
		return nav == null ? null : nav.getPanel();
	}

	private void onTabChanged()
	{
		activateSelected();
		pushHistory();
		UiBridge.sidebarDirty();
	}

	/** Runs the activate/deactivate pair for the tab that is selected now. */
	private void activateSelected()
	{
		NavigationButton previous = selectedTab;
		NavigationButton current = navAt(sidebar.getSelectedIndex());
		if (previous == current)
		{
			return;
		}
		if (previous != null && previous.getPanel() != null)
		{
			try
			{
				SwingUtil.deactivate(previous.getPanel());
			}
			catch (Throwable t)
			{
				installErrorPanel(previous, t);
			}
		}
		selectedTab = current;
		if (current != null && current.getPanel() != null)
		{
			try
			{
				SwingUtil.activate(current.getPanel());
			}
			catch (Throwable t)
			{
				installErrorPanel(current, t);
			}
		}
		updateToggleIcon();
	}

	/** One-line dump of the sidebar's geometry, for device debugging (logcat/System.err). */
	private void dumpSidebar(NavigationButton nav)
	{
		StringBuilder sb = new StringBuilder("ClientUI sidebar state: tabs=")
			.append(sidebar.getTabCount())
			.append(" nav=").append(nav == null ? "none" : nav.getTooltip())
			.append(" selected=").append(sidebar.getSelectedIndex())
			.append(" root=").append(root.getWidth()).append('x').append(root.getHeight())
			.append(" sidebar=").append(sidebar.getWidth()).append('x').append(sidebar.getHeight())
			.append(" placement=").append(sidebar.getTabPlacement());
		for (int i = 0; i < sidebar.getTabCount(); i++)
		{
			Rectangle bounds = sidebar.getBoundsAt(i);
			javax.swing.Icon icon = sidebar.getIconAt(i);
			sb.append(" [").append(i).append(':').append(sidebar.getTitleAt(i))
				.append(" bounds=").append(bounds)
				.append(" icon=").append(icon == null ? "none" : icon.getIconWidth() + "x" + icon.getIconHeight())
				.append(" comp=").append(sidebar.getComponentAt(i) == null ? "null"
					: sidebar.getComponentAt(i).getWidth() + "x" + sidebar.getComponentAt(i).getHeight())
				.append(']');
		}
		System.err.println(sb);
	}

	private void updateToggleIcon()
	{
		JButton button = sidebarNavBtn;
		if (button == null)
		{
			return;
		}
		boolean open = sidebar.isVisible() && getCurrentPanelIndex() >= 0;
		button.setIcon(new ImageIcon(open ? sidebarCloseIcon : sidebarOpenIcon));
		button.setToolTipText(open ? "Close sidebar" : "Open sidebar");
	}

	private NavigationButton navAt(int index)
	{
		if (index < 0)
		{
			return null;
		}
		int i = 0;
		for (NavigationButton nav : sidebarEntries)
		{
			if (i++ == index)
			{
				return nav;
			}
		}
		return null;
	}

	private void pushHistory()
	{
		HistoryEntry entry = new HistoryEntry(sidebar.isVisible(), selectedTab);
		selectedTabHistory.removeIf(e -> e.navBtn == entry.navBtn);
		selectedTabHistory.addLast(entry);
		while (selectedTabHistory.size() > 10)
		{
			selectedTabHistory.removeFirst();
		}
	}

	// -------------------------------------------------------------- helpers

	/** The panel's component, or an error panel when constructing it throws. */
	private Component panelComponentOf(NavigationButton nav)
	{
		try
		{
			PluginPanel panel = nav.getPanel();
			if (panel == null)
			{
				throw new IllegalStateException("no panel");
			}
			return panel.getWrappedPanel();
		}
		catch (Throwable t)
		{
			System.err.println("panel construction failed: " + nav.getTooltip() + " " + t);
			t.printStackTrace();
			return errorPanel(nav.getTooltip(), t);
		}
	}

	private ImageIcon iconOf(NavigationButton nav)
	{
		try
		{
			BufferedImage icon = nav.getIcon();
			if (icon == null)
			{
				return new ImageIcon();
			}
			return new ImageIcon(ImageUtil.resizeImage(icon, 16, 16));
		}
		catch (Throwable t)
		{
			return new ImageIcon();
		}
	}

	/** Replaces a broken tab's content with a PluginErrorPanel and logs the failure. */
	private void installErrorPanel(NavigationButton nav, Throwable t)
	{
		System.err.println("panel activation failed: " + (nav == null ? "?" : nav.getTooltip()) + " " + t);
		t.printStackTrace();
		if (nav == null || nav.getPanel() == null)
		{
			return;
		}
		final Component original;
		try
		{
			original = nav.getPanel().getWrappedPanel();
		}
		catch (Throwable nested)
		{
			return;
		}
		int index = indexOfComponent(original);
		if (index >= 0)
		{
			sidebar.setComponentAt(index, errorPanel(nav.getTooltip(), t));
		}
	}

	private int indexOfComponent(Component component)
	{
		for (int i = 0; i < sidebar.getTabCount(); i++)
		{
			if (sidebar.getComponentAt(i) == component)
			{
				return i;
			}
		}
		return -1;
	}

	private JPanel errorPanel(String tooltip, Throwable t)
	{
		PluginErrorPanel error = new PluginErrorPanel();
		String description = t == null ? "unknown error" : t.getClass().getSimpleName()
			+ (t.getMessage() == null ? "" : ": " + t.getMessage());
		try
		{
			error.setContent(tooltip == null ? "Panel unavailable" : tooltip, description);
		}
		catch (Throwable ignored)
		{
			// The error panel itself must never be the reason a plugin fails.
		}
		return error;
	}

	private static BufferedImage loadIcon(int size, String resource)
	{
		try
		{
			BufferedImage image = ImageUtil.loadImageResource(ClientUI.class, resource);
			if (image != null)
			{
				return image;
			}
		}
		catch (Throwable t)
		{
			System.err.println("ClientUI: icon " + resource + " unavailable: " + t);
		}
		return new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
	}

	/** One entry of the sidebar's selection history (upstream shape). */
	static final class HistoryEntry
	{
		final boolean sidebarOpen;
		final NavigationButton navBtn;

		HistoryEntry(boolean sidebarOpen, NavigationButton navBtn)
		{
			this.sidebarOpen = sidebarOpen;
			this.navBtn = navBtn;
		}
	}

	/**
	 * Upstream's right-click-on-a-tab popup: a navigation button may carry a
	 * {@code Map<String,Runnable>} of extra actions. On a touch device the app turns a
	 * long press into a BUTTON3 press, which arrives here.
	 */
	private final class TabPopupListener extends MouseAdapter
	{
		@Override
		public void mousePressed(MouseEvent e)
		{
			maybeShow(e);
		}

		@Override
		public void mouseReleased(MouseEvent e)
		{
			maybeShow(e);
		}

		private void maybeShow(MouseEvent e)
		{
			if (!(e.isPopupTrigger() || e.getButton() == MouseEvent.BUTTON3))
			{
				return;
			}
			int index = sidebar.indexAtLocation(e.getX(), e.getY());
			NavigationButton nav = navAt(index);
			if (nav == null || nav.getPopup() == null || nav.getPopup().isEmpty())
			{
				return;
			}
			JPopupMenu menu = new JPopupMenu();
			for (java.util.Map.Entry<String, Runnable> entry : nav.getPopup().entrySet())
			{
				javax.swing.JMenuItem item = new javax.swing.JMenuItem(entry.getKey());
				Runnable action = entry.getValue();
				item.addActionListener(a -> action.run());
				menu.add(item);
			}
			Rectangle bounds = sidebar.getBoundsAt(index);
			int x = bounds == null ? e.getX() : bounds.x;
			int y = bounds == null ? e.getY() : bounds.y + bounds.height;
			menu.show(sidebar, x, y);
		}
	}

	// ---------------------------------------------------- host-facing API

	/** Shows the sidebar; the port's drawer is opened by the app, so this is a no-op. */
	public void show()
	{
	}

	@Override
	public void paint(Graphics graphics)
	{
	}

	public void paintOverlays(Graphics2D graphics)
	{
		// Upstream paints the on-canvas sidebar handle here; the port's drawer has its own
		// chrome, and the game frame must stay exactly what the client rendered.
	}

	@Override
	public int getWidth()
	{
		int width = AWTBridge.activeWidth;
		return width > 0 ? width : DEFAULT_WIDTH;
	}

	@Override
	public int getHeight()
	{
		int height = AWTBridge.activeHeight;
		return height > 0 ? height : DEFAULT_HEIGHT;
	}

	public boolean isFocused()
	{
		return true;
	}

	@Override
	public void requestFocus()
	{
	}

	public void forceFocus()
	{
	}

	public void flashTaskbar()
	{
	}

	public Cursor getCurrentCursor()
	{
		return getDefaultCursor();
	}

	public Cursor getDefaultCursor()
	{
		return Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR);
	}

	public void setCursor(BufferedImage image, String name)
	{
	}

	@Override
	public void setCursor(Cursor cursor)
	{
		super.setCursor(cursor);
	}

	public void resetCursor()
	{
		super.setCursor(getDefaultCursor());
	}

	public Point getCanvasOffset()
	{
		return new Point(0, 0);
	}

	@Override
	public Insets getInsets()
	{
		return new Insets(0, 0, 0, 0);
	}

	public GraphicsConfiguration getGraphicsConfiguration()
	{
		GraphicsConfiguration configuration = graphicsConfiguration;
		if (configuration == null)
		{
			configuration = new MobileGraphicsConfiguration();
			graphicsConfiguration = configuration;
		}
		return configuration;
	}

	public TrayIcon getTrayIcon()
	{
		return null;
	}

	/** The app-side drawer is available or not (hidden on the launcher screen). */
	public void setSidebarAvailable(boolean available)
	{
		this.sidebarAvailable = available;
	}

	public boolean isSidebarAvailable()
	{
		return sidebarAvailable;
	}

	/** The component tree the app paints into its drawer. */
	public JPanel getSidebarRoot()
	{
		return root;
	}

	/**
	 * The live sidebar host, or null before {@link #init()}. The app dex cannot reference
	 * this class at compile time (it lives in the asset dex), so the conformance pass
	 * reaches this accessor reflectively.
	 */
	public static ClientUI instance()
	{
		return instance;
	}

	/**
	 * The navigation buttons the sidebar currently holds, in strip order — the app's
	 * plugin-conformance pass walks them to render each plugin panel once.
	 */
	public java.util.List<Object> navigationButtons()
	{
		return new java.util.ArrayList<Object>(sidebarEntries);
	}

	/**
	 * Minimal {@code GraphicsConfiguration}: the retained client code only reads
	 * {@code getDefaultTransform()} from it; everything else returns a sane non-null
	 * default so no caller sees {@code null}.
	 */
	private static final class MobileGraphicsConfiguration extends GraphicsConfiguration
	{
		@Override
		public GraphicsDevice getDevice()
		{
			return java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
		}

		@Override
		public ColorModel getColorModel()
		{
			return ColorModel.getRGBdefault();
		}

		@Override
		public ColorModel getColorModel(int transparency)
		{
			return ColorModel.getRGBdefault();
		}

		@Override
		public AffineTransform getDefaultTransform()
		{
			return new AffineTransform();
		}

		@Override
		public AffineTransform getNormalizingTransform()
		{
			return new AffineTransform();
		}

		@Override
		public Rectangle getBounds()
		{
			return new Rectangle(0, 0, DEFAULT_WIDTH, DEFAULT_HEIGHT);
		}
	}
}
