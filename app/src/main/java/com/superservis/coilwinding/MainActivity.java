package com.superservis.coilwinding;

import android.graphics.Color;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.Timer;

import ioio.lib.util.android.IOIOActivity;

public class MainActivity extends IOIOActivity {

    SeekBar seekBar_wire, seekBar_guide;
    TextView tvw_seekBarValue_guide, tvw_seekBarValue_wire, tvw_guidePush_counter, tvw_wirePush_counter, tvwCoilsPerLayer_counter, tvwCoilLayers_counter;
    boolean getPeriodFromSeekBar_guide = false;
    TextView tvw_guidePeriod, tvw_wirePeriod;
    EditText etx_coilsPerLayer, etx_coilLayers, etx_cueDuration;
    TextView tvw_timer, tvw_message;

    Timer timer = new Timer();
    long starttime = 0;

    /** IOIO controlled devices status. These are for UI. **/
    private enum motorSelect{
        GUIDE, WIRE, WIRING
    }
    private motorSelect motorSelect_current_;
    boolean motorSelect_switch_;

    private static final int OUTLET1_ON_PIN = 34;

    private enum motorState{
        RUNNING,
        STOP,
        DISCONNECTED,
        STOPPED,
        PAUSED,
        PLOTTING,
        RUNNING_CW,
        RUN_CW, RUN_CCW, WIRING, WIRE_CCW, GUIDE_RUN_CCW, GUIDE_RUN_CW, WIRE_RUN_CCW, WIRE_RUN_CW, WIRE_CW, WIRE_CW_AUTO, WIRE_CCW_AUTO, EMPTY_QUEUE, RUNNING_CCW
    }

    private motorState motorState_current_, motorState_guide_current_, motorState_wire_current_, motorState_coilWiring_current_;
    private motorState motorState_target_ = motorState.STOP, motorState_guide_target_ = motorState.STOP, motorState_wire_target_ = motorState.STOP, motorState_coilWiring_target_ = motorState.STOP;
    private boolean direction_g_ = false, direction_w_ = false, direction_dc_ = false, periodChanged = false;
    private int speed_ = 400;

    private int windings_desired_ = 1500, coilsPerLayer_desired_, coilLayers_desired_, duration_cue_; //(*16us base)
    private int windings_counter_ = 0, auto_counter_ = 0, auto_divider_ = 3;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    public void onClickCoilWinding(View view){
        motorState_target_ = motorState.WIRE_CCW;
        direction_w_ = false;
    }

}
