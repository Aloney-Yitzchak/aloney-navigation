package il.org.ay.navigation.ui;

import android.content.Context;
import android.content.res.Configuration;

import androidx.appcompat.app.AppCompatActivity;

import java.util.Locale;

/**
 * Base class for every screen. The app is in Hebrew only, so each screen is
 * created in Hebrew and right-to-left, even on a phone whose system language is
 * English: layouts, "start"/"end", and dialogs opened from the screen.
 */
public abstract class BaseActivity extends AppCompatActivity {

    public static final Locale HEBREW = Locale.forLanguageTag("he");

    @Override
    protected void attachBaseContext(Context base) {
        Configuration config = new Configuration(base.getResources().getConfiguration());
        config.setLocale(HEBREW);
        config.setLayoutDirection(HEBREW);
        super.attachBaseContext(base.createConfigurationContext(config));
    }
}
