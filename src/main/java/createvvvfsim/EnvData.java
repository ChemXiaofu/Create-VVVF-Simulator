package createvvvfsim;
import net.minecraft.world.phys.Vec3;
public class EnvData{
    public double direct_gain=1.0;
    public double direct_cutoff=1.0;
    public ReverbSend[] sends=new ReverbSend[4];
    public double occlusion=0.0;
    public double shared_space=0.0;
    public Vec3 source_pos=Vec3.ZERO;
    public Vec3 apparent_pos=Vec3.ZERO;
    public EnvData(){
        for(int i=0;i<4;i++) sends[i]=new ReverbSend();
    }
    public class ReverbSend{
        public double gain=0.0;
        public double cutoff=1.0;
    }
}