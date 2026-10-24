package il.org.ay.navigation.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomnavigation.BottomNavigationView;
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
    private TextView resultsCount;
    private TextView status;

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
        resultsCount = findViewById(R.id.results_count);
        status = findViewById(R.id.search_status);

        adapter = new RoomAdapter(this::onRoomChosen);
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
            if (item.getItemId() != R.id.nav_search) {
                comingSoon();
            }
            return false;  // stay on the search tab until the other screens exist
        });
        findViewById(R.id.search_ar_open).setOnClickListener(v -> comingSoon());

        search();
        showStatus();
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
        showStatus();
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
        resultsTitle.setText(query.isEmpty() ? getString(R.string.search_results_all)
                : getString(R.string.search_results, typed));
        resultsCount.setText(String.valueOf(rooms.size()));
        resultsCount.setVisibility(rooms.isEmpty() ? View.GONE : View.VISIBLE);
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
    private void showStatus() {
        repo.allRooms(rooms -> status.setText(
                getString(R.string.search_local_data, rooms.size(), repo.localVersion())));
    }

    private void onRoomChosen(CampusRoom room) {
        hideKeyboard();
        // T10 saves it to the recent searches; T11 opens the map with the route
        Snackbar.make(results, getString(R.string.search_chosen,
                RoomAdapter.displayName(results, room)), Snackbar.LENGTH_SHORT)
                .setAnchorView(R.id.bottom_nav).show();
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
