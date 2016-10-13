package com.xiaoyu.run4thirteenweeks;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.content.SharedPreferencesCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.Wearable;

public class MainActivity extends AppCompatActivity implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    private static final String KEY_SPRINT_DURATION = "sprint_duration";
    private static final int DEF_SPRINT_DURATION_MIN = 2;

    private static final String KEY_REST_DURATION = "rest_duration";
    private static final int DEF_REST_DURATION_MIN = 2;

    private EditText mEditTextSprintDuration;
    private EditText mEditTextRestDuration;
    private GoogleApiClient mGoogleApiClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                saveAndSynchronize();

                Snackbar.make(view, "Data have been save & synchronized.", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });

        mEditTextSprintDuration = (EditText) findViewById(R.id.editTextSprintDuration);
        mEditTextRestDuration = (EditText) findViewById(R.id.editTextRestDuration);

        mGoogleApiClient = new GoogleApiClient.Builder(this,
            this, this)
            .addApi(Wearable.API)
            .build();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (mEditTextSprintDuration != null) {
            mEditTextSprintDuration.setText(String.valueOf(getSprintDuration()));
        }

        if (mEditTextRestDuration != null) {
            mEditTextRestDuration.setText(String.valueOf(getRestDuration()));
        }

        mGoogleApiClient.connect();
    }

    @Override
    protected void onPause() {
        super.onPause();

        mGoogleApiClient.disconnect();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private int getSprintDuration() {
        SharedPreferences preferences = getPreferences(MODE_PRIVATE);
        int sprintDuration = DEF_SPRINT_DURATION_MIN;
        if (preferences.contains(KEY_SPRINT_DURATION)) {
            sprintDuration = preferences.getInt(KEY_SPRINT_DURATION, DEF_SPRINT_DURATION_MIN);
        }
        return sprintDuration;
    }

    private int getRestDuration() {
        SharedPreferences preferences = getPreferences(MODE_PRIVATE);
        int restDuration = DEF_REST_DURATION_MIN;
        if (preferences.contains(KEY_REST_DURATION)) {
            restDuration = preferences.getInt(KEY_REST_DURATION, DEF_REST_DURATION_MIN);
        }
        return restDuration;
    }

    private void saveAndSynchronize() {
        SharedPreferences preferences = getPreferences(MODE_PRIVATE);

        saveSprintDuration(preferences);

        saveRestDuration(preferences);

        synchronizeDataWithWearable();
    }

    private void saveRestDuration(SharedPreferences preferences) {
        if (mEditTextRestDuration != null) {
            int duration = DEF_REST_DURATION_MIN;
            try {
                duration = Integer.parseInt(mEditTextRestDuration.getText().toString());
            } catch (NumberFormatException ex) {

            }

            SharedPreferences.Editor editor = preferences.edit().putInt(KEY_REST_DURATION, duration);
            SharedPreferencesCompat.EditorCompat.getInstance().apply(editor);
        }
    }

    private void saveSprintDuration(SharedPreferences preferences) {
        if (mEditTextSprintDuration != null) {
            int duration = DEF_SPRINT_DURATION_MIN;
            try {
                duration = Integer.parseInt(mEditTextSprintDuration.getText().toString());
            } catch (NumberFormatException ex) {

            }
            SharedPreferences.Editor editor = preferences.edit().putInt(KEY_SPRINT_DURATION, duration);
            SharedPreferencesCompat.EditorCompat.getInstance().apply(editor);
        }
    }

    private void synchronizeDataWithWearable() {
        PutDataMapRequest request = PutDataMapRequest.create("/duration");
        DataMap dataMap = request.getDataMap();
        dataMap.putInt(KEY_SPRINT_DURATION, getSprintDuration());
        dataMap.putInt(KEY_REST_DURATION, getRestDuration());
        PendingResult<DataApi.DataItemResult> pendingResult =
            Wearable.DataApi.putDataItem(mGoogleApiClient, request.asPutDataRequest());
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {

    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }
}
