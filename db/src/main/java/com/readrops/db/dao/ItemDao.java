package com.readrops.db.dao;


import androidx.lifecycle.LiveData;
import androidx.paging.DataSource;
import androidx.room.Dao;
import androidx.room.Query;
import androidx.room.RawQuery;
import androidx.room.RoomWarnings;
import androidx.sqlite.db.SupportSQLiteQuery;

import com.readrops.db.entities.Feed;
import com.readrops.db.entities.Folder;
import com.readrops.db.entities.Item;
import com.readrops.db.pojo.ItemWithFeed;
import com.readrops.db.pojo.StarItem;

import java.util.List;

import io.reactivex.Completable;
import io.reactivex.Single;

@Dao
public interface ItemDao extends BaseDao<Item> {

    @RawQuery(observedEntities = {Item.class, Folder.class, Feed.class})
    DataSource.Factory<Integer, ItemWithFeed> selectAll(SupportSQLiteQuery query);

    @Query("Select * From Item Where id = :itemId")
    Item select(int itemId);

    @Query("Delete From Item Where feed_id In (Select id From Feed Where account_id = :accountId)")
    Completable deleteAll(int accountId);

    @Query("Select case When :guid In (Select guid From Item Inner Join Feed on Item.feed_id = Feed.id and account_id = :accountId) Then 1 else 0 end")
    boolean itemExists(String guid, int accountId);

    @Query("Select case When :remoteId In (Select remoteId from Item) And :feedId In (Select feed_id From Item) Then 1 else 0 end")
    boolean remoteItemExists(String remoteId, int feedId);

    @Query("Select * From Item Where remoteId = :remoteId And feed_id = :feedId")
    Item selectByRemoteId(String remoteId, int feedId);

    /**
     * Set an item read or unread
     *
     * @param itemId      id of the item to update
     * @param read        1 for read, 0 for unread
     * @param readChanged
     */
    @Query("Update Item Set read_changed = :readChanged, read = :read Where id = :itemId")
    Completable setReadState(int itemId, boolean read, boolean readChanged);

    @Query("Update Item set read_changed = 1, read = :readState Where feed_id In (Select id From Feed Where account_id = :accountId)")
    Completable setAllItemsReadState(int readState, int accountId);

    @Query("Update Item set read_changed = 1, read = :readState Where feed_id = :feedId")
    Completable setAllFeedItemsReadState(int feedId, int readState);

    @Query("Update Item set read_it_later = 1 Where id = :itemId")
    Completable setReadItLater(int itemId);

    @Query("Select count(*) From Item Where feed_id = :feedId And read = 0")
    int getUnreadCount(int feedId);

    @SuppressWarnings(RoomWarnings.CURSOR_MISMATCH)
    @Query("Select Item.id, title, Item.description, content, link, pub_date, image_link, author, read, text_color, " +
            "background_color, read_time, starred, Feed.name, Feed.id as feedId, siteUrl, Folder.id as folder_id, " +
            "Folder.name as folder_name from Item Inner Join Feed On Item.feed_id = Feed.id Left Join Folder on Folder.id = Feed.folder_id Where Item.id = :id")
    Single<ItemWithFeed> getItemById(int id);

    @Query("Select Item.remoteId From Item Inner Join Feed On Item.feed_id = Feed.id Where read_changed = 1 And read = 1 And account_id = :accountId")
    List<String> getReadChanges(int accountId);

    @Query("Select Item.remoteId From Item Inner Join Feed On Item.feed_id = Feed.id Where read_changed = 1 And read = 0 And account_id = :accountId")
    List<String> getUnreadChanges(int accountId);

    @Query("Select Item.remoteId From Item Inner Join Feed On Item.feed_id = Feed.id Where starred_changed = 1 And starred = 1 And account_id = :accountId")
    List<String> getFreshRSSStarChanges(int accountId);

    @Query("Select Item.remoteId From Item Inner Join Feed On Item.feed_id = Feed.id Where starred_changed = 1 And starred = 0 And account_id = :accountId")
    List<String> getFreshRSSUnstarChanges(int accountId);

    @Query("Select Item.guid, Feed.remoteId as feedRemoteId From Item Inner Join Feed On Item.feed_id = Feed.id Where starred_changed = 1 And starred = 1 And account_id = :accountId")
    List<StarItem> getStarChanges(int accountId);

    @Query("Select Item.guid, Feed.remoteId as feedRemoteId From Item Inner Join Feed On Item.feed_id = Feed.id Where starred_changed = 1 And starred = 0 And account_id = :accountId")
    List<StarItem> getUnstarChanges(int accountId);

    @Query("Update Item set read_changed = 0 Where feed_id in (Select id From Feed Where account_id = :accountId)")
    void resetReadChanges(int accountId);

    @Query("Update Item set starred_changed = 0 Where feed_id in (Select id From Feed Where account_id = :accountId)")
    void resetStarChanges(int accountId);

    @Query("Update Item set read = :read, starred = :starred Where remoteId = :remoteId")
    void setReadAndStarState(String remoteId, boolean read, boolean starred);

    @Query("Update Item set starred = :starred, starred_changed = :starredChanged Where id = :itemId")
    Completable setStarState(int itemId, boolean starred, boolean starredChanged);
}
