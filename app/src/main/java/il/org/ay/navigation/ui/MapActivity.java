package il.org.ay.navigation.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.snackbar.Snackbar;

import org.osmdroid.views.MapView;

import il.org.ay.navigation.R;
import il.org.ay.navigation.data.CampusGraph;
import il.org.ay.navigation.data.CampusRepository;

/**
 * Map and route (FR-05). T29: the campus map with its layers and paths, from the copy in
 * Room, so it works without a network. T15 adds the route, the live position and the
 * destination sheet.
 */
public class MapActivity extends BaseActivity {

    private MapView map;
    private CampusMapLayers layers;
    private TextView placeholder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        CampusMapLayers.configure(this);   // before the MapView exists
        setContentView(R.layout.activity_map);

        map = new MapView(this);
        FrameLayout container = findViewById(R.id.map_container);
        container.addView(map, 0, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        layers = new CampusMapLayers(map);

        placeholder = findViewById(R.id.map_placeholder);   // "loading the campus map…"

        // not working yet: shown again by the tasks that build them
        findViewById(R.id.map_sheet).setVisibility(View.GONE);          // T15: destination
        findViewById(R.id.map_my_location).setVisibility(View.GONE);    // T15: live position
        findViewById(R.id.map_wrong_location).setVisibility(View.GONE); // T23: calibration
        findViewById(R.id.map_floors).setVisibility(View.GONE);         // the campus data has no floors

        BottomNavigationView nav = findViewById(R.id.bottom_nav);
        nav.setSelectedItemId(R.id.nav_map);
        nav.setOnItemSelectedListener(item -> {
            if (item.getItemId() == R.id.nav_search) {
                openSearch();
            } else if (item.getItemId() != R.id.nav_map) {
                Snackbar.make(map, R.string.coming_soon, Snackbar.LENGTH_SHORT)
                        .setAnchorView(R.id.bottom_nav).show();
            }
            return false;
        });

        CampusRepository.get(this).loadGraph(this::showCampus);
    }

    private void showCampus(@NonNull CampusGraph graph) {
        if (graph.nodeCount() == 0) {
            placeholder.setText(R.string.search_no_data_hint);   // never synced: nothing to draw
            return;
        }
        placeholder.setVisibility(View.GONE);
        layers.showPaths(graph);
        map.post(() -> layers.zoomToCampus(graph));
    }

    private void openSearch() {
        // search is the screen under this one; bring it back instead of opening another
        startActivity(new Intent(this, SearchActivity.class).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
        finish();
        overridePendingTransition(0, 0);
    }

    @Override
    protected void onResume() {
        super.onResume();
        map.onResume();
    }

    @Override
    protected void onPause() {
        map.onPause();
        super.onPause();
    }

    @VisibleForTesting
    CampusMapLayers layers() {
        return layers;
    }
}
