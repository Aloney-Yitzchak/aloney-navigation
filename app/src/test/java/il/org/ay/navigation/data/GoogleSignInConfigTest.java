package il.org.ay.navigation.data;

import static org.junit.Assert.assertNull;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/**
 * T24b: with the CI placeholder config (no Google client in it) the app still builds and the
 * Google button is simply not offered, instead of crashing.
 */
@RunWith(RobolectricTestRunner.class)
public class GoogleSignInConfigTest {

    @Test
    public void withoutAWebClient_googleSignInIsNotOffered() {
        Context context = ApplicationProvider.getApplicationContext();
        org.junit.Assume.assumeTrue("needs the placeholder config",
                context.getResources().getIdentifier("default_web_client_id", "string", context.getPackageName()) == 0);
        assertNull(FirebaseAuthManager.webClientId(context));
    }
}
