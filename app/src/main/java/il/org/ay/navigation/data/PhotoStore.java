package il.org.ay.navigation.data;

import android.content.Context;
import android.os.Environment;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import il.org.ay.navigation.model.PhotoTask;

/**
 * T20a: the building photos on this phone, one folder per label (decision #17: they are
 * not uploaded anywhere). The folder is the app's own Pictures folder, so no storage
 * permission is needed, and the photos are removed with the app.
 *
 * <p>{@link #exportZip} packs them for the training notebook (T21):
 * {@code dataset/<label>/*.jpg}, {@code labels.txt} and {@code tasks.csv}.
 */
public class PhotoStore {

    private final File root;

    public PhotoStore(@NonNull File root) {
        this.root = root;
    }

    public static PhotoStore get(@NonNull Context context) {
        File pictures = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        if (pictures == null) {
            pictures = new File(context.getFilesDir(), "pictures");   // no external storage
        }
        return new PhotoStore(new File(pictures, "dataset"));
    }

    /**
     * A new, empty file for the next photo of this label. The name carries the time,
     * so photos from one walk can be kept together when splitting train/test (SDD 10, step 2).
     */
    @NonNull
    public File newPhoto(@NonNull String label) throws IOException {
        File dir = new File(root, label);
        if (!dir.isDirectory() && !dir.mkdirs()) {
            throw new IOException("cannot create " + dir);
        }
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.ROOT).format(new Date());
        File file = new File(dir, label + "_" + stamp + ".jpg");
        int n = 1;
        while (file.exists()) {
            file = new File(dir, label + "_" + stamp + "_" + n++ + ".jpg");
        }
        return file;
    }

    /** Photos of this label on this phone. */
    public int count(@NonNull String label) {
        return photos(new File(root, label)).size();
    }

    /** All photos on this phone. */
    public int total() {
        int sum = 0;
        for (File dir : labelDirs()) {
            sum += photos(dir).size();
        }
        return sum;
    }

    /**
     * Writes every photo to a zip, with labels.txt (one label per line: the tasks in their
     * order, then any folder without a task) and tasks.csv. Returns how many photos went in.
     */
    public int exportZip(@NonNull File zip, @NonNull List<PhotoTask> tasks) throws IOException {
        List<String> labels = new ArrayList<>();
        StringBuilder csv = new StringBuilder("label,name,target,photos_in_zip,note\n");
        for (PhotoTask t : tasks) {
            labels.add(t.label);
            csv.append(t.label).append(',').append(csvField(t.name)).append(',').append(t.target)
                    .append(',').append(count(t.label)).append(',').append(csvField(t.note)).append('\n');
        }
        for (File dir : labelDirs()) {
            if (!labels.contains(dir.getName())) {
                labels.add(dir.getName());   // a task deleted since: keep its photos
            }
        }
        int written = 0;
        try (ZipOutputStream out = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(zip)))) {
            out.setLevel(Deflater.NO_COMPRESSION);   // JPEG is compressed already
            for (String label : labels) {
                for (File photo : photos(new File(root, label))) {
                    out.putNextEntry(new ZipEntry("dataset/" + label + "/" + photo.getName()));
                    copy(photo, out);
                    out.closeEntry();
                    written++;
                }
            }
            out.putNextEntry(new ZipEntry("labels.txt"));
            out.write((String.join("\n", labels) + "\n").getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
            out.putNextEntry(new ZipEntry("tasks.csv"));
            // a BOM, so Excel opens the Hebrew names correctly
            out.write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});   // UTF-8 BOM
            out.write(csv.toString().getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
        return written;
    }

    /** Removes every photo from this phone (after they were exported). Returns how many. */
    public int deleteAll() {
        int deleted = 0;
        for (File dir : labelDirs()) {
            for (File photo : photos(dir)) {
                if (photo.delete()) {
                    deleted++;
                }
            }
            //noinspection ResultOfMethodCallIgnored
            dir.delete();
        }
        return deleted;
    }

    /** Removes one photo, e.g. one that came out blurred. */
    public boolean delete(@Nullable File photo) {
        return photo != null && photo.getParentFile() != null
                && photo.getParentFile().getParentFile() != null
                && photo.getParentFile().getParentFile().equals(root) && photo.delete();
    }

    private List<File> labelDirs() {
        File[] dirs = root.listFiles(File::isDirectory);
        if (dirs == null) {
            return new ArrayList<>();
        }
        Arrays.sort(dirs);
        return Arrays.asList(dirs);
    }

    private static List<File> photos(File dir) {
        File[] files = dir.listFiles((d, name) -> name.endsWith(".jpg") && new File(d, name).length() > 0);
        if (files == null) {
            return new ArrayList<>();
        }
        Arrays.sort(files);
        return Arrays.asList(files);
    }

    private static void copy(File from, OutputStream to) throws IOException {
        byte[] buffer = new byte[64 * 1024];
        try (InputStream in = new FileInputStream(from)) {
            int n;
            while ((n = in.read(buffer)) > 0) {
                to.write(buffer, 0, n);
            }
        }
    }

    private static String csvField(@Nullable String value) {
        if (value == null) {
            return "";
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
