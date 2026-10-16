package javax.swing;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dialog;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.concurrent.CountDownLatch;

import org.runelite.mobile.bridge.SwingEdt;

/**
 * Hand-written {@code javax.swing.JOptionPane}, the blocking dialogs RuneLite uses for
 * confirmations, notices and text prompts.
 *
 * <p>The desktop implementation delegates its whole visual layout to
 * {@code BasicOptionPaneUI}, which this port does not have. So the pane builds its own
 * children — message label (or the message component), optional icon, optional text
 * field and one button per option — with a {@link BorderLayout}, and the buttons carry
 * the desktop's value semantics: with an explicit {@code options} array the chosen
 * option object is the value, with an {@code optionType} the option's index is (except
 * {@code OK_CANCEL_OPTION}'s second button, which is {@link #CANCEL_OPTION}).
 *
 * <p><b>Blocking.</b> {@code showXxxDialog} creates a modal {@link JDialog} holding this
 * pane, makes it visible in the port's window layer and then <em>pumps the event queue
 * on the Swing dispatch thread</em>
 * ({@link SwingEdt#pumpUntil(java.util.function.BooleanSupplier)}) until the dialog is
 * hidden. Input keeps flowing — the app forwards taps and the pump dispatches them to
 * the dialog's buttons — while the caller's frame is suspended, exactly like
 * {@code EventQueue}'s nested loop on the desktop. Because that caller is the Swing
 * thread (RuneLite calls these from plugin code on the EDT) and not the Android main
 * thread, the app itself keeps rendering. A caller off the dispatch thread is marshalled
 * onto it and blocks on a latch instead.
 *
 * <p>Simplifications, all documented: {@code messageType} selects no icon (the port has
 * no standard icon set) but is stored and reported; an option that is a
 * {@code Component} is rendered by its {@code toString}; {@code selectionValues} in
 * {@code showInputDialog} is ignored (always a text field); {@code UNINITIALIZED_VALUE}
 * is the initial value of both {@code value} and {@code inputValue}, as in the JDK.
 */
public class JOptionPane extends JComponent implements javax.accessibility.Accessible {

    public static final Object UNINITIALIZED_VALUE = "uninitializedValue";

    public static final int DEFAULT_OPTION = -1;
    public static final int YES_NO_OPTION = 0;
    public static final int YES_NO_CANCEL_OPTION = 1;
    public static final int OK_CANCEL_OPTION = 2;

    public static final int YES_OPTION = 0;
    public static final int NO_OPTION = 1;
    public static final int CANCEL_OPTION = 2;
    public static final int OK_OPTION = 0;
    public static final int CLOSED_OPTION = -1;

    public static final int ERROR_MESSAGE = 0;
    public static final int INFORMATION_MESSAGE = 1;
    public static final int WARNING_MESSAGE = 2;
    public static final int QUESTION_MESSAGE = 3;
    public static final int PLAIN_MESSAGE = -1;

    public static final String ICON_PROPERTY = "icon";
    public static final String MESSAGE_PROPERTY = "message";
    public static final String VALUE_PROPERTY = "value";
    public static final String OPTIONS_PROPERTY = "options";
    public static final String INITIAL_VALUE_PROPERTY = "initialValue";
    public static final String MESSAGE_TYPE_PROPERTY = "messageType";
    public static final String OPTION_TYPE_PROPERTY = "optionType";
    public static final String SELECTION_VALUES_PROPERTY = "selectionValues";
    public static final String INITIAL_SELECTION_VALUE_PROPERTY = "initialSelectionValue";
    public static final String INPUT_VALUE_PROPERTY = "inputValue";
    public static final String WANTS_INPUT_PROPERTY = "wantsInput";

    private Object message;
    private int messageType = PLAIN_MESSAGE;
    private int optionType = DEFAULT_OPTION;
    private Icon icon;
    private Object[] options;
    private Object initialValue;
    private Object value = UNINITIALIZED_VALUE;
    private Object inputValue = UNINITIALIZED_VALUE;
    private Object[] selectionValues;
    private Object initialSelectionValue;
    private boolean wantsInput;
    /** The text field when {@link #wantsInput}, else null. */
    private JTextField inputComponent;
    /** Set when a button was pressed, so a later WINDOW_CLOSING must not clear the value. */
    private boolean chosen;

    public JOptionPane() {
        this(null, PLAIN_MESSAGE);
    }

    public JOptionPane(Object message) {
        this(message, PLAIN_MESSAGE);
    }

    public JOptionPane(Object message, int messageType) {
        this(message, messageType, DEFAULT_OPTION);
    }

    public JOptionPane(Object message, int messageType, int optionType) {
        this(message, messageType, optionType, null);
    }

    public JOptionPane(Object message, int messageType, int optionType, Icon icon) {
        this(message, messageType, optionType, icon, null);
    }

    public JOptionPane(Object message, int messageType, int optionType, Icon icon, Object[] options) {
        this(message, messageType, optionType, icon, options, null);
    }

    public JOptionPane(Object message, int messageType, int optionType, Icon icon, Object[] options,
                       Object initialValue) {
        super();
        this.message = message;
        this.messageType = messageType;
        this.optionType = optionType;
        this.icon = icon;
        this.options = options;
        this.initialValue = initialValue;
        setOpaque(true);
    }

    // ------------------------------------------------------------ properties

    public Object getMessage() {
        return message;
    }

    public void setMessage(Object newMessage) {
        Object old = message;
        message = newMessage;
        firePropertyChange(MESSAGE_PROPERTY, old, newMessage);
    }

    public int getMessageType() {
        return messageType;
    }

    public void setMessageType(int newType) {
        int old = messageType;
        messageType = newType;
        firePropertyChange(MESSAGE_TYPE_PROPERTY, old, newType);
    }

    public int getOptionType() {
        return optionType;
    }

    public void setOptionType(int newType) {
        int old = optionType;
        optionType = newType;
        firePropertyChange(OPTION_TYPE_PROPERTY, old, newType);
    }

    public Icon getIcon() {
        return icon;
    }

    public void setIcon(Icon newIcon) {
        Icon old = icon;
        icon = newIcon;
        firePropertyChange(ICON_PROPERTY, old, newIcon);
    }

    /** The explicit options, or the ones {@link #getOptionType()} implies. */
    public Object[] getOptions() {
        if (options != null) {
            return options;
        }
        switch (optionType) {
            case YES_NO_OPTION:
                return new Object[] {"Yes", "No"};
            case YES_NO_CANCEL_OPTION:
                return new Object[] {"Yes", "No", "Cancel"};
            case OK_CANCEL_OPTION:
                return new Object[] {"OK", "Cancel"};
            default:
                return new Object[] {"OK"};
        }
    }

    public void setOptions(Object[] newOptions) {
        Object[] old = options;
        options = newOptions;
        firePropertyChange(OPTIONS_PROPERTY, old, newOptions);
    }

    public Object getInitialValue() {
        return initialValue;
    }

    public void setInitialValue(Object newInitialValue) {
        Object old = initialValue;
        initialValue = newInitialValue;
        firePropertyChange(INITIAL_VALUE_PROPERTY, old, newInitialValue);
    }

    public Object getValue() {
        return value;
    }

    /** Sets the chosen value and notifies listeners (the JDK's value property). */
    public void setValue(Object newValue) {
        Object old = value;
        value = newValue;
        if (newValue == UNINITIALIZED_VALUE) {
            return;
        }
        firePropertyChange(VALUE_PROPERTY, old, newValue);
    }

    public boolean getWantsInput() {
        return wantsInput;
    }

    public void setWantsInput(boolean newValue) {
        boolean old = wantsInput;
        wantsInput = newValue;
        firePropertyChange(WANTS_INPUT_PROPERTY, old, newValue);
    }

    public Object getInputValue() {
        return inputValue;
    }

    public void setInputValue(Object newValue) {
        Object old = inputValue;
        inputValue = newValue;
        firePropertyChange(INPUT_VALUE_PROPERTY, old, newValue);
    }

    public Object[] getSelectionValues() {
        return selectionValues;
    }

    /**
     * Stored but not rendered: the port's input dialog is always a text field, so there
     * is no list/combo to choose from.
     */
    public void setSelectionValues(Object[] newValues) {
        Object[] old = selectionValues;
        selectionValues = newValues;
        firePropertyChange(SELECTION_VALUES_PROPERTY, old, newValues);
        if (newValues != null && newValues.length > 0) {
            setInitialSelectionValue(newValues[0]);
        }
    }

    public Object getInitialSelectionValue() {
        return initialSelectionValue;
    }

    public void setInitialSelectionValue(Object newValue) {
        Object old = initialSelectionValue;
        initialSelectionValue = newValue;
        firePropertyChange(INITIAL_SELECTION_VALUE_PROPERTY, old, newValue);
    }

    /** Asks for the initial value's button's focus; taps do not need focus on this port. */
    public void selectInitialValue() {
    }

    // ------------------------------------------------------------ building the dialog

    /**
     * A modal dialog holding this pane, packed and centred on {@code parent}.
     *
     * <p>Port behaviour: the dialog is modal and not resizable. Its value is cleared only
     * when the window is closed <em>without</em> a choice (the title bar's close glyph),
     * which is what lets {@link #showOptionDialog} report {@link #CLOSED_OPTION}.
     */
    public JDialog createDialog(Component parent, String title) {
        Window owner = parent == null ? null : SwingUtilities.getWindowAncestor(parent);
        final JDialog dialog;
        if (owner == null || owner instanceof Frame) {
            dialog = new JDialog((Frame) owner, title, true);
        } else if (owner instanceof Dialog) {
            dialog = new JDialog((Dialog) owner, title, true);
        } else {
            dialog = new JDialog(owner, title, Dialog.ModalityType.APPLICATION_MODAL);
        }
        return createDialog(parent, title, dialog);
    }

    private JDialog createDialog(Component parent, String title, final JDialog dialog) {
        setBorder(javax.swing.BorderFactory.createEmptyBorder(12, 12, 12, 12));
        Color background = UIManager.getColor("OptionPane.background");
        if (background != null) {
            setBackground(background);
        }
        buildContent(dialog);

        Container content = dialog.getContentPane();
        content.setLayout(new BorderLayout());
        content.add(this, BorderLayout.CENTER);
        dialog.setResizable(false);
        // Port deviation from the desktop: Window.setVisible(false) fires WINDOW_CLOSING
        // here, so a listener cannot simply clear the value — a button press would be
        // overwritten by its own "hide". Only a close without a choice clears it.
        dialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (!chosen) {
                    setValue(null);
                }
            }
        });
        dialog.pack();
        dialog.setLocationRelativeTo(parent);
        return dialog;
    }

    /** Builds the pane's children: message, icon, optional input field and buttons. */
    private void buildContent(final JDialog dialog) {
        removeAll();
        setLayout(new BorderLayout(10, 8));

        JComponent body = new JPanel(new BorderLayout(10, 0));
        if (icon != null) {
            body.add(new JLabel(icon), BorderLayout.WEST);
        }
        if (message instanceof Component) {
            body.add((Component) message, BorderLayout.CENTER);
        } else {
            body.add(new JLabel(message == null ? "" : String.valueOf(message)), BorderLayout.CENTER);
        }
        if (wantsInput) {
            inputComponent = new JTextField();
            if (initialSelectionValue != null) {
                inputComponent.setText(String.valueOf(initialSelectionValue));
            }
            JComponent input = new JPanel(new BorderLayout());
            if (initialValue instanceof Component) {
                input.add((Component) initialValue, BorderLayout.WEST);
            }
            input.add(inputComponent, BorderLayout.CENTER);
            body.add(input, BorderLayout.SOUTH);
        }
        add(body, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        Object[] optionList = getOptions();
        for (int i = 0; i < optionList.length; i++) {
            final int index = i;
            Component optionComponent = optionList[i] instanceof Component ? (Component) optionList[i] : null;
            if (optionComponent != null) {
                buttons.add(optionComponent);
                continue;
            }
            JButton button = new JButton(String.valueOf(optionList[i]));
            button.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    chosen = true;
                    resetInputValue(index);
                    setValue(valueForOption(index));
                    dialog.setVisible(false);
                }
            });
            buttons.add(button);
        }
        add(buttons, BorderLayout.SOUTH);
    }

    /**
     * The value a button press reports, exactly as {@code BasicOptionPaneUI} does it:
     * the explicit option object when {@code options} was given, else the option's index
     * (with the second {@code OK_CANCEL_OPTION} button mapping to {@link #CANCEL_OPTION}).
     */
    private Object valueForOption(int index) {
        if (options != null) {
            return index >= 0 && index < options.length ? options[index] : null;
        }
        if (optionType == OK_CANCEL_OPTION && index == 1) {
            return Integer.valueOf(CANCEL_OPTION);
        }
        return Integer.valueOf(index);
    }

    /** Copies the text field into the input value when the JDK's rules say to. */
    private void resetInputValue(int index) {
        if (inputComponent == null) {
            return;
        }
        boolean storeInput = options != null
                || optionType == DEFAULT_OPTION
                || ((optionType == YES_NO_OPTION || optionType == YES_NO_CANCEL_OPTION
                     || optionType == OK_CANCEL_OPTION) && index == 0);
        if (storeInput) {
            setInputValue(inputComponent.getText());
        }
    }

    // ------------------------------------------------------------ static API

    public static void showMessageDialog(Component parentComponent, Object message) {
        showMessageDialog(parentComponent, message, "Message", INFORMATION_MESSAGE);
    }

    public static void showMessageDialog(Component parentComponent, Object message, String title,
                                        int messageType) {
        JOptionPane pane = new JOptionPane(message, messageType, DEFAULT_OPTION);
        runModal(pane, parentComponent, title);
    }

    public static void showMessageDialog(Component parentComponent, Object message, String title,
                                        int messageType, Icon icon) {
        JOptionPane pane = new JOptionPane(message, messageType, DEFAULT_OPTION, icon);
        runModal(pane, parentComponent, title);
    }

    public static int showConfirmDialog(Component parentComponent, Object message) {
        return showConfirmDialog(parentComponent, message, "Select an Option", YES_NO_CANCEL_OPTION);
    }

    public static int showConfirmDialog(Component parentComponent, Object message, String title,
                                        int optionType) {
        return showConfirmDialog(parentComponent, message, title, optionType, QUESTION_MESSAGE);
    }

    public static int showConfirmDialog(Component parentComponent, Object message, String title,
                                        int optionType, int messageType) {
        return showOptionDialog(parentComponent, message, title, optionType, messageType, null, null, null);
    }

    public static int showConfirmDialog(Component parentComponent, Object message, String title,
                                        int optionType, int messageType, Icon icon) {
        return showOptionDialog(parentComponent, message, title, optionType, messageType, icon, null, null);
    }

    public static int showOptionDialog(Component parentComponent, Object message, String title,
                                       int optionType, int messageType, Icon icon, Object[] options,
                                       Object initialValue) {
        JOptionPane pane = new JOptionPane(message, messageType, optionType, icon, options, initialValue);
        Object selectedValue = runModal(pane, parentComponent, title);
        if (selectedValue == null || selectedValue == UNINITIALIZED_VALUE) {
            return CLOSED_OPTION;
        }
        if (options == null) {
            return selectedValue instanceof Integer ? ((Integer) selectedValue).intValue() : CLOSED_OPTION;
        }
        for (int i = 0; i < options.length; i++) {
            if (options[i] != null ? options[i].equals(selectedValue) : selectedValue == null) {
                return i;
            }
        }
        return CLOSED_OPTION;
    }

    public static String showInputDialog(Object message) {
        return showInputDialog(null, message);
    }

    public static String showInputDialog(Component parentComponent, Object message) {
        return showInputDialog(parentComponent, message, "Input", QUESTION_MESSAGE);
    }

    public static String showInputDialog(Component parentComponent, Object message, String title,
                                         int messageType) {
        Object result = showInputDialog(parentComponent, message, title, messageType, null, null, null);
        return result == null ? null : result.toString();
    }

    /** The JDK's full form; the result is the typed text or {@code null} when cancelled. */
    public static Object showInputDialog(Component parentComponent, Object message, String title,
                                         int messageType, Icon icon, Object[] selectionValues,
                                         Object initialSelectionValue) {
        JOptionPane pane = new JOptionPane(message, messageType, OK_CANCEL_OPTION, icon, null, null);
        pane.setWantsInput(true);
        pane.setSelectionValues(selectionValues);
        pane.setInitialSelectionValue(initialSelectionValue);
        runModal(pane, parentComponent, title);
        Object input = pane.getInputValue();
        return input == UNINITIALIZED_VALUE ? null : input;
    }

    // ------------------------------------------------------------ the nested loop

    /**
     * Shows the pane's dialog and blocks until it is hidden, returning
     * {@link #getValue()}.
     *
     * <p>On the dispatch thread this is the JDK's nested event loop: the dialog becomes
     * visible, queued events keep being dispatched (that is what delivers the user's taps
     * to the buttons) until the dialog is no longer visible, and the dialog is disposed
     * afterwards. Off the dispatch thread the same work is marshalled onto it and the
     * caller blocks on a latch.
     */
    private static Object runModal(JOptionPane pane, Component parent, String title) {
        if (SwingEdt.isDispatchThread()) {
            return runModalOnDispatchThread(pane, parent, title);
        }
        final Object[] result = new Object[1];
        final Throwable[] failure = new Throwable[1];
        final CountDownLatch latch = new CountDownLatch(1);
        final JOptionPane paneRef = pane;
        final Component parentRef = parent;
        final String titleRef = title;
        SwingEdt.post(new Runnable() {
            @Override
            public void run() {
                try {
                    result[0] = runModalOnDispatchThread(paneRef, parentRef, titleRef);
                } catch (Throwable t) {
                    failure[0] = t;
                } finally {
                    latch.countDown();
                }
            }
        });
        boolean interrupted = false;
        for (; ; ) {
            try {
                latch.await();
                break;
            } catch (InterruptedException e) {
                interrupted = true;
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
        if (failure[0] != null) {
            throw new RuntimeException("JOptionPane dialog failed", failure[0]);
        }
        return result[0];
    }

    private static Object runModalOnDispatchThread(JOptionPane pane, Component parent, String title) {
        JDialog dialog = pane.createDialog(parent, title);
        try {
            dialog.setVisible(true);
            SwingEdt.pumpUntil(new java.util.function.BooleanSupplier() {
                private final JDialog d = dialog;

                @Override
                public boolean getAsBoolean() {
                    return !d.isVisible();
                }
            });
        } finally {
            dialog.dispose();
        }
        return pane.getValue();
    }

    @Override
    public String toString() {
        return getClass().getName() + "[message=" + message + ",value=" + value + "]";
    }
}
