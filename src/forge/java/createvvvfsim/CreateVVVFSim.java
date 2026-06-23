package createvvvfsim;
import net.minecraftforge.fml.common.Mod;
@Mod(Configs.mod_id)
public class CreateVVVFSim{
    public CreateVVVFSim(){
        TrainSyncModel.register();
    }
}