package il.org.ay.navigation.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.FirebaseTooManyRequestsException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseUser;

/** {@link AuthManager} with Firebase Authentication, email and password. */
class FirebaseAuthManager extends AuthManager {

    private final FirebaseAuth auth;

    FirebaseAuthManager(FirebaseAuth auth) {
        this.auth = auth;
    }

    @Override
    public void signIn(@NonNull String email, @NonNull String password, @NonNull Callback callback) {
        auth.signInWithEmailAndPassword(email.trim(), password)
                // a fresh token: a claim given a minute ago counts right away
                .addOnSuccessListener(r -> readClaim(true, callback))
                .addOnFailureListener(e -> callback.onResult(toResult(e)));
    }

    @Override
    public void checkStaff(@NonNull Callback callback) {
        if (auth.getCurrentUser() == null) {
            callback.onResult(Result.NOT_STAFF);
            return;
        }
        readClaim(false, callback);
    }

    private void readClaim(boolean fresh, Callback callback) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            callback.onResult(Result.NOT_STAFF);
            return;
        }
        user.getIdToken(fresh)
                .addOnSuccessListener(token -> {
                    if (Boolean.TRUE.equals(token.getClaims().get("staff"))) {
                        callback.onResult(Result.STAFF);
                    } else {
                        auth.signOut();
                        callback.onResult(Result.NOT_STAFF);
                    }
                })
                .addOnFailureListener(e -> callback.onResult(toResult(e)));
    }

    static Result toResult(Exception e) {
        if (e instanceof FirebaseAuthInvalidCredentialsException || e instanceof FirebaseAuthInvalidUserException) {
            return Result.WRONG_CREDENTIALS;
        }
        if (e instanceof FirebaseTooManyRequestsException) {
            return Result.TOO_MANY_TRIES;
        }
        if (e instanceof FirebaseNetworkException) {
            return Result.NO_NETWORK;
        }
        return Result.FAILED;
    }

    @Nullable
    @Override
    public String email() {
        FirebaseUser user = auth.getCurrentUser();
        return user == null ? null : user.getEmail();
    }

    @Override
    public void signOut() {
        auth.signOut();
    }
}
