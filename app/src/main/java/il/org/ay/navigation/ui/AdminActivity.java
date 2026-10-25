package il.org.ay.navigation.ui;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.VisibleForTesting;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.List;
import java.util.Objects;

import il.org.ay.navigation.R;
import il.org.ay.navigation.data.AuthManager;
import il.org.ay.navigation.data.CampusRepository;
import il.org.ay.navigation.model.CampusRoom;

/**
 * The staff screen (T24, FR-10, UC-06; design: docs/design/admin-login.png and admin.png).
 *
 * <p>Two states in one activity: signing in, and the list of rooms with an edit sheet for
 * each. Only an account with the staff claim reaches the list; the Firestore rules check
 * the same claim again on every save (AT-10, NFR-10).
 */
public class AdminActivity extends BaseActivity {

    private AuthManager auth;
    private CampusRepository repo;

    private View loginState;
    private View roomsState;
    private MaterialToolbar toolbar;
    private TextInputEditText email;
    private TextInputEditText password;
    private TextView loginError;
    private MaterialButton loginButton;
    private TextInputEditText search;
    private AdminRoomAdapter adapter;

    private boolean signedIn;
    @Nullable
    private BottomSheetDialog editSheet;
    private final Runnable onDataChanged = this::refreshRooms;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin);
        auth = AuthManager.get();
        repo = CampusRepository.get(this);

        toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        loginState = findViewById(R.id.admin_login_state);
        roomsState = findViewById(R.id.admin_rooms_state);
        email = findViewById(R.id.admin_email);
        password = findViewById(R.id.admin_password);
        loginError = findViewById(R.id.admin_login_error);
        loginButton = findViewById(R.id.admin_login_button);
        search = findViewById(R.id.admin_search);

        loginButton.setOnClickListener(v -> signIn());
        // T24b: Google, when the Firebase config has it (not in the CI placeholder)
        boolean google = auth.canSignInWithGoogle(this);
        findViewById(R.id.admin_google).setVisibility(google ? View.VISIBLE : View.GONE);
        findViewById(R.id.admin_or).setVisibility(google ? View.VISIBLE : View.GONE);
        findViewById(R.id.admin_google).setOnClickListener(v -> signInWithGoogle());
        password.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_NULL) {
                signIn();
                return true;
            }
            return false;
        });
        findViewById(R.id.admin_back).setOnClickListener(v -> finish());
        findViewById(R.id.admin_photos).setOnClickListener(v ->
                startActivity(new android.content.Intent(this, PhotoTasksActivity.class)));   // T20a
        findViewById(R.id.admin_update_map).setOnClickListener(v ->
                Snackbar.make(roomsState, R.string.coming_soon, Snackbar.LENGTH_SHORT).show());   // T28

        adapter = new AdminRoomAdapter(this::openEditor);
        RecyclerView list = findViewById(R.id.admin_rooms);
        list.setAdapter(adapter);
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                refreshRooms();
            }
        });

        // signed in on this phone before? then straight to the rooms
        showLogin();
        auth.checkStaff(result -> {
            if (result == AuthManager.Result.STAFF) {
                showRooms();
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        repo.addChangeListener(onDataChanged);
    }

    @Override
    protected void onStop() {
        repo.removeChangeListener(onDataChanged);
        super.onStop();
    }

    // ------------------------------------------------------------ state 1: sign in

    private void showLogin() {
        signedIn = false;
        loginState.setVisibility(View.VISIBLE);
        roomsState.setVisibility(View.GONE);
        toolbar.setTitle(R.string.admin_login_screen);
        toolbar.setSubtitle(null);
        toolbar.getMenu().clear();
    }

    private void signIn() {
        String mail = text(email);
        String pw = password.getText() == null ? "" : password.getText().toString();
        if (mail.isEmpty() || pw.isEmpty()) {
            showLoginError(R.string.admin_error_fields);
            return;
        }
        loginError.setVisibility(View.GONE);
        loginButton.setEnabled(false);
        loginButton.setText(R.string.admin_signing_in);
        auth.signIn(mail, pw, result -> {
            if (isFinishing() || isDestroyed()) {
                return;
            }
            loginButton.setEnabled(true);
            loginButton.setText(R.string.admin_login_button);
            switch (result) {
                case STAFF:
                    password.setText(null);
                    showRooms();
                    break;
                case NOT_STAFF:
                    showLoginError(R.string.admin_error_not_staff);
                    break;
                case WRONG_CREDENTIALS:
                    showLoginError(R.string.admin_error_credentials);
                    break;
                case TOO_MANY_TRIES:
                    showLoginError(R.string.admin_error_too_many);
                    break;
                case NO_NETWORK:
                    showLoginError(R.string.admin_error_offline);
                    break;
                default:
                    showLoginError(R.string.admin_error_failed);
                    break;
            }
        });
    }

    private void signInWithGoogle() {
        loginError.setVisibility(View.GONE);
        View button = findViewById(R.id.admin_google);
        button.setEnabled(false);
        auth.signInWithGoogle(this, result -> {
            if (isFinishing() || isDestroyed()) {
                return;
            }
            button.setEnabled(true);
            switch (result) {
                case STAFF:
                    showRooms();
                    break;
                case CANCELLED:
                    break;
                case NOT_STAFF:
                    // the account exists now; staff access is given to it by email
                    loginError.setText(getString(R.string.admin_error_not_staff_google, lastGoogleEmail()));
                    loginError.setVisibility(View.VISIBLE);
                    break;
                case NO_NETWORK:
                    showLoginError(R.string.admin_error_offline);
                    break;
                default:
                    showLoginError(R.string.admin_error_failed);
                    break;
            }
        });
    }

    /** The account just used, for the message (signed out again by then, so from Firebase's last user). */
    private String lastGoogleEmail() {
        String mail = auth.lastEmail();
        return mail == null ? "" : mail;
    }

    private void showLoginError(@StringRes int message) {
        loginError.setText(message);
        loginError.setVisibility(View.VISIBLE);
    }

    // ------------------------------------------------------------ state 2: the rooms

    private void showRooms() {
        signedIn = true;
        loginState.setVisibility(View.GONE);
        roomsState.setVisibility(View.VISIBLE);
        toolbar.setTitle(R.string.admin_rooms_screen);
        String mail = auth.email();
        toolbar.setSubtitle(mail == null ? null : getString(R.string.admin_rooms_subtitle, mail));
        toolbar.getMenu().clear();
        toolbar.inflateMenu(R.menu.admin_bar);
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_logout) {
                auth.signOut();
                showLogin();
                return true;
            }
            return false;
        });
        refreshRooms();
    }

    private void refreshRooms() {
        if (!signedIn) {
            return;
        }
        repo.search(text(search), rooms -> {
            adapter.submitList(rooms);
            findViewById(R.id.admin_rooms).setContentDescription(
                    rooms.isEmpty() ? getString(R.string.admin_no_rooms) : null);
        });
        repo.allRooms(this::showMapCard);
        showPhotoCount();
    }

    /** T20a: how many building photos wait on this phone to be exported. */
    private void showPhotoCount() {
        il.org.ay.navigation.data.PhotoStore store = il.org.ay.navigation.data.PhotoStore.get(this);
        new Thread(() -> {
            int n = store.total();
            runOnUiThread(() -> ((TextView) findViewById(R.id.admin_photos_info)).setText(
                    getResources().getQuantityString(R.plurals.photo_summary_phone, n, n)));
        }).start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (signedIn) {
            showPhotoCount();   // back from photographing
        }
    }

    private void showMapCard(List<CampusRoom> rooms) {
        String year = null;
        for (CampusRoom r : rooms) {
            if (r.planYear != null) {
                year = r.planYear;
                break;
            }
        }
        TextView title = findViewById(R.id.admin_map_title);
        title.setText(year == null ? getString(R.string.admin_map_unknown_year)
                : getString(R.string.admin_map_title, hebrewYear(year)));
        TextView info = findViewById(R.id.admin_map_info);
        info.setText(rooms.isEmpty() ? getString(R.string.admin_no_rooms)
                : getString(R.string.admin_map_info, rooms.size(), (int) repo.localVersion()));
    }

    /** "tashpaz" (as in rooms.csv) → תשפ״ז. Any other value is shown as it is. */
    static String hebrewYear(String key) {
        switch (key) {
            case "tashpaz":
                return "תשפ״ז";
            case "tashpach":
                return "תשפ״ח";
            case "tashpat":
                return "תשפ״ט";
            default:
                return key;
        }
    }

    // ------------------------------------------------------------ the edit sheet (sheet_room_edit.xml)

    @android.annotation.SuppressLint("InflateParams")   // a dialog's content has no parent to inflate into
    private void openEditor(CampusRoom room) {
        BottomSheetDialog sheet = new BottomSheetDialog(this);
        View view = LayoutInflater.from(this).inflate(R.layout.sheet_room_edit, null, false);
        sheet.setContentView(view);

        ((TextView) view.findViewById(R.id.room_edit_number)).setText(room.number);
        ((TextView) view.findViewById(R.id.room_edit_title)).setText(getString(R.string.room_edit_title, room.number));
        ((TextView) view.findViewById(R.id.room_edit_entrance)).setText(room.entranceNodeId == null
                ? getString(R.string.room_edit_no_entrance)
                : getString(R.string.room_edit_entrance, room.entranceNodeId));
        TextInputEditText name = view.findViewById(R.id.room_edit_name);
        TextInputEditText owner = view.findViewById(R.id.room_edit_owner);
        TextInputLayout nameLayout = view.findViewById(R.id.room_edit_name_layout);
        name.setText(room.name);
        owner.setText(room.owner);

        MaterialButton save = view.findViewById(R.id.room_edit_save);
        view.findViewById(R.id.room_edit_close).setOnClickListener(v -> sheet.dismiss());
        view.findViewById(R.id.room_edit_cancel).setOnClickListener(v -> sheet.dismiss());
        save.setOnClickListener(v -> {
            nameLayout.setError(null);
            save.setEnabled(false);
            save.setText(R.string.room_edit_saving);
            repo.updateRoom(room.number, text(name), text(owner), result -> {
                save.setEnabled(true);
                save.setText(R.string.room_edit_save);
                onSaved(sheet, room, result, nameLayout);
            });
        });
        sheet.setOnDismissListener(d -> editSheet = null);
        editSheet = sheet;
        sheet.show();
    }

    private void onSaved(BottomSheetDialog sheet, CampusRoom room, CampusRepository.SaveResult result,
                         TextInputLayout nameLayout) {
        switch (result) {
            case SAVED:
                sheet.dismiss();
                Snackbar.make(roomsState, getString(R.string.room_edit_saved, room.number), Snackbar.LENGTH_LONG).show();
                break;
            case PENDING:
                sheet.dismiss();
                Snackbar.make(roomsState, R.string.room_edit_pending, Snackbar.LENGTH_LONG).show();
                break;
            case EMPTY_NAME:
                nameLayout.setError(getString(R.string.room_edit_empty_name));
                break;
            case NOT_ALLOWED:
                sheet.dismiss();
                auth.signOut();
                showLogin();
                showLoginError(R.string.room_edit_not_allowed);
                break;
            case NO_NETWORK:
                sheetMessage(sheet, R.string.admin_error_offline);
                break;
            case NO_SUCH_ROOM:
                sheetMessage(sheet, R.string.room_edit_no_such_room);
                break;
            default:
                sheetMessage(sheet, R.string.admin_error_failed);
                break;
        }
    }

    private void sheetMessage(BottomSheetDialog sheet, @StringRes int message) {
        View root = sheet.findViewById(R.id.room_edit_save);
        Snackbar.make(root != null ? root : roomsState, message, Snackbar.LENGTH_LONG).show();
    }

    @VisibleForTesting
    @Nullable
    BottomSheetDialog editSheet() {
        return editSheet;
    }

    private static String text(@Nullable TextView field) {
        return field == null || field.getText() == null ? "" : field.getText().toString().trim();
    }

    // ------------------------------------------------------------ the list

    /** One row per room (item_room.xml): number, name, owner, and an edit button. */
    static class AdminRoomAdapter extends ListAdapter<CampusRoom, AdminRoomAdapter.Holder> {

        interface OnEdit {
            void onEdit(CampusRoom room);
        }

        private final OnEdit onEdit;

        AdminRoomAdapter(OnEdit onEdit) {
            super(new DiffUtil.ItemCallback<CampusRoom>() {
                @Override
                public boolean areItemsTheSame(@NonNull CampusRoom a, @NonNull CampusRoom b) {
                    return a.number.equals(b.number);
                }

                @Override
                public boolean areContentsTheSame(@NonNull CampusRoom a, @NonNull CampusRoom b) {
                    return Objects.equals(a.name, b.name) && Objects.equals(a.owner, b.owner)
                            && Objects.equals(a.entranceNodeId, b.entranceNodeId);
                }
            });
            this.onEdit = onEdit;
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_room, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            CampusRoom room = getItem(position);
            holder.number.setText(room.number);
            holder.name.setText(RoomAdapter.displayName(holder.itemView, room));
            holder.owner.setText(room.owner);
            holder.owner.setVisibility(room.owner == null || room.owner.isEmpty() ? View.GONE : View.VISIBLE);
            String edit = holder.itemView.getContext().getString(R.string.admin_edit_room, room.number);
            holder.edit.setContentDescription(edit);
            holder.edit.setOnClickListener(v -> onEdit.onEdit(room));
            holder.itemView.setOnClickListener(v -> onEdit.onEdit(room));
        }

        static class Holder extends RecyclerView.ViewHolder {
            final TextView number;
            final TextView name;
            final TextView owner;
            final MaterialButton edit;

            Holder(@NonNull View item) {
                super(item);
                number = item.findViewById(R.id.room_number);
                name = item.findViewById(R.id.room_name);
                owner = item.findViewById(R.id.room_owner);
                edit = item.findViewById(R.id.room_favorite);
                edit.setIconResource(R.drawable.ic_edit);
                edit.setIconTintResource(R.color.primary);
                item.findViewById(R.id.room_distance).setVisibility(View.GONE);
            }
        }
    }
}
