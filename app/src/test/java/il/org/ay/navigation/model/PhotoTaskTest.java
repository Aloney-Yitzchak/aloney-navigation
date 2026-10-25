package il.org.ay.navigation.model;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

/** T20a: labels of new buildings never clash, and "other" stays last. */
public class PhotoTaskTest {

    @Test
    public void firstBuilding() {
        assertEquals("building_01", PhotoTask.nextLabel(Collections.emptyList()));
    }

    @Test
    public void aDeletedNumberIsReused_andUsedOnesAreSkipped() {
        assertEquals("building_02", PhotoTask.nextLabel(Arrays.asList(
                new PhotoTask("building_01", "a", 40, null, 1),
                new PhotoTask("building_03", "c", 40, null, 3),
                new PhotoTask(PhotoTask.OTHER, "אחר", 40, null, 1000))));
    }

    @Test
    public void newBuilding_goesAfterTheBuildings_beforeOther() {
        assertEquals(4, PhotoTask.nextOrder(Arrays.asList(
                new PhotoTask("building_01", "a", 40, null, 1),
                new PhotoTask("building_02", "b", 40, null, 3),
                new PhotoTask(PhotoTask.OTHER, "אחר", 40, null, 1000))));
        assertEquals(1, PhotoTask.nextOrder(Collections.singletonList(
                new PhotoTask(PhotoTask.OTHER, "אחר", 40, null, 1000))));
    }
}
