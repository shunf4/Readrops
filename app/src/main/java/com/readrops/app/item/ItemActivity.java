package com.readrops.app.item;

import android.Manifest;
import android.app.DownloadManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import com.readrops.db.logwrapper.Log;
import android.util.TypedValue;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.WebView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.browser.customtabs.CustomTabsIntent;
import androidx.core.app.ActivityCompat;
import androidx.core.app.ShareCompat;
import androidx.paging.PagedList;

import com.afollestad.materialdialogs.MaterialDialog;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.readrops.api.utils.DateUtils;
import com.readrops.app.R;
import com.readrops.app.databinding.ActivityItemBinding;
import com.readrops.app.itemslist.MainViewModel;
import com.readrops.app.utils.GlideRequests;
import com.readrops.app.utils.PermissionManager;
import com.readrops.app.utils.SharedPreferencesManager;
import com.readrops.app.utils.Utils;
import com.readrops.db.entities.Item;
import com.readrops.db.filters.FilterType;
import com.readrops.db.filters.ListSortType;
import com.readrops.db.pojo.ItemWithFeed;

import org.koin.androidx.viewmodel.compat.ViewModelCompat;
import org.koin.java.KoinJavaComponent;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.observers.DisposableSingleObserver;
import io.reactivex.schedulers.Schedulers;

import static com.readrops.app.utils.ReadropsKeys.ACCOUNT_ID;
import static com.readrops.app.utils.ReadropsKeys.ACTION_BAR_COLOR;
import static com.readrops.app.utils.ReadropsKeys.FILTER_FEED_ID;
import static com.readrops.app.utils.ReadropsKeys.FILTER_FOLDER_ID;
import static com.readrops.app.utils.ReadropsKeys.FILTER_TYPE;
import static com.readrops.app.utils.ReadropsKeys.IMAGE_URL;
import static com.readrops.app.utils.ReadropsKeys.INDEX_IN_LIST;
import static com.readrops.app.utils.ReadropsKeys.ITEM_ID;
import static com.readrops.app.utils.ReadropsKeys.SHOW_READ_ITEMS;
import static com.readrops.app.utils.ReadropsKeys.SORT_TYPE;
import static com.readrops.app.utils.ReadropsKeys.WEB_URL;

public class ItemActivity extends AppCompatActivity {

    private static final String TAG = ItemActivity.class.getSimpleName();
    private static final int WRITE_EXTERNAL_STORAGE_REQUEST = 1;

    private ActivityItemBinding binding;
    private ItemViewModel viewModel;

    private ItemWithFeed itemWithFeed;

    private boolean appBarCollapsed;

    private String urlToDownload;
    private String imageTitle;

    private int itemId;
    private String imageUrl;
    private int indexInList = -1;
    private PagedList<ItemWithFeed> allItems;
    private MainViewModel mainViewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityItemBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.collapsingLayoutToolbar);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        registerForContextMenu(binding.itemWebview);

        final TypedArray styledAttributes = getTheme().obtainStyledAttributes(
                new int[]{android.R.attr.actionBarSize});
        int actionBarSize = (int) styledAttributes.getDimension(0, 0);
        styledAttributes.recycle();

        binding.appBarLayout.addOnOffsetChangedListener(((appBarLayout1, i) -> {
            appBarCollapsed = Math.abs(i) >= (binding.appBarLayout.getTotalScrollRange() -
                    actionBarSize - ((8 * binding.appBarLayout.getTotalScrollRange()) / 100));

            invalidateOptionsMenu();
        }));

        viewModel = ViewModelCompat.getViewModel(this, ItemViewModel.class);

        binding.activityItemFab.setOnClickListener(v -> openInNavigator());

        binding.itemStarFab.setOnClickListener(v -> {
            Item item = itemWithFeed.getItem();

            if (item.isStarred()) {
                binding.itemStarFab.setImageResource(R.drawable.ic_empty_star);
            } else {
                binding.itemStarFab.setImageResource(R.drawable.ic_star);
            }

            item.setStarred(!item.isStarred());
            item.setStarredChanged(!item.isStarredChanged());

            viewModel.setStarState(item.getId(), item.isStarred(), item.isStarredChanged())
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .doOnError(throwable -> Utils.showSnackbar(binding.itemRoot, throwable.getMessage()))
                    .subscribe();
        });

        commonLoadReloadUI();
    }

    private void commonLoadReloadUI() {
        Intent intent = getIntent();

        itemId = intent.getIntExtra(ITEM_ID, 0);
        imageUrl = intent.getStringExtra(IMAGE_URL);
        indexInList = intent.getIntExtra(INDEX_IN_LIST, -1);
        Log.d(TAG, "index in list: " + indexInList);

        binding.switchItemButtons.setVisibility(View.GONE);

        if (intent.hasExtra(INDEX_IN_LIST)) {
            if (allItems == null) {
                mainViewModel = ViewModelCompat.getViewModel(this, MainViewModel.class);
                mainViewModel.enablePlaceholders = true;

                mainViewModel.setShowReadItems(intent.getBooleanExtra(SHOW_READ_ITEMS, false));
                mainViewModel.setFilterType((FilterType) intent.getSerializableExtra(FILTER_TYPE));
                mainViewModel.setSortType((ListSortType) intent.getSerializableExtra(SORT_TYPE));
                mainViewModel.setFilterFeedId(intent.getIntExtra(FILTER_FEED_ID, 0));
                mainViewModel.setFilterFolderId(intent.getIntExtra(FILTER_FOLDER_ID, 0));

                mainViewModel.getAllAccounts().observe(this, accounts -> {
                    mainViewModel.setAccountsPure(accounts);
                    mainViewModel.setCurrentAccountPure(intent.getIntExtra(ACCOUNT_ID, 0));
                    mainViewModel.invalidate();
                });

                mainViewModel.getItemsWithFeed().observe(this, itemWithFeeds -> {
                    allItems = itemWithFeeds;
                    allItems.loadAround(indexInList);
                    addButtonListenerOrHide();
                });
            } else {
                allItems.loadAround(indexInList);
                addButtonListenerOrHide();
            }
        }

        binding.mainScroll.scrollTo(0, 0);
        binding.appBarLayout.setExpanded(true);

        if (imageUrl == null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
            binding.collapsingLayout.setTitleEnabled(false);
            binding.collapsingLayoutScrim.setVisibility(View.GONE);
        } else {
            getSupportActionBar().setDisplayShowTitleEnabled(true);
            binding.collapsingLayoutScrim.setVisibility(View.VISIBLE);
            binding.collapsingLayout.setTitleEnabled(true);

            KoinJavaComponent.get(GlideRequests.class)
                    .load(imageUrl)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .into(binding.collapsingLayoutImage);
        }

        viewModel.getItemById(itemId)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new DisposableSingleObserver<ItemWithFeed>() {
                    @Override
                    public void onSuccess(ItemWithFeed itemWithFeed) {
                        bindUI(itemWithFeed);
                    }

                    @Override
                    public void onError(Throwable e) {
                        Log.e(TAG, e.getMessage());
                        Utils.showSnackbar(binding.itemRoot, e.getMessage());
                    }
                });
    }

    private void bindUI(ItemWithFeed itemWithFeed) {
        this.itemWithFeed = itemWithFeed;
        Item item = itemWithFeed.getItem();

        if (item.isStarred()) {
            binding.itemStarFab.setImageResource(R.drawable.ic_star);
        } else {
            binding.itemStarFab.setImageResource(R.drawable.ic_empty_star);
        }

        binding.activityItemDate.setText(DateUtils.formattedDateTimeByLocal(item.getPubDate()));

        if (item.getImageLink() == null)
            binding.collapsingLayoutToolbar.setTitle(itemWithFeed.getFeedName());
        else
            binding.collapsingLayout.setTitle(itemWithFeed.getFeedName());

        if (itemWithFeed.getFolder() != null) {
            binding.collapsingLayoutToolbar.setSubtitle(itemWithFeed.getFolder().getName());
        } else {
            binding.collapsingLayoutToolbar.setSubtitle("");
        }

        binding.activityItemTitle.setText(item.getTitle());

        if (itemWithFeed.getBgColor() != 0) {
            binding.activityItemTitle.setTextColor(itemWithFeed.getBgColor());
            binding.prevItemButton.setBackgroundTintList(ColorStateList.valueOf(itemWithFeed.getBgColor()));
            binding.nextItemButton.setBackgroundTintList(ColorStateList.valueOf(itemWithFeed.getBgColor()));
            binding.openUrlButton.setBackgroundTintList(ColorStateList.valueOf(itemWithFeed.getBgColor()));
            Utils.setDrawableColor(binding.activityItemDateLayout.getBackground(), itemWithFeed.getBgColor());
        } else if (itemWithFeed.getColor() != 0) {
            binding.activityItemTitle.setTextColor(itemWithFeed.getColor());
            binding.prevItemButton.setBackgroundTintList(ColorStateList.valueOf(itemWithFeed.getColor()));
            binding.nextItemButton.setBackgroundTintList(ColorStateList.valueOf(itemWithFeed.getColor()));
            binding.openUrlButton.setBackgroundTintList(ColorStateList.valueOf(itemWithFeed.getColor()));
            Utils.setDrawableColor(binding.activityItemDateLayout.getBackground(), itemWithFeed.getColor());
        } else {
            TypedValue typedValue = new TypedValue();
            TypedArray ca = obtainStyledAttributes(typedValue.data, new int[] { R.attr.colorAccent });
            int primaryColor = ca.getColor(0, 0);
            ca.recycle();

            binding.activityItemTitle.setTextColor(primaryColor);
            binding.prevItemButton.setBackgroundTintList(ColorStateList.valueOf(primaryColor));
            binding.nextItemButton.setBackgroundTintList(ColorStateList.valueOf(primaryColor));
            binding.openUrlButton.setBackgroundTintList(ColorStateList.valueOf(primaryColor));
            Utils.setDrawableColor(binding.activityItemDateLayout.getBackground(), primaryColor);
        }

        if (item.getAuthor() != null && !item.getAuthor().isEmpty()) {
            binding.activityItemAuthor.setText(getString(R.string.by_author, item.getAuthor()));
            binding.activityItemAuthor.setVisibility(View.VISIBLE);
        } else {
            binding.activityItemAuthor.setVisibility(View.GONE);
        }

        if (item.getReadTime() > 0) {
            int minutes = (int) Math.round(item.getReadTime());
            if (minutes < 1)
                binding.activityItemReadtime.setText(getResources().getString(R.string.read_time_lower_than_1));
            else if (minutes > 1)
                binding.activityItemReadtime.setText(getResources().getString(R.string.read_time, String.valueOf(minutes)));
            else
                binding.activityItemReadtime.setText(getResources().getString(R.string.read_time_one_minute));

            binding.activityItemReadtimeLayout.setVisibility(View.VISIBLE);
        } else {
            binding.activityItemReadtimeLayout.setVisibility(View.GONE);
        }

        if (itemWithFeed.getBgColor() != 0) {
            binding.collapsingLayout.setBackgroundColor(itemWithFeed.getBgColor());
            binding.collapsingLayout.setContentScrimColor(itemWithFeed.getBgColor());
            binding.collapsingLayout.setStatusBarScrimColor(itemWithFeed.getBgColor());

            getWindow().setStatusBarColor(itemWithFeed.getBgColor());
            binding.activityItemFab.setBackgroundTintList(ColorStateList.valueOf(itemWithFeed.getBgColor()));
            binding.itemStarFab.setBackgroundTintList(ColorStateList.valueOf(itemWithFeed.getBgColor()));
        } else if (itemWithFeed.getColor() != 0) {
            binding.collapsingLayout.setBackgroundColor(itemWithFeed.getColor());
            binding.collapsingLayout.setContentScrimColor(itemWithFeed.getColor());
            binding.collapsingLayout.setStatusBarScrimColor(itemWithFeed.getColor());

            getWindow().setStatusBarColor(itemWithFeed.getColor());
            binding.activityItemFab.setBackgroundTintList(ColorStateList.valueOf(itemWithFeed.getColor()));
            binding.itemStarFab.setBackgroundTintList(ColorStateList.valueOf(itemWithFeed.getColor()));
        } else {
            TypedValue typedValue = new TypedValue();
            TypedArray ca = obtainStyledAttributes(typedValue.data, new int[] { R.attr.colorAccent });
            int primaryColor = ca.getColor(0, 0);
            ca.recycle();
            binding.collapsingLayout.setBackgroundColor(primaryColor);
            binding.collapsingLayout.setContentScrimColor(primaryColor);
            binding.collapsingLayout.setStatusBarScrimColor(primaryColor);

            getWindow().setStatusBarColor(primaryColor);
            binding.activityItemFab.setBackgroundTintList(ColorStateList.valueOf(primaryColor));
            binding.itemStarFab.setBackgroundTintList(ColorStateList.valueOf(primaryColor));
        }

        binding.itemWebview.setItem(itemWithFeed);
    }

    private void switchItem(int itemIndex) {
        Intent itemIntent = new Intent(this, ItemActivity.class);
        ItemWithFeed prevItemWithFeed = allItems.get(itemIndex);
        itemIntent.putExtras(getIntent());
        itemIntent.putExtra(ITEM_ID, prevItemWithFeed.getItem().getId());
        itemIntent.putExtra(IMAGE_URL, prevItemWithFeed.getItem().getImageLink());
        itemIntent.putExtra(INDEX_IN_LIST, itemIndex);

        setIntent(itemIntent);
        commonLoadReloadUI();
        if (mainViewModel != null) {
            mainViewModel.setItemReadState(itemIntent.getIntExtra(ITEM_ID, 0), true, true)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .doOnError(throwable -> Utils.showSnackbar(binding.itemRoot, throwable.getMessage()))
                    .subscribe();
        }
    }

    private void addButtonListenerOrHide() {
        if (allItems != null) {
            binding.prevItemButton.setOnClickListener(view -> {
                switchItem(indexInList <= 0 ? 0 : indexInList - 1);
            });

            binding.nextItemButton.setOnClickListener(view -> {
                switchItem(
                        (indexInList >= allItems.size() - 1)
                                ? (allItems.size() - 1)
                                : indexInList + (mainViewModel.showReadItems() ? 1 : 0)
                );
            });

            binding.openUrlButton.setOnClickListener(view -> {
                openUrl();
            });
            binding.switchItemButtons.setVisibility(View.VISIBLE);
        } else {
            binding.switchItemButtons.setVisibility(View.GONE);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.item_menu, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem item = menu.findItem(R.id.item_open);
        item.setVisible(appBarCollapsed);

        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
            case R.id.item_share:
                shareArticle();
                return true;
            case R.id.item_open:
                openUrl();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onBackPressed() {
        finish();
        super.onBackPressed();
    }

    private void openUrl() {
        int value = Integer.parseInt(SharedPreferencesManager.readString(
                SharedPreferencesManager.SharedPrefKey.OPEN_ITEMS_IN));
        switch (value) {
            case 0:
                openInNavigator();
                break;
            case 1:
                openInWebView();
                break;
            default:
                openInCustomTab();
                break;
        }
    }

    private void openInNavigator() {
        Intent urlIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(itemWithFeed.getItem().getLink()));
        startActivity(urlIntent);
    }

    private void openInWebView() {
        Intent intent = new Intent(this, WebViewActivity.class);
        intent.putExtra(WEB_URL, itemWithFeed.getItem().getLink());
        intent.putExtra(ACTION_BAR_COLOR, itemWithFeed.getBgColor() != 0 ? itemWithFeed.getBgColor() : itemWithFeed.getColor());

        startActivity(intent);
    }

    private void openInCustomTab() {
        boolean darkTheme = Boolean.parseBoolean(SharedPreferencesManager.readString(SharedPreferencesManager.SharedPrefKey.DARK_THEME));
        int color = itemWithFeed.getBgColor() != 0 ? itemWithFeed.getBgColor() : itemWithFeed.getColor();

        CustomTabsIntent customTabsIntent = new CustomTabsIntent.Builder()
                .addDefaultShareMenuItem()
                .setToolbarColor(color)
                .setSecondaryToolbarColor(color)
                .setColorScheme(darkTheme ? CustomTabsIntent.COLOR_SCHEME_DARK : CustomTabsIntent.COLOR_SCHEME_LIGHT)
                .enableUrlBarHiding()
                .setShowTitle(true)
                .build();

        customTabsIntent.launchUrl(this, Uri.parse(itemWithFeed.getItem().getLink()));
    }

    private void shareArticle() {
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, itemWithFeed.getItem().getTitle() + " - " + itemWithFeed.getItem().getLink());
        startActivity(Intent.createChooser(shareIntent, getString(R.string.share_article)));
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        WebView.HitTestResult hitTestResult = binding.itemWebview.getHitTestResult();

        if (hitTestResult.getType() == WebView.HitTestResult.IMAGE_TYPE ||
                hitTestResult.getType() == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE) {
            new MaterialDialog.Builder(this)
                    .title(R.string.image_options)
                    .items(R.array.image_options)
                    .itemsCallback((dialog, itemView, position, text) -> {
                        switch (position) {
                            case 0:
                                shareImage(hitTestResult.getExtra());
                                break;
                            case 1:
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q || PermissionManager.isPermissionGranted(this, Manifest.permission.WRITE_EXTERNAL_STORAGE))
                                    downloadImage(hitTestResult.getExtra());
                                else {
                                    urlToDownload = hitTestResult.getExtra();
                                    PermissionManager.requestPermissions(this, WRITE_EXTERNAL_STORAGE_REQUEST, Manifest.permission.WRITE_EXTERNAL_STORAGE);
                                }
                                break;
                            case 2:
                                urlToDownload = hitTestResult.getExtra();
                                String content = binding.itemWebview.getItemContent();

                                Pattern p = Pattern.compile("(<img.*src=\"" + urlToDownload + "\".*>)");
                                Matcher m = p.matcher(content);
                                if (m.matches()) {
                                    Pattern p2 = Pattern.compile("<img.*(title|alt)=\"(.*?)\".*>");
                                    Matcher m2 = p2.matcher(content);
                                    if (m2.matches()) {
                                        imageTitle = m2.group(2);
                                    } else {
                                        imageTitle = "";
                                    }
                                }
                                new MaterialDialog.Builder(this)
                                        .title(urlToDownload)
                                        .content(imageTitle)
                                        .show();
                                break;
                            default:
                                throw new IllegalStateException("Unexpected value: " + position);
                        }

                    })
                    .show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == WRITE_EXTERNAL_STORAGE_REQUEST) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                downloadImage(urlToDownload);
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                } if (ActivityCompat.shouldShowRequestPermissionRationale(this, permissions[0])) {
                    Utils.showSnackBarWithAction(binding.itemRoot, getString(R.string.download_image_permission),
                            getString(R.string.try_again),
                            v -> PermissionManager.requestPermissions(this, WRITE_EXTERNAL_STORAGE_REQUEST,
                                    Manifest.permission.WRITE_EXTERNAL_STORAGE));
                } else {
                    Utils.showSnackBarWithAction(binding.itemRoot, getString(R.string.download_image_permission),
                            getString(R.string.permissions), v -> {
                                Intent intent = new Intent();
                                intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                                intent.setData(Uri.fromParts("package", getPackageName(), null));
                                startActivity(intent);
                            });
                }

            }
        }

        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    private void downloadImage(String url) {
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url))
                .setTitle(getString(R.string.download_image))
                .setMimeType("image/png")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "Readrops/" + System.currentTimeMillis() + ".png");

        request.allowScanningByMediaScanner();

        DownloadManager downloadManager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
        downloadManager.enqueue(request);
    }

    private void shareImage(String url) {
        KoinJavaComponent.get(GlideRequests.class)
                .asBitmap()
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .load(url)
                .into(new CustomTarget<Bitmap>() {
                    @Override
                    public void onResourceReady(@NonNull Bitmap resource, @Nullable Transition<? super Bitmap> transition) {
                        try {
                            Uri uri = viewModel.saveImageInCache(resource, ItemActivity.this);
                            Intent intent = ShareCompat.IntentBuilder.from(ItemActivity.this)
                                    .setType("image/png")
                                    .setStream(uri)
                                    .setChooserTitle(R.string.share_image)
                                    .createChooserIntent()
                                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

                            startActivity(intent);
                        } catch (Exception e) {
                            Log.e(TAG, e.getMessage());
                        }
                    }

                    @Override
                    public void onLoadCleared(@Nullable Drawable placeholder) {
                        // not useful
                    }
                });

    }
}
