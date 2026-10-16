package javax.swing;

import java.awt.Component;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;

/**
 * Swing {@code DefaultListCellRenderer}: the label-based cell renderer a {@link JList} or
 * {@link JComboBox} uses when no renderer was installed.
 *
 * <p>Hand-written because the shipped {@code TitleCaseListCellRenderer} subclasses it and
 * the RuneLite sidebar needs the selected/unselected colouring. It follows the JDK's
 * rubber-stamp shape: the same label instance is reconfigured and returned for every cell.
 *
 * <p>Deviation: the JDK's focus highlight border is read from
 * {@code UIManager.getBorder("List.focusCellHighlightBorder")}, which the port's theme
 * does not seed, so a focused cell keeps the plain (empty) border.
 */
public class DefaultListCellRenderer extends JLabel implements ListCellRenderer<Object> {

    private static final long serialVersionUID = 1418789684483382308L;

    /** Shared empty border; never null so {@code setBorder} always clears a stale border. */
    protected static Border noFocusBorder = new EmptyBorder(1, 1, 1, 1);

    public DefaultListCellRenderer() {
        setOpaque(true);
        setBorder(noFocusBorder);
    }

    @Override
    public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                  boolean isSelected, boolean cellHasFocus) {
        setText(value == null ? "" : value.toString());
        if (isSelected) {
            setBackground(list.getSelectionBackground());
            setForeground(list.getSelectionForeground());
        } else {
            setBackground(list.getBackground());
            setForeground(list.getForeground());
        }
        setFont(list.getFont());
        setOpaque(true);
        setBorder(cellHasFocus
                ? UIManager.getBorder("List.focusCellHighlightBorder")
                : noFocusBorder);
        return this;
    }

    /**
     * A renderer that comes from the look and feel. No look and feel is installed on this
     * port, so this is just a named {@code DefaultListCellRenderer}; the JDK's
     * {@code javax.swing.plaf.UIResource} marker interface is not present in this tree.
     */
    public static class UIResource extends DefaultListCellRenderer {
        private static final long serialVersionUID = 1L;
    }
}
