package com.miniride.activities;

import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.miniride.Miniride;
import com.miniride.R;
import com.miniride.models.CurrentUser;
import com.miniride.models.Driver;
import com.miniride.utilities.Prefs;
import com.miniride.utilities.Utils;
import com.slidingmenu.lib.app.SlidingFragmentActivity;

public class DriverHomeFragmentActivity extends SlidingFragmentActivity implements OnClickListener {

    private TextView textViewRadiusDisplay;
    private Button buttonMenu;
    private RelativeLayout driverHomeHeader;

    @Override public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        setContentView(R.layout.driver_home_fragment);
        setBehindContentView(R.layout.driver_menu_fragment);

        Miniride.setDriverHomeFragmentActivity(this);

        this.driverHomeHeader = (RelativeLayout) findViewById(R.id.header_ui);
        this.textViewRadiusDisplay = (TextView) findViewById(R.id.textViewDriverHomeRadius);

        this.buttonMenu = (Button) findViewById(R.id.buttonMenu);
        buttonMenu.setOnClickListener(this);

        if (CurrentUser.driverHasPaused())
            Prefs.saveBool("driverHasPaused", false);

        setRadiusDisplay();
        customizeSlidingMenu();

    }

    @Override
    protected void onResume() {
        super.onResume();
        Utils.displayFlashMessage(this, getIntent(), true);
        getIntent().removeExtra("flashMessage");
    }

    private void customizeSlidingMenu() {
        this.setSlidingActionBarEnabled(true);
        getSlidingMenu().setShadowWidthRes(R.dimen.shadow_width);
        getSlidingMenu().setBehindOffsetRes(R.dimen.actionbar_home_width);
        getSlidingMenu().setBehindScrollScale(0.25f);
    }

    public void setRadiusDisplay() {
        if (CurrentUser.driverHasPaused())
            textViewRadiusDisplay.setText(getString(R.string.appIsPaused));
        else
            textViewRadiusDisplay.setText(getString(R.string.requestRadius) + " " + Driver.loadCurrent().radius.toString() + " "
                    + getString(R.string.kilometers));
    }

    public void isDriverReceivingOrder(boolean isRideRequested) {
        if (isRideRequested)
            driverHomeHeader.setVisibility(View.GONE);
        else
            driverHomeHeader.setVisibility(View.VISIBLE);
        Miniride.getDriverHomeActivityInstance().toggleFooterLayout(isRideRequested);
    }

    @Override public void onClick(View v) {
        int id = v.getId();
        switch (id) {
        case R.id.buttonMenu:
            toggle();
            break;
        default:
            break;
        }

    }
}
