package il.org.ay.navigation.ui;

import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import il.org.ay.navigation.R;
import il.org.ay.navigation.data.AuthManager;
import il.org.ay.navigation.data.CampusRepository;
import il.org.ay.navigation.data.PhotoStore;
import il.org.ay.navigation.data.PhotoTasks;
import il.org.ay.navigation.model.PhotoTask;

/**
 * T20a: the buildings to photograph for the building model (SDD 10, step 1), shared by all
 * staff phones through Firestore. Staff add and edit buildings here, open the camera for one,
 * and export this phone's photos as one zip for training (T21).
 */
public class PhotoTasksActivity extends BaseActivity {

    private PhotoTasks tasks;
    private PhotoStore store;
    @Nullable
    private PhotoTasks.Subscription subscription;
    private TaskAdapter adapter;
    private List<PhotoTask> current = new ArrayList<>();
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_photo_tasks);
        tasks = PhotoTasks.get();
        store = PhotoStore.get(this);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_delete_photos) {
                confirmDeleteAll();
                return true;
            }
            return false;
        });
        adapter = new TaskAdapter(this::openCamera, this::openEditor);
        ((RecyclerView) findViewById(R.id.photo_tasks)).setAdapter(adapter);
        findViewById(R.id.photo_add_task).setOnClickListener(v -> openEditor(null));
        findViewById(R.id.photo_export).setOnClickListener(v -> export());

        // only staff manage the photos: the rules refuse anyone else's writes anyway
        AuthManager.get().checkStaff(result -> {
            if (result != AuthManager.Result.STAFF) {
                finish();
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        subscription = tasks.listen(list -> {
            current = list;
            refresh();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();   // back from the camera: new photos on this phone
    }

    @Override
    protected void onStop() {
        if (subscription != null) {
            subscription.remove();
            subscription = null;
        }
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        io.shutdown();
        super.onDestroy();
    }

    private void refresh() {
        List<PhotoTask> list = current;
        io.execute(() -> {
            List<Row> rows = new ArrayList<>();
            int onPhone = store.total();
            int taken = 0;
            int target = 0;
            for (PhotoTask t : list) {
                rows.add(new Row(t, store.count(t.label)));
                taken += Math.min(t.taken, t.target);
                target += t.target;
            }
            int allTaken = taken;
            int allTarget = target;
            main.post(() -> {
                if (isDestroyed()) {
                    return;
                }
                adapter.submitList(rows);
                ((TextView) findViewById(R.id.photo_summary)).setText(
                        getResources().getQuantityString(R.plurals.photo_summary_phone, onPhone, onPhone));
                ((TextView) findViewById(R.id.photo_summary_all)).setText(
                        getString(R.string.photo_summary_all, allTaken, allTarget));
                findViewById(R.id.photo_tasks).setContentDescription(
                        rows.isEmpty() ? getString(R.string.photo_tasks_empty) : null);
            });
        });
    }

    private void openCamera(PhotoTask task) {
        startActivity(PhotoCaptureActivity.intent(this, task));
    }

    // ------------------------------------------------------------ add / edit a building

    @android.annotation.SuppressLint("InflateParams")   // a dialog's content has no parent to inflate into
    private void openEditor(@Nullable PhotoTask existing) {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_photo_task, null, false);
        TextInputEditText name = view.findViewById(R.id.photo_task_name_field);
        TextInputEditText target = view.findViewById(R.id.photo_task_target_field);
        TextInputEditText note = view.findViewById(R.id.photo_task_note_field);
        TextInputLayout nameLayout = view.findViewById(R.id.photo_task_name_layout);
        TextInputLayout targetLayout = view.findViewById(R.id.photo_task_target_layout);
        name.setText(existing == null ? null : existing.name);
        target.setText(String.valueOf(existing == null ? 40 : existing.target));
        note.setText(existing == null ? null : existing.note);

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                .setTitle(existing == null ? R.string.photo_add_title : R.string.photo_edit_title)
                .setView(view)
                .setPositiveButton(R.string.room_edit_save, null)
                .setNegativeButton(R.string.action_cancel, null);
        if (existing != null && !existing.isOther()) {
            builder.setNeutralButton(R.string.photo_task_delete, (d, w) -> confirmDeleteTask(existing));
        }
        androidx.appcompat.app.AlertDialog dialog = builder.create();
        dialog.setOnShowListener(d -> dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String n = text(name);
                    int count = parse(text(target));
                    nameLayout.setError(n.isEmpty() ? getString(R.string.photo_error_name) : null);
                    targetLayout.setError(count < 1 || count > 500 ? getString(R.string.photo_error_target) : null);
                    if (n.isEmpty() || count < 1 || count > 500) {
                        return;
                    }
                    PhotoTask task = existing != null
                            ? new PhotoTask(existing.label, n, count, text(note), existing.order)
                            : new PhotoTask(PhotoTask.nextLabel(current), n, count, text(note),
                                    PhotoTask.nextOrder(current));
                    tasks.save(task, result -> showResult(result));
                    dialog.dismiss();
                }));
        dialog.show();
    }

    private void confirmDeleteTask(PhotoTask task) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(task.name)
                .setMessage(R.string.photo_task_delete_message)
                .setPositiveButton(R.string.photo_delete_confirm, (d, w) -> tasks.delete(task.label, this::showResult))
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void showResult(CampusRepository.SaveResult result) {
        if (result == CampusRepository.SaveResult.SAVED || isDestroyed()) {
            return;   // the list updates by itself
        }
        int message = result == CampusRepository.SaveResult.NOT_ALLOWED ? R.string.room_edit_not_allowed
                : result == CampusRepository.SaveResult.NO_NETWORK ? R.string.admin_error_offline
                : R.string.admin_error_failed;
        Snackbar.make(findViewById(R.id.photo_tasks), message, Snackbar.LENGTH_LONG).show();
    }

    // ------------------------------------------------------------ export and delete

    private void export() {
        Snackbar progress = Snackbar.make(findViewById(R.id.photo_tasks), R.string.photo_exporting, Snackbar.LENGTH_INDEFINITE);
        progress.show();
        List<PhotoTask> list = current;
        io.execute(() -> {
            File zip = null;
            int count;
            try {
                File dir = new File(getCacheDir(), "exports");
                File[] old = dir.listFiles();
                if (old != null) {
                    for (File f : old) {
                        //noinspection ResultOfMethodCallIgnored
                        f.delete();   // only the latest export is kept
                    }
                }
                //noinspection ResultOfMethodCallIgnored
                dir.mkdirs();
                String stamp = new SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.ROOT).format(new Date());
                zip = new File(dir, "aloney-buildings_" + stamp + ".zip");
                count = store.exportZip(zip, list);
            } catch (Exception e) {
                count = -1;
            }
            File result = zip;
            int photos = count;
            main.post(() -> {
                progress.dismiss();
                if (isDestroyed()) {
                    return;
                }
                if (photos == 0) {
                    Snackbar.make(findViewById(R.id.photo_tasks), R.string.photo_export_nothing, Snackbar.LENGTH_LONG).show();
                } else if (photos < 0) {
                    Snackbar.make(findViewById(R.id.photo_tasks), R.string.photo_export_failed, Snackbar.LENGTH_LONG).show();
                } else {
                    share(result);
                }
            });
        });
    }

    /** Opens the share sheet: Drive, Gmail, Quick Share to a computer… */
    private void share(File zip) {
        Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".exports", zip);
        Intent send = new Intent(Intent.ACTION_SEND)
                .setType("application/zip")
                .putExtra(Intent.EXTRA_STREAM, uri)
                .putExtra(Intent.EXTRA_SUBJECT, getString(R.string.photo_export_subject))
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        send.setClipData(ClipData.newRawUri(zip.getName(), uri));
        lastExport = zip;
        startActivity(Intent.createChooser(send, getString(R.string.photo_export_title)));
    }

    @Nullable
    private File lastExport;

    @VisibleForTesting
    @Nullable
    File lastExport() {
        return lastExport;
    }

    private void confirmDeleteAll() {
        io.execute(() -> {
            int n = store.total();
            main.post(() -> {
                if (n == 0) {
                    Snackbar.make(findViewById(R.id.photo_tasks), R.string.photo_export_nothing, Snackbar.LENGTH_LONG).show();
                    return;
                }
                new MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.photo_delete_all)
                        .setMessage(getString(R.string.photo_delete_all_message, n))
                        .setPositiveButton(R.string.photo_delete_confirm, (d, w) -> io.execute(() -> {
                            int deleted = store.deleteAll();
                            main.post(() -> {
                                Snackbar.make(findViewById(R.id.photo_tasks),
                                        getString(R.string.photo_deleted, deleted), Snackbar.LENGTH_LONG).show();
                                refresh();
                            });
                        }))
                        .setNegativeButton(R.string.action_cancel, null)
                        .show();
            });
        });
    }

    private static String text(@Nullable TextView field) {
        return field == null || field.getText() == null ? "" : field.getText().toString().trim();
    }

    private static int parse(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    // ------------------------------------------------------------ the list

    /** A task and how many of its photos are on this phone. */
    static final class Row {
        final PhotoTask task;
        final int onPhone;

        Row(PhotoTask task, int onPhone) {
            this.task = task;
            this.onPhone = onPhone;
        }
    }

    interface OnTask {
        void on(PhotoTask task);
    }

    static class TaskAdapter extends ListAdapter<Row, TaskAdapter.Holder> {

        private final OnTask onShoot;
        private final OnTask onEdit;

        TaskAdapter(OnTask onShoot, OnTask onEdit) {
            super(new DiffUtil.ItemCallback<Row>() {
                @Override
                public boolean areItemsTheSame(@NonNull Row a, @NonNull Row b) {
                    return a.task.label.equals(b.task.label);
                }

                @Override
                public boolean areContentsTheSame(@NonNull Row a, @NonNull Row b) {
                    return a.onPhone == b.onPhone && a.task.taken == b.task.taken && a.task.target == b.task.target
                            && Objects.equals(a.task.name, b.task.name) && Objects.equals(a.task.note, b.task.note);
                }
            });
            this.onShoot = onShoot;
            this.onEdit = onEdit;
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_photo_task, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder h, int position) {
            Row row = getItem(position);
            PhotoTask t = row.task;
            h.name.setText(t.name);
            h.note.setText(t.note);
            h.note.setVisibility(t.note == null || t.note.isEmpty() ? View.GONE : View.VISIBLE);
            h.icon.setImageResource(t.isOther() ? R.drawable.ic_park : R.drawable.ic_building);
            h.progress.setMax(Math.max(1, t.target));
            h.progress.setProgress(Math.min(t.target, Math.max(t.taken, row.onPhone)));
            h.count.setText(h.itemView.getContext().getString(R.string.photo_task_count,
                    row.onPhone, t.taken, t.target));
            h.shoot.setContentDescription(h.itemView.getContext().getString(R.string.photo_task_shoot, t.name));
            h.edit.setContentDescription(h.itemView.getContext().getString(R.string.photo_task_edit, t.name));
            h.shoot.setOnClickListener(v -> onShoot.on(t));
            h.itemView.setOnClickListener(v -> onShoot.on(t));
            h.edit.setOnClickListener(v -> onEdit.on(t));
        }

        static class Holder extends RecyclerView.ViewHolder {
            final android.widget.ImageView icon;
            final TextView name;
            final TextView note;
            final TextView count;
            final LinearProgressIndicator progress;
            final MaterialButton shoot;
            final MaterialButton edit;

            Holder(@NonNull View item) {
                super(item);
                icon = item.findViewById(R.id.photo_task_icon);
                name = item.findViewById(R.id.photo_task_name);
                note = item.findViewById(R.id.photo_task_note);
                count = item.findViewById(R.id.photo_task_count);
                progress = item.findViewById(R.id.photo_task_progress);
                shoot = item.findViewById(R.id.photo_task_shoot);
                edit = item.findViewById(R.id.photo_task_edit);
            }
        }
    }
}
