package createvvvfsim;
import com.simibubi.create.Create;
import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.Carriage.DimensionalCarriageEntity;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import com.simibubi.create.content.trains.entity.Train;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Tuple;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
public class TrainStatus{
    private static final double max_distance=Configs.max_distance;
    public static final List<TrainStatus> all_trains=new ArrayList<>();
    public final Train train;
    public final SoundGen gen=new SoundGen();
    public final VVVFSoundGen vvvf_gen=new VVVFSoundGen();
    private final FSmoother f_smoother=new FSmoother();
    private boolean is_move=false,is_last_move=false;
    public static final Object train_lock=new Object();
    private static final Logger LOGGER=LoggerFactory.getLogger("createvvvfsim");
    private TrainStatus(Train train){
        this.train=train;
    }
    public static void addTrain(Train train){
        TrainStatus status=new TrainStatus(train);
        synchronized(train_lock){
            all_trains.add(status);
        }
    }
    public static void tick(Level level,Player player){
        synchronized(train_lock){
            all_trains.removeIf(status->status.isInvalid(level));
            for(TrainStatus status:all_trains){
                Vec3 player_pos=player.position();
                List<Double> speeds=new ArrayList<>();
                double total_factor=0.0;
                for(Carriage carriage:status.train.carriages){
                    Tuple<Vec3,Vec3> now_last_pos=status.carriageNowLastPos(carriage);
                    if(now_last_pos==null) continue;
                    double move=now_last_pos.getA().distanceTo(now_last_pos.getB());
                    speeds.add(move*20.0);
                    if(!isCarriageInDimension(carriage,level)) continue;
                    double distance=now_last_pos.getA().distanceTo(player_pos);
                    total_factor+=Math.max(0.0,1.0-distance/max_distance);
                }
                speeds.sort(Double::compare);
                double carriage_speed=speeds.isEmpty()?0.0:speeds.get(speeds.size()/2);
                status.is_move=carriage_speed>1e-2;
                if(status.is_move && !status.is_last_move){
                    level.playLocalSound(player,SoundEvents.LAVA_EXTINGUISH,
                            SoundSource.NEUTRAL,0.75f*(float)total_factor,1f);
                    level.playLocalSound(player,SoundEvents.WOODEN_TRAPDOOR_CLOSE,
                            SoundSource.NEUTRAL,0.6f*(float)total_factor,1.5f);
                }

                double smoothed=status.f_smoother.smoothF(carriage_speed);
                status.gen.setAmp(total_factor);
                status.vvvf_gen.setAmp(total_factor);
                status.vvvf_gen.setF(smoothed);
                status.is_last_move=status.is_move;
            }
        }
    }
    private Tuple<Vec3,Vec3> carriageNowLastPos(Carriage carriage){
        CarriageContraptionEntity entity=carriage.anyAvailableEntity();
        if(entity==null) return null;
        return new Tuple<>(entity.position(),entity.getPrevPositionVec());
    }
    private static boolean isCarriageInDimension(Carriage carriage,Level level){
        DimensionalCarriageEntity dce=carriage.getDimensionalIfPresent(level.dimension());
        return dce!=null;
    }
    private boolean isInvalid(Level level){
        if(level==null) return true;
        if(this.train.id==null) return true;
        if(this.train.carriages==null || this.train.carriages.isEmpty()) return true;
        var railways=Create.RAILWAYS.sided(level);
        if(railways==null || railways.trains==null) return true;
        return !railways.trains.containsKey(this.train.id);
    }
    public static void fromServer(TrainSyncModel model,IPayloadContext context){
        UUID id=model.train_id();
        double speed=model.speed();
        List<Tuple<Vector3f,Integer>> carriage_data=model.carriage_data();
        int tick=model.server_tick();
        LOGGER.info("[server]: train_id: {}, speed: {}, tick: {}",id,speed,tick);
        for(Tuple<Vector3f,Integer> carriage:carriage_data){
            Vector3f pos=carriage.getA();
            switch(carriage.getB()){
                case 0:
                    LOGGER.info("pos: ({},{},{}) at overworld",pos.x,pos.y,pos.z);
                    break;
                case 1:
                    LOGGER.info("pos: ({},{},{}) at nether",pos.x,pos.y,pos.z);
                    break;
                case 2:
                    LOGGER.info("pos: ({},{},{}) at end",pos.x,pos.y,pos.z);
                    break;
            }
        }
    }
}