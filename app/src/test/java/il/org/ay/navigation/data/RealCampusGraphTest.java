package il.org.ay.navigation.data;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import il.org.ay.navigation.model.Edge;
import il.org.ay.navigation.model.Node;

/**
 * The real campus (campus-data/*.csv, the same files uploaded to Firestore):
 * every room can be reached from every other room, and fast enough (NFR-02).
 */
public class RealCampusGraphTest {

    private static CampusGraph graph;
    private static final Set<Integer> entrances = new LinkedHashSet<>();

    /** Gradle runs unit tests in app/, so the data is one folder up. */
    private static List<String[]> rows(String file) throws IOException {
        Path path = Paths.get("..", "campus-data", file);
        List<String[]> out = new ArrayList<>();
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        for (String line : lines.subList(1, lines.size())) {
            if (!line.isEmpty()) {
                out.add(line.split(",", -1));
            }
        }
        return out;
    }

    @BeforeClass
    public static void load() throws IOException {
        List<Node> nodes = new ArrayList<>();
        for (String[] r : rows("nodes.csv")) {          // id,lat,lng,kind,label
            nodes.add(new Node(Integer.parseInt(r[0]), Double.parseDouble(r[1]), Double.parseDouble(r[2])));
        }
        List<Edge> edges = new ArrayList<>();
        int id = 1;
        for (String[] r : rows("edges.csv")) {          // from_id,to_id,length_m
            edges.add(new Edge(id++, Integer.parseInt(r[0]), Integer.parseInt(r[1]),
                    r[2].isEmpty() ? 0 : Double.parseDouble(r[2])));
        }
        for (String[] r : rows("rooms.csv")) {          // number,name,owner,entrance_node_id,plan_year
            String entrance = r[r.length - 2];          // from the end: a name may hold a comma
            if (!entrance.isEmpty()) {
                entrances.add(Integer.parseInt(entrance));
            }
        }
        graph = new CampusGraph(nodes, edges);
    }

    @Test
    public void theWholeCampusIsOneConnectedGraph() {
        assertTrue(graph.nodeCount() > 100);
        assertTrue(graph.isConnected());
    }

    @Test
    public void everyEntrance_reachesEveryOtherEntrance_inUnderASecond() {
        long slowestNanos = 0;
        for (int from : entrances) {
            for (int to : entrances) {
                long start = System.nanoTime();
                Route route = graph.findPath(from, to);
                slowestNanos = Math.max(slowestNanos, System.nanoTime() - start);
                assertNotNull("no route " + from + " → " + to, route);
                // a walk is never shorter than the straight line between its ends
                Node a = graph.node(from);
                Node b = graph.node(to);
                assertTrue(route.totalDistance() + 1e-6 >= a.distanceTo(b));
            }
        }
        // NFR-02: under one second; in practice well under a millisecond
        assertTrue("slowest route took " + slowestNanos / 1e6 + " ms", slowestNanos < 1_000_000_000L);
    }

    @Test
    public void routeLength_isTheSumOfItsSegments() {
        for (int to : entrances) {
            Route r = graph.findPath(entrances.iterator().next(), to);
            double sum = 0;
            for (int i = 1; i < r.nodes().size(); i++) {
                sum += r.nodes().get(i - 1).distanceTo(r.nodes().get(i));
            }
            // edges.csv lengths are rounded to 0.1 m per segment
            assertTrue(Math.abs(sum - r.totalDistance()) < 0.05 * r.nodes().size() + 1e-6);
        }
    }
}
