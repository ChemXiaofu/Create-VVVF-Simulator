package createvvvfsim;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
public class VVVFSoundEngine{
    public static double settings_amp=1.0;
    public static boolean is_paused=false;
    public static final int sample_rate=44100;
    public static final int buffer_size=1<<12;
    private static final AudioFormat format=new AudioFormat(sample_rate,16,1,true,false);
    private static final Object mix_lock=new Object();
    private static final List<VVVFSoundGen> generators=new ArrayList<>();
    private static final double[] mix_buffer=new double[buffer_size];
    private static final byte[] out_buffer=new byte[buffer_size*2];
    private static final Thread thread=new Thread(VVVFSoundEngine::mixLoop);
    private static SourceDataLine dataline;
    static{
        try{
            dataline=AudioSystem.getSourceDataLine(format);
            dataline.open(format,buffer_size*4);
            dataline.start();
        }
        catch(LineUnavailableException ignored){}
        thread.setDaemon(true);
        thread.start();
    }
    public static void onPause(){
        synchronized(mix_lock){
            is_paused=true;
        }
    }
    public static void offPause(double volume){
        synchronized(mix_lock){
            settings_amp=volume;
            is_paused=false;
            mix_lock.notify();
        }
    }
    private static void mixLoop(){
        while(true){
            synchronized(mix_lock){
                while(generators.isEmpty() || is_paused || settings_amp<1e-3){
                    try{
                        mix_lock.wait();
                    }
                    catch(InterruptedException ignored){
                        Thread.currentThread().interrupt();
                    }
                }
            }
            synchronized(mix_lock){
                Arrays.fill(mix_buffer,0.0);
                for(VVVFSoundGen gen:generators) gen.mixTo(mix_buffer);
            }
            for(int i=0;i<buffer_size;i++){
                double clipped=Math.clamp(mix_buffer[i],-1.0,1.0)*settings_amp;
                short sample=(short)(clipped*Short.MAX_VALUE);
                out_buffer[i*2]=(byte)(sample&0xFF);
                out_buffer[i*2+1]=(byte)((sample>>8)&0xFF);
            }
            dataline.write(out_buffer,0,buffer_size*2);
        }
    }
    public static void addPlayer(VVVFSoundGen gen){
        synchronized(mix_lock){
            generators.add(gen);
            mix_lock.notify();
        }
    }
    public static void removePlayer(VVVFSoundGen gen){
        synchronized(mix_lock){
            generators.removeIf(generator->generator==gen);
        }
    }
}