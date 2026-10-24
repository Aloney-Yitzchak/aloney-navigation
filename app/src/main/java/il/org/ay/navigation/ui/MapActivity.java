package il.org.ay.navigation.ui;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;

import org.osmdroid.views.MapView;

import il.org.ay.navigation.R;
import il.org.ay.navigation.data.CampusGraph;
import il.org.ay.navigation.data.CampusRepository;
import il.org.ay.navigation.data.Route;
import il.org.ay.navigation.logic.LocationService;
import il.org.ay.navigation.logic.NavigationManager;
import il.org.ay.navigation.logic.WalkingEstimate;
import il.org.ay.navigation.model.CampusRoom;
import il.org.ay.navigation.model.LatLng;

/**
 * Map and route (T15, FR-05, AT-05). Opened from the tab, it shows the campus (T29).
 * Opened with a room ({@link #EXTRA_ROOM}), it navigates there: the live position comes
 * from LocationService, NavigationManager plans the route and recalculates it when the
 * user leaves it (T14), and the bottom sheet shows the room and the metres and minutes left.
 */
public class MapActivity extends BaseActivity implements LocationService.Listener {

    /** The room number to navigate to. */
    public static final String EXTRA_ROOM = "room";

    private CampusRepository repo;
    private MapView map;
    private CampusMapLayers layers;
    private TextView placeholder;
    private View sheet;
    private TextView sheetName;
    private TextView sheetDetail;
    private TextView sheetDistance;
    private TextView sheetTime;
    private MaterialButton favorite;
    private View myLocation;

    @Nullable
    private CampusGraph graph;
    @Nullable
    private CampusRoom room;
    @Nullable
    private NavigationManager nav;
    @Nullable
    private LocationService location;
    @Nullable
    private LatLng lastPosition;
    private boolean zoomedToRoute;

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            location = ((LocationService.LocalBinder) binder).getService();
            location.addListener(MapActivity.this);
            if (!location.isRunning()) {
                showStatus(getString(R.string.map_no_location));
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            location = null;
        }
    };

    /** Opens the map with a route to {@code roomNumber}. */
    public static Intent navigateTo(@NonNull Context context, @NonNull String roomNumber) {
        return new Intent(context, MapActivity.class).putExtra(EXTRA_ROOM, roomNumber);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        CampusMapLayers.configure(this);   // before the MapView exists
        setContentView(R.layout.activity_map);
        repo = CampusRepository.get(this);

        map = new MapView(this);
        FrameLayout container = findViewById(R.id.map_container);
        container.addView(map, 0, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        layers = new CampusMapLayers(map);

        placeholder = findViewById(R.id.map_placeholder);   // "loading the campus map…"
        sheet = findViewById(R.id.map_sheet);
        sheetName = findViewById(R.id.map_destination_name);
        sheetDetail = findViewById(R.id.map_destination_detail);
        sheetDistance = findViewById(R.id.map_distance);
        sheetTime = findViewById(R.id.map_time);
        favorite = findViewById(R.id.map_favorite);
        myLocation = findViewById(R.id.map_my_location);

        sheet.setVisibility(View.GONE);
        myLocation.setVisibility(View.GONE);
        findViewById(R.id.map_entrance).setVisibility(View.GONE);       // the data has no wings or floors
        findViewById(R.id.map_route_details).setVisibility(View.GONE);  // a step list is not planned
        findViewById(R.id.map_wrong_location).setVisibility(View.GONE); // T23: calibration
        findViewById(R.id.map_floors).setVisibility(View.GONE);         // the campus data has no floors

        myLocation.setOnClickListener(v -> {
            if (lastPosition != null) {
                layers.centerOn(lastPosition);
            }
        });
        findViewById(R.id.map_start_ar).setOnClickListener(v -> comingSoon());   // T16
        favorite.setOnClickListener(v -> toggleFavorite());

        BottomNavigationView nav = findViewById(R.id.bottom_nav);
        nav.setSelectedItemId(R.id.nav_map);
        nav.setOnItemSelectedListener(item -> {
            if (item.getItemId() == R.id.nav_search) {
                openSearch();
            } else if (item.getItemId() != R.id.nav_map) {
                comingSoon();
            }
            return false;
        });

        repo.loadGraph(this::onGraph);
    }

    private void onGraph(@NonNull CampusGraph g) {
        if (g.nodeCount() == 0) {
            placeholder.setText(R.string.search_no_data_hint);   // never synced: nothing to draw
            return;
        }
        graph = g;
        placeholder.setVisibility(View.GONE);
        layers.showPaths(g);
        String number = getIntent().getStringExtra(EXTRA_ROOM);
        if (number == null) {
            map.post(() -> layers.zoomToCampus(g));
            return;
        }
        repo.findRoom(number, this::onRoom);
    }

    private void onRoom(@Nullable CampusRoom r) {
        if (r == null || r.entranceNodeId == null || graph == null || graph.node(r.entranceNodeId) == null) {
            map.post(() -> layers.zoomToCampus(graph));
            showStatus(getString(R.string.map_no_route));
            return;
        }
        room = r;
        nav = new NavigationManager(graph, r.entranceNodeId, this::onRouteChanged);
        layers.showDestination(LatLng.of(graph.node(r.entranceNodeId)));

        sheet.setVisibility(View.VISIBLE);
        sheetName.setText(RoomAdapter.displayName(sheet, r));
        sheetDetail.setText(r.owner);
        sheetDetail.setVisibility(r.owner == null || r.owner.isEmpty() ? View.GONE : View.VISIBLE);
        sheetDistance.setVisibility(View.GONE);
        sheetTime.setText(R.string.map_waiting_for_location);
        repo.isFavorite(r.number, this::showFavorite);

        map.post(() -> layers.centerOn(LatLng.of(graph.node(r.entranceNodeId))));
        if (lastPosition != null) {
            nav.update(lastPosition);
        }
    }

    /** LocationService: a new position, smoothed and snapped (T11). */
    @Override
    public void onPosition(@NonNull LocationService.Position p) {
        lastPosition = p.smoothed;
        layers.showPosition(p.smoothed, p.accuracy);
        myLocation.setVisibility(View.VISIBLE);
        if (nav != null) {
            nav.update(p.smoothed);
            showDistance();
        }
    }

    /** NavigationManager: the first route, or a new one after leaving it (T14). */
    private void onRouteChanged(@NonNull Route route, boolean recalculated) {
        layers.drawRoute(route);
        showDistance();
        if (!zoomedToRoute || recalculated) {   // the first route, and each new one
            zoomedToRoute = true;
            // the sheet covers the bottom of the map: keep the route and the user above it
            map.post(() -> layers.zoomToRoute(route, lastPosition, sheet.getHeight()));
        }
        if (recalculated) {
            showStatus(getString(R.string.map_route_recalculated));
        }
    }

    private void showDistance() {
        if (nav == null || nav.getRoute() == null) {
            if (nav != null && lastPosition != null) {
                sheetTime.setText(R.string.map_no_route);
            }
            return;
        }
        double metres = nav.getDistanceLeft();
        sheetDistance.setVisibility(View.VISIBLE);
        sheetDistance.setText(getString(R.string.map_distance, WalkingEstimate.roundedMetres(metres)));
        sheetTime.setText(getString(R.string.map_minutes, WalkingEstimate.minutes(metres)));
    }

    private void toggleFavorite() {
        if (room != null) {
            repo.toggleFavorite(room.number, this::showFavorite);
        }
    }

    private void showFavorite(boolean starred) {
        favorite.setIconResource(starred ? R.drawable.ic_star_filled : R.drawable.ic_star);
        favorite.setIconTintResource(starred ? R.color.primary : R.color.text_secondary);
        favorite.setContentDescription(getString(starred ? R.string.action_unfavorite : R.string.action_favorite));
    }

    private void showStatus(String text) {
        Snackbar.make(map, text, Snackbar.LENGTH_LONG).setAnchorView(R.id.bottom_nav).show();
    }

    private void comingSoon() {
        Snackbar.make(map, R.string.coming_soon, Snackbar.LENGTH_SHORT).setAnchorView(R.id.bottom_nav).show();
    }

    private void openSearch() {
        // search is the screen under this one; bring it back instead of opening another
        startActivity(new Intent(this, SearchActivity.class).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
        finish();
        overridePendingTransition(0, 0);
    }

    @Override
    protected void onStart() {
        super.onStart();
        // the GPS runs only while this screen is visible (NFR-08)
        LocationService.bind(this, connection);
    }

    @Override
    protected void onStop() {
        if (location != null) {
            location.removeListener(this);
        }
        unbindService(connection);
        location = null;
        super.onStop();
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

    @VisibleForTesting
    @Nullable
    NavigationManager navigation() {
        return nav;
    }
}
