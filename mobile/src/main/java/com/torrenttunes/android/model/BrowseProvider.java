package com.torrenttunes.android.model;

import android.os.AsyncTask;

import com.torrenttunes.android.utils.LogHelper;
import com.torrenttunes.android.utils.Tools;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by tyler on 10/9/15.
 */
public class BrowseProvider {

    private static final String TAG = LogHelper.makeLogTag(BrowseProvider.class);

    private static final String ARTIST_CATALOG_URL = "http://torrenttunes.ml/get_artists";

    private static final String JSON_ARTIST_NAME = "name";
    private static final String JSON_ARTIST_MBID = "mbid";

    private Map<String, String> mArtistCache;

    enum State {
        NON_INITIALIZED, INITIALIZING, INITIALIZED
    }

    private volatile State mCurrentState = State.NON_INITIALIZED;

    public interface Callback {
        void onArtistCatalogReady(boolean success);
    }

    public BrowseProvider() {
        mArtistCache = new LinkedHashMap<>();
    }

    public Map<String, String> getArtists() {
        if (mCurrentState != State.INITIALIZED) {
            return Collections.emptyMap();
        }
        return mArtistCache;
    }

    public boolean isInitialized() {
        return mCurrentState == State.INITIALIZED;
    }

    /**
     * Get the list of music tracks from a server and caches the track information
     * for future reference, keying tracks by musicId and grouping by genre.
     */
    public void retrieveArtistCatalogAsync(final Callback callback) {
        LogHelper.d(TAG, "retrieveArtistsAsync called");
        if (mCurrentState == State.INITIALIZED) {
            // Nothing to do, execute callback immediately
            callback.onArtistCatalogReady(true);
            return;
        }

        // Asynchronously load the music catalog in a separate thread
        new AsyncTask<Void, Void, State>() {
            @Override
            protected State doInBackground(Void... params) {
                retrieveArtists();
                return mCurrentState;
            }

            @Override
            protected void onPostExecute(State current) {
                if (callback != null) {
                    callback.onArtistCatalogReady(current == State.INITIALIZED);
                }
            }
        }.execute();
    }

    private synchronized void retrieveArtists() {
        try {
            if (mCurrentState == State.NON_INITIALIZED) {
                mCurrentState = State.INITIALIZING;

                JSONArray jsonArray = Tools.fetchJSONArrayFromUrl(ARTIST_CATALOG_URL);
                if (jsonArray == null) {
                    return;
                }

                if (jsonArray != null) {
                    for (int j = 0; j < jsonArray.length(); j++) {
                        JSONObject artistObj = jsonArray.getJSONObject(j);

                        String artist = artistObj.getString(JSON_ARTIST_NAME);
                        String artistMbid = artistObj.getString(JSON_ARTIST_MBID);
                        mArtistCache.put(artistMbid, artist);

//                        MediaMetadataCompat item = buildFromJSON(tracks.getJSONObject(j), path);
//                        String musicId = item.getString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID);
//                        mMusicListById.put(musicId, new MutableMediaMetadataCompat(musicId, item))
// ;

                    }

//                    buildListsByArtist();
                }
                mCurrentState = State.INITIALIZED;
            }
        } catch (JSONException e) {
            LogHelper.e(TAG, e, "Could not retrieve artist list");
        } finally {
            if (mCurrentState != State.INITIALIZED) {
                // Something bad happened, so we reset state to NON_INITIALIZED to allow
                // retries (eg if the network connection is temporary unavailable)
                mCurrentState = State.NON_INITIALIZED;
            }
        }
    }

}
