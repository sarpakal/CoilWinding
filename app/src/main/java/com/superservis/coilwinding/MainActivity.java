package com.superservis.coilwinding;

import android.graphics.Color;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.Timer;

import ioio.lib.api.DigitalOutput;
import ioio.lib.api.Sequencer;
import ioio.lib.api.exception.ConnectionLostException;
import ioio.lib.util.BaseIOIOLooper;
import ioio.lib.util.IOIOLooper;
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
        motorState_target_ = motorState.WIRE_CW_AUTO;
        direction_w_ = true;
        starttime = System.currentTimeMillis();
    }

    class Looper extends BaseIOIOLooper {
        private Sequencer.ChannelCueBinary stepper_g_DirCue_ = new Sequencer.ChannelCueBinary();
        private Sequencer.ChannelCueSteps stepper_g_StepCue_ = new Sequencer.ChannelCueSteps();

        private Sequencer.ChannelCueBinary stepper_w_DirCue_ = new Sequencer.ChannelCueBinary();
        private Sequencer.ChannelCueSteps stepper_w_StepCue_ = new Sequencer.ChannelCueSteps();

        private Sequencer.ChannelCue[] cue_ = new Sequencer.ChannelCue[] {stepper_g_DirCue_, stepper_g_StepCue_, stepper_w_DirCue_, stepper_w_StepCue_};
        private Sequencer.ChannelCue[] cue_g = new Sequencer.ChannelCue[] {stepper_g_DirCue_, stepper_g_StepCue_};

        private Sequencer sequencer_, sequencer_g_, sequencer_w_;

        final Sequencer.ChannelConfigBinary stepper_g_DirConfig = new Sequencer.ChannelConfigBinary(false, false, new DigitalOutput.Spec(3));
        final Sequencer.ChannelConfigSteps stepper_g_StepConfig = new Sequencer.ChannelConfigSteps(new DigitalOutput.Spec(4));

        final Sequencer.ChannelConfigBinary stepper_w_DirConfig = new Sequencer.ChannelConfigBinary(false, false, new DigitalOutput.Spec(5));
        final Sequencer.ChannelConfigSteps stepper_w_StepConfig = new Sequencer.ChannelConfigSteps(new DigitalOutput.Spec(6));

        final Sequencer.ChannelConfig[] config_g = new Sequencer.ChannelConfig[] {stepper_g_DirConfig, stepper_g_StepConfig};
        final Sequencer.ChannelConfig[] config_w = new Sequencer.ChannelConfig[] { stepper_w_DirConfig, stepper_w_StepConfig };
        final Sequencer.ChannelConfig[] config = new Sequencer.ChannelConfig[] {stepper_g_DirConfig,
                stepper_g_StepConfig, stepper_w_DirConfig, stepper_w_StepConfig };
        boolean direction_g_changed_flag = false; // flag if guide motor direction is changed

        private DigitalOutput mOutlet1_do;

        /**
         * Setup(): Called every time a connection with IOIO has been established. Typically used to open pins.
         **/
        @Override
        protected void setup() throws ConnectionLostException, InterruptedException {
            sequencer_ = ioio_.openSequencer(config); // Event: CLOSED
            sequencer_.close();
            sequencer_.waitEventType(Sequencer.Event.Type.STOPPED);// At this point, the FIFO might still be at zero capacity, wait until opening is complete.  // Event: STOPPED
            setCurrentState(motorState.STOPPED);
            setCurrentState_guide(motorState.STOPPED);
            setCurrentState_wire(motorState.STOPPED);
            setCurrentState_coilWiring(motorState.STOPPED);
        } // End Method setup()

        @Override
        public void loop() throws ConnectionLostException, InterruptedException {

            switch (motorState_target_) {
                case EMPTY_QUEUE: // stops the system after emptying push commands from the queue
                    if (sequencer_.available() == 32) stopStartSystem();
                    break;
                case STOP:
                    // sequencer_.manualStop(); // manualStop() may not be called when running.
                    if (motorState_target_ != motorState.STOPPED) stopStartSystem();
                    break;
                case WIRE_CW_AUTO:
                    windings_counter_++; // no of total winding
                    int accelaration_period = 5;
                    int accelaration_divider_initial = 3;
                    int accelaration_divider_last = 8;

                    auto_counter_++; // switch to increase speed
                    if (windings_counter_ < (accelaration_divider_last - accelaration_divider_initial + 2) * accelaration_period) {
                        if (auto_divider_ <= accelaration_divider_last && auto_counter_ > accelaration_period) { //auto_divider_=3,4,5,6,7,8 max:8
                            auto_divider_++;
                            auto_counter_ = 0;
                        }
                    }

                    int decelaration_period = 5;
                    int decelaration_divider_initial = 3;
                    int decelaration_divider_last = 8;

                    if (windings_counter_ > windings_desired_ - (accelaration_divider_last - accelaration_divider_initial + 2) * decelaration_period) {
                        if (auto_divider_ >= accelaration_divider_initial && auto_counter_ > decelaration_period) {
                            auto_divider_--;
                            auto_counter_ = 0;
                        }
                    }

                    motorGuide_period = Integer.valueOf(tvw_seekBarValue_guide.getText().toString());
                    motorWire_period = Integer.valueOf(tvw_seekBarValue_wire.getText().toString());
                    duration_cue_ = Integer.valueOf(etx_cueDuration.getText().toString()); //(*16us base);
                    if (windings_counter_ < windings_desired_) {
                        push_wiring();

                        long millis = System.currentTimeMillis() - starttime;
                        int seconds = (int) (millis / 1000);
                        int minutes = seconds / 60;
                        seconds = seconds % 60;
//                        setTextViewTextOnUi(tvw_timer, String.format("%d:%02d", minutes, seconds));
                    } else {
                        motorState_target_ = motorState.EMPTY_QUEUE;
                    }

        /*
        //                    if (pushExecuted != true){
                            if (coilLayers_counter_ < coilLayers_desired_){
                                motorGuide_period = speed_ > 200 ? 20000 : 9000 * speed_ / 100;
                                motorWire_period = 2500 *speed_/100;
                                duration_cue_ = 62500/speed_*100;
                                push_wiring();
                                if(speed_ > 100) speed_ -= 5;
                            }
        //                        pushExecuted = true;
        //                    }
        */
                    break;
                case WIRE_CW:
                    motorGuide_period = Integer.valueOf(tvw_seekBarValue_guide.getText().toString());
                    motorWire_period = Integer.valueOf(tvw_seekBarValue_wire.getText().toString());
                    duration_cue_ = Integer.valueOf(etx_cueDuration.getText().toString()); //(*16us base);
                    push_wiring();
                    break;
            }


        }

        private void stopStartSystem() throws ConnectionLostException, InterruptedException {
            sequencer_.stop();
            setCurrentState(motorState.STOPPED);
            motorState_target_ = motorState.STOPPED;
            sequencer_.start();
            sequencer_.waitEventType(Sequencer.Event.Type.STOPPED);
        }

        private int coilsPerLayer_counter_, coilLayers_counter_, pushGuide_counter_, pushWire_counter_; // counters
        private int motorGuide_period = 18000, motorWire_period = 10000;
        private void push_wiring() throws ConnectionLostException, InterruptedException {
            // clock rate, pulse width and period durations of cue
            stepper_g_StepCue_.clk = Sequencer.Clock.CLK_250K;//.CLK_250K;//.CLK_62K5;//.CLK_16M;//.CLK_2M; /** 2 MHz (0.5us time-base). */
            stepper_g_StepCue_.pulseWidth = 2;
            stepper_g_StepCue_.period = motorGuide_period;
            stepper_g_DirCue_.value = !direction_g_;
            //setTextViewTextOnUi(tvw_guidePeriod, String.valueOf(stepper_g_StepCue_.period));

            stepper_w_StepCue_.clk = Sequencer.Clock.CLK_16M;
            stepper_w_StepCue_.pulseWidth = 2;
            stepper_w_StepCue_.period = motorWire_period;
            stepper_w_DirCue_.value = direction_w_; //CCW:false, CW:true
            //setTextViewTextOnUi(tvw_wirePeriod, String.valueOf(stepper_w_StepCue_.period));

            // run backlash distance after direction change
            if (direction_g_changed_flag){
                stepper_g_StepCue_.period /= 16;
                sequencer_.push(cue_, duration_cue_ /16);
                direction_g_changed_flag = false;
            } else
                sequencer_.push(cue_, duration_cue_); //Duration must be in the range [2..65536]

            pushGuide_counter_ = pushWire_counter_ ++; //?

            if(coilsPerLayer_counter_++ > coilsPerLayer_desired_)
            {
                direction_g_ = !direction_g_; // change guide stepper direction
                direction_g_changed_flag = true;
                coilsPerLayer_counter_ = 0; // reset coil counter
                coilLayers_counter_++; // increase layer counter
                //push_wiring_backlash(); // pass backlash
            }
            //setTextViewTextOnUi(tvw_guidePush_counter, String.valueOf(pushGuide_counter_));
            //setTextViewTextOnUi(tvw_wirePush_counter, String.valueOf(pushWire_counter_));
            //setTextViewTextOnUi(tvwCoilsPerLayer_counter, String.valueOf(coilsPerLayer_counter_));
            //setTextViewTextOnUi(tvwCoilLayers_counter, String.valueOf(coilLayers_counter_));

            if(!(coilLayers_counter_ < coilLayers_desired_)) motorState_target_ = motorState.EMPTY_QUEUE  ;// sequencer_.pause();

        }


    } /** End class Looper **/

    @Override
    protected IOIOLooper createIOIOLooper() {
        return new Looper();
    }

    private void setCurrentState(motorState state) {
        motorState_current_ = state;
    }

    private void setCurrentState_guide(motorState state) {
        motorState_guide_current_ = state;
    }

    private void setCurrentState_wire(motorState state) {
        motorState_wire_current_ = state;
    }

    private void setCurrentState_coilWiring(motorState state) {
        motorState_coilWiring_current_ = state;
    }



}
