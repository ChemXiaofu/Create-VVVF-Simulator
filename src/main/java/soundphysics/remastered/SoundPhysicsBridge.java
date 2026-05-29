package soundphysics.remastered;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
public interface SoundPhysicsBridge{
    boolean isAvailable();
    default void onSourcePlay(int sourceId,Vec3 position,SoundSource category,ResourceLocation soundId){}
    default void onSourceUpdate(int sourceId,Vec3 position,SoundSource category,ResourceLocation soundId){}
    default void onSourceStop(int sourceId){}
    static SoundPhysicsBridge noop(){
        return new NoopSoundPhysicsBridge();
    }
}