package il.org.ay.navigation.data;

import static org.junit.Assert.assertEquals;

import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.firestore.FirebaseFirestoreException;

import org.junit.Test;

/** T24: what each answer from Firebase means for the staff screen. */
public class ServerAnswersTest {

    @Test
    public void signIn_wrongPasswordOrNoSuchUser_sayTheSameThing() {
        // the same message for both, so the screen does not reveal which emails have accounts
        assertEquals(AuthManager.Result.WRONG_CREDENTIALS, FirebaseAuthManager.toResult(
                new FirebaseAuthInvalidCredentialsException("ERROR_WRONG_PASSWORD", "wrong")));
        assertEquals(AuthManager.Result.WRONG_CREDENTIALS, FirebaseAuthManager.toResult(
                new FirebaseAuthInvalidUserException("ERROR_USER_NOT_FOUND", "none")));
    }

    @Test
    public void signIn_noNetwork() {
        assertEquals(AuthManager.Result.NO_NETWORK,
                FirebaseAuthManager.toResult(new FirebaseNetworkException("offline")));
        assertEquals(AuthManager.Result.FAILED,
                FirebaseAuthManager.toResult(new IllegalStateException("?")));
    }

    @Test
    public void save_refusedByTheRules_isNotAllowed() {   // AT-10
        assertEquals(CampusRepository.SaveResult.NOT_ALLOWED, FirestoreSource.toSaveResult(
                new FirebaseFirestoreException("no", FirebaseFirestoreException.Code.PERMISSION_DENIED)));
        assertEquals(CampusRepository.SaveResult.NOT_ALLOWED, FirestoreSource.toSaveResult(
                new FirebaseFirestoreException("no", FirebaseFirestoreException.Code.UNAUTHENTICATED)));
    }

    @Test
    public void save_otherAnswers() {
        assertEquals(CampusRepository.SaveResult.NO_NETWORK, FirestoreSource.toSaveResult(
                new FirebaseFirestoreException("x", FirebaseFirestoreException.Code.UNAVAILABLE)));
        assertEquals(CampusRepository.SaveResult.NO_SUCH_ROOM, FirestoreSource.toSaveResult(
                new FirebaseFirestoreException("x", FirebaseFirestoreException.Code.NOT_FOUND)));
        assertEquals(CampusRepository.SaveResult.FAILED, FirestoreSource.toSaveResult(new RuntimeException()));
    }
}
