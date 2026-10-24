package il.org.ay.navigation.data;

/**
 * Where the shared campus data comes from. FirestoreSource is the real one;
 * tests use a fake, so the sync rules can be checked without a network.
 */
public interface CampusRemote {

    interface Callback<T> {
        void onResult(T value);

        void onError(Exception e);
    }

    /** meta/version.number in the cloud. */
    void fetchVersion(Callback<Long> callback);

    /** Everything: nodes, edges and rooms, plus the version they belong to. */
    void fetchAll(Callback<CampusSnapshot> callback);
}
