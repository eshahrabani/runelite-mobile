package javax.swing;

import java.awt.Component;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.swing.filechooser.FileFilter;

import org.runelite.mobile.bridge.UiBridge;

/**
 * Hand-written {@code javax.swing.JFileChooser} for this port.
 *
 * <p>Android has no file-system picker the core module could drive (it cannot see
 * {@code android.content.Intent}), so this is a <em>record-only</em> chooser: every
 * configuration call is stored (so RuneLite's profile import/export and
 * {@code Filepath.Chooser} can build one without a null dereference) and every
 * {@code showXxxDialog} reports the feature as unsupported — a toast — and returns
 * {@link #CANCEL_OPTION}, which is what those call sites treat as "user cancelled".
 *
 * <p>{@link #getSelectedFile()} is therefore always {@code null} and
 * {@link #getSelectedFiles()} an empty array.
 */
public class JFileChooser extends JComponent implements javax.accessibility.Accessible {

    public static final int OPEN_DIALOG = 0;
    public static final int SAVE_DIALOG = 1;
    public static final int APPROVE_OPTION = 0;
    public static final int CANCEL_OPTION = 1;
    public static final int ERROR_OPTION = -1;
    public static final int FILES_ONLY = 0;
    public static final int DIRECTORIES_ONLY = 1;
    public static final int FILES_AND_DIRECTORIES = 2;

    /** Feature name reported to the user (and logged once) by {@link UiBridge}. */
    private static final String UNSUPPORTED = "file chooser";

    private String dialogTitle;
    private int dialogType = OPEN_DIALOG;
    private int fileSelectionMode = FILES_ONLY;
    private boolean fileHidingEnabled = true;
    private boolean multiSelectionEnabled;
    private boolean acceptAllFileFilterUsed = true;
    private File currentDirectory;
    private File selectedFile;
    private File[] selectedFiles;
    private FileFilter fileFilter;
    private final List<FileFilter> choosableFilters = new ArrayList<>();

    public JFileChooser() {
        super();
        setOpaque(true);
    }

    // ------------------------------------------------------------ stored configuration

    public void setDialogTitle(String dialogTitle) {
        this.dialogTitle = dialogTitle;
    }

    public String getDialogTitle() {
        return dialogTitle;
    }

    public void setDialogType(int dialogType) {
        this.dialogType = dialogType;
    }

    public int getDialogType() {
        return dialogType;
    }

    public void setFileSelectionMode(int mode) {
        this.fileSelectionMode = mode;
    }

    public int getFileSelectionMode() {
        return fileSelectionMode;
    }

    public void setFileHidingEnabled(boolean b) {
        this.fileHidingEnabled = b;
    }

    public boolean isFileHidingEnabled() {
        return fileHidingEnabled;
    }

    public void setMultiSelectionEnabled(boolean b) {
        this.multiSelectionEnabled = b;
    }

    public boolean isMultiSelectionEnabled() {
        return multiSelectionEnabled;
    }

    public void setAcceptAllFileFilterUsed(boolean b) {
        this.acceptAllFileFilterUsed = b;
    }

    public boolean isAcceptAllFileFilterUsed() {
        return acceptAllFileFilterUsed;
    }

    public File getCurrentDirectory() {
        return currentDirectory;
    }

    public void setCurrentDirectory(File dir) {
        this.currentDirectory = dir;
    }

    public void setSelectedFile(File file) {
        this.selectedFile = file;
        this.selectedFiles = file == null ? null : new File[] {file};
    }

    /** Always {@code null}: no picker ever runs, so nothing can be selected. */
    public File getSelectedFile() {
        return null;
    }

    /** Always empty (never {@code null} — callers iterate the result). */
    public File[] getSelectedFiles() {
        return selectedFiles == null || selectedFiles.length == 0
                ? new File[0]
                : selectedFiles.clone();
    }

    public void setFileFilter(FileFilter filter) {
        this.fileFilter = filter;
    }

    public FileFilter getFileFilter() {
        return fileFilter;
    }

    public void addChoosableFileFilter(FileFilter filter) {
        if (filter != null) {
            choosableFilters.add(filter);
        }
    }

    public boolean removeChoosableFileFilter(FileFilter filter) {
        return choosableFilters.remove(filter);
    }

    public FileFilter[] getChoosableFileFilters() {
        return choosableFilters.toArray(new FileFilter[0]);
    }

    // ------------------------------------------------------------ showing

    /** Reports the feature as unsupported and returns {@link #CANCEL_OPTION}. */
    public int showDialog(Component parent, String approveButtonText) {
        UiBridge.notifyUnsupported(UNSUPPORTED);
        return CANCEL_OPTION;
    }

    /** Reports the feature as unsupported and returns {@link #CANCEL_OPTION}. */
    public int showOpenDialog(Component parent) {
        setDialogType(OPEN_DIALOG);
        return showDialog(parent, null);
    }

    /** Reports the feature as unsupported and returns {@link #CANCEL_OPTION}. */
    public int showSaveDialog(Component parent) {
        setDialogType(SAVE_DIALOG);
        return showDialog(parent, null);
    }
}
