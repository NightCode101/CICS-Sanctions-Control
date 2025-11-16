package cics.csup.sanctionsscontrol;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.firestore.DocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

public class UserAdapter extends RecyclerView.Adapter<UserAdapter.UserViewHolder> {

    private OnUserInteractionListener listener;
    private TextView emptyView;
    private List<User> userList = new ArrayList<>();
    private List<DocumentSnapshot> snapshotList = new ArrayList<>();

    private Context context;

    public UserAdapter(OnUserInteractionListener listener, TextView emptyView) {
        this.listener = listener;
        this.emptyView = emptyView;
    }

    @Override
    public int getItemCount() {
        return userList.size();
    }

    @NonNull
    @Override
    public UserViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        this.context = parent.getContext();
        View view = LayoutInflater.from(context)
                .inflate(R.layout.list_item_user, parent, false);
        return new UserViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull UserViewHolder holder, int position) {
        User model = userList.get(position);

        // --- THIS IS THE FIX ---
        // All text now uses getString with placeholders
        holder.name.setText(context.getString(R.string.list_item_name_format, model.getName()));
        holder.totalSanctions.setText(context.getString(R.string.list_item_total_sanctions_format, model.getTotalSanctions()));
        holder.passedBottles.setText(context.getString(R.string.list_item_passed_bottles_format, model.getPassedBottles()));
        holder.toBePass.setText(context.getString(R.string.list_item_to_be_pass_format, model.getToBePass()));
        // --- END OF FIX ---

        holder.buttonEdit.setOnClickListener(v -> {
            DocumentSnapshot snapshot = snapshotList.get(holder.getAdapterPosition());
            listener.onEditClick(snapshot);
        });

        holder.itemView.setOnLongClickListener(v -> {
            DocumentSnapshot snapshot = snapshotList.get(holder.getAdapterPosition());
            listener.onUserLongPress(snapshot);
            return true;
        });
    }

    public void setData(List<User> newUsers, List<DocumentSnapshot> newSnapshots) {
        this.userList.clear();
        this.userList.addAll(newUsers);
        this.snapshotList.clear();
        this.snapshotList.addAll(newSnapshots);

        if (userList.isEmpty()) {
            emptyView.setVisibility(View.VISIBLE);
        } else {
            emptyView.setVisibility(View.GONE);
        }

        notifyDataSetChanged();
    }

    class UserViewHolder extends RecyclerView.ViewHolder {
        TextView name, totalSanctions, passedBottles, toBePass;
        Button buttonEdit;

        public UserViewHolder(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.textViewName);
            totalSanctions = itemView.findViewById(R.id.textViewTotalSanctions);
            passedBottles = itemView.findViewById(R.id.textViewPassedBottles);
            toBePass = itemView.findViewById(R.id.textViewToBePass);
            buttonEdit = itemView.findViewById(R.id.buttonEdit);
        }
    }

    public interface OnUserInteractionListener {
        void onEditClick(DocumentSnapshot documentSnapshot);
        void onUserLongPress(DocumentSnapshot documentSnapshot);
    }
}