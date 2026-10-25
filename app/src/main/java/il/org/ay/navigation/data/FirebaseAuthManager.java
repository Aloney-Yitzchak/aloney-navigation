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
    @Nullable
    private String lastEmail;

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
                    lastEmail = user.getEmail();
                    if (Boolean.TRUE.equals(token.getClaims().get("staff"))) {
                        callback.onResult(Result.STAFF);
                    } else {
                        auth.signOut();
                        callback.onResult(Result.NOT_STAFF);
                    }
                })
                .addOnFailureListener(e -> callback.onResult(toResult(e)));
    }

    // ------------------------------------------------------------ T24b: Google

    /** The school's Google Workspace domain: its accounts come first in the picker. */
    static final String SCHOOL_DOMAIN = "a-y.org.il";

    /**
     * The web client id that Firebase puts in google-services.json once Google sign-in is
     * enabled (as the resource default_web_client_id). Looked up by name, so the app still
     * builds with the CI placeholder config, which has none: then the button is hidden.
     */
    @androidx.annotation.Nullable
    static String webClientId(android.content.Context context) {
        int id = context.getResources().getIdentifier("default_web_client_id", "string", context.getPackageName());
        return id == 0 ? null : context.getString(id);
    }

    @Override
    public boolean canSignInWithGoogle(@NonNull android.content.Context context) {
        return webClientId(context) != null;
    }

    @Override
    public void signInWithGoogle(@NonNull android.app.Activity activity, @NonNull Callback callback) {
        String clientId = webClientId(activity);
        if (clientId == null) {
            callback.onResult(Result.FAILED);
            return;
        }
        com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption option =
                new com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption.Builder(clientId)
                        .setHostedDomainFilter(SCHOOL_DOMAIN)
                        .build();
        androidx.credentials.GetCredentialRequest request = new androidx.credentials.GetCredentialRequest.Builder()
                .addCredentialOption(option)
                .build();
        androidx.credentials.CredentialManager manager = androidx.credentials.CredentialManager.create(activity);
        java.util.concurrent.Executor main = androidx.core.content.ContextCompat.getMainExecutor(activity);
        manager.getCredentialAsync(activity, request, new android.os.CancellationSignal(), main,
                new androidx.credentials.CredentialManagerCallback<androidx.credentials.GetCredentialResponse,
                        androidx.credentials.exceptions.GetCredentialException>() {
                    @Override
                    public void onResult(androidx.credentials.GetCredentialResponse response) {
                        androidx.credentials.Credential c = response.getCredential();
                        if (!(c instanceof androidx.credentials.CustomCredential)
                                || !com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
                                .TYPE_GOOGLE_ID_TOKEN_CREDENTIAL.equals(c.getType())) {
                            callback.onResult(Result.FAILED);
                            return;
                        }
                        String idToken = com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
                                .createFrom(c.getData()).getIdToken();
                        auth.signInWithCredential(com.google.firebase.auth.GoogleAuthProvider.getCredential(idToken, null))
                                .addOnSuccessListener(r -> readClaim(true, callback))
                                .addOnFailureListener(e -> callback.onResult(toResult(e)));
                    }

                    @Override
                    public void onError(@NonNull androidx.credentials.exceptions.GetCredentialException e) {
                        callback.onResult(e instanceof androidx.credentials.exceptions.GetCredentialCancellationException
                                ? Result.CANCELLED : Result.FAILED);
                    }
                });
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
    public String lastEmail() {
        return lastEmail;
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
