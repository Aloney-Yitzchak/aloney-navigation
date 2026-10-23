package il.org.ay.navigation;

import android.app.Application;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

/**
 * The app is in Hebrew only, so it always runs in Hebrew and right-to-left,
 * even on a phone whose system language is English. Without this, "start"
 * in a layout would mean left on such a phone, and the dialogs would be LTR.
 */
public class AloneyApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("he"));
    }
}
