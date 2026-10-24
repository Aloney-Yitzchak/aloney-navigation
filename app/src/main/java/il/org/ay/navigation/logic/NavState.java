package il.org.ay.navigation.logic;

/**
 * The five states of navigation (SDD 6, the state diagram). NavigationManager keeps one,
 * and every event in the diagram is a method that moves it, so the map screen and the
 * camera screen always show the same state.
 */
public enum NavState {
    /** No destination yet, or the user cancelled, or there is no way to the destination. */
    WAITING_FOR_DESTINATION,
    /** A destination was chosen; waiting for a position (or off campus) to plan from. */
    CALCULATING_ROUTE,
    /** Following a route. */
    NAVIGATING,
    /** The user is marking where they are (T23). */
    MANUAL_CALIBRATION,
    /** Within 5 m of the destination's entrance (FR-06). Final. */
    ARRIVED
}
