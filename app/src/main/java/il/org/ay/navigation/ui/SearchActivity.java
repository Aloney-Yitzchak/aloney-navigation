package il.org.ay.navigation.ui;

import android.os.Bundle;
import android.widget.TextView;

import il.org.ay.navigation.R;
import il.org.ay.navigation.data.CampusRepository;

/**
 * Destination search. Until T09 builds the real screen, it shows how many rooms
 * are in the local copy and which version they are, so the sync and the
 * airplane-mode test (AT-09) can be checked on a phone.
 */
public class SearchActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);
        TextView status = findViewById(R.id.search_placeholder);
        CampusRepository repo = CampusRepository.get(this);
        repo.allRooms(rooms -> status.setText(
                getString(R.string.search_local_data, rooms.size(), repo.localVersion())));
    }
}
