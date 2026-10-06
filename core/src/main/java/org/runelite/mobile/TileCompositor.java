package org.runelite.mobile;

/**
 * Software-renderer scene diagnostic hooks for the injected OSRS client.
 *
 * The 2026 client's software scene renderer draws each visible tile as a
 * textured quad: gp.em (tile loop) -> fh.aj/af/az (fq instances, concrete type
 * ff) -> ff.aj (quad) -> TextureProvider.getPixels (ec/fg) -> fu.ax/fu.mx
 * (renders the 256x256 tile texture into fu.al) -> ff.ct (textured-triangle
 * rasterizer) -> yw.ea/es/ed (hline fills) which write into yw.aj (the
 * Rasterizer2D display buffer). The world is therefore drawn directly into the
 * display buffer by the client; there is no missing tile composite.
 *
 * The injected hooks below diagnose where the scene pixels get lost on this
 * port. All game classes are accessed reflectively (obfuscation-proof names
 * are stable for a given client version and the field lookups walk the class
 * hierarchy). No hook may throw into the game's frame loop.
 */
public final class TileCompositor {

    private static volatile boolean logged;

    private static volatile ClassLoader cachedCl;

    /** Called by the launcher once the game's DexClassLoader exists. */
    public static void setClassLoader(ClassLoader cl) {
        cachedCl = cl;
    }

    private TileCompositor() {}

    private static volatile int hookFailures;

    /**
     * Sink for any diagnostic-hook failure. Injected hooks run inside the
     * game's frame loop, so none may ever throw into it.
     */
    private static void hookFailed(Throwable t) {
        if (hookFailures++ == 0) {
            System.err.println("[Hook] failed: " + t);
        }
    }

    /**
     * Called from inside the game's frame loop, on the client thread, right
     * after the scene render (gp.az). Read-only snapshot of the display buffer
     * at the moment the scene has drawn: samples pixels, counts distinct
     * colors and green/sky/black pixels. Also logs the per-frame counter
     * deltas for the scene draw hooks. Must not write into the display.
     *
     * @param clientObject the client instance (`this` in client.ij)
     */
    public static void composite(Object clientObject) {
        if (clientObject == null) {
            return;
        }
        try {
            ClassLoader cl = clFor(clientObject.getClass().getClassLoader());
            if (cl == null) {
                return;
            }
            frameCount++;
            Class<?> yw = cl.loadClass("yw");
            int[] disp = staticInts(yw, "aj");
            if (disp == null) {
                return;
            }
            int w = staticInt(yw, "ay");
            int h = staticInt(yw, "aq");
            int stride = (w > 0 && w <= 4096) ? w : 765;
            int hh = (h > 0 && h <= 4096) ? h : 503;

            if (frameCount <= 3 || (frameCount % 30) == 0) {
                System.err.println("[Frame] f=" + frameCount + " em=" + emCalls
                    + " aj=" + ajCalls + " ct=" + ctCalls + " bh=" + bhCalls
                    + " es=" + esCalls + " mx=" + mxEnters + " dg=" + dgCalls
                    + " present=" + presentCalls + " disp=" + disp.length
                    + " px=" + System.identityHashCode(disp));
            }

            if (!logged || (frameCount % 120) == 0) {
                logged = true;
                StringBuilder sb = new StringBuilder("[Snapshot] f=" + frameCount);
                appendYwState(yw, sb);
                try {
                    int[] fhae = staticInts(cl.loadClass("fh"), "ae");
                    sb.append(" fh.ae=").append(fhae == null ? "null"
                        : (System.identityHashCode(fhae) + "(" + fhae.length + ")"));
                } catch (Throwable ignored) {
                    sb.append(" fh.ae=ERR");
                }
                int[][] pts = {
                    {10, 10}, {40, 30}, {380, 150}, {380, 300}, {380, 450},
                    {60, 400}, {700, 400}, {700, 100}
                };
                sb.append(" samp=");
                for (int[] p : pts) {
                    int idx = p[1] * stride + p[0];
                    sb.append(String.format("%08X,", (idx >= 0 && idx < disp.length) ? disp[idx] : -1));
                }
                java.util.HashSet<Integer> colors = new java.util.HashSet<>();
                for (int i = 0; i < disp.length; i += 41) {
                    colors.add(disp[i] & 0x00FFFFFF);
                    if (colors.size() > 6000) {
                        break;
                    }
                }
                sb.append(" colors=").append(colors.size());
                int green = 0, sky = 0, dark = 0;
                for (int i = 0; i < disp.length; i += 17) {
                    int c = disp[i] & 0x00FFFFFF;
                    if (c == 0) {
                        dark++;
                    } else {
                        int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
                        if (g > 90 && g > r + 20 && g > b + 20) {
                            green++;
                        } else if (b > r && b > g && b > 180) {
                            sky++;
                        }
                    }
                }
                sb.append(" green=").append(green).append(" sky=").append(sky)
                    .append(" black=").append(dark);
                System.err.println(sb);
            }
        } catch (Throwable t) {
            try {
                if (!logged) {
                    logged = true;
                    System.err.println("[Snapshot] failed: " + t);
                }
            } catch (Throwable ignored) {}
        }
    }

    private static volatile int frameCount;

    /**
     * Called from inside client.ij right AFTER the 2D UI pass (client.iz),
     * just before the frame is presented. Reports whether the world drawn by
     * the scene (visible to {@link #composite}) survives the UI pass.
     *
     * @param clientObject the client instance
     */
    public static void compositeEnd(Object clientObject) {
        if (clientObject == null) {
            return;
        }
        try {
            ClassLoader cl = clFor(clientObject.getClass().getClassLoader());
            if (cl == null) {
                return;
            }
            endCount++;
            if (endCount % 30 != 0) {
                return;
            }
            Class<?> yw = cl.loadClass("yw");
            int[] disp = staticInts(yw, "aj");
            if (disp == null) {
                return;
            }
            int stride = 765;
            int w = staticInt(yw, "ay");
            if (w > 0 && w <= 4096) {
                stride = w;
            }
            int green = 0, sky = 0, dark = 0;
            for (int i = 0; i < disp.length; i += 17) {
                int c = disp[i] & 0x00FFFFFF;
                if (c == 0) {
                    dark++;
                } else {
                    int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
                    if (g > 90 && g > r + 20 && g > b + 20) {
                        green++;
                    } else if (b > r && b > g && b > 180) {
                        sky++;
                    }
                }
            }
            StringBuilder sb = new StringBuilder("[End] f=" + frameCount + " green=" + green
                + " sky=" + sky + " black=" + dark);
            int[][] pts = {{380, 300}, {380, 450}, {700, 400}, {100, 100}};
            for (int[] p : pts) {
                int idx = p[1] * stride + p[0];
                sb.append(String.format(",%08X", (idx >= 0 && idx < disp.length) ? disp[idx] : -1));
            }
            System.err.println(sb);
        } catch (Throwable t) {
            System.err.println("[End] error " + t);
        }
    }

    private static volatile int endCount;

    /**
     * Diagnostic hook injected at the start of yw.es (the hline fill used by
     * every scene polygon). A non-zero count proves the scene rasterizer is
     * actually writing pixels; the logged state shows the buffer target and
     * clip rect at the time.
     */
    public static void traceYwEs(int x0, int y, int x1, int color) {
        esCalls++;
        if (esCalls > 12 && (esCalls % 1024) != 0) {
            return;
        }
        try {
            ClassLoader cl = clFor(null);
            if (cl == null) {
                return;
            }
            Class<?> yw = cl.loadClass("yw");
            StringBuilder sb = new StringBuilder();
            sb.append("[EsTrace] call=").append(esCalls)
                .append(" x0=").append(x0).append(" y=").append(y).append(" x1=").append(x1)
                .append(" c=").append(String.format("%08X", color));
            appendYwState(yw, sb);
            System.err.println(sb);
        } catch (Throwable t) {
            System.err.println("[EsTrace] error " + t);
        }
    }

    private static volatile int esCalls;

    /**
     * Diagnostic hook injected at the start of ff.aj (the textured tile quad
     * draw). Counts tile draws per frame.
     */
    public static void traceFqAj() {
        try {
            ajCalls++;
            if (ajCalls <= 8 || (ajCalls % 256) == 0) {
                System.err.println("[AjTrace] aj=" + ajCalls + " ct=" + ctCalls + " es=" + esCalls);
            }
        } catch (Throwable t) {
            hookFailed(t);
        }
    }

    private static volatile int ajCalls;

    /**
     * Diagnostic hook injected at the start of ff.ct (the textured-triangle
     * rasterizer). Counts rasterized textured triangles.
     */
    public static void traceFqCt() {
        try {
            ctCalls++;
        } catch (Throwable t) {
            hookFailed(t);
        }
    }

    private static volatile int ctCalls;

    /**
     * Diagnostic hook injected at the start of ff.bh (the innermost textured
     * span blit with depth test). Counts per-pixel-span blend operations; a
     * count comparable to ct proves the pixel writes execute.
     */
    public static void traceFfBh() {
        try {
            bhCalls++;
        } catch (Throwable t) {
            hookFailed(t);
        }
    }

    private static volatile int bhCalls;

    /**
     * Diagnostic hook injected at the start of client.dg (the loading-screen
     * renderer). Counts how often the loading screen is drawn over the world.
     */
    public static void traceDg() {
        try {
            dgCalls++;
        } catch (Throwable t) {
            hookFailed(t);
        }
    }

    private static volatile int dgCalls;

    /**
     * Diagnostic hook injected at the start of tg.af (the MainBufferProvider
     * present -> Callbacks.draw). Reports the display buffer state at the
     * exact moment the frame is presented.
     */
    public static void tracePresent(Object provider) {
        presentCalls++;
        if (presentCalls % 15 != 0) {
            return;
        }
        try {
            ClassLoader cl = clFor(provider == null ? null : provider.getClass().getClassLoader());
            if (cl == null) {
                return;
            }
            Class<?> yw = cl.loadClass("yw");
            int[] disp = staticInts(yw, "aj");
            if (disp == null) {
                return;
            }
            int stride = 765;
            int w = staticInt(yw, "ay");
            if (w > 0 && w <= 4096) {
                stride = w;
            }
            int green = 0, sky = 0, dark = 0;
            for (int i = 0; i < disp.length; i += 17) {
                int c = disp[i] & 0x00FFFFFF;
                if (c == 0) {
                    dark++;
                } else {
                    int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
                    if (g > 90 && g > r + 20 && g > b + 20) {
                        green++;
                    } else if (b > r && b > g && b > 180) {
                        sky++;
                    }
                }
            }
            StringBuilder sb = new StringBuilder("[Present] p=" + presentCalls
                + " f=" + frameCount + " green=" + green + " sky=" + sky + " black=" + dark);
            int[][] pts = {{380, 300}, {380, 450}, {100, 100}};
            for (int[] pt : pts) {
                int idx = pt[1] * stride + pt[0];
                sb.append(String.format(",%08X", (idx >= 0 && idx < disp.length) ? disp[idx] : -1));
            }
            System.err.println(sb);
        } catch (Throwable t) {
            System.err.println("[Present] error " + t);
        }
    }

    private static volatile int presentCalls;

    /**
     * Diagnostic hook injected at the start of gp.em (the visible-tile loop).
     * Reports the number of tiles processed per scene render.
     */
    public static void traceGpEm(Object tiles) {
        try {
            emCalls++;
            if (emCalls <= 8 || (emCalls % 128) == 0) {
                int n = -1;
                if (tiles instanceof Object[]) {
                    n = ((Object[]) tiles).length;
                }
                System.err.println("[EmTrace] em=" + emCalls + " tiles=" + n);
            }
        } catch (Throwable t) {
            hookFailed(t);
        }
    }

    private static volatile int emCalls;

    /**
     * Diagnostic hook injected at the start of fu.az (the terrain tile render
     * trigger). Counts invocations and reports whether the tiles ever get
     * rendered (al != null) - distinguishes "trigger never fires" from
     * "region data missing".
     */
    public static void traceTileEnter(Object fuTile) {
        try {
            traceCalls++;
            if (traceCalls <= 8 || (traceCalls % 256) == 0) {
                java.lang.reflect.Field asF = findFieldInChain(fuTile.getClass(), "as");
                asF.setAccessible(true);
                int idx = asF.getInt(fuTile);
                java.lang.reflect.Field alF = findFieldInChain(fuTile.getClass(), "al");
                alF.setAccessible(true);
                Object al = alF.get(fuTile);
                System.err.println("[TileTrace] call=" + traceCalls + " tileIdx=" + idx
                    + " al=" + (al == null ? "null" : "set(" + ((int[]) al).length + ")"));
            }
        } catch (Throwable t) {
            System.err.println("[TileTrace] error " + t);
        }
    }

    private static volatile int traceCalls;

    /**
     * Diagnostic hook injected at the start of gp.az (the scene render entry).
     * Logs the two gate arguments and the decoded camera state.
     */
    public static void traceGate(int arg0, int arg1, Object camera) {
        try {
            gateCalls++;
            clFor(camera == null ? null : camera.getClass().getClassLoader());
            if (gateCalls <= 10 || (gateCalls % 128) == 0) {
                int camState = -999;
                if (camera != null) {
                    try {
                        java.lang.reflect.Field fap = findFieldInChain(camera.getClass(), "ap");
                        fap.setAccessible(true);
                        long v = fap.getInt(camera) & 0xFFFFFFFFL;
                        camState = (int) ((v * 0xfb67f7c1L) & 0xFFFFFFFFL);
                        if (camState > 0x7FFFFFFFL) camState -= 0x100000000L;
                    } catch (Throwable ignored) {}
                }
                System.err.println("[GateTrace] call=" + gateCalls + " arg0=" + arg0 + " arg1=" + arg1
                    + " cam=" + (camera == null ? "null" : camState));
            }
        } catch (Throwable t) {
            System.err.println("[GateTrace] error " + t);
        }
    }

    private static volatile int gateCalls;

    /**
     * Diagnostic hook injected at the data-present branch of fu.ax (right
     * after the va.bl null check passes). If this never fires, the region
     * tile data is missing (va.bl returns null for every tile).
     */
    public static void traceTileRender(Object fuTile) {
        try {
            dataCalls++;
            if (dataCalls <= 8 || (dataCalls % 64) == 0) {
                java.lang.reflect.Field asF = findFieldInChain(fuTile.getClass(), "as");
                asF.setAccessible(true);
                System.err.println("[TileData] render=" + dataCalls + " tileIdx=" + asF.getInt(fuTile));
            }
        } catch (Throwable t) {
            System.err.println("[TileData] error " + t);
        }
    }

    private static volatile int dataCalls;

    /**
     * Diagnostic hook injected at fu.mx (the tile terrain render). Tracks
     * entry and the boolean result (false = render failed).
     */
    public static void traceMxEnter(Object fuTile) {
        try {
            clFor(fuTile == null ? null : fuTile.getClass().getClassLoader());
            mxEnters++;
            if (mxEnters <= 8 || (mxEnters % 64) == 0) {
                java.lang.reflect.Field asF = findFieldInChain(fuTile.getClass(), "as");
                asF.setAccessible(true);
                System.err.println("[MxTrace] enter=" + mxEnters + " tileIdx=" + asF.getInt(fuTile));
            }
        } catch (Throwable t) {
            System.err.println("[MxTrace] error " + t);
        }
    }

    public static void traceMxResult(int result) {
        try {
            mxResults++;
            if (mxResults <= 16 || (mxResults % 64) == 0) {
                System.err.println("[MxTrace] result=" + result + " (total ok=" + (mxResults) + ")");
            }
        } catch (Throwable t) {
            hookFailed(t);
        }
    }

    private static volatile int mxEnters;
    private static volatile int mxResults;

    private static ClassLoader clFor(ClassLoader fallback) {
        if (fallback != null) {
            cachedCl = fallback;
            return fallback;
        }
        if (cachedCl != null) {
            return cachedCl;
        }
        try {
            ClassLoader tcl = Thread.currentThread().getContextClassLoader();
            if (tcl != null) {
                cachedCl = tcl;
            }
            return tcl;
        } catch (Throwable t) {
            return cachedCl;
        }
    }

    private static int staticInt(Class<?> c, String name) {
        try {
            java.lang.reflect.Field f = c.getDeclaredField(name);
            f.setAccessible(true);
            return f.getInt(null);
        } catch (Throwable t) {
            return Integer.MIN_VALUE;
        }
    }

    private static int[] staticInts(Class<?> c, String name) {
        try {
            java.lang.reflect.Field f = c.getDeclaredField(name);
            f.setAccessible(true);
            return (int[]) f.get(null);
        } catch (Throwable t) {
            return null;
        }
    }

    private static void appendYwState(Class<?> yw, StringBuilder sb) {
        int[] px = staticInts(yw, "aj");
        sb.append(" px=").append(px == null ? "null"
            : (System.identityHashCode(px) + "(" + px.length + ")"));
        sb.append(" ay=").append(staticInt(yw, "ay"));
        sb.append(" aq=").append(staticInt(yw, "aq"));
        sb.append(" clip=").append(staticInt(yw, "ai")).append(",")
            .append(staticInt(yw, "ap")).append(",")
            .append(staticInt(yw, "ar")).append(",")
            .append(staticInt(yw, "au"));
        try {
            java.lang.reflect.Field fd = yw.getDeclaredField("ad");
            fd.setAccessible(true);
            float[] depth = (float[]) fd.get(null);
            sb.append(" dep=").append(depth == null ? "null" : "(" + depth.length + ")");
        } catch (Throwable t) {
            sb.append(" dep=ERR");
        }
    }

    private static java.lang.reflect.Field findFieldInChain(Class<?> c, String name) {
        Class<?> cur = c;
        while (cur != null) {
            try {
                java.lang.reflect.Field f = cur.getDeclaredField(name);
                return f;
            } catch (NoSuchFieldException e) {
                cur = cur.getSuperclass();
            }
        }
        throw new RuntimeException("field not found: " + name);
    }
}