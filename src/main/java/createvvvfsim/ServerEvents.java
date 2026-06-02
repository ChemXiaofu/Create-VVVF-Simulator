package createvvvfsim;
import com.simibubi.create.Create;
import com.simibubi.create.content.trains.GlobalRailwayManager;
import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import com.simibubi.create.content.trains.entity.Train;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Tuple;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Vector3f;
@EventBusSubscriber(modid=CreateVVVFSim.mod_id)
public class ServerEvents{
    private static final int tick_period=5;
    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event){
        MinecraftServer server=event.getServer();
        if(server.getTickCount()%tick_period!=0) return;
        Set<Object> sent_trains=new HashSet<>();
        for(ServerLevel level:server.getAllLevels()){
            GlobalRailwayManager railway=Create.RAILWAYS.sided(level);
            if(railway==null || railway.trains==null) continue;
            for(Train train:railway.trains.values()){
                if(train==null || train.id==null || !sent_trains.add(train.id)) continue;
                List<Tuple<Vector3f,Integer>> carriage_data=new ArrayList<>();
                for(Carriage carriage:train.carriages){
                    CarriageContraptionEntity entity=carriage.anyAvailableEntity();
                    if(entity==null || entity.level()==null) continue;
                    Integer dimension_code=TrainSyncModel.dimension_code.get(entity.level().dimension());
                    if(dimension_code==null) continue;
                    Vector3f pos=entity.position().toVector3f();
                    carriage_data.add(new Tuple<>(pos,dimension_code));
                }
                TrainSyncModel model=new TrainSyncModel(train.id,Math.abs(train.speed)*20.0,carriage_data,server.getTickCount());
                PacketDistributor.sendToAllPlayers(model);
            }
        }
    }
}
