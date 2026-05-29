package soundphysics.remastered;
import net.neoforged.fml.ModList;
public final class SoundPhysicsBridgeManager{
    private static volatile SoundPhysicsBridge bridge=SoundPhysicsBridge.noop();
    public static void init(){
        if(!ModList.get().isLoaded("sound_physics_remastered")){
            bridge=SoundPhysicsBridge.noop();
            return;
        }
        SoundPhysicsBridge candidate=new ReflectiveSoundPhysicsBridge();
        bridge=candidate.isAvailable()?candidate:SoundPhysicsBridge.noop();
    }
    public static SoundPhysicsBridge getBridge(){
        return bridge;
    }
}