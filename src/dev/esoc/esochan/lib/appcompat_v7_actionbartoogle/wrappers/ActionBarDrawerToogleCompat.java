package dev.esoc.esochan.lib.appcompat_v7_actionbartoogle.wrappers;

import android.content.res.Configuration;
import androidx.drawerlayout.widget.DrawerLayout.DrawerListener;
import android.view.MenuItem;

public interface ActionBarDrawerToogleCompat extends DrawerListener {
    boolean onOptionsItemSelected(MenuItem item);

    void onConfigurationChanged(Configuration newConfig);

    void syncState();
}