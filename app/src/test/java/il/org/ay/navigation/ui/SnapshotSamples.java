package il.org.ay.navigation.ui;

import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import il.org.ay.navigation.R;
import il.org.ay.navigation.model.CampusRoom;

/**
 * Sample content for the layout snapshots, the same as in the Figma designs, so a
 * snapshot can be put next to its PNG. The app fills these views from Room at run time;
 * the layouts themselves hold no sample data (Paparazzi ignores tools:text).
 */
final class SnapshotSamples {

    private SnapshotSamples() {
    }

    static void text(View root, int id, CharSequence value) {
        ((TextView) root.findViewById(id)).setText(value);
    }

    static void selectTab(View root, int item) {
        ((BottomNavigationView) root.findViewById(R.id.bottom_nav)).setSelectedItemId(item);
    }

    /** The search screen as T09 runs it: the real adapter, "מז" typed, favourites still hidden (T10). */
    static void fillSearch(View v) {
        text(v, R.id.search_status, "64 חדרים · גרסה 3");
        text(v, R.id.search_text, "מז");
        text(v, R.id.results_title, v.getContext().getString(R.string.search_results, "מז"));
        text(v, R.id.results_count, "3");
        v.findViewById(R.id.results_count).setVisibility(View.VISIBLE);
        RecyclerView list = v.findViewById(R.id.search_results);
        RoomAdapter adapter = new RoomAdapter(room -> { }, room -> { });
        list.setAdapter(adapter);
        adapter.show(java.util.Arrays.asList(
                new CampusRoom("מז", "מזכירות ראשית", "קבלת קהל ושירותי תלמידים", 1, "תשפ\"ז"),
                new CampusRoom("34", "מזכירות פדגוגית", "רכזת בגרויות ורישום", 2, "תשפ\"ז"),
                new CampusRoom("12", null, "ליד מזכירות שכבת י'", 3, "תשפ\"ז")), "מז", null);
        selectTab(v, R.id.nav_search);
    }

    /** The empty box: favourites, recent searches and all the rooms (T10). */
    static void fillSearchStart(View v) {
        text(v, R.id.search_status, "64 חדרים · גרסה 3");
        v.findViewById(R.id.favorites_header).setVisibility(View.VISIBLE);
        v.findViewById(R.id.favorites_scroll).setVisibility(View.VISIBLE);
        ChipGroup favorites = v.findViewById(R.id.favorites_chips);
        addShortcut(favorites, "מזכירות ראשית", R.drawable.ic_star_filled);
        addShortcut(favorites, "ספרייה", R.drawable.ic_star_filled);
        v.findViewById(R.id.recent_header).setVisibility(View.VISIBLE);
        v.findViewById(R.id.recent_scroll).setVisibility(View.VISIBLE);
        ChipGroup recent = v.findViewById(R.id.recent_chips);
        addShortcut(recent, "מתמטיקה", R.drawable.ic_schedule);
        addShortcut(recent, "מעבדת ביולוגיה", R.drawable.ic_schedule);
        addShortcut(recent, "חדר 12", R.drawable.ic_schedule);
        text(v, R.id.results_count, "64");
        v.findViewById(R.id.results_count).setVisibility(View.VISIBLE);
        RecyclerView list = v.findViewById(R.id.search_results);
        RoomAdapter adapter = new RoomAdapter(room -> { }, room -> { });
        list.setAdapter(adapter);
        adapter.show(java.util.Arrays.asList(
                new CampusRoom("1", "חדר מורים", null, 1, "תשפ\"ז"),
                new CampusRoom("2", "אולם ספורט", "צוות חינוך גופני", 1, "תשפ\"ז"),
                new CampusRoom("12", null, "ליד מזכירות שכבת י'", 3, "תשפ\"ז"),
                new CampusRoom("20", "ספרייה", null, 2, "תשפ\"ז")), "", null);
        adapter.setFavorites(java.util.Collections.singleton("20"));
        selectTab(v, R.id.nav_search);
    }

    /** No room matches what was typed. */
    static void fillSearchEmpty(View v) {
        text(v, R.id.search_status, "64 חדרים · גרסה 3");
        text(v, R.id.search_text, "xyz");
        text(v, R.id.results_title, v.getContext().getString(R.string.search_results, "xyz"));
        v.findViewById(R.id.search_empty).setVisibility(View.VISIBLE);
        text(v, R.id.search_empty_title, v.getContext().getString(R.string.search_empty, "xyz"));
        selectTab(v, R.id.nav_search);
    }

    static void fillMap(View v) {
        text(v, R.id.map_floor_1, v.getContext().getString(R.string.map_floor, 1));
        text(v, R.id.map_floor_2, v.getContext().getString(R.string.map_floor, 2));
        text(v, R.id.map_destination_name, "חדר 42 — מתמטיקה");
        text(v, R.id.map_destination_detail, "רכזת שכבה ט'");
        text(v, R.id.map_distance, "120 מ'");
        text(v, R.id.map_time, "כ-2 דק' הליכה");
        text(v, R.id.map_entrance, "כניסה 12");
        selectTab(v, R.id.nav_map);
    }

    static void fillAr(View v) {
        text(v, R.id.ar_destination, v.getContext().getString(R.string.ar_destination, "חדר 42 — מתמטיקה"));
        text(v, R.id.ar_destination_distance, "45 מ'");
        text(v, R.id.ar_detected, "מבנה ט' זוהה");
        v.findViewById(R.id.ar_detected).setVisibility(View.VISIBLE);
        text(v, R.id.ar_gps_accuracy, v.getContext().getString(R.string.ar_gps_accuracy, 4));
        text(v, R.id.ar_heading, "צפון-מערב");
        text(v, R.id.ar_instruction, "פנו שמאלה בעוד 15 מ'");
        text(v, R.id.ar_instruction_detail, "חדר 42 · 120 מ' (כ-2 דק')");
        text(v, R.id.ar_step, v.getContext().getString(R.string.ar_step, 2, 4));
        text(v, R.id.ar_room_number, "42");
        text(v, R.id.ar_room_name, "חדר 42 — מתמטיקה");
        text(v, R.id.ar_room_owner, "רכזת שכבה ט'");
        text(v, R.id.ar_time_left, "2 דק'");
        selectTab(v, R.id.nav_ar);
    }

    static void fillArrival(View v) {
        text(v, R.id.arrival_room, "חדר 42 — מתמטיקה");
        text(v, R.id.arrival_detail, "רכזת שכבה ט'");
        text(v, R.id.arrival_summary, v.getResources().getQuantityString(R.plurals.arrival_summary, 2, 2, 120));
    }

    static void fillCalibration(View v) {
        text(v, R.id.calibration_warning_title, v.getContext().getString(R.string.calibration_warning_title, 18));
        ChipGroup places = v.findViewById(R.id.calibration_places);
        addShortcut(places, "בניין מדעים", R.drawable.ic_school);
        addShortcut(places, "רחבת הכניסה", R.drawable.ic_meeting_room);
        addShortcut(places, "ספרייה", R.drawable.ic_library);
        ((Chip) places.getChildAt(0)).setCheckable(true);
        ((Chip) places.getChildAt(0)).setChecked(true);
        text(v, R.id.calibration_selected, "בניין מדעים, כניסה ראשית");
    }

    static void fillAdminRooms(View v) {
        v.findViewById(R.id.admin_login_state).setVisibility(View.GONE);
        v.findViewById(R.id.admin_rooms_state).setVisibility(View.VISIBLE);
        com.google.android.material.appbar.MaterialToolbar bar = v.findViewById(R.id.toolbar);
        bar.setNavigationIcon(null);
        bar.setLogo(R.drawable.ic_logo_app);
        bar.setTitle(R.string.admin_rooms_screen);
        bar.setSubtitle(v.getContext().getString(R.string.admin_rooms_subtitle, "teacher@a-y.org.il"));
        bar.inflateMenu(R.menu.admin_bar);
        text(v, R.id.admin_map_title, v.getContext().getString(R.string.admin_map_title, "תשפ״ז"));
        text(v, R.id.admin_map_info, v.getContext().getString(R.string.admin_map_info, 64, 3));
        text(v, R.id.admin_search, "42");
        RecyclerView list = v.findViewById(R.id.admin_rooms);
        list.setAdapter(new RoomsAdapter(new String[][]{
                {"42", "חדר מתמטיקה", "רכזת שכבה ט'", ""},
        }));
    }

    static void fillRoomEdit(View v) {
        text(v, R.id.room_edit_number, "42");
        text(v, R.id.room_edit_title, v.getContext().getString(R.string.room_edit_title, "42"));
        text(v, R.id.room_edit_entrance, "כניסה 12");
        text(v, R.id.room_edit_name, "חדר מתמטיקה");
        text(v, R.id.room_edit_owner, "רכזת שכבה ט'");
    }

    static void fillAlignment(View v) {
        com.google.android.material.appbar.MaterialToolbar bar = v.findViewById(R.id.toolbar);
        bar.setTitle(v.getContext().getString(R.string.alignment_title, "תשפ״ז"));
        bar.setSubtitle(v.getContext().getString(R.string.alignment_step, 2, 3));
        text(v, R.id.alignment_opacity_value, "50%");
        text(v, R.id.alignment_save, v.getContext().getString(R.string.alignment_save, 3));
    }

    /** A dialog card in the middle of a dimmed screen, as Android shows it. */
    static View overDimmedScreen(View card, int gravity) {
        FrameLayout screen = new FrameLayout(card.getContext());
        screen.setBackgroundColor(0xFF7A7F7A);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, gravity);
        int margin = Math.round(24 * card.getResources().getDisplayMetrics().density);
        lp.setMargins(margin, margin, margin, margin);
        screen.addView(card, lp);
        return screen;
    }

    /** A bottom sheet over a screen that is dimmed behind it. */
    static View sheetOver(View screen, View sheet) {
        FrameLayout frame = new FrameLayout(screen.getContext());
        frame.addView(screen);
        View dim = new View(screen.getContext());
        dim.setBackgroundColor(0x99000000);
        frame.addView(dim);
        frame.addView(sheet, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));
        return frame;
    }

    static void addShortcut(ChipGroup group, String label, int icon) {
        Chip chip = (Chip) LayoutInflater.from(group.getContext())
                .inflate(R.layout.item_shortcut_chip, group, false);
        chip.setText(label);
        chip.setChipIconResource(icon);
        group.addView(chip);
    }

    /** Rows of {number, name, owner, distance}. The first row is the exact match. */
    static final class RoomsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private final String[][] rows;

        RoomsAdapter(String[][] rows) {
            this.rows = rows;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            View item = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_room, parent, false);
            return new RecyclerView.ViewHolder(item) { };
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            View v = holder.itemView;
            String[] row = rows[position];
            text(v, R.id.room_number, row[0]);
            text(v, R.id.room_name, row[1]);
            text(v, R.id.room_owner, row[2]);
            text(v, R.id.room_distance, row[3]);
            v.findViewById(R.id.room_distance).setVisibility(row[3].isEmpty() ? View.GONE : View.VISIBLE);
            if (position == 0 && rows.length > 1) {
                TextView number = v.findViewById(R.id.room_number);
                number.setBackgroundResource(R.drawable.bg_circle_primary);
                number.setTextColor(0xFFFFFFFF);
            }
        }

        @Override
        public int getItemCount() {
            return rows.length;
        }
    }
}
