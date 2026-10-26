package com.local.grundig.playstation;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.media.tv.TvContract;
import android.os.Bundle;
import android.widget.Toast;

/** A source-built tile for a TV input supplied when building, with no fixed HDMI port. */
public final class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        try {
            Intent input = new Intent(Intent.ACTION_VIEW)
                    .setDataAndType(TvContract.buildChannelUriForPassthroughInput(
                            getString(R.string.ps5_input_id)), "vnd.android.cursor.item/channel")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            String tvPackage = getString(R.string.ps5_tv_package);
            if (!tvPackage.isEmpty()) input.setPackage(tvPackage);
            startActivity(input);
        } catch (ActivityNotFoundException | SecurityException | IllegalArgumentException error) {
            Toast.makeText(this, R.string.input_unavailable, Toast.LENGTH_LONG).show();
        } finally {
            finish();
        }
    }
}
