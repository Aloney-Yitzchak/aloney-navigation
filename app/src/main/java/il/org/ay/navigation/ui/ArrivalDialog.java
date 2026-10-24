package il.org.ay.navigation.ui;

import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;

import il.org.ay.navigation.R;
import il.org.ay.navigation.logic.NavigationManager;
import il.org.ay.navigation.logic.WalkingEstimate;
import il.org.ay.navigation.model.CampusRoom;

/**
 * "You have arrived" (T19, FR-06, AT-06; layout dialog_arrival.xml). Shown by the map
 * screen or the camera screen when NavigationManager reaches ARRIVED. No door-sign photo
 * (design finding 10).
 */
public class ArrivalDialog extends DialogFragment {

    public static final String TAG = "arrival";
    private static final String ARG_NAME = "name";
    private static final String ARG_OWNER = "owner";
    private static final String ARG_MINUTES = "minutes";
    private static final String ARG_METRES = "metres";

    /** SDD 4: show(CampusRoom). Shows the dialog once, even if called again. */
    public static void show(@NonNull FragmentManager fm, @NonNull CharSequence name, @NonNull CampusRoom room,
                            @NonNull NavigationManager nav) {
        if (fm.findFragmentByTag(TAG) != null || fm.isStateSaved()) {
            return;
        }
        ArrivalDialog d = new ArrivalDialog();
        Bundle args = new Bundle();
        args.putCharSequence(ARG_NAME, name);
        args.putString(ARG_OWNER, room.owner);
        args.putInt(ARG_MINUTES, nav.minutesWalked());
        args.putInt(ARG_METRES, WalkingEstimate.roundedMetres(nav.startDistance()));
        d.setArguments(args);
        d.show(fm, TAG);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Dialog d = super.onCreateDialog(savedInstanceState);
        if (d.getWindow() != null) {
            d.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));   // the card has its own
        }
        return d;
    }

    @Override
    public void onStart() {
        super.onStart();
        // a dialog window wraps its content by default, which squeezed the card; the card
        // keeps its own 24 dp margins inside the full width
        if (getDialog() != null && getDialog().getWindow() != null) {
            android.view.Window w = getDialog().getWindow();
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            int margin = getResources().getDimensionPixelSize(R.dimen.spacing_large);
            w.getDecorView().setPadding(margin, 0, margin, 0);   // the card's side margins
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.dialog_arrival, container, false);
        Bundle a = requireArguments();
        ((TextView) v.findViewById(R.id.arrival_room)).setText(a.getCharSequence(ARG_NAME));
        TextView owner = v.findViewById(R.id.arrival_detail);
        String o = a.getString(ARG_OWNER);
        owner.setText(o);
        owner.setVisibility(o == null || o.isEmpty() ? View.GONE : View.VISIBLE);
        TextView summary = v.findViewById(R.id.arrival_summary);
        int minutes = a.getInt(ARG_MINUTES);
        minutes = Math.max(1, minutes);
        summary.setText(getResources().getQuantityString(R.plurals.arrival_summary, minutes, minutes, a.getInt(ARG_METRES)));
        // SDD 4: onDone()
        v.findViewById(R.id.arrival_new_destination).setOnClickListener(x -> newDestination());
        v.findViewById(R.id.arrival_close).setOnClickListener(x -> dismiss());
        return v;
    }

    /** "New destination": back to search, closing the navigation screens. */
    private void newDestination() {
        startActivity(new Intent(requireContext(), SearchActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        dismiss();
        requireActivity().finish();
    }
}
