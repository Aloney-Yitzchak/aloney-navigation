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

import java.util.List;
import java.util.Objects;

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
    private String query = "";

    public RoomAdapter(@NonNull OnRoomClick onClick) {
        super(DIFF);
        this.onClick = onClick;
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
        holder.bind(getItem(position), query, onClick);
    }

    /** One row. Holds its views so findViewById runs once per row, not on every scroll. */
    static class RoomHolder extends RecyclerView.ViewHolder {
        final TextView number;
        final TextView name;
        final TextView owner;

        RoomHolder(@NonNull View item) {
            super(item);
            number = item.findViewById(R.id.room_number);
            name = item.findViewById(R.id.room_name);
            owner = item.findViewById(R.id.room_owner);
            // favourites come in T10, distance in T11
            item.findViewById(R.id.room_favorite).setVisibility(View.GONE);
            item.findViewById(R.id.room_distance).setVisibility(View.GONE);
        }

        void bind(CampusRoom room, String query, OnRoomClick onClick) {
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
