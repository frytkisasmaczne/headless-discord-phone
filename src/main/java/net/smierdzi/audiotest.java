package net.smierdzi;

import javax.sound.sampled.*;

public class audiotest {

    static SourceDataLine getNamedLine(){
        Mixer.Info[] mixerInfos = AudioSystem.getMixerInfo();
        for (Mixer.Info info: mixerInfos) {
            System.out.println(info);
            if(info.getName().contains("Słuchawki (High Definition Audio Device)")){
                Mixer m = AudioSystem.getMixer(info);
                SourceDataLine sourceDataLine = null;
                try {
                    sourceDataLine = (SourceDataLine)m.getLine(m.getSourceLineInfo()[0]);

                } catch (LineUnavailableException e) {
                    e.printStackTrace();
                }
                System.out.println(m.getSourceLines().length);
                if (sourceDataLine != null){
                    return sourceDataLine;
                }

            }
        }
        return null;
    }

    public static void main(String[] args) {
//        getNamedLine();
        Mixer.Info[] mixerInfos = AudioSystem.getMixerInfo();
        for (Mixer.Info info: mixerInfos) {
            Mixer m = AudioSystem.getMixer(info);

            Line.Info[] lineInfos = m.getSourceLineInfo();
            System.out.println("speaker lines");
            for (Line.Info lineInfo: lineInfos) {
                System.out.println (info.getName()+"---"+lineInfo);
                Line line = null;
                try {
                    line = m.getLine(lineInfo);
                } catch (LineUnavailableException e) {
                    e.printStackTrace();
                }
                System.out.println("\t-----"+line);
            }
            lineInfos = m.getTargetLineInfo();
            System.out.println("mic lines");
            for (Line.Info lineInfo:lineInfos){
                System.out.println (m+"---"+lineInfo);
                Line line = null;
                try {
                    line = m.getLine(lineInfo);

                } catch (LineUnavailableException e) {
                    e.printStackTrace();
                }
                System.out.println("\t-----"+line);
            }
        }
    }
}
