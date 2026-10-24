package il.org.ay.navigation.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import org.osmdroid.events.MapListener;
import org.osmdroid.events.ScrollEvent;
import org.osmdroid.events.ZoomEvent;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import il.org.ay.navigation.R;
import il.org.ay.navigation.data.CampusGraph;
import il.org.ay.navigation.data.CampusRepository;
import il.org.ay.navigation.logic.NavigationManager;
import il.org.ay.navigation.logic.WalkingEstimate;
import il.org.ay.navigation.model.CampusRoom;
import il.org.ay.navigation.model.LatLng;
import il.org.ay.navigation.model.Node;

/**
 * Manual calibration (T23, FR-08, AT-08; layout dialog_calibration.xml). The user drags the
 * map under a fixed pin to where they really are, or taps a known place (a favourite or a
 * recent room) to jump to its entrance. "Confirm" sends the point back as the fragment
 * result {@link #RESULT}; MapActivity gives it to LocationService and re-plans the route.
 */
public class CalibrationDialog extends DialogFragment {

    public static final String TAG = "calibration";
    public static final String RESULT = "calibration_result";
    /** Sent when the dialog closes without a confirmed point. */
    public static final String CANCELLED = "calibration_cancelled";
    public static final String LAT = "lat";
    public static final String LNG = "lng";
    private static final String ARG_ACCURACY = "accuracy";

    /** Within this of an entrance, the place is named after its room. */
    static final double NEAR_ENTRANCE_M = 25;
    /** Below this the GPS is fine, and the warning is not shown. */
    static final double POOR_ACCURACY_M = 10;

    private boolean confirmed;
    private MapView map;
    private CampusMapLayers layers;
    private TextView selected;
    private CampusGraph graph;
    private final List<CampusRoom> rooms = new ArrayList<>();

    /** @param start where the map opens (the current position), or null for the whole campus */
    public static CalibrationDialog newInstance(@Nullable LatLng start, double accuracyM) {
        CalibrationDialog d = new CalibrationDialog();
        Bundle args = new Bundle();
        if (start != null) {
            args.putDouble(LAT, start.lat);
            args.putDouble(LNG, start.lng);
        }
        args.putDouble(ARG_ACCURACY, accuracyM);
        d.setArguments(args);
        return d;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(STYLE_NORMAL, R.style.Theme_AloneyNavigation_FullScreenDialog);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.dialog_calibration, container, false);
        selected = v.findViewById(R.id.calibration_selected);

        CampusMapLayers.configure(requireContext());
        map = new MapView(requireContext());
        FrameLayout box = v.findViewById(R.id.calibration_map);
        box.addView(map, 0, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        layers = new CampusMapLayers(map);
        map.addMapListener(new MapListener() {
            @Override
            public boolean onScroll(ScrollEvent event) {
                describeCenter();
                return false;
            }

            @Override
            public boolean onZoom(ZoomEvent event) {
                return false;
            }
        });

        double accuracy = requireArguments().getDouble(ARG_ACCURACY, -1);
        View warning = v.findViewById(R.id.calibration_warning);
        if (accuracy > POOR_ACCURACY_M) {
            ((TextView) v.findViewById(R.id.calibration_warning_title))
                    .setText(getString(R.string.calibration_warning_title, Math.round(accuracy)));
        } else {
            warning.setVisibility(View.GONE);
        }

        v.findViewById(R.id.calibration_close).setOnClickListener(x -> dismiss());
        v.findViewById(R.id.calibration_cancel).setOnClickListener(x -> dismiss());
        v.findViewById(R.id.calibration_confirm).setOnClickListener(x -> confirm());

        CampusRepository repo = CampusRepository.get(requireContext());
        repo.loadGraph(g -> {
            graph = g;
            layers.showPaths(g);
            map.post(this::centerAtStart);
            repo.allRooms(all -> {
                rooms.clear();
                rooms.addAll(all);
                describeCenter();
            });
        });
        repo.favorites(fav -> repo.recents(recent -> showKnownPlaces(v, fav, recent)));
        return v;
    }

    private void centerAtStart() {
        Bundle a = requireArguments();
        LatLng start = a.containsKey(LAT) ? new LatLng(a.getDouble(LAT), a.getDouble(LNG)) : null;
        CampusGraph.Snap snap = start == null || graph == null ? null : graph.snapToPath(start);
        if (snap != null && snap.projection.distance < NavigationManager.OFF_CAMPUS_M) {
            map.getController().setZoom(19.0);
            map.getController().setCenter(new GeoPoint(start.lat, start.lng));
        } else if (graph != null) {
            layers.zoomToCampus(graph);   // off campus (at home, say): show the whole campus
        }
        describeCenter();
    }

    /** Favourites first, then recent rooms, each once: one tap jumps to its entrance. */
    private void showKnownPlaces(View v, List<CampusRoom> favorites, List<CampusRoom> recent) {
        Map<String, CampusRoom> places = new LinkedHashMap<>();
        for (CampusRoom r : favorites) {
            places.put(r.number, r);
        }
        for (CampusRoom r : recent) {
            places.putIfAbsent(r.number, r);
        }
        ChipGroup group = v.findViewById(R.id.calibration_places);
        group.removeAllViews();
        for (CampusRoom r : places.values()) {
            Chip chip = (Chip) getLayoutInflater().inflate(R.layout.item_shortcut_chip, group, false);
            chip.setText(RoomAdapter.displayName(group, r));
            chip.setChipIconResource(R.drawable.ic_meeting_room);
            chip.setOnClickListener(x -> jumpTo(r));
            group.addView(chip);
        }
        View row = (View) group.getParent().getParent();
        row.setVisibility(places.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void jumpTo(CampusRoom r) {
        if (graph == null || r.entranceNodeId == null || graph.node(r.entranceNodeId) == null) {
            return;
        }
        Node n = graph.node(r.entranceNodeId);
        map.getController().animateTo(new GeoPoint(n.lat, n.lng), 19.5, 400L);
    }

    /** The pin's point, named after the nearest room entrance. */
    private void describeCenter() {
        if (selected == null || graph == null) {
            return;
        }
        LatLng c = center();
        CampusRoom nearest = null;
        double best = Double.MAX_VALUE;
        for (CampusRoom r : rooms) {
            Node n = r.entranceNodeId == null ? null : graph.node(r.entranceNodeId);
            if (n != null) {
                double d = c.distanceTo(LatLng.of(n));
                if (d < best) {
                    best = d;
                    nearest = r;
                }
            }
        }
        if (nearest == null) {
            selected.setText(R.string.calibration_point);
        } else if (best <= NEAR_ENTRANCE_M) {
            selected.setText(getString(R.string.calibration_near, RoomAdapter.displayName(selected, nearest)));
        } else {
            selected.setText(getString(R.string.calibration_from, WalkingEstimate.roundedMetres(best),
                    RoomAdapter.displayName(selected, nearest)));
        }
    }

    /** The map point under the pin's tip: the middle of the map. */
    @NonNull
    LatLng center() {
        GeoPoint g = (GeoPoint) map.getMapCenter();
        return new LatLng(g.getLatitude(), g.getLongitude());
    }

    private void confirm() {
        LatLng c = center();
        Bundle result = new Bundle();
        result.putDouble(LAT, c.lat);
        result.putDouble(LNG, c.lng);
        confirmed = true;
        getParentFragmentManager().setFragmentResult(RESULT, result);
        dismiss();
    }

    /** For tests: put the pin on a point, as dragging the map would. */
    @VisibleForTesting
    void moveTo(@NonNull LatLng p) {
        map.getController().setCenter(new GeoPoint(p.lat, p.lng));
        describeCenter();
    }

    @Override
    public void onDismiss(@NonNull android.content.DialogInterface dialog) {
        if (!confirmed && isAdded()) {
            getParentFragmentManager().setFragmentResult(CANCELLED, new Bundle());
        }
        super.onDismiss(dialog);
    }

    @Override
    public void onResume() {
        super.onResume();
        map.onResume();
    }

    @Override
    public void onPause() {
        map.onPause();
        super.onPause();
    }
}
