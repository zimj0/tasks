/**
 * Copyright (c) 2012 Todoroo Inc
 *
 * See the file "LICENSE" for the full license governing this code.
 */

package com.todoroo.astrid.provider;

import android.annotation.SuppressLint;
import android.arch.persistence.db.SupportSQLiteDatabase;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import com.todoroo.astrid.api.AstridApiConstants;
import com.todoroo.astrid.dao.Database;
import com.todoroo.astrid.dao.TaskDao;
import com.todoroo.astrid.data.Task;
import com.todoroo.astrid.service.TaskCreator;
import dagger.Lazy;
import java.util.Arrays;
import javax.inject.Inject;
import org.tasks.BuildConfig;
import org.tasks.LocalBroadcastManager;
import org.tasks.analytics.Tracker;
import org.tasks.analytics.Tracking.Events;
import org.tasks.injection.ContentProviderComponent;
import org.tasks.injection.InjectingContentProvider;
import timber.log.Timber;

/**
 * Astrid 3 Content Provider. There are two ways to use this content provider:
 * <ul>
 * <li>access it directly just like any other content provider
 * <li>use the DAO classes from the Astrid API library
 * </ul>
 * <p>
 * The following base URI's are supported:
 * <ul>
 * <li>content://com.todoroo.astrid/tasks - task data ({@link Task})
 * <li>content://com.todoroo.astrid/metadata - task metadata ({@link Metadata})
 * </ul>
 * <p>
 * Each URI supports the following components:
 * <ul>
 * <li>/ - operate on all items (insert, delete, update, query)
 * <li>/123 - operate on item id #123 (delete, update, query)
 * <li>/groupby/title - query with SQL "group by" (query)
 * </ul>
 * <p>
 * If you are writing a third-party application to access this data, you may
 * also consider using one of the Api DAO objects like TaskApiDao.
 *
 * @author Tim Su <tim@todoroo.com>
 */
@SuppressWarnings("DeprecatedIsStillUsed")
@Deprecated
public class Astrid3ContentProvider extends InjectingContentProvider {

  private static final Uri TASKS = Uri.parse("content://" + BuildConfig.APPLICATION_ID + "/tasks");
  private static final Uri METADATA = Uri
      .parse("content://" + BuildConfig.APPLICATION_ID + "/metadata");

  /**
   * URI for making a request over all items
   */
  private static final int URI_DIR = 1;

  /**
   * URI for making a request over a single item by id
   */
  private static final int URI_ITEM = 2;

  /**
   * URI for making a request over all items grouped by some field
   */
  private static final int URI_GROUP = 3;

  private static final UriMatcher uriMatcher;

  @SuppressLint("StaticFieldLeak")
  private static Database databaseOverride;

  // --- instance variables

  private boolean open;
  @Inject Lazy<Database> database;
  @Inject Lazy<Tracker> tracker;
  @Inject Lazy<LocalBroadcastManager> localBroadcastManager;
  @Inject Lazy<TaskCreator> taskCreator;
  @Inject Lazy<TaskDao> taskDao;

  static {
    uriMatcher = new UriMatcher(UriMatcher.NO_MATCH);

    for (Uri uri : new Uri[]{TASKS, METADATA}) {
      String authority = BuildConfig.APPLICATION_ID;

      String table = uri.toString();
      table = table.substring(table.indexOf('/', 11) + 1);

      uriMatcher.addURI(authority, table, URI_DIR);
      uriMatcher.addURI(authority, table + "/#", URI_ITEM);
      uriMatcher.addURI(authority, table +
          AstridApiConstants.GROUP_BY_URI + "*", URI_GROUP);
    }
  }

  public Astrid3ContentProvider() {
    setReadPermission(AstridApiConstants.PERMISSION_READ);
    setWritePermission(AstridApiConstants.PERMISSION_WRITE);
  }

  @Override
  public String getType(@NonNull Uri uri) {
    switch (uriMatcher.match(uri)) {
      case URI_DIR:
      case URI_GROUP:
        return "vnd.android.cursor.dir/vnd.astrid";
      case URI_ITEM:
        return "vnd.android.cursor/vnd.astrid.item";
      default:
        throw new IllegalArgumentException(
            "Unsupported URI " + uri + " (" + uriMatcher.match(uri) + ")");
    }
  }

  @Override
  protected void inject(ContentProviderComponent component) {
    component.inject(this);
  }

  public static void setDatabaseOverride(Database override) {
    databaseOverride = override;
  }

  private SupportSQLiteDatabase getDatabase() {
    if (!open) {
      database.get().openForWriting();
      open = true;
    }
    Database database = databaseOverride == null ? this.database.get() : databaseOverride;
    return database.getDatabase();
  }

    /* ======================================================================
     * =========================================================== delete ===
     * ====================================================================== */

  @Override
  public int delete(@NonNull Uri uri, String selection, String[] selectionArgs) {
    Timber.d("DELETE %s\nselection=%s\nselectionArgs=%s", uri, selection,
        Arrays.toString(selectionArgs));

    String tableName = getTableName(uri);
    if (!"tasks".equals(tableName)) {
      return 0;
    }

    switch (uriMatcher.match(uri)) {
      case URI_GROUP:
        throw new IllegalArgumentException("Only the / or /# URI is valid for deletion.");
      case URI_ITEM: {
        String itemSelector = String.format("_id = '%s'", uri.getPathSegments().get(1));
        if (TextUtils.isEmpty(selection)) {
          selection = itemSelector;
        } else {
          selection = itemSelector + " AND " + selection;
        }
      }
      case URI_DIR:
        break;
      default:
        throw new IllegalArgumentException(
            "Unknown URI " + uri + " (" + uriMatcher.match(uri) + ")");
    }

    int numDeleted = getDatabase().delete(tableName, selection, selectionArgs);
    if (numDeleted > 0) {
      localBroadcastManager.get().broadcastRefresh();
    }
    return numDeleted;
  }

    /* ======================================================================
     * =========================================================== insert ===
     * ====================================================================== */

  /**
   * Insert key/value pairs into given table
   */
  @Override
  public Uri insert(@NonNull Uri uri, ContentValues values) {
    Timber.d("INSERT %s\nvalues=%s", uri, values);
    String tableName = getTableName(uri);

    if (!"tasks".equals(tableName)) {
      return null;
    }

    switch (uriMatcher.match(uri)) {
      case URI_ITEM:
      case URI_GROUP:
        throw new IllegalArgumentException("Only the / URI is valid for insertion.");
      case URI_DIR: {
        Task task = taskCreator.get().createWithValues(null, "");
        taskDao.get().createNew(task);
        long id = task.getId();
        if (id <= 0) {
          throw new SQLException("Could not insert row into database (constraint failed?)");
        }
        getDatabase()
            .update(tableName, SQLiteDatabase.CONFLICT_REPLACE, values, "_id = '" + id + "'",
                null);
        taskDao.get().save(taskDao.get().fetch(id), null);
        Uri newUri = ContentUris.withAppendedId(uri, id);
        getContext().getContentResolver().notifyChange(newUri, null);
        return newUri;
      }

      default:
        throw new IllegalArgumentException("Unknown URI " + uri);
    }
  }

    /* ======================================================================
     * =========================================================== update ===
     * ====================================================================== */

  @Override
  public int update(@NonNull Uri uri, ContentValues values, String selection,
      String[] selectionArgs) {
    Timber.d("UPDATE %s\nvalues=%s\nselection=%s\nselectionArgs=%s", uri, values, selection,
        Arrays.toString(selectionArgs));
    String tableName = getTableName(uri);
    if (!"tasks".equals(tableName)) {
      return 0;
    }

    switch (uriMatcher.match(uri)) {
      case URI_DIR:
      case URI_GROUP:
        throw new IllegalArgumentException("Only the /# URI is valid for update.");
      case URI_ITEM:
        Long id = Long.parseLong(uri.getPathSegments().get(1));
        String byId = String.format("_id = '%s'", id);
        selection = TextUtils.isEmpty(selection) ? byId : byId + " AND " + selection;
        Task original = taskDao.get().fetch(id);
        int updated = getDatabase()
            .update(tableName, SQLiteDatabase.CONFLICT_ABORT, values, selection, selectionArgs);
        if (updated == 1) {
          taskDao.get().save(taskDao.get().fetch(id), original);
          getContext().getContentResolver().notifyChange(uri, null);
        }
        return updated;
      default:
        throw new IllegalArgumentException(
            "Unknown URI " + uri + " (" + uriMatcher.match(uri) + ")");
    }
  }

    /* ======================================================================
     * ============================================================ query ===
     * ====================================================================== */

  /**
   * Query by task.
   * <p>
   * Note that the "sortOrder" field actually can be used to append any
   * sort of clause to your SQL query as long as it is not also the
   * name of a column
   */
  @Override
  public Cursor query(@NonNull Uri uri, String[] projection, String selection,
      String[] selectionArgs, String sortOrder) {
    Timber.d("QUERY %s\ncolumns=%s\nselection=%s\nselectionArgs=%s\nsortOrder=%s", uri,
        Arrays.toString(projection), selection, Arrays.toString(selectionArgs), sortOrder);

    String tableName = getTableName(uri);
    if (!"tasks".equals(tableName)) {
      return null;
    }

    String groupBy = null;

    SQLiteQueryBuilder builder = new SQLiteQueryBuilder();
    builder.setTables(getTableName(uri));

    switch (uriMatcher.match(uri)) {
      case URI_GROUP:
        groupBy = uri.getPathSegments().get(2);
      case URI_DIR:
        break;
      case URI_ITEM:
        String itemSelector = String.format("_id = '%s'", uri.getPathSegments().get(1));
        builder.appendWhere(itemSelector);
        break;
      default:
        throw new IllegalArgumentException(
            "Unknown URI " + uri + " (" + uriMatcher.match(uri) + ")");
    }

    String query = builder.buildQuery(projection, selection, groupBy, null, sortOrder, null);
    Cursor cursor = getDatabase().query(query, selectionArgs);
    cursor.setNotificationUri(getContext().getContentResolver(), uri);
    return cursor;
  }

  // --- change listeners

  public static void notifyDatabaseModification(Context context) {
    ContentResolver cr = context.getContentResolver();
    cr.notifyChange(TASKS, null, false);
  }

  private String getTableName(Uri uri) {
    tracker.get().reportEvent(Events.ASTRID_3_CP);
    String uriString = uri.toString();
    if (uriString.startsWith(TASKS.toString())) {
      return "tasks";
    }
    if (uriString.startsWith(METADATA.toString())) {
      return "tags";
    }
    throw new IllegalArgumentException("Unsupported uri: " + uriString);
  }
}
