package javax.swing.text;

import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;

import javax.swing.Action;
import javax.swing.InputMap;
import javax.swing.ActionMap;
import javax.swing.KeyStroke;

/**
 * Hand-written {@code javax.swing.text.DefaultEditorKit}: the plain-text editor kit and the
 * action set a {@link JTextComponent} binds its keys to.
 *
 * <p>Why hand-written: this is where the port's typing behaviour comes from. The port has no
 * {@code TextUI} and no {@code Keymap} hierarchy, so the kit's actions are also what
 * {@code JTextComponent} installs into its {@code InputMap}/{@code ActionMap}; without it
 * BACK_SPACE, the arrows, HOME/END, Enter and Ctrl+C/X/V did nothing at all. The generated stub
 * declared no members.
 *
 * <p>Deviations from the JDK, all deliberate:
 * <ul>
 *   <li>The caret-movement actions move through {@link JTextComponent}'s own line layout
 *       ({@code modelToView}/{@code viewToModel} and its package-private line helpers) instead
 *       of the JDK's {@code Utilities.getPositionAbove/Below}, which need a view tree.</li>
 *   <li>{@code UIManager.getLookAndFeel().provideErrorFeedback(...)} is replaced by
 *       {@link Toolkit#beep()} — the port's LAF has no error feedback.</li>
 *   <li>The key <em>bindings</em> live in {@link #installDefaultBindings} rather than in the
 *       look-and-feel defaults table (the port's {@code UIDefaults} does not model the LAF's
 *       input maps). The strokes bound are the JDK's.</li>
 * </ul>
 */
public class DefaultEditorKit extends EditorKit {

    /** The property that names the string a read operation ended a line with. */
    public static final String EndOfLineStringProperty = "__EndOfLine__";

    // ------------------------------------------------ action name constants

    public static final String insertContentAction = "insert-content";
    public static final String insertBreakAction = "insert-break";
    public static final String insertTabAction = "insert-tab";
    public static final String deletePrevCharAction = "delete-previous";
    public static final String deleteNextCharAction = "delete-next";
    public static final String deleteNextWordAction = "delete-next-word";
    public static final String deletePrevWordAction = "delete-previous-word";
    public static final String readOnlyAction = "set-read-only";
    public static final String writableAction = "set-writable";
    public static final String cutAction = "cut-to-clipboard";
    public static final String copyAction = "copy-to-clipboard";
    public static final String pasteAction = "paste-from-clipboard";
    public static final String beepAction = "beep";
    public static final String pageUpAction = "page-up";
    public static final String pageDownAction = "page-down";
    public static final String forwardAction = "caret-forward";
    public static final String backwardAction = "caret-backward";
    public static final String selectionForwardAction = "selection-forward";
    public static final String selectionBackwardAction = "selection-backward";
    public static final String upAction = "caret-up";
    public static final String downAction = "caret-down";
    public static final String selectionUpAction = "selection-up";
    public static final String selectionDownAction = "selection-down";
    public static final String beginWordAction = "caret-begin-word";
    public static final String endWordAction = "caret-end-word";
    public static final String selectionBeginWordAction = "selection-begin-word";
    public static final String selectionEndWordAction = "selection-end-word";
    public static final String previousWordAction = "caret-previous-word";
    public static final String nextWordAction = "caret-next-word";
    public static final String selectionPreviousWordAction = "selection-previous-word";
    public static final String selectionNextWordAction = "selection-next-word";
    public static final String beginLineAction = "caret-begin-line";
    public static final String endLineAction = "caret-end-line";
    public static final String selectionBeginLineAction = "selection-begin-line";
    public static final String selectionEndLineAction = "selection-end-line";
    public static final String beginParagraphAction = "caret-begin-paragraph";
    public static final String endParagraphAction = "caret-end-paragraph";
    public static final String selectionBeginParagraphAction = "selection-begin-paragraph";
    public static final String selectionEndParagraphAction = "selection-end-paragraph";
    public static final String beginAction = "caret-begin";
    public static final String endAction = "caret-end";
    public static final String selectionBeginAction = "selection-begin";
    public static final String selectionEndAction = "selection-end";
    public static final String selectAllAction = "select-all";
    public static final String defaultKeyTypedAction = "default-typed";

    public DefaultEditorKit() {
    }

    @Override
    public String getContentType() {
        return "text/plain";
    }

    @Override
    public ViewFactory getViewFactory() {
        return null;
    }

    @Override
    public Caret createCaret() {
        return new DefaultCaret();
    }

    @Override
    public Document createDefaultDocument() {
        return new PlainDocument();
    }

    @Override
    public Action[] getActions() {
        return DEFAULT_ACTIONS.clone();
    }

    // ------------------------------------------------------ action objects

    private static final Action[] DEFAULT_ACTIONS = {
        new DefaultKeyTypedAction(),
        new InsertContentAction(),
        new InsertBreakAction(),
        new InsertTabAction(),
        new DeletePrevCharAction(),
        new DeleteNextCharAction(),
        new DeleteWordAction(deletePrevWordAction, false),
        new DeleteWordAction(deleteNextWordAction, true),
        new ReadOnlyAction(),
        new WritableAction(),
        new CutAction(),
        new CopyAction(),
        new PasteAction(),
        new BeepAction(),
        new NextVisualPositionAction(forwardAction, false, javax.swing.SwingConstants.EAST),
        new NextVisualPositionAction(backwardAction, false, javax.swing.SwingConstants.WEST),
        new NextVisualPositionAction(selectionForwardAction, true, javax.swing.SwingConstants.EAST),
        new NextVisualPositionAction(selectionBackwardAction, true, javax.swing.SwingConstants.WEST),
        new NextVisualPositionAction(upAction, false, javax.swing.SwingConstants.NORTH),
        new NextVisualPositionAction(downAction, false, javax.swing.SwingConstants.SOUTH),
        new NextVisualPositionAction(selectionUpAction, true, javax.swing.SwingConstants.NORTH),
        new NextVisualPositionAction(selectionDownAction, true, javax.swing.SwingConstants.SOUTH),
        new BeginWordAction(beginWordAction, false),
        new EndWordAction(endWordAction, false),
        new BeginWordAction(selectionBeginWordAction, true),
        new EndWordAction(selectionEndWordAction, true),
        new PreviousWordAction(previousWordAction, false),
        new NextWordAction(nextWordAction, false),
        new PreviousWordAction(selectionPreviousWordAction, true),
        new NextWordAction(selectionNextWordAction, true),
        new BeginLineAction(beginLineAction, false),
        new EndLineAction(endLineAction, false),
        new BeginLineAction(selectionBeginLineAction, true),
        new EndLineAction(selectionEndLineAction, true),
        new BeginParagraphAction(beginParagraphAction, false),
        new EndParagraphAction(endParagraphAction, false),
        new BeginParagraphAction(selectionBeginParagraphAction, true),
        new EndParagraphAction(selectionEndParagraphAction, true),
        new BeginAction(beginAction, false),
        new EndAction(endAction, false),
        new BeginAction(selectionBeginAction, true),
        new EndAction(selectionEndAction, true),
        new SelectAllAction(),
        new VerticalPageAction(pageUpAction, -1, false),
        new VerticalPageAction(pageDownAction, 1, false),
    };

    // ------------------------------------------------------ key bindings

    /**
     * Installs the JDK's default text key bindings into a component's maps.
     *
     * <p>The strokes are exactly the JDK's ({@code BasicLookAndFeel}/{@code MetalLookAndFeel}'s
     * {@code *InputMap} entries); a single-line component gets the field map, a multi-line one
     * additionally gets Enter (line break), the up/down arrows and page up/down.
     *
     * @param c         the component to bind
     * @param multiline true for a {@code JTextArea}-like component
     */
    static void installDefaultBindings(JTextComponent c, boolean multiline) {
        InputMap im = c.getInputMap();
        ActionMap am = c.getActionMap();
        Action[] actions = DEFAULT_ACTIONS;
        for (int i = 0; i < actions.length; i++) {
            Object name = actions[i].getValue(Action.NAME);
            if (name != null) {
                am.put(name.toString(), actions[i]);
            }
        }

        int ctrl = java.awt.event.InputEvent.CTRL_DOWN_MASK;
        int shift = java.awt.event.InputEvent.SHIFT_DOWN_MASK;

        put(im, KeyStroke.getKeyStroke(KeyEvent.VK_C, ctrl), copyAction);
        put(im, KeyStroke.getKeyStroke(KeyEvent.VK_V, ctrl), pasteAction);
        put(im, KeyStroke.getKeyStroke(KeyEvent.VK_X, ctrl), cutAction);
        put(im, KeyStroke.getKeyStroke(KeyEvent.VK_INSERT, ctrl), copyAction);
        put(im, KeyStroke.getKeyStroke(KeyEvent.VK_INSERT, shift), pasteAction);
        put(im, KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, shift), cutAction);
        put(im, KeyStroke.getKeyStroke(KeyEvent.VK_A, ctrl), selectAllAction);

        put(im, KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, shift), selectionBackwardAction);
        put(im, KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, shift), selectionForwardAction);
        put(im, KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, ctrl), previousWordAction);
        put(im, KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, ctrl), nextWordAction);
        put(im, KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, ctrl | shift), selectionPreviousWordAction);
        put(im, KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, ctrl | shift), selectionNextWordAction);

        put(im, KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0), backwardAction);
        put(im, KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), forwardAction);
        put(im, KeyStroke.getKeyStroke(KeyEvent.VK_HOME, 0), beginLineAction);
        put(im, KeyStroke.getKeyStroke(KeyEvent.VK_END, 0), endLineAction);
        put(im, KeyStroke.getKeyStroke(KeyEvent.VK_HOME, shift), selectionBeginLineAction);
        put(im, KeyStroke.getKeyStroke(KeyEvent.VK_END, shift), selectionEndLineAction);
        put(im, KeyStroke.getKeyStroke(KeyEvent.VK_HOME, ctrl), beginAction);
        put(im, KeyStroke.getKeyStroke(KeyEvent.VK_END, ctrl), endAction);

        put(im, KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, 0), deletePrevCharAction);
        put(im, KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, shift), deletePrevCharAction);
        put(im, KeyStroke.getKeyStroke(KeyEvent.VK_H, ctrl), deletePrevCharAction);
        put(im, KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), deleteNextCharAction);
        put(im, KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, ctrl), deletePrevWordAction);
        put(im, KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, ctrl), deleteNextWordAction);

        if (multiline) {
            put(im, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), insertBreakAction);
            put(im, KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), upAction);
            put(im, KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), downAction);
            put(im, KeyStroke.getKeyStroke(KeyEvent.VK_UP, shift), selectionUpAction);
            put(im, KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, shift), selectionDownAction);
            put(im, KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_UP, 0), pageUpAction);
            put(im, KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_DOWN, 0), pageDownAction);
        }
    }

    /** Binds {@code ks} to {@code actionName} when the stroke could be built. */
    private static void put(InputMap im, KeyStroke ks, String actionName) {
        if (ks != null) {
            im.put(ks, actionName);
        }
    }

    // ------------------------------------------------------------- actions

    /** Beeps when the action cannot do anything (the port's error feedback). */
    static void beep() {
        try {
            Toolkit.getDefaultToolkit().beep();
        } catch (Throwable ignored) {
            // A port with no audio still has to keep the editor working.
        }
    }

    private static boolean editable(JTextComponent target) {
        return target != null && target.isEditable() && target.isEnabled();
    }

    /** The action that inserts a typed character. */
    public static class DefaultKeyTypedAction extends TextAction {

        public DefaultKeyTypedAction() {
            super(defaultKeyTypedAction);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            JTextComponent target = getTextComponent(e);
            if (target == null || e == null) {
                return;
            }
            if (!editable(target)) {
                return;
            }
            String content = e.getActionCommand();
            if (content == null || content.isEmpty()) {
                return;
            }
            char c = content.charAt(0);
            // Control characters are handled by their own key bindings, not by typing.
            if (c >= 0x20 && c != 0x7F) {
                target.replaceSelection(content);
            }
        }
    }

    /** The action that inserts the action event's command string. */
    public static class InsertContentAction extends TextAction {

        public InsertContentAction() {
            super(insertContentAction);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            JTextComponent target = getTextComponent(e);
            if (target == null) {
                return;
            }
            if (!editable(target)) {
                beep();
                return;
            }
            String content = e == null ? null : e.getActionCommand();
            if (content != null) {
                target.replaceSelection(content);
            } else {
                beep();
            }
        }
    }

    /** The action that inserts a line break. */
    public static class InsertBreakAction extends TextAction {

        public InsertBreakAction() {
            super(insertBreakAction);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            JTextComponent target = getTextComponent(e);
            if (target == null) {
                return;
            }
            if (!editable(target)) {
                beep();
                return;
            }
            target.replaceSelection("\n");
        }
    }

    /** The action that inserts a tab character. */
    public static class InsertTabAction extends TextAction {

        public InsertTabAction() {
            super(insertTabAction);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            JTextComponent target = getTextComponent(e);
            if (target == null) {
                return;
            }
            if (!editable(target)) {
                beep();
                return;
            }
            target.replaceSelection("\t");
        }
    }

    /** The action that deletes the character (or selection) before the caret. */
    public static class DeletePrevCharAction extends TextAction {

        public DeletePrevCharAction() {
            super(deletePrevCharAction);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            JTextComponent target = getTextComponent(e);
            boolean beep = true;
            if (target != null && target.isEditable()) {
                try {
                    Document doc = target.getDocument();
                    Caret caret = target.getCaret();
                    int dot = caret.getDot();
                    int mark = caret.getMark();
                    if (dot != mark) {
                        doc.remove(Math.min(dot, mark), Math.abs(dot - mark));
                        beep = false;
                    } else if (dot > 0) {
                        int delChars = 1;
                        if (dot > 1) {
                            // A surrogate pair is one character to the user.
                            String dotChars = doc.getText(dot - 2, 2);
                            char c0 = dotChars.charAt(0);
                            char c1 = dotChars.charAt(1);
                            if (c0 >= '\uD800' && c0 <= '\uDBFF' && c1 >= '\uDC00' && c1 <= '\uDFFF') {
                                delChars = 2;
                            }
                        }
                        doc.remove(dot - delChars, delChars);
                        beep = false;
                    }
                } catch (BadLocationException ignored) {
                    // Falls through to the beep, as in the JDK.
                }
            }
            if (beep) {
                beep();
            }
        }
    }

    /** The action that deletes the character (or selection) after the caret. */
    public static class DeleteNextCharAction extends TextAction {

        public DeleteNextCharAction() {
            super(deleteNextCharAction);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            JTextComponent target = getTextComponent(e);
            boolean beep = true;
            if (target != null && target.isEditable()) {
                try {
                    Document doc = target.getDocument();
                    Caret caret = target.getCaret();
                    int dot = caret.getDot();
                    int mark = caret.getMark();
                    if (dot != mark) {
                        doc.remove(Math.min(dot, mark), Math.abs(dot - mark));
                        beep = false;
                    } else if (dot < doc.getLength()) {
                        int delChars = 1;
                        if (dot < doc.getLength() - 1) {
                            String dotChars = doc.getText(dot, 2);
                            char c0 = dotChars.charAt(0);
                            char c1 = dotChars.charAt(1);
                            if (c0 >= '\uD800' && c0 <= '\uDBFF' && c1 >= '\uDC00' && c1 <= '\uDFFF') {
                                delChars = 2;
                            }
                        }
                        doc.remove(dot, delChars);
                        beep = false;
                    }
                } catch (BadLocationException ignored) {
                    // Falls through to the beep, as in the JDK.
                }
            }
            if (beep) {
                beep();
            }
        }
    }

    /** The action that deletes the word before or after the caret. */
    public static class DeleteWordAction extends TextAction {
        private final boolean next;

        public DeleteWordAction(String name, boolean next) {
            super(name);
            this.next = next;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            JTextComponent target = getTextComponent(e);
            if (target == null || !target.isEditable()) {
                beep();
                return;
            }
            Caret caret = target.getCaret();
            int dot = caret.getDot();
            int mark = caret.getMark();
            if (dot != mark) {
                try {
                    target.getDocument().remove(Math.min(dot, mark), Math.abs(dot - mark));
                } catch (BadLocationException ignored) {
                    beep();
                }
                return;
            }
            int from = next ? dot : target.wordStart(dot);
            int to = next ? target.wordEnd(dot) : dot;
            if (from == to) {
                beep();
                return;
            }
            try {
                target.getDocument().remove(from, to - from);
                target.setCaretPosition(from);
            } catch (BadLocationException ignored) {
                beep();
            }
        }
    }

    /** The action that makes a component uneditable. */
    public static class ReadOnlyAction extends TextAction {

        public ReadOnlyAction() {
            super(readOnlyAction);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            JTextComponent target = getTextComponent(e);
            if (target != null) {
                target.setEditable(false);
            }
        }
    }

    /** The action that makes a component editable. */
    public static class WritableAction extends TextAction {

        public WritableAction() {
            super(writableAction);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            JTextComponent target = getTextComponent(e);
            if (target != null) {
                target.setEditable(true);
            }
        }
    }

    /** The action that copies the selection to the clipboard. */
    public static class CopyAction extends TextAction {

        public CopyAction() {
            super(copyAction);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            JTextComponent target = getTextComponent(e);
            if (target != null) {
                target.copy();
            }
        }
    }

    /** The action that moves the selection to the clipboard and deletes it. */
    public static class CutAction extends TextAction {

        public CutAction() {
            super(cutAction);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            JTextComponent target = getTextComponent(e);
            if (target != null) {
                target.cut();
            }
        }
    }

    /** The action that inserts the clipboard's text. */
    public static class PasteAction extends TextAction {

        public PasteAction() {
            super(pasteAction);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            JTextComponent target = getTextComponent(e);
            if (target != null) {
                target.paste();
            }
        }
    }

    /** The action that beeps. */
    public static class BeepAction extends TextAction {

        public BeepAction() {
            super(beepAction);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            beep();
        }
    }

    /** The action that moves the caret (or extends the selection) by one position. */
    public static class NextVisualPositionAction extends TextAction {
        private final boolean select;
        private final int direction;

        public NextVisualPositionAction(String name, boolean select, int direction) {
            super(name);
            this.select = select;
            this.direction = direction;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            JTextComponent target = getTextComponent(e);
            if (target == null) {
                return;
            }
            int dot = target.getCaret().getDot();
            int pos;
            switch (direction) {
                case javax.swing.SwingConstants.WEST:
                    pos = dot - 1;
                    break;
                case javax.swing.SwingConstants.NORTH:
                    pos = target.offsetAbove(dot);
                    break;
                case javax.swing.SwingConstants.SOUTH:
                    pos = target.offsetBelow(dot);
                    break;
                case javax.swing.SwingConstants.EAST:
                default:
                    pos = dot + 1;
                    break;
            }
            target.moveCaretTo(pos, select);
        }
    }

    /** The action that moves the caret to the start of the current word. */
    public static class BeginWordAction extends TextAction {
        private final boolean select;

        public BeginWordAction(String name, boolean select) {
            super(name);
            this.select = select;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            JTextComponent target = getTextComponent(e);
            if (target != null) {
                target.moveCaretTo(target.wordStart(target.getCaret().getDot()), select);
            }
        }
    }

    /** The action that moves the caret past the end of the current word. */
    public static class EndWordAction extends TextAction {
        private final boolean select;

        public EndWordAction(String name, boolean select) {
            super(name);
            this.select = select;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            JTextComponent target = getTextComponent(e);
            if (target != null) {
                target.moveCaretTo(target.wordEnd(target.getCaret().getDot()), select);
            }
        }
    }

    /** The action that moves the caret to the start of the previous word. */
    public static class PreviousWordAction extends TextAction {
        private final boolean select;

        public PreviousWordAction(String name, boolean select) {
            super(name);
            this.select = select;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            JTextComponent target = getTextComponent(e);
            if (target != null) {
                target.moveCaretTo(target.previousWord(target.getCaret().getDot()), select);
            }
        }
    }

    /** The action that moves the caret to the start of the next word. */
    public static class NextWordAction extends TextAction {
        private final boolean select;

        public NextWordAction(String name, boolean select) {
            super(name);
            this.select = select;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            JTextComponent target = getTextComponent(e);
            if (target != null) {
                target.moveCaretTo(target.nextWord(target.getCaret().getDot()), select);
            }
        }
    }

    /** The action that moves the caret to the start of the visual line. */
    public static class BeginLineAction extends TextAction {
        private final boolean select;

        public BeginLineAction(String name, boolean select) {
            super(name);
            this.select = select;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            JTextComponent target = getTextComponent(e);
            if (target != null) {
                target.moveCaretTo(target.visualLineStart(target.getCaret().getDot()), select);
            }
        }
    }

    /** The action that moves the caret to the end of the visual line. */
    public static class EndLineAction extends TextAction {
        private final boolean select;

        public EndLineAction(String name, boolean select) {
            super(name);
            this.select = select;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            JTextComponent target = getTextComponent(e);
            if (target != null) {
                target.moveCaretTo(target.visualLineEnd(target.getCaret().getDot()), select);
            }
        }
    }

    /** The action that moves the caret to the start of the paragraph (the logical line). */
    public static class BeginParagraphAction extends TextAction {
        private final boolean select;

        public BeginParagraphAction(String name, boolean select) {
            super(name);
            this.select = select;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            JTextComponent target = getTextComponent(e);
            if (target != null) {
                target.moveCaretTo(target.lineStart(target.getCaret().getDot()), select);
            }
        }
    }

    /** The action that moves the caret to the end of the paragraph (the logical line). */
    public static class EndParagraphAction extends TextAction {
        private final boolean select;

        public EndParagraphAction(String name, boolean select) {
            super(name);
            this.select = select;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            JTextComponent target = getTextComponent(e);
            if (target != null) {
                target.moveCaretTo(target.lineEnd(target.getCaret().getDot()), select);
            }
        }
    }

    /** The action that moves the caret to the start of the document. */
    public static class BeginAction extends TextAction {
        private final boolean select;

        public BeginAction(String name, boolean select) {
            super(name);
            this.select = select;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            JTextComponent target = getTextComponent(e);
            if (target != null) {
                target.moveCaretTo(0, select);
            }
        }
    }

    /** The action that moves the caret to the end of the document. */
    public static class EndAction extends TextAction {
        private final boolean select;

        public EndAction(String name, boolean select) {
            super(name);
            this.select = select;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            JTextComponent target = getTextComponent(e);
            if (target != null) {
                target.moveCaretTo(target.getDocument().getLength(), select);
            }
        }
    }

    /** The action that selects the whole document. */
    public static class SelectAllAction extends TextAction {

        public SelectAllAction() {
            super(selectAllAction);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            JTextComponent target = getTextComponent(e);
            if (target != null) {
                target.setCaretPosition(0);
                target.moveCaretPosition(target.getDocument().getLength());
            }
        }
    }

    /** The action that moves the caret up or down a page. */
    public static class VerticalPageAction extends TextAction {
        private final int direction;
        private final boolean select;

        public VerticalPageAction(String name, int direction, boolean select) {
            super(name);
            this.direction = direction;
            this.select = select;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            JTextComponent target = getTextComponent(e);
            if (target == null) {
                return;
            }
            int lines = target.visibleLineCount();
            int dot = target.getCaret().getDot();
            int pos = direction < 0 ? target.offsetAbove(dot, lines) : target.offsetBelow(dot, lines);
            target.moveCaretTo(pos, select);
        }
    }

    // ---------------------------------------------------------- text I/O

    @Override
    public void read(InputStream in, Document doc, int pos) throws IOException, BadLocationException {
        read(new java.io.InputStreamReader(in, "UTF-8"), doc, pos);
    }

    @Override
    public void read(Reader in, Document doc, int pos) throws IOException, BadLocationException {
        char[] buf = new char[4096];
        int n;
        int offset = pos;
        StringBuilder all = new StringBuilder();
        while ((n = in.read(buf)) > 0) {
            all.append(buf, 0, n);
        }
        doc.insertString(offset, all.toString(), null);
    }

    @Override
    public void write(OutputStream out, Document doc, int pos, int len) throws IOException, BadLocationException {
        write(new java.io.OutputStreamWriter(out, "UTF-8"), doc, pos, len);
    }

    @Override
    public void write(Writer out, Document doc, int pos, int len) throws IOException, BadLocationException {
        out.write(doc.getText(pos, len));
        out.flush();
    }
}
