package il.org.ay.navigation.logic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import il.org.ay.navigation.model.CampusRoom;

/** FR-01 / AT-01: which room comes first in the search results. */
public class RoomSearchTest {

    private static CampusRoom room(String number, String name, String owner) {
        return new CampusRoom(number, name, owner, 1, "תשפ\"ז");
    }

    private static List<String> numbers(List<CampusRoom> rooms) {
        List<String> out = new ArrayList<>();
        for (CampusRoom r : rooms) {
            out.add(r.number);
        }
        return out;
    }

    @Test
    public void normalize_trimsAndRemovesTheWordRoom() {
        assertEquals("42", RoomSearch.normalize("  חדר   42 "));
        assertEquals("מורים", RoomSearch.normalize("חדר מורים"));
        assertEquals("חדר", RoomSearch.normalize("חדר"));
        assertEquals("", RoomSearch.normalize(null));
    }

    @Test
    public void normalize_hebrewQuoteMarksBecomePlainOnes() {
        assertEquals("תשפ\"ז", RoomSearch.normalize("תשפ״ז"));
        assertEquals("ט'", RoomSearch.normalize("ט׳"));
    }

    @Test
    public void exactNumber_comesFirst_thenNumbersThatStartWithIt() {
        List<CampusRoom> found = Arrays.asList(
                room("142", "מעבדה", null),
                room("42", "מתמטיקה", null),
                room("420", null, null),
                room("ש42", null, null));
        assertEquals(Arrays.asList("42", "420", "142", "ש42"), numbers(RoomSearch.rank(found, "42")));
    }

    @Test
    public void nameBeforeOwner() {
        List<CampusRoom> found = Arrays.asList(
                room("3", "חדר מורים", "מזכירות"),
                room("7", "מזכירות פדגוגית", null),
                room("5", "ספרייה", "רכזת מזכירות"));
        // "מזכירות" starts the name of 7; it is only in the owner of 3 and 5
        assertEquals(Arrays.asList("7", "3", "5"), numbers(RoomSearch.rank(found, "מזכירות")));
    }

    @Test
    public void aWordInsideTheName_beatsTextInsideAWord() {
        List<CampusRoom> found = Arrays.asList(
                room("1", "אמנות", null),
                room("2", "חדר מנות", null));
        assertEquals(Arrays.asList("2", "1"), numbers(RoomSearch.rank(found, "מנות")));
    }

    @Test
    public void emptyQuery_allRoomsInNaturalOrder() {
        List<CampusRoom> all = Arrays.asList(room("10", null, null), room("ש36", null, null),
                room("9", null, null), room("36", null, null), room("מז", null, null));
        assertEquals(Arrays.asList("9", "10", "36", "מז", "ש36"), numbers(RoomSearch.rank(all, "")));
    }

    @Test
    public void naturalOrder() {
        assertTrue(RoomSearch.NATURAL.compare("9", "10") < 0);
        assertTrue(RoomSearch.NATURAL.compare("36", "ש36") < 0);
        assertTrue(RoomSearch.NATURAL.compare("ש9", "ש36") < 0);
        assertTrue(RoomSearch.NATURAL.compare("12", "12א") < 0);
        assertEquals(0, RoomSearch.NATURAL.compare("42", "42"));
    }

    @Test
    public void exactNumber_isCaseInsensitive_andNeverForAnEmptyQuery() {
        assertTrue(RoomSearch.isExactNumber(room("A1", null, null), "a1"));
        assertFalse(RoomSearch.isExactNumber(room("42", null, null), ""));
        assertFalse(RoomSearch.isExactNumber(room("42", null, null), "4"));
    }
}
