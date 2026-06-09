package createvvvfsim;
import com.simibubi.create.content.trains.entity.Train;
import genengine.BaseSoundGen;
import genengine.VVVFSoundGen;
import genengine.WindSoundGen;
public class TrainData{
    public final Train train;
    public final BaseSoundGen base_gen=new BaseSoundGen();
    public final VVVFSoundGen vvvf_gen=new VVVFSoundGen();
    public final WindSoundGen wind_gen=new WindSoundGen();
    public final FSmoother f_smoother=new FSmoother();
    public volatile EnvData env_data=new EnvData();
    public boolean is_reloaded=false,is_move=false,is_last_move=false;
    public TrainData(Train train){
        this.train=train;
    }
    public void set(double speed,double near_factor,double far_factor){
        double smoothed=f_smoother.smoothF(speed);
        base_gen.setAmp(near_factor);
        vvvf_gen.setAmp(near_factor);
        wind_gen.setAmp(far_factor);
        vvvf_gen.setF(smoothed);
        wind_gen.setF(smoothed);
    }
}