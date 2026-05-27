package createvvvfsim;
import com.simibubi.create.infrastructure.config.AllConfigs;
import com.simibubi.create.infrastructure.config.CTrains;
import vvvfsimulator.generation.audio.trainsound.Audio;
import vvvfsimulator.generation.audio.trainsound.AudioFilter.CppConvolutionFilter;
import vvvfsimulator.generation.audio.trainsound.AudioResourceManager;
import net.minecraft.world.phys.Vec3;
import java.util.Arrays;
import vvvfsimulator.vvvf.MyMath;
import vvvfsimulator.vvvf.calculation.Common;
import vvvfsimulator.vvvf.model.Struct;
import vvvfsimulator.vvvf.model.Struct.ElectricalParameter.CarrierParameter;
public class VVVFSoundGen{
    //sound config
    private static final int sample_rate=VVVFSoundEngine.sample_rate;
    private static final int buffer_size=VVVFSoundEngine.buffer_size;
    private static final double sample_dt=1.0/sample_rate;
    private static final float max_dist_amp=0.1f;
    private static final float feedback_amp=10f;//balance train sound and vvvf sound
    //train config
    private static final CTrains trains_config=AllConfigs.server().trains;
    private static final float max_speed=trains_config.trainTopSpeed.getF();
    private static final float max_acc=trains_config.trainAcceleration.getF()/20f;
    private static final float max_control_f=85f;
    //convolution config
    private static final int block_size=512;
    //speed filter config
    private static final int speeds_length=5;

    private int speeds_index=0;
    private final float[] speed_samples=new float[speeds_length];
    private float last_speed=0f;

    private volatile float target_f=0f;
    private volatile float target_amp=0f;
    private float current_f=0f;
    private float current_amp=0f;

    private final Struct.PulseControl pulse_control=new Struct.PulseControl();
    private final CarrierParameter.RandomFrequency carrier_random_f=new CarrierParameter.RandomFrequency(0.0,1.0);
    private final CarrierParameter.ConstantFrequency carrier_main_f=new CarrierParameter.ConstantFrequency(240.0);
    private final CarrierParameter carrier_f=new CarrierParameter(carrier_random_f,carrier_main_f);
    private final Struct.ElectricalParameter elect_state=new Struct.ElectricalParameter(false,false,2,pulse_control,carrier_f,null,0.0,0.0);
    private final vvvfsimulator.data.trainaudio.Struct train_config=new vvvfsimulator.data.trainaudio.Struct();
    private final CppConvolutionFilter convolution_filter;
    private final Struct.Domain domain=new Struct.Domain(train_config.motorSpec);
    private final float[] dry_buffer=new float[buffer_size];
    private final float[] wet_buffer=new float[buffer_size];
    public VVVFSoundGen(){
        int[] ir_sample_rate={-1};
        float[] ir=AudioResourceManager.readResourceAudioFileSample(AudioResourceManager.SAMPLE_IR_PATH,ir_sample_rate);
        train_config.impulseResponseSampleRate=sample_rate;
        train_config.impulseResponse=AudioResourceManager.resampleLinear(ir,ir_sample_rate[0],sample_rate);
        train_config.motorVolumeDb=-1;
        convolution_filter=new CppConvolutionFilter(block_size,train_config.impulseResponse);
        domain.electricalState=elect_state;
    }
    public void updateF(Vec3 move){
        float raw_speed=(float)move.length()*20f;
        raw_speed=Math.min(raw_speed,max_speed);
        speed_samples[speeds_index]=raw_speed;
        speeds_index=(speeds_index+1)%speeds_length;
        float[] speeds=Arrays.copyOf(speed_samples,speeds_length);
        Arrays.sort(speeds);
        float med_speed=speeds[speeds_length/2];
        float delta=Math.clamp(med_speed-last_speed,-max_acc,max_acc);
        last_speed+=delta;
        target_f=Math.clamp(last_speed/max_speed,0f,1f)*max_control_f;
    }
    public void updateAmp(float distance_amp){
        target_amp=max_dist_amp*distance_amp;
    }
    public void mixTo(float[] mix_buffer){
        float f_step=(target_f-current_f)/buffer_size;
        float amp_step=(target_amp-current_amp)/buffer_size;
        for(int i=0;i<buffer_size;i++){
            double last_base_f=Math.max(current_f,0f);
            current_f+=f_step;
            current_amp+=amp_step;
            double base_f=Math.max(current_f,0f);
            Struct.PulseControl.Pulse.PulseTypeName pulse_type;
            int pulse_count;

            //Strategy1: Siemens
            Struct.PulseControl.Pulse.PulseAlternative pulse_alt;
            if(base_f<6f){
                pulse_type=Struct.PulseControl.Pulse.PulseTypeName.ASYNC;
                pulse_count=1;
                pulse_alt=Struct.PulseControl.Pulse.PulseAlternative.Default;
                carrier_main_f.value=240.0;
            }//Start
            else if(base_f<21f){
                pulse_type=Struct.PulseControl.Pulse.PulseTypeName.ASYNC;
                pulse_count=1;
                pulse_alt=Struct.PulseControl.Pulse.PulseAlternative.Default;
                carrier_main_f.value=14.0*base_f+156.0;
            }//Async 240Hz-450Hz
            else if(base_f<40f){
                pulse_type=Struct.PulseControl.Pulse.PulseTypeName.ASYNC;
                pulse_count=1;
                pulse_alt=Struct.PulseControl.Pulse.PulseAlternative.Default;
                carrier_main_f.value=450.0;
            }//Async 450Hz
            else if(base_f<45.5f){
                pulse_type=Struct.PulseControl.Pulse.PulseTypeName.CHM;
                pulse_count=9;
                pulse_alt=Struct.PulseControl.Pulse.PulseAlternative.Alt6;
            }//CHM9 Alt6
            else if(base_f<48f){
                pulse_type=Struct.PulseControl.Pulse.PulseTypeName.CHM;
                pulse_count=9;
                pulse_alt=Struct.PulseControl.Pulse.PulseAlternative.Default;
            }//CHM9 Defalut
            else if(base_f<50.5f){
                pulse_type=Struct.PulseControl.Pulse.PulseTypeName.CHM;
                pulse_count=7;
                pulse_alt=Struct.PulseControl.Pulse.PulseAlternative.Alt3;
            }//CHM7 Alt3
            else if(base_f<54f){
                pulse_type=Struct.PulseControl.Pulse.PulseTypeName.CHM;
                pulse_count=7;
                pulse_alt=Struct.PulseControl.Pulse.PulseAlternative.Default;
            }//CHM7 Default
            else if(base_f<62.5f){
                pulse_type=Struct.PulseControl.Pulse.PulseTypeName.CHM;
                pulse_count=7;
                pulse_alt=Struct.PulseControl.Pulse.PulseAlternative.Alt3;
            }//CHM7 Alt3
            else if(base_f<64f){
                pulse_type=Struct.PulseControl.Pulse.PulseTypeName.CHM;
                pulse_count=5;
                pulse_alt=Struct.PulseControl.Pulse.PulseAlternative.Alt3;
            }//CHM5 Alt3
            else{
                pulse_type=Struct.PulseControl.Pulse.PulseTypeName.SYNC;
                pulse_count=1;
                pulse_alt=Struct.PulseControl.Pulse.PulseAlternative.Default;
            }//Square
            elect_state.baseWaveFrequency=base_f;
            elect_state.baseWaveAmplitude=base_f/65.0;
            pulse_control.pulseMode.alternative=pulse_alt;
            /*
            //Strategy2: Alstom
            double base_wave_amp=0.0196*base_f;
            if(base_f<15f){
                pulse_type=Struct.PulseControl.Pulse.PulseTypeName.ASYNC;
                pulse_count=1;
                carrier_main_f.value=300;
            }//Async 300Hz
            else if(base_f<27f){
                pulse_type=Struct.PulseControl.Pulse.PulseTypeName.ASYNC;
                pulse_count=1;
                carrier_main_f.value=400;
            }//Async 400Hz
            else if(base_f<42f){
                pulse_type=Struct.PulseControl.Pulse.PulseTypeName.SYNC;
                pulse_count=21;
            }//Sync 21
            else if(base_f<60f){
                pulse_type=Struct.PulseControl.Pulse.PulseTypeName.SYNC;
                pulse_count=15;
                base_wave_amp=0.04*base_f-0.88;
            }//Sync 15
            else{
                pulse_type=Struct.PulseControl.Pulse.PulseTypeName.SYNC;
                pulse_count=11;
                base_wave_amp=0.41*base_f-23.0;
            }//Sync 11
            elect_state.baseWaveFrequency=base_f;
            elect_state.baseWaveAmplitude=base_wave_amp;
            domain.setBaseWaveAngleFrequency(MyMath.M_2PI*base_f);
            */
            elect_state.isZeroOutput=base_f<=0.0;
            pulse_control.pulseMode.pulseType=pulse_type;
            pulse_control.pulseMode.pulseCount=pulse_count;
            if(last_base_f>1e-9 && base_f>1e-9) domain.multiplyBaseWaveTime(last_base_f/base_f);
            else if(base_f<=1e-9) domain.setBaseWaveTime(0.0);
            domain.addTime(sample_dt);
            domain.addBaseWaveTime(sample_dt);
            domain.getCarrierInstance().time+=sample_dt;
            Struct.PhaseState state=Common.getCalculator(2,pulse_type).calculate(domain,0.0);
            domain.motor.process(domain.getDeltaTime(),MyMath.M_2PI*base_f,state);
            double train_sound=Audio.calculateTrainSoundFromCurrentState(domain,train_config);
            dry_buffer[i]=(float)train_sound*current_amp;
        }
        convolution_filter.process(dry_buffer,0,wet_buffer,0,buffer_size);
        for(int i=0;i<buffer_size;i++) mix_buffer[i]+=wet_buffer[i];
    }
}