package stefano.loris.habitmining.helper;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import java.util.ArrayList;

public class DBHelper extends SQLiteOpenHelper {

    private static final String TAG = DBHelper.class.getSimpleName();

    // Database Version
    private static final int DATABASE_VERSION = 1;

    public static final String DATABASE_NAME = "MyDBName.db";

    public static final String ACTIVITIES_TABLE_NAME = "activities";
    public static final String KEY_ACTIVITY_NAME = "name";

    public DBHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {

        String createActionsTable = "CREATE TABLE " + ACTIVITIES_TABLE_NAME + "(" +
            KEY_ACTIVITY_NAME + " TEXT NOT NULL PRIMARY KEY)";

        db.execSQL(createActionsTable);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // drop old tables
        db.execSQL("DROP TABLE IF EXISTS " + ACTIVITIES_TABLE_NAME);
        // create the db again
        onCreate(db);
    }

    // MAIN USER OPERATIONS

    /**
     * @return all the activities that are in the local database
     */
    public ArrayList<String> getActivities() {
        ArrayList<String> activities = new ArrayList<>();

        String query = "SELECT name FROM " + ACTIVITIES_TABLE_NAME;

        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(query, null);
        // Move to first row
        cursor.moveToFirst();
        if (cursor.getCount() > 0) {
            for(int i = 0; i < cursor.getCount(); i++) {
                activities.add(cursor.getString(0));
                cursor.moveToPosition(i);
            }
        }
        cursor.close();
        db.close();

        // return activities
        return activities;
    }

    /**
     * Inserisce un'attività nel db locale
     *
     * @param activity attività da inserire
     */
    public void storeActivity(String activity) {
        SQLiteDatabase db = this.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put(KEY_ACTIVITY_NAME, activity);

        // Inserting Row
        long id = db.insert(ACTIVITIES_TABLE_NAME, null, values);
        //db.close();

        Log.d(TAG, "New activity inserted into sqlite: " + id);
    }

    /**
     * Remove activities
     */
    public void reset() {
        SQLiteDatabase db = this.getWritableDatabase();
        String query = "DELETE FROM " + ACTIVITIES_TABLE_NAME;
        db.execSQL(query);
        //db.close();
    }
}
