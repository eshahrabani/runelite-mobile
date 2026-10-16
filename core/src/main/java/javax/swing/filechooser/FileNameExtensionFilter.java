package javax.swing.filechooser;

import java.io.File;
import java.util.Locale;

/**
 * Hand-written {@code javax.swing.filechooser.FileNameExtensionFilter}: the JDK's
 * extension-list filter (RuneLite's profile import/export builds one with
 * {@code "RuneLite profiles", "properties"}).
 *
 * <p>Matches the desktop rule: directories always pass, a file passes when its extension
 * (the part after its last dot) equals one of the extensions, ignoring case.
 */
public final class FileNameExtensionFilter extends FileFilter {

    private final String description;
    private final String[] extensions;
    private final String[] lowerCaseExtensions;

    public FileNameExtensionFilter(String description, String... extensions) {
        this.description = description;
        if (extensions == null || extensions.length == 0) {
            this.extensions = new String[0];
            this.lowerCaseExtensions = new String[0];
        } else {
            this.extensions = new String[extensions.length];
            this.lowerCaseExtensions = new String[extensions.length];
            for (int i = 0; i < extensions.length; i++) {
                if (extensions[i] == null) {
                    throw new IllegalArgumentException("Extensions must be non-null and not empty");
                }
                this.extensions[i] = extensions[i];
                this.lowerCaseExtensions[i] = extensions[i].toLowerCase(Locale.ENGLISH);
            }
        }
    }

    @Override
    public boolean accept(File f) {
        if (f == null) {
            return false;
        }
        if (f.isDirectory()) {
            return true;
        }
        String fileName = f.getName();
        int dot = fileName.lastIndexOf('.');
        if (dot > 0 && dot < fileName.length() - 1) {
            String extension = fileName.substring(dot + 1).toLowerCase(Locale.ENGLISH);
            for (String candidate : lowerCaseExtensions) {
                if (extension.equals(candidate)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public String getDescription() {
        return description;
    }

    /** The extensions as given to the constructor. */
    public String[] getExtensions() {
        return extensions.clone();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(description == null ? "" : description);
        sb.append(" (");
        for (int i = 0; i < extensions.length; i++) {
            if (i > 0) {
                sb.append("; ");
            }
            sb.append("*.").append(extensions[i]);
        }
        return sb.append(')').toString();
    }
}
