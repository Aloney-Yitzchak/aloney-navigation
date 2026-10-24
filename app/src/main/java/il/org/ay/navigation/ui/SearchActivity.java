package il.org.ay.navigation.ui;

import android.os.Bundle;
import android.widget.TextView;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import il.org.ay.navigation.R;
import il.org.ay.navigation.data.CampusRepository;

/**
 * Destination search (layout: T30). Until T09 fills the list, the status chip shows
 * how many rooms are in the local copy and which version they are, so the sync and
 * the airplane-mode test (AT-09) can be checked on a phone.
 */
public class SearchActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);
        BottomNavigationView nav = findViewById(R.id.bottom_nav);
        nav.setSelectedItemId(R.id.nav_search);
        TextView status = findViewById(R.id.search_status);
        CampusRepository repo = CampusRepository.get(this);
        repo.allRooms(rooms -> status.setText(
                getString(R.string.search_local_data, rooms.size(), repo.localVersion())));
    }
}
