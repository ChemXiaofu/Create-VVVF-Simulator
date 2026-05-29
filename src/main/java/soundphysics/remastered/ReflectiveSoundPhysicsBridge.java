package soundphysics.remastered;
import java.lang.reflect.Method;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
final class ReflectiveSoundPhysicsBridge implements SoundPhysicsBridge{
    private final Method processSound;
    private final boolean available;
    ReflectiveSoundPhysicsBridge(){
        Method process=null;
        boolean ok=false;
        try{
            Class<?> soundPhysicsClass=Class.forName("com.sonicether.soundphysics.SoundPhysics");
            for(Method method:soundPhysicsClass.getMethods()) {
                if(!"processSound".equals(method.getName())) continue;
                Class<?>[] params=method.getParameterTypes();
                if(params.length==7 && params[0]==int.class && params[1]==double.class && params[2]==double.class
                        && params[3]==double.class && params[4]==SoundSource.class && params[6]==boolean.class){
                    process=method;
                    process.setAccessible(true);
                    ok=true;
                    break;
                }
            }
        }
        catch(Throwable ignored){}
        this.processSound=process;
        this.available=ok;
    }
    @Override
    public boolean isAvailable(){
        return available;
    }
    @Override
    public void onSourcePlay(int sourceId,Vec3 position,SoundSource category,ResourceLocation soundId){
        invokeProcess(sourceId,position,category,soundId,false);
    }
    @Override
    public void onSourceUpdate(int sourceId,Vec3 position,SoundSource category,ResourceLocation soundId){
        invokeProcess(sourceId,position,category,soundId,false);
    }
    private void invokeProcess(int sourceId,Vec3 position,SoundSource category,ResourceLocation soundId,boolean auxOnly){
        if (!available || processSound==null || position==null || category==null || soundId==null) return;
        try{
            processSound.invoke(null,sourceId,position.x,position.y,position.z,category,soundId,auxOnly);
        }
        catch (Throwable ignored){}
    }
}