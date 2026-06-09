package createvvvfsim;
import com.simibubi.create.Create;
import com.simibubi.create.content.trains.entity.Train;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
@EventBusSubscriber(modid=Configs.mod_id)
public class ServerEvents{
    private static final int sync_period=Configs.sync_period;
    private static int sync_current=sync_period;
    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event){
        if(sync_current==sync_period){
            sync_current=0;
            for(Train train:Create.RAILWAYS.trains.values()){
                TrainSyncModel model=new TrainSyncModel(train.id,Math.abs(train.speed)*20.0);
                PacketDistributor.sendToAllPlayers(model);
            }
        }
        sync_current++;
    }
}