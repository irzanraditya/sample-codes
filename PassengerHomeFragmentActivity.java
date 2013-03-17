package com.miniride.activities;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.miniride.Miniride;
import com.miniride.R;
import com.miniride.models.AddressData;
import com.miniride.models.CurrentUser;
import com.miniride.models.Passenger;
import com.miniride.utilities.Utils;
import com.slidingmenu.lib.app.SlidingFragmentActivity;

public class PassengerHomeFragmentActivity extends SlidingFragmentActivity implements OnClickListener {

    public static final String ACTION_SET_LABEL = "PassengerHomeFragmentActivity.action_set_label";
    public static final String EXTRA_SET_LABEL = "PassengerHomeFragmentActivity.extra_set_label";
    public static final String ACTION_SET_IMAGE = "PassengerHomeFragmentActivity.action_set_image";
    private Button buttonMenu;

    private class ChangeHeaderUiReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(final Context context, Intent intent) {
            buttonMenu = (Button)PassengerHomeFragmentActivity.this.findViewById(R.id.buttonMenu);
            final ImageView minirideLogo = (ImageView)PassengerHomeFragmentActivity.this.findViewById(R.id.minirideLogo);
            final TextView textLabel = (TextView)PassengerHomeFragmentActivity.this.findViewById(R.id.textLabel);

            final String action = intent.getAction();
            if (action.equals(ACTION_SET_LABEL)) {
                String message = intent.getStringExtra(EXTRA_SET_LABEL);
                textLabel.setText(message);
                textLabel.setVisibility(View.VISIBLE);
                minirideLogo.setVisibility(View.INVISIBLE);
                buttonMenu.setVisibility(View.INVISIBLE);
            } else if (action.equals(ACTION_SET_IMAGE)) {
                textLabel.setVisibility(View.INVISIBLE);
                minirideLogo.setVisibility(View.VISIBLE);
                buttonMenu.setVisibility(View.VISIBLE);
            }
        }
    }
    private ChangeHeaderUiReceiver changeUiReceiver;
    private Context context;
    private RelativeLayout passengerHeader;

    private boolean isFlashMessageShown;

    @Override public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.passenger_home_fragment);
        setBehindContentView(R.layout.passenger_menu_fragment);

        Miniride.setPassengerHomeFragmentActivity(this);

        this.buttonMenu = (Button) findViewById(R.id.buttonMenu);
        this.buttonMenu.setOnClickListener(this);

        this.passengerHeader = (RelativeLayout) findViewById(R.id.header_ui);

        this.context = this;

        isFlashMessageShown = false;
        if (savedInstanceState != null){
            isFlashMessageShown = savedInstanceState.getBoolean("flashMessageShown", false);
            if (isFlashMessageShown){
               Utils.removeFlashMessage(getIntent());
            }
        }

        customizeSlidingMenu();

        changeUiReceiver = new ChangeHeaderUiReceiver();
        final IntentFilter changeUiFilter = new IntentFilter();
        changeUiFilter.addAction(ACTION_SET_LABEL);
        changeUiFilter.addAction(ACTION_SET_IMAGE);
        registerReceiver(changeUiReceiver, changeUiFilter);
    }

    public void handleAddressChange() {
        Intent triggerIntent = getIntent();
        Bundle bundle = triggerIntent.getExtras();
        if (bundle != null) {
            if (bundle.getBoolean("fromAddressSelection", false)) {
                Miniride.getPassengerHomeActivityInstance().fromAddressChange = true;
                AddressData newPickupAddressData = AddressData.fromJson(bundle.getString("pickupAddressData"));
                if (bundle.getBoolean("startedForPickupAddress", false)) {
                    Miniride.getPassengerHomeActivityInstance().setPickupAddressToLocation(newPickupAddressData.asLocation());
                    Miniride.getPassengerHomeActivityInstance().updateMap(newPickupAddressData.latitude, newPickupAddressData.longitude);
                } else {
                    AddressData newDestinationAddressData = AddressData.fromJson(bundle.getString("destinationAddressData"));
                    Miniride.getPassengerHomeActivityInstance().setPickupAddressToLocation(newPickupAddressData.asLocation());
                    Miniride.getPassengerHomeActivityInstance().setDestinationAddressToLocation(newDestinationAddressData.asLocation());
                    Miniride.getPassengerHomeActivityInstance().showDestinationAddressBar();

                    Miniride.getPassengerHomeActivityInstance().createOrderController.start();

                    if (bundle.getBoolean("startedForPickupAddressWithDestinationPresent", false)) {
                        Miniride.getPassengerHomeActivityInstance().updateMap(newPickupAddressData.latitude, newPickupAddressData.longitude);
                    }

                }
                // DEACTIVATED FOR NOW: We show the order overlay immediately.
                // Miniride.getPassengerHomeActivityInstance().updateRouteOnMap();
            }
        }
    }

    public void isPassengerCreatingOrder(boolean isRideRequested, boolean isCreatingOrder) {
        if (isCreatingOrder){
            buttonMenu.setVisibility(View.INVISIBLE);
            if(isRideRequested)
                passengerHeader.setVisibility(View.GONE);
        }
        else{
            passengerHeader.setVisibility(View.VISIBLE);
            buttonMenu.setVisibility(View.VISIBLE);
        }
    }

    @Override public void onDestroy() {
        super.onDestroy();
        unregisterReceiver(changeUiReceiver);
    }

    @Override
    protected void onResume() {
        super.onResume();
        CurrentUser.setAsPassenger();
        String message = Utils.getFlashMessage(getIntent());
        if (message != null) {
            Utils.displayMsg(context, message);
            Utils.removeFlashMessage(getIntent());
            isFlashMessageShown = true;
        }
    }

    @Override
    protected void onSaveInstanceState (Bundle outState){
        super.onSaveInstanceState(outState);
        outState.putBoolean("flashMessageShown", isFlashMessageShown);
    }

    private void customizeSlidingMenu() {
        this.setSlidingActionBarEnabled(true);
        getSlidingMenu().setShadowWidthRes(R.dimen.shadow_width);
        getSlidingMenu().setBehindOffsetRes(R.dimen.actionbar_home_width);
        getSlidingMenu().setBehindScrollScale(0.25f);
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
