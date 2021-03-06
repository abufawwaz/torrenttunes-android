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

package com.torrenttunes.android;

 import android.annotation.TargetApi;
 import android.app.PendingIntent;
 import android.content.ComponentName;
 import android.content.Context;
 import android.content.Intent;
 import android.graphics.Bitmap;
 import android.media.session.MediaSession;
 import android.net.Uri;
 import android.os.Build;
 import android.os.Bundle;
 import android.os.Handler;
 import android.os.Message;
 import android.os.SystemClock;
 import android.support.v4.media.MediaDescriptionCompat;
 import android.support.v4.media.MediaMetadataCompat;
 import android.support.v4.media.browse.MediaBrowserCompat;
 import android.support.v4.media.session.MediaControllerCompat;
 import android.support.v4.media.session.MediaSessionCompat;
 import android.support.v4.media.session.PlaybackStateCompat;
 import android.support.v4.service.MediaBrowserServiceCompat;
 import android.support.v7.media.MediaRouter;

 import com.torrenttunes.android.R;
 import com.torrenttunes.android.model.MusicProvider;
 import com.torrenttunes.android.ui.NowPlayingActivity;
 import com.torrenttunes.android.utils.CarHelper;
 import com.torrenttunes.android.utils.LogHelper;
 import com.torrenttunes.android.utils.MediaIDHelper;
 import com.torrenttunes.android.utils.QueueHelper;
 import com.google.android.gms.cast.ApplicationMetadata;
 import com.google.android.libraries.cast.companionlibrary.cast.VideoCastManager;
 import com.google.android.libraries.cast.companionlibrary.cast.callbacks.VideoCastConsumerImpl;

 import java.lang.ref.WeakReference;
 import java.util.ArrayList;
 import java.util.Collections;
 import java.util.List;

 import static com.torrenttunes.android.utils.MediaIDHelper.MEDIA_ID_MUSICS_BY_ARTISTS;
 import static com.torrenttunes.android.utils.MediaIDHelper.MEDIA_ID_MUSICS_BY_GENRE;
 import static com.torrenttunes.android.utils.MediaIDHelper.MEDIA_ID_ROOT;
 import static com.torrenttunes.android.utils.MediaIDHelper.createBrowseCategoryMediaID;

/**
 * This class provides a MediaBrowser through a service. It exposes the media library to a browsing
 * client, through the onGetRoot and onLoadChildren methods. It also creates a MediaSession and
 * exposes it through its MediaSession.Token, which allows the client to create a MediaController
 * that connects to and send control commands to the MediaSession remotely. This is useful for
 * user interfaces that need to interact with your media session, like Android Auto. You can
 * (should) also use the same service from your app's UI, which gives a seamless playback
 * experience to the user.
 *
 * To implement a MediaBrowserService, you need to:
 *
 * <ul>
 *
 * <li> Extend {@link android.service.media.MediaBrowserService}, implementing the media browsing
 *      related methods {@link android.service.media.MediaBrowserService#onGetRoot} and
 *      {@link android.service.media.MediaBrowserService#onLoadChildren};
 * <li> In onCreate, start a new {@link android.media.session.MediaSession} and notify its parent
 *      with the session's token {@link android.service.media.MediaBrowserService#setSessionToken};
 *
 * <li> Set a callback on the
 *      {@link android.media.session.MediaSession#setCallback(android.media.session.MediaSession.Callback)}.
 *      The callback will receive all the user's actions, like play, pause, etc;
 *
 * <li> Handle all the actual music playing using any method your app prefers (for example,
 *      {@link android.media.MediaPlayer})
 *
 * <li> Update playbackState, "now playing" metadata and queue, using MediaSession proper methods
 *      {@link android.media.session.MediaSession#setPlaybackState(android.media.session.PlaybackState)}
 *      {@link android.media.session.MediaSession#setMetadata(android.media.MediaMetadata)} and
 *      {@link android.media.session.MediaSession#setQueue(java.util.List)})
 *
 * <li> Declare and export the service in AndroidManifest with an intent receiver for the action
 *      android.media.browse.MediaBrowserService
 *
 * </ul>
 *
 * To make your app compatible with Android Auto, you also need to:
 *
 * <ul>
 *
 * <li> Declare a meta-data tag in AndroidManifest.xml linking to a xml resource
 *      with a &lt;automotiveApp&gt; root element. For a media app, this must include
 *      an &lt;uses name="media"/&gt; element as a child.
 *      For example, in AndroidManifest.xml:
 *          &lt;meta-data android:name="com.google.android.gms.car.application"
 *              android:resource="@xml/automotive_app_desc"/&gt;
 *      And in res/values/automotive_app_desc.xml:
 *          &lt;automotiveApp&gt;
 *              &lt;uses name="media"/&gt;
 *          &lt;/automotiveApp&gt;
 *
 * </ul>

 * @see <a href="README.md">README.md</a> for more details.
 *
 */
public class MusicService extends MediaBrowserServiceCompat implements Playback.Callback {

    // Extra on MediaSession that contains the Cast device name currently connected to
    public static final String EXTRA_CONNECTED_CAST = "com.example.android.uamp.CAST_NAME";
    // The action of the incoming Intent indicating that it contains a command
    // to be executed (see {@link #onStartCommand})
    public static final String ACTION_CMD = "com.example.android.uamp.ACTION_CMD";
    // The key in the extras of the incoming Intent indicating the command that
    // should be executed (see {@link #onStartCommand})
    public static final String CMD_NAME = "CMD_NAME";
    // A value of a CMD_NAME key in the extras of the incoming Intent that
    // indicates that the music playback should be paused (see {@link #onStartCommand})
    public static final String CMD_PAUSE = "CMD_PAUSE";
    // A value of a CMD_NAME key that indicates that the music playback should switch
    // to local playback from cast playback.
    public static final String CMD_STOP_CASTING = "CMD_STOP_CASTING";

    private static final String TAG = LogHelper.makeLogTag(MusicService.class);
    // Action to thumbs up a media item
    private static final String CUSTOM_ACTION_THUMBS_UP = "com.example.android.uamp.THUMBS_UP";
    // Delay stopSelf by using a handler.
    private static final int STOP_DELAY = 30000;

    // Music catalog manager
    private MusicProvider mMusicProvider;
    private MediaSessionCompat mSession;
    // "Now playing" queue:
    private List<MediaSessionCompat.QueueItem> mPlayingQueue;
    private int mCurrentIndexOnQueue;
    private MediaNotificationManager mMediaNotificationManager;
    // Indicates whether the service was started.
    private boolean mServiceStarted;
    private Bundle mSessionExtras;
    private DelayedStopHandler mDelayedStopHandler = new DelayedStopHandler(this);
    private Playback mPlayback;
    private MediaRouter mMediaRouter;
    private PackageValidator mPackageValidator;

    /**
     * Consumer responsible for switching the Playback instances depending on whether
     * it is connected to a remote player.
     */
    private final VideoCastConsumerImpl mCastConsumer = new VideoCastConsumerImpl() {

        @Override
        public void onApplicationConnected(ApplicationMetadata appMetadata, String sessionId,
                                           boolean wasLaunched) {
            // In case we are casting, send the device name as an extra on MediaSession metadata.
            mSessionExtras.putString(EXTRA_CONNECTED_CAST, mCastManager.getDeviceName());
            mSession.setExtras(mSessionExtras);
            // Now we can switch to CastPlayback
            Playback playback = new CastPlayback(mMusicProvider);
            mMediaRouter.setMediaSessionCompat(mSession);
            switchToPlayer(playback, true);
        }

        @Override
        public void onDisconnected() {
            LogHelper.d(TAG, "onDisconnected");
            mSessionExtras.remove(EXTRA_CONNECTED_CAST);
            mSession.setExtras(mSessionExtras);
            Playback playback = new LocalPlayback(MusicService.this, mMusicProvider);
            mMediaRouter.setMediaSession(null);
            switchToPlayer(playback, false);
        }
    };

    private VideoCastManager mCastManager;
    private ComponentName mEventReceiver;
    private PendingIntent mMediaPendingIntent;
    private MediaControllerCompat mMediaController;

    /*
     * (non-Javadoc)
     * @see android.app.Service#onCreate()
     */
    @Override
    public void onCreate() {
        super.onCreate();
        LogHelper.d(TAG, "onCreate");

        mPlayingQueue = new ArrayList<>();
        mMusicProvider = new MusicProvider();
        mPackageValidator = new PackageValidator(this);

        mEventReceiver = new ComponentName(getPackageName(), MediaButtonReceiver.class.getName());

        Intent mediaButtonIntent = new Intent(Intent.ACTION_MEDIA_BUTTON);
        mediaButtonIntent.setComponent(mEventReceiver);
        mMediaPendingIntent = PendingIntent.getBroadcast(this, 0, mediaButtonIntent, 0);

        // Start a new MediaSession
        mSession = new MediaSessionCompat(this, "MusicService", mEventReceiver, mMediaPendingIntent);

        final MediaSessionCallback cb = new MediaSessionCallback();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            // Shouldn't really have to do this but the MediaSessionCompat method uses
            // an internal proxy class, which doesn't forward events such as
            // onPlayFromMediaId when running on Lollipop.
            final MediaSession session = (MediaSession) mSession.getMediaSession();
            // session.setCallback(new MediaSessionCallbackProxy(cb));
        } else {
            mSession.setCallback(cb);
        }

        setSessionToken(mSession.getSessionToken());
        mSession.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS |
            MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);

        mPlayback = new LocalPlayback(this, mMusicProvider);
        mPlayback.setState(PlaybackStateCompat.STATE_NONE);
        mPlayback.setCallback(this);
        mPlayback.start();

        Context context = getApplicationContext();
        Intent intent = new Intent(context, NowPlayingActivity.class);
        PendingIntent pi = PendingIntent.getActivity(context, 99 /*request code*/,
                intent, PendingIntent.FLAG_UPDATE_CURRENT);

        mSession.setSessionActivity(pi);

        mSessionExtras = new Bundle();
        CarHelper.setSlotReservationFlags(mSessionExtras, true, true, true);
        mSession.setExtras(mSessionExtras);

        updatePlaybackState(null);

        mMediaNotificationManager = new MediaNotificationManager(this);
        mCastManager = VideoCastManager.getInstance();
        mCastManager.addVideoCastConsumer(mCastConsumer);

        mMediaRouter = MediaRouter.getInstance(getApplicationContext());
    }

    /**
     * (non-Javadoc)
     * @see android.app.Service#onStartCommand(android.content.Intent, int, int)
     */
    @Override
    public int onStartCommand(Intent startIntent, int flags, int startId) {
        if (startIntent != null) {
            String action = startIntent.getAction();
            String command = startIntent.getStringExtra(CMD_NAME);
            if (ACTION_CMD.equals(action)) {
                if (CMD_PAUSE.equals(command)) {
                    if (mPlayback != null && mPlayback.isPlaying()) {
                        handlePauseRequest();
                    }
                } else if (CMD_STOP_CASTING.equals(command)) {
                    mCastManager.disconnect();
                }
            }
        }
        // Reset the delay handler to enqueue a message to stop the service if
        // nothing is playing.
        mDelayedStopHandler.removeCallbacksAndMessages(null);
        mDelayedStopHandler.sendEmptyMessageDelayed(0, STOP_DELAY);
        return START_STICKY;
    }

    /**
     * (non-Javadoc)
     * @see android.app.Service#onDestroy()
     */
    @Override
    public void onDestroy() {
        LogHelper.d(TAG, "onDestroy");
        // Service is being killed, so make sure we release our resources
        handleStopRequest(null);

        mCastManager = VideoCastManager.getInstance();
        mCastManager.removeVideoCastConsumer(mCastConsumer);

        mDelayedStopHandler.removeCallbacksAndMessages(null);
        // Always release the MediaSession to clean up resources
        // and notify associated MediaController(s).
        mSession.release();
    }

    @Override
    public BrowserRoot onGetRoot(String clientPackageName, int clientUid, Bundle rootHints) {
        LogHelper.d(TAG, "OnGetRoot: clientPackageName=" + clientPackageName,
                "; clientUid=" + clientUid + " ; rootHints=", rootHints);
        // To ensure you are not allowing any arbitrary app to browse your app's contents, you
        // need to check the origin:
        if (!mPackageValidator.isCallerAllowed(this, clientPackageName, clientUid)) {
            // If the request comes from an untrusted package, return null. No further calls will
            // be made to other media browsing methods.
            LogHelper.w(TAG, "OnGetRoot: IGNORING request from untrusted package "
                    + clientPackageName);
            return null;
        }
        //noinspection StatementWithEmptyBody
        if (CarHelper.isValidCarPackage(clientPackageName)) {
            // Optional: if your app needs to adapt ads, music library or anything else that
            // needs to run differently when connected to the car, this is where you should handle
            // it.
        }
        return new BrowserRoot(MEDIA_ID_ROOT, null);
    }

    @Override
    public void onLoadChildren(final String parentMediaId, final Result<List<MediaBrowserCompat.MediaItem>> result) {
        if (!mMusicProvider.isInitialized()) {
            // Use result.detach to allow calling result.sendResult from another thread:
            result.detach();

            mMusicProvider.retrieveMediaAsync(new MusicProvider.Callback() {
                @Override
                public void onMusicCatalogReady(boolean success) {
                    if (success) {
                        loadChildrenImpl(parentMediaId, result);
                    } else {
                        updatePlaybackState(getString(R.string.error_no_metadata));
                        result.sendResult(Collections.<MediaBrowserCompat.MediaItem>emptyList());
                    }
                }
            });

        } else {
            // If our music catalog is already loaded/cached, load them into result immediately
            loadChildrenImpl(parentMediaId, result);
        }
    }

    /**
     * Actual implementation of onLoadChildren that assumes that MusicProvider is already
     * initialized.
     */
    private void loadChildrenImpl(final String parentMediaId,
                                  final Result<List<MediaBrowserCompat.MediaItem>> result) {
        LogHelper.d(TAG, "OnLoadChildren: parentMediaId=", parentMediaId);

        List<MediaBrowserCompat.MediaItem> mediaItems = new ArrayList<>();

        if (MEDIA_ID_ROOT.equals(parentMediaId)) {
            LogHelper.d(TAG, "OnLoadChildren.ROOT");
            mediaItems.add(new MediaBrowserCompat.MediaItem(
                    new MediaDescriptionCompat.Builder()
                        .setMediaId(MEDIA_ID_MUSICS_BY_ARTISTS)
                        .setTitle(getString(R.string.browse_artists))
                        .setIconUri(Uri.parse("android.resource://" +
                                "com.example.android.uamp/drawable/ic_by_genre"))
                        .setSubtitle(getString(R.string.browse_artists_subtitle))
                        .build(), MediaBrowserCompat.MediaItem.FLAG_BROWSABLE
            ));

        } else if (MEDIA_ID_MUSICS_BY_ARTISTS.equals(parentMediaId)) {
            LogHelper.d(TAG, "OnLoadChildren.ARTISTS");
            for (String artist : mMusicProvider.getArtists()) {
                MediaBrowserCompat.MediaItem item = new MediaBrowserCompat.MediaItem(
                    new MediaDescriptionCompat.Builder()
                        .setMediaId(createBrowseCategoryMediaID(MEDIA_ID_MUSICS_BY_ARTISTS, artist))
                        .setTitle(artist)
                        .setSubtitle(getString(R.string.browse_musics_by_artist_subtitle, artist))
                        .build(), MediaBrowserCompat.MediaItem.FLAG_BROWSABLE
                );
                mediaItems.add(item);
            }

        } else if (parentMediaId.startsWith(MEDIA_ID_MUSICS_BY_ARTISTS)) {
            String artist = MediaIDHelper.getHierarchy(parentMediaId)[1];
            LogHelper.d(TAG, "OnLoadChildren.SONGS_BY_GENRE  genre=", artist);
            for (MediaMetadataCompat track : mMusicProvider.getMusicsByArtist(artist)) {
                // Since mediaMetadata fields are immutable, we need to create a copy, so we
                // can set a hierarchy-aware mediaID. We will need to know the media hierarchy
                // when we get a onPlayFromMusicID call, so we can create the proper queue based
                // on where the music was selected from (by artist, by genre, random, etc)
                String hierarchyAwareMediaID = MediaIDHelper.createMediaID(
                        track.getDescription().getMediaId(), MEDIA_ID_MUSICS_BY_ARTISTS, artist);
                MediaMetadataCompat trackCopy = new MediaMetadataCompat.Builder(track)
                        .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, hierarchyAwareMediaID)
                        .build();
                MediaBrowserCompat.MediaItem bItem = new MediaBrowserCompat.MediaItem(
                        trackCopy.getDescription(), MediaBrowserCompat.MediaItem.FLAG_PLAYABLE);
                mediaItems.add(bItem);
            }
        } else {
            LogHelper.w(TAG, "Skipping unmatched parentMediaId: ", parentMediaId);
        }
        LogHelper.d(TAG, "OnLoadChildren sending ", mediaItems.size(),
                " results for ", parentMediaId);
        result.sendResult(mediaItems);
    }
    
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private final class MediaSessionCallbackProxy extends MediaSession.Callback {

        private final MediaSessionCallback mMediaSessionCallback;

        public MediaSessionCallbackProxy(MediaSessionCallback cb) {
            mMediaSessionCallback = cb;
        }

        @Override
        public void onPlay() {
            mMediaSessionCallback.onPlay();
        }
        @Override
        public void onSkipToQueueItem(long queueId) {
            mMediaSessionCallback.onSkipToQueueItem(queueId);
        }

        @Override
        public void onSeekTo(long position) {
            mMediaSessionCallback.onSeekTo(position);
        }

        @Override
        public void onPlayFromMediaId(String mediaId, Bundle extras) {
            mMediaSessionCallback.onPlayFromMediaId(mediaId, extras);
        }
        @Override
        public void onPause() {
            mMediaSessionCallback.onPause();
        }
        @Override
        public void onPlayFromSearch(final String query, final Bundle extras) {
            mMediaSessionCallback.onPlayFromSearch(query, extras);
        }
        @Override
        public void onCustomAction(String action, Bundle extras) {
            mMediaSessionCallback.onCustomAction(action, extras);
        }
        @Override
        public void onSkipToPrevious() {
            mMediaSessionCallback.onSkipToPrevious();
        }

        @Override
        public void onSkipToNext() {
            mMediaSessionCallback.onSkipToNext();
        }

        @Override
        public void onStop() {
            mMediaSessionCallback.onStop();
        }
    }

    private final class MediaSessionCallback extends MediaSessionCompat.Callback {

        @Override
        public void onPlay() {
            LogHelper.d(TAG, "play");

            if (mPlayingQueue == null || mPlayingQueue.isEmpty()) {
                mPlayingQueue = QueueHelper.getRandomQueue(mMusicProvider);
                mSession.setQueue(mPlayingQueue);
                mSession.setQueueTitle(getString(R.string.random_queue_title));
                // start playing from the beginning of the queue
                mCurrentIndexOnQueue = 0;
            }

            if (mPlayingQueue != null && !mPlayingQueue.isEmpty()) {
                handlePlayRequest();
            }
        }

        @Override
        public void onSkipToQueueItem(long queueId) {
            LogHelper.d(TAG, "OnSkipToQueueItem:" + queueId);

            if (mPlayingQueue != null && !mPlayingQueue.isEmpty()) {
                // set the current index on queue from the music Id:
                mCurrentIndexOnQueue = QueueHelper.getMusicIndexOnQueue(mPlayingQueue, queueId);
                // play the music
                handlePlayRequest();
            }
        }

        @Override
        public void onSeekTo(long position) {
            LogHelper.d(TAG, "onSeekTo:", position);
            mPlayback.seekTo((int) position);
        }

        @Override
        public void onPlayFromMediaId(String mediaId, Bundle extras) {
            LogHelper.d(TAG, "playFromMediaId mediaId:", mediaId, "  extras=", extras);

            // The mediaId used here is not the unique musicId. This one comes from the
            // MediaBrowser, and is actually a "hierarchy-aware mediaID": a concatenation of
            // the hierarchy in MediaBrowser and the actual unique musicID. This is necessary
            // so we can build the correct playing queue, based on where the track was
            // selected from.
            mPlayingQueue = QueueHelper.getPlayingQueue(mediaId, mMusicProvider);
            mSession.setQueue(mPlayingQueue);
            String queueTitle = getString(R.string.browse_musics_by_genre_subtitle,
                    MediaIDHelper.extractBrowseCategoryValueFromMediaID(mediaId));
            mSession.setQueueTitle(queueTitle);

            if (mPlayingQueue != null && !mPlayingQueue.isEmpty()) {
                // set the current index on queue from the media Id:
                mCurrentIndexOnQueue = QueueHelper.getMusicIndexOnQueue(mPlayingQueue, mediaId);

                if (mCurrentIndexOnQueue < 0) {
                    LogHelper.e(TAG, "playFromMediaId: media ID ", mediaId,
                            " could not be found on queue. Ignoring.");
                } else {
                    // play the music
                    handlePlayRequest();
                }
            }
        }

        @Override
        public void onPause() {
            LogHelper.d(TAG, "pause. current state=" + mPlayback.getState());
            handlePauseRequest();
        }

        @Override
        public void onStop() {
            LogHelper.d(TAG, "stop. current state=" + mPlayback.getState());
            handleStopRequest(null);
        }

        @Override
        public void onSkipToNext() {
            LogHelper.d(TAG, "skipToNext");
            mCurrentIndexOnQueue++;
            if (mPlayingQueue != null && mCurrentIndexOnQueue >= mPlayingQueue.size()) {
                // This sample's behavior: skipping to next when in last song returns to the
                // first song.
                mCurrentIndexOnQueue = 0;
            }
            if (QueueHelper.isIndexPlayable(mCurrentIndexOnQueue, mPlayingQueue)) {
                handlePlayRequest();
            } else {
                LogHelper.e(TAG, "skipToNext: cannot skip to next. next Index=" +
                        mCurrentIndexOnQueue + " queue length=" +
                        (mPlayingQueue == null ? "null" : mPlayingQueue.size()));
                handleStopRequest("Cannot skip");
            }
        }

        @Override
        public void onSkipToPrevious() {
            LogHelper.d(TAG, "skipToPrevious");
            mCurrentIndexOnQueue--;
            if (mPlayingQueue != null && mCurrentIndexOnQueue < 0) {
                // This sample's behavior: skipping to previous when in first song restarts the
                // first song.
                mCurrentIndexOnQueue = 0;
            }
            if (QueueHelper.isIndexPlayable(mCurrentIndexOnQueue, mPlayingQueue)) {
                handlePlayRequest();
            } else {
                LogHelper.e(TAG, "skipToPrevious: cannot skip to previous. previous Index=" +
                        mCurrentIndexOnQueue + " queue length=" +
                        (mPlayingQueue == null ? "null" : mPlayingQueue.size()));
                handleStopRequest("Cannot skip");
            }
        }

        @Override
        public void onCustomAction(String action, Bundle extras) {
            if (CUSTOM_ACTION_THUMBS_UP.equals(action)) {
                LogHelper.i(TAG, "onCustomAction: favorite for current track");
                MediaMetadataCompat track = getCurrentPlayingMusic();
                if (track != null) {
                    String musicId = track.getString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID);
                    mMusicProvider.setFavorite(musicId, !mMusicProvider.isFavorite(musicId));
                }
                // playback state needs to be updated because the "Favorite" icon on the
                // custom action will change to reflect the new favorite state.
                updatePlaybackState(null);
            } else {
                LogHelper.e(TAG, "Unsupported action: ", action);
            }
        }

        @Override
        public void onPlayFromSearch(final String query, final Bundle extras) {
            LogHelper.d(TAG, "playFromSearch  query=", query, " extras=", extras);

            mPlayback.setState(PlaybackStateCompat.STATE_CONNECTING);

            // Voice searches may occur before the media catalog has been
            // prepared. We only handle the search after the musicProvider is ready.
            mMusicProvider.retrieveMediaAsync(new MusicProvider.Callback() {
                @Override
                public void onMusicCatalogReady(boolean success) {
                    mPlayingQueue = QueueHelper.getPlayingQueueFromSearch(query, extras,
                        mMusicProvider);

                    LogHelper.d(TAG, "playFromSearch  playqueue.length=" + mPlayingQueue.size());
                    mSession.setQueue(mPlayingQueue);

                    if (mPlayingQueue != null && !mPlayingQueue.isEmpty()) {
                        // immediately start playing from the beginning of the search results
                        mCurrentIndexOnQueue = 0;

                        handlePlayRequest();
                    } else {
                        // if nothing was found, we need to warn the user and stop playing
                        handleStopRequest(getString(R.string.no_search_results));
                    }
                }
            });
        }
    }

    /**
     * Handle a request to play music
     */
    private void handlePlayRequest() {
        LogHelper.d(TAG, "handlePlayRequest: mState=" + mPlayback.getState());

        mDelayedStopHandler.removeCallbacksAndMessages(null);
        if (!mServiceStarted) {
            LogHelper.v(TAG, "Starting service");
            // The MusicService needs to keep running even after the calling MediaBrowser
            // is disconnected. Call startService(Intent) and then stopSelf(..) when we no longer
            // need to play media.
            startService(new Intent(getApplicationContext(), MusicService.class));
            mServiceStarted = true;
        }

        if (QueueHelper.isIndexPlayable(mCurrentIndexOnQueue, mPlayingQueue)) {
            updateMetadata();
            mPlayback.play(mPlayingQueue.get(mCurrentIndexOnQueue));
        }

        // With the compatibility library this has to be set after the metadata.
        if (!mSession.isActive()) {
            mSession.setActive(true);
        }
    }

    /**
     * Handle a request to pause music
     */
    private void handlePauseRequest() {
        LogHelper.d(TAG, "handlePauseRequest: mState=" + mPlayback.getState());
        mPlayback.pause();
        // reset the delayed stop handler.
        mDelayedStopHandler.removeCallbacksAndMessages(null);
        mDelayedStopHandler.sendEmptyMessageDelayed(0, STOP_DELAY);
    }

    /**
     * Handle a request to stop music
     */
    private void handleStopRequest(String withError) {
        LogHelper.d(TAG, "handleStopRequest: mState=" + mPlayback.getState() + " error=", withError);
        mPlayback.stop(true);
        // reset the delayed stop handler.
        mDelayedStopHandler.removeCallbacksAndMessages(null);
        mDelayedStopHandler.sendEmptyMessageDelayed(0, STOP_DELAY);

        updatePlaybackState(withError);

        // service is no longer necessary. Will be started again if needed.
        stopSelf();
        mServiceStarted = false;
    }

    private void updateMetadata() {
        if (!QueueHelper.isIndexPlayable(mCurrentIndexOnQueue, mPlayingQueue)) {
            LogHelper.e(TAG, "Can't retrieve current metadata.");
            updatePlaybackState(getResources().getString(R.string.error_no_metadata));
            return;
        }
        MediaSessionCompat.QueueItem queueItem = mPlayingQueue.get(mCurrentIndexOnQueue);
        String musicId = MediaIDHelper.extractMusicIDFromMediaID(
                queueItem.getDescription().getMediaId());
        MediaMetadataCompat track = mMusicProvider.getMusic(musicId);
        final String trackId = track.getString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID);
        if (!musicId.equals(trackId)) {
            IllegalStateException e = new IllegalStateException("track ID should match musicId.");
            LogHelper.e(TAG, "track ID should match musicId.",
                " musicId=", musicId, " trackId=", trackId,
                " mediaId from queueItem=", queueItem.getDescription().getMediaId(),
                " title from queueItem=", queueItem.getDescription().getTitle(),
                " mediaId from track=", track.getDescription().getMediaId(),
                " title from track=", track.getDescription().getTitle(),
                " source.hashcode from track=", track.getString(MusicProvider.CUSTOM_METADATA_TRACK_SOURCE).hashCode(),
                e);
            throw e;
        }
        LogHelper.d(TAG, "Updating metadata for MusicID= " + musicId);
        mSession.setMetadata(track);

        // Set the proper album artwork on the media session, so it can be shown in the
        // locked screen and in other places.
        if (track.getDescription().getIconBitmap() == null &&
                track.getDescription().getIconUri() != null) {
            String albumUri = track.getDescription().getIconUri().toString();
            AlbumArtCache.getInstance().fetch(albumUri, new AlbumArtCache.FetchListener() {
                @Override
                public void onFetched(String artUrl, Bitmap bitmap, Bitmap icon) {
                    MediaSessionCompat.QueueItem queueItem = mPlayingQueue.get(mCurrentIndexOnQueue);
                    MediaMetadataCompat track = mMusicProvider.getMusic(trackId);
                    track = new MediaMetadataCompat.Builder(track)

                        // set high resolution bitmap in METADATA_KEY_ALBUM_ART. This is used, for
                        // example, on the lockscreen background when the media session is active.
                        .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, bitmap)

                        // set small version of the album art in the DISPLAY_ICON. This is used on
                        // the MediaDescription and thus it should be small to be serialized if
                        // necessary..
                        .putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, icon)

                        .build();

                    mMusicProvider.updateMusic(trackId, track);

                    // If we are still playing the same music
                    String currentPlayingId = MediaIDHelper.extractMusicIDFromMediaID(
                        queueItem.getDescription().getMediaId());
                    if (trackId.equals(currentPlayingId)) {
                        mSession.setMetadata(track);
                    }
                }
            });
        }
    }

    /**
     * Update the current media player state, optionally showing an error message.
     *
     * @param error if not null, error message to present to the user.
     */
    private void updatePlaybackState(String error) {
        LogHelper.d(TAG, "updatePlaybackState, playback state=" + mPlayback.getState());
        long position = PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN;
        if (mPlayback != null && mPlayback.isConnected()) {
            position = mPlayback.getCurrentStreamPosition();
        }

        PlaybackStateCompat.Builder stateBuilder = new PlaybackStateCompat.Builder()
                .setActions(getAvailableActions());

        setCustomAction(stateBuilder);
        int state = mPlayback.getState();

        // If there is an error message, send it to the playback state:
        if (error != null) {
            // Error states are really only supposed to be used for errors that cause playback to
            // stop unexpectedly and persist until the user takes action to fix it.
            stateBuilder.setErrorMessage(error);
            state = PlaybackStateCompat.STATE_ERROR;
        }
        stateBuilder.setState(state, position, 1.0f, SystemClock.elapsedRealtime());

        // Set the activeQueueItemId if the current index is valid.
        if (QueueHelper.isIndexPlayable(mCurrentIndexOnQueue, mPlayingQueue)) {
            MediaSessionCompat.QueueItem item = mPlayingQueue.get(mCurrentIndexOnQueue);
//            stateBuilder.setActiveQueueItemId(item.getQueueId());
        }

        mSession.setPlaybackState(stateBuilder.build());

        if (state == PlaybackStateCompat.STATE_PLAYING || state == PlaybackStateCompat.STATE_PAUSED) {
            mMediaNotificationManager.startNotification();
        }
    }

    private void setCustomAction(PlaybackStateCompat.Builder stateBuilder) {
        MediaMetadataCompat currentMusic = getCurrentPlayingMusic();
        if (currentMusic != null) {
            // Set appropriate "Favorite" icon on Custom action:
            String musicId = currentMusic.getString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID);
            int favoriteIcon = R.drawable.ic_star_off;
            if (mMusicProvider.isFavorite(musicId)) {
                favoriteIcon = R.drawable.ic_star_on;
            }
            LogHelper.d(TAG, "updatePlaybackState, setting Favorite custom action of music ",
                    musicId, " current favorite=", mMusicProvider.isFavorite(musicId));
//            stateBuilder.addCustomAction(CUSTOM_ACTION_THUMBS_UP, getString(R.string.favorite),
//                    favoriteIcon);
        }
    }

    private long getAvailableActions() {
        long actions = PlaybackStateCompat.ACTION_PLAY | PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID |
                PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH;
        if (mPlayingQueue == null || mPlayingQueue.isEmpty()) {
            return actions;
        }
        if (mPlayback.isPlaying()) {
            actions |= PlaybackStateCompat.ACTION_PAUSE;
        }
        if (mCurrentIndexOnQueue > 0) {
            actions |= PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS;
        }
        if (mCurrentIndexOnQueue < mPlayingQueue.size() - 1) {
            actions |= PlaybackStateCompat.ACTION_SKIP_TO_NEXT;
        }
        return actions;
    }

    private MediaMetadataCompat getCurrentPlayingMusic() {
        if (QueueHelper.isIndexPlayable(mCurrentIndexOnQueue, mPlayingQueue)) {
            MediaSessionCompat.QueueItem item = mPlayingQueue.get(mCurrentIndexOnQueue);
            if (item != null) {
                LogHelper.d(TAG, "getCurrentPlayingMusic for musicId=",
                        item.getDescription().getMediaId());
                return mMusicProvider.getMusic(
                        MediaIDHelper.extractMusicIDFromMediaID(item.getDescription().getMediaId()));
            }
        }
        return null;
    }

    /**
     * Implementation of the Playback.Callback interface
     */
    @Override
    public void onCompletion() {
        // The media player finished playing the current song, so we go ahead
        // and start the next.
        if (mPlayingQueue != null && !mPlayingQueue.isEmpty()) {
            // In this sample, we restart the playing queue when it gets to the end:
            mCurrentIndexOnQueue++;
            if (mCurrentIndexOnQueue >= mPlayingQueue.size()) {
                mCurrentIndexOnQueue = 0;
            }
            handlePlayRequest();
        } else {
            // If there is nothing to play, we stop and release the resources:
            handleStopRequest(null);
        }
    }

    @Override
    public void onPlaybackStatusChanged(int state) {
        updatePlaybackState(null);
    }

    @Override
    public void onError(String error) {
        updatePlaybackState(error);
    }

    @Override
    public void onMetadataChanged(String mediaId) {
        LogHelper.d(TAG, "onMetadataChanged", mediaId);
        List<MediaSessionCompat.QueueItem> queue = QueueHelper.getPlayingQueue(mediaId, mMusicProvider);
        int index = QueueHelper.getMusicIndexOnQueue(queue, mediaId);
        if (index > -1) {
            mCurrentIndexOnQueue = index;
            mPlayingQueue = queue;
            updateMetadata();
        }
    }

    /**
     * Helper to switch to a different Playback instance
     * @param playback switch to this playback
     */
    private void switchToPlayer(Playback playback, boolean resumePlaying) {
        if (playback == null) {
            throw new IllegalArgumentException("Playback cannot be null");
        }
        // suspend the current one.
        int oldState = mPlayback.getState();
        int pos = mPlayback.getCurrentStreamPosition();
        String currentMediaId = mPlayback.getCurrentMediaId();
        LogHelper.d(TAG, "Current position from " + playback + " is ", pos);
        mPlayback.stop(false);
        playback.setCallback(this);
        playback.setCurrentStreamPosition(pos < 0 ? 0 : pos);
        playback.setCurrentMediaId(currentMediaId);
        playback.start();
        // finally swap the instance
        mPlayback = playback;
        switch (oldState) {
            case PlaybackStateCompat.STATE_BUFFERING:
            case PlaybackStateCompat.STATE_CONNECTING:
            case PlaybackStateCompat.STATE_PAUSED:
                mPlayback.pause();
                break;
            case PlaybackStateCompat.STATE_PLAYING:
                if (resumePlaying && QueueHelper.isIndexPlayable(mCurrentIndexOnQueue, mPlayingQueue)) {
                    mPlayback.play(mPlayingQueue.get(mCurrentIndexOnQueue));
                } else if (!resumePlaying) {
                    mPlayback.pause();
                } else {
                    mPlayback.stop(true);
                }
                break;
            case PlaybackStateCompat.STATE_NONE:
                break;
            default:
                LogHelper.d(TAG, "Default called. Old state is ", oldState);
        }
    }

    /**
     * A simple handler that stops the service if playback is not active (playing)
     */
    private static class DelayedStopHandler extends Handler {
        private final WeakReference<MusicService> mWeakReference;

        private DelayedStopHandler(MusicService service) {
            mWeakReference = new WeakReference<>(service);
        }

        @Override
        public void handleMessage(Message msg) {
            MusicService service = mWeakReference.get();
            if (service != null && service.mPlayback != null) {
                if (service.mPlayback.isPlaying()) {
                    LogHelper.d(TAG, "Ignoring delayed stop since the media player is in use.");
                    return;
                }
                LogHelper.d(TAG, "Stopping service with delay handler.");
                service.stopSelf();
                service.mServiceStarted = false;
            }
        }
    }
}
