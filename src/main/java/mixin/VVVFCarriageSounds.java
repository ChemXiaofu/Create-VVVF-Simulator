package mixin;
import com.simibubi.create.AllSoundEvents;
import createvvvfsim.SoundGen;
import createvvvfsim.VVVFSoundEngine;
import createvvvfsim.VVVFSoundGen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import com.simibubi.create.content.trains.entity.Carriage.DimensionalCarriageEntity;
import com.simibubi.create.content.trains.entity.CarriageSounds;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value={CarriageSounds.class},remap=false,priority=1027)
public class VVVFCarriageSounds{
    private final SoundGen gen=new SoundGen();
    private final VVVFSoundGen vvvf_gen=new VVVFSoundGen();
    private static final double max_distance=64;
    private boolean is_move=false,is_last_move=false,
            is_near=false,is_last_near=false,
            is_play=false,is_last_play=false;
    private static final String lcr="Lcom/simibubi/create/",
            lcrce="tick("+lcr+"content/trains/entity/Carriage$DimensionalCarriageEntity;)V",
            lcrlp=lcr+"content/trains/entity/CarriageSounds$LoopingSound;",
            lmc="Lnet/minecraft/client/",
            lwl="Lnet/minecraft/world/",
            lsd="Lnet/minecraft/sounds/";
    @Redirect(method={lcrce},
        at=@At(target=lcr+"AllSoundEvents$SoundEntry;playAt("+lwl+"level/Level;"+lwl+"phys/Vec3;FFZ)V",value="INVOKE"),require=0,expect=0)
    private void noPlayAt(AllSoundEvents.SoundEntry self,Level world,Vec3 pos,float volume,float pitch,boolean fade){}
    @Redirect(method={lcrce},
        at=@At(target=lwl+"level/Level;playLocalSound(DDD"+lsd+"SoundEvent;"+lsd+"SoundSource;FFZ)V",value="INVOKE"),require=0,expect=0)
    private void noPlayLocalSound(Level self,double x,double y,double z,SoundEvent event,SoundSource source,float volume,float pitch,boolean distanceDelay){}
    @Redirect(method={"playIfMissing("+lmc+"Minecraft;"+lcrlp+lsd+"SoundEvent;Z)"+lcrlp},
        at=@At(target=lmc+"sounds/SoundManager;play("+lmc+"resources/sounds/SoundInstance;)V",value="INVOKE"),require=0,expect=0)
    private void noPlay(SoundManager self,SoundInstance sound){}
    @Inject(method={lcrce},at={@At("RETURN")},remap=false)
    private void tickUpdate(DimensionalCarriageEntity dce,CallbackInfo ci){
        CarriageContraptionEntity entity=dce.entity.get();
        LocalPlayer player=Minecraft.getInstance().player;
        if(entity!=null && player!=null){
            Vec3 train_pos=entity.position(),move=train_pos.subtract(entity.getPrevPositionVec());
            double distance=train_pos.distanceTo(player.position());
            is_move=move.length()>1e-2;
            is_near=distance<max_distance;
            is_play=is_move && is_near;
            if(is_near && !is_last_near) VVVFSoundEngine.addPlayer(gen);
            else if(!is_near && is_last_near) VVVFSoundEngine.removePlayer(gen);
            gen.updateAmp(1.0-distance/max_distance);
            if(is_play && !is_last_play){
                if(!is_last_move){
                    entity.level().playLocalSound(entity,SoundEvents.LAVA_EXTINGUISH,SoundSource.NEUTRAL,1f,1f);
                    entity.level().playLocalSound(entity,SoundEvents.WOODEN_TRAPDOOR_CLOSE, SoundSource.NEUTRAL,0.8f,1.5f);
                }
                VVVFSoundEngine.addPlayer(vvvf_gen);
            }
            else if(!is_play && is_last_play) VVVFSoundEngine.removePlayer(vvvf_gen);
            vvvf_gen.updateAmp(1.0-distance/max_distance);
            vvvf_gen.updateF(move);
        }
        else is_move=is_near=is_play=false;
        is_last_move=is_move;
        is_last_near=is_near;
        is_last_play=is_play;
    }
}