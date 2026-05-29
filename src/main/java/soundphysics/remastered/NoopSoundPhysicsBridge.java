package soundphysics.remastered;
final class NoopSoundPhysicsBridge implements SoundPhysicsBridge{
    @Override
    public boolean isAvailable(){
        return false;
    }
}