/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.torrenttunes.android.model;

import android.os.AsyncTask;
import android.support.v4.media.MediaMetadataCompat;

import com.torrenttunes.android.utils.LogHelper;
import com.torrenttunes.android.utils.Tools;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.Map;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * Utility class to get a list of MusicTrack's based on a server-side JSON
 * configuration.
 */
public class MusicProvider {

    private static final String TAG = LogHelper.makeLogTag(MusicProvider.class);

//    private static final String CATALOG_URL =
//        "http://storage.googleapis.com/automotive-media/music.json";

    private static final String ARTIST_CATALOG_URL = "http://torrenttunes.ml/get_artists";

    private static final String ARTIST_CATALOG_SONGS_URL = "http://torrenttunes.ml/get_all_songs";

    public static final String CUSTOM_METADATA_TRACK_SOURCE = "__SOURCE__";


    private static final String JSON_ARTIST_NAME = "name";
    private static final String JSON_ARTIST_MBID = "mbid";

//    private static final String JSON_MUSIC = "music";
//    private static final String JSON_TITLE = "title";
//    private static final String JSON_ALBUM = "album";
//    private static final String JSON_ARTIST = "artist";
//    private static final String JSON_GENRE = "genre";
//    private static final String JSON_SOURCE = "source";
//    private static final String JSON_IMAGE = "image";
//    private static final String JSON_TRACK_NUMBER = "trackNumber";
//    private static final String JSON_TOTAL_TRACK_COUNT = "totalTrackCount";
//    private static final String JSON_DURATION = "duration";

    // Categorized caches for artist data:

    private Map<String, String> mArtistCache;
    private JSONArray mCurrentArtistSongCache;
//    private ConcurrentMap<String, List<MediaMetadataCompat>> mMusicListByArtist;
//    private final ConcurrentMap<String, MutableMediaMetadataCompat> mMusicListById;

    private final Set<String> mFavoriteTracks;

    public enum State {
        NON_INITIALIZED, INITIALIZING, INITIALIZED
    }

    private volatile State mCurrentState = State.NON_INITIALIZED;
    public volatile State mCurrentSongState = State.NON_INITIALIZED;

    public interface Callback {
        void onArtistCatalogReady(boolean success);
    }

    public interface ArtistSongCallback {
        void onArtistSongCatalogReady(boolean success);
    }

    public MusicProvider() {
        mArtistCache = new LinkedHashMap<>();

        mFavoriteTracks = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    }

    /**
     * Get an iterator over the list of artists
     *
     * @return genres
     */
    public Map<String, String> getArtists() {
        if (mCurrentState != State.INITIALIZED) {
            return Collections.emptyMap();
        }
        return mArtistCache;
//        return mArtistCache.keySet();
    }

    public JSONArray getCurrentArtistSongs() {
        return mCurrentArtistSongCache;
    }

    /**
     * Get music tracks of the given genre
     *
     */
//    public Iterable<MediaMetadataCompat> getMusicsByArtist(String artistMbid) {
//        if (mCurrentState != State.INITIALIZED || !mMusicListByArtist.containsKey(artist)) {
//            return Collections.emptyList();
//        }
//        return mMusicListByArtist.get(artist);
//    }

    /**
     * Very basic implementation of a search that filter music tracks with title containing
     * the given query.
     *
     */
//    public Iterable<MediaMetadataCompat> searchMusicBySongTitle(String query) {
//        return searchMusic(MediaMetadataCompat.METADATA_KEY_TITLE, query);
//    }

    /**
     * Very basic implementation of a search that filter music tracks with album containing
     * the given query.
     *
     */
//    public Iterable<MediaMetadataCompat> searchMusicByAlbum(String query) {
//        return searchMusic(MediaMetadataCompat.METADATA_KEY_ALBUM, query);
//    }

    /**
     * Very basic implementation of a search that filter music tracks with artist containing
     * the given query.
     *
     */
//    public Iterable<MediaMetadataCompat> searchMusicByArtist(String query) {
//        return searchMusic(MediaMetadataCompat.METADATA_KEY_ARTIST, query);
//    }

//    Iterable<MediaMetadataCompat> searchMusic(String metadataField, String query) {
//        if (mCurrentState != State.INITIALIZED) {
//            return Collections.emptyList();
//        }
//        ArrayList<MediaMetadataCompat> result = new ArrayList<>();
//        query = query.toLowerCase(Locale.US);
//        for (MutableMediaMetadataCompat track : mMusicListById.values()) {
//            if (track.metadata.getString(metadataField).toLowerCase(Locale.US)
//                .contains(query)) {
//                result.add(track.metadata);
//            }
//        }
//        return result;
//    }


    /**
     * Return the MediaMetadataCompat for the given musicID.
     *
     * @param musicId The unique, non-hierarchical music ID.
     */
//    public MediaMetadataCompat getMusic(String musicId) {
//        return mMusicListById.containsKey(musicId) ? mMusicListById.get(musicId).metadata : null;
//    }

//    public synchronized void updateMusic(String musicId, MediaMetadataCompat metadata) {
//        MutableMediaMetadataCompat track = mMusicListById.get(musicId);
//        if (track == null) {
//            return;
//        }
//
//        String oldArtist = track.metadata.getString(MediaMetadataCompat.METADATA_KEY_ARTIST);
//        String newArtist = metadata.getString(MediaMetadataCompat.METADATA_KEY_ARTIST);
//
//        track.metadata = metadata;
//
//        // if genre has changed, we need to rebuild the list by genre
//        if (!oldArtist.equals(newArtist)) {
//            buildListsByArtist();
//        }
//    }

    public void setFavorite(String musicId, boolean favorite) {
        if (favorite) {
            mFavoriteTracks.add(musicId);
        } else {
            mFavoriteTracks.remove(musicId);
        }
    }

    public boolean isFavorite(String musicId) {
        return mFavoriteTracks.contains(musicId);
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

    public void retrieveArtistSongCatalogAsync(final String artistMbid, final ArtistSongCallback callback) {
        LogHelper.d(TAG, "retrieveArtistsSongAsync called");

        if (mCurrentSongState == State.INITIALIZED) {
            // Nothing to do, execute callback immediately
            callback.onArtistSongCatalogReady(true);
            return;
        }

        // Asynchronously load the music catalog in a separate thread
        new AsyncTask<Void, Void, State>() {
            @Override
            protected State doInBackground(Void... params) {
                retrieveCurrentArtistSongs(artistMbid);
                return mCurrentState;
            }

            @Override
            protected void onPostExecute(State current) {
                if (callback != null) {
                    callback.onArtistSongCatalogReady(current == State.INITIALIZED);
                }
            }
        }.execute();
    }

//    private synchronized void buildListsByArtist() {
//        ConcurrentMap<String, List<MediaMetadataCompat>> newMusicListByArtist = new ConcurrentHashMap<>();
//
//        for (MutableMediaMetadataCompat m : mMusicListById.values()) {
//            String artist = m.metadata.getString(MediaMetadataCompat.METADATA_KEY_ARTIST);
//            List<MediaMetadataCompat> list = newMusicListByArtist.get(artist);
//            if (list == null) {
//                list = new ArrayList<>();
//                newMusicListByArtist.put(artist, list);
//            }
//            list.add(m.metadata);
//        }
//        mMusicListByArtist = newMusicListByArtist;
//    }

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

//                        LogHelper.i(TAG, artistMbid + "/" + artist);

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

    private synchronized void retrieveCurrentArtistSongs(String artistMbid) {

        mCurrentArtistSongCache = Tools.fetchJSONArrayFromUrl(
                ARTIST_CATALOG_SONGS_URL + "/" + artistMbid);
    }



//    private MediaMetadataCompat buildFromJSON(JSONObject json, String basePath) throws JSONException {
//        String title = json.getString(JSON_TITLE);
//        String album = json.getString(JSON_ALBUM);
//        String artist = json.getString(JSON_ARTIST);
//        String genre = json.getString(JSON_GENRE);
//        String source = json.getString(JSON_SOURCE);
//        String iconUrl = json.getString(JSON_IMAGE);
//        int trackNumber = json.getInt(JSON_TRACK_NUMBER);
//        long totalTrackCount = json.getLong(JSON_TOTAL_TRACK_COUNT);
//        int duration = json.getInt(JSON_DURATION) * 1000; // ms
//
//        LogHelper.d(TAG, "Found music track: ", json);
//
//        // Media is stored relative to JSON file
//        if (!source.startsWith("http")) {
//            source = basePath + source;
//        }
//        if (!iconUrl.startsWith("http")) {
//            iconUrl = basePath + iconUrl;
//        }
//        // Since we don't have a unique ID in the server, we fake one using the hashcode of
//        // the music source. In a real world app, this could come from the server.
//        String id = String.valueOf(source.hashCode());
//
//        // Adding the music source to the MediaMetadataCompat (and consequently using it in the
//        // mediaSession.setMetadata) is not a good idea for a real world music app, because
//        // the session metadata can be accessed by notification listeners. This is done in this
//        // sample for convenience only.
//        return new MediaMetadataCompat.Builder()
//                .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, id)
//                .putString(CUSTOM_METADATA_TRACK_SOURCE, source)
//                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, album)
//                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist)
//                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration)
//                .putString(MediaMetadataCompat.METADATA_KEY_GENRE, genre)
//                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, iconUrl)
//                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
//                .putLong(MediaMetadataCompat.METADATA_KEY_TRACK_NUMBER, trackNumber)
//               // Note: With MediaMetadataCompat we seem to crash when setting the total track count.
//               // .putLong(MediaMetadataCompat.METADATA_KEY_NUM_TRACKS, totalTrackCount * 1L)
//
//                .build();
//    }




}
