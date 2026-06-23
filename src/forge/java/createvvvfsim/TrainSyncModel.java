package createvvvfsim;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
public record TrainSyncModel(UUID train_id,double speed){
    public static final SimpleChannel channel=NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(Configs.mod_id,Configs.sync_name),
            ()->Configs.version,Configs.version::equals,Configs.version::equals);
    private static int id=0;
    public static void register(){
        channel.messageBuilder(TrainSyncModel.class,id++,NetworkDirection.PLAY_TO_CLIENT)
                .encoder(TrainSyncModel::encode).decoder(TrainSyncModel::decode)
                .consumerMainThread(ClientEvents::onGetSpeed).add();
    }
    private static void encode(TrainSyncModel model,FriendlyByteBuf buf){
        buf.writeUUID(model.train_id);
        buf.writeDouble(model.speed);
    }
    private static TrainSyncModel decode(FriendlyByteBuf buf){
        return new TrainSyncModel(buf.readUUID(),buf.readDouble());
    }
}
