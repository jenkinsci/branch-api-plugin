package jenkins.branch;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.File;
import java.io.IOException;

/**
 * Copy of hudson.util.io.RewindableRotatingFileOutputStream that supports an initial append mode.
 */
// TODO replace with hudson.util.io.RewindableFileOutputStream once baseline core has version supporting initial append
class RewindableRotatingFileOutputStream extends RewindableFileOutputStream {
    /**
     * Number of log files to keep.
     */
    private final int size;

    public RewindableRotatingFileOutputStream(File out, int size) {
        super(out);
        this.size = size;
    }

    public RewindableRotatingFileOutputStream(File out, boolean initialAppend, int size) {
        super(out, initialAppend);
        this.size = size;
    }

    protected File getNumberedFileName(int n) {
        if (n == 0) return out;
        return new File(out.getPath() + "." + n);
    }

    @Override
    @SuppressFBWarnings("RV_RETURN_VALUE_IGNORED_BAD_PRACTICE")
    public void rewind() throws IOException {
        super.rewind();
        for (int i = size - 1; i >= 0; i--) {
            File fi = getNumberedFileName(i);
            if (fi.exists()) {
                File next = getNumberedFileName(i + 1);
                next.delete();
                fi.renameTo(next);
            }
        }
    }

    /**
     * Deletes all the log files, including rotated files.
     */
    @SuppressFBWarnings("RV_RETURN_VALUE_IGNORED_BAD_PRACTICE")
    public void deleteAll() {
        for (int i = 0; i <= size; i++) {
            getNumberedFileName(i).delete();
        }
    }
}
