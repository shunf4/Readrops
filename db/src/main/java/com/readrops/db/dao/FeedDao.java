package com.readrops.db.dao;


import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Query;
import androidx.room.RoomWarnings;
import androidx.room.Transaction;

import com.readrops.db.entities.Feed;
import com.readrops.db.entities.account.Account;
import com.readrops.db.pojo.FeedWithFolder;

import java.util.ArrayList;
import java.util.List;

import io.reactivex.Completable;
import io.reactivex.Single;

@Dao
public abstract class FeedDao implements BaseDao<Feed> {

    @Query("Select * from Feed Where account_id = :accountId order by name ASC")
    public abstract List<Feed> getFeeds(int accountId);

    @Query("Select * from Feed Order By name ASC")
    public abstract LiveData<List<Feed>> getAllFeeds();

    @Query("Select * from Feed Where id = :feedId")
    public abstract Feed getFeedById(int feedId);

    @Query("Select id From Feed Where url = :url and account_id = :accountId")
    public abstract int getFeedIdByUrl(String url, int accountId);

    @Query("Select case When :feedUrl In (Select url from Feed Where account_id = :accountId) Then 1 else 0 end")
    public abstract boolean feedExists(String feedUrl, int accountId);

    @Query("Select case When :remoteId In (Select remoteId from Feed Where account_id = :accountId) Then 1 else 0 end")
    public abstract boolean remoteFeedExists(String remoteId, int accountId);

    @Query("Select count(*) from Feed Where account_id = :accountId")
    public abstract Single<Integer> getFeedCount(int accountId);

    @Query("Select * from Feed Where url = :feedUrl And account_id = :accountId")
    public abstract Feed getFeedByUrl(String feedUrl, int accountId);

    @Query("Select id from Feed Where remoteId = :remoteId And account_id = :accountId")
    public abstract int getFeedIdByRemoteId(String remoteId, int accountId);

    @Query("Select * from Feed Where folder_id = :folderId")
    public abstract List<Feed> getFeedsByFolder(int folderId);

    @Query("Select * from Feed Where account_id = :accountId And folder_id is null")
    public abstract List<Feed> getFeedsWithoutFolder(int accountId);

    @Query("Update Feed set etag = :etag, last_modified = :lastModified Where id = :feedId")
    public abstract void updateHeaders(String etag, String lastModified, int feedId);

    @Query("Update Feed set name = :feedName, url = :feedUrl, folder_id = :folderId Where id = :feedId")
    public abstract void updateFeedFields(int feedId, String feedName, String feedUrl, Integer folderId);

    @Query("Update Feed set name = :name, folder_id = :folderId Where remoteId = :remoteFeedId And account_id = :accountId")
    public abstract void updateNameAndFolder(String remoteFeedId, int accountId, String name, Integer folderId);

    @Query("Update Feed set text_color = :textColor, background_color = :bgColor Where id = :feedId")
    public abstract void updateColors(int feedId, int textColor, int bgColor);

    @SuppressWarnings(RoomWarnings.CURSOR_MISMATCH)
    @Query("Select Feed.name as feed_name, Feed.id as feed_id, Folder.name as folder_name, Folder.id as folder_id, Folder.remoteId as folder_remoteId," +
            "Feed.description as feed_description, Feed.icon_url as feed_icon_url, Feed.url as feed_url, Feed.folder_id as feed_folder_id" +
            ", Feed.siteUrl as feed_siteUrl, Feed.remoteId as feed_remoteId from Feed Left Join Folder on Feed.folder_id = Folder.id Where Feed.account_id = :accountId Order by Feed.name")
    public abstract LiveData<List<FeedWithFolder>> getAllFeedsWithFolder(int accountId);

    @SuppressWarnings(RoomWarnings.CURSOR_MISMATCH)
    @Query("Select id, name, icon_url, notification_enabled, text_color, background_color, account_id From Feed Where account_id = :accountId")
    public abstract LiveData<List<Feed>> getFeedsForNotifPermission(int accountId);

    @Query("Select * From Feed Where id in (:ids)")
    public abstract List<Feed> selectFromIdList(List<Long> ids);

    @Query("Select remoteId From Feed Where account_id = :accountId")
    public abstract List<String> getFeedRemoteIdsOfAccount(int accountId);

    @Query("Delete from Feed Where remoteId in (:ids)")
    abstract void deleteByIds(List<String> ids);

    @Query("Delete From Feed Where account_id = :accountId")
    public abstract Completable deleteAll(int accountId);

    @Query("Select id From Folder Where remoteId = :remoteId And account_id = :accountId")
    abstract int getRemoteFolderLocalId(String remoteId, int accountId);

    @Query("Update Feed set notification_enabled = :enabled Where id = :feedId")
    public abstract Completable updateFeedNotificationState(int feedId, boolean enabled);

    @Query("Update Feed set notification_enabled = :enabled Where account_id = :accountId")
    public abstract Completable updateAllFeedsNotificationState(int accountId, boolean enabled);

    /**
     * Insert, update and delete feeds, by account
     *
     * @param feeds   feeds to insert or update
     * @param account owner of the feeds
     * @return the list of the inserted feeds ids
     */
    @Transaction
    public List<Long> feedsUpsert(List<Feed> feeds, Account account) {
        List<String> accountFeedIds = getFeedRemoteIdsOfAccount(account.getId());
        List<Feed> feedsToInsert = new ArrayList<>();

        for (Feed feed : feeds) {
            Integer folderId;

            try {
                int remoteFolderId = Integer.parseInt(feed.getRemoteFolderId());
                folderId = remoteFolderId == 0 ? null : getRemoteFolderLocalId(feed.getRemoteFolderId(), account.getId());
            } catch (Exception e) {
                folderId = feed.getRemoteFolderId() == null ? null : getRemoteFolderLocalId(feed.getRemoteFolderId(), account.getId());
            }

            if (remoteFeedExists(feed.getRemoteId(), account.getId())) {
                updateNameAndFolder(feed.getRemoteId(), account.getId(), feed.getName(), folderId);

                accountFeedIds.remove(feed.getRemoteId());
            } else {
                feed.setFolderId(folderId);

                feedsToInsert.add(feed);
            }
        }

        Log.d("FeedDao", "in feedsUpsert: accountFeedIds (to be deleted): " + accountFeedIds.toString());

        if (!accountFeedIds.isEmpty())
            deleteByIds(accountFeedIds);

        return insert(feedsToInsert);
    }
}

