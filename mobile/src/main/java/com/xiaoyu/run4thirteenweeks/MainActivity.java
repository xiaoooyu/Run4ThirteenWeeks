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

import butterknife.BindView;
import butterknife.ButterKnife;

public class MainActivity extends AppCompatActivity
    implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    @BindView(R.id.editTextSprintDuration)
    EditText mEditTextSprintDuration;

    private GoogleApiClient mGoogleApiClient;

    private static final String KEY_SPRINT_FORMULA = "sprint_formula";
    private static final String DEFL_SPRINT_FORMULA = "10-1-15-1-20-1-10";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);

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

    private String getSprintDuration() {
        SharedPreferences preferences = getPreferences(MODE_PRIVATE);
        String sprintDuration = DEFL_SPRINT_FORMULA;
        if (preferences.contains(KEY_SPRINT_FORMULA)) {
            sprintDuration = preferences.getString(KEY_SPRINT_FORMULA, DEFL_SPRINT_FORMULA);
        }
        return sprintDuration;
    }

    private void saveAndSynchronize() {
        SharedPreferences preferences = getPreferences(MODE_PRIVATE);

        saveSprintDuration(preferences);

        synchronizeDataWithWearable();
    }

    private void saveSprintDuration(SharedPreferences preferences) {
        if (mEditTextSprintDuration != null) {
            String formula = DEFL_SPRINT_FORMULA;
            try {
                formula = mEditTextSprintDuration.getText().toString();
            } catch (NumberFormatException ex) {

            }
            SharedPreferences.Editor editor = preferences.edit().putString(KEY_SPRINT_FORMULA, formula);
            SharedPreferencesCompat.EditorCompat.getInstance().apply(editor);
        }
    }

    private void synchronizeDataWithWearable() {
        PutDataMapRequest request = PutDataMapRequest.create("/duration");
        DataMap dataMap = request.getDataMap();
        dataMap.putString(KEY_SPRINT_FORMULA, getSprintDuration());
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
