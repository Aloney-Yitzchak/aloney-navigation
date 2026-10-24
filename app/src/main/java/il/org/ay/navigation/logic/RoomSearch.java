package il.org.ay.navigation.logic;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import il.org.ay.navigation.model.CampusRoom;

/**
 * Orders search results (FR-01, AT-01). The database finds every room whose number,
 * name or owner contains the text; this class decides which come first:
 *
 * <ol>
 *   <li>the room whose number is exactly what was typed ("42")</li>
 *   <li>rooms whose number starts with it ("4" finds 4, 40, 42…)</li>
 *   <li>rooms whose name starts with it, or has a word that starts with it</li>
 *   <li>rooms whose number contains it</li>
 *   <li>rooms whose name contains it</li>
 *   <li>rooms whose owner contains it</li>
 * </ol>
 *
 * Within a group, room numbers are in natural order: 9 before 10, and ש36 after 36.
 * Pure Java, no Android, so it is tested on the computer (RoomSearchTest).
 */
public final class RoomSearch {

    private RoomSearch() {
    }

    /**
     * What to send to the database: trimmed, single spaces, Hebrew quote marks as plain
     * ones, and without a leading "חדר " ("חדר 42" searches for 42).
     */
    @NonNull
    public static String normalize(@Nullable String text) {
        if (text == null) {
            return "";
        }
        String s = text.replace('״', '"').replace('׳', '\'')
                .replaceAll("\\s+", " ").trim();
        if (s.startsWith("חדר ") && s.length() > 4) {
            s = s.substring(4).trim();
        }
        return s;
    }

    /** Sorts the rooms the database found for {@code query} (already normalized). */
    @NonNull
    public static List<CampusRoom> rank(@NonNull List<CampusRoom> rooms, @NonNull String query) {
        List<CampusRoom> sorted = new ArrayList<>(rooms);
        String q = query.toLowerCase(Locale.ROOT);
        Comparator<CampusRoom> byGroup = Comparator.comparingInt(r -> group(r, q));
        Collections.sort(sorted, byGroup.thenComparing(r -> r.number, NATURAL));
        return sorted;
    }

    /** True when {@code query} is exactly this room's number: the screen highlights it. */
    public static boolean isExactNumber(@NonNull CampusRoom room, @NonNull String query) {
        return !query.isEmpty() && room.number.equalsIgnoreCase(query);
    }

    /** 0 = best. An empty query puts every room in the same group. */
    static int group(CampusRoom room, String q) {
        if (q.isEmpty()) {
            return 0;
        }
        String number = room.number.toLowerCase(Locale.ROOT);
        String name = lower(room.name);
        if (number.equals(q)) {
            return 0;
        }
        if (number.startsWith(q)) {
            return 1;
        }
        if (name.startsWith(q) || name.contains(" " + q)) {
            return 2;
        }
        if (number.contains(q)) {
            return 3;
        }
        if (name.contains(q)) {
            return 4;
        }
        if (lower(room.owner).contains(q)) {
            return 5;
        }
        return 6;  // the database matched it some other way; keep it, last
    }

    private static String lower(@Nullable String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }

    /**
     * Natural order of room numbers: the letters before the number ("ש" in ש36) first,
     * then the number as a number, then whatever follows.
     */
    public static final Comparator<String> NATURAL = (a, b) -> {
        String[] x = split(a);
        String[] y = split(b);
        int c = x[0].compareTo(y[0]);
        if (c != 0) {
            return c;
        }
        if (!x[1].equals(y[1])) {
            if (x[1].isEmpty()) {
                return -1;
            }
            if (y[1].isEmpty()) {
                return 1;
            }
            c = Long.compare(Long.parseLong(x[1]), Long.parseLong(y[1]));
            if (c != 0) {
                return c;
            }
        }
        return x[2].compareTo(y[2]);
    };

    /** "ש36א" -> {"ש", "36", "א"}; "מז" -> {"מז", "", ""}. */
    private static String[] split(String s) {
        int i = 0;
        while (i < s.length() && !Character.isDigit(s.charAt(i))) {
            i++;
        }
        int j = i;
        while (j < s.length() && Character.isDigit(s.charAt(j)) && j - i < 18) {
            j++;
        }
        return new String[]{s.substring(0, i), s.substring(i, j), s.substring(j)};
    }
}
