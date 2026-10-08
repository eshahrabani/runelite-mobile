package org.runelite.mobile;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.runelite.mobile.host.PluginConformance;
import org.runelite.mobile.host.PluginPanelRegistry;
import org.runelite.mobile.host.RuneLiteHost;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Native side panel: the mobile replacement for RuneLite's Swing client shell.
 *
 * <p>A collapsible drawer on the right edge with three tabs. <b>Plugins</b> lists every
 * plugin the runtime loaded and toggles it through {@code PluginManager}; <b>Config</b>
 * generates a form from {@code ConfigManager.getConfigDescriptor} and writes through the
 * config proxy (so {@code ConfigChanged} fires and plugins react); <b>Host</b> reports the
 * runtime state. Plugin Swing panels are never rendered -- a plugin that registered a
 * navigation button is listed as "panel not available on mobile" and opens its config
 * instead (RuneLite's {@code ClientToolbar} shim records those buttons in
 * {@link PluginPanelRegistry}).
 *
 * <p>Everything RuneLite-specific goes through reflection into the child class loader
 * ({@link RuneLiteHost}), because the app dex cannot see the asset dex. The drawer
 * consumes touches inside its own bounds only; the game surface underneath keeps its size,
 * so game input elsewhere is unaffected.
 */
public final class SidePanel implements PluginPanelRegistry.Listener {

    private static final String TAG = "RuneLiteMobile";
    /** Same preferences file the launcher uses for its own UI state. */
    private static final String PREFS_NAME = "RuneLiteMobilePrefs";
    private static final String PREFS_OPEN = "sidePanelOpen";
    private static final String PREFS_TAB = "sidePanelTab";
    private static final int TAB_PLUGINS = 0;
    private static final int TAB_CONFIG = 1;
    private static final int TAB_HOST = 2;

    private final Activity activity;
    private final FrameLayout root;
    private final int drawerWidthPx;
    private final float density;
    private final SharedPreferences prefs;

    private final FrameLayout container;
    private final LinearLayout drawer;
    private final TextView handle;
    private final Button[] tabButtons = new Button[3];
    private final FrameLayout content;
    private final LinearLayout pluginsList;
    private final LinearLayout configList;
    private final LinearLayout hostList;
    private final EditText search;
    private final TextView hostStatus;

    private int selectedTab;
    private boolean open;
    private Object configPlugin;      // plugin whose config is currently shown
    private String pendingNavigation; // panel-less plugin the user tapped
    private String hostStatusText = "";

    public SidePanel(Activity activity, FrameLayout rootLayout, int surfaceWidthPx) {
        this.activity = activity;
        this.root = rootLayout;
        this.density = activity.getResources().getDisplayMetrics().density;
        this.prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.drawerWidthPx = Math.min((int) (0.42f * surfaceWidthPx), (int) (520 * density));

        container = new FrameLayout(activity);
        FrameLayout.LayoutParams containerParams = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        container.setLayoutParams(containerParams);
        container.setClipChildren(false);

        // Handle strip (always visible, collapsed or not).
        handle = new TextView(activity);
        handle.setText("☰");
        handle.setTextSize(20f);
        handle.setTextColor(0xFFE0E0E0);
        handle.setGravity(Gravity.CENTER);
        handle.setBackgroundColor(0xE6141414);
        FrameLayout.LayoutParams handleParams = new FrameLayout.LayoutParams(
            (int) (32 * density), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.END);
        handle.setLayoutParams(handleParams);
        handle.setOnClickListener(v -> toggle());
        container.addView(handle);

        // Drawer.
        drawer = new LinearLayout(activity);
        drawer.setOrientation(LinearLayout.VERTICAL);
        drawer.setBackgroundColor(0xE6141414);
        FrameLayout.LayoutParams drawerParams = new FrameLayout.LayoutParams(
            drawerWidthPx, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.END);
        drawerParams.rightMargin = (int) (32 * density);
        drawer.setLayoutParams(drawerParams);
        drawer.setPadding((int) (10 * density), (int) (12 * density), (int) (10 * density), (int) (12 * density));

        LinearLayout tabRow = new LinearLayout(activity);
        tabRow.setOrientation(LinearLayout.HORIZONTAL);
        String[] titles = {"Plugins", "Config", "Host"};
        for (int i = 0; i < titles.length; i++) {
            final int index = i;
            Button button = new Button(activity);
            button.setText(titles[i]);
            button.setTextSize(12f);
            button.setTextColor(0xFFFFFFFF);
            button.setAllCaps(false);
            button.setPadding(0, 0, 0, 0);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            button.setLayoutParams(p);
            button.setOnClickListener(v -> selectTab(index));
            tabButtons[i] = button;
            tabRow.addView(button);
        }
        drawer.addView(tabRow);

        hostStatus = new TextView(activity);
        hostStatus.setTextSize(11f);
        hostStatus.setTextColor(0xFFB0B0B0);
        hostStatus.setPadding(0, (int) (4 * density), 0, (int) (4 * density));
        drawer.addView(hostStatus);

        search = new EditText(activity);
        search.setHint("search plugins");
        search.setTextSize(12f);
        search.setSingleLine(true);
        search.setTextColor(0xFFFFFFFF);
        search.setHintTextColor(0xFF808080);
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            public void afterTextChanged(Editable s) {
                if (selectedTab == TAB_PLUGINS) {
                    buildPluginRows(s.toString());
                }
            }
        });
        drawer.addView(search);

        content = new FrameLayout(activity);
        LinearLayout.LayoutParams contentParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        content.setLayoutParams(contentParams);
        drawer.addView(content);

        pluginsList = new LinearLayout(activity);
        pluginsList.setOrientation(LinearLayout.VERTICAL);
        configList = new LinearLayout(activity);
        configList.setOrientation(LinearLayout.VERTICAL);
        hostList = new LinearLayout(activity);
        hostList.setOrientation(LinearLayout.VERTICAL);

        ScrollView pluginsScroll = wrap(pluginsList);
        ScrollView configScroll = wrap(configList);
        ScrollView hostScroll = wrap(hostList);
        content.addView(pluginsScroll);
        content.addView(configScroll);
        content.addView(hostScroll);
        pluginsScroll.setTag("plugins");
        configScroll.setTag("config");
        hostScroll.setTag("host");

        // The drawer and the handle swallow touches so a tap never walks the character.
        View.OnTouchListener swallow = (v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_OUTSIDE) {
                return false;
            }
            return true;
        };
        drawer.setOnTouchListener(swallow);
        handle.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                toggle();
            }
            return true;
        });

        container.addView(drawer);
        root.addView(container);

        PluginPanelRegistry.setListener(this);
        selectedTab = prefs.getInt(PREFS_TAB, TAB_PLUGINS);
        open = prefs.getBoolean(PREFS_OPEN, false);
        applyState();
        selectTab(selectedTab);
        refreshPlugins();
    }

    private ScrollView wrap(View child) {
        ScrollView scroll = new ScrollView(activity);
        scroll.addView(child, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return scroll;
    }

    /** The "☰" toggle the host adds next to its settings button. */
    public View createToggleButton() {
        Button button = new Button(activity);
        button.setText("☰");
        button.setTextColor(0xFFFFFFFF);
        button.setTextSize(16f);
        GradientDrawable background = new GradientDrawable();
        background.setColor(0xBB2E2E3E);
        background.setCornerRadius(15 * density);
        background.setStroke(2, 0xFF4F4F5F);
        button.setBackground(background);
        button.setPadding((int) (28 * density), (int) (12 * density),
            (int) (28 * density), (int) (12 * density));
        return button;
    }

    public void toggle() {
        open = !open;
        applyState();
        prefs.edit().putBoolean(PREFS_OPEN, open).apply();
    }

    public void open() {
        if (!open) {
            toggle();
        }
    }

    public void close() {
        if (open) {
            toggle();
        }
    }

    public boolean isOpen() {
        return open;
    }

    public void setHostStatus(String text) {
        hostStatusText = text == null ? "" : text;
        hostStatus.setText(hostStatusText);
        if (selectedTab == TAB_HOST) {
            buildHostTab();
        }
    }

    private void applyState() {
        drawer.setVisibility(open ? View.VISIBLE : View.GONE);
        if (open) {
            refreshPlugins();
        }
    }

    private void selectTab(int index) {
        selectedTab = index;
        prefs.edit().putInt(PREFS_TAB, index).apply();
        for (int i = 0; i < tabButtons.length; i++) {
            tabButtons[i].setTextColor(i == index ? 0xFFFFC83D : 0xFFFFFFFF);
        }
        search.setVisibility(index == TAB_PLUGINS ? View.VISIBLE : View.GONE);
        for (int i = 0; i < content.getChildCount(); i++) {
            content.getChildAt(i).setVisibility(i == index ? View.VISIBLE : View.GONE);
        }
        if (index == TAB_PLUGINS) {
            buildPluginRows(search.getText().toString());
        } else if (index == TAB_CONFIG) {
            buildConfigPluginList();
        } else {
            buildHostTab();
        }
    }

    // ------------------------------------------------------------------ plugins tab
    @Override
    public void navigationAdded(String name) {
        pendingNavigation = name;
        if (selectedTab == TAB_PLUGINS) {
            buildPluginRows(search.getText().toString());
        }
    }

    @Override
    public void navigationRemoved(String name) {
        if (selectedTab == TAB_PLUGINS) {
            buildPluginRows(search.getText().toString());
        }
    }

    @Override
    public void panelOpened(String name) {
        // A plugin asked for its Swing panel: the port cannot show it, so its config is
        // the closest useful thing.
        showConfigFor(name);
    }

    public void refreshPlugins() {
        if (!open) {
            return;
        }
        if (selectedTab == TAB_PLUGINS) {
            buildPluginRows(search.getText().toString());
        }
        setHostStatus(RuneLiteHost.status());
    }

    private void buildPluginRows(String filter) {
        pluginsList.removeAllViews();
        List<Object> plugins = RuneLiteHost.plugins();
        if (plugins.isEmpty()) {
            pluginsList.addView(label(RuneLiteHost.isRunning()
                ? "no plugins loaded"
                : "RuneLite runtime not running: " + RuneLiteHost.status(), 12f, 0xFFB0B0B0));
            return;
        }
        String needle = filter == null ? "" : filter.trim().toLowerCase();
        List<Object> sorted = new ArrayList<>(plugins);
        sorted.sort(Comparator.comparing(RuneLiteHost::pluginName, String.CASE_INSENSITIVE_ORDER));
        int shown = 0;
        for (Object plugin : sorted) {
            String name = RuneLiteHost.pluginName(plugin);
            String description = pluginDescription(plugin);
            if (!needle.isEmpty()
                && !name.toLowerCase().contains(needle)
                && !description.toLowerCase().contains(needle)) {
                continue;
            }
            shown++;
            pluginsList.addView(pluginRow(plugin, name, description));
        }
        if (shown == 0) {
            pluginsList.addView(label("no plugin matches \"" + filter + "\"", 12f, 0xFFB0B0B0));
        }
        if (!PluginPanelRegistry.names().isEmpty()) {
            pluginsList.addView(label("registered panels (Swing, not available on mobile):", 11f, 0xFF808080));
            for (String navigation : PluginPanelRegistry.names()) {
                TextView row = label("• " + navigation + " — tap to open its config", 12f, 0xFF9E9E9E);
                row.setPadding(0, (int) (6 * density), 0, (int) (6 * density));
                row.setOnClickListener(v -> showConfigFor(navigation));
                pluginsList.addView(row);
            }
        }
    }

    private View pluginRow(Object plugin, String name, String description) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, (int) (8 * density), 0, (int) (8 * density));

        LinearLayout text = new LinearLayout(activity);
        text.setOrientation(LinearLayout.VERTICAL);
        text.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        text.addView(label(name, 13f, 0xFFFFFFFF));
        if (!description.isEmpty()) {
            text.addView(label(description, 11f, 0xFF9E9E9E));
        }
        row.addView(text);

        Switch toggle = new Switch(activity);
        toggle.setChecked(RuneLiteHost.isPluginEnabled(plugin));
        toggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!RuneLiteHost.setPluginEnabled(plugin, isChecked)) {
                toast("could not " + (isChecked ? "enable " : "disable ") + name);
                buttonView.setChecked(!isChecked);
                return;
            }
            toast(name + (isChecked ? " enabled" : " disabled"));
        });
        row.addView(toggle);
        return row;
    }

    private String pluginDescription(Object plugin) {
        try {
            Class<?> descriptorClass = RuneLiteHost.clientLoader()
                .loadClass("net.runelite.client.plugins.PluginDescriptor");
            Annotation descriptor = plugin.getClass().getAnnotation((Class) descriptorClass);
            if (descriptor != null) {
                Object value = descriptor.getClass().getMethod("description").invoke(descriptor);
                return value instanceof String ? (String) value : "";
            }
        } catch (Throwable ignored) {
            // descriptor unavailable: no description line
        }
        return "";
    }

    // ------------------------------------------------------------------- config tab
    private void buildConfigPluginList() {
        configList.removeAllViews();
        configPlugin = null;
        List<Object> plugins = RuneLiteHost.plugins();
        if (plugins.isEmpty()) {
            configList.addView(label("no plugins loaded", 12f, 0xFFB0B0B0));
            return;
        }
        List<Object> sorted = new ArrayList<>(plugins);
        sorted.sort(Comparator.comparing(RuneLiteHost::pluginName, String.CASE_INSENSITIVE_ORDER));
        for (Object plugin : sorted) {
            String name = RuneLiteHost.pluginName(plugin);
            TextView row = label(name, 13f, 0xFFE0E0E0);
            row.setPadding(0, (int) (10 * density), 0, (int) (10 * density));
            row.setOnClickListener(v -> showConfigFor(plugin));
            configList.addView(row);
        }
    }

    /** Shows the config of the plugin named {@code name} (or the list if not found). */
    private void showConfigFor(String name) {
        for (Object plugin : RuneLiteHost.plugins()) {
            if (RuneLiteHost.pluginName(plugin).equalsIgnoreCase(name)) {
                showConfigFor(plugin);
                return;
            }
        }
        selectTab(TAB_CONFIG);
    }

    private void showConfigFor(Object plugin) {
        selectTab(TAB_CONFIG);
        configPlugin = plugin;
        configList.removeAllViews();
        String name = RuneLiteHost.pluginName(plugin);
        boolean enabled = RuneLiteHost.isPluginEnabled(plugin);
        TextView header = label(enabled ? name : name + " (disabled)", 14f, 0xFFFFC83D);
        header.setPadding(0, (int) (6 * density), 0, (int) (10 * density));
        configList.addView(header);

        if (!enabled) {
            // This list shows every plugin and edits config for all of them, but a
            // disabled plugin does nothing with its config. Without this row that reads
            // as "the setting has no effect / the plugin is broken" (it is how the
            // Entity Hider report was produced), so say it and offer the one tap that
            // fixes it.
            TextView warn = label(name + " is disabled — config changes do nothing. Tap to enable.",
                12f, 0xFFFF9800);
            warn.setPadding(0, (int) (4 * density), 0, (int) (8 * density));
            warn.setOnClickListener(v -> {
                if (RuneLiteHost.setPluginEnabled(plugin, true)) {
                    toast(name + " enabled");
                } else {
                    toast("could not enable " + name);
                }
                showConfigFor(plugin);
            });
            configList.addView(warn);
        }

        Object configManager = RuneLiteHost.configManager();
        if (configManager == null) {
            configList.addView(label("RuneLite runtime not running", 12f, 0xFFB0B0B0));
            return;
        }
        try {
            Class<?> iface = RuneLiteHost.pluginConfigClass(plugin);
            if (iface == null) {
                configList.addView(label("this plugin has no configuration", 12f, 0xFFB0B0B0));
                return;
            }
            Object proxy = configManager.getClass().getMethod("getConfig", Class.class)
                .invoke(configManager, iface);
            Class<?> configIface = RuneLiteHost.clientLoader()
                .loadClass("net.runelite.client.config.Config");
            Object descriptor = configManager.getClass()
                .getMethod("getConfigDescriptor", configIface).invoke(configManager, proxy);
            if (descriptor == null) {
                configList.addView(label("no config descriptor", 12f, 0xFFB0B0B0));
                return;
            }
            Map<String, Method> items = configItemMethods(iface);
            List<Object> descriptors = new ArrayList<>((Collection<Object>) descriptor.getClass()
                .getMethod("getItems").invoke(descriptor));
            if (descriptors.isEmpty()) {
                configList.addView(label("no configuration items", 12f, 0xFFB0B0B0));
                return;
            }
            descriptors.sort(Comparator.comparingInt(d -> configItemPosition(d)));
            String currentSection = null;
            for (Object itemDescriptor : descriptors) {
                try {
                    Object annotation = itemDescriptor.getClass().getMethod("getItem").invoke(itemDescriptor);
                    String section = (String) annotation.getClass().getMethod("section").invoke(annotation);
                    if (section != null && !section.isEmpty() && !section.equals(currentSection)) {
                        currentSection = section;
                        configList.addView(label(section, 12f, 0xFFFFC83D));
                    }
                    View widget = buildConfigWidget(iface, proxy, configManager, descriptor,
                        itemDescriptor, annotation, items);
                    if (widget != null) {
                        configList.addView(widget);
                    }
                } catch (Throwable t) {
                    Log.w(TAG, "config item failed", t);
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "config form failed for " + name, t);
            configList.addView(label("config unavailable: " + t, 11f, 0xFFE57373));
        }
    }

    private int configItemPosition(Object descriptor) {
        try {
            return (Integer) descriptor.getClass().getMethod("position").invoke(descriptor);
        } catch (Throwable t) {
            return 0;
        }
    }

    private Map<String, Method> configItemMethods(Class<?> iface) {
        Map<String, Method> out = new java.util.HashMap<>();
        for (Method method : iface.getMethods()) {
            if (method.getParameterCount() != 0 || method.getReturnType() == void.class) {
                continue;
            }
            for (Annotation annotation : method.getAnnotations()) {
                if ("net.runelite.client.config.ConfigItem".equals(annotation.annotationType().getName())) {
                    try {
                        String keyName = (String) annotation.annotationType().getMethod("keyName")
                            .invoke(annotation);
                        out.put(keyName == null || keyName.isEmpty() ? method.getName() : keyName, method);
                    } catch (Throwable t) {
                        out.put(method.getName(), method);
                    }
                }
            }
        }
        return out;
    }

    private View buildConfigWidget(Class<?> iface, Object proxy, Object configManager, Object groupDescriptor,
                                   Object itemDescriptor, Object annotation, Map<String, Method> items) throws Exception {
        String key = (String) itemDescriptor.getClass().getMethod("key").invoke(itemDescriptor);
        String displayName = (String) annotation.getClass().getMethod("name").invoke(annotation);
        boolean secret = (Boolean) annotation.getClass().getMethod("secret").invoke(annotation);
        Method getter = items.get(key);
        if (getter == null) {
            return null;
        }
        Object value = getter.invoke(proxy);
        Class<?> type = getter.getReturnType();
        String group = configGroup(groupDescriptor);

        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, (int) (6 * density), 0, (int) (6 * density));
        row.addView(label(displayName == null || displayName.isEmpty() ? key : displayName, 12f, 0xFFE0E0E0));

        if (type == boolean.class || type == Boolean.class) {
            Switch toggle = new Switch(activity);
            toggle.setChecked(Boolean.TRUE.equals(value));
            toggle.setOnCheckedChangeListener((v, checked) ->
                writeConfig(iface, proxy, configManager, group, key, checked));
            row.addView(toggle);
            return row;
        }
        if (type == int.class || type == Integer.class || type == double.class || type == Double.class) {
            int[] range = configRange(itemDescriptor, type == double.class || type == Double.class);
            int min = range[0];
            int max = range[1];
            int current = (int) Math.round(((Number) (value == null ? 0 : value)).doubleValue());
            TextView valueLabel = label(String.valueOf(current), 12f, 0xFFFFC83D);
            SeekBar bar = new SeekBar(activity);
            bar.setMax(Math.max(1, max - min));
            bar.setProgress(Math.max(0, Math.min(max - min, current - min)));
            bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    valueLabel.setText(String.valueOf(min + progress));
                }

                public void onStartTrackingTouch(SeekBar seekBar) {
                }

                public void onStopTrackingTouch(SeekBar seekBar) {
                    int newValue = min + seekBar.getProgress();
                    if (type == double.class || type == Double.class) {
                        writeConfig(iface, proxy, configManager, group, key, (double) newValue);
                    } else {
                        writeConfig(iface, proxy, configManager, group, key, newValue);
                    }
                }
            });
            row.addView(valueLabel);
            row.addView(bar);
            return row;
        }
        if (type == String.class) {
            EditText field = new EditText(activity);
            field.setText(value == null ? "" : value.toString());
            field.setTextSize(12f);
            if (secret) {
                field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            }
            field.setOnFocusChangeListener((v, hasFocus) -> {
                if (!hasFocus) {
                    writeConfig(iface, proxy, configManager, group, key, field.getText().toString());
                }
            });
            row.addView(field);
            return row;
        }
        if (type == java.awt.Color.class) {
            Button button = new Button(activity);
            int rgb = value instanceof java.awt.Color ? ((java.awt.Color) value).getRGB() : 0xFFFFFFFF;
            button.setText(String.format("#%06X", rgb & 0xFFFFFF));
            button.setBackgroundColor(0xFF000000 | (rgb & 0xFFFFFF));
            button.setOnClickListener(v -> pickColour(iface, proxy, configManager, group, key, rgb));
            row.addView(button);
            return row;
        }
        if (type.isEnum()) {
            Spinner spinner = new Spinner(activity);
            Object[] constants = type.getEnumConstants();
            List<String> names = new ArrayList<>();
            for (Object constant : constants) {
                names.add(((Enum<?>) constant).name());
            }
            ArrayAdapter<String> adapter = new ArrayAdapter<>(activity,
                android.R.layout.simple_spinner_item, names);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinner.setAdapter(adapter);
            if (value != null) {
                spinner.setSelection(java.util.Arrays.asList(constants).indexOf(value));
            }
            final Class<?> enumType = type;
            spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    writeConfig(iface, proxy, configManager, group, key,
                        enumType.getEnumConstants()[position]);
                }

                public void onNothingSelected(AdapterView<?> parent) {
                }
            });
            row.addView(spinner);
            return row;
        }
        row.addView(label((value == null ? "null" : value.toString()) + "  (not editable on mobile)",
            11f, 0xFF9E9E9E));
        return row;
    }

    private int[] configRange(Object itemDescriptor, boolean floating) {
        try {
            Object range = itemDescriptor.getClass().getMethod("getRange").invoke(itemDescriptor);
            if (range != null) {
                int min = (Integer) range.getClass().getMethod("min").invoke(range);
                int max = (Integer) range.getClass().getMethod("max").invoke(range);
                if (max > min) {
                    return new int[]{min, max};
                }
            }
        } catch (Throwable ignored) {
            // no @Range: fall through to the default
        }
        return floating ? new int[]{0, 100} : new int[]{0, 100};
    }

    private String configGroup(Object groupDescriptor) {
        try {
            Object group = groupDescriptor.getClass().getMethod("getGroup").invoke(groupDescriptor);
            Object value = group.getClass().getMethod("value").invoke(group);
            return value instanceof String ? (String) value : "";
        } catch (Throwable t) {
            return "";
        }
    }

    private void pickColour(Class<?> iface, Object proxy, Object configManager, String group, String key, int current) {
        final EditText input = new EditText(activity);
        input.setText(String.format("%06X", current & 0xFFFFFF));
        new AlertDialog.Builder(activity)
            .setTitle("Colour (hex RRGGBB)")
            .setView(input)
            .setPositiveButton("Apply", (dialog, which) -> {
                try {
                    int rgb = (int) Long.parseLong(input.getText().toString().trim(), 16);
                    writeConfig(iface, proxy, configManager, group, key,
                        new java.awt.Color(rgb & 0xFFFFFF));
                } catch (Throwable t) {
                    toast("invalid colour: " + input.getText());
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    /**
     * Writes a config value. The setter on the config proxy is preferred (that is what
     * makes {@code ConfigChanged} fire); otherwise the value is stored as a string
     * through {@code ConfigManager.setConfiguration(group, key, value)}, which RuneLite
     * converts back with the item's declared type.
     */
    private void writeConfig(Class<?> iface, Object proxy, Object configManager, String group,
                             String key, Object value) {
        try {
            for (Method setter : iface.getMethods()) {
                if (setter.getName().equals(key) && setter.getParameterCount() == 1) {
                    setter.invoke(proxy, value);
                    RuneLiteHost.flushConfig();
                    toast(key + " updated");
                    return;
                }
            }
            String stringValue = stringify(value);
            configManager.getClass()
                .getMethod("setConfiguration", String.class, String.class, String.class)
                .invoke(configManager, group, key, stringValue);
            RuneLiteHost.flushConfig();
            toast(key + " = " + stringValue);
        } catch (Throwable t) {
            Log.w(TAG, "config write failed for " + key, t);
            toast("could not write " + key + ": " + t.getClass().getSimpleName());
        }
    }

    private String stringify(Object value) {
        if (value instanceof java.awt.Color) {
            // The decimal ARGB int, not hex: that is what ConfigManager.objectToString()
            // writes for a Color and what ColorUtil.fromString() reads back
            // (Integer.decode + new Color(int, true)). A hex string silently loses the
            // write ("00FF00" decodes as octal -> NumberFormatException -> null).
            return String.valueOf(((java.awt.Color) value).getRGB());
        }
        if (value instanceof Enum) {
            return ((Enum<?>) value).name();
        }
        return String.valueOf(value);
    }

    // --------------------------------------------------------------------- host tab
    private void buildHostTab() {
        hostList.removeAllViews();
        hostList.addView(hostLine("client version", RuneLiteHost.clientVersion()));
        hostList.addView(hostLine("runtime", RuneLiteHost.isRunning() ? "running" : "not running"));
        hostList.addView(hostLine("plugin index", RuneLiteHost.indexSize() + " classes"));
        hostList.addView(hostLine("active plugins", String.valueOf(RuneLiteHost.activePluginCount())));
        hostList.addView(hostLine("status", RuneLiteHost.status()));
        Throwable failure = RuneLiteHost.failure();
        hostList.addView(hostLine("last error", failure == null ? "none" : String.valueOf(failure)));
        List<String> pluginFailures = RuneLiteHost.pluginFailures();
        hostList.addView(hostLine("plugin load failures", String.valueOf(pluginFailures.size())));
        for (String entry : pluginFailures) {
            hostList.addView(label("  " + entry, 10f, 0xFFE57373));
        }
        int aot = ClientUpdater.clientDexAotStatus(activity);
        boolean aotBad = aot == ClientUpdater.AOT_STALE || aot == ClientUpdater.AOT_MISSING;
        String aotText = ClientUpdater.clientDexAotText(activity);
        hostList.addView(aotBad
            ? label("client AOT: " + aotText, 11f, 0xFFE57373)
            : hostLine("client AOT", aotText));
        hostList.addView(hostLine("on-device dexer", "unavailable"));
        hostList.addView(hostLine("conformance", PluginConformance.isRunning()
            ? "running…" : PluginConformance.lastSummary()));
        Button conformance = new Button(activity);
        conformance.setText("Run plugin conformance");
        conformance.setTextSize(12f);
        conformance.setEnabled(!PluginConformance.isRunning() && RuneLiteHost.isRunning());
        conformance.setOnClickListener(v -> {
            PluginConformance.run(activity);
            toast("conformance running — report: "
                + PluginConformance.reportFile(activity).getName());
        });
        hostList.addView(conformance);
        Button refresh = new Button(activity);
        refresh.setText("Refresh");
        refresh.setTextSize(12f);
        refresh.setOnClickListener(v -> {
            refreshPlugins();
            buildHostTab();
        });
        hostList.addView(refresh);
    }

    private View hostLine(String label, String value) {
        TextView row = label(label + ": " + value, 11f, 0xFFB0B0B0);
        row.setPadding(0, (int) (3 * density), 0, (int) (3 * density));
        return row;
    }

    // --------------------------------------------------------------------- helpers
    private TextView label(String text, float size, int colour) {
        TextView view = new TextView(activity);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(colour);
        return view;
    }

    private void toast(String message) {
        Toast.makeText(activity, message, Toast.LENGTH_SHORT).show();
    }
}
