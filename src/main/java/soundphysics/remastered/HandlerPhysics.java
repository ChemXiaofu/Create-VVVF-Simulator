package soundphysics.remastered;
import createvvvfsim.Configs;
import createvvvfsim.EnvData;
import createvvvfsim.TrainData;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import mixin.SPRAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import soundphysics.Handler;
public class HandlerPhysics extends Handler{
    private static final float PHI=1.618033988F;
    private static final float max_distance=256F;
    private static final ResourceLocation sound_id=ResourceLocation.fromNamespaceAndPath(Configs.mod_id,"train_vvvf");
    public static final Map<String,RegisteredClass> classes=new HashMap<>();
    private static Constructor<?> reflected_audio_constructor;
    private static Field config_field;
    public static boolean register(){
        if(!ModList.get().isLoaded("sound_physics_remastered")) return false;
        try{
            Class<?> sound_physics_mod=Class.forName("com.sonicether.soundphysics.SoundPhysicsMod");
            Class<?> reflected_audio=Class.forName("com.sonicether.soundphysics.ReflectedAudio");
            Class<?> raycast_utils=Class.forName("com.sonicether.soundphysics.utils.RaycastUtils");
            Map<String,Method> reflected_audio_methods=new HashMap<>();
            reflected_audio_methods.put("addDirectAirspace",reflected_audio.getMethod("addDirectAirspace",Vec3.class));
            reflected_audio_methods.put("addSharedAirspace",reflected_audio.getMethod("addSharedAirspace",Vec3.class,double.class));
            reflected_audio_methods.put("evaluateSoundPosition",reflected_audio.getMethod("evaluateSoundPosition",Vec3.class,Vec3.class));
            reflected_audio_methods.put("getSharedAirspaces",reflected_audio.getMethod("getSharedAirspaces"));
            classes.put("ReflectedAudio",new RegisteredClass(reflected_audio,reflected_audio_methods));
            Map<String,Method> raycast_utils_methods=new HashMap<>();
            raycast_utils_methods.put("rayCast",raycast_utils.getMethod("rayCast",BlockGetter.class,Vec3.class,Vec3.class,BlockPos.class));
            classes.put("RaycastUtils",new RegisteredClass(raycast_utils,raycast_utils_methods));
            reflected_audio_constructor=reflected_audio.getConstructor(double.class,ResourceLocation.class);
            config_field=sound_physics_mod.getField("CONFIG");
        }
        catch(Throwable ignored){
            return false;
        }
        return true;
    }
    @Override
    public EnvData getEnv(Vec3 train_pos,Vec3 player_pos,Level level){
        EnvData env_data=new EnvData();
        env_data.sourcePos=train_pos;
        env_data.apparentPos=train_pos;
        env_data.gameTime=level.getGameTime();
        Object config=getConfig();
        if(config==null) return env_data;
        try{
            double distance=player_pos.distanceTo(train_pos);
            if(distance>getDouble(config,"maxSoundProcessingDistance")) return env_data;
            float absorption_coeff=(float)(getFloat(config,"blockAbsorption")*3.0);
            double occlusion=SPRAccessor.createvvvfsim$calculateOcclusion(train_pos,player_pos,
                    SoundSource.NEUTRAL,sound_id);
            float direct_cutoff=(float)Math.exp(-occlusion*absorption_coeff);
            float direct_gain=(float)Math.pow(direct_cutoff,0.1);
            float send_gain0=0F,send_gain1=0F,send_gain2=0F,send_gain3=0F;
            float send_cutoff0=1F,send_cutoff1=1F,send_cutoff2=1F,send_cutoff3=1F;
            int num_rays=getInt(config,"environmentEvaluationRayCount");
            int ray_bounces=getInt(config,"environmentEvaluationRayBounces");
            if(num_rays<=0 || ray_bounces<=0) return env_data;
            Object audio_direction=reflected_audio_constructor.newInstance(occlusion,sound_id);
            float[] bounce_reflectivity_ratio=new float[ray_bounces];
            float rcp_total_rays=1F/(num_rays*ray_bounces);
            float g_angle=PHI*(float)Math.PI*2F;
            BlockPos train_block_pos=BlockPos.containing(train_pos);
            Vec3 direct_shared_airspace=SPRAccessor.createvvvfsim$getSharedAirspace(train_pos,player_pos);
            if(direct_shared_airspace!=null) invokeReflectedAudio(audio_direction,"addDirectAirspace",direct_shared_airspace);
            for(int i=0;i<num_rays;i++){
                float fi_n=(float)i/num_rays;
                float longitude=g_angle*i;
                float latitude=(float)Math.asin(fi_n*2F-1F);
                Vec3 ray_dir=new Vec3(Math.cos(latitude)*Math.cos(longitude),
                        Math.cos(latitude)*Math.sin(longitude),Math.sin(latitude));
                Vec3 ray_end=train_pos.add(ray_dir.scale(max_distance));
                BlockHitResult ray_hit=rayCast(level,train_pos,ray_end,train_block_pos);
                if(ray_hit.getType()!=HitResult.Type.BLOCK) continue;
                double ray_length=train_pos.distanceTo(ray_hit.getLocation());
                BlockPos last_hit_block=ray_hit.getBlockPos();
                Vec3 last_hit_pos=ray_hit.getLocation();
                Vec3 last_hit_normal=new Vec3(ray_hit.getDirection().step());
                Vec3 last_ray_dir=ray_dir;
                float total_ray_distance=(float)ray_length;
                Vec3 first_shared_airspace=SPRAccessor.createvvvfsim$getSharedAirspace(ray_hit,player_pos);
                if(first_shared_airspace!=null) invokeReflectedAudio(audio_direction,"addSharedAirspace",
                        first_shared_airspace,(double)total_ray_distance);
                for(int j=0;j<ray_bounces;j++){
                    Vec3 new_ray_dir=SPRAccessor.createvvvfsim$reflect(last_ray_dir,last_hit_normal);
                    Vec3 new_ray_start=last_hit_pos;
                    Vec3 new_ray_end=new_ray_start.add(new_ray_dir.scale(max_distance));
                    BlockHitResult new_ray_hit=rayCast(level,new_ray_start,new_ray_end,last_hit_block);
                    float block_reflectivity=SPRAccessor.createvvvfsim$getBlockReflectivity(last_hit_block);
                    float energy_towards_player=0.25F*(block_reflectivity*0.75F+0.25F);
                    if(new_ray_hit.getType()==HitResult.Type.MISS){
                        total_ray_distance+=last_hit_pos.distanceTo(player_pos);
                    }
                    else{
                        Vec3 new_ray_hit_pos=new_ray_hit.getLocation();
                        double new_ray_length=last_hit_pos.distanceTo(new_ray_hit_pos);
                        bounce_reflectivity_ratio[j]+=block_reflectivity;
                        total_ray_distance+=new_ray_length;
                        last_hit_pos=new_ray_hit_pos;
                        last_hit_normal=new Vec3(new_ray_hit.getDirection().step());
                        last_ray_dir=new_ray_dir;
                        last_hit_block=new_ray_hit.getBlockPos();
                        Vec3 shared_airspace=SPRAccessor.createvvvfsim$getSharedAirspace(new_ray_hit,player_pos);
                        if(shared_airspace!=null) invokeReflectedAudio(audio_direction,"addSharedAirspace",
                                shared_airspace,(double)total_ray_distance);
                    }
                    if(total_ray_distance>=getFloat(config,"reverbAttenuationDistance")){
                        float reflection_delay=Math.max(total_ray_distance,0F)*0.12F*block_reflectivity;
                        float cross0=1F-Mth.clamp(Math.abs(reflection_delay),0F,1F);
                        float cross1=1F-Mth.clamp(Math.abs(reflection_delay-1F),0F,1F);
                        float cross2=1F-Mth.clamp(Math.abs(reflection_delay-2F),0F,1F);
                        float cross3=Mth.clamp(reflection_delay-2F,0F,1F);
                        send_gain0+=cross0*energy_towards_player*6.4F*rcp_total_rays;
                        send_gain1+=cross1*energy_towards_player*12.8F*rcp_total_rays;
                        send_gain2+=cross2*energy_towards_player*12.8F*rcp_total_rays;
                        send_gain3+=cross3*energy_towards_player*12.8F*rcp_total_rays;
                    }
                    if(new_ray_hit.getType()==HitResult.Type.MISS) break;
                }
            }
            for(int i=0;i<bounce_reflectivity_ratio.length;i++) bounce_reflectivity_ratio[i]/=num_rays;
            Vec3 apparent_pos=(Vec3)invokeReflectedAudio(audio_direction,"evaluateSoundPosition",train_pos,player_pos);
            if(apparent_pos!=null) env_data.apparentPos=apparent_pos;
            float shared_airspace=(Integer)invokeReflectedAudio(audio_direction,"getSharedAirspaces")*64F*rcp_total_rays;
            float shared_airspace_weight0=Mth.clamp(shared_airspace/20F,0F,1F);
            float shared_airspace_weight1=Mth.clamp(shared_airspace/15F,0F,1F);
            float shared_airspace_weight2=Mth.clamp(shared_airspace/10F,0F,1F);
            float shared_airspace_weight3=Mth.clamp(shared_airspace/10F,0F,1F);
            send_cutoff0=(float)Math.exp(-occlusion*absorption_coeff)*(1F-shared_airspace_weight0)+shared_airspace_weight0;
            send_cutoff1=(float)Math.exp(-occlusion*absorption_coeff)*(1F-shared_airspace_weight1)+shared_airspace_weight1;
            send_cutoff2=(float)Math.exp(-occlusion*absorption_coeff)*(1F-shared_airspace_weight2)+shared_airspace_weight2;
            send_cutoff3=(float)Math.exp(-occlusion*absorption_coeff)*(1F-shared_airspace_weight3)+shared_airspace_weight3;
            float average_shared_airspace=(shared_airspace_weight0+shared_airspace_weight1+
                    shared_airspace_weight2+shared_airspace_weight3)*0.25F;
            direct_cutoff=Math.max((float)Math.pow(average_shared_airspace,0.5)*0.2F,direct_cutoff);
            direct_gain=(float)Math.pow(direct_cutoff,0.1);
            if(bounce_reflectivity_ratio.length>1) send_gain1*=bounce_reflectivity_ratio[1];
            if(bounce_reflectivity_ratio.length>2) send_gain2*=(float)Math.pow(bounce_reflectivity_ratio[2],3.0);
            if(bounce_reflectivity_ratio.length>3) send_gain3*=(float)Math.pow(bounce_reflectivity_ratio[3],4.0);
            send_gain0=Mth.clamp(send_gain0,0F,1F);
            send_gain1=Mth.clamp(send_gain1,0F,1F);
            send_gain2=Mth.clamp(send_gain2*1.05F-0.05F,0F,1F);
            send_gain3=Mth.clamp(send_gain3*1.05F-0.05F,0F,1F);
            send_gain0*=(float)Math.pow(send_cutoff0,0.1);
            send_gain1*=(float)Math.pow(send_cutoff1,0.1);
            send_gain2*=(float)Math.pow(send_cutoff2,0.1);
            send_gain3*=(float)Math.pow(send_cutoff3,0.1);
            float send_gain_multiplier=1F-Math.min((float)(distance/(Configs.far_distance*getFloat(config,"reverbDistance"))),1F);
            send_gain0*=send_gain_multiplier;
            send_gain1*=send_gain_multiplier;
            send_gain2*=send_gain_multiplier;
            send_gain3*=send_gain_multiplier;
            env_data.available=true;
            env_data.directGain=direct_gain;
            env_data.directCutoff=direct_cutoff;
            env_data.send0.gain=send_gain0;
            env_data.send0.cutoff=send_cutoff0;
            env_data.send1.gain=send_gain1;
            env_data.send1.cutoff=send_cutoff1;
            env_data.send2.gain=send_gain2;
            env_data.send2.cutoff=send_cutoff2;
            env_data.send3.gain=send_gain3;
            env_data.send3.cutoff=send_cutoff3;
            env_data.occlusion=occlusion;
            env_data.sharedAirspace=shared_airspace;
        }
        catch(Throwable ignored){}
        return env_data;
    }

    private static Object getConfig(){
        try{
            return config_field==null?null:config_field.get(null);
        }
        catch(Throwable ignored){
            return null;
        }
    }

    private static Object getConfigEntryValue(Object config,String field_name) throws ReflectiveOperationException{
        Field field=config.getClass().getField(field_name);
        Object entry=field.get(config);
        return entry.getClass().getMethod("get").invoke(entry);
    }

    private static float getFloat(Object config,String field_name) throws ReflectiveOperationException{
        return ((Number)getConfigEntryValue(config,field_name)).floatValue();
    }

    private static double getDouble(Object config,String field_name) throws ReflectiveOperationException{
        return ((Number)getConfigEntryValue(config,field_name)).doubleValue();
    }

    private static int getInt(Object config,String field_name) throws ReflectiveOperationException{
        return ((Number)getConfigEntryValue(config,field_name)).intValue();
    }

    private static Object invokeReflectedAudio(Object instance,String method,Object... args) throws ReflectiveOperationException{
        return classes.get("ReflectedAudio").methods.get(method).invoke(instance,args);
    }

    private static BlockHitResult rayCast(BlockGetter level,Vec3 from,Vec3 to,BlockPos ignore) throws ReflectiveOperationException{
        return (BlockHitResult)classes.get("RaycastUtils").methods.get("rayCast").invoke(null,level,from,to,ignore);
    }
    @Override
    public void handle(double[] mix_buffer,List<TrainData> train_datas){
        for(TrainData train_data:train_datas){
            EnvData env=train_data.env_data;
            //TODO
            train_data.base_gen.mixTo(mix_buffer);
            train_data.vvvf_gen.mixTo(mix_buffer);
            train_data.wind_gen.mixTo(mix_buffer);
        }
    }
}