package soundphysics.remastered;
import createvvvfsim.Configs;
import createvvvfsim.EnvData;
import createvvvfsim.TrainData;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import mixin.ISPRAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import soundphysics.Handler;
public class HandlerPhysics extends Handler{
    private static final int buffer_size=Configs.buffer_size;
    private static final double far_distance=Configs.far_distance;
    private static final float max_distance=256f;
    private static final float angle=(float)(Math.PI*(Math.sqrt(5f)+1f));
    private static ResourceLocation sound_id;
    private static Constructor<?> constructor;
    private static Method add_direct;
    private static Method add_shared;
    private static Method get_shared;
    private static Method eval_pos;
    private static Method ray_cast;
    private static double max_process_distance;
    private static float block_absorption;
    private static float decrease_distance;
    private static float reverb_distance;
    private static float d_rays;
    private static int ray_count;
    private static int ray_bounces;
    private static int head_ptr=0;
    private static boolean is_init=false;
    private static final int tail_size=buffer_size*64;
    private static final double[] train_buffer=new double[buffer_size];
    private static final double[][] tail_buffers=new double[4][tail_size];
    private static final int[] send_delays={
            (int)(Configs.sample_rate*0.0297),
            (int)(Configs.sample_rate*0.0411),
            (int)(Configs.sample_rate*0.0677),
            (int)(Configs.sample_rate*0.0973)};
    private static final double[] send_feedbacks={
            feedback(0.0297,0.15),
            feedback(0.0411,0.55),
            feedback(0.0677,1.68),
            feedback(0.0973,4.142)};
    private static final double[] send_filters=new double[4];
    private static final double[] feedback_filters=new double[4];
    private static double direct_filter=0.0;
    private static double feedback_filter=0.0;
    private static Object getValue(Object config,String field_name)
            throws ReflectiveOperationException{
        Field field=config.getClass().getField(field_name);
        Object entry=field.get(config);
        return entry.getClass().getMethod("get").invoke(entry);
    }
    private static Object invoke(Method method,Object instance,Object... args)
            throws InvocationTargetException,IllegalAccessException{
        return method.invoke(instance,args);
    }
    private static double feedback(double delay,double decay){
        return Math.pow(10.0,-3.0*delay/decay);
    }
    private static double lowpass(double input,double cutoff,double state){
        double alpha=Math.clamp(cutoff,0.02,1.0);
        return state+(input-state)*alpha;
    }
    private static int wrap(int value){
        value%=tail_size;
        return value<0?value+tail_size:value;
    }
    public static boolean register(){
        if(!ModList.get().isLoaded("sound_physics_remastered")) return false;
        try{
            Class<?> reflected_audio=Class.forName("com.sonicether.soundphysics.ReflectedAudio");
            constructor=reflected_audio.getConstructor(double.class,ResourceLocation.class);
            add_direct=reflected_audio.getMethod("addDirectAirspace",Vec3.class);
            add_shared=reflected_audio.getMethod("addSharedAirspace",Vec3.class,double.class);
            get_shared=reflected_audio.getMethod("getSharedAirspaces");
            eval_pos=reflected_audio.getMethod("evaluateSoundPosition",Vec3.class,Vec3.class);
            Class<?> raycast_utils=Class.forName("com.sonicether.soundphysics.utils.RaycastUtils");
            ray_cast=raycast_utils.getMethod("rayCast",BlockGetter.class,Vec3.class,Vec3.class,BlockPos.class);
        }
        catch(Throwable ignored){
            return false;
        }
        return true;
    }
    private static void initConsts() throws ReflectiveOperationException{
        Class<?> sound_physics_mod=Class.forName("com.sonicether.soundphysics.SoundPhysicsMod");
        Object config=sound_physics_mod.getField("CONFIG").get(null);
        max_process_distance=((Number)getValue(config,"maxSoundProcessingDistance")).doubleValue();
        block_absorption=((Number)getValue(config,"blockAbsorption")).floatValue();
        decrease_distance=((Number)getValue(config,"reverbAttenuationDistance")).floatValue();
        reverb_distance=((Number)getValue(config,"reverbDistance")).floatValue();
        ray_count=((Number)getValue(config,"environmentEvaluationRayCount")).intValue();
        ray_bounces=((Number)getValue(config,"environmentEvaluationRayBounces")).intValue();
        sound_id=ResourceLocation.fromNamespaceAndPath(Configs.mod_id,Configs.spr_sound_name);
        d_rays=1f/(ray_count*ray_bounces);
    }
    @Override
    public EnvData getEnv(Vec3 train_pos,Vec3 player_pos,Level level){
        try{
            if(!is_init){
                initConsts();
                is_init=true;
            }
        }
        catch(ReflectiveOperationException e){
            return null;
        }
        EnvData env_data=new EnvData();
        env_data.source_pos=env_data.apparent_pos=train_pos;
        double distance=train_pos.distanceTo(player_pos);
        if(distance>max_process_distance) return null;
        try{
            double occlusion=ISPRAccessor.calculateOcclusion(train_pos,player_pos,SoundSource.NEUTRAL,sound_id);
            Object audio_direction=constructor.newInstance(occlusion,sound_id);
            Vec3 airspace=ISPRAccessor.getSharedAirspace(train_pos,player_pos);
            float[] reflect_ratio=new float[ray_bounces];
            if(airspace!=null) invoke(add_direct,audio_direction,airspace);
            float[] send_gains=new float[4],send_cutoffs=new float[4];
            for(int i=0;i<ray_count;i++){
                float latitude=(float)Math.asin(2f*i/ray_count-1f),longitude=angle*i;
                Vec3 ray_dir=new Vec3(Math.cos(latitude)*Math.cos(longitude),
                        Math.cos(latitude)*Math.sin(longitude),Math.sin(latitude));
                Vec3 ray_end=train_pos.add(ray_dir.scale(max_distance));
                BlockHitResult ray_hit=(BlockHitResult)invoke(
                        ray_cast,null,level,train_pos,ray_end,BlockPos.containing(train_pos));
                if(ray_hit.getType()!=HitResult.Type.BLOCK) continue;
                double total_ray_distance=(float)train_pos.distanceTo(ray_hit.getLocation());
                BlockPos last_hit_block=ray_hit.getBlockPos();
                Vec3 last_ray_dir=ray_dir;
                Vec3 last_hit_pos=ray_hit.getLocation();
                Vec3 last_hit_normal=new Vec3(ray_hit.getDirection().step());
                Vec3 first_shared_airspace=ISPRAccessor.getSharedAirspace(ray_hit,player_pos);
                if(first_shared_airspace!=null)
                    invoke(add_shared,audio_direction,first_shared_airspace,total_ray_distance);
                for(int j=0;j<ray_bounces;j++){
                    Vec3 new_ray_dir=ISPRAccessor.reflect(last_ray_dir,last_hit_normal);
                    Vec3 new_ray_start=last_hit_pos;
                    Vec3 new_ray_end=new_ray_start.add(new_ray_dir.scale(max_distance));
                    BlockHitResult new_ray_hit=(BlockHitResult)invoke(
                            ray_cast,null,level,new_ray_start,new_ray_end,last_hit_block);
                    float block_reflectivity=ISPRAccessor.getBlockReflectivity(last_hit_block);
                    float energy=0.25f*(block_reflectivity*0.75f+0.25f);
                    if(new_ray_hit.getType()==HitResult.Type.MISS)
                        total_ray_distance+=last_hit_pos.distanceTo(player_pos);
                    else{
                        Vec3 new_ray_hit_pos=new_ray_hit.getLocation();
                        double new_ray_length=last_hit_pos.distanceTo(new_ray_hit_pos);
                        reflect_ratio[j]+=block_reflectivity;
                        total_ray_distance+=new_ray_length;
                        last_hit_pos=new_ray_hit_pos;
                        last_hit_normal=new Vec3(new_ray_hit.getDirection().step());
                        last_ray_dir=new_ray_dir;
                        last_hit_block=new_ray_hit.getBlockPos();
                        Vec3 shared_airspace=ISPRAccessor.getSharedAirspace(new_ray_hit,player_pos);
                        if(shared_airspace!=null)
                            invoke(add_shared,audio_direction,shared_airspace,total_ray_distance);
                    }
                    if(total_ray_distance>decrease_distance){
                        float reflection_delay=(float)Math.max(total_ray_distance,0f)*0.12f*block_reflectivity;
                        for(int k=0;k<4;k++){
                            float value=k==3?reflection_delay-2f:1f-Math.abs(reflection_delay-k);
                            float cross=Math.clamp(value,0f,1f),amp=k==0?6.4f:12.8f;
                            send_gains[k]+=cross*energy*amp*d_rays;
                        }
                    }
                    if(new_ray_hit.getType()==HitResult.Type.MISS) break;
                }
            }
            for(int i=0;i<ray_bounces;i++) reflect_ratio[i]/=ray_count;
            Vec3 apparent_pos=(Vec3)invoke(eval_pos,audio_direction,train_pos,player_pos);
            if(apparent_pos!=null) env_data.apparent_pos=apparent_pos;
            float shared_space=(Integer)invoke(get_shared,audio_direction)*64f*d_rays;
            float avg_space=0f,direct_cutoff=(float)Math.exp(-occlusion*block_absorption*3f);
            float send_gain_mul=1f-Math.min((float)(distance/(far_distance*reverb_distance)),1f);
            float[] factor={20f,15f,10f,10f},gain_fac={
                    1f,ray_bounces>1?reflect_ratio[1]:1f,
                    ray_bounces>2?(float)Math.pow(reflect_ratio[2],3.0):1f,
                    ray_bounces>3?(float)Math.pow(reflect_ratio[3],4.0):1f};
            for(int i=0;i<4;i++){
                float space_weight=Math.clamp(shared_space/factor[i],0f,1f);
                avg_space+=space_weight;
                send_cutoffs[i]=direct_cutoff*(1f-space_weight)+space_weight;
                if(reflect_ratio.length>i) send_gains[i]*=gain_fac[i];
                send_gains[i]=Math.clamp(i<2?send_gains[i]:(send_gains[i]*1.05f-0.05f),0f,1f);
                send_gains[i]*=(float)Math.pow(send_cutoffs[i],0.1)*send_gain_mul;
                env_data.sends[i].gain=send_gains[i];
                env_data.sends[i].cutoff=send_cutoffs[i];
            }
            avg_space*=0.25f;
            direct_cutoff=Math.max((float)Math.pow(avg_space,0.5)*0.2f,direct_cutoff);
            env_data.direct_gain=(float)Math.pow(direct_cutoff,0.1);
            env_data.direct_cutoff=direct_cutoff;
            env_data.occlusion=occlusion;
            env_data.shared_space=shared_space;
        }
        catch(Throwable e){
            System.out.println(e.getMessage());
        }
        return env_data;
    }
    @Override
    public void handle(double[] mix_buffer,List<TrainData> train_datas){
        for(TrainData train_data:train_datas){
            EnvData env=train_data.env_data;
            if(env==null){
                train_data.base_gen.mixTo(mix_buffer);
                train_data.vvvf_gen.mixTo(mix_buffer);
                train_data.wind_gen.mixTo(mix_buffer);
            }
            else{
                Arrays.fill(train_buffer,0.0);
                train_data.base_gen.mixTo(train_buffer);
                train_data.vvvf_gen.mixTo(train_buffer);
                train_data.wind_gen.mixTo(train_buffer);
                for(int i=0;i<buffer_size;i++){
                    double sample=train_buffer[i];
                    direct_filter=lowpass(sample,env.direct_cutoff,direct_filter);
                    mix_buffer[i]+=direct_filter*env.direct_gain;
                    for(int j=0;j<4;j++){
                        EnvData.ReverbSend send=env.sends[j];
                        send_filters[j]=lowpass(sample*send.gain,send.cutoff,send_filters[j]);
                        tail_buffers[j][wrap(head_ptr+i+send_delays[j])]+=send_filters[j];
                    }
                }
            }
        }
        for(int i=0;i<buffer_size;i++){
            double wet=0.0;
            for(int j=0;j<4;j++){
                double current=tail_buffers[j][head_ptr];
                tail_buffers[j][head_ptr]=0.0;
                feedback_filters[j]=lowpass(current,0.82,feedback_filters[j]);
                wet+=feedback_filters[j];
                tail_buffers[j][wrap(head_ptr+send_delays[j])]+=feedback_filters[j]*send_feedbacks[j];
            }
            feedback_filter=lowpass(wet,0.92,feedback_filter);
            mix_buffer[i]+=feedback_filter;
            head_ptr++;
            if(head_ptr==tail_size) head_ptr=0;
        }
    }
}