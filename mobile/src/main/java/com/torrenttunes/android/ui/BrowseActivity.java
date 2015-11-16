package com.torrenttunes.android.ui;

import android.app.Activity;
import android.app.ListActivity;
import android.content.Context;
import android.os.Bundle;
import android.support.v4.media.browse.MediaBrowserCompat;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import com.tonicartos.superslim.LayoutManager;
import com.torrenttunes.android.R;
import com.torrenttunes.android.model.BrowseProvider;
import com.torrenttunes.android.model.MusicProvider;
import com.torrenttunes.android.utils.LogHelper;

import java.util.Collections;

/**
 * Created by tyler on 10/9/15.
 */
public class BrowseActivity extends BaseActivity {

    private static final String TAG = LogHelper.makeLogTag(BrowseActivity.class);


    static final String[] FRUITS = new String[] { "Apple", "Avocado", "Banana",
            "Blueberry", "Coconut", "Durian", "Guava", "Kiwifruit",
            "Jackfruit", "Mango", "Olive", "Pear", "Sugar-apple" };


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_browse);
        initializeToolbar();

        BrowseProvider mBrowseProvider = new BrowseProvider();

        onLoadChildren(mBrowseProvider);
        setContentView(R.layout.activity_browse);


        ListView listView = (ListView) findViewById(R.id.fruit_list);
        listView.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, FRUITS));

//        findViewById(R.id.browse_layout);
    }

    @Override
    protected void onStart() {
        super.onStart();


    }

    private void onLoadChildren(BrowseProvider mBrowseProvider) {
        if (!mBrowseProvider.isInitialized()) {

            mBrowseProvider.retrieveArtistCatalogAsync(new BrowseProvider.Callback() {
                @Override
                public void onArtistCatalogReady(boolean success) {
                    if (success) {
//                        loadChildrenImpl();
                    }
                }
            });

        } else {
            // If our music catalog is already loaded/cached, load them into result immediately
            loadChildrenImpl(mBrowseProvider);
        }
    }

    private void loadChildrenImpl(BrowseProvider mBrowseProvider) {

    }

}
