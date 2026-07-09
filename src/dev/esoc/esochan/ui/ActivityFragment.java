/*
 * esochan (Meta Imageboard Client)
 * Copyright (C) 2014-2016  miku-nyan <https://github.com/miku-nyan>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package dev.esoc.esochan.ui;

import java.util.List;

import dev.esoc.esochan.R;
import dev.esoc.esochan.api.ChanModule;
import dev.esoc.esochan.api.models.UrlPageModel;
import dev.esoc.esochan.common.Async;
import dev.esoc.esochan.common.MainApplication;
import dev.esoc.esochan.ui.presentation.Subscriptions;
import dev.esoc.esochan.ui.presentation.Subscriptions.SubscriptionEntry;
import dev.esoc.esochan.ui.tabs.UrlHandler;
import dev.esoc.esochan.ui.theme.ThemeUtils;
import android.content.res.Resources;
import android.os.Bundle;
import androidx.fragment.app.Fragment;
import android.text.TextUtils;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

public class ActivityFragment extends Fragment implements AdapterView.OnItemClickListener {
    private MainActivity activity;
    private ActivityAdapter adapter;
    private ListView listView;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        activity = (MainActivity) getActivity();
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        activity.setTitle(R.string.tabs_activity);
        listView = (ListView) inflater.inflate(R.layout.history_fragment, container, false);
        listView.setOnItemClickListener(this);
        registerForContextMenu(listView);
        init();
        return listView;
    }

    @Override
    public void onResume() {
        super.onResume();
        init();
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        if (adapter == null) return;
        Object item = adapter.getItem(position);
        if (!(item instanceof SubscriptionEntry)) return;
        SubscriptionEntry entry = (SubscriptionEntry) item;
        UrlPageModel model = new UrlPageModel();
        model.chanName = entry.chan;
        model.type = UrlPageModel.TYPE_THREADPAGE;
        model.boardName = entry.board;
        model.threadNumber = entry.thread;
        if (entry.post != null) model.postNumber = entry.post;
        UrlHandler.open(model, activity);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        menu.add(Menu.NONE, R.id.menu_clear_activity, 101, R.string.menu_clear_activity).setIcon(
                ThemeUtils.getTintedIcon(activity.getTheme(), activity.getResources(), R.drawable.ic_menu_clear, R.attr.iconTint));
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.menu_clear_activity) {
            MainApplication.getInstance().subscriptions.reset();
            init();
            return true;
        }
        return false;
    }

    private void init() {
        Async.runAsync(new Runnable() {
            @Override
            public void run() {
                Subscriptions subs = MainApplication.getInstance().subscriptions;
                final List<SubscriptionEntry> threads = subs.listThreads();
                final List<SubscriptionEntry> posts = subs.listAll();
                Async.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (!isAdded()) return;
                        adapter = new ActivityAdapter(activity, threads, posts);
                        listView.setAdapter(adapter);
                    }
                });
            }
        });
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        if (adapter == null) return;
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;
        Object item = adapter.getItem(info.position);
        if (!(item instanceof SubscriptionEntry)) return;
        SubscriptionEntry entry = (SubscriptionEntry) item;
        if (entry.post == null) {
            menu.add(Menu.NONE, R.id.context_menu_remove_thread_subscriptions, 1,
                    R.string.context_menu_remove_thread_subscriptions);
        } else {
            menu.add(Menu.NONE, R.id.context_menu_remove_subscription, 1, R.string.context_menu_remove_subscription);
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        if (adapter == null) return false;
        int position = ((AdapterView.AdapterContextMenuInfo) item.getMenuInfo()).position;
        Object listItem = adapter.getItem(position);
        if (!(listItem instanceof SubscriptionEntry)) return false;
        SubscriptionEntry entry = (SubscriptionEntry) listItem;
        Subscriptions subs = MainApplication.getInstance().subscriptions;
        switch (item.getItemId()) {
            case R.id.context_menu_remove_subscription:
                if (entry.post != null) {
                    subs.removeSubscription(entry.chan, entry.board, entry.thread, entry.post);
                }
                init();
                return true;
            case R.id.context_menu_remove_thread_subscriptions:
                subs.removeThread(entry.chan, entry.board, entry.thread);
                init();
                return true;
            default:
                return false;
        }
    }

    private static class ActivityAdapter extends ArrayAdapter<Object> {
        private static final int SEPARATOR = 0;
        private static final int NORMAL_ITEM = 1;

        private final LayoutInflater inflater;
        private final int drawablePadding;
        private final Resources resources;

        public ActivityAdapter(MainActivity activity, List<SubscriptionEntry> threads, List<SubscriptionEntry> posts) {
            super(activity, 0);
            resources = activity.getResources();
            inflater = LayoutInflater.from(activity);
            drawablePadding = (int) (resources.getDisplayMetrics().density * 5 + 0.5f);
            if (threads.isEmpty() && posts.isEmpty()) {
                add(resources.getString(R.string.activity_empty));
                return;
            }
            if (!threads.isEmpty()) {
                add(resources.getString(R.string.activity_section_threads));
                for (SubscriptionEntry e : threads) add(e);
            }
            if (!posts.isEmpty()) {
                add(resources.getString(R.string.activity_section_posts));
                for (SubscriptionEntry e : posts) add(e);
            }
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            Object item = getItem(position);
            if (item instanceof SubscriptionEntry) {
                View v = convertView == null ? inflater.inflate(android.R.layout.simple_list_item_2, parent, false) : convertView;
                TextView tv1 = (TextView) v.findViewById(android.R.id.text1);
                TextView tv2 = (TextView) v.findViewById(android.R.id.text2);
                tv1.setSingleLine();
                tv2.setSingleLine();
                tv1.setEllipsize(TextUtils.TruncateAt.END);
                tv2.setEllipsize(TextUtils.TruncateAt.MIDDLE);
                SubscriptionEntry entry = (SubscriptionEntry) item;
                if (entry.post == null) {
                    tv1.setText(resources.getString(R.string.activity_thread_title, entry.board, entry.thread));
                } else {
                    tv1.setText(resources.getString(R.string.activity_post_title, entry.board, entry.thread, entry.post));
                }
                String url = buildUrl(entry);
                tv2.setText(url != null ? url : entry.chan);
                ChanModule chan = MainApplication.getInstance().getChanModule(entry.chan);
                if (chan != null) {
                    tv1.setCompoundDrawablesWithIntrinsicBounds(chan.getChanFavicon(), null, null, null);
                    tv1.setCompoundDrawablePadding(drawablePadding);
                } else {
                    tv1.setCompoundDrawablesWithIntrinsicBounds(null, null, null, null);
                }
                return v;
            }
            View v = convertView == null ? inflater.inflate(R.layout.list_separator, parent, false) : convertView;
            ((TextView) v).setText((String) item);
            return v;
        }

        private static String buildUrl(SubscriptionEntry entry) {
            try {
                ChanModule chan = MainApplication.getInstance().getChanModule(entry.chan);
                if (chan == null) return null;
                UrlPageModel model = new UrlPageModel();
                model.chanName = entry.chan;
                model.type = UrlPageModel.TYPE_THREADPAGE;
                model.boardName = entry.board;
                model.threadNumber = entry.thread;
                if (entry.post != null) model.postNumber = entry.post;
                return chan.buildUrl(model);
            } catch (Exception e) {
                return null;
            }
        }

        @Override
        public int getViewTypeCount() {
            return 2;
        }

        @Override
        public int getItemViewType(int position) {
            return getItem(position) instanceof SubscriptionEntry ? NORMAL_ITEM : SEPARATOR;
        }

        @Override
        public boolean isEnabled(int position) {
            return getItem(position) instanceof SubscriptionEntry;
        }
    }
}
