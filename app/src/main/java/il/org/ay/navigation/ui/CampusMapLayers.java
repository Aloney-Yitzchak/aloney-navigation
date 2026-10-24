package il.org.ay.navigation.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import org.osmdroid.config.Configuration;
import org.osmdroid.config.IConfigurationProvider;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.CustomZoomButtonsController;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.CopyrightOverlay;
import org.osmdroid.views.overlay.GroundOverlay;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Overlay;
import org.osmdroid.views.overlay.Polygon;
import org.osmdroid.views.overlay.Polyline;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import il.org.ay.navigation.R;
import il.org.ay.navigation.data.CampusGraph;
import il.org.ay.navigation.data.Route;
import il.org.ay.navigation.model.LatLng;
import il.org.ay.navigation.model.Node;

/**
 * The layers of the campus map (T29, FR-05, SDD 2.4), from bottom to top:
 * <ol>
 *   <li>OpenStreetMap tiles</li>
 *   <li>the school plan of the current year, half see-through (GroundOverlay)</li>
 *   <li>the campus paths, from the graph in Room</li>
 *   <li>the route, in orange (Polyline)</li>
 *   <li>the destination, and "you are here" with its accuracy circle (Marker)</li>
 *   <li>the OpenStreetMap credit, always visible</li>
 * </ol>
 *
 * <p>OpenStreetMap tile policy: the app names itself in the User-Agent, keeps the tiles it
 * has shown in a cache, and never downloads tiles ahead (no offline packs). Without a
 * network the map still shows what navigation needs: the paths, the route and the plan
 * all come from the phone, not from the tiles.
 */
public class CampusMapLayers {

    /** Plan transparency: 0 = solid, 1 = invisible. */
    static final float PLAN_TRANSPARENCY = 0.35f;
    /** How far past the campus the map may scroll, in degrees (≈ 300 m). */
    private static final double MARGIN_DEG = 0.003;

    private final Context context;
    private final MapView map;
    private final GroundOverlay plan = new GroundOverlay();
    private final List<Polyline> paths = new ArrayList<>();
    private final Polyline route = new Polyline();
    private final Marker destination;
    private final Marker here;
    private final Polygon hereAccuracy = new Polygon();
    private final CopyrightOverlay credit;
    private boolean planShown;
    private boolean positionShown;

    /**
     * osmdroid settings, once per app start, before the MapView is created: our own
     * User-Agent (the tile policy blocks library defaults) and a tile cache inside the app.
     */
    public static void configure(@NonNull Context context) {
        IConfigurationProvider c = Configuration.getInstance();
        c.setUserAgentValue(context.getPackageName() + " (Aloney Yitzchak campus navigation)");
        File base = new File(context.getFilesDir(), "osmdroid");
        c.setOsmdroidBasePath(base);
        c.setOsmdroidTileCache(new File(base, "tiles"));
    }

    public CampusMapLayers(@NonNull MapView map) {
        this.map = map;
        this.context = map.getContext();

        map.setTileSource(TileSourceFactory.MAPNIK);
        map.setMultiTouchControls(true);
        map.getZoomController().setVisibility(CustomZoomButtonsController.Visibility.NEVER);
        map.setMinZoomLevel(15.0);
        map.setMaxZoomLevel(20.5);   // tiles stop at 19; beyond that osmdroid enlarges them
        map.setTilesScaledToDpi(true);

        plan.setTransparency(PLAN_TRANSPARENCY);

        route.getOutlinePaint().setColor(ContextCompat.getColor(context, R.color.accent));
        route.getOutlinePaint().setStrokeWidth(dp(7));
        route.getOutlinePaint().setStrokeCap(Paint.Cap.ROUND);
        route.getOutlinePaint().setStrokeJoin(Paint.Join.ROUND);
        route.setInfoWindow(null);

        destination = new Marker(map);
        destination.setIcon(tinted(R.drawable.ic_location_on, R.color.accent, 40));
        destination.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        destination.setInfoWindow(null);

        here = new Marker(map);
        here.setIcon(ContextCompat.getDrawable(context, R.drawable.bg_you_are_here));
        here.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
        here.setTitle(context.getString(R.string.map_you_are_here));
        here.setInfoWindow(null);

        hereAccuracy.getFillPaint().setColor(0x221A73E8);
        hereAccuracy.getOutlinePaint().setColor(0x551A73E8);
        hereAccuracy.getOutlinePaint().setStrokeWidth(dp(1));
        hereAccuracy.setInfoWindow(null);

        credit = new CopyrightOverlay(context);
        credit.setAlignRight(true);   // bottom right, as the tile policy asks
        rebuild();
    }

    /** Draws every campus path and limits scrolling to the campus. */
    public void showPaths(@NonNull CampusGraph graph) {
        paths.clear();
        for (int[] s : graph.segments()) {
            Node a = graph.node(s[0]);
            Node b = graph.node(s[1]);
            Polyline line = new Polyline();
            line.setPoints(java.util.Arrays.asList(geo(a.lat, a.lng), geo(b.lat, b.lng)));
            line.getOutlinePaint().setColor(Color.WHITE);
            line.getOutlinePaint().setStrokeWidth(dp(4));
            line.getOutlinePaint().setStrokeCap(Paint.Cap.ROUND);
            line.setInfoWindow(null);
            paths.add(line);
        }
        BoundingBox box = bounds(graph);
        if (box != null) {
            map.setScrollableAreaLimitDouble(new BoundingBox(box.getLatNorth() + MARGIN_DEG,
                    box.getLonEast() + MARGIN_DEG, box.getLatSouth() - MARGIN_DEG, box.getLonWest() - MARGIN_DEG));
        }
        rebuild();
    }

    /** Shows the whole campus. Call after the map has its size (MapView.post). */
    public void zoomToCampus(@NonNull CampusGraph graph) {
        BoundingBox box = bounds(graph);
        if (box != null) {
            map.zoomToBoundingBox(box, false, (int) dp(24));
        }
    }

    /**
     * The school plan image with its four corners on the map. Four corners (not two plus a
     * rotation) let the image be skewed as well, so it follows the full affine fit of SDD 11.2.
     */
    public void showPlan(@NonNull Bitmap image, @NonNull LatLng topLeft, @NonNull LatLng topRight,
                         @NonNull LatLng bottomRight, @NonNull LatLng bottomLeft) {
        plan.setImage(image);
        plan.setPosition(geo(topLeft), geo(topRight), geo(bottomRight), geo(bottomLeft));
        planShown = true;
        rebuild();
    }

    public void hidePlan() {
        planShown = false;
        rebuild();
    }

    /** SDD 4: drawRoute(Route). Null hides the route. */
    public void drawRoute(@Nullable Route r) {
        List<GeoPoint> points = new ArrayList<>();
        if (r != null) {
            for (Node n : r.nodes()) {
                points.add(geo(n.lat, n.lng));
            }
            Node end = r.destination();
            destination.setPosition(geo(end.lat, end.lng));
        }
        route.setPoints(points);
        destination.setVisible(r != null);
        map.invalidate();
    }

    /** "You are here", with a circle as wide as the position is unsure. */
    public void showPosition(@NonNull LatLng p, double accuracyM) {
        here.setPosition(geo(p));
        hereAccuracy.setPoints(Polygon.pointsAsCircle(geo(p), Math.max(accuracyM, 1)));
        positionShown = true;
        here.setVisible(true);
        hereAccuracy.setVisible(true);
        map.invalidate();
    }

    @NonNull
    public MapView getMap() {
        return map;
    }

    /** The overlays, in drawing order (also for the tests). */
    @NonNull
    public List<Overlay> overlays() {
        return map.getOverlays();
    }

    @NonNull
    public GroundOverlay planOverlay() {
        return plan;
    }

    /** Puts the layers back in their order: bottom first. */
    private void rebuild() {
        List<Overlay> o = map.getOverlays();
        o.clear();
        if (planShown) {
            o.add(plan);
        }
        o.addAll(paths);
        o.add(route);
        o.add(hereAccuracy);
        o.add(destination);
        o.add(here);
        o.add(credit);
        if (route.getActualPoints().isEmpty()) {
            destination.setVisible(false);
        }
        here.setVisible(positionShown);
        hereAccuracy.setVisible(positionShown);
        map.invalidate();
    }

    @Nullable
    private static BoundingBox bounds(CampusGraph graph) {
        List<GeoPoint> points = new ArrayList<>();
        for (Node n : graph.nodes()) {
            points.add(geo(n.lat, n.lng));
        }
        return points.isEmpty() ? null : BoundingBox.fromGeoPointsSafe(points);
    }

    private static GeoPoint geo(double lat, double lng) {
        return new GeoPoint(lat, lng);
    }

    private static GeoPoint geo(LatLng p) {
        return new GeoPoint(p.lat, p.lng);
    }

    private float dp(float v) {
        return v * context.getResources().getDisplayMetrics().density;
    }

    private Drawable tinted(int icon, int color, int sizeDp) {
        Drawable d = ContextCompat.getDrawable(context, icon).mutate();
        d.setTint(ContextCompat.getColor(context, color));
        int px = (int) dp(sizeDp);
        d.setBounds(0, 0, px, px);
        Bitmap bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888);
        d.draw(new android.graphics.Canvas(bitmap));
        return new android.graphics.drawable.BitmapDrawable(context.getResources(), bitmap);
    }
}
