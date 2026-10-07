package net.runelite.client.ui.components.colorpicker;

import com.google.inject.Injector;
import java.awt.Color;
import java.awt.Component;
import java.awt.Window;
import javax.inject.Inject;

/**
 * Host replacement for {@code net.runelite.client.ui.components.colorpicker.ColorPickerManager}.
 *
 * <p>Upstream creates a floating {@link RuneliteColorPicker} dialog. There is no windowing
 * here, so {@code create} returns a non-null data-only picker that never opens; the native
 * side panel performs colour selection instead (plan D4).
 *
 * <p>Guice injects this class into several retained plugins ({@code GroundItemsPlugin},
 * {@code NpcIndicatorsPlugin}, …), so the constructor takes an {@link Injector} — the
 * upstream parameter ({@code ConfigManager}) is an asset-dex type the app classloader
 * cannot resolve.
 */
public class ColorPickerManager
{
	private final Injector injector;

	@Inject
	public ColorPickerManager(Injector injector)
	{
		this.injector = injector;
	}

	public RuneliteColorPicker create(net.runelite.api.Client client, Color color, String title, boolean allowAlpha)
	{
		return create((Component) null, color, title, allowAlpha);
	}

	public RuneliteColorPicker create(Component parent, Color color, String title, boolean allowAlpha)
	{
		RuneliteColorPicker picker = new RuneliteColorPicker();
		picker.setSelectedColor(color);
		return picker;
	}

	public RuneliteColorPicker create(Window parent, Color color, String title, boolean allowAlpha)
	{
		return create((Component) null, color, title, allowAlpha);
	}
}
