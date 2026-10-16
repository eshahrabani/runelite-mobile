package javax.swing.plaf.basic;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import javax.swing.AbstractButton;
import javax.swing.ButtonModel;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.ButtonUI;

/**
 * Basic look-and-feel {@code AbstractButton} painter and interaction install.
 *
 * <p>Hand-written because a button has to become an {@code ActionEvent}: the installed mouse
 * listener arms/presses on press and releases on release (the model fires exactly one
 * {@code ACTION_PERFORMED} when an armed press is released), rollover on enter/exit, and
 * {@code SPACE} key bindings do the same from the keyboard. {@code paint} fills the content
 * area, places the state-appropriate icon and the text with
 * {@link SwingUtilities#layoutCompoundLabel}, and paints the border (the port's UI paint
 * path does not call {@code JComponent.paintBorder}).
 */
public class BasicButtonUI extends ButtonUI {

    protected AbstractButton button;

    private MouseListener mouseListener;
    private FocusListener focusListener;
    private KeyStroke pressedKeyStroke;
    private KeyStroke releasedKeyStroke;
    private long lastClickTime;

    public static ComponentUI createUI(JComponent c) {
        return new BasicButtonUI();
    }

    public BasicButtonUI() {
        super();
    }

    @Override
    public void installUI(JComponent c) {
        button = (AbstractButton) c;
        installDefaults(button);
        installListeners(button);
        installKeyboardActions(button);
    }

    @Override
    public void uninstallUI(JComponent c) {
        uninstallKeyboardActions(button);
        uninstallListeners(button);
        uninstallDefaults(button);
        button = null;
    }

    protected void installDefaults(AbstractButton b) {
        // Nothing forced: the subclass constructor already applied the UIManager colours, and
        // a caller-set font/foreground must survive.
    }

    protected void uninstallDefaults(AbstractButton b) {
    }

    protected void installListeners(AbstractButton b) {
        mouseListener = createMouseListener();
        focusListener = createFocusListener();
        b.addMouseListener(mouseListener);
        b.addFocusListener(focusListener);
    }

    protected void uninstallListeners(AbstractButton b) {
        if (mouseListener != null) {
            b.removeMouseListener(mouseListener);
            mouseListener = null;
        }
        if (focusListener != null) {
            b.removeFocusListener(focusListener);
            focusListener = null;
        }
    }

    protected void installKeyboardActions(AbstractButton b) {
        pressedKeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0, false);
        releasedKeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0, true);
        b.registerKeyboardAction(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                AbstractButton target = BasicButtonUI.this.button;
                if (target != null && target.isEnabled()) {
                    target.getModel().setArmed(true);
                    target.getModel().setPressed(true);
                }
            }
        }, "pressed", pressedKeyStroke, JComponent.WHEN_FOCUSED);
        b.registerKeyboardAction(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                AbstractButton target = BasicButtonUI.this.button;
                if (target != null) {
                    target.getModel().setPressed(false);
                    target.getModel().setArmed(false);
                }
            }
        }, "released", releasedKeyStroke, JComponent.WHEN_FOCUSED);
    }

    protected void uninstallKeyboardActions(AbstractButton b) {
        if (pressedKeyStroke != null) {
            b.unregisterKeyboardAction(pressedKeyStroke);
        }
        if (releasedKeyStroke != null) {
            b.unregisterKeyboardAction(releasedKeyStroke);
        }
    }

    private MouseListener createMouseListener() {
        return new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                AbstractButton b = BasicButtonUI.this.button;
                if (b != null && b.isRolloverEnabled()) {
                    b.getModel().setRollover(true);
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                AbstractButton b = BasicButtonUI.this.button;
                if (b == null) {
                    return;
                }
                if (b.isRolloverEnabled()) {
                    b.getModel().setRollover(false);
                }
                if (b.getModel().isPressed()) {
                    b.getModel().setPressed(false);
                }
                b.getModel().setArmed(false);
            }

            @Override
            public void mousePressed(MouseEvent e) {
                AbstractButton b = BasicButtonUI.this.button;
                if (b == null || !b.isEnabled()) {
                    return;
                }
                if (e.getButton() != MouseEvent.BUTTON1 && e.getButton() != MouseEvent.NOBUTTON) {
                    return;
                }
                int threshold = b.getMultiClickThreshhold();
                if (threshold > 0 && e.getWhen() - lastClickTime < threshold) {
                    return;
                }
                lastClickTime = e.getWhen();
                b.getModel().setArmed(true);
                b.getModel().setPressed(true);
                b.requestFocus();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                AbstractButton b = BasicButtonUI.this.button;
                if (b == null || !b.getModel().isPressed()) {
                    return;
                }
                // Fires ACTION_PERFORMED (the model checks it is still armed).
                b.getModel().setPressed(false);
                b.getModel().setArmed(false);
            }
        };
    }

    private FocusListener createFocusListener() {
        return new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                repaintButton();
            }

            @Override
            public void focusLost(FocusEvent e) {
                repaintButton();
            }
        };
    }

    private void repaintButton() {
        if (button != null) {
            button.repaint();
        }
    }

    // ---------------------------------------------------------------- paint

    @Override
    public void paint(Graphics g, JComponent c) {
        AbstractButton b = (AbstractButton) c;
        ButtonModel model = b.getModel();

        if (b.isContentAreaFilled()) {
            Color background = b.getBackground();
            if (background != null) {
                g.setColor(background);
                g.fillRect(0, 0, b.getWidth(), b.getHeight());
            }
        }

        Font font = b.getFont();
        if (font != null) {
            g.setFont(font);
        }
        FontMetrics fm = g.getFontMetrics(font);
        Insets insets = b.getInsets();
        Rectangle viewR = new Rectangle(insets.left, insets.top,
                Math.max(0, b.getWidth() - insets.left - insets.right),
                Math.max(0, b.getHeight() - insets.top - insets.bottom));
        Rectangle iconR = new Rectangle();
        Rectangle textR = new Rectangle();
        String text = b.getText();
        Icon icon = b.getIconForState();
        String clipped = SwingUtilities.layoutCompoundLabel((JComponent) b, fm, text, icon,
                b.getVerticalAlignment(), b.getHorizontalAlignment(),
                b.getVerticalTextPosition(), b.getHorizontalTextPosition(),
                viewR, iconR, textR, b.getIconTextGap());

        if (icon != null) {
            paintIcon(g, b, iconR);
        }
        if (clipped != null && !clipped.isEmpty()) {
            paintText(g, b, textR, clipped);
        }
        if (b.isFocusPainted() && b.hasFocus()) {
            paintFocus(g, b, viewR, textR, iconR);
        }
        paintBorder(c, g);
    }

    protected void paintIcon(Graphics g, AbstractButton b, Rectangle iconR) {
        Icon icon = b.getIconForState();
        if (icon != null) {
            icon.paintIcon(b, g, iconR.x, iconR.y);
        }
    }

    protected void paintText(Graphics g, AbstractButton b, Rectangle textRect, String text) {
        FontMetrics fm = g.getFontMetrics(b.getFont());
        int x = textRect.x;
        int y = textRect.y + fm.getAscent();
        g.setColor(textColor(b));
        g.drawString(text, x, y);
    }

    protected void paintFocus(Graphics g, AbstractButton b, Rectangle viewRect,
                              Rectangle textRect, Rectangle iconRect) {
        // A thin outline around the content, in the button foreground (the desktop LAF draws
        // a dotted rectangle; the exact pixels are unspecified, so this is the obvious ring).
        Color foreground = b.getForeground();
        if (foreground == null) {
            foreground = javax.swing.UIManager.getColor("Button.foreground");
        }
        g.setColor(foreground != null ? foreground : Color.LIGHT_GRAY);
        g.drawRect(viewRect.x, viewRect.y, Math.max(0, viewRect.width - 1), Math.max(0, viewRect.height - 1));
    }

    protected void paintBorder(JComponent c, Graphics g) {
        javax.swing.border.Border border = c.getBorder();
        if (border == null) {
            return;
        }
        if (c instanceof AbstractButton && !((AbstractButton) c).isBorderPainted()) {
            return;
        }
        border.paintBorder(c, g, 0, 0, c.getWidth(), c.getHeight());
    }

    private Color textColor(AbstractButton b) {
        if (!b.isEnabled()) {
            Color foreground = b.getForeground();
            Color background = b.getBackground();
            if (foreground != null && background != null) {
                return new Color((foreground.getRed() + background.getRed()) / 2,
                        (foreground.getGreen() + background.getGreen()) / 2,
                        (foreground.getBlue() + background.getBlue()) / 2);
            }
            return Color.GRAY;
        }
        Color foreground = b.getForeground();
        return foreground != null ? foreground : Color.LIGHT_GRAY;
    }

    /** The JDK's small press offset; zero here (hard-edged, unscaled text). */
    protected int getTextShiftOffset() {
        return 0;
    }

    // ----------------------------------------------------------------- size

    @Override
    public Dimension getPreferredSize(JComponent c) {
        AbstractButton b = (AbstractButton) c;
        Font font = b.getFont();
        FontMetrics fm = b.getFontMetrics(font);
        String text = b.getText();
        Icon icon = b.getIconForState();
        Insets insets = b.getInsets();

        boolean hasText = text != null && !text.isEmpty();
        int textWidth = hasText ? fm.stringWidth(text) : 0;
        int textHeight = hasText ? fm.getHeight() : 0;
        int iconWidth = icon != null ? icon.getIconWidth() : 0;
        int iconHeight = icon != null ? icon.getIconHeight() : 0;
        int gap = (icon != null && hasText) ? b.getIconTextGap() : 0;

        int width = insets.left + insets.right + textWidth + iconWidth + gap + 8;
        int height = insets.top + insets.bottom + Math.max(textHeight, iconHeight) + 6;
        return new Dimension(width, height);
    }

    @Override
    public Dimension getMinimumSize(JComponent c) {
        return getPreferredSize(c);
    }

    @Override
    public Dimension getMaximumSize(JComponent c) {
        return getPreferredSize(c);
    }
}
