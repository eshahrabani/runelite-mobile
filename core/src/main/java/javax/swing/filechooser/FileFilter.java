package javax.swing.filechooser;

import java.io.File;

/**
 * Hand-written {@code javax.swing.filechooser.FileFilter}.
 *
 * <p>The port never opens a real file chooser (see {@link javax.swing.JFileChooser}), so
 * this type exists so that the client can build filters and hand them to a chooser. The
 * abstract members are spelled out with the desktop signatures: a {@link java.io.File}
 * predicate and a description, which is what a caller that keeps its own chooser needs.
 */
public abstract class FileFilter {

    /** True when {@code f} is selectable under this filter. */
    public abstract boolean accept(File f);

    /** Human-readable description of the accepted files. */
    public abstract String getDescription();
}
