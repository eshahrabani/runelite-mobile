package net.runelite.client.ui;

import com.google.inject.Injector;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.TrayIcon;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import javax.inject.Inject;
import org.runelite.mobile.bridge.AWTBridge;

/**
 * Host replacement for RuneLite's {@code net.runelite.client.ui.ClientUI}.
 *
 * <p>The upstream class owns the desktop window ({@code ContainableFrame}), the Swing
 * sidebar and the system tray. None of that exists on Android, so this shim answers the
 * queries the retained client classes (notably {@code Hooks} and {@code OverlayRenderer})
 * make — surface size, cursor, insets, graphics configuration — and no-ops the rest.
 *
 * <p>The constructor takes an {@link Injector} because Guice constructs this class and
 * the upstream parameters ({@code RuneLiteConfig}, {@code MouseManager}, {@code Client},
 * …) are asset-dex types the app classloader cannot resolve.
 */
public class ClientUI extends javax.swing.JFrame
{
	public static final BufferedImage ICON_128 =
		new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
	public static final BufferedImage ICON_16 =
		new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);

	private static final int DEFAULT_WIDTH = 765;
	private static final int DEFAULT_HEIGHT = 503;

	private final Injector injector;
	private GraphicsConfiguration graphicsConfiguration;

	@Inject
	public ClientUI(Injector injector)
	{
		super();
		this.injector = injector;
	}

	public void init()
	{
	}

	public void show()
	{
	}

	@Override
	public void paint(Graphics graphics)
	{
	}

	public void paintOverlays(Graphics2D graphics)
	{
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

	/**
	 * Minimal, data-only {@code GraphicsConfiguration}. The retained client code only
	 * reads {@code getDefaultTransform()} from it; everything else returns a sane
	 * non-null default so no caller sees {@code null}.
	 */
	private static final class MobileGraphicsConfiguration extends GraphicsConfiguration
	{
		MobileGraphicsConfiguration()
		{
			super();
		}

		@Override
		public GraphicsDevice getDevice()
		{
			return null;
		}

		public ColorModel getColorModel()
		{
			return null;
		}

		public ColorModel getColorModel(int transparency)
		{
			return null;
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
