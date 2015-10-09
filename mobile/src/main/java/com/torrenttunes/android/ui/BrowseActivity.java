package com.torrenttunes.android.ui;

import android.os.Bundle;

import com.torrenttunes.android.R;
import com.torrenttunes.android.utils.LogHelper;

/**
 * Created by tyler on 10/9/15.
 */
public class BrowseActivity extends BaseActivity {

    private static final String TAG = LogHelper.makeLogTag(BrowseActivity.class);

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_browse);
        initializeToolbar();
    }
}
