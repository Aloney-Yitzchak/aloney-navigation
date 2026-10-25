package il.org.ay.navigation.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.google.firebase.auth.FirebaseAuth;

/**
 * Staff sign-in (T24, FR-10, SDD 4). A staff member is a Firebase Authentication user whose
 * token has the custom claim {@code staff == true}, given with campus-data/set_staff_claim.py.
 *
 * <p>The app only uses the claim to decide what to show. What really protects the data are
 * the Firestore security rules, which check the same claim on every write (NFR-10).
 */
public abstract class AuthManager {

    public enum Result {
        /** Signed in, with the staff claim. */
        STAFF,
        /** Signed in without the claim, or not signed in at all. */
        NOT_STAFF,
        /** Wrong email or password, or no such account. */
        WRONG_CREDENTIALS,
        /** Too many wrong tries: Firebase blocks the account for a while. */
        TOO_MANY_TRIES,
        /** No network. Signing in needs one (NFR-06: FR-10 is the one online feature). */
        NO_NETWORK,
        /** Anything else. */
        FAILED
    }

    public interface Callback {
        void onResult(@NonNull Result result);
    }

    private static AuthManager instance;

    public static synchronized AuthManager get() {
        if (instance == null) {
            instance = new FirebaseAuthManager(FirebaseAuth.getInstance());
        }
        return instance;
    }

    /** Emulator tests sign in without a network or a real account. */
    @VisibleForTesting
    public static synchronized void useForTesting(@Nullable AuthManager fake) {
        instance = fake;
    }

    /**
     * Signs in and checks the claim. Without the claim the user is signed out again and the
     * result is NOT_STAFF, so a student account never stays signed in on the staff screen.
     */
    public abstract void signIn(@NonNull String email, @NonNull String password, @NonNull Callback callback);

    /**
     * Whether the user already signed in on this phone is staff. Uses the saved token, so it
     * also works offline; the rules check a fresh token anyway when saving.
     */
    public abstract void checkStaff(@NonNull Callback callback);

    /** The signed-in user's email, or null. */
    @Nullable
    public abstract String email();

    public abstract void signOut();
}
