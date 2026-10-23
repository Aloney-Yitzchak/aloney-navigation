package il.org.ay.navigation.ui;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import il.org.ay.navigation.R;

/**
 * Destination search. Placeholder from T07 so the app has somewhere to go
 * after the splash screen; the search box and results list come in T09.
 */
public class SearchActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);
    }
}
