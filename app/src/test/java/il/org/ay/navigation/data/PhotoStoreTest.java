package il.org.ay.navigation.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import il.org.ay.navigation.model.PhotoTask;

/** T20a: the photos on the phone, and the zip the training notebook reads (T21). */
public class PhotoStoreTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();
    private PhotoStore store;

    @Before
    public void setUp() throws IOException {
        store = new PhotoStore(new File(folder.getRoot(), "dataset"));
    }

    private File shoot(String label) throws IOException {
        File f = store.newPhoto(label);
        try (FileOutputStream out = new FileOutputStream(f)) {
            out.write(new byte[]{(byte) 0xFF, (byte) 0xD8, 1, 2, 3});   // the camera writes the JPEG
        }
        return f;
    }

    @Test
    public void eachPhoto_isANewFileInItsLabelsFolder() throws IOException {
        File a = shoot("building_01");
        File b = shoot("building_01");
        assertNotEquals(a, b);
        assertEquals("building_01", a.getParentFile().getName());
        assertTrue(a.getName().startsWith("building_01_"));
        assertEquals(2, store.count("building_01"));
        assertEquals(0, store.count("building_02"));
    }

    @Test
    public void aPhotoTheCameraNeverWrote_isNotCounted() throws IOException {
        File empty = store.newPhoto("building_01");
        assertTrue(empty.createNewFile());   // left empty, as after a failed capture
        assertEquals(0, store.count("building_01"));
        assertEquals(0, store.total());
    }

    @Test
    public void zip_hasAFolderPerLabel_labelsTxt_andTasksCsv() throws IOException {
        shoot("building_01");
        shoot("building_01");
        shoot(PhotoTask.OTHER);
        shoot("building_07");   // its task was deleted since: the photos still go in
        List<PhotoTask> tasks = Arrays.asList(
                new PhotoTask("building_01", "בניין \"המדעים\"", 40, "מהשביל, ומהחניה", 1),
                new PhotoTask("building_02", "חדר האוכל", 30, null, 2),
                new PhotoTask(PhotoTask.OTHER, "אחר", 40, null, 1000));
        File zip = folder.newFile("export.zip");
        assertEquals(4, store.exportZip(zip, tasks));

        try (ZipFile z = new ZipFile(zip)) {
            List<String> names = new ArrayList<>();
            for (ZipEntry e : java.util.Collections.list(z.entries())) {
                names.add(e.getName());
            }
            assertEquals(2, names.stream().filter(n -> n.startsWith("dataset/building_01/")).count());
            assertEquals(1, names.stream().filter(n -> n.startsWith("dataset/other/")).count());
            assertEquals(1, names.stream().filter(n -> n.startsWith("dataset/building_07/")).count());

            String labels = new String(z.getInputStream(z.getEntry("labels.txt")).readAllBytes(), StandardCharsets.UTF_8);
            // every task (even one with no photos yet), in order, then folders without a task
            assertEquals("building_01\nbuilding_02\nother\nbuilding_07\n", labels);

            String csv = new String(z.getInputStream(z.getEntry("tasks.csv")).readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(csv.contains("building_01,\"בניין \"\"המדעים\"\"\",40,2,\"מהשביל, ומהחניה\""));
            assertTrue(csv.contains("building_02,\"חדר האוכל\",30,0,"));
        }
    }

    @Test
    public void deleteAll_emptiesThePhone() throws IOException {
        shoot("building_01");
        shoot(PhotoTask.OTHER);
        assertEquals(2, store.deleteAll());
        assertEquals(0, store.total());
    }

    @Test
    public void delete_onlyRemovesItsOwnPhotos() throws IOException {
        File photo = shoot("building_01");
        File outside = folder.newFile("not-a-photo.jpg");
        assertFalse(store.delete(outside));
        assertTrue(outside.exists());
        assertTrue(store.delete(photo));
        assertEquals(0, store.count("building_01"));
    }
}
