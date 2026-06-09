package createvvvfsim;
import net.minecraft.world.phys.Vec3;
public class EnvData{
    public boolean available=false;
    public double directGain=1.0;
    public double directCutoff=1.0;
    public ReverbSend send0=new ReverbSend();
    public ReverbSend send1=new ReverbSend();
    public ReverbSend send2=new ReverbSend();
    public ReverbSend send3=new ReverbSend();
    public double occlusion=0.0;
    public double sharedAirspace=0.0;
    public Vec3 sourcePos=Vec3.ZERO;
    public Vec3 apparentPos=Vec3.ZERO;
    public long gameTime=0L;
    public class ReverbSend{
        public double gain=0.0;
        public double cutoff=1.0;
    }
}