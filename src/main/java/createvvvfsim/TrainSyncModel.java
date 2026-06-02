package createvvvfsim;
import java.util.*;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Tuple;
import net.minecraft.world.level.Level;
import org.joml.Vector3f;
public record TrainSyncModel(UUID train_id,double speed,List<Tuple<Vector3f,Integer>> carriage_data,
                             int server_tick) implements CustomPacketPayload{
    public static final Type<TrainSyncModel> model_type=new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateVVVFSim.mod_id,"train_sync"));
    public static final Map<ResourceKey<Level>,Integer> dimension_code=new HashMap<>();
    static{
        dimension_code.put(Level.OVERWORLD,0);
        dimension_code.put(Level.NETHER,1);
        dimension_code.put(Level.END,2);
    }
    public static final StreamCodec<RegistryFriendlyByteBuf,Tuple<Vector3f,Integer>> carriage_codec=StreamCodec.composite(
            ByteBufCodecs.VECTOR3F,Tuple::getA,ByteBufCodecs.VAR_INT,Tuple::getB,Tuple::new);
    public static final StreamCodec<RegistryFriendlyByteBuf,TrainSyncModel> stream_codec=StreamCodec.composite(
            UUIDUtil.STREAM_CODEC,TrainSyncModel::train_id,
            ByteBufCodecs.DOUBLE,TrainSyncModel::speed,
            ByteBufCodecs.collection(ArrayList::new,carriage_codec),TrainSyncModel::carriage_data,
            ByteBufCodecs.INT,TrainSyncModel::server_tick,
            TrainSyncModel::new);
    @Override
    public Type<TrainSyncModel> type(){
        return model_type;
    }
}