package net.smierdzi;

/*
 * Copyright 2015-2020 Austin Keener, Michael Ritter, Florian Spieß, and the JDA contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import com.pi4j.io.gpio.*;
import com.pi4j.io.gpio.event.GpioPinListenerDigital;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.audio.AudioReceiveHandler;
import net.dv8tion.jda.api.audio.AudioSendHandler;
import net.dv8tion.jda.api.audio.CombinedAudio;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceLeaveEvent;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.events.message.priv.PrivateMessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.managers.AudioManager;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import org.jetbrains.annotations.NotNull;

import javax.security.auth.login.LoginException;
import javax.sound.sampled.*;
import java.nio.ByteBuffer;
import java.util.EnumSet;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class dbb extends ListenerAdapter
{
    public static final String fourdotvcid = "752987166337925174";
    public static final String fourdottextchannelid = "752987166337925173";

    public static final String nggyu = "396307639669358598";
    public static final String legum = "397376055062560768";
    public static final String docreg = "765284452679024660";
    public static final String sergiusz = "811159413741584386";


    public enum s {idle,ringing,connected}

    static EchoHandler handler;
    static JDA jda;
    static AudioManager audioManager;

    public static VoiceChannel fourdotvc;
    public static TextChannel fourdottextchannel;

    public static final GpioController gpio = GpioFactory.getInstance();

    public static String token;

    public static s state = s.idle;

    public static void main(String[] args){
        if (args.length == 0)
        {
            System.err.println("Unable to start without token!");
            System.exit(1);
        }
        token = args[0];

        System.out.print("internet?...");
        try {
            init();
        } catch (Exception e) {
            System.out.println("nonexistent.");
            System.exit(1);
        }

        initgpio();

        System.out.println("ready");
    }

    public static void init() throws LoginException, InterruptedException {
        EnumSet<GatewayIntent> intents = EnumSet.of(
                GatewayIntent.GUILD_MESSAGES,
                GatewayIntent.DIRECT_MESSAGES,
                GatewayIntent.GUILD_VOICE_STATES
        );

        jda = JDABuilder.createDefault(token, intents)
                .addEventListeners(new dbb())
                .setActivity(Activity.listening("nikogo"))
                .setStatus(OnlineStatus.ONLINE)
                .enableCache(CacheFlag.VOICE_STATE)
                .build();

        jda.awaitReady();

        fourdotvc = jda.getVoiceChannelById(fourdotvcid);
        fourdottextchannel = jda.getTextChannelById(fourdottextchannelid);
    }

    //button for disconnecting, the one under the earpiece
    public static final GpioPinDigitalInput input00 = gpio.provisionDigitalInputPin(RaspiPin.GPIO_00);

    public static final GpioPinDigitalOutput input01 = gpio.provisionDigitalOutputPin(RaspiPin.GPIO_02);//buzzer for signaling incoming connnection
    public static final GpioPinDigitalInput input02 = gpio.provisionDigitalInputPin(RaspiPin.GPIO_03);//physical button number 1 for calling legum
    public static final GpioPinDigitalInput input03 = gpio.provisionDigitalInputPin(RaspiPin.GPIO_04);//2 for calling nggyu
    public static final GpioPinDigitalInput input04 = gpio.provisionDigitalInputPin(RaspiPin.GPIO_05);//3 for calling docreg
    public static final GpioPinDigitalInput input05 = gpio.provisionDigitalInputPin(RaspiPin.GPIO_29);//4 for calling sergiusz
//    public static final GpioPinDigitalInput input06 = gpio.provisionDigitalInputPin(RaspiPin.GPIO_25);//5
//    public static final GpioPinDigitalInput input07 = gpio.provisionDigitalInputPin(RaspiPin.GPIO_27);//6

    public static void initgpio() {
        input00.addListener((GpioPinListenerDigital) event -> {
            if(event.getState().isHigh()) disconnectvc();
            else connectvc();
        });
        input02.addListener((GpioPinListenerDigital) event -> {
            if(event.getState().isLow()) callPerson(legum);
        });
        input03.addListener((GpioPinListenerDigital) event -> {
            if(event.getState().isLow()) callPerson(nggyu);
        });
        input04.addListener((GpioPinListenerDigital) event -> {
            if(event.getState().isLow()) callPerson(docreg);
        });
        input05.addListener((GpioPinListenerDigital) event -> {
            if(event.getState().isLow()) callPerson(sergiusz);
        });
    }

    public static void callPerson(String userID){
        String name = "halo ";
        try{
            User user = jda.retrieveUserById(userID).complete();
            name+=user.getAsMention();
        }catch(NullPointerException ignored){}
        System.out.println(name+" "+userID);
        fourdottextchannel.sendMessage(name).queue();
    }

    @Override
    public void onPrivateMessageReceived(PrivateMessageReceivedEvent event){
        if(event.getMessage().getContentRaw().toLowerCase().contains("halo") && state==s.idle){
            System.out.println(event.getAuthor().getName()+"is calling");
            input01.setState(PinState.HIGH);
            state = s.ringing;
            try {
                for (int i=0;i<30;i++){
                    input01.setState(PinState.HIGH);
                    Thread.sleep(100);
                    if (state!=s.ringing) break;
                    input01.setState(PinState.LOW);
                    Thread.sleep(100);
                    if (state!=s.ringing) break;
                    input01.setState(PinState.HIGH);
                    Thread.sleep(100);
                    if (state!=s.ringing) break;
                    input01.setState(PinState.LOW);
                    Thread.sleep(100);
                    if (state!=s.ringing) break;
                    input01.setState(PinState.HIGH);
                    Thread.sleep(500);
                    if (state!=s.ringing) break;
                    input01.setState(PinState.LOW);
                    Thread.sleep(100);
                    if (state!=s.ringing) break;
                }
                if (state==s.ringing) state=s.idle;
            } catch (InterruptedException e) {
                System.out.println("interrupted exception in private message received");
            }
        }
    }

    @Override
    public void onGuildMessageReceived(@NotNull GuildMessageReceivedEvent event){
        if (event.getAuthor().isBot()) return;
        if (event.getMessage().getContentRaw().toLowerCase().contains("halo") && state==s.idle){
            System.out.println(event.getAuthor().getName()+" is calling");
            input01.setState(PinState.HIGH);
            state = s.ringing;
            try {
                for (int i=0;i<30;i++){
                    input01.setState(PinState.HIGH);
                    Thread.sleep(100);
                    if (state!=s.ringing) break;
                    input01.setState(PinState.LOW);
                    Thread.sleep(100);
                    if (state!=s.ringing) break;
                    input01.setState(PinState.HIGH);
                    Thread.sleep(100);
                    if (state!=s.ringing) break;
                    input01.setState(PinState.LOW);
                    Thread.sleep(100);
                    if (state!=s.ringing) break;
                    input01.setState(PinState.HIGH);
                    Thread.sleep(500);
                    if (state!=s.ringing) break;
                    input01.setState(PinState.LOW);
                    Thread.sleep(100);
                    if (state!=s.ringing) break;
                }
                if (state==s.ringing) state=s.idle;
            } catch (InterruptedException e) {
                System.out.println("interrupted exception in guild message received");
            }
        }
    }

    @Override
    public void onGuildVoiceLeave(GuildVoiceLeaveEvent event){
        if(event.getChannelLeft().getId().equals(fourdotvcid) && state==s.ringing){
            input01.setState(PinState.LOW);
            state=s.idle;
        }
    }

    public static void disconnectvc(){
        System.out.print("on hook, destroying...");
        if (audioManager!=null) {
            audioManager.closeAudioConnection();
            audioManager=null;
        }
        if (handler!=null){
            handler.close();
            handler=null;
        }
        System.out.println("OK.");
        jda.getPresence().setActivity(Activity.listening("nikogo"));
        jda.getPresence().setStatus(OnlineStatus.ONLINE);
        state = s.idle;
    }

    public static void connectvc(){
        Guild guild = fourdotvc.getGuild();
        audioManager = guild.getAudioManager();
        System.out.println("creating echo handler");
        handler = new EchoHandler();
        System.out.println("OK: echo handler somehow created");
        audioManager.setSendingHandler(handler);
        audioManager.setReceivingHandler(handler);
        audioManager.openAudioConnection(fourdotvc);

        jda.getPresence().setActivity(Activity.listening("kogos"));
        jda.getPresence().setStatus(OnlineStatus.DO_NOT_DISTURB);
        input01.setState(PinState.LOW);
        state=s.connected;
    }

    public static class EchoHandler implements AudioSendHandler, AudioReceiveHandler
    {
        /*
            All methods in this class are called by JDA threads when resources are available/ready for processing.
            The receiver will be provided with the latest 20ms of PCM stereo audio
            Note you can receive even while setting yourself to deafened!
            The sender will provide 20ms of PCM stereo audio (mic) once requested by JDA
            When audio is provided JDA will automatically set the bot to speaking!
         */
        final AudioFormat receivedAudioFormat = AudioReceiveHandler.OUTPUT_FORMAT;
        final AudioFormat sentAudioFormat = AudioSendHandler.INPUT_FORMAT;
        SourceDataLine sourceDataLine = null;
        TargetDataLine targetDataLine = null;
        private final int packetSize = 3840;
        byte[] tempbuffer = new byte[packetSize];
        byte[] recvbuffer = new byte[packetSize];

        public EchoHandler(){
            try{
                sourceDataLine = AudioSystem.getSourceDataLine(receivedAudioFormat);
                sourceDataLine.open();
                sourceDataLine.start();
                targetDataLine = AudioSystem.getTargetDataLine(sentAudioFormat);
                targetDataLine.open(sentAudioFormat, packetSize);
                targetDataLine.start();
                targetDataLine.read(tempbuffer, 0, tempbuffer.length);
            }
            catch(LineUnavailableException lue){
                System.out.println("shit waded, wrong sourcedataline info or whatever");
                lue.printStackTrace();
            }
            if(sourceDataLine != null && targetDataLine != null){
                System.out.println("speaker: "+sourceDataLine.getLineInfo());
                System.out.println("mic:     "+targetDataLine.getLineInfo());
            } else {
                System.err.println("well shit, this is really shit, audio devices are null");
            }

        }

        /* Receive Handling */

        @Override // combine multiple user audio-streams into a single one
        public boolean canReceiveCombined()
        {
            return true;
        }

        @Override
        public void handleCombinedAudio(CombinedAudio combinedAudio){
            recvbuffer = combinedAudio.getAudioData(1f); // volume at 100% = 1.0 (50% = 0.5 / 55% = 0.55)
            sourceDataLine.write(recvbuffer, 0, packetSize);
        }

        /* send handling */

        @Override
        public boolean canProvide(){
            return true;
        }

        @Override
        public ByteBuffer provide20MsAudio(){
            targetDataLine.read(tempbuffer, 0, packetSize);//read 20ms audio to buffer
            return tempbuffer == null ? null : ByteBuffer.wrap(tempbuffer);//wrap and pass the buffer
        }

        @Override
        public boolean isOpus(){
            // since we send audio that is received from discord we don't have opus but PCM
            return false;
        }

        public void close(){
            sourceDataLine.stop();
            sourceDataLine.flush();
            sourceDataLine.close();
            targetDataLine.stop();
            targetDataLine.flush();
            targetDataLine.close();
        }

    }
}