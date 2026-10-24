package il.org.ay.navigation.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import il.org.ay.navigation.R;
import il.org.ay.navigation.logic.RoomSearch;
import il.org.ay.navigation.model.CampusRoom;

/**
 * The search results (T09, rubric 6.9: RecyclerView). One row per room, layout item_room.xml.
 * DiffUtil works out which rows changed, so the list updates smoothly while typing.
 */
public class RoomAdapter extends ListAdapter<CampusRoom, RoomAdapter.RoomHolder> {

    public interface OnRoomClick {
        void onRoomClick(CampusRoom room);
    }

    private final OnRoomClick onClick;
    private final OnRoomClick onStarClick;
    private String query = "";
    private Set<String> favorites = Collections.emptySet();

    public RoomAdapter(@NonNull OnRoomClick onClick, @NonNull OnRoomClick onStarClick) {
        super(DIFF);
        this.onClick = onClick;
        this.onStarClick = onStarClick;
    }

    /** Which rooms have a filled star (T10). Only the rows whose star changed are redrawn. */
    public void setFavorites(@NonNull Set<String> numbers) {
        Set<String> old = favorites;
        favorites = new HashSet<>(numbers);
        for (int i = 0; i < getItemCount(); i++) {
            String n = getItem(i).number;
            if (old.contains(n) != favorites.contains(n)) {
                notifyItemChanged(i);
            }
        }
    }

    /** New results for {@code query}; the room whose number is exactly the query is highlighted. */
    public void show(@NonNull List<CampusRoom> rooms, @NonNull String query, Runnable done) {
        boolean queryChanged = !query.equals(this.query);
        this.query = query;
        submitList(rooms, () -> {
            if (queryChanged) {
                // the highlight depends on the query, not only on the room
                notifyItemRangeChanged(0, getItemCount());
            }
            if (done != null) {
                done.run();
            }
        });
    }

    @NonNull
    @Override
    public RoomHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View item = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_room, parent, false);
        return new RoomHolder(item);
    }

    @Override
    public void onBindViewHolder(@NonNull RoomHolder holder, int position) {
        CampusRoom room = getItem(position);
        holder.bind(room, query, favorites.contains(room.number), onClick, onStarClick);
    }

    /** One row. Holds its views so findViewById runs once per row, not on every scroll. */
    static class RoomHolder extends RecyclerView.ViewHolder {
        final TextView number;
        final TextView name;
        final TextView owner;
        final MaterialButton star;

        RoomHolder(@NonNull View item) {
            super(item);
            number = item.findViewById(R.id.room_number);
            name = item.findViewById(R.id.room_name);
            owner = item.findViewById(R.id.room_owner);
            star = item.findViewById(R.id.room_favorite);
            // distance and walking time come with the route, in T11
            item.findViewById(R.id.room_distance).setVisibility(View.GONE);
        }

        void bind(CampusRoom room, String query, boolean favorite, OnRoomClick onClick, OnRoomClick onStarClick) {
            number.setText(room.number);
            name.setText(displayName(itemView, room));
            owner.setText(room.owner);
            owner.setVisibility(room.owner == null || room.owner.isEmpty() ? View.GONE : View.VISIBLE);

            boolean exact = RoomSearch.isExactNumber(room, query);
            number.setBackgroundResource(exact ? R.drawable.bg_circle_primary : R.drawable.bg_circle_primary_container);
            number.setTextColor(ContextCompat.getColor(itemView.getContext(), exact ? R.color.white : R.color.primary));

            itemView.setContentDescription(itemView.getContext().getString(
                    R.string.room_badge_description, room.number) + ", " + name.getText());
            itemView.setOnClickListener(v -> onClick.onRoomClick(room));

            star.setIconResource(favorite ? R.drawable.ic_star_filled : R.drawable.ic_star);
            star.setIconTintResource(favorite ? R.color.primary : R.color.text_secondary);
            star.setContentDescription(itemView.getContext().getString(
                    favorite ? R.string.action_unfavorite : R.string.action_favorite));
            star.setOnClickListener(v -> onStarClick.onRoomClick(room));
        }
    }

    /** The room's name, or "חדר 42" when it has none. */
    static CharSequence displayName(View view, CampusRoom room) {
        if (room.name != null && !room.name.isEmpty()) {
            return room.name;
        }
        return view.getContext().getString(R.string.room_badge_description, room.number);
    }

    static final DiffUtil.ItemCallback<CampusRoom> DIFF = new DiffUtil.ItemCallback<CampusRoom>() {
        @Override
        public boolean areItemsTheSame(@NonNull CampusRoom a, @NonNull CampusRoom b) {
            return a.number.equals(b.number);
        }

        @Override
        public boolean areContentsTheSame(@NonNull CampusRoom a, @NonNull CampusRoom b) {
            return Objects.equals(a.name, b.name) && Objects.equals(a.owner, b.owner);
        }
    };
}
