package com.notificationpocket;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public final class NotificationAdapter extends BaseAdapter {
    public interface ActionListener {
        void onAction(NotificationEntry entry);
    }

    private final LayoutInflater inflater;
    private final ActionListener actionListener;
    private final ActionListener durationListener;
    private final List<NotificationEntry> items = new ArrayList<>();

    public NotificationAdapter(
            Context context, ActionListener actionListener, ActionListener durationListener) {
        this.inflater = LayoutInflater.from(context);
        this.actionListener = actionListener;
        this.durationListener = durationListener;
    }

    public void setItems(List<NotificationEntry> newItems) {
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return items.size();
    }

    @Override
    public NotificationEntry getItem(int position) {
        return items.get(position);
    }

    @Override
    public long getItemId(int position) {
        return items.get(position).key.hashCode();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.item_notification, parent, false);
            holder = new ViewHolder(convertView);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        NotificationEntry entry = getItem(position);
        holder.icon.setImageDrawable(entry.icon == null
                ? inflater.getContext().getDrawable(R.drawable.ic_notification)
                : entry.icon);
        holder.app.setText(entry.appLabel);
        holder.title.setText(entry.title.isEmpty() ? entry.appLabel : entry.title);
        holder.text.setText(entry.text);
        holder.text.setVisibility(entry.text.isEmpty() ? View.GONE : View.VISIBLE);
        holder.meta.setText(entry.meta);
        holder.action.setText(entry.snoozed ? R.string.restore : R.string.hide);
        holder.action.setContentDescription(inflater.getContext().getString(
                entry.snoozed ? R.string.restore_description : R.string.hide_description,
                holder.title.getText()));
        holder.action.setOnClickListener(view -> actionListener.onAction(entry));
        holder.action.setOnLongClickListener(entry.snoozed ? null : view -> {
            durationListener.onAction(entry);
            return true;
        });
        return convertView;
    }

    private static final class ViewHolder {
        final ImageView icon;
        final TextView app;
        final TextView title;
        final TextView text;
        final TextView meta;
        final Button action;

        ViewHolder(View root) {
            icon = root.findViewById(R.id.notification_icon);
            app = root.findViewById(R.id.notification_app);
            title = root.findViewById(R.id.notification_title);
            text = root.findViewById(R.id.notification_text);
            meta = root.findViewById(R.id.notification_meta);
            action = root.findViewById(R.id.notification_action);
        }
    }
}
