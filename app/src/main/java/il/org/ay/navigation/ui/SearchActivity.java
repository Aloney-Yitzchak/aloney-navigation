package il.org.ay.navigation.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.snackbar.Snackbar;

import java.util.List;

import il.org.ay.navigation.R;
import il.org.ay.navigation.data.CampusRepository;
import il.org.ay.navigation.logic.RoomSearch;
import il.org.ay.navigation.model.CampusRoom;

/**
 * Destination search (T09, FR-01, AT-01). The list updates while typing: by number,
 * name or owner, best match first (RoomSearch). With an empty box it shows all rooms.
 * The rooms come from the copy in Room, so search works without a network (AT-09).
 *
 * <p>Favourites and recent searches (T10, FR-02, AT-02) are shortcuts above the list:
 * the star in a row adds a favourite, choosing a room adds it to the recent searches.
 * Both are saved in Room, so they are still there after the app is closed.
 */
public class SearchActivity extends BaseActivity {

    /** Wait this long after the last key before searching, so fast typing runs one query. */
    static final long TYPING_PAUSE_MS = 150;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable runSearch = this::search;
    private final Runnable onCampusChanged = this::onCampusChanged;

    private CampusRepository repo;
    private RoomAdapter adapter;
    private EditText input;
    private RecyclerView results;
    private View empty;
    private TextView emptyTitle;
    private TextView emptyText;
    private TextView resultsTitle;

    private ChipGroup favoriteChips;
    private ChipGroup recentChips;
    private View favoritesHeader;
    private View favoritesScroll;
    private View recentHeader;
    private View recentScroll;
    private boolean hasRecents;
    private String currentQuery = "";

    /** Counts searches; a result that arrives after a newer search started is dropped. */
    private int generation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);
        repo = CampusRepository.get(this);

        input = findViewById(R.id.search_text);
        results = findViewById(R.id.search_results);
        empty = findViewById(R.id.search_empty);
        emptyTitle = findViewById(R.id.search_empty_title);
        emptyText = findViewById(R.id.search_empty_text);
        resultsTitle = findViewById(R.id.results_title);

        favoriteChips = findViewById(R.id.favorites_chips);
        recentChips = findViewById(R.id.recent_chips);
        favoritesHeader = findViewById(R.id.favorites_header);
        favoritesScroll = findViewById(R.id.favorites_scroll);
        recentHeader = findViewById(R.id.recent_header);
        recentScroll = findViewById(R.id.recent_scroll);

        adapter = new RoomAdapter(this::onRoomChosen, this::onStarClicked);
        results.setAdapter(adapter);

        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                handler.removeCallbacks(runSearch);
                handler.postDelayed(runSearch, TYPING_PAUSE_MS);
            }
        });
        input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                hideKeyboard();
                handler.removeCallbacks(runSearch);
                search();
                return true;
            }
            return false;
        });

        BottomNavigationView nav = findViewById(R.id.bottom_nav);
        nav.setSelectedItemId(R.id.nav_search);
        nav.setOnItemSelectedListener(item -> {
            if (item.getItemId() == R.id.nav_map) {
                startActivity(new Intent(this, MapActivity.class));
                overridePendingTransition(0, 0);
            } else if (item.getItemId() == R.id.nav_ar) {
                startActivity(ArNavActivity.navigateTo(this, null));
            } else if (item.getItemId() == R.id.nav_staff) {
                startActivity(new Intent(this, AdminActivity.class));   // T24
            } else if (item.getItemId() != R.id.nav_search) {
                comingSoon();
            }
            return false;  // this screen stays the search tab
        });

        search();
        loadShortcuts();
    }

    @Override
    protected void onStart() {
        super.onStart();
        repo.addChangeListener(onCampusChanged);
    }

    @Override
    protected void onStop() {
        repo.removeChangeListener(onCampusChanged);
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    /** The first sync finished while this screen was open: show the new rooms. */
    private void onCampusChanged() {
        search();
        loadShortcuts();  // a room that left the campus data took its favourite with it
    }

    private void search() {
        String typed = input.getText().toString();
        String query = RoomSearch.normalize(typed);
        int mine = ++generation;
        repo.search(typed, rooms -> {
            if (mine == generation && !isFinishing()) {
                showResults(rooms, query, typed.trim());
            }
        });
    }

    private void showResults(List<CampusRoom> rooms, String query, String typed) {
        currentQuery = query;
        showRecentSection();
        resultsTitle.setText(query.isEmpty() ? getString(R.string.search_results_all)
                : getString(R.string.search_results, typed));
        adapter.show(rooms, query, () -> results.scrollToPosition(0));

        boolean noRooms = rooms.isEmpty();
        empty.setVisibility(noRooms ? View.VISIBLE : View.GONE);
        if (noRooms && query.isEmpty()) {
            emptyTitle.setText(R.string.search_no_data);
            emptyText.setText(R.string.search_no_data_hint);
        } else if (noRooms) {
            emptyTitle.setText(getString(R.string.search_empty, typed));
            emptyText.setText(R.string.search_empty_hint);
        }
    }

    /** How many rooms the phone holds and which version (T08, AT-09). */
    private void onRoomChosen(CampusRoom room) {
        hideKeyboard();
        repo.addRecent(room.number, this::loadShortcuts);
        startActivity(MapActivity.navigateTo(this, room.number));   // T15: the route to it
    }

    // ------------------------------------------------------------ favourites and recent (T10)

    private void onStarClicked(CampusRoom room) {
        CharSequence name = RoomAdapter.displayName(results, room);
        repo.toggleFavorite(room.number, starred -> {
            loadShortcuts();
            Snackbar.make(results, getString(starred ? R.string.favorite_added : R.string.favorite_removed, name),
                    Snackbar.LENGTH_SHORT).setAnchorView(R.id.bottom_nav).show();
        });
    }

    /** Reads favourites and recent searches from Room and redraws the two chip rows and the stars. */
    private void loadShortcuts() {
        repo.favorites(rooms -> {
            fillChips(favoriteChips, rooms, R.drawable.ic_star_filled);
            boolean any = !rooms.isEmpty();
            favoritesHeader.setVisibility(any ? View.VISIBLE : View.GONE);
            favoritesScroll.setVisibility(any ? View.VISIBLE : View.GONE);
        });
        repo.favoriteNumbers(adapter::setFavorites);
        repo.recents(rooms -> {
            fillChips(recentChips, rooms, R.drawable.ic_schedule);
            hasRecents = !rooms.isEmpty();
            showRecentSection();
        });
    }

    /** Recent searches only while the box is empty: once typing starts, the results need the room. */
    private void showRecentSection() {
        int v = hasRecents && currentQuery.isEmpty() ? View.VISIBLE : View.GONE;
        recentHeader.setVisibility(v);
        recentScroll.setVisibility(v);
    }

    private void fillChips(ChipGroup group, List<CampusRoom> rooms, int icon) {
        group.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        for (CampusRoom room : rooms) {
            Chip chip = (Chip) inflater.inflate(R.layout.item_shortcut_chip, group, false);
            chip.setText(RoomAdapter.displayName(group, room));
            chip.setChipIconResource(icon);
            chip.setTag(room.number);
            chip.setOnClickListener(v -> onRoomChosen(room));
            group.addView(chip);
        }
    }

    private void comingSoon() {
        Snackbar.make(results, R.string.coming_soon, Snackbar.LENGTH_SHORT)
                .setAnchorView(R.id.bottom_nav).show();
    }

    private void hideKeyboard() {
        InputMethodManager imm = getSystemService(InputMethodManager.class);
        imm.hideSoftInputFromWindow(input.getWindowToken(), 0);
        input.clearFocus();
    }
}
