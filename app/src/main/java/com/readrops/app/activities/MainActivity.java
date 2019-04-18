package com.readrops.app.activities;

import android.arch.lifecycle.ViewModelProviders;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.widget.DrawerLayout;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.content.res.AppCompatResources;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.util.Log;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.afollestad.materialdialogs.MaterialDialog;
import com.bumptech.glide.Glide;
import com.bumptech.glide.integration.recyclerview.RecyclerViewPreloader;
import com.bumptech.glide.util.ViewPreloadSizeProvider;
import com.github.clans.fab.FloatingActionMenu;
import com.mikepenz.materialdrawer.Drawer;
import com.mikepenz.materialdrawer.DrawerBuilder;
import com.mikepenz.materialdrawer.model.PrimaryDrawerItem;
import com.mikepenz.materialdrawer.model.SecondaryDrawerItem;
import com.mikepenz.materialdrawer.model.interfaces.IDrawerItem;
import com.readrops.app.R;
import com.readrops.app.database.entities.Feed;
import com.readrops.app.database.entities.Folder;
import com.readrops.app.database.pojo.ItemWithFeed;
import com.readrops.app.utils.DrawerManager;
import com.readrops.app.utils.GlideApp;
import com.readrops.app.utils.SharedPreferencesManager;
import com.readrops.app.utils.Utils;
import com.readrops.app.viewmodels.MainViewModel;
import com.readrops.app.views.MainItemListAdapter;

import org.apache.commons.collections4.CollectionUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import io.reactivex.Observer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.observers.DisposableCompletableObserver;
import io.reactivex.observers.DisposableSingleObserver;
import io.reactivex.schedulers.Schedulers;

public class MainActivity extends AppCompatActivity implements SwipeRefreshLayout.OnRefreshListener {

    public static final String TAG = MainActivity.class.getSimpleName();
    public static final int ADD_FEED_REQUEST = 1;
    public static final int MANAGE_FEEDS_REQUEST = 2;
    public static final int ITEM_REQUEST = 3;

    private RecyclerView recyclerView;
    private MainItemListAdapter adapter;
    private SwipeRefreshLayout refreshLayout;

    private Drawer drawer;
    private FloatingActionMenu actionMenu;

    private List<ItemWithFeed> allItems;
    private List<ItemWithFeed> filteredItems;

    private MainViewModel viewModel;
    private DrawerManager drawerManager;

    private RelativeLayout syncProgressLayout;
    private TextView syncProgress;
    private ProgressBar syncProgressBar;

    private int feedCount;
    private int feedNb;
    private int filterFeedId;
    private boolean readItLater;

    private boolean showReadItems;
    private ListSortType sortType;

    private ActionMode actionMode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = findViewById(R.id.toolbar_main);
        setSupportActionBar(toolbar);
        toolbar.setTitleTextColor(Color.WHITE);

        actionMenu = findViewById(R.id.fab_menu);
        viewModel = ViewModelProviders.of(this).get(MainViewModel.class);

        allItems = new ArrayList<>();

        showReadItems = SharedPreferencesManager.readBoolean(this,
                SharedPreferencesManager.SharedPrefKey.SHOW_READ_ARTICLES);

        viewModel.getItemsWithFeed().observe(this, (itemWithFeeds -> {
            allItems = itemWithFeeds;

            if (!refreshLayout.isRefreshing())
                filterItems(filterFeedId);
        }));

        refreshLayout = findViewById(R.id.swipe_refresh_layout);
        refreshLayout.setOnRefreshListener(this);

        syncProgressLayout = findViewById(R.id.sync_progress_layout);
        syncProgress = findViewById(R.id.sync_progress_text_view);
        syncProgressBar = findViewById(R.id.sync_progress_bar);

        feedCount = 0;
        initRecyclerView();
        sortType = ListSortType.NEWEST_TO_OLDEST;

        drawer = new DrawerBuilder()
                .withActivity(this)
                .withToolbar(toolbar)
                .withShowDrawerOnFirstLaunch(true)
                .withOnDrawerItemClickListener((view, position, drawerItem) -> {
                    handleDrawerClick(drawerItem);
                    return true;
                })
                .build();

        drawerManager = new DrawerManager(drawer);
        updateDrawerFeeds();
    }

    private void handleDrawerClick(IDrawerItem drawerItem) {
        if (drawerItem instanceof PrimaryDrawerItem) {
            drawer.closeDrawer();
            int id = (int)drawerItem.getIdentifier();
            filterFeedId = 0;

            switch (id) {
                case DrawerManager.ARTICLES_ITEM_ID:
                    readItLater = false;
                    filterItems(0);
                    break;
                case DrawerManager.READ_LATER_ID:
                    readItLater = true;
                    filterItems(0);
                    break;

            }
        } else if (drawerItem instanceof SecondaryDrawerItem) {
            readItLater = false;
            drawer.closeDrawer();
            filterItems((int)drawerItem.getIdentifier());
        }
    }

    private void filterItems(int id) {
        filterFeedId = id;
        filteredItems = new ArrayList<>(allItems);

        CollectionUtils.filter(filteredItems, object -> {
            boolean showRead;
            if (object.getItem().isRead())
                showRead = (object.getItem().isRead() == showReadItems);
            else
                showRead = true; // item unread

            if (id != 0) {
                if (readItLater)
                    return object.getItem().isReadItLater() && object.getFeedId() == id && showRead;
                else
                    return !object.getItem().isReadItLater() && object.getFeedId() == id && showRead;
            } else {
                if (readItLater)
                    return object.getItem().isReadItLater() && showRead;
                else
                    return !object.getItem().isReadItLater() && showRead;
            }
        });

        sortItems();
        adapter.submitList(filteredItems);
    }

    private void sortItems() {
        switch (sortType) {
            case OLDEST_TO_NEWEST:
                Collections.sort(filteredItems, ((o1, o2) -> o1.getItem().getPubDate().compareTo(o2.getItem().getPubDate())));
                break;
            case NEWEST_TO_OLDEST:
                Collections.sort(filteredItems, ((o1, o2) -> -1 * o1.getItem().getPubDate().compareTo(o2.getItem().getPubDate())));
                break;
            default:
                Collections.sort(filteredItems, ((o1, o2) -> -1 * o1.getItem().getPubDate().compareTo(o2.getItem().getPubDate())));
                break;
        }
    }

    private void updateDrawerFeeds() {
        viewModel.getFoldersWithFeeds()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new DisposableSingleObserver<Map<Folder, List<Feed>>>() {
                    @Override
                    public void onSuccess(Map<Folder, List<Feed>> folderListHashMap) {
                        drawerManager.updateDrawer(getApplicationContext(), folderListHashMap);
                    }

                    @Override
                    public void onError(Throwable e) {

                    }
                });
    }

    @Override
    public void onBackPressed() {
        if (drawer.isDrawerOpen())
            drawer.closeDrawer();
        else
            super.onBackPressed();
    }

    private void initRecyclerView() {
        recyclerView = findViewById(R.id.items_recycler_view);

        ViewPreloadSizeProvider preloadSizeProvider = new ViewPreloadSizeProvider();
        adapter = new MainItemListAdapter(GlideApp.with(this), preloadSizeProvider);
        adapter.setOnItemClickListener(new MainItemListAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(ItemWithFeed itemWithFeed, int position) {
                if (actionMode == null) {
                    Intent intent = new Intent(getApplicationContext(), ItemActivity.class);

                    intent.putExtra(ItemActivity.ITEM_ID, itemWithFeed.getItem().getId());
                    intent.putExtra(ItemActivity.IMAGE_URL, itemWithFeed.getItem().getImageLink());
                    startActivityForResult(intent, ITEM_REQUEST);

                    viewModel.setItemReadState(itemWithFeed.getItem().getId(), true)
                            .subscribeOn(Schedulers.io())
                            .observeOn(AndroidSchedulers.mainThread())
                            .doOnError(throwable -> Toast.makeText(getApplicationContext(),
                                    "Error when updating in db", Toast.LENGTH_LONG).show())
                            .subscribe();

                    itemWithFeed.getItem().setRead(true);
                    adapter.notifyItemChanged(position, itemWithFeed);
                    updateDrawerFeeds();
                } else {
                    adapter.toggleSelection(position);

                    if (adapter.getSelection().isEmpty())
                        actionMode.finish();
                }

            }

            @Override
            public void onItemLongClick(ItemWithFeed itemWithFeed, int position) {
                if (actionMode != null)
                    return;

                adapter.toggleSelection(position);

                actionMode = startActionMode(new ActionMode.Callback() {
                    @Override
                    public boolean onCreateActionMode(ActionMode actionMode, Menu menu) {
                        drawer.getDrawerLayout().setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
                        refreshLayout.setEnabled(false);
                        actionMode.getMenuInflater().inflate(R.menu.item_list_contextual_menu, menu);

                        return true;
                    }

                    @Override
                    public boolean onPrepareActionMode(ActionMode actionMode, Menu menu) {
                        menu.findItem(R.id.item_mark_read).setVisible(!itemWithFeed.getItem().isRead());
                        menu.findItem(R.id.item_mark_unread).setVisible(itemWithFeed.getItem().isRead());

                        return true;
                    }

                    @Override
                    public boolean onActionItemClicked(ActionMode actionMode, MenuItem menuItem) {
                        switch (menuItem.getItemId()) {
                            case R.id.item_mark_read:
                                viewModel.setItemsReadState(getIdsFromPositions(adapter.getSelection()), true)
                                        .subscribeOn(Schedulers.io())
                                        .observeOn(AndroidSchedulers.mainThread())
                                        .doOnError(throwable -> Toast.makeText(getApplicationContext(),
                                                "Error when updating in db", Toast.LENGTH_LONG).show())
                                        .subscribe();
                                adapter.updateSelection(true);

                                break;
                            case R.id.item_mark_unread:
                                viewModel.setItemsReadState(getIdsFromPositions(adapter.getSelection()), false)
                                        .subscribeOn(Schedulers.io())
                                        .observeOn(AndroidSchedulers.mainThread())
                                        .doOnError(throwable -> Toast.makeText(getApplicationContext(),
                                                "Error when updating in db", Toast.LENGTH_LONG).show())
                                        .subscribe();
                                adapter.updateSelection(false);

                                break;
                        }

                        updateDrawerFeeds();
                        actionMode.finish();
                        return true;
                    }

                    @Override
                    public void onDestroyActionMode(ActionMode mode) {
                        mode.finish();
                        actionMode = null;

                        drawer.getDrawerLayout().setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);
                        refreshLayout.setEnabled(true);

                        adapter.clearSelection();
                    }
                });
            }
        });

        RecyclerViewPreloader<String> preloader = new RecyclerViewPreloader<String>(Glide.with(this), adapter, preloadSizeProvider, 10);
        recyclerView.addOnScrollListener(preloader);

        recyclerView.setRecyclerListener(viewHolder -> {
            MainItemListAdapter.ItemViewHolder vh = (MainItemListAdapter.ItemViewHolder) viewHolder;
            GlideApp.with(this).clear(vh.getItemImage());
        });

        RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(layoutManager);

        DividerItemDecoration decoration = new DividerItemDecoration(this, ((LinearLayoutManager) layoutManager).getOrientation());
        recyclerView.addItemDecoration(decoration);

        recyclerView.setAdapter(adapter);

        new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
            @Override
            public int getMovementFlags(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                int swipeFlags = ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT;

                return makeMovementFlags(0, swipeFlags);
            }

            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder viewHolder1) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int i) {
                if (i == ItemTouchHelper.LEFT) { // set item read state
                    ItemWithFeed itemWithFeed = adapter.getItemWithFeed(viewHolder.getAdapterPosition());

                    viewModel.setItemReadState(itemWithFeed.getItem().getId(), !itemWithFeed.getItem().isRead())
                            .subscribeOn(Schedulers.io())
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe();

                    itemWithFeed.getItem().setRead(!itemWithFeed.getItem().isRead());

                    adapter.notifyItemChanged(viewHolder.getAdapterPosition());
                } else { // add item to read it later section
                    viewModel.setItemReadItLater((int) adapter.getItemId(viewHolder.getAdapterPosition()))
                            .subscribeOn(Schedulers.io())
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe();

                    if (readItLater)
                        adapter.notifyItemChanged(viewHolder.getAdapterPosition());
                }
            }

            @Override
            public boolean isItemViewSwipeEnabled() {
                return true;
            }
        }).attachToRecyclerView(recyclerView);

        adapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override
            public void onItemRangeInserted(int positionStart, int itemCount) {
                recyclerView.scrollToPosition(0);
            }
        });
    }

    @Override
    public void onRefresh() {
        Log.d(TAG, "syncing started");

        viewModel.getFeedCount()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new DisposableSingleObserver<Integer>() {
                    @Override
                    public void onSuccess(Integer integer) {
                        feedNb = integer;
                        sync(null);
                    }

                    @Override
                    public void onError(Throwable e) {
                        Toast.makeText(getApplicationContext(), "error on getting feeds number", Toast.LENGTH_LONG).show();
                    }
                });
    }

    public void openAddFeedActivity(View view) {
        actionMenu.close(true);

        Intent intent = new Intent(this, AddFeedActivity.class);
        startActivityForResult(intent, ADD_FEED_REQUEST);
    }

    public void addFolder(View view) {
        actionMenu.close(true);

        Intent intent = new Intent(this, ManageFeedsActivity.class);
        startActivityForResult(intent, MANAGE_FEEDS_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (requestCode == ADD_FEED_REQUEST && resultCode ==  RESULT_OK) {
            ArrayList<Feed> feeds = data.getParcelableArrayListExtra("feedIds");

            if (feeds != null && feeds.size() > 0) {
                refreshLayout.setRefreshing(true);
                feedNb = feeds.size();
                sync(feeds);
            }
        } else if (requestCode == MANAGE_FEEDS_REQUEST) {
            updateDrawerFeeds();
        }

        super.onActivityResult(requestCode, resultCode, data);
    }

    private void sync(List<Feed> feeds) {
        viewModel.sync(feeds)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<Feed>() {
                    @Override
                    public void onSubscribe(Disposable d) {
                        syncProgressLayout.setVisibility(View.VISIBLE);
                        syncProgressBar.setProgress(0);
                    }

                    @Override
                    public void onNext(Feed feed) {
                        syncProgress.setText(getString(R.string.updating_feed, feed.getName()));
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            syncProgressBar.setProgress((feedCount * 100) / feedNb, true);
                        } else
                            syncProgressBar.setProgress((feedCount * 100) / feedNb);

                        feedCount++;
                    }

                    @Override
                    public void onError(Throwable e) {
                        refreshLayout.setRefreshing(false);
                        Toast.makeText(getApplication(), e.getMessage(), Toast.LENGTH_LONG).show();
                    }

                    @Override
                    public void onComplete() {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                            syncProgressBar.setProgress(100, true);
                        else
                            syncProgressBar.setProgress(100);

                        syncProgressLayout.setVisibility(View.GONE);
                        refreshLayout.setRefreshing(false);

                        adapter.submitList(allItems);
                        filterItems(filterFeedId);
                        updateDrawerFeeds(); // update drawer after syncing feeds
                    }
                });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.item_list_menu, menu);

        MenuItem articlesItem = menu.findItem(R.id.item_filter_read_items);
        articlesItem.setChecked(showReadItems);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.item_filter_read_items:
                if (item.isChecked()) {
                    item.setChecked(false);
                    showReadItems = false;
                    SharedPreferencesManager.writeValue(this,
                            SharedPreferencesManager.SharedPrefKey.SHOW_READ_ARTICLES, false);
                } else {
                    item.setChecked(true);
                    showReadItems = true;
                    SharedPreferencesManager.writeValue(this,
                            SharedPreferencesManager.SharedPrefKey.SHOW_READ_ARTICLES, true);
                }

                filterItems(filterFeedId);
                return true;
            case R.id.item_sort:
                displayFilterDialog();
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void displayFilterDialog() {
        int index = sortType == ListSortType.OLDEST_TO_NEWEST ? 1 : 0;

        new MaterialDialog.Builder(this)
                .title(getString(R.string.filter))
                .items(R.array.filter_items)
                .itemsCallbackSingleChoice(index, (dialog, itemView, which, text) -> {
                    String[] items = getResources().getStringArray(R.array.filter_items);

                    if (text.toString().equals(items[0]))
                        sortType = ListSortType.NEWEST_TO_OLDEST;
                    else
                        sortType = ListSortType.OLDEST_TO_NEWEST;

                    sortItems();
                    adapter.submitList(filteredItems);
                    adapter.notifyDataSetChanged();

                    return true;
                })
                .show();
    }

    private List<Integer> getIdsFromPositions(LinkedHashSet<Integer> positions) {
        List<Integer> ids = new ArrayList<>();

        for (int position : positions) {
            ids.add((int)adapter.getItemId(position));
        }

        return ids;
    }

    public enum ListSortType {
        NEWEST_TO_OLDEST,
        OLDEST_TO_NEWEST
    }
}
